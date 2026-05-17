package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.XaeroNpcMapCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderer", remap = false)
public abstract class XaeroMinimapNpcRendererMixin {
    @Inject(
        method = "renderElement(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void chowkingdom$renderNpcHead(
        @Coerce Object element,
        boolean hovered,
        boolean radar,
        double depth,
        float partialTick,
        double x,
        double y,
        @Coerce Object renderInfo,
        GuiGraphics guiGraphics,
        MultiBufferSource.BufferSource bufferSource,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (XaeroNpcMapCompat.isCkdmNpcElement(element)) {
            callback.setReturnValue(XaeroNpcMapCompat.renderMinimapNpc(element, renderInfo, guiGraphics, depth));
        }
    }
}
