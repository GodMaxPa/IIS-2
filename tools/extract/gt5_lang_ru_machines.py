#!/usr/bin/env python3
"""Extract Russian names of steam-age machines, pipes, casings and machine descriptions from the IIS pack's GregTech.lang.ru.
Usage: gt5_lang_ru_machines.py <GregTech.lang.ru> <out.json>"""
import json, re, sys
KEEP = re.compile(r'^(gt\.blockmachines\.[^=]*\.name|gt\.blockcasings[0-9]*\.\d+\.name'
                  r'|TileEntity_DESCRIPTION_\d+_Index_\d+|gt\.metaitem\.0[13]\.3?\d+\.(name|tooltip)|fluid\.[a-z._]+)$')
out = {}
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    m = re.match(r'\s*S:"?([^"=]+)"?=(.*)$', line.rstrip('\r\n'))
    if m and KEEP.match(m.group(1)):
        out[m.group(1)] = m.group(2).strip()
json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
print(len(out), 'strings')
