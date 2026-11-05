#!/usr/bin/env python3
"""Summarize named index-section candidates without equating sections to meshes."""
import argparse,csv,collections
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('input',type=Path);p.add_argument('--out',type=Path,default=Path('wrath_resource_candidate_summary.csv'));a=p.parse_args()
rows=list(csv.DictReader(a.input.open(newline='',encoding='utf-8')))
groups=collections.defaultdict(list)
for r in rows:groups[(r['archive'],r['name'])].append(r)
output=[]
for (archive,name),g in sorted(groups.items()):
 offsets=sorted(int(r['full_header_offset'],16) for r in g)
 output.append(dict(archive=archive,resource=name,section_count=len(g),index_count_total=sum(int(r['index_count']) for r in g),min_index=min(int(r['min_index']) for r in g),max_index=max(int(r['max_index']) for r in g),subtypes=','.join(sorted(set(r['subtype'] for r in g))),first_header=hex(offsets[0]),last_header=hex(offsets[-1]),span_bytes=offsets[-1]-offsets[0],duplicate_adjacent_total=sum(int(r['adjacent_duplicates']) for r in g)))
with a.out.open('w',newline='',encoding='utf-8') as f:
 w=csv.DictWriter(f,fieldnames=list(output[0]));w.writeheader();w.writerows(output)
print('Sections:',len(rows),'Distinct archive/resource pairs:',len(output))
for archive in sorted(set(x['archive'] for x in output)):
 g=[x for x in output if x['archive']==archive]
 print(archive,'resources',len(g),'sections',sum(x['section_count'] for x in g))
