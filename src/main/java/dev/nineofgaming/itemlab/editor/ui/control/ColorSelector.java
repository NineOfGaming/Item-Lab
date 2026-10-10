package dev.nineofgaming.itemlab.editor.ui.control;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImInt;
import net.minecraft.world.item.DyeColor;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Shared color controls used by every purpose-built editor.
 */
public final class ColorSelector {
    private ColorSelector() {
    }

    public static int drawRgb(String id, int color, boolean alpha) {
        return drawRgb(id, color, alpha, false);
    }

    public static int drawRgb(String id, int color, boolean alpha, boolean focusFirstChannel) {
        int a = alpha ? color >>> 24 & 0xFF : 0xFF;
        ImInt red = new ImInt(color >>> 16 & 0xFF);
        ImInt green = new ImInt(color >>> 8 & 0xFF);
        ImInt blue = new ImInt(color & 0xFF);
        ImInt opacity = new ImInt(a);
        boolean changed = false;
        int columns = alpha ? 4 : 3;
        if (ImGui.beginTable(
            "color_channels_" + id,
            columns,
            ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoPadOuterX
        )) {
            ImGui.tableNextRow();
            channelLabel("R");
            channelLabel("G");
            channelLabel("B");
            if (alpha) channelLabel("A");
            ImGui.tableNextRow();
            if (focusFirstChannel) ImGui.setKeyboardFocusHere();
            changed |= channelInput("R", red);
            changed |= channelInput("G", green);
            changed |= channelInput("B", blue);
            if (alpha) changed |= channelInput("A", opacity);
            ImGui.endTable();
        }

        int result = (alpha ? Math.clamp(opacity.get(), 0, 255) << 24 : 0)
            | Math.clamp(red.get(), 0, 255) << 16
            | Math.clamp(green.get(), 0, 255) << 8
            | Math.clamp(blue.get(), 0, 255);
        int display = alpha ? result : 0xFF000000 | result;
        float displayA = (display >>> 24 & 0xFF) / 255.0f;
        float displayR = (display >>> 16 & 0xFF) / 255.0f;
        float displayG = (display >>> 8 & 0xFF) / 255.0f;
        float displayB = (display & 0xFF) / 255.0f;
        ImGui.pushStyleColor(ImGuiCol.Button, displayR, displayG, displayB, displayA);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, displayR, displayG, displayB, displayA);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, displayR, displayG, displayB, displayA);
        ImGui.button(String.format("#%08X##preview_%s", display, id), -1.0f, 0.0f);
        ImGui.popStyleColor(3);
        return changed ? result : color;
    }

    public static DyeColor drawDye(String id, DyeColor selected) {
        DyeColor result = selected;
        float buttonSize = Math.max(24.0f, ImGui.getFrameHeight() + 4.0f);
        float spacing = ImGui.getStyle().getItemSpacingX();
        int columns = Math.clamp(
            (int) ((ImGui.getContentRegionAvailX() + spacing) / (buttonSize + spacing)),
            1,
            8
        );
        for (int index = 0; index < DyeColor.VALUES.size(); index++) {
            DyeColor color = DyeColor.VALUES.get(index);
            int rgb = color.getTextureDiffuseColor();
            float red = (rgb >>> 16 & 0xFF) / 255.0f;
            float green = (rgb >>> 8 & 0xFF) / 255.0f;
            float blue = (rgb & 0xFF) / 255.0f;
            ImGui.pushID(id + "_" + color.getName());
            ImGui.pushStyleColor(ImGuiCol.Button, red, green, blue, 1.0f);
            ImGui.pushStyleColor(
                ImGuiCol.ButtonHovered,
                Math.min(1.0f, red + 0.12f),
                Math.min(1.0f, green + 0.12f),
                Math.min(1.0f, blue + 0.12f),
                1.0f
            );
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, red * 0.85f, green * 0.85f, blue * 0.85f, 1.0f);
            if (ImGui.button("##color", buttonSize, buttonSize)) result = color;
            ImGui.popStyleColor(3);
            if (ImGui.isItemHovered()) ImGui.setTooltip(tr("color." + color.getName()));
            ImGui.popID();
            if (index % columns != columns - 1 && index + 1 < DyeColor.VALUES.size()) ImGui.sameLine();
        }
        ImGui.text(tr("color.selected", tr("color." + result.getName())));
        return result;
    }

    private static void channelLabel(String label) {
        ImGui.tableNextColumn();
        ImGui.text(label);
    }

    private static boolean channelInput(String label, ImInt value) {
        ImGui.tableNextColumn();
        ImGui.setNextItemWidth(-1.0f);
        if (!ImGui.inputInt("##" + label, value, 1, 16)) return false;
        value.set(Math.clamp(value.get(), 0, 255));
        return true;
    }
}
