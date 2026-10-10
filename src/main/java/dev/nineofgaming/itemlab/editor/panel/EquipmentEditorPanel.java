package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.IdentifierCollectionEditor;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.preview.EquipmentPreviewBase;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.sound.SoundPreviewKind;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Arrays;
import java.util.List;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.bool;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.indexOf;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.primitiveString;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Purpose-built editor for equipping, worn visuals, trims and equipment sounds.
 */
public final class EquipmentEditorPanel extends ComponentEditorPanel {
    private static final String[] SLOT_IDS = {
        "mainhand", "offhand", "feet", "legs", "chest", "head", "body", "saddle"
    };

    private final ItemLabConfig preferences = ItemLabConfig.get();
    private final CodecComponentState equippable = new CodecComponentState(DataComponents.EQUIPPABLE, "{\"slot\":\"head\"}");
    private final CodecComponentState trim = new CodecComponentState(
        DataComponents.TRIM,
        "{\"material\":\"minecraft:iron\",\"pattern\":\"minecraft:sentry\"}"
    );
    private final CodecComponentState providedTrimMaterial = new CodecComponentState(
        DataComponents.PROVIDES_TRIM_MATERIAL,
        "\"minecraft:iron\""
    );
    private final CodecComponentState breakSound = new CodecComponentState(
        DataComponents.BREAK_SOUND,
        "\"minecraft:entity.item.break\""
    );
    private List<IdSuggestion> sounds = List.of();
    private List<IdSuggestion> equipmentAssets = List.of();
    private List<IdSuggestion> textures = List.of();
    private List<IdSuggestion> entities = List.of();
    private List<IdSuggestion> trimMaterials = List.of();
    private List<IdSuggestion> trimPatterns = List.of();
    private final IdentifierCollectionEditor.FocusState allowedEntitiesFocus =
        new IdentifierCollectionEditor.FocusState();
    private boolean focusAddRestrictionButton;
    private EquipmentPreviewBase previewBase = preferences.equipmentPreviewBase();

    public EquipmentEditorPanel(ItemEditor host) {
        super(host, "equipment");
    }

    @Override
    public void requestPendingFocus() {
        resetAllowedEntitiesFocus();
        if (equippable.present() && equippable.value().isJsonObject()) {
            JsonObject value = equippable.value().getAsJsonObject();
            if (Arrays.stream(SLOT_IDS).noneMatch(slot -> slot.equals(string(value, "slot", "head")))) {
                requestPendingField("equippable", "slot");
                return;
            }
            if (requestInvalidIdentifier("equippable", "equippable/equip_sound",
                string(value, "equip_sound", "minecraft:item.armor.equip_generic"), sounds)) return;
            for (String[] field : new String[][]{{"asset_id", "equippable/asset"}, {"camera_overlay", "equippable/overlay"}}) {
                String source = pendingBufferText(field[1], string(value, field[0], ""));
                if ((value.has(field[0]) || !source.isBlank()) && invalidIdentifier(source, List.of(), false)) {
                    requestPendingField("equippable", field[1]);
                    return;
                }
            }
            JsonElement allowed = value.get("allowed_entities");
            if (allowed != null) {
                int size = allowed.isJsonArray() ? allowed.getAsJsonArray().size() : 1;
                for (int index = 0; index < size; index++) {
                    JsonElement entry = allowed.isJsonArray() ? allowed.getAsJsonArray().get(index) : allowed;
                    String source = pendingBufferText("equippable/entities/@identifier/" + index,
                        entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString() ? entry.getAsString() : "");
                    if (invalidIdentifier(source, entities, !allowed.isJsonArray())) {
                        requestPendingField("equippable", "allowed_entities");
                        allowedEntitiesFocus.requestRow(index);
                        return;
                    }
                }
            }
            if (equippable.supportsField("shearing_sound") && requestInvalidIdentifier("equippable", "equippable/shearing_sound",
                string(value, "shearing_sound", "minecraft:item.shears.snip"), sounds)) return;
        }
        if (requestInvalidComponent("equippable", equippable)) return;
        if (trim.present() && trim.value().isJsonObject()) {
            JsonObject value = trim.value().getAsJsonObject();
            if (requestInvalidIdentifier("trim", "trim/material", string(value, "material", "minecraft:iron"), trimMaterials)
                || requestInvalidIdentifier("trim", "trim/pattern", string(value, "pattern", "minecraft:sentry"), trimPatterns))
                return;
        }
        if (requestInvalidComponent("trim", trim)) return;
        if (providedTrimMaterial.present() && requestInvalidIdentifier("provided_trim", "provided_trim/material",
            primitiveString(providedTrimMaterial, "minecraft:iron"), trimMaterials)) return;
        if (requestInvalidComponent("provided_trim", providedTrimMaterial)) return;
        if (breakSound.present() && requestInvalidIdentifier("break_sound", "break_sound",
            primitiveString(breakSound, "minecraft:entity.item.break"), sounds)) return;
        if (requestInvalidComponent("break_sound", breakSound)) return;
        requestPendingField(equippable.present() ? "equippable" : trim.present() ? "trim"
            : providedTrimMaterial.present() ? "provided_trim" : breakSound.present() ? "break_sound" : "equippable", "$enabled");
    }

    private boolean requestInvalidComponent(String section, CodecComponentState state) {
        Minecraft client = Minecraft.getInstance();
        if (!state.present() || client.level == null) return false;
        try {
            state.apply(client, host.draft().copy());
        } catch (RuntimeException ignored) {
            requestPendingField(section, "$enabled");
            return true;
        }
        return false;
    }

    private boolean requestInvalidIdentifier(String section, String field, String fallback, List<IdSuggestion> suggestions) {
        if (!invalidIdentifier(pendingBufferText(field, fallback), suggestions, false)) return false;
        requestPendingField(section, field);
        return true;
    }

    private static boolean invalidIdentifier(String source, List<IdSuggestion> suggestions, boolean allowTag) {
        String value = source.trim();
        boolean tag = allowTag && value.startsWith("#");
        Identifier parsed = Identifier.tryParse(tag ? value.substring(1) : value);
        if (parsed == null) return true;
        String canonical = (tag ? "#" : "") + parsed;
        return !suggestions.isEmpty() && suggestions.stream().noneMatch(suggestion -> suggestion.id().equals(canonical));
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.EQUIPPABLE
            || type == DataComponents.TRIM
            || type == DataComponents.PROVIDES_TRIM_MATERIAL
            || type == DataComponents.BREAK_SOUND;
    }

    public void setCatalog(EditorCatalog catalog) {
        sounds = catalog.soundIds();
        equipmentAssets = catalog.registryIds("equipment_asset");
        textures = catalog.registryIds("texture");
        entities = catalog.registryIdsAndTags("entity_type");
        trimMaterials = catalog.registryIds("trim_material");
        trimPatterns = catalog.registryIds("trim_pattern");
    }

    public void sync(Minecraft client, ItemStack draft) {
        equippable.sync(client, draft);
        trim.sync(client, draft);
        providedTrimMaterial.sync(client, draft);
        breakSound.sync(client, draft);
        resetEditorUi();
        resetAllowedEntitiesFocus();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack draft) {
        if (type == DataComponents.EQUIPPABLE) equippable.sync(client, draft);
        else if (type == DataComponents.TRIM) trim.sync(client, draft);
        else if (type == DataComponents.PROVIDES_TRIM_MATERIAL) providedTrimMaterial.sync(client, draft);
        else if (type == DataComponents.BREAK_SOUND) breakSound.sync(client, draft);
        resetEditorUi();
        resetAllowedEntitiesFocus();
        dirty = equippable.differsFrom(client, draft)
            || trim.differsFrom(client, draft)
            || providedTrimMaterial.differsFrom(client, draft)
            || breakSound.differsFrom(client, draft);
    }

    @Override
    protected boolean hasPendingChanges() {
        Minecraft client = Minecraft.getInstance();
        ItemStack draft = host.draft();
        return equippable.differsFrom(client, draft)
            || trim.differsFrom(client, draft)
            || providedTrimMaterial.differsFrom(client, draft)
            || breakSound.differsFrom(client, draft);
    }

    public void draw(Minecraft client) {
        if (drawToolbar(client, null)) return;
        ItemStack previewStack = previewStack(client);
        host.setItemIconPreview(previewStack);
        EditorUi.beginScrollableChild("equipment_editor_sections", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            drawEquipmentPreview(client, previewStack, "equipment_tab");
            ImGui.spacing();
            drawEquippable(client);
            ImGui.spacing();
            drawTrim();
            ImGui.spacing();
            drawProvidedTrimMaterial();
            ImGui.spacing();
            drawBreakSound(client);
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (drawToolbar(client, type)) return;
        ItemStack previewStack = previewStack(client);
        host.setItemIconPreview(previewStack);
        drawEquipmentPreview(client, previewStack, "equipment_component");
        ImGui.spacing();
        if (type == DataComponents.EQUIPPABLE) drawEquippable(client);
        else if (type == DataComponents.TRIM) drawTrim();
        else if (type == DataComponents.PROVIDES_TRIM_MATERIAL) drawProvidedTrimMaterial();
        else if (type == DataComponents.BREAK_SOUND) drawBreakSound(client);
    }

    private void drawEquipmentPreview(Minecraft client, ItemStack previewStack, String id) {
        if (!EditorUi.beginCollapsibleCard(
            "worn_equipment_preview_" + id,
            tr("equipment.preview"),
            ImGuiTableFlags.SizingStretchProp
        )) return;
        for (EquipmentPreviewBase option : EquipmentPreviewBase.values()) {
            if (option != EquipmentPreviewBase.PLAYER) EditorUi.sameLineIfFits(option.label());
            boolean selected = previewBase == option;
            if (selected) ItemEditorTheme.pushActiveFilter();
            if (ImGui.button(option.label() + "##preview_base_" + id) && previewBase != option) {
                previewBase = option;
                preferences.setEquipmentPreviewBase(option);
            }
            if (selected) ItemEditorTheme.popActiveFilter();
        }
        ImGui.spacing();
        EditorUi.textDisabledWrapped(tr("preview.rotation_help"));
        ImGui.spacing();
        host.drawEquipmentPreview(client, previewStack, previewBase);
        if (previewStack.get(DataComponents.EQUIPPABLE) == null) {
            EditorUi.textDisabledWrapped(tr("equipment.preview.requires_equippable"));
        } else {
            Equippable value = previewStack.get(DataComponents.EQUIPPABLE);
            if (value != null && (value.slot() == EquipmentSlot.BODY || value.slot() == EquipmentSlot.SADDLE)) {
                EditorUi.textDisabledWrapped(tr("equipment.preview.entity_specific"));
            }
        }
        EditorUi.endCard();
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
        equippable.apply(client, edited);
        trim.apply(client, edited);
        providedTrimMaterial.apply(client, edited);
        breakSound.apply(client, edited);
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

    private boolean drawToolbar(Minecraft client, DataComponentType<?> componentType) {
        return drawPanelToolbar(
            () -> save(client, true),
            () -> {
                if (componentType == null) sync(client, host.draft());
                else syncComponent(client, componentType, host.draft());
            }
        );
    }

    private void drawEquippable(Minecraft client) {
        if (!beginSection("equippable", equippable)) return;
        JsonObject value = equippable.object();
        if (beginFields("equippable_fields")) {
            if (equippable.supportsField("slot")) {
                fieldLabel(tr("equipment.equippable.slot"), tr("equipment.equippable.slot.help"));
                ImInt slot = new ImInt(indexOf(SLOT_IDS, string(value, "slot", "head")));
                applyPendingFieldFocus("equippable", "slot");
                ImGui.setNextItemWidth(-1.0f);
                if (ImGui.combo("##equipment_slot", slot, slotLabels())) {
                    value.addProperty("slot", SLOT_IDS[slot.get()]);
                    dirty = true;
                }
            }

            if (equippable.supportsField("equip_sound")) {
                fieldLabel(tr("equipment.equippable.equip_sound"), tr("equipment.equippable.equip_sound.help"));
                soundIdentifier(client, value, "equip_sound", "equippable/equip_sound", "minecraft:item.armor.equip_generic");
            }

            if (equippable.supportsField("asset_id")) {
                fieldLabel(tr("equipment.equippable.asset"), tr("equipment.equippable.asset.help"));
                optionalIdentifier(
                    value,
                    "asset_id",
                    "equippable/asset",
                    tr("field_purpose.asset_identifier"),
                    equipmentAssets
                );
            }

            if (equippable.supportsField("camera_overlay")) {
                fieldLabel(tr("equipment.equippable.overlay"), tr("equipment.equippable.overlay.help"));
                optionalIdentifier(
                    value,
                    "camera_overlay",
                    "equippable/overlay",
                    tr("field_purpose.texture_identifier"),
                    textures
                );
            }

            if (equippable.supportsField("allowed_entities")) {
                fieldLabel(tr("equipment.allowed_entities"), tr("equipment.allowed_entities.help"));
                drawAllowedEntities(value);
            }
            ImGui.endTable();
        }

        ImGui.spacing();
        ImGui.separatorText(tr("flags.behavior"));
        if (beginFields("equippable_behavior")) {
            booleanRow(equippable, value, "dispensable", tr("equipment.behavior.dispensable"), tr("equipment.behavior.dispensable.help"), true);
            booleanRow(equippable, value, "swappable", tr("equipment.behavior.swappable"), tr("equipment.behavior.swappable.help"), true);
            booleanRow(equippable, value, "damage_on_hurt", tr("equipment.behavior.damage_on_hurt"), tr("equipment.behavior.damage_on_hurt.help"), true);
            booleanRow(equippable, value, "equip_on_interact", tr("equipment.behavior.equip_on_interact"), tr("equipment.behavior.equip_on_interact.help"), false);
            booleanRow(equippable, value, "can_be_sheared", tr("equipment.behavior.can_be_sheared"), tr("equipment.behavior.can_be_sheared.help"), false);
            if (equippable.supportsField("shearing_sound")) {
                fieldLabel(tr("equipment.equippable.shearing_sound"), tr("equipment.equippable.shearing_sound.help"));
                soundIdentifier(client, value, "shearing_sound", "equippable/shearing_sound", "minecraft:item.shears.snip");
            }
            ImGui.endTable();
        }
        endSection();
    }

    private static String[] slotLabels() {
        return new String[]{
            tr("slot.main_hand"), tr("slot.off_hand"), tr("slot.feet"), tr("slot.legs"),
            tr("slot.chest"), tr("slot.head"), tr("slot.body"), tr("slot.saddle")
        };
    }

    private void drawAllowedEntities(JsonObject value) {
        JsonElement existing = value.get("allowed_entities");
        if (consumePendingFieldFocus("equippable", "allowed_entities")) ImGui.setScrollHereY(0.35f);
        if (existing == null) {
            EditorUi.drawEmptyState(tr("equipment.allowed_entities.empty"));
            if (focusAddRestrictionButton) {
                ImGui.setKeyboardFocusHere();
                focusAddRestrictionButton = false;
            }
            if (EditorIcons.smallButton(
                "add_equipment_restriction",
                EditorIcons.Icon.ADD,
                tr("equipment.add_restriction")
            )) {
                value.addProperty("allowed_entities", "minecraft:player");
                allowedEntitiesFocus.requestRow(0);
                dirty = true;
            }
            return;
        }

        dirty |= IdentifierCollectionEditor.draw(
            host,
            "allowed_entities",
            "equippable/entities",
            existing,
            replacement -> value.add("allowed_entities", replacement),
            entities,
            tr("field_purpose.entity_identifier"),
            "minecraft:player",
            tr("equipment.entity_type"),
            textBuffers,
            allowedEntitiesFocus
        );
        if (EditorIcons.smallButton(
            "clear_equipment_restriction",
            EditorIcons.Icon.REMOVE,
            tr("equipment.clear_restriction")
        )) {
            value.remove("allowed_entities");
            textBuffers.keySet().removeIf(key -> key.startsWith("equippable/entities/@identifier/"));
            allowedEntitiesFocus.clear();
            focusAddRestrictionButton = true;
            dirty = true;
        }
    }

    private void resetAllowedEntitiesFocus() {
        allowedEntitiesFocus.clear();
        focusAddRestrictionButton = false;
    }

    private void drawTrim() {
        if (!beginSection("trim", trim)) return;
        JsonObject value = trim.object();
        if (beginFields("trim_fields")) {
            if (trim.supportsField("material")) {
                fieldLabel(tr("equipment.trim.material"), tr("equipment.trim.material.help"));
                identifier(value, "material", "trim/material", "minecraft:iron", trimMaterials);
            }
            if (trim.supportsField("pattern")) {
                fieldLabel(tr("equipment.trim.pattern"), tr("equipment.trim.pattern.help"));
                identifier(value, "pattern", "trim/pattern", "minecraft:sentry", trimPatterns);
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawProvidedTrimMaterial() {
        if (!beginSection("provided_trim", providedTrimMaterial)) return;
        if (beginFields("provided_trim_fields")) {
            fieldLabel(tr("equipment.provided_trim.material"), tr("equipment.provided_trim.material.help"));
            String current = primitiveString(providedTrimMaterial, "minecraft:iron");
            ImString material = buffer("provided_trim/material", current);
            applyPendingFieldFocus("provided_trim", "provided_trim/material");
            if (host.inputIdentifierWithSuggestions(
                "provided_trim_material",
                new FieldHints(tr("field_purpose.trim_material_identifier"), "minecraft:iron"),
                material,
                trimMaterials
            )) {
                providedTrimMaterial.setValue(new JsonPrimitive(material.get().trim()));
                dirty = true;
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawBreakSound(Minecraft client) {
        if (!beginSection("break_sound", breakSound)) return;
        if (beginFields("break_sound_fields")) {
            fieldLabel(tr("equipment.break_sound.sound"), tr("equipment.break_sound.sound.help"));
            String current = primitiveString(breakSound, "minecraft:entity.item.break");
            ImString sound = buffer("break_sound", current);
            if (host.inputSoundWithPreview(
                client,
                "break_sound",
                new FieldHints(
                    tr("field_purpose.sound_identifier"),
                    "minecraft:entity.item.break"
                ),
                sound,
                sounds,
                SoundPreviewKind.SOUND_EVENT,
                "",
                consumeSoundFocus("break_sound", "break_sound")
            )) {
                breakSound.setValue(new JsonPrimitive(sound.get().trim()));
                dirty = true;
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void booleanRow(
        CodecComponentState state,
        JsonObject value,
        String key,
        String label,
        String description,
        boolean fallback
    ) {
        if (!state.supportsField(key)) return;
        fieldLabel(label, description);
        ImBoolean enabled = new ImBoolean(bool(value, key, fallback));
        if (ImGui.checkbox("##" + key, enabled)) {
            value.addProperty(key, enabled.get());
            dirty = true;
        }
    }

    private void identifier(JsonObject owner, String key, String bufferKey, String fallback, List<IdSuggestion> suggestions) {
        String current = string(owner, key, fallback);
        ImString input = buffer(bufferKey, current);
        String purpose = key.equals("material")
            ? tr("field_purpose.trim_material_identifier")
            : key.equals("pattern")
            ? tr("field_purpose.trim_pattern_identifier")
            : tr("field_purpose.registered_value");
        applyPendingFieldFocus(bufferKey.startsWith("trim/") ? "trim" : "equippable", bufferKey);
        if (host.inputIdentifierWithSuggestions(key, new FieldHints(purpose, fallback), input, suggestions)) {
            owner.addProperty(key, input.get().trim());
            dirty = true;
        }
    }

    private void soundIdentifier(
        Minecraft client,
        JsonObject owner,
        String key,
        String bufferKey,
        String fallback
    ) {
        String current = string(owner, key, fallback);
        ImString input = buffer(bufferKey, current);
        if (host.inputSoundWithPreview(
            client,
            key,
            new FieldHints(tr("field_purpose.sound_identifier"), fallback),
            input,
            sounds,
            SoundPreviewKind.SOUND_EVENT,
            "",
            consumeSoundFocus("equippable", bufferKey)
        )) {
            owner.addProperty(key, input.get().trim());
            dirty = true;
        }
    }

    private void optionalIdentifier(
        JsonObject owner,
        String key,
        String bufferKey,
        String purposeHint,
        List<IdSuggestion> suggestions
    ) {
        String current = string(owner, key, "");
        ImString input = buffer(bufferKey, current);
        applyPendingFieldFocus("equippable", bufferKey);
        if (host.inputIdentifierWithSuggestions(
            key,
            FieldHints.purpose(purposeHint),
            input,
            suggestions
        )) {
            if (input.get().isBlank()) owner.remove(key);
            else owner.addProperty(key, input.get().trim());
            dirty = true;
        }
    }

    private boolean consumeSoundFocus(String section, String field) {
        if (!consumePendingFieldFocus(section, field)) return false;
        ImGui.setScrollHereY(0.35f);
        return true;
    }

}
