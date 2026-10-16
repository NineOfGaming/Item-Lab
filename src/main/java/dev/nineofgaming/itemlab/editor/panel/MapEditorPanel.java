package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.control.AutocompleteInput;
import dev.nineofgaming.itemlab.editor.ui.control.ColorSelector;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;

/**
 * Purpose-built map metadata/decorations editor with a native live map preview.
 */
public final class MapEditorPanel extends ComponentEditorPanel {
    private static final AtomicLong NEXT_DECORATION_ROW_ID = new AtomicLong();

    private final AutocompleteInput autocomplete = new AutocompleteInput();
    private final ImInt mapId = new ImInt(0);
    private int mapColor = 0xFFFFFF;
    private final ImBoolean colorPresent = new ImBoolean(false);
    private final ImBoolean decorationsPresent = new ImBoolean(false);
    private final List<DecorationRow> decorations = new ArrayList<>();
    private Set<String> originalDecorationNames = Set.of();
    private List<IdSuggestion> decorationTypes = List.of();
    private boolean mapIdPresent;
    private long focusDecorationRowId;
    private boolean focusAddDecorationButton;

    public MapEditorPanel(ItemEditor host) {
        super(host, "map");
    }

    @Override
    public void requestPendingFocus() {
        focusDecorationRowId = 0L;
        focusAddDecorationButton = false;
        if (decorationsPresent.get()) {
            Set<String> names = new HashSet<>();
            for (DecorationRow row : decorations) {
                String name = row.name.get().trim();
                String field = name.isEmpty() || !names.add(name) ? "name" : "";
                Identifier type = Identifier.tryParse(row.type.get().trim());
                if (field.isEmpty() && (type == null || !decorationTypes.isEmpty()
                    && decorationTypes.stream().noneMatch(suggestion -> suggestion.id().equals(type.toString()))))
                    field = "type";
                if (field.isEmpty() && !Double.isFinite(row.x.get())) field = "x";
                if (field.isEmpty() && !Double.isFinite(row.z.get())) field = "z";
                if (field.isEmpty() && !Float.isFinite((float) row.rotation.get())) field = "rotation";
                if (!field.isEmpty()) {
                    focusDecorationRowId = row.rowId;
                    requestPendingField("map_decorations", "row/" + row.rowId + "/" + field);
                    return;
                }
            }
            requestPendingField("map_decorations", "$enabled");
        } else {
            requestPendingField(mapIdPresent ? "map_id" : "map_color", "$enabled");
        }
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.MAP_ID
            || type == DataComponents.MAP_DECORATIONS
            || type == DataComponents.MAP_COLOR;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.has(DataComponents.MAP_ID)
            || stack.has(DataComponents.MAP_DECORATIONS)
            || stack.has(DataComponents.MAP_COLOR);
    }

    public void setCatalog(EditorCatalog catalog) {
        decorationTypes = catalog.registryIds("map_decoration_type");
    }

    public void sync(ItemStack stack) {
        MapId id = stack.get(DataComponents.MAP_ID);
        mapIdPresent = id != null;
        mapId.set(id == null ? 0 : id.id());

        MapItemColor color = stack.get(DataComponents.MAP_COLOR);
        colorPresent.set(color != null);
        mapColor = (color == null ? MapItemColor.DEFAULT.rgb() : color.rgb()) & 0xFFFFFF;

        decorations.clear();
        MapDecorations installedDecorations = stack.get(DataComponents.MAP_DECORATIONS);
        decorationsPresent.set(installedDecorations != null);
        originalDecorationNames = installedDecorations == null
            ? Set.of()
            : Set.copyOf(installedDecorations.decorations().keySet());
        if (installedDecorations != null) {
            installedDecorations.decorations().forEach((name, entry) -> decorations.add(new DecorationRow(
                name,
                entry.type().unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:player"),
                entry.x(),
                entry.z(),
                entry.rotation()
            )));
        }

        focusDecorationRowId = 0L;
        focusAddDecorationButton = false;
        resetEditorUi();
        dirty = false;
        autocomplete.reset();
    }

    public void syncComponent(DataComponentType<?> type, ItemStack stack) {
        sync(stack);
    }

    public void draw(Minecraft client) {
        drawEditor(client, null);
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        drawEditor(client, type);
    }

    private void drawEditor(Minecraft client, DataComponentType<?> componentType) {
        if (drawPanelToolbar(
            () -> save(client, true),
            () -> sync(host.draft())
        )) return;

        EditorUi.beginScrollableChild("map_editor", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            ItemStack preview = previewStack(client);
            host.setItemIconPreview(preview);
            EditorUi.drawPreviewFormSplit(
                "map_workspace",
                310.0f,
                () -> drawPreview(client, preview),
                () -> drawFields(client, componentType)
            );
        } finally {
            ImGui.endChild();
        }
    }

    private void drawPreview(Minecraft client, ItemStack preview) {
        ImGui.text(tr("map.preview"));
        host.drawMapPreview(preview, originalDecorationNames);
        if (!host.hasMapPreviewData(client, preview)) {
            EditorUi.textDisabledWrapped(tr("map.preview.no_data", mapId.get()));
        }
    }

    private void drawFields(Minecraft client, DataComponentType<?> componentType) {
        if (componentType == null || componentType == DataComponents.MAP_ID) drawMapId();
        if (componentType == null || componentType == DataComponents.MAP_COLOR) drawMapColor();
        if (componentType == null || componentType == DataComponents.MAP_DECORATIONS) drawDecorations(client);
    }

    private void drawMapId() {
        if (!host.beginComponentCard(
            "map_id",
            tr("map.id"),
            DataComponents.MAP_ID,
            hasPendingSectionFocus("map_id")
        )) return;
        ImBoolean enabled = new ImBoolean(mapIdPresent);
        applyPendingFieldFocus("map_id", "$enabled");
        if (ImGui.checkbox(tr("map.id.enabled"), enabled)) {
            mapIdPresent = enabled.get();
            dirty = true;
        }
        ImGui.beginDisabled(!mapIdPresent);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.inputInt("##map_id_value", mapId, 1, 10)) {
            mapId.set(Math.max(0, mapId.get()));
            dirty = true;
        }
        ImGui.endDisabled();
        EditorUi.endCard();
        ImGui.spacing();
    }

    private void drawMapColor() {
        if (!host.beginComponentCard(
            "map_color",
            tr("map.color"),
            DataComponents.MAP_COLOR,
            hasPendingSectionFocus("map_color")
        )) return;
        applyPendingFieldFocus("map_color", "$enabled");
        if (ImGui.checkbox(tr("map.color.override"), colorPresent)) dirty = true;
        ImGui.beginDisabled(!colorPresent.get());
        int changed = ColorSelector.drawRgb("map_color", mapColor, false);
        if (changed != mapColor) {
            mapColor = changed;
            dirty = true;
        }
        ImGui.endDisabled();
        EditorUi.endCard();
        ImGui.spacing();
    }

    private void drawDecorations(Minecraft client) {
        if (!host.beginComponentCard(
            "map_decorations",
            tr("map.decorations"),
            DataComponents.MAP_DECORATIONS,
            hasPendingSectionFocus("map_decorations")
        )) return;
        applyPendingFieldFocus("map_decorations", "$enabled");
        if (ImGui.checkbox(tr("map.decorations.enabled"), decorationsPresent)) dirty = true;
        ImGui.beginDisabled(!decorationsPresent.get());
        drawAddDecorationAction("top", false);
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        for (int index = 0; index < decorations.size(); index++) {
            DecorationRow row = decorations.get(index);
            ImGui.pushID("map_decoration_" + row.rowId);
            ImGui.spacing();
            EditorUi.ListAction rowAction = drawDecoration(client, index, row);
            if (rowAction != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = rowAction;
            }
            ImGui.popID();
        }
        EditorListMutations.Mutation<DecorationRow> mutation = EditorListMutations.apply(
            decorations,
            actionIndex,
            action,
            null,
            this::createDecorationRow
        );
        if (mutation.changed()) {
            if (mutation.focusRow() == null) {
                focusDecorationRowId = 0L;
                focusAddDecorationButton = true;
            } else {
                focusDecorationRowId = mutation.focusRow().rowId;
                focusAddDecorationButton = false;
            }
            autocomplete.reset();
            dirty = true;
        }
        if (decorations.isEmpty()) EditorUi.drawEmptyState(tr("map.decorations.empty"));
        drawAddDecorationAction("bottom", true);
        ImGui.endDisabled();
        EditorUi.endCard();
    }

    private void drawAddDecorationAction(String placement, boolean acceptFocus) {
        if (acceptFocus && focusAddDecorationButton) {
            ImGui.setKeyboardFocusHere();
            focusAddDecorationButton = false;
        }
        if (EditorIcons.button(
            "add_map_decoration_" + placement,
            EditorIcons.Icon.ADD,
            tr("map.decorations.add")
        )) {
            DecorationRow added = createDecorationRow();
            decorations.add(added);
            focusDecorationRowId = added.rowId;
            focusAddDecorationButton = false;
            autocomplete.reset();
            dirty = true;
        }
    }

    private DecorationRow createDecorationRow() {
        return new DecorationRow(
            "",
            decorationTypes.isEmpty() ? "minecraft:player" : decorationTypes.getFirst().id(),
            0.0,
            0.0,
            0.0
        );
    }

    private EditorUi.ListAction drawDecoration(Minecraft client, int index, DecorationRow row) {
        boolean requestFocus = focusDecorationRowId == row.rowId;
        EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
            "map_decoration_row",
            EditorUi.identifiedTitle(
                tr("map.decorations.entry", index + 1),
                row.name.get().trim() + " | " + row.type.get().trim()
            ),
            ImGuiTableFlags.SizingStretchProp,
            index,
            decorations.size(),
            false,
            false,
            true,
            requestFocus
        );
        if (card.focusApplied()) focusDecorationRowId = 0L;
        if (card.expanded()) {
            ImGui.text(tr("map.decorations.name"));
            String nameProblem = row.name.get().isBlank() ? tr("validation.key_required")
                : duplicateName(row.name.get(), index) ? tr("validation.key_duplicate") : "";
            if (!nameProblem.isBlank()) ItemEditorTheme.pushInvalidField();
            applyPendingFieldFocus("map_decorations", "row/" + row.rowId + "/name");
            ImGui.setNextItemWidth(-1.0f);
            if (inputTextWithHint(
                "##decoration_name",
                new FieldHints(tr("field_purpose.decoration_name"), "home"),
                row.name
            )) {
                dirty = true;
            }
            if (!nameProblem.isBlank()) ItemEditorTheme.popInvalidField();
            ItemEditorTheme.drawFieldProblem(nameProblem);

            ImGui.text(tr("map.decorations.type"));
            Identifier id = Identifier.tryParse(row.type.get().trim());
            Registry<MapDecorationType> registry = Objects.requireNonNull(client.level).registryAccess()
                .lookupOrThrow(Registries.MAP_DECORATION_TYPE);
            String typeProblem = id == null ? tr("validation.identifier_format")
                : !registry.containsKey(id) ? tr("validation.identifier_unknown") : "";
            applyPendingFieldFocus("map_decorations", "row/" + row.rowId + "/type");
            if (autocomplete.input(
                "map_decoration_type",
                new FieldHints(
                    tr("field_purpose.map_decoration_identifier"),
                    "minecraft:player"
                ),
                row.type,
                decorationTypes,
                typeProblem
            )) dirty = true;

            if (ImGui.beginTable(
                "map_decoration_coordinates",
                3,
                ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoPadOuterX
            )) {
                EditorUi.alignedTableFields(
                    new String[]{
                        tr("map.decorations.x"),
                        tr("map.decorations.z"),
                        tr("map.decorations.rotation")
                    },
                    () -> {
                        applyPendingFieldFocus("map_decorations", "row/" + row.rowId + "/x");
                        ImGui.setNextItemWidth(-1.0f);
                        if (ImGui.inputDouble("##decoration_x", row.x, 1.0, 10.0, "%.2f")) dirty = true;
                    },
                    () -> {
                        applyPendingFieldFocus("map_decorations", "row/" + row.rowId + "/z");
                        ImGui.setNextItemWidth(-1.0f);
                        if (ImGui.inputDouble("##decoration_z", row.z, 1.0, 10.0, "%.2f")) dirty = true;
                    },
                    () -> {
                        applyPendingFieldFocus("map_decorations", "row/" + row.rowId + "/rotation");
                        ImGui.setNextItemWidth(-1.0f);
                        if (ImGui.inputDouble("##decoration_rotation", row.rotation, 1.0, 15.0, "%.1f")) dirty = true;
                    }
                );
                ImGui.endTable();
            }
            EditorUi.endCard();
        }
        return card.action();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, this::sync);
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
        if (mapIdPresent) edited.set(DataComponents.MAP_ID, new MapId(Math.max(0, mapId.get())));
        else edited.remove(DataComponents.MAP_ID);

        if (colorPresent.get()) {
            edited.set(DataComponents.MAP_COLOR, new MapItemColor(mapColor));
        } else {
            edited.remove(DataComponents.MAP_COLOR);
        }

        if (decorationsPresent.get()) {
            Registry<MapDecorationType> registry = Objects.requireNonNull(client.level).registryAccess()
                .lookupOrThrow(Registries.MAP_DECORATION_TYPE);
            Map<String, MapDecorations.Entry> values = new LinkedHashMap<>();
            for (DecorationRow row : decorations) {
                String name = row.name.get().trim();
                if (name.isEmpty()) throw new IllegalArgumentException(tr("validation.key_required"));
                if (values.containsKey(name)) throw new IllegalArgumentException(tr("validation.key_duplicate"));
                Identifier id = Identifier.tryParse(row.type.get().trim());
                if (id == null) throw new IllegalArgumentException(tr("validation.identifier_format"));
                Holder.Reference<MapDecorationType> type = registry.get(id)
                    .orElseThrow(() -> new IllegalArgumentException(
                        tr("map.validation.decoration_type_unknown", id)
                    ));
                if (!Double.isFinite(row.x.get()) || !Double.isFinite(row.z.get())
                    || !Float.isFinite((float) row.rotation.get())) {
                    throw new IllegalArgumentException(tr("validation.number_finite"));
                }
                values.put(name, new MapDecorations.Entry(
                    type,
                    row.x.get(),
                    row.z.get(),
                    (float) row.rotation.get()
                ));
            }
            edited.set(DataComponents.MAP_DECORATIONS, new MapDecorations(Map.copyOf(values)));
        } else {
            edited.remove(DataComponents.MAP_DECORATIONS);
        }
        return edited;
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack(client);
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(state);
        dirty = true;
    }

    @Override
    protected boolean hasPendingChanges() {
        return !ItemStack.matches(host.draft(), buildEditedStack(Minecraft.getInstance()));
    }

    private boolean duplicateName(String name, int exceptIndex) {
        for (int index = 0; index < decorations.size(); index++) {
            if (index != exceptIndex && decorations.get(index).name.get().trim().equals(name.trim())) return true;
        }
        return false;
    }

    private static final class DecorationRow {
        private final long rowId = NEXT_DECORATION_ROW_ID.incrementAndGet();
        private final ImString name;
        private final ImString type;
        private final ImDouble x;
        private final ImDouble z;
        private final ImDouble rotation;

        private DecorationRow(String name, String type, double x, double z, double rotation) {
            this.name = new ImString(name, 512);
            this.type = new ImString(type, 512);
            this.x = new ImDouble(x);
            this.z = new ImDouble(z);
            this.rotation = new ImDouble(rotation);
        }
    }
}
