#!/usr/bin/env python3
"""Find length-prefixed resource names directly followed by candidate mesh headers."""
import argparse,csv,struct
from pathlib import Path

def scan(path):
 b=path.read_bytes()
 for off in range(len(b)-64):
  n=struct.unpack_from('<I',b,off)[0]
  if not 20<=n<=60:continue
  name=b[off+4:off+4+n]
  if b':' not in name or not all(32<=v<127 for v in name):continue
  for padding in (0,4,8,12):
   h=off+4+n+padding
   if h+16>len(b):continue
   typ,sub,capacity,count=struct.unpack_from('<4I',b,h)
   if typ==4 and sub in (1,2,4) and 0<count<=capacity<=20000 and h+16+2*count<=len(b):
    yield (path.name,hex(off),name.decode('ascii'),padding,hex(h),sub,capacity,count)

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('paks',nargs='+',type=Path)
 p.add_argument('--out',type=Path,default=Path('wrath_named_mesh_header_matches.csv'))
 a=p.parse_args()
 rows=[row for pak in a.paks for row in scan(pak)]
 with a.out.open('w',newline='',encoding='utf-8') as f:
  w=csv.writer(f)
  w.writerow(['archive','length_offset','resource_name','padding','header_offset','subtype','capacity','index_count'])
  w.writerows(rows)
 print('Named structural matches:',len(rows),'output:',a.out)
 print('CAUTION: these are candidate sections, not confirmed complete meshes.')
if __name__=='__main__':main()
