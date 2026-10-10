package dev.nineofgaming.itemlab.editor.ui.preview;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;
 *///?}
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Native model preview across GUI, held, dropped, framed, and head contexts.
 */
public final class ItemModelPreview {
    private static final float CANVAS_HEIGHT = 300.0f;
    private static final float MAX_PITCH_DEGREES = 60.0f;
    private static final int PLAYER_ENTITY_ID = Integer.MIN_VALUE + 3;
    private static final int ITEM_ENTITY_ID = Integer.MIN_VALUE + 4;
    private static final int FRAME_ENTITY_ID = Integer.MIN_VALUE + 5;
    private static final int DISPLAY_ENTITY_ID = Integer.MIN_VALUE + 6;
    private static final int SHELF_ENTITY_ID = Integer.MIN_VALUE + 7;

    private ItemStack stack = ItemStack.EMPTY;
    private ItemModelPreviewMode mode = ItemModelPreviewMode.FIXED;
    private ClientLevel entityLevel;
    private RemotePlayer playerMannequin;
    private ItemEntity droppedItem;
    private ItemFrame itemFrame;
    private Display.ItemDisplay directDisplay;
    private Display.BlockDisplay shelfDisplay;
    private NativePreviewGeometry.Capture geometry;
    private float yawDegrees;
    private float pitchDegrees;
    private boolean requestedThisFrame;

    public void beginImGuiFrame() {
        requestedThisFrame = false;
    }

    public void draw(Minecraft client, ItemStack previewStack, ItemModelPreviewMode previewMode) {
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        ImGui.invisibleButton("##item_model_preview_canvas", width, CANVAS_HEIGHT);

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
        if (!ImGui.isItemVisible()) return;

        if (previewMode != mode) {
            mode = previewMode;
            yawDegrees = 0.0f;
            pitchDegrees = 0.0f;
        }
        if (mode.isRotatable()) {
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
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
        }

        geometry = currentGeometry;
        stack = previewStack == null ? ItemStack.EMPTY : previewStack.copy();
        requestedThisFrame = true;
    }

    public void endImGuiFrame() {
        if (!requestedThisFrame) stack = ItemStack.EMPTY;
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        requestedThisFrame = false;
        entityLevel = null;
        playerMannequin = null;
        droppedItem = null;
        itemFrame = null;
        directDisplay = null;
        shelfDisplay = null;
        yawDegrees = 0.0f;
        pitchDegrees = 0.0f;
    }

    public boolean extract(GuiGraphicsExtractor graphics) {
        if (!requestedThisFrame) return false;

        NativePreviewGeometry.Bounds bounds = geometry.resolve(graphics);
        if (!bounds.valid()) return false;
        int left = bounds.left();
        int top = bounds.top();
        int right = bounds.right();
        int bottom = bounds.bottom();
        int clipLeft = bounds.clipLeft();
        int clipTop = bounds.clipTop();
        int clipRight = bounds.clipRight();
        int clipBottom = bounds.clipBottom();

        if (mode == ItemModelPreviewMode.GUI) {
            extractGui(graphics, left, top, right, bottom, clipLeft, clipTop, clipRight, clipBottom);
            return true;
        }

        if (mode == ItemModelPreviewMode.ON_SHELF) {
            return extractShelf(graphics, left, top, right, bottom, clipLeft, clipTop, clipRight, clipBottom);
        }

        Minecraft client = Minecraft.getInstance();
        Entity entity = prepareEntity(client);
        if (entity == null) return false;
        EntityRenderState renderState = extractRenderState(client.getEntityRenderDispatcher(), entity);
        if (mode.usesDirectModel() && renderState instanceof ItemDisplayEntityRenderState displayState) {
            client.getItemModelResolver().updateForNonLiving(
                displayState.item,
                stack.copyWithCount(1),
                mode.directContext(),
                entity
            );
        }
        prepareRenderState(renderState);

        if (mode.isFirstPerson() || mode == ItemModelPreviewMode.FIXED) {
            if (!(renderState instanceof ItemDisplayEntityRenderState displayState)) return false;
            return extractDirectItem(
                graphics,
                displayState,
                left,
                top,
                right,
                bottom,
                clipLeft,
                clipTop,
                clipRight,
                clipBottom
            );
        }

        int width = right - left;
        int height = bottom - top;
        float scale = switch (mode) {
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, HEAD ->
                Math.clamp(Math.min(width * 0.22f, height * 0.42f), 20.0f, 64.0f);
            case GROUND -> Math.clamp(Math.min(width * 0.34f, height * 0.58f), 48.0f, 150.0f);
            case FRAME -> Math.clamp(Math.min(width * 0.32f, height * 0.55f), 44.0f, 140.0f);
            default -> 64.0f;
        };
        Quaternionf poseRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf()
            .rotateX(pitchDegrees * ((float) Math.PI / 180.0f));
        // Match the equipment preview's orbit order: pitch in view space,
        // followed by yaw around the previewed object.
        poseRotation.mul(cameraRotation);
        if (mode == ItemModelPreviewMode.FRAME) {
            // Entity-space yaw runs opposite to the mannequin's model-space yaw.
            poseRotation.rotateY(-yawDegrees * ((float) Math.PI / 180.0f));
        }
        Vector3f rotatedCenter = poseRotation.transform(modelCenter(renderState));
        Vector3f translation = new Vector3f(0.0f, 0.0625f, 0.0f).sub(rotatedCenter);

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        graphics.entity(renderState, scale, translation, poseRotation, cameraRotation, left, top, right, bottom);
        graphics.disableScissor();
        return true;
    }

    private boolean extractDirectItem(
        GuiGraphicsExtractor graphics,
        ItemDisplayEntityRenderState displayState,
        int left,
        int top,
        int right,
        int bottom,
        int clipLeft,
        int clipTop,
        int clipRight,
        int clipBottom
    ) {
        int width = right - left;
        int height = bottom - top;
        float scale = mode.isFirstPerson()
            ? 1.0f
            : Math.clamp(Math.min(width * 0.32f, height * 0.52f), 44.0f, 132.0f);
        Quaternionf rotation = mode.isFirstPerson()
            ? new Quaternionf()
            : new Quaternionf().rotateZ((float) Math.PI);
        Vector3f itemTranslation;
        if (mode.isFirstPerson()) {
            float side = mode == ItemModelPreviewMode.FIRST_PERSON_RIGHT_HAND ? 1.0f : -1.0f;
            // Vanilla's fully-equipped neutral first-person hand transform.
            itemTranslation = new Vector3f(side * 0.56f, -0.52f, -0.72f);
        } else {
            rotation.rotateX(pitchDegrees * ((float) Math.PI / 180.0f));
            rotation.rotateY(-yawDegrees * ((float) Math.PI / 180.0f));
            AABB bounds = displayState.item.getModelBoundingBox();
            itemTranslation = new Vector3f(
                (float) -(bounds.minX + bounds.maxX) / 2.0f,
                (float) -(bounds.minY + bounds.maxY) / 2.0f,
                (float) -(bounds.minZ + bounds.maxZ) / 2.0f
            );
        }

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        Minecraft client = Minecraft.getInstance();
        float fieldOfView = ((NativeGuiOverlayRenderer) client.gameRenderer).itemlab$getHudFov();
        graphics.guiRenderState.addPicturesInPictureState(new DirectItemPreviewRenderState(
            displayState.item,
            mode.isFirstPerson()
                ? new Vector3f()
                : new Vector3f(0.0f, 0.0625f, 0.0f),
            rotation,
            itemTranslation,
            stack.copyWithCount(1),
            mode.directContext(),
            mode.isFirstPerson(),
            fieldOfView,
            left,
            top,
            right,
            bottom,
            scale,
            graphics.scissorStack.peek()
        ));
        graphics.disableScissor();
        return true;
    }

    private void extractGui(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        int right,
        int bottom,
        int clipLeft,
        int clipTop,
        int clipRight,
        int clipBottom
    ) {
        float scale = Math.max(1.0f, Math.min(right - left, bottom - top) * 0.58f / 16.0f);
        float slotSize = ItemIconPreview.NATIVE_SLOT_SIZE * scale;
        float x = (left + right - slotSize) / 2.0f;
        float y = (top + bottom - slotSize) / 2.0f;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            ItemIconPreview.SLOT_SPRITE,
            0,
            0,
            ItemIconPreview.NATIVE_SLOT_SIZE,
            ItemIconPreview.NATIVE_SLOT_SIZE
        );
        if (!stack.isEmpty()) {
            graphics.item(stack, 1, 1);
            graphics.itemDecorations(Minecraft.getInstance().font, stack, 1, 1);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    private boolean extractShelf(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        int right,
        int bottom,
        int clipLeft,
        int clipTop,
        int clipRight,
        int clipBottom
    ) {
        Minecraft client = Minecraft.getInstance();
        if (!prepareShelfEntities(client)) return false;

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        EntityRenderState shelfState = extractRenderState(dispatcher, shelfDisplay);
        EntityRenderState itemState = extractRenderState(dispatcher, directDisplay);
        if (!(shelfState instanceof BlockDisplayEntityRenderState blockDisplayState)
            || !(itemState instanceof ItemDisplayEntityRenderState itemDisplayState)) {
            return false;
        }
        client.getItemModelResolver().updateForNonLiving(
            itemDisplayState.item,
            stack.copyWithCount(1),
            ItemModelPreviewMode.ON_SHELF.directContext(),
            directDisplay
        );

        int width = right - left;
        int height = bottom - top;
        float scale = Math.clamp(Math.min(width * 0.32f, height * 0.52f), 44.0f, 132.0f);
        Quaternionf cameraRotation = new Quaternionf()
            .rotateX(pitchDegrees * ((float) Math.PI / 180.0f));
        Quaternionf poseRotation = new Quaternionf()
            .rotateZ((float) Math.PI)
            .mul(cameraRotation)
            .rotateY(-yawDegrees * ((float) Math.PI / 180.0f));
        // The shelf body occupies the rear 5/16 of the block, so its visual
        // center is farther back than the center of a full cube.
        Vector3f rotatedCenter = poseRotation.transform(new Vector3f(0.5f, 0.5f, 0.84375f));
        Vector3f translation = new Vector3f(0.0f, 0.0625f, 0.0f).sub(rotatedCenter);

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        graphics.guiRenderState.addPicturesInPictureState(new ShelfPreviewRenderState(
            blockDisplayState.blockModel,
            itemDisplayState.item,
            translation,
            poseRotation,
            left,
            top,
            right,
            bottom,
            scale,
            graphics.scissorStack.peek()
        ));
        graphics.disableScissor();
        return true;
    }

    private boolean prepareShelfEntities(Minecraft client) {
        if (!ensureEntityLevel(client)) return false;
        if (shelfDisplay == null) {
            //? if >=26.2 {
            shelfDisplay = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, Objects.requireNonNull(client.level));
            //?} else {
            /*shelfDisplay = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, client.level);
             *///?}
            shelfDisplay.setId(SHELF_ENTITY_ID);
        }
        shelfDisplay.setBlockState(Blocks.OAK_SHELF.defaultBlockState()
            .setValue(ShelfBlock.FACING, Direction.NORTH));
        shelfDisplay.tick();

        Display.ItemDisplay itemDisplay = directDisplay(client);
        Objects.requireNonNull(itemDisplay.getSlot(0)).set(stack.copyWithCount(1));
        itemDisplay.tick();
        return true;
    }

    private Entity prepareEntity(Minecraft client) {
        if (!ensureEntityLevel(client)) return null;

        if (mode == ItemModelPreviewMode.GROUND) {
            if (droppedItem == null) {
                droppedItem = new ItemEntity(Objects.requireNonNull(client.level), 0.0, 0.0, 0.0, stack.copy());
                droppedItem.setId(ITEM_ENTITY_ID);
            }
            droppedItem.setItem(stack.copy());
            droppedItem.setDeltaMovement(0.0, 0.0, 0.0);
            return droppedItem;
        }
        if (mode == ItemModelPreviewMode.FRAME) {
            if (itemFrame == null) {
                // NORTH cancels the renderer's built-in 180-degree horizontal
                // turn, giving the frame a front-facing neutral view.
                itemFrame = new ItemFrame(Objects.requireNonNull(client.level), BlockPos.ZERO, Direction.NORTH);
                itemFrame.setId(FRAME_ENTITY_ID);
            }
            itemFrame.setItem(stack.copy(), false);
            itemFrame.setRotation(0);
            return itemFrame;
        }
        if (mode.usesDirectModel()) {
            Display.ItemDisplay itemDisplay = directDisplay(client);
            Objects.requireNonNull(itemDisplay.getSlot(0)).set(stack.copyWithCount(1));
            itemDisplay.tick();
            return itemDisplay;
        }

        if (playerMannequin == null) {
            playerMannequin = new RemotePlayer(Objects.requireNonNull(client.level), Objects.requireNonNull(client.player).getGameProfile());
            playerMannequin.setId(PLAYER_ENTITY_ID);
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            playerMannequin.setItemSlot(slot, ItemStack.EMPTY);
        }
        playerMannequin.setMainArm(HumanoidArm.RIGHT);
        EquipmentSlot previewSlot = switch (mode) {
            case HEAD -> EquipmentSlot.HEAD;
            case THIRD_PERSON_LEFT_HAND -> EquipmentSlot.OFFHAND;
            default -> EquipmentSlot.MAINHAND;
        };
        playerMannequin.setItemSlot(previewSlot, stack.copyWithCount(1));
        playerMannequin.setInvisible(false);
        playerMannequin.setXRot(0.0f);
        playerMannequin.setYRot(0.0f);
        playerMannequin.setYBodyRot(0.0f);
        playerMannequin.setYHeadRot(0.0f);
        return playerMannequin;
    }

    private boolean ensureEntityLevel(Minecraft client) {
        if (client.level == null || client.player == null) return false;
        if (entityLevel != client.level) {
            entityLevel = client.level;
            playerMannequin = null;
            droppedItem = null;
            itemFrame = null;
            directDisplay = null;
            shelfDisplay = null;
        }
        return true;
    }

    private Display.ItemDisplay directDisplay(Minecraft client) {
        if (directDisplay == null) {
            //? if >=26.2 {
            directDisplay = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, Objects.requireNonNull(client.level));
            //?} else {
            /*directDisplay = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, client.level);
             *///?}
            directDisplay.setId(DISPLAY_ENTITY_ID);
        }
        return directDisplay;
    }

    private void prepareRenderState(EntityRenderState state) {
        state.nameTag = null;
        state.scoreText = null;
        if (state instanceof ItemEntityRenderState itemState) {
            // Drive the renderer's own ground-item rotation. Rotating outside
            // the entity renderer produced a different axis and an odd orbit.
            // The dropped-item renderer's zero angle presents the model back-to-front
            // in the GUI entity camera, so its neutral preview needs a half-turn.
            itemState.bobOffset = (float) Math.PI - yawDegrees * ((float) Math.PI / 180.0f);
            itemState.ageInTicks = 0.0f;
        }
        if (!(state instanceof LivingEntityRenderState livingState)) return;
        livingState.bodyRot = 180.0f + yawDegrees;
        livingState.yRot = 0.0f;
        livingState.xRot = 0.0f;
        livingState.boundingBoxWidth /= livingState.scale;
        livingState.boundingBoxHeight /= livingState.scale;
        livingState.scale = 1.0f;
        if (livingState instanceof AvatarRenderState avatarState) {
            avatarState.showHat = true;
            avatarState.showJacket = true;
            avatarState.showLeftSleeve = true;
            avatarState.showRightSleeve = true;
            avatarState.showLeftPants = true;
            avatarState.showRightPants = true;
        }
    }

    private static Vector3f modelCenter(EntityRenderState state) {
        if (state instanceof ItemEntityRenderState itemState && !itemState.item.isEmpty()) {
            AABB bounds = itemState.item.getModelBoundingBox();
            float spin = ItemEntity.getSpin(itemState.ageInTicks, itemState.bobOffset);
            Vector3f center = new Vector3f(
                (float) ((bounds.minX + bounds.maxX) / 2.0),
                (float) ((bounds.minY + bounds.maxY) / 2.0),
                (float) ((bounds.minZ + bounds.maxZ) / 2.0)
            );
            new Quaternionf().rotateY(spin).transform(center);
            center.y += (float) -bounds.minY + 0.1625f + (float) Math.sin(spin) * 0.1f;
            return center;
        }
        if (state instanceof ItemFrameRenderState frameState) {
            Direction direction = frameState.direction;
            // The entity renderer moves to the backing-block origin by 15/32,
            // then the thin frame model and displayed item sit 14/32 back toward
            // the viewer. Their visible shared plane is therefore only 1/32 from
            // the entity origin; using 15/32 made the frame orbit that plane.
            return new Vector3f(
                direction.getStepX() * 0.03125f,
                direction.getStepY() * 0.03125f,
                direction.getStepZ() * 0.03125f
            );
        }
        if (state instanceof ItemDisplayEntityRenderState displayState && !displayState.item.isEmpty()) {
            AABB bounds = displayState.item.getModelBoundingBox();
            Vector3f center = new Vector3f(
                (float) ((bounds.minX + bounds.maxX) / 2.0),
                (float) ((bounds.minY + bounds.maxY) / 2.0),
                (float) ((bounds.minZ + bounds.maxZ) / 2.0)
            );
            // ItemDisplayRenderer performs this half-turn before submitting its
            // item state. Include it when centering the transformed model.
            return new Quaternionf().rotateY((float) Math.PI).transform(center);
        }
        return new Vector3f(0.0f, Math.max(0.25f, state.boundingBoxHeight / 2.0f), 0.0f);
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
