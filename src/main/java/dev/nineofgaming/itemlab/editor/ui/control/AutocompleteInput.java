package dev.nineofgaming.itemlab.editor.ui.control;

import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Identifier input whose suggestions follow actual text-edit focus and dismiss predictably.
 */
public final class AutocompleteInput {
    private static final int MAX_VISIBLE_ROWS = 8;

    private int activeIndex;
    private int activeWidgetId;
    private int dismissedWidgetId;
    private String dismissedValue = "";
    private boolean suppressNextAutomaticActivation;
    private boolean lastInputCommitted;
    private boolean lastInputSubmitted;

    public void reset() {
        activeIndex = 0;
        activeWidgetId = 0;
        dismissedWidgetId = 0;
        dismissedValue = "";
        suppressNextAutomaticActivation = true;
        lastInputCommitted = false;
        lastInputSubmitted = false;
    }

    public boolean input(String id, String hint, ImString value, List<IdSuggestion> suggestions) {
        return input(id, hint, value, suggestions, identifierProblem(value.get()));
    }

    public boolean input(String id, FieldHints hints, ImString value, List<IdSuggestion> suggestions) {
        return input(id, hints, value, suggestions, identifierProblem(value.get()));
    }

    public boolean input(
        String id,
        FieldHints hints,
        ImString value,
        List<IdSuggestion> suggestions,
        String validationError
    ) {
        return input(id, hints.purposeHint(), hints.syntaxExample(), value, suggestions, validationError);
    }

    public boolean input(
        String id,
        String hint,
        ImString value,
        List<IdSuggestion> suggestions,
        String validationError
    ) {
        return input(id, hint, "", value, suggestions, validationError);
    }

    private boolean input(
        String id,
        String hint,
        String syntaxExample,
        ImString value,
        List<IdSuggestion> suggestions,
        String validationError
    ) {
        lastInputCommitted = false;
        lastInputSubmitted = false;
        ImGui.setNextItemWidth(-1.0f);
        boolean[] completedWithTab = {false};
        boolean invalid = validationError != null && !validationError.isBlank();
        if (invalid) ItemEditorTheme.pushInvalidField();
        boolean changed = EditorInput.inputTextWithHint(
            "##" + id,
            hint,
            value,
            ImGuiInputTextFlags.CallbackCompletion,
            new ImGuiInputTextCallback() {
                @Override
                public void accept(ImGuiInputTextCallbackData data) {
                    List<IdSuggestion> matches = matchingSuggestions(
                        suggestions,
                        data.getBuf().trim().toLowerCase(Locale.ROOT)
                    );
                    if (matches.isEmpty()) {
                        return;
                    }
                    int selected = Math.clamp(activeIndex, 0, matches.size() - 1);
                    String completion = matches.get(selected).id();
                    data.deleteChars(0, data.getBufTextLen());
                    data.insertChars(0, completion);
                    data.setCursorPos(completion.length());
                    data.setSelectionStart(completion.length());
                    data.setSelectionEnd(completion.length());
                    data.setBufDirty(true);
                    completedWithTab[0] = true;
                }
            }
        );
        if (invalid) ItemEditorTheme.popInvalidField();

        int widgetId = ImGui.getItemID();
        boolean active = ImGui.isItemActive();
        boolean activated = ImGui.isItemActivated();
        boolean deactivatedAfterEdit = ImGui.isItemDeactivatedAfterEdit();
        boolean inputHovered = ImGui.isItemHovered();
        if (inputHovered && syntaxExample != null && !syntaxExample.isBlank()) {
            EditorUi.setTooltip(tr("field.syntax_example", syntaxExample));
        }
        boolean mouseClicked = ImGui.isMouseClicked(0);
        ItemEditorTheme.drawFieldProblem(validationError);

        if (suppressNextAutomaticActivation) {
            suppressNextAutomaticActivation = false;
            if (active) {
                dismiss(widgetId, value.get());
            }
            lastInputCommitted = deactivatedAfterEdit;
            return changed;
        }

        if (activated) {
            activeWidgetId = widgetId;
            activeIndex = 0;
            clearDismissal(widgetId);
        }
        if (changed && !completedWithTab[0]) {
            activeWidgetId = widgetId;
            activeIndex = 0;
            clearDismissal(widgetId);
        }
        if (completedWithTab[0]) {
            dismiss(widgetId, value.get());
            lastInputCommitted = true;
            return true;
        }

        if (active) {
            if (activeWidgetId != widgetId) {
                activeIndex = 0;
            }
            activeWidgetId = widgetId;
        } else if (activeWidgetId != widgetId) {
            lastInputCommitted = deactivatedAfterEdit;
            return changed;
        } else if (!mouseClicked) {
            activeWidgetId = 0;
            lastInputCommitted = deactivatedAfterEdit;
            return changed;
        }

        if (dismissedWidgetId == widgetId && dismissedValue.equals(value.get())) {
            lastInputCommitted = deactivatedAfterEdit;
            return changed;
        }

        String query = value.get().trim().toLowerCase(Locale.ROOT);
        List<IdSuggestion> matches = matchingSuggestions(suggestions, query);
        activeIndex = Math.clamp(activeIndex, 0, Math.max(0, matches.size() - 1));
        boolean navigated = false;
        if (active && !matches.isEmpty()) {
            if (ImGui.isKeyPressed(ImGuiKey.DownArrow, false)) {
                activeIndex = Math.min(activeIndex + 1, matches.size() - 1);
                navigated = true;
            }
            if (ImGui.isKeyPressed(ImGuiKey.UpArrow, false)) {
                activeIndex = Math.max(activeIndex - 1, 0);
                navigated = true;
            }
        }
        if (active && (ImGui.isKeyPressed(ImGuiKey.Enter, false)
            || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false))) {
            if (!matches.isEmpty()) {
                value.set(matches.get(activeIndex).id());
            }
            dismiss(widgetId, value.get());
            lastInputCommitted = true;
            lastInputSubmitted = true;
            return !matches.isEmpty() || changed;
        }

        int visibleRows = Math.clamp(matches.size(), 1, MAX_VISIBLE_ROWS);
        float listHeight = visibleRows * ImGui.getFrameHeightWithSpacing()
            + ImGui.getStyle().getWindowPaddingY() * 2.0f + 2.0f;
        int windowFlags = matches.size() <= MAX_VISIBLE_ROWS
            ? ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
            : 0;
        boolean accepted = false;
        ItemEditorTheme.pushSuggestionPanel();
        EditorUi.beginScrollableChild(
            "##" + id + "_recommendations",
            0.0f,
            listHeight,
            ImGuiChildFlags.Borders | ImGuiChildFlags.AlwaysUseWindowPadding,
            windowFlags
        );
        try {
            if (matches.isEmpty()) {
                ImGui.textDisabled(tr("autocomplete.no_matches"));
            } else {
                accepted = drawRows(matches, value, navigated);
            }
        } finally {
            ImGui.endChild();
            ItemEditorTheme.popSuggestionPanel();
        }

        if (accepted) {
            dismiss(widgetId, value.get());
            lastInputCommitted = true;
            return true;
        }
        if (mouseClicked && !inputHovered) {
            dismiss(widgetId, value.get());
        }
        lastInputCommitted = deactivatedAfterEdit;
        return changed;
    }

    public boolean wasLastInputCommitted() {
        return lastInputCommitted;
    }

    public boolean wasLastInputSubmitted() {
        return lastInputSubmitted;
    }

    private boolean drawRows(List<IdSuggestion> matches, ImString value, boolean navigated) {
        for (int index = 0; index < matches.size(); index++) {
            IdSuggestion suggestion = matches.get(index);
            String label = suggestion.displayName().equals(suggestion.id())
                ? suggestion.id()
                : suggestion.displayName() + " (" + suggestion.id() + ")";
            float availableWidth = ImGui.getContentRegionAvailX();
            boolean selected = ImGui.selectable(label + "###suggestion_" + suggestion.id(), index == activeIndex);
            if (selected || ImGui.isItemClicked(0)) {
                value.set(suggestion.id());
                return true;
            }
            if (index == activeIndex && navigated) {
                ImGui.setScrollHereY(0.5f);
            }
            if (ImGui.isItemHovered()) {
                activeIndex = index;
                if (ImGui.calcTextSizeX(label) > availableWidth) {
                    ImGui.setTooltip(label);
                }
            }
        }
        return false;
    }

    private void dismiss(int widgetId, String value) {
        activeWidgetId = 0;
        dismissedWidgetId = widgetId;
        dismissedValue = value;
    }

    private void clearDismissal(int widgetId) {
        if (dismissedWidgetId == widgetId) {
            dismissedWidgetId = 0;
            dismissedValue = "";
        }
    }

    private static List<IdSuggestion> matchingSuggestions(List<IdSuggestion> suggestions, String query) {
        List<IdSuggestion> matches = new ArrayList<>();
        for (IdSuggestion suggestion : suggestions) {
            if (query.isEmpty() || suggestion.id().startsWith(query)) {
                matches.add(suggestion);
            }
        }
        if (!query.isEmpty()) {
            for (IdSuggestion suggestion : suggestions) {
                if (!suggestion.id().startsWith(query) && suggestion.searchable().contains(query)) {
                    matches.add(suggestion);
                }
            }
        }
        return matches;
    }

    private static String identifierProblem(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) return "";
        if (candidate.startsWith("#")) candidate = candidate.substring(1);
        return Identifier.tryParse(candidate) == null
            ? tr("validation.identifier_or_tag_format")
            : "";
    }
}
