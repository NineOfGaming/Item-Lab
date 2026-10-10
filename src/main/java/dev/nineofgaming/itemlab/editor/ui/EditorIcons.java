package dev.nineofgaming.itemlab.editor.ui;

import dev.nineofgaming.itemlab.ItemLab;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiHoveredFlags;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * Consistent icon-only editor actions backed by the bundled Material Icons font.
 */
public final class EditorIcons {
    private static final Logger LOGGER = ItemLab.createLogger("EditorIcons");
    private static final String FONT_RESOURCE = "/assets/itemlab/imgui_font/material_icons_regular.ttf";
    private static final float FONT_SIZE = 24.0f;
    private static final short[] GLYPH_RANGES = {
        (short) 0xE037, (short) 0xE037,
        (short) 0xE047, (short) 0xE047,
        (short) 0xE145, (short) 0xE145,
        (short) 0xE14D, (short) 0xE14D,
        (short) 0xE15A, (short) 0xE15A,
        (short) 0xE161, (short) 0xE161,
        (short) 0xE166, (short) 0xE166,
        (short) 0xE25D, (short) 0xE25D,
        (short) 0xE2C8, (short) 0xE2C8,
        (short) 0xE429, (short) 0xE429,
        (short) 0xE5CB, (short) 0xE5CB,
        (short) 0xE5CC, (short) 0xE5CC,
        (short) 0xE5CD, (short) 0xE5CD,
        (short) 0xE5D3, (short) 0xE5D3,
        (short) 0xE5D5, (short) 0xE5D5,
        (short) 0xE5D8, (short) 0xE5D8,
        (short) 0xE5DB, (short) 0xE5DB,
        (short) 0xE872, (short) 0xE872,
        (short) 0xE8B3, (short) 0xE8B3,
        0
    };

    private static ImFont iconFont;

    private EditorIcons() {
    }

    /**
     * Called by ImGuiMC whenever it rebuilds its font atlas.
     */
    public static void registerFont(ImFontAtlas atlas, ImFont defaultFont, float scale) {
        try (InputStream stream = EditorIcons.class.getResourceAsStream(FONT_RESOURCE)) {
            if (stream == null) {
                iconFont = null;
                LOGGER.error("Missing bundled Material Icons font: {}", FONT_RESOURCE);
                return;
            }

            ImFontConfig config = new ImFontConfig();
            try {
                config.setName("Item Lab Material Icons");
                config.setPixelSnapH(true);
                config.setOversampleH(2);
                config.setOversampleV(2);
                iconFont = atlas.addFontFromMemoryTTF(
                    stream.readAllBytes(),
                    FONT_SIZE * scale,
                    config,
                    GLYPH_RANGES
                );
            } finally {
                config.destroy();
            }
        } catch (IOException | RuntimeException exception) {
            iconFont = null;
            LOGGER.error("Could not load the bundled Material Icons font", exception);
        }
    }

    public static float buttonSize() {
        return Math.max(32.0f, ImGui.getFrameHeight() + 8.0f);
    }

    public static float smallButtonSize() {
        return Math.max(27.0f, ImGui.getFrameHeight() + 3.0f);
    }

    public static boolean button(String id, Icon icon, String tooltip) {
        return drawButton(id, icon, tooltip, buttonSize());
    }

    public static boolean smallButton(String id, Icon icon, String tooltip) {
        return drawButton(id, icon, tooltip, smallButtonSize());
    }

    /**
     * Draws an icon-only drag grip without ordinary button chrome.
     */
    public static void dragHandle(String id, String tooltip) {
        float size = smallButtonSize();
        ImGui.invisibleButton("##" + id, size, size);
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        if (hovered || active) {
            ImGui.getWindowDrawList().addRectFilled(
                ImGui.getItemRectMinX(),
                ImGui.getItemRectMinY(),
                ImGui.getItemRectMaxX(),
                ImGui.getItemRectMaxY(),
                ImGui.getColorU32(active ? ImGuiCol.HeaderActive : ImGuiCol.HeaderHovered, 0.55f),
                4.0f
            );
        }
        int color = ImGui.getColorU32(
            active ? ImGuiCol.DragDropTarget : hovered ? ImGuiCol.Text : ImGuiCol.TextDisabled
        );
        drawIcon(Icon.DRAG_HANDLE, size, color);
        if (hovered
            && !ImGui.isMouseDragging(0)
            && tooltip != null
            && !tooltip.isBlank()) {
            ImGui.setTooltip(tooltip);
        }
    }

    private static boolean drawButton(String id, Icon icon, String tooltip, float size) {
        boolean pressed = ImGui.button("##" + id, size, size);
        drawIcon(icon, size);
        if (tooltip != null
            && !tooltip.isBlank()
            && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(tooltip);
        }
        return pressed;
    }

    private static void drawIcon(Icon icon, float buttonSize) {
        drawIcon(icon, buttonSize, ImGui.getColorU32(ImGuiCol.Text));
    }

    private static void drawIcon(Icon icon, float buttonSize, int color) {
        ImFont font = iconFont;
        boolean fontAvailable = font != null && font.isLoaded();
        String text = fontAvailable ? icon.glyph : icon.fallback;
        float fontSize = fontAvailable
            ? buttonSize * 0.70f
            : Math.min(ImGui.getFontSize(), buttonSize * 0.58f);
        float textWidth = fontAvailable
            ? font.calcTextSizeAX(fontSize, Float.MAX_VALUE, 0.0f, text)
            : ImGui.calcTextSizeX(text);
        float textHeight = fontAvailable
            ? font.calcTextSizeAY(fontSize, Float.MAX_VALUE, 0.0f, text)
            : ImGui.getTextLineHeight();
        float x = ImGui.getItemRectMinX() + (buttonSize - textWidth) * 0.5f;
        float y = ImGui.getItemRectMinY() + (buttonSize - textHeight) * 0.5f;
        if (fontAvailable) {
            ImGui.getWindowDrawList().addText(font, Math.round(fontSize), x, y, color, text);
        } else {
            ImGui.getWindowDrawList().addText(x, y, color, text);
        }
    }

    public enum Icon {
        ADD("\uE145", "+"),
        REMOVE("\uE872", "x"),
        DUPLICATE("\uE14D", "++"),
        COPY("\uE14D", "++"),
        DRAG_HANDLE("\uE25D", "::"),
        MOVE_UP("\uE5D8", "^"),
        MOVE_DOWN("\uE5DB", "v"),
        PREVIOUS("\uE5CB", "<"),
        NEXT("\uE5CC", ">"),
        UNDO("\uE166", "<"),
        REDO("\uE15A", ">"),
        SAVE("\uE161", "S"),
        DISCARD("\uE5CD", "x"),
        BROWSE("\uE2C8", "..."),
        SETTINGS("\uE429", "="),
        DEFAULTS("\uE8B3", "R"),
        RELOAD("\uE5D5", "R"),
        PLAY("\uE037", ">"),
        STOP("\uE047", "[]"),
        MORE("\uE5D3", "...");

        private final String glyph;
        private final String fallback;

        Icon(String glyph, String fallback) {
            this.glyph = glyph;
            this.fallback = fallback;
        }
    }
}
