package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.client.ParagliderStaminaHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiStaminaAirOffsetMixin {
    @Shadow
    private int rightHeight;

    @Inject(method = "renderAirLevel", at = @At("HEAD"))
    private void chowkingdom$reserveStaminaHudSpace(GuiGraphics guiGraphics, CallbackInfo callback) {
        if (ParagliderStaminaHud.shouldOffsetAirBubbles()) {
            rightHeight += 12;
        }
    }
}