package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.preview.ItemModelPreviewMode;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.array;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.bool;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.integer;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.object;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Purpose-built compass editor for lodestone targets and tracking behavior.
 */
public final class CompassEditorPanel extends ComponentEditorPanel {
    private final CodecComponentState tracker = new CodecComponentState(
        DataComponents.LODESTONE_TRACKER,
        "{\"tracked\":true}"
    );
    private List<IdSuggestion> catalogDimensionIds = List.of();
    private List<IdSuggestion> dimensionIds = List.of();
    private boolean focusDimensionInput;

    public CompassEditorPanel(ItemEditor host) {
        super(host, "compass");
    }

    @Override
    public void requestPendingFocus() {
        focusDimensionInput = false;
        JsonElement value = tracker.value();
        if (tracker.present() && value.isJsonObject()) {
            JsonElement target = value.getAsJsonObject().get("target");
            if (target != null && target.isJsonObject()
                && !dimensionProblem(string(target.getAsJsonObject(), "dimension", "")).isBlank()) {
                requestPendingField("tracker", "dimension");
                return;
            }
        }
        requestPendingField("tracker", "$enabled");
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.LODESTONE_TRACKER;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.getItem() == Items.COMPASS || stack.has(DataComponents.LODESTONE_TRACKER);
    }

    public void setCatalog(EditorCatalog catalog) {
        catalogDimensionIds = catalog.registryIds("dimension");
    }

    public void sync(Minecraft client, ItemStack stack) {
        tracker.sync(client, stack);
        dimensionIds = dimensionSuggestions(client);
        focusDimensionInput = false;
        resetEditorUi();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (supports(type)) sync(client, stack);
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
            () -> sync(client, host.draft())
        )) return;

        EditorUi.beginScrollableChild(
            "compass_editor",
            0.0f,
            Math.max(1.0f, ImGui.getContentRegionAvailY()),
            false
        );
        try {
            ItemStack preview = previewStack(client);
            host.setItemIconPreview(preview);
            host.drawTooltipPreview(client, preview, "compass");
            ImGui.spacing();
            drawModelPreview(client, preview);
            ImGui.spacing();
            drawTracker(client);
        } finally {
            ImGui.endChild();
        }
    }

    private void drawModelPreview(Minecraft client, ItemStack preview) {
        if (!EditorUi.beginCollapsibleCard(
            "compass_preview",
            tr("compass.preview"),
            ImGuiTableFlags.SizingStretchProp
        )) return;
        host.drawItemModelPreview(client, preview, ItemModelPreviewMode.GUI);
        EditorUi.endCard();
    }

    private void drawTracker(Minecraft client) {
        if (!beginSection("tracker", tracker)) return;
        JsonObject value = tracker.object();
        boolean targetPresent = value.has("target") && value.get("target").isJsonObject();
        if (beginFields("lodestone_tracker_fields")) {
            if (tracker.supportsField("target")) {
                fieldLabel(tr("compass.target"), tr("compass.target.help"));
                ImBoolean enabled = new ImBoolean(targetPresent);
                if (ImGui.checkbox("##lodestone_target", enabled)) {
                    if (enabled.get()) value.add("target", currentTarget(client));
                    else value.remove("target");
                    targetPresent = enabled.get();
                    focusDimensionInput = targetPresent;
                    resetEditorUi();
                    dirty = true;
                }
            }

            if (targetPresent) {
                JsonObject target = object(value, "target");
                fieldLabel(tr("compass.dimension"), tr("compass.dimension.help"));
                ImString dimension = buffer(
                    "lodestone/dimension",
                    string(target, "dimension", currentDimension(client))
                );
                applyPendingFieldFocus("tracker", "dimension");
                if (focusDimensionInput) {
                    ImGui.setKeyboardFocusHere();
                    focusDimensionInput = false;
                }
                if (host.inputIdentifierWithSuggestions(
                    "lodestone_dimension",
                    new FieldHints(
                        tr("field_purpose.dimension_identifier"),
                        "minecraft:overworld"
                    ),
                    dimension,
                    dimensionIds,
                    dimensionProblem(dimension.get())
                )) {
                    target.addProperty("dimension", dimension.get().trim());
                    dirty = true;
                }

                fieldLabel(tr("compass.position"), tr("compass.position.help"));
                drawPosition(target);
            }

            if (tracker.supportsField("tracked")) {
                fieldLabel(tr("compass.tracked"), tr("compass.tracked.help"));
                ImBoolean tracked = new ImBoolean(bool(value, "tracked", true));
                if (ImGui.checkbox("##lodestone_tracked", tracked)) {
                    value.addProperty("tracked", tracked.get());
                    dirty = true;
                }
            }
            ImGui.endTable();
        }

        ImGui.beginDisabled(!targetPresent || client.player == null || client.level == null);
        if (ImGui.button(tr("compass.current_position"))) {
            JsonObject target = currentTarget(client);
            value.add("target", target);
            ImString dimension = buffer("lodestone/dimension", currentDimension(client));
            dimension.set(currentDimension(client));
            dirty = true;
        }
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) EditorUi.setTooltip(tr("compass.current_position.help"));
        endSection();
    }

    private void drawPosition(JsonObject target) {
        JsonArray position = array(target, "pos");
        ImInt x = new ImInt(coordinate(position, 0));
        ImInt y = new ImInt(coordinate(position, 1));
        ImInt z = new ImInt(coordinate(position, 2));
        if (ImGui.beginTable(
            "lodestone_coordinates",
            3,
            ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoPadOuterX
        )) {
            EditorUi.alignedTableFields(
                new String[]{"X", "Y", "Z"},
                () -> {
                    ImGui.setNextItemWidth(-1.0f);
                    if (ImGui.inputInt("##lodestone_x", x, 1, 10)) {
                        setPosition(target, x.get(), y.get(), z.get());
                        dirty = true;
                    }
                },
                () -> {
                    ImGui.setNextItemWidth(-1.0f);
                    if (ImGui.inputInt("##lodestone_y", y, 1, 10)) {
                        setPosition(target, x.get(), y.get(), z.get());
                        dirty = true;
                    }
                },
                () -> {
                    ImGui.setNextItemWidth(-1.0f);
                    if (ImGui.inputInt("##lodestone_z", z, 1, 10)) {
                        setPosition(target, x.get(), y.get(), z.get());
                        dirty = true;
                    }
                }
            );
            ImGui.endTable();
        }
    }

    private JsonObject currentTarget(Minecraft client) {
        JsonObject target = new JsonObject();
        target.addProperty("dimension", currentDimension(client));
        BlockPos position = client.player == null ? new BlockPos(0, 64, 0) : client.player.blockPosition();
        setPosition(target, position.getX(), position.getY(), position.getZ());
        return target;
    }

    private static String currentDimension(Minecraft client) {
        return client.level == null ? "minecraft:overworld" : client.level.dimension().identifier().toString();
    }

    private List<IdSuggestion> dimensionSuggestions(Minecraft client) {
        Map<String, IdSuggestion> suggestions = new LinkedHashMap<>();
        addDimension(suggestions, "minecraft:overworld");
        addDimension(suggestions, "minecraft:the_nether");
        addDimension(suggestions, "minecraft:the_end");
        for (IdSuggestion suggestion : catalogDimensionIds) {
            suggestions.putIfAbsent(suggestion.id(), suggestion);
        }
        if (client.getConnection() != null) {
            client.getConnection().levels().forEach(level ->
                addDimension(suggestions, level.identifier().toString())
            );
        }
        addDimension(suggestions, currentDimension(client));
        return suggestions.values().stream()
            .sorted(Comparator.comparing(IdSuggestion::id))
            .toList();
    }

    private static void addDimension(Map<String, IdSuggestion> suggestions, String id) {
        suggestions.putIfAbsent(id, IdSuggestion.of(id));
    }

    private static int coordinate(JsonArray position, int index) {
        if (index >= position.size()) return index == 1 ? 64 : 0;
        JsonElement value = position.get(index);
        return integer(value, index == 1 ? 64 : 0);
    }

    private static void setPosition(JsonObject target, int x, int y, int z) {
        JsonArray position = new JsonArray();
        position.add(x);
        position.add(y);
        position.add(z);
        target.add("pos", position);
    }

    private static String dimensionProblem(String value) {
        return Identifier.tryParse(value == null ? "" : value.trim()) == null
            ? tr("validation.identifier_format")
            : "";
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
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
        tracker.apply(client, edited);
        ItemStackCodecs.validateStack(edited);
        return edited;
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
        return tracker.differsFrom(Minecraft.getInstance(), host.draft());
    }
}
