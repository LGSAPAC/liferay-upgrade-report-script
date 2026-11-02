// BEGIN included '1_hosting.groovy'
// Hosting model: Liferay SaaS, Liferay PaaS (Liferay Cloud), self-hosted cloud native (Kubernetes / containers),
// self-hosted on a cloud platform or cloud VM, or self-hosted on premises. Best effort, based on what the JVM can
// see: environment variable NAMES (never their values), container / Kubernetes marker files, the machine vendor
// in /sys/class/dmi/id, and the suffixes of the database host and virtual host names (never the names themselves).
// Read-only, no network calls. Works on Liferay 6.1 and later.

// Wrapper function for '1_hosting.groovy'
def invoke__1_hosting_groovy(Map<String, Object> uasContext) {

    try {
        def loadClass = { String className ->
            try {
                return Class.forName(className, true, this.getClass().getClassLoader())
            } catch (Throwable ignored) {
                return null
            }
        }
        def readFile = { String path ->
            try {
                def file = new File(path)
                return (file.isFile() && file.canRead()) ? file.getText().trim() : ''
            } catch (Throwable ignored) {
                return ''
            }
        }
        def exists = { String path ->
            try {
                return new File(path).exists()
            } catch (Throwable ignored) {
                return false
            }
        }

        Map<String, String> env = [:]
        try {
            env = System.getenv() ?: [:]
        } catch (Throwable ignored) {
        }
        def envNames = env.keySet().collect { it.toString() }
        def hasEnv = { String name -> envNames.contains(name) }
        def envWithPrefix = { String prefix -> envNames.findAll { it.startsWith(prefix) }.sort() }

        def propsUtil = loadClass('com.liferay.portal.kernel.util.PropsUtil')
        def prop = { String key ->
            try {
                return propsUtil?.get(key)?.toString() ?: ''
            } catch (Throwable ignored) {
                return ''
            }
        }

        List<String> signals = []

        // ---- host names Liferay is reached with / database host: only the suffix is used ----------------------
        List<String> hostNames = []
        try {
            def companyService = loadClass('com.liferay.portal.kernel.service.CompanyLocalServiceUtil') ?: loadClass('com.liferay.portal.service.CompanyLocalServiceUtil')
            companyService?.getCompanies()?.each { company ->
                try {
                    if (company.virtualHostname) {
                        hostNames << company.virtualHostname.toString().toLowerCase()
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            loadClass('com.liferay.portal.kernel.service.VirtualHostLocalServiceUtil')?.getVirtualHosts(-1, -1)?.each {
                hostNames << it.hostname.toString().toLowerCase()
            }
        } catch (Throwable ignored) {
        }
        if (prop('web.server.host')) {
            hostNames << prop('web.server.host').toLowerCase()
        }
        def hostSuffix = { List<String> suffixes -> hostNames.any { host -> suffixes.any { host.endsWith(it) } } }

        String dbHost = ''
        try {
            def matcher = (prop('jdbc.default.url') =~ /\/\/(?:[^@\/]*@)?([^\/:;?,]+)/)
            if (matcher.find()) {
                dbHost = matcher.group(1).toLowerCase()
            }
        } catch (Throwable ignored) {
        }

        // ---- Liferay SaaS / PaaS ---------------------------------------------------------------------------------
        def lcpVars = envWithPrefix('LCP_')
        boolean saas = hostSuffix(['.lxc.liferay.com'])
        boolean liferayCloud = !lcpVars.isEmpty() || hostSuffix(['.lfr.cloud'])
        if (saas) {
            signals << 'host names on lxc.liferay.com (Liferay SaaS)'
        }
        if (!lcpVars.isEmpty()) {
            signals << "Liferay Cloud environment variables (${lcpVars.size()}: ${lcpVars.take(5).join(', ')}${lcpVars.size() > 5 ? ', ...' : ''})"
        }
        if (hostSuffix(['.lfr.cloud'])) {
            signals << 'host names on lfr.cloud (Liferay Cloud)'
        }

        // ---- containers and Kubernetes ---------------------------------------------------------------------------
        boolean kubernetes = hasEnv('KUBERNETES_SERVICE_HOST') || exists('/var/run/secrets/kubernetes.io/serviceaccount')
        String cgroup = readFile('/proc/1/cgroup') + '\n' + readFile('/proc/self/cgroup')
        String mountInfo = ''
        if (!kubernetes) {
            mountInfo = readFile('/proc/self/mountinfo')
        }
        boolean docker = exists('/.dockerenv') || cgroup.contains('docker') || mountInfo.contains('/docker/containers/')
        boolean podman = exists('/run/.containerenv') || cgroup.contains('libpod')
        boolean container = kubernetes || docker || podman || cgroup.contains('kubepods') || cgroup.contains('containerd') ||
                hasEnv('container')
        boolean openShift = !envWithPrefix('OPENSHIFT_').isEmpty() || exists('/var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt')
        boolean liferayImage = exists('/usr/local/bin/liferay_entrypoint.sh')
        if (kubernetes) {
            signals << (openShift ? 'Kubernetes (OpenShift) service account / environment' : 'Kubernetes service account / environment')
        }
        if (docker || podman || (container && !kubernetes)) {
            signals << "container runtime markers (${[docker ? 'Docker' : null, podman ? 'Podman' : null, cgroup.contains('containerd') ? 'containerd' : null].findAll { it }.join(', ') ?: 'container'})"
        }
        if (liferayImage) {
            signals << 'official Liferay Docker image (liferay_entrypoint.sh)'
        }

        // ---- cloud provider ----------------------------------------------------------------------------------------
        String dmi = ['sys_vendor', 'product_name', 'bios_vendor', 'bios_version', 'board_vendor', 'chassis_asset_tag']
                .collect { readFile("/sys/class/dmi/id/${it}") }.join(' | ').toLowerCase()
        String hypervisor = readFile('/sys/hypervisor/uuid').toLowerCase()
        Map<String, List<String>> providerSignals = [:]
        def addProvider = { String provider, String signal ->
            if (!providerSignals[provider]) {
                providerSignals[provider] = []
            }
            providerSignals[provider] << signal
        }
        if (dmi.contains('amazon') || hypervisor.startsWith('ec2')) addProvider('AWS', 'machine vendor')
        if (dmi.contains('7783-7084-3265-9085-8269-3286-77')) addProvider('Microsoft Azure', 'machine vendor')
        if (dmi.contains('google')) addProvider('Google Cloud', 'machine vendor')
        if (dmi.contains('oraclecloud') || dmi.contains('oracle cloud')) addProvider('Oracle Cloud', 'machine vendor')
        if (dmi.contains('alibaba')) addProvider('Alibaba Cloud', 'machine vendor')
        if (dmi.contains('digitalocean')) addProvider('DigitalOcean', 'machine vendor')
        if (dmi.contains('ibm') && dmi.contains('cloud')) addProvider('IBM Cloud', 'machine vendor')
        // only variables set by the platform itself: AWS_* / AZURE_* SDK settings (e.g. credentials for S3) are also used on premises
        if (['AWS_EXECUTION_ENV', 'AWS_WEB_IDENTITY_TOKEN_FILE', 'AWS_CONTAINER_CREDENTIALS_RELATIVE_URI', 'ECS_CONTAINER_METADATA_URI',
             'ECS_CONTAINER_METADATA_URI_V4'].any { hasEnv(it) }) addProvider('AWS', 'platform environment variables')
        if (['WEBSITE_SITE_NAME', 'WEBSITE_INSTANCE_ID', 'CONTAINER_APP_NAME', 'AZURE_FEDERATED_TOKEN_FILE'].any { hasEnv(it) }) addProvider('Microsoft Azure', 'platform environment variables')
        if (['K_SERVICE', 'GAE_APPLICATION'].any { hasEnv(it) }) addProvider('Google Cloud', 'platform environment variables')
        if (dbHost.endsWith('.rds.amazonaws.com')) addProvider('AWS', 'Amazon RDS database')
        if (dbHost.endsWith('.database.azure.com') || dbHost.endsWith('.database.windows.net')) addProvider('Microsoft Azure', 'Azure database')
        if (dbHost.endsWith('.oraclecloud.com')) addProvider('Oracle Cloud', 'Oracle Cloud database')
        if (dbHost.contains('cloudsql') || prop('jdbc.default.url').contains('cloudSqlInstance')) addProvider('Google Cloud', 'Cloud SQL database')

        // ---- managed services ----------------------------------------------------------------------------------------
        List<String> managed = []
        if (dbHost.endsWith('.rds.amazonaws.com')) managed << 'Database: Amazon RDS / Aurora'
        if (dbHost.endsWith('.database.azure.com')) managed << 'Database: Azure Database (PostgreSQL / MySQL)'
        if (dbHost.endsWith('.database.windows.net')) managed << 'Database: Azure SQL'
        if (dbHost.contains('cloudsql') || prop('jdbc.default.url').contains('cloudSqlInstance')) managed << 'Database: Google Cloud SQL'
        if (dbHost.endsWith('.oraclecloud.com')) managed << 'Database: Oracle Cloud database'
        String store = prop('dl.store.impl')
        String storeName = store ? store.substring(store.lastIndexOf('.') + 1) : ''
        if (storeName.toLowerCase().contains('s3')) managed << "File store: Amazon S3 (${storeName})"
        if (storeName.toLowerCase().contains('gcs')) managed << "File store: Google Cloud Storage (${storeName})"
        if (storeName.toLowerCase().contains('azure')) managed << "File store: Azure Blob Storage (${storeName})"
        if (exists('/mnt/persistent-storage')) managed << 'File store: Liferay Cloud persistent storage'

        // ---- platform services (third-party PaaS) ------------------------------------------------------------------
        String platformService = ''
        if (hasEnv('WEBSITE_SITE_NAME') || hasEnv('WEBSITE_INSTANCE_ID')) platformService = 'Azure App Service'
        else if (hasEnv('CONTAINER_APP_NAME')) platformService = 'Azure Container Apps'
        else if (hasEnv('ECS_CONTAINER_METADATA_URI') || hasEnv('ECS_CONTAINER_METADATA_URI_V4')) platformService = 'Amazon ECS / Fargate'
        else if (hasEnv('K_SERVICE')) platformService = 'Google Cloud Run'
        else if (hasEnv('GAE_APPLICATION')) platformService = 'Google App Engine'

        String provider = providerSignals ? providerSignals.keySet().join(' / ') : ''
        if (provider) {
            signals << "cloud provider ${provider} (${providerSignals.collect { k, v -> "${k}: ${v.unique().join(', ')}" }.join('; ')})"
        }
        if (platformService) {
            signals << "platform service environment variables (${platformService})"
        }

        // ---- conclusion --------------------------------------------------------------------------------------------
        String model
        String confidence
        if (saas) {
            model = 'Liferay SaaS (Liferay Experience Cloud)'
            confidence = 'medium (Liferay SaaS does not normally allow Groovy scripts: please confirm)'
        } else if (liferayCloud) {
            model = 'Liferay PaaS (Liferay Cloud)'
            confidence = lcpVars ? 'high' : 'medium'
        } else if (platformService) {
            model = "Self-hosted - cloud platform service (${platformService})"
            confidence = 'high'
        } else if (kubernetes) {
            model = "Self-hosted - cloud native (${openShift ? 'OpenShift / Kubernetes' : 'Kubernetes'}${provider ? ' on ' + provider : ''})"
            confidence = 'high'
        } else if (container) {
            model = "Self-hosted - containers (${podman && !docker ? 'Podman' : 'Docker'}${provider ? ' on ' + provider : ''})"
            confidence = 'high'
        } else if (provider) {
            model = "Self-hosted - cloud virtual machine (${provider})"
            confidence = providerSignals.values().flatten().any { it == 'machine vendor' || it.startsWith('platform') } ? 'high' : 'medium'
        } else {
            model = 'Self-hosted - on premises or private virtual machine'
            confidence = System.getProperty('os.name', '').toLowerCase().contains('windows') ?
                    'low (Windows: container and machine-vendor checks are not available)' : 'medium (no cloud or container signals found)'
        }

        println UASUtils.toCSVLine('infra_hosting_model', model,
                "Best-effort detection, confidence ${confidence}. Possible values: Liferay SaaS, Liferay PaaS (Liferay Cloud), " +
                "Self-hosted - cloud native (Kubernetes), Self-hosted - containers, Self-hosted - cloud platform service, " +
                "Self-hosted - cloud virtual machine, Self-hosted - on premises.\nSignals found:\n" +
                (signals ? signals.collect { " - ${it}" }.join('\n') : ' - none'))
        println UASUtils.toCSVLine('infra_hosting_container_platform',
                kubernetes ? (openShift ? 'OpenShift / Kubernetes' : 'Kubernetes') : (container ? (podman && !docker ? 'Podman' : 'Docker') : 'none detected'),
                "Via Kubernetes service account / environment, /.dockerenv, /run/.containerenv and /proc cgroups" +
                (liferayImage ? '. Runs the official Liferay Docker image' : ''))
        println UASUtils.toCSVLine('infra_hosting_cloud_provider', provider ?: 'none detected',
                providerSignals ? providerSignals.collect { k, v -> "${k}: ${v.unique().join(', ')}" }.join('\n') :
                        'Via machine vendor (/sys/class/dmi/id), environment variable names and the database host suffix')
        println UASUtils.toCSVLine('infra_hosting_managed_services', managed.size(),
                managed ? managed.collect { " - ${it}" }.join('\n') :
                        "No cloud-managed database or file store detected (file store: ${storeName ?: 'default'})")
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '1_hosting.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')

        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__1_hosting_groovy()
// END included '1_hosting.groovy'

