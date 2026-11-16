"""Cyber implants (D157): the surgery table, fourteen implants (seven body areas, tiers HV and EV), their looks, names and recipes.

Owner's answers: implants start at HV, a surgery table, simple permanent bonuses (13.11); the body has its own EU reserve (an internal
cell), two tiers HV and EV, implants stay after death, surgery costs EU and time and hurts (16.11).
The rules are in ru.iis.implant.ImplantLogic and Implants.BONUSES; the numbers here and there are the AI's draft (D157).
Called from auto.generate with its helpers (machine blocks, machine recipes).
"""
import os

from PIL import Image, ImageDraw

NS = 'iis'
SLOTS = ['cell', 'eyes', 'brain', 'heart', 'arms', 'legs', 'skin']
SLOT_NAMES = {'cell': ('Internal Cell', 'Внутренняя батарея', 'Cell', 'Батарея'), 'eyes': ('Optic Implant', 'Глазной имплант', 'Eyes', 'Глаза'),
              'brain': ('Neural Interface', 'Нейроинтерфейс', 'Brain', 'Мозг'), 'heart': ('Artificial Heart', 'Искусственное сердце', 'Heart', 'Сердце'),
              'arms': ('Reinforced Arm Joints', 'Усиленные суставы рук', 'Arms', 'Руки'), 'legs': ('Reinforced Leg Joints', 'Усиленные суставы ног', 'Legs', 'Ноги'),
              'skin': ('Subdermal Plating', 'Подкожная броня', 'Skin', 'Кожа')}
TIERS = {'hv': (1, 'advanced', 'StainlessSteel', 480, (236, 196, 64)), 'ev': (2, 'data', 'Titanium', 1920, (120, 204, 255))}
# what an implant does, per tier level L (1 for HV, 2 for EV): (en, ru)
EFFECT = {
    'cell': lambda L: (f'Body reserve: {L * L} 000 000 EU. Without a cell no implant works.', f'Запас тела: {L * L} 000 000 EU. Без батареи импланты не работают.'),
    'eyes': lambda L: ('Night vision' + ('; no blindness or darkness' if L == 2 else ''), 'Ночное зрение' + ('; не слепнут и не темнеют' if L == 2 else '')),
    'brain': lambda L: (f'Reach +{L} block', f'Дальность руки +{L} блок' + ('а' if L == 2 else '')),
    'heart': lambda L: (f'Health +{3 * L} hearts' + ('; slow healing' if L == 2 else ''), f'Здоровье +{3 * L} сердца' + ('; медленное лечение' if L == 2 else '')),
    'arms': lambda L: (f'Mining speed +{25 * L}%, attack damage +{2 * L}', f'Скорость копания +{25 * L}%, урон +{2 * L}'),
    'legs': lambda L: (f'Speed +{15 * L}%, higher jump, safe fall +{3 * L} blocks' + ('; steps up a block' if L == 2 else ''),
                       f'Скорость +{15 * L}%, выше прыжок, безопасное падение +{3 * L} блока' + ('; шагает на блок' if L == 2 else '')),
    'skin': lambda L: (f'Armour +{4 * L}' + ('; toughness +2' if L == 2 else ''), f'Броня +{4 * L}' + ('; твёрдость +2' if L == 2 else '')),
}
LANG = {
    'itemGroup.iis.implants': ('Implants', 'Импланты'),
    'message.iis.implant.no_table': ('Stand next to a surgery table', 'Встаньте рядом с хирургическим столом'),
    'message.iis.implant.empty_slot': ('Nothing is installed there', 'В этой области ничего не стоит'),
    'message.iis.implant.busy': ('The table is busy with an operation', 'Стол занят операцией'),
    'message.iis.implant.no_power': ('The table has %s EU, the operation needs %s', 'В столе %s EU, на операцию нужно %s'),
    'message.iis.implant.started': ('The operation has begun: stay next to the table', 'Операция началась: не отходите от стола'),
    'message.iis.implant.progress': ('Operation: %s%%', 'Операция: %s%%'),
    'message.iis.implant.installed': ('The implant is installed', 'Имплант установлен'),
    'message.iis.implant.removed': ('The implant is removed', 'Имплант снят'),
    'message.iis.implant.aborted': ('The operation is aborted', 'Операция прервана'),
    'message.iis.implant.header': ('Body: %s / %s EU, implants use %s EU/t. Table: %s / %s EU', 'Тело: %s / %s EU, импланты тратят %s EU/т. Стол: %s / %s EU'),
    'message.iis.implant.free': ('free', 'свободно'),
    'message.iis.implant.remove': ('[remove]', '[снять]'),
}


def drain(slot, level):
    return 0 if slot == 'cell' else (1 if slot == 'eyes' else 2) * level


def icon(slot, colour):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    steel, dark = (150, 156, 166, 255), (58, 62, 72, 255)
    c = colour + (255,)
    if slot == 'cell':
        d.rectangle([4, 3, 11, 13], fill=steel, outline=dark)
        d.rectangle([6, 1, 9, 2], fill=dark)
        d.rectangle([6, 5, 9, 11], fill=c)
    elif slot == 'eyes':
        d.ellipse([1, 4, 14, 12], fill=steel, outline=dark)
        d.ellipse([5, 5, 10, 11], fill=c, outline=dark)
        d.point([(7, 7), (8, 7), (7, 8), (8, 8)], fill=dark)
    elif slot == 'brain':
        d.ellipse([2, 2, 13, 12], fill=steel, outline=dark)
        d.line([(7, 3), (7, 11)], fill=dark)
        d.line([(4, 6), (6, 8), (4, 10)], fill=c)
        d.line([(11, 5), (9, 7), (11, 9)], fill=c)
        d.rectangle([6, 12, 9, 14], fill=dark)
    elif slot == 'heart':
        d.polygon([(2, 5), (4, 3), (7, 5), (10, 3), (13, 5), (13, 8), (8, 13), (2, 8)], fill=steel, outline=dark)
        d.line([(4, 7), (6, 7), (7, 5), (8, 9), (9, 7), (11, 7)], fill=c)
    elif slot == 'arms':
        d.line([(3, 13), (7, 8)], fill=steel, width=3)
        d.line([(7, 8), (12, 4)], fill=steel, width=3)
        d.ellipse([5, 6, 9, 10], fill=c, outline=dark)
        d.rectangle([11, 2, 13, 4], fill=dark)
    elif slot == 'legs':
        d.line([(6, 1), (6, 7)], fill=steel, width=3)
        d.line([(6, 7), (9, 12)], fill=steel, width=3)
        d.ellipse([4, 5, 8, 9], fill=c, outline=dark)
        d.rectangle([7, 12, 12, 14], fill=dark)
    else:
        for y in range(2, 14, 4):
            for x in range(2, 14, 4):
                d.rectangle([x + (2 if (y // 4) % 2 else 0) - 1, y, x + (2 if (y // 4) % 2 else 0) + 2, y + 2], fill=steel, outline=dark)
        d.line([(1, 14), (14, 14)], fill=c)
    return im


def table_top(casing_top, colour):
    im = casing_top.copy()
    d = ImageDraw.Draw(im)
    d.rectangle([2, 2, 13, 13], fill=(206, 214, 220, 255), outline=(70, 74, 84, 255))
    d.rectangle([7, 4, 8, 11], fill=(200, 40, 40, 255))
    d.rectangle([4, 7, 11, 8], fill=(200, 40, 40, 255))
    return im


def generate(g, h):
    """h: the helpers of auto.generate."""
    write, A, tag_of = g['write'], g['A'], g['tag_of']
    name, machine, tooltip, ing, out, mrec, casing, shaped, tex_out = (h[k] for k in ('name', 'machine', 'tooltip', 'ing', 'out', 'mrec', 'casing', 'shaped', 'tex_out'))

    # the surgery table: an HV machine
    s, top, bot = casing('hv', 'SIDE'), casing('hv', 'TOP'), casing('hv', 'BOTTOM')
    machine('surgery_table', {'north': s, 'south': s, 'east': s, 'west': s, 'up': table_top(top, (200, 40, 40)), 'down': bot})
    name(f'block.{NS}.surgery_table', 'Surgery Table', 'Хирургический стол')
    tooltip('surgery_table', [('HV machine: installs and removes cyber implants for EU and 10 seconds; it hurts.', 'Машина HV: ставит и снимает киберимпланты за EU и 10 секунд; это больно.'),
                              ('Right-click with an implant: install. Right-click with an empty hand: your body, with [remove] lines.',
                               'ПКМ имплантом — установить. ПКМ пустой рукой — состояние тела и строки [снять].'),
                              ('Charges the body of a player standing next to it.', 'Заряжает тело игрока, стоящего рядом.')])
    shaped('implants/surgery_table', f'{NS}:surgery_table', 1, ['PSP', 'RMR', 'CCC'],
           {'P': tag_of('plate', 'StainlessSteel'), 'S': f'{NS}:sensor_hv', 'R': f'{NS}:robot_arm_hv', 'M': f'{NS}:hull_hv', 'C': '#c:circuits/advanced'})

    # implants
    for slot in SLOTS:
        en, ru, slot_en, slot_ru = SLOT_NAMES[slot]
        name(f'implant.{NS}.slot.{slot}', slot_en, slot_ru)
        for t, (level, circ, metal, eut, colour) in TIERS.items():
            iid = f'implant_{slot}_{t}'
            icon(slot, colour).save(tex_out(f'item/implant/{iid}.png'))
            write(os.path.join(A, 'models', 'item', f'implant/{iid}.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/implant/{iid}'}})
            write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/implant/{iid}'}})
            name(f'item.{NS}.{iid}', f'{en} ({t.upper()})', f'{ru} ({t.upper()})')
            eff_en, eff_ru = EFFECT[slot](level)
            name(f'item.{NS}.{iid}.tooltip.0', f'Body area: {slot_en.lower()}. {eff_en}', f'Область тела: {slot_ru.lower()}. {eff_ru}')
            d = drain(slot, level)
            name(f'item.{NS}.{iid}.tooltip.1', (f'Uses {d} EU/t of the body reserve. ' if d else '') + 'Installed on a surgery table.',
                 (f'Тратит {d} EU/т из запаса тела. ' if d else '') + 'Ставится на хирургическом столе.')
            plate, c = tag_of('plate', metal), f'#c:circuits/{circ}'
            parts = {'cell': [ing(f'{NS}:energy_crystal' if t == 'hv' else f'{NS}:lapotron_crystal')],
                     'eyes': [ing(f'{NS}:sensor_{t}', 2), ing(f'{NS}:lens_diamond')],
                     'brain': [ing(f'{NS}:sensor_{t}'), ing(c, 2)],
                     'heart': [ing(f'{NS}:electric_pump_{t}'), ing(f'{NS}:electric_motor_{t}')],
                     'arms': [ing(f'{NS}:robot_arm_{t}', 2)],
                     'legs': [ing(f'{NS}:electric_piston_{t}', 2), ing(f'{NS}:electric_motor_{t}', 2)],
                     'skin': [ing(plate, 6), ing(f'{NS}:emitter_{t}')]}[slot]
            base = [ing(plate, 2), ing(c, 2)]
            if t == 'ev':   # the EV implant is an upgrade of the HV one
                base.append(ing(f'{NS}:implant_{slot}_hv'))
            mrec('assembler', iid, parts + base, [out(f'{NS}:{iid}')], 600, eut)
    for key, (en, ru) in LANG.items():
        name(key, en, ru)
    return {'implants': len(SLOTS) * len(TIERS)}
