package dev.gisketch.chowkingdom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gisketch.chowkingdom.npc.NpcClient;
import dev.gisketch.chowkingdom.profiles.NicknameConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void chowkingdom$renderNpcBalloon(LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callback) {
        NpcClient.renderBalloon(entity, poseStack, bufferSource, Minecraft.getInstance().font, packedLight);
    }
}