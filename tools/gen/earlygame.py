"""Owner's notes of 08.11 after a survival test (D142): IIS 1 recipes of the early game, flint tools, food in the reward shop.

Sources: IIS 1 GregTech.cfg WoodNeedsSawForCrafting=true (GT_CraftingRecipeLoader:101-102: two planks give 2 sticks by hand, 4 with a saw),
Recipes.cfg recipereplacements (GT_CraftingRecipeLoader:45-84: iron door, cauldron, hopper, bars, weighted pressure plates from plates and rods
with tools), scripts/Vanilla.zs (chests from logs, slabs with a saw, flint from gravel, tripwire hook with iron rings).
Flint tools: GT tools of flint in IIS 1; here vanilla-shaped and as strong as vanilla stone ones (owner).
"""
import os
import zipfile

from PIL import Image

NS = 'iis'
I = lambda x: f'{NS}:{x}'
MC = lambda x: f'minecraft:{x}'
WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak', 'crimson', 'warped', 'bamboo']
# Vanilla.zs: stone slabs only with a saw, two from a block
STONE_SLABS = {'stone': 'stone', 'sandstone': 'sandstone', 'cobblestone': 'cobblestone', 'brick': 'bricks', 'stone_brick': 'stone_bricks',
               'nether_brick': 'nether_bricks', 'quartz': 'quartz_block'}
# food sold without having been found first (owner): id, credit, how many a credit buys (proposal)
FOOD = [('bread', 0, 4), ('baked_potato', 0, 4), ('cooked_beef', 0, 2), ('cooked_porkchop', 0, 2), ('cooked_chicken', 0, 2), ('cooked_mutton', 0, 2),
        ('cooked_cod', 0, 2), ('cooked_salmon', 0, 2), ('apple', 0, 2), ('carrot', 0, 4), ('potato', 0, 4), ('melon_slice', 0, 8), ('sweet_berries', 0, 8),
        ('dried_kelp', 0, 8), ('cookie', 0, 8), ('pumpkin_pie', 0, 2), ('mushroom_stew', 0, 1), ('beetroot_soup', 0, 1), ('honey_bottle', 1, 2), ('cake', 1, 1)]


def generate(g):
    A, D, write, lang, shaped, shapeless, tag_of, OVR, result = g['A'], g['D'], g['write'], g['lang'], g['shaped'], g['shapeless'], g['tag_of'], g['OVR'], g['result']

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def override(rid, recipe):
        write(os.path.join(OVR, 'data', 'minecraft', 'recipe', rid + '.json'), recipe)

    never = {'neoforge:conditions': [{'type': 'neoforge:never'}]}

    # --- sticks: 2 planks -> 2 by hand, 4 with a saw ------------------------------------------------------------------------------
    override('stick', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'group': 'sticks', 'pattern': ['#', '#'], 'key': {'#': '#minecraft:planks'},
                       'result': result(MC('stick'), 2)})
    shaped('wood/stick_saw', MC('stick'), 4, ['s', 'P', 'P'], {'P': '#minecraft:planks'})
    # --- chests from logs (Vanilla.zs) ------------------------------------------------------------------------------------------------
    shaped('wood/chest_logs', MC('chest'), 2, ['LLL', 'L L', 'LLL'], {'L': '#minecraft:logs'})
    shaped('wood/chest_logs_saw', MC('chest'), 4, ['LLL', 'LsL', 'LLL'], {'L': '#minecraft:logs'})
    # --- slabs with a saw, two from a block -----------------------------------------------------------------------------------------
    for w in WOODS:
        override(f'{w}_slab', never)
        shaped(f'wood/{w}_slab_saw', MC(f'{w}_slab'), 2, ['sP'], {'P': MC(f'{w}_planks')})
    for slab, block in STONE_SLABS.items():
        override(f'{slab}_slab', never)
        shaped(f'stone/{slab}_slab_saw', MC(f'{slab}_slab'), 2, ['sB'], {'B': MC(block)})
    # --- iron things from plates and rods (recipereplacements) ------------------------------------------------------------------------
    P = lambda m: tag_of('plate', m)
    for rid, out, n, pat, key in (
            ('iron_door', 'iron_door', 3, ['XX ', 'XXh', 'XX '], {'X': P('Iron')}),
            ('cauldron', 'cauldron', 1, ['X X', 'XhX', 'XXX'], {'X': P('Iron')}),
            ('hopper', 'hopper', 1, ['XwX', 'XCX', ' X '], {'X': P('Iron'), 'C': '#c:chests/wooden'}),
            # GT_CraftingRecipeLoader:43 "tStack.stackSize /= 2": 8 bars from 6 rods (was 16, D145)
            ('iron_bars', 'iron_bars', 8, [' w ', 'XXX', 'XXX'], {'X': tag_of('rod', 'Iron')}),
            ('heavy_weighted_pressure_plate', 'heavy_weighted_pressure_plate', 1, ['XXh'], {'X': P('Iron')}),
            ('light_weighted_pressure_plate', 'light_weighted_pressure_plate', 1, ['XXh'], {'X': P('Gold')})):
        override(rid, never)
        shaped(f'vanilla/{rid}', MC(out), n, pat, key)
    override('tripwire_hook', never)
    shaped('vanilla/tripwire_hook', MC('tripwire_hook'), 1, ['R R', 'SPS'], {'R': tag_of('ring', 'Iron'), 'S': MC('stick'), 'P': '#minecraft:planks'})
    shaped('vanilla/flint_from_gravel', MC('flint'), 1, ['GG', 'GG'], {'G': MC('gravel')})

    # --- flint tools -------------------------------------------------------------------------------------------------------------------
    jar = g.get('MC_JAR') or os.path.join(g['ROOT'], 'build', 'moddev', 'artifacts', 'minecraft-patched-26.1.2.117-merged.jar')
    with zipfile.ZipFile(jar) as z:
        for t, en, ru, pat in (('sword', 'Flint Sword', 'Кремниевый меч', [' F', ' F', ' S']), ('shovel', 'Flint Shovel', 'Кремниевая лопата', ['F', 'S', 'S']),
                               ('pickaxe', 'Flint Pickaxe', 'Кремниевая кирка', ['FFF', ' S ', ' S ']), ('axe', 'Flint Axe', 'Кремниевый топор', ['FF', 'FS', ' S']),
                               ('hoe', 'Flint Hoe', 'Кремниевая мотыга', ['FF', ' S', ' S'])):
            im = Image.open(z.open(f'assets/minecraft/textures/item/stone_{t}.png')).convert('RGBA')
            px = im.load()
            for y in range(im.height):
                for x in range(im.width):
                    r, gg, b, a = px[x, y]
                    if a and abs(r - gg) < 14 and abs(gg - b) < 14:   # the grey stone head: darker, bluish flint
                        v = (r + gg + b) / 3
                        px[x, y] = (int(v * 0.55), int(v * 0.58), int(v * 0.68), a)
            p = os.path.join(A, 'textures', 'item', f'flint_{t}.png')
            os.makedirs(os.path.dirname(p), exist_ok=True)
            im.save(p)
            write(os.path.join(A, 'models', 'item', f'flint_{t}.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'{NS}:item/flint_{t}'}})
            write(os.path.join(A, 'items', f'flint_{t}.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/flint_{t}'}})
            name(f'item.{NS}.flint_{t}', en, ru)
            shaped(f'tool/flint_{t}', I(f'flint_{t}'), 1, pat, {'F': MC('flint'), 'S': MC('stick')})
    write(os.path.join(D, NS, 'tags', 'item', 'flint_tool_materials.json'), {'values': [MC('flint')]})
    for tag, t in (('swords', 'sword'), ('shovels', 'shovel'), ('pickaxes', 'pickaxe'), ('axes', 'axe'), ('hoes', 'hoe')):
        g['add_item_tag'](f'minecraft:{tag}', I(f'flint_{t}'))
        g['add_item_tag']('iis:flint_tools', I(f'flint_{t}'))   # D144: the quest asks for any of them
    return {'flint_tools': 5}
