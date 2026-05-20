package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

internal object DiverPerks {
    private val SWIM_MOVEMENT_SPEED_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:diver_swim_speed")

    fun onBreakSpeed(event: PlayerEvent.BreakSpeed) {
        val player = event.entity as? ServerPlayer ?: return
        if (!player.isUnderWater) return
        val reduction = RolePerks.configuredJobMaxBonusPercent(player, "underwater_mining_penalty_reduction").coerceIn(0.0, 1.0)
        if (reduction <= 0.0) return
        val penalty = event.originalSpeed - event.newSpeed
        if (penalty <= 0.0f) return
        event.newSpeed = event.newSpeed + (penalty * reduction).toFloat()
    }

    fun onItemFished(event: ItemFishedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (event.isCanceled) return
        val bonusChance = RolePerks.configuredJobChance(player, "fishing_bonus_drop_chance")
        if (bonusChance <= 0.0 || player.random.nextDouble() >= bonusChance) return
        val candidates = event.drops.filterNot(ItemStack::isEmpty)
        if (candidates.isEmpty()) return
        val bonus = candidates[player.random.nextInt(candidates.size)].copy()
        bonus.count = 1
        if (!player.inventory.add(bonus)) player.drop(bonus, false)
    }

    fun onPlayerTick(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val bonus = RolePerks.configuredJobBonusPercent(player, "swim_speed").coerceAtLeast(0.0)
        if (!player.isInWater || bonus <= 0.0) {
            attribute.removeModifier(SWIM_MOVEMENT_SPEED_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                SWIM_MOVEMENT_SPEED_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
    }
}
