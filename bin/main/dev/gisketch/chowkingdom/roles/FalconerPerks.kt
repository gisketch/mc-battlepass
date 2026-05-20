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

internal object FalconerPerks {
    private val HIGH_GROUND_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:falconer_high_ground")
    private val slowFallCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()
    private val scoutLeapCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()
    private val scoutLeapSprintTicks: MutableMap<UUID, Int> = linkedMapOf()
    private val scoutLeapReadyPlayers: MutableSet<UUID> = linkedSetOf()

    fun onLivingDamage(player: ServerPlayer, event: LivingDamageEvent.Pre) {
        if (event.source.getMsgId() != "fall") return
        val reduction = RolePerks.configuredJobMaxBonusPercent(player, "fall_damage_reduction").coerceIn(0.0, 0.95)
        if (reduction <= 0.0) return
        event.newDamage = (event.newDamage * (1.0 - reduction).toFloat()).coerceAtLeast(0.0f)
    }

    fun onPlayerTick(player: ServerPlayer) {
        applySlowFallLite(player)
        applyHighGround(player)
        applyScoutsLeap(player)
    }

    private fun applySlowFallLite(player: ServerPlayer) {
        val seconds = RolePerks.configuredJobMaxBonusPercent(player, "slow_fall_lite").toInt().coerceAtLeast(0)
        if (seconds <= 0 || player.fallDistance <= SLOW_FALL_TRIGGER_DISTANCE || player.deltaMovement.y >= 0.0) return
        val now = player.level().gameTime
        if (now < (slowFallCooldownUntilTicks[player.uuid] ?: 0L)) return
        slowFallCooldownUntilTicks[player.uuid] = now + SLOW_FALL_COOLDOWN_TICKS
        player.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, seconds * 20, 0, false, false, true))
    }

    private fun applyHighGround(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val bonus = RolePerks.configuredJobMaxBonusPercent(player, "high_ground_speed").coerceAtLeast(0.0)
        if (bonus <= 0.0 || player.y < HIGH_GROUND_Y) {
            attribute.removeModifier(HIGH_GROUND_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                HIGH_GROUND_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
    }

    private fun applyScoutsLeap(player: ServerPlayer) {
        val boost = RolePerks.configuredJobMaxBonusPercent(player, "scouts_leap").coerceAtLeast(0.0)
        if (boost <= 0.0) {
            scoutLeapSprintTicks.remove(player.uuid)
            scoutLeapReadyPlayers.remove(player.uuid)
            return
        }
        val now = player.level().gameTime
        val onGround = player.onGround()
        if (player.isSprinting && onGround) {
            val ticks = (scoutLeapSprintTicks[player.uuid] ?: 0) + 1
            scoutLeapSprintTicks[player.uuid] = ticks
            if (ticks >= SCOUT_LEAP_SPRINT_TICKS && now >= (scoutLeapCooldownUntilTicks[player.uuid] ?: 0L)) scoutLeapReadyPlayers.add(player.uuid)
        } else if (onGround) {
            scoutLeapSprintTicks[player.uuid] = 0
            scoutLeapReadyPlayers.remove(player.uuid)
        }
        if (player.uuid !in scoutLeapReadyPlayers || onGround || player.deltaMovement.y <= 0.0) return
        player.deltaMovement = player.deltaMovement.add(0.0, SCOUT_LEAP_BASE_JUMP_VELOCITY * boost, 0.0)
        player.hasImpulse = true
        scoutLeapReadyPlayers.remove(player.uuid)
        scoutLeapSprintTicks[player.uuid] = 0
        scoutLeapCooldownUntilTicks[player.uuid] = now + SCOUT_LEAP_COOLDOWN_TICKS
    }

    private const val SLOW_FALL_TRIGGER_DISTANCE = 8.0f
    private const val SLOW_FALL_COOLDOWN_TICKS = 900L
    private const val HIGH_GROUND_Y = 100.0
    private const val SCOUT_LEAP_SPRINT_TICKS = 100
    private const val SCOUT_LEAP_COOLDOWN_TICKS = 400L
    private const val SCOUT_LEAP_BASE_JUMP_VELOCITY = 0.42
}
