#!/usr/bin/env python3
"""Scan length-prefixed named resources for candidate index headers.

Preserves the two preceding 32-bit fields. Matches are candidates, not verified meshes.
"""
import argparse,csv,struct
from pathlib import Path
def scan(path):
 b=path.read_bytes()
 for pos in range(len(b)-64):
  n=struct.unpack_from('<I',b,pos)[0]
  if not 15<=n<=100 or pos+4+n+24>len(b):continue
  name=b[pos+4:pos+4+n]
  if b':' not in name or not all(32<=c<127 for c in name):continue
  for pad in (0,4,8,12):
   h=pos+4+n+pad
   if h+16>len(b):continue
   typ,sub,cap,count=struct.unpack_from('<4I',b,h)
   if typ!=4 or sub not in (1,2,4) or not 3<=count<=cap<=20000 or h+16+2*count>len(b):continue
   prefix=struct.unpack_from('<2I',b,h-8) if h>=8 else (None,None)
   yield dict(archive=path.name,name=name.decode(),name_offset=hex(pos),header_offset=hex(h),name_padding=pad,prefix0=prefix[0],prefix1=prefix[1],type=typ,subtype=sub,capacity=cap,index_count=count)
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('paks',type=Path,nargs='+')
 p.add_argument('--out',type=Path,default=Path('wrath_named_header_24byte_audit.csv'))
 a=p.parse_args();rows=[r for path in a.paks for r in scan(path)]
 if not rows:raise SystemExit('No candidates')
 with a.out.open('w',newline='',encoding='utf-8') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
 print('Named structural matches:',len(rows),'saved:',a.out)
if __name__=='__main__':main()
