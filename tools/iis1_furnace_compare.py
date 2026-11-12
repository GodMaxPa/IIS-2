"""Compares the furnace recipes of IIS 2 with the dump of IIS 1 (reference/iis1_dump/furnace.txt.gz, D147).

A: IIS 2 smelts it, IIS 1 did not; B: IIS 1 smelted it, IIS 2 does not; C: different result. With --write list B (both items exist in IIS 2)
goes to reference/iis1_furnace_import.json, which tools/gen/iis1import.py turns into smelting recipes.
Run from the repository root after gen_content.py:   python tools/iis1_furnace_compare.py [--write]
"""
import sys, json, glob, os, zipfile, collections
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__))))
import iis1_compare as ic
c = ic.Compare()
GEN = ic.GEN
tags = {}
def tag(name):
    if name in tags: return tags[name]
    tags[name] = out = set()
    ns, _, path = name.partition(':')
    for base in (os.path.join(GEN, 'data'), 'src/main/resources/data'):
        f = os.path.join(base, ns, 'tags', 'item', path + '.json')
        if os.path.exists(f):
            for v in json.load(open(f, encoding='utf-8')).get('values', []):
                v = v['id'] if isinstance(v, dict) else v
                out |= tag(v[1:]) if v.startswith('#') else {v}
    return out
def items(i):
    if isinstance(i, list): return set().union(*[items(x) for x in i]) if i else set()
    if isinstance(i, dict): i = i.get('item') or '#' + i.get('tag', '')
    return tag(i[1:]) if i.startswith('#') else {i}
two = {}
ovr = os.path.join(GEN, 'packs', 'iis_overrides', 'data', 'minecraft', 'recipe')
over = {os.path.basename(f)[:-5]: json.load(open(f, encoding='utf-8')) for f in glob.glob(os.path.join(ovr, '*.json'))}
def add(r, src):
    if r.get('type') != 'minecraft:smelting': return
    res = r['result']; rid = res['id'] if isinstance(res, dict) else res
    for i in items(r['ingredient']): two.setdefault(i, []).append((rid, res.get('count', 1) if isinstance(res, dict) else 1, src))
z = zipfile.ZipFile(ic.JAR)
for n in z.namelist():
    if n.startswith('data/minecraft/recipe/') and n.endswith('.json'):
        rid = n.split('/')[-1][:-5]
        if rid not in over: add(json.loads(z.read(n)), 'vanilla:' + rid)
for rid, r in over.items():
    if 'neoforge:conditions' not in r: add(r, 'override:' + rid)
base = os.path.join(GEN, 'data', 'iis', 'recipe')
for f in glob.glob(os.path.join(base, '**', '*.json'), recursive=True):
    if not os.path.basename(f).startswith('iis1f_'):
        add(json.load(open(f, encoding='utf-8')), 'iis:' + os.path.basename(f)[:-5])
one = {}
for r in ic.read('furnace'):
    if len(r) < 2: continue
    i, _ = c.item(r[0]); o, n = c.item(r[1])
    if i and o: one.setdefault(i, (o, n))
a = [(i, v) for i, v in sorted(two.items()) if i not in one and (i.startswith('iis:') or i.startswith('minecraft:'))]
b = [(i, v) for i, v in sorted(one.items()) if i not in two]
d = [(i, one[i], two[i]) for i in sorted(one) if i in two and all((x[0], x[1]) != one[i] for x in two[i])]
print('IIS1 mapped', len(one), 'IIS2 inputs', len(two))
print('== A only IIS 2:', len(a)); [print(' ', i, v[0]) for i, v in a]
print('== B only IIS 1:', len(b)); [print(' ', i, v) for i, v in b]
print('== C differ:', len(d)); [print(' ', i, x, y[:2]) for i, x, y in d]
if '--write' in sys.argv:
    rows = [[i, v[0], v[1]] for i, v in b if i.split(':')[0] in ('iis', 'minecraft') and v[0].split(':')[0] in ('iis', 'minecraft') and i != v[0]]
    json.dump(rows, open(os.path.join(ic.ROOT, 'reference', 'iis1_furnace_import.json'), 'w', encoding='utf-8'), indent=0)
    print('written', len(rows))
