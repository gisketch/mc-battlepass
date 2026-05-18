package dev.gisketch.chowkingdom.mixin;

import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomState.class)
public interface RandomStateAccessor {
    @Accessor("noises")
    HolderGetter<NormalNoise.NoiseParameters> chowkingdom$getNoises();
}

