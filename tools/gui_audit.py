"""Checks the machine windows from the report of `gradlew runClient -Pguishots` (run/gui_report.txt).

For every machine window it compares the slots of the menu with the slots painted on its texture:
- a menu slot over a place where the texture has no slot: the player sees an item floating on a plain panel;
- a painted slot without a menu slot: the player sees a slot that takes nothing.
Run: python tools/gui_audit.py
"""
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..')
TEX = os.path.join(ROOT, 'src', 'generated', 'resources', 'assets')


def painted(px, x, y):
    """A GT or vanilla slot: dark line along the top and left of its 18x18 frame, light along the bottom and right."""
    def dark(c):
        return c[3] > 0 and c[0] < 100 and c[1] < 100 and c[2] < 100

    def light(c):
        return c[3] > 0 and c[0] > 200 and c[1] > 200 and c[2] > 200
    try:
        top = sum(dark(px[x + i, y]) for i in range(1, 16))
        left = sum(dark(px[x, y + i]) for i in range(1, 16))
        bottom = sum(light(px[x + i, y + 17]) for i in range(2, 17))
        right = sum(light(px[x + 17, y + i]) for i in range(2, 17))
    except IndexError:
        return False
    return top >= 13 and left >= 13 and bottom >= 13 and right >= 13


def main():
    report = os.path.join(ROOT, 'run', 'gui_report.txt')
    seen, problems, other = {}, [], []
    for line in open(report, encoding='utf-8'):
        line = line.rstrip('\n')
        if line.startswith(('NOSCREEN', 'CRASH', 'ERR', 'DISCONNECTED')):
            other.append(line)
            continue
        block, screen, tex, title, slots = (line.split('|') + ['', '', '', ''])[:5]
        key = (screen, tex, slots)
        seen.setdefault(key, []).append(block)
    for (screen, tex, slots), blocks in sorted(seen.items(), key=lambda k: k[0][1]):
        if tex == '-':
            continue
        ns, path = tex.split(':')
        p = os.path.join(TEX, ns, path)
        if not os.path.exists(p):
            problems.append(f'NO TEXTURE {tex} {blocks[0]}')
            continue
        try:
            im = Image.open(p).convert("RGBA")
        except Exception as e:
            problems.append(f"UNREADABLE {tex} {e}")
            continue
        px = im.load()
        menu = [tuple(int(v) for v in s.split(',')) for s in slots.split(';') if s]
        menu_set = {(x - 1, y - 1) for x, y in menu}
        # scan the panel only: sprites live to the right of x=176
        scan = {(x, y) for y in range(0, 83) for x in range(0, 176 - 17) if painted(px, x, y)}
        floating = [m for m in menu_set if m not in scan and m[1] < 84]
        extra = [s for s in scan if s not in menu_set]
        if floating or extra:
            problems.append(f'{tex.split("textures/gui/")[-1]:42} {blocks[0]:32} ({len(blocks)} blocks) {screen}: нет на текстуре {sorted(floating)}; лишние на текстуре {sorted(extra)}')
    print(f'windows of different kinds: {len(seen)}')
    for line in problems:
        print(line)
    print('--- not opened')
    for line in other:
        print(line)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
