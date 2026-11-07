"""LV III: chemistry and melts (D76), called from lv.py with its recipe helpers.

* Electrolyzer / centrifuge decomposition of compound dusts with cells (ProcessingDust:61-110): components without a dust go
  into capsules (GT cells), the first fluid component becomes the fluid output, empty capsules are the second input.
* Fluid extractor: reverse fluid smelting of material forms (GT_RecipeRegistrator#registerReverseFluidSmelting):
  L·amount/M of molten metal, max(1, 24·amount/M) ticks, max(8, sqrt(2·T)) EU/t.
* Fluid solidifier: molds (ProcessingIngot:34, ProcessingPlate:36, ProcessingNugget:20, ProcessingGear:22, ProcessingBlock:36).
* Hand-written GT recipes of the chemical reactor, electrolyzer and fluid recipes of the centrifuge / mixer / solidifier /
  extractor, read from reference/original/gt5_machine_recipes.json (tools/gen/extract_gt5_recipes.py). A recipe is taken only
  when every item and fluid exists in IIS 2; expressions with loop variables are skipped.
GT cells are IIS capsules (iis:capsule with the iis:fluid_content component), 1 cell = 1000 mB.
"""
import importlib.util
import json
import math
import os
import re

NS = 'iis'
M = 3628800
L = 144
# GT OrePrefixes.mMaterialAmount for the forms we have (fractions of M)
AMOUNTS = {'ingot': (1, 1), 'dust': (1, 1), 'small_dust': (1, 4), 'tiny_dust': (1, 9), 'nugget': (1, 9), 'plate': (1, 1), 'rod': (1, 2),
           'bolt': (1, 8), 'screw': (1, 9), 'ring': (1, 4), 'foil': (1, 4), 'wire_fine': (1, 8), 'small_gear': (1, 1), 'gear': (4, 1),
           'long_rod': (1, 1), 'rotor': (17, 4)}
# GT prefix name -> our form prefix (gen_content PREFIXES)
GT_PREFIX = {'dust': 'dust', 'dustSmall': 'small_dust', 'dustTiny': 'tiny_dust', 'ingot': 'ingot', 'nugget': 'nugget', 'gem': 'gem', 'plate': 'plate',
             'stick': 'rod', 'bolt': 'bolt', 'screw': 'screw', 'ring': 'ring', 'foil': 'foil', 'wireFine': 'wire_fine', 'gearGtSmall': 'small_gear',
             'gearGt': 'gear', 'stickLong': 'long_rod', 'rotor': 'rotor', 'crushed': 'crushed', 'crushedPurified': 'purified',
             'dustImpure': 'impure_dust', 'dustPure': 'pure_dust'}
GET_FORM = {'getDust': 'dust', 'getDustSmall': 'small_dust', 'getDustTiny': 'tiny_dust', 'getIngots': 'ingot', 'getGems': 'gem',
            'getPlates': 'plate', 'getNuggets': 'nugget'}
VANILLA_ITEMS = {'paper': 'minecraft:paper', 'string': 'minecraft:string', 'sand': 'minecraft:sand', 'glass': 'minecraft:glass',
                 'gunpowder': 'minecraft:gunpowder', 'redstone': 'minecraft:redstone', 'sugar': 'minecraft:sugar', 'coal': 'minecraft:coal',
                 'bone': 'minecraft:bone', 'slime_ball': 'minecraft:slime_ball', 'glowstone_dust': 'minecraft:glowstone_dust',
                 'snowball': 'minecraft:snowball', 'ice': 'minecraft:ice', 'packed_ice': 'minecraft:packed_ice', 'obsidian': 'minecraft:obsidian',
                 'snow': 'minecraft:snow_block', 'stone': 'minecraft:stone', 'cobblestone': 'minecraft:cobblestone', 'gravel': 'minecraft:gravel',
                 'clay_ball': 'minecraft:clay_ball', 'wheat': 'minecraft:wheat', 'flint': 'minecraft:flint', 'rotten_flesh': 'minecraft:rotten_flesh',
                 'spider_eye': 'minecraft:spider_eye', 'fermented_spider_eye': 'minecraft:fermented_spider_eye', 'blaze_powder': 'minecraft:blaze_powder',
                 'netherrack': 'minecraft:netherrack', 'soul_sand': 'minecraft:soul_sand', 'melon': 'minecraft:melon_slice',
                 'pumpkin': 'minecraft:pumpkin', 'apple': 'minecraft:apple', 'carrot': 'minecraft:carrot', 'potato': 'minecraft:potato',
                 'reeds': 'minecraft:sugar_cane', 'egg': 'minecraft:egg', 'leather': 'minecraft:leather', 'feather': 'minecraft:feather'}

# 1.7.10 dye metadata 0..15 -> 26.1 colour names; coloured blocks -> 26.1 id suffix (stained_hardened_clay is terracotta now)
DYE_COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue',
              'brown', 'green', 'red', 'black']
COLORED = {'wool': 'wool', 'carpet': 'carpet', 'stained_hardened_clay': 'terracotta', 'stained_glass': 'stained_glass',
           'stained_glass_pane': 'stained_glass_pane'}
VANILLA_ITEMS.update({'hardened_clay': 'minecraft:terracotta', 'glass_pane': 'minecraft:glass_pane'})
# D130: what GT5-II squeezes and ferments; names of 1.7.10 where they changed
VANILLA_ITEMS.update({k: f'minecraft:{v}' for k, v in {
    'wheat_seeds': 'wheat_seeds', 'melon_seeds': 'melon_seeds', 'pumpkin_seeds': 'pumpkin_seeds', 'dirt': 'dirt', 'grass': 'grass_block', 'mycelium': 'mycelium', 'glowstone': 'glowstone',
    'brown_mushroom': 'brown_mushroom', 'red_mushroom': 'red_mushroom', 'brown_mushroom_block': 'brown_mushroom_block', 'red_mushroom_block': 'red_mushroom_block', 'melon_block': 'melon',
    'beef': 'beef', 'bread': 'bread', 'cake': 'cake', 'chicken': 'chicken', 'porkchop': 'porkchop', 'cooked_beef': 'cooked_beef', 'cooked_chicken': 'cooked_chicken', 'cooked_porkchop': 'cooked_porkchop',
    'cookie': 'cookie', 'mushroom_stew': 'mushroom_stew', 'nether_wart': 'nether_wart', 'golden_carrot': 'golden_carrot', 'speckled_melon': 'glistering_melon_slice', 'cooked_fished': 'cooked_cod'}.items()})
# item, meta -> modern item
VANILLA_META = {('fish', 0): 'minecraft:cod', ('fish', 1): 'minecraft:salmon', ('fish', 2): 'minecraft:tropical_fish', ('fish', 3): 'minecraft:pufferfish', ('fish', 32767): 'minecraft:cod',
                ('coal', 1): 'minecraft:charcoal', ('sand', 1): 'minecraft:red_sand', ('golden_apple', 0): 'minecraft:golden_apple', ('golden_apple', 1): 'minecraft:enchanted_golden_apple'}
# materials whose fluid has another id here
FLUID_ALIAS = {'AntiKnock': 'ethyltertbutylether', 'GasolineRegular': 'gasoline', 'GasolinePremium': 'highoctanegasoline'}


class Skip(Exception):
    pass


def chem_recipes(g, mrec, ing, out, ref, tag_of, prop, mat_id, names, WATER):
    ROOT = g['ROOT']
    code = g['code']
    # the fluid table itself (fluids.py writes iis/gt_fluids.json later in the same run, so the file may be stale here)
    spec = importlib.util.spec_from_file_location('iis_gen_fluids_table', os.path.join(ROOT, 'tools', 'gen', 'fluids.py'))
    fluids_mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fluids_mod)
    fluids = {e['id']: e for e, _, _ in fluids_mod.gt_fluid_table()}
    # GT material variable -> fluid by kind (GT_Proxy#addFluid keeps the material; e.g. HydricSulfide -> liquid_hydricsulfur,
    # HydrochloricAcid -> hydrochloricacid_gt5u)
    by_mat = {}
    for f in fluids.values():
        if f.get('material'):
            kind = {4: 'molten', 3: 'plasma', 2: 'gas'}.get(f['state'], 'fluid')
            by_mat.setdefault((f['material'], kind), f['id'])
    elements = {}
    for line in open(os.path.join(ROOT, 'reference', 'original', 'gt5_elements.txt'), encoding='utf-8'):
        sym, protons, _ = line.split()
        elements[sym] = int(protons)
    stats = {}

    # --- fluids and capsules --------------------------------------------------------------------------------------
    def fluid_id(material, kind='fluid'):
        if material == 'Water':
            return 'minecraft:water'
        if material == 'Lava':
            return 'minecraft:lava'
        if material in FLUID_ALIAS:
            return f'{NS}:{FLUID_ALIAS[material]}'
        if material == 'Creosote':
            return f'{NS}:creosote'   # Railcraft creosote is our own IISFluids fluid (D58), not a GT duplicate
        low = material.lower()
        cand = {'fluid': [low, f'liquid_{low}', f'solution_{low}'], 'gas': [low], 'molten': [f'molten_{low}'], 'plasma': [f'plasma_{low}']}[kind]
        for c in cand:
            if c in fluids:
                return f'{NS}:{c}'
        if (material, kind) in by_mat:
            return f"{NS}:{by_mat[(material, kind)]}"
        return None

    def named_fluid(name):
        low = name.lower().replace('.', '_')
        if low in ('water', 'ic2water'):
            return 'minecraft:water'
        snake_ = re.sub(r'(?<=[a-z0-9])([A-Z])', lambda m_: '_' + m_.group(1), name).lower()   # sRocketFuel -> rocket_fuel (D115)
        for c in (low, snake_, f'liquid_{low}', f'solution_{low}', low.removeprefix('liquid_')):
            if c in fluids:
                return f'{NS}:{c}'
        return None

    def capsule_in(fid=None):
        comp = {f'!{NS}:fluid_content': {}} if fid is None else {f'{NS}:fluid_content': {'fluid': fid, 'amount': 1000}}
        return {'neoforge:ingredient_type': 'neoforge:components', 'items': f'{NS}:capsule', 'components': comp}

    def capsule_out(fid, count):
        o = {'id': f'{NS}:capsule', 'count': count}
        if fid:
            o['components'] = {f'{NS}:fluid_content': {'fluid': fid, 'amount': 1000}}
        return o

    def protons(n, depth=0):
        m = code.get(n)
        if not m or depth > 8:
            return elements['Tc']
        el = next((a.split('.')[1] for a in m.get('rawArgs', []) if a.startswith('Element.')), None)
        if el and el in elements:
            return elements[el]
        comp = m.get('composition') or []
        if not comp:
            return elements['Tc']
        total = sum(a for _, a in comp)
        return sum(protons(c, depth + 1) * a for c, a in comp) // max(1, total)

    def extra_data(n):
        raw = (code.get(n) or {}).get('rawArgs') or []
        try:
            return int(raw[22])
        except (IndexError, ValueError):
            return 0

    # --- 1. decomposition with cells (ProcessingDust:61-110) ------------------------------------------------------------
    n_dec = 0
    for n in sorted(names):
        comp = (code.get(n) or {}).get('composition') or []
        dust = ref('dust', n)
        xd = extra_data(n)
        if not comp or not dust or not (xd & 3):
            continue
        dens = float(prop(n, 'Density', M) or M)
        mult = int(dens // M) if dens > M else 1
        item_amount, caps, lst, ok = 0, 0, [], True
        for c, a in comp:
            if a <= 0:
                continue
            if c == 'Air':
                st = ('cell', fluid_id('Air', 'gas'), a // 2)
            elif ref('dust', c):
                st = ('dust', ref('dust', c), a)
            else:
                fid = fluid_id(c, 'gas') or fluid_id(c, 'fluid')
                if not fid:
                    ok = False
                    break
                st = ('cell', fid, a)
            if item_amount + a * M <= 64 * dens:
                item_amount += a * M
                kind, what, cnt = st
                cnt *= mult
                if cnt > 0 and len(lst) < 6 and (kind != 'cell' or caps + cnt <= 64) and cnt <= 64:
                    if kind == 'cell':
                        caps += cnt
                    lst.append([kind, what, cnt])
        if not ok or not lst:
            continue
        item_amount = (1 if (item_amount * mult) % dens > 0 else 0) + int(item_amount * mult // dens)
        fout = None
        for i, (kind, what, cnt) in enumerate(lst):
            if kind == 'cell' and what and not what.endswith(':air'):
                fout = {'id': what, 'amount': 1000 * cnt}
                caps -= cnt
                lst.pop(i)
                break
        outs = [out(what, cnt) if kind == 'dust' else capsule_out(what, cnt) for kind, what, cnt in lst]
        inputs = [ing(tag_of('dust', n), item_amount)] + ([ing(capsule_in(), caps)] if caps > 0 else [])
        mid = mat_id(n)
        if xd & 1:
            mrec('electrolyzer', f'decompose_{mid}', inputs, outs, max(1, abs(protons(n) * 2 * item_amount)), min(4, len(lst)) * 30, fout=fout)
            n_dec += 1
        if xd & 2:
            mrec('centrifuge', f'decompose_{mid}', inputs, outs, max(1, abs(g['mass'](n) * 4 * item_amount)), min(4, len(lst)) * 5, fout=fout)
            n_dec += 1
    stats['decomposition'] = n_dec

    # --- 2. fluid extractor and solidifier for molten materials ------------------------------------------------------------
    n_fx = n_fs = 0
    for n in sorted(names):
        if n in g['NOT_GENERATED_MATERIALS'] or 'SMELTING_TO_FLUID' not in g['subtags'](n):
            continue
        into = prop(n, 'MaterialSmeltInto', n) or n
        fid = fluid_id(into, 'molten')
        if not fid:
            continue
        temp = fluids[fid.split(':')[1]]['temperature']
        eut = max(8, int(math.sqrt(2 * temp)))
        mid = mat_id(n)
        for pre, (num, den) in AMOUNTS.items():
            if not ref(pre, n):
                continue
            mb = L * num // den
            if mb <= 0:
                continue
            mrec('fluid_extractor', f'{pre}_{mid}', [ing(tag_of(pre, n))], [], max(1, 24 * num // den), eut, fout={'id': fid, 'amount': mb})
            n_fx += 1
        if into != n:
            continue
        for pre, mold, mb, dur, e in (('ingot', 'mold_ingot', 144, 32, 8), ('plate', 'mold_plate', 144, 32, 8), ('nugget', 'mold_nugget', 16, 16, 4),
                                      ('gear', 'mold_gear', 576, 128, 8)):
            if ref(pre, n):
                mrec('fluid_solidifier', f'{pre}_{mid}', [], [out(ref(pre, n))], dur, e, fin={'ingredient': fid, 'amount': mb}, keep=[f'{NS}:{mold}'])
                n_fs += 1
    for blk, n in (('minecraft:iron_block', 'Iron'), ('minecraft:gold_block', 'Gold'), ('minecraft:copper_block', 'Copper')):
        fid = fluid_id(n, 'molten')
        if fid:
            mrec('fluid_solidifier', f'block_{mat_id(n)}', [], [out(blk)], 288, 8, fin={'ingredient': fid, 'amount': 1296}, keep=[f'{NS}:mold_block'])
    stats['fluid_extractor'], stats['fluid_solidifier'] = n_fx, n_fs

    # --- 2b. chemical bath of crushed ore / impure dust (ProcessingDirty:26-35) ------------------------------------------
    # mercury or sodium persulfate, 1000 mB, 800 t, 8 EU/t; outputs purified (pure dust) 100 %, dust of the washed material
    # (own material or a by-product that carries the subtag) 70 %, stone dust 40 %. GT checks recipe collisions, so for the
    # same input and fluid the first recipe wins: own material first, then by-products in order.
    baths = (('WASHING_MERCURY', 'Mercury'), ('WASHING_SODIUMPERSULFATE', 'SodiumPersulfate'))
    n_bath, made_bath = 0, set()
    for n in sorted(names):
        bys = prop(n, 'ListMaterialByProducts', []) or []
        mid = mat_id(n)
        for src_p, dst_p in (('crushed', 'purified'), ('impure_dust', 'pure_dust')):
            if not ref(src_p, n) or not ref(dst_p, n):
                continue
            for washed in [n] + list(bys):
                st = g['subtags'](washed)
                into = prop(washed, 'MaterialMacerateInto', washed) or washed
                for tag, fluid in baths:
                    fid = fluid_id(fluid)
                    if tag not in st or not fid or not ref('dust', into):
                        continue
                    rid = f'{fluid.lower()}_{src_p}_{mid}'
                    if rid in made_bath:
                        continue
                    made_bath.add(rid)
                    mrec('chemical_bath', rid, [ing(tag_of(src_p, n))],
                         [out(ref(dst_p, n)), out(ref('dust', into), 1, 7000), out(ref('dust', 'Stone'), 1, 4000)], 800, 8,
                         fin={'ingredient': fid, 'amount': 1000})
                    n_bath += 1
    stats['chemical_bath_ore'] = n_bath

    # --- 3. hand-written GT recipes ---------------------------------------------------------------------------------------
    skipped_list = []
    calls = json.load(open(os.path.join(ROOT, 'reference', 'original', 'gt5_machine_recipes.json'), encoding='utf-8'))['calls']

    def expr(e):
        """('item', ingredient-or-id spec) / ('fluid', id, amount) / ('circuit', n) / ('none',) / ('int', v); Skip if unknown."""
        s = re.sub(r'\s+', '', e)
        s = re.sub(r',newObject\[0\]|,o\)|,newObject\[0\]\)', lambda m: ')' if m.group(0).endswith(')') else '', s)
        s = s.replace(',newObject[0])', ')')
        m = re.fullmatch(r'\(int\)\((\d+)L?\*3628800/3628800L?\)', s)
        if m:
            return ('int', int(m.group(1)))
        m = re.fullmatch(r'GT_OreDictUnificator\.getDust\(Materials\.(\w+),(\d+)L?\*3628800\)', s)
        if m:
            if not ref('dust', m.group(1)):
                raise Skip(e)
            return ('item', ('dust', m.group(1)), int(m.group(2)))
        if re.fullmatch(r'ItemList\.Cell_Air\.get\((\d+)L?\)', s):
            fid = fluid_id('Air', 'gas')
            if not fid:
                raise Skip(e)
            return ('cells', fid, int(re.search(r'\((\d+)', s).group(1)))
        if re.fullmatch(r'ItemList\.Cell_Water\.get\((\d+)L?\)', s):
            return ('cells', 'minecraft:water', int(re.search(r'\((\d+)', s).group(1)))
        # IIS 1: GregTech.cfg MoreComplicatedChemicalRecipes=true
        m = re.fullmatch(r'\(GT_Mod\.gregtechproxy\.mMoreComplicatedChemicalRecipes\?([\w.]+):[\w.]+\)(.*)', s)
        if m:
            s = m.group(1) + m.group(2)
        if s in ('GT_Values.NI', 'GT_Values.NF', 'null'):
            return ('none',)
        if s in ('true', 'false'):
            return ('bool', s == 'true')
        if re.fullmatch(r'-?\d+L?', s):
            return ('int', int(s.rstrip('L')))
        if re.fullmatch(r'[\d*+L()]+', s):
            return ('int', int(eval(s.replace('L', ''))))
        m = re.fullmatch(r'GT_Utility\.getIntegratedCircuit\((\d+)\)', s)
        if m:
            return ('circuit', int(m.group(1)))
        m = re.fullmatch(r'GT_OreDictUnificator\.get\(OrePrefixes\.(\w+),Materials\.(\w+),(\d+)L?\)', s)
        if m:
            pre, mat, cnt = m.group(1), m.group(2), int(m.group(3))
            if pre == 'cell':
                if mat == 'Empty':
                    return ('cells', None, cnt)
                fid = fluid_id(mat, 'fluid') or fluid_id(mat, 'gas')
                if not fid:
                    raise Skip(e)
                return ('cells', fid, cnt)
            ours = GT_PREFIX.get(pre)
            if not ours or not ref(ours, mat):
                raise Skip(e)
            return ('item', (ours, mat), cnt)
        m = re.fullmatch(r'Materials\.(\w+)\.(get\w+)\((\d+)L?\)', s)
        if m:
            mat, fn, cnt = m.group(1), m.group(2), int(m.group(3))
            if fn in ('getFluid', 'getGas', 'getMolten', 'getPlasma'):
                kind = {'getFluid': 'fluid', 'getGas': 'gas', 'getMolten': 'molten', 'getPlasma': 'plasma'}[fn]
                fid = fluid_id(mat, kind) or (fluid_id(mat, 'gas') if kind == 'fluid' else fluid_id(mat, 'fluid') if kind == 'gas' else None)
                if not fid:
                    raise Skip(e)
                return ('fluid', fid, cnt)
            if fn == 'getCells':
                if mat == 'Empty':
                    return ('cells', None, cnt)
                fid = fluid_id(mat, 'fluid') or fluid_id(mat, 'gas')
                if not fid:
                    raise Skip(e)
                return ('cells', fid, cnt)
            if fn in GET_FORM and ref(GET_FORM[fn], mat):
                return ('item', (GET_FORM[fn], mat), cnt)
            raise Skip(e)
        m = re.fullmatch(r'ItemList\.Cell_Empty\.get\((\d+)L?\)', s)
        if m:
            return ('cells', None, int(m.group(1)))
        m = re.fullmatch(r'GT_ModHandler\.getWater\((\d+)L?\)', s)
        if m:
            return ('fluid', 'minecraft:water', int(m.group(1)))
        m = re.fullmatch(r'GT_ModHandler\.getDistilledWater\((\d+)L?\)', s)
        if m:
            return ('fluid', f'{NS}:distilled_water', int(m.group(1)))
        m = re.fullmatch(r'ItemList\.Circuit_Integrated\.getWithDamage\(0L?,(\d+)L?\)', s)
        if m:
            return ('circuit', int(m.group(1)))
        m = re.fullmatch(r'newItemStack\(Blocks\.(wool|carpet|stained_hardened_clay|stained_glass|stained_glass_pane),(\d+),(\d+)\)', s)
        if m:
            base, cnt, meta = m.group(1), int(m.group(2)), int(m.group(3))
            ids = [f'minecraft:{c}_{COLORED[base]}' for c in (DYE_COLORS if meta == 32767 else [DYE_COLORS[meta]])]
            return ('item', ids[0], cnt) if len(ids) == 1 else ('items', ids, cnt)
        m = re.fullmatch(r'newFluidStack\((?:ItemList\.s(\w+)|FluidRegistry\.getFluid\("([\w.]+)"\)),(\d+)L?\)', s)
        if m:
            fid = named_fluid(m.group(1) or m.group(2))
            if not fid:
                raise Skip(e)
            return ('fluid', fid, int(m.group(3)))
        m = re.fullmatch(r'newItemStack\((?:Items|Blocks)\.(\w+)(?:,(\d+))?(?:,(\d+))?\)', s)
        if m:
            cnt_ = int(m.group(2) or 1)
            meta_ = int(m.group(3) or 0)
            if (m.group(1), meta_) in VANILLA_META:
                return ('item', VANILLA_META[(m.group(1), meta_)], cnt_)
            if m.group(1) in VANILLA_ITEMS and meta_ in (0, 32767):
                return ('item', VANILLA_ITEMS[m.group(1)], cnt_)
        m = re.fullmatch(r'ItemList\.Shape_Mold_(Ingot|Plate|Nugget|Gear|Ball|Block|Casing|Cylinder|Anvil|Bottle|Arrow)\.get\(0L?\)', s)
        if m:
            return ('mold', f'{NS}:mold_{m.group(1).lower()}')
        raise Skip(e)

    def as_in(x):
        if x[0] == 'item':
            spec, cnt = x[1], x[2]
            return ing(tag_of(*spec) if isinstance(spec, tuple) else spec, cnt)
        if x[0] == 'items':
            return ing(list(x[1]), x[2])
        if x[0] == 'cells':
            return ing(capsule_in(x[1]), x[2])
        raise Skip(x)

    def as_out(x):
        if x[0] == 'item':
            spec, cnt = x[1], x[2]
            return out(ref(*spec) if isinstance(spec, tuple) else spec, cnt)
        if x[0] == 'cells':
            return capsule_out(x[1], x[2])
        raise Skip(x)

    def fl(x, key):
        if x[0] == 'none':
            return None
        if x[0] != 'fluid':
            raise Skip(x)
        return {key: x[1], 'amount': x[2]}

    shapes = {
        # method, arg count -> (item inputs, fluid in, fluid out, item outputs, chances, duration, eut, default eut)
        ('addChemicalRecipe', 4): ([0, 1], None, None, [2], None, 3, None, 30),
        ('addChemicalRecipe', 6): ([0, 1], 2, 3, [4], None, 5, None, 30),
        ('addChemicalRecipe', 8): ([0, 1], 2, 3, [4, 5], None, 6, 7, 30),
        ('addChemicalRecipe', 9): ([0, 1, 2], 3, 4, [5, 6], None, 7, 8, 30),
        ('addChemicalRecipe', 10): ([0, 1, 2], 3, 4, [5, 6, 7], None, 8, 9, 30),
        ('addElectrolyzerRecipe', 13): ([0, 1], 2, 3, [4, 5, 6, 7, 8, 9], 10, 11, 12, 30),
        ('addCentrifugeRecipe', 13): ([0, 1], 2, 3, [4, 5, 6, 7, 8, 9], 10, 11, 12, 5),
        ('addMixerRecipe', 9): ([0, 1, 2, 3], 4, 5, [6], None, 7, 8, 8),
        ('addMixerRecipe', 11): ([0, 1, 2, 3, 4, 5], 6, 7, [8], None, 9, 10, 8),
        ('addFluidSolidifierRecipe', 5): ([0], 1, None, [2], None, 3, 4, 8),
        ('addFluidExtractionRecipe', 6): ([0], None, 2, [1], 3, 4, 5, 8),
        # GT_RecipeAdder#addChemicalBathRecipe(in, bath fluid, out1, out2, out3, chances, duration, EU/t)
        ('addChemicalBathRecipe', 8): ([0], 1, None, [2, 3, 4], 5, 6, 7, 8),
        # addDistilleryRecipe(circuit, in, out, [solid out], duration, EU/t, hidden)
        ('addDistilleryRecipe', 6): ([0], 1, 2, [], None, 3, 4, 8),
        ('addDistilleryRecipe', 7): ([0], 1, 2, [3], None, 4, 5, 8),
    }
    machine = {'addChemicalRecipe': 'chemical_reactor', 'addChemicalRecipeForBasicMachineOnly': 'chemical_reactor', 'addElectrolyzerRecipe': 'electrolyzer',
               'addCentrifugeRecipe': 'centrifuge', 'addMixerRecipe': 'mixer', 'addFluidSolidifierRecipe': 'fluid_solidifier',
               'addFluidExtractionRecipe': 'fluid_extractor', 'addChemicalBathRecipe': 'chemical_bath', 'addDistilleryRecipe': 'distillery'}
    taken, skipped, reasons = {}, 0, {}

    def distillery_scale(fin, fout, outs, dur):
        """GT_RecipeAdder#addDistilleryRecipe: batches over 1000 mB are scaled down; returns the new duration, edits amounts."""
        a_in, a_out = fin['amount'], fout['amount']
        t = max(1, (max(a_in, a_out) + 999) // 1000)
        if t <= 1:
            return dur
        for i in range(t, 6):
            if a_in % i == 0 and dur % i == 0:
                t = i
                break
        for i in range(t, 6):
            if a_in % i == 0 and dur % i == 0 and a_out % i == 0:
                t = i
                break
        fin['amount'] = (a_in + t - 1) // t
        fout['amount'] = a_out // t
        for o in outs:
            o['count'] = o['count'] // t
        outs[:] = [o for o in outs if o['count'] > 0]
        return (dur + t - 1) // t
    # D130: the mixer loop of GT over dust (GT_MachineRecipeLoader:1767-1796). Bronze, invar and the like are written by hand in lv.py, these had no maker
    DUST_LOOP = {1767, 1777, 1779, 1781, 1783, 1788, 1789, 1790, 1795, 1796}
    for c in calls:
        if c['line'] == 1827:   # D111: mHardRadonRecipe (GT_Mod:313, off by default): only :1829, 100 L of radon, stays
            continue
        meth = c['method'].replace('ForBasicMachineOnly', '')
        args = c['args']
        if c['line'] in DUST_LOOP:
            args = [a.replace('tPrefix.mMaterialAmount', '3628800').replace('tPrefix.dust', 'OrePrefixes.dust').replace('tPrefix', 'OrePrefixes.dust') for a in args]
        key = (meth, len(args))
        if meth == 'addChemicalRecipe' and len(args) == 7:
            # (in, in, fin, fout, out, out2, dur) or (in, in, fin, fout, out, dur, eut)
            key_shape = ([0, 1], 2, 3, [4], None, 5, 6, 30) if re.fullmatch(r'-?[\d*+L()\s]+', args[5]) else ([0, 1], 2, 3, [4, 5], None, 6, None, 30)
        elif key in shapes:
            key_shape = shapes[key]
        else:
            continue
        ins_i, fin_i, fout_i, outs_i, ch_i, dur_i, eut_i, def_eut = key_shape
        try:
            parsed = [expr(a) if i != ch_i else None for i, a in enumerate(args)]
            if meth == 'addDistilleryRecipe' and parsed[0][0] == 'int':
                parsed[0] = ('circuit', parsed[0][1])
            circuit, keep, inputs = None, [], []
            for i in ins_i:
                x = parsed[i]
                if x[0] == 'none':
                    continue
                if x[0] == 'circuit':
                    circuit = x[1]
                elif x[0] == 'mold':
                    keep.append(x[1])
                else:
                    inputs.append(as_in(x))
            fin = fl(parsed[fin_i], 'ingredient') if fin_i is not None else None
            fout = fl(parsed[fout_i], 'id') if fout_i is not None else None
            chances = None
            if ch_i is not None:
                raw = re.sub(r'\s+', '', args[ch_i])
                if raw not in ('null', 'GT_Values.NI'):
                    mm = re.fullmatch(r'newint\[\]\{([\d,]*)\}', raw)
                    if mm:
                        chances = [int(v) for v in mm.group(1).split(',') if v]
                    elif raw.isdigit():
                        chances = [int(raw)]
                    else:
                        raise Skip(raw)
            outs = []
            for k, i in enumerate(outs_i):
                x = parsed[i]
                if x[0] == 'none':
                    continue
                o = as_out(x)
                if meth == 'addFluidExtractionRecipe' and chances is not None:
                    o['chance'] = chances[0] if chances else 10000
                elif chances and k < len(chances) and chances[k] < 10000:
                    o['chance'] = chances[k]
                outs.append(o)
            dur = parsed[dur_i]
            eut = parsed[eut_i] if eut_i is not None else ('int', def_eut)
            if dur[0] != 'int' or eut[0] != 'int':
                raise Skip('time')
            if meth == 'addDistilleryRecipe':
                dur = ('int', distillery_scale(fin, fout, outs, dur[1]))
            # centrifuge and mixer: only recipes with fluids or cells (item-only ones come from lv.py)
            if meth in ('addCentrifugeRecipe', 'addMixerRecipe') and not (fin or fout or any('components' in o for o in outs)
                                                                          or any(isinstance(i['ingredient'], dict) for i in inputs)):
                continue
            if not inputs and not fin:
                continue
        except Skip as ex:
            skipped += 1
            skipped_list.append({'line': c['line'], 'method': c['method'], 'why': str(ex.args[0]) if ex.args else '?'})
            reasons[re.sub(r'\s+', '', str(ex.args[0]) if ex.args else '?')[:90]] = reasons.get(re.sub(r'\s+', '', str(ex.args[0]) if ex.args else '?')[:90], 0) + 1
            continue
        rtype = machine[c['method']]
        mrec(rtype, f"gt_{c['line']}", inputs, outs, max(1, dur[1]), max(1, eut[1]), fin=fin, fout=fout, circuit=circuit, keep=keep or None)
        taken[rtype] = taken.get(rtype, 0) + 1
    stats['gt_recipes'] = taken
    stats['gt_skipped'] = skipped
    # the list of what is still out, for a person to read: reference/gt_skipped.json
    with open(os.path.join(ROOT, 'reference', 'gt_skipped.json'), 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(skipped_list, fh, ensure_ascii=False, indent=0)
    stats['gt_skip_reasons'] = sorted(reasons.items(), key=lambda kv: -kv[1])[:40]
    return stats
