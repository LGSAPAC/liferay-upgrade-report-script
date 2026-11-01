import os, re, sys
repo, template = sys.argv[1], sys.argv[2]
LTS = {'2023.Q4', '2024.Q2', '2025.Q1', '2026.Q1'}
quarterlies = [f'{y}.Q{q}' for y in (2023, 2024, 2025, 2026) for q in (1, 2, 3, 4)]
quarterlies = [r for r in quarterlies if '2023.Q3' <= r <= '2026.Q3']
tpl = open(template).read()
QBLOCK = re.compile(r'// @@QUARTERLY_ONLY_BEGIN@@\n(.*?)// @@QUARTERLY_ONLY_END@@\n', re.S)

def build(target, folder, lts, banner_title, quarterly):
    fname = folder + '-all.groovy'
    body = QBLOCK.sub((lambda m: m.group(1)) if quarterly else '', tpl)
    banner = (
        f"// {banner_title} - upgrade assessment script\n"
        f"// Run via Control Panel > Server Administration > Script (language: Groovy).\n"
        f"// Output is CSV: \"Key\", \"Value\", \"Description\" (same format as the other version scripts).\n\n")
    out = banner + body.replace('@@TARGET_RELEASE@@', target).replace('@@TARGET_LTS@@', 'true' if lts else 'false').replace('@@FILE_NAME@@', fname)
    assert '@@' not in out
    os.makedirs(os.path.join(repo, folder), exist_ok=True)
    open(os.path.join(repo, folder, fname), 'w').write(out)
    print('wrote', folder + '/' + fname)

build('7.4', 'dxp_7_4', False, 'Liferay DXP 7.4 (GA1 - Update 92); for Quarterly Releases (2023.Q3+) use the matching dxp_YYYY_qN script', False)
for r in quarterlies:
    lts = r in LTS
    build(r, 'dxp_' + r.lower().replace('.', '_'), lts, f"Liferay DXP {r}{' LTS' if lts else ''} (any {r}.x patch release)", True)
