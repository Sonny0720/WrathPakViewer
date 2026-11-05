#!/usr/bin/env python3
"""Export corrected Cyclops candidate using contiguous XYZ floats.

Experimental: candidate index region and resource bounds remain unverified.
"""
import argparse,math,struct,statistics
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("pak",type=Path)
    p.add_argument("--index-offset",type=lambda x:int(x,0),default=0x8402)
    p.add_argument("--index-count",type=int,default=384)
    p.add_argument("--vertex-offset",type=lambda x:int(x,0),default=0xB278)
    p.add_argument("--out",type=Path,default=Path("cyclops_corrected_vertex_alignment.obj"))
    a=p.parse_args()
    b=a.pak.read_bytes()
    if a.index_count<3 or a.index_count%3 or a.index_offset<0 or a.index_offset+2*a.index_count>len(b):
        p.error("invalid index range")
    idx=struct.unpack_from(f"<{a.index_count}H",b,a.index_offset)
    n=max(idx)+1
    if a.vertex_offset<0 or a.vertex_offset+32*n>len(b):
        p.error("vertex range outside archive")
    verts=[struct.unpack_from("<3f",b,a.vertex_offset+32*i) for i in range(n)]
    uvs=[struct.unpack_from("<2f",b,a.vertex_offset+32*i+16) for i in range(n)]
    if not all(math.isfinite(x) for row in verts+uvs for x in row):
        raise ValueError("nonfinite vertex or UV coordinate")
    faces=[idx[i:i+3] for i in range(0,len(idx),3) if len(set(idx[i:i+3]))==3]
    lengths=[math.dist(verts[x],verts[y]) for f in faces for x,y in
             ((f[0],f[1]),(f[1],f[2]),(f[2],f[0]))]
    with a.out.open("w",encoding="utf-8") as f:
        f.write("# Experimental candidate; geometry identity unverified\n")
        for x,y,z in verts:f.write(f"v {x:.8g} {y:.8g} {z:.8g}\n")
        for u,v in uvs:f.write(f"vt {u:.8g} {1-v:.8g}\n")
        for x,y,z in faces:f.write(f"f {x+1}/{x+1} {y+1}/{y+1} {z+1}/{z+1}\n")
    print(f"{len(verts)} vertices, {len(faces)} faces")
    print(f"median edge={statistics.median(lengths):.3f}, max edge={max(lengths):.3f}")
    print("Candidate only: no verified mesh boundaries or visual identity.")
if __name__=="__main__":main()
