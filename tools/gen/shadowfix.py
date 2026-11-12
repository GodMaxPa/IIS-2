"""Machine recipes that another recipe hid for good (D150), found by tools/recipe_shadow.py.

A machine takes the most demanding recipe that fits its slots (GtRecipes.all). That leaves recipes with the same inputs,
or inputs that differ only in how many: those are told apart by a programmed circuit, as GT5 did.
Runs last in gen_content.py, over the recipe files already written.
"""
import glob
import json
import os

# recipe -> circuit: identical inputs, or the same things in other amounts
CIRCUITS = {
    'processing/assembler/computer_monitor': 1,      # = cover_screen
    'processing/assembler/vehicle_atv': 1, 'processing/assembler/vehicle_truck': 2,
    'processing/cartridge_press/grenade_round': 1, 'processing/cartridge_press/tank_shell': 2,
    'processing/chemical_reactor/gt_3133': 2,        # = gt_3131 with phenol and acetone swapped (the same clash is in GT5)
}
# the same input under a tag already has a recipe that wins by id
DROP = ['processing/macerator/vanilla_blaze_rod', 'processing/macerator/vanilla_flint']


def generate(g):
    root = os.path.join(g['D'], g['NS'], 'recipe')
    write = g['write']
    stats = {'bender': 0, 'autoclave': 0, 'named': 0, 'dropped': 0}

    def load(p):
        return json.load(open(p, encoding='utf-8'))

    def rid(p):
        return os.path.relpath(p, root).replace(os.sep, '/')[:-5]

    files = sorted(glob.glob(os.path.join(root, '**', '*.json'), recursive=True), key=rid)
    crops = {}
    for p in files:
        d = load(p)
        t = d.get('type')
        if t == 'iis:bender' and 'circuit' not in d and len(d.get('inputs', [])) == 1 and 1 < d['inputs'][0].get('count', 1) <= 24:
            # GT_Recipe: a bender recipe carries the circuit set to the size of its input stack; one item needs none here
            d['circuit'] = d['inputs'][0]['count']
            write(p, d)
            stats['bender'] += 1
        elif t == 'iis:autoclave' and os.path.basename(p).startswith('crop_') and 'circuit' not in d:
            crops.setdefault(json.dumps(d['inputs'], sort_keys=True), []).append(p)
    for group in crops.values():   # one leaf, several ores: only the amount of UU-matter differed
        if len(group) > 1:
            for n, p in enumerate(group):
                d = load(p)
                d['circuit'] = n + 1
                write(p, d)
                stats['autoclave'] += 1
    for name, c in CIRCUITS.items():
        p = os.path.join(root, name + '.json')
        d = load(p)
        d['circuit'] = c
        write(p, d)
        stats['named'] += 1
    for name in DROP:
        p = os.path.join(root, name + '.json')
        if os.path.exists(p):
            os.remove(p)
            stats['dropped'] += 1
    return stats
