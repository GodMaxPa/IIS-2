"""Steam age content generator (slice 3), called from gen_content.py with its globals.

Everything here follows GT5-II (IdealIndustrial/GT5Unofficial @ 00021e3) and the IIS 1.19.1 pack configs.
The original class or line is named next to each rule. Java code lists the same ids (ru.iis.machine.*).
"""
import json
import os
import shutil

from PIL import Image

# ----------------------------------------------------------------------------------------------------------------
# Catalogue (ids are referenced from Java: ru.iis.machine.SteamMachineType / BoilerType / PipeType / IISBlocks)
# ----------------------------------------------------------------------------------------------------------------
# id, GT overlay name, bricked hull, GT lang suffix, input slots, EN name, GT description index (bronze, steel)
MACHINES = [
    ('macerator',     'MACERATOR',     False, 'macerator',   1, 'Macerator',     (106, 107)),
    ('forge_hammer',  'HAMMER',        False, 'hammer',      1, 'Forge Hammer',  (112, 113)),
    ('compressor',    'COMPRESSOR',    False, 'compressor',  1, 'Compressor',    (115, 116)),
    ('extractor',     'EXTRACTOR',     False, 'extractor',   1, 'Extractor',     (109, 110)),
    ('alloy_smelter', 'ALLOY_SMELTER', True,  'alloysmelter', 2, 'Alloy Smelter', (118, 119)),
    ('furnace',       'FURNACE',       True,  'furnace',     1, 'Furnace',       (103, 104)),
]
TIERS = [('bronze', 'BRONZE', 'Steam ', 'bronzemachine'), ('steel', 'STEEL', 'High Pressure ', 'steelmachine')]
BOILERS = [
    # id, GT lang, desc index, EN, base, front, front active, top overlay, GUI
    ('boiler_coal_bronze', 'boiler.bronze', 100, 'Small Coal Boiler', 'BRONZEBRICKS', 'BOILER_FRONT', 'BOILER_FRONT_ACTIVE', 'OVERLAY_PIPE', 'BronzeBoiler'),
    ('boiler_coal_steel', 'boiler.steel', 101, 'High Pressure Coal Boiler', 'STEELBRICKS', 'BOILER_FRONT', 'BOILER_FRONT_ACTIVE', 'OVERLAY_PIPE', 'SteelBoiler'),
    ('boiler_lava_steel', 'boiler.lava', 102, 'High Pressure Lava Boiler', 'STEELBRICKS', 'BOILER_LAVA_FRONT', 'BOILER_LAVA_FRONT_ACTIVE', 'OVERLAY_PIPE', 'SteelBoiler'),
    ('boiler_solar_bronze', 'boiler.solar', 105, 'Simple Solar Boiler', 'BRONZEBRICKS', 'OVERLAY_PIPE', 'OVERLAY_PIPE', 'BOILER_SOLAR', 'SolarBoiler'),
]
HULLS = [('hull_bronze', 'BRONZE', 'hull.bronze', 'Bronze Hull'), ('hull_bronze_bricked', 'BRONZEBRICKS', 'hull.bronze_bricked', 'Bricked Bronze Hull'),
         ('hull_steel', 'STEEL', 'hull.steel', 'Steel Hull'), ('hull_steel_bricked', 'STEELBRICKS', 'hull.steel_bricked', 'Bricked Steel Hull')]
# GT_Loader_MetaTileEntities: GT_MetaPipeEntity_Fluid(capacity, heat resistance, gas proof); thickness from the same call
PIPES = [(mat, size, thick) for mat in ('Bronze', 'Steel') for size, thick in (('small', 6), ('medium', 8), ('large', 12))]
PIPE_STATS = {('Bronze', 'small'): (40, 2000), ('Bronze', 'medium'): (120, 2000), ('Bronze', 'large'): (240, 2000),
              ('Steel', 'small'): (80, 2500), ('Steel', 'medium'): (240, 2500), ('Steel', 'large'): (480, 2500)}
RU_PIPE_SIZE = {'small': '', 'medium': '', 'large': ''}

MACHINE_EN_DESC = {'macerator': 'Grinding your Ores', 'forge_hammer': 'Forge Hammer', 'compressor': 'Compressing Items',
                   'extractor': 'Extracting your first Rubber', 'alloy_smelter': 'Combination Smelter',
                   'furnace': 'Smelting things with compressed Steam'}
FACES = ('front', 'back', 'side', 'top', 'bottom')


def generate(g):
    write, A, D, NS, lang, TEX = g['write'], g['A'], g['D'], g['NS'], g['lang'], g['TEX']
    ref, tag_of, prop, subtags, mat_id = g['ref'], g['tag_of'], g['prop'], g['subtags'], g['mat_id']
    shaped, shapeless, smelting, add_recipe = g['shaped'], g['shapeless'], g['smelting'], g['add_recipe']
    add_item_tag, block_tags, ore_blocks, HOSTS = g['add_item_tag'], g['block_tags'], g['ore_blocks'], g['HOSTS']
    mat_by_name, names, NOT_GEN = g['mat_by_name'], g['names'], g['NOT_GENERATED_MATERIALS']
    ru_lang = json.load(open(os.path.join(g['REF'], 'gt5_lang_ru_machines.json'), encoding='utf-8'))
    GTB = os.path.join(TEX, 'gt_blocks')
    stats = {}

    def ru(key, fallback):
        v = ru_lang.get(key)
        return v.strip() if v else fallback

    def tex_out(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def load(name):
        im = Image.open(os.path.join(GTB, name + '.png')).convert('RGBA')
        meta = os.path.join(GTB, name + '.png.mcmeta')
        return im, (open(meta).read() if os.path.exists(meta) else None)

    def composite(rel, base, overlay=None):
        """base + overlay GT icons -> one texture (frames of animated overlays are composited one by one)."""
        b, _ = load(base)
        if overlay is None:
            b.save(tex_out(rel + '.png'))
            return
        o, meta = load(overlay)
        frames = o.height // o.width if o.height > o.width else 1
        out = Image.new('RGBA', (16, 16 * frames))
        for f in range(frames):
            frame = Image.alpha_composite(b.crop((0, 0, 16, 16)), o.crop((0, 16 * f, 16, 16 * f + 16)))
            out.paste(frame, (0, 16 * f))
        out.save(tex_out(rel + '.png'))
        if meta and frames > 1:
            with open(tex_out(rel + '.png.mcmeta'), 'w') as fh:
                fh.write(meta)

    def cube_model(model_id, faces):
        """faces: dict front/back/side/top/bottom -> texture id; front faces north."""
        write(os.path.join(A, 'models', 'block', model_id + '.json'), {
            'parent': 'minecraft:block/cube',
            'textures': {'particle': faces['side'], 'north': faces['front'], 'south': faces['back'], 'east': faces['side'],
                         'west': faces['side'], 'up': faces['top'], 'down': faces['bottom']}})

    def facing_blockstate(bid, has_active):
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for active in ((False, True) if has_active else (None,)):
                key = f'facing={facing}' + ('' if active is None else f',active={str(active).lower()}')
                model = f'{NS}:block/{bid}' + ('_active' if active else '')
                v = {'model': model}
                if y:
                    v['y'] = y
                variants[key] = v
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})

    def block_item(bid):
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})

    def simple_loot(bid):
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}]}]})

    def name(key, en, ru_text):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru_text

    def pickaxe(bid, level=1):
        block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')
        if level == 1:
            block_tags['minecraft:needs_stone_tool'].append(f'{NS}:{bid}')
        elif level == 2:
            block_tags['minecraft:needs_iron_tool'].append(f'{NS}:{bid}')

    def tooltip(bid, gt_index, en_lines):
        lines_ru = []
        i = 0
        while f'TileEntity_DESCRIPTION_{gt_index}_Index_{i}' in ru_lang:
            line = ru_lang[f'TileEntity_DESCRIPTION_{gt_index}_Index_{i}'].strip()
            if 'загрязнени' not in line:  # IIS 2.0 has no pollution (owner's decision, D27)
                lines_ru.append(line)
            i += 1
        for k, line in enumerate(en_lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', line, lines_ru[k] if k < len(lines_ru) else line)

    # --- steam fluid --------------------------------------------------------------------------------------------
    shutil.copy(os.path.join(TEX, 'fluids', 'fluid.steam.png'), tex_out('block/fluid/steam.png'))
    shutil.copy(os.path.join(TEX, 'fluids', 'fluid.steam.png.mcmeta'), tex_out('block/fluid/steam.png.mcmeta'))
    name(f'fluid_type.{NS}.steam', 'Steam', ru('fluid.steam', 'Пар'))

    # --- GUI textures -------------------------------------------------------------------------------------------
    for f in os.listdir(os.path.join(TEX, 'gui')):
        Image.open(os.path.join(TEX, 'gui', f)).convert('RGBA').save(tex_out('gui/' + f.lower()))

    # --- simple items -------------------------------------------------------------------------------------------
    for iid, en, ru_key in (('firebrick', 'Firebrick', 'gt.metaitem.01.32111.name'),
                            ('compressed_fireclay', 'Compressed Fireclay', 'gt.metaitem.01.32110.name')):
        shutil.copy(os.path.join(TEX, 'gt_items', iid + '.png'), tex_out(f'item/{iid}.png'))
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru(ru_key, en))

    # --- hulls and firebrick casing -----------------------------------------------------------------------------
    for bid, base, gt, en in HULLS:
        for face, src in (('side', 'SIDE'), ('top', 'TOP'), ('bottom', 'BOTTOM')):
            composite(f'block/machine/{bid}_{face}', f'MACHINE_{base}_{src}')
        t = {f: f'{NS}:block/machine/{bid}_{"side" if f in ("front", "back") else f}' for f in FACES}
        cube_model(bid, t)
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        block_item(bid)
        simple_loot(bid)
        pickaxe(bid)
        name(f'block.{NS}.{bid}', en, ru(f'gt.blockmachines.{gt}.name', en))
    composite('block/machine/firebrick_casing', 'MACHINE_CASING_DENSEBRICKS')
    write(os.path.join(A, 'models', 'block', 'firebrick_casing.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/machine/firebrick_casing'}})
    write(os.path.join(A, 'blockstates', 'firebrick_casing.json'), {'variants': {'': {'model': f'{NS}:block/firebrick_casing'}}})
    block_item('firebrick_casing')
    simple_loot('firebrick_casing')
    pickaxe('firebrick_casing')
    name(f'block.{NS}.firebrick_casing', 'Firebricks', ru('gt.blockcasings4.15.name', 'Огнеупорные кирпичи'))

    # --- steam machines -----------------------------------------------------------------------------------------
    machine_ids = []
    for mid, ov, bricked, gtname, inputs, en, desc in MACHINES:
        for ti, (tier, base0, en_prefix, gt_prefix) in enumerate(TIERS):
            bid = f'steam_{mid}_{tier}'
            machine_ids.append(bid)
            base = base0 + ('BRICKS' if bricked else '')
            for active in (False, True):
                suf = '_ACTIVE' if active else ''
                fs = '_active' if active else ''
                composite(f'block/machine/{bid}_front{fs}', f'MACHINE_{base}_SIDE', f'OVERLAY_FRONT_STEAM_{ov}{suf}')
                composite(f'block/machine/{bid}_side{fs}', f'MACHINE_{base}_SIDE', f'OVERLAY_SIDE_STEAM_{ov}{suf}')
                composite(f'block/machine/{bid}_top{fs}', f'MACHINE_{base}_TOP', f'OVERLAY_TOP_STEAM_{ov}{suf}')
                composite(f'block/machine/{bid}_bottom{fs}', f'MACHINE_{base}_BOTTOM', f'OVERLAY_BOTTOM_STEAM_{ov}{suf}')
                # GT5: after placement the output/vent facing is the back side; it shows OVERLAY_PIPE_OUT
                composite(f'block/machine/{bid}_back', f'MACHINE_{base}_SIDE', 'OVERLAY_PIPE_OUT')
                cube_model(bid + fs, {'front': f'{NS}:block/machine/{bid}_front{fs}', 'back': f'{NS}:block/machine/{bid}_back',
                                      'side': f'{NS}:block/machine/{bid}_side{fs}', 'top': f'{NS}:block/machine/{bid}_top{fs}',
                                      'bottom': f'{NS}:block/machine/{bid}_bottom{fs}'})
            facing_blockstate(bid, True)
            block_item(bid)
            simple_loot(bid)
            pickaxe(bid)
            name(f'block.{NS}.{bid}', en_prefix + en, ru(f'gt.blockmachines.{gt_prefix}.{gtname}.name', en_prefix + en))
            tooltip(bid, desc[ti], [MACHINE_EN_DESC[mid], ''])
            name(f'block.{NS}.{bid}.tooltip.1', 'Steam: 2 L per EU of the recipe; vents from the front side, keep it clear',
                 'Пар: 2 л на 1 EU рецепта; выпускает пар спереди — не загораживайте')
            add_item_tag('iis:steam_machines', f'{NS}:{bid}')
    stats['machines'] = len(machine_ids)

    # --- boilers ------------------------------------------------------------------------------------------------
    for bid, gt, desc, en, base, front, front_act, top_ov, gui in BOILERS:
        composite(f'block/machine/{bid}_side', f'MACHINE_{base}_SIDE')
        composite(f'block/machine/{bid}_bottom', f'MACHINE_{base}_BOTTOM')
        composite(f'block/machine/{bid}_top', f'MACHINE_{base}_TOP', top_ov)
        composite(f'block/machine/{bid}_front', f'MACHINE_{base}_SIDE', front)
        composite(f'block/machine/{bid}_front_active', f'MACHINE_{base}_SIDE', front_act)
        for fs in ('', '_active'):
            cube_model(bid + fs, {'front': f'{NS}:block/machine/{bid}_front{fs}', 'back': f'{NS}:block/machine/{bid}_side',
                                  'side': f'{NS}:block/machine/{bid}_side', 'top': f'{NS}:block/machine/{bid}_top',
                                  'bottom': f'{NS}:block/machine/{bid}_bottom'})
        facing_blockstate(bid, True)
        block_item(bid)
        simple_loot(bid)
        pickaxe(bid)
        name(f'block.{NS}.{bid}', en, ru(f'gt.blockmachines.{gt}.name', en))
        en_desc = {100: ['An early way to get Steam Power', 'Produces 120L of Steam per second'],
                   101: ['Faster than the Bronze Boiler', 'Produces 300L of Steam per second'],
                   102: ['A Boiler running off Lava', 'Produces 600L of Steam per second',
                         'Drops to 200L of Steam per second as byproduct slot fills'],
                   105: ['Steam Power by the Sun', 'Produces 120L of Steam per second',
                         'Calcifies over time, reducing Steam output to 40L/s', 'Break and replace to decalcify']}[desc]
        tooltip(bid, desc, en_desc)
        if desc == 102:  # the pack's line differs from GT's line; keep the meaning of the original
            name(f'block.{NS}.{bid}.tooltip.2', en_desc[2], 'Выход падает до 200 л/с по мере заполнения слота побочного продукта')
        if desc == 105:  # the pack's "Перерыв и замена" is a machine translation of "Break and replace"
            name(f'block.{NS}.{bid}.tooltip.3', en_desc[3], 'Сломайте и поставьте заново, чтобы убрать накипь')
        add_item_tag('iis:boilers', f'{NS}:{bid}')

    # --- bricked blast furnace ----------------------------------------------------------------------------------
    bid = 'bricked_blast_furnace'
    composite(f'block/machine/{bid}_front', 'MACHINE_CASING_BRICKEDBLASTFURNACE_INACTIVE')
    composite(f'block/machine/{bid}_front_active', 'MACHINE_CASING_BRICKEDBLASTFURNACE_ACTIVE')
    for fs in ('', '_active'):
        cube_model(bid + fs, {'front': f'{NS}:block/machine/{bid}_front{fs}', 'back': f'{NS}:block/machine/firebrick_casing',
                              'side': f'{NS}:block/machine/firebrick_casing', 'top': f'{NS}:block/machine/firebrick_casing',
                              'bottom': f'{NS}:block/machine/firebrick_casing'})
    facing_blockstate(bid, True)
    block_item(bid)
    simple_loot(bid)
    pickaxe(bid)
    name(f'block.{NS}.{bid}', 'Primitive Bricked Blast Furnace', ru('gt.blockmachines.multimachine.brickedblastfurnace.name', 'Примитивная кирпичная доменная печь'))
    tooltip(bid, 130, ['Controller Block for the Bricked Blast Furnace', 'Makes Steel and does basic Pyrometallurgy',
                       'Size(WxHxD): 3x4x3, Hollow, open top', 'Built from 32 Firebricks'])

    # --- pipes --------------------------------------------------------------------------------------------------
    pipe_ids = []
    for mat, size, thick in PIPES:
        m = mat_by_name[mat]
        iset = m['iconSet'] if os.path.isdir(os.path.join(TEX, 'pipes', m['iconSet'])) else 'METALLIC'
        pid = f'pipe_{size}_{mat_id(mat)}'
        pipe_ids.append(pid)
        side_tex = f'{NS}:block/pipe/{iset.lower()}_side'
        end_tex = f'{NS}:block/pipe/{iset.lower()}_{size}'
        shutil.copy(os.path.join(TEX, 'pipes', iset, 'pipeSide.png'), tex_out(f'block/pipe/{iset.lower()}_side.png'))
        shutil.copy(os.path.join(TEX, 'pipes', iset, f'pipe{size.capitalize()}.png'), tex_out(f'block/pipe/{iset.lower()}_{size}.png'))
        lo, hi = (16 - thick) / 2, (16 + thick) / 2
        allf = ('north', 'south', 'east', 'west', 'up', 'down')
        write(os.path.join(A, 'models', 'block', pid + '_core.json'), {
            'parent': 'minecraft:block/block', 'textures': {'particle': side_tex, 'side': side_tex, 'end': end_tex},
            'elements': [{'from': [lo, lo, lo], 'to': [hi, hi, hi],
                          'faces': {f: {'texture': '#side', 'tintindex': 0} for f in allf}}]})
        # arm towards north (z 0..lo); rotated in the blockstate for other directions
        write(os.path.join(A, 'models', 'block', pid + '_arm.json'), {
            'parent': 'minecraft:block/block', 'textures': {'particle': side_tex, 'side': side_tex, 'end': end_tex},
            'elements': [{'from': [lo, lo, 0], 'to': [hi, hi, lo],
                          'faces': {'north': {'texture': '#end', 'tintindex': 0, 'cullface': 'north'},
                                    'east': {'texture': '#side', 'tintindex': 0}, 'west': {'texture': '#side', 'tintindex': 0},
                                    'up': {'texture': '#side', 'tintindex': 0}, 'down': {'texture': '#side', 'tintindex': 0}}}]})
        rot = {'north': {}, 'south': {'y': 180}, 'east': {'y': 90}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
        parts = [{'apply': {'model': f'{NS}:block/{pid}_core'}}]
        for d, r in rot.items():
            parts.append({'when': {d: 'true'}, 'apply': dict({'model': f'{NS}:block/{pid}_arm'}, **r)})
        write(os.path.join(A, 'blockstates', pid + '.json'), {'multipart': parts})
        # item: core + north/south arms
        write(os.path.join(A, 'models', 'item', pid + '.json'), {
            'parent': 'minecraft:block/block', 'textures': {'particle': side_tex, 'side': side_tex, 'end': end_tex},
            'elements': [{'from': [lo, lo, 0], 'to': [hi, hi, 16],
                          'faces': {'north': {'texture': '#end', 'tintindex': 0}, 'south': {'texture': '#end', 'tintindex': 0},
                                    'east': {'texture': '#side', 'tintindex': 0}, 'west': {'texture': '#side', 'tintindex': 0},
                                    'up': {'texture': '#side', 'tintindex': 0}, 'down': {'texture': '#side', 'tintindex': 0}}}],
            'display': {'gui': {'rotation': [30, 45, 0], 'scale': [0.625, 0.625, 0.625]}}})
        write(os.path.join(A, 'items', pid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{pid}',
              'tints': [{'type': 'minecraft:constant', 'value': m['color']}]}})
        simple_loot(pid)
        pickaxe(pid)
        gt_size = {'small': '_small', 'medium': '', 'large': '_large'}[size]
        en = {'small': 'Small ', 'medium': '', 'large': 'Large '}[size] + f'{mat} Fluid Pipe'
        name(f'block.{NS}.{pid}', en, ru(f'gt.blockmachines.gt_pipe_{mat_id(mat)}{gt_size}.name', en))
        cap, heat = PIPE_STATS[(mat, size)]
        name(f'block.{NS}.{pid}.tooltip.0', f'Fluid Capacity: {cap * 20} L/sec', f'Пропускная способность: {cap * 20} л/с')
        name(f'block.{NS}.{pid}.tooltip.1', f'Heat Limit: {heat} K', f'Предел температуры: {heat} K')
        name(f'block.{NS}.{pid}.tooltip.2', 'Gas proof', 'Не пропускает газ')
        add_item_tag(f'iis:pipes/{size}/{mat_id(mat)}', f'{NS}:{pid}')
        add_item_tag('iis:pipes', f'{NS}:{pid}')
    stats['pipes'] = len(pipe_ids)

    # --- crafting recipes (GT_Loader_MetaTileEntities, ProcessingPipe) ------------------------------------------
    P_B, P_S = tag_of('plate', 'Bronze'), tag_of('plate', 'Steel')
    BRICKS, FURNACE, PISTON, ANVIL, GLASS = 'minecraft:bricks', 'minecraft:furnace', 'minecraft:piston', 'minecraft:anvil', 'minecraft:glass'
    shaped('machine/hull_bronze', f'{NS}:hull_bronze', 1, ['PPP', 'PhP', 'PPP'], {'P': P_B})
    shaped('machine/hull_bronze_bricked', f'{NS}:hull_bronze_bricked', 1, ['PPP', 'PhP', 'BBB'], {'P': P_B, 'B': BRICKS})
    shaped('machine/hull_steel', f'{NS}:hull_steel', 1, ['PPP', 'PhP', 'PPP'], {'P': P_S})
    shaped('machine/hull_steel_bricked', f'{NS}:hull_steel_bricked', 1, ['PPP', 'PhP', 'BBB'], {'P': P_S, 'B': BRICKS})
    shaped('machine/boiler_coal_bronze', f'{NS}:boiler_coal_bronze', 1, ['PPP', 'P P', 'BFB'], {'P': P_B, 'B': BRICKS, 'F': FURNACE})
    shaped('machine/boiler_coal_steel', f'{NS}:boiler_coal_steel', 1, ['PPP', 'P P', 'BFB'], {'P': P_S, 'B': BRICKS, 'F': FURNACE})
    shaped('machine/boiler_lava_steel', f'{NS}:boiler_lava_steel', 1, ['PPP', 'GGG', 'PMP'], {'P': P_S, 'G': GLASS, 'M': f'{NS}:hull_steel_bricked'})
    shaped('machine/boiler_solar_bronze', f'{NS}:boiler_solar_bronze', 1, ['GGG', 'SSS', 'PMP'],
           {'G': GLASS, 'S': tag_of('plate', 'Silver'), 'P': '#iis:pipes/small/bronze', 'M': f'{NS}:hull_bronze_bricked'})
    for tier, mat in (('bronze', 'Bronze'), ('steel', 'Steel')):
        X = f'#iis:pipes/small/{tier}'
        H, HB = f'{NS}:hull_{tier}', f'{NS}:hull_{tier}_bricked'
        shaped(f'machine/steam_furnace_{tier}', f'{NS}:steam_furnace_{tier}', 1, ['XXX', 'XMX', 'XFX'], {'M': HB, 'X': X, 'F': FURNACE})
        shaped(f'machine/steam_macerator_{tier}', f'{NS}:steam_macerator_{tier}', 1, ['DXD', 'XMX', 'PXP'], {'M': H, 'X': X, 'P': PISTON, 'D': 'minecraft:diamond'})
        shaped(f'machine/steam_extractor_{tier}', f'{NS}:steam_extractor_{tier}', 1, ['XXX', 'PMG', 'XXX'], {'M': H, 'X': X, 'P': PISTON, 'G': GLASS})
        shaped(f'machine/steam_forge_hammer_{tier}', f'{NS}:steam_forge_hammer_{tier}', 1, ['XPX', 'XMX', 'XAX'], {'M': H, 'X': X, 'P': PISTON, 'A': ANVIL})
        shaped(f'machine/steam_compressor_{tier}', f'{NS}:steam_compressor_{tier}', 1, ['XXX', 'PMP', 'XXX'], {'M': H, 'X': X, 'P': PISTON})
        shaped(f'machine/steam_alloy_smelter_{tier}', f'{NS}:steam_alloy_smelter_{tier}', 1, ['XXX', 'FMF', 'XXX'], {'M': HB, 'X': X, 'F': FURNACE})
    for mat, size, thick in PIPES:  # ProcessingPipe: small x6 "PwP/P P/PhP", medium x2 "PPP/w h/PPP", large x1 "PhP/P P/PwP"
        P = tag_of('plate', mat)
        pat, cnt = {'small': (['PwP', 'P P', 'PhP'], 6), 'medium': (['PPP', 'w h', 'PPP'], 2), 'large': (['PhP', 'P P', 'PwP'], 1)}[size]
        shaped(f'pipe/{size}_{mat_id(mat)}', f'{NS}:pipe_{size}_{mat_id(mat)}', cnt, pat, {'P': P})
    # Firebrick chain: GT_CraftingRecipeLoader:487, GT_MachineRecipeLoader:957-959, GT_Loader_MetaTileEntities:125/446
    shapeless('dust/fireclay', ref('dust', 'Fireclay'), 2, [tag_of('dust', 'Brick'), tag_of('dust', 'Clay')])
    smelting('smelting/firebrick', f'{NS}:compressed_fireclay', f'{NS}:firebrick', 1, xp=0.0)
    shaped('machine/firebrick_casing', f'{NS}:firebrick_casing', 2, ['BB', 'BB'], {'B': f'{NS}:firebrick'})
    shaped('machine/bricked_blast_furnace', f'{NS}:bricked_blast_furnace', 1, ['BBB', 'BPB', 'BBB'],
           {'B': f'{NS}:firebrick', 'P': '#c:plates/iron'})

    # --- machine recipes ----------------------------------------------------------------------------------------
    machine_recipes = {}

    def ing(item, count=1):
        return {'ingredient': item, 'count': count}

    def out(item, count=1, chance=10000):
        o = {'id': item, 'count': count}
        if chance < 10000:
            o['chance'] = chance
        return o

    def mrec(rtype, rid, inputs, outputs, duration, eut):
        outputs = [o for o in outputs if o['id']]
        if not outputs or any(i['ingredient'] is None for i in inputs):
            return
        key = f'{rtype}/{rid}'
        if key in machine_recipes:
            return  # first registration wins, as in GT recipe maps
        machine_recipes[key] = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(duration), 'eut': int(eut)}

    STONE_DUST = {'stone': 'Stone', 'deepslate': 'Stone', 'netherrack': 'Netherrack', 'end_stone': 'Endstone'}
    for n in sorted(names):
        if n in NOT_GEN:
            continue
        mid = mat_id(n)
        st = subtags(n)
        bys = prop(n, 'ListMaterialByProducts', []) or []
        ore_mult = max(1, prop(n, 'OreMultiplier', 1))
        by_mult = max(1, prop(n, 'OreByProductMultiplier', 1) or 1)
        crushed, impure, pure, dust = ref('crushed', n), ref('impure_dust', n), ref('pure_dust', n), ref('dust', n)
        gem = ref('gem', n)

        def by_dust(i):  # GT_Utility.selectItemInList(i, mMacerateInto, byproducts)
            lst = bys if bys else [n]
            return ref('dust', lst[min(i, len(lst) - 1)])
        # ProcessingOre: pulverize ore -> 2x crushed (x2 rich) + primary byproduct + host rock dust 50%; forge hammer -> gem or crushed
        for bid, on, host in ore_blocks:
            if on != n or crushed is None:
                continue
            mult = 2 if HOSTS[host]['rich'] else 1
            cnt = ore_mult * mult
            prim = ref('dust', bys[0]) if bys else dust
            mrec('macerator', bid, [ing(f'{NS}:{bid}')],
                 [out(crushed, 2 * cnt), out(prim, 1, min(10000, 100 * 10 * mult * by_mult)), out(ref('dust', STONE_DUST[host]), 1, 5000)], 400, 2)
            mrec('forge_hammer', bid, [ing(f'{NS}:{bid}')], [out(gem or crushed, cnt)], 16, 10)
        # ProcessingDirty / ProcessingPure / ProcessingCrushedOre
        if crushed and impure:
            mrec('macerator', f'crushed_{mid}', [ing(tag_of('crushed', n))], [out(impure), out(by_dust(0), 1, 1000)], 400, 2)
            mrec('forge_hammer', f'crushed_{mid}', [ing(tag_of('crushed', n))], [out(impure)], 10, 16)
        pur = ref('purified', n)
        if pur and pure:
            mrec('macerator', f'purified_{mid}', [ing(tag_of('purified', n))], [out(pure), out(by_dust(1), 1, 1000)], 400, 2)
            mrec('forge_hammer', f'purified_{mid}', [ing(tag_of('purified', n))], [out(pure)], 10, 16)
        # Recycling into dust (GT_RecipeRegistrator: macerating any material item gives its material amount as dust)
        if dust:
            for p, cnt_out in (('ingot', ('dust', 1)), ('plate', ('dust', 1)), ('gem', ('dust', 1)), ('nugget', ('tiny_dust', 1)),
                               ('rod', ('small_dust', 2)), ('ring', ('small_dust', 1)), ('bolt', ('tiny_dust', 1)), ('screw', ('tiny_dust', 1))):
                src = ref(p, n)
                o = ref(cnt_out[0], n)
                if src and o and 'NO_RECYCLING' not in st:
                    mrec('macerator', f'{p}_{mid}', [ing(tag_of(p, n))], [out(o, cnt_out[1])], 400, 2)
        # ProcessingIngot:57 forge hammer 3 ingots -> 2 plates (duration = material mass)
        if ref('ingot', n) and ref('plate', n) and 'NO_SMASHING' not in st:
            mrec('forge_hammer', f'plate_{mid}', [ing(tag_of('ingot', n), 3)], [out(ref('plate', n), 2)], max(1, g['mass'](n)), 16)
        # ProcessingDust:53/ProcessingBlock:64 compressor 9 -> storage block (only where a block exists: vanilla blocks)
    for n, block, prefix, cnt in (('Iron', 'minecraft:iron_block', 'ingot', 9), ('Gold', 'minecraft:gold_block', 'ingot', 9),
                                  ('Copper', 'minecraft:copper_block', 'ingot', 9), ('Iron', 'minecraft:iron_block', 'dust', 9),
                                  ('Gold', 'minecraft:gold_block', 'dust', 9), ('Copper', 'minecraft:copper_block', 'dust', 9),
                                  ('Redstone', 'minecraft:redstone_block', 'dust', 9), ('Coal', 'minecraft:coal_block', 'gem', 9),
                                  ('Diamond', 'minecraft:diamond_block', 'gem', 9), ('Emerald', 'minecraft:emerald_block', 'gem', 9),
                                  ('Lapis', 'minecraft:lapis_block', 'gem', 9), ('NetherQuartz', 'minecraft:quartz_block', 'gem', 4)):
        if ref(prefix, n):
            mrec('compressor', f'{prefix}_{mat_id(n)}_block', [ing(tag_of(prefix, n), cnt)], [out(block)], 300, 2)
    mrec('compressor', 'compressed_fireclay', [ing(tag_of('dust', 'Fireclay'))], [out(f'{NS}:compressed_fireclay')], 300, 2)
    mrec('compressor', 'packed_ice', [ing('minecraft:ice', 2)], [out('minecraft:packed_ice')], 300, 2)
    mrec('compressor', 'glowstone', [ing('minecraft:glowstone_dust', 4)], [out('minecraft:glowstone')], 300, 2)
    # GT_MachineRecipeLoader:883-884 extractor
    mrec('extractor', 'bookshelf', [ing('minecraft:bookshelf')], [out('minecraft:book', 3)], 300, 2)
    mrec('extractor', 'slime_ball', [ing('minecraft:slime_ball')], [out(ref('dust', 'RawRubber'), 2)], 300, 2)
    # Vanilla blocks (GT_MachineRecipeLoader forge hammer / pulverisation lines, GT_Loader_ItemData material amounts)
    for rid, i, o, c in (('stone', 'minecraft:stone', 'minecraft:cobblestone', 1), ('cobblestone', 'minecraft:cobblestone', 'minecraft:gravel', 1),
                         ('gravel', 'minecraft:gravel', 'minecraft:sand', 1), ('sandstone', 'minecraft:sandstone', 'minecraft:sand', 1),
                         ('bricks', 'minecraft:bricks', 'minecraft:brick', 3), ('terracotta', 'minecraft:terracotta', ref('dust', 'Clay'), 1),
                         ('brick', 'minecraft:brick', ref('small_dust', 'Brick'), 1), ('firebrick', f'{NS}:firebrick', ref('dust', 'Brick'), 1),
                         ('firebrick_casing', f'{NS}:firebrick_casing', f'{NS}:firebrick', 2), ('stone_bricks', 'minecraft:stone_bricks', 'minecraft:cracked_stone_bricks', 1)):
        mrec('forge_hammer', rid, [ing(i)], [out(o, c)], 10, 16)
    for rid, i, o, c in (('cobblestone', 'minecraft:cobblestone', 'minecraft:sand', 1), ('stone', 'minecraft:stone', 'minecraft:cobblestone', 1),
                         ('gravel', 'minecraft:gravel', ref('dust', 'Stone'), 1), ('furnace', 'minecraft:furnace', 'minecraft:sand', 6),
                         ('clay_ball', 'minecraft:clay_ball', ref('small_dust', 'Clay'), 2), ('clay', 'minecraft:clay', ref('dust', 'Clay'), 2),
                         ('brick', 'minecraft:brick', ref('small_dust', 'Brick'), 1), ('bricks', 'minecraft:bricks', ref('dust', 'Brick'), 1),
                         ('firebrick', f'{NS}:firebrick', ref('dust', 'Brick'), 1), ('firebrick_casing', f'{NS}:firebrick_casing', ref('dust', 'Brick'), 2),
                         ('compressed_fireclay', f'{NS}:compressed_fireclay', ref('small_dust', 'Fireclay'), 1),
                         ('netherrack', 'minecraft:netherrack', ref('dust', 'Netherrack'), 1), ('end_stone', 'minecraft:end_stone', ref('dust', 'Endstone'), 1),
                         ('coal', 'minecraft:coal', ref('dust', 'Coal'), 1), ('charcoal', 'minecraft:charcoal', ref('dust', 'Charcoal'), 1),
                         ('flint', 'minecraft:flint', ref('tiny_dust', 'Flint'), 4), ('blaze_rod', 'minecraft:blaze_rod', 'minecraft:blaze_powder', 3)):
        mrec('macerator', 'vanilla_' + rid, [ing(i)], [out(o, c)], 400, 2)
    # GT_MachineRecipeLoader:32-52 + :1559 alloy smelter (dust/ingot combinations, duration = amount * 50, 16 EU/t)
    ALLOYS = [('Tetrahedrite', 3, 'Tin', 1, 'Bronze', 3), ('Tetrahedrite', 3, 'Zinc', 1, 'Brass', 3), ('Copper', 3, 'Tin', 1, 'Bronze', 4),
              ('Copper', 3, 'Zinc', 1, 'Brass', 4), ('Copper', 1, 'Nickel', 1, 'Cupronickel', 2), ('Copper', 1, 'Redstone', 4, 'RedAlloy', 1),
              ('Iron', 1, 'Tin', 1, 'TinAlloy', 2), ('Iron', 2, 'Nickel', 1, 'Invar', 3), ('Gold', 1, 'Silver', 1, 'Electrum', 2)]
    for a, an, b, bn, o, on in ALLOYS:
        out_ingot = ref('ingot', o)
        if not out_ingot:
            continue
        for pa in ('ingot', 'dust'):
            for pb in ('ingot', 'dust'):
                if ref(pa, a) and ref(pb, b):
                    mrec('alloy_smelter', f'{mat_id(o)}_{pa}_{mat_id(a)}_{pb}_{mat_id(b)}',
                         [ing(tag_of(pa, a), an), ing(tag_of(pb, b), bn)], [out(out_ingot, on)], on * 50, 16)
    # Primitive (bricked) blast furnace: GT_RecipeAdder.addPrimitiveBlastRecipe (+ coal/charcoal gem and dust fuels)
    FUELS = [(ref('gem', 'Coal'), 'coal_gem'), (tag_of('dust', 'Coal'), 'coal_dust'), (ref('gem', 'Charcoal'), 'charcoal_gem'),
             (tag_of('dust', 'Charcoal'), 'charcoal_dust')]
    DARK_ASH_TINY, DARK_ASH = ref('tiny_dust', 'DarkAsh'), ref('dust', 'DarkAsh')

    def pbf(rid, in1, in2, coal, out1, out2, duration):
        for fuel, fid in FUELS:
            ins = [i for i in (in1, in2) if i] + [ing(fuel, coal)]
            outs = [o for o in (out1, out2) if o] + [out(DARK_ASH_TINY, coal)]
            mrec('primitive_blast', f'{rid}_{fid}', ins, outs, duration, 0)
        # x10 batch with coal blocks when every stack stays <= 6 (charcoal has no block in vanilla)
        if all(x is None or x['count'] <= 6 for x in (in1, in2, out1, out2)):
            ins = [dict(i, count=i['count'] * 10) for i in (in1, in2) if i] + [ing('minecraft:coal_block', coal)]
            outs = [dict(o, count=o['count'] * 10) for o in (out1, out2) if o] + [out(DARK_ASH, coal)]
            mrec('primitive_blast', f'{rid}_coal_block', ins, outs, duration * 10, 0)
    STEEL = ref('ingot', 'Steel')
    pbf('steel_from_iron_ingot', ing(tag_of('ingot', 'Iron')), None, 4, out(STEEL), None, 7200)   # GT_MachineRecipeLoader:3709
    pbf('steel_from_iron_dust', ing(tag_of('dust', 'Iron')), None, 4, out(STEEL), None, 7200)     # :3710
    pbf('steel_from_iron_block', ing('minecraft:iron_block'), None, 36, out(STEEL, 9), None, 64800)  # :3711
    pbf('steel_from_steel_dust', ing(tag_of('dust', 'Steel')), None, 2, out(STEEL), None, 7200)   # :3712
    # ProcessingOreSmelting:48-61: mixed ore dusts (direct smelting into another metal) -> 2 dust + 2 coal = 2 ingots (IIS: 2/3 yield)
    for n in sorted(names):
        ds = prop(n, 'MaterialDirectSmeltInto', n)
        if n in NOT_GEN or ds == n or not ref('dust', n) or ref('ingot', ds) is None:
            continue
        if 'NO_SMELTING' in subtags(n) or prop(n, 'BlastFurnaceRequired', False) or prop(ds, 'BlastFurnaceRequired', False):
            continue
        two = ing(tag_of('dust', n), 2)
        res = out(ref('ingot', ds), 2)
        if 'DONT_ADD_DEFAULT_BBF_RECIPE' not in subtags(n):
            pbf(f'{mat_id(n)}_dust', two, None, 2, res, None, 2400)
        elif n == 'Chalcopyrite':
            pbf('chalcopyrite_dust_sand', two, ing('minecraft:sand', 2), 2, res, out(ref('small_dust', 'Ferrosilite'), 4), 2400)
            pbf('chalcopyrite_dust_glass', two, ing(tag_of('dust', 'Glass'), 2) if ref('dust', 'Glass') else None, 2, res,
                out(ref('tiny_dust', 'Ferrosilite'), 14), 2400) if ref('dust', 'Glass') else None
        elif n == 'Tetrahedrite':  # antimony nuggets come with the antimony material (later); copper only for now
            pbf('tetrahedrite_dust', two, None, 2, res, None, 2400)
        elif n == 'Galena':
            pbf('galena_dust', two, None, 2, res, out(ref('nugget', 'Silver'), 6), 2400)
    for key, r in machine_recipes.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    stats['machine_recipes'] = len(machine_recipes)
    by_type = {}
    for r in machine_recipes.values():
        by_type[r['type']] = by_type.get(r['type'], 0) + 1
    stats.update(by_type)

    # creative tab / misc lang
    name('itemGroup.iis.machines', 'IIS 2.0: Machines', 'ИИС 2.0: Машины')
    for k, en, ruu in (('gui.iis.steam', 'Steam', 'Пар'), ('gui.iis.water', 'Water', 'Вода'), ('gui.iis.temperature', 'Temperature', 'Температура'),
                       ('gui.iis.fuel', 'Fuel', 'Топливо'), ('gui.iis.steam_blocked', 'Steam vent is blocked: clear the front side',
                                                                'Выход пара перекрыт: освободите переднюю сторону'),
                       ('gui.iis.no_steam', 'Not enough steam', 'Недостаточно пара'), ('gui.iis.boiler', 'Boiler', 'Котёл'),
                       ('gui.iis.amount', '%s / %s L', '%s / %s л'), ('gui.iis.heat', '%s / %s °C', '%s / %s °C'),
                       ('gui.iis.structure_incomplete', 'Structure incomplete', 'Структура не достроена'),
                       ('jei.iis.macerator', 'Macerator', 'Дробитель'), ('jei.iis.forge_hammer', 'Forge Hammer', 'Кузнечный молот'),
                       ('jei.iis.compressor', 'Compressor', 'Компрессор'), ('jei.iis.extractor', 'Extractor', 'Экстрактор'),
                       ('jei.iis.alloy_smelter', 'Alloy Smelter', 'Печь сплавов'), ('jei.iis.primitive_blast', 'Bricked Blast Furnace', 'Кирпичная доменная печь'),
                       ('jei.iis.eut', '%s EU/t (steam: %s L/t)', '%s EU/t (пар: %s л/т)'), ('jei.iis.duration', '%s s', '%s с'),
                       ('jei.iis.chance', 'Chance: %s%%', 'Шанс: %s%%'), ('jei.iis.steam_only_first', 'Steam machines give only the first output',
                                                                         'Паровые машины выдают только первый продукт'),
                       ('message.iis.pipe.connected', 'Connected', 'Подключено'), ('message.iis.pipe.disconnected', 'Disconnected', 'Отключено')):
        name(k, en, ruu)
    return stats
