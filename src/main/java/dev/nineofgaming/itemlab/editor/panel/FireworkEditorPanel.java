package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.control.ColorSelector;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.list.JsonListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.array;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.bool;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.indexOf;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.integer;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Purpose-built rocket and firework-star editor with a real client particle preview.
 */
public final class FireworkEditorPanel extends ComponentEditorPanel {
    private static final AtomicLong NEXT_EXPLOSION_ROW_ID = new AtomicLong();
    private static final AtomicLong NEXT_COLOR_ROW_ID = new AtomicLong();
    private static final String[] SHAPE_IDS = {
        "small_ball", "large_ball", "star", "creeper", "burst"
    };

    private final CodecComponentState fireworks = new CodecComponentState(
        DataComponents.FIREWORKS,
        "{\"flight_duration\":1,\"explosions\":[]}"
    );
    private final CodecComponentState starExplosion = new CodecComponentState(
        DataComponents.FIREWORK_EXPLOSION,
        "{\"shape\":\"small_ball\",\"colors\":[16777215],\"fade_colors\":[],\"has_trail\":false,\"has_twinkle\":false}"
    );
    private final List<Long> explosionRowIds = new ArrayList<>();
    private final Map<String, List<Long>> colorRowIds = new HashMap<>();
    private final Map<String, OrderedListDragDrop.Scope> colorDragScopes = new HashMap<>();
    private long focusExplosionRowId;
    private boolean focusAddExplosionButton;
    private String focusColorListId;
    private long focusColorRowId;
    private String focusAddColorListId;
    private OrderedListDragDrop.Scope explosionDragScope = OrderedListDragDrop.newScope("firework_explosions");

    public FireworkEditorPanel(ItemEditor host) {
        super(host, "firework");
    }

    @Override
    public void requestPendingFocus() {
        focusExplosionRowId = 0L;
        focusAddExplosionButton = false;
        focusColorListId = null;
        focusColorRowId = 0L;
        focusAddColorListId = null;
        if (fireworks.present()) {
            if (fireworks.value().isJsonObject()) {
                JsonObject value = fireworks.value().getAsJsonObject();
                if (invalidCodecField(value, "flight_duration", ExtraCodecs.UNSIGNED_BYTE)) {
                    requestPendingField("rocket", "flight_duration");
                    return;
                }
                JsonElement explosionsValue = value.get("explosions");
                if (explosionsValue != null && explosionsValue.isJsonArray()) {
                    JsonArray explosions = explosionsValue.getAsJsonArray();
                    ensureExplosionRowIds(explosions.size());
                    for (int index = 0; index < explosions.size(); index++) {
                        if (!explosions.get(index).isJsonObject()) continue;
                        long rowId = explosionRowIds.get(index);
                        String path = "rocket_explosion_" + rowId;
                        String field = invalidExplosionField(explosions.get(index).getAsJsonObject(), path);
                        if (field != null || index >= Fireworks.MAX_EXPLOSIONS) {
                            focusExplosionRowId = rowId;
                            requestPendingField("rocket", field == null ? path + "/shape" : field);
                            return;
                        }
                    }
                }
            }
            if (invalidCodecValue(Fireworks.CODEC, fireworks.value())) {
                requestPendingField("rocket", "$enabled");
                return;
            }
        }
        if (starExplosion.present()) {
            if (starExplosion.value().isJsonObject()) {
                String field = invalidExplosionField(starExplosion.value().getAsJsonObject(), "star_explosion");
                if (field != null) {
                    requestPendingField("star", field);
                    return;
                }
            }
            requestPendingField("star", "$enabled");
            return;
        }
        requestPendingField("rocket", "$enabled");
    }

    private String invalidExplosionField(JsonObject value, String path) {
        if (!value.has("shape") || invalidCodecField(value, "shape", FireworkExplosion.Shape.CODEC))
            return path + "/shape";
        for (String field : List.of("has_trail", "has_twinkle")) {
            if (invalidCodecField(value, field, Codec.BOOL)) return path + "/" + field;
        }
        for (String[] field : new String[][]{{"colors", "primary"}, {"fade_colors", "fade"}}) {
            JsonElement colorsValue = value.get(field[0]);
            if (colorsValue == null || !colorsValue.isJsonArray()) continue;
            JsonArray colors = colorsValue.getAsJsonArray();
            for (int index = 0; index < colors.size(); index++) {
                if (invalidCodecValue(Codec.INT, colors.get(index))) {
                    String listId = path + "/" + field[1];
                    List<Long> rowIds = colorRowIds.computeIfAbsent(listId, ignored -> new ArrayList<>());
                    ensureRowIds(rowIds, colors.size(), NEXT_COLOR_ROW_ID);
                    return listId + "/row/" + rowIds.get(index);
                }
            }
        }
        return null;
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.FIREWORKS || type == DataComponents.FIREWORK_EXPLOSION;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.has(DataComponents.FIREWORKS) || stack.has(DataComponents.FIREWORK_EXPLOSION);
    }

    public void sync(Minecraft client, ItemStack stack) {
        fireworks.sync(client, stack);
        starExplosion.sync(client, stack);
        resetExplosionRowIds();
        resetEditorUi();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (type == DataComponents.FIREWORKS) {
            fireworks.sync(client, stack);
            resetExplosionRowIds();
        } else if (type == DataComponents.FIREWORK_EXPLOSION) {
            starExplosion.sync(client, stack);
            resetColorRowIds();
        }
        resetEditorUi();
        dirty = fireworks.differsFrom(client, stack) || starExplosion.differsFrom(client, stack);
    }

    public void draw(Minecraft client) {
        if (drawToolbar(client, null)) return;
        ItemStack preview = previewStack(client);
        host.setItemIconPreview(preview);
        EditorUi.beginScrollableChild("firework_editor_sections", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            drawPreviews(client, preview, "firework");
            if (host.draft().has(DataComponents.FIREWORKS) || fireworks.present() || hasPendingSectionFocus("rocket")) {
                ImGui.spacing();
                drawRocket();
            }
            if (host.draft().has(DataComponents.FIREWORK_EXPLOSION) || starExplosion.present() || hasPendingSectionFocus("star")) {
                ImGui.spacing();
                drawStarExplosion();
            }
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (drawToolbar(client, type)) return;
        ItemStack preview = previewStack(client);
        host.setItemIconPreview(preview);
        drawPreviews(client, preview, "firework_component");
        ImGui.spacing();
        if (type == DataComponents.FIREWORKS) drawRocket();
        else if (type == DataComponents.FIREWORK_EXPLOSION) drawStarExplosion();
    }

    private void drawPreviews(Minecraft client, ItemStack preview, String id) {
        host.drawTooltipPreview(client, preview, id);
        ImGui.spacing();
        if (!EditorUi.beginCollapsibleCard(
            "firework_particle_preview_" + id,
            tr("firework.preview"),
            ImGuiTableFlags.SizingStretchProp
        )) return;
        EditorUi.textDisabledWrapped(tr("firework.preview.help"));
        List<FireworkExplosion> explosions = previewExplosions(preview);
        ImGui.beginDisabled(explosions.isEmpty());
        if (EditorIcons.button("play_firework_preview", EditorIcons.Icon.PLAY, tr("firework.preview.play"))) {
            playParticlePreview(client, explosions);
        }
        ImGui.endDisabled();
        if (explosions.isEmpty()) EditorUi.textDisabledWrapped(tr("firework.preview.empty"));
        EditorUi.endCard();
    }

    private void drawRocket() {
        if (!beginSection("rocket", fireworks)) return;
        JsonObject value = fireworks.object();
        if (beginFields("firework_rocket_fields")) {
            if (fireworks.supportsField("flight_duration")) {
                fieldLabel(tr("firework.flight"), tr("firework.flight.help"));
                ImInt duration = new ImInt(integer(value, "flight_duration", 1));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("rocket", "flight_duration");
                if (ImGui.inputInt("##flight_duration", duration, 1, 3)) {
                    value.addProperty("flight_duration", Math.clamp(duration.get(), 0, 255));
                    dirty = true;
                }
            }
            ImGui.endTable();
        }
        if (fireworks.supportsField("explosions")) {
            ImGui.spacing();
            drawExplosionList(array(value, "explosions"));
        }
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(explosionDragScope);
    }

    private void drawExplosionList(JsonArray explosions) {
        ensureExplosionRowIds(explosions.size());
        ImGui.separatorText(tr("firework.explosions"));
        drawAddExplosionAction(explosions, "top", false);
        ImGui.spacing();
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < explosions.size(); index++) {
            JsonElement element = explosions.get(index);
            if (!element.isJsonObject()) continue;
            long rowId = explosionRowIds.get(index);
            ImGui.pushID("firework_explosion_" + rowId);
            boolean requestFocus = focusExplosionRowId == rowId;
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "firework_explosion",
                explosionTitle(index, element.getAsJsonObject()),
                ImGuiTableFlags.SizingStretchProp,
                index,
                explosions.size(),
                true,
                explosions.size() < Fireworks.MAX_EXPLOSIONS,
                explosions.size() < Fireworks.MAX_EXPLOSIONS,
                requestFocus,
                explosionDragScope,
                rowId
            );
            if (card.focusApplied()) focusExplosionRowId = 0L;
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = card.action();
            }
            if (card.expanded()) {
                drawExplosionFields(element.getAsJsonObject(), "rocket_explosion_" + rowId);
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                explosionDragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        long removedExplosionRowId = reorderRequest == null && action == EditorUi.ListAction.REMOVE && actionIndex < explosionRowIds.size()
            ? explosionRowIds.get(actionIndex)
            : 0L;
        JsonListMutations.Mutation mutation = reorderRequest == null
            ? JsonListMutations.apply(
            explosions,
            explosionRowIds,
            actionIndex,
            action,
            NEXT_EXPLOSION_ROW_ID::incrementAndGet,
            FireworkEditorPanel::defaultExplosion
        )
            : JsonListMutations.reorder(explosions, explosionRowIds, reorderRequest);
        if (mutation.changed()) {
            if (removedExplosionRowId != 0L) {
                removeColorRows("rocket_explosion_" + removedExplosionRowId + "/");
            }
            if (mutation.focusRowId() == 0L) {
                focusExplosionRowId = 0L;
                focusAddExplosionButton = true;
            } else {
                focusExplosionRowId = mutation.focusRowId();
                focusAddExplosionButton = false;
            }
            resetEditorUi();
            dirty = true;
        }
        if (explosions.isEmpty()) EditorUi.drawEmptyState(tr("firework.explosions.empty"));
        drawAddExplosionAction(explosions, "bottom", true);
    }

    private void drawAddExplosionAction(JsonArray explosions, String placement, boolean acceptFocus) {
        ImGui.beginDisabled(explosions.size() >= Fireworks.MAX_EXPLOSIONS);
        if (acceptFocus && focusAddExplosionButton) {
            ImGui.setKeyboardFocusHere();
            focusAddExplosionButton = false;
        }
        if (EditorIcons.button(
            "add_firework_explosion_" + placement,
            EditorIcons.Icon.ADD,
            tr("firework.explosions.add")
        )) {
            explosions.add(defaultExplosion());
            long rowId = NEXT_EXPLOSION_ROW_ID.incrementAndGet();
            explosionRowIds.add(rowId);
            focusExplosionRowId = rowId;
            focusAddExplosionButton = false;
            resetEditorUi();
            dirty = true;
        }
        ImGui.endDisabled();
        if (explosions.size() >= Fireworks.MAX_EXPLOSIONS && ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("firework.explosions.limit", Fireworks.MAX_EXPLOSIONS));
        }
    }

    private void resetExplosionRowIds() {
        explosionRowIds.clear();
        focusExplosionRowId = 0L;
        focusAddExplosionButton = false;
        explosionDragScope = OrderedListDragDrop.newScope("firework_explosions");
        resetColorRowIds();
        ensureRowIds(explosionRowIds, array(fireworks.object(), "explosions").size(), NEXT_EXPLOSION_ROW_ID);
    }

    private void ensureExplosionRowIds(int size) {
        ensureRowIds(explosionRowIds, size, NEXT_EXPLOSION_ROW_ID);
    }

    private void resetColorRowIds() {
        colorRowIds.clear();
        colorDragScopes.clear();
        focusColorListId = null;
        focusColorRowId = 0L;
        focusAddColorListId = null;
    }

    private void removeColorRows(String listIdPrefix) {
        colorRowIds.keySet().removeIf(listId -> listId.startsWith(listIdPrefix));
        colorDragScopes.keySet().removeIf(listId -> listId.startsWith(listIdPrefix));
        if (focusColorListId != null && focusColorListId.startsWith(listIdPrefix)) {
            focusColorListId = null;
            focusColorRowId = 0L;
        }
        if (focusAddColorListId != null && focusAddColorListId.startsWith(listIdPrefix)) {
            focusAddColorListId = null;
        }
    }

    private static void ensureRowIds(List<Long> rowIds, int size, AtomicLong nextRowId) {
        while (rowIds.size() < size) {
            rowIds.add(nextRowId.incrementAndGet());
        }
        while (rowIds.size() > size) {
            rowIds.removeLast();
        }
    }

    private void drawStarExplosion() {
        if (!beginSection("star", starExplosion)) return;
        drawExplosionFields(starExplosion.object(), "star_explosion");
        endSection();
    }

    private void drawExplosionFields(JsonObject explosion, String path) {
        ImGui.pushID(path);
        try {
            if (beginFields("firework_explosion_fields")) {
                fieldLabel(tr("firework.shape"), tr("firework.shape.help"));
                ImInt shape = new ImInt(indexOf(SHAPE_IDS, string(explosion, "shape", SHAPE_IDS[0])));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus(path.startsWith("rocket_") ? "rocket" : "star", path + "/shape");
                if (ImGui.combo("##shape", shape, shapeLabels())) {
                    explosion.addProperty("shape", SHAPE_IDS[shape.get()]);
                    dirty = true;
                }

                fieldLabel(tr("firework.trail"), tr("firework.trail.help"));
                applyPendingFieldFocus(path.startsWith("rocket_") ? "rocket" : "star", path + "/has_trail");
                dirty |= booleanField(explosion, "has_trail", false);

                fieldLabel(tr("firework.twinkle"), tr("firework.twinkle.help"));
                applyPendingFieldFocus(path.startsWith("rocket_") ? "rocket" : "star", path + "/has_twinkle");
                dirty |= booleanField(explosion, "has_twinkle", false);
                ImGui.endTable();
            }

            ImGui.spacing();
            drawColors(
                array(explosion, "colors"),
                "primary",
                tr("firework.colors"),
                path + "/primary"
            );
            ImGui.spacing();
            drawColors(
                array(explosion, "fade_colors"),
                "fade",
                tr("firework.fade_colors"),
                path + "/fade"
            );
        } finally {
            ImGui.popID();
        }
    }

    private void drawColors(JsonArray colors, String path, String title, String listId) {
        List<Long> rowIds = colorRowIds.computeIfAbsent(listId, ignored -> new ArrayList<>());
        OrderedListDragDrop.Scope dragScope = colorDragScopes.computeIfAbsent(
            listId,
            ignored -> OrderedListDragDrop.newScope("firework/colors/" + listId)
        );
        ensureRowIds(rowIds, colors.size(), NEXT_COLOR_ROW_ID);
        ImGui.separatorText(title);
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < colors.size(); index++) {
            JsonElement element = colors.get(index);
            int color = element != null && element.isJsonPrimitive()
                ? integer(element, 0xFFFFFF) & 0xFFFFFF
                : 0xFFFFFF;
            long rowId = rowIds.get(index);
            ImGui.pushID(path + "_" + rowId);
            ImGui.beginGroup();
            if (EditorUi.beginInlineIconActionRow("firework_color_row", 4)) {
                OrderedListDragDrop.drawHandle(
                    "reorder_firework_color",
                    dragScope,
                    rowId,
                    tr("firework.color.entry", index + 1)
                );
                ImGui.sameLine();
                ImGui.alignTextToFramePadding();
                ImGui.text(tr("firework.color.entry", index + 1));
                ImGui.tableNextColumn();
                EditorUi.ListAction rowAction = EditorUi.drawInlineOrderedListActions(index, colors.size(), true);
                if (rowAction != EditorUi.ListAction.NONE) {
                    actionIndex = index;
                    action = rowAction;
                }
                ImGui.endTable();
            }
            boolean requestFocus = listId.equals(focusColorListId) && focusColorRowId == rowId;
            boolean pendingFocus = consumePendingFieldFocus(listId.startsWith("rocket_") ? "rocket" : "star", listId + "/row/" + rowId);
            if (pendingFocus) ImGui.setScrollHereY(0.35f);
            requestFocus |= pendingFocus;
            int changed = ColorSelector.drawRgb(path + "_" + rowId, color, false, requestFocus);
            if (requestFocus) {
                focusColorListId = null;
                focusColorRowId = 0L;
            }
            if (changed != color) {
                colors.set(index, new JsonPrimitive(changed));
                dirty = true;
            }
            ImGui.endGroup();
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                dragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        JsonListMutations.Mutation mutation = reorderRequest == null
            ? JsonListMutations.apply(
            colors,
            rowIds,
            actionIndex,
            action,
            NEXT_COLOR_ROW_ID::incrementAndGet,
            () -> new JsonPrimitive(0xFFFFFF)
        )
            : dragScope.equals(reorderRequest.scope())
            ? JsonListMutations.reorder(colors, rowIds, reorderRequest)
            : new JsonListMutations.Mutation(false, 0L);
        if (mutation.changed()) {
            if (mutation.focusRowId() == 0L) {
                focusColorListId = null;
                focusColorRowId = 0L;
                focusAddColorListId = listId;
            } else {
                focusColorListId = listId;
                focusColorRowId = mutation.focusRowId();
                focusAddColorListId = null;
            }
            resetEditorUi();
            dirty = true;
        }
        OrderedListDragDrop.autoScrollCurrentWindow(dragScope);
        if (colors.isEmpty()) EditorUi.drawEmptyState(tr("firework.colors.empty"));
        if (listId.equals(focusAddColorListId)) {
            ImGui.setKeyboardFocusHere();
            focusAddColorListId = null;
        }
        if (EditorIcons.smallButton(
            "add_" + path + "_color",
            EditorIcons.Icon.ADD,
            tr("firework.color.add")
        )) {
            colors.add(0xFFFFFF);
            long rowId = NEXT_COLOR_ROW_ID.incrementAndGet();
            rowIds.add(rowId);
            focusColorListId = listId;
            focusColorRowId = rowId;
            focusAddColorListId = null;
            resetEditorUi();
            dirty = true;
        }
    }

    private static boolean booleanField(JsonObject object, String key, boolean fallback) {
        ImBoolean value = new ImBoolean(bool(object, key, fallback));
        if (!ImGui.checkbox("##" + key, value)) return false;
        object.addProperty(key, value.get());
        return true;
    }

    private static String[] shapeLabels() {
        return new String[]{
            tr("firework.shape.small_ball"),
            tr("firework.shape.large_ball"),
            tr("firework.shape.star"),
            tr("firework.shape.creeper"),
            tr("firework.shape.burst")
        };
    }

    private static String explosionTitle(int index, JsonObject explosion) {
        int shape = indexOf(SHAPE_IDS, string(explosion, "shape", SHAPE_IDS[0]));
        return tr("firework.explosion.entry", index + 1, shapeLabels()[shape]);
    }

    private List<FireworkExplosion> previewExplosions(ItemStack preview) {
        Fireworks installedFireworks = preview.get(DataComponents.FIREWORKS);
        if (installedFireworks != null && !installedFireworks.explosions().isEmpty()) {
            return installedFireworks.explosions();
        }
        FireworkExplosion installedExplosion = preview.get(DataComponents.FIREWORK_EXPLOSION);
        return installedExplosion == null ? List.of() : List.of(installedExplosion);
    }

    private void playParticlePreview(Minecraft client, List<FireworkExplosion> explosions) {
        if (client.level == null || explosions.isEmpty()) return;
        //? if >=26.2 {
        Camera camera = client.gameRenderer.mainCamera();
        //?} else {
        /*Camera camera = client.gameRenderer.getMainCamera();
         *///?}
        Vector3fc forward = camera.forwardVector();
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        double x = camera.position().x + forward.x() * 5.0 - left.x() * 2.5 + up.x();
        double y = camera.position().y + forward.y() * 5.0 - left.y() * 2.5 + up.y();
        double z = camera.position().z + forward.z() * 5.0 - left.z() * 2.5 + up.z();
        client.particleEngine.add(new FireworkParticles.Starter(
            client.level,
            x,
            y,
            z,
            0.0,
            0.0,
            0.0,
            client.particleEngine,
            List.copyOf(explosions)
        ));
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
        fireworks.apply(client, edited);
        starExplosion.apply(client, edited);
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
        return !ItemStack.matches(host.draft(), buildEditedStack(Minecraft.getInstance()));
    }

    private boolean drawToolbar(Minecraft client, DataComponentType<?> componentType) {
        return drawPanelToolbar(
            () -> save(client, true),
            () -> {
                if (componentType == null) sync(client, host.draft());
                else syncComponent(client, componentType, host.draft());
            }
        );
    }

    private static JsonObject defaultExplosion() {
        JsonObject explosion = new JsonObject();
        explosion.addProperty("shape", SHAPE_IDS[0]);
        JsonArray colors = new JsonArray();
        colors.add(0xFFFFFF);
        explosion.add("colors", colors);
        explosion.add("fade_colors", new JsonArray());
        explosion.addProperty("has_trail", false);
        explosion.addProperty("has_twinkle", false);
        return explosion;
    }
}
