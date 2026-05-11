package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.npc.NpcQuestService
import dev.gisketch.chowkingdom.roles.ClassMentorQuestService
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import java.util.UUID
import kotlin.math.floor

object BattlepassVanillaEventIntegration {
    private val lastPositions = linkedMapOf<UUID, PositionSnapshot>()
    private val travelRemainders = linkedMapOf<UUID, Double>()

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onLivingDeath)
        NeoForge.EVENT_BUS.addListener(::onBlockBreak)
        NeoForge.EVENT_BUS.addListener(::onBabyEntitySpawn)
        NeoForge.EVENT_BUS.addListener(::onVillagerTrade)
        NeoForge.EVENT_BUS.addListener(::onItemFished)
        NeoForge.EVENT_BUS.addListener(::onItemCrafted)
        NeoForge.EVENT_BUS.addListener(::onItemSmelted)
        NeoForge.EVENT_BUS.addListener(::onItemUseFinish)
        NeoForge.EVENT_BUS.addListener(::onPlayerTick)
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        val player = event.source.entity as? ServerPlayer ?: return
        val entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.entity.type).toString()
        val attributes = linkedMapOf(
            "entity" to entityId,
            "entity.namespace" to entityId.substringBefore(':'),
            "dimension" to player.level().dimension().location().toString(),
            "monster" to (event.entity is Monster).toString(),
        )
        record(player, "minecraft:entity_killed", attributes = attributes)
        if (event.entity is Monster) record(player, "minecraft:monster_killed", attributes = attributes)
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        val crop = event.state.block as? CropBlock ?: return
        if (crop.isMaxAge(event.state)) record(player, "minecraft:crop_harvested")
    }

    private fun onBabyEntitySpawn(event: BabyEntitySpawnEvent) {
        val player = event.causedByPlayer as? ServerPlayer ?: return
        record(player, "minecraft:animal_bred")
    }

    private fun onVillagerTrade(event: TradeWithVillagerEvent) {
        val player = event.entity as? ServerPlayer ?: return
        record(player, "minecraft:villager_traded")
    }

    private fun onItemFished(event: ItemFishedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (event.drops.isNotEmpty()) record(player, "minecraft:fish_caught", event.drops.size)
    }

    private fun onItemCrafted(event: PlayerEvent.ItemCraftedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = event.crafting
        if (stack.isEmpty) return
        val attributes = itemAttributes(player, stack) + mapOf("process" to craftProcess(event.inventory.javaClass.name))
        val aliases = farmerFoodAliases(player, stack, attributes["process"].orEmpty())
        val signal = BattlepassMissionSignal(setOf("minecraft:item_crafted") + aliases, stack.count.coerceAtLeast(1), attributes)
        NpcQuestService.markFoodChainCreatedItem(player, stack, signal)
        ClassMentorQuestService.markFoodChainCreatedItem(player, stack, signal)
        record(player, "minecraft:item_crafted", stack.count.coerceAtLeast(1), attributes, aliases)
    }

    private fun onItemSmelted(event: PlayerEvent.ItemSmeltedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = event.smelting
        if (stack.isEmpty) return
        val attributes = itemAttributes(player, stack) + mapOf("process" to "smelt")
        val aliases = farmerFoodAliases(player, stack, "smelt")
        val signal = BattlepassMissionSignal(setOf("minecraft:item_smelted") + aliases, stack.count.coerceAtLeast(1), attributes)
        NpcQuestService.markFoodChainCreatedItem(player, stack, signal)
        ClassMentorQuestService.markFoodChainCreatedItem(player, stack, signal)
        record(player, "minecraft:item_smelted", stack.count.coerceAtLeast(1), attributes, aliases)
    }

    private fun onItemUseFinish(event: LivingEntityUseItemEvent.Finish) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = event.item
        if (stack.isEmpty || stack.getFoodProperties(player) == null) return
        record(player, "minecraft:item_eaten", 1, itemAttributes(player, stack.copyWithCount(1)))
    }

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val previous = lastPositions.put(player.uuid, PositionSnapshot(player.x, player.z)) ?: return
        val dx = player.x - previous.x
        val dz = player.z - previous.z
        val mountedPokemonAttributes = CobblemonBattlepassIntegration.riddenPokemonTravelAttributes(player)
        val moved = kotlin.math.sqrt(dx * dx + dz * dz)
        val maxTravel = if (mountedPokemonAttributes != null) MAX_MOUNT_TRAVEL_PER_TICK else MAX_TRAVEL_PER_TICK
        if (moved <= 0.0 || moved > maxTravel) return

        val total = (travelRemainders[player.uuid] ?: 0.0) + moved
        val blocks = floor(total).toInt()
        travelRemainders[player.uuid] = total - blocks
        if (blocks > 0) {
            val attributes = mapOf("dimension" to player.level().dimension().location().toString())
            record(player, "minecraft:blocks_traveled", blocks, attributes)
            if (isOnFootTravel(player)) record(player, "minecraft:travel_on_foot", blocks, attributes + mapOf("mode" to "on_foot"))
            if (mountedPokemonAttributes != null) {
                record(player, "cobblemon:pokemon_mount_traveled", blocks, mountedPokemonAttributes)
                when (mountedPokemonAttributes["mode"]) {
                    "pokemon_flying" -> record(player, "cobblemon:pokemon_mount_flying_traveled", blocks, mountedPokemonAttributes)
                    "pokemon_land" -> record(player, "cobblemon:pokemon_mount_land_traveled", blocks, mountedPokemonAttributes)
                }
            }
        }
    }

    private fun isOnFootTravel(player: ServerPlayer): Boolean =
        !player.isPassenger && !player.isFallFlying && !player.abilities.flying && !player.isInWater && !player.isInLava

    private fun itemAttributes(player: ServerPlayer, stack: ItemStack): Map<String, String> {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return mapOf(
            "item" to itemId,
            "item.namespace" to itemId.substringBefore(':'),
            "dimension" to player.level().dimension().location().toString(),
        )
    }

    private fun farmerFoodAliases(player: ServerPlayer, stack: ItemStack, process: String): Set<String> {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        if (itemId.namespace != "farmersdelight" || stack.getFoodProperties(player) == null) return emptySet()
        return setOf(
            "farmersdelight:food_created",
            "farmersdelight:${process}_food_created",
        )
    }

    private fun craftProcess(inventoryClassName: String): String {
        val name = inventoryClassName.lowercase()
        return if (name.contains("farmersdelight") || name.contains("cooking")) "cook" else "craft"
    }

    private fun record(player: ServerPlayer, eventId: String, amount: Int = 1, attributes: Map<String, String> = emptyMap(), aliases: Set<String> = emptySet()) {
        if (BattlepassMissionEventBank.record(player, eventId, amount, attributes, aliases)) {
            BattlepassNetwork.syncAllPlayers()
        }
    }

    private data class PositionSnapshot(val x: Double, val z: Double)

    private const val MAX_TRAVEL_PER_TICK = 1.5
    private const val MAX_MOUNT_TRAVEL_PER_TICK = 16.0
}
