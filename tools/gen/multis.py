"""Multiblocks by GT recipe maps (D104): implosion compressor, multi smelter, pyrolyse oven; ULV machine casing.

Controllers: casing texture + GT front overlay (GT_Loader_MetaTileEntities controller crafting), the structure in the tooltip.
Recipes generated here: implosion (ProcessingDust:164-186 dusts -> gems, ProcessingGem:56-119 gem grades, GT_RecipeAdder
#addImplosionRecipe: TNT = max(1, explosives / 2), 20 t, 30 EU/t) and pyrolysis (ProcessingLog:96-106, GT_MachineRecipeLoader:1818).
"""
import json
import os
import shutil

from PIL import Image

NS = 'iis'
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')


def generate(g):
    write, A, D, lang, REF = g['write'], g['A'], g['D'], g['lang'], g['REF']
    ref, tag_of, shaped, block_tags, names = g['ref'], g['tag_of'], g['shaped'], g['block_tags'], g['names']
    ru_lang = json.load(open(os.path.join(REF, 'gt5_lang_ru_machines.json'), encoding='utf-8'))
    BI = os.path.join(GT, 'blocks', 'iconsets')
    stats = {}
    rec = {}

    def ru(key, fallback):
        v = ru_lang.get(key) if key else None
        return v.strip() if v else fallback

    def name(key, en, ruu):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ruu

    def tooltip(bid, lines):
        for k, (en, ruu) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', en, ruu)

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

    def casing(bid, src, key, en):
        first(os.path.join(BI, src + '.png')).save(tex_out(f'block/multi/{bid}.png'))
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/multi/{bid}'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))

    def controller(bid, casing_tex, overlay, key, en, lines, active=None):
        side = f'{NS}:block/multi/{casing_tex}'
        base = Image.open(tex_out(f'block/multi/{casing_tex}.png')).convert('RGBA')
        for suf, ov in (('', overlay), ('_active', active or overlay + '_ACTIVE')):
            o = Image.open(os.path.join(BI, ov + '.png')).convert('RGBA')
            n = max(1, o.height // 16)
            out = Image.new('RGBA', (16, 16 * n))
            for f in range(n):
                out.paste(Image.alpha_composite(base, o.crop((0, 16 * f, 16, 16 * f + 16))), (0, 16 * f))
            out.save(tex_out(f'block/multi/{bid}_front{suf}.png'))
            meta = os.path.join(BI, ov + '.png.mcmeta')
            if n > 1 and os.path.exists(meta):
                shutil.copy(meta, tex_out(f'block/multi/{bid}_front{suf}.png.mcmeta'))
            write(os.path.join(A, 'models', 'block', bid + suf + '.json'), {'parent': 'minecraft:block/cube', 'textures': {
                'particle': side, 'north': f'{NS}:block/multi/{bid}_front{suf}', 'south': side, 'east': side, 'west': side, 'up': side, 'down': side}})
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for active in (False, True):
                v = {'model': f'{NS}:block/{bid}' + ('_active' if active else '')}
                if y:
                    v['y'] = y
                variants[f'facing={facing},active={str(active).lower()}'] = v
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
        tooltip(bid, lines)

    def ing(i, c=1):
        return {'ingredient': i, 'count': c}

    def out(i, c=1):
        return {'id': i, 'count': c}

    def mrec(rtype, rid, inputs, outputs, duration, eut, fin=None, fout=None, circuit=None):
        if any(i['ingredient'] is None for i in inputs) or any(not o['id'] for o in outputs):
            return
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(duration), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if fout:
            r['fluid_outputs'] = [fout]
        if circuit is not None:
            r['circuit'] = circuit
        rec.setdefault(f'{rtype}/{rid}', r)

    # --- casings -------------------------------------------------------------------------------------------------
    casing('casing_ulv', 'MACHINE_8V_SIDE', 'gt.blockcasings.0.name', 'ULV Machine Casing')
    # GT_Loader_MetaTileEntities:51 Casing_ULV "PPP/PwP/PPP" wrought iron; GT_MachineRecipeLoader:1371 assembler 8 plates + circuit 8
    shaped('multi/casing_ulv', f'{NS}:casing_ulv', 1, ['PPP', 'PwP', 'PPP'], {'P': tag_of('plate', 'WroughtIron')})
    mrec('assembler', 'casing_ulv', [ing(tag_of('plate', 'WroughtIron'), 8)], [out(f'{NS}:casing_ulv')], 50, 16, circuit=8)

    # --- controllers ---------------------------------------------------------------------------------------------
    controller('implosion_compressor', 'solid_steel_casing', 'OVERLAY_FRONT_IMPLOSION_COMPRESSOR', 'gt.blockmachines.multimachine.implosioncompressor.name',
               'Implosion Compressor', [
                   ('3x3x3 hollow, controller in the middle of a side', 'Куб 3×3×3, пустой внутри; контроллер — в середине стенки'),
                   ('At least 16 solid steel machine casings; the rest: hatches', 'Не меньше 16 прочных стальных корпусов; остальное — люки'),
                   ('Maintenance, muffler, input and output buses, energy hatches', 'Обслуживание, глушитель, входная и выходная шины, энергия'),
                   ('Recipes run without overclock', 'Рецепты идут без разгона'),
                   ('Causes 2000 Pollution per second', 'Загрязнение: 2000 в секунду')])
    # :1149 "OOO/CMC/WCW": obsidian, solid steel casing, Advanced circuits, aluminium cables
    shaped('multi/implosion_compressor', f'{NS}:implosion_compressor', 1, ['OOO', 'CMC', 'WCW'],
           {'O': 'minecraft:obsidian', 'M': f'{NS}:solid_steel_casing', 'C': '#c:circuits/advanced', 'W': '#iis:cables/1x/aluminium'})
    controller('multi_smelter', 'heat_proof_casing', 'OVERLAY_FRONT_MULTI_SMELTER', 'gt.blockmachines.multimachine.multifurnace.name', 'Multi Smelter', [
        ('3x3x3; controller in the middle of the bottom layer side', 'Куб 3×3×3; контроллер — в середине стороны нижнего слоя'),
        ('Middle layer: a ring of 8 equal heating coils around air', 'Средний слой: кольцо из 8 одинаковых катушек вокруг пустоты'),
        ('Bottom and top: heat proof casings; muffler in the top center', 'Низ и верх — теплоустойчивые корпуса; глушитель в центре верха'),
        ('Smelts 8 x coil level items at once (cupronickel 1 ... tungstensteel 8)', 'Плавит сразу 8 × уровень катушки (мельхиор 1 … вольфрамосталь 8)'),
        ('Causes 100 Pollution per second', 'Загрязнение: 100 в секунду')])
    # :1150 "FFF/CMC/WCW": furnaces, heat proof casing, Advanced circuits, annealed copper cables
    shaped('multi/multi_smelter', f'{NS}:multi_smelter', 1, ['FFF', 'CMC', 'WCW'],
           {'F': 'minecraft:furnace', 'M': f'{NS}:heat_proof_casing', 'C': '#c:circuits/advanced', 'W': '#iis:cables/1x/annealed_copper'})
    controller('pyrolyse_oven', 'casing_ulv', 'OVERLAY_FRONT_PYROLYSE_OVEN', 'gt.blockmachines.multimachine.pyro.name', 'Pyrolyse Oven', [
        ('5x5x4; controller in the middle of the bottom front side', 'Размер 5×5×4; контроллер — в середине передней стороны низа'),
        ('Bottom: 3x3 equal heating coils inside, ULV casings or hatches around', 'Низ: внутри 3×3 одинаковых катушек, по краю — каркасы ULV или люки'),
        ('Two layers of air inside ULV casing walls', 'Два слоя пустоты внутри стенок из каркасов ULV'),
        ('Top: ULV casings, input bus / hatch and muffler inside', 'Верх: каркасы ULV, входная шина и люк, глушитель — внутри'),
        ('Better coils work faster (cupronickel x1 ... tungstensteel x2)', 'Лучшие катушки быстрее (мельхиор ×1 … вольфрамосталь ×2)'),
        ('Causes 200 Pollution per second', 'Загрязнение: 200 в секунду')])
    # :1336 "WEP/EME/WCP": MV hull, MV pistons, 4x cupronickel wires, Good circuits, MV pump
    shaped('multi/pyrolyse_oven', f'{NS}:pyrolyse_oven', 1, ['WEP', 'EME', 'WCP'],
           {'M': f'{NS}:hull_mv', 'W': f'{NS}:electric_piston_mv', 'P': '#iis:wires/4x/cupronickel', 'E': '#c:circuits/good', 'C': f'{NS}:electric_pump_mv'})

    # --- implosion recipes ---------------------------------------------------------------------------------------
    def tnt(explosives):
        return max(1, explosives // 2)
    dust_gems = {'Diamond': 32, 'Opal': 24, 'Olivine': 24, 'Emerald': 24, 'Ruby': 24, 'Sapphire': 24, 'GreenSapphire': 24, 'Topaz': 24,
                 'BlueTopaz': 24, 'Tanzanite': 24, 'FoolsRuby': 16, 'GarnetRed': 16, 'GarnetYellow': 16, 'Jasper': 16, 'Amber': 16, 'Monazite': 16}
    for m, ex in dust_gems.items():
        d_, gem = ref('dust', m), ref('gem', m)
        if not d_ or not gem:
            continue
        mrec('implosion', f'gem_{g["mat_id"](m)}', [ing(tag_of('dust', m), 4), ing('minecraft:tnt', tnt(ex))],
             [out(gem, 3), out(ref('tiny_dust', 'DarkAsh'), ex // 2)], 20, 30)
    for m in sorted(names):
        for a, b in (('gem_chipped', 'gem_flawed'), ('gem_flawed', 'gem'), ('gem', 'gem_flawless'), ('gem_flawless', 'gem_exquisite')):
            if ref(a, m) and ref(b, m):
                mrec('implosion', f'{b}_{g["mat_id"](m)}', [ing(tag_of(a, m), 3), ing('minecraft:tnt', tnt(8))],
                     [out(ref(b, m)), out(ref('tiny_dust', 'DarkAsh'), 2)], 20, 30)

    # --- pyrolysis: ProcessingLog:96-106 (16 logs, circuit; with 1000 L nitrogen twice as fast), charcoal byproducts wait for
    # their fluid; GT_MachineRecipeLoader:1818 bio chaff + water -> biomass ------------------------------------------------
    N2 = {'ingredient': 'iis:nitrogen', 'amount': 1000}
    for c, gem_out, fluid, amt, dur, eut in ((1, ('minecraft:charcoal', 20), 'iis:creosote', 4000, 640, 64),
                                            (3, (ref('dust', 'Ash'), 4), 'iis:liquid_heavy_oil', 200, 320, 192),
                                            (5, ('minecraft:charcoal', 20), 'iis:woodgas', 1500, 640, 64),
                                            (7, ('minecraft:charcoal', 20), 'iis:woodvinegar', 3000, 640, 64),
                                            (9, ('minecraft:charcoal', 20), 'iis:woodtar', 1500, 640, 64)):
        rid = fluid.split(':')[1]
        mrec('pyrolyse', f'logs_{rid}', [ing('#minecraft:logs', 16)], [out(*gem_out)], dur, eut, fout={'id': fluid, 'amount': amt}, circuit=c)
        if c != 3:
            mrec('pyrolyse', f'logs_{rid}_nitrogen', [ing('#minecraft:logs', 16)], [out(*gem_out)], dur // 2, 96, fin=N2,
                 fout={'id': fluid, 'amount': amt}, circuit=c + 1)
    rec['pyrolyse/bio_chaff'] = {'type': f'{NS}:pyrolyse', 'inputs': [ing(f'{NS}:bio_chaff')], 'duration': 100, 'eut': 10, 'circuit': 1,
                                 'fluid_inputs': [{'ingredient': 'minecraft:water', 'amount': 1000}],
                                 'fluid_outputs': [{'id': 'iis:biomass', 'amount': 1500}]}

    # --- distillation tower and oil cracker -------------------------------------------------------------------------
    casing('clean_stainless_casing', 'MACHINE_CASING_CLEAN_STAINLESSSTEEL', 'gt.blockcasings4.1.name', 'Clean Stainless Steel Machine Casing')
    # GT_Loader_MetaTileEntities:67 "PhP/PFP/PwP" stainless plates + frame -> 2; GT_MachineRecipeLoader:1392 assembler 6 plates + frame
    shaped('multi/clean_stainless_casing', f'{NS}:clean_stainless_casing', 2, ['PhP', 'PFP', 'PwP'],
           {'P': tag_of('plate', 'StainlessSteel'), 'F': '#c:frames/stainless_steel'})
    mrec('assembler', 'clean_stainless_casing', [ing(tag_of('plate', 'StainlessSteel'), 6), ing('#c:frames/stainless_steel')],
         [out(f'{NS}:clean_stainless_casing', 2)], 50, 16)
    controller('distillation_tower', 'clean_stainless_casing', 'OVERLAY_FRONT_DISTILLATION_TOWER', 'gt.blockmachines.multimachine.distillationtower.name',
               'Distillation Tower', [
                   ('3x3 column, 3 to 12 high; controller in the middle of the bottom side', 'Столб 3×3 высотой 3–12; контроллер — в середине стороны низа'),
                   ('Bottom: clean stainless steel casings, inputs, outputs, maintenance, energy',
                    'Низ: чистые корпуса из нержавеющей стали, входы, выходы, обслуживание, энергия'),
                   ('Each upper layer: air in the middle; the top layer is solid', 'Каждый этаж выше — пустой в середине; верхний этаж сплошной'),
                   ('Fluid N of a recipe goes to the output hatches of layer N + 1', 'Жидкость № N рецепта уходит в выходные люки этажа N + 1')])
    # :1241 "CBC/FMF/CBC": EV hull, large stainless steel pipes, Data circuits, EV pumps
    shaped('multi/distillation_tower', f'{NS}:distillation_tower', 1, ['CBC', 'FMF', 'CBC'],
           {'M': f'{NS}:hull_ev', 'B': f'{NS}:pipe_large_stainless_steel', 'C': '#c:circuits/data', 'F': f'{NS}:electric_pump_ev'})
    controller('oil_cracker', 'clean_stainless_casing', 'OVERLAY_FRONT_OIL_CRACKER', 'gt.blockmachines.multimachine.cracker.name', 'Oil Cracking Unit', [
        ('5 wide, 3 high, 3 deep; controller in the middle of the front', 'Ширина 5, высота 3, глубина 3; контроллер — в середине передней стороны'),
        ('Rings of cupronickel coils on both sides of the middle slice', 'По обе стороны от середины — кольца мельхиоровых катушек'),
        ('Middle and ends: clean stainless steel casings (at least 18) or hatches',
         'Середина и торцы — чистые корпуса из нержавеющей стали (не меньше 18) или люки'),
        ('An input hatch on one end, an output hatch on the other', 'На одном торце входной люк, на другом — выходной'),
        ('Hydrogen or steam + a hydrocarbon -> its cracked fraction', 'Водород или пар + углеводород → крекинг-фракция')])
    # :1340 "WCW/EME/WCW": HV hull, cupronickel coil blocks, Advanced circuits, HV pumps
    shaped('multi/oil_cracker', f'{NS}:oil_cracker', 1, ['WCW', 'EME', 'WCW'],
           {'M': f'{NS}:hull_hv', 'W': f'{NS}:coil_cupronickel', 'E': '#c:circuits/advanced', 'C': f'{NS}:electric_pump_hv'})

    # --- large chemical reactor ---------------------------------------------------------------------------------
    casing('chemically_inert_casing', 'MACHINE_CASING_CHEMICALLY_INERT', 'gt.blockcasings8.0.name', 'Chemically Inert Machine Casing')
    # GT_MachineRecipeLoader:1400: solid steel casing + circuit 6 + 216 L molten PTFE (50 t, 16 EU/t); no crafting in GT
    rec['assembler/chemically_inert_casing'] = {'type': f'{NS}:assembler', 'inputs': [ing(f'{NS}:solid_steel_casing')],
                                                'outputs': [out(f'{NS}:chemically_inert_casing')], 'duration': 50, 'eut': 16, 'circuit': 6,
                                                'fluid_inputs': [{'ingredient': f'{NS}:molten_polytetrafluoroethylene', 'amount': 216}]}
    casing('ptfe_pipe_casing', 'MACHINE_CASING_PIPE_POLYTETRAFLUOROETHYLENE', 'gt.blockcasings8.1.name', 'PTFE Pipe Machine Casing')
    # GT_Loader_MetaTileEntities:79 "PIP/IFI/PIP" PTFE plates, medium PTFE pipes, PTFE frame -> 2
    shaped('multi/ptfe_pipe_casing', f'{NS}:ptfe_pipe_casing', 2, ['PIP', 'IFI', 'PIP'],
           {'P': tag_of('plate', 'Polytetrafluoroethylene'), 'I': f'{NS}:pipe_medium_polytetrafluoroethylene', 'F': '#c:frames/polytetrafluoroethylene'})
    controller('large_chemical_reactor', 'chemically_inert_casing', 'OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR', 'gt.blockmachines.multimachine.chemicalreactor.name',
               'Large Chemical Reactor', [
                   ('3x3x3, controller in the middle of a side; PTFE pipe casing in the center',
                    'Куб 3×3×3, контроллер — в середине стенки; в центре — корпус с ПТФЭ-трубами'),
                   ('At least one cupronickel coil in the middle of a face', 'Хотя бы одна мельхиоровая катушка в середине грани'),
                   ('The rest: chemically inert casings (at least 8) or hatches', 'Остальное — химически стойкие корпуса (не меньше 8) или люки'),
                   ('Chemical reactor recipes with perfect overclock (x4 power, /4 time)', 'Рецепты химреактора с идеальным разгоном (×4 энергии, ÷4 времени)')])
    # :1391 "CRC/PMP/CBC": Advanced circuits, stainless steel rotor, large PTFE pipes, HV motor, HV hull
    shaped('multi/large_chemical_reactor', f'{NS}:large_chemical_reactor', 1, ['CRC', 'PMP', 'CBC'],
           {'C': '#c:circuits/advanced', 'R': tag_of('rotor', 'StainlessSteel'), 'P': f'{NS}:pipe_large_polytetrafluoroethylene',
            'M': f'{NS}:electric_motor_hv', 'B': f'{NS}:hull_hv'})

    # --- large gas turbine ---------------------------------------------------------------------------------------
    casing('turbine_casing_stainless', 'MACHINE_CASING_CLEAN_STAINLESSSTEEL', 'gt.blockcasings4.10.name', 'Stainless Steel Turbine Casing')
    # GT_Loader_MetaTileEntities:72 "PhP/PFP/PwP" stainless plates + turbine casing -> 2; GT_MachineRecipeLoader:1397 assembler
    shaped('multi/turbine_casing_stainless', f'{NS}:turbine_casing_stainless', 2, ['PhP', 'PFP', 'PwP'],
           {'P': tag_of('plate', 'StainlessSteel'), 'F': f'{NS}:turbine_casing'})
    mrec('assembler', 'turbine_casing_stainless', [ing(tag_of('plate', 'StainlessSteel'), 6), ing(f'{NS}:turbine_casing')],
         [out(f'{NS}:turbine_casing_stainless', 2)], 50, 16)
    controller('large_gas_turbine', 'turbine_casing_stainless', 'LARGETURBINE_SS5', 'gt.blockmachines.multimachine.largegasturbine.name', 'Large Gas Turbine', [
        ('Shape of the large steam turbine on stainless steel turbine casings', 'Форма большой паровой турбины на турбинных корпусах из нержавеющей стали'),
        ('Burns gas fuels from input hatches; a rotor in the controller', 'Сжигает газовое топливо из входных люков; ротор — в контроллере'),
        ('Optimal flow = rotor optimum / EU per litre, up to 125 %', 'Оптимальный поток = оптимум ротора / EU на литр, до 125 %'),
        ('Needs a muffler: causes 160 Pollution per second', 'Нужен глушитель: загрязнение 160 в секунду')], active='LARGETURBINE_SS_ACTIVE5')
    # :1249 "CPC/PMP/BPB": EV hull, large stainless steel pipes, Data circuits, stainless steel gears
    shaped('multi/large_gas_turbine', f'{NS}:large_gas_turbine', 1, ['CPC', 'PMP', 'BPB'],
           {'M': f'{NS}:hull_ev', 'B': f'{NS}:pipe_large_stainless_steel', 'C': '#c:circuits/data', 'P': tag_of('gear', 'StainlessSteel')})

    # --- D121: large plasma generator: GT_MetaTileEntity_LargeTurbine_Plasma, on tungstensteel turbine casings (GT_Loader_MetaTileEntities:74, GT_MachineRecipeLoader:1399) ---------
    casing('turbine_casing_tungstensteel', 'MACHINE_CASING_ROBUST_TUNGSTENSTEEL', 'gt.blockcasings4.12.name', 'Tungstensteel Turbine Casing')
    name(f'block.{NS}.turbine_casing_tungstensteel', 'Tungstensteel Turbine Casing', 'Вольфрамостальной турбинный корпус')
    shaped('multi/turbine_casing_tungstensteel', f'{NS}:turbine_casing_tungstensteel', 2, ['PhP', 'PFP', 'PwP'], {'P': tag_of('plate', 'TungstenSteel'), 'F': f'{NS}:turbine_casing'})
    mrec('assembler', 'turbine_casing_tungstensteel', [ing(tag_of('plate', 'TungstenSteel'), 6), ing(f'{NS}:turbine_casing')], [out(f'{NS}:turbine_casing_tungstensteel', 2)], 50, 16)
    controller('large_plasma_turbine', 'turbine_casing_tungstensteel', 'LARGETURBINE_TU5', 'gt.blockmachines.multimachine.largeplasmaturbine.name', 'Large Plasma Generator', [
        ('Shape of the large steam turbine on tungstensteel turbine casings', 'Форма большой паровой турбины на турбинных корпусах из вольфрамостали'),
        ('Burns plasma from input hatches; a rotor in the controller', 'Сжигает плазму из входных люков; ротор — в контроллере'),
        ('Optimal flow = rotor optimum x 40 / EU per litre, up to 125 %', 'Оптимальный поток = оптимум ротора × 40 / EU на литр, до 125 %'),
        ('The cooled fluid goes to the output hatch', 'Остывшая жидкость уходит в выходной люк')], active='LARGETURBINE_TU_ACTIVE5')
    name(f'block.{NS}.large_plasma_turbine', 'Large Plasma Generator', 'Большой плазменный генератор')
    shaped('multi/large_plasma_turbine', f'{NS}:large_plasma_turbine', 1, ['CPC', 'PMP', 'BPB'],   # :1251
           {'M': f'{NS}:hull_uv', 'B': f'{NS}:pipe_huge_tungsten_steel', 'C': '#c:circuits/master', 'P': tag_of('gear', 'TungstenSteel')})

    fluids = json.load(open(os.path.join(g['RES'], NS, 'gt_fluids.json'), encoding='utf-8'))
    fluid_ids = {f['id'] for f in fluids}
    by_mat = {}
    for f in fluids:
        if 'material' in f and not f['id'].startswith(('hydrocracked_', 'steamcracked_')):
            by_mat.setdefault(f['material'], []).append(f['id'])

    def fluid_of(mat, kind):
        if mat == 'Water':
            return 'minecraft:water'
        if mat == 'Creosote':
            return f'{NS}:creosote'   # IIS 2 creosote is its own fluid (D74), not a GT fluid
        if kind in ('hydro', 'steam'):
            fid = ('hydrocracked_' if kind == 'hydro' else 'steamcracked_') + mat.lower()
            return f'{NS}:{fid}' if fid in fluid_ids else None
        c = by_mat.get(mat, [])
        if not c:
            return None
        pref = [x for x in c if x.startswith('gas_')] if kind == 'gas' else [x for x in c if x.startswith('liquid_')]
        return f'{NS}:{(pref or sorted(c, key=len))[0]}'

    # GT_Proxy:1714-1729 addAutoGenerated*CrackedFluid: 1000 L + 1000 L hydrogen / steam -> 1000 L cracked (20 t, 120 / 240 EU/t);
    # the circuit (1 / 2) only tells hydrogen from steam, the second fluid already does that (the cracker has no item input)
    for f in fluids:
        fid = f['id']
        for pre, second, eut in (('hydrocracked_', {'ingredient': 'iis:hydrogen', 'amount': 1000}, 120),
                                 ('steamcracked_', {'ingredient': 'iis:steam', 'amount': 1000}, 240)):
            if not fid.startswith(pre) or 'material' not in f:
                continue
            base = fluid_of(f['material'], 'gas') or fluid_of(f['material'], 'fluid')
            if not base:
                continue
            rec[f'cracking/{fid}'] = {'type': f'{NS}:cracking', 'duration': 20, 'eut': eut,
                                      'fluid_inputs': [{'ingredient': base, 'amount': 1000}, second],
                                      'fluid_outputs': [{'id': f'{NS}:{fid}', 'amount': 1000}]}

    # GT_MachineRecipeLoader: addDistillationTowerRecipe / addUniversalDistillationRecipe (fluid, fluid[], item, t, EU/t)
    import re
    gt_src = os.environ.get('IIS_GT5_SRC', 'C:/Users/Godkiller/AppData/Local/Temp/gt5src/src/main/java/gregtech')
    src = open(os.path.join(gt_src, 'loaders', 'postload', 'GT_MachineRecipeLoader.java'), encoding='utf-8', errors='replace').read()
    src = re.sub(r'//[^\n]*', '', src)

    def split_args(t):
        parts, depth, cur = [], 0, ''
        for ch in t:
            if ch in '([{':
                depth += 1
            elif ch in ')]}':
                depth -= 1
            if ch == ',' and depth == 0:
                parts.append(cur.strip())
                cur = ''
            else:
                cur += ch
        if cur.strip():
            parts.append(cur.strip())
        return parts

    def num(t):
        t = t.replace('L', '').replace(' ', '')
        return int(eval(t, {'__builtins__': {}})) if re.fullmatch(r'[\d*+()]+', t) else None

    def fl(t):
        t = re.sub(r'\s+', '', t)
        m = re.fullmatch(r'(?:Materials\.)?(\w+)\.(getFluid|getGas|getHydroCracked|getSteamCracked)\(([^)]*)\)', t)
        if m:
            kind = {'getFluid': 'fluid', 'getGas': 'gas', 'getHydroCracked': 'hydro', 'getSteamCracked': 'steam'}[m.group(2)]
            return fluid_of(m.group(1), kind), num(m.group(3))
        m = re.fullmatch(r'GT_ModHandler\.getDistilledWater\(([^)]*)\)', t)
        if m:
            return f'{NS}:distilled_water', num(m.group(1))
        return None, None

    def item(t):
        t = re.sub(r'\s+', '', t)
        if t in ('null', 'GT_Values.NI'):
            return {}
        m = re.fullmatch(r'(?:Materials\.)?(\w+)\.(getDust|getDustSmall|getDustTiny)\(([^)]*)\)', t)
        if m:
            form = {'getDust': 'dust', 'getDustSmall': 'small_dust', 'getDustTiny': 'tiny_dust'}[m.group(2)]
            return {'id': ref(form, m.group(1)), 'count': num(m.group(3))}
        m = re.fullmatch(r'GT_OreDictUnificator\.get\(OrePrefixes\.(dust|dustSmall|dustTiny),Materials\.(\w+),(\w+)\)', t)
        if m:
            form = {'dust': 'dust', 'dustSmall': 'small_dust', 'dustTiny': 'tiny_dust'}[m.group(1)]
            return {'id': ref(form, m.group(2)), 'count': num(m.group(3))}
        return None

    tower_skipped = 0
    for mm in re.finditer(r'(addDistillationTowerRecipe|addUniversalDistillationRecipe)\(', src):
        line = src.count('\n', 0, mm.start()) + 1
        i, depth = mm.end(), 1
        while depth:
            depth += {'(': 1, ')': -1}.get(src[i], 0)
            i += 1
        args = split_args(src[mm.end():i - 1])
        if len(args) != 5 or not args[1].replace(' ', '').startswith('newFluidStack[]{'):
            tower_skipped += 1
            continue
        fin, ain = fl(args[0])
        outs = [fl(x) for x in split_args(args[1][args[1].index('{') + 1:args[1].rindex('}')])]
        it, dur, eut = item(args[2]), num(args[3]), num(args[4])
        if not fin or not ain or any(not f_ or not a for f_, a in outs) or it is None or (it and not it['id']) or dur is None or eut is None:
            tower_skipped += 1
            continue
        r = {'type': f'{NS}:distillation_tower', 'duration': max(1, dur), 'eut': eut,
             'fluid_inputs': [{'ingredient': fin, 'amount': ain}], 'fluid_outputs': [{'id': f_, 'amount': a} for f_, a in outs]}
        if it:
            r['outputs'] = [it]
        rec[f'distillation_tower/gt_{line}'] = r
    stats['tower_skipped'] = tower_skipped


    # --- D105: HP steam turbine, heat exchanger, processing array, charcoal pile, cleanroom ------------------------
    def cube_block(bid, sides, top=None, bottom=None, render=None, loot=True):
        """Block model with own side/top/bottom textures (iis:block/... ids or minecraft ones)."""
        m = {'parent': 'minecraft:block/cube', 'textures': {'particle': sides, 'north': sides, 'south': sides, 'east': sides, 'west': sides,
                                                             'up': top or sides, 'down': bottom or sides}}
        if render:
            m['render_type'] = render
        write(os.path.join(A, 'models', 'block', bid + '.json'), m)
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        if loot:
            simple(bid)
        else:
            write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{bid}'}})

    def top_controller(bid, casing_tex, overlay, key, en, lines, both=False):
        """Controller with the GT overlay on top (charcoal pile; cleanroom also on the bottom), plain sides."""
        side = f'{NS}:block/multi/{casing_tex}'
        base = Image.open(tex_out(f'block/multi/{casing_tex}.png')).convert('RGBA')
        for suf, ov in (('', overlay), ('_active', overlay + '_ACTIVE')):
            o = Image.open(os.path.join(BI, ov + '.png')).convert('RGBA')
            n = max(1, o.height // 16)
            outi = Image.new('RGBA', (16, 16 * n))
            for f in range(n):
                outi.paste(Image.alpha_composite(base, o.crop((0, 16 * f, 16, 16 * f + 16))), (0, 16 * f))
            outi.save(tex_out(f'block/multi/{bid}_top{suf}.png'))
            meta = os.path.join(BI, ov + '.png.mcmeta')
            if n > 1 and os.path.exists(meta):
                shutil.copy(meta, tex_out(f'block/multi/{bid}_top{suf}.png.mcmeta'))
            top = f'{NS}:block/multi/{bid}_top{suf}'
            write(os.path.join(A, 'models', 'block', bid + suf + '.json'), {'parent': 'minecraft:block/cube', 'textures': {
                'particle': side, 'north': side, 'south': side, 'east': side, 'west': side, 'up': top, 'down': top if both else side}})
        variants = {}
        for facing in ('north', 'east', 'south', 'west'):
            for active in (False, True):
                variants[f'facing={facing},active={str(active).lower()}'] = {'model': f'{NS}:block/{bid}' + ('_active' if active else '')}
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': variants})
        simple(bid)
        name(f'block.{NS}.{bid}', en, ru(key, en))
        tooltip(bid, lines)

    def raw(rtype, rid, r):
        r = dict(r)
        r['type'] = f'{NS}:{rtype}'
        rec.setdefault(f'{rtype}/{rid}', r)

    # titanium turbine casing: GT_Loader_MetaTileEntities:73 "PhP/PFP/PwP" titanium plates + turbine casing -> 2; :1398 assembler
    casing('turbine_casing_titanium', 'MACHINE_CASING_STABLE_TITANIUM', 'gt.blockcasings4.11.name', 'Titanium Turbine Casing')
    shaped('multi/turbine_casing_titanium', f'{NS}:turbine_casing_titanium', 2, ['PhP', 'PFP', 'PwP'],
           {'P': tag_of('plate', 'Titanium'), 'F': f'{NS}:turbine_casing'})
    mrec('assembler', 'turbine_casing_titanium', [ing(tag_of('plate', 'Titanium'), 6), ing(f'{NS}:turbine_casing')],
         [out(f'{NS}:turbine_casing_titanium', 2)], 50, 16)
    controller('large_hp_steam_turbine', 'turbine_casing_titanium', 'LARGETURBINE_TI5', 'gt.blockmachines.multimachine.largehpturbine.name',
               'Large HP Steam Turbine', [
                   ('Shape of the large steam turbine on titanium turbine casings', 'Форма большой паровой турбины на титановых турбинных корпусах'),
                   ('Burns superheated steam: 1 L = 1 EU, up to 125 % of the optimal flow', 'Перегретый пар: 1 л = 1 EU, до 125 % оптимального потока'),
                   ('Spent steam goes to output hatches; plain steam is wasted', 'Отработанный пар — в выходные люки; обычный пар пропадает'),
                   ('A rotor in the controller; no pollution', 'Ротор — в контроллере; не загрязняет')], active='LARGETURBINE_TI_ACTIVE5')
    # GT_Loader_MetaTileEntities:1250 "CPC/PMP/BPB": IV hull, large titanium pipes, Elite circuits, titanium gears
    shaped('multi/large_hp_steam_turbine', f'{NS}:large_hp_steam_turbine', 1, ['CPC', 'PMP', 'BPB'],
           {'M': f'{NS}:hull_iv', 'B': f'{NS}:pipe_large_titanium', 'C': '#c:circuits/elite', 'P': tag_of('gear', 'Titanium')})

    controller('heat_exchanger', 'stable_titanium_casing', 'OVERLAY_FRONT_HEAT_EXCHANGER', 'gt.blockmachines.multimachine.heatexchanger.name',
               'Large Heat Exchanger', [
                   ('3x4x3; controller in the middle of the bottom front side', 'Размер 3×4×3; контроллер — в середине передней стороны внизу'),
                   ('Middle column: lava input hatch, 2 titanium pipe casings, output hatch', 'Средний столб: люк лавы, 2 корпуса с титановыми трубами, выходной люк'),
                   ('At least 24 stable titanium casings; distilled water in, steam out', 'Не меньше 24 стабильных титановых корпусов; вход воды, выход пара'),
                   ('Lava to pahoehoe lava; 1000 L/s or more gives superheated steam', 'Лава → лава пахоэхоэ; от 1000 л/с — перегретый пар'),
                   ('Circuit 1-25 lowers that threshold by 150 L/s a step for 1.5 % output', 'Схема 1–25 снижает порог на 150 л/с за ступень ценой 1,5 % выхода'),
                   ('Runs dry without distilled water - explodes', 'Без дистиллированной воды взрывается')])
    # :1294 "WCW/CMC/WCW": titanium pipe casing, medium titanium pipes, EV pumps
    shaped('multi/heat_exchanger', f'{NS}:heat_exchanger', 1, ['WCW', 'CMC', 'WCW'],
           {'M': f'{NS}:titanium_pipe_casing', 'C': f'{NS}:pipe_medium_titanium', 'W': f'{NS}:electric_pump_ev'})
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'LargeHeatExchanger.png'), tex_out('gui/multi/heat_exchanger.png'))
    name('gui.iis.heat_exchanger.threshold', 'Superheated at: %s L/s', 'Перегрев от: %s л/с')

    controller('processing_array', 'robust_tungstensteel_casing', 'OVERLAY_FRONT_PROCESSING_ARRAY', 'gt.blockmachines.multimachine.processingarray.name',
               'Processing Array', [
                   ('3x3x3 hollow, controller in the middle of a side', 'Куб 3×3×3, пустой внутри; контроллер — в середине стенки'),
                   ('At least 16 robust tungstensteel casings; the rest: hatches', 'Не меньше 16 прочных вольфрамостальных корпусов; остальное — люки'),
                   ('Put up to 16 single-block machines of one kind into the controller', 'В контроллер — до 16 одноблочных машин одного вида'),
                   ('Runs their recipes at their voltage, as many at once as machines', 'Работает по их рецептам и напряжению, столько сразу, сколько машин')])
    # D121: GT_MetaTileEntity_AdvancedProcessingArray; Casing_RobustHSSG: GT_Loader_MetaTileEntities:69, GT_MachineRecipeLoader:1881; craft :1238
    casing('robust_hssg_casing', 'MACHINE_CASING_ROBUST_HSSG', 'gt.blockcasings8.2.name', 'Robust HSS-G Machine Casing')
    name(f'block.{NS}.robust_hssg_casing', 'Robust HSS-G Machine Casing', 'Прочный корпус из HSS-G')
    shaped('multi/robust_hssg_casing', f'{NS}:robust_hssg_casing', 2, ['PhP', 'EFE', 'PwP'], {'P': tag_of('plate', 'HSSG'), 'E': tag_of('plate', 'Europium'), 'F': f'{NS}:robust_tungstensteel_casing'})
    mrec('assembler', 'robust_hssg_casing', [ing(tag_of('plate', 'HSSG'), 4), ing(tag_of('plate', 'Europium'), 2), ing(f'{NS}:robust_tungstensteel_casing')], [out(f'{NS}:robust_hssg_casing', 2)], 50, 16)
    controller('advanced_processing_array', 'robust_hssg_casing', 'OVERLAY_FRONT_PROCESSING_ARRAY', 'gt.blockmachines.multimachine.advancedprocessingarray.name',
               'Advanced Processing Array', [
                   ('5x5x5 hollow, controller in the middle of the front of the second layer', 'Куб 5×5×5, пустой внутри; контроллер — в середине передней стенки, на втором слое'),
                   ('At least 70 robust HSS-G casings; the rest: hatches', 'Не меньше 70 прочных корпусов из HSS-G; остальное — люки'),
                   ('Put up to 64 single-block machines of one kind into the controller', 'В контроллер — до 64 одноблочных машин одного вида'),
                   ('Runs their recipes at their voltage, as many at once as machines', 'Работает по их рецептам и напряжению, столько сразу, сколько машин')])
    name(f'block.{NS}.advanced_processing_array', 'Advanced Processing Array', 'Улучшенный обрабатывающий массив')
    shaped('multi/advanced_processing_array', f'{NS}:advanced_processing_array', 1, ['RRR', 'CPC', 'CPC'], {'R': f'{NS}:robot_arm_luv', 'C': '#c:circuits/master', 'P': f'{NS}:processing_array'})
    # GT_MachineRecipeLoader:71 needs the EV digital transformer (not ported yet) - the controller has no recipe for now
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'ProcessingArray.png'), tex_out('gui/multi/processing_array.png'))
    name('gui.iis.processing_array.no_machine', 'Insert single-block machines', 'Вставьте одноблочные машины')
    name('gui.iis.processing_array.parallel', 'Machines working: %s', 'Работает машин: %s')

    # charcoal pile igniter: casing = bronze plated bricks, rock breaker overlay on top; brittle charcoal looks like a coal block
    top_controller('charcoal_pile', 'bronze_plated_bricks', 'OVERLAY_TOP_ROCK_BREAKER', 'gt.blockmachines.multimachine.charcoalpile.name',
                   'Charcoal Pile Igniter', [
                       ('Place on top of a log pile, up to 11x6x11', 'Ставится сверху на кучу брёвен до 11×6×11'),
                       ('Bricks under the logs, dirt or grass around and above', 'Под брёвнами — кирпичи, вокруг и сверху — земля или дёрн'),
                       ('No air between the logs', 'Без пустот между брёвнами'),
                       ('Turns all the logs into brittle charcoal', 'Превращает все брёвна в ломкий уголь'),
                       ('Causes 100 Pollution per second', 'Загрязнение: 100 в секунду')])
    # GT_Loader_MetaTileEntities:1304 "EME/CCC": bricked bronze hull, wrought iron nuggets, flint
    shaped('multi/charcoal_pile', f'{NS}:charcoal_pile', 1, ['EME', 'CCC'],
           {'M': f'{NS}:hull_bronze_bricked', 'E': tag_of('nugget', 'WroughtIron'), 'C': 'minecraft:flint'})
    write(os.path.join(A, 'models', 'block', 'brittle_charcoal.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'minecraft:block/coal_block'}})
    write(os.path.join(A, 'blockstates', 'brittle_charcoal.json'), {'variants': {'': {'model': f'{NS}:block/brittle_charcoal'}}})
    write(os.path.join(A, 'items', 'brittle_charcoal.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/brittle_charcoal'}})
    # GT_Block_Reinforced#dropBlockAsItemWithChance: 1-2 charcoal; axe, harvest level 1
    write(os.path.join(D, NS, 'loot_table', 'blocks', 'brittle_charcoal.json'), {
        'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/brittle_charcoal',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                   'entries': [{'type': 'minecraft:item', 'name': 'minecraft:charcoal', 'functions': [
                       {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}}]}]}]})
    block_tags.setdefault('minecraft:mineable/axe', []).append(f'{NS}:brittle_charcoal')
    block_tags['minecraft:needs_stone_tool'].append(f'{NS}:brittle_charcoal')
    name(f'block.{NS}.brittle_charcoal', 'Brittle Charcoal', ru('gt.blockreinforced.4.name', 'Ломкий уголь'))
    g['shapeless']('multi/charcoal_from_brittle', 'minecraft:charcoal', 1, [f'{NS}:brittle_charcoal'])

    # cleanroom: plascrete (GT_Block_Reinforced:2), plascrete window (GT_Block_Glass_Casings:0, the GT png is broken - drawn here),
    # filter machine casing (GT_Block_Casings3:11)
    first(os.path.join(BI, 'BLOCK_PLASCRETE.png')).save(tex_out('block/multi/plascrete.png'))
    cube_block('plascrete', f'{NS}:block/multi/plascrete')
    name(f'block.{NS}.plascrete', 'Plascrete', ru('gt.blockreinforced.2.name', 'Пласкрит'))
    plas = first(os.path.join(BI, 'BLOCK_PLASCRETE.png'))
    win = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for yy in range(16):
        for xx in range(16):
            if xx in (0, 15) or yy in (0, 15):
                win.putpixel((xx, yy), plas.getpixel((xx, yy)))
            elif (xx + yy) % 7 == 0 and 3 < xx < 12:
                win.putpixel((xx, yy), (235, 240, 245, 110))
            else:
                win.putpixel((xx, yy), (200, 215, 225, 45))
    win.save(tex_out('block/multi/plascrete_window.png'))
    cube_block('plascrete_window', f'{NS}:block/multi/plascrete_window', render='minecraft:translucent')
    name(f'block.{NS}.plascrete_window', 'Plascrete Window', ru('gt.blockglass1.0.name', 'Окно из пласкрита'))
    casing('filter_casing', 'MACHINE_CASING_VENT', 'gt.blockcasings3.11.name', 'Filter Machine Casing')
    # GT_MachineRecipeLoader:343 black steel frame + plastic dust + 144 L concrete -> plascrete (200 t, 48 EU/t)
    raw('assembler', 'plascrete', {'inputs': [ing('#c:frames/black_steel'), ing(tag_of('dust', 'Plastic'))], 'outputs': [out(f'{NS}:plascrete')],
                                  'fluid_inputs': [{'ingredient': f'{NS}:molten_concrete', 'amount': 144}], 'duration': 200, 'eut': 48})
    # :347 plascrete + glass plate + 100 L glue -> window (600 t, 4 EU/t); the "BPB/PGP/BPB" craft with a glue cell is left out
    raw('assembler', 'plascrete_window', {'inputs': [ing(f'{NS}:plascrete'), ing(tag_of('plate', 'Glass'))], 'outputs': [out(f'{NS}:plascrete_window')],
                                         'fluid_inputs': [{'ingredient': f'{NS}:glue', 'amount': 100}], 'duration': 600, 'eut': 4})
    # GT_Loader_MetaTileEntities:85 "PPP/SSS/MFV": iron bars, item filters, MV motor, steel frame, steel rotor -> 2
    shaped('multi/filter_casing', f'{NS}:filter_casing', 2, ['PPP', 'SSS', 'MFV'],
           {'P': 'minecraft:iron_bars', 'S': f'{NS}:item_filter', 'M': f'{NS}:electric_motor_mv', 'F': '#c:frames/steel', 'V': tag_of('rotor', 'Steel')})
    top_controller('cleanroom', 'plascrete', 'OVERLAY_TOP_CLEANROOM', 'gt.blockmachines.multimachine.cleanroom.name', 'Cleanroom Controller', [
        ('Top center of a hollow room from 3x4x3 to 15x15x15, square plan', 'Центр потолка пустой комнаты от 3×4×3 до 15×15×15, квадратной в плане'),
        ('Ceiling: filter machine casings; edges: plascrete', 'Потолок — фильтрующие корпуса, рёбра — пласкрит'),
        ('Walls, floor: plascrete, windows, an iron door (two from 7x7)', 'Стены и пол: пласкрит, окна, железная дверь (от 7×7 — две)'),
        ('One maintenance and one energy hatch; machine hulls up to 1/3 of a wall', 'Один люк обслуживания и один энергетический; корпуса машин — до трети стены'),
        ('3 EU/t per ceiling block; cleaning 80 s per block of height', '3 EU/t на блок потолка; очистка — 80 с на блок высоты'),
        ('Keep the door closed; machines inside make cleanroom recipes', 'Держите дверь закрытой; машины внутри делают рецепты чистой комнаты')], both=True)
    # :1366 "FFF/RHR/MCM": HV hull, item filters, stainless steel rotors, HV motors, Advanced circuit
    shaped('multi/cleanroom', f'{NS}:cleanroom', 1, ['FFF', 'RHR', 'MCM'],
           {'H': f'{NS}:hull_hv', 'F': f'{NS}:item_filter', 'R': tag_of('rotor', 'StainlessSteel'), 'M': f'{NS}:electric_motor_hv',
            'C': '#c:circuits/advanced'})
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'MultiblockDisplay.png'), tex_out('gui/multi/cleanroom.png'))
    name('gui.iis.cleanroom.cleanliness', 'Cleanliness: %s%%', 'Чистота: %s%%')
    # ProcessingCrafting:52 (craftingLensWhite) CPU wafer - cleanroom only; GT_MachineRecipeLoader:511 cutter -> 8 CPU chips
    raw('laser_engraver', 'wafer_cpu', {'inputs': [ing(f'{NS}:silicon_wafer')], 'outputs': [out(f'{NS}:wafer_cpu')], 'duration': 900, 'eut': 120,
                                       'keep': ['#iis:crafting_lens/white'], 'cleanroom': True})

    # --- D106: mining pipes, drilling rigs, ore drilling plants, concrete backfillers --------------------------------
    # mining pipe and tip: own art (IC2 miningPipe / miningPipeTip are not ported, D5), a thin column
    def pipe_tex(name, tip):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for yy in range(16):
            for xx in range(16):
                base = 150 + (12 if xx in (6, 7) else 0) - (25 if xx in (5, 10) else 0)
                if tip and yy >= 12:
                    base = 70 + 10 * ((xx + yy) % 2)
                if yy % 4 == 0 and not (tip and yy >= 12):
                    base -= 30
                img.putpixel((xx, yy), (base, base, base + 8, 255))
        img.save(tex_out(f'block/multi/{name}.png'))
    for bid, tip in (('mining_pipe', False), ('mining_pipe_tip', True)):
        pipe_tex(bid, tip)
        t = f'{NS}:block/multi/{bid}'
        faces = {d: {'texture': '#side', 'uv': [5, 0, 11, 16]} for d in ('north', 'south', 'east', 'west')}
        faces['up'] = {'texture': '#side', 'uv': [5, 5, 11, 11]}
        faces['down'] = {'texture': '#side', 'uv': [5, 5, 11, 11]}
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/block', 'textures': {'particle': t, 'side': t},
                                                                   'elements': [{'from': [5, 0, 5], 'to': [11, 16, 11], 'faces': faces}]})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {
            'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{bid}',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:mining_pipe'}]}]})
        block_tags['minecraft:mineable/pickaxe'].append(f'{NS}:{bid}')
    write(os.path.join(A, 'items', 'mining_pipe.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/mining_pipe'}})
    name(f'block.{NS}.mining_pipe', 'Mining Pipe', 'Шахтная труба')
    name(f'block.{NS}.mining_pipe_tip', 'Mining Pipe Tip', 'Наконечник шахтной трубы')
    # GT_IndustialCraftRecipesLoader:155-163, scripts/GT_IC2.zs:321-323 (IIS 1)
    shaped('multi/mining_pipe', f'{NS}:mining_pipe', 1, ['hPf'], {'P': f'{NS}:pipe_small_steel'})
    mrec('wiremill', 'mining_pipe', [ing(f'{NS}:pipe_tiny_steel')], [out(f'{NS}:mining_pipe')], 200, 16)
    for size, n, dur in (('tiny', 1, 1), ('small', 2, 4), ('medium', 4, 8), ('large', 8, 16)):
        raw('extruder', f'mining_pipe_from_{size}', {'inputs': [ing(f'{NS}:pipe_{size}_steel')], 'outputs': [out(f'{NS}:mining_pipe', n)],
                                                    'duration': dur, 'eut': 64, 'keep': [f'{NS}:shape_extruder_pipe_tiny']})
    mrec('macerator', 'mining_pipe', [ing(f'{NS}:mining_pipe')], [out(f'{NS}:small_dust_steel', 2)], 300, 2)   # IC2 macerator -> GT (addIC2RecipesToGT: 300 t, 2 EU/t)

    first(os.path.join(BI, 'CONCRETE_LIGHT_STONE.png')).save(tex_out('block/multi/light_concrete.png'))
    cube_block('light_concrete', f'{NS}:block/multi/light_concrete')
    name(f'block.{NS}.light_concrete', 'Light Concrete', ru('gt.blockconcretes.8.name', 'Светлый бетон'))

    base_lines = lambda casing_en, casing_ru, frame_en, frame_ru: [
        ('3x7x3; controller in the middle of the front of the bottom 3x3', 'Размер 3×7×3; контроллер — в середине передней стороны нижней площадки 3×3'),
        (f'Bottom: {casing_en} or hatches; a pillar of 3 casings in the middle', f'Низ: {casing_ru} или люки; в центре — столб из 3 корпусов'),
        (f'{frame_en} frame boxes around the pillar and 3 on top', f'{frame_ru} — вокруг столба и 3 сверху'),
        ('Lowers mining pipes from the controller slot down to bedrock', 'Опускает шахтные трубы из слота контроллера до бедрока')]
    # D121: Casing_MiningOsmiridium: GT_Loader_MetaTileEntities:70, GT_MachineRecipeLoader:1394
    casing('mining_osmiridium_casing', 'MACHINE_CASING_MINING_OSMIRIDIUM', 'gt.blockcasings8.3.name', 'Mining Osmiridium Casing')
    name(f'block.{NS}.mining_osmiridium_casing', 'Mining Osmiridium Casing', 'Горный осмиридиевый корпус')
    shaped('multi/mining_osmiridium_casing', f'{NS}:mining_osmiridium_casing', 2, ['PhP', 'PFP', 'PwP'], {'P': tag_of('plate', 'Osmiridium'), 'F': '#c:frames/osmiridium'})
    mrec('assembler', 'mining_osmiridium_casing', [ing(tag_of('plate', 'Osmiridium'), 6), ing('#c:frames/osmiridium')], [out(f'{NS}:mining_osmiridium_casing', 2)], 50, 16)
    tiers = [('solid_steel_casing', 'solid steel casings', 'прочные стальные корпуса', 'Steel', 'Стальные рамки'),
             ('stable_titanium_casing', 'stable titanium casings', 'стабильные титановые корпуса', 'Titanium', 'Титановые рамки'),
             ('robust_tungstensteel_casing', 'robust tungstensteel casings', 'прочные вольфрамостальные корпуса', 'Tungstensteel',
              'Вольфрамостальные рамки'),
             ('mining_osmiridium_casing', 'mining osmiridium casings', 'горные осмиридиевые корпуса', 'Osmiridium', 'Осмиридиевые рамки')]
    roman = ['I', 'II', 'III', 'IV']
    volts = ['MV', 'HV', 'EV', 'IV']
    # oil drilling rigs: GT_Loader_MetaTileEntities:1314-1316 "WWW/EME/CCC"
    oil_craft = [('hull_mv', 'steel', '#c:circuits/good', 'electric_motor_mv'), ('oil_drill_1', 'titanium', '#c:circuits/advanced', 'electric_motor_hv'),
                 ('oil_drill_2', 'tungsten_steel', '#c:circuits/data', 'electric_motor_ev')]
    for i, (rng, mt) in enumerate(((1, 'MV'), (3, 'HV'), (6, 'EV'))):
        cas, cen, cru, fen, fru = tiers[i]
        controller(f'oil_drill_{i + 1}', cas, 'OVERLAY_FRONT_ORE_DRILL', f'gt.blockmachines.multimachine.oildrill{i + 1}.name', f'Oil Drilling Rig {roman[i]}',
                   base_lines(cen, cru, fen, fru) + [
                       (f'Maintenance and output hatch, {mt}+ energy hatch', f'Люк обслуживания, выходной люк, энергия от {mt}'),
                       (f'Pumps the underground fluid of a {rng}x{rng} chunk field', f'Качает подземную жидкость поля {rng}×{rng} чанков')])
        m, frame, circ, motor = oil_craft[i]
        shaped(f'multi/oil_drill_{i + 1}', f'{NS}:oil_drill_{i + 1}', 1, ['WWW', 'EME', 'CCC'],
               {'M': f'{NS}:{m}', 'W': f'#c:frames/{frame}', 'E': circ, 'C': f'{NS}:{motor}'})
    # ore drilling plants: :1323-1327
    ore_craft = [('hull_ev', 'titanium', '#c:circuits/data', 'electric_motor_ev'), ('ore_drill_1', 'tungsten_steel', '#c:circuits/elite', 'electric_motor_iv'),
                 ('ore_drill_2', 'osmiridium', '#c:circuits/master', 'electric_motor_luv'), ('ore_drill_3', 'tritanium', '#c:circuits/ultimate', 'electric_motor_zpm')]   # :1330, :1333 (D121)
    for i, (rad, mt) in enumerate(((48, 'MV'), (64, 'HV'), (96, 'EV'), (144, 'IV'))):
        cas, cen, cru, fen, fru = tiers[i]
        controller(f'ore_drill_{i + 1}', cas, 'OVERLAY_FRONT_ORE_DRILL', f'gt.blockmachines.multimachine.oredrill{i + 1}.name', f'Ore Drilling Plant {roman[i]}',
                   base_lines(cen, cru, fen, fru) + [
                       (f'Maintenance, drilling fluid input hatch, output bus, {mt}+ energy', f'Обслуживание, люк буровой жидкости, выходная шина, энергия от {mt}'),
                       (f'Mines ores in a radius of {rad} blocks, 2000 L of drilling fluid a cycle', f'Добывает руды в радиусе {rad} блоков, 2000 л буровой жидкости за цикл'),
                       ('Grinds them at once: every output x1-4', 'Сразу размалывает: каждый выход ×1–4')])
        m, frame, circ, motor = ore_craft[i]
        shaped(f'multi/ore_drill_{i + 1}', f'{NS}:ore_drill_{i + 1}', 1, ['WWW', 'EME', 'CCC'],
               {'M': f'{NS}:{m}', 'W': f'#c:frames/{frame}', 'E': circ, 'C': f'{NS}:{motor}'})
    # concrete backfillers: :1318-1321 "WPW/EME/CQC"
    bf_craft = [('hull_mv', 'steel', '#c:circuits/good', 'electric_motor_mv', 'electric_pump_mv'),
                ('concrete_backfiller_1', 'titanium', '#c:circuits/data', 'electric_motor_ev', 'electric_pump_ev')]
    for i, (rad, mt, key, en) in enumerate(((16, 'MV', 'concretebackfiller1', 'Concrete Backfiller'),
                                            (64, 'EV', 'concretebackfiller3', 'Advanced Concrete Backfiller'))):
        cas, cen, cru, fen, fru = tiers[i]
        controller(f'concrete_backfiller_{i + 1}', cas, 'OVERLAY_FRONT_ORE_DRILL', f'gt.blockmachines.multimachine.{key}.name', en,
                   base_lines(cen, cru, fen, fru) + [
                       (f'Maintenance and input hatch, {mt}+ energy hatch', f'Люк обслуживания, входной люк, энергия от {mt}'),
                       (f'On the way up fills air within {rad} blocks with light concrete', f'На подъёме заливает пустоты в радиусе {rad} блоков светлым бетоном'),
                       ('144 L of molten concrete a block', '144 л расплава бетона на блок')])
        m, frame, circ, motor, pump = bf_craft[i]
        shaped(f'multi/concrete_backfiller_{i + 1}', f'{NS}:concrete_backfiller_{i + 1}', 1, ['WPW', 'EME', 'CQC'],
               {'M': f'{NS}:{m}', 'W': f'#c:frames/{frame}', 'E': circ, 'C': f'{NS}:{motor}', 'P': f'{NS}:pipe_large_steel', 'Q': f'{NS}:{pump}'})
    shutil.copy(os.path.join(GT, 'gui', 'multimachines', 'DrillingRig.png'), tex_out('gui/multi/drill.png'))
    for k, en, ruu in (('gui.iis.drill.state.0', 'Lowering pipes', 'Спуск труб'), ('gui.iis.drill.state.1', 'At the bottom', 'У дна'),
                       ('gui.iis.drill.state.2', 'Lifting pipes', 'Подъём труб'), ('gui.iis.drill.depth', 'Depth: %s, pipes: %s', 'Глубина: %s, труб: %s')):
        name(k, en, ruu)

    # --- D107: water filters, pump intakes, water pumps, fluid filter recipes -----------------------------------------
    # GT_CraftingRecipeLoader:493 primitive filter "PSP/WOW/PSP"; GT_MachineRecipeLoader:2599 simple filter x4 (80 t, 8 EU/t);
    # :2600 fluid filter needs IC2 carbon fibre and glass fibre - not ported, the item has no recipe yet
    shaped('multi/primitive_fluid_filter', f'{NS}:primitive_fluid_filter', 1, ['PSP', 'WOW', 'PSP'],
           {'P': tag_of('plate', 'Paper'), 'S': 'minecraft:string', 'W': '#minecraft:wool', 'O': f'{NS}:pipe_medium_wood'})
    mrec('assembler', 'simple_fluid_filter', [ing(f'{NS}:pipe_small_copper'), ing(tag_of('plate', 'Paper'), 16), ing(tag_of('dust', 'Carbon'), 8)],
         [out(f'{NS}:simple_fluid_filter', 4)], 80, 8)
    tiers6 = ['ulv', 'lv', 'mv', 'hv', 'ev', 'iv']
    for i, t in enumerate(tiers6):
        T = t.upper()
        first(os.path.join(BI, f'MACHINE_CASING_PUMP_FILTER_{T}.png')).save(tex_out(f'block/multi/pump_intake_{t}.png'))
        first(os.path.join(BI, f'MACHINE_CASING_PUMP_FILTER_{T}_UP.png')).save(tex_out(f'block/multi/pump_intake_{t}_up.png'))
        cube_block(f'pump_intake_{t}', f'{NS}:block/multi/pump_intake_{t}', top=f'{NS}:block/multi/pump_intake_{t}_up')
        name(f'block.{NS}.pump_intake_{t}', ('Primitive' if i == 0 else T) + ' Pump Intake', ru(f'gt.blockcasings8.{10 + i}.name', f'Заборник насоса {T}'))
        first(os.path.join(BI, f'MACHINE_CASING_PUMP_{T}.png')).save(tex_out(f'block/multi/pump_casing_{t}.png'))
        ov = 'OVERLAY_PRIMITIVE_PUMP' if i == 0 else 'OVERLAY_ELECTRIC_PUMP'
        controller(f'water_pump_{t}', f'pump_casing_{t}', ov + '_INACTIVE', f'gt.blockmachines.multimachine.multi.waterpump.{i}.name',
                   'Primitive Water Pump' if i == 0 else f'{T} Electric Water Pump', [
                       ('2x2x1: an input next to the controller, an output hatch above it', 'Форма 2×2×1: сбоку от контроллера — вход, над ним — выходной люк'),
                       (('Steam input hatch, 40 L of steam a 10-tick cycle' if i == 0 else f'Energy hatch, {[8, 4, 16, 64, 256, 1024][i]} EU/t'),
                        ('Входной люк пара, 40 л пара за цикл в 10 тиков' if i == 0 else f'Энергетический люк, {[8, 4, 16, 64, 256, 1024][i]} EU/t')),
                       ('A fluid pipe of the tier on top of the controller', 'На контроллере — жидкостная труба своего уровня'),
                       (f'Pipes from the back (up to {10 if i == 0 else 10 + 4 * i}) to a {T} pump intake', f'Сзади — трубы (до {10 if i == 0 else 10 + 4 * i}) к заборнику {T}'),
                       ('Intake in the top water layer of a river, ocean or swamp', 'Заборник — в верхнем слое воды реки, океана или болота'),
                       (f'Needs {128 if i == 0 else 128 + 32 * i} water surface blocks in radius {16 if i == 0 else 16 + 4 * i}',
                        f'Нужно {128 if i == 0 else 128 + 32 * i} блоков водной глади в радиусе {16 if i == 0 else 16 + 4 * i}'),
                       ('Other pumps on the same water share the output', 'Другие насосы на той же воде делят выход')], active=ov + '_ACTIVE')
    # intakes: GT_CraftingRecipeLoader:494 ULV "RFR/FPF/RFR"; GT_MachineRecipeLoader:1836-1840 LV-IV assembler (160 t)
    shaped('multi/pump_intake_ulv', f'{NS}:pump_intake_ulv', 1, ['RFR', 'FPF', 'RFR'],
           {'R': tag_of('ring', 'Tin'), 'F': f'{NS}:primitive_fluid_filter', 'P': f'{NS}:pipe_medium_wood'})
    for t, mat, rings, filt, n, eut in (('lv', 'Bronze', 4, 'simple_fluid_filter', 4, 16), ('mv', 'Steel', 8, 'simple_fluid_filter', 16, 64),
                                         ('hv', 'StainlessSteel', 4, 'fluid_filter', 4, 256), ('ev', 'Titanium', 8, 'fluid_filter', 16, 1024),
                                         ('iv', 'TungstenSteel', 8, 'fluid_filter', 64, 1024)):
        mid = g['mat_id'](mat)
        mrec('assembler', f'pump_intake_{t}', [ing(f'{NS}:pipe_medium_{mid}'), ing(tag_of('ring', mat), rings), ing(f'{NS}:{filt}', n)],
             [out(f'{NS}:pump_intake_{t}')], 160, eut)
    # pumps: GT_Loader_MetaTileEntities:1413-1452
    shaped('multi/water_pump_ulv', f'{NS}:water_pump_ulv', 1, ['PBP', 'WHW', 'PBP'],
           {'P': 'minecraft:piston', 'B': f'{NS}:pipe_medium_bronze', 'W': f'{NS}:pipe_medium_wood', 'H': f'{NS}:hull_bronze'})
    for t, pipe, circ in (('lv', 'bronze', 'basic'), ('mv', 'steel', 'good'), ('hv', 'stainless_steel', 'advanced'), ('ev', 'titanium', 'data'),
                          ('iv', 'tungsten_steel', 'elite')):
        shaped(f'multi/water_pump_{t}', f'{NS}:water_pump_{t}', 1, ['PCP', 'BHB', 'PCP'],
               {'P': f'{NS}:electric_pump_{t}', 'C': f'#c:circuits/{circ}', 'B': f'{NS}:pipe_medium_{pipe}', 'H': f'{NS}:hull_{t}'})
    # fluid filter recipes: GT_MachineRecipeLoader:2595-2598 (50 t, 12 EU/t); byproducts IIS 2 has no material for are dropped
    def tiny(m, c):
        return {'id': f'{NS}:tiny_dust_{m}', 'count': c}
    have = lambda o: os.path.exists(os.path.join(A, 'items', o['id'].split(':')[1] + '.json'))
    for fid, outs in (('dirtywater', [tiny('silicon_dioxide', 5), tiny('phosphate', 3), tiny('biotite', 3), tiny('calcite', 2)]),
                      ('muddywater', [tiny('saltpeter', 4), tiny('potassium_feldspar', 1)])):
        for c in (1, 2):
            r = {'duration': 50, 'eut': 12, 'circuit': c, 'fluid_inputs': [{'ingredient': f'{NS}:{fid}', 'amount': 1000}],
                 'fluid_outputs': [{'id': 'minecraft:water', 'amount': 900}]}
            if c == 1:
                r['outputs'] = [o for o in outs if have(o)]
            raw('filter', f'{fid}_{c}', r)

    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    for k in ('implosion', 'pyrolyse', 'cracking', 'distillation_tower'):
        stats[k] = sum(1 for x in rec if x.startswith(k + '/'))
    return stats
