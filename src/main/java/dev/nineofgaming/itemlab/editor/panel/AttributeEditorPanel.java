package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.control.AutocompleteInput;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.text.RichTextLine;
import dev.nineofgaming.itemlab.editor.ui.text.TextFormatUi;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputText;

/**
 * State, rendering and validation for attribute modifiers.
 */
public final class AttributeEditorPanel implements PendingStackEditor {
    private static final float TEXT_FORMAT_POPUP_WIDTH = 360.0f;
    private static final AttributeModifier.Operation[] OPERATION_VALUES = {
        AttributeModifier.Operation.ADD_VALUE,
        AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    };
    private static final EquipmentSlotGroup[] SLOT_VALUES = {
        EquipmentSlotGroup.ANY, EquipmentSlotGroup.MAINHAND, EquipmentSlotGroup.OFFHAND,
        EquipmentSlotGroup.HAND, EquipmentSlotGroup.FEET, EquipmentSlotGroup.LEGS,
        EquipmentSlotGroup.CHEST, EquipmentSlotGroup.HEAD, EquipmentSlotGroup.ARMOR,
        EquipmentSlotGroup.BODY, EquipmentSlotGroup.SADDLE
    };

    private final ItemEditor host;
    private final AutocompleteInput autocomplete = new AutocompleteInput();
    private final List<AttributeRow> rows = new ArrayList<>();
    private List<IdSuggestion> attributeIds = List.of();
    private TextInputFormat textFormat = ItemLabConfig.get().textInputFormat();
    private long focusRowId;
    private boolean focusAddButton;
    private long focusInputRowId;
    private PendingField focusInputField;

    private enum PendingField {ATTRIBUTE, MODIFIER, AMOUNT, DISPLAY}

    private boolean dirty;
    private OrderedListDragDrop.Scope rowDragScope = OrderedListDragDrop.newScope("attributes");

    public AttributeEditorPanel(ItemEditor host) {
        this.host = host;
    }

    @Override
    public void requestPendingFocus() {
        focusAddButton = rows.isEmpty();
        AttributeRow blocking = rows.isEmpty() ? null : rows.getFirst();
        PendingField target = PendingField.ATTRIBUTE;
        for (AttributeRow row : rows) {
            PendingField invalid = invalidField(row);
            if (invalid != null) {
                blocking = row;
                target = invalid;
                break;
            }
        }
        focusRowId = blocking == null ? 0L : blocking.rowId;
        focusInputRowId = focusRowId;
        focusInputField = target;
    }

    private static PendingField invalidField(AttributeRow row) {
        Identifier attribute = Identifier.tryParse(row.attributeId.get().trim());
        if (attribute == null) return PendingField.ATTRIBUTE;
        if (Identifier.tryParse(row.modifierId.get().trim()) == null) return PendingField.MODIFIER;
        if (!BuiltInRegistries.ATTRIBUTE.containsKey(attribute)) return PendingField.ATTRIBUTE;
        if (!Double.isFinite(row.amount.get())) return PendingField.AMOUNT;
        if (row.display.get() == 2 && !row.displayText.validationError().isBlank()) return PendingField.DISPLAY;
        return null;
    }

    private void applyPendingInputFocus(AttributeRow row, PendingField field) {
        if (focusInputRowId != row.rowId || focusInputField != field) return;
        ImGui.setKeyboardFocusHere();
        focusInputRowId = 0L;
        focusInputField = null;
    }

    @Override
    public boolean isDirty() {
        if (dirty) {
            try {
                dirty = !ItemStack.matches(host.draft(), buildEditedStack());
            } catch (RuntimeException ignored) {
                dirty = true;
            }
        }
        return dirty;
    }

    public void setSuggestions(List<IdSuggestion> suggestions) {
        attributeIds = suggestions;
    }

    public void draw(Minecraft client) {
        drawEditor(client);
    }

    public void drawComponent(Minecraft client) {
        drawEditor(client);
    }

    private void drawEditor(Minecraft client) {
        ImGui.spacing();
        boolean openTextFormat = false;
        String addLabel = tr("attributes.add");
        if (ImGui.beginTable(
            "attribute_toolbar",
            2,
            ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
        )) {
            ImGui.tableSetupColumn("Actions", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn(
                "Formatting",
                ImGuiTableColumnFlags.WidthFixed,
                TextFormatUi.buttonWidth(textFormat)
            );
            ImGui.tableNextColumn();
            EditorUi.drawSaveDiscardActions(isDirty(), () -> save(client, true), () -> {
                sync(host.draft());
                host.setStatus(true, tr("message.discarded"));
            });
            EditorUi.sameLineIfFits(EditorIcons.buttonSize());
            if (focusAddButton) {
                ImGui.setKeyboardFocusHere();
                focusAddButton = false;
            }
            if (EditorIcons.button("add_attribute", EditorIcons.Icon.ADD, addLabel)) {
                AttributeRow added = AttributeRow.createDefault(rows.size(), textFormat);
                rows.add(added);
                focusRowId = added.rowId;
                dirty = true;
            }
            ImGui.tableNextColumn();
            openTextFormat = TextFormatUi.button("attribute_text_format", textFormat, null);
            ImGui.endTable();
        }
        if (openTextFormat) {
            ImGui.openPopup("attribute_text_format_popup");
        }
        EditorUi.beginScrollableChild("attribute_rows", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            host.drawTooltipPreview(client, previewStack(), "attributes");
            ImGui.spacing();
            ImGui.text(tr("attributes.row_count", rows.size()));
            host.drawDiamondFireComponentHint(DataComponents.ATTRIBUTE_MODIFIERS);
            ImGui.separator();
            ImGui.spacing();
            int actionIndex = -1;
            EditorUi.ListAction action = EditorUi.ListAction.NONE;
            OrderedListDragDrop.ReorderRequest reorderRequest = null;
            for (int index = 0; index < rows.size(); index++) {
                AttributeRow row = rows.get(index);
                ImGui.pushID("attribute_row_" + row.rowId);
                EditorUi.ListAction rowAction = drawRow(index, row);
                if (rowAction != EditorUi.ListAction.NONE) {
                    actionIndex = index;
                    action = rowAction;
                }
                OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                    rowDragScope,
                    row.rowId,
                    index
                );
                if (reorderRequest == null) reorderRequest = rowRequest;
                ImGui.popID();
                ImGui.spacing();
            }
            if (reorderRequest != null) applyRowReorder(reorderRequest);
            else applyRowAction(actionIndex, action);
            if (rows.isEmpty()) {
                EditorUi.drawEmptyState(tr("attributes.empty"));
            }
            OrderedListDragDrop.autoScrollCurrentWindow(rowDragScope);
        } finally {
            ImGui.endChild();
        }
        drawTextFormatPopup();
    }

    public void sync(ItemStack draft) {
        focusInputRowId = 0L;
        focusInputField = null;
        rows.clear();
        ItemAttributeModifiers modifiers = draft.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            String attributeId = entry.attribute().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(entry.attribute().getRegisteredName());
            int operation = indexOf(OPERATION_VALUES, entry.modifier().operation());
            int slot = indexOf(SLOT_VALUES, entry.slot());
            int display = switch (entry.display().type()) {
                case HIDDEN -> 1;
                case OVERRIDE -> 2;
                default -> 0;
            };
            Component displayText = entry.display() instanceof ItemAttributeModifiers.Display.OverrideText(
                Component component
            )
                ? component
                : Component.empty();
            rows.add(new AttributeRow(
                attributeId,
                entry.modifier().id().toString(),
                entry.modifier().amount(),
                operation,
                slot,
                display,
                displayText,
                textFormat
            ));
        }
        dirty = false;
        focusRowId = 0L;
        focusAddButton = false;
        rowDragScope = OrderedListDragDrop.newScope("attributes");
        autocomplete.reset();
    }

    public boolean save(Minecraft client, boolean announce) {
        try {
            ItemStack edited = buildEditedStack();
            host.acceptSectionDraft(client, edited);
            dirty = false;
            if (announce) host.setStatus(true, tr("message.saved"));
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    private ItemStack previewStack() {
        try {
            return buildEditedStack();
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack buildEditedStack() {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (AttributeRow row : rows) {
            Identifier attributeId = Identifier.tryParse(row.attributeId.get().trim());
            Identifier modifierId = Identifier.tryParse(row.modifierId.get().trim());
            if (attributeId == null) {
                throw new IllegalArgumentException(tr("attributes.validation.attribute_id", row.attributeId.get()));
            }
            if (modifierId == null) {
                throw new IllegalArgumentException(tr("attributes.validation.modifier_id", row.modifierId.get()));
            }
            Holder.Reference<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(attributeId)
                .orElseThrow(() -> new IllegalArgumentException(
                    tr("attributes.validation.attribute_unknown", attributeId)
                ));
            if (!Double.isFinite(row.amount.get())) {
                throw new IllegalArgumentException(tr("attributes.validation.amount_finite"));
            }

            AttributeModifier modifier = new AttributeModifier(
                modifierId,
                row.amount.get(),
                OPERATION_VALUES[Math.clamp(row.operation.get(), 0, OPERATION_VALUES.length - 1)]
            );
            EquipmentSlotGroup slot = SLOT_VALUES[Math.clamp(row.slot.get(), 0, SLOT_VALUES.length - 1)];
            ItemAttributeModifiers.Display display = switch (Math.clamp(row.display.get(), 0, 2)) {
                case 1 -> ItemAttributeModifiers.Display.hidden();
                case 2 -> ItemAttributeModifiers.Display.override(row.displayText.toComponent(false));
                default -> ItemAttributeModifiers.Display.attributeModifiers();
            };
            builder.add(attribute, modifier, slot, display);
        }

        ItemStack edited = host.draft().copy();
        if (rows.isEmpty()) edited.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        else edited.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        return edited;
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack();
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(state);
        dirty = true;
    }

    private EditorUi.ListAction drawRow(int index, AttributeRow row) {
        boolean requestFocus = focusRowId == row.rowId;
        EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
            "attribute_card",
            EditorUi.identifiedTitle(
                tr("attributes.row", index + 1),
                row.attributeId.get().trim()
            ),
            ImGuiTableFlags.SizingStretchProp,
            index,
            rows.size(),
            true,
            true,
            false,
            requestFocus,
            rowDragScope,
            row.rowId
        );
        if (card.focusApplied()) focusRowId = 0L;
        if (card.expanded()) {
            ImGui.text(tr("attributes.attribute_id"));
            Identifier attributeId = Identifier.tryParse(row.attributeId.get().trim());
            String attributeProblem = attributeId == null
                ? tr("validation.identifier_format")
                : !BuiltInRegistries.ATTRIBUTE.containsKey(attributeId) ? tr("validation.identifier_unknown") : "";
            applyPendingInputFocus(row, PendingField.ATTRIBUTE);
            if (autocomplete.input(
                "attribute_id",
                new FieldHints(tr("field_purpose.attribute_identifier"), "minecraft:attack_damage"),
                row.attributeId,
                attributeIds,
                attributeProblem
            )) dirty = true;

            if (ImGui.getContentRegionAvailX() >= 560.0f) {
                if (ImGui.beginTable(
                    "attribute_value_fields",
                    2,
                    ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
                )) {
                    ImGui.tableSetupColumn("Amount", ImGuiTableColumnFlags.WidthFixed, 210.0f);
                    ImGui.tableSetupColumn("Modifier", ImGuiTableColumnFlags.WidthStretch);
                    EditorUi.alignedTableFields(
                        new String[]{tr("attributes.amount"), tr("attributes.modifier_id")},
                        () -> drawAmountInput(row),
                        () -> drawModifierInput(row, index)
                    );
                    ImGui.endTable();
                }
            } else {
                drawAmount(row);
                drawModifier(row, index);
            }
            if (ImGui.getContentRegionAvailX() >= 620.0f) {
                if (ImGui.beginTable(
                    "attribute_options",
                    3,
                    ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoPadOuterX
                )) {
                    ImGui.tableSetupColumn("Operation", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableSetupColumn("Slot", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableSetupColumn("Tooltip", ImGuiTableColumnFlags.WidthStretch);
                    EditorUi.alignedTableFields(
                        new String[]{
                            tr("attributes.operation"),
                            tr("attributes.slot"),
                            tr("attributes.tooltip")
                        },
                        () -> drawOperationInput(row),
                        () -> drawSlotInput(row),
                        () -> drawDisplayInput(row)
                    );
                    ImGui.endTable();
                }
            } else {
                drawOperation(row);
                drawSlot(row);
                drawDisplay(row);
            }

            if (row.display.get() == 2) {
                ImGui.text(tr("attributes.custom_tooltip"));
                applyPendingInputFocus(row, PendingField.DISPLAY);
                String displayProblem = row.displayText.validationError();
                if (!displayProblem.isBlank()) ItemEditorTheme.pushInvalidField();
                ImGui.setNextItemWidth(-1.0f);
                if (inputText(
                    "##display_text",
                    row.displayText.editorSource(false, false)
                )) {
                    row.displayText.commitEditorSource();
                    dirty = true;
                }
                if (!displayProblem.isBlank()) ItemEditorTheme.popInvalidField();
                displayProblem = row.displayText.validationError();
                if (!displayProblem.isBlank()) {
                    ItemEditorTheme.drawFieldProblem(
                        tr("attributes.validation.text", row.displayText.format().label(), displayProblem)
                    );
                }
            }
            EditorUi.endCard();
        }
        return card.action();
    }

    private void applyRowAction(int index, EditorUi.ListAction action) {
        EditorListMutations.Mutation<AttributeRow> mutation = EditorListMutations.apply(
            rows,
            index,
            action,
            source -> source.copy(uniqueModifierId(source))
        );
        finishRowMutation(mutation);
    }

    private void applyRowReorder(OrderedListDragDrop.ReorderRequest request) {
        if (!rowDragScope.equals(request.scope())) return;
        finishRowMutation(EditorListMutations.reorder(rows, request, row -> row.rowId));
    }

    private void finishRowMutation(EditorListMutations.Mutation<AttributeRow> mutation) {
        if (!mutation.changed()) return;
        if (mutation.focusRow() == null) {
            focusRowId = 0L;
            focusAddButton = true;
        } else {
            focusRowId = mutation.focusRow().rowId;
        }
        autocomplete.reset();
        dirty = true;
    }

    private String uniqueModifierId(AttributeRow source) {
        Identifier id = Identifier.tryParse(source.modifierId.get().trim());
        String base = id == null
            ? "itemlab:modifier_copy"
            : id.getNamespace() + ":" + id.getPath() + "_copy";
        String candidate = base;
        int suffix = 2;
        while (modifierIdExists(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private boolean modifierIdExists(String id) {
        return rows.stream().anyMatch(row -> row.modifierId.get().trim().equals(id));
    }

    private void drawAmount(AttributeRow row) {
        ImGui.text(tr("attributes.amount"));
        drawAmountInput(row);
    }

    private void drawAmountInput(AttributeRow row) {
        applyPendingInputFocus(row, PendingField.AMOUNT);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.inputDouble("##amount", row.amount)) dirty = true;
    }

    private void drawModifier(AttributeRow row, int index) {
        ImGui.text(tr("attributes.modifier_id"));
        drawModifierInput(row, index);
    }

    private void drawModifierInput(AttributeRow row, int index) {
        applyPendingInputFocus(row, PendingField.MODIFIER);
        if (autocomplete.input(
            "modifier_id",
            new FieldHints(tr("field_purpose.modifier_identifier"), "itemlab:attack_damage"),
            row.modifierId,
            modifierIdSuggestions(row, index)
        )) {
            dirty = true;
        }
    }

    private void drawOperation(AttributeRow row) {
        ImGui.text(tr("attributes.operation"));
        drawOperationInput(row);
    }

    private void drawOperationInput(AttributeRow row) {
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.combo("##operation", row.operation, operationLabels())) dirty = true;
    }

    private void drawSlot(AttributeRow row) {
        ImGui.text(tr("attributes.slot"));
        drawSlotInput(row);
    }

    private void drawSlotInput(AttributeRow row) {
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.combo("##slot", row.slot, slotLabels())) dirty = true;
    }

    private void drawDisplay(AttributeRow row) {
        ImGui.text(tr("attributes.tooltip"));
        drawDisplayInput(row);
    }

    private void drawDisplayInput(AttributeRow row) {
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.combo("##display", row.display, displayLabels())) dirty = true;
    }

    private static String[] operationLabels() {
        return new String[]{
            tr("attributes.operation.add_value"),
            tr("attributes.operation.add_base"),
            tr("attributes.operation.add_total")
        };
    }

    private static String[] slotLabels() {
        return new String[]{
            tr("slot.any"), tr("slot.main_hand"), tr("slot.off_hand"), tr("slot.either_hand"),
            tr("slot.feet"), tr("slot.legs"), tr("slot.chest"), tr("slot.head"),
            tr("slot.armor"), tr("slot.body"), tr("slot.saddle")
        };
    }

    private static String[] displayLabels() {
        return new String[]{tr("option.default"), tr("state.hidden"), tr("attributes.custom_text")};
    }

    private void drawTextFormatPopup() {
        ImGui.setNextWindowSizeConstraints(
            TEXT_FORMAT_POPUP_WIDTH,
            0.0f,
            TEXT_FORMAT_POPUP_WIDTH,
            Float.MAX_VALUE
        );
        if (!ImGui.beginPopup("attribute_text_format_popup")) return;
        TextFormatUi.drawSelector("attribute_text_input_format", textFormat, host::switchTextInputFormat);
        ImGui.endPopup();
    }

    public Runnable prepareTextFormatChange(TextInputFormat targetFormat) {
        List<RichTextLine> fields = rows.stream().map(row -> row.displayText).toList();
        List<String> converted = fields.stream()
            .map(field -> field.convertedSource(targetFormat))
            .toList();
        return () -> {
            for (int index = 0; index < fields.size(); index++) {
                fields.get(index).switchFormat(targetFormat, converted.get(index));
            }
            textFormat = targetFormat;
        };
    }

    private static List<IdSuggestion> modifierIdSuggestions(AttributeRow row, int index) {
        Identifier attributeId = Identifier.tryParse(row.attributeId.get().trim());
        String path = attributeId == null ? "attribute" : attributeId.getPath().replace('/', '_');
        return List.of(
            IdSuggestion.of("itemlab:" + path),
            IdSuggestion.of("itemlab:" + path + "_" + (index + 1)),
            IdSuggestion.of("minecraft:base_" + path)
        );
    }

    private static <T> int indexOf(T[] values, T value) {
        for (int index = 0; index < values.length; index++) {
            if (Objects.equals(values[index], value)) return index;
        }
        return 0;
    }
}
