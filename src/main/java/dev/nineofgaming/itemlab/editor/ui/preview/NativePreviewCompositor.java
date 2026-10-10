package dev.nineofgaming.itemlab.editor.ui.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.impl.ImGuiMCImpl;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/**
 * Renders Minecraft-native preview elements into a transparent target and adds
 * that target to the editor's ImGui window hierarchy. This puts the previews
 * above ordinary editor widgets while leaving ImGui popups and tooltips above
 * them, with normal alpha compositing between every layer.
 */
@SuppressWarnings("UnstableApiUsage")
public final class NativePreviewCompositor {
    private static RenderTarget activeRenderTarget;

    private RenderTarget target;

    public void beginFrame(Minecraft client) {
        int width = Math.max(1, client.getWindow().getWidth());
        int height = Math.max(1, client.getWindow().getHeight());
        if (target == null) {
            target = ImGuiMCImpl.createRenderTarget(width, height, false);
        } else if (target.width != width || target.height != height) {
            ImGuiMCImpl.resizeRenderTarget(target, width, height);
        }
    }

    /**
     * Adds a final child draw-list layer without creating an input surface.
     */
    public void drawCompositeLayer() {
        if (target == null || ImGuiMCImpl.handler == null) return;

        float cursorX = ImGui.getCursorPosX();
        float cursorY = ImGui.getCursorPosY();
        // Keep the tiny carrier child inside the parent's content clip. A child
        // placed at the window origin sits under the title bar, so ImGui culls
        // its draw list and silently drops the composite image command.
        ImGui.setCursorScreenPos(
            ImGui.getWindowPosX() + ImGui.getWindowContentRegionMinX(),
            ImGui.getWindowPosY() + ImGui.getWindowContentRegionMinY()
        );
        ImGui.beginChild(
            "##itemlab_native_preview_composite",
            1.0f,
            1.0f,
            ImGuiChildFlags.None,
            ImGuiWindowFlags.NoInputs
                | ImGuiWindowFlags.NoBackground
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.NoScrollWithMouse
                | ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoFocusOnAppearing
        );
        try {
            ImGuiViewport viewport = ImGui.getMainViewport();
            long textureId = ImGuiMCImpl.handler.getRenderer().getImGuiId(
                ImGuiMC.getColorTexture(target),
                null
            );
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.pushClipRectFullScreen();
            try {
                drawList.addImage(
                    textureId,
                    viewport.getPosX(),
                    viewport.getPosY(),
                    viewport.getPosX() + viewport.getSizeX(),
                    viewport.getPosY() + viewport.getSizeY(),
                    0.0f,
                    1.0f,
                    1.0f,
                    0.0f
                );
            } finally {
                drawList.popClipRect();
            }
        } finally {
            ImGui.endChild();
            ImGui.setCursorPos(cursorX, cursorY);
            // SetCursorPos marks the parent as manually extended. Submit a
            // zero-size item so End() can validate the restored boundary.
            ImGui.dummy(0.0f, 0.0f);
        }
    }

    public void renderIntoTarget(Runnable renderer) {
        if (target == null) return;

        //? if >=26.2 {
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
            Objects.requireNonNull(target.getColorTexture()),
            new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f)
        );
        //?} else {
        /*RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);
         *///?}

        activeRenderTarget = target;
        try {
            renderer.run();
        } finally {
            activeRenderTarget = null;
        }
    }

    /**
     * Used by the GUI-renderer mixin only while the preview pass is active.
     */
    public static RenderTarget resolveRenderTarget(RenderTarget fallback) {
        return activeRenderTarget == null ? fallback : activeRenderTarget;
    }
}
