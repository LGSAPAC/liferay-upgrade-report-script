# usage: inject_section.py <section.groovy> <topic name e.g. 7_additional_info> <call to insert after> <file>...
import sys
section_file, topic, after, *files = sys.argv[1:]
section = open(section_file).read()
fn = f'invoke__{topic}_groovy'
begin, end_marker = f"// BEGIN included '{topic}.groovy'", f"// END included '{topic}.groovy'\n"
anchor = "// invoke the topic scripts' wrapper functions"
for path in files:
    s = open(path).read()
    if f'{fn}(uasContext)' in s:
        start = s.index(begin); end = s.index(end_marker) + len(end_marker) + 1
        s = s[:start] + section + s[end:]
    else:
        assert s.count(anchor) == 1, path
        s = s.replace(anchor, section + anchor)
        call = f'{after}(uasContext)\n'
        assert s.count(call) == 1, (path, call)
        s = s.replace(call, call + f'{fn}(uasContext)\n')
    open(path, 'w').write(s)
    print('ok', path)
