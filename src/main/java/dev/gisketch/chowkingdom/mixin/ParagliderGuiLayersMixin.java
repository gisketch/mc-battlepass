package dev.gisketch.chowkingdom.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "tictim.paraglider.client.ParagliderGuiLayers", remap = false)
public class ParagliderGuiLayersMixin {
    @Inject(method = "renderStaminaWheel", at = @At("HEAD"), cancellable = true, require = 0)
    private static void chowkingdom$hideNativeStaminaWheel(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        callback.cancel();
    }
}