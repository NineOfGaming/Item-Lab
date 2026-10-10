package dev.nineofgaming.itemlab.editor.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * A panel that can preview and restore its pending changes without committing them.
 */
public interface PendingStackEditor {
    boolean isDirty();

    ItemStack pendingStack(Minecraft client);

    void restorePending(Minecraft client, ItemStack state);

    void requestPendingFocus();
}
