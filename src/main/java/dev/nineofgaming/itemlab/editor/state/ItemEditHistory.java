package dev.nineofgaming.itemlab.editor.state;

import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Bounded, copy-safe undo history for accepted editor draft transactions.
 */
public final class ItemEditHistory {
    private static final int MAX_ENTRIES = 64;

    private final Deque<ItemStackSnapshot> undo = new ArrayDeque<>();
    private final Deque<ItemStackSnapshot> redo = new ArrayDeque<>();
    private ItemStackSnapshot transactionStart;
    private int transactionDepth;

    public void clear() {
        undo.clear();
        redo.clear();
        transactionStart = null;
        transactionDepth = 0;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    /**
     * Starts a transaction. Nested transactions collapse into the outermost one.
     */
    public void begin(Minecraft client, ItemStack current, List<ComponentOption> components) {
        if (transactionDepth++ == 0) {
            transactionStart = ItemStackSnapshot.capture(client, current, components);
        }
    }

    /**
     * Finishes a transaction and records its complete before-state once.
     */
    public void end(Minecraft client, ItemStack current) {
        if (transactionDepth <= 0) {
            throw new IllegalStateException("No item edit history transaction is active");
        }
        if (--transactionDepth != 0) return;

        if (!transactionStart.matches(client, current)) {
            pushUndo(transactionStart);
            redo.clear();
        }
        transactionStart = null;
    }

    /**
     * Records the state before a single accepted draft replacement.
     */
    public void record(
        Minecraft client,
        ItemStack current,
        ItemStack replacement,
        List<ComponentOption> components
    ) {
        if (ItemStack.matches(current, replacement)) return;
        if (transactionDepth > 0) return;

        pushUndo(ItemStackSnapshot.capture(client, current, components));
        redo.clear();
    }

    public ItemStack undo(Minecraft client, ItemStack current, List<ComponentOption> components) {
        if (undo.isEmpty()) return null;
        ItemStack restored = undo.peekLast().restore(client);
        push(redo, ItemStackSnapshot.capture(client, current, components));
        undo.removeLast();
        return restored;
    }

    public ItemStack redo(Minecraft client, ItemStack current, List<ComponentOption> components) {
        if (redo.isEmpty()) return null;
        ItemStack restored = redo.peekLast().restore(client);
        pushUndo(ItemStackSnapshot.capture(client, current, components));
        redo.removeLast();
        return restored;
    }

    private void pushUndo(ItemStackSnapshot snapshot) {
        push(undo, snapshot);
    }

    private static void push(Deque<ItemStackSnapshot> destination, ItemStackSnapshot snapshot) {
        destination.addLast(snapshot);
        while (destination.size() > MAX_ENTRIES) {
            destination.removeFirst();
        }
    }
}
