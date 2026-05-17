package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.XaeroNpcMapCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementReader", remap = false)
public abstract class XaeroMinimapNpcReaderMixin {
    @Inject(method = "getMenuName(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$hideNpcMenuName(@Coerce Object element, CallbackInfoReturnable<String> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue("");
        }
    }

    @Inject(method = "getFilterName(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$hideNpcFilterName(@Coerce Object element, CallbackInfoReturnable<String> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue("");
        }
    }

    @Inject(method = "getLeftSideLength(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;Lnet/minecraft/client/Minecraft;)I", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$hideNpcLeftText(@Coerce Object element, Minecraft minecraft, CallbackInfoReturnable<Integer> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue(9);
        }
    }

    @Inject(method = "isInteractable(Lxaero/hud/minimap/element/render/MinimapElementRenderLocation;Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$disableNpcInteractable(@Coerce Object location, @Coerce Object element, CallbackInfoReturnable<Boolean> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue(false);
        }
    }
}
