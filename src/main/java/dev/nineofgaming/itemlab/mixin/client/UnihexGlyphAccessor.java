package dev.nineofgaming.itemlab.mixin.client;

import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.font.providers.UnihexProvider$Glyph")
public interface UnihexGlyphAccessor {
    @Accessor("contents")
    UnihexProvider.LineData itemlab$getContents();

    @Accessor("left")
    int itemlab$getLeft();

    @Accessor("right")
    int itemlab$getRight();
}
