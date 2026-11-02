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
                1.0.0
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

// invoke the topic scripts' wrapper functions                         
uasInitPrivacy(UAS_MASK_PRIVATE_DATA)

def uasContext = [ debug: false ]

invoke__1_infra_groovy(uasContext)
invoke__1_hosting_groovy(uasContext)
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
    ${UASUtils.toCSVLine('uas_private_data_masked', UAS_MASK_PRIVATE_DATA)}
    ${UASUtils.toCSVLine('uas_start_date', uasMetadata['startDateFormatted'])}  
    ${UASUtils.toCSVLine('uas_end_date', uasMetadata['endDateFormatted'])}  
    ${UASUtils.toCSVLine('uas_duration', uasMetadata['duration'])}  
    """.stripIndent()

// END common/_combined-footer.groovy
