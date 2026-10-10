package dev.nineofgaming.itemlab.mixin.client;

import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the real target of direct connections and server transfers.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    // startConnecting can reject a duplicate request. Its tail runs even then;
    // connect is reached only for an accepted attempt, after disconnect clears the old target.
    @Inject(method = "connect", at = @At("HEAD"))
    private void itemlab$recordConnectionTarget(
        Minecraft minecraft,
        ServerAddress hostAndPort,
        ServerData server,
        TransferState transferState,
        CallbackInfo callbackInfo
    ) {
        DiamondFireContext.setConnectionTarget(hostAndPort);
    }
}
