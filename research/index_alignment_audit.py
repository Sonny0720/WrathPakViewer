#!/usr/bin/env python3
"""Rank 16-bit triangle-list alignments using topology, not appearance."""
import argparse,collections,struct
from pathlib import Path

def audit(data,offset,index_count):
    values=struct.unpack_from(f'<{index_count}H',data,offset)
    faces=[values[i:i+3] for i in range(0,index_count-2,3) if len(set(values[i:i+3]))==3]
    edges=collections.Counter(tuple(sorted(edge)) for a,b,c in faces for edge in ((a,b),(b,c),(c,a)))
    return len(faces),sum(n==2 for n in edges.values()),sum(n>2 for n in edges.values()),len(set(values))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('pak',type=Path)
    p.add_argument('--offset',type=lambda x:int(x,0),default=0x8400)
    p.add_argument('--index-count',type=int,default=384)
    a=p.parse_args()
    b=a.pak.read_bytes()
    for shift in range(-12,13,2):
        off=a.offset+shift
        if off<0 or off+2*a.index_count>len(b):continue
        n,shared,nonmanifold,unique=audit(b,off,a.index_count)
        print(f'0x{off:06X} triangles={n:3} shared_edges={shared:3} nonmanifold={nonmanifold:2} unique_indices={unique:3}')
    print('A strong six-byte phase is evidence for triangle lists, not proof of mesh identity.')
if __name__=='__main__':main()
