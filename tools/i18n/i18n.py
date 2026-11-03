"""Translations of the web page (index.html).

  python3 tools/i18n/i18n.py mark    tags every translatable block of index.html with data-i18n="<id>" (the id is a
                                      hash of the English HTML, so it changes when the English text changes) and
                                      writes tools/i18n/en.json with all English strings (blocks, attributes, JS texts)
  python3 tools/i18n/i18n.py build   checks tools/i18n/<lang>.json against en.json (missing / stale entries, changed
                                      HTML tags, ids or placeholders) and writes i18n.js, which the page loads

Run both after changing any text of index.html; then translate the entries reported as missing.
"""
import hashlib, html.parser, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HERE = os.path.join(ROOT, 'tools', 'i18n')
PAGE = os.path.join(ROOT, 'index.html')
LANGS = ['de', 'es', 'fr', 'it', 'pt', 'ja']
BLOCK = {'div', 'p', 'ol', 'ul', 'li', 'table', 'thead', 'tbody', 'tr', 'section', 'h1', 'h2', 'h3', 'select',
         'details', 'nav', 'header', 'main', 'footer', 'textarea'}
MARKABLE = {'h1', 'h2', 'h3', 'p', 'li', 'th', 'td', 'summary', 'label', 'button', 'option', 'a', 'span', 'div', 'footer'}
ATTRS = ('title', 'aria-label', 'placeholder')
VOID = {'br', 'input', 'img', 'meta', 'link', 'hr'}


def sid(text, prefix=''):
    return prefix + hashlib.sha1(text.encode()).hexdigest()[:8]


class Tree(html.parser.HTMLParser):
    def __init__(self, src):
        super().__init__(convert_charrefs=False)
        self.src, self.lines = src, src.split('\n')
        self.offsets = [0]
        for line in self.lines:
            self.offsets.append(self.offsets[-1] + len(line) + 1)
        self.stack, self.nodes, self.in_body, self.skip = [], [], False, 0

    def pos(self):
        line, col = self.getpos()
        return self.offsets[line - 1] + col

    def handle_starttag(self, tag, attrs):
        if tag == 'body':
            self.in_body = True
            return
        if not self.in_body:
            return
        start = self.pos()
        end_of_tag = self.src.index('>', start) + 1
        node = {'tag': tag, 'attrs': dict(attrs), 'start': start, 'inner': end_of_tag, 'children': [], 'text': '',
                'parent': self.stack[-1] if self.stack else None}
        if node['parent']:
            node['parent']['children'].append(node)
        self.nodes.append(node)
        if tag in ('script', 'style'):
            self.skip += 1
        if tag not in VOID:
            self.stack.append(node)

    def handle_endtag(self, tag):
        if not self.in_body or tag == 'body':
            return
        while self.stack:
            node = self.stack.pop()
            if node['tag'] in ('script', 'style'):
                self.skip -= 1
            if node['tag'] == tag:
                node['end'] = self.pos()
                break

    def handle_data(self, data):
        if self.in_body and self.stack and not self.skip:
            self.stack[-1]['text'] += data


def text_outside_code(node):
    text = node['text']
    for child in node['children']:
        if child['tag'] not in ('code', 'kbd', 'script', 'style'):
            text += text_outside_code(child)
    return text


def has_block(node):
    return any(c['tag'] in BLOCK or has_block(c) for c in node['children'])


def strip_marks(s):
    return re.sub(r' data-i18n(?:-attr)?="[^"]*"', '', s)


def mark():
    src = strip_marks(open(PAGE).read())
    tree = Tree(src)
    tree.feed(src)
    edits, strings = [], {}

    def walk(node, inside):
        marked = False
        if not inside and node['tag'] in MARKABLE and 'end' in node and not has_block(node) \
                and re.search(r'[A-Za-z]{2}', text_outside_code(node)) and 'data-noi18n' not in node['attrs'] \
                and not any('data-noi18n' in a for a in ancestors(node)):
            inner = src[node['inner']:node['end']]
            key = sid(' '.join(inner.split()))
            strings[key] = ' '.join(inner.split())
            edits.append((node['inner'] - 1, f' data-i18n="{key}"'))
            marked = True
        attr_marks = []
        for name in ATTRS:
            value = node['attrs'].get(name)
            if value and re.search(r'[A-Za-z]{2}', value) and not any('data-noi18n' in a for a in ancestors(node) + [node]):
                key = sid(value, 'a')
                strings[key] = value
                attr_marks.append(f'{name}:{key}')
        if attr_marks:
            edits.append((node['inner'] - 1, f' data-i18n-attr="{";".join(attr_marks)}"'))
        for child in node['children']:
            walk(child, inside or marked)

    def ancestors(node):
        out, p = [], node['parent']
        while p:
            out.append(p['attrs'])
            p = p['parent']
        return out

    for node in tree.nodes:
        if node['parent'] is None:
            walk(node, False)
    # end of the start tag: insert before '>' (or before '/>' )
    out = src
    for at, text in sorted(edits, key=lambda e: -e[0]):
        if out[at - 1] == '/':
            at -= 1
        out = out[:at] + text + out[at:]
    open(PAGE, 'w').write(out)

    head = out[:out.index('<body')]
    strings['meta.title'] = re.search(r'<title>(.*?)</title>', head).group(1)
    strings['meta.description'] = re.search(r'<meta name="description" content="([^"]*)"', head).group(1)
    for key, text in re.findall(r"\bt\('([a-z0-9_.]+)', '((?:[^'\\]|\\.)*)'", out):
        strings[key] = text.replace("\\'", "'").replace('\\n', '\n')
    json.dump(strings, open(os.path.join(HERE, 'en.json'), 'w'), ensure_ascii=False, indent=1, sort_keys=True)
    print(f'{len(strings)} English strings ({sum(1 for k in strings if not k.startswith(("a", "js.", "meta.")))} blocks)')


def skeleton(s):
    tags = re.findall(r'<(/?[a-z0-9]+)((?:\s+(?:id|href|for|class|aria-controls|type)="[^"]*")*)', s)
    return sorted((t, ' '.join(sorted(re.findall(r'(?:id|href|for)="[^"]*"', a)))) for t, a in tags), \
        sorted(re.findall(r'\{[a-z]+\}', s))


def build():
    en = json.load(open(os.path.join(HERE, 'en.json')))
    bundle, problems = {}, []
    for lang in LANGS:
        path = os.path.join(HERE, lang + '.json')
        tr = json.load(open(path)) if os.path.exists(path) else {}
        missing = [k for k in en if k not in tr]
        stale = [k for k in tr if k not in en]
        for k in en:
            if k in tr and skeleton(en[k]) != skeleton(tr[k]):
                problems.append(f'{lang} {k}: HTML tags, ids, links or {{placeholders}} differ from English')
        print(f'{lang}: {len(en) - len(missing)}/{len(en)} translated, {len(missing)} missing, {len(stale)} stale')
        bundle[lang] = {k: v for k, v in tr.items() if k in en}
    for p in problems:
        print('PROBLEM', p)
    with open(os.path.join(ROOT, 'i18n.js'), 'w') as f:
        f.write('// Generated by tools/i18n/i18n.py build from tools/i18n/<lang>.json - do not edit\n')
        f.write('window.UAS_I18N = ' + json.dumps(bundle, ensure_ascii=False, indent=0, sort_keys=True) + ';\n')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(mark() if sys.argv[1:] == ['mark'] else build())
