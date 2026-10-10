package dev.nineofgaming.itemlab.editor.component;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;

import java.util.Map;
import java.util.Set;

/**
 * Deterministic item-aware and frequently-used suggestions for the Components browser.
 */
public final class ComponentRecommendations {
    private static final Set<String> FREQUENTLY_USED = Set.of(
        "minecraft:custom_name",
        "minecraft:lore",
        "minecraft:custom_model_data",
        "minecraft:item_model",
        "minecraft:tooltip_display",
        "minecraft:enchantment_glint_override",
        "minecraft:rarity"
    );

    private static final Map<String, Set<String>> ITEM_COMPONENTS = Map.ofEntries(
        Map.entry("minecraft:sulfur_cube_bucket", Set.of("minecraft:sulfur_cube_content")),
        Map.entry("minecraft:bee_nest", Set.of("minecraft:bees")),
        Map.entry("minecraft:beehive", Set.of("minecraft:bees")),
        Map.entry("minecraft:compass", Set.of("minecraft:lodestone_tracker")),
        Map.entry("minecraft:player_head", Set.of("minecraft:profile", "minecraft:note_block_sound")),
        Map.entry("minecraft:decorated_pot", Set.of("minecraft:pot_decorations")),
        Map.entry("minecraft:writable_book", Set.of("minecraft:writable_book_content")),
        Map.entry("minecraft:written_book", Set.of("minecraft:written_book_content")),
        Map.entry("minecraft:firework_star", Set.of("minecraft:firework_explosion")),
        Map.entry("minecraft:firework_rocket", Set.of("minecraft:fireworks")),
        Map.entry("minecraft:crossbow", Set.of("minecraft:charged_projectiles")),
        Map.entry("minecraft:suspicious_stew", Set.of("minecraft:suspicious_stew_effects")),
        Map.entry("minecraft:ominous_bottle", Set.of("minecraft:ominous_bottle_amplifier")),
        Map.entry("minecraft:goat_horn", Set.of("minecraft:instrument")),
        Map.entry("minecraft:knowledge_book", Set.of("minecraft:recipes")),
        Map.entry("minecraft:map", Set.of("minecraft:map_post_processing")),
        Map.entry("minecraft:filled_map", Set.of(
            "minecraft:map_id",
            "minecraft:map_decorations",
            "minecraft:map_color",
            "minecraft:map_post_processing"
        )),
        Map.entry("minecraft:potion", Set.of("minecraft:potion_contents")),
        Map.entry("minecraft:splash_potion", Set.of("minecraft:potion_contents")),
        Map.entry("minecraft:lingering_potion", Set.of("minecraft:potion_contents")),
        Map.entry("minecraft:tipped_arrow", Set.of("minecraft:potion_contents"))
    );

    private static final Set<String> ENTITY_BUCKETS = Set.of(
        "minecraft:cod_bucket",
        "minecraft:salmon_bucket",
        "minecraft:pufferfish_bucket",
        "minecraft:tropical_fish_bucket",
        "minecraft:axolotl_bucket",
        "minecraft:tadpole_bucket"
    );

    private ComponentRecommendations() {
    }

    public static Kind classify(ItemStack stack, String componentId) {
        if (stack == null || stack.isEmpty() || componentId == null || componentId.isBlank()) {
            return Kind.NONE;
        }
        Identifier itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = itemKey.toString();
        if (isRelated(stack, itemId, componentId)) return Kind.RELATED;
        return FREQUENTLY_USED.contains(componentId) ? Kind.FREQUENTLY_USED : Kind.NONE;
    }

    private static boolean isRelated(ItemStack stack, String itemId, String componentId) {
        if (ITEM_COMPONENTS.getOrDefault(itemId, Set.of()).contains(componentId)) return true;
        if (componentId.equals("minecraft:block_entity_data")
            && stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof EntityBlock) {
            return true;
        }
        if (componentId.equals("minecraft:entity_data") && itemId.endsWith("_spawn_egg")) return true;
        if (componentId.equals("minecraft:bucket_entity_data") && ENTITY_BUCKETS.contains(itemId)) return true;
        if (componentId.equals("minecraft:banner_patterns")
            && (itemId.endsWith("_banner") || itemId.equals("minecraft:shield"))) {
            return true;
        }
        if (componentId.equals("minecraft:trim") && isArmor(itemId)) return true;
        return componentId.equals("minecraft:dyed_color")
            && (itemId.startsWith("minecraft:leather_") || itemId.equals("minecraft:wolf_armor"));
    }

    private static boolean isArmor(String itemId) {
        return itemId.endsWith("_helmet")
            || itemId.endsWith("_chestplate")
            || itemId.endsWith("_leggings")
            || itemId.endsWith("_boots");
    }

    public enum Kind {
        NONE,
        RELATED,
        FREQUENTLY_USED
    }
}
