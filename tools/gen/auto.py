"""Automation, quantum storage, basic drillers, adjustable transformers, chargers, storage blocks and field generators (D108).

Sources: GT_MetaTileEntity_Buffer and heirs, _DigitalChestBase, _QuantumTank, _Miner, _Pump, _Digital_Transformer, _Charger, GT_Block_Metal,
ProcessingBlock, GT_Loader_MetaTileEntities (crafting), GT_MachineRecipeLoader:409-413, 674, 1190-1191, 2893-2902. Block list:
reference/original/gt5_storage_blocks.json (extracted from GT_Loader_Item_Block_And_Fluid).
"""
import json
import os
import shutil

from PIL import Image

NS = 'iis'
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')
TIERS = ['lv', 'mv', 'hv', 'ev', 'iv']
AUTO_TIERS = TIERS + ['luv', 'zpm', 'uv']   # D110, D112, D121: automation and repellents go up to UV, as in GT


def generate(g):
    write, A, D, lang, REF, RES = g['write'], g['A'], g['D'], g['lang'], g['REF'], g['RES']
    tag_of, shaped, block_tags, mat_by_name, mat_id, mass = g['tag_of'], g['shaped'], g['block_tags'], g['mat_by_name'], g['mat_id'], g['mass']
    add_item_tag = g['add_item_tag']
    ru_lang = json.load(open(os.path.join(REF, 'gt5_lang_ru_machines.json'), encoding='utf-8'))
    BI = os.path.join(GT, 'blocks', 'iconsets')
    rec = {}
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

    def casing(tier, part):
        return first(os.path.join(BI, f'MACHINE_{ {"ulv": "8V", "luv": "LuV"}.get(tier, tier.upper()) }_{part}.png'))

    def over(base, *names):
        im = base.copy()
        for n in names:
            im = Image.alpha_composite(im, first(os.path.join(BI, n + '.png')))
        return im

    def loot(bid, components=None):
        entry = {'type': 'minecraft:item', 'name': f'{NS}:{bid}'}
        if components:
            entry['functions'] = [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': components}]
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [entry]}]})
        block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')

    def machine(bid, faces, active_faces=None, components=None):
        """faces: north (front) south east west up down -> image; a machine block with facing and active states."""
        for suf, fc in (('', faces), ('_active', active_faces or faces)):
            tx = {}
            for face, im in fc.items():
                rel = f'block/auto/{bid}{suf}_{face}'
                im.save(tex_out(rel + '.png'))
                tx[face] = f'{NS}:{rel}'
            tx['particle'] = tx['east']
            write(os.path.join(A, 'models', 'block', bid + suf + '.json'), {'parent': 'minecraft:block/cube', 'textures': tx})
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for active in (False, True):
                v = {'model': f'{NS}:block/{bid}' + ('_active' if active else '')}
                if y:
                    v['y'] = y
                variants[f'facing={facing},active={str(active).lower()}'] = v
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        loot(bid, components)

    def tooltip(bid, lines):
        for k, (en, ruu) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', en, ruu)

    def ing(i, c=1):
        return {'ingredient': i, 'count': c}

    def out(i, c=1):
        return {'id': i, 'count': c}

    def mrec(rtype, rid, inputs, outputs, duration, eut, fin=None, circuit=None, keep=None):
        if any(i['ingredient'] is None for i in inputs) or any(not o['id'] for o in outputs):
            return
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(duration), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if circuit is not None:
            r['circuit'] = circuit
        if keep:
            r['keep'] = [keep]
        rec.setdefault(f'{rtype}/{rid}', r)

    CIRC = {'lv': 'basic', 'mv': 'good', 'hv': 'advanced', 'ev': 'data', 'iv': 'elite', 'luv': 'master', 'zpm': 'ultimate', 'uv': 'superconductor'}
    CABLE = {'lv': 'Tin', 'mv': 'Copper', 'hv': 'Gold', 'ev': 'Aluminium', 'iv': 'Tungsten'}

    # --- automation ---------------------------------------------------------------------------------------------------
    KINDS = [('chest_buffer', 'AUTOMATION_CHESTBUFFER', 'ChestBuffer', 'automation.chestbuffer', 'Chest Buffer', 'Сундук-буфер', [
                 ('Buffers up to 27 item stacks and pushes them out of the back', 'Хранит до 27 стопок и выталкивает их назад'),
                 ('Screwdriver at the back: output stack size; 1 EU per moved item', 'Отвёртка сзади: размер порции; 1 EU за предмет')]),
             ('super_buffer', 'AUTOMATION_SUPERBUFFER', 'SuperBuffer', 'automation.superbuffer', 'Super Buffer', 'Сверхбуфер', [
                 ('Buffers up to 256 item stacks; pipes only', 'Хранит до 256 стопок; только через трубы'),
                 ('Screwdriver at the back: output stack size; 1 EU per moved item', 'Отвёртка сзади: размер порции; 1 EU за предмет')]),
             ('item_filter', 'AUTOMATION_FILTER', 'Filter', 'automation.filter', 'Item Filter', 'Фильтр предметов', [
                 ('Lets in only the sample items (up to 9 kinds), or everything else when inverted', 'Пропускает только образцы (до 9 видов) или, с инверсией, всё остальное'),
                 ('Screwdriver at the back: output stack size; 1 EU per moved item', 'Отвёртка сзади: размер порции; 1 EU за предмет')]),
             ('type_filter', 'AUTOMATION_TYPEFILTER', 'TypeFilter', 'automation.typefilter', 'Type Filter', 'Фильтр по типу', [
                 ('Lets in one kind of items: ores, dusts, ingots...', 'Пропускает один вид предметов: руды, пыли, слитки…'),
                 ('Click the label in the window to change the kind', 'Щелчок по подписи в окне меняет вид')]),
             ('regulator', 'AUTOMATION_REGULATOR', 'Regulator', 'automation.regulator', 'Item Regulator', 'Регулятор предметов', [
                 ('Sample: what and how many at once; number: target slot', 'Образец — что и сколько за раз, число — слот цели'),
                 ('3 EU per moved item', '3 EU за предмет')]),
             ('item_distributor', 'AUTOMATION_ITEMDISTRIBUTOR', 'ItemDistributor', 'automation.itemdistributor', 'Item Distributor', 'Распределитель предметов', [
                 ('Items in at the front, out by turns through sides with a set amount', 'Вход спереди, выход по очереди в стороны с заданным числом'),
                 ('Screwdriver on a side: items per side; 1 EU per moved item', 'Отвёртка по стороне: предметов на сторону; 1 EU за предмет')])]
    for kid, icon, gui, key, en, ruu, lines in KINDS:
        shutil.copy(os.path.join(GT, 'gui', gui + '.png'), tex_out(f'gui/auto/{kid}.png'))
        for i, t in enumerate(AUTO_TIERS):
            bid = f'{kid}_{t}'
            s, top, bot = casing(t, 'SIDE'), casing(t, 'TOP'), casing(t, 'BOTTOM')
            if kid == 'item_distributor':
                faces = {'north': over(s, icon), 'south': over(s, 'OVERLAY_PIPE_OUT', icon), 'east': over(s, 'OVERLAY_PIPE_OUT', icon), 'west': over(s, 'OVERLAY_PIPE_OUT', icon),
                         'up': over(top, 'OVERLAY_PIPE_OUT', icon), 'down': over(bot, 'OVERLAY_PIPE_OUT', icon)}
            else:
                faces = {'north': over(s, icon), 'south': over(s, 'OVERLAY_PIPE_OUT'), 'east': over(s, 'ARROW_LEFT', icon), 'west': over(s, 'ARROW_RIGHT', icon),
                         'up': over(top, 'ARROW_DOWN', icon), 'down': over(bot, 'ARROW_DOWN', icon)}
            machine(bid, faces)
            v = 32 << (2 * i)
            name(f'block.{NS}.{bid}', f'{t.upper()} {en}', ru(f'gt.blockmachines.{key}.tier.0{i + 1}.name', f'{ruu} {t.upper()}'))
            tooltip(bid, lines + [(f'Voltage IN: {v} EU/t, 2 A', f'Входное напряжение: {v} EU/t, 2 А')])
    # GT_Loader_MetaTileEntities:1643-1700 "CMV/ X " chest buffer, " F /CMV/ X " filters; :1709-1713 regulator; :1764-1768 distributor.
    # The super buffer (:1731-1746) needs a data orb or data sticks, which are not ported: no recipe yet
    for t in AUTO_TIERS:
        hull, conv, circ = f'{NS}:hull_{t}', f'{NS}:conveyor_module_{t}', '#c:circuits/basic'
        shaped(f'auto/chest_buffer_{t}', f'{NS}:chest_buffer_{t}', 1, ['CMV', ' X '], {'M': hull, 'V': conv, 'C': '#c:chests/wooden', 'X': circ})
        shaped(f'auto/item_filter_{t}', f'{NS}:item_filter_{t}', 1, [' F ', 'CMV', ' X '], {'M': hull, 'V': conv, 'C': '#c:chests/wooden', 'F': f'{NS}:item_filter', 'X': circ})
        shaped(f'auto/type_filter_{t}', f'{NS}:type_filter_{t}', 1, [' F ', 'CMV', ' X '],
               {'M': hull, 'V': conv, 'C': '#c:chests/wooden', 'F': f'{NS}:item_filter', 'X': '#c:circuits/advanced'})
        shaped(f'auto/regulator_{t}', f'{NS}:regulator_{t}', 1, ['XFX', 'VMV', 'XCX'],
               {'M': hull, 'V': f'{NS}:robot_arm_{t}', 'C': '#c:chests/wooden', 'F': f'{NS}:item_filter', 'X': '#c:circuits/advanced'})
        shaped(f'auto/item_distributor_{t}', f'{NS}:item_distributor_{t}', 1, ['XCX', 'VMV', ' V '],
               {'M': hull, 'V': conv, 'C': '#c:chests/wooden', 'X': '#c:circuits/good'})

    # --- quantum chests and tanks: :332-348 ---------------------------------------------------------------------------
    for i, t in enumerate(TIERS):
        s, top, bot = casing(t, 'SIDE'), casing(t, 'TOP'), casing(t, 'BOTTOM')
        bid = f'quantum_chest_{t}'
        machine(bid, {'north': over(s, 'OVERLAY_QCHEST'), 'south': s, 'east': s, 'west': s, 'up': top, 'down': bot}, components=[f'{NS}:quantum_content'])
        cap = 4000000 << i
        name(f'block.{NS}.{bid}', f'Quantum Chest {["I", "II", "III", "IV", "V"][i]}', ru(f'gt.blockmachines.quantum.chest.tier.0{i + 1}.name', f'Квантовый сундук {["I", "II", "III", "IV", "V"][i]}'))
        tooltip(bid, [(f'Stores {cap} items of one kind', f'Хранит {cap} предметов одного вида'), ('Keeps its contents when broken', 'При поломке содержимое остаётся в предмете')])
        # EV and IV need Master and Ultimate circuits, europium and americium plates: no recipe yet
        fg, frame = f'{NS}:field_generator_{t}', f'{NS}:hull_{t}'
        if i < 3:
            pl, circ = tag_of('plate', ['StainlessSteel', 'Titanium', 'TungstenSteel'][i]), ['#c:circuits/advanced', '#c:circuits/data', '#c:circuits/elite'][i]
            shaped(f'auto/{bid}', f'{NS}:{bid}', 1, ['DPD', 'PMP', 'DGD'], {'M': frame, 'G': fg, 'D': circ, 'P': pl})
        tid = f'quantum_tank_{t}'
        faces = {'north': s, 'south': s, 'east': s, 'west': s, 'up': over(top, 'OVERLAY_QTANK'), 'down': bot}
        tx = {}
        for face, im in faces.items():
            rel = f'block/auto/{tid}_{face}'
            im.save(tex_out(rel + '.png'))
            tx[face] = f'{NS}:{rel}'
        tx['particle'] = tx['east']
        write(os.path.join(A, 'models', 'block', tid + '.json'), {'parent': 'minecraft:block/cube', 'textures': tx})
        write(os.path.join(A, 'blockstates', tid + '.json'), {'variants': {'': {'model': f'{NS}:block/{tid}'}}})
        write(os.path.join(A, 'items', tid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{tid}'}})
        loot(tid, [f'{NS}:fluid_content'])
        name(f'block.{NS}.{tid}', f'Quantum Tank {["I", "II", "III", "IV", "V"][i]}', ru(f'gt.blockmachines.quantum.tank.tier.0{i + 1}.name', f'Квантовый резервуар {["I", "II", "III", "IV", "V"][i]}'))
        if i < 3:
            shaped(f'auto/{tid}', f'{NS}:{tid}', 1, ['DGD', 'PMP', 'DPD'], {'M': frame, 'G': fg, 'D': circ, 'P': pl})
    shutil.copy(os.path.join(GT, 'gui', 'BasicTank.png'), tex_out('gui/auto/quantum_chest.png'))
    name('gui.iis.quantum.stored', 'Stored:', 'Внутри:')
    name('gui.iis.quantum.max', 'Up to %s', 'До %s')

    # --- miners and pumps: :1115-1117, :1263-1267 -----------------------------------------------------------------------
    shutil.copy(os.path.join(GT, 'gui', 'basicmachines', 'Miner.png'), tex_out('gui/auto/miner.png'))
    MD = os.path.join(GT, 'blocks', 'basicmachines', 'miner')

    def minerfaces(t, active):
        suf = '_ACTIVE' if active else ''
        s, top, bot = casing(t, 'SIDE'), casing(t, 'TOP'), casing(t, 'BOTTOM')
        ov = lambda n: first(os.path.join(MD, f'OVERLAY_{n}{suf}.png'))
        side = Image.alpha_composite(s, ov('SIDE'))
        return {'north': Image.alpha_composite(s, ov('FRONT')), 'south': side, 'east': side, 'west': side, 'up': Image.alpha_composite(top, ov('TOP')), 'down': Image.alpha_composite(bot, ov('BOTTOM'))}

    for i, t in enumerate(TIERS[:3]):
        bid = f'miner_{t}'
        machine(bid, minerfaces(t, False), minerfaces(t, True))
        r, sp, e = [8, 16, 24][i], [160, 80, 40][i], [8, 32, 128][i]
        name(f'block.{NS}.{bid}', f'{t.upper()} Miner', ru(f'gt.blockmachines.basicmachine.miner.tier.0{i + 1}.name', f'Автобур {t.upper()}'))
        tooltip(bid, [(f'Mines ores in a {2 * r + 1}x{2 * r + 1} square under itself, down to bedrock', f'Добывает руду в квадрате {2 * r + 1}×{2 * r + 1} под собой, до бедрока'),
                      (f'{e} EU/t, {sp // 20} s per block; mining pipes in the input slots', f'{e} EU/t, {sp // 20} с на блок; шахтные трубы — во входных слотах')])
    # :1115-1117 "EEE/WMW/CSC"
    for t in TIERS[:3]:
        shaped(f'auto/miner_{t}', f'{NS}:miner_{t}', 1, ['EEE', 'WMW', 'CSC'],
               {'M': f'{NS}:hull_{t}', 'E': f'{NS}:electric_motor_{t}', 'C': f'#c:circuits/{CIRC[t]}', 'W': tag_of_cable(CABLE[t]), 'S': f'{NS}:sensor_{t}'})

    def pumpfaces(t, active):
        suf = '_ACTIVE' if active else ''
        s, top, bot = casing(t, 'SIDE'), casing(t, 'TOP'), casing(t, 'BOTTOM')
        return {'north': Image.alpha_composite(s, first(os.path.join(MD, f'OVERLAY_FRONT{suf}.png'))), 'south': over(s, 'OVERLAY_PIPE_OUT'), 'east': over(s, 'OVERLAY_ADV_PUMP'),
                'west': over(s, 'OVERLAY_ADV_PUMP'), 'up': over(top, 'OVERLAY_ADV_PUMP'), 'down': over(bot, 'OVERLAY_PIPE_OUT')}

    for i, t in enumerate(TIERS):
        bid = f'pump_{t}'
        machine(bid, pumpfaces(t, False), pumpfaces(t, True))
        r, sp, e = [10, 20, 40, 80, 160][i], [80, 40, 20, 10, 5][i], [4, 16, 64, 256, 1024][i]
        name(f'block.{NS}.{bid}', f'{t.upper()} Pump', ru(f'gt.blockmachines.basicmachine.pump.tier.0{i + 1}.name', f'Помпа {t.upper()}'))
        tooltip(bid, [(f'Pumps fluids in a {2 * r + 1}x{2 * r + 1} square under itself', f'Откачивает жидкости в квадрате {2 * r + 1}×{2 * r + 1} под собой'),
                      (f'{e} EU/t, a fluid block each {sp} ticks; mining pipes in the input slots', f'{e} EU/t, блок жидкости за {sp} тиков; шахтные трубы — во входных слотах')])
        pipe = ['bronze', 'steel', 'stainless_steel', 'titanium', 'tungsten_steel'][i]
        shaped(f'auto/pump_{t}', f'{NS}:pump_{t}', 1, ['CPC', 'PMP', 'BPB'],
               {'M': f'{NS}:hull_{t}', 'B': f'{NS}:pipe_large_{pipe}', 'C': f'#c:circuits/{CIRC[t]}', 'P': f'{NS}:electric_pump_{t}'})

    # --- ULV hull (:148), adjustable transformers (GT_MachineRecipeLoader:2893-2902) ---------------------------------------
    s, top, bot = casing('ulv', 'SIDE'), casing('ulv', 'TOP'), casing('ulv', 'BOTTOM')
    for face, im in (('side', s), ('top', top), ('bottom', bot)):
        im.save(tex_out(f'block/auto/hull_ulv_{face}.png'))
    write(os.path.join(A, 'models', 'block', 'hull_ulv.json'), {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'side': f'{NS}:block/auto/hull_ulv_side', 'top': f'{NS}:block/auto/hull_ulv_top', 'bottom': f'{NS}:block/auto/hull_ulv_bottom'}})
    write(os.path.join(A, 'blockstates', 'hull_ulv.json'), {'variants': {'': {'model': f'{NS}:block/hull_ulv'}}})
    write(os.path.join(A, 'items', 'hull_ulv.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/hull_ulv'}})
    loot('hull_ulv')
    name(f'block.{NS}.hull_ulv', 'ULV Machine Hull', ru('gt.blockmachines.hull.tier.00.name', 'Корпус машины ULV'))
    shaped('auto/hull_ulv', f'{NS}:hull_ulv', 1, ['PHP', 'CMC'], {'M': f'{NS}:casing_ulv', 'C': tag_of_cable('Lead'), 'H': tag_of('plate', 'WroughtIron'), 'P': '#minecraft:planks'})
    shaped('lv/transformer_ulv', f'{NS}:transformer_ulv', 1, [' BB', 'CM ', ' BB'], {'M': f'{NS}:hull_ulv', 'C': tag_of_cable('Tin'), 'B': tag_of_cable('Lead')})

    shutil.copy(os.path.join(GT, 'gui', 'DigitalTransformer.png'), tex_out('gui/auto/digital_transformer.png'))
    for i, t in enumerate(('ev', 'iv', 'luv', 'zpm', 'uv', 'max')):
        bid = f'adjustable_transformer_{t}'
        base = first(os.path.join(BI, f'DIGITAL_TRANSFORMER_{ {"luv": "LuV"}.get(t, t.upper()) }.png'))
        norm = {'north': over(base, 'OVERLAY_ENERGY_IN_MULTI'), 'south': over(base, 'OVERLAY_ENERGY_OUT'), 'east': over(base, 'OVERLAY_ENERGY_OUT'), 'west': over(base, 'OVERLAY_ENERGY_OUT'),
                'up': over(base, 'OVERLAY_ENERGY_OUT'), 'down': over(base, 'OVERLAY_ENERGY_OUT')}
        inv = {'north': over(base, 'OVERLAY_ENERGY_OUT_MULTI'), 'south': over(base, 'OVERLAY_ENERGY_IN'), 'east': over(base, 'OVERLAY_ENERGY_IN'), 'west': over(base, 'OVERLAY_ENERGY_IN'),
               'up': over(base, 'OVERLAY_ENERGY_IN'), 'down': over(base, 'OVERLAY_ENERGY_IN')}
        machine(bid, norm, inv)
        name(f'block.{NS}.{bid}', f'{t.upper()} Adjustable Transformer', ru(f'gt.blockmachines.basicmachine.d_transformer.tier.0{4 + i}.name', f'Настраиваемый трансформатор {t.upper()}'))
        tooltip(bid, [(f'{t.upper()} in at the front, any voltage up to {t.upper()} out at the other sides', f'{t.upper()} — вход спереди, любое напряжение до {t.upper()} — с остальных сторон'),
                      ('Input and output amperes are set in the window, up to 64', 'Токи входа и выхода задаются в окне, до 64 А'),
                      ('Soft hammer swaps input and output', 'Киянка меняет вход и выход местами')])
    mrec('assembler', 'adjustable_transformer_ev', [ing(f'{NS}:transformer_ulv', 8), ing(f'{NS}:transformer_lv', 4), ing(f'{NS}:transformer_mv', 2), ing(f'{NS}:transformer_hv'),
                                                    ing(f'{NS}:cover_screen'), ing('#c:circuits/data', 4)], [out(f'{NS}:adjustable_transformer_ev')], 50, 1920)
    mrec('assembler', 'adjustable_transformer_iv', [ing(f'{NS}:transformer_ev'), ing(f'{NS}:adjustable_transformer_ev'), ing('#c:circuits/elite', 2)],
         [out(f'{NS}:adjustable_transformer_iv')], 50, 1920)
    # GT_MachineRecipeLoader:2904-2907
    mrec('assembler', 'adjustable_transformer_luv', [ing(f'{NS}:transformer_iv'), ing(f'{NS}:adjustable_transformer_iv'), ing('#c:circuits/master', 2)],
         [out(f'{NS}:adjustable_transformer_luv')], 50, 1920)
    mrec('assembler', 'adjustable_transformer_zpm', [ing(f'{NS}:transformer_luv'), ing(f'{NS}:adjustable_transformer_luv'), ing('#c:circuits/ultimate', 2)],
         [out(f'{NS}:adjustable_transformer_zpm')], 50, 1920)   # :2909-2912 (D112)
    mrec('assembler', 'adjustable_transformer_uv', [ing(f'{NS}:transformer_zpm'), ing(f'{NS}:adjustable_transformer_zpm'), ing('#c:circuits/superconductor', 2)],
         [out(f'{NS}:adjustable_transformer_uv')], 50, 1920)   # :2914-2917 (D121)
    mrec('assembler', 'adjustable_transformer_max', [ing(f'{NS}:transformer_uv'), ing(f'{NS}:adjustable_transformer_uv'), ing('#c:circuits/infinite', 2)],
         [out(f'{NS}:adjustable_transformer_max')], 50, 1920)   # :2919-2922 (D133)

    # --- D133: well (GT_MetaTileEntity_Well, GT_CraftingRecipeLoader:491) and the muscle machines (GT_MetaTileEntity_PrimitiveMineDigger, _PrimitiveMiner)
    WM = os.path.join(GT, 'blocks', 'basicmachines', 'well')
    def well_face(part, active):
        im = casing('ulv', 'SIDE' if part in ('FRONT', 'SIDE') else part)
        return Image.alpha_composite(im, first(os.path.join(WM, f'OVERLAY_{part}{"_ACTIVE" if active else ""}.png')))
    machine('well', {'north': well_face('FRONT', False), 'south': well_face('SIDE', False), 'east': well_face('SIDE', False), 'west': well_face('SIDE', False), 'up': well_face('TOP', False), 'down': well_face('BOTTOM', False)},
            {'north': well_face('FRONT', True), 'south': well_face('SIDE', True), 'east': well_face('SIDE', True), 'west': well_face('SIDE', True), 'up': well_face('TOP', True), 'down': well_face('BOTTOM', True)})
    name(f'block.{NS}.well', 'Well', ru('gt.blockmachines.basicmachine.well.name', 'Колодец'))
    tooltip('well', [('Draws the fluid from under bedrock: water or oil, 1000 L at most', 'Достаёт жидкость из-под коренной породы: воду или нефть, не больше 1000 л'),
                     ('Fill rate depends on the field in this chunk', 'Скорость зависит от месторождения в этом чанке'),
                     ('Right click with a bucket or a capsule when it is full', 'Когда полон — ПКМ ведром или капсулой')])
    shaped('auto/well', f'{NS}:well', 1, ['SPS', 'WQW', 'BVB'], {'S': f'{NS}:gear_wood', 'P': 'minecraft:stick', 'W': '#minecraft:planks',
                                                                'Q': 'minecraft:string', 'B': '#c:cobblestones', 'V': 'minecraft:bucket'})
    bricks = first(os.path.join(BI, 'MACHINE_BRONZEPLATEDBRICKS.png'))
    for bid, ov, en, ruu, lines in (
            ('primitive_mine_digger', 'OVERLAY_PRIMITIVE_MINE_DIGGER', 'Primitive Mine Digger', 'Примитивный шахтокопатель', [
                ('Digs a shaft down with your muscle power: work it with the button in the window', 'Копает шахту вниз вашей силой: кнопка в окне'),
                ('Needs a primitive drill and ladders; cobblestone shores up the walls, torches go in at the end', 'Нужны примитивный бур и лестницы; булыжник крепит стены, факелы ставятся в конце'),
                ('Stops at an ore vein or bedrock and digs to the bottom of the vein', 'Останавливается у рудной жилы или коренной породы, жилу проходит до дна'),
                ('Take out everything but cobblestone and it climbs back picking up the ladders', 'Оставьте во входах только булыжник — поднимется, собирая лестницы'),
                ('Hard work: you will get hungry', 'Тяжёлая работа: вы проголодаетесь')]),
            ('primitive_small_ore_miner', 'OVERLAY_PRIMITIVE_MINER', 'Primitive Small Ore Miner', 'Примитивный добытчик малых руд', [
                ('Finds and digs small ores only, 7 x 7 under it, with your muscle power', 'Ищет и добывает только малые руды, 7 × 7 под собой, вашей силой'),
                ('Needs a primitive drill and cobblestone or dirt to fill the holes', 'Нужны примитивный бур и булыжник или земля для заделки'),
                ('Hard work: you will get hungry', 'Тяжёлая работа: вы проголодаетесь')])):
        front = over(bricks, ov)
        machine(bid, {'north': front, 'south': bricks, 'east': bricks, 'west': bricks, 'up': bricks, 'down': bricks},
                {'north': over(bricks, ov + '_DRILL'), 'south': bricks, 'east': bricks, 'west': bricks, 'up': bricks, 'down': bricks})
        name(f'block.{NS}.{bid}', en, ruu)
        tooltip(bid, lines)
    # GT_Loader_MetaTileEntities:1297, 1301
    shaped('auto/primitive_mine_digger', f'{NS}:primitive_mine_digger', 1, ['SPS', 'LHL', 'SPS'], {'S': f'{NS}:gear_stone', 'P': 'minecraft:piston', 'H': f'{NS}:hull_bronze', 'L': 'minecraft:ladder'})
    shaped('auto/primitive_small_ore_miner', f'{NS}:primitive_small_ore_miner', 1, ['GBG', 'BPB', 'IBI'], {'G': f'{NS}:gear_stone', 'B': '#c:plates/bronze', 'P': 'minecraft:piston', 'I': 'minecraft:iron_bars'})
    # the window of the muscle machines is drawn: GT PrimitiveMiner.png is not among the sources
    gui = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    gp = gui.load()
    def rect(x0, y0, x1, y1, c):
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                gp[xx, yy] = c
    rect(0, 0, 176, 166, (198, 198, 198, 255))
    rect(0, 0, 176, 1, (255, 255, 255, 255)); rect(0, 0, 1, 166, (255, 255, 255, 255))
    rect(0, 165, 176, 166, (85, 85, 85, 255)); rect(175, 0, 176, 166, (85, 85, 85, 255))
    def slot(x, y, w=18, h=18):
        rect(x - 1, y - 1, x - 1 + w, y - 1 + h, (139, 139, 139, 255))
        rect(x - 1, y - 1, x - 1 + w, y, (55, 55, 55, 255)); rect(x - 1, y - 1, x, y - 1 + h, (55, 55, 55, 255))
        rect(x - 1, y - 2 + h, x - 1 + w, y - 1 + h, (255, 255, 255, 255)); rect(x - 2 + w, y - 1, x - 1 + w, y - 1 + h, (255, 255, 255, 255))
    for i in range(4):
        slot(8, 8 + i * 18)
    for yy in range(4):
        for xx in range(6):
            slot(62 + xx * 18, 8 + yy * 18)
    for yy in range(3):
        for xx in range(9):
            slot(8 + xx * 18, 84 + yy * 18)
    for xx in range(9):
        slot(8 + xx * 18, 142)
    # the button (31,8) 26x22 raised; the strength (31,34) and work (31,44) bars sunk
    rect(31, 8, 57, 30, (170, 170, 170, 255)); rect(31, 8, 57, 9, (255, 255, 255, 255)); rect(31, 8, 32, 30, (255, 255, 255, 255))
    rect(31, 29, 57, 30, (85, 85, 85, 255)); rect(56, 8, 57, 30, (85, 85, 85, 255))
    for yb in (34, 44):
        slot(32, yb + 1, 24, 6)
    gui.save(tex_out('gui/auto/muscle.png'))
    for k, en, ruu in (('gui.iis.muscle.work', 'Work', 'Работать'), ('gui.iis.muscle.power', 'Strength %s / %s', 'Сила %s / %s'),
                       ('gui.iis.muscle.depth', 'Depth %s', 'Глубина %s'), ('gui.iis.muscle.done', 'Done', 'Готово'),
                       ('gui.iis.muscle.pickup', 'Climbing back', 'Поднимается'), ('gui.iis.muscle.tired', 'Too tired: the strength bar is full', 'Сила и так полна'),
                       ('message.iis.well.empty', 'Nothing yet: %s L of %s', 'Пока пусто: %s л из %s'), ('message.iis.well.state', '%s: %s L of 1000', '%s: %s л из 1000'),
                       ('message.iis.well.not_full', 'Wait until it is full', 'Подождите, пока наполнится')):
        name(k, en, ruu)
    # GT_MachineRecipeLoader:71: the processing array is finally craftable
    shaped('multi/processing_array', f'{NS}:processing_array', 1, ['CTC', 'FMF', 'CBC'],
           {'M': f'{NS}:hull_ev', 'B': f'{NS}:electric_pump_ev', 'C': '#c:circuits/elite', 'F': f'{NS}:robot_arm_ev', 'T': f'{NS}:adjustable_transformer_ev'})
    for k, en, ruu in (('gui.iis.dtransformer.in', 'In: %s V, %s A', 'Вход: %s В, %s А'), ('gui.iis.dtransformer.tier', 'Out: %s V', 'Выход: %s В'),
                       ('gui.iis.dtransformer.out', 'Out: %s A', 'Выход: %s А'), ('itemGroup.iis.automation', 'IIS 2.0: Automation and storage', 'ИИС 2.0: автоматика и хранилища'),
                       ('message.iis.auto.target', 'Output stack size: %s', 'Размер порции: %s'), ('message.iis.auto.target_off', 'Output stack size: any', 'Размер порции: любой'),
                       ('message.iis.auto.items_per_side', 'Items per side: %s', 'Предметов на сторону: %s'),
                       ('message.iis.auto.button.0.on', 'Energy output: on', 'Выдача энергии: включена'), ('message.iis.auto.button.0.off', 'Energy output: off', 'Выдача энергии: выключена'),
                       ('message.iis.auto.button.1.on', 'Redstone when full: on', 'Сигнал при заполнении: включён'), ('message.iis.auto.button.1.off', 'Redstone when full: off', 'Сигнал при заполнении: выключен'),
                       ('message.iis.auto.button.2.on', 'Inverted redstone: on', 'Инверсия сигнала: включена'), ('message.iis.auto.button.2.off', 'Inverted redstone: off', 'Инверсия сигнала: выключена'),
                       ('message.iis.auto.button.3.on', 'Inverted filter: on', 'Инверсия фильтра: включена'), ('message.iis.auto.button.3.off', 'Inverted filter: off', 'Инверсия фильтра: выключена'),
                       ('message.iis.auto.button.4.on', 'Ignore NBT: on', 'Без учёта данных предмета: включено'), ('message.iis.auto.button.4.off', 'Ignore NBT: off', 'Без учёта данных предмета: выключено')):
        name(k, en, ruu)
    TYPE_NAMES = {'ore': ('Ores', 'Руды'), 'ingot': ('Ingots', 'Слитки'), 'dust': ('Dusts', 'Пыли'), 'impure_dust': ('Impure dusts', 'Грязные пыли'), 'pure_dust': ('Purified dusts', 'Очищенные пыли'),
                  'small_dust': ('Small dusts', 'Малые кучки'), 'tiny_dust': ('Tiny dusts', 'Крошечные кучки'), 'crushed': ('Crushed ores', 'Дроблёные руды'), 'purified': ('Purified ores', 'Очищенные руды'),
                  'centrifuged': ('Centrifuged ores', 'Центрифуг. руды'), 'nugget': ('Nuggets', 'Самородки'), 'plate': ('Plates', 'Пластины'), 'double_plate': ('Double plates', 'Двойные пластины'),
                  'rod': ('Rods', 'Стержни'), 'long_rod': ('Long rods', 'Длинные стержни'), 'bolt': ('Bolts', 'Болты'), 'screw': ('Screws', 'Винты'), 'ring': ('Rings', 'Кольца'), 'gear': ('Gears', 'Шестерни'),
                  'small_gear': ('Small gears', 'Малые шестерни'), 'gem': ('Gems', 'Самоцветы'), 'foil': ('Foils', 'Фольга'), 'fine_wire': ('Fine wires', 'Тонкие провода'), 'block': ('Blocks', 'Блоки')}
    for k, (en, ruu) in TYPE_NAMES.items():
        name(f'gui.iis.auto.type.{k}', en, ruu)

    # --- battery chargers: :586-591 "WTW/WMW/BCB" -------------------------------------------------------------------------
    # D121: EV and IV had no recipe since D108: their batteries came with the IC2 gear (D118); LuV, ZPM and UV added with the orb cluster and the ultimate battery
    BAT = {'lv': f'{NS}:battery_lithium_lv', 'mv': f'{NS}:battery_lithium_mv', 'hv': f'{NS}:battery_lithium_hv', 'ev': '#iis:batteries/master', 'iv': f'{NS}:energy_orb',
           'luv': f'{NS}:energy_orb_cluster', 'zpm': f'{NS}:energy_orb_cluster', 'uv': f'{NS}:ultimate_battery'}
    CHARGER_WIRE = dict(CABLE, luv='vanadium_gallium', zpm='naquadah', uv='naquadah_alloy')
    for i, t in enumerate(AUTO_TIERS):
        bid = f'battery_charger_{t}'
        s, top, bot = casing(t, 'SIDE'), casing(t, 'TOP'), casing(t, 'BOTTOM')
        machine(bid, {'north': over(s, 'OVERLAY_ENERGY_OUT'), 'south': s, 'east': s, 'west': s, 'up': top, 'down': bot})
        v = 32 << (2 * i)
        TN = 'LuV' if t == 'luv' else t.upper()
        name(f'block.{NS}.{bid}', f'{TN} Battery Charger', ru(f'gt.blockmachines.batterycharger.16.tier.0{i + 1}.name', f'Зарядник {TN}'))
        tooltip(bid, [(f'16 slots: charges batteries and tools at up to {v * 15} EU/t each', f'16 слотов: заряжает батарейки и инструменты, до {v * 15} EU/t на предмет'),
                      (f'Output: {v} EU/t, 4 A per battery', f'Выход: {v} EU/t, 4 А на батарейку')])
        b = BAT.get(t)
        if b:
            shaped(f'auto/{bid}', f'{NS}:{bid}', 1, ['WTW', 'WMW', 'BCB'],
                   {'M': f'{NS}:hull_{t}', 'W': tag_of_wire(CHARGER_WIRE[t], 16), 'T': '#c:chests/wooden', 'B': b, 'C': f'#c:circuits/{CIRC[t]}'})

    # --- field generators and quantum items: GT_MachineRecipeLoader:409-413, 1190-1191, 674 --------------------------------
    mrec('assembler', 'field_generator_lv', [ing(tag_of('dust', 'EnderPearl')), ing('#iis:wires/1x/osmium', 4), ing('#c:circuits/basic', 4)], [out(f'{NS}:field_generator_lv')], 1800, 30)
    mrec('assembler', 'field_generator_mv', [ing(tag_of('dust', 'EnderEye')), ing('#iis:wires/2x/osmium', 4), ing('#c:circuits/good', 4)], [out(f'{NS}:field_generator_mv')], 1800, 120)
    mrec('assembler', 'field_generator_hv', [ing(f'{NS}:quantum_eye'), ing('#iis:wires/4x/osmium', 4), ing('#c:circuits/advanced', 4)], [out(f'{NS}:field_generator_hv')], 1800, 480)
    mrec('assembler', 'field_generator_ev', [ing(tag_of('dust', 'NetherStar')), ing('#iis:wires/8x/osmium', 4), ing('#c:circuits/data', 4)], [out(f'{NS}:field_generator_ev')], 1800, 1920)
    mrec('assembler', 'field_generator_iv', [ing(f'{NS}:quantum_star'), ing('#iis:wires/16x/osmium', 4), ing('#c:circuits/elite', 4)], [out(f'{NS}:field_generator_iv')], 1800, 7680)
    mrec('chemical_bath', 'quantum_eye', [ing('minecraft:ender_eye')], [out(f'{NS}:quantum_eye')], 480, 384, fin={'ingredient': f'{NS}:radon', 'amount': 250})
    mrec('chemical_bath', 'quantum_star', [ing('minecraft:nether_star')], [out(f'{NS}:quantum_star')], 1920, 384, fin={'ingredient': f'{NS}:radon', 'amount': 1250})
    mrec('assembler', 'cover_screen', [ing(tag_of('plate', 'Glass')), ing(tag_of('foil', 'Aluminium'), 4), ing('#c:circuits/basic'), ing(tag_of('wire_fine', 'Copper'), 4)],
         [out(f'{NS}:cover_screen')], 50, 16)

    # --- D109: monster repellents (:1272-1276 "EEE/ M /CCC") and the large combustion engine (:1359-1361) ---------------------------
    for i, t in enumerate(AUTO_TIERS):
        bid = f'monster_repellent_{t}'
        s, top, bot = casing(t, 'SIDE'), casing(t, 'TOP'), casing(t, 'BOTTOM')
        machine(bid, {'north': s, 'south': s, 'east': s, 'west': s, 'up': over(top, 'OVERLAY_TELEPORTER'), 'down': bot})
        name(f'block.{NS}.{bid}', f'{t.upper()} Monster Repellator', ru(f'gt.blockmachines.basicmachine.mobrep.tier.0{i + 1}.name', f'Отпугиватель монстров {t.upper()}'))
        tier = i + 1
        tooltip(bid, [(f'Mobs do not spawn within {4 + 12 * tier} blocks, {16 + 48 * tier} when powered', f'Мобы не появляются в {4 + 12 * tier} блоках, с энергией — в {16 + 48 * tier}'),
                      ('Screwdriver: hostile, hostile and neutral, or everyone', 'Отвёртка: враждебные; с нейтральными; все')])
        shaped(f'auto/{bid}', f'{NS}:{bid}', 1, ['EEE', ' M ', 'CCC'], {'M': f'{NS}:hull_{t}', 'E': f'{NS}:emitter_{t}', 'C': f'#c:circuits/{CIRC[t]}'})
    for k, en, ruu in (('message.iis.mobrep.hostiles', 'Prevents spawn of hostile creatures', 'Не даёт появляться враждебным'),
                       ('message.iis.mobrep.hostiles_neutrals', 'Prevents spawn of hostile and neutral creatures', 'Не даёт появляться враждебным и нейтральным'),
                       ('message.iis.mobrep.everyone', 'Prevents spawn of all creatures', 'Не даёт появляться никому')):
        name(k, en, ruu)

    for bid, src, key, en in (('engine_intake_casing', 'MACHINE_CASING_ENGINE_INTAKE', 'gt.blockcasings4.13.name', 'Engine Intake Casing'),
                              ('gear_box_titanium', 'MACHINE_CASING_GEARBOX_TITANIUM', 'gt.blockcasings2.4.name', 'Titanium Gear Box Casing')):
        first(os.path.join(BI, src + '.png')).save(tex_out(f'block/multi/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/multi/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        loot(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
    shaped('multi/engine_intake_casing', f'{NS}:engine_intake_casing', 2, ['PhP', 'RFR', 'PwP'],
           {'P': tag_of('rotor', 'Titanium'), 'R': f'{NS}:pipe_medium_titanium', 'F': f'{NS}:stable_titanium_casing'})
    shaped('multi/gear_box_titanium', f'{NS}:gear_box_titanium', 2, ['PhP', 'GFG', 'PwP'],
           {'P': tag_of('plate', 'Steel'), 'F': '#c:frames/titanium', 'G': tag_of('gear', 'Titanium')})
    base = first(os.path.join(BI, 'MACHINE_CASING_STABLE_TITANIUM.png'))
    machine('combustion_engine', {'north': over(base, 'OVERLAY_FRONT_DIESEL_ENGINE'), 'south': base, 'east': base, 'west': base, 'up': base, 'down': base},
            {'north': over(base, 'OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE'), 'south': base, 'east': base, 'west': base, 'up': base, 'down': base})
    # D130: владелец ищет его как «большой дизельный генератор», в GT он «Large Combustion Engine»
    name(f'block.{NS}.combustion_engine', 'Large Diesel Generator', 'Большой дизельный генератор')
    tooltip('combustion_engine', [('3x3x4 on stable titanium casings; 8 engine intakes around the controller', 'Размер 3×3×4 на стабильных титановых корпусах; вокруг контроллера 8 заборных корпусов'),
                                  ('Air in front of the intakes; 2 titanium gear boxes along the axis, dynamo at the back', 'Перед заборниками — воздух; по оси 2 титановые коробки передач, сзади динамо-люк'),
                                  ('Hatches in the side centers; muffler at the top of the rear layer', 'Люки — в серединах сторон; глушитель — сверху на третьем слое'),
                                  ('Diesel fuels and 1 L of lubricant per 72 ticks: 2048 EU/t', 'Дизельное топливо и 1 л смазки на 72 тика: 2048 EU/t'),
                                  ('50 L/t of oxygen: up to 6144 EU/t, twice the fuel', 'Кислород 50 л/т: до 6144 EU/t, топлива вдвое больше')])
    shaped('multi/combustion_engine', f'{NS}:combustion_engine', 1, ['PCP', 'EME', 'GWG'],
           {'M': f'{NS}:hull_ev', 'P': f'{NS}:electric_piston_ev', 'E': f'{NS}:electric_motor_ev', 'C': '#c:circuits/elite', 'W': tag_of_cable('tungsten_steel'), 'G': tag_of('gear', 'Titanium')})

    # --- storage blocks: GT_Block_Metal, ProcessingBlock (IIS Recipes.cfg: no crafting either way) --------------------------
    wanted = json.load(open(os.path.join(REF, 'gt5_storage_blocks.json'), encoding='utf-8'))
    fluid_ids = {f['id'] for f in json.load(open(os.path.join(RES, NS, 'gt_fluids.json'), encoding='utf-8'))}
    VANILLA = {'Copper': 'minecraft:copper_block'}
    made = []
    for n in wanted:
        m = mat_by_name.get(n)
        if not m or n in VANILLA:
            continue
        ingot, gem = g['ref']('ingot', n), g['ref']('gem', n)
        if not ingot and not gem:
            continue
        mid = mat_id(n)
        bid = f'block_{mid}'
        # the gem blocks of garnet are named the other way round in GT (D132)
        p = os.path.join(BI, 'BLOCK_' + {'GarnetYellow': 'YELLOWGARNET', 'GarnetRed': 'REDGARNET'}.get(n, n.upper()) + '.png')
        if not os.path.exists(p):
            continue
        first(p).save(tex_out(f'block/storage/{mid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/storage/{mid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})
        loot(bid)
        en = f'Block of {m["en"]}'
        name(f'block.{NS}.{bid}', en, f'Блок: {m.get("ru") or m["en"]}')
        add_item_tag(f'c:storage_blocks/{mid}', f'{NS}:{bid}')
        block_tags.setdefault(f'c:storage_blocks/{mid}', []).append(f'{NS}:{bid}')
        made.append(mid)
        tag = f'#c:storage_blocks/{mid}'
        if ingot:
            mrec('compressor', f'block_{mid}', [ing(tag_of('ingot', n), 9)], [out(f'{NS}:{bid}')], 300, 2)
        elif gem:
            # D121: GT_ModHandler compression of a gem into its block: without it the plates of olivine and ruby, cut from the block, had no source
            mrec('compressor', f'block_{mid}', [ing(tag_of('gem', n), 9)], [out(f'{NS}:{bid}')], 300, 2)
        plate = g['ref']('plate', n)
        if plate:
            dur = max(mass(n) * 10, 1)
            for rid, fl, amt, dd in (('', 'minecraft:water', max(4, min(1000, dur * 30 // 320)), dur * 2), ('_distilled', f'{NS}:distilled_water', max(3, min(750, dur * 30 // 426)), dur * 2),
                                     ('_lubricant', f'{NS}:lubricant', max(1, min(250, dur * 30 // 1280)), dur)):
                mrec('cutter', f'block_{mid}{rid}', [ing(tag)], [out(plate, 9)], dd, 30, fin={'ingredient': fl, 'amount': amt})
        if gem:
            mrec('forge_hammer', f'block_{mid}', [ing(tag)], [out(gem, 9)], 100, 24)
        molten = f'molten_{mid.replace("_", "")}'
        if molten in fluid_ids:
            mrec('fluid_solidifier', f'block_{mid}', [], [out(f'{NS}:{bid}')], 288, 8, fin={'ingredient': f'{NS}:{molten}', 'amount': 1296}, keep=f'{NS}:mold_block')
    write(os.path.join(RES, NS, 'storage_blocks.json'), made)
    stats['storage_blocks'] = len(made)
    for mat_name, block in (('Iron', 'minecraft:iron_block'), ('Gold', 'minecraft:gold_block'), ('Copper', 'minecraft:copper_block')):
        mrec('compressor', f'block_{mat_name.lower()}', [ing(tag_of('ingot', mat_name), 9)], [out(block)], 300, 2)

    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    stats['recipes'] = len(rec)
    return stats


def tag_of_cable(mat):
    return f'#iis:cables/1x/{mat.lower()}'


def tag_of_wire(mat, n):
    return f'#iis:wires/{n}x/{mat.lower()}'
