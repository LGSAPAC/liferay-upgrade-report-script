import os, re, sys
repo = sys.argv[1]
CE = [  # (source script, ce version, label for the banner)
    ('ee_6_1/ee_6_1-all.groovy', '6.1'),
    ('ee_6_2/ee_6_2-all.groovy', '6.2'),
    ('dxp_7_0/dxp_7_0-all.groovy', '7.0'),
    ('dxp_7_1/dxp_7_1-all.groovy', '7.1'),
    ('dxp_7_2/dxp_7_2-all.groovy', '7.2'),
    ('dxp_7_3/dxp_7_3-all.groovy', '7.3'),
    ('dxp_7_4/dxp_7_4-all.groovy', '7.4'),
]
section = open(os.path.join(os.path.dirname(__file__), 'ce_section.groovy')).read()
for src, ver in CE:
    s = open(os.path.join(repo, src)).read()
    src_name = os.path.basename(src)
    folder = 'ce_' + ver.replace('.', '_')
    fname = folder + '-all.groovy'
    # drop a generated banner of the source (7.4), keep everything else identical
    s = re.sub(r'\A(// [^\n]*\n)+\n', '', s) if s.startswith('// Liferay') else s
    s = s.replace(src_name, fname)
    anchor = '// invoke the topic scripts\' wrapper functions'
    assert s.count(anchor) == 1, src
    s = s.replace(anchor, section + anchor)
    import re as _re
    m = _re.search(r'^.*invoke__1_infra_groovy\(uasContext\).*\n', s, _re.M)
    assert m, src
    ce_call = f"invoke__ce_edition_groovy(uasContext + [ceTargetVersion: '{ver}', ceScriptFileName: '{fname}'])"
    if 'uasSectionTimings' in s:
        ce_call = f"uasTimed(uasSectionTimings, 'ce_edition') {{ {ce_call} }}"
    s = s[:m.end()] + ce_call + '\n' + s[m.end():]
    foot = "    ${UASUtils.toCSVLine('uas_target_liferay_version', uasMetadata['sourceSetLabel'])}\n"
    assert s.count(foot) == 1, src
    s = s.replace(foot, foot + "    ${UASUtils.toCSVLine('uas_target_liferay_edition', 'CE')}\n")
    # Do not count fragment bundles shipped by Liferay itself (e.g. com.liferay.adaptive.media.blogs.web.fragment)
    frag = "    if (headerContent != null && !headerContent.isEmpty()) {\n"
    if frag in s:
        s = s.replace(frag, "    // Skip fragments shipped by Liferay itself, only report custom ones\n"
                            "    def isLiferayShippedFragment = bundle.getSymbolicName()?.startsWith('com.liferay.') && !(bundle.getLocation() ?: '').contains('/osgi/modules/')\n\n"
                            "    if (headerContent != null && !headerContent.isEmpty() && !isLiferayShippedFragment) {\n")
    banner = (f"// Liferay Portal Community Edition (CE) {ver} - upgrade assessment script (read-only)\n"
              f"// Run on a UAT / test copy of Liferay Portal CE {ver} (any GA), never on production, at your own risk.\n"
              f"// Control Panel > Server Administration > Script (language: Groovy). Save the output as a .txt file.\n"
              f"// Output is CSV: \"Key\", \"Value\", \"Description\" - same keys as {src_name}, plus ce_* keys.\n\n")
    os.makedirs(os.path.join(repo, folder), exist_ok=True)
    open(os.path.join(repo, folder, fname), 'w').write(banner + s)
    print('wrote', folder + '/' + fname, 'from', src)
