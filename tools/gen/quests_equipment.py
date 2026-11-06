"""Chapter of the quest book about weapons, armour and vehicles (D129). IIS 1 had nothing like it, so it is written here, not converted.

Columns are epochs: steam, LV, MV, HV, EV, IV, LuV, ZPM, UV. It depends on nothing outside itself and nothing in the book depends on it:
a player who does not care for guns loses nothing. Rewards follow the rate of the IIS 1 book at the same epoch, plus ammunition to try the gun at once.
"""
import os

NS = 'iis'
BASE = 0x100000
CHAPTER = 0x1000
STEP_X, STEP_Y = 2.0, 1.5
# credit and count for one quest of an epoch: as in the chapters of IIS 1 of the same period
PAY = {0: ('copper', 2), 1: ('copper', 3), 2: ('copper', 5), 3: ('cupronickel', 1), 4: ('cupronickel', 2), 5: ('cupronickel', 4), 6: ('silver', 1), 7: ('silver', 2), 8: ('gold', 1)}

# key, column, row, items, what it needs, reward besides credits, title (ru, en), text (ru, en)
Q = [
    ('start', -1.5, 4, [], [], None, ('Оружие и транспорт', 'Weapons and vehicles'),
     ('Эта глава идёт рядом с основной книгой и ничего в ней не закрывает. Столбцы — эпохи: пар, LV, MV, HV, EV, IV, LuV, ZPM, UV. Сверху оружие и патроны, посередине броня, внизу транспорт.\n\nОружие: ПКМ — выстрел, удержание — очередь у автомата, R — перезарядка, присесть — прицел. Числа видны в подсказке предмета.\n\nМашины: ПКМ — сесть, с топливом в руке — заправить, Shift+ПКМ — окно с баком и грузом, Shift — выйти.',
      'This chapter runs beside the main book and locks nothing in it. Columns are epochs. Guns and ammunition on top, armour in the middle, vehicles below.\n\nGuns: right click shoots, hold for a burst, R reloads, sneak aims.\n\nVehicles: right click to get in, or to refuel with fuel in hand; sneak and right click for the window; sneak to get out.')),
    # --- powder ------------------------------------------------------------------------------------------------------------------
    ('powder_cartridge', 0, 1, ['powder_cartridge'], ['start'], None, ('Бумажный патрон', 'Paper cartridge'),
     ('Порох и свинцовая пуля в бумаге: всё, что нужно ружьям паровой эпохи. Делается на верстаке.', 'Powder and a lead ball in paper: all that the guns of the steam age need. Made on a crafting table.')),
    ('flintlock_pistol', 0, 2, ['flintlock_pistol'], ['powder_cartridge'], ('powder_cartridge', 8), ('Кремнёвый пистолет', 'Flintlock pistol'),
     ('Один выстрел, потом долгая перезарядка: R и несколько секунд терпения. После выстрела остаётся облако дыма.', 'One shot, then a long reload. A cloud of smoke stays behind.')),
    ('musket', 0, 3, ['musket'], ['flintlock_pistol'], ('powder_cartridge', 8), ('Мушкет', 'Musket'),
     ('Бьёт сильнее и дальше пистолета, заряжается ещё дольше. Присядьте: разброс станет вдвое меньше.', 'Hits harder and further than the pistol and reloads even longer. Sneak to halve the spread.')),
    ('rifled_musket', 1, 2, ['rifled_musket'], ['musket'], ('powder_cartridge', 8), ('Штуцер', 'Rifled musket'),
     ('Нарезной ствол из стали: точный дальний выстрел.', 'A rifled steel barrel: an accurate shot at long range.')),
    ('blunderbuss', 1, 3, ['blunderbuss'], ['musket'], ('powder_cartridge', 8), ('Мушкетон', 'Blunderbuss'),
     ('Широкий раструб и горсть дроби: страшно вблизи и бесполезно вдали.', 'A wide muzzle and a handful of shot: terrible up close, useless far away.')),
    # --- cartridges -----------------------------------------------------------------------------------------------------------------
    ('pistol_round', 2, 1, ['pistol_round'], ['rifled_musket'], None, ('Пистолетные патроны', 'Pistol rounds'),
     ('Первый патрон в гильзе: латунь, порох и свинец собирает сборщик.', 'The first cartridge with a case: an assembler makes it from brass, powder and lead.')),
    ('pistol', 2, 2, ['pistol'], ['pistol_round'], ('pistol_round', 24), ('Пистолет', 'Pistol'),
     ('Двенадцать выстрелов в магазине. После пороха кажется чудом.', 'Twelve shots in the magazine. After powder it feels like a miracle.')),
    ('smg', 2, 3, ['smg'], ['pistol'], ('pistol_round', 48), ('Пистолет-пулемёт', 'Submachine gun'),
     ('Держите ПКМ: очередь. Патроны те же, что у пистолета, расходуются быстро.', 'Hold right click for a burst. It takes the same rounds as the pistol and eats them fast.')),
    ('rifle_round', 3, 1, ['rifle_round'], ['smg'], None, ('Винтовочные патроны', 'Rifle rounds'),
     ('Длинная гильза и стальная пуля. Они же идут в снайперскую винтовку и пулемёт.', 'A long case and a steel bullet. The sniper rifle and the machine gun take them too.')),
    ('assault_rifle', 3, 2, ['assault_rifle'], ['rifle_round'], ('rifle_round', 32), ('Автомат', 'Assault rifle'),
     ('Точный и скорострельный: главное оружие эпохи HV.', 'Accurate and fast: the main gun of the HV age.')),
    ('shotgun', 3, 3, ['shotgun', 'shotgun_shell'], ['smg'], ('shotgun_shell', 16), ('Помповое ружьё', 'Pump shotgun'),
     ('Нужны и ружьё, и патроны к нему. Дробь разлетается широко: в упор сильнее всего, что есть в эту эпоху.', 'Needs the gun and its shells. The shot spreads wide: up close nothing of this age hits harder.')),
    ('grenade_launcher', 3, 4, ['grenade_launcher', 'grenade_round'], ['shotgun'], ('grenade_round', 8), ('Гранатомёт', 'Grenade launcher'),
     ('Граната летит дугой и взрывается при ударе. Блоки не ломает, но и вас заденет: половиной урона.', 'A grenade flies in an arc and bursts on impact. It breaks no blocks but will hurt you too, by half.')),
    ('sniper_rifle', 4, 2, ['sniper_rifle'], ['assault_rifle'], ('rifle_round', 16), ('Снайперская винтовка', 'Sniper rifle'),
     ('Присядьте, и прицел приблизит цель вчетверо. Пуля проходит сквозь первую цель.', 'Sneak and the scope brings the target four times closer. The bullet goes through the first target.')),
    ('machine_gun', 4, 3, ['machine_gun'], ['sniper_rifle'], ('rifle_round', 64), ('Пулемёт', 'Machine gun'),
     ('Сто патронов в ленте и долгая перезарядка. С ним медленнее ходите.', 'A hundred rounds on the belt and a long reload. You walk slower with it.')),
    ('rocket_launcher', 4, 4, ['rocket_launcher', 'rocket_round'], ['grenade_launcher'], ('rocket_round', 4), ('Ручная ракетная пушка', 'Rocket launcher'),
     ('Ракета идёт по прямой и рвётся широким взрывом. Выстрел один, перезарядка долгая.', 'The rocket flies straight and bursts wide. One shot, a long reload.')),
    # --- lasers and plasma ------------------------------------------------------------------------------------------------------------
    ('laser_pistol', 4, 5, ['laser_pistol'], ['rocket_launcher'], None, ('Лазерный пистолет', 'Laser pistol'),
     ('Патроны больше не нужны: стреляет зарядом. Заряжается в зарядниках машин и от ранцев, как электроинструмент. Цель загорается.', 'No more cartridges: it fires its charge. It charges in machines and from packs, like an electric tool. The target catches fire.')),
    ('laser_rifle', 5, 5, ['laser_rifle'], ['laser_pistol'], None, ('Лазерная винтовка', 'Laser rifle'),
     ('Луч очередью, насквозь через одну цель.', 'A beam in bursts, through one target.')),
    ('laser_cannon', 6, 5, ['laser_cannon'], ['laser_rifle'], None, ('Лазерная пушка', 'Laser cannon'),
     ('Один тяжёлый луч пробивает целый ряд врагов.', 'One heavy beam goes through a whole row of enemies.')),
    ('plasma_rifle', 7, 5, ['plasma_rifle'], ['laser_cannon'], None, ('Плазменная винтовка', 'Plasma rifle'),
     ('Сгустки плазмы: урон при попадании и малый взрыв, цели горят. Нужна наквада.', 'Bolts of plasma: damage on impact and a small blast, targets burn. Needs naquadah.')),
    ('plasma_cannon', 8, 5, ['plasma_cannon'], ['plasma_rifle'], None, ('Плазменная пушка', 'Plasma cannon'),
     ('Самое сильное оружие в игре: один выстрел съедает больше миллиона EU.', 'The strongest gun in the game: one shot eats over a million EU.')),
    # --- armour --------------------------------------------------------------------------------------------------------------------
    ('musketeer', 0, 7, 'set', ['start'], None, ('Мундир мушкетёра', 'Musketeer uniform'),
     ('Кожа и бронза, все четыре детали. Защищает как железо, держится дольше.', 'Leather and bronze, all four pieces. It protects like iron and lasts longer.')),
    ('ballistic', 2, 7, 'set', ['musketeer'], None, ('Бронежилет', 'Ballistic armour'),
     ('Алюминий и резина: защита как у алмазной брони.', 'Aluminium and rubber: protection like diamond armour.')),
    ('assault', 4, 7, 'set', ['ballistic'], None, ('Штурмовая броня', 'Assault armour'),
     ('Титан и углеродные пластины: как незерит, удары отбрасывают меньше.', 'Titanium and carbon plates: like netherite, with less knockback.')),
    ('power_armor', 5, 7, 'set', ['assault'], None, ('Силовая броня', 'Power armor'),
     ('Поглощает весь урон за заряд, пока он есть. Огонь не страшен, бег быстрее, шаг на блок, ночное зрение на N.', 'Absorbs all damage for charge while it lasts. Fireproof, faster running, steps up a block, night vision on N.')),
    ('plasma_armor', 7, 7, 'set', ['power_armor'], None, ('Плазменная броня', 'Plasma armor'),
     ('То же, что силовая, но дешевле на единицу урона и ёмче. Ещё даёт воздух и снимает отравы и радиацию.', 'As power armor, cheaper per point of damage and with more charge. It also gives air and removes poison and radiation.')),
    # --- vehicles -----------------------------------------------------------------------------------------------------------------
    ('wheel_wood', 0, 9, ['wheel_wood'], ['start'], None, ('Деревянное колесо', 'Wooden wheel'), ('Дерево и обод из бронзы.', 'Wood and a bronze rim.')),
    ('engine_steam', 0, 10, ['engine_steam'], ['wheel_wood'], None, ('Паровая машина', 'Steam engine'), ('Сердце первой самоходной машины.', 'The heart of the first self-moving machine.')),
    ('vehicle_steam_car', 0, 11, ['vehicle_steam_car'], ['engine_steam'], None, ('Паровой тягач', 'Steam tractor'),
     ('Ещё не быстро, зато уже не пешком. Топливо — то, что горит в печи: ПКМ углём по машине. Едет на W, S, рулит на A, D, пробел — тормоз.', 'Not fast, but no longer on foot. It burns what a furnace burns: right click it with coal. W and S to drive, A and D to steer, space to brake.')),
    ('wheel_rubber', 1, 9, ['wheel_rubber'], ['wheel_wood'], None, ('Резиновое колесо', 'Rubber wheel'), ('Резина и стальное кольцо. Идёт почти на всю технику.', 'Rubber and a steel ring. Nearly every vehicle takes it.')),
    ('engine_lv', 1, 10, ['engine_lv'], ['engine_steam', 'wheel_rubber'], None, ('Двигатель LV', 'LV engine'), ('Сталь, поршни и мотор своей эпохи.', 'Steel, pistons and a motor of its age.')),
    ('vehicle_quad', 1, 11, ['vehicle_quad'], ['engine_lv'], None, ('Квадроцикл', 'Quad bike'),
     ('Жидкое топливо: ведро или капсула того, что горит в дизельном генераторе. ПКМ с ним по машине.', 'Liquid fuel: a bucket or capsule of what a combustion generator burns. Right click the vehicle with it.')),
    ('engine_mv', 2, 10, ['engine_mv'], ['engine_lv'], None, ('Двигатель MV', 'MV engine'), ('На нём едет сразу несколько машин.', 'Several vehicles run on it.')),
    ('vehicle_motorbike', 1.6, 11.5, ['vehicle_motorbike'], ['engine_mv'], None, ('Мотоцикл', 'Motorcycle'), ('Самый быстрый на земле, но один седок и никакого груза.', 'The fastest on land, but one rider and no cargo.')),
    ('vehicle_car', 2, 12.5, ['vehicle_car'], ['engine_mv'], None, ('Автомобиль', 'Car'), ('Четыре места и немного груза в багажнике: Shift+ПКМ. Наезд на моба его ранит, удар о стену портит машину.', 'Four seats and a little cargo: sneak and right click. Running over a mob hurts it, hitting a wall hurts the car.')),
    ('vehicle_boat', 2.4, 11.5, ['vehicle_boat'], ['engine_mv'], None, ('Катер', 'Motor boat'), ('Ставится на воду и только по воде ходит. Четыре места.', 'Goes on water and only on water. Four seats.')),
    ('engine_hv', 3, 10, ['engine_hv'], ['engine_mv'], None, ('Двигатель HV', 'HV engine'), ('Хватит и на грузовик, и на самолёт.', 'Enough for a truck and for an airplane.')),
    ('vehicle_truck', 3, 12.5, ['vehicle_truck'], ['engine_hv'], None, ('Грузовик', 'Truck'), ('Медленный, зато 54 слота груза.', 'Slow, but with 54 slots of cargo.')),
    ('vehicle_plane', 3.4, 11.5, ['vehicle_plane'], ['engine_hv'], None, ('Самолёт', 'Airplane'),
     ('W и S двигают рычаг газа, летит туда, куда смотрите. Нужен разбег: ниже скорости сваливания машина проседает. Удар о землю на скорости опасен и для пилота.', 'W and S move the throttle, it flies where you look. It needs a run-up: below stall speed it sinks. Hitting the ground at speed is dangerous for the pilot too.')),
    ('engine_ev', 4, 10, ['engine_ev'], ['engine_hv'], None, ('Двигатель EV', 'EV engine'), ('Титан и детали уровня EV.', 'Titanium and EV parts.')),
    ('vehicle_helicopter', 4.4, 11.5, ['vehicle_helicopter'], ['engine_ev'], None, ('Вертолёт', 'Helicopter'),
     ('Пробел — вверх, Ctrl — вниз, W, A, S, D — движение, поворот мышью. Винт раскручивается несколько секунд. Кончилось топливо — садится, а не падает.', 'Space goes up, Ctrl down, W A S D move, the mouse turns. The rotor takes a few seconds to spin up. Out of fuel it comes down gently.')),
    ('vehicle_tank', 4, 12.5, ['vehicle_tank', 'tank_shell'], ['engine_ev'], ('tank_shell', 8), ('Танк', 'Tank'),
     ('Разворачивается на месте, башня смотрит туда же, куда вы. Снаряды кладутся в груз, клавиша G — выстрел. Пули ему не страшны.', 'Turns on the spot, the turret looks where you look. Shells go in the cargo, G fires. Bullets do nothing to it.')),
    ('jet_turbine', 5, 10, ['jet_turbine'], ['engine_ev'], None, ('Реактивная турбина', 'Jet turbine'), ('Вольфрамовая сталь и моторы IV.', 'Tungstensteel and IV motors.')),
    ('vehicle_jet', 5, 11, ['vehicle_jet'], ['jet_turbine'], None, ('Реактивный самолёт', 'Jet fighter'),
     ('Самая высокая скорость в игре и длинный разбег. Следите за топливом: жжёт быстро.', 'The highest speed in the game and a long run-up. Watch the fuel: it burns fast.')),
]
SET_PARTS = ('helmet', 'chestplate', 'leggings', 'boots')


def add_chapter(chapters_dir, tr, order_index, write, hexid, item_exists):
    keys = {q[0]: BASE + i for i, q in enumerate(Q)}
    out = []
    missing = []
    for key, col, row, items, deps, extra, title, text in Q:
        qid = keys[key]
        qo = {'id': hexid(0x1A, qid), 'x': col * STEP_X, 'y': row * STEP_Y}
        if deps:
            qo['dependencies'] = [hexid(0x1A, keys[d]) for d in deps]
        ids = [f'{NS}:{key}_{p}' for p in SET_PARTS] if items == 'set' else [f'{NS}:{i}' for i in items]
        for i in ids:
            if not item_exists(i):
                missing.append(i)
        if ids:
            qo['icon'] = {'id': ids[1] if items == 'set' else ids[0], 'count': 1}
            qo['tasks'] = [{'type': 'item', 'item': {'id': i, 'count': 1}, 'count': 1, 'id': hexid(0x1B, (qid << 8) | n)} for n, i in enumerate(ids)]
        else:
            qo['icon'] = {'id': f'{NS}:assault_rifle', 'count': 1}
            qo['size'] = 1.5
            qo['tasks'] = [{'type': 'checkmark', 'id': hexid(0x1B, qid << 8)}]
        rewards = []
        if ids:
            credit, count = PAY[max(0, int(col))]
            rewards.append({'type': 'item', 'item': {'id': f'{NS}:credit_{credit}', 'count': count}, 'id': hexid(0x1C, qid << 8)})
        if extra:
            rewards.append({'type': 'item', 'item': {'id': f'{NS}:{extra[0]}', 'count': extra[1]}, 'id': hexid(0x1C, (qid << 8) | 1)})
        if rewards:
            qo['rewards'] = rewards
        tr['ru_ru'][f'quest.{qo["id"]}.title'] = title[0]
        tr['en_us'][f'quest.{qo["id"]}.title'] = title[1]
        tr['ru_ru'][f'quest.{qo["id"]}.quest_desc'] = text[0].split('\n')
        tr['en_us'][f'quest.{qo["id"]}.quest_desc'] = text[1].split('\n')
        out.append(qo)
    if missing:
        raise SystemExit(f'quest chapter D129: no such items: {missing}')
    cid = hexid(0x1D, CHAPTER)
    fname = f'{order_index:02d}_weapons_and_vehicles'
    write(os.path.join(chapters_dir, fname + '.json5'), {'id': cid, 'filename': fname, 'order_index': order_index, 'default_quest_shape': '', 'icon': {'id': f'{NS}:assault_rifle', 'count': 1}, 'quests': out})
    tr['ru_ru'][f'chapter.{cid}.title'] = 'Оружие и транспорт'
    tr['en_us'][f'chapter.{cid}.title'] = 'Weapons and vehicles'
    return len(out), cid
