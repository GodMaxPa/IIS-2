"""Electric blast furnace content (GT_MetaTileEntity_ElectricBlastFurnace) and multiblock parts, called from gen_content.py.

Blocks: frames, heat proof casing, cupronickel coil, LV hatches/buses, maintenance and muffler hatches, EBF controller;
item: LV soldering iron; recipes: crafting + assembler + blast recipes (ProcessingDust, ProcessingOreSmelting,
GT_MachineRecipeLoader). Java lists the same ids (ru.iis.multi.*).
"""
import os
import shutil

from PIL import Image

GT = '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures'
FRAMES = ['Invar', 'Steel', 'Iron', 'Bronze', 'Aluminium']
HATCHES = [
    # id, overlay, GT lang key, EN
    ('hatch_energy_lv', 'OVERLAY_ENERGY_IN_MULTI', 'gt.blockmachines.hatch.energy.tier.01.name', 'LV Energy Hatch'),
    ('hatch_input_bus_lv', 'OVERLAY_PIPE_IN', 'gt.blockmachines.hatch.input_bus.tier.01.name', 'LV Input Bus'),
    ('hatch_output_bus_lv', 'OVERLAY_PIPE_OUT', 'gt.blockmachines.hatch.output_bus.tier.01.name', 'LV Output Bus'),
    ('hatch_input_lv', 'OVERLAY_PIPE_IN', 'gt.blockmachines.hatch.input.tier.01.name', 'LV Input Hatch'),
    ('hatch_output_lv', 'OVERLAY_PIPE_OUT', 'gt.blockmachines.hatch.output.tier.01.name', 'LV Output Hatch'),
    ('hatch_maintenance', 'OVERLAY_MAINTENANCE', 'gt.blockmachines.hatch.maintenance.name', 'Maintenance Hatch'),
    ('hatch_muffler_lv', 'OVERLAY_MUFFLER', 'gt.blockmachines.hatch.muffler.tier.01.name', 'LV Muffler Hatch'),
]


def generate(g):
    import json
    write, A, D, NS, lang, TEX, REF = g['write'], g['A'], g['D'], g['NS'], g['lang'], g['TEX'], g['REF']
    ref, tag_of, mat_id, mat_by_name, mass = g['ref'], g['tag_of'], g['mat_id'], g['mat_by_name'], g['mass']
    shaped, add_item_tag, block_tags, prop = g['shaped'], g['add_item_tag'], g['block_tags'], g['prop']
    ru_lang = json.load(open(os.path.join(REF, 'gt5_lang_ru_machines.json'), encoding='utf-8'))
    BI = os.path.join(GT, 'blocks', 'iconsets')
    LVI = os.path.join(TEX, 'lv', 'iconsets')
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

    def img(path):
        return Image.open(path).convert('RGBA')

    def first(path):
        return img(path).crop((0, 0, 16, 16))

    def over(base, ov):
        return Image.alpha_composite(base.copy(), ov)

    def cube(model_id, faces):
        write(os.path.join(A, 'models', 'block', model_id + '.json'), {
            'parent': 'minecraft:block/cube',
            'textures': {'particle': faces['side'], 'north': faces['front'], 'south': faces.get('back', faces['side']),
                         'east': faces['side'], 'west': faces['side'], 'up': faces.get('top', faces['side']),
                         'down': faces.get('bottom', faces['side'])}})

    def simple(bid, tool='pickaxe'):
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})
        block_tags['minecraft:mineable/' + tool].append(f'{NS}:{bid}')

    def tooltip(bid, lines):
        for k, (en, ruu) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', en, ruu)

    # --- frames (GT_MetaPipeEntity_Frame: "SSS/SwS/SSS" rods -> 2; assembler 4 rods -> 1) -------------------------
    frames = []
    for n in FRAMES:
        if not ref('rod', n):
            continue
        m = mat_by_name[n]
        mid = mat_id(n)
        bid = f'frame_{mid}'
        iset = m['iconSet'] if os.path.exists(os.path.join(GT, 'blocks', 'materialicons', m['iconSet'], 'frameGt.png')) else 'METALLIC'
        fr = first(os.path.join(GT, 'blocks', 'materialicons', iset, 'frameGt.png'))
        rgb = ((m['color'] >> 16) & 255, (m['color'] >> 8) & 255, m['color'] & 255)
        t = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                r, gg, b, a = fr.getpixel((x, y))
                t.putpixel((x, y), (r * rgb[0] // 255, gg * rgb[1] // 255, b * rgb[2] // 255, a))
        t.save(tex_out(f'block/multi/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:cutout',
                                                                  'textures': {'all': f'{NS}:block/multi/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        simple(bid)
        en = f'{m["en"]} Frame Box'
        name(f'block.{NS}.{bid}', en, ru(f'gt.blockmachines.gt_frame_{n.lower()}.name', en))
        shaped(f'multi/{bid}', f'{NS}:{bid}', 2, ['SSS', 'SwS', 'SSS'], {'S': tag_of('rod', n)})
        add_item_tag(f'c:frames/{mid}', f'{NS}:{bid}')
        frames.append(mid)
    stats['frames'] = len(frames)

    # --- casings ------------------------------------------------------------------------------------------------
    for bid, src, key, en in (('heat_proof_casing', 'MACHINE_HEATPROOFCASING', 'gt.blockcasings.11.name', 'Heat Proof Machine Casing'),
                              ('coil_cupronickel', 'MACHINE_COIL_CUPRONICKEL', 'gt.blockcasings5.0.name', 'Cupronickel Coil Block')):
        first(os.path.join(BI, src + '.png')).save(tex_out(f'block/multi/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/multi/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
    tooltip('coil_cupronickel', [('Base Heating Capacity = 1800 Kelvin', 'Нагрев: 1800 K')])
    shaped('multi/heat_proof_casing', f'{NS}:heat_proof_casing', 2, ['PhP', 'PFP', 'PwP'], {'P': tag_of('plate', 'Invar'), 'F': '#c:frames/invar'})
    shaped('multi/coil_cupronickel', f'{NS}:coil_cupronickel', 1, ['PPP', 'PwP', 'PPP'], {'P': '#iis:wires/2x/cupronickel'})

    # --- hatches (front overlay over the LV casing; any of 6 facings) -----------------------------------------------
    side = first(os.path.join(LVI, 'MACHINE_LV_SIDE.png'))
    side.save(tex_out('block/multi/hatch_lv_side.png'))
    for bid, ov, key, en in HATCHES:
        ovp = os.path.join(BI, ov + '.png')
        if not os.path.exists(ovp):
            ovp = os.path.join(LVI, ov + '.png')
        over(side, first(ovp)).save(tex_out(f'block/multi/{bid}_front.png'))
        s_ = f'{NS}:block/multi/hatch_lv_side'
        cube(bid, {'front': f'{NS}:block/multi/{bid}_front', 'side': s_})
        variants = {}
        for facing, rot in (('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}), ('down', {'x': 90}), ('up', {'x': 270})):
            variants[f'facing={facing}'] = dict({'model': f'{NS}:block/{bid}'}, **rot)
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
    tooltip('hatch_energy_lv', [('Energy input for multiblocks: 32 EU/t, up to 2 A, from the front', 'Вход энергии многоблочной машины: 32 EU/t, до 2 А, спереди')])
    tooltip('hatch_input_bus_lv', [('Item input for multiblocks, 4 slots', 'Вход предметов многоблочной машины, 4 слота')])
    tooltip('hatch_output_bus_lv', [('Item output for multiblocks, 4 slots', 'Выход предметов многоблочной машины, 4 слота')])
    tooltip('hatch_input_lv', [('Fluid input for multiblocks, 16000 L', 'Вход жидкости многоблочной машины, 16000 л')])
    tooltip('hatch_output_lv', [('Fluid output for multiblocks, 16000 L', 'Выход жидкости многоблочной машины, 16000 л')])
    tooltip('hatch_maintenance', [('Right-click with a tool to fix a problem', 'ПКМ инструментом — устранить неисправность'),
                                  ('Wrench, Screwdriver, Soft Hammer, Hammer, Soldering Iron, Crowbar', 'Ключ, отвёртка, киянка, молот, паяльник, монтировка')])
    tooltip('hatch_muffler_lv', [('Outputs the pollution, front must face air', 'Выпускает загрязнение; спереди должен быть воздух')])
    HULL = f'{NS}:hull_lv'
    shaped('multi/hatch_energy_lv', f'{NS}:hatch_energy_lv', 1, ['CM'], {'M': HULL, 'C': '#iis:cables/1x/tin'})
    shaped('multi/hatch_input_lv', f'{NS}:hatch_input_lv', 1, ['G', 'M'], {'M': HULL, 'G': 'minecraft:glass'})
    shaped('multi/hatch_output_lv', f'{NS}:hatch_output_lv', 1, ['M', 'G'], {'M': HULL, 'G': 'minecraft:glass'})
    shaped('multi/hatch_input_bus_lv', f'{NS}:hatch_input_bus_lv', 1, ['C', 'M'], {'M': HULL, 'C': '#c:chests/wooden'})
    shaped('multi/hatch_output_bus_lv', f'{NS}:hatch_output_bus_lv', 1, ['M', 'C'], {'M': HULL, 'C': '#c:chests/wooden'})
    shaped('multi/hatch_maintenance', f'{NS}:hatch_maintenance', 1, ['dwx', 'hMc', 'fsr'], {'M': HULL})
    shaped('multi/hatch_muffler_lv', f'{NS}:hatch_muffler_lv', 1, ['M', 'P'], {'M': HULL, 'P': f'{NS}:pipe_medium_steel'})

    # --- EBF controller --------------------------------------------------------------------------------------------
    hp = first(os.path.join(BI, 'MACHINE_HEATPROOFCASING.png'))
    for suf, ov in (('', 'OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE'), ('_active', 'OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_ACTIVE')):
        o = img(os.path.join(BI, ov + '.png'))
        frames_n = max(1, o.height // 16)
        out = Image.new('RGBA', (16, 16 * frames_n))
        for f in range(frames_n):
            out.paste(over(hp, o.crop((0, 16 * f, 16, 16 * f + 16))), (0, 16 * f))
        out.save(tex_out(f'block/multi/electric_blast_furnace_front{suf}.png'))
        meta = os.path.join(BI, ov + '.png.mcmeta')
        if frames_n > 1 and os.path.exists(meta):
            shutil.copy(meta, tex_out(f'block/multi/electric_blast_furnace_front{suf}.png.mcmeta'))
        cube('electric_blast_furnace' + suf, {'front': f'{NS}:block/multi/electric_blast_furnace_front{suf}', 'side': f'{NS}:block/multi/heat_proof_casing'})
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for active in (False, True):
            v = {'model': f'{NS}:block/electric_blast_furnace' + ('_active' if active else '')}
            if y:
                v['y'] = y
            variants[f'facing={facing},active={str(active).lower()}'] = v
    write(os.path.join(A, 'blockstates', 'electric_blast_furnace.json'), {'variants': variants})
    simple('electric_blast_furnace')
    name(f'block.{NS}.electric_blast_furnace', 'Electric Blast Furnace', ru('gt.blockmachines.multimachine.blastfurnace.name', 'Electric Blast Furnace'))
    tooltip('electric_blast_furnace', [
        ('3x3x4 hollow; controller in the middle of the bottom layer', 'Куб 3×3×4, пустой внутри; контроллер — в середине нижнего слоя'),
        ('Layers 2-3: 8 heating coils each; bottom and top: heat proof casings or hatches', 'Слои 2–3: по 8 катушек; низ и верх — теплоустойчивые корпуса или люки'),
        ('Bottom: energy hatches, input/output buses, input hatch, maintenance hatch', 'Низ: энергетические люки, входные/выходные шины, входной люк, люк обслуживания'),
        ('Top: muffler hatch in the middle (output hatches allowed on the top ring)', 'Верх: выхлопной люк в центре (на кольце верха — выходные люки)'),
        ('Two LV energy hatches give MV recipes (sum of voltages)', 'Два энергетических люка LV дают рецепты MV (напряжения складываются)'),
        ('Causes 100 Pollution per second', 'Загрязнение: 100 в секунду')])
    shaped('multi/electric_blast_furnace', f'{NS}:electric_blast_furnace', 1, ['FFF', 'CMC', 'WCW'],
           {'F': 'minecraft:furnace', 'C': '#c:circuits/basic', 'M': f'{NS}:heat_proof_casing', 'W': '#iis:cables/1x/tin'})
    gui_src = os.path.join(GT, 'gui', 'multimachines', 'ElectricBlastFurnace.png')
    shutil.copy(gui_src, tex_out('gui/multi/electric_blast_furnace.png'))

    # --- soldering iron LV (GT_MetaGenerated_Tool_01.SOLDERING_IRON_LV: "LBf/Sd /P  ", 100000 EU, 32 EU/t) -------------
    II = os.path.join(GT, 'items')
    head = img(os.path.join(II, 'materialicons', 'METALLIC', 'toolHeadSoldering.png')) if os.path.exists(os.path.join(II, 'materialicons', 'METALLIC', 'toolHeadSoldering.png')) \
        else img(os.path.join(II, 'materialicons', 'DULL', 'toolHeadSoldering.png'))

    def tint(im, rgb):
        out = Image.new('RGBA', im.size)
        for y in range(im.height):
            for x in range(im.width):
                r, gg, b, a = im.getpixel((x, y))
                out.putpixel((x, y), (r * rgb[0] // 255, gg * rgb[1] // 255, b * rgb[2] // 255, a))
        return out
    iron = mat_by_name['Iron']['color']
    rubber = mat_by_name['Rubber']['color']
    icon = Image.new('RGBA', (16, 16))
    for layer, rgb in ((os.path.join(II, 'iconsets', 'HANDLE_SOLDERING.png'), rubber), (None, iron),
                       (os.path.join(II, 'iconsets', 'POWER_UNIT_LV.png'), 0xFFFFFF)):
        src = head.crop((0, 0, 16, 16)) if layer is None else img(layer).crop((0, 0, 16, 16))
        icon = Image.alpha_composite(icon, tint(src, ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255)))
    icon.save(tex_out('item/soldering_iron_lv.png'))
    write(os.path.join(A, 'models', 'item', 'soldering_iron_lv.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'{NS}:item/soldering_iron_lv'}})
    write(os.path.join(A, 'items', 'soldering_iron_lv.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/soldering_iron_lv'}})
    name(f'item.{NS}.soldering_iron_lv', 'Soldering Iron (LV)', 'Паяльник (LV)')
    name(f'item.{NS}.soldering_iron_lv.tooltip', 'Needs solder (tin, lead or soldering alloy: ingot, rod or fine wire) and 10000 EU per use',
         'Нужен припой (олово, свинец или паяльный сплав: слиток, стержень или тонкая проволока) и 10000 EU за использование')
    shaped('multi/soldering_iron_lv', f'{NS}:soldering_iron_lv', 1, ['LBf', 'Sd ', 'P  '],
           {'L': f'{NS}:battery_lv', 'B': tag_of('bolt', 'Iron'), 'S': tag_of('rod', 'Iron'), 'P': tag_of('plate', 'Rubber')})
    for n in ('Tin', 'Lead', 'SolderingAlloy'):
        for p in ('ingot', 'rod', 'wire_fine'):
            if ref(p, n):
                add_item_tag('iis:solder', tag_of(p, n))

    # --- assembler recipes (GT_MachineRecipeLoader:1380, 1381; frame 4 rods) ---------------------------------------
    rec = {}

    def mrec(rtype, rid, inputs, outputs, duration, eut, temp=0, fin=None, fout=None):
        outputs = [o for o in outputs if o and o['id']]
        if any(i['ingredient'] is None for i in inputs) or not outputs:
            return
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(duration), 'eut': int(eut)}
        if temp:
            r['temp'] = int(temp)
        if fin:
            r['fluid_inputs'] = [fin]
        if fout:
            r['fluid_outputs'] = [fout]
        rec.setdefault(f'{rtype}/{rid}', r)

    def ing(i, c=1):
        return {'ingredient': i, 'count': c}

    def out(i, c=1, chance=10000):
        if i is None:
            return None
        o = {'id': i, 'count': c}
        if chance < 10000:
            o['chance'] = chance
        return o
    mrec('assembler', 'heat_proof_casing', [ing(tag_of('plate', 'Invar'), 6), ing('#c:frames/invar')], [out(f'{NS}:heat_proof_casing', 2)], 50, 16)
    mrec('assembler', 'coil_cupronickel', [ing('#iis:wires/2x/cupronickel', 8)], [out(f'{NS}:coil_cupronickel')], 50, 16)
    for mid in frames:
        mrec('assembler', f'frame_{mid}', [ing(f'#c:rods/{mid}', 4)], [out(f'{NS}:frame_{mid}')], 64, 8)

    # --- blast recipes ------------------------------------------------------------------------------------------------
    for n in sorted(g['names']):
        if not prop(n, 'BlastFurnaceRequired', False):
            continue
        temp = prop(n, 'BlastFurnaceTemp', 0)
        into = prop(n, 'MaterialSmeltInto', n) or n
        ingot = ref('ingot', into)
        if not ingot or temp > 1750 or 'NO_SMELTING' in g['subtags'](n):   # >1750 gives hot ingots (vacuum freezer, HV)
            continue
        mid = mat_id(n)
        dm = max(mass(n) // 40, 1) * temp
        # ProcessingDust: dust 1 / small 4 / tiny 9 -> ingot, max(mass/40,1)*temp ticks, 120 EU/t
        for p, c in (('dust', 1), ('small_dust', 4), ('tiny_dust', 9)):
            if ref(p, n):
                mrec('blast', f'{p}_{mid}', [ing(tag_of(p, n), c)], [out(ingot)], dm, 120, temp)
        # ProcessingOreSmelting: crushed / purified / impure dust -> ingot, max(mass/4,1)*temp ticks
        om = max(mass(n) // 4, 1) * temp
        for p in ('crushed', 'purified', 'impure_dust'):
            if ref(p, n):
                mrec('blast', f'{p}_{mid}', [ing(tag_of(p, n))], [out(ingot)], om, 120, temp)
    # GT_MachineRecipeLoader:1034-1039: ruby / green sapphire / sapphire -> 3 aluminium nuggets (1200 K, 100 EU/t)
    for n, ash in (('Ruby', True), ('GreenSapphire', True), ('Sapphire', False)):
        for p, t in (('dust', 400), ('gem', 320)):
            if ref(p, n) and ref('nugget', 'Aluminium'):
                mrec('blast', f'aluminium_from_{p}_{mat_id(n)}', [ing(tag_of(p, n))],
                     [out(ref('nugget', 'Aluminium'), 3), out(ref('tiny_dust', 'DarkAsh')) if ash else None], t, 100, 1200)
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    stats['blast'] = sum(1 for k in rec if k.startswith('blast/'))

    # --- misc lang -----------------------------------------------------------------------------------------------------
    for k, en, ruu in (
            ('jei.iis.blast', 'Electric Blast Furnace', 'Электрическая доменная печь'),
            ('itemGroup.iis.multi', 'IIS 2.0: Multiblocks', 'ИИС 2.0: многоблочные машины'),
            ('message.iis.machine.enabled', 'Machine enabled', 'Машина включена'),
            ('message.iis.machine.disabled', 'Machine disabled (soft hammer to enable)', 'Машина выключена (включить киянкой)'),
            ('jei.iis.temp', 'Heat: %s K', 'Нагрев: %s K'),
            ('gui.iis.ebf.structure_ok', 'Structure: complete', 'Структура: собрана'),
            ('gui.iis.ebf.structure_bad', 'Structure: incomplete', 'Структура: не собрана'),
            ('gui.iis.ebf.problems', 'Problems: %s', 'Неисправностей: %s'),
            ('gui.iis.ebf.efficiency', 'Efficiency: %s%%', 'Эффективность: %s%%'),
            ('gui.iis.ebf.progress', 'Progress: %s / %s s', 'Ход: %s / %s с'),
            ('gui.iis.ebf.eut', 'Uses %s EU/t', 'Потребляет %s EU/t'),
            ('gui.iis.ebf.heat', 'Heat: %s K', 'Нагрев: %s K'),
            ('gui.iis.ebf.voltage', 'Max input: %s EU/t', 'Наибольший вход: %s EU/t'),
            ('gui.iis.ebf.disabled', 'Machine disabled', 'Машина выключена'),
            ('gui.iis.ebf.disabled_hint', 'Soft hammer to enable', 'Включить — киянкой'),
            ('gui.iis.ebf.muffler', 'Muffler is blocked', 'Выхлопной люк перекрыт'),
            ('message.iis.maintenance.fixed', 'Fixed: %s', 'Устранено: %s'),
            ('message.iis.maintenance.none', 'No problems', 'Неисправностей нет'),
            ('message.iis.maintenance.list', 'Problems: %s', 'Неисправности: %s'),
            ('message.iis.maintenance.no_solder', 'Not enough solder or charge', 'Нет припоя или заряда'),
            ('maintenance.iis.wrench', 'pipe loose (wrench)', 'ослаблена труба (ключ)'),
            ('maintenance.iis.screwdriver', 'screws loose (screwdriver)', 'ослаблены винты (отвёртка)'),
            ('maintenance.iis.soft_hammer', 'something stuck (soft hammer)', 'что-то заело (киянка)'),
            ('maintenance.iis.hard_hammer', 'platings dented (hammer)', 'помята обшивка (молот)'),
            ('maintenance.iis.soldering', 'circuitry burned (soldering iron)', 'сгорела схема (паяльник)'),
            ('maintenance.iis.crowbar', 'something blocks (crowbar)', 'что-то мешает (монтировка)')):
        name(k, en, ruu)
    return stats
