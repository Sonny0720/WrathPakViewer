#!/usr/bin/env python3
"""Reproducible experimental audit of Cyclops PAK candidate mesh; no verified extraction claim."""
import csv, math, struct, statistics, argparse
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('pak',type=Path)
p.add_argument('--out',type=Path,default=Path('cyclops_suspicious_faces.csv'))
a=p.parse_args()
b=a.pak.read_bytes()
indices=struct.unpack_from('<384H',b,0x8402)
verts=[(struct.unpack_from('<f',b,0xB280+32*i)[0],
        *struct.unpack_from('<ff',b,0xB280+32*i+24))
       for i in range(max(indices)+1)]
rows=[];edges=[]
for j in range(0,384,3):
 t=indices[j:j+3]
 lengths=[math.dist(verts[x],verts[y]) for x,y in
          ((t[0],t[1]),(t[1],t[2]),(t[2],t[0]))]
 edges.extend(lengths)
 rows.append((j//3,*t,*lengths,max(lengths)))
med=statistics.median(edges)
with a.out.open('w',newline='') as f:
 w=csv.writer(f)
 w.writerow(['triangle','i0','i1','i2','edge01','edge12','edge20','longest_edge','over_5x_median'])
 for row in rows:w.writerow([*row,int(row[-1]>5*med)])
print('vertices:',len(verts),'faces:',len(rows),'median edge:',round(med,3))
print('suspicious face IDs:',[row[0] for row in rows if row[-1]>5*med])
print('output:',a.out)
