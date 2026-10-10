package dev.nineofgaming.itemlab.editor.ui.text;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a small TrueType font from monochrome bitmap rows entirely in memory.
 */
final class BitmapTrueTypeFont {
    private static final int UNITS_PER_EM = 16;
    private static final int ASCENT = 14;
    private static final int DESCENT = -2;
    private static final int CHECKSUM_MAGIC = 0xB1B0AFBA;

    private BitmapTrueTypeFont() {
    }

    static byte[] build(List<Glyph> requestedGlyphs) {
        List<Glyph> glyphs = requestedGlyphs.stream()
            .filter(glyph -> glyph.codepoint() > 0 && glyph.codepoint() < 0xFFFF)
            .sorted(Comparator.comparingInt(Glyph::codepoint))
            .toList();
        if (glyphs.isEmpty()) return null;

        try {
            List<byte[]> outlines = new ArrayList<>(glyphs.size() + 1);
            outlines.add(emptyGlyph());
            int maxPoints = 0;
            int maxContours = 0;
            for (Glyph glyph : glyphs) {
                Outline outline = outline(glyph);
                outlines.add(outline.bytes());
                maxPoints = Math.max(maxPoints, outline.points());
                maxContours = Math.max(maxContours, outline.contours());
            }

            GlyfData glyfData = glyfTable(outlines);
            int glyphCount = glyphs.size() + 1;
            int maxAdvance = glyphs.stream().mapToInt(Glyph::advance).max().orElse(UNITS_PER_EM);
            Map<String, byte[]> tables = new LinkedHashMap<>();
            tables.put("OS/2", os2Table(glyphs));
            tables.put("cmap", cmapTable(glyphs));
            tables.put("glyf", glyfData.glyf());
            tables.put("head", headTable());
            tables.put("hhea", hheaTable(glyphCount, maxAdvance));
            tables.put("hmtx", hmtxTable(glyphs));
            tables.put("loca", locaTable(glyfData.offsets()));
            tables.put("maxp", maxpTable(glyphCount, maxPoints, maxContours));
            tables.put("name", nameTable());
            tables.put("post", postTable());
            return sfnt(tables);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build the Minecraft glyph fallback font", exception);
        }
    }

    private static Outline outline(Glyph glyph) throws IOException {
        List<Rect> runs = new ArrayList<>();
        for (int row = 0; row < glyph.rows().length; row++) {
            int bits = glyph.rows()[row];
            int column = 0;
            while (column < glyph.width()) {
                while (column < glyph.width() && !pixel(bits, glyph.width(), column)) column++;
                int start = column;
                while (column < glyph.width() && pixel(bits, glyph.width(), column)) column++;
                if (start < column) runs.add(new Rect(start, row, column, row + 1));
            }
        }
        if (runs.isEmpty()) return new Outline(emptyGlyph(), 0, 0);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(runs.size());
        out.writeShort(runs.stream().mapToInt(Rect::left).min().orElse(0));
        out.writeShort(ASCENT - runs.stream().mapToInt(Rect::bottom).max().orElse(16));
        out.writeShort(runs.stream().mapToInt(Rect::right).max().orElse(0));
        out.writeShort(ASCENT - runs.stream().mapToInt(Rect::top).min().orElse(0));

        int pointCount = runs.size() * 4;
        for (int contour = 0; contour < runs.size(); contour++) {
            out.writeShort(contour * 4 + 3);
        }
        out.writeShort(0);
        for (int point = 0; point < pointCount; point++) out.writeByte(0x01);

        int previousX = 0;
        for (Rect run : runs) {
            int[] xs = {run.left(), run.left(), run.right(), run.right()};
            for (int x : xs) {
                out.writeShort(x - previousX);
                previousX = x;
            }
        }
        int previousY = 0;
        for (Rect run : runs) {
            int top = ASCENT - run.top();
            int bottom = ASCENT - run.bottom();
            int[] ys = {bottom, top, top, bottom};
            for (int y : ys) {
                out.writeShort(y - previousY);
                previousY = y;
            }
        }
        return new Outline(bytes.toByteArray(), pointCount, runs.size());
    }

    private static boolean pixel(int row, int width, int column) {
        return (row & 1 << (width - column - 1)) != 0;
    }

    private static byte[] emptyGlyph() {
        return new byte[10];
    }

    private static GlyfData glyfTable(List<byte[]> glyphs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int[] offsets = new int[glyphs.size() + 1];
        for (int index = 0; index < glyphs.size(); index++) {
            offsets[index] = bytes.size();
            bytes.write(glyphs.get(index));
            if ((bytes.size() & 1) != 0) bytes.write(0);
        }
        offsets[glyphs.size()] = bytes.size();
        return new GlyfData(bytes.toByteArray(), offsets);
    }

    private static byte[] cmapTable(List<Glyph> glyphs) throws IOException {
        int segmentCount = glyphs.size() + 1;
        int power = Integer.highestOneBit(segmentCount);
        int searchRange = power * 2;
        int entrySelector = Integer.numberOfTrailingZeros(power);
        int rangeShift = segmentCount * 2 - searchRange;

        ByteArrayOutputStream subtableBytes = new ByteArrayOutputStream();
        DataOutputStream subtable = new DataOutputStream(subtableBytes);
        subtable.writeShort(4);
        subtable.writeShort(16 + segmentCount * 8);
        subtable.writeShort(0);
        subtable.writeShort(segmentCount * 2);
        subtable.writeShort(searchRange);
        subtable.writeShort(entrySelector);
        subtable.writeShort(rangeShift);
        for (Glyph glyph : glyphs) subtable.writeShort(glyph.codepoint());
        subtable.writeShort(0xFFFF);
        subtable.writeShort(0);
        for (Glyph glyph : glyphs) subtable.writeShort(glyph.codepoint());
        subtable.writeShort(0xFFFF);
        for (int index = 0; index < glyphs.size(); index++) {
            int delta = index + 1 - glyphs.get(index).codepoint();
            subtable.writeShort(delta);
        }
        subtable.writeShort(1);
        for (int ignored = 0; ignored < segmentCount; ignored++) subtable.writeShort(0);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0);
        out.writeShort(1);
        out.writeShort(3);
        out.writeShort(1);
        out.writeInt(12);
        out.write(subtableBytes.toByteArray());
        return bytes.toByteArray();
    }

    private static byte[] headTable() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010000);
        out.writeInt(0x00010000);
        out.writeInt(0);
        out.writeInt(0x5F0F3CF5);
        out.writeShort(0x000B);
        out.writeShort(UNITS_PER_EM);
        out.writeLong(0);
        out.writeLong(0);
        out.writeShort(0);
        out.writeShort(DESCENT);
        out.writeShort(32);
        out.writeShort(ASCENT);
        out.writeShort(0);
        out.writeShort(8);
        out.writeShort(2);
        out.writeShort(1);
        out.writeShort(0);
        return bytes.toByteArray();
    }

    private static byte[] hheaTable(int glyphCount, int maxAdvance) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010000);
        out.writeShort(ASCENT);
        out.writeShort(DESCENT);
        out.writeShort(0);
        out.writeShort(maxAdvance);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(maxAdvance);
        out.writeShort(1);
        out.writeShort(0);
        out.writeShort(0);
        for (int ignored = 0; ignored < 4; ignored++) out.writeShort(0);
        out.writeShort(0);
        out.writeShort(glyphCount);
        return bytes.toByteArray();
    }

    private static byte[] hmtxTable(List<Glyph> glyphs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(UNITS_PER_EM / 2);
        out.writeShort(0);
        for (Glyph glyph : glyphs) {
            out.writeShort(glyph.advance());
            out.writeShort(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] locaTable(int[] offsets) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        for (int offset : offsets) out.writeInt(offset);
        return bytes.toByteArray();
    }

    private static byte[] maxpTable(int glyphCount, int maxPoints, int maxContours) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010000);
        out.writeShort(glyphCount);
        out.writeShort(maxPoints);
        out.writeShort(maxContours);
        for (int ignored = 0; ignored < 11; ignored++) out.writeShort(0);
        return bytes.toByteArray();
    }

    private static byte[] nameTable() throws IOException {
        record Name(int id, String value) {
        }
        List<Name> names = List.of(
            new Name(1, "Item Lab Minecraft Glyphs"),
            new Name(2, "Regular"),
            new Name(4, "Item Lab Minecraft Glyphs Regular"),
            new Name(6, "ItemLabMinecraftGlyphs-Regular")
        );
        List<byte[]> encoded = names.stream()
            .map(name -> name.value().getBytes(StandardCharsets.UTF_16BE))
            .toList();
        int stringOffset = 6 + names.size() * 12;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0);
        out.writeShort(names.size());
        out.writeShort(stringOffset);
        int offset = 0;
        for (int index = 0; index < names.size(); index++) {
            out.writeShort(3);
            out.writeShort(1);
            out.writeShort(0x0409);
            out.writeShort(names.get(index).id());
            out.writeShort(encoded.get(index).length);
            out.writeShort(offset);
            offset += encoded.get(index).length;
        }
        for (byte[] value : encoded) out.write(value);
        return bytes.toByteArray();
    }

    private static byte[] postTable() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00030000);
        out.writeInt(0);
        out.writeShort(0);
        out.writeShort(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        return bytes.toByteArray();
    }

    private static byte[] os2Table(List<Glyph> glyphs) throws IOException {
        int first = glyphs.getFirst().codepoint();
        int last = glyphs.getLast().codepoint();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0);
        out.writeShort(UNITS_PER_EM / 2);
        out.writeShort(400);
        out.writeShort(5);
        out.writeShort(0);
        for (int ignored = 0; ignored < 11; ignored++) out.writeShort(0);
        out.write(new byte[10]);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.write("ITLB".getBytes(StandardCharsets.US_ASCII));
        out.writeShort(0x0040);
        out.writeShort(first);
        out.writeShort(last);
        out.writeShort(ASCENT);
        out.writeShort(DESCENT);
        out.writeShort(0);
        out.writeShort(ASCENT);
        out.writeShort(-DESCENT);
        return bytes.toByteArray();
    }

    private static byte[] sfnt(Map<String, byte[]> tables) throws IOException {
        int count = tables.size();
        int power = Integer.highestOneBit(count);
        int searchRange = power * 16;
        int entrySelector = Integer.numberOfTrailingZeros(power);
        int rangeShift = count * 16 - searchRange;
        int tableOffset = 12 + count * 16;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010000);
        out.writeShort(count);
        out.writeShort(searchRange);
        out.writeShort(entrySelector);
        out.writeShort(rangeShift);

        int offset = tableOffset;
        int headOffset = -1;
        for (Map.Entry<String, byte[]> table : tables.entrySet()) {
            byte[] data = table.getValue();
            out.write(table.getKey().getBytes(StandardCharsets.US_ASCII));
            out.writeInt(checksum(data));
            out.writeInt(offset);
            out.writeInt(data.length);
            if (table.getKey().equals("head")) headOffset = offset;
            offset += alignedLength(data.length);
        }
        for (byte[] data : tables.values()) {
            out.write(data);
            for (int padding = data.length; padding < alignedLength(data.length); padding++) out.writeByte(0);
        }

        byte[] font = bytes.toByteArray();
        int adjustment = CHECKSUM_MAGIC - checksum(font);
        ByteBuffer.wrap(font).order(ByteOrder.BIG_ENDIAN).putInt(headOffset + 8, adjustment);
        return font;
    }

    private static int checksum(byte[] bytes) {
        long sum = 0;
        for (int offset = 0; offset < alignedLength(bytes.length); offset += 4) {
            int word = 0;
            for (int byteIndex = 0; byteIndex < 4; byteIndex++) {
                int index = offset + byteIndex;
                word = word << 8 | (index < bytes.length ? bytes[index] & 0xFF : 0);
            }
            sum = sum + Integer.toUnsignedLong(word) & 0xFFFFFFFFL;
        }
        return (int) sum;
    }

    private static int alignedLength(int length) {
        return length + 3 & ~3;
    }

    record Glyph(int codepoint, int width, int[] rows, int advance) {
    }

    private record Rect(int left, int top, int right, int bottom) {
    }

    private record Outline(byte[] bytes, int points, int contours) {
    }

    private record GlyfData(byte[] glyf, int[] offsets) {
    }
}
