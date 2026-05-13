package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.SpellEngineClassLockBridge;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.spell_engine.spellbinding.SpellBindingScreenHandler", remap = false)
public class SpellBindingScreenHandlerClassLockMixin {
    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$blockWrongClassSpellBinding(Player player, int button, CallbackInfoReturnable<Boolean> callback) {
        if (SpellEngineClassLockBridge.blockBindingButton(this, player, button)) {
            callback.setReturnValue(false);
        }
    }
}
