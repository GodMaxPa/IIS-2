"""Survival reachability over the generated recipes: what can actually be made starting from the world (ores, vanilla items, oil and water).

Found the uninsulated platinum and tungsten cables that had closed the IV tier since D103 (D110). Run from the repository root after gen_content.py:
    python -I tools/check_reach.py item_id [item_id ...]     why these items can or cannot be made
    python -I tools/check_reach.py --all                     every machine, part and circuit nothing can make
"""
import glob, json, os, sys
R = 'src/generated/resources'
tags = {}
for root in (f'{R}/data/*/tags/item', f'{R}/data/*/tags/items', 'src/main/resources/data/*/tags/item'):
    for f in glob.glob(root + '/**/*.json', recursive=True):
        ns = f.replace('\\', '/').split('/data/')[1].split('/')[0]
        name = f.replace('\\', '/').split('/tags/item')[1].lstrip('s').lstrip('/')[:-5]
        vals = json.load(open(f, encoding='utf-8')).get('values', [])
        tags.setdefault(f'{ns}:{name}', []).extend(v if isinstance(v, str) else v['id'] for v in vals)


def expand(i):
    """Items of an ingredient (string, list of strings)."""
    if isinstance(i, list):
        out = []
        for x in i:
            out += expand(x)
        return out
    if isinstance(i, dict):
        if 'items' in i:
            return expand(i['items'])
        if 'item' in i:
            return [i['item']]
        if 'tag' in i:
            return expand('#' + i['tag'])
        return []
    if i.startswith('#'):
        t = i[1:]
        if t in tags:
            return expand(tags[t])
        if t.startswith('minecraft:') or t.startswith('c:'):
            return ['minecraft:__world__' + t]
        return ['MISSING_TAG:' + t]
    return [i]


recipes = []
for f in glob.glob(f'{R}/data/iis/recipe/**/*.json', recursive=True):
    d = json.load(open(f, encoding='utf-8'))
    rid = f.replace('\\', '/').split('/recipe/')[1][:-5]
    if rid.startswith('processing/unpackager/'):
        continue
    t = d.get('type', '')
    reqs, outs = [], []
    if 'key' in d:
        pat = ''.join(d['pattern'])
        for k, v in d['key'].items():
            if k in pat:
                reqs.append(expand(v))
        outs.append(d['result']['id'])
    elif 'ingredients' in d:
        for v in d['ingredients']:
            reqs.append(expand(v))
        outs.append(d['result']['id'])
    elif 'ingredient' in d and 'result' in d:
        reqs.append(expand(d['ingredient']))
        outs.append(d['result']['id'] if isinstance(d['result'], dict) else d['result'])
    elif t.startswith('iis:'):
        for x in d.get('inputs', []):
            reqs.append(expand(x['ingredient']))
        for x in d.get('keep', []):
            reqs.append(expand(x))
        for x in d.get('fluid_inputs', []):
            ing = x['ingredient']
            reqs.append(['F:' + (ing if isinstance(ing, str) else ing.get('fluid', str(ing)))])
        outs += [x['id'] for x in d.get('outputs', [])]
        # a capsule of gas is that gas: any canner pours it out and back (D114)
        outs += ['F:' + x['components']['iis:fluid_content']['fluid'] for x in d.get('outputs', []) if 'iis:fluid_content' in x.get('components', {})]
        outs += ['F:' + x['id'] for x in d.get('fluid_outputs', [])]
        if t == 'iis:assembly_line':
            pass
    else:
        continue
    # the machine must exist too: any tier that can take the voltage (a multiblock or bare map: its controller)
    if t.startswith('iis:'):
        m = t[4:]
        ctrl = {'blast': ['ebf'], 'vacuum_freezer': ['vacuum_freezer'], 'implosion': ['implosion_compressor'], 'pyrolyse': ['pyrolyse_oven'], 'distillation_tower': ['distillation_tower'],
                'cracking': ['oil_cracker'], 'assembly_line': ['assembly_line'], 'fusion': ['fusion_computer_luv'], 'primitive_blast': ['bricked_blast_furnace'], 'coke_oven': ['coke_oven']}.get(m)
        if ctrl is None:
            eut = d.get('eut', 0)
            ctrl = [f'{m}_{tn}' for tn, v in (('lv', 32), ('mv', 128), ('hv', 512), ('ev', 2048), ('iv', 8192), ('luv', 32768), ('zpm', 131072)) if eut <= v] + ([f'steam_{m}_bronze', f'steam_{m}_steel', f'{m}_bronze', f'{m}_steel'] if eut <= 32 else [])
        ctrl = ['iis:' + c for c in ctrl if os.path.exists(f'{R}/assets/iis/items/{c}.json')]
        if ctrl:
            reqs.append(ctrl)
        if d.get('cleanroom'):
            reqs.append(['iis:cleanroom'])
    recipes.append((rid, reqs, outs))
print(len(recipes), 'recipes')

have = set()
for f in glob.glob(f'{R}/assets/iis/items/*.json'):
    b = os.path.basename(f)[:-5]
    if b.startswith(('ore_', 'deepslate_ore_', 'netherrack_ore', 'endstone_ore', 'small_ore')) or '_ore_' in b:
        have.add('iis:' + b)
# what ore blocks drop in the world (small ores drop crushed ore, dust and gems)
for f in glob.glob(f'{R}/data/iis/loot_table/blocks/*.json'):
    b = os.path.basename(f)[:-5]
    if b.startswith(('ore_', 'deepslate_ore_', 'small_ore', 'deepslate_small', 'nether_', 'end_')) or '_ore_' in b:
        t = open(f, encoding='utf-8').read()
        for part in t.split('"name"')[1:]:
            have.add(part.split('"')[1])
FLUID_SEEDS = ['minecraft:water', 'minecraft:lava', 'iis:oil', 'iis:oilmedium', 'iis:oillight', 'iis:oilheavy', 'iis:oilextraheavy', 'iis:naturalgas', 'iis:gas', 'iis:creosote', 'iis:saltwater', 'iis:dirtywater', 'iis:muddywater', 'iis:steam']
for x in FLUID_SEEDS:
    have.add('F:' + x)
seeds = ['iis:rubber_log', 'iis:sticky_resin', 'iis:plant_ball']
have |= {'iis:moon_rock', 'iis:moon_dirt', 'iis:moon_turf'}   # mined on the Moon: the rocket itself is checked by rocket_t1 (D115)
# the NASA workbench is a menu, not a recipe (D115)
recipes.append(('space/rocket_t1', [['iis:nasa_workbench'], ['iis:nose_cone'], ['iis:heavy_plating'], ['iis:rocket_fins'], ['iis:rocket_engine_t1']], ['iis:rocket_t1']))
# --with=iis:dust_naquadah: pretend an item is available, to see what else a missing source hides (D112: naquadah waits for space, D17)
for a_ in [x for x in sys.argv[1:] if x.startswith('--with=')]:
    have.add(a_[7:])
    sys.argv.remove(a_)


def ok(item):
    return item.startswith('minecraft:') or item in have


changed = True
while changed:
    changed = False
    for rid, reqs, outs in recipes:
        if all(o in have for o in outs):
            continue
        if all(any(ok(a) for a in r) for r in reqs):
            for o in outs:
                if o not in have:
                    have.add(o)
                    changed = True

ALL = sys.argv[1:] == ['--all']
targets = [] if ALL else sys.argv[1:] or ['circuit_master', 'circuit_elite', 'data_stick', 'hull_iv', 'hull_luv', 'electric_motor_luv', 'electric_pump_luv', 'conveyor_module_luv', 'electric_piston_luv', 'robot_arm_luv',
                           'emitter_luv', 'sensor_luv', 'macerator_iv', 'macerator_luv', 'assembly_line', 'reinforced_glass', 'scanner_iv', 'hatch_data_access_ev', 'transformer_iv', 'cable_platinum_1x',
                           'cable_tungsten_1x', 'cable_vanadium_gallium_1x', 'cable_niobium_titanium_1x', 'cable_yttrium_barium_cuprate_1x', 'circuit_assembler_iv', 'ingot_hssg', 'ingot_yttrium_barium_cuprate',
                           'ingot_niobium_titanium', 'plate_epoxid', 'super_buffer_lv', 'field_generator_hv', 'field_generator_iv', 'pipe_small_high_pressure', 'carbon_fiber', 'fluid_filter', 'chip_nano_cpu', 'chip_nor', 'chip_pic',
                           'wafer_cpu', 'dust_yttrium', 'dust_barium', 'dust_niobium', 'dust_molybdenum', 'dust_rare_earth', 'foil_silicone', 'long_rod_neodymium_magnetic']
by_out = {}
for rid, reqs, outs in recipes:
    for o in outs:
        by_out.setdefault(o, []).append((rid, reqs))
seen = set()


def why(item, depth=0):
    if item in seen or depth > 7:
        return
    seen.add(item)
    rs = by_out.get(item)
    if not rs:
        print('  ' * depth + item, 'NO RECIPE')
        return
    rid, reqs = rs[0]
    miss = [r for r in reqs if not any(ok(a) for a in r)]
    print('  ' * depth + item, 'via', rid, 'missing', [m[0] if len(m) == 1 else (m[:3]) for m in miss][:5])
    for m in miss[:6]:
        for a in m[:1]:
            why(a, depth + 1)


for t in targets:
    i = 'iis:' + t
    if i in have:
        print('OK ', t)
    else:
        print('NO ', t)
        why(i, 1)

if ALL:
    FORMS = ('dust_', 'nugget_', 'ingot_', 'plate_', 'double_plate_', 'rod_', 'bolt_', 'screw_', 'ring_', 'foil_', 'gear_', 'small_gear_', 'rotor_', 'crushed_', 'purified_', 'centrifuged_', 'gem_', 'lens_', 'block_',
             'turbine', 'frame_', 'small_dust_', 'tiny_dust_', 'pure_dust_', 'impure_dust_', 'hot_ingot_', 'long_rod_', 'wire_', 'cable_', 'round_', 'pipe_', 'ore_', 'deepslate_', 'small_ore', 'fine_')
    ids = [os.path.basename(f)[:-5] for f in glob.glob(f'{R}/assets/iis/items/*.json')]
    no = sorted(i for i in ids if 'iis:' + i not in have)
    print(len(ids), 'items,', len(no), 'unreachable')
    print('machines, parts, circuits:', [i for i in no if not i.startswith(FORMS) and '_head' not in i])
    print('dusts:', [i[5:] for i in no if i.startswith('dust_')])
    print('cables:', [i for i in no if i.startswith('cable_') and i.endswith('_1x')])
