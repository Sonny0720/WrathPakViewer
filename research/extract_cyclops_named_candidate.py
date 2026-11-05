#!/usr/bin/env python3
"""Experimental named Cyclops mesh candidate; counts and resource association need validation."""
import struct,sys
from pathlib import Path
src=Path(sys.argv[1]); out=Path(sys.argv[2]) if len(sys.argv)>2 else Path("cyclops_named_body_285_triangles.obj")
b=src.read_bytes()
assert b"C_Cyclops:C_DC_Cyclops_body_V1" in b
vstart=0xB278; n=struct.unpack_from("<I",b,0xB274)[0]
istart=0x834e; m=struct.unpack_from("<I",b,0x834a)[0]
assert n==2158 and m==855 and m%3==0
assert vstart+32*n<=len(b) and istart+2*m<=len(b)
indices=struct.unpack_from(f"<{m}H",b,istart)
assert max(indices)<n
with out.open("w") as f:
 f.write("# Experimental Cyclops named-body candidate; mesh association not fully verified\n")
 for i in range(n):
  x,y,z=struct.unpack_from("<3f",b,vstart+32*i)
  f.write(f"v {x:.8g} {y:.8g} {z:.8g}\n")
 for i in range(n):
  u,v=struct.unpack_from("<2f",b,vstart+32*i+16)
  f.write(f"vt {u:.8g} {1-v:.8g}\n")
 for i in range(0,m,3):
  a,c,d=indices[i:i+3]
  f.write(f"f {a+1}/{a+1} {c+1}/{c+1} {d+1}/{d+1}\n")
print(f"{n} vertices; {m//3} candidate triangles; output {out}")
