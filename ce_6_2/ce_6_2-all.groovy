// Liferay Portal Community Edition (CE) 6.2 - upgrade assessment script (read-only)
// Run on a UAT / test copy of Liferay Portal CE 6.2 (any GA), never on production, at your own risk.
// Control Panel > Server Administration > Script (language: Groovy). Save the output as a .txt file.
// Output is CSV: "Key", "Value", "Description" - same keys as ee_6_2-all.groovy, plus ce_* keys.

// BEGIN common/_combined-header.groovy

def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"

// ---------------------------------------------------------------------------------------------------------------
// PRIVACY OPTION
// Set to true to mask site names, site URLs, virtual instance names / web IDs, virtual hosts, host names in URLs,
// e-mail addresses, IP addresses, the database location and installation paths in the output. All counts and
// technical details are kept, so the report can still be assessed.
def UAS_MASK_PRIVATE_DATA = false
// ---------------------------------------------------------------------------------------------------------------

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

    // ---- Privacy option: see UAS_MASK_PRIVATE_DATA at the top of the script and '_uas_privacy.groovy' ----
    static boolean maskEnabled = false
    static Map<String, String> maskTokens = [:]
    static Map<String, String> maskPseudonyms = [:]
    static Map<String, Integer> maskCounters = [:]
    static java.util.regex.Pattern maskTokenPattern = null
    private static final List<String> _MASK_IGNORED = [
            'guest', 'global', '/guest', '/global', 'liferay', 'liferay.com', 'liferay dxp', 'liferay portal',
            'localhost', 'control panel', '/control_panel', 'true', 'false', 'null', 'default']

    static String pseudonym(String category, String value) {
        def key = category + '|' + value
        if (!maskPseudonyms.containsKey(key)) {
            def n = (maskCounters[category] ?: 0) + 1
            maskCounters[category] = n
            maskPseudonyms[key] = (category + '-' + n).toString()
        }
        return maskPseudonyms[key]
    }

    // entity: groupId / companyId, so that all names of one site (all languages, raw / localized) get the same placeholder
    static void addMaskToken(String category, Object value, Object entity = null) {
        def text = value?.toString()?.trim()
        if (!text || text.length() < 3 || (text.toLowerCase() in _MASK_IGNORED) || maskTokens.containsKey(text)) {
            return
        }
        maskTokens[text] = pseudonym(category, entity != null ? 'id:' + entity : text)
    }

    static void compileMaskTokens() {
        if (maskTokens) {
            def alternatives = maskTokens.keySet().sort { -it.length() }.collect { token ->
                def quoted = java.util.regex.Pattern.quote(token)
                (Character.isLetterOrDigit(token.charAt(0)) ? '(?<![\\w-])' : '') + quoted + '(?![\\w-])'
            }
            maskTokenPattern = java.util.regex.Pattern.compile(alternatives.join('|'))
        }
    }

    static String mask(String text) {
        if (!maskEnabled || !text) {
            return text
        }
        String result = text
        // database URLs: keep the database type only
        result = result.replaceAll(/(jdbc:[A-Za-z0-9]+:)[^\s"]*/, '$1[masked]')
        // host names in URLs
        result = result.replaceAll(/(?i)\b((?:https?|ftps?|wss?):\/\/)([^\/\s:"'<>?#]+)/) { all, scheme, host ->
            scheme + pseudonym('host', host.toLowerCase())
        }
        // e-mail addresses and IP:port
        result = result.replaceAll(/[\w.+-]+@[\w-]+(?:\.[\w-]+)+/) { all -> pseudonym('email', all.toLowerCase()) }
        result = result.replaceAll(/\b(?:\d{1,3}\.){3}\d{1,3}(?=:\d+)/) { all -> pseudonym('ip', all) }
        // site names, site URLs, instances, virtual hosts, database names, paths
        if (maskTokenPattern != null) {
            def matcher = maskTokenPattern.matcher(result)
            def buffer = new StringBuffer()
            while (matcher.find()) {
                matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(maskTokens[matcher.group()] ?: matcher.group()))
            }
            matcher.appendTail(buffer)
            result = buffer.toString()
        }
        return result
    }

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
                    '"' + escapeCSV(mask(it?.toString() ?: '')) + '"'
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

// BEGIN included '_uas_privacy.groovy'
// Privacy option (UAS_MASK_PRIVATE_DATA at the top of the script). When it is true, names and addresses that
// identify the organization are replaced in the output with stable placeholders (site-1, url-1, instance-1,
// host-1, db-1, email-1, path-1, ...). Counts, versions and technical details are kept, so the report can
// still be assessed. Read-only; works on Liferay 6.1 and later.

def uasInitPrivacy(boolean enabled) {
    UASUtils.maskEnabled = enabled
    if (!enabled) {
        return
    }
    def load = { String className ->
        try {
            return Class.forName(className, true, this.getClass().getClassLoader())
        } catch (Throwable ignored) {
            return null
        }
    }
    def propsUtil = load('com.liferay.portal.kernel.util.PropsUtil')
    def restrictions = load('com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil')

    // Virtual instances
    try {
        def companyService = load('com.liferay.portal.kernel.service.CompanyLocalServiceUtil') ?: load('com.liferay.portal.service.CompanyLocalServiceUtil')
        companyService.getCompanies().each { company ->
            UASUtils.addMaskToken('instance', company.webId, company.companyId)
            try { UASUtils.addMaskToken('instance', company.name, company.companyId) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('host', company.virtualHostname) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('host', company.mx) } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {
    }

    // Sites: names (all languages) and friendly URLs
    try {
        def groupService = load('com.liferay.portal.kernel.service.GroupLocalServiceUtil') ?: load('com.liferay.portal.service.GroupLocalServiceUtil')
        def query = groupService.dynamicQuery()
        query.add(restrictions.eq('site', true))
        groupService.dynamicQuery(query).each { group ->
            try { group.getNameMap().values().each { UASUtils.addMaskToken('site', it, group.groupId) } } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('site', group.nameCurrentValue, group.groupId) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('site', group.getName(), group.groupId) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('site', group.getDescriptiveName(), group.groupId) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('url', group.friendlyURL, group.groupId) } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {
    }

    // Virtual hosts (7.x)
    try {
        load('com.liferay.portal.kernel.service.VirtualHostLocalServiceUtil')?.getVirtualHosts(-1, -1)?.each {
            UASUtils.addMaskToken('host', it.hostname)
        }
    } catch (Throwable ignored) {
    }

    // Database location and installation paths
    try {
        def jdbcUrl = propsUtil?.get('jdbc.default.url')
        def hostMatcher = (jdbcUrl ?: '') =~ /\/\/([^\/:;?]+)/
        if (hostMatcher.find()) {
            UASUtils.addMaskToken('host', hostMatcher.group(1))
        }
        def dbMatcher = (jdbcUrl ?: '') =~ /\/([A-Za-z0-9_\-]+)(?:\?|;|$)/
        if (dbMatcher.find()) {
            UASUtils.addMaskToken('db', dbMatcher.group(1))
        }
        UASUtils.addMaskToken('db', propsUtil?.get('jdbc.default.username'))
        UASUtils.addMaskToken('db', propsUtil?.get('jdbc.default.jndi.name'))
    } catch (Throwable ignored) {
    }
    try {
        [propsUtil?.get('liferay.home'), System.getProperty('catalina.home'), System.getProperty('catalina.base')].each {
            UASUtils.addMaskToken('path', it)
        }
    } catch (Throwable ignored) {
    }
    UASUtils.compileMaskTokens()
}

// END included '_uas_privacy.groovy'


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
                
// the function 'invoke__1_infra_groovy' will be invoked at the very end of the 'ce_6_2-all.groovy'
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
                
// the function 'invoke__2_data_groovy' will be invoked at the very end of the 'ce_6_2-all.groovy'
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
                
// the function 'invoke__3_database_info_groovy' will be invoked at the very end of the 'ce_6_2-all.groovy'
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
                
// the function 'invoke__4_custom_code_groovy' will be invoked at the very end of the 'ce_6_2-all.groovy'
// invoke__4_custom_code_groovy([ debug: false ])              
// END included '4_custom_code.groovy'

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

// BEGIN included 'ce_edition.groovy' (Liferay Portal Community Edition only)
// Only uses APIs available from Liferay Portal 6.1 to 7.4, and only reads data.

// Wrapper function for 'ce_edition.groovy'
def invoke__ce_edition_groovy(Map<String, Object> uasContext) {

    try {
        def ceTarget = uasContext['ceTargetVersion'] as String
        def ceScript = uasContext['ceScriptFileName'] as String
        def releaseInfo = com.liferay.portal.kernel.util.ReleaseInfo.getReleaseInfo()
        def version = com.liferay.portal.kernel.util.ReleaseInfo.getVersion()

        def isCE = (releaseInfo =~ /Community Edition|\bCE\b/).find()
        def gaMatcher = (releaseInfo =~ /\bGA(\d+)\b/)
        def ga = gaMatcher.find() ? gaMatcher.group(1) : ''
        def versionParts = version.tokenize('.')
        def runningMajorMinor = versionParts.size() >= 2 ? versionParts[0] + '.' + versionParts[1] : version
        def versionMatch = (runningMajorMinor == ceTarget)

        // Edition
        println UASUtils.toCSVLine('ce_edition_detected', isCE ? 'CE' : 'not CE',
                isCE ? "Liferay Portal Community Edition: ${releaseInfo}" :
                        "WARNING: this is not a Community Edition installation (${releaseInfo}); use the EE / DXP script for this version instead")

        // Is this the right CE script?
        def matchComment = (versionMatch && isCE) ?
                "Script '${ceScript}' targets Liferay Portal CE ${ceTarget}, running ${version}${ga ? ' GA' + ga : ''}" :
                "WARNING: script '${ceScript}' targets Liferay Portal CE ${ceTarget} but the running version is ${releaseInfo}; use ce_${runningMajorMinor.replace('.', '_')}/ce_${runningMajorMinor.replace('.', '_')}-all.groovy"
        println UASUtils.toCSVLine('ce_target_version', ceTarget, matchComment)
        println UASUtils.toCSVLine('ce_target_match', versionMatch && isCE, (versionMatch && isCE) ? 'OK' : matchComment)

        // GA level
        // Some releases (e.g. 6.1.2) do not print the GA in the release info: up to 7.3, CE x.y.z is GA(z + 1)
        def gaComment = "Liferay Portal CE ${version} GA${ga}"
        if (!ga && versionParts.size() >= 3 && versionParts[2].isInteger() && ceTarget != '7.4') {
            ga = (versionParts[2].toInteger() + 1).toString()
            gaComment = "Liferay Portal CE ${version} GA${ga} (derived from the version number)"
        }
        println UASUtils.toCSVLine('ce_ga_level', ga, ga ? gaComment : 'GA level not found in the release info')

        // Commercial (EE / DXP) equivalent of the running CE version
        def commercialEquivalent
        def commercialComment
        if (ceTarget.startsWith('6.')) {
            commercialEquivalent = "Liferay Portal EE ${ceTarget}"
            commercialComment = "CE ${ceTarget} GA${ga} corresponds to Liferay Portal EE ${ceTarget}"
        } else if (ceTarget == '7.4') {
            commercialEquivalent = ga ? "Liferay DXP 7.4 Update ${ga}" : 'Liferay DXP 7.4'
            commercialComment = "CE 7.4 GA${ga} is built from the same code base as Liferay DXP 7.4 Update ${ga}. DXP continues with Quarterly Releases (2023.Q3+)."
        } else {
            commercialEquivalent = "Liferay DXP ${ceTarget}"
            commercialComment = "CE ${ceTarget} GA${ga} corresponds to Liferay DXP ${ceTarget}"
        }
        println UASUtils.toCSVLine('ce_commercial_equivalent', commercialEquivalent, commercialComment)

        // Health check flags: things Liferay typically looks at when validating a CE to DXP move
        def flags = []

        def connection = null
        try {
            connection = com.liferay.portal.kernel.dao.jdbc.DataAccess.getConnection()
            def dbProduct = connection.getMetaData().getDatabaseProductName()
            if (dbProduct =~ /(?i)hsql/) {
                flags << "Database is Hypersonic (HSQL): not supported for production; migrate to a supported database before upgrading"
            }
        } catch (Throwable t) {
            flags << "Unable to read the database type: ${t}"
        } finally {
            try {
                com.liferay.portal.kernel.dao.jdbc.DataAccess.cleanUp(connection)
            } catch (Throwable ignored) {
            }
        }

        def javaSpec = System.getProperty('java.specification.version', '')
        def javaMajor = javaSpec.startsWith('1.') ? javaSpec.substring(2) : javaSpec
        if (javaMajor.isInteger() && javaMajor.toInteger() < 11) {
            flags << "Java ${System.getProperty('java.version')}: current Liferay DXP releases require Java 17 or 21"
        }

        def clusterEnabled = com.liferay.portal.kernel.util.PropsUtil.get('cluster.link.enabled')
        if (clusterEnabled != 'true') {
            flags << "Clustering is not enabled (cluster.link.enabled=${clusterEnabled}): single node installation"
        }

        if (ceTarget.startsWith('6.')) {
            flags << "Liferay Portal ${ceTarget}: plugins (portlets, hooks, themes, ext) need to be migrated to the OSGi / modular architecture of Liferay DXP 7.x+"
        }

        println UASUtils.toCSVLine('ce_health_flags_count', flags.size(),
                "Items for the Liferay health check (see also infra_database, infra_jvm and infra_search):\n${flags.collect { " - ${it}" }.join('\n')}")
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script 'ce_edition.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')

        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__ce_edition_groovy()
// END included 'ce_edition.groovy'

// invoke the topic scripts' wrapper functions                         
uasInitPrivacy(UAS_MASK_PRIVATE_DATA)

def uasContext = [ debug: false ]

invoke__1_infra_groovy(uasContext)
invoke__ce_edition_groovy(uasContext + [ceTargetVersion: '6.2', ceScriptFileName: 'ce_6_2-all.groovy'])
invoke__1_hosting_groovy(uasContext)
invoke__1_compatibility_groovy(uasContext)
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
    ${UASUtils.toCSVLine('uas_target_liferay_edition', 'CE')}
    ${UASUtils.toCSVLine('uas_private_data_masked', UAS_MASK_PRIVATE_DATA)}
    ${UASUtils.toCSVLine('uas_start_date', uasMetadata['startDateFormatted'])}  
    ${UASUtils.toCSVLine('uas_end_date', uasMetadata['endDateFormatted'])}  
    ${UASUtils.toCSVLine('uas_duration', uasMetadata['duration'])}  
    """.stripIndent()

// END common/_combined-footer.groovy
