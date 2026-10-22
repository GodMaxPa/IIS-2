"""Bio path generator, first prototype (D64), called from gen_content.py with its globals.

Own procedural graphics (D40, revised D64): animated textures, JSON models, blockstates, loot, lang, sounds.json.
Aesthetics (owner, 2026-10-20): alien biopunk, Zerg / xenomorph — wet reddish flesh, dark chitin, membranes, veins;
violet and greenish bioluminescence only as accents. Sounds: our sound events delegate to recorded vanilla events (sounds.json), see D68.
Java side: ru.iis.bio.BioContent.
"""
import json
import os

import numpy as np
from PIL import Image, ImageDraw

MOODS = ['dormant', 'hungry', 'content', 'growing', 'alarmed']
VITALITY = ['fresh', 'alive', 'starving']
SOUND_IDS = ['heartbeat', 'growl', 'squelch', 'tear', 'gulp', 'chew', 'grow', 'hiss']


def smooth_noise(seed, blur=2, size=16):
    a = np.random.default_rng(seed).random((size, size))
    for _ in range(blur):
        a = (np.roll(a, 1, 0) + np.roll(a, -1, 0) + np.roll(a, 1, 1) + np.roll(a, -1, 1) + a * 2) / 6
    return (a - a.min()) / (a.max() - a.min() + 1e-9)


def lerp(a, b, t):
    a, b = np.array(a, float), np.array(b, float)
    t = np.asarray(t, float)[..., None]
    return a + (b - a) * t


def to_img(rgb, alpha=None):
    out = np.zeros(rgb.shape[:2] + (4,), np.uint8)
    out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    out[..., 3] = 255 if alpha is None else (np.asarray(alpha) * 255).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def strip(frames):
    im = Image.new('RGBA', (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        im.paste(f, (0, 16 * i))
    return im


YY, XX = np.mgrid[0:16, 0:16]

# ----------------------------------------------------------------------------------------------------------------
# Colony core: chitin shell + radial veins + pulsing heart; the mood changes colour and pulse rate
# ----------------------------------------------------------------------------------------------------------------
CORE_MOOD = {
    #            heart centre      heart edge       vein colour        radius amp  frames  frametime
    'dormant': ((120, 90, 130), (60, 40, 70), (90, 60, 100), 3.0, 0.5, 8, 10),
    'hungry':  ((150, 170, 120), (80, 95, 70), (110, 130, 90), 2.4, 0.4, 8, 7),
    'content': ((235, 70, 80), (130, 20, 40), (190, 50, 70), 3.5, 0.8, 8, 5),
    'growing': ((255, 120, 90), (170, 30, 50), (120, 255, 160), 4.0, 1.2, 8, 3),
    'alarmed': ((255, 210, 150), (230, 60, 40), (255, 150, 90), 4.5, 1.6, 8, 2),
}


def core_vein_mask():
    rnd = np.random.default_rng(77)
    im = Image.new('L', (16, 16), 0)
    d = ImageDraw.Draw(im)
    for k in range(9):
        ang = k * 2 * np.pi / 9 + rnd.uniform(-0.2, 0.2)
        x, y = 7.5, 7.5
        for _ in range(9):
            nx = x + np.cos(ang) + rnd.uniform(-0.6, 0.6)
            ny = y + np.sin(ang) + rnd.uniform(-0.6, 0.6)
            d.line([(x, y), (nx, ny)], fill=255)
            x, y = nx, ny
    return np.array(im) / 255.0


def core_frames(mood):
    c0, c1, vc, r0, amp, n, ft = CORE_MOOD[mood]
    shell_n = smooth_noise(11, 1)
    plates = smooth_noise(12, 2)
    veins = core_vein_mask()
    dist = np.hypot(XX - 7.5, YY - 7.5)
    border = (np.minimum(np.minimum(XX, YY), np.minimum(15 - XX, 15 - YY)) == 0)
    frames = []
    rnd = np.random.default_rng(5)
    irregular = rnd.uniform(0.6, 1.4, n) if mood == 'hungry' else np.ones(n)
    for f in range(n):
        ph = 2 * np.pi * f / n
        pulse = 0.5 + 0.5 * np.sin(ph) * irregular[f]
        rgb = lerp((24, 18, 28), (62, 46, 66), shell_n * 0.6 + plates * 0.4)
        rgb = np.where(border[..., None], rgb * 0.55, rgb)
        # flesh seams between plates
        seams = np.abs(plates - 0.5) < 0.045
        rgb = np.where(seams[..., None], np.array((95, 20, 38)), rgb)
        # veins glow with a wave running outward from the heart
        wave = 0.5 + 0.5 * np.sin(ph - dist * 0.7)
        vcol = lerp((40, 10, 24), vc, wave * (0.35 + 0.65 * pulse))
        vm = (veins > 0.5) & (dist > 2.5)
        rgb = np.where(vm[..., None], vcol, rgb)
        # heart
        r = r0 + amp * np.sin(ph) * irregular[f]
        t = np.clip(dist / max(r, 0.5), 0, 1.6)
        heart = lerp(c0, c1, np.clip(t, 0, 1))
        inside = dist <= r
        ring = (dist > r) & (dist <= r + 1.0)
        rgb = np.where(inside[..., None], heart, rgb)
        rgb = np.where(ring[..., None], rgb * 0.45 + np.array(c1) * 0.35, rgb)
        frames.append(to_img(rgb))
    return strip(frames), n, ft


# ----------------------------------------------------------------------------------------------------------------
# Living tissue: patchy wet flesh film with glowing veins (cutout); fresh -> alive -> starving
# ----------------------------------------------------------------------------------------------------------------
TISSUE = {
    #            flesh dark       flesh light      vein dark        vein glow A        glow B          cover glow frames frametime
    'fresh':    ((190, 70, 85), (235, 130, 130), (230, 120, 125), (255, 190, 190), (255, 190, 190), 0.40, 0.3, 6, 4),
    'alive':    ((100, 18, 36), (165, 42, 56), (70, 14, 40), (170, 90, 255), (110, 255, 160), 0.44, 1.0, 8, 5),
    'starving': ((70, 50, 48), (112, 84, 74), (52, 36, 38), (70, 52, 50), (70, 52, 50), 0.58, 0.0, 4, 20),
}


def tissue_frames(kind):
    fd, fl, vd, ga, gb, thr, glow, n, ft = TISSUE[kind]
    n1, n2, n3, n4, n5 = smooth_noise(21, 3), smooth_noise(22, 1), smooth_noise(23, 2), smooth_noise(24, 3), smooth_noise(25, 2)
    mask = n1 > thr
    # knock out isolated pixels so the film reads as blobs
    neigh = sum(np.roll(np.roll(mask, i, 0), j, 1) for i in (-1, 0, 1) for j in (-1, 0, 1) if (i, j) != (0, 0))
    mask = mask & (neigh >= 2)
    edge = mask & (neigh <= 5)
    veins = (np.abs(n4 - 0.5) < 0.07) & mask
    frames = []
    for f in range(n):
        ph = f / n
        rgb = lerp(fd, fl, n2)
        wet = (n3 > 0.8) & mask
        rgb = np.where(wet[..., None], lerp((240, 170, 160), (255, 220, 210), n3) * (0.6 if kind == 'starving' else 1.0), rgb)
        rgb = np.where(edge[..., None], rgb * 0.6, rgb)
        pulse = 0.5 + 0.5 * np.sin(2 * np.pi * (ph + n5))
        gcol = lerp(ga, gb, n5) if kind == 'alive' else np.array(ga, float)
        vcol = lerp(vd, gcol, np.clip(pulse * glow, 0, 1)) if glow > 0 else np.broadcast_to(np.array(vd, float), (16, 16, 3))
        rgb = np.where(veins[..., None], vcol, rgb)
        frames.append(to_img(rgb, mask))
    return strip(frames), n, ft


# ----------------------------------------------------------------------------------------------------------------
# Hull of the emergency shuttle, maw textures
# ----------------------------------------------------------------------------------------------------------------
def hull_tex():
    n1, n2, n3 = smooth_noise(31, 1), smooth_noise(32, 3), smooth_noise(33, 2)
    rgb = lerp((46, 48, 56), (84, 88, 98), n1)
    seam = (XX % 8 == 0) | (YY % 8 == 0)
    rgb = np.where(seam[..., None], rgb * 0.55, rgb)
    rivet = ((XX % 8 == 2) | (XX % 8 == 6)) & ((YY % 8 == 2) | (YY % 8 == 6))
    rgb = np.where(rivet[..., None], np.array((140, 146, 158)), rgb)
    scorch = n2 > 0.7
    rgb = np.where(scorch[..., None], rgb * 0.5, rgb)
    infect = n3 > 0.8
    rgb = np.where(infect[..., None], lerp((120, 24, 40), (190, 60, 70), n3), rgb)
    return to_img(rgb)


def window_tex():
    """Тёмное потрескавшееся остекление: непрозрачная рама, полупрозрачное стекло, трещины от удара."""
    n1 = smooth_noise(51, 2)
    rgb = lerp((14, 20, 32), (34, 50, 66), n1)
    alpha = np.full((16, 16), 0.72)
    cracks = Image.new('L', (16, 16), 0)
    d = ImageDraw.Draw(cracks)
    rnd = np.random.default_rng(52)
    for k in range(6):
        ang = k * 2 * np.pi / 6 + rnd.uniform(-0.3, 0.3)
        x, y = 5.0 + rnd.uniform(-1, 1), 9.0 + rnd.uniform(-1, 1)
        for _ in range(7):
            nx, ny = x + np.cos(ang) * 1.3 + rnd.uniform(-0.5, 0.5), y + np.sin(ang) * 1.3 + rnd.uniform(-0.5, 0.5)
            d.line([(x, y), (nx, ny)], fill=255)
            x, y = nx, ny
    cr = np.array(cracks) > 128
    rgb = np.where(cr[..., None], np.array((170, 190, 205)), rgb)
    alpha = np.where(cr, 0.95, alpha)
    border = (np.minimum(np.minimum(XX, YY), np.minimum(15 - XX, 15 - YY)) == 0)
    rgb = np.where(border[..., None], np.array((52, 54, 62)), rgb)
    alpha = np.where(border, 1.0, alpha)
    infect = (n1 > 0.86) & ~border
    rgb = np.where(infect[..., None], np.array((150, 30, 50)), rgb)
    alpha = np.where(infect, 0.9, alpha)
    return to_img(rgb, alpha)


def engine_tex():
    """Обгоревшее сопло: тёмная пластина с кольцом нагара и остывающим жаром в центре."""
    n1, n2 = smooth_noise(61, 1), smooth_noise(62, 2)
    rgb = lerp((30, 30, 36), (66, 68, 76), n1)
    dist = np.hypot(XX - 7.5, YY - 7.5)
    ring = (dist > 3.2) & (dist < 5.4)
    rgb = np.where(ring[..., None], lerp((18, 16, 18), (44, 40, 40), n2), rgb)
    glow = dist <= 3.2
    rgb = np.where(glow[..., None], lerp((220, 110, 40), (90, 24, 18), np.clip(dist / 3.2, 0, 1)), rgb)
    soot = n2 > 0.75
    rgb = np.where((soot & ~glow)[..., None], rgb * 0.5, rgb)
    return to_img(rgb)


def maw_skin_frames(dull):
    n1, n2 = smooth_noise(41, 1), smooth_noise(42, 2)
    frames = []
    for f in range(4):
        b = 0.88 + 0.12 * np.sin(2 * np.pi * f / 4)
        if dull:
            rgb = lerp((70, 52, 52), (104, 82, 76), n1)
        else:
            rgb = lerp((120, 28, 44), (186, 62, 70), n1) * b
        wr = (YY + (n2 * 4).astype(int)) % 4 == 0
        rgb = np.where(wr[..., None], rgb * 0.78, rgb)
        frames.append(to_img(rgb))
    return strip(frames) if not dull else frames[0]


def maw_mouth_frames():
    n1 = smooth_noise(43, 2)
    frames = []
    for f in range(4):
        p = 0.5 + 0.5 * np.sin(2 * np.pi * f / 4)
        rgb = lerp((36, 4, 12), (96, 14, 28), n1) * (0.8 + 0.3 * p)
        shine = (n1 > 0.82)
        rgb = np.where(shine[..., None], np.array((200, 90, 100)), rgb)
        frames.append(to_img(rgb))
    return strip(frames)


def maw_bone():
    n = smooth_noise(44, 1)
    return to_img(lerp((190, 178, 150), (236, 228, 205), n))


# ----------------------------------------------------------------------------------------------------------------
def generate(g):
    write, A, D, NS, lang, block_tags = g['write'], g['A'], g['D'], g['NS'], g['lang'], g['block_tags']

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def save(im, rel, frames=1, frametime=4, times=None):
        im.save(tex(rel + '.png'))
        if frames > 1:
            anim = {'frametime': frametime, 'interpolate': True}
            if times:
                anim['frames'] = [{'index': i, 'time': t} for i, t in enumerate(times)]
            write(tex(rel + '.png.mcmeta'), {'animation': anim})

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    stats = {'textures': 0}

    # --- core ---------------------------------------------------------------------------------------------------
    for mood in MOODS:
        im, n, ft = core_frames(mood)
        times = None
        if mood == 'hungry':  # неровный, слабый пульс
            rnd = np.random.default_rng(9)
            times = [int(ft * rnd.uniform(0.6, 1.8)) + 1 for _ in range(n)]
        save(im, f'block/bio/core_{mood}', n, ft, times)
        stats['textures'] += 1
        write(os.path.join(A, 'models', 'block', f'bio/core_{mood}.json'), {
            'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/bio/core_{mood}'}})
    write(os.path.join(A, 'blockstates', 'colony_core.json'),
          {'variants': {f'mood={m}': {'model': f'{NS}:block/bio/core_{m}'} for m in MOODS}})
    write(os.path.join(A, 'items', 'colony_core.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/core_content'}})

    # --- tissue -------------------------------------------------------------------------------------------------
    for v in VITALITY:
        im, n, ft = tissue_frames(v)
        save(im, f'block/bio/tissue_{v}', n, ft)
        stats['textures'] += 1
        write(os.path.join(A, 'models', 'block', f'bio/tissue_{v}.json'), {
            'ambientocclusion': False,
            'textures': {'particle': f'{NS}:block/bio/tissue_{v}', 'tissue': f'{NS}:block/bio/tissue_{v}'},
            'elements': [{'from': [0, 0, 0.1], 'to': [16, 16, 0.1], 'faces': {
                'north': {'uv': [16, 0, 0, 16], 'texture': '#tissue'}, 'south': {'uv': [0, 0, 16, 16], 'texture': '#tissue'}}}]})
    parts = []
    rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    for v in VITALITY:
        for face, r in rot.items():
            apply = {'model': f'{NS}:block/bio/tissue_{v}', 'uvlock': True, **r} if r else {'model': f'{NS}:block/bio/tissue_{v}'}
            parts.append({'when': {'AND': [{face: 'true'}, {'vitality': v}]}, 'apply': apply})
    write(os.path.join(A, 'blockstates', 'bio_tissue.json'), {'multipart': parts})

    # --- hull ---------------------------------------------------------------------------------------------------
    save(hull_tex(), 'block/bio/shuttle_hull')
    write(os.path.join(A, 'models', 'block', 'bio/shuttle_hull.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/bio/shuttle_hull'}})
    write(os.path.join(A, 'blockstates', 'shuttle_hull.json'), {'variants': {'': {'model': f'{NS}:block/bio/shuttle_hull'}}})
    write(os.path.join(A, 'items', 'shuttle_hull.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/shuttle_hull'}})

    save(window_tex(), 'block/bio/shuttle_window')
    save(engine_tex(), 'block/bio/shuttle_engine')
    stats['textures'] += 2
    for bid in ('shuttle_window', 'shuttle_engine'):
        write(os.path.join(A, 'models', 'block', f'bio/{bid}.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/bio/{bid}'}})
        write(os.path.join(A, 'blockstates', f'{bid}.json'), {'variants': {'': {'model': f'{NS}:block/bio/{bid}'}}})
        write(os.path.join(A, 'items', f'{bid}.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/{bid}'}})

    # --- maw ----------------------------------------------------------------------------------------------------
    save(maw_skin_frames(False), 'block/bio/maw_skin', 4, 8)
    save(maw_skin_frames(True), 'block/bio/maw_skin_dull')
    save(maw_mouth_frames(), 'block/bio/maw_mouth', 4, 6)
    save(maw_bone(), 'block/bio/maw_bone')
    stats['textures'] += 4

    def cuboid(frm, to, tx):
        return {'from': frm, 'to': to, 'faces': {f: {'texture': tx} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}

    def maw_model(skin, open_):
        s, m, b = '#skin', '#mouth', '#bone'
        els = [cuboid([1, 0, 0], [15, 4, 14], s)]
        els[0]['faces']['up'] = {'texture': m if open_ else s}
        els[0]['faces']['north'] = {'texture': s}
        if open_:
            els.append(cuboid([1, 4, 8], [15, 11, 14], s))          # затылочная часть верхней челюсти
            els.append(cuboid([1, 10, 0], [15, 14, 6], s))          # приподнятая верхняя губа
            for x in (2, 5, 8, 11, 13):                                # зубы внизу и вверху
                els.append(cuboid([x, 4, 0.5], [x + 1.5, 7, 2], b))
                els.append(cuboid([x, 7.5, 0.5], [x + 1.5, 10, 2], b))
        else:
            els.append(cuboid([1, 4, 0], [15, 9, 14], s))
            for x in (2, 5, 8, 11, 13):
                els.append(cuboid([x, 3, -0.5], [x + 1.5, 6, 0.5], b))
        return {'parent': 'minecraft:block/block', 'textures': {'particle': f'{NS}:block/bio/{skin}', 'skin': f'{NS}:block/bio/{skin}',
                                                                 'mouth': f'{NS}:block/bio/maw_mouth', 'bone': f'{NS}:block/bio/maw_bone'},
                'elements': els}

    write(os.path.join(A, 'models', 'block', 'bio/maw_closed.json'), maw_model('maw_skin', False))
    write(os.path.join(A, 'models', 'block', 'bio/maw_open.json'), maw_model('maw_skin', True))
    write(os.path.join(A, 'models', 'block', 'bio/maw_dull.json'), maw_model('maw_skin_dull', False))
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for conn in ('true', 'false'):
            for chew in ('true', 'false'):
                model = 'maw_dull' if conn == 'false' else ('maw_open' if chew == 'true' else 'maw_closed')
                v = {'model': f'{NS}:block/bio/{model}'}
                if y:
                    v['y'] = y
                variants[f'facing={facing},connected={conn},chewing={chew}'] = v
    write(os.path.join(A, 'blockstates', 'feeding_maw.json'), {'variants': variants})
    write(os.path.join(A, 'items', 'feeding_maw.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/maw_closed'}})

    # --- loot, tags ---------------------------------------------------------------------------------------------
    for bid in ('colony_core', 'feeding_maw', 'shuttle_hull', 'shuttle_window', 'shuttle_engine'):
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
    write(os.path.join(D, NS, 'loot_table', 'blocks', 'bio_tissue.json'), {'type': 'minecraft:block', 'pools': []})
    block_tags.setdefault('minecraft:mineable/pickaxe', []).append(f'{NS}:shuttle_hull')
    block_tags.setdefault('minecraft:needs_iron_tool', []).append(f'{NS}:shuttle_hull')
    block_tags.setdefault('minecraft:mineable/pickaxe', []).extend([f'{NS}:shuttle_window', f'{NS}:shuttle_engine'])
    block_tags.setdefault('minecraft:needs_iron_tool', []).append(f'{NS}:shuttle_engine')

    # --- sounds.json: наши события озвучены записями ванили (type: event), своих процедурных звуков нет (D68) ----------------
    def ev(name, volume=1.0, pitch=1.0):
        return {'name': name, 'type': 'event', 'volume': volume, 'pitch': pitch}

    sounds = {
        'heartbeat': [ev('entity.warden.heartbeat', 1.0, 1.0)],
        'growl': [ev('entity.ravager.ambient', 0.9, 0.55), ev('entity.hoglin.ambient', 0.9, 0.5)],
        'squelch': [ev('entity.slime.squish_small', 0.8, 0.8), ev('block.mud.step', 1.0, 0.9)],
        'tear': [ev('block.honey_block.break', 1.0, 0.8), ev('block.mud.break', 1.0, 0.8), ev('block.sculk.break', 0.9, 0.9)],
        'gulp': [ev('entity.generic.drink', 1.0, 0.7)],
        'chew': [ev('entity.generic.eat', 1.0, 0.6), ev('entity.strider.eat', 0.9, 0.8)],
        'grow': [ev('block.sculk_catalyst.bloom', 0.9, 1.0), ev('block.sculk.charge', 0.7, 1.0)],
        'hiss': [ev('block.fire.extinguish', 0.8, 1.3), ev('entity.generic.extinguish_fire', 0.8, 1.4)],
    }
    write(os.path.join(A, 'sounds.json'), {
        f'bio.{s}': {'subtitle': f'subtitles.{NS}.bio.{s}', 'sounds': sounds[s]} for s in SOUND_IDS})

    # --- lang ---------------------------------------------------------------------------------------------------
    name('itemGroup.iis.bio', 'IIS 2.0: Bio', 'ИИС 2.0: Био')
    name('block.iis.colony_core', 'Colony Core', 'Ядро колонии')
    name('block.iis.feeding_maw', 'Feeding Maw', 'Пасть-кормушка')
    name('block.iis.bio_tissue', 'Living Tissue', 'Живая ткань')
    name('block.iis.shuttle_hull', 'Emergency Shuttle Hull', 'Обшивка аварийного челнока')
    name('block.iis.shuttle_window', 'Cracked Shuttle Window', 'Потрескавшееся остекление челнока')
    name('block.iis.shuttle_engine', 'Burnt Shuttle Engine', 'Обгоревший двигатель челнока')
    name('block.iis.colony_core.tooltip.0', 'A dormant hearth of a primitive organism. Right-click: its state.',
         'Спящий очаг примитивного организма. ПКМ — состояние.')
    name('block.iis.colony_core.tooltip.1', 'Breaking it kills the living tissue around it.', 'Если сломать ядро, окружающая ткань отмирает.')
    name('block.iis.feeding_maw.tooltip.0', 'Place it against the core or living tissue, then feed it (right-click with food).',
         'Поставьте вплотную к ядру или живой ткани и кормите (ПКМ с едой).')
    name('block.iis.shuttle_engine.tooltip.0', 'A scorched engine nozzle of a crashed shuttle.', 'Обгоревшее сопло двигателя разбитого челнока.')
    name('block.iis.shuttle_hull.tooltip.0', 'Extremely tough wreck plating. Useless for now.', 'Невероятно прочная обшивка. Пока бесполезна.')
    name('message.iis.bio.status', 'Colony: %s | food %s/%s | tissue %s/%s', 'Колония: %s | питание %s/%s | ткань %s/%s')
    for k, en, ru in (('dormant', 'sleeping (feed it through a maw)', 'спит (накормите через пасть)'),
                      ('hungry', 'starving, tissue is withering', 'голодает, ткань увядает'),
                      ('content', 'fed', 'сыта'),
                      ('growing', 'growing', 'растёт'),
                      ('alarmed', 'alarmed!', 'встревожена!')):
        name(f'message.iis.bio.mood.{k}', en, ru)
    name('message.iis.bio.maw.ready', 'The maw is hungry: right-click it holding food.', 'Пасть голодна: нажмите на неё ПКМ с едой в руке.')
    name('message.iis.bio.maw.disconnected', 'The maw is not connected to a colony: place it against the core or living tissue.',
         'Пасть не связана с колонией: поставьте её вплотную к ядру или к живой ткани.')
    name('message.iis.bio.maw.full', 'The organism is full.', 'Организм сыт.')
    name('message.iis.bio.maw.fed', 'The organism took %s food units (%s/%s).', 'Организм принял %s ед. питания (%s/%s).')
    name('message.iis.bio.shuttle', 'Your escape shuttle crashed nearby: %s %s %s. Something that came aboard with you still smoulders inside the wreck.',
         'Рядом разбился ваш спасательный челнок: %s %s %s. В обломках тлеет то, что вы привезли с заражённого корабля.')
    for s, en, ru in (('heartbeat', 'Heartbeat', 'Удар сердца'), ('growl', 'Hungry growl', 'Голодное урчание'), ('squelch', 'Squelch', 'Хлюпанье'),
                      ('tear', 'Flesh tears', 'Плоть рвётся'), ('gulp', 'Gulp', 'Глоток'), ('chew', 'Chewing', 'Жевание'),
                      ('grow', 'Tissue grows', 'Ткань растёт'), ('hiss', 'Hiss', 'Шипение')):
        name(f'subtitles.iis.bio.{s}', en, ru)
    # срез Б-1 (D70): ручная биохимия очага
    import importlib.util as _ilu
    _spec = _ilu.spec_from_file_location('iis_gen_bio_b1', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bio_b1.py'))
    _b1 = _ilu.module_from_spec(_spec)
    _spec.loader.exec_module(_b1)
    stats['b1'] = _b1.generate_b1(g)
    return stats


def _merge_json(path, update, is_tag=False):
    data = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else ({'replace': False, 'values': []} if is_tag else {})
    if is_tag:
        for v in update:
            if v not in data['values']:
                data['values'].append(v)
    else:
        data.update(update)
        data = dict(sorted(data.items()))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


if __name__ == '__main__':
    # Standalone run (the full generator needs the GT5 sources, which exist only in the cloud environment):
    #   python -I tools/gen/bio.py     (from the repo root) — writes only the bio files into src/generated and merges lang/tags.
    ROOT = os.getcwd()
    GEN = os.path.join(ROOT, 'src', 'generated', 'resources')
    NS_ = 'iis'

    def _write(path, obj):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
            f.write('\n')

    lang_ = {'en_us': {}, 'ru_ru': {}}
    tags_ = {}
    A_, D_ = os.path.join(GEN, 'assets', NS_), os.path.join(GEN, 'data')
    st = generate({'write': _write, 'A': A_, 'D': D_, 'NS': NS_, 'lang': lang_, 'block_tags': tags_,
                   'RES': os.path.join(ROOT, 'src', 'main', 'resources')})
    for code, entries in lang_.items():
        _merge_json(os.path.join(A_, 'lang', code + '.json'), entries)
    for t, vals in tags_.items():
        ns, path = t.split(':')
        _merge_json(os.path.join(D_, ns, 'tags', 'block', path + '.json'), vals, is_tag=True)
    print('bio:', st)
