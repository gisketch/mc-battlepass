package dev.gisketch.chowkingdom.battlepass

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent

object BattlepassFarmersDelightEventIntegration {
    private val recentCuttingBoardUses: MutableMap<java.util.UUID, RecentCuttingBoardUse> = linkedMapOf()

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onRightClickBlock)
        NeoForge.EVENT_BUS.addListener(::onEntityJoinLevel)
        NeoForge.EVENT_BUS.addListener(::onBlockBreak)
        NeoForge.EVENT_BUS.addListener(::onItemCrafted)
        NeoForge.EVENT_BUS.addListener(::onItemUseFinish)
    }

    private fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val state = event.level.getBlockState(event.pos)
        val blockId = BuiltInRegistries.BLOCK.getKey(state.block)
        if (blockId.namespace != FARMERS_DELIGHT) return

        val attributes = mapOf("block" to blockId.toString(), "item" to itemId(event.itemStack))
        when {
            blockId.path.contains("cutting_board") && !event.itemStack.isEmpty -> recentCuttingBoardUses[player.uuid] = RecentCuttingBoardUse(event.pos, isKnife(event.itemStack), attributes, event.level.gameTime + CUTTING_BOARD_OUTPUT_WINDOW_TICKS)
            isFeastBlock(blockId.path) -> record(player, "farmersdelight:feast_served", attributes = attributes)
        }
    }

    private fun onEntityJoinLevel(event: EntityJoinLevelEvent) {
        val level = event.level as? ServerLevel ?: return
        val itemEntity = event.entity as? ItemEntity ?: return
        val stack = itemEntity.item
        if (stack.isEmpty) return

        val match = recentCuttingBoardUses.entries.firstOrNull { (_, recent) ->
            level.gameTime <= recent.expiresAt && itemEntity.blockPosition().distManhattan(recent.pos) <= CUTTING_BOARD_OUTPUT_DISTANCE
        } ?: return
        val player = level.server.playerList.getPlayer(match.key) ?: return
        val recent = match.value
        recentCuttingBoardUses.remove(match.key)
        val attributes = recent.attributes + mapOf("output" to itemId(stack))
        record(player, "farmersdelight:cutting_board_used", attributes = attributes)
        record(player, "farmersdelight:cutting_board_outputs", stack.count.coerceAtLeast(1), attributes)
        if (recent.usedKnife) record(player, "farmersdelight:knife_used", attributes = attributes)
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        val blockId = BuiltInRegistries.BLOCK.getKey(event.state.block)
        if (blockId.namespace == FARMERS_DELIGHT && blockId.path.contains("wild")) {
            record(player, "farmersdelight:wild_crop_harvested", attributes = mapOf("block" to blockId.toString()))
        }
    }

    private fun onItemCrafted(event: PlayerEvent.ItemCraftedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val itemId = BuiltInRegistries.ITEM.getKey(event.crafting.item)
        val inventoryClass = event.inventory.javaClass.name.lowercase()
        if (itemId.namespace != FARMERS_DELIGHT && !inventoryClass.contains("farmersdelight") && !inventoryClass.contains("cooking")) return

        val attributes = mapOf("item" to itemId.toString(), "item.namespace" to itemId.namespace)
        record(player, "farmersdelight:cooking_pot_meal_cooked", event.crafting.count, attributes)
    }

    private fun onItemUseFinish(event: LivingEntityUseItemEvent.Finish) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = event.item
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        if (itemId.namespace != FARMERS_DELIGHT || stack.getFoodProperties(player) == null) return
        record(player, "farmersdelight:meal_eaten", 1, mapOf("item" to itemId.toString(), "item.namespace" to itemId.namespace))
    }

    private fun record(player: ServerPlayer, eventId: String, amount: Int = 1, attributes: Map<String, String> = emptyMap()) {
        if (BattlepassMissionEventBank.record(player, eventId, amount, attributes)) BattlepassNetwork.syncAllPlayers()
    }

    private fun itemId(stack: ItemStack): String = if (stack.isEmpty) "minecraft:air" else BuiltInRegistries.ITEM.getKey(stack.item).toString()

    private fun isKnife(stack: ItemStack): Boolean = !stack.isEmpty && BuiltInRegistries.ITEM.getKey(stack.item).path.contains("knife")

    private fun isFeastBlock(path: String): Boolean = path.contains("feast") || path.contains("pie") || path.contains("cake") || path.contains("roast")

    private const val FARMERS_DELIGHT = "farmersdelight"
    private const val CUTTING_BOARD_OUTPUT_WINDOW_TICKS = 5L
    private const val CUTTING_BOARD_OUTPUT_DISTANCE = 2

    private data class RecentCuttingBoardUse(
        val pos: BlockPos,
        val usedKnife: Boolean,
        val attributes: Map<String, String>,
        val expiresAt: Long,
    )
}