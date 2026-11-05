#!/usr/bin/env python3
"""Map repeated resource-name occurrences and their surrounding bytes.

Finds structural repetition without treating any hit as a mesh payload.
"""
import argparse,csv
from collections import defaultdict
from pathlib import Path

TOKENS=(b"meshes",b"animation",b"chars",b"skinmesh",b"staticmesh",b"textures")
def hits(blob,needle):
    at=0
    while True:
        pos=blob.find(needle,at)
        if pos<0:return
        yield pos
        at=pos+1

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("paks",nargs="+",type=Path)
    p.add_argument("--out",type=Path,default=Path("repeated_names.csv"))
    a=p.parse_args()
    a.out.parent.mkdir(parents=True,exist_ok=True)
    with a.out.open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f)
        w.writerow(["archive","token","offset_hex","delta_from_previous",
                    "preceding_16_bytes_hex","following_16_bytes_hex"])
        for path in a.paks:
            blob=path.read_bytes()
            for token in TOKENS:
                positions=list(hits(blob,token))
                for i,offset in enumerate(positions):
                    w.writerow([path.name,token.decode(),f"0x{offset:X}",
                        offset-positions[i-1] if i else "",
                        blob[max(0,offset-16):offset].hex(),
                        blob[offset+len(token):offset+len(token)+16].hex()])
                print(f"{path.name}: {token.decode()} = {len(positions)} occurrences")
    print("Wrote",a.out)
if __name__=="__main__":main()
