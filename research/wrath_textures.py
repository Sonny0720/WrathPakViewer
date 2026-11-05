#!/usr/bin/env python3
"""Independent native MTX record/mip parser and DDS writer (Xbox BC1/BC3)."""
import argparse
from dataclasses import dataclass
from pathlib import Path
import re
import struct

@dataclass
class Texture:
    name: str
    record: int
    format: int
    levels: list

    @property
    def stem(self):
        return f'{self.name}_{self.record:x}'


def scan(data):
    result=[]
    for match in re.finditer(rb'\.mtx',data):
        q=match.end()
        if q+4>len(data):continue
        n=struct.unpack_from('<I',data,q)[0]
        if not 1<=n<=31 or q+8+n+71>len(data):continue
        raw=data[q+4:q+4+n];r=q+8+n
        if not re.fullmatch(rb'[A-Za-z0-9_ .-]+',raw) or data[r:r+n]!=raw or data[r+n]!=0 or data[q+4+n:q+8+n]!=bytes(4):continue
        texture=Texture(raw.decode(),r,0,[])
        while r+71<=len(data) and data[r:r+n]==raw and data[r+n]==0:
            w,h=struct.unpack_from('<II',data,r+32)
            size,block,fmt,one=struct.unpack_from('<4I',data,r+55)
            if not all(0<x<=4096 and x&(x-1)==0 for x in (w,h)) or fmt not in (65,69) or block!=8 or one!=1:break
            expected=((w+3)//4)*((h+3)//4)*(8 if fmt==65 else 16)
            if size!=expected or r+71+size>len(data):break
            if texture.levels:
                pw,ph,_,_=texture.levels[-1]
                if data[r-1]!=1 or fmt!=texture.format or (w,h)!=(max(1,pw//2),max(1,ph//2)):break
            texture.format=fmt;texture.levels.append((w,h,r+71,size));r+=72+size
            if w==h==1:break
        if texture.levels:result.append(texture)
    return result


def dds(data,texture):
    w,h,_,size=texture.levels[0];count=len(texture.levels)
    header=struct.pack('<7I',124,0xA1007 if count>1 else 0x81007,h,w,size,0,count)+bytes(44)
    header+=struct.pack('<II4s5I',32,4,b'DXT1' if texture.format==65 else b'DXT5',0,0,0,0,0)
    header+=struct.pack('<5I',0x401008 if count>1 else 0x1000,0,0,0,0)
    return b'DDS '+header+b''.join(data[p:p+n] for _,_,p,n in texture.levels)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('pak',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    data=a.pak.read_bytes();a.out.mkdir(parents=True,exist_ok=True);textures=scan(data)
    for texture in textures:(a.out/(texture.stem+'.dds')).write_bytes(dds(data,texture))
    print(len(textures),'textures',sum(len(t.levels) for t in textures),'mip levels')

if __name__=='__main__':main()
