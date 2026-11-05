#!/usr/bin/env python3
"""Compare candidate Cyclops mesh-group alignments and edge-length outliers.

Requires only Python's standard library. Candidate data is not a verified game mesh.
"""
import argparse,csv,math,statistics,struct,collections
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('pak',type=Path);p.add_argument('--out',type=Path,default=Path('cyclops_group_validation.csv'));a=p.parse_args()
b=a.pak.read_bytes();verts=[struct.unpack_from('<3f',b,0xB278+32*i) for i in range(2158)]
blocks=[('A_shifted',0x834E,855),('A_header',0x8350,855),('B',0x8A0C,1460),('C',0xAAD5,630),('D',0xAFCF,114)]
rows=[]
for name,off,count in blocks:
 indices=struct.unpack_from('<'+str(count)+'H',b,off)
 faces=[indices[i:i+3] for i in range(0,count-2,3) if len(set(indices[i:i+3]))==3 and max(indices[i:i+3])<len(verts)]
 edges=collections.Counter(tuple(sorted((x,y))) for t in faces for x,y in ((t[0],t[1]),(t[1],t[2]),(t[2],t[0])))
 lengths=[math.dist(verts[x],verts[y]) for t in faces for x,y in ((t[0],t[1]),(t[1],t[2]),(t[2],t[0]))]
 median=statistics.median(lengths) if lengths else float('nan')
 rows.append(dict(section=name,offset=hex(off),index_count=count,triangles=len(faces),unique_vertices=len(set(v for f in faces for v in f)),nonmanifold_edges=sum(n>2 for n in edges.values()),boundary_edges=sum(n==1 for n in edges.values()),median_edge=round(median,3),max_edge=round(max(lengths),3) if lengths else '',edges_over_5x_median=sum(d>5*median for d in lengths)))
with a.out.open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
for r in rows:print(r)
print('This script evaluates candidate topology, NOT actual mesh identity.')
