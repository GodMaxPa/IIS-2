"""Models of guns (D128). Origin at the grip, muzzle towards +Z; own design, units in mesh_art.py."""
import math

from mesh_art import Mesh, darker

WALNUT, DARK_WOOD = (106, 66, 38), (72, 44, 28)
IRON, STEEL, BLUED, BLACK, POLY, OLIVE = (112, 114, 122), (160, 164, 172), (52, 56, 66), (28, 29, 32), (44, 46, 50), (84, 92, 58)
BRASS, BRONZE, CHROME = (204, 160, 66), (178, 118, 58), (214, 218, 224)
WHITE, GREY = (222, 224, 228), (120, 126, 134)


def barrel(m, y, z0, z1, r, col, n=8, mat='metal'):
    m.cyl('z', (0, y, z0), r, z1 - z0, col, n=n, mat=mat)


def lock(m, z=0.0, top=3.6, col=IRON):
    """Flint lock on the right side: hammer and pan."""
    m.hexa([(1.0, 1.4, z - 2.8), (1.45, 1.4, z - 2.8), (1.45, 1.4, z - 2), (1.0, 1.4, z - 2), (1.0, top, z - 3.6), (1.45, top, z - 3.6), (1.45, top, z - 2.7), (1.0, top, z - 2.7)], col, 'metal')
    m.box(1.0, top - 0.4, z - 3.4, 1.45, top + 0.2, z - 2, (60, 60, 64), 'plain')
    m.box(1.0, 1.5, z - 0.8, 1.45, top - 0.4, z - 0.3, col, 'metal')


def flintlock_pistol():
    m = Mesh('flintlock_pistol')
    m.hexa([(-0.9, -6, -5.5), (0.9, -6, -5.5), (0.9, -6, -2.8), (-0.9, -6, -2.8), (-0.9, 0, -2.6), (0.9, 0, -2.6), (0.9, 0, 1.4), (-0.9, 0, 1.4)], WALNUT, 'wood')
    m.box(-1.1, -7, -6, 1.1, -5.8, -2.6, BRASS, 'metal')
    m.box(-0.9, 0, -3, 0.9, 1.4, 9, WALNUT, 'wood')
    barrel(m, 1.8, 0, 13, 0.8, IRON)
    m.cyl('z', (0, 1.8, 12), 1.05, 1.2, BRASS, n=8, mat='metal')
    m.cyl('z', (0, 1.8, 0), 1.05, 1.2, BRASS, n=8, mat='metal')
    m.box(-1.05, 0.3, -1.4, 1.05, 1.5, 2, BRONZE, 'metal')
    lock(m, 1.2, 3.3)
    m.box(-0.3, -1.6, -2.2, 0.3, -1.2, 0.4, BRASS, 'metal')
    m.box(-0.3, -1.6, 0.2, 0.3, 0, 0.6, BRASS, 'metal')
    m.meta['muzzle'] = [0, 1.8, 13.5]
    return m


def _long_stock(m, wood, butt_y=-3.6, z0=-14):
    m.hexa([(-1, butt_y, z0), (1, butt_y, z0), (1, -1, -2), (-1, -1, -2), (-1, 0.6, z0), (1, 0.6, z0), (1, 1, -2), (-1, 1, -2)], wood, 'wood')


def musket():
    m = Mesh('musket')
    _long_stock(m, WALNUT, -4.6, -15)
    m.box(-1.1, -4.8, -15.4, 1.1, 0.8, -14.8, BRASS, 'metal')
    m.box(-1, -1, -3, 1, 1.2, 24, WALNUT, 'wood')
    barrel(m, 1.7, -1, 36, 0.85, IRON)
    for z in (6, 15, 24):
        m.cyl('z', (0, 1.5, z), 1.15, 0.8, BRASS, n=8, mat='metal')
    m.box(-0.25, 2.5, 33.5, 0.25, 3.1, 34.2, BRASS, 'metal')
    m.box(-1.1, 0.2, -3, 1.1, 1.6, 2, BRONZE, 'metal')
    lock(m)
    m.box(-0.3, -2.2, -3, 0.3, -1.8, 1, BRASS, 'metal')
    m.cyl('z', (0, 0.5, 2), 0.28, 26, STEEL, n=5, mat='metal')
    m.meta['muzzle'] = [0, 1.7, 36.3]
    return m


def rifled_musket():
    m = Mesh('rifled_musket')
    _long_stock(m, DARK_WOOD, -4.4, -13)
    m.box(-1.1, -4.6, -13.4, 1.1, 0.8, -12.8, STEEL, 'metal')
    m.box(-1, -1, -3, 1, 1.2, 20, DARK_WOOD, 'wood')
    m.cyl('z', (0, 1.8, -1), 0.95, 30, BLUED, n=6, mat='metal')
    for z in (8, 17):
        m.cyl('z', (0, 1.5, z), 1.2, 0.8, STEEL, n=8, mat='metal')
    m.box(-0.5, 2.7, 3, 0.5, 3.6, 7, STEEL, 'metal')
    m.box(-0.25, 2.7, 28, 0.25, 3.3, 28.8, STEEL, 'metal')
    m.box(-1.1, 0.2, -3, 1.1, 1.6, 2, IRON, 'metal')
    lock(m, 0, 3.2)
    m.box(-0.3, -2.2, -3, 0.3, -1.8, 1, STEEL, 'metal')
    m.meta['muzzle'] = [0, 1.8, 29.3]
    return m


def blunderbuss():
    m = Mesh('blunderbuss')
    _long_stock(m, WALNUT, -4.6, -12)
    m.box(-1.1, -4.8, -12.4, 1.1, 0.8, -11.8, BRASS, 'metal')
    m.box(-1, -1, -3, 1, 1.2, 10, WALNUT, 'wood')
    m.cyl('z', (0, 1.9, -1), 1.15, 13, BRASS, n=10, mat='metal', r2=1.3)
    m.cyl('z', (0, 1.9, 12), 1.3, 6, BRASS, n=10, mat='metal', r2=3.2, caps=False)
    m.cyl('z', (0, 1.9, 17.9), 3.2, 0.2, (24, 24, 26), n=10, mat='plain')
    m.cyl('z', (0, 1.9, 3), 1.45, 0.8, BRONZE, n=10, mat='metal')
    m.cyl('z', (0, 1.9, 10), 1.5, 0.8, BRONZE, n=10, mat='metal')
    m.box(-1.1, 0.2, -3, 1.1, 1.6, 2, BRONZE, 'metal')
    lock(m)
    m.box(-0.3, -2.2, -3, 0.3, -1.8, 1, BRASS, 'metal')
    m.meta['muzzle'] = [0, 1.9, 18]
    return m


def _grip(m, col=POLY, z=-1.6, h=5.2):
    m.hexa([(-0.85, -h, z - 1.4), (0.85, -h, z - 1.4), (0.85, -h, z + 0.9), (-0.85, -h, z + 0.9), (-0.85, 0, z - 0.3), (0.85, 0, z - 0.3), (0.85, 0, z + 2.2), (-0.85, 0, z + 2.2)], col, 'cloth')


def _trigger(m, z=1.4, col=BLACK):
    m.box(-0.2, -1.5, z - 1.6, 0.2, -1.2, z + 1, col, 'metal')
    m.box(-0.2, -1.5, z + 0.8, 0.2, 0, z + 1.1, col, 'metal')
    m.box(-0.14, -1.1, z - 0.2, 0.14, 0, z + 0.1, CHROME, 'metal')


def pistol():
    m = Mesh('pistol')
    _grip(m, (58, 42, 32))
    _trigger(m)
    m.box(-0.8, 0, -2, 0.8, 1.1, 7, BLUED, 'metal')
    m.box(-0.9, 1.1, -3, 0.9, 2.8, 8, (56, 60, 70), 'scratch')
    for z in (-2.6, -2, -1.4, -0.8):
        m.box(-0.95, 1.4, z, 0.95, 2.6, z + 0.25, BLACK, 'plain')
    m.box(-0.3, 2.8, 7, 0.3, 3.2, 7.6, BLACK, 'metal')
    m.box(-0.5, 2.8, -2.8, 0.5, 3.2, -2.2, BLACK, 'metal')
    barrel(m, 1.9, 8, 8.5, 0.55, BLACK)
    m.meta['muzzle'] = [0, 1.9, 8.6]
    return m


def smg():
    m = Mesh('smg')
    _grip(m, POLY)
    _trigger(m)
    m.box(-1, 0, -8, 1, 3, 9, BLUED, 'scratch')
    m.box(-0.8, -0.8, 2, 0.8, 0, 8, POLY, 'cloth')
    # magazine
    m.hexa([(-0.65, -7, 2.4), (0.65, -7, 2.4), (0.65, -7, 4.5), (-0.65, -7, 4.5), (-0.65, -0.8, 3), (0.65, -0.8, 3), (0.65, -0.8, 5), (-0.65, -0.8, 5)], BLACK, 'metal')
    # folding stock
    m.box(-0.3, 1, -15, 0.3, 1.5, -8, STEEL, 'metal')
    m.box(-0.3, 0, -15, 0.3, 0.5, -8, STEEL, 'metal')
    m.box(-0.6, -1, -15.6, 0.6, 2, -15, POLY, 'cloth')
    # shroud with holes, muzzle
    m.cyl('z', (0, 1.9, 9), 0.95, 7, (66, 70, 78), n=8, mat='grill')
    m.cyl('z', (0, 1.9, 16), 0.6, 1.6, BLACK, n=8, mat='metal')
    m.box(-0.35, 3, 6, 0.35, 3.7, 7, BLACK, 'metal')
    m.box(-0.5, 3, -7, 0.5, 3.8, -6, BLACK, 'metal')
    m.box(1, 1.6, -3, 1.3, 2.4, 0.5, STEEL, 'metal')
    m.meta['muzzle'] = [0, 1.9, 17.7]
    return m


def shotgun():
    m = Mesh('shotgun')
    _long_stock(m, (88, 56, 34), -4.4, -13)
    m.box(-1.1, -4.6, -13.4, 1.1, 0.8, -12.8, BLACK, 'rubber')
    m.box(-1.1, -1, -3, 1.1, 2.2, 7, BLUED, 'metal')
    _trigger(m, 1.5)
    barrel(m, 1.5, 7, 28, 0.85, BLUED)
    m.cyl('z', (0, 0.1, 7), 0.8, 18, (64, 68, 76), n=8, mat='metal')
    # pump grip
    m.cyl('z', (0, 0.1, 13), 1.25, 6, (88, 56, 34), n=8, mat='wood')
    for z in (13.6, 15, 16.4, 17.8):
        m.cyl('z', (0, 0.1, z), 1.32, 0.4, DARK_WOOD, n=8, mat='plain')
    m.box(-0.2, 2.3, 27, 0.2, 2.8, 27.6, BRASS, 'metal')
    m.meta['muzzle'] = [0, 1.5, 28.2]
    return m


def assault_rifle():
    m = Mesh('assault_rifle')
    _grip(m, POLY, -1.2)
    _trigger(m, 2)
    m.box(-1, 0, -6, 1, 3.2, 10, (70, 76, 62), 'scratch')
    m.box(-0.95, 3.2, -6, 0.95, 3.7, 10, OLIVE, 'metal')
    # top rail
    for z in range(-5, 10, 2):
        m.box(-0.7, 3.7, z, 0.7, 4.1, z + 1, BLACK, 'metal')
    # curved magazine
    for a, b in ((-0.6, 3.4), (-3, 2.7), (-5.2, 1.6), (-7, 0.2)):
        m.hexa([(-0.65, a - 2.4, b - 0.7), (0.65, a - 2.4, b - 0.7), (0.65, a - 2.4, b + 1.6), (-0.65, a - 2.4, b + 1.6), (-0.65, a, b), (0.65, a, b), (0.65, a, b + 2.4), (-0.65, a, b + 2.4)], BLACK, 'metal')
    # stock
    m.hexa([(-0.9, -2.4, -18), (0.9, -2.4, -18), (0.9, 0, -6), (-0.9, 0, -6), (-0.9, 2.8, -18), (0.9, 2.8, -18), (0.9, 3, -6), (-0.9, 3, -6)], OLIVE, 'panel')
    m.box(-1, -2.6, -18.4, 1, 3, -17.8, BLACK, 'rubber')
    # handguard and barrel
    m.box(-1.05, 0.4, 10, 1.05, 3.1, 20, OLIVE, 'grill')
    barrel(m, 1.8, 20, 27, 0.55, BLACK)
    m.cyl('z', (0, 1.8, 27), 0.85, 2.6, BLACK, n=8, mat='grill')
    m.box(-0.35, 3.1, 19, 0.35, 4, 19.8, BLACK, 'metal')
    m.box(-0.25, 1, 18, 0.25, 3.2, 18.4, BLACK, 'metal')
    # small sight
    m.box(-0.7, 4.1, -1, 0.7, 5.6, 3, BLACK, 'metal')
    m.cyl('z', (0, 4.9, 3), 0.55, 0.1, (230, 60, 60), n=8, mat='plain', glow=True)
    m.meta['muzzle'] = [0, 1.8, 29.8]
    return m


def grenade_launcher():
    m = Mesh('grenade_launcher')
    _grip(m, POLY)
    _trigger(m)
    # stock
    m.hexa([(-0.8, -2, -13), (0.8, -2, -13), (0.8, 0, -3), (-0.8, 0, -3), (-0.8, 3, -13), (0.8, 3, -13), (0.8, 3, -3), (-0.8, 3, -3)], OLIVE, 'panel')
    m.box(-0.9, -2.2, -13.3, 0.9, 3.2, -12.9, BLACK, 'rubber')
    m.box(-1.1, 0, -3, 1.1, 3.4, 3, BLUED, 'metal')
    # revolving drum
    m.cyl('z', (0, 1.1, 3), 3.5, 7, BLUED, n=6, mat='rivet')
    for k in range(6):
        t = k * math.pi / 3
        m.cyl('z', (math.cos(t) * 2.3, 1.1 + math.sin(t) * 2.3, 9.9), 0.9, 0.2, (24, 24, 26), n=8, mat='plain')
    # tube
    m.cyl('z', (0, 1.1, 10), 1.9, 13, OLIVE, n=10, mat='metal')
    m.cyl('z', (0, 1.1, 23), 2.2, 1, BLACK, n=10, mat='metal')
    m.cyl('z', (0, 1.1, 24), 1.6, 0.1, (22, 22, 24), n=10, mat='plain')
    m.box(-0.8, -1.4, 13, 0.8, -0.6, 17, POLY, 'cloth')
    m.box(-0.3, 3, 20, 0.3, 4.2, 20.8, BLACK, 'metal')
    m.box(-0.5, 3.4, -1, 0.5, 4.8, 0, BLACK, 'metal')
    m.meta['muzzle'] = [0, 1.1, 24.3]
    return m


def sniper_rifle():
    m = Mesh('sniper_rifle')
    _grip(m, BLACK, -1)
    _trigger(m, 2)
    m.box(-1, 0, -5, 1, 2.6, 12, (46, 50, 56), 'scratch')
    # skeleton stock
    m.box(-0.7, 1.4, -19, 0.7, 2.6, -5, GREY, 'metal')
    m.box(-0.7, -2.4, -19, 0.7, -1.2, -9, GREY, 'metal')
    m.hexa([(-0.7, -1.2, -11), (0.7, -1.2, -11), (0.7, -1.2, -9), (-0.7, -1.2, -9), (-0.7, 1.4, -7), (0.7, 1.4, -7), (0.7, 1.4, -5), (-0.7, 1.4, -5)], GREY, 'metal')
    m.box(-0.9, -2.8, -19.5, 0.9, 3, -19, BLACK, 'rubber')
    m.box(-0.45, 2.6, -14, 0.45, 3.6, -9, BLACK, 'cloth')
    # magazine
    m.box(-0.7, -4, 2.5, 0.7, 0, 5, BLACK, 'metal')
    # long barrel with brake
    m.box(-1.1, 0.4, 12, 1.1, 2.6, 22, (46, 50, 56), 'grill')
    barrel(m, 1.6, 22, 42, 0.62, BLACK)
    m.box(-1, 0.8, 42, 1, 2.4, 45, BLACK, 'grill')
    # scope
    m.cyl('z', (0, 5.2, -2), 1.05, 10, BLACK, n=10, mat='metal')
    m.cyl('z', (0, 5.2, 8), 1.05, 4, BLACK, n=10, mat='metal', r2=1.6, caps=False)
    m.cyl('z', (0, 5.2, 12), 1.6, 0.6, BLACK, n=10, mat='metal')
    m.cyl('z', (0, 5.2, 12.65), 1.4, 0.05, (120, 190, 230), n=10, mat='plain', glow=True)
    m.cyl('z', (0, 5.2, -3.4), 1.25, 1.4, BLACK, n=10, mat='metal')
    m.box(-0.5, 2.6, 0, 0.5, 4.3, 1.4, BLACK, 'metal')
    m.box(-0.5, 2.6, 7, 0.5, 4.3, 8.4, BLACK, 'metal')
    m.cyl('y', (0, 6.2, 3), 0.5, 0.6, GREY, n=6, mat='metal')
    # bipod
    for s in (-1, 1):
        m.hexa([(-0.2, 0.2, 20), (0.2, 0.2, 20), (0.2, 0.2, 20.5), (-0.2, 0.2, 20.5), (s * 2.2 - 0.2, -5, 23), (s * 2.2 + 0.2, -5, 23), (s * 2.2 + 0.2, -5, 23.5), (s * 2.2 - 0.2, -5, 23.5)], GREY, 'metal')
    m.meta['muzzle'] = [0, 1.6, 45.3]
    return m


def machine_gun():
    m = Mesh('machine_gun')
    _grip(m, BLACK)
    _trigger(m)
    m.box(-1.3, 0, -7, 1.3, 3.4, 11, (64, 70, 56), 'rivet')
    m.hexa([(-0.9, -2.6, -19), (0.9, -2.6, -19), (0.9, 0, -7), (-0.9, 0, -7), (-0.9, 3, -19), (0.9, 3, -19), (0.9, 3.2, -7), (-0.9, 3.2, -7)], (60, 66, 52), 'panel')
    m.box(-1, -2.8, -19.4, 1, 3.2, -19, BLACK, 'rubber')
    # ammo box with belt
    m.box(-2.2, -6.6, 0, 2.2, -0.2, 8, OLIVE, 'rivet')
    m.box(-1.8, -6.7, 2, 1.8, -6.6, 6, BRASS, 'plain')
    for i in range(6):
        m.box(1.3, 0.1 + i * 0.5, 3 + i * 0.1, 2, 0.4 + i * 0.5, 7 + i * 0.1, BRASS, 'metal')
    # heavy barrel in a shroud with cooling holes
    m.cyl('z', (0, 1.8, 11), 1.5, 21, (54, 58, 50), n=10, mat='grill')
    barrel(m, 1.8, 32, 37, 0.8, BLACK)
    m.cyl('z', (0, 1.8, 37), 1.3, 2.4, BLACK, n=8, mat='grill')
    m.box(-0.4, 3.3, 29, 0.4, 4.4, 30, BLACK, 'metal')
    m.box(-0.8, 3.4, -3, 0.8, 4.2, 3, BLACK, 'metal')
    for s in (-1, 1):
        m.hexa([(-0.25, 0.4, 28), (0.25, 0.4, 28), (0.25, 0.4, 28.6), (-0.25, 0.4, 28.6), (s * 3 - 0.25, -6, 31), (s * 3 + 0.25, -6, 31), (s * 3 + 0.25, -6, 31.6), (s * 3 - 0.25, -6, 31.6)], GREY, 'metal')
    m.box(-0.6, 3.6, 9, 0.6, 5.4, 11, BLACK, 'metal')    # carrying handle
    m.meta['muzzle'] = [0, 1.8, 39.5]
    return m


def rocket_launcher():
    m = Mesh('rocket_launcher')
    col = (72, 84, 56)
    m.cyl('z', (0, 2.2, -18), 2.8, 40, col, n=12, mat='scratch')
    m.cyl('z', (0, 2.2, 22), 2.8, 5, darker(col, 0.8), n=12, mat='metal', r2=3.6, caps=False)
    m.cyl('z', (0, 2.2, 26.9), 3.5, 0.1, (20, 20, 22), n=12, mat='plain')
    m.cyl('z', (0, 2.2, -18), 2.8, -6, darker(col, 0.7), n=12, mat='metal', r2=3.8, caps=False)
    m.cyl('z', (0, 2.2, -24), 3.7, -0.1, (20, 20, 22), n=12, mat='plain')
    for z in (-8, 6):
        m.cyl('z', (0, 2.2, z), 3, 0.9, darker(col, 0.65), n=12, mat='metal')
    # warhead in the tube
    m.cyl('z', (0, 2.2, 23), 2.3, 4.5, (220, 60, 30), n=12, mat='paint', r2=0.7)
    _grip(m, BLACK, -1.6, 5.4)
    _trigger(m, 0.4)
    m.box(-0.8, -5, 6, 0.8, -0.4, 7.6, BLACK, 'cloth')
    m.box(-1, -0.6, -4, 1, 0, 8, BLACK, 'metal')
    # sight on the side
    m.box(2.4, 3.4, -2, 4.2, 6, 3, BLACK, 'metal')
    m.cyl('z', (3.3, 4.7, 3), 0.8, 0.1, (240, 80, 40), n=8, mat='plain', glow=True)
    m.box(-1.4, 5, -13, 1.4, 5.3, -6, (212, 170, 50), 'plain')
    m.meta['muzzle'] = [0, 2.2, 28]
    return m


GLOW_RED, GLOW_GRN, GLOW_BLU, GLOW_VIO, GLOW_CYN = (255, 60, 50), (90, 255, 110), (90, 150, 255), (210, 90, 255), (90, 240, 255)


def _emitter(m, y, z, r, glow, col=BLACK, ring=CHROME):
    m.cyl('z', (0, y, z), r, 1.6, ring, n=10, mat='metal', r2=r * 1.2, caps=False)
    m.cyl('z', (0, y, z + 1.55), r * 1.2, 0.05, col, n=10, mat='plain')
    m.cyl('z', (0, y, z + 1.6), r * 0.7, 0.05, glow, n=10, mat='plain', glow=True)


def laser_pistol():
    m = Mesh('laser_pistol')
    _grip(m, BLACK)
    _trigger(m)
    m.rloft([(-3.4, 0.9, 0, 3.2, 0.8), (1, 1, 0, 3.6, 0.9), (7, 1, 0.4, 3.2, 0.8), (9, 0.8, 0.9, 2.8, 0.6)], WHITE, 'panel')
    m.box(-1.05, 1.4, -3, -0.98, 2, 7, GLOW_RED, 'plain', glow=True)
    m.box(0.98, 1.4, -3, 1.05, 2, 7, GLOW_RED, 'plain', glow=True)
    m.cyl('z', (0, 1.85, 9), 0.75, 3, GREY, n=8, mat='metal')
    for z in (9.4, 10.4, 11.2):
        m.cyl('z', (0, 1.85, z), 0.95, 0.25, BLACK, n=8, mat='metal')
    _emitter(m, 1.85, 12, 0.7, GLOW_RED)
    # power cell
    m.box(-0.9, -4.8, -3.2, 0.9, -3.8, -1.4, GLOW_RED, 'grill', glow=True)
    m.box(-0.5, 3.4, -2, 0.5, 4, 2, BLACK, 'metal')
    m.meta['muzzle'] = [0, 1.85, 13.7]
    return m


def laser_rifle():
    m = Mesh('laser_rifle')
    _grip(m, BLACK, -1)
    _trigger(m, 2)
    m.rloft([(-16, 0.8, -1.8, 2.4, 0.7), (-7, 1, -0.2, 3.2, 0.9), (6, 1.3, -0.2, 3.8, 1), (14, 1.2, 0.2, 3.4, 0.9), (22, 0.9, 0.8, 3, 0.7)], (214, 218, 224), 'panel')
    m.box(-0.95, -2.2, -16.4, 0.95, 2.6, -16, BLACK, 'rubber')
    for s in (-1.33, 1.3):
        m.box(s, 1.2, -5, s + 0.03, 2, 18, GLOW_GRN, 'plain', glow=True)
    # coil on the barrel
    m.cyl('z', (0, 1.9, 22), 0.8, 7, GREY, n=8, mat='metal')
    for z in (22.4, 23.6, 24.8, 26, 27.2):
        m.cyl('z', (0, 1.9, z), 1.15, 0.45, (190, 130, 60), n=8, mat='metal')
    _emitter(m, 1.9, 29, 0.8, GLOW_GRN)
    # cell and sight
    m.box(-0.7, -4.2, 2.6, 0.7, -0.2, 5.4, BLACK, 'metal')
    m.box(-0.72, -3.6, 3, 0.72, -1, 5, GLOW_GRN, 'grill', glow=True)
    m.box(-0.8, 3.8, -2, 0.8, 5.6, 5, BLACK, 'metal')
    m.box(-0.6, 4.1, 5, 0.6, 5.3, 5.05, GLOW_GRN, 'plain', glow=True)
    m.meta['muzzle'] = [0, 1.9, 30.7]
    return m


def laser_cannon():
    m = Mesh('laser_cannon')
    col = (58, 64, 78)
    _grip(m, BLACK, -1)
    _trigger(m, 2)
    m.rloft([(-20, 1, -2, 3, 0.8), (-9, 1.8, -1, 5.4, 1.6), (8, 2.4, -1, 6, 2), (20, 2, 0, 5.4, 1.6)], col, 'plate')
    m.box(-1.2, -2.2, -20.3, 1.2, 3.2, -20, BLACK, 'rubber')
    # cells on the sides
    for s in (-1, 1):
        xa, xb = sorted((s * 2.3, s * 3.6))
        m.box(xa, 0.4, -7, xb, 3.4, 6, BLACK, 'metal')
        xa, xb = sorted((s * 3.55, s * 3.65))
        m.box(xa, 0.9, -6, xb, 2.9, 5, GLOW_BLU, 'grill', glow=True)
    # three prongs around the beam
    for k in range(3):
        t = math.pi / 2 + k * 2 * math.pi / 3
        x, y = math.cos(t) * 2.5, 2 + math.sin(t) * 2.5
        m.cyl('z', (x, y, 20), 0.7, 16, GREY, n=6, mat='metal')
        m.cyl('z', (x, y, 36), 0.7, 3, CHROME, n=6, mat='metal', r2=0.2)
    for z in (22, 28, 34):
        m.cyl('z', (0, 2, z), 3.4, 0.7, col, n=10, mat='metal')
    m.cyl('z', (0, 2, 20), 1.2, 17, BLACK, n=8, mat='metal')
    m.cyl('z', (0, 2, 37), 1.0, 0.1, GLOW_BLU, n=8, mat='plain', glow=True)
    m.box(-1, 5, -4, 1, 7, 8, BLACK, 'metal')
    m.cyl('z', (0, 6, 8), 0.7, 0.1, GLOW_BLU, n=8, mat='plain', glow=True)
    m.box(-0.8, -3.6, 7, 0.8, -1, 9, BLACK, 'cloth')
    m.meta['muzzle'] = [0, 2, 38]
    return m


def plasma_rifle():
    m = Mesh('plasma_rifle')
    col = (40, 42, 56)
    _grip(m, BLACK, -1)
    _trigger(m, 2)
    m.rloft([(-17, 0.9, -1.8, 2.8, 0.7), (-6, 1.3, -0.6, 3.8, 1.1), (8, 1.5, -0.6, 4.2, 1.2)], col, 'plate')
    m.box(-1, -2.1, -17.3, 1, 3, -17, BLACK, 'rubber')
    # glass chamber with plasma, seen from the side
    m.loft([(8, 0, 1.9, 1.5, 1.8), (10, 0, 1.9, 2.2, 2.5), (19, 0, 1.9, 2.2, 2.5), (21, 0, 1.9, 1.5, 1.8)], (200, 150, 255), n=10, mat='plain', glass=True)
    m.loft([(9, 0, 1.9, 1.1, 1.1), (20, 0, 1.9, 1.1, 1.1)], GLOW_VIO, n=8, mat='plain', glow=True)
    for z in (8, 14, 20.6):
        m.cyl('z', (0, 1.9, z), 2.6, 0.5, BLACK, n=10, mat='metal')
    for k in range(3):
        t = -math.pi / 2 + k * 2 * math.pi / 3
        m.box(math.cos(t) * 2.4 - 0.2, 1.9 + math.sin(t) * 2.4 - 0.2, 8, math.cos(t) * 2.4 + 0.2, 1.9 + math.sin(t) * 2.4 + 0.2, 21, GREY, 'metal')
    # nozzle with fins
    m.cyl('z', (0, 1.9, 21), 1.6, 5, col, n=10, mat='metal', r2=1.1)
    for k in range(4):
        t = k * math.pi / 2
        c_, s_ = math.cos(t), math.sin(t)
        m.quad((c_ * 1.1, 1.9 + s_ * 1.1, 21.5), (c_ * 2.9, 1.9 + s_ * 2.9, 22.5), (c_ * 2.2, 1.9 + s_ * 2.2, 26), (c_ * 1, 1.9 + s_ * 1, 26), BLACK, 'metal')
    _emitter(m, 1.9, 26, 1, GLOW_VIO)
    m.box(-0.8, 3.8, -2, 0.8, 5.4, 5, BLACK, 'metal')
    m.box(-0.75, -4, 2.8, 0.75, -0.2, 5.4, BLACK, 'metal')
    m.box(-0.77, -3.4, 3.2, 0.77, -1, 5, GLOW_VIO, 'grill', glow=True)
    m.meta['muzzle'] = [0, 1.9, 27.7]
    return m


def plasma_cannon():
    m = Mesh('plasma_cannon')
    col = (32, 34, 44)
    trim = (196, 164, 70)
    _grip(m, BLACK, -2)
    _trigger(m, 1)
    m.box(-0.9, -3.8, 8, 0.9, -1.6, 10, BLACK, 'cloth')
    m.rloft([(-20, 1.6, -2, 4, 1.2), (-10, 2.8, -1.6, 6.4, 2.2), (6, 3.4, -1.6, 7, 2.8)], col, 'plate')
    m.box(-1.6, -2.2, -20.3, 1.6, 4.2, -20, BLACK, 'rubber')
    m.sym(2.9, 1, -9, 3.4, 2, 5, trim, 'metal')
    # reactor with glow behind glass
    m.loft([(6, 0, 2.4, 3.6, 3.8), (8, 0, 2.4, 4.8, 5), (20, 0, 2.4, 4.8, 5), (22, 0, 2.4, 3.6, 3.8)], (120, 240, 255), n=12, mat='plain', glass=True)
    m.loft([(7, 0, 2.4, 2.6, 2.6), (21, 0, 2.4, 2.6, 2.6)], GLOW_CYN, n=10, mat='plain', glow=True)
    for z in (6.5, 13.6, 21):
        m.cyl('z', (0, 2.4, z), 5.2, 0.9, col, n=12, mat='metal')
        m.cyl('z', (0, 2.4, z + 0.3), 5.3, 0.3, trim, n=12, mat='metal', caps=False)
    # wide muzzle
    m.cyl('z', (0, 2.4, 22), 3.4, 6, col, n=12, mat='rivet', r2=4)
    m.cyl('z', (0, 2.4, 28), 4, 5, col, n=12, mat='metal', r2=5.6, caps=False)
    m.cyl('z', (0, 2.4, 32.9), 5.6, 0.1, BLACK, n=12, mat='plain')
    m.cyl('z', (0, 2.4, 33), 3, 0.1, GLOW_CYN, n=12, mat='plain', glow=True)
    # heat fins on top
    for z in range(-8, 5, 2):
        m.box(-2, 6.2, z, 2, 7.6, z + 0.7, (70, 74, 90), 'metal')
    m.box(-1.1, 7.6, -7, 1.1, 8.6, 4, BLACK, 'metal')
    m.meta['muzzle'] = [0, 2.4, 33.2]
    return m


ALL = [flintlock_pistol, musket, rifled_musket, blunderbuss, pistol, smg, shotgun, assault_rifle, grenade_launcher, sniper_rifle, machine_gun, rocket_launcher, laser_pistol, laser_rifle, laser_cannon, plasma_rifle, plasma_cannon]
