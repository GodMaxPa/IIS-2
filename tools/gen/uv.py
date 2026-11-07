"""UV (D121): osmiridium and naquadah alloy, crystal and wetware circuits, field generators ZPM and UV, the UV parts, gravi star, coolant.

Sources: GT_MachineRecipeLoader:122-123, 453, 455-461, 523-524, 531-533, 590-597, 1051-1052, 1192, 2072-2081, 2106-2114, 2137-2143, 2173-2182, 2214-2224, 2256-2266, 2297-2307, 2325-2359, 2436-2464;
ProcessingCrafting:66; ProcessingDust:41-44; GT_Loader_ItemData:165.

Everything here needs naquadah or fusion Mark II at some point, and naquadah waits for space (D113), so survival cannot reach it until then. `python -I tools/check_reach.py --with=iis:dust_naquadah` shows the rest.
The lapotronic orb stages above the cluster (Energy_Module, Energy_Cluster, ZPM2) follow the "EnableZPMandUVBatteries" branch: IIS 1 has it on (D132).
"""
import os

NS = 'iis'
I = lambda x: f'{NS}:{x}'


def generate(g):
    write, D, tag_of, shaped, shapeless, ref, mass, prop, add_item_tag = g['write'], g['D'], g['tag_of'], g['shaped'], g['shapeless'], g['ref'], g['mass'], g['prop'], g['add_item_tag']
    rec = {}
    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1, chance=None: dict({'id': i, 'count': c}, **({'chance': chance} if chance else {}))
    fl = lambda fid, mb: {'ingredient': I(fid), 'amount': mb}
    fo = lambda fid, mb: {'id': I(fid), 'amount': mb}
    SOL = lambda mb: fl('molten_solderingalloy', mb)
    LUB = lambda mb: fl('lubricant', mb)
    HELIUM = fl('helium', 1000)
    fine = lambda m: tag_of('wire_fine', m)

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, fins=None, fout=None, keep=None, temp=None, circuit=None, cleanroom=False):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(dur), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if fins:
            r['fluid_inputs'] = fins
        if fout:
            r['fluid_outputs'] = [fout]
        if keep:
            r['keep'] = [keep]
        if temp:
            r['temp'] = temp
        if circuit is not None:
            r['circuit'] = circuit
        if cleanroom:
            r['cleanroom'] = True
        rec[f'{rtype}/{rid}'] = r

    # --- materials -----------------------------------------------------------------------------------------------------
    mrec('blast', 'osmiridium_ingots', [ing(tag_of('ingot', 'Iridium'), 3), ing(tag_of('ingot', 'Osmium'))], [out(ref('hot_ingot', 'Osmiridium'), 4)], 500, 1920, fin=HELIUM, temp=2900)   # :1051
    mrec('blast', 'naquadah_alloy_ingots', [ing(tag_of('ingot', 'Naquadah')), ing(tag_of('ingot', 'Osmiridium'))], [out(ref('hot_ingot', 'NaquadahAlloy'), 2)], 500, 30720, fin=fl('argon', 1000), temp=7200)   # :1052
    # ProcessingDust:41-44: dust -> hot ingot in the EBF
    for m in ('Osmiridium', 'NaquadahAlloy'):
        t = prop(m, 'BlastFurnaceTemp', 0) or g['code'][m]['blastFurnaceTemp']
        mrec('blast', f'dust_{g["mat_id"](m)}', [ing(tag_of('dust', m))], [out(ref('hot_ingot', m))], max(mass(m) // 40, 1) * t, 120, temp=t)
    # rounds of neutronium for the UV motor, conveyor and piston: ProcessingRound, as for HSS-E (D112)
    mrec('lathe', 'round_neutronium', [ing(tag_of('nugget', 'Neutronium'))], [out(ref('round', 'Neutronium'))], max(mass('Neutronium') // 4, 1), 8)
    shaped('part/round_neutronium', ref('round', 'Neutronium'), 1, ['fX', 'X '], {'X': tag_of('nugget', 'Neutronium')})
    # :1892-1893 naquadah centrifuges into enriched naquadah and naquadria; neutronium needs the naquadria
    mrec('centrifuge', 'naquadah', [ing(tag_of('dust', 'Naquadah'))], [out(ref('tiny_dust', 'NaquadahEnriched'), 1, 5000), out(ref('tiny_dust', 'Naquadria'), 1, 1000)], 3200, 320)
    mrec('centrifuge', 'naquadah_enriched', [ing(tag_of('dust', 'NaquadahEnriched'))], [out(ref('small_dust', 'Naquadria'), 1, 2000), out(ref('small_dust', 'Naquadah'), 1, 3000)], 6400, 640)
    # :1192 gravi star: nether star + 288 L of neutronium
    mrec('autoclave', 'gravi_star', [ing('minecraft:nether_star')], [out(I('gravi_star'))], 480, 7680, fin=fl('molten_neutronium', 288))
    # :122-123 coolant
    mrec('mixer', 'ic2coolant_water', [ing(tag_of('dust', 'Lapis'))], [], 256, 48, fin={'ingredient': 'minecraft:water', 'amount': 125}, fout=fo('ic2coolant', 125))
    mrec('mixer', 'ic2coolant_distilled', [ing(tag_of('dust', 'Lapis'))], [], 256, 48, fin=fl('distilled_water', 1000), fout=fo('ic2coolant', 1000))

    # emerald is vanilla, its block too: compression, then the cutter as for every block (ProcessingBlock), the plate goes into the crystal chip
    mrec('compressor', 'emerald_block', [ing('minecraft:emerald', 9)], [out('minecraft:emerald_block')], 300, 2)
    em = max(mass('Emerald') * 10, 1)
    for rid, liq, amt, dd in (('', {'ingredient': 'minecraft:water', 'amount': max(4, min(1000, em * 30 // 320))}, 0, em * 2), ('_distilled', fl('distilled_water', max(3, min(750, em * 30 // 426))), 0, em * 2),
                              ('_lubricant', fl('lubricant', max(1, min(250, em * 30 // 1280))), 0, em)):
        mrec('cutter', f'emerald_block{rid}', [ing('minecraft:emerald_block')], [out(ref('plate', 'Emerald'), 9)], dd, 30, fin=liq)

    # --- crystal chain -----------------------------------------------------------------------------------------------------
    FG, MF = I('circuit_board_fiberglass'), I('circuit_board_multifiberglass')
    mrec('chemical_reactor', 'circuit_board_multifiberglass', [ing(FG), ing(tag_of('foil', 'Electrum'), 16)], [out(MF)], 100, 480, fin=fl('sulfuricacid', 250))   # :453
    for gem in ('emerald', 'olivine'):
        mrec('autoclave', f'raw_crystal_chip_{gem}', [ing(I(f'gem_exquisite_{gem}'))], [out(I('raw_crystal_chip'), 1, 1000)], 12000, 320, fin=fl('molten_europium', 16), cleanroom=True)   # :523-524
        mrec('blast', f'crystal_chip_elite_{gem}', [ing(I('raw_crystal_chip')), ing(tag_of('plate', gem.capitalize()))], [out(I('crystal_chip_elite'))], 900, 480, fin=HELIUM, temp=5000)   # :532-533
    shapeless('part/raw_crystal_chip', I('raw_crystal_chip'), 9, [I('chip_crystal_cpu')])   # :531
    mrec('laser_engraver', 'chip_crystal_cpu', [ing(I('crystal_chip_elite'))], [out(I('chip_crystal_cpu'))], 100, 10000, keep='#iis:crafting_lens/green', cleanroom=True)   # ProcessingCrafting:66
    # :590-593 Crystal circuits; the mainframe is the Superconductor circuit, the first circuit of the UV tier
    NT = fine('NiobiumTitanium')
    mrec('circuit_assembler', 'circuit_crystalprocessor', [ing(MF), ing(I('chip_crystal_cpu')), ing(I('chip_nano_cpu')), ing(I('capacitor_smd'), 2), ing(I('transistor_smd'), 2), ing(NT, 2)], [out(I('circuit_crystalprocessor'))], 200, 9600, fin=SOL(72))
    mrec('circuit_assembler', 'circuit_crystalcomputer', [ing(MF), ing(I('circuit_crystalprocessor'), 2), ing(I('coil_small'), 4), ing(I('capacitor_smd'), 4), ing(I('chip_ram'), 4), ing(NT, 6)], [out(I('circuit_crystalcomputer'))], 400, 9600, fin=SOL(144))
    mrec('circuit_assembler', 'circuit_ultimatecrystalcomputer', [ing(MF, 2), ing(I('circuit_crystalcomputer'), 3), ing(I('diode_smd'), 4), ing(I('chip_nor'), 4), ing(I('chip_ram'), 4), ing(NT, 6)], [out(I('circuit_ultimatecrystalcomputer'))], 400, 9600, fin=SOL(144))
    mrec('circuit_assembler', 'circuit_crystalmainframe', [ing('#c:frames/aluminium'), ing(I('circuit_ultimatecrystalcomputer'), 4), ing(I('coil_small'), 4), ing(I('capacitor_smd'), 24), ing(I('chip_ram'), 16), ing('#iis:wires/1x/superconductor', 12)],
         [out(I('circuit_crystalmainframe'))], 1600, 30720, fin=SOL(288))

    # --- wetware chain -----------------------------------------------------------------------------------------------------
    mrec('fluid_solidifier', 'petri_dish', [], [out(I('petri_dish'))], 160, 16, fin=fl('molten_polytetrafluoroethylene', 36), keep=I('mold_cylinder'))   # :455; polystyrene is not ported
    # GT_Loader_ItemData:165 raw meat grinds into its dust
    for meat in ('beef', 'porkchop', 'chicken', 'mutton', 'rabbit'):
        mrec('macerator', f'dust_meat_{meat}', [ing(f'minecraft:{meat}')], [out(ref('dust', 'MeatRaw'))], 400, 2)
    mrec('mixer', 'growth_medium_raw', [ing('minecraft:sugar', 4), ing(tag_of('dust', 'MeatRaw')), ing(tag_of('tiny_dust', 'Salt'))], [], 160, 16, fin=fl('distilled_water', 4000), fout=fo('growthmediumraw', 4000))   # :457
    mrec('fluid_heater', 'growth_medium_sterilized', [], [], 60, 24, fin=fl('growthmediumraw', 1000), fout=fo('growthmediumsterilized', 1000), circuit=1)   # :458
    mrec('assembler', 'circuit_board_wetware', [ing(MF), ing(I('petri_dish')), ing(I('electric_pump_lv')), ing(I('sensor_lv')), ing('#c:circuits/good')], [out(I('circuit_board_wetware'))], 400, 480, fin=fl('growthmediumsterilized', 250))   # :460-461
    WW, YB = I('circuit_board_wetware'), fine('YttriumBariumCuprate')
    mrec('assembly_line', 'chip_neuro_cpu', [ing(WW), ing(I('stem_cell'), 8), ing(I('glass_tube'), 8), ing(I('pipe_tiny_plastic'), 4), ing(I('item_casing_gold'), 8), ing(tag_of('foil', 'Silicone'), 64), ing(tag_of('plate', 'StainlessSteel'), 4)],
         [out(I('chip_neuro_cpu'))], 200, 80000, fins=[fl('growthmediumsterilized', 250), fl('uumatter', 100), fl('ic2coolant', 1000)], keep=I('circuit_crystalmainframe'), temp=72000)   # :2436-2449
    mrec('circuit_assembler', 'circuit_neuroprocessor', [ing(I('chip_neuro_cpu')), ing(I('chip_crystal_cpu')), ing(I('chip_nano_cpu')), ing(I('capacitor_smd'), 2), ing(I('transistor_smd'), 2), ing(YB, 2)], [out(I('circuit_neuroprocessor'))], 200, 38400, fin=SOL(72))   # :595
    mrec('circuit_assembler', 'circuit_wetwarecomputer', [ing(WW), ing(I('circuit_neuroprocessor'), 2), ing(I('coil_small'), 4), ing(I('capacitor_smd'), 4), ing(I('chip_ram'), 4), ing(YB, 6)], [out(I('circuit_wetwarecomputer'))], 400, 38400, fin=SOL(144))   # :596
    mrec('circuit_assembler', 'circuit_wetwaresupercomputer', [ing(WW, 2), ing(I('circuit_wetwarecomputer'), 3), ing(I('diode_smd'), 4), ing(I('chip_nor'), 4), ing(I('chip_ram'), 4), ing(YB, 6)], [out(I('circuit_wetwaresupercomputer'))], 400, 38400, fin=SOL(144))   # :597
    mrec('assembly_line', 'circuit_wetwaremainframe', [ing('#c:frames/tritanium', 4), ing(I('circuit_wetwaresupercomputer'), 8), ing(I('coil_small'), 4), ing(I('capacitor_smd'), 24), ing(I('resistor_smd'), 64), ing(I('transistor_smd'), 32), ing(I('diode_smd'), 16),
                                                        ing(I('chip_ram'), 16), ing('#iis:wires/1x/superconductor', 32), ing(tag_of('foil', 'Silicone'), 64)], [out(I('circuit_wetwaremainframe'))], 2000, 300000,
         fins=[SOL(2880), fl('ic2coolant', 10000)], keep=I('circuit_wetwaresupercomputer'), temp=288000)   # :2451-2464

    # --- field generators and UV parts on the assembly line -----------------------------------------------------------------
    def line(rid, research, time, inputs, fluids, eut=100000):
        mrec('assembly_line', rid, inputs, [out(I(rid))], 600, eut, fins=fluids, keep=research, temp=time)

    # :2325-2341 field generator ZPM: 16 Crystalprocessor Assemblies or Wetwareprocessors
    add_item_tag('iis:field_generator_zpm_circuits', I('circuit_crystalcomputer'))
    add_item_tag('iis:field_generator_zpm_circuits', I('circuit_neuroprocessor'))
    os64 = ing(fine('Osmium'), 64)
    line('field_generator_zpm', I('field_generator_luv'), 144000, [ing('#c:frames/hsse'), ing(tag_of('plate', 'HSSE'), 6), ing(I('quantum_star'), 4), ing(I('emitter_zpm'), 4), ing('#iis:field_generator_zpm_circuits', 16)] + [os64] * 8 + [ing('#iis:cables/4x/vanadium_gallium', 8)],
         [SOL(1152)], eut=24000)
    N = lambda pre, n: ing(tag_of(pre, 'Neutronium'), n)
    T = lambda n: ing('#iis:cables/4x/niobium_titanium', n)
    S64 = ing('#iis:wires/1x/superconductor', 64)
    M = ing(I('electric_motor_uv'))
    line('electric_motor_uv', I('electric_motor_zpm'), 288000, [ing(I('block_neodymium_magnetic')), N('long_rod', 4), N('ring', 4), N('round', 16), S64, S64, S64, S64, T(2)], [SOL(1296), LUB(2000)])   # :2070-2081
    line('electric_pump_uv', I('electric_pump_zpm'), 288000, [M, ing(I('pipe_large_high_pressure'), 2), N('plate', 2), N('screw', 8), ing(tag_of('ring', 'Silicone'), 16), N('rotor', 2), T(2)], [SOL(1296), LUB(2000)])   # :2105-2114
    line('conveyor_module_uv', I('conveyor_module_zpm'), 288000, [ing(I('electric_motor_uv'), 2), N('plate', 2), N('ring', 4), N('round', 32), T(2)], [fl('molten_styrenebutadienerubber', 2880), LUB(2000)])   # :2136-2143
    line('electric_piston_uv', I('electric_piston_zpm'), 288000, [M, N('plate', 6), N('ring', 4), N('round', 32), N('rod', 4), N('gear', 1), N('small_gear', 2), T(4)], [SOL(1296), LUB(2000)])   # :2172-2182
    line('robot_arm_uv', I('robot_arm_zpm'), 288000, [N('long_rod', 4), N('gear', 1), N('small_gear', 3), ing(I('electric_motor_uv'), 2), ing(I('electric_piston_uv')), ing('#c:circuits/master', 8), ing('#c:circuits/elite', 8), ing('#c:circuits/data', 24), T(6)],
         [SOL(2304), LUB(2000)])   # :2213-2224
    foil = ing(tag_of('foil', 'Osmiridium'), 64)
    for part in ('emitter', 'sensor'):   # :2255-2266, :2296-2307
        line(f'{part}_uv', I(f'{part}_zpm'), 288000, [ing('#c:frames/neutronium'), ing(I(f'{part}_zpm')), ing(I(f'{part}_luv'), 2), ing(I(f'{part}_iv'), 4), ing('#c:circuits/master', 7), foil, foil, foil, T(7)], [SOL(576)])
    line('field_generator_uv', I('field_generator_zpm'), 288000, [ing('#c:frames/neutronium'), N('plate', 6), ing(I('gravi_star')), ing(I('emitter_uv'), 4), ing('#c:circuits/master', 64)] + [os64] * 8 + [T(8)], [SOL(2304)])   # :2343-2359

    # GT_Loader_MetaTileEntities:335-336, 347-348 quantum tanks and chests EV and IV, closed until now: Master circuits and europium, Ultimate circuits and americium
    for t, circ, plate in (('ev', 'master', 'Europium'), ('iv', 'ultimate', 'Americium')):
        q = {'M': I(f'hull_{t}'), 'G': I(f'field_generator_{t}'), 'D': f'#c:circuits/{circ}', 'P': tag_of('plate', plate)}
        shaped(f'auto/quantum_chest_{t}', I(f'quantum_chest_{t}'), 1, ['DPD', 'PMP', 'DGD'], q)
        shaped(f'auto/quantum_tank_{t}', I(f'quantum_tank_{t}'), 1, ['DGD', 'PMP', 'DPD'], q)
    # :2482-2533 the "EnableZPMandUVBatteries" branch: IIS 1 has it on in config/GregTech/OverpoweredStuff.cfg (D132; D121 took the default branch by mistake)
    ASOC, HP, NEURO = ing(I('wafer_soc2'), 64), ing(I('wafer_hpic'), 64), ing(I('circuit_neuroprocessor'), 64)
    mrec('assembly_line', 'energy_module', [ing(tag_of('plate', 'Europium'), 16)] + [ing('#c:circuits/ultimate')] * 4 + [ing(I('energy_orb_cluster'), 8), ing(I('field_generator_luv'), 2), ASOC, ASOC, ing(I('diode_smd'), 8), ing(I('cable_naquadah_1x'), 32)],
         [out(I('energy_module'))], 2000, 100000, fins=[SOL(2880), fl('ic2coolant', 16000)], keep=I('energy_orb_cluster'), temp=288000)
    mrec('assembly_line', 'energy_cluster', [ing(tag_of('plate', 'Americium'), 16)] + [ing('#c:circuits/superconductor')] * 4 + [ing(I('energy_module'), 8), ing(I('field_generator_zpm'), 2), HP, HP, ing(I('diode_smd'), 16), ing(I('cable_naquadah_alloy_1x'), 32)],
         [out(I('energy_cluster'))], 2000, 200000, fins=[SOL(2880), fl('ic2coolant', 16000)], keep=I('energy_module'), temp=288000)
    mrec('assembly_line', 'ultimate_battery', [N('plate', 16)] + [ing('#c:circuits/infinite')] * 4 + [ing(I('energy_cluster'), 8), ing(I('field_generator_uv'), 2), NEURO, NEURO, ing(I('diode_smd'), 16), ing('#iis:wires/1x/superconductor', 32)],
         [out(I('ultimate_battery'))], 2000, 300000, fins=[SOL(2880), fl('ic2coolant', 16000), fl('molten_naquadria', 1152)], keep=I('energy_cluster'), temp=288000)

    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
