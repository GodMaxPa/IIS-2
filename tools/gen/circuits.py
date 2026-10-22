"""Programmed circuit of GT5 (GT_IntegratedCircuit_Item, D75): item models per configuration, crafting, lang.

GT textures: tools/gen/textures/circuits/<0..24>.png (gregtech:gt.integrated_circuit/<n>, IdealIndustrial/GT5Unofficial@00021e3).
Crafting as in GT: 1 basic circuit -> circuit 0 (shapeless); circuit + screwdriver in one of 24 positions -> configuration 1..24.
Java side: ru.iis.lv.item.ProgrammedCircuitItem, GtRecipe#circuit.

Standalone run: python -I tools/gen/circuits.py (from the repo root).
"""
import json
import os
import shutil

NS = 'iis'
HERE = os.path.dirname(os.path.abspath(__file__))

# GT_IntegratedCircuit_Item: screwdriver 'd' around circuit 'P' (rows of the 3x3 grid)
E = '   '
PATTERNS = {
    1: ['d  ', ' P ', E], 2: [' d ', ' P ', E], 3: ['  d', ' P ', E], 4: [E, ' Pd', E], 5: [E, ' P ', '  d'], 6: [E, ' P ', ' d '],
    7: [E, ' P ', 'd  '], 8: [E, 'dP ', E], 9: ['P d', E, E], 10: ['P  ', '  d', E], 11: ['P  ', E, '  d'], 12: ['P  ', E, ' d '],
    13: ['  P', E, '  d'], 14: ['  P', E, ' d '], 15: ['  P', E, 'd  '], 16: ['  P', 'd  ', E], 17: [E, E, 'd P'], 18: [E, 'd  ', '  P'],
    19: ['d  ', E, '  P'], 20: [' d ', E, '  P'], 21: ['d  ', E, 'P  '], 22: [' d ', E, 'P  '], 23: ['  d', E, 'P  '], 24: [E, '  d', 'P  '],
}


def generate(g):
    write, A, D, lang = g['write'], g['A'], g['D'], g['lang']

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    cases = []
    for n in range(25):
        dst = os.path.join(A, 'textures', 'item', 'circuit', f'{n}.png')
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy(os.path.join(HERE, 'textures', 'circuits', f'{n}.png'), dst)
        write(os.path.join(A, 'models', 'item', 'circuit', f'{n}.json'), {'parent': 'minecraft:item/generated',
                                                                          'textures': {'layer0': f'{NS}:item/circuit/{n}'}})
        if n:
            cases.append({'when': n, 'model': {'type': 'minecraft:model', 'model': f'{NS}:item/circuit/{n}'}})
    write(os.path.join(A, 'items', 'programmed_circuit.json'), {'model': {
        'type': 'minecraft:select', 'property': 'minecraft:component', 'component': f'{NS}:circuit_config', 'cases': cases,
        'fallback': {'type': 'minecraft:model', 'model': f'{NS}:item/circuit/0'}}})
    write(os.path.join(D, NS, 'recipe', 'lv', 'programmed_circuit.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ['#c:circuits/basic'],
        'result': {'id': f'{NS}:programmed_circuit', 'count': 1}})
    for n, pat in PATTERNS.items():
        write(os.path.join(D, NS, 'recipe', 'lv', f'programmed_circuit_{n}.json'), {
            'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pat,
            'key': {'P': f'{NS}:programmed_circuit', 'd': '#iis:tools/screwdriver'},
            'result': {'id': f'{NS}:programmed_circuit', 'count': 1, 'components': {f'{NS}:circuit_config': n}}})
    name('item.iis.programmed_circuit', 'Programmed Circuit', 'Программная схема')
    name('item.iis.programmed_circuit.numbered', 'Programmed Circuit (%s)', 'Программная схема (%s)')
    name('item.iis.programmed_circuit.config', 'Configuration: %s', 'Конфигурация: %s')
    name('item.iis.programmed_circuit.hint', 'Put it into a machine input: selects the recipe, is not consumed. Right-click +1, sneak −1 (screwdriver in inventory).',
         'Кладётся во вход машины: выбирает рецепт, не расходуется. ПКМ +1, Shift −1 (отвёртка в инвентаре).')
    name('message.iis.circuit.config', 'Circuit configuration: %s', 'Конфигурация схемы: %s')
    name('message.iis.circuit.need_screwdriver', 'You need a screwdriver in your inventory', 'Нужна отвёртка в инвентаре')
    name('jei.iis.circuit_not_consumed', 'Not consumed', 'Не расходуется')
    return {'circuit_recipes': len(PATTERNS) + 1}


def _merge_json(path, update):
    data = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    data.update(update)
    data = dict(sorted(data.items()))
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


if __name__ == '__main__':
    ROOT = os.getcwd()
    GEN = os.path.join(ROOT, 'src', 'generated', 'resources')

    def _write(path, obj):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
            f.write('\n')

    lang_ = {'en_us': {}, 'ru_ru': {}}
    A_, D_ = os.path.join(GEN, 'assets', NS), os.path.join(GEN, 'data')
    st = generate({'write': _write, 'A': A_, 'D': D_, 'lang': lang_})
    for code, entries in lang_.items():
        _merge_json(os.path.join(A_, 'lang', code + '.json'), entries)
    print('circuits:', st)
