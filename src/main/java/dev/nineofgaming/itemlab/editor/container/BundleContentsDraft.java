package dev.nineofgaming.itemlab.editor.container;

import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered, lossless editable state for the minecraft:bundle_contents component.
 */
public final class BundleContentsDraft {
    private boolean present;
    private final List<ItemStack> items = new ArrayList<>();

    public static BundleContentsDraft fromStack(ItemStack owner) {
        BundleContentsDraft draft = new BundleContentsDraft();
        BundleContents contents = owner.get(DataComponents.BUNDLE_CONTENTS);
        draft.present = contents != null;
        if (contents != null) {
            contents.items().forEach(template -> draft.items.add(ItemStackCodecs.stackFromTemplate(template)));
        }
        return draft;
    }

    public boolean present() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public ItemStack item(int index) {
        checkIndex(index);
        return items.get(index).copy();
    }

    public void addItem(ItemStack stack) {
        insertItem(items.size(), stack);
    }

    public void insertItem(int index, ItemStack stack) {
        if (index < 0 || index > items.size()) {
            throw new IllegalArgumentException("Bundle entry index is outside the ordered contents");
        }
        requireNonEmpty(stack);
        items.add(index, stack.copy());
    }

    public void setItem(int index, ItemStack stack) {
        checkIndex(index);
        requireNonEmpty(stack);
        items.set(index, stack.copy());
    }

    public void removeItem(int index) {
        checkIndex(index);
        items.remove(index);
    }

    public void moveItem(int from, int to) {
        checkIndex(from);
        checkIndex(to);
        if (from == to) return;
        ItemStack moved = items.remove(from);
        items.add(to, moved);
    }

    public int firstDisallowedItem() {
        for (int index = 0; index < items.size(); index++) {
            if (!BundleContents.canItemBeInBundle(items.get(index))) return index;
        }
        return -1;
    }

    public Fraction totalWeight() {
        return immutableContents().weight().getOrThrow();
    }

    public void applyTo(ItemStack owner) {
        if (!present) {
            owner.remove(DataComponents.BUNDLE_CONTENTS);
            return;
        }
        int disallowed = firstDisallowedItem();
        if (disallowed >= 0) {
            throw new IllegalArgumentException("Bundle entry " + (disallowed + 1) + " cannot be stored in a bundle");
        }
        owner.set(DataComponents.BUNDLE_CONTENTS, immutableContents());
    }

    public boolean matchesStack(ItemStack owner) {
        BundleContents current = owner.get(DataComponents.BUNDLE_CONTENTS);
        if (!present) return current == null;
        return current != null && immutableContents().equals(current);
    }

    private BundleContents immutableContents() {
        List<ItemStackTemplate> templates = items.stream()
            .map(ItemStackTemplate::fromNonEmptyStack)
            .toList();
        return templates.isEmpty() ? BundleContents.EMPTY : new BundleContents(templates);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IllegalArgumentException("Bundle entry index is outside the ordered contents");
        }
    }

    private static void requireNonEmpty(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Bundle contents cannot contain empty entries");
        }
    }
}
