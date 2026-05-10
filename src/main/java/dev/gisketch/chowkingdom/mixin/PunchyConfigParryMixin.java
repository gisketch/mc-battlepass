package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.PunchyParryClientBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "punchy.config.PunchyConfig", remap = false)
public abstract class PunchyConfigParryMixin {
    @Inject(method = "isModEnabled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void chowkingdom$disablePunchyDuringParry(CallbackInfoReturnable<Boolean> callback) {
        if (PunchyParryClientBridge.shouldDisablePunchyFirstPerson()) {
            callback.setReturnValue(false);
        }
    }
}
