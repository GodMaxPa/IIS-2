"""Fusion reactor Mark I (D116): superconducting coil, fusion coil, Iridium Neutron Reflector with its IC2 stand-ins (D5), Plutonium 241, HPIC wafer, the controller, fusion recipes.

Sources: GT_MetaTileEntity_FusionComputer, _FusionComputer1; GT_Loader_MetaTileEntities:98, 1221; GT_MachineRecipeLoader:515, 518, 1063-1089, 1093, 1104-1105, 1388, 1891, 2552-2565;
GT_Loader_Item_Block_And_Fluid:94; GT_IndustialCraftRecipesLoader:173, 178; GT_MetaGenerated_Item_01:370. Recipes above 160 million EU of ignition need Mark II and are not here yet.
"""
import os
import random
import shutil

from PIL import Image

NS = 'iis'
I = lambda x: f'{NS}:{x}'
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')
# input A, L, input B, L, output, L, ticks, EU/t, ignition (GT_MachineRecipeLoader:1064-1088, mSpecialValue up to 160003000)
RECIPES = [('deuterium', 125, 'tritium', 125, 'plasma_helium', 125, 16, 4096, 40000000),
           ('deuterium', 125, 'helium_3', 125, 'plasma_helium', 125, 16, 2048, 60000000),
           ('molten_carbon', 125, 'helium_3', 125, 'plasma_oxygen', 125, 32, 4096, 80000000),
           ('molten_beryllium', 16, 'molten_tungsten', 16, 'molten_platinum', 16, 32, 32768, 150000000),
           ('molten_neodymium', 16, 'hydrogen', 48, 'molten_europium', 16, 64, 24576, 150000000),
           ('molten_nickel', 16, 'molten_aluminium', 16, 'molten_niobium', 16, 28, 24578, 150000000),
           ('molten_tungsten', 16, 'helium', 16, 'molten_osmium', 16, 64, 24578, 150000000),
           ('molten_manganese', 16, 'hydrogen', 16, 'molten_iron', 16, 64, 8192, 120000000),
           ('molten_vanadium', 16, 'hydrogen', 125, 'molten_chrome', 16, 64, 24576, 140000000),
           ('molten_gallium', 16, 'radon', 125, 'molten_duranium', 16, 64, 16384, 140000000),
           # D121: Mark II and III (GT_MachineRecipeLoader:1063, 1067-1070, 1073-1075, 1080-1082, 1085-1086, 1088-1089): the store of the reactor decides which it can run
           ('molten_lithium', 16, 'molten_tungsten', 16, 'molten_iridium', 16, 32, 32768, 300000000),
           ('molten_aluminium', 16, 'molten_lithium', 16, 'plasma_sulfur', 125, 32, 10240, 240000000),
           ('molten_beryllium', 16, 'deuterium', 375, 'plasma_nitrogen', 175, 16, 16384, 180000000),
           ('molten_silicon', 16, 'molten_magnesium', 16, 'plasma_iron', 125, 32, 8192, 360000000),
           ('molten_potassium', 16, 'fluorine', 125, 'plasma_nickel', 125, 16, 32768, 480000000),
           ('molten_lutetium', 16, 'molten_chrome', 16, 'molten_americium', 16, 96, 49152, 200000000),
           ('molten_plutonium', 16, 'molten_thorium', 16, 'molten_naquadah', 16, 64, 32768, 300000000),
           ('molten_americium', 16, 'molten_naquadria', 16, 'molten_neutronium', 1, 1200, 98304, 600000000),
           ('mercury', 16, 'molten_magnesium', 16, 'molten_uranium', 16, 64, 49152, 240000000),
           ('molten_gold', 16, 'molten_aluminium', 16, 'molten_uranium', 16, 64, 49152, 240000000),
           ('molten_uranium', 16, 'helium', 16, 'molten_plutonium', 16, 128, 49152, 480000000),
           ('molten_titanium', 48, 'molten_duranium', 32, 'molten_tritanium', 16, 64, 32768, 200000000),
           ('molten_gold', 16, 'mercury', 16, 'radon', 125, 64, 32768, 200000000),
           ('molten_silver', 16, 'molten_lithium', 16, 'molten_indium', 16, 32, 24576, 380000000),
           ('molten_naquadahenriched', 15, 'radon', 125, 'molten_naquadria', 3, 64, 49152, 400000000)]


def spot(base, accent, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            c = accent if x in (2, 13) or y in (2, 13) else base
            v = rnd.randint(-8, 8)
            im.putpixel((x, y), (max(0, c[0] + v), max(0, c[1] + v), max(0, c[2] + v), 255))
    return im


def generate(g):
    write, A, D, lang, shaped, tag_of, block_tags, ref = g['write'], g['A'], g['D'], g['lang'], g['shaped'], g['tag_of'], g['block_tags'], g['ref']
    BI = os.path.join(GT, 'blocks', 'iconsets')
    rec = {}

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def first(path):
        return Image.open(path).convert('RGBA').crop((0, 0, 16, 16))

    def item(iid, en, ru, im):
        im.save(tex(f'item/{iid}.png'))
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)

    def loot(bid):
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
              'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [{'type': 'minecraft:item', 'name': I(bid)}]}]})
        block_tags.setdefault('minecraft:mineable/pickaxe', []).append(I(bid))

    def block(bid, en, ru, im):
        im.save(tex(f'block/multi/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/multi/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        loot(bid)
        name(f'block.{NS}.{bid}', en, ru)

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, fins=None, fout=None, keep=None, temp=None, circuit=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': dur, 'eut': eut}
        if fin:
            r['fluid_inputs'] = [fin]
        if fins:
            r['fluid_inputs'] = fins
        if fout:
            r['fluid_outputs'] = fout
        if keep:
            r['keep'] = [keep]
        if temp:
            r['temp'] = temp
        if circuit is not None:
            r['circuit'] = circuit
        rec[f'{rtype}/{rid}'] = r

    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1, chance=None: dict({'id': i, 'count': c}, **({'chance': chance} if chance else {}))
    fl = lambda f, n: {'ingredient': I(f), 'amount': n}

    # --- items and blocks ------------------------------------------------------------------------------------------------
    for iid, src, en, ru in (('wafer_hpic', '52', 'HPIC Wafer', 'Пластина высокомощных микросхем'), ('chip_hpic', '53', 'HPIC', 'Высокомощная микросхема')):
        item(iid, en, ru, Image.open(os.path.join(GT, 'items', 'gt.metaitem.03', src + '.png')).convert('RGBA'))
    item('iridium_alloy_ingot', 'Iridium Alloy Ingot', 'Слиток иридиевого сплава', Image.open(os.path.join(GT, 'items', 'gt.metaitem.01', '480.png')).convert('RGBA'))
    # IC2 is not ported (D5): our own reflectors and the iridium plate, with the GT recipes that make them
    item('iridium_alloy_plate', 'Iridium Reinforced Plate', 'Укреплённая иридиевая пластина', spot((200, 200, 215), (120, 120, 135), 1))
    item('reactor_reflector', 'Neutron Reflector', 'Отражатель нейтронов', spot((150, 120, 90), (90, 75, 60), 2))
    item('reactor_reflector_thick', 'Thick Neutron Reflector', 'Утолщённый отражатель нейтронов', spot((120, 95, 70), (70, 60, 50), 3))
    item('neutron_reflector', 'Iridium Neutron Reflector', 'Иридиевый отражатель нейтронов', spot((205, 205, 225), (110, 110, 130), 4))
    block('coil_superconductor', 'Superconducting Coil Block', 'Сверхпроводящая катушка', first(os.path.join(BI, 'MACHINE_COIL_SUPERCONDUCTOR.png')))
    block('fusion_coil', 'Fusion Coil Block', 'Катушка термоядерного реактора', first(os.path.join(BI, 'MACHINE_CASING_FUSION_COIL.png')))

    bid = 'fusion_computer_luv'
    side = first(os.path.join(BI, 'MACHINE_CASING_FUSION_GLASS.png'))
    on = first(os.path.join(BI, 'MACHINE_CASING_FUSION_GLASS_YELLOW.png'))
    Image.open(os.path.join(BI, 'OVERLAY_FUSION1.png')).convert('RGBA').crop((0, 0, 16, 16)).save(tex('block/multi/fusion_overlay.png'))
    ov = Image.open(tex('block/multi/fusion_overlay.png'))
    for suf, base in (('', side), ('_active', on)):
        Image.alpha_composite(base, ov).save(tex(f'block/multi/{bid}_front{suf}.png'))
        base.save(tex(f'block/multi/{bid}_side{suf}.png'))
        s = f'{NS}:block/multi/{bid}_side{suf}'
        write(os.path.join(A, 'models', 'block', bid + suf + '.json'), {'parent': 'minecraft:block/cube', 'textures': {
            'particle': s, 'north': f'{NS}:block/multi/{bid}_front{suf}', 'south': s, 'east': s, 'west': s, 'up': s, 'down': s}})
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
    name(f'block.{NS}.{bid}', 'Fusion Control Computer Mark I', 'Блок управления термоядерным реактором I')
    for k, (en, ru) in enumerate((('Ring of LuV machine casings around 32 superconducting coils; the back of the controller looks at the centre, 5 blocks away', 'Кольцо корпусов LuV вокруг 32 сверхпроводящих катушек; центр кольца за спиной контроллера, в 5 блоках'),
                                  ('2-16 input hatches, 1-16 output hatches, 1-16 energy hatches, all LuV or better, in place of casings', '2–16 входных люков, 1–16 выходных, 1–16 энергетических, все LuV и выше, вместо корпусов'),
                                  ('Each energy hatch: 2048 EU/t in, 10 million EU of store; ignition takes the start energy at once', 'Каждый энергетический люк: 2048 EU/t на вход и 10 млн EU запаса; зажигание сразу забирает стартовую энергию'))):
        name(f'block.{NS}.{bid}.tooltip.{k}', en, ru)
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'FusionComputer.png'), tex('gui/multi/fusion.png'))
    for k, en, ru in (('gui.iis.fusion.store', 'Store: %s / %s million EU', 'Запас: %s / %s млн EU'), ('jei.iis.fusion', 'Fusion Reactor', 'Термоядерный реактор'), ('jei.iis.fusion_start', 'Ignition: %s million EU', 'Зажигание: %s млн EU')
                      ):
        name(k, en, ru)

    # --- crafts ---------------------------------------------------------------------------------------------------------------
    P = lambda m: tag_of('plate', m)
    shaped('multi/coil_superconductor', I('coil_superconductor'), 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/superconductor'})   # GT_Loader_MetaTileEntities:98
    mrec('assembler', 'coil_superconductor', [ing('#iis:wires/2x/superconductor', 8)], [out(I('coil_superconductor'))], 50, 16, circuit=8)   # :1388
    shaped('multi/fusion_coil', I('fusion_coil'), 1, ['CTC', 'FMF', 'CTC'], {'M': I('coil_superconductor'), 'C': '#c:circuits/master', 'F': I('field_generator_mv'), 'T': I('neutron_reflector')})   # :1221
    # GT_IndustialCraftRecipesLoader:178, 173, GT_Loader_Item_Block_And_Fluid:94
    shaped('fusion/reactor_reflector', I('reactor_reflector'), 1, ['TGT', 'GSG', 'TGT'], {'T': P('Tin'), 'G': tag_of('dust', 'Graphite'), 'S': tag_of('double_plate', 'Steel')})
    shaped('fusion/reactor_reflector_thick', I('reactor_reflector_thick'), 1, [' N ', 'NBN', ' N '], {'B': tag_of('double_plate', 'Beryllium'), 'N': I('reactor_reflector')})
    shaped('fusion/neutron_reflector', I('neutron_reflector'), 1, ['RRR', 'RPR', 'RRR'], {'R': I('reactor_reflector_thick'), 'P': I('iridium_alloy_plate')})
    # GT_MetaGenerated_Item_01:370 "IAI/ADA/IAI" (the industrial diamond as everywhere: a diamond), :1093 implosion: 8 explosives -> plate + 4 tiny dark ash
    shaped('fusion/iridium_alloy_ingot', I('iridium_alloy_ingot'), 1, ['IAI', 'ADA', 'IAI'], {'I': P('Iridium'), 'A': I('advanced_alloy_plate'), 'D': 'minecraft:diamond'})
    mrec('implosion', 'iridium_alloy_plate', [ing(I('iridium_alloy_ingot')), ing('minecraft:tnt', 4)], [out(I('iridium_alloy_plate')), out(ref('tiny_dust', 'DarkAsh'), 4)], 20, 30)
    # GT_MachineRecipeLoader:518 HPIC wafer, :515 chips; :1891 plutonium centrifuge: 20 % tiny Pu-241, 30 % tiny uranium
    mrec('chemical_reactor', 'wafer_hpic', [ing(I('wafer_pic')), ing(tag_of('dust', 'IndiumGalliumPhosphide'), 2)], [out(I('wafer_hpic'))], 1200, 1920, fin=fl('molten_redalloy', 288))
    for suffix, dur, fin in (('', 1200, {'ingredient': 'minecraft:water', 'amount': 90}), ('_distilled', 1200, fl('distilled_water', 67)), ('_lubricant', 600, fl('lubricant', 22))):
        mrec('cutter', 'chip_hpic' + suffix, [ing(I('wafer_hpic'))], [out(I('chip_hpic'), 2)], dur, 48, fin=fin)
    mrec('centrifuge', 'plutonium', [ing(tag_of('dust', 'Plutonium'))], [out(ref('tiny_dust', 'Plutonium241'), 1, 2000), out(ref('tiny_dust', 'Uranium'), 1, 3000)], 1600, 320)
    # :1104-1105 the Moon's helium-3: rock grinds to dirt, dirt centrifuges
    mrec('macerator', 'moon_rock', [ing(I('moon_rock'))], [out(I('moon_dirt'))], 400, 2)
    mrec('macerator', 'mars_rock', [ing(I('mars_rock'))], [out(I('mars_dirt'))], 400, 2)   # D131: the same as the Moon's, GT has nothing for it
    mrec('centrifuge', 'moon_dirt', [ing(I('moon_dirt'))], [out('minecraft:sand', 1, 5000), out(ref('dust', 'Aluminium'), 1, 400), out(ref('dust', 'Calcite'), 1, 400), out(ref('dust', 'Iron'), 1, 100),
                                                             out(ref('dust', 'Magnesium'), 1, 100), out(ref('dust', 'Rutile'), 1, 100)], 400, 8, fout=[{'id': I('helium_3'), 'amount': 33}])
    # GT_ModHandler compression of a gem into its block (300 t, 2 EU/t): vanilla nether stars have no generated recipe; a plate is then cut from the block (quest 591)
    mrec('compressor', 'block_nether_star', [ing('minecraft:nether_star', 9)], [out(I('block_nether_star'))], 300, 2)
    # :2552-2565 the controller on the assembly line, research: superconducting wire
    mrec('assembly_line', bid, [ing(I('fusion_coil')), ing('#c:circuits/ultimate'), ing('#c:circuits/ultimate'), ing('#c:circuits/ultimate'), ing('#c:circuits/ultimate'), ing(P('Plutonium241')), ing(P('NetherStar')),
                                ing(I('field_generator_iv'), 2), ing(I('wafer_hpic'), 32), ing('#iis:wires/1x/superconductor', 32)], [out(I(bid))], 1000, 30000,
         fins=[fl('molten_solderingalloy', 2880)], keep='#iis:wires/1x/superconductor', temp=144000)
    # --- D121: Mark II and III: GT_MetaTileEntity_FusionComputer2, _FusionComputer3 --------------------------------------------------------
    for cid, tx, en, ru in (('casing_fusion', 'MACHINE_CASING_FUSION', 'Fusion Machine Casing', 'Корпус термоядерного реактора'), ('casing_fusion_2', 'MACHINE_CASING_FUSION_2', 'Fusion Machine Casing MK II', 'Корпус термоядерного реактора II')):
        block(cid, en, ru, first(os.path.join(BI, tx + '.png')))
    # GT_Loader_MetaTileEntities:1219-1220, GT_MachineRecipeLoader:1395
    shaped('multi/casing_fusion', I('casing_fusion'), 1, ['PhP', 'PFP', 'PwP'], {'P': P('TungstenSteel'), 'F': I('casing_luv')})
    mrec('assembler', 'casing_fusion', [ing(P('TungstenSteel'), 6), ing(I('casing_luv'))], [out(I('casing_fusion'))], 50, 16)
    shaped('multi/casing_fusion_2', I('casing_fusion_2'), 1, ['PhP', 'PFP', 'PwP'], {'P': P('Americium'), 'F': I('casing_fusion')})
    for fid, tx, ovl, en, ru, volt, hatch_in, store, casing_n, ru_casing in (
            ('fusion_computer_zpm', 'MACHINE_CASING_FUSION', 'OVERLAY_FUSION2', 'Fusion Control Computer Mark II', 'Блок управления термоядерным реактором II', 'ZPM', 4096, 20, 'fusion machine casings', 'корпусов термоядерного реактора'),
            ('fusion_computer_uv', 'MACHINE_CASING_FUSION_2', 'OVERLAY_FUSION3', 'Fusion Control Computer Mark III', 'Блок управления термоядерным реактором III', 'UV', 8192, 40, 'fusion machine casings MK II', 'корпусов термоядерного реактора II')):
        base = first(os.path.join(BI, tx + '.png'))
        ovim = first(os.path.join(BI, ovl + '.png'))
        Image.alpha_composite(base, ovim).save(tex(f'block/multi/{fid}_front.png'))
        base.save(tex(f'block/multi/{fid}_side.png'))
        s_ = f'{NS}:block/multi/{fid}_side'
        for suf in ('', '_active'):
            write(os.path.join(A, 'models', 'block', fid + suf + '.json'), {'parent': 'minecraft:block/cube', 'textures': {
                'particle': s_, 'north': f'{NS}:block/multi/{fid}_front', 'south': s_, 'east': s_, 'west': s_, 'up': s_, 'down': s_}})
        vs = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for active in (False, True):
                v = {'model': f'{NS}:block/{fid}' + ('_active' if active else '')}
                if y:
                    v['y'] = y
                vs[f'facing={facing},active={str(active).lower()}'] = v
        write(os.path.join(A, 'blockstates', fid + '.json'), {'variants': vs})
        write(os.path.join(A, 'items', fid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{fid}'}})
        loot(fid)
        name(f'block.{NS}.{fid}', en, ru)
        for k, (ent, rut) in enumerate(((f'Ring of {casing_n} around 32 fusion coils; the back of the controller looks at the centre, 5 blocks away', f'Кольцо {ru_casing} вокруг 32 катушек термоядерного реактора; центр кольца за спиной контроллера, в 5 блоках'),
                                        (f'2-16 input hatches, 1-16 output hatches, 1-16 energy hatches, all {volt} or better, in place of casings', f'2–16 входных люков, 1–16 выходных, 1–16 энергетических, все {volt} и выше, вместо корпусов'),
                                        (f'Each energy hatch: {hatch_in} EU/t in, {store} million EU of store; ignition takes the start energy at once', f'Каждый энергетический люк: {hatch_in} EU/t на вход и {store} млн EU запаса; зажигание сразу забирает стартовую энергию'))):
            name(f'block.{NS}.{fid}.tooltip.{k}', ent, rut)
    # GT_MachineRecipeLoader:2566-2593: the Superconductor circuit is one per slot
    SC = [ing('#c:circuits/superconductor')] * 4
    mrec('assembly_line', 'fusion_computer_zpm', [ing(I('fusion_coil'))] + SC + [ing(P('Europium'), 4), ing(I('field_generator_luv'), 2), ing(I('wafer_hpic'), 48), ing('#iis:wires/2x/superconductor', 32)], [out(I('fusion_computer_zpm'))], 1000, 60000,
         fins=[fl('molten_solderingalloy', 2880)], keep=I('block_europium'), temp=288000)
    mrec('assembly_line', 'fusion_computer_uv', [ing(I('fusion_coil'))] + SC + [ing(P('Americium'), 4), ing(I('field_generator_zpm'), 2), ing(I('wafer_hpic'), 64), ing('#iis:wires/4x/superconductor', 32)], [out(I('fusion_computer_uv'))], 1000, 90000,
         fins=[fl('molten_solderingalloy', 2880)], keep=I('block_americium'), temp=432000)
    for a, na, b, nb, o, no, dur, eut, start in RECIPES:
        mrec('fusion', o.replace('molten_', '').replace('plasma_', 'plasma_') + '_from_' + a.replace('molten_', ''), [], [], dur, eut, fins=[fl(a, na), fl(b, nb)], fout=[{'id': I(o), 'amount': no}], temp=start)
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
