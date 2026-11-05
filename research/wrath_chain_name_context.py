#!/usr/bin/env python3
"""Annotate adjoining candidate index sections with nearby resource-like names.

Proximity is evidence for investigation, not verified section ownership.
"""
import argparse,csv,re
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('chain_csv',type=Path)
p.add_argument('pak_dir',type=Path)
p.add_argument('--out',type=Path,default=Path('wrath_chain_name_context.csv'))
a=p.parse_args()
rows=list(csv.DictReader(a.chain_csv.open(newline='',encoding='utf-8')))
archives={}
for name in sorted({r['archive'] for r in rows}):
 b=(a.pak_dir/name).read_bytes()
 names=[]
 for m in re.finditer(rb'[\x20-\x7e]{8,}',b):
  s=m.group().decode('ascii')
  if (':' in s or '_' in s) and not s.startswith('http'):
   names.append((m.start(),s[:120]))
 archives[name]=names
out=[]
for r in rows:
 offset=int(r['offset'],16)
 near=sorted(((abs(offset-pos),pos,name) for pos,name in archives[r['archive']] if abs(offset-pos)<=2048),key=lambda t:t[0])[:3]
 out.append({**r,'nearest_name_offset':hex(near[0][1]) if near else '',
 'nearest_name_distance':near[0][0] if near else '',
 'nearest_name':near[0][2] if near else '',
 'other_nearby_names':' | '.join(f'{hex(pos)}:{name}' for _,pos,name in near[1:])})
with a.out.open('w',newline='',encoding='utf-8') as f:
 w=csv.DictWriter(f,fieldnames=list(out[0]));w.writeheader();w.writerows(out)
print('Annotated',len(out),'rows; names within 2048 bytes:',sum(bool(r['nearest_name']) for r in out))
