#!/usr/bin/env python3
"""Compare triangle-list and triangle-strip interpretations of uint16 indices.

Reports objective topology measures. Does not claim a candidate is a real mesh.
"""
import argparse,collections,csv,struct
from pathlib import Path

def triangles(indices,mode):
    if mode=="list":
        triples=(indices[i:i+3] for i in range(0,len(indices)-2,3))
    else:
        triples=((indices[i-2],indices[i-1],indices[i]) if i%2==0
                 else (indices[i-1],indices[i-2],indices[i])
                 for i in range(2,len(indices)))
    return [tuple(t) for t in triples if len(set(t))==3]

def metrics(indices,mode):
    faces=triangles(indices,mode)
    edges=collections.Counter(tuple(sorted((a,b))) for t in faces for a,b in
                              ((t[0],t[1]),(t[1],t[2]),(t[2],t[0])))
    used=set(v for t in faces for v in t)
    return {"mode":mode,"triangles":len(faces),"used_vertices":len(used),
            "boundary_edges":sum(n==1 for n in edges.values()),
            "nonmanifold_edges":sum(n>2 for n in edges.values()),
            "max_edge_incidence":max(edges.values(),default=0)}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("pak",type=Path)
    p.add_argument("--offset",type=lambda s:int(s,0),required=True)
    p.add_argument("--index-count",type=int,required=True)
    p.add_argument("--out",type=Path,default=Path("topology_comparison.csv"))
    a=p.parse_args()
    data=a.pak.read_bytes()
    if a.offset<0 or a.index_count<3 or a.offset+2*a.index_count>len(data):
        p.error("index window out of bounds")
    values=struct.unpack_from("<"+str(a.index_count)+"H",data,a.offset)
    rows=[metrics(values,mode) for mode in ("list","strip")]
    a.out.parent.mkdir(parents=True,exist_ok=True)
    with a.out.open("w",newline="",encoding="utf-8") as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0]))
        w.writeheader();w.writerows(rows)
    for row in rows:print(row)
    print("A plausible topology is NOT sufficient to verify vertex format or mesh identity.")
if __name__=="__main__":main()
