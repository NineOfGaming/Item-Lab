package dev.nineofgaming.itemlab.editor.ui.preview;

import dev.nineofgaming.itemlab.mixin.client.BookViewScreenAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.item.ItemStack;

/**
 * Embeds Minecraft's own book-view screen inside an ImGui preview canvas.
 */
public final class BookContentPreview {
    private static final float CANVAS_HEIGHT = 300.0f;
    private static final int NATIVE_WIDTH = 192;
    private static final int NATIVE_HEIGHT = 194;

    private final NativePreviewCanvas canvas = new NativePreviewCanvas();
    private ItemStack stack = ItemStack.EMPTY;
    private int page;

    public void beginImGuiFrame() {
        canvas.beginFrame();
    }

    public void draw(ItemStack previewStack, int currentPage) {
        if (canvas.draw("book_content_preview", CANVAS_HEIGHT)) {
            stack = previewStack == null ? ItemStack.EMPTY : previewStack.copyWithCount(1);
            page = Math.max(0, currentPage);
        }
    }

    public void endImGuiFrame() {
        if (!canvas.requested()) stack = ItemStack.EMPTY;
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        canvas.beginFrame();
    }

    public boolean extract(GuiGraphicsExtractor graphics) {
        if (stack.isEmpty() || !canvas.requested()) return false;
        BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(stack);
        if (access == null) return false;

        NativePreviewCanvas.Bounds bounds = canvas.resolve(graphics);
        if (!bounds.valid()) return false;
        float scale = Math.min(
            bounds.width() / (float) NATIVE_WIDTH,
            bounds.height() / (float) NATIVE_HEIGHT
        );
        int renderedWidth = Math.round(NATIVE_WIDTH * scale);
        int renderedHeight = Math.round(NATIVE_HEIGHT * scale);
        int left = bounds.left() + (bounds.width() - renderedWidth) / 2;
        int top = bounds.top() + (bounds.height() - renderedHeight) / 2;

        BookViewScreen screen = new BookViewScreen(access);
        screen.width = NATIVE_WIDTH;
        screen.height = NATIVE_HEIGHT;
        ((BookViewScreenAccessor) screen).itemlab$setCurrentPage(
            Math.clamp(page, 0, access.getPageCount() - 1)
        );

        graphics.enableScissor(bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, top);
        graphics.pose().scale(scale, scale);
        screen.extractBackground(graphics, -10_000, -10_000, 0.0f);
        screen.extractRenderState(graphics, -10_000, -10_000, 0.0f);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        return true;
    }
}
