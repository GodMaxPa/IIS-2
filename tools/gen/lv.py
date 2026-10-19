"""LV age content generator (slice 4), called from gen_content.py with its globals.

Source of truth: GT5-II (IdealIndustrial/GT5Unofficial @ 00021e3) and the IIS 1.19.1 pack (configs, scripts/*.zs).
Original classes/lines are named next to each rule. Java lists the same ids (ru.iis.lv.*).
"""
import json
import os
import shutil

from PIL import Image

# ----------------------------------------------------------------------------------------------------------------
# Catalogue
# ----------------------------------------------------------------------------------------------------------------
# GT_Loader_MetaTileEntities#makeWires (HardCoreCableLoss=false): material, loss insulated, loss bare, amps per 1x, voltage
WIRES = [
    ('RedAlloy', 0, 1, 1, 8),
    ('Lead', 2, 4, 2, 32),
    ('Tin', 1, 2, 1, 32),
    ('Copper', 2, 4, 1, 128),
    ('AnnealedCopper', 1, 2, 1, 128),
    ('Cupronickel', 3, 6, 2, 128),   # makeWires(Cupronickel, …, 3/6 (bEC), 2 A, V[2]) — for heating coils
]
WIRE_SIZES = [(1, 0.125, 0.25), (2, 0.25, 0.375), (4, 0.375, 0.5)]   # size, bare thickness, insulated thickness (GT)

# LV basic machines: id, GT name (lang/overlay), GT GUI, recipe map (None = vanilla smelting), inputs, outputs, tank, EN
MACHINES = [
    ('macerator',     'macerator',     'MACERATOR',        'Macerator1',   'macerator',     1, 1, 0,     'Macerator'),
    ('electric_furnace', 'e_furnace',  'ELECTRIC_FURNACE', 'E_Furnace',    None,            1, 1, 0,     'Electric Furnace'),
    ('alloy_smelter', 'alloysmelter',  'ALLOY_SMELTER',    'AlloySmelter', 'alloy_smelter', 6, 1, 0,     'Alloy Smelter'),
    ('compressor',    'compressor',    'COMPRESSOR',       'Compressor',   'compressor',    1, 1, 0,     'Compressor'),
    ('extractor',     'extractor',     'EXTRACTOR',        'Extractor',    'extractor',     1, 1, 0,     'Extractor'),
    ('forge_hammer',  'hammer',        'HAMMER',           'Hammer',       'forge_hammer',  1, 1, 0,     'Forge Hammer'),
    ('wiremill',      'wiremill',      'WIREMILL',         'Wiremill',     'wiremill',      1, 1, 0,     'Wiremill'),
    ('bender',        'bender',        'BENDER',           'Bender',       'bender',        2, 1, 0,     'Bending Machine'),
    ('lathe',         'lathe',         'LATHE',            'Lathe',        'lathe',         1, 2, 0,     'Lathe'),
    ('assembler',     'assembler',     'ASSEMBLER',        'Assembler',    'assembler',     6, 1, 16000, 'Assembling Machine'),
    ('mixer',         'mixer',         'MIXER',            'Mixer',        'mixer',         6, 1, 8000,  'Mixer'),
    ('polarizer',     'polarizer',     'POLARIZER',        'Polarizer',    'polarizer',     1, 1, 0,     'Polarizer'),
    ('ore_washer',    'orewasher',     'ORE_WASHER',       'OreWasher',    'ore_washer',    2, 3, 16000, 'Ore Washing Plant'),
]
GENERATORS = [
    # id, GT lang, texture prefix, EN
    ('steam_turbine_lv', 'basicgenerator.steamturbine.tier.01', 'STEAM_TURBINE', 'Basic Steam Turbine'),
    ('diesel_generator_lv', 'basicgenerator.diesel.tier.01', 'DIESEL_GENERATOR', 'Basic Combustion Generator'),
    ('gas_turbine_lv', 'basicgenerator.gasturbine.tier.01', 'GAS_TURBINE', 'Basic Gas Turbine'),
]

# Simple items: id, texture source ('gt:<file in lv/items>' or 'gen:<procedural kind>'), GT lang key, EN, RU fallback
ITEMS = [
    ('electric_motor_lv', 'gt:600', 'gt.metaitem.01.32600.name', 'Electric Motor (LV)', 'Электродвигатель (LV)'),
    ('electric_pump_lv', 'gt:610', 'gt.metaitem.01.32610.name', 'Electric Pump (LV)', 'Электрическая помпа (LV)'),
    ('conveyor_module_lv', 'gt:630', 'gt.metaitem.01.32630.name', 'Conveyor Module (LV)', 'Конвейерный модуль (LV)'),
    ('electric_piston_lv', 'gt:640', 'gt.metaitem.01.32640.name', 'Electric Piston (LV)', 'Электропоршень (LV)'),
    ('robot_arm_lv', 'gt:650', 'gt.metaitem.01.32650.name', 'Robot Arm (LV)', 'Манипулятор (LV)'),
    ('circuit_basic', 'gt:701', 'gt.metaitem.01.32701.name', 'Integrated Logic Circuit', 'Логическая интегральная электросхема'),
    ('vacuum_tube', 'gt:700', 'gt.metaitem.01.32700.name', 'Vacuum Tube', 'Вакуумная трубка'),
    ('coated_circuit_board', 'gt:710', 'gt.metaitem.01.32710.name', 'Coated Circuit Board', 'Покрытая печатная плата'),
    ('resistor', 'gt:716', 'gt.metaitem.01.32716.name', 'Resistor', 'Резистор'),
    ('glass_tube', 'gt:glass_tube', 'gt.metaitem.03.32012.name', 'Glass Tube', 'Стеклянная трубочка'),
    ('battery_lv', 'gt:519', 'gt.metaitem.01.32519.name', 'Small Battery (LV)', 'Малая батарейка (LV)'),
    ('steel_item_casing', 'gen:casing', None, 'Steel Item Casing', 'Стальная оболочка'),
    ('sticky_resin', 'gen:resin', None, 'Sticky Resin', 'Липкая смола'),
    ('coal_coke', 'gen:coke', None, 'Coal Coke', 'Коксовый уголь'),
    ('wet_coke_oven_brick', 'gen:wet_brick', None, 'Wet Coke Oven Brick', 'Сырой кирпич коксовой печи'),
    ('treetap', 'gen:treetap', None, 'Treetap', 'Краник'),
    ('multimeter', 'gen:multimeter', None, 'Multimeter', 'Мультиметр'),
    ('plate_wood', 'gen:wood_plate', 'gt.metaitem.01.17809.name', 'Wood Plate', 'Деревянная пластина'),
]

# Generator fuels (GT_Recipe_Map sDieselFuels / sTurbineFuels from Materials FuelPower/FuelType, IIS MaterialProperties.cfg):
# only fluids that exist in this slice are registered; the rest arrive with chemistry.
FUEL_TYPES = {0: 'diesel', 1: 'gas'}


def generate(g):
    write, A, D, NS, lang, TEX, REF = g['write'], g['A'], g['D'], g['NS'], g['lang'], g['TEX'], g['REF']
    ref, tag_of, prop, mat_id, names = g['ref'], g['tag_of'], g['prop'], g['mat_id'], g['names']
    shaped, shapeless, smelting = g['shaped'], g['shapeless'], g['smelting']
    add_item_tag, block_tags, mat_by_name = g['add_item_tag'], g['block_tags'], g['mat_by_name']
    item_ref = g['item_ref']
    ru_lang = json.load(open(os.path.join(REF, 'gt5_lang_ru_machines.json'), encoding='utf-8'))
    LV = os.path.join(TEX, 'lv')
    stats = {}

    def ru(key, fallback):
        v = ru_lang.get(key) if key else None
        return v.strip() if v else fallback

    def tex_out(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def img(path):
        return Image.open(path).convert('RGBA')

    def save_anim(im, rel, meta_src=None):
        im.save(tex_out(rel + '.png'))
        if meta_src and os.path.exists(meta_src):
            shutil.copy(meta_src, tex_out(rel + '.png.mcmeta'))

    def layer(base_path, overlay_path):
        """base (16x16) + overlay (16 x 16n, animated) -> composited frames."""
        b = img(base_path).crop((0, 0, 16, 16))
        if overlay_path is None or not os.path.exists(overlay_path):
            return b, None
        o = img(overlay_path)
        frames = max(1, o.height // 16)
        out = Image.new('RGBA', (16, 16 * frames))
        for f in range(frames):
            out.paste(Image.alpha_composite(b, o.crop((0, 16 * f, 16, 16 * f + 16))), (0, 16 * f))
        meta = overlay_path + '.mcmeta'
        return out, (meta if frames > 1 and os.path.exists(meta) else None)

    def name(key, en, ru_text):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru_text

    def cube_model(model_id, faces):
        write(os.path.join(A, 'models', 'block', model_id + '.json'), {
            'parent': 'minecraft:block/cube',
            'textures': {'particle': faces['side'], 'north': faces['front'], 'south': faces['back'], 'east': faces['side'],
                         'west': faces['side'], 'up': faces['top'], 'down': faces['bottom']}})

    def facing_blockstate(bid, has_active=True):
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for active in ((False, True) if has_active else (None,)):
                key = f'facing={facing}' + ('' if active is None else f',active={str(active).lower()}')
                v = {'model': f'{NS}:block/{bid}' + ('_active' if active else '')}
                if y:
                    v['y'] = y
                variants[key] = v
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})

    def block_item(bid, model=None):
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': model or f'{NS}:block/{bid}'}})

    def simple_loot(bid, drop=None):
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': drop or f'{NS}:{bid}'}]}]})

    def pickaxe(bid, level=1):
        block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')
        if level == 1:
            block_tags['minecraft:needs_stone_tool'].append(f'{NS}:{bid}')

    def tooltip_lines(bid, lines):
        for k, (en, ruu) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', en, ruu)

    def gt_desc(idx):
        out = []
        i = 0
        while f'TileEntity_DESCRIPTION_{idx}_Index_{i}' in ru_lang:
            line = ru_lang[f'TileEntity_DESCRIPTION_{idx}_Index_{i}'].strip()
            if 'загрязнени' not in line:
                out.append(line)
            i += 1
        return out

    def item_model(iid, layer0):
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': layer0}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})

    # --------------------------------------------------------------------------------------------------------
    # Procedural textures (D40: own graphics for things the reference has no texture for)
    # --------------------------------------------------------------------------------------------------------
    import random

    def noise_tex(base, var, seed, size=16):
        rnd = random.Random(seed)
        im = Image.new('RGBA', (size, size))
        for y in range(size):
            for x in range(size):
                d = rnd.randint(-var, var)
                im.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in base) + (255,))
        return im

    def gen_item(kind):
        rnd = random.Random(kind)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        if kind == 'resin':
            for y in range(4, 14):
                for x in range(4, 13):
                    if (x - 8.5) ** 2 / 20 + (y - 9) ** 2 / 25 < 1:
                        c = 150 + rnd.randint(-20, 20)
                        im.putpixel((x, y), (c, int(c * 0.6), 20, 255))
        elif kind == 'coke':
            for y in range(3, 14):
                for x in range(3, 14):
                    if abs(x - 8) + abs(y - 8) < 7 + rnd.randint(0, 1):
                        c = 50 + rnd.randint(-15, 25)
                        im.putpixel((x, y), (c, c, c + 8, 255))
        elif kind == 'wet_brick':
            for y in range(5, 12):
                for x in range(2, 14):
                    c = 120 + rnd.randint(-15, 15)
                    im.putpixel((x, y), (c, int(c * 0.85), int(c * 0.6), 255))
        elif kind == 'casing':
            for y in range(4, 12):
                for x in range(3, 13):
                    edge = x in (3, 12) or y in (4, 11)
                    c = 110 if edge else 150 + rnd.randint(-8, 8)
                    im.putpixel((x, y), (c, c, c + 5, 255))
        elif kind == 'wood_plate':
            for y in range(3, 13):
                for x in range(3, 13):
                    c = 150 + rnd.randint(-15, 15) - (10 if y % 3 == 0 else 0)
                    im.putpixel((x, y), (c, int(c * 0.72), int(c * 0.45), 255))
        elif kind == 'multimeter':
            for y in range(2, 15):
                for x in range(4, 12):
                    edge = x in (4, 11) or y in (2, 14)
                    im.putpixel((x, y), (60, 60, 60, 255) if edge else (230, 190, 40, 255))
            for y in range(4, 8):
                for x in range(6, 10):
                    im.putpixel((x, y), (90, 200, 120, 255))
            for (x, y) in ((7, 10), (8, 10), (7, 11), (8, 11)):
                im.putpixel((x, y), (40, 40, 40, 255))
            for y in range(12, 16):
                im.putpixel((5, y), (200, 30, 30, 255)); im.putpixel((10, y), (30, 30, 30, 255))
        elif kind == 'treetap':
            for y in range(6, 11):
                for x in range(2, 12):
                    c = 140 + rnd.randint(-10, 10)
                    im.putpixel((x, y), (c, int(c * 0.7), int(c * 0.45), 255))
            for y in range(10, 14):
                for x in range(10, 13):
                    im.putpixel((x, y), (120, 85, 50, 255))
        return im

    # --------------------------------------------------------------------------------------------------------
    # Simple items
    # --------------------------------------------------------------------------------------------------------
    for iid, src, key, en, ruf in ITEMS:
        kind, val = src.split(':')
        if kind == 'gt':
            shutil.copy(os.path.join(LV, 'items', val + '.png'), tex_out(f'item/{iid}.png'))
        else:
            gen_item(val).save(tex_out(f'item/{iid}.png'))
        item_model(iid, f'{NS}:item/{iid}')
        name(f'item.{NS}.{iid}', en, ru(key, ruf))
    item_ref[('plate', 'Wood')] = f'{NS}:plate_wood'
    add_item_tag('c:plates/wood', f'{NS}:plate_wood')
    add_item_tag('c:circuits/basic', f'{NS}:circuit_basic')   # GT OrePrefixes.circuit Basic
    name(f'item.{NS}.battery_lv.tooltip', 'Stores %s / %s EU', 'Запас: %s / %s EU')

    # creosote (Railcraft coke oven byproduct; GT Materials.Creosote, FuelType 3 = semifluid generator)
    name(f'fluid_type.{NS}.creosote', 'Creosote Oil', ru('fluid.creosote', 'Креозотовое масло'))
    name(f'item.{NS}.creosote_bucket', 'Creosote Bucket', 'Ведро креозотового масла')
    cre = noise_tex((90, 60, 20), 8, 'creosote')
    frames = Image.new('RGBA', (16, 16 * 8))
    for f in range(8):
        frames.paste(noise_tex((90, 60, 20), 10, f'creosote{f}'), (0, 16 * f))
    frames.save(tex_out('block/fluid/creosote.png'))
    with open(tex_out('block/fluid/creosote.png.mcmeta'), 'w') as fh:
        json.dump({'animation': {'frametime': 4}}, fh)
    bucket = img(os.path.join(TEX, 'vanilla_bucket.png')) if os.path.exists(os.path.join(TEX, 'vanilla_bucket.png')) else None
    if bucket is None:
        bucket = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(4, 15):
            for x in range(3, 13):
                bucket.putpixel((x, y), (150, 150, 155, 255))
        for y in range(5, 9):
            for x in range(4, 12):
                bucket.putpixel((x, y), (90, 60, 20, 255))
    bucket.save(tex_out('item/creosote_bucket.png'))
    item_model('creosote_bucket', f'{NS}:item/creosote_bucket')
    write(os.path.join(A, 'blockstates', 'creosote.json'), {'variants': {'': {'model': f'{NS}:block/creosote'}}})
    write(os.path.join(A, 'models', 'block', 'creosote.json'), {'textures': {'particle': f'{NS}:block/fluid/creosote'}})
    name(f'block.{NS}.creosote', 'Creosote Oil', ru('fluid.creosote', 'Креозотовое масло'))
    # electricity damage (GT_DamageSources#getElectricDamage: bypasses armour)
    write(os.path.join(D, NS, 'damage_type', 'electricity.json'), {'message_id': 'iis.electricity', 'exhaustion': 0.1, 'scaling': 'never'})
    write(os.path.join(D, 'minecraft', 'tags', 'damage_type', 'bypasses_armor.json'), {'replace': False, 'values': [f'{NS}:electricity']})
    # coke as furnace fuel (Railcraft coal coke: 3200 ticks)
    write(os.path.join(D, 'neoforge', 'data_maps', 'item', 'furnace_fuels.json'), {'values': {f'{NS}:coal_coke': {'burn_time': 3200}}})

    # --------------------------------------------------------------------------------------------------------
    # Wires and cables (GT_MetaPipeEntity_Cable)
    # --------------------------------------------------------------------------------------------------------
    wire_specs = []
    insulation = img(os.path.join(LV, 'iconsets', 'INSULATION_FULL.png'))
    for mat, loss_ins, loss, amps, volts in WIRES:
        m = mat_by_name[mat]
        color = m['color']
        rgb = ((color >> 16) & 255, (color >> 8) & 255, color & 255)
        iset = m['iconSet'] if os.path.isdir(os.path.join(LV, 'materialicons', m['iconSet'])) else 'METALLIC'
        wire_src = os.path.join(LV, 'materialicons', iset, 'wire.png')
        if not os.path.exists(wire_src):
            wire_src = os.path.join(LV, 'materialicons', 'METALLIC', 'wire.png')
        w = img(wire_src).crop((0, 0, 16, 16))
        tinted = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                r, gg, b, a = w.getpixel((x, y))
                tinted.putpixel((x, y), (r * rgb[0] // 255, gg * rgb[1] // 255, b * rgb[2] // 255, a))
        mid = mat_id(mat)
        tinted.save(tex_out(f'block/cable/wire_{mid}.png'))
        for size, bare, ins in WIRE_SIZES:
            for insulated in (False, True):
                bid = f'{"cable" if insulated else "wire"}_{mid}_{size}x'
                thick = (ins if insulated else bare) * 16
                lo, hi = (16 - thick) / 2, (16 + thick) / 2
                side = f'{NS}:block/cable/insulation' if insulated else f'{NS}:block/cable/wire_{mid}'
                end = f'{NS}:block/cable/{"cable" if insulated else "wire"}_{mid}_{size}x_end' if insulated else side
                if insulated:
                    ring = os.path.join(LV, 'iconsets', {1: 'INSULATION_SMALL', 2: 'INSULATION_MEDIUM', 4: 'INSULATION_MEDIUM_PLUS'}[size] + '.png')
                    endimg = Image.alpha_composite(tinted, img(ring).crop((0, 0, 16, 16)))
                    endimg.save(tex_out(f'block/cable/cable_{mid}_{size}x_end.png'))
                allf = ('north', 'south', 'east', 'west', 'up', 'down')
                write(os.path.join(A, 'models', 'block', bid + '_core.json'), {
                    'parent': 'minecraft:block/block', 'textures': {'particle': side, 'side': side, 'end': end},
                    'elements': [{'from': [lo, lo, lo], 'to': [hi, hi, hi], 'faces': {f: {'texture': '#end'} for f in allf}}]})
                write(os.path.join(A, 'models', 'block', bid + '_arm.json'), {
                    'parent': 'minecraft:block/block', 'textures': {'particle': side, 'side': side, 'end': end},
                    'elements': [{'from': [lo, lo, 0], 'to': [hi, hi, lo],
                                  'faces': {'north': {'texture': '#end', 'cullface': 'north'}, 'east': {'texture': '#side'},
                                            'west': {'texture': '#side'}, 'up': {'texture': '#side'}, 'down': {'texture': '#side'}}}]})
                rot = {'north': {}, 'south': {'y': 180}, 'east': {'y': 90}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
                parts = [{'apply': {'model': f'{NS}:block/{bid}_core'}}]
                for d, r in rot.items():
                    parts.append({'when': {d: 'true'}, 'apply': dict({'model': f'{NS}:block/{bid}_arm'}, **r)})
                write(os.path.join(A, 'blockstates', bid + '.json'), {'multipart': parts})
                write(os.path.join(A, 'models', 'item', bid + '.json'), {
                    'parent': 'minecraft:block/block', 'textures': {'particle': side, 'side': side, 'end': end},
                    'elements': [{'from': [lo, lo, 0], 'to': [hi, hi, 16],
                                  'faces': {'north': {'texture': '#end'}, 'south': {'texture': '#end'}, 'east': {'texture': '#side'},
                                            'west': {'texture': '#side'}, 'up': {'texture': '#side'}, 'down': {'texture': '#side'}}}],
                    'display': {'gui': {'rotation': [30, 45, 0], 'scale': [0.625, 0.625, 0.625]}}})
                write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{bid}'}})
                simple_loot(bid)
                block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')
                gt_key = f'gt.blockmachines.{"cable" if insulated else "wire"}.{mat.lower()}.{size:02d}.name'
                en = f'{size}x {m["en"]} {"Cable" if insulated else "Wire"}'
                name(f'block.{NS}.{bid}', en, ru(gt_key, en))
                v_loss = loss_ins if insulated else loss
                tooltip_lines(bid, [(f'Max Voltage: {volts} EU/t ({["ULV", "LV", "MV", "HV"][[8, 32, 128, 512].index(volts)]})',
                                     f'Макс. напряжение: {volts} EU/t ({["ULV", "LV", "MV", "HV"][[8, 32, 128, 512].index(volts)]})'),
                                    (f'Max Amperage: {amps * size}', f'Макс. ампераж: {amps * size}'),
                                    (f'Loss/Meter/Ampere: {v_loss} EU-Volt', f'Потери на блок: {v_loss} В')]
                              + ([] if insulated else [('Not insulated: shocks on touch', 'Без изоляции: бьёт током')]))
                wire_specs.append({'id': bid, 'material': mid, 'size': size, 'insulated': insulated, 'voltage': volts,
                                   'amperage': amps * size, 'loss': v_loss, 'thickness': thick})
                add_item_tag(f'iis:{"cables" if insulated else "wires"}/{size}x/{mid}', f'{NS}:{bid}')
                add_item_tag(f'iis:{"cables" if insulated else "wires"}', f'{NS}:{bid}')
    insulation.crop((0, 0, 16, 16)).save(tex_out('block/cable/insulation.png'))
    write(os.path.join(g['RES'], NS, 'cables.json'), wire_specs)
    stats['cables'] = len(wire_specs)

    def W(mat, size=1):
        return f'#iis:wires/{size}x/{mat_id(mat)}'

    def C(mat, size=1):
        return f'#iis:cables/{size}x/{mat_id(mat)}'

    # wire recipes: ProcessingWire (plate + wire cutter -> 1x; 2x <-> 1x; insulation) and wiremill
    for mat, *_ in WIRES:
        mid = mat_id(mat)
        if ref('plate', mat):
            shaped(f'wire/{mid}_1x', f'{NS}:wire_{mid}_1x', 1, ['Px'], {'P': tag_of('plate', mat)})
        shapeless(f'wire/{mid}_2x', f'{NS}:wire_{mid}_2x', 1, [W(mat), W(mat)])
        shapeless(f'wire/{mid}_4x', f'{NS}:wire_{mid}_4x', 1, [W(mat, 2), W(mat, 2)])
        shapeless(f'wire/{mid}_2x_split', f'{NS}:wire_{mid}_1x', 2, [W(mat, 2)])
        shapeless(f'wire/{mid}_4x_split', f'{NS}:wire_{mid}_1x', 4, [W(mat, 4)])
        for size, *_ in WIRE_SIZES:
            cost = size // 4 + 1
            if mat == 'RedAlloy':      # ProcessingWire: red alloy is insulated with paper
                shapeless(f'cable/{mid}_{size}x', f'{NS}:cable_{mid}_{size}x', 1, [W(mat, size)] + ['minecraft:paper'] * cost)
            elif mat in ('Lead', 'Tin'):  # black carpet + string
                shapeless(f'cable/{mid}_{size}x', f'{NS}:cable_{mid}_{size}x', 1, [W(mat, size)] + ['minecraft:black_carpet'] * cost + ['minecraft:string'])
            # copper and annealed copper: assembler with molten rubber (fluids of the chemistry slice) -> below via rubber plates

    # --------------------------------------------------------------------------------------------------------
    # Machine casing, hull (GT_Loader_MetaTileEntities: Casing_LV "PPP/PwP/PPP", HardMachineCasings Hull_LV "PHP/CMC")
    # --------------------------------------------------------------------------------------------------------
    ic = os.path.join(LV, 'iconsets')
    for f in ('MACHINE_LV_SIDE', 'MACHINE_LV_TOP', 'MACHINE_LV_BOTTOM'):
        img(os.path.join(ic, f + '.png')).crop((0, 0, 16, 16)).save(tex_out(f'block/lv/{f.lower()}.png'))
    side, top, bottom = (f'{NS}:block/lv/machine_lv_{x}' for x in ('side', 'top', 'bottom'))
    for bid, en, key in (('casing_lv', 'LV Machine Casing', 'gt.blockcasings.1.name'), ('hull_lv', 'LV Machine Hull', 'gt.blockmachines.hull.tier.01.name')):
        cube_model(bid, {'front': side, 'back': side, 'side': side, 'top': top, 'bottom': bottom})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        block_item(bid)
        simple_loot(bid)
        pickaxe(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
    shaped('lv/casing_lv', f'{NS}:casing_lv', 1, ['PPP', 'PwP', 'PPP'], {'P': tag_of('plate', 'Steel')})
    shaped('lv/hull_lv', f'{NS}:hull_lv', 1, ['PHP', 'CMC'],
           {'P': tag_of('plate', 'WroughtIron'), 'H': tag_of('plate', 'Steel'), 'C': C('Tin'), 'M': f'{NS}:casing_lv'})

    def machine_tex(bid, base_dir, faces_src):
        """faces_src: face -> (base iconset name, overlay path or None)."""
        for active in (False, True):
            suf = '_active' if active else ''
            faces = {}
            for face, (base, ov) in faces_src.items():
                ovp = ov(active) if callable(ov) else ov
                im, meta = layer(os.path.join(ic, base + '.png'), ovp)
                rel = f'block/lv/{bid}_{face}{suf}'
                save_anim(im, rel, meta)
                faces[face] = f'{NS}:{rel}'
            cube_model(bid + suf, faces)
        facing_blockstate(bid)
        block_item(bid)
        simple_loot(bid)
        pickaxe(bid)

    # --------------------------------------------------------------------------------------------------------
    # LV basic machines
    # --------------------------------------------------------------------------------------------------------
    machine_list = []
    for mid_, gtn, ov, gui, rmap, nin, nout, tank, en in MACHINES:
        bid = f'{mid_}_lv'
        odir = os.path.join(LV, 'basicmachines', ov.lower())

        def ovf(part):
            return lambda active: os.path.join(odir, f'OVERLAY_{part}{"_ACTIVE" if active else ""}.png')
        machine_tex(bid, odir, {
            'front': ('MACHINE_LV_SIDE', ovf('FRONT')), 'side': ('MACHINE_LV_SIDE', ovf('SIDE')),
            'back': ('MACHINE_LV_SIDE', os.path.join(ic, 'OVERLAY_PIPE_OUT.png')),
            'top': ('MACHINE_LV_TOP', ovf('TOP')), 'bottom': ('MACHINE_LV_BOTTOM', ovf('BOTTOM'))})
        shutil.copy(os.path.join(LV, 'gui', gui + '.png'), tex_out(f'gui/lv/{gui.lower()}.png'))
        name(f'block.{NS}.{bid}', f'Basic {en}', ru(f'gt.blockmachines.basicmachine.{gtn}.tier.01.name', f'Basic {en}'))
        tooltip_lines(bid, [('Voltage IN: 32 EU/t (LV)', 'Входное напряжение: 32 EU/t (LV)'),
                            ('Output side: back', 'Выход: задняя сторона')])
        machine_list.append({'id': bid, 'type': mid_, 'recipeMap': rmap, 'inputs': nin, 'outputs': nout, 'tank': tank,
                             'gui': f'lv/{gui.lower()}'})
    write(os.path.join(g['RES'], NS, 'lv_machines.json'), machine_list)
    stats['machines'] = len(machine_list)

    # --------------------------------------------------------------------------------------------------------
    # Generators, battery buffers, transformer
    # --------------------------------------------------------------------------------------------------------
    for bid, gtl, tp, en in GENERATORS:
        def gf(part):
            return lambda active: os.path.join(ic, f'{tp}_{part}{"_ACTIVE" if active else ""}.png')
        front_ov = os.path.join(ic, 'OVERLAY_ENERGY_OUT.png')
        # GT: front = casing + generator front + energy-out overlay
        faces = {}
        for active in (False, True):
            suf = '_active' if active else ''
            fr, meta = layer(os.path.join(ic, 'MACHINE_LV_SIDE.png'), gf('FRONT')(active))
            fr = Image.alpha_composite(fr.crop((0, 0, 16, 16)), img(front_ov).crop((0, 0, 16, 16)))
            save_anim(fr, f'block/lv/{bid}_front{suf}')
        for part, face, base in (('BACK', 'back', 'MACHINE_LV_SIDE'), ('SIDE', 'side', 'MACHINE_LV_SIDE'), ('TOP', 'top', 'MACHINE_LV_TOP'),
                                 ('BOTTOM', 'bottom', 'MACHINE_LV_BOTTOM')):
            for active in (False, True):
                suf = '_active' if active else ''
                im, meta = layer(os.path.join(ic, base + '.png'), gf(part)(active))
                save_anim(im, f'block/lv/{bid}_{face}{suf}', meta)
        for active in (False, True):
            suf = '_active' if active else ''
            cube_model(bid + suf, {f: f'{NS}:block/lv/{bid}_{f}{suf}' for f in ('front', 'back', 'side', 'top', 'bottom')})
        facing_blockstate(bid)
        block_item(bid)
        simple_loot(bid)
        pickaxe(bid)
        name(f'block.{NS}.{bid}', en, ru(f'gt.blockmachines.{gtl}.name', en))
    name(f'block.{NS}.steam_turbine_lv.tooltip.0', 'Converts Steam into EU: 3 EU per 7 L (86%)', 'Превращает пар в EU: 3 EU за 7 л (86%)')
    name(f'block.{NS}.steam_turbine_lv.tooltip.1', 'Output: 32 EU/t, 1 A from the front', 'Выход: 32 EU/t, 1 А спереди')
    name(f'block.{NS}.diesel_generator_lv.tooltip.0', 'Burns liquid fuels (efficiency 95%)', 'Сжигает жидкое топливо (КПД 95%)')
    name(f'block.{NS}.diesel_generator_lv.tooltip.1', 'Output: 32 EU/t, 1 A from the front', 'Выход: 32 EU/t, 1 А спереди')
    name(f'block.{NS}.gas_turbine_lv.tooltip.0', 'Burns gaseous fuels (efficiency 95%)', 'Сжигает газообразное топливо (КПД 95%)')
    name(f'block.{NS}.gas_turbine_lv.tooltip.1', 'Output: 32 EU/t, 1 A from the front', 'Выход: 32 EU/t, 1 А спереди')
    shutil.copy(os.path.join(LV, 'gui', 'BasicTank.png'), tex_out('gui/lv/basictank.png'))

    # GT_MetaTileEntity_BasicBatteryBuffer: front = energy out, other sides plain casing
    for bid, n, gtl, gui in (('battery_buffer_1x1_lv', 1, 'batterybuffer.01.tier.01', '1by1'), ('battery_buffer_2x2_lv', 4, 'batterybuffer.04.tier.01', '2by2')):
        for face, base, ov in (('front', 'MACHINE_LV_SIDE', os.path.join(ic, 'OVERLAY_ENERGY_OUT.png')), ('side', 'MACHINE_LV_SIDE', None),
                               ('top', 'MACHINE_LV_TOP', None), ('bottom', 'MACHINE_LV_BOTTOM', None)):
            im, _ = layer(os.path.join(ic, base + '.png'), ov)
            im.crop((0, 0, 16, 16)).save(tex_out(f'block/lv/{bid}_{face}.png'))
        cube_model(bid, {'front': f'{NS}:block/lv/{bid}_front', 'back': f'{NS}:block/lv/{bid}_side', 'side': f'{NS}:block/lv/{bid}_side',
                         'top': f'{NS}:block/lv/{bid}_top', 'bottom': f'{NS}:block/lv/{bid}_bottom'})
        facing_blockstate(bid, has_active=False)
        block_item(bid)
        simple_loot(bid)
        pickaxe(bid)
        en = f'Battery Buffer {"1x1" if n == 1 else "2x2"} (LV)'
        rut = ru(f'gt.blockmachines.{gtl}.name', en)
        if '1x1' not in rut and '2x2' not in rut and '4x' not in rut:
            rut = f'{rut} {"1x1" if n == 1 else "2x2"}'
        shutil.copy(os.path.join(LV, 'gui', gui + '.png'), tex_out(f'gui/lv/{gui}.png'))
        name(f'block.{NS}.{bid}', en, rut)
        tooltip_lines(bid, [('Charges batteries, outputs from the front', 'Заряжает батарейки, выдаёт энергию спереди'),
                            (f'Output: 32 EU/t, up to {n} A (one per battery)', f'Выход: 32 EU/t, до {n} А (по ампер на батарейку)')])
    # GT_MetaTileEntity_Transformer: step-down (default) — front = MV in (IN_MULTI), other sides LV out (OUT);
    # step-up — front = MV out (OUT_MULTI), other sides LV in (IN). Facing: any of 6 directions.
    for mode, front_ov, side_ov in (('', 'OVERLAY_ENERGY_IN_MULTI', 'OVERLAY_ENERGY_OUT'), ('_up', 'OVERLAY_ENERGY_OUT_MULTI', 'OVERLAY_ENERGY_IN')):
        for face, base, ov in (('front', 'MACHINE_LV_SIDE', front_ov), ('side', 'MACHINE_LV_SIDE', side_ov),
                               ('top', 'MACHINE_LV_TOP', side_ov), ('bottom', 'MACHINE_LV_BOTTOM', side_ov)):
            im, _ = layer(os.path.join(ic, base + '.png'), os.path.join(ic, ov + '.png'))
            im.crop((0, 0, 16, 16)).save(tex_out(f'block/lv/transformer_lv{mode}_{face}.png'))
        t = lambda f: f'{NS}:block/lv/transformer_lv{mode}_{f}'
        cube_model('transformer_lv' + mode, {'front': t('front'), 'back': t('side'), 'side': t('side'), 'top': t('top'), 'bottom': t('bottom')})
    variants = {}
    for facing, rot in (('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}), ('down', {'x': 90}), ('up', {'x': 270})):
        for up in (False, True):
            variants[f'facing={facing},step_up={str(up).lower()}'] = dict({'model': f'{NS}:block/transformer_lv' + ('_up' if up else '')}, **rot)
    write(os.path.join(A, 'blockstates', 'transformer_lv.json'), {'variants': variants})
    block_item('transformer_lv')
    simple_loot('transformer_lv')
    pickaxe('transformer_lv')
    name(f'block.{NS}.transformer_lv', 'LV Transformer', ru('gt.blockmachines.transformer.tier.01.name', 'LV Transformer'))
    tooltip_lines('transformer_lv', [('MV -> LV: input 128 EU/t 1 A at the front, output 32 EU/t 4 A at the other sides',
                                      'MV -> LV: вход 128 EU/t 1 А спереди, выход 32 EU/t 4 А с остальных сторон'),
                                     ('Wrench + sneak: invert (LV -> MV)', 'Ключ с Shift: обратить (LV -> MV)')])

    # --------------------------------------------------------------------------------------------------------
    # Coke oven (Railcraft; IIS RailCraft.zs "Coke Oven to LV age")
    # --------------------------------------------------------------------------------------------------------
    brick = noise_tex((120, 70, 45), 12, 'coke_brick')
    for y in range(16):
        for x in range(16):
            if y % 4 == 3 or (x + (y // 4) * 8) % 16 == 0:
                brick.putpixel((x, y), (70, 55, 45, 255))
    brick.save(tex_out('block/lv/coke_oven_bricks.png'))
    front = brick.copy()
    for y in range(5, 13):
        for x in range(4, 12):
            front.putpixel((x, y), (30, 25, 20, 255))
    front.save(tex_out('block/lv/coke_oven_front.png'))
    front_act = front.copy()
    for y in range(8, 13):
        for x in range(5, 11):
            front_act.putpixel((x, y), (230, 120 + (x * 13) % 60, 20, 255))
    front_act.save(tex_out('block/lv/coke_oven_front_active.png'))
    # coke oven GUI (own, D40): GT-style panel, slots, arrow (66,34) + filled arrow at (176,0), creosote tank (120,15)
    gui = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    BG, LIGHT, DARK, SLOT = (198, 198, 198, 255), (255, 255, 255, 255), (85, 85, 85, 255), (139, 139, 139, 255)
    for y in range(166):
        for x in range(176):
            gui.putpixel((x, y), BG)
    for x in range(176):
        gui.putpixel((x, 0), LIGHT); gui.putpixel((x, 165), DARK)
    for y in range(166):
        gui.putpixel((0, y), LIGHT); gui.putpixel((175, y), DARK)

    def slot(x, y, w=18, h=18):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                gui.putpixel((xx, yy), SLOT)
        for xx in range(x, x + w):
            gui.putpixel((xx, y), DARK); gui.putpixel((xx, y + h - 1), LIGHT)
        for yy in range(y, y + h):
            gui.putpixel((x, yy), DARK); gui.putpixel((x + w - 1, yy), LIGHT)
    for (x, y) in ((43, 34), (97, 34), (151, 16), (151, 52)):
        slot(x, y)
    slot(120, 15, 18, 58)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 83 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 141)

    def arrow(ox, oy, col):
        for y in range(17):
            for x in range(24):
                if (5 <= y <= 11 and x < 15) or (x >= 15 and abs(y - 8) <= 23 - x):
                    gui.putpixel((ox + x, oy + y), col)
    arrow(66, 34, (139, 139, 139, 255))
    arrow(176, 0, (255, 255, 255, 255))
    # bucket hint arrow between bucket slots
    for y in range(36, 51):
        gui.putpixel((160, y), DARK)
    gui.save(tex_out('gui/lv/coke_oven.png'))
    write(os.path.join(A, 'models', 'block', 'coke_oven_bricks.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/lv/coke_oven_bricks'}})
    write(os.path.join(A, 'blockstates', 'coke_oven_bricks.json'), {'variants': {'': {'model': f'{NS}:block/coke_oven_bricks'}}})
    block_item('coke_oven_bricks')
    simple_loot('coke_oven_bricks')
    pickaxe('coke_oven_bricks')
    for suf in ('', '_active'):
        b = f'{NS}:block/lv/coke_oven_bricks'
        cube_model('coke_oven' + suf, {'front': f'{NS}:block/lv/coke_oven_front{suf}', 'back': b, 'side': b, 'top': b, 'bottom': b})
    facing_blockstate('coke_oven')
    block_item('coke_oven')
    simple_loot('coke_oven')
    pickaxe('coke_oven')
    name(f'block.{NS}.coke_oven_bricks', 'Coke Oven Bricks', 'Кирпич коксовой печи')
    name(f'block.{NS}.coke_oven', 'Coke Oven', 'Коксовая печь')
    tooltip_lines('coke_oven', [('Controller of a 3x3x3 coke oven, hollow inside', 'Контроллер коксовой печи 3x3x3, пустой внутри'),
                                ('Built from 25 Coke Oven Bricks, controller in the middle of a side', 'Строится из 25 кирпичей коксовой печи, контроллер — в центре стенки'),
                                ('Coal -> Coal Coke + 500 L Creosote in 90 s', 'Уголь -> кокс + 500 л креозота за 90 с')])
    smelting('smelting/coke_oven_bricks', f'{NS}:wet_coke_oven_brick', f'{NS}:coke_oven_bricks', 1, xp=0.1)
    shaped('lv/coke_oven', f'{NS}:coke_oven', 1, ['BBB', 'B B', 'BBB'], {'B': f'{NS}:coke_oven_bricks'})
    add_item_tag('iis:fuels/coke', f'{NS}:coal_coke')

    # --------------------------------------------------------------------------------------------------------
    # Rubber tree (IC2 rubber tree replacement, D43)
    # --------------------------------------------------------------------------------------------------------
    bark = noise_tex((95, 80, 55), 14, 'rubber_bark')
    for y in range(16):
        for x in (3, 9, 13):
            bark.putpixel((x, y), (70, 58, 40, 255))
    bark.save(tex_out('block/lv/rubber_log.png'))
    top_ = noise_tex((170, 140, 90), 10, 'rubber_top')
    for y in range(16):
        for x in range(16):
            r2 = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if r2 > 49:
                top_.putpixel((x, y), (95, 80, 55, 255))
            elif int(r2 ** 0.5) % 3 == 0:
                top_.putpixel((x, y), (140, 110, 70, 255))
    top_.save(tex_out('block/lv/rubber_log_top.png'))
    for state, col in (('wet', (230, 170, 40)), ('dry', (120, 90, 50))):
        t = bark.copy()
        for y in range(5, 11):
            for x in range(5, 11):
                if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 8:
                    t.putpixel((x, y), col + (255,))
        t.save(tex_out(f'block/lv/rubber_log_{state}.png'))
    leaves = Image.new('RGBA', (16, 16))
    rnd = random.Random('leaves')
    for y in range(16):
        for x in range(16):
            if rnd.random() < 0.8:
                c = rnd.randint(90, 160)
                leaves.putpixel((x, y), (c, c, c, 255))
    leaves.save(tex_out('block/lv/rubber_leaves.png'))
    sap = Image.new('RGBA', (16, 16))
    for y in range(4, 16):
        sap.putpixel((7, y), (100, 80, 50, 255))
        sap.putpixel((8, y), (100, 80, 50, 255))
    for y in range(1, 9):
        for x in range(3, 13):
            if (x - 7.5) ** 2 + (y - 5) ** 2 < 14 and rnd.random() < 0.85:
                sap.putpixel((x, y), (60, 120 + rnd.randint(0, 40), 40, 255))
    sap.save(tex_out('block/lv/rubber_sapling.png'))
    write(os.path.join(A, 'models', 'block', 'rubber_log.json'), {'parent': 'minecraft:block/cube_column',
          'textures': {'end': f'{NS}:block/lv/rubber_log_top', 'side': f'{NS}:block/lv/rubber_log'}})
    for state in ('wet', 'dry'):
        write(os.path.join(A, 'models', 'block', f'rubber_log_{state}.json'), {'parent': 'minecraft:block/cube',
              'textures': {'particle': f'{NS}:block/lv/rubber_log', 'north': f'{NS}:block/lv/rubber_log_{state}',
                           'south': f'{NS}:block/lv/rubber_log', 'east': f'{NS}:block/lv/rubber_log', 'west': f'{NS}:block/lv/rubber_log',
                           'up': f'{NS}:block/lv/rubber_log_top', 'down': f'{NS}:block/lv/rubber_log_top'}})
    variants = {}
    for resin in ('none', 'wet', 'dry'):
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            v = {'model': f'{NS}:block/rubber_log' + ('' if resin == 'none' else f'_{resin}')}
            if y and resin != 'none':
                v['y'] = y
            variants[f'facing={facing},resin={resin}'] = v
    write(os.path.join(A, 'blockstates', 'rubber_log.json'), {'variants': variants})
    block_item('rubber_log')
    write(os.path.join(A, 'models', 'block', 'rubber_leaves.json'), {'parent': 'minecraft:block/leaves', 'textures': {'all': f'{NS}:block/lv/rubber_leaves'}})
    write(os.path.join(A, 'blockstates', 'rubber_leaves.json'), {'variants': {'': {'model': f'{NS}:block/rubber_leaves'}}})
    write(os.path.join(A, 'items', 'rubber_leaves.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/rubber_leaves',
          'tints': [{'type': 'minecraft:constant', 'value': 0x5F8F3F}]}})
    write(os.path.join(A, 'models', 'block', 'rubber_sapling.json'), {'parent': 'minecraft:block/cross', 'textures': {'cross': f'{NS}:block/lv/rubber_sapling'}})
    write(os.path.join(A, 'blockstates', 'rubber_sapling.json'), {'variants': {'': {'model': f'{NS}:block/rubber_sapling'}}})
    item_model('rubber_sapling', f'{NS}:block/lv/rubber_sapling')
    simple_loot('rubber_log')
    simple_loot('rubber_sapling')
    write(os.path.join(D, NS, 'loot_table', 'blocks', 'rubber_leaves.json'), {
        'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/rubber_leaves',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'name': f'{NS}:rubber_leaves', 'conditions': [{'condition': 'minecraft:match_tool',
             'predicate': {'items': 'minecraft:shears'}}]},
            {'type': 'minecraft:item', 'name': f'{NS}:rubber_sapling', 'conditions': [{'condition': 'minecraft:survives_explosion'},
             {'condition': 'minecraft:table_bonus', 'enchantment': 'minecraft:fortune', 'chances': [0.05, 0.0625, 0.083333336, 0.1]}]}]}]}]})
    for bid, en, ruu in (('rubber_log', 'Rubber Wood', 'Каучуковое дерево'), ('rubber_leaves', 'Rubber Leaves', 'Листья каучукового дерева'),
                         ('rubber_sapling', 'Rubber Sapling', 'Саженец каучукового дерева')):
        name(f'block.{NS}.{bid}', en, ruu)
    tooltip_lines('rubber_log', [('Use a Treetap on the resin spot', 'Добывайте смолу краником из отверстия')])
    block_tags.setdefault('minecraft:logs', []).append(f'{NS}:rubber_log')
    block_tags.setdefault('minecraft:logs_that_burn', []).append(f'{NS}:rubber_log')
    block_tags.setdefault('minecraft:leaves', []).append(f'{NS}:rubber_leaves')
    block_tags.setdefault('minecraft:saplings', []).append(f'{NS}:rubber_sapling')
    block_tags.setdefault('minecraft:mineable/axe', []).append(f'{NS}:rubber_log')
    block_tags.setdefault('minecraft:mineable/hoe', []).append(f'{NS}:rubber_leaves')
    add_item_tag('minecraft:logs', f'{NS}:rubber_log')
    add_item_tag('minecraft:logs_that_burn', f'{NS}:rubber_log')
    add_item_tag('minecraft:saplings', f'{NS}:rubber_sapling')
    shapeless('lv/rubber_log_planks', 'minecraft:jungle_planks', 3, [f'{NS}:rubber_log'])   # IC2: rubber wood -> 3 planks
    shaped('tool/treetap', f'{NS}:treetap', 1, [' P ', 'PPP', 'P  '], {'P': '#minecraft:planks'})   # IC2 treetap shape
    # worldgen: IC2 rubber trees in forests, swamps, jungles (own feature, D43)
    W_ = os.path.join(D, NS, 'worldgen')
    log_states = [{'weight': 6, 'data': {'Name': f'{NS}:rubber_log', 'Properties': {'facing': 'north', 'resin': 'none'}}}]
    for f in ('north', 'east', 'south', 'west'):
        log_states.append({'weight': 1, 'data': {'Name': f'{NS}:rubber_log', 'Properties': {'facing': f, 'resin': 'wet'}}})
    write(os.path.join(W_, 'configured_feature', 'rubber_tree.json'), {'type': 'minecraft:tree', 'config': {
        'ignore_vines': True, 'force_dirt': False,
        'minimum_size': {'type': 'minecraft:two_layers_feature_size', 'limit': 1, 'lower_size': 0, 'upper_size': 1},
        'dirt_provider': {'type': 'minecraft:simple_state_provider', 'state': {'Name': 'minecraft:dirt'}},
        'trunk_provider': {'type': 'minecraft:weighted_state_provider', 'entries': log_states},
        'foliage_provider': {'type': 'minecraft:simple_state_provider', 'state': {'Name': f'{NS}:rubber_leaves', 'Properties':
                             {'distance': '7', 'persistent': 'false', 'waterlogged': 'false'}}},
        'trunk_placer': {'type': 'minecraft:straight_trunk_placer', 'base_height': 5, 'height_rand_a': 2, 'height_rand_b': 1},
        'foliage_placer': {'type': 'minecraft:spruce_foliage_placer', 'radius': 2, 'offset': 0, 'trunk_height': {'type': 'minecraft:uniform', 'min_inclusive': 1, 'max_inclusive': 2}},
        'decorators': []}})
    write(os.path.join(W_, 'placed_feature', 'rubber_tree.json'), {'feature': f'{NS}:rubber_tree', 'placement': [
        {'type': 'minecraft:rarity_filter', 'chance': 6}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
        {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'},
        {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:would_survive', 'state': {'Name': f'{NS}:rubber_sapling', 'Properties': {'stage': '0'}}}},
        {'type': 'minecraft:biome'}]})
    write(os.path.join(D, NS, 'neoforge', 'biome_modifier', 'rubber_trees.json'), {
        'type': 'neoforge:add_features', 'biomes': ['minecraft:forest', 'minecraft:birch_forest', 'minecraft:dark_forest', 'minecraft:swamp',
                                                    'minecraft:jungle', 'minecraft:sparse_jungle', 'minecraft:mangrove_swamp', 'minecraft:old_growth_birch_forest'],
        'features': f'{NS}:rubber_tree', 'step': 'vegetal_decoration'})

    # --------------------------------------------------------------------------------------------------------
    # Crafting recipes of components and machines
    # --------------------------------------------------------------------------------------------------------
    P = lambda m: tag_of('plate', m)
    R = lambda m: tag_of('rod', m)
    CIRC = '#c:circuits/basic'
    MOTOR, PISTON, PUMP, CONV, ARM = (f'{NS}:{x}' for x in ('electric_motor_lv', 'electric_piston_lv', 'electric_pump_lv', 'conveyor_module_lv', 'robot_arm_lv'))
    HULL = f'{NS}:hull_lv'
    # GT_CraftingRecipeLoader:336 magnetic iron rod: iron rod + 4 redstone
    shapeless('lv/iron_magnetic_rod', ref('rod', 'IronMagnetic'), 1, [R('Iron')] + ['minecraft:redstone'] * 4)
    # GT_MetaGenerated_Item_01: components LV
    shaped('lv/electric_motor_lv', MOTOR, 1, ['CWR', 'WIW', 'RWC'], {'I': R('IronMagnetic'), 'R': R('Iron'), 'W': W('Copper'), 'C': C('Tin')})
    shaped('lv/electric_motor_lv_steel', MOTOR, 1, ['CWR', 'WIW', 'RWC'], {'I': R('SteelMagnetic'), 'R': R('Steel'), 'W': W('Copper'), 'C': C('Tin')})
    shaped('lv/electric_pump_lv', PUMP, 1, ['SXO', 'dPw', 'OMW'], {'M': MOTOR, 'O': tag_of('ring', 'Rubber'), 'X': tag_of('rotor', 'Tin'),
                                                                 'S': tag_of('screw', 'Tin'), 'W': C('Tin'), 'P': f'{NS}:pipe_medium_bronze'})
    shaped('lv/conveyor_module_lv', CONV, 1, ['RRR', 'MCM', 'RRR'], {'M': MOTOR, 'C': C('Tin'), 'R': P('Rubber')})
    shaped('lv/electric_piston_lv', PISTON, 1, ['PPP', 'CSS', 'CMG'], {'P': P('Steel'), 'S': R('Steel'), 'G': tag_of('small_gear', 'Steel'),
                                                                     'M': MOTOR, 'C': C('Tin')})
    shaped('lv/robot_arm_lv', ARM, 1, ['CCC', 'MSM', 'PES'], {'S': R('Steel'), 'M': MOTOR, 'P': PISTON, 'E': CIRC, 'C': C('Tin')})
    # circuits: ProcessingCircuit "RIR/VBV/CCC", GT_MachineRecipeLoader:440,463,471
    shaped('lv/circuit_basic', f'{NS}:circuit_basic', 1, ['RIR', 'VBV', 'CCC'], {'R': f'{NS}:resistor', 'I': f'{NS}:steel_item_casing',
                                                                               'V': f'{NS}:vacuum_tube', 'B': f'{NS}:coated_circuit_board', 'C': C('RedAlloy')})
    shaped('lv/resistor_coal', f'{NS}:resistor', 3, [' P ', 'FCF', ' P '], {'P': 'minecraft:paper', 'F': tag_of('wire_fine', 'Copper'), 'C': tag_of('dust', 'Coal')})
    shaped('lv/resistor_charcoal', f'{NS}:resistor', 3, [' P ', 'FCF', ' P '], {'P': 'minecraft:paper', 'F': tag_of('wire_fine', 'Copper'), 'C': tag_of('dust', 'Charcoal')})
    shaped('lv/vacuum_tube', f'{NS}:vacuum_tube', 1, ['PGP', 'FFF'], {'G': f'{NS}:glass_tube', 'P': 'minecraft:paper', 'F': tag_of('wire_fine', 'Copper')})
    shaped('lv/coated_circuit_board', f'{NS}:coated_circuit_board', 3, [' R ', 'PPP', ' R '], {'P': '#c:plates/wood', 'R': f'{NS}:sticky_resin'})
    # deviations (no source recipe in the reference, see PORTING_NOTES): glass tube, steel item casing, wood plate, battery
    shaped('lv/glass_tube', f'{NS}:glass_tube', 2, ['G G', 'G G', ' G '], {'G': 'minecraft:glass_pane'})
    shaped('lv/steel_item_casing', f'{NS}:steel_item_casing', 2, ['hP'], {'P': P('Steel')})
    shaped('lv/plate_wood', f'{NS}:plate_wood', 1, ['s', 'P'], {'P': '#minecraft:planks'})
    # multimeter (D51): own LV recipe — GT5 Portable Scanner was HV only
    shaped('lv/multimeter', f'{NS}:multimeter', 1, ['PGP', 'CEC', 'PCP'], {'P': P('Steel'), 'G': 'minecraft:glass_pane', 'C': C('Tin'), 'E': CIRC})
    shaped('lv/battery_lv', f'{NS}:battery_lv', 1, [' C ', 'TRT', 'TRT'], {'C': C('Tin'), 'T': P('Tin'), 'R': 'minecraft:redstone'})

    # LV machines (GT_Loader_MetaTileEntities, X.* placeholders resolved for LV)
    X = {'M': HULL, 'E': MOTOR, 'P': PISTON, 'C': CIRC, 'W': C('Tin'), 'V': CONV, 'A': ARM, 'U': PUMP,
         'H': W('Copper', 2), 'O': W('Copper', 4), 'R': tag_of('rotor', 'Tin'), 'G': 'minecraft:glass', 'Z': W('Tin', 2), 'S': R('Iron'),
         'D': 'minecraft:diamond', 'N': 'minecraft:anvil'}

    def mcraft(bid, pattern, letters):
        shaped(f'lv/{bid}', f'{NS}:{bid}', 1, pattern, {k: X[k] for k in letters})
    mcraft('macerator_lv', ['PED', 'WWM', 'CCW'], 'PEDWMC')
    mcraft('electric_furnace_lv', ['CHC', 'HMH', 'WHW'], 'CHWM')         # GT "ECE/CMC/WCW": E circuit, C heating coil, W cable
    mcraft('alloy_smelter_lv', ['COC', 'OMO', 'WOW'], 'COWM')            # same with double heating coil
    mcraft('compressor_lv', [' C ', 'PMP', 'WCW'], 'CPMW')
    mcraft('extractor_lv', ['GCG', 'PMU', 'WCW'], 'GCPMUW')
    mcraft('forge_hammer_lv', ['WPW', 'CMC', 'WNW'], 'WPCMN')
    mcraft('wiremill_lv', ['EWE', 'CMC', 'EWE'], 'EWCM')
    mcraft('bender_lv', ['PwP', 'CMC', 'EWE'], 'PCMEW')
    mcraft('lathe_lv', ['WCW', 'EMD', 'CWP'], 'WCEMDP')
    mcraft('assembler_lv', ['ACA', 'VMV', 'WCW'], 'ACVMW')
    mcraft('mixer_lv', ['GRG', 'GEG', 'CMC'], 'GRECM')
    mcraft('polarizer_lv', ['ZSZ', 'WMW', 'ZSZ'], 'ZSWM')
    mcraft('ore_washer_lv', ['RGR', 'CEC', 'WMW'], 'RGCEWM')
    # generators: GT_Loader_MetaTileEntities:1167/1177/1185
    shaped('lv/diesel_generator_lv', f'{NS}:diesel_generator_lv', 1, ['PCP', 'EME', 'GWG'],
           {'M': HULL, 'P': PISTON, 'E': MOTOR, 'C': CIRC, 'W': C('Tin'), 'G': tag_of('gear', 'Steel')})
    shaped('lv/gas_turbine_lv', f'{NS}:gas_turbine_lv', 1, ['CRC', 'RMR', 'EWE'],
           {'M': HULL, 'E': MOTOR, 'R': tag_of('rotor', 'Tin'), 'C': CIRC, 'W': C('Tin')})
    shaped('lv/steam_turbine_lv', f'{NS}:steam_turbine_lv', 1, ['PCP', 'RMR', 'EWE'],
           {'M': HULL, 'E': MOTOR, 'R': tag_of('rotor', 'Tin'), 'C': CIRC, 'W': C('Tin'), 'P': f'{NS}:pipe_medium_bronze'})
    # GT_Loader_MetaTileEntities:205 transformer, :499 battery buffer ("WTW/WMW", T = chest)
    shaped('lv/transformer_lv', f'{NS}:transformer_lv', 1, [' BB', 'CM ', ' BB'], {'M': HULL, 'C': C('Copper'), 'B': C('Tin')})
    shaped('lv/battery_buffer_1x1_lv', f'{NS}:battery_buffer_1x1_lv', 1, ['WTW', 'WMW'], {'M': HULL, 'W': W('Tin'), 'T': '#c:chests/wooden'})
    shaped('lv/battery_buffer_2x2_lv', f'{NS}:battery_buffer_2x2_lv', 1, ['WTW', 'WMW'], {'M': HULL, 'W': W('Tin', 4), 'T': '#c:chests/wooden'})

    # --------------------------------------------------------------------------------------------------------
    # Machine recipes
    # --------------------------------------------------------------------------------------------------------
    machine_recipes = {}

    def ing(item, count=1):
        return {'ingredient': item, 'count': count}

    def out(item, count=1, chance=10000):
        o = {'id': item, 'count': count}
        if chance < 10000:
            o['chance'] = chance
        return o

    def mrec(rtype, rid, inputs, outputs, duration, eut, fin=None, fout=None):
        outputs = [o for o in outputs if o['id']]
        if any(i['ingredient'] is None for i in inputs) or (not outputs and not fout):
            return
        key = f'{rtype}/{rid}'
        if key in machine_recipes:
            return
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(duration), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if fout:
            r['fluid_outputs'] = [fout]
        machine_recipes[key] = r

    WATER = lambda n: {'ingredient': 'minecraft:water', 'amount': n}
    for n in sorted(names):
        if n in g['NOT_GENERATED_MATERIALS']:
            continue
        mid = mat_id(n)
        st = g['subtags'](n)
        mass = max(1, g['mass'](n))
        ingot, plate, rod, bolt = ref('ingot', n), ref('plate', n), ref('rod', n), ref('bolt', n)
        # ProcessingIngot:58 bender ingot -> plate (mass, 24); ProcessingFoil:20 4 plates -> 4 foil (mass, 24)
        if ingot and plate:
            mrec('bender', f'plate_{mid}', [ing(tag_of('ingot', n))], [out(plate)], mass, 24)
        if plate and ref('foil', n):
            mrec('bender', f'foil_{mid}', [ing(tag_of('plate', n), 4)], [out(ref('foil', n), 4)], mass, 24)
        # ProcessingStick:20 lathe ingot -> rod + 2 small dust (mass*5, 16); ProcessingBolt? lathe bolt from rod? (cutter, MV)
        if ingot and rod and 'NO_WORKING' not in st:
            mrec('lathe', f'rod_{mid}', [ing(tag_of('ingot', n))], [out(rod), out(ref('small_dust', prop(n, 'MaterialMacerateInto', n)) or ref('small_dust', n), 2)], mass * 5, 16)
        # ProcessingWire / ProcessingFineWire wiremill: ingot -> 2 wire (100, 4); rod -> wire (50, 4); wire -> 4 fine (200, 8)
        if any(w[0] == n for w in WIRES):
            w1 = f'{NS}:wire_{mid}_1x'
            if ingot:
                mrec('wiremill', f'wire_{mid}', [ing(tag_of('ingot', n))], [out(w1, 2)], 100, 4)
            if rod:
                mrec('wiremill', f'wire_from_rod_{mid}', [ing(tag_of('rod', n))], [out(w1)], 50, 4)
            if ref('wire_fine', n):
                mrec('wiremill', f'fine_wire_{mid}', [ing(W(n))], [out(ref('wire_fine', n), 4)], 200, 8)
        elif ref('wire_fine', n) and ingot:   # fine wire of non-cable metals (gold, steel, ...): ingot -> 8 fine? GT: via wire; keep ingot->2 wire missing -> skip
            pass
        # ProcessingFineWire:24 crafting: foil + wire cutter -> fine wire
        if ref('wire_fine', n) and ref('foil', n):
            shaped(f'wire/fine_{mid}', ref('wire_fine', n), 1, ['Fx'], {'F': tag_of('foil', n)})
        # ProcessingPlate:92 crafting: plate + hammer -> 2 foil (GT tBits recipe "hX")
        if ref('foil', n) and plate:
            shaped(f'plate/foil_{mid}', ref('foil', n), 2, ['hP'], {'P': tag_of('plate', n)})
        # ProcessingGear / ProcessingRotor / ProcessingStickLong crafting
        if ref('gear', n) and plate and rod:
            shaped(f'part/gear_{mid}', ref('gear', n), 1, ['SPS', 'PwP', 'SPS'], {'P': tag_of('plate', n), 'S': tag_of('rod', n)})
        if ref('small_gear', n) and plate:
            shaped(f'part/small_gear_{mid}', ref('small_gear', n), 1, ['P ', ' h'], {'P': tag_of('plate', n)})
        if ref('rotor', n) and plate and ref('ring', n) and ref('screw', n):
            shaped(f'part/rotor_{mid}', ref('rotor', n), 1, ['PhP', 'SRf', 'PdP'], {'P': tag_of('plate', n), 'R': tag_of('ring', n), 'S': tag_of('screw', n)})
        if ref('long_rod', n) and rod:
            shaped(f'part/long_rod_{mid}', ref('long_rod', n), 1, ['ShS'], {'S': tag_of('rod', n)})
        # ProcessingDirty: ore washer crushed -> purified + tiny byproduct + stone dust, 1000 L water, 500 t, 16 EU/t
        if ref('crushed', n) and ref('purified', n):
            bys = prop(n, 'ListMaterialByProducts', []) or []
            by = bys[0] if bys else n
            mrec('ore_washer', f'crushed_{mid}', [ing(tag_of('crushed', n))],
                 [out(ref('purified', n)), out(ref('tiny_dust', by)), out(ref('dust', 'Stone'))], 500, 16, WATER(1000))
        if ref('impure_dust', n) and ref('dust', n):
            # ProcessingDustImpure: washing in a cauldron/ore washer -> clean dust (GT_ModHandler.addOreWasherRecipe for dustImpure)
            mrec('ore_washer', f'impure_{mid}', [ing(tag_of('impure_dust', n))], [out(ref('dust', n))], 8, 4, WATER(100))
        # LV macerator gives all outputs (steam ones keep only the first): already in iis:macerator
    # ProcessingTransforming: polarizer iron/wrought iron/steel forms -> magnetic (max(16, amount*128/M), 16 EU/t)
    for src, dst in (('Iron', 'IronMagnetic'), ('WroughtIron', 'IronMagnetic'), ('Steel', 'SteelMagnetic')):
        for p, ticks in (('rod', 64), ('ingot', 128), ('plate', 128), ('dust', 128), ('long_rod', 128)):
            if ref(p, src) and ref(p, dst):
                mrec('polarizer', f'{p}_{mat_id(src)}', [ing(tag_of(p, src))], [out(ref(p, dst))], ticks, 16)
    # IIS RailCraft.zs:254 Mixer: 4 clay dust + 5 sand + 100 L water -> wet coke oven brick (20 t, 16 EU/t)
    mrec('mixer', 'wet_coke_oven_brick', [ing(tag_of('dust', 'Clay'), 4), ing('#c:sands', 5)], [out(f'{NS}:wet_coke_oven_brick')], 20, 16, WATER(100))
    # GT_MachineRecipeLoader mixer alloy dusts (bronze, brass, red alloy, battery alloy, soldering alloy, invar, electrum, cupronickel)
    for rid, ins, o, oc, t in (('bronze', [('Copper', 3), ('Tin', 1)], 'Bronze', 4, 400), ('brass', [('Copper', 3), ('Zinc', 1)], 'Brass', 4, 400),
                               ('red_alloy', [('Copper', 1), ('Redstone', 4)], 'RedAlloy', 1, 100),
                               ('battery_alloy', [('Lead', 4), ('Antimony', 1)], 'BatteryAlloy', 5, 250),
                               ('soldering_alloy', [('Tin', 9), ('Antimony', 1)], 'SolderingAlloy', 10, 500),
                               ('invar', [('Iron', 2), ('Nickel', 1)], 'Invar', 3, 300), ('electrum', [('Gold', 1), ('Silver', 1)], 'Electrum', 2, 200),
                               ('cupronickel', [('Copper', 1), ('Nickel', 1)], 'Cupronickel', 2, 200)):
        if all(ref('dust', m) for m, _ in ins) and ref('dust', o):
            mrec('mixer', f'{rid}_dust', [ing(tag_of('dust', m), c) for m, c in ins], [out(ref('dust', o), oc)], t, 8)
    # alloy smelter (LV has 6 slots): battery alloy, soldering alloy; rubber (GT_MachineRecipeLoader:1235, restored, D45)
    for a, an, b, bn, o, on in (('Lead', 4, 'Antimony', 1, 'BatteryAlloy', 5), ('Tin', 9, 'Antimony', 1, 'SolderingAlloy', 10)):
        if ref('ingot', o):
            for pa in ('ingot', 'dust'):
                for pb in ('ingot', 'dust'):
                    if ref(pa, a) and ref(pb, b):
                        mrec('alloy_smelter', f'{mat_id(o)}_{pa}_{mat_id(a)}_{pb}_{mat_id(b)}', [ing(tag_of(pa, a), an), ing(tag_of(pb, b), bn)],
                             [out(ref('ingot', o), on)], on * 50, 16)
    if ref('dust', 'RawRubber') and ref('dust', 'Sulfur') and ref('ingot', 'Rubber'):
        mrec('alloy_smelter', 'rubber', [ing(tag_of('dust', 'RawRubber'), 3), ing(tag_of('dust', 'Sulfur'))], [out(ref('ingot', 'Rubber'))], 200, 8)
    # extractor: sticky resin -> 3 raw rubber dust, rubber log / sapling / 16 leaves (GT_MachineRecipeLoader:885-887, ProcessingLog:21)
    # D43 rubber from slime: GT_MachineRecipeLoader:881 smelting slimeball -> resin, :884 extractor slimeball -> 2 raw rubber
    smelting('smelting/sticky_resin_from_slime', 'minecraft:slime_ball', f'{NS}:sticky_resin', 1, xp=0.1)
    mrec('extractor', 'slime_ball', [ing('minecraft:slime_ball')], [out(ref('dust', 'RawRubber'), 2)], 300, 2)
    mrec('extractor', 'sticky_resin', [ing(f'{NS}:sticky_resin')], [out(ref('dust', 'RawRubber'), 3)], 300, 2)
    mrec('extractor', 'rubber_log', [ing(f'{NS}:rubber_log')], [out(ref('dust', 'RawRubber'))], 300, 2)
    mrec('extractor', 'rubber_sapling', [ing(f'{NS}:rubber_sapling')], [out(ref('dust', 'RawRubber'))], 300, 2)
    mrec('extractor', 'rubber_leaves', [ing(f'{NS}:rubber_leaves', 16)], [out(ref('dust', 'RawRubber'))], 300, 2)
    # compressor: wood dust -> wood plate (GT_MetaGenerated_Item_01:49)
    if ref('dust', 'Wood'):
        mrec('compressor', 'plate_wood', [ing(tag_of('dust', 'Wood'))], [out(f'{NS}:plate_wood')], 300, 2)
    # macerator: coke -> coke dust? (no coke material) ; coal coke as boiler fuel / BBF fuel handled in Java/steam
    # assembler: GT_MachineRecipeLoader:466 resistors, :441 coated board (no glue -> plates + resin), wire 2x/4x, LV components
    if ref('wire_fine', 'Copper'):
        mrec('assembler', 'resistor_coal', [ing(tag_of('dust', 'Coal')), ing(tag_of('wire_fine', 'Copper'), 4)], [out(f'{NS}:resistor', 12)], 160, 6)
        mrec('assembler', 'resistor_charcoal', [ing(tag_of('dust', 'Charcoal')), ing(tag_of('wire_fine', 'Copper'), 4)], [out(f'{NS}:resistor', 12)], 160, 6)
    mrec('assembler', 'coated_circuit_board', [ing('#c:plates/wood', 8), ing(f'{NS}:sticky_resin')], [out(f'{NS}:coated_circuit_board', 8)], 160, 8)
    for mat, *_ in WIRES:
        mid = mat_id(mat)
        mrec('assembler', f'wire_{mid}_2x', [ing(W(mat), 2)], [out(f'{NS}:wire_{mid}_2x')], 150, 8)
        mrec('assembler', f'wire_{mid}_4x', [ing(W(mat), 4)], [out(f'{NS}:wire_{mid}_4x')], 200, 8)
        if mat in ('Copper', 'AnnealedCopper', 'Cupronickel') and ref('plate', 'Rubber'):
            # ProcessingWire: molten rubber 144*cost in the assembler; until fluids of chemistry exist, rubber plates (D45)
            for size, *_ in WIRE_SIZES:
                mrec('assembler', f'cable_{mid}_{size}x', [ing(W(mat, size)), ing(P('Rubber'), size // 4 + 1)], [out(f'{NS}:cable_{mid}_{size}x')], 100, 8)
    # GT_MachineRecipeLoader:1963 robot arm etc. (assembler variants, 80 t, 30 EU/t)
    mrec('assembler', 'robot_arm_lv', [ing(MOTOR, 2), ing(PISTON), ing(R('Steel'), 2), ing(CIRC), ing(C('Tin'), 3)], [out(ARM)], 80, 30)
    # coke oven (Railcraft defaults COKE_COOK_TIME=1800, creosote 500 L; not in the reference — see PORTING_NOTES)
    mrec('coke_oven', 'coal', [ing('minecraft:coal')], [out(f'{NS}:coal_coke')], 1800, 0, None, {'id': f'{NS}:creosote', 'amount': 500})
    mrec('coke_oven', 'coal_block', [ing('minecraft:coal_block')], [], 1800 * 9, 0, None, {'id': f'{NS}:creosote', 'amount': 4500})
    machine_recipes.pop('coke_oven/coal_block', None)  # needs a coke block; coal only for now
    for key, r in machine_recipes.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    by_type = {}
    for r in machine_recipes.values():
        by_type[r['type']] = by_type.get(r['type'], 0) + 1
    stats['machine_recipes'] = by_type

    # generator fuels from material properties (FuelPower EU per litre, FuelType 0 diesel / 1 gas)
    fuels = {'diesel': {}, 'gas': {}}
    for n, v in g['props'].items():
        fp, ft = v.get('FuelPower', 0), v.get('FuelType', 0)
        if fp and ft in FUEL_TYPES:
            fuels[FUEL_TYPES[ft]][n] = fp
    write(os.path.join(g['RES'], NS, 'fuels.json'), fuels)

    # misc lang
    for k, en, ruu in (('itemGroup.iis.lv', 'IIS 2.0: LV', 'ИИС 2.0: LV'),
                       ('gui.iis.energy', 'Energy', 'Энергия'), ('gui.iis.eu', '%s / %s EU', '%s / %s EU'),
                       ('gui.iis.fluid', '%s: %s / %s L', '%s: %s / %s л'), ('gui.iis.no_energy', 'Not enough energy', 'Недостаточно энергии'),
                       ('gui.iis.empty', 'Empty', 'Пусто'),
                       ('jm.iis.category', 'IIS 2.0', 'ИИС 2.0'),
                       ('jm.iis.ore_grid', 'GT ore chunk grid (3x3 chunks, vein in the centre)', 'Сетка рудных чанков GT (3×3 чанка, жила в центре)'),
                       ('jm.iis.ore_chunk', 'Ore chunk: a GT vein is generated here', 'Рудный чанк: здесь генерируется жила GT'),
                       ('gui.iis.item_transfer.on', 'Item auto-output to the back: ON', 'Автовыгрузка предметов назад: ВКЛ'),
                       ('gui.iis.item_transfer.off', 'Item auto-output to the back: OFF (click)', 'Автовыгрузка предметов назад: ВЫКЛ (клик)'),
                       ('gui.iis.fluid_transfer.on', 'Fluid auto-output: ON', 'Автовыгрузка жидкости: ВКЛ'),
                       ('gui.iis.fluid_transfer.off', 'Fluid auto-output: OFF (click; LV machines have no fluid outputs yet)', 'Автовыгрузка жидкости: ВЫКЛ (клик; у LV машин пока нет жидких выходов)'),
                       ('jei.iis.bender', 'Bending Machine', 'Гибочный станок'), ('jei.iis.lathe', 'Lathe', 'Токарный станок'),
                       ('jei.iis.assembler', 'Assembler', 'Сборщик'), ('jei.iis.mixer', 'Mixer', 'Миксер'),
                       ('jei.iis.polarizer', 'Polarizer', 'Поляризатор'), ('jei.iis.ore_washer', 'Ore Washing Plant', 'Рудопромывочная установка'),
                       ('jei.iis.coke_oven', 'Coke Oven', 'Коксовая печь'), ('message.iis.transformer.down', 'Step down: MV -> LV', 'Понижение: MV -> LV'),
                       ('message.iis.transformer.up', 'Step up: LV -> MV', 'Повышение: LV -> MV'),
                       ('item.iis.multimeter.tooltip.0', 'Right-click a wire or machine: detailed reading', 'ПКМ по проводу или машине — подробный замер'),
                       ('item.iis.multimeter.tooltip.1', 'In hand: live reading of what you look at', 'В руке — живые показания того, на что смотришь'),
                       ('meter.iis.cable.limits', 'Limit: %s V, %s A, loss %s V per block', 'Предел: %s В, %s А, потеря %s В на блок'),
                       ('meter.iis.cable.now', 'Last second: %s V, %s A', 'За последнюю секунду: %s В, %s А'),
                       ('meter.iis.cable.heat', 'Overheat: %s / %s', 'Перегрев: %s / %s'),
                       ('meter.iis.cable.shock', 'Bare wire under current: shock %s damage', 'Голый провод под током: удар %s урона'),
                       ('meter.iis.stored', 'Stored: %s / %s EU', 'Запас: %s / %s EU'),
                       ('meter.iis.input', 'Input: up to %s V, %s A per tick', 'Вход: до %s В, %s А за тик'),
                       ('meter.iis.output', 'Output: %s V, up to %s A', 'Выход: %s В, до %s А'),
                       ('meter.iis.flow', 'Average: in %s EU/t, out %s EU/t (max input %s V)', 'В среднем: приход %s EU/t, расход %s EU/t (наибольшее входное %s В)'),
                       ('meter.iis.progress', 'Working: %s / %s ticks, %s EU/t', 'Работает: %s / %s тиков, %s EU/t'),
                       ('meter.iis.idle', 'Idle', 'Простаивает'),
                       ('meter.iis.stutter', 'Not enough energy: stuttering', 'Не хватает энергии: заикается'),
                       ('meter.iis.tank', 'Tank: %s / %s L', 'Бак: %s / %s л'),
                       ('meter.iis.danger', 'Charged: can explode from rain, water or fire', 'Заряжена: может взорваться от дождя, воды или огня'),
                       ('meter.iis.formed', 'Structure complete', 'Печь собрана'),
                       ('meter.iis.not_formed', 'Structure incomplete', 'Печь не достроена'),
                       ('meter.iis.pollution', 'Chunk pollution: %s', 'Загрязнение чанка: %s'),
                       ('meter.iis.nothing', 'Nothing to measure', 'Нечего измерять'),
                       ('meter.iis.line.cable', '%s V  %s A   (limit %s V / %s A, heat %s)', '%s В  %s А   (предел %s В / %s А, нагрев %s)'),
                       ('meter.iis.line.machine', '%s / %s EU   in %s EU/t   out %s EU/t', '%s / %s EU   приход %s EU/t   расход %s EU/t'),
                       ('message.iis.resin', 'No resin yet', 'Смолы пока нет'), ('gui.iis.coke_oven.incomplete', 'Incomplete structure', 'Печь не достроена'), ('death.attack.iis.electricity', '%1$s was electrocuted', '%1$s убило током')):
        name(k, en, ruu)
    stats['items'] = len(ITEMS)
    return stats
