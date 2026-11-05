#!/usr/bin/env python3
"""Audit recurring 16-byte index section headers in Cyclops PAK.

Header hypothesis: four uint32 words, final word is the count of following
uint16 indices. The meaning of the other fields is not established.
"""
import argparse,struct
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('pak',type=Path);a=p.parse_args()
b=a.pak.read_bytes()
for name,head in [('B',0x89FC),('C',0xAAC5),('D',0xAFC1)]:
    kind,subtype,capacity,count=struct.unpack_from('<4I',b,head)
    start=head+16;end=start+count*2
    if end>len(b):raise ValueError(f'{name}: index section exceeds PAK')
    values=struct.unpack_from('<'+str(count)+'H',b,start)
    print(f'{name}: header=0x{head:X} type={kind} subtype={subtype} capacity={capacity} count={count} indices=0x{start:X}..0x{end:X} min={min(values)} max={max(values)} unique={len(set(values))}')
    print('  next bytes:',b[end:end+16].hex(' '))
print('Fields named type/subtype/capacity are hypotheses, not confirmed semantics.')
