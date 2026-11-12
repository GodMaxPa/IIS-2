"""Crafting table recipes brought to the dump of the real IIS 1 pack (D147, reference/iis1_dump/crafting.txt.gz).

Found by `python tools/iis1_compare.py --all` (lists A, B, C):
- B, missing in IIS 2: hard hammer on crushed / purified / centrifuged ore -> impure dust / purified dust / dust; splitting of gems with a
  hammer (exquisite -> 2 flawless -> 2 gems -> 2 flawed -> 2 chipped); saw on a long rod -> 2 rods; rubber ring with a knife; energium dust;
  gunpowder from dusts; alloy dusts by hand (cobalt brass, stainless steel, brass, kanthal, osmiridium, and the "nerfed" small piles of invar,
  electrum, cupronickel, nichrome, brass from tetrahedrite); lead with a slimeball (2).
- A, IIS 2 crafted by hand what IIS 1 made only in machines: motors, pistons, pumps, conveyors, robot arms, emitters and sensors of MV-IV
  (assembler, circuit 5 — the recipes come with tools/gen/iis1import.py); tool heads and long rods of plastics and rubbers (extruder and
  fluid solidifier); bread (GT: only baked), quartz block (storage blocks only in the compressor), the vanilla bowl (x4; IIS 1: knife, 1).
- C, other amounts: wooden doors give 1 (GT recipe replacement), nichrome 15 small piles instead of 5 dusts.
Must run after iis1import (it checks which machine recipes exist before a table recipe is removed).
"""
import glob
import json
import os
import re
import zipfile

NS = 'iis'
MC = lambda x: f'minecraft:{x}'
# (output prefix, output material, count, {input material: count})
MIXES = [('dust', 'CobaltBrass', 9, {'Brass': 7, 'Aluminium': 1, 'Cobalt': 1}),
         ('dust', 'StainlessSteel', 9, {'Iron': 6, 'Nickel': 1, 'Manganese': 1, 'Chrome': 1}),
         ('dust', 'Brass', 3, {'Copper': 3, 'Zinc': 1}),
         ('small_dust', 'Brass', 9, {'Tetrahedrite': 3, 'Zinc': 1}),
         ('small_dust', 'Invar', 9, {'Iron': 2, 'Nickel': 1}),
         ('dust', 'Kanthal', 3, {'Iron': 1, 'Aluminium': 1, 'Chrome': 1}),
         ('small_dust', 'Electrum', 6, {'Gold': 1, 'Silver': 1}),
         ('small_dust', 'Cupronickel', 6, {'Copper': 1, 'Nickel': 1}),
         ('small_dust', 'Nichrome', 15, {'Nickel': 4, 'Chrome': 1}),
         ('dust', 'Osmiridium', 3, {'Iridium': 3, 'Osmium': 1})]
COMPONENTS = r'^lv/(electric_motor|electric_piston|electric_pump|conveyor_module|robot_arm|emitter|sensor)_(mv|hv|ev|iv)(_\w+)?$'
SOFT = ('Plastic', 'PolyvinylChloride', 'Rubber', 'Silicone', 'StyreneButadieneRubber')


def generate(g):
    D, OVR, write, shaped, shapeless, tag_of, ref = g['D'], g['OVR'], g['write'], g['shaped'], g['shapeless'], g['tag_of'], g['ref']
    recipes, names, mat_id = g['recipes'], g['names'], g['mat_id']
    stats = {}
    made = {}   # recipe outputs by hand so far: (item, count)
    for r in recipes.values():
        res = r.get('result')
        if isinstance(res, dict) and r.get('type', '').startswith('minecraft:crafting'):
            made.setdefault(res['id'], set()).add(res.get('count', 1))

    def drop(name):   # the recipe may already be on disk (gen_content writes the table recipes twice)
        recipes.pop(name, None)
        f = os.path.join(D, NS, 'recipe', name + '.json')
        if os.path.exists(f):
            os.remove(f)

    def bump(k):
        stats[k] = stats.get(k, 0) + 1

    # --- B: hammer on ores, gems, long rods ---------------------------------------------------------------------------------------
    for n in sorted(names):
        mid = mat_id(n)
        for src, dst in (('crushed', 'impure_dust'), ('purified', 'pure_dust'), ('centrifuged', 'dust')):
            s, d = ref(src, n), ref(dst, n)
            if s and d and s.startswith(NS + ':'):
                shaped(f'iis1p/hammer_{src}_{mid}', d, 1, ['h', 'X'], {'X': tag_of(src, n)})
                bump('hammer_ore')
        chain = [ref(p, n) for p in ('gem_exquisite', 'gem_flawless', 'gem', 'gem_flawed', 'gem_chipped')]
        tags = [tag_of(p, n) for p in ('gem_exquisite', 'gem_flawless', 'gem', 'gem_flawed', 'gem_chipped')]
        if all(chain[i] for i in (0, 1, 3, 4)) and chain[2]:
            for i in range(4):
                shaped(f'iis1p/split_{i}_{mid}', chain[i + 1], 2, ['h', 'X'], {'X': tags[i]})
                bump('gem_split')
        if ref('long_rod', n) and ref('rod', n):
            shaped(f'iis1p/saw_long_rod_{mid}', ref('rod', n), 2, ['s', 'X'], {'X': tag_of('long_rod', n)})
            bump('saw_long_rod')
    if ref('ring', 'Rubber') and ref('plate', 'Rubber'):
        shaped('iis1p/ring_rubber_knife', ref('ring', 'Rubber'), 1, ['k', 'X'], {'X': tag_of('plate', 'Rubber')})
    shapeless('iis1p/energium_dust', f'{NS}:energium_dust', 9, [tag_of('dust', 'Redstone')] * 5 + [tag_of('dust', 'Ruby')] * 4)
    for fuel in ('Coal', 'Charcoal', 'Carbon'):
        if ref('dust', fuel):
            shapeless(f'iis1p/gunpowder_{mat_id(fuel)}', MC('gunpowder'), 6,
                      [tag_of('dust', fuel)] * 3 + [tag_of('dust', 'Saltpeter')] * 2 + [tag_of('dust', 'Sulfur')])
    for prefix, m, cnt, ins in MIXES:
        o = ref(prefix, m)
        if o is None or not all(ref('dust', i) for i in ins):
            continue
        if cnt in made.get(o, ()) and prefix == 'dust':
            continue
        shapeless(f'iis1p/mix_{prefix}_{mat_id(m)}', o, cnt, [tag_of('dust', i) for i, k in ins.items() for _ in range(k)])
        bump('mix')
    drop('dust/mix_nichrome')   # IIS 1: 15 small piles (NerfDustCrafting), not 5 dusts
    if ref('dust', 'Wood') and ref('small_dust', 'Wood') and 4 not in made.get(ref('small_dust', 'Wood'), ()):
        shapeless('iis1p/small_dust_wood', ref('small_dust', 'Wood'), 4, [tag_of('dust', 'Wood')])
    shaped('iis1p/lead_slime', MC('lead'), 2, ['SS ', 'SR ', '  S'], {'S': MC('string'), 'R': '#c:slime_balls'}, 'equipment')

    # --- A: by hand in IIS 2, only in machines in IIS 1 ---------------------------------------------------------------------------------
    machine = set()   # everything the machines make
    for f in glob.glob(os.path.join(D, NS, 'recipe', 'processing', '**', '*.json'), recursive=True):
        try:
            for o in json.load(open(f, encoding='utf-8')).get('outputs', []):
                machine.add(o['id'])
        except ValueError:
            pass
    soft = {mat_id(n) for n in SOFT}
    assembled = set()
    for f in glob.glob(os.path.join(D, NS, 'recipe', 'processing', 'assembler', '*.json')):
        for o in json.load(open(f, encoding='utf-8')).get('outputs', []):
            assembled.add(o['id'])
    for name in sorted(recipes):
        r = recipes[name]
        res = r.get('result')
        if not isinstance(res, dict) or res['id'] not in machine:
            continue
        if re.match(COMPONENTS, name) and res['id'] in assembled:
            drop(name)
            bump('components_off')
        elif re.match(COMPONENTS, name):
            pass
        elif (name.startswith('tool_head/') or name.startswith('part/long_rod_')) and any(name.endswith('_' + s) for s in soft):
            drop(name)
            bump('soft_parts_off')
    # second pass over the lists (D147): only in machines in IIS 1 — reactor chamber (assembler, EV hull), glass tube (alloy smelter,
    # ball mould), frequency transmitter (assembler)
    for name in ('reactor/reactor_chamber', 'lv/glass_tube', 'monitor/frequency_transmitter'):
        r = recipes.get(name)
        if r and r['result']['id'] in machine:
            drop(name)
            bump('machine_only_off')
    # the transmitter keeps a recipe of its own with a fixed name (GameTest monitor_panel_reads): IIS 1 numbers, our copper cable
    write(os.path.join(D, NS, 'recipe', 'processing', 'assembler', 'frequency_transmitter.json'),
          {'type': f'{NS}:assembler', 'inputs': [{'ingredient': '#iis:cables/1x/copper', 'count': 1}, {'ingredient': '#c:circuits/basic', 'count': 1}],
           'outputs': [{'id': f'{NS}:frequency_transmitter', 'count': 1}], 'duration': 100, 'eut': 8})
    # paper ring: knife and 2 paper -> 1 (was saw and cardboard -> 2); small coil with a ferrite bolt gives 4; blaze powder with a mortar
    # (the vanilla recipe stays off); hydrated coal dust from coal dust and a bucket of water (the bucket comes back)
    if 'gt/ring_paper' in recipes:
        drop('gt/ring_paper')
        shaped('iis1p/ring_paper', f'{NS}:ring_paper', 1, ['PPk'], {'P': MC('paper')})
    if 'lv/coil_small' in recipes and ref('bolt', 'NickelZincFerrite'):
        shaped('iis1p/coil_small_ferrite', f'{NS}:coil_small', 4, ['WWW', 'WDW', 'WWW'],
               {'W': recipes['lv/coil_small']['key']['W'], 'D': tag_of('bolt', 'NickelZincFerrite')})
    shaped('iis1p/blaze_powder_mortar', MC('blaze_powder'), 2, ['X', 'm'], {'X': MC('blaze_rod')})
    if ref('dust', 'HydratedCoal'):
        shapeless('iis1p/hydrated_coal_8', ref('dust', 'HydratedCoal'), 8, [tag_of('dust', 'Coal')] * 8 + [MC('water_bucket')])
        shapeless('iis1p/hydrated_coal_1', ref('dust', 'HydratedCoal'), 1, [tag_of('dust', 'Coal'), MC('water_bucket')])
    never = {'neoforge:conditions': [{'type': 'neoforge:never'}]}
    for rid in ('bread', 'quartz_block', 'bowl'):
        write(os.path.join(OVR, 'data', 'minecraft', 'recipe', rid + '.json'), never)

    # --- C: wooden doors give 1 -----------------------------------------------------------------------------------------------------------
    jar = os.path.join(g['ROOT'], 'build', 'moddev', 'artifacts', 'minecraft-patched-26.1.2.117-merged.jar')
    if os.path.exists(jar):
        z = zipfile.ZipFile(jar)
        for n in z.namelist():
            m = re.match(r'data/minecraft/recipe/(\w+_door|\w+_fence)\.json$', n)
            if not m or m.group(1) in ('iron_door', 'copper_door', 'nether_brick_fence'):
                continue
            if m.group(1).endswith('_fence'):   # GT: a wooden fence recipe gives 2 (IIS 1: 6 sticks -> 2)
                r = json.loads(z.read(n))
                if r.get('type') == 'minecraft:crafting_shaped' and r['result'].get('count', 1) == 3:
                    r['result']['count'] = 2
                    write(os.path.join(OVR, 'data', 'minecraft', 'recipe', m.group(1) + '.json'), r)
                    bump('fences')
                continue
            r = json.loads(z.read(n))
            if r.get('type') == 'minecraft:crafting_shaped' and r['result'].get('count', 1) == 3:
                r['result']['count'] = 1
                write(os.path.join(OVR, 'data', 'minecraft', 'recipe', m.group(1) + '.json'), r)
                bump('doors')
    return stats
