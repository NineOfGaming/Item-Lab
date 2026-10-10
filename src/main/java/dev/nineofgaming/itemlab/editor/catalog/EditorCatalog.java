package dev.nineofgaming.itemlab.editor.catalog;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.*;

/**
 * Immutable registry-backed choices used by the editor's pickers and autocomplete fields.
 */
public record EditorCatalog(
    List<ItemOption> items,
    List<IdSuggestion> itemIds,
    List<IdSuggestion> enchantmentIds,
    List<IdSuggestion> attributeIds,
    List<IdSuggestion> soundIds,
    List<IdSuggestion> entityTypeIds,
    List<IdSuggestion> mobEffectIds,
    List<IdSuggestion> allRegistryIds,
    Map<String, List<IdSuggestion>> registryIdsByKind,
    List<ComponentOption> components
) {
    public List<IdSuggestion> registryIds(String kind) {
        return registryIdsByKind.getOrDefault(kind, List.of());
    }

    /**
     * Returns registered values and tags for a registry as one stable, de-duplicated list.
     */
    public List<IdSuggestion> registryIdsAndTags(String kind) {
        return mergeSuggestions(registryIds(kind), registryIds(kind + "#tags"));
    }

    public static EditorCatalog load(Minecraft client) {
        List<ItemOption> items = new ArrayList<>();
        List<IdSuggestion> itemIds = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == Items.AIR) {
                continue;
            }
            String displayName = item.getName(item.getDefaultInstance()).getString();
            String searchable = (displayName + " " + id).toLowerCase(Locale.ROOT);
            items.add(new ItemOption(id.toString(), displayName, searchable));
            itemIds.add(new IdSuggestion(id.toString(), displayName, searchable));
        }
        items.sort(Comparator.comparing(ItemOption::id));
        itemIds.sort(Comparator.comparing(IdSuggestion::id));

        List<IdSuggestion> attributes = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ATTRIBUTE.keySet()) {
            Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(id);
            String displayName = attribute == null
                ? id.toString()
                : Component.translatable(attribute.getDescriptionId()).getString();
            attributes.add(new IdSuggestion(
                id.toString(),
                displayName,
                (displayName + " " + id).toLowerCase(Locale.ROOT)
            ));
        }
        attributes.sort(Comparator.comparing(IdSuggestion::id));

        List<IdSuggestion> enchantments = new ArrayList<>();
        Objects.requireNonNull(client.level).registryAccess().lookup(Registries.ENCHANTMENT).ifPresent(enchantmentRegistry -> {
            for (Identifier id : enchantmentRegistry.keySet()) {
                Enchantment enchantment = enchantmentRegistry.getValue(id);
                String displayName = enchantment == null ? id.toString() : enchantment.description().getString();
                enchantments.add(new IdSuggestion(
                    id.toString(),
                    displayName,
                    (displayName + " " + id).toLowerCase(Locale.ROOT)
                ));
            }
        });
        enchantments.sort(Comparator.comparing(IdSuggestion::id));

        RegistryAccess registryAccess = client.level.registryAccess();
        Map<String, List<IdSuggestion>> registryIdsByKind = new HashMap<>();

        // Populate every registry exposed by the active connection first. This includes
        // datapack registries and registries supplied by other mods, not only vanilla's
        // compile-time Registries constants.
        registryAccess.registries().forEach(entry -> putRegistry(
            registryIdsByKind,
            entry.key().identifier(),
            entry.value()
        ));
        BuiltInRegistries.REGISTRY.entrySet().forEach(entry -> putRegistry(
            registryIdsByKind,
            entry.getKey().identifier(),
            entry.getValue()
        ));

        registryIdsByKind.put("item", List.copyOf(itemIds));
        registryIdsByKind.put("item_model", resourceSuggestions(client, "items", ".json", false));
        registryIdsByKind.put("tooltip_style", tooltipStyleSuggestions(client));
        registryIdsByKind.put("texture", resourceSuggestions(client, "textures", ".png", true));
        registryIdsByKind.put("block", registrySuggestions(BuiltInRegistries.BLOCK));
        registryIdsByKind.put("block_entity_type", registrySuggestions(BuiltInRegistries.BLOCK_ENTITY_TYPE));
        registryIdsByKind.put("sound_event", registrySuggestions(BuiltInRegistries.SOUND_EVENT));
        registryIdsByKind.put("entity_type", registrySuggestions(BuiltInRegistries.ENTITY_TYPE));
        registryIdsByKind.put("mob_effect", registrySuggestions(BuiltInRegistries.MOB_EFFECT));
        registryIdsByKind.put("potion", registrySuggestions(BuiltInRegistries.POTION));
        registryIdsByKind.put("attribute", List.copyOf(attributes));
        registryIdsByKind.put("enchantment", List.copyOf(enchantments));
        registryIdsByKind.put("trim_material", registrySuggestions(registryAccess, Registries.TRIM_MATERIAL));
        registryIdsByKind.put("trim_pattern", registrySuggestions(registryAccess, Registries.TRIM_PATTERN));
        registryIdsByKind.put("equipment_asset", mergeSuggestions(
            equipmentAssetSuggestions(),
            resourceSuggestions(client, "equipment", ".json", false)
        ));
        registryIdsByKind.put("damage_type", registrySuggestions(registryAccess, Registries.DAMAGE_TYPE));
        registryIdsByKind.put("instrument", registrySuggestions(registryAccess, Registries.INSTRUMENT));
        registryIdsByKind.put("jukebox_song", registrySuggestions(registryAccess, Registries.JUKEBOX_SONG));
        registryIdsByKind.put("banner_pattern", registrySuggestions(registryAccess, Registries.BANNER_PATTERN));
        registryIdsByKind.put("recipe", mergeSuggestions(
            registrySuggestions(registryAccess, Registries.RECIPE),
            RecipeResourceCatalog.load()
        ));
        registryIdsByKind.put("villager_variant", registrySuggestions(registryAccess, Registries.VILLAGER_TYPE));
        registryIdsByKind.put("wolf_variant", registrySuggestions(registryAccess, Registries.WOLF_VARIANT));
        registryIdsByKind.put("wolf_sound_variant", registrySuggestions(registryAccess, Registries.WOLF_SOUND_VARIANT));
        registryIdsByKind.put("painting_variant", registrySuggestions(registryAccess, Registries.PAINTING_VARIANT));
        registryIdsByKind.put("cat_variant", registrySuggestions(registryAccess, Registries.CAT_VARIANT));
        registryIdsByKind.put("cat_sound_variant", registrySuggestions(registryAccess, Registries.CAT_SOUND_VARIANT));
        registryIdsByKind.put("pig_variant", registrySuggestions(registryAccess, Registries.PIG_VARIANT));
        registryIdsByKind.put("pig_sound_variant", registrySuggestions(registryAccess, Registries.PIG_SOUND_VARIANT));
        registryIdsByKind.put("cow_variant", registrySuggestions(registryAccess, Registries.COW_VARIANT));
        registryIdsByKind.put("cow_sound_variant", registrySuggestions(registryAccess, Registries.COW_SOUND_VARIANT));
        registryIdsByKind.put("chicken_variant", registrySuggestions(registryAccess, Registries.CHICKEN_VARIANT));
        registryIdsByKind.put("chicken_sound_variant", registrySuggestions(registryAccess, Registries.CHICKEN_SOUND_VARIANT));
        registryIdsByKind.put("zombie_nautilus_variant", registrySuggestions(registryAccess, Registries.ZOMBIE_NAUTILUS_VARIANT));
        registryIdsByKind.put("frog_variant", registrySuggestions(registryAccess, Registries.FROG_VARIANT));

        Map<String, IdSuggestion> registeredIds = new HashMap<>();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            for (Identifier id : registry.keySet()) {
                registeredIds.putIfAbsent(id.toString(), IdSuggestion.of(id.toString()));
            }
        }
        for (IdSuggestion suggestion : enchantments) {
            registeredIds.putIfAbsent(suggestion.id(), suggestion);
        }
        for (List<IdSuggestion> registrySuggestions : registryIdsByKind.values()) {
            for (IdSuggestion suggestion : registrySuggestions) {
                registeredIds.putIfAbsent(suggestion.id(), suggestion);
            }
        }

        List<ComponentOption> components = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            if (type != null) {
                components.add(new ComponentOption(id.toString(), id.toString().toLowerCase(Locale.ROOT), type));
            }
        }
        components.sort(Comparator.comparing(ComponentOption::id));

        return new EditorCatalog(
            List.copyOf(items),
            List.copyOf(itemIds),
            List.copyOf(enchantments),
            List.copyOf(attributes),
            registrySuggestions(BuiltInRegistries.SOUND_EVENT),
            registrySuggestions(BuiltInRegistries.ENTITY_TYPE),
            registrySuggestions(BuiltInRegistries.MOB_EFFECT),
            registeredIds.values().stream().sorted(Comparator.comparing(IdSuggestion::id)).toList(),
            Map.copyOf(registryIdsByKind),
            List.copyOf(components)
        );
    }

    private static List<IdSuggestion> registrySuggestions(Registry<?> registry) {
        return registry.keySet().stream()
            .map(id -> IdSuggestion.of(id.toString()))
            .sorted(Comparator.comparing(IdSuggestion::id))
            .toList();
    }

    private static void putRegistry(
        Map<String, List<IdSuggestion>> registries,
        Identifier registryId,
        Registry<?> registry
    ) {
        List<IdSuggestion> values = registrySuggestions(registry);
        registries.put(registryId.toString(), values);
        registries.putIfAbsent(registryId.getPath(), values);
        List<IdSuggestion> tags = registryTagSuggestions(registry);
        registries.put(registryId + "#tags", tags);
        registries.putIfAbsent(registryId.getPath() + "#tags", tags);
    }

    private static List<IdSuggestion> registryTagSuggestions(Registry<?> registry) {
        Map<String, IdSuggestion> tags = new LinkedHashMap<>();
        try {
            registry.getTags().forEach(named -> {
                String id = "#" + named.key().location();
                tags.putIfAbsent(id, IdSuggestion.of(id));
            });
        } catch (RuntimeException ignored) {
            // Some mod registries do not expose tags during client initialization.
        }
        return tags.values().stream()
            .sorted(Comparator.comparing(IdSuggestion::id))
            .toList();
    }

    private static List<IdSuggestion> resourceSuggestions(
        Minecraft client,
        String root,
        String extension,
        boolean overlayTexturesOnly
    ) {
        Map<String, IdSuggestion> suggestions = new HashMap<>();
        try {
            client.getResourceManager().listResources(root, id -> id.getPath().endsWith(extension))
                .keySet()
                .forEach(resourceId -> {
                    String path = resourceId.getPath();
                    String prefix = root + "/";
                    if (!path.startsWith(prefix)) {
                        return;
                    }
                    String localPath = path.substring(prefix.length(), path.length() - extension.length());
                    if (overlayTexturesOnly
                        && !localPath.contains("overlay")
                        && !localPath.startsWith("misc/")) {
                        return;
                    }
                    String id = resourceId.getNamespace() + ":" + localPath;
                    suggestions.putIfAbsent(id, IdSuggestion.of(id));
                });
        } catch (RuntimeException ignored) {
            // Resource packs may still be reloading while the editor is opened.
        }
        return suggestions.values().stream()
            .sorted(Comparator.comparing(IdSuggestion::id))
            .toList();
    }

    private static List<IdSuggestion> tooltipStyleSuggestions(Minecraft client) {
        Map<String, IdSuggestion> suggestions = new HashMap<>();
        String root = "textures/gui/sprites/tooltip";
        String suffix = "_background.png";
        try {
            client.getResourceManager().listResources(root, id -> id.getPath().endsWith(suffix))
                .keySet()
                .forEach(resourceId -> {
                    String path = resourceId.getPath();
                    String prefix = root + "/";
                    if (!path.startsWith(prefix)) return;
                    String localPath = path.substring(prefix.length(), path.length() - suffix.length());
                    String id = resourceId.getNamespace() + ":" + localPath;
                    suggestions.putIfAbsent(id, IdSuggestion.of(id));
                });
        } catch (RuntimeException ignored) {
            // Resource packs may still be reloading while the editor is opened.
        }
        return suggestions.values().stream()
            .sorted(Comparator.comparing(IdSuggestion::id))
            .toList();
    }

    @SafeVarargs
    private static List<IdSuggestion> mergeSuggestions(List<IdSuggestion>... groups) {
        Map<String, IdSuggestion> merged = new HashMap<>();
        for (List<IdSuggestion> group : groups) {
            for (IdSuggestion suggestion : group) {
                merged.putIfAbsent(suggestion.id(), suggestion);
            }
        }
        return merged.values().stream()
            .sorted(Comparator.comparing(IdSuggestion::id))
            .toList();
    }

    private static <T> List<IdSuggestion> registrySuggestions(
        RegistryAccess registryAccess,
        ResourceKey<? extends Registry<? extends T>> registryKey
    ) {
        return registryAccess.lookup(registryKey)
            .map(EditorCatalog::registrySuggestions)
            .orElseGet(List::of);
    }

    private static List<IdSuggestion> equipmentAssetSuggestions() {
        Map<String, IdSuggestion> assets = new HashMap<>();
        addEquipmentAsset(assets, EquipmentAssets.LEATHER);
        addEquipmentAsset(assets, EquipmentAssets.COPPER);
        addEquipmentAsset(assets, EquipmentAssets.CHAINMAIL);
        addEquipmentAsset(assets, EquipmentAssets.IRON);
        addEquipmentAsset(assets, EquipmentAssets.GOLD);
        addEquipmentAsset(assets, EquipmentAssets.DIAMOND);
        addEquipmentAsset(assets, EquipmentAssets.TURTLE_SCUTE);
        addEquipmentAsset(assets, EquipmentAssets.NETHERITE);
        addEquipmentAsset(assets, EquipmentAssets.ARMADILLO_SCUTE);
        addEquipmentAsset(assets, EquipmentAssets.ELYTRA);
        addEquipmentAsset(assets, EquipmentAssets.SADDLE);
        addEquipmentAsset(assets, EquipmentAssets.TRADER_LLAMA);
        addEquipmentAsset(assets, EquipmentAssets.TRADER_LLAMA_BABY);
        EquipmentAssets.CARPETS.values().forEach(key -> addEquipmentAsset(assets, key));
        EquipmentAssets.HARNESSES.values().forEach(key -> addEquipmentAsset(assets, key));
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            Equippable equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
            if (equippable != null) {
                equippable.assetId().ifPresent(key -> addEquipmentAsset(assets, key));
            }
        }
        return assets.values().stream().sorted(Comparator.comparing(IdSuggestion::id)).toList();
    }

    private static void addEquipmentAsset(Map<String, IdSuggestion> assets, ResourceKey<?> key) {
        String id = key.identifier().toString();
        assets.putIfAbsent(id, IdSuggestion.of(id));
    }
}
