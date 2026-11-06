"""AE2 and Ender IO on top of IIS 2 (D125): the ready mods stay, the rules of IIS 1 are laid over them.

Owner's decision: take the ready mods for 26.1.2 and keep to IIS 1. Sources:
  scripts/AE2.zs, scripts/EnderIO.zs of the IIS 1.19.1 pack: read and translated here, not retyped;
  config/AppliedEnergistics2/AppliedEnergistics2.cfg: which AE2 features were off;
  GT5-II @00021e3: the AE2 chain inside GT (ProcessingCrafting, ProcessingGem, GT_MachineRecipeLoader) and the Ender alloys;
  reference/original/aeio_mods.json: items and recipes of the ready mods (tools/extract/aeio_mods.py).
What it writes:
  data/iis/recipe/aeio/...: recipes of IIS 1, each with a neoforge:mod_loaded condition, so IIS 2 still loads without the mods;
  packs/iis_overrides/data/{ae2,enderio}/recipe/...: switched-off recipes of the mods;
  c:hidden_from_recipe_viewers: what IIS 1 hid with NEI.hide, what it had off and what the old mods did not have;
  quests/AEIO_REPORT.md: everything that could not be carried over, by reason.
"""
import collections
import json
import os
import random
import re

from PIL import Image

NS = 'iis'
INST = os.environ.get('IIS1_INSTANCE', 'C:/Users/Godkiller/AppData/Roaming/forServerClient')
SEEDS = {0: 'certus', 600: 'nether', 1200: 'fluix'}
COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']

# appeng.items.materials.MaterialType; None: the modern mod has no such thing
AE_MM = {0: 'certus_quartz_crystal', 1: 'charged_certus_quartz_crystal', 2: 'certus_quartz_dust', 5: 'silicon', 6: 'matter_ball', 7: 'fluix_crystal', 8: 'fluix_dust', 9: 'fluix_pearl',
         10: f'{NS}:crystal_pure_certus', 11: f'{NS}:crystal_pure_nether', 12: f'{NS}:crystal_pure_fluix',
         13: 'calculation_processor_press', 14: 'engineering_processor_press', 15: 'logic_processor_press', 16: 'printed_calculation_processor',
         17: 'printed_engineering_processor', 18: 'printed_logic_processor', 19: 'silicon_press', 20: 'printed_silicon', 21: 'name_press', 22: 'logic_processor',
         23: 'calculation_processor', 24: 'engineering_processor', 25: 'basic_card', 26: 'redstone_card', 27: 'capacity_card', 28: 'advanced_card', 29: 'fuzzy_card',
         30: 'speed_card', 31: 'inverter_card', 32: 'spatial_cell_component_2', 33: 'spatial_cell_component_16', 34: 'spatial_cell_component_128',
         35: 'cell_component_1k', 36: 'cell_component_4k', 37: 'cell_component_16k', 38: 'cell_component_64k', 39: 'item_cell_housing', 41: 'wireless_receiver',
         42: 'wireless_booster', 43: 'formation_core', 44: 'annihilation_core', 45: 'sky_dust', 46: 'ender_dust', 47: 'singularity', 48: 'quantum_entangled_singularity', 52: 'blank_pattern', 53: 'crafting_card', 54: None, 55: None}
# appeng.items.parts.PartType
AE_PART = {16: 'fluix_glass_cable', 36: 'fluix_covered_cable', 56: 'fluix_smart_cable', 76: 'fluix_smart_dense_cable', 80: 'toggle_bus', 100: 'inverted_toggle_bus',
           120: 'cable_anchor', 140: 'quartz_fiber', 160: 'monitor', 180: 'semi_dark_monitor', 200: 'dark_monitor', 220: 'storage_bus', 240: 'import_bus', 260: 'export_bus',
           280: 'level_emitter', 300: 'annihilation_plane', 320: 'formation_plane', 340: 'pattern_encoding_terminal', 360: 'crafting_terminal', 380: 'terminal',
           400: 'storage_monitor', 420: 'conversion_monitor', 440: 'cable_interface', 460: 'me_p2p_tunnel', 480: 'pattern_access_terminal', 500: None}
for base, form in ((0, 'fluix_glass_cable'), (20, 'fluix_covered_cable'), (40, 'fluix_smart_cable'), (60, 'fluix_smart_dense_cable')):
    for i, c in enumerate(COLORS):
        AE_PART[base + i] = form.replace('fluix', c)
AE_NAMED = {'tile.BlockController': 'controller', 'tile.BlockEnergyCell': 'energy_cell', 'tile.BlockDenseEnergyCell': 'dense_energy_cell', 'tile.BlockChest': 'me_chest',
            'tile.BlockDrive': 'drive', 'tile.BlockCraftingUnit': 'crafting_unit', 'tile.BlockCraftingUnit:1': 'crafting_accelerator', 'tile.BlockMolecularAssembler': 'molecular_assembler',
            'tile.BlockQuantumRing': 'quantum_ring', 'tile.BlockQuantumLinkChamber': 'quantum_link', 'tile.BlockSpatialPylon': 'spatial_pylon', 'tile.BlockSpatialIOPort': 'spatial_io_port',
            'tile.BlockInterface': 'interface', 'tile.BlockCellWorkbench': 'cell_workbench', 'tile.BlockIOPort': 'io_port', 'tile.BlockCondenser': 'condenser',
            'tile.BlockEnergyAcceptor': 'energy_acceptor', 'tile.BlockSecurity': None, 'tile.BlockFluix': 'fluix_block', 'tile.BlockQuartz': 'quartz_block', 'tile.BlockQuartzChiseled': 'chiseled_quartz_block',
            'tile.BlockQuartzGlass': 'quartz_glass', 'tile.BlockQuartzLamp': 'quartz_vibrant_glass', 'tile.BlockQuartzTorch': 'quartz_fixture', 'tile.BlockLightDetector': 'light_detector',
            'tile.BlockTinyTNT': 'tiny_tnt', 'tile.BlockQuartzGrowthAccelerator': 'growth_accelerator', 'tile.BlockCharger': 'charger', 'tile.BlockInscriber': 'inscriber', 'tile.BlockGrinder': None,
            'tile.BlockCrank': 'crank', 'tile.OreQuartz': None, 'tile.OreQuartzCharged': None, 'item.ToolMemoryCard': 'memory_card', 'item.ToolNetworkTool': 'network_tool', 'item.ToolColorApplicator': 'color_applicator',
            'item.ToolMassCannon': 'matter_cannon', 'item.ToolPortableCell': 'portable_item_cell_1k', 'item.ToolWirelessTerminal': 'wireless_terminal', 'item.ToolBiometricCard': None,
            'item.ToolChargedStaff': 'charged_staff', 'item.ToolEntropyManipulator': 'entropy_manipulator', 'item.ToolCertusQuartzCuttingKnife': 'certus_quartz_cutting_knife', 'item.ItemViewCell': 'view_cell',
            'item.ItemSpatialStorageCell.2Cubed': 'spatial_storage_cell_2', 'item.ItemSpatialStorageCell.16Cubed': 'spatial_storage_cell_16', 'item.ItemSpatialStorageCell.128Cubed': 'spatial_storage_cell_128',
            'item.ItemBasicStorageCell.1k': 'item_storage_cell_1k', 'item.ItemBasicStorageCell.4k': 'item_storage_cell_4k', 'item.ItemBasicStorageCell.16k': 'item_storage_cell_16k', 'item.ItemBasicStorageCell.64k': 'item_storage_cell_64k',
            'tile.ChiseledQuartzSlabBlock': 'chiseled_quartz_slab', 'tile.QuartzPillarSlabBlock': 'quartz_pillar_slab', 'tile.QuartzSlabBlock': 'quartz_slab',
            'tile.ChiseledQuartzSlabBlock.double': 'chiseled_quartz_block', 'tile.QuartzPillarSlabBlock.double': 'quartz_pillar'}
# Old Ender IO -> new one. A conduit is one item with a component; None: the new mod has no such thing
EIO = {'blockFusedQuartz': 'fused_quartz', 'blockFusedQuartz:1': 'clear_glass', 'blockFusedQuartz:2': 'fused_quartz_e', 'blockFusedQuartz:3': 'clear_glass_e', 'itemMaterial': 'silicon',
       'itemMaterial:1': 'conduit_binder', 'itemMaterial:2': 'conduit_binder_composite', 'itemMaterial:3': 'pulsating_alloy_nugget', 'itemMaterial:4': 'vibrant_alloy_nugget',
       'itemMaterial:5': 'pulsating_crystal', 'itemMaterial:6': 'vibrant_crystal', 'itemMaterial:7': 'dark_steel_grinding_ball', 'itemMachinePart': 'void_chassis', 'itemMachinePart:1': 'iron_gear',
       'itemBasicCapacitor': 'basic_capacitor', 'itemBasicCapacitor:1': 'double_layer_capacitor', 'itemBasicCapacitor:2': 'octadic_capacitor', 'blockDarkIronBars': 'dark_steel_bars',
       'blockDarkSteelPressurePlate': 'dark_steel_pressure_plate', 'blockVacuumChest': 'vacuum_chest', 'itemConduitProbe': 'conduit_probe', 'itemYetaWrench': 'yeta_wrench', 'blockTravelAnchor': 'travel_anchor',
       'itemMagnet:16': 'electromagnet', 'itemXpTransfer': 'experience_rod', 'blockPoweredSpawner': 'powered_spawner', 'itemBasicFilterUpgrade': 'basic_item_filter', 'itemBasicFilterUpgrade:1': 'advanced_item_filter',
       'itemSoulVessel': 'soul_vial', 'blockSliceAndSplice': 'slice_and_splice', 'blockSoulBinder': 'soul_binder', 'blockPainter': 'painting_machine', 'blockCrafter': 'crafter', 'blockFarmStation': 'farming_station',
       'blockExperienceObelisk': 'xp_obelisk', 'blockAttractor': 'attractor_obelisk', 'blockReinforcedObsidian': 'reinforced_obsidian_block', 'itemGliderWing': 'glider_wing', 'itemGliderWing:1': 'glider',
       'itemTravelStaff:16': 'staff_of_travelling', 'blockKillerJoe': None, 'blockAlloySmelter': 'alloy_smelter', 'blockSagMill': 'sag_mill', 'blockVat': 'vat', 'blockWirelessCharger': 'wireless_charger',
       'blockZombieGenerator': None, 'blockStirlingGenerator': 'stirling_generator', 'blockCombustionGenerator': None, 'blockWeatherObelisk': 'weather_obelisk', 'blockTelePad': None, 'itemCoordSelector': 'coordinate_selector',
       'blockTank': 'fluid_tank', 'blockTank:1': 'pressurized_fluid_tank', 'blockEnchanter': 'enchanter', 'blockEnderRail': None, 'blockEnderIo': 'enderface', 'blockTransceiver': None, 'blockPowerMonitor': None,
       'blockSolarPanel': 'energetic_photovoltaic_module', 'blockSolarPanel:1': 'pulsating_photovoltaic_module', 'itemEnderFood': None, 'blockSpawnGuard': 'aversion_obelisk', 'blockBuffer': None, 'blockBuffer:1': None, 'blockBuffer:2': None, 'blockBuffer:3': None,
       'blockCapBank:1': None, 'blockCapBank:2': None, 'blockCapBank:3': None, 'blockElectricLight': None, 'blockElectricLight:2': None, 'blockInventoryPanel': None, 'blockReservoir': None,
       'itemFunctionUpgrade': None, 'itemExtractSpeedUpgrade': None, 'itemExtractSpeedUpgrade:1': None, 'itemPowderIngot': None, 'itemMEConduit': None, 'itemMEConduit:1': None}
# The new mod has one redstone conduit: the old insulated one (:2) becomes it, the old switch (:1) is gone
CONDUITS = {'itemPowerConduit': 'energy', 'itemPowerConduit:1': 'energetic_energy', 'itemPowerConduit:2': 'vibrant_energy', 'itemItemConduit': 'item', 'itemLiquidConduit': 'fluid',
            'itemLiquidConduit:1': 'energetic_fluid', 'itemLiquidConduit:2': 'vibrant_fluid', 'itemRedstoneConduit': 'redstone', 'itemRedstoneConduit:1': None, 'itemRedstoneConduit:2': 'redstone'}
# IIS 1 config (AppliedEnergistics2.cfg, features): off there, and things the old mod did not have, off here
AE_OFF = ('nether_quartz_', 'certus_quartz_axe', 'certus_quartz_hoe', 'certus_quartz_pickaxe', 'certus_quartz_shovel', 'certus_quartz_sword', 'certus_quartz_wrench',
          'meteorite_compass', 'vibration_chamber', 'crystal_resonance_generator', 'budding_quartz', 'quartz_bud', 'quartz_cluster', 'quartz_ore', 'mysterious_cube', 'debug_', '256k', 'name_press')
# Ender IO: only what IIS 1 kept. Filters, facades and the small things that belong to conduits are not in EnderIO.zs: they were never touched there
EIO_KEEP_OLD = ('filter', 'facade', 'silent_', 'dark_steel_ladder', 'dark_steel_door', 'soul_chain', 'block_detector', 'resetting_lever', 'location_printout', 'painted_')
TOOLS = {'craftingToolFile': 'file', 'craftingToolHardHammer': 'hammer', 'craftingToolSaw': 'saw', 'craftingToolScrewdriver': 'screwdriver', 'craftingToolSoftHammer': 'soft_hammer', 'craftingToolWrench': 'wrench'}
ORE_PREFIX = {'plate': 'plate', 'stick': 'rod', 'screw': 'screw', 'ingot': 'ingot', 'dust': 'dust', 'gem': 'gem', 'nugget': 'nugget', 'frameGt': 'frame'}
GT_MANUAL = {'gt.metaitem.01:17523': ('plate', 'Quartzite'), 'gt.metaitem.01:23523': ('rod', 'Quartzite'), 'gt.metaitem.01:2321': ('dust', 'Enderium'), 'gt.metaitem.01:23364': ('rod', 'DarkSteel')}
GT_ITEMS = {'gt.blockcasings:2': 'casing_mv', 'gt.blockmachines:12': 'hull_mv', 'gt.blockmachines:13': 'hull_hv', 'gt.blockmachines:1380': 'wire_annealed_copper_1x', 'gt.blockmachines:194': 'battery_buffer_4x4_ev',
            'gt.blockmachines:212': 'assembler_mv', 'gt.blockmachines:372': 'electrolyzer_mv', 'gt.blockmachines:42': 'hatch_energy_mv', 'gt.blockmachines:5122': 'pipe_medium_bronze', 'gt.blockmachines:5132': 'pipe_medium_steel',
            'gt.blockmachines:5142': 'pipe_medium_stainless_steel', 'gt.blockmachines:5170': 'pipe_tiny_plastic', 'gt.blockmachines:5612': 'item_pipe_medium_electrum', 'gt.metaitem.01:1633': 'small_dust_polydimethylsiloxane',
            'gt.metaitem.01:1649': 'small_dust_polyvinyl_chloride', 'gt.metaitem.01:17020': 'plate_silicon', 'gt.metaitem.01:17516': 'plate_certus_quartz', 'gt.metaitem.01:17522': 'plate_nether_quartz', 'gt.metaitem.01:17890': 'plate_glass',
            'gt.metaitem.01:2054': 'dust_silver', 'gt.metaitem.01:2085': 'dust_platinum', 'gt.metaitem.01:22305': 'plate_dense_steel', 'gt.metaitem.01:23516': 'rod_certus_quartz', 'gt.metaitem.01:23522': 'rod_nether_quartz',
            'gt.metaitem.01:24500': 'lens_diamond', 'gt.metaitem.01:24501': 'lens_emerald', 'gt.metaitem.01:24503': 'lens_sapphire', 'gt.metaitem.01:24504': 'lens_green_sapphire', 'gt.metaitem.01:24510': 'lens_opal',
            'gt.metaitem.01:24513': 'lens_blue_topaz', 'gt.metaitem.01:24528': 'lens_garnet_yellow', 'gt.metaitem.01:24890': 'lens_glass', 'gt.metaitem.01:2500': 'dust_diamond', 'gt.metaitem.01:2501': 'dust_emerald',
            'gt.metaitem.01:2516': 'dust_certus_quartz', 'gt.metaitem.01:2522': 'dust_nether_quartz', 'gt.metaitem.01:2523': 'dust_quartzite', 'gt.metaitem.01:2532': 'dust_ender_pearl', 'gt.metaitem.01:26032': 'bolt_iron',
            'gt.metaitem.01:2890': 'dust_glass', 'gt.metaitem.01:32307': 'mold_ball', 'gt.metaitem.01:32351': 'shape_extruder_rod', 'gt.metaitem.01:32500': 'battery_hull_lv', 'gt.metaitem.01:32518': 'battery_lithium_lv',
            'gt.metaitem.01:32528': 'battery_lithium_mv', 'gt.metaitem.01:32601': 'electric_motor_mv', 'gt.metaitem.01:32611': 'electric_pump_mv', 'gt.metaitem.01:32631': 'conveyor_module_mv', 'gt.metaitem.01:32641': 'electric_piston_mv',
            'gt.metaitem.01:32651': 'robot_arm_mv', 'gt.metaitem.01:32671': 'field_generator_mv', 'gt.metaitem.01:32672': 'field_generator_hv', 'gt.metaitem.01:32684': 'emitter_iv', 'gt.metaitem.01:32729': 'item_filter',
            'gt.metaitem.01:32740': 'cover_screen', 'gt.metaitem.01:32744': 'cover_crafting', 'gt.metaitem.03:32039': 'chip_ram'}
# Not ported in IIS 2 at all. Substitutes are an "отличие" (PORTING_NOTES); the reason is in the report
FOREIGN = {'IronChest:BlockIronChest:4': 'minecraft:chest', 'IronChest:BlockIronChest:6': 'minecraft:chest', 'IronChest:BlockIronChest': 'minecraft:chest', 'BuildCraft|Core:wrenchItem': '#iis:tools/wrench',
           'IC2:blockITNT': None, 'IC2:upgradeModule:5': 'minecraft:redstone_torch', 'minecraft:sand:1': 'minecraft:red_sand'}
FLUIDS = {'chlorine': 'chlorine'}
MACHINES = {'Assembler': 'assembler', 'AlloySmelter': 'alloy_smelter', 'ChemicalBath': 'chemical_bath', 'ChemicalReactor': 'chemical_reactor', 'Autoclave': 'autoclave', 'FluidSolidifier': 'fluid_solidifier',
            'FluidExtractor': 'fluid_extractor', 'Wiremill': 'wiremill', 'Lathe': 'lathe', 'Extruder': 'extruder', 'Centrifuge': 'centrifuge', 'Compressor': 'compressor', 'Macerator': 'macerator',
            'PrecisionLaser': 'laser_engraver', 'CuttingSaw': 'cutter'}


def quest_item(mod, name, dmg):
    """Old AE2 / Ender IO item of a quest -> new id or None (conduits are one item with a component: quests do not use it yet)."""
    dmg = 0 if dmg == 32767 else dmg
    if mod == 'appliedenergistics2':
        if name == 'item.ItemMultiMaterial':
            r = AE_MM.get(dmg)
        elif name == 'item.ItemMultiPart':
            r = AE_PART.get(dmg)
        elif name == 'item.ItemCrystalSeed':
            r = f'{NS}:crystal_seed_{SEEDS[dmg]}' if dmg in SEEDS else None
        else:
            r = AE_NAMED.get(f'{name}:{dmg}' if dmg else name)
        return None if r is None else (r if ':' in r else f'ae2:{r}')
    if mod == 'EnderIO':
        r = EIO.get(f'{name}:{dmg}' if dmg else name)
        return f'enderio:{r}' if r else None
    return None


class Stack:
    def __init__(self, ref, count=1):
        self.ref, self.count = ref, count

    def __mul__(self, n):
        return Stack(self.ref, self.count * n if self.count != 1 else n)


def strip_comments(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = re.sub(r'//[^\n]*', '', t)
    return re.sub(r'^\s*#[^\n]*', '', t, flags=re.M)


def split_args(s):
    out, depth, cur, tag = [], 0, '', False
    for ch in s:
        if ch == '<':
            tag = True
        elif ch == '>':
            tag = False
        if not tag:
            if ch in '[(':
                depth += 1
            elif ch in '])':
                depth -= 1
        if ch == ',' and depth == 0 and not tag:
            out.append(cur.strip())
            cur = ''
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


class Script:
    """The few ZenScript forms the pack's scripts use: variables, recipe calls, NEI.hide."""

    def __init__(self, path):
        self.text = strip_comments(open(path, encoding='cp1251', errors='replace').read())
        self.vars = {}
        for m in re.finditer(r'^\s*var (\w+)\s*=\s*(<[^;]+>(?:\s*\*\s*\d+)?)\s*;', self.text, re.M):
            self.vars[m.group(1)] = self.value(m.group(2))
        self.calls = []
        for m in re.finditer(r'^\s*(recipes|furnace|NEI|\w+)\.(remove|removeShapeless|removeShaped|addShaped|addShapeless|addRecipe|hide)\((.*?)\);', self.text, re.M | re.S):
            self.calls.append((m.group(1), m.group(2), m.group(3)))

    def value(self, s):
        s = s.strip()
        if s == 'null' or not s:
            return None
        if s.startswith('['):
            return [self.value(x) for x in split_args(s[1:-1])]
        m = re.fullmatch(r'(.+?)\s*\*\s*(\d+)', s, re.S)
        if m:
            v = self.value(m.group(1))
            return Stack(v.ref, int(m.group(2))) if isinstance(v, Stack) else v
        m = re.fullmatch(r'<([^<>]+)>', s)
        if m:
            return Stack(m.group(1))
        if re.fullmatch(r'-?\d+', s):
            return int(s)
        if s in self.vars:
            return self.vars[s]
        return ('?', s)


def generate(g):
    A, D, GEN, write, lang, mass, prop = g['A'], g['D'], g['GEN'], g['write'], g['lang'], g['mass'], g['prop']

    def tag_of(form, mat):
        if form == 'frame':
            return f'#c:frames/{g["mat_id"](mat)}'
        if form == 'block':
            return f'#c:storage_blocks/{g["mat_id"](mat)}'
        return g['tag_of'](form, mat)

    REF = g['REF']
    mods = json.load(open(os.path.join(REF, 'aeio_mods.json'), encoding='utf-8'))
    OVR = os.path.join(GEN, 'packs', 'iis_overrides', 'data')
    report = collections.defaultdict(list)
    recipes = {}
    I = lambda x: f'{NS}:{x}'
    ae = lambda x: None if x is None else (x if ':' in x else f'ae2:{x}')
    eio = lambda x: None if x is None else f'enderio:{x}'

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    # --- our own items for what the new AE2 no longer has: crystal seeds and pure crystals (GT_MachineRecipeLoader:395-401) ---
    def crystal(base, seed, gem):
        rnd = random.Random(seed)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        cx, cy, r = 7.5, 7.5, (3.2 if not gem else 5.8)
        for y in range(16):
            for x in range(16):
                d = abs(x - cx) + abs(y - cy) * 0.75
                if d > r:
                    continue
                v = rnd.randint(-14, 14) + int((cx - x + cy - y) * 5)
                im.putpixel((x, y), tuple(max(0, min(255, c + v)) for c in base) + (255,))
        return im

    def item(iid, en, ru, im):
        tdir = os.path.join(A, 'textures', 'item')
        os.makedirs(tdir, exist_ok=True)
        im.save(os.path.join(tdir, iid + '.png'))
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)

    for k, (en, ru, c) in {'certus': ('Certus Quartz', 'истинного кварца', (190, 215, 245)), 'nether': ('Nether Quartz', 'адского кварца', (235, 225, 215)), 'fluix': ('Fluix', 'флюикса', (150, 90, 215))}.items():
        item(f'crystal_seed_{k}', f'{en} Seed', f'Семя {ru}', crystal(c, len(k) * 7, False))
        item(f'crystal_pure_{k}', f'Pure {en} Crystal', f'Чистый кристалл {ru}', crystal(c, len(k) * 11, True))
    steel = (120, 125, 135)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            v = -30 if x in (2, 13) or y in (2, 13) else ((x + y) % 5) * 3
            im.putpixel((x, y), tuple(c + v for c in steel) + (255,))
    item('plate_dense_steel', 'Dense Steel Plate', 'Плотная стальная пластина', im)
    for iid, en, ru, c in (('plate_glowstone', 'Glowstone Plate', 'Светокаменная пластина', (225, 195, 90)), ('plate_obsidian', 'Obsidian Plate', 'Обсидиановая пластина', (60, 45, 85))):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(2, 14):
            for x in range(2, 14):
                v = -30 if x in (2, 13) or y in (2, 13) else ((x * 3 + y) % 7) * 2
                im.putpixel((x, y), tuple(max(0, q + v) for q in c) + (255,))
        item(iid, en, ru, im)
    name('itemGroup.iis.aeio', 'IIS 2: AE and Ender IO', 'ИИС 2: AE и Ender IO')

    # --- translating references ------------------------------------------------------------------------------------------
    def old_ae(body):
        p = body.split(':')
        name_, meta = p[0], int(p[1]) if len(p) > 1 else 0
        if name_ == 'item.ItemMultiMaterial':
            return ae(AE_MM.get(meta)), meta in AE_MM
        if name_ == 'item.ItemMultiPart':
            return ae(AE_PART.get(meta)), meta in AE_PART
        if name_ == 'item.ItemCrystalSeed':
            return (I(f'crystal_seed_{SEEDS[meta]}'), True) if meta in SEEDS else (None, False)
        return ae(AE_NAMED.get(body)), body in AE_NAMED

    def ore_ref(o):
        if o in TOOLS:
            return f'#iis:tools/{TOOLS[o]}'
        m = re.fullmatch(r'circuit(Basic|Good|Advanced|Data|Elite|Master)', o)
        if m:
            return f'#c:circuits/{m.group(1).lower()}'
        special = {'batteryElite': I('energy_crystal'), 'craftingRedstoneTorch': 'minecraft:redstone_torch', 'craftingWorkBench': 'minecraft:crafting_table', 'glass': '#c:glass_blocks', 'glowstone': 'minecraft:glowstone',
                   'gemFluix': 'ae2:fluix_crystal', 'itemCertusQuartz': '#iis:item_certus_quartz', 'itemNetherQuartz': '#iis:item_nether_quartz', 'itemIlluminatedPanel': '#ae2:illuminated_panel',
                   'itemVibrantCrystal': 'enderio:vibrant_crystal', 'itemAttractorCrystal': 'enderio:enticing_crystal', 'gemEnderPearl': 'minecraft:ender_pearl', 'dustGlowstone': 'minecraft:glowstone_dust',
                   'nuggetPlutonium241': I('nugget_plutonium241'), 'plateGlowstone': I('plate_glowstone'), 'plateObsidian': I('plate_obsidian')}
        if o in special:
            return special[o]
        m = re.fullmatch(r'(cable|wire)Gt(\d\d)(\w+)', o)
        if m:
            kind = 'cables' if m.group(1) == 'cable' else 'wires'
            return f'#iis:{kind}/{int(m.group(2))}x/{g["mat_id"](m.group(3))}'
        m = re.fullmatch(r'(frameGt|plate|stick|screw|ingot|dust|gem|nugget)([A-Z]\w+)', o)
        if m:
            return tag_of(ORE_PREFIX[m.group(1)], m.group(2))
        m = re.fullmatch(r'wire([A-Z]\w+)', o)
        if m:
            return f'#iis:wires/1x/{g["mat_id"](m.group(1))}'
        return None

    def trans(st):
        """Reference of the script -> (id or #tag or {conduit}, why it is missing)."""
        ref = st.ref
        if ref.startswith('ore:'):
            r = ore_ref(ref[4:])
            return r, None if r else f'нет соответствия <{ref}>'
        if ref in FOREIGN:
            r = FOREIGN[ref]
            return r, None if r else f'мода нет в ИИС 2: <{ref}>'
        mod, _, body = ref.partition(':')
        if mod == 'appliedenergistics2':
            r, known = old_ae(body)
            return r, None if r else (f'нет в новом AE2: <{ref}>' if known else f'нет соответствия <{ref}>')
        if mod == 'EnderIO':
            if body in CONDUITS:
                c = CONDUITS[body]
                return ({'conduit': c} if c else None), None if c else f'нет в новом Ender IO: <{ref}>'
            m = re.fullmatch(r'itemAlloy(?::(\d))?', body)
            if m:
                mat = {0: 'ElectricalSteel', 1: 'EnergeticAlloy', 2: 'VibrantAlloy', 3: 'RedstoneAlloy', 4: 'ConductiveIron', 5: 'PulsatingIron', 6: 'DarkSteel', 7: 'Soularium'}[int(m.group(1) or 0)]
                return tag_of('ingot', mat), None
            if body not in EIO:
                return None, f'нет соответствия <{ref}>'
            r = eio(EIO[body])
            return r, None if r else f'нет в новом Ender IO: <{ref}>'
        if mod == 'gregtech':
            if body.startswith('gt.integrated_circuit'):
                return {'circuit': int(body.split(':')[1])}, None
            if body in GT_MANUAL:
                return tag_of(*GT_MANUAL[body]), None
            if body in GT_ITEMS:
                return I(GT_ITEMS[body]), None
            return None, f'нет в ИИС 2: <{ref}>'
        if mod == 'minecraft':
            n = {'quartz_block': 'minecraft:quartz_block', 'quartz': 'minecraft:quartz', 'crafting_table': 'minecraft:crafting_table'}.get(body, ref)
            return n, None
        return None, f'нет соответствия <{ref}>'

    def fluid(st):
        n = st.ref.split(':', 1)[1]
        return I(FLUIDS.get(n, n.replace('.', '_')))

    def as_input(t):
        return {'neoforge:ingredient_type': 'enderio:conduit', 'conduit_type': f'enderio:{t["conduit"]}'} if isinstance(t, dict) and 'conduit' in t else t

    def out_stack(r, count=1):
        if isinstance(r, dict) and 'conduit' in r:
            return {'id': 'enderio:conduit', 'count': count, 'components': {'enderio:conduit': f'enderio:{r["conduit"]}'}}
        return {'id': r, 'count': count}

    used_names = collections.Counter()

    def rid(mod, kind, out):
        base = (out['id'] if isinstance(out, dict) else str(out)).split(':')[-1]
        if isinstance(out, dict) and 'components' in out:
            base = out['components']['enderio:conduit'].split(':')[-1] + '_conduit'
        key = f'{mod}/{kind}/{base}'
        used_names[key] += 1
        return key if used_names[key] == 1 else f'{key}_{used_names[key]}'

    def to_crafting(mod, grid, out, count, what):
        if isinstance(grid[0], Stack) or grid[0] is None:
            grid = [grid]
        rows = [list(r) for r in grid]
        while rows and all(c is None for c in rows[-1]):
            rows.pop()
        while rows and all(c is None for c in rows[0]):
            rows.pop(0)
        w = max(len(r) for r in rows)
        rows = [r + [None] * (w - len(r)) for r in rows]
        while all(r[-1] is None for r in rows):
            rows = [r[:-1] for r in rows]
        while all(r[0] is None for r in rows):
            rows = [r[1:] for r in rows]
        key, pat, letters = {}, [], 'ABCDEFGHI'
        seen = {}
        for r in rows:
            line = ''
            for c in r:
                if c is None:
                    line += ' '
                    continue
                t, why = trans(c)
                if t is None:
                    report[why].append(what)
                    return None
                ks = json.dumps(t)
                if ks not in seen:
                    seen[ks] = letters[len(seen)]
                    key[seen[ks]] = as_input(t)
                line += seen[ks]
            pat.append(line)
        return {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pat, 'key': key, 'result': out_stack(out, count)}

    # --- one script ------------------------------------------------------------------------------------------------------
    removed, hidden = set(), set()

    def machine_rec(mod, machine, args, what):
        """args of mods.gregtech.*.addRecipe; the shapes below are the ones the pack's scripts use."""
        rt = MACHINES[machine]
        outs, inputs, fin, fout, dur, eut, chance = [], [], None, None, None, None, None
        flat = lambda v: v if isinstance(v, list) else ([v] if v is not None else [])
        if machine == 'Assembler':
            outs = [args[0]]
            nums = [a for a in args[1:] if isinstance(a, int)]
            dur, eut = nums[0], nums[1]
            for a in args[1:]:
                if isinstance(a, int):
                    continue
                for s in flat(a):
                    if s.ref.startswith('liquid:'):
                        fin = s
                    else:
                        inputs.append(s)
        elif machine in ('AlloySmelter', 'Extruder', 'PrecisionLaser', 'FluidSolidifier'):
            outs = [args[0]]
            dur, eut = args[-2], args[-1]
            for s in args[1:-2]:
                if s is not None and s.ref.startswith('liquid:'):
                    fin = s
                elif s is not None:
                    inputs.append(s)
        elif machine in ('Wiremill', ):
            outs, inputs, dur, eut = [args[0]], [args[1]], args[2], args[3]
        elif machine == 'Lathe':
            outs, inputs, dur, eut = flat(args[0]), [args[1]], args[2], args[3]
        elif machine == 'ChemicalBath':
            outs, inputs, fin, dur, eut = flat(args[0]), [args[1]], args[2], args[4], args[5]
        elif machine == 'Autoclave':
            outs, inputs, fin, chance, dur, eut = [args[0]], [args[1]], args[2], args[3], args[4], args[5]
        elif machine == 'FluidExtractor':
            inputs, fout, chance, dur, eut = [args[1]], args[2], args[3], args[4], args[5]
        elif machine == 'ChemicalReactor':
            if len(args) == 4:
                outs, inputs, dur = [args[0]], [args[1], args[2]], args[3]
            else:
                outs, fout, inputs, fin, dur = flat(args[0]), args[1], [a for a in (args[2], args[3]) if a is not None], args[4], args[5]
            eut = 30
        elif machine == 'CuttingSaw':
            outs, inputs, fin, dur, eut = flat(args[0]), [args[1]], args[2], args[3], args[4]
        elif machine == 'Centrifuge':
            outs, inputs, dur, eut = flat(args[0]), [args[1]], args[3], 5
        elif machine in ('Compressor', 'Macerator'):
            outs, inputs, dur, eut = [args[0]], [args[1]], 400 if machine == 'Macerator' else 400, 2
        r = {'type': f'{NS}:{rt}', 'inputs': [], 'outputs': [], 'duration': int(dur), 'eut': int(eut)}
        for s in inputs:
            t, why = trans(s)
            if t is None:
                report[why].append(what)
                return None
            if isinstance(t, dict) and 'circuit' in t:
                r['circuit'] = t['circuit']
            elif s.count == 0:
                r.setdefault('keep', []).append(as_input(t))
            else:
                r['inputs'].append({'ingredient': as_input(t), 'count': s.count})
        for s in outs:
            t, why = trans(s)
            if t is None:
                report[why].append(what)
                return None
            if isinstance(t, str) and t.startswith('#'):
                # a material as a result: its tag cannot be an output, our item of that form can
                form = GT_MANUAL.get(s.ref.partition(':')[2])
                t = g['ref'](*form) if form else None
                if t is None:
                    report[f'выход-тег без предмета: {s.ref}'].append(what)
                    return None
            o = out_stack(t, s.count)
            if chance and chance < 10000:
                o['chance'] = chance
            r['outputs'].append(o)
        if fin is not None:
            r['fluid_inputs'] = [{'ingredient': fluid(fin), 'amount': fin.count}]
        if fout is not None:
            r['fluid_outputs'] = [{'id': fluid(fout), 'amount': fout.count}]
        return r

    def run_script(fname, mod):
        sc = Script(os.path.join(INST, 'scripts', fname))
        for obj, meth, body in sc.calls:
            args = [sc.value(a) for a in split_args(body)]
            short = re.sub(r'\s+', ' ', body)[:110]
            what = f'{fname}: {obj}.{meth}({short})'
            if any(isinstance(a, tuple) for a in args):
                report['непонятное выражение в скрипте'].append(what)
                continue
            if obj == 'recipes' and meth in ('remove', 'removeShapeless', 'removeShaped') and args and isinstance(args[0], Stack):
                t, _ = trans(args[0])
                if isinstance(t, str) and ':' in t and not t.startswith('#'):
                    removed.add(t)
                elif isinstance(t, dict) and 'conduit' in t:
                    removed.add('conduit:' + t['conduit'])
            elif obj == 'NEI' and meth == 'hide' and args and isinstance(args[0], Stack):
                t, _ = trans(args[0])
                if isinstance(t, str) and not t.startswith('#'):
                    hidden.add(t)
            elif obj == 'recipes' and meth == 'addShaped':
                t, why = trans(args[0])
                if t is None:
                    report[why].append(what)
                    continue
                r = to_crafting(mod, args[1], t, args[0].count, what)
                if r:
                    recipes[rid(mod, 'crafting', r['result'])] = r
            elif obj == 'recipes' and meth == 'addShapeless':
                t, why = trans(args[0])
                if t is None:
                    report[why].append(what)
                    continue
                ings = []
                for s in args[1]:
                    i, why = trans(s)
                    if i is None:
                        report[why].append(what)
                        ings = None
                        break
                    ings.append(as_input(i))
                if ings is not None:
                    r = {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ings, 'result': out_stack(t, args[0].count)}
                    recipes[rid(mod, 'crafting', r['result'])] = r
            elif meth == 'addRecipe' and obj in MACHINES:
                try:
                    r = machine_rec(mod, obj, args, what)
                except (IndexError, AttributeError, TypeError) as e:
                    report[f'непонятный вызов {obj}: {e}'].append(what)
                    continue
                if r and (r['outputs'] or r.get('fluid_outputs')):
                    head = r['outputs'][0] if r['outputs'] else r['fluid_outputs'][0]
                    recipes[rid(mod, MACHINES[obj], head)] = r

    run_script('AE2.zs', 'ae2')
    run_script('EnderIO.zs', 'enderio')
    # Slabs: the loop at the end of AE2.zs
    for full, slab in (('chiseled_quartz_block', 'chiseled_quartz_slab'), ('quartz_pillar', 'quartz_pillar_slab'), ('quartz_block', 'quartz_slab')):
        recipes[f'ae2/crafting/saw_{slab}'] = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['SB'], 'key': {'S': '#iis:tools/saw', 'B': f'ae2:{full}'}, 'result': out_stack(f'ae2:{slab}', 2)}

    # --- the AE2 chain inside GT ----------------------------------------------------------------------------------------
    def mrec(mod, rtype, key, inputs, outputs, dur, eut, fin=None, fins=None, fout=None, keep=None, circuit=None, temp=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': [{'ingredient': i, 'count': c} for i, c in inputs], 'outputs': [dict({'id': o, 'count': c}) for o, c in outputs], 'duration': int(dur), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [{'ingredient': fin[0], 'amount': fin[1]}]
        if fout:
            r['fluid_outputs'] = [{'id': fout[0], 'amount': fout[1]}]
        if keep:
            r['keep'] = keep if isinstance(keep, list) else [keep]
        if circuit is not None:
            r['circuit'] = circuit
        if temp:
            r['temp'] = temp
        recipes[f'{mod}/gt/{rtype}/{key}'] = r

    plate = lambda n: tag_of('plate', n)
    dust = lambda n: tag_of('dust', n)
    ingot = lambda n: tag_of('ingot', n)
    water, dist = 'minecraft:water', I('distilled_water')
    # Fluix: GT_MachineRecipeLoader:112-113
    for f, a, k in ((water, 500, 'water'), (dist, 500, 'distilled')):
        mrec('ae2', 'mixer', f'fluix_{k}', [('ae2:charged_certus_quartz_crystal', 1), (dust('Redstone'), 1), (tag_of('gem', 'NetherQuartz'), 1)], [('ae2:fluix_crystal', 2)], 20, 16, fin=(f, a))
    # Charged certus: ProcessingGem:72
    mrec('ae2', 'electrolyzer', 'charged_certus', [(tag_of('gem', 'CertusQuartz'), 1)], [('ae2:charged_certus_quartz_crystal', 1)], 2000, 30)
    # Fluix dust from a crystal: ProcessingGem, ordinary pulverising of a gem
    mrec('ae2', 'macerator', 'fluix_dust', [('ae2:fluix_crystal', 1)], [('ae2:fluix_dust', 1)], 400, 2)
    # Seeds: GT_MachineRecipeLoader:641-643; pure crystals: :395-401
    for k, d in (('certus', dust('CertusQuartz')), ('nether', dust('NetherQuartz')), ('fluix', 'ae2:fluix_dust')):
        mrec('ae2', 'assembler', f'seed_{k}', [(d, 1), ('#minecraft:sand', 1)], [(I(f'crystal_seed_{k}'), 2)], 64, 8)
        mrec('ae2', 'autoclave', f'pure_{k}_water', [(I(f'crystal_seed_{k}'), 1)], [(I(f'crystal_pure_{k}'), 1)], 2000, 24, fin=(water, 200))
        mrec('ae2', 'autoclave', f'pure_{k}_distilled', [(I(f'crystal_seed_{k}'), 1)], [(I(f'crystal_pure_{k}'), 1)], 1000, 24, fin=(dist, 200))
    # Presses: ProcessingCrafting:31-32, 39-40, 49-50, 69-70 (lenses by colour); AE2.zs adds its own above
    for press, lens in (('calculation_processor_press', 'blue'), ('engineering_processor_press', 'yellow'), ('logic_processor_press', 'green'), ('silicon_press', 'white')):
        mrec('ae2', 'laser_engraver', f'{press}_iron', [('minecraft:iron_block', 1)], [(f'ae2:{press}', 1)], 2000, 1920, keep=f'#iis:crafting_lens/{lens}')
        mrec('ae2', 'laser_engraver', f'{press}_wrought', [(tag_of('block', 'WroughtIron'), 1)], [(f'ae2:{press}', 1)], 2000, 1920, keep=f'#iis:crafting_lens/{lens}')
    # Printed circuits: GT_MachineRecipeLoader:544-548; processors :638-640
    fp = lambda key, i, press, o: mrec('ae2', 'forming_press', key, [(i, 1)], [(f'ae2:{o}', 1)], 200, 16, keep=f'ae2:{press}')
    fp('printed_calc_pure', I('crystal_pure_certus'), 'calculation_processor_press', 'printed_calculation_processor')
    fp('printed_calc', plate('CertusQuartz'), 'calculation_processor_press', 'printed_calculation_processor')
    fp('printed_eng', plate('Diamond'), 'engineering_processor_press', 'printed_engineering_processor')
    fp('printed_logic', plate('Gold'), 'logic_processor_press', 'printed_logic_processor')
    fp('printed_silicon', plate('Silicon'), 'silicon_press', 'printed_silicon')
    for k, o in (('calculation', 'calculation_processor'), ('engineering', 'engineering_processor'), ('logic', 'logic_processor')):
        mrec('ae2', 'assembler', o, [(f'ae2:printed_{k}_processor', 1), ('ae2:printed_silicon', 1)], [(f'ae2:{o}', 1)], 64, 32, fin=(I('molten_redstone'), 144))
    # Dense steel: ProcessingIngot:63 (the press blanks of AE2.zs are made from it)
    mrec('ae2', 'bender', 'plate_dense_steel', [(plate('Steel'), 9)], [(I('plate_dense_steel'), 1)], max(mass('Steel') * 9, 1), 96, circuit=9)
    # Plates of dust materials: ProcessingDust:56 (compressor, 1 dust to 1 plate)
    for k, d in (('glowstone', dust('Glowstone')), ('obsidian', dust('Obsidian'))):
        mrec('ae2', 'compressor', f'plate_{k}', [(d, 1)], [(I(f'plate_{k}'), 1)], 400, 2)
    # AE2.zs takes the crafting of the certus block away; IIS 1 kept it decorative through GT's automatic compressor recipes, see PORTING_NOTES
    mrec('ae2', 'compressor', 'quartz_block', [(tag_of('gem', 'CertusQuartz'), 4)], [('ae2:quartz_block', 1)], 400, 2)

    # --- Ender alloys: GT_MachineRecipeLoader:1020-1027 (furnace, smelter), :1800-1809 (mixer). The old mod's own machines are hidden in EnderIO.zs ---
    T = lambda n: g['code'][n].get('blastFurnaceTemp') or prop(n, 'BlastFurnaceTemp', 0) or 1000
    mrec('enderio', 'blast', 'electrical_steel', [(ingot('Steel'), 3), (ingot('Silicon'), 1)], [(g['ref']('ingot', 'ElectricalSteel'), 4), (tag_small := I('small_dust_dark_ash'), 2)], max(mass('ElectricalSteel') // 40, 1) * T('ElectricalSteel'), 120, temp=T('ElectricalSteel'))
    mrec('enderio', 'blast', 'energetic_alloy', [(ingot('Gold'), 1), (dust('Glowstone'), 1)], [(g['ref']('ingot', 'EnergeticAlloy'), 1)], T('EnergeticAlloy') // 10, 120, fin=(I('molten_redstone'), 144), temp=T('EnergeticAlloy'))
    mrec('enderio', 'blast', 'vibrant_alloy', [(ingot('EnergeticAlloy'), 1), ('minecraft:ender_pearl', 1)], [(g['ref']('ingot', 'VibrantAlloy'), 1)], T('VibrantAlloy') // 10, 480, temp=T('VibrantAlloy'))
    mrec('enderio', 'blast', 'pulsating_iron', [(ingot('Iron'), 1), ('minecraft:ender_pearl', 1)], [(g['ref']('ingot', 'PulsatingIron'), 1)], T('PulsatingIron') // 10, 120, temp=T('PulsatingIron'))
    mrec('enderio', 'blast', 'dark_steel', [(ingot('ElectricalSteel'), 1), (dust('Obsidian'), 1)], [(g['ref']('ingot', 'DarkSteel'), 1)], 4000, 120, temp=2000)
    mrec('enderio', 'alloy_smelter', 'redstone_alloy', [(dust('Redstone'), 1), (ingot('Silicon'), 1)], [(g['ref']('ingot', 'RedstoneAlloy'), 1)], 400, 24)
    mrec('enderio', 'alloy_smelter', 'conductive_iron', [(dust('Redstone'), 1), (ingot('Iron'), 1)], [(g['ref']('ingot', 'ConductiveIron'), 1)], 400, 24)
    mrec('enderio', 'alloy_smelter', 'soularium', [('minecraft:soul_sand', 1), (ingot('Gold'), 1)], [(g['ref']('ingot', 'Soularium'), 1)], 400, 24)
    for key, ins, oname, n in (('redstone_alloy', (dust('Redstone'), dust('Silicon'), dust('Coal')), 'RedstoneAlloy', 3), ('conductive_iron', (dust('RedstoneAlloy'), dust('Iron'), dust('Silver')), 'ConductiveIron', 3),
                               ('energetic_alloy', (dust('ConductiveIron'), dust('Gold'), dust('BlackSteel')), 'EnergeticAlloy', 3), ('vibrant_alloy', (dust('EnergeticAlloy'), dust('EnderEye'), dust('Chrome')), 'VibrantAlloy', 3),
                               ('electrical_steel', (dust('Steel'), dust('Coal'), dust('Silicon')), 'ElectricalSteel', 3), ('pulsating_iron', (dust('Iron'), dust('EnderPearl'), dust('RedstoneAlloy')), 'PulsatingIron', 3),
                               ('soularium', ('minecraft:soul_sand', dust('Gold'), dust('Ash')), 'Soularium', 3), ('dark_steel', (dust('ElectricalSteel'), dust('Coal'), dust('Obsidian')), 'DarkSteel', 3),
                               ('end_steel', (dust('DarkSteel'), dust('Endstone'), dust('Tungsten')), 'EndSteel', 3), ('enderium_base', (dust('Tin'), dust('Silver'), dust('Platinum')), 'EnderiumBase', 4)):
        amounts = [(i, 2 if (key == 'enderium_base' and k == 0) else 1) for k, i in enumerate(ins)]
        mrec('enderio', 'mixer', key, amounts, [(g['ref']('dust', oname), n)], 400 if key == 'enderium_base' else (100 if key == 'end_steel' else 300), 8, circuit=3)

    # Obsidian dust for dark steel: GT pulverises the vanilla block (GT_RecipeRegistrator, block of the Obsidian material)
    mrec('enderio', 'macerator', 'dust_obsidian', [('minecraft:obsidian', 1)], [(g['ref']('dust', 'Obsidian'), 1)], 400, 2)
    # Rod of ender eye for the wireless receiver of AE2.zs: ProcessingGem, lathe gem -> stick + small dust
    mrec('ae2', 'lathe', 'rod_ender_eye', [('minecraft:ender_eye', 1)], [(I('rod_ender_eye'), 1), (I('small_dust_ender_eye'), 2)], max(mass('EnderEye'), 1), 16)
    # Quartzite screw of AE2.zs (terminal, cell housing): gem -> rod (ProcessingGem:52), rod -> bolt -> screw by hand, as for any material
    mrec('ae2', 'lathe', 'rod_quartzite', [(tag_of('gem', 'Quartzite'), 1)], [(I('rod_quartzite'), 1), (I('small_dust_quartzite'), 2)], max(mass('Quartzite'), 1), 16)
    recipes['ae2/crafting/bolt_quartzite'] = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['S ', ' R'], 'key': {'S': '#iis:tools/saw', 'R': I('rod_quartzite')}, 'result': out_stack(I('bolt_quartzite'), 2)}
    recipes['ae2/crafting/screw_quartzite'] = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['FB', 'B '], 'key': {'F': '#iis:tools/file', 'B': I('bolt_quartzite')}, 'result': out_stack(I('screw_quartzite'), 1)}
    # Chiseled certus block: ProcessingCrafting:75
    mrec('ae2', 'laser_engraver', 'chiseled_quartz_block', [('ae2:quartz_block', 1)], [('ae2:chiseled_quartz_block', 1)], 50, 16, keep='#iis:crafting_lens/white')

    # --- writing recipes, each only when its mods are there ---------------------------------------------------------------
    known_items = {'ae2': set(mods['ae2']['items']), 'enderio': set(mods['enderio']['items'])}
    bad = collections.Counter()
    for key, r in recipes.items():
        text = json.dumps(r)
        need = [m for m in ('ae2', 'enderio') if f'"{m}:' in text or f'"#{m}:' in text]
        for m in need:
            for i in re.findall(rf'"{m}:(\w+)"', text):
                if i not in known_items[m] and not i.endswith(('_energy', '_fluid', '_item')) and i not in ('energy', 'fluid', 'item', 'redstone'):
                    bad[f'{m}:{i}'] += 1
        out = dict(r)
        if need:
            out['neoforge:conditions'] = [{'type': 'neoforge:mod_loaded', 'modid': m} for m in need]
        write(os.path.join(D, NS, 'recipe', 'aeio', key + '.json'), out)
    assert not bad, bad
    with open(os.path.join(g['ROOT'], 'src', 'main', 'resources', NS, 'aeio_recipes.json'), 'w', encoding='utf-8', newline=chr(10)) as f:
        json.dump(sorted(f'{NS}:aeio/{k}' for k in recipes), f, indent=0)

    # --- switching off and hiding -----------------------------------------------------------------------------------------
    obtainable = {r['result']['id'] for r in recipes.values() if 'result' in r} | {o['id'] for r in recipes.values() for o in r.get('outputs', [])}
    off = {'ae2': {}, 'enderio': {}}

    def out_key(e):
        return ('conduit:' + e['conduit'].split(':')[1]) if e.get('conduit') else e['out']

    for path, e in mods['ae2']['recipes'].items():
        o = e['out'] or ''
        why = None
        if e['type'] in ('ae2:inscriber', 'ae2:charger'):
            why = 'пресс и зарядник скрыты в AE2.zs, путь идёт через машины GT'
        elif e['type'] == 'ae2:transform' and 'singularity' not in path:
            why = 'в ИИС 1 нет выращивания кварца в мире: рецепты GT'
        elif e['type'] in ('minecraft:smelting', 'minecraft:blasting') and 'silicon' in o:
            why = 'кремний делает GT'
        elif out_key(e) in removed:
            why = 'recipes.remove в AE2.zs'
        elif any(k in o for k in AE_OFF):
            why = 'выключено в настройках ИИС 1 или нет в ИИС 1'
        if why:
            off['ae2'][path] = why
    for path, e in mods['enderio']['recipes'].items():
        o = (e['out'] or '').split(':')[-1]
        k = out_key(e)
        if e['type'] in ('enderio:soul_binding', 'enderio:slicing', 'enderio:painting'):
            if k in removed:
                off['enderio'][path] = 'recipes.remove в EnderIO.zs'
            continue
        if k in removed or 'photovoltaic' in o:
            off['enderio'][path] = 'recipes.remove в EnderIO.zs'
        elif e['type'] not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
            off['enderio'][path] = 'машины и обработка этого рода скрыты в EnderIO.zs'
        elif not (o.endswith('_conduit') or any(s in o for s in EIO_KEEP_OLD) or o in set(x for x in EIO.values() if x)):
            off['enderio'][path] = 'в ИИС 1 этого нет или оно скрыто'
    # Ingots, nuggets and blocks of the alloys: one set, GT's (GT_OreDictUnificator made them one in IIS 1)
    for path, e in mods['enderio']['recipes'].items():
        o = (e['out'] or '').split(':')[-1]
        if re.search(r'_(ingot|nugget)$|_alloy_block$|^dark_steel_block$|^end_steel_block$|^soularium_block$|grinding_ball$', o):
            off['enderio'].setdefault(path, 'слитки сплавов делает GT')

    # Fixpoint: a recipe whose ingredient cannot be had any more goes off too. Matter balls and singularities come from the condenser, not from recipes
    obtainable |= {'ae2:matter_ball', 'ae2:singularity', 'ae2:quantum_entangled_singularity'}
    changed = True
    while changed:
        changed = False
        have = set(obtainable)
        for ns in ('ae2', 'enderio'):
            for path, e in mods[ns]['recipes'].items():
                if path not in off[ns] and e['out']:
                    have.add(e['out'])
        for ns in ('ae2', 'enderio'):
            for path, e in mods[ns]['recipes'].items():
                if path in off[ns]:
                    continue
                for i in e['in']:
                    if (i.startswith('ae2:') or i.startswith('enderio:')) and i not in have and i.split(':')[1] in known_items[i.split(':')[0]]:
                        off[ns][path] = f'нельзя получить {i}'
                        changed = True
                        break

    for ns, paths in off.items():
        for path in paths:
            write(os.path.join(OVR, ns, 'recipe', path + '.json'), {'neoforge:conditions': [{'type': 'neoforge:never'}]})

    have = set(obtainable)
    for ns in ('ae2', 'enderio'):
        for path, e in mods[ns]['recipes'].items():
            if path not in off[ns] and e['out']:
                have.add(e['out'])
    hide = set(h for h in hidden if h.split(':')[0] in ('ae2', 'enderio'))
    for it in mods['ae2']['items']:
        full = f'ae2:{it}'
        if full not in have or any(k in it for k in AE_OFF) or it in ('certus_quartz_crystal', 'certus_quartz_dust', 'silicon'):
            if it not in ('facade', 'guide', 'matter_ball', 'singularity', 'quantum_entangled_singularity', 'wrapped_generic_stack', 'paint') and not it.startswith(('sky_stone', 'smooth_sky')):
                hide.add(full)
    for it in mods['enderio']['items']:
        full = f'enderio:{it}'
        if full not in have and it != 'conduit':
            hide.add(full)
        if re.search(r'_(ingot|nugget)$|_alloy_block$|^dark_steel_block$|^end_steel_block$|^soularium_block$', it):
            hide.add(full)
    write(os.path.join(D, 'c', 'tags', 'item', 'hidden_from_recipe_viewers.json'),
          {'replace': False, 'values': [{'id': h, 'required': False} for h in sorted(hide)]})

    # Tags the mods' own recipes read (conductive_alloy and pulsating_alloy are their names for GT's ConductiveIron and PulsatingIron)
    for tag, mat in (('ingots/conductive_alloy', 'ConductiveIron'), ('ingots/pulsating_alloy', 'PulsatingIron')):
        write(os.path.join(D, 'c', 'tags', 'item', tag + '.json'), {'replace': False, 'values': [g['ref']('ingot', mat)]})
    write(os.path.join(D, NS, 'tags', 'item', 'item_certus_quartz.json'), {'replace': False, 'values': [I('gem_certus_quartz'), I('crystal_pure_certus')]})
    write(os.path.join(D, NS, 'tags', 'item', 'item_nether_quartz.json'), {'replace': False, 'values': [g['ref']('gem', 'NetherQuartz'), I('crystal_pure_nether')]})
    # Certus and fluix of AE2 recipes that stay: our gem belongs to AE2's own "any certus" tag
    write(os.path.join(D, 'ae2', 'tags', 'item', 'all_certus_quartz.json'), {'replace': False, 'values': [I('gem_certus_quartz')]})
    # IIS 1 config: MeteoriteWorldGen=false, so no meteorites in the world
    write(os.path.join(OVR, 'ae2', 'worldgen', 'structure_set', 'meteorite.json'), {'neoforge:conditions': [{'type': 'neoforge:never'}]})

    # --- report ------------------------------------------------------------------------------------------------------------
    lines = ['# AE2 и Ender IO: что из ИИС 1 не перенеслось (D125)', '', f'Рецептов ИИС 1 записано: {len(recipes)}. Выключено рецептов модов: AE2 {len(off["ae2"])}, Ender IO {len(off["enderio"])}. Скрыто предметов: {len(hide)}.', '']
    for why, whats in sorted(report.items(), key=lambda kv: -len(kv[1])):
        lines.append(f'- {why}: {len(whats)}')
        for w in whats[:4]:
            lines.append(f'  - `{w}`')
    lines += ['', '## Выключенные рецепты по причинам', '']
    for ns in ('ae2', 'enderio'):
        for why, n in collections.Counter(off[ns].values()).most_common():
            lines.append(f'- {ns}: {why}: {n}')
    with open(os.path.join(g['ROOT'], 'quests', 'AEIO_REPORT.md'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')
    return {'recipes': len(recipes), 'off_ae2': len(off['ae2']), 'off_enderio': len(off['enderio']), 'hidden': len(hide), 'unported': sum(len(v) for v in report.values())}
