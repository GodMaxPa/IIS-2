"""Electric blast furnace content (GT_MetaTileEntity_ElectricBlastFurnace) and multiblock parts, called from gen_content.py.

Blocks: frames, heat proof casing, cupronickel coil, LV hatches/buses, maintenance and muffler hatches, EBF controller;
item: LV soldering iron; recipes: crafting + assembler + blast recipes (ProcessingDust, ProcessingOreSmelting,
GT_MachineRecipeLoader). Java lists the same ids (ru.iis.multi.*).
"""
import os
import shutil

from PIL import Image

# GT5 textures: the cloud checkout, or locally a checkout of IdealIndustrial/GT5Unofficial@00021e3 given by IIS_GT5_TEXTURES
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')
FRAMES = ['Invar', 'Steel', 'Iron', 'Bronze', 'Aluminium', 'BlueSteel', 'Titanium', 'TungstenSteel', 'StainlessSteel', 'Polytetrafluoroethylene', 'BlackSteel', 'HSSG', 'HSSE', 'Osmiridium', 'Tritanium', 'Neutronium']  # blue steel: D90; titanium: D97; D98; D104; plascrete D105
HATCHES = [
    # id, overlay, GT lang key, EN
    ('hatch_energy_lv', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.01.name', 'LV Energy Hatch'),
    ('hatch_input_bus_lv', 'OVERLAY_PIPE_IN', 'gt.blockmachines.hatch.input_bus.tier.01.name', 'LV Input Bus'),
    ('hatch_output_bus_lv', 'OVERLAY_PIPE_OUT', 'gt.blockmachines.hatch.output_bus.tier.01.name', 'LV Output Bus'),
    ('hatch_input_lv', 'OVERLAY_PIPE_IN', 'gt.blockmachines.hatch.input.tier.01.name', 'LV Input Hatch'),
    ('hatch_output_lv', 'OVERLAY_PIPE_OUT', 'gt.blockmachines.hatch.output.tier.01.name', 'LV Output Hatch'),
    ('hatch_maintenance', 'OVERLAY_MAINTENANCE', 'gt.blockmachines.hatch.maintenance.name', 'Maintenance Hatch'),
    # D119: GT_Loader_MetaTileEntities:397, 400
    ('hatch_maintenance_semiauto', 'OVERLAY_MAINTENANCE', 'gt.blockmachines.hatch.maintenance.semiauto.name', 'Semi-Auto Maintenance Hatch'),
    ('hatch_maintenance_auto', 'OVERLAY_MAINTENANCE', 'gt.blockmachines.hatch.maintenance.auto.name', 'Auto Maintenance Hatch'),
    ('hatch_muffler_lv', 'OVERLAY_MUFFLER', 'gt.blockmachines.hatch.muffler.tier.01.name', 'LV Muffler Hatch'),
    # D82: GT_Loader_MetaTileEntities:238 (MV casing)
    ('hatch_energy_mv', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.02.name', 'MV Energy Hatch'),
    # D89: GT_Loader_MetaTileEntities:215-216 dynamo hatches (energy out of multiblock generators)
    ('hatch_dynamo_lv', 'OVERLAY_ENERGY_OUT_MULTI', 'gt.blockmachines.hatch.dynamo.tier.01.name', 'LV Dynamo Hatch'),
    ('hatch_dynamo_mv', 'OVERLAY_ENERGY_OUT_MULTI', 'gt.blockmachines.hatch.dynamo.tier.02.name', 'MV Dynamo Hatch'),
    # D93: GT_Loader_MetaTileEntities:217, :239 HV dynamo and energy hatches
    ('hatch_energy_hv', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.03.name', 'HV Energy Hatch'),
    ('hatch_dynamo_hv', 'OVERLAY_ENERGY_OUT_MULTI', 'gt.blockmachines.hatch.dynamo.tier.03.name', 'HV Dynamo Hatch'),
    # D99: GT_Loader_MetaTileEntities:218, :240 EV dynamo and energy hatches
    ('hatch_energy_ev', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.04.name', 'EV Energy Hatch'),
    ('hatch_dynamo_ev', 'OVERLAY_ENERGY_OUT_MULTI', 'gt.blockmachines.hatch.dynamo.tier.04.name', 'EV Dynamo Hatch'),
    ('hatch_energy_iv', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.05.name', 'IV Energy Hatch'),   # D103
    ('hatch_dynamo_iv', 'OVERLAY_ENERGY_OUT_MULTI', 'gt.blockmachines.hatch.dynamo.tier.05.name', 'IV Dynamo Hatch'),
    ('hatch_energy_luv', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.06.name', 'LuV Energy Hatch'),   # D110
    ('hatch_dynamo_luv', 'OVERLAY_ENERGY_OUT_MULTI', 'gt.blockmachines.hatch.dynamo.tier.06.name', 'LuV Dynamo Hatch'),
    ('hatch_energy_zpm', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.07.name', 'ZPM Energy Hatch'),   # D112
    ('hatch_dynamo_zpm', 'OVERLAY_ENERGY_OUT_MULTI', 'gt.blockmachines.hatch.dynamo.tier.07.name', 'ZPM Dynamo Hatch'),
    ('hatch_energy_uv', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.08.name', 'UV Energy Hatch'),   # D121
    ('hatch_dynamo_uv', 'OVERLAY_ENERGY_OUT_MULTI', 'gt.blockmachines.hatch.dynamo.tier.08.name', 'UV Dynamo Hatch'),
    ('hatch_data_access_ev', 'OVERLAY_DATA_ACCESS', 'gt.blockmachines.hatch.dataaccess.name', 'Data Access Hatch'),   # GT_Loader_MetaTileEntities:402 (D110)
]
# D103: buses, fluid hatches and mufflers MV-IV (GT_Loader_MetaTileEntities:294-422)
for _tr, _tn in (('mv', '02'), ('hv', '03'), ('ev', '04'), ('iv', '05'), ('luv', '06'), ('zpm', '07'), ('uv', '08')):   # LuV: D110, ZPM: D112, UV: D121
    _T = 'LuV' if _tr == 'luv' else _tr.upper()
    HATCHES += [(f'hatch_input_bus_{_tr}', 'OVERLAY_PIPE_IN', f'gt.blockmachines.hatch.input_bus.tier.{_tn}.name', f'{_T} Input Bus'),
                (f'hatch_output_bus_{_tr}', 'OVERLAY_PIPE_OUT', f'gt.blockmachines.hatch.output_bus.tier.{_tn}.name', f'{_T} Output Bus'),
                (f'hatch_input_{_tr}', 'OVERLAY_PIPE_IN', f'gt.blockmachines.hatch.input.tier.{_tn}.name', f'{_T} Input Hatch'),
                (f'hatch_output_{_tr}', 'OVERLAY_PIPE_OUT', f'gt.blockmachines.hatch.output.tier.{_tn}.name', f'{_T} Output Hatch'),
                (f'hatch_muffler_{_tr}', 'OVERLAY_MUFFLER', f'gt.blockmachines.hatch.muffler.tier.{_tn}.name', f'{_T} Muffler Hatch')]


def generate(g):
    import json
    write, A, D, NS, lang, TEX, REF = g['write'], g['A'], g['D'], g['NS'], g['lang'], g['TEX'], g['REF']
    ref, tag_of, mat_id, mat_by_name, mass = g['ref'], g['tag_of'], g['mat_id'], g['mat_by_name'], g['mass']
    shaped, add_item_tag, block_tags, prop = g['shaped'], g['add_item_tag'], g['block_tags'], g['prop']
    ru_lang = json.load(open(os.path.join(REF, 'gt5_lang_ru_machines.json'), encoding='utf-8'))
    BI = os.path.join(GT, 'blocks', 'iconsets')
    LVI = os.path.join(TEX, 'lv', 'iconsets')
    stats = {}

    def ru(key, fallback):
        v = ru_lang.get(key) if key else None
        return v.strip() if v else fallback

    def name(key, en, ruu):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ruu

    def tex_out(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def img(path):
        return Image.open(path).convert('RGBA')

    def first(path):
        return img(path).crop((0, 0, 16, 16))

    def over(base, ov):
        return Image.alpha_composite(base.copy(), ov)

    def cube(model_id, faces):
        write(os.path.join(A, 'models', 'block', model_id + '.json'), {
            'parent': 'minecraft:block/cube',
            'textures': {'particle': faces['side'], 'north': faces['front'], 'south': faces.get('back', faces['side']),
                         'east': faces['side'], 'west': faces['side'], 'up': faces.get('top', faces['side']),
                         'down': faces.get('bottom', faces['side'])}})

    def simple(bid, tool='pickaxe'):
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
        block_tags['minecraft:mineable/' + tool].append(f'{NS}:{bid}')

    def tooltip(bid, lines):
        for k, (en, ruu) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', en, ruu)

    # --- frames (GT_MetaPipeEntity_Frame: "SSS/SwS/SSS" rods -> 2; assembler 4 rods -> 1) -------------------------
    frames = []
    for n in FRAMES:
        if not ref('rod', n):
            continue
        m = mat_by_name[n]
        mid = mat_id(n)
        bid = f'frame_{mid}'
        iset = m['iconSet'] if os.path.exists(os.path.join(GT, 'blocks', 'materialicons', m['iconSet'], 'frameGt.png')) else 'METALLIC'
        fr = first(os.path.join(GT, 'blocks', 'materialicons', iset, 'frameGt.png'))
        rgb = ((m['color'] >> 16) & 255, (m['color'] >> 8) & 255, m['color'] & 255)
        t = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                r, gg, b, a = fr.getpixel((x, y))
                t.putpixel((x, y), (r * rgb[0] // 255, gg * rgb[1] // 255, b * rgb[2] // 255, a))
        t.save(tex_out(f'block/multi/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:cutout',
                                                                  'textures': {'all': f'{NS}:block/multi/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        simple(bid)
        en = f'{m["en"]} Frame Box'
        name(f'block.{NS}.{bid}', en, ru(f'gt.blockmachines.gt_frame_{n.lower()}.name', en))
        shaped(f'multi/{bid}', f'{NS}:{bid}', 2, ['SSS', 'SwS', 'SSS'], {'S': tag_of('rod', n)})
        add_item_tag(f'c:frames/{mid}', f'{NS}:{bid}')
        frames.append(mid)
    stats['frames'] = len(frames)

    # --- casings ------------------------------------------------------------------------------------------------
    for bid, src, key, en in (('heat_proof_casing', 'MACHINE_HEATPROOFCASING', 'gt.blockcasings.11.name', 'Heat Proof Machine Casing'),
                              ('coil_cupronickel', 'MACHINE_COIL_CUPRONICKEL', 'gt.blockcasings5.0.name', 'Cupronickel Coil Block'),
                              ('coil_kanthal', 'MACHINE_COIL_KANTHAL', 'gt.blockcasings5.1.name', 'Kanthal Coil Block'),  # D93
                              ('coil_nichrome', 'MACHINE_COIL_NICHROME', 'gt.blockcasings5.2.name', 'Nichrome Coil Block'),  # D98
                              ('coil_tungstensteel', 'MACHINE_COIL_TUNGSTENSTEEL', 'gt.blockcasings5.3.name', 'TungstenSteel Coil Block'),  # D103
                              ('coil_hssg', 'MACHINE_COIL_HSSG', 'gt.blockcasings5.4.name', 'HSS-G Coil Block'),  # D112
                              ('coil_naquadah', 'MACHINE_COIL_NAQUADAH', 'gt.blockcasings5.5.name', 'Naquadah Coil Block'),  # D112
                              ('coil_naquadah_alloy', 'MACHINE_COIL_NAQUADAHALLOY', 'gt.blockcasings5.6.name', 'Naquadah Alloy Coil Block')):  # D121
        first(os.path.join(BI, src + '.png')).save(tex_out(f'block/multi/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/multi/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
    tooltip('coil_cupronickel', [('Base Heating Capacity = 1800 Kelvin', 'Нагрев: 1800 K')])
    tooltip('coil_kanthal', [('Base Heating Capacity = 2700 Kelvin', 'Нагрев: 2700 K')])
    tooltip('coil_nichrome', [('Base Heating Capacity = 3600 Kelvin', 'Нагрев: 3600 K')])
    tooltip('coil_tungstensteel', [('Base Heating Capacity = 4500 Kelvin', 'Нагрев: 4500 K')])
    tooltip('coil_hssg', [('Base Heating Capacity = 5400 Kelvin', 'Нагрев: 5400 K')])
    tooltip('coil_naquadah', [('Base Heating Capacity = 7200 Kelvin', 'Нагрев: 7200 K')])
    tooltip('coil_naquadah_alloy', [('Base Heating Capacity = 9001 Kelvin', 'Нагрев: 9001 K')])
    shaped('multi/heat_proof_casing', f'{NS}:heat_proof_casing', 2, ['PhP', 'PFP', 'PwP'], {'P': tag_of('plate', 'Invar'), 'F': '#c:frames/invar'})
    shaped('multi/coil_cupronickel', f'{NS}:coil_cupronickel', 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/cupronickel'})
    shaped('multi/coil_kanthal', f'{NS}:coil_kanthal', 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/kanthal'})   # GT_Loader_MetaTileEntities:92
    shaped('multi/coil_nichrome', f'{NS}:coil_nichrome', 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/nichrome'})   # :93 (D98)
    shaped('multi/coil_tungstensteel', f'{NS}:coil_tungstensteel', 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/tungsten_steel'})   # :94 (D103)
    shaped('multi/coil_hssg', f'{NS}:coil_hssg', 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/hssg'})   # :95 (D112)
    shaped('multi/coil_naquadah', f'{NS}:coil_naquadah', 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/naquadah'})   # :96 (D112)
    shaped('multi/coil_naquadah_alloy', f'{NS}:coil_naquadah_alloy', 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/naquadah_alloy'})   # :97 (D121)

    # --- hatches (front overlay over the LV casing; any of 6 facings) -----------------------------------------------
    sides = {}
    for tr in ('lv', 'mv', 'hv', 'ev', 'iv', 'luv', 'zpm', 'uv'):
        sides[tr] = first(os.path.join(LVI, f'MACHINE_{"LuV" if tr == "luv" else tr.upper()}_SIDE.png'))
        sides[tr].save(tex_out(f'block/multi/hatch_{tr}_side.png'))
    for bid, ov, key, en in HATCHES:
        tr = bid.rsplit('_', 1)[1] if bid.rsplit('_', 1)[1] in ('mv', 'hv', 'ev', 'iv', 'luv', 'zpm', 'uv') else 'lv'
        ovp = os.path.join(BI, ov + '.png')
        if not os.path.exists(ovp):
            ovp = os.path.join(LVI, ov + '.png')
        over(sides[tr], first(ovp)).save(tex_out(f'block/multi/{bid}_front.png'))
        s_ = f'{NS}:block/multi/hatch_{tr}_side'
        cube(bid, {'front': f'{NS}:block/multi/{bid}_front', 'side': s_})
        variants = {}
        for facing, rot in (('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}), ('down', {'x': 90}), ('up', {'x': 270})):
            variants[f'facing={facing}'] = dict({'model': f'{NS}:block/{bid}'}, **rot)
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
    tooltip('hatch_energy_lv', [('Energy input for multiblocks: 32 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 32 EU/t, до 2 А, спереди')])
    tooltip('hatch_energy_mv', [('Energy input for multiblocks: 128 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 128 EU/t, до 2 А, спереди')])
    tooltip('hatch_dynamo_lv', [('Energy output for multiblock generators: 32 EU/t, 1 A, from the front', 'Выход энергии многоблочного генератора: 32 EU/t, 1 А, спереди'),
                                ('More than 32 EU/t from the generator - the whole machine explodes', 'Больше 32 EU/t от генератора — вся машина взрывается')])
    tooltip('hatch_dynamo_mv', [('Energy output for multiblock generators: 128 EU/t, 1 A, from the front', 'Выход энергии многоблочного генератора: 128 EU/t, 1 А, спереди'),
                                ('More than 128 EU/t from the generator - the whole machine explodes', 'Больше 128 EU/t от генератора — вся машина взрывается')])
    tooltip('hatch_energy_hv', [('Energy input for multiblocks: 512 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 512 EU/t, до 2 А, спереди')])
    tooltip('hatch_dynamo_hv', [('Energy output for multiblock generators: 512 EU/t, 1 A, from the front', 'Выход энергии многоблочного генератора: 512 EU/t, 1 А, спереди'),
                                ('More than 512 EU/t from the generator - the whole machine explodes', 'Больше 512 EU/t от генератора — вся машина взрывается')])
    tooltip('hatch_energy_ev', [('Energy input for multiblocks: 2048 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 2048 EU/t, до 2 А, спереди')])
    tooltip('hatch_dynamo_ev', [('Energy output for multiblock generators: 2048 EU/t, 1 A, from the front', 'Выход энергии многоблочного генератора: 2048 EU/t, 1 А, спереди'),
                                ('More than 2048 EU/t from the generator - the whole machine explodes', 'Больше 2048 EU/t от генератора — вся машина взрывается')])
    tooltip('hatch_energy_luv', [('Energy input for multiblocks: 32768 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 32768 EU/t, до 2 А, спереди')])
    tooltip('hatch_energy_uv', [('Energy input for multiblocks: 524288 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 524288 EU/t, до 2 А, спереди')])
    tooltip('hatch_dynamo_uv', [('Energy output for multiblock generators: 524288 EU/t, 1 A, from the front', 'Выход энергии многоблочного генератора: 524288 EU/t, 1 А, спереди')])
    tooltip('hatch_energy_zpm', [('Energy input for multiblocks: 131072 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 131072 EU/t, до 2 А, спереди')])
    tooltip('hatch_dynamo_zpm', [('Energy output for multiblock generators: 131072 EU/t, 1 A, from the front', 'Выход энергии многоблочного генератора: 131072 EU/t, 1 А, спереди')])
    tooltip('hatch_dynamo_luv', [('Energy output for multiblock generators: 32768 EU/t, 1 A, from the front', 'Выход энергии многоблочного генератора: 32768 EU/t, 1 А, спереди'),
                                 ('More than 32768 EU/t from the generator - the whole machine explodes', 'Больше 32768 EU/t от генератора — вся машина взрывается')])
    tooltip('hatch_energy_iv', [('Energy input for multiblocks: 8192 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 8192 EU/t, до 2 А, спереди')])
    tooltip('hatch_dynamo_iv', [('Energy output for multiblock generators: 8192 EU/t, 1 A, from the front', 'Выход энергии многоблочного генератора: 8192 EU/t, 1 А, спереди'),
                                ('More than 8192 EU/t from the generator - the whole machine explodes', 'Больше 8192 EU/t от генератора — вся машина взрывается')])
    tooltip('hatch_input_bus_lv', [('Item input for multiblocks, 4 slots', 'Вход предметов многоблочной машины, 4 слота')])
    tooltip('hatch_output_bus_lv', [('Item output for multiblocks, 4 slots', 'Выход предметов многоблочной машины, 4 слота')])
    tooltip('hatch_input_lv', [('Fluid input for multiblocks, 16000 L', 'Вход жидкости многоблочной машины, 16000 л')])
    tooltip('hatch_output_lv', [('Fluid output for multiblocks, 16000 L', 'Выход жидкости многоблочной машины, 16000 л')])
    tooltip('hatch_maintenance', [('Right-click with a tool to fix a problem', 'ПКМ инструментом — устранить неисправность'),
                                  ('Wrench, Screwdriver, Soft Hammer, Hammer, Soldering Iron, Crowbar', 'Ключ, отвёртка, киянка, молот, паяльник, монтировка')])
    tooltip('hatch_muffler_lv', [('Outputs the pollution, front must face air', 'Выпускает загрязнение; спереди должен быть воздух')])
    # D103: GT getSlots (tier + 1)^2, countCapacity 8000 x (tier + 1) up to HV, 8000 x 8 x (tier - 3) above; muffler 0.7^(tier - 1)
    for tr, tier in (('mv', 2), ('hv', 3), ('ev', 4), ('iv', 5)):
        slots, tank = (tier + 1) ** 2, 8000 * ((tier - 3) * 8 if tier > 3 else tier + 1)
        shown = ' (the window shows 16)' if slots > 16 else ''
        shown_ru = ' (в окне 16)' if slots > 16 else ''
        tooltip(f'hatch_input_bus_{tr}', [(f'Item input for multiblocks, {slots} slots{shown}', f'Вход предметов многоблочной машины, {slots} слотов{shown_ru}')])
        tooltip(f'hatch_output_bus_{tr}', [(f'Item output for multiblocks, {slots} slots{shown}', f'Выход предметов многоблочной машины, {slots} слотов{shown_ru}')])
        tooltip(f'hatch_input_{tr}', [(f'Fluid input for multiblocks, {tank} L', f'Вход жидкости многоблочной машины, {tank} л')])
        tooltip(f'hatch_output_{tr}', [(f'Fluid output for multiblocks, {tank} L', f'Выход жидкости многоблочной машины, {tank} л')])
        pct = round(100 * 0.7 ** (tier - 1))
        tooltip(f'hatch_muffler_{tr}', [('Outputs the pollution, front must face air', 'Выпускает загрязнение; спереди должен быть воздух'),
                                        (f'Reduces pollution to {pct}%', f'Снижает загрязнение до {pct}%')])
    HULL = f'{NS}:hull_lv'
    shaped('multi/hatch_energy_lv', f'{NS}:hatch_energy_lv', 1, ['CM'], {'M': HULL, 'C': '#iis:cables/1x/tin'})
    # GT_Loader_MetaTileEntities:270 "CM ": MV hull, copper cable
    shaped('multi/hatch_energy_mv', f'{NS}:hatch_energy_mv', 1, ['CM'], {'M': f'{NS}:hull_mv', 'C': '#iis:cables/1x/copper'})
    # GT_Loader_MetaTileEntities:226-227 " MC": hull, cable (tin for LV, copper for MV)
    shaped('multi/hatch_dynamo_lv', f'{NS}:hatch_dynamo_lv', 1, ['MC'], {'M': HULL, 'C': '#iis:cables/1x/tin'})
    shaped('multi/hatch_dynamo_mv', f'{NS}:hatch_dynamo_mv', 1, ['MC'], {'M': f'{NS}:hull_mv', 'C': '#iis:cables/1x/copper'})
    # D93: :271 "CM " and :228 " MC": HV hull, gold cable
    shaped('multi/hatch_energy_hv', f'{NS}:hatch_energy_hv', 1, ['CM'], {'M': f'{NS}:hull_hv', 'C': '#iis:cables/1x/gold'})
    shaped('multi/hatch_dynamo_hv', f'{NS}:hatch_dynamo_hv', 1, ['MC'], {'M': f'{NS}:hull_hv', 'C': '#iis:cables/1x/gold'})
    # D99: :272 "CM " and :229 " MC": EV hull, aluminium cable
    shaped('multi/hatch_energy_ev', f'{NS}:hatch_energy_ev', 1, ['CM'], {'M': f'{NS}:hull_ev', 'C': '#iis:cables/1x/aluminium'})
    shaped('multi/hatch_dynamo_ev', f'{NS}:hatch_dynamo_ev', 1, ['MC'], {'M': f'{NS}:hull_ev', 'C': '#iis:cables/1x/aluminium'})
    # D103: GT_Loader_MetaTileEntities:273 "CM", :230 " MC": IV hull, tungsten cable
    shaped('multi/hatch_energy_iv', f'{NS}:hatch_energy_iv', 1, ['CM'], {'M': f'{NS}:hull_iv', 'C': '#iis:cables/1x/tungsten'})
    shaped('multi/hatch_dynamo_iv', f'{NS}:hatch_dynamo_iv', 1, ['MC'], {'M': f'{NS}:hull_iv', 'C': '#iis:cables/1x/tungsten'})
    # D110: :274 "CM", :231 " MC": LuV hull, vanadium-gallium cable
    shaped('multi/hatch_energy_luv', f'{NS}:hatch_energy_luv', 1, ['CM'], {'M': f'{NS}:hull_luv', 'C': '#iis:cables/1x/vanadium_gallium'})
    shaped('multi/hatch_dynamo_luv', f'{NS}:hatch_dynamo_luv', 1, ['MC'], {'M': f'{NS}:hull_luv', 'C': '#iis:cables/1x/vanadium_gallium'})
    # D112: :275 "CM", :232 " MC": ZPM hull, naquadah cable
    shaped('multi/hatch_energy_zpm', f'{NS}:hatch_energy_zpm', 1, ['CM'], {'M': f'{NS}:hull_zpm', 'C': '#iis:cables/1x/naquadah'})
    shaped('multi/hatch_dynamo_zpm', f'{NS}:hatch_dynamo_zpm', 1, ['MC'], {'M': f'{NS}:hull_zpm', 'C': '#iis:cables/1x/naquadah'})
    # D121: :276 "CM", :233 " MC": UV hull, 4x naquadah alloy wire
    shaped('multi/hatch_energy_uv', f'{NS}:hatch_energy_uv', 1, ['CM'], {'M': f'{NS}:hull_uv', 'C': '#iis:wires/4x/naquadah_alloy'})
    shaped('multi/hatch_dynamo_uv', f'{NS}:hatch_dynamo_uv', 1, ['MC'], {'M': f'{NS}:hull_uv', 'C': '#iis:wires/4x/naquadah_alloy'})
    shaped('multi/hatch_input_lv', f'{NS}:hatch_input_lv', 1, ['G', 'M'], {'M': HULL, 'G': 'minecraft:glass'})
    shaped('multi/hatch_output_lv', f'{NS}:hatch_output_lv', 1, ['M', 'G'], {'M': HULL, 'G': 'minecraft:glass'})
    shaped('multi/hatch_input_bus_lv', f'{NS}:hatch_input_bus_lv', 1, ['C', 'M'], {'M': HULL, 'C': '#c:chests/wooden'})
    shaped('multi/hatch_output_bus_lv', f'{NS}:hatch_output_bus_lv', 1, ['M', 'C'], {'M': HULL, 'C': '#c:chests/wooden'})
    shaped('multi/hatch_maintenance', f'{NS}:hatch_maintenance', 1, ['dwx', 'hMc', 'fsr'], {'M': HULL})
    shaped('multi/hatch_muffler_lv', f'{NS}:hatch_muffler_lv', 1, ['M', 'P'], {'M': HULL, 'P': f'{NS}:pipe_medium_steel'})
    # D103: :294-422 the same patterns on the MV-IV hulls (the muffler always takes a medium steel pipe)
    for tr in ('mv', 'hv', 'ev', 'iv', 'luv', 'zpm', 'uv'):
        H = f'{NS}:hull_{tr}'
        GL = f'{NS}:reinforced_glass' if tr in ('luv', 'zpm', 'uv') else 'minecraft:glass'   # :298-299, :321-322 (D112)
        shaped(f'multi/hatch_input_{tr}', f'{NS}:hatch_input_{tr}', 1, ['G', 'M'], {'M': H, 'G': GL})
        shaped(f'multi/hatch_output_{tr}', f'{NS}:hatch_output_{tr}', 1, ['M', 'G'], {'M': H, 'G': GL})
        shaped(f'multi/hatch_input_bus_{tr}', f'{NS}:hatch_input_bus_{tr}', 1, ['C', 'M'], {'M': H, 'C': '#c:chests/wooden'})
        shaped(f'multi/hatch_output_bus_{tr}', f'{NS}:hatch_output_bus_{tr}', 1, ['M', 'C'], {'M': H, 'C': '#c:chests/wooden'})
        shaped(f'multi/hatch_muffler_{tr}', f'{NS}:hatch_muffler_{tr}', 1, ['M', 'P'], {'M': H, 'P': f'{NS}:pipe_medium_steel'})

    # --- EBF controller --------------------------------------------------------------------------------------------
    hp = first(os.path.join(BI, 'MACHINE_HEATPROOFCASING.png'))
    for suf, ov in (('', 'OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE'), ('_active', 'OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_ACTIVE')):
        o = img(os.path.join(BI, ov + '.png'))
        frames_n = max(1, o.height // 16)
        out = Image.new('RGBA', (16, 16 * frames_n))
        for f in range(frames_n):
            out.paste(over(hp, o.crop((0, 16 * f, 16, 16 * f + 16))), (0, 16 * f))
        out.save(tex_out(f'block/multi/electric_blast_furnace_front{suf}.png'))
        meta = os.path.join(BI, ov + '.png.mcmeta')
        if frames_n > 1 and os.path.exists(meta):
            shutil.copy(meta, tex_out(f'block/multi/electric_blast_furnace_front{suf}.png.mcmeta'))
        cube('electric_blast_furnace' + suf, {'front': f'{NS}:block/multi/electric_blast_furnace_front{suf}', 'side': f'{NS}:block/multi/heat_proof_casing'})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for active in (False, True):
            v = {'model': f'{NS}:block/electric_blast_furnace' + ('_active' if active else '')}
            if y:
                v['y'] = y
            variants[f'facing={facing},active={str(active).lower()}'] = v
    write(os.path.join(A, 'blockstates', 'electric_blast_furnace.json'), {'variants': variants})
    simple('electric_blast_furnace')
    name(f'block.{NS}.electric_blast_furnace', 'Electric Blast Furnace', ru('gt.blockmachines.multimachine.blastfurnace.name', 'Electric Blast Furnace'))
    tooltip('electric_blast_furnace', [
        ('3x3x4 hollow; controller in the middle of the bottom layer', 'Куб 3×3×4, пустой внутри; контроллер — в середине нижнего слоя'),
        ('Layers 2-3: 8 heating coils each; bottom and top: heat proof casings or hatches', 'Слои 2–3: по 8 катушек; низ и верх — теплоустойчивые корпуса или люки'),
        ('Bottom: energy hatches, input/output buses, input hatch, maintenance hatch', 'Низ: энергетические люки, входные/выходные шины, входной люк, люк обслуживания'),
        ('Top: muffler hatch in the middle (output hatches allowed on the top ring)', 'Верх: выхлопной люк в центре (на кольце верха — выходные люки)'),
        ('Two LV energy hatches give MV recipes (sum of voltages)', 'Два энергетических люка LV дают рецепты MV (напряжения складываются)'),
        ('Causes 100 Pollution per second', 'Загрязнение: 100 в секунду')])
    shaped('multi/electric_blast_furnace', f'{NS}:electric_blast_furnace', 1, ['FFF', 'CMC', 'WCW'],
           {'F': 'minecraft:furnace', 'C': '#c:circuits/basic', 'M': f'{NS}:heat_proof_casing', 'W': '#iis:cables/1x/tin'})
    gui_src = os.path.join(GT, 'gui', 'multimachines', 'ElectricBlastFurnace.png')
    shutil.copy(gui_src, tex_out('gui/multi/electric_blast_furnace.png'))

    # --- vacuum freezer (GT_MetaTileEntity_VacuumFreezer, D81): frost proof casing (gt.blockcasings2.1) + controller ----------
    first(os.path.join(BI, 'MACHINE_CASING_FROST_PROOF.png')).save(tex_out('block/multi/frost_proof_casing.png'))
    write(os.path.join(A, 'models', 'block', 'frost_proof_casing.json'), {'parent': 'minecraft:block/cube_all',
                                                                         'textures': {'all': f'{NS}:block/multi/frost_proof_casing'}})
    write(os.path.join(A, 'blockstates', 'frost_proof_casing.json'), {'variants': {'': {'model': f'{NS}:block/frost_proof_casing'}}})
    simple('frost_proof_casing')
    name(f'block.{NS}.frost_proof_casing', 'Frost Proof Machine Casing', ru('gt.blockcasings2.1.name', 'Frost Proof Machine Casing'))
    # GT_Loader_MetaTileEntities:65 "PhP/PFP/PwP" aluminium plates + aluminium frame -> 2
    shaped('multi/frost_proof_casing', f'{NS}:frost_proof_casing', 2, ['PhP', 'PFP', 'PwP'], {'P': tag_of('plate', 'Aluminium'), 'F': '#c:frames/aluminium'})
    fp = first(os.path.join(BI, 'MACHINE_CASING_FROST_PROOF.png'))
    for suf, ov in (('', 'OVERLAY_FRONT_VACUUM_FREEZER'), ('_active', 'OVERLAY_FRONT_VACUUM_FREEZER_ACTIVE')):
        o = img(os.path.join(BI, ov + '.png'))
        frames_n = max(1, o.height // 16)
        out = Image.new('RGBA', (16, 16 * frames_n))
        for f in range(frames_n):
            out.paste(over(fp, o.crop((0, 16 * f, 16, 16 * f + 16))), (0, 16 * f))
        out.save(tex_out(f'block/multi/vacuum_freezer_front{suf}.png'))
        meta = os.path.join(BI, ov + '.png.mcmeta')
        if frames_n > 1 and os.path.exists(meta):
            shutil.copy(meta, tex_out(f'block/multi/vacuum_freezer_front{suf}.png.mcmeta'))
        cube('vacuum_freezer' + suf, {'front': f'{NS}:block/multi/vacuum_freezer_front{suf}', 'side': f'{NS}:block/multi/frost_proof_casing'})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for active in (False, True):
            v = {'model': f'{NS}:block/vacuum_freezer' + ('_active' if active else '')}
            if y:
                v['y'] = y
            variants[f'facing={facing},active={str(active).lower()}'] = v
    write(os.path.join(A, 'blockstates', 'vacuum_freezer.json'), {'variants': variants})
    simple('vacuum_freezer')
    name(f'block.{NS}.vacuum_freezer', 'Vacuum Freezer', ru('gt.blockmachines.multimachine.vacuumfreezer.name', 'Vacuum Freezer'))
    tooltip('vacuum_freezer', [
        ('Super cools hot ingots and cells', 'Охлаждает горячие слитки и капсулы'),
        ('3x3x3 hollow, controller in the middle of a side', 'Куб 3×3×3, пустой внутри; контроллер — в середине стенки'),
        ('Hatches anywhere: energy, input/output bus, maintenance', 'Люки где угодно: энергия, входная и выходная шины, обслуживание'),
        ('Frost Proof Machine Casings for the rest (16 at least)', 'Остальное — морозостойкие корпуса (не меньше 16)'),
        ('Recipes take 120 EU/t: two LV energy hatches or more', 'Рецепты — 120 EU/t: от двух энергетических люков LV'),
        ('Crafting arrives with the HV pump and the Data circuit', 'Крафт появится вместе с помпой HV и схемой «Данные»')])
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'VacuumFreezer.png'), tex_out('gui/multi/vacuum_freezer.png'))
    # heat damage of hot ingots (GT_DamageSources DamageSourceHeat: "steam", difficulty scaled)
    write(os.path.join(D, NS, 'damage_type', 'heat.json'), {'message_id': 'iis.heat', 'exhaustion': 0.1, 'scaling': 'always'})
    name('death.attack.iis.heat', '%1$s was boiled alive', '%1$s сварился заживо')

    # --- soldering iron LV (GT_MetaGenerated_Tool_01.SOLDERING_IRON_LV: "LBf/Sd /P  ", 100000 EU, 32 EU/t) -------------
    II = os.path.join(GT, 'items')
    head = img(os.path.join(II, 'materialicons', 'METALLIC', 'toolHeadSoldering.png')) if os.path.exists(os.path.join(II, 'materialicons', 'METALLIC', 'toolHeadSoldering.png')) \
        else img(os.path.join(II, 'materialicons', 'DULL', 'toolHeadSoldering.png'))

    def tint(im, rgb):
        out = Image.new('RGBA', im.size)
        for y in range(im.height):
            for x in range(im.width):
                r, gg, b, a = im.getpixel((x, y))
                out.putpixel((x, y), (r * rgb[0] // 255, gg * rgb[1] // 255, b * rgb[2] // 255, a))
        return out
    iron = mat_by_name['Iron']['color']
    rubber = mat_by_name['Rubber']['color']
    icon = Image.new('RGBA', (16, 16))
    for layer, rgb in ((os.path.join(II, 'iconsets', 'HANDLE_SOLDERING.png'), rubber), (None, iron),
                       (os.path.join(II, 'iconsets', 'POWER_UNIT_LV.png'), 0xFFFFFF)):
        src = head.crop((0, 0, 16, 16)) if layer is None else img(layer).crop((0, 0, 16, 16))
        icon = Image.alpha_composite(icon, tint(src, ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255)))
    icon.save(tex_out('item/soldering_iron_lv.png'))
    write(os.path.join(A, 'models', 'item', 'soldering_iron_lv.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'{NS}:item/soldering_iron_lv'}})
    write(os.path.join(A, 'items', 'soldering_iron_lv.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/soldering_iron_lv'}})
    name(f'item.{NS}.soldering_iron_lv', 'Soldering Iron (LV)', 'Паяльник (LV)')
    name(f'item.{NS}.soldering_iron_lv.tooltip', 'Needs solder (tin, lead or soldering alloy: ingot, rod or fine wire) and 10000 EU per use',
         'Нужен припой (олово, свинец или паяльный сплав: слиток, стержень или тонкая проволока) и 10000 EU за использование')
    shaped('multi/soldering_iron_lv', f'{NS}:soldering_iron_lv', 1, ['LBf', 'Sd ', 'P  '],
           {'L': f'{NS}:battery_lithium_lv', 'B': tag_of('bolt', 'Iron'), 'S': tag_of('rod', 'Iron'), 'P': tag_of('plate', 'Rubber')})
    for n in ('Tin', 'Lead', 'SolderingAlloy'):
        for p in ('ingot', 'rod', 'wire_fine'):
            if ref(p, n):
                add_item_tag('iis:solder', tag_of(p, n))

    # --- assembler recipes (GT_MachineRecipeLoader:1380, 1381; frame 4 rods) ---------------------------------------
    rec = {}

    def mrec(rtype, rid, inputs, outputs, duration, eut, temp=0, fin=None, fout=None, circuit=0):
        outputs = [o for o in outputs if o and o['id']]
        if any(i['ingredient'] is None for i in inputs) or not outputs:
            return
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(duration), 'eut': int(eut)}
        if temp:
            r['temp'] = int(temp)
        if circuit:
            r['circuit'] = circuit
        if fin:
            r['fluid_inputs'] = [fin]
        if fout:
            r['fluid_outputs'] = [fout]
        rec.setdefault(f'{rtype}/{rid}', r)

    def ing(i, c=1):
        return {'ingredient': i, 'count': c}

    def out(i, c=1, chance=10000):
        if i is None:
            return None
        o = {'id': i, 'count': c}
        if chance < 10000:
            o['chance'] = chance
        return o
    mrec('assembler', 'heat_proof_casing', [ing(tag_of('plate', 'Invar'), 6), ing('#c:frames/invar')], [out(f'{NS}:heat_proof_casing', 2)], 50, 16)
    # GT_MachineRecipeLoader:1381-1382: 8 2x wires + programmed circuit 8 (the circuit was missing before D93)
    mrec('assembler', 'coil_cupronickel', [ing('#iis:wires/2x/cupronickel', 8)], [out(f'{NS}:coil_cupronickel')], 50, 16, circuit=8)
    mrec('assembler', 'coil_kanthal', [ing('#iis:wires/2x/kanthal', 8)], [out(f'{NS}:coil_kanthal')], 50, 16, circuit=8)
    mrec('assembler', 'coil_nichrome', [ing('#iis:wires/2x/nichrome', 8)], [out(f'{NS}:coil_nichrome')], 50, 16, circuit=8)   # :1383
    mrec('assembler', 'coil_tungstensteel', [ing('#iis:wires/2x/tungsten_steel', 8)], [out(f'{NS}:coil_tungstensteel')], 50, 16, circuit=8)   # :1384 (D103)
    mrec('assembler', 'coil_hssg', [ing('#iis:wires/2x/hssg', 8)], [out(f'{NS}:coil_hssg')], 50, 16, circuit=8)   # :1385 (D112)
    mrec('assembler', 'coil_naquadah', [ing('#iis:wires/2x/naquadah', 8)], [out(f'{NS}:coil_naquadah')], 50, 16, circuit=8)   # :1386 (D112)
    mrec('assembler', 'coil_naquadah_alloy', [ing('#iis:wires/2x/naquadah_alloy', 8)], [out(f'{NS}:coil_naquadah_alloy')], 50, 16, circuit=8)   # :1387 (D121)
    for mid in frames:
        mrec('assembler', f'frame_{mid}', [ing(f'#c:rods/{mid}', 4)], [out(f'{NS}:frame_{mid}')], 64, 8)

    # --- blast recipes ------------------------------------------------------------------------------------------------
    for n in sorted(g['names']):
        if not prop(n, 'BlastFurnaceRequired', False):
            continue
        temp = prop(n, 'BlastFurnaceTemp', 0)
        into = prop(n, 'MaterialSmeltInto', n) or n
        ingot = ref('ingot', into)
        if not ingot or 'NO_SMELTING' in g['subtags'](n):
            continue
        if temp > 1750:   # ProcessingDust:44 / ProcessingOreSmelting:25: above 1750 K the furnace gives a hot ingot (D81)
            ingot = ref('hot_ingot', into)
            if not ingot:
                continue
        mid = mat_id(n)
        dm = max(mass(n) // 40, 1) * temp
        # ProcessingDust: dust 1 / small 4 / tiny 9 -> ingot, max(mass/40,1)*temp ticks, 120 EU/t
        for p, c in (('dust', 1), ('small_dust', 4), ('tiny_dust', 9)):
            if ref(p, n):
                mrec('blast', f'{p}_{mid}', [ing(tag_of(p, n), c)], [out(ingot)], dm, 120, temp)
        # ProcessingOreSmelting: crushed / purified / impure dust -> ingot, max(mass/4,1)*temp ticks
        om = max(mass(n) // 4, 1) * temp
        for p in ('crushed', 'purified', 'centrifuged', 'impure_dust'):
            if ref(p, n):
                mrec('blast', f'{p}_{mid}', [ing(tag_of(p, n))], [out(ingot)], om, 120, temp)
    # GT_MachineRecipeLoader:1034-1039: ruby / green sapphire / sapphire -> 3 aluminium nuggets (1200 K, 100 EU/t)
    for n, ash in (('Ruby', True), ('GreenSapphire', True), ('Sapphire', False)):
        for p, t in (('dust', 400), ('gem', 320)):
            if ref(p, n) and ref('nugget', 'Aluminium'):
                mrec('blast', f'aluminium_from_{p}_{mat_id(n)}', [ing(tag_of(p, n))],
                     [out(ref('nugget', 'Aluminium'), 3), out(ref('tiny_dust', 'DarkAsh')) if ash else None], t, 100, 1200)
    # --- vacuum freezer recipes (D81) -----------------------------------------------------------------------------------
    # GT_MachineRecipeLoader:1390 frost proof casing: 6 aluminium plates + aluminium frame -> 2 (50 t, 16 EU/t)
    mrec('assembler', 'frost_proof_casing', [ing(tag_of('plate', 'Aluminium'), 6), ing('#c:frames/aluminium')], [out(f'{NS}:frost_proof_casing', 2)], 50, 16)
    # ProcessingIngot:103 hot ingot -> ingot, max(mass * 3, 1) ticks; GT_Recipe(in, out, time) = 120 EU/t
    for n in sorted(g['names']):
        if ref('hot_ingot', n) and ref('ingot', n):
            mrec('vacuum_freezer', f'hot_ingot_{mat_id(n)}', [ing(tag_of('hot_ingot', n))], [out(ref('ingot', n))], max(mass(n) * 3, 1), 120)
    # GT_MachineRecipeLoader:1230 water cell -> ice cell (50 t); :1231 IC2 air cell -> liquid air: IC2 compressed air is not ported
    capsule = lambda fid: {'neoforge:ingredient_type': 'neoforge:components', 'items': f'{NS}:capsule',
                           'components': {f'{NS}:fluid_content': {'fluid': fid, 'amount': 1000}}}
    rec['vacuum_freezer/ice_capsule'] = {'type': f'{NS}:vacuum_freezer', 'inputs': [ing(capsule('minecraft:water'))],
                                         'outputs': [{'id': f'{NS}:capsule', 'count': 1, 'components': {f'{NS}:fluid_content': {'fluid': f'{NS}:ice', 'amount': 1000}}}],
                                         'duration': 50, 'eut': 120}
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    stats['blast'] = sum(1 for k in rec if k.startswith('blast/'))
    stats['vacuum_freezer'] = sum(1 for k in rec if k.startswith('vacuum_freezer/'))

    # --- misc lang -----------------------------------------------------------------------------------------------------
    for k, en, ruu in (
            ('jei.iis.blast', 'Electric Blast Furnace', 'Электрическая доменная печь'),
            ('jei.iis.vacuum_freezer', 'Vacuum Freezer', 'Вакуумный морозильник'),
            ('jei.iis.thermal_centrifuge', 'Thermal Centrifuge', 'Термальная центрифуга'),
            ('itemGroup.iis.multi', 'IIS 2.0: Multiblocks', 'ИИС 2.0: многоблочные машины'),
            ('message.iis.machine.enabled', 'Machine enabled', 'Машина включена'),
            ('message.iis.machine.disabled', 'Machine disabled (soft hammer to enable)', 'Машина выключена (включить киянкой)'),
            ('jei.iis.temp', 'Heat: %s K', 'Нагрев: %s K'),
            ('jei.iis.duration_cleanroom', '%s s, cleanroom only', '%s с, только в чистой комнате'),
            ('gui.iis.ebf.structure_ok', 'Structure: complete', 'Структура: собрана'),
            ('gui.iis.ebf.structure_bad', 'Structure: incomplete', 'Структура: не собрана'),
            ('gui.iis.ebf.problems', 'Problems: %s', 'Неисправностей: %s'),
            ('gui.iis.ebf.efficiency', 'Efficiency: %s%%', 'Эффективность: %s%%'),
            ('gui.iis.ebf.progress', 'Progress: %s / %s s', 'Ход: %s / %s с'),
            ('gui.iis.ebf.eut', 'Uses %s EU/t', 'Потребляет %s EU/t'),
            ('gui.iis.ebf.heat', 'Heat: %s K', 'Нагрев: %s K'),
            ('gui.iis.ebf.voltage', 'Max input: %s EU/t', 'Наибольший вход: %s EU/t'),
            ('gui.iis.ebf.disabled', 'Machine disabled', 'Машина выключена'),
            ('gui.iis.ebf.disabled_hint', 'Soft hammer to enable', 'Включить — киянкой'),
            ('gui.iis.ebf.muffler', 'Muffler is blocked', 'Выхлопной люк перекрыт'),
            ('message.iis.maintenance.fixed', 'Fixed: %s', 'Устранено: %s'),
            ('message.iis.maintenance.none', 'No problems', 'Неисправностей нет'),
            ('message.iis.maintenance.list', 'Problems: %s', 'Неисправности: %s'),
            ('message.iis.maintenance.no_solder', 'Not enough solder or charge', 'Нет припоя или заряда'),
            ('maintenance.iis.wrench', 'pipe loose (wrench)', 'ослаблена труба (ключ)'),
            ('maintenance.iis.screwdriver', 'screws loose (screwdriver)', 'ослаблены винты (отвёртка)'),
            ('maintenance.iis.soft_hammer', 'something stuck (soft hammer)', 'что-то заело (киянка)'),
            ('maintenance.iis.hard_hammer', 'platings dented (hammer)', 'помята обшивка (молот)'),
            ('maintenance.iis.soldering', 'circuitry burned (soldering iron)', 'сгорела схема (паяльник)'),
            ('maintenance.iis.crowbar', 'something blocks (crowbar)', 'что-то мешает (монтировка)')):
        name(k, en, ruu)
    return stats
