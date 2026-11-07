"""Space, first stage (D115): the Moon, oxygen, the tier 1 rocket.

Reference for numbers and recipes: Galacticraft 3.0.12 of the IIS 1 pack (config/Galacticraft, scripts/Galacticraft.zs), GT_MachineRecipeLoader:1096-1102, ProcessingPlate:91,
GT_Worldgenloader. The Galacticraft licence forbids distributing its code and art: everything here is written and drawn anew, as with Railcraft (D102).
"""
import os
import random

from PIL import Image

NS = 'iis'
I = lambda x: f'{NS}:{x}'
COMPRESSED = (('iron', 'Iron', (190, 190, 195), 'Compressed Iron', 'Сжатое железо'), ('steel', 'Steel', (120, 120, 128), 'Compressed Steel', 'Сжатая сталь'),
              ('aluminium', 'Aluminium', (150, 205, 225), 'Compressed Aluminium', 'Сжатый алюминий'), ('tin', 'Tin', (215, 215, 225), 'Compressed Tin', 'Сжатое олово'),
              ('copper', 'Copper', (225, 130, 70), 'Compressed Copper', 'Сжатая медь'), ('bronze', 'Bronze', (210, 150, 60), 'Compressed Bronze', 'Сжатая бронза'),
              ('titanium', 'Titanium', (210, 165, 215), 'Compressed Titanium', 'Сжатый титан'),
              ('meteoric_iron', 'MeteoricIron', (105, 80, 85), 'Compressed Meteoric Iron', 'Сжатое метеоритное железо'),
              ('desh', 'Desh', (60, 60, 64), 'Compressed Desh', 'Сжатый деш'))
# id, EN, RU, base colour, accent colour, shape
ITEMS = [('heavy_plating', 'Heavy Duty Plate', 'Сверхпрочное покрытие', (95, 100, 110), (150, 155, 165), 'plate'),
         ('heavy_plating_t2', 'Heavy Duty Plate T2', 'Сверхпрочное покрытие Т2', (120, 85, 60), (195, 145, 95), 'plate'),
         ('rocket_engine_t1', 'Tier 1 Rocket Engine', 'Ракетный двигатель 1-го уровня', (90, 90, 100), (230, 120, 40), 'engine'),
         ('rocket_fins', 'Rocket Fins', 'Ракетный стабилизатор', (170, 40, 40), (220, 220, 225), 'fins'),
         ('nose_cone', 'Nose Cone', 'Головной обтекатель', (215, 215, 220), (190, 40, 40), 'cone'),
         ('canvas', 'Canvas', 'Холст', (225, 215, 190), (190, 180, 150), 'plate'),
         ('parachute', 'Parachute', 'Парашют', (235, 235, 240), (200, 60, 60), 'cone'),
         ('wafer_basic', 'Basic Semiconductor Wafer', 'Основная полупроводниковая пластина', (70, 110, 80), (190, 210, 120), 'chip'),
         ('wafer_advanced', 'Advanced Semiconductor Wafer', 'Продвинутая полупроводниковая пластина', (80, 60, 120), (210, 170, 240), 'chip'),
         ('air_vent', 'Oxygen Vent', 'Кислородная вентиляция', (150, 155, 165), (60, 60, 70), 'grid'),
         ('air_fan', 'Oxygen Fan', 'Кислородный вентилятор', (130, 135, 145), (200, 205, 215), 'fan'),
         ('oxygen_concentrator', 'Oxygen Concentrator', 'Кислородный концентратор', (110, 135, 170), (190, 225, 245), 'grid'),
         ('oxygen_pipe', 'Oxygen Pipe', 'Кислородная труба', (205, 225, 240), (110, 170, 215), 'pipe'),
         ('canister_tin', 'Tin Canister', 'Оловянная канистра', (205, 205, 215), (130, 130, 140), 'can'),
         ('large_steel_cell', 'Large Steel Fluid Cell', 'Большая стальная канистра', (120, 120, 128), (70, 70, 78), 'can'),
         ('oxygen_mask', 'Oxygen Mask', 'Кислородная маска', (210, 225, 235), (70, 90, 110), 'mask'),
         ('oxygen_gear', 'Oxygen Gear', 'Кислородное оборудование', (110, 115, 125), (190, 225, 245), 'grid'),
         # D134: the third tier (GalacticraftMars itemBasicAsteroids 0-2, heavyNoseCone, GalacticraftCore engine:1)
         ('heavy_plating_t3', 'Heavy Duty Plate T3', 'Сверхпрочное покрытие Т3', (60, 70, 95), (120, 140, 190), 'plate'),
         ('heavy_rocket_engine', 'Heavy Rocket Engine', 'Тяжёлый ракетный двигатель', (60, 62, 72), (90, 150, 230), 'engine'),
         ('heavy_rocket_fins', 'Heavy Rocket Fins', 'Тяжёлый ракетный стабилизатор', (55, 60, 80), (200, 200, 210), 'fins'),
         ('heavy_nose_cone', 'Heavy Nose Cone', 'Тяжёлый головной обтекатель', (75, 80, 100), (190, 60, 50), 'cone'),
         ('rocket_booster', 'Tier 1 Booster', 'Ускоритель 1-го уровня', (150, 150, 160), (230, 160, 40), 'engine')]


def draw(shape, base, accent, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))

    def px(x, y, c):
        v = rnd.randint(-8, 8)
        im.putpixel((x, y), (max(0, min(255, c[0] + v)), max(0, min(255, c[1] + v)), max(0, min(255, c[2] + v)), 255))
    for y in range(16):
        for x in range(16):
            cx, cy = abs(x - 7.5), abs(y - 7.5)
            if shape == 'plate' and 2 <= x <= 13 and 3 <= y <= 12:
                px(x, y, accent if x in (2, 13) or y in (3, 12) else base)
            elif shape == 'engine' and ((cx <= 3 and y <= 9) or (y > 9 and cx <= 2 + (y - 9))):
                px(x, y, accent if y > 12 else base)
            elif shape == 'fins' and cx <= (y - 2) / 2 and 2 <= y <= 13:
                px(x, y, accent if y > 10 else base)
            elif shape == 'cone' and cx <= (y - 1) / 2.4 and 1 <= y <= 14:
                px(x, y, accent if y > 11 else base)
            elif shape == 'chip' and 3 <= x <= 12 and 3 <= y <= 12:
                px(x, y, accent if (x + y) % 4 == 0 else base)
            elif shape == 'grid' and 2 <= x <= 13 and 2 <= y <= 13:
                px(x, y, accent if x % 3 == 1 and y % 3 == 1 else base)
            elif shape == 'fan' and cx * cx + cy * cy <= 42:
                px(x, y, accent if (x + y) % 5 < 2 or (x - y) % 5 == 0 else base)
            elif shape == 'pipe' and 5 <= y <= 10:
                px(x, y, accent if y in (5, 10) else base)
            elif shape == 'can' and 4 <= x <= 11 and 2 <= y <= 14:
                px(x, y, accent if y in (2, 3, 14) else base)
            elif shape == 'mask' and 3 <= x <= 12 and 3 <= y <= 12:
                px(x, y, accent if y >= 9 else base)
            elif shape == 'tank' and 5 <= x <= 10 and 2 <= y <= 14:
                px(x, y, accent if y <= 4 else base)
    return im


def rock(base, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = base + rnd.randint(-12, 12) - (18 if rnd.random() < 0.07 else 0)
            im.putpixel((x, y), (c, c, c + 3, 255))
    return im


def tinted(base, seed, tint):
    im = rock(base, seed)
    for y in range(16):
        for x in range(16):
            r, gg, b, a = im.getpixel((x, y))
            im.putpixel((x, y), (min(255, int(r * tint[0])), min(255, int(gg * tint[1])), min(255, int(b * tint[2])), a))
    return im


def generate(g):
    write, A, D, lang, shaped, tag_of, block_tags, ref = g['write'], g['A'], g['D'], g['lang'], g['shaped'], g['tag_of'], g['block_tags'], g['ref']
    rec = {}

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def item(iid, en, ru, im):
        im.save(tex(f'item/{iid}.png'))
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)

    def block(bid, en, ru, im=None, model=None, tool='pickaxe'):
        if im is not None:
            im.save(tex(f'block/space/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), model or {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/space/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
              'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [{'type': 'minecraft:item', 'name': I(bid)}]}]})
        block_tags.setdefault(f'minecraft:mineable/{tool}', []).append(I(bid))
        name(f'block.{NS}.{bid}', en, ru)

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, keep=None, circuit=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': dur, 'eut': eut}
        if fin:
            r['fluid_inputs'] = [fin]
        if keep:
            r['keep'] = [keep]
        if circuit is not None:
            r['circuit'] = circuit
        rec[f'{rtype}/{rid}'] = r

    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1: {'id': i, 'count': c}
    fl = lambda f, n: {'ingredient': I(f), 'amount': n}

    # --- items -----------------------------------------------------------------------------------------------------------
    for k, (cid, mat, col, en, ru) in enumerate(COMPRESSED):
        acc = tuple(max(0, c - 50) for c in col)
        item(f'compressed_{cid}', en, ru, draw('plate', col, acc, 100 + k))
    for k, (iid, en, ru, base, acc, shape) in enumerate(ITEMS):
        item(iid, en, ru, draw(shape, base, acc, 200 + k))
    item('oxygen_tank_light', 'Light Oxygen Tank', 'Малый кислородный баллон', draw('tank', (90, 170, 90), (60, 110, 60), 300))
    item('heavy_ingot_t1', 'Heavy Duty Alloy Ingot T1', 'Сверхпрочный слиток Т1', draw('plate', (85, 85, 95), (40, 40, 50), 301))
    item('heavy_ingot_t2', 'Heavy Duty Alloy Ingot T2', 'Сверхпрочный слиток Т2', draw('plate', (110, 80, 60), (55, 40, 30), 303))
    item('rocket_t2', 'Tier 2 Rocket', 'Ракета 2-го уровня', draw('cone', (70, 72, 82), (215, 110, 40), 304))
    item('schematic_3x3', 'Schematic (3x3)', 'Схема (3×3)', Image.open(os.path.join(g['ROOT'], 'tools', 'gen', 'textures', 'lv', 'items', '497.png')).convert('RGBA'))
    item('rocket_t1', 'Tier 1 Rocket', 'Ракета 1-го уровня', draw('cone', (225, 225, 230), (190, 40, 40), 302))
    item('heavy_ingot_t3', 'Heavy Duty Alloy Ingot T3', 'Сверхпрочный слиток Т3', draw('plate', (60, 70, 95), (30, 35, 50), 305))
    item('rocket_t3', 'Tier 3 Rocket', 'Ракета 3-го уровня', draw('cone', (60, 70, 95), (90, 150, 230), 306))
    for k, en, ru in (('gui.iis.rocket.target.asteroids', 'Target: asteroids', 'Цель: астероиды'),
                      ('gui.iis.nasa.rocket_t3', 'Tier 3 rocket: heavy nose, 8 T3 plates, 4 heavy fins, heavy engine, 2 boosters', 'Ракета 3-го уровня: тяжёлый обтекатель, 8 покрытий Т3, 4 тяжёлых стабилизатора, тяжёлый двигатель, 2 ускорителя'),
                      ('block.iis.oxygen_collector.tooltip.0', 'Collects oxygen into its tank: from the air on planets with air, from plants nearby elsewhere', 'Собирает кислород в бак: из воздуха там, где он есть, иначе от растений вокруг'),
                      ('block.iis.oxygen_collector.tooltip.1', 'Pipes take the oxygen out', 'Кислород забирается трубами'),
                      ('block.iis.oxygen_distributor.tooltip.0', 'Takes oxygen by pipe and keeps a bubble of air around it, up to 10 blocks', 'Принимает кислород по трубе и держит вокруг пузырь воздуха до 10 блоков'),
                      ('block.iis.oxygen_distributor.tooltip.1', 'Inside the bubble nobody uses the tank', 'Внутри пузыря баллон не тратится'),
                      ('block.iis.cargo_loader.tooltip.0', 'Moves items from the chest beside it into a rocket on the pad next to it', 'Перекладывает предметы из соседнего сундука в ракету на соседней площадке'),
                      ('block.iis.cargo_unloader.tooltip.0', 'Moves the cargo of a rocket on the pad next to it into the chest beside it', 'Выгружает груз ракеты с соседней площадки в соседний сундук'),
                      ('message.iis.oxygen_collector', 'Oxygen: %s / %s L, %s L/s', 'Кислород: %s / %s л, %s л/с'),
                      ('message.iis.oxygen_distributor', 'Oxygen: %s / %s L, bubble %s blocks', 'Кислород: %s / %s л, пузырь %s блоков')):
        name(k, en, ru)
    for k, en, ru in (('item.iis.rocket.cargo', 'Cargo: %s slots', 'Груз: %s ячеек'), ('item.iis.rocket.fuel', 'Fuel: %s L', 'Топливо: %s л'),
                      ('item.iis.rocket_t1.hint', 'Place on the middle of a 3x3 launch pad', 'Ставится на середину взлётной площадки 3×3'),
                      ('item.iis.oxygen.hint', 'Keep in your inventory: mask, gear and a tank with oxygen let you breathe off Earth', 'Держи в инвентаре: маска, оборудование и баллон с кислородом дают дышать вне Земли'),
                      ('item.iis.oxygen_tank.level', 'Oxygen: %s / %s', 'Кислород: %s / %s'),
                      ('message.iis.oxygen.none', 'No oxygen! You need a mask, gear and a filled tank', 'Нет кислорода! Нужны маска, оборудование и полный баллон'),
                      ('message.iis.oxygen.low', 'Oxygen is running out: %s s', 'Кислород кончается: %s с'),
                      ('message.iis.rocket.fuel', 'Fuel: %s / %s L; a flight takes %s L', 'Топливо: %s / %s л; на полёт нужно %s л'),
                      ('message.iis.rocket.no_fuel', 'Not enough fuel: %s of %s L', 'Мало топлива: %s из %s л'),
                      ('message.iis.rocket.countdown', 'Launch in %s s', 'Старт через %s с'),
                      ('message.iis.rocket.ready', 'Press jump to launch', 'Нажми прыжок для старта'),
                      ('message.iis.rocket.pad', 'A rocket needs a complete 3x3 launch pad', 'Ракете нужна целая взлётная площадка 3×3'),
                      ('message.iis.fuel_loader', 'Rocket fuel: %s / %s L', 'Ракетное топливо: %s / %s л'),
                      ('gui.iis.rocket.target.mars', 'Target: Mars', 'Цель: Марс'), ('gui.iis.rocket.target.moon', 'Target: the Moon', 'Цель: Луна'),
                      ('gui.iis.nasa.rocket_t2', 'Tier 2 rocket: the same parts with T2 plates', 'Ракета 2-го уровня: те же детали, покрытия Т2'),
                      ('entity.iis.rocket', 'Rocket', 'Ракета'), ('itemGroup.iis.space', 'IIS 2.0: Space', 'ИИС 2.0: космос'),
                      ('death.attack.iis.oxygen_suffocation', '%s ran out of oxygen', '%s задохнулся без кислорода'),
                      ('gui.iis.nasa.rocket_t1', 'Tier 1 rocket: nose, 8 plates, 4 fins, engine; chests add cargo', 'Ракета 1-го уровня: обтекатель, 8 покрытий, 4 стабилизатора, двигатель; сундуки дают груз'),
                      ('block.iis.nasa_workbench.tooltip.0', 'Assembles rockets from parts', 'Собирает ракеты из деталей'),
                      ('block.iis.fuel_loader.tooltip.0', 'Takes rocket fuel by pipe and fills a rocket standing on the pad next to it', 'Принимает ракетное топливо по трубе и заправляет ракету на соседней площадке')):
        name(k, en, ru)

    # --- blocks ----------------------------------------------------------------------------------------------------------
    block('moon_rock', 'Moon Rock', 'Лунный камень', rock(110, 1))
    block('moon_dirt', 'Moon Dirt', 'Лунный грунт', rock(135, 2), tool='shovel')
    block('moon_turf', 'Moon Regolith', 'Лунный реголит', rock(165, 3), tool='shovel')
    block_tags.setdefault('minecraft:stone_ore_replaceables', []).append(I('moon_rock'))   # GT ore on the Moon sits on the plain stone form, as in GT5
    block('mars_rock', 'Mars Rock', 'Марсианский камень', tinted(115, 7, (1.15, 0.72, 0.55)))
    block('mars_dirt', 'Mars Soil', 'Марсианский грунт', tinted(125, 8, (1.25, 0.78, 0.55)), tool='shovel')
    block('mars_turf', 'Mars Dust', 'Марсианская пыль', tinted(150, 9, (1.3, 0.72, 0.45)), tool='shovel')
    block_tags['minecraft:stone_ore_replaceables'].append(I('mars_rock'))
    block('asteroid_rock', 'Asteroid Rock', 'Астероидная порода', tinted(80, 10, (0.85, 0.85, 0.95)))
    block_tags['minecraft:stone_ore_replaceables'].append(I('asteroid_rock'))
    # D134: oxygen machines and cargo loaders (own art)
    for bid, en, ru, col, acc in (('oxygen_collector', 'Oxygen Collector', 'Кислородный сборщик', 140, (60, 140, 210)),
                                  ('oxygen_distributor', 'Oxygen Bubble Distributor', 'Распределитель кислородного пузыря', 145, (90, 190, 230)),
                                  ('cargo_loader', 'Cargo Loader', 'Загрузчик груза', 125, (210, 160, 40)),
                                  ('cargo_unloader', 'Cargo Unloader', 'Разгрузчик груза', 125, (160, 110, 40))):
        im = rock(col, 20 + len(bid))
        for x in range(16):
            for y in range(16):
                if 3 <= x <= 12 and 3 <= y <= 12 and (x in (3, 12) or y in (3, 12) or (x + y) % 5 == 0):
                    im.putpixel((x, y), acc + (255,))
        block(bid, en, ru, im)
    pad = rock(95, 4)
    for k in range(16):
        for x, y in ((k, 0), (k, 15), (0, k), (15, k)):
            pad.putpixel((x, y), (205, 170, 50, 255))
    block('launch_pad', 'Rocket Launch Pad', 'Взлётная площадка', pad, model={'parent': 'minecraft:block/block', 'textures': {'particle': f'{NS}:block/space/launch_pad', 'all': f'{NS}:block/space/launch_pad'},
          'elements': [{'from': [0, 0, 0], 'to': [16, 3, 16], 'faces': {f: {'texture': '#all'} for f in ('down', 'up', 'north', 'south', 'west', 'east')}}]})
    side = rock(120, 5)
    for k in range(4, 12):
        for j in range(4, 12):
            side.putpixel((k, j), (180, 80, 40, 255))
    block('fuel_loader', 'Fuel Loader', 'Загрузчик топлива', side)
    top = rock(150, 6)
    for k in range(16):
        top.putpixel((k, k), (40, 70, 160, 255))
        top.putpixel((k, 15 - k), (40, 70, 160, 255))
    block('nasa_workbench', 'NASA Workbench', 'Верстак NASA', top)

    # --- entity art (own) --------------------------------------------------------------------------------------------------
    body = Image.new('RGBA', (64, 64), (225, 225, 230, 255))
    for y in range(64):
        for x in range(64):
            if y % 12 == 0:
                body.putpixel((x, y), (150, 150, 160, 255))
    for y in range(40, 64):
        for x in range(0, 64):
            body.putpixel((x, y), (185, 45, 45, 255))
    body.save(tex('entity/rocket_t1.png'))
    heavy = Image.new('RGBA', (64, 64), (72, 74, 84, 255))
    for y in range(64):
        for x in range(64):
            if y % 12 == 0:
                heavy.putpixel((x, y), (40, 42, 50, 255))
            if y >= 40:
                heavy.putpixel((x, y), (215, 110, 40, 255))
    heavy.save(tex('entity/rocket_t2.png'))
    t3 = Image.new('RGBA', (64, 64), (60, 70, 95, 255))
    for y in range(64):
        for x in range(64):
            if y % 12 == 0:
                t3.putpixel((x, y), (30, 35, 50, 255))
            if y >= 40:
                t3.putpixel((x, y), (90, 150, 230, 255))
    t3.save(tex('entity/rocket_t3.png'))

    # --- the Moon ------------------------------------------------------------------------------------------------------------
    # GT_Worldgen veins are stretched by D11 (y * 1.6 - 64), so the Moon keeps the overworld's height; surface near y = 70
    write(os.path.join(D, NS, 'dimension_type', 'moon.json'), {
        'ambient_light': 0.1, 'attributes': {
            'minecraft:gameplay/bed_rule': {'can_set_spawn': 'never', 'can_sleep': 'never', 'explodes': False},
            'minecraft:gameplay/respawn_anchor_works': False,
            'minecraft:visual/fog_color': '#000000', 'minecraft:visual/sky_color': '#000000', 'minecraft:visual/cloud_height': 2000.0},
        'coordinate_scale': 1.0, 'has_ceiling': False, 'has_ender_dragon_fight': False, 'has_fixed_time': False, 'has_skylight': True, 'height': 384,
        'infiniburn': '#minecraft:infiniburn_overworld', 'logical_height': 384, 'min_y': -64, 'monster_spawn_block_light_limit': 0,
        'monster_spawn_light_level': {'type': 'minecraft:uniform', 'max_inclusive': 7, 'min_inclusive': 0}, 'skybox': 'overworld', 'timelines': []})
    write(os.path.join(D, NS, 'worldgen', 'biome', 'moon.json'), {
        'attributes': {'minecraft:visual/sky_color': '#000000', 'minecraft:visual/fog_color': '#000000'}, 'carvers': [], 'downfall': 0.0, 'effects': {'water_color': '#3f76e4'},
        'features': [[], [], [], [], [], [], [I('gt_ore_veins')], [], [], [], []], 'has_precipitation': False, 'spawn_costs': {},
        'spawners': {'ambient': [], 'axolotls': [], 'creature': [], 'misc': [], 'underground_water_creature': [], 'water_ambient': [], 'water_creature': [],
                     'monster': [{'type': 'minecraft:zombie', 'weight': 100, 'minCount': 2, 'maxCount': 4}, {'type': 'minecraft:skeleton', 'weight': 100, 'minCount': 2, 'maxCount': 4},
                                 {'type': 'minecraft:spider', 'weight': 50, 'minCount': 1, 'maxCount': 2}, {'type': 'minecraft:creeper', 'weight': 50, 'minCount': 1, 'maxCount': 2}]},
        'temperature': 0.5})
    density = {'type': 'minecraft:add',
               'argument1': {'type': 'minecraft:y_clamped_gradient', 'from_y': -64, 'to_y': 320, 'from_value': 2.09375, 'to_value': -3.90625},
               'argument2': {'type': 'minecraft:mul', 'argument1': 0.3, 'argument2': {'type': 'minecraft:noise', 'noise': 'minecraft:surface', 'xz_scale': 0.8, 'y_scale': 0.0}}}
    zero = {k: 0.0 for k in ('barrier', 'continents', 'depth', 'erosion', 'fluid_level_floodedness', 'fluid_level_spread', 'lava', 'ridges', 'temperature', 'vegetation', 'vein_gap', 'vein_ridged', 'vein_toggle', 'preliminary_surface_level')}
    write(os.path.join(D, NS, 'worldgen', 'noise_settings', 'moon.json'), {
        'aquifers_enabled': False, 'default_block': {'Name': I('moon_rock')}, 'default_fluid': {'Name': 'minecraft:air'}, 'disable_mob_generation': False, 'legacy_random_source': False,
        'noise': {'height': 384, 'min_y': -64, 'size_horizontal': 1, 'size_vertical': 2},
        'noise_router': dict(zero, final_density=density, initial_density_without_jaggedness=density),
        'ore_veins_enabled': False, 'sea_level': -63, 'spawn_target': [],
        'surface_rule': {'type': 'minecraft:sequence', 'sequence': [
            {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:vertical_gradient', 'random_name': 'iis:moon_bedrock', 'true_at_and_below': {'above_bottom': 0}, 'false_at_and_above': {'above_bottom': 5}},
             'then_run': {'type': 'minecraft:block', 'result_state': {'Name': 'minecraft:bedrock'}}},
            {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'add_surface_depth': False, 'offset': 0, 'secondary_depth_range': 0, 'surface_type': 'floor'},
             'then_run': {'type': 'minecraft:block', 'result_state': {'Name': I('moon_turf')}}},
            {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'add_surface_depth': False, 'offset': 3, 'secondary_depth_range': 0, 'surface_type': 'floor'},
             'then_run': {'type': 'minecraft:block', 'result_state': {'Name': I('moon_dirt')}}}]}})
    write(os.path.join(D, NS, 'dimension', 'moon.json'), {'type': I('moon'), 'generator': {'type': 'minecraft:noise', 'settings': I('moon'), 'biome_source': {'type': 'minecraft:fixed', 'biome': I('moon')}}})

    # --- Mars (D131): the same height as the Moon, rolling hills, ore on the plain stone form. GC WorldProviderMars for the numbers (LowGravity, RocketEntity) ----------
    write(os.path.join(D, NS, 'dimension_type', 'mars.json'), {
        'ambient_light': 0.05, 'attributes': {
            'minecraft:gameplay/bed_rule': {'can_set_spawn': 'never', 'can_sleep': 'never', 'explodes': False},
            'minecraft:gameplay/respawn_anchor_works': False,
            'minecraft:visual/fog_color': '#c48a62', 'minecraft:visual/sky_color': '#b5764c', 'minecraft:visual/cloud_height': 2000.0},
        'coordinate_scale': 1.0, 'has_ceiling': False, 'has_ender_dragon_fight': False, 'has_fixed_time': False, 'has_skylight': True, 'height': 384,
        'infiniburn': '#minecraft:infiniburn_overworld', 'logical_height': 384, 'min_y': -64, 'monster_spawn_block_light_limit': 0,
        'monster_spawn_light_level': {'type': 'minecraft:uniform', 'max_inclusive': 7, 'min_inclusive': 0}, 'skybox': 'overworld', 'timelines': []})
    write(os.path.join(D, NS, 'worldgen', 'biome', 'mars.json'), {
        'attributes': {'minecraft:visual/sky_color': '#b5764c', 'minecraft:visual/fog_color': '#c48a62'}, 'carvers': [], 'downfall': 0.0, 'effects': {'water_color': '#3f76e4'},
        'features': [[], [], [], [], [], [], [I('gt_ore_veins')], [], [], [], []], 'has_precipitation': False, 'spawn_costs': {},
        'spawners': {'ambient': [], 'axolotls': [], 'creature': [], 'misc': [], 'underground_water_creature': [], 'water_ambient': [], 'water_creature': [],
                     'monster': [{'type': 'minecraft:zombie', 'weight': 100, 'minCount': 2, 'maxCount': 4}, {'type': 'minecraft:skeleton', 'weight': 100, 'minCount': 2, 'maxCount': 4},
                                 {'type': 'minecraft:spider', 'weight': 50, 'minCount': 1, 'maxCount': 2}, {'type': 'minecraft:creeper', 'weight': 50, 'minCount': 1, 'maxCount': 2}]},
        'temperature': 0.5})
    hills = {'type': 'minecraft:add',
             'argument1': {'type': 'minecraft:y_clamped_gradient', 'from_y': -64, 'to_y': 320, 'from_value': 2.09375, 'to_value': -3.90625},
             'argument2': {'type': 'minecraft:mul', 'argument1': 0.45, 'argument2': {'type': 'minecraft:noise', 'noise': 'minecraft:surface', 'xz_scale': 0.25, 'y_scale': 0.0}}}
    write(os.path.join(D, NS, 'worldgen', 'noise_settings', 'mars.json'), {
        'aquifers_enabled': False, 'default_block': {'Name': I('mars_rock')}, 'default_fluid': {'Name': 'minecraft:air'}, 'disable_mob_generation': False, 'legacy_random_source': False,
        'noise': {'height': 384, 'min_y': -64, 'size_horizontal': 1, 'size_vertical': 2},
        'noise_router': dict(zero, final_density=hills, initial_density_without_jaggedness=hills),
        'ore_veins_enabled': False, 'sea_level': -63, 'spawn_target': [],
        'surface_rule': {'type': 'minecraft:sequence', 'sequence': [
            {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:vertical_gradient', 'random_name': 'iis:mars_bedrock', 'true_at_and_below': {'above_bottom': 0}, 'false_at_and_above': {'above_bottom': 5}},
             'then_run': {'type': 'minecraft:block', 'result_state': {'Name': 'minecraft:bedrock'}}},
            {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'add_surface_depth': False, 'offset': 0, 'secondary_depth_range': 0, 'surface_type': 'floor'},
             'then_run': {'type': 'minecraft:block', 'result_state': {'Name': I('mars_turf')}}},
            {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'add_surface_depth': False, 'offset': 3, 'secondary_depth_range': 0, 'surface_type': 'floor'},
             'then_run': {'type': 'minecraft:block', 'result_state': {'Name': I('mars_dirt')}}}]}})
    write(os.path.join(D, NS, 'dimension', 'mars.json'), {'type': I('mars'), 'generator': {'type': 'minecraft:noise', 'settings': I('mars'), 'biome_source': {'type': 'minecraft:fixed', 'biome': I('mars')}}})
    # --- asteroids (D134): no ground, rocks in the void; GC WorldProviderAsteroids for gravity and fuel (LowGravity, RocketEntity) -------------------------
    write(os.path.join(D, NS, 'dimension_type', 'asteroids.json'), {
        'ambient_light': 0.05, 'attributes': {
            'minecraft:gameplay/bed_rule': {'can_set_spawn': 'never', 'can_sleep': 'never', 'explodes': False},
            'minecraft:gameplay/respawn_anchor_works': False,
            'minecraft:visual/fog_color': '#000000', 'minecraft:visual/sky_color': '#000000', 'minecraft:visual/cloud_height': 2000.0},
        'coordinate_scale': 1.0, 'has_ceiling': False, 'has_ender_dragon_fight': False, 'has_fixed_time': False, 'has_skylight': True, 'height': 384,
        'infiniburn': '#minecraft:infiniburn_overworld', 'logical_height': 384, 'min_y': -64, 'monster_spawn_block_light_limit': 0,
        'monster_spawn_light_level': {'type': 'minecraft:uniform', 'max_inclusive': 7, 'min_inclusive': 0}, 'skybox': 'overworld', 'timelines': []})
    write(os.path.join(D, NS, 'worldgen', 'biome', 'asteroids.json'), {
        'attributes': {'minecraft:visual/sky_color': '#000000', 'minecraft:visual/fog_color': '#000000'}, 'carvers': [], 'downfall': 0.0, 'effects': {'water_color': '#3f76e4'},
        'features': [[], [], [], [], [], [], [I('gt_ore_veins')], [], [], [], []], 'has_precipitation': False, 'spawn_costs': {},
        'spawners': {'ambient': [], 'axolotls': [], 'creature': [], 'misc': [], 'underground_water_creature': [], 'water_ambient': [], 'water_creature': [], 'monster': []},
        'temperature': 0.5})
    # blobs of 3D noise between y = 0 and y = 180, thinning out at the edges of the band
    rocks = {'type': 'minecraft:add', 'argument1': -0.42,
             'argument2': {'type': 'minecraft:noise', 'noise': 'minecraft:cave_cheese', 'xz_scale': 1.2, 'y_scale': 1.6}}
    band = {'type': 'minecraft:min', 'argument1': {'type': 'minecraft:y_clamped_gradient', 'from_y': -10, 'to_y': 30, 'from_value': -1.0, 'to_value': 1.0},
            'argument2': {'type': 'minecraft:y_clamped_gradient', 'from_y': 150, 'to_y': 190, 'from_value': 1.0, 'to_value': -1.0}}
    asteroids = {'type': 'minecraft:min', 'argument1': rocks, 'argument2': band}
    write(os.path.join(D, NS, 'worldgen', 'noise_settings', 'asteroids.json'), {
        'aquifers_enabled': False, 'default_block': {'Name': I('asteroid_rock')}, 'default_fluid': {'Name': 'minecraft:air'}, 'disable_mob_generation': True, 'legacy_random_source': False,
        'noise': {'height': 384, 'min_y': -64, 'size_horizontal': 1, 'size_vertical': 2},
        'noise_router': dict(zero, final_density=asteroids, initial_density_without_jaggedness=asteroids),
        'ore_veins_enabled': False, 'sea_level': -63, 'spawn_target': [], 'surface_rule': {'type': 'minecraft:sequence', 'sequence': []}})
    write(os.path.join(D, NS, 'dimension', 'asteroids.json'), {'type': I('asteroids'), 'generator': {'type': 'minecraft:noise', 'settings': I('asteroids'), 'biome_source': {'type': 'minecraft:fixed', 'biome': I('asteroids')}}})
    write(os.path.join(D, NS, 'damage_type', 'oxygen_suffocation.json'), {'message_id': 'iis.oxygen_suffocation', 'exhaustion': 0.0, 'scaling': 'never'})

    # --- recipes: scripts/Galacticraft.zs ------------------------------------------------------------------------------------
    CI, CS, CA, CT = I('compressed_iron'), I('compressed_steel'), I('compressed_aluminium'), I('compressed_tin')
    HP, VENT, FAN, CONC, PIPE, BW, AW = I('heavy_plating'), I('air_vent'), I('air_fan'), I('oxygen_concentrator'), I('oxygen_pipe'), I('wafer_basic'), I('wafer_advanced')
    # ProcessingPlate:91: 4 plates + 4 TNT -> 2 compressed + 2 tiny dark ash
    dark = ref('tiny_dust', 'DarkAsh')
    for cid, mat, *_ in COMPRESSED:
        mrec('implosion', f'compressed_{cid}', [ing(tag_of('plate', mat), 4), ing('minecraft:tnt', 2)], [out(I(f'compressed_{cid}'), 2), out(dark, 2)], 20, 30)
    mrec('implosion', 'heavy_plating', [ing(I('heavy_ingot_t1')), ing('minecraft:tnt', 4)], [out(HP), out(ref('tiny_dust', 'StainlessSteel'), 2)], 20, 30)   # GT :1100
    # GT :1101 and Galacticraft.zs: tungsten bolts, duranium, europium, HSS-S, the T1 plate and compressed meteoric iron from the Moon
    mrec('implosion', 'heavy_plating_t2', [ing(I('heavy_ingot_t2')), ing('minecraft:tnt', 4)], [out(I('heavy_plating_t2')), out(ref('tiny_dust', 'Tungsten'), 2)], 20, 30)
    shaped('space/heavy_ingot_t2', I('heavy_ingot_t2'), 1, ['BBB', 'DhE', 'SPM'], {'B': tag_of('bolt', 'Tungsten'), 'D': tag_of('plate', 'Duranium'), 'E': tag_of('plate', 'Europium'), 'S': tag_of('plate', 'HSSS'),
                                                                                    'P': HP, 'M': I('compressed_meteoric_iron'), 'h': '#iis:tools/hammer'})
    # zs: bolts, double titanium, double PTFE, compressed bronze and steel with a hammer
    shaped('space/heavy_ingot_t1', I('heavy_ingot_t1'), 1, ['BTB', 'ChS', 'BPB'], {'B': tag_of('bolt', 'StainlessSteel'), 'T': tag_of('double_plate', 'Titanium'), 'P': tag_of('double_plate', 'Polytetrafluoroethylene'),
                                                                                    'C': I('compressed_bronze'), 'S': CS, 'h': '#iis:tools/hammer'})
    mrec('assembler', 'wafer_basic', [ing(tag_of('plate', 'Diamond'), 2), ing(I('coated_circuit_board'), 4)], [out(BW)], 1600, 64, fin=fl('molten_solderingalloy', 144))
    mrec('assembler', 'wafer_basic_tin', [ing(tag_of('plate', 'Diamond'), 2), ing(I('coated_circuit_board'), 4)], [out(BW)], 1600, 64, fin=fl('molten_tin', 288))
    mrec('assembler', 'wafer_advanced', [ing(tag_of('plate', 'Diamond'), 4), ing(I('circuit_board_epoxy'), 8)], [out(AW)], 3200, 256, fin=fl('molten_solderingalloy', 288))
    mrec('assembler', 'wafer_advanced_tin', [ing(tag_of('plate', 'Diamond'), 4), ing(I('circuit_board_epoxy'), 8)], [out(AW)], 3200, 256, fin=fl('molten_tin', 576))
    # ProcessingGem:42-43: vanilla gems get no generated plates; the wafers need diamond ones
    mrec('forge_hammer', 'plate_diamond', [ing('minecraft:diamond')], [out(ref('plate', 'Diamond'))], max(g['mass']('Diamond'), 1), 16)
    mrec('bender', 'gem_diamond', [ing('minecraft:diamond')], [out(ref('plate', 'Diamond'))], max(g['mass']('Diamond') * 2, 1), 24)
    # GT_MetaGenerated_Item_01:392 schematic (3x3): schematic and a screwdriver
    shaped('space/schematic_3x3', I('schematic_3x3'), 1, ['  d', ' P '], {'P': I('schematic'), 'd': '#iis:tools/screwdriver'})
    mrec('extruder', 'oxygen_pipe', [ing(I('reinforced_glass'))], [out(PIPE, 2)], 120, 128, keep=I('shape_extruder_pipe_small'))
    mrec('extruder', 'canister_tin', [ing(CT)], [out(I('canister_tin'))], 120, 32, keep=I('shape_extruder_cell'))
    mrec('bender', 'canister_tin', [ing(CT, 2)], [out(I('canister_tin'))], 200, 8)
    mrec('assembler', 'large_steel_cell', [ing(tag_of('double_plate', 'Steel')), ing(tag_of('ring', 'Steel'), 2)], [out(I('large_steel_cell'))], 200, 64)
    shaped('space/air_vent', VENT, 1, ['BTB', 'TST', 'BTB'], {'B': 'minecraft:iron_bars', 'T': CT, 'S': CS})
    shaped('space/air_fan', FAN, 1, ['S S', 'RWR', 'S S'], {'S': tag_of('screw', 'Steel'), 'R': tag_of('rotor', 'Steel'), 'W': BW})
    shaped('space/oxygen_concentrator', CONC, 1, ['SVS', 'SFS', 'CPC'], {'S': CS, 'V': VENT, 'F': FAN, 'C': I('canister_tin'), 'P': I('electric_pump_lv')})
    # zs: 8 reinforced glass around the hazmat helmet (our own since D117)
    shaped('space/oxygen_mask', I('oxygen_mask'), 1, ['GGG', 'GHG', 'GGG'], {'G': I('reinforced_glass'), 'H': I('hazmat_helmet')})
    shaped('space/oxygen_gear', I('oxygen_gear'), 1, ['PPP', 'LCL', 'P P'], {'P': PIPE, 'L': I('electric_pump_lv'), 'C': CONC})
    empty_cell = {'neoforge:ingredient_type': 'neoforge:components', 'items': I('capsule'), 'components': {f'!{NS}:fluid_content': {}}}
    empty_tank = {'id': I('oxygen_tank_light'), 'count': 1, 'components': {'minecraft:damage': 900}}
    for rid, c in (('capsule', empty_cell), ('canister', I('canister_tin'))):
        write(os.path.join(D, NS, 'recipe', 'space', f'oxygen_tank_light_{rid}.json'), {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['APA', 'ACA', 'AAA'],
              'key': {'A': CA, 'P': PIPE, 'C': c}, 'result': empty_tank})
    # zs: FluidCanner: an empty tank and 20000 L of oxygen
    mrec('fluid_canner', 'oxygen_tank_light', [ing({'neoforge:ingredient_type': 'neoforge:components', 'items': I('oxygen_tank_light'), 'components': {'minecraft:damage': 900}})],
         [{'id': I('oxygen_tank_light'), 'count': 1, 'components': {'minecraft:damage': 0}}], 200, 8, fin=fl('oxygen', 20000))
    shaped('space/rocket_engine_t1', I('rocket_engine_t1'), 1, ['SHS', 'RFR', 'HPH'], {'S': I('pipe_small_steel'), 'H': HP, 'R': I('regulator_mv'), 'F': I('titanium_firebox_casing'), 'P': I('pipe_huge_titanium')})
    shaped('space/nose_cone', I('nose_cone'), 1, [' H ', 'HEH', 'HCH'], {'H': HP, 'E': I('sensor_ev'), 'C': '#c:circuits/elite'})
    shaped('space/rocket_fins', I('rocket_fins'), 1, [' S ', 'HSH', 'H H'], {'H': HP, 'S': CS})
    shaped('space/canvas', I('canvas'), 1, [' XY', 'XXX', 'YX '], {'X': 'minecraft:string', 'Y': '#c:rods/wooden'})
    shaped('space/parachute', I('parachute'), 1, ['XXX', 'Y Y', ' Y '], {'X': I('canvas'), 'Y': 'minecraft:string'})
    shaped('space/nasa_workbench', I('nasa_workbench'), 1, ['TSV', 'AWB', 'MCE'], {'T': tag_of('plate', 'Titanium'), 'V': tag_of('plate', 'Titanium'), 'S': I('schematic_3x3'), 'A': I('robot_arm_ev'), 'B': I('robot_arm_hv'), 'W': AW,
                                                                                    'M': '#c:circuits/master', 'C': '#iis:cables/4x/platinum', 'E': '#c:circuits/elite'})
    shaped('space/launch_pad', I('launch_pad'), 3, ['III', 'AAA', 'BBB'], {'I': CI, 'A': I('advanced_alloy_plate'), 'B': 'minecraft:iron_block'})
    shaped('space/fuel_loader', I('fuel_loader'), 1, ['SWS', 'PLM', 'APA'], {'S': CS, 'W': BW, 'P': I('electric_pump_mv'), 'L': I('large_steel_cell'), 'M': I('electric_motor_mv'), 'A': CA})
    # --- D134: the third tier. GT_MachineRecipeLoader:1098, 1102 for the ingot and the plate, scripts/Galacticraft.zs for the parts and machines ---------
    shaped('space/heavy_ingot_t3', I('heavy_ingot_t3'), 1, [' BB', 'hPC', ' BB'], {'B': tag_of('bolt', 'TungstenSteel'), 'P': I('heavy_plating_t2'), 'C': I('compressed_desh'), 'h': '#iis:tools/hammer'})
    mrec('implosion', 'heavy_plating_t3', [ing(I('heavy_ingot_t3')), ing('minecraft:tnt', 4)], [out(I('heavy_plating_t3')), out(ref('tiny_dust', 'TungstenSteel'), 2)], 20, 30)
    T3P = I('heavy_plating_t3')
    shaped('space/heavy_rocket_engine', I('heavy_rocket_engine'), 1, ['PUP', 'PRP', 'EEE'], {'P': T3P, 'U': I('pipe_small_high_pressure'), 'R': I('fluid_regulator_iv'), 'E': I('rocket_engine_t1')})
    shaped('space/heavy_nose_cone', I('heavy_nose_cone'), 1, [' N ', 'ASA', 'ACA'], {'N': I('nose_cone'), 'A': tag_of('plate', 'Americium'), 'S': I('sensor_luv'), 'C': '#c:circuits/master'})
    # the GC recipe of the heavy fins is removed in the script without a replacement; GC shape with tungstensteel rods (D134, AI)
    shaped('space/heavy_rocket_fins', I('heavy_rocket_fins'), 1, [' Y ', 'XYX', 'X X'], {'X': T3P, 'Y': tag_of('long_rod', 'TungstenSteel')})
    shaped('space/rocket_booster', I('rocket_booster'), 1, ['EEE', 'HSH', 'HVH'], {'E': tag_of('plate', 'Europium'), 'H': HP, 'S': I('emitter_iv'), 'V': VENT})
    shaped('space/oxygen_collector', I('oxygen_collector'), 1, ['ACA', 'VFM', 'SWS'], {'A': CA, 'C': CONC, 'V': VENT, 'F': FAN, 'M': I('electric_motor_lv'), 'S': CS, 'W': I('cable_aluminium_2x')})
    shaped('space/oxygen_distributor', I('oxygen_distributor'), 1, ['AFA', 'VMV', 'SFS'], {'A': CA, 'F': FAN, 'V': VENT, 'M': I('electric_motor_lv'), 'S': CS})
    shaped('space/cargo_loader', I('cargo_loader'), 1, ['AHA', 'CBC', 'SPS'], {'A': CA, 'H': 'minecraft:hopper', 'C': I('conveyor_module_mv'), 'B': I('chest_buffer_mv'), 'S': CS, 'P': I('item_pipe_medium_brass')})
    shaped('space/cargo_unloader', I('cargo_unloader'), 1, ['SPS', 'CBC', 'AHA'], {'A': CA, 'H': 'minecraft:hopper', 'C': I('conveyor_module_mv'), 'B': I('chest_buffer_mv'), 'S': CS, 'P': I('item_pipe_medium_brass')})
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
