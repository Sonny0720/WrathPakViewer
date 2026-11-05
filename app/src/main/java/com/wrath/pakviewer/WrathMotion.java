package com.wrath.pakviewer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.ZipOutputStream;

final class WrathMotion {
    private static final int JOINT_SIZE = 82;
    private static final String ANIMATION_PREFIX = "animation\\";
    private static final String ANIMATION_SUFFIX = ".boneanim";

    private WrathMotion() {}

    private static float value(byte[] data, int offset) {
        return Float.intBitsToFloat((int) WrathTextures.u32(data, offset));
    }

    private static int parent(byte[] data, int joint) {
        return (short) ((data[joint + 80] & 255) | ((data[joint + 81] & 255) << 8));
    }

    private static String jointName(byte[] data, int offset) {
        int length = 0;
        while (length < 32 && data[offset + length] != 0) {
            int ch = data[offset + length] & 255;
            if (ch < 32 || ch > 126) return "";
            length++;
        }
        if (length == 32) return "";
        for (int i = length; i < 32; i++) {
            if (data[offset + i] != 0) return "";
        }
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static boolean validJoint(byte[] data, int offset, int index) {
        int parent = parent(data, offset);
        if ((index == 0 ? parent != -1 : parent < 0 || parent >= index)
                || jointName(data, offset).isEmpty()) return false;
        float[] transform = new float[12];
        for (int i = 0; i < transform.length; i++) {
            transform[i] = value(data, offset + 32 + i * 4);
            if (!Float.isFinite(transform[i])) return false;
        }
        for (int row = 0; row < 3; row++) {
            for (int other = row; other < 3; other++) {
                double dot = 0;
                for (int k = 0; k < 3; k++) dot += transform[row * 3 + k] * transform[other * 3 + k];
                if (Math.abs(dot - (row == other ? 1 : 0)) > 0.001) return false;
            }
        }
        return true;
    }

    static void rigs(byte[] data, ZipOutputStream zip) throws IOException {
        int found = 0;
        for (int offset = 8; offset + JOINT_SIZE <= data.length; offset++) {
            if (!WrathTextures.has(data, offset, "root\0") && !WrathTextures.has(data, offset, "C_Root\0")) continue;
            long capacity = WrathTextures.u32(data, offset - 8);
            long count = WrathTextures.u32(data, offset - 4);
            // Limit this pass to multi-joint character tables. Static props often have a single root.
            if (count < 2 || count > capacity || capacity > 512 || offset + count * JOINT_SIZE > data.length) continue;
            int joint = 0;
            while (joint < count && validJoint(data, offset + joint * JOINT_SIZE, joint)) joint++;
            if (joint != count) continue;

            StringBuilder csv = new StringBuilder("joint,parent,name,r00,r01,r02,r10,r11,r12,r20,r21,r22,tx,ty,tz\n");
            StringBuilder obj = new StringBuilder("# Joint positions inferred from the stored inverse transforms\n");
            StringBuilder lines = new StringBuilder();
            for (int i = 0; i < count; i++) {
                int record = offset + i * JOINT_SIZE;
                int parent = parent(data, record);
                csv.append(i).append(',').append(parent).append(",\"")
                    .append(jointName(data, record).replace("\"", "\"\"")).append('"');
                for (int f = 0; f < 12; f++) csv.append(',').append(value(data, record + 32 + f * 4));
                csv.append('\n');
                obj.append('v');
                for (int row = 0; row < 3; row++) {
                    double coordinate = 0;
                    for (int k = 0; k < 3; k++) {
                        coordinate -= value(data, record + 32 + (row * 3 + k) * 4)
                            * value(data, record + 68 + k * 4);
                    }
                    obj.append(' ').append(coordinate);
                }
                obj.append('\n');
                if (parent >= 0) lines.append("l ").append(parent + 1).append(' ').append(i + 1).append('\n');
            }
            String stem = "rig_" + Integer.toHexString(offset);
            WrathExports.text(zip, stem + ".csv", csv.toString());
            WrathExports.text(zip, stem + ".obj", obj.append(lines).toString());
            found++;
            offset += (int) count * JOINT_SIZE - 1;
        }
        WrathExports.text(zip, "README.txt", "Character skeleton tables found: " + found
            + "\nCSV contains the stored matrices and joint hierarchy. OBJ is a line preview using inferred joint positions."
            + "\nMesh weights and an importable skinned rig are still under research.\n");
    }

    static void animations(byte[] data, ZipOutputStream zip) throws IOException {
        StringBuilder index = new StringBuilder("source_path,record_offset,record_bytes,length_field,file\n");
        for (int pathOffset = 16; pathOffset + ANIMATION_PREFIX.length() <= data.length; pathOffset++) {
            if (!WrathTextures.has(data, pathOffset, ANIMATION_PREFIX)) continue;
            int start = pathOffset - 16;
            long size = WrathTextures.u32(data, start);
            long lengthField = WrathTextures.u32(data, start + 4);
            long pathLength = WrathTextures.u32(data, start + 12);
            if (pathLength < ANIMATION_PREFIX.length() + ANIMATION_SUFFIX.length()
                    || pathLength > 260 || WrathTextures.u32(data, start + 8) != 0
                    || size != lengthField + pathLength + 32 || lengthField < 24
                    || start + size > data.length) continue;
            String path = new String(data, pathOffset, (int) pathLength, StandardCharsets.US_ASCII);
            if (!path.endsWith(ANIMATION_SUFFIX) || !path.matches("[A-Za-z0-9_\\\\.-]+")) continue;
            String name = path.substring(path.lastIndexOf('\\') + 1);
            int nameOffset = pathOffset + (int) pathLength + 8;
            String clip = name.substring(0, name.length() - ANIMATION_SUFFIX.length());
            if (!clip.equalsIgnoreCase(jointName(data, nameOffset))) continue;
            int end = start + (int) size;
            String entry = "clips/" + Integer.toHexString(start) + "_" + clip + ".json";
            String json = clipJson(data, nameOffset + 32, end, clip);
            if (json != null) {
                WrathExports.text(zip, entry, json);
            } else {
                entry = "";
            }
            index.append(path).append(',').append(start).append(',').append(size).append(',')
                .append(lengthField).append(',').append(entry).append('\n');
            pathOffset = start + (int) size - 1;
        }
        WrathExports.text(zip, "clips.csv", index.toString());
        WrathExports.text(zip, "README.txt", "Keyframe decoding is not implemented.\n"
            + "JSON clips contain a validated track directory and packed codec bytes in Base64.\n"
            + "These research files require a codec decoder for playback.\n"
            + "An empty file column in clips.csv indicates an unsupported clip layout.\n");
    }

    private static String clipJson(byte[] data, int header, int end, String name) {
        // The serialized wrapper carries 256 bytes before the codec header.
        if (end - header < 420 || WrathTextures.u32(data, header + 260) != 303185) return null;
        int base = header + 264;
        long packedLength = WrathTextures.u32(data, base);
        int packedEnd = end - 8;
        float time = value(data, header + 256);
        if (base + packedLength != packedEnd || !Float.isFinite(time) || time < 0
                || WrathTextures.u32(data, header + 256) != WrathTextures.u32(data, header + 300)
                || !name.equalsIgnoreCase(jointName(data, header + 268))) return null;

        int count = (int) (WrathTextures.u32(data, header + 308) & 65535);
        int directory = header + 416;
        long directoryEnd = directory + 4L * (count + 1);
        if (count == 0 || count > 512 || directoryEnd > packedEnd
                || WrathTextures.u32(data, header + 312) != 156
                || WrathTextures.u32(data, directory) != 0xffffffffL) return null;

        StringBuilder json = new StringBuilder("{\n  \"format\": \"wrath-packed-clip-v1\",\n  \"name\": \"")
            .append(name).append("\",\n  \"time_value\": ").append(time)
            .append(",\n  \"track_count\": ").append(count).append(",\n  \"tracks\": [");
        long previousEnd = directoryEnd;
        for (int i = 0; i < count; i++) {
            long relative = WrathTextures.u32(data, directory + (i + 1) * 4);
            long track = base + relative;
            if (track < previousEnd || track + 4 > packedEnd) return null;
            long length = WrathTextures.u32(data, (int) track);
            if (length < 32 || track + length > packedEnd) return null;
            previousEnd = track + length;
            if (i > 0) json.append(',');
            json.append("\n    {\"offset\": ").append(relative)
                .append(", \"bytes\": ").append(length).append('}');
        }
        return json.append("\n  ],\n  \"packed_base64\": \"")
            .append(Base64.getEncoder().encodeToString(Arrays.copyOfRange(data, base, packedEnd)))
            .append("\"\n}\n").toString();
    }
}
