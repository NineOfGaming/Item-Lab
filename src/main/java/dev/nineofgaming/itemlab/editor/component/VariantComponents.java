package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reviewed metadata shared by every entity-variant component editor.
 */
public final class VariantComponents {
    private static final Map<String, Definition> DEFINITIONS = definitions(
        fixed("minecraft:axolotl/variant", "Axolotl variant", "lucy",
            "lucy", "wild", "gold", "cyan", "blue"),
        fixed("minecraft:fox/variant", "Fox variant", "red", "red", "snow"),
        fixed("minecraft:horse/variant", "Horse variant", "white",
            "white", "creamy", "chestnut", "brown", "black", "gray", "dark_brown"),
        fixed("minecraft:llama/variant", "Llama variant", "creamy",
            "creamy", "white", "brown", "gray"),
        fixed("minecraft:mooshroom/variant", "Mooshroom variant", "red", "red", "brown"),
        fixed("minecraft:parrot/variant", "Parrot variant", "red_blue",
            "red_blue", "blue", "green", "yellow_blue", "gray"),
        fixed("minecraft:rabbit/variant", "Rabbit variant", "brown",
            "brown", "white", "black", "white_splotched", "gold", "salt", "evil"),
        fixed("minecraft:salmon/size", "Salmon size", "medium", "small", "medium", "large"),
        fixed("minecraft:tropical_fish/pattern", "Tropical fish pattern", "kob",
            "kob", "sunstreak", "snooper", "dasher", "brinely", "spotty",
            "flopper", "stripey", "glitter", "blockfish", "betty", "clayfish"),
        registry("minecraft:cat/variant", "Cat variant", "cat_variant", "minecraft:tabby"),
        registry("minecraft:cat/sound_variant", "Cat sound variant", "cat_sound_variant", "minecraft:classic"),
        registry("minecraft:chicken/variant", "Chicken variant", "chicken_variant", "minecraft:temperate"),
        registry("minecraft:chicken/sound_variant", "Chicken sound variant", "chicken_sound_variant", "minecraft:classic"),
        registry("minecraft:cow/variant", "Cow variant", "cow_variant", "minecraft:temperate"),
        registry("minecraft:cow/sound_variant", "Cow sound variant", "cow_sound_variant", "minecraft:classic"),
        registry("minecraft:frog/variant", "Frog variant", "frog_variant", "minecraft:temperate"),
        registry("minecraft:painting/variant", "Painting variant", "painting_variant", "minecraft:kebab"),
        registry("minecraft:pig/variant", "Pig variant", "pig_variant", "minecraft:temperate"),
        registry("minecraft:pig/sound_variant", "Pig sound variant", "pig_sound_variant", "minecraft:classic"),
        registry("minecraft:villager/variant", "Villager variant", "villager_variant", "minecraft:plains"),
        registry("minecraft:wolf/variant", "Wolf variant", "wolf_variant", "minecraft:pale"),
        registry("minecraft:wolf/sound_variant", "Wolf sound variant", "wolf_sound_variant", "minecraft:classic"),
        registry("minecraft:zombie_nautilus/variant", "Zombie nautilus variant", "zombie_nautilus_variant", "minecraft:temperate")
    );

    private VariantComponents() {
    }

    public static boolean supports(String componentId) {
        return DEFINITIONS.containsKey(componentId);
    }

    public static boolean supports(DataComponentType<?> type) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return id != null && supports(id.toString());
    }

    public static Definition definition(String componentId) {
        return DEFINITIONS.get(componentId);
    }

    public static Collection<Definition> definitions() {
        return DEFINITIONS.values();
    }

    public static List<IdSuggestion> suggestions(String componentId, EditorCatalog catalog) {
        Definition definition = definition(componentId);
        if (definition == null) return List.of();
        List<IdSuggestion> source = definition.registryBacked()
            ? catalog.registryIds(definition.registryKind())
            : definition.fixedChoices();
        return source.stream().map(VariantComponents::friendlySuggestion).toList();
    }

    static List<IdSuggestion> suggestions(
        String componentId,
        Map<String, List<IdSuggestion>> registryIdsByKind
    ) {
        Definition definition = definition(componentId);
        if (definition == null) return List.of();
        List<IdSuggestion> source = definition.registryBacked()
            ? registryIdsByKind.getOrDefault(definition.registryKind(), List.of())
            : definition.fixedChoices();
        return source.stream().map(VariantComponents::friendlySuggestion).toList();
    }

    static JsonElement starter(String componentId, EditorCatalog catalog) {
        Definition definition = definition(componentId);
        if (definition == null) return null;
        List<IdSuggestion> suggestions = catalog == null
            ? List.of()
            : suggestions(componentId, catalog);
        String value = suggestions.isEmpty() ? definition.fallbackValue() : suggestions.getFirst().id();
        return new JsonPrimitive(value);
    }

    private static IdSuggestion friendlySuggestion(IdSuggestion source) {
        String displayName = source.displayName().equals(source.id())
            ? humanize(source.id())
            : source.displayName();
        return new IdSuggestion(
            source.id(),
            displayName,
            (displayName + " " + source.id()).toLowerCase(Locale.ROOT)
        );
    }

    private static Definition fixed(
        String componentId,
        String label,
        String fallbackValue,
        String... choices
    ) {
        List<IdSuggestion> suggestions = java.util.Arrays.stream(choices)
            .map(IdSuggestion::of)
            .toList();
        return new Definition(componentId, label, "", suggestions, fallbackValue);
    }

    private static Definition registry(
        String componentId,
        String label,
        String registryKind,
        String fallbackValue
    ) {
        return new Definition(componentId, label, registryKind, List.of(), fallbackValue);
    }

    private static Map<String, Definition> definitions(Definition... definitions) {
        Map<String, Definition> result = new LinkedHashMap<>();
        for (Definition definition : definitions) {
            if (result.put(definition.componentId(), definition) != null) {
                throw new IllegalArgumentException("Duplicate variant component " + definition.componentId());
            }
        }
        return Map.copyOf(result);
    }

    private static String humanize(String value) {
        String local = value.substring(value.indexOf(':') + 1)
            .replace('/', ' ')
            .replace('_', ' ')
            .trim();
        if (local.isEmpty()) return value;
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }

    public record Definition(
        String componentId,
        String label,
        String registryKind,
        List<IdSuggestion> fixedChoices,
        String fallbackValue
    ) {
        public Definition {
            fixedChoices = List.copyOf(fixedChoices);
        }

        public boolean registryBacked() {
            return !registryKind.isBlank();
        }
    }
}
