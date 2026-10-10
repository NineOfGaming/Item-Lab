package dev.nineofgaming.itemlab.editor.ui.preview;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A live Minecraft entity preview for equippable items, trims, dyes, and worn assets.
 */
public final class EquipmentMannequinPreview {
    private static final float CANVAS_HEIGHT = 320.0f;
    private static final float MAX_PITCH_DEGREES = 60.0f;
    private static final int PLAYER_PREVIEW_ENTITY_ID = Integer.MIN_VALUE;
    private static final int ARMOR_STAND_PREVIEW_ENTITY_ID = Integer.MIN_VALUE + 1;

    private ItemStack stack = ItemStack.EMPTY;
    private EquipmentPreviewBase base = EquipmentPreviewBase.PLAYER;
    private ClientLevel entityLevel;
    private RemotePlayer playerMannequin;
    private ArmorStand armorStandMannequin;
    private NativePreviewGeometry.Capture geometry;
    private float yawDegrees;
    private float pitchDegrees;
    private boolean requestedThisFrame;

    public void beginImGuiFrame() {
        requestedThisFrame = false;
    }

    public void draw(Minecraft client, ItemStack previewStack, EquipmentPreviewBase previewBase) {
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        ImGui.invisibleButton("##equipment_mannequin_canvas", width, CANVAS_HEIGHT);

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

        boolean hovered = ImGui.isItemHovered();
        if (hovered && ImGui.isMouseDoubleClicked(0)) {
            yawDegrees = 0.0f;
            pitchDegrees = 0.0f;
        } else if (ImGui.isItemActive() && ImGui.isMouseDown(0)) {
            yawDegrees = wrapDegrees(yawDegrees - ImGui.getIO().getMouseDeltaX() * 0.8f);
            pitchDegrees = Math.clamp(
                pitchDegrees - ImGui.getIO().getMouseDeltaY() * 0.45f,
                -MAX_PITCH_DEGREES,
                MAX_PITCH_DEGREES
            );
        }

        geometry = currentGeometry;
        stack = previewStack == null ? ItemStack.EMPTY : previewStack.copyWithCount(1);
        base = previewBase;
        requestedThisFrame = true;
    }

    public void endImGuiFrame() {
        if (!requestedThisFrame) {
            stack = ItemStack.EMPTY;
        }
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        requestedThisFrame = false;
        entityLevel = null;
        playerMannequin = null;
        armorStandMannequin = null;
    }

    public boolean extract(GuiGraphicsExtractor graphics) {
        if (!requestedThisFrame) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        LivingEntity mannequin = prepareMannequin(client);
        if (mannequin == null) {
            return false;
        }

        NativePreviewGeometry.Bounds bounds = geometry.resolve(graphics);
        if (!bounds.valid()) {
            return false;
        }

        int width = bounds.width();
        int height = bounds.height();
        float scale = Math.clamp(Math.min(width * 0.22f, height * 0.42f), 20.0f, 60.0f);
        EntityRenderState renderState = extractRenderState(client.getEntityRenderDispatcher(), mannequin);
        Quaternionf poseRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf().rotateX(pitchDegrees * ((float) Math.PI / 180.0f));
        poseRotation.mul(cameraRotation);
        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyRot = 180.0f + yawDegrees;
            // The preview rotates the whole mannequin. These are model-relative
            // look angles, so leave them neutral instead of turning its head too.
            livingState.yRot = 0.0f;
            livingState.xRot = 0.0f;
            livingState.boundingBoxWidth /= livingState.scale;
            livingState.boundingBoxHeight /= livingState.scale;
            livingState.scale = 1.0f;
        }
        if (renderState instanceof AvatarRenderState avatarState) {
            // A synthetic RemotePlayer has no PlayerInfo from which Minecraft can
            // read model-customization flags, so its overlay layers default off.
            avatarState.showHat = true;
            avatarState.showJacket = true;
            avatarState.showLeftSleeve = true;
            avatarState.showRightSleeve = true;
            avatarState.showLeftPants = true;
            avatarState.showRightPants = true;
        }
        // GUI entities are normally rotated around their origin at their feet.
        // Offset by the rotated midpoint so pitch keeps the mannequin centered.
        Vector3f rotatedMidpoint = poseRotation.transform(
            new Vector3f(0.0f, renderState.boundingBoxHeight / 2.0f, 0.0f)
        );
        Vector3f translation = new Vector3f(0.0f, 0.0625f, 0.0f).sub(rotatedMidpoint);

        graphics.enableScissor(bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom());
        graphics.entity(
            renderState,
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

    private LivingEntity prepareMannequin(Minecraft client) {
        if (client.level == null || client.player == null) {
            return null;
        }
        if (entityLevel != client.level) {
            entityLevel = client.level;
            playerMannequin = null;
            armorStandMannequin = null;
        }

        LivingEntity mannequin;
        if (base == EquipmentPreviewBase.ARMOR_STAND) {
            if (armorStandMannequin == null) {
                armorStandMannequin = new ArmorStand(client.level, 0.0, 0.0, 0.0);
                armorStandMannequin.setId(ARMOR_STAND_PREVIEW_ENTITY_ID);
                armorStandMannequin.setShowArms(true);
            }
            mannequin = armorStandMannequin;
        } else {
            if (playerMannequin == null) {
                playerMannequin = new RemotePlayer(client.level, client.player.getGameProfile());
                playerMannequin.setId(PLAYER_PREVIEW_ENTITY_ID);
            }
            mannequin = playerMannequin;
        }

        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            mannequin.setItemSlot(slot, ItemStack.EMPTY);
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (!stack.isEmpty() && equippable != null) {
            mannequin.setItemSlot(equippable.slot(), stack.copyWithCount(1));
        }
        mannequin.setInvisible(base == EquipmentPreviewBase.EQUIPMENT_ONLY);
        mannequin.setXRot(0.0f);
        mannequin.setYRot(0.0f);
        mannequin.setYBodyRot(0.0f);
        mannequin.setYHeadRot(0.0f);
        return mannequin;
    }
}
