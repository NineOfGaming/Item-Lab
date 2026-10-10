package dev.nineofgaming.itemlab.editor.ui.preview;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Raw item-model scene used by screen-space display contexts.
 */
public record DirectItemPreviewRenderState(
    ItemStackRenderState item,
    Vector3f viewportTranslation,
    Quaternionf rotation,
    Vector3f itemTranslation,
    ItemStack stack,
    ItemDisplayContext displayContext,
    boolean perspective,
    float fieldOfView,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public DirectItemPreviewRenderState(
        ItemStackRenderState item,
        Vector3f viewportTranslation,
        Quaternionf rotation,
        Vector3f itemTranslation,
        ItemStack stack,
        ItemDisplayContext displayContext,
        boolean perspective,
        float fieldOfView,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        ScreenRectangle scissorArea
    ) {
        this(
            item,
            viewportTranslation,
            rotation,
            itemTranslation,
            stack,
            displayContext,
            perspective,
            fieldOfView,
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
