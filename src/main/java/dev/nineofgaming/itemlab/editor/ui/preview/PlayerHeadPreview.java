package dev.nineofgaming.itemlab.editor.ui.preview;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Native, rotatable player-head preview with Minecraft's base and hat skin layers.
 */
public final class PlayerHeadPreview {
    private static final float CANVAS_HEIGHT = 250.0f;
    private static final float MAX_PITCH_DEGREES = 60.0f;
    private static final int PREVIEW_ENTITY_ID = Integer.MIN_VALUE + 2;

    private ItemStack stack = ItemStack.EMPTY;
    private PlayerSkinPreviewMode mode = PlayerSkinPreviewMode.HEAD_ONLY;
    private ClientLevel entityLevel;
    private RemotePlayer mannequin;
    private NativePreviewGeometry.Capture geometry;
    private float yawDegrees;
    private float pitchDegrees;
    private boolean requestedThisFrame;

    public void beginImGuiFrame() {
        requestedThisFrame = false;
    }

    public void draw(Minecraft client, ItemStack previewStack, PlayerSkinPreviewMode previewMode) {
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        ImGui.invisibleButton("##player_head_canvas", width, CANVAS_HEIGHT);

        NativePreviewGeometry.Capture currentGeometry =
            NativePreviewGeometry.captureCurrentItem(client);
        ImGui.getWindowDrawList().addRectFilled(
            currentGeometry.screenLeft(),
            currentGeometry.screenTop(),
            currentGeometry.screenRight(),
            currentGeometry.screenBottom(),
            ImGui.getColorU32(0.035f, 0.050f, 0.070f, 0.72f),
            4.0f
        );
        ImGui.getWindowDrawList().addRect(
            currentGeometry.screenLeft(),
            currentGeometry.screenTop(),
            currentGeometry.screenRight(),
            currentGeometry.screenBottom(),
            ImGui.getColorU32(0.16f, 0.23f, 0.33f, 0.72f),
            4.0f
        );

        if (!ImGui.isItemVisible() || client.level == null || client.player == null) {
            return;
        }

        if (mode != previewMode) {
            mode = previewMode;
            yawDegrees = 0.0f;
            pitchDegrees = 0.0f;
        }

        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            yawDegrees = 0.0f;
            pitchDegrees = 0.0f;
        } else if (ImGui.isItemActive() && ImGui.isMouseDown(0)) {
            yawDegrees = wrapDegrees(yawDegrees - ImGui.getIO().getMouseDeltaX() * 0.8f);
            pitchDegrees = Math.clamp(
                pitchDegrees - ImGui.getIO().getMouseDeltaY() * 0.55f,
                -MAX_PITCH_DEGREES,
                MAX_PITCH_DEGREES
            );
        }

        geometry = currentGeometry;
        stack = previewStack == null ? ItemStack.EMPTY : previewStack.copyWithCount(1);
        requestedThisFrame = true;
    }

    public void endImGuiFrame() {
        if (!requestedThisFrame) stack = ItemStack.EMPTY;
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        requestedThisFrame = false;
        entityLevel = null;
        mannequin = null;
        yawDegrees = 0.0f;
        pitchDegrees = 0.0f;
    }

    public boolean extract(GuiGraphicsExtractor graphics) {
        if (!requestedThisFrame) return false;

        Minecraft client = Minecraft.getInstance();
        RemotePlayer player = prepareMannequin(client);
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (player == null || profile == null) return false;

        NativePreviewGeometry.Bounds bounds = geometry.resolve(graphics);
        if (!bounds.valid()) return false;

        int width = bounds.width();
        int height = bounds.height();
        float scale = mode == PlayerSkinPreviewMode.HEAD_ONLY
            ? Math.clamp(Math.min(width * 0.32f, height * 0.78f), 64.0f, 190.0f)
            : Math.clamp(Math.min(width * 0.22f, height * 0.42f), 20.0f, 60.0f);
        EntityRenderState renderState = extractRenderState(client.getEntityRenderDispatcher(), player);
        if (!(renderState instanceof AvatarRenderState avatarState)) return false;

        PlayerSkinRenderCache.RenderInfo skin = client.playerSkinRenderCache().getOrDefault(profile);
        avatarState.skin = skin.playerSkin();
        avatarState.isSpectator = mode == PlayerSkinPreviewMode.HEAD_ONLY;
        avatarState.showHat = true;
        avatarState.showJacket = true;
        avatarState.showLeftSleeve = true;
        avatarState.showRightSleeve = true;
        avatarState.showLeftPants = true;
        avatarState.showRightPants = true;
        avatarState.bodyRot = 180.0f + yawDegrees;
        avatarState.yRot = 0.0f;
        avatarState.xRot = 0.0f;
        avatarState.nameTag = null;
        avatarState.scoreText = null;
        avatarState.boundingBoxWidth /= avatarState.scale;
        avatarState.boundingBoxHeight /= avatarState.scale;
        avatarState.scale = 1.0f;

        Quaternionf poseRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf()
            .rotateX(pitchDegrees * ((float) Math.PI / 180.0f));
        poseRotation.mul(cameraRotation);
        float centerY = mode == PlayerSkinPreviewMode.HEAD_ONLY
            ? avatarState.boundingBoxHeight - 0.27f
            : avatarState.boundingBoxHeight / 2.0f;
        Vector3f rotatedCenter = poseRotation.transform(new Vector3f(0.0f, centerY, 0.0f));
        Vector3f translation = new Vector3f(0.0f, 0.0625f, 0.0f).sub(rotatedCenter);

        graphics.enableScissor(bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom());
        graphics.entity(
            avatarState,
            scale,
            translation,
            poseRotation,
            cameraRotation,
            bounds.left(),
            bounds.top(),
            bounds.right(),
            bounds.bottom()
        );
        graphics.disableScissor();
        return true;
    }

    private RemotePlayer prepareMannequin(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        if (entityLevel != client.level) {
            entityLevel = client.level;
            mannequin = null;
        }
        if (mannequin == null) {
            mannequin = new RemotePlayer(client.level, client.player.getGameProfile());
            mannequin.setId(PREVIEW_ENTITY_ID);
        }
        mannequin.setInvisible(false);
        mannequin.setXRot(0.0f);
        mannequin.setYRot(0.0f);
        mannequin.setYBodyRot(0.0f);
        mannequin.setYHeadRot(0.0f);
        return mannequin;
    }

    private static <T extends Entity> EntityRenderState extractRenderState(
        EntityRenderDispatcher dispatcher,
        T entity
    ) {
        return createRenderState(dispatcher.getRenderer(entity), entity);
    }

    private static <T extends Entity, S extends EntityRenderState> S createRenderState(
        EntityRenderer<? super T, S> renderer,
        T entity
    ) {
        S state = renderer.createRenderState(entity, 1.0f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        return state;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        return wrapped < -180.0f ? wrapped + 360.0f : wrapped > 180.0f ? wrapped - 360.0f : wrapped;
    }
}
