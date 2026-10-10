package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.editor.EditorNavigationGuard;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.control.AutocompleteInput;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * State, rendering and validation for normal and stored enchantments.
 */
public final class EnchantmentEditorPanel implements PendingStackEditor {

    private final ItemEditor host;
    private final AutocompleteInput autocomplete = new AutocompleteInput();
    private final ImInt target = new ImInt(0);
    private final List<EnchantmentRow> rows = new ArrayList<>();
    private List<IdSuggestion> suggestions = List.of();
    private long focusRowId;
    private boolean focusAddButton;
    private long focusIdentifierRowId;
    private boolean dirty;

    public EnchantmentEditorPanel(ItemEditor host) {
        this.host = host;
    }

    @Override
    public void requestPendingFocus() {
        focusAddButton = rows.isEmpty();
        Minecraft client = Minecraft.getInstance();
        Registry<Enchantment> registry = client.level == null
            ? null : client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        EnchantmentRow blocking = rows.isEmpty() ? null : rows.getFirst();
        for (EnchantmentRow row : rows) {
            Identifier id = Identifier.tryParse(row.id.get().trim());
            if (id == null || registry != null && !registry.containsKey(id)) {
                blocking = row;
                break;
            }
        }
        focusRowId = blocking == null ? 0L : blocking.rowId;
        focusIdentifierRowId = focusRowId;
    }

    @Override
    public boolean isDirty() {
        if (dirty) {
            try {
                dirty = !ItemStack.matches(host.draft(), buildEditedStack(Minecraft.getInstance()));
            } catch (RuntimeException ignored) {
                dirty = true;
            }
        }
        return dirty;
    }

    public void setSuggestions(List<IdSuggestion> suggestions) {
        this.suggestions = suggestions;
    }

    public void selectComponent(DataComponentType<?> type) {
        int requestedTarget = type == DataComponents.STORED_ENCHANTMENTS ? 1 : 0;
        EditorNavigationGuard.Decision decision = navigationDecision(
            requestedTarget,
            EditorNavigationGuard.Source.PROGRAMMATIC
        );
        if (!decision.allowed()) {
            reportBlockedNavigation(decision);
            return;
        }
        target.set(requestedTarget);
        sync(host.draft());
    }

    public void reloadComponent(DataComponentType<?> type, ItemStack draft) {
        target.set(type == DataComponents.STORED_ENCHANTMENTS ? 1 : 0);
        sync(draft);
    }

    public void draw(Minecraft client) {
        ImGui.spacing();
        ImGui.setNextItemWidth(-1.0f);
        int previousTarget = target.get();
        if (ImGui.combo("##enchantment_target", target, targetLabels())) {
            int requestedTarget = target.get();
            target.set(previousTarget);
            EditorNavigationGuard.Decision decision = navigationDecision(
                requestedTarget,
                EditorNavigationGuard.Source.USER
            );
            if (decision.allowed()) {
                target.set(requestedTarget);
                sync(host.draft());
            } else {
                reportBlockedNavigation(decision);
            }
        }

        drawActiveComponent(client);
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        int requestedTarget = type == DataComponents.STORED_ENCHANTMENTS ? 1 : 0;
        if (target.get() != requestedTarget) {
            EditorNavigationGuard.Decision decision = navigationDecision(
                requestedTarget,
                EditorNavigationGuard.Source.PROGRAMMATIC
            );
            if (!decision.allowed()) {
                ImGui.textWrapped(tr("enchantments.other_pending"));
                if (ImGui.button(tr("enchantments.discard_other"))) {
                    reloadComponent(type, host.draft());
                    host.setStatus(true, tr("message.discarded"));
                }
                return;
            }
            target.set(requestedTarget);
            sync(host.draft());
        }
        ImGui.spacing();
        drawActiveComponent(client);
    }

    private EditorNavigationGuard.Decision navigationDecision(
        int requestedTarget,
        EditorNavigationGuard.Source source
    ) {
        boolean sameTarget = requestedTarget == target.get();
        EditorNavigationGuard.Blocker blocker = sameTarget ? null : pendingNavigationBlocker();
        return EditorNavigationGuard.decide(
            source,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            sameTarget,
            blocker
        );
    }

    public EditorNavigationGuard.Blocker pendingNavigationBlocker() {
        if (!isDirty()) return null;

        EditorNavigationGuard.PendingState pendingState;
        String explanation;
        try {
            buildEditedStack(Minecraft.getInstance());
            pendingState = EditorNavigationGuard.PendingState.VALID;
            explanation = tr("navigation.pending", tr("tab.enchantments"));
        } catch (RuntimeException exception) {
            pendingState = EditorNavigationGuard.PendingState.INVALID;
            explanation = tr(
                "navigation.invalid",
                tr("tab.enchantments"),
                EditorUi.readableMessage(exception)
            );
        }
        return new EditorNavigationGuard.Blocker(
            tr("tab.enchantments"),
            "enchantments",
            pendingState,
            explanation,
            new EditorNavigationGuard.FocusRequest("enchantment_editor")
        );
    }

    private void reportBlockedNavigation(EditorNavigationGuard.Decision decision) {
        host.setStatus(false, decision.blocker().explanation());
        if (decision.blocker().focusRequest() == null) return;
        if (rows.isEmpty()) {
            focusAddButton = true;
        } else {
            focusRowId = rows.getFirst().rowId;
        }
    }

    private void drawActiveComponent(Minecraft client) {
        EditorUi.drawSaveDiscardActions(isDirty(), () -> save(client, true), () -> {
            sync(host.draft());
            host.setStatus(true, tr("message.discarded"));
        });
        EditorUi.sameLineIfFits(EditorIcons.buttonSize());
        if (focusAddButton) {
            ImGui.setKeyboardFocusHere();
            focusAddButton = false;
        }
        if (EditorIcons.button("add_enchantment", EditorIcons.Icon.ADD, tr("enchantments.add"))) {
            EnchantmentRow added = new EnchantmentRow("minecraft:sharpness", 1);
            rows.add(added);
            focusRowId = added.rowId;
            dirty = true;
        }
        Registry<Enchantment> registry = Objects.requireNonNull(client.level).registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        EditorUi.beginScrollableChild("enchantment_rows", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            host.drawTooltipPreview(client, previewStack(client), "enchantments_" + target.get());
            ImGui.spacing();
            ImGui.text(tr("enchantments.count", rows.size()));
            host.drawDiamondFireComponentHint(activeType());
            ImGui.separator();
            ImGui.spacing();
            int removeIndex = -1;
            for (int index = 0; index < rows.size(); index++) {
                EnchantmentRow row = rows.get(index);
                ImGui.pushID("enchantment_row_" + row.rowId);
                if (drawRow(registry, index, row) == EditorUi.ListAction.REMOVE) {
                    removeIndex = index;
                }
                ImGui.spacing();
                ImGui.popID();
            }
            if (rows.isEmpty()) {
                EditorUi.drawEmptyState(tr("enchantments.empty"));
            }
            applyRowAction(removeIndex, EditorUi.ListAction.REMOVE);
        } finally {
            ImGui.endChild();
        }
    }

    public void sync(ItemStack draft) {
        focusIdentifierRowId = 0L;
        rows.clear();
        ItemEnchantments enchantments = draft.getOrDefault(activeType(), ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            String id = entry.getKey().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(entry.getKey().getRegisteredName());
            rows.add(new EnchantmentRow(id, entry.getIntValue()));
        }
        dirty = false;
        focusRowId = 0L;
        focusAddButton = false;
        autocomplete.reset();
    }

    public boolean save(Minecraft client, boolean announce) {
        try {
            ItemStack edited = buildEditedStack(client);
            host.acceptSectionDraft(client, edited);
            dirty = false;
            if (announce) host.setStatus(true, tr("message.saved"));
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    private ItemStack previewStack(Minecraft client) {
        try {
            return buildEditedStack(client);
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack buildEditedStack(Minecraft client) {
        Registry<Enchantment> registry = Objects.requireNonNull(client.level).registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (EnchantmentRow row : rows) {
            Identifier id = Identifier.tryParse(row.id.get().trim());
            if (id == null) {
                throw new IllegalArgumentException(tr("enchantments.validation.id", row.id.get()));
            }
            Holder.Reference<Enchantment> holder = registry.get(id)
                .orElseThrow(() -> new IllegalArgumentException(
                    tr("enchantments.validation.unknown", id)
                ));
            mutable.set(holder, Math.clamp(row.level.get(), 1, 255));
        }

        ItemStack edited = host.draft().copy();
        if (rows.isEmpty()) edited.remove(activeType());
        else edited.set(activeType(), mutable.toImmutable());
        return edited;
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack(client);
    }

    public DataComponentType<?> pendingComponentType() {
        return activeType();
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(state);
        dirty = true;
    }

    public int historyTarget() {
        return target.get();
    }

    public void restorePending(Minecraft client, ItemStack state, int historyTarget) {
        target.set(Math.clamp(historyTarget, 0, 1));
        sync(state);
        dirty = true;
    }

    private DataComponentType<ItemEnchantments> activeType() {
        return target.get() == 1 ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    private static String[] targetLabels() {
        return new String[]{tr("tab.enchantments"), tr("enchantments.stored")};
    }

    private EditorUi.ListAction drawRow(Registry<Enchantment> registry, int index, EnchantmentRow row) {
        boolean requestFocus = focusRowId == row.rowId;
        EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
            "enchantment_card",
            EditorUi.identifiedTitle(
                tr("enchantments.row", index + 1),
                row.id.get().trim() + " | " + tr("enchantments.level") + " " + row.level.get()
            ),
            ImGuiTableFlags.SizingStretchProp,
            index,
            rows.size(),
            false,
            false,
            requestFocus
        );
        if (card.focusApplied()) focusRowId = 0L;
        if (card.expanded()) {
            Identifier currentId = Identifier.tryParse(row.id.get().trim());
            String enchantmentProblem = currentId == null
                ? tr("validation.identifier_format")
                : !registry.containsKey(currentId) ? tr("validation.identifier_unknown") : "";
            if (ImGui.beginTable(
                "enchantment_fields",
                2,
                ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
            )) {
                ImGui.tableSetupColumn("Identifier", ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableSetupColumn("Level", ImGuiTableColumnFlags.WidthFixed, 128.0f);
                EditorUi.alignedTableFields(
                    new String[]{tr("enchantments.id"), tr("enchantments.level")},
                    () -> {
                        if (focusIdentifierRowId == row.rowId) {
                            ImGui.setKeyboardFocusHere();
                            focusIdentifierRowId = 0L;
                        }
                        if (autocomplete.input(
                            "enchantment_id",
                            new FieldHints(
                                tr("field_purpose.enchantment_identifier"),
                                "minecraft:sharpness"
                            ),
                            row.id,
                            suggestions,
                            enchantmentProblem
                        )) dirty = true;
                    },
                    () -> {
                        ImGui.setNextItemWidth(-1.0f);
                        if (ImGui.inputInt("##level", row.level, 1, 5)) {
                            row.level.set(Math.clamp(row.level.get(), 1, 255));
                            dirty = true;
                        }
                    }
                );
                ImGui.endTable();
            }

            Identifier id = Identifier.tryParse(row.id.get().trim());
            if (id != null && registry.containsKey(id)) {
                registry.get(id).ifPresent(holder -> EditorUi.minecraftText(
                    Enchantment.getFullname(holder, row.level.get()),
                    Math.max(1.0f, ImGui.getContentRegionAvailX())
                ));
            }
            EditorUi.endCard();
        }
        return card.action();
    }

    private void applyRowAction(int index, EditorUi.ListAction action) {
        EditorListMutations.Mutation<EnchantmentRow> mutation = EditorListMutations.apply(
            rows,
            index,
            action,
            null
        );
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
}
