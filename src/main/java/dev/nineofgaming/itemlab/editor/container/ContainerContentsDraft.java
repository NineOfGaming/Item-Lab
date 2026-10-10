package dev.nineofgaming.itemlab.editor.container;

import com.mojang.serialization.DynamicOps;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Lossless editable state for the sparse, indexed minecraft:container component.
 */
public final class ContainerContentsDraft {
    public static final int MAX_SLOTS = 256;

    private boolean present;
    private final List<ItemStack> slots = new ArrayList<>();

    /**
     * Reads templates through NBT so overstacked entries and typed custom data remain lossless.
     */
    public static ContainerContentsDraft fromStack(ItemStack owner, DynamicOps<?> ops) {
        ContainerContentsDraft draft = new ContainerContentsDraft();
        ItemContainerContents contents = owner.get(DataComponents.CONTAINER);
        draft.present = contents != null;
        if (contents == null) return draft;

        DynamicOps<Tag> nbtOps = ops instanceof RegistryOps<?> registries ? registries.withParent(NbtOps.INSTANCE) : NbtOps.INSTANCE;
        Tag encoded = ItemContainerContents.CODEC.encodeStart(nbtOps, contents).getOrThrow();
        if (!(encoded instanceof ListTag slots)) {
            throw new IllegalStateException("Minecraft encoded container contents as a non-array value");
        }
        slots.forEach(slotValue -> {
            if (!(slotValue instanceof CompoundTag slot)) {
                throw new IllegalStateException("Minecraft encoded a container slot as a non-object value");
            }
            int index = slot.getInt("slot").orElseThrow();
            ItemStackTemplate template = ItemStackTemplate.CODEC.parse(
                nbtOps,
                slot.get("item")
            ).getOrThrow();
            draft.ensureSize(index + 1);
            draft.slots.set(index, ItemStackCodecs.stackFromTemplate(template));
        });
        draft.trimTrailingEmptySlots();
        return draft;
    }

    public boolean present() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public int highestOccupiedSlot() {
        for (int index = slots.size() - 1; index >= 0; index--) {
            if (!slots.get(index).isEmpty()) return index;
        }
        return -1;
    }

    public ItemStack item(int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        return slots.get(index).copy();
    }

    public void setItem(int index, ItemStack stack) {
        checkIndex(index);
        ensureSize(index + 1);
        slots.set(index, stack == null ? ItemStack.EMPTY : stack.copy());
        trimTrailingEmptySlots();
    }

    public void swap(int first, int second) {
        checkIndex(first);
        checkIndex(second);
        ensureSize(Math.max(first, second) + 1);
        ItemStack value = slots.get(first);
        slots.set(first, slots.get(second));
        slots.set(second, value);
        trimTrailingEmptySlots();
    }

    public int firstEmptySlot(int limit) {
        int checkedLimit = Math.clamp(limit, 0, MAX_SLOTS);
        for (int index = 0; index < checkedLimit; index++) {
            if (index >= slots.size() || slots.get(index).isEmpty()) return index;
        }
        return checkedLimit < MAX_SLOTS ? checkedLimit : -1;
    }

    public void applyTo(ItemStack owner) {
        if (!present) {
            owner.remove(DataComponents.CONTAINER);
            return;
        }
        List<ItemStack> copies = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) copies.add(stack.copy());
        owner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(copies));
    }

    public boolean matchesStack(ItemStack owner) {
        ItemContainerContents current = owner.get(DataComponents.CONTAINER);
        if (!present) return current == null;
        if (current == null) return false;
        List<ItemStack> copies = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) copies.add(stack.copy());
        return ItemContainerContents.fromItems(copies).equals(current);
    }

    private void ensureSize(int size) {
        if (size > MAX_SLOTS) throw new IllegalArgumentException("Container slot index must be below 256");
        while (slots.size() < size) slots.add(ItemStack.EMPTY);
    }

    private void trimTrailingEmptySlots() {
        while (!slots.isEmpty() && slots.getLast().isEmpty()) slots.removeLast();
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= MAX_SLOTS) {
            throw new IllegalArgumentException("Container slot index must be between 0 and 255");
        }
    }
}
