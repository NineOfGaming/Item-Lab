package dev.nineofgaming.itemlab.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the nested recipe-book search field for global shortcut suppression.
 */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("searchBox")
    EditBox itemlab$getSearchBox();
}
