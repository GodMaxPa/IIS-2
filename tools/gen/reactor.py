"""Nuclear reactor as in IC2 and solar panels (D117).

Rods: GT_Loader_Item_Block_And_Fluid:97-152, GT_MachineRecipeLoader:1059-1061, 1221-1229, GT_IndustialCraftRecipesLoader:126-127. Parts and crafts: scripts/GT_IC2.zs of IIS 1 (lines 228-259, 350-355, 372-392, 445-490).
Solar covers: GT_Mod:501-530, GT_Cover_SolarPanel, GT_MachineRecipeLoader:415, 1334; config/GregTech/OverpoweredStuff.cfg of IIS 1 gives crafts up to HV.
IC2 is closed (D5): what the sources above do not give comes from memory of how IC2 plays and is marked in docs/PORTING_NOTES.md. All art for IC2 things is our own.
"""
import os
import random
import shutil

from PIL import Image

import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gear_art as art

NS = 'iis'
I = lambda x: f'{NS}:{x}'
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')
FUELS = (('uranium', 'Uranium', 'урановый', (70, 170, 70)), ('mox', 'MOX', 'MOX', (70, 160, 190)), ('thorium', 'Thorium', 'ториевый', (60, 110, 60)), ('naquadah', 'Naquadah', 'наквадовый', (40, 60, 40)))
SOLARS = (('solar_panel', '', '750', 'Solar Panel', 'SOLARPANEL'), ('solar_panel_8v', '8V', '751', 'Solar Panel (8V)', 'SOLARPANEL_8V'), ('solar_panel_lv', 'LV', '752', 'Solar Panel (LV)', 'SOLARPANEL_LV'),
          ('solar_panel_mv', 'MV', '753', 'Solar Panel (MV)', 'SOLARPANEL_MV'), ('solar_panel_hv', 'HV', '754', 'Solar Panel (HV)', 'SOLARPANEL_HV'), ('solar_panel_ev', 'EV', '755', 'Solar Panel (EV)', 'SOLARPANEL_EV'),
          ('solar_panel_iv', 'IV', '756', 'Solar Panel (IV)', 'SOLARPANEL_IV'), ('solar_panel_luv', 'LuV', '757', 'Solar Panel (LuV)', 'SOLARPANEL_LuV'), ('solar_panel_zpm', 'ZPM', '758', 'Solar Panel (ZPM)', 'SOLARPANEL_ZPM'),
          ('solar_panel_uv', 'UV', '759', 'Solar Panel (UV)', 'SOLARPANEL_UV'))


def icon(base, accent, seed, shape='rod'):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            cx, cy = abs(x - 7.5), abs(y - 7.5)
            on = {'rod': 5 <= x <= 10 and 1 <= y <= 14, 'dual': (3 <= x <= 6 or 9 <= x <= 12) and 1 <= y <= 14, 'quad': (2 <= x <= 6 or 9 <= x <= 13) and (1 <= y <= 6 or 9 <= y <= 14),
                  'plate': 2 <= x <= 13 and 2 <= y <= 13, 'grid': 2 <= x <= 13 and 2 <= y <= 13, 'round': cx * cx + cy * cy < 42}[shape]
            if not on:
                continue
            edge = {'rod': y <= 2 or y >= 13, 'dual': y <= 2 or y >= 13, 'quad': y in (1, 6, 9, 14), 'plate': x in (2, 13) or y in (2, 13), 'grid': x % 3 == 1 and y % 3 == 1, 'round': (x + y) % 4 == 0}[shape]
            c = accent if edge else base
            v = rnd.randint(-4, 4)
            im.putpixel((x, y), (max(0, min(255, c[0] + v)), max(0, min(255, c[1] + v)), max(0, min(255, c[2] + v)), 255))
    return art.shade(im)


def generate(g):
    write, A, D, lang, shaped, shapeless, tag_of, block_tags, ref = g['write'], g['A'], g['D'], g['lang'], g['shaped'], g['shapeless'], g['tag_of'], g['block_tags'], g['ref']
    BI = os.path.join(GT, 'blocks', 'iconsets')
    rec = {}

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def item(iid, en, ru, im):
        im.save(tex(f'item/{iid}.png'))
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)

    def block(bid, en, ru, faces):
        for f, im in faces.items():
            im.save(tex(f'block/reactor/{bid}_{f}.png'))
        t = lambda f: f'{NS}:block/reactor/{bid}_{f if f in faces else "side"}'
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube', 'textures': {'particle': t('side'), 'north': t('side'), 'south': t('side'), 'east': t('side'), 'west': t('side'), 'up': t('top'), 'down': t('side')}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
              'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [{'type': 'minecraft:item', 'name': I(bid)}]}]})
        block_tags.setdefault('minecraft:mineable/pickaxe', []).append(I(bid))
        name(f'block.{NS}.{bid}', en, ru)

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, keep=None, circuit=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': dur, 'eut': eut}
        if fin:
            r['fluid_inputs'] = [fin]
        if keep:
            r['keep'] = [keep]
        if circuit is not None:
            r['circuit'] = circuit
        rec[f'{rtype}/{rid}'] = r

    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1: {'id': i, 'count': c}
    P = lambda m: tag_of('plate', m)
    DU = lambda m: tag_of('dust', m)

    # --- rods ------------------------------------------------------------------------------------------------------------------
    item('fuel_rod_empty', 'Fuel Rod (Empty)', 'Топливный стержень (пустой)', icon((150, 150, 158), (95, 95, 105), 1))
    item('enriched_uranium', 'Enriched Uranium Nuclear Fuel', 'Обогащённое урановое ядерное топливо', icon((90, 190, 90), (50, 120, 50), 2, 'round'))
    item('mox_fuel', 'MOX Nuclear Fuel', 'Ядерное топливо MOX', icon((90, 180, 210), (50, 110, 140), 3, 'round'))
    shapes = {1: 'rod', 2: 'dual', 4: 'quad'}
    rus = {1: '', 2: 'двойной ', 4: 'счетверённый '}
    ens = {1: '', 2: 'Dual ', 4: 'Quad '}
    for k, (fid, en, ru, col) in enumerate(FUELS):
        for n in (1, 2, 4):
            item(f'rod_{fid}_{n}', f'{ens[n]}Fuel Rod ({en})', f'{rus[n]}топливный стержень ({ru})'.capitalize(), icon(col, (110, 110, 120), 10 + k * 3 + n, shapes[n]))
            item(f'rod_depleted_{fid}_{n}', f'{ens[n]}Fuel Rod (Depleted {en})', f'{rus[n]}топливный стержень (отработанный, {ru})'.capitalize(), icon((90, 90, 90), (55, 55, 60), 40 + k * 3 + n, shapes[n]))
        # GT_Loader_Item_Block_And_Fluid:122, 125: dual "RPR", quad "RPR/CPC/RPR"
        shaped(f'reactor/rod_{fid}_2', I(f'rod_{fid}_2'), 1, ['RPR'], {'R': I(f'rod_{fid}_1'), 'P': P('Iron')})
        shaped(f'reactor/rod_{fid}_4', I(f'rod_{fid}_4'), 1, ['RPR', 'CPC', 'RPR'], {'R': I(f'rod_{fid}_1'), 'P': P('Iron'), 'C': P('Copper')})
    # ProcessingGem:42-43: lapis is a vanilla item, the LZH condensator and the advanced exchanger need its plates
    mrec('forge_hammer', 'plate_lapis', [ing('minecraft:lapis_lazuli')], [out(ref('plate', 'Lapis'))], max(g['mass']('Lapis'), 1), 16)
    mrec('bender', 'gem_lapis', [ing('minecraft:lapis_lazuli')], [out(ref('plate', 'Lapis'))], max(g['mass']('Lapis') * 2, 1), 24)
    # quest 180: extruder or bender with circuit 2
    mrec('extruder', 'fuel_rod_empty', [ing(tag_of('ingot', 'Iron'))], [out(I('fuel_rod_empty'))], 200, 16, keep=I('shape_extruder_cell'))
    mrec('bender', 'fuel_rod_empty', [ing(P('Iron'))], [out(I('fuel_rod_empty'))], 200, 16, circuit=2)
    U, U235, PU = tag_of('ingot', 'Uranium'), tag_of('nugget', 'Uranium235'), tag_of('ingot', 'Plutonium')
    mrec('alloy_smelter', 'enriched_uranium', [ing(U, 6), ing(U235, 3)], [out(I('enriched_uranium'))], 100, 48)   # zs:259
    mrec('alloy_smelter', 'mox_fuel', [ing(U, 6), ing(PU, 3)], [out(I('mox_fuel'))], 100, 48)   # zs:258
    shaped('reactor/enriched_uranium', I('enriched_uranium'), 1, ['UUU', 'NNN', 'UUU'], {'U': U, 'N': U235})   # GT_IndustialCraftRecipesLoader:126
    shaped('reactor/mox_fuel', I('mox_fuel'), 1, ['UUU', 'NNN', 'UUU'], {'U': U, 'N': PU})   # :127
    E = I('fuel_rod_empty')
    mrec('canner', 'rod_uranium', [ing(E), ing(I('enriched_uranium'))], [out(I('rod_uranium_1'))], 200, 2)   # zs:228
    mrec('canner', 'rod_mox', [ing(E), ing(I('mox_fuel'))], [out(I('rod_mox_1'))], 200, 2)   # zs:229
    mrec('canner', 'rod_thorium', [ing(E), ing(DU('Thorium'), 3)], [out(I('rod_thorium_1'))], 200, 2)   # zs:231
    mrec('canner', 'rod_naquadah', [ing(E), ing(DU('NaquadahEnriched'), 3)], [out(I('rod_naquadah_1'))], 30, 16)   # GT_MachineRecipeLoader:1059
    # thermal centrifuge of spent rods. GT :138-141, :150-152 give thorium and naquadah; uranium and MOX are IC2's and from memory: uranium back and a little plutonium
    TC = lambda rid, outs: mrec('thermal_centrifuge', rid, [ing(I(rid))], outs, 500, 48)
    for n, k in ((1, 1), (2, 2), (4, 4)):
        TC(f'rod_depleted_uranium_{n}', [out(ref('dust', 'Uranium'), 4 * k), out(ref('tiny_dust', 'Plutonium'), k), out(ref('dust', 'Iron'), k)])
        TC(f'rod_depleted_mox_{n}', [out(ref('dust', 'Uranium'), 4 * k), out(ref('dust', 'Plutonium'), k), out(ref('dust', 'Iron'), k)])
    TC('rod_depleted_thorium_1', [out(ref('small_dust', 'Lutetium'), 2), out(ref('dust', 'Thorium')), out(ref('dust', 'Iron'))])
    TC('rod_depleted_thorium_2', [out(ref('dust', 'Lutetium')), out(ref('dust', 'Thorium'), 2), out(ref('dust', 'Iron'), 3)])
    TC('rod_depleted_thorium_4', [out(ref('dust', 'Lutetium'), 2), out(ref('dust', 'Thorium'), 4), out(ref('dust', 'Iron'), 6)])
    TC('rod_depleted_naquadah_1', [out(ref('dust', 'Naquadah')), out(ref('small_dust', 'Naquadria'), 2), out(ref('dust', 'Iron'))])
    TC('rod_depleted_naquadah_2', [out(ref('dust', 'Naquadah'), 2), out(ref('dust', 'Naquadria')), out(ref('dust', 'Iron'), 3)])
    TC('rod_depleted_naquadah_4', [out(ref('dust', 'Naquadah'), 4), out(ref('dust', 'Naquadria'), 2), out(ref('dust', 'Iron'), 6)])

    # --- heat parts -----------------------------------------------------------------------------------------------------------
    parts = (('reactor_vent', 'Heat Vent', 'Теплоотвод', (150, 155, 165), (60, 60, 70)), ('reactor_vent_core', 'Reactor Heat Vent', 'Реакторный теплоотвод', (190, 110, 70), (60, 60, 70)),
             ('reactor_vent_gold', 'Overclocked Heat Vent', 'Разогнанный теплоотвод', (215, 180, 60), (60, 60, 70)), ('reactor_vent_diamond', 'Advanced Heat Vent', 'Улучшенный теплоотвод', (110, 220, 220), (60, 60, 70)),
             ('reactor_vent_spread', 'Component Heat Vent', 'Компонентный теплоотвод', (160, 160, 210), (60, 60, 70)), ('reactor_heat_switch', 'Heat Exchanger', 'Теплообменник', (180, 120, 70), (220, 200, 120)),
             ('reactor_heat_switch_core', 'Reactor Heat Exchanger', 'Реакторный теплообменник', (190, 90, 60), (220, 200, 120)), ('reactor_heat_switch_spread', 'Component Heat Exchanger', 'Компонентный теплообменник', (210, 170, 60), (220, 200, 120)),
             ('reactor_heat_switch_diamond', 'Advanced Heat Exchanger', 'Продвинутый теплообменник', (70, 90, 190), (220, 200, 120)))
    for k, (iid, en, ru, b, a) in enumerate(parts):
        item(iid, en, ru, icon(b, a, 70 + k, 'grid'))
    for k, (iid, en, ru, b) in enumerate((('coolant_10k', '10k Coolant Cell', 'Охлаждающий стержень 10к', (70, 140, 200)), ('coolant_30k', '30k Coolant Cell', 'Охлаждающий стержень 30к', (60, 120, 190)),
                                          ('coolant_60k', '60k Coolant Cell', 'Охлаждающий стержень 60к', (50, 100, 180)), ('coolant_he_60k', '60k He Coolant Cell', 'Гелиевый стержень 60к', (200, 200, 120)),
                                          ('coolant_he_180k', '180k He Coolant Cell', 'Гелиевый стержень 180к', (200, 190, 90)), ('coolant_he_360k', '360k He Coolant Cell', 'Гелиевый стержень 360к', (200, 170, 60)),
                                          ('coolant_nak_60k', '60k NaK Coolant Cell', 'Стержень NaK 60к', (160, 110, 200)), ('coolant_nak_180k', '180k NaK Coolant Cell', 'Стержень NaK 180к', (140, 90, 190)),
                                          ('coolant_nak_360k', '360k NaK Coolant Cell', 'Стержень NaK 360к', (120, 70, 180)))):
        item(iid, en, ru, icon(b, (130, 130, 140), 90 + k))
    for k, (iid, en, ru, b) in enumerate((('reactor_condensator', 'RSH Condensator', 'Конденсатор на красном камне', (190, 50, 50)), ('reactor_condensator_lap', 'LZH Condensator', 'Конденсатор на лазурите', (50, 70, 190)))):
        item(iid, en, ru, icon(b, (220, 220, 230), 110 + k, 'round'))
    for k, (iid, en, ru, b) in enumerate((('reactor_plating', 'Reactor Plating', 'Реакторная обшивка', (130, 130, 140)), ('reactor_plating_heat', 'Heat-Capacity Reactor Plating', 'Теплоёмкая реакторная пластина', (190, 120, 80)),
                                          ('reactor_plating_explosive', 'Containment Reactor Plating', 'Защитная реакторная обшивка', (80, 80, 90)))):
        item(iid, en, ru, icon(b, (70, 70, 78), 120 + k, 'plate'))
    item('plate_dense_copper', 'Dense Copper Plate', 'Плотная медная пластина', icon((225, 130, 70), (160, 90, 45), 130, 'plate'))
    item('carbon_mesh', 'Raw Carbon Mesh', 'Углеткань', icon((60, 60, 65), (35, 35, 38), 131, 'grid'))
    item('carbon_plate', 'Carbon Plate', 'Углепластик', icon((45, 45, 50), (25, 25, 28), 132, 'plate'))
    # GT_Utility#getIntegratedCircuit bender "9 plates -> dense" (ProcessingPlate), the one dense plate that this slice needs
    mrec('bender', 'plate_dense_copper', [ing(P('Copper'), 9)], [out(I('plate_dense_copper'))], 900, 96, circuit=9)
    # IC2: two fibres make a mesh, the mesh compresses into a plate (the recipe is removed in zs:152 only to be replaced by the machine form)
    shaped('reactor/carbon_mesh', I('carbon_mesh'), 1, ['F', 'F'], {'F': I('carbon_fiber')})
    mrec('compressor', 'carbon_plate', [ing(I('carbon_mesh'))], [out(I('carbon_plate'))], 300, 2)

    C1 = 1
    CU, SN, AU, FOIL, BARS, MOT = P('Copper'), P('Tin'), P('Gold'), tag_of('foil', 'Aluminium'), 'minecraft:iron_bars', I('electric_motor_lv')
    V, VC, VG, HS, HSC, HSP = (I(x) for x in ('reactor_vent', 'reactor_vent_core', 'reactor_vent_gold', 'reactor_heat_switch', 'reactor_heat_switch_core', 'reactor_heat_switch_spread'))
    # zs:445-490
    AL = I('advanced_alloy_plate')
    mrec('forming_press', 'reactor_plating', [ing(AL), ing(P('Lead'))], [out(I('reactor_plating'))], 200, 480)
    mrec('forming_press', 'reactor_plating_heat', [ing(I('plate_dense_copper')), ing(I('reactor_plating'))], [out(I('reactor_plating_heat'))], 220, 480)
    mrec('forming_press', 'reactor_plating_explosive', [ing(AL, 8), ing(I('reactor_plating'))], [out(I('reactor_plating_explosive'))], 220, 480)
    B = '#c:circuits/basic'
    mrec('assembler', 'reactor_heat_switch', [ing(B), ing(CU, 5), ing(SN, 3)], [out(HS)], 200, 60, circuit=C1)
    mrec('assembler', 'reactor_heat_switch_core', [ing(HS), ing(CU, 8)], [out(HSC)], 200, 60, circuit=C1)
    mrec('assembler', 'reactor_heat_switch_spread', [ing(HS), ing(AU, 4)], [out(HSP)], 200, 60, circuit=C1)
    mrec('assembler', 'reactor_heat_switch_diamond', [ing(HS), ing(B, 3), ing(P('Lapis'), 4), ing(CU, 6), ing(SN, 3)], [out(I('reactor_heat_switch_diamond'))], 200, 240)
    mrec('assembler', 'reactor_vent', [ing(MOT), ing(BARS, 4), ing(FOIL, 16)], [out(V)], 200, 60, circuit=C1)
    mrec('assembler', 'reactor_vent_core', [ing(V), ing(CU, 8)], [out(VC)], 200, 60, circuit=C1)
    mrec('assembler', 'reactor_vent_gold', [ing(VC), ing(AU, 4)], [out(VG)], 200, 60, circuit=C1)
    mrec('assembler', 'reactor_vent_diamond', [ing(V), ing('minecraft:diamond'), ing(BARS, 10), ing(MOT), ing(FOIL, 16)], [out(I('reactor_vent_diamond'))], 200, 240)
    mrec('assembler', 'reactor_vent_spread', [ing(V), ing(BARS, 4), ing(SN, 4)], [out(I('reactor_vent_spread'))], 200, 60, circuit=C1)
    shaped('reactor/reactor_condensator', I('reactor_condensator'), 1, ['RSR', 'RVR', 'RSR'], {'R': P('RedAlloy'), 'S': HSC, 'V': VC})
    shapeless('reactor/reactor_condensator_refill', I('reactor_condensator'), 1, [I('reactor_condensator'), P('RedAlloy'), P('RedAlloy'), P('RedAlloy')])
    shaped('reactor/reactor_condensator_lap', I('reactor_condensator_lap'), 1, ['LVL', 'CLC', 'LSL'], {'L': P('Lapis'), 'V': VG, 'C': I('reactor_condensator'), 'S': HSP})
    shapeless('reactor/reactor_condensator_lap_refill', I('reactor_condensator_lap'), 1, [I('reactor_condensator_lap'), P('Lapis'), P('Lapis'), P('Lapis')])
    # coolant: IC2 10k from memory (tin around a water capsule); He and NaK GT_Loader_Item_Block_And_Fluid:98-113
    water = {'neoforge:ingredient_type': 'neoforge:components', 'items': I('capsule'), 'components': {f'{NS}:fluid_content': {'fluid': 'minecraft:water', 'amount': 1000}}}
    helium = {'neoforge:ingredient_type': 'neoforge:components', 'items': I('capsule'), 'components': {f'{NS}:fluid_content': {'fluid': I('helium'), 'amount': 1000}}}
    shaped('reactor/coolant_10k', I('coolant_10k'), 1, [' T ', 'TWT', ' T '], {'T': SN, 'W': water})
    shaped('reactor/coolant_30k', I('coolant_30k'), 1, ['TTT', 'CCC', 'TTT'], {'T': SN, 'C': I('coolant_10k')})
    shaped('reactor/coolant_60k', I('coolant_60k'), 1, ['TCT', 'TDT', 'TCT'], {'T': SN, 'C': I('coolant_30k'), 'D': I('plate_dense_copper')})
    shaped('reactor/coolant_he_60k', I('coolant_he_60k'), 1, [' P ', 'PCP', ' P '], {'P': SN, 'C': helium})
    shaped('reactor/coolant_he_180k', I('coolant_he_180k'), 1, ['PCP', 'PCP', 'PCP'], {'P': SN, 'C': I('coolant_he_60k')})
    shaped('reactor/coolant_he_360k', I('coolant_he_360k'), 1, ['PCP', 'PDP', 'PCP'], {'P': SN, 'C': I('coolant_he_180k'), 'D': I('plate_dense_copper')})
    shaped('reactor/coolant_nak_60k', I('coolant_nak_60k'), 1, ['TST', 'PCP', 'TST'], {'T': SN, 'S': DU('Sodium'), 'P': DU('Potassium'), 'C': I('coolant_10k')})
    shaped('reactor/coolant_nak_180k', I('coolant_nak_180k'), 1, ['PCP', 'PCP', 'PCP'], {'P': SN, 'C': I('coolant_nak_60k')})
    shaped('reactor/coolant_nak_360k', I('coolant_nak_360k'), 1, ['PCP', 'PDP', 'PCP'], {'P': SN, 'C': I('coolant_nak_180k'), 'D': I('plate_dense_copper')})
    # GT_MachineRecipeLoader:1221-1229: the freezer takes heat out of used coolant cells
    for cid, dur in (('coolant_10k', 100), ('coolant_30k', 300), ('coolant_60k', 600), ('coolant_he_60k', 600), ('coolant_he_180k', 1800), ('coolant_he_360k', 3600), ('coolant_nak_60k', 600), ('coolant_nak_180k', 1800), ('coolant_nak_360k', 3600)):
        mrec('vacuum_freezer', cid, [ing(I(cid))], [{'id': I(cid), 'count': 1, 'components': {'minecraft:damage': 0}}], dur, 120)

    # --- blocks -----------------------------------------------------------------------------------------------------------------
    def base(col, seed):
        rnd = random.Random(seed)
        im = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                c = 18 if x in (0, 15) or y in (0, 15) else rnd.randint(-5, 5)
                im.putpixel((x, y), tuple(max(0, min(255, col[k] + (c if c < 10 else -40))) for k in range(3)) + (255,))
        return im
    block('nuclear_reactor', 'Nuclear Reactor', 'Ядерный реактор', {'side': art.reactor_side(), 'top': art.reactor_top()})
    block('reactor_chamber', 'Reactor Chamber', 'Реакторная камера', {'side': art.chamber_side(), 'top': art.chamber_side()})
    art.reactor_gui().save(tex('gui/reactor/reactor.png'))
    sun = Image.open(os.path.join(BI, 'SOLARPANEL.png')).convert('RGBA').crop((0, 0, 16, 16))
    block('solar_panel_block', 'Solar Panel', 'Солнечная панель', {'side': base((110, 110, 120), 5), 'top': Image.alpha_composite(base((110, 110, 120), 6), sun)})
    # zs:350-355; the chamber is from memory of IC2 (lead plates around a machine hull)
    shaped('reactor/nuclear_reactor', I('nuclear_reactor'), 1, ['DAD', 'CHC', 'TCT'], {'D': '#c:circuits/data', 'A': I('robot_arm_ev'), 'C': I('reactor_chamber'), 'H': I('hull_ev'), 'T': I('reactor_reflector_thick')})
    shaped('reactor/reactor_chamber', I('reactor_chamber'), 1, ['LLL', 'LHL', 'LLL'], {'L': P('Lead'), 'H': I('hull_lv')})
    shaped('reactor/solar_panel_block', I('solar_panel_block'), 1, ['GGG', 'DSD', 'CHC'], {'G': 'minecraft:glass', 'D': DU('Coal'), 'S': I('silicon_wafer'), 'C': B, 'H': I('hull_lv')})

    # --- hazmat suit: zs:372-392 ------------------------------------------------------------------------------------------------
    R, O = P('Rubber'), '#c:dyes/orange'
    for iid, en, ru, pic in (('hazmat_helmet', 'Hazmat Helmet', 'Шлем радиационно-защитного костюма', 'round'), ('hazmat_chestplate', 'Hazmat Chestplate', 'Куртка радиационно-защитного костюма', 'plate'),
                               ('hazmat_leggings', 'Hazmat Leggings', 'Поножи радиационно-защитного костюма', 'dual'), ('rubber_boots', 'Rubber Boots', 'Резиновые ботинки', 'quad')):
        item(iid, en, ru, art.icon({'round': art.I_HAZ_HELMET, 'plate': art.I_HAZ_CHEST, 'dual': art.I_HAZ_LEGS}.get(pic, art.I_RUBBER_BOOTS), art.P_HAZMAT))
    shaped('reactor/hazmat_helmet', I('hazmat_helmet'), 1, [' O ', 'RGR', 'RBR'], {'O': O, 'R': R, 'G': 'minecraft:glass', 'B': BARS})
    shaped('reactor/hazmat_chestplate', I('hazmat_chestplate'), 1, ['ROR', 'RRR', 'RRR'], {'O': O, 'R': R})
    shaped('reactor/hazmat_leggings', I('hazmat_leggings'), 1, ['RRR', 'ROR', 'R R'], {'O': O, 'R': R})
    shaped('reactor/rubber_boots', I('rubber_boots'), 1, ['R R', 'R R', 'RWR'], {'R': R, 'W': '#minecraft:wool'})
    suit, suit_legs = art.hazmat()
    suit.save(tex('entity/equipment/humanoid/hazmat.png'))
    suit_legs.save(tex('entity/equipment/humanoid_leggings/hazmat.png'))
    write(os.path.join(A, 'equipment', 'hazmat.json'), {'layers': {'humanoid': [{'texture': I('hazmat')}], 'humanoid_leggings': [{'texture': I('hazmat')}]}})

    # --- solar panels: covers ---------------------------------------------------------------------------------------------------
    for sid, tier, src, en, ic in SOLARS:
        item(sid, en, {'': 'Солнечная панель (крышка)'}.get(tier, f'Солнечная панель ({tier})'), Image.open(os.path.join(GT, 'items', 'gt.metaitem.01', src + '.png')).convert('RGBA'))
        Image.open(os.path.join(BI, ic + '.png')).convert('RGBA').crop((0, 0, 16, 16)).save(tex(f'block/cover/{sid}.png'))
    C = '#c:circuits/'
    SW1, SW2, SW3 = I('silicon_wafer'), I('silicon_wafer_glowstone'), I('silicon_wafer_naquadah')
    # GT_Mod:502-508
    for k, w in enumerate((SW1, SW2, SW3)):
        shaped(f'cover/solar_panel_{k}', I('solar_panel'), 1, ['SGS', 'CPC'], {'S': w, 'G': 'minecraft:glass_pane', 'C': C + 'basic', 'P': I('carbon_plate')})
    for k, w in enumerate((SW2, SW3)):
        shaped(f'cover/solar_panel_8v_{k}', I('solar_panel_8v'), 1, ['SGS', 'CPC', 'R R'], {'S': w, 'G': 'minecraft:glass_pane', 'C': C + 'good', 'P': '#iis:wires/1x/graphene', 'R': P('GalliumArsenide')})
        shaped(f'cover/solar_panel_lv_{k}', I('solar_panel_lv'), 1, ['SGS', 'CPC', 'R R'], {'S': w, 'G': I('reinforced_glass'), 'C': C + 'advanced', 'P': '#iis:wires/4x/graphene', 'R': P('IndiumGalliumPhosphide')})
    # OverpoweredStuff.cfg of IIS 1 turns on 8V, LV, MV, HV; EV and above have no craft there either
    shaped('cover/solar_panel_8v_chain', I('solar_panel_8v'), 1, ['SSS', 'STS', 'SSS'], {'S': I('solar_panel'), 'T': C + 'good'})
    for out_id, lower, t in (('solar_panel_lv', 'solar_panel_8v', 'transformer_ulv'), ('solar_panel_mv', 'solar_panel_lv', 'transformer_lv'), ('solar_panel_hv', 'solar_panel_mv', 'transformer_mv')):
        shaped(f'cover/{out_id}_chain', I(out_id), 1, [' S ', 'STS', ' S '], {'S': I(lower), 'T': I(t)})
    # GT_MachineRecipeLoader:415, 1334 graphene
    mrec('assembler', 'graphene', [ing(DU('Graphite'), 8), ing(tag_of('foil', 'Silicon'))], [out(ref('small_dust', 'Graphene'))], 480, 240, fin={'ingredient': I('glue'), 'amount': 250})
    mrec('wiremill', 'graphene_dust', [ing(DU('Graphene'))], [out(I('wire_graphene_1x'))], 400, 2)

    for k, en, ru in (('itemGroup.iis.reactor', 'IIS 2.0: Reactor', 'ИИС 2.0: реактор'), ('gui.iis.reactor.heat', 'Heat: %s / %s', 'Нагрев: %s / %s'), ('gui.iis.reactor.output', 'Output: %s EU/t', 'Выход: %s EU/t'),
                      ('gui.iis.reactor.off', 'Off: give it a redstone signal', 'Выключен: нужен сигнал красного камня'), ('effect.iis.radiation', 'Radiation', 'Радиация'),
                      ('death.attack.iis.radiation', '%s was irradiated', '%s получил смертельную дозу радиации'),
                      ('item.iis.reactor.fuel', 'Fuel: %s / %s s, rods: %s', 'Топливо: %s / %s с, стержней: %s'), ('item.iis.reactor.reflector', 'Pulses left: %s', 'Осталось импульсов: %s'),
                      ('item.iis.reactor.reflector_iridium', 'Never wears out', 'Не изнашивается'), ('item.iis.reactor.plating', 'Reactor heat limit +%s', 'Предел нагрева реактора +%s'),
                      ('item.iis.reactor.spread', 'Cools each neighbour by %s/s', 'Охлаждает каждого соседа на %s в секунду'), ('item.iis.reactor.heat', 'Heat: %s / %s', 'Тепло: %s / %s'),
                      ('item.iis.reactor.vent', 'From the hull %s/s, into the air %s/s', 'Из корпуса %s в секунду, в воздух %s в секунду'), ('item.iis.reactor.switch', 'With neighbours %s/s, with the hull %s/s', 'С соседями %s в секунду, с корпусом %s в секунду'),
                      ('block.iis.nuclear_reactor.tooltip.0', '3 columns of 6 cells and one more for every reactor chamber beside it', '3 столбца по 6 ячеек и ещё столбец за каждую реакторную камеру рядом'),
                      ('block.iis.nuclear_reactor.tooltip.1', 'Works while it or a chamber gets a redstone signal; overheating destroys everything around', 'Работает при сигнале красного камня на нём или на камере; перегрев разрушает всё вокруг'),
                      ('gui.iis.reactor.heat_short', 'Heat %s%% (%s/s)', 'Нагрев %s%% (%s/с)'), ('gui.iis.reactor.stopped', 'Stopped', 'Остановлен'), ('gui.iis.reactor.running', 'Running', 'Работает'),
                      ('gui.iis.reactor.locked', 'Closed: put a reactor chamber next to the reactor', 'Закрыто: поставьте реакторную камеру рядом с реактором'),
                      ('gui.iis.reactor.chamber_alone', 'This chamber does not touch a reactor', 'Камера не примыкает к реактору'),
                      ('gui.iis.reactor.mark.40', 'From 40%: fire around the reactor', 'С 40 %: вокруг вспыхивает огонь'), ('gui.iis.reactor.mark.50', 'From 50%: water nearby boils away', 'С 50 %: вода рядом выкипает'),
                      ('gui.iis.reactor.mark.70', 'From 70%: radiation around', 'С 70 %: радиация вокруг'), ('gui.iis.reactor.mark.85', 'From 85%: blocks melt into lava', 'С 85 %: блоки плавятся в лаву'),
                      ('gui.iis.reactor.mark.100', 'At 100%: explosion', 'На 100 %: взрыв'),
                      ('block.iis.reactor_chamber.tooltip.0', 'Adds a column of cells to the reactor beside it', 'Добавляет столбец ячеек реактору рядом'),
                      ('block.iis.reactor_chamber.tooltip.1', 'Opens the reactor, takes and gives parts, gives energy', 'Открывает окно реактора, принимает и отдаёт детали, выдаёт энергию'),
                      ('block.iis.solar_panel_block.tooltip.0', '1 EU/t by day under an open sky', '1 EU/t днём под открытым небом')):
        name(k, en, ru)
    write(os.path.join(D, NS, 'damage_type', 'radiation.json'), {'message_id': 'iis.radiation', 'exhaustion': 0.0, 'scaling': 'never'})
    write(os.path.join(D, 'minecraft', 'tags', 'damage_type', 'bypasses_armor.json'), {'replace': False, 'values': [f'{NS}:electricity', f'{NS}:radiation', f'{NS}:oxygen_suffocation']})
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
