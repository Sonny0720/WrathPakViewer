#!/usr/bin/env python3
"""Reproduce the Cyclops triangle-list OBJ candidate at measured offsets.

Experimental only. This file is not a verified game model; connectivity,
asset boundaries, and vertex semantics are not yet established.
"""
import argparse,collections,math,struct
from pathlib import Path

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("pak",type=Path)
    ap.add_argument("--out",type=Path,default=Path("cyclops_triangle_list_candidate.obj"))
    args=ap.parse_args()
    data=args.pak.read_bytes()
    vo,io,nv,ni=0xB280,0x8402,512,384
    if len(data)<max(vo+32*nv,io+2*ni):raise ValueError("Archive too small")
    vertices=[]
    for i in range(nv):
        p=vo+32*i
        values=(struct.unpack_from("<f",data,p)[0],
                struct.unpack_from("<f",data,p+24)[0],
                struct.unpack_from("<f",data,p+28)[0],
                struct.unpack_from("<f",data,p+8)[0],
                struct.unpack_from("<f",data,p+12)[0])
        if not all(math.isfinite(x) for x in values):raise ValueError("Nonfinite vertex")
        vertices.append(values)
    indices=struct.unpack_from("<384H",data,io)
    if max(indices)>=nv:raise ValueError("Indices exceed candidate vertex range")
    faces=[indices[i:i+3] for i in range(0,ni,3) if len(set(indices[i:i+3]))==3]
    edges=collections.Counter(tuple(sorted((a,b))) for tri in faces
                              for a,b in ((tri[0],tri[1]),(tri[1],tri[2]),(tri[2],tri[0])))
    args.out.parent.mkdir(parents=True,exist_ok=True)
    with args.out.open("w",encoding="utf-8") as out:
        out.write("# UNVERIFIED geometry candidate; do not label as extracted Cyclops\n")
        for x,y,z,u,v in vertices:out.write(f"v {x:.8g} {y:.8g} {z:.8g}\n")
        for x,y,z,u,v in vertices:out.write(f"vt {u:.8g} {1-v:.8g}\n")
        for tri in faces:out.write("f "+" ".join(f"{i+1}/{i+1}" for i in tri)+"\n")
    print(f"{len(faces)} candidate triangles, {sum(n>2 for n in edges.values())} nonmanifold edges, "
          f"{sum(n==1 for n in edges.values())} boundary edges")
    print(f"Saved experimental OBJ to {args.out}")
if __name__=="__main__":main()
