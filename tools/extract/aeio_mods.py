"""List items and recipes of the ready AE2 and Ender IO jars (D125) into reference/original/aeio_mods.json.

The generator (tools/gen/aeio.py) needs this list to switch off recipes and to hide items: it must not depend on the jars.
Usage (from the repo root): python -I tools/extract/aeio_mods.py <folder with appliedenergistics2-*.jar and enderio-*.jar>
"""
import glob
import json
import os
import re
import sys
import zipfile


def walk(node, found):
    """All item ids and tags a recipe mentions as ingredients."""
    if isinstance(node, str):
        if re.fullmatch(r'#?[a-z0-9_.-]+:[a-z0-9_./-]+', node):
            found.add(node)
    elif isinstance(node, list):
        for x in node:
            walk(x, found)
    elif isinstance(node, dict):
        for k, v in node.items():
            if k not in ('type', 'category', 'group', 'pattern', 'result', 'results', 'neoforge:conditions', 'show_notification'):
                walk(v, found)


def read_mod(jar, ns):
    z = zipfile.ZipFile(jar)
    names = z.namelist()
    items = sorted(m.group(1) for m in (re.fullmatch(rf'assets/{ns}/items/(\w+)\.json', n) for n in names) if m)
    recipes = {}
    for n in names:
        m = re.fullmatch(rf'data/{ns}/recipe/(.+)\.json', n)
        if not m:
            continue
        r = json.loads(z.read(n).decode('utf-8'))
        res = r.get('result') or (r.get('results') or [None])[0] or r.get('output')
        out = res if isinstance(res, str) else (res or {}).get('id')
        comp = (res or {}).get('components', {}).get('enderio:conduit') if isinstance(res, dict) else None
        found = set()
        walk(r, found)
        e = {'type': r.get('type', ''), 'out': out, 'in': sorted(found)}
        if comp:
            e['conduit'] = comp
        recipes[m.group(1)] = e
    return {'jar': os.path.basename(jar), 'items': items, 'recipes': recipes}


def main():
    src = sys.argv[1]
    data = {'ae2': read_mod(glob.glob(os.path.join(src, 'appliedenergistics2-*.jar'))[0], 'ae2'),
            'enderio': read_mod(glob.glob(os.path.join(src, 'enderio-*.jar'))[0], 'enderio')}
    dst = os.path.join('reference', 'original', 'aeio_mods.json')
    with open(dst, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=0, sort_keys=True)
    print({k: (len(v['items']), len(v['recipes'])) for k, v in data.items()}, os.path.getsize(dst))


if __name__ == '__main__':
    main()
