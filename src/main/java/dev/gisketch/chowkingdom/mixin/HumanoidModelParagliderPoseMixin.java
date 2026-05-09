package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.ParagliderStaminaBridge;
import dev.gisketch.chowkingdom.compat.ReviveRenderCompat;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelParagliderPoseMixin<T extends LivingEntity> {
    @Shadow
    public ModelPart head;

    @Shadow
    public ModelPart body;

    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    @Shadow
    public ModelPart rightLeg;

    @Shadow
    public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void chowkingdom$restoreParaglidingPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (!(entity instanceof Player player)) return;
        if (ReviveRenderCompat.isIncapacitated(player)) {
            chowkingdom$applyIncapacitatedLimpPose();
            return;
        }
        if (!ParagliderStaminaBridge.INSTANCE.shouldRenderParaglidingPose(player)) return;

        leftArm.xRot = PARAGLIDING_ARM_X_ROT;
        leftArm.yRot = 0.0F;
        leftArm.zRot = 0.0F;
        rightArm.xRot = PARAGLIDING_ARM_X_ROT;
        rightArm.yRot = 0.0F;
        rightArm.zRot = 0.0F;
        leftLeg.xRot = 0.0F;
        leftLeg.yRot = 0.0F;
        leftLeg.zRot = 0.0F;
        rightLeg.xRot = 0.0F;
        rightLeg.yRot = 0.0F;
        rightLeg.zRot = 0.0F;
        AnimationUtils.bobModelPart(rightArm, ageInTicks, -1.0F);
        AnimationUtils.bobModelPart(leftArm, ageInTicks, 1.0F);
    }

    private void chowkingdom$applyIncapacitatedLimpPose() {
        head.xRot = 0.25F;
        head.yRot = 0.0F;
        head.zRot = 0.0F;
        body.xRot = 0.0F;
        body.yRot = 0.0F;
        body.zRot = 0.0F;
        leftArm.xRot = 0.35F;
        leftArm.yRot = 0.0F;
        leftArm.zRot = 0.22F;
        rightArm.xRot = 0.35F;
        rightArm.yRot = 0.0F;
        rightArm.zRot = -0.22F;
        leftLeg.xRot = 0.12F;
        leftLeg.yRot = 0.04F;
        leftLeg.zRot = 0.0F;
        rightLeg.xRot = 0.12F;
        rightLeg.yRot = -0.04F;
        rightLeg.zRot = 0.0F;
    }

    private static final float PARAGLIDING_ARM_X_ROT = 3.3831854F;
}