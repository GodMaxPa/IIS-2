"""MV machines that IIS 2 had no LV version of (D83), called from gen_content.py after fluids.py with its globals.

Recipes of the extruder and the alloy smelter molds: GT5-II (IdealIndustrial/GT5Unofficial @ 00021e3) ProcessingShaping.
Machine blocks, shapes and their crafting live in lv.py (MACHINES, ITEMS); this module writes the machine recipes only.
"""
import os

# ProcessingShaping: shape -> (output form, input count, output count per tAmount, duration rule, EU/t factor).
# Duration rules: ('mass', k) = max(mass * k * tAmount, tAmount); ('flat', k) = k * tAmount. Forms without items here
# (hammer and file heads, IC2 fuel rods and non-steel item casings) are not generated.
SHAPES = [
    ('ingot', 'ingot', 1, 1, ('ingot', 10), 4),
    ('pipe_tiny', 'pipe_tiny', 1, 2, ('flat', 4), 8),
    ('pipe_small', 'pipe_small', 1, 1, ('flat', 8), 8),
    ('pipe_medium', 'pipe_medium', 3, 1, ('flat', 24), 8),
    ('pipe_large', 'pipe_large', 6, 1, ('flat', 48), 8),
    ('pipe_huge', 'pipe_huge', 12, 1, ('flat', 96), 8),
    ('plate', 'plate', 1, 1, ('mass', 1), 8),
    ('rod', 'rod', 1, 2, ('mass', 2), 6),
    ('wire', 'wire', 1, 2, ('mass', 2), 6),
    ('bolt', 'bolt', 1, 8, ('mass', 2), 8),
    ('ring', 'ring', 1, 4, ('mass', 2), 6),
    ('sword', 'sword_head', 2, 1, ('mass', 2), 8),
    ('pickaxe', 'pickaxe_head', 3, 1, ('mass', 3), 8),
    ('shovel', 'shovel_head', 1, 1, ('mass', 1), 8),
    ('axe', 'axe_head', 3, 1, ('mass', 3), 8),
    ('hoe', 'hoe_head', 2, 1, ('mass', 2), 8),
    ('saw', 'saw_head', 2, 1, ('mass', 2), 8),
    ('gear', 'gear', 4, 1, ('mass', 5), 8),
    ('small_gear', 'small_gear', 1, 1, ('mass', 0.2), 8),
]
BLOCKS = {'Iron': 'minecraft:iron_block', 'Gold': 'minecraft:gold_block', 'Copper': 'minecraft:copper_block'}
M = 3628800   # GT material unit
# Forms GT recycles (OrePrefixes.mAllowNormalRecycling) with their material amount (GT_RecipeRegistrator#registerMaterialRecycling)
RECYCLED = {'plate': M, 'foil': M // 4, 'long_rod': M, 'rod': M // 2, 'bolt': M // 8, 'screw': M // 9, 'ring': M // 4, 'wire_fine': M // 8,
            'rotor': M * 4 + M // 4, 'small_gear': M, 'gear': M * 4, 'gem': M, 'gem_chipped': M // 4, 'gem_flawed': M // 2,
            'gem_flawless': M * 2, 'gem_exquisite': M * 4, 'sword_head': M * 2, 'pickaxe_head': M * 3, 'shovel_head': M, 'axe_head': M * 3,
            'hoe_head': M * 2, 'saw_head': M * 2, 'drill_head': M * 4, 'chainsaw_head': M * 2, 'wrench_head': M * 4}
PIPE_AMOUNTS = {'tiny': M // 2, 'small': M, 'medium': M * 3, 'large': M * 6, 'huge': M * 12}
WIRE_AMOUNTS = {1: M // 2, 2: M, 4: M * 2, 8: M * 4, 12: M * 6, 16: M * 8}


def generate(g):
    write, D, NS = g['write'], g['D'], g['NS']
    ref, tag_of, mat_id, prop, mass, subtags = g['ref'], g['tag_of'], g['mat_id'], g['prop'], g['mass'], g['subtags']
    pipes = {pid for pid, *_ in g['_fluids'].pipe_specs()}
    rec = {}

    def mrec(rtype, rid, inputs, outputs, duration, eut, keep):
        outputs = [o for o in outputs if o['id']]
        if any(i['ingredient'] is None for i in inputs) or not outputs:
            return
        rec.setdefault(f'{rtype}/{rid}', {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs,
                                          'duration': max(1, int(duration)), 'eut': max(1, int(eut)), 'keep': [keep]})

    def ing(i, c=1):
        return {'ingredient': i, 'count': c}

    def out(i, c=1):
        return {'id': i, 'count': c}

    def form(prefix, n):
        if prefix.startswith('pipe_'):
            pid = f'pipe_{prefix[5:]}_{mat_id(n)}'
            return f'{NS}:{pid}' if pid in pipes else None
        if prefix == 'wire':
            return f'{NS}:wire_{mat_id(n)}_1x' if n in wires else None
        return ref(prefix, n)

    wires = {w[0] for w in g['_lv'].WIRES}
    shape = lambda s: f'{NS}:shape_extruder_{s}'
    for n in sorted(g['names']):
        if n in g['NOT_GENERATED_MATERIALS'] or 'NO_SMELTING' in subtags(n):
            continue
        if not (n == 'Glass' or ref('ingot', n)):
            continue
        into = prop(n, 'MaterialSmeltInto', n) or n
        st, m, mid = subtags(n), mass(n), mat_id(n)
        mult = 64 if prop(n, 'BlastFurnaceTemp', 0) >= 2800 else 16
        # GT: dust is an input only for NO_SMASHING materials (their multiplier / 4); ingots always (tAmount = 1)
        sources = [('ingot', ref('ingot', n))]
        if 'NO_SMASHING' in st:
            mult //= 4
            sources.append(('dust', ref('dust', n)))
        for src, item in sources:
            if not item:
                continue
            tin = tag_of(src, n)
            pre = f'{src}_{mid}'
            if into in BLOCKS:
                mrec('extruder', f'block_from_{pre}', [ing(tin, 9)], [out(BLOCKS[into])], 10, 8 * mult, shape('block'))
                mrec('alloy_smelter', f'block_from_{pre}', [ing(tin, 9)], [out(BLOCKS[into])], 5, 4 * mult, f'{NS}:mold_block')
            for sh, fo, cin, cout, (rule, k), f in SHAPES:
                if sh == 'ingot' and src == 'ingot' and into == n:
                    continue
                if sh in ('pipe_small', 'pipe_medium', 'pipe_large') and into in ('Redstone', 'Glowstone'):
                    continue
                item_out = form(fo, into)
                if not item_out:
                    continue
                dur = 10 if rule == 'ingot' else (k if rule == 'flat' else max(int(m * k), 1))
                mrec('extruder', f'{sh}_from_{pre}', [ing(tin, cin)], [out(item_out, cout)], dur, f * mult, shape(sh))
            # alloy smelter molds: 2 -> plate (mass * 2, 2 * mult), 8 -> gear (mass * 10, 2 * mult)
            if ref('plate', into):
                mrec('alloy_smelter', f'plate_from_{pre}', [ing(tin, 2)], [out(ref('plate', into))], max(m * 2, 1), 2 * mult, f'{NS}:mold_plate')
            if ref('gear', into):
                mrec('alloy_smelter', f'gear_from_{pre}', [ing(tin, 8)], [out(ref('gear', into))], max(m * 10, 1), 2 * mult, f'{NS}:mold_gear')
            # ProcessingShaping switch on mSmeltInto (capsule = GT cell; steel item casing = IC2 advanced iron casing)
            if into == 'Glass':
                mrec('extruder', f'bottle_from_{pre}', [ing(tin)], [out('minecraft:glass_bottle')], 32, 16, shape('bottle'))
                mrec('alloy_smelter', f'bottle_from_{pre}', [ing(tin)], [out('minecraft:glass_bottle')], 64, 4, f'{NS}:mold_bottle')
            if into in ('Steel', 'Tin'):
                mrec('extruder', f'cell_from_{pre}', [ing(tin, 2)], [out(f'{NS}:capsule')], 128, 32, shape('cell'))
            if into == 'Steel':
                mrec('extruder', f'casing_from_{pre}', [ing(tin)], [out(f'{NS}:steel_item_casing', 2)], 32, 3 * mult, shape('casing'))
                mrec('alloy_smelter', f'casing_from_{pre}', [ing(tin, 2)], [out(f'{NS}:steel_item_casing', 3)], 128, mult, f'{NS}:mold_casing')
            if into in ('Iron', 'WroughtIron'):
                mrec('alloy_smelter', f'anvil_from_{pre}', [ing(tin, 31)], [out('minecraft:anvil')], 512, 4 * mult, f'{NS}:mold_anvil')
    # forming press: GT_MachineRecipeLoader:1322 brass plate + coinage mold -> 4 doge coins (100 t, 16 EU/t),
    # :551 glass + arrow head mold -> empty glass arrow head (64 t, 4 EU/t); IC2 coins, AE2/BuildCraft and food are not ported
    mrec('forming_press', 'coin_doge', [ing(tag_of('plate', 'Brass'))], [out(f'{NS}:coin_doge', 4)], 100, 16, f'{NS}:mold_credit')
    mrec('forming_press', 'arrow_head_glass_empty', [ing('minecraft:glass')], [out(f'{NS}:arrow_head_glass_empty')], 64, 4, f'{NS}:mold_arrow')
    # fluid heater: GT_MachineRecipeLoader:723-724 circuit 1, 6 L water / distilled water -> 960 L steam (30 t, 32 EU/t)
    for fid, rid in (('minecraft:water', 'steam_from_water'), (f'{NS}:distilled_water', 'steam_from_distilled_water')):
        rec[f'fluid_heater/{rid}'] = {'type': f'{NS}:fluid_heater', 'inputs': [], 'outputs': [], 'duration': 30, 'eut': 32,
                                      'fluid_inputs': [{'ingredient': fid, 'amount': 6}], 'fluid_outputs': [{'id': f'{NS}:steam', 'amount': 960}],
                                      'circuit': 1}
    # --- arc furnace: GT_RecipeRegistrator#registerReverseArcSmelting (ArcSmeltIntoAnnealedWrought = true) -------------------
    # metal -> its smelt-into's arc-smelt-into (iron -> wrought iron, copper -> annealed copper), flammable -> ash / 8,
    # explosive -> ash / 16, other non-metals -> nothing; output getIngotOrDust; max(16, amount * mass / M) ticks, 96 EU/t,
    # oxygen = duration in L (GT_RecipeAdder#addArcFurnaceRecipe:1124)
    def ingot_or_dust(mat, amt):
        if mat in BLOCKS and ((amt % (9 * M) == 0 and amt // (9 * M) > 1) or amt >= 72 * M):
            return BLOCKS[mat], amt // (9 * M)
        for pre, ok, cnt in (('ingot', amt % M == 0 or amt >= 8 * M, amt // M), ('nugget', amt * 9 >= M, amt * 9 // M),
                             ('dust', amt % M == 0 or amt >= 16 * M, amt // M), ('small_dust', (amt * 4) % M == 0 or amt >= 8 * M, amt * 4 // M),
                             ('tiny_dust', amt * 9 >= M, amt * 9 // M)):
            if ok and cnt > 0 and ref(pre, mat):
                return ref(pre, mat), cnt
        return None

    def arc(rid, ingredient, n, amount):
        st = subtags(n)
        target = lambda: prop(prop(n, 'MaterialSmeltInto', n) or n, 'MaterialArcSmeltInto', n) or n
        if 'UNBURNABLE' in st:
            mat, amt = target(), amount
        elif 'EXPLOSIVE' in st:
            mat, amt = 'Ash', amount // 16
        elif 'FLAMMABLE' in st:
            mat, amt = 'Ash', amount // 8
        elif 'NO_SMELTING' in st or 'METAL' not in st:
            return
        else:
            mat, amt = target(), amount
        res = ingot_or_dust(mat, amt) if amt > 0 else None
        if not res or res[1] > 64:
            return
        dur = max(16, amt * mass(mat) // M)
        rec.setdefault(f'arc_furnace/{rid}', {'type': f'{NS}:arc_furnace', 'inputs': [ing(ingredient)], 'outputs': [out(res[0], res[1])],
                                             'duration': dur, 'eut': 96, 'fluid_inputs': [{'ingredient': f'{NS}:oxygen', 'amount': dur}]})

    for n in sorted(g['names']):
        mid = mat_id(n)
        for pre, amount in RECYCLED.items():
            if ref(pre, n):
                arc(f'{pre}_{mid}', tag_of(pre, n), n, amount)
        for size, amount in PIPE_AMOUNTS.items():
            if f'pipe_{size}_{mid}' in pipes:
                arc(f'pipe_{size}_{mid}', f'{NS}:pipe_{size}_{mid}', n, amount)
        if n in wires:
            for k, amount in WIRE_AMOUNTS.items():
                arc(f'wire_{k}x_{mid}', f'#iis:wires/{k}x/{mid}', n, amount)
        # ProcessingIngot:38, ProcessingDust:34/240/256: ingots and dusts only when the arc changes the material
        into = prop(n, 'MaterialSmeltInto', n) or n
        if (prop(into, 'MaterialArcSmeltInto', into) or into) != n:
            if ref('ingot', n):
                arc(f'ingot_{mid}', tag_of('ingot', n), n, M)
            if not prop(n, 'BlastFurnaceRequired', False):
                for pre, amount in (('dust', M), ('small_dust', M // 4), ('tiny_dust', M // 9)):
                    if ref(pre, n):
                        arc(f'{pre}_{mid}', tag_of(pre, n), n, amount)

    # --- lenses and the laser engraver (D86) -------------------------------------------------------------------------------
    # ProcessingLens: lathe plate -> lens + small dust (max(mass / 2, 1), 480 EU/t); exquisite gem -> lens + 2 dust (mass, 24)
    import json
    dyes = {}
    for x in json.load(open(os.path.join(g['REF'], 'gt5_materials.json'), encoding='utf-8')):
        dyes[x['name']] = next((a[len('Dyes.dye'):] for a in x.get('rawArgs', []) if isinstance(a, str) and a.startswith('Dyes.dye')), None)
    lenses = {}
    for n in sorted(g['names']):
        lens = ref('lens', n)
        if not lens:
            continue
        lenses[n] = lens
        mid = mat_id(n)
        if ref('plate', n):
            rec.setdefault(f'lathe/lens_{mid}', {'type': f'{NS}:lathe', 'inputs': [ing(tag_of('plate', n))],
                                               'outputs': [out(lens)] + ([out(ref('small_dust', n))] if ref('small_dust', n) else []),
                                               'duration': max(mass(n) // 2, 1), 'eut': 480})
        if ref('gem_exquisite', n):
            rec.setdefault(f'lathe/lens_from_exquisite_{mid}', {'type': f'{NS}:lathe', 'inputs': [ing(tag_of('gem_exquisite', n))],
                                                              'outputs': [out(lens)] + ([out(ref('dust', n), 2)] if ref('dust', n) else []),
                                                              'duration': max(mass(n), 1), 'eut': 24})
        # GT_Proxy:1086 every lens is also craftingLens<colour of the material>
        if dyes.get(n):
            g['add_item_tag'](f'iis:crafting_lens/{dyes[n].lower()}', lens)
    # ProcessingCrafting craftingLensWhite (:72-74): chiseled sandstone, stone bricks and quartz (50 t, 16 EU/t)
    if any(dyes.get(n) == 'White' for n in lenses):
        for rid, a, b in (('chiseled_sandstone', 'minecraft:smooth_sandstone', 'minecraft:chiseled_sandstone'),
                          ('chiseled_stone_bricks', 'minecraft:stone', 'minecraft:chiseled_stone_bricks'),
                          ('chiseled_quartz_block', 'minecraft:quartz_block', 'minecraft:chiseled_quartz_block')):
            mrec('laser_engraver', rid, [ing(a)], [out(b)], 50, 16, '#iis:crafting_lens/white')
    # GT_MachineRecipeLoader:499-501 paper + ruby / red garnet / jasper lens -> 4 feathers (32 t, 8 EU/t)
    for n in ('Ruby', 'GarnetRed', 'Jasper'):
        if n in lenses:
            mrec('laser_engraver', f'feather_{mat_id(n)}', [ing('minecraft:paper')], [out('minecraft:feather', 4)], 32, 8, lenses[n])
    # GT_MachineRecipeLoader:1981, :1983 emitters in the assembler (circuit 5, 80 t, 30 EU/t)
    for tier, gem, rod, circ, cable in (('lv', tag_of('gem', 'Quartzite'), 'Brass', '#c:circuits/basic', 'Tin'),
                                        ('mv', 'minecraft:quartz', 'Electrum', '#c:circuits/good', 'Copper')):
        rec[f'assembler/emitter_{tier}'] = {'type': f'{NS}:assembler', 'inputs': [ing(gem), ing(tag_of('rod', rod), 4), ing(circ, 2),
                                                                                ing(f'#iis:cables/1x/{mat_id(cable)}', 2)],
                                            'outputs': [out(f'{NS}:emitter_{tier}')], 'duration': 80, 'eut': 30, 'circuit': 5}

    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'lenses': len(lenses), 'laser_engraver': sum(1 for k in rec if k.startswith('laser_engraver/')), 'arc_furnace': sum(1 for k in rec if k.startswith('arc_furnace/')), 'forming_press': sum(1 for k in rec if k.startswith('forming_press/')), 'fluid_heater': sum(1 for k in rec if k.startswith('fluid_heater/')),
            'extruder': sum(1 for k in rec if k.startswith('extruder/')), 'alloy_smelter_molds': sum(1 for k in rec if k.startswith('alloy_smelter/'))}
