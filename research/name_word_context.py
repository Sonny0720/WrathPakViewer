#!/usr/bin/env python3
"""Inspect null-terminated post-index names and preceding words.

No assumed fixed record size; no payload extraction or offset interpretation.
"""
import argparse,csv,struct
from pathlib import Path
def u32(b,p):return struct.unpack_from("<I",b,p)[0]
def scan(path,limit):
 b=path.read_bytes()
 if len(b)<32 or u32(b,0)!=0x52049:raise ValueError("Unrecognized PAK")
 block=28+4*u32(b,24)+u32(b,8)
 if block+8>len(b):raise ValueError("Bad post-index position")
 cap,n=struct.unpack_from("<II",b,block)
 if n>cap or n>(len(b)-block-8)//2:raise ValueError("Invalid IDs")
 start=block+8+2*n
 end=min(len(b),start+limit)
 rows=[]
 for pos in range(start,end):
  if pos>start and b[pos-1] not in (0,):continue
  q=pos
  while q<end and 32<=b[q]<=126 and q-pos<128:q+=1
  if q-pos<4 or q>=end or b[q]!=0:continue
  words=[]
  for k in range(1,7):
   at=pos-4*k
   words.append(f"0x{u32(b,at):08X}" if at>=start else "")
  rows.append([path.name,f"0x{pos:X}",pos-start,b[pos:q].decode("ascii"),*words])
 return rows
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument("paks",nargs="+",type=Path)
 p.add_argument("--out",type=Path,default=Path("name_word_context.csv"))
 p.add_argument("--limit",type=int,default=65536)
 a=p.parse_args()
 if not 1<=a.limit<=1048576:p.error("limit must be 1..1048576")
 a.out.parent.mkdir(parents=True,exist_ok=True)
 with a.out.open("w",newline="",encoding="utf-8") as f:
  w=csv.writer(f);w.writerow(["archive","offset","relative","name",*[f"word_before_{i}" for i in range(1,7)]])
  for pak in a.paks:
   rows=scan(pak,a.limit);w.writerows(rows)
   print(pak.name,len(rows),"candidate strings")
 print(a.out)
if __name__=="__main__":main()
