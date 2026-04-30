package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.discord.DiscordScreenshotClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void chowkingdom$sendDiscordScreenshot(long window, int key, int scanCode, int action, int modifiers, CallbackInfo callback) {
        if (DiscordScreenshotClient.handleKeyPress(window, key, scanCode, action, modifiers)) {
            callback.cancel();
        }
    }
}