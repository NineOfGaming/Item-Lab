package dev.nineofgaming.itemlab.editor.ui.preview;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * A live item icon drawn by Minecraft into a real inventory-slot sprite.
 */
public final class ItemIconPreview {
    static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final float SLOT_SCALE = 2.0f;
    static final int NATIVE_SLOT_SIZE = 18;

    private ItemStack stack = ItemStack.EMPTY;
    private NativePreviewGeometry.Capture geometry;
    private boolean requestedThisFrame;

    public void beginImGuiFrame() {
        requestedThisFrame = false;
    }

    public float imGuiSize(Minecraft client) {
        return NATIVE_SLOT_SIZE * SLOT_SCALE * NativePreviewGeometry.imGuiScale(client);
    }

    public void draw(Minecraft client, ItemStack previewStack) {
        float size = imGuiSize(client);
        ImGui.dummy(size, size);
        if (!ImGui.isItemVisible()) {
            return;
        }

        geometry = NativePreviewGeometry.captureCurrentItem(client);
        requestedThisFrame = true;
        setStack(previewStack);
    }

    public void setStack(ItemStack previewStack) {
        if (!requestedThisFrame) {
            return;
        }
        stack = previewStack == null ? ItemStack.EMPTY : previewStack.copy();
    }

    public void endImGuiFrame() {
        if (!requestedThisFrame) {
            stack = ItemStack.EMPTY;
        }
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        requestedThisFrame = false;
    }

    public boolean extract(GuiGraphicsExtractor graphics) {
        if (!requestedThisFrame) {
            return false;
        }

        NativePreviewGeometry.Bounds bounds = geometry.resolve(graphics);
        if (!bounds.valid()) {
            return false;
        }

        float scale = Math.min(
            bounds.width() / (float) NATIVE_SLOT_SIZE,
            bounds.height() / (float) NATIVE_SLOT_SIZE
        );
        graphics.enableScissor(bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.left(), bounds.top());
        graphics.pose().scale(scale, scale);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, 0, 0, NATIVE_SLOT_SIZE, NATIVE_SLOT_SIZE);
        if (!stack.isEmpty()) {
            graphics.item(stack, 1, 1);
            graphics.itemDecorations(Minecraft.getInstance().font, stack, 1, 1);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
        return true;
    }
}
