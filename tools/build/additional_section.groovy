// BEGIN included '7_additional_info.groovy'
// Sizing and hidden customization data: Documents and Media storage, Marketplace apps, scripts in workflows /
// scheduled jobs, languages. Read-only and self-contained: works on Liferay 7.0 and later.

// Wrapper function for '7_additional_info.groovy'
def invoke__7_additional_info_groovy(Map<String, Object> uasContext) {

    try {
        def humanSize = { Number bytes ->
            if (bytes == null) {
                return 'n/a'
            }
            def units = ['B', 'KB', 'MB', 'GB', 'TB']
            double value = bytes.doubleValue()
            int unit = 0
            while (value >= 1024 && unit < units.size() - 1) {
                value /= 1024
                unit++
            }
            return String.format('%.1f %s', value, units[unit])
        }

        def bundleContext = null
        try {
            bundleContext = com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext()
        } catch (Throwable ignored) {
        }
        if (bundleContext == null) {
            try {
                bundleContext = org.osgi.framework.FrameworkUtil.getBundle(
                        com.liferay.dynamic.data.mapping.service.DDMStructureLocalService.class).getBundleContext()
            } catch (Throwable ignored) {
            }
        }
        def loadClass = { String className ->
            try {
                return Class.forName(className)
            } catch (Throwable ignored) {
            }
            def parts = className.tokenize('.')
            def appPrefix = parts.size() > 3 ? parts[0..3].join('.') : className
            for (candidate in (bundleContext?.bundles ?: []).findAll {
                it.state == org.osgi.framework.Bundle.ACTIVE && it.symbolicName && it.symbolicName.startsWith(appPrefix)
            }) {
                try {
                    return candidate.loadClass(className)
                } catch (Throwable ignored) {
                }
            }
            return null
        }

        // ------------------------------------------------------------------
        // Documents and Media storage (sizes from the database; the file store itself is not read)
        // ------------------------------------------------------------------
        try {
            def dlFileEntryService = com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil
            def projections = com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil

            def sumQuery = dlFileEntryService.dynamicQuery()
            sumQuery.setProjection(projections.sum('size'))
            def totalSize = dlFileEntryService.dynamicQuery(sumQuery)?.find { it != null } ?: 0

            def byMimeQuery = dlFileEntryService.dynamicQuery()
            byMimeQuery.setProjection(projections.projectionList()
                    .add(projections.groupProperty('mimeType'))
                    .add(projections.rowCount())
                    .add(projections.sum('size')))
            def byMime = dlFileEntryService.dynamicQuery(byMimeQuery).collect { row ->
                [mimeType: row[0] ?: '(none)', count: (row[1] ?: 0) as long, size: (row[2] ?: 0) as long]
            }.sort { -it.size }

            def largestQuery = dlFileEntryService.dynamicQuery()
            largestQuery.setProjection(projections.max('size'))
            def largest = dlFileEntryService.dynamicQuery(largestQuery)?.find { it != null } ?: 0

            println UASUtils.toCSVLine('data_dl_total_size_bytes', totalSize,
                    "Latest versions of all Documents and Media files: ${humanSize(totalSize)}, largest file: ${humanSize(largest)} (from DLFileEntry.size)")
            println UASUtils.toCSVLine('data_dl_files_by_mime_type', byMime.size(),
                    "mimeType, files, size (largest first, top 25):\n${byMime.take(25).collect { " - ${it.mimeType}, ${it.count}, ${humanSize(it.size)}" }.join('\n')}")

            def versionsSumQuery = com.liferay.document.library.kernel.service.DLFileVersionLocalServiceUtil.dynamicQuery()
            versionsSumQuery.setProjection(projections.sum('size'))
            def versionsSize = com.liferay.document.library.kernel.service.DLFileVersionLocalServiceUtil.dynamicQuery(versionsSumQuery)?.find { it != null } ?: 0
            println UASUtils.toCSVLine('data_dl_all_versions_size_bytes', versionsSize,
                    "All versions of all Documents and Media files: ${humanSize(versionsSize)} (approximate size of the file store to migrate)")
        } catch (Throwable t) {
            println UASUtils.toCSVLine('data_dl_total_size_bytes', '', "n/a - ${t}")
        }

        // ------------------------------------------------------------------
        // Marketplace apps (.lpkg) installed
        // ------------------------------------------------------------------
        try {
            def marketplaceDir = com.liferay.portal.kernel.util.PropsUtil.get('module.framework.marketplace.dir') ?:
                    "${com.liferay.portal.kernel.util.PropsUtil.get('liferay.home')}/osgi/marketplace"
            def lpkgs = (new File(marketplaceDir as String).listFiles() ?: []).findAll { it.isFile() && it.name.endsWith('.lpkg') }.sort { it.name }
            def liferayLpkgs = lpkgs.findAll { it.name.startsWith('Liferay') }
            def otherLpkgs = lpkgs - liferayLpkgs
            println UASUtils.toCSVLine('custom_code_marketplace_apps_count', otherLpkgs.size(),
                    "Marketplace apps (.lpkg) in '${marketplaceDir}' not named 'Liferay*' (${liferayLpkgs.size()} Liferay packages not listed):\n${otherLpkgs.collect { " - ${it.name} (${humanSize(it.length())})" }.join('\n')}")
        } catch (Throwable t) {
            println UASUtils.toCSVLine('custom_code_marketplace_apps_count', '', "n/a - ${t}")
        }

        // ------------------------------------------------------------------
        // Scripts in workflow definitions, scheduled jobs and Dispatch triggers (hidden custom code)
        // ------------------------------------------------------------------
        try {
            def kaleoClass = loadClass('com.liferay.portal.workflow.kaleo.service.KaleoDefinitionLocalServiceUtil')
            if (kaleoClass == null) {
                println UASUtils.toCSVLine('custom_code_workflow_scripts_count', '', 'n/a - workflow not available in this release')
            } else {
                def withScripts = kaleoClass.getKaleoDefinitions(-1, -1).collect { definition ->
                    def content = definition.content ?: ''
                    def languages = (content =~ /<script-language>\s*([a-zA-Z]+)\s*<\/script-language>/).collect { it[1].toLowerCase() }
                    [definition: definition, scripts: languages.size(), languages: languages.unique()]
                }.findAll { it.scripts > 0 }
                println UASUtils.toCSVLine('custom_code_workflow_scripts_count', withScripts.sum(0) { it.scripts },
                        "Scripts (actions / conditions / assignments) in workflow definitions, name vVersion: count (languages):\n${withScripts.collect { " - ${it.definition.name} v${it.definition.version}: ${it.scripts} (${it.languages.join(', ')})" }.join('\n')}")
            }
        } catch (Throwable t) {
            println UASUtils.toCSVLine('custom_code_workflow_scripts_count', '', "n/a - ${t}")
        }

        try {
            def jobs = com.liferay.portal.kernel.scheduler.SchedulerEngineHelperUtil.getScheduledJobs()
            def customJobs = jobs.findAll { job ->
                def name = (job.jobName ?: '') + ' ' + (job.groupName ?: '')
                // Dispatch jobs are reported in custom_code_dispatch_triggers_count
                !name.contains('com.liferay.') && !name.startsWith('DISPATCH_')
            }
            println UASUtils.toCSVLine('custom_code_scheduled_jobs_count', customJobs.size(),
                    "Scheduled jobs not provided by Liferay, excluding Dispatch jobs (${jobs.size()} scheduled jobs in total), jobName | groupName:\n${customJobs.collect { " - ${it.jobName} | ${it.groupName}" }.join('\n')}")
        } catch (Throwable t) {
            println UASUtils.toCSVLine('custom_code_scheduled_jobs_count', '', "n/a - ${t}")
        }

        try {
            def dispatchClass = loadClass('com.liferay.dispatch.service.DispatchTriggerLocalServiceUtil')
            if (dispatchClass == null) {
                println UASUtils.toCSVLine('custom_code_dispatch_triggers_count', '', 'n/a - Dispatch not available in this release')
            } else {
                def triggers = dispatchClass.getDispatchTriggers(-1, -1)
                def system = triggers.findAll { t -> try { t.isSystem() } catch (Throwable ignored) { false } }
                def userTriggers = triggers - system
                def byType = userTriggers.countBy { it.dispatchTaskExecutorType }
                println UASUtils.toCSVLine('custom_code_dispatch_triggers_count', userTriggers.size(),
                        "Dispatch triggers created by users (${system.size()} system triggers not counted), by task executor type (e.g. groovy-script = Groovy code):\n${byType.collect { " - ${it.key}: ${it.value}" }.join('\n')}")
            }
        } catch (Throwable t) {
            println UASUtils.toCSVLine('custom_code_dispatch_triggers_count', '', "n/a - ${t}")
        }

        // ------------------------------------------------------------------
        // Languages
        // ------------------------------------------------------------------
        try {
            def companyLocales = com.liferay.portal.kernel.service.CompanyLocalServiceUtil.getCompanies().collect { company ->
                def locales = com.liferay.portal.kernel.util.PrefsPropsUtil.getString(company.companyId, 'locales') ?:
                        com.liferay.portal.kernel.util.PropsUtil.get('locales.enabled')
                "${company.webId}: default ${company.locale}, available ${locales}"
            }
            // only sites whose settings mention inheritLocales (instead of loading every group, incl. one per user)
            def localesQuery = com.liferay.portal.kernel.service.GroupLocalServiceUtil.dynamicQuery()
            localesQuery.add(com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil.eq('site', true))
            localesQuery.add(com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil.like('typeSettings', '%inheritLocales%'))
            def sitesWithOwnLanguages = com.liferay.portal.kernel.service.GroupLocalServiceUtil.dynamicQuery(localesQuery).findAll {
                it.getTypeSettingsProperty('inheritLocales') == 'false'
            }
            def allLocales = com.liferay.portal.kernel.util.PropsUtil.get('locales.enabled')?.tokenize(',') ?: []
            println UASUtils.toCSVLine('data_locales_enabled_count', allLocales.size(),
                    "Languages per virtual instance:\n${companyLocales.collect { " - ${it}" }.join('\n')}\nSites with their own language settings: ${sitesWithOwnLanguages.size()}")
        } catch (Throwable t) {
            println UASUtils.toCSVLine('data_locales_enabled_count', '', "n/a - ${t}")
        }
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '7_additional_info.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')

        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__7_additional_info_groovy()
// END included '7_additional_info.groovy'

