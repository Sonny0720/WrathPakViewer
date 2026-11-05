#!/usr/bin/env python3
"""Inspect candidate named Cyclops body V1 vertex block; no faces are invented."""
import argparse,math,struct
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('pak',type=Path)
p.add_argument('--out',type=Path,default=Path('cyclops_body_vertex_pointcloud.obj'))
a=p.parse_args()
b=a.pak.read_bytes()
name=b'C_Cyclops:C_DC_Cyclops_body_V1'
loc=b.find(name)
if loc<0:raise SystemExit('named body resource not found')
start=0xB278;stride=32;count=2158
if start+count*stride>len(b):raise SystemExit('range out of bounds')
vertices=[]
for i in range(count):
 xyz=struct.unpack_from('<3f',b,start+i*stride)
 if not all(math.isfinite(x) for x in xyz):raise SystemExit(f'nonfinite vertex {i}')
 vertices.append(xyz)
with a.out.open('w') as f:
 f.write('# Experimental named-body candidate point cloud; faces not verified\n')
 for x,y,z in vertices:f.write(f'v {x:.8g} {y:.8g} {z:.8g}\n')
 for i in range(count):f.write(f'p {i+1}\n')
print('named resource offset',hex(loc),'candidate vertex start',hex(start))
print('candidate vertex count',count,'stride',stride,'end',hex(start+count*stride))
for axis in range(3):
 values=[v[axis] for v in vertices]
 print('XYZ'[axis],min(values),max(values))
print('output',a.out)
