#!/usr/bin/env python3
"""Extract Russian material names (S:Material.<name>=...) from the IIS pack's GregTech.lang.ru.
Usage: gt5_lang_ru.py <GregTech.lang.ru> <out.json>"""
import json, re, sys
out, ores = {}, {}
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    m = re.match(r'\s*S:"?Material\.([^"=]+)"?=(.*)$', line.rstrip('\r\n'))
    if m: out[m.group(1)] = m.group(2).strip()
    o = re.match(r'\s*S:gt\.blockores\.(\d+)\.name=(.*)$', line.rstrip('\r\n'))
    if o and int(o.group(1)) < 1000 and '%material' not in o.group(2): ores[o.group(1)] = o.group(2).strip()
out['__ore_names__'] = ores
json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
print(len(out), 'names')
