"""Bio simple start (D155): up to LV the path runs on four fluids and without vessels; the full v0.7 depth starts at MV.

Owner's answers of 16.11: four fluids (water, the colony's nutrition N0, the growth medium G0, runoff); no vessels up to LV (organs on
the colony's tissue take nutrition and medium themselves); one mode for everybody (the old complex recipes below MV leave the game);
the organs that exist for the complex fluids move to MV.

`apply(b1)` rewrites the tables of bio_b1 after bio_b2.extend and before they are written. The v0.7 recipes stay readable in bio_b1.py;
every departure from them is listed here. All numbers are the AI's draft (the owner's mandate of 11.11), recorded in D155.

The rule of the two media:
  G0 - everything up to LV and the way into MV (the reinforced shell, the nutrient gland);
  G1 - MV: only the gland makes N1, E1 and G1, so whatever is grown on G1 (vessels, the pump, the valve, the membrane filter,
       the other MV organs) cannot be had before the gland.
"""

# LV and gland recipes feed on the colony's N0 while they work, mB/t (the bed takes 0.010)
UPKEEP_LV = 0.020
FED = ('nest', 'loosener', 'compactor', 'gland')

# left the game: hand mixing of the MV media (only the gland makes them), the old G0 and N0 mixes of five fluids,
# the bed versions of the pump, the gland and the membrane filter (they were the LV shortcut to vessels)
DROP = {'l11_n1', 'l12_e1', 'l13_g1', 'l09_n0', 'l10_g0', 'g_n0', 'g_g0', 'a07_bed', 'f06_bed', 'f09_bed'}
# stay on G1 = MV: vessels, the valve, the pump, the membrane filter, the MV organs other than the gland
ON_G1 = {'v_working', 'v_narrow', 'check_valve', 'a07_nest', 'f09_nest', 'ore_stomach', 'crossbreeder', 'incubator', 'isolator'}
# ULV vessels were grown in the bed on G0: now in the nest on G1 like the rest of the vessels
TO_NEST = {'v_capillary', 'v_microchannel'}

TEXT = {
    'measuring_vessel': (['Growth medium G0: 600 mB of the colony\'s N0, binding gel and bone powder.', 'From MV: suspension and emulsion for the gland.'],
                         ['Ростовая среда G0: 600 мБ N0 колонии, связующий гель и костный порошок.', 'С MV: взвесь и эмульсия для железы.']),
    'stone_press': (['From MV: plant extract for the nutrient gland.'], ['С MV: растительный экстракт для питательной железы.']),
    'filter_vessel': (['From MV: purified water W0 for the nutrient gland. Filter pad in the tool slot.'],
                      ['С MV: очищенная вода W0 для питательной железы. Прокладка — в слот инструмента.']),
    'hand_substrate': (['Place against the colony tissue: it feeds on the colony N0 and takes N0 and G0 from the colony itself.',
                        'Soft tissue, membranes, the first growth bed.'],
                       ['Поставьте вплотную к ткани колонии: питается её N0, сама берёт N0 и G0 из запасов колонии.', 'Мягкая ткань, мембраны, первое ростовое ложе.']),
    'growth_bed': (['Place against the colony tissue. Grows tissues, the feeding maw and the forming nest.',
                    'Takes G0 from the living tanks of the colony itself (or pour it in with a ladle).'],
                   ['Ставится вплотную к ткани колонии. Выращивает ткани, пасть-кормушку и формовочное гнездо.',
                    'Сама берёт G0 из живых резервуаров колонии (или налейте ковшом).']),
    'forming_nest': (['LV organ: grows LV parts and organs on G0, MV parts on G1.', 'Place against the colony tissue: feeds on its N0 (0.020 mB/t while working), takes the medium itself.'],
                     ['LV-орган: растит LV-детали и органы на G0, части MV — на G1.', 'Ставится вплотную к ткани колонии: питается её N0 (0,020 мБ/т в работе), среду берёт сам.']),
    'nutrient_gland': (['The first MV organ: N1 in 20 s, E1 in 30 s, G1 in 40 s. Nothing else makes them.', 'Place against the colony tissue: feeds on its N0.'],
                       ['Первый орган MV: N1 за 20 с, E1 за 30 с, G1 за 40 с. Больше их ничто не делает.', 'Ставится вплотную к ткани колонии: питается её N0.']),
    'membrane_filter': (['MV organ: purified water W0 in 20 s. Filter pad in the tool slot.', 'Body buffer 250 N1, 0.005 mB/t.'],
                        ['MV-орган: очищенная вода W0 за 20 с. Прокладка — в слот инструмента.', 'Буфер корпуса 250 N1, 0,005 мБ/т.']),
    'living_tank': (['LV, 8000 mB. The colony\'s store: places and organs on its tissue take what their recipe needs from it themselves.',
                     'Fill it with a ladle. With vessels (MV): sneak + right-click switches receive / supply.'],
                    ['LV, 8000 мБ. Запас колонии: места и органы на её ткани сами берут из него то, что нужно рецепту.',
                     'Наполняется ковшом. С сосудами (MV): Shift + ПКМ переключает приём / выдачу.']),
    'loosener': (['LV organ: ore -> 2 crushed ore. Place against the colony tissue: feeds on its N0.'], ['LV-орган: руда -> 2 дроблёной руды. Ставится вплотную к ткани колонии: питается её N0.']),
    'compactor': (['LV organ: chitin plates and the reinforced shell, on G0. Place against the colony tissue.'],
                  ['LV-орган: хитиновые пластины и армированная оболочка, на G0. Ставится вплотную к ткани колонии.']),
    'check_valve': (['MV: lets fluid through only the way you looked when placing it.', 'Opens at 0.002 atm, up to 128 mB/t.'],
                    ['MV: пропускает только туда, куда вы смотрели при установке.', 'Открывается от 0,002 атм, до 128 мБ/т.']),
    'manual_clamp': (['For vessels (MV), non-living: right-click — tighten (5 steps up to closed).', 'Sneak + right-click — show the network.'],
                     ['Для сосудов (MV), неживой: ПКМ — пережать сильнее (5 ступеней до закрытия).', 'Shift + ПКМ — показать сеть.']),
    'muscle_pump': (['MV: takes from the back, pushes into vessels in front (where you looked). Up to 0.200 atm, 128 mB/t.',
                     'Buffers: 250 N1 (0.010 mB/t always) and 250 E1 (work).'],
                    ['MV: берёт сзади, подаёт в сосуды спереди (куда вы смотрели). До 0,200 атм, 128 мБ/т.', 'Буферы: 250 N1 (0,010 мБ/т всегда) и 250 E1 (работа).']),
    'tissue_microchannel': (['Vessel (from MV), low pressure: 4 mB/t, 10 mB. Lives on colony N0.'], ['Сосуд (с MV), низкое давление: 4 мБ/т, 10 мБ. Живёт на N0 колонии.']),
    'capillary_bundle': (['Vessel (from MV), low pressure: 16 mB/t, 40 mB. Lives on colony N0.'], ['Сосуд (с MV), низкое давление: 16 мБ/т, 40 мБ. Живёт на N0 колонии.']),
    'narrow_fibrous_vessel': (['Vessel (from MV): 32 mB/t, 100 mB. Lives on colony N0.'], ['Сосуд (с MV): 32 мБ/т, 100 мБ. Живёт на N0 колонии.']),
    'working_fibrous_vessel': (['Vessel (from MV): 128 mB/t, 400 mB. Lives on colony N0.'], ['Сосуд (с MV): 128 мБ/т, 400 мБ. Живёт на N0 колонии.']),
}
GUI = {
    'jei.iis.bio.gland': ('Nutrient Gland (MV)', 'Питательная железа (MV)'),
    'jei.iis.bio.membrane_filter': ('Membrane Filter (MV)', 'Мембранный фильтр (MV)'),
    'gui.iis.bio.status.no_nutrient': ('No nutrition: the colony is out of N0 (or the organ buffer is empty)', 'Нет питания: у колонии кончился N0 (или пуст буфер органа)'),
}


def _swap(fluids, old, new, amount=None):
    for f in fluids:
        if f['fluid'] == old:
            f['fluid'] = new
            if amount is not None:
                f['amount'] = amount


def apply(b1):
    I, F, R = b1.I, b1.F, b1.R
    out, stats = [], {'dropped': 0, 'to_g0': 0, 'fed': 0}
    for r in b1.RECIPES:
        rid, st = r['id'], r['station']
        if rid in DROP:
            stats['dropped'] += 1
            continue
        # the growth fraction is gone: the pocket gives nutrition and runoff only; the first tissue grows on the colony's N0,
        # a seed is renewed on water
        r['out_fluids'] = [f for f in r['out_fluids'] if f['fluid'] != 'primary_fraction']
        if rid == 'b12_tissue':
            _swap(r['fluids'], 'primary_fraction', 'n0')
        if rid == 'p_renew':
            _swap(r['fluids'], 'primary_fraction', 'water', 250)
        if rid in TO_NEST:
            r['station'] = st = 'nest'
            _swap(r['fluids'], 'g0', 'g1')
        # the nest and the compactor work on G0 below MV
        if st == 'nest' and rid not in ON_G1 and rid not in TO_NEST:
            if any(f['fluid'] == 'g1' for f in r['fluids']):
                stats['to_g0'] += 1
            _swap(r['fluids'], 'g1', 'g0')
        if st == 'compactor':
            _swap(r['fluids'], 'binder', 'g0')
        # the gland is the first MV organ: it takes a reinforced shell
        if rid == 'f06_nest':
            r['items'].append(I('reinforced_shell'))
            r['spec'] = 'F06; D155: первый орган MV'
        if st in FED:
            r['upkeep'] = UPKEEP_LV
            stats['fed'] += 1
        elif 'upkeep' in r and st == 'nest':
            del r['upkeep']
        out.append(r)
    # the growth medium of the simple start: the colony's nutrition and two solid additives
    out.append(R('s_g0', 'mixer', 40, [I('binding_gel'), I('bone_powder')], [F('n0', 600)], out_fluids=[F('g0', 1000)], spec='D155 простой старт'))
    b1.RECIPES[:] = out
    assert not any(f['fluid'] == 'primary_fraction' for r in out for f in r['fluids'] + r['out_fluids'])
    for bid, (ten, tru) in TEXT.items():
        en, ru = b1.BLOCKS[bid][:2]
        b1.BLOCKS[bid] = (en, ru, ten, tru)
    b1.GUI.update(GUI)
    return stats
