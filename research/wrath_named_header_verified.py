#!/usr/bin/env python3
"""Validate six-u32 named index headers; structural evidence, not verified meshes."""
import argparse,csv,struct
from pathlib import Path
from collections import Counter
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('input',type=Path)
 p.add_argument('--pak-dir',type=Path,default=Path('.'))
 p.add_argument('--out',type=Path,default=Path('wrath_named_header_verified.csv'))
 a=p.parse_args();rows=list(csv.DictReader(a.input.open(newline='')))
 cache={};out=[]
 for r in rows:
  name=r['archive']
  if name not in cache:cache[name]=(a.pak_dir/name).read_bytes()
  b=cache[name];h=int(r['header_offset'],16)
  if h<8 or h+16+2*int(r['index_count'])>len(b):continue
  header=struct.unpack_from('<6I',b,h-8)
  if header[:3]!=(4,0,4):continue
  indices=struct.unpack_from(f'<{header[5]}H',b,h+16)
  out.append({**r,'full_header_offset':hex(h-8),'min_index':min(indices),'max_index':max(indices),'adjacent_duplicates':sum(x==y for x,y in zip(indices,indices[1:]))})
 if not out:raise SystemExit('No matches')
 with a.out.open('w',newline='',encoding='utf-8') as f:
  w=csv.DictWriter(f,fieldnames=list(out[0]));w.writeheader();w.writerows(out)
 print('Six-value matches',len(out),'/',len(rows))
 for name in sorted(set(r['archive'] for r in out)):
  group=[r for r in out if r['archive']==name]
  print(name,len(group),'names',len(set(r['name'] for r in group)),'subtypes',dict(Counter(r['subtype'] for r in group)))
if __name__=='__main__':main()
