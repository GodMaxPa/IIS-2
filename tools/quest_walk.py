"""Quest-by-quest walkthrough check (D150): can a survival player complete every quest of the book, and at what energy tier.

Built on check_reach.py (the recipes of the mod, AE2 and Ender IO), plus what it takes for granted:
  * vanilla recipes from the game jar, minus the ones the override pack switches off or replaces;
  * vanilla items are free only when the world gives them (no recipe at all); vanilla ores do not generate;
  * the Moon and Mars open with their rockets.
Every item gets the lowest energy tier it can be made at (0 = hands, steam and primitive furnaces, 1 = LV ... 8 = UV).

Run from the repository root after gen_content.py and quests.py:
    python -I tools/quest_walk.py                 summary
    python -I tools/quest_walk.py --write         also writes docs/WALKTHROUGH.md
    python -I tools/quest_walk.py --why item_id   the cheapest chain to an item, or what breaks it
    python -I tools/quest_walk.py --chapter 07    every quest of a chapter with its tier
"""
import collections
import contextlib
import glob
import io
import json
import os
import re
import runpy
import sys
import zipfile

ARGS = sys.argv[1:]
sys.argv = ['check_reach.py', 'capsule']
with contextlib.redirect_stdout(io.StringIO()):
    G = runpy.run_path(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'check_reach.py'))
R = 'src/generated/resources'
JAR = 'build/moddev/artifacts/minecraft-patched-26.1.2.117-merged.jar'
INF = 99
TIERS = ['ulv', 'lv', 'mv', 'hv', 'ev', 'iv', 'luv', 'zpm', 'uv', 'max']
TNAME = ['без тока', 'LV', 'MV', 'HV', 'EV', 'IV', 'LuV', 'ZPM', 'UV', 'MAX']
SUFFIX = re.compile(r'_(ulv|lv|mv|hv|ev|iv|luv|zpm|uv|max)$')
MULTI = {'blast', 'vacuum_freezer', 'implosion', 'pyrolyse', 'distillation_tower', 'cracking', 'assembly_line', 'fusion'}
# the world gives these although a vanilla recipe also makes them (9 <-> 1 pairs and the like)
NATURAL = {'wheat', 'slime_ball', 'honeycomb', 'honey_bottle', 'melon_slice', 'bamboo', 'nether_wart', 'ice', 'snow', 'moss_block', 'dripstone_block', 'pointed_dripstone', 'bone_meal', 'bone',
           'white_wool', 'string', 'leather', 'rabbit_hide', 'sugar', 'clay', 'clay_ball', 'mud', 'glowstone', 'glowstone_dust', 'magma_block', 'packed_ice', 'amethyst_shard', 'resin_clump',
           'red_mushroom', 'brown_mushroom', 'pumpkin', 'pumpkin_seeds', 'melon_seeds', 'wheat_seeds', 'beetroot_seeds', 'dried_kelp', 'red_sand', 'sand', 'gravel', 'flint', 'coarse_dirt', 'rooted_dirt',
           'mossy_cobblestone', 'cobweb', 'chest', 'bookshelf', 'book', 'paper', 'saddle', 'lead', 'name_tag', 'nautilus_shell', 'turtle_scute', 'armadillo_scute', 'blaze_powder', 'magma_cream',
           'ender_eye', 'fermented_spider_eye', 'black_dye', 'white_dye', 'blue_dye', 'brown_dye', 'green_dye', 'red_dye', 'yellow_dye', 'orange_dye', 'pink_dye', 'light_blue_dye', 'magenta_dye',
           'light_gray_dye', 'gray_dye', 'cyan_dye', 'purple_dye', 'lime_dye', 'prismarine', 'prismarine_bricks', 'dark_prismarine', 'sea_lantern', 'sponge', 'wet_sponge', 'terracotta', 'stone',
           'smooth_stone', 'deepslate', 'cobbled_deepslate', 'tuff', 'calcite', 'andesite', 'diorite', 'granite', 'sandstone', 'red_sandstone', 'obsidian', 'crying_obsidian', 'blackstone',
           'basalt', 'smooth_basalt', 'netherrack', 'nether_bricks', 'end_stone', 'purpur_block', 'end_rod', 'chorus_fruit', 'popped_chorus_fruit', 'shulker_shell', 'elytra', 'dragon_breath',
           'filled_map', 'spawner', 'totem_of_undying', 'trident', 'heart_of_the_sea', 'nether_star', 'wither_skeleton_skull', 'skeleton_skull', 'zombie_head', 'creeper_head', 'experience_bottle', 'enchanted_book'}
NATURAL = {'minecraft:' + n for n in NATURAL}
NATURAL.add('enderio:broken_spawner')   # drops from a broken vanilla spawner

recipes = list(G['recipes'])
META = G['META']
ptags = G['tags']

# ---------------------------------------------------------------- vanilla data
Z = zipfile.ZipFile(JAR)
NAMES = Z.namelist()
MC_ITEMS = {'minecraft:' + n[len('assets/minecraft/items/'):-5] for n in NAMES if n.startswith('assets/minecraft/items/') and n.endswith('.json')}
vtags = {}
for n in NAMES:
    if n.startswith('data/minecraft/tags/item/') and n.endswith('.json'):
        vtags['minecraft:' + n[len('data/minecraft/tags/item/'):-5]] = [v if isinstance(v, str) else v['id'] for v in json.loads(Z.read(n)).get('values', [])]


def tag_items(t, seen=()):
    out = []
    for v in vtags.get(t, []) + ptags.get(t, []):
        if v.startswith('#'):
            if v[1:] not in seen:
                out += tag_items(v[1:], seen + (t,))
        else:
            out.append(v)
    return out


def vexpand(x):
    if isinstance(x, list):
        out = []
        for y in x:
            out += vexpand(y)
        return out
    if isinstance(x, dict):
        return vexpand(x.get('item') or ('#' + x['tag'] if 'tag' in x else []))
    if x.startswith('#'):
        return tag_items(x[1:]) or ['free:' + x]
    return [x]


def vanilla_recipe(rid, d):
    t = d.get('type', '').replace('minecraft:', '')
    if any(c.get('type') == 'neoforge:never' for c in d.get('neoforge:conditions', [])):
        return None
    res = d.get('result')
    out = res.get('id') if isinstance(res, dict) else res
    if not out:
        return None
    if t == 'crafting_shaped':
        pat = ''.join(d['pattern'])
        reqs = [vexpand(v) for k, v in d['key'].items() if k in pat]
    elif t == 'crafting_shapeless':
        reqs = [vexpand(v) for v in d['ingredients']]
    elif t in ('smelting', 'blasting', 'smoking', 'campfire_cooking', 'stonecutting'):
        reqs = [vexpand(d['ingredient'])]
    elif t == 'smithing_transform':
        reqs = [vexpand(d[k]) for k in ('template', 'base', 'addition') if k in d]
    elif t == 'crafting_transmute':
        reqs = [vexpand(d[k]) for k in ('input', 'material') if k in d]
    else:
        return None
    return ('mc/' + rid, reqs, [out])


vraw = {n[len('data/minecraft/recipe/'):-5]: json.loads(Z.read(n)) for n in NAMES if n.startswith('data/minecraft/recipe/') and n.endswith('.json')}
MC_CRAFTED = set()   # vanilla items the vanilla game makes by a recipe: the world does not hand them out
for rid, d in vraw.items():
    r = vanilla_recipe(rid, d)
    if r:
        MC_CRAFTED.update(r[2])
OFF = []
for f in glob.glob(f'{R}/packs/iis_overrides/data/minecraft/recipe/*.json'):
    rid = os.path.basename(f)[:-5]
    vraw[rid] = json.load(open(f, encoding='utf-8'))
    if vanilla_recipe(rid, vraw[rid]) is None:
        OFF.append(rid)
for rid, d in vraw.items():
    r = vanilla_recipe(rid, d)
    if r:
        recipes.append(r)

# ---------------------------------------------------------------- what the world gives
ITEM_IDS = {os.path.basename(f)[:-5] for f in glob.glob(f'{R}/assets/iis/items/*.json')}


def is_ore(b):
    return (b.startswith(('ore_', 'deepslate_ore_', 'netherrack_ore', 'endstone_ore', 'small_ore')) or '_ore_' in b) and not b.startswith(G['MACHINES'])


veins = json.load(open('src/main/resources/iis/veins.json', encoding='utf-8'))
vdims, sdims = {}, {}
for v in (veins if isinstance(veins, list) else veins['veins']):
    for k in ('primary', 'secondary', 'between', 'sporadic'):
        if v.get(k):
            vdims.setdefault(v[k].split(':')[1], set()).update(v['dimensions'])
for v in json.load(open('src/main/resources/iis/small_ores.json', encoding='utf-8')):
    sdims.setdefault(v['material'].split(':')[1], set()).update(v['dimensions'])
VARIANTS = [('deepslate_small_ore_', sdims, None), ('nether_small_ore_', sdims, 'nether'), ('end_small_ore_', sdims, 'end'), ('small_ore_', sdims, None),
            ('deepslate_ore_', vdims, None), ('nether_ore_', vdims, 'nether'), ('end_ore_', vdims, 'end'), ('ore_', vdims, None)]
NO_VEIN = []


def ore_gate(b):
    """What an ore block waits for: '' = found at once, a rocket = only on the Moon, Mars or the asteroids, None = no vein has it."""
    for prefix, table, only in VARIANTS:
        if b.startswith(prefix):
            d = table.get(b[len(prefix):], set())
            d = d & {only} if only else d - {'nether', 'end'}
            if not d:
                NO_VEIN.append(b)
                return None
            return '' if d & {'overworld', 'nether', 'end'} else 'iis:rocket_t1' if 'iis:moon' in d else 'iis:rocket_t2' if 'iis:mars' in d else 'iis:rocket_t3'
    return ''


tier = {}
for x in G['FLUID_SEEDS']:
    tier['F:' + x] = 0
for x in G['seeds']:
    tier[x] = 0
for b in ITEM_IDS:
    if is_ore(b):
        g = ore_gate(b)
        if g:
            recipes.append(('world/' + b, [[g]], ['iis:' + b]))
        elif g == '':
            tier['iis:' + b] = 0
for f in glob.glob(f'{R}/data/iis/loot_table/blocks/*.json'):
    b = os.path.basename(f)[:-5]
    if is_ore(b) or b.startswith(('deepslate_small', 'nether_', 'end_')):
        names = [part.split('"')[1] for part in open(f, encoding='utf-8').read().split('"name"')[1:]]
        if b in ITEM_IDS:
            recipes.append(('loot/' + b, [['iis:' + b]], names))
recipes.append(('world/moon', [['iis:rocket_t1']], ['iis:moon_rock', 'iis:moon_dirt', 'iis:moon_turf']))
recipes.append(('world/mars', [['iis:rocket_t2']], ['iis:mars_rock', 'iis:mars_dirt', 'iis:mars_turf']))
recipes = [r for r in recipes if not r[0].startswith('world/') or True]

made = set()
for rid, reqs, outs in recipes:
    made.update(outs)
for i in MC_ITEMS:
    if (i not in MC_CRAFTED and not i.endswith('_ore') and not i.startswith('minecraft:raw_')) or i in NATURAL or i.endswith('_smithing_template'):
        tier[i] = 0
# other mods: what no recipe makes comes from the world (AE2 meteorites, presses)
used = set()
for rid, reqs, outs in recipes:
    for r in reqs:
        used.update(r)
for i in NATURAL:
    tier[i] = 0
for i in used:
    if i.startswith('free:') or '__world__' in i:
        tier[i] = 0
    elif not i.startswith(('iis:', 'minecraft:', 'F:', 'conduit:', 'MISSING_TAG')) and i not in made:
        tier[i] = 0


def base(rid):
    if rid in META:
        t, eut = META[rid]
        if t[4:] in MULTI:
            return next((n for n, v in enumerate((8, 32, 128, 512, 2048, 8192, 32768, 131072, 524288)) if eut <= v), 9)
    return 0


def floor(item):
    m = SUFFIX.search(item)
    return TIERS.index(m.group(1)) if m and item.startswith('iis:') else 0


BASE = [base(r[0]) for r in recipes]
best = {}
changed = True
while changed:
    changed = False
    for n, (rid, reqs, outs) in enumerate(recipes):
        v = BASE[n]
        for r in reqs:
            v = max(v, min((tier.get(a, INF) for a in r), default=INF))
            if v >= INF:
                break
        if v >= INF:
            continue
        for o in outs:
            v2 = max(v, floor(o))
            if v2 < tier.get(o, INF):
                tier[o] = v2
                best[o] = n
                changed = True

by_out = collections.defaultdict(list)
for n, (rid, reqs, outs) in enumerate(recipes):
    for o in outs:
        by_out[o].append(n)


def why(item, depth=0, seen=None, out=None, limit=6):
    """The chain that breaks an item (or the cheapest chain when it can be made)."""
    seen = set() if seen is None else seen
    out = [] if out is None else out
    pad = '  ' * depth
    if item in seen or depth > limit:
        return out
    seen.add(item)
    if tier.get(item, INF) < INF:
        if item in best:
            rid, reqs, _ = recipes[best[item]]
            out.append(f'{pad}{item} [{TNAME[tier[item]]}] via {rid}')
            for r in reqs:
                a = min(r, key=lambda x: tier.get(x, INF))
                if tier.get(a, 0) == tier[item] and tier[item] > 0:
                    why(a, depth + 1, seen, out, limit)
        else:
            out.append(f'{pad}{item} [мир]')
        return out
    rs = by_out.get(item)
    if not rs:
        out.append(f'{pad}{item}: НЕТ РЕЦЕПТА')
        return out
    # the recipe with the fewest missing parts
    def missing(n):
        return [r for r in recipes[n][1] if all(tier.get(a, INF) >= INF for a in r)]
    n = min(rs, key=lambda n: len(missing(n)))
    miss = missing(n)
    out.append(f'{pad}{item}: {recipes[n][0]}, не хватает {[m[0] if len(m) == 1 else m[:3] for m in miss][:4]}')
    for m in miss[:4]:
        why(m[0], depth + 1, seen, out, limit)
    return out


# ---------------------------------------------------------------- quests
LANG = {}
lp = 'quests/ftbquests/lang/ru_ru/iis1_quests.json5'
if os.path.exists(lp):
    LANG = json.load(open(lp, encoding='utf-8'))
for f in glob.glob('quests/ftbquests/lang/ru_ru/**/*.json5', recursive=True):
    with contextlib.suppress(Exception):
        LANG.update(json.load(open(f, encoding='utf-8')))


def node(stack):
    """What a task item stands for: a list of needs, each a list of alternatives."""
    comp = stack.get('components', {}) if isinstance(stack, dict) else {}
    iid = stack['id'] if isinstance(stack, dict) else stack
    if 'ftbfiltersystem:filter' in comp:   # "any of": a tag or a list of items
        alts = []
        for kind, arg in re.findall(r'(item_tag|item)\(([^()]+)\)', comp['ftbfiltersystem:filter']):
            alts += (tag_items(arg) or ['MISSING_TAG:' + arg]) if kind == 'item_tag' else [arg]
        return [alts]
    out = [[iid]]
    if 'enderio:conduit' in comp:
        out = [['conduit:' + comp['enderio:conduit']]]
    if 'iis:fluid_content' in comp:
        fl = comp['iis:fluid_content']
        out.append(['F:' + (fl['fluid'] if isinstance(fl, dict) else fl)])
    return out


def cheapest(alts):
    return min(alts, key=lambda a: tier.get(a, INF))


OTHER = set()   # items of other mods the check takes as given


class Quest:
    pass


quests = {}
chapters = []
for f in sorted(glob.glob('quests/ftbquests/chapters/*.json5')):
    d = json.load(open(f, encoding='utf-8'))
    ch = os.path.basename(f)[:-6]
    chapters.append((ch, LANG.get(f'chapter.{d["id"]}.title', ch)))
    for q in d['quests']:
        o = Quest()
        o.id, o.ch, o.deps = q['id'], ch, q.get('dependencies', [])
        o.any = 'one' in str(q.get('dependency_requirement', ''))
        o.nodes, o.other, o.rewards = [], [], []
        for t in q.get('tasks', []):
            if t['type'] == 'item':
                o.nodes += node(t['item'])
            elif t['type'] == 'fluid':
                fl = t['fluid']
                o.nodes.append(['F:' + (fl.get('id') or fl.get('fluid') if isinstance(fl, dict) else fl)])
            else:
                o.other.append(t['type'])
        for r in q.get('rewards', []):
            if r.get('type') == 'item':
                o.rewards.append(r['item']['id'] if isinstance(r['item'], dict) else r['item'])
        for alts in o.nodes:   # other mods: what our tables have no recipe for is taken as given
            for a in alts:
                if not a.startswith(('iis:', 'minecraft:', 'F:', 'conduit:', 'MISSING_TAG')) and a not in made:
                    tier.setdefault(a, 0)
                    OTHER.add(a)
        o.nodes = [cheapest(a) for a in o.nodes]
        o.title = LANG.get(f'quest.{o.id}.title') or (o.nodes[0] if o.nodes else o.id)
        if isinstance(o.title, list):
            o.title = ' '.join(o.title)
        o.tier = max((tier.get(n, INF) for n in o.nodes), default=0)
        o.bad = [n for n in o.nodes if tier.get(n, INF) >= INF]
        quests[o.id] = o


def exists(i):
    if i.startswith('iis:'):
        return i[4:] in ITEM_IDS
    if i.startswith('minecraft:'):
        return i in MC_ITEMS
    return True


# a quest is open when its dependencies can be completed
state = {}


def open_(qid, stack=()):
    if qid in state:
        return state[qid]
    q = quests[qid]
    if qid in stack:
        return True
    deps = [d for d in q.deps if d in quests]
    res = [open_(d, stack + (qid,)) and not quests[d].bad for d in deps]
    state[qid] = (any(res) if q.any else all(res)) if deps else True
    return state[qid]


sys.setrecursionlimit(10000)
for qid in quests:
    open_(qid)
hard = [q for q in quests.values() if q.bad]
locked = [q for q in quests.values() if not q.bad and not state[q.id]]
missing_dep = [(q, d) for q in quests.values() for d in q.deps if d not in quests]
no_item = [(q, n) for q in quests.values() for n in q.nodes if not n.startswith(('F:', 'conduit:')) and not exists(n)]
no_reward = [(q, n) for q in quests.values() for n in q.rewards if not exists(n)]
# a quest that needs a much later tier than a quest standing behind it
inversions = []
for q in quests.values():
    if q.bad:
        continue
    for d in q.deps:
        p = quests.get(d)
        if p and not p.bad and p.tier - q.tier >= 2:
            inversions.append((p, q))
# vanilla items nothing makes any more
dead_vanilla = sorted(i for i in MC_ITEMS if tier.get(i, INF) >= INF)
used_items = set(used)
for q in quests.values():
    used_items.update(q.nodes)


def line(q):
    return f'{q.ch} | {q.title} | {", ".join(q.nodes)[:90]}'


def report():
    out = []
    w = out.append
    w('# Проверка прохождения по квестам (D150)')
    w('')
    w('Пишется автоматически: `python -I tools/quest_walk.py --write` (после `gen_content.py` и `quests.py`). Руками не править.')
    w('')
    w(f'Квестов в книге: {len(quests)}. Нельзя выполнить (предмет не сделать в выживании): **{len(hard)}**. Заперты за ними: **{len(locked)}**. '
      f'Ссылок на несуществующие квесты: {len(missing_dep)}. Предметов заданий, которых нет в игре: {len(no_item)}. Наград, которых нет в игре: {len(no_reward)}.')
    w('')
    w('Ступень квеста — наименьшая ступень энергии, на которой можно сделать его предметы: без тока (руки, пар, примитивные печи), LV, MV … UV.')
    w('')
    w('## Главы')
    w('')
    w('| Глава | Квестов | ' + ' | '.join(TNAME[:9]) + ' | Не выполнить | Заперто |')
    w('|---|---|' + '---|' * 11)
    for ch, title in chapters:
        qs = [q for q in quests.values() if q.ch == ch]
        cnt = collections.Counter(q.tier for q in qs if not q.bad)
        w(f'| {ch[:2]} {title} | {len(qs)} | ' + ' | '.join(str(cnt.get(t, '')) for t in range(9)) + f' | {sum(1 for q in qs if q.bad) or ""} | {sum(1 for q in qs if q in locked) or ""} |')
    w('')
    w('## Квесты, которые нельзя выполнить')
    w('')
    for q in sorted(hard, key=lambda q: q.ch):
        w(f'- **{q.title}** ({q.ch})')
        for b in q.bad:
            for l in why(b, limit=5)[:8]:
                w('  - `' + l.strip() + '`' if l.startswith('  ') else '  - ' + l)
    w('')
    w('## Квест раньше, чем его ступень')
    w('')
    w('Квест, без которого не открыть следующий, требует ступень на две и более выше, чем сам следующий.')
    w('')
    for p, q in sorted(inversions, key=lambda x: (x[1].ch, x[0].tier - x[1].tier)):
        w(f'- {q.ch}: «{q.title}» [{TNAME[q.tier]}] ждёт «{p.title}» [{TNAME[p.tier]}] ({p.ch})')
    w('')
    w('## Ванильные предметы, которые больше нечем сделать')
    w('')
    w('Используются в рецептах или квестах: ' + (', '.join(i[10:] for i in dead_vanilla if i in used_items) or 'нет') + '.')
    w('')
    w('Остальные (рецепт выключен или нужна ванильная руда, которой в мире нет): ' + ', '.join(i[10:] for i in dead_vanilla if i not in used_items) + '.')
    w('')
    return '\n'.join(out)


if ARGS[:1] == ['--why']:
    for a in ARGS[1:]:
        print('\n'.join(why(a if ':' in a else 'iis:' + a, limit=12)))
elif ARGS[:1] == ['--chapter']:
    for q in sorted((q for q in quests.values() if q.ch.startswith(ARGS[1])), key=lambda q: q.tier):
        print(('НЕТ ' if q.bad else 'ЗАП ' if q in locked else '    ') + (TNAME[q.tier] if not q.bad else '?').ljust(9), line(q))
else:
    print(f'{len(recipes)} recipes, {len(OFF)} vanilla recipes off, {len(quests)} quests')
    print(f'hard {len(hard)}, locked behind {len(locked)}, missing deps {len(missing_dep)}, no item {len(no_item)}, no reward {len(no_reward)}, inversions {len(inversions)}')
    print('ore blocks no vein has:', sorted(set(NO_VEIN)))
    print('dead vanilla in use:', [i for i in dead_vanilla if i in used_items])
    for q in sorted(hard, key=lambda q: q.ch):
        print('HARD', line(q), '->', q.bad)
    for q, n in no_item:
        print('NOITEM', line(q), n)
    for q, n in no_reward[:20]:
        print('NOREWARD', line(q), n)
    if '--write' in ARGS:
        open('docs/WALKTHROUGH.md', 'w', encoding='utf-8', newline='\n').write(report())
        print('docs/WALKTHROUGH.md written')
