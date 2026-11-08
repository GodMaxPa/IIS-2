"""Shields of the Cyber path and automatic turrets in the manner of Open Modular Turrets (D140).

Turret numbers: IIS 1 config/openmodularturrets.cfg (OMT 2.3.7); the OMT jar is not in the IIS 1 folder, so models, recipes and textures are our own.
Recipes, shield numbers and the slot layout are proposals for the owner.
"""
import os

from PIL import Image, ImageDraw

from mesh_art import Mesh, darker

NS = 'iis'
I = lambda x: f'{NS}:{x}'
MC = lambda x: f'minecraft:{x}'

STEEL = (150, 155, 165)
DARK = (40, 42, 48)
OLIVE = (84, 92, 58)
GUNMETAL = (78, 82, 92)

KINDS = [  # id, en, ru, tier, colour
    ('disposable', 'Disposable Item Turret', 'Одноразовая турель', 1, (120, 110, 100)),
    ('potato', 'Potato Cannon Turret', 'Картофельная пушка', 1, (150, 120, 70)),
    ('machine_gun', 'Machine Gun Turret', 'Пулемётная турель', 2, GUNMETAL),
    ('incendiary', 'Incendiary Turret', 'Зажигательная турель', 2, (150, 70, 40)),
    ('grenade', 'Grenade Launcher Turret', 'Гранатомётная турель', 3, OLIVE),
    ('relativistic', 'Relativistic Turret', 'Релятивистская турель', 3, (60, 90, 150)),
    ('rocket', 'Rocket Launcher Turret', 'Ракетная турель', 4, (90, 100, 64)),
    ('teleporter', 'Teleporter Turret', 'Турель-телепортатор', 4, (90, 50, 120)),
    ('laser', 'Laser Turret', 'Лазерная турель', 4, (170, 50, 50)),
    ('railgun', 'Railgun Turret', 'Рельсотрон', 5, (60, 70, 110)),
]
BASES = [(1, (120, 120, 120)), (2, (110, 125, 150)), (3, (150, 160, 175)), (4, (190, 120, 60)), (5, (70, 70, 80))]


def head(kind, colour):
    m = Mesh(f'turret_{kind}')
    m.part('turret', pivot=(0, 0, 0), anim='turret')
    m.cyl('y', (0, -4, 0), 4.5, 1.5, DARK, n=12, mat='metal')
    m.box(-3.5, -2.5, -3.5, 3.5, 1.5, 3.5, colour, 'plate')
    m.sym(3.5, -2, -2, 4.5, 3.5, 2, darker(colour, 0.8), 'panel')
    m.part('barrel', pivot=(0, 1, 0), anim='barrel', parent='turret')
    if kind == 'disposable':
        m.box(-1.5, -0.5, -2, 1.5, 2.5, 4, darker(colour, 0.9), 'scratch')
        m.cyl('z', (0, 1, 4), 1, 4, DARK, n=8, mat='metal')
    elif kind == 'potato':
        m.cyl('z', (0, 1, -2), 2.4, 10, (120, 100, 60), n=10, mat='metal', cap_color=DARK)
    elif kind == 'machine_gun':
        m.box(-2, -0.5, -3, 2, 3, 3, DARK, 'grill')
        for x in (-1, 1):
            m.cyl('z', (x, 1.2, 3), 0.6, 8, STEEL, n=6, mat='metal')
        m.cyl('x', (-3.5, 0.5, 0), 1.6, 1.5, (60, 60, 64), n=8, mat='metal')
    elif kind == 'incendiary':
        m.cyl('z', (0, 1.2, -2), 1.2, 10, STEEL, n=8, mat='metal')
        m.cyl('z', (0, 1.2, 8), 1.5, 1, DARK, n=8, mat='grill')
        m.cyl('z', (0, -0.8, -3), 1.4, 5, (210, 120, 30), n=8, mat='paint')
    elif kind == 'grenade':
        m.cyl('z', (0, 1, -3), 2, 9, OLIVE, n=10, mat='scratch', cap_color=DARK)
        m.box(-2.6, -0.8, -3, 2.6, 0.2, 2, DARK, 'metal')
    elif kind == 'relativistic':
        m.cyl('z', (0, 1, -1), 1, 4, STEEL, n=8, mat='metal')
        m.cyl('z', (0, 1, 3), 3.5, 0.8, (70, 110, 200), n=12, mat='panel', r2=1.5, glow=True)
    elif kind == 'rocket':
        m.box(-3, -1, -3, 3, 4, 5, colour, 'plate')
        for x in (-1.5, 1.5):
            for y in (0.2, 2.8):
                m.cyl('z', (x, y, 5), 1, 0.4, DARK, n=8, mat='grill')
    elif kind == 'teleporter':
        m.cyl('z', (0, 1, -1), 1.2, 3, STEEL, n=8, mat='metal')
        m.loft([(2, 0, 1, 0.5, 0.5), (3, 0, 1, 2.6, 2.6), (6, 0, 1, 2.6, 2.6), (7, 0, 1, 0.5, 0.5)], (170, 80, 220), n=10, mat='plain', glow=True)
    elif kind == 'laser':
        m.cyl('z', (0, 1, -2), 1.4, 6, STEEL, n=8, mat='metal')
        m.cyl('z', (0, 1, 4), 0.7, 5, DARK, n=8, mat='metal')
        m.cyl('z', (0, 1, 9), 0.5, 0.6, (255, 60, 50), n=8, mat='plain', glow=True)
    elif kind == 'railgun':
        m.box(-2, -0.5, -3, 2, 2.5, 2, colour, 'plate')
        for x in (-1.3, 1.3):
            m.box(x - 0.4, 0, 2, x + 0.4, 2, 12, STEEL, 'metal')
        m.box(-0.9, 0.6, 2, 0.9, 1.4, 11, (80, 170, 255), 'plain', glow=True)
    return m


def shield_ballistic():
    """Vanilla shield geometry after its (1, -1, -1) flip, moved by -8 px as the mesh item renderer centres the block."""
    m = Mesh('ballistic_shield')
    body = (88, 96, 84)
    m.box(-14.5, -19.5, -7.2, -1.5, 3.5, -6, body, 'plate')
    m.box(-14.5, -19.5, -6, -1.5, -18.5, -5.4, darker(body, 0.75), 'metal')
    m.box(-14.5, 2.5, -6, -1.5, 3.5, -5.4, darker(body, 0.75), 'metal')
    m.box(-12, -2, -7.4, -4, 0.5, -7.2, (90, 140, 170), 'plain', glass=True)
    m.box(-9, -11, -12.5, -7, -5, -7.2, DARK, 'rubber')
    return m


def shield_force():
    m = Mesh('force_shield')
    m.box(-9.5, -12, -12.5, -6.5, -4, -7.2, DARK, 'rubber')
    m.box(-12, -14, -8, -4, -2, -6.6, (60, 64, 76), 'panel')
    m.box(-11, -13, -6.6, -5, -12, -6.2, (80, 200, 255), 'plain', glow=True)
    m.box(-15, -20, -6.4, -1, 4, -6.1, (90, 190, 255), 'plain', glass=True)
    return m


SHIELD_DISPLAY = {
    'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [10, 6, -4], 'scale': [1, 1, 1]},
    'thirdperson_lefthand': {'rotation': [0, 90, 0], 'translation': [10, 6, 12], 'scale': [1, 1, 1]},
    'firstperson_righthand': {'rotation': [0, 180, 5], 'translation': [-10, 1.75, -10], 'scale': [1.25, 1.25, 1.25]},
    'firstperson_lefthand': {'rotation': [0, 180, 5], 'translation': [10, 0, -10], 'scale': [1.25, 1.25, 1.25]},
    'gui': {'rotation': [15, -25, -5], 'translation': [2, 3, 0], 'scale': [0.65, 0.65, 0.65]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [-4.5, 4.5, -5], 'scale': [0.55, 0.55, 0.55]},
    'ground': {'rotation': [0, 0, 0], 'translation': [2, 4, 2], 'scale': [0.25, 0.25, 0.25]}}
SHIELD_BLOCKING = dict(SHIELD_DISPLAY, **{
    'thirdperson_righthand': {'rotation': [45, 155, 0], 'translation': [-3.49, 11, -2], 'scale': [1, 1, 1]},
    'thirdperson_lefthand': {'rotation': [45, 155, 0], 'translation': [11.51, 7, 2.5], 'scale': [1, 1, 1]},
    'firstperson_righthand': {'rotation': [0, 180, -5], 'translation': [-15, 3.25, -11], 'scale': [1.25, 1.25, 1.25]},
    'firstperson_lefthand': {'rotation': [0, 180, -5], 'translation': [5, 5, -11], 'scale': [1.25, 1.25, 1.25]}})
TURRET_ITEM_DISPLAY = {
    'gui': {'rotation': [25, 215, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.4, 0.4, 0.4]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 8, 0], 'scale': [0.6, 0.6, 0.6]},
    'firstperson_righthand': {'rotation': [10, 200, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'firstperson_lefthand': {'rotation': [10, 160, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [75, 180, 0], 'translation': [0, 2, 1], 'scale': [0.4, 0.4, 0.4]},
    'thirdperson_lefthand': {'rotation': [75, 180, 0], 'translation': [0, 2, 1], 'scale': [0.4, 0.4, 0.4]}}


def generate(g):
    A, D, write, lang, shaped, tag_of = g['A'], g['D'], g['write'], g['lang'], g['shaped'], g['tag_of']
    rec = {}
    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1: {'id': i, 'count': c}
    P = lambda m: tag_of('plate', m)
    C = '#c:circuits/'

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def save_tex(rel, im):
        p = os.path.join(A, 'textures', rel + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)

    def mesh_special(base, mesh):
        return {'type': 'minecraft:special', 'base': base, 'model': {'type': f'{NS}:mesh', 'mesh': mesh}}

    def flip_z(m):
        # D142: the shield was the other way round in depth: in the blocking pose of the hand it went behind the camera
        for part in m.parts:
            # with the vanilla (1, -1, -1) item transformation: the mesh is in vanilla model space (y down, z back), minus 8 for the renderer's centring
            part.quads = [([(x, -16 - y, -16 - z) for x, y, z in q], col, mat, fl) for q, col, mat, fl in part.quads]
        return m

    meshes = [head(k, c) for k, _, _, _, c in KINDS] + [flip_z(shield_ballistic()), flip_z(shield_force())]
    for m in meshes:
        m.write(os.path.join(A, 'meshes', m.name + '.json'))

    # --- shields --------------------------------------------------------------------------------------------------------------
    for sid, en, ru in (('ballistic_shield', 'Ballistic Shield', 'Баллистический щит'), ('force_shield', 'Force Shield', 'Силовой щит')):
        for suffix, disp in (('', SHIELD_DISPLAY), ('_blocking', SHIELD_BLOCKING)):
            write(os.path.join(A, 'models', 'item', 'mesh', sid + suffix + '.json'), {'gui_light': 'front', 'textures': {'particle': 'minecraft:block/iron_block'}, 'display': disp})
        write(os.path.join(A, 'items', sid + '.json'), {'model': {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                                                                  'on_false': mesh_special(f'{NS}:item/mesh/{sid}', sid),
                                                                  'on_true': mesh_special(f'{NS}:item/mesh/{sid}_blocking', sid),
                                                                  'transformation': {'left_rotation': [0, 0, 0, 1], 'right_rotation': [0, 0, 0, 1], 'scale': [1, -1, -1], 'translation': [0, 0, 0]}},
                                                        'hand_animation_on_swap': False})
        name(f'item.{NS}.{sid}', en, ru)
    name('item.iis.ballistic_shield.tooltip.0', 'MV. Holds bullets and blows; blasts by half, lasers and plasma by a third', 'MV. Держит пули и удары; взрывы — наполовину, лазер и плазму — на треть')
    name('item.iis.force_shield.tooltip.0', 'IV. Holds everything from the front; spends 5000 EU per blocked heart half instead of wearing out',
         'IV. Держит всё спереди; вместо износа тратит 5000 EU за каждую остановленную единицу урона')

    # --- turret bases ------------------------------------------------------------------------------------------------------------
    for tier, col in BASES:
        bid = f'turret_base_t{tier}'
        for face in ('side', 'top'):
            im = Image.new('RGBA', (16, 16), col + (255,))
            d = ImageDraw.Draw(im)
            d.rectangle([0, 0, 15, 15], outline=darker(col, 0.6) + (255,))
            if face == 'side':
                d.rectangle([3, 3, 12, 12], fill=darker(col, 0.5) + (255,))
                for y in range(4, 12, 2):
                    d.line([(4, y), (11, y)], fill=darker(col, 0.8) + (255,))
                for k in range(tier):
                    d.point((2 + 3 * k, 14), fill=(220, 60, 40, 255))
            else:
                d.ellipse([4, 4, 11, 11], fill=darker(col, 0.7) + (255,), outline=darker(col, 0.45) + (255,))
            save_tex(f'block/defense/{bid}_{face}', im)
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_column', 'textures': {
            'side': f'{NS}:block/defense/{bid}_side', 'end': f'{NS}:block/defense/{bid}_top'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'models', 'item', bid + '.json'), {'parent': f'{NS}:block/{bid}'})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': I(bid)}]}]})
        g['block_tags']['minecraft:mineable/pickaxe'].append(I(bid))
        cap = [500, 50000, 150000, 500000, 10000000][tier - 1]
        name(f'block.{NS}.{bid}', f'Turret Base (Tier {tier})', f'База турели ({tier} уровень)')
        name(f'block.{NS}.{bid}.tooltip.0', f'Stores {cap // 8} EU; turrets up to tier {tier} on any side; 9 ammo slots, 9 for upgrades and addons',
             f'Запас {cap // 8} EU; турели до {tier} уровня на любой грани; 9 слотов патронов, 9 — улучшений и дополнений')
        name(f'block.{NS}.{bid}.tooltip.1', 'Owner: sneak + right click with empty hand changes targets', 'Владелец: Shift + ПКМ пустой рукой — выбор целей')

    # --- turrets -----------------------------------------------------------------------------------------------------------------
    mount_tex = f'{NS}:block/defense/turret_mount'
    im = Image.new('RGBA', (16, 16), (70, 72, 80, 255))
    ImageDraw.Draw(im).rectangle([0, 0, 15, 15], outline=(40, 40, 46, 255))
    save_tex('block/defense/turret_mount', im)
    write(os.path.join(A, 'models', 'block', 'turret_mount.json'), {'textures': {'all': mount_tex, 'particle': mount_tex}, 'elements': [
        {'from': [3, 0, 3], 'to': [13, 1, 13], 'faces': {f: {'texture': '#all'} for f in ('down', 'up', 'north', 'south', 'west', 'east')}},
        {'from': [6, 1, 6], 'to': [10, 4, 10], 'faces': {f: {'texture': '#all'} for f in ('up', 'north', 'south', 'west', 'east')}}]})
    rot = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'south': {'x': 90, 'y': 180}, 'east': {'x': 90, 'y': 90}, 'west': {'x': 90, 'y': 270}}
    for kid, en, ru, tier, col in KINDS:
        tid = f'turret_{kid}'
        write(os.path.join(A, 'blockstates', tid + '.json'), {'variants': {f'facing={f}': {'model': f'{NS}:block/turret_mount', **r} for f, r in rot.items()}})
        write(os.path.join(A, 'models', 'item', 'mesh', tid + '.json'), {'gui_light': 'front', 'textures': {'particle': mount_tex}, 'display': TURRET_ITEM_DISPLAY})
        write(os.path.join(A, 'items', tid + '.json'), {'model': mesh_special(f'{NS}:item/mesh/{tid}', tid), 'hand_animation_on_swap': False})
        write(os.path.join(D, NS, 'loot_table', 'blocks', tid + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': I(tid)}]}]})
        g['block_tags']['minecraft:mineable/pickaxe'].append(I(tid))
        name(f'block.{NS}.{tid}', en, ru)
    tips = {'disposable': ('Shoots cobblestone from the base', 'Стреляет булыжником из базы'), 'potato': ('Shoots potatoes', 'Стреляет картошкой'),
            'machine_gun': ('Shoots rifle rounds', 'Стреляет винтовочными патронами'), 'incendiary': ('Shoots blaze powder, sets on fire', 'Стреляет огненным порошком, поджигает'),
            'grenade': ('Shoots grenade rounds', 'Стреляет гранатами'), 'relativistic': ('Slows targets down, needs no ammo', 'Замедляет цели, патроны не нужны'),
            'rocket': ('Shoots rockets', 'Стреляет ракетами'), 'teleporter': ('Throws targets away, needs no ammo', 'Отбрасывает цели телепортацией, патроны не нужны'),
            'laser': ('Burns targets, needs no ammo', 'Прожигает цели, патроны не нужны'), 'railgun': ('Shoots ferrous slugs through everything in line', 'Стреляет ферросердечниками насквозь')}
    for kid, en, ru, tier, col in KINDS:
        e, r = tips[kid]
        name(f'block.{NS}.turret_{kid}.tooltip.0', f'Tier {tier}. {e}', f'{tier} уровень. {r}')
        name(f'block.{NS}.turret_{kid}.tooltip.1', 'Place on a turret base', 'Ставится на базу турели')

    # --- upgrades, addons, ammo ----------------------------------------------------------------------------------------------------
    def icon(iid, en, ru, fn, tip=None):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        fn(ImageDraw.Draw(im))
        save_tex(f'item/defense/{iid}', im)
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/defense/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)
        if tip:
            name(f'item.{NS}.{iid}.tooltip.0', *tip)

    def chip(colour, mark):
        def f(d):
            d.rectangle([2, 3, 13, 12], fill=(40, 90, 50, 255), outline=(20, 50, 25, 255))
            for x in (4, 7, 10):
                d.line([(x, 1), (x, 2)], fill=(200, 180, 80, 255))
                d.line([(x, 13), (x, 14)], fill=(200, 180, 80, 255))
            d.rectangle([5, 5, 10, 10], fill=colour + (255,))
            mark(d)
        return f

    icon('turret_upgrade_accuracy', 'Accuracy Upgrade', 'Улучшение точности', chip((200, 60, 60), lambda d: d.point((7, 7), fill=(255, 255, 255, 255))),
         ('Turret spread −20% each, up to 4', 'Разброс турелей −20% за штуку, до 4'))
    icon('turret_upgrade_efficiency', 'Efficiency Upgrade', 'Улучшение экономичности', chip((60, 160, 60), lambda d: None), ('Energy per shot −8% each, up to 4', 'Энергия на выстрел −8% за штуку, до 4'))
    icon('turret_upgrade_fire_rate', 'Fire Rate Upgrade', 'Улучшение скорострельности', chip((220, 160, 40), lambda d: None), ('Pause between shots −10% each, up to 4', 'Пауза между выстрелами −10% за штуку, до 4'))
    icon('turret_upgrade_range', 'Range Upgrade', 'Улучшение дальности', chip((70, 110, 210), lambda d: None), ('Range +2 blocks each, up to 4', 'Дальность +2 блока за штуку, до 4'))
    icon('turret_addon_solar', 'Solar Panel Addon', 'Дополнение: солнечная панель', lambda d: (d.rectangle([2, 4, 13, 11], fill=(30, 40, 90, 255), outline=(150, 150, 160, 255)),
                                                                                             d.line([(7, 4), (7, 11)], fill=(90, 110, 170, 255))), ('+10 RF/t (1.25 EU/t) in daylight', '+10 RF/t (1,25 EU/t) днём под открытым небом'))
    icon('turret_addon_redstone', 'Redstone Reactor Addon', 'Дополнение: редстоун-реактор', lambda d: (d.rectangle([3, 3, 12, 12], fill=(90, 90, 96, 255)), d.ellipse([5, 5, 10, 10], fill=(220, 30, 30, 255))),
         ('Burns redstone from the ammo slots: 1550 RF each', 'Сжигает редстоун из слотов патронов: 1550 RF за штуку'))
    icon('turret_addon_recycler', 'Recycler Addon', 'Дополнение: утилизатор', lambda d: (d.rectangle([3, 3, 12, 12], fill=(90, 96, 90, 255)), d.arc([4, 4, 11, 11], 30, 330, fill=(80, 220, 80, 255))),
         ('10% chance per addon to keep the round', 'Шанс 10% за штуку сберечь патрон'))
    icon('ferro_slug', 'Ferrous Slug', 'Ферросердечник', lambda d: (d.rectangle([5, 2, 10, 13], fill=(130, 130, 140, 255), outline=(70, 70, 80, 255)), d.line([(6, 3), (6, 12)], fill=(190, 190, 200, 255))))

    # --- recipes (proposal: OMT recipes are not known; GT parts of the tier) -----------------------------------------------------------
    shaped('defense/ballistic_shield', I('ballistic_shield'), 1, ['PGP', 'PRP', 'PSP'], {'P': P('Aluminium'), 'G': I('reinforced_glass'), 'R': P('Rubber'), 'S': tag_of('rod', 'Steel')})
    rec['assembler/force_shield'] = {'type': f'{NS}:assembler', 'inputs': [ing(P('TungstenSteel'), 6), ing(I('field_generator_iv')), ing(I('emitter_iv')), ing(I('energy_orb')), ing(C + 'elite', 2)],
                                     'outputs': [out(I('force_shield'))], 'duration': 600, 'eut': 1920}
    shaped('defense/turret_base_t1', I('turret_base_t1'), 1, ['PRP', 'RCR', 'PRP'], {'P': P('Iron'), 'R': MC('redstone'), 'C': '#c:chests'})
    for tier, plate, hull, sensor, circ in ((2, 'Steel', 'lv', 'sensor_lv', 'basic'), (3, 'Aluminium', 'mv', 'sensor_mv', 'good'), (4, 'StainlessSteel', 'hv', 'sensor_hv', 'advanced'),
                                            (5, 'TungstenSteel', 'ev', 'sensor_ev', 'data')):
        shaped(f'defense/turret_base_t{tier}', I(f'turret_base_t{tier}'), 1, ['PSP', 'CHC', 'PBP'], {'P': P(plate), 'S': I(sensor), 'C': C + circ, 'H': I(f'hull_{hull}'),
                                                                                                    'B': I(f'turret_base_t{tier - 1}')})
    T = {
        'disposable': (['RDR', ' P ', 'PPP'], {'R': MC('redstone'), 'D': MC('dispenser'), 'P': P('Iron')}),
        'potato': (['  B', 'RDR', 'PPP'], {'B': tag_of('rod', 'Iron'), 'R': MC('redstone'), 'D': MC('dispenser'), 'P': P('Iron')}),
        'machine_gun': (['GBB', 'TMS', 'PPP'], {'G': I('gun_spring'), 'B': I('barrel_steel'), 'T': I('trigger_group'), 'M': I('electric_motor_lv'), 'S': I('sensor_lv'), 'P': P('Steel')}),
        'incendiary': ([' BF', 'TUS', 'PPP'], {'B': I('barrel_steel'), 'F': MC('blaze_rod'), 'T': I('trigger_group'), 'U': I('electric_pump_lv'), 'S': I('sensor_lv'), 'P': P('Steel')}),
        'grenade': (['BBC', 'TXS', 'PPP'], {'B': I('barrel_stainless'), 'C': C + 'good', 'T': I('trigger_group'), 'X': I('electric_piston_mv'), 'S': I('sensor_mv'), 'P': P('Aluminium')}),
        'relativistic': (['EFE', 'CSC', 'PPP'], {'E': I('emitter_mv'), 'F': I('field_generator_mv'), 'C': C + 'good', 'S': I('sensor_mv'), 'P': P('Aluminium')}),
        'rocket': (['BBB', 'TXS', 'PCP'], {'B': I('barrel_titanium'), 'T': I('trigger_group'), 'X': I('electric_piston_hv'), 'S': I('sensor_hv'), 'C': C + 'advanced', 'P': P('Titanium')}),
        'teleporter': (['EYE', 'CFS', 'PPP'], {'E': I('emitter_hv'), 'Y': MC('ender_eye'), 'C': C + 'advanced', 'F': I('field_generator_hv'), 'S': I('sensor_hv'), 'P': P('Titanium')}),
        'laser': (['ELE', 'CSC', 'PPP'], {'E': I('emitter_hv'), 'L': '#iis:lenses/ruby', 'C': C + 'advanced', 'S': I('sensor_hv'), 'P': P('Titanium')}),
        'railgun': (['BFB', 'CSC', 'PEP'], {'B': I('barrel_titanium'), 'F': I('field_generator_ev'), 'C': C + 'data', 'S': I('sensor_ev'), 'E': I('emitter_ev'), 'P': P('TungstenSteel')}),
    }
    for kid, (pat, key) in T.items():
        shaped(f'defense/turret_{kid}', I(f'turret_{kid}'), 1, pat, key)
    shaped('defense/turret_upgrade_accuracy', I('turret_upgrade_accuracy'), 1, [' S ', 'CPC'], {'S': I('scope'), 'C': C + 'basic', 'P': P('Steel')})
    shaped('defense/turret_upgrade_efficiency', I('turret_upgrade_efficiency'), 1, ['CWC', 'CPC'], {'W': '#iis:cables/1x/tin', 'C': C + 'basic', 'P': P('Steel')})
    shaped('defense/turret_upgrade_fire_rate', I('turret_upgrade_fire_rate'), 1, [' G ', 'CMC'], {'G': I('gun_spring'), 'M': I('electric_motor_lv'), 'C': C + 'basic'})
    shaped('defense/turret_upgrade_range', I('turret_upgrade_range'), 1, [' S ', 'CPC'], {'S': I('sensor_lv'), 'C': C + 'basic', 'P': P('Steel')})
    shaped('defense/turret_addon_solar', I('turret_addon_solar'), 1, [' S ', 'CPC'], {'S': I('solar_panel'), 'C': C + 'basic', 'P': P('Steel')})
    shaped('defense/turret_addon_redstone', I('turret_addon_redstone'), 1, ['PRP', 'CFC', 'PPP'], {'R': MC('redstone_block'), 'F': MC('furnace'), 'C': C + 'basic', 'P': P('Steel')})
    shaped('defense/turret_addon_recycler', I('turret_addon_recycler'), 1, ['PXP', 'CHC', 'PPP'], {'X': I('electric_piston_mv'), 'H': '#c:chests', 'C': C + 'good', 'P': P('Aluminium')})
    shaped('defense/ferro_slug', I('ferro_slug'), 4, ['h', 'R', 'M'], {'R': tag_of('rod', 'Steel'), 'M': tag_of('rod', 'IronMagnetic')})
    for key, en, ru in (('message.iis.turret.mode.0', 'Turrets attack: monsters', 'Турели бьют: монстров'),
                        ('message.iis.turret.mode.1', 'Turrets attack: monsters and animals', 'Турели бьют: монстров и животных'),
                        ('message.iis.turret.mode.2', 'Turrets attack: monsters and players', 'Турели бьют: монстров и игроков'),
                        ('message.iis.turret.mode.3', 'Turrets attack: everyone but the trusted', 'Турели бьют: всех, кроме своих'),
                        ('message.iis.turret.not_owner', 'This turret base belongs to %s', 'Эта база турели принадлежит %s'),
                        ('itemGroup.iis.defense', 'IIS 2.0: Defense', 'ИИС 2.0: оборона')):
        name(key, en, ru)
    for rid, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', rid + '.json'), r)
    return {'turrets': len(KINDS), 'bases': len(BASES), 'meshes': len(meshes)}
