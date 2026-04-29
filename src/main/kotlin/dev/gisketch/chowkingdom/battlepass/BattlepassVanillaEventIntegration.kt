package dev.gisketch.chowkingdom.battlepass

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent
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
        NeoForge.EVENT_BUS.addListener(::onPlayerTick)
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        val player = event.source.entity as? ServerPlayer ?: return
        if (event.entity is Monster) record(player, "minecraft:monster_killed")
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

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val previous = lastPositions.put(player.uuid, PositionSnapshot(player.x, player.z)) ?: return
        val dx = player.x - previous.x
        val dz = player.z - previous.z
        val moved = kotlin.math.sqrt(dx * dx + dz * dz)
        if (moved <= 0.0 || moved > MAX_TRAVEL_PER_TICK) return

        val total = (travelRemainders[player.uuid] ?: 0.0) + moved
        val blocks = floor(total).toInt()
        travelRemainders[player.uuid] = total - blocks
        if (blocks > 0) record(player, "minecraft:blocks_traveled", blocks)
    }

    private fun record(player: ServerPlayer, eventId: String, amount: Int = 1) {
        if (BattlepassMissionProgressStore.recordEvent(player, eventId, amount)) {
            BattlepassNetwork.syncAllPlayers()
        }
    }

    private data class PositionSnapshot(val x: Double, val z: Double)

    private const val MAX_TRAVEL_PER_TICK = 1.5
}