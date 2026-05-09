package dev.gisketch.chowkingdom.roles

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockDropsEvent
import net.neoforged.neoforge.event.level.BlockEvent
import java.util.UUID

internal object ExcavatorPerks {
    private val activeAreaMiningPlayers: MutableSet<UUID> = linkedSetOf()

    fun onBreakSpeed(event: PlayerEvent.BreakSpeed) {
        val player = event.entity as? ServerPlayer ?: return
        val bonus = RolePerks.configuredJobBonusPercent(player, "terrain_mining_speed").coerceAtLeast(0.0)
        if (bonus <= 0.0 || !isTerrainSpeedBlock(event.state)) return
        event.newSpeed = (event.newSpeed * (1.0 + bonus).toFloat()).coerceAtLeast(0.0f)
    }

    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        if (player.uuid in activeAreaMiningPlayers || player.isCrouching) return
        val rank = JobLevels.jobLevel(player)
        val shape = excavationShape(rank) ?: return
        if (!isExcavationBlock(event.state)) return
        val level = player.level() as? ServerLevel ?: return
        val positions = excavationPositions(event.pos, player.direction, player.xRot, shape)
        activeAreaMiningPlayers += player.uuid
        try {
            positions.forEach { pos -> breakExtraBlock(level, player, pos) }
        } finally {
            activeAreaMiningPlayers -= player.uuid
        }
    }

    fun onBlockDrops(event: BlockDropsEvent) {
        val player = event.breaker as? ServerPlayer ?: return
        val chance = RolePerks.configuredJobChance(player, "archaeologist")
        if (chance <= 0.0 || player.random.nextDouble() >= chance || !isArchaeologistBlock(event.state)) return
        val pool = RolePerks.jobPerks(player, "archaeologist").flatMap { perk -> perk.rewardPool }.ifEmpty { DEFAULT_TREASURE_POOL }
        val candidates = pool.mapNotNull { item -> RoleItemStacks.fromId(item, "archaeologist reward") }
        if (candidates.isEmpty()) return
        val stack = candidates[player.random.nextInt(candidates.size)].copy()
        event.drops.add(ItemEntity(player.level(), event.pos.x + 0.5, event.pos.y + 0.5, event.pos.z + 0.5, stack))
    }

    fun onPlayerTick(player: ServerPlayer) {
        if (RolePerks.jobPerks(player, "tunnel_sense").isEmpty() || player.y >= TUNNEL_SENSE_MAX_Y || player.level().canSeeSky(player.blockPosition())) return
        player.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, TUNNEL_SENSE_DURATION_TICKS, 0, false, false, true))
    }

    private fun breakExtraBlock(level: ServerLevel, player: ServerPlayer, pos: BlockPos) {
        val state = level.getBlockState(pos)
        if (!isExcavationBlock(state) || state.getDestroySpeed(level, pos) < 0.0f) return
        val stack = player.mainHandItem
        if (!level.destroyBlock(pos, true, player)) return
        damageTool(player, stack)
    }

    private fun damageTool(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty || !stack.isDamageableItem || player.abilities.instabuild) return
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND)
    }

    private fun excavationPositions(origin: BlockPos, facing: Direction, xRot: Float, shape: ExcavationShape): List<BlockPos> {
        val verticalPlane = kotlin.math.abs(xRot) < 55.0f
        val horizontal = if (facing.axis == Direction.Axis.X) Direction.SOUTH else Direction.EAST
        val vertical = Direction.UP
        val firstAxis = if (verticalPlane) horizontal else Direction.EAST
        val secondAxis = if (verticalPlane) vertical else Direction.SOUTH
        val firstOffsets = centeredOffsets(shape.width)
        val secondOffsets = centeredOffsets(shape.height)
        return firstOffsets.flatMap { first -> secondOffsets.map { second -> origin.relative(firstAxis, first).relative(secondAxis, second) } }
            .filterNot { pos -> pos == origin }
    }

    private fun centeredOffsets(size: Int): List<Int> = when (size) {
        1 -> listOf(0)
        2 -> listOf(0, 1)
        else -> listOf(-1, 0, 1)
    }

    private fun excavationShape(rank: Int): ExcavationShape? = when {
        rank >= 5 -> ExcavationShape(3, 3)
        rank == 4 -> ExcavationShape(3, 2)
        rank == 3 -> ExcavationShape(2, 2)
        rank == 2 -> ExcavationShape(2, 1)
        else -> null
    }

    private fun isTerrainSpeedBlock(state: BlockState): Boolean {
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return TERRAIN_SPEED_BLOCK_PARTS.any(id::contains) && !isOreBlock(id)
    }

    private fun isExcavationBlock(state: BlockState): Boolean {
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return SOFT_BLOCK_PARTS.any(id::contains) && !isOreBlock(id)
    }

    private fun isArchaeologistBlock(state: BlockState): Boolean {
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return ARCHAEOLOGIST_BLOCK_PARTS.any(id::contains)
    }

    private fun isOreBlock(id: String): Boolean = id.contains("ore") || id == "ancient_debris"

    private data class ExcavationShape(
        val width: Int,
        val height: Int,
    )

    private const val TUNNEL_SENSE_MAX_Y = 40.0
    private const val TUNNEL_SENSE_DURATION_TICKS = 260
    private val TERRAIN_SPEED_BLOCK_PARTS = listOf("dirt", "sand", "gravel", "clay", "mud", "stone", "deepslate", "granite", "diorite", "andesite", "tuff")
    private val SOFT_BLOCK_PARTS = listOf("dirt", "sand", "gravel", "clay", "mud")
    private val ARCHAEOLOGIST_BLOCK_PARTS = listOf("sand", "gravel", "clay", "suspicious")
    private val DEFAULT_TREASURE_POOL = listOf("minecraft:flint", "minecraft:iron_nugget*2", "minecraft:gold_nugget", "minecraft:emerald")
}
