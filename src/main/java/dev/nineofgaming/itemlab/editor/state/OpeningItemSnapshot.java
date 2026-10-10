package dev.nineofgaming.itemlab.editor.state;

import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Deep codec snapshot captured once when the editor opens.
 */
public final class OpeningItemSnapshot {
    private ItemStackSnapshot snapshot;

    public boolean isAvailable() {
        return snapshot != null;
    }

    public void capture(Minecraft client, ItemStack stack, List<ComponentOption> components) {
        snapshot = ItemStackSnapshot.capture(client, stack, components);
    }

    public ItemStack restore(Minecraft client) {
        requireAvailable();
        return snapshot.restore(client);
    }

    public ItemStack restoreComponent(Minecraft client, ItemStack current, DataComponentType<?> type) {
        requireAvailable();
        return snapshot.restoreComponent(client, current, type);
    }

    public boolean componentMatches(Minecraft client, ItemStack currentStack, DataComponentType<?> type) {
        return snapshot != null && snapshot.componentMatches(client, currentStack, type);
    }

    private void requireAvailable() {
        if (snapshot == null) {
            throw new IllegalStateException("No opening item has been captured");
        }
    }
}
