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
    + [f'{m}_{p}' for m in ('iron', 'golden', 'copper') for p in ('helmet', 'chestplate', 'leggings', 'boots')]
ARMOUR = {'helmet': ['PPP', 'PhP'], 'chestplate': ['PhP', 'PPP', 'PPP'], 'leggings': ['PPP', 'PhP', 'P P'], 'boots': ['P P', 'PhP']}


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
    shaped('iis1/stone_button', MC('stone_button'), 2, ['S', 'S'], {'S': MC('stone')}, 'redstone')
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
    return {'off': len(OFF), 'machine': len(rec)}
