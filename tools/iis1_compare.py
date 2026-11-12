"""Compares the crafting table and furnace recipes of IIS 2 with the dump of IIS 1 (reference/iis1_dump, D147).

For every item that exists in both packs it reports:
  A  IIS 2 crafts it on the table, IIS 1 did not (the recipe should be off or moved to a machine);
  B  IIS 1 crafted it, IIS 2 does not;
  C  both craft it, but the number made or the number of ingredients differs.
Run from the repository root after gen_content.py:   python -I tools/iis1_compare.py [crafting|furnace] [--all]
Items are matched by the quest mapper (tools/gen/quests.py) and, for vanilla ones, by the English name.
"""
import collections
import glob
import gzip
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'gen'))
import quests  # noqa: E402

DUMP = os.path.join(ROOT, 'reference', 'iis1_dump')
GEN = os.path.join(ROOT, 'src', 'generated', 'resources')
JAR = os.path.join(ROOT, 'build', 'moddev', 'artifacts', 'minecraft-patched-26.1.2.117-merged.jar')
# 1.7.10 English names that changed
NAME_FIX = {'Wooden Planks': 'Oak Planks', 'Oak Wood Planks': 'Oak Planks', 'Wood': 'Oak Log', 'Oak Wood': 'Oak Log', 'Wooden Door': 'Oak Door', 'Wooden Trapdoor': 'Oak Trapdoor',
            'Fence': 'Oak Fence', 'Oak Fence': 'Oak Fence', 'Fence Gate': 'Oak Fence Gate', 'Wooden Button': 'Oak Button', 'Wooden Pressure Plate': 'Oak Pressure Plate',
            'Sign': 'Oak Sign', 'Boat': 'Oak Boat', 'Bed': 'Red Bed', 'Wool': 'White Wool', 'Carpet': 'White Carpet', 'Stone Bricks': 'Stone Bricks', 'Workbench': 'Crafting Table',
            'Crafting Table': 'Crafting Table', 'Redstone': 'Redstone Dust', 'Slimeball': 'Slimeball', 'Bottle o\' Enchanting': 'Bottle o\' Enchanting', 'Rail': 'Rail',
            'Wooden Slab': 'Oak Slab', 'Oak Wood Slab': 'Oak Slab', 'Oak Wood Stairs': 'Oak Stairs', 'Nether Brick Fence': 'Nether Brick Fence', 'Clay': 'Clay Ball',
            'Clay Block': 'Clay', 'Hardened Clay': 'Terracotta', 'Bricks': 'Bricks', 'Brick': 'Brick', 'Glass Pane': 'Glass Pane', 'Lapis Lazuli': 'Lapis Lazuli',
            'Ink Sac': 'Ink Sac', 'Cocoa Beans': 'Cocoa Beans', 'Bone Meal': 'Bone Meal', 'Melon': 'Melon Slice', 'Glistering Melon': 'Glistering Melon Slice',
            'Golden Rail': 'Powered Rail', 'Sticky Piston': 'Sticky Piston', 'Redstone Repeater': 'Redstone Repeater', 'Redstone Comparator': 'Redstone Comparator',
            'Minecart with Chest': 'Minecart with Chest', 'Empty Map': 'Empty Map', 'Steak': 'Steak', 'Raw Beef': 'Raw Beef', 'Raw Fish': 'Raw Cod', 'Cooked Fish': 'Cooked Cod',
            'Moss Stone': 'Mossy Cobblestone', 'Block of Quartz': 'Block of Quartz', 'Pillar Quartz Block': 'Quartz Pillar', 'Chiseled Quartz Block': 'Chiseled Quartz Block',
            'Monster Spawner': 'Monster Spawner', 'Lily Pad': 'Lily Pad', 'Jack o\'Lantern': 'Jack o\'Lantern', 'Stone Slab': 'Smooth Stone Slab', 'Leather Tunic': 'Leather Tunic',
            'Chain Helmet': 'Chainmail Helmet', 'Chain Chestplate': 'Chainmail Chestplate', 'Chain Leggings': 'Chainmail Leggings', 'Chain Boots': 'Chainmail Boots',
            'Book and Quill': 'Book and Quill', 'Eye of Ender': 'Eye of Ender', 'Fire Charge': 'Fire Charge', 'Firework Rocket': 'Firework Rocket', 'Cobblestone Wall': 'Cobblestone Wall',
            'Weighted Pressure Plate (Light)': 'Light Weighted Pressure Plate', 'Weighted Pressure Plate (Heavy)': 'Heavy Weighted Pressure Plate', 'Daylight Sensor': 'Daylight Detector',
            'Stone Brick Stairs': 'Stone Brick Stairs', 'Nether Brick': 'Nether Bricks', 'Glowstone Dust': 'Glowstone Dust', 'Enchantment Table': 'Enchanting Table', 'Snow': 'Snow Block',
            'Snow Layer': 'Snow', 'Trapped Chest': 'Trapped Chest', 'Tripwire Hook': 'Tripwire Hook', 'Item Frame': 'Item Frame', 'Flower Pot': 'Flower Pot', 'Lead': 'Lead'}


def read(name):
    with gzip.open(os.path.join(DUMP, name + '.txt.gz'), 'rt', encoding='utf-8') as f:
        return [l.rstrip('\n').split('\t') for l in f]


class Compare:
    def __init__(self):
        self.mp = quests.Mapper()
        self.names = {r[0]: r[1] for r in read('names') if len(r) > 1}
        z = zipfile.ZipFile(JAR)
        self.z = z
        en = json.loads(z.read('assets/minecraft/lang/en_us.json'))
        self.by_en = {}
        for k, v in en.items():
            p = k.split('.')
            if len(p) == 3 and p[0] in ('item', 'block') and p[1] == 'minecraft':
                self.by_en.setdefault(v, 'minecraft:' + p[2])
        self.ore = {}
        for r in read('oredict'):
            self.ore[r[0]] = r[1:]
        self.cache = {}

    def item(self, s):
        """IIS 1 stack text -> (IIS 2 item id or None, count)."""
        m = re.match(r'([^@]+)@(-?\d+)(?:\*(\d+))?', s)
        if not m:
            return None, 1
        iid, meta, n = m.group(1), int(m.group(2)), int(m.group(3) or 1)
        key = (iid, meta)
        if key not in self.cache:
            r = self.mp.item(iid, meta)
            if r is None and iid.startswith('minecraft:'):
                dn = self.names.get(f'{iid}@{meta}', '')
                dn = NAME_FIX.get(dn, dn)
                r = self.by_en.get(dn)
                if r is None and meta == 32767:
                    dn = self.names.get(f'{iid}@0', '')
                    r = self.by_en.get(NAME_FIX.get(dn, dn))
            self.cache[key] = r
        return self.cache[key], n

    def ing(self, s):
        """Ingredient text -> a comparable token."""
        if s == 'null':
            return None
        if s.startswith('ore:'):
            return s
        if s.startswith('['):
            return 'list'
        r, _ = self.item(s)
        return r or s.split('*')[0]


def iis2_crafting():
    """output id -> list of (count, n_ingredients, source)."""
    out = collections.defaultdict(list)
    ovr = os.path.join(GEN, 'packs', 'iis_overrides', 'data', 'minecraft', 'recipe')
    over = {os.path.basename(f)[:-5]: json.load(open(f, encoding='utf-8')) for f in glob.glob(os.path.join(ovr, '*.json'))}

    def add(r, src):
        if r.get('type') not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
            return
        res = r.get('result', {})
        rid = res.get('id') if isinstance(res, dict) else res
        cnt = res.get('count', 1) if isinstance(res, dict) else 1
        if r['type'] == 'minecraft:crafting_shaped':
            n = sum(1 for row in r['pattern'] for ch in row if ch != ' ')
            tools = sum(1 for row in r['pattern'] for ch in row if ch != ' ' and str(r['key'].get(ch, '')).startswith('#iis:tools/'))
        else:
            n = len(r.get('ingredients', []))
            tools = sum(1 for i in r.get('ingredients', []) if str(i).startswith('#iis:tools/'))
        out[rid].append((cnt, n, tools, src))
    z = zipfile.ZipFile(JAR)
    for n in z.namelist():
        if n.startswith('data/minecraft/recipe/') and n.endswith('.json'):
            rid = n.split('/')[-1][:-5]
            if rid in over:
                continue
            try:
                add(json.loads(z.read(n)), 'vanilla:' + rid)
            except ValueError:
                pass
    for rid, r in over.items():
        if 'neoforge:conditions' not in r:
            add(r, 'override:' + rid)
    base = os.path.join(GEN, 'data', 'iis', 'recipe')
    for f in glob.glob(os.path.join(base, '**', '*.json'), recursive=True):
        add(json.load(open(f, encoding='utf-8')), 'iis:' + f[len(base) + 1:-5].replace(os.sep, '/'))
    return out


def main():
    full = '--all' in sys.argv
    c = Compare()
    two = iis2_crafting()
    one = collections.defaultdict(list)
    known = set()
    for r in read('crafting'):
        if len(r) < 5:
            continue
        kind, cls, outp, size, ins = r[0], r[1], r[2], r[3], r[4]
        iid, cnt = c.item(outp)
        if kind == 'other':
            if iid:
                one[iid].append((cnt, -1, cls.split('.')[-1]))
            continue
        parts = [x for x in re.split(r'\|(?![^\[]*\])', ins.strip('[]')) if x]
        toks = [c.ing(x) for x in parts]
        toks = [t for t in toks if t]
        for t in toks:
            if t and not t.startswith('ore:') and ':' in t:
                known.add(t)
        if iid:
            ntools = sum(1 for t in toks if t.startswith('ore:craftingTool'))
            one[iid].append((cnt, len(toks), ntools, ' '.join(sorted(collections.Counter(toks).elements()))[:150]))
    for lst in c.ore.values():
        for s in lst:
            r, _ = c.item(s)
            if r:
                known.add(r)
    known |= set(one)
    a, b, d = [], [], []
    for iid in sorted(set(two) | set(one)):
        if not (iid.startswith('minecraft:') or iid.startswith('iis:')):
            continue
        r2, r1 = two.get(iid, []), one.get(iid, [])
        if r2 and not r1:
            if iid in known:
                a.append((iid, r2))
        elif r1 and not r2:
            b.append((iid, r1))
        elif r1 and r2:
            m1, m2 = max(x[0] for x in r1), max(x[0] for x in r2)
            hand1 = min((x[2] for x in r1 if x[1] >= 0), default=0)
            hand2 = min(x[2] for x in r2)
            if ':tiny_dust_' in iid and (m1, m2) == (9, 18):   # D110: 18 tiny piles from two dusts instead of 9 from one
                continue
            if m1 != m2 or (hand1 == 0) != (hand2 == 0):
                d.append((iid, r1, r2))
    print(f'IIS 1 crafting recipes with a mapped output: {sum(len(v) for v in one.values())} for {len(one)} items; IIS 2: {sum(len(v) for v in two.values())} for {len(two)} items')
    lim = None if full else 400
    print(f'\n== A. Crafted on the table in IIS 2, not in IIS 1: {len(a)}')
    for iid, r2 in a[:lim]:
        print(f'  {iid}: ' + '; '.join(f'x{x[0]} [{x[3]}]' for x in r2[:3]))
    print(f'\n== B. Crafted in IIS 1, not in IIS 2: {len(b)}')
    for iid, r1 in b[:lim]:
        print(f'  {iid}: ' + ' || '.join(f'x{x[0]} <- {x[-1]}' for x in r1[:2]))
    print(f'\n== C. Different amount or tool use: {len(d)}')
    for iid, r1, r2 in d[:lim]:
        print(f'  {iid}: IIS1 ' + ' || '.join(f'x{x[0]} <- {x[-1]}' for x in r1[:3]) + '   ###   IIS2 ' + '; '.join(f'x{x[0]} n{x[1]} t{x[2]} [{x[3]}]' for x in r2[:3]))


if __name__ == '__main__':
    main()
