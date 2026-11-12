"""Machine recipes of IIS 1 that IIS 2 lacked, taken from the dump of the real pack (D147).

reference/iis1_import.json is written by `python tools/iis1_gt_compare.py --write`: every GT recipe of IIS 1 whose items and fluids all
exist in IIS 2 and whose first output IIS 2 did not make in that machine at all (recycling of parts in the fluid extractor, alloy smelter
and arc furnace, ores with calcite in the blast furnace, disassembling of machines, components MV and up in the assembler, dyes, ...).
Left out: recipes that clash with an existing one (reference/iis1_import_skip.json), maps IIS 2 fills by its own rules (plasma arc furnace, fluid canner, printer), turbines (own item) and "crushed" outputs of the
hammer and macerator (ores of other mods that the name matching confuses with ours).
"""
import json
import os

NS = 'iis'
SKIP_MAPS = {'plasma_arc_furnace', 'fluid_canner', 'printer', 'fusion'}   # fusion needs the start energy, IIS 2 has all of GT's
MAX_OUT = {'macerator': 3}


def generate(g):
    D, write, ROOT = g['D'], g['write'], g['ROOT']
    path = os.path.join(ROOT, 'reference', 'iis1_import.json')
    if not os.path.exists(path):
        return {'imported': 0}
    data = json.load(open(path, encoding='utf-8'))
    exists = lambda i: i.startswith('minecraft:') or not i.startswith(f'{NS}:') or os.path.exists(os.path.join(g['A'], 'items', i.split(':')[1] + '.json'))
    # recipes whose inputs another recipe of the same machine already uses for a different result (tools/iis1_conflicts.py --delete-list)
    skip_path = os.path.join(ROOT, 'reference', 'iis1_import_skip.json')
    skip = {tuple(x) for x in json.load(open(skip_path, encoding='utf-8'))} if os.path.exists(skip_path) else set()
    stats = {}
    for m, recs in sorted(data.items()):
        if m in SKIP_MAPS:
            continue
        seen, n = set(), 0
        for r in recs:
            outs = r['out']
            first = outs[0][0] if outs else ''
            if any(o[0].startswith(f'{NS}:turbine_') for o in outs) or (m in ('forge_hammer', 'macerator') and first.startswith(f'{NS}:crushed_')):
                continue
            if (m, ' '.join(sorted(i[0] for i in r['in'])), first) in skip:
                continue
            if len(outs) > MAX_OUT.get(m, 99) or {o[0] for o in outs} & {i[0] for i in r['in']}:
                continue   # more outputs than the machine has slots; "X -> X" is a 1.7.10 name mapped wrongly (melon slice / melon block)
            ids = [i[0] for i in r['in'] + r['keep'] + outs]
            if not all(exists(i) for i in ids):
                continue
            key = (tuple(sorted(map(tuple, r['in']))), tuple(sorted(map(tuple, r['fin']))), r['circuit'], tuple(sorted(k[0] for k in r['keep'])))
            if key in seen:
                continue
            seen.add(key)
            j = {'type': f'{NS}:{m}', 'inputs': [{'ingredient': i, 'count': c} for i, c in r['in']],
                 'outputs': [dict({'id': i, 'count': c}, **({'chance': ch} if ch < 10000 else {})) for i, c, ch in outs],
                 'duration': max(1, r['dur']), 'eut': max(1, r['eut'])}
            if r['fin']:
                j['fluid_inputs'] = [{'ingredient': f, 'amount': a} for f, a in r['fin']]
            if r['fout']:
                j['fluid_outputs'] = [{'id': f, 'amount': a} for f, a in r['fout']]
            if r['keep']:
                j['keep'] = [k[0] for k in r['keep']]
            if r['circuit'] is not None:
                j['circuit'] = r['circuit']
            if m == 'blast' and r['special']:
                j['temp'] = r['special']
            n += 1
            write(os.path.join(D, NS, 'recipe', 'processing', m, f'iis1_{n:04d}.json'), j)
        stats[m] = n
    # furnace recipes of IIS 1 that IIS 2 lacked (tools/iis1_furnace_compare.py --write): parts, tool heads, wires and cables back into metal
    fpath = os.path.join(ROOT, 'reference', 'iis1_furnace_import.json')
    if os.path.exists(fpath):
        n = 0
        for src, dst, cnt in json.load(open(fpath, encoding='utf-8')):
            if exists(src) and exists(dst):
                n += 1
                g['smelting'](f'iis1f/iis1f_{src.split(":")[1]}', src, dst, cnt, 0.1)
        stats['furnace'] = n
    return stats
