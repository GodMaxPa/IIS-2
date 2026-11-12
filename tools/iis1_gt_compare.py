"""Compares the GT machine recipes of IIS 2 with the dump of IIS 1 (reference/iis1_dump/gt.txt.gz, D147).

Per recipe map: how many IIS 1 recipes there are, how many have every item and fluid known to IIS 2 ("mappable"), and how many of those
make something that IIS 2 does not make in that machine at all ("missing outputs"). With --write the missing ones go to
reference/iis1_import.json, which tools/gen/iis1import.py turns into recipes.
Run from the repository root after gen_content.py:   python tools/iis1_gt_compare.py [--write] [--map NAME]
"""
import collections
import glob
import gzip
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
import iis1_compare  # noqa: E402

GEN = os.path.join(ROOT, 'src', 'generated', 'resources', 'data', 'iis', 'recipe')
MAPS = {'macerator': 'macerator', 'hammer': 'forge_hammer', 'cuttingsaw': 'cutter', 'metalbender': 'bender', 'fluidextractor': 'fluid_extractor',
        'fluidsolidifier': 'fluid_solidifier', 'chemicalreactor': 'chemical_reactor', 'blastfurnace': 'blast', 'implosioncompressor': 'implosion',
        'primitiveblastfurnace': 'primitive_blast', 'orewasher': 'ore_washer', 'thermalcentrifuge': 'thermal_centrifuge', 'vacuumfreezer': 'vacuum_freezer',
        'laserengraver': 'laser_engraver', 'brewer': 'brewery', 'press': 'forming_press', 'circuitassembler': 'circuit_assembler',
        'distillationtower': 'distillation_tower', 'craker': 'cracking', 'pyro': 'pyrolyse', 'electromagneticseparator': 'electromagnetic_separator',
        'arcfurnace': 'arc_furnace', 'plasmaarcfurnace': 'plasma_arc_furnace', 'fluidcanner': 'fluid_canner', 'packager': 'packager', 'unpackager': 'unpackager',
        'extruder': 'extruder', 'alloysmelter': 'alloy_smelter', 'lathe': 'lathe', 'wiremill': 'wiremill', 'polarizer': 'polarizer', 'mixer': 'mixer',
        'centrifuge': 'centrifuge', 'electrolyzer': 'electrolyzer', 'canner': 'canner', 'autoclave': 'autoclave', 'compressor': 'compressor', 'extractor': 'extractor',
        'sifter': 'sifter', 'distillery': 'distillery', 'fermenter': 'fermenter', 'slicer': 'slicer', 'fusionreactor': 'fusion', 'rockbreaker': 'rock_breaker',
        'fluidheater': 'fluid_heater', 'filter': 'filter', 'printer': 'printer', 'chemicalbath': 'chemical_bath', 'assembler': 'assembler', 'disassembler': 'disassembler'}
CIRCUIT = 'gregtech:gt.integrated_circuit@'
# moulds, shapes and lenses stay in the machine (stack size 0 in GT)


def main():
    c = iis1_compare.Compare()
    only = sys.argv[sys.argv.index('--map') + 1] if '--map' in sys.argv else None
    two = collections.defaultdict(set)      # our map -> output ids (items and fluids)
    for f in glob.glob(os.path.join(GEN, '**', '*.json'), recursive=True):
        if os.path.basename(f).startswith('iis1_'):   # our own import of the dump does not count as "IIS 2 has it"
            continue
        try:
            r = json.load(open(f, encoding='utf-8'))
        except ValueError:
            continue
        t = r.get('type', '')
        if not t.startswith('iis:'):
            continue
        for o in r.get('outputs', []):
            two[t[4:]].add(o['id'])
        for o in r.get('fluid_outputs', []):
            two[t[4:]].add('fluid:' + o['id'])
    stats = collections.OrderedDict()
    missing = collections.defaultdict(list)
    unmapped = collections.Counter()
    with gzip.open(os.path.join(ROOT, 'reference', 'iis1_dump', 'gt.txt.gz'), 'rt', encoding='utf-8') as f:
        for line in f:
            r = line.rstrip('\n').split('\t')
            if r[0].startswith('#') or len(r) < 8:
                continue
            gt = r[0].replace('gt.recipe.', '')
            ours = MAPS.get(gt)
            if ours is None or (only and ours != only):
                continue
            st = stats.setdefault(ours, [0, 0, 0])
            st[0] += 1
            if 'fake' in (r[10] if len(r) > 10 else ''):
                continue
            ok = True
            ins, keep, circuit = [], [], None
            for s in [x for x in r[1].split('|') if x and x != 'null']:
                if s.startswith(CIRCUIT):
                    circuit = int(s[len(CIRCUIT):].split('*')[0])
                    continue
                iid, n = c.item(s)
                if iid is None:
                    ok = False
                    unmapped[s.split('*')[0]] += 1
                    continue
                (keep if s.endswith('*0') else ins).append((iid, n))
            outs = []
            chances = [int(x) for x in re.findall(r'\d+', r[8])] if len(r) > 8 else []
            for k, s in enumerate([x for x in r[3].split('|') if x and x != 'null']):
                iid, n = c.item(s)
                if iid is None:
                    ok = False
                    unmapped[s.split('*')[0]] += 1
                    continue
                outs.append((iid, n, chances[k] if k < len(chances) else 10000))
            fin, fout = [], []
            for src, dst in ((r[2], fin), (r[4], fout)):
                for s in [x for x in src.split('|') if x and x != 'null']:
                    name, _, amt = s.rpartition('*')
                    fl = c.mp.fluid(name)
                    if fl is None:
                        ok = False
                        unmapped['fluid ' + name] += 1
                        continue
                    dst.append((fl, int(amt)))
            if not ok or not (outs or fout):
                continue
            st[1] += 1
            first = outs[0][0] if outs else 'fluid:' + fout[0][0]
            if first not in two.get(ours, ()):
                st[2] += 1
                missing[ours].append({'in': ins, 'keep': keep, 'circuit': circuit, 'fin': fin, 'out': outs, 'fout': fout,
                                      'dur': int(r[5]), 'eut': int(r[6]), 'special': int(r[7])})
    print(f'{"map":28}{"IIS 1":>8}{"mappable":>10}{"missing":>9}{"IIS 2 outputs":>15}')
    for k, (a, b, m) in sorted(stats.items(), key=lambda kv: -kv[1][2]):
        print(f'{k:28}{a:8}{b:10}{m:9}{len(two.get(k, ())):15}')
    print('\nmost frequent unknown items and fluids:')
    for k, v in unmapped.most_common(40):
        print(f'  {v:6} {k}  {c.names.get(k, "")[:50]}')
    if '--write' in sys.argv:
        with open(os.path.join(ROOT, 'reference', 'iis1_import.json'), 'w', encoding='utf-8', newline='\n') as fh:
            json.dump(missing, fh, ensure_ascii=False, indent=0, sort_keys=True)
        print('written', sum(len(v) for v in missing.values()))


if __name__ == '__main__':
    main()
