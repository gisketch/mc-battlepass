package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.SkylandsSeedCompat;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
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

    @ModifyVariable(
        method = "fillFromNoise",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private RandomState chowkingdom$useSkylandsSeedForFillFromNoise(RandomState randomState, Blender blender, RandomState original, StructureManager structureManager, ChunkAccess chunk) {
        return SkylandsSeedCompat.randomStateFor(this, randomState, this.generatorSettings());
    }

    @ModifyVariable(
        method = "buildSurface",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private RandomState chowkingdom$useSkylandsSeedForBuildSurface(RandomState randomState, WorldGenRegion region, StructureManager structureManager, RandomState original, ChunkAccess chunk) {
        return SkylandsSeedCompat.randomStateFor(this, randomState, this.generatorSettings());
    }

    @ModifyVariable(
        method = "applyCarvers",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private RandomState chowkingdom$useSkylandsSeedForCarvers(RandomState randomState, WorldGenRegion region, long seed, RandomState original, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        return SkylandsSeedCompat.randomStateFor(this, randomState, this.generatorSettings());
    }

    @ModifyVariable(
        method = "getBaseHeight",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private RandomState chowkingdom$useSkylandsSeedForBaseHeight(RandomState randomState, int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState original) {
        return SkylandsSeedCompat.randomStateFor(this, randomState, this.generatorSettings());
    }

    @ModifyVariable(
        method = "getBaseColumn",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private RandomState chowkingdom$useSkylandsSeedForBaseColumn(RandomState randomState, int x, int z, LevelHeightAccessor heightAccessor, RandomState original) {
        return SkylandsSeedCompat.randomStateFor(this, randomState, this.generatorSettings());
    }

    @ModifyVariable(
        method = "isAnchorLikeSpawnCandidate",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private RandomState chowkingdom$useSkylandsSeedForSpawnCandidate(RandomState randomState, RandomState original, int x, int z, int maxBuildHeight) {
        return SkylandsSeedCompat.randomStateFor(this, randomState, this.generatorSettings());
    }

    @ModifyVariable(
        method = "resolveAnchorSpawnPos",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private RandomState chowkingdom$useSkylandsSeedForSpawnPos(RandomState randomState, RandomState original, int x, int z, int maxBuildHeight) {
        return SkylandsSeedCompat.randomStateFor(this, randomState, this.generatorSettings());
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
