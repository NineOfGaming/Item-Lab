package dev.nineofgaming.itemlab.editor.state;

import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Codec-backed deep snapshot of an item stack and its persistent component values.
 */
public final class ItemStackSnapshot {
    private final Map<DataComponentType<?>, Tag> componentValues = new IdentityHashMap<>();
    private final Set<DataComponentType<?>> capturedComponents =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<DataComponentType<?>> fallbackComponents =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final ItemStack fallback;
    private final String encodedStack;

    private ItemStackSnapshot(Minecraft client, ItemStack stack, List<ComponentOption> components) {
        fallback = stack.copy();

        String encoded;
        try {
            encoded = ItemStackCodecs.encodeStackSnbt(client, stack);
        } catch (RuntimeException ignored) {
            encoded = "";
        }
        encodedStack = encoded;

        for (ComponentOption option : components) {
            DataComponentType<?> type = option.type();
            if (type.codec() == null) {
                fallbackComponents.add(type);
                continue;
            }
            try {
                Object value = stack.get(type);
                if (value != null) {
                    componentValues.put(type, ItemStackCodecs.encodeComponentNbt(client, type, value).copy());
                }
                capturedComponents.add(type);
            } catch (RuntimeException ignored) {
                // The fallback retains values supplied by codecs which cannot encode here.
                fallbackComponents.add(type);
            }
        }
    }

    public static ItemStackSnapshot capture(
        Minecraft client,
        ItemStack stack,
        List<ComponentOption> components
    ) {
        return new ItemStackSnapshot(client, stack, components);
    }

    public ItemStack restore(Minecraft client) {
        ItemStack restored = encodedStack.isBlank()
            ? fallback.copy()
            : ItemStackCodecs.decodeStackSnbt(client, encodedStack);
        for (DataComponentType<?> type : capturedComponents) {
            restoreCapturedComponent(client, restored, type);
        }
        for (DataComponentType<?> type : fallbackComponents) {
            copyComponent(fallback, restored, type);
        }
        return restored;
    }

    public ItemStack restoreComponent(Minecraft client, ItemStack current, DataComponentType<?> type) {
        ItemStack restored = current.copy();
        if (capturedComponents.contains(type)) {
            restoreCapturedComponent(client, restored, type);
        } else {
            copyComponent(fallback, restored, type);
        }
        return restored;
    }

    public boolean matches(Minecraft client, ItemStack stack) {
        try {
            return ItemStack.matches(restore(client), stack);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean componentMatches(Minecraft client, ItemStack currentStack, DataComponentType<?> type) {
        if (!capturedComponents.contains(type)) {
            return Objects.equals(currentStack.get(type), fallback.get(type));
        }

        Object current = currentStack.get(type);
        Tag captured = componentValues.get(type);
        if (current == null || captured == null) {
            return current == null && captured == null;
        }
        try {
            return Objects.equals(ItemStackCodecs.encodeComponentNbt(client, type, current), captured);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void restoreCapturedComponent(Minecraft client, ItemStack destination, DataComponentType<?> type) {
        Tag value = componentValues.get(type);
        if (value == null) {
            destination.remove(type);
        } else {
            ItemStackCodecs.decodeAndSetComponentNbt(client, destination, type, value.copy());
        }
    }

    private static <T> void copyComponent(
        ItemStack source,
        ItemStack destination,
        DataComponentType<T> type
    ) {
        T value = source.get(type);
        if (value == null) {
            destination.remove(type);
        } else {
            destination.set(type, value);
        }
    }
}
