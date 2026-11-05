# Xbox character rigs and animation records

The three sample PAKs each have a multi-joint table after the mesh data.
The table starts after two little-endian 32-bit values giving capacity and
joint count. Each joint uses 82 bytes: a zero-padded 32-byte name, nine
float32 rotation values, three float32 translation values, and a signed
16-bit parent index. The root uses -1. Other parents precede their children.
The stored bases are orthonormal in the tested tables. The skeleton OBJ uses
the inferred inverse transform convention, computing each position as -R*t
with R formed from the nine stored floats in row order. The CSV preserves
the original values for testing other conventions. Skinning is pending.

Animation paths have a 16-byte little-endian header immediately before the
path: total record size, a secondary length, zero, and path byte length.
The observed relation is total size = secondary length + path length + 32.
The path is followed by eight bytes and a zero-padded 32-byte clip name.
For contiguous clips, the total size lands exactly on the next header.
The clip index preserves the complete source range and secondary length.
Export Everything keeps the original PAK for source comparisons.

| Archive | Joint count | JSON clip files |
| --- | ---: | ---: |
| Cyclops | 98 | 66 |
| Centaur | 88 | 68 |
| Archdemon | 126 | 69 |

Clip totals include interface records. Remaining work is locating the track
headers and interpreting time, rotation, and translation keys, then testing
the decoded motion against the recovered skeleton and skin weights.

## Packed clip directory

Relative to the end of the outer 32-byte clip name, the inner header begins
at byte 256. The value at 260 is 303185 in the three samples. The length word
at 264 gives the size of the packed region starting at that word and ending
eight bytes before the next record. The inner name at 268 matches the outer
name. A float at 256 repeats at 300. Its timing units are unconfirmed.
The low 16 bits at 308 give the track count. The directory at 416 starts with
a sentinel of 0xffffffff, followed by one relative offset per track. Offsets
are relative to byte 264. Each track starts with its own byte length.

The JSON contains the track offsets, lengths, and packed region in Base64.
Track-to-joint mapping, time units and keyframe compression still need
verification before export to a playable animation format.
