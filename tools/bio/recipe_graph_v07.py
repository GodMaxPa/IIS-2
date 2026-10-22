"""Проверка рецептурного графа Био из спецификации владельца v0.7, §22 (ULV–LV): достижимость без замкнутых зависимостей.

Это проектная проверка, а не игровой код: количества не моделируются (только «можно ли получить хоть раз»), кроме стартового
посева. Запуск из корня репозитория:  python -I tools/bio/recipe_graph_v07.py

Режимы:
  full   — все рецепты §22, включая ручные/переходные обходы (так, как предлагает спецификация);
  strict — без переходных обходов (первый LV-орган на ULV-ложе, ручные N1/E1/G1 и т. п.): показывает, какие кольца эти обходы рвут.
Неопределённые в спецификации условия (как открыть «функцию сокращения», что такое «наблюдение оболочечного роста», где стоит
«ручная подложка», откуда туша) заданы как отдельные «допущения»; без них проверка показывает, что именно блокируется.
"""
import sys

# Стартовые ресурсы: ванильный мир без машин Кибера + то, что спецификация даёт на челноке (§22.2, §21.12).
VANILLA = {'planks', 'stick', 'cobblestone', 'flint', 'wheat', 'vegetable', 'raw_meat', 'bone', 'wool', 'water', 'coal',
           'adult_animal'}
SHUTTLE = {'core', 'pocket', 'seed_base', 'N0'}

# Допущения, которых в спецификации нет явно (каждое — решение владельца/разработчика).
ASSUMPTIONS = {
    'carcass_mechanic': 'туша взрослого животного получается разделкой на W01 ножом W02 (как получить тушу — не описано)',
    'manual_substrate': '«ручная подложка» для B12/B14/A01 — отдельное место (в списке W01–W11 его нет)',
    'func_contraction': 'функция «сокращение» для мышечного посева открывается наблюдением у колонии (механика не описана)',
    'obs_shell_growth': '«наблюдение оболочечного роста» для хитиновой культуры B22 (механика не описана)',
}

# (id, входы-расходники, требования-места/инструменты (не расходуются), выходы, переходный_обход)
R = []


def r(rid, inputs, needs, outputs, transitional=False):
    R.append((rid, set(inputs), set(needs), set(outputs), transitional))


# --- §22.3 ручные места -----------------------------------------------------------------------------------------
r('W01', ['planks', 'stick'], [], ['W01'])
r('W02', ['flint', 'stick'], [], ['W02'])
r('W03', ['cobblestone', 'stick'], [], ['W03'])
r('W04', ['cobblestone'], [], ['W04'])
r('W05', ['cobblestone', 'stick'], [], ['W05'])
r('W06', ['W04', 'stick'], [], ['W06'])
r('W07', ['plant_fiber', 'stick'], [], ['W07'])
r('W08', ['planks', 'plant_fiber', 'stick'], [], ['W08'])
r('W09', ['plant_fiber'], [], ['W09'])
r('W10', ['W04', 'W09', 'stick'], [], ['W10'])
r('W11', ['planks', 'coal'], [], ['W11'])
# --- §22.4 материалы ----------------------------------------------------------------------------------------------
r('carcass', ['adult_animal'], ['W01', 'W02', 'carcass_mechanic'], ['meat_mass', 'conn_fiber', 'bone_fragment'])
r('B01', ['raw_meat'], ['W03'], ['meat_mass'])
r('B02', ['vegetable'], ['W03'], ['plant_mass'])
r('B03', ['wheat'], ['W01', 'W02'], ['plant_fiber', 'plant_mass'])
r('B04', ['wool'], ['W01', 'W02'], ['animal_fiber'])
r('B05', ['conn_fiber'], ['W01'], ['cord'])
r('B06', ['conn_fiber', 'water', 'coal'], ['W04', 'W05'], ['gel', 'runoff'])
r('B06/F03', ['conn_fiber', 'water'], ['F03'], ['gel', 'runoff'])
r('B07', ['bone'], ['W01', 'W02'], ['bone_fragment'])
r('B08', ['bone_fragment'], ['W01', 'W02'], ['rib'])
r('B09', ['bone_fragment'], ['W03'], ['bone_powder'])
r('B10', ['plant_fiber', 'cord'], ['W01'], ['frame'])
r('B11', ['plant_mass', 'plant_fiber', 'gel'], ['W01'], ['substrate'])
r('B12', ['substrate', 'seed_base', 'PF'], ['manual_substrate', 'N0'], ['soft_tissue'])
r('B13', ['substrate', 'seed_base', 'G0'], ['A01'], ['soft_tissue'])
r('B14', ['soft_tissue', 'G0'], ['A01', 'W07'], ['membrane'])
r('B14/manual', ['soft_tissue', 'G0'], ['manual_substrate', 'W07'], ['membrane'], transitional=True)
for rid, extra, out in (('B15', ['soft_tissue', 'plant_fiber'], 'fibrous'), ('B16', ['soft_tissue', 'meat_mass', 'seed_muscle'], 'muscle_bundle'),
                        ('B17', ['muscle_bundle', 'cord', 'rib'], 'muscle_node'), ('B19', ['membrane', 'fibrous'], 'lining'),
                        ('B20', ['membrane', 'fibrous'], 'petals')):
    needs = ['A03'] + (['W07'] if rid in ('B19', 'B20') else [])
    r(rid, extra + ['G1'], needs, [out])
    r(rid + '/ULV', extra + ['G0'], ['A01'] + needs[1:], [out], transitional=True)
r('B18', ['rib', 'gel'], ['A04'], ['ring'])
r('B18/manual', ['rib', 'gel'], ['W07'], ['ring'], transitional=True)
r('B21', ['seed_base', 'substrate', 'G0'], ['A01'], ['working_culture'])
r('B22', ['working_culture', 'substrate', 'G1'], ['A03', 'obs_shell_growth'], ['chitin_culture'])
r('B23', ['chitin_culture', 'plant_mass', 'meat_mass', 'G1'], ['A06'], ['soft_chitin', 'chitin_culture'])
r('B24', ['soft_chitin', 'G1'], ['A06'], ['chitin_scale'])
r('B25', ['soft_chitin', 'gel'], ['A04'], ['chitin_plate'])
r('B26', ['chitin_plate', 'gel'], ['A04', 'W07'], ['chitin_shell'])
r('B27', ['plant_fiber', 'fibrous'], ['A04'], ['filter_pad'])
r('B27/manual', ['plant_fiber', 'fibrous'], ['W07'], ['filter_pad'], transitional=True)
r('B28', ['plant_mass', 'meat_mass', 'water'], ['pocket'], ['paste', 'runoff'])
r('B29', ['soft_tissue', 'G0'], ['core'], ['seed_base'])
r('B29/contraction', ['soft_tissue', 'G0'], ['core', 'func_contraction'], ['seed_muscle'])
r('seed_renewal', ['meat_mass', 'plant_mass', 'PF'], ['core', 'N0'], ['seed_base'])
# --- §22.5 жидкости -----------------------------------------------------------------------------------------------
r('L01', ['plant_mass', 'meat_mass', 'water'], ['pocket'], ['N0', 'PF', 'runoff'])
r('L02', ['plant_mass', 'water'], ['W08'], ['extract', 'runoff', 'plant_residue'])
r('L02/F01', ['plant_mass', 'water'], ['F01'], ['extract', 'runoff', 'plant_residue'])
r('L03', ['meat_mass', 'water'], ['pocket'], ['hydrolysate', 'runoff', 'organic_residue'])
r('L03/F02', ['meat_mass', 'water'], ['F02'], ['hydrolysate', 'runoff', 'organic_residue'])
r('L04', ['bone_powder', 'water'], ['W06'], ['suspension'])
r('L05', ['gel', 'water', 'coal'], ['W04', 'W05'], ['binder'])
r('L05/F03', ['gel', 'water'], ['F03'], ['binder'])
r('L06', ['hydrolysate', 'extract', 'paste'], ['W06'], ['emulsion', 'runoff'], transitional=True)
r('L06/F07', ['hydrolysate', 'extract', 'paste'], ['F07'], ['emulsion', 'runoff'])
r('L07', ['extract', 'paste'], ['pocket'], ['concentrate', 'runoff'], transitional=True)
r('L07/F08', ['extract', 'paste'], ['F08'], ['concentrate', 'runoff'])
r('L08', ['water'], ['W10'], ['W0', 'runoff'])
r('L08/F09', ['water'], ['F09'], ['W0', 'runoff'])
r('L09', ['water', 'extract', 'hydrolysate', 'suspension'], ['W06'], ['N0'])
r('L10', ['N0', 'hydrolysate', 'binder', 'suspension'], ['W06'], ['G0'])
for rid, ins, out in (('L11', ['W0', 'hydrolysate', 'extract', 'suspension', 'emulsion'], 'N1'), ('L12', ['N1', 'concentrate', 'emulsion'], 'E1'),
                      ('L13', ['N1', 'hydrolysate', 'binder', 'extract', 'suspension'], 'G1')):
    r(rid, ins, ['F06'], [out])
    r(rid + '/manual', ins, ['W06'], [out], transitional=True)
# --- §22.6 органы (каждый расходует посев) ------------------------------------------------------------------------------
ULV = {
    'A01': ['frame', 'substrate', 'membrane', 'rib', 'G0'],
    'A02': ['frame', 'soft_tissue', 'membrane', 'rib', 'G0'],
    'F01': ['frame', 'soft_tissue', 'membrane', 'cord', 'G0'],
    'F02': ['frame', 'soft_tissue', 'membrane', 'rib', 'G0'],
    'F04': ['frame', 'soft_tissue', 'membrane', 'rib', 'G0'],
    'F05': ['frame', 'membrane', 'soft_tissue', 'cord', 'G0'],
}
for oid, ins in ULV.items():
    r(oid, ins + ['seed_base'], ['manual_substrate', 'W07'] if oid == 'A01' else ['A01'], [oid])
LV = {
    'A03': ['frame', 'rib', 'membrane', 'muscle_node'],
    'A04': ['frame', 'rib', 'membrane', 'muscle_node'],
    'A05': ['frame', 'rib', 'lining', 'muscle_node'],
    'A07': ['frame', 'muscle_node', 'petals', 'lining', 'fibrous'],
    'F03': ['frame', 'fibrous', 'membrane', 'muscle_node'],
    'F06': ['frame', 'membrane', 'lining', 'muscle_node'],
    'F07': ['frame', 'fibrous', 'membrane', 'muscle_node'],
    'F08': ['frame', 'fibrous', 'petals', 'muscle_node'],
    'F09': ['frame', 'lining', 'membrane', 'filter_pad'],
}
for oid, ins in LV.items():
    r(oid, ins + ['G1', 'seed_base'], ['A03'], [oid])
    r(oid + '/ULV', ins + ['G0', 'seed_base'], ['A01'], [oid], transitional=True)
for oid, ins in {'A06': ['frame', 'membrane', 'lining', 'rib'], 'A08': ['frame', 'membrane', 'lining', 'fibrous'],
                 'A09': ['frame', 'membrane', 'muscle_node', 'lining'], 'A10': ['frame', 'membrane', 'lining', 'muscle_node'],
                 'A11': ['frame', 'membrane', 'lining'], 'A12': ['frame', 'membrane', 'cord', 'muscle_node']}.items():
    r(oid, ins + ['G1', 'seed_base'], ['A03'], [oid])
# --- §22.7–22.8 сосуды, плёнка, переносные резервуары ----------------------------------------------------------------
r('microchannel', ['soft_tissue', 'membrane', 'cord', 'G0', 'seed_base'], ['A01'], ['microchannel'])
r('vessel_fibrous_narrow', ['lining', 'fibrous', 'ring', 'G1', 'seed_base'], ['A03'], ['vessel_fibrous_narrow'])
r('vessel_fibrous_narrow/ULV', ['lining', 'fibrous', 'ring', 'G0', 'seed_base'], ['A01'], ['vessel_fibrous_narrow'], transitional=True)
r('vessel_chitin_narrow', ['lining', 'chitin_shell', 'G1', 'seed_base'], ['A03'], ['vessel_chitin_narrow'])
r('film', ['membrane', 'gel', 'coal'], ['W04', 'W05'], ['film'])
r('P01', ['film', 'cord', 'rib'], ['W01'], ['P01'])
r('P03', ['film', 'frame', 'ring', 'cord'], ['W01'], ['P03'])


def solve(allow_transitional=True, assumptions=frozenset(ASSUMPTIONS)):
    have = set(VANILLA) | set(SHUTTLE) | set(assumptions)
    stage = {k: 0 for k in have}
    via = {}
    step = 0
    changed = True
    while changed:
        changed = False
        step += 1
        new = {}
        for rid, ins, needs, outs, trans in R:
            if trans and not allow_transitional:
                continue
            if ins <= have and needs <= have:
                for o in outs:
                    if o not in have and o not in new:
                        new[o] = rid
        for o, rid in new.items():
            have.add(o)
            stage[o] = step
            via[o] = rid
            changed = True
    return have, stage, via


def report(title, allow_transitional, assumptions=frozenset(ASSUMPTIONS)):
    have, stage, via = solve(allow_transitional, assumptions)
    every = set()
    for _, ins, needs, outs, _ in R:
        every |= ins | needs | outs
    missing = sorted(x for x in every - have if x not in ASSUMPTIONS)
    print(f'== {title}: достижимо {len([x for x in have if x not in ASSUMPTIONS])}, недостижимо {len(missing)}')
    if missing:
        print('   недостижимо:', ', '.join(missing))
    return have, stage, via


if __name__ == '__main__':
    have, stage, via = report('full (все обходы и допущения)', True)
    print('   порядок получения ключевых узлов (шаг: узел ← рецепт):')
    for k in ('N0', 'PF', 'gel', 'frame', 'substrate', 'soft_tissue', 'G0', 'membrane', 'A01', 'A02', 'F02', 'F05', 'seed_muscle',
              'muscle_node', 'A03', 'A04', 'A07', 'N1', 'E1', 'G1', 'F06', 'A06', 'chitin_shell', 'P01', 'A10'):
        print(f'     {stage.get(k, "—"):>3}: {k:<22} ← {via.get(k, "старт")}')
    report('strict (без переходных обходов)', False)
    for a in ASSUMPTIONS:
        print(f'-- без допущения «{a}»: {ASSUMPTIONS[a]}')
        report('   ', True, frozenset(set(ASSUMPTIONS) - {a}))
    sys.exit(0)
