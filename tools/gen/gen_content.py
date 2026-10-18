#!/usr/bin/env python3
"""Generate IIS 2.0 game content from reference data of the original IIS (GT5-II + pack configs).

Inputs (repo-relative, see reference/README.md):
  reference/original/gt5_materials.json            materials from GT5-II code (id, colour, icon set, composition)
  reference/original/iis_material_properties.json  final material properties of the IIS pack (MaterialProperties.cfg)
  reference/original/iis_ore_veins.json            ore veins of the IIS pack (WorldGeneration.cfg)
  reference/original/gt5_lang_ru_materials.json    Russian names from the pack's GregTech.lang.ru
  tools/gen/lang/*.json                            hand-written base translations
  tools/gen/textures/                              GT5 icons (ore overlays, material item icons, tool icons)
  tools/gen/vanilla/*.json                         vanilla noise settings (to switch off noise ore veins)
Outputs:
  src/main/resources/iis/{materials,veins,items,tools}.json   read by the mod at startup
  src/generated/resources/...                                  assets and data (models, lang, loot, tags, recipes, worldgen)

Run from the repo root:  python3 -I tools/gen/gen_content.py
Rules follow GT5-II code; references to the original are given next to each rule.
"""
import json, os, re, shutil

ROOT = os.getcwd()
REF = os.path.join(ROOT, 'reference', 'original')
GEN = os.path.join(ROOT, 'src', 'generated', 'resources')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
TEX = os.path.join(ROOT, 'tools', 'gen', 'textures')
NS = 'iis'


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def snake(name):
    s = re.sub(r'(?<=[a-z0-9])([A-Z])', r'_\1', name)
    s = re.sub(r'(?<=[A-Z])([A-Z][a-z])', r'_\1', s)
    return s.lower()


# ----------------------------------------------------------------------------------------------------------------
# Source data
# ----------------------------------------------------------------------------------------------------------------
code = {m['name']: m for m in json.load(open(os.path.join(REF, 'gt5_materials.json'), encoding='utf-8'))}
props = json.load(open(os.path.join(REF, 'iis_material_properties.json'), encoding='utf-8'))
veins_src = json.load(open(os.path.join(REF, 'iis_ore_veins.json'), encoding='utf-8'))
small_src = json.load(open(os.path.join(REF, 'iis_small_ores.json'), encoding='utf-8'))
ru = json.load(open(os.path.join(REF, 'gt5_lang_ru_materials.json'), encoding='utf-8'))
ru_ore = ru.pop('__ore_names__', {})
ru_meta = ru.pop('__metaitems__', {})
ru_tools = ru.pop('__tools__', {})


def prop(name, key, default=None):
    return props.get(name.lower(), {}).get(key, default)


# Element masses for the tool handle rule (GT5 Materials: handle by mass), approximated from Element.java.
ELEMENTS = {}
for line in open(os.path.join(ROOT, 'reference', 'original', 'gt5_elements.txt'), encoding='utf-8'):
    sym, protons, neutrons = line.split()
    ELEMENTS[sym] = int(protons) + int(neutrons)


def mass(name, depth=0):
    m = code.get(name)
    if not m or depth > 8:
        return ELEMENTS['Tc']
    el = next((a.split('.')[1] for a in m.get('rawArgs', []) if a.startswith('Element.')), None)
    if el and el in ELEMENTS:
        return ELEMENTS[el]
    comp = m.get('composition') or []
    if not comp:
        return ELEMENTS['Tc']
    total = sum(a for _, a in comp)
    return sum(mass(c, depth + 1) * a for c, a in comp) // max(1, total)


def handle_material(name):
    # GT5-II Materials.java: Diamond -> Wood; mass > 2*Tc -> TungstenSteel; mass > Tc -> Steel; else Wood
    if name == 'Diamond':
        return 'Wood'
    m = mass(name)
    return 'TungstenSteel' if m > ELEMENTS['Tc'] * 2 else 'Steel' if m > ELEMENTS['Tc'] else 'Wood'


# ----------------------------------------------------------------------------------------------------------------
# Veins (owner decision D11: y * 1.6 - 64). Moon/Mars/asteroid veins wait for the space slice (D17).
# ----------------------------------------------------------------------------------------------------------------
DIM_MAP = {'Overworld': 'overworld', 'Nether': 'nether', 'TheEnd': 'end'}
LAYERS = ('primary', 'secondary', 'between', 'sporadic')
veins, ore_dims = [], {}
for v in veins_src:
    dims = sorted({DIM_MAP[d] for d in v['dimensions'] if d in DIM_MAP})
    if not dims:
        continue
    for d in dims:
        for k in LAYERS:
            if v[k]:
                ore_dims.setdefault(v[k], set()).add(d)
    veins.append({'name': v['name'], 'minY': round(v['minY'] * 1.6) - 64, 'maxY': round(v['maxY'] * 1.6) - 64,
                  'legacyMinY': v['minY'], 'legacyMaxY': v['maxY'], 'weight': v['weight'], 'density': v['density'],
                  'size': v['size'], **{k: (f'{NS}:{snake(v[k])}' if v[k] else None) for k in LAYERS}, 'dimensions': dims})

# Small ores (GT_Worldgen_GT_Ore_SmallPieces): same height stretch; heights clamped to the modern world
small_ores, small_dims = [], {}
for so in small_src:
    dims = sorted({DIM_MAP[d] for d in so['dimensions'] if d in DIM_MAP})
    if not dims or not so['material']:
        continue
    for d in dims:
        small_dims.setdefault(so['material'], set()).add(d)
    small_ores.append({'name': so['name'], 'minY': max(-64, round(so['minY'] * 1.6) - 64),
                       'maxY': min(319, round(so['maxY'] * 1.6) - 64), 'amount': so['amount'],
                       'material': f"{NS}:{snake(so['material'])}", 'dimensions': dims})

# ----------------------------------------------------------------------------------------------------------------
# Material set of this slice: ore materials, what they smelt into, bronze (alloy) and flint (mortar)
# ----------------------------------------------------------------------------------------------------------------
names = set(ore_dims) | set(small_dims) | {'Stone', 'Netherrack', 'Endstone'}  # host rock dusts drop from small ores
for n in list(names):
    ds = prop(n, 'MaterialDirectSmeltInto')
    if ds and ds in code:
        names.add(ds)
names |= {'Bronze', 'Flint', 'Wood'}

# Forms (GT5 OrePrefixes): name, generation bits, meta-item index (gt.metaitem.01/02.<index*1000+id>), EN template, tag
B_DUST, B_METAL, B_GEM, B_ORE, B_TOOL = 1, 2, 4, 8, 64
PREFIXES = [
    # id prefix      gt prefix            bits                     lang key          en template                tag
    ('crushed',       'crushed',          B_ORE,                   '01', 5,  'Crushed {} Ore',          'iis:crushed_ores'),
    ('purified',      'crushedPurified',  B_ORE,                   '01', 6,  'Purified {} Ore',         'iis:purified_ores'),
    ('impure_dust',   'dustImpure',       B_ORE,                   '01', 3,  'Impure Pile of {} Dust',  'iis:impure_dusts'),
    ('pure_dust',     'dustPure',         B_ORE,                   '01', 4,  'Purified Pile of {} Dust', 'iis:pure_dusts'),
    ('dust',          'dust',             B_DUST | B_METAL | B_GEM | B_ORE, '01', 2, '{} Dust',          'c:dusts'),
    ('small_dust',    'dustSmall',        B_DUST | B_METAL | B_GEM | B_ORE, '01', 1, 'Small Pile of {} Dust', 'iis:small_dusts'),
    ('tiny_dust',     'dustTiny',         B_DUST | B_METAL | B_GEM | B_ORE, '01', 0, 'Tiny Pile of {} Dust', 'iis:tiny_dusts'),
    ('ingot',         'ingot',            B_METAL,                 '01', 11, '{} Ingot',                'c:ingots'),
    ('nugget',        'nugget',           B_METAL,                 '01', 9,  '{} Nugget',               'c:nuggets'),
    ('gem',           'gem',              B_GEM,                   '01', 8,  '{}',                      'c:gems'),
    ('plate',         'plate',            B_METAL | B_GEM,         '01', 17, '{} Plate',                'c:plates'),
    ('rod',           'stick',            B_METAL | B_GEM,         '01', 23, '{} Rod',                  'c:rods'),
    ('bolt',          'bolt',             B_METAL | B_GEM,         '01', 26, '{} Bolt',                 'iis:bolts'),
    ('screw',         'screw',            B_METAL | B_GEM,         '01', 27, '{} Screw',                'iis:screws'),
    ('ring',          'ring',             B_METAL,                 '01', 28, '{} Ring',                 'iis:rings'),
]
HEADS = [
    # id prefix        gt prefix            meta index, en template
    ('sword_head',     'toolHeadSword',     0, '{} Sword Blade'),
    ('pickaxe_head',   'toolHeadPickaxe',   1, '{} Pickaxe Head'),
    ('shovel_head',    'toolHeadShovel',    2, '{} Shovel Head'),
    ('axe_head',       'toolHeadAxe',       3, '{} Axe Head'),
    ('hoe_head',       'toolHeadHoe',       4, '{} Hoe Head'),
    ('saw_head',       'toolHeadSaw',       7, '{} Saw Blade'),
]
# GT5 mNotGeneratedItems + modern vanilla items that take the place of GT items
VANILLA = {
    ('ingot', 'Iron'): 'minecraft:iron_ingot', ('nugget', 'Iron'): 'minecraft:iron_nugget',
    ('ingot', 'Gold'): 'minecraft:gold_ingot', ('nugget', 'Gold'): 'minecraft:gold_nugget',
    ('ingot', 'Copper'): 'minecraft:copper_ingot', ('nugget', 'Copper'): 'minecraft:copper_nugget',
    ('gem', 'Coal'): 'minecraft:coal', ('gem', 'Diamond'): 'minecraft:diamond', ('gem', 'Emerald'): 'minecraft:emerald',
    ('gem', 'Lapis'): 'minecraft:lapis_lazuli', ('gem', 'NetherQuartz'): 'minecraft:quartz',
    ('gem', 'Flint'): 'minecraft:flint', ('dust', 'Redstone'): 'minecraft:redstone', ('rod', 'Wood'): 'minecraft:stick',
}
NOT_GENERATED_MATERIALS = {'Wood'}  # wood forms (planks, sticks) are vanilla

# Tools (GT5 GT_Tool_*): damage per block / per crafting use in GT units (100 = 1 point of GT durability)
TOOLS = {
    #  id           GT tool id  block dmg craft dmg  durability mult  speed mult  head prefix       kind
    'sword':       dict(gt=0,  block=200, craft=100, mult=1.0, speed=1.0,  head='sword_head',   kind='sword'),
    'pickaxe':     dict(gt=2,  block=50,  craft=100, mult=1.0, speed=1.0,  head='pickaxe_head', kind='pickaxe'),
    'shovel':      dict(gt=4,  block=50,  craft=100, mult=1.0, speed=1.0,  head='shovel_head',  kind='shovel'),
    'axe':         dict(gt=6,  block=50,  craft=100, mult=1.0, speed=1.0,  head='axe_head',     kind='axe'),
    'hoe':         dict(gt=8,  block=50,  craft=100, mult=1.0, speed=1.0,  head='hoe_head',     kind='hoe'),
    'saw':         dict(gt=10, block=50,  craft=200, mult=1.0, speed=1.0,  head='saw_head',     kind='axe'),
    'hammer':      dict(gt=12, block=50,  craft=400, mult=1.0, speed=0.75, head=None,           kind='pickaxe'),
    'wrench':      dict(gt=16, block=50,  craft=800, mult=1.0, speed=1.0,  head=None,           kind='pickaxe'),
    'file':        dict(gt=18, block=50,  craft=400, mult=1.0, speed=1.0,  head=None,           kind=None),
    'screwdriver': dict(gt=22, block=200, craft=400, mult=1.0, speed=1.0,  head=None,           kind=None),
    'mortar':      dict(gt=24, block=50,  craft=400, mult=1.0, speed=1.0,  head=None,           kind=None),
    'knife':       dict(gt=34, block=100, craft=100, mult=1.0, speed=1.0,  head=None,           kind='sword'),
}
MORTAR_MATERIALS = ('Flint', 'Bronze', 'Iron')  # GT_MetaGenerated_Tool_01: flint/bronze/iron mortars (+steel later)
# Crafting characters of GT tools
TOOL_CHARS = {'h': 'hammer', 'f': 'file', 's': 'saw', 'm': 'mortar', 'w': 'wrench', 'd': 'screwdriver', 'k': 'knife'}


def has_form(prefix, name):
    """Does material have this form (GT OrePrefixes.doGenerateItem), regardless of whether it is vanilla."""
    bits = next((p[2] for p in PREFIXES if p[0] == prefix), 0)
    return (prop(name, 'MaterialTypes', 0) & bits) != 0


def tool_capable(name):
    return (prop(name, 'MaterialTypes', 0) & B_TOOL) != 0 and prop(name, 'ToolDurability', 0) > 0


def subtags(name):
    return set(prop(name, 'ListSubTags', []) or [])


def mat_id(name):
    return snake(name)


# Tool materials of this slice: tool-capable materials with a wooden handle (steel/tungstensteel rods come later)
tool_mats = sorted(n for n in names if tool_capable(n) and handle_material(n) == 'Wood'
                   and n not in NOT_GENERATED_MATERIALS and (has_form('ingot', n) or has_form('gem', n)))

# ----------------------------------------------------------------------------------------------------------------
# Items: material forms
# ----------------------------------------------------------------------------------------------------------------
items = []          # generated by us: {id, prefix, material}
item_ref = {}       # (prefix, material) -> item id (ours or vanilla)
for n in sorted(names):
    if n in NOT_GENERATED_MATERIALS:
        continue
    for prefix, gtp, bits, *_ in PREFIXES:
        if not has_form(prefix, n):
            continue
        if (prefix, n) in VANILLA:
            item_ref[(prefix, n)] = VANILLA[(prefix, n)]
            continue
        iid = f'{prefix}_{mat_id(n)}'
        items.append({'id': iid, 'prefix': prefix, 'material': mat_id(n)})
        item_ref[(prefix, n)] = f'{NS}:{iid}'
for n in tool_mats:
    for prefix, gtp, idx, en in HEADS:
        iid = f'{prefix}_{mat_id(n)}'
        items.append({'id': iid, 'prefix': prefix, 'material': mat_id(n)})
        item_ref[(prefix, n)] = f'{NS}:{iid}'
for k, v in VANILLA.items():
    item_ref.setdefault(k, v)


def ref(prefix, name):
    return item_ref.get((prefix, name))


def tag_of(prefix, name):
    t = next((p[6] for p in PREFIXES if p[0] == prefix), None)
    if t is None:
        t = f'iis:tool_heads/{prefix[:-5]}'
    return f'#{t}/{mat_id(name)}'


# ----------------------------------------------------------------------------------------------------------------
# Tools
# ----------------------------------------------------------------------------------------------------------------
tools = []
for tool, t in TOOLS.items():
    mats = MORTAR_MATERIALS if tool == 'mortar' else tool_mats
    for n in mats:
        if tool != 'mortar' and n == 'Flint':
            continue
        dur = prop(n, 'ToolDurability', 0)
        # GT: MaxDamage = 100 * durability * multiplier; block break costs 50 -> 1 point here = 50 GT units
        max_damage = int(100 * dur * t['mult']) // 50
        tools.append({'id': f'{tool}_{mat_id(n)}', 'tool': tool, 'material': mat_id(n), 'handle': mat_id(handle_material(n)),
                      'maxDamage': max_damage, 'blockDamage': max(1, t['block'] // 50), 'craftDamage': max(1, t['craft'] // 50),
                      'speed': round(prop(n, 'ToolSpeed', 1.0) * t['speed'], 3), 'quality': prop(n, 'ToolQuality', 0),
                      'kind': t['kind']})

# ----------------------------------------------------------------------------------------------------------------
# Materials file
# ----------------------------------------------------------------------------------------------------------------
TEX_ORE = os.path.join(TEX, 'ore')
ore_sets = {f[:-4] for f in os.listdir(TEX_ORE) if f.endswith('.png')}
materials = []
for n in sorted(names):
    m = code[n]
    rgba = m.get('rgba') or [255, 255, 255, 0]
    pr = props.get(n.lower(), {})
    if 'MatRGBA' in pr:  # pack config colour wins
        rgba = [int(x) for x in str(pr['MatRGBA']).split(',') if x][:4]
    iset = str(m.get('iconSet', 'SET_DULL')).replace('SET_', '')
    dims = ore_dims.get(n, set())
    hosts = (['stone', 'deepslate'] if 'overworld' in dims else []) + (['netherrack'] if 'nether' in dims else []) \
        + (['end_stone'] if 'end' in dims else [])
    sdims = small_dims.get(n, set())
    small_hosts = (['stone', 'deepslate'] if 'overworld' in sdims else []) + (['netherrack'] if 'nether' in sdims else []) \
        + (['end_stone'] if 'end' in sdims else [])
    formula = ru_meta.get(f"01.{2000 + m['id']}", {}).get('tooltip')
    ds = prop(n, 'MaterialDirectSmeltInto', n)
    materials.append({
        'id': mat_id(n), 'gtName': n, 'gtId': m['id'], 'en': m.get('localName') or n,
        'ru': ru.get(n.lower()) or m.get('localName') or n, 'ruOre': ru_ore.get(str(m['id'])),
        'color': (rgba[0] << 16) | (rgba[1] << 8) | rgba[2], 'iconSet': iset,
        'harvestLevel': max(0, min(7, int(prop(n, 'ToolQuality', 1)))), 'hosts': hosts, 'smallHosts': small_hosts,
        'ruSmallOre': ru_ore.get(str(16000 + m['id'])), 'formula': formula,
        'types': prop(n, 'MaterialTypes', 0), 'subTags': sorted(subtags(n)),
        'directSmelting': mat_id(ds) if ds in code else mat_id(n),
        'oreMultiplier': prop(n, 'OreMultiplier', 1), 'smeltingMultiplier': prop(n, 'OreSmeltingMultiplier', 1),
        'blastFurnaceRequired': bool(prop(n, 'BlastFurnaceRequired', False)), 'blastFurnaceTemp': prop(n, 'BlastFurnaceTemp', 0),
        'byProducts': [mat_id(b) for b in (prop(n, 'ListMaterialByProducts', []) or [])],
        'toolDurability': prop(n, 'ToolDurability', 0), 'toolSpeed': prop(n, 'ToolSpeed', 1.0), 'toolQuality': prop(n, 'ToolQuality', 0),
    })
mat_by_name = {m['gtName']: m for m in materials}
write(os.path.join(RES, NS, 'materials.json'), materials)
write(os.path.join(RES, NS, 'veins.json'), veins)
write(os.path.join(RES, NS, 'small_ores.json'), small_ores)
write(os.path.join(RES, NS, 'items.json'), items)
write(os.path.join(RES, NS, 'tools.json'), tools)
# every (form, material) -> item id, including vanilla items that take GT's place (used by code: hammer drops, cauldron)
write(os.path.join(RES, NS, 'forms.json'), {f'{p}/{mat_id(n)}': iid for (p, n), iid in sorted(item_ref.items())
                                            if n not in NOT_GENERATED_MATERIALS})

# ----------------------------------------------------------------------------------------------------------------
# Assets
# ----------------------------------------------------------------------------------------------------------------
if os.path.isdir(GEN):
    shutil.rmtree(GEN)
A, D = os.path.join(GEN, 'assets', NS), os.path.join(GEN, 'data')


def copy_tex(src, dst_rel):
    dst = os.path.join(A, 'textures', dst_rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy(src, dst)


lang = {'en_us': {}, 'ru_ru': {}}
for code_ in lang:
    p = os.path.join(ROOT, 'tools', 'gen', 'lang', code_ + '.json')
    if os.path.exists(p):
        lang[code_].update(json.load(open(p, encoding='utf-8')))

# --- ore blocks ---
for s in ore_sets:
    copy_tex(os.path.join(TEX_ORE, s + '.png'), f'block/ore/{s.lower()}.png')
write(os.path.join(A, 'models', 'block', 'ore_template.json'), {
    'parent': 'minecraft:block/block', 'textures': {'particle': '#base'},
    'elements': [
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#base', 'cullface': f} for f in ('down', 'up', 'north', 'south', 'west', 'east')}},
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#overlay', 'cullface': f, 'tintindex': 0} for f in ('down', 'up', 'north', 'south', 'west', 'east')}},
    ]})
HOSTS = {
    'stone':      dict(prefix='',           tex='minecraft:block/stone',      en='',           ru='',                    rich=False),
    'deepslate':  dict(prefix='deepslate_', tex='minecraft:block/deepslate',  en='Deepslate ', ru=' (глубинный сланец)', rich=False),
    'netherrack': dict(prefix='nether_',    tex='minecraft:block/netherrack', en='Nether ',    ru=' (незерак)',          rich=True),
    'end_stone':  dict(prefix='end_',       tex='minecraft:block/end_stone',  en='End ',       ru=' (эндерняк)',         rich=True),
}
block_tags = {'minecraft:mineable/pickaxe': [], 'minecraft:needs_stone_tool': [], 'minecraft:needs_iron_tool': [],
              'minecraft:needs_diamond_tool': [], 'iis:ores': []}
item_tags = {}


def add_item_tag(tag, value):
    item_tags.setdefault(tag, [])
    if value not in item_tags[tag]:
        item_tags[tag].append(value)


ore_blocks = []  # (block id, material name, host)
for m in materials:
    n = m['gtName']
    for h in m['hosts']:
        host = HOSTS[h]
        bid = f"{host['prefix']}ore_{m['id']}"
        ore_blocks.append((bid, n, h))
        iset = m['iconSet'] if m['iconSet'] in ore_sets else 'DULL'
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': f'{NS}:block/ore_template',
              'textures': {'base': host['tex'], 'overlay': f"{NS}:block/ore/{iset.lower()}"}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}',
              'tints': [{'type': 'minecraft:constant', 'value': m['color']}]}})
        lang['en_us'][f'block.{NS}.{bid}'] = f"{host['en']}{m['en']} Ore"
        lang['ru_ru'][f'block.{NS}.{bid}'] = f"{m['ruOre'] or 'Руда: ' + m['ru']}{host['ru']}"
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
        block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')
        block_tags['iis:ores'].append(f'{NS}:{bid}')
        add_item_tag(f'iis:ores/{m["id"]}', f'{NS}:{bid}')
        lvl = m['harvestLevel']
        if lvl == 1:
            block_tags['minecraft:needs_stone_tool'].append(f'{NS}:{bid}')
        elif lvl == 2:
            block_tags['minecraft:needs_iron_tool'].append(f'{NS}:{bid}')
        elif lvl >= 3:
            block_tags['minecraft:needs_diamond_tool'].append(f'{NS}:{bid}')

# --- small ores (GT_TileEntity_Ores meta >= 16000): random gems/crushed/impure dust, sometimes host rock dust ---
TEX_SMALL = os.path.join(TEX, 'ore_small')
small_sets = {f[:-4] for f in os.listdir(TEX_SMALL) if f.endswith('.png')}
for s_ in small_sets:
    copy_tex(os.path.join(TEX_SMALL, s_ + '.png'), f'block/ore_small/{s_.lower()}.png')
HOST_DUST = {'stone': 'Stone', 'deepslate': 'Stone', 'netherrack': 'Netherrack', 'end_stone': 'Endstone'}
block_tags['iis:small_ores'] = []
small_blocks = []
for m in materials:
    n = m['gtName']
    for h in m['smallHosts']:
        host = HOSTS[h]
        bid = f"{host['prefix']}small_ore_{m['id']}"
        small_blocks.append((bid, n, h))
        iset = m['iconSet'] if m['iconSet'] in small_sets else 'DULL'
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': f'{NS}:block/ore_template',
              'textures': {'base': host['tex'], 'overlay': f"{NS}:block/ore_small/{iset.lower()}"}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}',
              'tints': [{'type': 'minecraft:constant', 'value': m['color']}]}})
        lang['en_us'][f'block.{NS}.{bid}'] = f"Small {host['en']}{m['en']} Ore"
        lang['ru_ru'][f'block.{NS}.{bid}'] = f"{m['ruSmallOre'] or 'Вкрапление: ' + m['ru']}{host['ru']}"
        # GT selector: exquisite(1)/flawless(2)/gem(12) -> gem; flawed(5) -> crushed; crushed(10); chipped(5) -> impure; impure(10)
        weights = {}
        gem = item_ref.get(('gem', n))
        crushed = item_ref.get(('crushed', n))
        impure = item_ref.get(('impure_dust', n))
        if gem: weights[gem] = weights.get(gem, 0) + 15
        if crushed: weights[crushed] = weights.get(crushed, 0) + 15
        elif gem: weights[gem] += 5
        if impure: weights[impure] = weights.get(impure, 0) + 15
        elif gem: weights[gem] += 5
        pools = [{'rolls': max(1, prop(n, 'OreMultiplier', 1)), 'bonus_rolls': 0.0,
                  'entries': [{'type': 'minecraft:item', 'name': i, 'weight': w} for i, w in weights.items()]}]
        host_dust = item_ref.get(('dust', HOST_DUST[h]))
        if host_dust:
            pools.append({'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.3333}],
                          'entries': [{'type': 'minecraft:item', 'name': host_dust}]})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
              'pools': [dict(p, conditions=p.get('conditions', []) + [{'condition': 'minecraft:survives_explosion'}]) for p in pools]})
        block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')
        block_tags['iis:small_ores'].append(f'{NS}:{bid}')
        lvl = max(0, m['harvestLevel'] - 1)  # GT: small ores are one harvest level lower
        if lvl == 1:
            block_tags['minecraft:needs_stone_tool'].append(f'{NS}:{bid}')
        elif lvl == 2:
            block_tags['minecraft:needs_iron_tool'].append(f'{NS}:{bid}')
        elif lvl >= 3:
            block_tags['minecraft:needs_diamond_tool'].append(f'{NS}:{bid}')

# --- material items ---
ICON = os.path.join(TEX, 'materialicons')
used_icons = set()


def icon_for(iset, gtp):
    for s in (iset, 'DULL', 'NONE'):
        if os.path.exists(os.path.join(ICON, s, gtp + '.png')):
            return s
    return None


gtp_of = {p[0]: p[1] for p in PREFIXES} | {h[0]: h[1] for h in HEADS}
meta_of = {p[0]: (p[3], p[4]) for p in PREFIXES} | {h[0]: ('02', h[2]) for h in HEADS}
en_of = {p[0]: p[5] for p in PREFIXES} | {h[0]: h[3] for h in HEADS}
RU_FALLBACK = {'crushed': 'Измельчённая руда', 'purified': 'Промытая измельчённая руда', 'impure_dust': 'Грязная пыль',
               'pure_dust': 'Чистая пыль', 'dust': 'Пыль', 'small_dust': 'Маленькая кучка пыли', 'tiny_dust': 'Крошечная кучка пыли',
               'ingot': 'Слиток', 'nugget': 'Самородок', 'gem': 'Кристалл', 'plate': 'Пластина', 'rod': 'Стержень',
               'bolt': 'Болт', 'screw': 'Винт', 'ring': 'Кольцо', 'sword_head': 'Лезвие меча', 'pickaxe_head': 'Наконечник кирки',
               'shovel_head': 'Полотно лопаты', 'axe_head': 'Наконечник топора', 'hoe_head': 'Полотно мотыги', 'saw_head': 'Лезвие пилы'}
for it in items:
    n = next(k for k, v in mat_by_name.items() if v['id'] == it['material'])
    m = mat_by_name[n]
    gtp = gtp_of[it['prefix']]
    iset = icon_for(m['iconSet'], gtp)
    used_icons.add((iset, gtp))
    base = f'{NS}:item/materialicons/{iset.lower()}/{gtp.lower()}'
    write(os.path.join(A, 'models', 'item', it['id'] + '.json'), {'parent': 'minecraft:item/generated',
          'textures': {'layer0': base, 'layer1': base + '_overlay'}})
    write(os.path.join(A, 'items', it['id'] + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{it["id"]}',
          'tints': [{'type': 'minecraft:constant', 'value': m['color']}]}})
    lang['en_us'][f'item.{NS}.{it["id"]}'] = en_of[it['prefix']].format(m['en'])
    book, idx = meta_of[it['prefix']]
    ru_name = ru_meta.get(f'{book}.{idx * 1000 + m["gtId"]}', {}).get('name')
    lang['ru_ru'][f'item.{NS}.{it["id"]}'] = ru_name or f"{RU_FALLBACK[it['prefix']]} ({m['ru']})"
    if m['formula']:
        lang['en_us'][f'item.{NS}.{it["id"]}.formula'] = m['formula']
        lang['ru_ru'][f'item.{NS}.{it["id"]}.formula'] = m['formula']
for (iset, gtp) in used_icons:
    for suffix in ('', '_OVERLAY'):
        src = os.path.join(ICON, iset, gtp + suffix + '.png')
        if os.path.exists(src):
            copy_tex(src, f'item/materialicons/{iset.lower()}/{gtp.lower()}{suffix.lower()}.png')
        else:  # transparent overlay placeholder
            copy_tex(os.path.join(TEX, 'transparent.png'), f'item/materialicons/{iset.lower()}/{gtp.lower()}{suffix.lower()}.png')

# item tags: our items and the vanilla items that take GT's place
for (prefix, n), iid in item_ref.items():
    t = next((p[6] for p in PREFIXES if p[0] == prefix), None) or f'iis:tool_heads/{prefix[:-5]}'
    if n in NOT_GENERATED_MATERIALS:
        continue
    add_item_tag(f'{t}/{mat_id(n)}', iid)

# --- tools ---
TOOL_ICON = os.path.join(TEX, 'tools')
TOOL_LAYERS = {
    # tool: (head icon, handle icon); 'set:<prefix>' = material icon set texture, 'icon:<NAME>' = GT iconsets texture
    'pickaxe': ('set:toolHeadPickaxe', 'set:stick'), 'axe': ('set:toolHeadAxe', 'set:stick'),
    'shovel': ('set:toolHeadShovel', 'set:stick'), 'hoe': ('set:toolHeadHoe', 'set:stick'),
    'hammer': ('set:toolHeadHammer', 'set:stick'), 'sword': ('set:toolHeadSword', 'icon:HANDLE_SWORD'),
    'file': ('set:toolHeadFile', 'icon:HANDLE_FILE'), 'saw': ('set:toolHeadSaw', 'icon:HANDLE_SAW'),
    'screwdriver': ('set:toolHeadScrewdriver', 'icon:HANDLE_SCREWDRIVER'),
    'wrench': ('icon:WRENCH', None), 'mortar': ('icon:MORTAR', None), 'knife': ('icon:KNIFE', None),
}


def layer_tex(spec, mat):
    kind, name = spec.split(':')
    if kind == 'set':
        iset = icon_for(mat['iconSet'], name)
        src = os.path.join(ICON, iset, name + '.png')
        rel = f'item/materialicons/{iset.lower()}/{name.lower()}'
        ov = os.path.join(ICON, iset, name + '_OVERLAY.png')
    else:
        src = os.path.join(TOOL_ICON, name + '.png')
        rel = f'item/tools/{name.lower()}'
        ov = os.path.join(TOOL_ICON, name + '_OVERLAY.png')
    copy_tex(src, rel + '.png')
    copy_tex(ov if os.path.exists(ov) else os.path.join(TEX, 'transparent.png'), rel + '_overlay.png')
    return f'{NS}:{rel}'


wood = mat_by_name['Wood']
for t in tools:
    m = next(v for v in materials if v['id'] == t['material'])
    h = next(v for v in materials if v['id'] == t['handle'])
    head_spec, handle_spec = TOOL_LAYERS[t['tool']]
    textures, tints = {}, []
    if handle_spec:
        textures['layer0'] = layer_tex(handle_spec, h)
        textures['layer1'] = layer_tex(head_spec, m)
        textures['layer2'] = textures['layer1'] + '_overlay'
        tints = [{'type': 'minecraft:constant', 'value': h['color']}, {'type': 'minecraft:constant', 'value': m['color']}]
    else:
        textures['layer0'] = layer_tex(head_spec, m)
        textures['layer1'] = textures['layer0'] + '_overlay'
        tints = [{'type': 'minecraft:constant', 'value': m['color']}]
    write(os.path.join(A, 'models', 'item', t['id'] + '.json'), {'parent': 'minecraft:item/handheld', 'textures': textures})
    write(os.path.join(A, 'items', t['id'] + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{t["id"]}', 'tints': tints}})
    tool_ru = ru_tools.get(str(TOOLS[t['tool']]['gt']), t['tool'])
    lang['en_us'][f'item.{NS}.{t["id"]}'] = f"{m['en']} {t['tool'].capitalize()}"
    lang['ru_ru'][f'item.{NS}.{t["id"]}'] = f"{tool_ru} ({m['ru']})"
    add_item_tag(f'iis:tools/{t["tool"]}', f'{NS}:{t["id"]}')
    if t['kind'] in ('pickaxe', 'axe', 'shovel', 'hoe', 'sword'):
        add_item_tag({'pickaxe': 'minecraft:pickaxes', 'axe': 'minecraft:axes', 'shovel': 'minecraft:shovels',
                      'hoe': 'minecraft:hoes', 'sword': 'minecraft:swords'}[t['kind']], f'{NS}:{t["id"]}')

lang['en_us'].update({'itemGroup.iis.ores': 'IIS 2.0: Ores', 'itemGroup.iis.materials': 'IIS 2.0: Materials', 'itemGroup.iis.tools': 'IIS 2.0: Tools'})
lang['ru_ru'].update({'itemGroup.iis.ores': 'ИИС 2.0: Руды', 'itemGroup.iis.materials': 'ИИС 2.0: Материалы', 'itemGroup.iis.tools': 'ИИС 2.0: Инструменты'})

# ----------------------------------------------------------------------------------------------------------------
# Recipes
# ----------------------------------------------------------------------------------------------------------------
recipes = {}


def result(item, count=1):
    r = {'id': item}
    if count != 1:
        r['count'] = count
    return r


def add_recipe(name, obj):
    assert name not in recipes, name
    recipes[name] = obj


def shaped(name, out, count, pattern, key, category='misc'):
    # GT tool characters in patterns become tool tags; letters in key are explicit ingredients
    k = dict(key)
    for row in pattern:
        for ch in row:
            if ch in TOOL_CHARS and ch not in k:
                k[ch] = f'#iis:tools/{TOOL_CHARS[ch]}'
    add_recipe(name, {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern, 'key': k, 'result': result(out, count)})


def shapeless(name, out, count, ingredients, category='misc'):
    add_recipe(name, {'type': 'minecraft:crafting_shapeless', 'category': category, 'ingredients': ingredients, 'result': result(out, count)})


def smelting(name, ingredient, out, count=1, xp=0.1, time=200):
    add_recipe(name, {'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': ingredient, 'result': result(out, count),
                      'experience': xp, 'cookingtime': time})


def smelt_output(n, prefix_kind):
    """GT5 ProcessingOreSmelting: what crushed/dust of material n smelt into. Returns (item, count) or None."""
    st = subtags(n)
    if 'NO_SMELTING' in st:
        return None
    ds = prop(n, 'MaterialDirectSmeltInto', n)
    if prop(n, 'BlastFurnaceRequired', False) or prop(ds, 'BlastFurnaceRequired', False):
        return None  # electric blast furnace (later slice)
    mixed_two_thirds = True  # IIS GregTech.cfg: MixedOreOnlyYieldsTwoThirdsOfPureOre=true
    if prefix_kind == 'crushed':
        out_prefix, size = ('nugget', 10) if ds == n else (('nugget', 6) if mixed_two_thirds else ('ingot', 1))
    else:  # dust, impure, pure
        out_prefix, size = ('ingot', 1) if ds == n else (('nugget', 6) if mixed_two_thirds else ('ingot', 1))
    out = ref(out_prefix, ds)
    if out is None:
        out = ref('gem' if 'SMELTING_TO_GEM' in subtags(n) else 'ingot', ds)
        size = 1
    if out is None:
        return None
    return out, size


for n in sorted(names):
    if n in NOT_GENERATED_MATERIALS:
        continue
    mid = mat_id(n)
    st = subtags(n)
    ds = prop(n, 'MaterialDirectSmeltInto', n)

    # 1. Ore blocks in a furnace (GT5 ProcessingOre.registerStandardOreRecipes). Nether/End ores are "rich": x2.
    for bid, on, host in ore_blocks:
        if on != n:
            continue
        mult = (2 if HOSTS[host]['rich'] else 1)
        if prop(n, 'BlastFurnaceRequired', False) or prop(ds, 'BlastFurnaceRequired', False):
            continue
        ingot = ref('ingot', ds)
        if ingot is not None:
            smelt_into = ingot
            if 'SMELTING_TO_GEM' in st:
                smelt_into = ref('gem', ds) or ref('gem', n) or ingot
            smelting(f'smelting/{bid}', f'{NS}:{bid}', smelt_into, mult * prop(n, 'OreSmeltingMultiplier', 1), xp=0.7)
        else:
            gem = ref('gem', ds)
            if gem is not None:
                smelting(f'smelting/{bid}', f'{NS}:{bid}', gem, max(1, mult * prop(n, 'OreSmeltingMultiplier', 1) // 2), xp=0.7)

    # 2. Crushed ores and dusts in a furnace (GT5 ProcessingOreSmelting)
    for prefix, kind in (('crushed', 'crushed'), ('purified', 'crushed'), ('dust', 'dust'), ('impure_dust', 'dust'), ('pure_dust', 'dust')):
        src = ref(prefix, n)
        if src is None or not src.startswith(NS + ':'):
            continue
        out = smelt_output(n, kind)
        if out:
            smelting(f'smelting/{prefix}_{mid}', tag_of(prefix, n), out[0], out[1])

    # 3. Mortar (GT5 ProcessingIngot/Plate/Gem, SubTag MORTAR_GRINDABLE): ingot/plate/gem -> dust
    dust = ref('dust', n)
    if 'MORTAR_GRINDABLE' in st and dust:
        for src_prefix in ('ingot', 'plate', 'gem'):
            if ref(src_prefix, n):
                shaped(f'mortar/{src_prefix}_{mid}', dust, 1, ['X', 'm'], {'X': tag_of(src_prefix, n)})

    # 4. Hammer: 2 ingots -> plate, gem -> plate (GT5 ProcessingPlate, not NO_SMASHING)
    plate = ref('plate', n)
    if plate and 'NO_SMASHING' not in st:
        if ref('ingot', n):
            shaped(f'hammer/plate_{mid}', plate, 1, ['h', 'X', 'X'], {'X': tag_of('ingot', n)})
        elif ref('gem', n):
            shaped(f'hammer/plate_{mid}', plate, 1, ['h', 'X'], {'X': tag_of('gem', n)})

    # 5. File: ingot -> rod; saw: rod -> 2 bolts; file: 2 bolts -> screw (GT5 ProcessingStick/Bolt/Screw)
    rod, bolt, screw = ref('rod', n), ref('bolt', n), ref('screw', n)
    if rod and rod.startswith(NS) and ref('ingot', n) and 'NO_WORKING' not in st:
        shaped(f'file/rod_{mid}', rod, 1, ['f ', ' X'], {'X': tag_of('ingot', n)})
    if bolt and rod:
        shaped(f'saw/bolt_{mid}', bolt, 2, ['s ', ' X'], {'X': tag_of('rod', n)})
    if screw and bolt:
        shaped(f'file/screw_{mid}', screw, 1, ['fX', 'X '], {'X': tag_of('bolt', n)})

    # 6. Dust piles (GT5 ProcessingDust): 4 small or 9 tiny -> 1 dust
    if dust and ref('small_dust', n):
        shapeless(f'dust/from_small_{mid}', dust, 1, [tag_of('small_dust', n)] * 4)
    if dust and ref('tiny_dust', n):
        shapeless(f'dust/from_tiny_{mid}', dust, 1, [tag_of('tiny_dust', n)] * 9)

# IIS NuggetsFix.zs: nugget + mortar -> tiny dust for common metals
for n in ('Gold', 'Silver', 'Tin', 'Copper', 'Iron', 'Lead'):
    if n in names and ref('nugget', n) and ref('tiny_dust', n):
        shaped(f'mortar/nugget_{mat_id(n)}', ref('tiny_dust', n), 1, ['X', 'm'], {'X': tag_of('nugget', n)})

# Alloy dusts by hand (GT_CraftingRecipeLoader, IIS: NerfDustCrafting=true)
shapeless('dust/bronze', ref('dust', 'Bronze'), 3, [tag_of('dust', 'Copper')] * 3 + [tag_of('dust', 'Tin')])
if ref('dust', 'Tetrahedrite'):
    shapeless('dust/bronze_from_tetrahedrite', ref('small_dust', 'Bronze'), 9, [tag_of('dust', 'Tetrahedrite')] * 3 + [tag_of('dust', 'Tin')])

# Tool heads and tools (GT5 ProcessingToolHead / ProcessingToolOther)
STICK = 'minecraft:stick'
for n in tool_mats:
    mid = mat_id(n)
    metal = ref('ingot', n) is not None
    P, I, G = tag_of('plate', n), (tag_of('ingot', n) if metal else None), (tag_of('gem', n) if ref('gem', n) else None)
    X = I or G
    has_plate = ref('plate', n) is not None
    if metal and has_plate:
        shaped(f'tool_head/pickaxe_{mid}', ref('pickaxe_head', n), 1, ['PII', 'f h'], {'P': P, 'I': I})
        shaped(f'tool_head/axe_{mid}', ref('axe_head', n), 1, ['PIh', 'P  ', 'f  '], {'P': P, 'I': I})
        shaped(f'tool_head/shovel_{mid}', ref('shovel_head', n), 1, ['fPh'], {'P': P})
        shaped(f'tool_head/sword_{mid}', ref('sword_head', n), 1, [' P ', 'fPh'], {'P': P})
        shaped(f'tool_head/hoe_{mid}', ref('hoe_head', n), 1, ['PIh', 'f  '], {'P': P, 'I': I})
        shaped(f'tool_head/saw_{mid}', ref('saw_head', n), 1, ['PP ', 'fh '], {'P': P})
    elif G:
        shaped(f'tool_head/pickaxe_{mid}', ref('pickaxe_head', n), 1, ['GGG', 'f  '], {'G': G})
        shaped(f'tool_head/axe_{mid}', ref('axe_head', n), 1, ['GG ', 'G  ', 'f  '], {'G': G})
        shaped(f'tool_head/shovel_{mid}', ref('shovel_head', n), 1, ['fG'], {'G': G})
        shaped(f'tool_head/sword_{mid}', ref('sword_head', n), 1, [' G', 'fG'], {'G': G})
        shaped(f'tool_head/hoe_{mid}', ref('hoe_head', n), 1, ['GG ', 'f  '], {'G': G})
        shaped(f'tool_head/saw_{mid}', ref('saw_head', n), 1, ['GGf'], {'G': G})
    for tool in ('pickaxe', 'axe', 'shovel', 'sword', 'hoe', 'saw'):
        shapeless(f'tool/{tool}_{mid}', f'{NS}:{tool}_{mid}', 1, [tag_of(f'{tool}_head', n), STICK], 'equipment')
    shaped(f'tool/hammer_{mid}', f'{NS}:hammer_{mid}', 1, ['XX ', 'XXS', 'XX '], {'X': X, 'S': STICK}, 'equipment')
    if has_plate:
        shaped(f'tool/file_{mid}', f'{NS}:file_{mid}', 1, ['P', 'P', 'S'], {'P': P, 'S': STICK}, 'equipment')
    if I:
        shaped(f'tool/wrench_{mid}', f'{NS}:wrench_{mid}', 1, ['IhI', 'III', ' I '], {'I': I}, 'equipment')
    if ref('rod', n):
        shaped(f'tool/screwdriver_{mid}', f'{NS}:screwdriver_{mid}', 1, [' fS', ' Sh', 'W  '], {'S': tag_of('rod', n), 'W': STICK}, 'equipment')
    if has_plate and ref('rod', n):
        shaped(f'tool/knife_{mid}', f'{NS}:knife_{mid}', 1, ['fPh', ' S '], {'P': P, 'S': tag_of('rod', n)}, 'equipment')
for n in MORTAR_MATERIALS:
    I = 'minecraft:flint' if n == 'Flint' else tag_of('ingot', n)
    shaped(f'tool/mortar_{mat_id(n)}', f'{NS}:mortar_{mat_id(n)}', 1, [' I ', 'SIS', 'SSS'], {'I': I, 'S': '#c:stones'}, 'equipment')

for name, r in recipes.items():
    write(os.path.join(D, NS, 'recipe', name + '.json'), r)

# IIS ToolFix.zs / IngotsFix.zs: vanilla metal tools and nuggets -> ingot are not craftable (modern copper tools and spears too)
DISABLED = [f'{m}_{t}' for m in ('iron', 'golden', 'diamond', 'copper') for t in ('pickaxe', 'axe', 'shovel', 'hoe', 'sword', 'spear')] \
    + ['iron_ingot_from_nuggets', 'gold_ingot_from_nuggets', 'copper_ingot_from_nuggets']
# Overrides of vanilla files live in a separate built-in data pack placed on TOP (registered by IISPacks):
# in the shared mod data pack the 'minecraft' mod's own files could win over ours.
OVR = os.path.join(GEN, 'packs', 'iis_overrides')
write(os.path.join(OVR, 'pack.mcmeta'), {'pack': {'description': 'IIS 2.0: overrides of vanilla data',
                                                  'min_format': [101, 1], 'max_format': [101, 1]}})
for r in DISABLED:
    write(os.path.join(OVR, 'data', 'minecraft', 'recipe', r + '.json'), {'neoforge:conditions': [{'type': 'neoforge:never'}]})

# ----------------------------------------------------------------------------------------------------------------
# Tags, lang
# ----------------------------------------------------------------------------------------------------------------
for t, vals in block_tags.items():
    ns, path = t.split(':')
    write(os.path.join(D, ns, 'tags', 'block', path + '.json'), {'replace': False, 'values': vals})
for t, vals in item_tags.items():
    ns, path = t.split(':')
    write(os.path.join(D, ns, 'tags', 'item', path + '.json'), {'replace': False, 'values': vals})
for code_, entries in lang.items():
    write(os.path.join(A, 'lang', code_ + '.json'), dict(sorted(entries.items())))

# ----------------------------------------------------------------------------------------------------------------
# Worldgen: our vein feature + removal of vanilla ores (owner decision D12)
# ----------------------------------------------------------------------------------------------------------------
write(os.path.join(D, NS, 'worldgen', 'configured_feature', 'gt_ore_veins.json'), {'type': f'{NS}:gt_ore_veins', 'config': {}})
write(os.path.join(D, NS, 'worldgen', 'placed_feature', 'gt_ore_veins.json'), {'feature': f'{NS}:gt_ore_veins', 'placement': []})
BM = os.path.join(D, NS, 'neoforge', 'biome_modifier')
for dim in ('overworld', 'nether', 'end'):  # a biome list may not mix tags: one modifier per dimension tag
    write(os.path.join(BM, f'add_gt_ore_veins_{dim}.json'), {'type': 'neoforge:add_features',
          'biomes': f'#minecraft:is_{dim}', 'features': f'{NS}:gt_ore_veins', 'step': 'underground_ores'})
VANILLA_ORES = ['ore_coal_lower', 'ore_coal_upper', 'ore_copper', 'ore_copper_large', 'ore_diamond', 'ore_diamond_buried',
                'ore_diamond_large', 'ore_diamond_medium', 'ore_emerald', 'ore_gold', 'ore_gold_extra', 'ore_gold_lower',
                'ore_iron_middle', 'ore_iron_small', 'ore_iron_upper', 'ore_lapis', 'ore_lapis_buried', 'ore_redstone',
                'ore_redstone_lower', 'ore_gold_nether', 'ore_gold_deltas', 'ore_quartz_nether', 'ore_quartz_deltas']
for dim in ('overworld', 'nether'):
    write(os.path.join(BM, f'remove_vanilla_ores_{dim}.json'), {'type': 'neoforge:remove_features',
          'biomes': f'#minecraft:is_{dim}', 'features': [f'minecraft:{f}' for f in VANILLA_ORES], 'steps': 'underground_ores'})
for n in ('overworld', 'large_biomes', 'amplified'):  # large noise-based iron/copper veins of modern worldgen
    s = json.load(open(os.path.join(ROOT, 'tools', 'gen', 'vanilla', n + '.json'), encoding='utf-8'))
    s['ore_veins_enabled'] = False
    write(os.path.join(OVR, 'data', 'minecraft', 'worldgen', 'noise_settings', n + '.json'), s)

print(f'{len(materials)} materials, {len(ore_blocks)} ore blocks, {len(small_blocks)} small ore blocks, {len(veins)} veins, {len(items)} items, '
      f'{len(tools)} tools ({", ".join(tool_mats)} + mortars), {len(recipes)} recipes')
