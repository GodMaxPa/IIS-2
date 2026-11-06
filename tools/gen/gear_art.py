"""Our own art for the industrial gear (D5, D126): suits on the player, item icons, the backpack model, the reactor blocks and the reactor window.

Everything is drawn here from text pictures, no file is taken from IC2. A picture is a list of rows, one letter a pixel:
K outline, D dark, B base, L light, H highlight, A accent, G glow, V glass, S steel, W white, '.' empty. A palette gives each letter its colour.
"""
import random

from PIL import Image, ImageDraw

P_NANO = {'K': (18, 20, 24), 'D': (42, 46, 54), 'B': (68, 74, 86), 'L': (98, 106, 120), 'H': (146, 156, 170), 'A': (52, 150, 80), 'G': (140, 255, 160), 'V': (30, 90, 50), 'S': (120, 124, 132), 'W': (230, 240, 235)}
P_QUANTUM = {'K': (12, 16, 40), 'D': (26, 40, 92), 'B': (44, 66, 142), 'L': (76, 104, 190), 'H': (146, 176, 240), 'A': (214, 176, 70), 'G': (130, 240, 255), 'V': (20, 90, 130), 'S': (150, 160, 190), 'W': (235, 245, 255)}
P_HAZMAT = {'K': (70, 34, 10), 'D': (170, 84, 20), 'B': (224, 124, 34), 'L': (244, 164, 70), 'H': (255, 204, 130), 'A': (240, 214, 60), 'G': (24, 26, 30), 'V': (96, 176, 196), 'S': (70, 72, 78), 'W': (235, 235, 225)}
P_STEEL = {'K': (34, 36, 40), 'D': (78, 82, 90), 'B': (122, 126, 134), 'L': (160, 164, 172), 'H': (205, 208, 214), 'A': (240, 150, 50), 'G': (255, 220, 110), 'V': (60, 70, 80), 'S': (96, 100, 108), 'W': (240, 240, 240)}
P_LEATHER = {'K': (44, 28, 16), 'D': (84, 56, 32), 'B': (118, 80, 46), 'L': (150, 106, 64), 'H': (186, 142, 96), 'A': (210, 60, 50), 'G': (255, 120, 90), 'V': (70, 20, 20), 'S': (120, 124, 132), 'W': (230, 230, 220)}


def pack_palette(accent, glow):
    p = dict(P_LEATHER)
    p['A'], p['G'] = accent, glow
    p['V'] = tuple(int(v * 0.4) for v in accent)
    return p


P_BATPACK = pack_palette((210, 60, 50), (255, 130, 110))
P_ADV_BATPACK = pack_palette((214, 180, 60), (255, 236, 140))
P_ENERGY_PACK = pack_palette((190, 40, 60), (255, 90, 110))
P_LAPPACK = dict(pack_palette((50, 90, 220), (130, 190, 255)), D=(30, 40, 90), B=(44, 60, 130), L=(70, 90, 170), H=(120, 150, 220), K=(14, 18, 44))


def paint(im, x, y, rows, pal, seed=0):
    """Draws a picture; base and plate pixels get a small grain so large surfaces do not look flat."""
    rnd = random.Random(seed)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == '.':
                continue
            c = pal[ch]
            v = rnd.randint(-3, 3) if ch in 'BLD' else 0
            im.putpixel((x + i, y + j), tuple(max(0, min(255, c[k] + v)) for k in range(3)) + (255,))


def flat(w, h, ch):
    return [ch * w] * h


def box(im, u, v, w, h, d, pal, front=None, back=None, right=None, left=None, top=None, bottom=None, seed=0):
    """Paints a cube on the standard layout: top and bottom in the first row, then right, front, left, back."""
    parts = ((u + d, v, top, w, d), (u + d + w, v, bottom, w, d), (u, v + d, right, d, h), (u + d, v + d, front, w, h), (u + d + w, v + d, left, d, h), (u + d + w + d, v + d, back, w, h))
    for n, (x, y, rows, _, _) in enumerate(parts):
        if rows is not None:
            paint(im, x, y, rows, pal, seed + n)


def mirror(rows):
    return [r[::-1] for r in rows]


# --- nano ---------------------------------------------------------------------------------------------------------------------------
NANO_HEAD = dict(
    front=['LLHHHHLL', 'LBBBBBBD', 'BKKKKKKD', 'BKGGGGKD', 'BKVVVVKD', 'DBKKKKBK', 'DBAKKADK', 'KDDKKDDK'],
    right=['LLLLLLLB', 'BBBBBBBD', 'KKKKBBBD', 'GVKBBABD', 'KKKBBABD', 'DBBBBBDK', 'DDBBDDDK', 'KDDDDDKK'],
    back=['BLLLLLLD', 'BBBDDBBD', 'BBDBBDBD', 'BBDAADBD', 'BBDBBDBD', 'DBBDDBBK', 'DDBBBBDK', 'KDDDDDDK'],
    top=['BLLHHLLB', 'LLBBBBLD', 'LBBDDBBD', 'LBDAADBD', 'LBDAADBD', 'LBBDDBBD', 'BBBBBBBD', 'BDDDDDDD'],
    bottom=flat(8, 8, 'K'))
NANO_BODY = dict(
    front=['DLLBBLLD', 'LBBBBBBD', 'LBAKKABD', 'BBKGGKBD', 'BBKGGKBD', 'BBAKKABD', 'DBBBBBBK', 'KDDBBDDK', 'KBBDDBBK', 'DBBDDBBK', 'KKKKKKKK', 'DAADDAAD'],
    back=['DLLBBLLD', 'LBDDDDBD', 'BBDAADBD', 'BBDBBDBD', 'BBDAADBD', 'BBDDDDBD', 'DBBBBBBK', 'KDBBBBDK', 'KDBDDBDK', 'KDBBBBDK', 'KKKKKKKK', 'DDAAAADD'],
    right=['LBBD', 'LBBD', 'BBDK', 'BBAK', 'BBAK', 'BBDK', 'BBDK', 'DBDK', 'DBDK', 'DBDK', 'KKKK', 'DADK'],
    top=flat(8, 4, 'L'), bottom=flat(8, 4, 'K'))
NANO_ARM = dict(
    front=['LHHB', 'LBBD', 'BBBD', 'KKKK', 'LBBD', 'LABD', 'BABD', 'BBBD', 'KKKK', 'DBBD', 'DGGK', 'KDDK'],
    side=['LHHB', 'LBBD', 'BBBD', 'KKKK', 'LBBD', 'LBBD', 'BBBD', 'BBBD', 'KKKK', 'DBBD', 'DAAK', 'KDDK'],
    top=['HHLB', 'HLLB', 'LLBD', 'BBDD'], bottom=flat(4, 4, 'K'))
NANO_LEG = dict(
    front=['BLBD', 'BLBD', 'DBBK', 'KKKK', 'LHBD', 'LAAD', 'LBBD', 'KKKK', 'BBBD', 'BABD', 'BBBD', 'DDDK'],
    side=['BBBD', 'BBBD', 'DBBK', 'KKKK', 'BBBD', 'BLBD', 'BLBD', 'KKKK', 'BBBD', 'BBBD', 'BBBD', 'DDDK'],
    top=flat(4, 4, 'B'), bottom=flat(4, 4, 'K'))
NANO_WAIST = dict(
    front=['........', '........', '........', '........', '........', '........', '........', 'BBBBBBBB', 'BBBBBBBD', 'BLBDDBBK', 'KKKKKKKK', 'DAADDAAD'],
    side=['....', '....', '....', '....', '....', '....', '....', 'BBBD', 'BBBD', 'BBDK', 'KKKK', 'DADK'],
    back=['........', '........', '........', '........', '........', '........', '........', 'BBBBBBBB', 'BBBBBBBD', 'BBDDDDBK', 'KKKKKKKK', 'DDAAAADD'])
NANO_BOOT = dict(
    front=['....', '....', '....', '....', '....', '....', '....', 'LLLB', 'LAAD', 'BBBD', 'KKKK', 'DDDK'],
    side=['....', '....', '....', '....', '....', '....', '....', 'LLBD', 'LBBD', 'BBDD', 'KKKK', 'DDDK'],
    top=None, bottom=flat(4, 4, 'K'))

# --- quantum -----------------------------------------------------------------------------------------------------------------------
QUANTUM_HEAD = dict(
    front=['AHHHHHHA', 'LLBBBBLD', 'BKKKKKKD', 'KGGGGGGK', 'KGWGGWGK', 'BKKKKKKD', 'DBALLABK', 'KDDAADDK'],
    right=['AHHLLLLB', 'LLBBBBBD', 'KKKBBABD', 'GGKBAABD', 'KKKBBABD', 'DBBBBBDK', 'DBBDDDDK', 'KDDDDDKK'],
    back=['BHHHHHHD', 'LLBAABBD', 'BBDGGDBD', 'BBDGGDBD', 'BBDAADBD', 'DBBBBBBK', 'DDBBBBDK', 'KDDAADDK'],
    top=['BAHHHHAB', 'LBBAABBD', 'LBDGGDBD', 'HADGGDAD', 'HADGGDAD', 'LBDGGDBD', 'LBBAABBD', 'BDDAADDD'],
    bottom=flat(8, 8, 'K'))
QUANTUM_BODY = dict(
    front=['AHLBBLHA', 'LBBBBBBD', 'BBAKKABD', 'BAKGGKAD', 'BAKGGKAD', 'BBAKKABD', 'LBBAABBD', 'KBBBBBBK', 'LBKBBKBD', 'BBKBBKBD', 'KAAAAAAK', 'DBBAABBD'],
    back=['AHLBBLHA', 'LBKKKKBD', 'BBKGGKBD', 'BBKGGKBD', 'BBKGGKBD', 'BBKKKKBD', 'LBBBBBBD', 'KBBAABBK', 'BBBBBBBD', 'BBBBBBBD', 'KAAAAAAK', 'DBBAABBD'],
    right=['ALBD', 'LBBD', 'BBDK', 'BGDK', 'BGDK', 'BBDK', 'BBDK', 'BADK', 'BBDK', 'BBDK', 'KAAK', 'DBDK'],
    top=flat(8, 4, 'L'), bottom=flat(8, 4, 'K'))
QUANTUM_ARM = dict(
    front=['AHHA', 'HLLD', 'LBBD', 'KAAK', 'LBBD', 'LGBD', 'BGBD', 'BBBD', 'KAAK', 'DBBD', 'DGGK', 'KDDK'],
    side=['AHHA', 'HLLD', 'LBBD', 'KAAK', 'LBBD', 'LBBD', 'BBBD', 'BBBD', 'KAAK', 'DBBD', 'DBBK', 'KDDK'],
    top=['AHHA', 'HLLB', 'HLBD', 'ABDA'], bottom=flat(4, 4, 'K'))
QUANTUM_LEG = dict(
    front=['BLBD', 'BGBD', 'DBBK', 'KAAK', 'LHBD', 'LGGD', 'LBBD', 'KAAK', 'BBBD', 'BGBD', 'BBBD', 'DDDK'],
    side=['BBBD', 'BBBD', 'DBBK', 'KAAK', 'BBBD', 'BLBD', 'BLBD', 'KAAK', 'BBBD', 'BBBD', 'BBBD', 'DDDK'],
    top=flat(4, 4, 'B'), bottom=flat(4, 4, 'K'))
QUANTUM_WAIST = dict(
    front=['........', '........', '........', '........', '........', '........', '........', 'BBBBBBBB', 'LBKBBKBD', 'BBKBBKBD', 'KAAAAAAK', 'DBBAABBD'],
    side=['....', '....', '....', '....', '....', '....', '....', 'BBBD', 'BBBD', 'BBDK', 'KAAK', 'DBDK'],
    back=['........', '........', '........', '........', '........', '........', '........', 'BBBBBBBB', 'BBBBBBBD', 'BBBBBBBD', 'KAAAAAAK', 'DBBAABBD'])
QUANTUM_BOOT = dict(
    front=['....', '....', '....', '....', '....', '....', '....', 'AHHA', 'LGGD', 'BBBD', 'KAAK', 'DDDK'],
    side=['....', '....', '....', '....', '....', '....', '....', 'AHLB', 'LBBD', 'BBDD', 'KAAK', 'DDDK'],
    top=None, bottom=flat(4, 4, 'K'))

# --- hazmat: a rubber suit, a round window, a filter and a zip ---------------------------------------------------------------------
HAZMAT_HEAD = dict(
    front=['BLLLLLLB', 'LKKKKKKD', 'LKVVVWKD', 'BKVVVVKD', 'BKKKKKKD', 'BBSSSSBD', 'DBSGGSDK', 'KDSSSSDK'],
    right=['BLLLLLLB', 'LBBBBBBD', 'KBBBBBBD', 'KBBBBBBD', 'BBBAABBD', 'BBBBBBBD', 'DBBBBBDK', 'KDDDDDKK'],
    back=['BLLLLLLD', 'LBBBBBBD', 'BBBBBBBD', 'BBBBBBBD', 'AAAAAAAA', 'BBBBBBBD', 'DBBBBBDK', 'KDDDDDDK'],
    top=['BLLHHLLB', 'LLLLLLLD', 'LLBBBBLD', 'HLBBBBBD', 'HLBBBBBD', 'LLBBBBLD', 'LBBBBBBD', 'BDDDDDDD'],
    bottom=flat(8, 8, 'K'))
HAZMAT_BODY = dict(
    front=['DLLKKLLD', 'LBBKGBBD', 'LBBGKBBD', 'BBAAAABD', 'BBAGGABD', 'BBAGGABD', 'BBAAAABD', 'BBBGKBBK', 'DBBKGBBK', 'DBBGKBBK', 'KAAAAAAK', 'DBBBBBBD'],
    back=['DLLBBLLD', 'LBBBBBBD', 'LBSSSSBD', 'BBSGGSBD', 'BBSGGSBD', 'BBSSSSBD', 'BBBBBBBD', 'BBBBBBBK', 'DBBBBBBK', 'DBBBBBBK', 'KAAAAAAK', 'DBBBBBBD'],
    right=['LBBD', 'LBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'BBBK', 'KAAK', 'DBBK'],
    top=flat(8, 4, 'L'), bottom=flat(8, 4, 'K'))
HAZMAT_ARM = dict(
    front=['LLLB', 'LBBD', 'BBBD', 'BBBD', 'AAAA', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'KKKK', 'SSSK', 'SSSK'],
    side=['LLLB', 'LBBD', 'BBBD', 'BBBD', 'AAAA', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'KKKK', 'SSSK', 'SSSK'],
    top=flat(4, 4, 'L'), bottom=flat(4, 4, 'K'))
HAZMAT_LEG = dict(
    front=['LBBD', 'LBBD', 'BBBD', 'BBBD', 'BBBD', 'AAAA', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'DBBK', 'DBBK'],
    side=['LBBD', 'LBBD', 'BBBD', 'BBBD', 'BBBD', 'AAAA', 'BBBD', 'BBBD', 'BBBD', 'BBBD', 'DBBK', 'DBBK'],
    top=flat(4, 4, 'B'), bottom=flat(4, 4, 'K'))
HAZMAT_WAIST = dict(
    front=['........', '........', '........', '........', '........', '........', '........', 'BBBBBBBB', 'BBBBBBBD', 'DBBBBBBK', 'KAAAAAAK', 'DBBBBBBD'],
    side=['....', '....', '....', '....', '....', '....', '....', 'BBBD', 'BBBD', 'BBBD', 'KAAK', 'DBBK'],
    back=['........', '........', '........', '........', '........', '........', '........', 'BBBBBBBB', 'BBBBBBBD', 'DBBBBBBK', 'KAAAAAAK', 'DBBBBBBD'])
HAZMAT_BOOT = dict(
    front=['....', '....', '....', '....', '....', '....', '....', 'SSSS', 'SSSK', 'SSSK', 'KKKK', 'GGGG'],
    side=['....', '....', '....', '....', '....', '....', '....', 'SSSS', 'SSSK', 'SSSK', 'KKKK', 'GGGG'],
    top=None, bottom=flat(4, 4, 'G'))


def suit_textures(head, body, arm, leg, waist, boot, pal, seed):
    """(humanoid layer: helmet, chest, boots; humanoid_leggings layer: waist and legs)."""
    main = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    box(main, 0, 0, 8, 8, 8, pal, front=head['front'], right=head['right'], left=mirror(head['right']), back=head['back'], top=head['top'], bottom=head['bottom'], seed=seed)
    box(main, 16, 16, 8, 12, 4, pal, front=body['front'], back=body['back'], right=body['right'], left=mirror(body['right']), top=body['top'], bottom=body['bottom'], seed=seed + 10)
    box(main, 40, 16, 4, 12, 4, pal, front=arm['front'], back=arm['side'], right=arm['side'], left=arm['side'], top=arm['top'], bottom=arm['bottom'], seed=seed + 20)
    box(main, 0, 16, 4, 12, 4, pal, front=boot['front'], back=boot['side'], right=boot['side'], left=boot['side'], top=boot['top'], bottom=boot['bottom'], seed=seed + 30)
    legs = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    box(legs, 16, 16, 8, 12, 4, pal, front=waist['front'], back=waist['back'], right=waist['side'], left=mirror(waist['side']), bottom=flat(8, 4, 'K'), seed=seed + 40)
    box(legs, 0, 16, 4, 12, 4, pal, front=leg['front'], back=leg['side'], right=leg['side'], left=leg['side'], top=leg['top'], bottom=leg['bottom'], seed=seed + 50)
    return main, legs


def nano():
    return suit_textures(NANO_HEAD, NANO_BODY, NANO_ARM, NANO_LEG, NANO_WAIST, NANO_BOOT, P_NANO, 100)


def quantum():
    return suit_textures(QUANTUM_HEAD, QUANTUM_BODY, QUANTUM_ARM, QUANTUM_LEG, QUANTUM_WAIST, QUANTUM_BOOT, P_QUANTUM, 200)


def hazmat():
    return suit_textures(HAZMAT_HEAD, HAZMAT_BODY, HAZMAT_ARM, HAZMAT_LEG, HAZMAT_WAIST, HAZMAT_BOOT, P_HAZMAT, 300)


def goggles():
    """Only the strap and the lenses: the head stays visible."""
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    front = ['........', '........', 'KKKKKKKK', 'KGGKKGGK', 'KVGKKVGK', 'KKKKKKKK', '........', '........']
    strap = ['........', '........', 'DDDDDDDD', 'KKKKKKKK', 'DDDDDDDD', '........', '........', '........']
    box(im, 0, 0, 8, 8, 8, P_NANO, front=front, right=strap, left=strap, back=strap, seed=400)
    return im


def harness(pal):
    """A pack on the back: straps over the chest, the pack itself is the model (GearPackLayer); the plate on the back remains in case the model is off."""
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    front = ['.D....D.', '.D....D.', '.D....D.', '.D....D.', '.DAAAAD.', '.DKKKKD.', '.D....D.', '.D....D.', '........', '........', '........', '........']
    back = ['.BBBBBB.', 'BLLLLLLD', 'BLAAAAAD', 'BLGGGGAD', 'BLAAAAAD', 'BLLLLLLD', 'BLAAAAAD', 'BLGGGGAD', 'BLAAAAAD', 'BLLLLLLD', '.DDDDDD.', '........']
    side = ['.D..', '.D..', '.D..', '.D..', '.D..', '.D..', '.D..', '.D..', '....', '....', '....', '....']
    box(im, 16, 16, 8, 12, 4, pal, front=front, back=back, right=side, left=mirror(side), seed=500)
    return im


def jet_harness():
    pal = P_STEEL
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    front = ['.D....D.', '.D....D.', '.D....D.', '.D....D.', '.DSSSSD.', '.DKAAKD.', '.D....D.', '.D....D.', '........', '........', '........', '........']
    back = ['.LL..LL.', 'BLHBBLHD', 'BLBBBLBD', 'BLBSSLBD', 'BLBSSLBD', 'BLBBBLBD', 'BLBBBLBD', 'BLBBBLBD', 'DBBDDBBK', '.KK..KK.', '.AA..AA.', '.GG..GG.']
    side = ['.D..', '.D..', '.D..', '.D..', '.D..', '.D..', '.D..', '.D..', '....', '....', '....', '....']
    box(im, 16, 16, 8, 12, 4, pal, front=front, back=back, right=side, left=mirror(side), seed=600)
    return im


# --- the pack as a real shape ----------------------------------------------------------------------------------------------------------
def pack_model(pal):
    """Texture of the batpack model: one cube 8x10x4 at (0,0), see GearPackLayer."""
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    front = ['KKKKKKKK', 'KDDDDDDK', 'KDBBBBDK', 'KDBBBBDK', 'KDBBBBDK', 'KDBBBBDK', 'KDBBBBDK', 'KDBBBBDK', 'KDDDDDDK', 'KKKKKKKK']
    back = ['DLLLLLLD', 'LKKKKKKD', 'LKGAGAKD', 'LKAGAGKD', 'LKKKKKKD', 'LSSSSSSD', 'LKKKKKKD', 'LKGAGAKD', 'LKKKKKKD', 'DDDDDDDD']
    side = ['DLLD', 'LBBD', 'LABD', 'LABD', 'LBBD', 'LBBD', 'LABD', 'LABD', 'LBBD', 'DDDD']
    box(im, 0, 0, 8, 10, 4, pal, front=front, back=back, right=side, left=mirror(side), top=['DLLLLLLD', 'LHHHHHHD', 'LHLLLLBD', 'DDDDDDDD'], bottom=flat(8, 4, 'K'), seed=700)
    return im


def jet_model():
    """Texture of the jetpack model: a tank 3x10x3 at (0,0) drawn twice, a nozzle 2x3x2 at (0,16), a flame 2x4x2 at (16,16)."""
    pal = P_STEEL
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    side = ['LHB', 'LHB', 'LBD', 'KKK', 'LBD', 'LBD', 'SSS', 'LBD', 'KKK', 'DDK']
    box(im, 0, 0, 3, 10, 3, pal, front=side, back=side, right=side, left=side, top=['LHB', 'HHB', 'LBD'], bottom=flat(3, 3, 'K'), seed=800)
    noz = ['KK', 'DD', 'SS']
    box(im, 0, 16, 2, 3, 2, pal, front=noz, back=noz, right=noz, left=noz, top=flat(2, 2, 'K'), bottom=flat(2, 2, 'A'), seed=810)
    flame = ['AG', 'GA', 'AG', '.A']
    box(im, 16, 16, 2, 4, 2, pal, front=flame, back=flame, right=flame, left=flame, seed=820)
    return im


# --- icons ------------------------------------------------------------------------------------------------------------------------------
I_HELMET = ['................', '................', '....KKKKKKKK....', '...KHHLLLLLBK...', '..KHLBBBBBBBDK..', '..KLBBBBBBBBDK..', '..KLKKKKKKKKDK..', '..KBKGGGGGVKDK..', '..KBKGGVVVVKDK..', '..KBBKKKKKKBDK..',
            '..KDBBDKKDBDDK..', '..KKDDK..KDDKK..', '...KKK....KKK...', '................', '................', '................']
I_CHEST = ['...KKK....KKK...', '..KHHLK..KLLBK..', '.KHLLLBKKBBBBDK.', '.KLBBBBBBBBBBDK.', '.KLBKBLLLLBKBDK.', '.KKKKBBAABBKKKK.', '...KBBAGGABDK...', '...KLBAGGABDK...', '...KLBBAABBDK...', '...KLBBBBBBDK...',
           '...KBBKKKKBDK...', '...KAAAAAAAAK...', '...KDBBDDBBDK...', '...KKKKKKKKKK...', '................', '................']
I_LEGS = ['................', '...KKKKKKKKKK...', '...KAAAAAAAAK...', '...KLBBBBBBDK...', '...KLBBKKBBDK...', '...KLBDKKLBDK...', '...KHBDKKLBDK...', '...KKKKKKKKKK...', '...KABDKKABDK...', '...KLBDKKLBDK...',
          '...KLBDKKLBDK...', '...KLBDKKLBDK...', '...KDDDKKDDDK...', '...KKKKKKKKKK...', '................', '................']
I_BOOTS = ['................', '................', '................', '................', '...KKKKKKKKKK...', '...KHLDKKHLDK...', '...KLBDKKLBDK...', '...KABDKKABDK...', '...KLBDKKLBDK...', '..KLLBDKKLLBDK..',
           '.KHLBBDKKHLBBDK.', '.KLBBBDKKLBBBDK.', '.KKKKKKKKKKKKKK.', '.KDDDDKKKKDDDDK.', '..KKKK....KKKK..', '................']
I_GOGGLES = ['................', '................', '................', '................', '................', '.KKKKKKKKKKKKKK.', 'KDKKKKKKKKKKKKDK', 'KKKGGVKKKKGGVKKK', 'KDKGVVKDDKGVVKDK', 'KKKKKKKDDKKKKKKK',
             '.KKKKKKKKKKKKKK.', '................', '................', '................', '................', '................']
I_JET = ['................', '...KKK....KKK...', '..KHLBK..KHLBK..', '.KHLBBDKKHLBBDK.', '.KLBBBDKKLBBBDK.', '.KKKKKKSSKKKKKK.', '.KLBBBDSSLBBBDK.', '.KLBBBDKKLBBBDK.', '.KSSSSDKKSSSSDK.', '.KLBBBDKKLBBBDK.',
         '.KDBBDDKKDBBDDK.', '..KKKKK..KKKKK..', '...KDK....KDK...', '...AGA....AGA...', '...GAG....GAG...', '....A......A....']
I_PACK = ['................', '....KK....KK....', '...KDDK..KDDK...', '..KKKKKKKKKKKK..', '..KHLLLLLLLLDK..', '..KLKKKKKKKKDK..', '..KLKGAGAGAKDK..', '..KLKAGAGAGKDK..', '..KLKKKKKKKKDK..', '..KLBBBSSBBBDK..',
          '..KLKKKKKKKKDK..', '..KLKGAGAGAKDK..', '..KLKKKKKKKKDK..', '..KDDDDDDDDDDK..', '..KKKKKKKKKKKK..', '................']
I_LASER = ['................', '................', '................', '................', '.KKKKKKKKKKK....', 'KHHLLLLLLLLBKKKK', 'KLLBBBBBBBBBKGGK', 'KBBAABAABDDDKAAK', 'KDDDDDDDDDDDKKKK', '.KKKKKKKDBBDK...',
           '......KKDBBK....', '.....KDBBDKK....', '.....KBBDK......', '....KBBDK.......', '....KKKK........', '................']
I_SABER = ['..............KK', '.............KGW', '............KGGK', '...........KGGK.', '..........KGGK..', '.........KGGK...', '........KGGK....', '.......KGGK.....', '......KGGK......', '...KK.KGK.......',
           '...KSKSKK.......', '....KSLK........', '...KLKSSK.......', '..KLBK.KK.......', '.KLBK...........', '.KKK............']
I_BATTERY = ['................', '.......KK.......', '......KSSK......', '.....KKKKKK.....', '.....KHLBDK.....', '.....KLBBDK.....', '.....KAAAAK.....', '.....KAGAAK.....', '.....KAGAAK.....', '.....KAAAAK.....',
             '.....KAAAAK.....', '.....KLBBDK.....', '.....KLBBDK.....', '.....KKKKKK.....', '................', '................']
I_CRYSTAL = ['................', '.......KK.......', '......KHLK......', '.....KHHLAK.....', '....KHHLLAAK....', '...KHLLGLAAAK...', '..KHLLGGLAAAVK..', '..KLLLGLLAAVVK..', '...KLLLLAAVVK...', '....KLLAAVVK....',
             '.....KLAVVK.....', '......KAVK......', '.......KK.......', '................', '................', '................']
I_ORB = ['................', '.....KKKKKK.....', '....KHHLLAAK....', '...KHWHLLAAVK...', '..KHWHLLLAAAVK..', '..KHHLLLGLAAVK..', '..KLLLLGGGAAVK..', '..KLLLLLGLAAVK..', '..KLLLLLLAAAVK..', '..KALLLLAAAVVK..',
         '...KALAAAVVVK...', '....KAVVVVVK....', '.....KKKKKK.....', '................', '................', '................']
I_HAZ_HELMET = ['................', '................', '....KKKKKKKK....', '...KHHLLLLLBK...', '..KHLBBBBBBBDK..', '..KLKKKKKKKKDK..', '..KLKVVVVWVKDK..', '..KBKVVVVVVKDK..', '..KBKKKKKKKKDK..', '..KBBSSSSSSBDK..',
                '..KDBSGGGGSDDK..', '..KKDSSSSSSDKK..', '...KKKKKKKKKK...', '................', '................', '................']
I_HAZ_CHEST = ['...KKK....KKK...', '..KHHLK..KLLBK..', '.KHLLLBKKBBBBDK.', '.KLBBBBGKBBBBDK.', '.KLBKBBKGBBKBDK.', '.KKKKBBGKBBKKKK.', '...KBAAAAAADK...', '...KLAGGGGADK...', '...KLAAAAAADK...', '...KLBBKGBBDK...',
               '...KBBBGKBBDK...', '...KAAAAAAAAK...', '...KDBBDDBBDK...', '...KKKKKKKKKK...', '................', '................']
I_HAZ_LEGS = ['................', '...KKKKKKKKKK...', '...KAAAAAAAAK...', '...KLBBBBBBDK...', '...KLBBKKBBDK...', '...KLBDKKLBDK...', '...KHBDKKLBDK...', '...KAAAKKAAAK...', '...KLBDKKLBDK...', '...KLBDKKLBDK...',
              '...KLBDKKLBDK...', '...KLBDKKLBDK...', '...KDDDKKDDDK...', '...KKKKKKKKKK...', '................', '................']
I_RUBBER_BOOTS = ['................', '................', '................', '...KKKKKKKKKK...', '...KHLDKKHLDK...', '...KLBDKKLBDK...', '...KAADKKAADK...', '...KLBDKKLBDK...', '...KLBDKKLBDK...', '..KLLBDKKLLBDK..',
                  '.KHLBBDKKHLBBDK.', '.KLBBBDKKLBBBDK.', '.KKKKKKKKKKKKKK.', '.KGGGGKKKKGGGGK.', '..KKKK....KKKK..', '................']


def icon(rows, pal, seed=1):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    paint(im, 0, 0, rows, pal, seed)
    return im


def scale(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def shade(im):
    """Gives a flat one-colour silhouette an outline and light from the top left, the way hand-drawn icons have it."""
    w, h = im.size
    src = im.load()
    out = im.copy()
    px = out.load()
    on = lambda x, y: 0 <= x < w and 0 <= y < h and src[x, y][3] > 0
    for y in range(h):
        for x in range(w):
            if not on(x, y):
                continue
            c = src[x, y]
            edge = not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1))
            if edge:
                px[x, y] = scale(c, 0.42) + (255,)
            elif not on(x - 2, y) or not on(x, y - 2):
                px[x, y] = scale(c, 1.28) + (255,)
            elif not on(x + 2, y) or not on(x, y + 2):
                px[x, y] = scale(c, 0.76) + (255,)
    return out


# --- reactor blocks ----------------------------------------------------------------------------------------------------------------------
def plate(col, seed, bolts=True):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            v = rnd.randint(-4, 4)
            if x == 0 or y == 0:
                v = 34
            elif x == 15 or y == 15:
                v = -44
            elif x == 1 or y == 1:
                v = 12
            elif x == 14 or y == 14:
                v = -18
            im.putpixel((x, y), tuple(max(0, min(255, c + v)) for c in col) + (255,))
    if bolts:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            im.putpixel((x, y), scale(col, 1.5) + (255,))
            im.putpixel((x + 1 if x < 8 else x - 1, y + 1 if y < 8 else y - 1), scale(col, 0.55) + (255,))
    return im


def reactor_side():
    """Riveted hull, a row of vents and a warning stripe."""
    col = (86, 92, 98)
    im = plate(col, 11)
    d = ImageDraw.Draw(im)
    d.rectangle((4, 4, 11, 8), fill=scale(col, 0.45))
    for x in (5, 7, 9):
        d.line((x, 4, x, 8), fill=scale(col, 0.75))
    d.line((4, 3, 11, 3), fill=scale(col, 1.3))
    for x in range(3, 13):
        d.point((x, 10), fill=(226, 184, 40) if (x // 2) % 2 == 0 else (28, 28, 30))
        d.point((x, 11), fill=(226, 184, 40) if ((x + 1) // 2) % 2 == 0 else (28, 28, 30))
    return im


def reactor_top():
    """The core seen from above: rods in a glowing pool."""
    col = (86, 92, 98)
    im = plate(col, 12)
    d = ImageDraw.Draw(im)
    d.rectangle((3, 3, 12, 12), fill=(24, 40, 30))
    d.rectangle((3, 3, 12, 3), fill=(16, 22, 18))
    d.rectangle((3, 3, 3, 12), fill=(16, 22, 18))
    for x in (4, 7, 10):
        for y in (4, 7, 10):
            d.rectangle((x, y, x + 1, y + 1), fill=(90, 220, 110))
            d.point((x, y), fill=(180, 255, 190))
    return im


def chamber_side():
    """A lead plate with a hatch: visibly a part of the reactor."""
    col = (108, 110, 124)
    im = plate(col, 13)
    d = ImageDraw.Draw(im)
    d.rectangle((3, 3, 12, 12), outline=scale(col, 0.5))
    d.line((4, 4, 11, 4), fill=scale(col, 1.3))
    d.line((4, 4, 4, 11), fill=scale(col, 1.3))
    for x in range(5, 11):
        d.point((x, 5 + (x % 2) * 0), fill=scale(col, 0.8))
    d.rectangle((6, 7, 9, 8), fill=(30, 30, 34))
    d.point((7, 7), fill=(90, 220, 110))
    d.point((8, 8), fill=(90, 220, 110))
    return im


# --- reactor window -------------------------------------------------------------------------------------------------------------------
GUI_BG, GUI_LIGHT, GUI_DARK, GUI_EDGE = (198, 198, 198), (255, 255, 255), (85, 85, 85), (0, 0, 0)
SLOT_TL, SLOT_FILL, SLOT_BR = (55, 55, 55), (139, 139, 139), (255, 255, 255)


def gui_panel(d, w, h):
    d.rectangle((3, 0, w - 4, h - 1), fill=GUI_EDGE)
    d.rectangle((0, 3, w - 1, h - 4), fill=GUI_EDGE)
    for x, y in ((1, 2), (2, 1), (w - 2, 2), (w - 3, 1), (1, h - 3), (2, h - 2), (w - 2, h - 3), (w - 3, h - 2)):
        d.point((x, y), fill=GUI_EDGE)
    d.rectangle((3, 1, w - 4, h - 2), fill=GUI_BG)
    d.rectangle((1, 3, w - 2, h - 4), fill=GUI_BG)
    d.rectangle((2, 2, w - 3, h - 3), fill=GUI_BG)
    d.line((3, 1, w - 5, 1), fill=GUI_LIGHT)
    d.line((3, 2, w - 5, 2), fill=GUI_LIGHT)
    d.line((1, 3, 1, h - 5), fill=GUI_LIGHT)
    d.line((2, 3, 2, h - 5), fill=GUI_LIGHT)
    d.line((4, h - 2, w - 3, h - 2), fill=GUI_DARK)
    d.line((4, h - 3, w - 3, h - 3), fill=GUI_DARK)
    d.line((w - 2, 4, w - 2, h - 4), fill=GUI_DARK)
    d.line((w - 3, 4, w - 3, h - 4), fill=GUI_DARK)


def gui_slot(d, x, y):
    d.rectangle((x, y, x + 17, y + 17), fill=SLOT_FILL)
    d.line((x, y, x + 16, y), fill=SLOT_TL)
    d.line((x, y, x, y + 16), fill=SLOT_TL)
    d.line((x + 1, y + 17, x + 17, y + 17), fill=SLOT_BR)
    d.line((x + 17, y + 1, x + 17, y + 17), fill=SLOT_BR)


def reactor_gui():
    """176x240: grid at (8,18), heat bar at (8,131), player inventory at (8,158), then sprites on the right: closed cell (176,0), lamp off (176,18), lamp on (184,18)."""
    im = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    gui_panel(d, 176, 240)
    # a recessed well under the reactor grid
    d.rectangle((5, 15, 170, 127), fill=(120, 120, 124))
    d.line((5, 15, 170, 15), fill=SLOT_TL)
    d.line((5, 15, 5, 127), fill=SLOT_TL)
    d.line((6, 127, 170, 127), fill=GUI_LIGHT)
    d.line((170, 16, 170, 127), fill=GUI_LIGHT)
    for r in range(6):
        for c in range(9):
            gui_slot(d, 7 + c * 18, 17 + r * 18)
    # heat bar
    d.rectangle((7, 130, 168, 139), fill=(28, 28, 30))
    d.line((7, 130, 167, 130), fill=SLOT_TL)
    d.line((7, 130, 7, 138), fill=SLOT_TL)
    d.line((8, 139, 168, 139), fill=GUI_LIGHT)
    d.line((168, 131, 168, 139), fill=GUI_LIGHT)
    # zones under the bar: a coloured strip shows where each overheat effect starts
    for x in range(8, 168):
        p = (x - 8) * 100 // 160
        col = (63, 185, 80) if p < 40 else (200, 200, 48) if p < 50 else (232, 160, 32) if p < 70 else (224, 96, 24) if p < 85 else (224, 32, 32)
        d.point((x, 141), fill=col)
        d.point((x, 142), fill=scale(col, 0.75))
    for r in range(3):
        for c in range(9):
            gui_slot(d, 7 + c * 18, 157 + r * 18)
    for c in range(9):
        gui_slot(d, 7 + c * 18, 215)
    # closed cell: a bolted plate with hazard corners
    pl = (92, 94, 100)
    d.rectangle((176, 0, 193, 17), fill=pl)
    d.line((176, 0, 192, 0), fill=(130, 132, 138))
    d.line((176, 0, 176, 16), fill=(130, 132, 138))
    d.line((177, 17, 193, 17), fill=(52, 54, 58))
    d.line((193, 1, 193, 17), fill=(52, 54, 58))
    for k in range(5, 13):
        d.point((176 + k, k), fill=(66, 68, 72))
        d.point((176 + 17 - k, k), fill=(66, 68, 72))
    for x, y in ((2, 2), (15, 2), (2, 15), (15, 15)):
        d.point((176 + x, y), fill=(150, 152, 158))
    # lamps
    for u, rim, core, hi in ((176, (60, 60, 60), (92, 34, 30), (130, 60, 54)), (184, (60, 60, 60), (70, 220, 90), (190, 255, 200))):
        d.rectangle((u + 1, 18, u + 6, 25), fill=rim)
        d.rectangle((u, 19, u + 7, 24), fill=rim)
        d.rectangle((u + 1, 19, u + 6, 24), fill=core)
        d.point((u + 2, 20), fill=hi)
        d.point((u + 3, 20), fill=hi)
        d.point((u + 2, 21), fill=hi)
    return im
