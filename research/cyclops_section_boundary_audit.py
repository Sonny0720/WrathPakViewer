#!/usr/bin/env python3
"""Reproduce verified byte boundaries in a Cyclops PAK.

This script does not assume the index sections belong to the same mesh.
"""
import argparse,struct
from pathlib import Path

def main():
    p=argparse.ArgumentParser()
    p.add_argument('pak',type=Path)
    a=p.parse_args()
    b=a.pak.read_bytes()
    u32=lambda offset:struct.unpack_from('<I',b,offset)[0]
    for label,start,count in [('A',0x8350,855),('B',0x8A0C,1460)]:
        values=struct.unpack_from(f'<{count}H',b,start)
        end=start+count*2
        print(f'{label}: start=0x{start:X} end=0x{end:X} count={count} min={min(values)} max={max(values)} unique={len(set(values))}')
        print('  first 12:',values[:12])
        print('  last 12:',values[-12:])
        print('  next 16 bytes:',b[end:end+16].hex(' '))
    vertex_count=u32(0xB274)
    vertex_start=0xB278
    vertex_end=vertex_start+vertex_count*32
    print('vertex count:',vertex_count,'stride:',32,'start:',hex(vertex_start),'end:',hex(vertex_end))
    print('post-block repeated counts:',u32(0x1C048),u32(0x1C04C))
    assert vertex_count==2158
    assert vertex_end==0x1C038
    assert u32(0x1C048)==vertex_count==u32(0x1C04C)
    print('Byte-boundary checks PASS. Mesh identity and topology remain UNVERIFIED.')
if __name__=='__main__':main()
