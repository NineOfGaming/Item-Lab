package dev.nineofgaming.itemlab.editor.ui.text;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import dev.nineofgaming.itemlab.mixin.client.FontManagerAccessor;
import dev.nineofgaming.itemlab.mixin.client.FontSetAccessor;
import dev.nineofgaming.itemlab.mixin.client.MinecraftFontManagerAccessor;
import dev.nineofgaming.itemlab.mixin.client.UnihexGlyphAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.UnihexProvider;

/**
 * Reads bitmap glyphs from Minecraft's active default-font Unihex providers.
 */
final class MinecraftUnihexGlyphSource {
    private MinecraftUnihexGlyphSource() {
    }

    static boolean isReady() {
        FontSet fontSet = defaultFontSet();
        if (fontSet == null) return false;
        return ((FontSetAccessor) fontSet).itemlab$getActiveProviders().stream()
            .anyMatch(UnihexProvider.class::isInstance);
    }

    static BitmapTrueTypeFont.Glyph glyph(int codepoint) {
        FontSet fontSet = defaultFontSet();
        if (fontSet == null) return null;

        for (GlyphProvider provider : ((FontSetAccessor) fontSet).itemlab$getActiveProviders()) {
            if (!(provider instanceof UnihexProvider)) continue;
            UnbakedGlyph unbaked = provider.getGlyph(codepoint);
            if (!(unbaked instanceof UnihexGlyphAccessor glyph)) continue;
            return extract(codepoint, glyph);
        }
        return null;
    }

    private static FontSet defaultFontSet() {
        Minecraft client = Minecraft.getInstance();
        FontManager fontManager = ((MinecraftFontManagerAccessor) client).itemlab$getFontManager();
        return ((FontManagerAccessor) fontManager)
            .itemlab$getFontSets()
            .get(Minecraft.DEFAULT_FONT);
    }

    private static BitmapTrueTypeFont.Glyph extract(int codepoint, UnihexGlyphAccessor glyph) {
        UnihexProvider.LineData contents = glyph.itemlab$getContents();
        int left = glyph.itemlab$getLeft();
        int right = glyph.itemlab$getRight();
        int width = right - left + 1;
        int[] rows = new int[16];
        int widthMask = width >= 32 ? -1 : (1 << width) - 1;

        for (int row = 0; row < rows.length; row++) {
            int aligned = contents.line(row) << left;
            rows[row] = aligned >>> (32 - width) & widthMask;
        }
        return new BitmapTrueTypeFont.Glyph(codepoint, width, rows, width + 2);
    }
}
