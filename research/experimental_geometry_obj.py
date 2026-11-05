#!/usr/bin/env python3
"""Experimental Cyclops geometry reconstruction, NOT a verified model decoder.

Observed Cyclops PAK has a repeated 32-byte vertex-like record near 0xB280:
position floats at +0,+24,+28, UV floats at +8,+12; other fields unknown.
Index-like uint16 triples occur near 0x8400. User-supplied offsets are
required; output MUST be visually inspected before calling it a mesh.
"""
import argparse,math,struct
from pathlib import Path

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument("pak",type=Path)
 p.add_argument("--vertex-offset",type=lambda x:int(x,0),required=True)
 p.add_argument("--vertex-count",type=int,required=True)
 p.add_argument("--index-offset",type=lambda x:int(x,0),required=True)
 p.add_argument("--triangle-count",type=int,required=True)
 p.add_argument("--topology",choices=["list","strip"],default="list")
 p.add_argument("--out",type=Path,default=Path("experimental.obj"))
 a=p.parse_args()
 b=a.pak.read_bytes()
 if a.vertex_count<3 or a.triangle_count<1: p.error("counts must be positive")
 if a.vertex_offset<0 or a.vertex_offset+32*a.vertex_count>len(b):p.error("vertex range out of bounds")
 if a.index_offset<0 or a.index_offset+(6*a.triangle_count if a.topology=="list" else 2*(a.triangle_count+2))>len(b):p.error("index range out of bounds")
 verts=[]
 for i in range(a.vertex_count):
  at=a.vertex_offset+32*i
  x=struct.unpack_from("<f",b,at)[0]
  y,z=struct.unpack_from("<ff",b,at+24)
  u,v=struct.unpack_from("<ff",b,at+8)
  if not all(math.isfinite(t) and abs(t)<100000 for t in (x,y,z,u,v)):
   raise ValueError(f"invalid candidate vertex {i} at 0x{at:X}")
  verts.append((x,y,z,u,v))
 faces=[]
 if a.topology=="list":
  for i in range(a.triangle_count):
   tri=struct.unpack_from("<HHH",b,a.index_offset+6*i)
   if max(tri)>=len(verts):raise ValueError(f"index outside vertex range: triangle {i}: {tri}")
   if len(set(tri))==3:faces.append(tri)
 else:
  # Here --triangle-count is the number of strip triangles, requiring N+2 indices.
  if a.index_offset+2*(a.triangle_count+2)>len(b):p.error("strip index range out of bounds")
  values=struct.unpack_from("<"+str(a.triangle_count+2)+"H",b,a.index_offset)
  for i in range(a.triangle_count):
   tri=(values[i],values[i+1],values[i+2])
   if max(tri)>=len(verts):raise ValueError(f"index outside vertex range: strip triangle {i}: {tri}")
   if len(set(tri))==3:
    faces.append((tri[1],tri[0],tri[2]) if i&1 else tri)
 a.out.parent.mkdir(parents=True,exist_ok=True)
 with a.out.open("w",encoding="utf-8") as f:
  f.write("# EXPERIMENTAL / UNVERIFIED Wrath geometry hypothesis\n")
  for x,y,z,u,v in verts:f.write(f"v {x:.8g} {y:.8g} {z:.8g}\n")
  for x,y,z,u,v in verts:f.write(f"vt {u:.8g} {1-v:.8g}\n")
  for tri in faces:f.write("f "+" ".join(f"{n+1}/{n+1}" for n in tri)+"\n")
 print(f"Experimental OBJ: {len(verts)} vertices, {len(faces)} faces -> {a.out}")
 print("Do not treat this as a validated mesh until the result is inspected.")
if __name__=="__main__":main()
