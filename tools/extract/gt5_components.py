#!/usr/bin/env python3
"""Extract GT5 component whitelists (OrePrefixes static init: 'enableUnusedX' = false in the IIS MaterialComponents.cfg).
A prefix listed here is generated for a material only if the material is whitelisted
(or, where 'toolBypass' is true, if the material is tool-capable: mTypes & 0x40).
Usage: gt5_components.py <OrePrefixes.java> <out.json>"""
import json, re, sys
src = open(sys.argv[1], encoding='utf-8').read()
start = src.index('for (Materials aMaterial : Materials.values()) {', src.index('enableUnusedGems'))
end = src.index('for (IMaterialHandler aRegistrator', start)
block = src[start:end]
out = {}
for m in re.finditer(r'if \(!enableUnused\w+(.*?)\)\s*\n?\s*(\w+)\.mDisabledItems\.add\(aMaterial\);', block, re.S):
    cond, prefix = m.group(1), m.group(2)
    if prefix in out:
        continue
    out[prefix] = {'toolBypass': '0x40' in cond, 'whitelist': sorted(set(re.findall(r'Materials\.(\w+)', cond)))}
json.dump(out, open(sys.argv[2], 'w'), indent=1, sort_keys=True)
print({k: len(v['whitelist']) for k, v in out.items()})
