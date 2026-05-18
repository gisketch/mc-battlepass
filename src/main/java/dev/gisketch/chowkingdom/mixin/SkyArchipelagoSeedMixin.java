package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.SkylandsSeedCompat;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator", remap = false)
public abstract class SkyArchipelagoSeedMixin extends NoiseBasedChunkGenerator {
    private SkyArchipelagoSeedMixin() {
        super(null, null);
    }

    @ModifyVariable(method = "getIslandField", at = @At(value = "STORE"), ordinal = 0, require = 0)
    private long chowkingdom$overrideSkyIslandLayoutSeed(long original, RandomState randomState) {
        return SkylandsSeedCompat.overrideLayoutSeed(original, randomState, this.generatorSettings());
    }

    @Redirect(
        method = {"lambda$getIslandField$3", "lambda$getIslandField$4"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;sampler()Lnet/minecraft/world/level/biome/Climate$Sampler;"
        ),
        require = 0
    )
    private Climate.Sampler chowkingdom$overrideSkyIslandBiomeSampler(RandomState randomState) {
        Holder<NoiseGeneratorSettings> settings = this.generatorSettings();
        return SkylandsSeedCompat.samplerFor(randomState, settings);
    }
}
