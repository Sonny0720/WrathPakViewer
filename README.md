# Wrath PAK Viewer — experimental Android source

This is an Android Studio project, **not a compiled APK**. Open the folder in Android Studio, let Gradle sync, then use **Build > Build APK(s)**. Java 17 and Android SDK 35 are recommended.

Current capabilities: Android file picker, ASCII string browsing/search with hexadecimal offsets, signature counts, and export of the string index to CSV. Works offline. No storage permission needed (Storage Access Framework).

**Not implemented:** proprietary asset directory parsing, mesh decoding, texture decoding, 3D preview, model/texture extraction. The presence of a RIFF signature does not guarantee a valid independent WAV asset. Do not mistake string matches for a real internal file listing.

This is an early prototype. The parser has a 150 MB safety cap and may use considerable memory for large files.


## Version 2.2

Open PAK is followed by Export Everything, Models, Textures, Rigs, and
Animation (Raw). Export Everything also preserves audio, metadata, and the
original PAK in its other-assets ZIP.

Rigs export joint names, parent links, stored matrices, and skeleton line
previews. Animation exports indexed raw clip records. The three character
samples yielded 312 joints and 203 animation records, including interface clips.
Skin weights and playable keyframes remain under research.

See [texture findings](research/TEXTURES.md) and [geometry research](research/GEOMETRY.md).
