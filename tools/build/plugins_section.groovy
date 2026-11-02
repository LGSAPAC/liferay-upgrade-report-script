// BEGIN included '_uas_plugins.groovy'
// PluginPackageUtil.getInstalledPluginPackages() sorts the installed plugin packages and fails with a
// NullPointerException when one of them has no package information (seen while a plugin is being deployed).
// uasInstalledPluginPackages() retries, then falls back to the unsorted list, so the custom code section completes.
def uasInstalledPluginPackages() {
    def util = null
    try {
        util = Class.forName('com.liferay.portal.plugin.PluginPackageUtil', true, this.getClass().getClassLoader())
    } catch (Throwable ignored) {
        return []
    }
    Throwable error = null
    for (int attempt = 0; attempt < 3; attempt++) {
        try {
            return (util.getInstalledPluginPackages() ?: []).findAll { it != null }
        } catch (Throwable t) {
            error = t
            try {
                Thread.sleep(2000)
            } catch (Throwable ignored) {
            }
        }
    }
    // fallback: read the repository of installed plugin packages without sorting it
    try {
        def repository = null
        def holders = [null]
        util.declaredFields.each { field ->
            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type.name == 'com.liferay.portal.plugin.PluginPackageUtil') {
                field.accessible = true
                holders << field.get(null)
            }
        }
        holders.each { holder ->
            util.declaredFields.each { field ->
                if (repository == null && field.type.name == 'com.liferay.portal.plugin.LocalPluginPackageRepository' &&
                        (holder != null || java.lang.reflect.Modifier.isStatic(field.modifiers))) {
                    field.accessible = true
                    repository = field.get(holder)
                }
            }
        }
        def mapField = repository?.class?.declaredFields?.find { Map.isAssignableFrom(it.type) }
        if (mapField != null) {
            mapField.accessible = true
            return (mapField.get(repository)?.values()?.toList() ?: []).findAll { it != null }
        }
    } catch (Throwable ignored) {
    }
    throw error
}
// END included '_uas_plugins.groovy'

