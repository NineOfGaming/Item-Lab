package dev.nineofgaming.itemlab.editor.ui.preview;

import dev.nineofgaming.itemlab.mixin.client.MapItemSavedDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Set;

/**
 * Live map pixels and decorations rendered by Minecraft's map renderer.
 */
public final class MapContentPreview {
    private static final float CANVAS_HEIGHT = 300.0f;
    private static final int MAP_SIZE = 128;

    private final NativePreviewCanvas canvas = new NativePreviewCanvas();
    private ItemStack stack = ItemStack.EMPTY;
    private Set<String> replacedDecorationNames = Set.of();

    public void beginImGuiFrame() {
        canvas.beginFrame();
    }

    public void draw(ItemStack previewStack, Set<String> originalDecorationNames) {
        if (canvas.draw("map_content_preview", CANVAS_HEIGHT)) {
            stack = previewStack == null ? ItemStack.EMPTY : previewStack.copyWithCount(1);
            replacedDecorationNames = originalDecorationNames == null
                ? Set.of()
                : Set.copyOf(originalDecorationNames);
        }
    }

    public void endImGuiFrame() {
        if (!canvas.requested()) stack = ItemStack.EMPTY;
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        replacedDecorationNames = Set.of();
        canvas.beginFrame();
    }

    public boolean hasMapData(Minecraft client, ItemStack candidate) {
        if (client.level == null || candidate == null) return false;
        MapId id = candidate.get(DataComponents.MAP_ID);
        return id != null && MapItem.getSavedData(id, client.level) != null;
    }

    public boolean extract(GuiGraphicsExtractor graphics) {
        if (stack.isEmpty() || !canvas.requested()) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;
        MapId id = stack.get(DataComponents.MAP_ID);
        if (id == null) return false;
        MapItemSavedData data = MapItem.getSavedData(id, client.level);
        if (data == null) return false;

        NativePreviewCanvas.Bounds bounds = canvas.resolve(graphics);
        if (!bounds.valid()) return false;
        int size = Math.min(bounds.width(), bounds.height());
        int left = bounds.left() + (bounds.width() - size) / 2;
        int top = bounds.top() + (bounds.height() - size) / 2;
        float scale = size / (float) MAP_SIZE;

        MapItemSavedData previewData = previewData(client, data, stack, replacedDecorationNames);
        MapRenderState state = new MapRenderState();
        client.getMapRenderer().extractRenderState(id, previewData, state);
        graphics.enableScissor(bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, top);
        graphics.pose().scale(scale, scale);
        graphics.map(state);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        return true;
    }

    private static MapItemSavedData previewData(
        Minecraft client,
        MapItemSavedData source,
        ItemStack previewStack,
        Set<String> replacedDecorationNames
    ) {
        MapItemSavedData copy = MapItemSavedData.createForClient(source.scale, source.locked, source.dimension);
        copy.colors = source.colors.clone();
        MapItemSavedDataAccessor sourceAccess = (MapItemSavedDataAccessor) source;
        MapItemSavedDataAccessor copyAccess = (MapItemSavedDataAccessor) copy;
        copyAccess.itemlab$getDecorationsById().putAll(sourceAccess.itemlab$getDecorationsById());
        replacedDecorationNames.forEach(copyAccess.itemlab$getDecorationsById()::remove);

        MapDecorations pending = previewStack.get(DataComponents.MAP_DECORATIONS);
        if (pending != null && client.level != null) {
            pending.decorations().forEach((name, entry) -> {
                copyAccess.itemlab$getDecorationsById().remove(name);
                copyAccess.itemlab$addDecoration(
                    entry.type(),
                    client.level,
                    name,
                    entry.x(),
                    entry.z(),
                    entry.rotation(),
                    null
                );
            });
        }
        return copy;
    }
}
