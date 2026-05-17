package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import java.util.UUID

object BattlepassElytraGate {
    const val REQUIRED_LEVEL = 500

    fun register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickItem)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onUseItemStart)
        NeoForge.EVENT_BUS.addListener(::onEquipmentChange)
        NeoForge.EVENT_BUS.addListener(::onPlayerTickPost)
        NeoForge.EVENT_BUS.addListener(::onItemTooltip)
    }

    fun isElytra(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val id = BuiltInRegistries.ITEM.getKey(stack.item)
        return id.namespace == "minecraft" && id.path == "elytra" || id.path.contains("elytra")
    }

    fun canWear(player: ServerPlayer): Boolean = BattlepassXpStore.overallLevel(player) >= REQUIRED_LEVEL

    private fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = player.getItemInHand(event.hand)
        if (!shouldBlock(player, stack)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.FAIL
        deny(player)
    }

    private fun onUseItemStart(event: LivingEntityUseItemEvent.Start) {
        val player = event.entity as? ServerPlayer ?: return
        if (!shouldBlock(player, event.item)) return
        event.isCanceled = true
        event.duration = 0
        deny(player)
    }

    private fun onEquipmentChange(event: LivingEquipmentChangeEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (event.slot != EquipmentSlot.CHEST || !shouldBlock(player, event.to)) return
        player.setItemSlot(event.slot, ItemStack.EMPTY)
        giveBack(player, event.to)
        deny(player)
    }

    private fun onPlayerTickPost(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.tickCount % TICK_CHECK_INTERVAL != 0) return
        val chest = player.getItemBySlot(EquipmentSlot.CHEST)
        if (!shouldBlock(player, chest)) return
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY)
        giveBack(player, chest)
        deny(player)
    }

    private fun onItemTooltip(event: ItemTooltipEvent) {
        if (!isElytra(event.itemStack)) return
        val serverPlayer = event.entity as? ServerPlayer
        val currentLevel = serverPlayer?.let(BattlepassXpStore::overallLevel) ?: event.entity?.uuid?.let(::clientOverallLevel)
        if (currentLevel != null && currentLevel >= REQUIRED_LEVEL) return
        insertUnderTitle(event, Component.literal("Locked: Battlepass Level $REQUIRED_LEVEL").withStyle(ChatFormatting.DARK_RED))
        if (currentLevel != null) {
            insertUnderTitle(event, Component.literal("Current battlepass level: $currentLevel/$REQUIRED_LEVEL").withStyle(ChatFormatting.GRAY))
        } else {
            insertUnderTitle(event, Component.literal("Cannot be worn before level $REQUIRED_LEVEL.").withStyle(ChatFormatting.GRAY))
        }
    }

    private fun shouldBlock(player: ServerPlayer, stack: ItemStack): Boolean = isElytra(stack) && !canWear(player)

    private fun giveBack(player: ServerPlayer, stack: ItemStack) {
        val copy = stack.copy()
        if (copy.isEmpty) return
        if (!player.inventory.add(copy)) player.drop(copy, false)
    }

    private fun deny(player: ServerPlayer) {
        val level = BattlepassXpStore.overallLevel(player)
        val content = "Need Battlepass Level $REQUIRED_LEVEL to wear Elytra. Current: $level."
        SnackbarNetwork.send(player, SnackbarNotification.item(SnackbarIcons.ERROR, "ELYTRA LOCKED", content, SnackbarType.ERROR, SnackbarSounds.ERROR))
        player.displayClientMessage(Component.literal(content), true)
    }

    private fun clientOverallLevel(playerId: UUID): Int? = runCatching {
        val stateClass = Class.forName("dev.gisketch.chowkingdom.battlepass.BattlepassClientState")
        val state = stateClass.getField("INSTANCE").get(null)
        val progress = stateClass.getMethod("playerProgress", UUID::class.java).invoke(state, playerId) ?: return@runCatching null
        val xpByPass = progress.javaClass.getMethod("getXpByPass").invoke(progress) as? Map<*, *> ?: return@runCatching null
        xpByPass.values.filterIsInstance<Int>().sum() / 100
    }.getOrNull()

    private fun insertUnderTitle(event: ItemTooltipEvent, line: Component) {
        event.toolTip.add(1.coerceAtMost(event.toolTip.size), line)
    }

    private const val TICK_CHECK_INTERVAL = 20
}
