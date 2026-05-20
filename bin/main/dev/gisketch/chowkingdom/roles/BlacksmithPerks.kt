package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.event.AnvilUpdateEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import kotlin.math.floor

internal object BlacksmithPerks {
    private val repairingCooldownUntilTicks: MutableMap<java.util.UUID, Long> = linkedMapOf()

    fun onAnvilUpdate(event: AnvilUpdateEvent) {
        val player = event.player as? ServerPlayer ?: return
        if (RolePerks.jobPerks(player, "forge_discount").isEmpty()) return
        val discounted = floor(event.cost * FORGE_DISCOUNT_MULTIPLIER).toLong().coerceAtLeast(1L)
        if (discounted < event.cost) event.cost = discounted
    }

    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        applyUnbreakingLite(player)
        if (isOre(event.state)) applyRepairingLite(player)
    }

    fun onLivingDeath(event: LivingDeathEvent) {
        val player = event.source.entity as? ServerPlayer ?: return
        if (event.entity === player || event.entity !is LivingEntity) return
        applyUnbreakingLite(player)
        applyRepairingLite(player)
    }

    fun onItemSmelted(event: PlayerEvent.ItemSmeltedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (RolePerks.jobPerks(player, "ore_tempering").isEmpty()) return
        val result = event.smelting
        if (result.isEmpty || !isTemperedIngot(result)) return
        val bonusCount = (0 until result.count).count { player.random.nextDouble() < ORE_TEMPERING_CHANCE }
        if (bonusCount <= 0) return
        val bonus = ItemStack(result.item, bonusCount.coerceAtMost(result.maxStackSize))
        if (!player.inventory.add(bonus)) player.drop(bonus, false)
        SnackbarNetwork.send(player, SnackbarNotification.texture(SnackbarIcons.CHOWCOIN_TEXTURE, "ORE TEMPERING", "+$bonusCount bonus ingot", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
    }

    private fun applyUnbreakingLite(player: ServerPlayer) {
        val chance = RolePerks.configuredJobMaxBonusPercent(player, "unbreaking_lite").coerceAtLeast(0.0)
        if (chance <= 0.0 || player.random.nextDouble() >= chance) return
        repairHeldItem(player)
    }

    private fun applyRepairingLite(player: ServerPlayer) {
        val chance = RolePerks.configuredJobMaxBonusPercent(player, "repairing_lite").coerceAtLeast(0.0)
        if (chance <= 0.0) return
        val now = player.level().gameTime
        if (now < (repairingCooldownUntilTicks[player.uuid] ?: 0L)) return
        if (player.random.nextDouble() >= chance) return
        if (!repairHeldItem(player)) return
        repairingCooldownUntilTicks[player.uuid] = now + REPAIRING_COOLDOWN_TICKS
    }

    private fun repairHeldItem(player: ServerPlayer): Boolean {
        val stack = player.mainHandItem
        if (stack.isEmpty || !stack.isDamageableItem || !stack.isDamaged) return false
        stack.damageValue = (stack.damageValue - 1).coerceAtLeast(0)
        return true
    }

    private fun isOre(state: BlockState): Boolean {
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return id.contains("_ore") || id.contains("ancient_debris")
    }

    private fun isTemperedIngot(stack: ItemStack): Boolean = stack.`is`(Items.IRON_INGOT) || stack.`is`(Items.COPPER_INGOT) || stack.`is`(Items.GOLD_INGOT)

    private const val FORGE_DISCOUNT_MULTIPLIER = 0.80
    private const val ORE_TEMPERING_CHANCE = 0.10
    private const val REPAIRING_COOLDOWN_TICKS = 600L
}
