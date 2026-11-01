// Liferay DXP 2025.Q1 LTS (any 2025.Q1.x patch release) - upgrade assessment script
// Run via Control Panel > Server Administration > Script (language: Groovy).
// Output is CSV: "Key", "Value", "Description" (same format as the other version scripts).

// BEGIN common/_combined-header.groovy

def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"

// the @...@ tokens can in theory contain ' or ", so use a safe way to write these as Groovy String literals
def uasMetadata = [
        projectVersion: """\
                0.11.2
            """.trim(),
        sourceSetLabel: """\
                Liferay DXP 2025.Q1
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
import com.liferay.portal.kernel.search.SearchEngineHelperUtil
import com.liferay.portal.kernel.util.ReleaseInfo
import com.liferay.portal.kernel.util.ServerDetector
import com.liferay.portal.search.engine.SearchEngineInformation
import com.liferay.portal.kernel.util.PropsUtil
import org.osgi.framework.FrameworkUtil
import org.osgi.util.tracker.ServiceTracker
              
// Wrapper function for '1_infra.groovy'
def invoke__1_infra_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.jdbc.DataAccess
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.search.SearchEngine
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.search.SearchEngineHelperUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.ReleaseInfo
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.ServerDetector
//__GRADLE_COMMENT__ import com.liferay.portal.search.engine.SearchEngineInformation
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsUtil
//__GRADLE_COMMENT__ import org.osgi.framework.FrameworkUtil
//__GRADLE_COMMENT__ import org.osgi.util.tracker.ServiceTracker

println("${UASUtils.toCSVLine('Key', 'Value', 'Description')}")

// Version
println("${UASUtils.toCSVLine('infra_liferay_version', ReleaseInfo.getVersion(), ReleaseInfo.getReleaseInfo())}")

// To determine the sub version (Update level / Quarterly Release) on 7.4
// 7.4 GA/Updates display as e.g. "7.4.13 Update 92" (or "U92"/"GA1"), Quarterly Releases as e.g. "2024.Q2.8" (sometimes with "LTS")
// ReleaseInfo::getVersionDisplayName does not exist in the first 7.4 GA / Update builds (e.g. CE 7.4 GA4)
def _versionDisplayName
try {
    _versionDisplayName = ReleaseInfo.getVersionDisplayName()
} catch (MissingMethodException ignored) {
    _versionDisplayName = ReleaseInfo.getReleaseInfo()
}
def inputString = "${_versionDisplayName} ${ReleaseInfo.getReleaseInfo()}"
def updateValue = null
def quarterlyRelease = false
def quarterlyMatcher = inputString =~ /(\d{4})\.Q([1-4])(?:\.(\d+))?/
def updateMatcher = inputString =~ /Update (\d+)/
def shortUpdateMatcher = inputString =~ /\bU(\d+)\b/
def gaMatcher = inputString =~ /\bGA(\d+)\b/
// Liferay Portal Community Edition: "7.4.3.132 CE GA132". CE 7.4 GA n is built from the same code as DXP 7.4 Update n.
def communityEdition = (inputString =~ /Community Edition|\bCE\b/).find()

if (quarterlyMatcher.find()) {
    updateValue = Integer.MAX_VALUE
    quarterlyRelease = true
    println("Liferay 7.4 Quarterly version: " + _versionDisplayName)
} else if (updateMatcher.find()) {
    updateValue = updateMatcher.group(1).toInteger()
    println("Liferay 7.4 Update value is: " + updateValue)
} else if (shortUpdateMatcher.find()) {
    updateValue = shortUpdateMatcher.group(1).toInteger()
    println("Liferay 7.4 Update value is: " + updateValue)
} else if (gaMatcher.find()) {
    // DXP 7.4 GA1 == update level 0; CE GA n == update level n
    updateValue = communityEdition ? gaMatcher.group(1).toInteger() : 0
    println("Liferay 7.4 GA release: GA" + gaMatcher.group(1) + (communityEdition ? ' (Community Edition)' : ''))
} else {
    println("Update value not found")
}

// Store the detected release on the shared context, so the other topic scripts can use it
uasContext['liferayUpdateValue'] = updateValue
uasContext['liferayQuarterlyRelease'] = quarterlyRelease

// The internal scripting executor class moved between bundles over the 7.4 Updates / Quarterly Releases.
// It is only used to get hold of an OSGi BundleContext, so look it up dynamically (a hard-coded class reference
// throws at runtime if the class does not exist in the running version, aborting the whole infra section).
def executorCandidates = [
        'com.liferay.portal.scripting.groovy.internal.GroovyExecutor'                : 'com.liferay.portal.scripting.groovy',
        'com.liferay.portal.scripting.groovy.internal.GroovyScriptingExecutor'       : 'com.liferay.portal.scripting.groovy',
        'com.liferay.server.admin.web.internal.scripting.ServerScripting'            : 'com.liferay.server.admin.web',
        'com.liferay.server.admin.web.internal.scripting.util.ServerScriptingUtil'   : 'com.liferay.server.admin.web',
]
def preferredExecutor
if (updateValue != null && updateValue <= 37) {
    preferredExecutor = 'com.liferay.portal.scripting.groovy.internal.GroovyExecutor'
} else if (updateValue != null && updateValue > 37 && updateValue < 46) {
    preferredExecutor = 'com.liferay.portal.scripting.groovy.internal.GroovyScriptingExecutor'
} else if (updateValue != null && updateValue >= 46 && updateValue < 102) {
    preferredExecutor = 'com.liferay.server.admin.web.internal.scripting.ServerScripting'
} else {
    preferredExecutor = 'com.liferay.server.admin.web.internal.scripting.util.ServerScriptingUtil'
}

def executorClass = null
def systemBundleContext = null
try {
    systemBundleContext = com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext()
} catch (Throwable ignored) {
}

([preferredExecutor] + (executorCandidates.keySet() as List).reverse()).unique().find { className ->
    if (systemBundleContext == null) {
        return false
    }
    def candidateBundle = systemBundleContext.bundles.find { it.symbolicName == executorCandidates[className] }
    if (candidateBundle == null) {
        return false
    }
    try {
        executorClass = candidateBundle.loadClass(className)
    } catch (Throwable ignored) {
        executorClass = null
    }
    return executorClass != null
}

// Use the selected executor class
println("Using executor class: " + (executorClass?.getName() ?: 'n/a (falling back to the search API bundle)'))

// Patches
// 7.4 GA/Updates (and the first Quarterly Releases) expose PatcherUtil; later Quarterly Releases expose PatcherValues.
// Try both, so that an API removed in a given release does not abort the rest of the infra section.
def patchesPrinted = false

if (quarterlyRelease) {
    try {
        def patches = com.liferay.portal.kernel.patcher.PatcherValues.INSTALLED_PATCH_NAMES.size()

        def patchesDetails = """\
            Via PatcherValues:
            - installed patches: ${com.liferay.portal.kernel.patcher.PatcherValues.INSTALLED_PATCH_NAMES.join(', ')}"""
        println("${UASUtils.toCSVLine('infra_liferay_patches', patches, patchesDetails)}")
        patchesPrinted = true
    } catch (Throwable t) {
        uasContext['patcherValuesError'] = t.toString()
    }
}

if (!patchesPrinted && communityEdition) {
    // Community Edition has no patching tool / fix packs / hotfixes
    println("${UASUtils.toCSVLine('infra_liferay_patches', 0, 'n/a - Liferay Portal Community Edition (no patching tool, fix packs or hotfixes)')}")
    patchesPrinted = true
}

if (!patchesPrinted) {
    try {
        def patches = com.liferay.portal.kernel.patcher.PatcherUtil.getInstalledPatches().size()
        def fixedIssues = com.liferay.portal.kernel.patcher.PatcherUtil.getFixedIssues()
        def fixedIssuesDisplayedMax = 10
        def fixedIssuesDisplayed = fixedIssues.take(fixedIssuesDisplayedMax)

        def patchesDetails = """\
    Via PatcherUtil:
    - installed patches: ${com.liferay.portal.kernel.patcher.PatcherUtil.getInstalledPatches().join(', ')}
    - fixed issues: ${fixedIssuesDisplayed.join(', ')}\
    ${fixedIssues.size() > fixedIssuesDisplayedMax ? "... (${fixedIssues.size()} total)" : ''}
    - patch levels: ${com.liferay.portal.kernel.patcher.PatcherUtil.getPatchLevels().join(', ')}
    - separation ID: ${com.liferay.portal.kernel.patcher.PatcherUtil.getSeparationId()}
    - patching tool: ${com.liferay.portal.kernel.patcher.PatcherUtil.getPatchingToolVersionDisplayName()}"""

        println("${UASUtils.toCSVLine('infra_liferay_patches', patches, patchesDetails)}")
        patchesPrinted = true
    } catch (Throwable t) {
        println("${UASUtils.toCSVLine('infra_liferay_patches', '', "n/a - neither PatcherValues nor PatcherUtil available in this release (${uasContext['patcherValuesError'] ?: t})")}")
    }
}


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
    def bundle = FrameworkUtil.getBundle(executorClass ?: SearchEngineInformation.class)
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

def searchEngine = "${searchEngineInformation?.vendorString} - ${searchEngineInformation?.clientVersionString}"
def searchEngineDetails = searchEngineInformation?.properties?.collect { "- ${it.key}: ${it.value}"}.join('\n')

println("${UASUtils.toCSVLine('infra_search', searchEngine, searchEngineDetails)}")


// Release details (7.4 GA / Updates and Quarterly Releases: 2023.Q3 onwards)
// Liferay LTS Quarterly Releases (per Liferay's release schedule); a release whose display name contains 'LTS' is also flagged
def LTS_QUARTERLY_RELEASES = ['2023.Q4', '2024.Q2', '2025.Q1', '2026.Q1']

def releaseType = 'unknown'
def quarterlyLabel = ''
def quarterlyYear = ''
def quarterlyQuarter = ''
def quarterlyPatch = ''
def quarterlyLts = false

if (quarterlyRelease) {
    releaseType = 'quarterly'
    quarterlyYear = quarterlyMatcher.group(1)
    quarterlyQuarter = "Q${quarterlyMatcher.group(2)}".toString()
    quarterlyPatch = quarterlyMatcher.group(3) ?: '0'
    quarterlyLabel = "${quarterlyYear}.${quarterlyQuarter}.${quarterlyPatch}".toString()
    quarterlyLts = ("${quarterlyYear}.${quarterlyQuarter}".toString() in LTS_QUARTERLY_RELEASES) || (inputString =~ /\bLTS\b/).find()
} else if (communityEdition) {
    releaseType = 'ce'
} else if (updateValue == 0) {
    releaseType = 'ga'
} else if (updateValue != null) {
    releaseType = 'update'
}

// ReleaseInfo::getBuildDate returns a java.util.Date on Quarterly Releases (a String on older versions)
def buildDate = ReleaseInfo.getBuildDate()
def buildAgeDays = ''
try {
    def parsedBuildDate = (buildDate instanceof Date) ? buildDate : new java.text.SimpleDateFormat('MMMM d, yyyy', Locale.US).parse(buildDate as String)
    buildDate = parsedBuildDate.format('yyyy-MM-dd')
    buildAgeDays = ((new Date().time - parsedBuildDate.time) / (1000L * 60 * 60 * 24)) as long
} catch (Throwable ignored) {
}

def jvmMajorVersion = System.getProperty('java.specification.version', '').replaceFirst(/^1\./, '')

def releaseDetails = """\
Via ReleaseInfo:
- version: ${ReleaseInfo.getVersion()}
- version display name: ${_versionDisplayName}
- release info: ${ReleaseInfo.getReleaseInfo()}
- build number: ${ReleaseInfo.getBuildNumber()}
- build date: ${buildDate}${buildAgeDays != '' ? " (${buildAgeDays} days ago)" : ''}
- edition: ${communityEdition ? 'Community Edition (CE)' : 'Digital Experience Platform (DXP)'}
- release type: ${releaseType}
- update level: ${updateValue != null && updateValue != Integer.MAX_VALUE ? updateValue : 'n/a'}
- quarterly release: ${quarterlyRelease ? quarterlyLabel : 'n/a'}
- quarterly year: ${quarterlyYear ?: 'n/a'}
- quarterly quarter: ${quarterlyQuarter ?: 'n/a'}
- quarterly patch: ${quarterlyPatch ?: 'n/a'}
- LTS: ${quarterlyLts}
- JVM major version: ${jvmMajorVersion}"""

println("${UASUtils.toCSVLine('infra_liferay_release_type', releaseType, releaseDetails)}")
println("${UASUtils.toCSVLine('infra_liferay_edition', communityEdition ? 'CE' : 'DXP', communityEdition ? 'Liferay Portal Community Edition: DXP-only features (e.g. Search Experiences / Blueprints) are reported as n/a' : 'Liferay Digital Experience Platform')}")

// Is this script the right one for the running release?
def targetRelease = uasContext['targetRelease'] as String
def runningRelease = quarterlyRelease ? "${quarterlyYear}.${quarterlyQuarter}".toString() : '7.4'
def targetMatch = (runningRelease == targetRelease)
def targetMatchComment = targetMatch ?
        "Script '${uasContext['targetScriptFileName']}' targets ${targetRelease}${uasContext['targetReleaseLts'] ? ' (LTS)' : ''}, running ${_versionDisplayName}" :
        "WARNING: script '${uasContext['targetScriptFileName']}' targets ${targetRelease} but the running release is ${quarterlyRelease ? quarterlyLabel : _versionDisplayName}; use the script matching the running release (dxp_${quarterlyRelease ? runningRelease.toLowerCase().replace('.', '_') : '7_4'})"
println("${UASUtils.toCSVLine('infra_liferay_script_target_release', targetRelease, targetMatchComment)}")
println("${UASUtils.toCSVLine('infra_liferay_script_target_match', targetMatch, targetMatch ? 'OK' : targetMatchComment)}")
println("${UASUtils.toCSVLine('infra_liferay_update_level', (updateValue != null && updateValue != Integer.MAX_VALUE) ? updateValue : '', quarterlyRelease ? 'n/a for Quarterly Releases (they follow 7.4 Update 92)' : (communityEdition ? "CE GA${updateValue} (same code base as DXP 7.4 Update ${updateValue})" : 'Via ReleaseInfo::getVersionDisplayName'))}")
println("${UASUtils.toCSVLine('infra_liferay_quarterly_release', quarterlyLabel, quarterlyRelease ? "Year: ${quarterlyYear}, Quarter: ${quarterlyQuarter}, Patch: ${quarterlyPatch}" : 'Not a Quarterly Release')}")
println("${UASUtils.toCSVLine('infra_liferay_quarterly_release_lts', quarterlyLts, "LTS Quarterly Releases: ${LTS_QUARTERLY_RELEASES.join(', ')}")}")
println("${UASUtils.toCSVLine('infra_liferay_build_age_days', buildAgeDays, "Days since build date '${buildDate}'")}")

// Portal schema version & module upgrade state (Release table)
try {
    def portalRelease = com.liferay.portal.kernel.service.ReleaseLocalServiceUtil.fetchRelease('portal')
    println("${UASUtils.toCSVLine('infra_portal_schema_version', portalRelease?.schemaVersion ?: '', "Via ReleaseLocalServiceUtil::fetchRelease('portal'), buildNumber: ${portalRelease?.buildNumber}, verified: ${portalRelease?.verified}")}")

    def allReleases = com.liferay.portal.kernel.service.ReleaseLocalServiceUtil.getReleases(-1, -1)
    // Release state 0 == ReleaseConstants.STATE_GOOD; anything else is a failed/pending upgrade or verify
    def failedReleases = allReleases.findAll { it.state != 0 }
    println("${UASUtils.toCSVLine('infra_upgrade_failed_modules_count', failedReleases.size(), "Release entries with state != STATE_GOOD (out of ${allReleases.size()}):\n${failedReleases.collect { " - ${it.servletContextName} { schemaVersion=${it.schemaVersion}, state=${it.state} }" }.join('\n')}")}")
} catch (Throwable t) {
    println("${UASUtils.toCSVLine('infra_portal_schema_version', '', "n/a - ${t}")}")
}

// Upgrade-relevant portal properties for 7.4 / Quarterly Releases
[
        infra_database_partition_enabled: 'database.partition.enabled',
        infra_cluster_link_enabled      : 'cluster.link.enabled',
        infra_upgrade_database_auto_run : 'upgrade.database.auto.run',
        infra_company_default_web_id    : 'company.default.web.id',
].each { csvKey, propertyKey ->
    println("${UASUtils.toCSVLine(csvKey, PropsUtil.get(propertyKey) ?: '', "Via PropsUtil.get('${propertyKey}')")}")
}

// Feature flags (feature.flag.*) are widely used on Quarterly Releases to enable beta / release features
try {
    def featureFlags = PropsUtil.getProperties('feature.flag.', false)
    def enabledFeatureFlags = featureFlags.findAll { (it.value as String).trim().equalsIgnoreCase('true') }.collect { it.key }.sort()
    println("${UASUtils.toCSVLine('infra_feature_flags_enabled_count', enabledFeatureFlags.size(), "Via PropsUtil.getProperties('feature.flag.'), enabled (=true) of ${featureFlags.size()} defined:\n${enabledFeatureFlags.collect { " - ${it}" }.join('\n')}")}")
} catch (Throwable t) {
    println("${UASUtils.toCSVLine('infra_feature_flags_enabled_count', '', "n/a - ${t}")}")
}
   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '1_infra.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__1_infra_groovy()
                
// the function 'invoke__1_infra_groovy' will be invoked at the very end of the 'dxp_2025_q1-all.groovy'
// invoke__1_infra_groovy([ debug: false ])              
// END included '1_infra.groovy'

// BEGIN included '2_data.groovy'  

// Imports for '2_data.groovy' 
import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil
import com.liferay.dynamic.data.mapping.model.DDMStructure
import com.liferay.dynamic.data.mapping.model.DDMTemplate
import com.liferay.dynamic.data.mapping.service.DDMFieldLocalServiceUtil
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalServiceUtil
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalServiceUtil
import com.liferay.expando.kernel.model.ExpandoBridge
import com.liferay.expando.kernel.model.ExpandoTable
import com.liferay.expando.kernel.service.ExpandoTableLocalServiceUtil
import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil
import com.liferay.exportimport.kernel.staging.StagingUtil
import com.liferay.fragment.service.FragmentEntryLocalServiceUtil
import com.liferay.journal.service.JournalArticleLocalServiceUtil
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalServiceUtil
import com.liferay.portal.kernel.dao.orm.QueryUtil
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal
import com.liferay.portal.kernel.service.*
import com.liferay.portal.kernel.util.PrefsPropsUtil
import com.liferay.portal.kernel.util.PropsKeys
import com.liferay.portal.kernel.util.Validator
import com.liferay.portal.kernel.template.TemplateConstants
import com.liferay.portal.kernel.util.PropsUtil
              
// Wrapper function for '2_data.groovy'
def invoke__2_data_groovy(Map<String, Object> uasContext) {

    try {
//__GRADLE_COMMENT__ import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.model.DDMStructure
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.model.DDMTemplate
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.service.DDMFieldLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.service.DDMStructureLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.expando.kernel.model.ExpandoBridge
//__GRADLE_COMMENT__ import com.liferay.expando.kernel.model.ExpandoTable
//__GRADLE_COMMENT__ import com.liferay.expando.kernel.service.ExpandoTableLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil
//__GRADLE_COMMENT__ import com.liferay.exportimport.kernel.staging.StagingUtil
//__GRADLE_COMMENT__ import com.liferay.fragment.service.FragmentEntryLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.journal.service.JournalArticleLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.dao.orm.QueryUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.security.auth.CompanyThreadLocal
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.*
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PrefsPropsUtil
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsKeys
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.Validator
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.template.TemplateConstants
//__GRADLE_COMMENT__ import com.liferay.portal.kernel.util.PropsUtil

def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"

// Groups and Sites
def groups = GroupLocalServiceUtil.getGroups(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def sites = groups.findAll { it.site }

// DDM
def wcTemplateClassnameId =
        ClassNameLocalServiceUtil.getClassNameId(DDMStructure.class)
def ddmTemplates = DDMTemplateLocalServiceUtil.getDDMTemplates(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
def companyIds = CompanyLocalServiceUtil.getCompanies()*.companyId
// 'Default user' was renamed to 'guest user' during 7.4 / Quarterly Releases (getDefaultUserId removed in later releases)
def defaultUserIds = companyIds.collect { companyId ->
    try {
        UserLocalServiceUtil.getGuestUserId(companyId)
    } catch (MissingMethodException ignored) {
        UserLocalServiceUtil.getDefaultUserId(companyId)
    }
}
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
def dDMFieldsCount = DDMFieldLocalServiceUtil.getDDMFieldsCount()

def companiesCount = CompanyLocalServiceUtil.getCompaniesCount()
def usersCount = UserLocalServiceUtil.getUsersCount()
def layoutsCount = LayoutLocalServiceUtil.getLayoutsCount()
def journalArticlesCount = JournalArticleLocalServiceUtil.getJournalArticlesCount()
def dLFileEntriesCount = DLFileEntryLocalServiceUtil.getDLFileEntriesCount()
def portletPreferences
try {
    portletPreferences = PortletPreferencesLocalServiceUtil.getPortletPreferences()
} catch (MissingMethodException ignored) {
    portletPreferences = PortletPreferencesLocalServiceUtil.getPortletPreferences(QueryUtil.ALL_POS, QueryUtil.ALL_POS)
}
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

// There may be many more, but ROM calculator only asks about VM and FTL, so only make sure these are put as CSV lines
def ddmTemplatesSupportedLangs = [TemplateConstants.LANG_TYPE_VM, TemplateConstants.LANG_TYPE_FTL]
def webContentTemplatesByLang =
        webContentTemplates.groupBy { DDMTemplate it -> it.language }

ddmTemplatesSupportedLangs.each {
    webContentTemplatesByLang.putIfAbsent(it, Collections.emptyList())
}

def adtTemplatesByLang =
        applicationDisplayTemplates.groupBy { DDMTemplate it -> it.language }

ddmTemplatesSupportedLangs.each {
    adtTemplatesByLang.putIfAbsent(it, Collections.emptyList())
}

// Questions keys

println UASUtils.toCSVLine("data_companies_count", "${companiesCount}", "")
println UASUtils.toCSVLine("data_custom_application_display_templates_adt_count", "${applicationDisplayTemplatesCount}", "")
println UASUtils.toCSVLine("data_custom_web_content_templates_wc_count", "${webContentTemplatesCount}", "")
println UASUtils.toCSVLine("data_ddm_contents_count", "", "n/a (deprecated in 7.4+)")
println UASUtils.toCSVLine("data_ddm_fields_count", "${dDMFieldsCount}", "")
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

// StagingUtil::getLiveGroup(long) vs getLiveGroup(Group) availability differs across 7.4 Updates / Quarterly Releases
def _getLiveGroup = { group ->
    try {
        return StagingUtil.getLiveGroup(group) ?: group
    } catch (Throwable ignored) {
        try {
            return StagingUtil.getLiveGroup(group.groupId) ?: group
        } catch (Throwable ignored2) {
            return group
        }
    }
}

def siteDetails = ''<<''
def childGroupCount = 0;

siteDetails <<= "type, companyId, groupId, friendlyURL, name, staging, parent site name"

sites.each { it ->
    def parentGroupName = "N/A"
    def type = "PARENT_GROUP"
    def liveGroup = _getLiveGroup(it)
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

def stagingEnabled = sites.any{ _getLiveGroup(it).staged }

// data_staging_enabled
println UASUtils.toCSVLine('data_staging_enabled',"${stagingEnabled}",'see data_sites_count for more details')

// data_file_store_method
println UASUtils.toCSVLine('data_file_store_method', PropsUtil.get('dl.store.impl'), "");
// data_live_users_enabled
println UASUtils.toCSVLine('data_live_users_enabled', PropsUtil.get('live.users.enabled'), "");

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


// Additional 7.4 / Quarterly Release data points (each one guarded, as availability varies per release)
def _countVia = { String csvKey, String comment, Closure counter ->
    try {
        println UASUtils.toCSVLine(csvKey, "${counter()}", comment)
    } catch (Throwable t) {
        println UASUtils.toCSVLine(csvKey, "", "n/a - ${t}")
    }
}

_countVia('data_forms_count', 'Via DDMFormInstanceLocalServiceUtil::getDDMFormInstancesCount') {
    com.liferay.dynamic.data.mapping.service.DDMFormInstanceLocalServiceUtil.getDDMFormInstancesCount()
}
_countVia('data_form_records_count', 'Via DDMFormInstanceRecordLocalServiceUtil::getDDMFormInstanceRecordsCount') {
    com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalServiceUtil.getDDMFormInstanceRecordsCount()
}
_countVia('data_layout_page_template_structures_count', 'Content pages / page templates / master pages structures, via LayoutPageTemplateStructureLocalServiceUtil') {
    com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalServiceUtil.getLayoutPageTemplateStructuresCount()
}
_countVia('data_asset_entries_count', 'Via AssetEntryLocalServiceUtil::getAssetEntriesCount') {
    com.liferay.asset.kernel.service.AssetEntryLocalServiceUtil.getAssetEntriesCount()
}
_countVia('data_dl_file_versions_count', 'Via DLFileVersionLocalServiceUtil::getDLFileVersionsCount') {
    com.liferay.document.library.kernel.service.DLFileVersionLocalServiceUtil.getDLFileVersionsCount()
}
_countVia('data_organizations_count', 'Via OrganizationLocalServiceUtil::getOrganizationsCount') {
    OrganizationLocalServiceUtil.getOrganizationsCount()
}
_countVia('data_user_groups_count', 'Via UserGroupLocalServiceUtil::getUserGroupsCount') {
    UserGroupLocalServiceUtil.getUserGroupsCount()
}
_countVia('data_roles_count', 'Via RoleLocalServiceUtil::getRolesCount') {
    RoleLocalServiceUtil.getRolesCount()
}
_countVia('data_virtual_hosts_count', 'Via VirtualHostLocalServiceUtil::getVirtualHostsCount') {
    VirtualHostLocalServiceUtil.getVirtualHostsCount()
}
   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '2_data.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__2_data_groovy()
                
// the function 'invoke__2_data_groovy' will be invoked at the very end of the 'dxp_2025_q1-all.groovy'
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
                
// the function 'invoke__3_database_info_groovy' will be invoked at the very end of the 'dxp_2025_q1-all.groovy'
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
// Read the constants defensively, falling back to the raw portal properties, in case a constant
// is removed in a given 7.4 Update / Quarterly Release
def _propsValue = { String constantName, String propertyKey ->
    try {
        return PropsValues."${constantName}" as String
    } catch (Throwable ignored) {
        return com.liferay.portal.kernel.util.PropsUtil.get(propertyKey)
    }
}
String _PROPS_VALUES_MODULE_FRAMEWORK_BASE_DIR =
        _propsValue('MODULE_FRAMEWORK_BASE_DIR', 'module.framework.base.dir')
String _PROPS_VALUES_MODULE_FRAMEWORK_MARKETPLACE_DIR =
        _propsValue('MODULE_FRAMEWORK_MARKETPLACE_DIR', 'module.framework.marketplace.dir')
String _PROPS_VALUES_MODULE_FRAMEWORK_MODULES_DIR =
        _propsValue('MODULE_FRAMEWORK_MODULES_DIR', 'module.framework.modules.dir')
String _PROPS_VALUES_MODULE_FRAMEWORK_WAR_DIR =
        _propsValue('MODULE_FRAMEWORK_WAR_DIR', 'module.framework.war.dir')

// LayoutTemplateLocalServiceUtil
def _LayoutTemplateLocalServiceUtil_getLangType = { LayoutTemplate it ->
    // Method is present in 7.1+
    return LayoutTemplateLocalServiceUtil.getLangType(it.layoutTemplateId, it.standard, it.themeId)
}

// WARs shipped with the product are installed from 'osgi/portal-war' (or from an .lpkg / 'osgi/marketplace'),
// custom WARs from 'osgi/war' / the deploy folder. Quarterly Releases keep adding stock themes (cms-theme,
// ai-hub-theme, ...), so classify by install location instead of relying on a fixed list of names only.
Map<String, String> _contextPathToBundleLocation = [:]
try {
    com.liferay.portal.kernel.module.util.SystemBundleUtil.getBundleContext().bundles.each { b ->
        def webContextPath = b.headers.get('Web-ContextPath')
        if (webContextPath) {
            _contextPathToBundleLocation[(webContextPath as String).replaceFirst('^/', '')] = b.location
        }
    }
} catch (Throwable t) {
    uasUtils.debug "Unable to map web context paths to bundle locations: ${t}"
}
def _isLiferayShippedPlugin = { plugin ->
    def location = _contextPathToBundleLocation[plugin?.context?.toString()?.replaceFirst('^/', '')] ?: ''
    return plugin?.groupId == 'liferay' &&
            (location.contains('/osgi/portal-war/') || location.contains('.lpkg') || location.contains('/osgi/marketplace/'))
}

def allUniqueInstalledPlugins =
        PluginPackageUtil.getInstalledPluginPackages().sort().reverse().unique { it.moduleId }


// custom_code_all_installed_plugins_count

println UASUtils.toCSVLine(
        'custom_code_all_installed_plugins_count', allUniqueInstalledPlugins.size(),
        "Via PluginPackageUtil::getInstalledPluginPackages, custom ones and also the ones from 'liferay':\n${allUniqueInstalledPlugins.collect { " - ${it}" }.join('\n')}")


uasUtils.debugCSVLine "allUniqueInstalledPlugins (${allUniqueInstalledPlugins.size()})", """${allUniqueInstalledPlugins.collect { "* ${it}\n${it.properties.collect { "  - ${it.key}: ${it.value}" }.join('\n')}" }.join('\n')}"""

// custom_code_marketplace_lpkg_override_modules_count
// custom_code_static_lpkg_override_modules_count

// 'osgi/marketplace/override' by default
def marketplaceLpkgOverrideModulesDir = new File(_PROPS_VALUES_MODULE_FRAMEWORK_MARKETPLACE_DIR, 'override')

// 'osgi/static' by default; based on README.md in osgi/marketplace/override, it's
def staticLpkgOverrideModulesDir = new File(_PROPS_VALUES_MODULE_FRAMEWORK_BASE_DIR, 'static')

uasUtils.debugCSVLine 'marketplaceLpkgOverrideModulesDir', marketplaceLpkgOverrideModulesDir.absolutePath
uasUtils.debugCSVLine 'staticLpkgOverrideModulesDir', staticLpkgOverrideModulesDir.absolutePath

[
        custom_code_marketplace_lpkg_override_modules_count: marketplaceLpkgOverrideModulesDir,
        custom_code_static_lpkg_override_modules_count     : staticLpkgOverrideModulesDir
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
    } else {
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
    } else if (warOsgiPath.isFile()) {
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
def liferayHookPlugins = hookPlugins.findAll { it.groupId == 'liferay' || _isLiferayShippedPlugin(it) }
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

def defaultPluginsList = [~/liferay\/saml-hook\/\d*\.\d*\.\d*\.\d*\/war/]
def liferayPortletPlugins = [];

portletPlugins.each { plugin ->
    if (defaultPluginsList.find { plugin.moduleId.matches(it.pattern()) } || _isLiferayShippedPlugin(plugin)) {
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
                        it.layoutTemplateId == liferayTemplate.layoutTemplateId
            }
        }

println UASUtils.toCSVLine(
        'custom_code_layout_templates_count', customLayoutTemplates.size(),
        "Via LayoutTemplateLocalServiceUtil::getLayoutTemplates, excluding ${liferayLayoutTemplates.size()} authored by 'liferay' (${liferayLayoutTemplates*.layoutTemplateId}):\n${customLayoutTemplates.collect { " - ${it.name} { layoutTemplateId=${it.layoutTemplateId}, servletContextName=${it.servletContextName} }" }.join('\n')}")

// as in LayoutTemplateLocalServiceImpl::supportedLangTypes
def layoutTemplatesSupportedLangs = [TemplateConstants.LANG_TYPE_VM, TemplateConstants.LANG_TYPE_FTL]

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
def customOSGiModules = (customOSGiModulesDir.listFiles() ?: []).findAll { it.name.endsWith('.jar') }

uasUtils.debugCSVLine 'customOSGiModulesDir', customOSGiModulesDir.absolutePath

println UASUtils.toCSVLine(
        'custom_code_osgi_modules_count', customOSGiModules.size(),
        "Counted by listing the '${customOSGiModulesDir}' directory (*.jar):\n${customOSGiModules.collect { " - ${it.name}" }.join('\n')}"
)

// Themes

def themePlugins = allUniqueInstalledPlugins.findAll {
    'theme' in it.types
}

def defaultThemesList = [~/liferay\/speedwell-theme\/\d*\.\d*\.\d*\/war/, ~/liferay\/minium-theme\/\d*\.\d*\.\d*\/war/, ~/liferay\/dialect-theme\/\d*\.\d*\.\d*\/war/, ~/liferay\/classic-theme\/\d*\.\d*\.\d*\/war/, ~/liferay\/admin-theme\/\d*\.\d*\.\d*\/war/, ~/liferay\/cms-theme\/\d*\.\d*\.\d*\/war/]
def liferayThemePluginPackages = [];

themePlugins.each { theme ->
    if (defaultThemesList.find { theme.moduleId.matches(it.pattern()) } || _isLiferayShippedPlugin(theme)) {
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
        [TemplateConstants.LANG_TYPE_VM, TemplateConstants.LANG_TYPE_FTL, 'jsp']

def customThemesByLang = customThemes.groupBy { it.templateExtension }
themesSupportedLangs.each { customThemesByLang.putIfAbsent(it, Collections.emptyList()) }

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


// Features of Liferay Hook extension point

Map<String, String> hookContextToLiferayHookXmlContent = customHookPlugins.collectEntries {
    def contextName = it.context

    // TODO app server might not explode the .wars (into directories) and keep them as .war in auto-deploy dir
    File warAppServerAutoDeployPath = new File(DeployUtil.getAutoDeployDestDir(), contextName)
    File warOsgiPath = new File(_PROPS_VALUES_MODULE_FRAMEWORK_WAR_DIR, "${contextName}.war")

    String liferayHookXmlContent

    if (warAppServerAutoDeployPath.isDirectory()) {
        liferayHookXmlContent = new File(warAppServerAutoDeployPath, LIFERAY_HOOK_XML_WEBAPP_PATH).text

    } else if (warOsgiPath.isFile()) {
        liferayHookXmlContent = ZipFileUtil.openInputStream(warOsgiPath, LIFERAY_HOOK_XML_WEBAPP_PATH).text
    } else {
        uasUtils.debug "Hook '${contextName}' found in neither '${warAppServerAutoDeployPath}' nor '${warOsgiPath}' (probably part of some .lpkg), cannot get the content of its '${LIFERAY_HOOK_XML_WEBAPP_PATH}'."
    }

    return [contextName, liferayHookXmlContent]
}

Map<String, Boolean> contextNameToCustomJspDir = [:]
Map<String, Boolean> contextNameToCustomJspApplicationAdapter = [:]
Map<String, List<String>> contextNameToServletFilters = [:]
Map<String, List<String>> contextNameToStrutsActions = [:]
Map<String, List<String>> contextNameToIndexerPostProcessors = [:]
Map<String, List<String>> contextNameToServiceWrappers = [:]
Map<String, Boolean> contextNameToOverridePortalProperties = [:]
Map<String, Integer> contextNameToOverrideLanguageProperties = [:]

def unprocessedHookContexts = hookContextToLiferayHookXmlContent.findAll { !it.value }.collect { it.key }
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

// Liferay Service Builder: How many?

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

    headerContent = bundle.getHeaders().get("Fragment-Host")

    // Skip fragments shipped by Liferay itself (e.g. com.liferay.adaptive.media.blogs.web.fragment), only report custom ones
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


   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '4_custom_code.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__4_custom_code_groovy()
                
// the function 'invoke__4_custom_code_groovy' will be invoked at the very end of the 'dxp_2025_q1-all.groovy'
// invoke__4_custom_code_groovy([ debug: false ])              
// END included '4_custom_code.groovy'

// BEGIN included '5_new_items.groovy'  

// Imports for '5_new_items.groovy' 
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil
import com.liferay.object.service.ObjectDefinitionLocalServiceUtil
import com.liferay.object.service.ObjectEntryLocalServiceUtil
import com.liferay.segments.service.SegmentsEntryLocalServiceUtil
import org.jsoup.Jsoup
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil
import com.liferay.portal.kernel.module.util.SystemBundleUtil

def loadOptionalClass(String className, String bundleSymbolicName) {
    try {
        def bundleContext = SystemBundleUtil.getBundleContext()
        def bundle = bundleContext.bundles.find {
            it.symbolicName == bundleSymbolicName && it.state == Bundle.ACTIVE
        }
        if (bundle == null) {
            // Bundle symbolic names occasionally change between 7.4 Updates / Quarterly Releases:
            // fall back to any ACTIVE bundle whose symbolic name is a package prefix of the class
            bundle = bundleContext.bundles.find {
                it.state == Bundle.ACTIVE && it.symbolicName && className.startsWith("${it.symbolicName}.")
            }
        }
        if (bundle == null) {
            out.println("Bundle not found or not ACTIVE: ${bundleSymbolicName}")
            return null
        }
        return bundle.loadClass(className)
    } catch (Throwable e) {
        out.println("loadOptionalClass failed for ${className}: ${e}")
        return null
    }
}

// Wrapper function for '5_new_items.groovy'
def invoke__5_new_items_groovy(Map<String, Object> uasContext) {

    try {
// Objects

//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.CompanyLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.object.service.ObjectDefinitionLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.object.service.ObjectEntryLocalServiceUtil

int STATUS_APPROVED = 0  // WorkflowConstants.STATUS_APPROVED

def companies = CompanyLocalServiceUtil.getCompanies()

try {
    int grandTotalSystemCount = 0
    int grandTotalCustomCount = 0
    long grandTotalCustomEntriesCount = 0

    def objectEntrydefinitions = new StringBuilder()
    def customObjectDetails = new StringBuilder('companyId, name, label, scope, storage, active, fields, relationships, entries\n')

    // Getter availability differs per release (e.g. getStorageType only exists on newer Quarterly Releases)
    def _safeGet = { obj, String getter ->
        try {
            return obj."${getter}"()
        } catch (Throwable ignored) {
            return 'n/a'
        }
    }

    companies.each { company ->
        long companyId = company.getCompanyId()

        // getObjectDefinitions(long companyId, int status) does not exist in early 7.4 Updates / CE GAs,
        // getObjectDefinitions(int start, int end) exists in all of them
        def objectDefinitions = ObjectDefinitionLocalServiceUtil.getObjectDefinitions(-1, -1).findAll {
            it.companyId == companyId && it.status == STATUS_APPROVED
        }

        int companySystemCount = 0
        int companyCustomCount = 0

        objectDefinitions.each { objectDefinition ->
            long objectDefinitionId = objectDefinition.getObjectDefinitionId()
            boolean isSystem = objectDefinition.isSystem()  // true means system object

            // Count by objectDefinitionId: getObjectEntriesCount(long, long) takes a *groupId* (0 for company scoped
            // objects), so passing the companyId always returned 0. System objects store their data in their own tables.
            def count = ''
            if (isSystem) {
                count = 'n/a (system object, stored in its own table)'
            } else {
                try {
                    def dynamicQuery = ObjectEntryLocalServiceUtil.dynamicQuery()
                    dynamicQuery.add(com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil.eq('objectDefinitionId', objectDefinitionId))
                    count = ObjectEntryLocalServiceUtil.dynamicQueryCount(dynamicQuery)
                } catch (Throwable t) {
                    count = "n/a (${t.class.simpleName})"
                }
            }

            if (isSystem) {
                companySystemCount++
            } else {
                companyCustomCount++
                if (count instanceof Number) {
                    grandTotalCustomEntriesCount += count
                }
            }

            objectEntrydefinitions.append("Company ID: ${companyId}, Object: ${objectDefinition.getName()}, System: ${isSystem}, Entries: ${count}").append('\n')

            if (!isSystem) {
                def fieldsCount = 'n/a'
                try {
                    fieldsCount = com.liferay.object.service.ObjectFieldLocalServiceUtil.getObjectFields(objectDefinitionId).size()
                } catch (Throwable ignored) {
                }
                def relationshipsCount = 'n/a'
                try {
                    relationshipsCount = com.liferay.object.service.ObjectRelationshipLocalServiceUtil.getObjectRelationships(objectDefinitionId).size()
                } catch (Throwable ignored) {
                }
                customObjectDetails.append("${companyId}, ${objectDefinition.getName()}, ${_safeGet(objectDefinition, 'getLabelCurrentValue')}, " +
                        "${_safeGet(objectDefinition, 'getScope')}, ${_safeGet(objectDefinition, 'getStorageType')}, " +
                        "${_safeGet(objectDefinition, 'isActive')}, ${fieldsCount}, ${relationshipsCount}, ${count}").append('\n')
            }
        }


        grandTotalSystemCount += companySystemCount
        grandTotalCustomCount += companyCustomCount
    }


    println UASUtils.toCSVLine(
            'newitems_object_entry_definitions', grandTotalSystemCount + grandTotalCustomCount,objectEntrydefinitions.toString())
    println UASUtils.toCSVLine(
            'newitems_total_system_object_entry_definition_count', grandTotalSystemCount,"Number of system object entries")
    println UASUtils.toCSVLine(
            'newitems_total_custom_object_entry_definition_count', grandTotalCustomCount,"Number of custom object entries")
    println UASUtils.toCSVLine(
            'newitems_total_custom_object_entries_count', grandTotalCustomEntriesCount, "Total number of entries stored in custom object definitions")
    println UASUtils.toCSVLine(
            'newitems_custom_object_definitions_info', grandTotalCustomCount, customObjectDetails.toString())
} catch (Throwable t) {
    println UASUtils.toCSVLine('newitems_object_entry_definitions', '', "n/a - ${t}")
}


// List segments
//__GRADLE_COMMENT__ import com.liferay.segments.service.SegmentsEntryLocalServiceUtil
//__GRADLE_COMMENT__ import org.jsoup.Jsoup

try {
    def segmentsEntries = SegmentsEntryLocalServiceUtil.getSegmentsEntries(-1, -1)

    def sb = new StringBuffer()

    segmentsEntries.each { entry ->
        def rawName = entry.getName() ?: "(unnamed)"
        def cleanName = Jsoup.parse(rawName).text()

        def active = entry.isActive()

        sb.append("Name: ${cleanName} Active: ${active}\n")
    }

    println UASUtils.toCSVLine(
            'newitems_total_number_of_segments', segmentsEntries.size(), sb.toString())
} catch (Throwable t) {
    println UASUtils.toCSVLine('newitems_total_number_of_segments', '', "n/a - ${t}")
}


// Number of Commerce products

//__GRADLE_COMMENT__ import com.liferay.portal.kernel.service.CompanyLocalServiceUtil
//__GRADLE_COMMENT__ import com.liferay.commerce.product.model.CPDefinition
//__GRADLE_COMMENT__ import com.liferay.commerce.product.service.CPDefinitionLocalServiceUtil

def globalCount = 0

def cpDefinitionServiceClass = loadOptionalClass(
    "com.liferay.commerce.product.service.CPDefinitionLocalServiceUtil",
    "com.liferay.commerce.product.service")

if (cpDefinitionServiceClass) {
    try {
        companies.each { company ->
            long companyId = company.getCompanyId()

            def cpDefinitions = cpDefinitionServiceClass.getCPDefinitions(-1, -1).findAll {
                it.getCompanyId() == companyId
            }

            globalCount += cpDefinitions.size()
        }


        println UASUtils.toCSVLine(
            'newitems_total_commerce_products', globalCount, "Total number of commerce product across all companies")
    } catch (Throwable t) {
        println UASUtils.toCSVLine('newitems_total_commerce_products', '', "n/a - ${t}")
    }

} else {
    out.println("Commerce not available — skipping CPDefinition count")
}


// Additional 7.4 / Quarterly Release features (2023.Q3+). Each one is optional: a missing or inactive module
// in a given release results in an 'n/a' line rather than aborting the section.
def _optionalCount = { String csvKey, String className, String bundleSymbolicName, String methodName, String comment ->
    def serviceClass = loadOptionalClass(className, bundleSymbolicName)

    if (serviceClass == null) {
        println UASUtils.toCSVLine(csvKey, '', "n/a - ${className} not available in this release")
        return
    }

    try {
        println UASUtils.toCSVLine(csvKey, serviceClass."${methodName}"(), comment)
    } catch (Throwable t) {
        println UASUtils.toCSVLine(csvKey, '', "n/a - ${t}")
    }
}

_optionalCount('newitems_client_extensions_count',
        'com.liferay.client.extension.service.ClientExtensionEntryLocalServiceUtil', 'com.liferay.client.extension.service',
        'getClientExtensionEntriesCount', 'Client extensions created via the UI (Applications > Client Extensions)')
_optionalCount('newitems_picklists_count',
        'com.liferay.list.type.service.ListTypeDefinitionLocalServiceUtil', 'com.liferay.list.type.service',
        'getListTypeDefinitionsCount', 'Picklists (ListTypeDefinition) used by Objects')
_optionalCount('newitems_object_relationships_count',
        'com.liferay.object.service.ObjectRelationshipLocalServiceUtil', 'com.liferay.object.service',
        'getObjectRelationshipsCount', 'Object relationships')
_optionalCount('newitems_object_actions_count',
        'com.liferay.object.service.ObjectActionLocalServiceUtil', 'com.liferay.object.service',
        'getObjectActionsCount', 'Object actions')
_optionalCount('newitems_search_blueprints_count',
        'com.liferay.search.experiences.service.SXPBlueprintLocalServiceUtil', 'com.liferay.search.experiences.service',
        'getSXPBlueprintsCount', 'Search Experiences blueprints')
_optionalCount('newitems_search_elements_count',
        'com.liferay.search.experiences.service.SXPElementLocalServiceUtil', 'com.liferay.search.experiences.service',
        'getSXPElementsCount', 'Search Experiences elements (system + custom)')
_optionalCount('newitems_workflow_definitions_count',
        'com.liferay.portal.workflow.kaleo.service.KaleoDefinitionLocalServiceUtil', 'com.liferay.portal.workflow.kaleo.service',
        'getKaleoDefinitionsCount', 'Kaleo workflow definitions')
_optionalCount('newitems_style_books_count',
        'com.liferay.style.book.service.StyleBookEntryLocalServiceUtil', 'com.liferay.style.book.service',
        'getStyleBookEntriesCount', 'Style books')
_optionalCount('newitems_collections_count',
        'com.liferay.asset.list.service.AssetListEntryLocalServiceUtil', 'com.liferay.asset.list.service',
        'getAssetListEntriesCount', 'Collections (asset lists)')
_optionalCount('newitems_batch_planner_plans_count',
        'com.liferay.batch.planner.service.BatchPlannerPlanLocalServiceUtil', 'com.liferay.batch.planner.service',
        'getBatchPlannerPlansCount', 'Import / export (batch planner) templates')
_optionalCount('newitems_oauth2_applications_count',
        'com.liferay.oauth2.provider.service.OAuth2ApplicationLocalServiceUtil', 'com.liferay.oauth2.provider.service',
        'getOAuth2ApplicationsCount', 'OAuth2 applications (incl. those created for client extensions)')

// Client extensions deployed as workspace .zip files (osgi/client-extensions)
try {
    def liferayHome = com.liferay.portal.kernel.util.PropsUtil.get('liferay.home')
    def clientExtensionsDir = new File(liferayHome, 'osgi/client-extensions')
    def deployedClientExtensions = (clientExtensionsDir.listFiles() ?: []).findAll {
        it.name.endsWith('.zip') || it.name.endsWith('.json')
    }
    println UASUtils.toCSVLine(
            'newitems_deployed_client_extensions_count', deployedClientExtensions.size(),
            clientExtensionsDir.isDirectory() ?
                    "Counted by listing the '${clientExtensionsDir}' directory (*.zip, *.json):\n${deployedClientExtensions.collect { " - ${it.name}" }.join('\n')}" :
                    "The '${clientExtensionsDir}' directory does not exist.")
} catch (Throwable t) {
    println UASUtils.toCSVLine('newitems_deployed_client_extensions_count', '', "n/a - ${t}")
}

// Client extensions / remote apps details. Depending on the release:
//  - early 7.4 GA / Updates: "Remote Apps" (RemoteAppEntry)
//  - later 7.4 Updates and Quarterly Releases: "Client Extensions", created in the UI (ClientExtensionEntry)
//    and/or deployed from a Liferay Workspace (.zip in osgi/client-extensions); CETManager lists both
def _cxGet = { obj, String getter, Object... args ->
    try {
        def value = obj."${getter}"(*args)
        return value instanceof Map ? value.values().join(' ') : value
    } catch (Throwable ignored) {
        return null
    }
}
def _cxName = { obj ->
    _cxGet(obj, 'getNameCurrentValue') ?: _cxGet(obj, 'getName', com.liferay.portal.kernel.util.LocaleUtil.getDefault()) ?: _cxGet(obj, 'getName') ?: '(unnamed)'
}

// Remote Apps (early 7.4)
def remoteAppServiceClass = loadOptionalClass('com.liferay.remote.app.service.RemoteAppEntryLocalServiceUtil', 'com.liferay.remote.app.service')
if (remoteAppServiceClass) {
    try {
        def remoteApps = remoteAppServiceClass.getRemoteAppEntries(-1, -1)
        println UASUtils.toCSVLine('newitems_remote_apps_count', remoteApps.size(),
                "Remote Apps (predecessor of Client Extensions), name | type | URL:\n${remoteApps.collect { " - ${_cxName(it)} | ${_cxGet(it, 'getType') ?: 'n/a'} | ${_cxGet(it, 'getIFrameURL') ?: _cxGet(it, 'getCustomElementURLs') ?: _cxGet(it, 'getUrl') ?: 'n/a'}" }.join('\n')}")
    } catch (Throwable t) {
        println UASUtils.toCSVLine('newitems_remote_apps_count', '', "n/a - ${t}")
    }
} else {
    println UASUtils.toCSVLine('newitems_remote_apps_count', '', 'n/a - Remote Apps not available in this release (replaced by Client Extensions)')
}

// Client extensions created in the UI
def clientExtensionServiceClass = loadOptionalClass('com.liferay.client.extension.service.ClientExtensionEntryLocalServiceUtil', 'com.liferay.client.extension.service')
if (clientExtensionServiceClass) {
    try {
        def entries = clientExtensionServiceClass.getClientExtensionEntries(-1, -1)
        println UASUtils.toCSVLine('newitems_client_extensions_info', entries.size(),
                "Client extensions created in the UI, name | type | status (0 = approved):\n${entries.collect { " - ${_cxName(it)} | ${_cxGet(it, 'getType') ?: 'n/a'} | ${_cxGet(it, 'getStatus')}" }.join('\n')}")
    } catch (Throwable t) {
        println UASUtils.toCSVLine('newitems_client_extensions_info', '', "n/a - ${t}")
    }
} else {
    println UASUtils.toCSVLine('newitems_client_extensions_info', '', 'n/a - Client Extensions not available in this release')
}

// All client extensions (UI + workspace deployed), via the CETManager OSGi service
try {
    def bundleContext = SystemBundleUtil.getBundleContext()
    def cetManagerReference = bundleContext.getServiceReference('com.liferay.client.extension.type.manager.CETManager')

    if (cetManagerReference == null) {
        println UASUtils.toCSVLine('newitems_all_client_extensions_count', '', 'n/a - CETManager not available in this release')
    } else {
        def cetManager = bundleContext.getService(cetManagerReference)
        try {
            def getCETsMethod = cetManager.class.methods.find { it.name == 'getCETs' && it.parameterTypes.length > 0 && it.parameterTypes[0] == long.class }
            def allCETs = []
            companies.each { company ->
                def args = getCETsMethod.parameterTypes.collect { type ->
                    if (type.simpleName == 'Pagination') {
                        // com.liferay.portal.vulcan.pagination.Pagination is mandatory in newer releases
                        return type.getMethod('of', int.class, int.class).invoke(null, 1, 100000)
                    }
                    type == long.class ? company.companyId : (type == int.class ? -1 : (type == boolean.class ? false : null))
                }
                args[0] = company.companyId
                allCETs.addAll(getCETsMethod.invoke(cetManager, args as Object[]) ?: [])
            }
            def byType = allCETs.groupBy { _cxGet(it, 'getType') ?: 'n/a' }.collect { type, cets -> "${type}: ${cets.size()}" }.join(', ')
            println UASUtils.toCSVLine('newitems_all_client_extensions_count', allCETs.size(),
                    "Client extensions created in the UI and deployed from a Liferay Workspace (by type: ${byType ?: 'none'}), " +
                    "name | type | source:\n${allCETs.collect { " - ${_cxName(it)} | ${_cxGet(it, 'getType') ?: 'n/a'} | ${_cxGet(it, 'isReadOnly') ? 'workspace deployment' : 'created in the UI'}" }.join('\n')}")
        } finally {
            bundleContext.ungetService(cetManagerReference)
        }
    }
} catch (Throwable t) {
    println UASUtils.toCSVLine('newitems_all_client_extensions_count', '', "n/a - ${t}")
}
   
    } catch (Throwable t) {
        println "UAS ERROR: Execution failed for topic script '5_new_items.groovy': ${t}"
        println t.getStackTrace().collect { "    ${it}" }.join('\n')
        
        // swallow the exception, to continue with run of the next topic file, if any
    }

    println ""
} // END invoke__5_new_items_groovy()
                
// the function 'invoke__5_new_items_groovy' will be invoked at the very end of the 'dxp_2025_q1-all.groovy'
// invoke__5_new_items_groovy([ debug: false ])              
// END included '5_new_items.groovy'

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

        try {
            def preferences
            try {
                preferences = com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil.getPortletPreferences()
            } catch (MissingMethodException ignored) {
                preferences = com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil.getPortletPreferences(-1, -1)
            }
            // ownerType 3 = PortletKeys.PREFS_OWNER_TYPE_LAYOUT (widget placed on a page)
            def portletIdToPlids = [:]
            preferences.each { p ->
                if (p.ownerType == 3 && p.plid > 0) {
                    def root = (p.portletId - ~/_INSTANCE.*/).toLowerCase()
                    def plids = portletIdToPlids[root]
                    if (plids == null) {
                        plids = [] as Set
                        portletIdToPlids[root] = plids
                    }
                    plids << p.plid
                }
            }

            // Widgets placed on widget pages are also stored in the page layout (column-N=portletId,...), even when
            // no preferences exist yet (e.g. a deprecated widget that is hidden and never rendered)
            com.liferay.portal.kernel.service.LayoutLocalServiceUtil.getLayouts(-1, -1).findAll { it.type == 'portlet' }.each { layout ->
                try {
                    layout.typeSettingsProperties.each { key, value ->
                        if (key.toString().startsWith('column-') || key.toString().startsWith('nested-column-')) {
                            value.toString().tokenize(',').each { portletId ->
                                def root = (portletId.trim() - ~/_INSTANCE.*/).toLowerCase()
                                if (root) {
                                    def plids = portletIdToPlids[root]
                                    if (plids == null) {
                                        plids = [] as Set
                                        portletIdToPlids[root] = plids
                                    }
                                    plids << layout.plid
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            // Ignore widgets placed in the Control Panel
            def controlPanelPlids = [] as Set
            portletIdToPlids.values().flatten().unique().each { plid ->
                try {
                    def layout = com.liferay.portal.kernel.service.LayoutLocalServiceUtil.fetchLayout(plid)
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
            def siteGroupIds = com.liferay.portal.kernel.service.GroupLocalServiceUtil.getGroups(-1, -1).findAll {
                it.site && !it.isControlPanel() && !it.isUser()
            }*.groupId as Set

            def typeCounts = [:].withDefault { 0 }
            def privateSitePages = 0
            def layouts = com.liferay.portal.kernel.service.LayoutLocalServiceUtil.getLayouts(-1, -1)
            layouts.each { layout ->
                if (layout.groupId in siteGroupIds) {
                    def system = false
                    try {
                        system = layout.isSystem()  // drafts and master / template pages (7.2+)
                    } catch (Throwable ignored) {
                    }
                    if (!system) {
                        typeCounts[layout.type] = typeCounts[layout.type] + 1
                        if (layout.privateLayout) {
                            privateSitePages++
                        }
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
            def sitesWithOwnLanguages = com.liferay.portal.kernel.service.GroupLocalServiceUtil.getGroups(-1, -1).findAll {
                it.site && it.getTypeSettingsProperty('inheritLocales') == 'false'
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

// invoke the topic scripts' wrapper functions                         
def uasContext = [
        debug: false,
        // The release this script was built for
        targetRelease: '2025.Q1',
        targetReleaseLts: true,
        targetScriptFileName: 'dxp_2025_q1-all.groovy'
]

invoke__1_infra_groovy(uasContext)
invoke__2_data_groovy(uasContext)
invoke__3_database_info_groovy(uasContext)
invoke__4_custom_code_groovy(uasContext)
invoke__5_new_items_groovy(uasContext)
invoke__6_deprecations_groovy(uasContext)
invoke__7_additional_info_groovy(uasContext)
  
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