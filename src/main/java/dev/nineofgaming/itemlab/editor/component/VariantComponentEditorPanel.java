package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nineofgaming.itemlab.editor.EditorNavigationGuard;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;

import java.util.List;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Shared Components-tab editor for fixed and registry-backed entity variants.
 */
public final class VariantComponentEditorPanel {
    private final ItemEditor host;
    private final ImString value = new ImString(512);
    private EditorCatalog catalog;
    private ComponentOption selected;
    private String loadedValue = "";
    private List<IdSuggestion> suggestions = List.of();
    private boolean focusValueInput;

    public void requestPendingFocus() {
        focusValueInput = true;
    }

    public VariantComponentEditorPanel(ItemEditor host) {
        this.host = host;
    }

    public void setCatalog(EditorCatalog catalog) {
        this.catalog = catalog;
        refreshSuggestions();
    }

    public void clear() {
        selected = null;
        loadedValue = "";
        value.clear();
        suggestions = List.of();
        focusValueInput = false;
    }

    public void sync(ComponentOption option, JsonElement encoded) {
        if (option == null || !VariantComponents.supports(option.id())
            || encoded == null || !encoded.isJsonPrimitive()
            || !encoded.getAsJsonPrimitive().isString()) {
            clear();
            return;
        }
        selected = option;
        loadedValue = encoded.getAsString();
        value.set(loadedValue);
        refreshSuggestions();
        focusValueInput = true;
        host.resetAutocomplete();
    }

    public boolean isDirty() {
        return selected != null && !value.get().trim().equals(loadedValue);
    }

    public EditorNavigationGuard.Blocker pendingNavigationBlocker(Minecraft client) {
        if (!isDirty()) return null;
        String problem = problem(client);
        boolean invalid = !problem.isBlank();
        return new EditorNavigationGuard.Blocker(
            tr("tab.components"),
            "components",
            invalid
                ? EditorNavigationGuard.PendingState.INVALID
                : EditorNavigationGuard.PendingState.VALID,
            invalid
                ? tr("navigation.invalid", tr("tab.components"), problem)
                : tr("navigation.pending", tr("tab.components")),
            new EditorNavigationGuard.FocusRequest("variant_value")
        );
    }

    public void draw(Minecraft client) {
        if (selected == null) return;
        String problem = problem(client);
        boolean present = host.draft().has(selected.type());
        boolean pending = isDirty();
        EditorUi.drawPrimaryDiscardActions(
            present ? tr("action.save_changes") : tr("component_form.add_component"),
            (pending || !present) && problem.isBlank(),
            () -> save(client, true),
            pending,
            this::discard
        );

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        if (!EditorUi.beginCard("variant_component", 1, ImGuiTableFlags.SizingStretchProp)) return;
        ImGui.tableNextColumn();
        VariantComponents.Definition definition = VariantComponents.definition(selected.id());
        ImGui.text(definition.label());
        EditorUi.textDisabledWrapped(tr(definition.registryBacked()
            ? "variant.registry_help"
            : "variant.fixed_help"));
        ImGui.spacing();
        if (focusValueInput) {
            ImGui.setKeyboardFocusHere();
            focusValueInput = false;
        }
        host.inputIdentifierWithSuggestions(
            "variant_value",
            new FieldHints(
                tr("field_purpose.variant_value"),
                definition.fallbackValue()
            ),
            value,
            suggestions,
            problem
        );
        if (host.wasLastIdentifierInputSubmitted()
            && (isDirty() || !present)
            && problem(client).isBlank()) {
            save(client, true);
        }
        String candidate = value.get().trim();
        boolean available = suggestions.stream().anyMatch(suggestion -> suggestion.id().equals(candidate));
        if (!candidate.isBlank() && !available) {
            ItemEditorTheme.drawFieldWarning(tr("variant.unavailable", candidate));
        }
        ImGui.textDisabled(tr("variant.serialized_value", candidate));
        EditorUi.endCard();
    }

    public boolean save(Minecraft client, boolean announce) {
        if (selected == null || !isDirty() && host.draft().has(selected.type())) return true;
        String problem = problem(client);
        if (!problem.isBlank()) {
            host.setStatus(false, problem);
            return false;
        }
        return host.setSelectedComponent(client, new JsonPrimitive(value.get().trim()), announce);
    }

    public DataComponentType<?> pendingType() {
        return selected == null ? null : selected.type();
    }

    public JsonElement pendingValue(Minecraft client) {
        if (selected == null) {
            throw new IllegalStateException("No variant component is selected");
        }
        String problem = problem(client);
        if (!problem.isBlank()) throw new IllegalArgumentException(problem);
        return new JsonPrimitive(value.get().trim());
    }

    public void restorePending(JsonElement state) {
        if (selected == null || state == null || !state.isJsonPrimitive()
            || !state.getAsJsonPrimitive().isString()) {
            return;
        }
        value.set(state.getAsString());
        focusValueInput = true;
        host.resetAutocomplete();
    }

    private void discard() {
        value.set(loadedValue);
        host.resetAutocomplete();
    }

    private String problem(Minecraft client) {
        String candidate = value.get().trim();
        if (candidate.isEmpty()) return tr("validation.identifier_required");
        DataComponentType<?> type = selected.type();
        return ComponentValueFactory.accepts(
            client,
            host.draft(),
            type,
            new JsonPrimitive(candidate)
        ) ? "" : tr("variant.invalid", candidate);
    }

    private void refreshSuggestions() {
        suggestions = selected == null || catalog == null
            ? List.of()
            : VariantComponents.suggestions(selected.id(), catalog);
    }
}
