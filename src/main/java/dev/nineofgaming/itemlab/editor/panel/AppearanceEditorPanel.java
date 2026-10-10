package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.control.ColorSelector;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.JsonListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.preview.ItemModelPreviewMode;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.array;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.bool;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.integer;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.number;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.primitiveInt;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.primitiveString;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Purpose-built editor for models, tooltip presentation, and item colors.
 */
public final class AppearanceEditorPanel extends ComponentEditorPanel {
    private static final AtomicLong NEXT_CUSTOM_MODEL_ROW_ID = new AtomicLong();

    private final ItemLabConfig preferences = ItemLabConfig.get();
    private final ImString itemNameTranslationKey = new ImString(512);
    private final ImString itemNameFallback = new ImString(1024);
    private Component originalItemName = Component.empty();
    private Object[] itemNameArguments = TranslatableContents.NO_ARGS;
    private boolean originalItemNameWasTranslatable;
    private boolean itemNameChanged;
    private boolean itemNamePresent;
    private final CodecComponentState itemModel = new CodecComponentState(DataComponents.ITEM_MODEL, "\"minecraft:stone\"");
    private final CodecComponentState tooltipStyle = new CodecComponentState(DataComponents.TOOLTIP_STYLE, "\"minecraft:default\"");
    private final CodecComponentState customModelData = new CodecComponentState(
        DataComponents.CUSTOM_MODEL_DATA,
        "{\"floats\":[],\"flags\":[],\"strings\":[],\"colors\":[]}"
    );
    private final CodecComponentState dyedColor = new CodecComponentState(DataComponents.DYED_COLOR, "10511680");
    private final CodecComponentState dye = new CodecComponentState(DataComponents.DYE, "\"white\"");
    private List<IdSuggestion> itemModels = List.of();
    private List<IdSuggestion> tooltipStyles = List.of();
    private final Map<String, List<Long>> customModelRowIds = new HashMap<>();
    private final Map<String, OrderedListDragDrop.Scope> customModelDragScopes = new HashMap<>();
    private ItemModelPreviewMode previewMode = preferences.itemModelPreviewMode();
    private long focusCustomModelRowId;
    private String focusCustomModelAddListId;

    public AppearanceEditorPanel(ItemEditor host) {
        super(host, "appearance");
    }

    @Override
    public void requestPendingFocus() {
        focusCustomModelRowId = 0L;
        focusCustomModelAddListId = null;
        if (itemNameChanged && itemNamePresent && itemNameTranslationKey.get().trim().isEmpty()) {
            requestPendingField("item_name", "translation_key");
            return;
        }
        CodecComponentState[] states = {itemModel, tooltipStyle, customModelData, dyedColor, dye};
        String[] sections = {"item_model", "tooltip_style", "custom_model_data", "dyed_color", "dye"};
        for (int index = 0; index < states.length; index++) {
            CodecComponentState state = states[index];
            if (!state.present()) continue;
            String section = sections[index];
            if (index < 2 && Identifier.tryParse(pendingBufferText(section, primitiveString(state, "")).trim()) == null) {
                requestPendingField(section, "identifier");
                return;
            }
            try {
                // Validate only an isolated value; recovery must not normalize the editor model.
                state.apply(Minecraft.getInstance(), host.draft().copy());
            } catch (RuntimeException ignored) {
                requestPendingField(section, index < 2 ? "identifier" : "$enabled");
                return;
            }
        }
        requestPendingField("item_name", "$enabled");
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.ITEM_NAME
            || type == DataComponents.ITEM_MODEL
            || type == DataComponents.TOOLTIP_STYLE
            || type == DataComponents.CUSTOM_MODEL_DATA
            || type == DataComponents.DYED_COLOR
            || type == DataComponents.DYE;
    }

    public void setCatalog(EditorCatalog catalog) {
        List<IdSuggestion> discoveredModels = catalog.registryIds("item_model");
        itemModels = discoveredModels.isEmpty() ? catalog.itemIds() : discoveredModels;
        tooltipStyles = catalog.registryIds("tooltip_style");
    }

    public void sync(Minecraft client, ItemStack draft) {
        syncItemName(draft);
        itemModel.sync(client, draft);
        tooltipStyle.sync(client, draft);
        customModelData.sync(client, draft);
        dyedColor.sync(client, draft);
        dye.sync(client, draft);
        clearTransientState();
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack draft) {
        if (type == DataComponents.ITEM_NAME) syncItemName(draft);
        else if (type == DataComponents.ITEM_MODEL) itemModel.sync(client, draft);
        else if (type == DataComponents.TOOLTIP_STYLE) tooltipStyle.sync(client, draft);
        else if (type == DataComponents.CUSTOM_MODEL_DATA) customModelData.sync(client, draft);
        else if (type == DataComponents.DYED_COLOR) dyedColor.sync(client, draft);
        else if (type == DataComponents.DYE) dye.sync(client, draft);
        resetEditorUi();
        resetCustomModelListUi();
        dirty = itemNameChanged
            || itemModel.differsFrom(client, draft)
            || tooltipStyle.differsFrom(client, draft)
            || customModelData.differsFrom(client, draft)
            || dyedColor.differsFrom(client, draft)
            || dye.differsFrom(client, draft);
    }

    private void syncItemName(ItemStack draft) {
        Component currentItemName = draft.get(DataComponents.ITEM_NAME);
        itemNamePresent = currentItemName != null;
        originalItemName = currentItemName == null ? Component.empty() : currentItemName.copy();
        if (currentItemName != null && currentItemName.getContents() instanceof TranslatableContents translatable) {
            originalItemNameWasTranslatable = true;
            itemNameTranslationKey.set(translatable.getKey());
            itemNameFallback.set(translatable.getFallback() == null ? "" : translatable.getFallback());
            itemNameArguments = translatable.getArgs().clone();
        } else {
            originalItemNameWasTranslatable = false;
            itemNameTranslationKey.set(draft.getItem().getDescriptionId());
            itemNameFallback.set(currentItemName == null ? "" : currentItemName.getString());
            itemNameArguments = TranslatableContents.NO_ARGS;
        }
        itemNameChanged = false;
    }

    public void draw(Minecraft client) {
        if (drawToolbar(client, null)) return;
        ItemStack preview = previewStack(client);
        host.setItemIconPreview(preview);
        EditorUi.beginScrollableChild("appearance_editor_sections", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            drawModelPreview(client, preview, "presentation");
            ImGui.spacing();
            host.drawTooltipPreview(client, preview, "presentation");
            ImGui.spacing();
            drawItemName();
            ImGui.spacing();
            drawItemModel();
            ImGui.spacing();
            drawTooltipStyle();
            ImGui.spacing();
            drawCustomModelData();
            ImGui.spacing();
            drawRgbComponent("dyed_color", dyedColor, 0xA06540);
            ImGui.spacing();
            drawDyeComponent("dye", dye);
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (drawToolbar(client, type)) return;
        ItemStack preview = previewStack(client);
        host.setItemIconPreview(preview);
        drawModelPreview(client, preview, "presentation_component");
        ImGui.spacing();
        host.drawTooltipPreview(client, preview, "presentation_component");
        ImGui.spacing();
        if (type == DataComponents.ITEM_NAME) drawItemName();
        else if (type == DataComponents.ITEM_MODEL) drawItemModel();
        else if (type == DataComponents.TOOLTIP_STYLE) drawTooltipStyle();
        else if (type == DataComponents.CUSTOM_MODEL_DATA) drawCustomModelData();
        else if (type == DataComponents.DYED_COLOR) {
            drawRgbComponent("dyed_color", dyedColor, 0xA06540);
        } else if (type == DataComponents.DYE) {
            drawDyeComponent("dye", dye);
        }
    }

    private void drawModelPreview(Minecraft client, ItemStack preview, String id) {
        if (!EditorUi.beginCollapsibleCard(
            "item_model_preview_" + id,
            tr("appearance.model_preview"),
            ImGuiTableFlags.SizingStretchProp
        )) return;
        boolean firstMode = true;
        for (ItemModelPreviewMode option : ItemModelPreviewMode.values()) {
            if (!firstMode) EditorUi.sameLineIfFits(option.label());
            boolean selected = previewMode == option;
            if (selected) ItemEditorTheme.pushActiveFilter();
            if (ImGui.button(option.label() + "##item_model_preview_mode_" + id) && previewMode != option) {
                previewMode = option;
                preferences.setItemModelPreviewMode(option);
            }
            if (selected) ItemEditorTheme.popActiveFilter();
            firstMode = false;
        }
        if (previewMode.isRotatable()) {
            ImGui.spacing();
            EditorUi.textDisabledWrapped(tr("preview.rotation_help"));
        }
        ImGui.spacing();
        host.drawItemModelPreview(client, preview, previewMode);
        EditorUi.endCard();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    private ItemStack previewStack(Minecraft client) {
        try {
            return buildEditedStack(client);
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack buildEditedStack(Minecraft client) {
        ItemStack edited = host.draft().copy();
        if (itemNameChanged) {
            if (itemNamePresent) edited.set(DataComponents.ITEM_NAME, buildItemName());
            else edited.remove(DataComponents.ITEM_NAME);
        }
        itemModel.apply(client, edited);
        tooltipStyle.apply(client, edited);
        customModelData.apply(client, edited);
        dyedColor.apply(client, edited);
        dye.apply(client, edited);
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack(client);
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(client, state);
        // sync marks the name clean, but a restored name/removal still has to replace the host draft's name.
        itemNameChanged = !Objects.equals(state.get(DataComponents.ITEM_NAME), host.draft().get(DataComponents.ITEM_NAME));
        dirty = true;
    }

    @Override
    protected boolean hasPendingChanges() {
        return !ItemStack.matches(host.draft(), buildEditedStack(Minecraft.getInstance()));
    }

    private boolean drawToolbar(Minecraft client, DataComponentType<?> componentType) {
        return drawPanelToolbar(
            () -> save(client, true),
            () -> {
                if (componentType == null) sync(client, host.draft());
                else syncComponent(client, componentType, host.draft());
            }
        );
    }

    private void clearTransientState() {
        resetEditorUi();
        resetCustomModelListUi();
        dirty = false;
    }

    private void drawItemName() {
        boolean reveal = hasPendingSectionFocus("item_name");
        if (!EditorUi.beginCard("presentation_item_name", 1, ImGuiTableFlags.SizingStretchProp, reveal)) return;
        ImGui.tableNextColumn();
        ImBoolean enabled = new ImBoolean(itemNamePresent);
        applyPendingFieldFocus("item_name", "$enabled");
        if (ImGui.checkbox("##enabled_item_name", enabled)) {
            itemNamePresent = enabled.get();
            itemNameChanged = true;
            dirty = true;
        }
        ImGui.sameLine();
        boolean expanded = EditorUi.collapsibleHeader(
            "presentation_item_name",
            tr("appearance.item_name"),
            host.componentSectionStartsExpanded(DataComponents.ITEM_NAME),
            reveal
        );
        host.drawDiamondFireComponentHint(DataComponents.ITEM_NAME);
        if (!expanded) {
            EditorUi.endCard();
            return;
        }
        ImGui.spacing();
        ImGui.beginDisabled(!itemNamePresent);
        ImGui.spacing();
        ImGui.text(tr("field.translation_key"));
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("item_name", "translation_key");
        if (inputTextWithHint(
            "##item_name_translation_key",
            new FieldHints(
                tr("field_purpose.translation_key"),
                tr("appearance.item_name.translation_key_hint")
            ),
            itemNameTranslationKey
        )) {
            itemNameChanged = true;
            dirty = true;
        }
        ImGui.text(tr("field.fallback_optional"));
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("item_name", "fallback");
        if (inputTextWithHint(
            "##item_name_fallback",
            tr("appearance.item_name.fallback_hint"),
            itemNameFallback
        )) {
            itemNameChanged = true;
            dirty = true;
        }
        if (!originalItemNameWasTranslatable && !itemNameChanged) {
            EditorUi.textDisabledWrapped(tr("appearance.item_name.literal"));
        }
        if (itemNameArguments.length > 0) {
            EditorUi.textDisabledWrapped(tr("appearance.item_name.arguments", itemNameArguments.length));
        }
        ImGui.endDisabled();
        EditorUi.endCard();
    }

    private Component buildItemName() {
        String key = itemNameTranslationKey.get().trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException(tr("appearance.item_name.translation_key_required"));
        }
        String fallback = itemNameFallback.get();
        MutableComponent rebuilt = fallback.isBlank()
            ? Component.translatable(key, itemNameArguments)
            : Component.translatableWithFallback(key, fallback, itemNameArguments);
        rebuilt.setStyle(originalItemName.getStyle());
        for (Component sibling : originalItemName.getSiblings()) {
            rebuilt.append(sibling.copy());
        }
        return rebuilt;
    }

    private void drawItemModel() {
        if (!beginSection("item_model", itemModel)) return;
        String current = primitiveString(itemModel, "minecraft:stone");
        ImString input = buffer("item_model", current);
        applyPendingFieldFocus("item_model", "identifier");
        if (host.inputIdentifierWithSuggestions(
            "appearance_item_model",
            new FieldHints(tr("field_purpose.model_identifier"), "minecraft:stone"),
            input,
            itemModels
        )) {
            itemModel.setValue(new JsonPrimitive(input.get().trim()));
            dirty = true;
        }
        endSection();
    }

    private void drawTooltipStyle() {
        if (!beginSection("tooltip_style", tooltipStyle)) return;
        String current = primitiveString(tooltipStyle, "minecraft:default");
        ImString input = buffer("tooltip_style", current);
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("tooltip_style", "identifier");
        boolean changed = tooltipStyles.isEmpty()
            ? inputTextWithHint(
            "##appearance_tooltip_style",
            new FieldHints(
                tr("field_purpose.tooltip_style_identifier"),
                tr("appearance.tooltip_style.hint")
            ),
            input
        )
            : host.inputIdentifierWithSuggestions(
            "appearance_tooltip_style",
            new FieldHints(
                tr("field_purpose.tooltip_style_identifier"),
                tr("appearance.tooltip_style.hint")
            ),
            input,
            tooltipStyles
        );
        if (changed) {
            tooltipStyle.setValue(new JsonPrimitive(input.get().trim()));
            dirty = true;
        }
        endSection();
    }

    private void drawCustomModelData() {
        if (!beginSection("custom_model_data", customModelData)) return;
        JsonObject value = customModelData.object();
        if (customModelData.supportsField("floats")) {
            drawFloatList(array(value, "floats"));
            ImGui.spacing();
        }
        if (customModelData.supportsField("flags")) {
            drawFlagList(array(value, "flags"));
            ImGui.spacing();
        }
        if (customModelData.supportsField("strings")) {
            drawStringList(array(value, "strings"));
            ImGui.spacing();
        }
        if (customModelData.supportsField("colors")) {
            drawColorList(array(value, "colors"));
        }
        endSection();
    }

    private void drawFloatList(JsonArray values) {
        String listId = "floats";
        List<Long> rowIds = customModelRowIds(listId, values.size());
        OrderedListDragDrop.Scope dragScope = customModelDragScope(listId);
        ImGui.separatorText(tr("appearance.custom_model.floats"));
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < values.size(); index++) {
            long rowId = rowIds.get(index);
            ImGui.pushID("model_float_" + rowId);
            ImDouble input = new ImDouble(number(values.get(index), 0.0));
            ImGui.beginGroup();
            if (EditorUi.beginInlineIconActionRow("model_float_row", 4)) {
                OrderedListDragDrop.drawHandle(
                    "reorder_model_float",
                    dragScope,
                    rowId,
                    tr("appearance.custom_model.value", index + 1)
                );
                ImGui.sameLine();
                focusCustomModelRow(rowId);
                ImGui.setNextItemWidth(-1.0f);
                if (ImGui.inputDouble("##value", input, 0.1, 1.0, "%.3f")) {
                    values.set(index, new JsonPrimitive(input.get()));
                    dirty = true;
                }
                ImGui.tableNextColumn();
                EditorUi.ListAction rowAction = EditorUi.drawInlineOrderedListActions(index, values.size(), true);
                if (rowAction != EditorUi.ListAction.NONE) {
                    actionIndex = index;
                    action = rowAction;
                }
                ImGui.endTable();
            }
            ImGui.endGroup();
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                dragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
        }
        applyCustomModelAction(
            values, rowIds, actionIndex, action, listId, new JsonPrimitive(0.0), reorderRequest, dragScope
        );
        if (values.isEmpty()) EditorUi.drawEmptyState(tr("appearance.custom_model.floats.empty"));
        focusCustomModelAdd(listId);
        if (EditorIcons.smallButton(
            "add_model_float",
            EditorIcons.Icon.ADD,
            tr("appearance.custom_model.add_float")
        )) {
            values.add(0.0);
            focusCustomModelRowId = addCustomModelRow(rowIds);
            focusCustomModelAddListId = null;
            dirty = true;
        }
        OrderedListDragDrop.autoScrollCurrentWindow(dragScope);
    }

    private void drawFlagList(JsonArray values) {
        String listId = "flags";
        List<Long> rowIds = customModelRowIds(listId, values.size());
        OrderedListDragDrop.Scope dragScope = customModelDragScope(listId);
        ImGui.separatorText(tr("tab.flags"));
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < values.size(); index++) {
            long rowId = rowIds.get(index);
            ImGui.pushID("model_flag_" + rowId);
            ImBoolean input = new ImBoolean(bool(values.get(index), false));
            ImGui.beginGroup();
            if (EditorUi.beginInlineIconActionRow("model_flag_row", 4)) {
                OrderedListDragDrop.drawHandle(
                    "reorder_model_flag",
                    dragScope,
                    rowId,
                    tr("appearance.custom_model.value", index + 1)
                );
                ImGui.sameLine();
                focusCustomModelRow(rowId);
                if (ImGui.checkbox(tr("appearance.custom_model.value", index + 1), input)) {
                    values.set(index, new JsonPrimitive(input.get()));
                    dirty = true;
                }
                ImGui.tableNextColumn();
                EditorUi.ListAction rowAction = EditorUi.drawInlineOrderedListActions(index, values.size(), true);
                if (rowAction != EditorUi.ListAction.NONE) {
                    actionIndex = index;
                    action = rowAction;
                }
                ImGui.endTable();
            }
            ImGui.endGroup();
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                dragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
        }
        applyCustomModelAction(
            values, rowIds, actionIndex, action, listId, new JsonPrimitive(false), reorderRequest, dragScope
        );
        if (values.isEmpty()) EditorUi.drawEmptyState(tr("appearance.custom_model.flags.empty"));
        focusCustomModelAdd(listId);
        if (EditorIcons.smallButton(
            "add_model_flag",
            EditorIcons.Icon.ADD,
            tr("appearance.custom_model.add_flag")
        )) {
            values.add(false);
            focusCustomModelRowId = addCustomModelRow(rowIds);
            focusCustomModelAddListId = null;
            dirty = true;
        }
        OrderedListDragDrop.autoScrollCurrentWindow(dragScope);
    }

    private void drawStringList(JsonArray values) {
        String listId = "strings";
        List<Long> rowIds = customModelRowIds(listId, values.size());
        OrderedListDragDrop.Scope dragScope = customModelDragScope(listId);
        ImGui.separatorText(tr("appearance.custom_model.strings"));
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < values.size(); index++) {
            long rowId = rowIds.get(index);
            ImGui.pushID("model_string_" + rowId);
            String current = string(values.get(index), "");
            ImString input = buffer("custom_model/strings/" + rowId, current);
            ImGui.beginGroup();
            if (EditorUi.beginInlineIconActionRow("model_string_row", 4)) {
                OrderedListDragDrop.drawHandle(
                    "reorder_model_string",
                    dragScope,
                    rowId,
                    current.isBlank() ? tr("appearance.custom_model.value", index + 1) : current
                );
                ImGui.sameLine();
                focusCustomModelRow(rowId);
                ImGui.setNextItemWidth(-1.0f);
                if (inputTextWithHint(
                    "##value",
                    new FieldHints(tr("field_purpose.model_predicate_string"), "examplemod:variant"),
                    input
                )) {
                    values.set(index, new JsonPrimitive(input.get()));
                    dirty = true;
                }
                ImGui.tableNextColumn();
                EditorUi.ListAction rowAction = EditorUi.drawInlineOrderedListActions(index, values.size(), true);
                if (rowAction != EditorUi.ListAction.NONE) {
                    actionIndex = index;
                    action = rowAction;
                }
                ImGui.endTable();
            }
            ImGui.endGroup();
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                dragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
        }
        applyCustomModelAction(
            values, rowIds, actionIndex, action, listId, new JsonPrimitive(""), reorderRequest, dragScope
        );
        if (values.isEmpty()) EditorUi.drawEmptyState(tr("appearance.custom_model.strings.empty"));
        focusCustomModelAdd(listId);
        if (EditorIcons.smallButton(
            "add_model_string",
            EditorIcons.Icon.ADD,
            tr("appearance.custom_model.add_string")
        )) {
            values.add("");
            focusCustomModelRowId = addCustomModelRow(rowIds);
            focusCustomModelAddListId = null;
            dirty = true;
        }
        OrderedListDragDrop.autoScrollCurrentWindow(dragScope);
    }

    private void drawColorList(JsonArray values) {
        String listId = "colors";
        List<Long> rowIds = customModelRowIds(listId, values.size());
        OrderedListDragDrop.Scope dragScope = customModelDragScope(listId);
        ImGui.separatorText(tr("appearance.custom_model.colors"));
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < values.size(); index++) {
            long rowId = rowIds.get(index);
            ImGui.pushID("model_color_" + rowId);
            int original = integer(values.get(index), 0xFFFFFFFF);
            ImGui.beginGroup();
            if (EditorUi.beginInlineIconActionRow("model_color_row", 4)) {
                OrderedListDragDrop.drawHandle(
                    "reorder_model_color",
                    dragScope,
                    rowId,
                    tr("appearance.custom_model.value", index + 1)
                );
                ImGui.sameLine();
                focusCustomModelRow(rowId);
                int changed = ColorSelector.drawRgb("argb", original, true);
                if (changed != original) {
                    values.set(index, new JsonPrimitive(changed));
                    dirty = true;
                }
                ImGui.tableNextColumn();
                EditorUi.ListAction rowAction = EditorUi.drawInlineOrderedListActions(index, values.size(), true);
                if (rowAction != EditorUi.ListAction.NONE) {
                    actionIndex = index;
                    action = rowAction;
                }
                ImGui.endTable();
            }
            ImGui.endGroup();
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                dragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        applyCustomModelAction(
            values,
            rowIds,
            actionIndex,
            action,
            listId,
            new JsonPrimitive(0xFFFFFFFF),
            reorderRequest,
            dragScope
        );
        if (values.isEmpty()) EditorUi.drawEmptyState(tr("appearance.custom_model.colors.empty"));
        focusCustomModelAdd(listId);
        if (EditorIcons.smallButton(
            "add_model_color",
            EditorIcons.Icon.ADD,
            tr("appearance.custom_model.add_color")
        )) {
            values.add(0xFFFFFFFF);
            focusCustomModelRowId = addCustomModelRow(rowIds);
            focusCustomModelAddListId = null;
            dirty = true;
        }
        OrderedListDragDrop.autoScrollCurrentWindow(dragScope);
    }

    private List<Long> customModelRowIds(String listId, int size) {
        List<Long> rowIds = customModelRowIds.computeIfAbsent(listId, ignored -> new ArrayList<>());
        while (rowIds.size() < size) rowIds.add(NEXT_CUSTOM_MODEL_ROW_ID.incrementAndGet());
        while (rowIds.size() > size) rowIds.removeLast();
        return rowIds;
    }

    private void applyCustomModelAction(
        JsonArray values,
        List<Long> rowIds,
        int actionIndex,
        EditorUi.ListAction action,
        String listId,
        JsonElement insertedValue,
        OrderedListDragDrop.ReorderRequest reorderRequest,
        OrderedListDragDrop.Scope dragScope
    ) {
        if (reorderRequest == null && (actionIndex < 0 || action == EditorUi.ListAction.NONE)) return;
        if (reorderRequest != null && !dragScope.equals(reorderRequest.scope())) return;
        long affectedRowId = reorderRequest == null && actionIndex < rowIds.size()
            ? rowIds.get(actionIndex)
            : 0L;
        JsonListMutations.Mutation mutation = reorderRequest == null
            ? JsonListMutations.apply(
            values,
            rowIds,
            actionIndex,
            action,
            NEXT_CUSTOM_MODEL_ROW_ID::incrementAndGet,
            insertedValue::deepCopy
        )
            : JsonListMutations.reorder(values, rowIds, reorderRequest);
        if (!mutation.changed()) return;
        if (reorderRequest == null && action == EditorUi.ListAction.REMOVE) {
            textBuffers.remove("custom_model/" + listId + "/" + affectedRowId);
        }
        if (mutation.focusRowId() == 0L) {
            focusCustomModelRowId = 0L;
            focusCustomModelAddListId = listId;
        } else {
            focusCustomModelRowId = mutation.focusRowId();
            focusCustomModelAddListId = null;
        }
        dirty = true;
    }

    private static long addCustomModelRow(List<Long> rowIds) {
        long rowId = NEXT_CUSTOM_MODEL_ROW_ID.incrementAndGet();
        rowIds.add(rowId);
        return rowId;
    }

    private void focusCustomModelRow(long rowId) {
        if (focusCustomModelRowId != rowId) return;
        ImGui.setKeyboardFocusHere();
        focusCustomModelRowId = 0L;
    }

    private void focusCustomModelAdd(String listId) {
        if (!listId.equals(focusCustomModelAddListId)) return;
        ImGui.setKeyboardFocusHere();
        focusCustomModelAddListId = null;
    }

    private void resetCustomModelListUi() {
        customModelRowIds.clear();
        customModelDragScopes.clear();
        focusCustomModelRowId = 0L;
        focusCustomModelAddListId = null;
    }

    private OrderedListDragDrop.Scope customModelDragScope(String listId) {
        return customModelDragScopes.computeIfAbsent(
            listId,
            ignored -> OrderedListDragDrop.newScope("custom_model_data/" + listId)
        );
    }

    private void drawRgbComponent(
        String id,
        CodecComponentState state,
        int fallback
    ) {
        if (!beginSection(id, state)) return;
        int original = primitiveInt(state, fallback);
        int changed = ColorSelector.drawRgb(id, original, false);
        if (changed != original) {
            state.setValue(new JsonPrimitive(changed));
            dirty = true;
        }
        endSection();
    }

    private void drawDyeComponent(
        String id,
        CodecComponentState state
    ) {
        if (!beginSection(id, state)) return;
        String selected = primitiveString(state, "white");
        DyeColor original = DyeColor.byName(selected, DyeColor.WHITE);
        DyeColor changed = ColorSelector.drawDye(id, original);
        if (changed != original) {
            state.setValue(new JsonPrimitive(changed.getName()));
            dirty = true;
        }
        endSection();
    }

}
