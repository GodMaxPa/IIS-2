"""Owner's notes of 09.11, items 10-11 (D144): vanilla recipes brought to what IIS 1 had.

Sources (forServerClient, the IIS 1 pack):
- config/GregTech/Recipes.cfg: storageblockcrafting / storageblockdecrafting all false (blocks of ingots, gems and dusts only in
  machines: compressor, cutter, macerator); disabledrecipes torchesFromCoal, wood2charcoalsmelting, enderchest = true;
  recipereplacements Iron.Bucket, Iron/Gold armour, Iron.Shears = true; harderrecipes sugarpaper = true.
- GT5 GT_CraftingRecipeLoader: bucket "XhX/ X " from plates (DELETE_ALL_OTHER_SHAPED_RECIPES), minecart from plates and wheels,
  chainmail from steel rings, torches from sulfur (2) and tricalcium phosphate (6), pistons from other metals, TNT, glass bottle
  and cookie only from machines, sticks from saplings and grass with a saw, feathers from paper, bowls with a knife,
  hammer conversions (stone -> cobble -> gravel -> sand); GT_RecipeRegistrator sShapesA (armour, shears).
- scripts/Vanilla.zs: no repeater, comparator, dispenser, dropper, TNT minecart, blaze powder, ender chest by hand; saddle with a knife.
  The replacements are GT assembler recipes (ProcessingStone, ProcessingStoneCobble, ProcessingCrafting, GT_MachineRecipeLoader:628, 1360).
- Torch from a stick and sticky resin, 4 (owner, "as in IIS 1").
Copper armour did not exist in IIS 1; it follows the iron rule (AI).
"""
import os

NS = 'iis'
I = lambda x: f'{NS}:{x}'
MC = lambda x: f'minecraft:{x}'
# vanilla recipes that IIS 1 did not have
OFF = ['iron_block', 'gold_block', 'copper_block', 'diamond_block', 'emerald_block', 'lapis_block', 'redstone_block', 'coal_block', 'glowstone',
       'iron_ingot_from_iron_block', 'gold_ingot_from_gold_block', 'copper_ingot', 'copper_ingot_from_waxed_copper_block', 'diamond', 'emerald',
       'lapis_lazuli', 'redstone', 'coal', 'torch', 'soul_torch', 'charcoal', 'bucket', 'minecart', 'shears', 'repeater', 'comparator', 'dispenser',
       'dropper', 'tnt_minecart', 'paper', 'sugar_from_sugar_cane', 'tnt', 'glass_bottle', 'cookie', 'ender_chest', 'blaze_powder', 'saddle'] \
    + [f'{m}_{p}' for m in ('iron', 'golden', 'copper') for p in ('helmet', 'chestplate', 'leggings', 'boots')] \
    + [  # D145 (owner, 11.11: "very thoroughly"): NuggetsFix.zs/IngotsFix.zs (no nuggets or ingots by hand), storage blocks of every kind,
       # GT_IndustialCraftRecipesLoader:227 flint and steel, ToolFix.zs:38 arrow, Chisel.zs:9 sandstone, GT_IC2.zs:660 lead,
       # Vanilla.zs:hopper_minecart; the vanilla smelting of tools into one nugget gives way to the GT amounts (below)
       'iron_nugget', 'gold_nugget', 'copper_nugget', 'netherite_block', 'netherite_ingot_from_netherite_block', 'raw_iron_block', 'raw_iron',
       'raw_gold_block', 'raw_gold', 'raw_copper_block', 'raw_copper', 'flint_and_steel', 'arrow', 'sandstone', 'lead', 'hopper_minecart',
       'iron_nugget_from_smelting', 'iron_nugget_from_blasting', 'gold_nugget_from_smelting', 'gold_nugget_from_blasting',
       'copper_nugget_from_smelting', 'copper_nugget_from_blasting',
       # things that did not exist in 1.7.10, made by the IIS 1 rule for iron things (recipereplacements: plates and tools); AI
       'iron_trapdoor', 'iron_chain', 'copper_chain', 'copper_bars', 'copper_door', 'copper_trapdoor', 'copper_chest', 'lightning_rod', 'spyglass',
       'blast_furnace', 'stonecutter', 'smithing_table', 'shield', 'crossbow']
# ingots of metal in a vanilla tool or armour piece (GT ItemData), for the furnace (half, in nuggets) and machines (all)
METAL_IN = {'pickaxe': 3, 'shovel': 1, 'axe': 3, 'hoe': 2, 'sword': 2, 'spear': 1, 'helmet': 5, 'chestplate': 8, 'leggings': 7, 'boots': 4,
            'horse_armor': 6, 'nautilus_armor': 5}
VANILLA_METALS = {'iron': ('Iron', 'iron'), 'golden': ('Gold', 'gold'), 'copper': ('Copper', 'copper')}
VANILLA_BLOCKS = {'Iron': 'iron_block', 'Gold': 'gold_block', 'Copper': 'copper_block', 'Diamond': 'diamond_block', 'Emerald': 'emerald_block',
                  'Lapis': 'lapis_block', 'Redstone': 'redstone_block', 'Coal': 'coal_block'}
ARC_INTO = {'Iron': 'WroughtIron', 'Copper': 'AnnealedCopper'}   # GT mSmeltInto of the arc furnace
ARMOUR ={'helmet': ['PPP', 'PhP'], 'chestplate': ['PhP', 'PPP', 'PPP'], 'leggings': ['PPP', 'PhP', 'P P'], 'boots': ['P P', 'PhP']}


def generate(g):
    D, OVR, write, shaped, shapeless, tag_of = g['D'], g['OVR'], g['write'], g['shaped'], g['shapeless'], g['tag_of']
    rec = {}
    ing = lambda i, c=1: {'ingredient': i, 'count': c}
    out = lambda i, c=1: {'id': i, 'count': c}

    def mrec(rtype, rid, inputs, outputs, dur, eut, fin=None):
        r = {'type': f'{NS}:{rtype}', 'inputs': inputs, 'outputs': outputs, 'duration': int(dur), 'eut': int(eut)}
        if fin:
            r['fluid_inputs'] = [fin]
        rec[f'{rtype}/{rid}'] = r

    def add_blasting(name, ingredient, item, count):
        g['add_recipe'](name, {'type': 'minecraft:blasting', 'category': 'misc', 'ingredient': ingredient, 'result': g['result'](item, count),
                               'experience': 0.1, 'cookingtime': 100})

    never = {'neoforge:conditions': [{'type': 'neoforge:never'}]}
    for rid in OFF:
        write(os.path.join(OVR, 'data', 'minecraft', 'recipe', rid + '.json'), never)

    P = lambda m: tag_of('plate', m)
    # --- torches -----------------------------------------------------------------------------------------------------------------
    shaped('iis1/torch_resin', MC('torch'), 4, ['R', 'S'], {'R': I('sticky_resin'), 'S': MC('stick')})
    shaped('iis1/torch_sulfur', MC('torch'), 2, ['C', 'S'], {'C': tag_of('dust', 'Sulfur'), 'S': MC('stick')})
    shaped('iis1/torch_phosphate', MC('torch'), 6, ['C', 'S'], {'C': tag_of('dust', 'TricalciumPhosphate'), 'S': MC('stick')})
    shaped('iis1/soul_torch_resin', MC('soul_torch'), 4, ['R', 'S', 'B'], {'R': I('sticky_resin'), 'S': MC('stick'), 'B': '#minecraft:soul_fire_base_blocks'})
    # --- iron things from plates -------------------------------------------------------------------------------------------------
    shaped('iis1/bucket', MC('bucket'), 1, ['XhX', ' X '], {'X': P('Iron')})
    shaped('iis1/shears', MC('shears'), 1, ['hP', 'Pf'], {'P': P('Iron')})
    for wheels, plate in (('iron', 'Iron'), ('steel', 'Steel')):
        shaped(f'iis1/minecart_{wheels}', MC('minecart'), 1, [' h ', 'PwP', 'WPW'], {'P': P(plate), 'W': I(f'minecart_wheels_{wheels}')})
    for m, mat in (('iron', 'Iron'), ('golden', 'Gold'), ('copper', 'Copper')):
        for part, pat in ARMOUR.items():
            shaped(f'iis1/{m}_{part}', MC(f'{m}_{part}'), 1, pat, {'P': P(mat)}, 'equipment')
    for part, pat in (('helmet', ['RRR', 'RhR']), ('chestplate', ['RhR', 'RRR', 'RRR']), ('leggings', ['RRR', 'RhR', 'R R']), ('boots', ['R R', 'RhR'])):
        shaped(f'iis1/chainmail_{part}', MC(f'chainmail_{part}'), 1, pat, {'R': tag_of('ring', 'Steel')}, 'equipment')
    for mat in ('Bronze', 'Aluminium', 'Steel', 'Titanium'):
        shaped(f'iis1/piston_{mat.lower()}', MC('piston'), 1, ['WWW', 'CBC', 'CRC'],
               {'W': '#minecraft:planks', 'C': '#c:cobblestones', 'R': MC('redstone'), 'B': tag_of('ingot', mat)}, 'redstone')
    # --- paper and sugar with a mortar, saddle with a knife -------------------------------------------------------------------------
    shaped('iis1/dust_paper', I('dust_paper'), 2, ['SSS', ' m '], {'S': MC('sugar_cane')})
    shaped('iis1/paper', MC('paper'), 2, [' C ', 'SSS', ' C '], {'S': tag_of('dust', 'Paper'), 'C': MC('stone_slab')})
    shaped('iis1/sugar', MC('sugar'), 1, ['Sm'], {'S': MC('sugar_cane')})
    shaped('iis1/saddle', MC('saddle'), 1, ['SkS', 'LLL', 'LWL'], {'S': MC('string'), 'L': MC('leather'), 'W': '#minecraft:wool'}, 'equipment')
    # --- GT conveniences ---------------------------------------------------------------------------------------------------------
    shaped('iis1/stick_sapling', MC('stick'), 1, ['s', 'X'], {'X': '#minecraft:saplings'})
    shaped('iis1/stick_dead_bush', MC('stick'), 2, ['s', 'X'], {'X': MC('dead_bush')})
    shaped('iis1/stick_grass', MC('stick'), 2, ['s', 'X'], {'X': MC('short_grass')})
    shaped('iis1/feather', MC('feather'), 2, ['P k'], {'P': MC('paper')})
    shaped('iis1/bowl', MC('bowl'), 1, ['k', 'X'], {'X': '#minecraft:planks'})
    for rid, src, dst in (('cobblestone', 'stone', 'cobblestone'), ('gravel', 'cobblestone', 'gravel'), ('sand', 'gravel', 'sand'),
                          ('cracked_stone_bricks', 'stone_bricks', 'cracked_stone_bricks')):
        shaped(f'iis1/hammer_{rid}', MC(dst), 1, ['h', 'X'], {'X': MC(src)})
    # (no 2-button recipe: Vanilla.zs removes GT's "S/S" -> 2 stone buttons)
    # --- D145 -----------------------------------------------------------------------------------------------------------------------
    ref, mat_by_name, mass, smelting = g['ref'], g['mat_by_name'], g['mass'], g['smelting']
    shaped('iis1/flint_and_steel', MC('flint_and_steel'), 1, ['S ', ' F'], {'S': tag_of('nugget', 'Steel'), 'F': MC('flint')}, 'equipment')
    shaped('iis1/arrow', MC('arrow'), 1, ['  A', ' S ', 'F  '], {'A': I('arrow_head_flint'), 'S': MC('stick'), 'F': MC('feather')}, 'equipment')
    shaped('iis1/lead', MC('lead'), 1, ['SS ', 'SR ', '  S'], {'S': MC('string'), 'R': I('sticky_resin')}, 'equipment')
    mrec('compressor', 'sandstone', [ing('#c:sands/colorless', 4)], [out(MC('sandstone'))], 300, 2)
    mrec('assembler', 'hopper_minecart', [ing(MC('minecart')), ing(MC('hopper'))], [out(MC('hopper_minecart'))], 400, 4)
    shaped('iis1/iron_trapdoor', MC('iron_trapdoor'), 1, ['PPh', 'PP '], {'P': P('Iron')}, 'redstone')
    shaped('iis1/copper_trapdoor', MC('copper_trapdoor'), 1, ['PPh', 'PP '], {'P': P('Copper')}, 'redstone')
    shaped('iis1/copper_door', MC('copper_door'), 3, ['XX ', 'XXh', 'XX '], {'X': P('Copper')}, 'redstone')
    shaped('iis1/iron_chain', MC('iron_chain'), 1, ['R', 'R', 'R'], {'R': tag_of('ring', 'Iron')}, 'building')
    shaped('iis1/copper_chain', MC('copper_chain'), 1, ['R', 'R', 'R'], {'R': tag_of('rod', 'Copper')}, 'building')   # GT has no copper rings
    shaped('iis1/copper_bars', MC('copper_bars'), 8, [' w ', 'XXX', 'XXX'], {'X': tag_of('rod', 'Copper')}, 'building')
    shaped('iis1/copper_chest', MC('copper_chest'), 1, ['PPP', 'PCP', 'PPP'], {'P': P('Copper'), 'C': '#c:chests/wooden'}, 'building')
    shaped('iis1/lightning_rod', MC('lightning_rod'), 1, ['R', 'R', 'R'], {'R': tag_of('rod', 'Copper')}, 'redstone')
    shaped('iis1/spyglass', MC('spyglass'), 1, [' A ', ' P ', ' P '], {'A': MC('amethyst_shard'), 'P': P('Copper')}, 'equipment')
    shaped('iis1/blast_furnace', MC('blast_furnace'), 1, ['PPP', 'PFP', 'SSS'], {'P': P('Iron'), 'F': MC('furnace'), 'S': MC('smooth_stone')}, 'misc')
    shaped('iis1/stonecutter', MC('stonecutter'), 1, [' P ', 'SSS'], {'P': P('Iron'), 'S': MC('stone')}, 'misc')
    shaped('iis1/smithing_table', MC('smithing_table'), 1, ['PP', 'WW', 'WW'], {'P': P('Iron'), 'W': '#minecraft:planks'}, 'misc')
    shaped('iis1/shield', MC('shield'), 1, ['WPW', 'WWW', ' W '], {'P': P('Iron'), 'W': '#minecraft:planks'}, 'equipment')
    shaped('iis1/crossbow', MC('crossbow'), 1, ['SPS', 'THT', ' S '], {'S': MC('stick'), 'P': P('Iron'), 'T': MC('string'), 'H': MC('tripwire_hook')}, 'equipment')
    # vanilla tools and armour back into metal: furnace half in nuggets, macerator all as dust, arc furnace all as ingots (GT)
    for pre, (mat, short) in VANILLA_METALS.items():
        nug, dust, arc = f'minecraft:{short}_nugget', ref('dust', mat), ref('ingot', ARC_INTO.get(mat, mat))
        for part, n in METAL_IN.items():
            item = MC(f'{pre}_{part}')
            smelting(f'iis1/smelt_{pre}_{part}', item, nug, n * 9 // 2, 0.1)
            add_blasting(f'iis1/blast_{pre}_{part}', item, nug, n * 9 // 2)
            mrec('macerator', f'vanilla_{pre}_{part}', [ing(item)], [out(dust, n)], 200 * n, 2)
            mrec('arc_furnace', f'vanilla_{pre}_{part}', [ing(item)], [out(arc, n)], mass(mat) * n, 96,
                 {'ingredient': I('oxygen'), 'amount': mass(mat) * n})
    for part, rings in (('helmet', 5), ('chestplate', 8), ('leggings', 7), ('boots', 4)):   # steel rings: a quarter of an ingot each
        item = MC(f'chainmail_{part}')
        smelting(f'iis1/smelt_chainmail_{part}', item, 'minecraft:iron_nugget', rings * 9 // 8, 0.1)
        mrec('macerator', f'vanilla_chainmail_{part}', [ing(item)], [out(ref('small_dust', 'Steel'), rings)], 100 * rings, 2)
    # storage blocks: only machines make them (compressor), and every block goes back: macerator 9 dust, arc furnace and furnace 9 ingots
    n_blocks = 0
    for n, m in sorted(mat_by_name.items()):
        blk = VANILLA_BLOCKS.get(n) or ref('block', n)
        if not blk:
            continue
        mid = m['id']
        if ref('dust', n):
            mrec('macerator', f'block_{mid}', [ing(blk)], [out(ref('dust', n), 9)], max(400, mass(n) * 9), 2)
        if ref('ingot', n):
            into = ARC_INTO.get(n, n)
            if ref('ingot', into):
                mrec('arc_furnace', f'block_{mid}', [ing(blk)], [out(ref('ingot', into), 9)], mass(n) * 9, 96,
                     {'ingredient': I('oxygen'), 'amount': mass(n) * 9})
            if not m['blastFurnaceRequired'] and 'NO_SMELTING' not in m['subTags']:
                smelting(f'iis1/smelt_block_{mid}', blk, ref('ingot', n), 9, 0.7)
        n_blocks += 1
    # --- redstone parts in the assembler -----------------------------------------------------------------------------------------
    redstone = {'ingredient': I('molten_redstone'), 'amount': 144}
    concrete = {'ingredient': I('molten_concrete'), 'amount': 144}
    mrec('assembler', 'repeater', [ing(MC('stone'), 3), ing(MC('redstone_torch'), 2)], [out(MC('repeater'))], 100, 4, redstone)
    mrec('assembler', 'repeater_concrete', [ing(MC('redstone_torch'), 2), ing(MC('redstone'))], [out(MC('repeater'))], 800, 1, concrete)
    mrec('assembler', 'comparator', [ing(MC('redstone_torch'), 3), ing('#c:gems/quartz')], [out(MC('comparator'))], 800, 1, concrete)
    mrec('assembler', 'dropper', [ing('#c:cobblestones', 7), ing(MC('redstone'))], [out(MC('dropper'))], 400, 4)
    mrec('assembler', 'dispenser', [ing('#c:cobblestones', 7), ing(MC('bow'))], [out(MC('dispenser'))], 400, 4, redstone)
    mrec('assembler', 'tnt_minecart', [ing(MC('minecart')), ing(MC('tnt'))], [out(MC('tnt_minecart'))], 400, 4)
    for rid, r in rec.items():
        write(os.path.join(D, NS, 'recipe', 'processing', rid + '.json'), r)
    return {'off': len(OFF), 'machine': len(rec), 'blocks': n_blocks}
