package dev.nineofgaming.itemlab.mixin.client;

import dev.nineofgaming.itemlab.ItemLab;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps focus-loss pausing from replacing Item Lab with Minecraft's pause screen.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void itemlab$keepEditorOpenOnPauseRequest(boolean suppressPauseMenuIfWeReallyArePausing, CallbackInfo callbackInfo) {
        if (ItemLab.isEditorOpen()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void itemlab$clearConnectionTarget(
        Screen screen,
        boolean keepResourcePacks,
        boolean stopSound,
        CallbackInfo callbackInfo
    ) {
        DiamondFireContext.clearConnectionTarget();
    }
}
