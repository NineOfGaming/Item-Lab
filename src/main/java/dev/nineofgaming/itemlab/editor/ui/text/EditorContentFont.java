package dev.nineofgaming.itemlab.editor.ui.text;

import dev.nineofgaming.itemlab.ItemLab;
import foundry.imgui.api.ImGuiMC;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import org.slf4j.Logger;

import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JetBrains Mono with all glyphs present in the bundled font available to editable item text.
 */
public final class EditorContentFont {
    private static final Logger LOGGER = ItemLab.createLogger("EditorContentFont");
    private static final String REGULAR_FONT_RESOURCE = "/assets/imguimc/imgui_font/jetbrains_mono-regular.ttf";
    private static final String ITALIC_FONT_RESOURCE = "/assets/imguimc/imgui_font/jetbrains_mono-italic.ttf";
    private static final String BOLD_FONT_RESOURCE = "/assets/imguimc/imgui_font/jetbrains_mono-bold.ttf";
    private static final String BOLD_ITALIC_FONT_RESOURCE =
        "/assets/imguimc/imgui_font/jetbrains_mono-bold_italic.ttf";
    private static final float FONT_SIZE = 20.0f;
    private static final short[] BASE_GLYPH_RANGES = {
        0x0020, (short) 0xD7FF,
        (short) 0xE000, (short) 0xFFFD,
        0
    };
    private static final Set<Integer> REQUESTED_MINECRAFT_GLYPHS = new LinkedHashSet<>();
    private static final Set<Integer> UNAVAILABLE_MINECRAFT_GLYPHS = new LinkedHashSet<>();
    private static final Map<Integer, BitmapTrueTypeFont.Glyph> MINECRAFT_GLYPHS = new LinkedHashMap<>();
    private static ImFont contentFont;
    private static ImFont italicFont;
    private static ImFont boldFont;
    private static ImFont boldItalicFont;
    private static Font coverageFont;
    private static byte[] minecraftFallbackData;
    private static short[] minecraftFallbackRanges = {0};
    private static boolean minecraftFallbackRebuildPending;

    private EditorContentFont() {
    }

    /**
     * Called by ImGuiMC whenever it rebuilds its font atlas.
     */
    public static void registerFont(ImFontAtlas atlas, ImFont defaultFont, float scale) {
        contentFont = registerStyle(atlas, REGULAR_FONT_RESOURCE, "Regular", scale);
        italicFont = registerStyle(atlas, ITALIC_FONT_RESOURCE, "Italic", scale);
        boldFont = registerStyle(atlas, BOLD_FONT_RESOURCE, "Bold", scale);
        boldItalicFont = registerStyle(atlas, BOLD_ITALIC_FONT_RESOURCE, "Bold Italic", scale);

        if (contentFont == null) contentFont = defaultFont;
        if (italicFont == null) italicFont = contentFont;
        if (boldFont == null) boldFont = contentFont;
        if (boldItalicFont == null) boldItalicFont = boldFont;
    }

    private static ImFont registerStyle(
        ImFontAtlas atlas,
        String baseResource,
        String styleName,
        float scale
    ) {
        ImFont font;
        try (InputStream stream = EditorContentFont.class.getResourceAsStream(baseResource)) {
            if (stream == null) {
                LOGGER.error("Missing ImGuiMC JetBrains Mono font: {}", baseResource);
                return null;
            }

            ImFontConfig config = new ImFontConfig();
            try {
                config.setName("Item Lab Unicode Content " + styleName);
                config.setPixelSnapH(true);
                config.setOversampleH(2);
                config.setOversampleV(2);
                font = atlas.addFontFromMemoryTTF(
                    stream.readAllBytes(),
                    FONT_SIZE * scale,
                    config,
                    BASE_GLYPH_RANGES
                );
            } finally {
                config.destroy();
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not load the {} Unicode editor-content font", styleName, exception);
            return null;
        }

        mergeMinecraftFallback(atlas, font, styleName, scale);
        return font;
    }

    private static void mergeMinecraftFallback(
        ImFontAtlas atlas,
        ImFont destination,
        String styleName,
        float scale
    ) {
        byte[] data = minecraftFallbackData;
        if (data == null || minecraftFallbackRanges.length <= 1) return;

        // ImGui 1.92 mutates the config passed to AddFontFromMemoryTTF. A fresh config and
        // byte array for every merge avoids both config reuse and shared native ownership.
        ImFontConfig config = new ImFontConfig();
        try {
            config.setName("Item Lab Minecraft Glyphs " + styleName);
            config.setMergeMode(true);
            config.setDstFont(destination);
            config.setPixelSnapH(true);
            config.setOversampleH(1);
            config.setOversampleV(1);
            atlas.addFontFromMemoryTTF(
                data.clone(),
                FONT_SIZE * scale,
                config,
                minecraftFallbackRanges
            );
        } finally {
            config.destroy();
        }
    }

    /**
     * Pushes the expanded font if it was loaded, returning whether it must be popped.
     */
    public static boolean push() {
        ImFont font = contentFont;
        if (font == null || !font.isLoaded()) return false;
        ImGui.pushFont(font, 0.0f);
        return true;
    }

    /**
     * Returns the editor font matching a Minecraft bold/italic style.
     */
    public static ImFont styleFont(boolean bold, boolean italic) {
        ImFont font;
        if (bold && italic) {
            font = boldItalicFont;
        } else if (bold) {
            font = boldFont;
        } else if (italic) {
            font = italicFont;
        } else {
            font = contentFont;
        }
        return font != null && font.isLoaded() ? font : null;
    }

    /**
     * Queues Minecraft glyphs missing from JetBrains Mono for the next safe atlas rebuild.
     */
    public static void observeText(String text) {
        if (text == null || text.isEmpty()) return;
        text.codePoints().forEach(EditorContentFont::observeCodepoint);
    }

    public static void observeCodepoint(int codepoint) {
        if (codepoint < 0x20 || codepoint >= 0xFFFF) return;
        Font coverage = coverageFont();
        if (coverage != null && coverage.canDisplay(codepoint)) return;
        if (MINECRAFT_GLYPHS.containsKey(codepoint)
            || UNAVAILABLE_MINECRAFT_GLYPHS.contains(codepoint)) return;
        if (REQUESTED_MINECRAFT_GLYPHS.add(codepoint)) {
            minecraftFallbackRebuildPending = true;
        }
    }

    /**
     * Resolves queued glyphs and rebuilds ImGui outside an active frame.
     */
    public static void tickMinecraftFallback() {
        if (!minecraftFallbackRebuildPending) return;
        if (!MinecraftUnihexGlyphSource.isReady()) return;
        ImFont managedFont = ImGuiMC.getFont(ImGuiMC.FONT_DEFAULT, false, false);
        if (managedFont == null
            || !managedFont.isLoaded()
            || managedFont.getDebugName() == null
            || !managedFont.getDebugName().contains("jetbrains_mono")) return;
        minecraftFallbackRebuildPending = false;

        boolean added = false;
        for (int codepoint : List.copyOf(REQUESTED_MINECRAFT_GLYPHS)) {
            BitmapTrueTypeFont.Glyph glyph = MinecraftUnihexGlyphSource.glyph(codepoint);
            if (glyph == null) {
                UNAVAILABLE_MINECRAFT_GLYPHS.add(codepoint);
            } else {
                MINECRAFT_GLYPHS.put(codepoint, glyph);
                added = true;
            }
            REQUESTED_MINECRAFT_GLYPHS.remove(codepoint);
        }
        if (!added) return;

        minecraftFallbackData = BitmapTrueTypeFont.build(new ArrayList<>(MINECRAFT_GLYPHS.values()));
        minecraftFallbackRanges = glyphRanges(MINECRAFT_GLYPHS.keySet());
        LOGGER.info("Built runtime Minecraft fallback with {} glyphs", MINECRAFT_GLYPHS.size());
        ImGuiMC.rebuildFonts();
    }

    private static Font coverageFont() {
        if (coverageFont != null) return coverageFont;
        try (InputStream stream = EditorContentFont.class.getResourceAsStream(REGULAR_FONT_RESOURCE)) {
            if (stream == null) return null;
            coverageFont = Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (Exception exception) {
            LOGGER.warn("Could not inspect JetBrains Mono glyph coverage", exception);
        }
        return coverageFont;
    }

    private static short[] glyphRanges(Set<Integer> codepoints) {
        List<Integer> sorted = codepoints.stream().sorted().toList();
        List<Short> ranges = new ArrayList<>();
        for (int index = 0; index < sorted.size(); ) {
            int start = sorted.get(index);
            int end = start;
            while (++index < sorted.size() && sorted.get(index) == end + 1) {
                end = sorted.get(index);
            }
            ranges.add((short) start);
            ranges.add((short) end);
        }
        short[] result = new short[ranges.size() + 1];
        for (int index = 0; index < ranges.size(); index++) result[index] = ranges.get(index);
        return result;
    }

}
