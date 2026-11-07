"""GT items that only the IIS 1 quest book still waited for (D132): small components, the ASoC wafer, ferrite, tungsten carbide, PPS, arsenic trioxide.

Sources: GT_MetaGenerated_Item_01:120-124, 289, 335-338, 376, GT_CraftingRecipeLoader:338-339, 396, 489, GT_MachineRecipeLoader:96, 420, 495, 1030, 1054, 1356-1358, 1907, 1911, 3034, 3724, 3752,
ProcessingCrafting:45, GT_MetaGenerated_Tool_01:120, 146.
"""
import os

from PIL import Image

NS = 'iis'
I = lambda x: f'{NS}:{x}'
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')


def first_frame(path):
    im = Image.open(path).convert('RGBA')
    return im.crop((0, 0, im.width, im.width))


def generate(g):
    A, D, write, lang, shaped, shapeless, tag_of, ref, mass, prop = g['A'], g['D'], g['write'], g['lang'], g['shaped'], g['shapeless'], g['tag_of'], g['ref'], g['mass'], g['prop']
    rec = {}
    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    fl = lambda fid, mb: {'ingredient': I(fid), 'amount': mb}

    def out(i, c=1):
        return {'id': i, 'count': c}

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, fout=None, keep=None, temp=None, cleanroom=False, circuit=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(dur), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if fout:
            r['fluid_outputs'] = [fout]
        if keep:
            r['keep'] = [keep]
        if temp:
            r['temp'] = temp
        if cleanroom:
            r['cleanroom'] = True
        if circuit is not None:
            r['circuit'] = circuit
        rec[f'{rtype}/{rid}'] = r

    def save_item(iid, en, ru, im):
        p = os.path.join(A, 'textures', 'item', iid + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        lang['en_us'][f'item.{NS}.{iid}'] = en
        lang['ru_ru'][f'item.{NS}.{iid}'] = ru

    M1 = 'gt.metaitem.01/'
    for iid, en, ru, src in (
            ('match', 'Match', 'Спичка', M1 + '471.png'),
            ('empty_spray', 'Empty Spray Can', 'Пустой спрей', M1 + '402.png'),
            ('minecart_wheels_iron', 'Iron Minecart Wheels', 'Железные колёса вагонетки', M1 + '100.png'),
            ('minecart_wheels_steel', 'Steel Minecart Wheels', 'Стальные колёса вагонетки', M1 + '101.png'),
            ('punch_card', 'Punch Card', 'Пустая перфокарта', M1 + '485.png'),
            ('small_gunpowder_bundle', 'Small Gunpowder Bundle', 'Маленькая пачка пороха', M1 + '409.png'),
            ('compact_disc', 'Compact Disc', 'Компакт-диск', M1 + '20.png'),
            ('waterproof_kit', 'Water Proof Upgrade', 'Комплект влагозащиты', M1 + '17.png'),
            ('fluid_regulator_iv', 'Fluid Regulator (IV)', 'Жидкостный регулятор (IV)', M1 + '619.png'),
            ('wafer_soc2', 'ASoC Wafer', 'Улучшенная система на чипе (Кремниевая вафля)', 'gt.metaitem.03/48.png')):
        path = os.path.join(GT, 'items', src)
        save_item(iid, en, ru, first_frame(path) if os.path.exists(path) else Image.new('RGBA', (16, 16), (150, 150, 150, 255)))
    for key, en, ru in (('hint', 'Right click a machine: rain and thunder no longer harm it', 'ПКМ по машине: дождь и гроза ей больше не страшны'),
                        ('done', 'This machine is now waterproof', 'Машина теперь защищена от влаги'),
                        ('already', 'This machine is already waterproof', 'Машина уже защищена от влаги')):
        lang['en_us'][f'item.iis.waterproof_kit.{key}'] = en
        lang['ru_ru'][f'item.iis.waterproof_kit.{key}'] = ru

    # a plain tinted icon for what has no icon of its own in GT: forms of materials that the form table does not make
    def form(iid, en, ru, shape, color):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y, row in enumerate(shape):
            for x, c in enumerate(row):
                if c != '.':
                    k = 1.0 if c == '#' else 0.7
                    im.putpixel((x + (16 - len(row)) // 2, y + (16 - len(shape)) // 2), tuple(int(v * k) for v in color) + (255,))
        save_item(iid, en, ru, im)

    PLATE = ['#########', '#########', '#########', '#########', '#########', '+++++++++']
    RING = ['..###..', '.#+.+#.', '#+...+#', '#.....#', '#+...+#', '.#+.+#.', '..###..']
    GEAR = ['.#.#.#.', '#######', '.##.##.', '##...##', '.##.##.', '#######', '.#.#.#.']
    TIP = ['...#', '..##', '.##+', '##+.', '#+..']
    form('double_plate_paper', 'Cardboard', 'Картон', PLATE, (190, 150, 105))
    form('ring_paper', 'Paper Ring', 'Бумажное кольцо', RING, (240, 240, 230))
    form('plate_concrete', 'Concrete Plate', 'Бетонная пластина', PLATE, (120, 120, 120))
    form('small_gear_wood', 'Small Wood Gear', 'Деревянная шестерня', GEAR, (130, 90, 45))
    form('small_gear_stone', 'Small Stone Gear', 'Каменная шестерня', GEAR, (135, 135, 135))
    form('arrow_head_flint', 'Flint Arrow Head', 'Наконечник стрелы из диоксида кремния', TIP, (70, 70, 80))
    # the scoop has no icon set of its own in the sources: drawn
    form('scoop', 'Scoop', 'Сачок', ['..####', '.#++++#', '.#++++#', '..####.', '...#...', '...#...', '...#...', '...#...'], (150, 120, 80))

    # the flask looks and works like a universal capsule of 1000 L
    write(os.path.join(A, 'items', 'volumetric_flask.json'), {'model': {'type': 'minecraft:condition', 'property': 'minecraft:has_component', 'component': 'iis:fluid_content',
                                                                        'on_true': {'type': 'minecraft:model', 'model': 'iis:item/universal_capsule_filled', 'tints': [{'type': 'iis:fluid_content'}]},
                                                                        'on_false': {'type': 'minecraft:model', 'model': 'iis:item/universal_capsule'}}})
    lang['en_us']['item.iis.volumetric_flask'] = 'Volumetric Flask'
    lang['ru_ru']['item.iis.volumetric_flask'] = 'Мерная колба'

    # --- recipes of the small items -----------------------------------------------------------------------------------------
    # GT takes a wooden bolt; wood has no bolts in IIS 2, so a stick (D132)
    mrec('assembler', 'match', [ing('minecraft:stick'), ing(tag_of('small_dust', 'Phosphorus'))], [out(I('match'))], 16, 16)
    mrec('assembler', 'match_4', [ing('minecraft:stick', 4), ing(tag_of('dust', 'Phosphorus'))], [out(I('match'), 4)], 64, 16)
    mrec('assembler', 'empty_spray', [ing('minecraft:redstone'), ing(I('capsule'))], [out(I('empty_spray'))], 800, 1)
    for n, m, d in (('iron', 'Iron', 500), ('wrought', 'WroughtIron', 400), ('steel', 'Steel', 300)):
        res = I('minecart_wheels_steel') if n == 'steel' else I('minecart_wheels_iron')
        mrec('assembler', f'minecart_wheels_{n}', [ing(tag_of('rod', m)), ing(tag_of('ring', m), 2)], [out(res)], d, 2)
        mrec('assembler', f'minecart_from_wheels_{n}', [ing(tag_of('plate', m), 3), ing(res, 2)], [out('minecraft:minecart')], d, 2)
    shaped('gt/minecart_wheels_iron', I('minecart_wheels_iron'), 1, [' h ', 'RSR', ' w '], {'R': tag_of('ring', 'Iron'), 'S': tag_of('rod', 'Iron')})
    shaped('gt/minecart_wheels_steel', I('minecart_wheels_steel'), 1, [' h ', 'RSR', ' w '], {'R': tag_of('ring', 'Steel'), 'S': tag_of('rod', 'Steel')})
    mrec('bender', 'double_plate_paper', [ing('minecraft:paper', 2)], [out(I('double_plate_paper'))], 40, 24)
    shaped('gt/punch_card', I('punch_card'), 2, ['Pk'], {'P': I('double_plate_paper')})
    mrec('cutter', 'punch_card', [ing(I('double_plate_paper'))], [out(I('punch_card'), 3)], 16, 4)
    shaped('gt/small_gunpowder_bundle', I('small_gunpowder_bundle'), 1, [' P ', 'PWP', ' B '], {'P': 'minecraft:paper', 'W': 'minecraft:gunpowder', 'B': 'minecraft:bowl'})
    mrec('assembler', 'small_gunpowder_bundle', [ing('minecraft:bowl', 5), ing('minecraft:gunpowder'), ing('minecraft:paper', 10)], [out(I('small_gunpowder_bundle'), 5)], 320, 4, fin=fl('glue', 500))
    mrec('forming_press', 'compact_disc', [ing(tag_of('foil', 'Aluminium')), ing(tag_of('foil', 'Plastic'))], [out(I('compact_disc'), 4)], 480, 16)
    mrec('assembler', 'waterproof_kit', [ing(tag_of('plate', 'Rubber'), 6), ing(tag_of('rod', 'Rubber'), 12)], [out(I('waterproof_kit'))], 800, 4, fin=fl('glue', 300))
    mrec('assembler', 'fluid_regulator_iv', [ing(I('electric_pump_iv')), ing('#c:circuits/elite', 2)], [out(I('fluid_regulator_iv'))], 800, 64)
    shaped('gt/scoop', I('scoop'), 1, ['SWS', 'SPS', ' S '], {'P': '#minecraft:planks', 'S': 'minecraft:stick', 'W': '#minecraft:wool'})
    shapeless('gt/flint_from_gravel', 'minecraft:flint', 1, [I('scoop'), 'minecraft:gravel'])
    # GT_MachineRecipeLoader:271: until now light concrete had no recipe at all
    mrec('fluid_solidifier', 'light_concrete', [], [out(I('light_concrete'))], 12, 4, fin=fl('molten_concrete', 144), keep=I('mold_block'))
    mrec('fluid_solidifier', 'plate_concrete', [],[out(I('plate_concrete'))], 20, 8, fin=fl('molten_concrete', 144), keep=I('mold_plate'))
    mrec('fluid_solidifier', 'volumetric_flask', [], [out(I('volumetric_flask'))], 44, 24, fin=fl('molten_borosilicateglass', 144), keep=I('mold_ball'))
    # ring and gears of materials without ingots: GT makes them from rods and plates of the material, here from the sheet or the block (D132)
    shaped('gt/ring_paper', I('ring_paper'), 2, ['Ps'], {'P': I('double_plate_paper')})
    shaped('gt/small_gear_wood', I('small_gear_wood'), 1, ['Ps'], {'P': tag_of('plate', 'Wood')})
    shaped('gt/small_gear_stone', I('small_gear_stone'), 1, ['Ps'], {'P': 'minecraft:stone'})
    shaped('gt/arrow_head_flint', I('arrow_head_flint'), 1, ['Xf'], {'X': 'minecraft:flint'})

    # --- ASoC wafer: ProcessingCrafting:45 ----------------------------------------------------------------------------------
    mrec('laser_engraver', 'wafer_soc2', [ing(I('silicon_wafer_naquadah'))], [out(I('wafer_soc2'))], 200, 1920, keep='#iis:crafting_lens/orange', cleanroom=True)

    # --- materials ------------------------------------------------------------------------------------------------------------
    # ferrite: GT_CraftingRecipeLoader:396, GT_MachineRecipeLoader:1054
    shapeless('dust/mix_ferrite_mixture', ref('dust', 'FerriteMixture'), 6, [tag_of('dust', 'Nickel'), tag_of('dust', 'Zinc')] + [tag_of('dust', 'Iron')] * 4)
    t = prop('NickelZincFerrite', 'BlastFurnaceTemp', 0) or g['code']['NickelZincFerrite']['blastFurnaceTemp']
    mrec('blast', 'nickel_zinc_ferrite', [ing(tag_of('dust', 'FerriteMixture'))], [out(ref('ingot', 'NickelZincFerrite'))], 600, 120, fin=fl('oxygen', 2000), temp=t)
    # tungsten carbide: :1030
    t = prop('TungstenCarbide', 'BlastFurnaceTemp', 0) or g['code']['TungstenCarbide']['blastFurnaceTemp']
    mrec('blast', 'tungsten_carbide', [ing(tag_of('ingot', 'Tungsten')), ing(tag_of('dust', 'Carbon'))], [out(ref('hot_ingot', 'TungstenCarbide') or ref('ingot', 'TungstenCarbide'), 2)], max(mass('TungstenCarbide') // 40, 1) * t, 480, temp=t)
    # polyphenylene sulfide: :3034
    mrec('chemical_reactor', 'polyphenylene_sulfide', [ing(tag_of('dust', 'Sulfur'))], [], 320, 30, fin=fl('benzene', 1000), fout={'id': I('molten_polyphenylenesulfide'), 'amount': 2000}, circuit=1)
    for form_, mold in (('ingot', 'mold_ingot'), ('plate', 'mold_plate')):
        mrec('fluid_solidifier', f'{form_}_polyphenylene_sulfide', [], [out(ref(form_, 'PolyphenyleneSulfide'))], 20, 8, fin=fl('molten_polyphenylenesulfide', 144), keep=I(mold))
    # arsenic trioxide: :3724, :3752
    mrec('blast', 'cobaltite_roast', [ing(tag_of('dust', 'Cobaltite'))], [out(ref('dust', 'CobaltOxide')), out(ref('dust', 'ArsenicTrioxide'))], 120, 120, fin=fl('oxygen', 3000), fout={'id': I('sulfurdioxide'), 'amount': 1000}, temp=1200)
    mrec('blast', 'arsenic_trioxide', [ing(tag_of('dust', 'ArsenicTrioxide'), 2), ing(tag_of('dust', 'Carbon'))], [out(ref('ingot', 'Arsenic'))], 240, 120, fout={'id': I('carbondioxide'), 'amount': 1000}, temp=1200)

    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
