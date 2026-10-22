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


class Skip(Exception):
    pass


def chem_recipes(g, mrec, ing, out, ref, tag_of, prop, mat_id, names, WATER):
    ROOT = g['ROOT']
    code = g['code']
    fluids = {f['id']: f for f in json.load(open(os.path.join(g['RES'], NS, 'gt_fluids.json'), encoding='utf-8'))}
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
        low = material.lower()
        cand = {'fluid': [low, f'liquid_{low}', f'solution_{low}'], 'gas': [low], 'molten': [f'molten_{low}'], 'plasma': [f'plasma_{low}']}[kind]
        for c in cand:
            if c in fluids:
                return f'{NS}:{c}'
        return None

    def named_fluid(name):
        low = name.lower().replace('.', '_')
        if low in ('water', 'ic2water'):
            return 'minecraft:water'
        for c in (low, f'liquid_{low}', f'solution_{low}', low.removeprefix('liquid_')):
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

    # --- 3. hand-written GT recipes ---------------------------------------------------------------------------------------
    calls = json.load(open(os.path.join(ROOT, 'reference', 'original', 'gt5_machine_recipes.json'), encoding='utf-8'))['calls']

    def expr(e):
        """('item', ingredient-or-id spec) / ('fluid', id, amount) / ('circuit', n) / ('none',) / ('int', v); Skip if unknown."""
        s = re.sub(r'\s+', '', e)
        s = re.sub(r',newObject\[0\]|,o\)|,newObject\[0\]\)', lambda m: ')' if m.group(0).endswith(')') else '', s)
        s = s.replace(',newObject[0])', ')')
        if s in ('GT_Values.NI', 'GT_Values.NF', 'null'):
            return ('none',)
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
        m = re.fullmatch(r'newFluidStack\((?:ItemList\.s(\w+)|FluidRegistry\.getFluid\("([\w.]+)"\)),(\d+)L?\)', s)
        if m:
            fid = named_fluid(m.group(1) or m.group(2))
            if not fid:
                raise Skip(e)
            return ('fluid', fid, int(m.group(3)))
        m = re.fullmatch(r'newItemStack\((?:Items|Blocks)\.(\w+),(\d+)(?:,0)?\)', s)
        if m and m.group(1) in VANILLA_ITEMS:
            return ('item', VANILLA_ITEMS[m.group(1)], int(m.group(2)))
        m = re.fullmatch(r'ItemList\.Shape_Mold_(Ingot|Plate|Nugget|Gear|Ball|Block)\.get\(0L?\)', s)
        if m:
            return ('mold', f'{NS}:mold_{m.group(1).lower()}')
        raise Skip(e)

    def as_in(x):
        if x[0] == 'item':
            spec, cnt = x[1], x[2]
            return ing(tag_of(*spec) if isinstance(spec, tuple) else spec, cnt)
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
    }
    machine = {'addChemicalRecipe': 'chemical_reactor', 'addChemicalRecipeForBasicMachineOnly': 'chemical_reactor', 'addElectrolyzerRecipe': 'electrolyzer',
               'addCentrifugeRecipe': 'centrifuge', 'addMixerRecipe': 'mixer', 'addFluidSolidifierRecipe': 'fluid_solidifier',
               'addFluidExtractionRecipe': 'fluid_extractor'}
    taken, skipped = {}, 0
    for c in calls:
        meth = c['method'].replace('ForBasicMachineOnly', '')
        args = c['args']
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
                    if not mm:
                        raise Skip(raw)
                    chances = [int(v) for v in mm.group(1).split(',') if v]
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
            # centrifuge and mixer: only recipes with fluids or cells (item-only ones come from lv.py)
            if meth in ('addCentrifugeRecipe', 'addMixerRecipe') and not (fin or fout or any('components' in o for o in outs)
                                                                          or any(isinstance(i['ingredient'], dict) for i in inputs)):
                continue
            if not inputs and not fin:
                continue
        except Skip:
            skipped += 1
            continue
        rtype = machine[c['method']]
        mrec(rtype, f"gt_{c['line']}", inputs, outs, max(1, dur[1]), max(1, eut[1]), fin=fin, fout=fout, circuit=circuit, keep=keep or None)
        taken[rtype] = taken.get(rtype, 0) + 1
    stats['gt_recipes'] = taken
    stats['gt_skipped'] = skipped
    return stats
