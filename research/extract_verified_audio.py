#!/usr/bin/env python3
"""Extract embedded RIFF/WAVE streams with validated chunk boundaries."""
import argparse
import csv
import struct
from pathlib import Path

def waves(blob):
    cursor=0
    while True:
        pos=blob.find(b"RIFF",cursor)
        if pos<0:break
        cursor=pos+4
        if pos+12>len(blob) or blob[pos+8:pos+12]!=b"WAVE":continue
        length=struct.unpack_from("<I",blob,pos+4)[0]+8
        if length<20 or pos+length>len(blob):continue
        off=pos+12
        has_fmt=has_data=False
        valid=True
        while off+8<=pos+length:
            tag=blob[off:off+4]
            size=struct.unpack_from("<I",blob,off+4)[0]
            if off+8+size>pos+length:
                valid=False
                break
            if tag==b"fmt " and size>=16:has_fmt=True
            if tag==b"data":has_data=True
            off+=8+size+(size&1)
        if valid and has_fmt and has_data and off==pos+length:
            yield pos,length
            cursor=pos+length

def extract(path,output):
    blob=path.read_bytes()
    if len(blob)<32 or struct.unpack_from("<I",blob)[0]!=0x52049:
        raise ValueError(f"{path}: invalid PAK header")
    folder=output/path.stem
    folder.mkdir(parents=True,exist_ok=True)
    found=list(waves(blob))
    with (folder/"manifest.csv").open("w",newline="",encoding="utf-8") as f:
        writer=csv.writer(f)
        writer.writerow(["filename","source_offset_hex","bytes"])
        for i,(offset,size) in enumerate(found):
            filename=f"wave_{i:04d}_{offset:08x}.wav"
            (folder/filename).write_bytes(blob[offset:offset+size])
            writer.writerow([filename,f"0x{offset:08X}",size])
    print(f"{path.name}: {len(found)} WAV streams")

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("paks",nargs="+",type=Path)
    ap.add_argument("--out",type=Path,default=Path("extracted_audio"))
    args=ap.parse_args()
    for path in args.paks:extract(path,args.out)

if __name__=="__main__":
    main()
