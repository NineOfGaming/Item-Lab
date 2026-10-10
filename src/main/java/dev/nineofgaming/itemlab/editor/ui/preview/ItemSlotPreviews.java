package dev.nineofgaming.itemlab.editor.ui.preview;

import imgui.ImGui;
import imgui.flag.ImGuiButtonFlags;
import imgui.flag.ImGuiKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects independently positioned native inventory slots during an ImGui frame.
 */
final class ItemSlotPreviews {
    private static final float SLOT_SCALE = 1.0f;
    private static final int SELECTED_COLOR = 0xFFFFD45A;
    private static final int OVERFLOW_COLOR = 0xFFFF8A65;
    private static final int DROP_TARGET_FILL_COLOR = 0x663A9EFF;
    private static final int DROP_TARGET_OUTLINE_COLOR = 0xFF7DCFFF;
    private static final int DRAG_SOURCE_FILL_COLOR = 0x443A9EFF;
    private static final int DRAG_SOURCE_OUTLINE_COLOR = 0xFF7DCFFF;
    private static final int INSERTION_GUIDE_COLOR = 0x887DCFFF;
    private static final int INSERTION_TARGET_COLOR = 0xFFB9E2FF;
    private static final int INSERTION_NONE = 0;
    private static final int INSERTION_BEFORE = -1;
    private static final int INSERTION_AFTER = 1;

    private final List<Request> requests = new ArrayList<>();
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int mouseX;
    private int mouseY;

    void beginImGuiFrame() {
        requests.clear();
        hoveredStack = ItemStack.EMPTY;
    }

    float imGuiSize(Minecraft client) {
        return ItemIconPreview.NATIVE_SLOT_SIZE * SLOT_SCALE * NativePreviewGeometry.imGuiScale(client);
    }

    SlotInteraction draw(
        Minecraft client,
        String id,
        ItemStack stack,
        boolean selected,
        boolean overflow
    ) {
        float size = imGuiSize(client);
        boolean clicked = ImGui.invisibleButton(
            "##itemlab_slot_" + id,
            size,
            size,
            ImGuiButtonFlags.EnableNav
        );
        boolean hovered = ImGui.isItemHovered();
        boolean focused = ImGui.isItemFocused();
        boolean doubleClicked = hovered && ImGui.isMouseDoubleClicked(0);
        boolean keyboardActivated = focused && (ImGui.isKeyPressed(ImGuiKey.Enter, false)
            || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false));
        boolean deletePressed = focused && ImGui.isKeyPressed(ImGuiKey.Delete, false);
        SlotInteraction slotInteraction = new SlotInteraction(
            clicked,
            doubleClicked,
            hovered,
            focused,
            keyboardActivated,
            deletePressed
        );
        if (!ImGui.isItemVisible()) {
            return slotInteraction;
        }

        requests.add(new Request(
            NativePreviewGeometry.captureCurrentItem(client),
            stack == null ? ItemStack.EMPTY : stack.copy(),
            selected || focused,
            overflow,
            false,
            false,
            false,
            INSERTION_NONE
        ));
        if (hovered && stack != null && !stack.isEmpty()) {
            hoveredStack = stack.copy();
            mouseX = NativePreviewGeometry.resolveScreenX(ImGui.getMousePosX(), client);
            mouseY = NativePreviewGeometry.resolveScreenY(ImGui.getMousePosY(), client);
        }
        return slotInteraction;
    }

    void markLastDrawnSlotAsDropTarget() {
        if (requests.isEmpty()) return;
        int index = requests.size() - 1;
        Request request = requests.get(index);
        requests.set(index, new Request(
            request.geometry(),
            request.stack(),
            request.selected(),
            request.overflow(),
            true,
            request.dragSource(),
            request.insertionGuides(),
            request.insertionTarget()
        ));
    }

    void markLastDrawnSlotAsDragSource() {
        if (requests.isEmpty()) return;
        int index = requests.size() - 1;
        Request request = requests.get(index);
        requests.set(index, new Request(
            request.geometry(),
            request.stack(),
            request.selected(),
            request.overflow(),
            request.dropTarget(),
            true,
            request.insertionGuides(),
            request.insertionTarget()
        ));
    }

    void markLastDrawnSlotAsInsertionGuide() {
        setLastDrawnSlotInsertion(true, INSERTION_NONE);
    }

    void markLastDrawnSlotAsInsertionTarget(boolean after) {
        setLastDrawnSlotInsertion(true, after ? INSERTION_AFTER : INSERTION_BEFORE);
    }

    private void setLastDrawnSlotInsertion(boolean guides, int target) {
        if (requests.isEmpty()) return;
        int index = requests.size() - 1;
        Request request = requests.get(index);
        requests.set(index, new Request(
            request.geometry(),
            request.stack(),
            request.selected(),
            request.overflow(),
            request.dropTarget(),
            request.dragSource(),
            guides,
            target
        ));
    }

    void endImGuiFrame() {
        // Requests intentionally survive until the post-ImGui extraction pass.
    }

    void clear() {
        requests.clear();
        hoveredStack = ItemStack.EMPTY;
    }

    boolean extractSlots(GuiGraphicsExtractor graphics) {
        if (requests.isEmpty()) return false;
        for (Request request : requests) extractSlot(graphics, request);
        return true;
    }

    boolean extractHoveredTooltip(GuiGraphicsExtractor graphics) {
        if (!hoveredStack.isEmpty()) {
            Minecraft client = Minecraft.getInstance();
            graphics.setTooltipForNextFrame(client.font, hoveredStack, mouseX, mouseY);
            // Minecraft puts deferred tooltips in a new stratum. Keeping this as
            // the final extraction guarantees that no preview can cover it.
            graphics.extractDeferredElements(mouseX, mouseY, 0.0f);
            return true;
        }
        return false;
    }

    private static void extractSlot(GuiGraphicsExtractor graphics, Request request) {
        NativePreviewGeometry.Bounds bounds = request.geometry.resolve(graphics);
        if (!bounds.valid()) return;

        float scale = Math.min(
            bounds.width() / (float) ItemIconPreview.NATIVE_SLOT_SIZE,
            bounds.height() / (float) ItemIconPreview.NATIVE_SLOT_SIZE
        );
        graphics.enableScissor(bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.left(), bounds.top());
        graphics.pose().scale(scale, scale);
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            ItemIconPreview.SLOT_SPRITE,
            0,
            0,
            ItemIconPreview.NATIVE_SLOT_SIZE,
            ItemIconPreview.NATIVE_SLOT_SIZE
        );
        if (!request.stack.isEmpty()) {
            graphics.item(request.stack, 1, 1);
            graphics.itemDecorations(Minecraft.getInstance().font, request.stack, 1, 1);
        }
        if (request.selected || request.overflow) {
            graphics.outline(
                0,
                0,
                ItemIconPreview.NATIVE_SLOT_SIZE,
                ItemIconPreview.NATIVE_SLOT_SIZE,
                request.overflow ? OVERFLOW_COLOR : SELECTED_COLOR
            );
        }
        if (request.dropTarget) {
            graphics.fill(
                1,
                1,
                ItemIconPreview.NATIVE_SLOT_SIZE - 1,
                ItemIconPreview.NATIVE_SLOT_SIZE - 1,
                DROP_TARGET_FILL_COLOR
            );
            graphics.outline(
                0,
                0,
                ItemIconPreview.NATIVE_SLOT_SIZE,
                ItemIconPreview.NATIVE_SLOT_SIZE,
                DROP_TARGET_OUTLINE_COLOR
            );
        }
        if (request.dragSource) {
            graphics.fill(
                1,
                1,
                ItemIconPreview.NATIVE_SLOT_SIZE - 1,
                ItemIconPreview.NATIVE_SLOT_SIZE - 1,
                DRAG_SOURCE_FILL_COLOR
            );
            graphics.outline(
                0,
                0,
                ItemIconPreview.NATIVE_SLOT_SIZE,
                ItemIconPreview.NATIVE_SLOT_SIZE,
                DRAG_SOURCE_OUTLINE_COLOR
            );
        }
        if (request.insertionGuides) {
            graphics.fill(0, 1, 1, ItemIconPreview.NATIVE_SLOT_SIZE - 1, INSERTION_GUIDE_COLOR);
            graphics.fill(
                ItemIconPreview.NATIVE_SLOT_SIZE - 1,
                1,
                ItemIconPreview.NATIVE_SLOT_SIZE,
                ItemIconPreview.NATIVE_SLOT_SIZE - 1,
                INSERTION_GUIDE_COLOR
            );
        }
        if (request.insertionTarget != INSERTION_NONE) {
            boolean before = request.insertionTarget == INSERTION_BEFORE;
            int left = before ? 0 : ItemIconPreview.NATIVE_SLOT_SIZE - 3;
            int right = before ? 3 : ItemIconPreview.NATIVE_SLOT_SIZE;
            int capLeft = before ? 0 : left - 2;
            int capRight = before ? 5 : ItemIconPreview.NATIVE_SLOT_SIZE;
            graphics.fill(left, 0, right, ItemIconPreview.NATIVE_SLOT_SIZE, INSERTION_TARGET_COLOR);
            graphics.fill(
                capLeft,
                0,
                capRight,
                2,
                INSERTION_TARGET_COLOR
            );
            graphics.fill(
                capLeft,
                ItemIconPreview.NATIVE_SLOT_SIZE - 2,
                capRight,
                ItemIconPreview.NATIVE_SLOT_SIZE,
                INSERTION_TARGET_COLOR
            );
            if (before) {
                // The extractor rasterizes a quad beginning at the left clip edge
                // one pixel inward. Cross the edge for only the two corner cells;
                // extending the complete stem would make the left marker wider.
                graphics.fill(-1, 0, 0, 1, INSERTION_TARGET_COLOR);
                graphics.fill(
                    -1,
                    ItemIconPreview.NATIVE_SLOT_SIZE - 1,
                    0,
                    ItemIconPreview.NATIVE_SLOT_SIZE,
                    INSERTION_TARGET_COLOR
                );
            }
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    record SlotInteraction(
        boolean clicked,
        boolean doubleClicked,
        boolean hovered,
        boolean focused,
        boolean keyboardActivated,
        boolean deletePressed
    ) {
    }

    private record Request(
        NativePreviewGeometry.Capture geometry,
        ItemStack stack,
        boolean selected,
        boolean overflow,
        boolean dropTarget,
        boolean dragSource,
        boolean insertionGuides,
        int insertionTarget
    ) {
    }
}
