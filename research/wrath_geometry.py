#!/usr/bin/env python3
"""Bounded Xbox character geometry decoder. No textures, rig or PS2 support claimed.

Exports each discovered buffer separately and combines buffers with the same
serialized character/material variant. Source coordinates are preserved (Y up).
"""
import argparse
from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import re
import struct


class FormatError(ValueError):
    pass


class Reader:
    def __init__(self, data, offset, end=None):
        self.data, self.pos, self.end = data, offset, len(data) if end is None else end

    def uint(self):
        if self.pos + 4 > self.end:
            raise FormatError('truncated uint32')
        value = struct.unpack_from('<I', self.data, self.pos)[0]
        self.pos += 4
        return value

    def count(self, maximum):
        capacity, count = self.uint(), self.uint()
        if not 0 <= count <= capacity <= maximum:
            raise FormatError('invalid allocation/count')
        return count

    def indices(self):
        count = self.count(100000)
        if self.pos + 2 * count > self.end:
            raise FormatError('truncated uint16 array')
        values = struct.unpack_from(f'<{count}H', self.data, self.pos)
        self.pos += 2 * count
        return values


def triangles(values, strip=False):
    if not strip and len(values) % 3:
        raise FormatError('triangle list count is not divisible by three')
    if strip:
        source = ((values[i-2], values[i-1], values[i]) if i % 2 == 0
                  else (values[i-1], values[i-2], values[i])
                  for i in range(2, len(values)))
    else:
        source = (values[i:i+3] for i in range(0, len(values), 3))
    # Strip parity advances even across degenerate stitching indices.
    return [tuple(t) for t in source if len(set(t)) == 3]


def draw_container(reader):
    batches = reader.count(64)
    if batches == 0:
        raise FormatError('empty draw container')
    faces, evidence = [], []
    for _ in range(batches):
        start = reader.pos
        ids = reader.indices()
        faces.extend(triangles(ids))
        strips = reader.count(64)
        strip_counts = []
        for _ in range(strips):
            strip = reader.indices()
            faces.extend(triangles(strip, True))
            strip_counts.append(len(strip))
        palette = reader.indices()
        flag = reader.uint()
        if flag != 0:
            raise FormatError('unsupported batch trailer')
        evidence.append(dict(offset=start, list_indices=len(ids),
                             strip_indices=strip_counts, palette_entries=len(palette)))
    return faces, evidence


def resource(data, offset, end):
    reader = Reader(data, offset, end)
    # Observed optional serialization prefix; it is not a topology enum.
    if data[offset:offset+8] == struct.pack('<II', 4, 0):
        reader.pos += 8
    faces, batches = draw_container(reader)
    while (reader.pos + 8 <= end and data[reader.pos:reader.pos+4] == struct.pack('<I', 4)
           and 1 <= struct.unpack_from('<I', data, reader.pos+4)[0] <= 4):
        continuation = Reader(data, reader.pos, end)
        more_faces, more_batches = draw_container(continuation)
        faces.extend(more_faces)
        batches.extend(more_batches)
        reader.pos = continuation.pos
    return faces, batches, reader.pos


def vertex_buffers(data):
    found = []
    for power in range(7, 17):
        capacity = 1 << power
        pattern, pos = struct.pack('<I', capacity), 0
        while (pos := data.find(pattern, pos)) >= 0:
            if pos + 8 <= len(data):
                count = struct.unpack_from('<I', data, pos+4)[0]
                end = pos + 8 + 32 * count
                if (100 < count <= capacity and end + 24 <= len(data)
                        and data[end:end+16] == bytes(16)
                        and struct.unpack_from('<I', data, end+20)[0] == count):
                    records = [struct.unpack_from('<3fI2fII', data, pos+8+32*i)
                               for i in range(count)]
                    if all(math.isfinite(x) and abs(x) < 100000
                           for row in records for x in (*row[:3], *row[4:6])):
                        found.append(dict(offset=pos, end=end, records=records))
            pos += 1
    return sorted(found, key=lambda item: item['offset'])


def variant(name):
    match = re.fullmatch(r'(C_[^:]+):(C_[A-Z]{2}_[^_]+)_(.+)', name)
    if not match:
        return None
    return match[1] + '_' + match[2] + ('_low' if re.match(r'L_V', match[3]) else '_main')


def decode(data):
    if len(data) < 28 or struct.unpack_from('<I', data)[0] != 0x52049:
        raise FormatError('unsupported PAK header; only observed Xbox layout supported')
    markers = list(re.finditer(rb'meshes\\[^\x00-\x1f]{1,120}\.msh', data))
    names = list(re.finditer(rb'[A-Za-z][A-Za-z_0-9]*:[A-Za-z][A-Za-z_0-9]*', data))
    result, rejected, previous = [], [], 0
    for vb in vertex_buffers(data):
        marker = next((m for m in reversed(markers) if m.start() < vb['offset']), None)
        floor = max(previous, marker.end() if marker else 0)
        previous = vb['end']
        if marker is None or not marker.group().lower().startswith(b'meshes\\chars\\'):
            continue
        groups = []
        consumed = floor
        for name in names:
            if not consumed <= name.start() < vb['offset'] or name.start() < 4:
                continue
            if struct.unpack_from('<I', data, name.start()-4)[0] != len(name.group()):
                continue
            label = name.group().decode('ascii')
            if not label.startswith('C_'):
                continue
            try:
                faces, batches, end = resource(data, name.end(), vb['offset'])
                if not faces or any(max(t) >= len(vb['records']) for t in faces):
                    raise FormatError('empty geometry or index exceeds vertex buffer')
                groups.append(dict(name=label, offset=name.start(), end=end,
                                   faces=faces, batches=batches))
                consumed = end
            except FormatError as error:
                rejected.append(dict(name=label, offset=name.start(), reason=str(error)))
        if groups:
            keys = {variant(g['name']) for g in groups}
            result.append(dict(**vb, source=marker.group().decode(), groups=groups,
                               variant=next(iter(keys)) if len(keys) == 1 else None))
    return result, rejected


def write_obj(buffers, path):
    base = 0
    with path.open('w', encoding='utf-8') as out:
        out.write('# Xbox geometry; source coordinates Y up. Rig/materials not recovered.\n')
        for buffer in buffers:
            for row in buffer['records']:
                out.write('v %.9g %.9g %.9g\n' % tuple(row[:3]))
            for row in buffer['records']:
                out.write('vt %.9g %.9g\n' % (row[4], 1-row[5]))
            for group in buffer['groups']:
                out.write('g ' + re.sub(r'[^A-Za-z0-9_]', '_', group['name'])
                          + '_' + format(group['offset'], 'x') + '\n')
                for face in group['faces']:
                    out.write('f ' + ' '.join(f'{i+base+1}/{i+base+1}' for i in face) + '\n')
            base += len(buffer['records'])


def extract(path, output):
    data = path.read_bytes()
    buffers, rejected = decode(data)
    if not buffers:
        raise FormatError('no supported character buffers found')
    output.mkdir(parents=True, exist_ok=True)
    exports = []
    for number, buffer in enumerate(buffers):
        name = f'buffer_{number:02d}.obj'
        write_obj([buffer], output/name)
        exports.append(name)
    variants = sorted({b['variant'] for b in buffers if b['variant']})
    for key in variants:
        selected = [b for b in buffers if b['variant'] == key]
        # Multiple mesh sources are never combined, even if material names match.
        for source in sorted({b['source'] for b in selected}):
            same = [b for b in selected if b['source'] == source]
            name = re.sub(r'[^A-Za-z0-9_]', '_', key + '_' + source.rsplit('\\', 1)[-1]) + '.obj'
            write_obj(same, output/name)
            exports.append(name)
    report = dict(archive=path.name, sha256=hashlib.sha256(data).hexdigest(),
                  status='recognizability requires visual inspection; completeness unproven',
                  exports=exports, rejected=rejected, buffers=[])
    for buffer in buffers:
        used = {i for g in buffer['groups'] for t in g['faces'] for i in t}
        report['buffers'].append(dict(offset=buffer['offset'], vertices=len(buffer['records']),
            referenced_vertices=len(used), source=buffer['source'], variant=buffer['variant'],
            groups=[{k: v for k, v in g.items() if k != 'faces'} | dict(triangles=len(g['faces']))
                    for g in buffer['groups']]))
    (output/'audit.json').write_text(json.dumps(report, indent=2)+'\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pak', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = extract(args.pak, args.out)
    except (OSError, FormatError) as error:
        parser.exit(1, str(error)+'\n')
    print(f"{len(report['buffers'])} buffers; {len(report['exports'])} OBJ exports; "
          f"{len(report['rejected'])} rejected resource candidates")


if __name__ == '__main__':
    main()
