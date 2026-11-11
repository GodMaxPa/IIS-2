"""Crafting grid pictures for the quest book (D144, owner: "show the recipe right in the quest").

For the item of a quest's first task, the crafting table recipe that makes it is drawn as a picture (3x3 grid, arrow, result) from the
item textures (ours and vanilla, constant tints applied; blocks by a face texture) and put into the quest text as an FTB Library
image component. Machine recipes stay in JEI (FTB XMod Compat opens them from the task).
"""
import glob
import json
import os
import zipfile

from PIL import Image, ImageDraw

NS = 'iis'
CELL = 18
FACES = ('layer0', 'all', 'side', 'front', 'north', 'texture', 'end', 'top', 'cross', 'plant', 'particle')


class Recipes:
    def __init__(self, root):
        self.root = root
        self.gen = os.path.join(root, 'src', 'generated', 'resources')
        self.jar = zipfile.ZipFile(os.path.join(root, 'build', 'moddev', 'artifacts', 'minecraft-patched-26.1.2.117-merged.jar'))
        self.names = set(self.jar.namelist())
        self.by_result = {}
        ovr = os.path.join(self.gen, 'packs', 'iis_overrides', 'data', 'minecraft', 'recipe')
        disabled = set()
        for f in glob.glob(os.path.join(ovr, '*.json')):
            r = json.load(open(f, encoding='utf-8'))
            if 'neoforge:conditions' in r:
                disabled.add(os.path.basename(f)[:-5])
            else:
                self._add(r)
        for f in glob.glob(os.path.join(self.gen, 'data', NS, 'recipe', '**', '*.json'), recursive=True):
            self._add(json.load(open(f, encoding='utf-8')))
        for n in self.names:
            if n.startswith('data/minecraft/recipe/') and n.endswith('.json'):
                rid = n.split('/')[-1][:-5]
                if rid in disabled or os.path.exists(os.path.join(ovr, rid + '.json')):
                    continue
                try:
                    self._add(json.loads(self.jar.read(n)))
                except ValueError:
                    pass
        self.icons = {}

    def _add(self, r):
        if r.get('type') not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
            return
        res = r.get('result', {})
        rid = res.get('id') if isinstance(res, dict) else res
        if rid:
            self.by_result.setdefault(rid, []).append(r)

    # --- data lookup ------------------------------------------------------------------------------------------------------------
    def _json(self, ns, path):
        p = os.path.join(self.gen, *path.split('/')).replace(os.sep + 'NS' + os.sep, os.sep)
        ours = os.path.join(self.gen, path.replace('{ns}', ns))
        if os.path.exists(ours):
            return json.load(open(ours, encoding='utf-8'))
        jp = path.replace('{ns}', ns)
        if jp in self.names:
            return json.loads(self.jar.read(jp))
        return None

    def tag_first(self, tag, depth=0):
        ns, path = tag.split(':', 1)
        for base in (os.path.join(self.gen, 'data', ns, 'tags', 'item', path + '.json'),):
            if os.path.exists(base):
                for v in json.load(open(base, encoding='utf-8')).get('values', []):
                    v = v['id'] if isinstance(v, dict) else v
                    r = self.tag_first(v[1:], depth + 1) if v.startswith('#') and depth < 8 else v
                    if r and not r.startswith('#'):
                        return r
        jp = f'data/{ns}/tags/item/{path}.json'
        if jp in self.names:
            for v in json.loads(self.jar.read(jp)).get('values', []):
                v = v['id'] if isinstance(v, dict) else v
                r = self.tag_first(v[1:], depth + 1) if v.startswith('#') and depth < 8 else v
                if r:
                    return r
        return {'c:cobblestones': 'minecraft:cobblestone', 'c:chests/wooden': 'minecraft:chest', 'c:glass_blocks': 'minecraft:glass',
                'c:gems/quartz': 'minecraft:quartz', 'c:dyes/blue': 'minecraft:blue_dye', 'c:stones': 'minecraft:stone', 'c:strings': 'minecraft:string',
                'c:rods/wooden': 'minecraft:stick', 'c:ingots/iron': 'minecraft:iron_ingot', 'c:dusts/redstone': 'minecraft:redstone'}.get(tag)

    def ingredient(self, ing):
        if isinstance(ing, list):
            ing = ing[0] if ing else None
        if isinstance(ing, dict):
            ing = ing.get('item') or ('#' + ing['tag'] if 'tag' in ing else None)
        if not ing:
            return None
        return self.tag_first(ing[1:]) if ing.startswith('#') else ing

    # --- icons ------------------------------------------------------------------------------------------------------------------
    def _png(self, ref):
        ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
        rel = f'assets/{ns}/textures/{path}.png'
        p = os.path.join(self.gen, *rel.split('/'))
        try:
            im = Image.open(p) if os.path.exists(p) else Image.open(self.jar.open(rel)) if rel in self.names else None
        except OSError:
            im = None
        if im is None:
            return None
        im = im.convert('RGBA')
        return im.crop((0, 0, im.width, im.width)).resize((16, 16), Image.NEAREST) if im.height != 16 or im.width != 16 else im

    def _model(self, ref):
        ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
        textures, depth = {}, 0
        while ref and depth < 10:
            ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
            m = self._json(ns, f'assets/{{ns}}/models/{path}.json')
            if m is None:
                break
            for k, v in m.get('textures', {}).items():
                textures.setdefault(k, v)
            ref = m.get('parent')
            depth += 1
        for _ in range(4):   # "#side" references
            for k, v in list(textures.items()):
                if isinstance(v, str) and v.startswith('#'):
                    textures[k] = textures.get(v[1:], v)
        return textures

    def icon(self, iid):
        if iid in self.icons:
            return self.icons[iid]
        ns, path = iid.split(':', 1)
        im = None
        desc = self._json(ns, f'assets/{{ns}}/items/{path}.json')
        model, tints = None, []
        if desc:
            mm = desc.get('model', {})
            for _ in range(4):   # select / condition models: take the first plain one
                if mm.get('type') == 'minecraft:model':
                    break
                mm = (mm.get('cases') or [{}])[0].get('model') or mm.get('on_false') or mm.get('fallback') or mm.get('model') or {}
                if not isinstance(mm, dict):
                    mm = {}
            model, tints = mm.get('model'), mm.get('tints', [])
        tex = self._model(model or f'{ns}:item/{path}')
        if 'layer0' in tex:
            im = Image.new('RGBA', (16, 16))
            for k in range(4):
                t = tex.get(f'layer{k}')
                if not t:
                    continue
                layer = self._png(t)
                if layer is None:
                    continue
                tint = tints[k] if k < len(tints) else None
                if tint and tint.get('type') == 'minecraft:constant':
                    c = tint['value']
                    c = (c >> 16 & 255, c >> 8 & 255, c & 255) if isinstance(c, int) else tuple(int(x * 255) for x in c[:3])
                    px = layer.load()
                    for y in range(16):
                        for x in range(16):
                            r, g, b, a = px[x, y]
                            px[x, y] = (r * c[0] // 255, g * c[1] // 255, b * c[2] // 255, a)
                im = Image.alpha_composite(im, layer)
        else:
            for f in FACES:
                if f in tex:
                    im = self._png(tex[f])
                    if im is not None:
                        break
        self.icons[iid] = im
        return im

    # --- picture -----------------------------------------------------------------------------------------------------------------
    def grid(self, rec):
        """3x3 rows of item ids (or None) for a crafting recipe."""
        if rec['type'] == 'minecraft:crafting_shaped':
            rows = [[self.ingredient(rec['key'].get(ch)) if ch != ' ' else None for ch in row] for row in rec['pattern']]
        else:
            ings = [self.ingredient(i) for i in rec.get('ingredients', [])]
            rows = [ings[i:i + 3] for i in range(0, len(ings), 3)]
        return rows

    def picture(self, iid):
        recs = self.by_result.get(iid)
        if not recs:
            return None
        rec = sorted(recs, key=lambda r: r['type'] != 'minecraft:crafting_shaped')[0]
        rows = self.grid(rec)
        if any(c is None for r in rows for c in r if c is not None) or not rows:
            return None
        w, h = CELL * 3 + 22 + CELL + 4, CELL * 3 + 2
        im = Image.new('RGBA', (w, h), (198, 198, 198, 255))
        d = ImageDraw.Draw(im)

        def slot(x, y, item):
            d.rectangle([x, y, x + CELL - 1, y + CELL - 1], fill=(139, 139, 139, 255))
            d.line([x, y, x + CELL - 2, y], fill=(55, 55, 55, 255))
            d.line([x, y, x, y + CELL - 2], fill=(55, 55, 55, 255))
            d.line([x + 1, y + CELL - 1, x + CELL - 1, y + CELL - 1], fill=(255, 255, 255, 255))
            d.line([x + CELL - 1, y + 1, x + CELL - 1, y + CELL - 1], fill=(255, 255, 255, 255))
            ic = self.icon(item) if item else None
            if ic is not None:
                im.alpha_composite(ic, (x + 1, y + 1))
            return ic is not None or item is None
        ok = True
        for r in range(3):
            for c in range(3):
                item = rows[r][c] if r < len(rows) and c < len(rows[r]) else None
                ok &= slot(1 + c * CELL, 1 + r * CELL, item)
        ax = CELL * 3 + 5
        d.polygon([(ax, h // 2 - 3), (ax + 10, h // 2 - 3), (ax + 10, h // 2 - 6), (ax + 15, h // 2), (ax + 10, h // 2 + 6), (ax + 10, h // 2 + 3), (ax, h // 2 + 3)],
                  fill=(139, 139, 139, 255))
        ok &= slot(w - CELL - 2, h // 2 - CELL // 2, iid)
        return im if ok else None


def add_pictures(root, chapters_dir, tr):
    """Draw the recipes of the quests' first task items; returns how many quests got one."""
    rp = Recipes(root)
    out_dir = os.path.join(rp.gen, 'assets', NS, 'textures', 'quest_recipe')
    os.makedirs(out_dir, exist_ok=True)
    for f in glob.glob(os.path.join(out_dir, '*.png')):
        os.remove(f)
    done, n = {}, 0
    for f in sorted(glob.glob(os.path.join(chapters_dir, '*.json5'))):
        ch = json.load(open(f, encoding='utf-8'))
        for q in ch.get('quests', []):
            task = next((t for t in q.get('tasks', []) if t.get('type') == 'item'), None)
            iid = task and task['item'].get('id')
            if iid == 'ftbfiltersystem:smart_filter':   # any of several items: draw the one on the quest's icon
                iid = q.get('icon', {}).get('id')
            if not iid or iid == 'ftbfiltersystem:smart_filter':
                continue
            if iid not in done:
                pic = rp.picture(iid)
                name = iid.replace(':', '__')
                if pic is not None:
                    pic.save(os.path.join(out_dir, name + '.png'))
                done[iid] = name if pic is not None else None
            if not done[iid]:
                continue
            for loc, word in (('ru_ru', 'Рецепт на верстаке:'), ('en_us', 'Crafting table recipe:')):
                key = f'quest.{q["id"]}.quest_desc'
                lines = list(tr[loc].get(key, []))
                lines += ['', word, f'{{image:{NS}:textures/quest_recipe/{done[iid]}.png width:120 height:72 align:center}}']
                tr[loc][key] = lines
            n += 1
    return n
