#!/usr/bin/env python3
"""Locate *candidate* triangle-index streams in Wrath PAKs.

A candidate is a run of little-endian uint16 triples with repeated indices,
plausible vertex range, and nondegenerate triangles. These heuristics can
produce false positives; no OBJ is written without a verified vertex format.
"""
import argparse,csv,struct
from pathlib import Path

def scan(data,step=2,window=192,max_vertices=65535):
    # Sample bounded windows instead of enumerating every alignment/length.
    for offset in range(0,len(data)-window*2+1,step*window):
        for align in (0,2):
            start=offset+align
            if start+window*2>len(data):continue
            values=struct.unpack_from("<"+str(window)+"H",data,start)
            triangles=[values[i:i+3] for i in range(0,window,3)]
            unique=set(values)
            degenerate=sum(len(set(t))<3 for t in triangles)
            if len(unique)<24 or len(unique)>window*0.85:continue
            if degenerate>len(triangles)//16:continue
            lo,hi=min(unique),max(unique)
            if hi>=max_vertices or hi-lo>8192:continue
            # A high proportion of adjacent index values is a weak topology clue.
            shared=sum(len(set(triangles[i]) & set(triangles[i+1]))>0
                       for i in range(len(triangles)-1))
            if shared<4:continue
            yield offset+align,lo,hi,len(unique),degenerate,shared

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("paks",nargs="+",type=Path)
    ap.add_argument("--out",type=Path,default=Path("candidate_index_streams.csv"))
    ap.add_argument("--max-results",type=int,default=200)
    args=ap.parse_args()
    if not 1<=args.max_results<=100000:ap.error("invalid --max-results")
    args.out.parent.mkdir(parents=True,exist_ok=True)
    with args.out.open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f)
        w.writerow(["archive","offset_hex","minimum_index","maximum_index",
                    "unique_indices","degenerate_triangles","adjacent_shared_vertices"])
        for pak in args.paks:
            data=pak.read_bytes()
            count=0
            for record in scan(data):
                w.writerow([pak.name,f"0x{record[0]:X}",*record[1:]])
                count+=1
                if count>=args.max_results:break
            print(pak.name,count,"heuristic candidates (NOT verified meshes)")
    print("No model files are produced; vertex and index formats require validation.")
if __name__=="__main__":
    main()
