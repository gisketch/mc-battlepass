package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import java.util.UUID

internal object ShinobiPerks {
    private val SNEAK_SPEED_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:shinobi_sneak_speed")
    private val smokeStepCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()
    private val lastReducedPoisonDuration: MutableMap<UUID, Int> = linkedMapOf()

    fun onLivingDamage(event: LivingDamageEvent.Pre) {
        val attacker = event.source.entity as? ServerPlayer
        if (attacker != null && event.source.directEntity === attacker) applyPoisonAspect(attacker, event)
        (event.entity as? ServerPlayer)?.let(::applySmokeStep)
    }

    fun onPlayerTick(player: ServerPlayer) {
        applySneakSpeed(player)
        applyToxicResistance(player)
    }

    private fun applyPoisonAspect(attacker: ServerPlayer, event: LivingDamageEvent.Pre) {
        val chance = RolePerks.configuredJobChance(attacker, "poison_aspect_lite")
        if (chance <= 0.0 || attacker.random.nextDouble() >= chance) return
        val duration = poisonDurationTicks(JobLevels.jobLevel(attacker))
        event.entity.addEffect(MobEffectInstance(MobEffects.POISON, duration, 0, false, false, true))
    }

    private fun applySneakSpeed(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val bonus = RolePerks.configuredJobMaxBonusPercent(player, "shinobi_sneak_speed").coerceAtLeast(0.0)
        if (bonus <= 0.0 || !player.isCrouching) {
            attribute.removeModifier(SNEAK_SPEED_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                SNEAK_SPEED_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
    }

    private fun applyToxicResistance(player: ServerPlayer) {
        if (RolePerks.jobPerks(player, "toxic_resistance").isEmpty()) return
        val poison = player.getEffect(MobEffects.POISON) ?: run {
            lastReducedPoisonDuration.remove(player.uuid)
            return
        }
        val previousReduced = lastReducedPoisonDuration[player.uuid]
        if (previousReduced != null && poison.duration <= previousReduced) {
            lastReducedPoisonDuration[player.uuid] = poison.duration
            return
        }
        val reducedDuration = (poison.duration * TOXIC_RESISTANCE_DURATION_MULTIPLIER).toInt().coerceAtLeast(1)
        player.addEffect(MobEffectInstance(MobEffects.POISON, reducedDuration, poison.amplifier, poison.isAmbient, poison.isVisible, poison.showIcon()))
        lastReducedPoisonDuration[player.uuid] = reducedDuration
    }

    private fun applySmokeStep(player: ServerPlayer) {
        if (!player.isCrouching || RolePerks.jobPerks(player, "smoke_step").isEmpty()) return
        val now = player.level().gameTime
        if (now < (smokeStepCooldownUntilTicks[player.uuid] ?: 0L)) return
        smokeStepCooldownUntilTicks[player.uuid] = now + SMOKE_STEP_COOLDOWN_TICKS
        player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, SMOKE_STEP_DURATION_TICKS, 1, false, false, true))
    }

    private fun poisonDurationTicks(rank: Int): Int = when {
        rank >= 5 -> 80
        rank >= 3 -> 60
        else -> 40
    }

    private const val TOXIC_RESISTANCE_DURATION_MULTIPLIER = 0.50
    private const val SMOKE_STEP_DURATION_TICKS = 80
    private const val SMOKE_STEP_COOLDOWN_TICKS = 1200L
}
