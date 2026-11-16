"""Mother ship (D156): the blocks of the hull, the creatures of the ship, loot and the placement in the world.

Owner's answers: a rare large structure in the plain Overworld, a fallen hull with three tiers inside, no separate dimensions (13.11);
own creatures at once, a real boss creature, loot = Bio samples and rare materials, rare and with no locator item (16.11).
The geometry is in ru.iis.ship.ShipLayout, the behaviour in ru.iis.ship; here are the looks, the names and the data.
Every number (loot, frequency) is the AI's draft, recorded in D156.
"""
import os

import numpy as np
from PIL import Image

NS = 'iis'

# id: (en, ru, tooltip en, tooltip ru)
BLOCKS = {
    'ship_plating': ('Mother Ship Plating', 'Обшивка материнского корабля', ['A plain alloy: the ship was kept by its shields, not by its hull.'],
                     ['Обычный сплав: корабль держали щиты, а не корпус.']),
    'ship_deck': ('Ship Deck', 'Палуба корабля', [], []),
    'ship_bulkhead': ('Ship Bulkhead', 'Переборка корабля', [], []),
    'ship_light': ('Ship Light', 'Корабельный светильник', ['Still glows.'], ['Всё ещё светит.']),
    'wild_growth': ('Wild Growth', 'Дикий нарост', ['The infection of a mother ship. Belongs to no colony and does not spread.'],
                    ['Заражение материнского корабля. Не принадлежит ни одной колонии и не растёт.']),
    'ship_lift': ('Cargo Lift Pad', 'Площадка грузового лифта', ['Right-click: down. Sneak + right-click: up.', 'Takes you to the nearest pad in the same column, up to 16 blocks.'],
                  ['ПКМ — вниз. Shift + ПКМ — вверх.', 'Переносит на ближайшую площадку в том же столбце, не дальше 16 блоков.']),
}
ENTITIES = {'ship_crawler': ('Crawler', 'Ползун'), 'ship_brute': ('Supermutant', 'Супермутант'), 'ship_overmind': ('Superorganism Core', 'Ядро сверхорганизма')}
LANG = {
    'itemGroup.iis.ship': ('Mother Ship', 'Материнский корабль'),
    'message.iis.ship.lift.none': ('No other lift pad in this column', 'В этом столбце нет другой площадки'),
}

# chest loot: (item, weight, min, max). Bio samples and rare materials (the owner's answer).
TIER_OUTER = [('base_seed', 12, 1, 3), ('soft_tissue', 12, 2, 4), ('living_membrane', 10, 1, 3), ('fibrous_tissue', 8, 1, 3), ('binding_gel', 8, 2, 4),
              ('growth_substrate', 8, 2, 4), ('bone_ring', 6, 1, 2), ('tissue_lining', 6, 1, 2), ('energy_paste', 6, 1, 3),
              ('dust_titanium', 5, 1, 3), ('dust_chrome', 5, 1, 3), ('dust_tungsten', 3, 1, 2)]
TIER_CORE = [('muscle_node', 10, 1, 2), ('tissue_lining', 10, 2, 4), ('chitin_plate', 10, 2, 4), ('reinforced_shell', 8, 1, 2), ('hybrid_seed', 6, 1, 2),
             ('agent_pig_fleshy', 3, 1, 1), ('agent_cow_binding', 3, 1, 1), ('agent_sheep_longfiber', 3, 1, 1), ('agent_chicken_shell', 3, 1, 1),
             ('dust_titanium', 8, 2, 5), ('dust_tungsten', 8, 2, 4), ('dust_platinum', 5, 1, 3), ('dust_iridium', 2, 1, 2)]
BOSS_CHEST = [('organ_embryo', 10, 1, 2), ('regulated_shell', 8, 1, 2), ('enzyme_culture', 8, 1, 2), ('hybrid_seed', 8, 2, 3), ('reinforced_shell', 8, 2, 4),
              ('dust_iridium', 8, 2, 4), ('dust_osmium', 6, 1, 3), ('dust_platinum', 8, 2, 5), ('dust_tungsten', 8, 3, 6)]
CHESTS = {'ship_tier1': (TIER_OUTER, 4, 7), 'ship_tier2': (TIER_CORE, 5, 8), 'ship_tier3': (TIER_OUTER, 4, 7), 'ship_core': (BOSS_CHEST, 9, 12)}
# creature drops: (item, min, max)
DROPS = {'ship_crawler': [('connective_fiber', 0, 2), ('bone_fragment', 0, 1)],
         'ship_brute': [('meat_mass', 2, 4), ('thick_sinew', 0, 2), ('muscle_bundle', 0, 1)],
         'ship_overmind': [('organ_embryo', 1, 1), ('hybrid_seed', 1, 2), ('dust_iridium', 2, 4)]}

# one ship per SPACING chunks on average (110 chunks = 1760 blocks), never closer than SEPARATION chunks
SPACING, SEPARATION, SALT = 110, 50, 916250117


def _noise(seed, shape):
    return np.random.default_rng(seed).random(shape)


def _img(rgb):
    a = np.clip(rgb, 0, 255).astype(np.uint8)
    out = np.dstack([a, np.full(a.shape[:2], 255, np.uint8)])
    return Image.fromarray(out, 'RGBA')


def _panel(base, seed, seams=(0, 8), rivets=True, dark=0.55):
    n = _noise(seed, (16, 16))
    rgb = np.array(base, float)[None, None, :] * (0.88 + 0.2 * n[..., None])
    for s in seams:
        rgb[s, :, :] *= dark
        rgb[:, s, :] *= dark
    if rivets:
        for y in (2, 10):
            for x in (2, 10):
                rgb[y, x] = np.array(base, float) * 1.35
    return rgb


def block_textures():
    t = {}
    t['ship_plating'] = _img(_panel((74, 78, 88), 901))
    t['ship_bulkhead'] = _img(_panel((132, 138, 146), 902, seams=(0,), rivets=False, dark=0.7))
    deck = _panel((92, 96, 100), 903, seams=(), rivets=False)
    for i in range(0, 16, 4):
        deck[i, :, :] *= 0.6
        deck[:, i, :] *= 0.6
    t['ship_deck'] = _img(deck)
    yy, xx = np.mgrid[0:16, 0:16]
    r = np.hypot(xx - 7.5, yy - 7.5)
    light = np.array((70, 76, 86), float)[None, None, :] * np.ones((16, 16, 1))
    glow = np.clip(1.2 - r / 6.5, 0, 1)[..., None]
    t['ship_light'] = _img(light * (1 - glow) + np.array((170, 236, 255), float)[None, None, :] * glow)
    n1, n2 = _noise(905, (16, 16)), _noise(906, (16, 16))
    g = np.array((120, 28, 44), float)[None, None, :] * (0.7 + 0.6 * n1[..., None])
    g[n2 > 0.86] = (226, 150, 120)
    g[np.abs(n1 - 0.5) < 0.04] = (56, 10, 22)
    t['wild_growth'] = _img(g)
    lift = _panel((66, 70, 78), 907, seams=(0,), rivets=False)
    stripe = ((xx + yy) // 3) % 2 == 0
    border = (xx < 2) | (xx > 13) | (yy < 2) | (yy > 13)
    lift[border & stripe] = (232, 190, 40)
    lift[border & ~stripe] = (30, 30, 34)
    arrow = (np.abs(xx - 7.5) <= (yy - 5) * 0.9) & (yy >= 5) & (yy <= 9)
    lift[arrow] = (240, 200, 60)
    t['ship_lift'] = _img(lift)
    return t


def entity_texture(w, h, c0, c1, seed, eyes=()):
    n = _noise(seed, (h, w))
    n2 = _noise(seed + 1, (h, w))
    rgb = np.array(c0, float)[None, None, :] * (1 - n[..., None]) + np.array(c1, float)[None, None, :] * n[..., None]
    rgb[np.abs(n2 - 0.5) < 0.03] = np.array(c1, float) * 0.45          # veins
    rgb[n2 > 0.95] = (236, 190, 150)                                    # pustules
    for (x, y, ww, hh) in eyes:
        rgb[y:y + hh, x:x + ww] = (255, 214, 90)
    return _img(rgb)


def _pool(entries, lo, hi, exists):
    out = []
    for item, weight, a, b in entries:
        assert exists(item), 'ship loot: no item ' + item
        e = {'type': 'minecraft:item', 'name': f'{NS}:{item}', 'weight': weight}
        if b > 1:
            e['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': a, 'max': b}}]
        out.append(e)
    return {'rolls': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}, 'bonus_rolls': 0.0, 'entries': out}


def generate(g):
    write, A, D, lang, block_tags = g['write'], g['A'], g['D'], g['lang'], g['block_tags']

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def exists(item):
        return os.path.exists(os.path.join(A, 'items', item + '.json'))

    # blocks
    for bid, im in block_textures().items():
        im.save(tex(f'block/ship/{bid}.png'))
    for bid, (en, ru, ten, tru) in BLOCKS.items():
        write(os.path.join(A, 'models', 'block', f'ship/{bid}.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/ship/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/ship/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/ship/{bid}'}})
        name(f'block.{NS}.{bid}', en, ru)
        for k, (a, b) in enumerate(zip(ten, tru)):
            name(f'block.{NS}.{bid}.tooltip.{k}', a, b)
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
    for bid in ('ship_plating', 'ship_deck', 'ship_bulkhead', 'ship_lift', 'ship_light'):
        block_tags.setdefault('minecraft:mineable/pickaxe', []).append(f'{NS}:{bid}')
    for bid in ('ship_plating', 'ship_deck', 'ship_bulkhead', 'ship_lift'):
        block_tags.setdefault('minecraft:needs_iron_tool', []).append(f'{NS}:{bid}')
    block_tags.setdefault('minecraft:mineable/hoe', []).append(f'{NS}:wild_growth')

    # creatures
    entity_texture(64, 32, (96, 30, 44), (176, 84, 76), 911, eyes=[(6, 26, 2, 2), (11, 26, 2, 2)]).save(tex('entity/ship_crawler.png'))
    entity_texture(64, 64, (112, 44, 52), (196, 120, 100), 913, eyes=[(9, 37, 2, 2), (13, 37, 2, 2)]).save(tex('entity/ship_brute.png'))
    entity_texture(128, 80, (88, 16, 36), (214, 74, 74), 915).save(tex('entity/ship_overmind.png'))
    for eid, (en, ru) in ENTITIES.items():
        name(f'entity.{NS}.{eid}', en, ru)
        pools = []
        for item, a, b in DROPS[eid]:
            assert exists(item), 'ship drop: no item ' + item
            pools.append({'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{item}', 'functions': [
                {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': a, 'max': b}}]}]})
        write(os.path.join(D, NS, 'loot_table', 'entities', eid + '.json'), {'type': 'minecraft:entity', 'random_sequence': f'{NS}:entities/{eid}', 'pools': pools})
    for key, (en, ru) in LANG.items():
        name(key, en, ru)

    # chests
    for cid, (entries, lo, hi) in CHESTS.items():
        write(os.path.join(D, NS, 'loot_table', 'chests', cid + '.json'), {'type': 'minecraft:chest', 'random_sequence': f'{NS}:chests/{cid}',
                                                                           'pools': [_pool(entries, lo, hi, exists)]})

    # the structure in the world: land of the Overworld, rare
    write(os.path.join(D, NS, 'worldgen', 'structure', 'mother_ship.json'), {
        'type': f'{NS}:mother_ship', 'biomes': f'#{NS}:has_structure/mother_ship', 'spawn_overrides': {}, 'step': 'surface_structures', 'terrain_adaptation': 'none'})
    write(os.path.join(D, NS, 'worldgen', 'structure_set', 'mother_ship.json'), {
        'structures': [{'structure': f'{NS}:mother_ship', 'weight': 1}],
        'placement': {'type': 'minecraft:random_spread', 'spacing': SPACING, 'separation': SEPARATION, 'salt': SALT}})
    write(os.path.join(D, NS, 'tags', 'worldgen', 'biome', 'has_structure', 'mother_ship.json'), {'replace': False, 'values': ['#minecraft:is_overworld']})
    return {'blocks': len(BLOCKS), 'creatures': len(ENTITIES), 'chests': len(CHESTS)}
