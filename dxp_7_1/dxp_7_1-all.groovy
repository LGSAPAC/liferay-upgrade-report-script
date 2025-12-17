// BEGIN common/_combined-header.groovy

def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"

// the @...@ tokens can in theory contain ' or ", so use a safe way to write these as Groovy String literals
def uasMetadata = [
        projectVersion: """\
                0.11.2
            """.trim(),
        sourceSetLabel: """\
                Liferay DXP 7.1
            """.trim(),
        startDate: new Date()
]
uasMetadata['startDateFormatted'] = uasMetadata['startDate'].format(DEFAULT_DATE_FORMAT)

// END common/_combined-header.groovy

// BEGIN included '_utils.groovy'  

// Any common scripts, as utils.groovy, should only have function definitions in them
// The content of any src/common/* script (top-level only!) will be include in the *-all.groovy files
// generated for every Liferay version.

/**
 * All utility methods from <code>src/common/_utils.groovy</code>.
 */
class UASUtils {

    private final Script _script
    private final Map<String, Object> _uasContext
    private static final String _DEBUG_MESSAGE_PREFIX = 'DEBUG UAS: '

    UASUtils(Script script, Map<String, Object> uasContext) {
        if (script == null) {
            throw new IllegalArgumentException("'script' argument cannot be null (1st arg).")
        }
        if (uasContext == null) {
            throw new IllegalArgumentException("'uasContext' argument cannot be null (2nd arg).")
        }

        _script = script
        _uasContext = uasContext
    }

    /**
     * Prints given message to stdout if 'debug' was set to <code>true<code>
     * in the context.
     * @param message
     */
    void debug(Object message) {
        def printDebugMessages = (_uasContext['debug'] as String).toBoolean()

        if (printDebugMessages && message != null) {
            _script.println "${(message as String).readLines().collect {"${_DEBUG_MESSAGE_PREFIX}${it}"}.join('\n')}"
        }
    }

    /**
     * Prints given message to stdout (as a single CSV line, one cell per part) if 'debug' was set to <code>true<code>
     * in the context.
     * @param message
     */
    void debugCSVLine(Object... messageParts) {
        def printDebugMessages = (_uasContext['debug'] as String).toBoolean()
        
        if (printDebugMessages && messageParts != null && messageParts.size() > 0) {
            // prefix just the content in the first cell, and only first line
            messageParts[0] = "${_DEBUG_MESSAGE_PREFIX}${messageParts[0]}"

            printlnCSV(messageParts)
        }
    }
    
    /**
     * Replaces encloser character for CSV (" by default) with its escaped value ("" by default).
     *
     * @param raw content (typically a String) you want to write into a CSV cell
     * @return same as raw, but with any encloser char escaped;
     *          edge cases: null -> '', '' -> '', <any> -> <any_escaped>.
     */
    static String escapeCSV(Object raw) {
        def encloser = '"'

        return (raw ? raw.toString() : '').replace(encloser, "${encloser}${encloser}")
    }

    /**
     * Returns a single line of CSV, taking care of the necessary escaping. The result can be output
     * by the script using for example the `println` function.
     *
     * @param key the raw key of the CSV line
     * @param value the raw value of the CSV line
     * @param comment the raw comment of the CSV line (optional)
     * @return
     */
    static String toCSVLine(Object... cells) {
        def csvLine =
                cells.collect {
                    '"' + escapeCSV(it?.toString() ?: '') + '"'
                }.join(', ')

        return csvLine
    }

    /**
     * Prints one CSV line to stdout, using 'println'.
     * @param cells
     * @return
     */
    void printlnCSV(Object... cells) {
        _script.println(toCSVLine(cells))
    }
}

String.metaClass.escapeCSV = { return UASUtils.escapeCSV(delegate) }
// just in case someone invokes on any other type -- don't fail the script, convert to String instead
Object.metaClass.escapeCSV = { return UASUtils.escapeCSV(delegate) }

class TableInfo implements Comparable<TableInfo> {
    private int rows;
    private String name;

    TableInfo(String name, int rows) {
        this.name = name;
        this.rows = rows;
    }

    public String toString() {
        return this.name + ", " + rows;
    }

    public int compareTo(TableInfo other) {
        if (this.rows == other.rows) {
            return this.name.compareTo(other.name);
        }
        else {
            return other.rows - this.rows;
        }
    }
}


// END included '_utils.groovy'

// BEGIN included '1_infra.groovy'  

// Imports for '1_infra.groovy' 
import com.liferay.portal.kernel.dao.jdbc.DataAccess
import com.liferay.portal.kernel.patcher.PatcherUtil
import com.liferay.portal.kernel.search.SearchEngine
import com.liferay.portal.kernel.search.SearchEngineHelperUtil
import com.liferay.portal.kernel.util.ReleaseInfo
import com.liferay.portal.kernel.util.ServerDetector
import com.liferay.portal.scripting.groovy.internal.GroovyExecutor
import com.liferay.portal.search.engine.SearchEngineInformation
import com.liferay.portal.util.PropsUtil
import org.osgi.framework.FrameworkUtil
import org.osgi.util.tracker.ServiceTracker
              
// Wrapper function for '1_infra.groovy'
def invoke__1_infra_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.jdbc.DataAccess
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.patcher.PatcherUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.search.SearchEngine
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.search.SearchEngineHelperUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.ReleaseInfo
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.ServerDetector
//__GRADLE_COMMENT__ import com.liferay.portal.scripting.groovy.internal.GroovyExecutor
//__GRADLE_COMMENT__ import com.liferay.portal.search.engine.SearchEngineInformation
//__GRADLE_COMMENT__ import com.liferay.portal.util.PropsUtil
//__GRADLE_COMMENT__ import org.osgi.framework.FrameworkUtil
//__GRADLE_COMMENT__ import org.osgi.util.tracker.ServiceTracker


println("${UASUtils.toCSVLine('Key', 'Value', 'Description')}")

// Version
println("${UASUtils.toCSVLine('infra_liferay_version', ReleaseInfo.getVersion(), ReleaseInfo.getReleaseInfo())}")


// Patches
def patches = PatcherUtil.getInstalledPatches().size()
def fixedIssues = PatcherUtil.getFixedIssues()
def fixedIssuesDisplayedMax = 10
def fixedIssuesDisplayed = fixedIssues.take(fixedIssuesDisplayedMax)

def patchesDetails = """\
Via PatcherUtil:
- installed patches: ${PatcherUtil.getInstalledPatches().join(', ')}
- fixed issues: ${fixedIssuesDisplayed.join(', ')}\
${fixedIssues.size() > fixedIssuesDisplayedMax ? "... (${fixedIssues.size()} total)" : ''}
- patch levels: ${PatcherUtil.getPatchLevels().join(', ')}
- separation ID: ${PatcherUtil.getSeparationId()}
- patching tool: ${PatcherUtil.getPatchingToolVersionDisplayName()}"""

println("${UASUtils.toCSVLine('infra_liferay_patches', patches, patchesDetails)}")


// App Server
def appServerId = ServerDetector.getServerId()

def appServer = appServerId
def appServerComment = 'no details available'

if(ServerDetector.isTomcat()) {
    // class not on classpath (packaged in tomcat/lib/catalina.jar)
    //    appServerDetails = org.apache.catalina.util.ServerInfo.getServerInfo()

    // Viable options: https://www.cloudhadoop.com/tomcat-check-version/

    def tomcatHomeDir = new File(System.getProperty('catalina.home'))
    def tomcatReleaseNotes = new File(tomcatHomeDir, 'RELEASE-NOTES')

    if (tomcatReleaseNotes.canRead()) {
        def tomcatWithVersion = tomcatReleaseNotes.text.find(/Apache Tomcat Version .*/)

        appServer = tomcatWithVersion
        appServerComment = "Via regexp match in ${tomcatReleaseNotes}"
    } else {
        appServerComment = "no details available - ${tomcatReleaseNotes} not found"
    }
}

println("${UASUtils.toCSVLine('infra_app_server_id', appServerId, 'Via ServerDetector::getServerId')}")
println("${UASUtils.toCSVLine('infra_app_server', appServer, appServerComment)}")


// JVM
def jvm = System.getProperty('java.version')
def jvmDetails = """\
Via System properties of the JVM:
- vendor: ${System.getProperty('java.vendor', 'unknown')} 
- version: ${System.getProperty('java.version')}
- vendor.url: ${System.getProperty('java.vendor.url')}"""

println("${UASUtils.toCSVLine('infra_jvm', jvm, jvmDetails)}")


// OS
def os = System.getProperty('os.name',  'unknown')
def osDetails = """\
Via System properties of the JVM:
- name: ${System.getProperty('os.name')}
- version: ${System.getProperty('os.version')}
- arch: ${System.getProperty('os.arch')}"""

println("${UASUtils.toCSVLine('infra_os', os, osDetails)}")


// Database Server
String databaseJdbc = PropsUtil.get('jdbc.default.driverClassName')
String databaseJdbcDetails = """\
JDBC:
- Driver: ${PropsUtil.get('jdbc.default.driverClassName')}
- URL: ${PropsUtil.get('jdbc.default.url')}
- JNDI name: ${PropsUtil.get('jdbc.default.jndi.name')}"""

// Based on code from Jorge Diaz on Slack
def connection = DataAccess.getConnection()
def connectionMetaData = connection.getMetaData()

String dbName = connectionMetaData.getDatabaseProductName()
int dbMajorVersion = connectionMetaData.getDatabaseMajorVersion()
int dbMinorVersion = connectionMetaData.getDatabaseMinorVersion()
String dbDriverName = connectionMetaData.getDriverName()
String dbUrl = connectionMetaData.getURL()

def database = "${dbName} ${dbMajorVersion}.${dbMinorVersion}"
def databaseDetails = """\
Via DataAccess::getConnection::getMetaData:
- databaseProductName: ${dbName}
- databaseMajorVersion: ${dbMajorVersion}
- databaseMinorVersion: ${dbMinorVersion}
- driverName: ${dbDriverName}
- URL: ${dbUrl}"""

println("${UASUtils.toCSVLine('infra_database', database, databaseDetails)}")
println("${UASUtils.toCSVLine('infra_database_jdbc', databaseJdbc, databaseJdbcDetails)}")


// Search Information
SearchEngineInformation searchEngineInformation = null
ServiceTracker<SearchEngineInformation, Object> st = null
try {
    def bundle = FrameworkUtil.getBundle(GroovyExecutor.class)
    st = new ServiceTracker(bundle.bundleContext, SearchEngineInformation.class, null)
    st.open()
    if (!st.isEmpty()) {
        searchEngineInformation = st.getService()
    }
}
catch (Exception e) {
    println e
}
finally {
    if (st != null) {
        st.close()
    }
}

// There's no ::vendorString or ::clientVersionString in SearchEngineInformation in 7.1.10-ga1, only ::statusString
def searchEngine = searchEngineInformation.statusString
def searchEngineDetails = searchEngineInformation?.properties?.collect { "- ${it.key}: ${it.value}"}.join('\n')

println("${UASUtils.toCSVLine('infra_search', searchEngine, searchEngineDetails)}")   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '1_infra.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__1_infra_groovy()
                
// the function 'invoke__1_infra_groovy' will be invoked at the very end of the 'dxp_7_1-all.groovy'
// invoke__1_infra_groovy([ debug: false ])              
// END included '1_infra.groovy'

// BEGIN included '2_data.groovy'  

// Imports for '2_data.groovy' 
import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil
import com.liferay.dynamic.data.mapping.model.DDMTemplate
import com.liferay.dynamic.data.mapping.model.DDMStructure
import com.liferay.dynamic.data.mapping.service.DDMContentLocalServiceUtil
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalServiceUtil
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalServiceUtil
import com.liferay.expando.kernel.model.ExpandoBridge
import com.liferay.expando.kernel.model.ExpandoTable
import com.liferay.expando.kernel.service.ExpandoTableLocalServiceUtil
import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil
import com.liferay.exportimport.kernel.staging.StagingUtil
import com.liferay.journal.service.JournalArticleLocalServiceUtil
import com.liferay.portal.kernel.dao.orm.QueryUtil
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
import com.liferay.portal.kernel.util.PropsKeys
import com.liferay.portal.kernel.util.Validator
import com.liferay.portal.util.PropsUtil
import com.liferay.fragment.service.FragmentEntryLocalServiceUtil
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalServiceUtil
              
// Wrapper function for '2_data.groovy'
def invoke__2_data_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.model.DDMTemplate
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.model.DDMStructure
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.service.DDMContentLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.service.DDMStructureLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.expando.kernel.model.ExpandoBridge
//__GRADLE_COMMENT__ import com.liferay.expando.kernel.model.ExpandoTable
//__GRADLE_COMMENT__ import com.liferay.expando.kernel.service.ExpandoTableLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil
//__GRADLE_COMMENT__ import com.liferay.exportimport.kernel.staging.StagingUtil
//__GRADLE_COMMENT__ import com.liferay.journal.service.JournalArticleLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.orm.QueryUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.security.auth.CompanyThreadLocal
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.ClassNameLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.CompanyLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.GroupLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.LayoutFriendlyURLLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.LayoutLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.UserLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.template.TemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PrefsPropsUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsKeys
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.Validator
//__GRADLE_COMMENT__ import com.liferay.portal.util.PropsUtil
//__GRADLE_COMMENT__ import com.liferay.fragment.service.FragmentEntryLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalServiceUtil

def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"

// Groups and Sites
def groups = GroupLocalServiceUtil.getGroups(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def sites = groups.findAll { it.site }

// DDM
def wcTemplateClassnameId =
        ClassNameLocalServiceUtil.getClassNameId(DDMStructure.class)
def ddmTemplates = DDMTemplateLocalServiceUtil.getDDMTemplates(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def companyIds = CompanyLocalServiceUtil.getCompanies()*.companyId
def defaultUserIds = companyIds.collect { companyId -> UserLocalServiceUtil.getDefaultUserId(companyId) }
def templatesCategorized = ddmTemplates.inject([adt: [], wc: []]) { templateCategories, template ->
    // Ignore BASIC-WEB-CONTENT
    if (template.getClassNameId() == wcTemplateClassnameId && template.getTemplateKey() != 'BASIC-WEB-CONTENT') {
        templateCategories.wc.push(template)
        // Only show custom ADTs
    } else if (!(template.getUserId() in defaultUserIds)) {
        templateCategories.adt.push(template)
    }
    return templateCategories
}
List<DDMTemplate> webContentTemplates = templatesCategorized.wc
def webContentTemplatesCount = webContentTemplates.size()
List<DDMTemplate> applicationDisplayTemplates = templatesCategorized.adt
def applicationDisplayTemplatesCount = applicationDisplayTemplates.size()
def dDMStructuresCount = DDMStructureLocalServiceUtil.getDDMStructuresCount()
def dDMTemplatesCount = DDMTemplateLocalServiceUtil.getDDMTemplatesCount()
def dDMContentsCount = DDMContentLocalServiceUtil.getDDMContentsCount()

// Others
def companiesCount = CompanyLocalServiceUtil.getCompaniesCount()
def usersCount = UserLocalServiceUtil.getUsersCount()
def layoutsCount = LayoutLocalServiceUtil.getLayoutsCount()
def journalArticlesCount = JournalArticleLocalServiceUtil.getJournalArticlesCount()
def dLFileEntriesCount = DLFileEntryLocalServiceUtil.getDLFileEntriesCount()
def portletPreferences = PortletPreferencesLocalServiceUtil.getPortletPreferences()
def portletIds = portletPreferences.collect {
    it.portletId - ~/_INSTANCE.*/
}.unique().sort()
def layoutFriendlyURLsCount = LayoutFriendlyURLLocalServiceUtil.getLayoutFriendlyURLsCount()
def layoutFriendlyURLs =
        LayoutFriendlyURLLocalServiceUtil.getLayoutFriendlyURLs(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def liferayPortlets =
        portletIds.findAll { it.contains('liferay') }
def customPortlets =
        portletIds.findAll { !it.contains('liferay') }
def fragmentsCount = FragmentEntryLocalServiceUtil.getFragmentEntriesCount()
def layoutPageTemplatesCount = LayoutPageTemplateEntryLocalServiceUtil.getLayoutPageTemplateEntriesCount()

// Questions keys

println UASUtils.toCSVLine("data_companies_count", "${companiesCount}", "")
println UASUtils.toCSVLine("data_custom_application_display_templates_adt_count", "${applicationDisplayTemplatesCount}", "")
println UASUtils.toCSVLine("data_custom_web_content_templates_wc_count", "${webContentTemplatesCount}", "")
println UASUtils.toCSVLine("data_ddm_contents_count", "${dDMContentsCount}", "") // this question keys is not present in 7.4 script
println UASUtils.toCSVLine("data_ddm_structures_count", "${dDMStructuresCount}", "")
println UASUtils.toCSVLine("data_ddm_templates_count", "${dDMTemplatesCount}", "")
println UASUtils.toCSVLine("data_dl_files_count", "${dLFileEntriesCount}", "")
println UASUtils.toCSVLine("data_fragments_count", "${fragmentsCount}", "")
println UASUtils.toCSVLine("data_journal_articles_count", "${journalArticlesCount}", "")
println UASUtils.toCSVLine("data_layouts_count", "${layoutsCount}", "")
println UASUtils.toCSVLine("data_layouts_friendly_url_count", "${layoutFriendlyURLsCount}", "")
println UASUtils.toCSVLine("data_page_templates_count", "${layoutPageTemplatesCount}", "")
println UASUtils.toCSVLine("data_used_custom_portlets_count", "${customPortlets.size()}", "\n${customPortlets.collect { "      * ${it}" }.join('\n')}")
println UASUtils.toCSVLine("data_used_liferay_portlets_count", "${liferayPortlets.size()}", "\n${liferayPortlets.collect { "      * ${it}" }.join('\n')}")
println UASUtils.toCSVLine("data_user_private_pages_enabled", "${PropsUtil.get('layout.user.private.layouts.enabled')}", "")
println UASUtils.toCSVLine("data_user_public_pages_enabled", "${PropsUtil.get('layout.user.public.layouts.enabled')}", "")
println UASUtils.toCSVLine("data_users_count", "${usersCount}", "")

if (layoutsCount > usersCount) {
    println UASUtils.toCSVLine("data_users_layouts_count", "${(usersCount * 2)}", "")
    println UASUtils.toCSVLine("data_custom_layouts_count", "${(layoutsCount - (usersCount * 2))}", "")
}
// There may be many more, but ROM calculator only asks about VM and FTL, so only make sure these are put as CSV lines
def ddmTemplatesSupportedLangs = [ TemplateConstants.LANG_TYPE_VM, TemplateConstants.LANG_TYPE_FTL ]
def webContentTemplatesByLang =
        webContentTemplates.groupBy { DDMTemplate it -> it.language }

ddmTemplatesSupportedLangs.each {
    webContentTemplatesByLang.putIfAbsent(it, Collections.emptyList())
}

webContentTemplatesByLang.each { lang, templates ->
    println UASUtils.toCSVLine(
            "data_wc_templates_by_lang_${lang}_count", templates.size(),
            "Custom WC templates with '${lang}' as the language:\n${templates.collect { " - ${it.nameCurrentValue} { templateKey=${it.templateKey}, templateId=${it.templateId} }" }.join('\n')}")
}

def adtTemplatesByLang =
        applicationDisplayTemplates.groupBy { DDMTemplate it -> it.language }

ddmTemplatesSupportedLangs.each {
    adtTemplatesByLang.putIfAbsent(it, Collections.emptyList())
}

adtTemplatesByLang.each { lang, templates ->
    println UASUtils.toCSVLine(
            "data_adt_templates_by_lang_${lang}_count", templates.size(),
            "Custom ADT templates with '${lang}' as the language:\n${templates.collect { " - ${it.nameCurrentValue} { templateKey=${it.templateKey}, templateId=${it.templateId} }" }.join('\n')}")
}

def siteDetails = ''<<''
def childGroupCount = 0;

siteDetails <<= "type, companyId, groupId, friendlyURL, name, staging, parent site name"

sites.each { it ->
    def parentGroupName = "N/A"
    def type = "PARENT_GROUP"
    def liveGroup = StagingUtil.getLiveGroup(it.groupId)
    def parentGroupId = it.parentGroupId

    if (!parentGroupId==0) {
        type = "CHILD_GROUP"
        parentGroupName = GroupLocalServiceUtil.getGroup(parentGroupId).getName()
        childGroupCount++
    }

    siteDetails <<= "\n\t${type}, ${it.companyId}, ${it.groupId}, ${it.friendlyURL}, ${it.nameCurrentValue}, ${liveGroup.staged}, ${parentGroupName}"
}

// data_sites_count
def siteSize = sites.size()
println UASUtils.toCSVLine('data_sites_count',"${siteSize}","${siteDetails.toString()}")
println UASUtils.toCSVLine('data_sites_details', "", "${siteSize-childGroupCount} Parent Sites, ${childGroupCount} Child Sites")

def stagingEnabled = sites.any{StagingUtil.getLiveGroup(it.groupId).staged}

// data_staging_enabled
println UASUtils.toCSVLine('data_staging_enabled',"${stagingEnabled}",'see data_sites_count for more details')

// data_file_store_method
println UASUtils.toCSVLine('data_file_store_method', PropsUtil.get(PropsKeys.DL_STORE_IMPL), "");
// data_live_users_enabled
println UASUtils.toCSVLine('data_live_users_enabled', PropsUtil.get(PropsKeys.LIVE_USERS_ENABLED), "");

// data_google_maps_integration
def googleMapsIntegration = false;
def mapProviderKey = "MAP_PROVIDER_KEY";
def googleMapsKey = "GoogleMaps";

// Enabled via Site Settings - Advanced Tab
for (def group : groups) {
    def typeSettings = group.getTypeSettingsProperty(mapProviderKey);

    if (!Validator.isBlank(typeSettings)) {
        if (typeSettings == googleMapsKey) {
            googleMapsIntegration = true;
        }
    }
}

// Enabled via Instance Settings --> Miscellaneous
for (def companyId : companyIds) {
    def companyPortletPreferences = PrefsPropsUtil.getPreferences(companyId);

    def mapsIntegrationProperty = companyPortletPreferences.getValue(mapProviderKey, null);

    if (mapsIntegrationProperty == googleMapsKey) {
        googleMapsIntegration = true;
    }
}

println UASUtils.toCSVLine('data_google_maps_integration', "${googleMapsIntegration}" , "");

// Expando Fields
int expandoTablesCount = 0;
String expandoTableDetails = "ExpandoClassName, ExpandoColumnsCount";
List<ExpandoTable> expandoTables =
        ExpandoTableLocalServiceUtil.getExpandoTables(QueryUtil.ALL_POS, QueryUtil.ALL_POS);

for (e in  expandoTables) {
    String expandoClassName =
            ClassNameLocalServiceUtil.fetchByClassNameId(e.getClassNameId()).getClassName();

    ExpandoBridge expandoBridge =
            ExpandoBridgeFactoryUtil.getExpandoBridge(CompanyThreadLocal.getCompanyId(), expandoClassName, 0L);

    if (expandoBridge.getAttributes().size() > 0) {
        expandoTableDetails <<= "\n\t${expandoClassName}, ${expandoBridge.getAttributes().size()}"
        expandoTablesCount++;
    }
}
println UASUtils.toCSVLine('expando_count', "${expandoTablesCount}", "")
println UASUtils.toCSVLine('expando_tables_info', "", "${expandoTableDetails.toString()}")   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '2_data.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__2_data_groovy()
                
// the function 'invoke__2_data_groovy' will be invoked at the very end of the 'dxp_7_1-all.groovy'
// invoke__2_data_groovy([ debug: false ])              
// END included '2_data.groovy'

// BEGIN included '3_database_info.groovy'  

// Imports for '3_database_info.groovy' 
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.dao.jdbc.DataAccess
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
              
// Wrapper function for '3_database_info.groovy'
def invoke__3_database_info_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.db.DB;
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.db.DBManagerUtil;
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.db.DBType;
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.jdbc.DataAccess

//__GRADLE_COMMENT__ import java.sql.Connection;
//__GRADLE_COMMENT__ import java.sql.DatabaseMetaData;
//__GRADLE_COMMENT__ import java.sql.PreparedStatement;
//__GRADLE_COMMENT__ import java.sql.ResultSet;
//__GRADLE_COMMENT__ import java.util.ArrayList;
//__GRADLE_COMMENT__ import java.util.Collections;
//__GRADLE_COMMENT__ import java.util.List;

try {
    Connection connection = null;
    ResultSet rs = null;
    PreparedStatement ps = null;

    DB db = DBManagerUtil.getDB();

    DBType dbType = db.getDBType();

    try {
        connection = DataAccess.getConnection();

        DatabaseMetaData metadata = connection.getMetaData();

        String catalog = connection.getCatalog();
        String schema = null;

        if ((catalog == null) && (dbType.equals(DBType.ORACLE))) {
            catalog = metadata.getUserName();
            schema = catalog;
        }

        rs = metadata.getTables(catalog, schema, "%", null);

        List<TableInfo> tables = new ArrayList<TableInfo>();

        while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            String tableType = rs.getString("TABLE_TYPE");

            if (dbType.equals(DBType.MARIADB)) {
                if (!tableType.equals("BASE TABLE")) {
                    continue
                }
            }
            else if (!"TABLE".equals(tableType)) {
                continue;
            }

            ResultSet rs2 = null;

            try {
                ps = connection.prepareStatement(
                    "select count(*) from " + tableName);

                rs2 = ps.executeQuery();

                if (rs2.next()) {
                    tables.add(new TableInfo(tableName, rs2.getInt(1)));
                }
            }
            catch (Exception e) {
                System.out.println(
                    "Unable to recover data from " + tableName);
            }
            finally {
                DataAccess.cleanUp(rs2);
            }
        }

        Collections.sort(tables);

        def tableDetails = "tableName, rowsCount\n"

        for(TableInfo table : tables) {
            tableDetails <<= table;
            tableDetails <<= "\n";
        }

        println UASUtils.toCSVLine('data_tables_count', "${tables.size}", "");
        println UASUtils.toCSVLine('data_tables_info', "", "${tableDetails.toString()}");
    }
    finally {
        DataAccess.cleanUp(connection, ps, rs);
    }

    println "";
}
catch(Exception exception) {
    System.out.println(exception.getMessage());
}
   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '3_database_info.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__3_database_info_groovy()
                
// the function 'invoke__3_database_info_groovy' will be invoked at the very end of the 'dxp_7_1-all.groovy'
// invoke__3_database_info_groovy([ debug: false ])              
// END included '3_database_info.groovy'

// BEGIN included '4_custom_code.groovy'  

// Imports for '4_custom_code.groovy' 
import com.liferay.dynamic.data.mapping.service.DDMContentLocalService
import com.liferay.portal.deploy.DeployUtil
import com.liferay.portal.deploy.auto.PluginAutoDeployListenerHelper
import com.liferay.portal.kernel.dao.orm.QueryUtil
import com.liferay.portal.kernel.model.LayoutTemplate
import com.liferay.portal.kernel.model.Release
import com.liferay.portal.kernel.model.ReleaseConstants
import com.liferay.portal.kernel.plugin.PluginPackage
import com.liferay.portal.kernel.service.LayoutTemplateLocalServiceUtil
import com.liferay.portal.kernel.service.PortletServiceUtil
import com.liferay.portal.kernel.service.ReleaseLocalServiceUtil
import com.liferay.portal.kernel.service.ThemeLocalServiceUtil
import com.liferay.portal.kernel.template.TemplateConstants
import com.liferay.portal.kernel.util.PortalUtil
import com.liferay.portal.kernel.util.PropsKeys
import com.liferay.portal.kernel.util.PropsUtil
import com.liferay.portal.kernel.xml.Document
import com.liferay.portal.kernel.xml.SAXReaderUtil
import com.liferay.portal.kernel.zip.ZipFileUtil
import com.liferay.portal.plugin.PluginPackageUtil
import com.liferay.portal.util.PropsValues
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
              
// Wrapper function for '4_custom_code.groovy'
def invoke__4_custom_code_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.service.DDMContentLocalService
//__GRADLE_COMMENT__ import com.liferay.portal.deploy.DeployUtil
//__GRADLE_COMMENT__ import com.liferay.portal.deploy.auto.PluginAutoDeployListenerHelper
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.orm.QueryUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.model.LayoutTemplate
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.model.Release
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.model.ReleaseConstants
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.plugin.PluginPackage
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.LayoutTemplateLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.PortletServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.ReleaseLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.ThemeLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.template.TemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PortalUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsKeys
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.xml.Document
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.xml.SAXReaderUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.zip.ZipFileUtil
//__GRADLE_COMMENT__ import com.liferay.portal.plugin.PluginPackageUtil
//__GRADLE_COMMENT__ import com.liferay.portal.util.PropsValues
//__GRADLE_COMMENT__ import org.osgi.framework.Bundle
//__GRADLE_COMMENT__ import org.osgi.framework.BundleContext
//__GRADLE_COMMENT__ import org.osgi.framework.FrameworkUtil

// 'uasContext' is a parameter to the wrapper function for this script when combining it with the others;
// see build.gradle -> buildTopicScriptsForSourceSet / buildCombinedScriptsForSourceSet
def uasUtils = new UASUtils(this, uasContext)

// 7.0 vs 7.1+ differences
// PropsValues: Constants are present in 7.1+
String _PROPS_VALUES_MODULE_FRAMEWORK_MARKETPLACE_DIR =
        PropsValues.MODULE_FRAMEWORK_MARKETPLACE_DIR
String _PROPS_VALUES_MODULE_FRAMEWORK_MODULES_DIR =
        PropsValues.MODULE_FRAMEWORK_MODULES_DIR
String _PROPS_VALUES_MODULE_FRAMEWORK_WAR_DIR =
        PropsValues.MODULE_FRAMEWORK_WAR_DIR

// LayoutTemplateLocalServiceUtil
def _LayoutTemplateLocalServiceUtil_getLangType = { LayoutTemplate it ->
    // Method is present in 7.1+
    return LayoutTemplateLocalServiceUtil.getLangType(it.layoutTemplateId, it.standard, it.themeId)
}

def allUniqueInstalledPlugins =
        PluginPackageUtil.getInstalledPluginPackages().sort().reverse().unique { it.moduleId }

// Output all plugins as a CSV line, just in case we miss-categorize some custom plugin as a 'liferay' one
println UASUtils.toCSVLine(
        'custom_code_all_installed_plugins_count', allUniqueInstalledPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages, custom ones and also the ones from 'liferay':\n${allUniqueInstalledPlugins.collect { " - ${it}" }.join('\n')}")


uasUtils.debugCSVLine "allUniqueInstalledPlugins (${allUniqueInstalledPlugins.size()})", """${allUniqueInstalledPlugins.collect {"* ${it}\n${it.properties.collect { "  - ${it.key}: ${it.value}" }.join('\n')}"}.join('\n')}"""

// 'osgi/marketplace/override' by default
def marketplaceLpkgOverrideModulesDir = new File(_PROPS_VALUES_MODULE_FRAMEWORK_MARKETPLACE_DIR, 'override')

// 'osgi/static' by default; based on README.md in osgi/marketplace/override, it's
def staticLpkgOverrideModulesDir = new File(PropsValues.MODULE_FRAMEWORK_BASE_DIR, 'static')

uasUtils.debugCSVLine 'marketplaceLpkgOverrideModulesDir', marketplaceLpkgOverrideModulesDir.absolutePath
uasUtils.debugCSVLine 'staticLpkgOverrideModulesDir', staticLpkgOverrideModulesDir.absolutePath

[
        custom_code_marketplace_lpkg_override_modules_count: marketplaceLpkgOverrideModulesDir,
        custom_code_static_lpkg_override_modules_count: staticLpkgOverrideModulesDir
].each { csvKey, overrideDir ->
    def overridesCount
    def comment

    if (overrideDir.isDirectory()) {
        def overrideModules =
                overrideDir.listFiles().findAll {
                    it.name.endsWith('.jar') || it.name.endsWith('.war')
                }

        overridesCount = overrideModules.size()
        comment = "Counted by listing the '${overrideDir}' directory (*.jar, *.war):\n${overrideModules.collect { " - ${it.name}" }.join('\n')}"
    }
    else {
        overridesCount = 0
        comment = "The '${overrideDir}' directory does not exist."
    }

    println UASUtils.toCSVLine(csvKey, overridesCount, comment)
}


// Hooks

// https://github.com/liferay/liferay-portal/blob/7.2.x/modules/apps/marketplace/marketplace-service/src/main/java/com/liferay/marketplace/service/impl/AppLocalServiceImpl.java#L133
def LIFERAY_HOOK_XML_WEBAPP_PATH = 'WEB-INF/liferay-hook.xml'

def pureHookPlugins = allUniqueInstalledPlugins.findAll {
    'hook' in it.types
}

def portletPluginsWithHooks = allUniqueInstalledPlugins.findAll {
    'portlet' in it.types
}.findAll {
    def contextName = it.context

    // since 7.0, the .war is _not_ (deployed as a directory) in tomcat/webapps, but stays (as a .war file) in 'osgi/war'

    File warAppServerAutoDeployPath = new File(DeployUtil.getAutoDeployDestDir(), contextName)
    File warOsgiPath = new File(_PROPS_VALUES_MODULE_FRAMEWORK_WAR_DIR, "${contextName}.war")

    File contextDirOrFile
    def isHook = false

    if (warAppServerAutoDeployPath.isDirectory()) {
        contextDirOrFile = warAppServerAutoDeployPath

        uasUtils.debug "'${warAppServerAutoDeployPath}' is an existing directory, checking hook inside of it"
    } else if (warOsgiPath.isFile()){
        contextDirOrFile = warOsgiPath

        uasUtils.debug "'${warOsgiPath}' is an existing file, checking hook inside of it"
    } else {
        uasUtils.debug "Neither '${warAppServerAutoDeployPath}' is an existing directory nor '${warOsgiPath}' is an existing file, _not_ checking hook inside of '${contextName}'"
    }

    if (contextDirOrFile) {
        // PluginAutoDeployListenerHelper::isHookPlugin ignores the hook inside if it's also a portlet plugin
        // (seems to be exclusive detection), so check for the XML file directly
        isHook = new PluginAutoDeployListenerHelper(contextDirOrFile).isMatchingFile(LIFERAY_HOOK_XML_WEBAPP_PATH, false)

        uasUtils.debug "'${contextName}': isHook= ${isHook}"
    }

    return isHook
}

// There may be even hooks from Liferay, example: liferay/saml-hook
// TODO developers might leave 'groupId == liferay' even for their custom hooks, because it's typically generated this way by the hook template (plugins SDK / Blae archetype). We might inadvertently exclude these custom plugins, by mistake.
def hookPlugins = (pureHookPlugins + portletPluginsWithHooks).unique { it.moduleId }
def liferayHookPlugins = hookPlugins.findAll { it.groupId == 'liferay' }
def customHookPlugins = hookPlugins.findAll { !(it.moduleId in liferayHookPlugins*.moduleId) }

uasUtils.debugCSVLine 'hookPlugins', hookPlugins
uasUtils.debugCSVLine 'liferayHookPlugins', liferayHookPlugins

println UASUtils.toCSVLine(
        'custom_code_hook_plugins_count', customHookPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages, excluding ${liferayHookPlugins.size} authored by 'liferay' (${liferayHookPlugins*.moduleId}):\n${customHookPlugins.collect { " - ${it.moduleId}" }.join('\n')}")


// Portlets

def portletPlugins = allUniqueInstalledPlugins.findAll {
    'portlet' in it.types
}

def defaultPluginsList = [ ~/liferay\/saml-hook\/\d*\.\d*\.\d*\.\d*\/war/ ]
def liferayPortletPlugins = [];

portletPlugins.each { plugin ->
    if (defaultPluginsList.find { plugin.moduleId.matches(it.pattern()) } ) {
        liferayPortletPlugins.add(plugin)
    }
}

def customPortletPlugins = portletPlugins.findAll { !(it.moduleId in liferayPortletPlugins*.moduleId) }

uasUtils.debugCSVLine 'portletPlugins', portletPlugins
uasUtils.debugCSVLine 'liferayPortletPlugins', liferayPortletPlugins

println UASUtils.toCSVLine(
        'custom_code_portlet_plugins_count', customPortletPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages, excluding ${liferayPortletPlugins.size()} authored by 'liferay' (${liferayPortletPlugins}):\n${customPortletPlugins.collect { " - ${it.moduleId}" }.join('\n')}")

def portlets = PortletServiceUtil.getWARPortlets()

// the output would be huge, so omit it
//uasUtils.debugCSVLine 'portlets', portlets

def liferayPortlets =
        portlets.findAll {
            it.portlet_name.startsWith('com_liferay_') ||
                    it.portlet_name.startsWith('com.liferay.') ||
                    liferayPortletPlugins.find { liferayPlugin ->
                        it.servlet_context_name == liferayPlugin.context
                    } ||
                    it.servlet_context_name == 'hello-soy-web' // present in 7.0-ga1
        }

def customPortlets =
        portlets.findAll {
            !liferayPortlets.find { lfrPortlet ->
                it.portlet_name == lfrPortlet.portlet_name && it.servlet_context_name == lfrPortlet.servlet_context_name
            }
        }

//uasUtils.debugCSVLine 'portlets', portlets
//uasUtils.debugCSVLine 'liferayPortlets', liferayPortlets

// Typically around 150 from liferay, so just list the count of excluded ones
println UASUtils.toCSVLine(
        'custom_code_portlets_count', customPortlets.size(),
        "Via PortletServiceUtil::getWARPortlets, excluding ${liferayPortlets.size()} authored by 'liferay':\n${customPortlets.collect { " - ${it.portlet_name} { servletContextName=${it.servlet_context_name} }" }.join('\n')}")

// Layout templates

def liferayLayoutTemplatePackages = [] // the stock ones seem not to be placed inside a "plugin"?
uasUtils.debugCSVLine 'liferayLayoutTemplatePackages', liferayLayoutTemplatePackages

def layoutTemplatePlugins = allUniqueInstalledPlugins.findAll {
    'layout-template' in it.types || it.name.endsWith('-layout-template')
}
def customLayoutTemplatePlugins =
        layoutTemplatePlugins.findAll {
            !(it.moduleId in liferayLayoutTemplatePackages*.moduleId)
        }

println UASUtils.toCSVLine(
        'custom_code_layout_template_plugins_count', customLayoutTemplatePlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages, excluding ${liferayLayoutTemplatePackages.size()} authored by 'liferay' (${liferayLayoutTemplatePackages}):\n${customLayoutTemplatePlugins.collect { " - ${it.moduleId}" }.join('\n')}")


// TODO this does list layout-templates installed as part of a -theme plugin
def layoutTemplates = LayoutTemplateLocalServiceUtil.getLayoutTemplates()
def liferayLayoutTemplates =
        layoutTemplates.findAll { it.pluginPackage.groupId == 'liferay' || it.servletContextName == PortalUtil.getServletContextName() }

uasUtils.debugCSVLine 'liferayLayoutTemplates', liferayLayoutTemplates*.layoutTemplateId

def customLayoutTemplates =
        layoutTemplates.findAll {
            null == liferayLayoutTemplates.find { liferayTemplate ->
                it.servletContextName == liferayTemplate.servletContextName &&
                        it.layoutTemplateId == liferayTemplate.layoutTemplateId }
        }

println UASUtils.toCSVLine(
        'custom_code_layout_templates_count', customLayoutTemplates.size(),
        "Via LayoutTemplateLocalServiceUtil::getLayoutTemplates, excluding ${liferayLayoutTemplates.size()} authored by 'liferay' (${liferayLayoutTemplates*.layoutTemplateId}):\n${customLayoutTemplates.collect { " - ${it.name} { layoutTemplateId=${it.layoutTemplateId}, servletContextName=${it.servletContextName} }" }.join('\n')}")

// as in LayoutTemplateLocalServiceImpl::supportedLangTypes
def layoutTemplatesSupportedLangs = [ TemplateConstants.LANG_TYPE_VM, TemplateConstants.LANG_TYPE_FTL ]

def customLayoutTemplatesByLang =
        customLayoutTemplates.groupBy {
            _LayoutTemplateLocalServiceUtil_getLangType(it)
        }
layoutTemplatesSupportedLangs.each {
    customLayoutTemplatesByLang.putIfAbsent(it, Collections.emptyList())
}

customLayoutTemplatesByLang.each { lang, langLayoutTemplates ->
    println UASUtils.toCSVLine(
            "custom_code_layout_templates_by_lang_${lang}_count", langLayoutTemplates.size(),
            "Custom layout templates with '${lang}' as the language:\n${langLayoutTemplates.collect { " - ${it.name} { layoutTemplateId=${it.layoutTemplateId}, servletContextName=${it.servletContextName} }" }.join('\n')}")
}


// OSGi Modules

def customOSGiModulesDir = new File(_PROPS_VALUES_MODULE_FRAMEWORK_MODULES_DIR)
def customOSGiModules = customOSGiModulesDir.listFiles().findAll { it.name.endsWith('.jar')}

uasUtils.debugCSVLine 'customOSGiModulesDir', customOSGiModulesDir.absolutePath

println UASUtils.toCSVLine(
        'custom_code_osgi_modules_count', customOSGiModules.size(),
        "Counted by listing the '${customOSGiModulesDir}' directory (*.jar):\n${customOSGiModules.collect { " - ${it.name}" }.join('\n')}"
)


// Themes

def themePlugins = allUniqueInstalledPlugins.findAll {
    'theme' in it.types
}

def defaultThemesList = [ ~/liferay\/classic-theme\/\d*\.\d*\.\d*\/war/, ~/liferay\/admin-theme\/\d*\.\d*\.\d*\/war/ ]
def liferayThemePluginPackages = [];

themePlugins.each { theme ->
    if (defaultThemesList.find { theme.moduleId.matches(it.pattern()) } ) {
        liferayThemePluginPackages.add(theme)
    }
}

def liferayThemeServletContexts = liferayThemePluginPackages.collect { it.context }

uasUtils.debugCSVLine 'liferayThemePluginPackages', liferayThemePluginPackages
uasUtils.debugCSVLine 'liferayThemeServletContexts', liferayThemeServletContexts

def customThemePlugins = themePlugins.findAll { !(it.moduleId in liferayThemePluginPackages*.moduleId) }

println UASUtils.toCSVLine(
        'custom_code_theme_plugins_count', customThemePlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages, excluding ${liferayThemePluginPackages.size()} authored by 'liferay' (${liferayThemePluginPackages*.moduleId}):\n${customThemePlugins.collect { " - ${it.moduleId}" }.join('\n')}")

def themes = ThemeLocalServiceUtil.getWARThemes()

def liferayThemeIds = themes.findAll { it.servletContextName in liferayThemeServletContexts }.collect { it.themeId }
uasUtils.debugCSVLine 'liferayThemeIds', liferayThemeIds

def customThemes = themes.findAll { !(it.themeId in liferayThemeIds) }

println UASUtils.toCSVLine(
        'custom_code_themes_count', customThemes.size(),
        "Via ThemeLocalServiceUtil::getWARThemes, excluding ${liferayThemeIds.size()} authored by 'liferay' (${liferayThemeIds}):\n${customThemes.collect { " - ${it.name} { themeId=${it.themeId}, servletContextName=${it.servletContextName} }" }.join('\n')}")

def themesSupportedLangs =
        [ TemplateConstants.LANG_TYPE_VM, TemplateConstants.LANG_TYPE_FTL, 'jsp' ]

def customThemesByLang = customThemes.groupBy {it.templateExtension }
themesSupportedLangs.each { customThemesByLang.putIfAbsent(it, Collections.emptyList())}

customThemesByLang.each { lang, langThemes ->
    println UASUtils.toCSVLine(
            "custom_code_themes_by_lang_${lang}_count", langThemes.size(),
            "Custom themes with '${lang}' as the language:\n${langThemes.collect { " - ${it.name} { themeId=${it.themeId}, servletContextName=${it.servletContextName} }" }.join('\n')}")
}

// Webs

def webPlugins = allUniqueInstalledPlugins.findAll {
    'web' in it.types || it.name.endsWith('-web')
}

println UASUtils.toCSVLine(
        'custom_code_web_plugins_count', webPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages:\n${webPlugins.collect { " - ${it.moduleId}" }.join('\n')}")


//
// Features of Liferay Hook extension point
//
Map<String, String> hookContextToLiferayHookXmlContent = customHookPlugins.collectEntries {
    def contextName = it.context

    // TODO app server might not explode the .wars (into directories) and keep them as .war in auto-deploy dir
    File warAppServerAutoDeployPath = new File(DeployUtil.getAutoDeployDestDir(), contextName)
    File warOsgiPath = new File(_PROPS_VALUES_MODULE_FRAMEWORK_WAR_DIR, "${contextName}.war")

    String liferayHookXmlContent

    if (warAppServerAutoDeployPath.isDirectory()) {
        liferayHookXmlContent = new File(warAppServerAutoDeployPath, LIFERAY_HOOK_XML_WEBAPP_PATH).text

    } else if (warOsgiPath.isFile()){
        liferayHookXmlContent = ZipFileUtil.openInputStream(warOsgiPath, LIFERAY_HOOK_XML_WEBAPP_PATH).text
    } else {
        uasUtils.debug "Hook '${contextName}' found in neither '${warAppServerAutoDeployPath}' nor '${warOsgiPath}' (probably part of some .lpkg), cannot get the content of its '${LIFERAY_HOOK_XML_WEBAPP_PATH}'."
    }

    return [ contextName, liferayHookXmlContent ]
}

Map<String, Boolean> contextNameToCustomJspDir = [:]
Map<String, Boolean> contextNameToCustomJspApplicationAdapter = [:]
Map<String, List<String>> contextNameToServletFilters = [:]
Map<String, List<String>> contextNameToStrutsActions = [:]
Map<String, List<String>> contextNameToIndexerPostProcessors = [:]
Map<String, List<String>> contextNameToServiceWrappers = [:]
Map<String, Boolean> contextNameToOverridePortalProperties = [:]
Map<String, Integer> contextNameToOverrideLanguageProperties = [:]

def unprocessedHookContexts = hookContextToLiferayHookXmlContent.findAll { !it.value } .collect { it.key }
uasUtils.debugCSVLine 'unprocessedHookContexts (cannot find effective deploy location of their war)', unprocessedHookContexts

hookContextToLiferayHookXmlContent.findAll { it.value }.each { contextName, liferayHookXmlContent ->
    Document document = SAXReaderUtil.read(liferayHookXmlContent, false)
    def root = document.getRootElement()

    def portalProperties = root.elements('portal-properties')
    contextNameToOverridePortalProperties[contextName] = portalProperties.size() > 0

    def languageProperties = root.elements('language-properties')
    contextNameToOverrideLanguageProperties[contextName] = languageProperties.size()

    def customJspDir = root.element('custom-jsp-dir')
    contextNameToCustomJspDir[contextName] = (customJspDir != null)

    // For the JSPs to act as "Application Adapter", there have to be some provided by the hook AND explicitly marked as not-global
    def customJspGlobal = root.element('custom-jsp-global')
    contextNameToCustomJspApplicationAdapter[contextName] =
            contextNameToCustomJspDir[contextName] &&
                    customJspGlobal != null &&
                    customJspGlobal.getTextTrim() == 'false'

    def indexerPostProcessors = root.elements('indexer-post-processor')
    contextNameToIndexerPostProcessors[contextName] = indexerPostProcessors.collect { "{ indexer-class-name=${it.elementText('indexer-class-name')} }" }

    def services = root.elements('service')
    contextNameToServiceWrappers[contextName] = services.collect { "{ service-type='${it.elementText('service-type')} }'" }

    // <servlet-filter> and <servlet-filter-mapping> have ot be 1-to-1 and
    // we are more interested in the URL pattern than the impl class name, so count the latter
    def servletFilters = root.elements('servlet-filter-mapping')
    contextNameToServletFilters[contextName] = servletFilters.collect {
        "{ servlet-filter-name=${it.elementText('servlet-filter-name')}, url-pattern=${it.elementText('url-pattern')} }"
    }

    def strutsActions = root.elements('struts-action')
    contextNameToStrutsActions[contextName] = strutsActions.collect { "{ struts-action-path=${it.elementText('struts-action-path')} }" }
}

// Features of the Liferay Hook extension point: JSP Hook/Custom JSP
println UASUtils.toCSVLine(
        'custom_code_hook_extensions_custom_jsp',
        contextNameToCustomJspDir.values().count(true),
        "Hooks with <custom-jsp-dir> in '${LIFERAY_HOOK_XML_WEBAPP_PATH}' files:\n${contextNameToCustomJspDir.collect { " - ${it.key}: ${it.value}" }.join('\n')}")

// Features of the Liferay Hook extension point: Servet Filters
println UASUtils.toCSVLine(
        'custom_code_hook_extensions_servlet_filters',
        contextNameToServletFilters.values().sum(0) { it.size() },
        "Sum of <servlet-filter-mapping> '${LIFERAY_HOOK_XML_WEBAPP_PATH}' files:\n${contextNameToServletFilters.collect { " - ${it.key}: ${it.value.size()} ${it.value}" }.join('\n')}")

// Features of the Liferay Hook extension point: Struts Actions
println UASUtils.toCSVLine(
        'custom_code_hook_extensions_struts_actions',
        contextNameToStrutsActions.values().sum(0) { it.size() },
        "Sum of <struts-action> in '${LIFERAY_HOOK_XML_WEBAPP_PATH}' files:\n${contextNameToStrutsActions.collect { " - ${it.key}: ${it.value.size()} ${it.value}" }.join('\n')}")

// Features of the Liferay Hook extension point: Indexer Post Processors
println UASUtils.toCSVLine(
        'custom_code_hook_extensions_indexer_post_processors',
        contextNameToIndexerPostProcessors.values().sum(0) { it.size() },
        "Sum of <indexer-post-processor> in '${LIFERAY_HOOK_XML_WEBAPP_PATH}' files:\n${contextNameToIndexerPostProcessors.collect { " - ${it.key}: ${it.value.size()} ${it.value}" }.join('\n')}")

// Features of the Liferay Hook extension point: Service Wrappers
println UASUtils.toCSVLine(
        'custom_code_hook_extensions_service_wrappers',
        contextNameToServiceWrappers.values().sum(0) { it.size() },
        "Sum of <service> in '${LIFERAY_HOOK_XML_WEBAPP_PATH}' files:\n${contextNameToServiceWrappers.collect { " - ${it.key}: ${it.value.size()} ${it.value}" }.join('\n')}")

// Features of the Liferay Hook extension point: Override Portal Properties
println UASUtils.toCSVLine(
        'custom_code_hook_extensions_override_portal_properties',
        contextNameToOverridePortalProperties.values().count(true),
        "Hooks with <portal-properties> in '${LIFERAY_HOOK_XML_WEBAPP_PATH}' files:\n${contextNameToOverridePortalProperties.collect { " - ${it.key}: ${it.value}" }.join('\n')}")

// Application Adapter for a JSP Hook: Yes/No
println UASUtils.toCSVLine(
        'custom_code_hook_extensions_application_adapters',
        contextNameToCustomJspApplicationAdapter.values().count(true),
        "Hooks where <custom-jsp-global> == true in '${LIFERAY_HOOK_XML_WEBAPP_PATH}' files:\n${contextNameToCustomJspApplicationAdapter.collect { " - ${it.key}: ${it.value}" }.join('\n')}")

// Overriding language files: Yes/No
println UASUtils.toCSVLine(
        'custom_code_hook_extensions_override_language_properties',
        contextNameToOverrideLanguageProperties.values().sum(0) { it },
        "Sum of <language-properties> in '${LIFERAY_HOOK_XML_WEBAPP_PATH}' files:\n${contextNameToOverrideLanguageProperties.collect { " - ${it.key}: ${it.value}" }.join('\n')}")


//
// Liferay Service Builder
//

def releases = ReleaseLocalServiceUtil.getReleases(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def isLiferayRelease = { Release r ->
    PluginPackage plugin = allUniqueInstalledPlugins.find { it.context == r.servletContextName }

    //  bundleSymbolicName not present in 6.x, but Release::bundleSymbolicName delegates to Release::servletContextName
    //  (as seen in the impl of 7.0+)
    r.servletContextName == ReleaseConstants.DEFAULT_SERVLET_CONTEXT_NAME ||
            r.servletContextName.startsWith('com.liferay.') ||
            (plugin && plugin.groupId == 'liferay')
}
def liferayReleases = releases.findAll { isLiferayRelease(it) }
def customReleases = releases.findAll { !isLiferayRelease(it) }

// typically around 150 from liferay (in 7.x), so just list the count of excluded ones
//uasUtils.debugCSVLine 'releases', releases
//uasUtils.debugCSVLine 'liferayReleases', liferayReleases

// Liferay Service Builder
println UASUtils.toCSVLine(
        'custom_code_service_builder_modules',
        customReleases.size(),
        "Custom modules from ReleaseLocalServiceUtil::getReleases, excluding ${liferayReleases.size()} authored by 'liferay':\n${customReleases.collect { " - ${it.servletContextName} ${it}" }.join('\n')}")

//
// Fragment-Host count & info
//
def collectFiles
collectFiles = {
    bundle, path, fileList ->

        Enumeration<String> resources = bundle.getEntryPaths(path)

        if (resources != null) {
            while (resources.hasMoreElements()) {
                String resourcePath = resources.nextElement()

                if (!resourcePath.endsWith("/") && (resourcePath.endsWith(".jsp") || resourcePath.endsWith(".jspf"))) {
                    fileList.add(resourcePath)
                } else {
                    collectFiles(bundle, resourcePath, fileList)
                }
            }
        }
}

BundleContext bundleContext = FrameworkUtil.getBundle(DDMContentLocalService.class).getBundleContext()

def fragmentHostInfo = ""
def fragmentHostCount = 0

for (Bundle bundle : bundleContext.getBundles()) {

    headerContent = bundle.getHeaders().get("Fragment-Host");

    if (headerContent != null && !headerContent.isEmpty()) {

        List<String> fileList = new ArrayList<>()

        collectFiles(bundle, "/", fileList)

        if (!fileList.isEmpty()) {
            fragmentHostCount++
            fragmentHostInfo += "\n\t" + bundle.getSymbolicName() + " overrides " + headerContent + " files: " + fileList
        }
    }
}

println UASUtils.toCSVLine("custom_code_fragment_hosts_count", fragmentHostCount, "")
println UASUtils.toCSVLine("custom_code_fragment_hosts_info", fragmentHostInfo, "")
   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '4_custom_code.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__4_custom_code_groovy()
                
// the function 'invoke__4_custom_code_groovy' will be invoked at the very end of the 'dxp_7_1-all.groovy'
// invoke__4_custom_code_groovy([ debug: false ])              
// END included '4_custom_code.groovy'

// invoke the topic scripts' wrapper functions                         
def uasContext = [ debug: false ]

invoke__1_infra_groovy(uasContext)
invoke__2_data_groovy(uasContext)
invoke__3_database_info_groovy(uasContext)
invoke__4_custom_code_groovy(uasContext)
  
// BEGIN common/_combined-footer.groovy

import groovy.time.TimeCategory 
import groovy.time.TimeDuration

// 'Map uasMetadata' should have been defined in _combined-header.groovy
uasMetadata['endDate'] = new Date()
uasMetadata['endDateFormatted'] = uasMetadata['endDate'].format(DEFAULT_DATE_FORMAT)
uasMetadata['duration'] =
        uasMetadata['startDate'] ?
            TimeCategory.minus( uasMetadata['endDate'], uasMetadata['startDate'] ) :
            'n/a'
println """\
    ${UASUtils.toCSVLine('uas_version', uasMetadata['projectVersion'])}
    ${UASUtils.toCSVLine('uas_target_liferay_version', uasMetadata['sourceSetLabel'])}
    ${UASUtils.toCSVLine('uas_start_date', uasMetadata['startDateFormatted'])}  
    ${UASUtils.toCSVLine('uas_end_date', uasMetadata['endDateFormatted'])}  
    ${UASUtils.toCSVLine('uas_duration', uasMetadata['duration'])}  
    """.stripIndent()

// END common/_combined-footer.groovy
