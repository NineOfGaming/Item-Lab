package dev.nineofgaming.itemlab.mixin.client;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(FontSet.class)
public interface FontSetAccessor {
    @Accessor("activeProviders")
    List<GlyphProvider> itemlab$getActiveProviders();
}
