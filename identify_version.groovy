// Step 0: identifies the Liferay edition and version and names the upgrade assessment script to run.
// Read-only. Works on Liferay Portal 6.1 to Liferay DXP Quarterly Releases.
// Run via Control Panel > Server Administration > Script (language: Groovy).

def releaseInfo = com.liferay.portal.kernel.util.ReleaseInfo.getReleaseInfo()
def version = com.liferay.portal.kernel.util.ReleaseInfo.getVersion()
def parts = version.tokenize('.')
def majorMinor = parts.size() >= 2 ? parts[0] + '.' + parts[1] : version

def edition
def script
def quarterly = (releaseInfo =~ /(\d{4})\.Q([1-4])/)

if (releaseInfo.contains('Community Edition') || (releaseInfo =~ /\bCE\b/).find()) {
    edition = 'Liferay Portal Community Edition (CE)'
    script = "ce_${majorMinor.replace('.', '_')}/ce_${majorMinor.replace('.', '_')}-all.groovy"
} else if (quarterly.find()) {
    edition = 'Liferay DXP Quarterly Release'
    def name = "dxp_${quarterly.group(1)}_q${quarterly.group(2)}"
    script = "${name}/${name}-all.groovy"
} else if (majorMinor.startsWith('6.')) {
    edition = 'Liferay Portal Enterprise Edition (EE)'
    script = "ee_${majorMinor.replace('.', '_')}/ee_${majorMinor.replace('.', '_')}-all.groovy"
} else {
    edition = 'Liferay DXP'
    script = "dxp_${majorMinor.replace('.', '_')}/dxp_${majorMinor.replace('.', '_')}-all.groovy"
}

println "Release:  ${releaseInfo}"
println "Edition:  ${edition}"
println "Version:  ${version}"
println "Script:   ${script}"
println ""
println "Download ${script} from https://github.com/LGSAPAC/liferay-upgrade-report-script and run it on your UAT environment."
