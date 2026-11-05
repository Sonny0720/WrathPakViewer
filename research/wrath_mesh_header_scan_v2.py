#!/usr/bin/env python3
"""Scan all byte alignments for candidate Wrath mesh index-section headers.

Matches are structural candidates, not verified meshes.
"""
import argparse,csv,struct,collections
from pathlib import Path

def scan(path):
 b=path.read_bytes();off=0
 while True:
  off=b.find(b'\x04\x00\x00\x00',off)
  if off<0:break
  if off+16<=len(b):
   _,subtype,capacity,count=struct.unpack_from('<4I',b,off)
   if subtype in (1,2,4) and 12<=count<=12000 and count<=capacity<=20000 and off+16+2*count<=len(b):
    values=struct.unpack_from(f'<{count}H',b,off+16)
    if max(values)<=20000:
     yield (path.name,hex(off),subtype,capacity,count,min(values),max(values))
  off+=1

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('paks',type=Path,nargs='+')
 p.add_argument('--out',type=Path,default=Path('wrath_mesh_header_scan_v2.csv'))
 a=p.parse_args()
 rows=[]
 for pak in a.paks:
  hits=list(scan(pak));rows.extend(hits)
  print(pak.name, 'candidates',len(hits),'odd offsets',sum(int(r[1],16)%2 for r in hits),'subtypes',dict(collections.Counter(r[2] for r in hits)))
 with a.out.open('w',newline='',encoding='utf-8') as f:
  w=csv.writer(f);w.writerow(['archive','header_offset_hex','subtype','capacity','index_count','min_index','max_index']);w.writerows(rows)
 print('Total candidate sections:',len(rows),'CSV:',a.out)
 print('WARNING: these matches are NOT confirmed meshes.')
if __name__=='__main__':main()
