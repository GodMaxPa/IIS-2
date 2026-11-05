"""LuV: chips and nano circuits up to Master, data stick, scanner, assembly line and LuV parts (D110).

Sources: GT_MachineRecipeLoader:402-403, 449, 468-492, 503-519, 570, 579-583, 622-623, 1032, 1772, 1589-1591, 2046-2323;
GT_CraftingRecipeLoader:269-274, 394, 408; GT_MetaTileEntity_AssemblyLine, _Scanner, _Hatch_DataAccess; ProcessingDust:41-44; ProcessingRound;
ProcessingPlate:181-183; ProcessingCrafting:34, 77; GT_Loader_MetaTileEntities:83-86, 405, 748-753, 1356-1357. IC2 is not ported (D5): the mixed
metal ingot, advanced alloy, reinforced glass and carbon fibre are our own items and block with the GT recipes that make them.
"""
import json
import os
import shutil

from PIL import Image

NS = 'iis'
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')


def generate(g):
    write, A, D, lang, REF = g['write'], g['A'], g['D'], g['lang'], g['REF']
    tag_of, shaped, shapeless, block_tags, ref, mass, prop = g['tag_of'], g['shaped'], g['shapeless'], g['block_tags'], g['ref'], g['mass'], g['prop']
    ru_lang = json.load(open(os.path.join(REF, 'gt5_lang_ru_machines.json'), encoding='utf-8'))
    BI = os.path.join(GT, 'blocks', 'iconsets')
    rec = {}
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

    def first(path):
        return Image.open(path).convert('RGBA').crop((0, 0, 16, 16))

    def loot(bid):
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
        block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')

    def simple_block(bid, texture, key, en, ruu=None, render_type=None):
        model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': texture}}
        if render_type:
            model['render_type'] = render_type
        write(os.path.join(A, 'models', 'block', bid + '.json'), model)
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        loot(bid)
        name(f'block.{NS}.{bid}', en, ru(key, ruu or en))

    def tooltip(bid, lines):
        for k, (en, ruu) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', en, ruu)

    def ing(i, c=1):
        return {'ingredient': i, 'count': c}

    def out(i, c=1, chance=None):
        o = {'id': i, 'count': c}
        if chance is not None:
            o['chance'] = chance
        return o

    def mrec(rtype, rid, inputs, outputs, duration, eut, fin=None, fins=None, circuit=None, keep=None, temp=None, cleanroom=False):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(duration), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if fins:
            r['fluid_inputs'] = fins
        if circuit is not None:
            r['circuit'] = circuit
        if keep:
            r['keep'] = keep if isinstance(keep, list) else [keep]
        if temp:
            r['temp'] = temp
        if cleanroom:
            r['cleanroom'] = True
        rec.setdefault(f'{rtype}/{rid}', r)

    def fl(fid, mb):
        return {'ingredient': f'{NS}:{fid}', 'amount': mb}

    def cutter(rid, inp, outs, dur, eut):
        """GT_RecipeAdder#addCutterRecipe:438-440: water, distilled water (both duration x2), lubricant."""
        mrec('cutter', rid, inp, outs, dur * 2, eut, {'ingredient': 'minecraft:water', 'amount': max(4, min(1000, dur * eut // 320))})
        mrec('cutter', rid + '_distilled', inp, outs, dur * 2, eut, fl('distilled_water', max(3, min(750, dur * eut // 426))))
        mrec('cutter', rid + '_lubricant', inp, outs, dur, eut, fl('lubricant', max(1, min(250, dur * eut // 1280))))

    I = lambda x: f'{NS}:{x}'
    fine = lambda m: tag_of('wire_fine', m)
    SOL = lambda mb: fl('molten_solderingalloy', mb)
    LUB = lambda mb: fl('lubricant', mb)

    # --- materials -----------------------------------------------------------------------------------------------------
    # GT_CraftingRecipeLoader:408 HSS-G, :394 YBCO; GT_MachineRecipeLoader:1772 YBCO in the mixer (600 t per 6 dusts, 8 EU/t)
    shapeless('dust/mix_hssg', ref('dust', 'HSSG'), 9, [tag_of('dust', 'TungstenSteel')] * 5 + [tag_of('dust', 'Chrome')] + [tag_of('dust', 'Molybdenum')] * 2 + [tag_of('dust', 'Vanadium')])
    shapeless('dust/mix_yttrium_barium_cuprate', ref('dust', 'YttriumBariumCuprate'), 6, [tag_of('dust', 'Yttrium')] + [tag_of('dust', 'Barium')] * 2 + [tag_of('dust', 'Copper')] * 3)
    mrec('mixer', 'yttrium_barium_cuprate', [ing(tag_of('dust', 'Copper'), 3), ing(tag_of('dust', 'Barium'), 2), ing(tag_of('dust', 'Yttrium'))], [out(ref('dust', 'YttriumBariumCuprate'), 6)], 600, 8)
    shapeless('dust/mix_niobium_titanium', ref('dust', 'NiobiumTitanium'), 2, [tag_of('dust', 'Niobium'), tag_of('dust', 'Titanium')])
    # ProcessingDust:41-44: dust -> ingot in the EBF, hot above 1750 K; YBCO takes 7000 L of oxygen
    for m in ('HSSG', 'YttriumBariumCuprate', 'NiobiumTitanium', 'Niobium', 'Yttrium', 'Lanthanum', 'Cerium'):
        t = prop(m, 'BlastFurnaceTemp', 0)
        result = ref('hot_ingot', m) if t > 1750 else ref('ingot', m)
        mrec('blast', f'dust_{g["mat_id"](m)}', [ing(tag_of('dust', m))], [out(result)], max(mass(m) // 40, 1) * t, 120,
             fin=fl('oxygen', 7000) if m == 'YttriumBariumCuprate' else None, temp=t)
    # GT_MachineRecipeLoader:1904 rare earth (64 t, 20 EU/t), each output with a 25 % chance
    mrec('centrifuge', 'rare_earth', [ing(tag_of('dust', 'RareEarth'))],
         [out(ref('small_dust', m), 1, 2500) for m in ('Neodymium', 'Yttrium', 'Lanthanum', 'Cerium', 'Cadmium', 'Caesium')], 64, 20)
    # ProcessingGem:42-43: vanilla gems have no generated plate recipes; the ender lenses (lathe, plate -> lens) need these
    for m, gem in (('EnderPearl', 'minecraft:ender_pearl'), ('EnderEye', 'minecraft:ender_eye')):
        mrec('forge_hammer', f'plate_{g["mat_id"](m)}', [ing(gem)], [out(ref('plate', m))], max(mass(m), 1), 16)
        mrec('bender', f'gem_{g["mat_id"](m)}', [ing(gem)], [out(ref('plate', m))], max(mass(m) * 2, 1), 24)
    # GT_Loader_ItemData:93 + GT_RecipeRegistrator: glass grinds into dust (the glass plates of the screen cover and the plascrete window start here)
    mrec('macerator', 'glass', [ing('minecraft:glass')], [out(ref('dust', 'Glass'))], max(mass('Glass') * 2, 1), 4)
    # D102 rails take molten blaze (IIS 1 scripts): blaze powder melts like a dust; GT_Loader_MetaTileEntities:1148 the freezer controller, promised in D81
    mrec('fluid_extractor', 'blaze_powder', [ing('minecraft:blaze_powder')], [], 24, 80)
    rec['fluid_extractor/blaze_powder']['fluid_outputs'] = [{'id': f'{NS}:molten_blaze', 'amount': 144}]
    shaped('multi/vacuum_freezer', I('vacuum_freezer'), 1, ['PPP', 'CMC', 'WCW'], {'M': I('frost_proof_casing'), 'P': I('electric_pump_hv'), 'C': '#c:circuits/data', 'W': '#iis:cables/1x/gold'})
    # ProcessingNugget:17: an iron nugget smelts into a wrought iron nugget; without it the LV hull has no wrought iron plates to start from
    g['smelting']('smelting/nugget_iron_wrought', 'minecraft:iron_nugget', ref('nugget', 'WroughtIron'), 1, xp=0.1)
    # D111: borosilicate glass (GT_CraftingRecipeLoader:397, GT_MachineRecipeLoader:1797), glass fibre (:497), the fluid filter (:2600: HV-IV intakes and filter machines)
    shapeless('dust/mix_borosilicate_glass', ref('dust', 'BorosilicateGlass'), 8, [tag_of('dust', 'Boron')] + [tag_of('dust', 'Glass')] * 7)
    mrec('mixer', 'borosilicate_glass', [ing(tag_of('dust', 'Boron')), ing(tag_of('dust', 'Glass'), 7)], [out(ref('dust', 'BorosilicateGlass'), 8)], 200, 8)
    mrec('extruder', 'glass_fiber', [ing(tag_of('ingot', 'BorosilicateGlass'))], [out(I('glass_fiber'), 8)], 160, 96, keep=I('shape_extruder_wire'))
    mrec('assembler', 'fluid_filter', [ing(I('pipe_small_plastic')), ing(I('carbon_fiber'), 8), ing(I('glass_fiber'), 32)], [out(I('fluid_filter'), 4)], 80, 48)
    # D111: :1890 uranium dust gives a little plutonium; six ingots of it then give radon (:1829, extracted by chem.py)
    mrec('centrifuge', 'uranium', [ing(tag_of('dust', 'Uranium'))], [out(ref('tiny_dust', 'Uranium235'), 1, 2000), out(ref('tiny_dust', 'Plutonium'), 1, 200)], 800, 320)
    # GT_MachineRecipeLoader:1032 niobium-titanium from ingots
    mrec('blast', 'niobium_titanium_ingots', [ing(tag_of('ingot', 'Niobium')), ing(tag_of('ingot', 'Titanium'))], [out(ref('hot_ingot', 'NiobiumTitanium'), 2)],
         max(mass('NiobiumTitanium') // 80, 1) * 4500, 480, temp=4500)
    # ProcessingRound: lathe nugget -> round (mass / 4 ticks, 8 EU/t), by hand "fX/X "
    mrec('lathe', 'round_hssg', [ing(tag_of('nugget', 'HSSG'))], [out(ref('round', 'HSSG'))], max(mass('HSSG') // 4, 1), 8)
    shaped('part/round_hssg', ref('round', 'HSSG'), 1, ['fX', 'X '], {'X': tag_of('nugget', 'HSSG')})

    # --- IC2 replacements (D5): GT_CraftingRecipeLoader:269-274 mixed metal, IC2 compressor -> advanced alloy, ProcessingPlate:182-183 reinforced glass -----
    for a, b in (('Bronze', 'Tin'), ('Bronze', 'Zinc'), ('Bronze', 'Aluminium'), ('Brass', 'Tin'), ('Brass', 'Zinc'), ('Brass', 'Aluminium')):
        shaped(f'part/mixed_metal_ingot_{a.lower()}_{b.lower()}', I('mixed_metal_ingot'), 1, ['X', 'Y', 'Z'], {'X': tag_of('plate', 'Iron'), 'Y': tag_of('plate', a), 'Z': tag_of('plate', b)})
    mrec('compressor', 'advanced_alloy', [ing(I('mixed_metal_ingot'))], [out(I('advanced_alloy_plate'))], 300, 2)
    mrec('alloy_smelter', 'reinforced_glass', [ing(I('advanced_alloy_plate')), ing('minecraft:glass', 3)], [out(I('reinforced_glass'), 4)], 400, 4)
    mrec('alloy_smelter', 'reinforced_glass_dust', [ing(I('advanced_alloy_plate')), ing(tag_of('dust', 'Glass'), 3)], [out(I('reinforced_glass'), 4)], 400, 4)
    glass = Image.new('RGBA', (16, 16), (150, 165, 175, 70))
    for k in range(16):
        for x, y in ((k, 0), (k, 15), (0, k), (15, k), (k, k), (k, 15 - k)):
            glass.putpixel((x, y), (105, 115, 125, 255))
    glass.save(tex_out('block/multi/reinforced_glass.png'))
    simple_block('reinforced_glass', f'{NS}:block/multi/reinforced_glass', None, 'Reinforced Glass', 'Армированное стекло', render_type='minecraft:cutout')
    # GT_MachineRecipeLoader:402-403 carbon fibre in the autoclave (lutetium is not ported)
    for metal, chance in (('palladium', 9000), ('platinum', 5000)):
        mrec('autoclave', f'carbon_fiber_{metal}', [ing(tag_of('dust', 'Carbon'), 16)], [out(I('carbon_fiber'), 8, chance)], 600, 5, fin=fl(f'molten_{metal}', 4))

    # --- wafers and chips ------------------------------------------------------------------------------------------------
    # :622-623 glowstone doped boule and wafer; ProcessingCrafting:34, 77 and :503, 505: engraved in the cleanroom
    mrec('blast', 'silicon_boule_glowstone', [ing(tag_of('dust', 'Silicon'), 64), ing('minecraft:glowstone_dust', 8)], [out(I('silicon_boule_glowstone'))], 12000, 480, fin=fl('nitrogen', 8000), temp=2484)
    cutter('silicon_wafer_glowstone', [ing(I('silicon_boule_glowstone'))], [out(I('silicon_wafer_glowstone'), 32)], 400, 64)
    W2 = I('silicon_wafer_glowstone')
    mrec('laser_engraver', 'wafer_nand', [ing(W2)], [out(I('wafer_nand'))], 500, 480, keep=tag_of('lens', 'EnderPearl'), cleanroom=True)
    mrec('laser_engraver', 'wafer_nor', [ing(W2)], [out(I('wafer_nor'))], 500, 480, keep=tag_of('lens', 'EnderEye'), cleanroom=True)
    mrec('laser_engraver', 'wafer_pic', [ing(W2)], [out(I('wafer_pic'))], 500, 480, keep='#iis:crafting_lens/blue', cleanroom=True)
    mrec('laser_engraver', 'wafer_cpu_glowstone', [ing(W2)], [out(I('wafer_cpu'), 4)], 500, 480, keep='#iis:crafting_lens/white', cleanroom=True)
    for chip, wafer, n in (('chip_nand', 'wafer_nand', 32), ('chip_nor', 'wafer_nor', 16), ('chip_pic', 'wafer_pic', 4), ('chip_nano_cpu', 'wafer_nano_cpu', 7)):
        cutter(chip, [ing(I(wafer))], [out(I(chip), n)], 600, 48)   # :510-516
    # :519 nano CPU wafer; the glowstone melts like any dust (ProcessingDust / fluid extractor)
    mrec('chemical_reactor', 'wafer_nano_cpu', [ing(I('wafer_cpu')), ing(I('carbon_fiber'), 16)], [out(I('wafer_nano_cpu'))], 400, 1920, fin=fl('molten_glowstone', 576))
    mrec('fluid_extractor', 'glowstone_dust', [ing('minecraft:glowstone_dust')], [], 24, 40)
    rec['fluid_extractor/glowstone_dust']['fluid_outputs'] = [{'id': f'{NS}:molten_glowstone', 'amount': 144}]

    # --- boards and SMD parts --------------------------------------------------------------------------------------------
    PL = lambda mb: fl('molten_plastic', mb)
    mrec('chemical_reactor', 'circuit_board_epoxy', [ing(tag_of('plate', 'Epoxid')), ing(tag_of('foil', 'Copper'))], [out(I('circuit_board_epoxy'))], 500, 10, fin=fl('sulfuricacid', 125))   # :449
    mrec('assembler', 'resistor_smd', [ing(tag_of('dust', 'Carbon')), ing(fine('Electrum'), 4)], [out(I('resistor_smd'), 24)], 80, 96, fin=PL(144))   # :469
    mrec('assembler', 'diode_smd', [ing(fine('Platinum'), 4), ing(tag_of('small_dust', 'Gallium'))], [out(I('diode_smd'), 32)], 400, 120, fin=PL(288))   # :480
    mrec('assembler', 'transistor_smd', [ing(tag_of('plate', 'Gallium')), ing(fine('AnnealedCopper'), 6)], [out(I('transistor_smd'), 32)], 80, 96, fin=PL(288))   # :488
    mrec('assembler', 'capacitor_smd', [ing(tag_of('foil', 'Silicone'), 8), ing(tag_of('foil', 'Aluminium'), 2)], [out(I('capacitor_smd'), 32)], 120, 120, fin=PL(72))   # :491

    # --- nano circuits and the data stick: GT_MachineRecipeLoader:570, 579-583 -----------------------------------------------
    EP = I('circuit_board_epoxy')
    mrec('circuit_assembler', 'data_stick', [ing(I('circuit_board_plastic')), ing(I('circuit_processor')), ing(I('chip_nand'), 32), ing(I('chip_ram'), 4), ing(fine('RedAlloy'), 8),
                                             ing(tag_of('plate', 'Plastic'), 4)], [out(I('data_stick'))], 400, 90, fin=SOL(144))
    mrec('circuit_assembler', 'circuit_nanoprocessor', [ing(EP), ing(I('chip_nano_cpu')), ing(I('resistor_smd'), 2), ing(I('capacitor_smd'), 2), ing(I('transistor_smd'), 2), ing(fine('Electrum'), 2)],
         [out(I('circuit_nanoprocessor'))], 200, 600, fin=SOL(72))
    mrec('circuit_assembler', 'circuit_nanocomputer', [ing(EP), ing(I('circuit_nanoprocessor'), 2), ing(I('coil_small'), 4), ing(I('capacitor_smd'), 4), ing(I('chip_ram'), 4), ing(fine('Electrum'), 6)],
         [out(I('circuit_nanocomputer'))], 400, 600, fin=SOL(144))
    mrec('circuit_assembler', 'circuit_elitenanocomputer', [ing(EP, 2), ing(I('circuit_nanocomputer'), 3), ing(I('diode_smd'), 4), ing(I('chip_nor'), 4), ing(I('chip_ram'), 4), ing(fine('Electrum'), 6)],
         [out(I('circuit_elitenanocomputer'))], 400, 600, fin=SOL(144))
    mrec('circuit_assembler', 'circuit_master', [ing('#c:frames/aluminium'), ing(I('circuit_elitenanocomputer'), 4), ing(I('coil_small'), 4), ing(I('capacitor_smd'), 24), ing(I('chip_ram'), 16),
                                                 ing('#iis:wires/1x/annealed_copper', 12)], [out(I('circuit_master'))], 1600, 1920, fin=SOL(288))

    # --- high pressure pipes: GT_Loader_MetaTileEntities:1589-1591 ------------------------------------------------------------
    mrec('assembler', 'pipe_small_high_pressure', [ing(I('pipe_small_tungsten_steel')), ing(I('electric_pump_ev'))], [out(I('pipe_small_high_pressure'))], 300, 96)
    mrec('assembler', 'pipe_medium_high_pressure', [ing(I('pipe_medium_tungsten_steel')), ing(I('electric_pump_iv'))], [out(I('pipe_medium_high_pressure'))], 400, 148)
    mrec('assembler', 'pipe_large_high_pressure', [ing(I('pipe_large_tungsten_steel')), ing(I('electric_pump_iv'), 2)], [out(I('pipe_large_high_pressure'))], 600, 256)

    # --- casings, assembly line, data hatch, scanner ------------------------------------------------------------------------
    for bid, src, key, en in (('gear_box_tungsten_steel', 'MACHINE_CASING_GEARBOX_TUNGSTENSTEEL', 'gt.blockcasings2.5.name', 'Assembling Line Casing'),
                              ('assembler_casing', 'MACHINE_CASING_ASSEMBLER', 'gt.blockcasings2.9.name', 'Assembler Machine Casing'),
                              ('grate_casing', 'MACHINE_CASING_GRATE', 'gt.blockcasings3.10.name', 'Grate Machine Casing')):
        first(os.path.join(BI, src + '.png')).save(tex_out(f'block/multi/{bid}.png'))
        simple_block(bid, f'{NS}:block/multi/{bid}', key, en)
    # GT_Loader_MetaTileEntities:83 "PhP/GFG/PwP", :84 "PVP/PFP/PMP", :86 "PVP/PFP/PMP"
    shaped('multi/gear_box_tungsten_steel', I('gear_box_tungsten_steel'), 2, ['PhP', 'GFG', 'PwP'], {'P': tag_of('plate', 'Steel'), 'F': '#c:frames/tungsten_steel', 'G': I('robot_arm_iv')})
    shaped('multi/grate_casing', I('grate_casing'), 2, ['PVP', 'PFP', 'PMP'], {'P': 'minecraft:iron_bars', 'F': '#c:frames/steel', 'M': I('electric_motor_mv'), 'V': tag_of('rotor', 'Steel')})
    shaped('multi/assembler_casing', I('assembler_casing'), 2, ['PVP', 'PFP', 'PMP'], {'P': '#c:circuits/data', 'F': '#c:frames/tungsten_steel', 'M': I('electric_motor_iv'), 'V': '#c:circuits/elite'})

    bid = 'assembly_line'
    side = first(os.path.join(BI, 'MACHINE_CASING_SOLID_STEEL.png'))
    for suf, ov in (('', 'OVERLAY_FRONT_ASSEMBLY_LINE'), ('_active', 'OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE')):
        Image.alpha_composite(side, first(os.path.join(BI, ov + '.png'))).save(tex_out(f'block/multi/{bid}_front{suf}.png'))
        write(os.path.join(A, 'models', 'block', bid + suf + '.json'), {'parent': 'minecraft:block/cube', 'textures': {
            'particle': f'{NS}:block/multi/solid_steel_casing', 'north': f'{NS}:block/multi/{bid}_front{suf}', 'south': f'{NS}:block/multi/solid_steel_casing', 'east': f'{NS}:block/multi/solid_steel_casing',
            'west': f'{NS}:block/multi/solid_steel_casing', 'up': f'{NS}:block/multi/solid_steel_casing', 'down': f'{NS}:block/multi/solid_steel_casing'}})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for active in (False, True):
            v = {'model': f'{NS}:block/{bid}' + ('_active' if active else '')}
            if y:
                v['y'] = y
            variants[f'facing={facing},active={str(active).lower()}'] = v
    write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
    write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
    loot(bid)
    name(f'block.{NS}.{bid}', 'Assembly Line', ru('gt.blockmachines.multimachine.assemblyline.name', 'Сборочная линия'))
    tooltip(bid, [('Width 5-16, 4 high, 3 deep; the controller is in the second layer from the top', 'Ширина 5–16, высота 4, глубина 3; контроллер — во втором слое сверху'),
                  ('Top middle row: solid steel casings or energy hatches. Below: grate, assembler casing, grate', 'Верх, средний ряд: стальные корпуса или люки энергии. Ниже: решётка, сборочный корпус, решётка'),
                  ('Then reinforced glass, line casing, reinforced glass; bottom: solid steel casings or hatches', 'Дальше армированное стекло, корпус линии, армированное стекло; низ: стальные корпуса или люки'),
                  ('Needs a data stick with the plan: in the controller or a data access hatch', 'Нужна флешка с чертежом: в контроллере или люке данных'),
                  ('Each input bus holds one ingredient in the order of the plan; exactly one output bus', 'Каждая входная шина — свой ингредиент по порядку чертежа; выходная шина ровно одна')])
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'AssemblyLine.png'), tex_out('gui/multi/assembly_line.png'))
    # :1357 "WCW/EME/WCW"
    shaped('multi/assembly_line', I(bid), 1, ['WCW', 'EME', 'WCW'], {'M': I('hull_iv'), 'W': I('assembler_casing'), 'E': '#c:circuits/elite', 'C': I('robot_arm_iv')})
    # :405 "COC/OMO/COC"
    shaped('multi/hatch_data_access_ev', I('hatch_data_access_ev'), 1, ['COC', 'OMO', 'COC'], {'M': I('hull_ev'), 'O': I('data_stick'), 'C': '#c:circuits/elite'})
    tooltip('hatch_data_access_ev', [('Holds 4 data sticks for the assembly line, in the upper layer next to the grates', 'Хранит 4 флешки для сборочной линии, ставится в ряд с решётками')])
    # :748-753 scanner "CTC/WMW/CRC"; the LuV one needs the Ultimate circuit, which is not ported
    for t, circ, cable in (('lv', 'good', 'tin'), ('mv', 'advanced', 'copper'), ('hv', 'data', 'gold'), ('ev', 'elite', 'aluminium'), ('iv', 'master', 'tungsten')):
        shaped(f'lv/scanner_{t}', I(f'scanner_{t}'), 1, ['CTC', 'WMW', 'CRC'],
               {'M': I(f'hull_{t}'), 'T': I(f'emitter_{t}'), 'R': I(f'sensor_{t}'), 'C': f'#c:circuits/{circ}', 'W': f'#iis:cables/1x/{cable}'})
    # super buffer, :1743-1747 "DMV/DDD" with data sticks (the data orb is not ported)
    for t in ('lv', 'mv', 'hv', 'ev', 'iv', 'luv'):
        shaped(f'auto/super_buffer_{t}', I(f'super_buffer_{t}'), 1, ['DMV', 'DDD'], {'M': I(f'hull_{t}'), 'V': I(f'conveyor_module_{t}'), 'D': I('data_stick')})

    for k, en, ruu in (('item.iis.data_stick.research', 'Construction plan: %s', 'Чертёж: %s'), ('item.iis.data_stick.empty', 'Empty: research it in a scanner', 'Пустая: запиши чертёж в сканере'),
                       ('jei.iis.assembly_line', 'Assembly Line', 'Сборочная линия'), ('jei.iis.research', 'Research in a scanner: %s s', 'Исследование в сканере: %s с')):
        name(k, en, ruu)

    # --- LuV parts on the assembly line: GT_MachineRecipeLoader:2046-2281; temp = research time in ticks (GT mResearchTime) -----------
    Y = lambda n: ing('#iis:cables/1x/yttrium_barium_cuprate', n)
    H = lambda pre, n: ing(tag_of(pre, 'HSSG'), n)

    def line(rid, research, inputs, fluids, out_id):
        mrec('assembly_line', rid, inputs, [out(I(out_id))], 600, 6000, fins=fluids, keep=I(research), temp=144000)

    W64 = ing(fine('AnnealedCopper'), 64)
    line('electric_motor_luv', 'electric_motor_iv', [ing(tag_of('long_rod', 'NeodymiumMagnetic')), H('long_rod', 2), W64, W64, W64, W64, Y(2)], [SOL(144), LUB(250)], 'electric_motor_luv')
    M = ing(I('electric_motor_luv'))
    line('electric_pump_luv', 'electric_pump_iv', [M, ing(I('pipe_small_high_pressure'), 2), H('plate', 2), H('screw', 8), ing(tag_of('ring', 'Silicone'), 4), H('rotor', 2), Y(2)], [SOL(144), LUB(250)], 'electric_pump_luv')
    line('conveyor_module_luv', 'conveyor_module_iv', [ing(I('electric_motor_luv'), 2), H('plate', 2), H('ring', 4), H('round', 32), Y(2)], [fl('molten_styrenebutadienerubber', 1440), LUB(250)], 'conveyor_module_luv')
    line('electric_piston_luv', 'electric_piston_iv', [M, H('plate', 6), H('ring', 4), H('round', 32), H('rod', 4), H('gear', 1), H('small_gear', 2), Y(4)], [SOL(144), LUB(250)], 'electric_piston_luv')
    line('robot_arm_luv', 'robot_arm_iv', [H('long_rod', 4), H('gear', 1), H('small_gear', 3), ing(I('electric_motor_luv'), 2), ing(I('electric_piston_luv')), ing('#c:circuits/master', 2), ing('#c:circuits/elite', 2),
                                           ing('#c:circuits/data', 6), Y(6)], [SOL(576), LUB(250)], 'robot_arm_luv')
    foil = ing(tag_of('foil', 'Electrum'), 64)
    line('emitter_luv', 'emitter_iv', [ing('#c:frames/hssg'), ing(I('emitter_iv')), ing(I('emitter_ev'), 2), ing(I('emitter_hv'), 4), ing('#c:circuits/data', 7), foil, foil, foil, Y(7)], [SOL(576)], 'emitter_luv')
    line('sensor_luv', 'sensor_iv', [ing('#c:frames/hssg'), ing(I('sensor_iv')), ing(I('sensor_ev'), 2), ing(I('sensor_hv'), 4), ing('#c:circuits/data', 7), foil, foil, foil, Y(7)], [SOL(576)], 'sensor_luv')

    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    stats['assembly_line'] = sum(1 for x in rec if x.startswith('assembly_line/'))
    return stats
