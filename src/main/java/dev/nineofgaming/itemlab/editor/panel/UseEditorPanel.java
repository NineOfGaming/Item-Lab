package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.list.JsonListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.sound.SoundPreviewKind;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.array;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.bool;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.indexOf;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.integer;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.number;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.primitiveInt;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.panel.StatusEffectEditorSupport.durationSummary;
import static dev.nineofgaming.itemlab.editor.panel.StatusEffectEditorSupport.effectName;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;

/**
 * Purpose-built editor for the components involved in using and consuming an item.
 */
public final class UseEditorPanel extends ComponentEditorPanel {
    private static final AtomicLong NEXT_LIST_ROW_ID = new AtomicLong();
    private static final String[] ANIMATION_IDS = {
        "none", "eat", "drink", "block", "bow", "trident", "crossbow", "spyglass", "toot_horn", "brush", "bundle", "spear"
    };
    private static final String[] EFFECT_IDS = {
        "minecraft:apply_effects", "minecraft:remove_effects", "minecraft:clear_all_effects",
        "minecraft:teleport_randomly", "minecraft:play_sound"
    };

    private final CodecComponentState food = new CodecComponentState(
        DataComponents.FOOD,
        "{\"nutrition\":1,\"saturation\":0.1,\"can_always_eat\":false}"
    );
    private final CodecComponentState consumable = new CodecComponentState(
        DataComponents.CONSUMABLE,
        "{\"consume_seconds\":1.6,\"animation\":\"eat\",\"sound\":\"minecraft:entity.generic.eat\",\"has_consume_particles\":true,\"on_consume_effects\":[]}"
    );
    private final CodecComponentState useEffects = new CodecComponentState(
        DataComponents.USE_EFFECTS,
        "{\"can_sprint\":false,\"interact_vibrations\":false,\"speed_multiplier\":0.2}"
    );
    private final CodecComponentState remainder = new CodecComponentState(
        DataComponents.USE_REMAINDER,
        "{\"id\":\"minecraft:glass_bottle\",\"count\":1}"
    );
    // The item/count controls do not edit the remainder's opaque component patch.
    // Keep its native types, which a JSON round trip cannot represent losslessly.
    private JsonElement remainderComponentsJson;
    private Tag remainderComponentsNbt;
    private final CodecComponentState cooldown = new CodecComponentState(
        DataComponents.USE_COOLDOWN,
        "{\"seconds\":1.0}"
    );
    private final CodecComponentState stewEffects = new CodecComponentState(
        DataComponents.SUSPICIOUS_STEW_EFFECTS,
        "[]"
    );
    private final CodecComponentState ominousAmplifier = new CodecComponentState(
        DataComponents.OMINOUS_BOTTLE_AMPLIFIER,
        "0"
    );
    private final CodecComponentState deathProtection = new CodecComponentState(
        DataComponents.DEATH_PROTECTION,
        "{\"death_effects\":[]}"
    );
    private final ImInt newConsumeEffectType = new ImInt(0);
    private final ImInt newDeathEffectType = new ImInt(0);
    private final Map<String, List<Long>> rowIdsByList = new HashMap<>();
    private OrderedListDragDrop.Scope stewEffectDragScope = OrderedListDragDrop.newScope("use/stew_effects");
    private OrderedListDragDrop.Scope consumeEffectDragScope = OrderedListDragDrop.newScope("use/consume_effects");
    private OrderedListDragDrop.Scope deathEffectDragScope = OrderedListDragDrop.newScope("use/death_effects");
    private List<IdSuggestion> itemIds = List.of();
    private List<IdSuggestion> soundIds = List.of();
    private List<IdSuggestion> effectIds = List.of();
    private String focusListId;
    private long focusListRowId;
    private String focusAddListId;

    public UseEditorPanel(ItemEditor host) {
        super(host, "use");
    }

    @Override
    public void requestPendingFocus() {
        clearRowFocus();
        focusAddListId = null;
        CodecComponentState[] states = {food, consumable, useEffects, remainder, cooldown, stewEffects, ominousAmplifier, deathProtection};
        String[] sections = {"food", "consumable", "use_effects", "use_remainder", "use_cooldown", "stew_effects", "ominous_amplifier", "death_protection"};
        Minecraft client = Minecraft.getInstance();
        for (int index = 0; index < states.length; index++) {
            CodecComponentState state = states[index];
            if (!state.present()) continue;
            String field = invalidUseField(sections[index], state.value());
            if (field != null) {
                requestPendingField(sections[index], field);
                return;
            }
            if (client.level != null) {
                try {
                    if (state == remainder) applyRemainder(client, host.draft().copy());
                    else state.apply(client, host.draft().copy());
                } catch (RuntimeException ignored) {
                    requestPendingField(sections[index], "$enabled");
                    return;
                }
            }
        }
        for (int index = 0; index < states.length; index++) {
            if (states[index].present()) {
                requestPendingField(sections[index], "$enabled");
                return;
            }
        }
        requestPendingField("food", "$enabled");
    }

    private String invalidUseField(String section, JsonElement source) {
        if (section.equals("ominous_amplifier")) {
            return invalidCodecValue(DataComponents.OMINOUS_BOTTLE_AMPLIFIER.codec(), source) ? "value" : null;
        }
        if (section.equals("stew_effects")) {
            if (!source.isJsonArray()) return "$enabled";
            JsonArray entries = source.getAsJsonArray();
            List<Long> rowIds = rowIdsFor("stew", entries.size());
            for (int index = 0; index < entries.size(); index++) {
                if (!entries.get(index).isJsonObject()) return "$enabled";
                JsonObject entry = entries.get(index).getAsJsonObject();
                String path = "stew_effect/" + rowIds.get(index);
                if (invalidEffectId(pendingBufferText(path + "/id", string(entry, "id", "")))) {
                    focusRow("stew", rowIds.get(index));
                    return path + "/id";
                }
                // Stew's duration is lenientOptionalFieldOf(INT, 160); malformed values use its native default.
            }
            return null;
        }
        if (!source.isJsonObject()) return "$enabled";
        JsonObject value = source.getAsJsonObject();
        return switch (section) {
            case "food" -> {
                if (!value.has("nutrition") || invalidCodecField(value, "nutrition", ExtraCodecs.NON_NEGATIVE_INT))
                    yield "nutrition";
                if (!value.has("saturation") || invalidCodecField(value, "saturation", Codec.FLOAT)) yield "saturation";
                yield invalidCodecField(value, "can_always_eat", Codec.BOOL) ? "can_always_eat" : null;
            }
            case "consumable" -> {
                if (invalidCodecField(value, "consume_seconds", ExtraCodecs.NON_NEGATIVE_FLOAT))
                    yield "consume_seconds";
                if (invalidCodecField(value, "animation", ItemUseAnimation.CODEC)) yield "animation";
                if (invalidSound(value.get("sound"), "consumable/sound", false)) yield "sound";
                if (invalidCodecField(value, "has_consume_particles", Codec.BOOL)) yield "has_consume_particles";
                yield invalidConsumeEffects(value.get("on_consume_effects"), "consume");
            }
            case "use_effects" -> {
                if (invalidCodecField(value, "can_sprint", Codec.BOOL)) yield "can_sprint";
                if (invalidCodecField(value, "interact_vibrations", Codec.BOOL)) yield "interact_vibrations";
                yield invalidCodecField(value, "speed_multiplier", Codec.floatRange(0.0f, 1.0f)) ? "speed_multiplier" : null;
            }
            case "use_remainder" -> {
                Identifier id = Identifier.tryParse(pendingBufferText("remainder/id", string(value, "id", "")).trim());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) yield "id";
                yield invalidCodecField(value, "count", ExtraCodecs.intRange(1, 99)) ? "count" : null;
            }
            case "use_cooldown" -> {
                if (!value.has("seconds") || invalidCodecField(value, "seconds", ExtraCodecs.POSITIVE_FLOAT))
                    yield "seconds";
                String group = pendingBufferText("cooldown/group", string(value, "cooldown_group", "")).trim();
                yield (value.has("cooldown_group") || !group.isBlank()) && Identifier.tryParse(group) == null ? "cooldown_group" : null;
            }
            case "death_protection" -> invalidConsumeEffects(value.get("death_effects"), "death_protection");
            default -> null;
        };
    }

    private String invalidConsumeEffects(JsonElement source, String listId) {
        if (source == null) return null;
        if (!source.isJsonArray()) return "$enabled";
        JsonArray entries = source.getAsJsonArray();
        List<Long> rowIds = rowIdsFor(listId, entries.size());
        for (int index = 0; index < entries.size(); index++) {
            if (!entries.get(index).isJsonObject()) return "$enabled";
            JsonObject effect = entries.get(index).getAsJsonObject();
            String path = listId + "/" + rowIds.get(index);
            Identifier type = Identifier.tryParse(string(effect, "type", ""));
            String typeId = type == null ? "" : type.toString();
            String field = null;
            if (!List.of(EFFECT_IDS).contains(typeId)) field = path + "/type";
            else if (typeId.equals(EFFECT_IDS[0])) {
                if (invalidCodecField(effect, "probability", Codec.floatRange(0.0f, 1.0f)))
                    field = path + "/probability";
                else {
                    JsonElement statuses = effect.get("effects");
                    if (statuses == null || !statuses.isJsonArray()) field = path + "/type";
                    else {
                        String statusList = path + "/status";
                        JsonArray values = statuses.getAsJsonArray();
                        List<Long> statusRows = rowIdsFor(statusList, values.size());
                        for (int statusIndex = 0; statusIndex < values.size(); statusIndex++) {
                            if (!values.get(statusIndex).isJsonObject()) {
                                field = path + "/type";
                                break;
                            }
                            JsonObject status = values.get(statusIndex).getAsJsonObject();
                            String statusPath = statusList + "/" + statusRows.get(statusIndex);
                            if (invalidEffectId(pendingBufferText(statusPath, string(status, "id", ""))))
                                field = statusPath + "/id";
                            else if (invalidCodecField(status, "duration", Codec.INT)) field = statusPath + "/duration";
                            else if (invalidCodecField(status, "amplifier", ExtraCodecs.UNSIGNED_BYTE))
                                field = statusPath + "/amplifier";
                            else if (invalidRegistryValue(MobEffectInstance.CODEC, status)) field = path + "/type";
                            if (field != null) break;
                        }
                    }
                }
            } else if (typeId.equals(EFFECT_IDS[1])) {
                JsonElement removed = effect.get("effects");
                if (removed != null && removed.isJsonArray()) {
                    JsonArray values = removed.getAsJsonArray();
                    String removeList = path + "/remove_effect";
                    List<Long> removeRows = rowIdsFor(removeList, values.size());
                    for (int removeIndex = 0; removeIndex < values.size(); removeIndex++) {
                        String removePath = removeList + "/" + removeRows.get(removeIndex);
                        if (invalidEffectId(pendingBufferText(removePath, string(values.get(removeIndex), "")))) {
                            field = removePath + "/id";
                            break;
                        }
                    }
                }
                // Scalar holder-set identifiers/tags are native representations, not editable list rows.
                if (field == null && (removed == null || invalidRegistryValue(ConsumeEffect.CODEC, effect)))
                    field = path + "/type";
            } else if (typeId.equals(EFFECT_IDS[3])) {
                if (invalidCodecField(effect, "diameter", ExtraCodecs.POSITIVE_FLOAT)) field = path + "/diameter";
            } else if (typeId.equals(EFFECT_IDS[4]) && invalidSound(effect.get("sound"), path + "/sound", true))
                field = path + "/sound";
            if (field != null) {
                focusRow(listId, rowIds.get(index));
                return field;
            }
        }
        return null;
    }

    private static boolean invalidRegistryValue(Codec<?> codec, JsonElement source) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;
        try {
            return codec.parse(ItemStackCodecs.jsonOps(client), source).error().isPresent();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private boolean invalidSound(JsonElement value, String path, boolean required) {
        ImString pending = textBuffers.get(path);
        if (pending == null && value == null) return required;
        if (pending == null && value.isJsonObject()) return invalidCodecValue(SoundEvent.DIRECT_CODEC, value);
        Identifier id = Identifier.tryParse(pendingBufferText(path, string(value, "")).trim());
        return id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }

    private static boolean invalidEffectId(String source) {
        Identifier id = Identifier.tryParse(source.trim());
        return id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id);
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.FOOD
            || type == DataComponents.CONSUMABLE
            || type == DataComponents.USE_EFFECTS
            || type == DataComponents.USE_REMAINDER
            || type == DataComponents.USE_COOLDOWN
            || type == DataComponents.SUSPICIOUS_STEW_EFFECTS
            || type == DataComponents.OMINOUS_BOTTLE_AMPLIFIER
            || type == DataComponents.DEATH_PROTECTION;
    }

    public void setCatalog(EditorCatalog catalog) {
        itemIds = catalog.itemIds();
        soundIds = catalog.soundIds();
        effectIds = catalog.mobEffectIds();
    }

    public void sync(Minecraft client, ItemStack draft) {
        food.sync(client, draft);
        consumable.sync(client, draft);
        useEffects.sync(client, draft);
        syncRemainder(client, draft);
        cooldown.sync(client, draft);
        stewEffects.sync(client, draft);
        ominousAmplifier.sync(client, draft);
        deathProtection.sync(client, draft);
        resetListRows();
        resetEditorUi();
        newConsumeEffectType.set(0);
        newDeathEffectType.set(0);
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack draft) {
        if (type == DataComponents.FOOD) food.sync(client, draft);
        else if (type == DataComponents.CONSUMABLE) consumable.sync(client, draft);
        else if (type == DataComponents.USE_EFFECTS) useEffects.sync(client, draft);
        else if (type == DataComponents.USE_REMAINDER) syncRemainder(client, draft);
        else if (type == DataComponents.USE_COOLDOWN) cooldown.sync(client, draft);
        else if (type == DataComponents.SUSPICIOUS_STEW_EFFECTS) stewEffects.sync(client, draft);
        else if (type == DataComponents.OMINOUS_BOTTLE_AMPLIFIER) ominousAmplifier.sync(client, draft);
        else if (type == DataComponents.DEATH_PROTECTION) deathProtection.sync(client, draft);
        if (type == DataComponents.CONSUMABLE
            || type == DataComponents.SUSPICIOUS_STEW_EFFECTS
            || type == DataComponents.DEATH_PROTECTION) {
            resetListRows();
        } else {
            resetDragScopes();
        }
        resetEditorUi();
        clearRowFocus();
        focusAddListId = null;
        dirty = food.differsFrom(client, draft)
            || consumable.differsFrom(client, draft)
            || useEffects.differsFrom(client, draft)
            || remainder.differsFrom(client, draft)
            || cooldown.differsFrom(client, draft)
            || stewEffects.differsFrom(client, draft)
            || ominousAmplifier.differsFrom(client, draft)
            || deathProtection.differsFrom(client, draft);
    }

    @Override
    protected boolean hasPendingChanges() {
        Minecraft client = Minecraft.getInstance();
        ItemStack draft = host.draft();
        return food.differsFrom(client, draft)
            || consumable.differsFrom(client, draft)
            || useEffects.differsFrom(client, draft)
            || remainder.differsFrom(client, draft)
            || cooldown.differsFrom(client, draft)
            || stewEffects.differsFrom(client, draft)
            || ominousAmplifier.differsFrom(client, draft)
            || deathProtection.differsFrom(client, draft);
    }

    public void draw(Minecraft client) {
        if (drawToolbar(client, null)) return;
        EditorUi.beginScrollableChild("use_editor_sections", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            drawFood();
            ImGui.spacing();
            drawConsumable(client);
            ImGui.spacing();
            drawStewEffects();
            ImGui.spacing();
            drawOminousAmplifier();
            ImGui.spacing();
            drawUseEffects();
            ImGui.spacing();
            drawRemainder();
            ImGui.spacing();
            drawCooldown();
            ImGui.spacing();
            drawDeathProtection();
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (drawToolbar(client, type)) return;
        if (type == DataComponents.FOOD) drawFood();
        else if (type == DataComponents.CONSUMABLE) drawConsumable(client);
        else if (type == DataComponents.USE_EFFECTS) drawUseEffects();
        else if (type == DataComponents.USE_REMAINDER) drawRemainder();
        else if (type == DataComponents.USE_COOLDOWN) drawCooldown();
        else if (type == DataComponents.SUSPICIOUS_STEW_EFFECTS) drawStewEffects();
        else if (type == DataComponents.OMINOUS_BOTTLE_AMPLIFIER) drawOminousAmplifier();
        else if (type == DataComponents.DEATH_PROTECTION) drawDeathProtection();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        ItemStack edited = host.draft().copy();
        food.apply(client, edited);
        consumable.apply(client, edited);
        useEffects.apply(client, edited);
        applyRemainder(client, edited);
        cooldown.apply(client, edited);
        stewEffects.apply(client, edited);
        ominousAmplifier.apply(client, edited);
        deathProtection.apply(client, edited);
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    private void syncRemainder(Minecraft client, ItemStack draft) {
        remainder.sync(client, draft);
        remainderComponentsJson = null;
        remainderComponentsNbt = null;
        if (!remainder.present() || !remainder.value().isJsonObject()) return;
        JsonElement components = remainder.value().getAsJsonObject().get("components");
        Tag encoded = ItemStackCodecs.encodeComponentNbt(client, DataComponents.USE_REMAINDER, draft.get(DataComponents.USE_REMAINDER));
        if (components != null && encoded instanceof CompoundTag compound && compound.contains("components")) {
            remainderComponentsJson = components.deepCopy();
            remainderComponentsNbt = Objects.requireNonNull(compound.get("components")).copy();
        }
    }

    private void applyRemainder(Minecraft client, ItemStack edited) {
        if (remainder.present() && !remainder.hasNbtValue() && remainderComponentsNbt != null
            && remainder.value().isJsonObject()
            && remainderComponentsJson.equals(remainder.value().getAsJsonObject().get("components"))) {
            CompoundTag encoded = ItemStackCodecs.jsonObjectToNbt(remainder.value().getAsJsonObject());
            encoded.put("components", remainderComponentsNbt.copy());
            ItemStackCodecs.decodeAndSetComponentNbt(client, edited, DataComponents.USE_REMAINDER, encoded);
        } else {
            // Changed component sources still pass through the native codec; never mask invalid edits.
            remainder.apply(client, edited);
        }
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

    private void drawFood() {
        if (!beginSection("food", food)) return;
        JsonObject value = food.object();
        if (beginFields("food_fields")) {
            if (food.supportsField("nutrition")) {
                fieldLabel(tr("use.food.nutrition"), tr("use.food.nutrition.help"));
                ImInt nutrition = new ImInt(integer(value, "nutrition", 1));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("food", "nutrition");
                if (ImGui.inputInt("##food_nutrition", nutrition, 1, 2)) {
                    value.addProperty("nutrition", Math.max(0, nutrition.get()));
                    dirty = true;
                }
            }
            if (food.supportsField("saturation")) {
                fieldLabel(tr("use.food.saturation"), tr("use.food.saturation.help"));
                ImDouble saturation = new ImDouble(number(value, "saturation", 0.1));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("food", "saturation");
                if (ImGui.inputDouble("##food_saturation", saturation, 0.1, 0.5, "%.2f")) {
                    value.addProperty("saturation", Math.max(0.0, saturation.get()));
                    dirty = true;
                }
            }
            if (food.supportsField("can_always_eat")) {
                fieldLabel(tr("use.food.always_edible"), tr("use.food.always_edible.help"));
                applyPendingFieldFocus("food", "can_always_eat");
                dirty |= booleanField(value, "can_always_eat", false);
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawConsumable(Minecraft client) {
        if (!beginSection("consumable", consumable)) return;
        JsonObject value = consumable.object();
        if (beginFields("consumable_fields")) {
            if (consumable.supportsField("consume_seconds")) {
                fieldLabel(tr("use.consumable.time"), tr("use.consumable.time.help"));
                ImDouble seconds = new ImDouble(number(value, "consume_seconds", 1.6));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("consumable", "consume_seconds");
                if (ImGui.inputDouble("##consume_seconds", seconds, 0.1, 0.5, "%.2f")) {
                    value.addProperty("consume_seconds", Math.max(0.0, seconds.get()));
                    dirty = true;
                }
            }
            if (consumable.supportsField("animation")) {
                fieldLabel(tr("use.consumable.animation"), tr("use.consumable.animation.help"));
                ImInt animation = new ImInt(indexOf(ANIMATION_IDS, string(value, "animation", "eat")));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("consumable", "animation");
                if (ImGui.combo("##consume_animation", animation, animationLabels())) {
                    value.addProperty("animation", ANIMATION_IDS[animation.get()]);
                    dirty = true;
                }
            }
            if (consumable.supportsField("sound")) {
                fieldLabel(tr("use.consumable.sound"), tr("use.consumable.sound.help"));
                ImString sound = buffer("consumable/sound", string(value, "sound", "minecraft:entity.generic.eat"));
                boolean requestFocus = consumePendingFieldFocus("consumable", "sound");
                if (requestFocus) ImGui.setScrollHereY(0.35f);
                if (host.inputSoundWithPreview(
                    client,
                    "consume_sound",
                    new FieldHints(
                        tr("field_purpose.sound_identifier"),
                        "minecraft:entity.generic.eat"
                    ),
                    sound,
                    soundIds,
                    SoundPreviewKind.SOUND_EVENT,
                    "",
                    requestFocus
                )) {
                    value.addProperty("sound", sound.get().trim());
                    dirty = true;
                }
            }
            if (consumable.supportsField("has_consume_particles")) {
                fieldLabel(tr("use.consumable.particles"), tr("use.consumable.particles.help"));
                applyPendingFieldFocus("consumable", "has_consume_particles");
                dirty |= booleanField(value, "has_consume_particles", true);
            }
            ImGui.endTable();
        }
        if (consumable.supportsField("on_consume_effects")) {
            ImGui.spacing();
            drawConsumeEffects(value);
        }
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(consumeEffectDragScope);
    }

    private void drawStewEffects() {
        if (!beginSection("stew_effects", stewEffects)) return;
        JsonArray effects = stewEffects.array();
        String listId = "stew";
        List<Long> rowIds = rowIdsFor(listId, effects.size());
        drawAddStewEffectAction(effects, rowIds, listId, "top", false);
        ImGui.spacing();
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < effects.size(); index++) {
            JsonElement element = effects.get(index);
            if (!element.isJsonObject()) continue;
            JsonObject effect = element.getAsJsonObject();
            long rowId = rowIds.get(index);
            ImGui.pushID("stew_effect_" + rowId);
            boolean requestFocus = listId.equals(focusListId) && focusListRowId == rowId;
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "stew_effect",
                tr("use.stew.effect.entry", index + 1, effectName(effect)),
                ImGuiTableFlags.SizingStretchProp,
                index,
                effects.size(),
                true,
                true,
                true,
                requestFocus,
                stewEffectDragScope,
                rowId
            );
            if (card.focusApplied()) clearRowFocus();
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = card.action();
            }
            if (card.expanded()) {
                drawStewEffectFields(effect, rowId);
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                stewEffectDragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        long removedRowId = reorderRequest == null ? removedRowId(rowIds, actionIndex, action) : 0L;
        JsonListMutations.Mutation mutation = reorderRequest == null
            ? JsonListMutations.apply(
            effects,
            rowIds,
            actionIndex,
            action,
            NEXT_LIST_ROW_ID::incrementAndGet,
            UseEditorPanel::defaultStewEffect
        )
            : stewEffectDragScope.equals(reorderRequest.scope())
            ? JsonListMutations.reorder(effects, rowIds, reorderRequest)
            : new JsonListMutations.Mutation(false, 0L);
        if (mutation.changed()) {
            if (removedRowId != 0L) removeListRows(listId + "/" + removedRowId + "/");
            applyListFocus(listId, mutation);
            dirty = true;
        }
        if (effects.isEmpty()) EditorUi.drawEmptyState(tr("use.stew.effects.empty"));
        drawAddStewEffectAction(effects, rowIds, listId, "bottom", true);
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(stewEffectDragScope);
    }

    private void drawAddStewEffectAction(
        JsonArray effects,
        List<Long> rowIds,
        String listId,
        String placement,
        boolean acceptFocus
    ) {
        if (acceptFocus) applyAddFocus(listId);
        if (EditorIcons.button(
            "add_stew_effect_" + placement,
            EditorIcons.Icon.ADD,
            tr("use.stew.effects.add")
        )) {
            effects.add(defaultStewEffect());
            long rowId = NEXT_LIST_ROW_ID.incrementAndGet();
            rowIds.add(rowId);
            focusRow(listId, rowId);
            resetEditorUi();
            dirty = true;
        }
    }

    private void drawStewEffectFields(JsonObject effect, long rowId) {
        if (!beginFields("stew_effect_fields")) return;
        fieldLabel(tr("potion.effect.id"), tr("potion.effect.id.help"));
        ImString id = buffer("stew_effect/" + rowId + "/id", string(effect, "id", "minecraft:speed"));
        applyPendingFieldFocus("stew_effects", "stew_effect/" + rowId + "/id");
        if (host.inputIdentifierWithSuggestions(
            "stew_effect_id",
            new FieldHints(tr("field_purpose.effect_identifier"), "minecraft:speed"),
            id,
            effectIds
        )) {
            effect.addProperty("id", id.get().trim());
            dirty = true;
        }

        fieldLabel(tr("potion.effect.duration"), tr("potion.effect.duration.help"));
        ImInt duration = new ImInt(integer(effect, "duration", 160));
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("stew_effects", "stew_effect/" + rowId + "/duration");
        if (ImGui.inputInt("##stew_duration", duration, 20, 200)) {
            effect.addProperty("duration", Math.max(-1, duration.get()));
            dirty = true;
        }
        EditorUi.textDisabledWrapped(durationSummary(Math.max(-1, duration.get())));
        ImGui.endTable();
    }

    private void drawOminousAmplifier() {
        if (!beginSection("ominous_amplifier", ominousAmplifier)) return;
        if (beginFields("ominous_amplifier_fields")) {
            fieldLabel(tr("use.ominous.level"), tr("use.ominous.level.help"));
            ImInt level = new ImInt(Math.clamp(primitiveInt(ominousAmplifier, 0) + 1, 1, 5));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus("ominous_amplifier", "value");
            if (ImGui.inputInt("##ominous_level", level, 1, 1)) {
                ominousAmplifier.setValue(new JsonPrimitive(Math.clamp(level.get(), 1, 5) - 1));
                dirty = true;
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawConsumeEffects(JsonObject owner) {
        drawEffectList(
            owner,
            "on_consume_effects",
            "consume",
            tr("use.after_consuming"),
            newConsumeEffectType,
            consumeEffectDragScope
        );
    }

    private void drawEffectList(
        JsonObject owner,
        String field,
        String scope,
        String heading,
        ImInt newEffectType,
        OrderedListDragDrop.Scope dragScope
    ) {
        ImGui.separatorText(heading);
        String section = scope.equals("consume") ? "consumable" : "death_protection";
        JsonArray effects = array(owner, field);
        List<Long> rowIds = rowIdsFor(scope, effects.size());
        drawAddEffectAction(effects, rowIds, scope, scope, newEffectType, "top", false);
        ImGui.spacing();
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < effects.size(); index++) {
            if (!effects.get(index).isJsonObject()) continue;
            long rowId = rowIds.get(index);
            ImGui.pushID(scope + "_effect_" + rowId);
            JsonObject effect = effects.get(index).getAsJsonObject();
            int effectType = indexOf(EFFECT_IDS, string(effect, "type", EFFECT_IDS[2]));
            boolean requestFocus = scope.equals(focusListId) && focusListRowId == rowId;
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "consume_effect",
                EditorUi.identifiedTitle(
                    tr("use.effect", index + 1),
                    effectLabels()[effectType]
                ),
                ImGuiTableFlags.SizingStretchProp,
                index,
                effects.size(),
                true,
                true,
                true,
                requestFocus,
                dragScope,
                rowId
            );
            if (card.focusApplied()) clearRowFocus();
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = card.action();
            }
            if (card.expanded()) {
                int currentType = effectType;
                ImInt type = new ImInt(currentType);
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus(section, scope + "/" + rowId + "/type");
                if (ImGui.combo("##" + scope + "_effect_type", type, effectLabels())) {
                    effects.set(index, newEffect(type.get()));
                    effect = effects.get(index).getAsJsonObject();
                    currentType = type.get();
                    removeListRows(scope + "/" + rowId + "/");
                    textBuffers.clear();
                    dirty = true;
                }
                drawConsumeEffectFields(
                    Minecraft.getInstance(),
                    section,
                    effect,
                    currentType,
                    scope + "/" + rowId,
                    dragScope,
                    rowId
                );
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                dragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        long removedRowId = reorderRequest == null ? removedRowId(rowIds, actionIndex, action) : 0L;
        JsonListMutations.Mutation mutation = reorderRequest == null
            ? JsonListMutations.apply(
            effects,
            rowIds,
            actionIndex,
            action,
            NEXT_LIST_ROW_ID::incrementAndGet,
            () -> newEffect(newEffectType.get())
        )
            : dragScope.equals(reorderRequest.scope())
            ? JsonListMutations.reorder(effects, rowIds, reorderRequest)
            : new JsonListMutations.Mutation(false, 0L);
        if (mutation.changed()) {
            if (removedRowId != 0L) removeListRows(scope + "/" + removedRowId + "/");
            applyListFocus(scope, mutation);
            dirty = true;
        }
        if (effects.isEmpty()) EditorUi.drawEmptyState(tr("use.effects.empty"));
        drawAddEffectAction(effects, rowIds, scope, scope, newEffectType, "bottom", true);
    }

    private void drawAddEffectAction(
        JsonArray effects,
        List<Long> rowIds,
        String listId,
        String scope,
        ImInt newEffectType,
        String placement,
        boolean acceptFocus
    ) {
        ImGui.setNextItemWidth(Math.clamp(ImGui.getContentRegionAvailX(), 1.0f,
            220.0f));
        ImGui.combo("##new_" + scope + "_effect_" + placement, newEffectType, effectLabels());
        EditorUi.sameLineIfFits(EditorIcons.buttonSize());
        if (acceptFocus) applyAddFocus(listId);
        if (EditorIcons.button(
            "add_" + scope + "_effect_" + placement,
            EditorIcons.Icon.ADD,
            tr("use.add_effect")
        )) {
            effects.add(newEffect(newEffectType.get()));
            long rowId = NEXT_LIST_ROW_ID.incrementAndGet();
            rowIds.add(rowId);
            focusRow(listId, rowId);
            resetEditorUi();
            dirty = true;
        }
    }

    private static String[] animationLabels() {
        return new String[]{
            tr("empty.none"), tr("animation.eat"), tr("animation.drink"), tr("animation.block"),
            tr("animation.bow"), tr("animation.trident"), tr("animation.crossbow"),
            tr("animation.spyglass"), tr("animation.toot_horn"), tr("animation.brush"),
            tr("animation.bundle"), tr("animation.spear")
        };
    }

    private static String[] effectLabels() {
        return new String[]{
            tr("use.effect.apply"), tr("use.effect.remove"), tr("use.effect.clear"),
            tr("use.effect.teleport"), tr("use.effect.sound")
        };
    }

    private void drawConsumeEffectFields(
        Minecraft client,
        String section,
        JsonObject effect,
        int type,
        String effectPath,
        OrderedListDragDrop.Scope parentDragScope,
        long parentRowId
    ) {
        if (type == 0) {
            ImGui.text(tr("use.effect.probability"));
            ImDouble probability = new ImDouble(number(effect, "probability", 1.0));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus(section, effectPath + "/probability");
            if (ImGui.inputDouble(
                "##probability",
                probability,
                0.05,
                0.25,
                "%.2f"
            )) {
                effect.addProperty("probability", Math.clamp(probability.get(), 0.0, 1.0));
                dirty = true;
            }
            JsonArray entries = array(effect, "effects");
            String listId = effectPath + "/status";
            OrderedListDragDrop.Scope dragScope = parentDragScope.nested(parentRowId, "status_effects");
            List<Long> rowIds = rowIdsFor(listId, entries.size());
            int actionIndex = -1;
            EditorUi.ListAction action = EditorUi.ListAction.NONE;
            OrderedListDragDrop.ReorderRequest reorderRequest = null;
            for (int index = 0; index < entries.size(); index++) {
                if (!entries.get(index).isJsonObject()) continue;
                long rowId = rowIds.get(index);
                ImGui.pushID("status_" + rowId);
                JsonObject status = entries.get(index).getAsJsonObject();
                ImString id = buffer(listId + "/" + rowId, string(status, "id", "minecraft:speed"));
                boolean requestFocus = listId.equals(focusListId) && focusListRowId == rowId;
                ImGui.beginGroup();
                OrderedListDragDrop.drawHandle(
                    "reorder_status_effect",
                    dragScope,
                    rowId,
                    effectName(status)
                );
                ImGui.sameLine();
                if (requestFocus) ImGui.setKeyboardFocusHere();
                applyPendingFieldFocus(section, listId + "/" + rowId + "/id");
                if (host.inputIdentifierWithSuggestions(
                    "status_effect",
                    new FieldHints(tr("field_purpose.effect_identifier"), "minecraft:speed"),
                    id,
                    effectIds
                )) {
                    status.addProperty("id", id.get().trim());
                    dirty = true;
                }
                if (requestFocus) clearRowFocus();
                ImInt duration = new ImInt(integer(status, "duration", 200));
                ImInt amplifier = new ImInt(integer(status, "amplifier", 0));
                if (ImGui.beginTable(
                    "status_numbers",
                    2,
                    ImGuiTableFlags.SizingStretchSame | ImGuiTableFlags.NoPadOuterX
                )) {
                    EditorUi.alignedTableFields(
                        new String[]{tr("use.duration_ticks"), tr("use.amplifier")},
                        () -> {
                            ImGui.setNextItemWidth(-1.0f);
                            applyPendingFieldFocus(section, listId + "/" + rowId + "/duration");
                            if (ImGui.inputInt("##duration", duration)) {
                                status.addProperty("duration", Math.max(0, duration.get()));
                                dirty = true;
                            }
                        },
                        () -> {
                            ImGui.setNextItemWidth(-1.0f);
                            applyPendingFieldFocus(section, listId + "/" + rowId + "/amplifier");
                            if (ImGui.inputInt("##amplifier", amplifier)) {
                                status.addProperty("amplifier", Math.clamp(amplifier.get(), 0, 255));
                                dirty = true;
                            }
                        }
                    );
                    ImGui.endTable();
                }
                if (EditorIcons.smallButton(
                    "insert_status_effect",
                    EditorIcons.Icon.ADD,
                    tr("action.insert_below")
                )) {
                    actionIndex = index;
                    action = EditorUi.ListAction.INSERT_AFTER;
                }
                ImGui.sameLine();
                if (EditorIcons.smallButton(
                    "remove_status_effect",
                    EditorIcons.Icon.REMOVE,
                    tr("use.remove_status_effect")
                )) {
                    actionIndex = index;
                    action = EditorUi.ListAction.REMOVE;
                }
                ImGui.endGroup();
                OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                    dragScope,
                    rowId,
                    index
                );
                if (reorderRequest == null) reorderRequest = rowRequest;
                ImGui.popID();
            }
            JsonListMutations.Mutation mutation = reorderRequest == null
                ? JsonListMutations.apply(
                entries,
                rowIds,
                actionIndex,
                action,
                NEXT_LIST_ROW_ID::incrementAndGet,
                UseEditorPanel::defaultStatusEffect
            )
                : dragScope.equals(reorderRequest.scope())
                ? JsonListMutations.reorder(entries, rowIds, reorderRequest)
                : new JsonListMutations.Mutation(false, 0L);
            if (mutation.changed()) {
                applyListFocus(listId, mutation);
                dirty = true;
            }
            if (entries.isEmpty()) EditorUi.drawEmptyState(tr("use.status_effects.empty"));
            applyAddFocus(listId);
            if (EditorIcons.smallButton(
                "add_status_effect",
                EditorIcons.Icon.ADD,
                tr("use.add_status_effect")
            )) {
                entries.add(defaultStatusEffect());
                long rowId = NEXT_LIST_ROW_ID.incrementAndGet();
                rowIds.add(rowId);
                focusRow(listId, rowId);
                resetEditorUi();
                dirty = true;
            }
            OrderedListDragDrop.autoScrollCurrentWindow(dragScope);
        } else if (type == 1) {
            JsonArray ids = array(effect, "effects");
            drawIdentifierList(
                section,
                ids,
                effectPath + "/remove_effect",
                effectIds,
                "minecraft:speed",
                tr("use.status_effect"),
                parentDragScope.nested(parentRowId, "removed_effects")
            );
        } else if (type == 2) {
            EditorUi.textDisabledWrapped(tr("use.clear_effects"));
        } else if (type == 3) {
            ImGui.text(tr("use.effect.diameter"));
            ImDouble diameter = new ImDouble(number(effect, "diameter", 16.0));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus(section, effectPath + "/diameter");
            if (ImGui.inputDouble(
                "##diameter",
                diameter,
                1.0,
                8.0,
                "%.1f"
            )) {
                effect.addProperty("diameter", Math.max(0.01, diameter.get()));
                dirty = true;
            }
        } else {
            ImString sound = buffer(effectPath + "/sound", string(effect, "sound", "minecraft:entity.generic.eat"));
            boolean requestFocus = consumePendingFieldFocus(section, effectPath + "/sound");
            if (requestFocus) ImGui.setScrollHereY(0.35f);
            if (host.inputSoundWithPreview(
                client,
                "effect_sound",
                new FieldHints(
                    tr("field_purpose.sound_identifier"),
                    "minecraft:entity.generic.eat"
                ),
                sound,
                soundIds,
                SoundPreviewKind.SOUND_EVENT,
                "",
                requestFocus
            )) {
                effect.addProperty("sound", sound.get().trim());
                dirty = true;
            }
        }
    }

    private void drawUseEffects() {
        if (!beginSection("use_effects", useEffects)) return;
        JsonObject value = useEffects.object();
        if (beginFields("use_effect_fields")) {
            if (useEffects.supportsField("can_sprint")) {
                fieldLabel(tr("use.behavior.can_sprint"), tr("use.behavior.can_sprint.help"));
                applyPendingFieldFocus("use_effects", "can_sprint");
                dirty |= booleanField(value, "can_sprint", false);
            }
            if (useEffects.supportsField("interact_vibrations")) {
                fieldLabel(tr("use.behavior.vibrations"), tr("use.behavior.vibrations.help"));
                applyPendingFieldFocus("use_effects", "interact_vibrations");
                dirty |= booleanField(value, "interact_vibrations", false);
            }
            if (useEffects.supportsField("speed_multiplier")) {
                fieldLabel(tr("use.behavior.speed"), tr("use.behavior.speed.help"));
                ImDouble speed = new ImDouble(number(value, "speed_multiplier", 0.2));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("use_effects", "speed_multiplier");
                if (ImGui.inputDouble("##use_speed", speed, 0.05, 0.25, "%.2f")) {
                    value.addProperty("speed_multiplier", Math.max(0.0, speed.get()));
                    dirty = true;
                }
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawRemainder() {
        if (!beginSection("use_remainder", remainder)) return;
        JsonObject value = remainder.object();
        if (beginFields("remainder_fields")) {
            if (remainder.supportsField("id")) {
                fieldLabel(tr("use.remainder.item"), tr("use.remainder.item.help"));
                ImString id = buffer("remainder/id", string(value, "id", "minecraft:glass_bottle"));
                applyPendingFieldFocus("use_remainder", "id");
                if (host.inputIdentifierWithSuggestions(
                    "remainder_item",
                    new FieldHints(tr("field_purpose.item_identifier"), "minecraft:glass_bottle"),
                    id,
                    itemIds
                )) {
                    value.addProperty("id", id.get().trim());
                    dirty = true;
                }
            }
            if (remainder.supportsField("count")) {
                fieldLabel(tr("use.remainder.count"), tr("use.remainder.count.help"));
                ImInt count = new ImInt(integer(value, "count", 1));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("use_remainder", "count");
                if (ImGui.inputInt("##remainder_count", count, 1, 8)) {
                    value.addProperty("count", Math.clamp(count.get(), 1, 99));
                    dirty = true;
                }
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawCooldown() {
        if (!beginSection("use_cooldown", cooldown)) return;
        JsonObject value = cooldown.object();
        if (beginFields("cooldown_fields")) {
            if (cooldown.supportsField("seconds")) {
                fieldLabel(tr("use.cooldown.seconds"), tr("use.cooldown.seconds.help"));
                ImDouble seconds = new ImDouble(number(value, "seconds", 1.0));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("use_cooldown", "seconds");
                if (ImGui.inputDouble("##cooldown_seconds", seconds, 0.1, 1.0, "%.2f")) {
                    value.addProperty("seconds", Math.max(0.0, seconds.get()));
                    dirty = true;
                }
            }
            if (cooldown.supportsField("cooldown_group")) {
                fieldLabel(tr("use.cooldown.group"), tr("use.cooldown.group.help"));
                ImString group = buffer("cooldown/group", string(value, "cooldown_group", ""));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("use_cooldown", "cooldown_group");
                if (inputTextWithHint(
                    "##cooldown_group",
                    new FieldHints(
                        tr("field_purpose.cooldown_group_identifier"),
                        "minecraft:food"
                    ),
                    group
                )) {
                    if (group.get().isBlank()) value.remove("cooldown_group");
                    else value.addProperty("cooldown_group", group.get().trim());
                    dirty = true;
                }
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawDeathProtection() {
        if (!beginSection("death_protection", deathProtection)) return;
        drawEffectList(
            deathProtection.object(),
            "death_effects",
            "death_protection",
            tr("use.death_protection.effects"),
            newDeathEffectType,
            deathEffectDragScope
        );
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(deathEffectDragScope);
    }

    private boolean booleanField(JsonObject object, String key, boolean fallback) {
        ImBoolean value = new ImBoolean(bool(object, key, fallback));
        if (!ImGui.checkbox("##" + key, value)) return false;
        object.addProperty(key, value.get());
        return true;
    }

    private void drawIdentifierList(
        String section,
        JsonArray values,
        String path,
        List<IdSuggestion> suggestions,
        String fallback,
        String label,
        OrderedListDragDrop.Scope dragScope
    ) {
        List<Long> rowIds = rowIdsFor(path, values.size());
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < values.size(); index++) {
            long rowId = rowIds.get(index);
            ImGui.pushID("identifier_" + rowId);
            String current = values.get(index).isJsonPrimitive() ? values.get(index).getAsString() : fallback;
            ImString id = buffer(path + "/" + rowId, current);
            String removeLabel = tr("action.remove", label);
            ImGui.beginGroup();
            if (EditorUi.beginInlineIconActionRow("identifier_row", 2)) {
                boolean requestFocus = path.equals(focusListId) && focusListRowId == rowId;
                OrderedListDragDrop.drawHandle("reorder_identifier", dragScope, rowId, current);
                ImGui.sameLine();
                if (requestFocus) ImGui.setKeyboardFocusHere();
                applyPendingFieldFocus(section, path + "/" + rowId + "/id");
                if (host.inputIdentifierWithSuggestions(
                    "identifier",
                    new FieldHints(tr("field_purpose.effect_identifier"), fallback),
                    id,
                    suggestions
                )) {
                    values.set(index, new JsonPrimitive(id.get().trim()));
                    dirty = true;
                }
                if (requestFocus) clearRowFocus();
                ImGui.tableNextColumn();
                if (EditorIcons.smallButton(
                    "insert",
                    EditorIcons.Icon.ADD,
                    tr("action.insert_below")
                )) {
                    actionIndex = index;
                    action = EditorUi.ListAction.INSERT_AFTER;
                }
                ImGui.sameLine();
                if (EditorIcons.smallButton("remove", EditorIcons.Icon.REMOVE, removeLabel)) {
                    actionIndex = index;
                    action = EditorUi.ListAction.REMOVE;
                }
                ImGui.endTable();
            }
            ImGui.endGroup();
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                dragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
        }
        JsonListMutations.Mutation mutation = reorderRequest == null
            ? JsonListMutations.apply(
            values,
            rowIds,
            actionIndex,
            action,
            NEXT_LIST_ROW_ID::incrementAndGet,
            () -> new JsonPrimitive(fallback)
        )
            : dragScope.equals(reorderRequest.scope())
            ? JsonListMutations.reorder(values, rowIds, reorderRequest)
            : new JsonListMutations.Mutation(false, 0L);
        if (mutation.changed()) {
            applyListFocus(path, mutation);
            dirty = true;
        }
        if (values.isEmpty()) EditorUi.drawEmptyState(tr("use.status_effects.empty"));
        applyAddFocus(path);
        if (EditorIcons.smallButton(
            "add_" + path,
            EditorIcons.Icon.ADD,
            tr("action.add", label)
        )) {
            values.add(fallback);
            long rowId = NEXT_LIST_ROW_ID.incrementAndGet();
            rowIds.add(rowId);
            focusRow(path, rowId);
            resetEditorUi();
            dirty = true;
        }
        OrderedListDragDrop.autoScrollCurrentWindow(dragScope);
    }

    private List<Long> rowIdsFor(String listId, int size) {
        List<Long> rowIds = rowIdsByList.computeIfAbsent(listId, ignored -> new ArrayList<>());
        while (rowIds.size() < size) rowIds.add(NEXT_LIST_ROW_ID.incrementAndGet());
        while (rowIds.size() > size) rowIds.removeLast();
        return rowIds;
    }

    private void resetListRows() {
        rowIdsByList.clear();
        resetDragScopes();
        focusListId = null;
        focusListRowId = 0L;
        focusAddListId = null;
    }

    private void resetDragScopes() {
        stewEffectDragScope = OrderedListDragDrop.newScope("use/stew_effects");
        consumeEffectDragScope = OrderedListDragDrop.newScope("use/consume_effects");
        deathEffectDragScope = OrderedListDragDrop.newScope("use/death_effects");
    }

    private void applyListFocus(String listId, JsonListMutations.Mutation mutation) {
        if (mutation.focusRowId() == 0L) {
            clearRowFocus();
            focusAddListId = listId;
        } else {
            focusRow(listId, mutation.focusRowId());
        }
        resetEditorUi();
    }

    private void focusRow(String listId, long rowId) {
        focusListId = listId;
        focusListRowId = rowId;
        focusAddListId = null;
    }

    private void clearRowFocus() {
        focusListId = null;
        focusListRowId = 0L;
    }

    private void applyAddFocus(String listId) {
        if (!listId.equals(focusAddListId)) return;
        ImGui.setKeyboardFocusHere();
        focusAddListId = null;
    }

    private void removeListRows(String prefix) {
        rowIdsByList.keySet().removeIf(listId -> listId.startsWith(prefix));
        if (focusListId != null && focusListId.startsWith(prefix)) clearRowFocus();
        if (focusAddListId != null && focusAddListId.startsWith(prefix)) focusAddListId = null;
    }

    private static long removedRowId(
        List<Long> rowIds,
        int index,
        EditorUi.ListAction action
    ) {
        return action == EditorUi.ListAction.REMOVE && index >= 0 && index < rowIds.size()
            ? rowIds.get(index)
            : 0L;
    }


    private static JsonObject newEffect(int type) {
        JsonObject effect = new JsonObject();
        effect.addProperty("type", EFFECT_IDS[Math.clamp(type, 0, EFFECT_IDS.length - 1)]);
        if (type == 0) {
            JsonArray entries = new JsonArray();
            entries.add(defaultStatusEffect());
            effect.add("effects", entries);
            effect.addProperty("probability", 1.0);
        } else if (type == 1) {
            JsonArray effects = new JsonArray();
            effects.add("minecraft:speed");
            effect.add("effects", effects);
        } else if (type == 3) {
            effect.addProperty("diameter", 16.0);
        } else if (type == 4) {
            effect.addProperty("sound", "minecraft:entity.generic.eat");
        }
        return effect;
    }

    private static JsonObject defaultStatusEffect() {
        JsonObject effect = new JsonObject();
        effect.addProperty("id", "minecraft:speed");
        effect.addProperty("duration", 200);
        effect.addProperty("amplifier", 0);
        return effect;
    }

    private static JsonObject defaultStewEffect() {
        JsonObject effect = new JsonObject();
        effect.addProperty("id", "minecraft:speed");
        effect.addProperty("duration", 160);
        return effect;
    }

}
