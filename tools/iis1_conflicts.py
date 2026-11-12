"""Finds imported IIS 1 machine recipes (processing/<map>/iis1_*.json) whose inputs are also the inputs of another recipe of the same
machine with a different result (D147). Counts are ignored: the machine would take whichever recipe it meets first.
Run from the repository root after gen_content.py:   python tools/iis1_conflicts.py [--delete-list]
"""
import collections
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'src', 'generated', 'resources', 'data')
tags = {}


def tag(name):
    """Items of an item tag, nested tags resolved."""
    if name in tags:
        return tags[name]
    tags[name] = out = set()
    ns, _, path = name.partition(':')
    f = os.path.join(DATA, ns, 'tags', 'item', path + '.json')
    if os.path.exists(f):
        for v in json.load(open(f, encoding='utf-8')).get('values', []):
            v = v['id'] if isinstance(v, dict) else v
            if v.startswith('#'):
                out |= tag(v[1:])
            else:
                out.add(v)
    return out


def items(ing):
    if isinstance(ing, dict):   # {"item": ...} / {"tag": ...} / data component ingredients
        ing = ing.get('item') or ('#' + ing['tag'] if 'tag' in ing else json.dumps(ing, sort_keys=True))
    if isinstance(ing, list):
        return frozenset().union(*[items(i) for i in ing]) if ing else frozenset()
    return frozenset(tag(ing[1:])) if ing.startswith('#') else frozenset([ing])


def sig(r):
    return (len(r.get('inputs', [])), r.get('circuit'), tuple(sorted(str(f['ingredient']) for f in r.get('fluid_inputs', []))),
            tuple(sorted(map(str, r.get('keep', [])))))


def main():
    conflicts = []
    for d in sorted(glob.glob(os.path.join(DATA, 'iis', 'recipe', 'processing', '*'))):
        ours, imp = collections.defaultdict(list), []
        for f in glob.glob(os.path.join(d, '*.json')):
            r = json.load(open(f, encoding='utf-8'))
            if os.path.basename(f).startswith('iis1_'):
                imp.append((f, r))
            else:
                ours[sig(r)].append((f, r, [items(i['ingredient']) for i in r.get('inputs', [])]))
        for f, r in imp:
            mine = [i['ingredient'] for i in r.get('inputs', [])]
            for g, o, sets in ours.get(sig(r), ()):
                left = list(sets)
                for i in mine:
                    hit = next((s for s in left if i in s), None)
                    if hit is None:
                        break
                    left.remove(hit)
                else:
                    if [x['id'] for x in o.get('outputs', [])] != [x['id'] for x in r.get('outputs', [])]:
                        conflicts.append((os.path.relpath(f, DATA), os.path.relpath(g, DATA), mine, [x['id'] for x in r.get('outputs', [])],
                                          [x['id'] for x in o.get('outputs', [])]))
                    break
    by = collections.Counter(c[0].split(os.sep)[-2] for c in conflicts)
    print('conflicts:', len(conflicts), dict(by))
    for c in conflicts[:60]:
        print(' ', c[0].replace(os.sep, '/').split('processing/')[1], 'vs', os.path.basename(c[1]), c[2], '->', c[3], '| ours', c[4])
    if '--delete-list' in sys.argv:
        json.dump(sorted({(c[0].split(os.sep)[-2], ' '.join(sorted(c[2])), c[3][0] if c[3] else '') for c in conflicts}), open(os.path.join(ROOT, 'reference', 'iis1_import_skip.json'), 'w'), indent=0)


if __name__ == '__main__':
    main()
