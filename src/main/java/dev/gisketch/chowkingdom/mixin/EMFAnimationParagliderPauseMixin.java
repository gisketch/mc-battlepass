package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.ParagliderStaminaBridge;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.EMFAnimation", remap = false)
public abstract class EMFAnimationParagliderPauseMixin {
    private static Method getEmfEntityMethod;
    private static boolean lookupFailed;

    @Inject(method = "calculateAndSet", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$pauseFreshAnimationsDuringParagliding(CallbackInfo callback) {
        Player player = currentPlayer();
        if (player != null && ParagliderStaminaBridge.INSTANCE.shouldRenderParaglidingPose(player)) {
            callback.cancel();
        }
    }

    private static Player currentPlayer() {
        Object entity = currentEmfEntity();
        return entity instanceof Player player ? player : null;
    }

    private static Object currentEmfEntity() {
        if (lookupFailed) return null;
        try {
            if (getEmfEntityMethod == null) {
                Class<?> contextClass = Class.forName("traben.entity_model_features.models.animation.EMFAnimationEntityContext");
                getEmfEntityMethod = contextClass.getMethod("getEMFEntity");
            }
            return getEmfEntityMethod.invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            lookupFailed = true;
            return null;
        }
    }
}
