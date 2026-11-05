"""GregTech remainder, first batch (D119): GT credits, printer, recycler, amplifabricator, mass fabricator, fermenter, plasma arc furnace, disassembler (eggs only),
duct tape, auto repair kit, semi-automatic and automatic maintenance hatches.

Sources are named at each recipe: GT_Loader_MetaTileEntities, GT_MachineRecipeLoader, GT_RecipeAdder, GT_MetaGenerated_Item_01.
"""
import glob
import json
import os
import random

from PIL import Image

NS = 'iis'
I = lambda x: f'{NS}:{x}'
CREDITS = (('copper', 'Copper GT Credit', 'Медный жетон GT', (210, 120, 70)), ('cupronickel', 'Cupronickel GT Credit', 'Мельхиоровый жетон GT', (190, 170, 110)),
           ('silver', 'Silver GT Credit', 'Серебряный жетон GT', (210, 210, 225)), ('gold', 'Gold GT Credit', 'Золотой жетон GT', (240, 210, 70)),
           ('platinum', 'Platinum GT Credit', 'Платиновый жетон GT', (220, 235, 240)), ('osmium', 'Osmium GT Credit', 'Осмиевый жетон GT', (90, 100, 200)),
           ('naquadah', 'Naquadah GT Credit', 'Наквадовый жетон GT', (40, 60, 40)), ('neutronium', 'Neutronium GT Credit', 'Нейтрониевый жетон GT', (235, 235, 235)))
DYES = ('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black')


def coin(col, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d > 36:
                continue
            k = 0.7 if d > 25 or (x == 7 and 4 < y < 11) else 1.0
            v = rnd.randint(-5, 5)
            im.putpixel((x, y), tuple(max(0, min(255, int(c * k) + v)) for c in col) + (255,))
    return im


def spot(col, accent, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(3, 13):
            c = accent if x in (3, 12) or y in (3, 12) else col
            v = rnd.randint(-6, 6)
            im.putpixel((x, y), tuple(max(0, min(255, k + v)) for k in c) + (255,))
    return im


def generate(g):
    write, A, D, lang, shaped, shapeless, tag_of, ref = g['write'], g['A'], g['D'], g['lang'], g['shaped'], g['shapeless'], g['tag_of'], g['ref']
    rec = {}
    g['item_tags'].setdefault('c:circuits/superconductor', [])   # filled by the UV tier: recipes that need it stay closed until then

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def item(iid, en, ru, im):
        p = os.path.join(A, 'textures', 'item', iid + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, fout=None, circuit=None, fins=None, keep=None, temp=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': dur, 'eut': eut}
        if fin:
            r['fluid_inputs'] = [fin]
        if fins:
            r['fluid_inputs'] = fins
        if fout:
            r['fluid_outputs'] = [fout]
        if circuit is not None:
            r['circuit'] = circuit
        if keep:
            r['keep'] = [keep]
        if temp:
            r['temp'] = temp
        rec[f'{rtype}/{rid}'] = r

    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1, chance=None: dict({'id': i, 'count': c}, **({'chance': chance} if chance else {}))
    fl = lambda fid, mb: {'ingredient': f'{NS}:{fid}', 'amount': mb}
    fo = lambda fid, mb: {'id': f'{NS}:{fid}', 'amount': mb}

    # --- credits: GT_MetaGenerated_Item_01:57-64, 104-117; only quest rewards give them (GT_MachineRecipeLoader:1321) ------------------
    for k, (mid, en, ru, col) in enumerate(CREDITS):
        item(f'credit_{mid}', en, ru, coin(col, 300 + k))
    for (low, _, _, _), (high, _, _, _) in zip(CREDITS, CREDITS[1:]):
        shapeless(f'credit/{low}_to_{high}', I(f'credit_{high}'), 1, [I(f'credit_{low}')] * 8)
        shapeless(f'credit/{high}_to_{low}', I(f'credit_{low}'), 8, [I(f'credit_{high}')])

    # --- parts ----------------------------------------------------------------------------------------------------------------------
    item('scrap', 'Scrap', 'Утильсырьё', spot((120, 100, 80), (80, 70, 60), 320))
    item('duct_tape', 'Reinforced Duct Tape', 'Сверхтехнологичная изолента', spot((170, 170, 180), (110, 110, 120), 321))
    item('auto_repair_kit', 'Auto Repair Kit', 'Автоматический ремонтный набор', spot((190, 90, 60), (110, 60, 40), 322))
    item('stem_cell', 'Stemcell', 'Стволовые клетки', spot((220, 190, 200), (180, 120, 140), 323))
    # GT_MachineRecipeLoader:649-651
    for rubber in ('Rubber', 'StyreneButadieneRubber', 'Silicone'):
        if ref('plate', rubber):
            mrec('assembler', f'duct_tape_{rubber.lower()}', [ing(tag_of('plate', rubber), 3), ing(I('carbon_mesh'), 3)], [out(I('duct_tape'))], 100, 64, fin=fl('glue', 300))
    # :2017
    mrec('assembler', 'auto_repair_kit', [ing(I('duct_tape'), 4), ing(tag_of('screw', 'Steel'), 4), ing(tag_of('wire_fine', 'SolderingAlloy'), 16), ing('#c:circuits/advanced', 2)], [out(I('auto_repair_kit'))], 80, 384,
         fin=fl('lubricant', 2000))
    # GT_Loader_MetaTileEntities:398
    shaped('multi/hatch_maintenance_semiauto', I('hatch_maintenance_semiauto'), 1, ['CHC', 'AMA', 'CHC'], {'M': I('hull_iv'), 'H': I('hatch_maintenance'), 'A': I('robot_arm_iv'), 'C': '#c:circuits/elite'})
    # GT_MachineRecipeLoader:2032-2043: needs superconductor circuits, the UV tier gives them
    mrec('assembly_line', 'hatch_maintenance_auto', [ing(I('hull_zpm')), ing(I('robot_arm_zpm'), 2), ing('#c:circuits/superconductor', 4), ing(I('auto_repair_kit'), 64)], [out(I('hatch_maintenance_auto'))], 600, 30000,
         fins=[fl('lubricant', 500)], keep=I('hatch_maintenance_semiauto'), temp=288000)

    # GT_Loader_MetaTileEntities:894-900 mass fabricator "CFC/WMW/CFC" (the other machines are in lv.py NEW_CRAFT)
    for t, circuit, cable in (('lv', 'good', 'tin'), ('mv', 'advanced', 'copper'), ('hv', 'data', 'gold'), ('ev', 'elite', 'aluminium'), ('iv', 'master', 'platinum'), ('luv', 'ultimate', 'niobium_titanium'), ('zpm', 'superconductor', 'naquadah')):
        if os.path.exists(os.path.join(A, 'items', f'field_generator_{t}.json')):
            shaped(f'lv/massfab_{t}', I(f'massfab_{t}'), 1, ['CFC', 'WMW', 'CFC'], {'M': I(f'hull_{t}'), 'F': I(f'field_generator_{t}'), 'C': f'#c:circuits/{circuit}', 'W': f'#iis:cables/4x/{cable}'})
    # --- recipes ---------------------------------------------------------------------------------------------------------------------
    # GT_Recipe_Map_Recycler: anything -> scrap at 12.5 %, 45 t, 1 EU/t: in code (ElectricMachineBlockEntity)
    # GT_MachineRecipeLoader:1012 addAmplifier(scrap x 9, 180 t, 1 mB) at 32 EU/t
    mrec('amplifab', 'scrap', [ing(I('scrap'), 9)], [], 180, 32, fout=fo('uuamplifier', 1))
    # GT_MetaTileEntity_Massfabricator: V[1] * 2^(tier + 2) EU/t for 3215 / 2^(tier - 1) ticks, UU amplifier cuts the time 40 times. The total matches 32 EU/t for 8 x as long: overclocking gives the same at each tier.
    mrec('massfab', 'uu_matter_amplifier', [], [], 3215 * 8 // 40, 32, fin=fl('uuamplifier', 1), fout=fo('uumatter', 1))
    mrec('massfab', 'uu_matter', [], [], 3215 * 8, 32, fout=fo('uumatter', 1), circuit=1)
    # GT_MachineRecipeLoader:3401 (2 EU/t: GT_RecipeAdder:799)
    mrec('fermenter', 'biomass', [], [], 150, 2, fin=fl('biomass', 100), fout=fo('fermentedbiomass', 100))
    # GT_MetaTileEntity_Disassembler: eggs give stem cells at (tier + 1) %, 16 EU/t, 2400 >> tier ticks. Here the LV chance, machine crafting data is not kept in items
    mrec('disassembler', 'egg', [ing('minecraft:egg')], [out(I('stem_cell'), 1, 200)], 1200, 16)
    # GT_Recipe_Map_Printer: 144 mB dye paints what crafting would paint, 256 t, 2 EU/t
    fluids = {'dye_chemical_dye' + d.replace('_', ''): d for d in DYES}
    fluids.update({'dye_watermixed_dye' + d.replace('_', ''): d for d in DYES})
    for f, d in fluids.items():
        for kind, inp, target in (('wool', '#minecraft:wool', f'minecraft:{d}_wool'), ('carpet', '#minecraft:wool_carpets', f'minecraft:{d}_carpet'), ('glass', 'minecraft:glass', f'minecraft:{d}_stained_glass'),
                                  ('pane', 'minecraft:glass_pane', f'minecraft:{d}_stained_glass_pane'), ('terracotta', 'minecraft:terracotta', f'minecraft:{d}_terracotta')):
            mrec('printer', f'{kind}_{f}', [ing(inp)], [out(target)], 256, 2, fin=fl(f, 144))
    # GT_RecipeAdder:1129-1133: each arc furnace recipe also runs on argon or nitrogen plasma, plasma amount = duration / (mass x 16), time / 16, EU / 3
    for path in sorted(glob.glob(os.path.join(D, NS, 'recipe', 'processing', 'arc_furnace', '*.json'))):
        src = json.load(open(path, encoding='utf-8'))
        rid = os.path.basename(path)[:-5]
        for gas, mass in (('argon', 40), ('nitrogen', 14)):
            mrec('plasma_arc_furnace', f'{rid}_{gas}', src['inputs'], src['outputs'], max(1, src['duration'] // 16), max(1, src['eut'] // 3),
                 fin=fl(f'plasma_{gas}', max(1, src['duration'] // (mass * 16))), fout=fo(gas, max(1, src['duration'] // (mass * 16))))

    for k, en, ru in (('jei.iis.fermenter', 'Fermenter', 'Ферментёр'), ('jei.iis.printer', 'Printer', 'Принтер'), ('jei.iis.amplifab', 'Amplifabricator', 'Амплифабрикатор'),
                      ('jei.iis.massfab', 'Mass Fabricator', 'Производитель материи'), ('jei.iis.plasma_arc_furnace', 'Plasma Arc Furnace', 'Плазменно-дуговая печь'),
                      ('jei.iis.disassembler', 'Disassembler', 'Разборочный станок'),
                      ('message.iis.maintenance.taped', 'Everything is fixed with duct tape', 'Всё починено изолентой'), ('message.iis.maintenance.kits', 'Repair kits: %s', 'Ремонтных наборов: %s'),
                      ('block.iis.hatch_maintenance_semiauto.tooltip.0', 'Repairs by itself: 1 Auto Repair Kit per repair; right-click with kits to load', 'Чинит сам: 1 ремонтный набор на починку; ПКМ наборами — загрузить'),
                      ('block.iis.hatch_maintenance_auto.tooltip.0', 'No need for maintaining', 'Обслуживание не нужно')):
        name(k, en, ru)
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
