"""Machine recipes the game can never pick (D150).

A machine takes the first recipe that fits what lies in its slots and tank, trying the most demanding first (GtRecipes.all: more inputs,
fluids, items, mB; ties by id, as RecipeManager.prepare sorts them). So recipe A is dead when recipe B of the same machine fits whenever A fits
and is tried before it: in practice the two ask for exactly the same. GT5 told such recipes apart by the programmed circuit.

    python -I tools/recipe_shadow.py            summary by machine
    python -I tools/recipe_shadow.py bender     every dead recipe of a machine
    python -I tools/recipe_shadow.py --json     reference/recipe_shadow.json: {dead recipe: the recipe that takes its place}

    python -I tools/recipe_shadow.py --soft bender   pairs told apart only by amounts
"""
import collections
import contextlib
import glob
import io
import json
import os
import runpy
import sys

ARGS = sys.argv[1:]
sys.argv = ['check_reach.py', 'capsule']
with contextlib.redirect_stdout(io.StringIO()):
    G = runpy.run_path(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'check_reach.py'))
R = 'src/generated/resources'
expand = G['expand']


def items(ing):
    if isinstance(ing, dict) and ing.get('components'):   # a capsule of oxygen is not a capsule of hydrogen
        c = json.dumps(ing['components'], sort_keys=True)
        return frozenset(i + c for i in expand(ing))
    return frozenset(expand(ing))


def out_id(x):
    return x['id'] + (json.dumps(x['components'], sort_keys=True) if x.get('components') else '')


class Rec:
    pass


maps = collections.defaultdict(list)
for f in glob.glob(f'{R}/data/iis/recipe/**/*.json', recursive=True):
    d = json.load(open(f, encoding='utf-8'))
    t = d.get('type', '')
    if not t.startswith('iis:') or 'duration' not in d:
        continue
    r = Rec()
    r.id = os.path.relpath(f, R).replace(os.sep, '/')[len('data/iis/recipe/'):-5]
    r.inputs = [(items(x['ingredient']), x.get('count', 1)) for x in d.get('inputs', [])]
    r.keep = [items(x) for x in d.get('keep', [])]
    r.fluids = []
    for x in d.get('fluid_inputs', []):
        ing = x['ingredient']
        r.fluids.append((ing if isinstance(ing, str) else json.dumps(ing, sort_keys=True), x.get('amount', 1)))
    r.circuit = d.get('circuit', -1)
    r.eut = d.get('eut', 0)
    r.out = tuple(sorted([out_id(x) for x in d.get('outputs', [])] + [x['id'] for x in d.get('fluid_outputs', [])]))
    r.temp = d.get('temp', 0)
    maps[t[4:]].append(r)


def covers(b, a):
    """B fits whenever A fits."""
    if b.circuit >= 0 and b.circuit != a.circuit:
        return False
    if not b.inputs and not b.fluids:
        return False
    for name, amount in b.fluids:
        if not any(n == name and am >= amount for n, am in a.fluids):
            return False
    present = [s for s, _ in a.inputs] + a.keep
    for k in b.keep:
        if not any(s <= k for s in present):
            return False
    used = set()
    for s, c in b.inputs:
        hit = next((n for n, (sa, ca) in enumerate(a.inputs) if n not in used and sa <= s and ca >= c), None)
        if hit is None:
            return False
        used.add(hit)
    return True


def spec(r):
    """GtRecipes.specificity: the machine tries the most demanding recipe first."""
    return (len(r.inputs) + len(r.keep) + (1 if r.circuit >= 0 else 0), len(r.fluids), sum(c for _, c in r.inputs), sum(am for _, am in r.fluids))


soft = collections.defaultdict(list)   # the same things in other amounts: picked only by how much is put in
dead = {}
rows = collections.defaultdict(list)
for m, rs in maps.items():
    rs.sort(key=lambda r: r.id)
    by_item = collections.defaultdict(list)
    by_fluid = collections.defaultdict(list)
    for n, r in enumerate(rs):
        r.n = n
        if r.inputs:
            for i in r.inputs[0][0]:
                by_item[i].append(r)
        else:
            for name, _ in r.fluids:
                by_fluid[name].append(r)
    for a in rs:
        cand = {}
        for s, _ in a.inputs:
            for i in s:
                for b in by_item.get(i, ()):
                    cand[b.n] = b
        for name, _ in a.fluids:
            for b in by_fluid.get(name, ()):
                cand[b.n] = b
        for n in sorted(cand):
            b = cand[n]
            if b is a or b.out == a.out or not covers(b, a):
                continue
            sa, sb = spec(a), spec(b)
            if sb > sa or (sb == sa and b.n < a.n):
                dead[a.id] = (b.id, 'always')
                rows[m].append((a, b, 'always'))
                break
            if sb[:2] == sa[:2] and b.circuit == a.circuit:
                soft[m].append((a, b))

if ARGS[:1] == ['--json']:
    json.dump({k: v[0] for k, v in sorted(dead.items()) if v[1] == 'always'}, open('reference/recipe_shadow.json', 'w', encoding='utf-8'), indent=0)
    print(len(dead), 'dead recipes written')
elif ARGS[:1] == ['--soft']:
    for a, b in soft.get(ARGS[1], []):
        print(f'{a.id} {list(a.out)[:2]} > {b.id} {list(b.out)[:2]}')
elif ARGS:
    for a, b, kind in rows.get(ARGS[0], []):
        print(f'{kind:11} {a.id} {list(a.out)[:2]} c{a.circuit} <- {b.id} {list(b.out)[:2]} c{b.circuit}')
else:
    print(sum(len(v) for v in maps.values()), 'machine recipes;', len(dead), 'can never be picked')
    print('told apart only by amounts:', {m: len(v) for m, v in sorted(soft.items())})
    for m, v in sorted(rows.items(), key=lambda x: -len(x[1])):
        print(f'{m:26} {len(v):5}  always {sum(1 for x in v if x[2] == "always"):5}   e.g. {v[0][0].id} <- {v[0][1].id}')
