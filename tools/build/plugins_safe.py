"""Makes the installed plugin package lookups fail-safe in the version scripts (idempotent): replaces
PluginPackageUtil.getInstalledPluginPackages() with uasInstalledPluginPackages() (plugins_section.groovy).

usage: plugins_safe.py <file> ...
"""
import os, sys

SECTION = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'plugins_section.groovy')).read()
BEGIN, END = "// BEGIN included '_uas_plugins.groovy'", "// END included '_uas_plugins.groovy'\n"
ANCHOR = "// END included '_utils.groovy'\n"

for path in sys.argv[1:]:
    s = open(path).read()
    for old in ('com.liferay.portal.plugin.PluginPackageUtil.getInstalledPluginPackages()', 'PluginPackageUtil.getInstalledPluginPackages()'):
        s = s.replace(old, 'uasInstalledPluginPackages()')
    if BEGIN in s:
        a = s.index(BEGIN)
        b = s.index(END) + len(END) + 1
        s = s[:a] + SECTION + s[b:]
    else:
        assert s.count(ANCHOR) == 1, path
        s = s.replace(ANCHOR, ANCHOR + '\n' + SECTION, 1)
    open(path, 'w').write(s)
    print('plugins', path)
