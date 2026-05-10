package dev.gisketch.chowkingdom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gisketch.chowkingdom.compat.PunchyParryClientBridge;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererParryMixin {
    @Inject(method = "renderFirstPerson", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void chowkingdom$skipPunchyFirstPersonDuringParry(ItemInHandRenderer renderer, LocalPlayer player, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callback) {
        if (PunchyParryClientBridge.shouldDisablePunchyFirstPerson()) {
            callback.cancel();
        }
    }
}
