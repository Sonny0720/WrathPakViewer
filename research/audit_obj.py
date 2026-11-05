#!/usr/bin/env python3
"""Audit experimental OBJ topology; do not mistake a syntactically valid OBJ for a verified mesh."""
import argparse,math,statistics
from collections import Counter,defaultdict
from pathlib import Path

def load(path):
    verts=[];faces=[]
    for lineno,line in enumerate(path.read_text(encoding="utf-8").splitlines(),1):
        parts=line.split()
        if not parts or parts[0].startswith("#"):continue
        if parts[0]=="v" and len(parts)>=4:
            verts.append(tuple(float(x) for x in parts[1:4]))
        elif parts[0]=="f":
            if len(parts)!=4:raise ValueError(f"line {lineno}: only triangles supported")
            faces.append(tuple(int(x.split("/")[0])-1 for x in parts[1:]))
    if not verts or not faces:raise ValueError("no vertices or triangles")
    if any(i<0 or i>=len(verts) for face in faces for i in face):
        raise ValueError("face references nonexistent vertex")
    if any(not all(math.isfinite(c) for c in v) for v in verts):
        raise ValueError("non-finite coordinates")
    return verts,faces

def dist(a,b):return math.dist(a,b)

def audit(verts,faces):
    used=set(i for f in faces for i in f)
    lengths=[dist(verts[a],verts[b]) for f in faces for a,b in
             ((f[0],f[1]),(f[1],f[2]),(f[2],f[0]))]
    edges=Counter(tuple(sorted((a,b))) for f in faces for a,b in
                  ((f[0],f[1]),(f[1],f[2]),(f[2],f[0])))
    parents={v:v for v in used}
    def root(v):
        while parents[v]!=v:
            parents[v]=parents[parents[v]]
            v=parents[v]
        return v
    for a,b in edges:parents[root(a)]=root(b)
    components=Counter(root(i) for i in used)
    median=statistics.median(lengths)
    long_faces=sum(any(dist(verts[a],verts[b])>5*median for a,b in
                       ((f[0],f[1]),(f[1],f[2]),(f[2],f[0]))) for f in faces)
    print("vertices:",len(verts),"referenced:",len(used),"triangles:",len(faces))
    print("connected components:",len(components),"largest component:",max(components.values()))
    print("boundary edges:",sum(n==1 for n in edges.values()),
          "nonmanifold edges:",sum(n>2 for n in edges.values()))
    print("median edge length:",round(median,4),
          "faces with edge > 5x median:",long_faces)
    print("WARNING: this measures geometry consistency, not whether the model matches the game.")

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("obj",type=Path)
    a=p.parse_args()
    audit(*load(a.obj))
if __name__=="__main__":main()
