package dev.nineofgaming.itemlab.mixin.client;

import dev.nineofgaming.itemlab.ItemLab;
import foundry.imgui.impl.ImGuiHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("UnstableApiUsage")
@Mixin(value = ImGuiHandler.class, remap = false)
public abstract class ImGuiHandlerMixin {
    @Inject(
        method = "endFrame",
        at = @At(
            value = "INVOKE",
            target = "Limgui/ImGui;getDrawData()Limgui/ImDrawData;"
        ),
        remap = false
    )
    private void itemlab$renderNativePreviewsForComposite(CallbackInfo callbackInfo) {
        ItemLab.renderNativePreviewsForImGui();
    }
}
