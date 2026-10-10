package dev.nineofgaming.itemlab.editor.ui.preview;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * A native item stack centered on the cursor while an ImGui drag is active.
 */
final class DraggedItemPreview {
    private static final int ITEM_SIZE = 16;

    private ItemStack stack = ItemStack.EMPTY;
    private int mouseX;
    private int mouseY;
    private boolean requestedThisFrame;

    void beginImGuiFrame() {
        requestedThisFrame = false;
    }

    void draw(Minecraft client, ItemStack draggedStack) {
        if (draggedStack == null || draggedStack.isEmpty()) return;
        stack = draggedStack.copy();
        mouseX = NativePreviewGeometry.resolveScreenX(ImGui.getMousePosX(), client);
        mouseY = NativePreviewGeometry.resolveScreenY(ImGui.getMousePosY(), client);
        requestedThisFrame = true;
    }

    void endImGuiFrame() {
        if (!requestedThisFrame) stack = ItemStack.EMPTY;
    }

    void clear() {
        stack = ItemStack.EMPTY;
        requestedThisFrame = false;
    }

    boolean extract(GuiGraphicsExtractor graphics) {
        if (!requestedThisFrame || stack.isEmpty()) return false;
        int left = mouseX - ITEM_SIZE / 2;
        int top = mouseY - ITEM_SIZE / 2;
        graphics.item(stack, left, top);
        graphics.itemDecorations(Minecraft.getInstance().font, stack, left, top);
        return true;
    }
}
