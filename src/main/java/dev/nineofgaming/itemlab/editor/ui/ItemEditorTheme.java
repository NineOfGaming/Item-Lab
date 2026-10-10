package dev.nineofgaming.itemlab.editor.ui;

import dev.nineofgaming.itemlab.editor.ui.syntax.CodeTokenType;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * Small, scoped adjustments that keep ImGui's palette while adding a little hierarchy.
 */
public final class ItemEditorTheme {
    private static final int STYLE_VAR_COUNT = 9;
    private static final int CARD_COLOR_COUNT = 6;
    private static final int PANEL_COLOR_COUNT = 2;
    private static final int INVALID_FIELD_COLOR_COUNT = 4;
    private static final int ACTIVE_FILTER_COLOR_COUNT = 3;
    private static final CodeColor CODE_GUTTER = new CodeColor(0.035f, 0.050f, 0.072f, 1.0f);
    private static final CodeColor CODE_ACTIVE_LINE = new CodeColor(0.12f, 0.25f, 0.38f, 0.58f);
    private static final CodeColor CODE_ERROR_LINE = new CodeColor(0.43f, 0.08f, 0.10f, 0.58f);

    private ItemEditorTheme() {
    }

    public static void push() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 5.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 5.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.TabRounding, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 5.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6.0f, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8.0f, 6.0f);
    }

    public static void pop() {
        ImGui.popStyleVar(STYLE_VAR_COUNT);
    }

    public static void pushCard() {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.075f, 0.105f, 0.150f, 0.98f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.24f, 0.34f, 0.47f, 0.90f);
        ImGui.pushStyleColor(ImGuiCol.TableRowBg, 0.075f, 0.105f, 0.150f, 0.98f);
        ImGui.pushStyleColor(ImGuiCol.TableRowBgAlt, 0.085f, 0.120f, 0.170f, 0.98f);
        ImGui.pushStyleColor(ImGuiCol.TableBorderStrong, 0.24f, 0.34f, 0.47f, 0.90f);
        ImGui.pushStyleColor(ImGuiCol.TableBorderLight, 0.16f, 0.23f, 0.33f, 0.78f);
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 9.0f, 7.0f);
    }

    public static void popCard() {
        ImGui.popStyleVar();
        ImGui.popStyleColor(CARD_COLOR_COUNT);
    }

    public static void pushPanel() {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.055f, 0.075f, 0.105f, 0.98f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.19f, 0.28f, 0.39f, 0.90f);
    }

    public static void popPanel() {
        ImGui.popStyleColor(PANEL_COLOR_COUNT);
    }

    public static void pushSuggestionPanel() {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.065f, 0.090f, 0.125f, 0.99f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.23f, 0.34f, 0.47f, 0.95f);
    }

    public static void popSuggestionPanel() {
        ImGui.popStyleColor(PANEL_COLOR_COUNT);
    }

    public static void pushActiveFilter() {
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getStyleColorVec4(ImGuiCol.HeaderHovered));
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getStyleColorVec4(ImGuiCol.HeaderActive));
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.getStyleColorVec4(ImGuiCol.HeaderActive));
    }

    public static void popActiveFilter() {
        ImGui.popStyleColor(ACTIVE_FILTER_COLOR_COUNT);
    }

    public static void pushInvalidField() {
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.25f, 0.075f, 0.085f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.32f, 0.095f, 0.105f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0.36f, 0.105f, 0.115f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.95f, 0.30f, 0.32f, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
    }

    public static void popInvalidField() {
        ImGui.popStyleVar();
        ImGui.popStyleColor(INVALID_FIELD_COLOR_COUNT);
    }

    public static void drawFieldProblem(String problem) {
        if (problem == null || problem.isBlank()) return;
        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.42f, 0.42f, 1.0f);
        ImGui.textWrapped("! " + problem);
        ImGui.popStyleColor();
    }

    public static void drawFieldWarning(String warning) {
        if (warning == null || warning.isBlank()) return;
        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.72f, 0.26f, 1.0f);
        ImGui.textWrapped("! " + warning);
        ImGui.popStyleColor();
    }

    /**
     * Theme-consistent semantic colors for presentation-only code tokens.
     */
    public static int codeTokenColor(CodeTokenType type) {
        return packed(codeTokenColorValue(type));
    }

    /**
     * Pure color values used by headless contrast checks as well as ImGui rendering.
     */
    public static CodeColor codeTokenColorValue(CodeTokenType type) {
        return switch (type) {
            case KEY -> new CodeColor(0.42f, 0.76f, 1.00f, 1.0f);
            case STRING -> new CodeColor(0.58f, 0.84f, 0.56f, 1.0f);
            case ESCAPE -> new CodeColor(0.96f, 0.76f, 0.38f, 1.0f);
            case NUMBER -> new CodeColor(0.95f, 0.64f, 0.36f, 1.0f);
            case LITERAL -> new CodeColor(0.78f, 0.64f, 1.00f, 1.0f);
            case PUNCTUATION -> new CodeColor(0.68f, 0.75f, 0.84f, 1.0f);
            case UNCLASSIFIED -> new CodeColor(0.86f, 0.89f, 0.94f, 1.0f);
        };
    }

    public static int codeEditorGutterColor() {
        return packed(CODE_GUTTER);
    }

    public static int codeEditorActiveLineColor() {
        return packed(CODE_ACTIVE_LINE);
    }

    public static int codeEditorErrorLineColor() {
        return packed(CODE_ERROR_LINE);
    }

    public static CodeColor codeEditorErrorLineColorValue() {
        return CODE_ERROR_LINE;
    }

    private static int packed(CodeColor color) {
        return ImGui.getColorU32(color.red, color.green, color.blue, color.alpha);
    }

    public record CodeColor(float red, float green, float blue, float alpha) {
        public CodeColor {
            requireUnit(red, "red");
            requireUnit(green, "green");
            requireUnit(blue, "blue");
            requireUnit(alpha, "alpha");
        }

        private static void requireUnit(float value, String channel) {
            if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
                throw new IllegalArgumentException(channel + " must be between 0 and 1: " + value);
            }
        }
    }
}
