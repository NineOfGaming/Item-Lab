package dev.nineofgaming.itemlab.editor.component;

import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;

import java.util.Set;

/**
 * Reviewed coverage classification for every vanilla item component in supported targets.
 */
public final class ComponentCoverage {
    private static final Set<String> REVIEWED_COMPONENTS = Set.of(
        "minecraft:additional_trade_cost",
        "minecraft:attack_range",
        "minecraft:attribute_modifiers",
        "minecraft:axolotl/variant",
        "minecraft:banner_patterns",
        "minecraft:base_color",
        "minecraft:bees",
        "minecraft:block_entity_data",
        "minecraft:block_state",
        "minecraft:blocks_attacks",
        "minecraft:break_sound",
        "minecraft:bucket_entity_data",
        "minecraft:bundle_contents",
        "minecraft:can_break",
        "minecraft:can_place_on",
        "minecraft:cat/collar",
        "minecraft:cat/sound_variant",
        "minecraft:cat/variant",
        "minecraft:charged_projectiles",
        "minecraft:chicken/sound_variant",
        "minecraft:chicken/variant",
        "minecraft:consumable",
        "minecraft:container",
        "minecraft:container_loot",
        "minecraft:cow/sound_variant",
        "minecraft:cow/variant",
        "minecraft:creative_slot_lock",
        "minecraft:custom_data",
        "minecraft:custom_model_data",
        "minecraft:custom_name",
        "minecraft:damage",
        "minecraft:damage_resistant",
        "minecraft:damage_type",
        "minecraft:death_protection",
        "minecraft:debug_stick_state",
        "minecraft:dye",
        "minecraft:dyed_color",
        "minecraft:enchantable",
        "minecraft:enchantment_glint_override",
        "minecraft:enchantments",
        "minecraft:entity_data",
        "minecraft:equippable",
        "minecraft:firework_explosion",
        "minecraft:fireworks",
        "minecraft:food",
        "minecraft:fox/variant",
        "minecraft:frog/variant",
        "minecraft:glider",
        "minecraft:horse/variant",
        "minecraft:instrument",
        "minecraft:intangible_projectile",
        "minecraft:item_model",
        "minecraft:item_name",
        "minecraft:jukebox_playable",
        "minecraft:kinetic_weapon",
        "minecraft:llama/variant",
        "minecraft:lock",
        "minecraft:lodestone_tracker",
        "minecraft:lore",
        "minecraft:map_color",
        "minecraft:map_decorations",
        "minecraft:map_id",
        "minecraft:map_post_processing",
        "minecraft:max_damage",
        "minecraft:max_stack_size",
        "minecraft:minimum_attack_charge",
        "minecraft:mooshroom/variant",
        "minecraft:note_block_sound",
        "minecraft:ominous_bottle_amplifier",
        "minecraft:painting/variant",
        "minecraft:parrot/variant",
        "minecraft:piercing_weapon",
        "minecraft:pig/sound_variant",
        "minecraft:pig/variant",
        "minecraft:pot_decorations",
        "minecraft:potion_contents",
        "minecraft:potion_duration_scale",
        "minecraft:profile",
        "minecraft:provides_banner_patterns",
        "minecraft:provides_trim_material",
        "minecraft:rabbit/variant",
        "minecraft:rarity",
        "minecraft:recipes",
        "minecraft:repair_cost",
        "minecraft:repairable",
        "minecraft:salmon/size",
        "minecraft:sheep/color",
        "minecraft:shulker/color",
        "minecraft:stored_enchantments",
        "minecraft:sulfur_cube_content",
        "minecraft:suspicious_stew_effects",
        "minecraft:swing_animation",
        "minecraft:tool",
        "minecraft:tooltip_display",
        "minecraft:tooltip_style",
        "minecraft:trim",
        "minecraft:tropical_fish/base_color",
        "minecraft:tropical_fish/pattern",
        "minecraft:tropical_fish/pattern_color",
        "minecraft:unbreakable",
        "minecraft:use_cooldown",
        "minecraft:use_effects",
        "minecraft:use_remainder",
        "minecraft:villager/variant",
        "minecraft:weapon",
        "minecraft:wolf/collar",
        "minecraft:wolf/sound_variant",
        "minecraft:wolf/variant",
        "minecraft:writable_book_content",
        "minecraft:written_book_content",
        "minecraft:zombie_nautilus/variant"
    );
    private static final Set<String> NETWORK_ONLY = Set.of(
        "minecraft:additional_trade_cost",
        "minecraft:creative_slot_lock",
        "minecraft:map_post_processing"
    );
    private static final Set<String> GENERATED_STRUCTURED = Set.of(
        "minecraft:cat/collar",
        "minecraft:sheep/color",
        "minecraft:shulker/color",
        "minecraft:tropical_fish/base_color",
        "minecraft:tropical_fish/pattern_color",
        "minecraft:wolf/collar"
    );
    private static final Set<String> RAW_PERSISTENT_FALLBACK = Set.of();

    private ComponentCoverage() {
    }

    public static Category classify(ComponentOption option, boolean topLevelDedicated) {
        String id = option.id();
        if (!REVIEWED_COMPONENTS.contains(id)) return Category.UNCLASSIFIED;
        if (NETWORK_ONLY.contains(id)) return Category.NETWORK_ONLY;
        if (InlineComponentEditorRegistry.supports(id)) {
            return Category.INLINE_DEDICATED;
        }
        if (GENERATED_STRUCTURED.contains(id)) return Category.GENERATED_STRUCTURED;
        if (RAW_PERSISTENT_FALLBACK.contains(id)) return Category.RAW_PERSISTENT_FALLBACK;
        return topLevelDedicated ? Category.TOP_LEVEL_DEDICATED : Category.UNCLASSIFIED;
    }

    public enum Category {
        TOP_LEVEL_DEDICATED,
        INLINE_DEDICATED,
        GENERATED_STRUCTURED,
        RAW_PERSISTENT_FALLBACK,
        NETWORK_ONLY,
        UNCLASSIFIED
    }
}
