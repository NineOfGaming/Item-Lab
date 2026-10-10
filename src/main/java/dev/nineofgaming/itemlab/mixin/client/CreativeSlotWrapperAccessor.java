package dev.nineofgaming.itemlab.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the real player slot behind the creative inventory's display wrapper.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface CreativeSlotWrapperAccessor {
    @Accessor("target")
    Slot itemlab$getTargetSlot();
}
