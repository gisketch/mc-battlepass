package dev.gisketch.chowkingdom.mixin;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.unusual.block_factorys_bosses.event.ClientEvents", remap = false)
public class BlockFactoryRollHudMixin {
    @Inject(method = "renderGUI", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void chowkingdom$hideRollGui(RenderGuiEvent.Pre event, CallbackInfo callback) {
        callback.cancel();
    }
}
