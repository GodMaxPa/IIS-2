"""The quest book rebuilt around IIS 2 (D152, owner 16.11: "rewrite the book much more, lean on what IIS 2 has").

Runs at the end of quests.py over the chapters it wrote:
  * the progress chapters of the IIS 1 book ("First Aluminium", "Good Circuit", "Big Chemistry" ...) are taken apart; their quests go into
    chapters by energy tier: Steam and steel, LV, MV ... UV. A quest's tier is the lowest tier its items can be made at in IIS 2
    (tools/quest_walk.py) and never lower than the tier of the quests it waits for. Inside a chapter the quests of one old chapter
    keep their mutual layout, squeezed, one block under another;
  * every chapter opens with a quest written for IIS 2: what the tier is and what it is for;
  * every quest of the IIS 1 stock gets a text made from IIS 2 data: its tier, the machine recipe that makes its item, where its ore
    lies (veins.json). What the old text said about vein heights is cut out; the rest of it stays below.
Topical chapters (general progress, machines, rails, AE2, Ender IO, space ...) and our own chapters keep their layout.
"""
import contextlib
import glob
import io
import json
import os
import re
import runpy
import sys
import zipfile

TIER_KEYS = ['steam', 'lv', 'mv', 'hv', 'ev', 'iv', 'luv', 'zpm', 'uv']
EU = [0, 32, 128, 512, 2048, 8192, 32768, 131072, 524288]
# old chapters that are taken apart (file name without the number)
POOL = ['steam__steel', 'circuit__turbine__pump', 'first_aluminium', 'good_circuit', 'nether__lbb', 'more_multiblocks', 'polyethylene__pgs', 'hv__cleanroom',
        'vacuum_frizer__lst', 'titanium__start_of_me', 'big_chemistry', 'dtower__icompressor', 'very_nice_equipment', 'cmbengine_lturb_odp',
        'al_nuc_reactor_odp_iii', 'n_star__fusion_reactor', 'naquadah__fusion_ii', 'large_plasma_generator', 'rocket__odp__array', 'end_game_content']
# the book in its new order: our opening, the tiers, then the topical chapters as they were
FRONT = ['landing', 'bronze']
TOPICAL = ['g_general_progress', 'g_quality_of_life', 'g_sb_machines', 'g_multiblocks', 'project_red', 'rails__trains', 'cross_breeding', 'ae2_proc__cont',
           'advanced_me_network', 'portable_and_wireless', 'space_rocket__moon', 'enderio_features']
HEAD = {
    'steam': ('Пар и сталь', 'Steam and steel', 'iis:boiler_coal_bronze',
              'Первые машины работают на паре: котёл греет воду, пар идёт по бронзовым трубам в машины.\n\nДревесный уголь жгут в угольной яме, кокс и креозот даёт коксовая печь, сталь варит кирпичная доменная печь. Примитивный шахтокопатель и добытчик малых руд помогают искать жилы.\n\nЗдесь собрано всё, что делается без электричества. Цель — сталь и каркас механизма LV.',
              'The first machines run on steam: a boiler heats water, steam goes to machines through bronze pipes.\n\nCharcoal is burnt in a charcoal pile, the coke oven gives coke and creosote, the bricked blast furnace makes steel. The primitive mine digger and small ore miner help to find veins.\n\nEverything made without electricity is gathered here. The goal is steel and the LV machine casing.'),
    'lv': ('LV: первое электричество', 'LV: the first electricity', 'iis:hull_lv',
           'Напряжение LV — до 32 EU/т. Первый генератор — паровая турбина: пар у вас уже есть.\n\nПрограммируемая схема в слоте машины выбирает рецепт, когда из одного и того же делается разное. В гибочном станке: слиток гнётся в пластину без схемы, схема 2 — двойная пластина, 4 — фольга, 12 — капсулы.\n\nМашина сама берёт рецепт, которому нужно больше всего из лежащего в слотах.\n\nЗдесь собрано всё, чему хватает LV.',
           'LV is up to 32 EU/t. The first generator is the steam turbine: you already have steam.\n\nA programmed circuit in a machine slot picks the recipe when the same input makes different things. In the bender: an ingot bends into a plate with no circuit, circuit 2 is a double plate, 4 is foil, 12 is capsules.\n\nA machine takes the recipe that needs the most of what lies in its slots.\n\nEverything LV is enough for is gathered here.'),
    'mv': ('MV', 'MV', 'iis:hull_mv', 'Напряжение MV — до 128 EU/т. Здесь собрано всё, чему нужна ступень MV: её машины, материалы и многоблоки.', 'MV is up to 128 EU/t. Everything that needs the MV tier is gathered here: its machines, materials and multiblocks.'),
    'hv': ('HV', 'HV', 'iis:hull_hv', 'Напряжение HV — до 512 EU/т. Здесь собрано всё, чему нужна ступень HV.', 'HV is up to 512 EU/t. Everything that needs the HV tier is gathered here.'),
    'ev': ('EV', 'EV', 'iis:hull_ev', 'Напряжение EV — до 2048 EU/т. Здесь собрано всё, чему нужна ступень EV.', 'EV is up to 2048 EU/t. Everything that needs the EV tier is gathered here.'),
    'iv': ('IV', 'IV', 'iis:hull_iv', 'Напряжение IV — до 8192 EU/т. Здесь собрано всё, чему нужна ступень IV.', 'IV is up to 8192 EU/t. Everything that needs the IV tier is gathered here.'),
    'luv': ('LuV', 'LuV', 'iis:hull_luv', 'Напряжение LuV — до 32 768 EU/т. Здесь собрано всё, чему нужна ступень LuV.', 'LuV is up to 32,768 EU/t. Everything that needs the LuV tier is gathered here.'),
    'zpm': ('ZPM', 'ZPM', 'iis:hull_zpm', 'Напряжение ZPM — до 131 072 EU/т. Здесь собрано всё, чему нужна ступень ZPM.', 'ZPM is up to 131,072 EU/t. Everything that needs the ZPM tier is gathered here.'),
    'uv': ('UV', 'UV', 'iis:hull_uv', 'Напряжение UV — до 524 288 EU/т. Вершина пути Кибера: здесь собрано всё, чему нужна ступень UV.', 'UV is up to 524,288 EU/t. The top of the Cyber path: everything that needs the UV tier is gathered here.'),
}
DIMS = {'overworld': ('Верхний мир', 'Overworld'), 'nether': ('Нижний мир', 'Nether'), 'end': ('Край', 'End'), 'iis:moon': ('Луна', 'Moon'),
        'iis:mars': ('Марс', 'Mars'), 'iis:asteroids': ('Астероиды', 'Asteroids')}
TNAME = (['без электричества', 'LV', 'MV', 'HV', 'EV', 'IV', 'LuV', 'ZPM', 'UV', 'MAX'], ['no electricity', 'LV', 'MV', 'HV', 'EV', 'IV', 'LuV', 'ZPM', 'UV', 'MAX'])
WORDS = {'tier': ('Доступно с', 'Available from'), 'make': ('Один из способов', 'One way'), 'where': ('Где искать', 'Where to look'), 'circuit': ('схема', 'circuit'),
         'sec': ('с', 's'), 'furnace': ('Печь', 'Furnace'), 'vein': ('жила', 'vein'), 'drops': ('Выпадает из', 'Drops from'),
         'crop': ('Урожай культуры: выращивается на палочках для грядки и выводится скрещиванием', 'A crop harvest: grown on crop sticks and bred by crossbreeding'),
         'recycler': ('Утилизатор: из любого предмета с шансом 12,5 %', 'Recycler: from any item, 12.5 % chance'), 'nasa': ('Верстак NASA', 'NASA workbench'),
         'reactor': ('Отработанный стержень из ядерного реактора', 'A depleted rod from the nuclear reactor'), 'mb': ('мБ', 'mB'), 'eut': ('EU/т', 'EU/t'),
         'any': ('или похожее', 'or the like')}
HEIGHTS = [re.compile(r'(?:[Вв]ысота для поиска|[Hh]eight to search):?\s*(?:[^()\n]{0,90}\([^()\n]*\)[.,;]?\s*)+'),
           re.compile(r'(?:[Вв]ысота для поиска|[Hh]eight to search)[^.\n]*(?:\.|$)\s*')]


def hexid(kind, n):
    return f'{(kind << 56) | n:016X}'


def rebuild(root, out):
    os.chdir(root)
    argv = sys.argv
    sys.argv = ['quest_walk.py', '--chapter', '~none~']
    with contextlib.redirect_stdout(io.StringIO()):
        G = runpy.run_path(os.path.join(root, 'tools', 'quest_walk.py'))
    sys.argv = argv
    tier, best, recipes, walk = G['tier'], G['best'], G['recipes'], G['quests']
    R = os.path.join(root, 'src', 'generated', 'resources')
    cdir = os.path.join(out, 'chapters')
    chapters = {}
    for f in sorted(glob.glob(os.path.join(cdir, '*.json5'))):
        chapters[os.path.basename(f)[3:-6]] = json.load(open(f, encoding='utf-8'))
    lang = [json.load(open(os.path.join(out, 'lang', loc, 'iis1_quests.json5'), encoding='utf-8')) for loc in ('ru_ru', 'en_us')]
    unknown = set(chapters) - set(POOL) - set(FRONT) - set(TOPICAL)
    own = sorted(n for n in unknown if int(chapters[n]['id'][2:], 16) >= 0x1000)   # our chapters: weapons, what else
    if unknown - set(own):
        raise SystemExit(f'quests_book: chapters of the book with no place in the new one: {sorted(unknown - set(own))}')

    # ------------------------------------------------------------------ names
    names = [json.load(open(os.path.join(R, 'assets', 'iis', 'lang', loc + '.json'), encoding='utf-8')) for loc in ('ru_ru', 'en_us')]
    sys.path.insert(0, os.path.join(root, 'tools', 'gen'))
    import quests as _q
    _, vanilla_ru = _q.vanilla_items_and_names()
    with zipfile.ZipFile(os.path.join(root, 'build', 'moddev', 'artifacts', 'minecraft-patched-26.1.2.117-merged.jar')) as z:
        vanilla_en = json.loads(z.read('assets/minecraft/lang/en_us.json'))
    vanilla = [vanilla_ru, vanilla_en]

    def name(i, L):
        if i.startswith('F:'):
            f = i[2:]
            ns, p = f.split(':', 1)
            return names[L].get(f'fluid_type.{ns}.{p}') or vanilla[L].get(f'block.{ns}.{p}') or p
        if i.startswith('conduit:') or ':' not in i:
            return i
        ns, p = i.split(':', 1)
        table = names[L] if ns == 'iis' else vanilla[L]
        return table.get(f'item.{ns}.{p}') or table.get(f'block.{ns}.{p}') or p.replace('_', ' ')

    def ing_name(ing, L):
        if isinstance(ing, list):
            return ing_name(ing[0], L) if ing else '?'
        if isinstance(ing, dict):
            base = ing_name(ing.get('items') or ing.get('item') or ('#' + ing['tag'] if 'tag' in ing else '?'), L)
            fl = (ing.get('components') or {}).get('iis:fluid_content')
            return f'{base} ({name("F:" + fl["fluid"], L)})' if isinstance(fl, dict) else base
        if ing.startswith('#'):
            items = G['tag_items'](ing[1:])
            return name(items[0], L) if items else ing[1:].split('/')[-1].replace('_', ' ')
        return name(ing, L)

    # ------------------------------------------------------------------ where ore lies
    veins = json.load(open(os.path.join(root, 'src', 'main', 'resources', 'iis', 'veins.json'), encoding='utf-8'))

    def vein_lines(mat, L):
        out_ = []
        for v in veins:
            if f'iis:{mat}' in (v.get('primary'), v.get('secondary'), v.get('between'), v.get('sporadic')):
                dims = ', '.join(DIMS.get(d, (d, d))[L] for d in v['dimensions'])
                out_.append(f'{dims}, Y {v["minY"]}…{v["maxY"]} ({WORDS["vein"][L]}: {name("iis:ore_" + v["primary"].split(":")[1], L)})')
        return out_

    def ore_materials(node, depth=0, seen=None):
        """Ore blocks at the root of the cheapest chain to an item."""
        seen = set() if seen is None else seen
        if node in seen or depth > 5:
            return []
        seen.add(node)
        m = re.match(r'iis:(?:deepslate_|nether_|end_)?(?:small_)?ore_(.+)$', node)
        if m:
            return [m.group(1)]
        if node not in best:
            return []
        out_ = []
        for r in recipes[best[node]][1]:
            a = min(r, key=lambda x: tier.get(x, 99)) if r else None
            if a and not a.startswith('F:'):
                out_ += ore_materials(a, depth + 1, seen)
        return out_

    def how(node, L):
        """One line on how IIS 2 makes the thing, or None when the crafting picture or nothing says it."""
        if node not in best:
            return None
        rid = recipes[best[node]][0]
        if rid.startswith('loot/'):
            return f'{WORDS["drops"][L]}: {name("iis:" + rid[5:], L)}'
        if rid.startswith('crop/'):
            return WORDS['crop'][L]
        if rid == 'code/recycler':
            return WORDS['recycler'][L]
        if rid.startswith('space/rocket'):
            return WORDS['nasa'][L]
        if rid.startswith('reactor/'):
            return WORDS['reactor'][L]
        if rid.startswith('mc/'):
            d = G['vraw'].get(rid[3:], {})
        else:
            p = os.path.join(R, 'data', 'iis', 'recipe', rid + '.json')
            if not os.path.exists(p):
                return None
            d = json.load(open(p, encoding='utf-8'))
        t = d.get('type', '')
        if t in ('minecraft:smelting', 'minecraft:blasting'):
            return f'{WORDS["furnace"][L]}: {ing_name(d["ingredient"], L)}'
        if not t.startswith('iis:'):
            return None
        parts = [f'{x.get("count", 1)} × {ing_name(x["ingredient"], L)}' for x in d.get('inputs', [])]
        parts += [ing_name(x, L) for x in d.get('keep', [])]
        for x in d.get('fluid_inputs', []):
            ing = x['ingredient']
            parts.append(f'{x.get("amount", 1)} {WORDS["mb"][L]} {name("F:" + ing, L) if isinstance(ing, str) else "?"}')
        if d.get('circuit', -1) >= 0:
            parts.append(f'{WORDS["circuit"][L]} {d["circuit"]}')
        machine = names[L].get('jei.iis.' + t[4:]) or re.sub(r'\s*\(LV\)$', '', names[L].get(f'block.iis.{t[4:]}_lv', t[4:].replace('_', ' ')))
        sec = f'{d.get("duration", 0) / 20:g}'.replace('.', ',' if L == 0 else '.')
        tail = f'{d.get("eut", 0)} {WORDS["eut"][L]}, {sec} {WORDS["sec"][L]}' if d.get('eut') else f'{sec} {WORDS["sec"][L]}'
        return f'{machine}: {", ".join(parts)}. {tail}.'

    # ------------------------------------------------------------------ texts from IIS 2 data
    def retext(q):
        w = walk.get(q['id'])
        if w is None or int(q['id'][2:], 16) >= 0x100000:   # our own quests are written by hand
            return
        for L, table in enumerate(lang):
            key = f'quest.{q["id"]}.quest_desc'
            old = table.get(key, [])
            old = [old] if isinstance(old, str) else list(old)
            had_heights = any(h.search(line) for line in old for h in HEIGHTS)
            cleaned = []
            for line in old:
                for h in HEIGHTS:
                    line = h.sub('', line)
                cleaned.append(line.replace('NEI', 'JEI').strip() if '{image' not in line else line)
            while cleaned and not cleaned[0].strip():
                cleaned.pop(0)
            new = []
            if w.nodes and not w.bad:
                new.append(f'{WORDS["tier"][L]}: {TNAME[L][w.tier]}.')
                h = how(w.nodes[0], L) if len(w.nodes) == 1 else None
                if h:
                    new.append(f'{WORDS["make"][L]}: {h}')
                mats = list(dict.fromkeys(m for n in w.nodes[:3] for m in ore_materials(n)))
                if had_heights or (w.nodes[0] not in best and mats):
                    where = list(dict.fromkeys(v for m in mats[:4] for v in vein_lines(m, L)))[:6]
                    if where:
                        new.append(f'{WORDS["where"][L]}: ' + '; '.join(where) + '.')
            if new:
                table[key] = new + ([''] + cleaned if any(c.strip() for c in cleaned) else [])
            elif cleaned != old:
                table[key] = cleaned

    for ch in chapters.values():
        for q in ch['quests']:
            retext(q)

    # ------------------------------------------------------------------ tier chapters out of the progress chapters
    pool = {}
    for n in POOL:
        if n in chapters:
            for q in chapters[n]['quests']:
                pool[q['id']] = (n, q)
    memo = {}

    def home(qid, stack=()):
        if qid in memo:
            return memo[qid]
        w = walk[qid]
        t = 0 if w.bad else min(w.tier, 8)
        for d in pool[qid][1].get('dependencies', []):
            if d in pool and d not in stack:
                t = max(t, home(d, stack + (qid,)))
            elif d in walk and not walk[d].bad and d not in pool:   # a quest of the opening or of a topical chapter
                pass
        memo[qid] = t
        return t

    sys.setrecursionlimit(10000)
    tiers = {t: [] for t in range(9)}
    for qid in pool:
        tiers[home(qid)].append(qid)

    def tree_layout(qs):
        """A tech tree: a quest stands one column right of what it waits for, on the height of it; quests tied to nothing in the chapter
        go into a grid below. The old chapters' layouts made no sense once their quests were dealt out by tier."""
        ids_ = {q['id'] for q in qs}
        parents = {q['id']: [d for d in q.get('dependencies', []) if d in ids_] for q in qs}
        children = {q['id']: [] for q in qs}
        for c, ps in parents.items():
            for p_ in ps:
                children[p_].append(c)
        depth = {}

        def dep(i, stack=()):
            if i not in depth:
                depth[i] = 1 + max((dep(p_, stack + (i,)) for p_ in parents[i] if p_ not in stack), default=-1)
            return depth[i]

        for q in qs:
            dep(q['id'])
        for i in sorted(depth, key=lambda k: -depth[k]):   # a root moves right, next to what waits for it
            if children[i] and not parents[i]:
                depth[i] = max(depth[i], min(depth[c] for c in children[i]) - 1)
        loose = [q for q in qs if not parents[q['id']] and not children[q['id']]]
        tied = [q for q in qs if parents[q['id']] or children[q['id']]]
        layers = {}
        for q in tied:
            layers.setdefault(depth[q['id']], []).append(q['id'])
        y = {}
        for d in sorted(layers):
            for n, i in enumerate(sorted(layers[d])):
                y[i] = float(n)
        for sweep in range(6):
            for d in (sorted(layers) if sweep % 2 == 0 else sorted(layers, reverse=True)):
                near = parents if sweep % 2 == 0 else children
                want = {i: (sum(y[k] for k in near[i]) / len(near[i]) if near[i] else y[i]) for i in layers[d]}
                cur = None
                for i in sorted(layers[d], key=lambda k: (want[k], k)):
                    cur = want[i] if cur is None else max(cur + 1.0, want[i])
                    y[i] = cur
        top = min(y.values(), default=0.0)
        by_id = {q['id']: q for q in qs}
        out_ = [(by_id[i], (2.0 + depth[i] * 2.0, round((y[i] - top) * 2) / 2 + 2.0)) for i in y]
        base = max((p_[1] for _, p_ in out_), default=0.0) + 3.0
        for n, q in enumerate(sorted(loose, key=lambda q: q['id'])):
            out_.append((q, (2.0 + (n % 16) * 1.5, base + (n // 16) * 1.5)))
        return out_

    new_chapters = []
    for t in range(9):
        key = TIER_KEYS[t]
        ru, en, icon, tru, ten = HEAD[key]
        hid = hexid(0x1A, 0x130000 + t)
        quests_ = [{'id': hid, 'x': 0.0, 'y': 0.0, 'size': 1.5, 'icon': {'id': icon, 'count': 1}, 'tasks': [{'type': 'checkmark', 'id': hexid(0x1B, (0x130000 + t) << 8)}]}]
        for L, (title, text) in enumerate(((ru, tru), (en, ten))):
            lang[L][f'quest.{hid}.title'] = title
            lang[L][f'quest.{hid}.quest_desc'] = text.split('\n')
        for q, (x, y) in tree_layout([pool[i][1] for i in tiers[t]]):
            q = dict(q)
            q['x'], q['y'] = x, y
            q.pop('hide_until_deps_visible', None)
            quests_.append(q)
        cid = hexid(0x1D, 0x1020 + t)
        for L, title in enumerate((ru, en)):
            lang[L][f'chapter.{cid}.title'] = title
        new_chapters.append((key, {'id': cid, 'default_quest_shape': '', 'icon': {'id': icon, 'count': 1}, 'quests': quests_}))

    # ------------------------------------------------------------------ write the book in its new order
    for f in glob.glob(os.path.join(cdir, '*.json5')):
        os.remove(f)
    book = [(n, chapters[n]) for n in FRONT] + new_chapters + [(n, chapters[n]) for n in TOPICAL if n in chapters] + [(n, chapters[n]) for n in own]
    ids = {q['id'] for _, ch in book for q in ch['quests']}
    for idx, (n, ch) in enumerate(book):
        ch['filename'] = f'{idx:02d}_{n}'
        ch['order_index'] = idx
        if 'quest_links' in ch:
            ch['quest_links'] = [l for l in ch['quest_links'] if l['linked_quest'] in ids]
            if not ch['quest_links']:
                del ch['quest_links']
        with open(os.path.join(cdir, ch['filename'] + '.json5'), 'w', encoding='utf-8', newline='\n') as fh:
            json.dump(ch, fh, ensure_ascii=False, indent=2)
            fh.write('\n')
    for loc, table in zip(('ru_ru', 'en_us'), lang):
        with open(os.path.join(out, 'lang', loc, 'iis1_quests.json5'), 'w', encoding='utf-8', newline='\n') as fh:
            json.dump(dict(sorted(table.items())), fh, ensure_ascii=False, indent=2)
            fh.write('\n')
    return {TIER_KEYS[t]: len(v) for t, v in tiers.items()}
