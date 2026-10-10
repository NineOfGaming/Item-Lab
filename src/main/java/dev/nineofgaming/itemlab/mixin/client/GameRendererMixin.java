package dev.nineofgaming.itemlab.mixin.client;

import dev.nineofgaming.itemlab.editor.ui.preview.NativeGuiOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
//? if <26.2 {
/*import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
*///?}
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements NativeGuiOverlayRenderer {
    @Shadow
    @Final
    private GameRenderState gameRenderState;
    @Shadow
    @Final
    private GuiRenderer guiRenderer;
    //? if <26.2 {
    /*@Shadow @Final private FogRenderer fogRenderer;
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;
    @Shadow @Final private SubmitNodeStorage submitNodeStorage;
    *///?}

    @Override
    public GuiRenderState itemlab$getGuiRenderState() {
        return gameRenderState.guiRenderState;
    }

    @Override
    public float itemlab$getHudFov() {
        var cameraState = gameRenderState.levelRenderState.cameraRenderState;
        if (cameraState.hudFov > 0.0f) return cameraState.hudFov;
        return Minecraft.getInstance().options.fov().get();
    }

    @Override
    public void itemlab$renderGuiOverlay() {
        //? if >=26.2 {
        guiRenderer.render();
        //?} else {
        /*guiRenderer.render(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
         *///?}
        guiRenderer.endFrame();

        //? if <26.2 {
        /*// Tooltip images can contain item renders (for example bundle contents),
        // so finish the collectors populated by the second GUI pass on 26.1.
        submitNodeStorage.endFrame();
        featureRenderDispatcher.endFrame();
        *///?}
    }
}
