"""Bio draft B-2 (D148): the rest of LV, the first mutant lines, ore processing, storage and item transfer, a skeleton of MV and HV.

Owner's request of 12.11: "rough work, implement the ideas from our documentation"; answers: skeleton up to MV-HV, mutants as real
creatures, ores -> metals and storage/transfer, numbers at the AI's discretion (every number here is a DRAFT, not approved).
Sources: docs/bio_spec/IIS2_BIO_SPEC_v0.7.md §6 (ladder ULV-MAX: loosener and compactor on LV; crossbreeder, incubator, ore stomach,
culture isolator on MV; growth accelerator, biochemical reactor, solution separation on HV), §17.4-17.5 (controlled exposure; the product
of a mutant line is needed for the MV shells), §19.8 (four simple LV lines), §18.4 (Bio leaves waste: the sump).
The spec gives no recipes above LV: everything below is the AI's proposal built on the §22 materials.

`extend(b1, g)` adds to the tables of bio_b1 before it writes them; `generate(b1, g)` writes the models, textures and extra data after.
"""
import os

import numpy as np

NS = 'iis'
UP = 0.0   # LV+ organs feed on their own N1/E1 buffers, no colony upkeep

# id: (en, ru, tooltip en, tooltip ru, colour 1, colour 2, kind)
ITEMS = {
    'observation_probe': ('Observation Probe', 'Щуп наблюдения', ['Right-click an animal: records its baseline, shows its line and product.'],
                          ['ПКМ по животному: фиксирует исходное состояние, показывает линию и запас продукта.'], (220, 210, 190), (150, 110, 90), 'tool'),
    'collection_scraper': ('Collection Scraper', 'Сборный скребок', ['Right-click a working mutant line: collect its product (once per 5 min).'],
                           ['ПКМ по рабочей линии мутанта: собрать продукт (раз в 5 минут).'], (200, 190, 170), (120, 90, 70), 'tool'),
    'agent_pig_fleshy': ('Exposure Agent: Fleshy Pig', 'Агент воздействия: мясистая свинья',
                         ['Use on an observed adult pig near a vivarium pad. Its carcass gives more meat and sinew.'],
                         ['На наблюдённую взрослую свинью рядом с площадкой вивария. Туша даёт больше мяса и жил.'], (230, 140, 150), (150, 60, 80), 'vial'),
    'agent_cow_binding': ('Exposure Agent: Binding Cow', 'Агент воздействия: связующая корова',
                          ['Use on an observed adult cow near a vivarium pad. Product: binding secretion.'],
                          ['На наблюдённую взрослую корову рядом с площадкой вивария. Продукт: связующий секрет.'], (230, 200, 130), (160, 120, 60), 'vial'),
    'agent_sheep_longfiber': ('Exposure Agent: Long-Fibre Sheep', 'Агент воздействия: длинноволокнистая овца',
                              ['Use on an observed adult sheep near a vivarium pad. Product: long fibre.'],
                              ['На наблюдённую взрослую овцу рядом с площадкой вивария. Продукт: длинное волокно.'], (230, 230, 220), (150, 150, 140), 'vial'),
    'agent_chicken_shell': ('Exposure Agent: Dense-Shell Chicken', 'Агент воздействия: курица с плотной оболочкой',
                            ['Use on an observed adult chicken near a vivarium pad. Product: dense shell.'],
                            ['На наблюдённую взрослую курицу рядом с площадкой вивария. Продукт: плотная оболочка.'], (240, 230, 200), (170, 150, 110), 'vial'),
    'carcass_fleshy': ('Fleshy Pig Carcass', 'Туша мясистой свиньи', ['Butcher on a butcher block with a knife: far more meat and thick sinew.'],
                       ['Разделать на площадке ножом: гораздо больше мяса и толстые жилы.'], (220, 110, 120), (140, 50, 60), 'blob'),
    'thick_sinew': ('Thick Sinew', 'Толстая жила', ['Product of the fleshy pig line.'], ['Продукт мясистой линии свиньи.'], (220, 170, 150), (150, 100, 80), 'strand'),
    'binding_secretion': ('Binding Secretion', 'Связующий секрет', ['Product of the binding cow line: quick liquid binder, gel.'],
                          ['Продукт связующей линии коровы: быстрое жидкое связующее, гель.'], (240, 210, 140), (190, 150, 70), 'blob'),
    'long_fiber': ('Long Fibre', 'Длинное волокно', ['Product of the long-fibre sheep line: strong fibrous tissue.'],
                   ['Продукт длинноволокнистой линии овцы: прочная волокнистая ткань.'], (240, 240, 230), (180, 180, 170), 'strand'),
    'dense_shell': ('Dense Shell', 'Плотная оболочка', ['Product of the dense-shell chicken line: bone and mineral raw material (not chitin).'],
                    ['Продукт линии курицы с плотной оболочкой: костно-минеральное сырьё (не хитин).'], (240, 235, 215), (170, 160, 130), 'blob'),
    'bio_compost': ('Bio Compost', 'Биокомпост', ['From the waste sump. Crafts into bone meal.'], ['Из отходной ямы. На верстаке — костная мука.'],
                    (110, 86, 50), (70, 54, 30), 'blob'),
    'chitin_culture': ('Chitin Culture', 'Хитиновая культура', ['An independent shell-growing culture (LV).'], ['Самостоятельная оболочечная культура (LV).'],
                       (200, 170, 90), (120, 96, 40), 'blob'),
    'chitin_flake': ('Chitin Flake', 'Хитиновая чешуйка', [], [], (210, 180, 100), (140, 110, 50), 'plate'),
    'chitin_plate': ('Chitin Plate', 'Хитиновая пластина', ['Compacted flakes: shells of MV and HV organs.'], ['Уплотнённые чешуйки: оболочки органов MV и HV.'],
                     (190, 160, 80), (110, 86, 36), 'plate'),
    'reinforced_shell': ('Reinforced Shell', 'Армированная оболочка', ['MV: needs the product of any one mutant line.'],
                         ['MV: нужен продукт любой одной мутантной линии.'], (190, 110, 120), (110, 50, 70), 'plate'),
    'hybrid_seed': ('Hybrid Seed', 'Гибридный посев', ['From the crossbreeder: both parent seeds are spent.'], ['Из скрещивателя: оба родительских посева расходуются.'],
                    (200, 120, 200), (120, 50, 130), 'blob'),
    'enzyme_culture': ('Enzyme Culture', 'Ферментная культура', ['A stable culture from the isolator.'], ['Стабильная культура из изолятора.'],
                       (110, 210, 170), (50, 130, 100), 'blob'),
    'organ_embryo': ('Organ Embryo', 'Зародыш органа', ['Incubated from a hybrid seed: the start of every HV organ.'],
                     ['Инкубируется из гибридного посева: начало каждого HV-органа.'], (230, 150, 170), (150, 70, 100), 'blob'),
    'regulated_shell': ('Regulated Shell', 'Регулируемая оболочка', ['HV: the first ones grow in the MV incubator.'], ['HV: первые растут в MV-инкубаторе.'],
                        (150, 110, 190), (80, 50, 120), 'plate'),
    'mineral_binder': ('Mineral Binder', 'Минеральное связующее', ['HV: prepared for the first EV composite (not used yet).'],
                       ['HV: заготовка для первого EV-композита (пока не используется).'], (200, 200, 190), (130, 130, 120), 'blob'),
    'mineral_sediment': ('Mineral Sediment', 'Минеральный осадок', ['Settles out of runoff. Grinds into bone powder.'],
                         ['Осадок из стока. Перетирается в костный порошок.'], (170, 160, 140), (110, 100, 86), 'blob'),
}
# id: (en, ru, tooltip en, tooltip ru, side colours, top colours)
BLOCKS = {
    'waste_sump': ('Waste Sump', 'Отходная яма', ['Not alive. Runoff and residue turn into compost.'], ['Неживая. Сток и остатки превращаются в компост.'],
                   ((120, 120, 120), (90, 90, 90)), ((90, 74, 46), (60, 48, 30))),
    'loosener': ('Loosener', 'Разрыхлитель', ['LV organ: ore -> 2 crushed ore. Buffers 250 N1 + 250 E1.'], ['LV-орган: руда -> 2 дроблёной руды. Буферы 250 N1 + 250 E1.'],
                 ((170, 70, 70), (100, 30, 40)), ((210, 190, 170), (140, 120, 100))),
    'compactor': ('Compactor', 'Уплотнитель', ['LV organ: chitin plates and shells of the next tier.'], ['LV-орган: хитиновые пластины и оболочки следующего тира.'],
                  ((150, 60, 80), (90, 26, 46)), ((190, 160, 80), (120, 96, 40))),
    'ore_stomach': ('Ore Stomach', 'Рудный желудок', ['MV organ: crushed ore + digestive acid -> dust and tiny piles.'],
                    ['MV-орган: дроблёная руда + пищеварительная кислота -> пыль и крошечные кучки.'], ((160, 90, 60), (100, 46, 30)), ((184, 208, 64), (110, 130, 30))),
    'crossbreeder': ('Crossbreeder', 'Скрещиватель', ['MV organ: two seeds are spent, a hybrid seed comes out.'], ['MV-орган: два посева расходуются, выходит гибридный посев.'],
                     ((160, 70, 130), (90, 30, 80)), ((220, 150, 210), (140, 80, 140))),
    'incubator': ('Incubator', 'Инкубатор', ['MV organ: embryos; the second mode grows the first HV shells and organs.'],
                  ['MV-орган: зародыши; второй режим растит первые HV-оболочки и органы.'], ((180, 100, 110), (110, 50, 70)), ((240, 190, 200), (170, 110, 130))),
    'isolator': ('Culture Isolator', 'Изолятор культур', ['MV organ: digestive acid, enzyme cultures and solution, growth medium G2.'],
                 ['MV-орган: пищеварительная кислота, ферментные культуры и раствор, ростовая среда G2.'], ((120, 150, 130), (60, 90, 80)), ((150, 220, 190), (80, 140, 120))),
    'accelerator': ('Growth Accelerator', 'Ускоритель роста', ['HV organ: tissues and membranes several times faster.'], ['HV-орган: ткани и мембраны в несколько раз быстрее.'],
                    ((150, 90, 170), (80, 40, 110)), ((230, 170, 240), (150, 90, 170))),
    'bioreactor': ('Biochemical Reactor', 'Биохимический реактор', ['HV organ: enzyme leaching of ore (2 dusts per crushed ore), mineral binder.'],
                   ['HV-орган: выщелачивание руды ферментом (2 пыли с дроблёной руды), минеральное связующее.'], ((90, 140, 130), (40, 80, 76)), ((100, 210, 170), (50, 130, 100))),
    'separator': ('Solution Separator', 'Разделитель растворов', ['HV organ: runoff -> purified water and sediment.'], ['HV-орган: сток -> очищенная вода и осадок.'],
                  ((110, 120, 160), (60, 66, 100)), ((130, 180, 230), (70, 110, 160))),
    'living_store': ('Living Store', 'Живое хранилище', ['A sac of 27 slots.'], ['Мешок на 27 ячеек.'], ((170, 70, 90), (100, 30, 50)), ((220, 160, 110), (150, 80, 60))),
    'peristaltic_channel': ('Peristaltic Channel', 'Перистальтический канал',
                            ['Pushes items the way you looked when placing it: stores, organs, other channels. 4 items per half second.'],
                            ['Проталкивает предметы туда, куда вы смотрели при установке: хранилища, органы, каналы. 4 предмета за полсекунды.'],
                            ((176, 56, 72), (110, 26, 40)), ((230, 150, 150), (60, 14, 24))),
    'vivarium_pad': ('Vivarium Pad', 'Площадка вивария', ['Controlled exposure works within 4 blocks of a pad.'], ['Воздействие на животное проводится не дальше 4 блоков от площадки.'],
                     ((120, 80, 90), (70, 40, 50)), ((200, 120, 140), (130, 60, 90))),
}
STATION_NAMES = {'sump': ('Waste Sump', 'Отходная яма'), 'loosener': ('Loosener (LV)', 'Разрыхлитель (LV)'), 'compactor': ('Compactor (LV)', 'Уплотнитель (LV)'),
                 'ore_stomach': ('Ore Stomach (MV)', 'Рудный желудок (MV)'), 'crossbreeder': ('Crossbreeder (MV)', 'Скрещиватель (MV)'),
                 'incubator': ('Incubator (MV)', 'Инкубатор (MV)'), 'isolator': ('Culture Isolator (MV)', 'Изолятор культур (MV)'),
                 'accelerator': ('Growth Accelerator (HV)', 'Ускоритель роста (HV)'), 'bioreactor': ('Biochemical Reactor (HV)', 'Биохимический реактор (HV)'),
                 'separator': ('Solution Separator (HV)', 'Разделитель растворов (HV)')}
FLUIDS = {'digestive_acid': ('Digestive Acid', 'Пищеварительная кислота', (184, 208, 64)), 'acid_runoff': ('Acid Runoff', 'Кислый сток', (122, 138, 48)),
          'enzyme_solution': ('Enzyme Solution', 'Ферментный раствор', (96, 200, 160)), 'g2': ('Growth Medium G2', 'Ростовая среда G2', (128, 64, 160))}
LANG = {
    'gui.iis.bio.incubator.mode.0': ('Mode: embryos', 'Режим: зародыши'),
    'gui.iis.bio.incubator.mode.1': ('Mode: shells and organs', 'Режим: оболочки и органы'),
    'message.iis.bio.channel.empty': ('The channel is empty', 'Канал пуст'),
    'message.iis.bio.channel.holds': ('In the channel: %s x %s', 'В канале: %s x %s'),
    'message.iis.bio.mutant.baseline': ('%s: baseline recorded, no line', '%s: исходное состояние записано, линии нет'),
    'message.iis.bio.mutant.observe_first': ('Observe the animal with the probe first', 'Сначала наблюдение щупом'),
    'message.iis.bio.mutant.wrong_species': ('This agent is for another species', 'Этот агент — для другого вида'),
    'message.iis.bio.mutant.baby': ('Only adult animals', 'Только взрослые животные'),
    'message.iis.bio.mutant.already': ('This animal already has a line: exposure does not stack', 'У животного уже есть линия: воздействие не складывается'),
    'message.iis.bio.mutant.no_pad': ('No vivarium pad within 4 blocks', 'Нет площадки вивария в 4 блоках'),
    'message.iis.bio.mutant.exposed': ('Exposure done: the line shows in 2 minutes', 'Воздействие проведено: линия проявится через 2 минуты'),
    'message.iis.bio.mutant.manifesting': ('%s: manifesting, %s s left', '%s: проявляется, осталось %s с'),
    'message.iis.bio.mutant.working_carcass': ('%s: working line; its product is in the carcass (bio knife)', '%s: рабочая линия; продукт — в туше (нож Био)'),
    'message.iis.bio.mutant.regrowing': ('%s: product regrows, %s s left', '%s: продукт восстанавливается, осталось %s с'),
    'message.iis.bio.mutant.ready': ('%s: product is ready (scraper)', '%s: продукт готов (скребок)'),
    'entity.iis.mutant.pig_fleshy': ('Pig: fleshy line', 'Свинья: мясистая линия'),
    'entity.iis.mutant.cow_binding': ('Cow: binding secretion', 'Корова: связующая секреция'),
    'entity.iis.mutant.sheep_longfiber': ('Sheep: long-fibre line', 'Овца: длинноволокнистая линия'),
    'entity.iis.mutant.chicken_shell': ('Chicken: dense shell', 'Курица: плотная оболочка'),
}


def recipes(b1, g):
    I, O, F, R = b1.I, b1.O, b1.F, b1.R
    KNIFE, MANDREL = b1.KNIFE, b1.MANDREL
    out = [
        # --- отходная яма (§18.4: сбор отходов Био) ---------------------------------------------------------------------------
        R('sump_residue', 'sump', 60, [I('organic_residue')], [F('runoff', 1000)], [O('bio_compost')], spec='D148 черновик'),
        R('sump_plant', 'sump', 60, [I('plant_residue')], [F('runoff', 1000)], [O('bio_compost')], spec='D148 черновик'),
        R('sump_rotten', 'sump', 60, [I('rotten_mass')], [F('runoff', 500)], [O('bio_compost')], spec='D148 черновик'),
        R('sump_acid', 'sump', 60, [I('bone_powder')], [F('acid_runoff', 1000)], [O('mineral_sediment')], spec='D148 черновик: нейтрализация кислого стока'),
        # --- инструменты наблюдения и сбора, продукты мутантных линий (§19.8) --------------------------------------------------
        R('probe', 'butcher', 20, [I('bone_rib'), I('sinew_cord'), I('living_membrane')], out_items=[O('observation_probe')], spec='D148'),
        R('scraper', 'butcher', 20, [I('bone_rib', 2), I('sinew_cord')], out_items=[O('collection_scraper')], spec='D148'),
        R('carcass_fleshy', 'butcher', 30, [I('carcass_fleshy')], out_items=[O('meat_mass', 8), O('connective_fiber', 4), O('thick_sinew', 2), O('bone_fragment', 2)],
          tool=KNIFE, spec='§19.8 pig_fleshy'),
        R('secretion_gel', 'mortar', 10, [I('binding_secretion')], out_items=[O('binding_gel')], spec='§19.8 cow_binding'),
        R('shell_powder', 'mortar', 10, [I('dense_shell')], out_items=[O('bone_powder', 3)], spec='§19.8 chicken_shell'),
        R('sediment_powder', 'mortar', 10, [I('mineral_sediment')], out_items=[O('bone_powder')], spec='D148'),
        R('secretion_binder', 'pot', 15, [I('binding_secretion', 2)], [F('water', 1000)], out_fluids=[F('binder', 1000)], heat=True, spec='§19.8 cow_binding'),
        # --- формовочное гнездо LV: агенты, хитин, новые органы и блоки ---------------------------------------------------------
        R('agent_pig', 'nest', 120, [I('soft_tissue'), I('meat_mass', 2), I('base_seed')], [F('g1', 100)], [O('agent_pig_fleshy')], spec='§17.4 агент'),
        R('agent_cow', 'nest', 120, [I('soft_tissue'), I('binding_gel', 2), I('base_seed')], [F('g1', 100)], [O('agent_cow_binding')], spec='§17.4 агент'),
        R('agent_sheep', 'nest', 120, [I('soft_tissue'), I('#minecraft:wool', 2), I('base_seed')], [F('g1', 100)], [O('agent_sheep_longfiber')], spec='§17.4 агент'),
        R('agent_chicken', 'nest', 120, [I('soft_tissue'), I('minecraft:egg', 2), I('base_seed')], [F('g1', 100)], [O('agent_chicken_shell')], spec='§17.4 агент'),
        R('long_fibrous', 'nest', 60, [I('soft_tissue'), I('long_fiber')], [F('g1', 50)], [O('fibrous_tissue', 2)], lineage=True, spec='§19.8 sheep_longfiber'),
        R('chitin_culture', 'nest', 240, [I('base_seed'), I('bone_powder', 2), I('soft_tissue')], [F('g1', 100)], [O('chitin_culture')], lineage=True,
          spec='§19 самостоятельная хитиновая культура (черновик)'),
        R('chitin_flake', 'nest', 120, [I('chitin_culture'), I('bone_powder', 2)], [F('g1', 100)], [O('chitin_flake', 6)], spec='D148 черновик'),
        R('vivarium_pad', 'nest', 120, [I('woven_frame'), I('soft_tissue', 2), I('living_membrane'), I('base_seed')], [F('g1', 100)], [O('vivarium_pad', 4)],
          lineage=True, spec='§17.4 виварий (черновик)'),
        R('living_store', 'nest', 180, [I('woven_frame', 2), I('living_membrane', 4), I('tissue_lining'), I('base_seed')], [F('g1', 150)], [O('living_store')],
          lineage=True, spec='D148 живое хранилище'),
        R('peristaltic_channel', 'nest', 90, [I('tissue_lining'), I('muscle_bundle'), I('bone_ring'), I('base_seed')], [F('g1', 50)], [O('peristaltic_channel', 4)],
          lineage=True, spec='D148 перенос предметов'),
        R('loosener', 'nest', 300, [I('woven_frame'), I('muscle_node', 2), I('bone_rib', 4), I('tissue_lining'), I('base_seed')], [F('g1', 250)], [O('loosener')],
          lineage=True, spec='§6 LV разрыхлитель'),
        R('compactor', 'nest', 300, [I('woven_frame', 2), I('muscle_node', 2), I('bone_ring', 2), I('tissue_lining'), I('base_seed')], [F('g1', 250)], [O('compactor')],
          lineage=True, spec='§6 LV уплотнитель'),
        # MV-органы: первый экземпляр следующего класса растёт на предыдущем уровне долгим процессом (§6)
        R('ore_stomach', 'nest', 600, [I('reinforced_shell', 2), I('muscle_node', 2), I('tissue_lining', 2), I('living_membrane', 2), I('bone_ring', 2), I('base_seed')],
          [F('g1', 500)], [O('ore_stomach')], lineage=True, spec='§6 MV рудный желудок'),
        R('crossbreeder', 'nest', 600, [I('reinforced_shell', 2), I('living_membrane', 4), I('muscle_node'), I('valve_petal', 2), I('base_seed')],
          [F('g1', 500)], [O('crossbreeder')], lineage=True, spec='§6 MV скрещиватель'),
        R('incubator', 'nest', 600, [I('reinforced_shell', 3), I('living_membrane', 4), I('tissue_lining', 2), I('muscle_node'), I('base_seed')],
          [F('g1', 500)], [O('incubator')], lineage=True, spec='§6 MV инкубатор'),
        R('isolator', 'nest', 300, [I('reinforced_shell'), I('living_membrane', 2), I('fine_filter_pad'), I('tissue_lining'), I('base_seed')],
          [F('g1', 250)], [O('isolator')], lineage=True, spec='§6 MV изолятор культур'),
        # --- уплотнитель LV -------------------------------------------------------------------------------------------------------
        R('chitin_plate', 'compactor', 40, [I('chitin_flake', 4)], [F('binder', 100)], [O('chitin_plate')], spec='D148 черновик'),
        # армированная оболочка MV: продукт любой одной мутантной линии (§17.5 LV -> MV; §19.8: не требуется владеть всеми четырьмя)
        R('shell_sheep', 'compactor', 120, [I('fibrous_tissue', 2), I('chitin_plate'), I('long_fiber', 2)], [F('binder', 250)], [O('reinforced_shell')],
          lineage=True, spec='§17.5 sheep_longfiber'),
        R('shell_cow', 'compactor', 120, [I('fibrous_tissue', 2), I('chitin_plate'), I('binding_secretion', 2)], [F('binder', 250)], [O('reinforced_shell')],
          lineage=True, spec='§17.5 cow_binding'),
        R('shell_chicken', 'compactor', 120, [I('fibrous_tissue', 2), I('chitin_plate'), I('dense_shell', 2)], [F('binder', 250)], [O('reinforced_shell')],
          lineage=True, spec='§17.5 chicken_shell'),
        R('shell_pig', 'compactor', 120, [I('fibrous_tissue', 2), I('chitin_plate'), I('thick_sinew', 2)], [F('binder', 250)], [O('reinforced_shell')],
          lineage=True, spec='§17.5 pig_fleshy'),
        # --- скрещиватель MV: оба родителя расходуются (§6) --------------------------------------------------------------------
        R('cross_muscle', 'crossbreeder', 300, [I('base_seed'), I('muscle_seed')], [F('g1', 250)], [O('hybrid_seed')], lineage=True, spec='§6 MV'),
        R('cross_chitin', 'crossbreeder', 300, [I('base_seed'), I('chitin_culture')], [F('g1', 250)], [O('hybrid_seed')], lineage=True, spec='§6 MV'),
        # --- изолятор культур MV ---------------------------------------------------------------------------------------------------
        R('acid', 'isolator', 60, [I('rotten_mass')], [F('hydrolysate', 500), F('extract', 500)], out_fluids=[F('digestive_acid', 1000)], spec='D148 черновик'),
        R('enzyme_culture', 'isolator', 180, [I('hybrid_seed'), I('organic_residue')], [F('n1', 500)], [O('enzyme_culture')], lineage=True,
          spec='§6 MV стабильная ферментная культура'),
        R('enzyme_solution', 'isolator', 120, [I('enzyme_culture')], [F('w0', 1000), F('n1', 250)], out_fluids=[F('enzyme_solution', 1000)], spec='D148 черновик'),
        R('g2', 'isolator', 60, [I('energy_paste')], [F('g1', 800), F('enzyme_solution', 200)], out_fluids=[F('g2', 1000)], spec='D148 черновик: среда MV'),
        # --- инкубатор MV: 0 зародыши, 1 первые HV-оболочки и органы -----------------------------------------------------------
        R('embryo', 'incubator', 240, [I('hybrid_seed'), I('soft_tissue', 2), I('living_membrane')], [F('g1', 250)], [O('organ_embryo')], lineage=True, spec='§6 MV'),
        R('regulated_shell', 'incubator', 480, [I('reinforced_shell', 2), I('organ_embryo'), I('chitin_plate', 2), I('enzyme_culture')], [F('g2', 250)],
          [O('regulated_shell')], mode=1, lineage=True, spec='§6 HV: первый экземпляр на MV'),
        R('accelerator', 'incubator', 900, [I('regulated_shell', 2), I('organ_embryo'), I('muscle_node', 2), I('tissue_lining', 2), I('living_membrane', 2)],
          [F('g2', 500)], [O('accelerator')], mode=1, lineage=True, spec='§6 HV ускоритель роста'),
        R('bioreactor', 'incubator', 900, [I('regulated_shell', 3), I('organ_embryo'), I('chitin_plate', 4), I('tissue_lining', 2), I('bone_ring', 4)],
          [F('g2', 500)], [O('bioreactor')], mode=1, lineage=True, spec='§6 HV биохимический реактор'),
        R('separator', 'incubator', 900, [I('regulated_shell', 2), I('organ_embryo'), I('fine_filter_pad', 2), I('living_membrane', 4)],
          [F('g2', 500)], [O('separator')], mode=1, lineage=True, spec='§6 HV разделение растворов'),
        # --- ускоритель роста HV ---------------------------------------------------------------------------------------------------
        R('acc_tissue', 'accelerator', 20, [I('growth_substrate'), I('#iis:bio/seeds')], [F('g2', 25)], [O('soft_tissue', 2)], lineage=True, spec='§6 HV'),
        R('acc_membrane', 'accelerator', 30, [I('soft_tissue')], [F('g2', 25)], [O('living_membrane')], tool=MANDREL, lineage=True, spec='§6 HV'),
        R('acc_lining', 'accelerator', 30, [I('living_membrane'), I('fibrous_tissue')], [F('g2', 25)], [O('tissue_lining', 2)], tool=MANDREL, lineage=True, spec='§6 HV'),
        R('acc_fibrous', 'accelerator', 30, [I('soft_tissue'), I('plant_fiber', 2)], [F('g2', 25)], [O('fibrous_tissue', 2)], lineage=True, spec='§6 HV'),
        # --- биохимический реактор и разделитель HV ------------------------------------------------------------------------------
        R('mineral_binder', 'bioreactor', 120, [I('bone_powder', 4), I('mineral_sediment', 2)], [F('enzyme_solution', 250), F('binder', 500)],
          [O('mineral_binder', 2)], spec='§6 HV: минеральное связующее для EV'),
        R('sep_runoff', 'separator', 40, fluids=[F('runoff', 1000)], out_items=[O('mineral_sediment')], out_fluids=[F('w0', 700), F('hydrolysate', 100)], spec='§6 HV'),
        R('sep_acid', 'separator', 40, [I('bone_powder')], [F('acid_runoff', 1000)], [O('mineral_sediment', 2)], [F('w0', 600)], spec='§6 HV'),
    ]
    # --- руды: разрыхлитель LV, рудный желудок MV, выщелачивание HV. Слиток — из пыли в обычной печи. ---------------------------------
    ref, mat_id = g['ref'], g['mat_id']
    ores = 0
    for n in sorted(g['names']):
        crushed, dust, mid = ref('crushed', n), ref('dust', n), mat_id(n)
        if not crushed or not dust or not crushed.startswith(NS + ':'):
            continue
        if f'{NS}:ores/{mid}' not in g['item_tags']:
            continue
        ores += 1
        out.append(R(f'loosen_{mid}', 'loosener', 30, [I(f'#{NS}:ores/{mid}')], out_items=[O(crushed, 2)], spec='§6 LV: руда -> 2 дроблёной (черновик)'))
        outs = [O(dust, 1)] + ([O(ref('tiny_dust', n), 3)] if ref('tiny_dust', n) else [])
        out.append(R(f'digest_{mid}', 'ore_stomach', 40, [I(crushed)], [F('digestive_acid', 250)], outs, [F('acid_runoff', 250)],
                     spec='§6 MV: 1 дроблёная -> пыль + 3 крошечные (черновик)'))
        out.append(R(f'leach_{mid}', 'bioreactor', 30, [I(crushed)], [F('enzyme_solution', 100), F('water', 500)], [O(dust, 2)], [F('acid_runoff', 200)],
                     spec='§6 HV: 1 дроблёная -> 2 пыли (черновик)'))
    return out, ores


def extend(b1, g):
    """Adds the B-2 tables to bio_b1 before generate_b1 writes them."""
    new, ores = recipes(b1, g)
    b1.RECIPES.extend(new)
    for iid, (en, ru, ten, tru, _c0, _c1, _kind) in ITEMS.items():
        b1.ITEMS[iid] = (en, ru, ten, tru)
    for bid, (en, ru, ten, tru, _s, _t) in BLOCKS.items():
        b1.BLOCKS[bid] = (en, ru, ten, tru)
    for fid, (en, ru, rgb) in FLUIDS.items():
        b1.FLUIDS[fid] = (en, ru)
        b1.FLUID_RGB[fid] = rgb
    for st, (en, ru) in STATION_NAMES.items():
        b1.GUI[f'jei.{NS}.bio.{st}'] = (en, ru)
    b1.GUI.update(LANG)
    b1.CRAFTING['waste_sump'] = (['C C', 'CPC', 'CCC'], {'C': 'minecraft:cobblestone', 'P': '#minecraft:planks'})
    old_art = b1.item_art

    def art(iid):
        if iid not in ITEMS:
            return old_art(iid)
        _en, _ru, _a, _b, c0, c1, kind = ITEMS[iid]
        seed = 300 + sorted(ITEMS).index(iid)
        if kind == 'strand':
            return b1._strands(c0, seed, n=4, twist=True)
        if kind == 'plate':
            return b1._draw(lambda d: (d.polygon([(3, 5), (12, 3), (13, 11), (4, 13)], fill=c0 + (255,), outline=c1 + (255,)),
                                       d.line([(5, 7), (11, 6)], fill=c1 + (255,)), d.line([(5, 10), (11, 9)], fill=c1 + (255,))))
        if kind == 'vial':
            return b1._draw(lambda d: (d.rectangle([6, 2, 9, 4], fill=(120, 90, 60, 255)), d.ellipse([3, 4, 12, 14], fill=c0 + (255,), outline=c1 + (255,)),
                                       d.ellipse([5, 8, 10, 13], fill=c1 + (255,))))
        if kind == 'tool':
            return b1._draw(lambda d: (d.line([(3, 13), (11, 4)], fill=c1 + (255,), width=2), d.ellipse([9, 2, 13, 6], fill=c0 + (255,), outline=c1 + (255,))))
        return b1._blob(c0, c1, seed)
    b1.item_art = art
    return {'recipes': len(new), 'ore_materials': ores}


def generate(b1, g):
    """Textures, models, block states and extra data of the B-2 blocks (after generate_b1)."""
    write, A, D, block_tags = g['write'], g['A'], g['D'], g['block_tags']

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    for k, (bid, (_en, _ru, _a, _b, side, top)) in enumerate(BLOCKS.items()):
        s = b1._tex(side[0], side[1], 400 + 2 * k)
        t = np.array(b1._tex(top[0], top[1], 401 + 2 * k))
        n = b1._noise(450 + k)
        t[np.abs(n - 0.5) < 0.05, :3] = [int(c * 0.6) for c in top[1]]
        from PIL import Image
        t = Image.fromarray(t, 'RGBA')
        if bid == 'peristaltic_channel':     # выход — тёмное устье со светлым кольцом
            a = np.array(s)
            yy, xx = np.mgrid[0:16, 0:16]
            rr = np.hypot(xx - 7.5, yy - 7.5)
            a[rr < 4.5, :3] = top[1]
            a[(rr >= 3.6) & (rr < 4.8), :3] = top[0]
            t = Image.fromarray(a, 'RGBA')
        s.save(tex(f'block/bio/{bid}_side.png'))
        t.save(tex(f'block/bio/{bid}_top.png'))
        st, tt = f'{NS}:block/bio/{bid}_side', f'{NS}:block/bio/{bid}_top'
        if bid == 'peristaltic_channel':
            write(os.path.join(A, 'models', 'block', f'bio/{bid}.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': st, 's': st, 'f': tt},
                  'elements': [{'from': [4, 4, 0], 'to': [12, 12, 16], 'faces': {'north': {'texture': '#f'}, 'south': {'texture': '#s'}, 'east': {'texture': '#s'},
                                                                                 'west': {'texture': '#s'}, 'up': {'texture': '#s'}, 'down': {'texture': '#s'}}}]})
            write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {f'facing={fc}': dict({'model': f'{NS}:block/bio/{bid}'}, **r) for fc, r in rot.items()}})
        elif bid == 'vivarium_pad':
            write(os.path.join(A, 'models', 'block', f'bio/{bid}.json'), {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'side': st, 'top': tt, 'bottom': st}})
            write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/bio/{bid}'}}})
        else:
            # орган: корпус с горловиной сверху
            els = [{'from': [1, 0, 1], 'to': [15, 11, 15], 'faces': {f: {'texture': '#s'} for f in ('north', 'south', 'east', 'west', 'down')} | {'up': {'texture': '#t'}}},
                   {'from': [4, 11, 4], 'to': [12, 14, 12], 'faces': {f: {'texture': '#s'} for f in ('north', 'south', 'east', 'west')} | {'up': {'texture': '#t'}}}]
            write(os.path.join(A, 'models', 'block', f'bio/{bid}.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': st, 's': st, 't': tt}, 'elements': els})
            write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/bio/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/{bid}'}})
    block_tags.setdefault('minecraft:mineable/pickaxe', []).append(f'{NS}:waste_sump')
    write(os.path.join(D, NS, 'recipe', 'bio', 'bone_meal_from_compost.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [f'{NS}:bio_compost'], 'result': {'id': 'minecraft:bone_meal', 'count': 2}})
    return {'blocks': len(BLOCKS), 'items': len(ITEMS)}
