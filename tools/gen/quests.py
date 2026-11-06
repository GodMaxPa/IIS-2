"""IIS 1 quest book (BetterQuesting, config/betterquesting/DefaultQuests.json) -> FTB Quests 26.1.2 (JSON5, file version 13).

D101 (owner: "квесты как в ИИС 1", the whole book; quests with items not yet ported stay hidden).
Run after gen_content.py (it reads the generated lang and item models):

    IIS1_INSTANCE=<IIS 1 game dir> IIS_GT5_SRC=<GT5-II src/main/java/gregtech> python -I tools/gen/quests.py

Output: quests/ftbquests/ (copy into <game>/config/ftbquests/quests/) and quests/REPORT.md.

Item mapping, in order:
  * vanilla 1.7.10 ids: renamed ones by table, the rest as is if the item still exists;
  * IC2 items that IIS 2 replaced (cells -> capsules, resin, treetap, circuits ...) by table;
  * GT meta-tools -> our tool of the same kind, iron head (BetterQuesting ignored the tool NBT);
  * GT battery buffers by MetaTileEntity key (GT gives all sizes one name);
  * everything else from GT by its Russian name in GregTech.lang.ru (the generator names our content from the same keys),
    first in our lang, then in the vanilla one; OreDict name as the last resort.
A quest whose task needs an unmapped item or fluid is not written; quests that depended on it depend on its own
dependencies instead. Unmapped reward items are dropped (mostly GT coins: the IIS 1 shop is not ported).
"""
import collections
import json
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INST = os.environ.get('IIS1_INSTANCE', 'C:/Users/Godkiller/AppData/Roaming/forServerClient')
GTSRC = os.environ.get('IIS_GT5_SRC', 'C:/Users/Godkiller/AppData/Local/Temp/gt5src/src/main/java/gregtech')
NS = 'iis'
OUT = os.path.join(ROOT, 'quests', 'ftbquests')
GEN = os.path.join(ROOT, 'src', 'generated', 'resources', 'assets', NS)


def read_lang(path):
    out = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        line = line.rstrip('\n').rstrip('\r')
        if '=' in line and not line.startswith('#'):
            k, _, v = line.partition('=')
            out[k.strip()] = v
    return out


def gt_lang():
    out = {}
    for line in open(os.path.join(INST, 'GregTech.lang.ru'), encoding='utf-8', errors='replace'):
        line = line.strip()
        if line.startswith('S:') and '=' in line:
            k, _, v = line[2:].partition('=')
            out[k] = v
    return out


def mte_names():
    """GT_Loader_MetaTileEntities: MetaTileEntity id -> unlocalized name (gt.blockmachines.<name>.name)."""
    src = open(os.path.join(GTSRC, 'loaders', 'preload', 'GT_Loader_MetaTileEntities.java'), encoding='utf-8', errors='replace').read()
    return {int(m.group(1)): m.group(2) for m in re.finditer(r'new GT_MetaTileEntity_\w+\(\s*(\d+)\s*,\s*"([^"]+)"', src)}


def vanilla_items_and_names():
    jar = os.path.join(ROOT, 'build', 'moddev', 'artifacts', 'minecraft-patched-26.1.2.117-merged.jar')
    with zipfile.ZipFile(jar) as z:
        items = {n[len('assets/minecraft/items/'):-5] for n in z.namelist() if n.startswith('assets/minecraft/items/') and n.endswith('.json')}
    ru = {}
    for idx in ('34.json', '30.json'):
        p = os.path.join(INST, 'assets', 'indexes', idx)
        if not os.path.exists(p):
            continue
        h = json.load(open(p, encoding='utf-8'))['objects'].get('minecraft/lang/ru_ru.json')
        if h:
            ru = json.load(open(os.path.join(INST, 'assets', 'objects', h['hash'][:2], h['hash']), encoding='utf-8'))
            break
    return items, ru


# 1.7.10 -> 26.1 (id, damage); damage None = any
MC_RENAMES = {
    ('planks', 0): 'oak_planks', ('log', 0): 'oak_log', ('wool', 0): 'white_wool', ('wool', 15): 'black_wool',
    ('carpet', 15): 'black_carpet', ('dye', 0): 'ink_sac', ('dye', 1): 'red_dye', ('coal', 1): 'charcoal',
    ('brick_block', 0): 'bricks', ('reeds', 0): 'sugar_cane', ('sign', 0): 'oak_sign', ('skull', 1): 'wither_skeleton_skull',
    ('skull', 3): 'player_head', ('stone_slab', 0): 'smooth_stone_slab', ('wooden_slab', 0): 'oak_slab',
    ('stonebrick', 0): 'stone_bricks', ('wooden_door', 0): 'oak_door', ('bed', 0): 'red_bed', ('golden_rail', 0): 'powered_rail',
    ('wooden_pressure_plate', 0): 'oak_pressure_plate', ('fence', 0): 'oak_fence', ('trapdoor', 0): 'oak_trapdoor',
    ('stained_hardened_clay', 0): 'white_terracotta', ('hardened_clay', 0): 'terracotta', ('melon_block', 0): 'melon',
    ('lit_pumpkin', 0): 'jack_o_lantern', ('noteblock', 0): 'note_block', ('web', 0): 'cobweb', ('sapling', 0): 'oak_sapling',
}
# IC2 items that IIS 2 has an own counterpart for (D74: cells are capsules; D92: IC2 circuits are GT circuits)
IC2_MAP = {
    ('itemCellEmpty', 0): f'{NS}:capsule', ('itemFluidCell', 0): f'{NS}:universal_capsule', ('itemHarz', 0): f'{NS}:sticky_resin',
    ('itemTreetap', 0): f'{NS}:treetap', ('itemBiochaff', 0): f'{NS}:bio_chaff', ('itemPartCircuit', 0): f'{NS}:circuit_basic',
    ('itemPartCircuitAdv', 0): f'{NS}:circuit_advanced', ('blockRubWood', 0): f'{NS}:rubber_log', ('itemToolMEter', 0): f'{NS}:multimeter',
    ('itemRubber', 0): f'{NS}:plate_rubber',
}
# Railcraft items ported in D102 (track items are told apart by their track id in the damage value)
# D115: Galacticraft, first stage of the space slice: own items that stand for GC ones
GC_MAP = {('GalacticraftCore:item.spaceship', d): 'iis:rocket_t1' for d in (0, 1, 2, 3)}
GC_MAP.update({('GalacticraftCore:item.heavyPlating', 0): 'iis:heavy_plating', ('GalacticraftCore:item.oxygenConcentrator', 0): 'iis:oxygen_concentrator',
               ('GalacticraftCore:tile.rocketWorkbench', 0): 'iis:nasa_workbench', ('GalacticraftCore:item.basicItem', 7): 'iis:compressed_tin',
               ('GalacticraftCore:item.basicItem', 8): 'iis:compressed_aluminium', ('GalacticraftCore:item.basicItem', 9): 'iis:compressed_steel',
               ('GalacticraftCore:item.basicItem', 10): 'iis:compressed_bronze', ('GalacticraftCore:item.basicItem', 11): 'iis:compressed_iron',
               ('GalacticraftCore:item.basicItem', 13): 'iis:wafer_basic', ('GalacticraftCore:item.basicItem', 14): 'iis:wafer_advanced',
               ('GalacticraftCore:item.rocketFins', 0): 'iis:rocket_fins', ('GalacticraftCore:item.noseCone', 0): 'iis:nose_cone',
               ('GalacticraftCore:item.engine', 0): 'iis:rocket_engine_t1', ('GalacticraftCore:item.parachute', 0): 'iis:parachute',
               ('GalacticraftCore:item.canvas', 0): 'iis:canvas', ('GalacticraftCore:tile.landingPad', 0): 'iis:launch_pad',
               ('GalacticraftCore:tile.fuelLoader', 0): 'iis:fuel_loader', ('GalacticraftCore:item.oxygenMask', 0): 'iis:oxygen_mask',
               ('GalacticraftCore:item.canister', 0): 'iis:canister_tin', ('GalacticraftCore:item.airVent', 0): 'iis:air_vent',
               ('GalacticraftCore:item.airFan', 0): 'iis:air_fan', ('GalacticraftCore:item.oxygenGear', 0): 'iis:oxygen_gear',
               ('GalacticraftCore:tile.oxygenPipe', 0): 'iis:oxygen_pipe', ('GalacticraftCore:item.oxygenTankLightFull', 900): 'iis:oxygen_tank_light',
               ('gregtech:gt.metaitem.01', 32462): 'iis:heavy_ingot_t1', ('gregtech:gt.metaitem.01', 32405): 'iis:large_steel_cell', ('gregtech:gt.metaitem.01', 32497): 'iis:schematic_3x3',
               # D116
               ('gregtech:gt.blockmachines', 1193): 'iis:fusion_computer_luv', ('gregtech:gt.blockcasings4', 7): 'iis:fusion_coil', ('gregtech:gt.neutronreflector', 0): 'iis:neutron_reflector',
               ('gregtech:gt.metaitem.03', 32052): 'iis:wafer_hpic', ('gregtech:gt.blockcasings', 15): 'iis:coil_superconductor'})
# D122
GC_MAP.update({('gregtech:gt.metaitem.01', 32018): 'iis:data_reader_mv', ('gregtech:gt.metaitem.01', 32019): 'iis:data_reader_ev', ('gregtech:gt.metaitem.01', 32762): 'iis:portable_scanner', ('gregtech:gt.metaitem.01', 32707): 'iis:data_orb',
               ('gregtech:gt.blockmachines', 1156): 'iis:seismic_prospector_lv', ('gregtech:gt.blockmachines', 1173): 'iis:seismic_prospector_ev', ('gregtech:gt.blockmachines', 132): 'iis:hatch_data_access_luv', ('gregtech:gt.blockmachines', 5165): 'iis:pipe_small_high_pressure',
               ('gregtech:gt.blockmachines', 30): 'iis:hatch_dynamo_ulv', ('gregtech:gt.blockmachines', 40): 'iis:hatch_energy_ulv', ('gregtech:gt.blockmachines', 50): 'iis:hatch_input_ulv', ('gregtech:gt.blockmachines', 60): 'iis:hatch_output_ulv',
               ('gregtech:gt.metaitem.02', 32010): 'iis:gelled_toluene', ('gregtech:gt.blockcasings', 9): 'iis:casing_max', ('IC2:itemDynamite', 0): 'iis:dynamite', ('IC2:blockITNT', 0): 'iis:industrial_tnt',
               ('gregtech:gt.blockmachines', 70): 'iis:hatch_input_bus_ulv', ('gregtech:gt.blockmachines', 80): 'iis:hatch_output_bus_ulv'})
# D121: gem blocks: GT_Loader_Item_Block_And_Fluid:322-379
GC_MAP.update({('gregtech:gt.blockgem1', 4): 'iis:block_blue_topaz', ('gregtech:gt.blockgem2', 12): 'iis:block_sapphire', ('gregtech:gt.blockgem2', 5): 'iis:block_opal', ('gregtech:gt.blockgem1', 7): 'iis:block_ender_eye'})
# D121: UV tier: GT_Loader_MetaTileEntities ids and casings, GT_MetaGenerated_Item_01 and _03
GC_MAP.update({('gregtech:gt.blockmachines', 1194): 'iis:fusion_computer_zpm', ('gregtech:gt.blockmachines', 1195): 'iis:fusion_computer_uv', ('gregtech:gt.blockmachines', 1153): 'iis:large_plasma_turbine',
               ('gregtech:gt.blockmachines', 1188): 'iis:advanced_processing_array', ('gregtech:gt.blockmachines', 1178): 'iis:ore_drill_3', ('gregtech:gt.blockmachines', 1179): 'iis:ore_drill_4',
               ('gregtech:gt.blockmachines', 1198): 'iis:plasma_generator_luv', ('gregtech:gt.blockcasings5', 6): 'iis:coil_naquadah_alloy', ('gregtech:gt.blockcasings4', 6): 'iis:casing_fusion',
               ('gregtech:gt.blockcasings4', 8): 'iis:casing_fusion_2', ('gregtech:gt.blockcasings8', 2): 'iis:robust_hssg_casing', ('gregtech:gt.blockcasings4', 12): 'iis:turbine_casing_tungstensteel',
               ('gregtech:gt.blockcasings8', 3): 'iis:mining_osmiridium_casing', ('gregtech:gt.blockcasings', 8): 'iis:casing_uv',
               ('gregtech:gt.metaitem.01', 32608): 'iis:electric_motor_uv', ('gregtech:gt.metaitem.01', 32622): 'iis:electric_pump_uv', ('gregtech:gt.metaitem.01', 32638): 'iis:conveyor_module_uv',
               ('gregtech:gt.metaitem.01', 32647): 'iis:electric_piston_uv', ('gregtech:gt.metaitem.01', 32657): 'iis:robot_arm_uv', ('gregtech:gt.metaitem.01', 32687): 'iis:emitter_uv',
               ('gregtech:gt.metaitem.01', 32697): 'iis:sensor_uv', ('gregtech:gt.metaitem.01', 32676): 'iis:field_generator_zpm', ('gregtech:gt.metaitem.01', 32677): 'iis:field_generator_uv',
               ('gregtech:gt.metaitem.01', 32726): 'iis:gravi_star', ('gregtech:gt.metaitem.01', 32712): 'iis:circuit_board_multifiberglass', ('gregtech:gt.metaitem.01', 32713): 'iis:crystal_chip_elite',
               ('gregtech:gt.metaitem.01', 32313): 'iis:mold_cylinder', ('gregtech:gt.metaitem.03', 32069): 'iis:raw_crystal_chip', ('gregtech:gt.metaitem.03', 32070): 'iis:chip_crystal_cpu',
               ('gregtech:gt.metaitem.03', 32072): 'iis:chip_neuro_cpu', ('gregtech:gt.metaitem.03', 32022): 'iis:petri_dish', ('gregtech:gt.metaitem.03', 32006): 'iis:circuit_board_wetware',
               ('gregtech:gt.metaitem.03', 32089): 'iis:circuit_crystalprocessor', ('gregtech:gt.metaitem.03', 32096): 'iis:circuit_crystalcomputer', ('gregtech:gt.metaitem.03', 32090): 'iis:circuit_ultimatecrystalcomputer',
               ('gregtech:gt.metaitem.03', 32091): 'iis:circuit_crystalmainframe', ('gregtech:gt.metaitem.03', 32092): 'iis:circuit_neuroprocessor', ('gregtech:gt.metaitem.03', 32093): 'iis:circuit_wetwarecomputer',
               ('gregtech:gt.metaitem.03', 32094): 'iis:circuit_wetwaresupercomputer', ('gregtech:gt.metaitem.03', 32095): 'iis:circuit_wetwaremainframe'})
# D117: the reactor, its parts and the solar panels are own items that stand for the IC2 and GT ones
GC_MAP.update({('IC2:blockGenerator', 5): 'iis:nuclear_reactor', ('IC2:blockReactorChamber', 0): 'iis:reactor_chamber', ('IC2:itemUran', 0): 'iis:enriched_uranium',
               ('IC2:itemFuelRod', 0): 'iis:fuel_rod_empty', ('IC2:reactorCondensatorLap', 1): 'iis:reactor_condensator_lap', ('IC2:reactorCoolantSimple', 1): 'iis:coolant_10k',
               ('IC2:reactorCoolantTriple', 1): 'iis:coolant_30k', ('IC2:reactorCoolantSix', 1): 'iis:coolant_60k', ('IC2:reactorHeatSwitch', 1): 'iis:reactor_heat_switch',
               ('IC2:reactorHeatSwitchDiamond', 1): 'iis:reactor_heat_switch_diamond', ('IC2:reactorHeatSwitchSpread', 1): 'iis:reactor_heat_switch_spread',
               ('IC2:reactorPlating', 0): 'iis:reactor_plating', ('IC2:reactorPlatingHeat', 0): 'iis:reactor_plating_heat', ('IC2:reactorReflector', 1): 'iis:reactor_reflector',
               ('IC2:reactorReflectorThick', 1): 'iis:reactor_reflector_thick', ('IC2:reactorVent', 1): 'iis:reactor_vent', ('IC2:reactorVentDiamond', 1): 'iis:reactor_vent_diamond',
               ('gregtech:gt.Thoriumcell', 0): 'iis:rod_thorium_1', ('gregtech:gt.reactorUraniumSimple', 0): 'iis:rod_uranium_1',
               ('IC2:itemPartCarbonMesh', 0): 'iis:carbon_mesh', ('IC2:itemPartCarbonPlate', 0): 'iis:carbon_plate',
               ('gregtech:gt.metaitem.01', 32750): 'iis:solar_panel', ('gregtech:gt.metaitem.01', 32751): 'iis:solar_panel_8v', ('gregtech:gt.metaitem.01', 32752): 'iis:solar_panel_lv',
               ('gregtech:gt.metaitem.01', 32753): 'iis:solar_panel_mv', ('gregtech:gt.metaitem.01', 32754): 'iis:solar_panel_hv', ('gregtech:gt.metaitem.01', 32755): 'iis:solar_panel_ev',
               ('gregtech:gt.metaitem.01', 32756): 'iis:solar_panel_iv', ('gregtech:gt.metaitem.01', 32757): 'iis:solar_panel_luv', ('gregtech:gt.metaitem.01', 32758): 'iis:solar_panel_zpm',
               ('gregtech:gt.metaitem.01', 32759): 'iis:solar_panel_uv'})
# D118: IC2 gear and parts, own items too; damage values differ between charged and empty forms in the quests
for _id, _to in (('itemAdvBat', 'battery_adv'), ('itemBatRE', 'battery_re'), ('itemBatREDischarged', 'battery_re'), ('itemBatCrystal', 'energy_crystal'), ('itemBatLamaCrystal', 'lapotron_crystal'),
                 ('itemBatChargeRE', 'charging_battery_re'), ('itemBatChargeAdv', 'charging_battery_adv'), ('itemBatChargeCrystal', 'charging_energy_crystal'), ('itemBatChargeLamaCrystal', 'charging_lapotron_crystal'),
                 ('itemArmorNanoHelmet', 'nano_helmet'), ('itemArmorNanoChestplate', 'nano_chestplate'), ('itemArmorNanoLegs', 'nano_leggings'), ('itemArmorNanoBoots', 'nano_boots'),
                 ('itemArmorQuantumHelmet', 'quantum_helmet'), ('itemArmorQuantumChestplate', 'quantum_chestplate'), ('itemArmorQuantumLegs', 'quantum_leggings'), ('itemArmorQuantumBoots', 'quantum_boots'),
                 ('itemNightvisionGoggles', 'nightvision_goggles'), ('itemArmorJetpackElectric', 'jetpack'), ('itemArmorBatpack', 'batpack'), ('itemArmorAdvBatpack', 'adv_batpack'),
                 ('itemArmorEnergypack', 'energy_pack'), ('itemArmorLappack', 'lappack'), ('itemToolMiningLaser', 'mining_laser'), ('itemNanoSaber', 'nano_saber')):
    for _d in (0, 1, 26, 27):
        GC_MAP[('IC2:' + _id, _d)] = 'iis:' + _to
# D119: GT credits (quest store and rewards), machines' own ids
for _i, _c in enumerate(('copper', 'cupronickel', 'silver', 'gold', 'platinum', 'osmium', 'naquadah', 'neutronium')):
    GC_MAP[('gregtech:gt.metaitem.01', 32000 + _i)] = f'iis:credit_{_c}'
GC_MAP.update({('gregtech:gt.metaitem.01', 32762): 'iis:multimeter', ('gregtech:gt.metaitem.01', 32764): 'iis:duct_tape', ('gregtech:gt.metaitem.01', 32021): 'iis:auto_repair_kit',
               ('gregtech:gt.metaitem.03', 32073): 'iis:stem_cell', ('gregtech:gt.blockmachines', 111): 'iis:hatch_maintenance_semiauto', ('gregtech:gt.blockmachines', 114): 'iis:hatch_maintenance_auto',
               ('gregtech:gt.blockmachines', 90): 'iis:hatch_maintenance', ('IC2:itemScrap', 0): 'iis:scrap'})
GC_MAP.update({('IC2:itemArmorHazmatHelmet', 0): 'iis:hazmat_helmet', ('IC2:itemArmorHazmatChestplate', 0): 'iis:hazmat_chestplate', ('IC2:itemArmorHazmatLeggings', 0): 'iis:hazmat_leggings',
               ('IC2:itemArmorRubBoots', 0): 'iis:rubber_boots'})
GC_MAP.update({('IC2:blockElectric', 0): 'iis:batbox', ('IC2:blockElectric', 7): 'iis:cesu', ('IC2:blockElectric', 1): 'iis:mfe', ('IC2:blockElectric', 2): 'iis:mfsu',
               ('IC2:blockElectric', 3): 'iis:transformer_lv', ('IC2:blockElectric', 4): 'iis:transformer_mv', ('IC2:blockElectric', 5): 'iis:transformer_hv', ('IC2:blockElectric', 6): 'iis:transformer_ev',
               ('IC2:itemDust2', 2): 'iis:energium_dust', ('IC2:itemCasing', 0): 'iis:item_casing_copper', ('IC2:itemCasing', 1): 'iis:item_casing_tin', ('IC2:itemCasing', 2): 'iis:item_casing_bronze',
               ('IC2:itemCasing', 3): 'iis:item_casing_gold', ('IC2:itemCasing', 4): 'iis:item_casing_iron', ('IC2:itemCasing', 5): 'iis:steel_item_casing', ('IC2:itemCasing', 6): 'iis:item_casing_lead',
               ('IC2:itemPartCoalBall', 0): 'iis:coal_ball', ('IC2:itemPartCoalBlock', 0): 'iis:compressed_coal_ball', ('IC2:itemPartCoalChunk', 0): 'iis:coal_chunk', ('IC2:itemPartIndustrialDiamond', 0): 'iis:industrial_diamond',
               ('IC2:itemPartCarbonFibre', 0): 'iis:carbon_fiber', ('IC2:itemPartAlloy', 0): 'iis:advanced_alloy_plate', ('IC2:itemIngot', 4): 'iis:mixed_metal_ingot', ('IC2:itemPartIridium', 0): 'iis:iridium_alloy_plate',
               ('IC2:blockAlloyGlass', 0): 'iis:reinforced_glass', ('IC2:blockMiningPipe', 0): 'iis:mining_pipe', ('IC2:blockScaffold', 0): 'minecraft:scaffolding',
               ('gregtech:gt.metaitem.01', 32597): 'iis:energy_orb', ('gregtech:gt.metaitem.01', 32599): 'iis:energy_orb_cluster'})
RAILCRAFT_MAP = {
    ('detector', 0): 'detector_item', ('part.tie', 0): 'tie_wood', ('part.tie', 1): 'tie_stone', ('part.railbed', 0): 'railbed_wood',
    ('part.railbed', 1): 'railbed_stone', ('part.rail', 0): 'rail_standard', ('part.rail', 1): 'rail_advanced', ('part.rail', 2): 'rail_wooden',
    ('part.rail', 3): 'rail_high_speed', ('part.rail', 4): 'rail_reinforced', ('part.rail', 5): 'rail_electric', ('part.rebar', 0): 'rebar',
    ('part.circuit', 0): 'circuit_controller', ('part.circuit', 1): 'circuit_receiver', ('part.circuit', 2): 'circuit_signal',
    ('track', 736): 'track_wooden', ('track', 20176): 'track_locking', ('track', 30516): 'track_locomotive',
    ('cart.loco.steam.solid', 0): 'loco_steam', ('cart.loco.electric', 0): 'loco_electric', ('cart.track.layer', 0): 'track_layer',
    ('cart.bore', 0): 'tunnel_bore', ('borehead.iron', 0): 'borehead_iron', ('borehead.steel', 0): 'borehead_steel',
    ('borehead.diamond', 0): 'borehead_diamond', ('tool.signal.tuner', 0): 'signal_tuner', ('tool.surveyor', 0): 'signal_surveyor',
    ('signal', 3): 'block_signal', ('signal', 8): 'signal_receiver_box', ('signal', 9): 'signal_controller_box',
    ('signal', 2): 'switch_motor', ('signal', 4): 'switch_lever', ('part.signal.lamp', 0): 'signal_lamp', ('armor.goggles', 0): 'trackman_goggles',
    ('machine.gamma', 0): 'item_loader', ('machine.gamma', 1): 'item_unloader', ('machine.gamma', 2): 'item_loader_advanced',
    ('machine.gamma', 3): 'item_unloader_advanced', ('machine.epsilon', 0): 'electric_feeder', ('tool.crowbar', 0): 'crowbar_red',
    ('tool.crowbar.reinforced', 0): 'crowbar_reinforced',
    # the IC2 MFE cart is not ported: the electric locomotive takes an MV 2x2 battery buffer instead (PORTING_NOTES, D102)
    ('cart.energy.mfe', 0): 'battery_buffer_2x2_mv',
}
GT_TOOLS = {100: 'drill_lv', 110: 'chainsaw_lv', 120: 'wrench_lv', 0: 'sword', 2: 'pickaxe', 4: 'shovel', 6: 'axe', 8: 'hoe', 10: 'saw', 12: 'hammer', 14: 'soft_hammer', 16: 'wrench',
            18: 'file', 20: 'crowbar', 22: 'screwdriver', 24: 'mortar', 26: 'wire_cutter', 34: 'knife'}
TIERS = {'00': 'ulv', '09': 'max', '01': 'lv', '02': 'mv', '03': 'hv', '04': 'ev', '05': 'iv', '06': 'luv', '07': 'zpm', '08': 'uv'}
# GT_Loader_MetaTileEntities:1468-1510 makeWires: start + 0..5 wires 1x..16x, + 6..10 cables 1x..12x
GT_WIRES = {2000: 'red_alloy', 1200: 'cobalt', 1220: 'lead', 1240: 'tin', 1260: 'zinc', 1280: 'soldering_alloy', 1300: 'iron', 1320: 'nickel', 1340: 'cupronickel', 1360: 'copper', 1380: 'annealed_copper',
            1400: 'kanthal', 1420: 'gold', 1440: 'electrum', 1460: 'silver', 1480: 'blue_alloy', 1500: 'nichrome', 1520: 'steel', 1540: 'black_steel', 1560: 'titanium', 1580: 'aluminium', 1600: 'graphene',
            1620: 'osmium', 1640: 'platinum', 1660: 'tungsten_steel', 1680: 'tungsten', 1700: 'hssg', 1720: 'niobium_titanium', 1740: 'vanadium_gallium', 1760: 'yttrium_barium_cuprate', 1780: 'naquadah',
            1800: 'naquadah_alloy', 1820: 'duranium', 2020: 'superconductor'}
WIRE_FORMS = ('wire_{}_1x', 'wire_{}_2x', 'wire_{}_4x', 'wire_{}_8x', 'wire_{}_12x', 'wire_{}_16x', 'cable_{}_1x', 'cable_{}_2x', 'cable_{}_4x', 'cable_{}_8x', 'cable_{}_12x')
# :1540-1579 fluid pipes: start + 0..4 tiny, small, medium, large, huge
GT_PIPES = {5100: 'wood', 5110: 'copper', 5120: 'bronze', 5130: 'steel', 5140: 'stainless_steel', 5150: 'titanium', 5160: 'tungsten_steel', 5170: 'plastic', 5175: 'polytetrafluoroethylene'}
PIPE_SIZES = ('tiny', 'small', 'medium', 'large', 'huge')
# :1620-1628 item pipes (D123): start + 0..2 medium, large, huge, + 3..5 restrictive
GT_ITEM_PIPES = {5602: 'brass', 5612: 'electrum', 5622: 'platinum', 5632: 'osmium', 5640: 'polyvinyl_chloride', 5646: 'wrought_iron', 5652: 'nickel', 5658: 'cobalt', 5664: 'aluminium'}
ITEM_PIPE_FORMS = ('item_pipe_medium_{}', 'item_pipe_large_{}', 'item_pipe_huge_{}', 'item_pipe_restrictive_medium_{}', 'item_pipe_restrictive_large_{}', 'item_pipe_restrictive_huge_{}')
# GT_MetaGenerated_Item_01 and _02: damage // 1000 is the prefix, damage % 1000 the material
META01_FORMS = {0: 'tiny_dust', 1: 'small_dust', 2: 'dust', 3: 'impure_dust', 4: 'pure_dust', 5: 'crushed', 6: 'purified', 7: 'centrifuged', 8: 'gem', 9: 'nugget', 11: 'ingot', 12: 'hot_ingot',
                17: 'plate', 18: 'double_plate', 23: 'rod', 24: 'lens', 25: 'round', 26: 'bolt', 27: 'screw', 28: 'ring', 29: 'foil'}
META02_FORMS = {0: 'sword_head', 1: 'pickaxe_head', 2: 'shovel_head', 3: 'axe_head', 4: 'hoe_head', 7: 'saw_head', 8: 'drill_head', 9: 'chainsaw_head', 10: 'wrench_head', 22: 'long_rod', 31: 'small_gear'}
VANILLA_FORMS = {('ingot', 'iron'): 'iron_ingot', ('ingot', 'gold'): 'gold_ingot', ('ingot', 'copper'): 'copper_ingot', ('nugget', 'iron'): 'iron_nugget', ('nugget', 'gold'): 'gold_nugget',
                 ('nugget', 'copper'): 'copper_nugget', ('gem', 'diamond'): 'diamond', ('gem', 'emerald'): 'emerald', ('gem', 'coal'): 'coal', ('gem', 'charcoal'): 'charcoal', ('gem', 'quartz'): 'quartz',
                 ('dust', 'redstone'): 'redstone', ('dust', 'glowstone'): 'glowstone_dust', ('dust', 'gunpowder'): 'gunpowder', ('dust', 'blaze'): 'blaze_powder', ('dust', 'sugar'): 'sugar'}
# OreDict prefixes (GT OrePrefixes names) -> our item id prefix
OREDICT_PREFIXES = ['ingot', 'dust', 'plate', 'gem', 'nugget', 'block', 'gear', 'stick', 'ring', 'bolt', 'screw', 'wire', 'cable', 'ore']
OREDICT_OUR = {'stick': 'rod'}


def load_aeio():
    import importlib.util
    spec = importlib.util.spec_from_file_location('iis_gen_aeio', os.path.join(ROOT, 'tools', 'gen', 'aeio.py'))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class Mapper:
    def __init__(self):
        self.aeio = load_aeio()
        ae = json.load(open(os.path.join(ROOT, 'reference', 'original', 'aeio_mods.json'), encoding='utf-8'))
        self.mod_items = {'ae2': set(ae['ae2']['items']), 'enderio': set(ae['enderio']['items'])}
        self.gtl = gt_lang()
        self.mte = mte_names()
        self.ours = {f[:-5] for f in os.listdir(os.path.join(GEN, 'items')) if f.endswith('.json')}
        self.vanilla, vru = vanilla_items_and_names()
        lang = json.load(open(os.path.join(GEN, 'lang', 'ru_ru.json'), encoding='utf-8'))
        self.by_name = collections.defaultdict(list)
        for k, v in lang.items():
            parts = k.split('.')
            if len(parts) == 3 and parts[0] in ('item', 'block') and parts[1] == NS and parts[2] in self.ours:
                self.by_name[v].append(f'{NS}:{parts[2]}')
        for k, v in vru.items():
            parts = k.split('.')
            if len(parts) == 3 and parts[0] in ('item', 'block') and parts[1] == 'minecraft' and parts[2] in self.vanilla:
                self.by_name.setdefault(v, []).append(f'minecraft:{parts[2]}')
        self.tools = collections.defaultdict(list)
        for t in json.load(open(os.path.join(ROOT, 'src', 'main', 'resources', NS, 'tools.json'), encoding='utf-8')):
            self.tools[t['tool']].append(t['id'])
        self.fluids = {k.split('.', 2)[2] for k in lang if k.startswith(f'fluid_type.{NS}.')}
        # GT material id -> our material id
        ours_mats = {x['material'] for x in json.load(open(os.path.join(ROOT, 'src', 'main', 'resources', NS, 'items.json'), encoding='utf-8')) if x.get('material')}
        norm = {m.replace('_', ''): m for m in ours_mats}
        self.gt_mat = {}
        for m in json.load(open(os.path.join(ROOT, 'reference', 'original', 'gt5_materials.json'), encoding='utf-8')):
            key = m['name'].replace('_', '').lower()
            self.gt_mat[m['id']] = norm.get(key, key)

    @staticmethod
    def pick(cands):
        # our own item first, then vanilla; among equals the shortest id (stone ore before deepslate_ore, ...)
        return sorted(set(cands), key=lambda c: (not c.startswith(NS + ':'), len(c), c))[0]

    def item(self, iid, dmg, oredict=''):
        mod, _, name = iid.partition(':')
        r = GC_MAP.get((iid, dmg))
        if r:
            return r
        if mod == 'minecraft':
            r = MC_RENAMES.get((name, dmg))
            if r is None and dmg in (0, 32767) and name in self.vanilla:
                r = name
            r = f'minecraft:{r}' if r else None
        elif mod == 'IC2':
            r = IC2_MAP.get((name, dmg))
        elif mod == 'Railcraft':
            r = RAILCRAFT_MAP.get((name, dmg))
            r = f'{NS}:{r}' if r else None
        elif mod == 'gregtech':
            r = self.gregtech(name, dmg)
        elif mod in ('appliedenergistics2', 'EnderIO', 'IronChest', 'ChickenChunks', 'betterbuilderswands', 'chisel', 'extracells'):
            r = self.aeio.quest_item(mod, name, dmg)
        if r is None and oredict:
            r = self.oredict(oredict)
        if r is None:
            return None
        ns, _, path = r.partition(':')
        ok = path in self.ours if ns == NS else (path in self.mod_items[ns] if ns in self.mod_items else ns in ('ironchest', 'chunkloaders', 'constructionwand', 'chisel') or path in self.vanilla)
        return r if ok else None

    def structural(self, name, dmg):
        """Ids the GT loader makes in loops: wires, cables, frames, fluid pipes, material items (D119)."""
        if name == 'gt.integrated_circuit':
            return f'{NS}:programmed_circuit'
        if name == 'gt.metatool.01' and dmg == 160:
            return f'{NS}:soldering_iron_lv'
        if name == 'gt.blockmachines':
            for base, mat in GT_WIRES.items():
                if base <= dmg <= base + 10:
                    return f'{NS}:' + WIRE_FORMS[dmg - base].format(mat)
            if 4096 <= dmg < 5096 and self.gt_mat.get(dmg - 4096):
                return f'{NS}:frame_{self.gt_mat[dmg - 4096]}'
            for base, mat in GT_ITEM_PIPES.items():
                if base <= dmg < base + 6:
                    return f'{NS}:' + ITEM_PIPE_FORMS[dmg - base].format(mat)
            for base, mat in GT_PIPES.items():
                if base <= dmg < base + 5:
                    return f'{NS}:pipe_{PIPE_SIZES[dmg - base]}_{mat}'
            return None
        forms = {'gt.metaitem.01': META01_FORMS, 'gt.metaitem.02': META02_FORMS}.get(name)
        if forms and dmg < 32000 and dmg // 1000 in forms:
            form, mat = forms[dmg // 1000], self.gt_mat.get(dmg % 1000)
            if not mat:
                return None
            if f'{form}_{mat}' in self.ours:
                return f'{NS}:{form}_{mat}'
            if (form, mat) in VANILLA_FORMS:
                return 'minecraft:' + VANILLA_FORMS[(form, mat)]
        return None

    def gregtech(self, name, dmg):
        r = self.structural(name, dmg)
        if r:
            return r
        if name == 'gt.metatool.01':
            kind = GT_TOOLS.get(dmg)
            ids = self.tools.get(kind, [])
            if not ids:
                return None
            pref = [i for i in ids if i.endswith('_iron')] or [i for i in ids if i.endswith('_steel')] or ids
            return f'{NS}:{pref[0]}'
        if name == 'gt.blockmachines':
            key = self.mte.get(dmg)
            if key is None:
                return None
            m = re.fullmatch(r'batterybuffer\.(\d\d)\.tier\.(\d\d)', key)
            if m and TIERS.get(m.group(2)):
                k = {1: 1, 4: 2, 9: 3, 16: 4}.get(int(m.group(1)))
                return f'{NS}:battery_buffer_{k}x{k}_{TIERS[m.group(2)]}' if k else None
            label = self.gtl.get(f'gt.blockmachines.{key}.name')
        else:
            label = self.gtl.get(f'{name}.{dmg}.name')
        if not label or label not in self.by_name:
            return None
        return self.pick(self.by_name[label])

    def oredict(self, od):
        for p in OREDICT_PREFIXES:
            if od.startswith(p) and len(od) > len(p) and od[len(p)].isupper():
                mat = re.sub(r'(?<!^)(?=[A-Z])', '_', od[len(p):]).lower()
                cand = f'{OREDICT_OUR.get(p, p)}_{mat}'
                if cand in self.ours:
                    return f'{NS}:{cand}'
                for v in (f'{mat}_{p}', f'{mat}_{OREDICT_OUR.get(p, p)}'):
                    if v in self.vanilla:
                        return f'minecraft:{v}'
        return None

    def fluid(self, name):
        if name in ('water', 'lava'):
            return f'minecraft:{name}'
        for c in (name, name.replace('liquid_', ''), name.replace('_gt5u', ''), name.replace('.', '_'), name.replace('ic2', '')):
            if c in self.fluids:
                return f'{NS}:{c}'
        return None


def hexid(kind, n):
    # FTB Quests object ids: 64-bit, written as %016X; the high byte keeps kinds apart
    return f'{(kind << 56) | n:016X}'


def bq_items(t):
    return [it for _, it in sorted(t.get('requiredItems:9', {}).items(), key=lambda e: int(e[0].split(':')[0]))]


def main():
    mp = Mapper()
    bq = json.load(open(os.path.join(INST, 'config', 'betterquesting', 'DefaultQuests.json'), encoding='utf-8'))
    lang_dir = os.path.join(INST, 'config', 'betterquesting', 'resources', 'iia', 'lang')
    langs = {'ru_ru': read_lang(os.path.join(lang_dir, 'ru_RU.lang')), 'en_us': read_lang(os.path.join(lang_dir, 'en_US.lang'))}
    quests = {q['questID:3']: q for q in bq['questDatabase:9'].values()}

    missing_items, missing_fluids, dropped_rewards = collections.Counter(), collections.Counter(), collections.Counter()
    built, hidden = {}, {}
    for qid, q in sorted(quests.items()):
        props = q['properties:10']['betterquesting:10']
        tasks, reason = [], None
        for ti, (_, t) in enumerate(sorted(q.get('tasks:9', {}).items(), key=lambda e: int(e[0].split(':')[0]))):
            tid = t['taskID:8']
            if tid == 'bq_standard:retrieval':
                for it in bq_items(t):
                    m = mp.item(it['id:8'], it.get('Damage:2', 0), it.get('OreDict:8', ''))
                    if m is None:
                        missing_items[(it['id:8'], it.get('Damage:2', 0))] += 1
                        reason = reason or f"{it['id:8']}:{it.get('Damage:2', 0)}"
                        continue
                    task = {'type': 'item', 'item': {'id': m, 'count': 1}, 'count': max(1, it.get('Count:3', 1))}
                    if t.get('consume:1', 0):
                        task['consume_items'] = True
                    tasks.append(task)
            elif tid == 'bq_standard:fluid':
                for fl in t.get('requiredFluids:9', {}).values():
                    m = mp.fluid(fl.get('FluidName:8', ''))
                    if m is None:
                        missing_fluids[fl.get('FluidName:8')] += 1
                        reason = reason or f"fluid {fl.get('FluidName:8')}"
                        continue
                    # ftblibrary FluidStack.MAP_CODEC: {fluid, amount}
                    tasks.append({'type': 'fluid', 'fluid': {'fluid': m, 'amount': max(1, fl.get('Amount:3', 1000))}})
            elif tid == 'bq_standard:checkbox':
                tasks.append({'type': 'checkmark'})
            elif tid == 'bq_standard:hunt':
                ent = {'Creeper': 'minecraft:creeper', 'Zombie': 'minecraft:zombie', 'Skeleton': 'minecraft:skeleton',
                       'Spider': 'minecraft:spider', 'Enderman': 'minecraft:enderman'}.get(t.get('target:8'))
                if ent is None:
                    reason = reason or f"hunt {t.get('target:8')}"
                    continue
                tasks.append({'type': 'kill', 'entity': ent, 'value': t.get('required:3', 1)})
            else:
                reason = reason or tid
        if reason or not tasks:
            hidden[qid] = reason or 'no tasks'
            continue
        rewards = []
        for _, r in sorted(q.get('rewards:9', {}).items(), key=lambda e: int(e[0].split(':')[0])):
            if r['rewardID:8'] != 'bq_standard:item':
                dropped_rewards[r['rewardID:8']] += 1
                continue
            for it in [v for _, v in sorted(r.get('rewards:9', {}).items(), key=lambda e: int(e[0].split(':')[0]))]:
                m = mp.item(it['id:8'], it.get('Damage:2', 0), it.get('OreDict:8', ''))
                if m is None:
                    dropped_rewards[(it['id:8'], it.get('Damage:2', 0))] += 1
                    continue
                rewards.append({'type': 'item', 'item': {'id': m, 'count': max(1, it.get('Count:3', 1))}})
        icon = props.get('icon:10', {})
        icon_id = mp.item(icon.get('id:8', ''), icon.get('Damage:2', 0), icon.get('OreDict:8', '')) if icon else None
        built[qid] = {'props': props, 'tasks': tasks, 'rewards': rewards, 'icon': icon_id,
                      'deps': list(q.get('preRequisites:11', [])), 'or': props.get('questLogic:8') == 'OR'}

    # dependencies through hidden quests: a hidden quest is replaced by its own (visible) dependencies
    memo = {}

    def visible_deps(qid, seen=()):
        if qid in memo:
            return memo[qid]
        out = []
        for d in quests.get(qid, {}).get('preRequisites:11', []):
            if d in seen:
                continue
            if d in built:
                out.append(d)
            elif d in quests:
                out += visible_deps(d, seen + (qid,))
        memo[qid] = list(dict.fromkeys(out))
        return memo[qid]

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(os.path.join(OUT, 'chapters'))
    tr = {k: {} for k in langs}

    def text(key_base, bq_key, kind):
        for loc, table in langs.items():
            v = table.get(bq_key)
            if v is None:
                continue
            if kind == 'title':
                tr[loc][f'{key_base}.title'] = v
            else:
                lines = v.replace('\\n', '\n').split('\n')
                tr[loc][f'{key_base}.quest_desc'] = lines

    placed = set()
    chapters = 0
    lines = sorted(bq['questLines:9'].values(), key=lambda l: l.get('lineID:3', 0))
    order = [int(k.split(':')[0]) for k in bq['questLines:9']]
    lines = [bq['questLines:9'][k] for k in sorted(bq['questLines:9'], key=lambda k: int(k.split(':')[0]))]
    for idx, line in enumerate(lines):
        lid = line.get('lineID:3', idx)
        lp = line['properties:10']['betterquesting:10']
        entries = [e for _, e in sorted(line.get('quests:9', {}).items(), key=lambda e: int(e[0].split(':')[0]))]
        cq, links = [], []
        for e in entries:
            qid = e['id:3']
            if qid not in built:
                continue
            b = built[qid]
            sx = e.get('sizeX:3', 24) / 24.0
            x = (e['x:3'] + e.get('sizeX:3', 24) / 2) / 24.0
            y = (e['y:3'] + e.get('sizeY:3', 24) / 2) / 24.0
            if qid in placed:
                links.append({'id': hexid(0x1E, (lid << 20) | qid), 'linked_quest': hexid(0x1A, qid), 'x': x, 'y': y, 'size': sx})
                continue
            placed.add(qid)
            qo = {'id': hexid(0x1A, qid), 'x': x, 'y': y}
            if abs(sx - 1.0) > 1e-6:
                qo['size'] = sx
            deps = visible_deps(qid)
            if deps:
                qo['dependencies'] = [hexid(0x1A, d) for d in deps]
                if b['or'] and len(deps) > 1:
                    qo['dependency_requirement'] = 'one_completed'
            if b['props'].get('visibility:8') in ('NORMAL', 'CHAIN'):
                qo['hide_until_deps_visible'] = True
            if b['icon']:
                qo['icon'] = {'id': b['icon'], 'count': 1}
            qo['tasks'] = [dict(t, id=hexid(0x1B, (qid << 8) | i)) for i, t in enumerate(b['tasks'])]
            if b['rewards']:
                qo['rewards'] = [dict(r, id=hexid(0x1C, (qid << 8) | i)) for i, r in enumerate(b['rewards'])]
            text(f'quest.{qo["id"]}', b['props'].get('name:8', ''), 'title')
            text(f'quest.{qo["id"]}', b['props'].get('desc:8', ''), 'desc')
            cq.append(qo)
        if not cq and not links:
            continue
        cid = hexid(0x1D, lid)
        fname = f'{idx:02d}_' + re.sub(r'[^a-z0-9_]+', '_', lp.get('name:8', f'line_{lid}').replace('bq.questline.', '').replace('.name', '').lower()).strip('_')
        ch = {'id': cid, 'filename': fname, 'order_index': idx, 'default_quest_shape': '', 'quests': cq}
        if links:
            ch['quest_links'] = links
        icon = lp.get('icon:10', {})
        icon_id = mp.item(icon.get('id:8', ''), icon.get('Damage:2', 0), icon.get('OreDict:8', '')) if icon else None
        if icon_id:
            ch['icon'] = {'id': icon_id, 'count': 1}
        text(f'chapter.{cid}', lp.get('name:8', ''), 'title')
        write(os.path.join(OUT, 'chapters', fname + '.json5'), ch)
        chapters += 1
    # BaseQuestFile#readData: these keys are required (orElseThrow) in file version 13
    write(os.path.join(OUT, 'data.json5'), {'version': 13, 'default_reward_team': False, 'default_consume_items': False,
                                            'default_autoclaim_rewards': 'disabled', 'default_quest_shape': 'circle',
                                            'default_quest_disable_jei': False, 'drop_loot_crates': False,
                                            'fallback_locale': 'en_us', 'grid_scale': 0.5})
    # TranslationManager#loadFromFile: a language is a folder lang/<locale>/ (every .json5 inside is read)
    for loc, table in tr.items():
        os.makedirs(os.path.join(OUT, 'lang', loc), exist_ok=True)
        write(os.path.join(OUT, 'lang', loc, 'iis1_quests.json5'), dict(sorted(table.items())))

    rep = [f'# IIS 1 quests -> FTB Quests (D101)', '',
           f'Quests in IIS 1: {len(quests)}; written: {len(placed)}; hidden until their items are ported: {len(hidden)}; chapters: {chapters}.', '',
           '## Most needed unported items (task count)', '']
    for (i, d), c in missing_items.most_common(80):
        label = mp.gtl.get(f'gt.blockmachines.{mp.mte.get(d)}.name') if i == 'gregtech:gt.blockmachines' else mp.gtl.get(f'{i.split(":", 1)[1]}.{d}.name', '')
        rep.append(f'- {c} × `{i}:{d}` {label or ""}')
    rep += ['', '## Unported fluids', ''] + [f'- {c} × `{f}`' for f, c in missing_fluids.most_common()]
    rep += ['', '## Dropped reward items', ''] + [f'- {c} × `{k}`' for k, c in dropped_rewards.most_common(30)]
    with open(os.path.join(ROOT, 'quests', 'REPORT.md'), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('\n'.join(rep) + '\n')
    print(f'quests: {len(quests)} total, {len(placed)} written, {len(hidden)} hidden, {chapters} chapters')


def write(path, obj):
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=2)
        fh.write('\n')


if __name__ == '__main__':
    main()
