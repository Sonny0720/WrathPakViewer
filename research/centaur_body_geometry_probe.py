#!/usr/bin/env python3
"""Experimental Centaur body OBJ from a candidate shared vertex buffer.

Requires the user's legally obtained c_icentaur-lo_centaur-.pak.
Offsets are research hypotheses, not a complete general-purpose extractor.
"""
import argparse,struct,math
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('pak',type=Path);p.add_argument('out',type=Path)
a=p.parse_args();b=a.pak.read_bytes()
vertex_count=struct.unpack_from('<I',b,0x23790)[0]
assert 0<vertex_count<100000 and 0x23794+vertex_count*32<=len(b)
vertices=[struct.unpack_from('<3f',b,0x23794+i*32) for i in range(vertex_count)]
assert all(all(math.isfinite(x) for x in v) for v in vertices)
header=struct.unpack_from('<6I',b,0x19cab)
assert header[:5]==(4,0,4,2,8192),header
indices=struct.unpack_from(f'<{header[5]}H',b,0x19cab+24)
faces=[indices[i:i+3] for i in range(0,len(indices)-2,3)]
faces=[f for f in faces if len(set(f))==3 and max(f)<vertex_count]
with a.out.open('w') as out:
 out.write('# Candidate Centaur body, unverified\n')
 for v in vertices:out.write('v %.7f %.7f %.7f\n'%v)
 out.write('g Centaur_Body_candidate\n')
 for face in faces:out.write('f %d %d %d\n'%tuple(i+1 for i in face))
print('vertices',vertex_count,'faces',len(faces),'output',a.out)
