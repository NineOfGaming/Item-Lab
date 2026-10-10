package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Edits Minecraft's holder-set shape: one tag or one/many direct identifiers.
 */
public final class IdentifierCollectionEditor {
    private IdentifierCollectionEditor() {
    }

    public static boolean draw(
        ItemEditor host,
        String id,
        String path,
        JsonElement current,
        Consumer<JsonElement> setter,
        List<IdSuggestion> suggestions,
        String fallback,
        String entryLabel,
        Map<String, ImString> buffers
    ) {
        return draw(
            host,
            id,
            path,
            current,
            setter,
            suggestions,
            fallback,
            fallback,
            entryLabel,
            buffers,
            null
        );
    }

    public static boolean draw(
        ItemEditor host,
        String id,
        String path,
        JsonElement current,
        Consumer<JsonElement> setter,
        List<IdSuggestion> suggestions,
        String fallback,
        String entryLabel,
        Map<String, ImString> buffers,
        FocusState focusState
    ) {
        return draw(
            host,
            id,
            path,
            current,
            setter,
            suggestions,
            fallback,
            fallback,
            entryLabel,
            buffers,
            focusState
        );
    }

    public static boolean draw(
        ItemEditor host,
        String id,
        String path,
        JsonElement current,
        Consumer<JsonElement> setter,
        List<IdSuggestion> suggestions,
        String purposeHint,
        String fallback,
        String entryLabel,
        Map<String, ImString> buffers
    ) {
        return draw(
            host,
            id,
            path,
            current,
            setter,
            suggestions,
            purposeHint,
            fallback,
            entryLabel,
            buffers,
            null
        );
    }

    public static boolean draw(
        ItemEditor host,
        String id,
        String path,
        JsonElement current,
        Consumer<JsonElement> setter,
        List<IdSuggestion> suggestions,
        String purposeHint,
        String fallback,
        String entryLabel,
        Map<String, ImString> buffers,
        FocusState focusState
    ) {
        List<String> values = readValues(current);
        if (values.isEmpty()) {
            values.add(fallback);
        }

        boolean changed = false;
        int remove = -1;
        for (int index = 0; index < values.size(); index++) {
            ImGui.pushID(id + "_" + index);
            String currentValue = values.get(index);
            String bufferKey = path + "/@identifier/" + index;
            ImString input = buffers.computeIfAbsent(
                bufferKey,
                ignored -> new ImString(currentValue, Math.max(512, currentValue.length() * 2 + 64))
            );
            String removeLabel = tr("action.remove", entryLabel);
            if (EditorUi.beginInlineIconActionRow("identifier_row")) {
                if (focusState != null && focusState.consumeRow(index)) {
                    ImGui.setScrollHereY(0.35f);
                    ImGui.setKeyboardFocusHere();
                }
                if (host.inputIdentifierWithSuggestions("identifier", purposeHint, input, suggestions)) {
                    String entered = input.get().trim();
                    values.set(index, entered);
                    if (entered.startsWith("#") && values.size() > 1) {
                        values = new ArrayList<>(List.of(entered));
                        buffers.keySet().removeIf(key -> key.startsWith(path + "/@identifier/"));
                        if (focusState != null) focusState.requestRow(0);
                    }
                    changed = true;
                }
                if (ImGui.isItemHovered()) {
                    EditorUi.setTooltip(tr("component_form.syntax_example", fallback));
                }

                ImGui.tableNextColumn();
                ImGui.beginDisabled(values.size() == 1);
                if (EditorIcons.smallButton("remove", EditorIcons.Icon.REMOVE, removeLabel)) {
                    remove = index;
                }
                ImGui.endDisabled();
                if (values.size() == 1 && ImGui.isItemHovered()) {
                    EditorUi.setTooltip(tr("collection.requires_one"));
                }
                ImGui.endTable();
            }
            ImGui.popID();
        }

        if (remove >= 0) {
            values.remove(remove);
            buffers.keySet().removeIf(key -> key.startsWith(path + "/@identifier/"));
            if (focusState != null) focusState.requestRow(Math.min(remove, values.size() - 1));
            changed = true;
        }

        boolean usesTag = values.stream().anyMatch(value -> value.startsWith("#"));
        ImGui.beginDisabled(usesTag);
        if (EditorIcons.smallButton(
            "add_" + id,
            EditorIcons.Icon.ADD,
            tr("action.add", entryLabel)
        )) {
            values.add(fallback);
            if (focusState != null) focusState.requestRow(values.size() - 1);
            changed = true;
        }
        ImGui.endDisabled();
        if (usesTag && ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("collection.tag_is_collection"));
        }

        if (changed) {
            setter.accept(encode(values));
        }
        return changed;
    }

    /**
     * Carries a structural focus request across frames for one identifier collection.
     */
    public static final class FocusState {
        private int requestedRow = -1;

        public void requestRow(int index) {
            requestedRow = Math.max(0, index);
        }

        public void clear() {
            requestedRow = -1;
        }

        boolean consumeRow(int index) {
            if (requestedRow != index) return false;
            requestedRow = -1;
            return true;
        }
    }

    private static List<String> readValues(JsonElement current) {
        List<String> values = new ArrayList<>();
        if (current == null || current.isJsonNull()) {
            return values;
        }
        if (current.isJsonPrimitive()) {
            values.add(current.getAsString());
            return values;
        }
        if (current.isJsonArray()) {
            for (JsonElement entry : current.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    values.add(entry.getAsString());
                }
            }
        }
        return values;
    }

    private static JsonElement encode(List<String> values) {
        if (values.size() == 1) {
            return new JsonPrimitive(values.getFirst());
        }
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
