package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.list.JsonListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.sound.SoundPreviewKind;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;

/**
 * Typed controls and local edit state for the component selected in the browser.
 */
public final class ComponentFormPanel {
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong();
    private static final List<IdSuggestion> DYE_COLORS = literalSuggestions(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );
    private static final List<IdSuggestion> FIREWORK_SHAPES = literalSuggestions(
        "small_ball", "large_ball", "star", "creeper", "burst"
    );
    private static final List<IdSuggestion> SWING_ANIMATIONS = literalSuggestions("none", "whack", "stab");
    private static final Map<String, List<IdSuggestion>> COMPONENT_LITERAL_SUGGESTIONS = Map.ofEntries(
        Map.entry("minecraft:dye", DYE_COLORS),
        Map.entry("minecraft:base_color", DYE_COLORS),
        Map.entry("minecraft:wolf/collar", DYE_COLORS),
        Map.entry("minecraft:tropical_fish/base_color", DYE_COLORS),
        Map.entry("minecraft:tropical_fish/pattern_color", DYE_COLORS),
        Map.entry("minecraft:cat/collar", DYE_COLORS),
        Map.entry("minecraft:sheep/color", DYE_COLORS),
        Map.entry("minecraft:shulker/color", DYE_COLORS)
    );

    private final ItemEditor host;
    private final ImString scalar = new ImString(8_192);
    private final ImBoolean booleanValue = new ImBoolean(false);
    private final Map<String, ImString> fieldBuffers = new HashMap<>();
    private final Map<String, String> syntaxExamples = new HashMap<>();
    private final Map<String, JsonElement> inferredFieldStarters = new HashMap<>();
    private final Set<String> unavailableFieldStarters = new HashSet<>();
    private final Map<String, Boolean> removableFields = new HashMap<>();
    private final Map<String, String> fieldProblems = new HashMap<>();
    private final Map<String, List<Long>> arrayRowIds = new HashMap<>();
    private OrderedListDragDrop.Scope dragSessionScope = OrderedListDragDrop.newScope("generated");
    private JsonElement value = new JsonObject();
    private JsonElement loadedValue = new JsonObject();
    private List<IdSuggestion> itemIds = List.of();
    private List<IdSuggestion> enchantmentIds = List.of();
    private List<IdSuggestion> attributeIds = List.of();
    private List<IdSuggestion> soundIds = List.of();
    private List<IdSuggestion> entityTypeIds = List.of();
    private List<IdSuggestion> mobEffectIds = List.of();
    private Map<String, List<IdSuggestion>> registryIdsByKind = Map.of();
    private final Map<String, List<IdSuggestion>> suggestionsByExistingValue = new HashMap<>();
    private String problem = "";
    private String liveCodecProblem = "";
    private boolean dirty;
    private long focusRowId;
    private String focusAddListPath;
    private String focusFieldPath;

    public ComponentFormPanel(ItemEditor host) {
        this.host = host;
    }

    public void setCatalog(EditorCatalog catalog) {
        itemIds = catalog.itemIds();
        enchantmentIds = catalog.enchantmentIds();
        attributeIds = catalog.attributeIds();
        soundIds = catalog.soundIds();
        entityTypeIds = catalog.entityTypeIds();
        mobEffectIds = catalog.mobEffectIds();
        registryIdsByKind = catalog.registryIdsByKind();
        suggestionsByExistingValue.clear();
    }

    public void clear() {
        value = new JsonObject();
        loadedValue = new JsonObject();
        clearWorkingState();
    }

    private void clearWorkingState() {
        scalar.clear();
        booleanValue.set(false);
        fieldBuffers.clear();
        syntaxExamples.clear();
        inferredFieldStarters.clear();
        unavailableFieldStarters.clear();
        removableFields.clear();
        fieldProblems.clear();
        arrayRowIds.clear();
        dragSessionScope = OrderedListDragDrop.newScope("generated");
        focusRowId = 0L;
        focusAddListPath = null;
        focusFieldPath = null;
        problem = "";
        liveCodecProblem = "";
        dirty = false;
    }

    public void load(JsonElement encoded) {
        loadedValue = encoded.deepCopy();
        loadWorkingCopy(loadedValue);
    }

    public void discardChanges() {
        loadWorkingCopy(loadedValue);
    }

    private void loadWorkingCopy(JsonElement source) {
        clearWorkingState();
        value = source.deepCopy();
        if (!value.isJsonPrimitive()) {
            return;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            booleanValue.set(primitive.getAsBoolean());
        } else {
            scalar.set(primitive.getAsString());
        }
    }

    public boolean isDirty() {
        if (dirty && problem.isBlank() && fieldProblems.isEmpty() && liveCodecProblem.isBlank()) {
            dirty = !Objects.equals(value, loadedValue);
        }
        return dirty;
    }

    public String problem() {
        if (!problem.isBlank()) return problem;
        return fieldProblems.entrySet().stream().min(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue).orElse("");
    }

    public JsonElement value() {
        return value;
    }

    public JsonElement pendingValue() {
        return value.deepCopy();
    }

    /**
     * Unlike history capture, saving must not use the last valid value behind invalid text.
     */
    public JsonElement validatedPendingValue() {
        String localProblem = problem();
        if (!localProblem.isBlank()) throw new IllegalArgumentException(localProblem);
        return pendingValue();
    }

    public void requestPendingFocus() {
        focusFieldPath = fieldProblems.keySet().stream().sorted().findFirst().orElse("$");
    }

    private void applyPendingFieldFocus(String path) {
        if (focusFieldPath == null || !focusFieldPath.equals("$") && !focusFieldPath.equals(path)) return;
        ImGui.setScrollHereY(0.25f);
        ImGui.setKeyboardFocusHere();
        focusFieldPath = null;
    }

    private boolean pendingFocusWithin(String path) {
        return focusFieldPath != null && (focusFieldPath.equals("$")
            || focusFieldPath.equals(path) || focusFieldPath.startsWith(path + "/"));
    }

    public void restorePending(JsonElement baseline, JsonElement state) {
        load(baseline);
        loadWorkingCopy(state);
        dirty = !Objects.equals(value, loadedValue);
    }

    public void draw(Minecraft client) {
        ComponentOption selected = host.selectedComponent();
        if (selected == null || selected.type().codec() == null) {
            return;
        }
        liveCodecProblem = validateCurrentValue(client, selected.type());
        drawActions(client);
        ItemEditorTheme.drawFieldProblem(liveCodecProblem.isBlank()
            ? ""
            : tr("component_form.invalid", liveCodecProblem));
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        try {
            drawGeneric(client, selected.type());
        } catch (RuntimeException exception) {
            ImGui.textWrapped(tr("component_form.read_error", EditorUi.readableMessage(exception)));
        }
    }

    private void drawGeneric(Minecraft client, DataComponentType<?> type) {
        boolean changed = false;
        String componentId = host.selectedComponent().id();
        if (isUnitComponent(type)) {
            if (EditorUi.beginCard("generic_component_fields", 1, ImGuiTableFlags.SizingStretchProp)) {
                ImGui.tableNextColumn();
                ImGui.textDisabled(tr("component_form.no_fields"));
                EditorUi.endCard();
            }
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (beginPropertyTable("generic_component_fields")) {
                List<ComponentSchemas.Field> missingFields = ComponentSchemas.missingFields(componentId, "$", object, type);
                if (object.isEmpty() && missingFields.isEmpty() && !ComponentSchemas.allowsCustomFields(componentId, "$")) {
                    ImGui.tableNextColumn();
                    ImGui.textDisabled(tr("component_form.no_fields"));
                    ImGui.tableNextColumn();
                }
                for (Map.Entry<String, JsonElement> entry : new ArrayList<>(object.entrySet())) {
                    String key = entry.getKey();
                    ImGui.pushID(key);
                    ComponentFieldContext context = fieldContext(client, type, "$", key);
                    propertyLabel(context);
                    changed |= drawPropertyValue(
                        client,
                        context,
                        entry.getValue(),
                        replacement -> object.add(key, replacement)
                    );
                    if (context.removable()
                        && EditorIcons.smallButton(
                        "remove_field",
                        EditorIcons.Icon.REMOVE,
                        tr("component_form.remove_field")
                    )) {
                        object.remove(key);
                        clearBuffers("$/" + key);
                        changed = true;
                    }
                    ImGui.popID();
                }
                if (!missingFields.isEmpty()) {
                    propertyLabel(tr("component_form.add_properties"), tr("component_form.add_properties.help"));
                    changed |= drawMissingFields(client, type, object, "$", missingFields);
                }
                if (ComponentSchemas.allowsCustomFields(componentId, "$")) {
                    propertyLabel(tr("component_form.custom_field"), tr("component_form.custom_field.help"));
                    changed |= drawCustomFieldAdder(object, "$", componentId);
                }
                EditorUi.endCard();
            }
        } else if (beginPropertyTable("generic_component_fields")) {
            ComponentFieldContext context = rootValueContext(type);
            propertyLabel(context);
            if (value.isJsonPrimitive()
                && ComponentSchemas.identifierCollection(componentId, "$") == null) {
                drawPrimitive(type);
            } else {
                changed = drawPropertyValue(client, context, value, replacement -> value = replacement);
            }
            EditorUi.endCard();
        }
        if (changed) {
            dirty = true;
        }
    }

    private void drawPrimitive(DataComponentType<?> type) {
        applyPendingFieldFocus("$");
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            if (ImGui.checkbox(
                tr(booleanValue.get() ? "component_form.boolean.enabled" : "component_form.boolean.disabled")
                    + "##component_boolean",
                booleanValue
            )) {
                value = new JsonPrimitive(booleanValue.get());
                markChanged();
            }
            return;
        }
        if (primitive.isNumber()) {
            ComponentFieldContext context = rootValueContext(type);
            String fieldProblem = numberProblem(scalar.get());
            if (!fieldProblem.isBlank()) {
                ItemEditorTheme.pushInvalidField();
            }
            ImGui.setNextItemWidth(-1.0f);
            if (inputTextWithHint(
                "##component_number",
                context.purposeHint(),
                scalar
            )) {
                dirty = true;
                try {
                    JsonElement parsed = JsonParser.parseString(scalar.get());
                    if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isNumber()) {
                        value = parsed;
                        problem = "";
                    } else {
                        problem = tr("validation.number");
                    }
                } catch (RuntimeException exception) {
                    problem = tr("validation.number");
                }
            }
            if (!fieldProblem.isBlank()) {
                ItemEditorTheme.popInvalidField();
            }
            drawSyntaxExample(ComponentFieldExamples.number(host.selectedComponent().id(), "$", "$"));
            ItemEditorTheme.drawFieldProblem(numberProblem(scalar.get()));
            return;
        }
        ComponentFieldContext context = rootValueContext(type);
        List<IdSuggestion> suggestions = context.registrySuggestions();
        if (suggestions.isEmpty() && primitive.isString()) {
            suggestions = suggestionsForExistingValue(primitive.getAsString());
        }
        ComponentOption selected = host.selectedComponent();
        List<IdSuggestion> fixedChoices = selected == null
            ? List.of()
            : literalSuggestionsForComponent(selected.id());
        if (!fixedChoices.isEmpty()) {
            if (drawFixedChoices(fixedChoices)) {
                value = new JsonPrimitive(scalar.get());
                markChanged();
            }
            return;
        }
        SoundPreviewKind previewKind = primitive.isString() ? previewKindFor(type, "Value", "$") : null;
        boolean changed;
        if (suggestions.isEmpty()) {
            if (!liveCodecProblem.isBlank()) {
                ItemEditorTheme.pushInvalidField();
            }
            changed = previewKind == null
                ? inputFullWidthText("##component_string", context.purposeHint(), scalar)
                : host.inputSoundWithPreview(
                Minecraft.getInstance(),
                "component_string",
                context.purposeHint(),
                scalar,
                List.of(),
                previewKind,
                liveCodecProblem
            );
            if (!liveCodecProblem.isBlank()) {
                ItemEditorTheme.popInvalidField();
            }
        } else if (previewKind != null) {
            changed = host.inputSoundWithPreview(
                Minecraft.getInstance(),
                "component_string",
                context.purposeHint(),
                scalar,
                suggestions,
                previewKind,
                liveCodecProblem
            );
        } else {
            changed = host.inputIdentifierWithSuggestions(
                "component_string",
                context.purposeHint(),
                scalar,
                suggestions,
                liveCodecProblem
            );
        }
        if (changed) {
            value = new JsonPrimitive(scalar.get());
            markChanged();
        }
        drawSyntaxExample(context.syntaxExample());
    }

    private boolean drawFixedChoices(List<IdSuggestion> choices) {
        float availableWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float spacing = ImGui.getStyle().getItemSpacingX();
        float usedWidth = 0.0f;
        boolean changed = false;
        for (int index = 0; index < choices.size(); index++) {
            IdSuggestion choice = choices.get(index);
            String label = humanize(choice.displayName());
            float width = ImGui.calcTextSizeX(label) + ImGui.getStyle().getFramePaddingX() * 2.0f;
            if (usedWidth > 0.0f && usedWidth + spacing + width <= availableWidth) {
                ImGui.sameLine();
                usedWidth += spacing;
            } else if (usedWidth > 0.0f) {
                usedWidth = 0.0f;
            }
            boolean selected = choice.id().equals(scalar.get());
            if (selected) ItemEditorTheme.pushActiveFilter();
            if (ImGui.button(label + "##component_choice_" + index)) {
                scalar.set(choice.id());
                changed = true;
            }
            if (selected) ItemEditorTheme.popActiveFilter();
            usedWidth += width;
        }
        return changed;
    }

    private boolean beginPropertyTable(String id) {
        float labelWidth = Math.clamp(ImGui.getContentRegionAvailX() * 0.36f, 120.0f, 180.0f);
        if (!EditorUi.beginCard(id, 2, ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.BordersInnerV,
            focusFieldPath != null)) {
            return false;
        }
        ImGui.tableSetupColumn("Property", ImGuiTableColumnFlags.WidthFixed, labelWidth);
        ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch);
        return true;
    }

    private boolean beginNestedPropertyTable(String id) {
        float labelWidth = Math.clamp(ImGui.getContentRegionAvailX() * 0.36f, 104.0f, 168.0f);
        if (!ImGui.beginTable(
            id,
            2,
            ImGuiTableFlags.SizingStretchProp
                | ImGuiTableFlags.BordersInnerV
                | ImGuiTableFlags.RowBg
                | ImGuiTableFlags.NoPadOuterX
        )) {
            return false;
        }
        ImGui.tableSetupColumn("Property", ImGuiTableColumnFlags.WidthFixed, labelWidth);
        ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch);
        return true;
    }

    private static void propertyLabel(String label, String description) {
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.textWrapped(label);
        if (ImGui.isItemHovered() && !description.isBlank()) {
            EditorUi.setTooltip(description);
        }
        ImGui.tableNextColumn();
    }

    private static void propertyLabel(ComponentFieldContext context) {
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.textWrapped(context.displayLabel());
        ImGui.textDisabled(tr("component_form.state." + context.stateLabel()));
        if (!context.description().isBlank()) {
            EditorUi.textDisabledWrapped(context.description());
        }
        ImGui.tableNextColumn();
    }

    private boolean drawPropertyValue(
        Minecraft client,
        ComponentFieldContext context,
        JsonElement current,
        Consumer<JsonElement> setter
    ) {
        String label = context.displayLabel();
        String path = context.jsonPath();
        String uiPath = context.uiPath();
        if (current == null || current.isJsonNull()) {
            ImGui.textWrapped(tr("component_form.not_set", humanize(label)));
            return false;
        }
        ComponentSchemas.IdentifierCollection identifierCollection = ComponentSchemas.identifierCollection(
            host.selectedComponent().id(),
            path
        );
        if (identifierCollection != null) {
            applyPendingFieldFocus(uiPath);
            return IdentifierCollectionEditor.draw(
                host,
                "collection",
                uiPath,
                current,
                setter,
                idsAndTags(identifierCollection.registryKind()),
                purposeHint(
                    context.componentId(),
                    context.jsonPath(),
                    context.fieldKey(),
                    identifierCollection.purposeHintKey(),
                    idsAndTags(identifierCollection.registryKind())
                ),
                identifierCollection.fallbackId(),
                identifierCollection.entryLabel(),
                fieldBuffers
            );
        }
        if (current.isJsonObject()) {
            return drawObjectValue(client, context, current.getAsJsonObject());
        }
        if (current.isJsonArray()) {
            return drawListValue(client, context, current.getAsJsonArray());
        }
        return drawScalarValue(context, current.getAsJsonPrimitive(), setter);
    }

    private boolean drawObjectValue(
        Minecraft client,
        ComponentFieldContext context,
        JsonObject object
    ) {
        DataComponentType<?> type = context.componentType();
        String path = context.jsonPath();
        String componentId = context.componentId();
        boolean changed = false;
        if (!EditorUi.beginCollapsibleCard(
            "generated_object_" + context.stableId(),
            tr("component_form.object_properties"),
            ImGuiTableFlags.SizingStretchProp,
            true,
            pendingFocusWithin(context.uiPath())
        )) return false;
        if (beginNestedPropertyTable("object_fields_" + context.stableId())) {
            List<ComponentSchemas.Field> missingFields = ComponentSchemas.missingFields(componentId, path, object, type);
            if (object.isEmpty() && missingFields.isEmpty() && !ComponentSchemas.allowsCustomFields(componentId, path)) {
                ImGui.tableNextColumn();
                ImGui.textDisabled(tr("component_form.default_values"));
                ImGui.tableNextColumn();
            }
            for (Map.Entry<String, JsonElement> entry : new ArrayList<>(object.entrySet())) {
                String key = entry.getKey();
                ImGui.pushID(key);
                ComponentFieldContext child = fieldContext(
                    client,
                    type,
                    path,
                    context.uiPath(),
                    context.uiScope(),
                    key
                );
                propertyLabel(child);
                changed |= drawPropertyValue(
                    client,
                    child,
                    entry.getValue(),
                    replacement -> object.add(key, replacement)
                );
                if (child.removable() && EditorIcons.smallButton(
                    "remove_field",
                    EditorIcons.Icon.REMOVE,
                    tr("component_form.remove_field")
                )) {
                    object.remove(key);
                    clearBuffers(child.uiPath());
                    changed = true;
                }
                ImGui.popID();
            }
            if (!missingFields.isEmpty()) {
                propertyLabel(tr("component_form.add_properties"), tr("component_form.add_properties.help"));
                changed |= drawMissingFields(client, type, object, path, missingFields);
            }
            if (ComponentSchemas.allowsCustomFields(componentId, path)) {
                propertyLabel(tr("component_form.custom_field"), tr("component_form.custom_field.help"));
                changed |= drawCustomFieldAdder(object, context.uiPath(), componentId);
            }
            ImGui.endTable();
        }
        EditorUi.endCard();
        return changed;
    }

    private boolean drawListValue(
        Minecraft client,
        ComponentFieldContext context,
        JsonArray array
    ) {
        DataComponentType<?> type = context.componentType();
        String path = context.jsonPath();
        String uiPath = context.uiPath();
        OrderedListDragDrop.Scope dragScope = context.uiScope();
        ComponentSchemas.ArrayEntry entrySchema = ComponentSchemas.arrayEntry(context.componentId(), path);
        List<Long> rowIds = rowIds(uiPath, array.size());
        JsonElement newEntry = arrayEntryStarter(client, context, array, entrySchema);
        String addLabel = entrySchema == null ? "entry" : entrySchema.label();
        boolean changed = drawAddArrayEntryAction(
            uiPath,
            addLabel,
            array,
            rowIds,
            newEntry,
            "top",
            false
        );
        if (newEntry != null) ImGui.spacing();
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < array.size(); index++) {
            int currentIndex = index;
            long rowId = rowIds.get(index);
            ImGui.pushID("generated_array_row_" + rowId);
            String entryLabel = entrySchema == null ? tr("component_form.entry", index + 1)
                : tr("component_form.named_entry", humanize(entrySchema.label()), index + 1);
            boolean requestFocus = focusRowId == rowId
                || pendingFocusWithin(uiPath + "/" + rowId);
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "array_entry",
                EditorUi.identifiedTitle(entryLabel, EditorUi.jsonSummary(array.get(index))),
                ImGuiTableFlags.SizingStretchProp,
                index,
                array.size(),
                true,
                true,
                newEntry != null,
                requestFocus,
                dragScope,
                rowId
            );
            if (card.focusApplied()) focusRowId = 0L;
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = card.action();
            }
            if (card.expanded()) {
                ComponentFieldContext entryContext = valueContext(
                    type,
                    path + "/" + index,
                    uiPath + "/" + rowId,
                    entryLabel,
                    dragScope.nested(rowId, "entry")
                );
                changed |= drawPropertyValue(
                    client,
                    entryContext,
                    array.get(index),
                    replacement -> array.set(currentIndex, replacement)
                );
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                dragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        if (reorderRequest != null || actionIndex >= 0) {
            long removedRowId = reorderRequest == null && action == EditorUi.ListAction.REMOVE
                ? rowIds.get(actionIndex)
                : 0L;
            JsonListMutations.Mutation mutation = reorderRequest != null
                ? dragScope.equals(reorderRequest.scope())
                ? JsonListMutations.reorder(array, rowIds, reorderRequest)
                : new JsonListMutations.Mutation(false, 0L)
                : newEntry == null
                ? JsonListMutations.apply(
                array,
                rowIds,
                actionIndex,
                action,
                NEXT_ROW_ID::incrementAndGet
            )
                : JsonListMutations.apply(
                array,
                rowIds,
                actionIndex,
                action,
                NEXT_ROW_ID::incrementAndGet,
                newEntry::deepCopy
            );
            if (mutation.changed()) {
                focusRowId = mutation.focusRowId();
                focusAddListPath = mutation.focusRowId() == 0L ? uiPath : null;
                if (removedRowId != 0L) clearBuffers(uiPath + "/" + removedRowId);
                changed = true;
            }
        }
        if (array.isEmpty()) {
            EditorUi.textDisabledWrapped(newEntry == null
                ? tr("component_form.no_editable_entries")
                : tr("component_form.no_entries"));
        }
        changed |= drawAddArrayEntryAction(
            uiPath,
            addLabel,
            array,
            rowIds,
            newEntry,
            "bottom",
            true
        );
        OrderedListDragDrop.autoScrollCurrentWindow(dragScope);
        return changed;
    }

    private JsonElement arrayEntryStarter(
        Minecraft client,
        ComponentFieldContext context,
        JsonArray array,
        ComponentSchemas.ArrayEntry entrySchema
    ) {
        if (entrySchema != null) {
            JsonElement schemaEntry = entrySchema.newValue();
            if (schemaEntry != null) return schemaEntry;
        }
        if (!array.isEmpty()) return array.get(array.size() - 1).deepCopy();
        if (entrySchema != null) return null;

        String path = context.jsonPath();
        String cacheKey = context.componentId() + "@" + path + "/@entry";
        JsonElement inferredEntry = inferredFieldStarters.get(cacheKey);
        if (inferredEntry == null && !unavailableFieldStarters.contains(cacheKey)) {
            inferredEntry = inferArrayEntry(client, context.componentType(), path);
            if (inferredEntry == null) {
                unavailableFieldStarters.add(cacheKey);
            } else {
                inferredFieldStarters.put(cacheKey, inferredEntry.deepCopy());
            }
        }
        return inferredEntry;
    }

    private boolean drawAddArrayEntryAction(
        String path,
        String addLabel,
        JsonArray array,
        List<Long> rowIds,
        JsonElement newEntry,
        String placement,
        boolean acceptFocus
    ) {
        if (newEntry == null) return false;
        if (acceptFocus && path.equals(focusAddListPath)) {
            ImGui.setKeyboardFocusHere();
            focusAddListPath = null;
        }
        if (!EditorIcons.smallButton(
            "add_array_entry_" + path + "_" + placement,
            EditorIcons.Icon.ADD,
            tr("action.add", addLabel)
        )) return false;

        array.add(newEntry.deepCopy());
        long addedId = NEXT_ROW_ID.incrementAndGet();
        rowIds.add(addedId);
        focusRowId = addedId;
        focusAddListPath = null;
        return true;
    }

    private boolean drawScalarValue(
        ComponentFieldContext context,
        JsonPrimitive primitive,
        Consumer<JsonElement> setter
    ) {
        applyPendingFieldFocus(context.uiPath());
        DataComponentType<?> type = context.componentType();
        String label = context.displayLabel();
        String path = context.jsonPath();
        String uiPath = context.uiPath();
        if (primitive.isBoolean()) {
            ImBoolean field = new ImBoolean(primitive.getAsBoolean());
            if (ImGui.checkbox(
                tr(field.get() ? "component_form.boolean.enabled" : "component_form.boolean.disabled")
                    + "##boolean",
                field
            )) {
                setter.accept(new JsonPrimitive(field.get()));
                return true;
            }
            return false;
        }

        ImString field = fieldBuffers.computeIfAbsent(uiPath,
            ignored -> new ImString(primitive.getAsString(), Math.max(512, primitive.getAsString().length() * 2 + 64)));
        List<IdSuggestion> suggestions = context.registrySuggestions();
        if (suggestions.isEmpty() && primitive.isString()) {
            suggestions = suggestionsForExistingValue(primitive.getAsString());
        }
        if (!primitive.isString() || suggestions.isEmpty() && !looksLikeIdentifierField(label, path, field.get())) {
            suggestions = List.of();
        }
        SoundPreviewKind previewKind = primitive.isString() ? previewKindFor(type, label, path) : null;
        String localProblem = fieldProblems.getOrDefault(uiPath, "");
        String example = primitive.isNumber() && context.syntaxExample().equals("example")
            ? ComponentFieldExamples.number(context.componentId(), path, context.fieldKey())
            : context.syntaxExample();
        if (!localProblem.isBlank()) ItemEditorTheme.pushInvalidField();
        boolean changed;
        if (previewKind != null) {
            changed = host.inputSoundWithPreview(
                Minecraft.getInstance(),
                "value",
                context.purposeHint(),
                field,
                suggestions,
                previewKind
            );
        } else {
            changed = suggestions.isEmpty()
                ? inputFullWidthText("##value", context.purposeHint(), field)
                : host.inputIdentifierWithSuggestions("value", context.purposeHint(), field, suggestions);
        }
        if (!localProblem.isBlank()) ItemEditorTheme.popInvalidField();
        drawSyntaxExample(example);
        if (!changed) {
            ItemEditorTheme.drawFieldProblem(localProblem);
            return false;
        }
        if (primitive.isNumber()) {
            dirty = true;
            try {
                JsonElement parsed = JsonParser.parseString(field.get());
                if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isNumber()) {
                    setter.accept(parsed);
                    fieldProblems.remove(uiPath);
                    ItemEditorTheme.drawFieldProblem("");
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Keep the previous valid number until this field is valid again.
            }
            fieldProblems.put(uiPath, tr("component_form.number_invalid", context.displayLabel()));
            ItemEditorTheme.drawFieldProblem(fieldProblems.get(uiPath));
            return false;
        }
        fieldProblems.remove(uiPath);
        setter.accept(new JsonPrimitive(field.get()));
        ItemEditorTheme.drawFieldProblem("");
        return true;
    }

    private boolean isFieldRemovable(
        Minecraft client,
        DataComponentType<?> type,
        String componentId,
        String path,
        String fieldName
    ) {
        if (ComponentSchemas.isRequired(componentId, path, fieldName)) {
            return false;
        }
        if (ComponentSchemas.isRemovable(componentId, path, fieldName)) {
            return true;
        }
        String cacheKey = componentId + "@" + path + "/" + fieldName;
        return removableFields.computeIfAbsent(cacheKey, ignored -> {
            JsonElement testValue = value.deepCopy();
            JsonElement owner = elementAt(testValue, path);
            if (owner == null || !owner.isJsonObject()) {
                return false;
            }
            owner.getAsJsonObject().remove(fieldName);
            return ComponentValueFactory.accepts(client, host.draft(), type, testValue);
        });
    }

    private boolean drawMissingFields(
        Minecraft client,
        DataComponentType<?> type,
        JsonObject object,
        String path,
        List<ComponentSchemas.Field> fields
    ) {
        boolean changed = false;
        for (ComponentSchemas.Field field : fields) {
            String cacheKey = host.selectedComponent().id() + "@" + path + "/" + field.key();
            JsonElement starter = inferredFieldStarters.get(cacheKey);
            if (starter == null && !unavailableFieldStarters.contains(cacheKey)) {
                starter = inferFieldStarter(client, type, object, path, field);
                if (starter == null) {
                    unavailableFieldStarters.add(cacheKey);
                } else {
                    inferredFieldStarters.put(cacheKey, starter.deepCopy());
                }
            }

            ImGui.pushID("missing_" + field.key());
            ImGui.textWrapped(field.label());
            String state = tr(field.required()
                ? "component_form.state.required"
                : "component_form.state.optional");
            EditorUi.sameLineIfFits(state);
            ImGui.textDisabled(state);
            EditorUi.textDisabledWrapped(field.description());
            if (starter == null) {
                EditorUi.textDisabledWrapped(tr("component_form.no_safe_starter"));
            }
            ImGui.beginDisabled(starter == null);
            if (ImGui.button(tr("action.add", field.label())) && starter != null) {
                object.add(field.key(), starter.deepCopy());
                inferredFieldStarters.remove(cacheKey);
                changed = true;
            }
            ImGui.endDisabled();
            ImGui.spacing();
            ImGui.popID();
        }
        return changed;
    }

    private JsonElement inferFieldStarter(
        Minecraft client,
        DataComponentType<?> type,
        JsonObject object,
        String path,
        ComponentSchemas.Field field
    ) {
        List<JsonElement> candidates = new ArrayList<>();
        JsonElement documentedDefault = field.newValue();
        if (documentedDefault != null) {
            candidates.add(documentedDefault);
        }
        JsonElement installedExample = ComponentValueFactory.installedFieldExample(client, type, field.key());
        if (installedExample != null) {
            candidates.add(installedExample);
        }

        List<IdSuggestion> fieldSuggestions = suggestionsForField(field.label(), path + "/" + field.key());
        for (int index = 0; index < Math.min(8, fieldSuggestions.size()); index++) {
            candidates.add(new JsonPrimitive(fieldSuggestions.get(index).id()));
        }

        String normalized = field.key().toLowerCase(Locale.ROOT);
        boolean mayBeRegisteredId = normalized.equals("id")
            || normalized.equals("type")
            || normalized.contains("asset")
            || normalized.contains("material")
            || normalized.contains("pattern")
            || normalized.contains("sound")
            || normalized.contains("variant")
            || normalized.contains("effect")
            || normalized.contains("item")
            || normalized.contains("block");
        if (fieldSuggestions.isEmpty() && mayBeRegisteredId) {
            Set<String> registryCandidates = new LinkedHashSet<>();
            for (List<IdSuggestion> registry : registryIdsByKind.values()) {
                if (!registry.isEmpty()) {
                    registryCandidates.add(registry.getFirst().id());
                    if (registryCandidates.size() >= 32) {
                        break;
                    }
                }
            }
            registryCandidates.forEach(id -> candidates.add(new JsonPrimitive(id)));
        }

        if (normalized.endsWith("s")
            || normalized.contains("effects")
            || normalized.contains("rules")
            || normalized.contains("pages")
            || normalized.contains("colors")
            || normalized.contains("predicates")) {
            candidates.add(new JsonArray());
        }
        if (normalized.startsWith("is_")
            || normalized.startsWith("has_")
            || normalized.startsWith("can_")
            || normalized.startsWith("show_")
            || normalized.contains("enabled")
            || normalized.contains("tracked")
            || normalized.contains("resolved")) {
            candidates.add(new JsonPrimitive(false));
        }
        if (normalized.contains("count")
            || normalized.contains("level")
            || normalized.contains("duration")
            || normalized.contains("damage")
            || normalized.contains("speed")
            || normalized.contains("scale")
            || normalized.contains("factor")
            || normalized.contains("angle")
            || normalized.contains("range")
            || normalized.contains("distance")
            || normalized.contains("ticks")) {
            candidates.add(new JsonPrimitive(0));
            candidates.add(new JsonPrimitive(1));
        }
        candidates.add(new JsonPrimitive(false));
        candidates.add(new JsonPrimitive(0));
        candidates.add(new JsonPrimitive(1));
        candidates.add(new JsonPrimitive(""));
        candidates.add(new JsonArray());
        candidates.add(new JsonObject());
        candidates.add(new JsonPrimitive("none"));
        candidates.add(new JsonPrimitive("default"));

        for (JsonElement candidate : candidates) {
            JsonObject testValue = object.deepCopy();
            testValue.add(field.key(), candidate.deepCopy());
            boolean accepted = field.discovered()
                ? ComponentValueFactory.preservesField(
                client,
                host.draft(),
                type,
                testValue,
                path,
                field.key()
            )
                : ComponentValueFactory.accepts(client, host.draft(), type, testValue);
            if (accepted) {
                return candidate;
            }
        }
        return null;
    }

    private JsonElement inferArrayEntry(Minecraft client, DataComponentType<?> type, String path) {
        List<JsonElement> candidates = new ArrayList<>();
        JsonElement installedExample = ComponentValueFactory.installedArrayEntryExample(client, type, path);
        if (installedExample != null) {
            candidates.add(installedExample);
        }
        List<IdSuggestion> suggestions = suggestionsForField("Entry", path);
        for (int index = 0; index < Math.min(8, suggestions.size()); index++) {
            candidates.add(new JsonPrimitive(suggestions.get(index).id()));
        }
        candidates.add(new JsonObject());
        candidates.add(new JsonPrimitive(""));
        candidates.add(new JsonPrimitive(0));
        candidates.add(new JsonPrimitive(1));
        candidates.add(new JsonPrimitive(false));
        candidates.add(new JsonArray());

        for (JsonElement candidate : candidates) {
            JsonElement testValue = value.deepCopy();
            JsonElement target = elementAt(testValue, path);
            if (target == null || !target.isJsonArray()) {
                continue;
            }
            target.getAsJsonArray().add(candidate.deepCopy());
            if (ComponentValueFactory.accepts(client, host.draft(), type, testValue)) {
                return candidate;
            }
        }
        return null;
    }

    private static JsonElement elementAt(JsonElement root, String path) {
        JsonElement current = root;
        if (path.equals("$")) {
            return current;
        }
        for (String segment : path.substring(2).split("/")) {
            if (current == null) {
                return null;
            }
            if (current.isJsonObject()) {
                current = current.getAsJsonObject().get(segment);
            } else if (current.isJsonArray()) {
                try {
                    int index = Integer.parseInt(segment);
                    current = index >= 0 && index < current.getAsJsonArray().size()
                        ? current.getAsJsonArray().get(index)
                        : null;
                } catch (NumberFormatException exception) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    private boolean drawCustomFieldAdder(JsonObject object, String path, String componentId) {
        String bufferPath = path + "/@new_field";
        ImString fieldName = stringBuffer(bufferPath, "");
        ImGui.setNextItemWidth(-1.0f);
        inputTextWithHint(
            "##custom_field_name",
            purposeHint(componentId, path + "/$key", "key", null, List.of()),
            fieldName
        );
        drawSyntaxExample(ComponentFieldExamples.customFieldKey(componentId));
        String key = fieldName.get().trim();
        boolean valid = !key.isEmpty() && !object.has(key);
        ItemEditorTheme.drawFieldProblem(!key.isEmpty() && object.has(key)
            ? tr("validation.key_duplicate")
            : "");
        JsonElement addedValue = null;
        JsonElement schemaValue = ComponentSchemas.customFieldValue(componentId);
        ImGui.beginDisabled(!valid);
        if (schemaValue != null && ImGui.button(tr("component_form.add_decoration"))) {
            addedValue = schemaValue;
        } else if (schemaValue == null && ImGui.button(tr("value_type.string"))) {
            addedValue = new JsonPrimitive("");
        }
        if (schemaValue == null) {
            EditorUi.sameLineIfFits(tr("value_type.number"));
            if (ImGui.button(tr("value_type.number"))) {
                addedValue = new JsonPrimitive(0);
            }
            EditorUi.sameLineIfFits(tr("value_type.boolean"));
            if (ImGui.button(tr("value_type.boolean"))) {
                addedValue = new JsonPrimitive(false);
            }
            EditorUi.sameLineIfFits(tr("value_type.object"));
            if (ImGui.button(tr("value_type.object"))) {
                addedValue = new JsonObject();
            }
            EditorUi.sameLineIfFits(tr("value_type.list"));
            if (ImGui.button(tr("value_type.list"))) {
                addedValue = new JsonArray();
            }
        }
        ImGui.endDisabled();
        if (addedValue == null) {
            return false;
        }
        object.add(key, addedValue);
        fieldBuffers.remove(bufferPath);
        return true;
    }

    private void drawActions(Minecraft client) {
        ComponentOption selected = host.selectedComponent();
        DataComponentType<?> type = selected.type();
        boolean pending = isDirty();
        boolean canSave = (pending || !host.draft().has(type))
            && problem.isBlank()
            && fieldProblems.isEmpty()
            && liveCodecProblem.isBlank();
        EditorUi.drawPrimaryDiscardActions(
            host.draft().has(type) ? tr("action.save_changes") : tr("component_form.add_component"),
            canSave,
            () -> host.setSelectedComponent(client, value),
            pending,
            () -> host.reloadSelectedComponent(client)
        );
    }

    private boolean isUnitComponent(DataComponentType<?> type) {
        if (host.draft().get(type) instanceof Unit || ComponentValueFactory.commonStarter(type) instanceof Unit) {
            return true;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDefaultInstance().get(type) instanceof Unit) {
                return true;
            }
        }
        return false;
    }

    private void markChanged() {
        dirty = true;
        problem = "";
    }

    private String validateCurrentValue(Minecraft client, DataComponentType<?> type) {
        try {
            ItemStack test = host.draft().copy();
            ItemStackCodecs.decodeAndSetComponent(client, test, type, value);
            ItemStackCodecs.validateStack(test);
            return "";
        } catch (RuntimeException exception) {
            return EditorUi.readableMessage(exception);
        }
    }

    static String numberProblem(String source) {
        try {
            JsonElement parsed = JsonParser.parseString(source);
            return parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isNumber()
                ? ""
                : tr("validation.number");
        } catch (RuntimeException exception) {
            return tr("validation.number");
        }
    }

    private ImString stringBuffer(String path, String initialValue) {
        return fieldBuffers.computeIfAbsent(path,
            ignored -> new ImString(initialValue, Math.max(512, initialValue.length() * 2 + 64)));
    }

    private void clearBuffers(String path) {
        fieldBuffers.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"));
        fieldProblems.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"));
        arrayRowIds.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"));
    }

    private ComponentFieldContext fieldContext(
        Minecraft client,
        DataComponentType<?> type,
        String ownerPath,
        String fieldKey
    ) {
        String componentId = host.selectedComponent().id();
        return fieldContext(
            client,
            type,
            ownerPath,
            ownerPath,
            dragSessionScope.child(componentId).child(ownerPath),
            fieldKey
        );
    }

    private ComponentFieldContext fieldContext(
        Minecraft client,
        DataComponentType<?> type,
        String ownerPath,
        String ownerUiPath,
        OrderedListDragDrop.Scope ownerUiScope,
        String fieldKey
    ) {
        String componentId = host.selectedComponent().id();
        String path = ownerPath.equals("$")
            ? "$/" + fieldKey
            : ownerPath + "/" + fieldKey;
        String uiPath = ownerUiPath.equals("$")
            ? "$/" + fieldKey
            : ownerUiPath + "/" + fieldKey;
        ComponentSchemas.Field schema = ComponentSchemas.field(componentId, ownerPath, fieldKey);
        String label = schema == null ? fieldKey : schema.label();
        String description = schema == null
            ? tr("component_form.discovered_help")
            : schema.description();
        boolean custom = schema == null && ComponentSchemas.allowsCustomFields(componentId, ownerPath);
        List<IdSuggestion> suggestions = suggestionsForField(label, path);
        return new ComponentFieldContext(
            componentId,
            type,
            path,
            uiPath,
            ownerUiScope.child(fieldKey),
            fieldKey,
            label,
            description,
            schema,
            suggestions,
            purposeHint(componentId, path, fieldKey,
                schema == null ? null : schema.purposeHintKey(), suggestions),
            syntaxExample(componentId, path, fieldKey,
                schema == null ? null : schema.syntaxExample(), suggestions),
            schema != null && schema.required(),
            isFieldRemovable(client, type, componentId, ownerPath, fieldKey),
            custom
        );
    }

    private ComponentFieldContext rootValueContext(DataComponentType<?> type) {
        String componentId = host.selectedComponent().id();
        List<IdSuggestion> suggestions = suggestionsForComponent(type);
        return new ComponentFieldContext(
            componentId,
            type,
            "$",
            "$",
            dragSessionScope.child(componentId).child("$"),
            "$",
            tr("component_form.value"),
            tr("component_form.value.help"),
            null,
            suggestions,
            purposeHint(componentId, "$", "$", null, suggestions),
            syntaxExample(componentId, "$", "$", null, suggestions),
            true,
            false,
            false
        );
    }

    private ComponentFieldContext valueContext(
        DataComponentType<?> type,
        String path,
        String uiPath,
        String label,
        OrderedListDragDrop.Scope uiScope
    ) {
        String componentId = host.selectedComponent().id();
        String ownerPath = path.substring(0, path.lastIndexOf('/'));
        ComponentSchemas.ArrayEntry entry = ComponentSchemas.arrayEntry(componentId, ownerPath);
        List<IdSuggestion> suggestions = suggestionsForField(label, path);
        return new ComponentFieldContext(
            componentId,
            type,
            path,
            uiPath,
            uiScope,
            path.substring(path.lastIndexOf('/') + 1),
            label,
            tr("component_form.entry_help"),
            null,
            suggestions,
            purposeHint(componentId, path, path.substring(path.lastIndexOf('/') + 1),
                entry == null ? null : entry.purposeHintKey(), suggestions),
            syntaxExample(componentId, path, path.substring(path.lastIndexOf('/') + 1),
                entry == null ? null : entry.syntaxExample(), suggestions),
            true,
            false,
            false
        );
    }

    private static String purposeHint(
        String componentId,
        String path,
        String fieldKey,
        String schemaPurposeKey,
        List<IdSuggestion> suggestions
    ) {
        return tr(ComponentFieldExamples.purposeKey(
            componentId,
            path,
            fieldKey,
            schemaPurposeKey,
            suggestions != null && !suggestions.isEmpty()
        ));
    }

    private String syntaxExample(
        String componentId,
        String path,
        String fieldKey,
        String schemaExample,
        List<IdSuggestion> suggestions
    ) {
        return syntaxExamples.computeIfAbsent(
            componentId + "@" + path,
            ignored -> ComponentFieldExamples.resolve(componentId, path, fieldKey, schemaExample, suggestions)
        );
    }

    private static void drawSyntaxExample(String example) {
        if (example != null && !example.isBlank()) {
            EditorUi.textDisabledWrapped(tr("component_form.syntax_example", example));
        }
    }

    private List<Long> rowIds(String path, int size) {
        List<Long> ids = arrayRowIds.computeIfAbsent(path, ignored -> new ArrayList<>());
        while (ids.size() < size) ids.add(NEXT_ROW_ID.incrementAndGet());
        while (ids.size() > size) ids.removeLast();
        return ids;
    }

    private List<IdSuggestion> suggestionsForComponent(DataComponentType<?> type) {
        ComponentOption selected = host.selectedComponent();
        if (selected != null) {
            List<IdSuggestion> variantSuggestions = VariantComponents.suggestions(
                selected.id(),
                registryIdsByKind
            );
            if (!variantSuggestions.isEmpty()) {
                return variantSuggestions;
            }
        }
        if (type == DataComponents.ITEM_MODEL) {
            List<IdSuggestion> models = ids("item_model");
            return models.isEmpty() ? itemIds : models;
        }
        if (type == DataComponents.DAMAGE_TYPE) {
            return ids("damage_type");
        }
        if (type == DataComponents.INSTRUMENT) {
            return ids("instrument");
        }
        if (type == DataComponents.PROVIDES_TRIM_MATERIAL) {
            return ids("trim_material");
        }
        if (type == DataComponents.JUKEBOX_PLAYABLE) {
            return ids("jukebox_song");
        }
        if (type == DataComponents.NOTE_BLOCK_SOUND || type == DataComponents.BREAK_SOUND) {
            return soundIds;
        }
        if (selected != null) {
            List<IdSuggestion> literals = COMPONENT_LITERAL_SUGGESTIONS.getOrDefault(selected.id(), List.of());
            if (!literals.isEmpty()) {
                return literals;
            }
            String componentKind = selected.id().substring(selected.id().indexOf(':') + 1);
            List<IdSuggestion> exactRegistry = ids(componentKind);
            if (!exactRegistry.isEmpty()) {
                return exactRegistry;
            }
        }
        return List.of();
    }

    private SoundPreviewKind previewKindFor(DataComponentType<?> type, String label, String path) {
        if (path.equals("$")) {
            if (type == DataComponents.INSTRUMENT) return SoundPreviewKind.INSTRUMENT;
            if (type == DataComponents.JUKEBOX_PLAYABLE) return SoundPreviewKind.JUKEBOX_SONG;
            if (type == DataComponents.NOTE_BLOCK_SOUND || type == DataComponents.BREAK_SOUND) {
                return SoundPreviewKind.SOUND_EVENT;
            }
            return null;
        }
        String fieldName = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        String normalized = (label + " " + fieldName).toLowerCase(Locale.ROOT);
        if (normalized.contains("sound")) return SoundPreviewKind.SOUND_EVENT;
        if (normalized.contains("instrument")) return SoundPreviewKind.INSTRUMENT;
        if (normalized.contains("jukebox") || normalized.contains("song")) {
            return SoundPreviewKind.JUKEBOX_SONG;
        }
        return null;
    }

    private List<IdSuggestion> suggestionsForField(String label, String path) {
        String component = host.selectedComponent() == null ? "" : host.selectedComponent().id();
        String normalized = (label + " " + path).toLowerCase(Locale.ROOT);
        String fieldName = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        List<IdSuggestion> exactRegistry = ids(fieldName);
        if (exactRegistry.isEmpty() && fieldName.endsWith("_id")) {
            exactRegistry = ids(fieldName.substring(0, fieldName.length() - 3));
        }
        if (exactRegistry.isEmpty() && fieldName.endsWith("s")) {
            exactRegistry = idsAndTags(fieldName.substring(0, fieldName.length() - 1));
        }
        if (!exactRegistry.isEmpty()) {
            return exactRegistry;
        }
        if ((component.equals("minecraft:charged_projectiles")
            || component.equals("minecraft:bundle_contents")
            || component.equals("minecraft:container")
            || component.equals("minecraft:sulfur_cube_content")
        ) && fieldName.equals("id")) {
            return itemIds;
        }
        if (component.equals("minecraft:recipes") && (path.equals("$") || fieldName.equals("recipe"))) {
            return ids("recipe");
        }
        if (component.equals("minecraft:pot_decorations") && path.equals("$")) {
            return itemIds;
        }
        if (component.equals("minecraft:banner_patterns")) {
            if (fieldName.equals("pattern")) {
                return ids("banner_pattern");
            }
            if (fieldName.equals("color")) {
                return DYE_COLORS;
            }
        }
        if (component.equals("minecraft:map_decorations") && fieldName.equals("type")) {
            return ids("map_decoration_type");
        }
        if (component.equals("minecraft:death_protection") && fieldName.equals("type")) {
            return ids("consume_effect_type");
        }
        if (component.equals("minecraft:blocks_attacks") && fieldName.equals("bypassed_by")) {
            return idsAndTags("damage_type");
        }
        if (component.equals("minecraft:firework_explosion") && fieldName.equals("shape")) {
            return FIREWORK_SHAPES;
        }
        if (component.equals("minecraft:swing_animation") && fieldName.equals("type")) {
            return SWING_ANIMATIONS;
        }
        if (fieldName.equals("color") || fieldName.endsWith("_color")) {
            return DYE_COLORS;
        }
        if (component.endsWith(":trim")) {
            return normalized.contains("pattern") ? ids("trim_pattern") : ids("trim_material");
        }
        if (normalized.contains("asset")) {
            return ids("equipment_asset");
        }
        if (normalized.contains("sound")) {
            return soundIds;
        }
        if (normalized.contains("trim") && normalized.contains("material")) {
            return ids("trim_material");
        }
        if (normalized.contains("banner") && normalized.contains("pattern")) {
            return ids("banner_pattern");
        }
        if (normalized.contains("pattern")) {
            return component.contains("trim") ? ids("trim_pattern") : List.of();
        }
        if (normalized.contains("potion")) {
            return ids("potion");
        }
        if (normalized.contains("instrument")) {
            return ids("instrument");
        }
        if (normalized.contains("jukebox") || normalized.contains("song")) {
            return ids("jukebox_song");
        }
        if (normalized.contains("recipe")) {
            return ids("recipe");
        }
        if (normalized.contains("damage type") || component.contains("damage_resistant")) {
            return idsAndTags("damage_type");
        }
        if (normalized.contains("block entity")) {
            return ids("block_entity_type");
        }
        if (normalized.contains("entity")) {
            return normalized.contains("allowed") ? idsAndTags("entity_type") : entityTypeIds;
        }
        if (normalized.contains("block")) {
            return idsAndTags("block");
        }
        if (normalized.contains("effect")) {
            return mobEffectIds;
        }
        if (normalized.contains("enchant")) {
            return enchantmentIds;
        }
        if (normalized.contains("attribute")) {
            return attributeIds;
        }
        if (normalized.contains("item") || normalized.contains("model")) {
            return fieldName.equals("items") ? idsAndTags("item") : itemIds;
        }
        if (normalized.contains("variant")) {
            String componentKind = component.substring(component.indexOf(':') + 1);
            List<IdSuggestion> componentRegistry = ids(componentKind);
            if (!componentRegistry.isEmpty()) {
                return componentRegistry;
            }
        }
        return List.of();
    }

    private List<IdSuggestion> ids(String kind) {
        return registryIdsByKind.getOrDefault(kind, List.of());
    }

    private List<IdSuggestion> idsAndTags(String kind) {
        List<IdSuggestion> values = ids(kind);
        List<IdSuggestion> tags = ids(kind + "#tags");
        if (tags.isEmpty()) {
            return values;
        }
        List<IdSuggestion> combined = new ArrayList<>(values.size() + tags.size());
        combined.addAll(values);
        combined.addAll(tags);
        return List.copyOf(combined);
    }

    private List<IdSuggestion> suggestionsForExistingValue(String value) {
        if (value == null || value.isBlank() || !value.contains(":")) {
            return List.of();
        }
        return suggestionsByExistingValue.computeIfAbsent(value, id -> {
            List<IdSuggestion> best = List.of();
            for (Map.Entry<String, List<IdSuggestion>> registry : registryIdsByKind.entrySet()) {
                if (registry.getKey().endsWith("#tags") || registry.getValue().isEmpty()) {
                    continue;
                }
                boolean contains = registry.getValue().stream().anyMatch(suggestion -> suggestion.id().equals(id));
                if (contains && (best.isEmpty() || registry.getValue().size() < best.size())) {
                    best = registry.getValue();
                }
            }
            return best;
        });
    }

    private static List<IdSuggestion> literalSuggestions(String... values) {
        return java.util.Arrays.stream(values).map(IdSuggestion::of).toList();
    }

    static List<IdSuggestion> literalSuggestionsForComponent(String componentId) {
        return COMPONENT_LITERAL_SUGGESTIONS.getOrDefault(componentId, List.of());
    }

    private static boolean inputFullWidthText(String id, String hint, ImString field) {
        ImGui.setNextItemWidth(-1.0f);
        return inputTextWithHint(id, hint, field);
    }

    private static boolean looksLikeIdentifierField(String label, String path, String current) {
        String normalized = (label + " " + path).toLowerCase(Locale.ROOT);
        return current.contains(":")
            || normalized.endsWith(" id")
            || normalized.contains("item")
            || normalized.contains("model")
            || normalized.contains("attribute")
            || normalized.contains("enchant");
    }

    private static String humanize(String source) {
        String normalized = source.replace('_', ' ').trim();
        return normalized.isEmpty() ? source : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

}
