"""No magic (D137, owner's decision): no enchanting, no potions, no beacon, conduit, totem, enchanted golden apple, bottle o' enchanting.

What it writes into the iis_overrides pack (data/minecraft/...):
  recipes of the magic blocks switched off; villager trades: trades that give or want magic items leave the trade lists, enchanting functions
  leave the other trades (the item stays plain). Into data/iis: the global loot modifier iis:no_magic that takes magic items out of all loot
  and strips enchantments (ru.iis.world.NoMagic). The hidden tag for recipe viewers gets the magic items.
Witches, evokers and the Ender IO soul and experience machines stay (owner's answers).
"""
import json
import os
import zipfile

MAGIC = ['enchanting_table', 'brewing_stand', 'beacon', 'conduit', 'enchanted_book', 'splash_potion', 'lingering_potion', 'tipped_arrow', 'experience_bottle',
         'totem_of_undying', 'enchanted_golden_apple']
RECIPES_OFF = ['enchanting_table', 'brewing_stand', 'beacon', 'conduit', 'tipped_arrow']
ENCHANT_FUNCTIONS = ('minecraft:enchant_with_levels', 'minecraft:enchant_randomly', 'minecraft:set_enchantments', 'minecraft:enchanted_count_increase')


def magic_stack(s):
    """A trade side ({id, components}) that is magic: the listed items, and every potion but plain water."""
    if not isinstance(s, dict):
        return False
    i = s.get('id', '').replace('minecraft:', '')
    if i in MAGIC:
        return True
    if i == 'potion':
        return (s.get('components') or {}).get('minecraft:potion_contents', {}).get('potion') != 'minecraft:water'
    return False


def generate(g):
    write, GEN, D, ROOT = g['write'], g['GEN'], g['D'], g['ROOT']
    OVR = os.path.join(GEN, 'packs', 'iis_overrides', 'data', 'minecraft')
    for r in RECIPES_OFF:
        write(os.path.join(OVR, 'recipe', r + '.json'), {'neoforge:conditions': [{'type': 'neoforge:never'}]})

    jar = os.path.join(ROOT, 'build', 'moddev', 'artifacts', 'minecraft-patched-26.1.2.117-merged.jar')
    removed, stripped = [], []
    with zipfile.ZipFile(jar) as z:
        trades = {n[len('data/minecraft/villager_trade/'):-5]: json.loads(z.read(n)) for n in z.namelist()
                  if n.startswith('data/minecraft/villager_trade/') and n.endswith('.json')}
        tags = {n[len('data/minecraft/tags/villager_trade/'):-5]: json.loads(z.read(n)) for n in z.namelist()
                if n.startswith('data/minecraft/tags/villager_trade/') and n.endswith('.json')}
    gone = set()
    for tid, t in trades.items():
        if magic_stack(t.get('gives')) or magic_stack(t.get('wants')) or magic_stack(t.get('additional_wants')):
            gone.add(f'minecraft:{tid}')
            removed.append(tid)
            continue
        mods = t.get('given_item_modifiers') or []
        if any(m.get('function') in ENCHANT_FUNCTIONS for m in mods):
            keep = []
            for m in mods:
                if m.get('function') in ENCHANT_FUNCTIONS:
                    continue
                # the filter that throws the trade away when the item came out without enchantments
                if m.get('function') == 'minecraft:filtered' and 'minecraft:enchantments' in json.dumps(m.get('item_filter', {})):
                    continue
                keep.append(m)
            t = dict(t)
            if keep:
                t['given_item_modifiers'] = keep
            else:
                t.pop('given_item_modifiers', None)
            write(os.path.join(OVR, 'villager_trade', tid + '.json'), t)
            stripped.append(tid)
    # trade lists without the magic trades
    for tag, data in tags.items():
        values = data.get('values', [])
        kept = [v for v in values if (v if isinstance(v, str) else v.get('id')) not in gone]
        if len(kept) != len(values):
            write(os.path.join(OVR, 'tags', 'villager_trade', tag + '.json'), {'replace': True, 'values': kept})

    # the global loot modifier
    write(os.path.join(D, 'iis', 'loot_modifiers', 'no_magic.json'), {'type': 'iis:no_magic', 'conditions': []})
    # NeoForge 26.1: every file under loot_modifiers is a modifier of its own, there is no global list any more
    tag = os.path.join(D, 'c', 'tags', 'item', 'hidden_from_recipe_viewers.json')
    data = json.load(open(tag, encoding='utf-8'))
    data['values'] += [{'id': i, 'required': False} for i in HIDDEN]
    write(tag, data)
    g['lang']['en_us']['message.iis.no_magic'] = 'There is no magic in this world'
    g['lang']['ru_ru']['message.iis.no_magic'] = 'Магии в этом мире нет'
    return {'trades_removed': len(removed), 'trades_plain': len(stripped)}


HIDDEN = [f'minecraft:{i}' for i in MAGIC]
