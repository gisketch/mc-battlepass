package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.XaeroNpcMapCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Pseudo
@Mixin(targets = "xaero.map.radar.tracker.PlayerTrackerMapElementReader", remap = false)
public abstract class XaeroWorldMapNpcReaderMixin {
    @Inject(method = "getMenuName(Lxaero/map/radar/tracker/PlayerTrackerMapElement;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$hideNpcMenuName(@Coerce Object element, CallbackInfoReturnable<String> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue("");
        }
    }

    @Inject(method = "getFilterName(Lxaero/map/radar/tracker/PlayerTrackerMapElement;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$hideNpcFilterName(@Coerce Object element, CallbackInfoReturnable<String> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue("");
        }
    }

    @Inject(method = "getLeftSideLength(Lxaero/map/radar/tracker/PlayerTrackerMapElement;Lnet/minecraft/client/Minecraft;)I", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$hideNpcLeftText(@Coerce Object element, Minecraft minecraft, CallbackInfoReturnable<Integer> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue(9);
        }
    }

    @Inject(method = "isRightClickValid(Lxaero/map/radar/tracker/PlayerTrackerMapElement;)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$disableNpcRightClick(@Coerce Object element, CallbackInfoReturnable<Boolean> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getRightClickOptions(Lxaero/map/radar/tracker/PlayerTrackerMapElement;Lxaero/map/gui/IRightClickableElement;)Ljava/util/ArrayList;", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$hideNpcRightClickOptions(@Coerce Object element, @Coerce Object rightClickable, CallbackInfoReturnable<ArrayList<?>> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue(new ArrayList<>());
        }
    }

    @Inject(method = "isInteractable(Lxaero/map/element/render/ElementRenderLocation;Lxaero/map/radar/tracker/PlayerTrackerMapElement;)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void chowkingdom$disableNpcInteractable(@Coerce Object location, @Coerce Object element, CallbackInfoReturnable<Boolean> callback) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue(false);
        }
    }
}
