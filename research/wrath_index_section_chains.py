#!/usr/bin/env python3
"""Find exactly adjoining candidate index-section headers from a scanner CSV.

Adjacency is stronger structural evidence than isolated matches, not proof of
mesh identity or triangle topology.
"""
import argparse,csv,collections
from pathlib import Path

def chains(rows):
    by_off={int(r['header_offset']):r for r in rows}
    next_of={}
    previous=set()
    for off,r in by_off.items():
        end=off+16+2*int(r['index_count'])
        if end in by_off:
            next_of[off]=end
            previous.add(end)
    result=[]
    for off in sorted(by_off):
        if off in previous:continue
        chain=[off]
        while chain[-1] in next_of:
            chain.append(next_of[chain[-1]])
        if len(chain)>1:result.append(chain)
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('input',type=Path)
    p.add_argument('--out',type=Path,default=Path('wrath_index_section_chains.csv'))
    a=p.parse_args()
    rows=list(csv.DictReader(a.input.open(newline='')))
    groups=collections.defaultdict(list)
    for r in rows:groups[r['archive']].append(r)
    output=[]
    for archive,group in sorted(groups.items()):
        cs=chains(group)
        print(f'{archive}: {len(group)} candidates, {len(cs)} adjoining chains, {sum(len(c)-1 for c in cs)} exact links')
        by_off={int(r['header_offset']):r for r in group}
        for j,chain in enumerate(cs,1):
            for k,off in enumerate(chain,1):
                r=by_off[off]
                output.append(dict(archive=archive,chain=j,position=k,chain_length=len(chain),offset=hex(off),subtype=r['subtype'],count=r['index_count'],min_index=r['min_index'],max_index=r['max_index']))
    with a.out.open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=['archive','chain','position','chain_length','offset','subtype','count','min_index','max_index'])
        w.writeheader();w.writerows(output)
    print('Saved',a.out)
if __name__=='__main__':main()
