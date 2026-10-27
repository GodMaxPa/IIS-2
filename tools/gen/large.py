"""Large multiblock generators (D88): large boilers (GT_MetaTileEntity_LargeBoiler_*), called from gen_content.py.

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
]
# tier, casing, pipe, firebox, steam L/t, steam per coal (L), heat-up seconds (32 portions of 40 ticks for bronze, 42 of 30 for
# steel; GT's description says 500 / increase = 31.25 and 41.67 s, forgetting the boost), EN, material
BOILERS = [
    ('bronze', 'bronze_plated_bricks', 'bronze_pipe_casing', 'bronze_firebox_casing', 800, 32000, 64, 'Large Bronze Boiler', 'Bronze'),
    ('steel', 'solid_steel_casing', 'steel_pipe_casing', 'steel_firebox_casing', 1200, 36000, 63, 'Large Steel Boiler', 'Steel'),
]


def generate(g):
    import json
    write, A, D, NS, lang, REF = g['write'], g['A'], g['D'], g['NS'], g['lang'], g['REF']
    tag_of, shaped, block_tags = g['tag_of'], g['shaped'], g['block_tags']
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
    shaped('multi/solid_steel_casing', f'{NS}:solid_steel_casing', 2, ['PhP', 'PFP', 'PwP'], {'P': tag_of('plate', 'Steel'), 'F': '#c:frames/steel'})
    for m in ('Bronze', 'Steel'):
        mid = m.lower()
        # :75-76 pipe casings "PIP/IFI/PIP" (I — medium pipe), :87-88 fireboxes "PSP/SFS/PSP" (S — rod)
        shaped(f'multi/{mid}_pipe_casing', f'{NS}:{mid}_pipe_casing', 2, ['PIP', 'IFI', 'PIP'],
               {'P': tag_of('plate', m), 'F': f'#c:frames/{mid}', 'I': f'{NS}:pipe_medium_{mid}'})
        shaped(f'multi/{mid}_firebox_casing', f'{NS}:{mid}_firebox_casing', 2, ['PSP', 'SFS', 'PSP'],
               {'P': tag_of('plate', m), 'F': f'#c:frames/{mid}', 'S': tag_of('rod', m)})
    tooltip('bronze_firebox_casing', [('Bottom layer of the Large Bronze Boiler', 'Нижний слой большого бронзового котла')])
    tooltip('steel_firebox_casing', [('Bottom layer of the Large Steel Boiler', 'Нижний слой большого стального котла')])
    tooltip('bronze_pipe_casing', [('Middle column of the Large Bronze Boiler', 'Середина большого бронзового котла')])
    tooltip('steel_pipe_casing', [('Middle column of the Large Steel Boiler', 'Середина большого стального котла')])

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
        m_ru = 'бронзовые' if tier == 'bronze' else 'стальные'
        c_en, c_ru = ('Bronze Plated Bricks', 'кирпичи, обшитые бронзой') if tier == 'bronze' else ('Solid Steel Machine Casings', 'стальные корпуса механизма')
        tooltip(bid, [
            (f'Produces {per_coal} L of Steam with 1 Coal at {steam} L/t', f'{per_coal} л пара с 1 угля, {steam} л/т'),
            ('A programmed circuit in the controller throttles it (-50 L/t per config)', 'Программная схема в контроллере снижает выход (−50 л/т за единицу)'),
            ('3x5x3, controller in the middle of the front of the bottom layer', 'Форма 3×5×3, контроллер — в середине передней стороны нижнего слоя'),
            (f'Bottom: {mat} Firebox Casings (min 3) or maintenance, input hatch/bus, muffler', f'Низ: {m_ru} топки (не меньше 3) или люки: обслуживание, входные люк и шина, глушитель'),
            (f'Above: hollow {c_en} (min 24) or steam output hatches', f'Выше: полые {c_ru} (не меньше 24) или выходные люки для пара'),
            (f'Inside: 3 {mat} Pipe Casings', f'Внутри: 3 {m_ru} блока обсадных труб'),
            ('Distilled Water first, then Water; runs dry - explodes', 'Сначала дистиллированная вода, потом обычная; без воды — взрыв'),
            (f'Diesel fuels have 1/4 efficiency; heats up in {heat_s} s', f'Дизельное топливо — с 1/4 отдачи; разогрев {heat_s} с'),
            ('Causes up to 240 Pollution per second', 'Загрязнение: до 240 в секунду')])
    # GT_Loader_MetaTileEntities:1157 "WCW/CMC/WCW": bronze firebox, basic circuit, tin cable. The steel boiler (:1158) needs
    # an Advanced (HV) circuit — not in IIS 2 yet: its controller comes from the creative tab until HV (like the vacuum freezer).
    shaped('multi/large_boiler_bronze', f'{NS}:large_boiler_bronze', 1, ['WCW', 'CMC', 'WCW'],
           {'M': f'{NS}:bronze_firebox_casing', 'C': '#c:circuits/basic', 'W': '#iis:cables/1x/tin'})
    name(f'block.{NS}.large_boiler_steel.tooltip.9', 'Crafting arrives with the Advanced (HV) circuit', 'Крафт появится вместе со схемой HV')
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
    return stats
