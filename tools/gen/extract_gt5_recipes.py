"""Extract machine recipe calls from GT5 sources into reference/original/gt5_machine_recipes.json (D76).

Usage (from the repo root, GT5 = IdealIndustrial/GT5Unofficial@00021e3):
    python -I tools/gen/extract_gt5_recipes.py <path to GT_MachineRecipeLoader.java>

Each call of the listed GT_RecipeAdder methods becomes {"method", "line", "args": [raw Java expressions]}; commented-out
lines are skipped. Interpretation (materials, forms, cells, fluids, overloads) is done by tools/gen/chem.py, so the raw
data stays a faithful copy of the original.
"""
import json
import os
import re
import sys

METHODS = ('addChemicalRecipe', 'addChemicalRecipeForBasicMachineOnly', 'addElectrolyzerRecipe', 'addCentrifugeRecipe', 'addMixerRecipe',
           'addFluidExtractionRecipe', 'addFluidSolidifierRecipe', 'addFluidSmelterRecipe')


def strip_comments(src):
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith('//', i):
            j = src.find('\n', i)
            i = n if j < 0 else j
        elif src.startswith('/*', i):
            j = src.find('*/', i + 2)
            seg = src[i:(n if j < 0 else j + 2)]
            out.append('\n' * seg.count('\n'))
            i = n if j < 0 else j + 2
        elif src[i] == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == '\\' else 1
            out.append(src[i:j + 1])
            i = j + 1
        else:
            out.append(src[i])
            i += 1
    return ''.join(out)


def split_args(s):
    args, depth, cur = [], 0, []
    for ch in s:
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        if ch == ',' and depth == 0:
            args.append(''.join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if ''.join(cur).strip():
        args.append(''.join(cur).strip())
    return args


def extract(src):
    src = strip_comments(src)
    calls = []
    pat = re.compile(r'GT_Values\.RA\.(' + '|'.join(METHODS) + r')\s*\(')
    for m in pat.finditer(src):
        i, depth = m.end(), 1
        while i < len(src) and depth:
            depth += {'(': 1, ')': -1}.get(src[i], 0)
            i += 1
        body = src[m.end():i - 1]
        line = src.count('\n', 0, m.start()) + 1
        calls.append({'method': m.group(1), 'line': line, 'args': [re.sub(r'\s+', ' ', a) for a in split_args(body)]})
    return calls


if __name__ == '__main__':
    path = sys.argv[1]
    calls = extract(open(path, encoding='utf-8', errors='replace').read())
    dst = os.path.join('reference', 'original', 'gt5_machine_recipes.json')
    with open(dst, 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'source': 'IdealIndustrial/GT5Unofficial@00021e3 ' + os.path.basename(path), 'calls': calls}, f, ensure_ascii=False, indent=1)
        f.write('\n')
    from collections import Counter
    print(len(calls), Counter(c['method'] for c in calls))
