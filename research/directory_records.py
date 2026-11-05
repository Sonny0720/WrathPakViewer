#!/usr/bin/env python3
"""Survey candidate 36-byte records without assuming they are asset boundaries.

CAUTION: name fields overlap additional nonzero words. This is a diagnostic
fixed-stride view only, not a validated directory schema or asset extractor.
"""
import argparse
import csv
import struct
from pathlib import Path

def u32(b, p):
    return struct.unpack_from("<I", b, p)[0]

def analyze(path, count):
    data = path.read_bytes()
    if len(data) < 32 or u32(data, 0) != 0x52049:
        raise ValueError(f"{path}: invalid PAK header")
    block = 28 + 4*u32(data,24) + u32(data,8)
    if block+8 > len(data):
        raise ValueError("post-index header out of bounds")
    cap, n = struct.unpack_from("<II",data,block)
    if n > cap or n > (len(data)-block-8)//2:
        raise ValueError("invalid post-index ID table")
    start = block+8+2*n
    rows=[]
    for i in range(count):
        p=start+36*i
        if p+36 > len(data):break
        raw=data[p+12:p+36]
        nul=raw.find(b"\x00")
        name=raw[:nul].decode("ascii","replace") if nul>=0 else ""
        printable=bool(name) and all(32<=ord(c)<=126 for c in name)
        rows.append([path.name,i,f"0x{p:08X}",f"0x{u32(data,p):08X}",
                     f"0x{u32(data,p+4):08X}",f"0x{u32(data,p+8):08X}",
                     name,printable,raw.hex()])
    return rows

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("paks",nargs="+",type=Path)
    ap.add_argument("--count",type=int,default=64)
    ap.add_argument("--out",type=Path,default=Path("candidate_36_byte_records.csv"))
    args=ap.parse_args()
    if not 1<=args.count<=10000:ap.error("--count must be 1..10000")
    args.out.parent.mkdir(parents=True,exist_ok=True)
    with args.out.open("w",newline="",encoding="utf-8") as f:
        writer=csv.writer(f)
        writer.writerow(["archive","index","offset","word0","word1","word2",
                         "candidate_name","printable","raw_24_bytes_hex"])
        for p in args.paks:
            rows=analyze(p,args.count)
            writer.writerows(rows)
            print(f"{p.name}: {len(rows)} candidate windows")
    print("These records are unverified and MUST NOT be used as file offsets.")

if __name__=="__main__":
    main()
