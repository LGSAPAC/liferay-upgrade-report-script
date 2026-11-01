"""Applies the robustness / performance changes to the 7.x sources (idempotent).

usage: harden.py <file>:<variant> ...   (variant: 7.0, 7.1, 7.2, 7.3, 7.4)
"""
import os, re, sys

GEN = os.path.dirname(os.path.abspath(__file__))
read = lambda name: open(os.path.join(GEN, name)).read()
HELPERS, DATA, DBINFO = read('helpers_section.groovy'), read('data_section.groovy'), read('dbinfo_section.groovy')


def replace_block(s, topic, new_block):
    begin = f"// BEGIN included '{topic}.groovy'"
    end = f"// END included '{topic}.groovy'"
    i = s.index(begin)
    j = s.index(end, i) + len(end)
    return s[:i] + new_block.rstrip('\n') + s[j:]


def region(s, topic):
    i = s.index(f"// BEGIN included '{topic}.groovy'")
    j = s.index(f"// END included '{topic}.groovy'", i)
    return i, j


def guard_custom_code(s):
    """Wraps the independent parts of 4_custom_code in uasGuard blocks."""
    i, j = region(s, '4_custom_code')
    code = s[i:j]
    if 'uasGuard(' in code:
        return s
    groups = [
        (r"^[ \t]*def LIFERAY_HOOK_XML_WEBAPP_PATH", ['custom_code_hook_plugins_count']),
        (r'^[ \t]*def portletPlugins = ', ['custom_code_portlet_plugins_count', 'custom_code_portlets_count']),
        (r'^[ \t]*def liferayLayoutTemplatePackages', ['custom_code_layout_template_plugins_count', 'custom_code_layout_templates_count',
                                       'custom_code_layout_templates_by_lang_vm_count', 'custom_code_layout_templates_by_lang_ftl_count']),
        (r'^[ \t]*def customOSGiModulesDir', ['custom_code_osgi_modules_count']),
        (r'^[ \t]*def themePlugins', ['custom_code_theme_plugins_count', 'custom_code_themes_count', 'custom_code_themes_by_lang_vm_count',
                             'custom_code_themes_by_lang_ftl_count', 'custom_code_themes_by_lang_jsp_count']),
        (r'^[ \t]*def webPlugins', ['custom_code_web_plugins_count']),
        (r'^[ \t]*Map<String, String> hookContextToLiferayHookXmlContent', [
            'custom_code_hook_extensions_custom_jsp', 'custom_code_hook_extensions_servlet_filters',
            'custom_code_hook_extensions_struts_actions', 'custom_code_hook_extensions_indexer_post_processors',
            'custom_code_hook_extensions_service_wrappers', 'custom_code_hook_extensions_override_portal_properties',
            'custom_code_hook_extensions_application_adapters', 'custom_code_hook_extensions_override_language_properties']),
        (r'^[ \t]*def releases = ReleaseLocalServiceUtil', ['custom_code_service_builder_modules']),
        (r'^[ \t]*def collectFiles\s*$', ['custom_code_fragment_hosts_count', 'custom_code_fragment_hosts_info']),
    ]
    starts = []
    for pattern, keys in groups:
        m = re.search(pattern, code, re.M)
        assert m, pattern
        starts.append((m.start(), keys))
    end_m = re.search(r"\n\s*\} catch \(Throwable t\) \{\n\s*println \"UAS ERROR: Execution failed for topic script '4_custom_code.groovy'", code)
    assert end_m
    starts.sort()
    out, pos = [], 0
    for k, (start, keys) in enumerate(starts):
        out.append(code[pos:start])
        if k > 0:
            out.append('}\n\n')
        out.append('uasGuard(' + repr(keys).replace("'", "'") + ') {\n')
        pos = start
    out.append(code[pos:end_m.start()])
    out.append('\n}\n')
    out.append(code[end_m.start():])
    code = ''.join(out)
    # variables shared between the hooks part and the hook extensions part
    code = code.replace("uasGuard(['custom_code_hook_plugins_count']) {",
                        "def LIFERAY_HOOK_XML_WEBAPP_PATH = 'WEB-INF/liferay-hook.xml'\ndef customHookPlugins = []\n\nuasGuard(['custom_code_hook_plugins_count']) {", 1)
    code = re.sub(r"^[ \t]*def LIFERAY_HOOK_XML_WEBAPP_PATH = 'WEB-INF/liferay-hook.xml'\n(?!def customHookPlugins)", '', code, count=1, flags=re.M)
    code = code.replace('def customHookPlugins = hookPlugins.findAll', 'customHookPlugins = hookPlugins.findAll', 1)
    return s[:i] + code + s[j:]


def harden(path, variant):
    s = open(path).read()
    fname = os.path.basename(path).replace('quarterly_template.groovy', '@@FILE_NAME@@')
    if os.path.basename(path) == 'quarterly_template.groovy':
        fname = '@@FILE_NAME@@'

    # 1. helpers
    if "BEGIN included '_uas_helpers.groovy'" in s:
        s = replace_block(s, '_uas_helpers', HELPERS)
    else:
        s = s.replace("// BEGIN included '1_infra.groovy'", HELPERS + "// BEGIN included '1_infra.groovy'", 1)

    # 2 + 3. data and database sections
    s = replace_block(s, '2_data', DATA.replace('@@DATA_VARIANT@@', variant).replace('@@FILE_NAME@@', fname))
    s = replace_block(s, '3_database_info', DBINFO.replace('@@FILE_NAME@@', fname))

    # 4. infra: no compile-time dependency on the search and scripting modules
    s = re.sub(r'^(//__GRADLE_COMMENT__ )?import com\.liferay\.portal\.search\.engine\.SearchEngineInformation\s*\n', '', s, flags=re.M)
    s = re.sub(r'^(//__GRADLE_COMMENT__ )?import com\.liferay\.portal\.scripting\.groovy\.internal\.GroovyExecutor\s*\n', '', s, flags=re.M)
    s = s.replace('SearchEngineInformation searchEngineInformation = null', 'def searchEngineInformation = null')
    s = s.replace('ServiceTracker<SearchEngineInformation, Object> st = null', 'ServiceTracker st = null')
    s = s.replace('st = new ServiceTracker(bundle.bundleContext, SearchEngineInformation.class, null)',
                  "st = new ServiceTracker(bundle.bundleContext, 'com.liferay.portal.search.engine.SearchEngineInformation', null)")
    s = s.replace('    def bundle = FrameworkUtil.getBundle(GroovyExecutor.class)\n',
                  "    def groovyExecutorClass = uasClass('com.liferay.portal.scripting.groovy.internal.GroovyExecutor')\n"
                  "    def bundle = [bundleContext: (groovyExecutorClass ? FrameworkUtil.getBundle(groovyExecutorClass)?.bundleContext : null) ?: uasBundleContext()]\n")
    s = s.replace('    def bundle = FrameworkUtil.getBundle(executorClass ?: SearchEngineInformation.class)\n',
                  "    def bundle = [bundleContext: (executorClass ? FrameworkUtil.getBundle(executorClass)?.bundleContext : null) ?: uasBundleContext()]\n")
    s = s.replace('def searchEngine = searchEngineInformation.statusString', 'def searchEngine = searchEngineInformation?.statusString')

    # 5. custom code: no compile-time dependency on DDM, safe directory listing, guarded parts
    s = re.sub(r'^(//__GRADLE_COMMENT__ )?import com\.liferay\.dynamic\.data\.mapping\.service\.DDMContentLocalService\s*\n', '', s, flags=re.M)
    s = s.replace('BundleContext bundleContext = FrameworkUtil.getBundle(DDMContentLocalService.class).getBundleContext()',
                  'BundleContext bundleContext = uasBundleContext()')
    s = s.replace("def customOSGiModules = customOSGiModulesDir.listFiles().findAll { it.name.endsWith('.jar')}",
                  "def customOSGiModules = (customOSGiModulesDir.listFiles() ?: []).findAll { it.name.endsWith('.jar')}")
    s = guard_custom_code(s)

    # 6. new items (7.4 template): Objects / Segments / Commerce loaded at runtime
    if variant == '7.4':
        for cls in ['com.liferay.object.service.ObjectDefinitionLocalServiceUtil', 'com.liferay.object.service.ObjectEntryLocalServiceUtil',
                    'com.liferay.segments.service.SegmentsEntryLocalServiceUtil']:
            s = re.sub(r'^import ' + re.escape(cls) + r'\s*\n', '', s, flags=re.M)
        marker = "    try {\n// Objects\n"
        if marker in s and 'def ObjectDefinitionLocalServiceUtil = uasClass' not in s:
            s = s.replace(marker, marker +
                          "def ObjectDefinitionLocalServiceUtil = uasClass('com.liferay.object.service.ObjectDefinitionLocalServiceUtil')\n"
                          "def ObjectEntryLocalServiceUtil = uasClass('com.liferay.object.service.ObjectEntryLocalServiceUtil')\n"
                          "def SegmentsEntryLocalServiceUtil = uasClass('com.liferay.segments.service.SegmentsEntryLocalServiceUtil')\n", 1)
        old_commerce = s[s.index("def globalCount = 0\n"):s.index("// Additional 7.4 / Quarterly Release features")] if "def globalCount = 0\n" in s else None
        new_commerce = '''uasGuard(['newitems_total_commerce_products']) {
    def cpDefinitionServiceClass = uasClass("com.liferay.commerce.product.service.CPDefinitionLocalServiceUtil")

    if (cpDefinitionServiceClass) {
        println UASUtils.toCSVLine(
            'newitems_total_commerce_products', cpDefinitionServiceClass.getCPDefinitionsCount(), "Total number of commerce product across all companies")
    } else {
        // Commerce modules not active (deactivated, blacklisted or failed): read the table directly, if it exists
        def connection = null
        def statement = null
        def result = null
        try {
            connection = com.liferay.portal.kernel.dao.jdbc.DataAccess.getConnection()
            statement = connection.createStatement()
            result = statement.executeQuery('select count(*) from CPDefinition')
            result.next()
            println UASUtils.toCSVLine(
                'newitems_total_commerce_products', result.getLong(1), "Total number of commerce product across all companies (Commerce modules are not active: counted in the CPDefinition table)")
        } catch (Throwable ignored) {
            println UASUtils.toCSVLine('newitems_total_commerce_products', '', "n/a - Commerce is not active and the CPDefinition table does not exist")
        } finally {
            com.liferay.portal.kernel.dao.jdbc.DataAccess.cleanUp(connection, statement, result)
        }
    }
}


'''
        if old_commerce:
            s = s.replace(old_commerce, new_commerce, 1)

        # Objects block: report all of its keys when it fails
        s = s.replace("""} catch (Throwable t) {
    println UASUtils.toCSVLine('newitems_object_entry_definitions', '', "n/a - ${t}")
}""", """} catch (Throwable t) {
    ['newitems_object_entry_definitions', 'newitems_total_system_object_entry_definition_count',
     'newitems_total_custom_object_entry_definition_count', 'newitems_total_custom_object_entries_count',
     'newitems_custom_object_definitions_info'].each { key ->
        println UASUtils.toCSVLine(key, '', "n/a - ${t}")
    }
}""")

    # 7. timings: each topic script is timed and reported in uas_section_durations
    if 'def uasSectionTimings = [:]' not in s:
        s = re.sub(r'^(invoke__[0-9a-z_]+_groovy\((?:uasContext[^\n]*)\))\s*$',
                   lambda m: "uasTimed(uasSectionTimings, '%s') { %s }" % (m.group(1).split('(')[0].replace('invoke__', '').replace('_groovy', ''), m.group(1)),
                   s, flags=re.M)
        s = s.replace("uasTimed(uasSectionTimings, '1_infra')", "def uasSectionTimings = [:]\n\nuasTimed(uasSectionTimings, '1_infra')", 1)
        foot = "    ${UASUtils.toCSVLine('uas_duration', uasMetadata['duration'])}  \n"
        assert s.count(foot) == 1, path
        s = s.replace(foot, foot + "    ${UASUtils.toCSVLine('uas_section_durations', uasSectionTimings.values().sum(0), uasSectionTimings.collect { \"${it.key}: ${it.value} ms\" }.join(', '))}\n")

    open(path, 'w').write(s)
    print('hardened', path, variant)


for arg in sys.argv[1:]:
    p, v = arg.rsplit(':', 1)
    harden(p, v)
