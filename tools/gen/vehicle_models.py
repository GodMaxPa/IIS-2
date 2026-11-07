"""Models of road, water and air vehicles (D128). Own design; see mesh_art.py for the units."""
import math

from mesh_art import Mesh, darker, mix, rgb

GLASS_C = (150, 205, 225)
DARK = (34, 36, 40)
STEEL = (150, 155, 165)
CHROME = (205, 210, 218)
BRASS = (196, 150, 60)
RUBBER = (28, 28, 32)
LAMP = (255, 240, 170)
TAIL = (235, 40, 30)


def seat(m, x, z, c=(60, 50, 44), y=6, h=8):
    m.box(x - 4.5, y, z - 4, x + 4.5, y + 2, z + 5, c, 'cloth')
    m.box(x - 4.5, y, z - 5.5, x + 4.5, y + h + 3, z - 3.5, c, 'cloth')
    m.box(x - 3, y + h + 3, z - 5.2, x + 3, y + h + 6, z - 3.8, darker(c, 0.85), 'cloth')


def steam_car():
    """Early road tractor: wood and brass, a vertical boiler, spoked wheels."""
    m = Mesh('steam_car')
    wood, iron = (112, 74, 42), (58, 58, 62)
    m.rloft([(-22, 9, 9, 13), (22, 9, 9, 13)], wood, 'wood')
    m.sym(9, 9, -22, 10, 14, 22, BRASS, 'metal')
    m.box(-9, 13, 2, 9, 15, 20, darker(wood), 'wood')
    # boiler over the rear axle
    m.cyl('y', (0, 13, -14), 7.5, 19, iron, n=14, mat='rivet', cap_color=darker(iron))
    for y in (16, 22, 28):
        m.cyl('y', (0, y, -14), 7.9, 1, BRASS, n=14, mat='metal')
    m.cyl('y', (0, 32, -14), 7.5, 3, iron, r2=3, n=14, mat='metal')
    m.cyl('y', (0, 35, -14), 2.6, 11, DARK, n=10, mat='metal')
    m.cyl('y', (0, 45, -14), 2.6, 2.2, BRASS, r2=4.2, n=10, mat='metal', caps=False)
    m.cyl('y', (0, 47.2, -14), 3.6, 0.5, DARK, n=10, mat='plain')
    m.box(-3, 15, -7, 3, 21, -6, (220, 110, 40), 'grill', glow=True)    # fire door
    # pipes and gauge
    m.cyl('z', (4, 20, -7), 0.9, 15, BRASS, n=6, mat='metal')
    m.cyl('z', (-4, 20, -7), 0.9, 15, BRASS, n=6, mat='metal')
    m.cyl('z', (0, 24, -6.5), 2.2, 0.8, BRASS, n=10, mat='metal', cap_color=(235, 235, 220))
    # bench and canopy posts
    m.box(-8, 15, 0, 8, 17, 8, (90, 40, 34), 'cloth')
    m.box(-8, 17, 0, 8, 25, 1.6, (90, 40, 34), 'cloth')
    m.sym(8.4, 14, 16, 9.4, 32, 17.2, BRASS, 'metal')
    m.sym(8.4, 14, -5, 9.4, 32, -3.8, BRASS, 'metal')
    m.rloft([(-7, 11, 32, 33, 11), (0, 11.4, 33, 35, 9), (10, 11.4, 33, 35, 9), (20, 10.6, 32, 33, 10.6)], (54, 42, 34), 'cloth')
    # front: tiller, lamp
    m.cyl('y', (0, 14, 14), 0.8, 8, iron, n=6, mat='metal')
    m.cyl('x', (-4.5, 22, 14), 0.7, 9, BRASS, n=6, mat='metal')
    m.box(-2.2, 12, 21.5, 2.2, 16.4, 23, BRASS, 'metal')
    m.box(-1.6, 12.6, 23, 1.6, 15.8, 23.3, LAMP, 'plain', glow=True)
    m.box(-11, 7, 15, 11, 8.6, 19, iron, 'metal')    # axles
    m.box(-12, 8, -18, 12, 9.6, -13, iron, 'metal')
    for s in (-1, 1):
        m.wheel(f'front{s}', s * 11.5, 7, 17, 7, 2.2, 'steer', (64, 48, 34), BRASS, spokes=5)
        m.wheel(f'rear{s}', s * 12.8, 10, -15, 10, 2.8, 'wheel', (64, 48, 34), BRASS, spokes=6)
    return m


def quad():
    m = Mesh('quad')
    col, plastic = (190, 60, 28), (34, 34, 38)
    m.rloft([(-15, 6, 6, 10, 5), (-6, 7, 5, 11), (6, 7, 5, 11), (15, 5, 6, 10, 4)], plastic, 'metal')
    m.rloft([(-3, 5, 11, 14.5, 4), (9, 5, 11, 15, 3)], col, 'paint')    # tank
    m.rloft([(-18, 4.2, 11, 14, 3.6), (-3, 4.6, 11.5, 15, 4)], (30, 30, 30), 'cloth')    # seat
    # fenders
    for s in (-1, 1):
        for z, nm in ((13, 'f'), (-13, 'r')):
            m.rloft([(z - 8, 6, 11.5, 12.8), (z - 4, 6, 13.5, 14.5), (z + 4, 6, 13.5, 14.5), (z + 8, 6, 11.5, 12.8)], col, 'paint', caps=True) if False else None
            x0 = 7 if s > 0 else -14
            x1 = 14 if s > 0 else -7
            m.hexa([(x0, 11.4, z - 8), (x1, 11.4, z - 8), (x1, 11.4, z + 8), (x0, 11.4, z + 8), (x0, 13, z - 8), (x1, 13, z - 8), (x1, 13, z + 8), (x0, 13, z + 8)], col, 'paint')
            m.hexa([(x0, 13, z - 5), (x1, 13, z - 5), (x1, 13, z + 5), (x0, 13, z + 5), (x0, 14.6, z - 3), (x1, 14.6, z - 3), (x1, 14.6, z + 3), (x0, 14.6, z + 3)], col, 'paint')
        m.box(s * 5.5 - 1, 4, 10, s * 5.5 + 1, 8, 16, DARK, 'metal')
        m.box(s * 5.5 - 1, 4, -16, s * 5.5 + 1, 8, -10, DARK, 'metal')
    # steering
    m.cyl('y', (0, 14, 9), 1, 7, DARK, n=6, mat='metal')
    m.cyl('x', (-8, 21, 9), 0.8, 16, CHROME, n=6, mat='metal')
    m.sym(7, 20.2, 8.2, 9, 21.8, 9.8, RUBBER, 'rubber')
    # front and rear racks, lamps
    m.box(-7, 15, 14, 7, 16, 22, DARK, 'grill')
    m.box(-7, 15, -24, 7, 16, -16, DARK, 'grill')
    m.sym(2, 11.5, 15.5, 5, 13.8, 16.5, LAMP, 'plain', glow=True)
    m.sym(3, 11, -16.2, 6, 13, -15.5, TAIL, 'plain', glow=True)
    m.box(-6, 5, 15, 6, 7, 18, CHROME, 'metal')
    m.cyl('z', (4, 7.5, -22), 1.3, 7, CHROME, n=8, mat='metal')
    for s in (-1, 1):
        m.wheel(f'front{s}', s * 10.5, 5.5, 13, 5.5, 5, 'steer', RUBBER, (200, 200, 205))
        m.wheel(f'rear{s}', s * 10.5, 5.5, -13, 5.5, 5, 'wheel', RUBBER, (200, 200, 205))
    return m


def motorbike():
    m = Mesh('motorbike')
    col = (28, 70, 150)
    # frame
    m.rloft([(-8, 1.6, 7, 11), (10, 1.6, 9, 14)], DARK, 'metal')
    m.hexa([(-1, 5, -14), (1, 5, -14), (1, 5, -8), (-1, 5, -8), (-1, 7, -14), (1, 7, -14), (1, 11, -8), (-1, 11, -8)], DARK, 'metal')
    # engine
    m.box(-3, 6, -6, 3, 12, 6, (70, 72, 76), 'rivet')
    for i in range(5):
        m.box(-3.4, 7 + i * 1.1, -5, 3.4, 7.5 + i * 1.1, 5, CHROME, 'metal')
    m.cyl('x', (-3.5, 7, -2), 3, 7, (46, 46, 50), n=10, mat='metal')
    # tank and tail
    m.rloft([(-2, 3, 12.5, 15, 2.4), (3, 4.2, 12, 18, 3), (9, 3.4, 12.5, 17.4, 2.4), (13, 2.2, 13.5, 16, 2)], col, 'paint')
    m.box(-0.6, 16.6, 1, 0.6, 18.1, 10, CHROME, 'paint')
    m.rloft([(-14, 3, 13, 15.5), (-2, 3.8, 12.5, 15.2)], (28, 26, 26), 'cloth')    # saddle
    m.rloft([(-19, 2.2, 13.5, 16), (-13, 3, 12, 14.5)], col, 'paint')
    m.box(-2, 13, -19.4, 2, 15, -18.8, TAIL, 'plain', glow=True)
    # fork, bars, lamp
    m.part('fork', (0, 16, 11), 'steerbar')
    m.sym(1.4, 9, 11.5, 2.2, 19, 12.4, CHROME, 'metal')
    m.cyl('x', (-7, 20, 11), 0.8, 14, CHROME, n=6, mat='metal')
    m.sym(5.5, 19.3, 10.3, 8, 20.7, 11.7, RUBBER, 'rubber')
    m.cyl('z', (0, 16.5, 13), 2.8, 2.5, CHROME, n=10, mat='metal')
    m.cyl('z', (0, 16.5, 15.5), 2.3, 0.3, LAMP, n=10, mat='plain', glow=True)
    m.part('body')
    # exhaust
    m.cyl('z', (3.8, 6, -16), 1.3, 22, CHROME, n=8, mat='metal')
    m.cyl('z', (-3.8, 6, -16), 1.3, 22, CHROME, n=8, mat='metal')
    # fenders
    m.rloft([(-22, 1.8, 11.5, 12.2), (-16, 1.8, 12.8, 13.5), (-10, 1.8, 11.5, 12.2)], col, 'paint')
    m.wheel('front', 0, 6.5, 17, 6.5, 2.6, 'steer', RUBBER, CHROME, spokes=4)
    m.wheel('rear', 0, 6.5, -16, 6.5, 3.2, 'wheel', RUBBER, CHROME, spokes=4)
    return m


def car():
    """A saloon with a real glasshouse: the driver looks over the bonnet through the windscreen."""
    m = Mesh('car')
    col, low = (156, 34, 34), (118, 24, 24)
    m.rloft([(-37, 13.5, 6, 12.5, 12), (-34, 15.5, 4.5, 15, 14), (-22, 16, 4.5, 16, 15), (10, 16, 4.5, 16, 15), (20, 16, 4.5, 15.5, 14.5), (32, 15.5, 4.5, 13.5, 13), (37, 13.5, 6, 11.5, 10)], col, 'paint')
    m.box(-15.5, 3.6, -28, 15.5, 4.6, 28, DARK, 'metal')    # floor
    # glasshouse
    m.rloft([(-27, 14, 15.5, 16, 13.2), (-20, 14.6, 16, 27, 11.5), (5, 14.6, 16, 27, 11.5), (17, 14.6, 15.5, 16, 13.4)], GLASS_C, 'plain', glass=True)
    m.rloft([(-20.5, 12, 27, 28, 11), (-16, 12.6, 27, 28.4, 11.5), (2, 12.6, 27, 28.4, 11.5), (5.5, 12, 27, 28, 11)], col, 'paint')    # roof
    for x in (-1, 1):
        # pillars
        m.hexa([(x * 13.6 - 0.6, 15.5, 16.5), (x * 13.6 + 0.6, 15.5, 16.5), (x * 13.6 + 0.6, 15.5, 17.6), (x * 13.6 - 0.6, 15.5, 17.6), (x * 11.4 - 0.6, 27.5, 5), (x * 11.4 + 0.6, 27.5, 5), (x * 11.4 + 0.6, 27.5, 6.2), (x * 11.4 - 0.6, 27.5, 6.2)], low, 'paint')
        m.hexa([(x * 13.8 - 0.6, 15.5, -7.4), (x * 13.8 + 0.6, 15.5, -7.4), (x * 13.8 + 0.6, 15.5, -6.2), (x * 13.8 - 0.6, 15.5, -6.2), (x * 11.6 - 0.6, 27.5, -7.4), (x * 11.6 + 0.6, 27.5, -7.4), (x * 11.6 + 0.6, 27.5, -6.2), (x * 11.6 - 0.6, 27.5, -6.2)], low, 'paint')
        m.hexa([(x * 13.6 - 0.6, 15.5, -27), (x * 13.6 + 0.6, 15.5, -27), (x * 13.6 + 0.6, 15.5, -25.8), (x * 13.6 - 0.6, 15.5, -25.8), (x * 11.4 - 0.6, 27.5, -20.5), (x * 11.4 + 0.6, 27.5, -20.5), (x * 11.4 + 0.6, 27.5, -19.3), (x * 11.4 - 0.6, 27.5, -19.3)], low, 'paint')
        # door lines, handles
        xs = x * 16.05
        for z in (-22, -6.6, 11):
            m.box(xs - 0.05, 6, z, xs + 0.05, 15.5, z + 0.4, darker(col, 0.45), 'plain')
        m.box(xs - 0.1, 12.6, 7, xs + 0.1, 13.4, 10, CHROME, 'metal')
        m.box(xs - 0.1, 12.6, -11, xs + 0.1, 13.4, -8, CHROME, 'metal')
        m.box(xs - 0.1, 7.6, -22, xs + 0.1, 8.3, 11, CHROME, 'metal')
        # mirrors
        m.box(x * 14.8, 16.5, 12, x * 18 if x > 0 else x * 14.8 - 3.2, 18.2, 13.4, low, 'paint') if x > 0 else m.box(x * 18, 16.5, 12, x * 14.8, 18.2, 13.4, low, 'paint')
        # lights
    m.sym(9, 9.6, 36.6, 14, 12.2, 37.2, LAMP, 'plain', glow=True)
    m.sym(9, 9.6, -37.2, 14, 12, -36.6, TAIL, 'plain', glow=True)
    # grille, bumpers, plate
    m.box(-8, 7.4, 36.8, 8, 11, 37.2, (50, 52, 56), 'grill')
    m.box(-15, 4.4, 36.4, 15, 6.6, 38.4, CHROME, 'metal')
    m.box(-15, 4.4, -38.4, 15, 6.6, -36.4, CHROME, 'metal')
    m.box(-4, 8, -37.6, 4, 10, -37, (230, 230, 225), 'plain')
    m.box(-4, 4.6, 38.4, 4, 6.4, 38.7, (230, 230, 225), 'plain')
    # bonnet and boot lines
    m.box(-11, 15.02, 19, 11, 15.2, 19.5, darker(col, 0.5), 'plain')
    m.box(-0.3, 15.0, 19.5, 0.3, 15.2, 33, darker(col, 0.8), 'plain')
    m.box(-12, 15.05, -28, 12, 15.2, -27.5, darker(col, 0.5), 'plain')
    # cabin: seats, dash and wheel are seen from inside
    m.sym(3, 6, -3, 12.5, 7.5, 5, (60, 50, 44), 'cloth')
    m.sym(3, 7, -4.5, 12.5, 20, -3, (60, 50, 44), 'cloth')
    m.box(-12.5, 6, -19, 12.5, 7.5, -11, (60, 50, 44), 'cloth')
    m.box(-12.5, 7, -20.5, 12.5, 18, -19, (60, 50, 44), 'cloth')
    m.box(-14.5, 12, 11, 14.5, 16.5, 16, (30, 30, 32), 'cloth')
    m.cyl('z', (7.8, 15.5, 9.8), 3.2, 0.6, (24, 24, 26), n=12, mat='plain')
    m.cyl('z', (7.8, 13, 10.4), 0.5, 2, DARK, n=6, mat='metal')
    for s in (-1, 1):
        for z, nm, an in ((23, 'front', 'steer'), (-23, 'rear', 'wheel')):
            xa, xb = sorted((s * 16.02, s * 16.12))
            m.box(xa, 2.2, z - 7, xb, 12.2, z + 7, DARK, 'plain')
            m.wheel(f'{nm}{s}', s * 15.2, 5.5, z, 5.5, 4.2, an, RUBBER, CHROME)
    return m


def truck():
    """A cab-over lorry with a canvas-free steel box and twin rear wheels."""
    m = Mesh('truck')
    cab, box_c, frame = (44, 92, 150), (196, 196, 188), (40, 40, 44)
    m.box(-11, 7, -52, 11, 11, 48, frame, 'rivet')
    # cab
    m.rloft([(26, 17.5, 10, 24, 17), (47, 17.5, 10, 24, 17), (50, 16, 11, 22, 15.5)], cab, 'paint')
    m.rloft([(26, 17, 24, 40, 15.5), (40, 17, 24, 40, 15), (46.5, 16, 24, 38, 14)], GLASS_C, 'plain', glass=True)
    m.rloft([(26, 16, 39.6, 42, 15), (46.5, 15, 37.6, 40.6, 13.6)], cab, 'paint')
    m.box(-15.5, 41.6, 28, 15.5, 43.6, 40, darker(cab, 0.85), 'panel')    # visor / sleeper top
    m.box(-17.2, 24, 26, 17.2, 40, 31, cab, 'paint')    # rear of cab
    for s in (-1, 1):
        m.hexa([(s * 17 - 0.7, 24, 45), (s * 17 + 0.7, 24, 45), (s * 17 + 0.7, 24, 46.6), (s * 17 - 0.7, 24, 46.6), (s * 15.4 - 0.7, 39, 38.5), (s * 15.4 + 0.7, 39, 38.5), (s * 15.4 + 0.7, 39, 40), (s * 15.4 - 0.7, 39, 40)], darker(cab, 0.7), 'paint')
        m.box(s * 17.5 - 0.1, 12, 33, s * 17.5 + 0.1, 24, 33.4, darker(cab, 0.5), 'plain')
        m.box(s * 17.5 - 0.1, 12, 44, s * 17.5 + 0.1, 24, 44.4, darker(cab, 0.5), 'plain')
        m.box(s * 17.5 - 0.15, 18.6, 34.5, s * 17.5 + 0.15, 19.6, 38, CHROME, 'metal')
        # mirror on an arm, step
        xm0, xm1 = sorted((s * 17, s * 20))
        m.box(xm0, 33, 43, xm1, 34, 44, DARK, 'metal')
        xm0, xm1 = sorted((s * 19.5, s * 21))
        m.box(xm0, 28, 42.5, xm1, 35, 44, DARK, 'metal')
        xs0, xs1 = sorted((s * 15, s * 18.6))
        m.box(xs0, 7.5, 34, xs1, 9, 42, (150, 150, 155), 'rivet')
    m.box(-12, 12, 50.1, 12, 22, 50.6, (46, 48, 52), 'grill')
    m.sym(11, 12.5, 50, 15.5, 16, 50.8, LAMP, 'plain', glow=True)
    m.box(-17, 8, 50.2, 17, 11.5, 53, (120, 124, 130), 'metal')    # bumper
    m.box(-16, 40.6, 40.5, 16, 41.6, 42, LAMP, 'grill', glow=True)
    # seats inside
    m.sym(3, 24, 31, 14, 25.5, 38, (50, 44, 40), 'cloth')
    m.sym(3, 24, 31, 14, 37, 32.5, (50, 44, 40), 'cloth')
    m.box(-15, 27, 42, 15, 30, 45.5, (28, 28, 30), 'cloth')
    m.cyl('y', (8, 28, 41), 3.6, 0.6, (24, 24, 26), n=12, mat='plain')
    # exhaust stack behind the cab
    m.cyl('y', (15, 12, 23), 1.5, 34, CHROME, n=8, mat='metal')
    m.cyl('z', (14, 13, 2), 3.4, 18, (58, 60, 66), n=10, mat='rivet')    # tank
    # cargo box
    m.rloft([(-52, 18, 16, 46), (22, 18, 16, 46)], box_c, 'panel')
    m.box(-18.2, 14, -52.4, 18.2, 16.4, 22.4, (100, 104, 110), 'rivet')
    for z in range(-48, 22, 12):
        m.sym(18, 16, z, 18.4, 46, z + 1, darker(box_c, 0.75), 'metal')
    m.box(-18.2, 26, -40, 18.2, 34, 12, (205, 110, 34), 'plain') if False else None
    m.sym(18.05, 26, -44, 18.2, 34, 16, (205, 110, 34), 'plain')
    m.box(-17, 18, -52.3, 17, 44, -52, darker(box_c, 0.8), 'panel')
    m.box(-0.3, 18, -52.5, 0.3, 44, -52.2, DARK, 'plain')
    m.sym(4, 20, -52.6, 5, 42, -52.3, (120, 124, 130), 'metal')
    m.sym(13, 12, -52.5, 16.5, 15, -52, TAIL, 'plain', glow=True)
    m.box(-17, 8, -53, 17, 10.5, -51, (70, 72, 76), 'rivet')
    for s in (-1, 1):
        m.wheel(f'front{s}', s * 15.5, 7, 37, 7, 5, 'steer', RUBBER, (210, 200, 70))
        m.wheel(f'rear{s}', s * 16, 7, -30, 7, 5.5, 'wheel', RUBBER, (210, 200, 70))
        m.wheel(f'mid{s}', s * 16, 7, -14, 7, 5.5, 'wheel', RUBBER, (210, 200, 70))
    return m


def boat():
    m = Mesh('boat')
    white, stripe, deck = (236, 236, 230), (24, 84, 150), (150, 104, 62)
    hull = [(-32, 13, 6, 15, 15), (-16, 14, 3, 15.5, 15.5), (6, 13, 1.5, 15.5, 15), (20, 9, 3.5, 16.5, 11), (30, 3.5, 8, 17.5, 6), (36, 0.6, 12, 18, 2)]
    m.rloft(hull, white, 'paint')
    # stripe along the waterline
    m.rloft([(-32.1, 13.3, 8, 10, 13.4), (-16, 14.3, 7, 9, 14.4), (6, 13.4, 6, 8, 14), (20, 9.6, 7, 9, 10.2), (30, 4, 9, 11, 4.6)], stripe, 'paint', caps=False)
    m.rloft([(-31, 13, 15, 15.6, 13), (6, 13.4, 15.5, 16.1, 13.4), (19, 9.4, 16.5, 17.1, 9.4), (29, 4.8, 17.5, 18.1, 4.8)], deck, 'wood', caps=False)
    # cockpit tub
    m.box(-11, 15.6, -20, 11, 15.8, 8, (70, 60, 52), 'cloth')
    m.rloft([(9, 12, 16, 18, 11.5), (13, 10, 16.5, 25, 8)], GLASS_C, 'plain', glass=True)
    m.box(-11.6, 25, 12.6, 11.6, 25.8, 13.6, CHROME, 'metal')
    m.sym(9.4, 17, 11, 10.4, 25.4, 13, CHROME, 'metal')
    m.sym(3, 16, -3, 11, 17.5, 4, (190, 70, 50), 'cloth')
    m.sym(3, 16, -4.5, 11, 25, -3, (190, 70, 50), 'cloth')
    m.box(-11, 16, -19, 11, 17.5, -11, (190, 70, 50), 'cloth')
    m.box(-11, 16, -20.5, 11, 23, -19, (190, 70, 50), 'cloth')
    m.box(-11, 16, 6, 11, 20, 9, (40, 40, 44), 'cloth')
    m.cyl('z', (6.5, 20, 5.6), 2.8, 0.6, (24, 24, 26), n=12, mat='plain')
    # rails, lamp
    m.sym(12.5, 17, -28, 13, 18, -8, CHROME, 'metal')
    m.sym(0.3, 18, 24, 1, 19.5, 26, (40, 230, 70), 'plain', glow=True)
    # outboard motor
    m.box(-4, 8, -38, 4, 18, -32, (32, 34, 38), 'paint')
    m.rloft([(-38, 4, 18, 22, 3), (-32.5, 4, 18, 21, 3)], (200, 60, 30), 'paint')
    m.box(-1.2, 0, -37, 1.2, 8, -34, (50, 52, 56), 'metal')
    m.part('prop', (0, 1.5, -38), 'prop', 3.0)
    for i in range(3):
        t = math.radians(120 * i)
        c, s_ = math.cos(t), math.sin(t)
        m.quad((0, 1.5, -38.3), (c * 3.4 - s_ * 0.8, 1.5 + s_ * 3.4 + c * 0.8, -38.3), (c * 3.4 + s_ * 0.8, 1.5 + s_ * 3.4 - c * 0.8, -37.7), (0, 1.5, -37.7), CHROME, 'metal')
    return m


def plane():
    """A light monoplane with an open cockpit, tail wheel and a two-bladed propeller."""
    m = Mesh('plane')
    col, wing, acc = (214, 196, 60), (222, 208, 92), (52, 62, 130)

    def body(t):
        return acc if abs(math.sin(t)) < 0.25 else col

    m.loft([(-68, 0, 25, 1, 1.2), (-44, 0, 23, 4.6, 5.2), (-16, 0, 20, 8.6, 10), (2, 0, 19, 10, 11.6), (24, 0, 18, 10, 11), (34, 0, 18, 8.8, 9.4)], body, n=12)
    m.loft([(34, 0, 18, 8.8, 9.4), (38, 0, 18, 8, 8)], (40, 42, 46), n=12, mat='grill')
    m.cyl('z', (0, 18, 38), 2.6, 5, CHROME, r2=0.6, n=10, mat='metal')
    # exhausts
    m.sym(8.4, 15, 28, 10, 16, 34, DARK, 'metal')
    # cockpit: open tub with a screen
    m.box(-7, 17, -14, 7, 28.4, 8, (40, 36, 34), 'cloth')
    m.rloft([(8, 7.6, 26, 28.5, 7.6), (14, 6, 26.5, 35, 3.4)], GLASS_C, 'plain', glass=True)
    m.cyl('z', (0, 30, -22), 3.2, 8, col, n=8, mat='paint')    # headrest
    m.box(-5.5, 19, -6, 5.5, 20.5, 2, (100, 54, 40), 'cloth')
    m.box(-5.5, 20, -7.5, 5.5, 31, -6, (100, 54, 40), 'cloth')
    m.box(-7, 25, 7, 7, 28, 9, (22, 22, 24), 'cloth')
    # wings
    for s in (-1, 1):
        x0, x1 = (9, 70) if s > 0 else (-70, -9)
        y_in, y_out = 14, 20
        def W(x, z):
            t = abs(x) / 70.0
            return (x, y_in + (y_out - y_in) * t, z)
        c = [W(x0, 2), W(x1, 2 + 2), W(x1, 18 - 2), W(x0, 22)]
        m.hexa([c[0], c[1], c[2], c[3], (c[0][0], c[0][1] + 2.4, c[0][2]), (c[1][0], c[1][1] + 0.8, c[1][2]), (c[2][0], c[2][1] + 0.8, c[2][2]), (c[3][0], c[3][1] + 2.4, c[3][2])], wing, 'panel')
        # wing tip, roundel, strut
        a, b = (56, 69) if s > 0 else (-69, -56)
        m.box(a, 19.4, 5, b, 20.2, 15, acc, 'paint')
        cx = s * 44
        m.cyl('y', (cx, 19.4, 11), 4, 0.1, (235, 235, 235), n=12, mat='plain')
        m.cyl('y', (cx, 19.55, 11), 2, 0.1, acc, n=12, mat='plain')
        # main gear
        xg = s * 11
        m.hexa([(xg - 0.8, 4, 12), (xg + 0.8, 4, 12), (xg + 0.8, 4, 13.4), (xg - 0.8, 4, 13.4), (s * 7 - 0.8, 13, 12), (s * 7 + 0.8, 13, 12), (s * 7 + 0.8, 13, 13.4), (s * 7 - 0.8, 13, 13.4)], DARK, 'metal')
        m.rloft([(9, abs(xg) * 0 + 1.6, 3.5, 7), (17, 1.6, 3.5, 7)], col, 'paint') if False else None
        m.wheel(f'gear{s}', xg, 3.5, 12.7, 3.5, 2, 'wheel', RUBBER, CHROME)
    # tail
    for s in (-1, 1):
        x0, x1 = (0.5, 24) if s > 0 else (-24, -0.5)
        m.hexa([(x0, 24, -68), (x1, 25, -68), (x1, 25, -58), (x0, 24, -52), (x0, 25, -68), (x1, 25.5, -68), (x1, 25.5, -58), (x0, 25, -52)], wing, 'panel')
    m.hexa([(-0.5, 26, -70), (0.5, 26, -70), (0.5, 26, -50), (-0.5, 26, -50), (-0.4, 42, -71), (0.4, 42, -71), (0.4, 42, -62), (-0.4, 42, -62)], acc, 'panel')
    m.box(-0.8, 20, -66, 0.8, 24, -64, DARK, 'metal')
    m.wheel('tailw', 0, 19, -65, 2, 1.2, 'wheel', RUBBER, CHROME) if False else None
    m.wheel('tailw', 0, 2, -55, 2, 1.2, 'wheel', RUBBER, CHROME)
    m.hexa([(-0.5, 2, -55.6), (0.5, 2, -55.6), (0.5, 2, -54.6), (-0.5, 2, -54.6), (-0.5, 19, -58.6), (0.5, 19, -58.6), (0.5, 19, -57.6), (-0.5, 19, -57.6)], DARK, 'metal')
    # propeller
    m.part('prop', (0, 18, 43), 'prop', 5.0)
    for s in (-1, 1):
        m.hexa([(s * 1, 17.2, 42.8), (s * 18, 17.7, 42.8), (s * 18, 17.7, 43.3), (s * 1, 17.2, 43.3), (s * 1, 19, 42.8), (s * 18, 18.3, 42.8), (s * 18, 18.3, 43.3), (s * 1, 19, 43.3)], (46, 40, 34), 'wood')
    m.part('body')
    return m


def helicopter():
    m = Mesh('helicopter')
    col, acc = (44, 86, 62), (220, 200, 60)
    # bubble and cabin
    m.loft([(40, 0, 22, 1.5, 2), (36, 0, 22, 8, 8), (28, 0, 21.5, 13, 13), (18, 0, 21.5, 15, 15), (8, 0, 22, 15, 15.6)], GLASS_C, n=12, mat='plain', glass=True, caps=False)
    m.rloft([(6, 15, 7, 30), (9, 15, 6.6, 12, 15), (30, 11, 8, 12, 11), (38, 4, 13, 14, 4)], col, 'paint')
    m.rloft([(-20, 12, 12, 34, 7), (-10, 15, 8, 37, 9), (8, 15, 6.5, 37.5, 9)], col, 'panel')
    # seats inside the bubble
    m.sym(3, 12, 16, 12, 13.5, 24, (54, 48, 44), 'cloth')
    m.sym(3, 12, 14.5, 12, 26, 16, (54, 48, 44), 'cloth')
    m.box(-12, 11, 28, 12, 17, 31, (26, 26, 28), 'cloth')
    # doors and stripe
    for s in (-1, 1):
        m.box(s * 15.05 - 0.05, 16, -10, s * 15.05 + 0.05, 18, 6, acc, 'plain')
        m.box(s * 15.05 - 0.05, 20, -7, s * 15.05 + 0.05, 28, 3, GLASS_C, 'plain', glass=True)
    # engine cowling and mast
    m.rloft([(-24, 6, 30, 36, 3), (-12, 8, 32, 42, 6), (8, 8, 34, 42, 6)], darker(col, 0.8), 'grill')
    m.cyl('y', (0, 42, -3), 1.8, 7, STEEL, n=8, mat='metal')
    # tail boom
    m.loft([(-20, 0, 26, 7, 7), (-50, 0, 28, 3, 3), (-88, 0, 31, 1.6, 1.8)], col, n=10)
    m.hexa([(-0.5, 28, -92), (0.5, 28, -92), (0.5, 28, -78), (-0.5, 28, -78), (-0.4, 46, -96), (0.4, 46, -96), (0.4, 46, -90), (-0.4, 46, -90)], col, 'panel')
    m.box(-8, 30, -82, 8, 31, -76, acc, 'panel')
    m.box(-1.5, 31, -93, 1.5, 34, -90, DARK, 'metal')
    # skids
    for s in (-1, 1):
        x = s * 14
        m.cyl('z', (x, 1.2, -18), 1.2, 44, DARK, n=8, mat='metal')
        m.cyl('z', (x, 1.2, 26), 1.2, 5, DARK, r2=0.9, n=8, mat='metal')
        for z in (-10, 16):
            m.hexa([(x - 0.8, 1.5, z), (x + 0.8, 1.5, z), (x + 0.8, 1.5, z + 1.6), (x - 0.8, 1.5, z + 1.6), (s * 10 - 0.8, 10, z), (s * 10 + 0.8, 10, z), (s * 10 + 0.8, 10, z + 1.6), (s * 10 - 0.8, 10, z + 1.6)], DARK, 'metal')
    m.box(-2, 7, 37, 2, 9, 38, LAMP, 'plain', glow=True)
    # main rotor
    m.part('rotor', (0, 50, -3), 'rotor', 3.0)
    m.cyl('y', (0, 49, -3), 2.6, 2.2, STEEL, n=8, mat='metal')
    for k in range(4):
        t = math.radians(90 * k)
        c, s_ = math.cos(t), math.sin(t)
        def P(r, w, y):
            return (c * r - s_ * w, y, -3 + s_ * r + c * w)
        m.hexa([P(2, -2, 49.4), P(68, -2, 49.9), P(68, 2, 49.9), P(2, 2, 49.4), P(2, -2, 50), P(68, -2, 50.2), P(68, 2, 50.2), P(2, 2, 50)], (40, 42, 46), 'metal')
        m.hexa([P(60, -2.1, 49.9), P(68.2, -2.1, 49.9), P(68.2, 2.1, 49.9), P(60, 2.1, 49.9), P(60, -2.1, 50.25), P(68.2, -2.1, 50.25), P(68.2, 2.1, 50.25), P(60, 2.1, 50.25)], acc, 'plain')
    # tail rotor
    m.part('tail', (1.6, 39, -91), 'tail', 5.0)
    m.cyl('x', (0.5, 39, -91), 1, 1.6, STEEL, n=6, mat='metal')
    for s in (-1, 1):
        m.quad((1.6, 39, -91 - 0.8), (1.6, 39 + s * 11, -91 - 0.8), (1.6, 39 + s * 11, -91 + 0.8), (1.6, 39, -91 + 0.8), (40, 42, 46), 'metal')
    m.part('body')
    return m


def tank():
    m = Mesh('tank')
    col, dark_c, track = (84, 96, 58), (62, 72, 44), (38, 38, 38)
    # hull
    m.rloft([(-44, 21, 11, 20, 18), (-38, 22, 10, 22, 21), (22, 22, 10, 22, 21), (36, 21, 11, 19, 16), (44, 19, 9, 15, 14)], col, 'plate')
    m.box(-22, 8, -30, 22, 10.5, 30, dark_c, 'metal')
    # engine deck
    m.box(-15, 22, -40, 15, 23, -22, darker(col, 0.8), 'grill')
    m.sym(16, 22, -40, 19, 25, -30, dark_c, 'rivet')
    m.cyl('z', (19.5, 24, -44), 1.8, 12, (60, 60, 60), n=8, mat='metal')
    m.cyl('z', (-19.5, 24, -44), 1.8, 12, (60, 60, 60), n=8, mat='metal')
    # hatch and periscope of the driver
    m.box(-4, 21, 24, 4, 22, 32, dark_c, 'rivet')
    m.box(-2, 22, 28, 2, 23.6, 29, (120, 170, 190), 'plain')
    # tow hooks and spare tracks
    m.sym(9, 13, 44, 12, 15, 46, DARK, 'metal')
    m.box(-18, 15.6, 35.4, -6, 19, 36.4, track, 'tread')
    # tracks on both sides
    for s in (-1, 1):
        x0, x1 = sorted((s * 22, s * 34))
        m.rloft([(-46, 6, 4, 11), (-40, 6, 1, 15), (38, 6, 1, 15), (46, 6, 5, 11)], track, 'tread') if False else None
        def T(y, z):
            return [(x0, y, z), (x1, y, z)]
        prof = [(-46, 7), (-42, 1.5), (40, 1.5), (47, 9), (43, 15), (-42, 15), (-46, 7)]
        for a, b in zip(prof, prof[1:]):
            m.quad((x0, a[1], a[0]), (x1, a[1], a[0]), (x1, b[1], b[0]), (x0, b[1], b[0]), track, 'tread', center=(s * 28, 8, 0))
        xi = s * 34 if s > 0 else s * 34
        xo = s * 22
        for x, nm in ((x0, 'in'), (x1, 'out')):
            m.quad((x, 7, -46), (x, 1.5, -42), (x, 1.5, 40), (x, 7, 47), track, 'plain')
            m.quad((x, 7, 47), (x, 9, 47), (x, 15, 43), (x, 15, -42), track, 'plain')
            m.quad((x, 7, -46), (x, 7, 47), (x, 15, -42), (x, 15, -42), track, 'plain')
        # skirt
        xs0, xs1 = sorted((s * 21.6, s * 34.8))
        m.rloft([(-44, 0, 0, 0)], col) if False else None
        m.hexa([(xs0, 15, -44), (xs1, 15, -44), (xs1, 15, 42), (xs0, 15, 42), (xs0, 17.5, -44), (xs1, 17.5, -44), (xs1, 17.5, 42), (xs0, 17.5, 42)], col, 'plate')
        xo2 = s * 34.8
        for i, z in enumerate(range(-40, 40, 20)):
            xa, xb = sorted((s * 34.8, s * 35.2))
            m.box(xa, 11, z, xb, 16, z + 18, darker(col, 0.86), 'rivet')
        # road wheels
        for i, z in enumerate((-32, -18, -4, 10, 24)):
            m.wheel(f'road{s}_{i}', s * 28, 6, z, 5.4, 11.5, 'wheel', (56, 58, 50), (120, 124, 110))
        m.wheel(f'drive{s}', s * 28, 9, 40, 5, 11.5, 'wheel', (40, 40, 40), (110, 110, 100), spokes=4)
        m.wheel(f'idle{s}', s * 28, 8, -41, 4.6, 11.5, 'wheel', (56, 58, 50), (120, 124, 110))
    # turret
    m.part('turret', (0, 22, -2), 'turret')
    m.rloft([(-20, 13, 22, 33, 11), (-12, 17, 22, 35, 14), (10, 17, 22, 35, 14), (20, 10, 23, 33.5, 7)], col, 'plate')
    m.cyl('y', (0, 22, -2), 17, 1.4, dark_c, n=14, mat='metal')
    m.box(-15, 22.4, -24, 15, 31, -18, dark_c, 'rivet')    # bustle
    m.cyl('y', (-7, 35, -4), 5, 2.4, dark_c, n=10, mat='rivet')    # cupola
    m.box(-9, 37.4, -6, -5, 38.6, -2, (120, 170, 190), 'plain')
    m.cyl('y', (8, 35, -8), 0.6, 18, DARK, n=6, mat='metal')    # aerial
    m.box(6, 35, 4, 12, 37, 12, dark_c, 'metal')
    m.part('barrel', (0, 29, 18), 'barrel', 1.0, 'turret')
    m.box(-6, 25.5, 16, 6, 32.5, 24, dark_c, 'plate')
    m.cyl('z', (0, 29, 22), 2.7, 18, col, n=10, mat='metal')
    m.cyl('z', (0, 29, 40), 2.1, 26, col, n=10, mat='metal')
    m.cyl('z', (0, 29, 60), 2.7, 8, darker(col, 0.8), n=10, mat='metal')
    m.cyl('z', (0, 29, 68), 3.2, 3, DARK, n=10, mat='grill')
    m.meta['muzzle'] = [0, 29, 71]
    m.part('body')
    return m


def jet():
    m = Mesh('jet')
    col, dark_c, low = (112, 124, 136), (72, 82, 94), (160, 170, 178)

    def body(t):
        return low if math.sin(t) < -0.3 else col

    m.loft([(84, 0, 16.5, 0.2, 0.2), (72, 0, 16.5, 3, 3), (52, 0, 17, 7, 7.5), (26, 0, 17, 9.5, 10), (-6, 0, 17, 11, 10), (-42, 0, 17, 12.5, 8.6), (-62, 0, 17, 12.5, 7.4)], body, n=14)
    # intakes
    for s in (-1, 1):
        x0, x1 = sorted((s * 8, s * 15))
        m.rloft([(-40, 0, 0, 0)], col) if False else None
        m.hexa([(x0, 10, -26), (x1, 10, -26), (x1, 10, 22), (x0, 10, 22), (x0, 20, -26), (x1, 20, -26), (x1, 20, 12), (x0, 20, 12)], dark_c, 'panel')
        m.box(x0, 10.6, 22, x1, 19, 22.2, (26, 26, 28), 'plain')
    # canopy
    m.loft([(56, 0, 24, 1.5, 0.3), (46, 0, 25, 5.6, 5.4), (28, 0, 26.5, 6.8, 10), (10, 0, 26, 5.6, 6.4), (0, 0, 26, 1.6, 0.8)], GLASS_C, n=12, mat='plain', glass=True, caps=False)
    m.box(-5.6, 22.5, 16, 5.6, 26, 42, (34, 34, 38), 'cloth')
    m.box(-4.4, 24, 24, 4.4, 25.6, 32, (50, 50, 54), 'cloth')
    m.box(-4.4, 25, 22, 4.4, 35, 24, (50, 50, 54), 'cloth')
    # delta wings
    for s in (-1, 1):
        a, b = (10, 72) if s > 0 else (-72, -10)
        tip = s * 72
        root = s * 10
        def W(x, z, y):
            return (x, y, z)
        m.hexa([W(root, 26, 13), W(tip, -38, 12), W(tip, -48, 12), W(root, -48, 13), W(root, 26, 16), W(tip, -38, 12.5), W(tip, -48, 12.5), W(root, -48, 15.5)], col, 'panel')
        m.box(tip - (0.5 if s > 0 else -0.5) - 0.6, 10.6, -48, tip + 0.6, 13, -30, dark_c, 'metal')
        # stabiliser and fin
        m.hexa([(s * 12, 13, -46), (s * 40, 13, -66), (s * 40, 13, -72), (s * 12, 13, -66), (s * 12, 14.6, -46), (s * 40, 13.4, -66), (s * 40, 13.4, -72), (s * 12, 14.6, -66)], dark_c, 'panel')
        m.hexa([(s * 9, 24, -36), (s * 9.8, 24, -36), (s * 9.8, 24, -66), (s * 9, 24, -66), (s * 16, 46, -62), (s * 16.8, 46, -62), (s * 16.8, 46, -72), (s * 16, 46, -72)], col, 'panel')
        # missiles and gear
        xr = s * 44
        m.cyl('z', (xr, 9.6, -30), 1.2, 22, (220, 220, 220), n=8, mat='paint')
        m.cyl('z', (xr, 9.6, -8), 1.2, 4, (200, 50, 40), r2=0.2, n=8, mat='paint')
        m.box(xr - 0.3, 9.8, -22, xr + 0.3, 12.4, -16, DARK, 'metal')
        xg = s * 13
        m.box(xg - 0.8, 3, -22, xg + 0.8, 11, -20.5, DARK, 'metal')
        m.wheel(f'gear{s}', xg, 3, -21, 3, 2.4, 'wheel', RUBBER, CHROME)
    m.box(-0.8, 3, 42, 0.8, 11, 43.4, DARK, 'metal')
    m.wheel('nose', 0, 3, 43, 3, 2, 'steer', RUBBER, CHROME)
    # nozzles and afterburner
    for s in (-1, 1):
        m.cyl('z', (s * 5.8, 16.6, -62), 6, -8, (50, 50, 54), r2=5, n=12, mat='metal', cap_color=(255, 120, 40))
        m.cyl('z', (s * 5.8, 16.6, -70.2), 3.6, 0.2, (255, 170, 60), n=12, mat='plain', glow=True)
    m.box(-0.4, 16, 83, 0.4, 17, 90, DARK, 'metal')    # pitot
    return m


def atv():
    """All-terrain vehicle: high clearance, huge tyres, a roll cage and a roof rack."""
    m = Mesh('atv')
    col, dark_c = (72, 96, 56), (44, 58, 36)
    # frame high above the axles
    m.box(-10, 13, -32, 10, 16, 32, DARK, 'rivet')
    m.rloft([(-34, 17, 15, 26, 16), (-30, 18, 14.5, 28, 17), (14, 18, 14.5, 28, 17), (24, 17.5, 15, 28, 16), (36, 15.5, 16, 26, 13)], col, 'plate')
    # cabin, open to the sides behind the screen
    m.rloft([(-24, 17, 28, 30, 16), (12, 17, 28, 30, 16)], dark_c, 'cloth', caps=False)
    m.rloft([(12, 16.6, 28, 30, 16), (18, 16.2, 28, 42, 14)], GLASS_C, 'plain', glass=True)
    m.rloft([(-26, 16.4, 44, 45.5, 15.4), (19, 16.4, 44, 45.5, 15.4)], dark_c, 'paint')
    for s in (-1, 1):
        for z in (-25, -8, 11):
            xa, xb = sorted((s * 15.6, s * 17))
            m.box(xa, 28, z, xb, 44, z + 1.4, DARK, 'metal')
        xa, xb = sorted((s * 18.05, s * 18.2))
        m.box(xa, 17, -28, xb, 26, 12, darker(col, 0.8), 'panel')
        # jerry cans and snorkel side
    # roof rack with a spare wheel
    m.box(-14, 45.5, -24, 14, 46.5, 16, DARK, 'grill')
    m.cyl('y', (0, 46.5, -8), 8.5, 3.5, RUBBER, n=14, mat='tread', cap_color=(46, 46, 50))
    # snorkel, winch, bull bar, lamps
    m.cyl('y', (15, 26, 14), 1.1, 20, DARK, n=8, mat='metal')
    m.box(-9, 16, 36, 9, 21, 38, (70, 70, 74), 'rivet')
    m.cyl('x', (-6, 18.5, 38.6), 2, 12, (150, 120, 50), n=10, mat='metal')
    m.sym(15, 14, 36, 16.4, 28, 37.4, DARK, 'metal')
    m.box(-16.4, 26.6, 36, 16.4, 28, 37.4, DARK, 'metal')
    m.sym(9, 23, 36, 13.5, 26, 36.6, LAMP, 'plain', glow=True)
    m.sym(11, 22, -34.6, 15, 25, -34, TAIL, 'plain', glow=True)
    m.box(-12, 25, 12.4, 12, 28, 16, (28, 28, 30), 'cloth')
    # seats
    m.sym(3, 15.5, 0, 12, 17, 8, (60, 50, 44), 'cloth')
    m.sym(3, 16, -1.5, 12, 28, 0, (60, 50, 44), 'cloth')
    m.box(-12, 15.5, -15, 12, 17, -7, (60, 50, 44), 'cloth')
    m.box(-12, 16, -16.5, 12, 26, -15, (60, 50, 44), 'cloth')
    m.cyl('z', (7, 26, 11), 3, 0.6, (24, 24, 26), n=12, mat='plain')
    # jacks of the suspension
    for s in (-1, 1):
        for z in (24, -22):
            m.hexa([(s * 8 - 0.9, 15, z - 1), (s * 8 + 0.9, 15, z - 1), (s * 8 + 0.9, 15, z + 1), (s * 8 - 0.9, 15, z + 1), (s * 15 - 0.9, 8.5, z - 1), (s * 15 + 0.9, 8.5, z - 1), (s * 15 + 0.9, 8.5, z + 1), (s * 15 - 0.9, 8.5, z + 1)], (200, 170, 40), 'metal')
        m.wheel(f'front{s}', s * 17.5, 9, 24, 9, 7, 'steer', RUBBER, (200, 170, 40))
        m.wheel(f'rear{s}', s * 17.5, 9, -22, 9, 7, 'wheel', RUBBER, (200, 170, 40))
    return m


ALL = [steam_car, quad, motorbike, car, atv, truck, boat, plane, helicopter, tank, jet]
