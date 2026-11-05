#!/usr/bin/env python3
"""Experimental named Centaur geometry export; NOT a validated model decoder."""
import argparse,csv,struct,math
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('pak',type=Path);p.add_argument('verified_csv',type=Path)
p.add_argument('obj',type=Path);p.add_argument('audit_csv',type=Path)
a=p.parse_args()
b=a.pak.read_bytes()
count=struct.unpack_from('<I',b,0x23790)[0]
assert count==5268
vertices=[struct.unpack_from('<3f',b,0x23794+i*32) for i in range(count)]
rows=list(csv.DictReader(a.verified_csv.open()))
selected=[r for r in rows if r['archive']==a.pak.name and r['name'].startswith('C_Centaur:C_LO_Centaur_') and int(r['header_offset'],16)<0x23790 and r['name'].split('Centaur_')[-1].split('_V')[0] in ('Body','Horn','Armor','Hair')]
audits=[]
with a.obj.open('w') as out:
 out.write('# Unverified Centaur geometry hypothesis\n')
 for v in vertices:out.write('v %.7f %.7f %.7f\n'%v)
 for r in selected:
  h=int(r['header_offset'],16);n=int(r['index_count']);sub=int(r['subtype'])
  indices=struct.unpack_from(f'<{n}H',b,h+16)
  if sub in (2,4):
   faces=[indices[i:i+3] for i in range(0,n-2,3)]
  else:
   faces=[(indices[i],indices[i+1],indices[i+2]) if i%2==0 else (indices[i+1],indices[i],indices[i+2]) for i in range(n-2)]
  faces=[f for f in faces if len(set(f))==3 and max(f)<count]
  label=r['name'].split(':')[-1]
  out.write('g '+label+'\n')
  for f in faces:out.write('f %d %d %d\n'%tuple(i+1 for i in f))
  edges=sorted(math.dist(vertices[i],vertices[j]) for f in faces for i,j in ((f[0],f[1]),(f[1],f[2]),(f[2],f[0])))
  audits.append(dict(part=label,header=hex(h),subtype=sub,index_count=n,faces=len(faces),used_vertices=len(set(i for f in faces for i in f)),median_edge=edges[len(edges)//2] if edges else '',max_edge=edges[-1] if edges else '',p95_edge=edges[int(.95*(len(edges)-1))] if edges else ''))
with a.audit_csv.open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(audits[0]));w.writeheader();w.writerows(audits)
print('Exported',sum(x['faces'] for x in audits),'faces across',len(audits),'sections; visually unverified')
