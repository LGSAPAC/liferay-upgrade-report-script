"""Adds the hosting model section ('1_hosting.groovy', hosting_section.groovy) to the version scripts (idempotent).
The section runs right after the infrastructure section.

usage: hosting.py <file> ...
"""
import os, re, sys

GEN = os.path.dirname(os.path.abspath(__file__))
SECTION = open(os.path.join(GEN, 'hosting_section.groovy')).read()
BEGIN, END = "// BEGIN included '1_hosting.groovy'", "// END included '1_hosting.groovy'\n"
ANCHOR = "// invoke the topic scripts' wrapper functions"


def apply(path):
    s = open(path).read()
    if BEGIN in s:
        a = s.index(BEGIN)
        b = s.index(END) + len(END) + 1
        s = s[:a] + SECTION + s[b:]
    else:
        assert s.count(ANCHOR) == 1, path
        s = s.replace(ANCHOR, SECTION + ANCHOR)
        m = re.search(r'^.*invoke__1_infra_groovy\(uasContext\).*\n', s, re.M)
        assert m, path
        call = 'invoke__1_hosting_groovy(uasContext)'
        if 'uasSectionTimings' in s:
            call = "uasTimed(uasSectionTimings, '1_hosting') { %s }" % call
        s = s[:m.end()] + call + '\n' + s[m.end():]
    open(path, 'w').write(s)
    print('hosting', path)


for p in sys.argv[1:]:
    apply(p)
