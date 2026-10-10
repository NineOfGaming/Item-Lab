package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;
//? if >=26.2 {
import net.minecraft.world.item.component.SulfurCubeContent;
//?}

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Minecraft codec boundary for stack and data-component serialization.
 */
public final class ItemStackCodecs {
    public static final int MAX_SERIALIZED_COUNT = 99;

    private ItemStackCodecs() {
    }

    public static String encodeStackSnbt(Minecraft client, ItemStack stack) {
        return encodeStackSnbt(client, stack, false);
    }

    /**
     * Encodes a stack using Minecraft's persistent item codec. When requested, the
     * component patch is expanded with every effective persistent component inherited
     * from the item's prototype. Existing patch entries are retained so explicit
     * component removals remain visible and round-trip correctly.
     */
    public static String encodeStackSnbt(
        Minecraft client,
        ItemStack stack,
        boolean includeDefaultComponents
    ) {
        CompoundTag encoded = encodeStackTag(client, stack);
        if (includeDefaultComponents) {
            expandEffectiveComponentsSnbt(client, stack, encoded);
        }
        return prettySnbt(encoded);
    }

    private static CompoundTag encodeStackTag(Minecraft client, ItemStack stack) {
        Tag encoded = ItemStack.CODEC.encodeStart(nbtOps(client), stack).getOrThrow();
        if (!(encoded instanceof CompoundTag compound)) {
            throw new IllegalStateException("Minecraft encoded the item stack as a non-compound NBT value");
        }
        return compound;
    }

    private static void expandEffectiveComponentsSnbt(
        Minecraft client,
        ItemStack stack,
        CompoundTag root
    ) {
        CompoundTag components = root.getCompound("components")
            .map(CompoundTag::copy)
            .orElseGet(CompoundTag::new);
        for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            if (type.codec() == null) continue;
            Object value = stack.get(type);
            if (value == null) continue;
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id != null) {
                components.put(id.toString(), encodeComponentNbt(client, type, value));
            }
        }
        if (!components.isEmpty()) root.put("components", components);
    }

    /**
     * Components inherited from the item prototype and injected only by the expanded raw view.
     */
    public static Set<DataComponentType<?>> inheritedPersistentComponents(ItemStack stack) {
        Set<DataComponentType<?>> inherited = new LinkedHashSet<>();
        for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            if (type.codec() != null && stack.get(type) != null && !stack.hasNonDefault(type)) {
                inherited.add(type);
            }
        }
        return Set.copyOf(inherited);
    }

    /**
     * Turns omitted entries that were injected by the expanded raw view into explicit
     * component-patch removals. Existing or edited overrides remain untouched.
     */
    public static void applyExpandedDefaultDeletions(
        ItemStack stack,
        Set<DataComponentType<?>> expandedDefaults,
        Set<String> encodedComponentKeys
    ) {
        for (DataComponentType<?> type : expandedDefaults) {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id == null) continue;
            String key = id.toString();
            if (!encodedComponentKeys.contains(key) && !encodedComponentKeys.contains("!" + key)) {
                stack.remove(type);
            }
        }
    }

    public static ItemStack decodeStackNbt(Minecraft client, Tag encoded) {
        // 26.1's count decoding replaces malformed/out-of-range
        // input with 1. Reject it before decoding instead of silently saving a
        // different count. Omitted counts retain Minecraft's native default.
        if (encoded instanceof CompoundTag compound && compound.contains("count")) {
            Tag countTag = compound.get("count");
            int count = Codec.intRange(1, MAX_SERIALIZED_COUNT).parse(NbtOps.INSTANCE, countTag).getOrThrow();
            double requested = NbtOps.INSTANCE.getNumberValue(Objects.requireNonNull(countTag)).getOrThrow().doubleValue();
            if (requested != count) {
                throw new IllegalArgumentException("Item stack count must be a whole number between 1 and " + MAX_SERIALIZED_COUNT);
            }
        }
        return ItemStack.CODEC.parse(nbtOps(client), encoded).getOrThrow();
    }

    public static ItemStack decodeStackSnbt(Minecraft client, String source) {
        return decodeStackSnbtDocument(client, source).stack();
    }

    public static DecodedStackSnbt decodeStackSnbtDocument(Minecraft client, String source) {
        CompoundTag encoded = parseCompoundSnbt(source);
        ItemStack stack = decodeStackNbt(client, encoded);
        Set<String> componentKeys = encoded.getCompound("components")
            .map(components -> Set.copyOf(components.keySet()))
            .orElseGet(Set::of);
        return new DecodedStackSnbt(stack, componentKeys);
    }

    public record DecodedStackSnbt(ItemStack stack, Set<String> componentKeys) {
        public DecodedStackSnbt {
            componentKeys = Set.copyOf(componentKeys);
        }
    }

    public static String formatSnbt(String source) {
        return prettySnbt(parseCompoundSnbt(source));
    }

    public static CompoundTag parseCompoundSnbt(String source) {
        try {
            return TagParser.parseCompoundFully(source);
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    public static String prettySnbt(Tag tag) {
        return new SnbtPrinterTagVisitor("  ", 0, new ArrayList<>()).visit(tag);
    }

    /**
     * Converts a schema-defined JSON envelope to NBT before opaque SNBT fields are spliced into it.
     */
    public static CompoundTag jsonObjectToNbt(JsonObject value) {
        Tag converted = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, value);
        if (!(converted instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Expected a JSON object to convert to an NBT compound");
        }
        return compound;
    }

    /**
     * Validates editor output without applying vanilla's inventory-capacity rule.
     * Minecraft's persistent codec permits counts through 99 even when an item's
     * max_stack_size is lower; strict validation rejects those otherwise valid
     * overstacked values. Component invariants are checked on count-normalized
     * probes, recursively for container, bundle, charged-projectile, and sulfur cube contents.
     */
    public static void validateStack(ItemStack stack) {
        if (stack == null) throw new IllegalArgumentException("Item stack is missing");
        if (stack.isEmpty()) {
            ItemStack.validateStrict(stack).getOrThrow();
            return;
        }

        int count = stack.getCount();
        if (count < 1 || count > MAX_SERIALIZED_COUNT) {
            throw new IllegalArgumentException(
                "Item stack count must be between 1 and " + MAX_SERIALIZED_COUNT
            );
        }

        ItemStack componentProbe = stack.copyWithCount(
            Math.clamp(stack.getMaxStackSize(), 1, count)
        );

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            componentProbe.remove(DataComponents.CONTAINER);
            container.nonEmptyItems().forEach(template -> validateStack(stackFromTemplate(template)));
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            componentProbe.remove(DataComponents.BUNDLE_CONTENTS);
            bundle.items().forEach(template -> validateStack(stackFromTemplate(template)));
            bundle.weight().getOrThrow();
        }

        ChargedProjectiles chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (chargedProjectiles != null) {
            componentProbe.remove(DataComponents.CHARGED_PROJECTILES);
            chargedProjectiles.items().forEach(template -> validateStack(stackFromTemplate(template)));
        }
        //? if >=26.2 {
        SulfurCubeContent sulfurCubeContent = stack.get(DataComponents.SULFUR_CUBE_CONTENT);
        if (sulfurCubeContent != null) {
            componentProbe.remove(DataComponents.SULFUR_CUBE_CONTENT);
            validateStack(stackFromTemplate(sulfurCubeContent.absorbedBlockItemStack()));
        }
        //?}
        ItemStack.validateStrict(componentProbe).getOrThrow();
    }

    /**
     * Materializes stored data without ItemStackTemplate#create() applying strict capacity validation.
     */
    public static ItemStack stackFromTemplate(ItemStackTemplate template) {
        return new ItemStack(template.item(), template.count(), template.components());
    }

    public static JsonElement encodeComponent(Minecraft client, DataComponentType<?> type, Object value) {
        return encodeComponentUnchecked(client, type, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonElement encodeComponentUnchecked(
        Minecraft client,
        DataComponentType<?> rawType,
        Object rawValue
    ) {
        DataComponentType<T> type = (DataComponentType<T>) rawType;
        Codec<T> codec = type.codec();
        if (codec == null) {
            throw new IllegalStateException("This component has no persistent codec");
        }
        return codec.encodeStart(jsonOps(client), (T) rawValue).getOrThrow();
    }

    public static Tag encodeComponentNbt(Minecraft client, DataComponentType<?> type, Object value) {
        return encodeComponentNbtUnchecked(client, type, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> Tag encodeComponentNbtUnchecked(
        Minecraft client,
        DataComponentType<?> rawType,
        Object rawValue
    ) {
        DataComponentType<T> type = (DataComponentType<T>) rawType;
        Codec<T> codec = type.codec();
        if (codec == null) {
            throw new IllegalStateException("This component has no persistent codec");
        }
        return codec.encodeStart(nbtOps(client), (T) rawValue).getOrThrow();
    }

    public static void decodeAndSetComponentNbt(
        Minecraft client,
        ItemStack stack,
        DataComponentType<?> type,
        Tag value
    ) {
        decodeAndSetComponentNbtUnchecked(client, stack, type, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> void decodeAndSetComponentNbtUnchecked(
        Minecraft client,
        ItemStack stack,
        DataComponentType<?> rawType,
        Tag encodedValue
    ) {
        DataComponentType<T> type = (DataComponentType<T>) rawType;
        Codec<T> codec = type.codec();
        if (codec == null) {
            throw new IllegalStateException("This component has no persistent codec");
        }
        T value = codec.parse(nbtOps(client), encodedValue).getOrThrow();
        stack.set(type, value);
    }

    public static Tag canonicalizeComponentNbt(
        Minecraft client,
        DataComponentType<?> type,
        Tag value
    ) {
        return canonicalizeComponentNbtUnchecked(client, type, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> Tag canonicalizeComponentNbtUnchecked(
        Minecraft client,
        DataComponentType<?> rawType,
        Tag encodedValue
    ) {
        DataComponentType<T> type = (DataComponentType<T>) rawType;
        Codec<T> codec = type.codec();
        if (codec == null) {
            throw new IllegalStateException("This component has no persistent codec");
        }
        T value = codec.parse(nbtOps(client), encodedValue).getOrThrow();
        return codec.encodeStart(nbtOps(client), value).getOrThrow();
    }

    public static void decodeAndSetComponent(
        Minecraft client,
        ItemStack stack,
        DataComponentType<?> type,
        JsonElement value
    ) {
        decodeAndSetComponentUnchecked(client, stack, type, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> void decodeAndSetComponentUnchecked(
        Minecraft client,
        ItemStack stack,
        DataComponentType<?> rawType,
        JsonElement encodedValue
    ) {
        DataComponentType<T> type = (DataComponentType<T>) rawType;
        Codec<T> codec = type.codec();
        if (codec == null) {
            throw new IllegalStateException("This component has no persistent codec");
        }
        T value = codec.parse(jsonOps(client), encodedValue).getOrThrow();
        stack.set(type, value);
    }

    public static void setComponentValue(ItemStack stack, DataComponentType<?> type, Object value) {
        setComponentValueUnchecked(stack, type, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setComponentValueUnchecked(
        ItemStack stack,
        DataComponentType<?> rawType,
        Object rawValue
    ) {
        stack.set((DataComponentType<T>) rawType, (T) rawValue);
    }

    public static <T> void resetComponentToPrototype(ItemStack stack, DataComponentType<T> type) {
        DataComponentPatch restoredPatch = stack.getComponentsPatch().forget(type::equals);
        if (stack.getComponents() instanceof PatchedDataComponentMap components) {
            components.restorePatch(restoredPatch);
        } else {
            // ItemStack currently always owns a PatchedDataComponentMap, but preserve a
            // behaviorally-correct fallback for another implementation supplied by a mod.
            T prototypeValue = stack.getPrototype().get(type);
            if (prototypeValue == null) stack.remove(type);
            else stack.set(type, prototypeValue);
        }

        if (stack.hasNonDefault(type)) {
            throw new IllegalStateException("Minecraft kept the component override after it was reset");
        }
    }

    public static RegistryOps<JsonElement> jsonOps(Minecraft client) {
        return RegistryOps.create(JsonOps.INSTANCE, Objects.requireNonNull(client.level).registryAccess());
    }

    public static RegistryOps<Tag> nbtOps(Minecraft client) {
        return RegistryOps.create(NbtOps.INSTANCE, Objects.requireNonNull(client.level).registryAccess());
    }
}
