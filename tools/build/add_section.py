"""Adds a self-contained topic section to the version scripts, called right after another topic (idempotent).

usage: add_section.py <section.groovy> <topic e.g. 1_compatibility> <run after topic e.g. 1_hosting> <file> ...
"""
import re, sys

section_file, topic, after, *files = sys.argv[1:]
SECTION = open(section_file).read()
BEGIN, END = f"// BEGIN included '{topic}.groovy'", f"// END included '{topic}.groovy'\n"
ANCHOR = "// invoke the topic scripts' wrapper functions"

for path in files:
    s = open(path).read()
    if BEGIN in s:
        a = s.index(BEGIN)
        b = s.index(END) + len(END) + 1
        s = s[:a] + SECTION + s[b:]
    else:
        assert s.count(ANCHOR) == 1, path
        s = s.replace(ANCHOR, SECTION + ANCHOR)
        m = re.search(r'^.*invoke__%s_groovy\(uasContext\).*\n' % re.escape(after), s, re.M)
        assert m, (path, after)
        call = f'invoke__{topic}_groovy(uasContext)'
        if 'uasSectionTimings' in s:
            call = f"uasTimed(uasSectionTimings, '{topic}') {{ {call} }}"
        s = s[:m.end()] + call + '\n' + s[m.end():]
    open(path, 'w').write(s)
    print(topic, path)
