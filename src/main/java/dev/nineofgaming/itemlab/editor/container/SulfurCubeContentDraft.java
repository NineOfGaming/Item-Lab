package dev.nineofgaming.itemlab.editor.container;

import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
//? if >=26.2 {
import net.minecraft.world.item.component.SulfurCubeContent;
//?}

/**
 * Lossless editable state for the 26.2-only {@code minecraft:sulfur_cube_content} component.
 */
public final class SulfurCubeContentDraft {
    private boolean present;
    private ItemStack stack = ItemStack.EMPTY;

    public static boolean supported() {
        //? if >=26.2 {
        return true;
        //?} else {
        /*return false;
         *///?}
    }

    public static DataComponentType<?> componentType() {
        //? if >=26.2 {
        return DataComponents.SULFUR_CUBE_CONTENT;
        //?} else {
        /*return null;
         *///?}
    }

    public static SulfurCubeContentDraft fromStack(ItemStack owner) {
        SulfurCubeContentDraft draft = new SulfurCubeContentDraft();
        //? if >=26.2 {
        SulfurCubeContent content = owner.get(DataComponents.SULFUR_CUBE_CONTENT);
        draft.present = content != null;
        if (content != null) draft.stack = ItemStackCodecs.stackFromTemplate(content.absorbedBlockItemStack());
        //?}
        return draft;
    }

    public boolean present() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public ItemStack stack() {
        return stack.copy();
    }

    public void setStack(ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public void applyTo(ItemStack owner) {
        //? if >=26.2 {
        if (!present) {
            owner.remove(DataComponents.SULFUR_CUBE_CONTENT);
            return;
        }
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Sulfur cube content requires a non-empty absorbed block item");
        }
        owner.set(DataComponents.SULFUR_CUBE_CONTENT, SulfurCubeContent.ofNonEmpty(stack));
        //?} else {
        /*if (present) {
            throw new IllegalStateException("Sulfur cube content is unavailable before Minecraft 26.2");
        }
        *///?}
    }

    public boolean matchesStack(ItemStack owner) {
        SulfurCubeContentDraft other = fromStack(owner);
        return present == other.present && ItemStack.matches(stack, other.stack);
    }
}
