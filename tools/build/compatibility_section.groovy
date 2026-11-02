// BEGIN included '1_compatibility.groovy'
// Support lifecycle of the running version, and the running stack (JDK, application server, database, operating
// system, search engine, editor) compared with the Liferay DXP compatibility matrix of the current upgrade targets
// (2026.Q1 LTS and 2026.Q3), plus breaking changes of recent releases that can be detected in the installation.
// Sources: the public Liferay end of service life dates, compatibility matrices and release breaking changes.
// Read-only, no network calls. Works on Liferay 6.1 and later.

// Wrapper function for '1_compatibility.groovy'
def invoke__1_compatibility_groovy(Map<String, Object> uasContext) {

    try {
        def TARGET = '2026.Q1 LTS / 2026.Q3'
        def CERTIFIED = 'certified'
        def DEPRECATED = 'deprecated'
        def NOT_CERTIFIED = 'not certified'
        def UNKNOWN = 'unknown'
        List<String> issues = []

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
                return (file.isFile() && file.canRead()) ? file.getText() : ''
            } catch (Throwable ignored) {
                return ''
            }
        }
        def propsUtil = loadClass('com.liferay.portal.kernel.util.PropsUtil')
        def prop = { String key ->
            try {
                return propsUtil?.get(key)?.toString() ?: ''
            } catch (Throwable ignored) {
                return ''
            }
        }
        def status = { String key, String component, String state, String detected, String details ->
            if (state == DEPRECATED || state == NOT_CERTIFIED) {
                issues << "${component}: ${detected} (${state})".toString()
            }
            println UASUtils.toCSVLine(key, state, "${detected}. ${details}".toString())
        }

        // ---- 1. Support lifecycle ----------------------------------------------------------------------------------
        def releaseInfo = com.liferay.portal.kernel.util.ReleaseInfo.getReleaseInfo() ?: ''
        def version = com.liferay.portal.kernel.util.ReleaseInfo.getVersion() ?: ''
        String displayName = ''
        try {
            displayName = com.liferay.portal.kernel.util.ReleaseInfo.getVersionDisplayName() ?: ''
        } catch (Throwable ignored) {
        }
        boolean communityEdition = releaseInfo.contains('Community Edition') || (releaseInfo =~ /\bCE\b/).find()
        def quarterlyMatcher = ((displayName + ' ' + releaseInfo) =~ /(\d{4})\.Q([1-4])/)
        String quarterly = quarterlyMatcher.find() ? "${quarterlyMatcher.group(1)}.Q${quarterlyMatcher.group(2)}".toString() : ''
        def parts = version.tokenize('.')
        String majorMinor = parts.size() >= 2 ? parts[0] + '.' + parts[1] : version
        Integer update = null
        def updateMatcher = ((displayName + ' ' + releaseInfo) =~ /(?:Update |\bU)(\d+)\b/)
        if (updateMatcher.find()) {
            update = updateMatcher.group(1).toInteger()
        }

        // First ship date, end of Premium Support, end of life (public Liferay end of service life dates)
        Map<String, List<String>> lifecycle = [
                '2026.Q3': ['2026-08-19', '2027-08-18', '2027-08-18'],
                '2026.Q2': ['2026-05-13', '2027-05-12', '2027-05-12'],
                '2026.Q1': ['2026-03-18', '2029-03-17', '2031-03-17'],
                '2025.Q4': ['2025-11-26', '2026-11-25', '2026-11-25'],
                '2025.Q3': ['2025-09-03', '2026-09-02', '2026-09-02'],
                '2025.Q2': ['2025-05-28', '2026-05-27', '2026-05-27'],
                '2025.Q1': ['2025-02-19', '2028-02-18', '2030-02-18'],
                '2024.Q4': ['2024-12-11', '2025-12-10', '2025-12-10'],
                '2024.Q3': ['2024-09-12', '2025-09-11', '2025-09-11'],
                '2024.Q2': ['2024-06-12', '2025-06-11', '2025-06-11'],
                '2024.Q1': ['2024-03-13', '2027-03-13', '2029-03-12'],
                '2023.Q4': ['2023-12-13', '2025-12-31', '2025-12-31'],
                '2023.Q3': ['2023-10-20', '2025-12-31', '2025-12-31'],
                '7.4'    : ['2023-09-01', '2027-09-01', '2030-09-01'],
                '7.3'    : ['2020-10-13', '2024-10-12', '2027-10-12'],
                '7.2'    : ['2019-06-04', '2023-06-03', '2026-06-03'],
                '7.1'    : ['2018-07-10', '2022-11-13', '2025-11-13'],
                '7.0'    : ['2016-06-15', '2020-06-14', '2023-06-14'],
                '6.2'    : ['2013-12-02', '2017-12-01', '2020-12-01'],
                '6.1'    : ['2012-02-22', '2016-02-21', '2016-02-21']]
        def LTS = ['2024.Q1', '2025.Q1', '2026.Q1']
        def dateFormat = new java.text.SimpleDateFormat('yyyy-MM-dd')
        String today = dateFormat.format(new Date())
        String releaseKey = quarterly ?: majorMinor
        String releaseLabel = quarterly ? "Liferay DXP ${quarterly}${quarterly in LTS ? ' LTS' : ''}" :
                "Liferay ${communityEdition ? 'Portal CE' : (majorMinor.startsWith('6.') ? 'Portal EE' : 'DXP')} ${majorMinor}${update != null ? ' (update ' + update + ')' : ''}"
        def dates = lifecycle[releaseKey]
        if (communityEdition) {
            println UASUtils.toCSVLine('compat_support_phase', 'Community Edition - no Liferay support',
                    "${releaseLabel}: Liferay Portal CE has community support only. Liferay DXP subscriptions include Premium Support, " +
                    "security fixes and upgrade help. The DXP equivalent of this version reaches end of life on " +
                    "${lifecycle[majorMinor]?.get(2) ?: 'n/a'}.")
        } else if (dates == null) {
            println UASUtils.toCSVLine('compat_support_phase', UNKNOWN, "${releaseLabel}: no service life dates known for this release")
        } else {
            String phase
            String note = ''
            if (today < dates[1]) {
                phase = 'Premium Support'
            } else if (today < dates[2]) {
                phase = 'Limited Support'
                note = ' Limited Support: existing fixes and upgrade help only, no new fixes (unless Extended Premium Support was purchased).'
            } else {
                phase = 'End of life'
                note = ' End of life: no Liferay support services (unless Extended Premium or Sustained Support was agreed). Upgrade to a supported release.'
            }
            if (releaseKey == '7.4' && update != 92 && today >= '2025-08-31') {
                phase = today < dates[2] ? 'Limited Support' : phase
                note = ' DXP 7.4 updates other than Update 92 stopped receiving hotfixes on 2025-08-31; only 7.4 Update 92 has Premium Support until 2027-09-01.' + note
            }
            long daysLeft = (dateFormat.parse(today < dates[1] ? dates[1] : dates[2]).time - dateFormat.parse(today).time).intdiv(86400000L)
            println UASUtils.toCSVLine('compat_support_phase', phase,
                    "${releaseLabel}: first ship date ${dates[0]}, end of Premium Support ${dates[1]}, end of life ${dates[2]}" +
                    (daysLeft > 0 ? " (${daysLeft} days until the end of ${today < dates[1] ? 'Premium Support' : 'life'})" : '') +
                    ". Quarterly Releases: LTS (2024.Q1, 2025.Q1, 2026.Q1) have 3 years of Premium Support and 2 years of Limited Support, " +
                    "other Quarterly Releases 1 year of Premium Support.${note}")
            if (phase != 'Premium Support') {
                issues << "Support: ${releaseLabel} is in ${phase}".toString()
            }
        }

        println UASUtils.toCSVLine('compat_target_release', TARGET,
                'The compat_* lines compare this installation with the Liferay DXP compatibility matrix of 2026.Q1 LTS and 2026.Q3 ' +
                '(same certified application servers, databases, operating systems, JDK and search engines). "deprecated" means ' +
                'still certified but planned for removal; "not certified" means it must change before or during the upgrade.')

        // ---- 2. JDK -------------------------------------------------------------------------------------------------
        try {
            def javaSpec = System.getProperty('java.specification.version', '')
            def javaMajor = javaSpec.startsWith('1.') ? javaSpec.substring(2) : javaSpec
            String state = javaMajor == '21' ? CERTIFIED : (javaMajor == '17' ? DEPRECATED : NOT_CERTIFIED)
            status('compat_jdk', 'JDK', state, "Java ${System.getProperty('java.version')} (${System.getProperty('java.vendor')})",
                    'Certified: JDK 21; JDK 17 is deprecated. All Java TCK compliant builds of these versions are certified.')
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_jdk', '', "n/a - ${t}")
        }

        // ---- 3. Application server ----------------------------------------------------------------------------------
        try {
            def serverDetector = loadClass('com.liferay.portal.kernel.util.ServerDetector')
            String serverId = (serverDetector?.getServerId() ?: '').toString().toLowerCase()
            String detected = serverId ?: 'unknown'
            String state = UNKNOWN
            if (serverId.contains('tomcat')) {
                String tomcatVersion = ''
                try {
                    tomcatVersion = loadClass('org.apache.catalina.util.ServerInfo')?.getServerNumber() ?: ''
                } catch (Throwable ignored) {
                }
                if (!tomcatVersion) {
                    def notes = readFile(new File(System.getProperty('catalina.home', '.'), 'RELEASE-NOTES').path)
                    tomcatVersion = notes.find(/Apache Tomcat Version (\d+(?:\.\d+)*)/) { all, v -> v } ?: ''
                }
                detected = "Apache Tomcat ${tomcatVersion ?: '(version not detected)'}"
                state = tomcatVersion.startsWith('10.1') ? CERTIFIED : (tomcatVersion ? NOT_CERTIFIED : UNKNOWN)
            } else if (serverId.contains('wildfly') || serverId.contains('jboss')) {
                String jbossVersion = System.getProperty('jboss.product.version') ?: System.getProperty('jboss.version') ?: ''
                String product = System.getProperty('jboss.product.name') ?: (serverId.contains('wildfly') ? 'WildFly' : 'JBoss EAP')
                detected = "${product} ${jbossVersion ?: '(version not detected)'}"
                if (jbossVersion) {
                    state = (product.toLowerCase().contains('eap') && jbossVersion.startsWith('8.')) ||
                            (!product.toLowerCase().contains('eap') && jbossVersion.startsWith('30.')) ? CERTIFIED : NOT_CERTIFIED
                }
            } else if (serverId.contains('weblogic')) {
                String weblogicVersion = ''
                try {
                    weblogicVersion = loadClass('weblogic.version')?.getReleaseBuildVersion() ?: ''
                } catch (Throwable ignored) {
                }
                detected = "Oracle WebLogic ${weblogicVersion ?: '(version not detected)'}"
                state = weblogicVersion ? (weblogicVersion.startsWith('15.') ? CERTIFIED : NOT_CERTIFIED) : UNKNOWN
            } else if (serverId) {
                state = NOT_CERTIFIED
            }
            status('compat_app_server', 'Application server', state, detected,
                    'Certified: Apache Tomcat 10.1, JBoss EAP 8.x, WildFly 30, Oracle WebLogic 15c (Jakarta EE 10). ' +
                    'Tomcat 9, JBoss EAP 7.4, WildFly 26.1 and WebLogic 14c are not supported from 2025.Q3; WebSphere is not supported')
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_app_server', '', "n/a - ${t}")
        }

        // ---- 4. Database ----------------------------------------------------------------------------------------------
        try {
            def dataAccess = loadClass('com.liferay.portal.kernel.dao.jdbc.DataAccess')
            def connection = dataAccess.getConnection()
            String product = ''
            String productVersion = ''
            int major = 0
            int minor = 0
            try {
                def metaData = connection.getMetaData()
                product = metaData.getDatabaseProductName() ?: ''
                productVersion = metaData.getDatabaseProductVersion() ?: ''
                major = metaData.getDatabaseMajorVersion()
                minor = metaData.getDatabaseMinorVersion()
            } finally {
                dataAccess.cleanUp(connection)
            }
            String name = product.toLowerCase()
            String jdbcUrl = prop('jdbc.default.url').toLowerCase()
            String detected = "${product} ${productVersion}".trim()
            String state = NOT_CERTIFIED
            if (jdbcUrl.contains('.rds.amazonaws.com') && jdbcUrl.contains('.cluster-')) {
                detected = "Amazon Aurora (${detected})"
                state = CERTIFIED
            } else if (productVersion.toLowerCase().contains('mariadb') || name.contains('mariadb')) {
                def m = (productVersion =~ /(?:5\.5\.5-)?(\d+)\.(\d+)/)
                String v = m.find() ? "${m.group(1)}.${m.group(2)}" : "${major}.${minor}"
                detected = "MariaDB ${v}"
                state = (v in ['10.6', '11.8']) ? CERTIFIED : ((v in ['10.2', '10.4']) ? DEPRECATED : NOT_CERTIFIED)
            } else if (name.contains('mysql')) {
                String v = "${major}.${minor}"
                detected = "MySQL ${productVersion}"
                state = (v in ['8.0', '8.4']) ? CERTIFIED : (v == '5.7' ? DEPRECATED : NOT_CERTIFIED)
            } else if (name.contains('postgresql')) {
                detected = "PostgreSQL ${productVersion}"
                state = (major in [14, 15, 16, 17]) ? CERTIFIED : ((major in [12, 13]) ? DEPRECATED : NOT_CERTIFIED)
            } else if (name.contains('sql server')) {
                Map<Integer, String> releases = [11: '2012', 12: '2014', 13: '2016', 14: '2017', 15: '2019', 16: '2022', 17: '2025']
                detected = "SQL Server ${releases[major] ?: ''} (${productVersion})"
                state = (major in [15, 16]) ? CERTIFIED : (major == 14 ? DEPRECATED : NOT_CERTIFIED)
            } else if (name.contains('oracle')) {
                detected = "Oracle Database ${major}.${minor}"
                state = major == 19 ? CERTIFIED : NOT_CERTIFIED
            } else if (name.contains('db2')) {
                detected = "IBM DB2 ${major}.${minor}"
                state = (major == 11 && minor == 5) ? CERTIFIED : ((major == 11 && minor == 1) ? DEPRECATED : NOT_CERTIFIED)
            } else if (name.contains('hsql')) {
                detected = "HSQLDB ${productVersion} (embedded demo database)"
            }
            status('compat_database', 'Database', state, detected,
                    'Certified: Amazon Aurora, MySQL 8.0 / 8.4, MariaDB 10.6 / 11.8, PostgreSQL 14 - 17, Oracle 19c, SQL Server 2019 / 2022, DB2 11.5. ' +
                    'Deprecated: MySQL 5.7, MariaDB 10.2 / 10.4, PostgreSQL 12 / 13, SQL Server 2017, DB2 11.1. HSQLDB is for demos only. ' +
                    'Oracle, SQL Server and DB2 require a DXP Enterprise subscription')
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_database', '', "n/a - ${t}")
        }

        // ---- 5. Operating system ------------------------------------------------------------------------------------
        try {
            String osName = System.getProperty('os.name', '')
            String osVersion = System.getProperty('os.version', '')
            String detected = "${osName} ${osVersion}"
            String state = NOT_CERTIFIED
            boolean liferayImage = new File('/usr/local/bin/liferay_entrypoint.sh').exists()
            if (osName.toLowerCase().contains('linux')) {
                def release = readFile('/etc/os-release')
                def value = { String key ->
                    def m = java.util.regex.Pattern.compile('(?m)^' + key + '="?([^"\\n]*)"?\\s*$').matcher(release)
                    m.find() ? m.group(1) : ''
                }
                String id = value('ID').toLowerCase()
                String versionId = value('VERSION_ID')
                String major = versionId.tokenize('.') ? versionId.tokenize('.')[0] : ''
                detected = value('PRETTY_NAME') ?: "${id} ${versionId}"
                Map<String, Map<String, List<String>>> matrix = [
                        alpine: [certified: ['3'], deprecated: []],
                        amzn  : [certified: ['2', '2023'], deprecated: []],
                        centos: [certified: [], deprecated: ['7', '8']],
                        debian: [certified: ['12'], deprecated: ['10', '11']],
                        fedora: [certified: ['*'], deprecated: []],
                        ol    : [certified: ['8', '9'], deprecated: ['7']],
                        rhel  : [certified: ['8', '9', '10'], deprecated: ['7']],
                        rocky : [certified: ['9'], deprecated: []],
                        sles  : [certified: ['15'], deprecated: ['12']],
                        ubuntu: [certified: ['22.04'], deprecated: ['18.04', '20.04']]]
                def entry = matrix[id]
                String key = id == 'ubuntu' ? versionId : major
                if (entry != null) {
                    state = (entry.certified.contains('*') || entry.certified.contains(key)) ? CERTIFIED :
                            (entry.deprecated.contains(key) ? DEPRECATED : NOT_CERTIFIED)
                }
                if (liferayImage) {
                    detected += ' (official Liferay Docker image)'
                    state = CERTIFIED
                }
            } else if (osName.toLowerCase().contains('windows')) {
                state = (osName =~ /Windows Server (2016|2019|2022)/).find() ? CERTIFIED : NOT_CERTIFIED
            } else if (osName.toLowerCase().contains('aix')) {
                state = osVersion.startsWith('7.2') ? CERTIFIED : NOT_CERTIFIED
            } else if (osName.toLowerCase().contains('sunos')) {
                detected = "Oracle Solaris (SunOS ${osVersion})"
                state = osVersion.startsWith('5.11') ? CERTIFIED : NOT_CERTIFIED
            }
            status('compat_os', 'Operating system', state, detected,
                    'Certified: Alpine 3.x, Amazon Linux 2 / 2023, Debian 12, Fedora, IBM AIX 7.2, Oracle Linux 8 / 9, Oracle Solaris 11, ' +
                    'RHEL 8 / 9 / 10, Rocky Linux 9, SUSE 15, Ubuntu 22.04, Windows Server 2016 / 2019 / 2022. Deprecated: CentOS 7 / 8, ' +
                    'Debian 10 / 11, Oracle Linux 7, RHEL 7, SUSE 12, Ubuntu 18.04 / 20.04. Containers are fine when the components are certified')
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_os', '', "n/a - ${t}")
        }

        // ---- 6. Search engine ---------------------------------------------------------------------------------------
        try {
            String detected = majorMinor.startsWith('6.') ? 'Lucene / Solr (Liferay 6.x search)' : 'not detected'
            String state = majorMinor.startsWith('6.') ? NOT_CERTIFIED : UNKNOWN
            def bundleContext = null
            try {
                bundleContext = loadClass('com.liferay.portal.kernel.module.util.SystemBundleUtil')?.getBundleContext()
            } catch (Throwable ignored) {
            }
            def reference = bundleContext?.getServiceReference('com.liferay.portal.search.engine.SearchEngineInformation')
            if (reference != null) {
                def information = bundleContext.getService(reference)
                try {
                    String vendor = information.getVendorString() ?: ''
                    String nodes = ''
                    try {
                        nodes = information.getNodesString() ?: ''
                    } catch (Throwable ignored) {
                    }
                    String clientVersion = ''
                    try {
                        clientVersion = information.getClientVersionString() ?: ''
                    } catch (Throwable ignored) {
                    }
                    def nodeVersion = (nodes =~ /(\d+\.\d+\.\d+)/)
                    String serverVersion = nodeVersion.find() ? nodeVersion.group(1) : ''
                    String v = serverVersion ?: clientVersion
                    String lowerVendor = vendor.toLowerCase()
                    detected = "${vendor} ${serverVersion ? 'server ' + serverVersion : 'connector ' + clientVersion}".trim()
                    if (lowerVendor.contains('solr')) {
                        state = NOT_CERTIFIED
                    } else if (lowerVendor.contains('opensearch')) {
                        state = v.startsWith('2.19') ? CERTIFIED : NOT_CERTIFIED
                    } else if (v) {
                        state = v.startsWith('8.19') ? CERTIFIED : NOT_CERTIFIED
                    }
                    if (lowerVendor.contains('sidecar') || lowerVendor.contains('embedded')) {
                        detected += ' (sidecar / embedded: not for production)'
                        state = NOT_CERTIFIED
                    }
                } finally {
                    bundleContext.ungetService(reference)
                }
            }
            status('compat_search_engine', 'Search engine', state, detected,
                    'Certified: Elasticsearch 8.19.x (connector bundled) and OpenSearch 2.19.x (connector on Liferay Marketplace). ' +
                    'Elasticsearch 7 reached end of life on 2026-01-15 and is not supported from 2026.Q1: upgrade the Elastic stack to 8.19 before upgrading Liferay. ' +
                    'Solr and the sidecar / embedded Elasticsearch are not supported for production')
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_search_engine', '', "n/a - ${t}")
        }

        // ---- 7. Rich text editor --------------------------------------------------------------------------------------
        try {
            String editor = prop('editor.wysiwyg.default') ?: 'ckeditor'
            String state = editor.toLowerCase().contains('ckeditor5') ? CERTIFIED : (editor.toLowerCase().startsWith('ckeditor') ? DEPRECATED : NOT_CERTIFIED)
            status('compat_wysiwyg_editor', 'Default rich text editor', state, "editor.wysiwyg.default=${editor}",
                    'CKEditor 4 is deprecated from 2026.Q3; CKEditor 5 is the supported editor. Other editors (TinyMCE, AlloyEditor) are no longer certified. ' +
                    'Custom CKEditor 4 plugins or configurations must be reviewed')
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_wysiwyg_editor', '', "n/a - ${t}")
        }

        // ---- 8. Breaking changes that can be detected -------------------------------------------------------------------
        def bundleContext = null
        try {
            bundleContext = loadClass('com.liferay.portal.kernel.module.util.SystemBundleUtil')?.getBundleContext()
        } catch (Throwable ignored) {
        }

        // 8a. Portal properties that moved to System / Instance Settings (2025.Q3, 2025.Q4)
        try {
            String liferayHome = prop('liferay.home')
            Map<String, String> moved = [
                    'dl.file.entry.raw.metadata.processor.excluded.mime.types': '2025.Q3: moved to Instance / System Settings, reconfigure it there',
                    'mail.session.mail'                                         : '2025.Q4: Server Administration > Mail moved to System Settings > Email > Mail Settings; settings stored in the database are migrated, portal properties must be checked']
            List<String> found = []
            def files = []
            if (liferayHome) {
                new File(liferayHome).listFiles()?.each { file ->
                    if (file.isFile() && file.name.startsWith('portal-') && file.name.endsWith('.properties')) {
                        files << file
                    }
                }
            }
            files.each { file ->
                file.eachLine { line ->
                    def key = line.trim().tokenize('=')[0]?.trim()
                    if (key && !key.startsWith('#')) {
                        moved.each { prefix, why ->
                            if (key.startsWith(prefix)) {
                                found << "${key} in ${file.name} (${why})".toString()
                            }
                        }
                    }
                }
            }
            System.getenv()?.keySet()?.each { name ->
                if (name.startsWith('LIFERAY_MAIL_PERIOD_SESSION_PERIOD_MAIL') || name.startsWith('LIFERAY_DL_PERIOD_FILE_PERIOD_ENTRY_PERIOD_RAW_PERIOD_METADATA')) {
                    found << "environment variable ${name}".toString()
                }
            }
            found = found.unique()
            println UASUtils.toCSVLine('compat_breaking_moved_properties_count', found.size(),
                    found ? "Portal properties that moved to System / Instance Settings in recent releases:\n${found.collect { " - ${it}" }.join('\n')}" :
                            "None of the known moved portal properties are set (checked portal-*.properties in liferay.home and LIFERAY_* environment variables)")
            if (found) {
                issues << "Breaking change: ${found.size()} moved portal properties".toString()
            }
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_breaking_moved_properties_count', '', "n/a - ${t}")
        }

        // 8b. Configurations removed or changed in 2026.Q1 (Data Cleanup / Removal, Amazon S3 store on AWS SDK 2)
        try {
            if (bundleContext == null) {
                println UASUtils.toCSVLine('compat_breaking_configurations_count', '', 'n/a - OSGi configuration not available in this release')
            } else {
                List<String> found = []
                def reference = bundleContext.getServiceReference('org.osgi.service.cm.ConfigurationAdmin')
                if (reference != null) {
                    def configurationAdmin = bundleContext.getService(reference)
                    try {
                        ['com.liferay.data.cleanup.internal.configuration.DataCleanupConfiguration',
                         'com.liferay.data.cleanup.internal.configuration.DataRemovalConfiguration'].each { pid ->
                            if (configurationAdmin.listConfigurations("(service.pid=${pid})")) {
                                found << "${pid.tokenize('.').last()}: removed in 2026.Q1, the cleanup actions are now in Server Administration > Resources (System / Module Cleanup Actions)".toString()
                            }
                        }
                        if (prop('dl.store.impl').contains('S3Store')) {
                            (configurationAdmin.listConfigurations('(service.pid=com.liferay.portal.store.s3.configuration.S3StoreConfiguration)') ?: []).each { configuration ->
                                def used = ['ntlmProxyDomain', 'ntlmProxyWorkstation', 'signerOverride', 'connectionProtocol'].findAll {
                                    configuration.properties?.get(it)?.toString()?.trim()
                                }
                                if (used) {
                                    found << "Amazon S3 store uses ${used.join(', ')}: from 2026.Q1 (AWS SDK for Java 2) these settings only apply to IBM S3; NTLM proxy authentication to AWS no longer works".toString()
                                }
                            }
                        }
                    } finally {
                        bundleContext.ungetService(reference)
                    }
                }
                println UASUtils.toCSVLine('compat_breaking_configurations_count', found.size(),
                        found ? found.collect { " - ${it}" }.join('\n') : 'No Data Cleanup / Data Removal configurations and no Amazon S3 store settings affected by 2026.Q1 breaking changes')
                if (found) {
                    issues << "Breaking change: ${found.size()} configurations to review".toString()
                }
            }
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_breaking_configurations_count', '', "n/a - ${t}")
        }

        // 8c. Portlet frameworks that need new versions from 2025.Q3 (Jakarta EE): PortletMVC4Spring, Liferay Faces (JSF)
        try {
            List<String> found = []
            if (bundleContext != null) {
                bundleContext.bundles.each { bundle ->
                    def name = bundle.symbolicName ?: ''
                    def imports = bundle.headers?.get('Import-Package')?.toString() ?: ''
                    if (name.startsWith('com.liferay.') && !(bundle.location ?: '').contains('/osgi/war/') && !(bundle.location ?: '').contains('/deploy/')) {
                        return
                    }
                    def frameworks = []
                    if (imports.contains('com.liferay.portletmvc4spring') || imports.contains('org.springframework.web.portlet')) {
                        frameworks << 'PortletMVC4Spring / Spring Portlet MVC'
                    }
                    if (imports.contains('javax.faces') || imports.contains('com.liferay.faces')) {
                        frameworks << 'Liferay Faces (JSF)'
                    }
                    if (frameworks) {
                        found << "${name} ${bundle.version}: ${frameworks.join(', ')}".toString()
                    }
                }
            } else {
                // Liferay 6.x: look at the libraries of the deployed web applications
                def webapps = new File(System.getProperty('catalina.base', System.getProperty('catalina.home', '.')), 'webapps')
                webapps.listFiles()?.each { app ->
                    def libs = new File(app, 'WEB-INF/lib').list() ?: []
                    def frameworks = []
                    if (libs.any { it.contains('portletmvc4spring') || it.contains('spring-webmvc-portlet') }) {
                        frameworks << 'Spring Portlet MVC'
                    }
                    if (libs.any { it.contains('liferay-faces') || it.contains('jsf-api') || it.contains('javax.faces') }) {
                        frameworks << 'Liferay Faces (JSF)'
                    }
                    if (frameworks) {
                        found << "${app.name}: ${frameworks.join(', ')}".toString()
                    }
                }
            }
            println UASUtils.toCSVLine('compat_breaking_portlet_frameworks_count', found.size(),
                    (found ? "Custom portlets built with frameworks that need a Jakarta EE version from 2025.Q3 (PortletMVC4Spring 6.x, new Liferay Faces release):\n" +
                            found.collect { " - ${it}" }.join('\n') : 'No PortletMVC4Spring / Spring Portlet MVC or Liferay Faces (JSF) portlets found'))
            if (found) {
                issues << "Breaking change: ${found.size()} PortletMVC4Spring / JSF portlets".toString()
            }
        } catch (Throwable t) {
            println UASUtils.toCSVLine('compat_breaking_portlet_frameworks_count', '', "n/a - ${t}")
        }

        // ---- 9. Summary -------------------------------------------------------------------------------------------------
        println UASUtils.toCSVLine('compat_issues_count', issues.size(),
                issues ? "Items to address for an upgrade to ${TARGET}:\n${issues.collect { " - ${it}" }.join('\n')}" :
                        "No support lifecycle or compatibility issues found for ${TARGET}")
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '1_compatibility.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')

        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__1_compatibility_groovy()
// END included '1_compatibility.groovy'

