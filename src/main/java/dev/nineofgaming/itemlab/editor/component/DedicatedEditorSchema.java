package dev.nineofgaming.itemlab.editor.component;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;

import java.util.List;
import java.util.Set;

/**
 * Root codec fields intentionally covered by each purpose-built component editor.
 */
public final class DedicatedEditorSchema {
    private DedicatedEditorSchema() {
    }

    public static Coverage coverage(DataComponentType<?> type) {
        Set<String> handled = fields(type);
        if (handled.isEmpty() || !EditorVersionProfile.hasKnownRootShape(type)) {
            return Coverage.EXACT;
        }
        return new Coverage(
            EditorVersionProfile.handledFieldsNotSupportedBy(type, handled),
            EditorVersionProfile.fieldsNotHandledBy(type, handled)
        );
    }

    public static Set<String> fields(DataComponentType<?> type) {
        if (type == DataComponents.FOOD) {
            return Set.of("nutrition", "saturation", "can_always_eat");
        }
        if (type == DataComponents.CONSUMABLE) {
            return Set.of("consume_seconds", "animation", "sound", "has_consume_particles", "on_consume_effects");
        }
        if (type == DataComponents.USE_EFFECTS) {
            return Set.of("can_sprint", "interact_vibrations", "speed_multiplier");
        }
        if (type == DataComponents.USE_REMAINDER) return Set.of("id", "count");
        if (type == DataComponents.USE_COOLDOWN) return Set.of("seconds", "cooldown_group");
        if (type == DataComponents.DEATH_PROTECTION) return Set.of("death_effects");
        if (type == DataComponents.SWING_ANIMATION) return Set.of("type", "duration");
        if (type == DataComponents.WEAPON) {
            return Set.of("item_damage_per_attack", "disable_blocking_for_seconds");
        }
        if (type == DataComponents.PIERCING_WEAPON) {
            return Set.of("deals_knockback", "dismounts", "sound", "hit_sound");
        }
        if (type == DataComponents.KINETIC_WEAPON) {
            return Set.of(
                "contact_cooldown_ticks", "delay_ticks", "dismount_conditions", "knockback_conditions",
                "damage_conditions", "forward_movement", "damage_multiplier", "sound", "hit_sound"
            );
        }
        if (type == DataComponents.INSTRUMENT) {
            return Set.of("sound_event", "use_duration", "range", "description");
        }
        if (type == DataComponents.TOOL) {
            return Set.of("rules", "default_mining_speed", "damage_per_block", "can_destroy_blocks_in_creative");
        }
        if (type == DataComponents.ATTACK_RANGE) {
            return Set.of("min_reach", "max_reach", "min_creative_reach", "max_creative_reach", "hitbox_margin", "mob_factor");
        }
        if (type == DataComponents.DAMAGE_RESISTANT) return Set.of("types");
        if (type == DataComponents.REPAIRABLE) return Set.of("items");
        if (type == DataComponents.BLOCKS_ATTACKS) {
            return Set.of(
                "block_delay_seconds", "disable_cooldown_scale", "damage_reductions", "item_damage",
                "bypassed_by", "block_sound", "disabled_sound"
            );
        }
        if (type == DataComponents.EQUIPPABLE) {
            return Set.of(
                "slot", "equip_sound", "asset_id", "camera_overlay", "allowed_entities", "dispensable",
                "swappable", "damage_on_hurt", "equip_on_interact", "can_be_sheared", "shearing_sound"
            );
        }
        if (type == DataComponents.TRIM) return Set.of("material", "pattern");
        if (type == DataComponents.CUSTOM_MODEL_DATA) return Set.of("floats", "flags", "strings", "colors");
        if (type == DataComponents.TOOLTIP_DISPLAY) return Set.of("hide_tooltip", "hidden_components");
        if (type == DataComponents.CAN_BREAK || type == DataComponents.CAN_PLACE_ON) {
            return Set.of("blocks", "state", "nbt", "components", "predicates");
        }
        if (type == DataComponents.WRITABLE_BOOK_CONTENT) return Set.of("pages");
        if (type == DataComponents.WRITTEN_BOOK_CONTENT) {
            return Set.of("title", "author", "generation", "pages", "resolved");
        }
        if (type == DataComponents.POTION_CONTENTS) {
            return Set.of("potion", "custom_color", "custom_effects", "custom_name");
        }
        if (type == DataComponents.LODESTONE_TRACKER) return Set.of("target", "tracked");
        if (type == DataComponents.LOCK) return Set.of("items", "count", "components", "predicates");
        if (type == DataComponents.CONTAINER_LOOT) return Set.of("loot_table", "seed");
        if (type == DataComponents.FIREWORKS) return Set.of("flight_duration", "explosions");
        if (type == DataComponents.FIREWORK_EXPLOSION) {
            return Set.of("shape", "colors", "fade_colors", "has_trail", "has_twinkle");
        }
        if (type == DataComponents.PROFILE) {
            return Set.of("name", "id", "properties", "texture", "cape", "elytra", "model");
        }
        //? if >=26.2 {
        if (type == DataComponents.SULFUR_CUBE_CONTENT) return Set.of("id", "count", "components");
        //?}
        return Set.of();
    }

    public record Coverage(List<String> missingFields, List<String> extraFields) {
        private static final Coverage EXACT = new Coverage(List.of(), List.of());

        public boolean isExact() {
            return missingFields.isEmpty() && extraFields.isEmpty();
        }
    }
}
