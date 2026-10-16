package dev.nineofgaming.itemlab.editor.panel;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.BeesDraft;
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
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Lossless, Components-tab-only editor for ordered beehive occupants.
 */
public final class BeesEditorPanel extends ComponentEditorPanel {
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong();
    private static final String DEFAULT_ENTITY_TYPE = "minecraft:bee";
    private static final int NORMAL_CAPACITY = 3;
    private static final int SNBT_CAPACITY = 262_144;

    private final List<OccupantRow> rows = new ArrayList<>();
    private boolean present;
    private List<IdSuggestion> entityTypeSuggestions = List.of();
    private Set<String> knownEntityTypes = Set.of();
    private long focusRowId;
    private boolean focusAddButton;
    private boolean focusPresenceToggle;
    private long focusEntityDataRowId;
    private OrderedListDragDrop.Scope rowDragScope = OrderedListDragDrop.newScope("bees");

    public BeesEditorPanel(ItemEditor host) {
        super(host, "bees");
    }

    @Override
    public void requestPendingFocus() {
        focusEntityDataRowId = 0L;
        OccupantRow blocking = rows.isEmpty() ? null : rows.getFirst();
        for (OccupantRow row : rows) {
            if (!entityTypeProblem(row.entityType.get()).isBlank()) {
                blocking = row;
                break;
            }
            try {
                parseEntityData(row.snbt.get());
            } catch (RuntimeException exception) {
                blocking = row;
                focusEntityDataRowId = row.id;
                break;
            }
        }
        focusRowId = blocking == null ? 0L : blocking.id;
        focusPresenceToggle = blocking == null || !present;
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.BEES;
    }

    public void setCatalog(EditorCatalog catalog) {
        List<IdSuggestion> sorted = new ArrayList<>(catalog.entityTypeIds());
        sorted.sort(Comparator
            .comparing((IdSuggestion suggestion) -> !isBeehiveInhabitant(suggestion.id()))
            .thenComparing(IdSuggestion::id));
        entityTypeSuggestions = List.copyOf(sorted);
        Set<String> ids = new HashSet<>();
        for (IdSuggestion suggestion : entityTypeSuggestions) ids.add(suggestion.id());
        knownEntityTypes = Set.copyOf(ids);
    }

    public void sync(Minecraft client, ItemStack stack) {
        BeesDraft draft = BeesDraft.fromStack(stack);
        present = draft.present();
        rows.clear();
        for (int index = 0; index < draft.size(); index++) {
            rows.add(newRow(draft.occupant(index)));
        }
        resetEditorUi();
        focusPresenceToggle = false;
        focusEntityDataRowId = 0L;
        focusRowId = 0L;
        focusAddButton = false;
        rowDragScope = OrderedListDragDrop.newScope("bees");
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (supports(type)) sync(client, stack);
    }

    public void draw(Minecraft client) {
        if (drawPanelToolbar(
            () -> save(client, true),
            () -> sync(client, host.draft())
        )) return;

        if (!beginBeesSection()) return;
        try {
            drawGuidance();
            drawAddAction("top", false, true);
            ImGui.spacing();
            drawRows();
        } finally {
            ImGui.endDisabled();
            EditorUi.endCard();
        }
        OrderedListDragDrop.autoScrollCurrentWindow(rowDragScope);
    }

    private boolean beginBeesSection() {
        if (!EditorUi.beginCard("bees_component", 1, ImGuiTableFlags.SizingStretchProp,
            focusRowId != 0L || focusPresenceToggle)) {
            return false;
        }
        ImGui.tableNextColumn();
        ImBoolean enabled = new ImBoolean(present);
        boolean revealForFocus = focusRowId != 0L || focusPresenceToggle;
        if (focusPresenceToggle) {
            ImGui.setKeyboardFocusHere();
            focusPresenceToggle = false;
        }
        if (ImGui.checkbox("##enabled_bees", enabled)) {
            present = enabled.get();
            if (present && rows.isEmpty()) {
                OccupantRow added = newRow(BeesDraft.normalBee());
                rows.add(added);
                focusRowId = added.id;
            }
            dirty = true;
        }
        ImGui.sameLine();
        boolean expanded = host.drawComponentSectionHeader(
            "bees_section",
            tr("panel.bees.section.occupants.title"),
            DataComponents.BEES,
            revealForFocus
        );
        if (!expanded) {
            EditorUi.endCard();
            return false;
        }
        ImGui.beginDisabled(!present);
        ImGui.spacing();
        return true;
    }

    private void drawGuidance() {
        ItemStack owner = host.draft();
        if (!owner.is(Items.BEE_NEST) && !owner.is(Items.BEEHIVE)) {
            ItemEditorTheme.drawFieldWarning(tr("bees.owner_warning"));
        }
        if (rows.size() > NORMAL_CAPACITY) {
            ItemEditorTheme.drawFieldWarning(tr("bees.capacity_warning", rows.size(), NORMAL_CAPACITY));
        }
    }

    private void drawAddAction(String placement, boolean acceptFocus, boolean showCount) {
        if (acceptFocus && focusAddButton) {
            ImGui.setKeyboardFocusHere();
            focusAddButton = false;
        }
        if (EditorIcons.smallButton(
            "add_bee_occupant_" + placement,
            EditorIcons.Icon.ADD,
            tr("bees.add")
        )) {
            OccupantRow added = newRow(BeesDraft.normalBee());
            rows.add(added);
            focusRowId = added.id;
            focusAddButton = false;
            present = true;
            dirty = true;
            host.resetAutocomplete();
            resetEditorUi();
        }
        if (showCount) {
            ImGui.sameLine();
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(tr("bees.count", rows.size()));
        }
    }

    private void drawRows() {
        int actionIndex = -1;
        EditorUi.ListAction pendingAction = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < rows.size(); index++) {
            OccupantRow row = rows.get(index);
            String type = row.entityType.get().trim();
            String detail = tr(
                "bees.occupant_summary",
                type.isBlank() ? tr("bees.unknown_entity") : type,
                row.ticksInHive.get(),
                row.minTicksInHive.get()
            );

            ImGui.pushID("bee_occupant_" + row.id);
            boolean requestFocus = focusRowId == row.id;
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "bee_occupant",
                EditorUi.identifiedTitle(tr("bees.occupant", index + 1), detail),
                ImGuiTableFlags.SizingStretchProp,
                index,
                rows.size(),
                true,
                true,
                true,
                requestFocus,
                rowDragScope,
                row.id
            );
            if (card.focusApplied()) focusRowId = 0L;
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                pendingAction = card.action();
            }
            if (card.expanded()) {
                drawRow(row, requestFocus);
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                rowDragScope,
                row.id,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        if (reorderRequest != null) applyRowReorder(reorderRequest);
        else if (actionIndex >= 0) applyRowAction(actionIndex, pendingAction);
        if (rows.isEmpty()) EditorUi.drawEmptyState(tr("bees.empty"));
        drawAddAction("bottom", true, false);
    }

    private void drawRow(OccupantRow row, boolean requestFocus) {
        if (beginFields("bee_fields")) {
            fieldLabel(tr("bees.entity_type"), tr("bees.entity_type.help"));
            if (requestFocus && focusEntityDataRowId != row.id) ImGui.setKeyboardFocusHere();
            String problem = entityTypeProblem(row.entityType.get());
            if (host.inputIdentifierWithSuggestions(
                "entity_type",
                new FieldHints(tr("field_purpose.entity_identifier"), DEFAULT_ENTITY_TYPE),
                row.entityType,
                entityTypeSuggestions,
                problem
            )) dirty = true;
            String entityType = row.entityType.get().trim();
            if (problem.isBlank() && !knownEntityTypes.isEmpty() && !knownEntityTypes.contains(entityType)) {
                ItemEditorTheme.drawFieldWarning(tr("bees.entity_type.unavailable", entityType));
            } else if (problem.isBlank() && !isBeehiveInhabitant(entityType)) {
                ItemEditorTheme.drawFieldWarning(tr("bees.entity_type.not_inhabitant", entityType));
            }

            fieldLabel(tr("bees.ticks_in_hive"), tr("bees.ticks_in_hive.help"));
            ImGui.setNextItemWidth(-1.0f);
            if (ImGui.inputInt("##ticks_in_hive", row.ticksInHive, 20, 200)) dirty = true;
            if (row.ticksInHive.get() < 0) ItemEditorTheme.drawFieldWarning(tr("bees.negative_time"));

            fieldLabel(tr("bees.min_ticks_in_hive"), tr("bees.min_ticks_in_hive.help"));
            ImGui.setNextItemWidth(-1.0f);
            if (ImGui.inputInt("##min_ticks_in_hive", row.minTicksInHive, 20, 200)) dirty = true;
            if (row.minTicksInHive.get() < 0) ItemEditorTheme.drawFieldWarning(tr("bees.negative_time"));
            ImGui.endTable();
        }

        ImGui.spacing();
        if (EditorUi.collapsibleHeader(
            "bee_entity_data",
            tr("bees.entity_data"),
            false,
            focusEntityDataRowId == row.id
        )) {
            EditorUi.textDisabledWrapped(tr("bees.entity_data.help"));
            drawEntityData(row);
        }
    }

    private void drawEntityData(OccupantRow row) {
        if (focusEntityDataRowId == row.id) {
            row.editor.requestFocus();
            focusEntityDataRowId = 0L;
        }
        String problem = validateEntityData(row);
        if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
        if (row.editor.draw(
            "bee_entity_snbt",
            row.snbt,
            -1.0f,
            190.0f,
            ImGuiInputTextFlags.AllowTabInput,
            row.errorLine,
            CodeLanguage.SNBT
        )) {
            dirty = true;
            row.validationProblem = "";
        }
        if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
        problem = validateEntityData(row);
        ItemEditorTheme.drawFieldProblem(problem);

        if (ImGui.button(tr("bees.entity_data.format"))) {
            try {
                row.snbt.set(prettySnbt(parseEntityData(row.snbt.get())));
                row.validationProblem = "";
                row.errorLine = 0;
                dirty = true;
            } catch (RuntimeException exception) {
                setEntityDataProblem(row, exception);
            }
        }
        EditorUi.sameLineIfFits(tr("bees.entity_data.reset"));
        if (ImGui.button(tr("bees.entity_data.reset"))) {
            BeesDraft.Occupant normal = BeesDraft.normalBee();
            row.entityType.set(normal.entityType());
            row.ticksInHive.set(normal.ticksInHive());
            row.minTicksInHive.set(normal.minTicksInHive());
            row.snbt.set(prettySnbt(normal.entityData()));
            row.validationProblem = "";
            row.errorLine = 0;
            row.editor.resetCursor();
            dirty = true;
            host.resetAutocomplete();
        }
    }

    private void applyRowAction(int index, EditorUi.ListAction action) {
        EditorListMutations.Mutation<OccupantRow> mutation = EditorListMutations.apply(
            rows,
            index,
            action,
            OccupantRow::copy,
            () -> newRow(BeesDraft.normalBee())
        );
        finishRowMutation(mutation);
    }

    private void applyRowReorder(OrderedListDragDrop.ReorderRequest request) {
        if (!rowDragScope.equals(request.scope())) return;
        finishRowMutation(EditorListMutations.reorder(rows, request, row -> row.id));
    }

    private void finishRowMutation(EditorListMutations.Mutation<OccupantRow> mutation) {
        if (!mutation.changed()) return;
        if (mutation.focusRow() == null) {
            focusRowId = 0L;
            focusAddButton = true;
        } else {
            focusRowId = mutation.focusRow().id;
            focusAddButton = false;
        }
        dirty = true;
        host.resetAutocomplete();
        resetEditorUi();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        BeesDraft pending = buildDraft();
        ItemStack edited = host.draft().copy();
        pending.applyTo(edited);
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(client, state);
        dirty = true;
    }

    @Override
    protected boolean hasPendingChanges() {
        return !buildDraft().matchesStack(host.draft());
    }

    private BeesDraft buildDraft() {
        BeesDraft draft = new BeesDraft();
        draft.setPresent(present);
        if (!present) return draft;
        for (OccupantRow row : rows) {
            String entityType = row.entityType.get().trim();
            String problem = entityTypeProblem(entityType);
            if (!problem.isBlank()) throw new IllegalArgumentException(problem);
            draft.add(new BeesDraft.Occupant(
                entityType,
                parseEntityData(row.snbt.get()),
                row.ticksInHive.get(),
                row.minTicksInHive.get()
            ));
        }
        return draft;
    }

    private String validateEntityData(OccupantRow row) {
        try {
            parseEntityData(row.snbt.get());
            row.validationProblem = "";
            row.errorLine = 0;
        } catch (RuntimeException exception) {
            setEntityDataProblem(row, exception);
        }
        return row.validationProblem;
    }

    private static void setEntityDataProblem(OccupantRow row, RuntimeException exception) {
        row.validationProblem = EditorUi.readableMessage(exception);
        row.errorLine = CodeEditorInput.errorLine(exception, row.snbt.get());
    }

    private static CompoundTag parseEntityData(String source) {
        try {
            CompoundTag tag = TagParser.parseCompoundFully(source);
            if (tag.contains("id")) throw new IllegalArgumentException(tr("bees.entity_data.reserved_id"));
            return tag;
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private static String prettySnbt(Tag tag) {
        return new SnbtPrinterTagVisitor("  ", 0, new ArrayList<>()).visit(tag);
    }

    private static String entityTypeProblem(String source) {
        String value = source == null ? "" : source.trim();
        if (value.isBlank()) return tr("validation.identifier_required");
        Identifier id = Identifier.tryParse(value);
        if (id == null) return tr("validation.identifier_format");
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? "" : tr("validation.identifier_unknown");
    }

    private static boolean isBeehiveInhabitant(String source) {
        if (DEFAULT_ENTITY_TYPE.equals(source)) return true;
        Identifier id = Identifier.tryParse(source);
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(id);
        return type != null
            && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(EntityTypeTags.BEEHIVE_INHABITORS);
    }

    private static OccupantRow newRow(BeesDraft.Occupant occupant) {
        String type = occupant.entityType();
        String snbt = prettySnbt(occupant.entityData());
        return new OccupantRow(
            NEXT_ROW_ID.incrementAndGet(),
            new ImString(type, Math.max(512, type.length() * 2 + 64)),
            new ImInt(occupant.ticksInHive()),
            new ImInt(occupant.minTicksInHive()),
            new ImString(snbt, SNBT_CAPACITY)
        );
    }

    private static final class OccupantRow {
        private final long id;
        private final ImString entityType;
        private final ImInt ticksInHive;
        private final ImInt minTicksInHive;
        private final ImString snbt;
        private final CodeEditorInput editor = new CodeEditorInput();
        private String validationProblem = "";
        private int errorLine;

        private OccupantRow(
            long id,
            ImString entityType,
            ImInt ticksInHive,
            ImInt minTicksInHive,
            ImString snbt
        ) {
            this.id = id;
            this.entityType = entityType;
            this.ticksInHive = ticksInHive;
            this.minTicksInHive = minTicksInHive;
            this.snbt = snbt;
        }

        private OccupantRow copy() {
            return new OccupantRow(
                NEXT_ROW_ID.incrementAndGet(),
                new ImString(entityType.get(), Math.max(512, entityType.get().length() * 2 + 64)),
                new ImInt(ticksInHive.get()),
                new ImInt(minTicksInHive.get()),
                new ImString(snbt.get(), SNBT_CAPACITY)
            );
        }
    }
}
