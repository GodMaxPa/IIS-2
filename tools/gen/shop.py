"""Reward shop (D130): prices of raw materials in quest credits. Owner: only raw materials, price by the epoch of the material.

The epoch comes from the material: no blast furnace is the copper credit, then by the temperature of the furnace, as titanium for silver and naquadah for platinum.
"""
import json
import os

NS = 'iis'
CREDITS = ['copper', 'cupronickel', 'silver', 'gold', 'platinum', 'osmium', 'naquadah', 'neutronium']

# vanilla raw materials: id, credit, how many a credit buys
VANILLA = [
    ('oak_log', 0, 4), ('spruce_log', 0, 4), ('birch_log', 0, 4), ('jungle_log', 0, 4), ('acacia_log', 0, 4), ('dark_oak_log', 0, 4), ('cherry_log', 0, 4), ('mangrove_log', 0, 4),
    ('cobblestone', 0, 16), ('stone', 0, 8), ('andesite', 0, 8), ('diorite', 0, 8), ('granite', 0, 8), ('deepslate', 0, 8), ('dirt', 0, 16), ('sand', 0, 16), ('red_sand', 0, 16), ('gravel', 0, 16),
    ('clay_ball', 0, 16), ('flint', 0, 4), ('string', 0, 4), ('leather', 0, 2), ('feather', 0, 4), ('bone', 0, 4), ('gunpowder', 0, 2), ('sugar_cane', 0, 4), ('wheat', 0, 4), ('wheat_seeds', 0, 8),
    ('apple', 0, 2), ('carrot', 0, 4), ('potato', 0, 4), ('pumpkin', 0, 2), ('melon_slice', 0, 8), ('egg', 0, 4), ('white_wool', 0, 2), ('obsidian', 1, 2), ('netherrack', 0, 16), ('soul_sand', 0, 8),
    ('glowstone_dust', 1, 4), ('blaze_rod', 1, 1), ('ender_pearl', 1, 1), ('ghast_tear', 2, 1), ('slime_ball', 0, 2), ('magma_cream', 1, 2), ('quartz', 0, 4), ('end_stone', 0, 8), ('prismarine_shard', 1, 2)]


def tier_of(m):
    """Credit of a material: the hotter the furnace it needs, the dearer. Steel 1000 K is cupronickel, titanium 1940 K silver, tungsten 3000 K gold, naquadah 5000 K platinum."""
    t = m.get('blastFurnaceTemp', 0) or 0
    if not m.get('blastFurnaceRequired') and t == 0:
        return 1 if (m.get('harvestLevel', 0) or 0) >= 3 else 0
    if t < 1800:
        return 1
    if t < 2500:
        return 2
    if t < 4000:
        return 3
    if t < 7000:
        return 4
    return 5


def generate(g, name, tex, write, shaped, art):
    ref, tag_of, materials, GEN = g['ref'], g['tag_of'], g['materials'], g['GEN']
    entries = []
    seen = set()

    def add(item, tier, give):
        if item and item not in seen:
            seen.add(item)
            entries.append({'id': item, 'tier': tier, 'give': give})

    for m in materials:
        n, t = m['gtName'], tier_of(m)
        # an ore block of the plain stone host, dust, ingot, gem: what comes out of the ground and the first furnace
        if m.get('hosts'):
            add(f"{NS}:ore_{m['id']}", t, 2)
        for prefix, give in (('dust', 2), ('ingot', 2), ('gem', 2)):
            add(ref(prefix, n), t, give)
    for vid, t, give in VANILLA:
        add(f'minecraft:{vid}', t, give)
    # D142 (owner): food is sold from the start, without having been found
    from earlygame import FOOD
    for vid, t, give in FOOD:
        item = f'minecraft:{vid}'
        if item in seen:
            next(e for e in entries if e['id'] == item)['always'] = True
        else:
            seen.add(item)
            entries.append({'id': item, 'tier': t, 'give': give, 'always': True})
    path = os.path.join(GEN, 'iis', 'shop_prices.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump({'credits': CREDITS, 'items': entries}, fh, separators=(',', ':'))

    # block
    side = art.plate((120, 92, 56), 950)
    front = art.plate((120, 92, 56), 951, False)
    for x in range(3, 13):
        for y in range(3, 11):
            front.putpixel((x, y), (40, 34, 26, 255))
    for x, y, c in ((5, 5, (225, 130, 70)), (6, 5, (225, 130, 70)), (9, 5, (214, 214, 224)), (10, 5, (214, 214, 224)), (5, 8, (240, 215, 80)), (6, 8, (240, 215, 80)), (9, 8, (190, 220, 240)), (10, 8, (190, 220, 240))):
        front.putpixel((x, y), c + (255,))
    top = art.plate((150, 116, 72), 952)
    side.save(tex('block/reward_shop_side.png'))
    front.save(tex('block/reward_shop_front.png'))
    top.save(tex('block/reward_shop_top.png'))
    A, D = g['A'], g['D']
    write(os.path.join(A, 'models', 'block', 'reward_shop.json'), {'parent': 'minecraft:block/cube', 'textures': {'particle': f'{NS}:block/reward_shop_side', 'north': f'{NS}:block/reward_shop_front', 'south': f'{NS}:block/reward_shop_front', 'east': f'{NS}:block/reward_shop_side', 'west': f'{NS}:block/reward_shop_side', 'up': f'{NS}:block/reward_shop_top', 'down': f'{NS}:block/reward_shop_side'}})
    write(os.path.join(A, 'blockstates', 'reward_shop.json'), {'variants': {'': {'model': f'{NS}:block/reward_shop'}}})
    write(os.path.join(A, 'items', 'reward_shop.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/reward_shop'}})
    write(os.path.join(D, NS, 'loot_table', 'blocks', 'reward_shop.json'), {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/reward_shop', 'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:reward_shop'}]}]})
    g['block_tags'].setdefault('minecraft:mineable/axe', []).append(f'{NS}:reward_shop')
    shaped('equipment/reward_shop', f'{NS}:reward_shop', 1, ['PPP', 'PCP', 'PPP'], {'P': '#minecraft:planks', 'C': 'minecraft:chest'})
    name(f'block.{NS}.reward_shop', 'Reward Shop', 'Магазин наград')
    name(f'block.{NS}.reward_shop.tooltip.0', 'Sells raw materials that have been in your inventory, for quest credits', 'Продаёт за жетоны квестов сырьё, которое уже бывало у вас в инвентаре')
    for k, en, ru in (('gui.iis.shop.title', 'Reward Shop', 'Магазин наград'), ('gui.iis.shop.search', 'Search', 'Поиск'), ('gui.iis.shop.wallet', 'Credits worth %s copper', 'Жетонов на %s медных'),
                      ('gui.iis.shop.empty', 'Nothing yet: pick up ore, dust, an ingot or wood first', 'Пока пусто: сначала подержите в инвентаре руду, пыль, слиток или дерево'),
                      ('gui.iis.shop.price', 'Price: %s × %s', 'Цена: %s × %s'), ('gui.iis.shop.gives', 'You get: %s', 'Получите: %s'), ('gui.iis.shop.help', 'Click buys, Shift+click buys eight', 'Щелчок покупает, Shift+щелчок покупает восемь раз'),
                      ('message.iis.shop.poor', 'Not enough credits', 'Не хватает жетонов')):
        name(k, en, ru)
