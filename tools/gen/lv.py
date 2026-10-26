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
# GT_Loader_MetaTileEntities#makeWires:1468-1496 with HardCoreCableLoss=false (bEC = true): material, loss insulated,
# loss bare, amps per 1x, voltage. Only materials that exist in IIS 2 (titanium, tungsten, osmium… come with their materials).
WIRES = [
    ('RedAlloy', 0, 1, 1, 8),
    ('Cobalt', 2, 4, 2, 32),
    ('Lead', 2, 4, 2, 32),
    ('Tin', 1, 2, 1, 32),
    ('Zinc', 1, 2, 1, 32),
    ('SolderingAlloy', 1, 2, 1, 32),
    ('Iron', 3, 6, 2, 128),
    ('Nickel', 3, 6, 3, 128),
    ('Cupronickel', 3, 6, 2, 128),   # heating coils
    ('Copper', 2, 4, 1, 128),
    ('AnnealedCopper', 1, 2, 1, 128),
    ('Gold', 2, 4, 3, 512),          # electrolyzer (LV III); D76 used the hardcore 6/12 by mistake, fixed in D79
    ('Electrum', 2, 4, 2, 512),
    ('Silver', 1, 2, 1, 512),
    ('Steel', 2, 4, 2, 2048),
    ('Aluminium', 1, 2, 1, 2048),
    ('Platinum', 1, 2, 2, 8192),
]
# size, bare thickness, insulated thickness (GT makeWires; there is no 16x cable — commented out in GT), insulation ring
# (GT_MetaPipeEntity_Cable#getTexture by thickness: <0.374 TINY, <0.499 SMALL, <0.624 MEDIUM, <0.749 MEDIUM_PLUS, <0.874 LARGE)
WIRE_SIZES = [(1, 0.125, 0.25, 'INSULATION_TINY'), (2, 0.25, 0.375, 'INSULATION_SMALL'), (4, 0.375, 0.5, 'INSULATION_MEDIUM'),
              (8, 0.5, 0.625, 'INSULATION_MEDIUM_PLUS'), (12, 0.625, 0.75, 'INSULATION_LARGE'), (16, 0.75, None, None)]
VOLTAGE_NAMES = {8: 'ULV', 32: 'LV', 128: 'MV', 512: 'HV', 2048: 'EV', 8192: 'IV'}
# ProcessingWire: insulation by material (the GT switch falls through: red alloy also takes the carpet and rubber recipes,
# the carpet group also takes rubber)
WIRE_PAPER = ('RedAlloy',)
WIRE_CARPET = ('RedAlloy', 'Cobalt', 'Lead', 'Tin', 'Zinc', 'SolderingAlloy')
WIRE_RUBBER = WIRE_CARPET + ('Iron', 'Nickel', 'Cupronickel', 'Copper', 'AnnealedCopper', 'Gold', 'Electrum', 'Silver', 'Steel', 'Aluminium')

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
    # LV II: ore refinement and cutting (GT_Loader_MetaTileEntities:766, 1075, 1003, 1012, 658)
    ('centrifuge',    'centrifuge',    'CENTRIFUGE',       'Centrifuge',   'centrifuge',    2, 6, 64000, 'Centrifuge'),
    ('sifter',        'sifter',        'SIFTER',           'Sifter',       'sifter',        1, 9, 0,     'Sifting Machine'),
    ('electromagnetic_separator', 'electromagneticseparator', 'ELECTROMAGNETIC_SEPARATOR', 'ElectromagneticSeparator',
                      'electromagnetic_separator', 1, 3, 0, 'Electromagnetic Separator'),
    ('autoclave',     'autoclave',     'AUTOCLAVE',        'Autoclave',    'autoclave',     1, 1, 8000,  'Autoclave'),
    ('cutter',        'cutter',        'CUTTER',           'Cutter',       'cutter',        1, 2, 8000,  'Cutting Machine'),
    ('canner',        'canner',        'CANNER',           'Canner',       'canner',        2, 2, 0,     'Canning Machine'),
    # LV III: chemistry and melts (GT_Loader_MetaTileEntities:775, 830, 958, 967; D76)
    ('electrolyzer',  'electrolyzer',  'ELECTROLYZER',     'Electrolyzer', 'electrolyzer',  2, 6, 64000, 'Electrolyzer'),
    ('chemical_reactor', 'chemicalreactor', 'CHEMICAL_REACTOR', 'ChemicalReactor', 'chemical_reactor', 3, 3, 16000, 'Chemical Reactor'),
    ('fluid_extractor', 'fluidextractor', 'FLUID_EXTRACTOR', 'FluidExtractor', 'fluid_extractor', 1, 1, 16000, 'Fluid Extractor'),
    ('fluid_solidifier', 'fluidsolidifier', 'FLUID_SOLIDIFIER', 'FluidSolidifier', 'fluid_solidifier', 1, 1, 16000, 'Fluid Solidifier'),
    # LV IV: distillery and chemical bath (GT_Loader_MetaTileEntities:976, 985)
    ('distillery',    'distillery',    'DISTILLERY',       'Distillery',   'distillery',    1, 1, 1000,  'Distillery'),
    ('chemical_bath', 'chemicalbath',  'CHEMICAL_BATH',    'ChemicalBath', 'chemical_bath', 1, 3, 8000,  'Chemical Bath'),
    # MV (D81): GT_Loader_MetaTileEntities:784 LV thermal centrifuge (its ore recipes need 48 EU/t, i.e. the MV one)
    ('thermal_centrifuge', 'thermalcentrifuge', 'THERMAL_CENTRIFUGE', 'ThermalCentrifuge', 'thermal_centrifuge', 1, 3, 0, 'Thermal Centrifuge'),
    # D83: machines IIS 2 had no LV version of; GT_Loader_MetaTileEntities:685-686 extruder (2 in, 1 out)
    ('extruder',      'extruder',      'EXTRUDER',         'Extruder',     'extruder',      2, 1, 0,     'Extruder'),
    # :1039 forming press (2, 1), :1057 fluid heater (1, 0, 8000), :1102 electric oven (furnace recipes)
    ('forming_press', 'press',         'PRESS',            'Press',        'forming_press', 2, 1, 0,     'Forming Press'),
    ('fluid_heater',  'fluidheater',   'FLUID_HEATER',     'FluidHeater',  'fluid_heater',  1, 0, 8000,  'Fluid Heater'),
    ('electric_oven', 'e_oven',        'ELECTRIC_OVEN',    'E_Oven',       None,            1, 1, 0,     'Electric Oven'),
    # :1084-1085 arc furnace (1, 6, 16000; oxygen in the tank)
    ('arc_furnace',   'arcfurnace',    'ARC_FURNACE',      'ArcFurnace',   'arc_furnace',   1, 6, 16000, 'Arc Furnace'),
    # :1030 precision laser engraver (2, 1)
    ('laser_engraver', 'laserengraver', 'LASER_ENGRAVER',  'LaserEngraver', 'laser_engraver', 2, 1, 0,    'Precision Laser Engraver'),
]
# GT crafting of the D83 machines (both tiers, X.* resolved per tier like MV_CRAFT)
NEW_CRAFT = {
    'extruder': (['CCE', 'XMP', 'CCE'], {'M': 'HULL', 'X': 'PISTON', 'E': 'CIRCUIT', 'P': 'PIPE', 'C': 'COIL_HEATING_DOUBLE'}),
    'forming_press': (['WPW', 'CMC', 'WPW'], {'M': 'HULL', 'P': 'PISTON', 'C': 'CIRCUIT', 'W': 'WIRE'}),
    'fluid_heater': (['OGO', 'PMP', 'WCW'], {'M': 'HULL', 'P': 'PUMP', 'O': 'COIL_HEATING_DOUBLE', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS'}),
    'electric_oven': (['CEC', 'CMC', 'WEW'], {'M': 'HULL', 'E': 'CIRCUIT', 'W': 'WIRE', 'C': 'COIL_HEATING'}),
    'arc_furnace': (['WGW', 'CMC', 'PPP'], {'M': 'HULL', 'P': 'PLATE', 'C': 'CIRCUIT', 'W': 'WIRE4', 'G': '#c:dusts/graphite'}),
    'laser_engraver': (['PEP', 'CMC', 'WCW'], {'M': 'HULL', 'E': 'EMITTER', 'P': 'PISTON', 'C': 'CIRCUIT', 'W': 'WIRE'}),
}
# MV basic machines (GT tier 2, "Advanced ..."): same columns. GT_Loader_MetaTileEntities Machine_MV_*: the same inputs,
# outputs and tanks as LV except the mixer (16000 L, :1022); the compressor tank (LV 4000, MV 8000) stays 0 as at LV (D82)
MV_TANKS = {'mixer': 16000}
MV_MACHINES = [m[:7] + (MV_TANKS.get(m[0], m[7]),) + m[8:] for m in MACHINES if m[0] != 'thermal_centrifuge'] + [
    ('thermal_centrifuge', 'thermalcentrifuge', 'THERMAL_CENTRIFUGE', 'ThermalCentrifuge', 'thermal_centrifuge', 1, 3, 0, 'Thermal Centrifuge'),
]
# GT_Loader_MetaTileEntities Machine_MV_* crafting: GT pattern letters; X.* resolved for tier 2 in lv.generate
# (GT_MetaTileEntity_BasicMachine_GT_Recipe:57-330, Tier.ELECTRIC[2]); other values are literal ingredients
MV_CRAFT = {
    'macerator': (['PEG', 'WWM', 'CCW'], {'M': 'HULL', 'E': 'MOTOR', 'P': 'PISTON', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'INDUSTRIAL_DIAMOND'}),
    'electric_furnace': (['ECE', 'CMC', 'WCW'], {'M': 'HULL', 'E': 'CIRCUIT', 'W': 'WIRE', 'C': 'COIL_HEATING'}),
    'alloy_smelter': (['ECE', 'CMC', 'WCW'], {'M': 'HULL', 'E': 'CIRCUIT', 'W': 'WIRE', 'C': 'COIL_HEATING_DOUBLE'}),
    'compressor': ([' C ', 'PMP', 'WCW'], {'M': 'HULL', 'P': 'PISTON', 'C': 'CIRCUIT', 'W': 'WIRE'}),
    'extractor': (['GCG', 'EMP', 'WCW'], {'M': 'HULL', 'E': 'PISTON', 'P': 'PUMP', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS'}),
    'forge_hammer': (['WPW', 'CMC', 'WAW'], {'M': 'HULL', 'P': 'PISTON', 'C': 'CIRCUIT', 'W': 'WIRE', 'A': 'minecraft:anvil'}),
    'wiremill': (['EWE', 'CMC', 'EWE'], {'M': 'HULL', 'E': 'MOTOR', 'C': 'CIRCUIT', 'W': 'WIRE'}),
    'bender': (['PwP', 'CMC', 'EWE'], {'M': 'HULL', 'E': 'MOTOR', 'P': 'PISTON', 'C': 'CIRCUIT', 'W': 'WIRE'}),
    'lathe': (['WCW', 'EMD', 'CWP'], {'M': 'HULL', 'E': 'MOTOR', 'P': 'PISTON', 'C': 'CIRCUIT', 'W': 'WIRE', 'D': 'INDUSTRIAL_DIAMOND'}),
    'assembler': (['ACA', 'VMV', 'WCW'], {'M': 'HULL', 'V': 'CONVEYOR', 'A': 'ROBOT_ARM', 'C': 'CIRCUIT', 'W': 'WIRE'}),
    'mixer': (['GRG', 'GEG', 'CMC'], {'M': 'HULL', 'E': 'MOTOR', 'R': 'ROTOR', 'C': 'CIRCUIT', 'G': 'GLASS'}),
    'polarizer': (['ZSZ', 'WMW', 'ZSZ'], {'M': 'HULL', 'S': 'STICK_ELECTROMAGNETIC', 'Z': 'COIL_ELECTRIC', 'W': 'WIRE'}),
    'ore_washer': (['RGR', 'CEC', 'WMW'], {'M': 'HULL', 'R': 'ROTOR', 'E': 'MOTOR', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS'}),
    'centrifuge': (['CEC', 'WMW', 'CEC'], {'M': 'HULL', 'E': 'MOTOR', 'C': 'CIRCUIT', 'W': 'WIRE'}),
    'sifter': (['WFW', 'PMP', 'CFC'], {'M': 'HULL', 'P': 'PISTON', 'F': 'iis:item_filter', 'C': 'CIRCUIT', 'W': 'WIRE'}),
    'electromagnetic_separator': (['VWZ', 'WMS', 'CWZ'], {'M': 'HULL', 'S': 'STICK_ELECTROMAGNETIC', 'Z': 'COIL_ELECTRIC', 'V': 'CONVEYOR',
                                                          'C': 'CIRCUIT', 'W': 'WIRE'}),
    'autoclave': (['IGI', 'IMI', 'CPC'], {'M': 'HULL', 'P': 'PUMP', 'C': 'CIRCUIT', 'I': 'PLATE', 'G': 'GLASS'}),
    'cutter': (['WCG', 'VMB', 'CWE'], {'M': 'HULL', 'E': 'MOTOR', 'V': 'CONVEYOR', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS',
                                       'B': 'iis:diamond_sawblade'}),
    'canner': (['WPW', 'CMC', 'GGG'], {'M': 'HULL', 'P': 'PUMP', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS'}),
    'electrolyzer': (['IGI', 'IMI', 'CWC'], {'M': 'HULL', 'C': 'CIRCUIT', 'W': 'WIRE', 'I': 'SILVER_WIRE', 'G': 'GLASS'}),
    'chemical_reactor': (['GRG', 'WEW', 'CMC'], {'M': 'HULL', 'R': 'ROTOR', 'E': 'MOTOR', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS'}),
    'fluid_extractor': (['GCG', 'PME', 'WCW'], {'M': 'HULL', 'E': 'PISTON', 'P': 'PUMP', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS'}),
    'fluid_solidifier': (['PGP', 'WMW', 'CBC'], {'M': 'HULL', 'P': 'PUMP', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS', 'B': '#c:chests/wooden'}),
    'distillery': (['GBG', 'CMC', 'WPW'], {'M': 'HULL', 'P': 'PUMP', 'B': 'minecraft:blaze_rod', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS'}),
    'chemical_bath': (['VGW', 'PGV', 'CMC'], {'M': 'HULL', 'P': 'PUMP', 'V': 'CONVEYOR', 'C': 'CIRCUIT', 'W': 'WIRE', 'G': 'GLASS'}),
}
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
    # MV (D81): GT_MetaGenerated_Item_01:483-629, :695, :711
    ('electric_motor_mv', 'gt:601', 'gt.metaitem.01.32601.name', 'Electric Motor (MV)', 'Электродвигатель (MV)'),
    ('electric_pump_mv', 'gt:611', 'gt.metaitem.01.32611.name', 'Electric Pump (MV)', 'Электрическая помпа (MV)'),
    ('conveyor_module_mv', 'gt:631', 'gt.metaitem.01.32631.name', 'Conveyor Module (MV)', 'Конвейерный модуль (MV)'),
    ('electric_piston_mv', 'gt:641', 'gt.metaitem.01.32641.name', 'Electric Piston (MV)', 'Электропоршень (MV)'),
    ('robot_arm_mv', 'gt:651', 'gt.metaitem.01.32651.name', 'Robot Arm (MV)', 'Манипулятор (MV)'),
    # D86: GT_MetaGenerated_Item_01:656-657 emitters
    ('emitter_lv', 'gt:680', 'gt.metaitem.01.32680.name', 'Emitter (LV)', 'Излучатель (LV)'),
    ('emitter_mv', 'gt:681', 'gt.metaitem.01.32681.name', 'Emitter (MV)', 'Излучатель (MV)'),
    ('circuit_good', 'gt:702', 'gt.metaitem.01.32702.name', 'Good Electronic Circuit', 'Хорошая электросхема'),
    ('diode', 'gt:715', 'gt.metaitem.01.32715.name', 'Diode', 'Диод'),
    ('circuit_basic', 'gt:701', 'gt.metaitem.01.32701.name', 'Integrated Logic Circuit', 'Логическая интегральная электросхема'),
    ('vacuum_tube', 'gt:700', 'gt.metaitem.01.32700.name', 'Vacuum Tube', 'Вакуумная трубка'),
    ('coated_circuit_board', 'gt:710', 'gt.metaitem.01.32710.name', 'Coated Circuit Board', 'Покрытая печатная плата'),
    ('resistor', 'gt:716', 'gt.metaitem.01.32716.name', 'Resistor', 'Резистор'),
    ('glass_tube', 'gt:glass_tube', 'gt.metaitem.03.32012.name', 'Glass Tube', 'Стеклянная трубочка'),
    ('diamond_sawblade', 'gt:721', 'gt.metaitem.01.32721.name', 'Diamond Sawblade', 'Алмазный пильный диск'),
    ('item_filter', 'gt:729', 'gt.metaitem.01.32729.name', 'Item Filter', 'Фильтр'),
    ('battery_lv', 'gt:519', 'gt.metaitem.01.32519.name', 'Small Sodium Battery', 'Малая натриевая батарея'),
    ('battery_cadmium_lv', 'gt:517', 'gt.metaitem.01.32517.name', 'Small Cadmium Battery', 'Малая кадмиевая батарея'),
    ('battery_lithium_lv', 'gt:518', 'gt.metaitem.01.32518.name', 'Small Lithium Battery', 'Малая литиевая батарея'),
    ('battery_hull_lv', 'gt:500', 'gt.metaitem.01.32500.name', 'Small Battery Hull', 'Малый корпус батареи'),
    # covers (GT_MetaGenerated_Item_01:752-783)
    ('cover_controller', 'gt:730', 'gt.metaitem.01.32730.name', 'Machine Controller', 'Контроллер машины'),
    ('cover_activity_detector', 'gt:731', 'gt.metaitem.01.32731.name', 'Activity Detector', 'Детектор активности'),
    ('cover_fluid_detector', 'gt:732', 'gt.metaitem.01.32732.name', 'Fluid Detector', 'Детектор жидкости'),
    ('cover_item_detector', 'gt:733', 'gt.metaitem.01.32733.name', 'Item Detector', 'Детектор предметов'),
    ('cover_energy_detector', 'gt:734', 'gt.metaitem.01.32734.name', 'Energy Detector', 'Детектор энергии'),
    ('cover_crafting', 'gt:744', 'gt.metaitem.01.32744.name', 'Crafting Table Cover', 'Покрытие-верстак'),
    ('cover_drain', 'gt:745', 'gt.metaitem.01.32745.name', 'Drain', 'Сток'),
    ('cover_shutter', 'gt:749', 'gt.metaitem.01.32749.name', 'Shutter Module', 'Модуль заслонки'),
    # GT_MetaGenerated_Item_01:519, :531 (D80)
    ('fluid_regulator_lv', 'gt:615', 'gt.metaitem.01.32615.name', 'Fluid Regulator (LV)', 'Регулятор жидкости (LV)'),
    ('cover_fluid_filter', 'gt:635', 'gt.metaitem.01.32635.name', 'Fluid Filter', 'Фильтр жидкости'),
    ('steel_item_casing', 'gen:casing', None, 'Steel Item Casing', 'Стальная оболочка'),
    ('sticky_resin', 'gen:resin', None, 'Sticky Resin', 'Липкая смола'),
    ('coal_coke', 'gen:coke', None, 'Coal Coke', 'Коксовый уголь'),
    ('wet_coke_oven_brick', 'gen:wet_brick', None, 'Wet Coke Oven Brick', 'Сырой кирпич коксовой печи'),
    ('treetap', 'gen:treetap', None, 'Treetap', 'Краник'),
    ('multimeter', 'gen:multimeter', None, 'Multimeter', 'Мультиметр'),
    ('plate_wood', 'gen:wood_plate', 'gt.metaitem.01.17809.name', 'Wood Plate', 'Деревянная пластина'),
    # LV III: molds of the fluid solidifier (GT_MetaGenerated_Item_01:179-212, D76)
    ('shape_empty', 'gt:300', 'gt.metaitem.01.32300.name', 'Empty Shape Plate', 'Пустая заготовка формы'),
    ('mold_plate', 'gt:301', 'gt.metaitem.01.32301.name', 'Mold (Plate)', 'Форма (пластина)'),
    ('mold_gear', 'gt:303', 'gt.metaitem.01.32303.name', 'Mold (Gear)', 'Форма (шестерня)'),
    ('mold_ingot', 'gt:306', 'gt.metaitem.01.32306.name', 'Mold (Ingot)', 'Форма (слиток)'),
    ('mold_ball', 'gt:307', 'gt.metaitem.01.32307.name', 'Mold (Ball)', 'Форма (шар)'),
    ('mold_block', 'gt:308', 'gt.metaitem.01.32308.name', 'Mold (Block)', 'Форма (блок)'),
    ('mold_nugget', 'gt:309', 'gt.metaitem.01.32309.name', 'Mold (Nuggets)', 'Форма (самородки)'),
    # D83: extruder shapes (GT_MetaGenerated_Item_01:222-246; hammer and file heads are not items here) and the
    # alloy smelter molds of ProcessingShaping
    ('shape_extruder_plate', 'gt:350', 'gt.metaitem.01.32350.name', 'Extruder Shape (Plate)', 'Насадка экструдера (пластина)'),
    ('shape_extruder_rod', 'gt:351', 'gt.metaitem.01.32351.name', 'Extruder Shape (Rod)', 'Насадка экструдера (стержень)'),
    ('shape_extruder_bolt', 'gt:352', 'gt.metaitem.01.32352.name', 'Extruder Shape (Bolt)', 'Насадка экструдера (болт)'),
    ('shape_extruder_ring', 'gt:353', 'gt.metaitem.01.32353.name', 'Extruder Shape (Ring)', 'Насадка экструдера (кольцо)'),
    ('shape_extruder_cell', 'gt:354', 'gt.metaitem.01.32354.name', 'Extruder Shape (Cell)', 'Насадка экструдера (капсула)'),
    ('shape_extruder_ingot', 'gt:355', 'gt.metaitem.01.32355.name', 'Extruder Shape (Ingot)', 'Насадка экструдера (слиток)'),
    ('shape_extruder_wire', 'gt:356', 'gt.metaitem.01.32356.name', 'Extruder Shape (Wire)', 'Насадка экструдера (провод)'),
    ('shape_extruder_casing', 'gt:357', 'gt.metaitem.01.32357.name', 'Extruder Shape (Casing)', 'Насадка экструдера (оболочка)'),
    ('shape_extruder_pipe_tiny', 'gt:358', 'gt.metaitem.01.32358.name', 'Extruder Shape (Tiny Pipe)', 'Насадка экструдера (крошечная труба)'),
    ('shape_extruder_pipe_small', 'gt:359', 'gt.metaitem.01.32359.name', 'Extruder Shape (Small Pipe)', 'Насадка экструдера (малая труба)'),
    ('shape_extruder_pipe_medium', 'gt:360', 'gt.metaitem.01.32360.name', 'Extruder Shape (Normal Pipe)', 'Насадка экструдера (труба)'),
    ('shape_extruder_pipe_large', 'gt:361', 'gt.metaitem.01.32361.name', 'Extruder Shape (Large Pipe)', 'Насадка экструдера (большая труба)'),
    ('shape_extruder_pipe_huge', 'gt:362', 'gt.metaitem.01.32362.name', 'Extruder Shape (Huge Pipe)', 'Насадка экструдера (огромная труба)'),
    ('shape_extruder_block', 'gt:363', 'gt.metaitem.01.32363.name', 'Extruder Shape (Block)', 'Насадка экструдера (блок)'),
    ('shape_extruder_sword', 'gt:364', 'gt.metaitem.01.32364.name', 'Extruder Shape (Sword Blade)', 'Насадка экструдера (клинок меча)'),
    ('shape_extruder_pickaxe', 'gt:365', 'gt.metaitem.01.32365.name', 'Extruder Shape (Pickaxe Head)', 'Насадка экструдера (оголовье кирки)'),
    ('shape_extruder_shovel', 'gt:366', 'gt.metaitem.01.32366.name', 'Extruder Shape (Shovel Head)', 'Насадка экструдера (оголовье лопаты)'),
    ('shape_extruder_axe', 'gt:367', 'gt.metaitem.01.32367.name', 'Extruder Shape (Axe Head)', 'Насадка экструдера (оголовье топора)'),
    ('shape_extruder_hoe', 'gt:368', 'gt.metaitem.01.32368.name', 'Extruder Shape (Hoe Head)', 'Насадка экструдера (оголовье мотыги)'),
    ('shape_extruder_saw', 'gt:371', 'gt.metaitem.01.32371.name', 'Extruder Shape (Saw Blade)', 'Насадка экструдера (полотно пилы)'),
    ('shape_extruder_gear', 'gt:372', 'gt.metaitem.01.32372.name', 'Extruder Shape (Gear)', 'Насадка экструдера (шестерня)'),
    ('shape_extruder_bottle', 'gt:373', 'gt.metaitem.01.32373.name', 'Extruder Shape (Bottle)', 'Насадка экструдера (бутылка)'),
    ('shape_extruder_small_gear', 'gt:374', 'gt.metaitem.01.32374.name', 'Extruder Shape (Small Gear)', 'Насадка экструдера (малая шестерня)'),
    ('mold_casing', 'gt:302', 'gt.metaitem.01.32302.name', 'Mold (Casing)', 'Форма (оболочка)'),
    ('mold_bottle', 'gt:305', 'gt.metaitem.01.32305.name', 'Mold (Bottle)', 'Форма (бутылка)'),
    ('mold_anvil', 'gt:314', 'gt.metaitem.01.32314.name', 'Mold (Anvil)', 'Форма (наковальня)'),
    # forming press (D83): GT_MetaGenerated_Item_01:186, :198 molds, :66 doge coin, :129 empty glass arrow head
    ('mold_credit', 'gt:304', 'gt.metaitem.01.32304.name', 'Mold (Coinage)', 'Форма (чеканка)'),
    ('mold_arrow', 'gt:316', 'gt.metaitem.01.32316.name', 'Mold (Arrow Head)', 'Форма (наконечник стрелы)'),
    ('coin_doge', 'gt:9', 'gt.metaitem.01.32009.name', 'Doge Coin', 'Монета Doge'),
    ('arrow_head_glass_empty', 'gt:200', 'gt.metaitem.01.32200.name', 'Empty Glass Arrow Head', 'Пустой стеклянный наконечник стрелы'),
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
    # cover faces (GT registerCover textures: casing + overlay, first animation frame)
    for cid, casing, ov in (('conveyor', 'LV', 'CONVEYOR'), ('pump', 'LV', 'PUMP'), ('arm', 'LV', 'ARM'), ('shutter', 'LV', 'SHUTTER'),
                            ('controller', 'MV', 'CONTROLLER'), ('activity_detector', 'MV', 'ACTIVITYDETECTOR'),
                            ('item_detector', 'MV', 'ITEMDETECTOR'), ('fluid_detector', 'MV', 'FLUIDDETECTOR'),
                            ('energy_detector', 'MV', 'ENERGYDETECTOR'), ('drain', 'LV', 'DRAIN'), ('crafting', 'LV', 'CRAFTING'),
                            # GT_MetaGenerated_Item_01:525, :532: LV casing + pump / shutter overlay
                            ('fluid_regulator', 'LV', 'PUMP'), ('fluid_filter', 'LV', 'SHUTTER')):
        icd = os.path.join(LV, 'iconsets')
        im, _ = layer(os.path.join(icd, f'MACHINE_{casing}_SIDE.png'), os.path.join(icd, f'OVERLAY_{ov}.png'))
        im.crop((0, 0, 16, 16)).save(tex_out(f'block/cover/{cid}.png'))
    en_int = {}
    for line in open(os.path.join(g['ROOT'], 'tools', 'gen', 'lang', 'gt5_interaction_en.txt'), encoding='utf-8'):
        k, _, v = line.rstrip('\n').partition('=')
        en_int[k] = v
    for i in list(range(1, 56)) + list(range(82, 86)):
        k = f'Interaction_DESCRIPTION_Index_{i:03d}'
        name(f'cover.{NS}.mode.{i:03d}', en_int.get(k, k), ru(k, en_int.get(k, k)))
    name(f'cover.{NS}.all_slots', 'all', 'все')
    # GT_Cover_Fluidfilter modes 4-7: in GT5-II @ 00021e3 their keys (Index_219-222) point to unrelated lines, so the text is ours
    for mode, en, ruu in ((4, 'Deny input, filter output', 'Запрет входа, выход только выбранной'),
                          (5, 'Deny input, invert output', 'Запрет входа, выход всех, кроме выбранной'),
                          (6, 'Permit any input, filter output', 'Любой вход, выход только выбранной'),
                          (7, 'Permit any input, invert output', 'Любой вход, выход всех, кроме выбранной')):
        name(f'cover.{NS}.fluid_filter.mode.{mode}', en, ruu)
    for i, (en, ruu) in enumerate((('Cover: right-click a side of a machine, pipe or hatch (not the front)', 'Покрытие: ПКМ по стороне машины, трубы или люка (не по лицевой)'),
                                   ('Screwdriver: change mode (Shift: back)', 'Отвёртка: сменить режим (Shift — назад)'),
                                   ('Crowbar: remove the cover', 'Монтировка: снять покрытие'))):
        name(f'cover.{NS}.tooltip.{i}', en, ruu)
    name(f'message.{NS}.cover.attached', 'Cover attached: %s', 'Покрытие установлено: %s')
    item_ref[('plate', 'Wood')] = f'{NS}:plate_wood'
    add_item_tag('c:plates/wood', f'{NS}:plate_wood')
    add_item_tag('c:circuits/basic', f'{NS}:circuit_basic')   # GT OrePrefixes.circuit Basic
    add_item_tag('c:circuits/good', f'{NS}:circuit_good')     # GT OrePrefixes.circuit Good (D81)
    name(f'item.{NS}.battery_lv.tooltip', 'Stores %s / %s EU', 'Запас: %s / %s EU')

    # creosote (Railcraft coke oven byproduct; GT Materials.Creosote, FuelType 3 = semifluid generator)
    name(f'fluid_type.{NS}.creosote', 'Creosote Oil', ru('fluid.creosote', 'Креозотовое масло'))
    name(f'item.{NS}.creosote_bucket', 'Creosote Bucket', 'Ведро креозотового масла')
    cre = noise_tex((90, 60, 20), 8, 'creosote')
    frames = Image.new('RGBA', (16, 16 * 8))
    for f in range(8):
        frames.paste(noise_tex((90, 60, 20), 10, f'creosote{f}'), (0, 16 * f))
    frames.save(tex_out('block/fluid/creosote.png'))
    with open(tex_out('block/fluid/creosote.png.mcmeta'), 'w', newline=chr(10)) as fh:
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
        for size, bare, ins, ring_name in WIRE_SIZES:
            for insulated in (False, True):
                if insulated and ins is None:
                    continue
                bid = f'{"cable" if insulated else "wire"}_{mid}_{size}x'
                thick = (ins if insulated else bare) * 16
                lo, hi = (16 - thick) / 2, (16 + thick) / 2
                side = f'{NS}:block/cable/insulation' if insulated else f'{NS}:block/cable/wire_{mid}'
                end = f'{NS}:block/cable/{"cable" if insulated else "wire"}_{mid}_{size}x_end' if insulated else side
                if insulated:
                    ring = os.path.join(LV, 'iconsets', ring_name + '.png')
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
                tooltip_lines(bid, [(f'Max Voltage: {volts} EU/t ({VOLTAGE_NAMES[volts]})',
                                     f'Макс. напряжение: {volts} EU/t ({VOLTAGE_NAMES[volts]})'),
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

    # wire recipes: ProcessingWire:42-89 (plate + wire cutter -> 1x; joining and splitting by hand; insulation) and wiremill
    for mat, *_ in WIRES:
        mid = mat_id(mat)
        if ref('plate', mat):
            shaped(f'wire/{mid}_1x', f'{NS}:wire_{mid}_1x', 1, ['Px'], {'P': tag_of('plate', mat)})
        shapeless(f'wire/{mid}_2x', f'{NS}:wire_{mid}_2x', 1, [W(mat), W(mat)])
        shapeless(f'wire/{mid}_4x', f'{NS}:wire_{mid}_4x', 1, [W(mat, 2), W(mat, 2)])
        shapeless(f'wire/{mid}_4x_from_1x', f'{NS}:wire_{mid}_4x', 1, [W(mat)] * 4)
        shapeless(f'wire/{mid}_8x', f'{NS}:wire_{mid}_8x', 1, [W(mat, 4), W(mat, 4)])
        shapeless(f'wire/{mid}_8x_from_1x', f'{NS}:wire_{mid}_8x', 1, [W(mat)] * 8)
        shapeless(f'wire/{mid}_12x', f'{NS}:wire_{mid}_12x', 1, [W(mat, 8), W(mat, 4)])
        shapeless(f'wire/{mid}_16x', f'{NS}:wire_{mid}_16x', 1, [W(mat, 8), W(mat, 8)])
        shapeless(f'wire/{mid}_16x_from_12x', f'{NS}:wire_{mid}_16x', 1, [W(mat, 12), W(mat, 4)])
        for size in (2, 4, 8, 12, 16):
            shapeless(f'wire/{mid}_{size}x_split', f'{NS}:wire_{mid}_1x', size, [W(mat, size)])
        for size, _b, ins, _r in WIRE_SIZES:
            if ins is None:
                continue
            cost = size // 4 + 1   # ProcessingWire: costMultiplier = cableWidth / 4 + 1
            if mat in WIRE_PAPER:
                shapeless(f'cable/{mid}_{size}x', f'{NS}:cable_{mid}_{size}x', 1, [W(mat, size)] + ['minecraft:paper'] * cost)
            if mat in WIRE_CARPET:   # black carpet + string
                shapeless(f'cable/{mid}_{size}x' + ('_carpet' if mat in WIRE_PAPER else ''), f'{NS}:cable_{mid}_{size}x', 1,
                          [W(mat, size)] + ['minecraft:black_carpet'] * cost + ['minecraft:string'])
            # rubber group: assembler with molten rubber (below, with the machine recipes)

    # --------------------------------------------------------------------------------------------------------
    # Machine casing, hull (GT_Loader_MetaTileEntities: Casing_LV "PPP/PwP/PPP", HardMachineCasings Hull_LV "PHP/CMC")
    # --------------------------------------------------------------------------------------------------------
    ic = os.path.join(LV, 'iconsets')
    for f in ('MACHINE_LV_SIDE', 'MACHINE_LV_TOP', 'MACHINE_LV_BOTTOM', 'MACHINE_MV_SIDE', 'MACHINE_MV_TOP', 'MACHINE_MV_BOTTOM'):
        img(os.path.join(ic, f + '.png')).crop((0, 0, 16, 16)).save(tex_out(f'block/lv/{f.lower()}.png'))
    for bid, en, key in (('casing_lv', 'LV Machine Casing', 'gt.blockcasings.1.name'), ('hull_lv', 'LV Machine Hull', 'gt.blockmachines.hull.tier.01.name'),
                         ('casing_mv', 'MV Machine Casing', 'gt.blockcasings.2.name'), ('hull_mv', 'MV Machine Hull', 'gt.blockmachines.hull.tier.02.name')):
        tr = bid[-2:]
        side, top, bottom = (f'{NS}:block/lv/machine_{tr}_{x}' for x in ('side', 'top', 'bottom'))
        cube_model(bid, {'front': side, 'back': side, 'side': side, 'top': top, 'bottom': bottom})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        block_item(bid)
        simple_loot(bid)
        pickaxe(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
    shaped('lv/casing_lv', f'{NS}:casing_lv', 1, ['PPP', 'PwP', 'PPP'], {'P': tag_of('plate', 'Steel')})
    shaped('lv/hull_lv', f'{NS}:hull_lv', 1, ['PHP', 'CMC'],
           {'P': tag_of('plate', 'WroughtIron'), 'H': tag_of('plate', 'Steel'), 'C': C('Tin'), 'M': f'{NS}:casing_lv'})
    # GT_Loader_MetaTileEntities:53 Casing_MV (aluminium), :173 Hull_MV "PHP/CMC" (copper cable, aluminium + wrought iron plates)
    shaped('lv/casing_mv', f'{NS}:casing_mv', 1, ['PPP', 'PwP', 'PPP'], {'P': tag_of('plate', 'Aluminium')})
    shaped('lv/hull_mv', f'{NS}:hull_mv', 1, ['PHP', 'CMC'],
           {'P': tag_of('plate', 'WroughtIron'), 'H': tag_of('plate', 'Aluminium'), 'C': C('Copper'), 'M': f'{NS}:casing_mv'})

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
    for (mid_, gtn, ov, gui, rmap, nin, nout, tank, en), tr in [(m, 'lv') for m in MACHINES] + [(m, 'mv') for m in MV_MACHINES]:
        bid = f'{mid_}_{tr}'
        odir = os.path.join(LV, 'basicmachines', ov.lower())
        T = tr.upper()

        def ovf(part):
            return lambda active: os.path.join(odir, f'OVERLAY_{part}{"_ACTIVE" if active else ""}.png')
        machine_tex(bid, odir, {
            'front': (f'MACHINE_{T}_SIDE', ovf('FRONT')), 'side': (f'MACHINE_{T}_SIDE', ovf('SIDE')),
            'back': (f'MACHINE_{T}_SIDE', os.path.join(ic, 'OVERLAY_PIPE_OUT.png')),
            'top': (f'MACHINE_{T}_TOP', ovf('TOP')), 'bottom': (f'MACHINE_{T}_BOTTOM', ovf('BOTTOM'))})
        shutil.copy(os.path.join(LV, 'gui', gui + '.png'), tex_out(f'gui/lv/{gui.lower()}.png'))
        prefix, tier_n, volts = ('Basic', '01', 32) if tr == 'lv' else ('Advanced', '02', 128)
        name(f'block.{NS}.{bid}', f'{prefix} {en}', ru(f'gt.blockmachines.basicmachine.{gtn}.tier.{tier_n}.name', f'{prefix} {en}'))
        tooltip_lines(bid, [(f'Voltage IN: {volts} EU/t ({T})', f'Входное напряжение: {volts} EU/t ({T})'),
                            ('Output side: back', 'Выход: задняя сторона')])
        machine_list.append({'id': bid, 'type': mid_ if tr == 'lv' else f'{mid_}_{tr}', 'recipeMap': rmap, 'inputs': nin, 'outputs': nout,
                             'tank': tank, 'gui': f'lv/{gui.lower()}'})
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
    # GT_MetaTileEntity_Transformer: step-down (default) — front = higher tier in (IN_MULTI), other sides lower tier out (OUT);
    # step-up — front = higher tier out (OUT_MULTI), other sides lower tier in (IN). Facing: any of 6 directions. Casing of mTier.
    for tr, lo, hi, n_ in (('lv', 32, 128, '01'), ('mv', 128, 512, '02')):
        bid, T, LO, HI = f'transformer_{tr}', tr.upper(), VOLTAGE_NAMES[lo], VOLTAGE_NAMES[hi]
        for mode, front_ov, side_ov in (('', 'OVERLAY_ENERGY_IN_MULTI', 'OVERLAY_ENERGY_OUT'), ('_up', 'OVERLAY_ENERGY_OUT_MULTI', 'OVERLAY_ENERGY_IN')):
            for face, base, ov in (('front', f'MACHINE_{T}_SIDE', front_ov), ('side', f'MACHINE_{T}_SIDE', side_ov),
                                   ('top', f'MACHINE_{T}_TOP', side_ov), ('bottom', f'MACHINE_{T}_BOTTOM', side_ov)):
                im, _ = layer(os.path.join(ic, base + '.png'), os.path.join(ic, ov + '.png'))
                im.crop((0, 0, 16, 16)).save(tex_out(f'block/lv/{bid}{mode}_{face}.png'))
            t = lambda f: f'{NS}:block/lv/{bid}{mode}_{f}'
            cube_model(bid + mode, {'front': t('front'), 'back': t('side'), 'side': t('side'), 'top': t('top'), 'bottom': t('bottom')})
        variants = {}
        for facing, rot in (('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}), ('down', {'x': 90}), ('up', {'x': 270})):
            for up in (False, True):
                variants[f'facing={facing},step_up={str(up).lower()}'] = dict({'model': f'{NS}:block/{bid}' + ('_up' if up else '')}, **rot)
        write(os.path.join(A, 'blockstates', f'{bid}.json'), {'variants': variants})
        block_item(bid)
        simple_loot(bid)
        pickaxe(bid)
        name(f'block.{NS}.{bid}', f'{LO} Transformer', ru(f'gt.blockmachines.transformer.tier.{n_}.name', f'{LO} Transformer'))
        tooltip_lines(bid, [(f'{HI} -> {LO}: input {hi} EU/t 1 A at the front, output {lo} EU/t 4 A at the other sides',
                             f'{HI} -> {LO}: вход {hi} EU/t 1 А спереди, выход {lo} EU/t 4 А с остальных сторон'),
                            (f'Wrench + sneak: invert ({LO} -> {HI})', f'Ключ с Shift: обратить ({LO} -> {HI})')])

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
    # IC2: rubber wood -> 3 planks; GT ProcessingLog:84 with WoodNeedsSawForCrafting (D87): by hand 3 / 2 = 1, with a saw 3
    shapeless('lv/rubber_log_planks', 'minecraft:jungle_planks', 1, [f'{NS}:rubber_log'])
    shaped('lv/rubber_log_planks_saw', 'minecraft:jungle_planks', 3, ['s', 'L'], {'L': f'{NS}:rubber_log'})
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
    # battery: GT canning (hull + 2 dust); the old own crafting recipe was removed when sodium/lithium/cadmium became available

    # LV machines (GT_Loader_MetaTileEntities, X.* placeholders resolved for LV)
    X = {'M': HULL, 'E': MOTOR, 'P': PISTON, 'C': CIRC, 'W': C('Tin'), 'V': CONV, 'A': ARM, 'U': PUMP,
         'H': W('Copper', 2), 'O': W('Copper', 4), 'R': tag_of('rotor', 'Tin'), 'G': 'minecraft:glass', 'Z': W('Tin', 2), 'S': R('Iron'),
         'D': 'minecraft:diamond', 'N': 'minecraft:anvil',
         'B': f'{NS}:diamond_sawblade', 'F': f'{NS}:item_filter', 'I': P('Steel'), 'Y': W('Tin', 2)}

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
    # LV II: GT_Loader_MetaTileEntities:766 centrifuge, 1075 sifter (P = piston), 1003 separator (S = iron rod, Z = 2x tin wire),
    # 1012 autoclave (I = steel plate), 658 cutter (B = diamond sawblade)
    mcraft('centrifuge_lv', ['CEC', 'WMW', 'CEC'], 'CEWM')
    mcraft('sifter_lv', ['WFW', 'PMP', 'CFC'], 'WFPMC')
    shaped('lv/electromagnetic_separator_lv', f'{NS}:electromagnetic_separator_lv', 1, ['VWZ', 'WMS', 'CWZ'],
           {'V': CONV, 'W': C('Tin'), 'Z': W('Tin', 2), 'M': HULL, 'S': R('Iron'), 'C': CIRC})
    mcraft('autoclave_lv', ['IGI', 'IMI', 'CUC'], 'IGMCU')
    mcraft('cutter_lv', ['WCG', 'VMB', 'CWE'], 'WCGVMBE')
    mcraft('canner_lv', ['WUW', 'CMC', 'GGG'], 'WUCMG')   # GT_Loader_MetaTileEntities:640 aTextWirePump/aTextCableHull/GGG
    # LV III (D76): :775 electrolyzer "IGI/IMI/CWC" (I = 1x gold wire), :830 chemical reactor "GRG/WEW/CMC",
    # :958 fluid extractor "GCG/PME/WCW", :967 fluid solidifier "PGP/WMW/CBC" (P = pump, B = chest)
    shaped('lv/electrolyzer_lv', f'{NS}:electrolyzer_lv', 1, ['IGI', 'IMI', 'CWC'], {'I': W('Gold'), 'G': 'minecraft:glass', 'M': HULL, 'C': CIRC, 'W': C('Tin')})
    mcraft('chemical_reactor_lv', ['GRG', 'WEW', 'CMC'], 'GRWECM')
    # LV IV: :976 distillery "GBG/CMC/WPW" (B = X.STICK_DISTILLATION = blaze rod), :985 chemical bath "VGW/PGV/CMC"
    shaped('lv/distillery_lv', f'{NS}:distillery_lv', 1, ['GBG', 'CMC', 'WPW'], {'G': 'minecraft:glass', 'B': 'minecraft:blaze_rod', 'C': CIRC,
                                                                            'M': HULL, 'W': C('Tin'), 'P': PUMP})
    shaped('lv/chemical_bath_lv', f'{NS}:chemical_bath_lv', 1, ['VGW', 'PGV', 'CMC'], {'V': CONV, 'G': 'minecraft:glass', 'W': C('Tin'), 'P': PUMP,
                                                                                  'C': CIRC, 'M': HULL})
    # LV thermal centrifuge: GT_Loader_MetaTileEntities:784 "CEC/OMO/WEW" (O = X.COIL_HEATING_DOUBLE = 4x copper wire at LV)
    mcraft('thermal_centrifuge_lv', ['CEC', 'OMO', 'WEW'], 'CEOMW')

    # --- MV (D81) ------------------------------------------------------------------------------------------------
    MOTOR_MV, PISTON_MV, PUMP_MV, CONV_MV, ARM_MV = (f'{NS}:{x}' for x in ('electric_motor_mv', 'electric_piston_mv', 'electric_pump_mv',
                                                                           'conveyor_module_mv', 'robot_arm_mv'))
    HULL_MV, CIRC_MV = f'{NS}:hull_mv', '#c:circuits/good'
    # GT_MetaGenerated_Item_01:495 motor, :550 pump, :577 conveyor, :611 piston, :629 robot arm (MV columns)
    shaped('lv/electric_motor_mv', MOTOR_MV, 1, ['CWR', 'WIW', 'RWC'], {'I': R('SteelMagnetic'), 'R': R('Aluminium'), 'W': W('Copper', 2), 'C': C('Copper')})
    shaped('lv/electric_pump_mv', PUMP_MV, 1, ['SXO', 'dPw', 'OMW'], {'M': MOTOR_MV, 'O': tag_of('ring', 'Rubber'), 'X': tag_of('rotor', 'Bronze'),
                                                                     'S': tag_of('screw', 'Bronze'), 'W': C('Copper'), 'P': f'{NS}:pipe_medium_steel'})
    shaped('lv/conveyor_module_mv', CONV_MV, 1, ['RRR', 'MCM', 'RRR'], {'M': MOTOR_MV, 'C': C('Copper'), 'R': P('Rubber')})
    shaped('lv/electric_piston_mv', PISTON_MV, 1, ['PPP', 'CSS', 'CMG'], {'P': P('Aluminium'), 'S': R('Aluminium'), 'G': tag_of('small_gear', 'Aluminium'),
                                                                         'M': MOTOR_MV, 'C': C('Copper')})
    shaped('lv/robot_arm_mv', ARM_MV, 1, ['CCC', 'MSM', 'PES'], {'S': R('Aluminium'), 'M': MOTOR_MV, 'P': PISTON_MV, 'E': CIRC_MV, 'C': C('Copper')})
    # GT_MachineRecipeLoader:537 good circuit "IVC/VDV/CVI": V = IC2 electronic circuit (here the basic circuit, D81),
    # I = IC2 steel item casing; :475-478 diode "BG /WDW/BG ": 4 diodes from a tiny pile of gallium (silicon wafer: later)
    shaped('lv/circuit_good', f'{NS}:circuit_good', 1, ['IVC', 'VDV', 'CVI'], {'D': f'{NS}:diode', 'C': C('RedAlloy'), 'V': CIRC,
                                                                              'I': f'{NS}:steel_item_casing'})
    for wid, wire in (('wire', W('Tin')), ('fine_wire', tag_of('wire_fine', 'Tin'))):
        shaped(f'lv/diode_{wid}', f'{NS}:diode', 4, ['BG ', 'WDW', 'BG '], {'B': '#c:dyes/black', 'G': 'minecraft:glass_pane',
                                                                           'D': tag_of('tiny_dust', 'Gallium'), 'W': wire})
    # MV machines (D82): GT X.* for tier 2. Industrial diamond = IC2 item, GT falls back to a diamond without IC2
    # (GT_Proxy:393); the MV electrolyzer takes 1x silver wire instead of gold (GT_Loader_MetaTileEntities:776)
    XMV = {'HULL': HULL_MV, 'CIRCUIT': CIRC_MV, 'WIRE': C('Copper'), 'GLASS': 'minecraft:glass', 'PLATE': P('Aluminium'),
           'COIL_HEATING': W('Cupronickel', 2), 'COIL_HEATING_DOUBLE': W('Cupronickel', 4), 'STICK_ELECTROMAGNETIC': R('Steel'),
           'COIL_ELECTRIC': W('Copper', 2), 'ROTOR': tag_of('rotor', 'Bronze'), 'MOTOR': MOTOR_MV, 'PISTON': PISTON_MV,
           'PUMP': PUMP_MV, 'CONVEYOR': CONV_MV, 'ROBOT_ARM': ARM_MV, 'INDUSTRIAL_DIAMOND': 'minecraft:diamond', 'SILVER_WIRE': W('Silver')}
    MV_CRAFT.update(NEW_CRAFT)
    XMV['PIPE'] = f'{NS}:pipe_medium_steel'
    XMV['WIRE4'] = C('Copper', 4)
    XMV['EMITTER'] = f'{NS}:emitter_mv'
    # GT_MetaGenerated_Item_01:665, :668 emitters "SSC/WQS/CWS": quartzite / nether quartz, brass / electrum rods, circuit, cable
    shaped('lv/emitter_lv', f'{NS}:emitter_lv', 1, ['SSC', 'WQS', 'CWS'], {'Q': tag_of('gem', 'Quartzite'), 'S': R('Brass'), 'C': CIRC, 'W': C('Tin')})
    shaped('lv/emitter_mv', f'{NS}:emitter_mv', 1, ['SSC', 'WQS', 'CWS'], {'Q': 'minecraft:quartz', 'S': R('Electrum'), 'C': CIRC_MV, 'W': C('Copper')})
    # LV versions of the D83 machines: X.* for tier 1 (Tier.ELECTRIC[1], GT_MetaTileEntity_BasicMachine_GT_Recipe:57-330)
    XLV = {'HULL': HULL, 'CIRCUIT': CIRC, 'WIRE': C('Tin'), 'GLASS': 'minecraft:glass', 'PLATE': P('Steel'), 'PIPE': f'{NS}:pipe_medium_bronze',
           'COIL_HEATING': W('Copper', 2), 'COIL_HEATING_DOUBLE': W('Copper', 4), 'MOTOR': MOTOR, 'PISTON': PISTON, 'PUMP': PUMP,
           'CONVEYOR': CONV, 'ROBOT_ARM': ARM, 'WIRE4': C('Tin', 4), 'EMITTER': f'{NS}:emitter_lv'}
    for mid_, (pattern, letters) in NEW_CRAFT.items():
        shaped(f'lv/{mid_}_lv', f'{NS}:{mid_}_lv', 1, pattern, {k: XLV.get(v, v) for k, v in letters.items()})
    # GT_MetaGenerated_Item_01:204-254 extruder shapes (wire cutter) and molds (hammer) from the empty shape
    for sid, pat in (
        ('shape_extruder_plate', ['   ', 'xP ', '   ']),
        ('shape_extruder_rod', ['   ', ' P ', '  x']),
        ('shape_extruder_bolt', ['x  ', ' P ', '   ']),
        ('shape_extruder_ring', ['   ', ' Px', '   ']),
        ('shape_extruder_cell', [' x ', ' P ', '   ']),
        ('shape_extruder_ingot', ['  x', ' P ', '   ']),
        ('shape_extruder_wire', ['   ', ' P ', ' x ']),
        ('shape_extruder_casing', ['   ', ' P ', 'x  ']),
        ('shape_extruder_pipe_tiny', [' x ', '   ', 'P  ']),
        ('shape_extruder_pipe_small', ['P  ', '  x', '   ']),
        ('shape_extruder_pipe_medium', ['P  ', '   ', ' x ']),
        ('shape_extruder_pipe_large', ['P  ', '   ', '  x']),
        ('shape_extruder_pipe_huge', ['  x', '   ', 'P  ']),
        ('shape_extruder_block', ['P x', '   ', '   ']),
        ('shape_extruder_sword', ['  P', '   ', '  x']),
        ('shape_extruder_pickaxe', ['  P', '   ', ' x ']),
        ('shape_extruder_shovel', ['  P', '   ', 'x  ']),
        ('shape_extruder_axe', ['  P', 'x  ', '   ']),
        ('shape_extruder_hoe', ['   ', '   ', 'x P']),
        ('shape_extruder_saw', [' x ', '   ', '  P']),
        ('shape_extruder_gear', ['x  ', '   ', 'P  ']),
        ('shape_extruder_bottle', ['   ', '  x', 'P  ']),
        ('shape_extruder_small_gear', ['   ', 'x  ', 'P  ']),
        ('mold_casing', ['  h', ' P ', '   ']),
        ('mold_bottle', ['   ', ' P ', '  h']),
        ('mold_anvil', ['  P', '   ', ' h ']),
        ('mold_credit', ['h  ', ' P ', '   ']),
        ('mold_arrow', ['  P', 'h  ', '   ']),
    ):
        shaped(f'lv/{sid}', f'{NS}:{sid}', 1, pat, {'P': f'{NS}:shape_empty'})
    MV_CRAFT['thermal_centrifuge'] = (['CEC', 'OMO', 'WEW'], {'M': 'HULL', 'E': 'MOTOR', 'C': 'CIRCUIT', 'W': 'WIRE', 'O': 'COIL_HEATING_DOUBLE'})
    for mid_, (pattern, letters) in MV_CRAFT.items():
        shaped(f'lv/{mid_}_mv', f'{NS}:{mid_}_mv', 1, pattern, {k: XMV.get(v, v) for k, v in letters.items()})
    # GT_Loader_MetaTileEntities:206 MV transformer " BB/CM / BB": MV hull, gold cable, copper cables
    shaped('lv/transformer_mv', f'{NS}:transformer_mv', 1, [' BB', 'CM ', ' BB'], {'M': HULL_MV, 'C': C('Gold'), 'B': C('Copper')})

    # GT_MetaGenerated_Item_01:181 empty shape "hf/PP/PP" (steel plates), :204-212 molds: empty shape + hammer in a position
    shaped('lv/shape_empty', f'{NS}:shape_empty', 1, ['hf', 'PP', 'PP'], {'P': P('Steel'), 'h': '#iis:tools/hammer', 'f': '#iis:tools/file'})
    for mold, pat in (('mold_plate', [' h ', ' P ', '   ']), ('mold_gear', ['   ', ' Ph', '   ']), ('mold_ingot', ['   ', ' P ', ' h ']),
                      ('mold_ball', ['   ', ' P ', 'h  ']), ('mold_block', ['   ', 'hP ', '   ']), ('mold_nugget', ['P h', '   ', '   '])):
        shaped(f'lv/{mold}', f'{NS}:{mold}', 1, pat, {'P': f'{NS}:shape_empty', 'h': '#iis:tools/hammer'})
    shaped('lv/fluid_extractor_lv', f'{NS}:fluid_extractor_lv', 1, ['GCG', 'PME', 'WCW'], {'G': 'minecraft:glass', 'C': CIRC, 'P': PUMP, 'M': HULL,
                                                                                        'E': PISTON, 'W': C('Tin')})
    shaped('lv/fluid_solidifier_lv', f'{NS}:fluid_solidifier_lv', 1, ['PGP', 'WMW', 'CBC'], {'P': PUMP, 'G': 'minecraft:glass', 'W': C('Tin'), 'M': HULL,
                                                                                         'C': CIRC, 'B': '#c:chests/wooden'})
    # GT_MetaGenerated_Item_01:405 small battery hull "C/P/P" (tin cable, battery alloy plates)
    shaped('lv/battery_hull_lv', f'{NS}:battery_hull_lv', 1, ['C', 'P', 'P'], {'C': C('Tin'), 'P': P('BatteryAlloy')})
    # GT_MetaGenerated_Item_01:731 diamond sawblade " D /DGD/ D " (small diamond dust, cobalt brass gear); :1472 assembler variant
    shaped('lv/diamond_sawblade', f'{NS}:diamond_sawblade', 1, [' D ', 'DGD', ' D '],
           {'D': tag_of('small_dust', 'Diamond'), 'G': tag_of('gear', 'CobaltBrass')})
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

    def mrec(rtype, rid, inputs, outputs, duration, eut, fin=None, fout=None, circuit=None, keep=None):
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
        if circuit is not None:
            r['circuit'] = circuit
        if keep:
            r['keep'] = keep
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
        if any(w[0] == n for w in WIRES):   # ProcessingWire:28-30
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
        # MV (D81): thermal centrifuge, GT_ModHandler.addThermalCentrifugeRecipe -> RA (500 t, 48 EU/t; the heat argument is
        # used only for IC2): ProcessingDirty:24 crushed -> centrifuged + tiny byproduct #1 + stone dust,
        # ProcessingCrushedOre:41 purified -> centrifuged + tiny byproduct #1
        if ref('centrifuged', n):
            bys = prop(n, 'ListMaterialByProducts', []) or []
            by1 = (bys or [n])[min(1, len(bys or [n]) - 1)]
            if ref('crushed', n):
                mrec('thermal_centrifuge', f'crushed_{mid}', [ing(tag_of('crushed', n))],
                     [out(ref('centrifuged', n)), out(ref('tiny_dust', by1)), out(ref('dust', 'Stone'))], 500, 48)
            if ref('purified', n):
                mrec('thermal_centrifuge', f'purified_{mid}', [ing(tag_of('purified', n))],
                     [out(ref('centrifuged', n)), out(ref('tiny_dust', by1))], 500, 48)
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
                               ('cupronickel', [('Copper', 1), ('Nickel', 1)], 'Cupronickel', 2, 200),
                               # GT_MachineRecipeLoader:1770 (LV III, D76)
                               ('stainless_steel', [('Iron', 4), ('Invar', 3), ('Manganese', 1), ('Chrome', 1)], 'StainlessSteel', 9, 900)):
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
        # ProcessingWire:52-56: N x 1x wire + programmed circuit N -> Nx wire
        for size, dur in ((2, 150), (4, 200), (8, 300), (12, 400), (16, 500)):
            mrec('assembler', f'wire_{mid}_{size}x', [ing(W(mat), size)], [out(f'{NS}:wire_{mid}_{size}x')], dur, 8, circuit=size)
        if mat in WIRE_RUBBER:
            # ProcessingWire:113-115: wire + circuit 24 + 144 mB molten rubber per cost -> cable (100 t, 8 EU/t); styrene-butadiene
            # rubber and silicone variants come with those materials
            for size, _b, ins, _r in WIRE_SIZES:
                if ins is None:
                    continue
                mrec('assembler', f'cable_{mid}_{size}x', [ing(W(mat, size))], [out(f'{NS}:cable_{mid}_{size}x')], 100, 8,
                     fin={'ingredient': f'{NS}:molten_rubber', 'amount': 144 * (size // 4 + 1)}, circuit=24)
    # GT_MachineRecipeLoader:1963 robot arm etc. (assembler variants, 80 t, 30 EU/t)
    mrec('assembler', 'robot_arm_lv', [ing(MOTOR, 2), ing(PISTON), ing(R('Steel'), 2), ing(CIRC), ing(C('Tin'), 3)], [out(ARM)], 80, 30)
    # coke oven (Railcraft defaults COKE_COOK_TIME=1800, creosote 500 L; not in the reference — see PORTING_NOTES)
    mrec('coke_oven', 'coal', [ing('minecraft:coal')], [out(f'{NS}:coal_coke')], 1800, 0, None, {'id': f'{NS}:creosote', 'amount': 500})
    mrec('coke_oven', 'coal_block', [ing('minecraft:coal_block')], [], 1800 * 9, 0, None, {'id': f'{NS}:creosote', 'amount': 4500})
    machine_recipes.pop('coke_oven/coal_block', None)  # needs a coke block; coal only for now
    lv2_recipes(g, mrec, ing, out, ref, tag_of, prop, mat_id, names, WATER)
    # LV III: chemistry and melts (D76)
    import importlib.util as _ilu
    _spec = _ilu.spec_from_file_location('iis_gen_chem', os.path.join(g['ROOT'], 'tools', 'gen', 'chem.py'))
    _chem = _ilu.module_from_spec(_spec)
    _spec.loader.exec_module(_chem)
    stats['chem'] = _chem.chem_recipes(g, mrec, ing, out, ref, tag_of, prop, mat_id, names, WATER)
    # GT_MachineRecipeLoader:1371-1372: assembler 8 plates + programmed circuit 8 -> machine casing (50 t, 16 EU/t); the hull
    # variant needs molten plastic (mHardMachineCasings) and waits for plastic
    for tr, mat in (('lv', 'Steel'), ('mv', 'Aluminium')):
        mrec('assembler', f'casing_{tr}', [ing(tag_of('plate', mat), 8)], [out(f'{NS}:casing_{tr}')], 50, 16, circuit=8)
    # --- covers: GT_MachineRecipeLoader:610-615 (lever/torch/pressure plates + plate + molten solder), :671 shutter, items :783 drain/crafting ---
    cover_plates = [tag_of('plate', m) for m in ('Iron', 'WroughtIron', 'Aluminium') if ref('plate', m)]
    for pl in cover_plates:
        pm = pl.split('/')[-1]
        for item, cover in (('minecraft:lever', 'cover_controller'), ('minecraft:redstone_torch', 'cover_activity_detector'),
                            ('minecraft:heavy_weighted_pressure_plate', 'cover_fluid_detector'),
                            ('minecraft:light_weighted_pressure_plate', 'cover_item_detector'), (f'{NS}:multimeter', 'cover_energy_detector')):
            # molten solder (72-144 L) -> one solder item until molten metals exist (PORTING_NOTES); IC2 EU-meter -> our multimeter
            mrec('assembler', f'{cover}_{pm}', [ing(item), ing(pl), ing('#iis:solder')], [out(f'{NS}:{cover}')], 800, 16)
        mrec('assembler', f'cover_shutter_{pm}', [ing(pl, 2), ing('minecraft:iron_door')], [out(f'{NS}:cover_shutter', 2)], 800, 16)
        mrec('assembler', f'cover_drain_{pm}', [ing(pl, 2), ing('minecraft:iron_bars', 2)], [out(f'{NS}:cover_drain')], 800, 16)
        mrec('assembler', f'cover_crafting_{pm}', [ing(pl), ing('minecraft:crafting_table')], [out(f'{NS}:cover_crafting')], 800, 16)
    # GT_MachineRecipeLoader:416 regulator: LV pump + 2 basic circuits; :422 fluid filter: shutter + 2 basic circuits (800 t, 4 EU/t)
    mrec('assembler', 'fluid_regulator_lv', [ing(PUMP), ing('#c:circuits/basic', 2)], [out(f'{NS}:fluid_regulator_lv')], 800, 4)
    mrec('assembler', 'cover_fluid_filter', [ing(f'{NS}:cover_shutter'), ing('#c:circuits/basic', 2)], [out(f'{NS}:cover_fluid_filter')], 800, 4)
    # GT_MetaGenerated_Item_01:463-465 canner: 2 dust + hull -> battery (100 t, 2 EU/t); :453-455 extractor: battery -> hull
    for m, bid in (('Cadmium', 'battery_cadmium_lv'), ('Lithium', 'battery_lithium_lv'), ('Sodium', 'battery_lv')):
        if ref('dust', m):
            mrec('canner', bid, [ing(tag_of('dust', m), 2), ing(f'{NS}:battery_hull_lv')], [out(f'{NS}:{bid}')], 100, 2)
        mrec('extractor', f'hull_from_{bid}', [ing(f'{NS}:{bid}')], [out(f'{NS}:battery_hull_lv')], 300, 2)
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
                       ('gui.iis.fluid_transfer.off', 'Fluid auto-output to the back: OFF (click)', 'Автовыгрузка жидкости назад: ВЫКЛ (клик)'),
                       ('gui.iis.fluid_in_out', 'In: %s; out: %s', 'Вход: %s; выход: %s'),
                       ('jei.iis.electrolyzer', 'Electrolyzer', 'Электролизёр'), ('jei.iis.chemical_reactor', 'Chemical Reactor', 'Химический реактор'),
                       ('jei.iis.fluid_extractor', 'Fluid Extractor', 'Экстрактор жидкостей'),
                       ('jei.iis.fluid_solidifier', 'Fluid Solidifier', 'Отвердитель жидкостей'),
                       ('jei.iis.distillery', 'Distillery', 'Дистиллятор'), ('jei.iis.extruder', 'Extruder', 'Экструдер'), ('jei.iis.forming_press', 'Forming Press', 'Формовочный пресс'),
                       ('jei.iis.fluid_heater', 'Fluid Heater', 'Нагреватель жидкостей'),
                       ('jei.iis.arc_furnace', 'Arc Furnace', 'Дуговая печь'), ('jei.iis.laser_engraver', 'Precision Laser Engraver', 'Лазерный гравёр'), ('jei.iis.chemical_bath', 'Chemical Bath', 'Химическая ванна'),
                       ('jei.iis.bender', 'Bending Machine', 'Гибочный станок'), ('jei.iis.lathe', 'Lathe', 'Токарный станок'),
                       ('jei.iis.assembler', 'Assembler', 'Сборщик'), ('jei.iis.mixer', 'Mixer', 'Миксер'),
                       ('jei.iis.polarizer', 'Polarizer', 'Поляризатор'), ('jei.iis.ore_washer', 'Ore Washing Plant', 'Рудопромывочная установка'),
                       ('jei.iis.coke_oven', 'Coke Oven', 'Коксовая печь'),
                       ('jei.iis.centrifuge', 'Centrifuge', 'Центрифуга'), ('jei.iis.sifter', 'Sifter', 'Просеиватель'),
                       ('jei.iis.electromagnetic_separator', 'Electromagnetic Separator', 'Электромагнитный сепаратор'),
                       ('jei.iis.autoclave', 'Autoclave', 'Автоклав'), ('jei.iis.cutter', 'Cutting Saw', 'Пильный станок'), ('jei.iis.canner', 'Canning Machine', 'Консервирующий станок'), ('message.iis.transformer.down', 'Step down: %s -> %s', 'Понижение: %s -> %s'),
                       ('message.iis.transformer.up', 'Step up: %s -> %s', 'Повышение: %s -> %s'),
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


# ----------------------------------------------------------------------------------------------------------------
# LV II: centrifuge, sifter, electromagnetic separator, autoclave, cutting machine
# ----------------------------------------------------------------------------------------------------------------
SIFTER_RICH = {'Tanzanite', 'Sapphire', 'Olivine', 'GreenSapphire', 'Opal', 'Amethyst', 'Emerald', 'Ruby', 'Amber', 'Diamond',
               'FoolsRuby', 'BlueTopaz', 'GarnetRed', 'Topaz', 'Jasper', 'GarnetYellow'}   # ProcessingCrushedOre:46
WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak']
M = 3628800


def lv2_recipes(g, mrec, ing, out, ref, tag_of, prop, mat_id, names, WATER):
    NS = 'iis'
    DISTILLED = lambda n: {'ingredient': f'{NS}:distilled_water', 'amount': n}   # GT_ModHandler.getDistilledWater (IC2 replacement)
    code = g['code']

    def byproduct(n, idx):
        """GT_Utility.selectItemInList(idx, material, mOreByProducts)."""
        bys = prop(n, 'ListMaterialByProducts', []) or []
        if not bys:
            return n
        return bys[min(idx, len(bys) - 1)]

    def cutter(rid, inp, outs, dur, eut):
        """GT_RecipeAdder#addCutterRecipe:438-440: water (duration x2, max(4, min(1000, d*e/320))), distilled water (duration x2,
        max(3, min(750, d*e/426))), lubricant (duration x1, max(1, min(250, d*e/1280)))."""
        mrec('cutter', rid, inp, outs, dur * 2, eut, WATER(max(4, min(1000, dur * eut // 320))))
        mrec('cutter', rid + '_distilled', inp, outs, dur * 2, eut, DISTILLED(max(3, min(750, dur * eut // 426))))
        mrec('cutter', rid + '_lubricant', inp, outs, dur, eut, {'ingredient': f'{NS}:lubricant', 'amount': max(1, min(250, dur * eut // 1280))})

    def extra_data(n):
        raw = (code.get(n) or {}).get('rawArgs') or []
        try:
            return int(raw[22])
        except (IndexError, ValueError):
            return 0

    def decomposition(n):
        """ProcessingDust:57: dust -> component dusts. None when a component has no dust (GT would put it in a cell)."""
        comp = (code.get(n) or {}).get('composition') or []
        if not comp:
            return None
        dens = float(prop(n, 'Density', M) or M)
        mult = int(dens // M) if dens > M else 1
        outs, amount = [], 0
        for c, a in comp:
            if c == 'Air' or not ref('dust', c):
                return None
            if amount + a * M > 64 * dens:
                continue
            amount += a * M
            outs.append((ref('dust', c), a * mult))
        if not outs or len(outs) > 6:
            return None
        count = (1 if (amount * mult) % dens > 0 else 0) + int(amount * mult // dens)
        return count, outs

    for n in sorted(names):
        if n in g['NOT_GENERATED_MATERIALS']:
            continue
        mid, st, mass = mat_id(n), g['subtags'](n), max(1, g['mass'](n))
        dust = ref('dust', n)
        # --- centrifuge: impure / pure dust -> dust + byproduct (ProcessingDust:189) ---
        for pre, idx in (('impure_dust', 0), ('pure_dust', 1)):
            if not (ref(pre, n) and dust):
                continue
            by = byproduct(n, idx)
            tiny = ref('tiny_dust', by) or ref('nugget', by)
            if tiny:
                mrec('centrifuge', f'{pre}_{mid}', [ing(tag_of(pre, n))], [out(dust), out(tiny)], mass * 8, 5)
            elif ref('small_dust', by):
                mrec('centrifuge', f'{pre}_{mid}', [ing(tag_of(pre, n), 2)], [out(dust, 2), out(ref('small_dust', by))], mass * 16, 5)
            elif ref('dust', by) or ref('gem', by):
                mrec('centrifuge', f'{pre}_{mid}', [ing(tag_of(pre, n), 9)], [out(dust, 9), out(ref('dust', by) or ref('gem', by))], mass * 72, 5)
            else:
                mrec('centrifuge', f'{pre}_{mid}', [ing(tag_of(pre, n))], [out(dust)], mass, 5)
        # --- centrifuge / electrolyzer decomposition without cells (ProcessingDust:57-110) ---
        if dust and extra_data(n) & 2:
            d = decomposition(n)
            if d:
                count, outs = d
                mrec('centrifuge', f'decompose_{mid}', [ing(tag_of('dust', n), count)], [out(i, c) for i, c in outs],
                     max(1, mass * 4 * count), min(4, len(outs)) * 5)
        # --- electromagnetic separator: pure dust (ProcessingDust:192) ---
        if ref('pure_dust', n) and dust:
            for tag, m in (('ELECTROMAGNETIC_SEPERATION_GOLD', 'Gold'), ('ELECTROMAGNETIC_SEPERATION_IRON', 'Iron'),
                           ('ELECTROMAGNETIC_SEPERATION_NEODYMIUM', 'Neodymium')):
                if tag in st:
                    mrec('electromagnetic_separator', f'pure_{mid}', [ing(tag_of('pure_dust', n))],
                         [out(dust), out(ref('small_dust', m), 1, 4000), out(ref('nugget', m), 1, 2000)], 400, 24)
        # --- autoclave: CRYSTALLISABLE dusts + 200 L water -> gem (ProcessingDust:114-115, :204-205); distilled water: faster, higher chance ---
        if 'CRYSTALLISABLE' in st and ref('gem', n):
            for pre, chance, dchance in (('dust', 7000, 9000), ('impure_dust', 9000, 9500), ('pure_dust', 9000, 9500)):
                if ref(pre, n):
                    mrec('autoclave', f'{pre}_{mid}', [ing(tag_of(pre, n))], [out(ref('gem', n), 1, chance)], 2000, 24, WATER(200))
                    mrec('autoclave', f'{pre}_{mid}_distilled', [ing(tag_of(pre, n))], [out(ref('gem', n), 1, dchance)], 1500, 24,
                         DISTILLED(200))
        # --- sifter: purified crushed gem ore (ProcessingCrushedOre:44) ---
        gem = ref('gem', n)
        if gem and ref('purified', n):
            ch = [300, 1200, 4500, 1400, 2800, 3500] if n in SIFTER_RICH else [100, 400, 1500, 2000, 4000, 5000]
            forms = [ref('gem_exquisite', n) or gem, ref('gem_flawless', n) or gem, gem, ref('gem_flawed', n) or gem,
                     ref('gem_chipped', n) or gem, dust or gem]
            mrec('sifter', f'purified_{mid}', [ing(tag_of('purified', n))], [out(f, 1, c) for f, c in zip(forms, ch)], 800, 16)
        # --- cutter: rod -> 4 bolts, long rod -> 2 rods (ProcessingStick:21, ProcessingStickLong:20) ---
        if 'NO_WORKING' not in st:
            if ref('rod', n) and ref('bolt', n):
                cutter(f'bolt_{mid}', [ing(tag_of('rod', n))], [out(ref('bolt', n), 4)], max(mass * 2, 1), 4)
            if ref('long_rod', n) and ref('rod', n):
                cutter(f'rod_{mid}', [ing(tag_of('long_rod', n))], [out(ref('rod', n), 2)], max(mass, 1), 4)
    # ProcessingBlock:17 block -> 9 plates (mass*10, 30 EU/t): vanilla storage blocks of our metals
    for blk, n in (('minecraft:iron_block', 'Iron'), ('minecraft:gold_block', 'Gold'), ('minecraft:copper_block', 'Copper')):
        if ref('plate', n):
            cutter(f'block_{mat_id(n)}', [ing(blk)], [out(ref('plate', n), 9)], max(g['mass'](n) * 10, 1), 30)
    # ProcessingLog:80 log -> planks (vanilla count) + 2 wood dust (200, 8); ProcessingPlank:50 plank -> 2 slabs (25, 4)
    for w in WOODS:
        cutter(f'log_{w}', [ing(f'#minecraft:{w}_logs')], [out(f'minecraft:{w}_planks', 4), out(ref('dust', 'Wood'), 2)], 200, 8)
        cutter(f'planks_{w}', [ing(f'minecraft:{w}_planks')], [out(f'minecraft:{w}_slab', 2)], 25, 4)
    for w in ('crimson', 'warped'):
        cutter(f'stem_{w}', [ing(f'#minecraft:{w}_stems')], [out(f'minecraft:{w}_planks', 4), out(ref('dust', 'Wood'), 2)], 200, 8)
        cutter(f'planks_{w}', [ing(f'minecraft:{w}_planks')], [out(f'minecraft:{w}_slab', 2)], 25, 4)
    cutter('log_rubber', [ing(f'{NS}:rubber_log')], [out('minecraft:jungle_planks', 3), out(ref('dust', 'Wood'), 2)], 200, 8)
    # GT_MachineRecipeLoader:1239-1248 glass panes and stone slabs
    cutter('glass_pane', [ing('minecraft:glass', 3)], [out('minecraft:glass_pane', 8)], 50, 8)
    for b, slab in (('stone', 'stone_slab'), ('sandstone', 'sandstone_slab'), ('cobblestone', 'cobblestone_slab'),
                    ('bricks', 'brick_slab'), ('stone_bricks', 'stone_brick_slab'), ('nether_bricks', 'nether_brick_slab'),
                    ('quartz_block', 'quartz_slab')):
        cutter(f'slab_{b}', [ing(f'minecraft:{b}')], [out(f'minecraft:{slab}', 2)], 25, 8)
    # IIS GT_IC2.zs:747 autoclave: clay dust + 250 L water -> clay ball (20 t, 2 EU/t)
    if ref('dust', 'Clay'):
        mrec('autoclave', 'clay_ball', [ing(tag_of('dust', 'Clay'))], [out('minecraft:clay_ball')], 20, 2, WATER(250))
    # GT_MachineRecipeLoader:1784 mixer / :1786 alloy smelter cobalt brass; GT_MachineRecipeLoader:414 item filter
    if all(ref('dust', m) for m in ('Brass', 'Aluminium', 'Cobalt', 'CobaltBrass')):
        mrec('mixer', 'cobalt_brass_dust', [ing(tag_of('dust', 'Brass'), 7), ing(tag_of('dust', 'Aluminium')), ing(tag_of('dust', 'Cobalt'))],
             [out(ref('dust', 'CobaltBrass'), 9)], 900, 8)
    if ref('ingot', 'CobaltBrass'):
        mrec('alloy_smelter', 'cobalt_brass', [ing(tag_of('dust', 'Brass'), 7), ing(tag_of('dust', 'Aluminium')), ing(tag_of('dust', 'Cobalt'))],
             [out(ref('ingot', 'CobaltBrass'), 9)], 450, 16)
    if ref('gear', 'CobaltBrass') and ref('dust', 'Diamond'):
        mrec('assembler', 'diamond_sawblade', [ing(tag_of('gear', 'CobaltBrass')), ing(tag_of('dust', 'Diamond'))], [out(f'{NS}:diamond_sawblade')], 1600, 2)
    if ref('wire_fine', 'Steel') and ref('foil', 'Zinc'):
        mrec('assembler', 'item_filter', [ing(tag_of('wire_fine', 'Steel'), 64), ing(tag_of('foil', 'Zinc'), 16)], [out(f'{NS}:item_filter')], 1600, 16)
