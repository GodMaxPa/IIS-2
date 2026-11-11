"""Railway by Railcraft 9.12 of the IIS 1 pack (D102), pass 1: track parts, tracks, switches, logic tracks, crowbars.

Recipes are those of IIS 1: GT5-II (GT_MachineRecipeLoader:1195-1210, 1438-1455, ProcessingSlab, GT_RailcraftRecipesLoader)
plus the pack script scripts/RailCraft.zs (assembler junctions/switches/wyes/boosters, concrete tracks, logic tracks by hand;
Railcraft rolling machine, rock crusher and blast furnace are removed there). Track outputs of the GT generic
"railbed + 6 rails" assembler recipe follow Railcraft EnumTrack#registerRecipe (recipeOutput x 2) and ModuleCore (vanilla rails).

Textures are our own: parts are drawn here, tracks reuse the vanilla rail textures with a tint per track kind
(Railcraft's license allows its png only in texture packs). Java side: ru.iis.rail.
"""
import os

from PIL import Image, ImageDraw

NS = 'iis'

KINDS = {  # TrackKind in Java: id -> (tint, en, ru)
    'wooden': (0x9C7A4E, 'Wooden', 'Деревянный'),
    'standard': (0xFFFFFF, '', ''),
    'reinforced': (0x6E7180, 'Reinforced', 'Усиленный'),
    'high_speed': (0xE0C060, 'High Speed', 'Скоростной'),
    'electric': (0xD9884A, 'Electric', 'Электрический'),
}
# id -> (class, kind, en, ru); classes: track, booster, transition, junction, switch, wye, logic:<type>
TRACKS = {}
for k, (_, en, ru) in KINDS.items():
    base = 'track' if k == 'standard' else f'track_{k}'
    pre_en, pre_ru = (en + ' ', ru + ' ') if en else ('', '')
    if k != 'standard':
        TRACKS[base] = ('track', k, f'{en} Track', f'{ru} путь')
    if k in ('wooden', 'reinforced', 'high_speed'):
        TRACKS[base + '_booster'] = ('booster', k, f'{pre_en}Booster Track', f'{pre_ru}путь-ускоритель'.capitalize())
    if k == 'high_speed':
        TRACKS[base + '_transition'] = ('transition', k, 'High Speed Transition Track', 'Переходный скоростной путь')
    if k != 'high_speed':
        TRACKS[base + '_junction'] = ('junction', k, f'{pre_en}Junction Track', f'{pre_ru}путь-крестовина'.capitalize())
    TRACKS[base + '_switch'] = ('switch', k, f'{pre_en}Switch Track', f'{pre_ru}путь-стрелка'.capitalize())
    TRACKS[base + '_wye'] = ('wye', k, f'{pre_en}Wye Track', f'{pre_ru}путь-тройник'.capitalize())
TRACKS['track_buffer_stop'] = ('logic:buffer_stop', 'standard', 'Buffer Stop', 'Путь-упор')
TRACKS['track_locking'] = ('logic:locking', 'standard', 'Locking Track', 'Блокирующий путь')
TRACKS['track_coupler'] = ('logic:coupler', 'standard', 'Coupler Track', 'Сцепной путь')
TRACKS['track_locomotive'] = ('logic:locomotive', 'standard', 'Locomotive Track', 'Локомотивный путь')

ITEMS = {  # id -> (en, ru)
    'tie_wood': ('Wooden Tie', 'Деревянная шпала'),
    'tie_stone': ('Stone Tie', 'Каменная шпала'),
    'railbed_wood': ('Wooden Railbed', 'Деревянное полотно'),
    'railbed_stone': ('Stone Railbed', 'Каменное полотно'),
    'rail_standard': ('Standard Rail', 'Обычный рельс'),
    'rail_advanced': ('Advanced Rail', 'Улучшенный рельс'),
    'rail_wooden': ('Wooden Rail', 'Деревянный рельс'),
    'rail_high_speed': ('High Speed Rail', 'Скоростной рельс'),
    'rail_reinforced': ('Reinforced Rail', 'Усиленный рельс'),
    'rail_electric': ('Electric Rail', 'Электрический рельс'),
    'rebar': ('Rebar', 'Арматура'),
    'circuit_controller': ('Controller Circuit', 'Схема управления'),
    'circuit_receiver': ('Receiver Circuit', 'Схема приёмника'),
    'circuit_signal': ('Signal Circuit', 'Сигнальная схема'),
    'crowbar_red': ('Crowbar', 'Монтировка Railcraft'),
    'crowbar_reinforced': ('Reinforced Crowbar', 'Усиленная монтировка'),
    'signal_lamp': ('Signal Lamp', 'Сигнальная лампа'),
    'signal_surveyor': ('Signal Block Surveyor', 'Геодезист сигнальных блоков'),
    'signal_tuner': ('Signal Tuner', 'Тюнер сигналов'),
    'trackman_goggles': ("Trackman's Goggles", 'Очки путейца'),
    'track_layer': ('Track Layer Cart', 'Путеукладчик'),
    'tunnel_bore': ('Tunnel Bore', 'Тоннельный бур'),
    'borehead_iron': ('Iron Bore Head', 'Железная буровая головка'),
    'borehead_steel': ('Steel Bore Head', 'Стальная буровая головка'),
    'borehead_diamond': ('Diamond Bore Head', 'Алмазная буровая головка'),
    'loco_steam': ('Steam Locomotive', 'Паровоз'),
    'loco_electric': ('Electric Locomotive', 'Электровоз'),
}


def generate(g):
    write, A, D, lang = g['write'], g['A'], g['D'], g['lang']
    block_tags, add_item_tag = g['block_tags'], g['add_item_tag']
    stats = {}

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def tex(rel):
        p = os.path.join(A, 'textures', rel + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    # ---------------------------------------------------------------------------------------------- part textures
    def canvas():
        return Image.new('RGBA', (16, 16), (0, 0, 0, 0))

    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

    def draw_tie(c):
        im = canvas(); d = ImageDraw.Draw(im)
        d.rectangle([1, 6, 14, 9], fill=c); d.line([1, 6, 14, 6], fill=shade(c, 1.25)); d.line([1, 9, 14, 9], fill=shade(c, 0.7))
        for x in (3, 12):
            d.rectangle([x, 6, x + 1, 9], fill=shade(c, 0.85))
        return im

    def draw_railbed(c):
        im = canvas(); d = ImageDraw.Draw(im)
        for y in (2, 6, 10):
            d.rectangle([1, y, 14, y + 2], fill=c); d.line([1, y, 14, y], fill=shade(c, 1.25)); d.line([1, y + 2, 14, y + 2], fill=shade(c, 0.7))
        return im

    def draw_rail(c):
        im = canvas(); d = ImageDraw.Draw(im)
        for i in range(12):
            x, y = 2 + i, 13 - i
            d.rectangle([x, y - 1, x + 1, y], fill=c)
            d.point((x, y - 2), fill=shade(c, 1.3))
            d.point((x + 1, y + 1), fill=shade(c, 0.6))
        return im

    def draw_rebar(c):
        im = canvas(); d = ImageDraw.Draw(im)
        for i in range(12):
            x, y = 2 + i, 13 - i
            d.point((x, y), fill=c); d.point((x + 1, y), fill=shade(c, 0.7))
            if i % 3 == 0:
                d.point((x, y - 1), fill=shade(c, 1.3))
        return im

    def draw_circuit(c):
        im = canvas(); d = ImageDraw.Draw(im)
        d.rectangle([2, 2, 13, 13], fill=(40, 90, 50, 255)); d.rectangle([2, 2, 13, 13], outline=(25, 60, 30, 255))
        d.rectangle([5, 5, 10, 10], fill=(30, 30, 30, 255)); d.rectangle([6, 6, 9, 9], fill=c)
        for x in (4, 7, 11):
            d.line([x, 3, x, 4], fill=(210, 180, 80, 255)); d.line([x, 11, x, 12], fill=(210, 180, 80, 255))
        return im

    def draw_crowbar(c):
        im = canvas(); d = ImageDraw.Draw(im)
        for i in range(10):
            d.point((3 + i, 13 - i), fill=c); d.point((4 + i, 13 - i), fill=shade(c, 0.7))
        d.line([12, 3, 14, 3], fill=c); d.line([14, 3, 14, 5], fill=shade(c, 0.8)); d.line([2, 12, 2, 14], fill=c)
        return im

    def draw_lamp():
        im = canvas(); d = ImageDraw.Draw(im)
        d.rectangle([4, 1, 11, 14], fill=(40, 40, 45, 255)); d.rectangle([4, 1, 11, 14], outline=(20, 20, 22, 255))
        for y, c in ((2, (60, 220, 60, 255)), (6, (240, 210, 40, 255)), (10, (220, 40, 30, 255))):
            d.ellipse([5, y, 10, y + 3], fill=c)
        return im

    def draw_tool(c):
        im = canvas(); d = ImageDraw.Draw(im)
        d.rectangle([3, 3, 12, 13], fill=(60, 60, 65, 255)); d.rectangle([3, 3, 12, 13], outline=(25, 25, 28, 255))
        d.rectangle([5, 5, 10, 8], fill=(150, 210, 230, 255)); d.rectangle([5, 10, 7, 11], fill=c); d.rectangle([9, 10, 10, 11], fill=(200, 200, 200, 255))
        d.line([11, 0, 11, 3], fill=(150, 150, 150, 255))
        return im

    def draw_goggles():
        im = canvas(); d = ImageDraw.Draw(im)
        d.line([0, 7, 15, 7], fill=(90, 60, 35, 255)); d.line([0, 8, 15, 8], fill=(70, 45, 25, 255))
        for x in (2, 9):
            d.ellipse([x, 4, x + 5, 10], fill=(110, 110, 115, 255)); d.ellipse([x + 1, 5, x + 4, 9], fill=(120, 220, 120, 255))
        return im

    def draw_maint(c):
        im = canvas(); d = ImageDraw.Draw(im)
        d.rectangle([1, 6, 14, 11], fill=(70, 72, 78, 255)); d.rectangle([3, 3, 12, 6], fill=c)
        d.line([2, 12, 13, 12], fill=(40, 40, 40, 255))
        for x in (3, 11):
            d.ellipse([x - 2, 11, x + 2, 15], fill=(30, 30, 30, 255))
        d.line([12, 2, 15, 0], fill=(150, 150, 150, 255))
        return im

    def draw_bore():
        im = canvas(); d = ImageDraw.Draw(im)
        d.rectangle([0, 4, 10, 11], fill=(80, 82, 90, 255)); d.rectangle([0, 4, 10, 11], outline=(45, 46, 50, 255))
        d.polygon([(10, 2), (15, 7), (15, 8), (10, 13)], fill=(170, 170, 175, 255))
        d.line([11, 4, 14, 7], fill=(120, 120, 125, 255)); d.line([11, 11, 14, 8], fill=(120, 120, 125, 255))
        for x in (2, 8):
            d.ellipse([x - 2, 11, x + 2, 15], fill=(30, 30, 30, 255))
        return im

    def draw_head(c):
        im = canvas(); d = ImageDraw.Draw(im)
        d.ellipse([1, 1, 14, 14], fill=shade(c, 0.7)); d.ellipse([3, 3, 12, 12], fill=c)
        for (x, y) in ((7, 0), (0, 7), (14, 7), (7, 14), (2, 2), (12, 2), (2, 12), (12, 12)):
            d.point((x, y), fill=shade(c, 1.3))
        d.ellipse([6, 6, 9, 9], fill=(40, 40, 40, 255))
        return im

    def draw_loco(body, frame, steam):
        im = canvas(); d = ImageDraw.Draw(im)
        d.rectangle([1, 10, 14, 12], fill=frame)
        for x in (3, 11):
            d.ellipse([x - 2, 11, x + 2, 15], fill=(30, 30, 30, 255)); d.point((x, 13), fill=(150, 150, 150, 255))
        if steam:
            d.rectangle([1, 5, 10, 9], fill=body); d.line([1, 5, 10, 5], fill=shade(body, 1.6))
            d.rectangle([10, 2, 14, 9], fill=shade(frame, 0.8)); d.rectangle([11, 3, 13, 5], fill=(220, 200, 120, 255))
            d.rectangle([3, 1, 4, 4], fill=(30, 30, 30, 255))
        else:
            d.rectangle([1, 3, 14, 9], fill=body); d.line([1, 3, 14, 3], fill=shade(body, 1.2))
            d.rectangle([2, 4, 4, 6], fill=(140, 200, 230, 255)); d.rectangle([11, 4, 13, 6], fill=(140, 200, 230, 255))
            d.line([6, 0, 9, 2], fill=(60, 60, 60, 255)); d.line([7, 2, 7, 3], fill=(60, 60, 60, 255))
        return im

    WOOD, STONE = (130, 95, 60, 255), (125, 125, 125, 255)
    RAILC = {'standard': (175, 175, 180, 255), 'advanced': (205, 170, 70, 255), 'wooden': (150, 110, 70, 255),
             'high_speed': (225, 200, 110, 255), 'reinforced': (80, 82, 95, 255), 'electric': (205, 120, 70, 255)}
    images = {'tie_wood': draw_tie(WOOD), 'tie_stone': draw_tie(STONE), 'railbed_wood': draw_railbed(WOOD), 'railbed_stone': draw_railbed(STONE),
              'rebar': draw_rebar((120, 70, 50, 255)), 'circuit_controller': draw_circuit((220, 50, 40, 255)),
              'circuit_receiver': draw_circuit((60, 200, 60, 255)), 'circuit_signal': draw_circuit((230, 210, 50, 255)),
              'crowbar_red': draw_crowbar((190, 40, 40, 255)), 'crowbar_reinforced': draw_crowbar((150, 155, 170, 255))}
    for k, c in RAILC.items():
        images[f'rail_{k}'] = draw_rail(c)
    images['signal_lamp'] = draw_lamp()
    images['signal_surveyor'] = draw_tool((70, 120, 200, 255))
    images['signal_tuner'] = draw_tool((200, 60, 50, 255))
    images['trackman_goggles'] = draw_goggles()
    images['track_layer'] = draw_maint((150, 120, 40, 255))
    images['tunnel_bore'] = draw_bore()
    for k, c in (('iron', (200, 200, 205, 255)), ('steel', (110, 115, 125, 255)), ('diamond', (90, 220, 210, 255))):
        images[f'borehead_{k}'] = draw_head(c)
    images['loco_steam'] = draw_loco((45, 45, 50, 255), (170, 40, 35, 255), True)
    images['loco_electric'] = draw_loco((205, 170, 40, 255), (70, 75, 85, 255), False)
    for iid, im in images.items():
        im.save(tex(f'item/rail/{iid}'))
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/handheld' if iid.startswith('crowbar') else 'minecraft:item/generated',
                                                                'textures': {'layer0': f'{NS}:item/rail/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', *ITEMS[iid])

    # ---------------------------------------------------------------------------------------------- track models
    M = os.path.join(A, 'models', 'block', 'rail')

    def plane(texture='#rail', y=1.0, rot=0):
        up = {'uv': [0, 0, 16, 16], 'texture': texture, 'tintindex': 0}
        down = {'uv': [0, 16, 16, 0], 'texture': texture, 'tintindex': 0}
        if rot:
            up['rotation'] = rot
            down['rotation'] = (360 - rot) % 360
        return {'from': [0, y, 0], 'to': [16, y, 16], 'faces': {'down': down, 'up': up}}

    def raised(angle):
        e = plane(y=9)
        e['rotation'] = {'origin': [8, 9, 8], 'axis': 'x', 'angle': angle, 'rescale': True}
        return e

    write(os.path.join(M, 'template_flat.json'), {'ambientocclusion': False, 'textures': {'particle': '#rail'}, 'elements': [plane()]})
    write(os.path.join(M, 'template_raised_ne.json'), {'ambientocclusion': False, 'textures': {'particle': '#rail'}, 'elements': [raised(45)]})
    write(os.path.join(M, 'template_raised_sw.json'), {'ambientocclusion': False, 'textures': {'particle': '#rail'}, 'elements': [raised(-45)]})

    def model(mid, parent, rail_tex, extra=None):
        m = {'parent': f'{NS}:block/rail/{parent}', 'textures': {'rail': rail_tex}}
        if extra:
            m.update(extra)
        write(os.path.join(M, mid + '.json'), m)
        return f'{NS}:block/rail/{mid}'

    def raw_model(mid, textures, elements):
        write(os.path.join(M, mid + '.json'), {'ambientocclusion': False, 'textures': textures, 'elements': elements})
        return f'{NS}:block/rail/{mid}'

    Y_STRAIGHT = {'north_south': 0, 'east_west': 90}
    Y_CORNER = {'south_east': 0, 'south_west': 90, 'north_west': 180, 'north_east': 270}
    Y_FACING = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

    def rail_variants(flat, corner, ne, sw, prefix=''):
        v = {}
        for s, y in Y_STRAIGHT.items():
            v[prefix + f'shape={s}'] = {'model': flat, 'y': y} if y else {'model': flat}
        if corner:
            for s, y in Y_CORNER.items():
                v[prefix + f'shape={s}'] = {'model': corner, 'y': y} if y else {'model': corner}
        v[prefix + 'shape=ascending_north'] = {'model': ne}
        v[prefix + 'shape=ascending_east'] = {'model': ne, 'y': 90}
        v[prefix + 'shape=ascending_south'] = {'model': sw}
        v[prefix + 'shape=ascending_west'] = {'model': sw, 'y': 90}
        return v

    # diverging curve of a switch: from the points side (facing opposite) to the branch (clockwise, or counter-clockwise if mirrored)
    CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
    CCW = {v: k for k, v in CW.items()}
    OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}

    def curve(a, b):
        ns = [s for s in (a, b) if s in ('north', 'south')][0]
        ew = [s for s in (a, b) if s in ('east', 'west')][0]
        return f'{ns}_{ew}'

    for bid, (cls, k, en, ru) in TRACKS.items():
        color = KINDS[k][0]
        item_tex = 'minecraft:block/rail'
        if cls == 'track':
            flat = model(bid, 'template_flat', 'minecraft:block/rail')
            corner = model(bid + '_corner', 'template_flat', 'minecraft:block/rail_corner')
            ne = model(bid + '_raised_ne', 'template_raised_ne', 'minecraft:block/rail')
            sw = model(bid + '_raised_sw', 'template_raised_sw', 'minecraft:block/rail')
            variants = rail_variants(flat, corner, ne, sw)
        elif cls in ('booster', 'transition'):
            variants = {}
            for on in (False, True):
                t = 'minecraft:block/powered_rail_on' if on else 'minecraft:block/powered_rail'
                suf = '_on' if on else ''
                flat = model(bid + suf, 'template_flat', t)
                ne = model(bid + suf + '_raised_ne', 'template_raised_ne', t)
                sw = model(bid + suf + '_raised_sw', 'template_raised_sw', t)
                variants.update(rail_variants(flat, None, ne, sw, prefix=f'powered={str(on).lower()},'))
            item_tex = 'minecraft:block/powered_rail'
        elif cls == 'junction':
            m = raw_model(bid, {'particle': 'minecraft:block/rail', 'rail': 'minecraft:block/rail'}, [plane(), plane(y=1.02, rot=90)])
            variants = {'': {'model': m}}
        elif cls == 'switch':
            straight = model(bid, 'template_flat', 'minecraft:block/rail')
            corner = model(bid + '_corner', 'template_flat', 'minecraft:block/rail_corner')
            variants = {}
            for f in Y_FACING:
                for mir in (False, True):
                    br = CCW[f] if mir else CW[f]
                    c = curve(OPP[f], br)
                    key = f'facing={f},mirrored={str(mir).lower()}'
                    ys = Y_STRAIGHT['north_south' if f in ('north', 'south') else 'east_west']
                    variants[key + ',switched=false'] = {'model': straight, 'y': ys} if ys else {'model': straight}
                    variants[key + ',switched=true'] = {'model': corner, 'y': Y_CORNER[c]} if Y_CORNER[c] else {'model': corner}
            item_tex = 'minecraft:block/rail_corner'
        elif cls == 'wye':
            # two curves from the stem (south for facing north): south_east (rotation 0) and south_west (rotation 90)
            m = raw_model(bid, {'particle': 'minecraft:block/rail_corner', 'rail': 'minecraft:block/rail_corner'}, [plane(), plane(y=1.02, rot=90)])
            variants = {f'facing={f}': ({'model': m, 'y': y} if y else {'model': m}) for f, y in Y_FACING.items()}
            item_tex = 'minecraft:block/rail_corner'
        else:   # logic
            kind = cls.split(':')[1]
            variants = {}
            if kind == 'buffer_stop':
                bumper = {'from': [1, 1, 0], 'to': [15, 9, 3], 'faces': {d: {'texture': '#bumper'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
                post = {'from': [6, 1, 3], 'to': [10, 5, 6], 'faces': {d: {'texture': '#post'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
                m = raw_model(bid, {'particle': 'minecraft:block/rail', 'rail': 'minecraft:block/rail', 'bumper': 'minecraft:block/stripped_oak_log',
                                    'post': 'minecraft:block/iron_block'}, [plane(), bumper, post])
                for f, y in Y_FACING.items():
                    variants[f'facing={f}'] = {'model': m, 'y': y} if y else {'model': m}
            else:
                base_t = {'locking': 'minecraft:block/activator_rail', 'locomotive': 'minecraft:block/powered_rail'}.get(kind, 'minecraft:block/detector_rail')
                for on in (False, True):
                    m = model(bid + ('_on' if on else ''), 'template_flat', base_t + ('_on' if on else ''))
                    for f, y in Y_FACING.items():
                        variants[f'facing={f},powered={str(on).lower()}'] = {'model': m, 'y': y} if y else {'model': m}
                item_tex = base_t
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        write(os.path.join(A, 'models', 'item', bid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': item_tex}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{bid}',
                                                                  'tints': [{'type': 'minecraft:constant', 'value': -16777216 | color}]}})
        name(f'block.{NS}.{bid}', en, ru)
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), g['simple_loot_table'](bid) if 'simple_loot_table' in g else {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}],
                                                  'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        block_tags.setdefault('minecraft:rails', []).append(f'{NS}:{bid}')
        block_tags.setdefault('minecraft:mineable/pickaxe', []).append(f'{NS}:{bid}')
    stats['tracks'] = len(TRACKS)

    # switch lever / motor (Railcraft signal:4 / signal:2)
    for bid, top, en, ru in (('switch_lever', 'minecraft:block/lever', 'Switch Lever', 'Переводной рычаг'),
                             ('switch_motor', 'minecraft:block/redstone_lamp', 'Switch Motor', 'Стрелочный привод')):
        for thrown in (False, True):
            post = {'from': [4, 0, 4], 'to': [12, 8, 12], 'faces': {d: {'texture': '#side'} for d in ('north', 'south', 'east', 'west', 'down')}}
            post['faces']['up'] = {'texture': '#top'}
            flag = {'from': [7, 8, 7], 'to': [9, 14, 9], 'faces': {d: {'texture': '#flag'} for d in ('north', 'south', 'east', 'west', 'up')}}
            raw_model(bid + ('_thrown' if thrown else ''), {'particle': 'minecraft:block/iron_block', 'side': 'minecraft:block/iron_block',
                                                           'top': top + ('_on' if thrown and 'lamp' in top else ''),
                                                           'flag': 'minecraft:block/red_wool' if thrown else 'minecraft:block/lime_wool'},
                      [post, flag])
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {
            'thrown=false': {'model': f'{NS}:block/rail/{bid}'}, 'thrown=true': {'model': f'{NS}:block/rail/{bid}_thrown'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/rail/{bid}'}})
        name(f'block.{NS}.{bid}', en, ru)
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}],
                                                  'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        block_tags.setdefault('minecraft:mineable/pickaxe', []).append(f'{NS}:{bid}')

    for cb in ('crowbar_red', 'crowbar_reinforced'):
        add_item_tag(f'{NS}:crowbars', f'{NS}:{cb}')
    add_item_tag(f'{NS}:crowbars', f'#{NS}:tools/crowbar')

    def simple_block_loot(bid, tool='minecraft:mineable/pickaxe'):
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}],
                                                  'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        block_tags.setdefault(tool, []).append(f'{NS}:{bid}')

    # ---------------------------------------------------------------------------------------------- recipes
    R = os.path.join(D, NS, 'recipe', 'rail')
    n_rec = [0]

    def ing(x, count=1):
        return {'ingredient': x, 'count': count}

    def machine(rid, typ, inputs, out, count, duration, eut, fluid=None, circuit=None):
        r = {'type': f'{NS}:{typ}', 'inputs': inputs, 'outputs': [{'id': out, 'count': count}], 'duration': duration, 'eut': eut}
        if fluid:
            r['fluid_inputs'] = [{'ingredient': fluid[0], 'amount': fluid[1]}]
        if circuit is not None:
            r['circuit'] = circuit
        write(os.path.join(R, rid + '.json'), r)
        n_rec[0] += 1

    def shaped(rid, pattern, key, out, count=1):
        write(os.path.join(R, rid + '.json'), {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key,
                                               'result': {'id': out, 'count': count}})
        n_rec[0] += 1

    def shapeless(rid, ingredients, out, count=1):
        write(os.path.join(R, rid + '.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ingredients,
                                               'result': {'id': out, 'count': count}})
        n_rec[0] += 1

    I = lambda x: f'{NS}:{x}'
    # ties: RailCraft.zs:575 chemical bath 1 wooden slab + 500 L creosote (160 t, 5 EU/t); stone tie GT_MachineRecipeLoader:1438
    machine('tie_wood', 'chemical_bath', [ing('#minecraft:wooden_slabs')], I('tie_wood'), 1, 160, 5, fluid=('iis:creosote', 500))
    for slab in ('minecraft:smooth_stone_slab', 'minecraft:quartz_slab'):
        machine(f'tie_stone_{slab.split(":")[1]}', 'assembler', [ing(slab, 3), ing(I('rebar'))], I('tie_stone'), 1, 128, 8)
    # railbeds: :1448-1449, 4 ties + circuit 4 (200 t, 4 EU/t)
    machine('railbed_wood', 'assembler', [ing(I('tie_wood'), 4)], I('railbed_wood'), 1, 200, 4, circuit=4)
    machine('railbed_stone', 'assembler', [ing(I('tie_stone'), 4)], I('railbed_stone'), 1, 200, 4, circuit=4)
    # rails and rebar from rods in the bender: :1195-1210
    for mat, rails, rebar, t in (('aluminium', 2, 4, 200), ('iron', 4, 8, 400), ('wrought_iron', 5, 10, 400), ('bronze', 3, 8, 300),
                                 ('steel', 8, 16, 800), ('stainless_steel', 12, 24, 1200), ('titanium', 16, 32, 1600)):
        machine(f'rail_standard_{mat}', 'bender', [ing(f'#c:rods/{mat}', 6)], I('rail_standard'), rails, t, 15)
        machine(f'rebar_{mat}', 'bender', [ing(f'#c:rods/{mat}', 12)], I('rebar'), rebar, 400 if mat == 'bronze' else t, 15)
    machine('rail_reinforced_tungsten_steel', 'bender', [ing('#c:rods/tungsten_steel', 6)], I('rail_reinforced'), 24, 2400, 30)
    machine('rebar_tungsten_steel', 'bender', [ing('#c:rods/tungsten_steel', 12)], I('rebar'), 48, 2400, 15)
    # :1442-1447 and RailCraft.zs:344
    machine('rail_high_speed', 'assembler', [ing('#c:ingots/steel', 3), ing('#c:plates/gold', 3)], I('rail_high_speed'), 8, 400, 4, fluid=('iis:molten_blaze', 432))
    machine('rail_advanced', 'assembler', [ing(I('rail_standard'), 3), ing('#c:plates/gold', 3)], I('rail_advanced'), 8, 400, 4, fluid=('iis:molten_redstone', 432))
    machine('rail_advanced_fine_wire', 'assembler', [ing(I('rail_standard')), ing('#iis:fine_wires/gold', 2)], I('rail_advanced'), 1, 100, 16, fluid=('iis:molten_redstone', 144))
    for w in ('copper', 'annealed_copper'):
        machine(f'rail_electric_{w}', 'assembler', [ing(I('rail_standard')), ing(f'#iis:wires/1x/{w}')], I('rail_electric'), 1, 50, 4)
    for p in ('iron', 'wrought_iron'):
        machine(f'rail_wooden_{p}', 'assembler', [ing(I('tie_wood'), 6), ing(f'#c:plates/{p}')], I('rail_wooden'), 6, 400, 4)
    # GT generic railbed + 6 rails = the Railcraft crafting output (:1450-1455); with 144 / 288 L redstone the booster grids
    for rail, bed, out, n in (('rail_standard', 'railbed_wood', 'minecraft:rail', 32), ('rail_wooden', 'railbed_wood', I('track_wooden'), 32),
                              ('rail_high_speed', 'railbed_stone', I('track_high_speed'), 32), ('rail_electric', 'railbed_stone', I('track_electric'), 32),
                              ('rail_reinforced', 'railbed_stone', I('track_reinforced'), 32)):
        machine(f'track_{out.split(":")[1]}_from_bed', 'assembler', [ing(I(bed)), ing(I(rail), 6)], out, n, 400, 4)
    machine('golden_rail_from_bed', 'assembler', [ing(I('railbed_wood')), ing(I('rail_advanced'), 6)], 'minecraft:powered_rail', 16, 400, 4, fluid=('iis:molten_redstone', 144))
    machine('track_high_speed_booster_from_bed', 'assembler', [ing(I('railbed_stone')), ing(I('rail_high_speed'), 6)], I('track_high_speed_booster'), 16, 400, 4,
            fluid=('iis:molten_redstone', 288))
    # RailCraft.zs:353-404: junctions, switches, wyes, boosters in the assembler (circuit not consumed)
    for base, track in (('', 'minecraft:rail'), ('wooden_', I('track_wooden'))):
        pre = 'track_' + base
        machine(f'{pre}junction', 'assembler', [ing(track, 2)], I(f'{pre}junction'), 1, 100, 16, circuit=1)
        machine(f'{pre}switch', 'assembler', [ing(track, 2)], I(f'{pre}switch'), 1, 100, 16, circuit=2)
        machine(f'{pre}wye', 'assembler', [ing(track, 2)], I(f'{pre}wye'), 1, 100, 16, circuit=3)
        boost = 'minecraft:powered_rail' if not base else I('track_wooden_booster')
        machine(f'{pre}booster', 'assembler', [ing(track, 2), ing('#iis:fine_wires/gold', 2)], boost, 1, 100, 16, fluid=('iis:molten_redstone', 144))
    machine('track_reinforced', 'assembler', [ing(I('rail_reinforced'), 3)], I('track_reinforced'), 16, 400, 4, fluid=('iis:molten_concrete', 576), circuit=1)
    for kind, c in (('junction', 2), ('switch', 3), ('wye', 4)):
        machine(f'track_reinforced_{kind}', 'assembler', [ing(I('track_reinforced'), 3)], I(f'track_reinforced_{kind}'), 8, 100, 16, fluid=('iis:molten_concrete', 288), circuit=c)
        machine(f'track_electric_{kind}', 'assembler', [ing(I('rail_electric'), 3)], I(f'track_electric_{kind}'), 8, 100, 16, fluid=('iis:molten_concrete', 288), circuit=c)
    machine('track_reinforced_booster', 'assembler', [ing(I('rail_reinforced')), ing(I('rail_advanced'))], I('track_reinforced_booster'), 4, 50, 16, fluid=('iis:molten_concrete', 144))
    machine('track_electric', 'assembler', [ing(I('rail_electric'), 3)], I('track_electric'), 16, 400, 4, fluid=('iis:molten_concrete', 576), circuit=1)
    machine('track_high_speed_transition', 'assembler', [ing(I('track_high_speed'), 2)], I('track_high_speed_transition'), 1, 100, 16, fluid=('iis:molten_redstone', 144), circuit=1)
    machine('track_high_speed_switch', 'assembler', [ing(I('track_high_speed'), 2)], I('track_high_speed_switch'), 1, 100, 16, circuit=2)
    machine('track_high_speed_wye', 'assembler', [ing(I('track_high_speed'), 2)], I('track_high_speed_wye'), 1, 100, 16, circuit=3)
    machine('track_high_speed_booster', 'assembler', [ing(I('track_high_speed'), 2), ing('#iis:fine_wires/gold', 2)], I('track_high_speed_booster'), 1, 100, 16,
            fluid=('iis:molten_redstone', 144))
    # RailCraft.zs:429-450: logic tracks by hand; vanilla activator and detector rails
    shapeless('activator_rail', ['minecraft:rail', 'minecraft:redstone_torch'], 'minecraft:activator_rail')
    shapeless('detector_rail', ['minecraft:powered_rail', 'minecraft:stone_pressure_plate'], 'minecraft:detector_rail')
    shapeless('track_buffer_stop', ['minecraft:rail', '#c:storage_blocks/iron'], I('track_buffer_stop'))
    shapeless('track_coupler', ['minecraft:powered_rail', f'#{NS}:tools/crowbar'], I('track_coupler'))
    shaped('track_locking', ['PBP'], {'P': 'minecraft:stone_pressure_plate', 'B': 'minecraft:powered_rail'}, I('track_locking'))
    # RailCraft.zs:174-180: switch motor and lever
    dyes = {'R': '#c:dyes/red', 'K': '#c:dyes/black', 'W': '#c:dyes/white', 'P': I('electric_piston_lv'), 'I': '#c:plates/iron'}
    shaped('switch_motor', ['RKW', 'PCI'], dict(dyes, C=I('circuit_receiver')), I('switch_motor'))
    shaped('switch_lever', ['RKW', 'PLI'], dict(dyes, L='minecraft:lever'), I('switch_lever'))
    # GT_RailcraftRecipesLoader:25-27: Railcraft circuits in the assembler (144 L soldering alloy, circuit 3)
    for cid, wire in (('circuit_controller', 'red_alloy'), ('circuit_receiver', 'gold'), ('circuit_signal', 'electrum')):
        machine(cid, 'assembler', [ing('#c:circuits/basic'), ing(f'#iis:fine_wires/{wire}', 4), ing(I('coated_circuit_board'))], I(cid), 1, 120, 5,
                fluid=('iis:molten_solderingalloy', 144), circuit=3)
    # GT_RailcraftRecipesLoader:120-121: crowbars "hDS / DSD / SDf" with red dye
    for cid, ingot in (('crowbar_red', 'minecraft:iron_ingot'), ('crowbar_reinforced', '#c:ingots/steel')):
        shaped(cid, ['hDS', 'DSD', 'SDf'], {'h': f'#{NS}:tools/hammer', 'f': f'#{NS}:tools/file', 'D': '#c:dyes/red', 'S': ingot}, I(cid))
    # --- locomotives and the electric feeder (pass 2) -------------------------------------------------------------
    # RailCraft.zs:229-240 (IIS 1): steam loco "BBF/BBF/ICC" with high-pressure coal boilers and bronze firebox casings;
    # electric loco "MAW/AGA/HBH" — the IC2 MFE cart is not ported, replaced by an MV 2x2 battery buffer (PORTING_NOTES)
    shaped('loco_steam', ['BBF', 'BBF', 'ICC'], {'B': I('boiler_coal_steel'), 'F': I('bronze_firebox_casing'), 'I': 'minecraft:iron_bars',
                                                 'C': 'minecraft:minecart'}, I('loco_steam'))
    shaped('loco_electric', ['MAW', 'AGA', 'HBH'], {'M': I('cover_controller'), 'A': '#c:circuits/advanced', 'W': f'#{NS}:tools/wrench',
                                                    'G': I('steel_gear_box_casing'), 'H': I('electric_motor_hv'), 'B': I('battery_buffer_2x2_mv')},
           I('loco_electric'))
    # GT_RailcraftRecipesLoader:114: electric feeder "PWP/WWW/PWP", iron plates and 2x copper wire
    shaped('electric_feeder', ['PWP', 'WWW', 'PWP'], {'P': '#c:plates/iron', 'W': f'#{NS}:wires/2x/copper'}, I('electric_feeder'))
    # --- pass 3: signals (ModuleSignals), detector (ModuleAutomation), loaders (ModuleTransport), tools ------------
    shaped('signal_lamp', ['PG ', 'PYT', 'PRS'], {'P': '#c:glass_panes/colorless', 'G': '#c:dyes/lime', 'Y': '#c:dyes/yellow', 'R': '#c:dyes/red',
                                                  'S': '#c:dusts/redstone', 'T': '#c:dusts/glowstone'}, I('signal_lamp'))
    shaped('block_signal', ['LCI', ' BI'], {'L': I('signal_lamp'), 'C': I('circuit_signal'), 'I': '#c:ingots/iron', 'B': '#c:dyes/black'}, I('block_signal'))
    shaped('signal_receiver_box', ['ICI', 'IRI'], {'I': '#c:ingots/iron', 'R': '#c:dusts/redstone', 'C': I('circuit_receiver')}, I('signal_receiver_box'))
    shaped('signal_controller_box', ['ICI', 'IRI'], {'I': '#c:ingots/iron', 'R': '#c:dusts/redstone', 'C': I('circuit_controller')}, I('signal_controller_box'))
    shaped('signal_surveyor', [' C ', 'BGB', ' R '], {'G': '#c:glass_panes/colorless', 'C': 'minecraft:compass', 'B': 'minecraft:stone_button',
                                                     'R': '#c:dusts/redstone'}, I('signal_surveyor'))
    shaped('signal_tuner', [' T ', 'BRB'], {'B': 'minecraft:stone_button', 'R': I('circuit_receiver'), 'T': 'minecraft:redstone_torch'}, I('signal_tuner'))
    shaped('trackman_goggles', ['GCG', 'I I', 'LLL'], {'C': I('circuit_receiver'), 'I': '#c:ingots/steel', 'L': 'minecraft:leather',
                                                      'G': '#c:glass_panes/colorless'}, I('trackman_goggles'))
    shaped('detector_item', ['XXX', 'XPX', 'XXX'], {'X': '#minecraft:planks', 'P': 'minecraft:stone_pressure_plate'}, I('detector_item'))
    shaped('item_loader', ['SSS', 'SLS', 'SDS'], {'S': '#c:cobblestones', 'D': I('detector_item'), 'L': 'minecraft:hopper'}, I('item_loader'))
    shaped('item_unloader', ['SSS', 'SDS', 'SLS'], {'S': '#c:cobblestones', 'D': I('detector_item'), 'L': 'minecraft:hopper'}, I('item_unloader'))
    # RailCraft.zs:322-334 (IIS 1): advanced loader/unloader with steel plates, redstone and an LV conveyor
    for b in ('item_loader', 'item_unloader'):
        shaped(b + '_advanced', ['PRP', 'RLR', 'PCP'], {'P': '#c:plates/steel', 'R': '#c:dusts/redstone', 'L': I(b), 'C': I('conveyor_module_lv')}, I(b + '_advanced'))
    # RailCraft.zs:445: locomotive track = standard track + signal lamp
    shapeless('track_locomotive', ['minecraft:rail', I('signal_lamp')], I('track_locomotive'))
    # RailCraft.zs:309-313 and 241-246 (IIS 1): track layer and tunnel bore; GT_RailcraftRecipesLoader:122-124: bore heads
    shaped('track_layer', ['RMR', 'CGC', 'AmA'], {'R': I('circuit_receiver'), 'M': I('cover_controller'), 'C': I('conveyor_module_lv'),
                                                  'G': I('steel_gear_box_casing'), 'A': I('robot_arm_lv'), 'm': 'minecraft:minecart'}, I('track_layer'))
    shaped('tunnel_bore', ['GLG', 'BLB', 'hTw'], {'G': I('steel_gear_box_casing'), 'L': I('loco_steam'), 'B': I('boiler_coal_steel'),
                                                  'h': f'#{NS}:tools/hammer', 'T': I('track_layer'), 'w': f'#{NS}:tools/wrench'}, I('tunnel_bore'))
    for k, blk in (('iron', '#c:storage_blocks/iron'), ('steel', '#c:storage_blocks/steel'), ('diamond', '#c:storage_blocks/diamond')):
        shaped(f'borehead_{k}', ['SSS', 'SBS', 'SSS'], {'S': '#c:ingots/steel', 'B': blk}, I(f'borehead_{k}'))
    stats['recipes'] = n_rec[0]

    def lamp_tex(c):
        im = Image.new('RGBA', (16, 16), (25, 25, 28, 255)); d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=(60, 60, 66, 255))
        d.ellipse([3, 3, 12, 12], fill=c)
        if c[0] + c[1] + c[2] > 200:
            d.ellipse([5, 4, 7, 6], fill=shade(c, 1.5))
        return im
    LAMPS = {'off': (45, 45, 50, 255), 'green': (60, 220, 60, 255), 'yellow': (240, 210, 40, 255), 'red': (220, 40, 30, 255)}
    for k, c in LAMPS.items():
        lamp_tex(c).save(tex(f'block/rail/lamp_{k}'))
    metal = Image.new('RGBA', (16, 16), (70, 72, 78, 255)); dm = ImageDraw.Draw(metal); dm.rectangle([0, 0, 15, 15], outline=(45, 46, 50, 255))
    metal.save(tex('block/rail/signal_metal'))
    box_top = {}
    for bid, col in (('signal_controller_box', (200, 60, 50, 255)), ('signal_receiver_box', (60, 180, 70, 255))):
        im = metal.copy(); d = ImageDraw.Draw(im); d.rectangle([2, 2, 13, 13], fill=(40, 40, 44, 255)); d.rectangle([4, 4, 11, 11], fill=col)
        im.save(tex(f'block/rail/{bid}_top')); box_top[bid] = f'{NS}:block/rail/{bid}_top'

    def cube(fr, to, faces):
        return {'from': fr, 'to': to, 'faces': faces}

    def all_faces(t, lamp_face=None):
        return {d: {'texture': '#lamp' if d == lamp_face else t} for d in ('north', 'south', 'east', 'west', 'up', 'down')}

    YF = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    signal_blocks = {'block_signal': ('Block Signal', 'Блок-сигнал'), 'signal_controller_box': ('Signal Controller Box', 'Коробка-контроллер сигнала'),
                     'signal_receiver_box': ('Signal Receiver Box', 'Коробка-приёмник сигнала')}
    for bid, (en, ru) in signal_blocks.items():
        variants = {}
        for lamp in LAMPS:
            t = {'particle': f'{NS}:block/rail/signal_metal', 'metal': f'{NS}:block/rail/signal_metal', 'lamp': f'{NS}:block/rail/lamp_{lamp}'}
            if bid == 'block_signal':
                els = [cube([7, 0, 7], [9, 8, 9], all_faces('#metal')), cube([5, 8, 6], [11, 16, 10], all_faces('#metal', 'north'))]
            else:
                t['top'] = box_top[bid]
                faces = all_faces('#metal', 'north')
                faces['up'] = {'texture': '#top'}
                els = [cube([2, 0, 2], [14, 10, 14], faces)]
            raw_model(bid + '_' + lamp, t, els)
            for f, y in YF.items():
                v = {'model': f'{NS}:block/rail/{bid}_{lamp}'}
                if y:
                    v['y'] = y
                variants[f'facing={f},lamp={lamp}'] = v
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/rail/{bid}_green'}})
        name(f'block.{NS}.{bid}', en, ru)
        simple_block_loot(bid)

    # item detector: planks with a pressure plate; loaders: cobblestone casings with a mouth on the cart side
    im = Image.new('RGBA', (16, 16), (150, 115, 70, 255)); d = ImageDraw.Draw(im)
    for y in (3, 7, 11, 15):
        d.line([0, y, 15, y], fill=(110, 82, 50, 255))
    d.rectangle([4, 4, 11, 11], fill=(125, 125, 125, 255)); d.rectangle([4, 4, 11, 11], outline=(90, 90, 90, 255))
    im.save(tex('block/rail/detector_item'))
    write(os.path.join(A, 'models', 'block', 'rail', 'detector_item.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/rail/detector_item'}})
    write(os.path.join(A, 'blockstates', 'detector_item.json'), {'variants': {'': {'model': f'{NS}:block/rail/detector_item'}}})
    write(os.path.join(A, 'items', 'detector_item.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/rail/detector_item'}})
    name(f'block.{NS}.detector_item', 'Item Detector', 'Детектор предметов')
    simple_block_loot('detector_item', 'minecraft:mineable/axe')

    loaders = (('item_loader', 'Item Loader', 'Погрузчик предметов', False), ('item_unloader', 'Item Unloader', 'Разгрузчик предметов', False),
               ('item_loader_advanced', 'Advanced Item Loader', 'Продвинутый погрузчик предметов', True),
               ('item_unloader_advanced', 'Advanced Item Unloader', 'Продвинутый разгрузчик предметов', True))
    for bid, en, ru, adv in loaders:
        base = (85, 88, 96, 255) if adv else (110, 110, 110, 255)
        side = Image.new('RGBA', (16, 16), base); d = ImageDraw.Draw(side)
        for (x, y) in ((2, 3), (9, 2), (5, 9), (11, 11), (1, 12)):
            d.rectangle([x, y, x + 3, y + 2], fill=shade(base, 0.8))
        d.rectangle([0, 0, 15, 15], outline=shade(base, 0.6))
        if 'unloader' in bid:
            d.polygon([(6, 10), (10, 10), (10, 8), (12, 8), (8, 4), (4, 8), (6, 8)], fill=(60, 160, 220, 255))
        else:
            d.polygon([(6, 6), (10, 6), (10, 8), (12, 8), (8, 12), (4, 8), (6, 8)], fill=(230, 180, 40, 255))
        side.save(tex(f'block/rail/{bid}_side'))
        mouth = side.copy(); dm2 = ImageDraw.Draw(mouth); dm2.rectangle([3, 3, 12, 12], fill=(30, 30, 30, 255)); dm2.rectangle([5, 5, 10, 10], fill=(55, 55, 55, 255))
        mouth.save(tex(f'block/rail/{bid}_mouth'))
        sidet, moutht = f'{NS}:block/rail/{bid}_side', f'{NS}:block/rail/{bid}_mouth'
        # the model's north face is the cart side; the blockstate turns it to FACING
        write(os.path.join(A, 'models', 'block', 'rail', bid + '.json'), {'parent': 'minecraft:block/cube', 'textures': {
            'particle': sidet, 'north': moutht, 'south': sidet, 'east': sidet, 'west': sidet, 'up': sidet, 'down': sidet}})
        rot = {'north': {}, 'south': {'y': 180}, 'east': {'y': 90}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {f'facing={f}': dict({'model': f'{NS}:block/rail/{bid}'}, **r) for f, r in rot.items()}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/rail/{bid}'}})
        name(f'block.{NS}.{bid}', en, ru)
        simple_block_loot(bid)

    for key, en, ru in (('aspect.green', 'green', 'зелёный'), ('aspect.yellow', 'yellow', 'жёлтый'), ('aspect.red', 'red', 'красный'),
                        ('aspect.blink_red', 'blinking red', 'мигающий красный'), ('aspect.off', 'off', 'выключен')):
        name(f'signal.{NS}.{key}', en, ru)
    for key, en, ru in (('signal.trigger', 'Emits redstone on: %s', 'Сигнал при показании: %s'),
                        ('signal.cleared', 'Selection cleared', 'Выбор сброшен'),
                        ('signal.surveyor.first', 'Signal selected: click the second block signal', 'Сигнал выбран: щёлкните второй блок-сигнал'),
                        ('signal.surveyor.wrong_first', 'Click a block signal', 'Щёлкните блок-сигнал'),
                        ('signal.tuner.first', 'Controller selected: click a receiver box', 'Контроллер выбран: щёлкните коробку-приёмник'),
                        ('signal.tuner.wrong_first', 'Click a controller box or a block signal first', 'Сначала щёлкните коробку-контроллер или блок-сигнал'),
                        ('signal.tuner.wrong_second', 'Click a receiver box', 'Щёлкните коробку-приёмник'),
                        ('signal.too_far', 'Too far: more than %s blocks', 'Слишком далеко: больше %s блоков'),
                        ('signal.paired', 'Paired', 'Спарено'),
                        ('goggles', 'Aspect: %s, %s', 'Показание: %s, %s'), ('goggles.unpaired', 'not paired', 'не спарен'),
                        ('goggles.paired', 'paired with %s %s %s', 'спарен с %s %s %s'),
                        ('detector.mode', 'Mode: %s', 'Режим: %s'), ('detector.mode.anything', 'any inventory cart', 'любая вагонетка с инвентарём'),
                        ('detector.mode.empty', 'empty', 'пустая'), ('detector.mode.full', 'full', 'полная'),
                        ('detector.mode.not_empty', 'not empty', 'не пустая'), ('detector.mode.analog', 'analog (by fill)', 'аналоговый (по заполнению)'),
                        ('rail.loco_track', 'Locomotives are set to: %s', 'Локомотивы переводятся в режим: %s')):
        name(f'message.{NS}.{key}', en, ru)
    block_tips = (
        ('block_signal', [('Pair two with a surveyor: red while a cart is between their tracks',
                           'Спарьте два геодезистом: красный, пока между их путями есть вагонетка')]),
        ('signal_controller_box', [('Green without redstone, red with it; tune a receiver to it',
                                    'Без сигнала зелёный, с сигналом красный; на него настраивают приёмник')]),
        ('signal_receiver_box', [('Emits redstone while its controller shows the chosen aspect (right-click)',
                                  'Подаёт сигнал, пока контроллер показывает выбранное (ПКМ)')]),
        ('detector_item', [('Redstone when a cart with an inventory is next to it; right-click: mode',
                            'Сигнал, когда рядом вагонетка с инвентарём; ПКМ — режим')]),
        ('item_loader', [('Loads the cart below, one item per tick; redstone when done', 'Грузит вагонетку под собой, по предмету за тик; сигнал, когда закончил')]),
        ('item_unloader', [('Unloads the cart above, one item per tick; redstone when done', 'Разгружает вагонетку над собой, по предмету за тик; сигнал, когда закончил')]),
        ('item_loader_advanced', [('Loads the cart in front', 'Грузит вагонетку перед собой')]),
        ('item_unloader_advanced', [('Unloads the cart in front', 'Разгружает вагонетку перед собой')]),
        ('track_locomotive', [('Powered: sets passing locomotives to its mode (crowbar: run / idle / stop)',
                               'С сигналом переводит локомотивы в свой режим (монтировка: ход / холостой / стоп)')]))
    for bid, lines in block_tips:
        for i, (en, ru) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{i}', en, ru)
    item_tips = (('track_layer', [('Lays track from its stock ahead of itself; max 0.1 blocks/tick', 'Кладёт путь из запаса перед собой; не быстрее 0.1 блока/тик')]),
                 ('tunnel_bore', [('Bores a 3x3 tunnel and lays track; needs a bore head and solid fuel', 'Бурит тоннель 3×3 и кладёт путь; нужны головка и твёрдое топливо'),
                                  ('Slots: head, 8 fuel / 9 ballast (gravel) / 9 tracks', 'Слоты: головка, 8 топлива / 9 балласта (гравий) / 9 путей')]),
                 ('borehead_iron', [('Durability 1500, iron level, speed 1.0', 'Прочность 1500, уровень железа, скорость 1.0')]),
                 ('borehead_steel', [('Durability 3000, iron level, speed 1.2', 'Прочность 3000, уровень железа, скорость 1.2')]),
                 ('borehead_diamond', [('Durability 6000, diamond level, speed 1.4', 'Прочность 6000, уровень алмаза, скорость 1.4')]),
                 ('signal_surveyor', [('Right-click two block signals to pair them', 'ПКМ по двум блок-сигналам — спарить')]),
                 ('signal_tuner', [('Right-click a controller, then a receiver box', 'ПКМ по контроллеру, затем по приёмнику')]),
                 ('trackman_goggles', [('Worn: shows the aspect and pairing of the signal you look at', 'Надетые показывают показание и пару сигнала под прицелом')]))
    for iid, lines in item_tips:
        for i, (en, ru) in enumerate(lines):
            name(f'item.{NS}.{iid}.tooltip.{i}', en, ru)

    im = canvas(); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], fill=(95, 98, 105, 255)); d.rectangle([0, 0, 15, 15], outline=(60, 62, 68, 255))
    d.rectangle([3, 3, 12, 12], fill=(50, 52, 58, 255))
    d.polygon([(9, 3), (5, 8), (8, 8), (6, 12), (11, 6), (8, 6)], fill=(235, 190, 50, 255))
    im.save(tex('block/rail/electric_feeder'))
    write(os.path.join(A, 'models', 'block', 'rail', 'electric_feeder.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/rail/electric_feeder'}})
    write(os.path.join(A, 'blockstates', 'electric_feeder.json'), {'variants': {'': {'model': f'{NS}:block/rail/electric_feeder'}}})
    write(os.path.join(A, 'items', 'electric_feeder.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/rail/electric_feeder'}})
    write(os.path.join(D, NS, 'loot_table', 'blocks', 'electric_feeder.json'), {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:electric_feeder'}],
                                              'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    block_tags.setdefault('minecraft:mineable/pickaxe', []).append(f'{NS}:electric_feeder')
    block_tags.setdefault('minecraft:needs_stone_tool', []).append(f'{NS}:electric_feeder')
    name(f'block.{NS}.electric_feeder', 'Electric Feeder', 'Электрический фидер')
    name(f'block.{NS}.electric_feeder.tooltip.0', 'Feeds electric tracks next to it from GT cables, up to 512 V',
         'Питает соседние электрические пути от проводов GT, до 512 В')
    name(f'message.{NS}.electric_feeder', 'Stored: %s / %s EU', 'Запас: %s / %s EU')
    # --- D146: water tank siding (Railcraft TANK_WATER; GT_RailcraftRecipesLoader:111 "PPP/ISI/PPP", IIS 1 railcraft_stuff_use_plates) --
    im = canvas(); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], fill=(150, 110, 62, 255))
    for y in (3, 7, 11):
        d.line([0, y, 15, y], fill=(118, 84, 46, 255))
    for x0, y0 in ((5, 0), (11, 4), (3, 8), (9, 12)):
        d.line([x0, y0, x0, y0 + 2], fill=(118, 84, 46, 255))
    for y in (1, 14):
        d.rectangle([0, y - 1, 15, y], fill=(110, 112, 118, 255))
        for x in (2, 7, 12):
            d.point((x, y - 1), fill=(170, 172, 178, 255))
    d.rectangle([0, 0, 15, 15], outline=(70, 50, 30, 255))
    im.save(tex('block/rail/water_tank_siding'))
    write(os.path.join(A, 'models', 'block', 'rail', 'water_tank_siding.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/rail/water_tank_siding'}})
    write(os.path.join(A, 'blockstates', 'water_tank_siding.json'), {'variants': {'': {'model': f'{NS}:block/rail/water_tank_siding'}}})
    write(os.path.join(A, 'items', 'water_tank_siding.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/rail/water_tank_siding'}})
    write(os.path.join(D, NS, 'loot_table', 'blocks', 'water_tank_siding.json'), {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:water_tank_siding'}],
                                              'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    block_tags.setdefault('minecraft:mineable/axe', []).append(f'{NS}:water_tank_siding')
    name(f'block.{NS}.water_tank_siding', 'Water Tank Siding', 'Обшивка водонапорной башни')
    for i, (en, ru) in enumerate((('Hollow 3x3x3 cube of 26 sidings collects water from the air', 'Полый куб 3×3×3 из 26 блоков собирает воду из воздуха'),
                                  ('More in rain, none in a desert, half without open sky; 400 buckets', 'В дождь больше, в пустыне нет, без неба вдвое меньше; 400 вёдер'),
                                  ('Gives water down from the bottom; pipes take it from any siding', 'Отдаёт воду вниз; трубы забирают из любого блока обшивки'))):
        name(f'block.{NS}.water_tank_siding.tooltip.{i}', en, ru)
    name(f'message.{NS}.water_tank', 'Water: %s / %s buckets', 'Вода: %s / %s вёдер')
    name(f'message.{NS}.water_tank.incomplete', 'Not a complete 3x3x3 tank', 'Куб 3×3×3 не собран')
    for slime in ('minecraft:slime_ball', f'{NS}:sticky_resin'):
        shaped(f'water_tank_siding_{slime.split(":")[1]}', ['PPP', 'ISI', 'PPP'], {'P': '#minecraft:planks', 'I': '#c:plates/iron', 'S': slime},
               I('water_tank_siding'), 6)
    for eid in ('loco_steam', 'loco_electric', 'track_layer', 'tunnel_bore'):
        name(f'entity.{NS}.{eid}', *ITEMS[eid])
    name(f'item.{NS}.loco_steam.tooltip.0', 'Water and solid fuel; boils at 100 °C, needs half a steam tank to move',
         'Вода и твёрдое топливо; кипит со 100 °C, едет от половины бака пара')
    name(f'item.{NS}.loco_steam.tooltip.1', 'Takes fuel from a linked chest cart', 'Берёт топливо из сцепленной сундучной вагонетки')
    name(f'item.{NS}.loco_electric.tooltip.0', 'Charges on electric tracks fed by an electric feeder', 'Заряжается на электрических путях от фидера')
    for key, en, ru in (('mode.running', 'Run', 'Ход'), ('mode.idle', 'Idle', 'Холост.'), ('mode.shutdown', 'Stop', 'Стоп'),
                        ('speed.max', 'Max', 'Макс'), ('speed.slow', '0.3', '0.3'), ('speed.slower', '0.2', '0.2'), ('speed.slowest', '0.1', '0.1'),
                        ('speed.reverse', 'Rev', 'Назад'), ('water', 'Water %s', 'Вода %s'), ('steam', 'Steam %s', 'Пар %s'),
                        ('heat', 'Heat %s°', 'Жар %s°'), ('charge', 'Charge %s / %s EU', 'Заряд %s / %s EU'),
                        ('electric_hint', 'Charges on electric track', 'Зарядка на электропути')):
        name(f'gui.{NS}.loco.{key}', en, ru)

    # vanilla rail crafting is replaced (Railcraft ModuleCore removes it; IIS 1 makes rails in the GT assembler)
    ovr = os.path.join(g['GEN'], 'packs', 'iis_overrides', 'data', 'minecraft', 'recipe')
    for r in ('rail', 'powered_rail', 'detector_rail', 'activator_rail'):
        write(os.path.join(ovr, r + '.json'), {'neoforge:conditions': [{'type': 'neoforge:never'}]})

    name(f'itemGroup.{NS}.rail', 'IIS 2.0: Railway', 'ИИС 2.0: Железная дорога')
    for key, en, ru in (('link_selected', 'Cart selected: click another cart to link', 'Вагонетка выбрана: щёлкните другую, чтобы сцепить'),
                        ('linked', 'Carts linked', 'Вагонетки сцеплены'), ('unlinked', 'Carts unlinked', 'Вагонетки расцеплены'),
                        ('link_failed', 'Cannot link: too far apart or no free coupling', 'Не сцепить: далеко или нет свободной сцепки'),
                        ('coupler', 'Mode: coupler', 'Режим: сцепка'), ('decoupler', 'Mode: decoupler', 'Режим: расцепка')):
        name(f'message.{NS}.rail.{key}', en, ru)
    for bid, lines in (('track_high_speed', [('Up to 0.8 blocks/tick once a booster speeds the cart up', 'До 0.8 блока/тик после разгона ускорителем'),
                                             ('A fast cart off this track or on a curve explodes', 'Быстрая вагонетка вне такого пути или на повороте взрывается')]),
                       ('track_high_speed_transition', [('Powered: speeds carts up; unpowered: slows them to 0.39', 'С сигналом разгоняет, без сигнала тормозит до 0.39')]),
                       ('track_wooden', [('Carts go no faster than 0.12 blocks/tick', 'Вагонетки не быстрее 0.12 блока/тик')]),
                       ('track_reinforced', [('Up to 0.5 blocks/tick, explosion-proof', 'До 0.5 блока/тик, не боится взрывов')]),
                       ('track_locking', [('Holds a cart; redstone releases it', 'Держит вагонетку, красный сигнал отпускает')]),
                       ('track_coupler', [('Powered: couples the passing cart (crowbar: decoupler mode)', 'С сигналом сцепляет проходящую вагонетку (монтировка — режим расцепки)')]),
                       ('track_buffer_stop', [('Stops carts heading into the bumper', 'Останавливает вагонетки перед брусом')]),
                       ('switch_lever', [('Right-click: throws the switch tracks next to it', 'ПКМ — переводит соседние стрелки')]),
                       ('switch_motor', [('Redstone throws the switch tracks next to it', 'Красный сигнал переводит соседние стрелки')]),
                       ('crowbar_red', [('Right-click two carts to link or unlink them', 'ПКМ по двум вагонеткам — сцепить или расцепить')]),
                       ('crowbar_reinforced', [('Right-click two carts to link or unlink them', 'ПКМ по двум вагонеткам — сцепить или расцепить')])):
        for i, (en, ru) in enumerate(lines):
            name(f'{"item" if bid.startswith("crowbar") else "block"}.{NS}.{bid}.tooltip.{i}', en, ru)
    for bid in TRACKS:
        if bid.endswith('_switch') or bid.endswith('_wye'):
            name(f'block.{NS}.{bid}.tooltip.0', 'Thrown by a switch lever or motor next to it; crowbar mirrors a switch',
                 'Переводится рычагом или приводом рядом; монтировка зеркалит стрелку')
    return stats
