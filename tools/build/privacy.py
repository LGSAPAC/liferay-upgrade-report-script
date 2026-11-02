"""Adds the privacy option (UAS_MASK_PRIVATE_DATA) to the version scripts (idempotent).

usage: privacy.py <file> ...
"""
import os, sys

GEN = os.path.dirname(os.path.abspath(__file__))
SECTION = open(os.path.join(GEN, 'privacy_section.groovy')).read()

FLAG = '''
// ---------------------------------------------------------------------------------------------------------------
// PRIVACY OPTION
// Set to true to mask site names, site URLs, virtual instance names / web IDs, virtual hosts, host names in URLs,
// e-mail addresses, IP addresses, the database location and installation paths in the output. All counts and
// technical details are kept, so the report can still be assessed.
def UAS_MASK_PRIVATE_DATA = false
// ---------------------------------------------------------------------------------------------------------------
'''

UTILS = r'''
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
'''


def apply(path):
    s = open(path).read()
    if 'UAS_MASK_PRIVATE_DATA' not in s:
        date_line = 'def DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH.mm.ss.SSSSS Z"\n'
        assert date_line in s, path
        s = s.replace(date_line, date_line + FLAG, 1)

        assert s.count('class UASUtils {\n') == 1, path
        s = s.replace('class UASUtils {\n', 'class UASUtils {\n' + UTILS, 1)

        csv = """'"' + escapeCSV(it?.toString() ?: '') + '"'"""
        assert s.count(csv) == 1, path
        s = s.replace(csv, """'"' + escapeCSV(mask(it?.toString() ?: '')) + '"'""")

        end_utils = "// END included '_utils.groovy'\n"
        assert s.count(end_utils) == 1, path
        s = s.replace(end_utils, end_utils + '\n' + SECTION, 1)

        i = s.index('\ndef uasContext') + 1
        s = s[:i] + 'uasInitPrivacy(UAS_MASK_PRIVATE_DATA)\n\n' + s[i:]

        foot = "    ${UASUtils.toCSVLine('uas_target_liferay_version', uasMetadata['sourceSetLabel'])}\n"
        assert s.count(foot) == 1, path
        s = s.replace(foot, foot + "    ${UASUtils.toCSVLine('uas_private_data_masked', UAS_MASK_PRIVATE_DATA)}\n", 1)
    else:
        # refresh the privacy section
        begin, end = "// BEGIN included '_uas_privacy.groovy'", "// END included '_uas_privacy.groovy'\n"
        a = s.index(begin)
        b = s.index(end) + len(end) + 1
        s = s[:a] + SECTION + s[b:]
    open(path, 'w').write(s)
    print('privacy', path)


for p in sys.argv[1:]:
    apply(p)
