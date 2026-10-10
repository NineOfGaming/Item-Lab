package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.list.JsonListMutations;
import dev.nineofgaming.itemlab.editor.ui.control.CodeEditorInput;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeLanguage;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.sound.SoundPreviewKind;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.EditorVersionProfile;
import dev.nineofgaming.itemlab.editor.component.IdentifierCollectionEditor;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.array;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.bool;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.indexOf;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.integer;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.number;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.object;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.primitiveDouble;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.primitiveString;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Purpose-built editor for weapon, tool and attack-related components.
 */
public final class CombatEditorPanel extends ComponentEditorPanel {
    private static final int SOURCE_CAPACITY = 65_536;
    private static final int MAX_CHARGED_PROJECTILES = 1_024;
    private static final AtomicLong NEXT_PROJECTILE_ID = new AtomicLong();
    private static final AtomicLong NEXT_TOOL_RULE_ID = new AtomicLong();
    private static final AtomicLong NEXT_DAMAGE_REDUCTION_ID = new AtomicLong();
    private static final String[] SWING_IDS = {"none", "whack", "stab"};

    private final CodecComponentState minimumCharge = new CodecComponentState(DataComponents.MINIMUM_ATTACK_CHARGE, "1.0");
    private final CodecComponentState damageType = new CodecComponentState(DataComponents.DAMAGE_TYPE, "\"minecraft:player_attack\"");
    private final CodecComponentState swing = new CodecComponentState(
        DataComponents.SWING_ANIMATION,
        "{\"type\":\"whack\",\"duration\":6}"
    );
    private final CodecComponentState weapon = new CodecComponentState(
        DataComponents.WEAPON,
        "{\"item_damage_per_attack\":1,\"disable_blocking_for_seconds\":0.0}"
    );
    private final CodecComponentState piercingWeapon = new CodecComponentState(
        DataComponents.PIERCING_WEAPON,
        "{\"deals_knockback\":true,\"dismounts\":false}"
    );
    private final CodecComponentState kineticWeapon = new CodecComponentState(
        DataComponents.KINETIC_WEAPON,
        "{\"contact_cooldown_ticks\":10,\"delay_ticks\":15,"
            + "\"dismount_conditions\":{\"max_duration_ticks\":100,\"min_speed\":14.0},"
            + "\"knockback_conditions\":{\"max_duration_ticks\":200,\"min_speed\":5.1},"
            + "\"damage_conditions\":{\"max_duration_ticks\":300,\"min_relative_speed\":4.6},"
            + "\"forward_movement\":0.38,\"damage_multiplier\":0.7}"
    );
    private final CodecComponentState chargedProjectiles = new CodecComponentState(
        DataComponents.CHARGED_PROJECTILES,
        "[]"
    );
    private final CodecComponentState tool = new CodecComponentState(
        DataComponents.TOOL,
        "{\"rules\":[],\"default_mining_speed\":1.0,\"damage_per_block\":1,\"can_destroy_blocks_in_creative\":true}"
    );
    private final CodecComponentState range = new CodecComponentState(
        DataComponents.ATTACK_RANGE,
        "{\"min_reach\":0.0,\"max_reach\":3.0,\"min_creative_reach\":0.0,\"max_creative_reach\":5.0,\"hitbox_margin\":0.0,\"mob_factor\":1.0}"
    );
    private final CodecComponentState resistance = new CodecComponentState(
        DataComponents.DAMAGE_RESISTANT,
        "{\"types\":\"minecraft:in_fire\"}"
    );
    private final CodecComponentState repairable = new CodecComponentState(
        DataComponents.REPAIRABLE,
        "{\"items\":\"minecraft:iron_ingot\"}"
    );
    private final CodecComponentState blocksAttacks = new CodecComponentState(
        DataComponents.BLOCKS_ATTACKS,
        "{\"block_delay_seconds\":0.0,\"disable_cooldown_scale\":1.0,\"damage_reductions\":[{\"horizontal_blocking_angle\":90.0,\"base\":0.0,\"factor\":1.0}],\"item_damage\":{\"threshold\":1.0,\"base\":0.0,\"factor\":1.0}}"
    );
    private final CodecComponentState enchantable = new CodecComponentState(DataComponents.ENCHANTABLE, "{\"value\":10}");
    private final List<ProjectileRow> projectileRows = new ArrayList<>();
    private final List<Long> toolRuleRowIds = new ArrayList<>();
    private final List<Long> damageReductionRowIds = new ArrayList<>();
    private OrderedListDragDrop.Scope projectileDragScope = OrderedListDragDrop.newScope("combat/charged_projectiles");
    private OrderedListDragDrop.Scope toolRuleDragScope = OrderedListDragDrop.newScope("combat/tool_rules");
    private OrderedListDragDrop.Scope damageReductionDragScope = OrderedListDragDrop.newScope("combat/damage_reductions");
    private List<IdSuggestion> damageTypeIds = List.of();
    private List<IdSuggestion> blockIds = List.of();
    private List<IdSuggestion> itemIds = List.of();
    private List<IdSuggestion> projectileItemIds = List.of();
    private Set<String> knownItemIds = Set.of();
    private List<IdSuggestion> soundIds = List.of();
    private long focusProjectileRowId;
    private boolean focusAddProjectileButton;
    private long focusToolRuleRowId;
    private boolean focusAddToolRuleButton;
    private long focusDamageReductionRowId;
    private boolean focusAddDamageReductionButton;
    private final Map<String, IdentifierCollectionEditor.FocusState> collectionFocus = new HashMap<>();

    public CombatEditorPanel(ItemEditor host) {
        super(host, "combat");
    }

    @Override
    public void requestPendingFocus() {
        clearCombatRecoveryFocus();
        CodecComponentState[] states = {minimumCharge, damageType, swing, weapon, piercingWeapon, kineticWeapon,
            chargedProjectiles, tool, range, resistance, repairable, blocksAttacks, enchantable};
        String[] sections = {"minimum_charge", "damage_type", "swing", "weapon", "piercing_weapon", "kinetic_weapon",
            "charged_projectiles", "tool", "attack_range", "damage_resistant", "repairable", "blocks_attacks", "enchantable"};
        Minecraft client = Minecraft.getInstance();
        for (int index = 0; index < states.length; index++) {
            CodecComponentState state = states[index];
            if (!state.present()) continue;
            String field = invalidCombatField(sections[index], state.value());
            if (field != null) {
                requestPendingField(sections[index], field);
                return;
            }
            if (client.level != null) {
                try {
                    if (state == chargedProjectiles) {
                        ItemStackCodecs.decodeAndSetComponentNbt(client, host.draft().copy(), state.type(), buildProjectileList(client));
                    } else state.apply(client, host.draft().copy());
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
        requestPendingField("minimum_charge", "$enabled");
    }

    private String invalidCombatField(String section, JsonElement source) {
        switch (section) {
            case "minimum_charge" -> {
                return invalidCodecValue(DataComponents.MINIMUM_ATTACK_CHARGE.codec(), source) ? "value" : null;
            }
            case "enchantable" -> {
                return invalidCodecValue(DataComponents.ENCHANTABLE.codec(), source) ? "value" : null;
            }
            case "damage_type" -> {
                if (source.isJsonObject()) return null;
                String id = pendingBufferText("damage_type", string(source, ""));
                return invalidIdentifier(id, damageRegistry(), damageTypeIds, false) ? "value" : null;
            }
            case "charged_projectiles" -> {
                return invalidProjectileField();
            }
        }
        if (!source.isJsonObject()) return "$enabled";
        JsonObject value = source.getAsJsonObject();
        switch (section) {
            case "swing":
                if (invalidCodecField(value, "type", SwingAnimationType.CODEC)) return "type";
                if (invalidCodecField(value, "duration", ExtraCodecs.POSITIVE_INT)) return "duration";
                break;
            case "weapon":
                if (invalidCodecField(value, "item_damage_per_attack", ExtraCodecs.NON_NEGATIVE_INT))
                    return "item_damage_per_attack";
                if (invalidCodecField(value, "disable_blocking_for_seconds", ExtraCodecs.NON_NEGATIVE_FLOAT))
                    return "disable_blocking_for_seconds";
                break;
            case "piercing_weapon":
                for (String key : List.of("deals_knockback", "dismounts"))
                    if (invalidCodecField(value, key, Codec.BOOL)) return key;
                for (String key : List.of("sound", "hit_sound"))
                    if (invalidOptionalSound(value, key, "piercing_weapon/" + key)) return key;
                break;
            case "kinetic_weapon":
                for (String key : List.of("contact_cooldown_ticks", "delay_ticks"))
                    if (invalidCodecField(value, key, ExtraCodecs.NON_NEGATIVE_INT)) return key;
                for (String key : List.of("forward_movement", "damage_multiplier"))
                    if (invalidCodecField(value, key, Codec.FLOAT)) return key;
                for (String key : List.of("sound", "hit_sound"))
                    if (invalidOptionalSound(value, key, "kinetic_weapon/" + key)) return key;
                for (String key : List.of("dismount_conditions", "knockback_conditions", "damage_conditions")) {
                    if (!value.has(key)) continue;
                    if (!value.get(key).isJsonObject()) return key + "/$enabled";
                    JsonObject condition = value.getAsJsonObject(key);
                    if (!condition.has("max_duration_ticks") || invalidCodecField(condition, "max_duration_ticks", ExtraCodecs.NON_NEGATIVE_INT))
                        return key + "/max_duration_ticks";
                    for (String field : List.of("min_speed", "min_relative_speed"))
                        if (invalidCodecField(condition, field, Codec.FLOAT)) return key + "/" + field;
                }
                break;
            case "tool":
                if (invalidCodecField(value, "default_mining_speed", Codec.FLOAT)) return "default_mining_speed";
                if (invalidCodecField(value, "damage_per_block", ExtraCodecs.NON_NEGATIVE_INT))
                    return "damage_per_block";
                if (invalidCodecField(value, "can_destroy_blocks_in_creative", Codec.BOOL))
                    return "can_destroy_blocks_in_creative";
                if (!value.has("rules")) return "add_rule";
                if (!value.get("rules").isJsonArray()) return "$enabled";
                JsonArray rules = value.getAsJsonArray("rules");
                ensureRowIds(toolRuleRowIds, rules.size(), NEXT_TOOL_RULE_ID);
                for (int index = 0; index < rules.size(); index++) {
                    if (!rules.get(index).isJsonObject()) return "$enabled";
                    JsonObject rule = rules.get(index).getAsJsonObject();
                    long rowId = toolRuleRowIds.get(index);
                    String path = "tool/rule/" + rowId;
                    String field = invalidCollection(rule.get("blocks"), path + "/blocks", BuiltInRegistries.BLOCK, blockIds);
                    if (field == null && invalidCodecField(rule, "speed", ExtraCodecs.POSITIVE_FLOAT))
                        field = path + "/speed";
                    if (field == null && invalidCodecField(rule, "correct_for_drops", Codec.BOOL))
                        field = path + "/correct_for_drops";
                    if (field != null) {
                        focusToolRuleRowId = rowId;
                        return field;
                    }
                }
                break;
            case "attack_range":
                for (String key : List.of("min_reach", "max_reach", "min_creative_reach", "max_creative_reach", "hitbox_margin", "mob_factor")) {
                    if (!value.has(key)) continue;
                    JsonObject probe = new JsonObject();
                    probe.add(key, value.get(key));
                    if (invalidCodecValue(AttackRange.CODEC, probe)) return key;
                }
                break;
            case "damage_resistant":
                return invalidCollection(value.get("types"), "resistance/types", damageRegistry(), damageTypeIds);
            case "repairable":
                return invalidCollection(value.get("items"), "repairable/items", BuiltInRegistries.ITEM, itemIds);
            case "blocks_attacks":
                for (String key : List.of("block_delay_seconds", "disable_cooldown_scale"))
                    if (invalidCodecField(value, key, ExtraCodecs.NON_NEGATIVE_FLOAT)) return key;
                for (String key : List.of("block_sound", "disabled_sound"))
                    if (invalidOptionalSound(value, key, "blocking/" + key)) return key;
                if (value.has("bypassed_by")) {
                    String field = invalidCollection(value.get("bypassed_by"), "blocking/bypassed_by", damageRegistry(), damageTypeIds);
                    if (field != null) return field;
                }
                if (value.has("item_damage")) {
                    if (!value.get("item_damage").isJsonObject()) return "$enabled";
                    JsonObject itemDamage = value.getAsJsonObject("item_damage");
                    for (String key : List.of("threshold", "base", "factor")) {
                        if (!itemDamage.has(key) || invalidCodecField(itemDamage, key, key.equals("threshold") ? ExtraCodecs.NON_NEGATIVE_FLOAT : Codec.FLOAT))
                            return "blocking/item_damage/" + key;
                    }
                }
                if (!value.has("damage_reductions")) break;
                if (!value.get("damage_reductions").isJsonArray()) return "$enabled";
                JsonArray reductions = value.getAsJsonArray("damage_reductions");
                ensureRowIds(damageReductionRowIds, reductions.size(), NEXT_DAMAGE_REDUCTION_ID);
                for (int index = 0; index < reductions.size(); index++) {
                    if (!reductions.get(index).isJsonObject()) return "$enabled";
                    JsonObject reduction = reductions.get(index).getAsJsonObject();
                    long rowId = damageReductionRowIds.get(index);
                    String path = "blocking/reduction/" + rowId;
                    String field = invalidCodecField(reduction, "horizontal_blocking_angle", ExtraCodecs.POSITIVE_FLOAT) ? path + "/horizontal_blocking_angle" : null;
                    if (field == null && reduction.has("type"))
                        field = invalidCollection(reduction.get("type"), path + "/type", damageRegistry(), damageTypeIds);
                    for (String key : List.of("base", "factor"))
                        if (field == null && (!reduction.has(key) || invalidCodecField(reduction, key, Codec.FLOAT)))
                            field = path + "/" + key;
                    if (field != null) {
                        focusDamageReductionRowId = rowId;
                        return field;
                    }
                }
                break;
            default:
                break;
        }
        return null;
    }

    private String invalidProjectileField() {
        int maximum = maximumChargedProjectiles();
        if (projectileRows.size() > maximum) return projectileField(projectileRows.get(maximum), "id");
        Minecraft client = Minecraft.getInstance();
        for (ProjectileRow row : projectileRows) {
            if (row.componentsPresent.get() && !snbtCompoundProblem(row.componentsSource.get()).isBlank())
                return projectileField(row, "components");
            Identifier id = Identifier.tryParse(row.itemId.get().trim());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.getValue(id) == Items.AIR)
                return projectileField(row, "id");
            if (row.count.get() < 1 || row.count.get() > 99) return projectileField(row, "count");
            if (client.level != null && !projectileProblem(client, row).isBlank())
                return projectileField(row, row.componentsPresent.get() ? "components" : "id");
        }
        return null;
    }

    private String projectileField(ProjectileRow row, String field) {
        focusProjectileRowId = row.id;
        if (field.equals("components")) row.componentsEditor.requestFocus();
        return "row/" + row.id + "/" + field;
    }

    private String invalidCollection(JsonElement source, String path, Registry<?> registry, List<IdSuggestion> suggestions) {
        int size = source != null && source.isJsonArray() ? source.getAsJsonArray().size() : 1;
        for (int index = 0; index < size; index++) {
            JsonElement entry = source != null && source.isJsonArray() ? source.getAsJsonArray().get(index) : source;
            String value = pendingBufferText(path + "/@identifier/" + index, string(entry, ""));
            if (invalidIdentifier(value, registry, suggestions, source != null && !source.isJsonArray())) {
                collectionFocus.computeIfAbsent(path, ignored -> new IdentifierCollectionEditor.FocusState()).requestRow(index);
                return path;
            }
        }
        return null;
    }

    private static boolean invalidIdentifier(String source, Registry<?> registry, List<IdSuggestion> suggestions, boolean allowTag) {
        String value = source.trim();
        boolean tag = value.startsWith("#");
        if (tag && !allowTag) return true;
        Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        if (id == null) return true;
        if (!tag && registry != null) return !registry.containsKey(id);
        String canonical = (tag ? "#" : "") + id;
        return !suggestions.isEmpty() && suggestions.stream().noneMatch(suggestion -> suggestion.id().equals(canonical));
    }

    private static Registry<?> damageRegistry() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? null : client.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
    }

    private boolean invalidOptionalSound(JsonObject owner, String key, String path) {
        JsonElement value = owner.get(key);
        String pending = pendingBufferText(path, string(value, ""));
        if (value == null && pending.isBlank()) return false;
        if (value != null && value.isJsonObject()) return invalidCodecValue(SoundEvent.DIRECT_CODEC, value);
        Identifier id = Identifier.tryParse(pending.trim());
        return id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }

    private void clearCombatRecoveryFocus() {
        focusProjectileRowId = 0L;
        focusAddProjectileButton = false;
        focusToolRuleRowId = 0L;
        focusAddToolRuleButton = false;
        focusDamageReductionRowId = 0L;
        focusAddDamageReductionButton = false;
        collectionFocus.values().forEach(IdentifierCollectionEditor.FocusState::clear);
        projectileRows.forEach(row -> row.componentsEditor.cancelFocusRequest());
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.MINIMUM_ATTACK_CHARGE
            || type == DataComponents.DAMAGE_TYPE
            || type == DataComponents.SWING_ANIMATION
            || type == DataComponents.WEAPON
            || type == DataComponents.PIERCING_WEAPON
            || type == DataComponents.KINETIC_WEAPON
            || type == DataComponents.CHARGED_PROJECTILES
            || type == DataComponents.TOOL
            || type == DataComponents.ATTACK_RANGE
            || type == DataComponents.DAMAGE_RESISTANT
            || type == DataComponents.REPAIRABLE
            || type == DataComponents.BLOCKS_ATTACKS
            || type == DataComponents.ENCHANTABLE;
    }

    public void setCatalog(EditorCatalog catalog) {
        damageTypeIds = catalog.registryIdsAndTags("damage_type");
        blockIds = catalog.registryIdsAndTags("block");
        itemIds = catalog.registryIdsAndTags("item");
        projectileItemIds = catalog.itemIds();
        Set<String> knownItems = new HashSet<>();
        projectileItemIds.forEach(suggestion -> knownItems.add(suggestion.id()));
        knownItemIds = Set.copyOf(knownItems);
        soundIds = catalog.soundIds();
    }

    public void sync(Minecraft client, ItemStack draft) {
        minimumCharge.sync(client, draft);
        damageType.sync(client, draft);
        swing.sync(client, draft);
        weapon.sync(client, draft);
        piercingWeapon.sync(client, draft);
        kineticWeapon.sync(client, draft);
        chargedProjectiles.sync(client, draft);
        loadProjectileRows(client, draft);
        tool.sync(client, draft);
        range.sync(client, draft);
        resistance.sync(client, draft);
        repairable.sync(client, draft);
        blocksAttacks.sync(client, draft);
        enchantable.sync(client, draft);
        resetEditorUi();
        resetCombatListUi();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack draft) {
        if (type == DataComponents.MINIMUM_ATTACK_CHARGE) minimumCharge.sync(client, draft);
        else if (type == DataComponents.DAMAGE_TYPE) damageType.sync(client, draft);
        else if (type == DataComponents.SWING_ANIMATION) swing.sync(client, draft);
        else if (type == DataComponents.WEAPON) weapon.sync(client, draft);
        else if (type == DataComponents.PIERCING_WEAPON) piercingWeapon.sync(client, draft);
        else if (type == DataComponents.KINETIC_WEAPON) kineticWeapon.sync(client, draft);
        else if (type == DataComponents.CHARGED_PROJECTILES) {
            chargedProjectiles.sync(client, draft);
            loadProjectileRows(client, draft);
        } else if (type == DataComponents.TOOL) tool.sync(client, draft);
        else if (type == DataComponents.ATTACK_RANGE) range.sync(client, draft);
        else if (type == DataComponents.DAMAGE_RESISTANT) resistance.sync(client, draft);
        else if (type == DataComponents.REPAIRABLE) repairable.sync(client, draft);
        else if (type == DataComponents.BLOCKS_ATTACKS) blocksAttacks.sync(client, draft);
        else if (type == DataComponents.ENCHANTABLE) enchantable.sync(client, draft);
        clearPendingFocus();
        String bufferPrefix = type == DataComponents.DAMAGE_TYPE ? "damage_type"
            : type == DataComponents.PIERCING_WEAPON ? "piercing_weapon/"
            : type == DataComponents.KINETIC_WEAPON ? "kinetic_weapon/"
            : type == DataComponents.TOOL ? "tool/"
            : type == DataComponents.DAMAGE_RESISTANT ? "resistance/"
            : type == DataComponents.REPAIRABLE ? "repairable/"
            : type == DataComponents.BLOCKS_ATTACKS ? "blocking/" : null;
        if (bufferPrefix != null) {
            textBuffers.keySet().removeIf(key -> key.startsWith(bufferPrefix));
            collectionFocus.keySet().removeIf(key -> key.startsWith(bufferPrefix));
        }
        host.resetAutocomplete();
        if (type == DataComponents.TOOL) toolRuleRowIds.clear();
        if (type == DataComponents.BLOCKS_ATTACKS) damageReductionRowIds.clear();
        resetCombatDragScopes();
        clearCombatRecoveryFocus();
        dirty = differsFromDraft(client, draft);
    }

    private boolean differsFromDraft(Minecraft client, ItemStack draft) {
        return minimumCharge.differsFrom(client, draft)
            || damageType.differsFrom(client, draft)
            || swing.differsFrom(client, draft)
            || weapon.differsFrom(client, draft)
            || piercingWeapon.differsFrom(client, draft)
            || kineticWeapon.differsFrom(client, draft)
            || projectilesDifferFromDraft(client, draft)
            || tool.differsFrom(client, draft)
            || range.differsFrom(client, draft)
            || resistance.differsFrom(client, draft)
            || repairable.differsFrom(client, draft)
            || blocksAttacks.differsFrom(client, draft)
            || enchantable.differsFrom(client, draft);
    }

    @Override
    protected boolean hasPendingChanges() {
        return differsFromDraft(Minecraft.getInstance(), host.draft());
    }

    private boolean projectilesDifferFromDraft(Minecraft client, ItemStack draft) {
        Object installed = draft.get(DataComponents.CHARGED_PROJECTILES);
        if (chargedProjectiles.present() != (installed != null)) return true;
        if (!chargedProjectiles.present()) return false;
        try {
            Tag pending = ItemStackCodecs.canonicalizeComponentNbt(client, DataComponents.CHARGED_PROJECTILES, buildProjectileList(client));
            return !pending.equals(ItemStackCodecs.encodeComponentNbt(client, DataComponents.CHARGED_PROJECTILES, installed));
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    public void draw(Minecraft client) {
        if (drawToolbar(client, null)) return;
        EditorUi.beginScrollableChild("combat_editor_sections", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            drawAttackBehavior();
            ImGui.spacing();
            drawWeapon();
            ImGui.spacing();
            drawPiercingWeapon(client);
            ImGui.spacing();
            drawKineticWeapon(client);
            ImGui.spacing();
            drawChargedProjectiles(client);
            ImGui.spacing();
            drawTool();
            ImGui.spacing();
            drawRange();
            ImGui.spacing();
            drawResistance();
            ImGui.spacing();
            drawRepairable();
            ImGui.spacing();
            drawBlocksAttacks(client);
            ImGui.spacing();
            drawEnchantable();
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (drawToolbar(client, type)) return;
        if (type == DataComponents.MINIMUM_ATTACK_CHARGE
            || type == DataComponents.DAMAGE_TYPE
            || type == DataComponents.SWING_ANIMATION) {
            drawAttackBehavior();
        } else if (type == DataComponents.WEAPON) drawWeapon();
        else if (type == DataComponents.PIERCING_WEAPON) drawPiercingWeapon(client);
        else if (type == DataComponents.KINETIC_WEAPON) drawKineticWeapon(client);
        else if (type == DataComponents.CHARGED_PROJECTILES) drawChargedProjectiles(client);
        else if (type == DataComponents.TOOL) drawTool();
        else if (type == DataComponents.ATTACK_RANGE) drawRange();
        else if (type == DataComponents.DAMAGE_RESISTANT) drawResistance();
        else if (type == DataComponents.REPAIRABLE) drawRepairable();
        else if (type == DataComponents.BLOCKS_ATTACKS) drawBlocksAttacks(client);
        else if (type == DataComponents.ENCHANTABLE) drawEnchantable();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        ItemStack edited = host.draft().copy();
        minimumCharge.apply(client, edited);
        damageType.apply(client, edited);
        swing.apply(client, edited);
        weapon.apply(client, edited);
        piercingWeapon.apply(client, edited);
        kineticWeapon.apply(client, edited);
        if (chargedProjectiles.present()) {
            ItemStackCodecs.decodeAndSetComponentNbt(client, edited, DataComponents.CHARGED_PROJECTILES, buildProjectileList(client));
        } else chargedProjectiles.apply(client, edited);
        tool.apply(client, edited);
        range.apply(client, edited);
        resistance.apply(client, edited);
        repairable.apply(client, edited);
        blocksAttacks.apply(client, edited);
        enchantable.apply(client, edited);
        ItemStackCodecs.validateStack(edited);
        return edited;
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

    private void drawAttackBehavior() {
        if (!EditorUi.beginCollapsibleCard(
            "combat_behavior",
            tr("combat.attack_behavior"),
            ImGuiTableFlags.SizingStretchProp,
            host.componentSectionStartsExpanded(
                DataComponents.MINIMUM_ATTACK_CHARGE,
                DataComponents.DAMAGE_TYPE,
                DataComponents.SWING_ANIMATION
            ),
            hasPendingSectionFocus("minimum_charge") || hasPendingSectionFocus("damage_type") || hasPendingSectionFocus("swing")
        )) return;
        host.drawDiamondFireComponentHint(DataComponents.MINIMUM_ATTACK_CHARGE);
        if (beginFields("combat_behavior_fields")) {
            presenceField("minimum_charge", tr("combat.attack.minimum_charge"), tr("combat.attack.minimum_charge.help"), minimumCharge);
            if (minimumCharge.present()) {
                ImDouble value = new ImDouble(primitiveDouble(minimumCharge, 1.0));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("minimum_charge", "value");
                if (ImGui.inputDouble("##minimum_charge", value, 0.05, 0.25, "%.2f")) {
                    minimumCharge.setValue(new JsonPrimitive(Math.clamp(value.get(), 0.0, 1.0)));
                    dirty = true;
                }
            } else ImGui.textDisabled(tr("state.disabled"));

            presenceField("damage_type", tr("combat.attack.damage_type"), tr("combat.attack.damage_type.help"), damageType);
            if (damageType.present()) {
                String current = primitiveString(damageType, "minecraft:player_attack");
                ImString id = buffer("damage_type", current);
                applyPendingFieldFocus("damage_type", "value");
                if (host.inputIdentifierWithSuggestions(
                    "attack_damage_type",
                    new FieldHints(
                        tr("field_purpose.damage_type_identifier"),
                        "minecraft:player_attack"
                    ),
                    id,
                    damageTypeIds
                )) {
                    damageType.setValue(new JsonPrimitive(id.get().trim()));
                    dirty = true;
                }
            } else ImGui.textDisabled(tr("state.disabled"));

            presenceField("swing", tr("combat.attack.swing_animation"), tr("combat.attack.swing_animation.help"), swing);
            if (swing.present()) {
                JsonObject value = swing.object();
                if (swing.supportsField("type")) {
                    ImInt type = new ImInt(indexOf(SWING_IDS, string(value, "type", "whack")));
                    ImGui.setNextItemWidth(-1.0f);
                    applyPendingFieldFocus("swing", "type");
                    if (ImGui.combo("##swing_type", type, swingLabels())) {
                        value.addProperty("type", SWING_IDS[type.get()]);
                        dirty = true;
                    }
                }
                if (swing.supportsField("duration")) {
                    ImInt duration = new ImInt(integer(value, "duration", 6));
                    ImGui.setNextItemWidth(-1.0f);
                    applyPendingFieldFocus("swing", "duration");
                    if (ImGui.inputInt("##swing_duration", duration, 1, 5)) {
                        value.addProperty("duration", Math.max(0, duration.get()));
                        dirty = true;
                    }
                }
            } else ImGui.textDisabled(tr("state.disabled"));
            ImGui.endTable();
        }
        EditorUi.endCard();
    }

    private static String[] swingLabels() {
        return new String[]{tr("empty.none"), tr("combat.swing.whack"), tr("combat.swing.stab")};
    }

    private void drawWeapon() {
        if (!beginSection("weapon", weapon)) return;
        JsonObject value = weapon.object();
        if (beginFields("weapon_fields")) {
            if (weapon.supportsField("item_damage_per_attack")) {
                fieldLabel(tr("combat.weapon.damage_per_attack"), tr("combat.weapon.damage_per_attack.help"));
                ImInt damage = new ImInt(integer(value, "item_damage_per_attack", 1));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("weapon", "item_damage_per_attack");
                if (ImGui.inputInt("##weapon_damage", damage, 1, 2)) {
                    value.addProperty("item_damage_per_attack", Math.max(0, damage.get()));
                    dirty = true;
                }
            }
            if (weapon.supportsField("disable_blocking_for_seconds")) {
                fieldLabel(tr("combat.weapon.disable_blocking"), tr("combat.weapon.disable_blocking.help"));
                ImDouble seconds = new ImDouble(number(value, "disable_blocking_for_seconds", 0.0));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("weapon", "disable_blocking_for_seconds");
                if (ImGui.inputDouble("##disable_blocking", seconds, 0.1, 0.5, "%.2f")) {
                    value.addProperty("disable_blocking_for_seconds", Math.max(0.0, seconds.get()));
                    dirty = true;
                }
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawPiercingWeapon(Minecraft client) {
        if (!beginSection("piercing_weapon", piercingWeapon)) return;
        JsonObject value = piercingWeapon.object();
        if (beginFields("piercing_weapon_fields")) {
            if (piercingWeapon.supportsField("deals_knockback")) {
                fieldLabel(tr("combat.piercing.knockback"), tr("combat.piercing.knockback.help"));
                applyPendingFieldFocus("piercing_weapon", "deals_knockback");
                dirty |= booleanField(value, "deals_knockback", true);
            }
            if (piercingWeapon.supportsField("dismounts")) {
                fieldLabel(tr("combat.piercing.dismount"), tr("combat.piercing.dismount.help"));
                applyPendingFieldFocus("piercing_weapon", "dismounts");
                dirty |= booleanField(value, "dismounts", false);
            }
            if (piercingWeapon.supportsField("sound")) {
                fieldLabel(tr("combat.piercing.attack_sound"), tr("combat.piercing.attack_sound.help"));
                optionalSoundIdentifier(
                    client,
                    value,
                    "sound",
                    "piercing_weapon/sound"
                );
            }
            if (piercingWeapon.supportsField("hit_sound")) {
                fieldLabel(tr("combat.piercing.hit_sound"), tr("combat.piercing.hit_sound.help"));
                optionalSoundIdentifier(
                    client,
                    value,
                    "hit_sound",
                    "piercing_weapon/hit_sound"
                );
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawKineticWeapon(Minecraft client) {
        if (!beginSection("kinetic_weapon", kineticWeapon)) return;
        JsonObject value = kineticWeapon.object();
        if (beginFields("kinetic_weapon_fields")) {
            if (kineticWeapon.supportsField("contact_cooldown_ticks")) {
                fieldLabel(tr("combat.kinetic.contact_cooldown"), tr("combat.kinetic.contact_cooldown.help"));
                ImInt ticks = new ImInt(integer(value, "contact_cooldown_ticks", 10));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("kinetic_weapon", "contact_cooldown_ticks");
                if (ImGui.inputInt("##kinetic_contact_cooldown", ticks, 1, 5)) {
                    value.addProperty("contact_cooldown_ticks", Math.max(0, ticks.get()));
                    dirty = true;
                }
            }
            if (kineticWeapon.supportsField("delay_ticks")) {
                fieldLabel(tr("combat.kinetic.attack_delay"), tr("combat.kinetic.attack_delay.help"));
                ImInt ticks = new ImInt(integer(value, "delay_ticks", 0));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("kinetic_weapon", "delay_ticks");
                if (ImGui.inputInt("##kinetic_delay", ticks, 1, 5)) {
                    value.addProperty("delay_ticks", Math.max(0, ticks.get()));
                    dirty = true;
                }
            }
            if (kineticWeapon.supportsField("forward_movement")) {
                fieldLabel(tr("combat.kinetic.forward_movement"), tr("combat.kinetic.forward_movement.help"));
                ImDouble movement = new ImDouble(number(value, "forward_movement", 0.0));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("kinetic_weapon", "forward_movement");
                if (ImGui.inputDouble("##kinetic_forward_movement", movement, 0.01, 0.1, "%.2f")) {
                    value.addProperty("forward_movement", movement.get());
                    dirty = true;
                }
            }
            if (kineticWeapon.supportsField("damage_multiplier")) {
                fieldLabel(tr("combat.kinetic.damage_multiplier"), tr("combat.kinetic.damage_multiplier.help"));
                ImDouble multiplier = new ImDouble(number(value, "damage_multiplier", 1.0));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("kinetic_weapon", "damage_multiplier");
                if (ImGui.inputDouble("##kinetic_damage_multiplier", multiplier, 0.05, 0.25, "%.2f")) {
                    value.addProperty("damage_multiplier", multiplier.get());
                    dirty = true;
                }
            }
            if (kineticWeapon.supportsField("sound")) {
                fieldLabel(tr("combat.kinetic.use_sound"), tr("combat.kinetic.use_sound.help"));
                optionalSoundIdentifier(
                    client,
                    value,
                    "sound",
                    "kinetic_weapon/sound"
                );
            }
            if (kineticWeapon.supportsField("hit_sound")) {
                fieldLabel(tr("combat.kinetic.hit_sound"), tr("combat.kinetic.hit_sound.help"));
                optionalSoundIdentifier(
                    client,
                    value,
                    "hit_sound",
                    "kinetic_weapon/hit_sound"
                );
            }
            ImGui.endTable();
        }

        ImGui.spacing();
        ImGui.separatorText(tr("combat.kinetic.conditions"));
        if (kineticWeapon.supportsField("dismount_conditions")) {
            drawKineticCondition(
                value,
                "dismount_conditions",
                tr("combat.kinetic.condition.dismount"),
                tr("combat.kinetic.condition.dismount.help"),
                100,
                14.0,
                0.0
            );
        }
        if (kineticWeapon.supportsField("knockback_conditions")) {
            drawKineticCondition(
                value,
                "knockback_conditions",
                tr("combat.kinetic.condition.knockback"),
                tr("combat.kinetic.condition.knockback.help"),
                200,
                5.1,
                0.0
            );
        }
        if (kineticWeapon.supportsField("damage_conditions")) {
            drawKineticCondition(
                value,
                "damage_conditions",
                tr("combat.kinetic.condition.damage"),
                tr("combat.kinetic.condition.damage.help"),
                300,
                0.0,
                4.6
            );
        }
        endSection();
    }

    private void drawKineticCondition(
        JsonObject owner,
        String key,
        String label,
        String description,
        int defaultDuration,
        double defaultSpeed,
        double defaultRelativeSpeed
    ) {
        boolean present = owner.has(key);
        ImBoolean enabled = new ImBoolean(present);
        applyPendingFieldFocus("kinetic_weapon", key + "/$enabled");
        if (ImGui.checkbox(label + "##kinetic_" + key, enabled)) {
            if (enabled.get()) {
                JsonObject condition = new JsonObject();
                condition.addProperty("max_duration_ticks", defaultDuration);
                if (defaultSpeed != 0.0) condition.addProperty("min_speed", defaultSpeed);
                if (defaultRelativeSpeed != 0.0) {
                    condition.addProperty("min_relative_speed", defaultRelativeSpeed);
                }
                owner.add(key, condition);
            } else {
                owner.remove(key);
            }
            present = enabled.get();
            dirty = true;
        }
        if (ImGui.isItemHovered()) EditorUi.setTooltip(description);
        if (!present) {
            ImGui.spacing();
            return;
        }

        JsonObject condition = object(owner, key);
        if (beginFields("kinetic_" + key + "_fields")) {
            fieldLabel(tr("combat.kinetic.maximum_duration"), tr("combat.kinetic.maximum_duration.help"));
            ImInt duration = new ImInt(integer(condition, "max_duration_ticks", defaultDuration));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus("kinetic_weapon", key + "/max_duration_ticks");
            if (ImGui.inputInt("##kinetic_" + key + "_duration", duration, 1, 20)) {
                condition.addProperty("max_duration_ticks", Math.max(0, duration.get()));
                dirty = true;
            }

            fieldLabel(tr("combat.kinetic.minimum_speed"), tr("combat.kinetic.minimum_speed.help"));
            ImDouble speed = new ImDouble(number(condition, "min_speed", 0.0));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus("kinetic_weapon", key + "/min_speed");
            if (ImGui.inputDouble("##kinetic_" + key + "_speed", speed, 0.1, 1.0, "%.2f")) {
                condition.addProperty("min_speed", speed.get());
                dirty = true;
            }

            fieldLabel(tr("combat.kinetic.minimum_relative_speed"), tr("combat.kinetic.minimum_relative_speed.help"));
            ImDouble relativeSpeed = new ImDouble(number(condition, "min_relative_speed", 0.0));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus("kinetic_weapon", key + "/min_relative_speed");
            if (ImGui.inputDouble(
                "##kinetic_" + key + "_relative_speed",
                relativeSpeed,
                0.1,
                1.0,
                "%.2f"
            )) {
                condition.addProperty("min_relative_speed", relativeSpeed.get());
                dirty = true;
            }
            ImGui.endTable();
        }
        ImGui.spacing();
    }

    private void drawChargedProjectiles(Minecraft client) {
        if (!beginSection("charged_projectiles", chargedProjectiles)) return;

        int maximumProjectiles = maximumChargedProjectiles();
        drawAddProjectileAction("top", maximumProjectiles, false);
        ImGui.spacing();
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < projectileRows.size(); index++) {
            ProjectileRow row = projectileRows.get(index);
            ImGui.pushID("charged_projectile_" + row.id);
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "charged_projectile",
                EditorUi.identifiedTitle(
                    tr("combat.projectile", index + 1),
                    row.itemId.get().trim()
                ),
                ImGuiTableFlags.SizingStretchProp,
                index,
                projectileRows.size(),
                true,
                projectileRows.size() < maximumProjectiles,
                projectileRows.size() < maximumProjectiles,
                focusProjectileRowId == row.id,
                projectileDragScope,
                row.id
            );
            if (card.focusApplied()) focusProjectileRowId = 0L;
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = card.action();
            }
            if (card.expanded()) {
                String itemProblem = projectileItemProblem(row.itemId.get());
                if (beginFields("charged_projectile_fields")) {
                    fieldLabel(tr("field.item_id"), tr("combat.projectile.item.help"));
                    applyPendingFieldFocus("charged_projectiles", "row/" + row.id + "/id");
                    if (host.inputIdentifierWithSuggestions(
                        "projectile_item",
                        new FieldHints(tr("field_purpose.item_identifier"), "minecraft:arrow"),
                        row.itemId,
                        projectileItemIds,
                        itemProblem
                    )) dirty = true;

                    fieldLabel(tr("field.count"), tr("combat.projectile.count.help"));
                    ImGui.setNextItemWidth(-1.0f);
                    applyPendingFieldFocus("charged_projectiles", "row/" + row.id + "/count");
                    if (ImGui.inputInt("##projectile_count", row.count, 1, 8)) {
                        row.count.set(Math.clamp(row.count.get(), 1, 99));
                        dirty = true;
                    }
                    ImGui.endTable();
                }

                ImGui.spacing();
                if (ImGui.checkbox(tr("combat.projectile.components"), row.componentsPresent)) {
                    dirty = true;
                }
                if (ImGui.isItemHovered()) EditorUi.setTooltip(tr("combat.projectile.components.help"));
                if (row.componentsPresent.get()) {
                    if (consumePendingFieldFocus("charged_projectiles", "row/" + row.id + "/components")) {
                        ImGui.setScrollHereY(0.35f);
                        row.componentsEditor.requestFocus();
                    }
                    String snbtProblem = snbtCompoundProblem(row.componentsSource.get());
                    int errorLine = snbtProblem.isBlank()
                        ? 0
                        : snbtCompoundErrorLine(row.componentsSource.get());
                    if (!snbtProblem.isBlank()) ItemEditorTheme.pushInvalidField();
                    if (row.componentsEditor.draw(
                        "projectile_components",
                        row.componentsSource,
                        -1.0f,
                        132.0f,
                        ImGuiInputTextFlags.AllowTabInput,
                        errorLine,
                        CodeLanguage.SNBT
                    )) dirty = true;
                    if (!snbtProblem.isBlank()) ItemEditorTheme.popInvalidField();
                    snbtProblem = snbtCompoundProblem(row.componentsSource.get());
                    ItemEditorTheme.drawFieldProblem(snbtProblem);
                    ImGui.beginDisabled(!snbtProblem.isBlank());
                    if (ImGui.button(tr("combat.projectile.components.format"))) {
                        formatPendingSnbt(row.componentsSource, "combat.projectile.components.invalid");
                    }
                    ImGui.endDisabled();
                }

                String rowProblem = projectileProblem(client, row);
                if (!rowProblem.isBlank()
                    && itemProblem.isBlank()
                    && (!row.componentsPresent.get()
                    || snbtCompoundProblem(row.componentsSource.get()).isBlank())) {
                    ItemEditorTheme.drawFieldProblem(rowProblem);
                }
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                projectileDragScope,
                row.id,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }

        if (reorderRequest != null) applyProjectileReorder(reorderRequest);
        else applyProjectileAction(actionIndex, action);
        if (projectileRows.isEmpty()) EditorUi.drawEmptyState(tr("combat.projectiles.empty"));
        drawAddProjectileAction("bottom", maximumProjectiles, true);
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(projectileDragScope);
    }

    private void loadProjectileRows(Minecraft client, ItemStack draft) {
        projectileRows.forEach(row -> row.componentsEditor.cancelFocusRequest());
        projectileRows.clear();
        Object installed = draft.get(DataComponents.CHARGED_PROJECTILES);
        Tag encodedNbt = installed == null
            ? null
            : ItemStackCodecs.encodeComponentNbt(client, DataComponents.CHARGED_PROJECTILES, installed);
        ListTag encodedNbtList = encodedNbt instanceof ListTag list ? list : null;
        JsonArray encodedList = chargedProjectiles.array();
        for (int index = 0; index < encodedList.size(); index++) {
            JsonElement entry = encodedList.get(index);
            if (entry.isJsonObject()) {
                CompoundTag entryNbt = encodedNbtList != null
                    && index < encodedNbtList.size()
                    && encodedNbtList.get(index) instanceof CompoundTag compound
                    ? compound
                    : null;
                projectileRows.add(new ProjectileRow(entry.getAsJsonObject(), entryNbt));
            } else if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                JsonObject expanded = new JsonObject();
                expanded.addProperty("id", entry.getAsString());
                projectileRows.add(new ProjectileRow(expanded, null));
            }
        }
    }

    private ListTag buildProjectileList(Minecraft client) {
        ListTag result = new ListTag();
        int maximumProjectiles = maximumChargedProjectiles();
        if (projectileRows.size() > maximumProjectiles) {
            throw new IllegalArgumentException(tr("combat.projectiles.too_many", maximumProjectiles));
        }
        for (int index = 0; index < projectileRows.size(); index++) {
            ProjectileRow row = projectileRows.get(index);
            CompoundTag encoded = row.toNbt();
            try {
                ItemStack stack = ItemStackCodecs.decodeStackNbt(client, encoded);
                if (stack.isEmpty()) throw new IllegalArgumentException(tr("combat.projectile.empty_item"));
                ItemStackCodecs.validateStack(stack);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                    tr("combat.projectile.error", index + 1, EditorUi.readableMessage(exception)),
                    exception
                );
            }
            result.add(encoded);
        }
        return result;
    }

    private static int maximumChargedProjectiles() {
        return EditorVersionProfile.targetVersion().compareTo("26.2") >= 0
            ? MAX_CHARGED_PROJECTILES
            : Integer.MAX_VALUE;
    }

    private String projectileProblem(Minecraft client, ProjectileRow row) {
        try {
            CompoundTag encoded = row.toNbt();
            ItemStack stack = ItemStackCodecs.decodeStackNbt(client, encoded);
            if (stack.isEmpty()) return tr("combat.projectile.empty_item");
            ItemStackCodecs.validateStack(stack);
            return "";
        } catch (RuntimeException exception) {
            return EditorUi.readableMessage(exception);
        }
    }

    private String projectileItemProblem(String source) {
        String value = source.trim();
        if (value.isEmpty()) return tr("validation.identifier_required");
        if (Identifier.tryParse(value) == null) return tr("validation.identifier_format");
        if (!knownItemIds.isEmpty() && !knownItemIds.contains(value)) {
            return tr("validation.identifier_unknown");
        }
        return "";
    }

    private void applyProjectileAction(int index, EditorUi.ListAction action) {
        EditorListMutations.Mutation<ProjectileRow> mutation = EditorListMutations.apply(
            projectileRows,
            index,
            action,
            ProjectileRow::copy,
            ProjectileRow::createDefault
        );
        finishProjectileMutation(mutation);
    }

    private void applyProjectileReorder(OrderedListDragDrop.ReorderRequest request) {
        if (!projectileDragScope.equals(request.scope())) return;
        finishProjectileMutation(EditorListMutations.reorder(projectileRows, request, row -> row.id));
    }

    private void finishProjectileMutation(EditorListMutations.Mutation<ProjectileRow> mutation) {
        if (!mutation.changed()) return;
        if (mutation.focusRow() == null) {
            focusProjectileRowId = 0L;
            focusAddProjectileButton = true;
        } else {
            focusProjectileRowId = mutation.focusRow().id;
            focusAddProjectileButton = false;
        }
        dirty = true;
    }

    private void drawAddProjectileAction(String placement, int maximumProjectiles, boolean acceptFocusRequest) {
        ImGui.beginDisabled(projectileRows.size() >= maximumProjectiles);
        if (acceptFocusRequest && focusAddProjectileButton) {
            ImGui.setKeyboardFocusHere();
            focusAddProjectileButton = false;
        }
        if (EditorIcons.button(
            "add_charged_projectile_" + placement,
            EditorIcons.Icon.ADD,
            tr("combat.projectiles.add")
        )) {
            ProjectileRow added = ProjectileRow.createDefault();
            projectileRows.add(added);
            focusProjectileRowId = added.id;
            focusAddProjectileButton = false;
            dirty = true;
        }
        ImGui.endDisabled();
    }

    private static String snbtCompoundProblem(String source) {
        try {
            ItemStackCodecs.parseCompoundSnbt(source);
            return "";
        } catch (RuntimeException exception) {
            return tr("combat.projectile.components.invalid", EditorUi.readableMessage(exception));
        }
    }

    private static int snbtCompoundErrorLine(String source) {
        try {
            ItemStackCodecs.parseCompoundSnbt(source);
            return 0;
        } catch (RuntimeException exception) {
            return CodeEditorInput.errorLine(exception, source);
        }
    }

    private void drawTool() {
        if (!beginSection("tool", tool)) return;
        JsonObject value = tool.object();
        if (beginFields("tool_fields")) {
            if (tool.supportsField("default_mining_speed")) {
                fieldLabel(tr("combat.tool.default_speed"), tr("combat.tool.default_speed.help"));
                ImDouble speed = new ImDouble(number(value, "default_mining_speed", 1.0));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("tool", "default_mining_speed");
                if (ImGui.inputDouble("##default_mining_speed", speed, 0.1, 1.0, "%.2f")) {
                    value.addProperty("default_mining_speed", Math.max(0.0, speed.get()));
                    dirty = true;
                }
            }
            if (tool.supportsField("damage_per_block")) {
                fieldLabel(tr("combat.tool.damage_per_block"), tr("combat.tool.damage_per_block.help"));
                ImInt damage = new ImInt(integer(value, "damage_per_block", 1));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("tool", "damage_per_block");
                if (ImGui.inputInt("##damage_per_block", damage, 1, 2)) {
                    value.addProperty("damage_per_block", Math.max(0, damage.get()));
                    dirty = true;
                }
            }
            if (tool.supportsField("can_destroy_blocks_in_creative")) {
                fieldLabel(tr("combat.tool.creative_destruction"), tr("combat.tool.creative_destruction.help"));
                applyPendingFieldFocus("tool", "can_destroy_blocks_in_creative");
                dirty |= booleanField(value, "can_destroy_blocks_in_creative", true);
            }
            ImGui.endTable();
        }
        if (tool.supportsField("rules")) {
            ImGui.spacing();
            drawToolRules(value);
        }
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(toolRuleDragScope);
    }

    private void drawToolRules(JsonObject owner) {
        ImGui.separatorText(tr("combat.mining_rules"));
        JsonArray rules = array(owner, "rules");
        ensureRowIds(toolRuleRowIds, rules.size(), NEXT_TOOL_RULE_ID);
        drawAddToolRuleAction(rules, "top", false);
        ImGui.spacing();
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < rules.size(); index++) {
            if (!rules.get(index).isJsonObject()) continue;
            long rowId = toolRuleRowIds.get(index);
            ImGui.pushID("tool_rule_" + rowId);
            JsonObject rule = rules.get(index).getAsJsonObject();
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "tool_rule",
                EditorUi.identifiedTitle(
                    tr("combat.rule", index + 1),
                    EditorUi.jsonSummary(rule.get("blocks"))
                ),
                ImGuiTableFlags.SizingStretchProp,
                index,
                rules.size(),
                true,
                true,
                true,
                focusToolRuleRowId == rowId,
                toolRuleDragScope,
                rowId
            );
            if (card.focusApplied()) focusToolRuleRowId = 0L;
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = card.action();
            }
            if (card.expanded()) {
                String path = "tool/rule/" + rowId;
                prepareCollectionFocus("tool", path + "/blocks");
                dirty |= IdentifierCollectionEditor.draw(
                    host,
                    "tool_rule_blocks",
                    "tool/rule/" + rowId + "/blocks",
                    rule.get("blocks"),
                    replacement -> rule.add("blocks", replacement),
                    blockIds,
                    tr("field_purpose.block_identifier"),
                    "minecraft:stone",
                    tr("combat.block"),
                    textBuffers,
                    collectionFocus.computeIfAbsent(path + "/blocks", ignored -> new IdentifierCollectionEditor.FocusState())
                );
                ImDouble speed = new ImDouble(number(rule, "speed", 1.0));
                ImGui.text(tr("combat.mining_speed"));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("tool", path + "/speed");
                if (ImGui.inputDouble("##rule_speed", speed, 0.1, 1.0, "%.2f")) {
                    rule.addProperty("speed", Math.max(0.0, speed.get()));
                    dirty = true;
                }
                ImBoolean correct = new ImBoolean(bool(rule, "correct_for_drops", false));
                applyPendingFieldFocus("tool", path + "/correct_for_drops");
                if (ImGui.checkbox(tr("combat.correct_for_drops"), correct)) {
                    rule.addProperty("correct_for_drops", correct.get());
                    dirty = true;
                }
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                toolRuleDragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        if (reorderRequest != null) applyToolRuleReorder(rules, reorderRequest);
        else applyToolRuleAction(rules, actionIndex, action);
        if (rules.isEmpty()) EditorUi.drawEmptyState(tr("empty.rules"));
        drawAddToolRuleAction(rules, "bottom", true);
    }

    private void applyToolRuleAction(JsonArray rules, int index, EditorUi.ListAction action) {
        JsonListMutations.Mutation mutation = JsonListMutations.apply(
            rules,
            toolRuleRowIds,
            index,
            action,
            NEXT_TOOL_RULE_ID::incrementAndGet,
            CombatEditorPanel::defaultToolRule
        );
        finishToolRuleMutation(mutation);
    }

    private void applyToolRuleReorder(
        JsonArray rules,
        OrderedListDragDrop.ReorderRequest request
    ) {
        if (!toolRuleDragScope.equals(request.scope())) return;
        finishToolRuleMutation(JsonListMutations.reorder(rules, toolRuleRowIds, request));
    }

    private void finishToolRuleMutation(JsonListMutations.Mutation mutation) {
        if (!mutation.changed()) return;
        resetEditorUi();
        if (mutation.focusRowId() == 0L) {
            focusToolRuleRowId = 0L;
            focusAddToolRuleButton = true;
        } else {
            focusToolRuleRowId = mutation.focusRowId();
            focusAddToolRuleButton = false;
        }
        dirty = true;
    }

    private void drawAddToolRuleAction(JsonArray rules, String placement, boolean acceptFocusRequest) {
        if (acceptFocusRequest) applyPendingFieldFocus("tool", "add_rule");
        if (acceptFocusRequest && focusAddToolRuleButton) {
            ImGui.setKeyboardFocusHere();
            focusAddToolRuleButton = false;
        }
        if (EditorIcons.button(
            "add_mining_rule_" + placement,
            EditorIcons.Icon.ADD,
            tr("combat.add_rule")
        )) {
            rules.add(defaultToolRule());
            long rowId = NEXT_TOOL_RULE_ID.incrementAndGet();
            toolRuleRowIds.add(rowId);
            focusToolRuleRowId = rowId;
            focusAddToolRuleButton = false;
            dirty = true;
        }
    }

    private static JsonObject defaultToolRule() {
        JsonObject rule = new JsonObject();
        rule.addProperty("blocks", "minecraft:stone");
        rule.addProperty("speed", 1.0);
        rule.addProperty("correct_for_drops", false);
        return rule;
    }

    private void drawRange() {
        if (!beginSection("attack_range", range)) return;
        JsonObject value = range.object();
        if (beginFields("range_fields")) {
            doubleField(range, value, "min_reach", tr("combat.range.minimum"), tr("combat.range.minimum.help"), 0.0);
            doubleField(range, value, "max_reach", tr("combat.range.maximum"), tr("combat.range.maximum.help"), 3.0);
            doubleField(range, value, "min_creative_reach", tr("combat.range.creative_minimum"), tr("combat.range.creative_minimum.help"), 0.0);
            doubleField(range, value, "max_creative_reach", tr("combat.range.creative_maximum"), tr("combat.range.creative_maximum.help"), 5.0);
            doubleField(range, value, "hitbox_margin", tr("combat.range.hitbox_margin"), tr("combat.range.hitbox_margin.help"), 0.0);
            doubleField(range, value, "mob_factor", tr("combat.range.mob_factor"), tr("combat.range.mob_factor.help"), 1.0);
            ImGui.endTable();
        }
        endSection();
    }

    private void drawResistance() {
        if (!beginSection("damage_resistant", resistance)) return;
        JsonObject value = resistance.object();
        if (beginFields("resistance_fields")) {
            if (resistance.supportsField("types")) {
                fieldLabel(tr("combat.resistance.damage_types"), tr("combat.resistance.damage_types.help"));
                prepareCollectionFocus("damage_resistant", "resistance/types");
                dirty |= IdentifierCollectionEditor.draw(
                    host,
                    "resistant_types",
                    "resistance/types",
                    value.get("types"),
                    replacement -> value.add("types", replacement),
                    damageTypeIds,
                    tr("field_purpose.damage_type_identifier"),
                    "minecraft:in_fire",
                    tr("combat.damage_type"),
                    textBuffers,
                    collectionFocus.computeIfAbsent("resistance/types", ignored -> new IdentifierCollectionEditor.FocusState())
                );
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawRepairable() {
        if (!beginSection("repairable", repairable)) return;
        JsonObject value = repairable.object();
        if (beginFields("repairable_fields")) {
            if (repairable.supportsField("items")) {
                fieldLabel(tr("combat.repairable.items"), tr("combat.repairable.items.help"));
                prepareCollectionFocus("repairable", "repairable/items");
                dirty |= IdentifierCollectionEditor.draw(
                    host,
                    "repair_items",
                    "repairable/items",
                    value.get("items"),
                    replacement -> value.add("items", replacement),
                    itemIds,
                    tr("field_purpose.item_identifier"),
                    "minecraft:iron_ingot",
                    tr("combat.repair_item"),
                    textBuffers,
                    collectionFocus.computeIfAbsent("repairable/items", ignored -> new IdentifierCollectionEditor.FocusState())
                );
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawEnchantable() {
        if (!beginSection("enchantable", enchantable)) return;
        if (beginFields("enchantable_fields")) {
            fieldLabel(tr("combat.enchantable.value"), tr("combat.enchantable.value.help"));
            JsonObject encoded = enchantable.object();
            ImInt value = new ImInt(integer(encoded, "value", 10));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus("enchantable", "value");
            if (ImGui.inputInt("##enchantability", value, 1, 5)) {
                encoded.addProperty("value", Math.max(1, value.get()));
                dirty = true;
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawBlocksAttacks(Minecraft client) {
        if (!beginSection("blocks_attacks", blocksAttacks)) return;
        JsonObject value = blocksAttacks.object();
        if (beginFields("blocking_fields")) {
            doubleField(blocksAttacks, value, "block_delay_seconds", tr("combat.blocking.delay"), tr("combat.blocking.delay.help"), 0.0);
            doubleField(blocksAttacks, value, "disable_cooldown_scale", tr("combat.blocking.disable_cooldown_scale"), tr("combat.blocking.disable_cooldown_scale.help"), 1.0);

            if (blocksAttacks.supportsField("block_sound")) {
                fieldLabel(tr("combat.blocking.block_sound"), tr("combat.blocking.block_sound.help"));
                optionalSoundIdentifier(client, value, "block_sound", "blocking/block_sound");
            }
            if (blocksAttacks.supportsField("disabled_sound")) {
                fieldLabel(tr("combat.blocking.disabled_sound"), tr("combat.blocking.disabled_sound.help"));
                optionalSoundIdentifier(client, value, "disabled_sound", "blocking/disabled_sound");
            }
            if (blocksAttacks.supportsField("bypassed_by")) {
                fieldLabel(tr("combat.blocking.bypassed_by"), tr("combat.blocking.bypassed_by.help"));
                optionalCollection(
                    value,
                    "bypassed_by",
                    "blocking/bypassed_by",
                    damageTypeIds,
                    "minecraft:bypasses_shield",
                    tr("combat.damage_type")
                );
            }
            ImGui.endTable();
        }

        if (blocksAttacks.supportsField("item_damage")) {
            ImGui.spacing();
            ImGui.separatorText(tr("combat.blocking_item_damage"));
            JsonObject itemDamage = object(value, "item_damage");
            if (beginFields("blocking_item_damage")) {
                doubleField("blocks_attacks", "blocking/item_damage/threshold", itemDamage, "threshold", tr("combat.blocking.item_damage.threshold"), tr("combat.blocking.item_damage.threshold.help"), 1.0);
                doubleField("blocks_attacks", "blocking/item_damage/base", itemDamage, "base", tr("combat.blocking.item_damage.base"), tr("combat.blocking.item_damage.base.help"), 0.0);
                doubleField("blocks_attacks", "blocking/item_damage/factor", itemDamage, "factor", tr("combat.blocking.item_damage.factor"), tr("combat.blocking.item_damage.factor.help"), 1.0);
                ImGui.endTable();
            }
        }

        if (blocksAttacks.supportsField("damage_reductions")) {
            ImGui.spacing();
            drawDamageReductions(value);
        }
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(damageReductionDragScope);
    }

    private void drawDamageReductions(JsonObject owner) {
        ImGui.separatorText(tr("combat.damage_reductions"));
        JsonArray reductions = array(owner, "damage_reductions");
        ensureRowIds(damageReductionRowIds, reductions.size(), NEXT_DAMAGE_REDUCTION_ID);
        drawAddDamageReductionAction(reductions, "top", false);
        ImGui.spacing();
        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < reductions.size(); index++) {
            if (!reductions.get(index).isJsonObject()) continue;
            long rowId = damageReductionRowIds.get(index);
            ImGui.pushID("damage_reduction_" + rowId);
            JsonObject reduction = reductions.get(index).getAsJsonObject();
            String damageTypes = EditorUi.jsonSummary(reduction.get("type"));
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "damage_reduction",
                EditorUi.identifiedTitle(
                    tr("combat.reduction", index + 1),
                    damageTypes.isBlank() ? tr("combat.reduction.all_damage") : damageTypes
                ),
                ImGuiTableFlags.SizingStretchProp,
                index,
                reductions.size(),
                true,
                true,
                true,
                focusDamageReductionRowId == rowId,
                damageReductionDragScope,
                rowId
            );
            if (card.focusApplied()) focusDamageReductionRowId = 0L;
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = card.action();
            }
            if (card.expanded()) {
                if (beginFields("reduction_fields")) {
                    doubleField("blocks_attacks", "blocking/reduction/" + rowId + "/horizontal_blocking_angle", reduction, "horizontal_blocking_angle", tr("combat.reduction.angle"), tr("combat.reduction.angle.help"), 90.0);
                    fieldLabel(tr("combat.reduction.damage_types"), tr("combat.reduction.damage_types.help"));
                    optionalCollection(
                        reduction,
                        "type",
                        "blocking/reduction/" + rowId + "/type",
                        damageTypeIds,
                        "minecraft:in_fire",
                        tr("combat.damage_type")
                    );
                    doubleField("blocks_attacks", "blocking/reduction/" + rowId + "/base", reduction, "base", tr("combat.reduction.base"), tr("combat.reduction.base.help"), 0.0);
                    doubleField("blocks_attacks", "blocking/reduction/" + rowId + "/factor", reduction, "factor", tr("combat.reduction.factor"), tr("combat.reduction.factor.help"), 1.0);
                    ImGui.endTable();
                }
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                damageReductionDragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }
        if (reorderRequest != null) applyDamageReductionReorder(reductions, reorderRequest);
        else applyDamageReductionAction(reductions, actionIndex, action);
        if (reductions.isEmpty()) EditorUi.drawEmptyState(tr("empty.rules"));
        drawAddDamageReductionAction(reductions, "bottom", true);
    }

    private void applyDamageReductionAction(JsonArray reductions, int index, EditorUi.ListAction action) {
        JsonListMutations.Mutation mutation = JsonListMutations.apply(
            reductions,
            damageReductionRowIds,
            index,
            action,
            NEXT_DAMAGE_REDUCTION_ID::incrementAndGet,
            CombatEditorPanel::defaultDamageReduction
        );
        finishDamageReductionMutation(mutation);
    }

    private void applyDamageReductionReorder(
        JsonArray reductions,
        OrderedListDragDrop.ReorderRequest request
    ) {
        if (!damageReductionDragScope.equals(request.scope())) return;
        finishDamageReductionMutation(JsonListMutations.reorder(
            reductions,
            damageReductionRowIds,
            request
        ));
    }

    private void finishDamageReductionMutation(JsonListMutations.Mutation mutation) {
        if (!mutation.changed()) return;
        resetEditorUi();
        if (mutation.focusRowId() == 0L) {
            focusDamageReductionRowId = 0L;
            focusAddDamageReductionButton = true;
        } else {
            focusDamageReductionRowId = mutation.focusRowId();
            focusAddDamageReductionButton = false;
        }
        dirty = true;
    }

    private void drawAddDamageReductionAction(
        JsonArray reductions,
        String placement,
        boolean acceptFocusRequest
    ) {
        if (acceptFocusRequest && focusAddDamageReductionButton) {
            ImGui.setKeyboardFocusHere();
            focusAddDamageReductionButton = false;
        }
        if (EditorIcons.button(
            "add_damage_reduction_" + placement,
            EditorIcons.Icon.ADD,
            tr("combat.add_reduction")
        )) {
            reductions.add(defaultDamageReduction());
            long rowId = NEXT_DAMAGE_REDUCTION_ID.incrementAndGet();
            damageReductionRowIds.add(rowId);
            focusDamageReductionRowId = rowId;
            focusAddDamageReductionButton = false;
            dirty = true;
        }
    }

    private static JsonObject defaultDamageReduction() {
        JsonObject reduction = new JsonObject();
        reduction.addProperty("horizontal_blocking_angle", 90.0);
        reduction.addProperty("base", 0.0);
        reduction.addProperty("factor", 1.0);
        return reduction;
    }

    private static void ensureRowIds(List<Long> rowIds, int size, AtomicLong nextRowId) {
        while (rowIds.size() < size) rowIds.add(nextRowId.incrementAndGet());
        while (rowIds.size() > size) rowIds.removeLast();
    }

    private void resetCombatListUi() {
        toolRuleRowIds.clear();
        damageReductionRowIds.clear();
        resetCombatDragScopes();
        clearCombatRecoveryFocus();
        collectionFocus.clear();
    }

    private void resetCombatDragScopes() {
        projectileDragScope = OrderedListDragDrop.newScope("combat/charged_projectiles");
        toolRuleDragScope = OrderedListDragDrop.newScope("combat/tool_rules");
        damageReductionDragScope = OrderedListDragDrop.newScope("combat/damage_reductions");
    }

    private void presenceField(String section, String label, String description, CodecComponentState state) {
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.textWrapped(label);
        if (ImGui.isItemHovered() && !description.isBlank()) {
            EditorUi.setTooltip(description);
        }
        ImGui.tableNextColumn();
        ImBoolean enabled = new ImBoolean(state.present());
        applyPendingFieldFocus(section, "$enabled");
        if (ImGui.checkbox("##enabled_" + label, enabled)) {
            state.setPresent(enabled.get());
            dirty = true;
        }
        ImGui.sameLine();
    }

    private void doubleField(String section, String field, JsonObject object, String key, String label, String description, double fallback) {
        fieldLabel(label, description);
        ImDouble value = new ImDouble(number(object, key, fallback));
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus(section, field);
        if (ImGui.inputDouble("##" + key, value, 0.1, 0.5, "%.2f")) {
            object.addProperty(key, value.get());
            dirty = true;
        }
    }

    private void doubleField(
        CodecComponentState state,
        JsonObject object,
        String key,
        String label,
        String description,
        double fallback
    ) {
        if (state.supportsField(key)) {
            doubleField(state == range ? "attack_range" : "blocks_attacks", key, object, key, label, description, fallback);
        }
    }

    private boolean booleanField(JsonObject object, String key, boolean fallback) {
        ImBoolean value = new ImBoolean(bool(object, key, fallback));
        if (!ImGui.checkbox("##" + key, value)) return false;
        object.addProperty(key, value.get());
        return true;
    }

    private void optionalSoundIdentifier(
        Minecraft client,
        JsonObject owner,
        String key,
        String bufferKey
    ) {
        String current = string(owner, key, "");
        ImString input = buffer(bufferKey, current);
        String section = bufferKey.startsWith("blocking/") ? "blocks_attacks" : bufferKey.substring(0, bufferKey.indexOf('/'));
        boolean requestFocus = consumePendingFieldFocus(section, key);
        if (requestFocus) ImGui.setScrollHereY(0.35f);
        if (host.inputSoundWithPreview(
            client,
            key,
            FieldHints.purpose(tr("field_purpose.sound_identifier")),
            input,
            soundIds,
            SoundPreviewKind.SOUND_EVENT,
            "",
            requestFocus
        )) {
            if (input.get().isBlank()) owner.remove(key);
            else owner.addProperty(key, input.get().trim());
            dirty = true;
        }
    }

    private void optionalCollection(
        JsonObject owner,
        String key,
        String path,
        List<IdSuggestion> suggestions,
        String fallback,
        String entryLabel
    ) {
        ImBoolean enabled = new ImBoolean(owner.has(key));
        if (ImGui.checkbox(tr("action.restrict") + "##" + path, enabled)) {
            if (enabled.get()) owner.addProperty(key, fallback);
            else owner.remove(key);
            textBuffers.keySet().removeIf(bufferKey -> bufferKey.startsWith(path));
            dirty = true;
        }
        if (owner.has(key)) {
            prepareCollectionFocus("blocks_attacks", path);
            dirty |= IdentifierCollectionEditor.draw(
                host,
                "optional_collection",
                path,
                owner.get(key),
                replacement -> owner.add(key, replacement),
                suggestions,
                tr("field_purpose.damage_type_identifier"),
                fallback,
                entryLabel,
                textBuffers,
                collectionFocus.computeIfAbsent(path, ignored -> new IdentifierCollectionEditor.FocusState())
            );
        } else {
            EditorUi.textDisabledWrapped(tr("empty.none"));
        }
    }

    private void prepareCollectionFocus(String section, String path) {
        if (consumePendingFieldFocus(section, path)) ImGui.setScrollHereY(0.35f);
    }

    private static final class ProjectileRow {
        private final long id = NEXT_PROJECTILE_ID.incrementAndGet();
        private final ImString itemId;
        private final ImInt count;
        private final ImBoolean componentsPresent;
        private final ImString componentsSource;
        private final CodeEditorInput componentsEditor = new CodeEditorInput();
        private final JsonObject extraFields;

        private static ProjectileRow createDefault() {
            JsonObject value = new JsonObject();
            value.addProperty("id", "minecraft:arrow");
            return new ProjectileRow(value, null);
        }

        private ProjectileRow(JsonObject encoded, CompoundTag encodedNbt) {
            itemId = new ImString(string(encoded, "id", "minecraft:arrow"), 512);
            count = new ImInt(Math.clamp(integer(encoded, "count", 1), 1, 99));
            componentsPresent = new ImBoolean(encoded.has("components"));
            componentsSource = new ImString(
                encodedNbt == null
                    ? "{}"
                    : encodedNbt.getCompound("components")
                    .map(ItemStackCodecs::prettySnbt)
                    .orElse("{}"),
                SOURCE_CAPACITY
            );
            extraFields = encoded.deepCopy();
            extraFields.remove("id");
            extraFields.remove("count");
            extraFields.remove("components");
        }

        private ProjectileRow(ProjectileRow source) {
            itemId = new ImString(source.itemId.get(), 512);
            count = new ImInt(source.count.get());
            componentsPresent = new ImBoolean(source.componentsPresent.get());
            componentsSource = new ImString(source.componentsSource.get(), SOURCE_CAPACITY);
            extraFields = source.extraFields.deepCopy();
        }

        private ProjectileRow copy() {
            return new ProjectileRow(this);
        }

        private CompoundTag toNbt() {
            JsonObject result = extraFields.deepCopy();
            result.addProperty("id", itemId.get().trim());
            if (count.get() != 1) result.addProperty("count", count.get());
            CompoundTag encoded = ItemStackCodecs.jsonObjectToNbt(result);
            if (componentsPresent.get()) {
                encoded.put("components", ItemStackCodecs.parseCompoundSnbt(componentsSource.get()));
            }
            return encoded;
        }
    }

}
