package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.container.SourceInventorySnapshot;
import dev.nineofgaming.itemlab.editor.container.SulfurCubeContentDraft;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.preview.NativeItemPreviews;
import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Focused Components-tab editor for the 26.2-only sulfur cube payload.
 */
public final class SulfurCubeContentEditorPanel extends ComponentEditorPanel {
    private static final int MAX_ITEM_COUNT = 99;
    private static final int MAX_PICKER_RESULTS = 100;
    private static final String SOURCE_STACK_PAYLOAD = "ITEMLAB_SULFUR_CUBE_SOURCE";

    private SulfurCubeContentDraft content = new SulfurCubeContentDraft();
    private final ImString itemId = new ImString(256);
    private final ImInt itemCount = new ImInt(1);
    private final ImString itemSearch = new ImString(256);
    private List<IdSuggestion> itemIds = List.of();
    private boolean itemIdPending;
    private ItemStack selectedSourceStack = ItemStack.EMPTY;
    private String selectedSourceId = "";
    private String selectedSourceLabel = "";
    private SourceInventorySnapshot.SourceSlot activeSourceDrag;
    private boolean focusPresenceToggle;
    private boolean focusItemSlot;

    public SulfurCubeContentEditorPanel(ItemEditor host) {
        super(host, "sulfur_cube_content");
    }

    public static boolean supports(DataComponentType<?> type) {
        return SulfurCubeContentDraft.supported()
            && type == SulfurCubeContentDraft.componentType();
    }

    public void setCatalog(EditorCatalog catalog) {
        itemIds = catalog.itemIds();
    }

    public void sync(Minecraft client, ItemStack stack) {
        content = SulfurCubeContentDraft.fromStack(stack);
        selectedSourceStack = ItemStack.EMPTY;
        selectedSourceId = "";
        selectedSourceLabel = "";
        activeSourceDrag = null;
        focusPresenceToggle = false;
        focusItemSlot = false;
        syncItemFields();
        resetEditorUi();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (!supports(type)) return;
        sync(client, stack);
        requestInitialFocus();
    }

    public void draw(Minecraft client) {
        if (drawPanelToolbar(
            () -> save(client, true),
            () -> sync(client, host.draft())
        )) return;

        if (!EditorUi.beginCard("sulfur_cube_content", 1, ImGuiTableFlags.SizingStretchProp,
            focusItemSlot || focusPresenceToggle || hasPendingSectionFocus("content"))) return;
        ImGui.tableNextColumn();
        ImBoolean enabled = new ImBoolean(content.present());
        boolean revealForFocus = focusItemSlot || focusPresenceToggle || hasPendingSectionFocus("content");
        if (focusPresenceToggle) {
            ImGui.setKeyboardFocusHere();
            focusPresenceToggle = false;
        }
        if (ImGui.checkbox("##enabled_sulfur_cube_content", enabled)) {
            content.setPresent(enabled.get());
            if (enabled.get() && content.stack().isEmpty()) {
                content.setStack(new ItemStack(Items.STONE));
                syncItemFields();
            }
            if (enabled.get()) requestItemFocus();
            dirty = true;
        }
        ImGui.sameLine();
        boolean expanded = host.drawComponentSectionHeader(
            "sulfur_cube_content_section",
            tr("panel.sulfur_cube_content.section.content.title"),
            SulfurCubeContentDraft.componentType(),
            revealForFocus
        );
        if (!expanded) {
            EditorUi.endCard();
            return;
        }
        ImGui.beginDisabled(!content.present());
        try {
            ImGui.spacing();
            drawOwnerWarning();
            drawAbsorbedItem(client);
            ImGui.spacing();
            drawSourceInventory(client);
        } finally {
            ImGui.endDisabled();
            EditorUi.endCard();
        }
        drawItemPicker();
    }

    private void drawOwnerWarning() {
        if (!content.present()) return;
        Identifier ownerId = BuiltInRegistries.ITEM.getKey(host.draft().getItem());
        if (!ownerId.toString().equals("minecraft:sulfur_cube_bucket")) {
            ItemEditorTheme.drawFieldWarning(tr("sulfur_cube_content.owner_warning", ownerId));
        }
    }

    private void drawAbsorbedItem(Minecraft client) {
        ItemStack stack = content.stack();
        boolean requestingFocus = focusItemSlot;
        if (requestingFocus) ImGui.setKeyboardFocusHere();
        NativeItemPreviews.ItemSlotInteraction interaction = host.drawItemSlotPreview(
            client,
            "sulfur_cube_content_item",
            stack,
            true,
            false
        );
        if (requestingFocus && interaction.focused()) focusItemSlot = false;
        if ((interaction.doubleClicked() || interaction.keyboardActivated()) && !stack.isEmpty()) {
            host.requestSulfurCubeItemEdit(stack);
        }
        if (interaction.deletePressed() && !stack.isEmpty()) removeContent();
        if (ImGui.beginDragDropTarget()) {
            try {
                if (ImGui.getDragDropPayload(SOURCE_STACK_PAYLOAD) != null) {
                    host.markLastDrawnItemSlotAsDropTarget();
                }
                SourceInventorySnapshot.SourceSlot source = ImGui.acceptDragDropPayload(
                    SOURCE_STACK_PAYLOAD,
                    ImGuiDragDropFlags.AcceptNoDrawDefaultRect,
                    SourceInventorySnapshot.SourceSlot.class
                );
                if (source != null) queueSourceCopy(source.stack());
            } finally {
                ImGui.endDragDropTarget();
            }
        }
        if (!stack.isEmpty() && ImGui.beginPopupContextItem("##sulfur_cube_content_context")) {
            try {
                if (ImGui.menuItem(tr("sulfur_cube_content.edit_item"))) {
                    host.requestSulfurCubeItemEdit(stack);
                }
                if (ImGui.menuItem(tr("sulfur_cube_content.remove"))) removeContent();
            } finally {
                ImGui.endPopup();
            }
        }
        String stackLabel = stack.isEmpty()
            ? tr("sulfur_cube_content.empty")
            : stack.getHoverName().getString();
        EditorUi.sameLineIfFits(stackLabel);
        ImGui.beginGroup();
        ImGui.textWrapped(stackLabel);
        if (!stack.isEmpty() && !(stack.getItem() instanceof BlockItem)) {
            ItemEditorTheme.drawFieldWarning(tr("sulfur_cube_content.non_block_warning"));
        }
        ImGui.endGroup();

        ImGui.spacing();
        if (beginFields("sulfur_cube_content_fields")) {
            fieldLabel(tr("sulfur_cube_content.item"), tr("sulfur_cube_content.item.help"));
            String problem = itemProblem();
            if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
            applyPendingFieldFocus("content", "item_id");
            if (host.inputIdentifierWithSuggestions(
                "sulfur_cube_content_item_id",
                new FieldHints(tr("field_purpose.item_identifier"), "minecraft:stone"),
                itemId,
                itemIds,
                problem
            )) {
                itemIdChanged();
            }
            if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
            ItemEditorTheme.drawFieldProblem(problem);

            fieldLabel(tr("sulfur_cube_content.count"), tr("sulfur_cube_content.count.help"));
            ImGui.beginDisabled(stack.isEmpty());
            ImGui.setNextItemWidth(-1.0f);
            if (ImGui.inputInt("##sulfur_cube_content_count", itemCount, 1, 8)) {
                itemCount.set(Math.clamp(itemCount.get(), 1, MAX_ITEM_COUNT));
                content.setStack(stack.copyWithCount(itemCount.get()));
                dirty = true;
            }
            ImGui.endDisabled();
            ImGui.endTable();
        }

        String selectItemLabel = tr("sulfur_cube_content.choose_item");
        if (ImGui.button(selectItemLabel)) {
            itemSearch.set("");
            ImGui.openPopup("sulfur_cube_content_item_picker");
        }
        String editItemLabel = tr("sulfur_cube_content.edit_item");
        EditorUi.sameLineIfFits(editItemLabel);
        ImGui.beginDisabled(stack.isEmpty());
        if (ImGui.button(editItemLabel)) {
            host.requestSulfurCubeItemEdit(stack);
        }
        String removeLabel = tr("sulfur_cube_content.remove");
        EditorUi.sameLineIfFits(removeLabel);
        if (ImGui.button(removeLabel)) removeContent();
        ImGui.endDisabled();
    }

    private void drawItemPicker() {
        ImGui.setNextWindowSizeConstraints(420.0f, 260.0f, 620.0f, 620.0f);
        if (!ImGui.beginPopup("sulfur_cube_content_item_picker")) return;
        try {
            ImGui.text(tr("sulfur_cube_content.choose_item"));
            ImGui.separator();
            ImGui.setNextItemWidth(-1.0f);
            inputTextWithHint("##sulfur_cube_content_item_search", tr("sulfur_cube_content.search_hint"), itemSearch);
            String query = itemSearch.get().trim().toLowerCase(Locale.ROOT);
            EditorUi.beginScrollableChild("sulfur_cube_content_item_results", 0.0f, 0.0f, false);
            try {
                int shown = 0;
                int matched = 0;
                for (IdSuggestion suggestion : itemIds) {
                    if (!query.isEmpty() && !suggestion.searchable().contains(query)) continue;
                    matched++;
                    if (shown >= MAX_PICKER_RESULTS) continue;
                    shown++;
                    String label = suggestion.displayName().equals(suggestion.id())
                        ? suggestion.id()
                        : suggestion.displayName() + "  (" + suggestion.id() + ")";
                    if (ImGui.selectable(label + "###sulfur_cube_picker_" + suggestion.id())) {
                        replaceItem(suggestion.id());
                        requestItemFocus();
                        ImGui.closeCurrentPopup();
                        break;
                    }
                }
                if (matched == 0) EditorUi.textDisabledWrapped(tr("sulfur_cube_content.search_empty"));
                if (matched > MAX_PICKER_RESULTS) {
                    ImGui.textDisabled(tr("sulfur_cube_content.search_more", matched - MAX_PICKER_RESULTS));
                }
            } finally {
                ImGui.endChild();
            }
        } finally {
            ImGui.endPopup();
        }
    }

    private void drawSourceInventory(Minecraft client) {
        if (!EditorUi.beginCollapsibleCard(
            "sulfur_cube_content_source",
            tr("container.source.title"),
            ImGuiTableFlags.SizingStretchProp
        )) return;
        try {
            EditorUi.textDisabledWrapped(tr("sulfur_cube_content.source.help"));
            SourceInventorySnapshot source = host.sourceInventory();
            for (int groupIndex = 0; groupIndex < source.groups().size(); groupIndex++) {
                SourceInventorySnapshot.SourceGroup group = source.groups().get(groupIndex);
                ImGui.spacing();
                ImGui.text(group.label());
                drawSourceGroup(client, group, groupIndex);
            }
            if (!selectedSourceStack.isEmpty()) {
                ImGui.spacing();
                ImGui.textWrapped(tr(
                    "container.source.selected",
                    selectedSourceLabel,
                    selectedSourceStack.getHoverName().getString(),
                    selectedSourceStack.getCount()
                ));
            }
            ImGui.beginDisabled(selectedSourceStack.isEmpty());
            String copyStackLabel = tr("sulfur_cube_content.source.copy_stack");
            if (ImGui.button(copyStackLabel)) {
                queueSourceCopy(selectedSourceStack);
            }
            String copyOneLabel = tr("sulfur_cube_content.source.copy_one");
            EditorUi.sameLineIfFits(copyOneLabel);
            if (ImGui.button(copyOneLabel)) {
                queueSourceCopy(selectedSourceStack.copyWithCount(1));
            }
            ImGui.endDisabled();
            EditorUi.textDisabledWrapped(tr("sulfur_cube_content.source.interaction_help"));
        } finally {
            EditorUi.endCard();
        }
    }

    private void drawSourceGroup(
        Minecraft client,
        SourceInventorySnapshot.SourceGroup group,
        int groupIndex
    ) {
        float slotSize = host.itemSlotPreviewSize(client);
        int columns = Math.max(1, (int) (ImGui.getContentRegionAvailX() / slotSize));
        List<SourceInventorySnapshot.SourceSlot> slots = group.slots();
        int rows = Math.max(1, (slots.size() + columns - 1) / columns);
        float originX = ImGui.getCursorPosX();
        float originY = ImGui.getCursorPosY();
        ImGui.dummy(Math.max(slotSize, columns * slotSize), rows * slotSize);
        float endY = ImGui.getCursorPosY();
        for (int index = 0; index < slots.size(); index++) {
            SourceInventorySnapshot.SourceSlot slot = slots.get(index);
            ImGui.pushID("sulfur_source_" + groupIndex);
            ImGui.pushID(index);
            try {
                ImGui.setCursorPos(
                    originX + (index % columns) * slotSize,
                    originY + ((float) index / columns) * slotSize
                );
                String id = groupIndex + "/" + slot.id();
                ItemStack stack = slot.stack();
                NativeItemPreviews.ItemSlotInteraction interaction = host.drawItemSlotPreview(
                    client,
                    "sulfur_source_slot",
                    stack,
                    selectedSourceId.equals(id),
                    false
                );
                if ((interaction.clicked() || interaction.focused()) && !stack.isEmpty()) {
                    selectSource(group, slot, id);
                }
                if ((interaction.doubleClicked() || interaction.keyboardActivated()) && !stack.isEmpty()) {
                    selectSource(group, slot, id);
                    queueSourceCopy(stack);
                }
                if (!stack.isEmpty() && ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
                    try {
                        activeSourceDrag = slot;
                        ImGui.setDragDropPayload(SOURCE_STACK_PAYLOAD, activeSourceDrag);
                        host.drawDraggedItemPreview(client, stack);
                    } finally {
                        ImGui.endDragDropSource();
                    }
                }
                if (!stack.isEmpty() && ImGui.beginPopupContextItem("##sulfur_source_context")) {
                    try {
                        selectSource(group, slot, id);
                        if (ImGui.menuItem(tr("sulfur_cube_content.source.copy_stack"))) {
                            queueSourceCopy(stack);
                        }
                        if (ImGui.menuItem(tr("sulfur_cube_content.source.copy_one"))) {
                            queueSourceCopy(stack.copyWithCount(1));
                        }
                    } finally {
                        ImGui.endPopup();
                    }
                }
            } finally {
                ImGui.popID();
                ImGui.popID();
            }
        }
        ImGui.setCursorPos(originX, endY);
    }

    private void selectSource(
        SourceInventorySnapshot.SourceGroup group,
        SourceInventorySnapshot.SourceSlot slot,
        String id
    ) {
        selectedSourceStack = slot.stack();
        selectedSourceId = id;
        selectedSourceLabel = slot.displayLabel().isBlank()
            ? group.label() + " #" + (slot.sourceIndex() + 1)
            : group.label() + " - " + slot.displayLabel();
    }

    private void queueSourceCopy(ItemStack source) {
        if (source == null || source.isEmpty()) return;
        applySourceCopy(source);
    }

    private void applySourceCopy(ItemStack source) {
        content.setPresent(true);
        content.setStack(source);
        syncItemFields();
        requestItemFocus();
        dirty = true;
    }

    private void replaceItem(String value) {
        Identifier id = Identifier.tryParse(value);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) return;
        ItemStack current = content.stack();
        int count = Math.clamp(itemCount.get(), 1, MAX_ITEM_COUNT);
        content.setStack(current.isEmpty()
            ? new ItemStack(item, count)
            : current.transmuteCopy(item, count));
        content.setPresent(true);
        syncItemFields();
        dirty = true;
    }

    private void itemIdChanged() {
        itemIdPending = true;
        dirty = true;
        applyItemIdIfValid();
    }

    private void applyItemIdIfValid() {
        String value = itemId.get().trim();
        Identifier id = Identifier.tryParse(value);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) return;
        replaceItem(value);
        itemIdPending = false;
    }

    private String itemProblem() {
        if (!itemIdPending) return "";
        String value = itemId.get().trim();
        if (value.isEmpty()) return tr("sulfur_cube_content.item_required");
        Identifier id = Identifier.tryParse(value);
        if (id == null) return tr("validation.identifier_format");
        if (!BuiltInRegistries.ITEM.containsKey(id)) return tr("validation.identifier_unknown");
        return BuiltInRegistries.ITEM.getValue(id) == Items.AIR ? tr("error.empty_item") : "";
    }

    private void syncItemFields() {
        ItemStack stack = content.stack();
        Identifier id = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
        itemId.set(id == null ? "" : id.toString());
        itemCount.set(stack.isEmpty() ? 1 : Math.clamp(stack.getCount(), 1, MAX_ITEM_COUNT));
        itemIdPending = false;
    }

    private void removeContent() {
        content.setPresent(false);
        content.setStack(ItemStack.EMPTY);
        syncItemFields();
        focusItemSlot = false;
        focusPresenceToggle = true;
        dirty = true;
    }

    public void replaceItemFromChild(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        content.setPresent(true);
        content.setStack(stack);
        syncItemFields();
        requestItemFocus();
        dirty = true;
    }

    public void requestItemFocus() {
        clearPendingFocus();
        focusPresenceToggle = false;
        focusItemSlot = content.present() && !content.stack().isEmpty();
        if (!focusItemSlot) focusPresenceToggle = true;
    }

    @Override
    public void requestPendingFocus() {
        clearPendingFocus();
        if (content.present() && !itemProblem().isBlank()) {
            focusItemSlot = false;
            focusPresenceToggle = false;
            requestPendingField("content", "item_id");
            return;
        }
        requestInitialFocus();
    }

    private void requestInitialFocus() {
        requestItemFocus();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack();
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(client, state);
        dirty = true;
    }

    @Override
    protected boolean hasPendingChanges() {
        return (content.present() && !itemProblem().isBlank()) || !content.matchesStack(host.draft());
    }

    private ItemStack buildEditedStack() {
        String problem = itemProblem();
        if (content.present() && !problem.isBlank()) throw new IllegalArgumentException(problem);
        ItemStack edited = host.draft().copy();
        content.applyTo(edited);
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

}
