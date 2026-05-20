package dev.gisketch.chowkingdom.roles

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions
import java.util.function.Consumer

class JobRankMobEffect(private val jobSlot: Int) : MobEffect(MobEffectCategory.BENEFICIAL, 0xD6B35A) {
	@Suppress("OVERRIDE_DEPRECATION")
	override fun initializeClient(consumer: Consumer<IClientMobEffectExtensions>) {
		consumer.accept(JobStatusClientEffectExtensions(jobSlot))
	}

	override fun getSortOrder(effectInstance: MobEffectInstance): Int = -200 + jobSlot
}