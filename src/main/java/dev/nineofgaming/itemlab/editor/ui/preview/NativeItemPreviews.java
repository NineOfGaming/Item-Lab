package dev.nineofgaming.itemlab.editor.ui.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Coordinates native item previews so Minecraft renders them into one ImGui composite layer.
 */
public final class NativeItemPreviews {
    private final NativePreviewCompositor compositor = new NativePreviewCompositor();
    private final ItemIconPreview icon = new ItemIconPreview();
    private final ItemSlotPreviews slots = new ItemSlotPreviews();
    private final DraggedItemPreview draggedItem = new DraggedItemPreview();
    private final ItemTooltipPreview tooltip = new ItemTooltipPreview();
    private final EquipmentMannequinPreview equipment = new EquipmentMannequinPreview();
    private final PlayerHeadPreview playerHead = new PlayerHeadPreview();
    private final ItemModelPreview itemModel = new ItemModelPreview();
    private final BookContentPreview book = new BookContentPreview();
    private final MapContentPreview map = new MapContentPreview();
    private final BannerContentPreview banner = new BannerContentPreview();

    public void beginImGuiFrame() {
        compositor.beginFrame(Minecraft.getInstance());
        icon.beginImGuiFrame();
        slots.beginImGuiFrame();
        draggedItem.beginImGuiFrame();
        tooltip.beginImGuiFrame();
        equipment.beginImGuiFrame();
        playerHead.beginImGuiFrame();
        itemModel.beginImGuiFrame();
        book.beginImGuiFrame();
        map.beginImGuiFrame();
        banner.beginImGuiFrame();
    }

    public void endImGuiFrame() {
        icon.endImGuiFrame();
        slots.endImGuiFrame();
        draggedItem.endImGuiFrame();
        tooltip.endImGuiFrame();
        equipment.endImGuiFrame();
        playerHead.endImGuiFrame();
        itemModel.endImGuiFrame();
        book.endImGuiFrame();
        map.endImGuiFrame();
        banner.endImGuiFrame();
    }

    public void drawCompositeLayer() {
        compositor.drawCompositeLayer();
    }

    public void clear() {
        icon.clear();
        slots.clear();
        draggedItem.clear();
        tooltip.clear();
        equipment.clear();
        playerHead.clear();
        itemModel.clear();
        book.clear();
        map.clear();
        banner.clear();
    }

    public float itemIconSize(Minecraft client) {
        return icon.imGuiSize(client);
    }

    public void drawItemIcon(Minecraft client, ItemStack stack) {
        icon.draw(client, stack);
    }

    public void setItemIconStack(ItemStack stack) {
        icon.setStack(stack);
    }

    public float itemSlotSize(Minecraft client) {
        return slots.imGuiSize(client);
    }

    public ItemSlotInteraction drawItemSlot(
        Minecraft client,
        String id,
        ItemStack stack,
        boolean selected,
        boolean overflow
    ) {
        ItemSlotPreviews.SlotInteraction interaction = slots.draw(client, id, stack, selected, overflow);
        return new ItemSlotInteraction(
            interaction.clicked(),
            interaction.doubleClicked(),
            interaction.hovered(),
            interaction.focused(),
            interaction.keyboardActivated(),
            interaction.deletePressed()
        );
    }

    public void markLastDrawnItemSlotAsDropTarget() {
        slots.markLastDrawnSlotAsDropTarget();
    }

    public void markLastDrawnItemSlotAsDragSource() {
        slots.markLastDrawnSlotAsDragSource();
    }

    public void markLastDrawnItemSlotAsInsertionGuide() {
        slots.markLastDrawnSlotAsInsertionGuide();
    }

    public void markLastDrawnItemSlotAsInsertionTarget(boolean after) {
        slots.markLastDrawnSlotAsInsertionTarget(after);
    }

    public void drawDraggedItem(Minecraft client, ItemStack stack) {
        draggedItem.draw(client, stack);
    }

    public void drawTooltip(ItemStack stack, String id) {
        tooltip.draw(stack, id);
        icon.setStack(stack);
    }

    public void drawEquipment(Minecraft client, ItemStack stack, EquipmentPreviewBase base) {
        equipment.draw(client, stack, base);
        icon.setStack(stack);
    }

    public void drawPlayerHead(Minecraft client, ItemStack stack, PlayerSkinPreviewMode mode) {
        playerHead.draw(client, stack, mode);
        icon.setStack(stack);
    }

    public void drawItemModel(Minecraft client, ItemStack stack, ItemModelPreviewMode mode) {
        itemModel.draw(client, stack, mode);
        icon.setStack(stack);
    }

    public void drawBook(ItemStack stack, int page) {
        book.draw(stack, page);
        icon.setStack(stack);
    }

    public void drawMap(ItemStack stack, Set<String> originalDecorationNames) {
        map.draw(stack, originalDecorationNames);
        icon.setStack(stack);
    }

    public boolean hasMapData(Minecraft client, ItemStack stack) {
        return map.hasMapData(client, stack);
    }

    public void drawBanner(ItemStack stack) {
        banner.draw(stack);
        icon.setStack(stack);
    }

    public void renderForComposite() {
        Minecraft client = Minecraft.getInstance();
        NativeGuiOverlayRenderer renderer = (NativeGuiOverlayRenderer) client.gameRenderer;
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(
            client,
            renderer.itemlab$getGuiRenderState(),
            0,
            0
        );
        boolean hasIcon = icon.extract(graphics);
        boolean hasSlots = slots.extractSlots(graphics);
        boolean hasEquipment = equipment.extract(graphics);
        boolean hasPlayerHead = playerHead.extract(graphics);
        boolean hasItemModel = itemModel.extract(graphics);
        boolean hasBook = book.extract(graphics);
        boolean hasMap = map.extract(graphics);
        boolean hasBanner = banner.extract(graphics);
        boolean hasDraggedItem = draggedItem.extract(graphics);
        boolean hasTooltip = tooltip.extractNativeTooltip(graphics);
        boolean hasHoveredTooltip = !hasDraggedItem && slots.extractHoveredTooltip(graphics);
        compositor.renderIntoTarget(() -> {
            if (hasIcon || hasSlots || hasDraggedItem || hasTooltip || hasHoveredTooltip || hasEquipment
                || hasPlayerHead || hasItemModel || hasBook || hasMap || hasBanner) {
                renderer.itemlab$renderGuiOverlay();
            }
        });
    }

    public record ItemSlotInteraction(
        boolean clicked,
        boolean doubleClicked,
        boolean hovered,
        boolean focused,
        boolean keyboardActivated,
        boolean deletePressed
    ) {
    }
}
