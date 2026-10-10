package dev.nineofgaming.itemlab.mixin.client;

import dev.nineofgaming.itemlab.editor.ui.preview.DirectItemPreviewRenderState;
import dev.nineofgaming.itemlab.editor.ui.preview.DirectItemPreviewRenderer;
import dev.nineofgaming.itemlab.editor.ui.preview.NativePreviewCompositor;
import dev.nineofgaming.itemlab.editor.ui.preview.ShelfPreviewRenderState;
import dev.nineofgaming.itemlab.editor.ui.preview.ShelfPreviewRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
//? if <26.2 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
*///?}
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers Item Lab's composite shelf scene with Minecraft's GUI renderer.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Mutable
    @Shadow
    @Final
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void itemlab$registerShelfPreview(
        GuiRenderState renderState,
        //? if <26.2 {
            /*MultiBufferSource.BufferSource bufferSource,
            SubmitNodeCollector submitNodeCollector,
            *///?}
        FeatureRenderDispatcher featureRenderDispatcher,
        List<PictureInPictureRenderer<?>> pictureInPictureRenderers,
        CallbackInfo callbackInfo
    ) {
        Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> extended =
            new HashMap<>(this.pictureInPictureRenderers);
        //? if >=26.2 {
        DirectItemPreviewRenderer directItemRenderer = new DirectItemPreviewRenderer();
        ShelfPreviewRenderer shelfRenderer = new ShelfPreviewRenderer();
        //?} else {
        /*DirectItemPreviewRenderer directItemRenderer = new DirectItemPreviewRenderer(bufferSource);
        ShelfPreviewRenderer shelfRenderer = new ShelfPreviewRenderer(bufferSource);
        *///?}
        extended.put(DirectItemPreviewRenderState.class, directItemRenderer);
        extended.put(ShelfPreviewRenderState.class, shelfRenderer);
        this.pictureInPictureRenderers = Map.copyOf(extended);
    }

    //? if >=26.2 {
    @Redirect(
        method = "draw",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
        )
    )
    private RenderTarget itemlab$useNativePreviewTarget(net.minecraft.client.renderer.GameRenderer gameRenderer) {
        return NativePreviewCompositor.resolveRenderTarget(gameRenderer.mainRenderTarget());
    }
    //?} else {
    /*@Redirect(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
            )
    )
    private RenderTarget itemlab$useNativePreviewTarget(Minecraft client) {
        return NativePreviewCompositor.resolveRenderTarget(client.getMainRenderTarget());
    }
    *///?}
}
