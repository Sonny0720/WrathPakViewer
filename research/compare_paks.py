#!/usr/bin/env python3
"""Compare post-index record regions across multiple Wrath PAK files.

Read-only research: offsets and words are observations, not asset boundaries.
"""
import argparse
import csv
import struct
from pathlib import Path

MAGIC = 0x52049

def u32(blob, at):
    return struct.unpack_from("<I", blob, at)[0]

def analyze(path, sample_bytes):
    blob = path.read_bytes()
    if len(blob) < 32 or u32(blob, 0) != MAGIC:
        raise ValueError(f"{path}: invalid magic or truncated header")
    name_count = u32(blob, 24)
    block = 28 + 4 * name_count + u32(blob, 8)
    if block + 8 > len(blob):
        raise ValueError(f"{path}: post-index block out of range")
    capacity, ids = u32(blob, block), u32(blob, block + 4)
    if ids > capacity or ids > (len(blob) - block - 8) // 2:
        raise ValueError(f"{path}: invalid ID table")
    record = block + 8 + 2 * ids
    end = min(len(blob), record + sample_bytes)
    return blob, dict(file=path.name, size=len(blob), name_count=name_count,
                      block=block, id_capacity=capacity, id_count=ids,
                      record_start=record, record_end=end)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paks", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, default=Path("research_output"))
    parser.add_argument("--bytes", type=int, default=8192)
    args = parser.parse_args()
    if not 1 <= args.bytes <= 1048576:
        parser.error("--bytes must be 1..1048576")
    args.out.mkdir(parents=True, exist_ok=True)
    summary = []
    with (args.out / "post_index_words.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["file", "offset_hex", "relative_offset", "u32_le",
                         "u32_hex", "printable_ascii4"])
        for path in args.paks:
            blob, meta = analyze(path, args.bytes)
            summary.append(meta)
            start, end = meta["record_start"], meta["record_end"]
            for pos in range(start, end - 3, 4):
                word = u32(blob, pos)
                raw = blob[pos:pos+4]
                text = raw.decode("ascii") if all(32 <= b <= 126 for b in raw) else ""
                writer.writerow([path.name, f"0x{pos:X}", pos-start,
                                 word, f"0x{word:08X}", text])
    with (args.out / "archive_summary.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(summary[0]))
        writer.writeheader()
        writer.writerows(summary)
    for meta in summary:
        print(f"{meta['file']}: {meta['size']} bytes, {meta['id_count']} IDs, "
              f"post-index data 0x{meta['record_start']:X}")
    print(f"Saved analysis to {args.out}")

if __name__ == "__main__":
    main()
