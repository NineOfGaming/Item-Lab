package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponentType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Human-facing field and collection metadata for vanilla component codecs.
 */
final class ComponentSchemas {
    private static final Map<String, List<Field>> FIELDS = new HashMap<>();
    private static final Map<String, ArrayEntry> ARRAY_ENTRIES = new HashMap<>();
    private static final Map<String, IdentifierCollection> IDENTIFIER_COLLECTIONS = new HashMap<>();

    static {
        register("minecraft:use_effects",
            field("can_sprint", "Can sprint", "Allow sprinting while using the item.", true, false),
            field("interact_vibrations", "Interaction vibrations", "Emit game-event vibrations while using.", true, false),
            field("speed_multiplier", "Speed multiplier", "Movement-speed multiplier while using.", 0.2, false));
        register("minecraft:custom_model_data",
            field("floats", "Float values", "Numeric model predicates.", array(), false),
            field("flags", "Flags", "Boolean model predicates.", array(), false),
            field("strings", "String values", "Text model predicates.", array(), false),
            field("colors", "Colors", "ARGB color model predicates.", array(), false));
        register("minecraft:food",
            field("nutrition", "Nutrition", "Hunger points restored.", 1, true),
            field("saturation", "Saturation", "Saturation restored.", 0.1, true),
            field("can_always_eat", "Always edible", "Allow eating when the hunger bar is full.", false, false));
        register("minecraft:use_cooldown",
            field("seconds", "Cooldown", "Cooldown duration in seconds.", 1.0, true),
            field("cooldown_group", "Cooldown group", "Optional shared cooldown identifier.",
                "minecraft:item", false, "field_purpose.cooldown_group_identifier", "minecraft:food"));
        register("minecraft:damage_resistant",
            field("types", "Damage types", "Damage types this item resists.", "minecraft:in_fire", true));
        register("minecraft:tool",
            field("rules", "Mining rules", "Per-block mining behavior.", array(), true),
            field("default_mining_speed", "Default speed", "Mining speed outside explicit rules.", 1.0, false),
            field("damage_per_block", "Damage per block", "Durability consumed per mined block.", 1, false),
            field("can_destroy_blocks_in_creative", "Creative destruction", "Allow breaking blocks in creative mode.", true, false));
        register("minecraft:weapon",
            field("item_damage_per_attack", "Damage per attack", "Durability consumed per attack.", 1, true),
            field("disable_blocking_for_seconds", "Disable blocking", "Seconds that a hit disables blocking.", 0.0, false));
        register("minecraft:attack_range",
            field("min_reach", "Minimum reach", "Minimum survival reach distance.", 0.0, false),
            field("max_reach", "Maximum reach", "Maximum survival reach distance.", 3.0, false),
            field("min_creative_reach", "Creative minimum", "Minimum creative reach distance.", 0.0, false),
            field("max_creative_reach", "Creative maximum", "Maximum creative reach distance.", 5.0, false),
            field("hitbox_margin", "Hitbox margin", "Extra target hitbox margin.", 0.0, false),
            field("mob_factor", "Mob factor", "Reach multiplier used by mobs.", 1.0, false));
        register("minecraft:death_protection",
            field("death_effects", "Death effects", "Effects applied when death is prevented.", array(), true));
        register("minecraft:blocks_attacks",
            field("block_delay_seconds", "Block delay", "Delay before blocking becomes active.", 0.0, true),
            field("disable_cooldown_scale", "Disable cooldown scale", "Scales the blocking-disable cooldown.", 1.0, false),
            field("damage_reductions", "Damage reductions", "Rules controlling blocked damage.", array(), true),
            field("item_damage", "Item damage", "Durability damage function.", object("{\"threshold\":1.0,\"base\":0.0,\"factor\":1.0}"), false),
            field("bypassed_by", "Bypassed by", "Damage types that bypass blocking.", "minecraft:bypasses_shield", false),
            field("block_sound", "Block sound", "Sound played for a successful block.", "minecraft:item.shield.block", false),
            field("disabled_sound", "Disabled sound", "Sound played when blocking is disabled.", "minecraft:item.shield.break", false));
        register("minecraft:piercing_weapon",
            field("deals_knockback", "Deals knockback", "Whether hits apply knockback.", true, true),
            field("dismounts", "Dismounts", "Whether hits dismount the target.", false, false),
            field("sound", "Attack sound", "Sound played on attack.", "minecraft:item.trident.throw", false),
            field("hit_sound", "Hit sound", "Sound played when the attack lands.", "minecraft:item.trident.hit", false));
        register("minecraft:kinetic_weapon",
            field("contact_cooldown_ticks", "Contact cooldown", "Ticks before another contact hit.", 10, true),
            field("delay_ticks", "Attack delay", "Ticks before damage begins.", 0, false),
            field("dismount_conditions", "Dismount conditions", "Conditions that dismount the target.", object("{}"), false),
            field("knockback_conditions", "Knockback conditions", "Conditions that apply knockback.", object("{}"), false),
            field("damage_conditions", "Damage conditions", "Conditions that apply damage.", object("{}"), false),
            field("forward_movement", "Forward movement", "Movement applied during the attack.", 0.0, false),
            field("damage_multiplier", "Damage multiplier", "Multiplier applied to attack damage.", 1.0, false),
            field("sound", "Attack sound", "Sound played while attacking.", "minecraft:item.trident.throw", false),
            field("hit_sound", "Hit sound", "Sound played on contact.", "minecraft:item.trident.hit", false));
        register("minecraft:swing_animation",
            field("type", "Animation", "Swing animation type.", "whack", true),
            field("duration", "Duration", "Animation duration in ticks.", 6, false));
        register("minecraft:potion_contents",
            field("potion", "Potion", "Registered base potion.", "minecraft:water", false),
            field("custom_color", "Custom color", "Optional packed RGB color.", 3694022, false),
            field("custom_effects", "Custom effects", "Additional status effects.", array(), false),
            field("custom_name", "Custom name", "Potion translation-name suffix.", "custom", false));
        register("minecraft:writable_book_content",
            field("pages", "Pages", "Editable book pages.", array(), true));
        register("minecraft:written_book_content",
            field("title", "Title", "Book title.", "Untitled", true),
            field("author", "Author", "Book author.", "Player", true),
            field("generation", "Generation", "Original, copy, or copy of a copy.", 0, true),
            field("pages", "Pages", "Written book pages.", array(), true),
            field("resolved", "Resolved", "Whether text components have been resolved.", false, false));
        register("minecraft:lodestone_tracker",
            field("target", "Target", "Optional lodestone dimension and position.", object("{\"dimension\":\"minecraft:overworld\",\"pos\":[0,64,0]}"), false),
            field("tracked", "Tracked", "Continue searching for the lodestone.", true, false));
        register("minecraft:firework_explosion",
            field("shape", "Shape", "Explosion shape.", "small_ball", true),
            field("colors", "Colors", "Primary packed RGB colors.", array(), true),
            field("fade_colors", "Fade colors", "Optional packed RGB fade colors.", array(), false),
            field("has_trail", "Trail", "Draw a particle trail.", false, false),
            field("has_twinkle", "Twinkle", "Play the twinkle effect.", false, false));
        register("minecraft:fireworks",
            field("flight_duration", "Flight duration", "Rocket flight-duration level.", 1, true),
            field("explosions", "Explosions", "Firework-star explosions.", array(), true));
        register("minecraft:profile",
            field("name", "Player name", "Resolvable player name.", "Player", false),
            field("id", "Player UUID", "Optional UUID encoded as four integers.", object("[0,0,0,0]"), false),
            field("properties", "Profile properties", "Signed or unsigned profile properties.", array(), false),
            field("texture", "Skin asset", "Optional resource-pack skin texture.", "minecraft:entity/player/wide/steve", false),
            field("cape", "Cape asset", "Optional resource-pack cape texture.", "minecraft:entity/player/wide/steve", false),
            field("elytra", "Elytra asset", "Optional resource-pack elytra texture.", "minecraft:entity/player/wide/steve", false),
            field("model", "Skin model", "Optional wide or slim model override.", "wide", false));
        register("minecraft:lock",
            field("items", "Key items", "Optional item or item tag accepted as a key.", array("minecraft:tripwire_hook"), false),
            field("count", "Key count", "Optional exact count or count range.", 1, false),
            field("components", "Exact components", "Exact components required on the key item.", object("{}"), false),
            field("predicates", "Component predicates", "Partial component predicates required on the key item.", object("{}"), false));
        register("minecraft:container_loot",
            field("loot_table", "Loot table", "Loot table identifier.", "minecraft:empty", true),
            field("seed", "Seed", "Optional deterministic loot seed.", 0, false));
        register("minecraft:can_break",
            field("blocks", "Blocks", "Blocks this item may break in adventure mode.", "minecraft:stone", false),
            field("state", "Block state", "Optional block-state conditions.", object("{}"), false),
            field("nbt", "Block-entity NBT", "Optional block-entity NBT condition.", "{}", false),
            field("components", "Exact components", "Exact block-entity component values.", object("{}"), false),
            field("predicates", "Component predicates", "Partial block-entity component predicates.", object("{}"), false));
        register("minecraft:can_place_on",
            field("blocks", "Blocks", "Blocks this item may be placed against in adventure mode.", "minecraft:stone", false),
            field("state", "Block state", "Optional block-state conditions.", object("{}"), false),
            field("nbt", "Block-entity NBT", "Optional block-entity NBT condition.", "{}", false),
            field("components", "Exact components", "Exact block-entity component values.", object("{}"), false),
            field("predicates", "Component predicates", "Partial block-entity component predicates.", object("{}"), false));

        registerAt("minecraft:tool", "$/rules/*",
            field("blocks", "Blocks", "Blocks matched by this mining rule.", "minecraft:stone", true),
            field("speed", "Mining speed", "Optional mining speed for matching blocks.", 1.0, false),
            field("correct_for_drops", "Correct for drops", "Whether the tool can harvest drops from matching blocks.", false, false));
        registerAt("minecraft:fireworks", "$/explosions/*",
            field("shape", "Shape", "Explosion shape.", "small_ball", true),
            field("colors", "Colors", "Primary packed RGB colors.", array(), true),
            field("fade_colors", "Fade colors", "Optional packed RGB fade colors.", array(), false),
            field("has_trail", "Trail", "Draw a particle trail.", false, false),
            field("has_twinkle", "Twinkle", "Play the twinkle effect.", false, false));
        registerAt("minecraft:potion_contents", "$/custom_effects/*",
            field("id", "Effect", "Registered status effect.", "minecraft:speed", true),
            field("duration", "Duration", "Effect duration in ticks.", 200, false),
            field("amplifier", "Amplifier", "Zero-based effect strength.", 0, false),
            field("ambient", "Ambient", "Render the effect as ambient.", false, false),
            field("show_particles", "Show particles", "Render effect particles.", true, false),
            field("show_icon", "Show icon", "Show the effect icon in the HUD.", true, false));

        entry("minecraft:custom_model_data", "$/floats", "float", "0.0",
            "field_purpose.numeric_value", "0.0");
        entry("minecraft:custom_model_data", "$/flags", "flag", "false");
        entry("minecraft:custom_model_data", "$/strings", "string", "\"\"",
            "field_purpose.model_predicate_string", "examplemod:variant");
        entry("minecraft:custom_model_data", "$/colors", "color", "-1",
            "field_purpose.color_value", "-1");
        entry("minecraft:tool", "$/rules", "mining rule", "{\"blocks\":\"minecraft:stone\",\"speed\":1.0,\"correct_for_drops\":false}");
        entry("minecraft:death_protection", "$/death_effects", "death effect", "{\"type\":\"minecraft:clear_all_effects\"}");
        entry("minecraft:blocks_attacks", "$/damage_reductions", "damage reduction", "{\"horizontal_blocking_angle\":90.0,\"type\":\"minecraft:in_fire\",\"base\":0.0,\"factor\":1.0}");
        entry("minecraft:potion_contents", "$/custom_effects", "status effect", "{\"id\":\"minecraft:speed\",\"duration\":200,\"amplifier\":0}");
        entry("minecraft:suspicious_stew_effects", "$", "stew effect", "{\"id\":\"minecraft:speed\",\"duration\":160}");
        entry("minecraft:writable_book_content", "$/pages", "page", "\"\"",
            "field_purpose.book_page", null);
        entry("minecraft:written_book_content", "$/pages", "page", "\"\"",
            "field_purpose.book_page", null);
        entry("minecraft:firework_explosion", "$/colors", "color", "16777215");
        entry("minecraft:firework_explosion", "$/fade_colors", "fade color", "16777215");
        entry("minecraft:fireworks", "$/explosions", "explosion", "{\"shape\":\"small_ball\",\"colors\":[16777215]}");
        entry("minecraft:charged_projectiles", "$", "projectile", "{\"id\":\"minecraft:arrow\",\"count\":1}");
        entry("minecraft:bundle_contents", "$", "item", "{\"id\":\"minecraft:stone\",\"count\":1}");
        entry("minecraft:container", "$", "item", "{\"id\":\"minecraft:stone\",\"count\":1}");
        entry("minecraft:recipes", "$", "recipe", "\"minecraft:acacia_boat\"",
            "field_purpose.recipe_identifier", "minecraft:acacia_boat");
        entry("minecraft:banner_patterns", "$", "banner layer", "{\"pattern\":\"minecraft:base\",\"color\":\"white\"}");
        entry("minecraft:pot_decorations", "$", "sherd", "\"minecraft:brick\"",
            "field_purpose.item_identifier", "minecraft:brick");
        entry("minecraft:profile", "$/properties", "profile property", "{\"name\":\"textures\",\"value\":\"\"}");
        entry("minecraft:bees", "$", "bee", "{\"entity_data\":{\"id\":\"minecraft:bee\"},\"ticks_in_hive\":0,\"min_ticks_in_hive\":600}");
        entry("minecraft:can_break", "$", "block predicate", "{\"blocks\":\"minecraft:stone\"}");
        entry("minecraft:can_place_on", "$", "block predicate", "{\"blocks\":\"minecraft:stone\"}");

        collection("minecraft:damage_resistant", "$/types", "damage_type", "minecraft:in_fire", "damage type");
        collection("minecraft:repairable", "$/items", "item", "minecraft:iron_ingot", "repair item");
        collection("minecraft:tool", "$/rules/*/blocks", "block", "minecraft:stone", "block");
        collection("minecraft:blocks_attacks", "$/bypassed_by", "damage_type", "minecraft:bypasses_shield", "damage type");
        collection("minecraft:blocks_attacks", "$/damage_reductions/*/type", "damage_type", "minecraft:in_fire", "damage type");
        collection("minecraft:can_break", "$/blocks", "block", "minecraft:stone", "block");
        collection("minecraft:can_break", "$/*/blocks", "block", "minecraft:stone", "block");
        collection("minecraft:can_place_on", "$/blocks", "block", "minecraft:stone", "block");
        collection("minecraft:can_place_on", "$/*/blocks", "block", "minecraft:stone", "block");
        collection("minecraft:equippable", "$/allowed_entities", "entity_type", "minecraft:player", "entity type");
        collection("minecraft:provides_banner_patterns", "$", "banner_pattern", "minecraft:base", "banner pattern");
    }

    private ComponentSchemas() {
    }

    static List<Field> missingFields(
        String componentId,
        String path,
        JsonObject current,
        DataComponentType<?> type
    ) {
        Map<String, Field> available = new LinkedHashMap<>();
        for (Field field : FIELDS.getOrDefault(key(componentId, path), List.of())) {
            if (!path.equals("$") || EditorVersionProfile.supportsRootField(type, field.key())) {
                available.put(field.key(), field);
            }
        }
        if (path.equals("$")) {
            for (String fieldName : CodecFieldDiscovery.fieldsFor(type)) {
                available.putIfAbsent(fieldName, discoveredField(fieldName));
            }
        }
        return available.values().stream()
            .filter(field -> !current.has(field.key()))
            .toList();
    }

    static Field field(String componentId, String path, String fieldName) {
        for (Field field : FIELDS.getOrDefault(key(componentId, path), List.of())) {
            if (field.key().equals(fieldName)) return field;
        }
        return null;
    }

    static JsonObject requiredStarter(String componentId, DataComponentType<?> type) {
        List<Field> fields = FIELDS.get(key(componentId, "$"));
        if (fields == null) {
            return null;
        }
        JsonObject starter = new JsonObject();
        for (Field field : fields) {
            if (field.required() && EditorVersionProfile.supportsRootField(type, field.key())) {
                starter.add(field.key(), field.newValue());
            }
        }
        return starter;
    }

    static Set<String> declaredRootFields(String componentId) {
        Set<String> result = new LinkedHashSet<>();
        for (Field field : FIELDS.getOrDefault(key(componentId, "$"), List.of())) {
            result.add(field.key());
        }
        return Set.copyOf(result);
    }

    static boolean isRemovable(String componentId, String path, String fieldName) {
        for (Field field : FIELDS.getOrDefault(key(componentId, path), List.of())) {
            if (field.key().equals(fieldName)) {
                return !field.required();
            }
        }
        return allowsCustomFields(componentId, path) && !fieldName.equals("id");
    }

    static boolean isRequired(String componentId, String path, String fieldName) {
        for (Field field : FIELDS.getOrDefault(key(componentId, path), List.of())) {
            if (field.key().equals(fieldName)) {
                return field.required();
            }
        }
        return false;
    }

    static ArrayEntry arrayEntry(String componentId, String path) {
        return ARRAY_ENTRIES.get(key(componentId, path));
    }

    static IdentifierCollection identifierCollection(String componentId, String path) {
        return IDENTIFIER_COLLECTIONS.get(key(componentId, normalizeCollectionPath(path)));
    }

    static boolean allowsCustomFields(String componentId, String path) {
        if (!path.equals("$")) {
            return componentId.equals("minecraft:custom_data")
                || componentId.equals("minecraft:bucket_entity_data")
                || componentId.equals("minecraft:block_entity_data");
        }
        return componentId.equals("minecraft:custom_data")
            || componentId.equals("minecraft:bucket_entity_data")
            || componentId.equals("minecraft:block_entity_data")
            || componentId.equals("minecraft:block_state")
            || componentId.equals("minecraft:debug_stick_state")
            || componentId.equals("minecraft:map_decorations");
    }

    static JsonElement customFieldValue(String componentId) {
        if (componentId.equals("minecraft:map_decorations")) {
            return object("{\"type\":\"minecraft:player\",\"x\":0.0,\"z\":0.0,\"rotation\":0.0}");
        }
        return null;
    }

    private static void register(String componentId, Field... fields) {
        FIELDS.put(key(componentId, "$"), List.of(fields));
    }

    private static void registerAt(String componentId, String path, Field... fields) {
        FIELDS.put(key(componentId, path), List.of(fields));
    }

    private static void entry(String componentId, String path, String label, String json) {
        JsonElement defaultValue = JsonParser.parseString(json);
        ARRAY_ENTRIES.put(key(componentId, path), new ArrayEntry(
            label,
            defaultValue,
            null,
            primitiveExample(defaultValue)
        ));
    }

    private static void entry(
        String componentId,
        String path,
        String label,
        String json,
        String purposeHintKey,
        String syntaxExample
    ) {
        ARRAY_ENTRIES.put(
            key(componentId, path),
            new ArrayEntry(label, JsonParser.parseString(json), purposeHintKey, syntaxExample)
        );
    }

    private static void collection(
        String componentId,
        String path,
        String registryKind,
        String fallbackId,
        String entryLabel
    ) {
        IDENTIFIER_COLLECTIONS.put(
            key(componentId, path),
            new IdentifierCollection(
                registryKind,
                fallbackId,
                entryLabel,
                switch (registryKind) {
                    case "item" -> "field_purpose.item_identifier";
                    case "block" -> "field_purpose.block_identifier";
                    case "entity_type" -> "field_purpose.entity_identifier";
                    case "damage_type" -> "field_purpose.damage_type_identifier";
                    case "banner_pattern" -> "field_purpose.banner_pattern_identifier";
                    default -> "field_purpose.registered_value";
                }
            )
        );
    }

    private static Field field(String key, String label, String description, Object defaultValue, boolean required) {
        JsonElement encoded = defaultValue instanceof JsonElement json
            ? json
            : JsonParser.parseString(defaultValue instanceof String text ? quote(text) : defaultValue.toString());
        return new Field(key, label, description, encoded, null, primitiveExample(encoded), required);
    }

    private static Field field(
        String key,
        String label,
        String description,
        Object defaultValue,
        boolean required,
        String purposeHintKey,
        String syntaxExample
    ) {
        JsonElement encoded = defaultValue instanceof JsonElement json
            ? json
            : JsonParser.parseString(defaultValue instanceof String text ? quote(text) : defaultValue.toString());
        return new Field(key, label, description, encoded, purposeHintKey, syntaxExample, required);
    }

    private static Field discoveredField(String key) {
        return new Field(
            key,
            humanize(key),
            "Discovered from the installed component codec.",
            null,
            null,
            null,
            false
        );
    }

    private static String primitiveExample(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) return null;
        String example = value.getAsString();
        return example.isBlank() ? null : example;
    }

    private static JsonElement array() {
        return JsonParser.parseString("[]");
    }

    private static JsonElement array(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static JsonElement object(String json) {
        return JsonParser.parseString(json);
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String key(String componentId, String path) {
        return componentId + "@" + normalizeCollectionPath(path);
    }

    private static String normalizeCollectionPath(String path) {
        return path.replaceAll("/\\d+(?=/|$)", "/*");
    }

    private static String humanize(String source) {
        String normalized = source.replace('_', ' ').trim();
        return normalized.isEmpty()
            ? source
            : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    record Field(
        String key,
        String label,
        String description,
        JsonElement defaultValue,
        String purposeHintKey,
        String syntaxExample,
        boolean required
    ) {
        JsonElement newValue() {
            return defaultValue == null ? null : defaultValue.deepCopy();
        }

        boolean discovered() {
            return defaultValue == null;
        }
    }

    record ArrayEntry(String label, JsonElement defaultValue, String purposeHintKey, String syntaxExample) {
        JsonElement newValue() {
            return defaultValue.deepCopy();
        }
    }

    record IdentifierCollection(
        String registryKind,
        String fallbackId,
        String entryLabel,
        String purposeHintKey
    ) {
    }
}
