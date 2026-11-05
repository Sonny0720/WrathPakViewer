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
Exports preserve the complete byte range starting at the header. The
secondary length is retained without assigning it a track or frame meaning.

| Archive | Joint count | Raw clip records |
| --- | ---: | ---: |
| Cyclops | 98 | 66 |
| Centaur | 88 | 68 |
| Archdemon | 126 | 69 |

Clip totals include interface records. Remaining work is locating the track
headers and interpreting time, rotation, and translation keys, then testing
the decoded motion against the recovered skeleton and skin weights.
