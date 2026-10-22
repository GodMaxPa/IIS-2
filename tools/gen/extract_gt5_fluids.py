"""One-off extraction of the GT5-II fluid list into reference/original/gt5_fluids.json (D72).

Sources (IdealIndustrial/GT5Unofficial @ 00021e3):
  gregtech/loaders/preload/GT_Loader_Item_Block_And_Fluid.java — explicit addFluid(name, local, material, state, tempK, ...)
    and the autogeneration loop (molten / plasma / corresponding fluid / corresponding gas / cracked);
  gregtech/common/GT_Proxy.java — addFluid states (0 solid, 1 liquid, 2 gas, 3 plasma, 4 molten), temperatures
    (molten = meltingPoint or 1000, plasma = 10000, cracked = 775 gas);
  gregtech/api/enums/Materials.java — setHasCorrespondingGas / setLiquidTemperature.
The set of fluid NAMES is the one the IIS pack actually registered: the fluid.* keys of GregTech.lang.ru
(reference/original/gt5_lang_ru_machines.json). Potion brews (potion.*) are left out (GT brewing is not in IIS 2 yet).

Usage: python -I tools/gen/extract_gt5_fluids.py <dir with ibf.java, materials.java>
"""
import json
import os
import re
import sys

ROOT = os.getcwd()
SRC = sys.argv[1]
ru = json.load(open(os.path.join(ROOT, 'reference', 'original', 'gt5_lang_ru_machines.json'), encoding='utf-8'))
mats = json.load(open(os.path.join(ROOT, 'reference', 'original', 'gt5_materials.json'), encoding='utf-8'))
by_name = {m['name'].lower(): m for m in mats}
ibf = open(os.path.join(SRC, 'ibf.java'), encoding='utf-8').read()
mj = open(os.path.join(SRC, 'materials.java'), encoding='utf-8').read()

explicit = {}
for name, local, mat, state, temp in re.findall(r'addFluid\("([^"]+)",\s*"([^"]*)",\s*([\w.]+|null),\s*(\d),\s*(\d+)', ibf):
    explicit[name.lower()] = (local, mat, int(state), int(temp))

gas_mats, liquid_temp = set(), {}
for m in re.finditer(r'(\w+)\s*=\s*new Materials\((.*?)\);', mj):
    var, body = m.group(1), m.group(2)
    if 'setHasCorrespondingGas(true)' in body:
        gas_mats.add(var.lower())
    lt = re.search(r'setLiquidTemperature\((\d+)\)', body)
    if lt:
        liquid_temp[var.lower()] = int(lt.group(1))
# setHasCorrespondingGas set later on existing materials
for var in re.findall(r'(\w+)\.setHasCorrespondingGas\(true\)', mj):
    gas_mats.add(var.lower())

out = []
for key, ru_name in sorted(ru.items()):
    if not key.startswith('fluid.'):
        continue
    name = key[6:]
    if name.startswith('potion.'):
        continue
    kind = name.split('.')[0] if '.' in name else ''
    base = name.split('.', 1)[1] if kind in ('molten', 'plasma', 'hydrocracked', 'steamcracked') else name
    mat = by_name.get(base.lower())
    rgba = mat['rgba'][:3] if mat and mat.get('rgba') else [200, 200, 200]
    if name in explicit:
        local, _, state, temp = explicit[name]
    elif kind == 'molten':
        state, temp, local = 4, (mat.get('meltingPoint', 0) if mat and mat.get('meltingPoint', 0) > 0 else 1000), 'Molten ' + ((mat.get('localName') or base) if mat else base)
    elif kind == 'plasma':
        state, temp, local = 3, 10000, ((mat.get('localName') or base) if mat else base) + ' Plasma'
    elif kind in ('hydrocracked', 'steamcracked'):
        state, temp = 2, 775
        local = ('Hydro-Cracked ' if kind == 'hydrocracked' else 'Steam-Cracked ') + ((mat.get('localName') or base) if mat else base)
    else:
        gas = base.lower() in gas_mats
        state = 2 if gas else 1
        temp = 295 if gas else liquid_temp.get(base.lower(), (mat.get('meltingPoint', 0) if mat and mat.get('meltingPoint', 0) > 0 else 295))
        local = mat['localName'] if mat and mat.get('localName') else base
    out.append({'id': name, 'en': local, 'ru': ru_name.strip(), 'state': state, 'temperature': temp, 'rgb': rgba})

with open(os.path.join(ROOT, 'reference', 'original', 'gt5_fluids.json'), 'w', encoding='utf-8') as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
    f.write('\n')
from collections import Counter
print(len(out), Counter(o['state'] for o in out))
