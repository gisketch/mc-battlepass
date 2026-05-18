package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.compat.SkylandsSeedCompat;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorSeedMixin {
    @Shadow
    public abstract Holder<NoiseGeneratorSettings> generatorSettings();

    @ModifyVariable(
        method = "createBiomes(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private RandomState chowkingdom$useSkylandsSeedForCreateBiomes(RandomState randomState, RandomState original, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return SkylandsSeedCompat.randomStateFor(this, randomState, this.generatorSettings());
    }
}
