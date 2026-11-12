"""The opening of the quest book, written for IIS 2 (D151, owner in the interview of 13.11: "rewrite the beginning for IIS 2").

Two chapters take the place of "First Steps" and "First Tools" of the IIS 1 book: "Landing" (the shuttle, the three paths, wood, flint,
stone, the first ores of this world) and "Bronze and tools" (GT hand tools, bronze, the first parts). The rest of the book stays.
A quest of the old chapters is replaced by the new quest that asks for the same item (ALIAS for the ones that changed), so every later
quest that hung on it hangs on the new one; an old quest with no counterpart hands its own dependencies on.

A task: 'id', 'id*N', '#tag*N' (any item of the tag) or a raw FTB Filter System expression ('or(item(a)item(b))').
A dependency: a key of these chapters, or '@' + the task of a quest that stays in the IIS 1 book ('@#iis:tools/hammer').
"""
import json
import os

NS = 'iis'
BASE = 0x120000
STEP_X, STEP_Y = 1.5, 1.5
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# veins of the Overworld the first hours need: (vein in veins.json, what it gives)
VEINS = [('cassiterite', 'олово (касситерит)', 'tin (cassiterite)'), ('tetrahedrite', 'медь (тетраэдрит), сурьма', 'copper (tetrahedrite), antimony'),
         ('copper', 'медь (халькопирит), пирит, железо', 'copper (chalcopyrite), pyrite, iron'), ('iron', 'железо (лимониты, полосчатое железо), малахит', 'iron (limonites, banded iron), malachite'),
         ('magnetite', 'железо (магнетит)', 'iron (magnetite)'), ('coal', 'уголь', 'coal'), ('lignite', 'бурый уголь', 'lignite'),
         ('galena', 'свинец и серебро (галенит)', 'lead and silver (galena)'), ('redstone', 'красная пыль, рубин, киноварь', 'redstone, ruby, cinnabar'),
         ('nickel', 'никель, кобальт', 'nickel, cobalt'), ('diamond', 'алмазы, графит', 'diamonds, graphite')]


def vein_lines():
    table = {v['name']: v for v in json.load(open(os.path.join(ROOT, 'src', 'main', 'resources', 'iis', 'veins.json'), encoding='utf-8'))}
    ru, en = [], []
    for name, r, e in VEINS:
        v = table[name]
        ru.append(f'Y {v["minY"]} … {v["maxY"]}: {r}')
        en.append(f'Y {v["minY"]} to {v["maxY"]}: {e}')
    return ru, en


_VR, _VE = vein_lines()
SILVER = 'or(item(iis:crushed_silver)item(iis:impure_dust_silver)item(iis:ingot_silver)item(iis:ore_silver))'
MEAT = 'or(item(minecraft:cooked_beef)item(minecraft:cooked_porkchop)item(minecraft:cooked_chicken)item(minecraft:cooked_mutton)item(minecraft:cooked_rabbit)item(minecraft:cooked_cod)item(minecraft:cooked_salmon))'
PICK2 = 'or(item(iis:pickaxe_head_iron)item(iis:pickaxe_head_bronze))'

# key, column, row, tasks, deps, (ru title, en title), (ru text, en text)
LANDING = [
    ('start', 0, 3, [], [], ('Аварийная посадка', 'Crash landing'),
     ('Челнок упал, и он ваш: один на игрока на весь мир. После смерти новый не появится.\n\nЭлектроника в нём сгорела, корпус сверхпрочный. Сейчас он почти бесполезен, но не ломайте и не теряйте его: позже корпус можно переработать, а на крыше живёт очаг, с которого начинается путь Био.\n\nЭта глава и следующая написаны для ИИС 2. Дальше идёт книга ИИС 1.',
      'The shuttle crashed, and it is yours: one per player for the whole world. No new one after death.\n\nIts electronics are burnt, its hull is extremely tough. It is nearly useless now, but keep it: the hull can be recycled much later, and the growth on its roof is where the Bio path begins.\n\nThis chapter and the next are written for IIS 2. The book of IIS 1 goes on after them.')),
    ('paths', 0, 1.5, [], ['start'], ('Три пути', 'Three paths'),
     ('Кибер — машины: камень, бронза, пар, электричество от LV до UV. Эта книга ведёт по нему.\n\nБио — живая колония. Она начинается с ядра и пасти на крыше вашего челнока: накормите пасть едой, и ткань начнёт расти.\n\nДуо — когда развиты обе ветви.\n\nПути не мешают друг другу и не требуют друг друга. Выбирать навсегда не нужно: можно идти одним, а к другому вернуться позже.',
      'Cyber is machines: stone, bronze, steam, electricity from LV to UV. This book leads along it.\n\nBio is a living colony. It starts from the core and the maw on the roof of your shuttle: feed the maw and the tissue begins to grow.\n\nDuo is for when both branches are developed.\n\nThe paths neither block nor need each other. Nothing is chosen for good.')),
    ('book', 0, 4.5, [], ['start'], ('Как читать книгу', 'How to read the book'),
     ('Рецепт верстака нарисован прямо в тексте квеста. Остальные рецепты смотрите в JEI: R по предмету — как сделать, U — где используется.\n\nЗа квесты дают жетоны. Их принимает магазин наград (глава «Что ещё есть в ИИС 2»).\n\nГлавы с пометкой [О] — общие: прогресс, удобства, все машины и многоблоки.\n\nМир сам сохраняется в архив каждый игровой день, хранятся последние 20 копий.',
      'A crafting table recipe is drawn right in the quest text. See the other recipes in JEI: R on an item for how to make it, U for its uses.\n\nQuests pay credits. The reward shop takes them (chapter "What else IIS 2 has").\n\nChapters marked [G] are general: progress, comforts, every machine and multiblock.\n\nThe world is archived every game day, the last 20 copies are kept.')),
    ('logs', 1, 3, ['#minecraft:logs*4'], ['start'], ('Древесина', 'Wood'),
     ('Срубите любое дерево.', 'Fell any tree.')),
    ('planks', 2, 3, ['#minecraft:planks*4'], ['logs'], ('Доски', 'Planks'),
     ('Из бревна в сетке крафта выходит только две доски. Пила даст больше — она в следующей главе.', 'A log gives only two planks in the crafting grid. A saw gives more: it is in the next chapter.')),
    ('table', 3, 3, ['minecraft:crafting_table'], ['planks'], ('Верстак', 'Crafting table'), ('Почти всё начало игры делается на нём.', 'Nearly all of the early game is made on it.')),
    ('stick', 3, 4, ['minecraft:stick*4'], ['planks'], ('Палки', 'Sticks'), ('Рукояти инструментов и факелы.', 'Tool handles and torches.')),
    ('gravel', 1, 1, ['minecraft:gravel*4'], ['start'], ('Гравий', 'Gravel'), ('Гравий лежит у воды и в пещерах. Из него выпадает кремень.', 'Gravel lies by water and in caves. Flint drops from it.')),
    ('flint', 2, 1, ['minecraft:flint*3'], ['gravel'], ('Кремень', 'Flint'),
     ('Кремень выпадает не с каждого блока: ставьте гравий и копайте снова.', 'Flint does not drop from every block: place the gravel and dig again.')),
    ('flint_pickaxe', 4, 1, ['iis:flint_pickaxe'], ['flint', 'table', 'stick'], ('Кремнёвая кирка', 'Flint pickaxe'),
     ('Первая настоящая кирка. Кремнёвые инструменты здесь как каменные в обычной игре, а каменные хуже.\n\nЖелезную кирку из слитков на верстаке не сделать: нужна головка, выкованная молотом. Это следующая глава.',
      'The first real pickaxe. Flint tools here are what stone ones are in the plain game, and stone ones are worse.\n\nAn iron pickaxe cannot be crafted from ingots: it needs a head forged with a hammer. That is the next chapter.')),
    ('flint_axe', 4, 0, ['iis:flint_axe'], ['flint', 'table', 'stick'], ('Кремнёвый топор', 'Flint axe'), ('Рубит дерево заметно быстрее рук.', 'Fells wood much faster than hands.')),
    ('flint_shovel', 4, 2, ['iis:flint_shovel'], ['flint', 'table', 'stick'], ('Кремнёвая лопата', 'Flint shovel'), ('Гравий, песок и глина пойдут быстрее.', 'Gravel, sand and clay go faster.')),
    ('flint_sword', 5, 0, ['iis:flint_sword'], ['flint', 'table', 'stick'], ('Кремнёвый меч', 'Flint sword'), ('Ночью пригодится.', 'Handy at night.')),
    ('cobble', 5, 1, ['minecraft:cobblestone*8'], ['flint_pickaxe'], ('Булыжник', 'Cobblestone'), ('Восемь штук — на печь.', 'Eight of them make a furnace.')),
    ('furnace', 6, 1, ['minecraft:furnace'], ['cobble'], ('Печь', 'Furnace'),
     ('Плавит руду и готовит еду.\n\nДревесный уголь из брёвен в печи не получается: его жгут в угольной яме (глава «Пар и сталь»). Пока топите досками и углём.',
      'Smelts ore and cooks food.\n\nLogs give no charcoal in a furnace: it is burnt in a charcoal pile (chapter "Steam & Steel"). Burn planks and coal for now.')),
    ('stone', 7, 0, ['minecraft:stone*5'], ['furnace'], ('Камень', 'Stone'), ('Обожгите булыжник. Камень нужен для ступки и каменных деталей.', 'Smelt cobblestone. Stone goes into the mortar and stone parts.')),
    ('food', 7, 1, [MEAT + '*2'], ['furnace'], ('Горячий ужин', 'A hot dinner'), ('Пожарьте любое мясо или рыбу.', 'Cook any meat or fish.')),
    ('home', 4, 3, ['minecraft:chest', '#minecraft:wooden_doors'], ['table'], ('Укрытие', 'Shelter'),
     ('Сундук и дверь. Любая коробка с дверью защитит от ночных гостей; челнок тоже подойдёт как ориентир.', 'A chest and a door. Any box with a door keeps the night out; the shuttle makes a good landmark too.')),
    ('wool', 3, 5, ['#minecraft:wool*3'], ['start'], ('Шерсть', 'Wool'), ('Три блока шерсти любого цвета — с овец.', 'Three blocks of wool of any colour, from sheep.')),
    ('bed', 4, 5, ['#minecraft:beds'], ['wool', 'table'], ('Кровать', 'Bed'), ('Пропускает ночь и запоминает место возрождения.', 'Skips the night and keeps your respawn point.')),
    ('treetap', 4, 4, ['iis:treetap'], ['table'], ('Краник', 'Treetap'), ('Нужен, чтобы собирать смолу с каучуковых деревьев.', 'Collects resin from rubber trees.')),
    ('resin', 5, 4, ['iis:sticky_resin'], ['treetap'], ('Смола', 'Sticky resin'),
     ('Найдите каучуковое дерево: у его ствола на одной стороне есть отверстие со смолой. ПКМ краником по полному отверстию даёт смолу, со временем оно наполняется снова. Поставленное вами бревно смолы не даёт.',
      'Find a rubber tree: its trunk has a resin hole on one side. Right click a full hole with the treetap for resin; it refills with time. A log you placed gives none.')),
    ('torch', 6, 4, ['minecraft:torch*4'], ['resin', 'stick'], ('Факел', 'Torch'), ('Первые факелы делаются из смолы и палки.', 'The first torches are made of resin and a stick.')),
    ('veins', 6, 2.5, [], ['flint_pickaxe'], ('Руда лежит жилами', 'Ore lies in veins'),
     ('Обычной руды в мире нет. Руда собрана в большие жилы: нашли одну — хватит надолго. Между жилами попадаются одиночные малые руды.\n\nГде искать в Верхнем мире (высоты ИИС 2, в старых квестах книги они другие):\n' + '\n'.join(_VR),
      'There is no plain ore in the world. Ore is gathered in large veins: find one and it lasts. Single small ores turn up between the veins.\n\nWhere to look in the Overworld (IIS 2 heights; older quests of the book give other ones):\n' + '\n'.join(_VE))),
    ('tin', 8, 2, ['iis:ingot_tin'], ['veins', 'furnace'], ('Олово', 'Tin'), ('Жила касситерита — самая частая и лежит почти на любой высоте. Руду плавит печь.', 'The cassiterite vein is the most common and lies at nearly any height. A furnace smelts the ore.')),
    ('copper', 8, 3, ['minecraft:copper_ingot'], ['veins', 'furnace'], ('Медь', 'Copper'), ('Тетраэдрит в горах или халькопирит глубоко под землёй.', 'Tetrahedrite in the mountains or chalcopyrite deep underground.')),
    ('iron', 8, 4, ['minecraft:iron_ingot'], ['veins', 'furnace'], ('Железо', 'Iron'), ('Магнетит выше уровня моря или лимониты в глубине.', 'Magnetite above sea level or limonites in the deep.')),
    ('coal', 8, 5, ['minecraft:coal*8'], ['veins'], ('Уголь', 'Coal'), ('Топливо для печи и будущих котлов.', 'Fuel for the furnace and the boilers to come.')),
    ('silver', 8, 1, [SILVER], ['veins'], ('Серебряная руда', 'Silver ore'), ('Серебро лежит в жиле галенита вместе со свинцом. Оно понадобится для солнечного котла.', 'Silver lies in the galena vein with lead. The solar boiler will need it.')),
    ('sand', 1, 6, ['minecraft:sand*4'], ['start'], ('Песок', 'Sand'), ('Для стекла.', 'For glass.')),
    ('glass', 6, 6, ['minecraft:glass'], ['sand', 'furnace'], ('Стекло', 'Glass'), ('Песок в печи.', 'Sand in a furnace.')),
    ('pane', 7, 6, ['minecraft:glass_pane'], ['glass'], ('Стеклянная панель', 'Glass pane'), ('Шесть блоков стекла на верстаке.', 'Six glass blocks on the table.')),
    ('clay', 1, 7, ['minecraft:clay_ball*4'], ['start'], ('Глина', 'Clay'), ('Глина лежит под водой в реках и на пляжах.', 'Clay lies under water in rivers and on beaches.')),
    ('brick', 6, 7, ['minecraft:brick'], ['clay', 'furnace'], ('Кирпич', 'Brick'), ('Комок глины в печи.', 'A clay ball in a furnace.')),
    ('bricks', 7, 7, ['minecraft:bricks*3'], ['brick'], ('Кирпичный блок', 'Bricks'), ('Кирпичи пойдут на первый паровой котёл и угольную яму.', 'Bricks go into the first steam boiler and the charcoal pile.')),
    ('slime', 1, 5, ['minecraft:slime_ball'], ['start'], ('Слизь', 'Slime'), ('Со слизней на болотах. Понадобится для обшивки водонапорной башни.', 'From slimes in swamps. The water tank siding will need it.')),
]

BRONZE = [
    ('tools', 0, 3, [], ['iron'], ('Инструменты GregTech', 'GregTech tools'),
     ('Дальше почти всё делается инструментом в сетке крафта: молот куёт пластины, напильник точит стержни, пила режет, ключ собирает корпуса. Инструмент не тратится, а изнашивается.\n\nСами инструменты — в главе «[О] Качество жизни»: начните с молота, напильника и пилы. Подойдёт инструмент из любого металла.',
      'From here on nearly everything is made with a tool in the crafting grid: a hammer forges plates, a file turns rods, a saw cuts, a wrench assembles hulls. A tool is not used up, it wears.\n\nThe tools themselves are in "[G] Quality of Life": start with a hammer, a file and a saw. A tool of any metal will do.')),
    ('mortar', 1, 0, ['#iis:tools/mortar'], ['stone', 'flint'], ('Ступка', 'Mortar'), ('Растирает слитки и камни в пыль прямо в сетке крафта.', 'Grinds ingots and stones into dust right in the crafting grid.')),
    ('dust_bronze', 2, 0, ['iis:dust_bronze'], ['mortar', 'copper', 'tin'], ('Бронзовая пыль', 'Bronze dust'),
     ('Разотрите слитки ступкой и смешайте три медные пыли с одной оловянной.', 'Grind the ingots with the mortar and mix three copper dusts with one of tin.')),
    ('ingot_bronze', 3, 0, ['iis:ingot_bronze'], ['dust_bronze'], ('Бронза', 'Bronze'), ('Пыль в печь. Бронза — металл всей паровой эпохи.', 'Dust into the furnace. Bronze is the metal of the whole steam age.')),
    ('plate_bronze', 4, 0, ['iis:plate_bronze'], ['@#iis:tools/hammer', 'ingot_bronze'], ('Бронзовая пластина', 'Bronze plate'), ('Слитки и молот в сетке крафта.', 'Ingots and a hammer in the crafting grid.')),
    ('hull_bronze', 5, 0, ['iis:hull_bronze'], ['plate_bronze'], ('Бронзовый корпус', 'Bronze hull'),
     ('Основа паровых машин. С ним открывается и примитивный шахтокопатель (глава «Пар и сталь»).', 'The base of steam machines. It also opens the primitive mine digger (chapter "Steam & Steel").')),
    ('rod_bronze', 4, 1, ['iis:rod_bronze'], ['ingot_bronze', '@#iis:tools/file'], ('Бронзовый стержень', 'Bronze rod'), ('Слиток и напильник.', 'An ingot and a file.')),
    ('pick2', 3, 1, [PICK2], ['@#iis:tools/hammer', '@#iis:tools/file'], ('Кирка получше', 'A better pickaxe'),
     ('Выкуйте железную или бронзовую головку кирки и соедините с палкой. Такая кирка берёт руду, которую кремнёвая не возьмёт.', 'Forge an iron or bronze pickaxe head and join it with a stick. It mines ore the flint one cannot.')),
    ('plate_iron', 1, 3, ['iis:plate_iron'], ['tools', '@#iis:tools/hammer'], ('Железная пластина', 'Iron plate'), ('Железные слитки и молот.', 'Iron ingots and a hammer.')),
    ('rod_iron', 1, 2, ['iis:rod_iron*3'], ['tools', '@#iis:tools/file'], ('Железный стержень', 'Iron rod'), ('Слиток и напильник. Позже стержни точит токарный станок.', 'An ingot and a file. A lathe turns rods later.')),
    ('wrench', 2, 2, ['#iis:tools/wrench'], ['@#iis:tools/hammer'], ('Гаечный ключ', 'Wrench'),
     ('Снимает машины GregTech, соединяет трубы и нужен почти в каждом рецепте корпуса.', 'Takes GregTech machines down, connects pipes and is in nearly every hull recipe.')),
    ('gear_iron', 3, 2, ['iis:gear_iron'], ['rod_iron', 'plate_iron', 'wrench'], ('Железная шестерня', 'Iron gear'), ('Стержни, пластины и ключ.', 'Rods, plates and a wrench.')),
    ('bucket', 2, 3, ['minecraft:bucket'], ['plate_iron'], ('Ведро', 'Bucket'), ('Три железные пластины и молот: из слитков ведро не делается.', 'Three iron plates and a hammer: ingots make no bucket.')),
    ('cauldron', 2, 4, ['minecraft:cauldron'], ['plate_iron'], ('Котёл', 'Cauldron'), ('Семь железных пластин и молот.', 'Seven iron plates and a hammer.')),
    ('redstone', 1, 5, ['minecraft:redstone'], ['veins'], ('Красная пыль', 'Redstone'), ('Жила красной пыли лежит глубоко: вместе с ней идут рубин и киноварь.', 'The redstone vein lies deep, with ruby and cinnabar.')),
    ('piston', 3, 4, ['minecraft:piston'], ['redstone', 'ingot_bronze'], ('Поршень', 'Piston'), ('Доски, булыжник, красная пыль и бронзовый слиток. Деталь первых паровых машин.', 'Planks, cobblestone, redstone and a bronze ingot. A part of the first steam machines.')),
    ('ingot_silver', 1, 6, ['iis:ingot_silver'], ['furnace', 'silver'], ('Серебряный слиток', 'Silver ingot'), ('Серебряная руда в печи.', 'Silver ore in a furnace.')),
    ('plate_silver', 2, 6, ['iis:plate_silver*2'], ['@#iis:tools/hammer', 'ingot_silver'], ('Серебряная пластина', 'Silver plate'), ('Для солнечного котла.', 'For the solar boiler.')),
    ('string', 4, 5, ['minecraft:string'], ['tools'], ('Нить', 'String'), ('С пауков или из шерсти.', 'From spiders or from wool.')),
    ('bow', 5, 5, ['minecraft:bow'], ['string'], ('Лук', 'Bow'), ('Бьёт издалека.', 'Strikes from afar.')),
    ('small_gear_wood', 4, 3, ['iis:small_gear_wood'], ['@#iis:tools/saw'], ('Малая деревянная шестерня', 'Small wooden gear'), ('Доска и пила.', 'A plank and a saw.')),
    ('well', 5, 4, ['iis:well'], ['string', 'small_gear_wood', 'bucket'], ('Колодец', 'Well'), ('Деревянные шестерни, палка, доски, нить, булыжник и ведро. Даёт воду без реки под боком: будущим котлам её нужно много.', 'Wooden gears, a stick, planks, string, cobblestone and a bucket. Gives water with no river nearby: the boilers to come need a lot.')),
    ('button', 6, 1, ['minecraft:stone_button'], ['stone'], ('Каменная кнопка', 'Stone button'), ('Из камня.', 'Of stone.')),
    ('small_gear_stone', 6, 2, ['iis:small_gear_stone'], ['stone', '@#iis:tools/file'], ('Каменная шестерня', 'Stone gear'), ('Гладкий камень (камень, обожжённый ещё раз) и напильник.', 'Smooth stone (stone smelted once more) and a file.')),
    ('knife', 6, 3, ['#iis:tools/knife'], ['@#iis:tools/file'], ('Нож', 'Knife'), ('Слабое оружие, зато режет бумажные кольца и многое другое.', 'A poor weapon, but it cuts paper rings and much else.')),
    ('wire_cutter', 6, 4, ['#iis:tools/wire_cutter'], ['@#iis:tools/screwdriver', '@#iis:tools/saw'], ('Кусачки', 'Wire cutter'), ('Режут пластины на провода, а фольгу на проволоку.', 'Cut plates into wires and foil into fine wire.')),
    ('slab', 6, 5, ['#minecraft:wooden_slabs'], ['@#iis:tools/saw'], ('Деревянная плита', 'Wooden slab'), ('Доски и пила.', 'Planks and a saw.')),
    ('pipe_wood', 6, 6, ['iis:pipe_medium_wood'], ['@#iis:tools/saw', '@#iis:tools/soft_hammer'], ('Деревянная труба', 'Wooden pipe'),
     ('Самый дешёвый водопровод. Пар по деревянным трубам не пускайте.', 'The cheapest water line. Keep steam out of wooden pipes.')),
    ('scoop', 5, 6, ['iis:scoop'], ['wool', 'table', 'stick'], ('Сачок', 'Scoop'), ('Шерсть, доска и палки.', 'Wool, a plank and sticks.')),
]

CHAPTERS = [('landing', 'first_steps', LANDING, ('Высадка', 'Landing'), 'iis:flint_pickaxe'),
            ('bronze', 'first_tools', BRONZE, ('Бронза и инструменты', 'Bronze and tools'), 'iis:ingot_bronze')]
# old task -> new quest, where the task itself changed
ALIAS = {'minecraft:white_wool': 'wool', 'item_tag(minecraft:beds)': 'bed', 'minecraft:oak_log': 'logs', 'minecraft:oak_planks': 'planks',
         'minecraft:wooden_pickaxe': 'flint_pickaxe', 'minecraft:stone_pickaxe': 'flint_pickaxe', 'minecraft:stone_shovel': 'flint_shovel', 'minecraft:wooden_shovel': 'flint_shovel',
         'minecraft:cooked_chicken': 'food', 'minecraft:oak_slab': 'slab', 'iis:crushed_redstone': 'redstone'}

KEYS = {}
n = 0
for _, _, table, _, _ in CHAPTERS:
    for q in table:
        assert q[0] not in KEYS, q[0]
        KEYS[q[0]] = BASE + n
        n += 1


def parse(task):
    """'id*N' -> (signature, count)."""
    count = 1
    if '*' in task:
        task, c = task.rsplit('*', 1)
        count = int(c)
    if task.startswith('#'):
        task = f'item_tag({task[1:]})'
    return task, count


def by_task():
    """Signature of the first task -> quest id, for the quests of the old chapters to find their successors."""
    out = {}
    for _, _, table, _, _ in CHAPTERS:
        for q in table:
            if q[3]:
                out.setdefault(parse(q[3][0])[0], KEYS[q[0]])
    for sig, key in ALIAS.items():
        out[sig] = KEYS[key]
    return out


def replaced_lines():
    return {old for _, old, _, _, _ in CHAPTERS}


def add_chapters(chapters_dir, tr, order, write, hexid, item_exists, filter_item, find_old):
    """order: old line name -> its place in the book; find_old: task signature -> id of a quest that stays in the book."""
    total, missing = 0, []
    for name, old, table, title, icon in CHAPTERS:
        out = []
        for key, col, row, tasks, deps, qtitle, text in table:
            qid = KEYS[key]
            qo = {'id': hexid(0x1A, qid), 'x': col * STEP_X, 'y': row * STEP_Y}
            dl = []
            for d in deps:
                if d.startswith('@'):
                    o = find_old(parse(d[1:])[0])
                    if o is None:
                        raise SystemExit(f'quests_start: no quest of the book asks for {d[1:]}')
                    dl.append(hexid(0x1A, o))
                else:
                    dl.append(hexid(0x1A, KEYS[d]))
            if dl:
                qo['dependencies'] = dl
            tl = []
            for i, t in enumerate(tasks):
                sig, count = parse(t)
                if '(' in sig:
                    item = filter_item(sig)
                else:
                    if sig.startswith(f'{NS}:') and not item_exists(sig):
                        missing.append(sig)
                    item = {'id': sig, 'count': 1}
                tl.append({'type': 'item', 'item': item, 'count': count, 'id': hexid(0x1B, (qid << 8) | i)})
            if tl:
                first = parse(tasks[0])[0]
                qo['icon'] = {'id': first if '(' not in first else ICONS.get(key, icon), 'count': 1}
                qo['tasks'] = tl
                qo['rewards'] = [{'type': 'item', 'item': {'id': f'{NS}:credit_copper', 'count': 1 if name == 'landing' else 2}, 'id': hexid(0x1C, qid << 8)}]
            else:
                qo['icon'] = {'id': ICONS.get(key, icon), 'count': 1}
                qo['size'] = 1.5
                qo['tasks'] = [{'type': 'checkmark', 'id': hexid(0x1B, qid << 8)}]
            tr['ru_ru'][f'quest.{qo["id"]}.title'], tr['en_us'][f'quest.{qo["id"]}.title'] = qtitle
            tr['ru_ru'][f'quest.{qo["id"]}.quest_desc'] = text[0].split('\n')
            tr['en_us'][f'quest.{qo["id"]}.quest_desc'] = text[1].split('\n')
            out.append(qo)
        idx = order[old]
        cid = hexid(0x1D, 0x1010 + (0 if name == 'landing' else 1))
        fname = f'{idx:02d}_{name}'
        write(os.path.join(chapters_dir, fname + '.json5'), {'id': cid, 'filename': fname, 'order_index': idx, 'default_quest_shape': '',
                                                             'icon': {'id': icon, 'count': 1}, 'quests': out})
        tr['ru_ru'][f'chapter.{cid}.title'], tr['en_us'][f'chapter.{cid}.title'] = title
        total += len(out)
    if missing:
        raise SystemExit(f'quests_start: no such items: {missing}')
    return total


ICONS = {'start': 'minecraft:campfire', 'paths': 'minecraft:compass', 'book': 'minecraft:book', 'veins': 'iis:ore_cassiterite', 'tools': 'iis:hammer_iron',
         'logs': 'minecraft:oak_log', 'planks': 'minecraft:oak_planks', 'wool': 'minecraft:white_wool', 'bed': 'minecraft:red_bed', 'food': 'minecraft:cooked_beef',
         'silver': 'iis:ore_silver', 'mortar': 'iis:mortar_flint', 'pick2': 'iis:pickaxe_head_bronze', 'wrench': 'iis:wrench_iron', 'knife': 'iis:knife_iron',
         'wire_cutter': 'iis:wire_cutter_iron', 'slab': 'minecraft:oak_slab', 'home': 'minecraft:chest'}
