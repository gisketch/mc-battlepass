package dev.gisketch.chowkingdom.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.unusual.block_factorys_bosses.network.DodgeRollMessage", remap = false)
public class BlockFactoryDodgeRollMessageMixin {
    @Inject(method = "pressAction", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void chowkingdom$disableDodgeRoll(Player player, int eventType, float leftImpulse, float forwardImpulse, CallbackInfo callback) {
        callback.cancel();
    }
}
