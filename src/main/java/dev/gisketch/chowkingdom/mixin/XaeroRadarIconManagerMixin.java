package dev.gisketch.chowkingdom.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.gisketch.chowkingdom.compat.CobblemonXaeroRadarCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.radar.icon.RadarIconManager", remap = false)
public abstract class XaeroRadarIconManagerMixin {
    @Inject(
        method = "get(Lnet/minecraft/world/entity/Entity;FZZLnet/minecraft/client/gui/GuiGraphics;Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lxaero/common/icon/XaeroIcon;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void chowkingdom$forceUnscannedCobblemonDot(
        Entity entity,
        float iconScale,
        boolean debugEntityIcons,
        boolean debugEntityVariantIds,
        GuiGraphics guiGraphics,
        RenderTarget framebuffer,
        CallbackInfoReturnable<Object> callback
    ) {
        if (!CobblemonXaeroRadarCompat.shouldForceUnscannedPokemonDot(entity)) {
            return;
        }

        Object dotIcon = CobblemonXaeroRadarCompat.xaeroDotIcon();
        if (dotIcon != null) {
            callback.setReturnValue(dotIcon);
        }
    }
}