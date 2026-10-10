package dev.nineofgaming.itemlab.editor.ui.preview;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared ImGui placement and clipping for native Minecraft content previews.
 */
final class NativePreviewCanvas {
    private NativePreviewGeometry.Capture geometry;
    private boolean requested;

    void beginFrame() {
        requested = false;
    }

    boolean draw(String id, float height) {
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        ImGui.invisibleButton("##" + id, width, height);

        geometry = NativePreviewGeometry.captureCurrentItem(Minecraft.getInstance());
        ImGui.getWindowDrawList().addRectFilled(
            geometry.screenLeft(),
            geometry.screenTop(),
            geometry.screenRight(),
            geometry.screenBottom(),
            ImGui.getColorU32(0.035f, 0.050f, 0.070f, 0.72f),
            4.0f
        );
        ImGui.getWindowDrawList().addRect(
            geometry.screenLeft(),
            geometry.screenTop(),
            geometry.screenRight(),
            geometry.screenBottom(),
            ImGui.getColorU32(0.16f, 0.23f, 0.33f, 0.72f),
            4.0f
        );
        if (!ImGui.isItemVisible()) {
            return false;
        }

        requested = true;
        return true;
    }

    boolean requested() {
        return requested;
    }

    Bounds resolve(GuiGraphicsExtractor graphics) {
        NativePreviewGeometry.Bounds bounds = geometry.resolve(graphics);
        return new Bounds(
            bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
            bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom()
        );
    }

    record Bounds(
        int left,
        int top,
        int right,
        int bottom,
        int clipLeft,
        int clipTop,
        int clipRight,
        int clipBottom
    ) {
        boolean valid() {
            return right > left && bottom > top && clipRight > clipLeft && clipBottom > clipTop;
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }
}
