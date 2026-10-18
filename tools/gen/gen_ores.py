#!/usr/bin/env python3
"""Generate IIS ore content from reference data.

Inputs (repo-relative):
  reference/original/gt5_materials.json, reference/original/iis_ore_veins.json
  reference/original/gt5_lang_ru_materials.json   (Russian names extracted from GregTech.lang.ru)
  tools/gen/lang/*.json                           (hand-written base translations)
  tools/gen/textures/ore/*.png                    (GT5 ore overlay icons, per texture set)
Outputs:
  src/main/resources/iis/materials.json, src/main/resources/iis/veins.json  (read by the mod at startup)
  src/generated/resources/...                     (models, blockstates, item models, lang, loot, tags, worldgen)

Run from repo root: python3 -I tools/gen/gen_ores.py
"""
import json, os, re, shutil

ROOT = os.getcwd()
REF = os.path.join(ROOT, 'reference', 'original')
GEN = os.path.join(ROOT, 'src', 'generated', 'resources')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'iis'

# Host rocks: id prefix, vanilla base texture, Russian/English suffix, dimension, hardness bonus
HOSTS = {
    'stone':      dict(prefix='',           tex='minecraft:block/stone',      en='',               ru='',                     dim='overworld', extra=0.0, sound='stone'),
    'deepslate':  dict(prefix='deepslate_', tex='minecraft:block/deepslate',  en='Deepslate ',     ru=' (глубинный сланец)',  dim='overworld', extra=1.5, sound='deepslate'),
    'netherrack': dict(prefix='nether_',    tex='minecraft:block/netherrack', en='Nether ',        ru=' (незерак)',           dim='nether',    extra=0.0, sound='netherrack'),
    'end_stone':  dict(prefix='end_',       tex='minecraft:block/end_stone',  en='End ',           ru=' (эндерняк)',          dim='end',       extra=0.0, sound='stone'),
}
DIM_MAP = {'Overworld': 'overworld', 'Nether': 'nether', 'TheEnd': 'end'}  # Moon/Mars/Asteroid: later (space)

def snake(name):
    s = re.sub(r'(?<=[a-z0-9])([A-Z])', r'_\1', name)
    s = re.sub(r'(?<=[A-Z])([A-Z][a-z])', r'_\1', s)
    return s.lower()

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')

mats = {m['name']: m for m in json.load(open(os.path.join(REF, 'gt5_materials.json'), encoding='utf-8'))}
veins_src = json.load(open(os.path.join(REF, 'iis_ore_veins.json'), encoding='utf-8'))
ru_names = json.load(open(os.path.join(REF, 'gt5_lang_ru_materials.json'), encoding='utf-8'))
ru_ore_names = ru_names.pop('__ore_names__', {})

# --- veins (heights stretched per owner decision D11: y*1.6-64) ---
def stretch(y): return round(y * 1.6) - 64
veins, used = [], {}
for v in veins_src:
    dims = sorted({DIM_MAP[d] for d in v['dimensions'] if d in DIM_MAP})
    if not dims: continue
    layers = {k: v[k] for k in ('primary', 'secondary', 'between', 'sporadic')}
    for d in dims:
        for m in layers.values():
            if m: used.setdefault(m, set()).add(d)
    veins.append({'name': v['name'], 'minY': stretch(v['minY']), 'maxY': stretch(v['maxY']),
                  'legacyMinY': v['minY'], 'legacyMaxY': v['maxY'],
                  'weight': v['weight'], 'density': v['density'], 'size': v['size'],
                  **{k: (f'{NS}:{snake(m)}' if m else None) for k, m in layers.items()},
                  'dimensions': dims})

# --- materials ---
TEX_DIR = os.path.join(ROOT, 'tools', 'gen', 'textures', 'ore')
available_sets = {f[:-4] for f in os.listdir(TEX_DIR) if f.endswith('.png')}
materials = []
for name in sorted(used):
    m = mats[name]
    rgba = m.get('rgba', [255, 255, 255, 0])
    iset = str(m.get('iconSet', 'SET_DULL')).replace('SET_', '')
    if iset not in available_sets: iset = 'DULL'
    level = max(0, min(7, int(m.get('toolQuality', 1)) if isinstance(m.get('toolQuality'), int) else 1))
    hosts = ['stone', 'deepslate'] * ('overworld' in used[name]) + ['netherrack'] * ('nether' in used[name]) + ['end_stone'] * ('end' in used[name])
    materials.append({'id': snake(name), 'gtName': name, 'gtId': m['id'], 'en': m.get('localName') or name,
                      'ru': ru_names.get(name.lower()) or m.get('localName') or name,
                      'ruOre': ru_ore_names.get(str(m['id'])),
                      'color': (rgba[0] << 16) | (rgba[1] << 8) | rgba[2], 'iconSet': iset,
                      'harvestLevel': level, 'hosts': hosts})
write(os.path.join(RES, NS, 'materials.json'), materials)
write(os.path.join(RES, NS, 'veins.json'), veins)

# --- assets ---
if os.path.isdir(GEN): shutil.rmtree(GEN)
A, D = os.path.join(GEN, 'assets', NS), os.path.join(GEN, 'data')
for s in available_sets:
    os.makedirs(os.path.join(A, 'textures', 'block', 'ore'), exist_ok=True)
    shutil.copy(os.path.join(TEX_DIR, s + '.png'), os.path.join(A, 'textures', 'block', 'ore', s.lower() + '.png'))
write(os.path.join(A, 'models', 'block', 'ore_template.json'), {
    'parent': 'minecraft:block/block',
    'textures': {'particle': '#base'},
    'elements': [
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#base', 'cullface': f} for f in ('down', 'up', 'north', 'south', 'west', 'east')}},
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#overlay', 'cullface': f, 'tintindex': 0} for f in ('down', 'up', 'north', 'south', 'west', 'east')}},
    ]})

lang = {'en_us': {}, 'ru_ru': {}}
for code in lang:
    p = os.path.join(ROOT, 'tools', 'gen', 'lang', code + '.json')
    if os.path.exists(p): lang[code].update(json.load(open(p, encoding='utf-8')))
lang['en_us']['itemGroup.iis.ores'] = 'IIS 2.0: Ores'
lang['ru_ru']['itemGroup.iis.ores'] = 'ИИС 2.0: Руды'

tags = {'minecraft:mineable/pickaxe': [], 'minecraft:needs_stone_tool': [], 'minecraft:needs_iron_tool': [], 'minecraft:needs_diamond_tool': []}
ore_blocks = []
for m in materials:
    for h in m['hosts']:
        host = HOSTS[h]
        bid = f"{host['prefix']}ore_{m['id']}"
        ore_blocks.append(bid)
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': f'{NS}:block/ore_template',
              'textures': {'base': host['tex'], 'overlay': f"{NS}:block/ore/{m['iconSet'].lower()}"}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}',
              'tints': [{'type': 'minecraft:constant', 'value': m['color']}]}})
        lang['en_us'][f'block.{NS}.{bid}'] = f"{host['en']}{m['en']} Ore"
        lang['ru_ru'][f'block.{NS}.{bid}'] = f"{m['ruOre'] or 'Руда: ' + m['ru']}{host['ru']}"
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
        tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')
        lvl = m['harvestLevel']
        if lvl == 1: tags['minecraft:needs_stone_tool'].append(f'{NS}:{bid}')
        elif lvl == 2: tags['minecraft:needs_iron_tool'].append(f'{NS}:{bid}')
        elif lvl >= 3: tags['minecraft:needs_diamond_tool'].append(f'{NS}:{bid}')
for t, vals in tags.items():
    ns, path = t.split(':')
    write(os.path.join(D, ns, 'tags', 'block', path + '.json'), {'replace': False, 'values': vals})
for code, entries in lang.items():
    write(os.path.join(A, 'lang', code + '.json'), dict(sorted(entries.items())))

# --- worldgen: our vein feature + removal of vanilla ores (owner decision D12) ---
write(os.path.join(D, NS, 'worldgen', 'configured_feature', 'gt_ore_veins.json'), {'type': f'{NS}:gt_ore_veins', 'config': {}})
write(os.path.join(D, NS, 'worldgen', 'placed_feature', 'gt_ore_veins.json'), {'feature': f'{NS}:gt_ore_veins', 'placement': []})
BM = os.path.join(D, NS, 'neoforge', 'biome_modifier')
# A biome list may not mix tags, so one modifier per dimension tag.
for dim in ('overworld', 'nether', 'end'):
    write(os.path.join(BM, f'add_gt_ore_veins_{dim}.json'), {'type': 'neoforge:add_features',
          'biomes': f'#minecraft:is_{dim}', 'features': f'{NS}:gt_ore_veins', 'step': 'underground_ores'})
vanilla_ores = ['ore_coal_lower', 'ore_coal_upper', 'ore_copper', 'ore_copper_large', 'ore_diamond', 'ore_diamond_buried',
                'ore_diamond_large', 'ore_diamond_medium', 'ore_emerald', 'ore_gold', 'ore_gold_extra', 'ore_gold_lower',
                'ore_iron_middle', 'ore_iron_small', 'ore_iron_upper', 'ore_lapis', 'ore_lapis_buried', 'ore_redstone',
                'ore_redstone_lower', 'ore_gold_nether', 'ore_gold_deltas', 'ore_quartz_nether', 'ore_quartz_deltas']
for dim in ('overworld', 'nether'):
    write(os.path.join(BM, f'remove_vanilla_ores_{dim}.json'), {'type': 'neoforge:remove_features',
          'biomes': f'#minecraft:is_{dim}', 'features': [f'minecraft:{f}' for f in vanilla_ores], 'steps': 'underground_ores'})

# Large noise-based iron/copper veins of modern worldgen: disabled with the vanilla ores (D12).
for n in ('overworld', 'large_biomes', 'amplified'):
    ns_settings = json.load(open(os.path.join(ROOT, 'tools', 'gen', 'vanilla', n + '.json'), encoding='utf-8'))
    ns_settings['ore_veins_enabled'] = False
    write(os.path.join(D, 'minecraft', 'worldgen', 'noise_settings', n + '.json'), ns_settings)

print(f'{len(materials)} materials, {len(ore_blocks)} ore blocks, {len(veins)} veins')
