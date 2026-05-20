package dev.gisketch.chowkingdom.roles

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import java.util.UUID

internal object MagmaScoutPerks {
    private val lavaGraceUntilTicks: MutableMap<UUID, Long> = linkedMapOf()
    private val lavaGraceCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()
    private val heatBurstCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()

    fun onLivingDamage(player: ServerPlayer, event: LivingDamageEvent.Pre) {
        if (!isFireDamage(event.source)) return
        triggerHeatBurst(player)
        if (isLavaWalkerDamage(event.source) && tryApplyLavaGrace(player)) {
            event.newDamage = 0.0f
            return
        }
        val fireReduction = RolePerks.configuredJobMaxBonusPercent(player, "fire_damage_reduction").coerceIn(0.0, 0.95)
        val lavaReduction = if (isLavaWalkerDamage(event.source)) RolePerks.configuredJobMaxBonusPercent(player, "lava_walker").coerceIn(0.0, 0.95) else 0.0
        val multiplier = (1.0 - fireReduction) * (1.0 - lavaReduction)
        if (multiplier < 1.0) event.newDamage = (event.newDamage * multiplier.toFloat()).coerceAtLeast(0.0f)
    }

    private fun tryApplyLavaGrace(player: ServerPlayer): Boolean {
        if (RolePerks.jobPerks(player, "lava_walker").isEmpty()) return false
        val rank = JobLevels.jobLevel(player)
        val graceTicks = when {
            rank >= 5 -> 60L
            rank >= 4 -> 40L
            rank >= 3 -> 20L
            else -> 0L
        }
        if (graceTicks <= 0L) return false
        val now = player.level().gameTime
        if (now < (lavaGraceUntilTicks[player.uuid] ?: 0L)) return true
        if (now < (lavaGraceCooldownUntilTicks[player.uuid] ?: 0L)) return false
        lavaGraceUntilTicks[player.uuid] = now + graceTicks
        lavaGraceCooldownUntilTicks[player.uuid] = now + HEAT_COOLDOWN_TICKS
        return true
    }

    private fun triggerHeatBurst(player: ServerPlayer) {
        if (RolePerks.jobPerks(player, "heat_burst").isEmpty()) return
        val now = player.level().gameTime
        if (now < (heatBurstCooldownUntilTicks[player.uuid] ?: 0L)) return
        heatBurstCooldownUntilTicks[player.uuid] = now + HEAT_COOLDOWN_TICKS
        player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, HEAT_BURST_TICKS, 0, false, false, true))
        player.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, HEAT_BURST_TICKS, 0, false, false, true))
    }

    private fun isFireDamage(source: net.minecraft.world.damagesource.DamageSource): Boolean {
        val id = source.getMsgId()
        return id in FIRE_DAMAGE_IDS || id in LAVA_WALKER_DAMAGE_IDS
    }

    private fun isLavaWalkerDamage(source: net.minecraft.world.damagesource.DamageSource): Boolean = source.getMsgId() in LAVA_WALKER_DAMAGE_IDS

    private const val HEAT_BURST_TICKS = 100
    private const val HEAT_COOLDOWN_TICKS = 1800L
    private val FIRE_DAMAGE_IDS = setOf("inFire", "onFire", "lava", "hotFloor", "fireball", "unattributedFireball")
    private val LAVA_WALKER_DAMAGE_IDS = setOf("inFire", "onFire", "lava", "hotFloor")
}
