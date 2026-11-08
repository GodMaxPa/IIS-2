"""IC2 Nuclear Control panels of the IIS 1 quest book (D141): information panel and extender, advanced panel and extender, range and colour
upgrades, the GregTech sensor kit and card, the GT computer monitor, the IC2 frequency transmitter.

Sources: IIS 1 scripts/NuclearControl.zs (panel, extender, advanced panel, advanced extender, colour upgrade, range upgrade: assembler sensor LV +
frequency transmitter), GT_MetaGenerated_Item_01:767, 813, 817 (computer monitor, sensor kit: assembler sensor LV + emitter LV),
GT_MachineRecipeLoader:674 (computer monitor). The IC2 frequency transmitter recipe is from memory (cable + circuit).
"""
import os

from PIL import Image, ImageDraw

NS = 'iis'
I = lambda x: f'{NS}:{x}'
MC = lambda x: f'minecraft:{x}'
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')

PANELS = [  # id, en, ru, casing colour, screen colour
    ('information_panel', 'Industrial Information Panel', 'Промышленная информационная панель', (120, 124, 130), (20, 60, 30)),
    ('information_panel_extender', 'Information Panel Extender', 'Расширитель информационной панели', (120, 124, 130), (20, 60, 30)),
    ('advanced_information_panel', 'Advanced Information Panel', 'Продвинутая информационная панель', (60, 62, 70), (15, 40, 70)),
    ('advanced_panel_extender', 'Advanced Panel Extender', 'Продвинутый расширитель панели', (60, 62, 70), (15, 40, 70)),
]


def generate(g):
    A, D, write, lang, shaped, shapeless, tag_of = g['A'], g['D'], g['write'], g['lang'], g['shaped'], g['shapeless'], g['tag_of']
    rec = {}
    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1: {'id': i, 'count': c}

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def save_tex(rel, im):
        p = os.path.join(A, 'textures', rel + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)

    def item(iid, en, ru, im):
        save_tex(f'item/monitor/{iid}', im)
        write(os.path.join(A, 'models', 'item', iid + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/monitor/{iid}'}})
        write(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{iid}'}})
        name(f'item.{NS}.{iid}', en, ru)

    def gt_icon(n):
        im = Image.open(os.path.join(GT, 'items', 'gt.metaitem.01', f'{n}.png')).convert('RGBA')
        return im.crop((0, 0, im.width, im.width))

    def drawn(fn):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        fn(ImageDraw.Draw(im))
        return im

    item('computer_monitor', 'Computer Monitor', 'Компьютерный монитор', gt_icon(740))
    item('gt_sensor_kit', 'GregTech Sensor Kit', 'Набор с датчиком GregTech', gt_icon(763))
    item('gt_sensor_card', 'GregTech Sensor Card', 'Карта датчика GregTech', drawn(lambda d: (d.rectangle([2, 3, 13, 12], fill=(200, 200, 190, 255), outline=(90, 90, 80, 255)),
                                                                                            d.rectangle([4, 5, 8, 9], fill=(60, 120, 200, 255)), d.line([(9, 6), (12, 6)], fill=(40, 40, 40, 255)))))
    item('frequency_transmitter', 'Frequency Transmitter', 'Передатчик частоты', drawn(lambda d: (d.rectangle([5, 6, 10, 14], fill=(70, 70, 80, 255)),
                                                                                               d.line([(7, 1), (7, 6)], fill=(180, 180, 190, 255)), d.point((7, 1), fill=(220, 40, 40, 255)))))
    item('range_upgrade', 'Range Upgrade', 'Улучшение дальности (панели)', drawn(lambda d: (d.rectangle([2, 3, 13, 12], fill=(40, 90, 50, 255)),
                                                                                         d.arc([4, 4, 11, 11], 200, 340, fill=(230, 230, 230, 255)))))
    item('color_upgrade', 'Color Upgrade', 'Улучшение цвета', drawn(lambda d: [d.rectangle([2 + 3 * i, 4, 4 + 3 * i, 11], fill=c + (255,))
                                                                              for i, c in enumerate(((220, 40, 40), (230, 200, 40), (60, 180, 60), (60, 90, 220)))]))
    for key, en, ru in (
            ('item.iis.gt_sensor_kit.tooltip.0', 'Right click a machine, reactor or energy storage: becomes a sensor card for it', 'ПКМ по машине, реактору или накопителю: становится картой датчика для него'),
            ('item.iis.gt_sensor_card.tooltip.0', 'Put into an information panel', 'Вставляется в информационную панель'),
            ('item.iis.gt_sensor_card.target', 'Target: %s, %s, %s', 'Цель: %s, %s, %s'),
            ('item.iis.range_upgrade.tooltip.0', 'Panel: card range 16 → +16 blocks each, up to 4', 'Панель: дальность карт 16 → +16 блоков за штуку, до 4'),
            ('item.iis.color_upgrade.tooltip.0', 'Panel: right click the panel with a dye to colour its text', 'Панель: ПКМ красителем по панели меняет цвет текста'),
            ('block.iis.information_panel.tooltip.0', 'One sensor card; extenders around it make the screen bigger', 'Одна карта датчика; расширители рядом увеличивают экран'),
            ('block.iis.advanced_information_panel.tooltip.0', 'Four sensor cards; advanced extenders make the screen bigger', 'Четыре карты датчика; продвинутые расширители увеличивают экран'),
            ('monitor.iis.out_of_range', 'Out of range', 'Вне досягаемости'), ('monitor.iis.no_data', 'No data', 'Нет данных'),
            ('monitor.iis.heat', 'Heat: %s / %s', 'Нагрев: %s / %s'), ('monitor.iis.output', 'Output: %s EU/t', 'Выход: %s EU/t'),
            ('monitor.iis.energy', 'Energy: %s / %s EU', 'Энергия: %s / %s EU'), ('monitor.iis.progress', 'Progress: %s%%', 'Работа: %s%%'),
            ('monitor.iis.flow', 'In %s, out %s EU/t', 'Вход %s, выход %s EU/t'), ('monitor.iis.empty', 'No cards', 'Нет карт')):
        name(key, en, ru)

    for bid, en, ru, casing, screen in PANELS:
        side = Image.new('RGBA', (16, 16), casing + (255,))
        ImageDraw.Draw(side).rectangle([0, 0, 15, 15], outline=tuple(int(c * 0.6) for c in casing) + (255,))
        save_tex(f'block/monitor/{bid}_side', side)
        front = Image.new('RGBA', (16, 16), screen + (255,))
        ImageDraw.Draw(front).rectangle([0, 0, 15, 15], outline=tuple(int(c * 0.6) for c in casing) + (255,))
        save_tex(f'block/monitor/{bid}_front', front)
        write(os.path.join(A, 'models', 'block', bid + '.json'), {'parent': 'minecraft:block/orientable', 'textures': {
            'front': f'{NS}:block/monitor/{bid}_front', 'side': f'{NS}:block/monitor/{bid}_side', 'top': f'{NS}:block/monitor/{bid}_side', 'particle': f'{NS}:block/monitor/{bid}_side'}})
        write(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {f'facing={f}': {'model': f'{NS}:block/{bid}', **({'y': y} if y else {})}
                                                                          for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
        write(os.path.join(A, 'models', 'item', bid + '.json'), {'parent': f'{NS}:block/{bid}'})
        write(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{bid}'}})
        write(os.path.join(D, NS, 'loot_table', 'blocks', bid + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': I(bid)}]}]})
        g['block_tags']['minecraft:mineable/pickaxe'].append(I(bid))
        name(f'block.{NS}.{bid}', en, ru)

    # --- recipes ---------------------------------------------------------------------------------------------------------------------
    rec['assembler/computer_monitor'] = {'type': f'{NS}:assembler', 'inputs': [ing(tag_of('plate', 'Glass')), ing(tag_of('foil', 'Aluminium'), 4), ing('#c:circuits/basic'),
                                                                            ing(tag_of('wire_fine', 'Copper'), 4)], 'outputs': [out(I('computer_monitor'))], 'duration': 50, 'eut': 16}
    rec['assembler/gt_sensor_kit'] = {'type': f'{NS}:assembler', 'inputs': [ing(I('sensor_lv')), ing(I('emitter_lv'))], 'outputs': [out(I('gt_sensor_kit'))], 'duration': 1600, 'eut': 2}
    rec['assembler/range_upgrade'] = {'type': f'{NS}:assembler', 'inputs': [ing(I('sensor_lv')), ing(I('frequency_transmitter'))], 'outputs': [out(I('range_upgrade'))], 'duration': 1600, 'eut': 2}
    shapeless('monitor/frequency_transmitter', I('frequency_transmitter'), 1, ['#iis:cables/1x/copper', '#c:circuits/basic'])
    shaped('monitor/color_upgrade', I('color_upgrade'), 1, ['RYG', 'WCM', 'KNB'], {'R': '#c:dyes/red', 'Y': '#c:dyes/yellow', 'G': '#c:dyes/green', 'W': '#c:dyes/white',
                                                                                'C': '#c:circuits/advanced', 'M': '#c:dyes/magenta', 'K': '#c:dyes/black', 'N': '#c:dyes/cyan', 'B': '#c:dyes/blue'})
    shaped('monitor/information_panel', I('information_panel'), 1, ['MGM', 'CHC', 'PWP'], {'M': I('computer_monitor'), 'G': MC('lime_stained_glass_pane'), 'C': '#c:circuits/basic',
                                                                                        'H': I('casing_ulv'), 'P': tag_of('plate', 'Iron'), 'W': '#iis:cables/1x/red_alloy'})
    shaped('monitor/information_panel_extender', I('information_panel_extender'), 1, ['GMG', 'PPP', 'PWP'], {'G': MC('lime_stained_glass_pane'), 'M': I('computer_monitor'),
                                                                                                          'P': tag_of('plate', 'Wood'), 'W': '#iis:cables/1x/red_alloy'})
    shaped('monitor/advanced_information_panel', I('advanced_information_panel'), 1, ['RIU', 'PCP', 'whd'], {'R': I('range_upgrade'), 'I': I('information_panel'), 'U': I('color_upgrade'),
                                                                                                          'P': I('carbon_plate'), 'C': '#c:circuits/advanced'})
    shaped('monitor/advanced_panel_extender', I('advanced_panel_extender'), 1, ['CEU', 'PSP', 'whd'], {'C': '#c:circuits/basic', 'E': I('information_panel_extender'), 'U': I('color_upgrade'),
                                                                                                    'P': I('carbon_plate'), 'S': tag_of('plate', 'Steel')})
    for rid, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', rid + '.json'), r)
    return {'panels': len(PANELS), 'recipes': len(rec) + 6}
