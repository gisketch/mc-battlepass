package dev.gisketch.chowkingdom.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.sweenus.simplytooltips.client.render.TooltipGifRecorder", remap = false)
public class SimplyTooltipsGifRecorderMixin {
    @Inject(method = "chat", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void chowkingdom$suppressCaptureChat(Minecraft minecraft, Component message, CallbackInfo callback) {
        callback.cancel();
    }
}
