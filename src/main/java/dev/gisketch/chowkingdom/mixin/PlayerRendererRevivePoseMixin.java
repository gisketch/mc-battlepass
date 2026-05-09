package dev.gisketch.chowkingdom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gisketch.chowkingdom.compat.ReviveRenderCompat;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerRendererRevivePoseMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
    private void chowkingdom$lieDownWhenIncapacitated(AbstractClientPlayer player, PoseStack poseStack, float bob, float bodyYaw, float partialTick, float scale, CallbackInfo callback) {
        if (!ReviveRenderCompat.isIncapacitated(player)) return;
        if (!player.isVisuallySwimming()) poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.translate(0.0F, -0.15F, 0.28F);
    }
}
