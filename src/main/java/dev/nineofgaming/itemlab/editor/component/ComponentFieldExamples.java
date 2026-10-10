package dev.nineofgaming.itemlab.editor.component;

import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;

import java.util.List;
import java.util.Locale;

/**
 * Presentation-only purpose hints and syntax examples for generated component fields.
 */
final class ComponentFieldExamples {
    private ComponentFieldExamples() {
    }

    static String resolve(
        String componentId,
        String path,
        String fieldKey,
        String schemaExample,
        List<IdSuggestion> suggestions
    ) {
        if (schemaExample != null && !schemaExample.isBlank()) {
            return schemaExample;
        }
        if (componentId.equals("minecraft:block_state")
            || componentId.equals("minecraft:debug_stick_state")) {
            if (fieldKey.equals("facing")) return "north";
            if (fieldKey.equals("axis")) return "y";
            if (!fieldKey.equals("$") && !fieldKey.equals("key") && !fieldKey.equals("$key")) {
                return "value";
            }
        }

        List<IdSuggestion> available = suggestions == null ? List.of() : suggestions;
        String fieldScope = (path + " " + fieldKey).toLowerCase(Locale.ROOT);
        String normalized = path.equals("$") || fieldKey.equals("$")
            ? componentId.toLowerCase(Locale.ROOT) + " " + fieldScope
            : fieldScope;
        if ((path.equals("$") || fieldKey.equals("$")) && isOpaqueDataComponent(componentId)) {
            return "{CustomName:'example'}";
        }
        for (String preferred : preferredIdentifiers(normalized)) {
            if (available.stream().anyMatch(suggestion -> suggestion.id().equals(preferred))) {
                return preferred;
            }
        }
        if (!available.isEmpty()) {
            return available.getFirst().id();
        }

        if (normalized.contains("translation_key_suffix")) return "custom";
        if (normalized.contains("translation_key")) return "item.minecraft.diamond_sword";
        if (normalized.contains("uuid")) return "00000000-0000-0000-0000-000000000000";
        if (normalized.contains("cooldown_group")) return "minecraft:food";
        if (normalized.contains("tooltip_style")) return "minecraft:default";
        if ((componentId.equals("minecraft:map_decorations") && fieldKey.equals("type"))
            || normalized.contains("map_decoration")
            || normalized.contains("decoration_type")) {
            return "minecraft:player";
        }
        if (normalized.contains("modifier") && normalized.contains("id")) return "itemlab:attack_damage";
        if (normalized.contains("variant")) return "examplemod:variant";
        if (normalized.contains("dimension")) return "minecraft:overworld";
        if (normalized.contains("sound")) return "minecraft:item.shield.block";
        if (normalized.contains("loot_table")) return "minecraft:empty";
        if (normalized.contains("recipe")) return "minecraft:acacia_boat";
        if (normalized.contains("effect")) return "minecraft:speed";
        if (normalized.contains("enchant")) return "minecraft:sharpness";
        if (normalized.contains("attribute")) return "minecraft:attack_damage";
        if (normalized.contains("entity")) return "minecraft:pig";
        if (normalized.contains("block")) return "minecraft:stone";
        if (normalized.contains("item") || normalized.contains("model")) return "minecraft:stone";
        if (normalized.contains("texture") || normalized.contains("asset")) {
            return "minecraft:entity/player/wide/steve";
        }
        if (normalized.contains("nbt") || normalized.contains("data")) return "{example:1b}";
        if (fieldKey.equals("name")) return "example";
        if (fieldKey.equals("key")) return "example_key";
        return "example";
    }

    /**
     * Resolves a translated-purpose vocabulary key without consulting suggestion order or values.
     * Registry suggestions remain syntax help and autocomplete data, never the source of a purpose hint.
     */
    static String purposeKey(
        String componentId,
        String path,
        String fieldKey,
        String schemaPurposeKey,
        boolean hasRegistrySuggestions
    ) {
        if (schemaPurposeKey != null && !schemaPurposeKey.isBlank()) {
            return schemaPurposeKey;
        }

        String fieldScope = (path + " " + fieldKey).toLowerCase(Locale.ROOT);
        String component = componentId.toLowerCase(Locale.ROOT);
        String normalized = path.equals("$") || fieldKey.equals("$")
            ? component + " " + fieldScope
            : fieldScope;
        if ((path.equals("$") || fieldKey.equals("$")) && isOpaqueDataComponent(component)) {
            return "field_purpose.data_value";
        }
        boolean profileComponent = component.equals("minecraft:profile");
        boolean profileProperty = profileComponent && fieldScope.contains("/properties/");
        if (normalized.contains("translation_key_suffix")) return "field_purpose.translation_key_suffix";
        if (normalized.contains("translation_key")) return "field_purpose.translation_key";
        if (normalized.contains("uuid")
            || normalized.endsWith("/id id") && profileComponent) {
            return "field_purpose.uuid";
        }
        if (profileProperty && fieldKey.equals("name")) return "field_purpose.profile_property_name";
        if (profileProperty && fieldKey.equals("value")) return "field_purpose.profile_property_payload";
        if (profileProperty && fieldKey.equals("signature")) return "field_purpose.profile_property_signature";
        if (profileComponent && fieldKey.equals("name")) return "field_purpose.player_name";
        if (normalized.contains("minimum") || normalized.matches(".*(?:^|[/_ ])min(?:imum)?(?:$|[/_ ]).*")) {
            return "field_purpose.minimum_value";
        }
        if (normalized.contains("maximum") || normalized.matches(".*(?:^|[/_ ])max(?:imum)?(?:$|[/_ ]).*")) {
            return "field_purpose.maximum_value";
        }
        if (normalized.contains("cooldown_group")) return "field_purpose.cooldown_group_identifier";
        if (normalized.contains("duration") || normalized.contains("delay") || normalized.contains("cooldown")) {
            return normalized.contains("second")
                ? "field_purpose.duration_seconds"
                : "field_purpose.duration_ticks";
        }
        if (normalized.contains("sound")) return "field_purpose.sound_identifier";
        if (normalized.contains("loot_table")) return "field_purpose.loot_table_identifier";
        if (normalized.contains("recipe")) return "field_purpose.recipe_identifier";
        if (normalized.contains("potion")) return "field_purpose.potion_identifier";
        if (normalized.contains("effect")) return "field_purpose.effect_identifier";
        if (normalized.contains("enchant")) return "field_purpose.enchantment_identifier";
        if (normalized.contains("modifier") && normalized.contains("id")) {
            return "field_purpose.modifier_identifier";
        }
        if (normalized.contains("attribute")) return "field_purpose.attribute_identifier";
        if (component.equals("minecraft:block_state") || component.equals("minecraft:debug_stick_state")) {
            return fieldKey.equals("$") || fieldKey.equals("key")
                ? "field_purpose.block_state_property"
                : "field_purpose.block_state_value";
        }
        if (component.equals("minecraft:map_decorations") && fieldKey.equals("key")) {
            return "field_purpose.decoration_name";
        }
        if ((component.equals("minecraft:map_decorations") && fieldKey.equals("type"))
            || normalized.contains("map_decoration")
            || normalized.contains("decoration_type")) {
            return "field_purpose.map_decoration_identifier";
        }
        if (normalized.contains("tooltip_style")) return "field_purpose.tooltip_style_identifier";
        if (normalized.contains("damage_type")) return "field_purpose.damage_type_identifier";
        if (normalized.contains("banner_pattern")) return "field_purpose.banner_pattern_identifier";
        if (normalized.contains("instrument")) return "field_purpose.instrument_identifier";
        if (normalized.contains("jukebox") || normalized.contains("song")) {
            return "field_purpose.jukebox_song_identifier";
        }
        if (normalized.contains("trim_material")) return "field_purpose.trim_material_identifier";
        if (normalized.contains("trim_pattern")) return "field_purpose.trim_pattern_identifier";
        if (normalized.contains("block_entity")) return "field_purpose.block_entity_identifier";
        if (normalized.contains("block")) return "field_purpose.block_identifier";
        if (normalized.contains("entity")) return "field_purpose.entity_identifier";
        if (normalized.contains("item") && !normalized.contains("item_model")) {
            return "field_purpose.item_identifier";
        }
        if (normalized.contains("model")) return "field_purpose.model_identifier";
        if (normalized.contains("texture")) return "field_purpose.texture_identifier";
        if (normalized.contains("asset")) return "field_purpose.asset_identifier";
        if (normalized.contains("dimension")) return "field_purpose.dimension_identifier";
        if (normalized.contains("color")) return "field_purpose.color_value";
        if (normalized.contains("seed")) return "field_purpose.seed_value";
        if (normalized.contains("variant")) return "field_purpose.variant_value";
        if (normalized.contains("count")
            || normalized.contains("level")
            || normalized.contains("amplifier")
            || normalized.contains("damage")
            || normalized.contains("cost")
            || normalized.contains("speed")
            || normalized.contains("factor")
            || normalized.contains("scale")
            || normalized.contains("saturation")
            || normalized.contains("angle")
            || normalized.contains("rotation")
            || normalized.contains("reach")
            || normalized.contains("margin")
            || normalized.contains("probability")
            || normalized.contains("diameter")
            || normalized.contains("nutrition")) {
            return "field_purpose.numeric_value";
        }
        if (fieldKey.equals("key") || fieldKey.equals("$key")) return "field_purpose.entry_key";
        if (hasRegistrySuggestions) return "field_purpose.registered_value";
        if (component.equals("minecraft:custom_data")
            || component.equals("minecraft:bucket_entity_data")
            || component.equals("minecraft:block_entity_data")) {
            return "field_purpose.data_value";
        }
        if (path.matches(".*/\\d+$")) return "field_purpose.list_entry";
        return "field_purpose.field_value";
    }

    static String customFieldKey(String componentId) {
        return switch (componentId) {
            case "minecraft:block_state", "minecraft:debug_stick_state" -> "facing";
            case "minecraft:map_decorations" -> "home";
            case "minecraft:bucket_entity_data", "minecraft:block_entity_data" -> "CustomName";
            default -> "example_property";
        };
    }

    static String number(String componentId, String path, String fieldKey) {
        String normalized = (componentId + " " + path + " " + fieldKey).toLowerCase(Locale.ROOT);
        if (normalized.contains("max_stack_size")) return "64";
        if (normalized.contains("max_damage")) return "250";
        if (normalized.contains("color")) return "16777215";
        if (normalized.contains("angle") || normalized.contains("rotation")) return "90";
        if (normalized.contains("duration") || normalized.contains("ticks")) return "20";
        if (normalized.contains("speed")
            || normalized.contains("factor")
            || normalized.contains("scale")
            || normalized.contains("saturation")) {
            return "1.0";
        }
        if (normalized.contains("count")
            || normalized.contains("level")
            || normalized.contains("amplifier")
            || normalized.contains("damage")
            || normalized.contains("cost")) {
            return "1";
        }
        return "0";
    }

    private static List<String> preferredIdentifiers(String normalized) {
        if (normalized.contains("sound")) {
            return List.of(
                "minecraft:item.shield.block",
                "minecraft:entity.item.break",
                "minecraft:block.note_block.harp"
            );
        }
        if (normalized.contains("loot_table")) return List.of("minecraft:empty");
        if (normalized.contains("recipe")) return List.of("minecraft:acacia_boat");
        if (normalized.contains("potion")) return List.of("minecraft:water");
        if (normalized.contains("effect")) return List.of("minecraft:speed");
        if (normalized.contains("enchant")) return List.of("minecraft:sharpness");
        if (normalized.contains("attribute")) return List.of("minecraft:attack_damage");
        if (normalized.contains("damage_type") || normalized.contains("damage type")) {
            return List.of("minecraft:in_fire");
        }
        if (normalized.contains("banner") && normalized.contains("pattern")) {
            return List.of("minecraft:base");
        }
        if (normalized.contains("block_entity")) return List.of("minecraft:chest");
        if (normalized.contains("entity")) return List.of("minecraft:pig", "minecraft:player");
        if (normalized.contains("block")) return List.of("minecraft:stone");
        if (normalized.contains("item") || normalized.contains("model")) return List.of("minecraft:stone");
        return List.of();
    }

    private static boolean isOpaqueDataComponent(String componentId) {
        return componentId.equals("minecraft:custom_data")
            || componentId.equals("minecraft:bucket_entity_data")
            || componentId.equals("minecraft:block_entity_data");
    }
}
