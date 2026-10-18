#!/usr/bin/env python3
"""Extract final material properties used by the IIS pack (config/GregTech/MaterialProperties.cfg).
These override GT5-II code defaults: direct smelting, ore/smelting multipliers, byproducts, sub tags, types bitmask, EBF temp...
Usage: gt5_material_props.py <MaterialProperties.cfg> <out.json>"""
import json, re, sys
stack, cur, out = [], None, {}
for raw in open(sys.argv[1], encoding='utf-8', errors='replace'):
    t = raw.strip()
    if t.endswith('{'):
        stack.append(t[:-1].strip()); cur = {}
    elif t == '}':
        if stack:
            section = stack.pop()
            # key = config section name = GT internal material name in lower case (e.g. "brownlimonite")
            if cur and 'MaterialName' in cur and len(stack) >= 1 and stack[0] == 'materials' and stack[-1] != 'custom':
                out[section] = cur
        cur = {}
    elif '=' in t and cur is not None and ':' in t:
        k, v = t.split('=', 1)
        typ, key = k.split(':', 1)
        key = key.strip('"').split('_', 1)[0]
        if typ == 'I': v = int(v)
        elif typ == 'D': v = float(v)
        elif typ == 'B': v = (v == 'true')
        elif key.startswith('List'): v = [x for x in v.split(',') if x]
        cur[key] = v
json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
print(len(out), 'materials')
