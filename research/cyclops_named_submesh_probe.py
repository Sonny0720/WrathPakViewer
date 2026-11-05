#!/usr/bin/env python3
"""Inspect named Cyclops index sections as separate experimental OBJ groups."""
import argparse,collections,struct
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("pak",type=Path)
p.add_argument("--out",type=Path,default=Path("cyclops_named_groups_candidate.obj"))
a=p.parse_args()
b=a.pak.read_bytes()
blocks=[("body_initial",0x834e,855),("body_second",0x8a0c,1460),("body_named_later",0xaad5,630),("additional_block",0xafcf,114)]
verts=[struct.unpack_from("<3f",b,0xb278+32*i) for i in range(2158)]
with a.out.open("w") as f:
 f.write("# Experimental geometry groups; index topology and grouping unverified\n")
 for v in verts:f.write("v %.8g %.8g %.8g\n"%v)
 for name,off,n in blocks:
  indices=struct.unpack_from("<"+str(n)+"H",b,off)
  faces=[indices[i:i+3] for i in range(0,n-2,3) if len(set(indices[i:i+3]))==3 and max(indices[i:i+3])<len(verts)]
  f.write(f"g {name}\n")
  for t in faces:f.write("f %d %d %d\n"%tuple(i+1 for i in t))
  edges=collections.Counter(tuple(sorted(e)) for x,y,z in faces for e in ((x,y),(y,z),(z,x)))
  print(name,hex(off),"indices",n,"min",min(indices),"max",max(indices),"faces",len(faces),"nonmanifold",sum(v>2 for v in edges.values()))
print("Wrote",a.out)
