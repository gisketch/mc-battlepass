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
@Mixin(targets = "xaero.map.radar.tracker.PlayerTrackerMapElementRenderer", remap = false)
public abstract class XaeroWorldMapNpcRendererMixin {
    @Inject(
        method = "renderElement(Lxaero/map/radar/tracker/PlayerTrackerMapElement;ZDFDDLxaero/map/element/render/ElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void chowkingdom$renderNpcHead(
        @Coerce Object element,
        boolean hovered,
        double depth,
        float optionalScale,
        double x,
        double y,
        @Coerce Object renderInfo,
        GuiGraphics guiGraphics,
        MultiBufferSource.BufferSource bufferSource,
        @Coerce Object rendererProvider,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (XaeroNpcMapCompat.renderWorldMapNpc(element, guiGraphics, x, y)) {
            callback.setReturnValue(true);
        }
    }
}
