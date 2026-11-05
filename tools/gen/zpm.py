"""ZPM: quantum circuits up to Ultimate, ZPM parts, the LuV field generator, crafts that waited for the Ultimate circuit (D112).

Sources: GT_MachineRecipeLoader:425-426, 450-452, 504, 506, 516, 520-521, 585-588, 624-625, 1053, 1791, 2058-2068, 2095-2103, 2128-2134, 2161-2170,
2199-2211, 2243-2253, 2284-2294, 2310-2323; GT_CraftingRecipeLoader:334, 398-399, 409, 419; GT_Loader_MetaTileEntities:753-754, 1748.

Naquadah ore lies only on Mars and asteroids in IIS (iis_ore_veins.json), whose veins wait for the space slice (D17): everything that needs naquadah has its recipe,
but cannot be reached in survival until then. Crystal and wetware circuits are not ported: they need europium (Mars, fusion) and living cells.
"""
import os

NS = 'iis'


def generate(g):
    write, D, tag_of, shaped, shapeless, ref, mass, prop, mat_id = g['write'], g['D'], g['tag_of'], g['shaped'], g['shapeless'], g['ref'], g['mass'], g['prop'], g['mat_id']
    rec = {}

    def ing(i, c=1):
        return {'ingredient': i, 'count': c}

    def out(i, c=1):
        return {'id': i, 'count': c}

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
    # GT_CraftingRecipeLoader:409 HSS-E: 6 HSS-G, cobalt, manganese, silicon -> 9
    shapeless('dust/mix_hsse', ref('dust', 'HSSE'), 9, [tag_of('dust', 'HSSG')] * 6 + [tag_of('dust', 'Cobalt'), tag_of('dust', 'Manganese'), tag_of('dust', 'Silicon')])
    # ProcessingDust:41-44: dust -> ingot in the EBF, hot above 1750 K
    for m in ('HSSE', 'Naquadah', 'GalliumArsenide'):
        t = prop(m, 'BlastFurnaceTemp', 0) or g['code'][m]['blastFurnaceTemp']
        result = ref('hot_ingot', m) if t > 1750 else ref('ingot', m)
        mrec('blast', f'dust_{mat_id(m)}', [ing(tag_of('dust', m))], [out(result)], max(mass(m) // 40, 1) * t, 120, temp=t)
    # ProcessingRound: lathe nugget -> round (mass / 4 ticks, 8 EU/t), by hand "fX/X "
    mrec('lathe', 'round_hsse', [ing(tag_of('nugget', 'HSSE'))], [out(ref('round', 'HSSE'))], max(mass('HSSE') // 4, 1), 8)
    shaped('part/round_hsse', ref('round', 'HSSE'), 1, ['fX', 'X '], {'X': tag_of('nugget', 'HSSE')})
    # :1053 gallium arsenide; :419, :1791 indium gallium phosphide (mixer 200 t per 3 dusts, 8 EU/t)
    mrec('blast', 'gallium_arsenide_ingots', [ing(tag_of('ingot', 'Gallium')), ing(tag_of('ingot', 'Arsenic'))], [out(ref('ingot', 'GalliumArsenide'), 2)], 600, 120, temp=1200)
    shapeless('dust/mix_indium_gallium_phosphide', ref('dust', 'IndiumGalliumPhosphide'), 3, [tag_of('dust', 'Indium'), tag_of('dust', 'Gallium'), tag_of('dust', 'Phosphorus')])
    mrec('mixer', 'indium_gallium_phosphide', [ing(tag_of('dust', 'Indium')), ing(tag_of('dust', 'Gallium')), ing(tag_of('dust', 'Phosphorus'))], [out(ref('dust', 'IndiumGalliumPhosphide'), 3)], 200, 8)
    # GT_CraftingRecipeLoader:398-399, GT_MachineRecipeLoader:450-451 fibre-reinforced epoxy
    for fibre in ('glass_fiber', 'carbon_fiber'):
        shapeless(f'dust/mix_epoxid_fiber_reinforced_{fibre}', ref('dust', 'EpoxidFiberReinforced'), 1, [tag_of('dust', 'Epoxid'), I(fibre)])
        mrec('chemical_bath', f'epoxid_fiber_reinforced_{fibre}', [ing(I(fibre))], [out(ref('plate', 'EpoxidFiberReinforced'))], 240, 16, fin=fl('molten_epoxid', 144))
    # GT_CraftingRecipeLoader:334 superconductor wire: "NPT/CCC/NPT" with nitrogen cells, LV pumps, tiny tungstensteel pipes, naquadah wire
    N2 = {'neoforge:ingredient_type': 'neoforge:components', 'items': I('capsule'), 'components': {'iis:fluid_content': {'fluid': I('nitrogen'), 'amount': 1000}}}
    shaped('wire/superconductor_craft', I('wire_superconductor_1x'), 3, ['NPT', 'CCC', 'NPT'],
           {'N': N2, 'P': I('electric_pump_lv'), 'T': I('pipe_tiny_tungsten_steel'), 'C': '#iis:wires/1x/naquadah'})

    # --- wafers and chips ------------------------------------------------------------------------------------------------
    # :624-625 naquadah doped boule and wafer; :504, :506 they give four times the NAND and NOR wafers
    mrec('blast', 'silicon_boule_naquadah', [ing(I('block_silicon'), 16), ing(tag_of('ingot', 'Naquadah'))], [out(I('silicon_boule_naquadah'))], 1500, 1920, fin=fl('argon', 8000), temp=5400)
    cutter('silicon_wafer_naquadah', [ing(I('silicon_boule_naquadah'))], [out(I('silicon_wafer_naquadah'), 64)], 800, 384)
    W3 = I('silicon_wafer_naquadah')
    mrec('laser_engraver', 'wafer_nand_naquadah', [ing(W3)], [out(I('wafer_nand'), 4)], 200, 1920, keep=tag_of('lens', 'EnderPearl'), cleanroom=True)
    mrec('laser_engraver', 'wafer_nor_naquadah', [ing(W3)], [out(I('wafer_nor'), 4)], 200, 1920, keep=tag_of('lens', 'EnderEye'), cleanroom=True)
    # :520-521 QBit wafer from the nano wafer; :516 five chips
    mrec('chemical_reactor', 'wafer_quantum_cpu_radon', [ing(I('wafer_nano_cpu')), ing(tag_of('dust', 'IndiumGalliumPhosphide'))], [out(I('wafer_quantum_cpu'))], 600, 1920, fin=fl('radon', 50))
    mrec('chemical_reactor', 'wafer_quantum_cpu_eye', [ing(I('wafer_nano_cpu')), ing(I('quantum_eye'), 2)], [out(I('wafer_quantum_cpu'))], 400, 1920, fin=fl('molten_galliumarsenide', 288))
    cutter('chip_quantum_cpu', [ing(I('wafer_quantum_cpu'))], [out(I('chip_quantum_cpu'), 5)], 600, 48)

    # --- quantum circuits: :452 the board, :585-588 the chain up to Ultimate -----------------------------------------------
    mrec('chemical_reactor', 'circuit_board_fiberglass', [ing(tag_of('plate', 'EpoxidFiberReinforced')), ing(tag_of('foil', 'Copper'))], [out(I('circuit_board_fiberglass'))], 500, 10, fin=fl('sulfuricacid', 125))
    FG = I('circuit_board_fiberglass')
    mrec('circuit_assembler', 'circuit_quantumprocessor', [ing(FG), ing(I('chip_quantum_cpu')), ing(I('chip_nano_cpu')), ing(I('capacitor_smd'), 2), ing(I('transistor_smd'), 2), ing(fine('Platinum'), 2)],
         [out(I('circuit_quantumprocessor'))], 200, 2400, fin=SOL(72))
    mrec('circuit_assembler', 'circuit_quantumcomputer', [ing(FG), ing(I('circuit_quantumprocessor'), 2), ing(I('coil_small'), 4), ing(I('capacitor_smd'), 4), ing(I('chip_ram'), 4), ing(fine('Platinum'), 6)],
         [out(I('circuit_quantumcomputer'))], 400, 2400, fin=SOL(144))
    mrec('circuit_assembler', 'circuit_masterquantumcomputer', [ing(FG, 2), ing(I('circuit_quantumcomputer'), 3), ing(I('diode_smd'), 4), ing(I('chip_nor'), 4), ing(I('chip_ram'), 4), ing(fine('Platinum'), 6)],
         [out(I('circuit_masterquantumcomputer'))], 400, 2400, fin=SOL(144))
    mrec('circuit_assembler', 'circuit_quantummainframe', [ing('#c:frames/aluminium'), ing(I('circuit_masterquantumcomputer'), 4), ing(I('coil_small'), 4), ing(I('capacitor_smd'), 24), ing(I('chip_ram'), 16),
                                                           ing('#iis:wires/1x/annealed_copper', 12)], [out(I('circuit_quantummainframe'))], 1600, 7680, fin=SOL(288))

    # --- what waited for the Ultimate circuit: GT_Loader_MetaTileEntities:753-754 scanners "CTC/WMW/CRC", :1748 super buffer -------
    for t, cable in (('luv', 'vanadium_gallium'), ('zpm', 'naquadah')):
        shaped(f'lv/scanner_{t}', I(f'scanner_{t}'), 1, ['CTC', 'WMW', 'CRC'],
               {'M': I(f'hull_{t}'), 'T': I(f'emitter_{t}'), 'R': I(f'sensor_{t}'), 'C': '#c:circuits/ultimate', 'W': f'#iis:cables/1x/{cable}'})
    shaped('auto/super_buffer_zpm', I('super_buffer_zpm'), 1, ['DMV', 'DDD'], {'M': I('hull_zpm'), 'V': I('conveyor_module_zpm'), 'D': I('data_stick')})

    # --- ZPM parts on the assembly line: GT_MachineRecipeLoader:2058-2294; research 144000 ticks from the LuV part -----------------
    V = lambda n: ing('#iis:cables/4x/vanadium_gallium', n)
    H = lambda pre, n: ing(tag_of(pre, 'HSSE'), n)

    def line(rid, research, inputs, fluids, out_id, eut=24000):
        mrec('assembly_line', rid, inputs, [out(I(out_id))], 600, eut, fins=fluids, keep=I(research), temp=144000)

    W64 = ing(fine('Platinum'), 64)
    M = ing(I('electric_motor_zpm'))
    line('electric_motor_zpm', 'electric_motor_luv', [ing(tag_of('long_rod', 'NeodymiumMagnetic'), 2), H('long_rod', 4), H('ring', 4), H('round', 16), W64, W64, W64, W64, V(2)], [SOL(288), LUB(750)], 'electric_motor_zpm')
    line('electric_pump_zpm', 'electric_pump_luv', [M, ing(I('pipe_medium_high_pressure'), 2), H('plate', 2), H('screw', 8), ing(tag_of('ring', 'Silicone'), 16), H('rotor', 2), V(2)], [SOL(288), LUB(750)], 'electric_pump_zpm')
    line('conveyor_module_zpm', 'conveyor_module_luv', [ing(I('electric_motor_zpm'), 2), H('plate', 2), H('ring', 4), H('round', 32), V(2)], [fl('molten_styrenebutadienerubber', 2880), LUB(750)], 'conveyor_module_zpm')
    line('electric_piston_zpm', 'electric_piston_luv', [M, H('plate', 6), H('ring', 4), H('round', 32), H('rod', 4), H('gear', 1), H('small_gear', 2), V(4)], [SOL(288), LUB(750)], 'electric_piston_zpm')
    line('robot_arm_zpm', 'robot_arm_luv', [H('long_rod', 4), H('gear', 1), H('small_gear', 3), ing(I('electric_motor_zpm'), 2), ing(I('electric_piston_zpm')), ing('#c:circuits/master', 4), ing('#c:circuits/elite', 4),
                                            ing('#c:circuits/data', 12), V(6)], [SOL(1152), LUB(750)], 'robot_arm_zpm')
    foil = ing(tag_of('foil', 'Platinum'), 64)
    for part in ('emitter', 'sensor'):
        line(f'{part}_zpm', f'{part}_luv', [ing('#c:frames/hsse'), ing(I(f'{part}_luv')), ing(I(f'{part}_iv'), 2), ing(I(f'{part}_ev'), 4), ing('#c:circuits/elite', 7), foil, foil, foil, V(7)], [SOL(576)], f'{part}_zpm')
    # :2310-2323 the LuV field generator; of the three circuits only the master quantum computer is ported
    os64 = ing(fine('Osmium'), 64)
    line('field_generator_luv', 'field_generator_iv', [ing('#c:frames/hssg'), ing(tag_of('plate', 'HSSG'), 6), ing(I('quantum_star')), ing(I('emitter_luv'), 4), ing(I('circuit_masterquantumcomputer'), 8), os64, os64, os64, os64,
                                                       ing('#iis:cables/1x/yttrium_barium_cuprate', 8)], [SOL(576)], 'field_generator_luv', eut=6000)

    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
