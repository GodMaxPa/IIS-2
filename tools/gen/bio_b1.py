"""Bio slice B-1 (D70): manual biochemistry of the hearth — recipes table, crafting, item/block art, models, lang.

Called from tools/gen/bio.py. Numbers follow the owner's spec v0.7 §22 as a TEMPORARY TEST SET (decision R1), plus the
owner's decisions R2–R9 (2026-10-22): N0 in mB, any food in the maw (prepared food far more efficient), carcasses by size
via the bio knife, hand substrate on living tissue, measuring ladle, no culture/reserves on the shuttle (scraping), maw as
the first grown organ. The recipe table is also the machine-readable table required by §22.12.
"""
import json
import os

import numpy as np
from PIL import Image, ImageDraw

NS = 'iis'


# ----------------------------------------------------------------------------------------------------------------
# Recipe table
# ----------------------------------------------------------------------------------------------------------------
def I(key, count=1):
    return {'key': key if (key.startswith('#') or ':' in key or key == 'food') else f'{NS}:{key}', 'count': count}


def O(item, count=1):
    return {'item': item if ':' in item else f'{NS}:{item}', 'count': count}


def F(fluid, amount):
    return {'fluid': fluid, 'amount': amount}


def R(rid, station, seconds, items=(), fluids=(), out_items=(), out_fluids=(), mode=0, tool='', heat=False, upkeep=0.0, lineage=False, spec=''):
    r = {'id': rid, 'station': station, 'mode': mode, 'seconds': seconds, 'items': list(items), 'fluids': list(fluids),
         'out_items': list(out_items), 'out_fluids': list(out_fluids)}
    if tool:
        r['tool'] = tool if ':' in tool else f'{NS}:{tool}'
    if heat:
        r['heat'] = True
    if upkeep:
        r['upkeep'] = upkeep
    if lineage:
        r['lineage'] = True
    if spec:
        r['spec'] = spec
    return r


KNIFE, MANDREL = 'bio_knife', 'woven_mandrel'
UPKEEP = 0.010  # §22.6: ручная подложка / ULV-ложе — N0 0,010 мБ/т

RECIPES = [
    # --- W01 разделочная площадка (порядок: сначала более сложные составы) -----------------------------------------
    R('hand_substrate', 'butcher', 20, [I('growth_substrate'), I('#minecraft:planks', 2)], out_items=[O('hand_substrate')], spec='D70'),
    R('b11_substrate', 'butcher', 20, [I('plant_mass'), I('plant_fiber', 2), I('binding_gel')], out_items=[O('growth_substrate')], spec='B11'),
    R('b10_frame', 'butcher', 20, [I('plant_fiber', 4), I('sinew_cord')], out_items=[O('woven_frame')], spec='B10'),
    R('carcass_large', 'butcher', 40, [I('carcass_large')], out_items=[O('meat_mass', 5), O('connective_fiber', 3), O('bone_fragment', 3)], tool=KNIFE, spec='§22.4 корова'),
    R('carcass_medium', 'butcher', 30, [I('carcass_medium')], out_items=[O('meat_mass', 4), O('connective_fiber', 2), O('bone_fragment', 2)], tool=KNIFE, spec='§22.4 свинья'),
    R('carcass_small', 'butcher', 20, [I('carcass_small')], out_items=[O('meat_mass', 2), O('connective_fiber', 1), O('bone_fragment', 1)], tool=KNIFE, spec='§22.4 курица'),
    R('carcass_rotten', 'butcher', 20, [I('carcass_rotten')], out_items=[O('rotten_mass', 3), O('bone_fragment', 1)], tool=KNIFE, spec='D70 гнилая туша'),
    R('b03_wheat', 'butcher', 20, [I('minecraft:wheat', 2)], out_items=[O('plant_fiber', 2), O('plant_mass', 1)], tool=KNIFE, spec='B03'),
    R('b18_ring', 'butcher', 80, [I('bone_rib', 2), I('binding_gel')], out_items=[O('bone_ring')], tool=MANDREL, spec='B18 вручную (W07, 80 с)'),
    R('b27_pad', 'butcher', 80, [I('plant_fiber', 4), I('fibrous_tissue')], out_items=[O('fine_filter_pad')], tool=MANDREL, spec='B27 вручную 80 с'),
    R('b27_pad_animal', 'butcher', 80, [I('connective_fiber', 4), I('fibrous_tissue')], out_items=[O('fine_filter_pad')], tool=MANDREL, spec='B27 вручную 80 с'),
    R('clamp', 'butcher', 10, [I('minecraft:stick', 2), I('sinew_cord')], out_items=[O('manual_clamp')], spec='§22.7 ручной зажим ULV'),
    R('b05_cord', 'butcher', 10, [I('connective_fiber', 2)], out_items=[O('sinew_cord')], spec='B05'),
    R('b07_bone', 'butcher', 10, [I('minecraft:bone')], out_items=[O('bone_fragment', 2)], tool=KNIFE, spec='B07'),
    R('b08_rib', 'butcher', 10, [I('bone_fragment')], out_items=[O('bone_rib')], tool=KNIFE, spec='B08'),
    # --- W03 ступа ------------------------------------------------------------------------------------------------------
    R('b01_meat', 'mortar', 10, [I('#iis:bio/raw_meat')], out_items=[O('meat_mass')], spec='B01'),
    R('b02_plant', 'mortar', 10, [I('#iis:bio/vegetables')], out_items=[O('plant_mass')], spec='B02'),
    R('b09_powder', 'mortar', 10, [I('bone_fragment')], out_items=[O('bone_powder')], spec='B09'),
    R('rotten_mass', 'mortar', 10, [I('minecraft:rotten_flesh')], out_items=[O('rotten_mass')], spec='D70'),
    # --- W04 сосуд на очаге W05 -----------------------------------------------------------------------------------------
    R('l05_binder', 'pot', 30, [I('binding_gel', 2)], [F('water', 1000)], out_fluids=[F('binder', 1000)], heat=True, spec='L05'),
    R('honey_binder', 'pot', 30, [I('minecraft:honeycomb', 2)], [F('water', 1000)], out_fluids=[F('binder', 1000)], heat=True, spec='D143 пчёлы: соты — связующее'),
    R('b06_gel', 'pot', 120, [I('connective_fiber', 2)], [F('water', 250)], out_items=[O('binding_gel')], out_fluids=[F('runoff', 250)], heat=True, spec='B06'),
    # --- W08 ручной отжим, W10 гравитационный фильтр (D72) --------------------------------------------------------------
    R('l02_extract', 'press', 80, [I('plant_mass', 2)], [F('water', 1000)], [O('plant_residue')], [F('extract', 800), F('runoff', 200)], spec='L02'),
    R('l08_w0', 'filter', 40, fluids=[F('water', 1000)], out_fluids=[F('w0', 950), F('runoff', 50)], tool='filter_pad', spec='L08 (W10)'),
    # --- W06 мерный сосуд -----------------------------------------------------------------------------------------------
    R('l13_g1', 'mixer', 80, fluids=[F('n1', 500), F('hydrolysate', 200), F('binder', 150), F('extract', 100), F('suspension', 50)],
      out_fluids=[F('g1', 1000)], spec='L13 вручную ×2'),
    R('l11_n1', 'mixer', 40, fluids=[F('w0', 800), F('hydrolysate', 100), F('extract', 70), F('suspension', 20), F('emulsion', 10)],
      out_fluids=[F('n1', 1000)], spec='L11 вручную ×2'),
    R('l12_e1', 'mixer', 60, fluids=[F('n1', 600), F('concentrate', 300), F('emulsion', 100)], out_fluids=[F('e1', 1000)], spec='L12 вручную ×2'),
    R('l06_emulsion', 'mixer', 120, [I('energy_paste')], [F('hydrolysate', 500), F('extract', 500)], out_fluids=[F('emulsion', 800), F('runoff', 200)], spec='L06'),
    R('l09_n0', 'mixer', 20, fluids=[F('water', 900), F('extract', 60), F('hydrolysate', 30), F('suspension', 10)], out_fluids=[F('n0', 1000)], spec='L09'),
    R('l10_g0', 'mixer', 40, fluids=[F('n0', 600), F('hydrolysate', 200), F('binder', 150), F('suspension', 50)], out_fluids=[F('g0', 1000)], spec='L10'),
    R('l04_suspension', 'mixer', 40, [I('bone_powder')], [F('water', 1000)], out_fluids=[F('suspension', 1000)], spec='L04'),
    # --- карман ядра: 0 питание, 1 гидролизат, 2 почкование --------------------------------------------------------------
    R('p_l01', 'pocket', 60, [I('plant_mass'), I('meat_mass')], [F('water', 500)], out_fluids=[F('n0', 350), F('primary_fraction', 50), F('runoff', 100)], spec='L01'),
    R('p_meat', 'pocket', 40, [I('meat_mass')], [F('water', 250)], out_fluids=[F('n0', 150), F('primary_fraction', 20), F('runoff', 50)], spec='D70 одна масса'),
    R('p_plant', 'pocket', 40, [I('plant_mass')], [F('water', 250)], out_fluids=[F('n0', 150), F('primary_fraction', 20), F('runoff', 50)], spec='D70 одна масса'),
    R('p_rotten', 'pocket', 20, [I('rotten_mass')], out_fluids=[F('n0', 40), F('runoff', 60)], spec='D70 гниль'),
    R('p_raw', 'pocket', 10, [I('food')], out_fluids=[{'fluid': 'n0', 'per_nutrition': 10}, F('runoff', 20)], spec='D70 сырая пища'),
    R('p_b28_paste', 'pocket', 60, [I('plant_mass', 2), I('meat_mass')], [F('water', 250)], [O('energy_paste')], [F('runoff', 250)], mode=1, spec='B28'),
    R('p_l07', 'pocket', 120, [I('energy_paste', 2)], [F('extract', 1000)], out_fluids=[F('concentrate', 800), F('runoff', 200)], mode=1, spec='L07'),
    R('p_l03', 'pocket', 120, [I('meat_mass', 2)], [F('water', 1000)], [O('organic_residue')], [F('hydrolysate', 800), F('runoff', 200)], mode=1, spec='L03'),
    R('p_muscle_seed', 'pocket', 120, [I('base_seed'), I('meat_mass', 2)], [F('g0', 50)], [O('muscle_seed')], mode=2, lineage=True,
      spec='D73 ВРЕМЕННО: мышечный посев в v0.7 не задан'),
    R('p_renew', 'pocket', 120, [I('meat_mass'), I('plant_mass')], [F('primary_fraction', 50)], [O('base_seed')], mode=2, lineage=True, spec='§22.4 возобновление посева'),
    R('p_b29', 'pocket', 120, [I('soft_tissue')], [F('g0', 50)], [O('base_seed')], mode=2, lineage=True, spec='B29'),
    # --- ручная подложка ---------------------------------------------------------------------------------------------------
    R('a01_bed', 'substrate', 120, [I('woven_frame'), I('growth_substrate'), I('living_membrane'), I('bone_rib', 2), I('base_seed')], [F('g0', 50)],
      [O('growth_bed')], tool=MANDREL, upkeep=UPKEEP, lineage=True, spec='A01'),
    R('b12_tissue', 'substrate', 120, [I('growth_substrate'), I('#iis:bio/seeds')], [F('primary_fraction', 50)], [O('soft_tissue')], upkeep=UPKEEP, lineage=True, spec='B12'),
    R('b14_membrane', 'substrate', 180, [I('soft_tissue')], [F('g0', 75)], [O('living_membrane')], tool=MANDREL, upkeep=UPKEEP, lineage=True, spec='B14'),
    # --- A01 ростовое ложе ------------------------------------------------------------------------------------------------
    R('a02_maw', 'bed', 180, [I('woven_frame'), I('soft_tissue', 2), I('living_membrane'), I('bone_rib', 2), I('base_seed')], [F('g0', 75)],
      [O('feeding_maw')], upkeep=UPKEEP, lineage=True, spec='A02'),
    # сосуды §22.7 (D72); LV-изделия — на ложе до LV-гнезда, переходные B15/B19 — G0 ×2 и время ×2 (§22.13)
    R('v_capillary', 'bed', 180, [I('soft_tissue', 2), I('living_membrane', 2), I('woven_frame'), I('base_seed')], [F('g0', 75)],
      [O('capillary_bundle')], upkeep=UPKEEP, lineage=True, spec='§22.7 капиллярный пучок'),
    R('v_microchannel', 'bed', 120, [I('soft_tissue'), I('living_membrane'), I('sinew_cord'), I('base_seed')], [F('g0', 50)],
      [O('tissue_microchannel', 4)], upkeep=UPKEEP, lineage=True, spec='§22.7 микроканал'),
    # первые LV-органы и мышечная линия на ULV-ложе: время ×2, G0 ×2 вместо G1 (§21.9, §22.6, §22.13); B16 — особый режим 480 с / 200 G0
    R('a03_bed', 'bed', 600, [I('woven_frame', 2), I('bone_rib', 2), I('living_membrane', 2), I('muscle_node'), I('base_seed')], [F('g0', 500)],
      [O('forming_nest')], upkeep=UPKEEP, lineage=True, spec='A03 первый в ложе (§21.8)'),
    R('a07_bed', 'bed', 480, [I('woven_frame'), I('muscle_node'), I('valve_petal', 2), I('tissue_lining'), I('fibrous_tissue', 2), I('base_seed')],
      [F('g0', 300)], [O('muscle_pump')], upkeep=UPKEEP, lineage=True, spec='A07 первый в ложе'),
    R('f06_bed', 'bed', 480, [I('woven_frame', 2), I('living_membrane', 3), I('tissue_lining', 2), I('muscle_node'), I('base_seed')], [F('g0', 500)],
      [O('nutrient_gland')], upkeep=UPKEEP, lineage=True, spec='F06 первый в ложе'),
    R('f09_bed', 'bed', 240, [I('woven_frame'), I('tissue_lining'), I('living_membrane'), I('fine_filter_pad'), I('base_seed')], [F('g0', 150)],
      [O('membrane_filter')], upkeep=UPKEEP, lineage=True, spec='F09 первый в ложе'),
    R('b16_bed', 'bed', 480, [I('soft_tissue'), I('meat_mass'), I('muscle_seed')], [F('g0', 200)], [O('muscle_bundle')], upkeep=UPKEEP, lineage=True,
      spec='B16 особый режим'),
    R('b17_bed', 'bed', 240, [I('muscle_bundle'), I('sinew_cord'), I('bone_rib')], [F('g0', 100)], [O('muscle_node')], upkeep=UPKEEP, lineage=True,
      spec='B17 переходный'),
    R('b20_bed', 'bed', 180, [I('living_membrane'), I('fibrous_tissue')], [F('g0', 50)], [O('valve_petal', 2)], tool=MANDREL, mode=1, upkeep=UPKEEP,
      lineage=True, spec='B20 переходный (режим «лепестки»)'),
    R('b19_lining', 'bed', 240, [I('living_membrane'), I('fibrous_tissue')], [F('g0', 100)], [O('tissue_lining')], tool=MANDREL,
      upkeep=UPKEEP, lineage=True, spec='B19 переходный'),
    R('b15_plant', 'bed', 240, [I('soft_tissue'), I('plant_fiber', 2)], [F('g0', 150)], [O('fibrous_tissue')], upkeep=UPKEEP, lineage=True, spec='B15 переходный'),
    R('b15_animal', 'bed', 240, [I('soft_tissue'), I('connective_fiber', 2)], [F('g0', 150)], [O('fibrous_tissue')], upkeep=UPKEEP, lineage=True, spec='B15 переходный'),
    # --- A03 формовочное гнездо LV (питание — свои буферы N1/E1) ------------------------------------------------------
    R('a03_nest', 'nest', 300, [I('woven_frame', 2), I('bone_rib', 2), I('living_membrane', 2), I('muscle_node'), I('base_seed')], [F('g1', 250)],
      [O('forming_nest')], lineage=True, spec='A03'),
    R('a07_nest', 'nest', 240, [I('woven_frame'), I('muscle_node'), I('valve_petal', 2), I('tissue_lining'), I('fibrous_tissue', 2), I('base_seed')],
      [F('g1', 150)], [O('muscle_pump')], lineage=True, spec='A07'),
    R('f06_nest', 'nest', 240, [I('woven_frame', 2), I('living_membrane', 3), I('tissue_lining', 2), I('muscle_node'), I('base_seed')], [F('g1', 250)],
      [O('nutrient_gland')], lineage=True, spec='F06'),
    R('f09_nest', 'nest', 120, [I('woven_frame'), I('tissue_lining'), I('living_membrane'), I('fine_filter_pad'), I('base_seed')], [F('g1', 75)],
      [O('membrane_filter')], lineage=True, spec='F09'),
    R('living_tank', 'nest', 240, [I('woven_frame', 2), I('living_membrane', 4), I('tissue_lining', 2), I('bone_rib', 4), I('base_seed')], [F('g1', 200)],
      [O('living_tank')], lineage=True, spec='§22.7 живой бак'),
    R('v_working', 'nest', 120, [I('tissue_lining', 2), I('fibrous_tissue', 2), I('bone_ring', 2), I('base_seed')], [F('g1', 100)],
      [O('working_fibrous_vessel')], lineage=True, spec='§22.7 рабочий волокнистый'),
    R('v_narrow', 'nest', 60, [I('tissue_lining'), I('fibrous_tissue'), I('bone_ring'), I('base_seed')], [F('g1', 50)],
      [O('narrow_fibrous_vessel')], lineage=True, spec='§22.7 узкий волокнистый'),
    R('check_valve', 'nest', 60, [I('tissue_lining'), I('valve_petal'), I('bone_ring'), I('base_seed')], [F('g1', 25)],
      [O('check_valve')], lineage=True, spec='§22.7 обратный клапан'),
    R('b16_nest', 'nest', 180, [I('soft_tissue'), I('meat_mass'), I('muscle_seed')], [F('g1', 100)], [O('muscle_bundle')], lineage=True, spec='B16'),
    R('b17_nest', 'nest', 120, [I('muscle_bundle'), I('sinew_cord'), I('bone_rib')], [F('g1', 50)], [O('muscle_node')], lineage=True, spec='B17'),
    R('b19_nest', 'nest', 120, [I('living_membrane'), I('fibrous_tissue')], [F('g1', 50)], [O('tissue_lining')], tool=MANDREL, lineage=True, spec='B19'),
    R('b20_nest', 'nest', 90, [I('living_membrane'), I('fibrous_tissue')], [F('g1', 25)], [O('valve_petal', 2)], tool=MANDREL, mode=1, lineage=True,
      spec='B20 (режим «лепестки»)'),
    R('b15_nest', 'nest', 120, [I('soft_tissue'), I('plant_fiber', 2)], [F('g1', 75)], [O('fibrous_tissue')], lineage=True, spec='B15'),
    R('b15_nest_animal', 'nest', 120, [I('soft_tissue'), I('connective_fiber', 2)], [F('g1', 75)], [O('fibrous_tissue')], lineage=True, spec='B15'),
    # --- F06 питательная железа: серийные N1 20 с, E1 30 с, G1 40 с, N0/G0 по исходному времени ------------------------------
    R('g_g1', 'gland', 40, fluids=[F('n1', 500), F('hydrolysate', 200), F('binder', 150), F('extract', 100), F('suspension', 50)],
      out_fluids=[F('g1', 1000)], spec='L13 F06'),
    R('g_n1', 'gland', 20, fluids=[F('w0', 800), F('hydrolysate', 100), F('extract', 70), F('suspension', 20), F('emulsion', 10)],
      out_fluids=[F('n1', 1000)], spec='L11 F06'),
    R('g_e1', 'gland', 30, fluids=[F('n1', 600), F('concentrate', 300), F('emulsion', 100)], out_fluids=[F('e1', 1000)], spec='L12 F06'),
    R('g_g0', 'gland', 40, fluids=[F('n0', 600), F('hydrolysate', 200), F('binder', 150), F('suspension', 50)], out_fluids=[F('g0', 1000)], spec='L10 F06'),
    R('g_n0', 'gland', 20, fluids=[F('water', 900), F('extract', 60), F('hydrolysate', 30), F('suspension', 10)], out_fluids=[F('n0', 1000)], spec='L09 F06'),
    # --- F09 мембранный фильтр -----------------------------------------------------------------------------------------
    R('f09_w0', 'membrane_filter', 20, fluids=[F('water', 1000)], out_fluids=[F('w0', 950), F('runoff', 50)], tool='fine_filter_pad', spec='L08 F09'),
    R('b13_tissue', 'bed', 120, [I('growth_substrate'), I('#iis:bio/seeds')], [F('g0', 50)], [O('soft_tissue')], upkeep=UPKEEP, lineage=True, spec='B13'),
    R('b14_bed', 'bed', 180, [I('soft_tissue')], [F('g0', 75)], [O('living_membrane')], tool=MANDREL, upkeep=UPKEEP, lineage=True, spec='B14'),
]

ITEM_TAGS = {
    'bio/raw_meat': ['minecraft:porkchop', 'minecraft:beef', 'minecraft:mutton', 'minecraft:chicken', 'minecraft:rabbit', 'minecraft:cod', 'minecraft:salmon'],
    'bio/vegetables': ['minecraft:carrot', 'minecraft:potato', 'minecraft:beetroot'],
    'bio/seeds': [f'{NS}:primitive_culture', f'{NS}:base_seed'],
}

CRAFTING = {
    'bio_knife': (['F', 'S'], {'F': 'minecraft:flint', 'S': 'minecraft:stick'}),
    'woven_mandrel': (['FSF', 'FSF'], {'F': f'{NS}:plant_fiber', 'S': 'minecraft:stick'}),
    'measuring_ladle': (['B', 'S'], {'B': 'minecraft:bowl', 'S': 'minecraft:stick'}),
    'butcher_block': (['SS', 'PP', 'PP'], {'S': 'minecraft:stick', 'P': '#minecraft:planks'}),
    'stone_mortar': ([' S ', 'C C', ' C '], {'S': 'minecraft:stick', 'C': 'minecraft:cobblestone'}),
    'stone_pot': (['C C', 'CCC'], {'C': 'minecraft:cobblestone'}),
    'stone_hearth': (['SS', 'CC', 'CC'], {'S': 'minecraft:stick', 'C': 'minecraft:cobblestone'}),
    'measuring_vessel': (['S', 'P'], {'S': 'minecraft:stick', 'P': f'{NS}:stone_pot'}),
    'stone_press': (['PSP', 'F F', 'PSP'], {'P': '#minecraft:planks', 'S': 'minecraft:stick', 'F': f'{NS}:plant_fiber'}),
    'filter_pad': (['FF', 'FF'], {'F': f'{NS}:plant_fiber'}),
    'filter_vessel': (['SDS', ' P '], {'S': 'minecraft:stick', 'D': f'{NS}:filter_pad', 'P': f'{NS}:stone_pot'}),
}

# ----------------------------------------------------------------------------------------------------------------
# Names
# ----------------------------------------------------------------------------------------------------------------
ITEMS = {
    'bio_knife': ('Stone Butchering Knife', 'Каменный разделочный нож', ['Kill an adult animal with it to get a carcass instead of normal drops.', 'Scrapes a primitive culture from colony tissue (right-click).'],
                  ['Убитое им взрослое животное оставляет тушу вместо обычной добычи.', 'Соскабливает неразвитую культуру с ткани колонии (ПКМ).']),
    'woven_mandrel': ('Woven Mandrel', 'Плетёная оправка', ['Shaping tool for membranes and the growth bed.'], ['Инструмент формовки мембран и ростового ложа.']),
    'measuring_ladle': ('Measuring Ladle', 'Мерный ковш', ['Right-click water: scoop. Right-click air: change portion. Sneak: pour out.', 'Right-click a vessel or organ: pour in or take out.'],
                        ['ПКМ по воде — набрать. ПКМ в воздух — сменить порцию. Shift — вылить.', 'ПКМ по сосуду или органу — налить или набрать.']),
    'carcass_small': ('Small Carcass', 'Малая туша', ['Butcher on a butcher block with a knife.'], ['Разделать на площадке ножом.']),
    'carcass_medium': ('Medium Carcass', 'Средняя туша', ['Butcher on a butcher block with a knife.'], ['Разделать на площадке ножом.']),
    'carcass_large': ('Large Carcass', 'Большая туша', ['Butcher on a butcher block with a knife.'], ['Разделать на площадке ножом.']),
    'carcass_rotten': ('Rotten Carcass', 'Гнилая туша', ['Only rotten mass and bones: poor food for the colony.'], ['Только гнилая масса и кости: плохая пища для колонии.']),
    'meat_mass': ('Meat Mass', 'Мясная масса', [], []),
    'plant_mass': ('Plant Mass', 'Растительная масса', [], []),
    'rotten_mass': ('Rotten Mass', 'Гнилая масса', [], []),
    'plant_fiber': ('Plant Fiber', 'Растительное волокно', [], []),
    'connective_fiber': ('Connective Fiber', 'Соединительное волокно', [], []),
    'sinew_cord': ('Sinew Cord', 'Жильный шнур', [], []),
    'binding_gel': ('Binding Gel', 'Связующий гель', [], []),
    'bone_fragment': ('Bone Fragment', 'Костный фрагмент', [], []),
    'bone_rib': ('Bone Rib', 'Костяное ребро', [], []),
    'bone_powder': ('Bone Powder', 'Костный порошок', [], []),
    'woven_frame': ('Woven Frame', 'Плетёный каркас', [], []),
    'growth_substrate': ('Growth Substrate', 'Ростовая подложка', [], []),
    'organic_residue': ('Organic Residue', 'Органический остаток', ['Waste. Can be composted.'], ['Отход. Можно компостировать.']),
    'soft_tissue': ('Soft Tissue', 'Мягкая ткань', [], []),
    'living_membrane': ('Living Membrane', 'Живая мембрана', [], []),
    'primitive_culture': ('Primitive Culture', 'Неразвитая культура', ['Scraped from the colony tissue. Grows only soft tissue.'], ['Соскоб ткани колонии. Годится только для мягкой ткани.']),
    'filter_pad': ('Coarse Filter Pad', 'Грубая фильтровальная прокладка', ['Tool slot of the gravity filter: 25 batches.'], ['В слот инструмента гравитационного фильтра: 25 партий.']),
    'plant_residue': ('Plant Residue', 'Растительный остаток', ['Waste. Can be composted.'], ['Отход. Можно компостировать.']),
    'energy_paste': ('Energy Paste', 'Энергетическая паста', [], []),
    'bone_ring': ('Bone Ring', 'Костяное кольцо', [], []),
    'fibrous_tissue': ('Fibrous Tissue', 'Волокнистая ткань', [], []),
    'tissue_lining': ('Tissue Lining', 'Тканевая выстилка', [], []),
    'muscle_seed': ('Muscle Seed', 'Мышечный посев', ['TEMPORARY recipe: budding in the pocket (not in spec v0.7).'], ['ВРЕМЕННЫЙ рецепт: почкование в кармане (в v0.7 не задан).']),
    'muscle_bundle': ('Muscle Bundle', 'Мышечный пучок', [], []),
    'muscle_node': ('Muscle Node', 'Мышечный узел', ['Drive of pumps, the forming nest and glands.'], ['Привод насоса, формовочного гнезда и желёз.']),
    'valve_petal': ('Valve Petal', 'Клапанный лепесток', [], []),
    'fine_filter_pad': ('Filter Pad', 'Фильтровальная прокладка', ['Tool slot of the membrane filter: 100 batches.'], ['В слот инструмента мембранного фильтра: 100 партий.']),
    'base_seed': ('Base Colony Seed', 'Базовый посев колонии', ['A bud of the core. Every new organ needs one.'], ['Почка ядра. Нужна для каждого нового органа.']),
}
BLOCKS = {
    'butcher_block': ('Butcher Block', 'Разделочная площадка', ['Carcasses, fibers, bones, frame, substrate. Knife in the tool slot.'], ['Туши, волокна, кости, каркас, подложка. Нож — в слот инструмента.']),
    'stone_mortar': ('Stone Mortar', 'Каменная ступа', ['Grinds meat, vegetables and bone fragments.'], ['Перетирает мясо, овощи, костные фрагменты.']),
    'stone_pot': ('Stone Cooking Pot', 'Каменный варочный сосуд', ['Needs a lit hearth below: gel, liquid binder.'], ['Нужен горящий очаг снизу: гель, жидкое связующее.']),
    'stone_hearth': ('Stone Hearth', 'Очаг', ['Right-click: fuel slot (any furnace fuel). Heats the pot above.'], ['ПКМ — слот топлива (любое печное топливо). Греет сосуд сверху.']),
    'stone_press': ('Hand Press', 'Ручной отжим', ['Plant extract from plant mass and water.'], ['Растительный экстракт из растительной массы и воды.']),
    'filter_vessel': ('Gravity Filter', 'Гравитационный фильтр', ['Purified water W0. Filter pad in the tool slot.'], ['Очищенная вода W0. Прокладка — в слот инструмента.']),
    'measuring_vessel': ('Measuring Vessel', 'Мерный смесительный сосуд', ['Mineral suspension and growth medium G0.'], ['Минеральная взвесь и ростовая среда G0.']),
    'hand_substrate': ('Hand Growth Substrate', 'Ручная ростовая подложка', ['Place against the colony tissue: it feeds on the colony N0.', 'Soft tissue, membranes, the first growth bed.'],
                       ['Поставьте вплотную к ткани колонии: питается её N0.', 'Мягкая ткань, мембраны, первое ростовое ложе.']),
    'growth_bed': ('Growth Bed', 'Ростовое ложе', ['Place against the colony tissue. Grows tissues and ULV organs (feeding maw).'],
                   ['Ставится вплотную к ткани колонии. Выращивает ткани и ULV-органы (пасть-кормушку).']),
}
# сосуды Био §20.2 (D72): id, толщина px, цвет — как в VesselBlock.SPECS
VESSELS = [('tissue_microchannel', 4, (192, 80, 90)), ('capillary_bundle', 6, (176, 56, 72)),
           ('narrow_fibrous_vessel', 8, (160, 64, 80)), ('working_fibrous_vessel', 12, (144, 48, 64))]
BLOCKS.update({
    'forming_nest': ('Forming Nest', 'Формовочное гнездо', ['LV organ: grows LV parts and organs. Buffers: 250 N1 and 250 E1 (ladle, capsule).',
                                                            'Upkeep N1 0.020, E1 0.010 mB/t while working; 25% N1 idle.'],
                     ['LV-орган: растит LV-детали и органы. Буферы: 250 N1 и 250 E1 (ковш, капсула).', 'Расход N1 0,020, E1 0,010 мБ/т в работе; 25% N1 в простое.']),
    'nutrient_gland': ('Nutrient Gland', 'Питательная железа', ['LV organ: N1 in 20 s, E1 in 30 s, G1 in 40 s.', 'Buffers 250 N1 + 250 E1; N1 0.020, E1 0.020 mB/t.'],
                       ['LV-орган: N1 за 20 с, E1 за 30 с, G1 за 40 с.', 'Буферы 250 N1 + 250 E1; N1 0,020, E1 0,020 мБ/т.']),
    'membrane_filter': ('Membrane Filter', 'Мембранный фильтр', ['LV organ: purified water W0 in 20 s. Filter pad in the tool slot.', 'Body buffer 250 N1, 0.005 mB/t.'],
                        ['LV-орган: очищенная вода W0 за 20 с. Прокладка — в слот инструмента.', 'Буфер корпуса 250 N1, 0,005 мБ/т.']),
    'check_valve': ('Check Valve', 'Обратный клапан', ['LV: lets fluid through only the way you looked when placing it.', 'Opens at 0.002 atm, up to 128 mB/t.'],
                    ['LV: пропускает только туда, куда вы смотрели при установке.', 'Открывается от 0,002 атм, до 128 мБ/т.']),
    'manual_clamp': ('Manual Clamp', 'Ручной зажим', ['ULV, non-living: right-click — tighten (5 steps up to closed).', 'Sneak + right-click — show the network.'],
                     ['ULV, неживой: ПКМ — пережать сильнее (5 ступеней до закрытия).', 'Shift + ПКМ — показать сеть.']),
    'living_tank': ('Living Tank', 'Живой резервуар', ['LV, 8000 mB, open sac (no pressure of its own). Sneak + right-click: receive / supply.'],
                    ['LV, 8000 мБ, открытый мешок (сам давления не создаёт). Shift + ПКМ: приём / выдача.']),
    'muscle_pump': ('Muscle Pump', 'Мышечный насос', ['LV: takes from the back, pushes into vessels in front (where you looked). Up to 0.200 atm, 128 mB/t.',
                                                      'Buffers: 250 N1 (0.010 mB/t always) and 250 E1 (work).'],
                    ['LV: берёт сзади, подаёт в сосуды спереди (куда вы смотрели). До 0,200 атм, 128 мБ/т.', 'Буферы: 250 N1 (0,010 мБ/т всегда) и 250 E1 (работа).']),
    'tissue_microchannel': ('Tissue Microchannel', 'Тканевый микроканал', ['ULV vessel: 4 mB/t, 10 mB. Lives on colony N0.'], ['Сосуд ULV: 4 мБ/т, 10 мБ. Живёт на N0 колонии.']),
    'capillary_bundle': ('Capillary Bundle', 'Капиллярный пучок', ['ULV vessel: 16 mB/t, 40 mB. Lives on colony N0.'], ['Сосуд ULV: 16 мБ/т, 40 мБ. Живёт на N0 колонии.']),
    'narrow_fibrous_vessel': ('Narrow Fibrous Vessel', 'Узкий волокнистый сосуд', ['LV vessel: 32 mB/t, 100 mB. Lives on colony N0.'], ['Сосуд LV: 32 мБ/т, 100 мБ. Живёт на N0 колонии.']),
    'working_fibrous_vessel': ('Working Fibrous Vessel', 'Рабочий волокнистый сосуд', ['LV vessel: 128 mB/t, 400 mB. Lives on colony N0.'], ['Сосуд LV: 128 мБ/т, 400 мБ. Живёт на N0 колонии.']),
})
FLUIDS = {'n0': ('Nutrient Medium N0', 'Питательная среда N0'), 'primary_fraction': ('Primary Growth Fraction', 'Первичная ростовая фракция'),
          'runoff': ('Organic Runoff', 'Органический сток'), 'hydrolysate': ('Meat Hydrolysate', 'Мясной гидролизат'),
          'suspension': ('Mineral Suspension', 'Минеральная взвесь'), 'binder': ('Liquid Binder', 'Жидкое связующее'),
          'g0': ('Growth Medium G0', 'Ростовая среда G0'), 'extract': ('Plant Extract', 'Растительный экстракт'),
          'emulsion': ('Lipid Emulsion', 'Липидная эмульсия'), 'concentrate': ('Energy Concentrate', 'Энергетический концентрат'),
          'w0': ('Purified Water W0', 'Очищенная вода W0'), 'n1': ('Nutrient Medium N1', 'Питательная среда N1'),
          'e1': ('Muscle Energy Emulsion E1', 'Мышечная энергетическая эмульсия E1'), 'g1': ('Growth Medium G1', 'Ростовая среда G1')}
FLUID_RGB = {'n0': (192, 98, 90), 'primary_fraction': (232, 154, 174), 'runoff': (106, 88, 56), 'hydrolysate': (168, 63, 54),
             'suspension': (214, 207, 188), 'binder': (220, 196, 138), 'g0': (156, 56, 104), 'extract': (143, 176, 80),
             'emulsion': (232, 210, 160), 'concentrate': (216, 154, 48), 'w0': (110, 168, 224), 'n1': (208, 120, 104),
             'e1': (232, 176, 64), 'g1': (176, 72, 128)}
GUI = {
    'gui.iis.bio.pocket': ('Digestive Pocket of the Core', 'Пищеварительный карман ядра'),
    'gui.iis.bio.tool': ('Tool', 'Инстр.'),
    'gui.iis.bio.pocket.mode.0': ('Mode: nutrition', 'Режим: питание'),
    'gui.iis.bio.pocket.mode.1': ('Mode: hydrolysate', 'Режим: гидролизат'),
    'gui.iis.bio.pocket.mode.2': ('Mode: budding', 'Режим: почкование'),
    'gui.iis.bio.status.idle': ('Idle', 'Ожидает'),
    'gui.iis.bio.status.running': ('Working', 'Работает'),
    'gui.iis.bio.status.waiting_input': ('Missing ingredients or tool', 'Не хватает состава или инструмента'),
    'gui.iis.bio.status.no_heat': ('No heat: light the hearth below', 'Нет жара: разожгите очаг снизу'),
    'gui.iis.bio.status.no_colony': ('Not connected to a colony', 'Не связано с колонией'),
    'gui.iis.bio.status.no_nutrient': ('The colony has no N0', 'У колонии нет N0'),
    'gui.iis.bio.status.output_full': ('Output full: empty it', 'Выход полон: опорожните'),
    'gui.iis.bio.tank.input': ('Input: %s %s / %s mB', 'Вход: %s %s / %s мБ'),
    'gui.iis.bio.tank.output': ('Product: %s %s / %s mB', 'Продукт: %s %s / %s мБ'),
    'gui.iis.bio.tank.colony': ('Colony reserve: %s %s / %s mB', 'Запас колонии: %s %s / %s мБ'),
    'gui.iis.bio.progress': ('Batch: %s%%', 'Партия: %s%%'),
    'message.iis.bio.ladle.dose': ('Ladle portion: %s mB', 'Порция ковша: %s мБ'),
    'message.iis.bio.ladle.poured': ('Poured out', 'Вылито'),
    'message.iis.bio.ladle.poured_into': ('Poured %s mB of %s', 'Налито %s мБ: %s'),
    'message.iis.bio.ladle.took': ('Took %s mB of %s', 'Набрано %s мБ: %s'),
    'message.iis.bio.tank.no_room': ('No suitable tank or no room', 'Нет подходящего бака или места'),
    'message.iis.bio.tank.empty': ('Nothing to take', 'Нечего набрать'),
    'message.iis.bio.hearth.full': ('The hearth is full of fuel', 'Очаг полон топлива'),
    'message.iis.bio.hearth.heat': ('Heat left: %s s', 'Жара осталось: %s с'),
    'message.iis.bio.scrape.done': ('Scraped a primitive culture', 'Соскоб: неразвитая культура'),
    'message.iis.bio.scrape.dead': ('This tissue belongs to no living colony', 'Эта ткань не принадлежит живой колонии'),
    'jei.iis.bio.butcher': ('Butcher Block', 'Разделочная площадка'),
    'jei.iis.bio.mortar': ('Stone Mortar', 'Каменная ступа'),
    'jei.iis.bio.pot': ('Cooking Pot (hearth below)', 'Варочный сосуд на очаге'),
    'jei.iis.bio.mixer': ('Measuring Vessel', 'Мерный сосуд'),
    'jei.iis.bio.pocket': ('Digestive Pocket of the Core', 'Пищеварительный карман ядра'),
    'jei.iis.bio.substrate': ('Hand Growth Substrate', 'Ручная ростовая подложка'),
    'jei.iis.bio.bed': ('Growth Bed', 'Ростовое ложе'),
    'jei.iis.bio.press': ('Hand Press', 'Ручной отжим'),
    'jei.iis.bio.filter': ('Gravity Filter', 'Гравитационный фильтр'),
    'gui.iis.bio.hearth.heat': ('Heat: %s s', 'Жар: %s с'),
    'message.iis.bio.vessel.info': ('%s: %s / %s mB, flow up to %s mB/t', '%s: %s / %s мБ, поток до %s мБ/т'),
    'message.iis.bio.vessel.starving': ('Starving, no colony N0 here — not pumping. %s: %s / %s mB', 'Голодает (нет N0 колонии) — не качает. %s: %s / %s мБ'),
    'jei.iis.bio.nest': ('Forming Nest (LV)', 'Формовочное гнездо (LV)'),
    'jei.iis.bio.gland': ('Nutrient Gland (LV)', 'Питательная железа (LV)'),
    'jei.iis.bio.membrane_filter': ('Membrane Filter (LV)', 'Мембранный фильтр (LV)'),
    'gui.iis.bio.bed.mode.0': ('Mode: tissues and organs', 'Режим: ткани и органы'),
    'gui.iis.bio.bed.mode.1': ('Mode: valve petals', 'Режим: лепестки'),
    'gui.iis.bio.nest.mode.0': ('Mode: parts and organs', 'Режим: детали и органы'),
    'gui.iis.bio.nest.mode.1': ('Mode: valve petals', 'Режим: лепестки'),
    'gui.iis.bio.tank.buffer': ('Organ buffer: %s %s / %s mB', 'Буфер органа: %s %s / %s мБ'),
    'message.iis.bio.vessel.content': ('%s: %s / %s mB', '%s: %s / %s мБ'),
    'message.iis.bio.vessel.hydraulics': ('Pressure %s atm (safe %s, burst %s), flow %s of %s mB/t, wear D %s',
                                          'Давление %s атм изб. (безопасно %s, разрыв %s), поток %s из %s мБ/т, износ D %s'),
    'message.iis.bio.vessel.starving': ('Starving: no colony N0 here — the vessel is closed', 'Голодает (нет N0 колонии) — сосуд закрыт'),
    'message.iis.bio.vessel.overpressure': ('Overpressure: the wall wears out, at the burst pressure it tears', 'Перегрузка: стенка изнашивается, на разрывном давлении лопнет'),
    'message.iis.bio.vessel.repaired': ('Patched: wear D %s', 'Заплатка: износ D %s'),
    'message.iis.bio.vessel.replace': ('Too worn (D %s): only replacement helps', 'Слишком изношен (D %s): только замена'),
    'message.iis.bio.network': ('Network: %s parts, %s %s / %s mB, %s, head %s atm, flow %s mB/t to %s receivers',
                                'Сеть: %s участков, %s %s / %s мБ, %s, напор %s атм, поток %s мБ/т в %s приёмника'),
    'message.iis.bio.network.no_pump': ('no pump (flows only downhill)', 'нет насоса (течёт только вниз)'),
    'message.iis.bio.network.pump.core': ('pump: colony core (ULV)', 'насос: ядро колонии (ULV)'),
    'message.iis.bio.network.pump.muscle': ('pump: muscle pump (LV)', 'насос: мышечный (LV)'),
    'message.iis.bio.pump.info': ('Muscle pump: N1 %s / 250, E1 %s / 250 mB; head %s atm, flow %s mB/t',
                                  'Мышечный насос: N1 %s / 250, E1 %s / 250 мБ; напор %s атм, поток %s мБ/т'),
    'message.iis.bio.pump.starving': ('Muscle pump starving: no N1 (N1 %s, E1 %s) — stopped', 'Насос голодает: нет N1 (N1 %s, E1 %s) — стоит'),
    'message.iis.bio.living_tank.receive': ('Living tank (receive): %s %s / %s mB', 'Живой резервуар (приём): %s %s / %s мБ'),
    'message.iis.bio.living_tank.supply': ('Living tank (supply): %s %s / %s mB', 'Живой резервуар (выдача): %s %s / %s мБ'),
    'message.iis.bio.living_tank.starving': ('Living tank starving (no colony N0): %s %s / %s mB', 'Живой резервуар голодает (нет N0 колонии): %s %s / %s мБ'),
    'message.iis.bio.clamp.0': ('Clamp open', 'Зажим открыт'),
    'message.iis.bio.clamp.1': ('Clamp: +0.0025 atm·t/mB', 'Зажим: +0,0025 атм·т/мБ'),
    'message.iis.bio.clamp.2': ('Clamp: +0.01 atm·t/mB', 'Зажим: +0,01 атм·т/мБ'),
    'message.iis.bio.clamp.3': ('Clamp: +0.04 atm·t/mB', 'Зажим: +0,04 атм·т/мБ'),
    'message.iis.bio.clamp.4': ('Clamp closed', 'Зажим закрыт'),
    'jei.iis.bio.heat': ('Needs heat from the hearth', 'Нужен жар очага'),
    'jei.iis.bio.upkeep': ('Feeds on colony N0: %s mB/t', 'Питается N0 колонии: %s мБ/т'),
    'jei.iis.bio.per_nutrition': ('N0: %s mB per food point', 'N0: %s мБ за очко сытости'),
    'item.iis.bio.lineage': ('Colony line %s', 'Линия колонии %s'),
    'item.iis.measuring_ladle.content': ('%s: %s / %s mB', '%s: %s / %s мБ'),
    'item.iis.measuring_ladle.dose': ('Portion: %s mB', 'Порция: %s мБ'),
    # пасть теперь отдаёт пищу в карман
    'message.iis.bio.maw.fed': ('Swallowed into the digestive pocket (colony N0 %s/%s)', 'Проглочено в пищеварительный карман (N0 колонии %s/%s)'),
    'message.iis.bio.maw.full': ('The digestive pocket is full', 'Пищеварительный карман полон'),
    'message.iis.bio.maw.ready': ('The maw takes any food. Ground masses digest far better.', 'Пасть берёт любую пищу. Перетёртые массы перевариваются намного лучше.'),
}


# ----------------------------------------------------------------------------------------------------------------
# Art (16×16, procedural)
# ----------------------------------------------------------------------------------------------------------------
def _noise(seed, size=16):
    a = np.random.default_rng(seed).random((size, size))
    for _ in range(2):
        a = (np.roll(a, 1, 0) + np.roll(a, -1, 0) + np.roll(a, 1, 1) + np.roll(a, -1, 1) + a * 2) / 6
    return (a - a.min()) / (a.max() - a.min() + 1e-9)


def _blob(c0, c1, seed, r=5.5, cx=7.5, cy=8.5, hi=(255, 230, 220), squash=1.0):
    n = _noise(seed)
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.hypot((xx - cx), (yy - cy) * squash) + (n - 0.5) * 2.2
    a = d < r
    t = np.clip(d / r, 0, 1)[..., None]
    rgb = np.array(c0, float) * (1 - t) + np.array(c1, float) * t
    edge = a & (d > r - 1.2)
    rgb[edge] *= 0.6
    spot = a & (n > 0.82)
    rgb[spot] = hi
    return _img(rgb, a)


def _img(rgb, alpha):
    out = np.zeros((16, 16, 4), np.uint8)
    out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    out[..., 3] = np.where(alpha, 255, 0).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def _strands(color, seed, n=5, twist=False):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rnd = np.random.default_rng(seed)
    for k in range(n):
        x0 = 3 + k * 2 + rnd.integers(-1, 2)
        pts = [(x0 + (np.sin(y * (0.9 if twist else 0.5) + k) * (2 if twist else 1)), y) for y in range(2, 15)]
        shade = tuple(int(c * (0.75 + 0.25 * (k % 2))) for c in color) + (255,)
        d.line(pts, fill=shade, width=1)
    return im


def _draw(fn):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    fn(ImageDraw.Draw(im))
    return im


def item_art(iid):
    if iid == 'meat_mass':
        return _blob((206, 70, 70), (130, 26, 36), 1)
    if iid == 'plant_mass':
        return _blob((136, 170, 70), (70, 96, 34), 2, hi=(200, 220, 140))
    if iid == 'rotten_mass':
        return _blob((120, 130, 80), (70, 70, 46), 3, hi=(150, 160, 100))
    if iid == 'plant_fiber':
        return _strands((214, 196, 110), 4)
    if iid == 'connective_fiber':
        return _strands((236, 190, 190), 5)
    if iid == 'sinew_cord':
        return _strands((200, 150, 120), 6, n=3, twist=True)
    if iid == 'binding_gel':
        im = _blob((236, 206, 130), (190, 150, 70), 7, r=5, hi=(255, 250, 220))
        a = np.array(im)
        a[..., 3] = np.where(a[..., 3] > 0, 210, 0)
        return Image.fromarray(a, 'RGBA')
    if iid == 'bone_fragment':
        return _draw(lambda d: (d.polygon([(4, 11), (8, 4), (12, 6), (9, 12)], fill=(228, 220, 196, 255), outline=(150, 140, 120, 255))))
    if iid == 'bone_rib':
        return _draw(lambda d: d.arc([2, 2, 16, 18], 180, 280, fill=(232, 224, 200, 255), width=3))
    if iid == 'bone_powder':
        return _draw(lambda d: (d.ellipse([3, 8, 13, 14], fill=(236, 232, 220, 255), outline=(170, 165, 150, 255)), d.ellipse([5, 6, 11, 11], fill=(246, 244, 236, 255))))
    if iid == 'woven_frame':
        def f(d):
            for k in range(3, 14, 3):
                d.line([(k, 2), (k, 13)], fill=(170, 130, 70, 255))
                d.line([(2, k), (13, k)], fill=(140, 104, 56, 255))
            d.rectangle([2, 2, 13, 13], outline=(110, 80, 40, 255))
        return _draw(f)
    if iid == 'growth_substrate':
        def f(d):
            d.rectangle([2, 5, 13, 11], fill=(150, 110, 70, 255), outline=(90, 64, 40, 255))
            for x in range(3, 13, 2):
                d.point((x, 7), fill=(210, 110, 120, 255))
                d.point((x + 1, 9), fill=(120, 170, 80, 255))
        return _draw(f)
    if iid == 'organic_residue':
        return _blob((96, 74, 50), (54, 40, 28), 8, r=4.5, hi=(120, 96, 64))
    if iid == 'soft_tissue':
        return _blob((236, 140, 150), (170, 60, 80), 9, hi=(255, 210, 210))
    if iid == 'living_membrane':
        im = _blob((240, 160, 180), (190, 90, 120), 10, r=6.5, squash=1.6, hi=(255, 230, 240))
        a = np.array(im)
        a[..., 3] = np.where(a[..., 3] > 0, 190, 0)
        return Image.fromarray(a, 'RGBA')
    if iid == 'primitive_culture':
        return _blob((170, 90, 220), (90, 30, 90), 11, r=4.5, hi=(220, 180, 255))
    if iid == 'base_seed':
        im = _blob((220, 60, 80), (110, 20, 50), 12, r=4.8, cy=9.5, hi=(140, 255, 170))
        d = ImageDraw.Draw(im)
        d.line([(8, 5), (8, 2)], fill=(120, 230, 150, 255))
        return im
    if iid == 'filter_pad':
        def f(d):
            d.rectangle([2, 4, 13, 11], fill=(200, 184, 120, 255), outline=(130, 110, 60, 255))
            for k in range(3, 13, 2):
                d.line([(k, 4), (k, 11)], fill=(170, 150, 90, 255))
        return _draw(f)
    if iid == 'plant_residue':
        return _blob((110, 120, 60), (60, 66, 34), 14, r=4.5, hi=(140, 150, 90))
    if iid == 'energy_paste':
        return _blob((236, 180, 70), (170, 100, 30), 15, r=5, hi=(255, 240, 180))
    if iid == 'bone_ring':
        return _draw(lambda d: (d.ellipse([2, 2, 13, 13], outline=(232, 224, 200, 255), width=3), d.ellipse([3, 3, 12, 12], outline=(170, 160, 136, 255), width=1)))
    if iid == 'fibrous_tissue':
        im = _blob((220, 120, 120), (150, 50, 64), 16, hi=(255, 200, 200))
        d = ImageDraw.Draw(im)
        for k in range(4, 13, 3):
            d.line([(k, 4), (k + 2, 12)], fill=(250, 210, 190, 255))
        return im
    if iid == 'tissue_lining':
        im = _blob((230, 150, 170), (160, 70, 100), 17, r=6.5, squash=1.8, hi=(255, 230, 240))
        d = ImageDraw.Draw(im)
        d.line([(2, 8), (13, 8)], fill=(150, 60, 90, 255))
        return im
    if iid == 'muscle_seed':
        im = _blob((200, 60, 70), (110, 20, 40), 18, r=4.6, cy=9.5, hi=(255, 170, 150))
        d = ImageDraw.Draw(im)
        d.line([(6, 6), (10, 12)], fill=(250, 120, 110, 255))
        d.line([(10, 6), (6, 12)], fill=(250, 120, 110, 255))
        return im
    if iid == 'muscle_bundle':
        return _strands((200, 70, 70), 19, n=6, twist=True)
    if iid == 'muscle_node':
        im = _blob((190, 60, 64), (100, 20, 30), 20, r=5.5, hi=(255, 160, 150))
        d = ImageDraw.Draw(im)
        for k in range(4, 13, 2):
            d.arc([3, k - 3, 13, k + 3], 200, 340, fill=(240, 130, 120, 255))
        return im
    if iid == 'valve_petal':
        return _draw(lambda d: (d.polygon([(3, 13), (8, 2), (13, 13)], fill=(232, 150, 168, 220), outline=(170, 80, 110, 255)),
                                d.line([(8, 4), (8, 12)], fill=(190, 90, 120, 255))))
    if iid == 'fine_filter_pad':
        def f(d):
            d.rectangle([2, 4, 13, 11], fill=(220, 170, 160, 255), outline=(150, 90, 90, 255))
            for k in range(3, 13, 2):
                d.line([(k, 4), (k, 11)], fill=(190, 130, 120, 255))
                d.line([(2, k - 1), (13, k - 1)], fill=(200, 140, 130, 255)) if k < 12 else None
        return _draw(f)
    if iid.startswith('carcass_'):
        size = {'carcass_small': 3.5, 'carcass_medium': 5.0, 'carcass_large': 6.5, 'carcass_rotten': 5.0}[iid]
        c0, c1 = ((200, 70, 70), (110, 26, 36)) if iid != 'carcass_rotten' else ((130, 140, 90), (70, 70, 50))
        im = _blob(c0, c1, 13 + len(iid), r=size, squash=1.4, hi=(240, 220, 210))
        d = ImageDraw.Draw(im)
        for k in range(-1, 2):
            d.line([(7 + 2 * k, 8 - int(size / 2)), (7 + 2 * k, 8 + int(size / 2))], fill=(236, 226, 200, 255))
        return im
    if iid == 'bio_knife':
        return _draw(lambda d: (d.polygon([(9, 2), (13, 3), (8, 10), (6, 9)], fill=(70, 70, 78, 255), outline=(140, 140, 150, 255)),
                                d.line([(6, 10), (2, 14)], fill=(130, 90, 50, 255), width=2)))
    if iid == 'woven_mandrel':
        def f(d):
            d.rectangle([5, 2, 10, 13], fill=(190, 160, 90, 255), outline=(110, 80, 40, 255))
            for y in range(3, 13, 2):
                d.line([(5, y), (10, y + 1)], fill=(140, 104, 56, 255))
        return _draw(f)
    if iid == 'measuring_ladle':
        return _draw(lambda d: (d.ellipse([2, 8, 9, 14], fill=(150, 104, 60, 255), outline=(90, 60, 30, 255)), d.ellipse([3, 9, 8, 12], fill=(80, 54, 28, 255)),
                                d.line([(8, 10), (14, 2)], fill=(150, 104, 60, 255), width=2)))
    raise KeyError(iid)


def _tex(c0, c1, seed, pattern=None):
    n = _noise(seed)
    rgb = np.array(c0, float) * (1 - n[..., None]) + np.array(c1, float) * n[..., None]
    if pattern == 'planks':
        yy = np.mgrid[0:16, 0:16][0]
        rgb[(yy % 4) == 0] *= 0.7
    if pattern == 'cobble':
        rgb[(n > 0.55) & (n < 0.6)] *= 0.6
    return _img(rgb, np.ones((16, 16), bool))


def block_textures():
    t = {}
    t['butcher_block_top'] = _tex((150, 110, 70), (120, 86, 52), 21, 'planks')
    a = np.array(t['butcher_block_top'])
    n = _noise(22)
    stain = n > 0.78
    a[stain, :3] = (120, 30, 36)
    t['butcher_block_top'] = Image.fromarray(a, 'RGBA')
    t['butcher_block_side'] = _tex((128, 92, 56), (100, 72, 44), 23, 'planks')
    stone = ((128, 128, 128), (96, 96, 96))
    t['stone_bio'] = _tex(*stone, 24, 'cobble')
    inner = _tex((70, 70, 70), (50, 50, 50), 25)
    t['stone_inner'] = inner
    hearth = np.array(_tex(*stone, 26, 'cobble'))
    hearth[9:15, 4:12, :3] = (30, 24, 22)
    t['stone_hearth_front'] = Image.fromarray(hearth.copy(), 'RGBA')
    lit = hearth.copy()
    rnd = np.random.default_rng(27)
    for y in range(10, 15):
        for x in range(5, 11):
            lit[y, x, :3] = (255, int(120 + rnd.integers(0, 100)), 30) if rnd.random() < 0.8 else (200, 60, 20)
    t['stone_hearth_front_lit'] = Image.fromarray(lit, 'RGBA')
    t['substrate_top'] = _tex((160, 112, 72), (200, 110, 120), 28)
    a = np.array(t['substrate_top'])
    for k in range(1, 16, 3):
        a[k, :, :3] = (110, 80, 44)
        a[:, k, :3] = (130, 92, 52)
    t['substrate_top'] = Image.fromarray(a, 'RGBA')
    t['bed_top'] = _tex((200, 80, 96), (130, 30, 54), 29)
    a = np.array(t['bed_top'])
    n = _noise(30)
    a[(np.abs(n - 0.5) < 0.05), :3] = (150, 90, 230)
    t['bed_top'] = Image.fromarray(a, 'RGBA')
    t['bed_side'] = _tex((60, 44, 56), (100, 60, 70), 31)
    t['filter_top'] = _tex((200, 184, 120), (160, 140, 84), 32, 'planks')
    t['nest_top'] = _tex((176, 60, 84), (90, 24, 44), 33)
    a = np.array(t['nest_top'])
    n = _noise(34)
    a[(np.abs(n - 0.5) < 0.06), :3] = (230, 170, 120)
    t['nest_top'] = Image.fromarray(a, 'RGBA')
    t['gland'] = _tex((190, 96, 92), (120, 40, 52), 35)
    t['gland_top'] = _tex((220, 160, 110), (150, 80, 60), 36)
    t['membrane'] = _tex((236, 176, 196), (190, 110, 140), 37)
    t['sac'] = _tex((170, 70, 90), (100, 30, 50), 38)
    a = np.array(t['sac'])
    for y in range(2, 16, 4):
        a[y, :, :3] = (90, 24, 44)
    t['sac'] = Image.fromarray(a, 'RGBA')
    t['pump_side'] = _tex((170, 56, 60), (100, 24, 34), 39)
    pf = np.array(_tex((170, 56, 60), (100, 24, 34), 40))
    yy, xx = np.mgrid[0:16, 0:16]
    rr = np.hypot(xx - 7.5, yy - 7.5)
    pf[rr < 5, :3] = (60, 14, 24)
    pf[(rr >= 4) & (rr < 5.2), :3] = (230, 150, 150)
    t['pump_front'] = Image.fromarray(pf, 'RGBA')
    return t


def vessel_tex(color, seed, frames=4):
    """Живая трубка: влажная плоть цвета исполнения, продольные жилы, медленная пульсация (кадры)."""
    im = Image.new('RGBA', (16, 16 * frames))
    n = _noise(seed)
    yy, xx = np.mgrid[0:16, 0:16]
    for f in range(frames):
        k = 0.9 + 0.12 * np.sin(2 * np.pi * f / frames)
        rgb = np.array(color, float)[None, None, :] * (0.7 + 0.45 * n[..., None]) * k
        vein = (np.abs(np.sin((xx + np.roll(n, f, 0) * 3) * 0.9)) < 0.18)
        rgb[vein] = np.array((110, 30, 60)) * k
        glint = n > 0.86
        rgb[glint] = (255, 200, 210)
        im.paste(_img(rgb, np.ones((16, 16), bool)), (0, 16 * f))
    return im


def generate_b1(g):
    write, A, D, lang, block_tags, RES = g['write'], g['A'], g['D'], g['lang'], g['block_tags'], g['RES']

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    # recipes table (machine-readable, §22.12): unique ids
    ids = [r['id'] for r in RECIPES]
    assert len(ids) == len(set(ids)), 'duplicate bio recipe id'
    os.makedirs(os.path.join(RES, NS), exist_ok=True)
    with open(os.path.join(RES, NS, 'bio_recipes.json'), 'w', encoding='utf-8', newline=chr(10)) as f:
        json.dump(RECIPES, f, ensure_ascii=False, indent=1)
        f.write('\n')

    for tag, vals in ITEM_TAGS.items():
        write(os.path.join(D, NS, 'tags', 'item', tag + '.json'), {'replace': False, 'values': vals})
    for iid, (pattern, key) in CRAFTING.items():
        write(os.path.join(D, NS, 'recipe', 'bio', iid + '.json'), {
            'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
            'key': {k: v for k, v in key.items()}, 'result': {'id': f'{NS}:{iid}', 'count': 1}})
    write(os.path.join(D, 'neoforge', 'data_maps', 'item', 'compostables.json'),
          {'values': {f'{NS}:organic_residue': {'chance': 0.5}, f'{NS}:rotten_mass': {'chance': 0.3}, f'{NS}:plant_residue': {'chance': 0.5}}})

    # items
    for iid, (en, ru, ten, tru) in ITEMS.items():
        item_art(iid).save(tex(f'item/bio/{iid}.png'))
        write(os.path.join(A, 'models', 'item', f'bio/{iid}.json'), {'parent': 'minecraft:item/handheld' if iid in ('bio_knife', 'measuring_ladle') else 'minecraft:item/generated',
                                                                    'textures': {'layer0': f'{NS}:item/bio/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/bio/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)
        for k, (a, b) in enumerate(zip(ten, tru)):
            name(f'item.{NS}.{iid}.tooltip.{k}', a, b)

    # fluids
    for fid, (en, ru) in FLUIDS.items():
        c = FLUID_RGB[fid]
        n = _noise(40 + len(fid))
        rgb = np.array(c, float)[None, None, :] * (0.85 + 0.3 * n[..., None])
        _img(rgb, np.ones((16, 16), bool)).save(tex(f'block/fluid/bio_{fid}.png'))
        name(f'fluid_type.{NS}.bio_{fid}', en, ru)

    # blocks
    t = block_textures()
    for k, im in t.items():
        im.save(tex(f'block/bio/{k}.png'))

    def box(frm, to, faces):
        return {'from': frm, 'to': to, 'faces': {f: {'texture': faces.get(f, faces['side'])} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}

    def model(mid, els, particle):
        write(os.path.join(A, 'models', 'block', f'bio/{mid}.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': particle, **TEXS},
                                                                     'elements': els})

    TEXS = {k: f'{NS}:block/bio/{k}' for k in t}
    S = lambda k: '#' + k
    model('butcher_block', [box([0, 0, 0], [16, 14, 16], {'side': S('butcher_block_side'), 'up': S('butcher_block_top')})], TEXS['butcher_block_side'])
    model('stone_mortar', [box([4, 0, 4], [12, 2, 12], {'side': S('stone_bio')}), box([4, 2, 4], [5, 8, 12], {'side': S('stone_bio')}),
                           box([11, 2, 4], [12, 8, 12], {'side': S('stone_bio')}), box([5, 2, 4], [11, 8, 5], {'side': S('stone_bio')}),
                           box([5, 2, 11], [11, 8, 12], {'side': S('stone_bio')}), box([7, 3, 7], [9, 12, 9], {'side': S('butcher_block_side')})], TEXS['stone_bio'])
    pot = [box([1, 0, 1], [15, 2, 15], {'side': S('stone_bio'), 'up': S('stone_inner')}),
           box([1, 2, 1], [3, 12, 15], {'side': S('stone_bio')}), box([13, 2, 1], [15, 12, 15], {'side': S('stone_bio')}),
           box([3, 2, 1], [13, 12, 3], {'side': S('stone_bio')}), box([3, 2, 13], [13, 12, 15], {'side': S('stone_bio')})]
    model('stone_pot', pot, TEXS['stone_bio'])
    vessel = [box([0, 0, 0], [16, 4, 16], {'side': S('stone_bio')})]
    for x in (1, 6, 11):
        for z in (2, 9):
            vessel.append(box([x, 4, z], [x + 4, 10, z + 5], {'side': S('stone_bio'), 'up': S('stone_inner')}))
    model('measuring_vessel', vessel, TEXS['stone_bio'])
    for lit in ('', '_lit'):
        write(os.path.join(A, 'models', 'block', f'bio/stone_hearth{lit}.json'), {'parent': 'minecraft:block/orientable', 'textures': {
            'front': f'{NS}:block/bio/stone_hearth_front{lit}', 'side': TEXS['stone_bio'], 'top': TEXS['stone_inner']}})
    model('stone_press', [box([0, 0, 0], [16, 3, 16], {'side': S('butcher_block_side'), 'up': S('butcher_block_top')}),
                          box([2, 3, 2], [14, 9, 14], {'side': S('butcher_block_side'), 'up': S('stone_inner')}),
                          box([1, 9, 7], [15, 11, 9], {'side': S('butcher_block_side')}), box([7, 11, 7], [9, 15, 9], {'side': S('butcher_block_side')})],
          TEXS['butcher_block_side'])
    model('filter_vessel', pot + [box([3, 10, 3], [13, 11, 13], {'side': S('filter_top')}),
                                  box([1, 12, 7], [15, 13, 9], {'side': S('butcher_block_side')})], TEXS['stone_bio'])
    model('forming_nest', [box([0, 0, 0], [16, 5, 16], {'side': S('bed_side')}), box([1, 5, 1], [15, 9, 15], {'side': S('bed_side'), 'up': S('nest_top')}),
                           box([2, 9, 7], [3, 15, 9], {'side': S('butcher_block_side')}), box([13, 9, 7], [14, 15, 9], {'side': S('butcher_block_side')}),
                           box([3, 14, 7], [13, 15, 9], {'side': S('butcher_block_side')})], TEXS['nest_top'])
    model('nutrient_gland', [box([2, 0, 2], [14, 10, 14], {'side': S('gland'), 'up': S('gland_top')}), box([0, 1, 4], [3, 7, 12], {'side': S('gland')}),
                             box([13, 1, 4], [16, 7, 12], {'side': S('gland')}), box([4, 1, 0], [12, 7, 3], {'side': S('gland')}),
                             box([4, 1, 13], [12, 7, 16], {'side': S('gland')}), box([5, 10, 5], [11, 13, 11], {'side': S('gland_top')})], TEXS['gland'])
    model('membrane_filter', [box([1, 0, 1], [15, 12, 15], {'side': S('gland'), 'up': S('membrane')})], TEXS['gland'])
    model('hand_substrate', [box([0, 0, 0], [16, 4, 16], {'side': S('butcher_block_side'), 'up': S('substrate_top')})], TEXS['substrate_top'])
    model('growth_bed', [box([0, 0, 0], [16, 4, 16], {'side': S('bed_side')}), box([1, 4, 1], [15, 8, 15], {'side': S('bed_side'), 'up': S('bed_top')})], TEXS['bed_top'])
    write(os.path.join(A, 'models', 'block', 'bio/living_tank.json'), {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'side': TEXS['sac'], 'top': TEXS['gland_top'], 'bottom': TEXS['bed_side']}})
    write(os.path.join(A, 'models', 'block', 'bio/muscle_pump.json'), {'parent': 'minecraft:block/orientable_with_bottom', 'textures': {
        'front': TEXS['pump_front'], 'side': TEXS['pump_side'], 'top': TEXS['pump_side'], 'bottom': TEXS['pump_side']}})
    write(os.path.join(A, 'blockstates', 'muscle_pump.json'), {'variants': {
        f'facing={fc}': dict({'model': f'{NS}:block/bio/muscle_pump'}, **r) for fc, r in {
            'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}.items()}})
    write(os.path.join(A, 'items', 'muscle_pump.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/muscle_pump'}})
    for bid in ('butcher_block', 'stone_mortar', 'stone_pot', 'measuring_vessel', 'hand_substrate', 'growth_bed', 'stone_press', 'filter_vessel',
                'forming_nest', 'nutrient_gland', 'membrane_filter', 'living_tank'):
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/bio/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/{bid}'}})
    # очаг поворачивается к игроку, как печь (D72)
    yrot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    write(os.path.join(A, 'blockstates', 'stone_hearth.json'), {'variants': {
        f'facing={fc},lit={str(lt).lower()}': dict({'model': f'{NS}:block/bio/stone_hearth' + ('_lit' if lt else '')}, **({'y': r} if r else {}))
        for fc, r in yrot.items() for lt in (False, True)}})
    # сосуды Био: живая трубка по толщине исполнения, соединения — multipart как у труб
    for vid, thick, color in VESSELS:
        vt = f'{NS}:block/bio/{vid}'
        vessel_tex(color, 70 + thick).save(tex(f'block/bio/{vid}.png'))
        write(tex(f'block/bio/{vid}.png.mcmeta'), {'animation': {'frametime': 6, 'interpolate': True}})
        lo, hi = (16 - thick) / 2, (16 + thick) / 2
        allf = ('north', 'south', 'east', 'west', 'up', 'down')
        write(os.path.join(A, 'models', 'block', f'bio/{vid}_core.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': vt, 'v': vt},
              'elements': [{'from': [lo, lo, lo], 'to': [hi, hi, hi], 'faces': {f: {'texture': '#v'} for f in allf}}]})
        write(os.path.join(A, 'models', 'block', f'bio/{vid}_arm.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': vt, 'v': vt},
              'elements': [{'from': [lo + 0.5, lo + 0.5, 0], 'to': [hi - 0.5, hi - 0.5, lo],
                            'faces': {f: dict({'texture': '#v'}, **({'cullface': 'north'} if f == 'north' else {})) for f in ('north', 'east', 'west', 'up', 'down')}}]})
        rot = {'north': {}, 'south': {'y': 180}, 'east': {'y': 90}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
        parts = [{'apply': {'model': f'{NS}:block/bio/{vid}_core'}}]
        for d_, r_ in rot.items():
            parts.append({'when': {d_: 'true'}, 'apply': dict({'model': f'{NS}:block/bio/{vid}_arm'}, **r_)})
        write(os.path.join(A, 'blockstates', vid + '.json'), {'multipart': parts})
        write(os.path.join(A, 'models', 'item', f'bio/{vid}.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': vt, 'v': vt},
              'elements': [{'from': [lo, lo, 0], 'to': [hi, hi, 16], 'faces': {f: {'texture': '#v'} for f in allf}}],
              'display': {'gui': {'rotation': [30, 45, 0], 'scale': [0.625, 0.625, 0.625]}}})
        write(os.path.join(A, 'items', vid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/bio/{vid}'}})
    # обратный клапан: утолщение с гребнем на выходе (FACING), соединения только по оси; зажим — деревянный хомут на капиллярном пучке
    vt = f'{NS}:block/bio/check_valve'
    vessel_tex((138, 48, 72), 91).save(tex('block/bio/check_valve.png'))
    write(tex('block/bio/check_valve.png.mcmeta'), {'animation': {'frametime': 6, 'interpolate': True}})
    allf = ('north', 'south', 'east', 'west', 'up', 'down')
    write(os.path.join(A, 'models', 'block', 'bio/check_valve_core.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': vt, 'v': vt, 'r': TEXS['butcher_block_side']},
          'elements': [{'from': [3, 3, 3], 'to': [13, 13, 13], 'faces': {f: {'texture': '#v'} for f in allf}},
                       {'from': [5, 5, 1], 'to': [11, 11, 3], 'faces': {f: {'texture': '#r'} for f in allf}}]})
    write(os.path.join(A, 'models', 'block', 'bio/check_valve_arm.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': vt, 'v': vt},
          'elements': [{'from': [4, 4, 0], 'to': [12, 12, 3], 'faces': {f: {'texture': '#v'} for f in ('north', 'east', 'west', 'up', 'down')}}]})
    rot6 = {'north': {}, 'south': {'y': 180}, 'east': {'y': 90}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    parts = [{'when': {'facing': fc}, 'apply': dict({'model': f'{NS}:block/bio/check_valve_core'}, **r)} for fc, r in rot6.items()]
    for d_, r_ in rot6.items():
        parts.append({'when': {d_: 'true'}, 'apply': dict({'model': f'{NS}:block/bio/check_valve_arm'}, **r_)})
    write(os.path.join(A, 'blockstates', 'check_valve.json'), {'multipart': parts})
    write(os.path.join(A, 'items', 'check_valve.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/check_valve_core'}})
    ct = f'{NS}:block/bio/capillary_bundle'
    write(os.path.join(A, 'models', 'block', 'bio/manual_clamp_core.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': TEXS['butcher_block_side'],
          'v': ct, 'w': TEXS['butcher_block_side']},
          'elements': [{'from': [5, 5, 5], 'to': [11, 11, 11], 'faces': {f: {'texture': '#v'} for f in allf}},
                       {'from': [4, 4, 6.5], 'to': [12, 12, 9.5], 'faces': {f: {'texture': '#w'} for f in allf}},
                       {'from': [7, 12, 7], 'to': [9, 15, 9], 'faces': {f: {'texture': '#w'} for f in allf}}]})
    write(os.path.join(A, 'models', 'block', 'bio/manual_clamp_arm.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': ct, 'v': ct},
          'elements': [{'from': [5.5, 5.5, 0], 'to': [10.5, 10.5, 5], 'faces': {f: {'texture': '#v'} for f in ('north', 'east', 'west', 'up', 'down')}}]})
    parts = [{'apply': {'model': f'{NS}:block/bio/manual_clamp_core'}}]
    for d_, r_ in rot6.items():
        parts.append({'when': {d_: 'true'}, 'apply': dict({'model': f'{NS}:block/bio/manual_clamp_arm'}, **r_)})
    write(os.path.join(A, 'blockstates', 'manual_clamp.json'), {'multipart': parts})
    write(os.path.join(A, 'items', 'manual_clamp.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/manual_clamp_core'}})
    write(os.path.join(A, 'items', 'stone_hearth.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/bio/stone_hearth'}})
    for bid, (en, ru, ten, tru) in BLOCKS.items():
        name(f'block.{NS}.{bid}', en, ru)
        for k, (a, b) in enumerate(zip(ten, tru)):
            name(f'block.{NS}.{bid}.tooltip.{k}', a, b)
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
    for bid in ('stone_mortar', 'stone_pot', 'stone_hearth', 'measuring_vessel', 'filter_vessel'):
        block_tags.setdefault('minecraft:mineable/pickaxe', []).append(f'{NS}:{bid}')
    block_tags.setdefault('minecraft:mineable/axe', []).append(f'{NS}:butcher_block')
    block_tags.setdefault('minecraft:mineable/axe', []).append(f'{NS}:stone_press')
    for key, (en, ru) in GUI.items():
        name(key, en, ru)
    return {'recipes': len(RECIPES), 'items': len(ITEMS), 'blocks': len(BLOCKS)}
