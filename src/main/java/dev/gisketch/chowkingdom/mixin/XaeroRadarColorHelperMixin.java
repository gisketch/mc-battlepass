package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.CobblemonXaeroRadarCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.radar.color.RadarColorHelper", remap = false)
public abstract class XaeroRadarColorHelperMixin {
    @Inject(
        method = "getEntityColor(Lnet/minecraft/world/entity/Entity;FZIIZLxaero/hud/minimap/radar/color/RadarColor;Lxaero/hud/minimap/radar/color/RadarColor;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void chowkingdom$forceUnscannedCobblemonWhiteDot(
        Entity entity,
        float verticalDifference,
        boolean cave,
        int heightLimit,
        int startFadingAt,
        boolean heightBasedFade,
        @Coerce Object radarColor,
        @Coerce Object fallbackColor,
        CallbackInfoReturnable<Integer> callback
    ) {
        if (CobblemonXaeroRadarCompat.shouldForceUnscannedPokemonDot(entity)) {
            callback.setReturnValue(CobblemonXaeroRadarCompat.UNSCANNED_DOT_COLOR);
        }
    }
}