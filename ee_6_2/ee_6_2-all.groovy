// BEGIN common/_combined-header.groovy

def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"

// the @...@ tokens can in theory contain ' or ", so use a safe way to write these as Groovy String literals
def uasMetadata = [
        projectVersion: """\
                0.11.2
            """.trim(),
        sourceSetLabel: """\
                Liferay Portal EE 6.2
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
import com.liferay.portal.kernel.search.SearchEngineUtil
import com.liferay.portal.kernel.util.ReleaseInfo
import com.liferay.portal.kernel.util.ServerDetector
import com.liferay.portal.kernel.util.PropsUtil
              
// Wrapper function for '1_infra.groovy'
def invoke__1_infra_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.jdbc.DataAccess
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.patcher.PatcherUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.search.SearchEngine
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.search.SearchEngineUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.ReleaseInfo
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.ServerDetector
//import com.liferay.portal.scripting.groovy.internal.GroovyExecutor
//import com.liferay.portal.search.engine.SearchEngineInformation
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsUtil
//import org.osgi.framework.FrameworkUtil
//import org.osgi.util.tracker.ServiceTracker

println("${UASUtils.toCSVLine('Key', 'Value', 'Description')}")

// Version
println("${UASUtils.toCSVLine('infra_liferay_version', ReleaseInfo.getVersion(), ReleaseInfo.getReleaseInfo())}")


// Patches
def patches = PatcherUtil.getInstalledPatches().size()
def fixedIssues = PatcherUtil.getFixedIssues()
def fixedIssuesDisplayedMax = 10
def fixedIssuesDisplayed =
        (fixedIssues.size() <= fixedIssuesDisplayedMax) ?
                fixedIssues :
                fixedIssues.toList().subList(0, fixedIssuesDisplayedMax).toArray()

def patchesDetails = """\
Via PatcherUtil:
- installed patches: ${PatcherUtil.getInstalledPatches().join(', ')}
- fixed issues: ${fixedIssuesDisplayed.join(', ')}\
${fixedIssues.size() > fixedIssuesDisplayedMax ? "... (${fixedIssues.size()} total)" : ''}
- patch levels: ${PatcherUtil.getPatchLevels().join(', ')}
- separation ID: n/a (7.1+ only)
- patching tool: n/a (7.0+ only)"""

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
// There's no OSGi in 6.x
// TODO: Can't get "client version" -- not in the API (~ the SearchEngine class)
def searchEnginesIds =
        SearchEngineUtil.getSearchEngineIds()

Map<String, SearchEngine> searchEnginesById = [:]
searchEnginesIds.each { searchEnginesById[it] = SearchEngineUtil.getSearchEngine(it) }

def searchEngine = searchEnginesById[SearchEngineUtil.getDefaultSearchEngineId()].getVendor()
def searchEngineDetails = """\
Via SearchEngineUtil::getSearchEngineIds:
${searchEnginesById.collect {"- ${it.key} { vendor=${it.value.getVendor()} }"}.join('\n')}"""

println("${UASUtils.toCSVLine('infra_search', searchEngine, searchEngineDetails)}")   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '1_infra.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__1_infra_groovy()
                
// the function 'invoke__1_infra_groovy' will be invoked at the very end of the 'ee_6_2-all.groovy'
// invoke__1_infra_groovy([ debug: false ])              
// END included '1_infra.groovy'

// BEGIN included '2_data.groovy'  

// Imports for '2_data.groovy' 
import com.liferay.portal.kernel.template.TemplateConstants
import com.liferay.portal.service.PortletLocalServiceUtil
import com.liferay.portlet.documentlibrary.service.DLFileEntryLocalServiceUtil
import com.liferay.portlet.dynamicdatamapping.model.DDMStructure
import com.liferay.portlet.dynamicdatamapping.model.DDMTemplate
import com.liferay.portlet.dynamicdatamapping.model.DDMTemplateConstants
import com.liferay.portlet.dynamicdatamapping.service.DDMStructureLocalServiceUtil
import com.liferay.portlet.dynamicdatamapping.service.DDMTemplateLocalServiceUtil
import com.liferay.portlet.dynamicdatamapping.service.DDMContentLocalServiceUtil
import com.liferay.portal.kernel.staging.StagingUtil
import com.liferay.portlet.expando.model.ExpandoBridge
import com.liferay.portlet.expando.util.ExpandoBridgeFactoryUtil
import com.liferay.portlet.expando.model.ExpandoTable
import com.liferay.portlet.expando.service.ExpandoTableLocalServiceUtil
import com.liferay.portlet.journal.service.JournalArticleLocalServiceUtil
import com.liferay.portal.kernel.dao.orm.QueryUtil
import com.liferay.portal.kernel.util.PropsUtil
import com.liferay.portal.kernel.util.PropsKeys
import com.liferay.portal.security.auth.CompanyThreadLocal
import com.liferay.portal.service.ClassNameLocalServiceUtil
import com.liferay.portal.service.CompanyLocalServiceUtil
import com.liferay.portal.service.GroupLocalServiceUtil
import com.liferay.portal.service.LayoutLocalServiceUtil
import com.liferay.portal.service.PortletPreferencesLocalServiceUtil
import com.liferay.portal.service.UserLocalServiceUtil
import com.liferay.portlet.journal.service.JournalTemplateLocalServiceUtil
              
// Wrapper function for '2_data.groovy'
def invoke__2_data_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.template.TemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portal.service.PortletLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.documentlibrary.service.DLFileEntryLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.dynamicdatamapping.model.DDMStructure
//__GRADLE_COMMENT__ import com.liferay.portlet.dynamicdatamapping.model.DDMTemplate
//__GRADLE_COMMENT__ import com.liferay.portlet.dynamicdatamapping.model.DDMTemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portlet.dynamicdatamapping.service.DDMStructureLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.dynamicdatamapping.service.DDMTemplateLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.dynamicdatamapping.service.DDMContentLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.staging.StagingUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.expando.model.ExpandoBridge
//__GRADLE_COMMENT__ import com.liferay.portlet.expando.util.ExpandoBridgeFactoryUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.expando.model.ExpandoTable
//__GRADLE_COMMENT__ import com.liferay.portlet.expando.service.ExpandoTableLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.journal.service.JournalArticleLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.orm.QueryUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsKeys
//__GRADLE_COMMENT__ import com.liferay.portal.security.auth.CompanyThreadLocal
//__GRADLE_COMMENT__ import com.liferay.portal.service.ClassNameLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.CompanyLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.GroupLocalServiceUtil
//import com.liferay.portal.kernel.service.LayoutFriendlyURLLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.LayoutLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.PortletPreferencesLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.UserLocalServiceUtil
//import com.liferay.portal.kernel.template.TemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portlet.journal.service.JournalTemplateLocalServiceUtil
//import com.liferay.fragment.service.FragmentEntryLocalServiceUtil
//import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalServiceUtil

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
def layouts = LayoutLocalServiceUtil.getLayouts(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def layoutsCount = LayoutLocalServiceUtil.getLayoutsCount()
def journalArticlesCount = JournalArticleLocalServiceUtil.getJournalArticlesCount()
def dLFileEntriesCount = DLFileEntryLocalServiceUtil.getDLFileEntriesCount()
// There is no LayoutFriendlyURLLocalServiceUtil until 7.0+
//def totalLayoutFriendlyURLs = LayoutFriendlyURLLocalServiceUtil.getLayoutFriendlyURLsCount()
//def layoutFriendlyURLs = LayoutFriendlyURLLocalServiceUtil.getLayoutFriendlyURLs(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
//def uniqueFriendlyURLs =
//      layoutFriendlyURLs.findAll { it.mvccVersion == 1 }
//def totalFragments = FragmentEntryLocalServiceUtil.getFragmentEntriesCount()
//def totalPageTemplates = LayoutPageTemplateEntryLocalServiceUtil.getLayoutPageTemplateEntriesCount()

// Portlets
def portletPreferences = PortletPreferencesLocalServiceUtil.getPortletPreferences()
def portletIds = portletPreferences.collect {
    it.portletId - ~/_INSTANCE.*/
}.unique()

// before 7.0, the portlet IDs are numeric, not saying much
def portlets = portletIds.collect { portletId ->
    def portlet = PortletLocalServiceUtil.getPortletById(portletId)
    return "${portletId.padRight(3)} { displayName=${portlet?.getDisplayName()}, pluginPackageId=${portlet?.getPluginPackage()?.getPackageId()} }"
}.sort()

def liferayPortlets =
        portlets.findAll { it.contains('liferay') }
def customPortlets =
        portlets.findAll { !it.contains('liferay') }

def webContentTemplatesByLang =
        webContentTemplates.groupBy { DDMTemplate it -> it.language }

[
        TemplateConstants.LANG_TYPE_VM,
        TemplateConstants.LANG_TYPE_FTL,
        TemplateConstants.LANG_TYPE_XSL
].each { supportedLang ->
    if (!webContentTemplates.contains(supportedLang)) {
        webContentTemplatesByLang.put(supportedLang, Collections.emptyList())
    }
}

def adtTemplatesByLang =
        applicationDisplayTemplates.groupBy { DDMTemplate it -> it.language }

[
        TemplateConstants.LANG_TYPE_VM,
        TemplateConstants.LANG_TYPE_FTL
].each { supportedLang ->
    if (!adtTemplatesByLang.containsKey(supportedLang)) {
        adtTemplatesByLang.put(supportedLang, Collections.emptyList())
    }
}

// Questions keys

println UASUtils.toCSVLine("data_companies_count", "${companiesCount}", "")
println UASUtils.toCSVLine("data_custom_application_display_templates_adt_count", "${applicationDisplayTemplatesCount}", "")
println UASUtils.toCSVLine("data_custom_web_content_templates_wc_count", "${webContentTemplatesCount}", "")
println UASUtils.toCSVLine("data_ddm_contents_count", "${dDMContentsCount}", "")
println UASUtils.toCSVLine("data_ddm_structures_count", "${dDMStructuresCount}", "")
println UASUtils.toCSVLine("data_ddm_templates_count", "${dDMTemplatesCount}", "")
println UASUtils.toCSVLine("data_dl_files_count", "${dLFileEntriesCount}", "")
println UASUtils.toCSVLine("data_journal_articles_count", "${journalArticlesCount}", "")
println UASUtils.toCSVLine("data_layouts_count", "${layoutsCount}", "")
println UASUtils.toCSVLine("data_used_custom_portlets_count", "${customPortlets.size()}", "\n${customPortlets.collect { "      * ${it}" }.join('\n')}")
println UASUtils.toCSVLine("data_used_liferay_portlets_count", "${liferayPortlets.size()}", "\n${liferayPortlets.collect { "      * ${it}" }.join('\n')}")
println UASUtils.toCSVLine("data_user_private_pages_enabled", "${com.liferay.portal.util.PropsUtil.get('layout.user.private.layouts.enabled')}", "")
println UASUtils.toCSVLine("data_user_public_pages_enabled", "${com.liferay.portal.util.PropsUtil.get('layout.user.public.layouts.enabled')}", "")
println UASUtils.toCSVLine("data_users_count", "${usersCount}", "")


if (layoutsCount > usersCount) {
    println UASUtils.toCSVLine("data_users_layouts_count", "${(usersCount * 2)}", "")
    println UASUtils.toCSVLine("data_custom_layouts_count", "${(layoutsCount - (usersCount * 2))}", "")
}

webContentTemplatesByLang.each { lang, templates ->
    println UASUtils.toCSVLine(
            "data_wc_templates_by_lang_${lang}_count", templates.size(),
            "Custom WC templates with '${lang}' as the language:\n${templates.collect { " - ${it.nameCurrentValue} { templateId=${it.templateId}, templateKey=${it.templateKey} }" }.join('\n')}")
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

    siteDetails <<= "\n\t${type}, ${it.companyId}, ${it.groupId}, ${it.friendlyURL}, ${it.name}, ${liveGroup.staged}, ${parentGroupName}"
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

// Expando Fields
int expandoTablesCount = 0;
String expandoTableDetails = "ExpandoClassName, ExpandoColumnsCount";
List<ExpandoTable> expandoTables =
        ExpandoTableLocalServiceUtil.getExpandoTables(QueryUtil.ALL_POS, QueryUtil.ALL_POS);

for (e in  expandoTables) {
    String expandoClassName =
            ClassNameLocalServiceUtil.fetchClassName(e.getClassNameId()).value;

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
                
// the function 'invoke__2_data_groovy' will be invoked at the very end of the 'ee_6_2-all.groovy'
// invoke__2_data_groovy([ debug: false ])              
// END included '2_data.groovy'

// BEGIN included '3_database_info.groovy'  

// Imports for '3_database_info.groovy' 
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBFactoryUtil;
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
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.db.DBFactoryUtil;
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

    DB db = DBFactoryUtil.getDB();

    String dbType = db.getType();

    try {
        connection = DataAccess.getConnection();

        DatabaseMetaData metadata = connection.getMetaData();

        String catalog = connection.getCatalog();
        String schema = null;

        if ((catalog == null) && (dbType.equals(DB.TYPE_ORACLE))) {
            catalog = metadata.getUserName();
            schema = catalog;
        }

        rs = metadata.getTables(catalog, schema, "%", null);

        List<TableInfo> tables = new ArrayList<TableInfo>();

        while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            String tableType = rs.getString("TABLE_TYPE");

            if (!"TABLE".equals(tableType)) {
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
                
// the function 'invoke__3_database_info_groovy' will be invoked at the very end of the 'ee_6_2-all.groovy'
// invoke__3_database_info_groovy([ debug: false ])              
// END included '3_database_info.groovy'

// BEGIN included '4_custom_code.groovy'  

// Imports for '4_custom_code.groovy' 
import com.liferay.portal.deploy.DeployUtil
import com.liferay.portal.deploy.auto.HookAutoDeployListener
import com.liferay.portal.kernel.dao.orm.QueryUtil
import com.liferay.portal.kernel.plugin.PluginPackage
import com.liferay.portal.model.LayoutTemplate
import com.liferay.portal.model.Release
import com.liferay.portal.model.ReleaseConstants
import com.liferay.portal.plugin.PluginPackageUtil
import com.liferay.portal.service.LayoutTemplateLocalServiceUtil
import com.liferay.portal.service.PortletServiceUtil
import com.liferay.portal.service.ReleaseLocalServiceUtil
import com.liferay.portal.service.ThemeLocalServiceUtil
import com.liferay.portal.kernel.template.TemplateConstants
import com.liferay.portal.util.PortalUtil
import com.liferay.portal.kernel.util.PropsKeys
import com.liferay.portal.kernel.util.PropsUtil
import com.liferay.portal.kernel.xml.Document
import com.liferay.portal.kernel.xml.SAXReaderUtil
              
// Wrapper function for '4_custom_code.groovy'
def invoke__4_custom_code_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.portal.deploy.DeployUtil
//__GRADLE_COMMENT__ import com.liferay.portal.deploy.auto.HookAutoDeployListener
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.orm.QueryUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.plugin.PluginPackage
//__GRADLE_COMMENT__ import com.liferay.portal.model.LayoutTemplate
//__GRADLE_COMMENT__ import com.liferay.portal.model.Release
//__GRADLE_COMMENT__ import com.liferay.portal.model.ReleaseConstants
//__GRADLE_COMMENT__ import com.liferay.portal.plugin.PluginPackageUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.LayoutTemplateLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.PortletServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.ReleaseLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.ThemeLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.template.TemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portal.util.PortalUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsKeys
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.xml.Document
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.xml.SAXReaderUtil
//import com.liferay.portal.kernel.zip.ZipFileUtil


// 'uasContext' is a parameter to the wrapper function for this script when combining it with the others;
// see build.gradle -> buildTopicScriptsForSourceSet / buildCombinedScriptsForSourceSet
def uasUtils = new UASUtils(this, uasContext)

// 6.x vs. 7.0 vs. 7.1+ differences
// No OSGi in 6.x
String _PROPS_VALUES_MODULE_FRAMEWORK_MARKETPLACE_DIR =
        "n-a-in-6-x"
String _PROPS_VALUES_MODULE_FRAMEWORK_MODULES_DIR =
        "n-a-in-6-x"
String _PROPS_VALUES_MODULE_FRAMEWORK_WAR_DIR =
        "n-a-in-6-x"

// LayoutTemplateLocalServiceUtil
def _LayoutTemplateLocalServiceUtil_getLangType = { LayoutTemplate it ->
    // Method LayoutTemplateLocalServiceUtil::getLangType not present in 7.0-ga1; so inline the gist of the method
    if (it.templatePath.endsWith(".${TemplateConstants.LANG_TYPE_VM}")) {
        return TemplateConstants.LANG_TYPE_VM
    } else if (it.templatePath.endsWith(".${TemplateConstants.LANG_TYPE_FTL}")) {
        return TemplateConstants.LANG_TYPE_FTL
    } else {
        return TemplateConstants.LANG_TYPE_VM
    }
}

def allUniqueInstalledPlugins =
        PluginPackageUtil.getInstalledPluginPackages().sort().reverse().unique { it.moduleId }

// custom_code_all_installed_plugins_count
println UASUtils.toCSVLine(
        'custom_code_all_installed_plugins_count', allUniqueInstalledPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages, custom ones and also the ones from 'liferay':\n${allUniqueInstalledPlugins.collect { " - ${it}" }.join('\n')}")

uasUtils.debugCSVLine "allUniqueInstalledPlugins (${allUniqueInstalledPlugins.size()})", """${allUniqueInstalledPlugins.collect {"* ${it}\n${it.properties.collect { "  - ${it.key}: ${it.value}" }.join('\n')}"}.join('\n')}"""

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

def customThemesByLang = customThemes.groupBy { it.templateExtension }
themesSupportedLangs.each { lang ->
    if (!customThemesByLang.containsKey(lang)) {
        customThemesByLang.put(lang, Collections.emptyList())
    }
}

customThemesByLang.each { lang, langThemes ->
    println UASUtils.toCSVLine(
            "custom_code_themes_by_lang_${lang}_count", langThemes.size(),
            "Custom themes with '${lang}' as the language:\n${langThemes.collect { " - ${it.name} { themeId=${it.themeId}, servletContextName=${it.servletContextName} }" }.join('\n')}")
}

// layout

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

def layoutTemplatesSupportedLangs = [ TemplateConstants.LANG_TYPE_VM, TemplateConstants.LANG_TYPE_FTL ]

def customLayoutTemplatesByLang =
        customLayoutTemplates.groupBy {
            _LayoutTemplateLocalServiceUtil_getLangType(it)
        }
layoutTemplatesSupportedLangs.each { lang ->
    if (!customLayoutTemplatesByLang.containsKey(lang)) {
        customLayoutTemplatesByLang.put(lang, Collections.emptyList())
    }
}

customLayoutTemplatesByLang.each { lang, langLayoutTemplates ->
    println UASUtils.toCSVLine(
            "custom_code_layout_templates_by_lang_${lang}_count", langLayoutTemplates.size(),
            "Custom layout templates with '${lang}' as the language:\n${langLayoutTemplates.collect { " - ${it.name} { layoutTemplateId=${it.layoutTemplateId}, servletContextName=${it.servletContextName} }" }.join('\n')}")
}

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

def portlets = []
def portletsJsonArray = PortletServiceUtil.getWARPortlets()

for(int i = 0; i < portletsJsonArray.length(); ++i) {
    def portletsJsonArrayItem = portletsJsonArray.getJSONObject(i)
    portlets.add([
            portlet_name: portletsJsonArrayItem.getString('portlet_name'),
            servlet_context_name: portletsJsonArrayItem.getString('servlet_context_name')
    ])
}

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


println UASUtils.toCSVLine(
        'custom_code_portlets_count', customPortlets.size(),
        "Via PortletServiceUtil::getWARPortlets, excluding ${liferayPortlets.size()} authored by 'liferay':\n${customPortlets.collect { " - ${it.portlet_name} { servletContextName=${it.servlet_context_name} }" }.join('\n')}")

// Liferay Service Builder

def releases = ReleaseLocalServiceUtil.getReleases(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def isLiferayRelease = { Release r ->
    PluginPackage plugin = allUniqueInstalledPlugins.find { it.context == r.servletContextName }

    r.servletContextName == ReleaseConstants.DEFAULT_SERVLET_CONTEXT_NAME ||
            r.servletContextName.startsWith('com.liferay.') ||
            (plugin && plugin.groupId == 'liferay')
}
def liferayReleases = releases.findAll { isLiferayRelease(it) }
def customReleases = releases.findAll { !isLiferayRelease(it) }


println UASUtils.toCSVLine(
        'custom_code_service_builder_modules',
        customReleases.size(),
        "Custom modules from ReleaseLocalServiceUtil::getReleases, excluding ${liferayReleases.size()} authored by 'liferay':\n${customReleases.collect { " - ${it.servletContextName} ${it}" }.join('\n')}")

// Webs

def webPlugins = allUniqueInstalledPlugins.findAll {
    'web' in it.types || it.name.endsWith('-web')
}

println UASUtils.toCSVLine(
        'custom_code_web_plugins_count', webPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages:\n${webPlugins.collect { " - ${it.moduleId}" }.join('\n')}")

   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '4_custom_code.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__4_custom_code_groovy()
                
// the function 'invoke__4_custom_code_groovy' will be invoked at the very end of the 'ee_6_2-all.groovy'
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
