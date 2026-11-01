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

