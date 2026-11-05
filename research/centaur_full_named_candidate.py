#!/usr/bin/env python3
"""Experimental full early Centaur named-section export.

Includes head, torso and eye omitted by the earlier strict six-u32 prefix
filter. These sections have valid-looking index headers but their preceding
bytes can overlap resource names. NOT a verified character extractor.
"""
import argparse,csv,struct,math,statistics,collections
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('pak',type=Path);p.add_argument('candidate_csv',type=Path)
p.add_argument('obj',type=Path);p.add_argument('audit',type=Path);a=p.parse_args()
b=a.pak.read_bytes();n=struct.unpack_from('<I',b,0x23790)[0];assert n==5268
v=[struct.unpack_from('<3f',b,0x23794+i*32) for i in range(n)]
rows=[r for r in csv.DictReader(a.candidate_csv.open()) if r['archive']==a.pak.name and r['name'].startswith('C_Centaur:C_LO_Centaur_') and int(r['header_offset'],16)<0x23790]
report=[]
with a.obj.open('w') as f:
 f.write('# Unverified Centaur candidate, triangle lists for all named early sections\n')
 for x in v:f.write('v %.7f %.7f %.7f\n'%x)
 for r in rows:
  h=int(r['header_offset'],16);m=int(r['index_count'])
  ids=struct.unpack_from(f'<{m}H',b,h+16)
  if max(ids)>=n:continue
  faces=[ids[i:i+3] for i in range(0,m-2,3) if len(set(ids[i:i+3]))==3]
  edges=[math.dist(v[x],v[y]) for t in faces for x,y in ((t[0],t[1]),(t[1],t[2]),(t[2],t[0]))]
  freq=collections.Counter(tuple(sorted((x,y))) for t in faces for x,y in ((t[0],t[1]),(t[1],t[2]),(t[2],t[0])))
  f.write('g '+r['name'].split(':')[-1]+'_'+hex(h)[2:]+'\n')
  for t in faces:f.write('f %d %d %d\n'%tuple(i+1 for i in t))
  report.append(dict(part=r['name'].split(':')[-1],offset=hex(h),subtype=r['subtype'],indices=m,triangles=len(faces),used_vertices=len(set(i for t in faces for i in t)),min_index=min(ids),max_index=max(ids),median_edge=round(statistics.median(edges),2) if edges else '',max_edge=round(max(edges),2) if edges else '',shared_edges=sum(c>1 for c in freq.values()),nonmanifold_edges=sum(c>2 for c in freq.values())))
with a.audit.open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(report[0]));w.writeheader();w.writerows(report)
print('sections',len(report),'triangles',sum(r['triangles'] for r in report))
