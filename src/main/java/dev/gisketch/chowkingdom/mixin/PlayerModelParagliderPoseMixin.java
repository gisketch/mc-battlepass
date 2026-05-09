package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.ParagliderStaminaBridge;
import dev.gisketch.chowkingdom.compat.ReviveRenderCompat;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelParagliderPoseMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void chowkingdom$restorePlayerParaglidingPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (!(entity instanceof Player player)) return;
        if (ReviveRenderCompat.isIncapacitated(player)) return;
        if (!ParagliderStaminaBridge.INSTANCE.shouldRenderParaglidingPose(player)) return;

        HumanoidModel<?> humanoidModel = (HumanoidModel<?>)(Object)this;
        PlayerModel<?> playerModel = (PlayerModel<?>)(Object)this;
        humanoidModel.leftArm.xRot = PARAGLIDING_ARM_X_ROT;
        humanoidModel.leftArm.yRot = 0.0F;
        humanoidModel.leftArm.zRot = 0.0F;
        humanoidModel.rightArm.xRot = PARAGLIDING_ARM_X_ROT;
        humanoidModel.rightArm.yRot = 0.0F;
        humanoidModel.rightArm.zRot = 0.0F;
        humanoidModel.leftLeg.xRot = 0.0F;
        humanoidModel.leftLeg.yRot = 0.0F;
        humanoidModel.leftLeg.zRot = 0.0F;
        humanoidModel.rightLeg.xRot = 0.0F;
        humanoidModel.rightLeg.yRot = 0.0F;
        humanoidModel.rightLeg.zRot = 0.0F;
        AnimationUtils.bobModelPart(humanoidModel.rightArm, ageInTicks, -1.0F);
        AnimationUtils.bobModelPart(humanoidModel.leftArm, ageInTicks, 1.0F);
        playerModel.leftSleeve.copyFrom(humanoidModel.leftArm);
        playerModel.rightSleeve.copyFrom(humanoidModel.rightArm);
        playerModel.leftPants.copyFrom(humanoidModel.leftLeg);
        playerModel.rightPants.copyFrom(humanoidModel.rightLeg);
    }

    private static final float PARAGLIDING_ARM_X_ROT = 3.3831854F;
}
