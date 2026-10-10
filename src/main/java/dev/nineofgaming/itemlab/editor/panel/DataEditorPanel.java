package dev.nineofgaming.itemlab.editor.panel;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nineofgaming.itemlab.editor.EditorNavigationGuard;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.control.CodeEditorInput;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeLanguage;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DebugStickState;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Structured editors for persistent custom, entity, and block data components.
 */
public final class DataEditorPanel {
    private static final AtomicLong NEXT_NODE_ID = new AtomicLong();
    private static final int VALUE_CAPACITY = 8_192;
    private static final int SOURCE_CAPACITY = 262_144;
    private static final float MIN_TYPE_COLUMN_WIDTH = 128.0f;

    private final ItemEditor host;
    private final List<NbtNode> entries = new ArrayList<>();
    private final ImString snbtSource = new ImString(SOURCE_CAPACITY);
    private final CodeEditorInput codeEditor = new CodeEditorInput();
    private final ImString entityType = new ImString("minecraft:pig", 512);
    private final ImString blockEntityType = new ImString("minecraft:chest", 512);
    private final List<PropertyRow> propertyRows = new ArrayList<>();
    private List<IdSuggestion> entityTypes = List.of();
    private List<IdSuggestion> blockEntityTypes = List.of();
    private List<IdSuggestion> blockIds = List.of();
    private DataTarget target = DataTarget.CUSTOM_DATA;
    private boolean entityDataPresent;
    private boolean blockEntityDataPresent;
    private boolean snbtMode;
    private boolean dirty;
    private int validationLine;
    private long focusNodeId;
    private long focusPropertyRowId;
    private long focusAddChildNodeId;
    private boolean focusAddButton;
    private boolean focusPresenceToggle;
    private boolean focusMetadataType;
    private PendingNodeField focusNodeField;
    private boolean focusPropertyValue;
    private final Set<Long> focusAncestorIds = new HashSet<>();

    private enum PendingNodeField {KEY, VALUE, ACTION}

    public DataEditorPanel(ItemEditor host) {
        this.host = host;
    }

    public static boolean supports(DataComponentType<?> type) {
        return type.codec() != null
            && (type == DataComponents.CUSTOM_DATA
            || type == DataComponents.ENTITY_DATA
            || type == DataComponents.BUCKET_ENTITY_DATA
            || type == DataComponents.BLOCK_ENTITY_DATA
            || type == DataComponents.BLOCK_STATE
            || type == DataComponents.DEBUG_STICK_STATE);
    }

    public void setCatalog(EditorCatalog catalog) {
        entityTypes = catalog.entityTypeIds();
        blockEntityTypes = catalog.registryIds("block_entity_type");
        blockIds = catalog.registryIds("block");
    }

    public EditorNavigationGuard.Decision navigationDecision(
        DataComponentType<?> type,
        EditorNavigationGuard.Source source
    ) {
        return navigationDecision(DataTarget.from(type), source);
    }

    public void reportBlockedNavigation(EditorNavigationGuard.Decision decision) {
        if (decision.allowed()) return;
        host.setStatus(false, decision.blocker().explanation());
        if (decision.blocker().focusRequest() != null) {
            requestInitialContentFocus();
        }
    }

    public void selectComponent(DataComponentType<?> type, ItemStack stack) {
        DataTarget requested = DataTarget.from(type);
        EditorNavigationGuard.Decision decision = navigationDecision(
            requested,
            EditorNavigationGuard.Source.PROGRAMMATIC
        );
        if (!decision.allowed()) {
            reportBlockedNavigation(decision);
            return;
        }
        if (requested != null && requested != target) {
            target = requested;
            codeEditor.resetCursor();
            sync(stack);
            requestInitialContentFocus();
        }
    }

    public void resetCodeEditorView() {
        codeEditor.resetCursor();
    }

    public void syncComponent(DataComponentType<?> type, ItemStack stack) {
        if (target.type == type) {
            sync(stack);
            requestInitialContentFocus();
        }
    }

    public boolean isComponentDirty(DataComponentType<?> type) {
        return target.type == type && isDirty();
    }

    public boolean isCustomDataDirty() {
        return isComponentDirty(DataComponents.CUSTOM_DATA);
    }

    public boolean isDirty() {
        if (dirty) {
            try {
                dirty = !ItemStack.matches(host.draft(), pendingStack());
            } catch (RuntimeException ignored) {
                dirty = true;
            }
        }
        return dirty;
    }

    public void sync(ItemStack stack) {
        CompoundTag tag = currentTag(stack);
        syncEntityMetadata(stack);
        syncBlockEntityMetadata(stack);
        syncPropertyRows(stack);
        loadTree(tag);
        snbtSource.set(prettySnbt(tag));
        validationLine = 0;
        clearFocusRequests();
        dirty = false;
    }

    public void draw(Minecraft client, boolean diamondFirePending) {
        boolean customDataBlocked = customDataBlocked(diamondFirePending);
        drawToolbar(client, customDataBlocked);
        drawTargetSelector();
        EditorUi.beginScrollableChild(
            "data_editor",
            0.0f,
            Math.max(1.0f, ImGui.getContentRegionAvailY()),
            false
        );
        try {
            drawContents(client, customDataBlocked, false);
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(
        Minecraft client,
        DataComponentType<?> type,
        boolean diamondFirePending
    ) {
        selectComponent(type, host.draft());
        boolean customDataBlocked = customDataBlocked(diamondFirePending);
        drawToolbar(client, customDataBlocked);
        drawContents(client, customDataBlocked, true);
    }

    private boolean customDataBlocked(boolean diamondFirePending) {
        return target == DataTarget.CUSTOM_DATA && diamondFirePending;
    }

    private void drawToolbar(Minecraft client, boolean editingBlocked) {
        ImGui.spacing();
        EditorUi.drawSaveDiscardActions(isDirty(), () -> save(client, true), () -> {
            sync(host.draft());
            host.setStatus(true, tr("message.discarded"));
        });

        if (!target.usesNbt() || !snbtMode) {
            EditorUi.sameLineIfFits(EditorIcons.buttonSize());
            boolean payloadDisabled = target == DataTarget.ENTITY_DATA && !entityDataPresent
                || target == DataTarget.BLOCK_ENTITY_DATA && !blockEntityDataPresent;
            ImGui.beginDisabled(editingBlocked || payloadDisabled);
            drawAddAction("top", false);
            ImGui.endDisabled();
        }
    }

    private void drawAddAction(String placement, boolean acceptFocus) {
        if (acceptFocus && focusAddButton) {
            ImGui.setKeyboardFocusHere();
            focusAddButton = false;
        }
        String addLabel = target.usesNbt() ? tr("data.add_field") : tr("data.add_property");
        if (!EditorIcons.button("add_data_" + placement, EditorIcons.Icon.ADD, addLabel)) return;
        if (target.usesNbt()) {
            NbtNode added = newNode("", NbtType.STRING);
            entries.add(added);
            focusNodeId = added.id;
        } else {
            PropertyRow added = defaultPropertyRow();
            propertyRows.add(added);
            focusPropertyRowId = added.id;
        }
        focusAddChildNodeId = 0L;
        focusAddButton = false;
        host.resetAutocomplete();
        dirty = true;
    }

    private void drawTargetSelector() {
        for (DataTarget option : DataTarget.values()) {
            if (option != DataTarget.CUSTOM_DATA) EditorUi.sameLineIfFits(tr(option.selectorKey));
            boolean selected = target == option;
            if (selected) ItemEditorTheme.pushActiveFilter();
            boolean pressed = ImGui.button(tr(option.selectorKey) + "##data_target_" + option.id);
            if (selected) ItemEditorTheme.popActiveFilter();
            if (pressed && !selected) {
                EditorNavigationGuard.Decision decision = navigationDecision(
                    option,
                    EditorNavigationGuard.Source.USER
                );
                if (decision.allowed()) {
                    target = option;
                    codeEditor.resetCursor();
                    sync(host.draft());
                    requestInitialContentFocus();
                } else {
                    reportBlockedNavigation(decision);
                }
            }
        }
        ImGui.spacing();
    }

    private EditorNavigationGuard.Decision navigationDecision(
        DataTarget requested,
        EditorNavigationGuard.Source source
    ) {
        boolean sameTarget = requested == null || requested == target;
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
            pendingStack();
            pendingState = EditorNavigationGuard.PendingState.VALID;
            explanation = tr("navigation.pending", tr("tab.data"));
        } catch (RuntimeException exception) {
            pendingState = EditorNavigationGuard.PendingState.INVALID;
            explanation = tr(
                "navigation.invalid",
                tr("tab.data"),
                EditorUi.readableMessage(exception)
            );
        }
        return new EditorNavigationGuard.Blocker(
            tr("tab.data"),
            "data",
            pendingState,
            explanation,
            new EditorNavigationGuard.FocusRequest("data_editor")
        );
    }

    private void drawContents(Minecraft client, boolean editingBlocked, boolean inline) {
        ImGui.text(tr(target.titleKey));
        host.drawDiamondFireComponentHint(target.type);
        ImGui.separator();
        if (editingBlocked) {
            ItemEditorTheme.drawFieldProblem(tr("data.diamondfire_pending"));
        }

        boolean payloadDisabled = target == DataTarget.ENTITY_DATA && !drawEntityMetadata()
            || target == DataTarget.BLOCK_ENTITY_DATA && !drawBlockEntityMetadata();

        ImGui.beginDisabled(editingBlocked || payloadDisabled);
        try {
            if (target.usesNbt()) {
                drawModeSelector();
                ImGui.spacing();
                if (snbtMode) {
                    drawSnbt(client, inline);
                } else {
                    drawTree();
                }
            } else {
                drawPropertyRows();
            }
        } finally {
            ImGui.endDisabled();
        }
    }

    private boolean drawEntityMetadata() {
        if (target != DataTarget.ENTITY_DATA) return true;

        ImBoolean present = new ImBoolean(entityDataPresent);
        applyPresenceFocus();
        if (ImGui.checkbox(tr("data.entity.enabled"), present)) {
            entityDataPresent = present.get();
            focusMetadataType = entityDataPresent;
            host.resetAutocomplete();
            dirty = true;
        }
        ImGui.beginDisabled(!entityDataPresent);
        ImGui.text(tr("data.entity.type"));
        ImGui.setNextItemWidth(-1.0f);
        applyMetadataTypeFocus();
        if (host.inputIdentifierWithSuggestions(
            "entity_data_type",
            new FieldHints(tr("field_purpose.entity_identifier"), tr("data.entity.type_hint")),
            entityType,
            entityTypes,
            entityTypeProblem(entityType.get())
        )) {
            dirty = true;
        }
        ImGui.endDisabled();
        ImGui.spacing();
        return entityDataPresent;
    }

    private boolean drawBlockEntityMetadata() {
        if (target != DataTarget.BLOCK_ENTITY_DATA) return true;

        ImBoolean present = new ImBoolean(blockEntityDataPresent);
        applyPresenceFocus();
        if (ImGui.checkbox(tr("data.block_entity.enabled"), present)) {
            blockEntityDataPresent = present.get();
            focusMetadataType = blockEntityDataPresent;
            host.resetAutocomplete();
            dirty = true;
        }
        ImGui.beginDisabled(!blockEntityDataPresent);
        ImGui.text(tr("data.block_entity.type"));
        ImGui.setNextItemWidth(-1.0f);
        applyMetadataTypeFocus();
        if (host.inputIdentifierWithSuggestions(
            "block_entity_data_type",
            new FieldHints(tr("field_purpose.block_entity_identifier"), tr("data.block_entity.type_hint")),
            blockEntityType,
            blockEntityTypes,
            blockEntityTypeProblem(blockEntityType.get())
        )) {
            dirty = true;
        }
        ImGui.endDisabled();
        ImGui.spacing();
        return blockEntityDataPresent;
    }

    public void requestPendingFocus() {
        clearFocusRequests();
        if (target == DataTarget.ENTITY_DATA && entityDataPresent && !entityTypeProblem(entityType.get()).isBlank()
            || target == DataTarget.BLOCK_ENTITY_DATA && blockEntityDataPresent
            && !blockEntityTypeProblem(blockEntityType.get()).isBlank()) {
            focusMetadataType = true;
            return;
        }
        if (target.usesNbt() && snbtMode) {
            codeEditor.requestFocus();
            return;
        }
        if (target.usesNbt() && requestInvalidNodeFocus(entries, NbtType.COMPOUND)) return;
        if (!target.usesNbt()) {
            for (int index = 0; index < propertyRows.size(); index++) {
                PropertyRow row = propertyRows.get(index);
                boolean debugStick = target == DataTarget.DEBUG_STICK_STATE;
                String keyProblem = debugStick ? debugStickBlockProblem(index) : propertyKeyProblem(index);
                String valueProblem = debugStick ? debugStickPropertyProblem(row) : blockStateValueProblem(row);
                if (!keyProblem.isBlank() || !valueProblem.isBlank()) {
                    focusPropertyRowId = row.id;
                    focusPropertyValue = keyProblem.isBlank();
                    return;
                }
            }
        }
        requestInitialContentFocus();
    }

    private boolean requestInvalidNodeFocus(List<NbtNode> siblings, NbtType parentType) {
        for (int index = 0; index < siblings.size(); index++) {
            NbtNode node = siblings.get(index);
            if (parentType != NbtType.LIST && !duplicateKeyProblem(siblings, index).isBlank()) {
                return requestNodeProblem(node, PendingNodeField.KEY);
            }
            if (siblings == entries && (target == DataTarget.ENTITY_DATA || target == DataTarget.BLOCK_ENTITY_DATA)
                && node.key.get().equals("id")) return requestNodeProblem(node, PendingNodeField.KEY);
            if (parentType == NbtType.LIST && index > 0 && node.type != siblings.getFirst().type) {
                // Type selection is locked for multi-element lists; row actions remain usable.
                return requestNodeProblem(node, PendingNodeField.ACTION);
            }
            if (node.type.container && requestInvalidNodeFocus(node.children, node.type)) {
                focusAncestorIds.add(node.id);
                return true;
            }
            if (!node.type.container && !valueProblem(node).isBlank()) {
                return requestNodeProblem(node, PendingNodeField.VALUE);
            }
        }
        return false;
    }

    private boolean requestNodeProblem(NbtNode node, PendingNodeField field) {
        focusNodeId = node.id;
        focusNodeField = field;
        return true;
    }

    private void requestInitialContentFocus() {
        clearFocusRequests();
        host.resetAutocomplete();
        if (target == DataTarget.ENTITY_DATA) {
            if (entityDataPresent) focusMetadataType = true;
            else focusPresenceToggle = true;
            return;
        }
        if (target == DataTarget.BLOCK_ENTITY_DATA) {
            if (blockEntityDataPresent) focusMetadataType = true;
            else focusPresenceToggle = true;
            return;
        }
        if (target.usesNbt()) {
            requestCollectionFocus();
            return;
        }
        requestCollectionFocus();
    }

    private void requestCollectionFocus() {
        focusNodeId = 0L;
        focusPropertyRowId = 0L;
        focusAddChildNodeId = 0L;
        focusAddButton = false;
        host.resetAutocomplete();
        if (target.usesNbt()) {
            if (entries.isEmpty()) focusAddButton = true;
            else focusNodeId = entries.getFirst().id;
        } else if (propertyRows.isEmpty()) {
            focusAddButton = true;
        } else {
            focusPropertyRowId = propertyRows.getFirst().id;
        }
    }

    private void clearFocusRequests() {
        codeEditor.cancelFocusRequest();
        focusNodeField = null;
        focusPropertyValue = false;
        focusAncestorIds.clear();
        focusNodeId = 0L;
        focusPropertyRowId = 0L;
        focusAddChildNodeId = 0L;
        focusAddButton = false;
        focusPresenceToggle = false;
        focusMetadataType = false;
    }

    private void applyPresenceFocus() {
        if (!focusPresenceToggle) return;
        ImGui.setKeyboardFocusHere();
        focusPresenceToggle = false;
    }

    private void applyMetadataTypeFocus() {
        if (!focusMetadataType) return;
        ImGui.setKeyboardFocusHere();
        focusMetadataType = false;
    }

    private void applyNodeFocus(NbtNode node, PendingNodeField field) {
        if (focusNodeId != node.id) return;
        if (focusNodeField != null && focusNodeField != field) return;
        ImGui.setKeyboardFocusHere();
        focusNodeId = 0L;
        focusNodeField = null;
        focusAncestorIds.clear();
    }

    private void removePropertyRow(int index) {
        if (index < 0 || index >= propertyRows.size()) return;
        propertyRows.remove(index);
        if (propertyRows.isEmpty()) {
            focusPropertyRowId = 0L;
            focusAddButton = true;
        } else {
            focusPropertyRowId = propertyRows.get(Math.min(index, propertyRows.size() - 1)).id;
            focusAddButton = false;
        }
        host.resetAutocomplete();
        dirty = true;
    }

    private void drawPropertyRows() {
        if (propertyRows.isEmpty()) {
            EditorUi.drawEmptyState(tr("data.properties.empty"));
            drawAddAction("bottom", true);
            return;
        }

        boolean debugStick = target == DataTarget.DEBUG_STICK_STATE;
        int remove = -1;
        if (ImGui.beginTable(
            "data_property_rows",
            3,
            ImGuiTableFlags.BordersInnerH
                | ImGuiTableFlags.RowBg
                | ImGuiTableFlags.SizingStretchProp
                | ImGuiTableFlags.NoPadOuterX
        )) {
            ImGui.tableSetupColumn(
                debugStick ? tr("data.debug_stick.block") : tr("data.block_state.property"),
                ImGuiTableColumnFlags.WidthStretch,
                debugStick ? 1.15f : 1.0f
            );
            ImGui.tableSetupColumn(
                debugStick ? tr("data.debug_stick.property") : tr("data.block_state.value"),
                ImGuiTableColumnFlags.WidthStretch,
                1.0f
            );
            ImGui.tableSetupColumn(
                tr("data.action"),
                ImGuiTableColumnFlags.WidthFixed,
                EditorIcons.smallButtonSize()
            );
            ImGui.tableHeadersRow();

            for (int index = 0; index < propertyRows.size(); index++) {
                PropertyRow row = propertyRows.get(index);
                ImGui.pushID("data_property_" + row.id);
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                if (focusPropertyRowId == row.id && !focusPropertyValue) {
                    ImGui.setScrollFromPosY(ImGui.getCursorPosY(), 0.25f);
                    ImGui.setKeyboardFocusHere();
                    focusPropertyRowId = 0L;
                }
                if (debugStick) {
                    if (host.inputIdentifierWithSuggestions(
                        "block",
                        new FieldHints(tr("field_purpose.block_identifier"), "minecraft:oak_log"),
                        row.key,
                        blockIds,
                        debugStickBlockProblem(index)
                    )) {
                        dirty = true;
                    }
                } else if (host.inputIdentifierWithSuggestions(
                    "property",
                    new FieldHints(
                        tr("field_purpose.block_state_property"),
                        tr("data.block_state.property_hint")
                    ),
                    row.key,
                    blockStatePropertySuggestions(),
                    propertyKeyProblem(index)
                )) {
                    dirty = true;
                }

                ImGui.tableNextColumn();
                List<IdSuggestion> valueSuggestions = debugStick
                    ? debugStickPropertySuggestions(row.key.get())
                    : blockStateValueSuggestions(row.key.get());
                String valueProblem = debugStick
                    ? debugStickPropertyProblem(row)
                    : blockStateValueProblem(row);
                if (focusPropertyRowId == row.id && focusPropertyValue) {
                    ImGui.setScrollFromPosY(ImGui.getCursorPosY(), 0.25f);
                    ImGui.setKeyboardFocusHere();
                    focusPropertyRowId = 0L;
                    focusPropertyValue = false;
                }
                if (host.inputIdentifierWithSuggestions(
                    debugStick ? "debug_property" : "value",
                    debugStick
                        ? new FieldHints(
                        tr("field_purpose.block_state_property"),
                        tr("data.debug_stick.property_hint")
                    )
                        : new FieldHints(
                        tr("field_purpose.block_state_value"),
                        tr("data.block_state.value_hint")
                    ),
                    row.value,
                    valueSuggestions,
                    valueProblem
                )) {
                    dirty = true;
                }

                ImGui.tableNextColumn();
                if (EditorIcons.smallButton("remove", EditorIcons.Icon.REMOVE, tr("action.remove_entry"))) {
                    remove = index;
                }
                ImGui.popID();
            }
            ImGui.endTable();
        }
        if (remove >= 0) {
            removePropertyRow(remove);
        }
        drawAddAction("bottom", true);
    }

    private void drawModeSelector() {
        if (modeButton(tr("data.view.tree") + "##data_tree_mode", !snbtMode)) {
            switchToTree();
        }
        ImGui.sameLine();
        if (modeButton(tr("data.view.snbt") + "##data_snbt_mode", snbtMode)) {
            switchToSnbt();
        }
    }

    private static boolean modeButton(String label, boolean selected) {
        if (selected) ItemEditorTheme.pushActiveFilter();
        boolean pressed = ImGui.button(label);
        if (selected) ItemEditorTheme.popActiveFilter();
        return pressed && !selected;
    }

    private void switchToTree() {
        TagValidation validation = validateTagSource(true);
        applyTagValidation(validation);
        if (!allowRepresentationChange(validation, tr("data.invalid_snbt", validation.problem()))) return;
        loadTree(validation.requireTag());
        snbtMode = false;
        requestCollectionFocus();
        dirty = true;
    }

    private void switchToSnbt() {
        TagValidation validation = validateTagSource(false);
        applyTagValidation(validation);
        if (!allowRepresentationChange(validation, tr("data.invalid_tree", validation.problem()))) return;
        snbtSource.set(prettySnbt(validation.requireTag()));
        snbtMode = true;
    }

    private boolean allowRepresentationChange(TagValidation validation, String invalidExplanation) {
        EditorNavigationGuard.Blocker blocker = null;
        if (!validation.valid()) {
            blocker = new EditorNavigationGuard.Blocker(
                tr("tab.data"),
                "data",
                EditorNavigationGuard.PendingState.INVALID,
                invalidExplanation,
                new EditorNavigationGuard.FocusRequest(snbtMode ? "data_snbt" : "data_tree")
            );
        } else if (isDirty()) {
            blocker = new EditorNavigationGuard.Blocker(
                tr("tab.data"),
                "data",
                EditorNavigationGuard.PendingState.VALID,
                tr("navigation.pending", tr("tab.data")),
                null
            );
        }
        EditorNavigationGuard.Decision decision = EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.COMPATIBLE_RETARGET,
            false,
            blocker
        );
        if (!decision.allowed()) {
            reportBlockedNavigation(decision);
        }
        return decision.allowed();
    }

    private void drawSnbt(Minecraft client, boolean inline) {
        TagValidation validation = validateTagSource(true);
        applyTagValidation(validation);
        boolean invalidBeforeEdit = !validation.valid();
        if (invalidBeforeEdit) ItemEditorTheme.pushInvalidField();
        float height = inline
            ? 300.0f
            : Math.max(160.0f, ImGui.getContentRegionAvailY() - 54.0f);
        if (codeEditor.draw(
            "custom_data_snbt",
            snbtSource,
            -1.0f,
            height,
            ImGuiInputTextFlags.AllowTabInput,
            validationLine,
            CodeLanguage.SNBT
        )) {
            dirty = true;
            validation = validateTagSource(true);
            applyTagValidation(validation);
        }
        if (invalidBeforeEdit) ItemEditorTheme.popInvalidField();
        ItemEditorTheme.drawFieldProblem(validation.valid()
            ? ""
            : tr("data.invalid_snbt", validation.problem()));

        if (ImGui.button(tr("data.format_snbt"))) {
            validation = validateTagSource(true);
            applyTagValidation(validation);
            if (validation.valid()) {
                snbtSource.set(prettySnbt(validation.requireTag()));
                dirty = true;
            }
        }
        EditorUi.sameLineIfFits(EditorIcons.buttonSize());
        if (EditorIcons.button("copy_custom_data_snbt", EditorIcons.Icon.COPY, tr("data.copy_snbt"))) {
            client.keyboardHandler.setClipboard(snbtSource.get());
            host.setStatus(true, tr("data.status.copied"));
        }
    }

    private void drawTree() {
        TagValidation validation = validateTagSource(false);
        applyTagValidation(validation);
        ItemEditorTheme.drawFieldProblem(validation.valid()
            ? ""
            : tr("data.invalid_tree", validation.problem()));

        if (entries.isEmpty()) {
            EditorUi.drawEmptyState(tr("data.empty"));
            drawAddAction("bottom", true);
            return;
        }
        String[] typeLabels = typeLabels();
        float typeColumnWidth = EditorUi.stableComboWidth(typeLabels, MIN_TYPE_COLUMN_WIDTH);
        if (!ImGui.beginTable(
            "custom_data_tree",
            4,
            ImGuiTableFlags.SizingStretchProp
                | ImGuiTableFlags.RowBg
                | ImGuiTableFlags.BordersInnerH
                | ImGuiTableFlags.BordersInnerV
                | ImGuiTableFlags.PadOuterX
        )) {
            return;
        }
        ImGui.tableSetupColumn(tr("data.key"), ImGuiTableColumnFlags.WidthStretch, 0.95f);
        ImGui.tableSetupColumn(
            tr("data.type"),
            ImGuiTableColumnFlags.WidthFixed | ImGuiTableColumnFlags.NoResize,
            typeColumnWidth
        );
        ImGui.tableSetupColumn(tr("data.value"), ImGuiTableColumnFlags.WidthStretch, 1.2f);
        ImGui.tableSetupColumn(
            "##actions",
            ImGuiTableColumnFlags.WidthFixed,
            EditorIcons.smallButtonSize() * 2.0f + ImGui.getStyle().getItemSpacingX()
        );
        ImGui.tableHeadersRow();
        drawNodeList(entries, NbtType.COMPOUND, 0, 0L);
        ImGui.endTable();
        drawAddAction("bottom", true);
    }

    private void drawNodeList(
        List<NbtNode> siblings,
        NbtType parentType,
        int depth,
        long parentNodeId
    ) {
        int actionIndex = -1;
        NodeAction pendingAction = NodeAction.NONE;
        for (int index = 0; index < siblings.size(); index++) {
            NbtNode node = siblings.get(index);
            ImGui.pushID(Long.toString(node.id));
            NodeAction action = drawNode(siblings, parentType, index, node, depth);
            ImGui.popID();
            if (action != NodeAction.NONE) {
                actionIndex = index;
                pendingAction = action;
            }
        }
        if (actionIndex >= 0) {
            NbtNode focusRow = applyAction(siblings, parentType, actionIndex, pendingAction);
            if (focusRow != null) {
                focusNodeId = focusRow.id;
                focusAddChildNodeId = 0L;
                focusAddButton = false;
            } else if (depth == 0) {
                focusNodeId = 0L;
                focusAddChildNodeId = 0L;
                focusAddButton = true;
            } else {
                focusNodeId = 0L;
                focusAddChildNodeId = parentNodeId;
            }
            dirty = true;
        }
    }

    private NodeAction drawNode(
        List<NbtNode> siblings,
        NbtType parentType,
        int index,
        NbtNode node,
        int depth
    ) {
        ImGui.tableNextRow(0, EditorIcons.smallButtonSize());
        if (focusNodeId == node.id || focusAncestorIds.contains(node.id)) {
            ImGui.setScrollFromPosY(ImGui.getCursorPosY(), 0.25f);
        }
        ImGui.tableNextColumn();
        ImGui.indent(depth * 17.0f);
        if (depth > 0) {
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled("\u2514");
            ImGui.sameLine();
        }
        boolean expanded = false;
        if (node.type.container) {
            if (focusAncestorIds.contains(node.id)) ImGui.setNextItemOpen(true, imgui.flag.ImGuiCond.Always);
            expanded = ImGui.treeNodeEx(
                "##expand",
                ImGuiTreeNodeFlags.DefaultOpen
                    | ImGuiTreeNodeFlags.NoTreePushOnOpen
                    | ImGuiTreeNodeFlags.FramePadding
            );
            ImGui.sameLine();
        } else {
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled("·");
            ImGui.sameLine();
        }
        if (parentType == NbtType.LIST) {
            ImGui.alignTextToFramePadding();
            ImGui.text(tr("data.list_item", index + 1));
        } else {
            String keyProblem = duplicateKeyProblem(siblings, index);
            if (!keyProblem.isBlank()) ItemEditorTheme.pushInvalidField();
            ImGui.setNextItemWidth(-1.0f);
            applyNodeFocus(node, PendingNodeField.KEY);
            if (inputTextWithHint(
                "##key",
                new FieldHints(tr("field_purpose.entry_key"), "CustomName"),
                node.key
            )) {
                dirty = true;
            }
            if (!keyProblem.isBlank()) ItemEditorTheme.popInvalidField();
            if (!keyProblem.isBlank() && ImGui.isItemHovered()) EditorUi.setTooltip(keyProblem);
        }
        ImGui.unindent(depth * 17.0f);

        ImGui.tableNextColumn();
        ImInt selectedType = new ImInt(node.type.ordinal());
        boolean listTypeLocked = parentType == NbtType.LIST && siblings.size() > 1;
        ImGui.beginDisabled(listTypeLocked);
        ImGui.setNextItemWidth(-1.0f);
        if (!listTypeLocked) applyNodeFocus(node, null);
        if (ImGui.combo("##type", selectedType, typeLabels())) {
            node.changeType(NbtType.values()[selectedType.get()]);
            dirty = true;
        }
        ImGui.endDisabled();
        if (listTypeLocked && ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("data.list_type_locked"));
        }

        ImGui.tableNextColumn();
        if (node.type.container) {
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(node.type == NbtType.COMPOUND
                ? tr("data.entry_count", node.children.size())
                : tr("data.element_count", node.children.size()));
        } else {
            String valueProblem = valueProblem(node);
            FieldHints hints = new FieldHints(valuePurpose(node.type), valueExample(node.type));
            if (!valueProblem.isBlank()) ItemEditorTheme.pushInvalidField();
            ImGui.setNextItemWidth(-1.0f);
            applyNodeFocus(node, PendingNodeField.VALUE);
            if (inputTextWithHint("##value", hints.purposeHint(), node.value)) {
                dirty = true;
            }
            if (!valueProblem.isBlank()) ItemEditorTheme.popInvalidField();
            if (!valueProblem.isBlank() && ImGui.isItemHovered()) {
                EditorUi.setTooltip(valueProblem);
            } else {
                EditorUi.showSyntaxExampleTooltip(hints.syntaxExample());
            }
        }

        ImGui.tableNextColumn();
        if (node.type.container) {
            if (focusAddChildNodeId == node.id) {
                ImGui.setKeyboardFocusHere();
                focusAddChildNodeId = 0L;
            }
            applyNodeFocus(node, null);
            if (EditorIcons.smallButton("add_child", EditorIcons.Icon.ADD, tr("data.add_child"))) {
                NbtNode added = addChild(node);
                focusNodeId = added.id;
                dirty = true;
            }
            ImGui.sameLine();
        }
        applyNodeFocus(node, PendingNodeField.ACTION);
        NodeAction action = drawActions(parentType, index, siblings.size());

        if (expanded) {
            drawNodeList(node.children, node.type, depth + 1, node.id);
        }
        return action;
    }

    private NodeAction drawActions(NbtType parentType, int index, int size) {
        return switch (EditorUi.drawCompactListActions(
            "data_node",
            index,
            size,
            parentType == NbtType.LIST,
            true,
            false
        )) {
            case MOVE_UP -> NodeAction.MOVE_UP;
            case MOVE_DOWN -> NodeAction.MOVE_DOWN;
            case DUPLICATE -> NodeAction.DUPLICATE;
            case REMOVE -> NodeAction.REMOVE;
            default -> NodeAction.NONE;
        };
    }

    private static NbtNode applyAction(
        List<NbtNode> siblings,
        NbtType parentType,
        int index,
        NodeAction action
    ) {
        NbtNode row = siblings.get(index);
        switch (action) {
            case MOVE_UP -> {
                if (index > 0) java.util.Collections.swap(siblings, index, index - 1);
                return row;
            }
            case MOVE_DOWN -> {
                if (index + 1 < siblings.size()) java.util.Collections.swap(siblings, index, index + 1);
                return row;
            }
            case DUPLICATE -> {
                NbtNode copy = row.deepCopy();
                if (parentType == NbtType.COMPOUND) {
                    copy.key.set(uniqueKey(siblings, copy.key.get() + "_copy"));
                }
                siblings.add(index + 1, copy);
                return copy;
            }
            case REMOVE -> {
                siblings.remove(index);
                return siblings.isEmpty() ? null : siblings.get(Math.min(index, siblings.size() - 1));
            }
            default -> {
                return null;
            }
        }
    }

    private static NbtNode addChild(NbtNode parent) {
        NbtNode added;
        if (parent.type == NbtType.LIST) {
            NbtType type = parent.children.isEmpty() ? NbtType.STRING : parent.children.getFirst().type;
            added = newNode("", type);
        } else {
            added = newNode("", NbtType.STRING);
        }
        parent.children.add(added);
        return added;
    }

    public boolean save(Minecraft client, boolean announce) {
        if (target == DataTarget.CUSTOM_DATA && host.hasPendingDiamondFireTagEdits()) {
            host.setStatus(false, tr("data.diamondfire_pending"));
            return false;
        }
        try {
            ItemStack edited = pendingStack();
            ItemStackCodecs.validateStack(edited);
            host.acceptDataDraft(client, edited, target.type);
            if (announce) host.setStatus(true, tr("message.saved"));
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    public ItemStack pendingStack() {
        ItemStack edited = host.draft().copy();
        switch (target) {
            case CUSTOM_DATA -> setCustomData(edited, DataComponents.CUSTOM_DATA, pendingTag());
            case BUCKET_ENTITY_DATA -> setCustomData(
                edited,
                DataComponents.BUCKET_ENTITY_DATA,
                pendingTag()
            );
            case ENTITY_DATA -> {
                if (!entityDataPresent) {
                    edited.remove(DataComponents.ENTITY_DATA);
                } else {
                    EntityType<?> resolvedType = resolveEntityType(entityType.get());
                    edited.set(DataComponents.ENTITY_DATA, TypedEntityData.of(resolvedType, pendingTag()));
                }
            }
            case BLOCK_ENTITY_DATA -> {
                if (!blockEntityDataPresent) {
                    edited.remove(DataComponents.BLOCK_ENTITY_DATA);
                } else {
                    BlockEntityType<?> resolvedType = resolveBlockEntityType(blockEntityType.get());
                    edited.set(
                        DataComponents.BLOCK_ENTITY_DATA,
                        TypedEntityData.of(resolvedType, pendingTag())
                    );
                }
            }
            case BLOCK_STATE -> {
                BlockItemStateProperties value = pendingBlockState();
                if (value.isEmpty()) edited.remove(DataComponents.BLOCK_STATE);
                else edited.set(DataComponents.BLOCK_STATE, value);
            }
            case DEBUG_STICK_STATE -> {
                DebugStickState value = pendingDebugStickState();
                if (value.properties().isEmpty()) edited.remove(DataComponents.DEBUG_STICK_STATE);
                else edited.set(DataComponents.DEBUG_STICK_STATE, value);
            }
        }
        return edited;
    }

    public DataComponentType<?> pendingComponentType() {
        return target.type;
    }

    private CompoundTag pendingTag() {
        CompoundTag tag = validateTagSource(snbtMode).requireTag();
        if ((target == DataTarget.ENTITY_DATA || target == DataTarget.BLOCK_ENTITY_DATA)
            && tag.contains("id")) {
            throw new IllegalArgumentException(tr("data.typed.reserved_id"));
        }
        return tag;
    }

    private BlockItemStateProperties pendingBlockState() {
        Map<String, String> properties = new LinkedHashMap<>();
        for (int index = 0; index < propertyRows.size(); index++) {
            PropertyRow row = propertyRows.get(index);
            requireNoProblem(propertyKeyProblem(index));
            requireNoProblem(blockStateValueProblem(row));
            String key = row.key.get().trim();
            String value = row.value.get().trim();
            properties.put(key, value);
        }
        return new BlockItemStateProperties(Map.copyOf(properties));
    }

    private DebugStickState pendingDebugStickState() {
        Map<Holder<Block>, Property<?>> properties = new LinkedHashMap<>();
        for (int index = 0; index < propertyRows.size(); index++) {
            PropertyRow row = propertyRows.get(index);
            requireNoProblem(debugStickBlockProblem(index));
            requireNoProblem(debugStickPropertyProblem(row));
            Block block = resolveBlock(row.key.get());
            Property<?> property = block.getStateDefinition().getProperty(row.value.get().trim());
            Holder<Block> holder = BuiltInRegistries.BLOCK.wrapAsHolder(block);
            properties.put(holder, Objects.requireNonNull(property));
        }
        return new DebugStickState(Map.copyOf(properties));
    }

    private static void setCustomData(
        ItemStack stack,
        DataComponentType<CustomData> type,
        CompoundTag tag
    ) {
        if (tag.isEmpty()) stack.remove(type);
        else stack.set(type, CustomData.of(tag));
    }

    private CompoundTag buildRoot() {
        CompoundTag root = new CompoundTag();
        Set<String> keys = new HashSet<>();
        for (NbtNode entry : entries) {
            String key = entry.key.get();
            if (key.isBlank()) throw new IllegalArgumentException(tr("validation.key_required"));
            if (!keys.add(key)) throw new IllegalArgumentException(tr("data.duplicate_key", key));
            root.put(key, buildTag(entry, key));
        }
        return root;
    }

    private static Tag buildTag(NbtNode node, String path) {
        try {
            return switch (node.type) {
                case BYTE -> parseByte(node.value.get());
                case SHORT -> ShortTag.valueOf(Short.parseShort(node.value.get().strip()));
                case INT -> IntTag.valueOf(Integer.parseInt(node.value.get().strip()));
                case LONG -> LongTag.valueOf(Long.parseLong(node.value.get().strip()));
                case FLOAT -> FloatTag.valueOf(Float.parseFloat(node.value.get().strip()));
                case DOUBLE -> DoubleTag.valueOf(Double.parseDouble(node.value.get().strip()));
                case BYTE_ARRAY -> new ByteArrayTag(parseByteArray(node.value.get()));
                case STRING -> StringTag.valueOf(node.value.get());
                case LIST -> buildList(node, path);
                case COMPOUND -> buildCompound(node, path);
                case INT_ARRAY -> new IntArrayTag(parseIntArray(node.value.get()));
                case LONG_ARRAY -> new LongArrayTag(parseLongArray(node.value.get()));
            };
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(tr("data.invalid_number", path, node.type.label()), exception);
        }
    }

    private static ByteTag parseByte(String source) {
        String value = source.strip();
        if (value.equalsIgnoreCase("true")) return ByteTag.ONE;
        if (value.equalsIgnoreCase("false")) return ByteTag.ZERO;
        return ByteTag.valueOf(Byte.parseByte(value));
    }

    private static CompoundTag buildCompound(NbtNode node, String path) {
        CompoundTag compound = new CompoundTag();
        Set<String> keys = new HashSet<>();
        for (NbtNode child : node.children) {
            String key = child.key.get();
            if (key.isBlank()) throw new IllegalArgumentException(tr("validation.key_required"));
            if (!keys.add(key)) throw new IllegalArgumentException(tr("data.duplicate_key", key));
            compound.put(key, buildTag(child, path + "." + key));
        }
        return compound;
    }

    private static ListTag buildList(NbtNode node, String path) {
        ListTag list = new ListTag();
        byte elementType = Tag.TAG_END;
        for (int index = 0; index < node.children.size(); index++) {
            Tag value = buildTag(node.children.get(index), path + "[" + index + "]");
            if (elementType == Tag.TAG_END) elementType = value.getId();
            if (value.getId() != elementType) {
                throw new IllegalArgumentException(tr("data.mixed_list", path));
            } else {
                list.addTag(list.size(), value);
            }
        }
        return list;
    }

    private static String valueProblem(NbtNode node) {
        return problemFrom(() -> {
            if (!node.type.container) buildTag(node, tr("data.value").toLowerCase(Locale.ROOT));
        });
    }

    private static String duplicateKeyProblem(List<NbtNode> siblings, int index) {
        String key = siblings.get(index).key.get();
        if (key.isBlank()) return tr("validation.key_required");
        for (int cursor = 0; cursor < siblings.size(); cursor++) {
            if (cursor != index && siblings.get(cursor).key.get().equals(key)) {
                return tr("data.duplicate_key", key);
            }
        }
        return "";
    }

    private static byte[] parseByteArray(String source) {
        String[] values = arrayValues(source);
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) result[index] = Byte.parseByte(values[index]);
        return result;
    }

    private static int[] parseIntArray(String source) {
        String[] values = arrayValues(source);
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) result[index] = Integer.parseInt(values[index]);
        return result;
    }

    private static long[] parseLongArray(String source) {
        String[] values = arrayValues(source);
        long[] result = new long[values.length];
        for (int index = 0; index < values.length; index++) result[index] = Long.parseLong(values[index]);
        return result;
    }

    private static String[] arrayValues(String source) {
        String stripped = source.strip();
        return stripped.isEmpty() ? new String[0] : stripped.split("[,\\s]+");
    }

    private static CompoundTag parseSnbt(String source) {
        try {
            return TagParser.parseCompoundFully(source);
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private TagValidation validateTagSource(boolean sourceSnbt) {
        try {
            CompoundTag tag = sourceSnbt ? parseSnbt(snbtSource.get()) : buildRoot();
            return new TagValidation(tag, null, "", 0);
        } catch (RuntimeException exception) {
            int line = sourceSnbt ? CodeEditorInput.errorLine(exception, snbtSource.get()) : 0;
            return new TagValidation(null, exception, EditorUi.readableMessage(exception), line);
        }
    }

    private void applyTagValidation(TagValidation validation) {
        validationLine = validation.line();
    }

    private static void requireNoProblem(String problem) {
        if (!problem.isBlank()) throw new IllegalArgumentException(problem);
    }

    private static String problemFrom(Runnable validation) {
        try {
            validation.run();
            return "";
        } catch (RuntimeException exception) {
            return EditorUi.readableMessage(exception);
        }
    }

    private static String prettySnbt(Tag tag) {
        return new SnbtPrinterTagVisitor("  ", 0, new ArrayList<>()).visit(tag);
    }

    private void loadTree(CompoundTag root) {
        entries.clear();
        root.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toLowerCase(Locale.ROOT)))
            .forEach(entry -> entries.add(fromTag(entry.getKey(), entry.getValue())));
    }

    private static NbtNode fromTag(String key, Tag tag) {
        NbtType type = NbtType.from(tag);
        NbtNode node = newNode(key, type);
        if (tag instanceof CompoundTag compound) {
            compound.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toLowerCase(Locale.ROOT)))
                .forEach(entry -> node.children.add(fromTag(entry.getKey(), entry.getValue())));
        } else if (tag instanceof ListTag list) {
            for (Tag value : list) {
                node.children.add(fromTag("", value));
            }
        } else {
            node.value.set(valueText(tag));
        }
        return node;
    }

    private static String valueText(Tag tag) {
        if (tag instanceof StringTag(String value)) return value;
        if (tag instanceof NumericTag number) return number.box().toString();
        if (tag instanceof ByteArrayTag array) return join(array.getAsByteArray());
        if (tag instanceof IntArrayTag array) return Arrays.stream(array.getAsIntArray())
            .mapToObj(Integer::toString)
            .collect(java.util.stream.Collectors.joining(", "));
        if (tag instanceof LongArrayTag array) return Arrays.stream(array.getAsLongArray())
            .mapToObj(Long::toString)
            .collect(java.util.stream.Collectors.joining(", "));
        return "";
    }

    private static String join(byte[] values) {
        StringBuilder result = new StringBuilder();
        for (byte value : values) {
            if (!result.isEmpty()) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private CompoundTag currentTag(ItemStack stack) {
        return switch (target) {
            case CUSTOM_DATA -> stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            case BUCKET_ENTITY_DATA -> stack.getOrDefault(
                DataComponents.BUCKET_ENTITY_DATA,
                CustomData.EMPTY
            ).copyTag();
            case ENTITY_DATA -> {
                TypedEntityData<EntityType<?>> data = stack.get(DataComponents.ENTITY_DATA);
                yield data == null ? new CompoundTag() : data.copyTagWithoutId();
            }
            case BLOCK_ENTITY_DATA -> {
                TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                yield data == null ? new CompoundTag() : data.copyTagWithoutId();
            }
            case BLOCK_STATE, DEBUG_STICK_STATE -> new CompoundTag();
        };
    }

    private void syncEntityMetadata(ItemStack stack) {
        if (target != DataTarget.ENTITY_DATA) return;
        TypedEntityData<EntityType<?>> data = stack.get(DataComponents.ENTITY_DATA);
        entityDataPresent = data != null;
        Identifier id = data == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(data.type());
        entityType.set(id == null ? "minecraft:pig" : id.toString());
    }

    private void syncBlockEntityMetadata(ItemStack stack) {
        if (target != DataTarget.BLOCK_ENTITY_DATA) return;
        TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        blockEntityDataPresent = data != null;
        Identifier id = data == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(data.type());
        blockEntityType.set(id == null ? "minecraft:chest" : id.toString());
    }

    private void syncPropertyRows(ItemStack stack) {
        propertyRows.clear();
        if (target == DataTarget.BLOCK_STATE) {
            BlockItemStateProperties state = stack.getOrDefault(
                DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY
            );
            state.properties().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> propertyRows.add(new PropertyRow(entry.getKey(), entry.getValue())));
        } else if (target == DataTarget.DEBUG_STICK_STATE) {
            DebugStickState state = stack.getOrDefault(
                DataComponents.DEBUG_STICK_STATE,
                DebugStickState.EMPTY
            );
            state.properties().entrySet().stream()
                .sorted(Comparator.comparing(entry -> normalizedBlockId(entry.getKey().value())))
                .forEach(entry -> propertyRows.add(new PropertyRow(
                    normalizedBlockId(entry.getKey().value()),
                    entry.getValue().getName()
                )));
        }
    }

    private static EntityType<?> resolveEntityType(String source) {
        String value = source == null ? "" : source.trim();
        Identifier id = Identifier.tryParse(value);
        // This registry returns its default pig for unknown IDs; reject before that fallback.
        EntityType<?> type = id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)
            ? null
            : BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == null) {
            throw new IllegalArgumentException(id == null
                ? tr("validation.identifier_format")
                : tr("data.entity.unknown_type", value));
        }
        return type;
    }

    private static String entityTypeProblem(String source) {
        return problemFrom(() -> resolveEntityType(source));
    }

    private static BlockEntityType<?> resolveBlockEntityType(String source) {
        String value = source == null ? "" : source.trim();
        Identifier id = Identifier.tryParse(value);
        BlockEntityType<?> type = id == null || !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)
            ? null
            : BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
        if (type == null) {
            throw new IllegalArgumentException(id == null
                ? tr("validation.identifier_format")
                : tr("data.block_entity.unknown_type", value));
        }
        return type;
    }

    private static String blockEntityTypeProblem(String source) {
        return problemFrom(() -> resolveBlockEntityType(source));
    }

    private PropertyRow defaultPropertyRow() {
        Block block = target == DataTarget.DEBUG_STICK_STATE ? defaultDebugBlock() : currentItemBlock();
        Property<?> property = block.getStateDefinition().getProperties().stream().findFirst().orElse(null);
        if (target == DataTarget.DEBUG_STICK_STATE) {
            return new PropertyRow(
                normalizedBlockId(block),
                property == null ? "" : property.getName()
            );
        }
        return new PropertyRow(
            property == null ? "" : property.getName(),
            property == null ? "" : firstPropertyValue(property)
        );
    }

    private Block currentItemBlock() {
        return host.draft().getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : Blocks.AIR;
    }

    private Block defaultDebugBlock() {
        Block current = currentItemBlock();
        if (!current.getStateDefinition().getProperties().isEmpty()) return current;
        Identifier oakLogId = Identifier.tryParse("minecraft:oak_log");
        Block oakLog = oakLogId == null ? null : BuiltInRegistries.BLOCK.getValue(oakLogId);
        if (oakLog != null && !oakLog.getStateDefinition().getProperties().isEmpty()) return oakLog;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.getStateDefinition().getProperties().isEmpty()) return block;
        }
        return current;
    }

    private List<IdSuggestion> blockStatePropertySuggestions() {
        return propertySuggestions(currentItemBlock());
    }

    private static List<IdSuggestion> debugStickPropertySuggestions(String blockId) {
        try {
            return propertySuggestions(resolveBlock(blockId));
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<IdSuggestion> blockStateValueSuggestions(String propertyName) {
        Property<?> property = currentItemBlock().getStateDefinition().getProperty(propertyName.trim());
        return property == null ? List.of() : propertyValueSuggestions(property);
    }

    private static List<IdSuggestion> propertySuggestions(Block block) {
        return block.getStateDefinition().getProperties().stream()
            .map(property -> IdSuggestion.of(property.getName()))
            .sorted(Comparator.comparing(IdSuggestion::id))
            .toList();
    }

    private static <T extends Comparable<T>> List<IdSuggestion> propertyValueSuggestions(Property<T> property) {
        return property.getPossibleValues().stream()
            .map(value -> IdSuggestion.of(property.getName(value)))
            .toList();
    }

    private static <T extends Comparable<T>> String firstPropertyValue(Property<T> property) {
        List<T> values = property.getPossibleValues();
        return values.isEmpty() ? "value" : property.getName(values.getFirst());
    }

    private String propertyKeyProblem(int index) {
        String key = propertyRows.get(index).key.get().trim();
        if (key.isEmpty()) return tr("data.property.required");
        for (int cursor = 0; cursor < propertyRows.size(); cursor++) {
            if (cursor != index && propertyRows.get(cursor).key.get().trim().equals(key)) {
                return tr("data.property.duplicate", key);
            }
        }
        return "";
    }

    private String debugStickBlockProblem(int index) {
        try {
            Block block = resolveBlock(propertyRows.get(index).key.get());
            for (int cursor = 0; cursor < propertyRows.size(); cursor++) {
                if (cursor == index) continue;
                try {
                    if (resolveBlock(propertyRows.get(cursor).key.get()) == block) {
                        return tr("data.debug_stick.duplicate_block", normalizedBlockId(block));
                    }
                } catch (RuntimeException ignored) {
                    // The other row reports its own invalid identifier.
                }
            }
            return "";
        } catch (RuntimeException exception) {
            return EditorUi.readableMessage(exception);
        }
    }

    private String blockStateValueProblem(PropertyRow row) {
        String value = row.value.get().trim();
        if (value.isEmpty()) return tr("data.value.required");
        Property<?> property = currentItemBlock().getStateDefinition().getProperty(row.key.get().trim());
        return property != null && property.getValue(value).isEmpty()
            ? tr("data.block_state.invalid_value", value, property.getName())
            : "";
    }

    private static String debugStickPropertyProblem(PropertyRow row) {
        String propertyName = row.value.get().trim();
        if (propertyName.isEmpty()) return tr("data.property.required");
        try {
            Block block = resolveBlock(row.key.get());
            return block.getStateDefinition().getProperty(propertyName) == null
                ? tr("data.debug_stick.unknown_property", propertyName, normalizedBlockId(block))
                : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static Block resolveBlock(String source) {
        String value = source == null ? "" : source.trim();
        Identifier id = Identifier.tryParse(value);
        Block block = id == null || !BuiltInRegistries.BLOCK.containsKey(id)
            ? null
            : BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) {
            throw new IllegalArgumentException(id == null
                ? tr("validation.identifier_format")
                : tr("data.debug_stick.unknown_block", value));
        }
        return block;
    }

    private static String normalizedBlockId(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id.toString();
    }

    private static NbtNode newNode(String key, NbtType type) {
        return new NbtNode(key, type, type.defaultValue);
    }

    private static String uniqueKey(List<NbtNode> siblings, String requested) {
        Set<String> keys = new HashSet<>();
        siblings.forEach(node -> keys.add(node.key.get()));
        if (!keys.contains(requested)) return requested;
        for (int suffix = 2; ; suffix++) {
            String candidate = requested + "_" + suffix;
            if (!keys.contains(candidate)) return candidate;
        }
    }

    private static String[] typeLabels() {
        return Arrays.stream(NbtType.values()).map(NbtType::label).toArray(String[]::new);
    }

    private static String valuePurpose(NbtType type) {
        return switch (type) {
            case BYTE_ARRAY, INT_ARRAY, LONG_ARRAY -> tr("field_purpose.array_values");
            case STRING -> tr("field_purpose.text_value");
            default -> tr("field_purpose.numeric_value");
        };
    }

    private static String valueExample(NbtType type) {
        return switch (type) {
            case BYTE -> tr("data.value_hint.byte");
            case BYTE_ARRAY, INT_ARRAY, LONG_ARRAY -> tr("data.value_hint.array");
            default -> "";
        };
    }

    public HistoryState historyState() {
        List<NodeState> nodeStates = entries.stream().map(NbtNode::state).toList();
        List<PropertyState> propertyStates = propertyRows.stream().map(PropertyRow::state).toList();
        boolean clean = !isDirty();
        Object semanticValue = null;
        String invalidState = null;
        if (!clean) {
            try {
                ItemStack pending = pendingStack();
                semanticValue = pending.get(target.type);
            } catch (RuntimeException ignored) {
                invalidState = (snbtMode ? "snbt:" + snbtSource.get() : "tree:" + nodeStates)
                    + ":present=" + entityDataPresent
                    + ":entity_type=" + entityType.get()
                    + ":block_present=" + blockEntityDataPresent
                    + ":block_entity_type=" + blockEntityType.get()
                    + ":properties=" + propertyStates;
            }
        }
        return new HistoryState(
            target,
            snbtMode,
            snbtSource.get(),
            nodeStates,
            entityDataPresent,
            entityType.get(),
            blockEntityDataPresent,
            blockEntityType.get(),
            propertyStates,
            clean,
            semanticValue,
            invalidState
        );
    }

    public void restoreHistoryState(HistoryState state) {
        target = state.target;
        snbtMode = state.snbtMode;
        snbtSource.set(state.snbtSource);
        entityDataPresent = state.entityDataPresent;
        entityType.set(state.entityType);
        blockEntityDataPresent = state.blockEntityDataPresent;
        blockEntityType.set(state.blockEntityType);
        entries.clear();
        state.entries.forEach(entry -> entries.add(NbtNode.fromState(entry)));
        propertyRows.clear();
        state.properties.forEach(entry -> propertyRows.add(PropertyRow.fromState(entry)));
        validationLine = 0;
        clearFocusRequests();
        dirty = true;
    }

    public static final class HistoryState {
        private final DataTarget target;
        private final boolean snbtMode;
        private final String snbtSource;
        private final List<NodeState> entries;
        private final boolean entityDataPresent;
        private final String entityType;
        private final boolean blockEntityDataPresent;
        private final String blockEntityType;
        private final List<PropertyState> properties;
        private final boolean clean;
        private final Object semanticValue;
        private final String invalidState;

        private HistoryState(
            DataTarget target,
            boolean snbtMode,
            String snbtSource,
            List<NodeState> entries,
            boolean entityDataPresent,
            String entityType,
            boolean blockEntityDataPresent,
            String blockEntityType,
            List<PropertyState> properties,
            boolean clean,
            Object semanticValue,
            String invalidState
        ) {
            this.target = target;
            this.snbtMode = snbtMode;
            this.snbtSource = snbtSource;
            this.entries = List.copyOf(entries);
            this.entityDataPresent = entityDataPresent;
            this.entityType = entityType;
            this.blockEntityDataPresent = blockEntityDataPresent;
            this.blockEntityType = blockEntityType;
            this.properties = List.copyOf(properties);
            this.clean = clean;
            this.semanticValue = semanticValue;
            this.invalidState = invalidState;
        }

        public boolean sameState(HistoryState other) {
            if (other == null || clean != other.clean) return false;
            if (clean) return true;
            return target == other.target
                && Objects.equals(semanticValue, other.semanticValue)
                && Objects.equals(invalidState, other.invalidState);
        }
    }

    private record TagValidation(
        CompoundTag tag,
        RuntimeException failure,
        String problem,
        int line
    ) {
        private boolean valid() {
            return failure == null;
        }

        private CompoundTag requireTag() {
            if (failure != null) throw failure;
            return Objects.requireNonNull(tag);
        }
    }

    private static final class NbtNode {
        private final long id = NEXT_NODE_ID.incrementAndGet();
        private final ImString key;
        private NbtType type;
        private final ImString value;
        private final List<NbtNode> children = new ArrayList<>();

        private NbtNode(String key, NbtType type, String value) {
            this.key = new ImString(key, Math.max(512, key.length() * 2 + 32));
            this.type = type;
            this.value = new ImString(value, Math.max(VALUE_CAPACITY, value.length() * 2 + 64));
        }

        private void changeType(NbtType next) {
            if (type == next) return;
            type = next;
            children.clear();
            value.set(next.defaultValue);
        }

        private NbtNode deepCopy() {
            NbtNode copy = new NbtNode(key.get(), type, value.get());
            children.forEach(child -> copy.children.add(child.deepCopy()));
            return copy;
        }

        private NodeState state() {
            return new NodeState(
                key.get(),
                type,
                value.get(),
                children.stream().map(NbtNode::state).toList()
            );
        }

        private static NbtNode fromState(NodeState state) {
            NbtNode node = new NbtNode(state.key, state.type, state.value);
            state.children.forEach(child -> node.children.add(fromState(child)));
            return node;
        }
    }

    private record NodeState(String key, NbtType type, String value, List<NodeState> children) {
        private NodeState {
            children = List.copyOf(children);
        }
    }

    private static final class PropertyRow {
        private final long id = NEXT_NODE_ID.incrementAndGet();
        private final ImString key;
        private final ImString value;

        private PropertyRow(String key, String value) {
            this.key = new ImString(key, Math.max(512, key.length() * 2 + 32));
            this.value = new ImString(value, Math.max(512, value.length() * 2 + 32));
        }

        private PropertyState state() {
            return new PropertyState(key.get(), value.get());
        }

        private static PropertyRow fromState(PropertyState state) {
            return new PropertyRow(state.key, state.value);
        }
    }

    private record PropertyState(String key, String value) {
    }

    private enum DataTarget {
        CUSTOM_DATA(
            "custom_data",
            DataComponents.CUSTOM_DATA,
            "data.target.custom_data",
            "data.custom_data"
        ),
        ENTITY_DATA(
            "entity_data",
            DataComponents.ENTITY_DATA,
            "data.target.entity_data",
            "data.entity_data"
        ),
        BUCKET_ENTITY_DATA(
            "bucket_entity_data",
            DataComponents.BUCKET_ENTITY_DATA,
            "data.target.bucket_entity_data",
            "data.bucket_entity_data"
        ),
        BLOCK_ENTITY_DATA(
            "block_entity_data",
            DataComponents.BLOCK_ENTITY_DATA,
            "data.target.block_entity_data",
            "data.block_entity_data"
        ),
        BLOCK_STATE(
            "block_state",
            DataComponents.BLOCK_STATE,
            "data.target.block_state",
            "data.block_state"
        ),
        DEBUG_STICK_STATE(
            "debug_stick_state",
            DataComponents.DEBUG_STICK_STATE,
            "data.target.debug_stick_state",
            "data.debug_stick_state"
        );

        private final String id;
        private final DataComponentType<?> type;
        private final String selectorKey;
        private final String titleKey;

        DataTarget(String id, DataComponentType<?> type, String selectorKey, String titleKey) {
            this.id = id;
            this.type = type;
            this.selectorKey = selectorKey;
            this.titleKey = titleKey;
        }

        private static DataTarget from(DataComponentType<?> type) {
            for (DataTarget target : values()) {
                if (target.type == type) return target;
            }
            return null;
        }

        private boolean usesNbt() {
            return this != BLOCK_STATE && this != DEBUG_STICK_STATE;
        }
    }

    private enum NbtType {
        BYTE("byte", "0", false),
        SHORT("short", "0", false),
        INT("int", "0", false),
        LONG("long", "0", false),
        FLOAT("float", "0.0", false),
        DOUBLE("double", "0.0", false),
        BYTE_ARRAY("byte_array", "", false),
        STRING("string", "", false),
        LIST("list", "", true),
        COMPOUND("compound", "", true),
        INT_ARRAY("int_array", "", false),
        LONG_ARRAY("long_array", "", false);

        private final String id;
        private final String defaultValue;
        private final boolean container;

        NbtType(String id, String defaultValue, boolean container) {
            this.id = id;
            this.defaultValue = defaultValue;
            this.container = container;
        }

        private String label() {
            return tr("data.type." + id);
        }

        private static NbtType from(Tag tag) {
            return switch (tag.getId()) {
                case Tag.TAG_BYTE -> BYTE;
                case Tag.TAG_SHORT -> SHORT;
                case Tag.TAG_INT -> INT;
                case Tag.TAG_LONG -> LONG;
                case Tag.TAG_FLOAT -> FLOAT;
                case Tag.TAG_DOUBLE -> DOUBLE;
                case Tag.TAG_BYTE_ARRAY -> BYTE_ARRAY;
                case Tag.TAG_STRING -> STRING;
                case Tag.TAG_LIST -> LIST;
                case Tag.TAG_COMPOUND -> COMPOUND;
                case Tag.TAG_INT_ARRAY -> INT_ARRAY;
                case Tag.TAG_LONG_ARRAY -> LONG_ARRAY;
                default -> throw new IllegalArgumentException("Unsupported NBT type: " + tag.getId());
            };
        }
    }

    private enum NodeAction {
        NONE,
        MOVE_UP,
        MOVE_DOWN,
        DUPLICATE,
        REMOVE
    }

}
