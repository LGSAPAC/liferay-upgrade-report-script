#!/usr/bin/env python3
"""Writes the version in VERSION (repository root) into the given scripts.

Every script prints it as the "uas_version" key, so a report shows which script version produced it.
Usage: stamp_version.py <VERSION file> <script>...  (idempotent)
"""
import re
import sys

version = open(sys.argv[1], encoding="utf-8").read().strip()
if not re.fullmatch(r"\d+\.\d+\.\d+", version):
    sys.exit(f"VERSION must look like 1.2.3, found {version!r}")

pattern = re.compile(r'(projectVersion: """\\\n\s*)[^\n]*(\n)')
for path in sys.argv[2:]:
    text = open(path, encoding="utf-8").read()
    new, count = pattern.subn(lambda m: m.group(1) + version + m.group(2), text)
    if count != 1:
        sys.exit(f"{path}: expected one projectVersion block, found {count}")
    if new != text:
        open(path, "w", encoding="utf-8").write(new)
