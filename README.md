# Wrath PAK Viewer — experimental Android source

This is an Android Studio project, **not a compiled APK**. Open the folder in Android Studio, let Gradle sync, then use **Build > Build APK(s)**. Java 17 and Android SDK 35 are recommended.

Current capabilities: Android file picker, ASCII string browsing/search with hexadecimal offsets, signature counts, and export of the string index to CSV. Works offline. No storage permission needed (Storage Access Framework).

**Not implemented:** proprietary asset directory parsing, mesh decoding, texture decoding, 3D preview, model/texture extraction. The presence of a RIFF signature does not guarantee a valid independent WAV asset. Do not mistake string matches for a real internal file listing.

This is an early prototype. The parser has a 150 MB safety cap and may use considerable memory for large files.


## Version 2.3

Open PAK is followed by Export Everything, Models, Textures, Rigs, and
Animation Data, and Audio. Export Everything also preserves audio, metadata, and the
original PAK in its other-assets ZIP.

Rigs export joint names, parent links, stored matrices, and skeleton line
previews. Animation Data exports indexed JSON clips containing the track directory and compressed codec bytes. Audio exports original WAV files in a dedicated ZIP. The three character
samples yielded 312 joints and 203 animation records, including interface clips.
Skin weights and playable keyframes remain under research.

See [texture findings](research/TEXTURES.md) and [geometry research](research/GEOMETRY.md).

## Windows desktop app

Download the **WrathPakViewer-Windows-x64** artifact from a successful build in the Actions tab. Extract the portable ZIP and run `WrathPakViewer.exe` inside the extracted folder. The folder includes the Java runtime.

Use **Open PAK File** and select an Xbox Wrath Unleashed archive. Choose an export button and a destination ZIP. For models, extract the ZIP and open `models/primary_model.obj` with its MTL and textures in place. The Windows app shares the Android decoders and supports the same formats and archive layouts.

The build also produces a command-line JAR for Java 17 or later:

```powershell
java -jar WrathPakViewer.jar input.pak models output.zip
```

Categories are `all`, `models`, `textures`, `rigs`, `animation`, and `audio`.

## Repack ZIP to PAK (v2.5)

On Android or Windows, choose **Repack ZIP to PAK** and open an **Export
Everything** ZIP. You can also open its nested `other.zip` to restore the
original PAK. Save the result as a new file and use the original game filename
when testing it in your Xbox game files. An untouched export restores the
original bytes exactly.

For texture edits, replace the matching DDS under `textures/` inside the nested
`textures.zip`, then put that ZIP back inside the Export Everything ZIP. Keep
the exported DDS filename, dimensions, BC1/DXT1 or BC3/DXT5 format, and mip
count. Save legacy DDS without a DX10 header. The repacker writes only the
fixed-size native texture payloads and preserves the other PAK bytes. DDS
files from the models ZIP and edits to PNG, OBJ, rigs, animation, and audio
are outside this repack path. Edited textures have been verified structurally
and by re-extraction. Xbox runtime testing is still pending.

The Windows CLI also supports `java -jar WrathPakViewer.jar repack input.zip output.pak`.
