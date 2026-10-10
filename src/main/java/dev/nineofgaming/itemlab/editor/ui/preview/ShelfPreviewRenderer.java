package dev.nineofgaming.itemlab.editor.ui.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
//? if <26.2 {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
*///?}
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

/**
 * Renders the shelf and its center-slot item into a shared depth buffer.
 */
public final class ShelfPreviewRenderer extends PictureInPictureRenderer<ShelfPreviewRenderState> {
    private static final int FULL_BRIGHT = 0x00F000F0;

    //? if >=26.2 {
    public ShelfPreviewRenderer() {
        super();
    }
    //?} else {
    /*public ShelfPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }
    *///?}

    @Override
    public @NonNull Class<ShelfPreviewRenderState> getRenderStateClass() {
        return ShelfPreviewRenderState.class;
    }

    //? if >=26.2 {
    @Override
    protected void renderToTexture(
        @NonNull ShelfPreviewRenderState state,
        @NonNull PoseStack pose,
        @NonNull SubmitNodeCollector collector
    ) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(
            com.mojang.blaze3d.platform.Lighting.Entry.ENTITY_IN_UI
        );
        renderScene(state, pose, collector);
    }
    //?} else {
    /*@Override
    protected void renderToTexture(ShelfPreviewRenderState state, PoseStack pose) {
        Minecraft client = Minecraft.getInstance();
        client.gameRenderer.getLighting().setupFor(
                com.mojang.blaze3d.platform.Lighting.Entry.ENTITY_IN_UI
        );
        FeatureRenderDispatcher features = client.gameRenderer.getFeatureRenderDispatcher();
        renderScene(state, pose, features.getSubmitNodeStorage());
        features.renderAllFeatures();
    }
    *///?}

    private static void renderScene(
        ShelfPreviewRenderState state,
        PoseStack pose,
        SubmitNodeCollector collector
    ) {
        pose.translate(state.translation().x, state.translation().y, state.translation().z);
        pose.mulPose(state.rotation());
        state.shelf().submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);

        if (state.item().isEmpty()) return;
        pose.pushPose();
        // This is the center-slot transform used by vanilla's ShelfRenderer.
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-180.0f));
        pose.translate(0.0f, 0.0f, -0.25f);
        pose.scale(0.25f, 0.25f, 0.25f);
        AABB bounds = state.item().getModelBoundingBox();
        pose.translate(0.0, -(bounds.minY + bounds.maxY) / 2.0, 0.0);
        state.item().submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    protected float getTranslateY(int textureHeight, int guiScale) {
        return textureHeight / 2.0f;
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return "itemlab shelf preview";
    }
}
