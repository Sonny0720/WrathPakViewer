#!/usr/bin/env python3
"""Experimental Cyclops multi-topology OBJ exporter (NOT a verified model decoder)."""
import argparse,struct,collections,math
from pathlib import Path
HEADERS=(0x833e,0x89fc,0x97a4,0xa0de,0xa7da,0xaac5,0xafc1,0xb222)
def run(src,out):
 b=src.read_bytes()
 vertices=[struct.unpack_from('<3f',b,0xb278+32*i) for i in range(2158)]
 if any(not all(math.isfinite(c) for c in v) for v in vertices):raise ValueError('nonfinite vertices')
 with out.open('w') as f:
  f.write('# UNVERIFIED Cyclops candidate: subtype 1 strip, others list\n')
  for v in vertices:f.write('v %.8g %.8g %.8g\n'%v)
  for j,h in enumerate(HEADERS,1):
   tag,kind,capacity,n=struct.unpack_from('<4I',b,h)
   if tag!=4 or n==0 or h+16+2*n>len(b):raise ValueError('invalid section')
   indices=struct.unpack_from('<'+str(n)+'H',b,h+16)
   if max(indices)>=len(vertices):raise ValueError('index exceeds vertex count')
   if kind==1:
    triangles=[(indices[i-2],indices[i-1],indices[i]) if i%2==0 else (indices[i-1],indices[i-2],indices[i]) for i in range(2,n)]
   else:triangles=[indices[i:i+3] for i in range(0,n-2,3)]
   triangles=[t for t in triangles if len(set(t))==3]
   edges=collections.Counter(tuple(sorted((a,c))) for t in triangles for a,c in ((t[0],t[1]),(t[1],t[2]),(t[2],t[0])))
   print(j,'subtype',kind,'mode','strip' if kind==1 else 'list','faces',len(triangles),'nonmanifold edges',sum(v>2 for v in edges.values()))
   f.write(f'g section_{j:02d}_type_{kind}\n')
   for t in triangles:f.write('f %d %d %d\n'%tuple(v+1 for v in t))
 print('Wrote',out,'— visually verify before treating as real geometry.')
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('pak',type=Path);p.add_argument('--out',type=Path,default=Path('cyclops_mixed_topology_candidate.obj'))
 a=p.parse_args();run(a.pak,a.out)
