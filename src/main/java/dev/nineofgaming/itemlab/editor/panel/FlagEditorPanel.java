package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;

/**
 * State, rendering and validation for flags, durability and tooltip visibility.
 */
public final class FlagEditorPanel implements PendingStackEditor {
    private static final int MAX_ITEM_COUNT = 99;
    private boolean focusPendingToolbar;
    private boolean focusMaxStackSize;

    @Override
    public void requestPendingFocus() {
        focusPendingToolbar = false;
        focusMaxStackSize = overrideMaxDamage.get() && overrideMaxStackSize.get() && maxStackSize.get() > 1;
        focusTooltipVisibilityControl = false;
        if (focusMaxStackSize) return;
        if (tooltipDirty) focusTooltipVisibilityControl = true;
        else focusPendingToolbar = true;
    }

    private static final Set<String> VANILLA_TOOLTIP_COMPONENT_IDS = Set.of(
        "minecraft:tropical_fish/pattern",
        "minecraft:instrument",
        "minecraft:map_id",
        "minecraft:bees",
        "minecraft:container_loot",
        "minecraft:container",
        "minecraft:banner_patterns",
        "minecraft:pot_decorations",
        "minecraft:written_book_content",
        "minecraft:charged_projectiles",
        "minecraft:fireworks",
        "minecraft:firework_explosion",
        "minecraft:potion_contents",
        "minecraft:jukebox_playable",
        "minecraft:trim",
        "minecraft:stored_enchantments",
        "minecraft:enchantments",
        "minecraft:dyed_color",
        "minecraft:profile",
        "minecraft:lore",
        "minecraft:sulfur_cube_content",
        "minecraft:attribute_modifiers",
        "minecraft:intangible_projectile",
        "minecraft:unbreakable",
        "minecraft:ominous_bottle_amplifier",
        "minecraft:suspicious_stew_effects",
        "minecraft:block_state",
        "minecraft:entity_data",
        "minecraft:block_entity_data",
        "minecraft:can_break",
        "minecraft:can_place_on",
        "minecraft:damage"
    );
    private static final Set<String> COMMON_TOOLTIP_COMPONENT_IDS = Set.of(
        "minecraft:attribute_modifiers",
        "minecraft:unbreakable",
        "minecraft:enchantments",
        "minecraft:stored_enchantments",
        "minecraft:lore",
        "minecraft:can_break",
        "minecraft:can_place_on",
        "minecraft:dyed_color",
        "minecraft:trim",
        "minecraft:potion_contents",
        "minecraft:container",
        "minecraft:damage"
    );

    private final ItemEditor host;
    private final ItemLabConfig preferences = ItemLabConfig.get();
    private final ImBoolean overrideMaxStackSize = new ImBoolean(false);
    private final ImInt maxStackSize = new ImInt(64);
    private final ImBoolean overrideMaxDamage = new ImBoolean(false);
    private final ImInt maxDamage = new ImInt(1);
    private final ImInt damage = new ImInt(0);
    private final ImInt rarityMode = new ImInt(0);
    private final ImBoolean unbreakable = new ImBoolean(false);
    private final ImInt glintMode = new ImInt(0);
    private final ImBoolean glider = new ImBoolean(false);
    private final ImBoolean intangibleProjectile = new ImBoolean(false);
    private final ImBoolean hideTooltip = new ImBoolean(false);
    private final ImBoolean showAllTooltipComponents = new ImBoolean(preferences.showAllTooltipComponents());
    private final ImBoolean showPresentTooltipComponentsOnly = new ImBoolean(preferences.showPresentTooltipComponentsOnly());
    private final ImString tooltipComponentSearch = new ImString(256);
    private final Set<DataComponentType<?>> hiddenTooltipComponents = new LinkedHashSet<>();
    private final ImInt repairCost = new ImInt(0);
    private boolean focusTooltipVisibilityControl;
    private boolean nonTooltipDirty;
    private boolean tooltipDirty;

    public FlagEditorPanel(ItemEditor host) {
        this.host = host;
    }

    @Override
    public boolean isDirty() {
        refreshDirtyState();
        return nonTooltipDirty || tooltipDirty;
    }

    public boolean isComponentDirty(DataComponentType<?> type) {
        refreshDirtyState();
        return type == DataComponents.TOOLTIP_DISPLAY && tooltipDirty;
    }

    private void refreshDirtyState() {
        if (!nonTooltipDirty && !tooltipDirty) return;
        try {
            ItemStack edited = buildEditedStack();
            TooltipDisplay originalTooltip = host.draft().get(DataComponents.TOOLTIP_DISPLAY);
            TooltipDisplay editedTooltip = edited.get(DataComponents.TOOLTIP_DISPLAY);
            tooltipDirty = !java.util.Objects.equals(originalTooltip, editedTooltip);

            if (originalTooltip == null) edited.remove(DataComponents.TOOLTIP_DISPLAY);
            else edited.set(DataComponents.TOOLTIP_DISPLAY, originalTooltip);
            nonTooltipDirty = !ItemStack.matches(host.draft(), edited);
        } catch (RuntimeException ignored) {
            // Invalid in-progress values remain pending.
        }
    }

    public void draw(Minecraft client) {
        ImGui.spacing();
        if (focusPendingToolbar) {
            ImGui.setKeyboardFocusHere();
            focusPendingToolbar = false;
        }
        EditorUi.drawSaveDiscardActions(isDirty(), () -> save(client, true), () -> {
            sync(host.draft());
            host.setStatus(true, tr("message.discarded"));
        });

        EditorUi.beginScrollableChild("flags_editor", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            host.drawTooltipPreview(client, previewStack(), "flags");
            ImGui.spacing();
            drawBehavior();
            ImGui.spacing();
            drawStackAndDurability();
            ImGui.spacing();
            drawTooltipVisibilityCard();
        } finally {
            ImGui.endChild();
        }
    }

    private void drawBehavior() {
        if (!EditorUi.beginCollapsibleCard(
            "flags_behavior",
            tr("flags.behavior"),
            ImGuiTableFlags.SizingStretchProp,
            host.componentSectionStartsExpanded(
                DataComponents.UNBREAKABLE,
                DataComponents.GLIDER,
                DataComponents.INTANGIBLE_PROJECTILE,
                DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
                DataComponents.RARITY
            )
        )) return;

        float widestBehaviorLabel = Math.max(
            ImGui.calcTextSizeX(tr("flags.unbreakable")),
            Math.max(
                ImGui.calcTextSizeX(tr("flags.glider")),
                ImGui.calcTextSizeX(tr("flags.intangible_projectile"))
            )
        );
        int behaviorColumns = ImGui.getContentRegionAvailX() >= (widestBehaviorLabel + 64.0f) * 2.0f
            ? 2
            : 1;
        if (ImGui.beginTable(
            "behavior_flags",
            behaviorColumns,
            ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoPadOuterX
        )) {
            ImGui.tableNextColumn();
            nonTooltipDirty |= ImGui.checkbox(tr("flags.unbreakable"), unbreakable);
            host.drawDiamondFireComponentHint(DataComponents.UNBREAKABLE);
            ImGui.tableNextColumn();
            nonTooltipDirty |= ImGui.checkbox(tr("flags.glider"), glider);
            host.drawDiamondFireComponentHint(DataComponents.GLIDER);
            ImGui.tableNextColumn();
            nonTooltipDirty |= ImGui.checkbox(tr("flags.intangible_projectile"), intangibleProjectile);
            host.drawDiamondFireComponentHint(DataComponents.INTANGIBLE_PROJECTILE);
            ImGui.endTable();
        }

        ImGui.text(tr("flags.glint"));
        host.drawDiamondFireComponentHint(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.combo("##flag_glint", glintMode, glintOptions())) nonTooltipDirty = true;
        ImGui.text(tr("flags.rarity"));
        host.drawDiamondFireComponentHint(DataComponents.RARITY);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.combo("##flag_rarity", rarityMode, rarityOptions())) nonTooltipDirty = true;

        EditorUi.endCard();
    }

    private void drawStackAndDurability() {
        if (!EditorUi.beginCollapsibleCard(
            "flags_stack_durability",
            tr("flags.stack_durability"),
            ImGuiTableFlags.SizingStretchProp,
            host.componentSectionStartsExpanded(
                DataComponents.MAX_STACK_SIZE,
                DataComponents.MAX_DAMAGE,
                DataComponents.DAMAGE,
                DataComponents.REPAIR_COST
            ),
            focusMaxStackSize
        )) return;

        nonTooltipDirty |= ImGui.checkbox(tr("flags.override_stack_size"), overrideMaxStackSize);
        host.drawDiamondFireComponentHint(DataComponents.MAX_STACK_SIZE);
        ImGui.beginDisabled(!overrideMaxStackSize.get());
        ImGui.text(tr("flags.maximum_stack_size"));
        ImGui.setNextItemWidth(-1.0f);
        if (focusMaxStackSize) {
            ImGui.setKeyboardFocusHere();
            focusMaxStackSize = false;
        }
        if (ImGui.inputInt("##maximum_stack_size", maxStackSize, 1, 8)) {
            maxStackSize.set(Math.clamp(maxStackSize.get(), 1, MAX_ITEM_COUNT));
            nonTooltipDirty = true;
        }
        ImGui.endDisabled();

        nonTooltipDirty |= ImGui.checkbox(tr("flags.override_max_damage"), overrideMaxDamage);
        host.drawDiamondFireComponentHint(DataComponents.MAX_DAMAGE);
        ImGui.beginDisabled(!overrideMaxDamage.get());
        ImGui.text(tr("flags.maximum_damage"));
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.inputInt("##maximum_damage", maxDamage, 1, 10)) {
            maxDamage.set(Math.clamp(maxDamage.get(), 1, Integer.MAX_VALUE));
            nonTooltipDirty = true;
        }
        ImGui.endDisabled();

        int effectiveMaxDamage = overrideMaxDamage.get() ? maxDamage.get() : Math.max(0, host.draft().getMaxDamage());
        ImGui.beginDisabled(effectiveMaxDamage == 0);
        ImGui.text(tr("flags.current_damage"));
        host.drawDiamondFireComponentHint(DataComponents.DAMAGE);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.inputInt("##current_damage", damage, 1, 10)) {
            damage.set(Math.clamp(damage.get(), 0, Math.max(0, effectiveMaxDamage)));
            nonTooltipDirty = true;
        }
        ImGui.endDisabled();

        ImGui.text(tr("flags.repair_cost"));
        host.drawDiamondFireComponentHint(DataComponents.REPAIR_COST);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.inputInt("##repair_cost", repairCost, 1, 5)) {
            repairCost.set(Math.max(0, repairCost.get()));
            nonTooltipDirty = true;
        }

        EditorUi.endCard();
    }

    private void drawTooltipVisibilityCard() {
        if (!EditorUi.beginCollapsibleCard(
            "flags_tooltip_visibility",
            tr("flags.tooltip_visibility"),
            ImGuiTableFlags.SizingStretchProp,
            host.componentSectionStartsExpanded(DataComponents.TOOLTIP_DISPLAY),
            focusTooltipVisibilityControl
        )) return;
        tooltipDirty |= drawTooltipVisibility();
        EditorUi.endCard();
    }

    private static String[] glintOptions() {
        return new String[]{tr("option.default"), tr("option.always_on"), tr("option.always_off")};
    }

    private static String[] rarityOptions() {
        return new String[]{
            tr("option.item_default"), tr("rarity.common"), tr("rarity.uncommon"),
            tr("rarity.rare"), tr("rarity.epic")
        };
    }

    /**
     * Draws the selected tooltip_display component without exposing its codec shape.
     */
    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (type != DataComponents.TOOLTIP_DISPLAY) {
            return;
        }

        ImGui.spacing();
        EditorUi.drawSaveDiscardActions(tooltipDirty, () -> saveTooltipDisplay(client, true), () -> {
            syncTooltipDisplay(host.draft());
            host.setStatus(true, tr("message.discarded"));
        });

        EditorUi.beginScrollableChild(
            "tooltip_display_component_editor",
            0.0f,
            Math.max(1.0f, ImGui.getContentRegionAvailY()),
            false
        );
        try {
            host.drawTooltipPreview(client, tooltipPreviewStack(), "tooltip_display_component");
            ImGui.spacing();
            drawTooltipVisibilityCard();
        } finally {
            ImGui.endChild();
        }
    }

    private boolean drawTooltipVisibility() {
        host.drawDiamondFireComponentHint(DataComponents.TOOLTIP_DISPLAY);
        if (focusTooltipVisibilityControl) {
            ImGui.setKeyboardFocusHere();
            focusTooltipVisibilityControl = false;
        }
        boolean changed = ImGui.checkbox(tr("flags.hide_tooltip"), hideTooltip);
        if (ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("flags.hide_tooltip.tooltip"));
        }

        ImGui.spacing();
        ImGui.setNextItemWidth(-1.0f);
        inputTextWithHint(
            "##tooltip_component_search",
            tr("flags.tooltip_components.search"),
            tooltipComponentSearch
        );

        if (ImGui.checkbox(tr("flags.show_all_components"), showAllTooltipComponents)) {
            preferences.setShowAllTooltipComponents(showAllTooltipComponents.get());
        }
        if (ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("flags.show_all_components.tooltip"));
        }
        if (ImGui.checkbox(tr("flags.present_only"), showPresentTooltipComponentsOnly)) {
            preferences.setShowPresentTooltipComponentsOnly(showPresentTooltipComponentsOnly.get());
        }
        if (ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("flags.present_only.tooltip"));
        }

        if (ImGui.button(tr("flags.hide_except_lore"))) {
            showOnlyLore();
            changed = true;
        }
        if (ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("flags.hide_except_lore.tooltip"));
        }

        List<TooltipComponentOption> options = tooltipComponentOptions();
        if (!options.isEmpty()) ImGui.textDisabled(tr("flags.matching_components", options.size()));

        float buttonWidth = Math.max(ImGui.calcTextSize(tr("flags.show_filtered")).x + ImGui.getStyle().getFramePaddingX() * 2.0f, 105.0f);
        boolean hasOptions = !options.isEmpty();
        ImGui.beginDisabled(!hasOptions);
        if (ImGui.button(tr("flags.hide_filtered"), buttonWidth, 0.0f)) {
            for (TooltipComponentOption option : options) {
                hiddenTooltipComponents.add(option.type());
            }
            changed = true;
        }
        EditorUi.sameLineIfFits(tr("flags.show_filtered"));
        if (ImGui.button(tr("flags.show_filtered"), buttonWidth, 0.0f)) {
            for (TooltipComponentOption option : options) {
                hiddenTooltipComponents.remove(option.type());
            }
            changed = true;
        }
        ImGui.endDisabled();

        float listHeight = Math.clamp(options.size() * ImGui.getTextLineHeightWithSpacing() + 14.0f, 94.0f, 300.0f);
        EditorUi.beginScrollableChild(
            "tooltip_component_visibility_list",
            0.0f,
            listHeight,
            ImGuiChildFlags.Borders | ImGuiChildFlags.AlwaysUseWindowPadding
        );
        try {
            if (!options.isEmpty() && ImGui.beginTable("tooltip_component_visibility_table", 2, ImGuiTableFlags.SizingStretchProp)) {
                ImGui.tableSetupColumn("Component", ImGuiTableColumnFlags.WidthStretch);
                float statusColumnWidth = ImGui.calcTextSizeX(tr("flags.present"))
                    + ImGui.getStyle().getCellPaddingX() * 2.0f
                    + 12.0f;
                ImGui.tableSetupColumn("Item", ImGuiTableColumnFlags.WidthFixed, statusColumnWidth);
                boolean drewCommonHeading = false;
                boolean drewOtherHeading = false;
                for (TooltipComponentOption option : options) {
                    if (option.common() && !drewCommonHeading) {
                        drawTooltipGroupHeading(tr("flags.group.common"));
                        drewCommonHeading = true;
                    } else if (!option.common() && !drewOtherHeading) {
                        drawTooltipGroupHeading(tr("flags.group.other"));
                        drewOtherHeading = true;
                    }
                    ImGui.pushID(option.id());
                    ImGui.tableNextRow();
                    ImGui.tableNextColumn();
                    boolean hidden = hiddenTooltipComponents.contains(option.type());
                    ImBoolean hiddenValue = new ImBoolean(hidden);
                    if (ImGui.checkbox(option.label() + "##hidden", hiddenValue)) {
                        if (hiddenValue.get()) hiddenTooltipComponents.add(option.type());
                        else hiddenTooltipComponents.remove(option.type());
                        changed = true;
                    }
                    if (ImGui.isItemHovered()) {
                        EditorUi.setTooltip(option.id() + (hiddenValue.get()
                            ? "\n" + tr("flags.hidden")
                            : "\n" + tr("flags.visible")));
                    }
                    ImGui.tableNextColumn();
                    ImGui.textDisabled(option.onItem() ? tr("flags.present") : "-");
                    ImGui.popID();
                }
                ImGui.endTable();
            } else if (options.isEmpty()) {
                EditorUi.drawEmptyState(tr("flags.tooltip_components.empty"));
            }
        } finally {
            ImGui.endChild();
        }

        return changed;
    }

    private void showOnlyLore() {
        hideTooltip.set(false);
        Set<DataComponentType<?>> activeTooltipProviders = activeTooltipProviderTypes();
        for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            if (type == DataComponents.TOOLTIP_DISPLAY || type == DataComponents.LORE) {
                continue;
            }
            Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (identifier != null && (VANILLA_TOOLTIP_COMPONENT_IDS.contains(identifier.toString())
                || activeTooltipProviders.contains(type)
                || hiddenTooltipComponents.contains(type))) {
                hiddenTooltipComponents.add(type);
            }
        }
        hiddenTooltipComponents.remove(DataComponents.LORE);
    }

    private static void drawTooltipGroupHeading(String label) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.spacing();
        ImGui.textDisabled(label);
        ImGui.tableNextColumn();
        ImGui.spacing();
    }

    public void sync(ItemStack draft) {
        focusPendingToolbar = false;
        focusMaxStackSize = false;
        overrideMaxStackSize.set(draft.hasNonDefault(DataComponents.MAX_STACK_SIZE));
        maxStackSize.set(Math.max(1, draft.getMaxStackSize()));
        overrideMaxDamage.set(draft.hasNonDefault(DataComponents.MAX_DAMAGE));
        maxDamage.set(Math.max(1, draft.getMaxDamage()));
        damage.set(Math.max(0, draft.getDamageValue()));
        repairCost.set(Math.max(0, draft.getOrDefault(DataComponents.REPAIR_COST, 0)));
        unbreakable.set(draft.has(DataComponents.UNBREAKABLE));
        glider.set(draft.has(DataComponents.GLIDER));
        intangibleProjectile.set(draft.has(DataComponents.INTANGIBLE_PROJECTILE));
        Boolean glint = draft.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        glintMode.set(glint == null ? 0 : glint ? 1 : 2);
        Rarity rarity = draft.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        rarityMode.set(draft.hasNonDefault(DataComponents.RARITY) ? rarity.ordinal() + 1 : 0);

        syncTooltipDisplay(draft);
        focusTooltipVisibilityControl = false;
        nonTooltipDirty = false;
    }

    public void syncComponent(DataComponentType<?> type, ItemStack draft) {
        if (type == DataComponents.MAX_STACK_SIZE) {
            overrideMaxStackSize.set(draft.hasNonDefault(DataComponents.MAX_STACK_SIZE));
            maxStackSize.set(Math.max(1, draft.getMaxStackSize()));
        } else if (type == DataComponents.MAX_DAMAGE) {
            overrideMaxDamage.set(draft.hasNonDefault(DataComponents.MAX_DAMAGE));
            maxDamage.set(Math.max(1, draft.getMaxDamage()));
        } else if (type == DataComponents.DAMAGE) {
            damage.set(Math.max(0, draft.getDamageValue()));
        } else if (type == DataComponents.REPAIR_COST) {
            repairCost.set(Math.max(0, draft.getOrDefault(DataComponents.REPAIR_COST, 0)));
        } else if (type == DataComponents.UNBREAKABLE) {
            unbreakable.set(draft.has(DataComponents.UNBREAKABLE));
        } else if (type == DataComponents.GLIDER) {
            glider.set(draft.has(DataComponents.GLIDER));
        } else if (type == DataComponents.INTANGIBLE_PROJECTILE) {
            intangibleProjectile.set(draft.has(DataComponents.INTANGIBLE_PROJECTILE));
        } else if (type == DataComponents.ENCHANTMENT_GLINT_OVERRIDE) {
            Boolean glint = draft.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            glintMode.set(glint == null ? 0 : glint ? 1 : 2);
        } else if (type == DataComponents.RARITY) {
            Rarity rarity = draft.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
            rarityMode.set(draft.hasNonDefault(DataComponents.RARITY) ? rarity.ordinal() + 1 : 0);
        } else if (type == DataComponents.TOOLTIP_DISPLAY) {
            syncTooltipDisplay(draft);
        }
        focusTooltipVisibilityControl = type == DataComponents.TOOLTIP_DISPLAY;
    }

    public boolean save(Minecraft client, boolean announce) {
        try {
            ItemStack edited = buildEditedStack();
            host.acceptSectionDraft(client, edited);
            sync(edited);
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

    private boolean saveTooltipDisplay(Minecraft client, boolean announce) {
        try {
            ItemStack edited = host.draft().copy();
            applyTooltipDisplay(edited);
            ItemStackCodecs.validateStack(edited);
            host.acceptSectionDraft(client, edited);
            syncTooltipDisplay(edited);
            if (announce) {
                host.setStatus(true, tr("message.saved"));
            }
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    private ItemStack tooltipPreviewStack() {
        try {
            ItemStack edited = host.draft().copy();
            applyTooltipDisplay(edited);
            ItemStackCodecs.validateStack(edited);
            return edited;
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack buildEditedStack() {
        ItemStack edited = host.draft().copy();
        if (overrideMaxStackSize.get()) {
            edited.set(DataComponents.MAX_STACK_SIZE, Math.clamp(maxStackSize.get(), 1, MAX_ITEM_COUNT));
        } else {
            ItemStackCodecs.resetComponentToPrototype(edited, DataComponents.MAX_STACK_SIZE);
        }
        if (overrideMaxDamage.get()) {
            edited.set(DataComponents.MAX_DAMAGE, Math.max(1, maxDamage.get()));
            if (edited.getMaxStackSize() > 1) {
                if (overrideMaxStackSize.get()) {
                    throw new IllegalArgumentException(tr("flags.validation.damageable_stack_size"));
                }
                edited.set(DataComponents.MAX_STACK_SIZE, 1);
            }
        } else {
            ItemStackCodecs.resetComponentToPrototype(edited, DataComponents.MAX_DAMAGE);
        }

        setUnitFlag(edited, DataComponents.UNBREAKABLE, unbreakable.get());
        setUnitFlag(edited, DataComponents.GLIDER, glider.get());
        setUnitFlag(edited, DataComponents.INTANGIBLE_PROJECTILE, intangibleProjectile.get());

        switch (glintMode.get()) {
            case 1 -> edited.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            case 2 -> edited.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            default -> ItemStackCodecs.resetComponentToPrototype(edited, DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
        if (rarityMode.get() == 0) {
            ItemStackCodecs.resetComponentToPrototype(edited, DataComponents.RARITY);
        } else {
            Rarity[] rarities = Rarity.values();
            edited.set(DataComponents.RARITY, rarities[Math.clamp(rarityMode.get() - 1, 0, rarities.length - 1)]);
        }
        if (repairCost.get() == 0) edited.remove(DataComponents.REPAIR_COST);
        else edited.set(DataComponents.REPAIR_COST, Math.max(0, repairCost.get()));
        if (edited.getMaxDamage() > 0) edited.setDamageValue(Math.clamp(damage.get(), 0, edited.getMaxDamage()));
        else edited.remove(DataComponents.DAMAGE);

        applyTooltipDisplay(edited);

        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack();
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(state);
        nonTooltipDirty = true;
        tooltipDirty = true;
    }

    public boolean isMaxStackSizeOverridden() {
        return overrideMaxStackSize.get();
    }

    public void restorePending(Minecraft client, ItemStack state, boolean stackSizeOverride) {
        restorePending(client, state);
        // Native SNBT omits prototype-equivalent values. The override choice still
        // matters when a pending header/signing edit selects a different prototype.
        overrideMaxStackSize.set(stackSizeOverride);
    }

    private void syncTooltipDisplay(ItemStack draft) {
        TooltipDisplay tooltip = draft.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        hideTooltip.set(tooltip.hideTooltip());
        hiddenTooltipComponents.clear();
        hiddenTooltipComponents.addAll(tooltip.hiddenComponents());
        tooltipDirty = false;
    }

    private void applyTooltipDisplay(ItemStack edited) {
        edited.set(
            DataComponents.TOOLTIP_DISPLAY,
            new TooltipDisplay(hideTooltip.get(), new LinkedHashSet<>(hiddenTooltipComponents))
        );
    }

    private List<TooltipComponentOption> tooltipComponentOptions() {
        Set<DataComponentType<?>> activeTooltipProviders = activeTooltipProviderTypes();
        String query = tooltipComponentSearch.get().trim().toLowerCase(Locale.ROOT);
        List<TooltipComponentOption> options = new ArrayList<>();
        for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            if (type == DataComponents.TOOLTIP_DISPLAY) {
                continue;
            }
            Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (identifier == null) {
                continue;
            }
            String id = identifier.toString();
            boolean activeProvider = activeTooltipProviders.contains(type);
            boolean hidden = hiddenTooltipComponents.contains(type);
            boolean onItem = host.draft().has(type);
            boolean relevant = VANILLA_TOOLTIP_COMPONENT_IDS.contains(id) || activeProvider || hidden;
            if (!showAllTooltipComponents.get() && !relevant) {
                continue;
            }
            if (showPresentTooltipComponentsOnly.get() && !onItem) {
                continue;
            }

            String label = humanize(identifier);
            if (!query.isEmpty()
                && !id.toLowerCase(Locale.ROOT).contains(query)
                && !label.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            options.add(new TooltipComponentOption(type, id, label, onItem, COMMON_TOOLTIP_COMPONENT_IDS.contains(id)));
        }
        options.sort(Comparator
            .comparing(TooltipComponentOption::common).reversed()
            .thenComparing(TooltipComponentOption::id));
        return options;
    }

    private Set<DataComponentType<?>> activeTooltipProviderTypes() {
        Set<DataComponentType<?>> activeTooltipProviders = new LinkedHashSet<>();
        for (TypedDataComponent<?> component : host.draft().getComponents()) {
            if (component.value() instanceof TooltipProvider) {
                activeTooltipProviders.add(component.type());
            }
        }
        return activeTooltipProviders;
    }

    private static String humanize(Identifier identifier) {
        String path = identifier.getPath().replace('_', ' ').replace('/', ' ');
        if (path.isEmpty()) {
            return identifier.toString();
        }
        String label = Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return "minecraft".equals(identifier.getNamespace())
            ? label
            : label + " (" + identifier.getNamespace() + ")";
    }

    private static void setUnitFlag(ItemStack stack, DataComponentType<Unit> type, boolean enabled) {
        if (enabled) stack.set(type, Unit.INSTANCE);
        else stack.remove(type);
    }

    private record TooltipComponentOption(
        DataComponentType<?> type,
        String id,
        String label,
        boolean onItem,
        boolean common
    ) {
    }
}
