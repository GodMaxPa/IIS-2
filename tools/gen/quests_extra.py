"""Chapter "What else IIS 2 has" (D144, owner: "add the primitive mine digger and other cool things that are not in the quests").

Things of IIS 2 that the book of IIS 1 does not show, or shows too deep: columns are epochs (steam, LV, MV, HV, EV, IV). Like the weapons
chapter it depends on nothing in the book and nothing depends on it. Tools of any material are asked by their tag (FTB Filter System).
"""
import os

NS = 'iis'
BASE = 0x110000
CHAPTER = 0x1001
STEP_X, STEP_Y = 2.0, 1.5
PAY = {0: ('copper', 2), 1: ('copper', 3), 2: ('copper', 5), 3: ('cupronickel', 1), 4: ('cupronickel', 2), 5: ('cupronickel', 4)}

# key, column, row, items ('#tag' = any of the tag), deps, title (ru, en), text (ru, en)
Q = [
    ('start', -1.5, 3, [], [], ('Что ещё есть в ИИС 2', 'What else IIS 2 has'),
     ('Эта глава собирает то, чего нет в книге ИИС 1 или что спрятано глубоко. Столбцы — эпохи: пар, LV, MV, HV, EV, IV. Глава ничего не закрывает в основной книге.\n\nМир сам сохраняется в архив каждый игровой день (20 минут): папка iis-world-backups рядом с saves, хранятся 20 последних.',
      'This chapter gathers what the book of IIS 1 does not show or hides deep. Columns are epochs. It locks nothing in the main book.\n\nThe world is archived every game day (20 minutes) into iis-world-backups next to saves; the last 20 are kept.')),
    # --- steam -------------------------------------------------------------------------------------------------------------------
    ('reward_shop', 0, 0, ['reward_shop'], ['start'], ('Магазин наград', 'Reward shop'),
     ('Жетоны за квесты тратятся здесь. Еда продаётся сразу, сырьё — когда оно хоть раз побывало у вас в инвентаре.', 'Quest credits are spent here. Food is sold at once, raw materials once they have been in your inventory.')),
    ('tool_rack', 0, 1, ['bibliocraft:oak_tool_rack'], ['start'], ('Стойка для инструментов', 'Tool rack'),
     ('Стойка BiblioCraft держит любые инструменты GT, электроинструменты тоже. Есть из любого дерева.', 'The BiblioCraft rack holds any GT tool, electric ones too. Any wood will do.')),
    ('charcoal_pile', 0, 2, ['charcoal_pile'], ['start'], ('Угольная яма', 'Charcoal pile'),
     ('Уголь из брёвен в печи не получить, как в ИИС 1. Сложите брёвна кучей до 11×6×11 на кирпичи, обложите землёй со всех сторон и сверху, без пустот, поставьте запальник сверху и подайте сигнал. Брёвна станут ломким углём.',
      'Logs give no charcoal in a furnace, as in IIS 1. Stack logs up to 11x6x11 on bricks, cover them with dirt on all sides and on top, no gaps, put the igniter on top and power it. The logs turn into brittle charcoal.')),
    ('coke_oven', 0, 3, ['coke_oven'], ['charcoal_pile'], ('Коксовая печь', 'Coke oven'),
     ('Кокс из угля и креозот из брёвен без всякого электричества.', 'Coke from coal and creosote from logs, no electricity at all.')),
    ('primitive_mine_digger', 0, 4, ['primitive_mine_digger'], ['start'], ('Примитивный шахтокопатель', 'Primitive mine digger'),
     ('Копает шахту вниз сам, на паре. Вставьте в него буровые трубы.', 'Digs a shaft down by itself, on steam. Put mining pipes in it.')),
    ('boiler_solar_steel', 0, 5, ['boiler_solar_steel'], ['start'], ('Солнечный котёл высокого давления', 'High pressure solar boiler'),
     ('Втрое больше пара, чем у бронзового: 360 л/с, после накипи 120 л/с. Сломайте и поставьте заново, чтобы убрать накипь.', 'Three times the steam of the bronze one: 360 L/s, 120 L/s calcified. Break and replace to clean it.')),
    ('flint_tools', 0, 6, ['#iis:flint_tools'], ['start'], ('Кремнёвые инструменты', 'Flint tools'),
     ('Как каменные в ванили, а каменные теперь хуже.', 'As good as vanilla stone ones; stone ones are worse now.')),
    ('sense', 0.7, 6.7, ['#iis:tools/sense'], ['flint_tools'], ('Коса', 'Sense'),
     ('Срезает траву, посевы и листву вокруг одним взмахом.', 'Cuts grass, crops and leaves around in one swing.')),
    ('plow', -0.7, 6.7, ['#iis:tools/plow'], ['flint_tools'], ('Плуг', 'Plow'),
     ('ПКМ вспахивает землю 3×3.', 'Right click tills 3x3 of dirt.')),
    ('branch_cutter', 0.7, 7.4, ['#iis:tools/branch_cutter'], ['sense'], ('Сучкорез', 'Branch cutter'),
     ('С каждой срезанной листвы падает саженец.', 'Every leaf block cut gives a sapling.')),
    ('butchery_knife', -0.7, 7.4, ['#iis:tools/butchery_knife'], ['plow'], ('Разделочный нож', 'Butchery knife'),
     ('С существа, убитого им, падает вдвое больше.', 'A creature killed with it drops twice as much.')),
    ('universal_spade', 0, 8, ['#iis:tools/universal_spade'], ['branch_cutter', 'butchery_knife'], ('Универсальная лопата', 'Universal spade'),
     ('Лопата, пила и монтировка в одном.', 'A shovel, a saw and a crowbar in one.')),
    # --- LV ----------------------------------------------------------------------------------------------------------------------
    ('tree_farm', 1, 0, ['tree_farm'], ['charcoal_pile'], ('Автоферма дерева', 'Tree farm'),
     ('Многоблок 5×5×5: внизу поле земли 3×3 и люки, стенки из стекла, сверху каркасы ULV. Саженец и вода дают брёвна и новые саженцы, удобрение ускоряет вдвое. Брёвна — в угольную яму или пиролизную печь.',
      'A 5x5x5 multiblock: 3x3 of dirt and hatches below, glass walls, ULV casings on top. A sapling and water give logs and new saplings; fertilizer doubles the speed. Logs go to the charcoal pile or the pyrolyse oven.')),
    ('quantum_chest', 1, 1, ['quantum_chest_lv'], ['start'], ('Квантовый сундук', 'Quantum chest'),
     ('Огромный запас одного предмета в одном блоке.', 'A huge stock of one item in one block.')),
    ('electric_treetap', 1, 2, ['electric_treetap'], ['start'], ('Электрокраник', 'Electric treetap'),
     ('Краник на заряде: смола без износа.', 'A treetap on charge: resin without wear.')),
    ('electric_wrench', 1, 3, ['electric_wrench'], ['start'], ('Электроключ', 'Electric wrench'),
     ('Снимает машины и служит ключом в рецептах, тратит заряд вместо прочности.', 'Takes machines down and is a wrench in recipes, spending charge instead of durability.')),
    ('buzzsaw', 1, 4, ['#iis:tools/buzzsaw'], ['start'], ('Циркулярная пила', 'Buzzsaw'),
     ('Пила для верстака на заряде.', 'A crafting saw on charge.')),
    ('screwdriver_lv', 1, 5, ['#iis:tools/screwdriver_lv'], ['buzzsaw'], ('Шуруповёрт', 'Electric screwdriver'),
     ('Отвёртка для верстака на заряде.', 'A crafting screwdriver on charge.')),
    ('turrets', 1, 6, ['turret_base_t1', 'turret_disposable'], ['start'], ('Первая турель', 'First turret'),
     ('База держит энергию и патроны, турель ставится сверху. Одноразовая стреляет булыжником.', 'The base holds energy and ammunition, the turret goes on top. The disposable one fires cobblestone.')),
    # --- MV ----------------------------------------------------------------------------------------------------------------------
    ('drill_mv', 2, 2, ['#iis:tools/drill_mv'], ['electric_wrench'], ('Бур MV', 'MV drill'),
     ('Вдвое быстрее и прочнее бура LV.', 'Twice as fast and tough as the LV drill.')),
    ('chainsaw_mv', 2, 3, ['#iis:tools/chainsaw_mv'], ['drill_mv'], ('Цепная пила MV', 'MV chainsaw'), ('Валит деревья целиком.', 'Fells whole trees.')),
    ('ballistic_shield', 2, 5, ['ballistic_shield'], ['turrets'], ('Баллистический щит', 'Ballistic shield'),
     ('Держит пули и удары спереди целиком, взрывы наполовину.', 'Stops bullets and blows from the front, explosions by half.')),
    ('turret_machine_gun', 2, 6, ['turret_base_t2', 'turret_machine_gun'], ['turrets'], ('Пулемётная турель', 'Machine gun turret'),
     ('Стреляет винтовочными патронами.', 'Fires rifle rounds.')),
    # --- HV ----------------------------------------------------------------------------------------------------------------------
    ('drill_hv', 3, 2, ['#iis:tools/drill_hv'], ['drill_mv'], ('Бур HV', 'HV drill'), ('Самый быстрый бур GT.', 'The fastest GT drill.')),
    ('jackhammer', 3, 3, ['#iis:tools/jackhammer'], ['drill_hv'], ('Отбойный молоток', 'Jackhammer'),
     ('Крушит камень быстрее любого бура.', 'Smashes stone faster than any drill.')),
    ('gravitool', 3, 4, ['gravitool'], ['electric_wrench', 'electric_treetap'], ('Гравитул', 'GraviTool'),
     ('Ключ, отвёртка, краник и мотыга в одном. В рецептах — ключ и отвёртка, по ПКМ — смола или пашня.', 'A wrench, screwdriver, treetap and hoe in one. A wrench and a screwdriver in recipes, resin or farmland on right click.')),
    ('seismic', 3, 5, ['seismic_prospector_ev'], ['drill_hv'], ('Сейсмический разведчик', 'Seismic prospector'),
     ('Ищет руду и нефть далеко под землёй.', 'Finds ores and oil deep underground.')),
    # --- EV ----------------------------------------------------------------------------------------------------------------------
    ('advanced_drill', 4, 2, ['advanced_drill'], ['drill_hv'], ('Продвинутый бур', 'Advanced drill'),
     ('Shift+ПКМ включает копку 3×3.', 'Sneak and right click for 3x3 mining.')),
    ('turret_rocket', 4, 6, ['turret_base_t4', 'turret_rocket'], ['turret_machine_gun'], ('Ракетная турель', 'Rocket turret'), ('Стреляет ракетами.', 'Fires rockets.')),
    ('naquadah_reactor', 4, 4, ['naquadah_reactor_ev'], ['seismic'], ('Наквадовый реактор', 'Naquadah reactor'), ('Энергия из наквады.', 'Power from naquadah.')),
    # --- IV ----------------------------------------------------------------------------------------------------------------------
    ('vajra', 5, 2, ['vajra'], ['advanced_drill'], ('Ваджра', 'Vajra'),
     ('Ломает любой блок мгновенно. Shift+ПКМ — режим «блоки целиком».', 'Breaks any block at once. Sneak and right click for whole blocks.')),
    ('force_shield', 5, 5, ['force_shield'], ['ballistic_shield'], ('Силовой щит', 'Force shield'), ('Держит всё спереди за заряд.', 'Stops everything in front, for charge.')),
    ('turret_railgun', 5, 6, ['turret_base_t5', 'turret_railgun'], ['turret_rocket'], ('Рельсотрон', 'Railgun'), ('Ферросердечник насквозь.', 'A ferro slug right through.')),
]


def add_chapter(chapters_dir, tr, order_index, write, hexid, item_exists, filter_item):
    keys = {q[0]: BASE + i for i, q in enumerate(Q)}
    out, missing = [], []
    for key, col, row, items, deps, title, text in Q:
        qid = keys[key]
        qo = {'id': hexid(0x1A, qid), 'x': col * STEP_X, 'y': row * STEP_Y}
        if deps:
            qo['dependencies'] = [hexid(0x1A, keys[d]) for d in deps]
        tasks = []
        for n, i in enumerate(items):
            if i.startswith('#'):
                item = filter_item(f'item_tag({i[1:]})')
            else:
                iid = i if ':' in i else f'{NS}:{i}'
                if iid.startswith(f'{NS}:') and not item_exists(iid):
                    missing.append(iid)
                item = {'id': iid, 'count': 1}
            tasks.append({'type': 'item', 'item': item, 'count': 1, 'id': hexid(0x1B, (qid << 8) | n)})
        if tasks:
            first = next((i for i in items if not i.startswith('#')), None)
            qo['icon'] = {'id': first if first and ':' in first else f'{NS}:{first or "pickaxe_iron"}', 'count': 1}
            if items[0].startswith('#'):
                qo['icon'] = {'id': ICONS.get(key, f'{NS}:pickaxe_iron'), 'count': 1}
            qo['tasks'] = tasks
            credit, count = PAY[max(0, int(col))]
            qo['rewards'] = [{'type': 'item', 'item': {'id': f'{NS}:credit_{credit}', 'count': count}, 'id': hexid(0x1C, qid << 8)}]
        else:
            qo['icon'] = {'id': f'{NS}:tree_farm', 'count': 1}
            qo['size'] = 1.5
            qo['tasks'] = [{'type': 'checkmark', 'id': hexid(0x1B, qid << 8)}]
        tr['ru_ru'][f'quest.{qo["id"]}.title'], tr['en_us'][f'quest.{qo["id"]}.title'] = title
        tr['ru_ru'][f'quest.{qo["id"]}.quest_desc'] = text[0].split('\n')
        tr['en_us'][f'quest.{qo["id"]}.quest_desc'] = text[1].split('\n')
        out.append(qo)
    if missing:
        raise SystemExit(f'quest chapter D144: no such items: {missing}')
    cid = hexid(0x1D, CHAPTER)
    fname = f'{order_index:02d}_what_else'
    write(os.path.join(chapters_dir, fname + '.json5'), {'id': cid, 'filename': fname, 'order_index': order_index, 'default_quest_shape': '',
                                                         'icon': {'id': f'{NS}:tree_farm', 'count': 1}, 'quests': out})
    tr['ru_ru'][f'chapter.{cid}.title'] = 'Что ещё есть в ИИС 2'
    tr['en_us'][f'chapter.{cid}.title'] = 'What else IIS 2 has'
    return len(out)


ICONS = {'flint_tools': f'{NS}:flint_pickaxe', 'sense': f'{NS}:sense_steel', 'plow': f'{NS}:plow_steel', 'branch_cutter': f'{NS}:branch_cutter_steel',
         'butchery_knife': f'{NS}:butchery_knife_steel', 'universal_spade': f'{NS}:universal_spade_steel', 'buzzsaw': f'{NS}:buzzsaw_steel',
         'screwdriver_lv': f'{NS}:screwdriver_lv_steel', 'drill_mv': f'{NS}:drill_mv_steel', 'chainsaw_mv': f'{NS}:chainsaw_mv_steel',
         'drill_hv': f'{NS}:drill_hv_steel', 'jackhammer': f'{NS}:jackhammer_steel'}
