#!/usr/bin/env python3
"""Read-only, reproducible structural survey of Wrath Unleashed PAK archives.

This is NOT an asset extractor. It does not interpret unknown values as offsets,
vertices, triangles, or compression headers.
"""
import argparse
import csv
import struct
from pathlib import Path

MAGIC = 0x00052049

def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]

def inspect(path, output, limit):
    data = path.read_bytes()
    if len(data) < 32 or u32(data, 0) != MAGIC:
        raise ValueError("Not a recognized Wrath PAK header")
    count = u32(data, 24)
    # Existing app's validated post-index block position.
    start = 28 + 4 * count + u32(data, 8)
    if start + 8 > len(data):
        raise ValueError("Post-index block outside file")
    capacity, id_count = u32(data, start), u32(data, start + 4)
    if id_count > capacity or id_count > (len(data) - start - 8) // 2:
        raise ValueError("Invalid post-index ID count")
    record_start = start + 8 + 2 * id_count
    end = min(len(data), record_start + limit)
    print(f"file={path.name} bytes={len(data)} post_index=0x{start:X} "
          f"capacity={capacity} ids={id_count} data_start=0x{record_start:X}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["offset_hex", "relative_offset", "length", "ascii",
                         "previous_u32_hex", "next_u32_hex"])
        p = record_start
        while p < end:
            q = p
            while q < end and 32 <= data[q] <= 126:
                q += 1
            if q - p >= 4 and q < end and data[q] == 0:
                before = f"0x{u32(data,p-4):08X}" if p >= 4 else ""
                after = f"0x{u32(data,q+1):08X}" if q+5 <= len(data) else ""
                writer.writerow([f"0x{p:08X}", p-record_start, q-p,
                                 data[p:q].decode("ascii"), before, after])
                p = q+1
            else:
                p += 1
    print(f"Saved string context CSV: {output}")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pak", type=Path)
    parser.add_argument("--out", type=Path, default=Path("post_index_strings.csv"))
    parser.add_argument("--limit", type=int, default=65536)
    args = parser.parse_args()
    if args.limit < 1 or args.limit > 16 * 1024 * 1024:
        parser.error("--limit must be between 1 and 16777216")
    inspect(args.pak, args.out, args.limit)

if __name__ == "__main__":
    main()
