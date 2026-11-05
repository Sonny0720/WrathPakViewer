#!/usr/bin/env python3
"""Analyze 36-byte candidate records and test numerical fields as relative offsets.

Does not extract payloads. Reports evidence and range validity, including
per-field monotonicity, to help identify an actual resource index.
"""
import argparse,csv,struct
from pathlib import Path
def u32(b,p):return struct.unpack_from("<I",b,p)[0]
def inspect(path,limit):
 b=path.read_bytes()
 if len(b)<32 or u32(b,0)!=0x52049:raise ValueError("Bad PAK")
 pos=28+4*u32(b,24)+u32(b,8)
 if pos+8>len(b):raise ValueError("Bad post-index")
 cap,n=struct.unpack_from("<II",b,pos)
 if n>cap or n>(len(b)-pos-8)//2:raise ValueError("Bad ID table")
 base=pos+8+2*n
 rows=[]
 for i in range(limit):
  at=base+36*i
  if at+36>len(b):break
  words=list(struct.unpack_from("<III",b,at))
  raw=b[at+12:at+36]
  name=raw.split(b"\x00",1)[0]
  if not name or any(c<32 or c>126 for c in name):break
  rows.append(dict(archive=path.name,index=i,record_offset=f"0x{at:X}",
   name=name.decode("ascii"),word0=words[0],word1=words[1],
   word2_low16=words[2]&65535,word2_high16=words[2]>>16,
   word0_in_file=words[0]<len(b),word1_in_file=words[1]<len(b)))
 return rows
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument("paks",nargs="+",type=Path)
 p.add_argument("--out",type=Path,default=Path("candidate_record_evidence.csv"))
 p.add_argument("--limit",type=int,default=256)
 a=p.parse_args()
 if not 1<=a.limit<=10000:p.error("limit out of range")
 allrows=[]
 for pak in a.paks:
  rows=inspect(pak,a.limit);allrows+=rows
  print(f"{pak.name}: {len(rows)} consecutive printable candidate names")
  for field in ("word0","word1","word2_low16"):
   vals=[r[field] for r in rows]
   increases=sum(x<=y for x,y in zip(vals,vals[1:]))
   print(f"  {field}: {increases}/{max(0,len(vals)-1)} nondecreasing pairs")
 if allrows:
  a.out.parent.mkdir(parents=True,exist_ok=True)
  with a.out.open("w",newline="",encoding="utf-8") as f:
   w=csv.DictWriter(f,fieldnames=list(allrows[0]));w.writeheader();w.writerows(allrows)
  print("Saved",a.out)
if __name__=="__main__":main()
