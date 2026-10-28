"""IIS 1 quest book (BetterQuesting, config/betterquesting/DefaultQuests.json) -> FTB Quests 26.1.2 (JSON5, file version 13).

D101 (owner: "квесты как в ИИС 1", the whole book; quests with items not yet ported stay hidden).
Run after gen_content.py (it reads the generated lang and item models):

    IIS1_INSTANCE=<IIS 1 game dir> IIS_GT5_SRC=<GT5-II src/main/java/gregtech> python -I tools/gen/quests.py

Output: quests/ftbquests/ (copy into <game>/config/ftbquests/quests/) and quests/REPORT.md.

Item mapping, in order:
  * vanilla 1.7.10 ids: renamed ones by table, the rest as is if the item still exists;
  * IC2 items that IIS 2 replaced (cells -> capsules, resin, treetap, circuits ...) by table;
  * GT meta-tools -> our tool of the same kind, iron head (BetterQuesting ignored the tool NBT);
  * GT battery buffers by MetaTileEntity key (GT gives all sizes one name);
  * everything else from GT by its Russian name in GregTech.lang.ru (the generator names our content from the same keys),
    first in our lang, then in the vanilla one; OreDict name as the last resort.
A quest whose task needs an unmapped item or fluid is not written; quests that depended on it depend on its own
dependencies instead. Unmapped reward items are dropped (mostly GT coins: the IIS 1 shop is not ported).
"""
import collections
import json
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
INST = os.environ.get('IIS1_INSTANCE', 'C:/Users/Godkiller/AppData/Roaming/forServerClient')
GTSRC = os.environ.get('IIS_GT5_SRC', 'C:/Users/Godkiller/AppData/Local/Temp/gt5src/src/main/java/gregtech')
NS = 'iis'
OUT = os.path.join(ROOT, 'quests', 'ftbquests')
GEN = os.path.join(ROOT, 'src', 'generated', 'resources', 'assets', NS)


def read_lang(path):
    out = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        line = line.rstrip('\n').rstrip('\r')
        if '=' in line and not line.startswith('#'):
            k, _, v = line.partition('=')
            out[k.strip()] = v
    return out


def gt_lang():
    out = {}
    for line in open(os.path.join(INST, 'GregTech.lang.ru'), encoding='utf-8', errors='replace'):
        line = line.strip()
        if line.startswith('S:') and '=' in line:
            k, _, v = line[2:].partition('=')
            out[k] = v
    return out


def mte_names():
    """GT_Loader_MetaTileEntities: MetaTileEntity id -> unlocalized name (gt.blockmachines.<name>.name)."""
    src = open(os.path.join(GTSRC, 'loaders', 'preload', 'GT_Loader_MetaTileEntities.java'), encoding='utf-8', errors='replace').read()
    return {int(m.group(1)): m.group(2) for m in re.finditer(r'new GT_MetaTileEntity_\w+\(\s*(\d+)\s*,\s*"([^"]+)"', src)}


def vanilla_items_and_names():
    jar = os.path.join(ROOT, 'build', 'moddev', 'artifacts', 'minecraft-patched-26.1.2.117-merged.jar')
    with zipfile.ZipFile(jar) as z:
        items = {n[len('assets/minecraft/items/'):-5] for n in z.namelist() if n.startswith('assets/minecraft/items/') and n.endswith('.json')}
    ru = {}
    for idx in ('34.json', '30.json'):
        p = os.path.join(INST, 'assets', 'indexes', idx)
        if not os.path.exists(p):
            continue
        h = json.load(open(p, encoding='utf-8'))['objects'].get('minecraft/lang/ru_ru.json')
        if h:
            ru = json.load(open(os.path.join(INST, 'assets', 'objects', h['hash'][:2], h['hash']), encoding='utf-8'))
            break
    return items, ru


# 1.7.10 -> 26.1 (id, damage); damage None = any
MC_RENAMES = {
    ('planks', 0): 'oak_planks', ('log', 0): 'oak_log', ('wool', 0): 'white_wool', ('wool', 15): 'black_wool',
    ('carpet', 15): 'black_carpet', ('dye', 0): 'ink_sac', ('dye', 1): 'red_dye', ('coal', 1): 'charcoal',
    ('brick_block', 0): 'bricks', ('reeds', 0): 'sugar_cane', ('sign', 0): 'oak_sign', ('skull', 1): 'wither_skeleton_skull',
    ('skull', 3): 'player_head', ('stone_slab', 0): 'smooth_stone_slab', ('wooden_slab', 0): 'oak_slab',
    ('stonebrick', 0): 'stone_bricks', ('wooden_door', 0): 'oak_door', ('bed', 0): 'red_bed', ('golden_rail', 0): 'powered_rail',
    ('wooden_pressure_plate', 0): 'oak_pressure_plate', ('fence', 0): 'oak_fence', ('trapdoor', 0): 'oak_trapdoor',
    ('stained_hardened_clay', 0): 'white_terracotta', ('hardened_clay', 0): 'terracotta', ('melon_block', 0): 'melon',
    ('lit_pumpkin', 0): 'jack_o_lantern', ('noteblock', 0): 'note_block', ('web', 0): 'cobweb', ('sapling', 0): 'oak_sapling',
}
# IC2 items that IIS 2 has an own counterpart for (D74: cells are capsules; D92: IC2 circuits are GT circuits)
IC2_MAP = {
    ('itemCellEmpty', 0): f'{NS}:capsule', ('itemFluidCell', 0): f'{NS}:universal_capsule', ('itemHarz', 0): f'{NS}:sticky_resin',
    ('itemTreetap', 0): f'{NS}:treetap', ('itemBiochaff', 0): f'{NS}:bio_chaff', ('itemPartCircuit', 0): f'{NS}:circuit_basic',
    ('itemPartCircuitAdv', 0): f'{NS}:circuit_advanced', ('blockRubWood', 0): f'{NS}:rubber_log', ('itemToolMEter', 0): f'{NS}:multimeter',
    ('itemRubber', 0): f'{NS}:plate_rubber',
}
GT_TOOLS = {0: 'sword', 2: 'pickaxe', 4: 'shovel', 6: 'axe', 8: 'hoe', 10: 'saw', 12: 'hammer', 14: 'soft_hammer', 16: 'wrench',
            18: 'file', 20: 'crowbar', 22: 'screwdriver', 24: 'mortar', 26: 'wire_cutter', 34: 'knife'}
TIERS = {'01': 'lv', '02': 'mv', '03': 'hv', '04': 'ev'}
# OreDict prefixes (GT OrePrefixes names) -> our item id prefix
OREDICT_PREFIXES = ['ingot', 'dust', 'plate', 'gem', 'nugget', 'block', 'gear', 'stick', 'ring', 'bolt', 'screw', 'wire', 'cable', 'ore']
OREDICT_OUR = {'stick': 'rod'}


class Mapper:
    def __init__(self):
        self.gtl = gt_lang()
        self.mte = mte_names()
        self.ours = {f[:-5] for f in os.listdir(os.path.join(GEN, 'items')) if f.endswith('.json')}
        self.vanilla, vru = vanilla_items_and_names()
        lang = json.load(open(os.path.join(GEN, 'lang', 'ru_ru.json'), encoding='utf-8'))
        self.by_name = collections.defaultdict(list)
        for k, v in lang.items():
            parts = k.split('.')
            if len(parts) == 3 and parts[0] in ('item', 'block') and parts[1] == NS and parts[2] in self.ours:
                self.by_name[v].append(f'{NS}:{parts[2]}')
        for k, v in vru.items():
            parts = k.split('.')
            if len(parts) == 3 and parts[0] in ('item', 'block') and parts[1] == 'minecraft' and parts[2] in self.vanilla:
                self.by_name.setdefault(v, []).append(f'minecraft:{parts[2]}')
        self.tools = collections.defaultdict(list)
        for t in json.load(open(os.path.join(ROOT, 'src', 'main', 'resources', NS, 'tools.json'), encoding='utf-8')):
            self.tools[t['tool']].append(t['id'])
        self.fluids = {k.split('.', 2)[2] for k in lang if k.startswith(f'fluid_type.{NS}.')}

    @staticmethod
    def pick(cands):
        # our own item first, then vanilla; among equals the shortest id (stone ore before deepslate_ore, ...)
        return sorted(set(cands), key=lambda c: (not c.startswith(NS + ':'), len(c), c))[0]

    def item(self, iid, dmg, oredict=''):
        mod, _, name = iid.partition(':')
        r = None
        if mod == 'minecraft':
            r = MC_RENAMES.get((name, dmg))
            if r is None and dmg in (0, 32767) and name in self.vanilla:
                r = name
            r = f'minecraft:{r}' if r else None
        elif mod == 'IC2':
            r = IC2_MAP.get((name, dmg))
        elif mod == 'gregtech':
            r = self.gregtech(name, dmg)
        if r is None and oredict:
            r = self.oredict(oredict)
        if r is None:
            return None
        ns, _, path = r.partition(':')
        ok = path in self.ours if ns == NS else path in self.vanilla
        return r if ok else None

    def gregtech(self, name, dmg):
        if name == 'gt.metatool.01':
            kind = GT_TOOLS.get(dmg)
            ids = self.tools.get(kind, [])
            if not ids:
                return None
            pref = [i for i in ids if i.endswith('_iron')] or [i for i in ids if i.endswith('_steel')] or ids
            return f'{NS}:{pref[0]}'
        if name == 'gt.blockmachines':
            key = self.mte.get(dmg)
            if key is None:
                return None
            m = re.fullmatch(r'batterybuffer\.(\d\d)\.tier\.(\d\d)', key)
            if m and TIERS.get(m.group(2)):
                k = {1: 1, 4: 2, 9: 3, 16: 4}.get(int(m.group(1)))
                return f'{NS}:battery_buffer_{k}x{k}_{TIERS[m.group(2)]}' if k else None
            label = self.gtl.get(f'gt.blockmachines.{key}.name')
        else:
            label = self.gtl.get(f'{name}.{dmg}.name')
        if not label or label not in self.by_name:
            return None
        return self.pick(self.by_name[label])

    def oredict(self, od):
        for p in OREDICT_PREFIXES:
            if od.startswith(p) and len(od) > len(p) and od[len(p)].isupper():
                mat = re.sub(r'(?<!^)(?=[A-Z])', '_', od[len(p):]).lower()
                cand = f'{OREDICT_OUR.get(p, p)}_{mat}'
                if cand in self.ours:
                    return f'{NS}:{cand}'
                for v in (f'{mat}_{p}', f'{mat}_{OREDICT_OUR.get(p, p)}'):
                    if v in self.vanilla:
                        return f'minecraft:{v}'
        return None

    def fluid(self, name):
        if name in ('water', 'lava'):
            return f'minecraft:{name}'
        for c in (name, name.replace('liquid_', ''), name.replace('_gt5u', ''), name.replace('.', '_'), name.replace('ic2', '')):
            if c in self.fluids:
                return f'{NS}:{c}'
        return None


def hexid(kind, n):
    # FTB Quests object ids: 64-bit, written as %016X; the high byte keeps kinds apart
    return f'{(kind << 56) | n:016X}'


def bq_items(t):
    return [it for _, it in sorted(t.get('requiredItems:9', {}).items(), key=lambda e: int(e[0].split(':')[0]))]


def main():
    mp = Mapper()
    bq = json.load(open(os.path.join(INST, 'config', 'betterquesting', 'DefaultQuests.json'), encoding='utf-8'))
    lang_dir = os.path.join(INST, 'config', 'betterquesting', 'resources', 'iia', 'lang')
    langs = {'ru_ru': read_lang(os.path.join(lang_dir, 'ru_RU.lang')), 'en_us': read_lang(os.path.join(lang_dir, 'en_US.lang'))}
    quests = {q['questID:3']: q for q in bq['questDatabase:9'].values()}

    missing_items, missing_fluids, dropped_rewards = collections.Counter(), collections.Counter(), collections.Counter()
    built, hidden = {}, {}
    for qid, q in sorted(quests.items()):
        props = q['properties:10']['betterquesting:10']
        tasks, reason = [], None
        for ti, (_, t) in enumerate(sorted(q.get('tasks:9', {}).items(), key=lambda e: int(e[0].split(':')[0]))):
            tid = t['taskID:8']
            if tid == 'bq_standard:retrieval':
                for it in bq_items(t):
                    m = mp.item(it['id:8'], it.get('Damage:2', 0), it.get('OreDict:8', ''))
                    if m is None:
                        missing_items[(it['id:8'], it.get('Damage:2', 0))] += 1
                        reason = reason or f"{it['id:8']}:{it.get('Damage:2', 0)}"
                        continue
                    task = {'type': 'item', 'item': {'id': m, 'count': 1}, 'count': max(1, it.get('Count:3', 1))}
                    if t.get('consume:1', 0):
                        task['consume_items'] = True
                    tasks.append(task)
            elif tid == 'bq_standard:fluid':
                for fl in t.get('requiredFluids:9', {}).values():
                    m = mp.fluid(fl.get('FluidName:8', ''))
                    if m is None:
                        missing_fluids[fl.get('FluidName:8')] += 1
                        reason = reason or f"fluid {fl.get('FluidName:8')}"
                        continue
                    # ftblibrary FluidStack.MAP_CODEC: {fluid, amount}
                    tasks.append({'type': 'fluid', 'fluid': {'fluid': m, 'amount': max(1, fl.get('Amount:3', 1000))}})
            elif tid == 'bq_standard:checkbox':
                tasks.append({'type': 'checkmark'})
            elif tid == 'bq_standard:hunt':
                ent = {'Creeper': 'minecraft:creeper', 'Zombie': 'minecraft:zombie', 'Skeleton': 'minecraft:skeleton',
                       'Spider': 'minecraft:spider', 'Enderman': 'minecraft:enderman'}.get(t.get('target:8'))
                if ent is None:
                    reason = reason or f"hunt {t.get('target:8')}"
                    continue
                tasks.append({'type': 'kill', 'entity': ent, 'value': t.get('required:3', 1)})
            else:
                reason = reason or tid
        if reason or not tasks:
            hidden[qid] = reason or 'no tasks'
            continue
        rewards = []
        for _, r in sorted(q.get('rewards:9', {}).items(), key=lambda e: int(e[0].split(':')[0])):
            if r['rewardID:8'] != 'bq_standard:item':
                dropped_rewards[r['rewardID:8']] += 1
                continue
            for it in [v for _, v in sorted(r.get('rewards:9', {}).items(), key=lambda e: int(e[0].split(':')[0]))]:
                m = mp.item(it['id:8'], it.get('Damage:2', 0), it.get('OreDict:8', ''))
                if m is None:
                    dropped_rewards[(it['id:8'], it.get('Damage:2', 0))] += 1
                    continue
                rewards.append({'type': 'item', 'item': {'id': m, 'count': max(1, it.get('Count:3', 1))}})
        icon = props.get('icon:10', {})
        icon_id = mp.item(icon.get('id:8', ''), icon.get('Damage:2', 0), icon.get('OreDict:8', '')) if icon else None
        built[qid] = {'props': props, 'tasks': tasks, 'rewards': rewards, 'icon': icon_id,
                      'deps': list(q.get('preRequisites:11', [])), 'or': props.get('questLogic:8') == 'OR'}

    # dependencies through hidden quests: a hidden quest is replaced by its own (visible) dependencies
    memo = {}

    def visible_deps(qid, seen=()):
        if qid in memo:
            return memo[qid]
        out = []
        for d in quests.get(qid, {}).get('preRequisites:11', []):
            if d in seen:
                continue
            if d in built:
                out.append(d)
            elif d in quests:
                out += visible_deps(d, seen + (qid,))
        memo[qid] = list(dict.fromkeys(out))
        return memo[qid]

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(os.path.join(OUT, 'chapters'))
    tr = {k: {} for k in langs}

    def text(key_base, bq_key, kind):
        for loc, table in langs.items():
            v = table.get(bq_key)
            if v is None:
                continue
            if kind == 'title':
                tr[loc][f'{key_base}.title'] = v
            else:
                lines = v.replace('\\n', '\n').split('\n')
                tr[loc][f'{key_base}.quest_desc'] = lines

    placed = set()
    chapters = 0
    lines = sorted(bq['questLines:9'].values(), key=lambda l: l.get('lineID:3', 0))
    order = [int(k.split(':')[0]) for k in bq['questLines:9']]
    lines = [bq['questLines:9'][k] for k in sorted(bq['questLines:9'], key=lambda k: int(k.split(':')[0]))]
    for idx, line in enumerate(lines):
        lid = line.get('lineID:3', idx)
        lp = line['properties:10']['betterquesting:10']
        entries = [e for _, e in sorted(line.get('quests:9', {}).items(), key=lambda e: int(e[0].split(':')[0]))]
        cq, links = [], []
        for e in entries:
            qid = e['id:3']
            if qid not in built:
                continue
            b = built[qid]
            sx = e.get('sizeX:3', 24) / 24.0
            x = (e['x:3'] + e.get('sizeX:3', 24) / 2) / 24.0
            y = (e['y:3'] + e.get('sizeY:3', 24) / 2) / 24.0
            if qid in placed:
                links.append({'id': hexid(0x1E, (lid << 20) | qid), 'linked_quest': hexid(0x1A, qid), 'x': x, 'y': y, 'size': sx})
                continue
            placed.add(qid)
            qo = {'id': hexid(0x1A, qid), 'x': x, 'y': y}
            if abs(sx - 1.0) > 1e-6:
                qo['size'] = sx
            deps = visible_deps(qid)
            if deps:
                qo['dependencies'] = [hexid(0x1A, d) for d in deps]
                if b['or'] and len(deps) > 1:
                    qo['dependency_requirement'] = 'one_completed'
            if b['props'].get('visibility:8') in ('NORMAL', 'CHAIN'):
                qo['hide_until_deps_visible'] = True
            if b['icon']:
                qo['icon'] = {'id': b['icon'], 'count': 1}
            qo['tasks'] = [dict(t, id=hexid(0x1B, (qid << 8) | i)) for i, t in enumerate(b['tasks'])]
            if b['rewards']:
                qo['rewards'] = [dict(r, id=hexid(0x1C, (qid << 8) | i)) for i, r in enumerate(b['rewards'])]
            text(f'quest.{qo["id"]}', b['props'].get('name:8', ''), 'title')
            text(f'quest.{qo["id"]}', b['props'].get('desc:8', ''), 'desc')
            cq.append(qo)
        if not cq and not links:
            continue
        cid = hexid(0x1D, lid)
        fname = f'{idx:02d}_' + re.sub(r'[^a-z0-9_]+', '_', lp.get('name:8', f'line_{lid}').replace('bq.questline.', '').replace('.name', '').lower()).strip('_')
        ch = {'id': cid, 'filename': fname, 'order_index': idx, 'default_quest_shape': '', 'quests': cq}
        if links:
            ch['quest_links'] = links
        icon = lp.get('icon:10', {})
        icon_id = mp.item(icon.get('id:8', ''), icon.get('Damage:2', 0), icon.get('OreDict:8', '')) if icon else None
        if icon_id:
            ch['icon'] = {'id': icon_id, 'count': 1}
        text(f'chapter.{cid}', lp.get('name:8', ''), 'title')
        write(os.path.join(OUT, 'chapters', fname + '.json5'), ch)
        chapters += 1
    # BaseQuestFile#readData: these keys are required (orElseThrow) in file version 13
    write(os.path.join(OUT, 'data.json5'), {'version': 13, 'default_reward_team': False, 'default_consume_items': False,
                                            'default_autoclaim_rewards': 'disabled', 'default_quest_shape': 'circle',
                                            'default_quest_disable_jei': False, 'drop_loot_crates': False,
                                            'fallback_locale': 'en_us', 'grid_scale': 0.5})
    # TranslationManager#loadFromFile: a language is a folder lang/<locale>/ (every .json5 inside is read)
    for loc, table in tr.items():
        os.makedirs(os.path.join(OUT, 'lang', loc), exist_ok=True)
        write(os.path.join(OUT, 'lang', loc, 'iis1_quests.json5'), dict(sorted(table.items())))

    rep = [f'# IIS 1 quests -> FTB Quests (D101)', '',
           f'Quests in IIS 1: {len(quests)}; written: {len(placed)}; hidden until their items are ported: {len(hidden)}; chapters: {chapters}.', '',
           '## Most needed unported items (task count)', '']
    for (i, d), c in missing_items.most_common(80):
        label = mp.gtl.get(f'gt.blockmachines.{mp.mte.get(d)}.name') if i == 'gregtech:gt.blockmachines' else mp.gtl.get(f'{i.split(":", 1)[1]}.{d}.name', '')
        rep.append(f'- {c} × `{i}:{d}` {label or ""}')
    rep += ['', '## Unported fluids', ''] + [f'- {c} × `{f}`' for f, c in missing_fluids.most_common()]
    rep += ['', '## Dropped reward items', ''] + [f'- {c} × `{k}`' for k, c in dropped_rewards.most_common(30)]
    with open(os.path.join(ROOT, 'quests', 'REPORT.md'), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('\n'.join(rep) + '\n')
    print(f'quests: {len(quests)} total, {len(placed)} written, {len(hidden)} hidden, {chapters} chapters')


def write(path, obj):
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=2)
        fh.write('\n')


if __name__ == '__main__':
    main()
