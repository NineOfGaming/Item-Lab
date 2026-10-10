package dev.nineofgaming.itemlab.editor.ui.preview;

import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Places Minecraft's native item tooltip over a reserved canvas inside ImGui.
 */
public final class ItemTooltipPreview {
    private static final int CANVAS_PADDING = 7;
    private static final int MIN_CANVAS_HEIGHT = 32;

    private ItemStack stack = ItemStack.EMPTY;
    private NativePreviewGeometry.Capture geometry;
    private boolean requestedThisFrame;

    public void beginImGuiFrame() {
        requestedThisFrame = false;
    }

    public void draw(ItemStack previewStack, String id) {
        boolean hasPreview = previewStack != null && !previewStack.isEmpty();
        Minecraft client = Minecraft.getInstance();
        float guiToImGuiScale = NativePreviewGeometry.imGuiScale(client);

        if (EditorUi.beginCollapsibleCard(
            "native_item_tooltip_preview_" + id,
            tr("preview.tooltip"),
            ImGuiTableFlags.SizingStretchProp
        )) {
            TooltipSize tooltipSize = hasPreview
                ? measureTooltip(client, previewStack)
                : TooltipSize.EMPTY;
            float viewportWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
            float canvasWidth = Math.max(
                viewportWidth,
                (tooltipSize.width() + CANVAS_PADDING * 2.0f) * guiToImGuiScale
            );
            float canvasHeight = Math.max(
                MIN_CANVAS_HEIGHT,
                tooltipSize.height() + CANVAS_PADDING * 2.0f
            ) * guiToImGuiScale;
            boolean horizontallyScrollable = canvasWidth > viewportWidth + 0.5f;
            float viewportHeight = canvasHeight + (horizontallyScrollable
                ? ImGui.getStyle().getScrollbarSize()
                : 0.0f);
            int viewportFlags = horizontallyScrollable
                ? ImGuiWindowFlags.HorizontalScrollbar
                : ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
            if (EditorUi.beginScrollableChild(
                "native_item_tooltip_canvas_" + id,
                viewportWidth,
                viewportHeight,
                ImGuiChildFlags.None,
                viewportFlags
            )) {
                ImGui.dummy(canvasWidth, canvasHeight);
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

                if (hasPreview && ImGui.isItemVisible()) {
                    // Canvas coordinates may move outside the visible child while it
                    // scrolls. The child's clip rect keeps the native tooltip inside it.
                    geometry = currentGeometry;
                    stack = previewStack.copy();
                    requestedThisFrame = true;
                }
            }
            ImGui.endChild();
            EditorUi.endCard();
        }
    }

    public void endImGuiFrame() {
        if (!requestedThisFrame) {
            stack = ItemStack.EMPTY;
        }
    }

    public void clear() {
        stack = ItemStack.EMPTY;
        requestedThisFrame = false;
    }

    public boolean extractNativeTooltip(GuiGraphicsExtractor graphics) {
        if (stack.isEmpty()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        NativePreviewGeometry.Bounds bounds = geometry.resolve(graphics);
        if (!bounds.valid()) {
            return false;
        }
        int canvasLeft = bounds.left();
        int canvasTop = bounds.top();
        int canvasRight = bounds.right();
        int canvasBottom = bounds.bottom();
        List<FormattedCharSequence> lines = tooltipLines(client, stack);
        ClientTooltipPositioner positioner = (screenWidth, screenHeight, ignoredX, ignoredY, tooltipWidth, tooltipHeight) -> {
            int canvasWidth = Math.max(1, canvasRight - canvasLeft);
            int canvasHeight = Math.max(1, canvasBottom - canvasTop);
            int x = canvasLeft + (canvasWidth - tooltipWidth) / 2;
            int y = canvasTop + (canvasHeight - tooltipHeight) / 2;

            int canvasMinX = canvasLeft + CANVAS_PADDING;
            int canvasMaxX = canvasRight - tooltipWidth - CANVAS_PADDING;
            if (canvasMaxX >= canvasMinX) {
                x = Math.clamp(x, canvasMinX, canvasMaxX);
            }
            int canvasMinY = canvasTop + CANVAS_PADDING;
            int canvasMaxY = canvasBottom - tooltipHeight - CANVAS_PADDING;
            if (canvasMaxY >= canvasMinY) {
                y = Math.clamp(y, canvasMinY, canvasMaxY);
            }
            return new Vector2i(x, y);
        };

        graphics.enableScissor(bounds.clipLeft(), bounds.clipTop(), bounds.clipRight(), bounds.clipBottom());
        graphics.setTooltipForNextFrame(
            client.font,
            lines,
            stack.getTooltipImage(),
            positioner,
            canvasLeft,
            canvasTop,
            false,
            stack.get(DataComponents.TOOLTIP_STYLE)
        );
        graphics.extractDeferredElements(canvasLeft, canvasTop, 0.0f);
        graphics.disableScissor();
        return true;
    }

    private static TooltipSize measureTooltip(Minecraft client, ItemStack stack) {
        List<ClientTooltipComponent> components = new ArrayList<>();
        tooltipLines(client, stack).stream()
            .map(ClientTooltipComponent::create)
            .forEach(components::add);
        stack.getTooltipImage().ifPresent(image -> components.add(
            Math.min(1, components.size()),
            ClientTooltipComponent.create(image)
        ));

        int width = 0;
        int height = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            width = Math.max(width, component.getWidth(client.font));
            height += component.getHeight(client.font);
        }
        return new TooltipSize(width, Math.max(1, height));
    }

    private static List<FormattedCharSequence> tooltipLines(Minecraft client, ItemStack stack) {
        return Screen.getTooltipFromItem(client, stack).stream()
            .map(Component::getVisualOrderText)
            .toList();
    }

    private record TooltipSize(int width, int height) {
        private static final TooltipSize EMPTY = new TooltipSize(0, 0);
    }
}
