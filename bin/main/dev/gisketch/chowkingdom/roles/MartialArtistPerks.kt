package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Monster
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import java.util.UUID

internal object MartialArtistPerks {
    private val AGILITY_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:martial_artist_agility")
    private val agilityUntilTicks: MutableMap<UUID, Long> = linkedMapOf()
    private val comboStates: MutableMap<UUID, ComboState> = linkedMapOf()
    private val secondWindCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()

    fun onLivingDamage(event: LivingDamageEvent.Pre) {
        val attacker = event.source.entity as? ServerPlayer ?: return
        if (event.source.directEntity !== attacker) return
        val target = event.entity
        applyKnockbackLite(attacker, target)
        applyAgilityLite(attacker)
        applyComboFlow(attacker, target, event)
    }

    fun onLivingDeath(event: LivingDeathEvent) {
        val attacker = event.source.entity as? ServerPlayer ?: return
        val target = event.entity
        if (target !is Monster || RolePerks.jobPerks(attacker, "second_wind").isEmpty()) return
        val now = attacker.level().gameTime
        if (now < (secondWindCooldownUntilTicks[attacker.uuid] ?: 0L)) return
        secondWindCooldownUntilTicks[attacker.uuid] = now + SECOND_WIND_COOLDOWN_TICKS
        attacker.heal(SECOND_WIND_HEAL_AMOUNT)
    }

    fun onPlayerTick(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.ATTACK_SPEED) ?: return
        val now = player.level().gameTime
        val bonus = RolePerks.configuredJobMaxBonusPercent(player, "agility_lite").coerceAtLeast(0.0)
        if (bonus <= 0.0 || now >= (agilityUntilTicks[player.uuid] ?: 0L)) {
            attribute.removeModifier(AGILITY_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                AGILITY_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
    }

    private fun applyKnockbackLite(attacker: ServerPlayer, target: LivingEntity) {
        val bonus = RolePerks.configuredJobMaxBonusPercent(attacker, "knockback_lite").coerceAtLeast(0.0)
        if (bonus <= 0.0) return
        target.knockback(KNOCKBACK_BASE_STRENGTH * bonus, attacker.x - target.x, attacker.z - target.z)
    }

    private fun applyAgilityLite(attacker: ServerPlayer) {
        if (RolePerks.jobPerks(attacker, "agility_lite").isEmpty()) return
        agilityUntilTicks[attacker.uuid] = attacker.level().gameTime + AGILITY_DURATION_TICKS
    }

    private fun applyComboFlow(attacker: ServerPlayer, target: LivingEntity, event: LivingDamageEvent.Pre) {
        if (RolePerks.jobPerks(attacker, "combo_flow").isEmpty()) return
        val now = attacker.level().gameTime
        val previous = comboStates[attacker.uuid]
        val hitCount = if (previous?.targetUuid == target.uuid && now - previous.lastHitTick <= COMBO_RESET_TICKS) previous.hitCount + 1 else 1
        comboStates[attacker.uuid] = ComboState(target.uuid, hitCount, now)
        if (hitCount % COMBO_HIT_INTERVAL != 0) return
        event.newDamage = (event.newDamage * (1.0 + COMBO_DAMAGE_BONUS).toFloat()).coerceAtLeast(0.0f)
    }

    private data class ComboState(
        val targetUuid: UUID,
        val hitCount: Int,
        val lastHitTick: Long,
    )

    private const val KNOCKBACK_BASE_STRENGTH = 0.4
    private const val AGILITY_DURATION_TICKS = 60L
    private const val COMBO_RESET_TICKS = 80L
    private const val COMBO_HIT_INTERVAL = 3
    private const val COMBO_DAMAGE_BONUS = 0.10
    private const val SECOND_WIND_HEAL_AMOUNT = 2.0f
    private const val SECOND_WIND_COOLDOWN_TICKS = 400L
}
