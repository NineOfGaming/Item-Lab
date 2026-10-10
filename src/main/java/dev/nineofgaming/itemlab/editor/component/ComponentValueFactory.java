package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates neutral, codec-valid starting values for components that are not on the draft yet.
 */
public final class ComponentValueFactory {
    private final Map<String, JsonElement> templates = new HashMap<>();
    private EditorCatalog catalog;

    public void setCatalog(EditorCatalog catalog) {
        this.catalog = catalog;
        clear();
    }

    void clear() {
        templates.clear();
    }

    public JsonElement create(Minecraft client, ItemStack draft, ComponentOption option) {
        JsonElement cached = templates.get(option.id());
        if (cached != null) {
            return cached.deepCopy();
        }

        DataComponentType<?> type = option.type();
        Object prototypeValue = draft.getPrototype().get(type);
        if (prototypeValue != null) {
            return remember(client, option.id(), type, prototypeValue);
        }

        Object commonValue = commonStarter(type);
        if (commonValue != null) {
            return remember(client, option.id(), type, commonValue);
        }

        JsonElement schemaStarter = schemaStarter(type, option.id());
        if (schemaStarter != null && accepts(client, draft, type, schemaStarter)) {
            return remember(option.id(), schemaStarter);
        }
        JsonObject requiredStarter = ComponentSchemas.requiredStarter(option.id(), type);
        if (requiredStarter != null && accepts(client, draft, type, requiredStarter)) {
            return remember(option.id(), requiredStarter);
        }

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            Object example = item.getDefaultInstance().get(type);
            if (example == null) {
                continue;
            }
            try {
                return remember(client, option.id(), type, example);
            } catch (RuntimeException ignored) {
                // A modded default may rely on registry data that is not available in this context.
            }
        }

        for (JsonElement candidate : fallbackCandidates(option.id())) {
            if (accepts(client, draft, type, candidate)) {
                return remember(option.id(), candidate);
            }
        }
        throw new IllegalArgumentException("No codec-valid starter is known for " + option.id());
    }

    static Object commonStarter(DataComponentType<?> type) {
        if (type == DataComponents.CUSTOM_NAME) {
            return Component.literal("");
        }
        if (type == DataComponents.LORE) {
            return new ItemLore(List.of());
        }
        if (type == DataComponents.UNBREAKABLE
            || type == DataComponents.GLIDER
            || type == DataComponents.CREATIVE_SLOT_LOCK
            || type == DataComponents.INTANGIBLE_PROJECTILE) {
            return Unit.INSTANCE;
        }
        if (type == DataComponents.ENCHANTMENT_GLINT_OVERRIDE) {
            return true;
        }
        if (type == DataComponents.MAX_STACK_SIZE) {
            return 64;
        }
        if (type == DataComponents.MAX_DAMAGE) {
            return 100;
        }
        if (type == DataComponents.DAMAGE) {
            return 0;
        }
        if (type == DataComponents.RARITY) {
            return Rarity.COMMON;
        }
        if (type == DataComponents.ENCHANTMENTS || type == DataComponents.STORED_ENCHANTMENTS) {
            return ItemEnchantments.EMPTY;
        }
        if (type == DataComponents.ATTRIBUTE_MODIFIERS) {
            return ItemAttributeModifiers.EMPTY;
        }
        if (type == DataComponents.TOOLTIP_DISPLAY) {
            return TooltipDisplay.DEFAULT;
        }
        return null;
    }

    private JsonElement remember(Minecraft client, String id, DataComponentType<?> type, Object value) {
        return remember(id, ItemStackCodecs.encodeComponent(client, type, value));
    }

    private JsonElement remember(String id, JsonElement value) {
        templates.put(id, value.deepCopy());
        return value;
    }

    private JsonElement schemaStarter(DataComponentType<?> type, String componentId) {
        JsonElement variantStarter = VariantComponents.starter(componentId, catalog);
        if (variantStarter != null) {
            return variantStarter;
        }
        if (type == DataComponents.CONSUMABLE) {
            return new JsonObject();
        }
        if (type == DataComponents.EQUIPPABLE) {
            return json("{\"slot\":\"head\"}");
        }
        if (type == DataComponents.TRIM) {
            return json("{\"material\":\"" + firstId("trim_material", "minecraft:iron")
                + "\",\"pattern\":\"" + firstId("trim_pattern", "minecraft:sentry") + "\"}");
        }
        if (type == DataComponents.FOOD) {
            return json("{\"nutrition\":1,\"saturation\":0.1}");
        }
        if (type == DataComponents.USE_COOLDOWN) {
            return json("{\"seconds\":1.0}");
        }
        if (type == DataComponents.USE_REMAINDER) {
            return json("{\"id\":\"minecraft:glass_bottle\",\"count\":1}");
        }
        if (type == DataComponents.DEATH_PROTECTION) {
            return json("{\"death_effects\":[]}");
        }
        if (type == DataComponents.POTION_DURATION_SCALE) {
            return new JsonPrimitive(1.0);
        }
        if (type == DataComponents.SUSPICIOUS_STEW_EFFECTS) {
            return new JsonArray();
        }
        if (type == DataComponents.OMINOUS_BOTTLE_AMPLIFIER) {
            return new JsonPrimitive(0);
        }
        if (type == DataComponents.REPAIRABLE) {
            return json("{\"items\":\"minecraft:iron_ingot\"}");
        }
        if (type == DataComponents.DAMAGE_RESISTANT) {
            return json("{\"types\":\"minecraft:in_fire\"}");
        }
        if (type == DataComponents.WRITABLE_BOOK_CONTENT) {
            return json("{\"pages\":[]}");
        }
        if (type == DataComponents.FIREWORK_EXPLOSION) {
            return json("{\"shape\":\"small_ball\",\"colors\":[]}");
        }
        if (type == DataComponents.FIREWORKS) {
            return json("{\"flight_duration\":1,\"explosions\":[]}");
        }
        if (type == DataComponents.ENTITY_DATA) {
            return json("{\"id\":\"minecraft:pig\"}");
        }
        if (type == DataComponents.BLOCK_ENTITY_DATA) {
            return json("{\"id\":\"minecraft:chest\"}");
        }
        //? if >=26.2 {
        if (type == DataComponents.SULFUR_CUBE_CONTENT) {
            return json("{\"id\":\"minecraft:stone\",\"count\":1}");
        }
        //?}
        if (type == DataComponents.DAMAGE_TYPE) {
            return new JsonPrimitive(firstId("damage_type", "minecraft:player_attack"));
        }
        if (type == DataComponents.INSTRUMENT) {
            return new JsonPrimitive(firstId("instrument", "minecraft:ponder_goat_horn"));
        }
        if (type == DataComponents.PROVIDES_TRIM_MATERIAL) {
            return new JsonPrimitive(firstId("trim_material", "minecraft:iron"));
        }
        if (type == DataComponents.JUKEBOX_PLAYABLE) {
            return new JsonPrimitive(firstId("jukebox_song", "minecraft:13"));
        }
        if (type == DataComponents.BREAK_SOUND || type == DataComponents.NOTE_BLOCK_SOUND) {
            return new JsonPrimitive(firstId("sound_event", "minecraft:item.armor.equip_generic"));
        }
        if (type == DataComponents.PROVIDES_BANNER_PATTERNS) {
            return new JsonPrimitive(firstId("banner_pattern", "minecraft:base"));
        }
        if (type == DataComponents.MAP_POST_PROCESSING) {
            return new JsonPrimitive("lock");
        }
        if (type == DataComponents.DYE
            || type == DataComponents.BASE_COLOR
            || type == DataComponents.WOLF_COLLAR
            || type == DataComponents.TROPICAL_FISH_BASE_COLOR
            || type == DataComponents.TROPICAL_FISH_PATTERN_COLOR
            || type == DataComponents.CAT_COLLAR
            || type == DataComponents.SHEEP_COLOR
            || type == DataComponents.SHULKER_COLOR) {
            return new JsonPrimitive("white");
        }
        String componentKind = componentId.substring(componentId.indexOf(':') + 1);
        String registeredValue = firstId(componentKind, "");
        if (!registeredValue.isEmpty()) {
            return new JsonPrimitive(registeredValue);
        }
        return null;
    }

    private List<JsonElement> fallbackCandidates(String id) {
        List<JsonElement> candidates = new java.util.ArrayList<>();
        if (id.endsWith("_override")) {
            candidates.add(new JsonPrimitive(true));
        }
        if (id.endsWith("_size") || id.endsWith("_damage") || id.endsWith("_cost")) {
            candidates.add(new JsonPrimitive(0));
        }
        if (id.endsWith("_name") || id.endsWith("_model") || id.endsWith("_style")) {
            candidates.add(new JsonPrimitive(""));
        }
        if (id.endsWith("lore") || id.endsWith("enchantments") || id.endsWith("effects")) {
            candidates.add(new JsonArray());
        }
        candidates.add(new JsonObject());
        candidates.add(new JsonArray());
        candidates.add(new JsonPrimitive(0));
        candidates.add(new JsonPrimitive(1));
        candidates.add(new JsonPrimitive(false));
        candidates.add(new JsonPrimitive(""));
        return candidates;
    }

    static boolean accepts(Minecraft client, ItemStack draft, DataComponentType<?> type, JsonElement candidate) {
        try {
            ItemStack probe = draft.copy();
            ItemStackCodecs.decodeAndSetComponent(client, probe, type, candidate);
            return probe.has(type);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean preservesField(
        Minecraft client,
        ItemStack draft,
        DataComponentType<?> type,
        JsonElement candidate,
        String path,
        String fieldName
    ) {
        try {
            ItemStack probe = draft.copy();
            ItemStackCodecs.decodeAndSetComponent(client, probe, type, candidate);
            Object decoded = probe.get(type);
            if (decoded == null) return false;
            JsonElement encoded = ItemStackCodecs.encodeComponent(client, type, decoded);
            JsonElement owner = elementAt(encoded, path);
            return owner != null && owner.isJsonObject() && owner.getAsJsonObject().has(fieldName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static JsonElement installedFieldExample(Minecraft client, DataComponentType<?> type, String fieldName) {
        for (Item item : BuiltInRegistries.ITEM) {
            Object component = item.getDefaultInstance().get(type);
            if (component == null) {
                continue;
            }
            try {
                JsonElement encoded = ItemStackCodecs.encodeComponent(client, type, component);
                if (encoded.isJsonObject() && encoded.getAsJsonObject().has(fieldName)) {
                    return encoded.getAsJsonObject().get(fieldName).deepCopy();
                }
            } catch (RuntimeException ignored) {
                // A third-party item may expose a component which cannot be encoded here.
            }
        }
        return null;
    }

    static JsonElement installedArrayEntryExample(Minecraft client, DataComponentType<?> type, String path) {
        for (Item item : BuiltInRegistries.ITEM) {
            Object component = item.getDefaultInstance().get(type);
            if (component == null) {
                continue;
            }
            try {
                JsonElement encoded = ItemStackCodecs.encodeComponent(client, type, component);
                JsonElement atPath = elementAt(encoded, path);
                if (atPath != null && atPath.isJsonArray() && !atPath.getAsJsonArray().isEmpty()) {
                    return atPath.getAsJsonArray().get(0).deepCopy();
                }
            } catch (RuntimeException ignored) {
                // Keep looking for another installed example.
            }
        }
        return null;
    }

    private static JsonElement elementAt(JsonElement root, String path) {
        JsonElement current = root;
        if (path.equals("$")) {
            return current;
        }
        for (String segment : path.substring(2).split("/")) {
            if (current == null) {
                return null;
            }
            if (current.isJsonObject()) {
                current = current.getAsJsonObject().get(segment);
            } else if (current.isJsonArray()) {
                try {
                    int index = Integer.parseInt(segment);
                    current = index >= 0 && index < current.getAsJsonArray().size()
                        ? current.getAsJsonArray().get(index)
                        : null;
                } catch (NumberFormatException exception) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    private String firstId(String kind, String fallback) {
        if (catalog == null) {
            return fallback;
        }
        List<IdSuggestion> suggestions = catalog.registryIds(kind);
        if (!fallback.isEmpty()) {
            for (IdSuggestion suggestion : suggestions) {
                if (suggestion.id().equals(fallback)) {
                    return fallback;
                }
            }
        }
        return suggestions.isEmpty() ? fallback : suggestions.getFirst().id();
    }

    private static JsonElement json(String source) {
        return JsonParser.parseString(source);
    }
}
