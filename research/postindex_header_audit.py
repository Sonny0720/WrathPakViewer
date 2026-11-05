#!/usr/bin/env python3
"""Cross-archive audit of candidate post-index header fields.

This does not assert that candidate entry counts or names represent a complete
resource directory. No payload boundaries or mesh data are inferred.
"""
import argparse
import csv
import struct
from pathlib import Path

def u32(blob, pos):
    return struct.unpack_from("<I", blob, pos)[0]

def inspect(path):
    blob = path.read_bytes()
    if len(blob) < 32 or u32(blob, 0) != 0x52049:
        raise ValueError(f"{path}: invalid header")
    name_count = u32(blob, 24)
    block = 28 + 4 * name_count + u32(blob, 8)
    if block + 8 > len(blob):
        raise ValueError(f"{path}: invalid post-index offset")
    capacity, count = struct.unpack_from("<II", blob, block)
    if count > capacity or count > (len(blob) - block - 8) // 2:
        raise ValueError(f"{path}: invalid post-index IDs")
    start = block + 8 + 2 * count
    if start + 48 > len(blob):
        raise ValueError(f"{path}: truncated candidate header")
    a, b, c = struct.unpack_from("<III", blob, start)
    preview = blob[start + 12:start + 44]
    nul = preview.find(b"\x00")
    first = preview[:nul].decode("ascii", "replace") if nul >= 0 else ""
    return [path.name, len(blob), f"0x{block:X}", count, f"0x{start:X}",
            a, b, c, first, blob[start + 12:start + 48].hex()]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paks", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, default=Path("postindex_header_audit.csv"))
    args = parser.parse_args()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["archive", "archive_bytes", "block_offset", "id_count",
                         "record_start", "header_word0", "header_word1",
                         "header_word2_candidate_count", "first_string",
                         "next_36_bytes_hex"])
        for pak in args.paks:
            row = inspect(pak)
            writer.writerow(row)
            print(pak.name, "candidate count:", row[7], "first string:", row[8])
    print("Wrote", args.out)

if __name__ == "__main__":
    main()
