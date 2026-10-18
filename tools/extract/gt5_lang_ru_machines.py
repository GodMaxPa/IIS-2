#!/usr/bin/env python3
"""Extract Russian names of steam-age machines, pipes, casings and machine descriptions from the IIS pack's GregTech.lang.ru.
Usage: gt5_lang_ru_machines.py <GregTech.lang.ru> <out.json>"""
import json, re, sys
KEEP = re.compile(r'^(gt\.blockmachines\.(boiler|bronzemachine|steelmachine|hull|gt_pipe|multimachine\.brickedblastfurnace)[^=]*'
                  r'|TileEntity_DESCRIPTION_1[0-3][0-9]_Index_\d+|gt\.blockcasings4\.15\.name|gt\.metaitem\.01\.3211[01]\.name|fluid\.steam)$')
out = {}
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    m = re.match(r'\s*S:"?([^"=]+)"?=(.*)$', line.rstrip('\r\n'))
    if m and KEEP.match(m.group(1)):
        out[m.group(1)] = m.group(2).strip()
json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
print(len(out), 'strings')
