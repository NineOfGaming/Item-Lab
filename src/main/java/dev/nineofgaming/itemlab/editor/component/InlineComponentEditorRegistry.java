package dev.nineofgaming.itemlab.editor.component;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Intentional Components-tab editor registrations, independent of top-level tab routing.
 */
public final class InlineComponentEditorRegistry {
    private static final Map<String, Kind> REGISTRATIONS = registrations();

    private InlineComponentEditorRegistry() {
    }

    public static boolean supports(String componentId) {
        return REGISTRATIONS.containsKey(componentId);
    }

    public static boolean supports(DataComponentType<?> type) {
        return kind(type) != null;
    }

    public static Kind kind(DataComponentType<?> type) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return id == null ? null : REGISTRATIONS.get(id.toString());
    }

    public static Set<String> registeredIds() {
        return REGISTRATIONS.keySet();
    }

    private static Map<String, Kind> registrations() {
        Map<String, Kind> result = new LinkedHashMap<>();
        result.put("minecraft:can_break", Kind.ADVENTURE_PREDICATE);
        result.put("minecraft:can_place_on", Kind.ADVENTURE_PREDICATE);
        result.put("minecraft:recipes", Kind.RECIPES);
        result.put("minecraft:bees", Kind.BEES);
        result.put("minecraft:sulfur_cube_content", Kind.SULFUR_CUBE_CONTENT);
        for (VariantComponents.Definition definition : VariantComponents.definitions()) {
            if (result.put(definition.componentId(), Kind.ENTITY_VARIANT) != null) {
                throw new IllegalArgumentException("Duplicate inline editor " + definition.componentId());
            }
        }
        return Map.copyOf(result);
    }

    public enum Kind {
        ADVENTURE_PREDICATE,
        RECIPES,
        BEES,
        SULFUR_CUBE_CONTENT,
        ENTITY_VARIANT
    }
}
