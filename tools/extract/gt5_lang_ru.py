#!/usr/bin/env python3
"""Extract Russian material names (S:Material.<name>=...) from the IIS pack's GregTech.lang.ru.
Usage: gt5_lang_ru.py <GregTech.lang.ru> <out.json>"""
import json, re, sys
out, ores, metaitems, tools = {}, {}, {}, {}
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    m = re.match(r'\s*S:"?Material\.([^"=]+)"?=(.*)$', line.rstrip('\r\n'))
    if m: out[m.group(1)] = m.group(2).strip()
    o = re.match(r'\s*S:gt\.blockores\.(\d+)\.name=(.*)$', line.rstrip('\r\n'))
    if o and int(o.group(1)) < 1000 and '%material' not in o.group(2): ores[o.group(1)] = o.group(2).strip()
    mi = re.match(r'\s*S:gt\.metaitem\.(0[12])\.(\d+)\.(name|tooltip)=(.*)$', line.rstrip('\r\n'))
    if mi: metaitems.setdefault(f'{mi.group(1)}.{mi.group(2)}', {})[mi.group(3)] = mi.group(4).strip()
    mt = re.match(r'\s*S:gt\.metatool\.01\.(\d+)\.name=(.*)$', line.rstrip('\r\n'))
    if mt: tools[mt.group(1)] = mt.group(2).strip()
out['__ore_names__'] = ores
out['__metaitems__'] = metaitems
out['__tools__'] = tools
json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
print(len(out), 'names')
