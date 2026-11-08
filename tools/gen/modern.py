"""Owner's decision of 08.11 on items 6, 8 and 10 of the survival notes (D143): diamond coating of tools, vanilla stones and amethyst
in the GT system, the vanilla content that appeared after 1.7.10.

Diamond coating: the assembler puts a thin layer of diamond on a finished GT hand tool (two small piles of diamond dust; one for a
shovel or a hoe). The tool gets x1.5 durability, +25 % speed and mines one level higher, up to diamond. The stats are written into the
stack (max_damage, tool) by the recipe, so nothing in code depends on it; recoating a worn tool renews it.
Stones: macerator recipes by what the rock is made of (granite and diorite: quartz; andesite and basalt: magnetite, olivine; tuff:
volcanic ash, bentonite; calcite and dripstone: calcite). Amethyst: the vanilla shard is the GT gem (gen_content VANILLA).
"""
import os

NS = 'iis'
I = lambda x: f'{NS}:{x}'
MC = lambda x: f'minecraft:{x}'
COATED = ('pickaxe', 'axe', 'shovel', 'hoe', 'saw', 'sword')
MINEABLE = {'pickaxe': 'pickaxe', 'axe': 'axe', 'shovel': 'shovel', 'hoe': 'hoe'}
INCORRECT = ['wooden', 'stone', 'iron', 'diamond', 'netherite']
# rock -> (main output, [(form, material, chance of 10000)]); main output None = one pile of stone dust
STONES = {
    'granite': (None, [('dust', 'SiliconDioxide', 2000)]), 'polished_granite': (None, [('dust', 'SiliconDioxide', 2000)]),
    'diorite': (None, [('dust', 'SiliconDioxide', 1000)]), 'polished_diorite': (None, [('dust', 'SiliconDioxide', 1000)]),
    'andesite': (None, [('dust', 'SiliconDioxide', 1000), ('dust', 'Magnetite', 500)]), 'polished_andesite': (None, [('dust', 'SiliconDioxide', 1000), ('dust', 'Magnetite', 500)]),
    'tuff': (None, [('dust', 'Ash', 2000), ('dust', 'Bentonite', 1000)]), 'polished_tuff': (None, [('dust', 'Ash', 2000), ('dust', 'Bentonite', 1000)]),
    'basalt': (None, [('dust', 'Olivine', 1000), ('dust', 'Magnetite', 500)]), 'smooth_basalt': (None, [('dust', 'Olivine', 1000), ('dust', 'Magnetite', 500)]),
    'polished_basalt': (None, [('dust', 'Olivine', 1000), ('dust', 'Magnetite', 500)]),
    'blackstone': (None, [('dust', 'DarkAsh', 1000), ('dust', 'Sulfur', 500)]), 'polished_blackstone': (None, [('dust', 'DarkAsh', 1000), ('dust', 'Sulfur', 500)]),
    'gilded_blackstone': (None, [('small_dust', 'Gold', 10000), ('small_dust', 'Gold', 5000)]),
    'cobbled_deepslate': (None, [('dust', 'Magnetite', 500)]),
    'calcite': (('dust', 'Calcite', 1), []), 'dripstone_block': (('dust', 'Calcite', 1), []), 'pointed_dripstone': (('small_dust', 'Calcite', 1), []),
    'amethyst_block': (('dust', 'Amethyst', 4), []), 'amethyst_cluster': (('crushed', 'Amethyst', 2), []), 'large_amethyst_bud': (('dust', 'Amethyst', 1), []),
    'medium_amethyst_bud': (('small_dust', 'Amethyst', 2), []), 'small_amethyst_bud': (('small_dust', 'Amethyst', 1), []),
}
NETHERITE_TOOLS = ('sword', 'spear', 'shovel', 'pickaxe', 'hoe', 'axe')


def tool_component(kind, quality, speed):
    """The Tool component of a GT tool as ToolMaterial#applyToolProperties builds it, for another level and speed."""
    return {'rules': [{'blocks': f'#minecraft:incorrect_for_{INCORRECT[min(4, quality)]}_tool', 'correct_for_drops': False},
                      {'blocks': f'#minecraft:mineable/{MINEABLE[kind]}', 'correct_for_drops': True, 'speed': speed}],
            'damage_per_block': 1, 'can_destroy_blocks_in_creative': True}


def generate(g):
    D, OVR, write, lang, shaped, ref, tag_of, add_recipe, tools = g['D'], g['OVR'], g['write'], g['lang'], g['shaped'], g['ref'], g['tag_of'], g['add_recipe'], g['tools']
    rec = {}
    ing = lambda i, c=1: {'ingredient': i, 'count': c}

    def name(key, en, ru):
        lang['en_us'][key] = en
        lang['ru_ru'][key] = ru

    def mrec(rtype, rid, inputs, outputs, dur, eut):
        rec[f'{rtype}/{rid}'] = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(dur), 'eut': int(eut)}

    def override(rid, recipe):
        write(os.path.join(OVR, 'data', 'minecraft', 'recipe', rid + '.json'), recipe)

    never = {'neoforge:conditions': [{'type': 'neoforge:never'}]}

    # --- diamond coating ---------------------------------------------------------------------------------------------------------
    name('tooltip.iis.diamond_coated', 'Diamond coating', 'Алмазное напыление')
    coated = 0
    for t in tools:
        if t['tool'] not in COATED:
            continue
        q = t['quality'] if t['quality'] >= 3 else t['quality'] + 1
        comps = {'minecraft:max_damage': t['maxDamage'] * 3 // 2,
                 'minecraft:lore': [{'translate': 'tooltip.iis.diamond_coated', 'color': 'aqua', 'italic': False}]}
        kind = 'axe' if t['tool'] == 'saw' else t['tool']
        if kind in MINEABLE:
            comps['minecraft:tool'] = tool_component(kind, q, round(t['speed'] * 1.25, 3))
        n = 1 if t['tool'] in ('shovel', 'hoe') else 2
        mrec('assembler', f'diamond_coating_{t["id"]}', [ing(I(t['id'])), ing(tag_of('small_dust', 'Diamond'), n)],
             [{'id': I(t['id']), 'count': 1, 'components': comps}], 200, 16)
        coated += 1

    # --- vanilla stones and amethyst in the macerator ----------------------------------------------------------------------------
    stone = ref('dust', 'Stone')
    mrec('macerator', 'vanilla_deepslate', [ing(MC('deepslate'))], [{'id': MC('cobbled_deepslate'), 'count': 1}], 400, 2)
    for rock, (main, extra) in STONES.items():
        if main:
            outs = [{'id': ref(main[0], main[1]), 'count': main[2]}]
        else:
            outs = [{'id': stone, 'count': 1}]
        for form, mat, chance in extra:
            r = ref(form, mat)
            assert r, (form, mat)
            o = {'id': r, 'count': 1}
            if chance < 10000:
                o['chance'] = chance
            outs.append(o)
        assert all(o['id'] for o in outs), rock
        mrec('macerator', f'vanilla_{rock}', [ing(MC(rock))], outs, 400, 2)

    # --- netherite: no vanilla tools (D101), ancient debris is an ore for the macerator ---------------------------------------------
    for t in NETHERITE_TOOLS:
        override(f'netherite_{t}_smithing', never)
    mrec('macerator', 'ancient_debris', [ing(MC('ancient_debris'))], [{'id': MC('netherite_scrap'), 'count': 2}], 400, 30)

    # --- crafter: automation after LV --------------------------------------------------------------------------------------------
    override('crafter', never)
    shaped('vanilla/crafter', MC('crafter'), 1, ['PAP', 'PTP', 'CDC'],
           {'P': tag_of('plate', 'Iron'), 'A': I('robot_arm_lv'), 'T': MC('crafting_table'), 'C': '#c:circuits/basic', 'D': MC('dropper')}, 'redstone')

    # --- elytra: they glide only with a frame of the jetpack's level (advanced circuit) ---------------------------------------------
    name('tooltip.iis.elytra_frame', 'Will not glide without a frame (crafting table)', 'Без рамы не планируют (верстак)')
    add_recipe('vanilla/elytra_frame', {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': ['RCR', 'PEP', 'R R'],
               'key': {'R': tag_of('rod', 'Steel'), 'C': '#c:circuits/advanced', 'P': tag_of('plate', 'Aluminium'), 'E': MC('elytra')},
               'result': {'id': MC('elytra'), 'components': {'minecraft:glider': {}, '!minecraft:lore': {}}}})

    for rid, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', rid + '.json'), r)
    return {'coated': coated, 'recipes': len(rec)}
