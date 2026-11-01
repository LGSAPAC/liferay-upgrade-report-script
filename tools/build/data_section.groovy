// BEGIN included '2_data.groovy'

// Imports for '2_data.groovy' (portal kernel only; classes of OSGi modules are loaded with uasClass)
import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil
import com.liferay.expando.kernel.model.ExpandoBridge
import com.liferay.expando.kernel.model.ExpandoTable
import com.liferay.expando.kernel.service.ExpandoTableLocalServiceUtil
import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil
import com.liferay.exportimport.kernel.staging.StagingUtil
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil
import com.liferay.portal.kernel.dao.orm.QueryUtil
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal
import com.liferay.portal.kernel.service.ClassNameLocalServiceUtil
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil
import com.liferay.portal.kernel.service.GroupLocalServiceUtil
import com.liferay.portal.kernel.service.LayoutFriendlyURLLocalServiceUtil
import com.liferay.portal.kernel.service.LayoutLocalServiceUtil
import com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil
import com.liferay.portal.kernel.service.UserLocalServiceUtil
import com.liferay.portal.kernel.template.TemplateConstants
import com.liferay.portal.kernel.util.PrefsPropsUtil
import com.liferay.portal.kernel.util.Validator

// Wrapper function for '2_data.groovy'
def invoke__2_data_groovy(Map<String, Object> uasContext) {

    try {
        // Liferay version family this script was generated for: 7.0, 7.1, 7.2, 7.3 or 7.4 (7.4 GA/Updates and Quarterly Releases)
        def variant = '@@DATA_VARIANT@@'

        def DDMStructureLocalServiceUtil = uasClass('com.liferay.dynamic.data.mapping.service.DDMStructureLocalServiceUtil')
        def DDMTemplateLocalServiceUtil = uasClass('com.liferay.dynamic.data.mapping.service.DDMTemplateLocalServiceUtil')
        def JournalArticleLocalServiceUtil = uasClass('com.liferay.journal.service.JournalArticleLocalServiceUtil')

        def companies = CompanyLocalServiceUtil.getCompanies()
        def companyIds = companies*.companyId

        // Sites only (the groups table also holds one group per user, organization, ...)
        def sites = null
        def loadSites = {
            if (sites == null) {
                def siteQuery = GroupLocalServiceUtil.dynamicQuery()
                siteQuery.add(RestrictionsFactoryUtil.eq('site', true))
                siteQuery.addOrder(com.liferay.portal.kernel.dao.orm.OrderFactoryUtil.asc('name'))  // same order as GroupLocalServiceUtil::getGroups
                sites = GroupLocalServiceUtil.dynamicQuery(siteQuery)
            }
            return sites
        }

        // Counts and lists shared by several keys, each computed once
        def counts = [:]
        def countOf = { String name, Closure counter ->
            if (!counts.containsKey(name)) {
                counts[name] = counter()
            }
            return counts[name]
        }

        // DDM templates: custom web content templates (WC) and application display templates (ADT)
        def webContentTemplates = null
        def applicationDisplayTemplates = null
        def loadTemplates = {
            if (webContentTemplates == null) {
                def wcTemplateClassnameId = ClassNameLocalServiceUtil.getClassNameId('com.liferay.dynamic.data.mapping.model.DDMStructure')
                def defaultUserIds = companyIds.collect { companyId ->
                    try {
                        UserLocalServiceUtil.getGuestUserId(companyId)
                    } catch (MissingMethodException ignored) {
                        UserLocalServiceUtil.getDefaultUserId(companyId)
                    }
                }
                def wc = []
                def adt = []
                DDMTemplateLocalServiceUtil.getDDMTemplates(QueryUtil.ALL_POS, QueryUtil.ALL_POS).each { template ->
                    // Ignore BASIC-WEB-CONTENT
                    if (template.getClassNameId() == wcTemplateClassnameId && template.getTemplateKey() != 'BASIC-WEB-CONTENT') {
                        wc << template
                    // Only show custom ADTs
                    } else if (!(template.getUserId() in defaultUserIds)) {
                        adt << template
                    }
                }
                webContentTemplates = wc
                applicationDisplayTemplates = adt
            }
        }

        // Questions keys
        uasGuard(['data_companies_count']) {
            println UASUtils.toCSVLine("data_companies_count", "${CompanyLocalServiceUtil.getCompaniesCount()}", "")
        }
        uasGuard(['data_custom_application_display_templates_adt_count', 'data_custom_web_content_templates_wc_count']) {
            loadTemplates()
            println UASUtils.toCSVLine("data_custom_application_display_templates_adt_count", "${applicationDisplayTemplates.size()}", "")
            println UASUtils.toCSVLine("data_custom_web_content_templates_wc_count", "${webContentTemplates.size()}", "")
        }
        if (variant == '7.4') {
            println UASUtils.toCSVLine("data_ddm_contents_count", "", "n/a (deprecated in 7.4+)")
            uasGuard(['data_ddm_fields_count']) {
                println UASUtils.toCSVLine("data_ddm_fields_count", "${uasClass('com.liferay.dynamic.data.mapping.service.DDMFieldLocalServiceUtil').getDDMFieldsCount()}", "")
            }
        } else {
            uasGuard(['data_ddm_contents_count']) {
                println UASUtils.toCSVLine("data_ddm_contents_count", "${uasClass('com.liferay.dynamic.data.mapping.service.DDMContentLocalServiceUtil').getDDMContentsCount()}", "") // this question keys is not present in 7.4 script
            }
        }
        uasGuard(['data_ddm_structures_count']) {
            println UASUtils.toCSVLine("data_ddm_structures_count", "${DDMStructureLocalServiceUtil.getDDMStructuresCount()}", "")
        }
        uasGuard(['data_ddm_templates_count']) {
            println UASUtils.toCSVLine("data_ddm_templates_count", "${DDMTemplateLocalServiceUtil.getDDMTemplatesCount()}", "")
        }
        uasGuard(['data_dl_files_count']) {
            println UASUtils.toCSVLine("data_dl_files_count", "${DLFileEntryLocalServiceUtil.getDLFileEntriesCount()}", "")
        }
        if (variant != '7.0') {
            uasGuard(['data_fragments_count']) {
                println UASUtils.toCSVLine("data_fragments_count", "${uasClass('com.liferay.fragment.service.FragmentEntryLocalServiceUtil').getFragmentEntriesCount()}", "")
            }
        }
        uasGuard(['data_journal_articles_count']) {
            println UASUtils.toCSVLine("data_journal_articles_count", "${JournalArticleLocalServiceUtil.getJournalArticlesCount()}", "")
        }
        uasGuard(['data_layouts_count']) {
            println UASUtils.toCSVLine("data_layouts_count", "${countOf('layouts') { LayoutLocalServiceUtil.getLayoutsCount() }}", "")
        }
        uasGuard(['data_layouts_friendly_url_count']) {
            println UASUtils.toCSVLine("data_layouts_friendly_url_count", "${LayoutFriendlyURLLocalServiceUtil.getLayoutFriendlyURLsCount()}", "")
        }
        if (variant != '7.0') {
            uasGuard(['data_page_templates_count']) {
                println UASUtils.toCSVLine("data_page_templates_count", "${uasClass('com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalServiceUtil').getLayoutPageTemplateEntriesCount()}", "")
            }
        }
        uasGuard(['data_used_custom_portlets_count', 'data_used_liferay_portlets_count']) {
            // Distinct portlet IDs only, instead of loading every portlet preferences row into memory
            def portletIdQuery = PortletPreferencesLocalServiceUtil.dynamicQuery()
            portletIdQuery.setProjection(ProjectionFactoryUtil.distinct(ProjectionFactoryUtil.property('portletId')))
            def portletIds = PortletPreferencesLocalServiceUtil.dynamicQuery(portletIdQuery).collect {
                (it as String) - ~/_INSTANCE.*/
            }.unique().sort()
            def liferayPortlets = portletIds.findAll { it.contains('liferay') }
            def customPortlets = portletIds.findAll { !it.contains('liferay') }
            println UASUtils.toCSVLine("data_used_custom_portlets_count", "${customPortlets.size()}", "\n${customPortlets.collect { "      * ${it}" }.join('\n')}")
            println UASUtils.toCSVLine("data_used_liferay_portlets_count", "${liferayPortlets.size()}", "\n${liferayPortlets.collect { "      * ${it}" }.join('\n')}")
        }
        println UASUtils.toCSVLine("data_user_private_pages_enabled", "${com.liferay.portal.kernel.util.PropsUtil.get('layout.user.private.layouts.enabled')}", "")
        println UASUtils.toCSVLine("data_user_public_pages_enabled", "${com.liferay.portal.kernel.util.PropsUtil.get('layout.user.public.layouts.enabled')}", "")
        uasGuard(['data_users_count', 'data_users_layouts_count', 'data_custom_layouts_count']) {
            def usersCount = countOf('users') { UserLocalServiceUtil.getUsersCount() }
            def layoutsCount = countOf('layouts') { LayoutLocalServiceUtil.getLayoutsCount() }
            println UASUtils.toCSVLine("data_users_count", "${usersCount}", "")

            if (layoutsCount > usersCount) {
                println UASUtils.toCSVLine("data_users_layouts_count", "${(usersCount * 2)}", "")
                println UASUtils.toCSVLine("data_custom_layouts_count", "${(layoutsCount - (usersCount * 2))}", "")
            }
        }

        // There may be many more, but ROM calculator only asks about VM and FTL, so only make sure these are put as CSV lines
        def ddmTemplatesSupportedLangs = [TemplateConstants.LANG_TYPE_VM, TemplateConstants.LANG_TYPE_FTL]
        uasGuard(ddmTemplatesSupportedLangs.collect { "data_wc_templates_by_lang_${it}_count".toString() } +
                ddmTemplatesSupportedLangs.collect { "data_adt_templates_by_lang_${it}_count".toString() }) {
            loadTemplates()
            def webContentTemplatesByLang = webContentTemplates.groupBy { it.language }
            ddmTemplatesSupportedLangs.each {
                webContentTemplatesByLang.putIfAbsent(it, Collections.emptyList())
            }
            def adtTemplatesByLang = applicationDisplayTemplates.groupBy { it.language }
            ddmTemplatesSupportedLangs.each {
                adtTemplatesByLang.putIfAbsent(it, Collections.emptyList())
            }

            webContentTemplatesByLang.each { lang, templates ->
                println UASUtils.toCSVLine(
                        "data_wc_templates_by_lang_${lang}_count", templates.size(),
                        "Custom WC templates with '${lang}' as the language:\n${templates.collect { " - ${it.nameCurrentValue} { templateKey=${it.templateKey}, templateId=${it.templateId} }" }.join('\n')}")
            }

            adtTemplatesByLang.each { lang, templates ->
                println UASUtils.toCSVLine(
                        "data_adt_templates_by_lang_${lang}_count", templates.size(),
                        "Custom ADT templates with '${lang}' as the language:\n${templates.collect { " - ${it.nameCurrentValue} { templateKey=${it.templateKey}, templateId=${it.templateId} }" }.join('\n')}")
            }
        }

        def liveGroups = [:]
        def liveGroupOf = { group ->
            if (!liveGroups.containsKey(group.groupId)) {
                def liveGroup = group
                try {
                    liveGroup = StagingUtil.getLiveGroup(group) ?: group
                } catch (Throwable ignored) {
                    try {
                        liveGroup = StagingUtil.getLiveGroup(group.groupId) ?: group
                    } catch (Throwable ignored2) {
                    }
                }
                liveGroups[group.groupId] = liveGroup
            }
            return liveGroups[group.groupId]
        }

        uasGuard(['data_sites_count', 'data_sites_details']) {
            def siteDetails = '' << ''
            def childGroupCount = 0

            siteDetails <<= "type, companyId, groupId, friendlyURL, name, staging, parent site name"

            loadSites().each { site ->
                def parentGroupName = "N/A"
                def type = "PARENT_GROUP"
                def parentGroupId = site.parentGroupId

                if (!parentGroupId == 0) {
                    type = "CHILD_GROUP"
                    parentGroupName = GroupLocalServiceUtil.getGroup(parentGroupId).getName()
                    childGroupCount++
                }

                siteDetails <<= "\n\t${type}, ${site.companyId}, ${site.groupId}, ${site.friendlyURL}, ${site.nameCurrentValue}, ${liveGroupOf(site).staged}, ${parentGroupName}"
            }

            // data_sites_count
            def siteSize = sites.size()
            println UASUtils.toCSVLine('data_sites_count', "${siteSize}", "${siteDetails.toString()}")
            println UASUtils.toCSVLine('data_sites_details', "", "${siteSize - childGroupCount} Parent Sites, ${childGroupCount} Child Sites")
        }

        uasGuard(['data_staging_enabled']) {
            // data_staging_enabled
            println UASUtils.toCSVLine('data_staging_enabled', "${loadSites().any { liveGroupOf(it).staged }}", 'see data_sites_count for more details')
        }

        if (variant == '7.3') {
            uasGuard(['data_app_builder_apps_count']) {
                // data_app_builder_apps_count
                def appBuilderClass = uasClass('com.liferay.app.builder.service.AppBuilderAppLocalServiceUtil')
                println UASUtils.toCSVLine('data_app_builder_apps_count', appBuilderClass ? "${appBuilderClass.getAppBuilderAppsCount()}" : '',
                        appBuilderClass ? '' : 'n/a - App Builder not available')
            }
        }

        // data_file_store_method
        println UASUtils.toCSVLine('data_file_store_method', com.liferay.portal.kernel.util.PropsUtil.get('dl.store.impl'), "")
        // data_live_users_enabled
        println UASUtils.toCSVLine('data_live_users_enabled', com.liferay.portal.kernel.util.PropsUtil.get('live.users.enabled'), "")

        uasGuard(['data_google_maps_integration']) {
            // data_google_maps_integration
            def googleMapsIntegration = false
            def mapProviderKey = "MAP_PROVIDER_KEY"
            def googleMapsKey = "GoogleMaps"

            // Enabled via Site Settings - Advanced Tab (only groups whose settings mention the map provider)
            def mapQuery = GroupLocalServiceUtil.dynamicQuery()
            mapQuery.add(RestrictionsFactoryUtil.like('typeSettings', "%${mapProviderKey}%".toString()))
            for (def group : GroupLocalServiceUtil.dynamicQuery(mapQuery)) {
                def typeSettings = group.getTypeSettingsProperty(mapProviderKey)

                if (!Validator.isBlank(typeSettings) && typeSettings == googleMapsKey) {
                    googleMapsIntegration = true
                }
            }

            // Enabled via Instance Settings --> Miscellaneous
            for (def companyId : companyIds) {
                def companyPortletPreferences = PrefsPropsUtil.getPreferences(companyId)

                def mapsIntegrationProperty = companyPortletPreferences.getValue(mapProviderKey, null)

                if (mapsIntegrationProperty == googleMapsKey) {
                    googleMapsIntegration = true
                }
            }

            println UASUtils.toCSVLine('data_google_maps_integration', "${googleMapsIntegration}", "")
        }

        uasGuard(['expando_count', 'expando_tables_info']) {
            // Expando Fields
            int expandoTablesCount = 0
            String expandoTableDetails = "ExpandoClassName, ExpandoColumnsCount"
            List<ExpandoTable> expandoTables =
                    ExpandoTableLocalServiceUtil.getExpandoTables(QueryUtil.ALL_POS, QueryUtil.ALL_POS)

            for (e in expandoTables) {
                try {
                    String expandoClassName =
                            ClassNameLocalServiceUtil.fetchByClassNameId(e.getClassNameId()).getClassName()

                    ExpandoBridge expandoBridge =
                            ExpandoBridgeFactoryUtil.getExpandoBridge(CompanyThreadLocal.getCompanyId(), expandoClassName, 0L)

                    if (expandoBridge.getAttributes().size() > 0) {
                        expandoTableDetails <<= "\n\t${expandoClassName}, ${expandoBridge.getAttributes().size()}"
                        expandoTablesCount++
                    }
                } catch (Throwable ignored) {
                    // a single broken expando table must not hide the others
                }
            }

            println UASUtils.toCSVLine('expando_count', "${expandoTablesCount}", "")
            println UASUtils.toCSVLine('expando_tables_info', "", "${expandoTableDetails.toString()}")
        }

        if (variant == '7.4') {
            // Additional 7.4 / Quarterly Release data points
            [
                    [key: 'data_forms_count', className: 'com.liferay.dynamic.data.mapping.service.DDMFormInstanceLocalServiceUtil', method: 'getDDMFormInstancesCount', comment: 'Via DDMFormInstanceLocalServiceUtil::getDDMFormInstancesCount'],
                    [key: 'data_form_records_count', className: 'com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalServiceUtil', method: 'getDDMFormInstanceRecordsCount', comment: 'Via DDMFormInstanceRecordLocalServiceUtil::getDDMFormInstanceRecordsCount'],
                    [key: 'data_layout_page_template_structures_count', className: 'com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalServiceUtil', method: 'getLayoutPageTemplateStructuresCount', comment: 'Content pages / page templates / master pages structures, via LayoutPageTemplateStructureLocalServiceUtil'],
                    [key: 'data_asset_entries_count', className: 'com.liferay.asset.kernel.service.AssetEntryLocalServiceUtil', method: 'getAssetEntriesCount', comment: 'Via AssetEntryLocalServiceUtil::getAssetEntriesCount'],
                    [key: 'data_dl_file_versions_count', className: 'com.liferay.document.library.kernel.service.DLFileVersionLocalServiceUtil', method: 'getDLFileVersionsCount', comment: 'Via DLFileVersionLocalServiceUtil::getDLFileVersionsCount'],
                    [key: 'data_organizations_count', className: 'com.liferay.portal.kernel.service.OrganizationLocalServiceUtil', method: 'getOrganizationsCount', comment: 'Via OrganizationLocalServiceUtil::getOrganizationsCount'],
                    [key: 'data_user_groups_count', className: 'com.liferay.portal.kernel.service.UserGroupLocalServiceUtil', method: 'getUserGroupsCount', comment: 'Via UserGroupLocalServiceUtil::getUserGroupsCount'],
                    [key: 'data_roles_count', className: 'com.liferay.portal.kernel.service.RoleLocalServiceUtil', method: 'getRolesCount', comment: 'Via RoleLocalServiceUtil::getRolesCount'],
                    [key: 'data_virtual_hosts_count', className: 'com.liferay.portal.kernel.service.VirtualHostLocalServiceUtil', method: 'getVirtualHostsCount', comment: 'Via VirtualHostLocalServiceUtil::getVirtualHostsCount'],
            ].each { check ->
                uasGuard([check.key]) {
                    def clazz = uasClass(check.className)
                    if (clazz == null) {
                        println UASUtils.toCSVLine(check.key, '', "n/a - ${check.className} not available in this release")
                    } else {
                        println UASUtils.toCSVLine(check.key, "${clazz."${check.method}"()}", check.comment)
                    }
                }
            }
        }
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '2_data.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')

        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__2_data_groovy()

// the function 'invoke__2_data_groovy' will be invoked at the very end of the '@@FILE_NAME@@'
// invoke__2_data_groovy([ debug: false ])
// END included '2_data.groovy'
