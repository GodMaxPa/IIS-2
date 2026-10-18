#!/usr/bin/env python3
"""Extract GT5-II material definitions from Materials.java into JSON (reference data, not game data).

Usage: gt5_materials.py <path/to/gregtech/api/enums/Materials.java> <out.json>
"""
import json, re, sys

src = open(sys.argv[1], encoding='utf-8', errors='replace').read()
src = re.sub(r'//[^\n]*', '', src)

def split_args(s):
    out, depth, cur, q = [], 0, '', False
    for ch in s:
        if ch == '"': q = not q
        if not q:
            if ch in '([{': depth += 1
            elif ch in ')]}': depth -= 1
            elif ch == ',' and depth == 0:
                out.append(cur.strip()); cur = ''; continue
        cur += ch
    if cur.strip(): out.append(cur.strip())
    return out

def num(x):
    x = x.strip().rstrip('FfLl')
    try: return int(x)
    except ValueError:
        try: return float(x)
        except ValueError: return x

mats = []
for m in re.finditer(r'(\w+)\s*=\s*new Materials\(', src):
    var, start = m.group(1), m.end()
    depth, i = 1, start
    while depth:
        c = src[i]
        if c == '(': depth += 1
        elif c == ')': depth -= 1
        i += 1
    args = split_args(src[start:i-1])
    strs = [k for k, a in enumerate(args) if re.fullmatch(r'"[^"]*"', a)]
    if not strs: continue
    n = strs[0]
    rec = {'var': var, 'id': num(args[0]), 'name': args[n].strip('"'),
           'localName': args[n+1].strip('"') if n+1 < len(args) else None, 'rawArgs': args}
    pre = args[:n]
    # Full overload: id, iconSet, toolSpeed, durability, toolQuality, types, R, G, B, A, name, local, fuel, fuelPower, melt, blast, blastReq, transparent, oreValue, densMul, densDiv, color, ...
    if len(pre) >= 10:
        rec.update({'iconSet': pre[1].replace('TextureSet.', ''), 'toolSpeed': num(pre[2]), 'durability': num(pre[3]),
                    'toolQuality': num(pre[4]), 'types': num(pre[5]), 'rgba': [num(x) for x in pre[6:10]]})
        post = args[n+2:]
        keys = ['fuelType', 'fuelPower', 'meltingPoint', 'blastFurnaceTemp', 'blastFurnaceRequired', 'transparent',
                'oreValue', 'densityMultiplier', 'densityDivider', 'color']
        for k, v in zip(keys, post):
            rec[k] = num(v) if k != 'color' else v.replace('Dyes.', '')
        comp = re.findall(r'new MaterialStack\((\w+),\s*(\d+)\)', ','.join(post[10:]))
        if comp: rec['composition'] = [[c, int(a)] for c, a in comp]
    mats.append(rec)

# Newer builder-style definitions: X = new MaterialBuilder(id, TextureSet.SET_x, "Name")...constructMaterial();
for m in re.finditer(r'(\w+)\s*=\s*new MaterialBuilder\(\s*(\d+)\s*,\s*TextureSet\.(\w+)\s*,\s*"([^"]+)"\)(.*?)\.constructMaterial\(\)', src, re.S):
    var, mid, iset, name, chain = m.groups()
    rec = {'var': var, 'id': int(mid), 'name': name, 'iconSet': iset, 'builder': True, 'rawChain': ' '.join(chain.split())}
    rgb = re.search(r'setRGB\((\d+),\s*(\d+),\s*(\d+)\)', chain)
    if rgb: rec['rgba'] = [int(x) for x in rgb.groups()] + [0]
    comp = re.findall(r'new MaterialStack\((\w+),\s*(\d+)\)', chain)
    if comp: rec['composition'] = [[c, int(a)] for c, a in comp]
    mats.append(rec)

json.dump(mats, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(f'{len(mats)} materials, {sum(1 for x in mats if "rgba" in x)} with full stats')
