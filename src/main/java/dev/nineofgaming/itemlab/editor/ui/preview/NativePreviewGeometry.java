package dev.nineofgaming.itemlab.editor.ui.preview;

import imgui.ImGui;
import imgui.ImGuiViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Converts ImGui screen rectangles to stable Minecraft GUI-pixel rectangles.
 */
final class NativePreviewGeometry {
    private NativePreviewGeometry() {
    }

    static Capture captureCurrentItem(Minecraft client) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        float viewportWidth = Math.max(1.0f, viewport.getSizeX());
        float viewportHeight = Math.max(1.0f, viewport.getSizeY());
        float guiWidth = renderWidth(client);
        float guiHeight = renderHeight(client);

        float rawLeft = ImGui.getItemRectMinX();
        float rawTop = ImGui.getItemRectMinY();
        float rawRight = ImGui.getItemRectMaxX();
        float rawBottom = ImGui.getItemRectMaxY();

        int left = Math.round((rawLeft - viewport.getPosX()) * guiWidth / viewportWidth);
        int top = Math.round((rawTop - viewport.getPosY()) * guiHeight / viewportHeight);
        int width = Math.max(1, Math.round((rawRight - rawLeft) * guiWidth / viewportWidth));
        int height = Math.max(1, Math.round((rawBottom - rawTop) * guiHeight / viewportHeight));

        return new Capture(
            left,
            top,
            width,
            height,
            resolveCoordinate(
                ImGui.getWindowDrawList().getClipRectMinX(),
                viewport.getPosX(), viewportWidth, guiWidth
            ),
            resolveCoordinate(
                ImGui.getWindowDrawList().getClipRectMinY(),
                viewport.getPosY(), viewportHeight, guiHeight
            ),
            resolveCoordinate(
                ImGui.getWindowDrawList().getClipRectMaxX(),
                viewport.getPosX(), viewportWidth, guiWidth
            ),
            resolveCoordinate(
                ImGui.getWindowDrawList().getClipRectMaxY(),
                viewport.getPosY(), viewportHeight, guiHeight
            ),
            viewport.getPosX() + left * viewportWidth / guiWidth,
            viewport.getPosY() + top * viewportHeight / guiHeight,
            viewport.getPosX() + (left + width) * viewportWidth / guiWidth,
            viewport.getPosY() + (top + height) * viewportHeight / guiHeight
        );
    }

    static float imGuiScale(Minecraft client) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        return Math.max(1.0f, viewport.getSizeY()) / renderHeight(client);
    }

    static int resolveScreenX(float screenX, Minecraft client) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        return resolveCoordinate(
            screenX,
            viewport.getPosX(),
            Math.max(1.0f, viewport.getSizeX()),
            renderWidth(client)
        );
    }

    static int resolveScreenY(float screenY, Minecraft client) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        return resolveCoordinate(
            screenY,
            viewport.getPosY(),
            Math.max(1.0f, viewport.getSizeY()),
            renderHeight(client)
        );
    }

    private static int resolveCoordinate(float value, float origin, float extent, float guiExtent) {
        return Math.round((value - origin) * guiExtent / extent);
    }

    private static float renderWidth(Minecraft client) {
        return client.getWindow().getWidth() / (float) Math.max(1, client.getWindow().getGuiScale());
    }

    private static float renderHeight(Minecraft client) {
        return client.getWindow().getHeight() / (float) Math.max(1, client.getWindow().getGuiScale());
    }

    record Capture(
        int left,
        int top,
        int width,
        int height,
        int clipLeft,
        int clipTop,
        int clipRight,
        int clipBottom,
        float screenLeft,
        float screenTop,
        float screenRight,
        float screenBottom
    ) {
        Bounds resolve(GuiGraphicsExtractor graphics) {
            return new Bounds(
                left,
                top,
                left + width,
                top + height,
                Math.clamp(clipLeft, 0, graphics.guiWidth()),
                Math.clamp(clipTop, 0, graphics.guiHeight()),
                Math.clamp(clipRight, 0, graphics.guiWidth()),
                Math.clamp(clipBottom, 0, graphics.guiHeight())
            );
        }
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
