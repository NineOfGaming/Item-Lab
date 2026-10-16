package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.control.AutocompleteInput;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.preview.ItemModelPreviewMode;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.PotDecorations;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Four-sided decorated-pot editor with a live rotatable Minecraft preview.
 */
public final class DecoratedPotEditorPanel extends ComponentEditorPanel {
    private static final String BRICK_ID = "minecraft:brick";
    private static final float PREVIEW_COLUMN_WIDTH = 310.0f;

    private final AutocompleteInput autocomplete = new AutocompleteInput();
    private final ImBoolean decorationsPresent = new ImBoolean(false);
    private final EnumMap<PotSide, ImString> sideValues = new EnumMap<>(PotSide.class);
    private List<IdSuggestion> sherdSuggestions = List.of();
    private boolean focusEnabledToggle;
    private PotSide requestedSideFocus;

    public DecoratedPotEditorPanel(ItemEditor host) {
        super(host, "decorated_pot");
        for (PotSide side : PotSide.values()) {
            sideValues.put(side, new ImString(BRICK_ID, 512));
        }
    }

    @Override
    public void requestPendingFocus() {
        focusEnabledToggle = false;
        requestedSideFocus = null;
        if (decorationsPresent.get()) {
            for (PotSide side : PotSide.values()) {
                Identifier parsed = Identifier.tryParse(sideValues.get(side).get().trim());
                if (parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed)) {
                    requestPendingField("pot_decorations", "side/" + side.id);
                    return;
                }
            }
        }
        requestPendingField("pot_decorations", "$enabled");
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.POT_DECORATIONS;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.getItem() == Items.DECORATED_POT || stack.has(DataComponents.POT_DECORATIONS);
    }

    public void setCatalog(EditorCatalog catalog) {
        Map<String, IdSuggestion> itemSuggestions = new HashMap<>();
        for (IdSuggestion suggestion : catalog.itemIds()) {
            itemSuggestions.put(suggestion.id(), suggestion);
        }

        List<IdSuggestion> suggestions = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.DECORATED_POT_INGREDIENTS)) {
            Identifier id = BuiltInRegistries.ITEM.getKey(holder.value());
            suggestions.add(itemSuggestions.getOrDefault(id.toString(), IdSuggestion.of(id.toString())));
        }
        if (suggestions.isEmpty()) {
            suggestions = catalog.itemIds().stream()
                .filter(suggestion -> suggestion.id().equals(BRICK_ID)
                    || suggestion.id().endsWith("_pottery_sherd"))
                .toList();
        } else {
            suggestions.sort(java.util.Comparator.comparing(IdSuggestion::id));
        }
        sherdSuggestions = List.copyOf(suggestions);
    }

    public void sync(ItemStack stack) {
        PotDecorations installed = stack.get(DataComponents.POT_DECORATIONS);
        decorationsPresent.set(installed != null);
        List<Item> sides = (installed == null ? PotDecorations.EMPTY : installed).ordered();
        PotSide[] orderedSides = PotSide.values();
        for (int index = 0; index < orderedSides.length; index++) {
            sideValues.get(orderedSides[index]).set(itemId(sides.get(index)));
        }
        dirty = false;
        resetEditorUi();
        focusEnabledToggle = false;
        requestedSideFocus = null;
        autocomplete.reset();
    }

    public void syncComponent(DataComponentType<?> type, ItemStack stack) {
        sync(stack);
        focusEnabledToggle = !decorationsPresent.get();
        requestedSideFocus = decorationsPresent.get() ? PotSide.BACK : null;
    }

    public void draw(Minecraft client) {
        drawEditor(client);
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        drawEditor(client);
    }

    private void drawEditor(Minecraft client) {
        if (drawPanelToolbar(
            () -> save(client, true),
            () -> sync(host.draft())
        )) return;

        EditorUi.beginScrollableChild("decorated_pot_editor", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            ItemStack editedPreview = previewStack();
            host.setItemIconPreview(editedPreview);
            ItemStack potPreview = decoratedPotPreview(editedPreview);
            EditorUi.drawPreviewFormSplit(
                "decorated_pot_workspace",
                PREVIEW_COLUMN_WIDTH,
                () -> drawPreview(client, potPreview),
                this::drawSides
            );
        } finally {
            ImGui.endChild();
        }
    }

    private void drawPreview(Minecraft client, ItemStack preview) {
        ImGui.text(tr("decorated_pot.preview"));
        host.drawItemModelPreview(client, preview, ItemModelPreviewMode.FIXED);
        EditorUi.textDisabledWrapped(tr("preview.rotation_help"));
    }

    private void drawSides() {
        if (!host.beginComponentCard(
            "decorated_pot_sides",
            tr("decorated_pot.sides"),
            DataComponents.POT_DECORATIONS,
            hasPendingSectionFocus("pot_decorations")
        )) return;
        applyPendingFieldFocus("pot_decorations", "$enabled");
        if (focusEnabledToggle) {
            ImGui.setKeyboardFocusHere();
            focusEnabledToggle = false;
        }
        if (ImGui.checkbox(tr("decorated_pot.enabled"), decorationsPresent)) {
            dirty = true;
            requestedSideFocus = decorationsPresent.get() ? PotSide.BACK : null;
            autocomplete.reset();
        }
        EditorUi.textDisabledWrapped(tr("decorated_pot.sides.help"));
        ImGui.spacing();
        ImGui.beginDisabled(!decorationsPresent.get());
        for (PotSide side : PotSide.values()) {
            drawSide(side, sideValues.get(side));
        }
        ImGui.endDisabled();
        EditorUi.endCard();
    }

    private void drawSide(PotSide side, ImString value) {
        ImGui.text(tr(side.labelKey));
        Identifier parsed = Identifier.tryParse(value.get().trim());
        String problem = parsed == null
            ? tr("validation.identifier_format")
            : !BuiltInRegistries.ITEM.containsKey(parsed) ? tr("validation.identifier_unknown") : "";
        if (requestedSideFocus == side) {
            ImGui.setKeyboardFocusHere();
            requestedSideFocus = null;
        }
        applyPendingFieldFocus("pot_decorations", "side/" + side.id);
        if (autocomplete.input(
            "decorated_pot_" + side.id,
            new FieldHints(tr("field_purpose.item_identifier"), BRICK_ID),
            value,
            sherdSuggestions,
            problem
        )) dirty = true;
        ImGui.spacing();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, this::sync);
    }

    private ItemStack previewStack() {
        try {
            return buildEditedStack();
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack decoratedPotPreview(ItemStack edited) {
        ItemStack preview = edited.getItem() == Items.DECORATED_POT
            ? edited.copy()
            : Items.DECORATED_POT.getDefaultInstance();
        PotDecorations decorations = edited.get(DataComponents.POT_DECORATIONS);
        if (decorations != null) preview.set(DataComponents.POT_DECORATIONS, decorations);
        else preview.remove(DataComponents.POT_DECORATIONS);
        return preview;
    }

    private ItemStack buildEditedStack() {
        ItemStack edited = host.draft().copy();
        if (decorationsPresent.get()) {
            edited.set(DataComponents.POT_DECORATIONS, new PotDecorations(
                resolveItem(sideValues.get(PotSide.BACK)),
                resolveItem(sideValues.get(PotSide.LEFT)),
                resolveItem(sideValues.get(PotSide.RIGHT)),
                resolveItem(sideValues.get(PotSide.FRONT))
            ));
        } else {
            edited.remove(DataComponents.POT_DECORATIONS);
        }
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

    @Override
    protected boolean hasPendingChanges() {
        return !ItemStack.matches(host.draft(), buildEditedStack());
    }

    private static Item resolveItem(ImString value) {
        Identifier id = Identifier.tryParse(value.get().trim());
        if (id == null) throw new IllegalArgumentException(tr("validation.identifier_format"));
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalArgumentException(tr("validation.identifier_unknown") + " " + id);
        }
        return BuiltInRegistries.ITEM.getValue(id);
    }

    private static String itemId(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id.toString();
    }

    private enum PotSide {
        BACK("back", "decorated_pot.side.back"),
        LEFT("left", "decorated_pot.side.left"),
        RIGHT("right", "decorated_pot.side.right"),
        FRONT("front", "decorated_pot.side.front");

        private final String id;
        private final String labelKey;

        PotSide(String id, String labelKey) {
            this.id = id;
            this.labelKey = labelKey;
        }
    }
}
