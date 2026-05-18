package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.SkylandsBiomeAliasCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeManager.class)
public class SkylandsBiomeAliasMixin {
    @Shadow
    @Final
    private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    @Inject(method = "getBiome", at = @At("RETURN"), cancellable = true)
    private void chowkingdom$aliasSkylandsBiome(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> callback) {
        chowkingdom$alias(pos, callback);
    }

    @Inject(method = "getNoiseBiomeAtQuart", at = @At("RETURN"), cancellable = true)
    private void chowkingdom$aliasSkylandsNoiseBiome(int quartX, int quartY, int quartZ, CallbackInfoReturnable<Holder<Biome>> callback) {
        chowkingdom$alias(new BlockPos(QuartPos.toBlock(quartX), QuartPos.toBlock(quartY), QuartPos.toBlock(quartZ)), callback);
    }

    private void chowkingdom$alias(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> callback) {
        if (!(this.noiseBiomeSource instanceof Level level)) return;
        callback.setReturnValue(SkylandsBiomeAliasCompat.aliasBiome(level, pos, callback.getReturnValue()));
    }
}
