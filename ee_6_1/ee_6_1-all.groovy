// BEGIN common/_combined-header.groovy

def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"

// the @...@ tokens can in theory contain ' or ", so use a safe way to write these as Groovy String literals
def uasMetadata = [
        projectVersion: """\
                0.11.2
            """.trim(),
        sourceSetLabel: """\
                Liferay Portal EE 6.1
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
import com.liferay.portal.kernel.search.SearchEngine
import com.liferay.portal.kernel.search.SearchEngineUtil
import com.liferay.portal.kernel.util.ReleaseInfo
import com.liferay.portal.kernel.util.ServerDetector
import com.liferay.portal.kernel.util.PropsUtil
              
// Wrapper function for '1_infra.groovy'
def invoke__1_infra_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.jdbc.DataAccess
//import com.liferay.portal.kernel.patcher.PatcherUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.search.SearchEngine
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.search.SearchEngineUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.ReleaseInfo
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.ServerDetector
//import com.liferay.portal.scripting.groovy.internal.GroovyExecutor
//import com.liferay.portal.search.engine.SearchEngineInformation
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsUtil
//import org.osgi.framework.FrameworkUtil
//import org.osgi.util.tracker.ServiceTracker


println '# Infrastructure (question keys: infra_*)'

// Version
println """\

## Liferay Version
${UASUtils.toCSVLine('infra_liferay_version', ReleaseInfo.getVersion(), ReleaseInfo.getReleaseInfo())}
"""


// Patches
// Class is not present in every 6.1, but only some service packs later
def patcherUtilClassName = 'com.liferay.portal.kernel.patcher.PatcherUtil'
boolean hasPatcherUtil = false
Throwable hasPatcherUtilException
try {
    hasPatcherUtil = (Class.forName(patcherUtilClassName) != null)
} catch(Throwable t) {
    println "${patcherUtilClassName} cannot be found: ${t}"
    hasPatcherUtilException = t
}
def patches
def patchesDetails

if (hasPatcherUtil) {
    patches = com.liferay.portal.kernel.patcher.PatcherUtil.getInstalledPatches().size()
    def fixedIssues = com.liferay.portal.kernel.patcher.PatcherUtil.getFixedIssues()
    def fixedIssuesDisplayedMax = 10
    def fixedIssuesDisplayed =
            (fixedIssues.size() <= fixedIssuesDisplayedMax) ?
                    fixedIssues :
                    fixedIssues.toList().subList(0, fixedIssuesDisplayedMax).toArray()

    patchesDetails = """\
Via PatcherUtil:
- installed patches: ${com.liferay.portal.kernel.patcher.PatcherUtil.getInstalledPatches().join(', ')}
- fixed issues: ${fixedIssuesDisplayed.join(', ')}\
${fixedIssues.size() > fixedIssuesDisplayedMax ? "... (${fixedIssues.size()} total)" : ''}
- patch levels: ${com.liferay.portal.kernel.patcher.PatcherUtil.getPatchLevels().join(', ')}
- separation ID: n/a (7.1+ only)
- patching tool: n/a (7.0+ only)"""
} else {
    patches = "-1"
    patchesDetails = """\
Class 'patcherUtilClassName' could not be found:
${hasPatcherUtilException}"""
}

println """\
## Liferay Patches
${UASUtils.toCSVLine('infra_liferay_patches', patches, patchesDetails)} 
"""


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

println """\
## App Server
${UASUtils.toCSVLine('infra_app_server_id', appServerId, 'Via ServerDetector::getServerId')} 
${UASUtils.toCSVLine('infra_app_server', appServer, appServerComment)} 
"""


// JVM
def jvm = System.getProperty('java.version')
def jvmDetails = """\
Via System properties of the JVM:
- vendor: ${System.getProperty('java.vendor', 'unknown')} 
- version: ${System.getProperty('java.version')}
- vendor.url: ${System.getProperty('java.vendor.url')}"""

println """\
## Java
${UASUtils.toCSVLine('infra_jvm', jvm, jvmDetails)}
"""


// OS
def os = System.getProperty('os.name',  'unknown')
def osDetails = """\
Via System properties of the JVM:
- name: ${System.getProperty('os.name')}
- version: ${System.getProperty('os.version')}
- arch: ${System.getProperty('os.arch')}"""

println """\
## OS 
${UASUtils.toCSVLine('infra_os', os, osDetails)}   
"""


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

println """\
## Database
${UASUtils.toCSVLine('infra_database', database, databaseDetails)}
${UASUtils.toCSVLine('infra_database_jdbc', databaseJdbc, databaseJdbcDetails)}
"""


// Search Information
// There's no OSGi in 6.x
// TODO: Cannot get "client version" -- not in the API (~ the SearchEngine class)
// TODO no SearchEngineUtil::getSearchEngineIds in 6.1.10-ga1, but added later?
def hasSearchEnginesIdsMethod = SearchEngineUtil.getMethods().find { it.getName() == 'getSearchEngineIds' }
def searchEnginesIds =
        hasSearchEnginesIdsMethod ?
                SearchEngineUtil.getSearchEngineIds() :
                [ SearchEngineUtil.SYSTEM_ENGINE_ID, SearchEngineUtil.GENERIC_ENGINE_ID ]

Map<String, SearchEngine> searchEnginesById = [:]
searchEnginesIds.each { searchEnginesById[it] = SearchEngineUtil.getSearchEngine(it) }

// no SearchEngineUtil::getDefaultSearchEngineId in 6.1.10-ga1
def searchEngine = searchEnginesById[SearchEngineUtil.SYSTEM_ENGINE_ID].getVendor()
def searchEngineDetails = """\
Via ${hasSearchEnginesIdsMethod ? 'SearchEngineUtil::getSearchEngineIds' : 'SearchEngineUtil::getSearchEngine for SYSTEM + GENERIC engine IDs'}:
${searchEnginesById.collect {"- ${it.key} { vendor=${it.value.getVendor()} }"}.join('\n')}"""


println """\
## Search
${UASUtils.toCSVLine('infra_search', searchEngine, searchEngineDetails)}
"""   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '1_infra.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__1_infra_groovy()
                
// the function 'invoke__1_infra_groovy' will be invoked at the very end of the 'ee_6_1-all.groovy'
// invoke__1_infra_groovy([ debug: false ])              
// END included '1_infra.groovy'

// BEGIN included '2_data.groovy'  

// Imports for '2_data.groovy' 
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
import com.liferay.portlet.expando.model.ExpandoTable
import com.liferay.portlet.expando.service.ExpandoTableLocalServiceUtil
import com.liferay.portlet.expando.util.ExpandoBridgeFactoryUtil
import com.liferay.portlet.journal.model.JournalTemplate
import com.liferay.portlet.journal.model.JournalTemplateConstants
import com.liferay.portlet.journal.service.JournalArticleLocalServiceUtil
import com.liferay.portal.kernel.dao.orm.QueryUtil
import com.liferay.portal.security.auth.CompanyThreadLocal
import com.liferay.portal.service.GroupLocalServiceUtil
import com.liferay.portal.service.LayoutLocalServiceUtil
import com.liferay.portal.service.PortletPreferencesLocalServiceUtil
import com.liferay.portal.kernel.util.PropsUtil
import com.liferay.portal.service.ClassNameLocalServiceUtil
import com.liferay.portal.service.CompanyLocalServiceUtil
import com.liferay.portal.service.UserLocalServiceUtil
import com.liferay.portlet.journal.service.JournalTemplateLocalServiceUtil
              
// Wrapper function for '2_data.groovy'
def invoke__2_data_groovy(Map<String, Object> uasContext) {

    try {
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
//__GRADLE_COMMENT__ import com.liferay.portlet.expando.model.ExpandoTable
//__GRADLE_COMMENT__ import com.liferay.portlet.expando.service.ExpandoTableLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.expando.util.ExpandoBridgeFactoryUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.journal.model.JournalTemplate
//__GRADLE_COMMENT__ import com.liferay.portlet.journal.model.JournalTemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portlet.journal.service.JournalArticleLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.orm.QueryUtil
//__GRADLE_COMMENT__ import com.liferay.portal.security.auth.CompanyThreadLocal
//__GRADLE_COMMENT__ import com.liferay.portal.service.GroupLocalServiceUtil
//import com.liferay.portal.kernel.service.LayoutFriendlyURLLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.LayoutLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.PortletPreferencesLocalServiceUtil
//import com.liferay.portal.kernel.template.TemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.ClassNameLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.CompanyLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.service.UserLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portlet.journal.service.JournalTemplateLocalServiceUtil
//import com.liferay.fragment.service.FragmentEntryLocalServiceUtil
//import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalServiceUtil

println '# Data (question keys: data_*)'
def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"

// Groups and Sites
def groups = GroupLocalServiceUtil.getGroups(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def sites = groups.findAll { it.site }

// DDM
// 6.1 does not use DDM for WC templates
List<JournalTemplate> webContentTemplates = JournalTemplateLocalServiceUtil.getJournalTemplates(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def webContentTemplatesCount = JournalTemplateLocalServiceUtil.getJournalTemplatesCount()

// ADTs ony in 6.2+
List<DDMTemplate> applicationDisplayTemplates = []
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

println """
## Sites (and Staging)
"""

println UASUtils.toCSVLine('companyId', 'groupId', 'friendlyURL', 'name', 'staging')

sites.each { it ->
    def liveGroup = StagingUtil.getLiveGroup(it.groupId)

    println UASUtils.toCSVLine(it.companyId, it.groupId, it.friendlyURL, it.name, liveGroup.staged) 
}

println """
    User Public Pages Enabled: ${PropsUtil.get('layout.user.public.layouts.enabled')}
    User Private Pages Enabled: ${PropsUtil.get('layout.user.private.layouts.enabled')}
    """.stripIndent()

println """
## Data size"""

println """\
    Total Companies: ${companiesCount}
    Total Users: ${usersCount}
    Total Layouts: ${layoutsCount}
    Total Layouts FriendlyURLs: n/a (7.0+)
    """.stripIndent()

if (layoutsCount > usersCount) {
    println """\
        Total Users Layouts: ${(usersCount * 2)}
        Total Custom Layouts: ${(layoutsCount - (usersCount * 2))}
        """.stripIndent()
}

println """\
    Total DDMStructures: ${dDMStructuresCount}
    Total DDMTemplates: ${dDMTemplatesCount}
    Total Custom Web Content Templates (WC): ${webContentTemplatesCount}
    Total Custom Application Display Templates (ADT): n/a (6.2+)
    Total DDMContentsCount: ${dDMContentsCount}
    Total JournalArticles: ${journalArticlesCount}
    Total DLFiles: ${dLFileEntriesCount}
    Total Fragments: n/a (7.1+)
    Total Page Templates: n/a (7.1+)
    
    Total Used Liferay Portlets: ${liferayPortlets.size()}
${liferayPortlets.collect { "      * ${it }" }.join('\n')}
    Total Used Custom Portlets: ${customPortlets.size()}
${customPortlets.collect { "      * ${it}" }.join('\n')}
    """.stripIndent()

def webContentTemplatesByLang =
        webContentTemplates.groupBy { it.langType }

JournalTemplateConstants.LANG_TYPES.each {supportedLang ->
    webContentTemplatesByLang.putIfAbsent(supportedLang, Collections.emptyList())
}

webContentTemplatesByLang.each { lang, templates ->
    println UASUtils.toCSVLine(
            "data_wc_templates_by_lang_${lang}_count", templates.size(),
            "Custom WC templates with '${lang}' as the language:\n${templates.collect { " - ${it.nameCurrentValue} { templateId=${it.templateId}, templateKey=n/a }" }.join('\n')}")
}

def adtTemplatesByLang =
        applicationDisplayTemplates.groupBy { DDMTemplate it -> it.language }

DDMTemplateConstants.LANG_TYPES.each { supportedLang ->
    adtTemplatesByLang.putIfAbsent(supportedLang, Collections.emptyList())
}

adtTemplatesByLang.each { lang, templates ->
    println UASUtils.toCSVLine(
            "data_adt_templates_by_lang_${lang}_count", templates.size(),
            "Only in 6.2+")
}

println """
## Pages"""

def uniqueFriendlyURLs = layouts.collect{it -> return [
    "companyId":it.companyId, 
    "groupId":it.groupId,  
    "privateLayout":it.privateLayout, 
    "friendlyURL":it.friendlyURL,
    "createDate":it.createDate ? it.createDate.format(DEFAULT_DATE_FORMAT) : '']}

uniqueFriendlyURLs = uniqueFriendlyURLs.sort{x,y -> 
    x.companyId <=> y.companyId ?: 
    x.groupId <=> y.groupId ?: 
    x.privateLayout <=> y.privateLayout ?: 
    x.friendlyURL <=> y.friendlyURL
}

println """\
    Total Pages: ${layoutsCount}
    Total Versions: n/a (7.0+)
""".stripIndent()

println UASUtils.toCSVLine('companyId', 'groupId', 'friendlyURL', 'privateLayout', 'createDate')

uniqueFriendlyURLs.each {
    println UASUtils.toCSVLine(it.companyId, it.groupId, it.friendlyURL, it.privateLayout, it.createDate)
}

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
                
// the function 'invoke__2_data_groovy' will be invoked at the very end of the 'ee_6_1-all.groovy'
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
                
// the function 'invoke__3_database_info_groovy' will be invoked at the very end of the 'ee_6_1-all.groovy'
// invoke__3_database_info_groovy([ debug: false ])              
// END included '3_database_info.groovy'

// invoke the topic scripts' wrapper functions                         
def uasContext = [ debug: false ]

invoke__1_infra_groovy(uasContext)
invoke__2_data_groovy(uasContext)
invoke__3_database_info_groovy(uasContext)
  
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
