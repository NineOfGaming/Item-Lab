package dev.nineofgaming.itemlab.editor.ui.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
//? if <26.2 {
/*import net.minecraft.client.renderer.MultiBufferSource;
 *///?}
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jspecify.annotations.NonNull;

/**
 * Submits a resolved item model without ItemDisplay's extra entity rotation or camera.
 */
public final class DirectItemPreviewRenderer extends PictureInPictureRenderer<DirectItemPreviewRenderState> {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float HUD_NEAR_PLANE = 0.05f;
    private static final float HUD_FAR_PLANE = 100.0f;
    private final Projection perspectiveProjection = new Projection();
    private final ProjectionMatrixBuffer perspectiveProjectionBuffer =
        new ProjectionMatrixBuffer("itemlab first-person preview");

    //? if >=26.2 {
    public DirectItemPreviewRenderer() {
        super();
    }
    //?} else {
    /*public DirectItemPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }
    *///?}

    @Override
    public @NonNull Class<DirectItemPreviewRenderState> getRenderStateClass() {
        return DirectItemPreviewRenderState.class;
    }

    //? if >=26.2 {
    @Override
    public void prepare(
        DirectItemPreviewRenderState state,
        @NonNull GuiRenderState guiRenderState,
        @NonNull FeatureRenderDispatcher featureRenderDispatcher,
        int guiScale
    ) {
        Lighting lighting = Minecraft.getInstance().gameRenderer.lighting();
        lighting.setupFor(lightingFor(state.perspective()));
        try {
            super.prepare(state, guiRenderState, featureRenderDispatcher, guiScale);
        } finally {
            // GameRenderer selects this before entering the GUI pass. Do not let
            // a direct preview change the lighting used by later GUI features.
            lighting.setupFor(Lighting.Entry.ITEMS_3D);
        }
    }

    @Override
    protected void renderToTexture(
        @NonNull DirectItemPreviewRenderState state,
        @NonNull PoseStack pose,
        @NonNull SubmitNodeCollector collector
    ) {
        renderItem(state, pose, collector);
    }
    //?} else {
    /*@Override
    public void prepare(DirectItemPreviewRenderState state, GuiRenderState guiRenderState, int guiScale) {
        Lighting lighting = Minecraft.getInstance().gameRenderer.getLighting();
        lighting.setupFor(lightingFor(state.perspective()));
        try {
            super.prepare(state, guiRenderState, guiScale);
        } finally {
            // GameRenderer selects this before entering the GUI pass. Do not let
            // a direct preview change the lighting used by later GUI features.
            lighting.setupFor(Lighting.Entry.ITEMS_3D);
        }
    }

    @Override
    protected void renderToTexture(DirectItemPreviewRenderState state, PoseStack pose) {
        Minecraft client = Minecraft.getInstance();
        FeatureRenderDispatcher features = client.gameRenderer.getFeatureRenderDispatcher();
        renderItem(state, pose, features.getSubmitNodeStorage());
        features.renderAllFeatures();
    }
    *///?}

    private void renderItem(
        DirectItemPreviewRenderState state,
        PoseStack pose,
        SubmitNodeCollector collector
    ) {
        if (state.perspective()) {
            configurePerspective(state, pose);
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                    client.player,
                    state.stack(),
                    state.displayContext(),
                    pose,
                    collector,
                    previewPackedLight()
                );
            }
            return;
        }
        pose.translate(
            state.viewportTranslation().x,
            state.viewportTranslation().y,
            state.viewportTranslation().z
        );
        pose.mulPose(state.rotation());
        pose.translate(
            state.itemTranslation().x,
            state.itemTranslation().y,
            state.itemTranslation().z
        );
        state.item().submit(
            pose,
            collector,
            previewPackedLight(),
            OverlayTexture.NO_OVERLAY,
            0
        );
    }

    static Lighting.Entry lightingFor(boolean perspective) {
        return perspective ? Lighting.Entry.LEVEL : Lighting.Entry.ENTITY_IN_UI;
    }

    static int previewPackedLight() {
        // Display context changes directional shading, not the stable
        // brightness expected from an editor preview.
        return FULL_BRIGHT;
    }

    private void configurePerspective(DirectItemPreviewRenderState state, PoseStack pose) {
        perspectiveProjection.setupPerspective(
            HUD_NEAR_PLANE,
            HUD_FAR_PLANE,
            state.fieldOfView(),
            Math.max(1.0f, state.x1() - state.x0()),
            Math.max(1.0f, state.y1() - state.y0())
        );
        RenderSystem.setProjectionMatrix(
            perspectiveProjectionBuffer.getBuffer(perspectiveProjection),
            ProjectionType.PERSPECTIVE
        );
        pose.setIdentity();
        pose.translate(
            state.itemTranslation().x,
            state.itemTranslation().y,
            state.itemTranslation().z
        );
    }

    @Override
    protected float getTranslateY(int textureHeight, int guiScale) {
        return textureHeight / 2.0f;
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return "itemlab direct item preview";
    }

    @Override
    public void close() {
        super.close();
        perspectiveProjectionBuffer.close();
    }
}
