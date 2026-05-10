package dev.gisketch.chowkingdom.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mod.azure.azurelibarmor.common.animation.AzAnimatorAccessor", remap = false)
public interface AzureLibArmorAnimatorAccessorMixin {
    @Inject(method = "getOrNull", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void chowkingdom$nullWhenAnimatorAccessorMissing(Object target, CallbackInfoReturnable<Object> callback) {
        if (!chowkingdom$isAnimatorAccessor(target)) {
            callback.setReturnValue(null);
        }
    }

    private static boolean chowkingdom$isAnimatorAccessor(Object target) {
        if (target == null) return false;
        try {
            return Class.forName("mod.azure.azurelibarmor.common.animation.AzAnimatorAccessor").isInstance(target);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }
}
