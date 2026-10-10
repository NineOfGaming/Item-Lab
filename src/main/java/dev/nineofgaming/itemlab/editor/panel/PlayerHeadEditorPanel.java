package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.list.JsonListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.preview.PlayerSkinPreviewMode;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.array;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.indexOf;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextMultilineWithHint;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;

/**
 * Complete profile editor with a native two-layer, rotatable player-head preview.
 */
public final class PlayerHeadEditorPanel extends ComponentEditorPanel {
    private static final int MAX_PROPERTIES = 16;
    private static final AtomicLong NEXT_PROPERTY_ROW_ID = new AtomicLong();
    private static final String[] MODEL_IDS = {"wide", "slim"};

    private final ItemLabConfig preferences = ItemLabConfig.get();
    private final CodecComponentState profile = new CodecComponentState(
        DataComponents.PROFILE,
        "{\"name\":\"Player\",\"properties\":[]}"
    );
    private final List<Long> propertyRowIds = new ArrayList<>();
    private List<IdSuggestion> textures = List.of();
    private PlayerSkinPreviewMode previewMode = preferences.playerSkinPreviewMode();
    private long focusPropertyRowId;
    private boolean focusAddPropertyButton;
    private OrderedListDragDrop.Scope propertyDragScope = OrderedListDragDrop.newScope("player_head/properties");

    public PlayerHeadEditorPanel(ItemEditor host) {
        super(host, "player_head");
    }

    @Override
    public void requestPendingFocus() {
        JsonElement encoded = profile.value();
        if (profile.present() && encoded.isJsonObject()) {
            JsonObject value = encoded.getAsJsonObject();
            if (value.has("name") && !nameProblem(pendingText("profile_name", string(value, "name", ""))).isBlank()) {
                requestPendingField("profile", "name");
                return;
            }
            if (value.has("id") && !uuidProblem(pendingText("profile_uuid", uuidString(value.get("id"), ""))).isBlank()) {
                requestPendingField("profile", "uuid");
                return;
            }
            String[][] textures = {{"texture", "skin_texture"}, {"cape", "cape_texture"}, {"elytra", "elytra_texture"}};
            for (String[] texture : textures) {
                if (!identifierProblem(pendingText(texture[1], string(value, texture[0], ""))).isBlank()) {
                    requestPendingField("profile", texture[0]);
                    return;
                }
            }
            JsonElement properties = value.get("properties");
            if (properties != null && properties.isJsonArray()) {
                for (int index = 0; index < properties.getAsJsonArray().size(); index++) {
                    JsonElement property = properties.getAsJsonArray().get(index);
                    if (property.isJsonObject() && string(property.getAsJsonObject(), "name", "").isBlank()) {
                        requestPendingField("profile", "property/" + index + "/name");
                        return;
                    }
                }
            }
        }
        requestPendingField("profile", "$enabled");
    }

    private String pendingText(String key, String fallback) {
        ImString source = textBuffers.get(key);
        return source == null ? fallback : source.get();
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.PROFILE;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.has(DataComponents.PROFILE);
    }

    public void setCatalog(EditorCatalog catalog) {
        textures = catalog.registryIds("texture");
    }

    public void sync(Minecraft client, ItemStack stack) {
        profile.sync(client, stack);
        normalizeObjectEncoding();
        resetPropertyRowIds();
        resetEditorUi();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (supports(type)) sync(client, stack);
    }

    public void draw(Minecraft client) {
        if (drawToolbar(client, null)) return;
        ItemStack preview = previewStack(client);
        host.setItemIconPreview(preview);
        EditorUi.beginScrollableChild("player_head_editor_sections", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            drawHeadPreview(client, preview, "player_head");
            ImGui.spacing();
            drawProfile(client);
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (drawToolbar(client, type)) return;
        ItemStack preview = previewStack(client);
        host.setItemIconPreview(preview);
        drawHeadPreview(client, preview, "player_head_component");
        ImGui.spacing();
        drawProfile(client);
    }

    private void drawHeadPreview(Minecraft client, ItemStack preview, String id) {
        if (!EditorUi.beginCollapsibleCard(
            "player_head_preview_" + id,
            tr("player_head.preview"),
            ImGuiTableFlags.SizingStretchProp
        )) return;
        for (PlayerSkinPreviewMode option : PlayerSkinPreviewMode.values()) {
            if (option != PlayerSkinPreviewMode.HEAD_ONLY) EditorUi.sameLineIfFits(option.label());
            boolean selected = previewMode == option;
            if (selected) ItemEditorTheme.pushActiveFilter();
            if (ImGui.button(option.label() + "##player_skin_preview_mode_" + id) && previewMode != option) {
                previewMode = option;
                preferences.setPlayerSkinPreviewMode(option);
            }
            if (selected) ItemEditorTheme.popActiveFilter();
        }
        ImGui.spacing();
        EditorUi.textDisabledWrapped(tr("preview.rotation_help"));
        ImGui.spacing();
        host.drawPlayerHeadPreview(client, preview, previewMode);
        if (preview.get(DataComponents.PROFILE) == null) {
            EditorUi.textDisabledWrapped(tr("player_head.preview.requires_profile"));
        }
        EditorUi.endCard();
    }

    private void drawProfile(Minecraft client) {
        if (!beginSection("profile", profile)) return;
        JsonObject value = profile.object();
        if (beginFields("player_profile_fields")) {
            if (profile.supportsField("name")) {
                fieldLabel(tr("player_head.name"), tr("player_head.name.help"));
                drawOptionalName(client, value);
            }
            if (profile.supportsField("id")) {
                fieldLabel(tr("player_head.uuid"), tr("player_head.uuid.help"));
                drawOptionalUuid(client, value);
            }
            if (profile.supportsField("texture")) {
                fieldLabel(tr("player_head.texture"), tr("player_head.texture.help"));
                drawOptionalTexture(value, "texture", "skin_texture");
            }
            if (profile.supportsField("model")) {
                fieldLabel(tr("player_head.model"), tr("player_head.model.help"));
                drawOptionalModel(value);
            }
            if (profile.supportsField("cape")) {
                fieldLabel(tr("player_head.cape"), tr("player_head.cape.help"));
                drawOptionalTexture(value, "cape", "cape_texture");
            }
            if (profile.supportsField("elytra")) {
                fieldLabel(tr("player_head.elytra"), tr("player_head.elytra.help"));
                drawOptionalTexture(value, "elytra", "elytra_texture");
            }
            ImGui.endTable();
        }

        if (profile.supportsField("properties")) {
            ImGui.spacing();
            drawProperties(value);
        }
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(propertyDragScope);
    }

    private void drawOptionalName(Minecraft client, JsonObject value) {
        ImBoolean enabled = new ImBoolean(value.has("name"));
        if (ImGui.checkbox(tr("player_head.name.enabled"), enabled)) {
            if (enabled.get()) value.addProperty("name", currentPlayerName(client));
            else value.remove("name");
            resetEditorUi();
            dirty = true;
        }
        ImGui.beginDisabled(!enabled.get());
        ImString name = buffer("profile_name", string(value, "name", currentPlayerName(client)));
        String problem = nameProblem(name.get());
        if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("profile", "name");
        if (inputTextWithHint(
            "##profile_name",
            new FieldHints(tr("field_purpose.player_name"), currentPlayerName(client)),
            name
        )) {
            value.addProperty("name", name.get());
            dirty = true;
        }
        if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
        ItemEditorTheme.drawFieldProblem(nameProblem(name.get()));
        ImGui.endDisabled();
    }

    private void drawOptionalUuid(Minecraft client, JsonObject value) {
        ImBoolean enabled = new ImBoolean(value.has("id"));
        if (ImGui.checkbox(tr("player_head.uuid.enabled"), enabled)) {
            if (enabled.get()) value.add("id", uuidJson(UUID.fromString(currentPlayerUuid(client))));
            else value.remove("id");
            resetEditorUi();
            dirty = true;
        }
        ImGui.beginDisabled(!enabled.get());
        ImString id = buffer("profile_uuid", uuidString(value.get("id"), currentPlayerUuid(client)));
        String problem = uuidProblem(id.get());
        if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("profile", "uuid");
        if (inputTextWithHint(
            "##profile_uuid",
            new FieldHints(tr("field_purpose.uuid"), currentPlayerUuid(client)),
            id
        )) {
            try {
                value.add("id", uuidJson(UUID.fromString(id.get().trim())));
            } catch (IllegalArgumentException exception) {
                // Keep the invalid text in the codec draft so Save cannot silently
                // retain the last valid UUID while the field reports an error.
                value.addProperty("id", id.get().trim());
            }
            dirty = true;
        }
        if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
        ItemEditorTheme.drawFieldProblem(uuidProblem(id.get()));
        ImGui.endDisabled();
    }

    private void drawOptionalTexture(JsonObject value, String key, String bufferKey) {
        ImString texture = buffer(bufferKey, string(value, key, ""));
        String problem = identifierProblem(texture.get());
        applyPendingFieldFocus("profile", key);
        if (host.inputIdentifierWithSuggestions(
            bufferKey,
            new FieldHints(
                tr("field_purpose.texture_identifier"),
                "minecraft:entity/player/wide/steve"
            ),
            texture,
            textures,
            problem
        )) {
            if (texture.get().isBlank()) value.remove(key);
            else value.addProperty(key, texture.get().trim());
            dirty = true;
        }
    }

    private void drawOptionalModel(JsonObject value) {
        ImBoolean enabled = new ImBoolean(value.has("model"));
        if (ImGui.checkbox(tr("player_head.model.override"), enabled)) {
            if (enabled.get()) value.addProperty("model", MODEL_IDS[0]);
            else value.remove("model");
            dirty = true;
        }
        ImGui.beginDisabled(!enabled.get());
        ImInt selected = new ImInt(indexOf(MODEL_IDS, string(value, "model", MODEL_IDS[0])));
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.combo("##profile_model", selected, new String[]{
            tr("player_head.model.wide"),
            tr("player_head.model.slim")
        })) {
            value.addProperty("model", MODEL_IDS[selected.get()]);
            dirty = true;
        }
        ImGui.endDisabled();
    }

    private void drawProperties(JsonObject value) {
        ImGui.separatorText(tr("player_head.properties"));
        EditorUi.textDisabledWrapped(tr("player_head.properties.help"));
        ImGui.spacing();
        JsonArray properties = array(value, "properties");
        ensurePropertyRowIds(properties.size());
        drawAddPropertyAction(properties, "top", false);
        ImGui.spacing();
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < properties.size(); index++) {
            JsonElement element = properties.get(index);
            if (!element.isJsonObject()) continue;
            JsonObject property = element.getAsJsonObject();
            long rowId = propertyRowIds.get(index);
            ImGui.pushID("profile_property_" + rowId);
            boolean requestFocus = focusPropertyRowId == rowId
                || hasPendingFieldFocus("profile", "property/" + index + "/name");
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "profile_property",
                propertyTitle(index, property),
                ImGuiTableFlags.SizingStretchProp,
                index,
                properties.size(),
                true,
                properties.size() < MAX_PROPERTIES,
                properties.size() < MAX_PROPERTIES,
                requestFocus,
                propertyDragScope,
                rowId
            );
            if (card.focusApplied()) focusPropertyRowId = 0L;
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = card.action();
            }
            if (card.expanded()) {
                drawProperty(property, rowId, index);
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                propertyDragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        JsonListMutations.Mutation mutation = reorderRequest == null
            ? JsonListMutations.apply(
            properties,
            propertyRowIds,
            actionIndex,
            action,
            NEXT_PROPERTY_ROW_ID::incrementAndGet,
            PlayerHeadEditorPanel::defaultProperty
        )
            : propertyDragScope.equals(reorderRequest.scope())
            ? JsonListMutations.reorder(properties, propertyRowIds, reorderRequest)
            : new JsonListMutations.Mutation(false, 0L);
        if (mutation.changed()) {
            if (mutation.focusRowId() == 0L) {
                focusPropertyRowId = 0L;
                focusAddPropertyButton = true;
            } else {
                focusPropertyRowId = mutation.focusRowId();
                focusAddPropertyButton = false;
            }
            resetEditorUi();
            dirty = true;
        }
        if (properties.isEmpty()) EditorUi.drawEmptyState(tr("player_head.properties.empty"));
        drawAddPropertyAction(properties, "bottom", true);
    }

    private void drawAddPropertyAction(JsonArray properties, String placement, boolean acceptFocus) {
        ImGui.beginDisabled(properties.size() >= MAX_PROPERTIES);
        if (acceptFocus && focusAddPropertyButton) {
            ImGui.setKeyboardFocusHere();
            focusAddPropertyButton = false;
        }
        if (EditorIcons.smallButton(
            "add_profile_property_" + placement,
            EditorIcons.Icon.ADD,
            tr("player_head.properties.add")
        )) {
            properties.add(defaultProperty());
            long rowId = NEXT_PROPERTY_ROW_ID.incrementAndGet();
            propertyRowIds.add(rowId);
            focusPropertyRowId = rowId;
            focusAddPropertyButton = false;
            resetEditorUi();
            dirty = true;
        }
        ImGui.endDisabled();
        if (properties.size() >= MAX_PROPERTIES && ImGui.isItemHovered()) {
            EditorUi.setTooltip(tr("player_head.properties.limit", MAX_PROPERTIES));
        }
    }

    private static JsonObject defaultProperty() {
        JsonObject property = new JsonObject();
        property.addProperty("name", "");
        property.addProperty("value", "");
        return property;
    }

    private void drawProperty(JsonObject property, long rowId, int index) {
        if (!beginFields("profile_property_fields")) return;

        fieldLabel(tr("player_head.property.name"), tr("player_head.property.name.help"));
        ImString name = buffer("property_name_" + rowId, string(property, "name", ""));
        String nameProblem = name.get().isBlank() ? tr("validation.key_required") : "";
        if (!nameProblem.isBlank()) ItemEditorTheme.pushInvalidField();
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("profile", "property/" + index + "/name");
        if (inputTextWithHint(
            "##property_name",
            new FieldHints(tr("field_purpose.profile_property_name"), "textures"),
            name
        )) {
            property.addProperty("name", name.get().trim());
            dirty = true;
        }
        if (!nameProblem.isBlank()) ItemEditorTheme.popInvalidField();
        ItemEditorTheme.drawFieldProblem(name.get().isBlank() ? tr("validation.key_required") : "");

        fieldLabel(tr("player_head.property.value"), tr("player_head.property.value.help"));
        ImString propertyValue = largeBuffer("property_value_" + rowId, string(property, "value", ""), 32768);
        if (inputTextMultilineWithHint(
            "##property_value",
            new FieldHints(
                tr("field_purpose.profile_property_payload"),
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7Li4ufX19"
            ),
            propertyValue,
            -1.0f,
            92.0f,
            ImGuiInputTextFlags.AllowTabInput
        )) {
            property.addProperty("value", propertyValue.get());
            dirty = true;
        }

        fieldLabel(tr("player_head.property.signature"), tr("player_head.property.signature.help"));
        ImBoolean signed = new ImBoolean(property.has("signature"));
        if (ImGui.checkbox(tr("player_head.property.signature.enabled"), signed)) {
            if (signed.get()) property.addProperty("signature", "");
            else property.remove("signature");
            resetEditorUi();
            dirty = true;
        }
        ImGui.beginDisabled(!signed.get());
        ImString signature = largeBuffer(
            "property_signature_" + rowId,
            string(property, "signature", ""),
            4096
        );
        if (inputTextMultilineWithHint(
            "##property_signature",
            new FieldHints(tr("field_purpose.profile_property_signature"), "MEUCIQ..."),
            signature,
            -1.0f,
            72.0f,
            ImGuiInputTextFlags.AllowTabInput
        )) {
            property.addProperty("signature", signature.get());
            dirty = true;
        }
        ImGui.endDisabled();
        ImGui.endTable();
    }

    private void resetPropertyRowIds() {
        propertyRowIds.clear();
        propertyDragScope = OrderedListDragDrop.newScope("player_head/properties");
        focusPropertyRowId = 0L;
        focusAddPropertyButton = false;
        ensurePropertyRowIds(array(profile.object(), "properties").size());
    }

    private void ensurePropertyRowIds(int size) {
        while (propertyRowIds.size() < size) {
            propertyRowIds.add(NEXT_PROPERTY_ROW_ID.incrementAndGet());
        }
        while (propertyRowIds.size() > size) {
            propertyRowIds.removeLast();
        }
    }

    private ImString largeBuffer(String key, String value, int capacity) {
        return textBuffers.computeIfAbsent(key, ignored -> new ImString(value, Math.max(capacity, value.length() * 2 + 64)));
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
        profile.apply(client, edited);
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

    private void normalizeObjectEncoding() {
        JsonElement encoded = profile.value();
        if (encoded != null && encoded.isJsonPrimitive() && encoded.getAsJsonPrimitive().isString()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", encoded.getAsString());
            object.add("properties", new JsonArray());
            profile.setValue(object);
        }
    }

    private static String currentPlayerName(Minecraft client) {
        return client.player == null ? "Player" : client.player.getGameProfile().name();
    }

    private static String currentPlayerUuid(Minecraft client) {
        return client.player == null
            ? "00000000-0000-0000-0000-000000000000"
            : client.player.getGameProfile().id().toString();
    }

    private static String nameProblem(String value) {
        return StringUtil.isValidPlayerName(value) ? "" : tr("player_head.validation.name");
    }

    private static String uuidProblem(String value) {
        try {
            UUID.fromString(value.trim());
            return "";
        } catch (IllegalArgumentException exception) {
            return tr("player_head.validation.uuid");
        }
    }

    private static JsonArray uuidJson(UUID uuid) {
        JsonArray encoded = new JsonArray();
        for (int part : UUIDUtil.uuidToIntArray(uuid)) encoded.add(part);
        return encoded;
    }

    private static String uuidString(JsonElement encoded, String fallback) {
        try {
            if (encoded != null && encoded.isJsonArray() && encoded.getAsJsonArray().size() == 4) {
                int[] parts = new int[4];
                for (int index = 0; index < parts.length; index++) {
                    parts[index] = encoded.getAsJsonArray().get(index).getAsInt();
                }
                return UUIDUtil.uuidFromIntArray(parts).toString();
            }
            return string(encoded, fallback);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String identifierProblem(String value) {
        if (value == null || value.isBlank()) return "";
        return Identifier.tryParse(value.trim()) == null ? tr("validation.identifier_format") : "";
    }

    private static String propertyTitle(int index, JsonObject property) {
        return tr("player_head.property.entry", index + 1, string(property, "name", ""));
    }
}
