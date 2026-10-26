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
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'extruder': sum(1 for k in rec if k.startswith('extruder/')), 'alloy_smelter_molds': sum(1 for k in rec if k.startswith('alloy_smelter/'))}
