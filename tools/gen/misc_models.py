"""Small models (D128): cartridges, wheels and engines as items, shells in flight, the raised parts of armour. Own design; units in mesh_art.py."""
import math

from mesh_art import Mesh, darker

BRASS = (204, 160, 66)
COPPER = (180, 96, 54)
LEAD = (110, 110, 124)
STEEL = (150, 155, 165)
DARK = (34, 36, 40)
RED = (190, 50, 40)
OLIVE = (84, 92, 58)
CHROME = (205, 210, 218)


def powder_cartridge():
    m = Mesh('powder_cartridge')
    m.cyl('z', (0, 0, -4), 2.2, 7, (222, 210, 170), n=10, mat='cloth', cap_color=(190, 176, 130))
    m.cyl('z', (0, 0, 3), 2.2, 2.5, (222, 210, 170), n=10, mat='cloth', r2=0.7)
    m.cyl('z', (0, 0, -0.8), 2.3, 0.6, (120, 80, 50), n=10, mat='plain')
    return m


def _round(name, case, tip, r, l_case, l_tip):
    m = Mesh(name)
    z0 = -(l_case + l_tip) / 2
    m.cyl('z', (0, 0, z0), r * 1.08, 0.6, darker(case, 0.8), n=10, mat='metal')
    m.cyl('z', (0, 0, z0 + 0.6), r, l_case - 0.6, case, n=10, mat='metal')
    m.cyl('z', (0, 0, z0 + l_case), r, l_tip, tip, n=10, mat='metal', r2=r * 0.25)
    return m


def pistol_round():
    return _round('pistol_round', BRASS, COPPER, 1.9, 5, 3)


def rifle_round():
    return _round('rifle_round', BRASS, COPPER, 1.4, 8, 5)


def shotgun_shell():
    m = Mesh('shotgun_shell')
    m.cyl('z', (0, 0, -4.5), 2.5, 2, BRASS, n=10, mat='metal', cap_color=darker(BRASS, 0.8))
    m.cyl('z', (0, 0, -2.5), 2.3, 7, RED, n=10, mat='panel', cap_color=(120, 30, 24))
    return m


def grenade_round():
    m = Mesh('grenade_round')
    m.cyl('z', (0, 0, -4), 3.2, 3, BRASS, n=12, mat='metal', cap_color=darker(BRASS, 0.8))
    m.loft([(-1, 0, 0, 3, 3), (1, 0, 0, 3, 3), (3.4, 0, 0, 2, 2), (4.6, 0, 0, 0.6, 0.6)], OLIVE, n=12)
    m.loft([(1.2, 0, 0, 3.05, 3.05), (1.8, 0, 0, 3.05, 3.05)], (230, 200, 60), n=12, mat='plain', caps=False)
    return m


def rocket_round():
    m = Mesh('rocket_round')
    m.cyl('z', (0, 0, -7), 2.2, 8, OLIVE, n=12, mat='scratch')
    m.loft([(1, 0, 0, 2.5, 2.5), (3, 0, 0, 2.5, 2.5), (6, 0, 0, 1.4, 1.4), (8, 0, 0, 0.3, 0.3)], RED, n=12)
    for k in range(4):
        t = k * math.pi / 2
        c, s = math.cos(t), math.sin(t)
        m.quad((c * 2, s * 2, -7), (c * 4.4, s * 4.4, -8.4), (c * 4.4, s * 4.4, -6), (c * 2, s * 2, -4), DARK, 'metal')
    return m


def tank_shell():
    m = Mesh('tank_shell')
    m.cyl('z', (0, 0, -7), 3.2, 8, BRASS, n=12, mat='metal', cap_color=darker(BRASS, 0.8))
    m.loft([(1, 0, 0, 3.2, 3.2), (3, 0, 0, 3.2, 3.2), (6.5, 0, 0, 1.6, 1.6), (8, 0, 0, 0.4, 0.4)], (70, 74, 60), n=12)
    m.loft([(1.4, 0, 0, 3.25, 3.25), (2, 0, 0, 3.25, 3.25)], RED, n=12, mat='plain', caps=False)
    return m


def wheel_wood():
    m = Mesh('wheel_wood')
    m.wheel('w', 0, 0, 0, 7, 2.5, None, (84, 60, 40), BRASS, spokes=5)
    return m


def wheel_rubber():
    m = Mesh('wheel_rubber')
    m.wheel('w', 0, 0, 0, 7, 5, None, (28, 28, 32), CHROME)
    return m


def _engine(name, block, trim):
    m = Mesh(name)
    m.box(-5, -5, -6, 5, 3, 6, block, 'rivet')
    m.rloft([(-6, 4, 3, 7, 3), (6, 4, 3, 7, 3)], trim, 'paint')
    for z in (-3.5, 0, 3.5):
        m.cyl('y', (2.5, 7, z), 0.7, 1.6, CHROME, n=6, mat='metal')
    m.cyl('x', (-7, -1, 0), 2, 2, darker(block, 0.7), n=10, mat='metal')
    m.cyl('z', (0, -1, 6), 3, 1.6, (40, 40, 44), n=10, mat='grill')
    return m


def engine_steam():
    m = Mesh('engine_steam')
    m.cyl('z', (0, 0, -6), 5, 12, (60, 60, 64), n=12, mat='rivet')
    for z in (-3, 3):
        m.cyl('z', (0, 0, z), 5.3, 0.8, BRASS, n=12, mat='metal')
    m.cyl('y', (0, 4, 2), 1.2, 5, BRASS, n=8, mat='metal')
    m.cyl('x', (3, -2, -3), 2, 4, (178, 118, 58), n=10, mat='metal')
    return m


def engine_lv():
    return _engine('engine_lv', (90, 92, 98), (190, 190, 200))


def engine_mv():
    return _engine('engine_mv', (84, 78, 66), (214, 150, 60))


def engine_hv():
    return _engine('engine_hv', (70, 74, 84), (170, 180, 60))


def engine_ev():
    return _engine('engine_ev', (56, 62, 74), (60, 140, 200))


def jet_turbine():
    m = Mesh('jet_turbine')
    m.loft([(-8, 0, 0, 4, 4), (-2, 0, 0, 5.4, 5.4), (5, 0, 0, 5.4, 5.4), (8, 0, 0, 4.4, 4.4)], (130, 134, 144), n=14, mat='metal', caps=False)
    m.cyl('z', (0, 0, 7.9), 4.4, 0.1, (30, 30, 34), n=14, mat='plain')
    m.cyl('z', (0, 0, -8), 4, 0.1, (255, 150, 60), n=14, mat='plain', glow=True)
    m.cyl('z', (0, 0, 8), 1.2, 1.4, CHROME, n=8, mat='metal', r2=0.3)
    for k in range(8):
        t = k * math.pi / 4
        c, s = math.cos(t), math.sin(t)
        m.quad((c * 1.2, s * 1.2, 7.8), (c * 4.2 - s * 0.8, s * 4.2 + c * 0.8, 8.05), (c * 4.2 + s * 0.8, s * 4.2 - c * 0.8, 8.05), (c * 1.2, s * 1.2, 8), CHROME, 'metal')
    return m


# --- in flight ------------------------------------------------------------------------------------------------------------------
def proj_grenade():
    m = Mesh('proj_grenade')
    m.loft([(-1.6, 0, 0, 1.3, 1.3), (1, 0, 0, 1.3, 1.3), (2.4, 0, 0, 0.4, 0.4)], OLIVE, n=8)
    return m


def proj_rocket():
    m = Mesh('proj_rocket')
    m.cyl('z', (0, 0, -5), 1.3, 5, OLIVE, n=8, mat='metal')
    m.loft([(0, 0, 0, 1.5, 1.5), (2, 0, 0, 1.5, 1.5), (4, 0, 0, 0.3, 0.3)], RED, n=8)
    m.cyl('z', (0, 0, -5.1), 1, 0.1, (255, 190, 80), n=8, mat='plain', glow=True)
    for k in range(4):
        t = k * math.pi / 2
        c, s = math.cos(t), math.sin(t)
        m.quad((c * 1.2, s * 1.2, -5), (c * 2.8, s * 2.8, -6), (c * 2.8, s * 2.8, -4), (c * 1.2, s * 1.2, -3), DARK, 'metal')
    return m


def proj_shell():
    m = Mesh('proj_shell')
    m.loft([(-4, 0, 0, 1.6, 1.6), (2, 0, 0, 1.6, 1.6), (5, 0, 0, 0.3, 0.3)], (70, 74, 60), n=8)
    m.cyl('z', (0, 0, -4.1), 1.2, 0.1, (255, 200, 110), n=8, mat='plain', glow=True)
    return m


def _plasma(name, r, col):
    m = Mesh(name)
    m.loft([(-r * 1.8, 0, 0, 0.1, 0.1), (-r * 0.5, 0, 0, r, r), (r * 0.5, 0, 0, r, r), (r * 1.4, 0, 0, 0.1, 0.1)], col, n=8, mat='plain', glow=True)
    m.loft([(-r * 3.4, 0, 0, 0.05, 0.05), (-r, 0, 0, r * 0.45, r * 0.45), (0, 0, 0, r * 0.45, r * 0.45)], (255, 255, 255), n=6, mat='plain', glow=True)
    return m


def proj_plasma():
    return _plasma('proj_plasma', 1.6, (210, 90, 255))


def proj_plasma_big():
    return _plasma('proj_plasma_big', 4, (90, 240, 255))


# --- raised parts of armour: y up, z forward, origin at the pivot of the body part --------------------------------------------------
def a_tricorn():
    """Three-cornered hat on the head: origin at the neck, the head goes up to y = 8."""
    m = Mesh('a_tricorn')
    brim = (34, 30, 34)
    pts = [(0, 9.5), (6.8, 2), (6.2, -5.5), (0, -6), (-6.2, -5.5), (-6.8, 2)]
    y0, y1 = 6.6, 7.2
    for a, b in zip(pts, pts[1:] + pts[:1]):
        m.hexa([(0, y0, 0), (a[0], y0, a[1]), (b[0], y0, b[1]), (b[0], y0, b[1]), (0, y1, 0), (a[0], y1, a[1]), (b[0], y1, b[1]), (b[0], y1, b[1])], brim, 'cloth')
    m.rloft([(-4.3, 4.3, 7.2, 10, 3.6), (4.3, 4.3, 7.2, 10, 3.6)], brim, 'cloth')
    # raised edges and a gold trim
    for a, b in zip(pts, pts[1:] + pts[:1]):
        m.hexa([(a[0], y1, a[1]), (b[0], y1, b[1]), (b[0] * 0.92, y1, b[1] * 0.92), (a[0] * 0.92, y1, a[1] * 0.92), (a[0], y1 + 0.9, a[1]), (b[0], y1 + 0.9, b[1]), (b[0] * 0.92, y1 + 0.9, b[1] * 0.92), (a[0] * 0.92, y1 + 0.9, a[1] * 0.92)], (198, 160, 70), 'metal')
    m.cyl('z', (2.6, 8.8, 4.25), 1.1, 0.25, (210, 40, 40), n=8, mat='plain')
    return m


def a_musketeer_body():
    """Cross belts and a cartridge pouch: origin at the neck, the body goes down to y = -12."""
    m = Mesh('a_musketeer_body')
    m.box(-3, -11, -2.9, 3, -8, -2.2, (88, 58, 34), 'cloth')
    m.box(-2.2, -10.4, -3, 2.2, -8.6, -2.85, (198, 160, 70), 'metal')
    return m


def a_ballistic_body():
    """Vest with pouches."""
    m = Mesh('a_ballistic_body')
    col, dark_c = (60, 66, 44), (34, 36, 28)
    for x in (-3, 0, 3):
        m.box(x - 1.3, -9.8, 2.4, x + 1.3, -6.2, 3.2, col, 'cloth')
        m.box(x - 1.3, -7, 2.5, x + 1.3, -6.2, 3.3, dark_c, 'plain')
    m.box(-4.4, -12.2, 2.2, 4.4, -10.8, 2.8, dark_c, 'cloth')
    m.box(-3, -8, -3.1, 3, -2, -2.1, col, 'cloth')    # radio on the back
    m.cyl('y', (2.3, -2, -2.6), 0.2, 7, DARK, n=4, mat='plain')
    return m


def _shoulder(name, col, trim, r=3.4, glow=None):
    """Arm plate: origin at the shoulder pivot; the arm hangs down."""
    m = Mesh(name)
    m.rloft([(-r, r * 0.9, 0, 2.6, r * 0.6), (0, r, 0, 3.4, r * 0.7), (r, r * 0.9, 0, 2.6, r * 0.6)], col, 'plate')
    m.box(-r * 0.9, -0.6, -r * 1.01, r * 0.9, 0, r * 1.01, trim, 'metal')
    if glow:
        m.box(-1.2, 1, r + 0.02, 1.2, 1.8, r + 0.1, glow, 'plain', glow=True)
    return m


def a_assault_shoulder():
    return _shoulder('a_assault_shoulder', (84, 88, 98), (206, 130, 40))


def a_power_shoulder():
    return _shoulder('a_power_shoulder', (206, 212, 222), (40, 44, 56), 3.6, (90, 240, 255))


def a_plasma_shoulder():
    return _shoulder('a_plasma_shoulder', (58, 38, 96), (200, 164, 70), 3.9, (240, 130, 255))


def a_assault_body():
    m = Mesh('a_assault_body')
    col = (84, 88, 98)
    m.rloft([(2.1, 4.2, -9, -1, 3.8), (3.1, 3.6, -8.4, -1.6, 3.2)], col, 'plate')
    m.box(-1.6, -6, 3.1, 1.6, -4.4, 3.25, (206, 130, 40), 'plain')
    m.box(-3.4, -8, -3.6, 3.4, -1, -2.1, (60, 64, 70), 'rivet')
    return m


def _back_unit(name, col, trim, glow, tubes=2):
    """Energy pack with a glowing core on the back."""
    m = Mesh(name)
    m.rloft([(-5.2, 3.2, -9, -0.6, 2.6), (-2.1, 4.2, -9.6, 0, 3.6)], col, 'plate')
    m.box(-2.6, -6.4, -5.3, 2.6, -2, -5.2, glow, 'grill', glow=True)
    m.box(-3.2, -7, -5.3, 3.2, -6.4, -5.1, trim, 'metal')
    m.box(-3.2, -2, -5.3, 3.2, -1.4, -5.1, trim, 'metal')
    for s in (-1, 1)[:tubes]:
        m.cyl('y', (s * 3.2, -8, -3.4), 0.8, 8.8, trim, n=8, mat='metal')
        m.cyl('y', (s * 3.2, 0.8, -3.4), 0.8, 2, trim, n=8, mat='metal', r2=0.3)
    return m


def a_power_back():
    return _back_unit('a_power_back', (178, 184, 196), (40, 44, 56), (90, 240, 255))


def a_plasma_back():
    return _back_unit('a_plasma_back', (40, 26, 70), (200, 164, 70), (240, 130, 255))


def a_power_head():
    m = Mesh('a_power_head')
    m.rloft([(-3, 0.5, 8, 9.6), (3, 0.5, 8, 9.3)], (40, 190, 220), 'paint')
    return m


def a_plasma_head():
    m = Mesh('a_plasma_head')
    m.hexa([(-0.4, 8, -5), (0.4, 8, -5), (0.4, 8, 4), (-0.4, 8, 4), (-0.3, 11, -6), (0.3, 11, -6), (0.3, 10, 2), (-0.3, 10, 2)], (200, 164, 70), 'metal')
    m.sym(3.8, 4.5, 3.6, 4.6, 7, 4.6, (240, 130, 255), 'plain', glow=True)
    return m


# --- vehicle parts ----------------------------------------------------------------------------------------------------------------
def _frame(name, col, plates):
    m = Mesh(name)
    m.sym(4, -1, -8, 5.4, 1, 8, col, 'rivet')
    for z in (-6, 0, 6):
        m.box(-4, -0.8, z - 0.7, 4, 0.8, z + 0.7, col, 'metal')
    if plates:
        m.box(-5.4, 1, -8, 5.4, 1.8, 8, darker(col, 0.8), 'plate')
    return m


def chassis_light():
    return _frame('chassis_light', (120, 124, 132), False)


def chassis_heavy():
    return _frame('chassis_heavy', (168, 172, 180), False)


def chassis_armored():
    return _frame('chassis_armored', (70, 74, 80), True)


def vehicle_suspension():
    m = Mesh('vehicle_suspension')
    m.cyl('y', (0, -6, 0), 2.6, 12, (190, 150, 50), n=10, mat='grill')
    m.cyl('y', (0, -7, 0), 1.2, 14, CHROME, n=8, mat='metal')
    m.box(-4, 6, -1.4, 4, 7.4, 1.4, DARK, 'metal')
    m.box(-4, -7.4, -1.4, 4, -6, 1.4, DARK, 'metal')
    return m


def transmission():
    m = Mesh('transmission')
    m.rloft([(-7, 4, -4, 3, 3), (3, 4.4, -4.4, 4, 3.6), (7, 2.6, -2.6, 2.6, 2)], (112, 116, 124), 'rivet')
    m.cyl('z', (0, 0, 7), 1.2, 2.4, CHROME, n=8, mat='metal')
    m.cyl('z', (0, 0, -9), 1.2, 2.4, CHROME, n=8, mat='metal')
    return m


def fuel_tank():
    m = Mesh('fuel_tank')
    m.rloft([(-7, 5, -4, 4, 4), (-5, 6, -5, 5, 5), (5, 6, -5, 5, 5), (7, 5, -4, 4, 4)], (160, 40, 34), 'panel')
    m.cyl('y', (2, 5, 3), 1.6, 1.8, CHROME, n=8, mat='metal')
    m.box(-4, 5, -2, -1.5, 6, 2, DARK, 'metal')
    return m


def drivetrain():
    m = Mesh('drivetrain')
    m.cyl('z', (0, 0, -8), 0.9, 16, STEEL, n=8, mat='metal')
    m.rloft([(-3, 3, -3, 3, 2.6), (3, 3, -3, 3, 2.6)], (90, 92, 98), 'rivet')
    for z in (-7, 7):
        m.cyl('x', (-7, 0, z), 0.8, 14, STEEL, n=6, mat='metal')
        m.cyl('x', (-7, 0, z), 2, 1, DARK, n=8, mat='metal')
        m.cyl('x', (6, 0, z), 2, 1, DARK, n=8, mat='metal')
    return m


def body_panels():
    m = Mesh('body_panels')
    for i, c in enumerate(((156, 34, 34), (130, 30, 30), (156, 34, 34))):
        m.box(-6, -5 + i * 3.2, -7, 6, -4.2 + i * 3.2, 7, c, 'paint')
    m.box(-4, 4.8, -4, 4, 5.2, 4, GLASS, 'plain')
    return m


GLASS = (150, 205, 225)


def dashboard():
    m = Mesh('dashboard')
    m.box(-7, -3, -2, 7, 3, 2, (32, 32, 36), 'cloth')
    for x in (-4, 0, 4):
        m.cyl('z', (x, 0, 2), 1.7, 0.2, CHROME, n=10, mat='metal')
        m.cyl('z', (x, 0, 2.2), 1.4, 0.05, (230, 230, 220), n=10, mat='plain', glow=True)
    return m


def wing():
    m = Mesh('wing')
    m.hexa([(-8, -0.8, -3), (8, -0.5, -2), (8, -0.5, 2), (-8, -0.8, 3), (-8, 0.8, -3), (8, 0.2, -2), (8, 0.2, 2), (-8, 0.8, 3)], (214, 196, 60), 'panel')
    return m


def propeller():
    m = Mesh('propeller')
    m.cyl('z', (0, 0, -1), 1.8, 3, CHROME, n=10, mat='metal', r2=0.4)
    for s in (-1, 1):
        m.hexa([(s * 1, -0.9, -0.4), (s * 8, -0.5, -0.4), (s * 8, -0.5, 0.2), (s * 1, -0.9, 0.2), (s * 1, 1, -0.4), (s * 8, 0.5, -0.4), (s * 8, 0.5, 0.2), (s * 1, 1, 0.2)], (70, 48, 30), 'wood')
    return m


def rotor_blades():
    m = Mesh('rotor_blades')
    for i, y in enumerate((-1.5, 1.5)):
        m.box(-8, y - 0.3, -1.4, 8, y + 0.3, 1.4, (44, 46, 50), 'metal')
        m.box(6, y - 0.32, -1.42, 8, y + 0.32, 1.42, (220, 200, 60), 'plain')
    return m


def landing_gear():
    m = Mesh('landing_gear')
    m.hexa([(-0.9, -2, -0.9), (0.9, -2, -0.9), (0.9, -2, 0.9), (-0.9, -2, 0.9), (-0.9, 7, -2), (0.9, 7, -2), (0.9, 7, 0), (-0.9, 7, 0)], DARK, 'metal')
    m.wheel('w', 0, -3, 0, 4, 3, None, (28, 28, 32), CHROME)
    return m


def track_unit():
    m = Mesh('track_unit')
    prof = [(-8, 0), (-5, -3), (5, -3), (8, 0), (5, 3), (-5, 3), (-8, 0)]
    for a, b in zip(prof, prof[1:]):
        m.quad((-3, a[1], a[0]), (3, a[1], a[0]), (3, b[1], b[0]), (-3, b[1], b[0]), (38, 38, 38), 'tread', center=(0, 0, 0))
    for z in (-5, 0, 5):
        m.cyl('x', (-3.2, 0, z), 2.3, 6.4, (92, 96, 84), n=8, mat='metal')
    return m


def boat_hull():
    m = Mesh('boat_hull')
    m.rloft([(-8, 5, -3, 3, 5), (-2, 5.4, -4, 3, 5.4), (4, 3.6, -3, 3.5, 4), (8, 0.3, 0, 4, 0.8)], (236, 236, 230), 'paint')
    return m


def tank_turret():
    m = Mesh('tank_turret')
    col = (84, 96, 58)
    m.rloft([(-6, 5, -3, 3, 4), (-3, 6, -3, 4, 5), (3, 6, -3, 4, 5), (6, 3.6, -2.6, 3, 2.6)], col, 'plate')
    m.cyl('z', (0, 0, 6), 1.4, 5, col, n=8, mat='metal')
    m.cyl('z', (0, 0, 11), 1.8, 1.4, DARK, n=8, mat='grill')
    return m


def cargo_kit():
    m = Mesh('cargo_kit')
    m.box(-7, -1, -7, 7, 0, 7, DARK, 'grill')
    m.box(-6, 0, -6, 0, 6, 0, (130, 90, 50), 'wood')
    m.box(1, 0, -5, 6.4, 5, 1, (110, 76, 42), 'wood')
    m.box(-5, 0, 1, 2, 4, 6.5, (70, 90, 56), 'cloth')
    return m


def a_cargo_rack():
    """Rack on top of a vehicle with the cargo kit: crates under a net, the renderer scales it to the vehicle."""
    m = Mesh('a_cargo_rack')
    m.box(-10, 0, -10, 10, 1, 10, DARK, 'grill')
    m.box(-9, 1, -9, -1, 8, -1, (130, 90, 50), 'wood')
    m.box(0, 1, -9, 9, 6, -2, (110, 76, 42), 'wood')
    m.box(-9, 1, 0, -2, 6, 9, (84, 96, 58), 'cloth')
    m.box(-1, 1, 1, 9, 9, 9, (140, 98, 54), 'wood')
    for z in (-9.5, 0, 9.5):
        m.box(-10, 9.2, z - 0.2, 10, 9.5, z + 0.2, (40, 40, 44), 'plain')
    return m


# --- gun parts and magazines --------------------------------------------------------------------------------------------------
def _barrel(name, col):
    m = Mesh(name)
    m.cyl('z', (0, 0, -8), 1, 16, col, n=8, mat='metal')
    m.cyl('z', (0, 0, -8), 1.5, 2, darker(col, 0.8), n=8, mat='metal')
    m.cyl('z', (0, 0, 7), 1.3, 1, darker(col, 0.6), n=8, mat='metal')
    return m


def barrel_steel():
    return _barrel('barrel_steel', (112, 114, 122))


def barrel_stainless():
    return _barrel('barrel_stainless', (176, 180, 188))


def barrel_titanium():
    return _barrel('barrel_titanium', (150, 130, 170))


def trigger_group():
    m = Mesh('trigger_group')
    m.box(-2, -1, -5, 2, 3, 5, (60, 62, 68), 'metal')
    m.box(-0.5, -5, 0, 0.5, -1, 1, CHROME, 'metal')
    m.box(-0.5, -6, -3, 0.5, -5, 3, (60, 62, 68), 'metal')
    m.box(-0.5, -6, 3, 0.5, -1, 4, (60, 62, 68), 'metal')
    return m


def gun_spring():
    m = Mesh('gun_spring')
    for i in range(7):
        m.cyl('z', (0, 0, -7 + i * 2), 3, 0.8, (190, 192, 200), n=8, mat='metal', caps=False)
    m.cyl('z', (0, 0, -7), 0.5, 14, (190, 192, 200), n=4, mat='metal')
    return m


def scope():
    m = Mesh('scope')
    m.cyl('z', (0, 0, -7), 1.6, 14, (28, 29, 32), n=10, mat='metal')
    m.cyl('z', (0, 0, 7), 2.4, 1.4, (28, 29, 32), n=10, mat='metal')
    m.cyl('z', (0, 0, 8.45), 2, 0.05, (120, 190, 230), n=10, mat='plain', glow=True)
    m.cyl('z', (0, 0, -8), 2, 1, (28, 29, 32), n=10, mat='metal')
    return m


def _mag(name, col, w, h, curve=0):
    m = Mesh(name)
    for i in range(3):
        y0 = -h / 2 + i * h / 3
        z = curve * (i - 1)
        m.box(-w, y0, -2.4 + z, w, y0 + h / 3 + 0.1, 2.4 + z, col, 'metal')
    m.box(-w * 0.6, h / 2, -1.6 - curve, w * 0.6, h / 2 + 0.8, 1.6 - curve, (204, 160, 66), 'metal')
    return m


def mag_pistol():
    return _mag('mag_pistol', (40, 42, 46), 1.2, 9)


def mag_smg():
    return _mag('mag_smg', (34, 36, 40), 1.2, 13)


def mag_rifle():
    return _mag('mag_rifle', (34, 36, 40), 1.4, 12, 1.4)


def mag_sniper():
    return _mag('mag_sniper', (70, 74, 80), 1.6, 6)


def mag_belt():
    m = Mesh('mag_belt')
    m.box(-5, -4, -7, 5, 4, 7, (84, 92, 58), 'rivet')
    m.box(-2, 4, -3, 2, 4.6, 3, (60, 62, 44), 'metal')
    for z in (-5, -2.5, 0, 2.5, 5):
        m.box(5, 2, z - 0.5, 7, 3, z + 0.5, (204, 160, 66), 'metal')
    return m


ALL = [powder_cartridge, pistol_round, rifle_round, shotgun_shell, grenade_round, rocket_round, tank_shell, wheel_wood, wheel_rubber, engine_steam, engine_lv, engine_mv, engine_hv, engine_ev, jet_turbine,
       proj_grenade, proj_rocket, proj_shell, proj_plasma, proj_plasma_big, chassis_light, chassis_heavy, chassis_armored, vehicle_suspension, transmission, fuel_tank, drivetrain, body_panels, dashboard, wing, propeller, rotor_blades, landing_gear, track_unit, boat_hull, tank_turret, cargo_kit, a_cargo_rack, barrel_steel, barrel_stainless, barrel_titanium, trigger_group, gun_spring, scope, mag_pistol, mag_smg, mag_rifle, mag_sniper, mag_belt, a_tricorn, a_musketeer_body, a_ballistic_body, a_assault_shoulder, a_power_shoulder, a_plasma_shoulder, a_assault_body, a_power_back, a_plasma_back, a_power_head, a_plasma_head]
