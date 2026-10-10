package dev.nineofgaming.itemlab.editor.ui.preview;

import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Access to Minecraft's GUI render pass used to populate the native preview composite.
 */
public interface NativeGuiOverlayRenderer {
    GuiRenderState itemlab$getGuiRenderState();

    float itemlab$getHudFov();

    void itemlab$renderGuiOverlay();
}
