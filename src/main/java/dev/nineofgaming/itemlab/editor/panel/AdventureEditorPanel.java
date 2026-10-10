package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.IdentifierCollectionEditor;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.control.CodeEditorInput;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeLanguage;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Structured editor for the adventure-mode can-break and can-place-on predicates.
 */
public final class AdventureEditorPanel extends ComponentEditorPanel {
    private static final int SOURCE_CAPACITY = 65_536;
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong();

    private final SectionModel canBreak = new SectionModel(DataComponents.CAN_BREAK, "can_break");
    private final SectionModel canPlaceOn = new SectionModel(DataComponents.CAN_PLACE_ON, "can_place_on");
    private List<IdSuggestion> blockSuggestions = List.of();
    private Set<String> knownBlocksAndTags = Set.of();

    public AdventureEditorPanel(ItemEditor host) {
        super(host, "adventure");
    }

    @Override
    public void requestPendingFocus() {
        clearRecoveryRequests();
        Minecraft client = Minecraft.getInstance();
        for (SectionModel section : List.of(canBreak, canPlaceOn)) {
            if (!section.present.get()) continue;
            if (section.predicates.isEmpty()) {
                requestPendingField(section.key, "add_predicate");
                return;
            }
            // Match the builder's order: validate every row before decoding the section.
            for (PredicateRow row : section.predicates) {
                if (row.blocksPresent.get()) {
                    int invalid = invalidBlockIndex(section, row);
                    if (invalid >= 0) {
                        row.blocksFocus.requestRow(invalid);
                        requestPredicateField(section, row, "blocks");
                        return;
                    }
                }
                if (row.statePresent.get()) {
                    for (int index = 0; index < row.states.size(); index++) {
                        StateRow state = row.states.get(index);
                        String field = null;
                        if (!stateKeyProblem(row, index).isBlank()) field = "name";
                        else if (state.mode.get() == 0) {
                            if (!stateValueProblem(row, state.name.get(), state.exact.get(), true).isBlank())
                                field = "exact";
                        } else if (!stateValueProblem(row, state.name.get(), state.minimum.get(), false).isBlank()
                            || (state.minimum.get().trim().isEmpty() && state.maximum.get().trim().isEmpty()))
                            field = "minimum";
                        else if (!stateValueProblem(row, state.name.get(), state.maximum.get(), false).isBlank())
                            field = "maximum";
                        if (field != null) {
                            row.focusStateRowId = state.id;
                            requestPredicateField(section, row, "state/" + state.id + "/" + field);
                            return;
                        }
                    }
                }
                if (row.nbtPresent.get() && !snbtProblem(row.nbtSource.get()).isBlank()) {
                    requestPredicateField(section, row, "nbt");
                    return;
                }
                if (row.componentsPresent.get() && !componentSnbtProblem(row.componentsSource.get()).isBlank()) {
                    requestPredicateField(section, row, "components");
                    return;
                }
                if (row.componentPredicatesPresent.get() && !componentSnbtProblem(row.componentPredicatesSource.get()).isBlank()) {
                    requestPredicateField(section, row, "predicates");
                    return;
                }
            }
            if (client.level != null) {
                try {
                    applySection(client, host.draft().copy(), section);
                } catch (RuntimeException ignored) {
                    // Syntactically valid SNBT can still contain invalid native component values.
                    for (PredicateRow row : section.predicates) {
                        try {
                            CompoundTag value = buildPredicateNbt(row);
                            Tag components = value.remove("components");
                            Tag predicates = value.remove("predicates");
                            if (invalidPredicate(client, section, value)) break;
                            if (components != null) {
                                value.put("components", components);
                                if (invalidPredicate(client, section, value)) {
                                    requestPredicateField(section, row, "components");
                                    return;
                                }
                            }
                            if (predicates != null) {
                                value.put("predicates", predicates);
                                if (invalidPredicate(client, section, value)) {
                                    requestPredicateField(section, row, "predicates");
                                    return;
                                }
                            }
                        } catch (RuntimeException unclassified) {
                            break;
                        }
                    }
                    requestPendingField(section.key, "$enabled");
                    return;
                }
            }
        }
        requestPendingField(canPlaceOn.present.get() && !canBreak.present.get() ? canPlaceOn.key : canBreak.key, "$enabled");
    }

    private void clearRecoveryRequests() {
        for (SectionModel section : List.of(canBreak, canPlaceOn)) {
            section.focusPredicateRowId = 0L;
            section.focusAddPredicateButton = false;
            section.focusPresenceToggle = false;
            for (PredicateRow row : section.predicates) {
                row.focusStateRowId = 0L;
                row.focusAddStateButton = false;
                row.blocksFocus.clear();
                row.nbtEditor.cancelFocusRequest();
                row.componentsEditor.cancelFocusRequest();
                row.componentPredicatesEditor.cancelFocusRequest();
            }
        }
    }

    private int invalidBlockIndex(SectionModel section, PredicateRow row) {
        List<String> values = new ArrayList<>();
        if (row.blocks.isJsonPrimitive()) values.add(row.blocks.getAsString());
        else if (row.blocks.isJsonArray()) {
            for (JsonElement entry : row.blocks.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) values.add(entry.getAsString());
            }
        }
        if (values.isEmpty()) return 0;
        for (int index = 0; index < values.size(); index++) {
            String value = pendingBufferText(blockBufferPath(section, row) + "/@identifier/" + index, values.get(index)).trim();
            if ((value.startsWith("#") && values.size() > 1) || !blockSelectionProblem(new JsonPrimitive(value)).isBlank())
                return index;
        }
        return blockSelectionProblem(row.blocks).isBlank() ? -1 : 0;
    }

    private boolean invalidPredicate(Minecraft client, SectionModel section, CompoundTag value) {
        try {
            ItemStackCodecs.decodeAndSetComponentNbt(client, host.draft().copy(), section.type, value);
            return false;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private void requestPredicateField(SectionModel section, PredicateRow row, String field) {
        section.focusPredicateRowId = row.id;
        requestPendingField(section.key, predicateField(row, field));
        switch (field) {
            case "nbt" -> row.nbtEditor.requestFocus();
            case "components" -> row.componentsEditor.requestFocus();
            case "predicates" -> row.componentPredicatesEditor.requestFocus();
            default -> {
            }
        }
    }

    private static String predicateField(PredicateRow row, String field) {
        return "predicate/" + row.id + "/" + field;
    }

    private static String blockBufferPath(SectionModel section, PredicateRow row) {
        return "adventure/" + section.key + "/" + row.id + "/blocks";
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.CAN_BREAK || type == DataComponents.CAN_PLACE_ON;
    }

    public void setCatalog(EditorCatalog catalog) {
        blockSuggestions = catalog.registryIdsAndTags("block");
        Set<String> known = new HashSet<>();
        blockSuggestions.forEach(suggestion -> known.add(suggestion.id()));
        knownBlocksAndTags = Set.copyOf(known);
    }

    public void sync(Minecraft client, ItemStack stack) {
        loadSection(client, stack, canBreak);
        loadSection(client, stack, canPlaceOn);
        dirty = false;
        resetEditorUi();
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        sync(client, stack);
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        drawEditor(client, type);
    }

    private void drawEditor(Minecraft client, DataComponentType<?> focusedType) {
        if (drawPanelToolbar(
            () -> save(client, true),
            () -> sync(client, host.draft())
        )) return;

        EditorUi.beginScrollableChild(
            "adventure_editor",
            0.0f,
            Math.max(1.0f, ImGui.getContentRegionAvailY()),
            false
        );
        try {
            ItemStack preview = previewStack(client);
            host.setItemIconPreview(preview);
            host.drawTooltipPreview(client, preview, "adventure_restrictions");
            ImGui.spacing();
            drawSections(client, focusedType);
        } finally {
            ImGui.endChild();
        }
    }

    private void drawSections(Minecraft client, DataComponentType<?> focusedType) {
        if (focusedType == null || focusedType == DataComponents.CAN_BREAK) {
            drawSection(client, canBreak);
        }
        if (focusedType == null || focusedType == DataComponents.CAN_PLACE_ON) {
            drawSection(client, canPlaceOn);
        }
    }

    private void drawSection(Minecraft client, SectionModel section) {
        if (!EditorUi.beginCard(
            "adventure_" + section.key,
            1,
            ImGuiTableFlags.SizingStretchProp,
            hasPendingSectionFocus(section.key)
        )) return;
        ImGui.tableNextColumn();

        drawSectionContents(client, section);
        EditorUi.endCard();
        OrderedListDragDrop.autoScrollCurrentWindow(section.predicateDragScope);
        ImGui.spacing();
    }

    private void drawSectionContents(Minecraft client, SectionModel section) {
        boolean reveal = hasPendingSectionFocus(section.key);
        if (section.focusPresenceToggle) {
            ImGui.setKeyboardFocusHere();
            section.focusPresenceToggle = false;
        }
        applyPendingFieldFocus(section.key, "$enabled");
        if (ImGui.checkbox("##enabled_" + section.key, section.present)) {
            section.focusPresenceToggle = false;
            if (section.present.get() && section.predicates.isEmpty()) {
                PredicateRow added = PredicateRow.createDefault();
                section.predicates.add(added);
                section.focusPredicateRowId = added.id;
                section.focusAddPredicateButton = false;
            } else if (!section.present.get()) {
                section.focusPredicateRowId = 0L;
                section.focusAddPredicateButton = false;
            }
            resetEditorUi();
            dirty = true;
        }
        ImGui.sameLine();
        boolean expanded = EditorUi.collapsibleHeader(
            "adventure_section_" + section.key,
            tr("adventure." + section.key),
            host.componentSectionStartsExpanded(section.type),
            reveal
        );
        if (ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("adventure." + section.key + ".help"));
        }
        host.drawDiamondFireComponentHint(section.type);
        if (!expanded) return;
        ImGui.beginDisabled(!section.present.get());
        ImGui.spacing();
        drawAddPredicateAction(section, "top", false);

        int actionIndex = -1;
        EditorUi.ListAction pendingAction = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < section.predicates.size(); index++) {
            PredicateRow row = section.predicates.get(index);
            ImGui.pushID(section.key + "_" + row.id);
            EditorUi.ListAction action = drawPredicate(client, section, index, row);
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                section.predicateDragScope,
                row.id,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            if (action != EditorUi.ListAction.NONE) {
                actionIndex = index;
                pendingAction = action;
            }
        }
        if (reorderRequest != null) {
            applyPredicateReorder(section, reorderRequest);
        } else if (actionIndex >= 0) {
            applyPredicateAction(section, actionIndex, pendingAction);
        }
        if (section.predicates.isEmpty()) EditorUi.drawEmptyState(tr("adventure.predicate.empty"));
        drawAddPredicateAction(section, "bottom", true);

        ImGui.endDisabled();
    }

    private void drawAddPredicateAction(SectionModel section, String placement, boolean acceptFocus) {
        if (acceptFocus) applyPendingFieldFocus(section.key, "add_predicate");
        if (acceptFocus && section.focusAddPredicateButton) {
            ImGui.setKeyboardFocusHere();
            section.focusAddPredicateButton = false;
        }
        if (EditorIcons.button(
            "add_" + section.key + "_predicate_" + placement,
            EditorIcons.Icon.ADD,
            tr("adventure.predicate.add")
        )) {
            PredicateRow added = PredicateRow.createDefault();
            section.predicates.add(added);
            section.focusPredicateRowId = added.id;
            section.focusAddPredicateButton = false;
            resetEditorUi();
            dirty = true;
        }
    }

    private EditorUi.ListAction drawPredicate(
        Minecraft client,
        SectionModel section,
        int index,
        PredicateRow row
    ) {
        boolean requestFocus = section.focusPredicateRowId == row.id;
        EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
            "adventure_predicate",
            EditorUi.identifiedTitle(
                tr("adventure.predicate.entry", index + 1),
                predicateSummary(row)
            ),
            ImGuiTableFlags.SizingStretchProp,
            index,
            section.predicates.size(),
            true,
            true,
            true,
            requestFocus,
            section.predicateDragScope,
            row.id
        );
        if (card.focusApplied()) section.focusPredicateRowId = 0L;
        if (card.expanded()) {
            drawBlocks(section, row);
            drawStateConditions(section, row);
            drawNbtCondition(section, row);
            drawSnbtCondition(
                section,
                row,
                "components",
                tr("adventure.components"),
                tr("adventure.components.help"),
                row.componentsPresent,
                row.componentsSource,
                row.componentsEditor
            );
            drawSnbtCondition(
                section,
                row,
                "predicates",
                tr("adventure.component_predicates"),
                tr("adventure.component_predicates.help"),
                row.componentPredicatesPresent,
                row.componentPredicatesSource,
                row.componentPredicatesEditor
            );
            EditorUi.endCard();
        }
        return card.action();
    }

    private void drawBlocks(SectionModel section, PredicateRow row) {
        if (ImGui.checkbox(tr("adventure.blocks.enabled"), row.blocksPresent)) dirty = true;
        if (ImGui.isItemHovered()) EditorUi.setTooltip(tr("adventure.blocks.help"));
        if (!row.blocksPresent.get()) {
            ImGui.spacing();
            return;
        }

        if (consumePendingFieldFocus(section.key, predicateField(row, "blocks"))) ImGui.setScrollHereY(0.35f);
        if (IdentifierCollectionEditor.draw(
            host,
            section.key + "_blocks",
            blockBufferPath(section, row),
            row.blocks,
            value -> row.blocks = value,
            blockSuggestions,
            tr("field_purpose.block_identifier"),
            "minecraft:stone",
            tr("adventure.block"),
            textBuffers,
            row.blocksFocus
        )) dirty = true;
        ItemEditorTheme.drawFieldProblem(blockSelectionProblem(row.blocks));
        ImGui.spacing();
    }

    private void drawStateConditions(SectionModel section, PredicateRow row) {
        if (ImGui.checkbox(tr("adventure.state.enabled"), row.statePresent)) {
            if (row.statePresent.get() && row.states.isEmpty()) {
                row.focusAddStateButton = true;
            } else if (!row.statePresent.get()) {
                row.focusStateRowId = 0L;
                row.focusAddStateButton = false;
            }
            resetEditorUi();
            dirty = true;
        }
        if (ImGui.isItemHovered()) EditorUi.setTooltip(tr("adventure.state.help"));
        if (!row.statePresent.get()) {
            ImGui.spacing();
            return;
        }

        drawAddStateAction(row, "top", false);

        int actionIndex = -1;
        EditorUi.ListAction pendingAction = EditorUi.ListAction.NONE;
        for (int index = 0; index < row.states.size(); index++) {
            StateRow state = row.states.get(index);
            ImGui.pushID("state_" + state.id);
            boolean requestFocus = row.focusStateRowId == state.id;
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "adventure_state_row",
                EditorUi.identifiedTitle(
                    tr("adventure.state.entry", index + 1),
                    state.summary()
                ),
                ImGuiTableFlags.SizingStretchProp,
                index,
                row.states.size(),
                false,
                true,
                true,
                requestFocus
            );
            if (card.focusApplied()) row.focusStateRowId = 0L;
            if (card.expanded()) {
                drawStateRow(section, row, index, state);
                EditorUi.endCard();
            }
            ImGui.popID();
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                pendingAction = card.action();
            }
        }
        if (actionIndex >= 0) {
            applyStateAction(row, actionIndex, pendingAction);
        }
        if (row.states.isEmpty()) EditorUi.drawEmptyState(tr("adventure.state.empty"));
        drawAddStateAction(row, "bottom", true);
        ImGui.spacing();
    }

    private void drawAddStateAction(PredicateRow row, String placement, boolean acceptFocus) {
        if (acceptFocus && row.focusAddStateButton) {
            ImGui.setKeyboardFocusHere();
            row.focusAddStateButton = false;
        }
        if (EditorIcons.smallButton(
            "add_state_condition_" + placement,
            EditorIcons.Icon.ADD,
            tr("adventure.state.add")
        )) {
            StateRow added = StateRow.createDefault();
            row.states.add(added);
            row.focusStateRowId = added.id;
            row.focusAddStateButton = false;
            resetEditorUi();
            dirty = true;
        }
    }

    private void drawStateRow(SectionModel section, PredicateRow predicate, int index, StateRow row) {
        String path = predicateField(predicate, "state/" + row.id + "/");
        ImGui.text(tr("adventure.state.property"));
        String keyProblem = stateKeyProblem(predicate, index);
        applyPendingFieldFocus(section.key, path + "name");
        if (host.inputIdentifierWithSuggestions(
            "state_property",
            new FieldHints(tr("field_purpose.block_state_property"), "facing"),
            row.name,
            statePropertySuggestions(predicate),
            keyProblem
        )) dirty = true;

        ImGui.text(tr("adventure.state.match"));
        if (modeButton(tr("adventure.state.exact") + "##exact", row.mode.get() == 0)) {
            row.mode.set(0);
            dirty = true;
        }
        ImGui.sameLine();
        if (modeButton(tr("adventure.state.range") + "##range", row.mode.get() == 1)) {
            row.mode.set(1);
            dirty = true;
        }

        if (row.mode.get() == 0) {
            ImGui.text(tr("adventure.state.value"));
            String valueProblem = stateValueProblem(predicate, row.name.get(), row.exact.get(), true);
            applyPendingFieldFocus(section.key, path + "exact");
            if (host.inputIdentifierWithSuggestions(
                "state_exact_value",
                new FieldHints(tr("field_purpose.block_state_value"), "north"),
                row.exact,
                stateValueSuggestions(predicate, row.name.get()),
                valueProblem
            )) dirty = true;
        } else if (ImGui.beginTable(
            "state_range_values",
            2,
            ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoPadOuterX
        )) {
            List<IdSuggestion> values = stateValueSuggestions(predicate, row.name.get());
            EditorUi.alignedTableFields(
                new String[]{tr("adventure.state.minimum"), tr("adventure.state.maximum")},
                () -> {
                    applyPendingFieldFocus(section.key, path + "minimum");
                    if (host.inputIdentifierWithSuggestions(
                        "state_minimum",
                        new FieldHints(tr("field_purpose.minimum_value"), "0"),
                        row.minimum,
                        values,
                        stateValueProblem(predicate, row.name.get(), row.minimum.get(), false)
                    )) dirty = true;
                },
                () -> {
                    applyPendingFieldFocus(section.key, path + "maximum");
                    if (host.inputIdentifierWithSuggestions(
                        "state_maximum",
                        new FieldHints(tr("field_purpose.maximum_value"), "15"),
                        row.maximum,
                        values,
                        stateValueProblem(predicate, row.name.get(), row.maximum.get(), false)
                    )) dirty = true;
                }
            );
            ImGui.endTable();
            if (row.minimum.get().trim().isEmpty() && row.maximum.get().trim().isEmpty()) {
                ItemEditorTheme.drawFieldProblem(tr("adventure.state.range_required"));
            }
        }
    }

    private void drawNbtCondition(SectionModel section, PredicateRow row) {
        if (ImGui.checkbox(tr("adventure.nbt.enabled"), row.nbtPresent)) dirty = true;
        if (ImGui.isItemHovered()) EditorUi.setTooltip(tr("adventure.nbt.help"));
        if (!row.nbtPresent.get()) {
            ImGui.spacing();
            return;
        }

        String problem = snbtProblem(row.nbtSource.get());
        int errorLine = problem.isBlank() ? 0 : snbtErrorLine(row.nbtSource.get());
        if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
        if (consumePendingFieldFocus(section.key, predicateField(row, "nbt"))) {
            ImGui.setScrollHereY(0.35f);
            row.nbtEditor.requestFocus();
        }
        if (row.nbtEditor.draw(
            "block_entity_nbt",
            row.nbtSource,
            -1.0f,
            132.0f,
            ImGuiInputTextFlags.AllowTabInput,
            errorLine,
            CodeLanguage.SNBT
        )) dirty = true;
        if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
        problem = snbtProblem(row.nbtSource.get());
        ItemEditorTheme.drawFieldProblem(problem);
        ImGui.beginDisabled(!problem.isBlank());
        if (ImGui.button(tr("adventure.nbt.format"))) {
            formatPendingSnbt(row.nbtSource, "adventure.nbt.invalid");
        }
        ImGui.endDisabled();
        ImGui.spacing();
    }

    private void drawSnbtCondition(
        SectionModel section,
        PredicateRow row,
        String id,
        String label,
        String help,
        ImBoolean present,
        ImString source,
        CodeEditorInput editor
    ) {
        if (ImGui.checkbox(label + "##enabled_" + id, present)) dirty = true;
        if (ImGui.isItemHovered()) EditorUi.setTooltip(help);
        if (!present.get()) {
            ImGui.spacing();
            return;
        }

        String problem = componentSnbtProblem(source.get());
        int errorLine = problem.isBlank() ? 0 : snbtErrorLine(source.get());
        if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
        if (consumePendingFieldFocus(section.key, predicateField(row, id))) {
            ImGui.setScrollHereY(0.35f);
            editor.requestFocus();
        }
        if (editor.draw(
            "block_entity_" + id,
            source,
            -1.0f,
            132.0f,
            ImGuiInputTextFlags.AllowTabInput,
            errorLine,
            CodeLanguage.SNBT
        )) dirty = true;
        if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
        problem = componentSnbtProblem(source.get());
        ItemEditorTheme.drawFieldProblem(problem);
        ImGui.beginDisabled(!problem.isBlank());
        if (ImGui.button(tr("adventure.snbt.format") + "##" + id)) {
            formatPendingSnbt(source, "adventure.snbt.invalid");
        }
        ImGui.endDisabled();
        ImGui.spacing();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack(client);
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(client, state);
        dirty = true;
    }

    @Override
    protected boolean hasPendingChanges() {
        return !ItemStack.matches(host.draft(), buildEditedStack(Minecraft.getInstance()));
    }

    private ItemStack previewStack(Minecraft client) {
        try {
            return buildEditedStack(client);
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack buildEditedStack(Minecraft client) {
        return applyPending(client, host.draft());
    }

    private ItemStack applyPending(Minecraft client, ItemStack base) {
        ItemStack edited = base.copy();
        applySection(client, edited, canBreak);
        applySection(client, edited, canPlaceOn);
        return edited;
    }

    private void applySection(Minecraft client, ItemStack edited, SectionModel section) {
        if (!section.present.get()) {
            edited.remove(section.type);
            return;
        }

        if (section.predicates.isEmpty()) {
            throw new IllegalArgumentException(tr("adventure.predicates.required"));
        }
        ListTag predicates = new ListTag();
        for (PredicateRow row : section.predicates) {
            predicates.add(buildPredicateNbt(row));
        }
        Tag value = predicates.size() == 1 ? predicates.getFirst() : predicates;
        ItemStackCodecs.decodeAndSetComponentNbt(client, edited, section.type, value);
    }

    private CompoundTag buildPredicateNbt(PredicateRow row) {
        JsonObject result = row.extraFields.deepCopy();
        if (row.blocksPresent.get()) {
            String blockProblem = blockSelectionProblem(row.blocks);
            if (!blockProblem.isBlank()) throw new IllegalArgumentException(blockProblem);
            result.add("blocks", row.blocks.deepCopy());
        }

        if (row.statePresent.get()) {
            JsonObject state = new JsonObject();
            for (int index = 0; index < row.states.size(); index++) {
                StateRow condition = row.states.get(index);
                String keyProblem = stateKeyProblem(row, index);
                if (!keyProblem.isBlank()) throw new IllegalArgumentException(keyProblem);
                String exactProblem = condition.mode.get() == 0
                    ? stateValueProblem(row, condition.name.get(), condition.exact.get(), true)
                    : "";
                if (!exactProblem.isBlank()) throw new IllegalArgumentException(exactProblem);
                if (condition.mode.get() != 0) {
                    String minimumProblem = stateValueProblem(
                        row,
                        condition.name.get(),
                        condition.minimum.get(),
                        false
                    );
                    String maximumProblem = stateValueProblem(
                        row,
                        condition.name.get(),
                        condition.maximum.get(),
                        false
                    );
                    if (!minimumProblem.isBlank()) throw new IllegalArgumentException(minimumProblem);
                    if (!maximumProblem.isBlank()) throw new IllegalArgumentException(maximumProblem);
                }
                state.add(condition.name.get().trim(), condition.toJson());
            }
            result.add("state", state);
        }

        if (row.nbtPresent.get()) {
            parseSnbt(row.nbtSource.get());
            result.addProperty("nbt", row.nbtSource.get().strip());
        }
        CompoundTag encoded = ItemStackCodecs.jsonObjectToNbt(result);
        if (row.componentsPresent.get()) {
            encoded.put("components", parseSnbt(row.componentsSource.get()));
        }
        if (row.componentPredicatesPresent.get()) {
            encoded.put("predicates", parseSnbt(row.componentPredicatesSource.get()));
        }
        return encoded;
    }

    private void loadSection(Minecraft client, ItemStack stack, SectionModel section) {
        section.predicates.clear();
        section.predicateDragScope = OrderedListDragDrop.newScope("adventure/" + section.key + "/predicates");
        section.focusPredicateRowId = 0L;
        section.focusAddPredicateButton = false;
        section.focusPresenceToggle = false;
        Object installed = stack.get(section.type);
        section.present.set(installed != null);
        if (installed == null) return;

        JsonElement encoded = ItemStackCodecs.encodeComponent(client, section.type, installed);
        Tag encodedNbt = ItemStackCodecs.encodeComponentNbt(client, section.type, installed);
        if (encoded.isJsonObject()) {
            CompoundTag predicateNbt = encodedNbt instanceof CompoundTag compound ? compound : null;
            section.predicates.add(new PredicateRow(encoded.getAsJsonObject(), predicateNbt));
        } else if (encoded.isJsonArray()) {
            ListTag encodedNbtList = encodedNbt instanceof ListTag list ? list : null;
            JsonArray encodedList = encoded.getAsJsonArray();
            for (int index = 0; index < encodedList.size(); index++) {
                JsonElement entry = encodedList.get(index);
                if (!entry.isJsonObject()) continue;
                CompoundTag predicateNbt = encodedNbtList != null
                    && index < encodedNbtList.size()
                    && encodedNbtList.get(index) instanceof CompoundTag compound
                    ? compound
                    : null;
                section.predicates.add(new PredicateRow(entry.getAsJsonObject(), predicateNbt));
            }
        } else {
            throw new IllegalStateException(section.key + " encoded as neither a predicate nor a predicate list");
        }
    }

    private String blockSelectionProblem(JsonElement blocks) {
        List<String> values = identifierValues(blocks);
        if (values.isEmpty()) return tr("adventure.blocks.required");
        if (values.stream().anyMatch(value -> value.startsWith("#")) && values.size() > 1) {
            return tr("collection.tag_is_collection");
        }
        for (String value : values) {
            String rawId = value.startsWith("#") ? value.substring(1) : value;
            Identifier id = Identifier.tryParse(rawId);
            if (id == null) return tr("validation.identifier_or_tag_format");
            if (value.startsWith("#")) {
                if (!knownBlocksAndTags.isEmpty() && !knownBlocksAndTags.contains(value)) {
                    return tr("validation.identifier_unknown");
                }
            } else if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                return tr("validation.identifier_unknown");
            }
        }
        return "";
    }

    private List<IdSuggestion> statePropertySuggestions(PredicateRow predicate) {
        Set<String> names = new HashSet<>();
        for (Block block : selectedBlocks(predicate)) {
            block.getStateDefinition().getProperties().forEach(property -> names.add(property.getName()));
        }
        return names.stream().sorted().map(IdSuggestion::of).toList();
    }

    private List<IdSuggestion> stateValueSuggestions(PredicateRow predicate, String propertyName) {
        String name = propertyName.trim();
        Set<String> values = new HashSet<>();
        for (Block block : selectedBlocks(predicate)) {
            Property<?> property = block.getStateDefinition().getProperty(name);
            if (property != null) addPropertyValues(values, property);
        }
        return values.stream().sorted().map(IdSuggestion::of).toList();
    }

    private static List<Block> selectedBlocks(PredicateRow predicate) {
        if (!predicate.blocksPresent.get()) return List.of();
        List<Block> blocks = new ArrayList<>();
        for (String value : identifierValues(predicate.blocks)) {
            if (value.startsWith("#")) {
                Identifier id = Identifier.tryParse(value.substring(1));
                if (id == null) continue;
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
                for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                    blocks.add(holder.value());
                }
            } else {
                Identifier id = Identifier.tryParse(value);
                Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
                if (block != null) blocks.add(block);
            }
        }
        return blocks;
    }

    private static <T extends Comparable<T>> void addPropertyValues(Set<String> values, Property<T> property) {
        property.getPossibleValues().forEach(value -> values.add(property.getName(value)));
    }

    private static List<String> identifierValues(JsonElement encoded) {
        List<String> values = new ArrayList<>();
        if (encoded == null || encoded.isJsonNull()) return values;
        if (encoded.isJsonPrimitive()) {
            values.add(encoded.getAsString().trim());
        } else if (encoded.isJsonArray()) {
            for (JsonElement entry : encoded.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) values.add(entry.getAsString().trim());
            }
        }
        values.removeIf(String::isBlank);
        return values;
    }

    private static String stateKeyProblem(PredicateRow predicate, int index) {
        List<StateRow> rows = predicate.states;
        String key = rows.get(index).name.get().trim();
        if (key.isEmpty()) return tr("data.property.required");
        for (int cursor = 0; cursor < rows.size(); cursor++) {
            if (cursor != index && rows.get(cursor).name.get().trim().equals(key)) {
                return tr("data.property.duplicate", key);
            }
        }
        List<Block> selected = selectedBlocks(predicate);
        if (!selected.isEmpty() && selected.stream()
            .noneMatch(block -> block.getStateDefinition().getProperty(key) != null)) {
            return tr("adventure.state.unknown_property", key);
        }
        return "";
    }

    private static String stateValueProblem(
        PredicateRow predicate,
        String propertyName,
        String source,
        boolean required
    ) {
        String value = source.trim();
        if (value.isEmpty()) return required ? tr("data.value.required") : "";
        String key = propertyName.trim();
        boolean foundProperty = false;
        boolean foundValue = false;
        for (Block block : selectedBlocks(predicate)) {
            Property<?> property = block.getStateDefinition().getProperty(key);
            if (property == null) continue;
            foundProperty = true;
            if (property.getValue(value).isPresent()) {
                foundValue = true;
                break;
            }
        }
        if (foundProperty && !foundValue) {
            return tr("adventure.state.invalid_value", value, key);
        }
        return "";
    }

    private static String predicateSummary(PredicateRow row) {
        if (!row.blocksPresent.get()) return tr("adventure.blocks.any_summary");
        List<String> values = identifierValues(row.blocks);
        if (values.isEmpty()) return tr("adventure.blocks.none_summary");
        return values.size() == 1 ? values.getFirst() : values.getFirst() + " +" + (values.size() - 1);
    }

    private static boolean modeButton(String label, boolean selected) {
        if (selected) ItemEditorTheme.pushActiveFilter();
        boolean pressed = ImGui.button(label);
        if (selected) ItemEditorTheme.popActiveFilter();
        return pressed && !selected;
    }

    private void applyPredicateAction(
        SectionModel section,
        int index,
        EditorUi.ListAction action
    ) {
        EditorListMutations.Mutation<PredicateRow> mutation = EditorListMutations.apply(
            section.predicates,
            index,
            action,
            PredicateRow::copy,
            PredicateRow::createDefault
        );
        finishPredicateMutation(section, mutation);
    }

    private void applyPredicateReorder(
        SectionModel section,
        OrderedListDragDrop.ReorderRequest request
    ) {
        if (!section.predicateDragScope.equals(request.scope())) return;
        finishPredicateMutation(section, EditorListMutations.reorder(
            section.predicates,
            request,
            row -> row.id
        ));
    }

    private void finishPredicateMutation(
        SectionModel section,
        EditorListMutations.Mutation<PredicateRow> mutation
    ) {
        if (!mutation.changed()) return;
        if (mutation.focusRow() == null) {
            section.focusPredicateRowId = 0L;
            section.focusAddPredicateButton = false;
            section.focusPresenceToggle = true;
            section.present.set(false);
        } else {
            section.focusPredicateRowId = mutation.focusRow().id;
            section.focusAddPredicateButton = false;
            section.focusPresenceToggle = false;
        }
        resetEditorUi();
        dirty = true;
    }

    private void applyStateAction(PredicateRow row, int index, EditorUi.ListAction action) {
        EditorListMutations.Mutation<StateRow> mutation = EditorListMutations.apply(
            row.states,
            index,
            action,
            StateRow::copy,
            StateRow::createDefault
        );
        if (!mutation.changed()) return;
        if (mutation.focusRow() == null) {
            row.focusStateRowId = 0L;
            row.focusAddStateButton = true;
        } else {
            row.focusStateRowId = mutation.focusRow().id;
            row.focusAddStateButton = false;
        }
        resetEditorUi();
        dirty = true;
    }

    private static CompoundTag parseSnbt(String source) {
        try {
            return TagParser.parseCompoundFully(source);
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private static String prettySnbt(CompoundTag tag) {
        return new SnbtPrinterTagVisitor("  ", 0, new ArrayList<>()).visit(tag);
    }

    private static String snbtProblem(String source) {
        try {
            parseSnbt(source);
            return "";
        } catch (RuntimeException exception) {
            return tr("adventure.nbt.invalid", EditorUi.readableMessage(exception));
        }
    }

    private static String componentSnbtProblem(String source) {
        try {
            parseSnbt(source);
            return "";
        } catch (RuntimeException exception) {
            return tr("adventure.snbt.invalid", EditorUi.readableMessage(exception));
        }
    }

    private static int snbtErrorLine(String source) {
        try {
            parseSnbt(source);
            return 0;
        } catch (RuntimeException exception) {
            return CodeEditorInput.errorLine(exception, source);
        }
    }

    private static String snbtSource(CompoundTag owner, String field) {
        if (owner == null) return "{}";
        return owner.getCompound(field).map(AdventureEditorPanel::prettySnbt).orElse("{}");
    }

    private static final class SectionModel {
        private final DataComponentType<?> type;
        private final String key;
        private final ImBoolean present = new ImBoolean(false);
        private final List<PredicateRow> predicates = new ArrayList<>();
        private OrderedListDragDrop.Scope predicateDragScope;
        private long focusPredicateRowId;
        private boolean focusAddPredicateButton;
        private boolean focusPresenceToggle;

        private SectionModel(DataComponentType<?> type, String key) {
            this.type = type;
            this.key = key;
            this.predicateDragScope = OrderedListDragDrop.newScope("adventure/" + key + "/predicates");
        }
    }

    private static final class PredicateRow {
        private final long id = NEXT_ROW_ID.incrementAndGet();
        private final ImBoolean blocksPresent;
        private JsonElement blocks;
        private final IdentifierCollectionEditor.FocusState blocksFocus = new IdentifierCollectionEditor.FocusState();
        private final ImBoolean statePresent;
        private final List<StateRow> states = new ArrayList<>();
        private long focusStateRowId;
        private boolean focusAddStateButton;
        private final ImBoolean nbtPresent;
        private final ImString nbtSource;
        private final CodeEditorInput nbtEditor = new CodeEditorInput();
        private final ImBoolean componentsPresent;
        private final ImString componentsSource;
        private final CodeEditorInput componentsEditor = new CodeEditorInput();
        private final ImBoolean componentPredicatesPresent;
        private final ImString componentPredicatesSource;
        private final CodeEditorInput componentPredicatesEditor = new CodeEditorInput();
        private final JsonObject extraFields;

        private static PredicateRow createDefault() {
            JsonObject value = new JsonObject();
            value.addProperty("blocks", "minecraft:stone");
            return new PredicateRow(value, null);
        }

        private PredicateRow(JsonObject encoded, CompoundTag encodedNbt) {
            blocksPresent = new ImBoolean(encoded.has("blocks"));
            blocks = encoded.has("blocks")
                ? encoded.get("blocks").deepCopy()
                : new JsonPrimitive("minecraft:stone");

            statePresent = new ImBoolean(encoded.has("state"));
            if (encoded.has("state") && encoded.get("state").isJsonObject()) {
                encoded.getAsJsonObject("state").entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().toLowerCase()))
                    .forEach(entry -> states.add(StateRow.from(entry.getKey(), entry.getValue())));
            }

            nbtPresent = new ImBoolean(encoded.has("nbt"));
            String nbt = encoded.has("nbt") && encoded.get("nbt").isJsonPrimitive()
                ? encoded.get("nbt").getAsString()
                : "{}";
            nbtSource = new ImString(nbt, SOURCE_CAPACITY);

            componentsPresent = new ImBoolean(encoded.has("components"));
            componentsSource = new ImString(snbtSource(encodedNbt, "components"), SOURCE_CAPACITY);
            componentPredicatesPresent = new ImBoolean(encoded.has("predicates"));
            componentPredicatesSource = new ImString(snbtSource(encodedNbt, "predicates"), SOURCE_CAPACITY);

            extraFields = encoded.deepCopy();
            extraFields.remove("blocks");
            extraFields.remove("state");
            extraFields.remove("nbt");
            extraFields.remove("components");
            extraFields.remove("predicates");
        }

        private PredicateRow(PredicateRow source) {
            blocksPresent = new ImBoolean(source.blocksPresent.get());
            blocks = source.blocks.deepCopy();
            statePresent = new ImBoolean(source.statePresent.get());
            source.states.forEach(state -> states.add(state.copy()));
            nbtPresent = new ImBoolean(source.nbtPresent.get());
            nbtSource = new ImString(source.nbtSource.get(), SOURCE_CAPACITY);
            componentsPresent = new ImBoolean(source.componentsPresent.get());
            componentsSource = new ImString(source.componentsSource.get(), SOURCE_CAPACITY);
            componentPredicatesPresent = new ImBoolean(source.componentPredicatesPresent.get());
            componentPredicatesSource = new ImString(source.componentPredicatesSource.get(), SOURCE_CAPACITY);
            extraFields = source.extraFields.deepCopy();
        }

        private PredicateRow copy() {
            return new PredicateRow(this);
        }
    }

    private static final class StateRow {
        private final long id = NEXT_ROW_ID.incrementAndGet();
        private final ImString name;
        private final ImInt mode;
        private final ImString exact;
        private final ImString minimum;
        private final ImString maximum;

        private StateRow(String name, String exact) {
            this(name, 0, exact, "", "");
        }

        private static StateRow createDefault() {
            return new StateRow("", "");
        }

        private StateRow(String name, int mode, String exact, String minimum, String maximum) {
            this.name = new ImString(name, 256);
            this.mode = new ImInt(mode);
            this.exact = new ImString(exact, 256);
            this.minimum = new ImString(minimum, 256);
            this.maximum = new ImString(maximum, 256);
        }

        private static StateRow from(String name, JsonElement value) {
            if (value.isJsonObject()) {
                JsonObject range = value.getAsJsonObject();
                return new StateRow(
                    name,
                    1,
                    "",
                    range.has("min") ? range.get("min").getAsString() : "",
                    range.has("max") ? range.get("max").getAsString() : ""
                );
            }
            return new StateRow(name, value.getAsString());
        }

        private JsonElement toJson() {
            if (mode.get() == 0) {
                String value = exact.get().trim();
                if (value.isEmpty()) throw new IllegalArgumentException(tr("data.value.required"));
                return new JsonPrimitive(value);
            }
            String min = minimum.get().trim();
            String max = maximum.get().trim();
            if (min.isEmpty() && max.isEmpty()) {
                throw new IllegalArgumentException(tr("adventure.state.range_required"));
            }
            JsonObject range = new JsonObject();
            if (!min.isEmpty()) range.addProperty("min", min);
            if (!max.isEmpty()) range.addProperty("max", max);
            return range;
        }

        private String summary() {
            String key = name.get().trim();
            if (mode.get() == 0) return key + " = " + exact.get().trim();
            String min = minimum.get().trim().isEmpty() ? "-" : minimum.get().trim();
            String max = maximum.get().trim().isEmpty() ? "+" : maximum.get().trim();
            return key + " = " + min + ".." + max;
        }

        private StateRow copy() {
            return new StateRow(name.get(), mode.get(), exact.get(), minimum.get(), maximum.get());
        }
    }
}
