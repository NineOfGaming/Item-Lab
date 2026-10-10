package dev.nineofgaming.itemlab.editor.ui.control;

import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.text.EditorContentFont;

import imgui.ImGui;
import imgui.ImDrawList;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;

/**
 * ImGui text fields that discover runtime Minecraft fallback glyphs before and after editing.
 */
public final class EditorInput {
    private EditorInput() {
    }

    public static boolean inputText(String label, ImString value) {
        observe(value);
        boolean changed = ImGui.inputText(label, value);
        observe(value);
        return changed;
    }

    public static boolean inputTextWithHint(String label, String hint, ImString value) {
        EditorContentFont.observeText(hint);
        observe(value);
        boolean changed = ImGui.inputTextWithHint(label, hint, value);
        observe(value);
        return changed;
    }

    /**
     * Text input whose empty-state purpose and value-shaped syntax help remain distinct.
     */
    public static boolean inputTextWithHint(String label, FieldHints hints, ImString value) {
        boolean changed = inputTextWithHint(label, hints.purposeHint(), value);
        EditorUi.showSyntaxExampleTooltip(hints.syntaxExample());
        return changed;
    }

    public static boolean inputTextWithHint(
        String label,
        String hint,
        ImString value,
        int flags,
        ImGuiInputTextCallback callback
    ) {
        EditorContentFont.observeText(hint);
        observe(value);
        boolean changed = ImGui.inputTextWithHint(label, hint, value, flags, callback);
        observe(value);
        return changed;
    }

    public static boolean inputTextMultiline(
        String label,
        ImString value,
        float width,
        float height,
        int flags
    ) {
        observe(value);
        boolean changed = ImGui.inputTextMultiline(label, value, width, height, flags);
        observe(value);
        return changed;
    }

    /**
     * Multiline text input with a presentation-only hint drawn inside an empty control.
     */
    public static boolean inputTextMultilineWithHint(
        String label,
        String hint,
        ImString value,
        float width,
        float height,
        int flags
    ) {
        EditorContentFont.observeText(hint);
        observe(value);
        boolean changed = ImGui.inputTextMultiline(label, value, width, height, flags);
        observe(value);
        if (value.get().isEmpty()) {
            float left = ImGui.getItemRectMinX();
            float top = ImGui.getItemRectMinY();
            float right = ImGui.getItemRectMaxX();
            float bottom = ImGui.getItemRectMaxY();
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.pushClipRect(left, top, right, bottom, true);
            drawList.addText(
                left + ImGui.getStyle().getFramePaddingX(),
                top + ImGui.getStyle().getFramePaddingY(),
                ImGui.getColorU32(ImGuiCol.TextDisabled),
                hint
            );
            drawList.popClipRect();
        }
        return changed;
    }

    /**
     * Multiline counterpart to the shared purpose/syntax hint contract.
     */
    public static boolean inputTextMultilineWithHint(
        String label,
        FieldHints hints,
        ImString value,
        float width,
        float height,
        int flags
    ) {
        boolean changed = inputTextMultilineWithHint(
            label,
            hints.purposeHint(),
            value,
            width,
            height,
            flags
        );
        EditorUi.showSyntaxExampleTooltip(hints.syntaxExample());
        return changed;
    }

    public static boolean inputTextMultiline(
        String label,
        ImString value,
        float width,
        float height,
        int flags,
        ImGuiInputTextCallback callback
    ) {
        observe(value);
        boolean changed = ImGui.inputTextMultiline(label, value, width, height, flags, callback);
        observe(value);
        return changed;
    }

    private static void observe(ImString value) {
        EditorContentFont.observeText(value.get());
    }
}
