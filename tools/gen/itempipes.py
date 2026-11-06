"""Item pipes (D123): models, crafts, extruder and restrictive recipes.

Sources: GT_Loader_MetaTileEntities:1620-1628, 1809-1816 generateItemPipes; ProcessingPipe:30-48; ProcessingShaping:42-45; OrePrefixes:497-499.
"""
import os

from PIL import Image

NS = 'iis'
HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, 'textures')
GT = os.environ.get('IIS_GT5_TEXTURES', '/home/claude/ref/gt5/src/main/resources/assets/gregtech/textures')
I = lambda x: f'{NS}:{x}'
# GT material, id, first GT id, EN, RU adjective feminine
MATERIALS = (('Brass', 'brass', 5602, 'Brass', 'латунная'), ('Electrum', 'electrum', 5612, 'Electrum', 'электрумовая'), ('Platinum', 'platinum', 5622, 'Platinum', 'платиновая'), ('Osmium', 'osmium', 5632, 'Osmium', 'осмиевая'),
             ('PolyvinylChloride', 'polyvinyl_chloride', 5640, 'PVC', 'ПВХ'), ('WroughtIron', 'wrought_iron', 5646, 'Wrought Iron', 'из кованого железа'), ('Nickel', 'nickel', 5652, 'Nickel', 'никелевая'),
             ('Cobalt', 'cobalt', 5658, 'Cobalt', 'кобальтовая'), ('Aluminium', 'aluminium', 5664, 'Aluminium', 'алюминиевая'))
BASE = {'brass': 1, 'electrum': 2, 'platinum': 4, 'osmium': 8, 'polyvinyl_chloride': 4, 'wrought_iron': 1, 'nickel': 1, 'cobalt': 2, 'aluminium': 2}
# size, thickness, slot multiplier, step, ingots in the extruder, ticks, restrictor rings, EN, RU
SIZES = (('medium', 8, 1, 32768, 3, 24, 3, '', ''), ('large', 12, 2, 16384, 6, 48, 4, 'Large ', 'большая '), ('huge', 14, 4, 8192, 12, 96, 5, 'Huge ', 'огромная '))
CRAFT = {'medium': (['PPP', 'w h', 'PPP'], 2), 'large': (['PhP', 'P P', 'PwP'], 1)}


def generate(g):
    write, A, D, lang, shaped, tag_of, ref, add_item_tag, block_tags, code = g['write'], g['A'], g['D'], g['lang'], g['shaped'], g['tag_of'], g['ref'], g['add_item_tag'], g['block_tags'], g['code']
    rec = {}
    made = 0

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def tex(rel):
        p = os.path.join(A, 'textures', rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    # GT draws the restrictor on top of the pipe: here it is baked into a side texture of its own
    side = Image.open(os.path.join(TEX, 'pipes', 'METALLIC', 'pipeSide.png')).convert('RGBA').crop((0, 0, 16, 16))
    side.save(tex('block/pipe/item_side.png'))
    Image.alpha_composite(side, Image.open(os.path.join(GT, 'blocks', 'iconsets', 'PIPE_RESTRICTOR.png')).convert('RGBA').crop((0, 0, 16, 16))).save(tex('block/pipe/item_side_restrictive.png'))
    for tsize in ('medium', 'large'):
        Image.open(os.path.join(TEX, 'pipes', 'METALLIC', f'pipe{tsize.capitalize()}.png')).convert('RGBA').crop((0, 0, 16, 16)).save(tex(f'block/pipe/item_{tsize}.png'))

    allf = ('north', 'south', 'east', 'west', 'up', 'down')
    rot = {'north': {}, 'south': {'y': 180}, 'east': {'y': 90}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    for gm, mat, _gid, en_m, ru_m in MATERIALS:
        r, gg, b = code[gm]['rgba'][:3]
        color = (r << 16) | (gg << 8) | b
        plate, ingot = tag_of('plate', gm), ref('ingot', gm)
        temp = code[gm].get('blastFurnaceTemp', 0) or 0
        volt = 64 if temp >= 2800 else 16
        if gm == 'PolyvinylChloride':
            volt //= 4   # ProcessingShaping: NO_SMASHING
        for size, thick, mul, step, ingots, ticks, rings, sen, sru in SIZES:
            for restrictive in (False, True):
                pid = 'item_pipe_' + ('restrictive_' if restrictive else '') + f'{size}_{mat}'
                tsize = 'large' if size == 'huge' else size
                side_tex, end_tex = f'{NS}:block/pipe/item_side' + ('_restrictive' if restrictive else ''), f'{NS}:block/pipe/item_{tsize}'
                lo, hi = (16 - thick) / 2, (16 + thick) / 2
                txs = {'particle': side_tex, 'side': side_tex, 'end': end_tex}
                write(os.path.join(A, 'models', 'block', pid + '_core.json'), {'parent': 'minecraft:block/block', 'textures': txs,
                      'elements': [{'from': [lo, lo, lo], 'to': [hi, hi, hi], 'faces': {f: {'texture': '#side', 'tintindex': 0} for f in allf}}]})
                write(os.path.join(A, 'models', 'block', pid + '_arm.json'), {'parent': 'minecraft:block/block', 'textures': txs,
                      'elements': [{'from': [lo, lo, 0], 'to': [hi, hi, lo],
                                    'faces': {'north': {'texture': '#end', 'tintindex': 0, 'cullface': 'north'}, 'east': {'texture': '#side', 'tintindex': 0}, 'west': {'texture': '#side', 'tintindex': 0},
                                              'up': {'texture': '#side', 'tintindex': 0}, 'down': {'texture': '#side', 'tintindex': 0}}}]})
                parts = [{'apply': {'model': f'{NS}:block/{pid}_core'}}]
                for d, ro in rot.items():
                    parts.append({'when': {d: 'true'}, 'apply': dict({'model': f'{NS}:block/{pid}_arm'}, **ro)})
                write(os.path.join(A, 'blockstates', pid + '.json'), {'multipart': parts})
                write(os.path.join(A, 'models', 'item', pid + '.json'), {'parent': 'minecraft:block/block', 'textures': txs,
                      'elements': [{'from': [lo, lo, 0], 'to': [hi, hi, 16],
                                    'faces': {'north': {'texture': '#end', 'tintindex': 0}, 'south': {'texture': '#end', 'tintindex': 0}, 'east': {'texture': '#side', 'tintindex': 0},
                                              'west': {'texture': '#side', 'tintindex': 0}, 'up': {'texture': '#side', 'tintindex': 0}, 'down': {'texture': '#side', 'tintindex': 0}}}],
                      'display': {'gui': {'rotation': [30, 45, 0], 'scale': [0.625, 0.625, 0.625]}}})
                write(os.path.join(A, 'items', pid + '.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{pid}', 'tints': [{'type': 'minecraft:constant', 'value': color}]}})
                write(os.path.join(D, NS, 'loot_table', 'blocks', pid + '.json'), {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{pid}',
                      'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [{'type': 'minecraft:item', 'name': I(pid)}]}]})
                block_tags.setdefault('iis:mineable/wrench', []).append(I(pid))
                add_item_tag('iis:item_pipes', I(pid))
                slots = BASE[mat] * mul
                # "Большая ограничительная латунная предметная труба", "Предметная труба из кованого железа"
                prefix = f'{sru}{"ограничительная " if restrictive else ""}'
                if ru_m.startswith('из '):
                    rname = f'{prefix}предметная труба {ru_m}'
                else:
                    rname = f'{prefix}{ru_m} предметная труба'
                rname = rname[0].upper() + rname[1:]
                name(f'block.{NS}.{pid}', f'{sen}{"Restrictive " if restrictive else ""}{en_m} Item Pipe', rname)
                name(f'block.{NS}.{pid}.tooltip.0', f'Item Capacity: {slots} stacks/sec', f'Пропускная способность: {slots} порц./с')
                name(f'block.{NS}.{pid}.tooltip.1', f'Routing value: {step // BASE[mat] * (100 if restrictive else 1)}', f'Цена маршрута: {step // BASE[mat] * (100 if restrictive else 1)}')
                if restrictive:
                    name(f'block.{NS}.{pid}.tooltip.2', 'Items pick other routes first', 'Предметы идут сюда в последнюю очередь')
                made += 1
            base_id = f'item_pipe_{size}_{mat}'
            if size in CRAFT:   # ProcessingPipe:30-41
                pat, cnt = CRAFT[size]
                shaped(f'pipe/item_{size}_{mat}', I(base_id), cnt, pat, {'P': plate, 'h': '#iis:tools/hammer', 'w': '#iis:tools/wrench'})
            if ingot:   # ProcessingShaping:42-45
                rec[f'extruder/{base_id}'] = {'type': f'{NS}:extruder', 'inputs': [{'ingredient': tag_of('ingot', gm), 'count': ingots}], 'outputs': [{'id': I(base_id), 'count': 1}], 'duration': ticks, 'eut': 8 * volt,
                                              'keep': [I(f'shape_extruder_pipe_{size}')]}
            # ProcessingPipe:48: rings of steel, 400 ticks each, 4 EU/t
            rid = f'item_pipe_restrictive_{size}_{mat}'
            rec[f'assembler/{rid}'] = {'type': f'{NS}:assembler', 'inputs': [{'ingredient': I(base_id), 'count': 1}, {'ingredient': tag_of('ring', 'Steel'), 'count': rings}], 'outputs': [{'id': I(rid), 'count': 1}], 'duration': 400 * rings, 'eut': 4}
    # GT_MachineRecipeLoader:3465 + GT_RecipeAdder#addDefaultPolymerizationRecipes:273-276: vinyl chloride -> molten PVC
    def cap(fid):
        return {'neoforge:ingredient_type': 'neoforge:components', 'items': I('capsule'), 'components': {'iis:fluid_content': {'fluid': I(fid), 'amount': 1000}}}

    def poly(rid, cell, fin, amt, mb, dur):
        rec[f'chemical_reactor/pvc_{rid}'] = {'type': f'{NS}:chemical_reactor', 'inputs': [{'ingredient': cap(cell), 'count': 1}], 'outputs': [{'id': I('capsule'), 'count': 1}], 'duration': dur, 'eut': 30, 'circuit': 1,
                                              'fluid_inputs': [{'ingredient': I(fin), 'amount': amt}], 'fluid_outputs': [{'id': I('molten_polyvinylchloride'), 'amount': mb}]}
    poly('air_cell', 'air', 'vinylchloride', 288, 288, 320)
    poly('oxygen_cell', 'oxygen', 'vinylchloride', 144, 216, 160)
    poly('vinyl_cell_air', 'vinylchloride', 'air', 14000, 1000, 1120)
    poly('vinyl_cell_oxygen', 'vinylchloride', 'oxygen', 7000, 1500, 1120)
    for key, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', key + '.json'), r)
    return {'item_pipes': made, 'recipes': len(rec)}
