"""Models of locomotives and wagons (D128): they replace the minecart with a block in it. Own design; units in mesh_art.py.

Rail gauge: the wheels stand at x = +-5. A model may be longer than the entity box; TrainLinks.halfLength keeps coupled cars from overlapping.
"""
import math

from mesh_art import Mesh, darker

DARK = (30, 31, 35)
STEEL = (120, 124, 132)
CHROME = (205, 210, 218)
BRASS = (200, 156, 62)
LAMP = (255, 238, 170)
RED = (168, 38, 30)
WHEEL = (44, 45, 50)
GLASS_C = (150, 205, 225)


def couplers(m, zf, zb):
    """Buffers at both ends."""
    for z, d in ((zf, 1), (zb, -1)):
        m.box(-7, 3.6, min(z, z + d * 0.6), 7, 6.6, max(z, z + d * 0.6), RED, 'rivet')
        for s in (-1, 1):
            m.cyl('z', (s * 4.8, 5.1, z if d > 0 else z - 1.4), 0.7, 1.4, STEEL, n=6, mat='metal')
            m.cyl('z', (s * 4.8, 5.1, z + 1.4 if d > 0 else z - 1.7), 1.2, 0.3, CHROME, n=8, mat='metal')
        m.box(-0.6, 4.3, min(z, z + d * 1.6), 0.6, 5.5, max(z, z + d * 1.6), DARK, 'metal')


def bogie(m, name, z):
    """Two-axle truck under a wagon."""
    m.box(-6.2, 2.4, z - 5, 6.2, 4, z + 5, DARK, 'rivet')
    m.sym(5, 1.6, z - 4.6, 6.4, 3.6, z + 4.6, (54, 54, 58), 'metal')
    for i, dz in enumerate((-2.8, 2.8)):
        m.part(f'{name}{i}', (0, 1.9, z + dz), 'wheel', 8.0)
        m.cyl('x', (-5.6, 1.9, z + dz), 1.9, 11.2, WHEEL, n=10, mat='metal')
        m.cyl('x', (-5.7, 1.9, z + dz), 0.8, 11.4, STEEL, n=6, mat='metal')
        m.part('body')


def steam_loco():
    m = Mesh('steam_loco')
    green, blk, cab = (34, 64, 44), (30, 30, 33), (48, 86, 60)
    # frame and running boards
    m.box(-4.8, 3, -21, 4.8, 6, 20, DARK, 'rivet')
    m.sym(3.6, 7, -9, 7.8, 7.5, 18, (70, 70, 74), 'metal')
    # boiler with bands
    m.loft([(-6, 0, 12, 5.3, 5.3), (12, 0, 12, 5.3, 5.3)], green, n=14, mat='paint')
    for z in (-3, 2, 7):
        m.loft([(z, 0, 12, 5.45, 5.45), (z + 0.6, 0, 12, 5.45, 5.45)], BRASS, n=14, mat='metal', caps=False)
    # smokebox
    m.loft([(12, 0, 12, 5.6, 5.6), (18.4, 0, 12, 5.6, 5.6)], blk, n=14, mat='rivet')
    m.loft([(18.4, 0, 12, 5.6, 5.6), (19, 0, 12, 4.6, 4.6)], (60, 60, 64), n=14, mat='metal')
    # chimney, dome, whistle, sand box
    m.cyl('y', (0, 16.5, 15), 1.9, 7, blk, n=10, mat='metal', r2=1.6)
    m.cyl('y', (0, 23.5, 15), 1.6, 1.5, blk, n=10, mat='metal', r2=2.6, caps=False)
    m.cyl('y', (0, 25, 15), 2.6, 0.5, (70, 70, 72), n=10, mat='metal')
    m.cyl('y', (0, 16.5, 4), 2.2, 2.6, BRASS, n=10, mat='metal', r2=2.3)
    m.cyl('y', (0, 19.1, 4), 2.3, 1, BRASS, n=10, mat='metal', r2=1.2)
    m.cyl('y', (0, 16.5, -2.5), 0.5, 3, BRASS, n=6, mat='metal')
    m.cyl('y', (0, 17, 9), 1.5, 2, cab, n=8, mat='paint', r2=1.2)
    # pipes along the boiler
    m.sym(4.9, 14.4, -6, 5.4, 14.9, 13, BRASS, 'metal')
    m.sym(5.3, 11, -6, 5.8, 11.5, 18, darker(green, 0.7), 'metal')
    # cylinders and front
    m.sym(4.6, 3.8, 11.5, 7.8, 7, 18, blk, 'rivet')
    m.sym(4.9, 7, 12, 5.6, 9, 17, BRASS, 'metal')
    m.box(-7.6, 6, 18.6, 7.6, 7.2, 21, RED, 'rivet')
    m.cyl('z', (0, 18.6, 18.6), 1.5, 1.2, BRASS, n=10, mat='metal')
    m.cyl('z', (0, 18.6, 19.8), 1.1, 0.2, LAMP, n=10, mat='plain', glow=True)
    # cowcatcher
    for i in range(-3, 4):
        x = i * 1.5
        m.hexa([(x - 0.3, 6.1, 19.8), (x + 0.3, 6.1, 19.8), (x + 0.3, 6.1, 21), (x - 0.3, 6.1, 21), (x * 0.45 - 0.3, 0.7, 24.5), (x * 0.45 + 0.3, 0.7, 24.5), (x * 0.45 + 0.3, 0.7, 25.2), (x * 0.45 - 0.3, 0.7, 25.2)], (150, 40, 34), 'metal')
    # cab
    m.rloft([(-15.5, 7.2, 7, 16), (-5.5, 7.2, 7, 16)], cab, 'panel')
    m.box(-7.2, 16, -15.5, 7.2, 22.5, -13.6, cab, 'panel')
    m.sym(5.8, 16, -13.6, 7.2, 22.5, -11.2, cab, 'panel')
    m.sym(5.8, 16, -7.5, 7.2, 22.5, -5.5, cab, 'panel')
    m.box(-7.2, 21, -7.5, 7.2, 22.5, -5, cab, 'panel')
    m.rloft([(-17, 7.6, 22.5, 23.2), (-10, 7.8, 23.3, 24.6, 6), (-3.8, 7.6, 22.5, 23.4)], blk, 'paint')
    m.sym(7.25, 8.6, -14, 7.35, 14.5, -7, BRASS, 'plate')
    m.cyl('z', (0, 12.5, -5.3), 2.4, 0.3, BRASS, n=10, mat='metal', cap_color=(240, 236, 220))
    m.box(-1.2, 7, -9, 1.2, 10.5, -6.5, (235, 120, 40), 'grill', glow=True)
    # tender
    m.rloft([(-23, 7, 6.5, 15), (-15.6, 7, 6.5, 15)], blk, 'rivet')
    m.rloft([(-23, 6, 15, 17, 3.5), (-16, 6, 15, 17, 3.5)], (16, 16, 18), 'tread')
    couplers(m, 21, -23)
    # driving wheels and connecting rod
    for i, z in enumerate((-8, -1.2, 5.6)):
        for s in (-1, 1):
            m.part(f'drive{s}_{i}', (s * 6, 3.3, z), 'wheel', 4.8)
            m.cyl('x', (s * 6 - 0.8, 3.3, z), 3.3, 1.6, WHEEL, n=14, mat='metal')
            m.cyl('x', (s * 6 - 0.9, 3.3, z), 3.45, 0.8, STEEL, n=14, mat='metal', caps=False)
            m.cyl('x', (s * 6 - 1, 3.3, z), 0.9, 2, RED, n=8, mat='plain')
            for k in range(4):
                t = k * math.pi / 4
                c_, s_ = math.cos(t) * 2.8, math.sin(t) * 2.8
                xs = s * 6 + s * 0.85
                m.quad((xs, 3.3 - s_, z - c_), (xs, 3.3 + s_, z + c_), (xs, 3.3 + s_ + 0.5, z + c_), (xs, 3.3 - s_ + 0.5, z - c_), RED, 'plain')
            m.part('body')
    for s in (-1, 1):
        m.part(f'rod{s}', (0, 3.3, 0), 'rod', 1.6)
        xa, xb = sorted((s * 7.1, s * 7.7))
        m.box(xa, 2.9, -9, xb, 3.7, 6.6, CHROME, 'metal')
        m.part(f'piston{s}', (0, 3.3, 0), 'piston', 1.6)
        xa, xb = sorted((s * 7.2, s * 7.6))
        m.box(xa, 3.1, 4, xb, 3.5, 14, CHROME, 'metal')
        m.part('body')
    # front truck and rear wheels of the tender
    for z, nm in ((14.5, 'pony'), (-19.5, 'tender')):
        m.part(nm, (0, 1.8, z), 'wheel', 8.0)
        m.cyl('x', (-5.6, 1.8, z), 1.8, 11.2, WHEEL, n=10, mat='metal')
        m.part('body')
    m.meta['smoke'] = [0, 26, 15]
    return m


def electric_loco():
    m = Mesh('electric_loco')
    col, stripe, roof = (182, 44, 36), (232, 200, 60), (92, 96, 104)
    m.box(-6.4, 4.2, -18, 6.4, 6.8, 18, DARK, 'rivet')
    # body with sloped noses
    m.rloft([(-19, 6.5, 7, 15, 5.5), (-16, 7.2, 6.6, 21.5, 6.2), (16, 7.2, 6.6, 21.5, 6.2), (19, 6.5, 7, 15, 5.5)], col, 'panel')
    # windscreens and side windows
    for d in (-1, 1):
        y0, y1 = (15.2, 21.3)
        zb, zt = (d * 18.9, d * 16.05)
        m.quad((-5.5, y0, zb + d * 0.15), (5.5, y0, zb + d * 0.15), (5.8, y1, zt + d * 0.15), (-5.8, y1, zt + d * 0.15), GLASS_C, 'plain')
        m.sym(3, 9.5, d * 19.05 - 0.1, 5.2, 11.5, d * 19.05 + 0.1, LAMP, 'plain', glow=True)
        m.box(-5.5, 7.6, d * 19.05 - 0.1, 5.5, 8.6, d * 19.05 + 0.1, stripe, 'plain')
        m.box(-1, 12, d * 19.05 - 0.1, 1, 14, d * 19.05 + 0.1, CHROME, 'metal')
        m.sym(7.0, 16, d * 11 - 1.8, 7.15, 20, d * 11 + 1.8, GLASS_C, 'plain')
    m.sym(7.15, 9, -18, 7.3, 10.2, 18, stripe, 'plain')
    m.sym(7.15, 15.6, -7.5, 7.3, 20.5, 7.5, (54, 56, 60), 'grill')
    m.sym(7.15, 7.6, -14, 7.3, 8.6, 14, (40, 40, 44), 'metal')
    # roof with insulators and pantograph
    m.box(-5.8, 21.5, -14, 5.8, 22.4, 14, roof, 'rivet')
    m.box(-3, 22.4, -5, 3, 23.6, 5, (60, 62, 68), 'grill')
    for x, z in ((-3, -9), (3, -9), (-3, 9), (3, 9)):
        m.cyl('y', (x, 22.4, z), 0.7, 1.4, (210, 170, 110), n=6, mat='plain')
    m.box(-4, 23.8, -10, 4, 24.2, -8, DARK, 'metal')
    m.box(-4, 23.8, 8, 4, 24.2, 10, DARK, 'metal')
    for s in (-1, 1):
        # two scissors arms and the bow
        x = s * 2.4
        m.hexa([(x - 0.25, 24, -9), (x + 0.25, 24, -9), (x + 0.25, 24, -8.5), (x - 0.25, 24, -8.5), (x - 0.25, 29, 0), (x + 0.25, 29, 0), (x + 0.25, 29, 0.5), (x - 0.25, 29, 0.5)], CHROME, 'metal')
        m.hexa([(x - 0.25, 24, 9), (x + 0.25, 24, 9), (x + 0.25, 24, 8.5), (x - 0.25, 24, 8.5), (x - 0.25, 29, 0), (x + 0.25, 29, 0), (x + 0.25, 29, 0.5), (x - 0.25, 29, 0.5)], CHROME, 'metal')
    m.box(-5.5, 29, -0.8, 5.5, 29.5, 1.2, (50, 50, 54), 'metal')
    couplers(m, 19, -19)
    bogie(m, 'front', 10.5)
    bogie(m, 'rear', -10.5)
    return m


def track_layer():
    m = Mesh('track_layer')
    yel = (216, 170, 40)
    m.box(-7, 5, -15, 7, 7, 15, (68, 62, 54), 'wood')
    m.sym(7, 4.6, -15, 7.3, 7.2, 15, yel, 'metal')
    # stacks of rails and ties
    for y in (7, 8.4, 9.8):
        for x in (-5, -2.5, 0, 2.5, 5):
            m.box(x - 0.7, y, -13, x + 0.7, y + 1.2, 0, (140, 144, 152), 'metal')
    for i in range(6):
        m.box(-6.5, 7, 1.5 + i * 2, 6.5, 8 + (i % 2) * 1, 3 + i * 2, (92, 62, 38), 'wood')
    # crane
    m.box(-3, 7, 8, 3, 18, 13, yel, 'panel')
    m.box(-3.5, 18, 7.5, 3.5, 19, 13.5, (60, 60, 64), 'metal')
    m.hexa([(-1, 15, 13), (1, 15, 13), (1, 15, 14.6), (-1, 15, 14.6), (-0.8, 22, 21), (0.8, 22, 21), (0.8, 22, 22), (-0.8, 22, 22)], yel, 'rivet')
    m.cyl('y', (0, 10, 21.5), 0.2, 12, DARK, n=4, mat='plain')
    m.box(-2, 8.5, 20.5, 2, 10, 22.5, DARK, 'metal')
    m.box(-2.6, 13, 13, 2.6, 16.5, 13.2, GLASS_C, 'plain')
    couplers(m, 15, -15)
    bogie(m, 'front', 8.5)
    bogie(m, 'rear', -8.5)
    return m


def tunnel_bore():
    m = Mesh('tunnel_bore')
    col, dark_c = (196, 142, 34), (56, 58, 64)
    m.box(-7, 4, -16, 7, 6.5, 13, DARK, 'rivet')
    m.loft([(-16, 0, 12, 6.8, 6.8), (12, 0, 12, 7.4, 7.4)], col, n=12, mat='rivet')
    for z in (-10, -2, 6):
        m.loft([(z, 0, 12, 7.4, 7.4), (z + 1, 0, 12, 7.5, 7.5)], dark_c, n=12, mat='metal', caps=False)
    # operator's cabin on top, exhaust
    m.box(-4, 18, -13, 4, 23, -5, (70, 74, 80), 'panel')
    m.box(-3.4, 19.6, -5.05, 3.4, 22, -4.9, GLASS_C, 'plain')
    m.cyl('y', (4, 18, 1), 0.9, 7, DARK, n=6, mat='metal')
    m.sym(7, 8, -14, 7.8, 9, 8, (46, 48, 52), 'metal')
    # head: spins
    m.part('head', (0, 12, 16), 'drill', 2.0)
    m.loft([(12, 0, 12, 7, 7), (18, 0, 12, 8, 8), (21, 0, 12, 5, 5), (27, 0, 12, 0.3, 0.3)], (120, 124, 132), n=8, mat='metal')
    for k in range(4):
        t = k * math.pi / 2
        c_, s_ = math.cos(t), math.sin(t)
        m.hexa([(c_ * 1 - s_, 12 + s_ * 1 + c_, 16), (c_ * 1 + s_, 12 + s_ * 1 - c_, 16), (c_ * 8.6 + s_, 12 + s_ * 8.6 - c_, 16), (c_ * 8.6 - s_, 12 + s_ * 8.6 + c_, 16),
                (c_ * 1 - s_, 12 + s_ * 1 + c_, 22), (c_ * 1 + s_, 12 + s_ * 1 - c_, 22), (c_ * 7 + s_, 12 + s_ * 7 - c_, 19), (c_ * 7 - s_, 12 + s_ * 7 + c_, 19)], (70, 72, 78), 'rivet')
    m.part('body')
    couplers(m, 13, -16.5)
    bogie(m, 'front', 6)
    bogie(m, 'rear', -10)
    return m


def wagon_box():
    """Covered goods van: the chest minecart."""
    m = Mesh('wagon_box')
    col, roof = (120, 66, 42), (84, 88, 92)
    m.box(-6.2, 4.2, -13, 6.2, 6.4, 13, DARK, 'rivet')
    m.rloft([(-13, 7.2, 6.4, 18.6), (13, 7.2, 6.4, 18.6)], col, 'wood')
    m.rloft([(-13.5, 7.7, 18.6, 19.5, 6.4), (13.5, 7.7, 18.6, 19.5, 6.4)], roof, 'metal')
    m.rloft([(-13.5, 6.4, 19.5, 20.5, 3), (13.5, 6.4, 19.5, 20.5, 3)], roof, 'metal')
    for s in (-1, 1):
        xa, xb = sorted((s * 7.2, s * 7.45))
        m.box(xa, 7.2, -4.2, xb, 17.6, 4.2, darker(col, 0.85), 'wood')    # door
        m.box(xa, 7.2, -4.4, xb, 8, 4.4, STEEL, 'metal')
        m.box(xa, 16.8, -4.4, xb, 17.6, 4.4, STEEL, 'metal')
        xc, xd = sorted((s * 7.3, s * 7.6))
        m.box(xc, 17.8, -5.2, xd, 18.2, 5.2, DARK, 'metal')
        m.box(xc, 11.5, 2.6, xd, 13, 3.6, CHROME, 'metal')
        for z in (-12.5, -8.5, 8, 12):
            xe, xf = sorted((s * 7.2, s * 7.35))
            m.box(xe, 6.6, z, xf, 18.4, z + 0.6, STEEL, 'metal')
    couplers(m, 13, -13)
    bogie(m, 'front', 7.5)
    bogie(m, 'rear', -7.5)
    return m


def wagon_hopper():
    """Open hopper: the hopper minecart."""
    m = Mesh('wagon_hopper')
    col = (70, 74, 82)
    m.box(-6.2, 4.2, -13, 6.2, 6, 13, DARK, 'rivet')
    m.rloft([(-13, 5, 8, 19, 7.2), (-11, 7, 5.6, 19, 7.2), (11, 7, 5.6, 19, 7.2), (13, 5, 8, 19, 7.2)], col, 'rivet')
    m.box(-6.4, 18.8, -11.5, 6.4, 19.1, 11.5, (20, 20, 22), 'tread')
    for z in (-9, -3, 3, 9):
        m.sym(7.2, 8, z, 7.5, 19.2, z + 0.8, darker(col, 0.7), 'metal')
    m.box(-7.6, 18.6, -13, 7.6, 19.4, -12.2, darker(col, 0.8), 'metal')
    m.box(-7.6, 18.6, 12.2, 7.6, 19.4, 13, darker(col, 0.8), 'metal')
    m.sym(7.25, 12.5, -7, 7.6, 15.5, 7, (212, 170, 50), 'plain')
    couplers(m, 13, -13)
    bogie(m, 'front', 8)
    bogie(m, 'rear', -8)
    return m


def wagon_passenger():
    """Open carriage with benches and a canopy: the plain minecart that people ride."""
    m = Mesh('wagon_passenger')
    col, trim = (44, 72, 120), (222, 206, 150)
    m.box(-6.2, 4.2, -13, 6.2, 6.4, 13, DARK, 'rivet')
    m.rloft([(-13, 7, 6.4, 8.4), (13, 7, 6.4, 8.4)], col, 'panel')
    m.sym(6.2, 8.4, -13, 7, 12.4, 13, col, 'panel')
    m.box(-7, 8.4, -13, 7, 12.4, -12.2, col, 'panel')
    m.box(-7, 8.4, 12.2, 7, 12.4, 13, col, 'panel')
    m.sym(7.02, 8.6, -12.5, 7.2, 9.4, 12.5, trim, 'plain')
    m.box(-5.5, 8.4, -3.5, 5.5, 9.7, 3.5, (120, 60, 40), 'cloth')
    m.box(-5.5, 9, -4.5, 5.5, 17, -3.5, (120, 60, 40), 'cloth')
    m.box(-5.5, 8.4, 8, 5.5, 9.7, 11, (120, 60, 40), 'cloth')
    m.box(-5.5, 9, 6.8, 5.5, 15, 7.8, (120, 60, 40), 'cloth')
    m.box(-5.5, 8.4, -11, 5.5, 9.7, -8, (120, 60, 40), 'cloth')
    m.box(-5.5, 9, -12, 5.5, 15, -11, (120, 60, 40), 'cloth')
    for x in (-6.6, 6.6):
        for z in (-12.4, 12.4):
            m.box(x - 0.35, 12.4, z - 0.35, x + 0.35, 28, z + 0.35, CHROME, 'metal')
    m.rloft([(-14, 7.8, 28, 28.6, 6.8), (14, 7.8, 28, 28.6, 6.8)], col, 'paint')
    m.rloft([(-14, 6.8, 28.6, 29.6, 3), (14, 6.8, 28.6, 29.6, 3)], darker(col, 0.8), 'paint')
    couplers(m, 13, -13)
    bogie(m, 'front', 8)
    bogie(m, 'rear', -8)
    return m


def wagon_furnace():
    """Small shunter that burns coal: the furnace minecart."""
    m = Mesh('wagon_furnace')
    col = (60, 62, 66)
    m.box(-6.2, 4.2, -12, 6.2, 6.4, 13, DARK, 'rivet')
    m.loft([(-6, 0, 12, 5.6, 5.6), (13, 0, 12, 5.6, 5.6)], col, n=12, mat='rivet')
    m.loft([(13, 0, 12, 5.6, 5.6), (13.6, 0, 12, 4, 4)], (40, 40, 44), n=12, mat='metal')
    m.box(-3, 7, 12.8, 3, 11, 13.8, (235, 120, 40), 'grill', glow=True)
    m.cyl('y', (0, 17, 8), 1.6, 5, DARK, n=8, mat='metal', r2=2.2)
    m.box(-6.6, 6.4, -13, 6.6, 18, -6, (120, 60, 40), 'panel')
    m.box(-7, 18, -14, 7, 19, -5, DARK, 'metal')
    m.box(-5, 13, -6.2, 5, 16.5, -5.9, GLASS_C, 'plain')
    couplers(m, 13.8, -13)
    bogie(m, 'front', 7.5)
    bogie(m, 'rear', -7.5)
    return m


ALL = [steam_loco, electric_loco, track_layer, tunnel_bore, wagon_box, wagon_hopper, wagon_passenger, wagon_furnace]
