package com.wrath.pakviewer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.*;

/** Restore the original Xbox PAK and optionally replace validated MTX payload bytes. */
public final class WrathRepack {
    private static final int MAX_PAK = 150_000_000;
    private static final int DDS_HEADER = 128;

    private WrathRepack() {}

    public static final class Result {
        public final String name;
        public final byte[] data;
        public final int changedTextures;

        private Result(String name, byte[] data, int changedTextures) {
            this.name = name;
            this.data = data;
            this.changedTextures = changedTextures;
        }
    }

    public static Result read(Path source) throws IOException {
        try (ZipFile outer = new ZipFile(source.toFile())) {
            ZipEntry other = unique(outer, "other.zip");
            ZipEntry textures = unique(outer, "textures.zip");
            try (ZipInputStream originals = new ZipInputStream(other == null
                    ? Files.newInputStream(source) : outer.getInputStream(other))) {
                String name = null;
                byte[] data = null;
                for (ZipEntry entry; (entry = originals.getNextEntry()) != null; ) {
                    String path = entry.getName();
                    if (!path.matches("original/[A-Za-z0-9._-]+[.]([pP][aA][kK])")) continue;
                    if (data != null) throw new IOException("ZIP contains more than one original PAK.");
                    name = path.substring("original/".length());
                    data = readLimited(originals, MAX_PAK);
                }
                if (data == null || data.length < 28 || WrathTextures.u32(data, 0) != 0x52049) {
                    throw new IOException("Use Export Everything or its other.zip containing the original Xbox PAK.");
                }
                int changed = 0;
                if (other != null && textures != null) {
                    try (ZipInputStream edits = new ZipInputStream(outer.getInputStream(textures))) {
                        changed = replaceTextures(data, edits);
                    }
                }
                return new Result(name, data, changed);
            }
        }
    }

    private static ZipEntry unique(ZipFile zip, String name) throws IOException {
        ZipEntry found = null;
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = entries.nextElement();
            if (!entry.getName().equals(name)) continue;
            if (found != null) throw new IOException("Duplicate ZIP entry: " + name);
            found = entry;
        }
        return found;
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[65536];
        for (int size; (size = in.read(buffer)) != -1; ) {
            if (size > limit - out.size()) throw new IOException("ZIP entry exceeds its supported size.");
            out.write(buffer, 0, size);
        }
        return out.toByteArray();
    }

    private static int replaceTextures(byte[] data, ZipInputStream edits) throws IOException {
        Map<String, WrathTextures.Texture> targets = new HashMap<>();
        for (WrathTextures.Texture texture : WrathTextures.find(data)) {
            targets.put("textures/" + texture.stem() + ".dds", texture);
        }
        int changed = 0;
        Set<String> seen = new HashSet<>();
        for (ZipEntry entry; (entry = edits.getNextEntry()) != null; ) {
            String path = entry.getName();
            if (!path.toLowerCase(Locale.ROOT).endsWith(".dds")) continue;
            WrathTextures.Texture texture = targets.get(path);
            if (texture == null || !seen.add(path)) throw new IOException("Unexpected or duplicate DDS: " + path);

            int expected = DDS_HEADER;
            for (WrathTextures.Level level : texture.levels) expected += level.size;
            byte[] dds = readLimited(edits, expected);
            WrathTextures.Level top = texture.levels.get(0);
            long mipCount = dds.length >= DDS_HEADER ? WrathTextures.u32(dds, 28) : 0;
            if (dds.length != expected || !WrathTextures.has(dds, 0, "DDS ")
                    || WrathTextures.u32(dds, 4) != 124 || WrathTextures.u32(dds, 76) != 32
                    || (WrathTextures.u32(dds, 80) & 4) == 0
                    || WrathTextures.u32(dds, 12) != top.height || WrathTextures.u32(dds, 16) != top.width
                    || WrathTextures.u32(dds, 24) > 1 || WrathTextures.u32(dds, 112) != 0
                    || Math.max(1, mipCount) != texture.levels.size()
                    || !WrathTextures.has(dds, 84, texture.format == 65 ? "DXT1" : "DXT5")) {
                throw new IOException("DDS must match the original dimensions, DXT format and mip count: " + path);
            }
            boolean modified = false;
            int offset = DDS_HEADER;
            for (WrathTextures.Level level : texture.levels) {
                for (int i = 0; !modified && i < level.size; i++) {
                    modified = dds[offset + i] != data[level.offset + i];
                }
                System.arraycopy(dds, offset, data, level.offset, level.size);
                offset += level.size;
            }
            if (modified) changed++;
        }
        return changed;
    }
}
