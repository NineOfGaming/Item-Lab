package dev.nineofgaming.itemlab.editor.ui.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Native banner-pattern or shield-item preview, selected from the edited item type.
 */
public final class BannerContentPreview {
    private static final float CANVAS_HEIGHT = 300.0f;
    private static final int ITEM_SIZE = 16;

    private final NativePreviewCanvas canvas = new NativePreviewCanvas();
    private ItemStack stack = ItemStack.EMPTY;
    private BannerFlagModel bannerModel;

    public void beginImGuiFrame() {
        canvas.beginFrame();
    }

    public void draw(ItemStack previewStack) {
        if (canvas.draw("banner_content_preview", CANVAS_HEIGHT)) {
            stack = previewStack == null ? ItemStack.EMPTY : previewStack.copyWithCount(1);
        }
    }

    public void endImGuiFrame() {
        if (!canvas.requested()) stack = ItemStack.EMPTY;
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        canvas.beginFrame();
    }

    public boolean extract(GuiGraphicsExtractor graphics) {
        if (stack.isEmpty() || !canvas.requested()) return false;
        NativePreviewCanvas.Bounds bounds = canvas.resolve(graphics);
        if (!bounds.valid()) return false;

        graphics.enableScissor(bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom());
        if (stack.getItem() instanceof ShieldItem) {
            int size = Math.min(bounds.width(), bounds.height());
            float scale = size * 0.72f / ITEM_SIZE;
            float left = bounds.left() + bounds.width() / 2.0f - ITEM_SIZE * scale / 2.0f;
            float top = bounds.top() + bounds.height() / 2.0f - ITEM_SIZE * scale / 2.0f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(left, top);
            graphics.pose().scale(scale, scale);
            graphics.item(stack, 0, 0);
            graphics.pose().popMatrix();
        } else {
            Minecraft client = Minecraft.getInstance();
            if (bannerModel == null) {
                bannerModel = new BannerFlagModel(client.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
                bannerModel.setupAnim(0.0f);
            }
            DyeColor baseColor = stack.get(DataComponents.BASE_COLOR);
            if (baseColor == null && stack.getItem() instanceof BannerItem bannerItem) {
                baseColor = bannerItem.getColor();
            }
            if (baseColor == null) baseColor = DyeColor.WHITE;
            BannerPatternLayers patterns = stack.getOrDefault(
                DataComponents.BANNER_PATTERNS,
                BannerPatternLayers.EMPTY
            );
            int width = Math.min(bounds.width(), Math.round(bounds.height() * 0.62f));
            int left = bounds.left() + (bounds.width() - width) / 2;
            graphics.bannerPattern(
                bannerModel,
                baseColor,
                patterns,
                left,
                bounds.top() + 8,
                left + width,
                bounds.bottom() - 8
            );
        }
        graphics.disableScissor();
        return true;
    }
}
