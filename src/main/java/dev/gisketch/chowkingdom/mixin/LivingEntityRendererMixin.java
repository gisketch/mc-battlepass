package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.profiles.NicknameConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void chowkingdom$showOwnNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (NicknameConfig.showOwnNameTag() && minecraft.player != null && entity == minecraft.player && !minecraft.options.hideGui) {
            callback.setReturnValue(true);
        }
    }
}