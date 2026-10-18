#!/usr/bin/env python3
"""Write GameTest structures (a stone floor plus empty space) as NBT.
Run from the repo root: python3 -I tools/gen/gametest_structures.py"""
import gzip, os, struct

DATA_VERSION = 4790  # MC 26.1.2 (version.json world_version)


def s(x):
    b = x.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def named(tag, name, payload):
    return bytes([tag]) + s(name) + payload


def compound(items):
    return b''.join(items) + b'\x00'


def int_list(values):
    return bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values)


def compound_list(compounds):
    if not compounds:
        return bytes([0]) + struct.pack('>i', 0)
    return bytes([10]) + struct.pack('>i', len(compounds)) + b''.join(compounds)


def structure(path, sx, sy, sz, floor='minecraft:smooth_stone'):
    blocks = [compound([named(9, 'pos', int_list([x, 0, z])), named(3, 'state', struct.pack('>i', 0))])
              for x in range(sx) for z in range(sz)]
    palette = [compound([named(8, 'Name', s(floor))])]
    root = compound([named(9, 'size', int_list([sx, sy, sz])), named(9, 'entities', compound_list([])),
                     named(9, 'blocks', compound_list(blocks)), named(9, 'palette', compound_list(palette)),
                     named(3, 'DataVersion', struct.pack('>i', DATA_VERSION))])
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(named(10, '', root))


structure('src/main/resources/data/iis/structure/floor9.nbt', 9, 7, 9)
print('ok')
