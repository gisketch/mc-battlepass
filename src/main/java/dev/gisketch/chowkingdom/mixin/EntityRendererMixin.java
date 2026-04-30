package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.profiles.NicknameConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void chowkingdom$showOwnNameTag(T entity, CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (NicknameConfig.showOwnNameTag() && minecraft.player != null && entity == minecraft.player && !minecraft.options.hideGui) {
            callback.setReturnValue(true);
        }
    }
}
