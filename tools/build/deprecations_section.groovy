// BEGIN included '6_deprecations.groovy'
// Usage of features that Liferay has deprecated, archived, removed or put in maintenance mode, based on the
// "Maintenance Mode and Deprecations" and "Breaking Changes" pages on learn.liferay.com for 7.3, 7.4 and the
// Quarterly Releases up to 2026.Q3.
// Read-only and self-contained: works on Liferay 7.0 and later; checks for features that do not exist in the
// running release report 0 / n/a instead of failing.

// Wrapper function for '6_deprecations.groovy'
def invoke__6_deprecations_groovy(Map<String, Object> uasContext) {

    try {
        def found = []

        def report = { String key, Object count, String status, String details ->
            def n = (count instanceof Number) ? count : null
            println UASUtils.toCSVLine("deprecation_${key}", count == null ? '' : count, "${status}${details ? '\n' + details : ''}")
            if (n != null && n > 0) {
                found << "${key} (${n}): ${status}"
            }
        }

        // OSGi access: SystemBundleUtil (7.1+) or the bundle of an OSGi API class (7.0)
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

        // Loads a class from portal-kernel or from any ACTIVE bundle of the same application
        def loadClass = { String className ->
            try {
                return Class.forName(className)
            } catch (Throwable ignored) {
            }
            if (bundleContext == null) {
                return null
            }
            def parts = className.tokenize('.')
            def appPrefix = parts.size() > 3 ? parts[0..3].join('.') : className
            def candidates = bundleContext.bundles.findAll {
                it.state == org.osgi.framework.Bundle.ACTIVE && it.symbolicName && it.symbolicName.startsWith(appPrefix)
            }
            for (candidate in candidates) {
                try {
                    return candidate.loadClass(className)
                } catch (Throwable ignored) {
                }
            }
            return null
        }

        // Calls a static count method on the first class that exists, null when the feature is not available
        def countVia = { List<String> classNames, String method ->
            for (className in classNames) {
                def clazz = loadClass(className)
                if (clazz != null) {
                    try {
                        return clazz."${method}"()
                    } catch (Throwable ignored) {
                    }
                }
            }
            return null
        }

        def naIfNull = { value -> value == null ? 'n/a (not available in this release)' : null }

        // ------------------------------------------------------------------
        // 1. Deprecated widgets placed on pages
        // ------------------------------------------------------------------
        def widgetChecks = [
                [key: 'widget_message_boards', patterns: ['message_boards_web_portlet_mbportlet'], status: 'Message Boards: deprecated in 2024.Q4, no direct replacement'],
                [key: 'widget_wiki', patterns: ['wiki_web_portlet_wikiportlet', 'wiki_web_portlet_wikidisplayportlet'], status: 'Wiki: deprecated in 2024.Q4 (deprecation feature flag LPD-35013); transition to Liferay Objects or Web Content'],
                [key: 'widget_microblogs', patterns: ['microblogs_web_portlet'], status: 'Microblogs: deprecated in 2024.Q4, no direct replacement'],
                [key: 'widget_questions', patterns: ['questions_web'], status: 'Questions widget: deprecated in 2024.Q4, no direct replacement'],
                [key: 'widget_activities', patterns: ['social_activities_web_portlet', 'activities_web_portlet'], status: 'Activities widget: deprecated in 2024.Q4'],
                [key: 'widget_contacts', patterns: ['contacts_web_portlet'], status: 'Contacts Center / Members / My Contacts / Profile: maintenance mode in 7.4; Contact Center widget deprecated in 2024.Q4, no direct replacement'],
                [key: 'widget_group_statistics', patterns: ['group_statistics_web'], status: 'Group Statistics widget: deprecated in 2024.Q4'],
                [key: 'widget_invite_members', patterns: ['invite_members_web'], status: 'Invite Members widget: deprecated in 2024.Q4'],
                [key: 'widget_media_gallery', patterns: ['igdisplayportlet'], status: 'Media Gallery widget: deprecated in 2024.Q4'],
                [key: 'widget_subscriptions', patterns: ['subscription'], status: 'My Subscriptions / Subscription Manager widgets: deprecated in 2024.Q4'],
                [key: 'widget_shared_with_me', patterns: ['sharedassets', 'shared_assets', 'sharedwithme'], status: 'Shared With Me widget: deprecated in 2024.Q4'],
                [key: 'widget_sites_directory', patterns: ['sitesdirectory', 'sites_directory'], status: 'Sites Directory widget: deprecated in 2024.Q4'],
                [key: 'widget_xsl_content', patterns: ['xsl_content_web'], status: 'XSL Content widget: deprecated in 2024.Q4'],
                [key: 'widget_most_viewed_assets', patterns: ['mostviewedassets', 'most_viewed_assets'], status: 'Most Viewed Assets widget: deprecated in 2024.Q4; use the Asset Publisher with the Most Viewed Assets collection provider'],
                [key: 'widget_walkthrough', patterns: ['walkthrough'], status: 'Walkthrough: deprecated in 2024.Q4, no direct replacement'],
                [key: 'widget_social_networking', patterns: ['social_networking'], status: 'Social Networking plugin: deprecated in 2024.Q4, no direct replacement'],
                [key: 'widget_web_content_search', patterns: ['journal_content_search_web'], status: 'Web Content Search widget: archived in 7.4, no direct replacement'],
                [key: 'widget_dynamic_data_lists', patterns: ['dynamic_data_lists_web_portlet_ddldisplayportlet', 'dynamic_data_lists_web_portlet_ddlportlet'], status: 'Dynamic Data Lists widgets: deprecated in 7.3, replaced by Objects'],
                [key: 'widget_kaleo_forms', patterns: ['kaleo_forms_web'], status: 'Kaleo Forms: deprecated in 2024.Q3, replaced by business processes for Objects (objects, workflow, form container fragments)'],
                [key: 'widget_bookmarks', patterns: ['bookmarks_web'], status: 'Bookmarks: archived in 7.3, no direct replacement'],
                [key: 'widget_flash', patterns: ['flash_web'], status: 'Flash widget: removed in 7.3, no direct replacement'],
                [key: 'widget_classic_search', patterns: ['com_liferay_portal_search_web_portlet_searchportlet'], status: 'Search widget (classic): deprecated in 7.1, replaced by the Search widgets'],
                [key: 'widget_polls', patterns: ['polls_web', 'pollsdisplay'], status: 'Polls: deprecated in 7.3, removed in 7.4, replaced by Forms'],
                [key: 'widget_asset_publisher', patterns: ['asset_publisher_web_portlet_assetpublisherportlet'], status: 'Asset Publisher: maintenance mode in 2024.Q4; its manual and dynamic selection options are deprecated, replaced by Collections'],
        ]

        def projections = com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil
        def restrictions = com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil
        def layoutService = com.liferay.portal.kernel.service.LayoutLocalServiceUtil

        try {
            def preferencesService = com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil
            def matchesCheck = { String portletId ->
                def id = portletId.toLowerCase()
                id.startsWith('com_liferay_') && widgetChecks.any { check -> check.patterns.any { id.contains(it) } }
            }

            def portletIdToPlids = [:]
            def addPlid = { String portletId, long plid ->
                def root = (portletId.trim() - ~/_INSTANCE.*/).toLowerCase()
                def plids = portletIdToPlids[root]
                if (plids == null) {
                    plids = [] as Set
                    portletIdToPlids[root] = plids
                }
                plids << plid
            }

            // Widgets placed on pages (ownerType 3 = PortletKeys.PREFS_OWNER_TYPE_LAYOUT): the distinct portlet IDs
            // first, then the pages of the deprecated ones only, instead of loading every preferences row
            def idQuery = preferencesService.dynamicQuery()
            idQuery.add(restrictions.eq('ownerType', 3))
            idQuery.setProjection(projections.distinct(projections.property('portletId')))
            preferencesService.dynamicQuery(idQuery).collect { it as String }.findAll { matchesCheck(it) }.each { portletId ->
                def plidQuery = preferencesService.dynamicQuery()
                plidQuery.add(restrictions.eq('ownerType', 3))
                plidQuery.add(restrictions.eq('portletId', portletId))
                plidQuery.setProjection(projections.distinct(projections.property('plid')))
                preferencesService.dynamicQuery(plidQuery).each { plid ->
                    if ((plid as long) > 0) {
                        addPlid(portletId, plid as long)
                    }
                }
            }

            // Widgets placed on widget pages are also stored in the page layout (column-N=portletId,...), even when
            // no preferences exist yet (e.g. a deprecated widget that is hidden and never rendered). Read in batches.
            def widgetPagesQuery = { ->
                def query = layoutService.dynamicQuery()
                query.add(restrictions.eq('type', 'portlet'))
                query
            }
            long widgetPages = layoutService.dynamicQueryCount(widgetPagesQuery())
            int batchSize = 500
            for (int batchStart = 0; batchStart < widgetPages; batchStart += batchSize) {
                def query = widgetPagesQuery()
                query.setProjection(projections.projectionList()
                        .add(projections.property('plid'))
                        .add(projections.property('typeSettings')))
                query.addOrder(com.liferay.portal.kernel.dao.orm.OrderFactoryUtil.asc('plid'))
                layoutService.dynamicQuery(query, batchStart, batchStart + batchSize).each { row ->
                    long plid = row[0] as long
                    (row[1] ?: '').toString().readLines().each { line ->
                        int separator = line.indexOf('=')
                        if (separator > 0) {
                            def key = line.substring(0, separator)
                            if (key.startsWith('column-') || key.startsWith('nested-column-')) {
                                line.substring(separator + 1).tokenize(',').each { portletId ->
                                    if (portletId.trim() && matchesCheck(portletId.trim())) {
                                        addPlid(portletId, plid)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Ignore widgets placed in the Control Panel
            def controlPanelPlids = [] as Set
            portletIdToPlids.values().flatten().unique().each { plid ->
                try {
                    def layout = layoutService.fetchLayout(plid)
                    if (layout == null || layout.getGroup().isControlPanel()) {
                        controlPanelPlids << plid
                    }
                } catch (Throwable ignored) {
                }
            }

            widgetChecks.each { check ->
                def matchingIds = portletIdToPlids.keySet().findAll { id -> id.startsWith('com_liferay_') && check.patterns.any { id.contains(it) } }
                def pages = matchingIds.collect { portletIdToPlids[it] }.flatten().findAll { !(it in controlPanelPlids) }.unique()
                report("${check.key}_pages_count", pages.size(), check.status,
                        matchingIds ? "Pages using it: ${pages.size()}, widget IDs: ${matchingIds.sort().join(', ')}" : '')
            }
        } catch (Throwable t) {
            report('widgets_pages_count', null, "n/a - ${t}", '')
        }

        // ------------------------------------------------------------------
        // 2. Page types, private pages, page templates, site templates, themes
        // ------------------------------------------------------------------
        try {
            def siteQuery = com.liferay.portal.kernel.service.GroupLocalServiceUtil.dynamicQuery()
            siteQuery.add(restrictions.eq('site', true))
            def siteGroupIds = com.liferay.portal.kernel.service.GroupLocalServiceUtil.dynamicQuery(siteQuery).findAll {
                !it.isControlPanel() && !it.isUser()
            }*.groupId as Set

            // Counted in the database, grouped by site / type / private (and system, 7.2+: drafts, master and template pages)
            def countRows = { boolean withSystem ->
                def query = layoutService.dynamicQuery()
                def projectionList = projections.projectionList()
                        .add(projections.groupProperty('groupId'))
                        .add(projections.groupProperty('type'))
                        .add(projections.groupProperty('privateLayout'))
                if (withSystem) {
                    projectionList.add(projections.groupProperty('system'))
                }
                projectionList.add(projections.rowCount())
                query.setProjection(projectionList)
                layoutService.dynamicQuery(query)
            }
            def rows
            boolean hasSystem = true
            try {
                rows = countRows(true)
            } catch (Throwable ignored) {
                hasSystem = false
                rows = countRows(false)
            }

            def typeCounts = [:].withDefault { 0 }
            def privateSitePages = 0
            rows.each { row ->
                def system = hasSystem ? (row[3] as boolean) : false
                long count = (hasSystem ? row[4] : row[3]) as long
                if ((row[0] as long) in siteGroupIds && !system) {
                    typeCounts[row[1]] = typeCounts[row[1]] + count
                    if (row[2] as boolean) {
                        privateSitePages += count
                    }
                }
            }

            println UASUtils.toCSVLine('data_layout_types', typeCounts.values().sum(0),
                    "Site pages by type (excluding user personal pages and system pages):\n${typeCounts.collect { " - ${it.key}: ${it.value}" }.join('\n')}")

            report('widget_pages_count', typeCounts['portlet'], 'Widget pages: maintenance mode in 2024.Q4, deprecated in 2026.Q3 (LPD-76864); convert them to content pages with the built-in converter', '')
            report('panel_pages_count', typeCounts['panel'], 'Panel pages: deprecated in 2026.Q3 (LPD-76864), no automatic migration; rebuild as one or more content pages', '')
            report('full_page_application_pages_count', typeCounts['full_page_application'], 'Full Page Application pages: deprecated in 2026.Q3 (LPD-76864), no automatic migration; rebuild as content pages', '')
            report('collection_pages_count', typeCounts['collection'], 'Collection pages: removed in 2025.Q1; existing ones are replaced by content pages with a Collection Display during the upgrade', '')
            report('private_site_pages_count', privateSitePages, 'Private pages: deprecated in 2024.Q4 (deprecation feature flag LPD-38869), no direct replacement', '')
        } catch (Throwable t) {
            report('pages_count', null, "n/a - ${t}", '')
        }

        report('widget_page_templates_count',
                countVia(['com.liferay.portal.kernel.service.LayoutPrototypeLocalServiceUtil'], 'getLayoutPrototypesCount'),
                'Widget page templates: deprecated in 2026.Q3 (LPD-76864), no automatic migration; rebuild as content page templates', '')

        try {
            def dynamicQuery = com.liferay.portal.kernel.service.LayoutSetLocalServiceUtil.dynamicQuery()
            dynamicQuery.add(com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil.eq('layoutSetPrototypeLinkEnabled', true))
            report('site_template_propagation_count', com.liferay.portal.kernel.service.LayoutSetLocalServiceUtil.dynamicQueryCount(dynamicQuery),
                    'Sites with site template propagation enabled: site template propagation is no longer available from 2026.Q1; changes must be propagated manually', '')
        } catch (Throwable t) {
            report('site_template_propagation_count', null, "n/a - ${t}", '')
        }

        try {
            def themeUsage = com.liferay.portal.kernel.service.LayoutSetLocalServiceUtil.getLayoutSets(-1, -1).countBy { it.themeId }
            println UASUtils.toCSVLine('data_themes_in_use', themeUsage.size(),
                    "Theme per site page set (public / private):\n${themeUsage.collect { " - ${it.key}: ${it.value}" }.join('\n')}")
            report('dialect_theme_count', themeUsage.findAll { it.key?.toLowerCase()?.contains('dialect') }.values().sum(0),
                    'Dialect theme: archived in 2026.Q3 (not in the default build); use the Classic theme and rebuild customizations against its tokens', '')
            def archivedThemes = themeUsage.findAll { t -> ['hello_world', 'helloworld', 'fjord', 'porygon', 'westeros'].any { (t.key ?: '').toLowerCase().contains(it) } }
            report('archived_themes_count', archivedThemes.values().sum(0),
                    'Archived themes in use (Hello World: archived in 7.4; Fjord, Porygon, Westeros: archived in 7.3)',
                    archivedThemes.collect { " - ${it.key}: ${it.value}" }.join('\n'))
        } catch (Throwable t) {
            report('dialect_theme_count', null, "n/a - ${t}", '')
        }

        // Deprecation feature flags that are switched on: each one keeps a deprecated feature available
        try {
            def ffReference = bundleContext?.getServiceReference('com.liferay.portal.kernel.feature.flag.FeatureFlagManager')
            if (ffReference == null) {
                report('feature_flags_enabled_count', null, 'Deprecation feature flags: n/a (feature flags not available in this release)', '')
            } else {
                def ffManager = bundleContext.getService(ffReference)
                try {
                    def isDeprecation = { f -> f.featureFlagType.name().equalsIgnoreCase('deprecation') } as java.util.function.Predicate
                    def lines = []
                    com.liferay.portal.kernel.service.CompanyLocalServiceUtil.getCompanies().each { company ->
                        ffManager.getFeatureFlags(company.companyId, isDeprecation).findAll { it.isEnabled() }.sort { it.key }.each { flag ->
                            def title = ''
                            try {
                                title = flag.getTitle(company.locale) ?: ''
                            } catch (Throwable ignored) {
                            }
                            lines << " - ${company.webId} | ${flag.key} | ${title}"
                        }
                    }
                    report('feature_flags_enabled_count', lines.size(),
                            'Deprecation feature flags switched on (each keeps a deprecated feature available; it is planned for removal)',
                            lines.join('\n'))
                } finally {
                    bundleContext.ungetService(ffReference)
                }
            }
        } catch (Throwable t) {
            report('feature_flags_enabled_count', null, "n/a - ${t}", '')
        }

        // ------------------------------------------------------------------
        // 3. Data stored in deprecated / removed applications
        // ------------------------------------------------------------------
        try {
            def mbClass = loadClass('com.liferay.message.boards.service.MBMessageLocalServiceUtil') ?:
                    loadClass('com.liferay.message.boards.kernel.service.MBMessageLocalServiceUtil')
            if (mbClass == null) {
                report('message_boards_messages_count', null, 'Message Boards: n/a (not available in this release)', '')
            } else {
                // categoryId -1 = comments (discussions), which are not Message Boards content
                def dynamicQuery = mbClass.dynamicQuery()
                dynamicQuery.add(com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil.ne('categoryId', -1L))
                report('message_boards_messages_count', mbClass.dynamicQueryCount(dynamicQuery),
                        'Message Boards messages (excluding comments): deprecated in 2024.Q4', '')
            }
        } catch (Throwable t) {
            report('message_boards_messages_count', null, "n/a - ${t}", '')
        }

        [
                [key: 'wiki_pages_count', classes: ['com.liferay.wiki.service.WikiPageLocalServiceUtil'], method: 'getWikiPagesCount', status: 'Wiki pages (all versions): Wiki deprecated in 2024.Q4'],
                [key: 'microblogs_entries_count', classes: ['com.liferay.microblogs.service.MicroblogsEntryLocalServiceUtil'], method: 'getMicroblogsEntriesCount', status: 'Microblogs entries: deprecated in 2024.Q4'],
                [key: 'ddl_record_sets_count', classes: ['com.liferay.dynamic.data.lists.service.DDLRecordSetLocalServiceUtil'], method: 'getDDLRecordSetsCount', status: 'Dynamic Data Lists (in 7.0 this also includes Forms): deprecated in 7.3, replaced by Objects'],
                [key: 'ddl_records_count', classes: ['com.liferay.dynamic.data.lists.service.DDLRecordLocalServiceUtil'], method: 'getDDLRecordsCount', status: 'Dynamic Data Lists records: deprecated in 7.3, replaced by Objects'],
                [key: 'kaleo_forms_processes_count', classes: ['com.liferay.portal.workflow.kaleo.forms.service.KaleoProcessLocalServiceUtil'], method: 'getKaleoProcessesCount', status: 'Kaleo Forms processes: deprecated in 2024.Q3'],
                [key: 'bookmarks_entries_count', classes: ['com.liferay.bookmarks.service.BookmarksEntryLocalServiceUtil'], method: 'getBookmarksEntriesCount', status: 'Bookmarks entries: archived in 7.3'],
                [key: 'forms_count', classes: ['com.liferay.dynamic.data.mapping.service.DDMFormInstanceLocalServiceUtil'], method: 'getDDMFormInstancesCount', status: 'Forms: maintenance mode in 2024.Q4; use the Form Container fragment if possible'],
                [key: 'custom_fields_count', classes: ['com.liferay.expando.kernel.service.ExpandoColumnLocalServiceUtil'], method: 'getExpandoColumnsCount', status: 'Custom Fields (expando columns, including default ones): maintenance mode in 2024.Q4; use Liferay Objects if possible'],
                [key: 'polls_questions_count', classes: ['com.liferay.polls.service.PollsQuestionLocalServiceUtil'], method: 'getPollsQuestionsCount', status: 'Polls: removed in 7.4, replaced by Forms'],
                [key: 'app_builder_apps_count', classes: ['com.liferay.app.builder.service.AppBuilderAppLocalServiceUtil'], method: 'getAppBuilderAppsCount', status: 'App Builder: removed in 7.4, replaced by Objects'],
                [key: 'knowledge_base_articles_count', classes: ['com.liferay.knowledge.base.service.KBArticleLocalServiceUtil'], method: 'getKBArticlesCount', status: 'Knowledge Base articles: maintenance mode in 2026.Q1; consider the Liferay CMS'],
                [key: 'blogs_entries_count', classes: ['com.liferay.blogs.service.BlogsEntryLocalServiceUtil', 'com.liferay.blogs.kernel.service.BlogsEntryLocalServiceUtil'], method: 'getBlogsEntriesCount', status: 'Blogs entries: maintenance mode in 2026.Q1; consider the Liferay CMS'],
        ].each { check ->
            def count = countVia(check.classes, check.method)
            report(check.key, count, check.status, naIfNull(count))
        }


        // Segments created locally in DXP (deprecated in 2026.Q1, use Analytics Cloud)
        try {
            def segmentsClass = loadClass('com.liferay.segments.service.SegmentsEntryLocalServiceUtil')
            def segments = segmentsClass ? segmentsClass.getSegmentsEntries(-1, -1).findAll { (it.segmentsEntryKey ?: '') != 'DEFAULT' } : null
            report('local_segments_count', segments?.size(), 'Segments authored locally in DXP: deprecated in 2026.Q1 (deprecation period of at least one year); use Analytics Cloud for segmentation', segments == null ? naIfNull(null) : '')
        } catch (Throwable t) {
            report('local_segments_count', null, "n/a - ${t}", '')
        }

        // Object 1:1 relationships (support within Form Container fragments deprecated in 2025.Q3)
        try {
            def relationshipsClass = loadClass('com.liferay.object.service.ObjectRelationshipLocalServiceUtil')
            def oneToOne = relationshipsClass ? relationshipsClass.getObjectRelationships(-1, -1).findAll { it.type == 'oneToOne' } : null
            report('object_one_to_one_relationships_count', oneToOne?.size(), 'Object 1:1 relationships: support within Form Container fragments deprecated in 2025.Q3, no direct replacement', oneToOne == null ? naIfNull(null) : '')
        } catch (Throwable t) {
            report('object_one_to_one_relationships_count', null, "n/a - ${t}", '')
        }

        // Widgets embedded in fragments (deprecated in 2024.Q4: add widgets directly to content pages)
        try {
            def fragmentLinkClass = loadClass('com.liferay.fragment.service.FragmentEntryLinkLocalServiceUtil')
            if (fragmentLinkClass == null) {
                report('fragments_embedded_widgets_count', null, 'Fragments: n/a (not available in this release)', '')
            } else {
                def dynamicQuery = fragmentLinkClass.dynamicQuery()
                dynamicQuery.add(com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil.like('html', '%<lfr-widget-%'))
                report('fragments_embedded_widgets_count', fragmentLinkClass.dynamicQueryCount(dynamicQuery),
                        'Fragments on pages that embed widgets (<lfr-widget-...>): deprecated in 2024.Q4; add widgets directly to content pages', '')
            }
        } catch (Throwable t) {
            report('fragments_embedded_widgets_count', null, "n/a - ${t}", '')
        }

        // Portal properties of deprecated features
        [
                [key: 'live_users_enabled', property: 'live.users.enabled', status: 'Live Users: deprecated in 7.2, replaced by Analytics Cloud'],
                [key: 'soap_web_services_enabled', property: 'axis.servlet.enabled', status: 'SOAP Web Services: deprecated (disabled by default in 7.4), replaced by JSON web services, REST and GraphQL'],
        ].each { check ->
            def value = com.liferay.portal.kernel.util.PropsUtil.get(check.property)
            report(check.key, value == 'true' ? 1 : 0, "${check.status} (${check.property}=${value})", '')
        }

        // Velocity in workflow definitions (Velocity no longer available in workflow from 2025.Q4)
        try {
            def kaleoClass = loadClass('com.liferay.portal.workflow.kaleo.service.KaleoDefinitionLocalServiceUtil')
            if (kaleoClass == null) {
                report('workflow_velocity_definitions_count', null, 'Workflow: n/a (not available in this release)', '')
            } else {
                def velocityDefinitions = kaleoClass.getKaleoDefinitions(-1, -1).findAll {
                    (it.content ?: '').toLowerCase().contains('<template-language>velocity')
                }
                report('workflow_velocity_definitions_count', velocityDefinitions.size(),
                        'Workflow definitions with Velocity templates: Velocity is no longer available in workflow from 2025.Q4; use FreeMarker',
                        velocityDefinitions.collect { " - ${it.name} v${it.version}" }.join('\n'))
            }
        } catch (Throwable t) {
            report('workflow_velocity_definitions_count', null, "n/a - ${t}", '')
        }

        // Velocity (VM) templates for web content / widgets
        try {
            def vmTemplates = com.liferay.dynamic.data.mapping.service.DDMTemplateLocalServiceUtil.getDDMTemplates(-1, -1).findAll {
                it.language == 'vm'
            }
            report('velocity_templates_count', vmTemplates.size(),
                    'Velocity (vm) web content / widget templates: migrate to FreeMarker (see data_wc_templates_by_lang_vm_count, data_adt_templates_by_lang_vm_count)', '')
        } catch (Throwable t) {
            report('velocity_templates_count', null, "n/a - ${t}", '')
        }

        // ------------------------------------------------------------------
        // 4. Authentication / SSO integrations that are archived or deprecated
        // ------------------------------------------------------------------
        try {
            def configurationAdminReference = bundleContext?.getServiceReference('org.osgi.service.cm.ConfigurationAdmin')
            if (configurationAdminReference == null) {
                report('sso_integrations_enabled_count', null, 'n/a - ConfigurationAdmin not available', '')
            } else {
                def configurationAdmin = bundleContext.getService(configurationAdminReference)
                try {
                    def ssoTypes = [
                            '.sso.cas.'       : 'CAS (archived in 7.4; use SAML, OpenID Connect or token-based SSO)',
                            '.sso.ntlm.'      : 'NTLM (archived in 7.4; replaced by Kerberos)',
                            '.sso.opensso.'   : 'OpenSSO / OpenAM (all versions of OpenAM deprecated in 2024.Q4)',
                            '.sso.openam.'    : 'OpenAM (all versions deprecated in 2024.Q4)',
                            '.sso.google.'    : 'Google Login / SSO (archived in 7.4; use SAML, OpenID Connect or token-based SSO)',
                            '.sso.openid.'    : 'OpenID (deprecated in 7.2, replaced by OpenID Connect)',
                            '.oauth.'         : 'OAuth 1.0a (archived in 7.4; replaced by OAuth 2.0)',
                    ]
                    def enabled = []
                    (configurationAdmin.listConfigurations(null) ?: []).each { configuration ->
                        def pid = (configuration.pid ?: '').toLowerCase()
                        def match = ssoTypes.find { pid.contains(it.key) && !pid.contains('.oauth2.') && !pid.contains('.openid.connect.') }
                        if (match) {
                            def properties = configuration.properties
                            def isEnabled = properties?.get('enabled')
                            if (isEnabled != null && isEnabled.toString() == 'true') {
                                enabled << "${match.value} { pid=${configuration.pid} }"
                            }
                        }
                    }
                    report('sso_integrations_enabled_count', enabled.size(),
                            'Archived / deprecated authentication integrations that are enabled', enabled.collect { " - ${it}" }.join('\n'))

                    def akismet = (configurationAdmin.listConfigurations('(service.pid=*akismet*)') ?: []).findAll {
                        def apiKey = it.properties?.get('apiKey')
                        apiKey != null && apiKey.toString().trim()
                    }
                    report('akismet_configured_count', akismet.size(), 'Akismet spam filtering: deprecated in 2024.Q4, no direct replacement', '')

                    def openOfficeEnabled = com.liferay.portal.kernel.util.PropsUtil.get('openoffice.server.enabled') == 'true' ||
                            (configurationAdmin.listConfigurations('(service.pid=*OpenOffice*)') ?: []).any { it.properties?.get('serverEnabled')?.toString() == 'true' }
                    report('openoffice_integration_enabled', openOfficeEnabled ? 1 : 0,
                            'LibreOffice / OpenOffice document conversion enabled: Apache OpenOffice support deprecated in 2026.Q3, LibreOffice is the supported replacement (same settings)', '')
                } finally {
                    bundleContext.ungetService(configurationAdminReference)
                }
            }
        } catch (Throwable t) {
            report('sso_integrations_enabled_count', null, "n/a - ${t}", '')
        }

        // ------------------------------------------------------------------
        // 5. Platform: Java, application server, search engine, archived modules
        // ------------------------------------------------------------------
        def javaSpec = System.getProperty('java.specification.version', '')
        def javaMajor = javaSpec.startsWith('1.') ? javaSpec.substring(2) : javaSpec
        report('java_version_count', (javaMajor.isInteger() && javaMajor.toInteger() < 17) ? 1 : 0,
                "Java ${System.getProperty('java.version')}: JDK 8 support ended with the 2024.Q4 release (7.4 must then use JDK 11); Quarterly Releases require JDK 17 or 21 (2025.Q1 breaking changes)", '')

        try {
            def appServerIssue = ''
            if (com.liferay.portal.kernel.util.ServerDetector.isTomcat()) {
                def releaseNotes = new File(System.getProperty('catalina.home'), 'RELEASE-NOTES')
                def tomcatVersion = releaseNotes.canRead() ? (releaseNotes.text.find(/Apache Tomcat Version (\d+)/) { all, major -> major }) : null
                if (tomcatVersion != null && tomcatVersion.toInteger() < 10) {
                    appServerIssue = "Apache Tomcat ${tomcatVersion}: not supported from 2025.Q3 (Jakarta EE 10); upgrade to Tomcat 10.1.x"
                }
            } else {
                appServerIssue = "${com.liferay.portal.kernel.util.ServerDetector.getServerId()}: JBoss EAP 7.4, WildFly 26.1 and WebLogic 14c are not supported from 2025.Q3 (Jakarta EE 10, use JBoss EAP 8.0 or a newer WildFly); WebSphere support ended with 2024.Q4. Check the compatibility matrix"
            }
            report('app_server_count', appServerIssue ? 1 : 0, appServerIssue ?: 'Application server: no known deprecation', '')
        } catch (Throwable t) {
            report('app_server_count', null, "n/a - ${t}", '')
        }

        try {
            def searchReference = bundleContext?.getServiceReference('com.liferay.portal.search.engine.SearchEngineInformation')
            def searchIssue = ''
            def searchDetails = 'n/a'
            if (searchReference != null) {
                def searchEngineInformation = bundleContext.getService(searchReference)
                try {
                    def vendor = searchEngineInformation.vendorString ?: ''
                    def version = searchEngineInformation.clientVersionString ?: ''
                    searchDetails = "${vendor} ${version}"
                    if (vendor.toLowerCase().contains('solr')) {
                        searchIssue = 'Solr connector: maintenance mode in 7.4; use Elasticsearch / OpenSearch'
                    } else if (version.startsWith('7.') || version.startsWith('6.')) {
                        searchIssue = "Elasticsearch ${version}: Elasticsearch 7 has reached end-of-life and is not supported from 2026.Q1; upgrade to Elasticsearch 8"
                    }
                    if (vendor.toLowerCase().contains('sidecar') || vendor.toLowerCase().contains('embedded')) {
                        searchIssue = (searchIssue ? searchIssue + '; ' : '') + 'Sidecar / embedded Elasticsearch is not supported for production'
                    }
                } finally {
                    bundleContext.ungetService(searchReference)
                }
            }
            report('search_engine_count', searchIssue ? 1 : 0, searchIssue ?: "Search engine: ${searchDetails}", '')
        } catch (Throwable t) {
            report('search_engine_count', null, "n/a - ${t}", '')
        }

        try {
            def archivedBundles = (bundleContext?.bundles ?: []).findAll {
                def bsn = it.symbolicName ?: ''
                it.state == org.osgi.framework.Bundle.ACTIVE && (bsn.startsWith('com.liferay.sync.') || bsn.contains('.solr'))
            }*.symbolicName.sort()
            report('archived_modules_active_count', archivedBundles.size(),
                    'Active modules of archived / maintenance features (Liferay Sync: archived in 7.4; Solr connector: maintenance mode in 7.4)',
                    archivedBundles.collect { " - ${it}" }.join('\n'))
        } catch (Throwable t) {
            report('archived_modules_active_count', null, "n/a - ${t}", '')
        }

        // ------------------------------------------------------------------
        // 6. Custom code using removed / deprecated frontend and plugin technologies
        // ------------------------------------------------------------------
        try {
            def liferayHome = com.liferay.portal.kernel.util.PropsUtil.get('liferay.home')
            def moduleDir = com.liferay.portal.kernel.util.PropsUtil.get('module.framework.modules.dir') ?: "${liferayHome}/osgi/modules"
            def warDir = com.liferay.portal.kernel.util.PropsUtil.get('module.framework.war.dir') ?: "${liferayHome}/osgi/war"
            def customArtifacts = [moduleDir, warDir].collect { new File(it as String) }.findAll { it.isDirectory() }.collect {
                (it.listFiles() ?: []).findAll { f -> f.name.endsWith('.jar') || f.name.endsWith('.war') }
            }.flatten()

            def soy = [], npmBundler = [], auiNav = [], oauth2Js = [], javaxEE = []
            customArtifacts.each { File artifact ->
                def zip = null
                try {
                    zip = new java.util.zip.ZipFile(artifact)
                    def hasSoy = false, hasNpm = false, hasAuiNav = false, hasOAuth2 = false
                    def hasJavax = false
                    def manifestEntry = zip.getEntry('META-INF/MANIFEST.MF')
                    if (manifestEntry != null) {
                        def manifest = zip.getInputStream(manifestEntry).getText('UTF-8').replaceAll(/\r?\n /, '')
                        if (manifest.contains('Liferay-JS-Config')) {
                            hasNpm = true
                        }
                        def imports = manifest.find(/(?m)^Import-Package: .*$/) ?: ''
                        if (imports.contains('javax.servlet') || imports.contains('javax.portlet')) {
                            hasJavax = true
                        }
                    }
                    def webXml = zip.getEntry('WEB-INF/web.xml')
                    if (webXml != null) {
                        def text = zip.getInputStream(webXml).getText('UTF-8')
                        if (text.contains('java.sun.com/xml/ns') || text.contains('xmlns.jcp.org/xml/ns')) {
                            hasJavax = true
                        }
                    }
                    zip.entries().each { entry ->
                        def name = entry.name
                        if (name.endsWith('.soy')) {
                            hasSoy = true
                        } else if (!entry.isDirectory() && entry.size < 2000000 &&
                                (name.endsWith('.jsp') || name.endsWith('.jspf') || name.endsWith('.js') || name.endsWith('package.json'))) {
                            def text = zip.getInputStream(entry).getText('UTF-8')
                            if (name.endsWith('package.json') && text.contains('liferay-npm-bundler')) {
                                hasNpm = true
                            }
                            if (text.contains('<aui:nav ') || text.contains('<aui:nav>') || text.contains('<aui:nav-bar')) {
                                hasAuiNav = true
                            }
                            if (text.contains('Liferay.OAuth2')) {
                                hasOAuth2 = true
                            }
                        }
                    }
                    if (hasSoy) soy << artifact.name
                    if (hasNpm) npmBundler << artifact.name
                    if (hasAuiNav) auiNav << artifact.name
                    if (hasOAuth2) oauth2Js << artifact.name
                    if (hasJavax) javaxEE << artifact.name
                } catch (Throwable ignored) {
                } finally {
                    try {
                        zip?.close()
                    } catch (Throwable ignored) {
                    }
                }
            }

            report('custom_javax_modules_count', javaxEE.size(), 'Custom modules / WARs using Java EE (javax.servlet, javax.portlet): Liferay moved to Jakarta EE 10 in 2025.Q3; javax.* code must be migrated to jakarta.*', javaxEE.collect { " - ${it}" }.join('\n'))
            report('custom_soy_modules_count', soy.size(), 'Custom modules with Soy templates: Soy is no longer used in Liferay frontend builds (2026.Q3) and the Soy-based components were removed; rewrite with React or JSP', soy.collect { " - ${it}" }.join('\n'))
            report('custom_npm_bundler_modules_count', npmBundler.size(), 'Custom modules built with liferay-npm-bundler (AMD): npm bundler deprecated in 2024.Q4 and the AMD loader removed in 2025.Q2; use ECMAScript modules', npmBundler.collect { " - ${it}" }.join('\n'))
            report('custom_aui_nav_tags_count', auiNav.size(), 'Custom JSPs using aui:nav-bar / aui:nav-item / aui:nav-bar-search: tags removed in 2025.Q2; use Clay', auiNav.collect { " - ${it}" }.join('\n'))
            report('custom_liferay_oauth2_js_count', oauth2Js.size(), 'Custom JavaScript using Liferay.OAuth2: deprecated in 2025.Q2', oauth2Js.collect { " - ${it}" }.join('\n'))
        } catch (Throwable t) {
            report('custom_modules_scan_count', null, "n/a - ${t}", '')
        }

        try {
            def extPlugins = com.liferay.portal.plugin.PluginPackageUtil.getInstalledPluginPackages().findAll {
                ('ext' in it.types) || (it.context ?: '').endsWith('-ext')
            }*.moduleId.unique().sort()
            report('ext_plugins_count', extPlugins.size(), 'EXT plugins: support removed in 7.4; replaced by configuration settings and standard extension points', extPlugins.collect { " - ${it}" }.join('\n'))
        } catch (Throwable t) {
            report('ext_plugins_count', null, "n/a - ${t}", '')
        }

        // ------------------------------------------------------------------
        // Summary
        // ------------------------------------------------------------------
        println UASUtils.toCSVLine('deprecations_in_use_count', found.size(),
                "Deprecated, archived, removed or maintenance-mode features in use (details in the deprecation_* lines):\n${found.collect { " - ${it}" }.join('\n')}")
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '6_deprecations.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')

        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__6_deprecations_groovy()
// END included '6_deprecations.groovy'

