package dev.nineofgaming.itemlab.editor.diamondfire;

import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputText;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;

/**
 * Focused editor for DiamondFire's hypercube:* PublicBukkitValues item tags.
 */
public final class DiamondFireTagEditorPanel {
    private static final String PUBLIC_BUKKIT_VALUES = "PublicBukkitValues";
    private static final String HYPERCUBE_PREFIX = "hypercube:";
    private static final int TYPE_AUTOMATIC = 0;
    private static final int TYPE_STRING = 1;
    private static final int TYPE_NUMBER = 2;
    private static final int TYPE_OTHER = 3;
    private static final float MIN_TYPE_COLUMN_WIDTH = 128.0f;
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong();

    private final ItemEditor host;
    private final List<TagRow> rows = new ArrayList<>();
    private long focusRowId;
    private long focusValueRowId;
    private boolean focusAddButton;
    private boolean dirty;

    public DiamondFireTagEditorPanel(ItemEditor host) {
        this.host = host;
    }

    public void requestPendingFocus() {
        focusRowId = 0L;
        focusValueRowId = 0L;
        focusAddButton = rows.isEmpty();
        for (int index = 0; index < rows.size(); index++) {
            TagRow row = rows.get(index);
            if (!tagKeyProblem(index, row).isBlank()) {
                focusRowId = row.rowId;
                return;
            }
            if (!numberProblem(row).isBlank()) {
                focusValueRowId = row.rowId;
                return;
            }
        }
        if (!rows.isEmpty()) focusRowId = rows.getFirst().rowId;
    }

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

    public void draw(Minecraft client, String serverHost, boolean customDataPending) {
        if (!DiamondFireFeatures.available()) return;
        ImGui.spacing();
        EditorUi.textDisabledWrapped(serverHost == null || serverHost.isBlank()
            ? tr("diamondfire.no_detected_server")
            : tr("diamondfire.server", serverHost));
        ImGui.spacing();

        if (customDataPending) {
            EditorUi.textDisabledWrapped(tr("diamondfire.custom_data_pending"));
            return;
        }

        EditorUi.drawSaveDiscardActions(isDirty(), () -> save(client, true), () -> {
            sync(host.draft());
            host.setStatus(true, tr("message.discarded"));
        });
        EditorUi.sameLineIfFits(EditorIcons.buttonSize());
        drawAddAction("top", false);
        EditorUi.beginScrollableChild("diamondfire_tag_rows", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            int actionIndex = -1;
            EditorUi.ListAction action = EditorUi.ListAction.NONE;
            String[] allValueTypes = valueTypes();
            float typeColumnWidth = EditorUi.stableComboWidth(allValueTypes, MIN_TYPE_COLUMN_WIDTH);
            if (ImGui.beginTable(
                "diamondfire_tags",
                4,
                ImGuiTableFlags.SizingStretchProp
                    | ImGuiTableFlags.RowBg
                    | ImGuiTableFlags.BordersInnerH
                    | ImGuiTableFlags.BordersInnerV
                    | ImGuiTableFlags.PadOuterX
            )) {
                ImGui.tableSetupColumn(tr("diamondfire.tag_key"), ImGuiTableColumnFlags.WidthStretch, 0.9f);
                ImGui.tableSetupColumn(
                    tr("diamondfire.value_type"),
                    ImGuiTableColumnFlags.WidthFixed | ImGuiTableColumnFlags.NoResize,
                    typeColumnWidth
                );
                ImGui.tableSetupColumn(tr("diamondfire.value"), ImGuiTableColumnFlags.WidthStretch, 1.2f);
                ImGui.tableSetupColumn("##actions", ImGuiTableColumnFlags.WidthFixed, EditorIcons.smallButtonSize());
                ImGui.tableHeadersRow();

                for (int index = 0; index < rows.size(); index++) {
                    TagRow row = rows.get(index);
                    ImGui.pushID("df_tag_" + row.rowId);
                    ImGui.tableNextRow(0, EditorIcons.smallButtonSize());
                    EditorUi.ListAction rowAction = drawRow(index, row);
                    if (rowAction != EditorUi.ListAction.NONE) {
                        actionIndex = index;
                        action = rowAction;
                    }
                    ImGui.popID();
                }
                ImGui.endTable();
            }
            if (actionIndex >= 0) {
                EditorListMutations.Mutation<TagRow> mutation = EditorListMutations.apply(
                    rows,
                    actionIndex,
                    action,
                    this::duplicateRow
                );
                if (mutation.changed()) {
                    if (mutation.focusRow() == null) {
                        focusRowId = 0L;
                        focusAddButton = true;
                    } else {
                        focusRowId = mutation.focusRow().rowId;
                        focusAddButton = false;
                    }
                    dirty = true;
                }
            }
            if (rows.isEmpty()) EditorUi.drawEmptyState(tr("diamondfire.empty"));
            drawAddAction("bottom", true);
        } finally {
            ImGui.endChild();
        }
    }

    public void sync(ItemStack draft) {
        focusValueRowId = 0L;
        rows.clear();
        CustomData customData = draft.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        customData.copyTag().getCompound(PUBLIC_BUKKIT_VALUES).ifPresent(publicValues -> publicValues.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(HYPERCUBE_PREFIX))
            .sorted(Comparator.comparing(entry -> entry.getKey().toLowerCase(Locale.ROOT)))
            .forEach(entry -> rows.add(TagRow.from(
                entry.getKey().substring(HYPERCUBE_PREFIX.length()),
                entry.getValue()
            ))));
        focusRowId = 0L;
        focusAddButton = false;
        dirty = false;
    }

    public boolean save(Minecraft client, boolean announce) {
        try {
            ItemStack edited = buildEditedStack();
            ItemStackCodecs.validateStack(edited);
            host.acceptCustomDataDraft(client, edited);
            if (announce) {
                host.setStatus(true, tr("message.saved"));
            }
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    private ItemStack buildEditedStack() {
        ItemStack edited = host.draft().copy();
        CustomData customData = edited.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customTag = customData.copyTag();
        Tag originalPublicValues = customTag.get(PUBLIC_BUKKIT_VALUES);
        if (originalPublicValues != null && !(originalPublicValues instanceof CompoundTag)) {
            if (rows.isEmpty()) return edited;
            throw new IllegalArgumentException(tr("diamondfire.validation.invalid_public_values"));
        }
        CompoundTag publicValues = customTag.getCompound(PUBLIC_BUKKIT_VALUES)
            .map(CompoundTag::copy)
            .orElseGet(CompoundTag::new);

        List<String> existingHypercubeKeys = publicValues.keySet().stream()
            .filter(key -> key.startsWith(HYPERCUBE_PREFIX))
            .toList();
        if (rows.isEmpty() && existingHypercubeKeys.isEmpty()) return edited;
        existingHypercubeKeys.forEach(publicValues::remove);

        Set<String> usedKeys = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            TagRow row = rows.get(index);
            String key = row.effectiveKey();
            if (key.isEmpty() && !row.hasUnchangedKey()) {
                throw new IllegalArgumentException(tr("diamondfire.validation.tag_key_required", index + 1));
            }
            if (!usedKeys.add(key)) {
                throw new IllegalArgumentException(tr("diamondfire.validation.duplicate_key", key));
            }
            publicValues.put(HYPERCUBE_PREFIX + key, row.toTag(index));
        }

        if (publicValues.isEmpty()) {
            customTag.remove(PUBLIC_BUKKIT_VALUES);
        } else {
            customTag.put(PUBLIC_BUKKIT_VALUES, publicValues);
        }
        if (customTag.isEmpty()) {
            edited.remove(DataComponents.CUSTOM_DATA);
        } else {
            edited.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
        }
        return edited;
    }

    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack();
    }

    public void restorePending(Minecraft client, ItemStack state) {
        sync(state);
        dirty = true;
    }

    public HistoryState historyState() {
        return new HistoryState(rows.stream().map(TagRow::historyState).toList());
    }

    public void restoreHistoryState(HistoryState state) {
        focusValueRowId = 0L;
        rows.clear();
        state.rows.forEach(row -> rows.add(TagRow.fromHistory(row)));
        focusRowId = 0L;
        focusAddButton = false;
        dirty = true;
    }

    private EditorUi.ListAction drawRow(int index, TagRow row) {
        ImGui.tableNextColumn();
        alignRowControl(ImGui.getFrameHeight());
        String keyProblem = tagKeyProblem(index, row);
        if (!keyProblem.isBlank()) ItemEditorTheme.pushInvalidField();
        ImGui.setNextItemWidth(-1.0f);
        boolean requestFocus = focusRowId == row.rowId;
        if (requestFocus) ImGui.setKeyboardFocusHere();
        if (inputTextWithHint("##tag_key", tr("diamondfire.tag_key_hint"), row.key)) {
            dirty = true;
        }
        if (requestFocus) focusRowId = 0L;
        if (!keyProblem.isBlank()) ItemEditorTheme.popInvalidField();
        if (ImGui.isItemHovered()) {
            EditorUi.setTooltip(keyProblem.isBlank() ? tr("diamondfire.prefix_tooltip") : keyProblem);
        }

        ImGui.tableNextColumn();
        alignRowControl(ImGui.getFrameHeight());
        ImGui.setNextItemWidth(-1.0f);
        String[] availableTypes = row.originalType == TYPE_OTHER ? valueTypes() : editableValueTypes();
        if (ImGui.combo("##tag_type", row.type, availableTypes)) {
            row.valueChanged = true;
            dirty = true;
        }
        if (row.type.get() == TYPE_AUTOMATIC && ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("diamondfire.automatic_tooltip"));
        }

        ImGui.tableNextColumn();
        alignRowControl(ImGui.getFrameHeight());
        ImGui.setNextItemWidth(-1.0f);
        if (row.type.get() == TYPE_OTHER) {
            ImGui.beginDisabled();
            inputText("##tag_value", row.value);
            ImGui.endDisabled();
            if (ImGui.isItemHovered()) {
                EditorUi.setTooltip(tr("diamondfire.preserved_nbt"));
            }
        } else {
            String valueProblem = numberProblem(row);
            if (focusValueRowId == row.rowId) {
                ImGui.setScrollHereY(0.25f);
                ImGui.setKeyboardFocusHere();
                focusValueRowId = 0L;
            }
            if (!valueProblem.isBlank()) ItemEditorTheme.pushInvalidField();
            FieldHints hints = switch (row.type.get()) {
                case TYPE_AUTOMATIC -> FieldHints.purpose(tr("diamondfire.automatic_hint"));
                case TYPE_NUMBER -> new FieldHints(tr("diamondfire.number_hint"), "1.0");
                default -> FieldHints.purpose(tr("diamondfire.value_hint"));
            };
            if (inputTextWithHint("##tag_value", hints.purposeHint(), row.value)) {
                row.valueChanged = true;
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
        alignRowControl(EditorIcons.smallButtonSize());
        return EditorUi.drawCompactListActions(
            "diamondfire_tag",
            index,
            rows.size(),
            false,
            true,
            false
        );
    }

    private void drawAddAction(String placement, boolean acceptFocus) {
        if (acceptFocus && focusAddButton) {
            ImGui.setKeyboardFocusHere();
            focusAddButton = false;
        }
        if (EditorIcons.button("add_df_tag_" + placement, EditorIcons.Icon.ADD, tr("diamondfire.add"))) {
            TagRow added = TagRow.newAutomatic();
            rows.add(added);
            focusRowId = added.rowId;
            focusAddButton = false;
            dirty = true;
        }
    }

    private TagRow duplicateRow(TagRow source) {
        TagRow duplicate = source.deepCopy();
        duplicate.key.set(uniqueTagKey(normalizedKey(source.key.get()) + "_copy"));
        return duplicate;
    }

    private String uniqueTagKey(String requested) {
        String base = requested == null || requested.isBlank() ? "tag" : requested;
        Set<String> keys = new HashSet<>();
        rows.forEach(row -> keys.add(row.effectiveKey()));
        if (!keys.contains(base)) return base;
        for (int suffix = 2; ; suffix++) {
            String candidate = base + "_" + suffix;
            if (!keys.contains(candidate)) return candidate;
        }
    }

    private static void alignRowControl(float controlHeight) {
        float offset = Math.max(0.0f, (EditorIcons.smallButtonSize() - controlHeight) * 0.5f);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + offset);
    }

    private static String normalizedKey(String value) {
        String key = value.trim();
        return key.startsWith(HYPERCUBE_PREFIX) ? key.substring(HYPERCUBE_PREFIX.length()).trim() : key;
    }

    private static String[] editableValueTypes() {
        return new String[]{
            tr("value_type.automatic"),
            tr("value_type.string"),
            tr("value_type.number")
        };
    }

    private static String[] valueTypes() {
        return new String[]{
            tr("value_type.automatic"),
            tr("value_type.string"),
            tr("value_type.number"),
            tr("value_type.other_nbt")
        };
    }

    private String tagKeyProblem(int rowIndex, TagRow row) {
        String key = row.effectiveKey();
        if (key.isEmpty() && !row.hasUnchangedKey()) return tr("validation.key_required");
        for (int index = 0; index < rows.size(); index++) {
            if (index != rowIndex && rows.get(index).effectiveKey().equals(key)) {
                return tr("validation.key_duplicate");
            }
        }
        return "";
    }

    private static String numberProblem(TagRow row) {
        if (row.type.get() != TYPE_NUMBER || row.hasUnchangedValue()) return "";
        try {
            double value = Double.parseDouble(row.value.get().trim());
            return Double.isFinite(value) ? "" : tr("validation.number_finite");
        } catch (NumberFormatException exception) {
            return tr("validation.number");
        }
    }

    private static final class TagRow {
        private final long rowId = NEXT_ROW_ID.incrementAndGet();
        private final ImString key;
        private final ImInt type;
        private final ImString value;
        private final Tag originalValue;
        private final int originalType;
        private String originalKey;
        private String originalKeyInput;
        private boolean valueChanged;

        private TagRow(String key, int type, String value, Tag originalValue) {
            this.key = new ImString(key, 512);
            this.type = new ImInt(type);
            this.value = new ImString(value, 8_192);
            this.originalValue = originalValue == null ? null : originalValue.copy();
            this.originalType = type;
            this.originalKey = originalValue == null ? null : key;
            this.originalKeyInput = this.key.get();
        }

        private boolean hasUnchangedKey() {
            return originalKey != null && key.get().equals(originalKeyInput);
        }

        private String effectiveKey() {
            // Unknown names are owned by the source NBT, not by our input normalization.
            return hasUnchangedKey() ? originalKey : normalizedKey(key.get());
        }

        private boolean hasUnchangedValue() {
            return !valueChanged && type.get() == originalType && originalValue != null;
        }

        private static TagRow newAutomatic() {
            return new TagRow("", TYPE_AUTOMATIC, "", null);
        }

        private static TagRow from(String key, Tag value) {
            if (value instanceof StringTag(String value1)) {
                return new TagRow(key, TYPE_STRING, value1, value);
            }
            if (value instanceof NumericTag number) {
                return new TagRow(key, TYPE_NUMBER, Double.toString(number.doubleValue()), value);
            }
            return new TagRow(key, TYPE_OTHER, value.toString(), value);
        }

        private TagRow deepCopy() {
            TagRow copy = new TagRow(key.get(), originalType, value.get(), originalValue);
            copy.type.set(type.get());
            copy.valueChanged = valueChanged;
            copy.originalKey = originalKey;
            copy.originalKeyInput = originalKeyInput;
            return copy;
        }

        private RowState historyState() {
            return new RowState(
                key.get(),
                type.get(),
                value.get(),
                originalValue == null ? null : originalValue.copy(),
                originalType,
                valueChanged,
                originalKey,
                originalKeyInput
            );
        }

        private static TagRow fromHistory(RowState state) {
            TagRow row = new TagRow(
                state.key(),
                state.originalType(),
                state.value(),
                state.originalValue()
            );
            row.type.set(state.type());
            row.valueChanged = state.valueChanged();
            row.originalKey = state.originalKey();
            row.originalKeyInput = state.originalKeyInput();
            return row;
        }

        private Tag toTag(int index) {
            int selectedType = type.get();
            if (hasUnchangedValue()) {
                return originalValue.copy();
            }
            return switch (selectedType) {
                case TYPE_AUTOMATIC -> inferredTag(value.get());
                case TYPE_STRING -> StringTag.valueOf(value.get());
                case TYPE_NUMBER -> {
                    try {
                        double number = Double.parseDouble(value.get().trim());
                        if (!Double.isFinite(number)) {
                            throw new NumberFormatException(tr("validation.number_finite"));
                        }
                        yield DoubleTag.valueOf(number);
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException(tr(
                            "diamondfire.validation.invalid_number",
                            index + 1,
                            value.get()
                        ));
                    }
                }
                case TYPE_OTHER -> {
                    if (originalValue == null) {
                        throw new IllegalArgumentException(tr("diamondfire.validation.missing_nbt", index + 1));
                    }
                    yield originalValue.copy();
                }
                default -> throw new IllegalArgumentException(tr("diamondfire.validation.unknown_type", index + 1));
            };
        }

        private static Tag inferredTag(String value) {
            try {
                double number = Double.parseDouble(value.trim());
                if (Double.isFinite(number)) {
                    return DoubleTag.valueOf(number);
                }
            } catch (NumberFormatException ignored) {
                // Values that are not finite numbers remain strings exactly as entered.
            }
            return StringTag.valueOf(value);
        }
    }

    private record RowState(
        String key,
        int type,
        String value,
        Tag originalValue,
        int originalType,
        boolean valueChanged,
        String originalKey,
        String originalKeyInput
    ) {
    }

    public static final class HistoryState {
        private final List<RowState> rows;

        private HistoryState(List<RowState> rows) {
            this.rows = List.copyOf(rows);
        }

        public boolean sameState(HistoryState other) {
            return other != null && rows.equals(other.rows);
        }
    }
}
