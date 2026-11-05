#!/usr/bin/env python3
"""Compare Centaur triangle list and strip geometry; export corrected OBJ.

Research hypothesis, not a confirmed game format or complete character mesh.
"""
import argparse,csv,struct,math
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('pak',type=Path);p.add_argument('headers',type=Path);p.add_argument('obj',type=Path);p.add_argument('audit',type=Path);a=p.parse_args()
b=a.pak.read_bytes();count=struct.unpack_from('<I',b,0x23790)[0];assert count==5268
verts=[struct.unpack_from('<3f',b,0x23794+i*32) for i in range(count)]
rows=list(csv.DictReader(a.headers.open()))
selected=[r for r in rows if r['archive']==a.pak.name and r['name'].startswith('C_Centaur:C_LO_Centaur_') and int(r['header_offset'],16)<0x23790 and r['name'].split('Centaur_')[-1].split('_V')[0] in ('Body','Horn','Armor','Hair')]
audit=[]
with a.obj.open('w') as out:
 out.write('# Experimental Centaur candidate: triangle lists for all selected sections\n')
 for v in verts:out.write('v %.7f %.7f %.7f\n'%v)
 for r in selected:
  h=int(r['header_offset'],16);n=int(r['index_count']);ids=struct.unpack_from(f'<{n}H',b,h+16)
  if max(ids)>=count:continue
  out.write('g '+r['name'].split(':')[-1]+'_'+hex(h)[2:]+'\n')
  for mode in ('list','strip'):
   faces=[ids[i:i+3] for i in (range(0,n-2,3) if mode=='list' else range(n-2))]
   faces=[f for f in faces if len(set(f))==3]
   edges=sorted(math.dist(verts[x],verts[y]) for f in faces for x,y in ((f[0],f[1]),(f[1],f[2]),(f[2],f[0])))
   audit.append(dict(part=r['name'].split(':')[-1],header=hex(h),subtype=r['subtype'],mode=mode,faces=len(faces),max_edge=round(edges[-1],3) if edges else '',p95_edge=round(edges[int(.95*(len(edges)-1))],3) if edges else ''))
   if mode=='list':
    for f in faces:out.write('f %d %d %d\n'%tuple(i+1 for i in f)
with a.audit.open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(audit[0]));w.writeheader();w.writerows(audit)
print('Saved',a.obj,a.audit,'list faces',sum(r['faces'] for r in audit if r['mode']=='list'))
