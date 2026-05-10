package dev.gisketch.chowkingdom.mixin;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.unusual.block_factorys_bosses.attachment.entity.RollAttachment", remap = false)
public class BlockFactoryRollAttachmentMixin {
    @Shadow(remap = false)
    protected int roll;

    @Inject(method = "startRoll", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$disableStartRoll(Player player, float leftImpulse, float forwardImpulse, CallbackInfoReturnable<Boolean> callback) {
        this.roll = 0;
        callback.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$disableRollTick(Player player, CallbackInfo callback) {
        this.roll = 0;
        if (player.getForcedPose() == Pose.CROUCHING) {
            player.setForcedPose(null);
        }
        callback.cancel();
    }

    @Inject(method = "rollCount", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$hideRollCharges(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(0);
    }

    @Inject(method = "isRolling", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$neverRolling(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }

    @Inject(method = "isInvulnerable", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$noRollInvulnerability(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }
}
