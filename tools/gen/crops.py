"""IC2 crops with selection, as in IIS 1 (D139): the crop cards, their harvest, tools, the GT crop drops and their processing.

Sources: GT_CropLoader (54 GT crops; the numbers are read from the GT source), GT_BaseCrop, GT_MetaGenerated_Item_02:314-354 (drops),
GT_MachineRecipeLoader:76, 373-377, 1598-1710 (processing; IIS 1 GregTech.cfg NerfCrops=true, CropNeedBlockBelow=true), :1752-1765
(fertilizer), GT_Loader_MetaTileEntities:1118-1134 (organic replicator), IIS 1 scripts crops.zs (crop list and products),
GT_IC2.zs:222-226, 623-637 (trowel, crop-matron, crop harvester), IC2CropsTools.zs (lens), Biomes.zs (humidity bonuses).
IC2 itself is closed source and not in the IIS 1 folder: the 29 IC2 crops and the IC2 recipes are from memory of IC2 experimental 2.2.
"""
import os
import re

from PIL import Image, ImageDraw

NS = 'iis'
I = lambda x: f'{NS}:{x}'
MC = lambda x: f'minecraft:{x}'
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')
GT_SRC = os.environ.get('IIS_GT5_SRC', '/home/claude/ref/gt5/src/main/java/gregtech')

# GT_MetaGenerated_Item_02:314-354: id, en, ru, food (nutrition, saturation) or None
DROPS = [
    (500, 'plumbilia_leaf', 'Plumbilia Leaf', 'Лист плюмбилии', None), (501, 'argentia_leaf', 'Argentia Leaf', 'Лист аргентии', None),
    (502, 'indigo_blossom', 'Indigo Blossom', 'Цветок индиго', None), (505, 'tea_leaf', 'Tea Leaf', 'Чайный лист', None),
    (510, 'oil_berry', 'Oil Berry', 'Нефтеягода', None), (511, 'bobs_yer_uncle_berry', 'Bobs-Yer-Uncle-Berry', 'Ягода «Бобс-йер-англ»', None),
    (512, 'uum_berry', 'UUM Berry', 'UUM-ягода', None), (513, 'uua_berry', 'UUA Berry', 'UUA-ягода', None),
    (520, 'milk_wart', 'Milk Wart', 'Молочная бородавка', None), (521, 'bauxia_leaf', 'Bauxia Leaf', 'Лист бокситии', None),
    (522, 'titania_leaf', 'Titania Leaf', 'Лист титании', None), (523, 'reactoria_leaf', 'Reactoria Leaf', 'Лист реактории', None),
    (524, 'uranium_leaf', 'Uranium Leaf', 'Урановый лист', None), (526, 'thunder_leaf', 'Thunder Leaf', 'Громовой лист', None),
    (527, 'nickelback_leaf', 'Nickelback Leaf', 'Лист никельбэка', None), (528, 'galvania_leaf', 'Galvania Leaf', 'Лист гальвании', None),
    (529, 'pyrolusium_leaf', 'Pyrolusium Leaf', 'Лист пиролюзиума', None), (530, 'coppon_fiber', 'Coppon Fiber', 'Волокно коппона', None),
    (531, 'scheelinium_leaf', 'Scheelinium Leaf', 'Лист шеелиниума', None), (532, 'platina_leaf', 'Platina Leaf', 'Лист платины', None),
    (533, 'quantaria_leaf', 'Quantaria Leaf', 'Лист квантарии', None), (534, 'quantaria_leaf_osmium', 'Quantaria Leaf (Osmium)', 'Лист квантарии (осмий)', None),
    (535, 'stargatium_leaf', 'Stargatium Leaf', 'Лист старгейтиума', None), (540, 'tine_twig', 'Tine Twig', 'Веточка тайна', None),
    (550, 'chilly_pepper', 'Chilly Pepper', 'Перец чили', (1, 0.3)), (551, 'lemon', 'Lemon', 'Лимон', (1, 0.3)),
    (552, 'tomato', 'Tomato', 'Помидор', (1, 0.2)), (553, 'max_tomato', 'Max Tomato', 'Макс-помидор', (9, 1.0)),
    (554, 'grapes', 'Grapes', 'Виноград', (2, 0.3)), (555, 'onion', 'Onion', 'Лук', (2, 0.2)),
    (556, 'cucumber', 'Cucumber', 'Огурец', (1, 0.2)), (557, 'rape', 'Rape', 'Рапс', None),
]

# GT crop -> (main drop, special drops); a drop is an item id or (form prefix, GT material)
GT_DROPS = {
    'Indigo': (I('indigo_blossom'), []), 'Flax': (MC('string'), []), 'Oilberries': (I('oil_berry'), []),
    'Bobsyeruncleranks': (I('bobs_yer_uncle_berry'), [MC('emerald')]), 'Diareed': (('tiny_dust', 'Diamond'), [MC('diamond')]),
    'Withereed': (('dust', 'Coal'), [MC('coal'), MC('coal')]), 'Blazereed': (MC('blaze_powder'), [MC('blaze_rod')]),
    'Eggplant': (MC('egg'), [MC('chicken'), MC('feather'), MC('feather'), MC('feather')]), 'Corium': (MC('leather'), []),
    'Corpseplant': (MC('rotten_flesh'), [MC('bone_meal'), MC('bone_meal'), MC('bone')]), 'Creeperweed': (MC('gunpowder'), []),
    'Enderbloom': (('dust', 'EnderPearl'), [MC('ender_pearl'), MC('ender_pearl'), MC('ender_eye')]),
    'Meatrose': (MC('pink_dye'), [MC('beef'), MC('porkchop'), MC('chicken'), MC('cod')]), 'Milkwart': (I('milk_wart'), []),
    'Slimeplant': (MC('slime_ball'), []), 'Spidernip': (MC('string'), [MC('spider_eye'), MC('cobweb')]), 'Tearstalks': (MC('ghast_tear'), []),
    'Tine': (I('tine_twig'), []), 'Coppon': (I('coppon_fiber'), []), 'Brown Mushrooms': (MC('brown_mushroom'), []),
    'Red Mushrooms': (MC('red_mushroom'), []), 'Argentia': (I('argentia_leaf'), []), 'Plumbilia': (I('plumbilia_leaf'), []),
    'Steeleafranks': (('dust', 'Steeleaf'), [('ingot', 'Steeleaf')]), 'Liveroots': (('dust', 'LiveRoot'), []),
    'Trollplant': (('gem', 'FoolsRuby'), [I('plant_ball'), I('scrap'), ('tiny_dust', 'Plutonium241')]),
    'Lazulia': (('tiny_dust', 'Lapis'), []), 'Glowheat': (('tiny_dust', 'Glowstone'), []),
    'Fertilia': (('tiny_dust', 'Calcite'), [I('fertilizer'), ('tiny_dust', 'Apatite'), ('dust', 'Phosphate')]),
    'Bauxia': (I('bauxia_leaf'), []), 'Titania': (I('titania_leaf'), []), 'Reactoria': (I('reactoria_leaf'), [I('uranium_leaf')]),
    'God of Thunder': (('small_dust', 'Coal'), [MC('coal'), I('thunder_leaf')]),
    'Transformium': (I('uua_berry'), [I('uua_berry'), I('uua_berry'), I('uua_berry'), I('uua_berry'), I('uum_berry')]),
    'Starwart': (('small_dust', 'Coal'), [MC('coal'), MC('skeleton_skull'), MC('wither_skeleton_skull'), MC('wither_skeleton_skull'), ('tiny_dust', 'NetherStar')]),
    'Zomplant': (MC('rotten_flesh'), []), 'Nickelback': (I('nickelback_leaf'), []), 'Galvania': (I('galvania_leaf'), []),
    'Evil Ore': (('tiny_dust', 'NetherQuartz'), [MC('quartz'), ('tiny_dust', 'CertusQuartz')]),
    'Olivia': (('tiny_dust', 'Olivine'), [('gem', 'Olivine')]), 'Sapphirum': (('tiny_dust', 'Sapphire'), [('gem', 'Sapphire')]),
    'Pyrolusium': (I('pyrolusium_leaf'), []), 'Scheelinium': (I('scheelinium_leaf'), []), 'Platina': (I('platina_leaf'), []),
    'Quantaria': (I('quantaria_leaf'), [I('quantaria_leaf_osmium')]), 'Stargatium': (('tiny_dust', 'Endstone'), [I('stargatium_leaf')]),
    'Lemon': (I('lemon'), []), 'Chilly': (I('chilly_pepper'), []), 'Tomato': (I('tomato'), [I('max_tomato')]), 'Grape': (I('grapes'), []),
    'Onion': (I('onion'), []), 'Cucumber': (I('cucumber'), []), 'Tea': (I('tea_leaf'), []), 'Rape': (I('rape'), []),
}
# GT_CropLoader tI[]: base seeds of four
GT_SEEDS = {'tI[0]': (I('indigo_blossom'), 4), 'tI[1]': (I('milk_wart'), 4), 'tI[2]': (MC('brown_mushroom'), 4), 'tI[3]': (MC('red_mushroom'), 4)}
GT_RU = {
    'Indigo': 'Индиго', 'Flax': 'Лён', 'Oilberries': 'Нефтеягоды', 'Bobsyeruncleranks': 'Бобс-йер-англ', 'Diareed': 'Диарид', 'Withereed': 'Визерид',
    'Blazereed': 'Блейзрид', 'Eggplant': 'Яйцерод', 'Corium': 'Кориум', 'Corpseplant': 'Трупоцвет', 'Creeperweed': 'Криперник', 'Enderbloom': 'Эндерцвет',
    'Meatrose': 'Мясороза', 'Milkwart': 'Молочник', 'Slimeplant': 'Слизнецвет', 'Spidernip': 'Паутинник', 'Tearstalks': 'Слезник', 'Tine': 'Тайн',
    'Coppon': 'Коппон', 'Brown Mushrooms': 'Коричневые грибы', 'Red Mushrooms': 'Красные грибы', 'Argentia': 'Аргентия', 'Plumbilia': 'Плюмбилия',
    'Steeleafranks': 'Стальнолист', 'Liveroots': 'Живокорень', 'Trollplant': 'Тролль-трава', 'Lazulia': 'Лазулия', 'Glowheat': 'Светопшеница',
    'Fertilia': 'Фертилия', 'Bauxia': 'Боксития', 'Titania': 'Титания', 'Reactoria': 'Реактория', 'God of Thunder': 'Бог грома',
    'Transformium': 'Трансформиум', 'Starwart': 'Звездник', 'Zomplant': 'Зомбоцвет', 'Nickelback': 'Никельбэк', 'Galvania': 'Гальвания',
    'Evil Ore': 'Злая руда', 'Olivia': 'Оливия', 'Sapphirum': 'Сапфириум', 'Pyrolusium': 'Пиролюзиум', 'Scheelinium': 'Шеелиниум', 'Platina': 'Платина',
    'Quantaria': 'Квантария', 'Stargatium': 'Старгейтиум', 'Lemon': 'Лимон', 'Chilly': 'Чили', 'Tomato': 'Помидор', 'Grape': 'Виноград',
    'Onion': 'Лук', 'Cucumber': 'Огурец', 'Tea': 'Чай', 'Rape': 'Рапс'}

# IC2 experimental 2.2 crops, from memory: id, en, ru, tier, max, harvest, after, (chem, food, def, colour, weed), attributes, drop, specials,
# block below (GT material), base seed
IC2 = [
    ('weed', 'Weed', 'Сорняк', 0, 5, 99, 1, (0, 0, 1, 0, 5), ['Weed', 'Bad'], None, [], None, None),
    ('wheat', 'Wheat', 'Пшеница', 1, 7, 7, 2, (0, 4, 0, 0, 2), ['White', 'Food', 'Wheat'], MC('wheat'), [], None, (MC('wheat_seeds'), 1)),
    ('pumpkin', 'Pumpkin', 'Тыква', 1, 4, 4, 3, (0, 1, 0, 3, 1), ['Orange', 'Decoration', 'Stem'], MC('pumpkin'), [], None, (MC('pumpkin_seeds'), 1)),
    ('melon', 'Melon', 'Арбуз', 2, 4, 4, 3, (0, 4, 0, 2, 0), ['Green', 'Food', 'Stem'], MC('melon_slice'), [MC('melon')], None, (MC('melon_seeds'), 1)),
    ('dandelion', 'Dandelion', 'Одуванчик', 2, 4, 4, 3, (1, 1, 0, 5, 1), ['Yellow', 'Flower'], MC('yellow_dye'), [], None, (MC('dandelion'), 1)),
    ('rose', 'Rose', 'Роза', 2, 4, 4, 3, (1, 1, 0, 5, 1), ['Red', 'Flower', 'Rose'], MC('red_dye'), [], None, (MC('poppy'), 1)),
    ('blackthorn', 'Blackthorn', 'Терновник', 2, 4, 4, 3, (1, 1, 0, 5, 1), ['Black', 'Flower', 'Rose'], MC('ink_sac'), [], None, None),
    ('tulip', 'Tulip', 'Тюльпан', 2, 4, 4, 3, (1, 1, 0, 5, 1), ['Purple', 'Flower', 'Tulip'], MC('purple_dye'), [], None, None),
    ('cyazint', 'Cyazint', 'Циазинт', 2, 4, 4, 3, (1, 1, 0, 5, 1), ['Blue', 'Flower'], MC('cyan_dye'), [], None, None),
    ('venomilia', 'Venomilia', 'Веномилия', 3, 6, 4, 3, (3, 1, 3, 3, 3), ['Purple', 'Flower', 'Tulip', 'Poison'], MC('purple_dye'), [I('grin_powder')], None, None),
    ('reed', 'Reed', 'Тростник', 2, 3, 2, 1, (0, 0, 2, 0, 2), ['Reed'], MC('sugar_cane'), [], None, (MC('sugar_cane'), 1)),
    ('stickreed', 'Stickreed', 'Липкий тростник', 4, 4, 2, 1, (2, 0, 1, 0, 1), ['Reed', 'Resin'], MC('sugar_cane'), [I('sticky_resin'), I('sticky_resin')], None, None),
    ('cocoa', 'Cocoa', 'Какао', 3, 4, 4, 3, (1, 3, 0, 4, 0), ['Brown', 'Food', 'Stem'], MC('cocoa_beans'), [], None, (MC('cocoa_beans'), 1)),
    ('redwheat', 'Redwheat', 'Красная пшеница', 6, 7, 7, 2, (3, 0, 0, 0, 2), ['Red', 'Redstone', 'Wheat'], MC('redstone'), [MC('wheat')], None, None),
    ('nether_wart', 'Nether Wart', 'Адский нарост', 5, 3, 3, 1, (4, 2, 0, 0, 1), ['Red', 'Nether', 'Ingredient', 'Soulsand'], MC('nether_wart'), [], None, (MC('nether_wart'), 1)),
    ('terra_wart', 'Terra Wart', 'Земляной нарост', 5, 3, 3, 1, (2, 0, 3, 0, 0), ['Blue', 'Aether', 'Consumable', 'Snow'], I('terra_wart'), [], None, None),
    ('ferru', 'Ferru', 'Ферру', 6, 4, 4, 2, (2, 0, 0, 1, 0), ['Gray', 'Leaves', 'Metal'], ('small_dust', 'Iron'), [], 'Iron', None),
    ('cyprium', 'Cyprium', 'Циприум', 6, 4, 4, 2, (2, 0, 0, 1, 0), ['Orange', 'Leaves', 'Metal'], ('small_dust', 'Copper'), [], 'Copper', None),
    ('stagnium', 'Stagnium', 'Стагниум', 6, 4, 4, 2, (2, 0, 0, 1, 0), ['Shiny', 'Leaves', 'Metal'], ('small_dust', 'Tin'), [], 'Tin', None),
    ('plumbiscus', 'Plumbiscus', 'Плюмбискус', 6, 4, 4, 2, (2, 0, 0, 1, 0), ['Dense', 'Leaves', 'Metal'], ('small_dust', 'Lead'), [], 'Lead', None),
    ('shining', 'Shining', 'Сияние', 7, 4, 4, 2, (2, 0, 0, 2, 0), ['Silver', 'Leaves', 'Metal'], ('small_dust', 'Silver'), [], 'Silver', None),
    ('aurelia', 'Aurelia', 'Аурелия', 8, 4, 4, 2, (2, 0, 0, 2, 0), ['Gold', 'Leaves', 'Metal'], ('small_dust', 'Gold'), [], 'Gold', None),
    ('coffee', 'Coffee', 'Кофе', 7, 5, 4, 3, (1, 4, 1, 2, 0), ['Leaves', 'Ingredient', 'Beans'], I('coffee_beans'), [], None, None),
    ('hops', 'Hops', 'Хмель', 5, 7, 7, 3, (2, 2, 0, 1, 1), ['Green', 'Ingredient', 'Wheat'], I('hops'), [], None, None),
    ('carrots', 'Carrots', 'Морковь', 2, 3, 3, 1, (0, 4, 0, 0, 2), ['Orange', 'Food', 'Carrots'], MC('carrot'), [], None, (MC('carrot'), 1)),
    ('potato', 'Potato', 'Картофель', 2, 4, 4, 1, (0, 4, 0, 0, 2), ['Yellow', 'Food', 'Potato'], MC('potato'), [MC('poisonous_potato')], None, (MC('potato'), 1)),
    ('eating_plant', 'Eating Plant', 'Хищное растение', 6, 6, 4, 3, (1, 1, 3, 1, 4), ['Bad', 'Food'], MC('rotten_flesh'), [], None, None),
    ('red_mushroom', 'Red Mushroom', 'Красный гриб', 2, 3, 3, 1, (0, 4, 0, 0, 4), ['Red', 'Food', 'Mushroom'], MC('red_mushroom'), [], None, (MC('red_mushroom'), 1)),
    ('brown_mushroom', 'Brown Mushroom', 'Коричневый гриб', 2, 3, 3, 1, (0, 4, 0, 0, 4), ['Brown', 'Food', 'Mushroom'], MC('brown_mushroom'), [], None, (MC('brown_mushroom'), 1)),
]

# GT_MachineRecipeLoader:1604-1693 addProcess(crop, material, chance, main); with NerfCrops only the chemical reactor and the autoclave
PROCESS = [
    ('coppon_fiber', ['Copper', 'Tetrahedrite', 'Chalcopyrite', 'Malachite', 'Pyrite', 'Stibnite']), ('tine_twig', ['Tin', 'Cassiterite']),
    ('plumbilia_leaf', ['Lead', 'Galena']), ('nickelback_leaf', ['Nickel', 'Garnierite', 'Pentlandite', 'Cobaltite', 'Wulfenite', 'Powellite']),
    ('galvania_leaf', ['Zinc', 'Sphalerite', 'Sulfur']), ('argentia_leaf', ['Silver']), ('bauxia_leaf', ['Aluminium', 'Bauxite']),
    ('pyrolusium_leaf', ['Manganese', 'Grossular', 'Spessartine', 'Pyrolusite', 'Tantalite']), ('titania_leaf', ['Titanium', 'Ilmenite', 'Bauxite']),
    ('scheelinium_leaf', ['Scheelite', 'Tungstate', 'Lithium']), ('platina_leaf', ['Platinum', 'Cooperite', 'Palladium', 'Neodymium', 'Bastnasite']),
    ('quantaria_leaf', ['Iridium']), ('quantaria_leaf_osmium', ['Osmium']), ('reactoria_leaf', ['Pitchblende']),
    ('uranium_leaf', ['Uraninite', 'Uranium', 'Pitchblende', 'Uranium235']), ('thunder_leaf', ['Thorium']),
    ('stargatium_leaf', ['Naquadah', 'NaquadahEnriched', 'Naquadria']), ('bobs_yer_uncle_berry', ['Emerald', 'Beryllium']),
]

# crops whose attributes name no colour: the colour of their fruit
CROP_COLOURS = {'tomato': (210, 40, 30), 'chilly': (200, 20, 20), 'lemon': (245, 230, 60), 'grape': (110, 40, 130), 'onion': (225, 205, 170), 'cucumber': (60, 140, 40),
                'tea': (90, 150, 60), 'rape': (240, 215, 40), 'oilberries': (30, 30, 30), 'glowheat': (250, 230, 110), 'transformium': (190, 60, 190),
                'flax': (200, 200, 220), 'eggplant': (240, 240, 230), 'corium': (150, 100, 60), 'milkwart': (245, 245, 245), 'slimeplant': (110, 200, 90),
                'spidernip': (220, 220, 220), 'tearstalks': (210, 240, 240), 'zomplant': (100, 140, 80), 'corpseplant': (140, 110, 90), 'blazereed': (240, 160, 30),
                'withereed': (40, 40, 40), 'creeperweed': (80, 180, 70), 'enderbloom': (40, 120, 110), 'meatrose': (220, 120, 140), 'trollplant': (180, 40, 60),
                'fertilia': (200, 200, 160), 'evil_ore': (230, 220, 210), 'coffee': (90, 55, 30), 'eating_plant': (120, 60, 40), 'stickreed': (200, 140, 40)}
ATTR_COLOURS = {'Red': (200, 40, 40), 'Yellow': (240, 220, 40), 'Blue': (50, 80, 220), 'Purple': (150, 60, 190), 'Black': (35, 30, 40),
                'Orange': (240, 140, 30), 'White': (230, 220, 170), 'Brown': (120, 75, 40), 'Green': (60, 170, 50), 'Gray': (150, 150, 150)}


def parse_gt_crops():
    path = os.path.join(GT_SRC, 'loaders', 'postload', 'GT_CropLoader.java')
    src = open(path, encoding='utf-8').read()
    rx = re.compile(r'new GT_BaseCrop\((\d+),\s*"([^"]+)",\s*"([^"]*)",\s*([^,]+?),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*'
                    r'new String\[\]\{([^}]*)\},\s*(?:Materials\.(\w+),)?')
    out = []
    for m in rx.finditer(src):
        g = m.groups()
        out.append({'gtId': int(g[0]), 'name': g[1], 'by': g[2], 'seed': GT_SEEDS.get(g[3].strip()), 'tier': max(1, int(g[4])), 'max': max(3, int(g[5])),
                    'speed': int(g[6]), 'after': int(g[7]), 'harvest': int(g[8]), 'stats': [int(x) for x in g[9:14]],
                    'attrs': re.findall(r'"([^"]+)"', g[14]), 'block': g[15]})
    return out


def generate(g):
    A, D, write, lang, shaped, shapeless, tag_of, ref, mass, mat_by_name = g['A'], g['D'], g['write'], g['lang'], g['shaped'], g['shapeless'], g['tag_of'], g['ref'], g['mass'], g['mat_by_name']
    rec = {}
    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1: {'id': i, 'count': c}
    unresolved = []

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, fout=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(dur), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if fout:
            r['fluid_outputs'] = [fout]
        rec[f'{rtype}/{rid}'] = r

    def item_id(spec):
        if isinstance(spec, tuple):
            r = ref(*spec)
            if r is None:
                unresolved.append(spec)
            return r
        return spec

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def save_tex(rel, im):
        p = os.path.join(A, 'textures', rel + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)

    def item(iid, en, ru, im=None, tex=None):
        if im is not None:
            save_tex(f'item/crop/{iid}', im)
            tex = f'{NS}:item/crop/{iid}'
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': tex}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)

    def gt_icon(gid):
        p = os.path.join(GT, 'items', 'gt.metaitem.02', f'{gid}.png')
        im = Image.open(p).convert('RGBA')
        return im.crop((0, 0, im.width, im.width))

    # --- GT drops ------------------------------------------------------------------------------------------------------------
    foods = {}
    for gid, iid, en, ru, food in DROPS:
        item(iid, en, ru, gt_icon(gid))
        if food:
            foods[iid] = food

    # --- cards ---------------------------------------------------------------------------------------------------------------
    cards = []

    def colour_of(attrs, block, drop):
        if block and block in mat_by_name:
            c = mat_by_name[block]['color']
            return ((c >> 16) & 255, (c >> 8) & 255, c & 255)
        for a in attrs:
            if a in ATTR_COLOURS:
                return ATTR_COLOURS[a]
        return (200, 60, 160) if 'uu' in str(drop) else (210, 200, 60)

    for cid, en, ru, tier, mx, harvest, after, stats, attrs, drop, special, block, seed in IC2:
        cards.append({'id': cid, 'en': en, 'ru': ru, 'by': 'IC2', 'mod': 'IC2', 'tier': tier, 'max': mx, 'harvest': min(harvest, mx), 'after': after,
                      'speed': 0, 'stats': list(stats), 'attrs': attrs, 'drop': item_id(drop) if drop else None,
                      'special': [item_id(s) for s in special], 'block': block, 'seed': seed, 'colour': colour_of(attrs, block, drop)})
    for c in parse_gt_crops():
        drop, special = GT_DROPS[c['name']]
        d = item_id(drop)
        sp = [x for x in (item_id(s) for s in special) if x]
        if d is None:   # Twilight Forest materials that IIS 2 does not have: the crop is left out (GT itself skips a crop without a drop)
            continue
        cid = re.sub(r'[^a-z0-9]+', '_', c['name'].lower()).strip('_')
        if cid in ('red_mushrooms', 'brown_mushrooms'):
            cid = 'gt_' + cid
        mx = c['max']
        cards.append({'id': cid, 'en': c['name'], 'ru': GT_RU.get(c['name'], c['name']), 'by': c['by'], 'mod': 'GregTech', 'tier': c['tier'], 'max': mx,
                      'harvest': min(max(c['harvest'], 2), mx), 'after': min(max(c['after'], 1), mx - 1), 'speed': c['speed'], 'stats': c['stats'],
                      'attrs': c['attrs'], 'drop': d, 'special': sp, 'block': c['block'], 'seed': c['seed'], 'colour': colour_of(c['attrs'], c['block'], d)})
    for c in cards:
        if c['id'] in CROP_COLOURS:
            c['colour'] = CROP_COLOURS[c['id']]
        if c['block']:
            c['blockMat'] = mat_by_name[c['block']]['id'] if c['block'] in mat_by_name else c['block'].lower()
        name(f'crop.{NS}.{c["id"]}', c['en'], c['ru'])
    write(os.path.join(g['GEN'], NS, 'crops.json'), [{k: v for k, v in c.items() if k not in ('colour', 'en', 'ru', 'block')} | {'block': c.get('blockMat')} for c in cards])

    # --- crop textures: drawn (IC2 and GT crop pictures are not in the IIS 1 folder) ----------------------------------------------
    VANILLA = {'wheat': lambda s, m: f'minecraft:block/wheat_stage{round((s - 1) * 7 / (m - 1))}',
               'carrots': lambda s, m: f'minecraft:block/carrots_stage{[0, 2, 3][s - 1]}',
               'potato': lambda s, m: f'minecraft:block/potatoes_stage{s - 1}',
               'nether_wart': lambda s, m: f'minecraft:block/nether_wart_stage{s - 1}',
               'reed': lambda s, m: 'minecraft:block/sugar_cane', 'red_mushroom': lambda s, m: 'minecraft:block/red_mushroom',
               'brown_mushroom': lambda s, m: 'minecraft:block/brown_mushroom', 'gt_red_mushrooms': lambda s, m: 'minecraft:block/red_mushroom',
               'gt_brown_mushrooms': lambda s, m: 'minecraft:block/brown_mushroom',
               'dandelion': lambda s, m: 'minecraft:block/dandelion' if s == m else None, 'rose': lambda s, m: 'minecraft:block/poppy' if s == m else None}

    def plant(seed, s, mx, colour, attrs, weed=False):
        import random
        rnd = random.Random(seed * 31 + s)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        px = im.load()
        frac = s / mx
        h = max(2, round(14 * frac))
        leaf = (60, 140, 40) if not weed else (90, 120, 40)
        dark = (35, 95, 25)
        metal = 'Metal' in attrs
        if metal:
            leaf = tuple((a + b) // 2 for a, b in zip(leaf, colour))
        for x0 in (3, 7, 11) if not weed else (2, 5, 8, 11, 13):
            x = x0 + rnd.randint(0, 1)
            hh = h - rnd.randint(0, 2) if not weed else max(1, round(h * rnd.uniform(0.5, 0.9)))
            for y in range(15, 15 - hh, -1):
                px[x, y] = dark + (255,)
                if (15 - y) % 3 == 1 and 15 - y > 0:
                    side = x - 1 if rnd.random() < 0.5 else x + 1
                    if 0 <= side < 16:
                        px[side, y] = leaf + (255,)
            top = 16 - hh
            if s == mx and not weed:
                for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 1)):
                    xx, yy = x + dx, top + dy - 1
                    if 0 <= xx < 16 and 0 <= yy < 16:
                        k = 1.0 if (dx, dy) == (0, 0) else 0.8
                        px[xx, yy] = tuple(min(255, int(v * k)) for v in colour) + (255,)
            elif s >= mx - 1 and not weed and 0 <= top - 1 < 16:
                px[x, top - 1] = tuple(int(v * 0.7 + 60 * 0.3) for v in colour) + (255,)
        return im

    def sticks(cross):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        for x in (3, 12):
            d.line([(x, 2), (x, 15)], fill=(150, 110, 60, 255))
            d.point((x, 2), fill=(110, 80, 40, 255))
        if cross:
            d.line([(0, 6), (15, 6)], fill=(165, 125, 70, 255))
            d.line([(0, 11), (15, 11)], fill=(140, 100, 55, 255))
        return im

    save_tex('block/crop/sticks', sticks(False))
    save_tex('block/crop/cross', sticks(True))
    for k in ('sticks', 'cross'):
        write(os.path.join(A, 'models', 'block', 'crop', f'{k}.json'), {'parent': 'minecraft:block/crop', 'render_type': 'minecraft:cutout',
                                                                         'textures': {'crop': f'{NS}:block/crop/{k}', 'particle': f'{NS}:block/crop/{k}'}})
    variants = {}
    for size in range(8):
        variants[f'crop=0,size={size}'] = {'model': f'{NS}:block/crop/sticks'}
        variants[f'crop=1,size={size}'] = {'model': f'{NS}:block/crop/cross'}
    n_tex = 0
    for idx, c in enumerate(cards):
        mx = c['max']
        for s in range(1, mx + 1):
            tex = VANILLA[c['id']](s, mx) if c['id'] in VANILLA else None
            if tex is None:
                save_tex(f'block/crop/{c["id"]}_{s}', plant(idx, s, mx, c['colour'], c['attrs'], c['id'] == 'weed'))
                tex = f'{NS}:block/crop/{c["id"]}_{s}'
                n_tex += 1
            write(os.path.join(A, 'models', 'block', 'crop', f'{c["id"]}_{s}.json'), {'parent': 'minecraft:block/crop', 'render_type': 'minecraft:cutout',
                                                                                     'textures': {'crop': tex, 'particle': tex}})
        for size in range(8):
            s = min(max(size, 1), mx)
            variants[f'crop={idx + 2},size={size}'] = {'model': f'{NS}:block/crop/{c["id"]}_{s}'}
    write(os.path.join(A, 'blockstates', 'crop.json'), {'variants': variants})
    name(f'block.{NS}.crop', 'Crop', 'Грядка')

    # --- items ---------------------------------------------------------------------------------------------------------------------
    def icon(draw_fn):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        draw_fn(ImageDraw.Draw(im))
        return im

    item('crop_sticks', 'Crop', 'Палочки для грядки', sticks(False))
    item('crop_seed', 'Seed Bag', 'Мешочек семян', icon(lambda d: (d.polygon([(4, 6), (11, 6), (13, 14), (2, 14)], fill=(170, 130, 80, 255), outline=(110, 80, 45, 255)),
                                                                    d.line([(5, 5), (10, 5)], fill=(90, 60, 30, 255)), d.point((7, 10), fill=(90, 160, 60, 255)))))
    name('item.iis.crop_seed.unknown', 'Unknown Seeds', 'Неизвестные семена')
    name('item.iis.crop_seed.named', 'Seed Bag (%s)', 'Мешочек семян (%s)')
    item('fertilizer', 'Fertilizer', 'Удобрение', icon(lambda d: (d.ellipse([3, 4, 12, 13], fill=(120, 95, 70, 255)), d.point([(5, 7), (9, 9), (7, 11), (10, 6)], fill=(230, 230, 210, 255)))))
    item('weed_ex', 'Weed-EX', 'Гербицид Weed-EX', icon(lambda d: (d.rectangle([5, 3, 10, 14], fill=(200, 200, 210, 255), outline=(90, 90, 100, 255)),
                                                                   d.rectangle([5, 7, 10, 11], fill=(150, 60, 170, 255)))))
    item('weeding_trowel', 'Weeding Trowel', 'Совок для прополки', icon(lambda d: (d.line([(3, 13), (8, 8)], fill=(110, 80, 45, 255), width=2),
                                                                                    d.polygon([(8, 8), (13, 3), (14, 6), (10, 10)], fill=(180, 180, 190, 255)))))
    item('grin_powder', 'Grin Powder', 'Порошок ухмылки', icon(lambda d: d.ellipse([3, 6, 12, 13], fill=(120, 170, 60, 255), outline=(70, 110, 30, 255))))
    item('coffee_beans', 'Coffee Beans', 'Кофейные зёрна', icon(lambda d: [d.ellipse([x, y, x + 4, y + 5], fill=(90, 55, 30, 255)) for x, y in ((3, 4), (8, 6), (5, 9))]))
    item('hops', 'Hops', 'Хмель', icon(lambda d: [d.ellipse([x, y, x + 5, y + 6], fill=(140, 190, 70, 255), outline=(80, 130, 40, 255)) for x, y in ((3, 3), (8, 6), (4, 9))]))
    item('terra_wart', 'Terra Wart', 'Земляной нарост', icon(lambda d: d.ellipse([3, 4, 12, 13], fill=(70, 110, 200, 255), outline=(40, 60, 140, 255))))
    item('crop_lens', 'Crop Lens', 'Линза для грядок', icon(lambda d: (d.line([(9, 9), (14, 14)], fill=(130, 90, 50, 255), width=2),
                                                                       d.ellipse([2, 2, 10, 10], fill=(170, 220, 240, 160), outline=(80, 80, 90, 255)))))
    item('cropnalyzer', 'Cropnalyzer', 'Анализатор культур', icon(lambda d: (d.rectangle([3, 2, 12, 14], fill=(70, 70, 80, 255), outline=(30, 30, 35, 255)),
                                                                             d.rectangle([5, 4, 10, 8], fill=(90, 200, 90, 255)), d.point([(5, 11), (8, 11), (10, 12)], fill=(220, 60, 60, 255)))))
    for key, en, ru in (
            ('crop.iis.lens.title', '%s, size %s of %s', '%s, размер %s из %s'), ('crop.iis.lens.empty', 'Crop sticks', 'Пустая грядка'),
            ('crop.iis.lens.cross', 'Cross crop sticks', 'Двойная грядка (для скрещивания)'),
            ('crop.iis.lens.stats', 'Growth %s, gain %s, resistance %s', 'Рост %s, урожайность %s, стойкость %s'),
            ('crop.iis.lens.storage', 'Nutrients %s, water %s, Weed-EX %s', 'Питание %s, вода %s, гербицид %s'),
            ('crop.iis.lens.env', 'Humidity %s, nutrients %s, air %s', 'Влажность %s, почва %s, воздух %s'),
            ('crop.iis.lens.block', 'Needs %s ore or block below to ripen', 'Для созревания снизу нужна руда или блок: %s'),
            ('crop.iis.seed.tier', 'Tier %s, discovered by %s', 'Уровень %s, открыл %s'), ('crop.iis.seed.attrs', 'Attributes: %s', 'Свойства: %s'),
            ('crop.iis.seed.stats', 'Gr %s  Ga %s  Re %s', 'Рост %s  Урож %s  Стойк %s'), ('crop.iis.seed.scan', 'Scan level %s of 4', 'Уровень анализа %s из 4'),
            ('item.iis.cropnalyzer.tooltip.0', 'Hold seeds in the other hand and use: scans them (10, 90, 900, 9000 EU per level)',
             'Держите семена в другой руке и используйте: анализ (10, 90, 900, 9000 EU за уровень)'),
            ('crop.iis.cropnalyzer.no_seed', 'Take a seed bag into the other hand', 'Возьмите мешочек семян в другую руку'),
            ('crop.iis.cropnalyzer.no_charge', 'Not enough energy: %s EU needed', 'Не хватает энергии: нужно %s EU'),
            ('item.iis.weeding_trowel.tooltip.0', 'Right click a weed: removes it and gives its seeds', 'ПКМ по сорняку: убирает его и даёт его семена'),
            ('item.iis.crop_lens.tooltip.0', 'Right click a crop: shows its stats and conditions', 'ПКМ по грядке: показывает свойства и условия'),
            ('block.iis.crop.tooltip.0', 'Place on farmland; a second one on empty sticks makes cross sticks for crossbreeding',
             'Ставится на пашню; вторые палочки на пустую грядку — двойная грядка для скрещивания'),
            ('block.iis.crop_matron.tooltip.0', 'Feeds, waters and weeds crops 9 × 3 × 9 around it: fertilizer, Weed-EX and water from its slots and tank',
             'Удобряет, поливает и пропалывает грядки 9 × 3 × 9 вокруг: удобрение, гербицид и вода из слотов и бака'),
            ('block.iis.crop_harvester.tooltip.0', 'Harvests ripe crops 9 × 3 × 9 around it into its inventory and removes weeds',
             'Собирает спелый урожай 9 × 3 × 9 вокруг в свой инвентарь и убирает сорняки'),
            ('item.iis.fertilizer.tooltip.0', 'Right click a crop: +100 nutrients', 'ПКМ по грядке: +100 питания'),
            ('item.iis.weed_ex.tooltip.0', 'Right click a crop: protects it from weeds', 'ПКМ по грядке: защищает от сорняков'),
            ('item.iis.crop_seed.tooltip.0', 'Plant on empty crop sticks', 'Сажается на пустую грядку'),
            ('itemGroup.iis.crops', 'IIS 2.0: Crops', 'ИИС 2.0: грядки')):
        name(key, en, ru)

    # --- machines (IC2 crop-matron, crop harvester) --------------------------------------------------------------------------------
    ic = os.path.join(g['TEX'], 'lv', 'iconsets')

    def machine(bid, en, ru, mark):
        base_side = Image.open(os.path.join(ic, 'MACHINE_LV_SIDE.png')).convert('RGBA')
        base_side = base_side.crop((0, 0, base_side.width, base_side.width)).resize((16, 16), Image.NEAREST)
        faces = {}
        for face in ('front', 'side', 'top'):
            im = base_side.copy()
            if face != 'side':
                ImageDraw.Draw(im).rectangle([4, 4, 11, 11], fill=(40, 40, 45, 255))
                mark(ImageDraw.Draw(im), face)
            save_tex(f'block/crop/{bid}_{face}', im)
            faces[face] = f'{NS}:block/crop/{bid}_{face}'
        write(os.path.join(A, 'models', 'block', f'{bid}.json'), {'parent': 'minecraft:block/orientable', 'textures': {
            'front': faces['front'], 'side': faces['side'], 'top': faces['top'], 'particle': faces['side']}})
        write(os.path.join(A, 'blockstates', f'{bid}.json'), {'variants': {f'facing={f}': {'model': f'{NS}:block/{bid}', **({'y': y} if y else {})}
                                                                          for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
        write(os.path.join(A, 'models', 'item', f'{bid}.json'), {'parent': f'{NS}:block/{bid}'})
        write(os.path.join(A, 'items', f'{bid}.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', f'{bid}.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': I(bid)}],
                                                                                                            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        name(f'block.{NS}.{bid}', en, ru)

    machine('crop_matron', 'Crop-Matron', 'Crop-Matron', lambda d, f: (d.line([(7, 5), (7, 10)], fill=(70, 170, 60, 255)), d.point([(6, 6), (8, 7)], fill=(70, 170, 60, 255)),
                                                                         d.point((9, 5), fill=(60, 120, 230, 255))))
    machine('crop_harvester', 'Crop Harvester', 'Сборщик урожая', lambda d, f: (d.line([(5, 10), (10, 5)], fill=(180, 180, 190, 255)), d.point([(6, 6), (9, 9)], fill=(230, 200, 60, 255))))
    g['block_tags']['minecraft:mineable/pickaxe'] += [I('crop_matron'), I('crop_harvester')]

    # --- recipes -------------------------------------------------------------------------------------------------------------------
    # IC2: "S S / S S" sticks -> 2 crops; scrap + bone meal -> 2 fertilizer; redstone / grin powder / empty cell -> Weed-EX (from memory)
    shaped('crop/crop_sticks', I('crop_sticks'), 2, ['S S', 'S S'], {'S': MC('stick')})
    shapeless('crop/fertilizer', I('fertilizer'), 2, [I('scrap'), MC('bone_meal')])
    shaped('crop/weed_ex', I('weed_ex'), 1, [' R ', ' G ', ' C '], {'R': MC('redstone'), 'G': I('grin_powder'), 'C': I('capsule')})
    shapeless('crop/grin_powder', I('grin_powder'), 1, [MC('spider_eye'), MC('poisonous_potato')])
    # GT_IC2.zs:222-226
    shaped('crop/weeding_trowel', I('weeding_trowel'), 1, ['RhR', ' R ', 'PRP'], {'R': tag_of('rod', 'Iron'), 'P': tag_of('plate', 'Rubber')})
    # IC2: cables, redstone, glass, circuit (from memory)
    shaped('crop/cropnalyzer', I('cropnalyzer'), 1, [' WW', 'RGR', 'RCR'], {'W': '#iis:cables/1x/tin', 'R': MC('redstone'), 'G': MC('glass'), 'C': '#c:circuits/basic'})
    # IC2CropsTools.zs: glass pane, file, string, long wooden rod (IIS 2 has no long wooden rod: a stick)
    shaped('crop/crop_lens', I('crop_lens'), 1, ['Gf ', ' S ', '  R'], {'G': MC('glass_pane'), 'S': MC('string'), 'R': MC('stick')})
    # GT_IC2.zs:623-637
    shaped('crop/crop_matron', I('crop_matron'), 1, ['ACA', 'PHV', 'BCE'], {'A': I('robot_arm_lv'), 'C': '#c:circuits/basic', 'P': I('electric_pump_lv'), 'H': I('hull_lv'),
                                                                           'V': I('conveyor_module_lv'), 'B': MC('chest'), 'E': I('capsule')})
    shaped('crop/crop_harvester', I('crop_harvester'), 1, ['ACA', 'PHS', 'WVW'], {'A': I('robot_arm_lv'), 'C': '#c:circuits/basic', 'P': I('electric_piston_lv'), 'H': I('hull_lv'),
                                                                                  'S': I('sensor_lv'), 'W': '#iis:cables/1x/tin', 'V': I('conveyor_module_lv')})
    # GT_Loader_MetaTileEntities:1127-1134 "CRC/WFW/CMC"
    for t, circuit, cable in (('lv', 'basic', 'tin'), ('mv', 'good', 'copper'), ('hv', 'advanced', 'gold'), ('ev', 'data', 'aluminium'), ('iv', 'elite', 'tungsten'),
                              ('luv', 'master', 'vanadium_gallium'), ('zpm', 'ultimate', 'naquadah'), ('uv', 'superconductor', 'naquadah_alloy')):
        shaped(f'lv/organic_replicator_{t}', I(f'organic_replicator_{t}'), 1, ['CRC', 'WFW', 'CMC'], {'M': I(f'hull_{t}'), 'R': I('crop_sticks'), 'C': f'#c:circuits/{circuit}',
                                                                                                    'F': I(f'field_generator_{t}'), 'W': f'#iis:cables/1x/{cable}'})

    # GT_MachineRecipeLoader:76, 373-377: fluid extractor
    fo = lambda fid, mb: {'id': fid, 'amount': mb}
    mrec('fluid_extractor', 'crop_rape', [ing(I('rape'))], [], 32, 2, fout=fo(I('seedoil'), 50))
    mrec('fluid_extractor', 'crop_indigo', [ing(I('indigo_blossom'))], [], 128, 4, fout=fo(I('indigo'), 144))
    milk = ref('dust', 'Milk')
    mrec('fluid_extractor', 'crop_milk_wart', [ing(I('milk_wart'))], [dict(out(milk), chance=1000)] if milk else [], 128, 4, fout=fo(I('milk'), 150))
    mrec('fluid_extractor', 'crop_oil_berry', [ing(I('oil_berry'))], [], 128, 4, fout=fo(I('oil'), 100))
    mrec('fluid_extractor', 'crop_uum_berry', [ing(I('uum_berry'))], [], 128, 4, fout=fo(I('uumatter'), 4))
    mrec('fluid_extractor', 'crop_uua_berry', [ing(I('uua_berry'))], [], 128, 4, fout=fo(I('uuamplifier'), 4))
    # :1712-1750 with NerfCrops: 9 leaves + crushed ore + water -> 4 purified (+ molten first byproduct); 16 leaves + UU-matter -> 1 purified
    n_proc = 0
    for crop, mats in PROCESS:
        for n in mats:
            if not ref('crushed', n) or not ref('purified', n):
                continue
            mid = mat_by_name[n]['id']
            bp = mat_by_name[n]['byProducts'][:1]
            molten = f'molten_{bp[0]}' if bp and f'fluid_type.{NS}.molten_{bp[0]}' in lang['en_us'] else None
            mrec('chemical_reactor', f'crop_{crop}_{mid}', [ing(I(crop), 9), ing(tag_of('crushed', n))], [out(ref('purified', n), 4)], 96, 24,
                 fin={'ingredient': MC('water'), 'amount': 1000}, fout=fo(I(molten), 144) if molten else None)
            mrec('autoclave', f'crop_{crop}_{mid}', [ing(I(crop), 16)], [out(ref('purified', n))], mass(n) * 128, 384,
                 fin={'ingredient': I('uumatter'), 'amount': max(1, (mass(n) + 9) // 10)})
            n_proc += 1
    # :1752-1765 fertilizer from saltpeter or potassium and sulfur, phosphates or ash
    for a, an in (('Saltpeter', 0), ('Potassium', 1)):
        for b, cnt in (('Sulfur', 2), ('TricalciumPhosphate', 3), ('Phosphate', 2), ('Ash', 1), ('DarkAsh', 1)):
            if not ref('dust', a) or not ref('dust', b):
                continue
            k = cnt + an
            for wn, wid in (('water', MC('water')), ('distilled', I('distilled_water'))):
                mrec('chemical_reactor', f'fertilizer_{mat_by_name[a]["id"]}_{mat_by_name[b]["id"]}_{wn}', [ing(tag_of('dust', a)), ing(tag_of('dust', b), 3 if b == 'Ash' else 1)],
                     [out(I('fertilizer'), k)], 100 * k, 30, fin={'ingredient': wid, 'amount': 1000})
    for rid, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', rid + '.json'), r)
    g['crop_foods'] = foods
    return {'cards': len(cards), 'gt_drops': len(DROPS), 'textures': n_tex, 'processing': n_proc, 'recipes': len(rec), 'unresolved': sorted(set(map(str, unresolved)))}
