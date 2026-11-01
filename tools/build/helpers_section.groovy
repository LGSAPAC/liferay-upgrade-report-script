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

