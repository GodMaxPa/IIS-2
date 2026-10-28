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

    WOOD, STONE = (130, 95, 60, 255), (125, 125, 125, 255)
    RAILC = {'standard': (175, 175, 180, 255), 'advanced': (205, 170, 70, 255), 'wooden': (150, 110, 70, 255),
             'high_speed': (225, 200, 110, 255), 'reinforced': (80, 82, 95, 255), 'electric': (205, 120, 70, 255)}
    images = {'tie_wood': draw_tie(WOOD), 'tie_stone': draw_tie(STONE), 'railbed_wood': draw_railbed(WOOD), 'railbed_stone': draw_railbed(STONE),
              'rebar': draw_rebar((120, 70, 50, 255)), 'circuit_controller': draw_circuit((220, 50, 40, 255)),
              'circuit_receiver': draw_circuit((60, 200, 60, 255)), 'circuit_signal': draw_circuit((230, 210, 50, 255)),
              'crowbar_red': draw_crowbar((190, 40, 40, 255)), 'crowbar_reinforced': draw_crowbar((150, 155, 170, 255))}
    for k, c in RAILC.items():
        images[f'rail_{k}'] = draw_rail(c)
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
                base_t = 'minecraft:block/activator_rail' if kind == 'locking' else 'minecraft:block/detector_rail'
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
    stats['recipes'] = n_rec[0]

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
