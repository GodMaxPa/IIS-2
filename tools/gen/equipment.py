"""Weapons, armour and vehicles of the Cyber path by epoch (D128): models, translations, recipes. Numbers live in ru.iis.equipment.Weapon and VehicleKind.

IIS 1 had these only as Flan's Mod packs outside the tech tree, so recipes here are a proposal: each epoch asks for the parts of its own tier.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gear_art as art
import gun_models
import mesh_art
import misc_models
import rail_models
import vehicle_models

NS = 'iis'
I = lambda x: f'{NS}:{x}'

GUN_NAMES = {
    'flintlock_pistol': ('Flintlock Pistol', 'Кремнёвый пистолет'), 'musket': ('Musket', 'Мушкет'), 'rifled_musket': ('Rifled Musket', 'Штуцер'), 'blunderbuss': ('Blunderbuss', 'Мушкетон'),
    'pistol': ('Pistol', 'Пистолет'), 'smg': ('Submachine Gun', 'Пистолет-пулемёт'), 'shotgun': ('Pump Shotgun', 'Помповое ружьё'), 'assault_rifle': ('Assault Rifle', 'Автомат'),
    'grenade_launcher': ('Grenade Launcher', 'Гранатомёт'), 'sniper_rifle': ('Sniper Rifle', 'Снайперская винтовка'), 'machine_gun': ('Machine Gun', 'Пулемёт'), 'rocket_launcher': ('Rocket Launcher', 'Ручная ракетная пушка'),
    'laser_pistol': ('Laser Pistol', 'Лазерный пистолет'), 'laser_rifle': ('Laser Rifle', 'Лазерная винтовка'), 'laser_cannon': ('Laser Cannon', 'Лазерная пушка'),
    'plasma_rifle': ('Plasma Rifle', 'Плазменная винтовка'), 'plasma_cannon': ('Plasma Cannon', 'Плазменная пушка')}
AMMO_NAMES = {
    'powder_cartridge': ('Paper Cartridge', 'Бумажный патрон'), 'pistol_round': ('Pistol Round', 'Пистолетный патрон'), 'rifle_round': ('Rifle Round', 'Винтовочный патрон'), 'shotgun_shell': ('Shotgun Shell', 'Ружейный патрон'),
    'grenade_round': ('Launcher Grenade', 'Граната к гранатомёту'), 'rocket_round': ('Rocket', 'Ракета'), 'tank_shell': ('Tank Shell', 'Танковый снаряд')}
PART_NAMES = {
    'wheel_wood': ('Wooden Wheel', 'Деревянное колесо'), 'wheel_rubber': ('Rubber Wheel', 'Резиновое колесо'), 'engine_steam': ('Steam Engine', 'Паровая машина'), 'engine_lv': ('Engine (LV)', 'Двигатель (LV)'),
    'engine_mv': ('Engine (MV)', 'Двигатель (MV)'), 'engine_hv': ('Engine (HV)', 'Двигатель (HV)'), 'engine_ev': ('Engine (EV)', 'Двигатель (EV)'), 'jet_turbine': ('Jet Turbine', 'Реактивная турбина')}
VEHICLE_NAMES = {
    'steam_car': ('Steam Tractor', 'Паровой тягач'), 'quad': ('Quad Bike', 'Квадроцикл'), 'motorbike': ('Motorcycle', 'Мотоцикл'), 'car': ('Car', 'Автомобиль'), 'truck': ('Truck', 'Грузовик'),
    'boat': ('Motor Boat', 'Катер'), 'plane': ('Airplane', 'Самолёт'), 'helicopter': ('Helicopter', 'Вертолёт'), 'tank': ('Tank', 'Танк'), 'jet': ('Jet Fighter', 'Реактивный самолёт')}
SETS = {'musketeer': ('Musketeer', 'мушкетёра'), 'ballistic': ('Ballistic', 'бронежилета'), 'assault': ('Assault', 'штурмовика'), 'power_armor': ('Power Armor', 'силовой брони'), 'plasma_armor': ('Plasma Armor', 'плазменной брони')}

# --- armour pictures: letters as in gear_art --------------------------------------------------------------------------------------
P_MUSKETEER = {'K': (30, 20, 14), 'D': (38, 46, 84), 'B': (52, 64, 112), 'L': (78, 92, 146), 'H': (120, 134, 180), 'A': (210, 170, 70), 'G': (255, 225, 130), 'V': (150, 40, 34), 'S': (170, 112, 56), 'W': (230, 225, 205)}
P_BALLISTIC = {'K': (20, 22, 18), 'D': (52, 58, 40), 'B': (76, 84, 58), 'L': (104, 112, 78), 'H': (140, 146, 104), 'A': (44, 44, 40), 'G': (220, 190, 90), 'V': (30, 34, 24), 'S': (96, 98, 92), 'W': (200, 200, 190)}
P_ASSAULT = {'K': (20, 22, 26), 'D': (50, 54, 62), 'B': (84, 88, 98), 'L': (120, 124, 134), 'H': (170, 174, 184), 'A': (206, 130, 40), 'G': (255, 190, 80), 'V': (90, 60, 20), 'S': (60, 64, 70), 'W': (230, 230, 230)}
P_POWER = {'K': (16, 18, 24), 'D': (40, 44, 56), 'B': (178, 184, 196), 'L': (206, 212, 222), 'H': (240, 244, 250), 'A': (40, 190, 220), 'G': (140, 250, 255), 'V': (20, 90, 120), 'S': (90, 96, 108), 'W': (255, 255, 255)}
P_PLASMA = {'K': (12, 8, 22), 'D': (34, 22, 58), 'B': (58, 38, 96), 'L': (88, 62, 140), 'H': (140, 110, 200), 'A': (200, 164, 70), 'G': (240, 130, 255), 'V': (90, 20, 120), 'S': (80, 70, 110), 'W': (250, 240, 255)}

F = art.flat
MUSK = dict(
    head=dict(front=['KDDDDDDK', 'AAAAAAAA', '........', '........', '........', '........', '........', '........'], right=['KDDDDDDK', 'AAAAAAAA', 'KK......', 'K.......', '........', '........', '........', '........'],
              back=['KDDDDDDK', 'AAAAAAAA', 'KKKKKKKK', 'KKKKKKKK', 'KKKKKKKK', 'VVVVVVVV', 'KKKKKKKK', '........'], top=F(8, 8, 'D'), bottom=None),
    body=dict(front=['DBWBBWBD', 'LBWBBWBD', 'LBBWWBBD', 'LBBWWBBD', 'LBWBBWBD', 'BWBASBWD', 'BWBASBWD', 'DBBASBBK', 'KSSSSSSK', 'KVVAAVVK', 'DBBAABBK', 'DBBAABBK'],
              back=['DBBBBBBD', 'LBBWWBBD', 'LBBWWBBD', 'BBWBBWBD', 'BBWBBWBD', 'BWBBBBWD', 'BWBBBBWD', 'DBBBBBBK', 'KSSSSSSK', 'KVVVVVVK', 'DBBDDBBK', 'DBBDDBBK'],
              right=['LBBD', 'LBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBK', 'SSSS', 'VVVV', 'BBBK', 'BBBK'], top=F(8, 4, 'B'), bottom=F(8, 4, 'K')),
    arm=dict(front=['AAAA', 'LBBD', 'LBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'AAAA', 'VVVV', 'WWWW', 'WWWW'], side=['AAAA', 'LBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'AAAA', 'VVVV', 'WWWW', 'WWWW'], top=F(4, 4, 'A'), bottom=F(4, 4, 'K')),
    leg=dict(front=['WWWW', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD'], side=['WWWW', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD', 'WWWD'], top=F(4, 4, 'W'), bottom=F(4, 4, 'K')),
    waist=dict(front=['........'] * 7 + ['BBBBBBBB', 'WWWWWWWW', 'WWWWWWWW', 'WWWKKWWW', 'WWWKKWWW'], side=['....'] * 7 + ['BBBD', 'WWWW', 'WWWW', 'WWWD', 'WWWD'], back=['........'] * 7 + ['BBBBBBBB', 'WWWWWWWW', 'WWWWWWWW', 'WWWKKWWW', 'WWWKKWWW']),
    boot=dict(front=['....'] * 5 + ['AAAA', 'SSSS', 'KSSK', 'KSSK', 'KSSK', 'KSSK', 'KKKK'], side=['....'] * 5 + ['AAAA', 'SSSS', 'SSSK', 'SSSK', 'SSSK', 'SSSK', 'KKKK'], top=None, bottom=F(4, 4, 'K')))
BALL = dict(
    head=dict(front=['BLLLLLLB', 'LBBBBBBD', 'BBBBBBBD', 'KKKKKKKK', '.GVKKVG.', '........', '.A....A.', '........'], right=['BLLLLLLB', 'LBBBBBBD', 'BBBBBBBD', 'KKKKKKKK', 'BBBBBBBD', 'DBBBBBDK', 'A......A', '........'],
              back=['BLLLLLLD', 'LBBBBBBD', 'BBBBBBBD', 'KKKKKKKK', 'BBBBBBBD', 'BBBBBBBD', 'DBBBBBDK', 'KKKKKKKK'], top=['BLLHHLLB', 'LLBBBBLD', 'LBBBBBBD', 'HBBBBBBD', 'HBBBBBBD', 'LBBBBBBD', 'LBBBBBBD', 'BDDDDDDD'], bottom=None),
    body=dict(front=['DAAAAAAD', 'LBBAABBD', 'LBBBBBBD', 'BLLBBLLD', 'BBBBBBBD', 'BBBBBBBD', 'BBBBBBBD', 'DBBBBBBK', 'KAGAAGAK', 'DBBBBBBK', 'KAAAAAAK', 'DDDDDDDD'],
              back=['DAAAAAAD', 'LBBBBBBD', 'LBBBBBBD', 'BBBBBBBD', 'BBBBBBBD', 'BBBBBBBD', 'BBBBBBBD', 'DBBBBBBK', 'KAAAAAAK', 'DBBBBBBK', 'KAAAAAAK', 'DDDDDDDD'],
              right=['LBBD', 'LBBD', 'BBBD', 'AAAA', 'BBBD', 'BBBD', 'AAAA', 'BBBK', 'KAAK', 'BBBK', 'KAAK', 'DDDD'], top=F(8, 4, 'L'), bottom=F(8, 4, 'K')),
    arm=dict(front=['LLLB', 'LBBD', 'BBBD', 'SSSS', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'KKKK', 'BBBD', 'AAAA', 'AAAA'], side=['LLLB', 'LBBD', 'BBBD', 'SSSS', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'KKKK', 'BBBD', 'AAAA', 'AAAA'], top=F(4, 4, 'L'), bottom=F(4, 4, 'K')),
    leg=dict(front=['BBBD', 'BBBD', 'BSSD', 'BBBD', 'KKKK', 'AAAA', 'AAAK', 'KKKK', 'BBBD', 'BBBD', 'BBBD', 'DDDK'], side=['BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'DDDK'], top=F(4, 4, 'B'), bottom=F(4, 4, 'K')),
    waist=dict(front=['........'] * 7 + ['BBBBBBBB', 'KAGAAGAK', 'BBBBBBBD', 'BBBDDBBK', 'BBBDDBBK'], side=['....'] * 7 + ['BBBD', 'KAAK', 'BBBD', 'BBBD', 'BBBK'], back=['........'] * 7 + ['BBBBBBBB', 'KAAAAAAK', 'BBBBBBBD', 'BBBDDBBK', 'BBBDDBBK']),
    boot=dict(front=['....'] * 7 + ['KKKK', 'AAAA', 'AGAA', 'AAAA', 'KKKK'], side=['....'] * 7 + ['KKKK', 'AAAA', 'AAAA', 'AAAA', 'KKKK'], top=None, bottom=F(4, 4, 'K')))
ASSAULT = dict(
    head=dict(front=['ALLLLLLA', 'LBBBBBBD', 'BKKKKKKD', 'KGGGGGGK', 'KGVVVVGK', 'BKKKKKKD', 'DBSSSSBK', 'KDSSSSDK'], right=['ALLLLLLB', 'LBBBBBBD', 'KKKBBBBD', 'GGKBSSBD', 'KKKBSSBD', 'BBBBBBBD', 'DBBBBBDK', 'KDDDDDKK'],
              back=['BLLLLLLD', 'LBBBBBBD', 'BBSSSSBD', 'BBSAASBD', 'BBSSSSBD', 'BBBBBBBD', 'DBBBBBDK', 'KDDDDDDK'], top=['BLLAALLB', 'LBBAABBD', 'LBBAABBD', 'HBBAABBD', 'HBBAABBD', 'LBBAABBD', 'LBBAABBD', 'BDDAADDD'], bottom=F(8, 8, 'K')),
    body=dict(front=['ALLBBLLA', 'LBBBBBBD', 'LBKKKKBD', 'BBKAAKBD', 'BBKAAKBD', 'BBKKKKBD', 'DBBBBBBK', 'KSSSSSSK', 'LBKBBKBD', 'BBKBBKBD', 'KAAAAAAK', 'DSSSSSSD'],
              back=['ALLBBLLA', 'LBSSSSBD', 'BBSKKSBD', 'BBSKKSBD', 'BBSKKSBD', 'BBSSSSBD', 'DBBBBBBK', 'KSSSSSSK', 'BBBBBBBD', 'BBBBBBBD', 'KAAAAAAK', 'DSSSSSSD'],
              right=['ALBD', 'LBBD', 'BBBK', 'BSSK', 'BSSK', 'BBBK', 'BBBK', 'KSSK', 'BBBK', 'BBBK', 'KAAK', 'DSSD'], top=F(8, 4, 'L'), bottom=F(8, 4, 'K')),
    arm=dict(front=['ALLA', 'LBBD', 'BBBD', 'KKKK', 'SSSS', 'LBBD', 'BBBD', 'BBBD', 'KKKK', 'LLBD', 'SSSS', 'KKKK'], side=['ALLA', 'LBBD', 'BBBD', 'KKKK', 'SSSS', 'BBBD', 'BBBD', 'BBBD', 'KKKK', 'BBBD', 'SSSS', 'KKKK'], top=F(4, 4, 'L'), bottom=F(4, 4, 'K')),
    leg=dict(front=['BLBD', 'BBBD', 'DBBK', 'KKKK', 'LHBD', 'LAAD', 'LBBD', 'KKKK', 'SSSS', 'SSSS', 'BBBD', 'DDDK'], side=['BBBD', 'BBBD', 'DBBK', 'KKKK', 'BBBD', 'BBBD', 'BBBD', 'KKKK', 'SSSS', 'BBBD', 'BBBD', 'DDDK'], top=F(4, 4, 'B'), bottom=F(4, 4, 'K')),
    waist=dict(front=['........'] * 7 + ['BBBBBBBB', 'LBKBBKBD', 'BBKBBKBD', 'KAAAAAAK', 'DSSSSSSD'], side=['....'] * 7 + ['BBBD', 'BBBD', 'BBDK', 'KAAK', 'DSSK'], back=['........'] * 7 + ['BBBBBBBB', 'BBBBBBBD', 'BBBBBBBD', 'KAAAAAAK', 'DSSSSSSD']),
    boot=dict(front=['....'] * 6 + ['ALLA', 'LBBD', 'SSSS', 'BBBD', 'KKKK', 'SSSK'], side=['....'] * 6 + ['ALLB', 'LBBD', 'SSSS', 'BBBD', 'KKKK', 'SSSK'], top=None, bottom=F(4, 4, 'K')))
POWER = dict(
    head=dict(front=['HHHHHHHH', 'HLLLLLLB', 'LKKKKKKB', 'KGGGGGGK', 'KVGGGGVK', 'LKKKKKKB', 'BLSSSSLD', 'DBSSSSBD'], right=['HHHHHHHB', 'LLLLLLLB', 'KKKLLLLB', 'GGKLAALB', 'KKKLAALB', 'LLLLLLBD', 'BLLLLBBD', 'DBBBBBDD'],
              back=['HHHHHHHB', 'LLLLLLLB', 'LLDDDDLB', 'LLDGGDLB', 'LLDDDDLB', 'LLLLLLBD', 'BBLLLBBD', 'DDBBBDDD'], top=['HHHAAHHH', 'HLLAALLB', 'HLLAALLB', 'HLLAALLB', 'LLLAALLB', 'LLLAALLB', 'LLLAALLB', 'BBBAABBD'], bottom=F(8, 8, 'K')),
    body=dict(front=['HHLLLLHB', 'HLLLLLLB', 'LLKKKKLB', 'LLKGGKLB', 'LAKGGKAB', 'LLKKKKLB', 'BLLAALBD', 'DKKKKKKD', 'LBBBBBBB', 'BBBBBBBD', 'KAAAAAAK', 'DKKKKKKD'],
              back=['HHLLLLHB', 'HLLLLLLB', 'LLDDDDLB', 'LLDDDDLB', 'LLDDDDLB', 'LLDDDDLB', 'BLLLLLBD', 'DKKKKKKD', 'LBBBBBBB', 'BBBBBBBD', 'KAAAAAAK', 'DKKKKKKD'],
              right=['HLLB', 'LLLB', 'LLBD', 'LABD', 'LABD', 'LLBD', 'LBBD', 'KKKK', 'LBBD', 'BBBD', 'KAAK', 'KKKK'], top=F(8, 4, 'H'), bottom=F(8, 4, 'K')),
    arm=dict(front=['HHHB', 'HLLB', 'LLBD', 'KKKK', 'LLBD', 'LABD', 'LABD', 'LLBD', 'KKKK', 'LLBD', 'DGGD', 'KKKK'], side=['HHHB', 'HLLB', 'LLBD', 'KKKK', 'LLBD', 'LLBD', 'LLBD', 'LLBD', 'KKKK', 'LLBD', 'DAAD', 'KKKK'], top=F(4, 4, 'H'), bottom=F(4, 4, 'K')),
    leg=dict(front=['LLBD', 'LABD', 'LLBD', 'KKKK', 'HLLB', 'HGGB', 'LLLB', 'KKKK', 'LLBD', 'LABD', 'LLBD', 'BBDD'], side=['LLBD', 'LLBD', 'LLBD', 'KKKK', 'LLLB', 'LLLB', 'LLLB', 'KKKK', 'LLBD', 'LLBD', 'LLBD', 'BBDD'], top=F(4, 4, 'L'), bottom=F(4, 4, 'K')),
    waist=dict(front=['........'] * 7 + ['LLLLLLLL', 'LBBBBBBB', 'BBBBBBBD', 'KAAAAAAK', 'DKKKKKKD'], side=['....'] * 7 + ['LLLB', 'LBBD', 'BBBD', 'KAAK', 'KKKK'], back=['........'] * 7 + ['LLLLLLLL', 'LBBBBBBB', 'BBBBBBBD', 'KAAAAAAK', 'DKKKKKKD']),
    boot=dict(front=['....'] * 6 + ['HHHB', 'HLLB', 'LGGB', 'LLBD', 'KKKK', 'DDDD'], side=['....'] * 6 + ['HHLB', 'LLLB', 'LLBD', 'LLBD', 'KKKK', 'DDDD'], top=None, bottom=F(4, 4, 'K')))
PLASMA = dict(
    head=dict(front=['AHHHHHHA', 'LLBBBBLD', 'BKKKKKKD', 'KGGWWGGK', 'KVGGGGVK', 'BKKKKKKD', 'DBAGGABK', 'KDDAADDK'], right=['AHHLLLLB', 'LLBBBBBD', 'KKKBBABD', 'GGKBGABD', 'KKKBBABD', 'DBBBBBDK', 'DBBDDDDK', 'KDDDDDKK'],
              back=['BHHHHHHD', 'LLBAABBD', 'BBDGGDBD', 'BBDGGDBD', 'BBDAADBD', 'DBBBBBBK', 'DDBBBBDK', 'KDDAADDK'], top=['BAHHHHAB', 'LBBAABBD', 'LBDGGDBD', 'HADGGDAD', 'HADGGDAD', 'LBDGGDBD', 'LBBAABBD', 'BDDAADDD'], bottom=F(8, 8, 'K')),
    body=dict(front=['AHLBBLHA', 'LBBAABBD', 'BBKKKKBD', 'BAKGGKAD', 'AGKWWKGA', 'BAKGGKAD', 'BBKKKKBD', 'KBBAABBK', 'LBGBBGBD', 'BBGBBGBD', 'KAAAAAAK', 'DBBAABBD'],
              back=['AHLBBLHA', 'LBKKKKBD', 'BBKGGKBD', 'BBKGGKBD', 'BBKGGKBD', 'BBKKKKBD', 'LBBBBBBD', 'KBBAABBK', 'BBBBBBBD', 'BBBBBBBD', 'KAAAAAAK', 'DBBAABBD'],
              right=['ALBD', 'LBBD', 'BBDK', 'BGDK', 'BGDK', 'BBDK', 'BBDK', 'BADK', 'BBDK', 'BBDK', 'KAAK', 'DBDK'], top=F(8, 4, 'L'), bottom=F(8, 4, 'K')),
    arm=dict(front=['AHHA', 'HLLD', 'LBBD', 'KAAK', 'LBBD', 'LGBD', 'BGBD', 'BGBD', 'KAAK', 'DBBD', 'DGGK', 'KDDK'], side=['AHHA', 'HLLD', 'LBBD', 'KAAK', 'LBBD', 'LBBD', 'BBBD', 'BBBD', 'KAAK', 'DBBD', 'DBBK', 'KDDK'], top=['AHHA', 'HLLB', 'HLBD', 'ABDA'], bottom=F(4, 4, 'K')),
    leg=dict(front=['BLBD', 'BGBD', 'DGBK', 'KAAK', 'LHBD', 'LGGD', 'LBBD', 'KAAK', 'BBBD', 'BGBD', 'BGBD', 'DDDK'], side=['BBBD', 'BBBD', 'DBBK', 'KAAK', 'BBBD', 'BLBD', 'BLBD', 'KAAK', 'BBBD', 'BBBD', 'BBBD', 'DDDK'], top=F(4, 4, 'B'), bottom=F(4, 4, 'K')),
    waist=dict(front=['........'] * 7 + ['BBBBBBBB', 'LBGBBGBD', 'BBGBBGBD', 'KAAAAAAK', 'DBBAABBD'], side=['....'] * 7 + ['BBBD', 'BBBD', 'BBDK', 'KAAK', 'DBDK'], back=['........'] * 7 + ['BBBBBBBB', 'BBBBBBBD', 'BBBBBBBD', 'KAAAAAAK', 'DBBAABBD']),
    boot=dict(front=['....'] * 7 + ['AHHA', 'LGGD', 'BBBD', 'KAAK', 'DDDK'], side=['....'] * 7 + ['AHLB', 'LBBD', 'BBDD', 'KAAK', 'DDDK'], top=None, bottom=F(4, 4, 'K')))


def suit(s, pal, seed):
    # конец рукава виден спереди, когда руки подняты с оружием: не чёрный
    return art.suit_textures(dict(s['head']), s['body'], dict(s['arm'], bottom=F(4, 4, 'D')), s['leg'], s['waist'], s['boot'], pal, seed)


def base_model(textures_particle, display):
    return {'gui_light': 'front', 'textures': {'particle': textures_particle}, 'display': display}


def gun_display(mesh):
    """Where a gun sits in the hand: the grip is the origin; the slot shows it from the side, whole."""
    b = mesh.bounds()
    length = b[5] - b[2]
    height = b[4] - b[1]
    fit = 14.5 / max(length, height, 1)
    hand = 0.92 if length < 20 else 0.72 if length < 36 else 0.62
    cz, cy = (b[2] + b[5]) / 2, (b[1] + b[4]) / 2
    return {
        'gui': {'rotation': [12, 90, 0], 'translation': [round(-cz * fit, 2), round(-cy * fit, 2), 0], 'scale': [round(fit, 3)] * 3},
        'fixed': {'rotation': [0, 90, 0], 'translation': [round(-cz * fit, 2), round(-cy * fit, 2), 0], 'scale': [round(fit, 3)] * 3},
        'ground': {'rotation': [0, 90, 0], 'translation': [0, 2, 0], 'scale': [0.3, 0.3, 0.3]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 8, 4], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [hand] * 3},
        'firstperson_lefthand': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [hand] * 3},
        'thirdperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, 1.5, 1], 'scale': [round(hand * 0.95, 3)] * 3},
        'thirdperson_lefthand': {'rotation': [0, 180, 0], 'translation': [0, 1.5, 1], 'scale': [round(hand * 0.95, 3)] * 3}}


def thing_display(mesh, gui_rot=(25, 215, 0), hand=None, margin=15.5):
    """Vehicles and small parts: a slanted view in the slot."""
    b = mesh.bounds()
    lx, ly, lz = b[3] - b[0], b[4] - b[1], b[5] - b[2]
    fit = margin / max((lx ** 2 + lz ** 2) ** 0.5 * 0.86, ly * 1.1, 1)
    c = ((b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2)
    import math
    ry = math.radians(gui_rot[1])
    # centre of the model after the turn, so that it stands in the middle of the slot
    tx = c[0] * math.cos(ry) + c[2] * math.sin(ry)
    h = hand if hand is not None else min(0.9, 10.0 / max(lx, lz, 1))
    return {
        'gui': {'rotation': list(gui_rot), 'translation': [round(-tx * fit, 2), round(-c[1] * fit * 0.9, 2), 0], 'scale': [round(fit, 3)] * 3},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, round(-c[1] * fit, 2), 0], 'scale': [round(fit, 3)] * 3},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [round(h * 0.5, 3)] * 3},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 8, 0], 'scale': [round(h * 0.6, 3)] * 3},
        'firstperson_righthand': {'rotation': [10, 200, 0], 'translation': [0, 3, 0], 'scale': [round(h * 0.7, 3)] * 3},
        'firstperson_lefthand': {'rotation': [10, 160, 0], 'translation': [0, 3, 0], 'scale': [round(h * 0.7, 3)] * 3},
        'thirdperson_righthand': {'rotation': [75, 180, 0], 'translation': [0, 2, 1], 'scale': [round(h * 0.6, 3)] * 3},
        'thirdperson_lefthand': {'rotation': [75, 180, 0], 'translation': [0, 2, 1], 'scale': [round(h * 0.6, 3)] * 3}}


def generate(g):
    write, A, D, lang, shaped, shapeless, tag_of = g['write'], g['A'], g['D'], g['lang'], g['shaped'], g['shapeless'], g['tag_of']
    rec = {}

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, circuit=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': dur, 'eut': eut}
        if fin:
            r['fluid_inputs'] = [fin]
        if circuit is not None:
            r['circuit'] = circuit
        rec[f'{rtype}/{rid}'] = r

    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1: {'id': i, 'count': c}
    P = lambda m: tag_of('plate', m)
    R = lambda m: tag_of('rod', m)
    SC = lambda m: tag_of('screw', m)
    BO = lambda m: tag_of('bolt', m)
    RI = lambda m: tag_of('ring', m)
    SG = lambda m: tag_of('small_gear', m)
    C = '#c:circuits/'

    # --- models ---------------------------------------------------------------------------------------------------------------------
    os.makedirs(os.path.join(A, 'meshes'), exist_ok=True)
    meshes = {}
    for mod in (vehicle_models, rail_models, gun_models, misc_models):
        for f in mod.ALL:
            m = f()
            meshes[m.name] = m
            m.write(os.path.join(A, 'meshes', m.name + '.json'))
    mesh_art.atlas().save(tex('entity/vehicle/atlas.png'))

    def mesh_item(iid, mesh, display):
        write(os.path.join(A, 'models', 'item', 'mesh', iid + '.json'), base_model('minecraft:block/iron_block', display))
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:special', 'base': f'{NS}:item/mesh/{iid}', 'model': {'type': f'{NS}:mesh', 'mesh': mesh}}, 'hand_animation_on_swap': False})

    for iid, (en, ru) in GUN_NAMES.items():
        mesh_item(iid, iid, gun_display(meshes[iid]))
        name(f'item.{NS}.{iid}', en, ru)
    for iid, (en, ru) in AMMO_NAMES.items():
        mesh_item(iid, iid, thing_display(meshes[iid], (30, 225, 0), 0.6, 12))
        name(f'item.{NS}.{iid}', en, ru)
    for iid, (en, ru) in PART_NAMES.items():
        mesh_item(iid, iid, thing_display(meshes[iid], (30, 225, 0), 0.6, 14))
        name(f'item.{NS}.{iid}', en, ru)
    for vid, (en, ru) in VEHICLE_NAMES.items():
        mesh_item(f'vehicle_{vid}', vid, thing_display(meshes[vid]))
        name(f'item.{NS}.vehicle_{vid}', en, ru)
        name(f'entity.{NS}.{vid}', en, ru)
    name(f'entity.{NS}.shot', 'Shell', 'Снаряд')

    # --- armour ---------------------------------------------------------------------------------------------------------------------
    def asset(a, main, legs):
        main.save(tex(f'entity/equipment/humanoid/{a}.png'))
        legs.save(tex(f'entity/equipment/humanoid_leggings/{a}.png'))
        write(os.path.join(A, 'equipment', f'{a}.json'), {'layers': {'humanoid': [{'texture': I(a)}], 'humanoid_leggings': [{'texture': I(a)}]}})

    for a, s, pal, seed in (('musketeer', MUSK, P_MUSKETEER, 1100), ('ballistic', BALL, P_BALLISTIC, 1200), ('assault', ASSAULT, P_ASSAULT, 1300), ('power_armor', POWER, P_POWER, 1400), ('plasma_armor', PLASMA, P_PLASMA, 1500)):
        asset(a, *suit(s, pal, seed))
        for p, en, pic, ru in (('helmet', 'Helmet', art.I_HELMET, 'Шлем'), ('chestplate', 'Chestplate', art.I_CHEST, 'Кираса'), ('leggings', 'Leggings', art.I_LEGS, 'Поножи'), ('boots', 'Boots', art.I_BOOTS, 'Ботинки')):
            iid = f'{a}_{p}'
            art.icon(pic, pal).save(tex(f'item/{iid}.png'))
            write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
            write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
            name(f'item.{NS}.{iid}', f'{SETS[a][0]} {en}', f'{ru} {SETS[a][1]}')

    # --- damage types --------------------------------------------------------------------------------------------------------------
    for d, en, ru in (('bullet', '%1$s was shot by %2$s', '%1$s застрелен игроком %2$s'), ('laser', '%1$s was burned through by %2$s', '%1$s прожжён лазером игрока %2$s'),
                      ('plasma', '%1$s was vaporised by %2$s', '%1$s испарён плазмой игрока %2$s'), ('shell_blast', '%1$s was blown up by %2$s', '%1$s разорван снарядом игрока %2$s')):
        write(os.path.join(D, NS, 'damage_type', d + '.json'), {'exhaustion': 0.0, 'message_id': f'{NS}.{d}', 'scaling': 'never'})
        name(f'death.attack.{NS}.{d}', en.replace('%2$s', 'someone').replace(' by someone', ''), ru.replace(' игроком %2$s', '').replace(' игрока %2$s', ''))
        name(f'death.attack.{NS}.{d}.player', en, ru)

    # --- ammunition -----------------------------------------------------------------------------------------------------------------
    shapeless('equipment/powder_cartridge', I('powder_cartridge'), 4, ['minecraft:paper', 'minecraft:gunpowder', BO('Lead')])
    mrec('assembler', 'pistol_round', [ing(P('Brass')), ing('minecraft:gunpowder', 2), ing(BO('Lead'), 4)], [out(I('pistol_round'), 16)], 60, 16)
    mrec('assembler', 'rifle_round', [ing(P('Brass'), 2), ing('minecraft:gunpowder', 4), ing(BO('Steel'), 4)], [out(I('rifle_round'), 16)], 80, 30)
    mrec('assembler', 'shotgun_shell', [ing(P('Brass')), ing(P('Rubber')), ing('minecraft:gunpowder', 2), ing(BO('Lead'), 8)], [out(I('shotgun_shell'), 8)], 80, 30)
    mrec('assembler', 'grenade_round', [ing(P('Steel'), 2), ing(P('Brass')), ing('minecraft:tnt')], [out(I('grenade_round'), 4)], 100, 30)
    mrec('assembler', 'rocket_round', [ing(P('Aluminium'), 4), ing(R('Steel')), ing('minecraft:tnt', 2), ing('minecraft:gunpowder', 4)], [out(I('rocket_round')), ], 200, 120)
    mrec('assembler', 'tank_shell', [ing(P('Steel'), 4), ing(P('Brass'), 2), ing('minecraft:tnt', 2)], [out(I('tank_shell'), 2)], 200, 120)

    # --- guns --------------------------------------------------------------------------------------------------------------------------
    WOOD = '#minecraft:planks'
    FLINT = 'minecraft:flint'
    shaped('equipment/flintlock_pistol', I('flintlock_pistol'), 1, ['FRR', 'dSP', 'W h'], {'F': FLINT, 'R': R('WroughtIron'), 'S': SC('WroughtIron'), 'P': P('WroughtIron'), 'W': WOOD})
    shaped('equipment/musket', I('musket'), 1, ['FRR', 'SPR', 'WWh'], {'F': FLINT, 'R': R('WroughtIron'), 'S': SC('WroughtIron'), 'P': P('WroughtIron'), 'W': WOOD})
    shaped('equipment/rifled_musket', I('rifled_musket'), 1, ['FRR', 'SPR', 'WWf'], {'F': FLINT, 'R': R('Steel'), 'S': SC('Steel'), 'P': P('Steel'), 'W': WOOD})
    shaped('equipment/blunderbuss', I('blunderbuss'), 1, ['FBB', 'SPB', 'WWh'], {'F': FLINT, 'B': P('Brass'), 'S': SC('Steel'), 'P': P('Steel'), 'W': WOOD})

    shaped('equipment/pistol', I('pistol'), 1, ['PPR', 'dCG', 'W h'], {'P': P('Aluminium'), 'R': R('Steel'), 'C': C + 'good', 'G': SG('Steel'), 'W': P('Rubber')})
    shaped('equipment/smg', I('smg'), 1, ['PPR', 'MCG', 'WPh'], {'P': P('Aluminium'), 'R': R('Steel'), 'C': C + 'good', 'G': SG('Steel'), 'M': I('electric_piston_lv'), 'W': P('Rubber')})
    shaped('equipment/shotgun', I('shotgun'), 1, ['PRR', 'WCG', 'Wdh'], {'P': P('StainlessSteel'), 'R': R('StainlessSteel'), 'C': C + 'advanced', 'G': SG('StainlessSteel'), 'W': WOOD})
    shaped('equipment/assault_rifle', I('assault_rifle'), 1, ['PPR', 'MCG', 'WPh'], {'P': P('StainlessSteel'), 'R': R('StainlessSteel'), 'C': C + 'advanced', 'G': SG('StainlessSteel'), 'M': I('electric_piston_mv'), 'W': P('Plastic')})
    shaped('equipment/grenade_launcher', I('grenade_launcher'), 1, ['PRR', 'MCP', 'W h'], {'P': P('StainlessSteel'), 'R': RI('StainlessSteel'), 'C': C + 'advanced', 'M': I('electric_piston_mv'), 'W': P('Plastic')})
    shaped('equipment/sniper_rifle', I('sniper_rifle'), 1, ['SRR', 'MCP', 'WPh'], {'S': I('sensor_ev'), 'R': R('Titanium'), 'C': C + 'data', 'M': I('electric_piston_hv'), 'P': P('Titanium'), 'W': P('Plastic')})
    shaped('equipment/machine_gun', I('machine_gun'), 1, ['PRR', 'MCK', 'WPh'], {'P': P('Titanium'), 'R': R('Titanium'), 'C': C + 'data', 'M': I('electric_motor_ev'), 'K': I('conveyor_module_hv'), 'W': P('Plastic')})
    shaped('equipment/rocket_launcher', I('rocket_launcher'), 1, ['PPP', 'SCM', 'PPP'], {'P': P('Titanium'), 'S': I('sensor_hv'), 'C': C + 'data', 'M': I('electric_piston_ev')})
    shaped('equipment/laser_pistol', I('laser_pistol'), 1, ['PEG', 'BCd', 'W h'], {'P': P('Titanium'), 'E': I('emitter_ev'), 'G': tag_of('lens', 'Ruby'), 'B': I('energy_crystal'), 'C': C + 'data', 'W': P('Plastic')})
    shaped('equipment/laser_rifle', I('laser_rifle'), 1, ['PEE', 'BCG', 'WPh'], {'P': P('TungstenSteel'), 'E': I('emitter_iv'), 'G': tag_of('lens', 'Olivine'), 'B': I('lapotron_crystal'), 'C': C + 'elite', 'W': P('Plastic')})
    shaped('equipment/laser_cannon', I('laser_cannon'), 1, ['PEE', 'BCE', 'PPh'], {'P': P('HSSE'), 'E': I('emitter_luv'), 'B': I('energy_orb'), 'C': C + 'master'})
    shaped('equipment/plasma_rifle', I('plasma_rifle'), 1, ['PFE', 'BCE', 'PPh'], {'P': P('NaquadahAlloy'), 'F': I('field_generator_zpm'), 'E': I('emitter_zpm'), 'B': I('energy_orb_cluster'), 'C': C + 'ultimate'})
    shaped('equipment/plasma_cannon', I('plasma_cannon'), 1, ['PFF', 'BCE', 'PPh'], {'P': P('Neutronium'), 'F': I('field_generator_uv'), 'E': I('emitter_uv'), 'B': I('energy_orb_cluster'), 'C': C + 'superconductor'})

    # --- armour recipes --------------------------------------------------------------------------------------------------------------
    def armor4(a, key, rows=(['MPM', 'M M'], ['P P', 'MPM', 'MMM'], ['MPM', 'M M', 'M M'], ['M M', 'P P'])):
        for p, pat in zip(('helmet', 'chestplate', 'leggings', 'boots'), rows):
            shaped(f'equipment/{a}_{p}', I(f'{a}_{p}'), 1, pat, key)

    armor4('musketeer', {'M': 'minecraft:leather', 'P': P('Bronze')})
    armor4('ballistic', {'M': P('Aluminium'), 'P': P('Rubber')})
    armor4('assault', {'M': P('Titanium'), 'P': I('carbon_plate')})
    PW = lambda n: ing(P('TungstenSteel'), n)
    PZ = lambda n: ing(P('NaquadahAlloy'), n)
    for i, (p, n, part) in enumerate((('helmet', 5, I('sensor_iv')), ('chestplate', 8, I('field_generator_iv')), ('leggings', 7, I('electric_motor_iv')), ('boots', 4, I('electric_piston_iv')))):
        mrec('assembler', f'power_armor_{p}', [PW(n), ing(I('energy_orb')), ing(part), ing(C + 'elite', 2)], [out(I(f'power_armor_{p}'))], 600, 1920, circuit=i)
    for i, (p, n, part) in enumerate((('helmet', 5, I('sensor_zpm')), ('chestplate', 8, I('field_generator_zpm')), ('leggings', 7, I('electric_motor_zpm')), ('boots', 4, I('electric_piston_zpm')))):
        mrec('assembler', f'plasma_armor_{p}', [PZ(n), ing(I('energy_orb_cluster')), ing(part), ing(C + 'ultimate', 2)], [out(I(f'plasma_armor_{p}'))], 1200, 30720, circuit=i)

    # --- vehicle parts and vehicles --------------------------------------------------------------------------------------------------
    shaped('equipment/wheel_wood', I('wheel_wood'), 1, ['WRW', 'RGR', 'WRW'], {'W': WOOD, 'R': R('WroughtIron'), 'G': RI('Bronze')})
    shaped('equipment/wheel_rubber', I('wheel_rubber'), 1, ['RRR', 'RGR', 'RRR'], {'R': P('Rubber'), 'G': RI('Steel')})
    shaped('equipment/engine_steam', I('engine_steam'), 1, ['PGP', 'RFR', 'PGP'], {'P': P('Bronze'), 'G': tag_of('gear', 'Bronze'), 'R': R('Bronze'), 'F': 'minecraft:furnace'})

    def engine(eid, plate, piston, motor, circuit):
        shaped(f'equipment/{eid}', I(eid), 1, ['PMP', 'NCN', 'PWP'], {'P': P(plate), 'M': I(motor), 'N': I(piston), 'C': C + circuit, 'W': tag_of('gear', plate)})

    engine('engine_lv', 'Steel', 'electric_piston_lv', 'electric_motor_lv', 'basic')
    engine('engine_mv', 'Aluminium', 'electric_piston_mv', 'electric_motor_mv', 'good')
    engine('engine_hv', 'StainlessSteel', 'electric_piston_hv', 'electric_motor_hv', 'advanced')
    engine('engine_ev', 'Titanium', 'electric_piston_ev', 'electric_motor_ev', 'data')
    shaped('equipment/jet_turbine', I('jet_turbine'), 1, ['PMP', 'FCF', 'PMP'], {'P': P('TungstenSteel'), 'M': I('electric_motor_iv'), 'F': I('electric_pump_iv'), 'C': C + 'elite'})

    V = lambda v: I(f'vehicle_{v}')
    W, RW = I('wheel_wood'), I('wheel_rubber')
    GL = '#c:glass_panes'
    shaped('equipment/vehicle_steam_car', V('steam_car'), 1, ['BPB', 'WEW', 'WPW'], {'B': WOOD, 'P': P('Bronze'), 'W': W, 'E': I('engine_steam')})
    shaped('equipment/vehicle_quad', V('quad'), 1, ['PPP', 'WEW', 'WPW'], {'P': P('Steel'), 'W': RW, 'E': I('engine_lv')})
    shaped('equipment/vehicle_motorbike', V('motorbike'), 1, ['  R', 'PEP', 'W W'], {'R': R('Aluminium'), 'P': P('Aluminium'), 'W': RW, 'E': I('engine_mv')})
    shaped('equipment/vehicle_car', V('car'), 1, ['GGG', 'PEP', 'WPW'], {'G': GL, 'P': P('Aluminium'), 'W': RW, 'E': I('engine_mv')})
    shaped('equipment/vehicle_boat', V('boat'), 1, ['P G', 'PEP', 'PPP'], {'G': GL, 'P': P('Aluminium'), 'E': I('engine_mv')})
    shaped('equipment/vehicle_truck', V('truck'), 1, ['PCG', 'PEP', 'WPW'], {'G': GL, 'P': P('StainlessSteel'), 'C': 'minecraft:chest', 'W': RW, 'E': I('engine_hv')})
    shaped('equipment/vehicle_plane', V('plane'), 1, ['WGW', 'PEP', 'R R'], {'W': P('Aluminium'), 'G': GL, 'P': P('StainlessSteel'), 'R': RW, 'E': I('engine_hv')})
    shaped('equipment/vehicle_helicopter', V('helicopter'), 1, ['RMR', 'GEP', 'PPP'], {'R': R('Titanium'), 'M': I('electric_motor_ev'), 'G': GL, 'P': P('Titanium'), 'E': I('engine_ev')})
    shaped('equipment/vehicle_tank', V('tank'), 1, ['PLP', 'WEW', 'WPW'], {'P': P('TungstenSteel'), 'L': I('rocket_launcher'), 'W': RW, 'E': I('engine_ev')})
    shaped('equipment/vehicle_jet', V('jet'), 1, ['GCG', 'PTP', 'PTP'], {'G': GL, 'C': C + 'elite', 'P': P('TungstenSteel'), 'T': I('jet_turbine')})

    # --- messages --------------------------------------------------------------------------------------------------------------------
    for k, en, ru in (
            ('itemGroup.iis.equipment', 'IIS 2.0: Weapons and Vehicles', 'ИИС 2.0: оружие и транспорт'),
            ('key.iis.reload', 'Reload', 'Перезарядка'), ('key.iis.vehicle_fire', 'Vehicle gun', 'Пушка машины'),
            ('tooltip.iis.gun.damage', 'Damage %s, range %s, shots per second %s', 'Урон %s, дальность %s, выстрелов в секунду %s'),
            ('tooltip.iis.gun.blast', 'Blast radius %s, does not break blocks', 'Радиус взрыва %s, блоки не ломает'),
            ('tooltip.iis.gun.pierce', 'Goes through %s targets', 'Пробивает целей: %s'),
            ('tooltip.iis.gun.auto', 'Right click and hold: burst. Sneak: aim', 'ПКМ, удержание — очередь. Присесть — прицел'),
            ('tooltip.iis.gun.semi', 'Right click: shot. Sneak: aim', 'ПКМ — выстрел. Присесть — прицел'),
            ('tooltip.iis.gun.magazine', 'Loaded %s of %s, ammunition: %s. R reloads', 'В магазине %s из %s, патрон: %s. R — перезарядка'),
            ('tooltip.iis.gun.energy', '%s EU a shot, %s shots left. Charged in a machine', '%s EU за выстрел, осталось выстрелов: %s. Заряжается в машине'),
            ('hud.iis.gun.shots', 'Shots: %s', 'Выстрелов: %s'), ('hud.iis.gun.reloading', 'Reloading...', 'Перезарядка...'),
            ('tooltip.iis.vehicle.speed', 'Top speed %s km/h, seats %s', 'Скорость до %s км/ч, мест: %s'),
            ('tooltip.iis.vehicle.solid', 'Burns furnace fuel', 'Топливо: то, что горит в печи'),
            ('tooltip.iis.vehicle.liquid', 'Liquid fuel: bucket or capsule of what a combustion generator burns', 'Топливо: ведро или капсула того, что горит в дизельном генераторе'),
            ('tooltip.iis.vehicle.fuel', 'Tank: %s%%', 'В баке: %s%%'), ('tooltip.iis.vehicle.cargo', 'Cargo: %s slots', 'Груз: %s слотов'),
            ('tooltip.iis.vehicle.help', 'Right click: get in, or refuel with fuel in hand. Sneak + right click: window', 'ПКМ — сесть, с топливом в руке — заправить. Shift+ПКМ — окно'),
            ('message.iis.vehicle.plane', 'W and S: throttle, mouse: where to fly, Shift: get out', 'W и S — газ, мышь — куда лететь, Shift — выйти'),
            ('message.iis.vehicle.helicopter', 'Space: up, Ctrl: down, W A S D: move, wait for the rotor to spin up', 'Пробел — вверх, Ctrl — вниз, W A S D — движение, винт раскручивается не сразу'),
            ('message.iis.vehicle.tank_full', 'The tank has no room for that', 'В баке нет места'), ('message.iis.vehicle.no_shells', 'No shells in the cargo', 'В грузе нет снарядов'),
            ('message.iis.vehicle.needs_water', 'A boat goes on water', 'Катер ставится на воду'), ('message.iis.vehicle.no_room', 'No room here', 'Здесь не поместится'),
            ('hud.iis.vehicle.speed', '%s km/h', '%s км/ч'), ('hud.iis.vehicle.no_fuel', 'No fuel', 'Нет топлива'), ('hud.iis.vehicle.fuel', 'Fuel %s%%', 'Топливо %s%%'),
            ('hud.iis.vehicle.throttle', 'Throttle %s%%, height %s', 'Газ %s%%, высота %s'), ('hud.iis.vehicle.rotor', 'Rotor %s%%, height %s', 'Винт %s%%, высота %s'),
            ('gui.iis.vehicle.cargo', 'Cargo', 'Груз'), ('gui.iis.vehicle.fuel', 'Fuel %s%%, about %s s at full power', 'Топливо %s%%, на %s с полного хода'), ('gui.iis.vehicle.health', 'Condition %s%%. Duct tape repairs it', 'Состояние %s%%. Чинится изолентой'),
            ('gui.iis.vehicle.slot_solid', 'Coal, charcoal, wood: anything a furnace burns', 'Уголь, древесный уголь, дрова: всё, что горит в печи'),
            ('gui.iis.vehicle.slot_liquid', 'Bucket or capsule of fuel', 'Ведро или капсула топлива')):
        name(k, en, ru)
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec), 'meshes': len(meshes)}
