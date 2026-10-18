#!/usr/bin/env python3
"""Extract ore vein definitions from the IIS pack config (config/GregTech/WorldGeneration.cfg)
and resolve material IDs to names using the output of gt5_materials.py.

Usage: gt5_veins.py <WorldGeneration.cfg> <materials.json> <out_veins.json> [out_small_ores.json]
Values are the ones actually used by the IIS 1.19.1 pack (not GT5 defaults).
"""
import json, sys

lines = open(sys.argv[1], encoding='utf-8', errors='replace').read().split('\n')
mats = {m['id']: m['name'] for m in json.load(open(sys.argv[2], encoding='utf-8')) if isinstance(m['id'], int)}

stack, sections, enabled = [], {}, {}
for ln in lines:
    t = ln.strip()
    if t.endswith('{'): stack.append(t[:-1].strip().strip('"'))
    elif t == '}':
        if stack: stack.pop()
    elif '=' in t and stack:
        k, v = t.split('=', 1)
        key = k.split(':', 1)[1].rsplit('_', 1)[0].strip('"')
        path = '/'.join(stack)
        sections.setdefault(path, {})[key] = v
        if path == 'worldgen' and key.startswith('ore.mix.'):
            enabled[key[len('ore.mix.'):]] = (v == 'true')

def mat(i):
    i = int(i)
    return None if i < 0 else mats.get(i % 1000, f'#{i}')

veins = []
for path, d in sections.items():
    if not path.startswith('worldgen/ore/mix/'): continue
    name = path[len('worldgen/ore/mix/'):].replace('/', '.')
    if not enabled.get(name, False): continue
    veins.append({
        'name': name,
        'minY': int(d['MinHeight']), 'maxY': int(d['MaxHeight']),
        'weight': int(d['RandomWeight']), 'density': int(d['Density']), 'size': int(d['Size']),
        'primary': mat(d['OrePrimaryLayer']), 'secondary': mat(d['OreSecondaryLayer']),
        'between': mat(d['OreSporadiclyInbetween']), 'sporadic': mat(d['OreSporaticlyAround']),
        'dimensions': [k for k in ('Overworld', 'Nether', 'TheEnd', 'Moon', 'Mars', 'Asteroid') if d.get(k) == 'true'],
        'restrictBiome': d.get('RestrictToBiomeName', 'None'),
    })
veins.sort(key=lambda v: v['name'])
json.dump(veins, open(sys.argv[3], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(f'{len(veins)} enabled veins')

# Small ores (GT_Worldgen_GT_Ore_SmallPieces): ore.small.<name> {MinHeight, MaxHeight, Amount, Ore, dims}
small_enabled = {}
for path, d in sections.items():
    if path == 'worldgen':
        for k, v in d.items():
            if k.startswith('ore.small.'):
                small_enabled[k[len('ore.small.'):]] = (v == 'true')
small = []
for path, d in sections.items():
    if not path.startswith('worldgen/ore/small/'): continue
    name = path[len('worldgen/ore/small/'):].replace('/', '.')
    if not small_enabled.get(name, False) or 'Ore' not in d: continue
    small.append({'name': name, 'minY': int(d['MinHeight']), 'maxY': int(d['MaxHeight']), 'amount': int(d['Amount']),
                  'material': mat(d['Ore']),
                  'dimensions': [k for k in ('Overworld', 'Nether', 'TheEnd', 'Moon', 'Mars', 'Asteroid') if d.get(k) == 'true']})
small.sort(key=lambda v: v['name'])
if len(sys.argv) > 4:
    json.dump(small, open(sys.argv[4], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(f'{len(small)} enabled small ores')
