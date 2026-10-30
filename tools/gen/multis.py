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

    def controller(bid, casing_tex, overlay, key, en, lines):
        side = f'{NS}:block/multi/{casing_tex}'
        base = Image.open(tex_out(f'block/multi/{casing_tex}.png')).convert('RGBA')
        for suf, ov in (('', overlay), ('_active', overlay + '_ACTIVE')):
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

    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    for k in ('implosion', 'pyrolyse', 'cracking', 'distillation_tower'):
        stats[k] = sum(1 for x in rec if x.startswith(k + '/'))
    return stats
