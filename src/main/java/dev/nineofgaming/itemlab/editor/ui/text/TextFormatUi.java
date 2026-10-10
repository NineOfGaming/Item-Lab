package dev.nineofgaming.itemlab.editor.ui.text;

import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.function.Consumer;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Shared, visibly stateful controls for choosing the editor's text input format.
 */
public final class TextFormatUi {
    private TextFormatUi() {
    }

    public static float buttonWidth(TextInputFormat format) {
        return ImGui.calcTextSizeX(buttonLabel(format))
            + ImGui.getStyle().getFramePaddingX() * 2.0f;
    }

    public static boolean button(String id, TextInputFormat format, String extraTooltip) {
        boolean pressed = ImGui.button(buttonLabel(format) + "##" + id);
        if (ImGui.isItemHovered()) {
            String tooltip = buttonLabel(format)
                + "\n" + format.colorHelp()
                + "\n" + format.styleHelp();
            if (extraTooltip != null && !extraTooltip.isBlank()) {
                tooltip += "\n\n" + extraTooltip;
            }
            ImGui.setTooltip(tooltip);
        }
        return pressed;
    }

    public static void drawSelector(
        String id,
        TextInputFormat current,
        Consumer<TextInputFormat> onSelected
    ) {
        drawSelector(id, current, onSelected, true);
    }

    public static void drawSelector(
        String id,
        TextInputFormat current,
        Consumer<TextInputFormat> onSelected,
        boolean showDescription
    ) {
        ImGui.text(tr("text.format"));
        TextInputFormat[] formats = TextInputFormat.values();
        for (int index = 0; index < formats.length; index++) {
            TextInputFormat option = formats[index];
            if (index > 0) EditorUi.sameLineIfFits(option.label());
            boolean selected = current == option;
            if (selected) ItemEditorTheme.pushActiveFilter();
            if (ImGui.button(option.label() + "##" + id + "_" + option.name()) && !selected) {
                onSelected.accept(option);
            }
            if (selected) ItemEditorTheme.popActiveFilter();
            if (!showDescription && ImGui.isItemHovered()) {
                EditorUi.setWrappedTooltip(option.colorHelp() + "\n" + option.styleHelp());
            }
        }
        if (showDescription) {
            EditorUi.textDisabledWrapped(current.colorHelp());
            EditorUi.textDisabledWrapped(current.styleHelp());
        }
        drawDocumentationLink(current);
    }

    private static void drawDocumentationLink(TextInputFormat format) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.38f, 0.68f, 1.0f, 1.0f);
        ImGui.textWrapped(format.documentationLabel());
        ImGui.popStyleColor();

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(format.documentationUrl());
        }
        if (ImGui.isItemClicked(0)) {
            Util.getPlatform().openUri(URI.create(format.documentationUrl()));
        }
    }

    private static String buttonLabel(TextInputFormat format) {
        return tr("text.format_button", format.label());
    }
}
