# Wrath PAK Viewer — experimental Android source

This is an Android Studio project, **not a compiled APK**. Open the folder in Android Studio, let Gradle sync, then use **Build > Build APK(s)**. Java 17 and Android SDK 35 are recommended.

Current capabilities: Android file picker, ASCII string browsing/search with hexadecimal offsets, signature counts, and export of the string index to CSV. Works offline. No storage permission needed (Storage Access Framework).

**Not implemented:** proprietary asset directory parsing, mesh decoding, texture decoding, 3D preview, model/texture extraction. The presence of a RIFF signature does not guarantee a valid independent WAV asset. Do not mistake string matches for a real internal file listing.

This is an early prototype. The parser has a 150 MB safety cap and may use considerable memory for large files.


## Version 2.0 character geometry

The feature branch includes Xbox OBJ ZIP extraction with corrected appended-vertex
mapping, outward OBJ winding and generated normals. Open `models/primary_model.obj`
in the exported ZIP; `parts/` variants may contain attachments only.
Cyclops, Centaur and Archdemon primary models have been checked in shaded,
backface-culled views. Textures, rigging, animation, PS2 support and complete
archive coverage remain unfinished. See [findings and reproduction](research/GEOMETRY.md).
