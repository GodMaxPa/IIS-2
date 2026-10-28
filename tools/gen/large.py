"""Large multiblock generators: large boilers (GT_MetaTileEntity_LargeBoiler_*, D88) and the large steam turbine with
dynamo hatches and rotors (GT_MetaTileEntity_LargeTurbine_Steam, D89), called from gen_content.py.

Blocks: boiler casings (GT_Block_Casings1:10, Casings2:0/12/13, Casings3:13/14) and controllers; recipes:
GT_Loader_MetaTileEntities:61-62, 75-76, 87-88, 1157-1158 and GT_MachineRecipeLoader:1389. Java lists the same ids
(ru.iis.multi.MultiContent). Textures come from the GT5 checkout (IIS_GT5_TEXTURES), like ebf.py.
"""
import os
import shutil

from PIL import Image

GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')

# id, GT texture, GT lang key, EN
CASINGS = [
    ('bronze_plated_bricks', 'MACHINE_BRONZEPLATEDBRICKS', 'gt.blockcasings.10.name', 'Bronze Plated Bricks'),
    ('bronze_pipe_casing', 'MACHINE_CASING_PIPE_BRONZE', 'gt.blockcasings2.12.name', 'Bronze Pipe Casing'),
    ('bronze_firebox_casing', 'MACHINE_CASING_FIREBOX_BRONZE', 'gt.blockcasings3.13.name', 'Bronze Firebox Casing'),
    ('solid_steel_casing', 'MACHINE_CASING_SOLID_STEEL', 'gt.blockcasings2.0.name', 'Solid Steel Machine Casing'),
    ('steel_pipe_casing', 'MACHINE_CASING_PIPE_STEEL', 'gt.blockcasings2.13.name', 'Steel Pipe Casing'),
    ('steel_firebox_casing', 'MACHINE_CASING_FIREBOX_STEEL', 'gt.blockcasings3.14.name', 'Steel Firebox Casing'),
    # D97: GT_Block_Casings4:2, Casings2:14, Casings4:3
    ('stable_titanium_casing', 'MACHINE_CASING_STABLE_TITANIUM', 'gt.blockcasings4.2.name', 'Stable Titanium Machine Casing'),
    ('titanium_pipe_casing', 'MACHINE_CASING_PIPE_TITANIUM', 'gt.blockcasings2.14.name', 'Titanium Pipe Casing'),
    ('titanium_firebox_casing', 'MACHINE_CASING_FIREBOX_TITANIUM', 'gt.blockcasings4.3.name', 'Titanium Firebox Casing'),
    # D98: GT_Block_Casings4:0, Casings2:15, Casings3:15
    ('robust_tungstensteel_casing', 'MACHINE_CASING_ROBUST_TUNGSTENSTEEL', 'gt.blockcasings4.0.name', 'Robust Tungstensteel Machine Casing'),
    ('tungstensteel_pipe_casing', 'MACHINE_CASING_PIPE_TUNGSTENSTEEL', 'gt.blockcasings2.15.name', 'Tungstensteel Pipe Casing'),
    ('tungstensteel_firebox_casing', 'MACHINE_CASING_FIREBOX_TUNGSTENSTEEL', 'gt.blockcasings3.15.name', 'Tungstensteel Firebox Casing'),
    # D102: GT_Block_Casings2:3, для паровоза, электровоза и тоннельного бура Railcraft (скрипт RailCraft.zs ИИС 1)
    ('steel_gear_box_casing', 'MACHINE_CASING_GEARBOX_STEEL', 'gt.blockcasings2.3.name', 'Steel Gear Box Casing'),
]
# tier, casing, pipe, firebox, steam L/t, steam per coal (L), heat-up seconds (32 portions of 40 ticks for bronze, 42 of 30 for
# steel; GT's description says 500 / increase = 31.25 and 41.67 s, forgetting the boost), EN, material
BOILERS = [
    ('bronze', 'bronze_plated_bricks', 'bronze_pipe_casing', 'bronze_firebox_casing', 800, 32000, 64, 'Large Bronze Boiler', 'Bronze'),
    ('steel', 'solid_steel_casing', 'steel_pipe_casing', 'steel_firebox_casing', 1200, 36000, 63, 'Large Steel Boiler', 'Steel'),
    # D97: 800 EU/t, boost 130 %: 63 portions of 26 ticks = 82 s (GT says 500 / 8 = 62.5 s)
    ('titanium', 'stable_titanium_casing', 'titanium_pipe_casing', 'titanium_firebox_casing', 1600, 41600, 82, 'Large Titanium Boiler', 'Titanium'),
    # D98: 1000 EU/t, boost 120 %: 125 portions of 24 ticks = 150 s (GT says 500 / 4 = 125 s)
    ('tungstensteel', 'robust_tungstensteel_casing', 'tungstensteel_pipe_casing', 'tungstensteel_firebox_casing', 2000, 48000, 150,
     'Large Tungstensteel Boiler', 'Tungstensteel'),
]


def generate(g):
    import json
    write, A, D, NS, lang, REF = g['write'], g['A'], g['D'], g['NS'], g['lang'], g['REF']
    tag_of, shaped, block_tags = g['tag_of'], g['shaped'], g['block_tags']
    ref, mat_id, mat_by_name = g['ref'], g['mat_id'], g['mat_by_name']
    rec = {}

    def mrec(rtype, rid, inputs, outputs, duration, eut, fin=None, keep=None, temp=0, fout=None, circuit=0):
        if any(i['ingredient'] is None for i in inputs) or any(o['id'] is None for o in outputs):
            return
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': max(1, int(duration)), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if fout:
            r['fluid_outputs'] = [fout]
        if keep:
            r['keep'] = [keep]
        if temp:
            r['temp'] = temp
        if circuit:
            r['circuit'] = circuit
        rec.setdefault(f'{rtype}/{rid}', r)

    def cutter(rid, inp, outs, dur, eut):
        """GT_RecipeAdder#addCutterRecipe:438-440, like lv.py: water, distilled water (both duration x2), lubricant."""
        mrec('cutter', rid, inp, outs, dur * 2, eut, {'ingredient': 'minecraft:water', 'amount': max(4, min(1000, dur * eut // 320))})
        mrec('cutter', rid + '_distilled', inp, outs, dur * 2, eut, {'ingredient': f'{NS}:distilled_water', 'amount': max(3, min(750, dur * eut // 426))})
        mrec('cutter', rid + '_lubricant', inp, outs, dur, eut, {'ingredient': f'{NS}:lubricant', 'amount': max(1, min(250, dur * eut // 1280))})

    def ing(i, c=1):
        return {'ingredient': i, 'count': c}

    def rout(i, c=1):
        return {'id': i, 'count': c}
    ru_lang = json.load(open(os.path.join(REF, 'gt5_lang_ru_machines.json'), encoding='utf-8'))
    BI = os.path.join(GT, 'blocks', 'iconsets')
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

    def simple(bid):
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
        block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')

    def tooltip(bid, lines):
        for k, (en, ruu) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', en, ruu)

    # --- casings -------------------------------------------------------------------------------------------------------
    for bid, src, key, en in CASINGS:
        first(os.path.join(BI, src + '.png')).save(tex_out(f'block/multi/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/multi/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
    # GT_Loader_MetaTileEntities:61 "PhP/PBP/PwP": bronze plates + brick block -> 2; :62 steel plates + steel frame -> 2
    shaped('multi/bronze_plated_bricks', f'{NS}:bronze_plated_bricks', 2, ['PhP', 'PBP', 'PwP'], {'P': tag_of('plate', 'Bronze'), 'B': 'minecraft:bricks'})
    # :81 steel gear box casing "PhP/GFG/PwP": steel plates, steel gears, steel frame -> 2 (D102)
    shaped('multi/steel_gear_box_casing', f'{NS}:steel_gear_box_casing', 2, ['PhP', 'GFG', 'PwP'],
           {'P': tag_of('plate', 'Steel'), 'G': tag_of('gear', 'Steel'), 'F': '#c:frames/steel'})
    shaped('multi/solid_steel_casing', f'{NS}:solid_steel_casing', 2, ['PhP', 'PFP', 'PwP'], {'P': tag_of('plate', 'Steel'), 'F': '#c:frames/steel'})
    # :63 stable titanium casing "PhP/PFP/PwP" (titanium plates + frame); assembler :1393
    shaped('multi/stable_titanium_casing', f'{NS}:stable_titanium_casing', 2, ['PhP', 'PFP', 'PwP'],
           {'P': tag_of('plate', 'Titanium'), 'F': '#c:frames/titanium'})
    # :68 robust tungstensteel casing "PhP/PFP/PwP" (D98); assembler :1391
    shaped('multi/robust_tungstensteel_casing', f'{NS}:robust_tungstensteel_casing', 2, ['PhP', 'PFP', 'PwP'],
           {'P': tag_of('plate', 'TungstenSteel'), 'F': '#c:frames/tungsten_steel'})
    for m in ('Bronze', 'Steel', 'Titanium', 'TungstenSteel'):   # :75-78, :87-90
        bm, mid = m.lower(), mat_id(m)
        # :75-76 pipe casings "PIP/IFI/PIP" (I — medium pipe), :87-88 fireboxes "PSP/SFS/PSP" (S — rod)
        shaped(f'multi/{bm}_pipe_casing', f'{NS}:{bm}_pipe_casing', 2, ['PIP', 'IFI', 'PIP'],
               {'P': tag_of('plate', m), 'F': f'#c:frames/{mid}', 'I': f'{NS}:pipe_medium_{mid}'})
        shaped(f'multi/{bm}_firebox_casing', f'{NS}:{bm}_firebox_casing', 2, ['PSP', 'SFS', 'PSP'],
               {'P': tag_of('plate', m), 'F': f'#c:frames/{mid}', 'S': tag_of('rod', m)})
    tooltip('bronze_firebox_casing', [('Bottom layer of the Large Bronze Boiler', 'Нижний слой большого бронзового котла')])
    tooltip('steel_firebox_casing', [('Bottom layer of the Large Steel Boiler', 'Нижний слой большого стального котла')])
    tooltip('bronze_pipe_casing', [('Middle column of the Large Bronze Boiler', 'Середина большого бронзового котла')])
    tooltip('steel_pipe_casing', [('Middle column of the Large Steel Boiler', 'Середина большого стального котла')])
    tooltip('titanium_firebox_casing', [('Bottom layer of the Large Titanium Boiler', 'Нижний слой большого титанового котла')])
    tooltip('titanium_pipe_casing', [('Middle column of the Large Titanium Boiler', 'Середина большого титанового котла')])
    tooltip('tungstensteel_firebox_casing', [('Bottom layer of the Large Tungstensteel Boiler', 'Нижний слой большого вольфрамостального котла')])
    tooltip('tungstensteel_pipe_casing', [('Middle column of the Large Tungstensteel Boiler', 'Середина большого вольфрамостального котла')])

    # --- controllers -----------------------------------------------------------------------------------------------------
    for tier, casing, pipe, firebox, steam, per_coal, heat_s, en, mat in BOILERS:
        bid = f'large_boiler_{tier}'
        base = first(os.path.join(BI, [c for c in CASINGS if c[0] == casing][0][1] + '.png'))
        for suf, ov in (('', 'OVERLAY_FRONT_LARGE_BOILER'), ('_active', 'OVERLAY_FRONT_LARGE_BOILER_ACTIVE')):
            Image.alpha_composite(base, first(os.path.join(BI, ov + '.png'))).save(tex_out(f'block/multi/{bid}_front{suf}.png'))
            side = f'{NS}:block/multi/{casing}'
            write(os.path.join(A, 'models', 'block', bid + suf + '.json'), {
                'parent': 'minecraft:block/cube',
                'textures': {'particle': side, 'north': f'{NS}:block/multi/{bid}_front{suf}', 'south': side, 'east': side, 'west': side,
                             'up': side, 'down': side}})
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for active in (False, True):
                v = {'model': f'{NS}:block/{bid}' + ('_active' if active else '')}
                if y:
                    v['y'] = y
                variants[f'facing={facing},active={str(active).lower()}'] = v
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(f'gt.blockmachines.multimachine.boiler.{tier}.name', en))
        m_ru = {'bronze': 'бронзовые', 'steel': 'стальные', 'titanium': 'титановые', 'tungstensteel': 'вольфрамостальные'}[tier]
        c_en, c_ru = {'bronze': ('Bronze Plated Bricks', 'кирпичи, обшитые бронзой'),
                      'steel': ('Solid Steel Machine Casings', 'стальные корпуса механизма'),
                      'titanium': ('Stable Titanium Machine Casings', 'стабильные титановые корпуса'),
                      'tungstensteel': ('Robust Tungstensteel Machine Casings', 'прочные вольфрамостальные корпуса')}[tier]
        oxygen = {'titanium': 32, 'tungstensteel': 40}.get(tier)
        tooltip(bid, [
            (f'Produces {per_coal} L of Steam with 1 Coal at {steam} L/t', f'{per_coal} л пара с 1 угля, {steam} л/т'),
            ('A programmed circuit in the controller throttles it (-50 L/t per config)', 'Программная схема в контроллере снижает выход (−50 л/т за единицу)'),
            ('3x5x3, controller in the middle of the front of the bottom layer', 'Форма 3×5×3, контроллер — в середине передней стороны нижнего слоя'),
            (f'Bottom: {mat} Firebox Casings (min 3) or maintenance, input hatch/bus, muffler', f'Низ: {m_ru} топки (не меньше 3) или люки: обслуживание, входные люк и шина, глушитель'),
            (f'Above: hollow {c_en} (min 24) or steam output hatches', f'Выше: полые {c_ru} (не меньше 24) или выходные люки для пара'),
            (f'Inside: 3 {mat} Pipe Casings', f'Внутри: 3 {m_ru} блока обсадных труб'),
            ('Distilled Water first, then Water; runs dry - explodes', 'Сначала дистиллированная вода, потом обычная; без воды — взрыв'),
            (f'Diesel fuels have 1/4 efficiency; heats up in {heat_s} s', f'Дизельное топливо — с 1/4 отдачи; разогрев {heat_s} с'),
            ('Causes up to 240 Pollution per second', 'Загрязнение: до 240 в секунду')]
            + ([(f'{oxygen} L of Oxygen per portion and solid fuel give a boost (x3)', f'{oxygen} л кислорода за порцию с твёрдым топливом — разгон ×3')]
               if oxygen else []))
    # GT_Loader_MetaTileEntities:1157 "WCW/CMC/WCW": bronze firebox, basic circuit, tin cable.
    shaped('multi/large_boiler_bronze', f'{NS}:large_boiler_bronze', 1, ['WCW', 'CMC', 'WCW'],
           {'M': f'{NS}:bronze_firebox_casing', 'C': '#c:circuits/basic', 'W': '#iis:cables/1x/tin'})
    # :1158 steel: steel firebox, Advanced (HV) circuits, copper cables (D92)
    shaped('multi/large_boiler_steel', f'{NS}:large_boiler_steel', 1, ['WCW', 'CMC', 'WCW'],
           {'M': f'{NS}:steel_firebox_casing', 'C': '#c:circuits/advanced', 'W': '#iis:cables/1x/copper'})
    # :1159 titanium: titanium firebox, Data circuits, gold cables (D97)
    shaped('multi/large_boiler_titanium', f'{NS}:large_boiler_titanium', 1, ['WCW', 'CMC', 'WCW'],
           {'M': f'{NS}:titanium_firebox_casing', 'C': '#c:circuits/data', 'W': '#iis:cables/1x/gold'})
    # :1160 tungstensteel: tungstensteel firebox, Elite circuits, aluminium cables (D98)
    shaped('multi/large_boiler_tungstensteel', f'{NS}:large_boiler_tungstensteel', 1, ['WCW', 'CMC', 'WCW'],
           {'M': f'{NS}:tungstensteel_firebox_casing', 'C': '#c:circuits/elite', 'W': '#iis:cables/1x/aluminium'})
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'LargeBoiler.png'), tex_out('gui/multi/large_boiler.png'))
    for k, en, ruu in (('gui.iis.boiler.steam', 'Steam: %s L/t', 'Пар: %s л/т'),
                       ('gui.iis.boiler.sh_steam', 'Superheated steam: %s L/t', 'Перегретый пар: %s л/т'),
                       ('gui.iis.boiler.efficiency', 'Efficiency: %s%%', 'КПД: %s%%'),
                       ('gui.iis.boiler.fuel', 'Fuel in reserve: %s t', 'Запас горения: %s т'),
                       ('gui.iis.boiler.circuit', 'Throttle: circuit %s', 'Снижение: схема %s')):
        name(k, en, ruu)

    # --- assembler: GT_MachineRecipeLoader:1389 6 steel plates + steel frame -> 2 solid steel casings (50 t, 16 EU/t) -------
    write(os.path.join(D, NS, 'recipe', 'processing', 'assembler', 'solid_steel_casing.json'), {
        'type': f'{NS}:assembler', 'inputs': [{'ingredient': tag_of('plate', 'Steel'), 'count': 6}, {'ingredient': '#c:frames/steel', 'count': 1}],
        'outputs': [{'id': f'{NS}:solid_steel_casing', 'count': 2}], 'duration': 50, 'eut': 16})
    stats['large_boilers'] = len(BOILERS)

    # --- large steam turbine (GT_MetaTileEntity_LargeTurbine_Steam, D89) ------------------------------------------------
    # Casing GT_Block_Casings4:9 ("PhP/PFP/PwP" magnalium plates + blue steel frame, D90) and controller (:1248, D92).
    first(os.path.join(BI, 'MACHINE_CASING_TURBINE.png')).save(tex_out('block/multi/turbine_casing.png'))
    write(os.path.join(A, 'models', 'block', 'turbine_casing.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/multi/turbine_casing'}})
    write(os.path.join(A, 'blockstates', 'turbine_casing.json'), {'variants': {'': {'model': f'{NS}:block/turbine_casing'}}})
    simple('turbine_casing')
    name(f'block.{NS}.turbine_casing', 'Turbine Casing', ru('gt.blockcasings4.9.name', 'Turbine Casing'))
    tooltip('turbine_casing', [('Walls of the Large Steam Turbine', 'Стенки большой паровой турбины')])
    # GT_Loader_MetaTileEntities:71 "PhP/PFP/PwP" magnalium plates + blue steel frame -> 2; GT_MachineRecipeLoader:1396 assembler (D90)
    shaped('multi/turbine_casing', f'{NS}:turbine_casing', 2, ['PhP', 'PFP', 'PwP'], {'P': tag_of('plate', 'Magnalium'), 'F': '#c:frames/blue_steel'})
    mrec('assembler', 'turbine_casing', [ing(tag_of('plate', 'Magnalium'), 6), ing('#c:frames/blue_steel')], [rout(f'{NS}:turbine_casing', 2)], 50, 16)
    tc = first(os.path.join(BI, 'MACHINE_CASING_TURBINE.png'))
    for suf, ov in (('', 'LARGETURBINE_ST5'), ('_active', 'LARGETURBINE_ST_ACTIVE5')):
        o = Image.open(os.path.join(BI, ov + '.png')).convert('RGBA')
        frames_n = max(1, o.height // 16)
        out = Image.new('RGBA', (16, 16 * frames_n))
        for f in range(frames_n):
            out.paste(Image.alpha_composite(tc, o.crop((0, 16 * f, 16, 16 * f + 16))), (0, 16 * f))
        out.save(tex_out(f'block/multi/large_steam_turbine_front{suf}.png'))
        meta = os.path.join(BI, ov + '.png.mcmeta')
        if frames_n > 1 and os.path.exists(meta):
            shutil.copy(meta, tex_out(f'block/multi/large_steam_turbine_front{suf}.png.mcmeta'))
        side = f'{NS}:block/multi/turbine_casing'
        write(os.path.join(A, 'models', 'block', 'large_steam_turbine' + suf + '.json'), {
            'parent': 'minecraft:block/cube',
            'textures': {'particle': side, 'north': f'{NS}:block/multi/large_steam_turbine_front{suf}', 'south': side, 'east': side,
                         'west': side, 'up': side, 'down': side}})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for active in (False, True):
            v = {'model': f'{NS}:block/large_steam_turbine' + ('_active' if active else '')}
            if y:
                v['y'] = y
            variants[f'facing={facing},active={str(active).lower()}'] = v
    write(os.path.join(A, 'blockstates', 'large_steam_turbine.json'), {'variants': variants})
    simple('large_steam_turbine')
    name(f'block.{NS}.large_steam_turbine', 'Large Steam Turbine', ru('gt.blockmachines.multimachine.largeturbine.name', 'Large Steam Turbine'))
    tooltip('large_steam_turbine', [
        ('3x3x4 hollow, controller in the middle of the front; 3x3 of air in front of it', 'Форма 3×3×4, пустая внутри; контроллер — в середине передней стороны, перед ним 3×3 воздуха'),
        ('Side centres of layers 2-3: steam input hatch, distilled water output hatch, maintenance', 'Середины сторон 2-го и 3-го слоёв: вход пара, выход дистиллированной воды, обслуживание'),
        ('Back centre: Dynamo Hatch; Turbine Casings for the rest (24 at least)', 'Середина задней стороны — динамо-люк; остальное — турбинные корпуса (не меньше 24)'),
        ('Needs a turbine rotor in the controller; output 60-3360 EU/t by rotor', 'Нужен ротор в контроллере; выход 60–3360 EU/t в зависимости от ротора'),
        ('More EU/t than the dynamo hatch takes - the whole machine explodes', 'Выход больше, чем принимает динамо-люк, — вся машина взрывается')])
    # GT_Loader_MetaTileEntities:1248 "CPC/PMP/BPB": HV hull, Advanced circuits, steel gears, large steel pipes (D92)
    shaped('multi/large_steam_turbine', f'{NS}:large_steam_turbine', 1, ['CPC', 'PMP', 'BPB'],
           {'M': f'{NS}:hull_hv', 'C': '#c:circuits/advanced', 'P': tag_of('gear', 'Steel'), 'B': f'{NS}:pipe_large_steel'})
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'LargeTurbine.png'), tex_out('gui/multi/large_turbine.png'))
    for k, en, ruu in (('gui.iis.turbine.no_rotor', 'Insert a turbine rotor', 'Вставьте ротор турбины'),
                       ('gui.iis.turbine.output', 'Output: %s EU/t', 'Выход: %s EU/t'),
                       ('gui.iis.turbine.flow', 'Optimal flow: %s L/t', 'Оптимальный поток: %s л/т'),
                       ('gui.iis.turbine.speed', 'Speed: %s%%, steam: %s L', 'Скорость: %s%%, пар: %s л'),
                       ('gui.iis.turbine.damage', 'Rotor wear: %s%%', 'Износ ротора: %s%%')):
        name(k, en, ruu)

    # --- rotors (GT_MetaGenerated_Tool_01 170-176): material in iis:rotor_material, tinted head + overlay --------------------
    II = os.path.join(GT, 'items', 'iconsets')
    for size, en, ruu in (('small', 'Small Turbine', 'Малая турбина'), ('normal', 'Turbine', 'Турбина'),
                          ('large', 'Large Turbine', 'Большая турбина'), ('huge', 'Huge Turbine', 'Огромная турбина')):
        iid = f'turbine_{size}'
        src = 'TURBINE' if size == 'normal' else f'TURBINE_{size.upper()}'
        first(os.path.join(II, src + '.png')).save(tex_out(f'item/turbine/{iid}.png'))
        first(os.path.join(II, src + '_OVERLAY.png')).save(tex_out(f'item/turbine/{iid}_overlay.png'))
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/handheld', 'textures': {
            'layer0': f'{NS}:item/turbine/{iid}', 'layer1': f'{NS}:item/turbine/{iid}_overlay'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}',
                                                                  'tints': [{'type': f'{NS}:rotor_material'}]}})
        name(f'item.{NS}.{iid}', en, ruu)
        name(f'item.{NS}.{iid}.named', f'%s {en}', f'{ruu} (%s)')
    for k, en, ruu in (('item.iis.turbine.efficiency', 'Turbine Efficiency: %s%%', 'КПД турбины: %s%%'),
                       ('item.iis.turbine.flow', 'Optimal Steam flow: %s L/t (%s L/s)', 'Оптимальный поток пара: %s л/т (%s л/с)'),
                       ('item.iis.turbine.durability', 'Durability: %s / %s', 'Прочность: %s / %s')):
        name(k, en, ruu)
    turbine_mats = [m for m in g['materials'] if m['types'] & 64 and m['toolDurability'] > 0
                    and 'NO_SMASHING' not in m['subTags'] and 'BOUNCY' not in m['subTags']]
    for m in turbine_mats:
        name(f'material.{NS}.{m["id"]}', m['en'], m['ru'])
    stats['rotor_materials'] = len(turbine_mats)

    # --- D90: blue steel chain and magnalium (GT_CraftingRecipeLoader:383-389, GT_MachineRecipeLoader:1766-1783) ------------
    # crafting: dusts mixed by hand
    for out_m, cnt, ins in (('RoseGold', 5, ['Gold'] * 4 + ['Copper']), ('BlackBronze', 5, ['Gold', 'Silver'] + ['Copper'] * 3),
                            ('BlackSteel', 5, ['Nickel', 'BlackBronze'] + ['Steel'] * 3),
                            ('BlueSteel', 8, ['RoseGold', 'Brass'] + ['Steel'] * 2 + ['BlackSteel'] * 4)):
        g['shapeless'](f'dust/mix_{mat_id(out_m)}', ref('dust', out_m), cnt, [tag_of('dust', m) for m in ins])
    # mixer: dust, small and tiny piles (amount of the form x 100 ticks per output dust, 8 EU/t)
    for out_m, cnt, ins in (('RoseGold', 5, [('Copper', 1), ('Gold', 4)]), ('BlackBronze', 5, [('Copper', 3), ('Electrum', 2)]),
                            ('BlackSteel', 5, [('BlackBronze', 1), ('Nickel', 1), ('Steel', 3)]),
                            ('BlueSteel', 8, [('RoseGold', 1), ('Brass', 1), ('BlackSteel', 4), ('Steel', 2)])):
        for p, ticks in (('dust', cnt * 100), ('small_dust', cnt * 100 // 4), ('tiny_dust', cnt * 100 // 9)):
            mrec('mixer', f'{mat_id(out_m)}_{p}', [ing(tag_of(p, m), c) for m, c in ins], [rout(ref(p, out_m), cnt)], ticks, 8)

    # --- D90: double plates (ProcessingPlate:110-120), turbine blades (ProcessingToolHead:154-161) and small rotors -------
    rods = ref('long_rod', 'Magnalium')
    for n in sorted(g['names']):
        dp, pl, mid = ref('double_plate', n), ref('plate', n), mat_id(n)
        if dp and pl:
            mrec('bender', f'double_plate_{mid}', [ing(tag_of('plate', n), 2)], [rout(dp)], max(g['mass'](n) * 2, 1), 96)
            shaped(f'plate/double_plate_{mid}', dp, 1, ['P', 'P', 'h'], {'P': tag_of('plate', n)})
        blade = ref('turbine_blade', n)
        if blade and dp and ref('screw', n):
            shaped(f'part/turbine_blade_{mid}', blade, 1, ['fPd', 'SPS', ' P '], {'P': tag_of('double_plate', n), 'S': tag_of('screw', n)})
        # assembler 4 blades + long magnalium rod -> small turbine (160 t, 100 EU/t); bigger rotors need titanium,
        # tungstensteel and americium rods (later)
        if blade and rods and any(m['id'] == mid for m in turbine_mats):
            m = mat_by_name[n]
            rec[f'assembler/turbine_small_{mid}'] = {
                'type': f'{NS}:assembler', 'inputs': [ing(tag_of('turbine_blade', n), 4), ing(tag_of('long_rod', 'Magnalium'))],
                'outputs': [{'id': f'{NS}:turbine_small', 'count': 1, 'components': {
                    f'{NS}:rotor_material': mid, 'minecraft:max_damage': 100 * int(m['toolDurability'] * 1.0)}}],
                'duration': 160, 'eut': 100}
    # --- D91: silicon wafers and chips (GT_MachineRecipeLoader:475-476, 507-508, 620-621, 1149; ProcessingCrafting:51, 58) ---
    mrec('electrolyzer', 'sand_silicon_dioxide', [ing('minecraft:sand', 8)], [rout(ref('dust', 'SiliconDioxide'))], 500, 25)
    mrec('blast', 'silicon_boule', [ing(tag_of('dust', 'Silicon'), 32), ing(tag_of('tiny_dust', 'Gallium'))], [rout(f'{NS}:silicon_boule')],
         9000, 120, temp=1784)
    cutter('silicon_wafer', [ing(f'{NS}:silicon_boule')], [rout(f'{NS}:silicon_wafer', 16)], 200, 8)
    # craftingLensRed -> integrated logic circuit wafer, craftingLensCyan -> RAM wafer (900 t, 120 EU/t, no cleanroom)
    mrec('laser_engraver', 'wafer_ilc', [ing(f'{NS}:silicon_wafer')], [rout(f'{NS}:wafer_ilc')], 900, 120, keep='#iis:crafting_lens/red')
    mrec('laser_engraver', 'wafer_ram', [ing(f'{NS}:silicon_wafer')], [rout(f'{NS}:wafer_ram')], 900, 120, keep='#iis:crafting_lens/cyan')
    cutter('chip_ilc', [ing(f'{NS}:wafer_ilc')], [rout(f'{NS}:chip_ilc', 8)], 600, 48)
    cutter('chip_ram', [ing(f'{NS}:wafer_ram')], [rout(f'{NS}:chip_ram', 32)], 600, 48)
    # diode from a silicon wafer: 1 diode (the tiny gallium variant gives 4, lv.py)
    for wid, wire in (('wire', '#iis:wires/1x/tin'), ('fine_wire', tag_of('wire_fine', 'Tin'))):
        shaped(f'lv/diode_wafer_{wid}', f'{NS}:diode', 1, ['BG ', 'WDW', 'BG '], {'B': '#c:dyes/black', 'G': 'minecraft:glass_pane',
                                                                             'D': f'{NS}:silicon_wafer', 'W': wire})
    stats['d92_machine_recipes'] = d92(g, mrec, ing, rout, shaped, tag_of, ref, NS)
    # --- D97: titanium ---------------------------------------------------------------------------------------------------
    # :1393 assembler 6 titanium plates + frame -> 2 stable titanium casings (50 t, 16 EU/t)
    mrec('assembler', 'stable_titanium_casing', [ing(tag_of('plate', 'Titanium'), 6), ing('#c:frames/titanium')],
         [rout(f'{NS}:stable_titanium_casing', 2)], 50, 16)
    # :1043 EBF: 2 magnesium dust + 1000 L TiCl4 -> hot titanium ingot + 2 MgCl2 dust (800 t, 480 EU/t, 1940 + 200 K)
    mrec('blast', 'titanium_tetrachloride', [ing(tag_of('dust', 'Magnesium'), 2)],
         [rout(ref('hot_ingot', 'Titanium')), rout(ref('dust', 'Magnesiumchloride'), 2)], 800, 480,
         fin={'ingredient': f'{NS}:titaniumtetrachloride', 'amount': 1000}, temp=2140)
    # ProcessingToolHead:156 normal turbine: 8 blades + long titanium rod (320 t, 400 EU/t — HV assembler); durability x2.
    # Large: D98 below; huge (16 + americium, 6400 EU/t, IV) waits for americium
    if ref('long_rod', 'Titanium'):
        for n in sorted(g['names']):
            mid = mat_id(n)
            if ref('turbine_blade', n) and any(m['id'] == mid for m in turbine_mats):
                m = mat_by_name[n]
                rec[f'assembler/turbine_normal_{mid}'] = {
                    'type': f'{NS}:assembler', 'inputs': [ing(tag_of('turbine_blade', n), 8), ing(tag_of('long_rod', 'Titanium'))],
                    'outputs': [{'id': f'{NS}:turbine_normal', 'count': 1, 'components': {
                        f'{NS}:rotor_material': mid, 'minecraft:max_damage': 100 * int(m['toolDurability'] * 2.0)}}],
                    'duration': 320, 'eut': 400}
    # GT_MachineRecipeLoader:446 chemical reactor: plastic plate + copper foil + 125 L sulfuric acid -> plastic board (500 t, 10)
    mrec('chemical_reactor', 'circuit_board_plastic', [ing(tag_of('plate', 'Plastic')), ing(tag_of('foil', 'Copper'))],
         [rout(f'{NS}:circuit_board_plastic')], 500, 10, fin={'ingredient': f'{NS}:sulfuricacid', 'amount': 125})
    # :571 circuit assembler: 2 plastic boards + 3 Advanced circuits + 4 diodes + 4 RAM + 6 fine electrum + 144 L soldering
    # alloy -> Data circuit (400 t, 90 EU/t — MV)
    mrec('circuit_assembler', 'circuit_data', [ing(f'{NS}:circuit_board_plastic', 2), ing(f'{NS}:circuit_advanced', 3), ing(f'{NS}:diode', 4),
                                               ing(f'{NS}:chip_ram', 4), ing(tag_of('wire_fine', 'Electrum'), 6)],
         [rout(f'{NS}:circuit_data')], 400, 90, fin={'ingredient': f'{NS}:molten_solderingalloy', 'amount': 144})
    # --- D98: nichrome, tungstensteel, Elite circuit, large rotor, EV parts ---------------------------------------------
    # GT_MachineRecipeLoader:1033 EBF 4 nickel + 1 chrome ingots -> 5 hot nichrome (max(mass / 32, 1) x 2700 t, 480 EU/t, 2700 K);
    # :1029 tungsten + steel ingots -> 2 hot tungstensteel (max(mass / 80, 1) x 3000 t, 480 EU/t, 3000 K — nichrome coils)
    mass = g['mass']
    mrec('blast', 'nichrome_ingots', [ing(tag_of('ingot', 'Nickel'), 4), ing(tag_of('ingot', 'Chrome'))], [rout(ref('hot_ingot', 'Nichrome'), 5)],
         max(mass('Nichrome') // 32, 1) * 2700, 480, temp=2700)
    mrec('blast', 'tungstensteel_ingots', [ing(tag_of('ingot', 'Tungsten')), ing(tag_of('ingot', 'Steel'))],
         [rout(ref('hot_ingot', 'TungstenSteel'), 2)], max(mass('TungstenSteel') // 80, 1) * 3000, 480, temp=3000)
    # IIS tungsten processing (GT_MachineRecipeLoader2:37-52): scheelite / tungstate + 4000 L HCl -> chloride + 7 tungstic acid
    # (100 t, 1920 EU/t — EV chemical reactor); tungstic acid + circuit 1 -> 4 WO3 + 3000 L water (200 t, 90); EBF 4 WO3 +
    # 6000 L hydrogen -> hot tungsten ingot (12000 t, 120 EU/t, 3000 K — nichrome coils)
    hcl = {'ingredient': f'{NS}:hydrochloricacid_gt5u', 'amount': 4000}
    mrec('chemical_reactor', 'tungstic_acid_scheelite', [ing(tag_of('dust', 'Scheelite'), 6)],
         [rout(ref('dust', 'CalciumChloride'), 3), rout(ref('dust', 'TungstigAcid'), 7)], 100, 1920, fin=hcl)
    mrec('chemical_reactor', 'tungstic_acid_tungstate', [ing(tag_of('dust', 'Tungstate'), 7)],
         [rout(ref('dust', 'LithiumChloride'), 4), rout(ref('dust', 'TungstigAcid'), 7)], 100, 1920, fin=hcl)
    mrec('chemical_reactor', 'tungsten_trioxide', [ing(tag_of('dust', 'TungstigAcid'), 7)], [rout(ref('dust', 'TungstenTrioxide'), 4)], 200, 90,
         fout={'id': 'minecraft:water', 'amount': 3000}, circuit=1)
    mrec('blast', 'tungsten_trioxide', [ing(tag_of('dust', 'TungstenTrioxide'), 4)], [rout(ref('hot_ingot', 'Tungsten'))], 12000, 120,
         fin={'ingredient': f'{NS}:hydrogen', 'amount': 6000}, temp=3000)
    # GT_CraftingRecipeLoader:381 4 nickel + 1 chrome dust -> 5 nichrome dust
    if ref('dust', 'Nichrome'):
        g['shapeless']('dust/mix_nichrome', ref('dust', 'Nichrome'), 5, [tag_of('dust', 'Nickel')] * 4 + [tag_of('dust', 'Chrome')])
    # :1391 assembler 6 tungstensteel plates + frame -> 2 robust casings
    mrec('assembler', 'robust_tungstensteel_casing', [ing(tag_of('plate', 'TungstenSteel'), 6), ing('#c:frames/tungsten_steel')],
         [rout(f'{NS}:robust_tungstensteel_casing', 2)], 50, 16)
    # ProcessingToolHead:157 large turbine: 12 blades + long tungstensteel rod (640 t, 1600 EU/t — EV assembler); durability x3
    if ref('long_rod', 'TungstenSteel'):
        for n in sorted(g['names']):
            mid = mat_id(n)
            if ref('turbine_blade', n) and any(m['id'] == mid for m in turbine_mats):
                m = mat_by_name[n]
                rec[f'assembler/turbine_large_{mid}'] = {
                    'type': f'{NS}:assembler', 'inputs': [ing(tag_of('turbine_blade', n), 12), ing(tag_of('long_rod', 'TungstenSteel'))],
                    'outputs': [{'id': f'{NS}:turbine_large', 'count': 1, 'components': {
                        f'{NS}:rotor_material': mid, 'minecraft:max_damage': 100 * int(m['toolDurability'] * 3.0)}}],
                    'duration': 640, 'eut': 1600}
    # :489 capacitor: plastic plate + 2 aluminium foil -> 2 (80 t, 96 EU/t)
    mrec('assembler', 'capacitor', [ing(tag_of('plate', 'Plastic')), ing(tag_of('foil', 'Aluminium'), 2)], [rout(f'{NS}:capacitor', 2)], 80, 96)
    # :572 Elite circuit: aluminium frame + 4 Data + 4 small coils + 24 capacitors + 16 RAM + 12 1x annealed copper wires +
    # 288 L soldering alloy (1600 t, 480 EU/t — HV circuit assembler)
    mrec('circuit_assembler', 'circuit_elite', [ing('#c:frames/aluminium'), ing(f'{NS}:circuit_data', 4), ing(f'{NS}:coil_small', 4),
                                                ing(f'{NS}:capacitor', 24), ing(f'{NS}:chip_ram', 16), ing('#iis:wires/1x/annealed_copper', 12)],
         [rout(f'{NS}:circuit_elite')], 1600, 480, fin={'ingredient': f'{NS}:molten_solderingalloy', 'amount': 288})
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    stats['d90_machine_recipes'] = len(rec)
    return stats


def d92(g, mrec, ing, rout, shaped, tag_of, ref, NS):
    """D92: biomass -> ethanol -> ethylene -> plastic and glue, the circuit assembler chain up to the Advanced (HV) circuit, HV hull.

    IIS 1 got bio chaff from IC2: config/ic2/macerator.ini of the IIS 1.19.1 pack, moved into the GT macerator by
    GT_ModHandler#addIC2RecipesToGT (GT_Mod:699, 300 t, 2 EU/t) unless GT already had a recipe for the input. Plant balls come
    from GT compressor recipes. Everything else is GT_MachineRecipeLoader / GT_RecipeAdder (line numbers below).
    Returns the number of recipes it tried to add (mrec skips those with a missing item).
    """
    def capsule(fid=None):
        comp = {f'!{NS}:fluid_content': {}} if fid is None else {f'{NS}:fluid_content': {'fluid': fid, 'amount': 1000}}
        return {'neoforge:ingredient_type': 'neoforge:components', 'items': f'{NS}:capsule', 'components': comp}

    def fl(fid, amount):
        return {'ingredient': fid, 'amount': amount}

    def fo(fid, amount):
        return {'id': fid, 'amount': amount}

    n = [0]

    def m(*a, **k):
        n[0] += 1
        mrec(*a, **k)
    # --- bio chaff: IC2 macerator entries GT did not override (reeds, melon, pumpkin, wheat and saplings have GT recipes) ---
    for rid, item, cnt in (('leaves', '#minecraft:leaves', 8), ('potato', 'minecraft:potato', 8), ('carrot', 'minecraft:carrot', 8),
                           ('cactus', 'minecraft:cactus', 8), ('wheat_seeds', 'minecraft:wheat_seeds', 16),
                           ('melon_seeds', 'minecraft:melon_seeds', 16), ('pumpkin_seeds', 'minecraft:pumpkin_seeds', 16),
                           ('plant_ball', f'{NS}:plant_ball', 1)):
        m('macerator', f'bio_chaff_{rid}', [ing(item, cnt)], [rout(f'{NS}:bio_chaff')], 300, 2)
    m('macerator', 'bio_chaff_dirt', [ing(f'{NS}:bio_chaff')], [rout('minecraft:dirt')], 300, 2)
    # GT's own macerator recipes for those plants (GT_MetaGenerated_Item_02:413-417, ProcessingSaplings:18; 400 t, 2 EU/t)
    m('macerator', 'sugar_cane', [ing('minecraft:sugar_cane')], [rout('minecraft:sugar')], 400, 2)
    m('macerator', 'melon_block', [ing('minecraft:melon')], [rout('minecraft:melon_slice', 8), dict(rout('minecraft:melon_seeds'), chance=8000)],
      400, 2)
    m('macerator', 'pumpkin', [ing('minecraft:pumpkin')], [rout('minecraft:pumpkin_seeds', 4)], 400, 2)
    m('macerator', 'melon_slice', [ing('minecraft:melon_slice')], [rout('minecraft:melon_seeds')], 400, 2)
    m('macerator', 'wheat', [ing('minecraft:wheat')], [rout(ref('dust', 'Wheat'))], 400, 2)
    m('macerator', 'sapling', [ing('#minecraft:saplings')], [rout(ref('small_dust', 'Wood'), 2)], 400, 2)
    # wood pulp (dustWood): ProcessingLog:24 log -> 6 + 80 % 1 (rubber log :22: 6 + 33 % sticky resin), 400 t, 2 EU/t;
    # 4 small or 9 tiny piles -> 1 (ProcessingDust; the generator skips the other Wood forms, they are vanilla)
    wood = ref('dust', 'Wood')
    for w, kind in [(w, 'logs') for w in ('oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak')] + \
                   [('crimson', 'stems'), ('warped', 'stems')]:
        m('macerator', f'{kind[:-1]}_{w}', [ing(f'#minecraft:{w}_{kind}')], [rout(wood, 6), dict(rout(wood), chance=8000)], 400, 2)
    m('macerator', 'log_rubber', [ing(f'{NS}:rubber_log')], [rout(wood, 6), dict(rout(f'{NS}:sticky_resin'), chance=3300)], 400, 2)
    # :383 fluid extractor: wood pulp -> 5 L creosote, 1 % plant ball (16 t, 4 EU/t)
    m('fluid_extractor', 'dust_wood_creosote', [ing(tag_of('dust', 'Wood'))], [dict(rout(f'{NS}:plant_ball'), chance=100)], 16, 4,
      fout=fo(f'{NS}:creosote', 5))
    if wood:
        g['shapeless']('dust/from_small_wood', wood, 1, [tag_of('small_dust', 'Wood')] * 4)
        g['shapeless']('dust/from_tiny_wood', wood, 1, [tag_of('tiny_dust', 'Wood')] * 9)
    # plant balls: compressor 8 saplings (ProcessingSaplings:19), 8 wheat (:955), 8 sugar cane (:1832); 300 t, 2 EU/t
    for rid, item in (('saplings', '#minecraft:saplings'), ('wheat', 'minecraft:wheat'), ('sugar_cane', 'minecraft:sugar_cane')):
        m('compressor', f'plant_ball_{rid}', [ing(item, 8)], [rout(f'{NS}:plant_ball')], 300, 2)
    # :1887 centrifuge: sticky resin -> 3 raw rubber dust, 10 % plant ball, 100 L glue (300 t, 5 EU/t)
    m('centrifuge', 'sticky_resin', [ing(f'{NS}:sticky_resin')],
      [rout(ref('dust', 'RawRubber'), 3), dict(rout(f'{NS}:plant_ball'), chance=1000)], 300, 5, fout=fo(f'{NS}:glue', 100))
    # --- brewery (:767, GT_RecipeAdder#addBrewingRecipe:784): bio chaff + 750 L water or distilled water -> 750 L biomass ---
    for rid, water in (('water', 'minecraft:water'), ('distilled_water', f'{NS}:distilled_water')):
        m('brewery', f'biomass_{rid}', [ing(f'{NS}:bio_chaff')], [], 128, 4, fin=fl(water, 750), fout=fo(f'{NS}:biomass', 750))
    # biomass -> ethanol (distillery :713) and ethanol -> ethylene (:3437) are imported by chem.py
    # --- polymerization (:3443, GT_RecipeAdder#addDefaultPolymerizationRecipes:271-276): ethylene -> molten polyethylene ---
    eth, pl = f'{NS}:ethylene', f'{NS}:molten_plastic'
    m('chemical_reactor', 'plastic_air_cell', [ing(capsule(f'{NS}:air'))], [rout(f'{NS}:capsule')], 320, 30,
      fin=fl(eth, 288), fout=fo(pl, 288), circuit=1)
    m('chemical_reactor', 'plastic_oxygen_cell', [ing(capsule(f'{NS}:oxygen'))], [rout(f'{NS}:capsule')], 160, 30,
      fin=fl(eth, 144), fout=fo(pl, 216), circuit=1)
    m('chemical_reactor', 'plastic_ethylene_cell_air', [ing(capsule(eth))], [rout(f'{NS}:capsule')], 1120, 30,
      fin=fl(f'{NS}:air', 14000), fout=fo(pl, 1000), circuit=1)
    m('chemical_reactor', 'plastic_ethylene_cell_oxygen', [ing(capsule(eth))], [rout(f'{NS}:capsule')], 1120, 30,
      fin=fl(f'{NS}:oxygen', 7000), fout=fo(pl, 1500), circuit=1)
    # --- phenolic circuit board (:442 crafting 8 wood dust + glue cell -> 8; :444 assembler with the plate mold, 30 t, 8 EU/t) ---
    shaped('lv/circuit_board_phenolic', f'{NS}:circuit_board_phenolic', 8, ['PRP', 'PPP', 'PPP'],
           {'P': tag_of('dust', 'Wood'), 'R': capsule(f'{NS}:glue')})
    m('assembler', 'circuit_board_phenolic', [ing(tag_of('dust', 'Wood'))], [rout(f'{NS}:circuit_board_phenolic')], 30, 8,
      fin=fl(f'{NS}:glue', 100), keep=f'{NS}:mold_plate')
    # --- transistor (:487): silicon plate + 6 fine tin wires + 144 L molten plastic -> 8 (80 t, 24 EU/t) ---
    m('assembler', 'transistor', [ing(tag_of('plate', 'Silicon')), ing(tag_of('wire_fine', 'Tin'), 6)], [rout(f'{NS}:transistor', 8)],
      80, 24, fin=fl(pl, 144))
    # --- circuit assembler (:555-564). IIS 1 Recipes.cfg useonlygoodsolderingmaterials=true: soldering alloy only, 72 L ---
    sol = fl(f'{NS}:molten_solderingalloy', 144 // 2)
    m('circuit_assembler', 'circuit_basic', [ing(f'{NS}:circuit_board_phenolic'), ing(f'{NS}:chip_ilc'), ing(f'{NS}:resistor', 2),
                                             ing(tag_of('wire_fine', 'Copper'), 4)], [rout(f'{NS}:circuit_basic')], 200, 8, fin=sol)
    m('circuit_assembler', 'circuit_integrated_good', [ing(f'{NS}:circuit_board_phenolic'), ing(f'{NS}:circuit_basic', 3),
                                                       ing(f'{NS}:resistor', 4), ing(tag_of('wire_fine', 'Gold'), 8)],
      [rout(f'{NS}:circuit_integrated_good')], 400, 16, fin=sol)
    m('circuit_assembler', 'circuit_advanced', [ing(f'{NS}:circuit_integrated_good', 2), ing(f'{NS}:chip_ilc', 3), ing(f'{NS}:chip_ram'),
                                                ing(f'{NS}:transistor', 4), ing(tag_of('wire_fine', 'Electrum'), 16)],
      [rout(f'{NS}:circuit_advanced')], 800, 28, fin=sol)
    # --- hulls in the assembler (:1404-1407, mHardMachineCasings): 2 cables + casing + 288 L molten plastic (50 t, 16 EU/t) ---
    for rid, cable, casing, hull in (('lv', 'tin', 'casing_lv', 'hull_lv'), ('mv', 'copper', 'casing_mv', 'hull_mv'),
                                     ('mv_annealed', 'annealed_copper', 'casing_mv', 'hull_mv'), ('hv', 'gold', 'casing_hv', 'hull_hv'),
                                     ('ev', 'aluminium', 'casing_ev', 'hull_ev')):   # :1408 EV (D98)
        m('assembler', f'hull_{rid}', [ing(f'#iis:cables/1x/{cable}', 2), ing(f'{NS}:{casing}')], [rout(f'{NS}:{hull}')], 50, 16,
          fin=fl(pl, 288))
    return n[0]
