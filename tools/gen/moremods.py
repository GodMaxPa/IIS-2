"""Ready mods with the rules of IIS 1 (D127): Iron Chests, Chunk Loaders, Construction Wand.

The mods themselves are listed in gradle.properties and docs/ORIGINAL_MODS.md. Here only what scripts of IIS 1 change:
  scripts/IronChests.zs, ChickenChunks.zs, BetterRuildersWands.zs. Each recipe carries a neoforge:mod_loaded condition, IIS 2 loads without the mods.
Mods without a script of their own (BiblioCraft, FramedBlocks, Storage Drawers, Chisel, AE2 wireless terminals, Mouse Tweaks) keep their recipes.
Differences are in docs/PORTING_NOTES.md.
"""
import json
import os

NS = 'iis'
I = lambda x: f'{NS}:{x}'
# recipes of the mods that the scripts replace
OFF = {
    'ironchest': ['chests/copper_iron_chest', 'chests/diamond_crystal_chest', 'chests/diamond_obsidian_chest', 'chests/gold_diamond_chest', 'chests/iron_gold_chest', 'chests/vanilla_copper_chest', 'chests/vanilla_dirt_chest',
                  'chests/vanilla_iron_chest', 'upgrades/copper_to_iron_chest_upgrade', 'upgrades/diamond_to_crystal_chest_upgrade', 'upgrades/diamond_to_obsidian_chest_upgrade', 'upgrades/gold_to_diamond_chest_upgrade',
                  'upgrades/iron_to_gold_chest_upgrade', 'upgrades/wood_to_copper_chest_upgrade', 'upgrades/wood_to_iron_chest_upgrade'],
    'chunkloaders': ['basic_chunk_loader'],
    'constructionwand': ['iron_wand', 'diamond_wand', 'infinity_wand'],
    # D130: Bigger AE2 stands for ExtraCells of IIS 1, its own recipes are a few diamonds. Accelerators and the singularity cell were not in IIS 1
    # D136: compact logic for Project Red of IIS 1. Its circuit plate came from a concrete plate (Project-Red.zs), so does the silicon base of Tiny Redstone;
    # the board of Little Big Redstone goes through the assembler like the conductive plate of Project-Red.zs
    'tinyredstone': ['silicon', 'silicon_compound', 'redstone_panel'],
    'little_big_redstone': ['redstone_circuit_board'],
    'tinygates': [],
    'bigger_ae2': ['4_core_crafting_accelerator', '16_core_crafting_accelerator', '64_core_crafting_accelerator', '256_core_crafting_accelerator', '1024_core_crafting_accelerator',
                   'advanced_fluid_cell_housing', 'advanced_item_cell_housing', 'digital_singularity_cell_component', 'digital_singularity_fluid_storage_cell', 'digital_singularity_item_storage_cell',
                   'quantum_cell_component', 'quantum_fluid_storage_cell', 'quantum_item_storage_cell', 'cell_upgrade/quantum_fluid_storage_cell', 'cell_upgrade/quantum_item_storage_cell'],
}
# nothing makes these any more: IronChests.zs removes them and adds no recipe
HIDDEN = ['ironchest:copper_to_iron_chest_upgrade'] + [f'bigger_ae2:{i}' for i in (
    '4_core_crafting_accelerator', '16_core_crafting_accelerator', '64_core_crafting_accelerator', '256_core_crafting_accelerator', '1024_core_crafting_accelerator', 'advanced_fluid_cell_housing', 'advanced_item_cell_housing',
    'digital_singularity_cell_component', 'digital_singularity_fluid_storage_cell', 'digital_singularity_item_storage_cell')]


def generate(g):
    write, D, GEN, RES = g['write'], g['D'], g['GEN'], g['RES']
    tag_of, ref = g['tag_of'], g['ref']
    recipes = {}
    PANE, CHEST = 'minecraft:glass_pane', 'minecraft:chest'
    P = lambda m: tag_of('plate', m)
    OBS = I('plate_obsidian')

    def ring(rid, out, around, centre):
        recipes[rid] = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['AAA', 'ACA', 'AAA'], 'key': {'A': around, 'C': centre}, 'result': {'id': out, 'count': 1}}

    def rows(rid, out, pattern, key):
        recipes[rid] = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key, 'result': {'id': out, 'count': 1}}

    # --- Iron Chests: IronChests.zs:51-97 chests, :100-157 upgrades ---------------------------------------------------------
    ring('ironchest/copper_chest', 'ironchest:copper_chest', P('Copper'), CHEST)
    ring('ironchest/iron_chest', 'ironchest:iron_chest', P('Iron'), CHEST)
    ring('ironchest/gold_chest', 'ironchest:gold_chest', P('Gold'), 'ironchest:iron_chest')
    rows('ironchest/diamond_chest', 'ironchest:diamond_chest', ['GGG', 'DCD', 'GGG'], {'G': PANE, 'D': P('Diamond'), 'C': 'ironchest:gold_chest'})
    ring('ironchest/crystal_chest', 'ironchest:crystal_chest', PANE, 'ironchest:diamond_chest')
    ring('ironchest/obsidian_chest', 'ironchest:obsidian_chest', OBS, 'ironchest:diamond_chest')
    ring('ironchest/dirt_chest', 'ironchest:dirt_chest', 'minecraft:dirt', CHEST)
    ring('ironchest/upgrade_wood_copper', 'ironchest:wood_to_copper_chest_upgrade', P('Copper'), '#minecraft:planks')
    ring('ironchest/upgrade_wood_iron', 'ironchest:wood_to_iron_chest_upgrade', P('Iron'), '#minecraft:planks')
    ring('ironchest/upgrade_iron_gold', 'ironchest:iron_to_gold_chest_upgrade', P('Gold'), P('Iron'))
    rows('ironchest/upgrade_gold_diamond', 'ironchest:gold_to_diamond_chest_upgrade', ['GGG', 'DCD', 'GGG'], {'G': PANE, 'D': P('Diamond'), 'C': P('Gold')})
    ring('ironchest/upgrade_diamond_crystal', 'ironchest:diamond_to_crystal_chest_upgrade', PANE, OBS)
    ring('ironchest/upgrade_diamond_obsidian', 'ironchest:diamond_to_obsidian_chest_upgrade', OBS, PANE)

    # IronChests.zs:161-168: the arc furnace takes chests apart, oxygen 488 L, 1200 ticks, 96 EU/t
    def arc(rid, chest, outs):
        recipes[f'ironchest/arc_{rid}'] = {'type': f'{NS}:arc_furnace', 'inputs': [{'ingredient': chest, 'count': 1}], 'outputs': [{'id': i, 'count': c} for i, c in outs],
                                           'duration': 1200, 'eut': 96, 'fluid_inputs': [{'ingredient': I('oxygen'), 'amount': 488}]}

    COPPER, WROUGHT, GOLD = ref('ingot', 'AnnealedCopper'), ref('ingot', 'WroughtIron'), 'minecraft:gold_ingot'
    GRAPHITE = ref('dust', 'Graphite')
    arc('copper', 'ironchest:copper_chest', [(COPPER, 8)])
    arc('iron', 'ironchest:iron_chest', [(WROUGHT, 8)])
    arc('gold', 'ironchest:gold_chest', [(WROUGHT, 8), (GOLD, 8)])
    arc('diamond', 'ironchest:diamond_chest', [(WROUGHT, 8), (GOLD, 8), ('minecraft:glass', 2), (GRAPHITE, 16)])
    arc('crystal', 'ironchest:crystal_chest', [(WROUGHT, 8), (GOLD, 8), ('minecraft:glass', 5), (GRAPHITE, 16)])
    arc('obsidian', 'ironchest:obsidian_chest', [(WROUGHT, 8), (GOLD, 8), ('minecraft:obsidian', 8), (GRAPHITE, 16)])

    # --- Chunk Loaders: ChickenChunks.zs:9-11 is the recipe of the big loader, it goes to the block that does the same -------
    rows('chunkloaders/basic_chunk_loader', 'chunkloaders:basic_chunk_loader', [' E ', 'GGG', 'ODO'], {'E': 'minecraft:ender_pearl', 'G': P('Gold'), 'O': OBS, 'D': I('industrial_diamond')})

    # --- Construction Wand: BetterRuildersWands.zs:4-20, wands of Better Builder's Wands ----------------------------------------
    for wand, plate, core in (('iron_wand', P('WroughtIron'), tag_of('ingot', 'WroughtIron')), ('diamond_wand', P('Diamond'), 'minecraft:diamond'), ('infinity_wand', P('NetherStar'), 'minecraft:nether_star')):
        rows(f'constructionwand/{wand}', f'constructionwand:{wand}', [' S ', 'FS ', 'PCH'], {'S': 'minecraft:stick', 'F': '#iis:tools/file', 'P': plate, 'C': core, 'H': '#iis:tools/hammer'})

    # --- Bigger AE2: the quantum cell stands for 16384K of Extra-Cells.zs:251-257, four steps of four over 256K on the assembler ------
    recipes['bigger_ae2/quantum_cell_component'] = {'type': f'{NS}:assembler', 'inputs': [{'ingredient': '#c:circuits/ultimate', 'count': 3}, {'ingredient': 'ae2:cell_component_256k', 'count': 64}, {'ingredient': 'ae2:engineering_processor', 'count': 1}],
                                                    'outputs': [{'id': 'bigger_ae2:quantum_cell_component', 'count': 1}], 'duration': 180, 'eut': 1920}
    recipes['bigger_ae2/quantum_item_storage_cell'] = {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ['bigger_ae2:quantum_cell_component', I('cell_housing_advanced')], 'result': {'id': 'bigger_ae2:quantum_item_storage_cell', 'count': 1}}
    recipes['bigger_ae2/quantum_fluid_storage_cell'] = {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ['bigger_ae2:quantum_cell_component', 'ae2:fluid_cell_housing'], 'result': {'id': 'bigger_ae2:quantum_fluid_storage_cell', 'count': 1}}

    # --- D136: compact logic (Tiny Redstone, Tiny Gates, Little Big Redstone) in the place of Project Red; Project-Red.zs for the parts ---
    recipes['logic/tiny_silicon'] = {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [I('plate_concrete')], 'result': {'id': 'tinyredstone:silicon', 'count': 1}}
    rows('logic/redstone_panel', 'tinyredstone:redstone_panel', ['RRR', 'iii', 'SSS'], {'R': P('RedAlloy'), 'i': 'tinyredstone:silicon', 'S': 'minecraft:smooth_stone_slab'})
    recipes['logic/redstone_circuit_board'] = {'type': f'{NS}:assembler', 'inputs': [{'ingredient': I('coated_circuit_board'), 'count': 1}, {'ingredient': P('RedAlloy'), 'count': 2},
                                                                                     {'ingredient': P('NetherQuartz'), 'count': 1}],
                                               'fluid_inputs': [{'ingredient': I('molten_redstone'), 'amount': 144}],
                                               'outputs': [{'id': 'little_big_redstone:redstone_circuit_board', 'count': 1}], 'duration': 200, 'eut': 16}

    # --- writing ---------------------------------------------------------------------------------------------------------------
    for key, r in recipes.items():
        need = sorted({m for m in OFF if f'"{m}:' in json.dumps(r)})
        out = dict(r)
        if need:
            out['neoforge:conditions'] = [{'type': 'neoforge:mod_loaded', 'modid': m} for m in need]
        write(os.path.join(D, NS, 'recipe', 'moremods', key + '.json'), out)
    with open(os.path.join(g['ROOT'], 'src', 'main', 'resources', NS, 'moremods_recipes.json'), 'w', encoding='utf-8', newline=chr(10)) as f:
        json.dump(sorted(f'{NS}:moremods/{k}' for k in recipes), f, indent=0)
    OVR = os.path.join(GEN, 'packs', 'iis_overrides', 'data')
    n = 0
    for ns, paths in OFF.items():
        for path in paths:
            write(os.path.join(OVR, ns, 'recipe', path + '.json'), {'neoforge:conditions': [{'type': 'neoforge:never'}]})
            n += 1
    tag = os.path.join(D, 'c', 'tags', 'item', 'hidden_from_recipe_viewers.json')
    data = json.load(open(tag, encoding='utf-8'))
    data['values'] += [{'id': i, 'required': False} for i in HIDDEN]
    # Extra-Cells.zs gives the 256K cell a recipe: it must be seen
    shown = {'ae2:item_storage_cell_256k', 'ae2:cell_component_256k'}
    data['values'] = [v for v in data['values'] if (v['id'] if isinstance(v, dict) else v) not in shown]
    write(tag, data)
    return {'recipes': len(recipes), 'off': n}
