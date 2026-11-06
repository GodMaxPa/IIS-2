"""GT tools (D122): seismic prospectors, data orb, data readers, portable scanner, vanadium steel.

Sources: GT_Loader_MetaTileEntities:1306-1309, GT_MachineRecipeLoader:525-530, GT_CraftingRecipeLoader:407, 495-496, GT_MetaGenerated_Item_01:812, GT_MetaTileEntity_SeismicProspector.
"""
import glob
import json
import os
import random

from PIL import Image

NS = 'iis'
I = lambda x: f'{NS}:{x}'


def spot(base, accent, seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(5, 11):
            c = accent if y in (3, 12) else base
            v = rnd.randint(-8, 8)
            im.putpixel((x, y), tuple(max(0, min(255, k + v)) for k in c) + (255,))
    return im


def generate(g):
    A, RES, ELEMENTS, code = g['A'], g['RES'], g['ELEMENTS'], g['code']
    write, D, lang, shaped, shapeless, tag_of, ref, mass, prop = g['write'], g['D'], g['lang'], g['shaped'], g['shapeless'], g['tag_of'], g['ref'], g['mass'], g['prop']
    rec = {}
    ing = lambda i, c=1: {'ingredient': i, 'count': c}

    def out(i, c=1, chance=None):
        return dict({'id': i, 'count': c}, **({'chance': chance} if chance else {}))

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None, temp=None, cleanroom=False):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(dur), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        if temp:
            r['temp'] = temp
        if cleanroom:
            r['cleanroom'] = True
        rec[f'{rtype}/{rid}'] = r

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    # --- vanadium steel: GT_CraftingRecipeLoader:407, ProcessingDust:41-44 ---------------------------------------------------
    shapeless('dust/mix_vanadium_steel', ref('dust', 'VanadiumSteel'), 9, [tag_of('dust', 'Vanadium'), tag_of('dust', 'Chrome')] + [tag_of('dust', 'Steel')] * 7)
    t = prop('VanadiumSteel', 'BlastFurnaceTemp', 0) or g['code']['VanadiumSteel']['blastFurnaceTemp']
    mrec('blast', 'dust_vanadium_steel', [ing(tag_of('dust', 'VanadiumSteel'))], [out(ref('ingot', 'VanadiumSteel'))], max(mass('VanadiumSteel') // 40, 1) * t, 120, temp=t)

    # --- seismic prospectors: GT_Loader_MetaTileEntities:1308-1309 -----------------------------------------------------------
    shaped('lv/seismic_prospector_lv', I('seismic_prospector_lv'), 1, ['WWW', 'EME', 'CCC'], {'M': I('hull_steel'), 'W': tag_of('double_plate', 'Steel'), 'E': '#c:circuits/basic', 'C': I('sensor_lv')})
    shaped('lv/seismic_prospector_ev', I('seismic_prospector_ev'), 1, ['WWW', 'EME', 'CCC'], {'M': I('hull_ev'), 'W': tag_of('double_plate', 'VanadiumSteel'), 'E': '#c:circuits/data', 'C': I('sensor_ev')})
    name(f'block.{NS}.seismic_prospector_lv', 'Seismic Prospector', 'Сейсморазведчик')
    name(f'block.{NS}.seismic_prospector_ev', 'Advanced Seismic Prospector', 'Улучшенный сейсморазведчик')
    for bid, lines in (('seismic_prospector_lv', (('Activate with 4 TNT, a Data Stick in the special slot; needs no energy', '4 ТНТ на вход, флешка в особый слот; энергия не нужна'),
                                                  ('Looks at the 5x5 column below, 10 seconds', 'Разведывает колонну 5×5 под собой, 10 секунд'))),
                       ('seismic_prospector_ev', (('Activate with 32 TNT, a Data Stick in the special slot; needs no energy', '32 ТНТ на вход, флешка в особый слот; энергия не нужна'),
                                                  ('Ores in 191x191 blocks by distance, oil of 3x3 fields, 40 seconds', 'Руды в квадрате 191×191 блок по дальности, нефть 3×3 полей, 40 секунд')))):
        for k, (en, ru) in enumerate(lines):
            name(f'block.{NS}.{bid}.tooltip.{k}', en, ru)

    # --- data orb: GT_MachineRecipeLoader:525-530 ------------------------------------------------------------------------------
    for gem in ('green_sapphire', 'emerald', 'olivine'):
        ex = I(f'gem_exquisite_{gem}')
        mrec('autoclave', f'data_orb_tritium_{gem}', [ing(ex)], [out(I('data_orb'), 1, 500)], 12000, 160, fin={'ingredient': I('tritium'), 'amount': 1000})
        mrec('autoclave', f'data_orb_uu_{gem}', [ing(ex, 8)], [out(I('data_orb'))], 12000, 320, fin={'ingredient': I('uumatter'), 'amount': 100}, cleanroom=True)

    # --- data readers and portable scanner: GT_CraftingRecipeLoader:495-496, GT_MetaGenerated_Item_01:812 ------------------------
    shaped('lv/data_reader_mv', I('data_reader_mv'), 1, ['PBP', 'CSC', 'PWP'], {'P': tag_of('plate', 'Aluminium'), 'W': '#iis:cables/1x/copper', 'B': I('battery_lithium_mv'), 'S': I('cover_screen'), 'C': '#c:circuits/good'})
    shaped('lv/data_reader_ev', I('data_reader_ev'), 1, ['PBP', 'CSC', 'PWP'], {'P': tag_of('plate', 'Titanium'), 'W': '#iis:cables/1x/aluminium', 'B': '#iis:batteries/master', 'S': I('cover_screen'), 'C': '#c:circuits/data'})
    shaped('lv/portable_scanner', I('portable_scanner'), 1, ['EPR', 'CSC', 'PBP'], {'C': '#c:circuits/advanced', 'P': tag_of('plate', 'Aluminium'), 'E': I('emitter_mv'), 'R': I('sensor_mv'), 'S': I('cover_screen'), 'B': I('battery_lithium_mv')})

    # --- elements for the scanner and the replicator: GT_MetaTileEntity_Scanner:111-122, GT_MetaTileEntity_Replicator:48-53 --------------
    elements = {}
    for n in g['names']:
        el = next((a.split('.')[1] for a in code.get(n, {}).get('rawArgs', []) if a.startswith('Element.')), None)
        if el and el in ELEMENTS and ref('dust', n):
            elements[ref('dust', n)] = max(1, mass(n))
    write(os.path.join(RES, NS, 'elements.json'), dict(sorted(elements.items())))
    # GT_Loader_MetaTileEntities:922-929 "EFE/CMC/EWE"
    for t, circuit, cable in (('lv', 'good', 'tin'), ('mv', 'advanced', 'copper'), ('hv', 'data', 'gold'), ('ev', 'elite', 'aluminium'), ('iv', 'master', 'platinum'), ('luv', 'ultimate', 'niobium_titanium'), ('zpm', 'superconductor', 'naquadah'), ('uv', 'infinite', 'naquadah_alloy')):
        shaped(f'lv/replicator_{t}', I(f'replicator_{t}'), 1, ['EFE', 'CMC', 'EWE'], {'M': I(f'hull_{t}'), 'F': I(f'field_generator_{t}'), 'E': I(f'emitter_{t}'), 'C': f'#c:circuits/{circuit}', 'W': f'#iis:cables/4x/{cable}'})

    # --- explosives: GT_MachineRecipeLoader:284, 686-694; GT_RecipeAdder#addImplosionRecipe ------------------------------------------------
    def fo(fid, mb):
        return {'id': I(fid), 'amount': mb}

    def fi(fid, mb):
        return {'ingredient': I(fid), 'amount': mb}

    def icon(iid, en, ru, im):
        p = os.path.join(A, 'textures', 'item', iid + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)
    # IC2 is closed (D5): own items that stand for its dynamite and industrial TNT
    icon('dynamite', 'Dynamite', 'Динамит', spot((170, 60, 50), (230, 220, 180), 401))
    icon('industrial_tnt', 'Industrial TNT', 'Промышленный ТНТ', spot((120, 60, 40), (210, 60, 50), 402))

    def fluid_rec(rtype, rid, inputs, outputs, dur, eut, fin=None, fout=None, keep=None, circuit=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': dur, 'eut': eut}
        if fin:
            r['fluid_inputs'] = [fin]
        if fout:
            r['fluid_outputs'] = [fout]
        if keep:
            r['keep'] = [keep]
        if circuit is not None:
            r['circuit'] = circuit
        rec[f'{rtype}/{rid}'] = r

    TOL = 'liquid_toluene'
    fluid_rec('distillery', 'toluene_from_heavy_fuel', [], [], 16, 24, fin=fi('liquid_heavy_fuel', 10), fout=fo(TOL, 4), circuit=1)
    fluid_rec('distillery', 'light_fuel_from_toluene', [], [], 16, 24, fin=fi(TOL, 30), fout=fo('liquid_light_fuel', 30), circuit=1)
    fluid_rec('fluid_solidifier', 'gelled_toluene', [], [out(I('gelled_toluene'))], 100, 16, fin=fi(TOL, 100), keep=I('mold_ball'))
    fluid_rec('chemical_reactor', 'gelled_toluene_sugar', [ing('minecraft:sugar'), ing(tag_of('tiny_dust', 'Plastic'))], [out(I('gelled_toluene'), 2)], 140, 192, fin=fi(TOL, 133))
    fluid_rec('chemical_reactor', 'tnt_from_toluene', [ing(I('gelled_toluene'), 4)], [out('minecraft:tnt')], 200, 24, fin=fi('sulfuricacid', 250))
    fluid_rec('chemical_reactor', 'industrial_tnt', [ing(I('gelled_toluene'), 4)], [out(I('industrial_tnt'))], 80, 480, fin=fi('liquid_nitrationmixture', 200), fout=fo('dilutedsulfuricacid', 200))
    CAP = {'neoforge:ingredient_type': 'neoforge:components', 'items': I('capsule'), 'components': {'iis:fluid_content': {'fluid': I('sulfuricacid'), 'amount': 1000}}}
    fluid_rec('mixer', 'nitration_mixture', [ing(CAP, 7)], [out(I('capsule'), 7)], 480, 2, fin=fi('nitricacid', 5000), fout=fo('liquid_nitrationmixture', 12000))
    fluid_rec('chemical_reactor', 'dynamite', [ing('minecraft:paper'), ing('minecraft:string')], [out(I('dynamite'))], 160, 4, fin=fi('glyceryl', 125))
    # GT_RecipeAdder#addImplosionRecipe: E explosives = E dynamite = E / 2 TNT = E / 4 industrial TNT; the recipes written so far hold TNT
    implosion = glob.glob(os.path.join(D, NS, 'recipe', '**', 'implosion', '*.json'), recursive=True)
    for f in implosion:
        r = json.load(open(f, encoding='utf-8'))
        tnt = next((i for i in r['inputs'] if i.get('ingredient') == 'minecraft:tnt'), None)
        if not tnt:
            continue
        rid = os.path.basename(f)[:-5]
        e = tnt['count'] * 2
        for suffix, item_, n in (('dynamite', I('dynamite'), e), ('itnt', I('industrial_tnt'), max(1, e // 4))):
            if n > 64:
                continue
            v = json.loads(json.dumps(r))
            next(i for i in v['inputs'] if i.get('ingredient') == 'minecraft:tnt').update(ingredient=item_, count=n)
            rec[f'implosion/{rid}_{suffix}'] = v

    # --- texts ------------------------------------------------------------------------------------------------------------------
    name(f'block.{NS}.replicator_lv', 'Basic Replicator', 'Базовый репликатор')
    for key, en, ru in (('item.iis.data_orb.empty', 'Empty: scan an element dust in a scanner', 'Пуста: отсканируйте пыль элемента в сканере'),
                        ('item.iis.data_orb.element', 'Elemental scan: %s', 'Скан элемента: %s'),
                        ('item.iis.prospection.raw', 'Raw prospection data: analyze in a scanner or with a data reader', 'Сырые данные разведки: анализ в сканере или считывателем'),
                        ('item.iis.prospection.analyzed', 'Analyzed prospection data', 'Обработанные данные разведки'),
                        ('item.iis.prospection.from', 'From X: %s Y: %s Z: %s, %s', 'Откуда: X: %s Y: %s Z: %s, %s'),
                        ('item.iis.prospection.ores', 'Ores below:', 'Руды внизу:'),
                        ('item.iis.prospection.near', 'Within %s blocks:', 'Ближе %s блоков:'),
                        ('item.iis.prospection.middle', 'Within %s blocks:', 'Дальше, до %s блоков:'),
                        ('item.iis.prospection.far', 'Within %s blocks:', 'Дальше, до %s блоков:'),
                        ('item.iis.prospection.none', 'nothing', 'ничего'),
                        ('item.iis.prospection.oil', 'Field %s: %s, %s-%s', 'Поле %s: %s, %s–%s'),
                        ('item.iis.prospection.oil_none', 'Field %s: nothing', 'Поле %s: ничего'),
                        ('item.iis.data_reader.no_data', 'Hold a data stick with prospection data in the other hand', 'Держите флешку с данными разведки в другой руке'),
                        ('item.iis.data_reader.no_energy', 'Not enough energy', 'Недостаточно энергии'),
                        ('item.iis.portable_scanner.pos', '----- X: %s Y: %s Z: %s, %s -----', '----- X: %s Y: %s Z: %s, %s -----'),
                        ('item.iis.portable_scanner.name', 'Name: %s', 'Название: %s'),
                        ('item.iis.portable_scanner.hard', 'Hardness: %s  Blast resistance: %s', 'Твёрдость: %s  Взрывостойкость: %s'),
                        ('item.iis.portable_scanner.tank', 'Tank %s: %s / %s L %s', 'Бак %s: %s / %s л %s'),
                        ('item.iis.portable_scanner.progress', 'Progress: %s / %s', 'Ход работы: %s / %s'),
                        ('item.iis.portable_scanner.energy', 'Energy: %s / %s EU', 'Энергия: %s / %s EU'),
                        ('item.iis.portable_scanner.formed', 'Structure formed', 'Структура собрана'),
                        ('item.iis.portable_scanner.not_formed', 'Structure incomplete', 'Структура не собрана'),
                        ('item.iis.portable_scanner.underground', 'Underground: %s, %s L', 'Под землёй: %s, %s л'),
                        ('item.iis.portable_scanner.pollution', 'Pollution in chunk: %s', 'Загрязнение в чанке: %s'),
                        ('item.iis.portable_scanner.clean', 'No pollution in chunk', 'Чанк чистый'),
                        ('item.iis.prospect.here', 'Here: %s', 'Здесь: %s'),
                        ('item.iis.prospect.lava', 'There is lava behind this rock', 'За этой стеной лава'),
                        ('item.iis.prospect.liquid', 'There is a liquid behind this rock', 'За стеной какая-то жидкость'),
                        ('item.iis.prospect.air', 'There is an air pocket behind this rock', 'Воздушный карман'),
                        ('item.iis.prospect.changing', 'Material is changing behind this rock', 'Изменчивый материал'),
                        ('item.iis.prospect.found', 'Found traces of %s', 'Найдены следы руд: %s'),
                        ('item.iis.prospect.nothing', 'No ores found', 'Нет руды :(')):
        name(key, en, ru)
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'recipes': len(rec)}
