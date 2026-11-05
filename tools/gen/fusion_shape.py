"""Writes ru/iis/multi/block/FusionShape.java from GT_MetaTileEntity_FusionComputer#checkMachine (D116): the ring has too many cells to retype by hand.

Usage: python -I tools/gen/fusion_shape.py <path to GT_MetaTileEntity_FusionComputer.java>
"""
import os
import re
import sys

src = open(sys.argv[1], encoding='utf-8').read()
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def body(name):
    i = src.index(name)
    i = src.index('{', i)
    depth, j = 0, i
    while True:
        if src[j] == '{':
            depth += 1
        elif src[j] == '}':
            depth -= 1
            if depth == 0:
                return src[i:j]
        j += 1


def num(s):
    s = s.replace(' ', '')
    return int(s) if s else 0


def cells(text, call, x, y, z):
    pat = rf'{call}\({x}\s*([+-]\s*\d+)?\s*,\s*{y}\s*([+-]\s*\d+)?\s*,\s*{z}\s*([+-]\s*\d+)?\s*[,)]'
    return sorted({(num(m[1] or ''), num(m[2] or ''), num(m[3] or '')) for m in re.finditer(pat, text)})


check = body('public boolean checkMachine')
coils = cells(body('private boolean checkCoils'), 'isFusionCoil', 'aX', 'aY', 'aZ')
hulls = cells(body('private boolean checkHulls'), 'isAdvancedMachineCasing', 'aX', 'aY', 'aZ')
upper = cells(body('private boolean checkUpperOrLowerHulls'), 'isAdvancedMachineCasing', 'aX', 'aY', 'aZ')
energy = cells(check, 'addIfEnergyInjector', 'xCenter', 'yCenter', 'zCenter')
out = cells(check, 'addIfExtractor', 'xCenter', 'yCenter', 'zCenter')
inp = cells(check, 'addIfInjector', 'xCenter', 'yCenter', 'zCenter')
four = [(5, 0, 0), (-5, 0, 0), (0, 0, 5), (0, 0, -5)]
casing = sorted((set(hulls) | {(x, 1, z) for x, _, z in upper} | {(x, -1, z) for x, _, z in upper} | set(four)) - set(energy) - set(out) - set(inp))
print(len(coils), 'coils', len(casing), 'casings', len(energy), 'energy', len(out), 'output', len(inp), 'input')


def table(name, rows, doc):
    flat = ', '.join(f'{{{x}, {y}, {z}}}' for x, y, z in rows)
    return f'    /** {doc} */\n    public static final int[][] {name} = {{{flat}}};\n'


text = ('package ru.iis.multi.block;\n\n'
        '/** Форма термоядерного реактора: клетки относительно центра кольца {x, y, z}. Создан tools/gen/fusion_shape.py из GT_MetaTileEntity_FusionComputer#checkMachine. */\n'
        'public final class FusionShape {\n    private FusionShape() {}\n\n'
        + table('COILS', coils, 'Катушки.') + table('CASINGS', casing, 'Только корпус (одна из клеток ±5 — сам контроллер).')
        + table('ENERGY', energy, 'Корпус или люк энергии.') + table('OUTPUT', out, 'Корпус или выходной люк.') + table('INPUT', inp, 'Корпус или входной люк.') + '}\n')
with open(os.path.join(ROOT, 'src', 'main', 'java', 'ru', 'iis', 'multi', 'block', 'FusionShape.java'), 'w', encoding='utf-8', newline='\n') as f:
    f.write(text)
