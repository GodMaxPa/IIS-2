"""IC2 gear (D118): batteries and crystals, nano and quantum armour, jetpack, batpacks, mining laser, nano saber, energy storage blocks, coal chain, item casings.

Recipes: scripts/GT_IC2.zs and GraviSuite.zs of IIS 1, GT_MachineRecipeLoader (lines at each recipe), GT_MetaGenerated_Item_01:473-480 for orbs.
IC2 is closed (D5): what those sources do not give comes from memory of how IC2 plays and is marked "memory" here and in docs/PORTING_NOTES.md. All art is our own.
"""
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gear_art as art

NS = 'iis'
I = lambda x: f'{NS}:{x}'

MASKS = {
    'helmet': ['................', '................', '....########....', '...##########...', '...##########...', '...##++++++##...', '...##++++++##...', '...##......##...', '...##......##...', '................',
               '................', '................', '................', '................', '................', '................'],
    'chest': ['................', '..###......###..', '..####....####..', '..############..', '..############..', '...##++++++##...', '....##++++##....', '....########....', '....########....', '....########....',
              '....########....', '....########....', '................', '................', '................', '................'],
    'legs': ['................', '....########....', '....########....', '....##++++##....', '....###..###....', '....###..###....', '....###..###....', '....###..###....', '....###..###....', '....###..###....',
             '....###..###....', '....###..###....', '................', '................', '................', '................'],
    'boots': ['................', '................', '................', '................', '................', '...###....###...', '...###....###...', '...###....###...', '...###....###...', '..####...####...',
              '.#####..#####...', '.#####..#####...', '.++++...++++....', '................', '................', '................'],
    'goggles': ['................', '................', '................', '................', '..############..', '.###++####++###.', '.###++####++###.', '..############..', '................', '................',
                '................', '................', '................', '................', '................', '................'],
    'pack': ['................', '....##....##....', '...##########...', '...##++++++##...', '...##++++++##...', '...##########...', '...##########...', '...##++++++##...', '...##++++++##...', '...##########...',
             '...##########...', '....##....##....', '................', '................', '................', '................'],
    'jet': ['................', '...###....###...', '..#####..#####..', '..#####..#####..', '..##+##..##+##..', '..##+######+##..', '..##+##..##+##..', '..#####..#####..', '..#####..#####..', '...###....###...',
            '...+++....+++...', '...+.+....+.+...', '................', '................', '................', '................'],
    'crystal': ['................', '.......##.......', '......####......', '.....##++##.....', '....##++++##....', '...##++++++##...', '..##++++++++##..', '..##++++++++##..', '...##++++++##...', '....##++++##....',
                '.....##++##.....', '......####......', '.......##.......', '................', '................', '................'],
    'battery': ['................', '......####......', '.....######.....', '.....##++##.....', '.....##++##.....', '.....##++##.....', '.....##++##.....', '.....##++##.....', '.....##++##.....', '.....##++##.....',
                '.....##++##.....', '.....######.....', '................', '................', '................', '................'],
    'laser': ['................', '.............##.', '............###.', '...........###..', '..........###...', '.........+##....', '........+##.....', '.......###......', '......####......', '.....####.......',
              '....###.#.......', '...###..........', '..###...........', '..##............', '................', '................'],
    'saber': ['................', '.............++.', '............+++.', '...........+++..', '..........+++...', '.........+++....', '........+++.....', '.......+++......', '......+++.......', '.....#++........',
              '...###+.........', '...##...........', '..###...........', '..#.............', '................', '................'],
    'ball': ['................', '................', '.....######.....', '....##++++##....', '...##++++++##...', '...#++++++++#...', '...#++++++++#...', '...#++++++++#...', '...##++++++##...', '....##++++##....',
             '.....######.....', '................', '................', '................', '................', '................'],
    'casing': ['................', '................', '................', '...##########...', '..##++++++++##..', '..#++++++++++#..', '..#++++++++++#..', '..#++++++++++#..', '..#++++++++++#..', '..##++++++++##..',
               '...##########...', '................', '................', '................', '................', '................'],
    'chip': ['................', '................', '...##########...', '...#++++++++#...', '...#+##++##+#...', '...#+##++##+#...', '...#++++++++#...', '...#++++++++#...', '...#+##++##+#...', '...#+##++##+#...',
             '...#++++++++#...', '...##########...', '................', '................', '................', '................'],
}


def mask_icon(shape, base, accent, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(MASKS[shape]):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            c = accent if ch == '+' else base
            v = rnd.randint(-3, 3)
            im.putpixel((x, y), (max(0, min(255, c[0] + v)), max(0, min(255, c[1] + v)), max(0, min(255, c[2] + v)), 255))
    return art.shade(im)


def generate(g):
    write, A, D, lang, shaped, tag_of, block_tags, ref, add_item_tag = g['write'], g['A'], g['D'], g['lang'], g['shaped'], g['tag_of'], g['block_tags'], g['ref'], g['add_item_tag']
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

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, fins=None, keep=None, circuit=None, temp=None, cleanroom=False):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': dur, 'eut': eut}
        if fin:
            r['fluid_inputs'] = [fin]
        if fins:
            r['fluid_inputs'] = fins
        if keep:
            r['keep'] = [keep]
        if circuit is not None:
            r['circuit'] = circuit
        if temp:
            r['temp'] = temp
        if cleanroom:
            r['cleanroom'] = True
        rec[f'{rtype}/{rid}'] = r

    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1: {'id': i, 'count': c}
    fl = lambda fid, mb: {'ingredient': f'{NS}:{fid}', 'amount': mb}
    P = lambda m: tag_of('plate', m)
    DU = lambda m: tag_of('dust', m)
    C = '#c:circuits/'
    CAB = lambda m: f'#iis:cables/1x/{m}'
    WIRE = lambda m: f'#iis:wires/1x/{m}'

    # --- batteries ---------------------------------------------------------------------------------------------------------------
    def gem(a, v, l, h, g):
        return dict(art.P_QUANTUM, A=a, V=v, L=l, H=h, G=g)

    RED = gem((190, 40, 40), (110, 16, 16), (240, 90, 90), (255, 190, 180), (255, 240, 230))
    BLUE = gem((40, 70, 190), (20, 30, 110), (90, 130, 240), (190, 215, 255), (240, 250, 255))
    VIOLET = gem((110, 50, 190), (60, 24, 110), (170, 110, 240), (225, 195, 255), (250, 240, 255))
    PINK = gem((160, 50, 160), (90, 24, 90), (225, 110, 225), (250, 195, 250), (255, 240, 255))
    GREEN_CASE = dict(art.P_NANO, A=(70, 180, 70), G=(190, 255, 170))
    for iid, en, ru, pic, pal in (
            ('battery_re', 'RE-Battery', 'Аккумулятор', art.I_BATTERY, art.P_BATPACK),
            ('battery_adv', 'Advanced RE-Battery', 'Продвинутый аккумулятор', art.I_BATTERY, art.P_ADV_BATPACK),
            ('energy_crystal', 'Energy Crystal', 'Энергетический кристалл', art.I_CRYSTAL, RED),
            ('lapotron_crystal', 'Lapotron Crystal', 'Лазуротроновый кристалл', art.I_CRYSTAL, BLUE),
            ('energy_orb', 'Lapotronic Energy Orb', 'Лазуротроновая энергетическая сфера', art.I_ORB, BLUE),
            ('energy_orb_cluster', 'Lapotronic Energy Orb Cluster', 'Кластер лазуротроновых энергетических сфер', art.I_ORB, VIOLET),
            ('energy_module', 'Energy Module', 'Энергетический модуль', art.I_ORB, BLUE),
            ('energy_cluster', 'Energy Cluster', 'Энергетический кластер', art.I_ORB, VIOLET),
            ('ultimate_battery', 'Ultimate Battery', 'Предельный аккумулятор', art.I_ORB, PINK),
            ('charging_battery_re', 'Charging RE-Battery', 'Заряжающий аккумулятор', art.I_BATTERY, GREEN_CASE),
            ('charging_battery_adv', 'Advanced Charging Battery', 'Продвинутый заряжающий аккумулятор', art.I_BATTERY, dict(GREEN_CASE, A=(214, 180, 60), G=(255, 236, 140))),
            ('charging_energy_crystal', 'Charging Energy Crystal', 'Заряжающий энергетический кристалл', art.I_CRYSTAL, dict(RED, K=(20, 60, 24))),
            ('charging_lapotron_crystal', 'Charging Lapotron Crystal', 'Заряжающий лазуротроновый кристалл', art.I_CRYSTAL, dict(BLUE, K=(20, 60, 24)))):
        item(iid, en, ru, art.icon(pic, pal))
    for tag, ids in (('basic', ('battery_re', 'battery_lv', 'battery_cadmium_lv', 'battery_lithium_lv', 'battery_acid_lv', 'battery_mercury_lv')),
                     ('advanced', ('battery_adv', 'battery_sodium_mv', 'battery_cadmium_mv', 'battery_lithium_mv', 'battery_acid_mv', 'battery_mercury_mv')),
                     ('elite', ('energy_crystal', 'battery_sodium_hv', 'battery_cadmium_hv', 'battery_lithium_hv', 'battery_acid_hv', 'battery_mercury_hv')),   # GT_Loader_OreDictionary:128
                     ('master', ('lapotron_crystal',)),   # :129
                     ('ultimate', ('energy_orb',))):
        for i in ids:
            add_item_tag(f'iis:batteries/{tag}', I(i))
    BAT = lambda t: f'#iis:batteries/{t}'
    # GT_MachineRecipeLoader:219
    mrec('fluid_canner', 'battery_re', [ing(I('battery_hull_lv'))], [out(I('battery_re'))], 4, 1, fin=fl('molten_redstone', 288))
    # zs:172
    shaped('gear/battery_adv', I('battery_adv'), 1, ['CBC', 'BSB', 'BLB'], {'C': CAB('annealed_copper'), 'B': I('item_casing_bronze'), 'S': DU('Sulfur'), 'L': DU('Lead')})
    item('energium_dust', 'Energium Dust', 'Энергопыль', mask_icon('ball', (160, 40, 40), (230, 70, 70), 5))
    # GT_MachineRecipeLoader:106, 108, 394, 398
    mrec('mixer', 'energium_dust', [ing(DU('Redstone'), 5), ing(DU('Ruby'), 4)], [out(I('energium_dust'), 9)], 900, 8)
    mrec('autoclave', 'energy_crystal', [ing(I('energium_dust'), 9)], [out(I('energy_crystal'))], 500, 256, fin={'ingredient': 'minecraft:water', 'amount': 1000})
    mrec('autoclave', 'energy_crystal_distilled', [ing(I('energium_dust'), 9)], [out(I('energy_crystal'))], 250, 256, fin=fl('distilled_water', 1000))
    # memory: IC2's own lapotron crystal recipe, IIS 1 does not change it
    shaped('gear/lapotron_crystal', I('lapotron_crystal'), 1, ['LCL', 'LEL', 'LCL'], {'L': DU('Lapis'), 'C': C + 'advanced', 'E': I('energy_crystal')})
    # ProcessingCrafting:33 (blue lens), GT_MachineRecipeLoader:607-608
    item('crystal_chip_master', 'Energy Chip Parts', 'Заготовка лазуротроновой схемы', mask_icon('chip', (40, 60, 170), (120, 170, 250), 7))
    mrec('laser_engraver', 'crystal_chip_master', [ing(I('lapotron_crystal'))], [out(I('crystal_chip_master'), 3)], 256, 480, keep='#iis:crafting_lens/blue', cleanroom=True)
    SOL = lambda mb: fl('molten_solderingalloy', mb)
    FG = I('circuit_board_fiberglass')
    FINE = tag_of('wire_fine', 'Platinum')
    mrec('circuit_assembler', 'energy_orb', [ing(FG), ing(I('chip_pic'), 4), ing(I('crystal_chip_master'), 18), ing(I('chip_nano_cpu')), ing(FINE, 16)], [out(I('energy_orb'))], 512, 1024, fin=SOL(144))
    mrec('circuit_assembler', 'energy_orb_cluster', [ing(FG), ing(I('chip_hpic'), 4), ing(I('energy_orb'), 8), ing(I('chip_quantum_cpu')), ing(FINE, 16), ing(P('Europium'), 4)],
         [out(I('energy_orb_cluster'))], 1024, 4096, fin=SOL(144))
    # memory: IC2's charging batteries, IIS 1 keeps them
    for iid, b, c in (('charging_battery_re', 'battery_re', 'basic'), ('charging_battery_adv', 'battery_adv', 'good'), ('charging_energy_crystal', 'energy_crystal', 'advanced'), ('charging_lapotron_crystal', 'lapotron_crystal', 'data')):
        shaped(f'gear/{iid}', I(iid), 1, ['BCB', 'B B', 'BCB'], {'B': I(b), 'C': C + c})

    # --- item casings: zs names them, hammer + plate as for the steel one (lv.py) ---------------------------------------------------
    for mid, mat, en, ru, col in (('copper', 'Copper', 'Copper', 'Медная', (225, 130, 70)), ('tin', 'Tin', 'Tin', 'Оловянная', (210, 210, 220)), ('bronze', 'Bronze', 'Bronze', 'Бронзовая', (225, 160, 70)),
                                  ('gold', 'Gold', 'Gold', 'Золотая', (240, 215, 80)), ('iron', 'Iron', 'Iron', 'Железная', (200, 200, 205)), ('lead', 'Lead', 'Lead', 'Свинцовая', (110, 90, 130))):
        item(f'item_casing_{mid}', f'{en} Item Casing', f'{ru} оболочка', mask_icon('casing', tuple(int(v * 0.7) for v in col), col, 20))
        shaped(f'gear/item_casing_{mid}', I(f'item_casing_{mid}'), 2, ['hP'], {'P': P(mat)})

    # --- coal chain: GT_MachineRecipeLoader:1483, ProcessingBlock:72-76, :1434, :942, :1092 ----------------------------------------
    item('coal_ball', 'Coal Ball', 'Угольный шарик', mask_icon('ball', (45, 45, 48), (70, 70, 74), 30))
    item('compressed_coal_ball', 'Compressed Coal Ball', 'Сжатый угольный шарик', mask_icon('ball', (30, 30, 34), (55, 55, 60), 31))
    item('coal_chunk', 'Coal Chunk', 'Куб сжатого угля', mask_icon('casing', (25, 25, 28), (50, 50, 55), 32))
    item('industrial_diamond', 'Industrial Diamond', 'Промышленный алмаз', mask_icon('crystal', (140, 170, 170), (210, 240, 240), 33))
    mrec('assembler', 'coal_ball', [ing(DU('Coal'), 8), ing('minecraft:flint')], [out(I('coal_ball'))], 400, 4)
    mrec('compressor', 'compressed_coal_ball', [ing(I('coal_ball'))], [out(I('compressed_coal_ball'))], 300, 2)
    for b in ('bricks', 'obsidian', 'iron_block'):
        mrec('assembler', f'coal_chunk_{b}', [ing(I('compressed_coal_ball'), 8), ing(f'minecraft:{b}')], [out(I('coal_chunk'))], 400, 4)
    mrec('compressor', 'industrial_diamond', [ing(I('coal_chunk'))], [out(I('industrial_diamond'))], 300, 2)
    mrec('implosion', 'industrial_diamond', [ing(I('coal_chunk')), ing('minecraft:tnt', 4)], [out(I('industrial_diamond')), out(ref('tiny_dust', 'DarkAsh'), 4)], 20, 30)
    shaped('gear/iridium_alloy_ingot_industrial', I('iridium_alloy_ingot'), 1, ['IAI', 'ADA', 'IAI'], {'I': P('Iridium'), 'A': I('advanced_alloy_plate'), 'D': I('industrial_diamond')})

    # --- armour ---------------------------------------------------------------------------------------------------------------------
    def suit(asset, main, legs=None):
        main.save(tex(f'entity/equipment/humanoid/{asset}.png'))
        layers = {'humanoid': [{'texture': I(asset)}]}
        if legs is not None:
            legs.save(tex(f'entity/equipment/humanoid_leggings/{asset}.png'))
            layers['humanoid_leggings'] = [{'texture': I(asset)}]
        write(os.path.join(A, 'equipment', f'{asset}.json'), {'layers': layers})

    suit('nano', *art.nano())
    suit('quantum', *art.quantum())
    suit('goggles', art.goggles())
    suit('jetpack', art.jet_harness())
    for asset, pal in (('batpack', art.P_BATPACK), ('adv_batpack', art.P_ADV_BATPACK), ('energy_pack', art.P_ENERGY_PACK), ('lappack', art.P_LAPPACK)):
        suit(asset, art.harness(pal))
        art.pack_model(pal).save(tex(f'entity/gear/{asset}.png'))
    art.jet_model().save(tex('entity/gear/jetpack.png'))
    pieces = (('helmet', 'Helmet', 'шлем', art.I_HELMET), ('chestplate', 'Bodyarmor', 'кираса', art.I_CHEST), ('leggings', 'Leggings', 'поножи', art.I_LEGS), ('boots', 'Boots', 'ботинки', art.I_BOOTS))
    for p, en, ru, pic in pieces:
        item(f'nano_{p}', f'NanoSuit {en}', f'Нано-{ru}', art.icon(pic, art.P_NANO))
        item(f'quantum_{p}', f'QuantumSuit {en}', f'Квантовый {ru}' if p == 'helmet' else ('Квантовая кираса' if p == 'chestplate' else f'Квантовые {ru}'), art.icon(pic, art.P_QUANTUM))
    item('nightvision_goggles', 'Nightvision Goggles', 'Прибор ночного видения', art.icon(art.I_GOGGLES, art.P_NANO))
    item('jetpack', 'Electric Jetpack', 'Электрический реактивный ранец', art.icon(art.I_JET, art.P_STEEL))
    item('batpack', 'BatPack', 'Аккумуляторный ранец', art.icon(art.I_PACK, art.P_BATPACK))
    item('adv_batpack', 'Advanced BatPack', 'Продвинутый аккумуляторный ранец', art.icon(art.I_PACK, art.P_ADV_BATPACK))
    item('energy_pack', 'EnergyPack', 'Энергетический ранец', art.icon(art.I_PACK, art.P_ENERGY_PACK))
    item('lappack', 'LapPack', 'Лазуротроновый ранец', art.icon(art.I_PACK, art.P_LAPPACK))
    item('mining_laser', 'Mining Laser', 'Лазерный бур', art.icon(art.I_LASER, art.P_STEEL))
    item('nano_saber', 'Nano Saber', 'Нано-сабля', art.icon(art.I_SABER, art.P_NANO))

    # GT_IndustialCraftRecipesLoader:270
    shaped('gear/nightvision_goggles', I('nightvision_goggles'), 1, ['AXA', 'RBR', 'SdS'],
           {'A': I('reactor_heat_switch_diamond'), 'X': tag_of('screw', 'StainlessSteel'), 'B': tag_of('bolt', 'StainlessSteel'), 'R': tag_of('ring', 'StainlessSteel'), 'S': tag_of('lens', 'Olivine')})
    # GraviSuite.zs:159-162
    CP, EC = I('carbon_plate'), I('energy_crystal')
    mrec('assembler', 'nano_helmet', [ing(CP, 4), ing(EC), ing(I('nightvision_goggles'))], [out(I('nano_helmet'))], 400, 120, circuit=0)
    mrec('assembler', 'nano_chestplate', [ing(CP, 7), ing(EC)], [out(I('nano_chestplate'))], 400, 120, circuit=1)
    mrec('assembler', 'nano_leggings', [ing(CP, 6), ing(EC)], [out(I('nano_leggings'))], 400, 120, circuit=2)
    mrec('assembler', 'nano_boots', [ing(CP, 4), ing(EC)], [out(I('nano_boots'))], 400, 120, circuit=3)
    # GT_MachineRecipeLoader:2362-2415, research 144000 ticks from the nano piece
    IR = lambda n: ing(I('iridium_alloy_plate'), n)
    SC = ing(tag_of('screw', 'Tungsten'), 4)
    FLUIDS = [SOL(2304), fl('molten_titanium', 1440)]
    M2 = ing(C + 'master', 2)
    ORB = ing(I('energy_orb'))

    def quantum(p, extra):
        mrec('assembly_line', f'quantum_{p}', [ing(I(f'nano_{p}')), M2] + extra + [SC], [out(I(f'quantum_{p}'))], 1500, 4096, fins=FLUIDS, keep=I(f'nano_{p}'), temp=144000)

    quantum('helmet', [IR(4), ORB, ing(I('sensor_iv')), ing(I('field_generator_ev'))])
    quantum('chestplate', [IR(6), ORB, ing(I('field_generator_hv'), 2), ing(I('electric_motor_iv'), 2)])
    quantum('leggings', [IR(6), ORB, ing(I('field_generator_hv'), 2), ing(I('electric_motor_iv'), 4)])
    quantum('boots', [IR(4), ORB, ing(I('field_generator_hv')), ing(I('electric_piston_iv'), 2)])

    # packs: zs:402, zs:926, zs:935; energy pack and lappack from memory
    shaped('gear/batpack', I('batpack'), 1, ['BCB', 'BPB', 'BWB'], {'B': BAT('basic'), 'C': C + 'basic', 'P': P('Aluminium'), 'W': WIRE('tin')})
    shaped('gear/jetpack', I('jetpack'), 1, ['ICI', 'IBI', 'G G'], {'I': I('item_casing_iron'), 'C': C + 'advanced', 'B': I('battery_lv'), 'G': DU('Glowstone')})
    shaped('gear/adv_batpack', I('adv_batpack'), 1, ['BCB', 'BIB'], {'B': I('battery_sodium_mv'), 'C': C + 'basic', 'I': I('item_casing_bronze')})
    shaped('gear/energy_pack', I('energy_pack'), 1, ['CAC', 'EIE', 'E E'], {'C': C + 'advanced', 'A': I('advanced_alloy_plate'), 'E': I('energy_crystal'), 'I': I('item_casing_iron')})
    shaped('gear/lappack', I('lappack'), 1, ['LCL', 'LEL', 'L L'], {'L': I('lapotron_crystal'), 'C': C + 'data', 'E': I('energy_pack')})
    # zs:424
    for gem in ('Diamond', 'Ruby', 'Jasper', 'GarnetRed'):
        shaped(f'gear/mining_laser_{gem.lower()}', I('mining_laser'), 1, ['TGT', 'XEC', 'SLd'],
               {'T': P('Titanium'), 'G': P('Gallium'), 'X': tag_of('gem_exquisite', gem), 'E': I('emitter_ev'), 'C': C + 'elite', 'S': tag_of('screw', 'Titanium'), 'L': I('charging_lapotron_crystal')})
    # memory
    shaped('gear/nano_saber', I('nano_saber'), 1, ['GA ', 'GA ', 'CEC'], {'G': DU('Glowstone'), 'A': I('advanced_alloy_plate'), 'C': CP, 'E': EC})

    # --- storage blocks: zs:267-318 --------------------------------------------------------------------------------------------------
    def storage(bid, en, ru, voltage, side_col, port_col, lower, wire, plate_or_bat, hull, bat, circuit):
        rnd = random.Random(bid)
        side = Image.new('RGBA', (16, 16), side_col + (255,))
        for y in range(16):
            for x in range(16):
                v = rnd.randint(-5, 5)
                c = tuple(max(0, min(255, k + v - (18 if x in (0, 15) or y in (0, 15) else 0))) for k in side_col)
                side.putpixel((x, y), c + (255,))
        front = side.copy()
        for y in range(5, 11):
            for x in range(5, 11):
                front.putpixel((x, y), port_col + (255,))
        top = side.copy()
        for x in range(3, 13):
            top.putpixel((x, 7), port_col + (255,))
            top.putpixel((x, 8), port_col + (255,))
        side.save(tex(f'block/gear/{bid}_side.png'))
        front.save(tex(f'block/gear/{bid}_front.png'))
        top.save(tex(f'block/gear/{bid}_top.png'))
        s = f'{NS}:block/gear/{bid}_side'
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube', 'textures': {
            'particle': s, 'north': f'{NS}:block/gear/{bid}_front', 'south': s, 'east': s, 'west': s, 'up': f'{NS}:block/gear/{bid}_top', 'down': s}})
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for active in ('false', 'true'):
                v = {'model': f'{NS}:block/{bid}'}
                if y:
                    v['y'] = y
                variants[f'facing={facing},active={active}'] = v
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
              'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [{'type': 'minecraft:item', 'name': I(bid)}]}]})
        block_tags.setdefault('minecraft:mineable/pickaxe', []).append(I(bid))
        name(f'block.{NS}.{bid}', en, ru)
        name(f'block.{NS}.{bid}.tooltip.0', f'Outputs {voltage} EU/t from the front; top slots charge items, bottom slots discharge batteries', f'Отдаёт {voltage} EU/t через лицевую сторону; верхние слоты заряжают предметы, нижние разряжают батареи')
        key = {'W': wire, 'P': plate_or_bat, 'B': bat, 'C': circuit}
        if hull:
            shaped(f'gear/{bid}', I(bid), 1, ['WPW', 'BHB', 'CBC'], dict(key, H=hull))
        if lower:
            shaped(f'gear/{bid}_upgrade', I(bid), 1, ['WPW', 'BHB', 'CBC'], dict(key, H=I(lower)))

    storage('batbox', 'BatBox', 'Энергохранилище', 32, (120, 85, 50), (190, 70, 60), None, WIRE('tin'), P('Steel'), I('hull_lv'), BAT('basic'), C + 'basic')
    storage('cesu', 'CESU', 'МЭХ', 128, (190, 130, 70), (190, 70, 60), 'batbox', WIRE('copper'), P('Bronze'), I('hull_mv'), BAT('advanced'), C + 'good')
    storage('mfe', 'MFE', 'МФЭ', 512, (150, 150, 160), (90, 170, 220), 'cesu', CAB('silver'), BAT('elite'), I('hull_hv'), BAT('elite'), C + 'advanced')
    storage('mfsu', 'MFSU', 'МФЭХ', 2048, (70, 75, 95), (90, 140, 250), 'mfe', CAB('tungsten_steel'), I('lapotron_crystal'), I('hull_ev'), I('lapotron_crystal'), C + 'data')

    for k, en, ru in (('itemGroup.iis.gear', 'IIS 2.0: Gear', 'ИИС 2.0: снаряжение'), ('key.iis.night_vision', 'Night vision', 'Ночное зрение'),
                      ('gear.iis.night_vision.on', 'Night vision on', 'Ночное зрение включено'), ('gear.iis.night_vision.off', 'Night vision off', 'Ночное зрение выключено'),
                      ('item.iis.nano_saber.on', 'Saber on', 'Сабля включена'), ('item.iis.nano_saber.off', 'Saber off', 'Сабля выключена'),
                      ('item.iis.mining_laser.mode', 'Mode: %s', 'Режим: %s'), ('item.iis.mining_laser.mining', 'Mining', 'Добыча'), ('item.iis.mining_laser.low_focus', 'Low focus', 'Слабая фокусировка'),
                      ('item.iis.mining_laser.long_range', 'Long range', 'Дальний'), ('item.iis.mining_laser.horizontal', 'Horizontal', 'Горизонтальный'), ('item.iis.mining_laser.super_heat', 'Super heat', 'Перегрев'),
                      ('item.iis.mining_laser.scatter', 'Scatter', 'Рассеянный'), ('item.iis.mining_laser.three', '3x3', '3×3')):
        name(k, en, ru)
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
