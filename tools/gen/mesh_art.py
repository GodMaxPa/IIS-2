"""Low-poly models of vehicles, trains and guns (D128): quads with a colour and a material, written as JSON for ru.iis.equipment.client.MeshModels.

Units are model pixels, 16 to a block. Z is forward, Y is up, ground (or the rail head) is y = 0. A guns's grip is at the origin.
All art is our own. preview() draws a model without the game, so its look can be judged while it is being made.
"""
import json
import math
import random

from PIL import Image, ImageDraw

# order is the row in textures/entity/vehicle/atlas.png: 4 x 4 cells of 64 x 64
MATS = ['paint', 'metal', 'panel', 'wood', 'rubber', 'grill', 'plain', 'rivet', 'tread', 'cloth', 'scratch', 'plate']
GLOW, GLASS = 1, 2


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255) if isinstance(c, int) else tuple(c)


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def darker(c, k=0.7):
    return tuple(int(v * k) for v in rgb(c))


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _norm(a):
    n = math.sqrt(_dot(a, a)) or 1.0
    return (a[0] / n, a[1] / n, a[2] / n)


def quad_normal(q):
    return _norm(_cross(_sub(q[2], q[0]), _sub(q[3], q[1])))


class Part:
    def __init__(self, name, pivot, anim, rate, parent):
        self.name, self.pivot, self.anim, self.rate, self.parent = name, pivot, anim, rate, parent
        self.quads = []


class Mesh:
    def __init__(self, name):
        self.name = name
        self.parts = []
        self.meta = {}
        self.part('body')

    def part(self, name, pivot=(0, 0, 0), anim=None, rate=1.0, parent=None):
        """Select a part, making it if new. anim: wheel, steer, prop, rotor, tail, turret, barrel, rod, drill."""
        for p in self.parts:
            if p.name == name:
                self.cur = p
                return self
        self.cur = Part(name, pivot, anim, rate, parent)
        self.parts.append(self.cur)
        return self

    def quad(self, a, b, c, d, color, mat='paint', glow=False, glass=False, center=None):
        q = [a, b, c, d]
        if center is not None:
            mid = tuple(sum(v[i] for v in q) / 4 for i in range(3))
            if _dot(quad_normal(q), _sub(mid, center)) < 0:
                q = [d, c, b, a]
        self.cur.quads.append((q, rgb(color), MATS.index(mat), (GLOW if glow else 0) | (GLASS if glass else 0)))
        return self

    def hexa(self, c, color, mat='paint', skip=(), **f):
        """c: eight corners, bottom ring then top ring, in the same order around."""
        center = tuple(sum(v[i] for v in c) / 8 for i in range(3))
        faces = {'bottom': (0, 1, 2, 3), 'top': (4, 5, 6, 7), 'a': (0, 1, 5, 4), 'b': (1, 2, 6, 5), 'c': (2, 3, 7, 6), 'd': (3, 0, 4, 7)}
        for k, (i, j, m, n) in faces.items():
            if k not in skip:
                self.quad(c[i], c[j], c[m], c[n], color, mat, center=center, **f)
        return self

    def box(self, x0, y0, z0, x1, y1, z1, color, mat='paint', **f):
        return self.hexa([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), (x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], color, mat, **f)

    def sym(self, x0, y0, z0, x1, y1, z1, color, mat='paint', **f):
        """The same box on both sides of the middle line."""
        self.box(x0, y0, z0, x1, y1, z1, color, mat, **f)
        return self.box(-x1, y0, z0, -x0, y1, z1, color, mat, **f)

    def rloft(self, stations, color, mat='paint', caps=True, **f):
        """Hull along Z: stations (z, half width, bottom, top[, half width on top])."""
        rings = []
        for s in stations:
            z, hw, y0, y1 = s[:4]
            ht = s[4] if len(s) > 4 else hw
            rings.append([(-hw, y0, z), (hw, y0, z), (ht, y1, z), (-ht, y1, z)])
        center = tuple(sum(v[i] for r in rings for v in r) / (4 * len(rings)) for i in range(3))
        for a, b in zip(rings, rings[1:]):
            for i in range(4):
                j = (i + 1) % 4
                self.quad(a[i], a[j], b[j], b[i], color, mat, center=center, **f)
        if caps:
            self.quad(*rings[0], color, mat, center=center, **f)
            self.quad(*rings[-1], color, mat, center=center, **f)
        return self

    def loft(self, rings, color, n=12, mat='paint', caps=True, phase=None, **f):
        """Round hull along Z: rings (z, cx, cy, rx, ry). Colour may be a function of the angle."""
        pts = []
        ph = phase if phase is not None else math.pi / n
        for z, cx, cy, rx, ry in rings:
            pts.append([(cx + rx * math.cos(ph + 2 * math.pi * i / n), cy + ry * math.sin(ph + 2 * math.pi * i / n), z) for i in range(n)])
        center = tuple(sum(v[i] for r in pts for v in r) / (n * len(pts)) for i in range(3))
        for a, b in zip(pts, pts[1:]):
            for i in range(n):
                j = (i + 1) % n
                col = color(ph + 2 * math.pi * (i + 0.5) / n) if callable(color) else color
                self.quad(a[i], a[j], b[j], b[i], col, mat, center=center, **f)
        if caps:
            for ring, zc in ((pts[0], rings[0]), (pts[-1], rings[-1])):
                mid = (zc[1], zc[2], zc[0])
                for i in range(n):
                    j = (i + 1) % n
                    col = color(0.0) if callable(color) else color
                    self.quad(mid, ring[i], ring[j], ring[j], col, mat, center=center, **f)
        return self

    def cyl(self, axis, c, r, length, color, n=12, mat='paint', r2=None, caps=True, cap_color=None, **f):
        """Cylinder or cone from c along +axis ('x', 'y' or 'z'); r2 is the far radius."""
        r2 = r if r2 is None else r2

        def at(k, rr, t):
            u, v = rr * math.cos(t), rr * math.sin(t)
            if axis == 'x':
                return (c[0] + k, c[1] + u, c[2] + v)
            if axis == 'y':
                return (c[0] + u, c[1] + k, c[2] + v)
            return (c[0] + u, c[1] + v, c[2] + k)

        ph = math.pi / n
        center = at(length / 2, 0, 0)
        for i in range(n):
            t0, t1 = ph + 2 * math.pi * i / n, ph + 2 * math.pi * (i + 1) / n
            self.quad(at(0, r, t0), at(0, r, t1), at(length, r2, t1), at(length, r2, t0), color, mat, center=center, **f)
            if caps:
                cc = cap_color if cap_color is not None else color
                self.quad(at(0, 0, 0), at(0, r, t0), at(0, r, t1), at(0, r, t1), cc, mat, center=center, **f)
                if r2 > 0:
                    self.quad(at(length, 0, 0), at(length, r2, t0), at(length, r2, t1), at(length, r2, t1), cc, mat, center=center, **f)
        return self

    def wheel(self, name, x, y, z, r, w, anim='wheel', tire=0x25252A, hub=0xB8BCC4, spokes=0, parent=None):
        """Wheel with its centre at (x, y, z); it makes its own part and returns to the body."""
        keep = self.cur
        self.part(name, (x, y, z), anim, 16.0 / r if r else 1.0, parent)
        self.cyl('x', (x - w / 2, y, z), r, w, tire, n=14, mat='tread', caps=False)
        self.cyl('x', (x - w / 2 - 0.05, y, z), r * 0.92, w + 0.1, tire, n=14, mat='rubber', caps=True, cap_color=darker(tire, 0.8))
        rim = r * 0.58
        self.cyl('x', (x - w / 2 - 0.25, y, z), rim, w + 0.5, hub, n=10, mat='metal', cap_color=hub)
        self.cyl('x', (x - w / 2 - 0.45, y, z), rim * 0.35, w + 0.9, darker(hub, 0.55), n=6, mat='plain')
        # bars across the hub: they are what shows the wheel turning
        side = 1 if x >= 0 else -1
        for i in range(max(spokes, 3)):
            t = math.pi * i / max(spokes, 3)
            dy, dz = math.sin(t) * rim * 0.9, math.cos(t) * rim * 0.9
            xs = x + side * (w / 2 + 0.3)
            ww = 0.45
            nx, nz = -math.sin(t) * ww, math.cos(t) * ww
            for s in (-1, 1):
                self.quad((xs, y - dy * 1 + nx * s, z - dz + nz * s * 0), (xs, y + dy + nx * s, z + dz), (xs, y + dy + nx * s + 0.6 * s * 0, z + dz + 0.6 * s), (xs, y - dy + nx * s, z - dz + 0.6 * s), darker(hub, 0.45), 'plain')
        self.cur = keep
        return self

    def bounds(self):
        xs, ys, zs = [], [], []
        for p in self.parts:
            for q, _, _, _ in p.quads:
                for v in q:
                    xs.append(v[0])
                    ys.append(v[1])
                    zs.append(v[2])
        return (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs))

    def to_json(self):
        out = []
        for p in self.parts:
            flat = []
            for q, col, mat, flags in p.quads:
                for v in q:
                    flat += [round(v[0], 2), round(v[1], 2), round(v[2], 2)]
                flat += [(col[0] << 16) | (col[1] << 8) | col[2], mat, flags]
            e = {'name': p.name, 'pivot': list(p.pivot), 'q': flat}
            if p.anim:
                e['anim'] = p.anim
                e['rate'] = round(p.rate, 3)
            if p.parent:
                e['parent'] = p.parent
            out.append(e)
        b = self.bounds()
        return dict(self.meta, parts=out, bounds=[round(v, 2) for v in b])

    def write(self, path):
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(self.to_json(), f, separators=(',', ':'))


# --- preview -------------------------------------------------------------------------------------------------------------------
def preview(mesh, size=520, yaw=35, pitch=24, bg=(150, 170, 190)):
    """Picture of the model from the given direction: painter's order, one-sided light."""
    ss = 2
    im = Image.new('RGB', (size * ss, size * ss), bg)
    d = ImageDraw.Draw(im, 'RGBA')
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    light = _norm((0.5, 0.8, 0.45))

    def view(v):
        x, y, z = v
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x, y, z)

    faces = []
    for p in mesh.parts:
        for q, col, mat, flags in p.quads:
            n = quad_normal(q)
            lam = 0.55 + 0.45 * max(0.0, _dot(n, light))
            lam = max(lam, 0.55 + 0.25 * max(0.0, -_dot(n, light)))
            c = tuple(int(min(255, k * lam)) for k in col)
            if flags & GLOW:
                c = col
            v = [view(a) for a in q]
            faces.append((sum(a[2] for a in v) / 4, v, c, flags))
    allv = [a for f in faces for a in f[1]]
    x0, x1 = min(a[0] for a in allv), max(a[0] for a in allv)
    y0, y1 = min(a[1] for a in allv), max(a[1] for a in allv)
    s = (size * ss * 0.92) / max(x1 - x0, y1 - y0, 1)
    cx, cyy = (x0 + x1) / 2, (y0 + y1) / 2
    for _, v, c, flags in sorted(faces, key=lambda f: f[0]):
        pts = [((a[0] - cx) * s + size * ss / 2, size * ss / 2 - (a[1] - cyy) * s) for a in v]
        if flags & GLASS:
            d.polygon(pts, fill=c + (110,))
        else:
            d.polygon(pts, fill=c + (255,), outline=tuple(int(k * 0.8) for k in c) + (255,))
    return im.resize((size, size), Image.LANCZOS)


def sheet(meshes, cols=4, size=380, views=((35, 24),)):
    rows = (len(meshes) * len(views) + cols - 1) // cols
    out = Image.new('RGB', (cols * size, rows * size), (40, 40, 40))
    i = 0
    d = ImageDraw.Draw(out)
    for m in meshes:
        for yaw, pitch in views:
            out.paste(preview(m, size - 4, yaw, pitch), ((i % cols) * size + 2, (i // cols) * size + 2))
            d.text(((i % cols) * size + 6, (i // cols) * size + 6), m.name, fill=(255, 255, 80))
            i += 1
    return out


# --- atlas ---------------------------------------------------------------------------------------------------------------------
def atlas():
    """16 cells of 64 x 64: light grey detail, tinted by the colour of each quad."""
    rnd = random.Random(128)
    im = Image.new('RGBA', (256, 256), (255, 255, 255, 255))
    px = im.load()

    def cell(i):
        return (i % 4) * 64, (i // 4) * 64

    def put(i, x, y, v):
        cx, cy = cell(i)
        v = max(0, min(255, int(v)))
        px[cx + x, cy + y] = (v, v, v, 255)

    for y in range(64):
        for x in range(64):
            put(0, x, y, 244 + rnd.randint(-5, 5))    # paint
            put(1, x, y, 225 + rnd.randint(-14, 14) + (8 if y % 3 == 0 else 0))    # brushed metal
            put(2, x, y, (150 if x % 32 == 0 or y % 16 == 0 else 238) + rnd.randint(-4, 4))    # panels
            plank = y // 8
            grain = 12 * math.sin((x + plank * 17) * 0.35 + plank) + rnd.randint(-8, 8)
            put(3, x, y, (130 if y % 8 == 0 else 215 + grain))    # wood
            put(4, x, y, 225 + rnd.randint(-16, 16))    # rubber
            put(5, x, y, 110 if y % 4 < 2 else 245)    # grille
            put(6, x, y, 255)    # plain
            rivet = (x % 12 in (5, 6)) and (y % 12 in (5, 6))
            put(7, x, y, (255 if rivet else 214) + rnd.randint(-5, 5) - (30 if x % 12 == 7 and y % 12 in (6, 7) else 0))    # rivets
            tread = 120 if (y + (x // 8) * 3) % 6 < 2 else 240
            put(8, x, y, tread + rnd.randint(-8, 8))    # tread
            put(9, x, y, (230 if (x + y) % 2 else 205) + rnd.randint(-6, 6))    # cloth
            put(10, x, y, 232 + rnd.randint(-10, 10))    # scratched paint
            put(11, x, y, (140 if x % 24 == 0 or y % 24 == 0 else 225) + rnd.randint(-8, 8))    # armour plate
    # scratches and dirt on the worn paint
    cx, cy = cell(10)
    for _ in range(60):
        x, y = rnd.randint(0, 60), rnd.randint(0, 60)
        for k in range(rnd.randint(2, 9)):
            xx, yy = x + k, y + (k // 3)
            if xx < 64 and yy < 64:
                v = rnd.randint(150, 205)
                px[cx + xx, cy + yy] = (v, v, v, 255)
    return im
