package dev.nineofgaming.itemlab.mixin.client;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Selects the page of the embedded read-only book preview without creating screen widgets.
 */
@Mixin(BookViewScreen.class)
public interface BookViewScreenAccessor {
    @Accessor("currentPage")
    void itemlab$setCurrentPage(int page);
}
