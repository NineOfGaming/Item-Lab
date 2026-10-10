package dev.nineofgaming.itemlab.editor.ui.preview;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One picture-in-picture scene containing both the shelf block and its displayed item.
 */
public record ShelfPreviewRenderState(
    BlockModelRenderState shelf,
    ItemStackRenderState item,
    Vector3f translation,
    Quaternionf rotation,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public ShelfPreviewRenderState(
        BlockModelRenderState shelf,
        ItemStackRenderState item,
        Vector3f translation,
        Quaternionf rotation,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        ScreenRectangle scissorArea
    ) {
        this(
            shelf,
            item,
            translation,
            rotation,
            x0,
            y0,
            x1,
            y1,
            scale,
            scissorArea,
            PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea)
        );
    }
}
