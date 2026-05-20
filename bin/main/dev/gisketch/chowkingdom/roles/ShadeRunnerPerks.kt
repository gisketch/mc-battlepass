package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import java.util.UUID

internal object ShadeRunnerPerks {
    private val SWIFT_SNEAK_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:shade_runner_swift_sneak")
    private val NIGHTSTEP_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:shade_runner_nightstep")
    private val backstabCooldownUntilTicks: MutableMap<String, Long> = linkedMapOf()
    private val shadowEscapeCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()

    fun onLivingDamage(event: LivingDamageEvent.Pre) {
        applyBackstab(event)
        (event.entity as? ServerPlayer)?.let { player -> applyShadowEscape(player, event) }
    }

    fun onPlayerTick(player: ServerPlayer) {
        applySwiftSneak(player)
        applyNightstep(player)
    }

    private fun applySwiftSneak(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val bonus = RolePerks.configuredJobMaxBonusPercent(player, "swift_sneak_lite").coerceAtLeast(0.0)
        if (bonus <= 0.0 || !player.isCrouching) {
            attribute.removeModifier(SWIFT_SNEAK_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                SWIFT_SNEAK_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
    }

    private fun applyNightstep(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val bonus = RolePerks.configuredJobMaxBonusPercent(player, "nightstep").coerceAtLeast(0.0)
        if (bonus <= 0.0 || !isNightstepActive(player)) {
            attribute.removeModifier(NIGHTSTEP_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                NIGHTSTEP_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
    }

    private fun applyBackstab(event: LivingDamageEvent.Pre) {
        val attacker = event.source.entity as? ServerPlayer ?: return
        if (event.source.directEntity !== attacker) return
        val target = event.entity
        val bonus = RolePerks.configuredJobMaxBonusPercent(attacker, "backstab_lite").coerceAtLeast(0.0)
        if (bonus <= 0.0 || !isBehindTarget(attacker, target)) return
        val now = attacker.level().gameTime
        val key = "${attacker.uuid}:${target.uuid}"
        if (now < (backstabCooldownUntilTicks[key] ?: 0L)) return
        event.newDamage = (event.newDamage * (1.0 + bonus).toFloat()).coerceAtLeast(0.0f)
        backstabCooldownUntilTicks[key] = now + BACKSTAB_COOLDOWN_TICKS
    }

    private fun applyShadowEscape(player: ServerPlayer, event: LivingDamageEvent.Pre) {
        if (RolePerks.jobPerks(player, "shadow_escape").isEmpty()) return
        val maxHealth = player.maxHealth
        val threshold = maxHealth * SHADOW_ESCAPE_HEALTH_THRESHOLD
        if (player.health <= threshold || player.health - event.newDamage > threshold) return
        val now = player.level().gameTime
        if (now < (shadowEscapeCooldownUntilTicks[player.uuid] ?: 0L)) return
        shadowEscapeCooldownUntilTicks[player.uuid] = now + SHADOW_ESCAPE_COOLDOWN_TICKS
        player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, SHADOW_ESCAPE_DURATION_TICKS, 1, false, false, true))
        (player.level() as? ServerLevel)?.sendParticles(ParticleTypes.POOF, player.x, player.y + 0.8, player.z, 24, 0.45, 0.65, 0.45, 0.02)
    }

    private fun isBehindTarget(attacker: ServerPlayer, target: LivingEntity): Boolean {
        val targetLook = target.lookAngle.horizontal().normalizeOrZero()
        val targetToAttacker = attacker.position().subtract(target.position()).horizontal().normalizeOrZero()
        if (targetLook.lengthSqr() == 0.0 || targetToAttacker.lengthSqr() == 0.0) return false
        return targetLook.dot(targetToAttacker) < -0.5
    }

    private fun isNightstepActive(player: ServerPlayer): Boolean {
        val level = player.level() as? ServerLevel ?: return false
        val dayTime = level.dayTime % 24000L
        if (dayTime in 13000L..23000L) return true
        return level.getMaxLocalRawBrightness(player.blockPosition()) <= LOW_LIGHT_THRESHOLD
    }

    private fun Vec3.horizontal(): Vec3 = Vec3(x, 0.0, z)

    private fun Vec3.normalizeOrZero(): Vec3 = if (lengthSqr() <= 1.0E-6) Vec3.ZERO else normalize()

    private const val BACKSTAB_COOLDOWN_TICKS = 200L
    private const val SHADOW_ESCAPE_DURATION_TICKS = 100
    private const val SHADOW_ESCAPE_COOLDOWN_TICKS = 2400L
    private const val SHADOW_ESCAPE_HEALTH_THRESHOLD = 0.30f
    private const val LOW_LIGHT_THRESHOLD = 7
}
