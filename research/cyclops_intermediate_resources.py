#!/usr/bin/env python3
"""Report named resource strings and high-index uint16 values near Cyclops vertex data."""
import argparse,re,struct
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("pak",type=Path)
a=p.parse_args()
data=a.pak.read_bytes()
lo,hi=0x9574,0xB278
if len(data)<hi:raise SystemExit("archive too short")
print("ASCII names in intermediate section:")
for m in re.finditer(rb"[ -~]{6,}",data[lo:hi]):
 print(f"0x{lo+m.start():X}: {m.group().decode('ascii')}")
print("16 uint16 values at 0xB230:",struct.unpack_from("<16H",data,0xB230))
print("Candidate vertex count at 0xB274:",struct.unpack_from("<I",data,0xB274)[0])
print("Resource names and index proximity do not prove which mesh owns which buffers.")
