// Liferay Portal Community Edition (CE) 7.2 - upgrade assessment script (read-only)
// Run on a UAT / test copy of Liferay Portal CE 7.2 (any GA), never on production, at your own risk.
// Control Panel > Server Administration > Script (language: Groovy). Save the output as a .txt file.
// Output is CSV: "Key", "Value", "Description" - same keys as dxp_7_2-all.groovy, plus ce_* keys.

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
                Liferay DXP 7.2
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


// BEGIN included '_uas_helpers.groovy'
// Robustness helpers shared by all topic scripts:
// - uasClass: loads classes of OSGi modules at runtime instead of importing them, so a module that is not active
//   (deactivated, blacklisted, or left unresolved by a failed upgrade, e.g. Commerce) cannot stop the whole script
//   from compiling. Returns null when the class is not available.
// - uasGuard: runs one block of the report; if it fails, the keys of that block that were not printed yet are
//   reported with an empty value and an 'n/a - <error>' description, and the script continues with the next block.
// - uasTimed: measures how long each topic script takes (reported as uas_section_durations).

@groovy.transform.Field Map<String, Object> _uasClassCache = [:]

def uasClass(String className) {
    if (_uasClassCache.containsKey(className)) {
        return _uasClassCache[className]
    }
    def clazz = null
    try {
        clazz = this.getClass().getClassLoader().loadClass(className)
    } catch (Throwable ignored) {
    }
    if (clazz == null) {
        try {
            def bundleContext = uasBundleContext()
            def parts = className.tokenize('.')
            def appPrefix = parts.size() > 3 ? parts[0..3].join('.') : className
            for (bundle in (bundleContext?.bundles ?: []).findAll {
                it.state == org.osgi.framework.Bundle.ACTIVE && it.symbolicName && it.symbolicName.startsWith(appPrefix)
            }) {
                try {
                    clazz = bundle.loadClass(className)
                    break
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }
    _uasClassCache[className] = clazz
    return clazz
}

def uasBundleContext() {
    try {
        return com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext()
    } catch (Throwable ignored) {
    }
    // 7.0: no SystemBundleUtil, use the bundle of a core module API class
    for (className in ['com.liferay.dynamic.data.mapping.service.DDMStructureLocalService', 'com.liferay.journal.service.JournalArticleLocalService']) {
        try {
            def bundle = org.osgi.framework.FrameworkUtil.getBundle(this.getClass().getClassLoader().loadClass(className))
            if (bundle?.getBundleContext() != null) {
                return bundle.getBundleContext()
            }
        } catch (Throwable ignored) {
        }
    }
    return null
}

def uasGuard(List<String> keys, Closure body) {
    def originalOut = binding.hasVariable('out') ? binding.getVariable('out') : null
    def buffer = new StringWriter()
    Throwable error = null
    try {
        if (originalOut != null) {
            binding.setVariable('out', new PrintWriter(buffer, true))
        }
        body()
    } catch (Throwable t) {
        error = t
    } finally {
        if (originalOut != null) {
            binding.setVariable('out', originalOut)
        }
    }
    def text = buffer.toString()
    if (originalOut != null) {
        originalOut.print(text)
    } else {
        print(text)
    }
    if (error != null) {
        def message = "n/a - ${error.class.name}: ${error.message}".toString()
        keys.findAll { key -> !text.contains('"' + key + '", ') }.each { key ->
            println UASUtils.toCSVLine(key, '', message)
        }
    }
}

def uasTimed(Map<String, Object> timings, String name, Closure body) {
    long start = System.currentTimeMillis()
    try {
        body()
    } catch (Throwable t) {
        println "UAS ERROR: '${name}' failed: ${t}"
    } finally {
        timings[name] = System.currentTimeMillis() - start
    }
}

// END included '_uas_helpers.groovy'

// BEGIN included '1_infra.groovy'  

// Imports for '1_infra.groovy' 
import com.liferay.portal.kernel.dao.jdbc.DataAccess
import com.liferay.portal.kernel.patcher.PatcherUtil
import com.liferay.portal.kernel.search.SearchEngine
import com.liferay.portal.kernel.search.SearchEngineHelperUtil
import com.liferay.portal.kernel.util.ReleaseInfo
import com.liferay.portal.kernel.util.ServerDetector
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
def searchEngineInformation = null
ServiceTracker st = null
try {
    def groovyExecutorClass = uasClass('com.liferay.portal.scripting.groovy.internal.GroovyExecutor')
    def bundle = [bundleContext: (groovyExecutorClass ? FrameworkUtil.getBundle(groovyExecutorClass)?.bundleContext : null) ?: uasBundleContext()]
    st = new ServiceTracker(bundle.bundleContext, 'com.liferay.portal.search.engine.SearchEngineInformation', null)
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

def searchEngine = "${searchEngineInformation?.vendorString} - ${searchEngineInformation?.clientVersionString}"
def searchEngineDetails = searchEngineInformation?.properties?.collect { "- ${it.key}: ${it.value}"}.join('\n')

println("${UASUtils.toCSVLine('infra_search', searchEngine, searchEngineDetails)}")   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '1_infra.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__1_infra_groovy()
                
// the function 'invoke__1_infra_groovy' will be invoked at the very end of the 'ce_7_2-all.groovy'
// invoke__1_infra_groovy([ debug: false ])              
// END included '1_infra.groovy'

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
        def variant = '7.2'

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

// the function 'invoke__2_data_groovy' will be invoked at the very end of the 'ce_7_2-all.groovy'
// invoke__2_data_groovy([ debug: false ])
// END included '2_data.groovy'

// BEGIN included '3_database_info.groovy'

// Imports for '3_database_info.groovy'
import com.liferay.portal.kernel.dao.db.DB
import com.liferay.portal.kernel.dao.db.DBManagerUtil
import com.liferay.portal.kernel.dao.db.DBType
import com.liferay.portal.kernel.dao.jdbc.DataAccess
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet

// Wrapper function for '3_database_info.groovy'
def invoke__3_database_info_groovy(Map<String, Object> uasContext) {

    try {
        // Rows of every table, read-only. Each count has its own statement (always closed) and a timeout, so one
        // very large or locked table cannot block the report; tables that cannot be counted are listed separately.
        int queryTimeoutSeconds = (uasContext['tableCountTimeoutSeconds'] ?: 120) as int

        Connection connection = null
        ResultSet rs = null

        try {
            DB db = DBManagerUtil.getDB()
            DBType dbType = db.getDBType()

            connection = DataAccess.getConnection()

            DatabaseMetaData metadata = connection.getMetaData()

            String catalog = connection.getCatalog()
            String schema = null

            if ((catalog == null) && (dbType.equals(DBType.ORACLE))) {
                catalog = metadata.getUserName()
                schema = catalog
            }

            rs = metadata.getTables(catalog, schema, "%", null)

            def tableNames = []
            while (rs.next()) {
                String tableType = rs.getString("TABLE_TYPE")
                if ("TABLE".equals(tableType) || "BASE TABLE".equals(tableType)) {
                    tableNames << rs.getString("TABLE_NAME")
                }
            }

            def tables = []
            def unreadable = []

            tableNames.each { String tableName ->
                def statement = null
                ResultSet countResult = null
                try {
                    statement = connection.createStatement()
                    try {
                        statement.setQueryTimeout(queryTimeoutSeconds)
                    } catch (Throwable ignored) {
                    }
                    countResult = statement.executeQuery("select count(*) from " + tableName)
                    if (countResult.next()) {
                        tables << [name: tableName, rows: countResult.getLong(1)]
                    }
                } catch (Throwable t) {
                    unreadable << "${tableName}: ${t.class.simpleName}${t.message ? ' - ' + t.message.readLines().find() : ''}"
                } finally {
                    DataAccess.cleanUp(statement, countResult)
                }
            }

            // Same order as before: most rows first, then by name
            tables.sort { a, b -> a.rows == b.rows ? a.name <=> b.name : b.rows <=> a.rows }

            def tableDetails = new StringBuilder("tableName, rowsCount\n")
            tables.each { tableDetails.append("${it.name}, ${it.rows}\n") }

            println UASUtils.toCSVLine('data_tables_count', "${tables.size()}", "")
            println UASUtils.toCSVLine('data_tables_info', "", "${tableDetails.toString()}")
            println UASUtils.toCSVLine('data_tables_unreadable_count', unreadable.size(),
                    "Tables that could not be counted (query timeout ${queryTimeoutSeconds}s or error):\n${unreadable.collect { " - ${it}" }.join('\n')}")
        }
        finally {
            DataAccess.cleanUp(connection, null, rs)
        }

        println ""
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '3_database_info.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')

        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__3_database_info_groovy()

// the function 'invoke__3_database_info_groovy' will be invoked at the very end of the 'ce_7_2-all.groovy'
// invoke__3_database_info_groovy([ debug: false ])
// END included '3_database_info.groovy'

// BEGIN included '4_custom_code.groovy'  

// Imports for '4_custom_code.groovy' 
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
def customHookPlugins = []

uasGuard(['custom_code_hook_plugins_count']) {

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
customHookPlugins = hookPlugins.findAll { !(it.moduleId in liferayHookPlugins*.moduleId) }

uasUtils.debugCSVLine 'hookPlugins', hookPlugins
uasUtils.debugCSVLine 'liferayHookPlugins', liferayHookPlugins

println UASUtils.toCSVLine(
        'custom_code_hook_plugins_count', customHookPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages, excluding ${liferayHookPlugins.size} authored by 'liferay' (${liferayHookPlugins*.moduleId}):\n${customHookPlugins.collect { " - ${it.moduleId}" }.join('\n')}")


// Portlets

}

uasGuard(['custom_code_portlet_plugins_count', 'custom_code_portlets_count']) {
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

}

uasGuard(['custom_code_layout_template_plugins_count', 'custom_code_layout_templates_count', 'custom_code_layout_templates_by_lang_vm_count', 'custom_code_layout_templates_by_lang_ftl_count']) {
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

}

uasGuard(['custom_code_osgi_modules_count']) {
def customOSGiModulesDir = new File(_PROPS_VALUES_MODULE_FRAMEWORK_MODULES_DIR)
def customOSGiModules = (customOSGiModulesDir.listFiles() ?: []).findAll { it.name.endsWith('.jar')}

uasUtils.debugCSVLine 'customOSGiModulesDir', customOSGiModulesDir.absolutePath

println UASUtils.toCSVLine(
        'custom_code_osgi_modules_count', customOSGiModules.size(),
        "Counted by listing the '${customOSGiModulesDir}' directory (*.jar):\n${customOSGiModules.collect { " - ${it.name}" }.join('\n')}"
)


// Themes

}

uasGuard(['custom_code_theme_plugins_count', 'custom_code_themes_count', 'custom_code_themes_by_lang_vm_count', 'custom_code_themes_by_lang_ftl_count', 'custom_code_themes_by_lang_jsp_count']) {
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

}

uasGuard(['custom_code_web_plugins_count']) {
def webPlugins = allUniqueInstalledPlugins.findAll {
    'web' in it.types || it.name.endsWith('-web')
}

println UASUtils.toCSVLine(
        'custom_code_web_plugins_count', webPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages:\n${webPlugins.collect { " - ${it.moduleId}" }.join('\n')}")


//
// Features of Liferay Hook extension point
//
}

uasGuard(['custom_code_hook_extensions_custom_jsp', 'custom_code_hook_extensions_servlet_filters', 'custom_code_hook_extensions_struts_actions', 'custom_code_hook_extensions_indexer_post_processors', 'custom_code_hook_extensions_service_wrappers', 'custom_code_hook_extensions_override_portal_properties', 'custom_code_hook_extensions_application_adapters', 'custom_code_hook_extensions_override_language_properties']) {
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

}

uasGuard(['custom_code_service_builder_modules']) {
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

}

uasGuard(['custom_code_fragment_hosts_count', 'custom_code_fragment_hosts_info']) {
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

BundleContext bundleContext = uasBundleContext()

def fragmentHostInfo = ""
def fragmentHostCount = 0

for (Bundle bundle : bundleContext.getBundles()) {

    headerContent = bundle.getHeaders().get("Fragment-Host")

    // Skip fragments shipped by Liferay itself, only report custom ones
    def isLiferayShippedFragment = bundle.getSymbolicName()?.startsWith('com.liferay.') && !(bundle.getLocation() ?: '').contains('/osgi/modules/')

    if (headerContent != null && !headerContent.isEmpty() && !isLiferayShippedFragment) {

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
}

   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '4_custom_code.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__4_custom_code_groovy()
                
// the function 'invoke__4_custom_code_groovy' will be invoked at the very end of the 'ce_7_2-all.groovy'
// invoke__4_custom_code_groovy([ debug: false ])              
// END included '4_custom_code.groovy'

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

            // Widgets placed on pages (ownerType 3 = PortletKeys.PREFS_OWNER_TYPE_LAYOUT): one read-only SQL scan returning only
            // the rows of the deprecated widgets, processed row by row with a per-widget cache (no entities in memory, no
            // regular expressions per row), so that millions of portlet preferences rows stay fast and use little memory
            // Lowercase parts that appear literally in the widget IDs (module names), so that the database can use a plain,
            // case-sensitive LIKE (lower() on every row and pattern is several times slower); the exact, case-insensitive
            // check per widget is done once per distinct widget ID below
            def widgetPatterns = ['message_boards_web_portlet', 'wiki_web_portlet', 'microblogs_web', 'questions_web', 'activities_web',
                                  'contacts_web', 'group_statistics_web', 'invite_members_web', 'document_library_web_portlet',
                                  'subscription', 'sharing_web', 'sites_directory', 'site_navigation_directory', 'xsl_content_web',
                                  'most_viewed', 'walkthrough', 'social_networking', 'journal_content_search_web',
                                  'dynamic_data_lists_web', 'kaleo_forms_web', 'bookmarks_web', 'flash_web', 'portal_search_web_portlet',
                                  'polls_web', 'asset_publisher_web', 'IGDisplay', 'SitesDirectory', 'MostViewed', 'SharedAssets',
                                  'Subscription', 'Walkthrough', 'PollsDisplay']
            def sql = 'select portletId, plid from PortletPreferences where ownerType = 3 and plid > 0 and (' +
                    widgetPatterns.collect { 'portletId like ?' }.join(' or ') + ')'
            Map<String, Boolean> rootMatches = new HashMap<String, Boolean>()
            def connection = null
            def statement = null
            def resultSet = null
            try {
                connection = com.liferay.portal.kernel.dao.jdbc.DataAccess.getConnection()
                statement = connection.prepareStatement(sql, java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY)
                widgetPatterns.eachWithIndex { pattern, index -> statement.setString(index + 1, '%' + pattern + '%') }
                statement.setFetchSize(5000)
                resultSet = statement.executeQuery()
                while (resultSet.next()) {
                    String portletId = resultSet.getString(1)
                    if (portletId == null) {
                        continue
                    }
                    int instance = portletId.indexOf('_INSTANCE_')
                    String root = (instance > 0 ? portletId.substring(0, instance) : portletId).toLowerCase()
                    Boolean matches = rootMatches.get(root)
                    if (matches == null) {
                        matches = matchesCheck(root)
                        rootMatches.put(root, matches)
                    }
                    if (matches) {
                        def plids = portletIdToPlids.get(root)
                        if (plids == null) {
                            plids = new HashSet<Long>()
                            portletIdToPlids.put(root, plids)
                        }
                        plids.add(resultSet.getLong(2))
                    }
                }
            } finally {
                com.liferay.portal.kernel.dao.jdbc.DataAccess.cleanUp(connection, statement, resultSet)
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

def uasSectionTimings = [:]

uasTimed(uasSectionTimings, '1_infra') { invoke__1_infra_groovy(uasContext) }
uasTimed(uasSectionTimings, 'ce_edition') { invoke__ce_edition_groovy(uasContext + [ceTargetVersion: '7.2', ceScriptFileName: 'ce_7_2-all.groovy']) }
uasTimed(uasSectionTimings, '1_hosting') { invoke__1_hosting_groovy(uasContext) }
uasTimed(uasSectionTimings, '2_data') { invoke__2_data_groovy(uasContext) }
uasTimed(uasSectionTimings, '3_database_info') { invoke__3_database_info_groovy(uasContext) }
uasTimed(uasSectionTimings, '4_custom_code') { invoke__4_custom_code_groovy(uasContext) }
uasTimed(uasSectionTimings, '6_deprecations') { invoke__6_deprecations_groovy(uasContext) }
uasTimed(uasSectionTimings, '7_additional_info') { invoke__7_additional_info_groovy(uasContext) }
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
    ${UASUtils.toCSVLine('uas_section_durations', uasSectionTimings.values().sum(0), uasSectionTimings.collect { "${it.key}: ${it.value} ms" }.join(', '))}
    """.stripIndent()

// END common/_combined-footer.groovy
