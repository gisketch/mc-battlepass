package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.level.BlockDropsEvent
import java.util.UUID
import kotlin.math.floor

internal object BugScoutPerks {
    private val WEB_WALKER_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:bug_scout_web_walker")
    private val swarmSenseCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()

    fun onBlockDrops(event: BlockDropsEvent) {
        val player = event.breaker as? ServerPlayer ?: return
        val chance = RolePerks.configuredJobChance(player, "tiny_forager")
        if (chance <= 0.0 || !isTinyForagerBlock(event.state) || player.random.nextDouble() >= chance) return
        val pool = RolePerks.jobPerks(player, "tiny_forager").flatMap { perk -> perk.rewardPool }.ifEmpty { DEFAULT_TINY_FORAGER_POOL }
        val candidates = pool.mapNotNull { item -> RoleItemStacks.fromId(item, "tiny_forager reward") }
        if (candidates.isEmpty()) return
        val stack = candidates[player.random.nextInt(candidates.size)]
        event.drops.add(ItemEntity(player.level(), event.pos.x + 0.5, event.pos.y + 0.5, event.pos.z + 0.5, stack.copy()))
    }

    fun onLivingDamage(event: LivingDamageEvent.Pre) {
        val attacker = event.source.entity as? ServerPlayer ?: return
        if (!isArthropodLike(event.entity)) return
        val bonus = RolePerks.configuredJobBonusPercent(attacker, "arthropod_damage_bonus").coerceAtLeast(0.0)
        if (bonus <= 0.0) return
        event.newDamage = (event.newDamage * (1.0 + bonus).toFloat()).coerceAtLeast(0.0f)
    }

    fun onPlayerTick(player: ServerPlayer) {
        applyWebWalker(player)
        applySwarmSense(player)
    }

    private fun applyWebWalker(player: ServerPlayer) {
        val retained = RolePerks.configuredJobMaxBonusPercent(player, "web_walker").coerceIn(0.0, 1.0)
        if (retained <= 0.0) {
            player.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(WEB_WALKER_MODIFIER)
            return
        }
        val sticky = stickyBlockUnderOrInside(player)
        val attribute = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        if (sticky == null) {
            attribute.removeModifier(WEB_WALKER_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                WEB_WALKER_MODIFIER,
                retained,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
        val movement = player.deltaMovement
        if (movement.horizontalDistanceSqr() > 0.0) player.deltaMovement = movement.multiply(1.0 + retained, 1.0, 1.0 + retained)
    }

    private fun applySwarmSense(player: ServerPlayer) {
        if (RolePerks.jobPerks(player, "swarm_sense").isEmpty()) return
        val level = player.level() as? ServerLevel ?: return
        val now = level.gameTime
        if (now % SWARM_SENSE_SCAN_INTERVAL_TICKS != 0L) return
        if (now < (swarmSenseCooldownUntilTicks[player.uuid] ?: 0L)) return
        val nearby = level.getEntitiesOfClass(Monster::class.java, player.boundingBox.inflate(SWARM_SENSE_RADIUS)).count { monster -> monster.isAlive && !monster.isRemoved }
        if (nearby < SWARM_SENSE_MIN_HOSTILES) return
        swarmSenseCooldownUntilTicks[player.uuid] = now + SWARM_SENSE_COOLDOWN_TICKS
        player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, SWARM_SENSE_DURATION_TICKS, 0, false, false, true))
    }

    private fun stickyBlockUnderOrInside(player: ServerPlayer): BlockState? {
        val level = player.level() as? ServerLevel ?: return null
        val box = player.boundingBox.inflate(0.05).expandTowards(0.0, -0.1, 0.0)
        for (x in floor(box.minX).toInt()..floor(box.maxX).toInt()) {
            for (y in floor(box.minY).toInt()..floor(box.maxY).toInt()) {
                for (z in floor(box.minZ).toInt()..floor(box.maxZ).toInt()) {
                    val state = level.getBlockState(BlockPos(x, y, z))
                    if (isStickyBlock(state)) return state
                }
            }
        }
        return null
    }

    private fun isTinyForagerBlock(state: BlockState): Boolean {
        if (state.`is`(BlockTags.LEAVES) || state.`is`(BlockTags.FLOWERS)) return true
        return state.`is`(Blocks.SHORT_GRASS) || state.`is`(Blocks.TALL_GRASS) || state.`is`(Blocks.FERN) || state.`is`(Blocks.LARGE_FERN)
    }

    private fun isStickyBlock(state: BlockState): Boolean {
        if (state.`is`(Blocks.COBWEB) || state.`is`(Blocks.HONEY_BLOCK) || state.`is`(Blocks.SLIME_BLOCK)) return true
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return id.contains("web") || id.contains("sticky")
    }

    private fun isArthropodLike(entity: Entity): Boolean {
        val id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).path
        return id in VANILLA_ARTHROPOD_IDS || id.contains("bug") || id.contains("insect") || id.contains("spider") || id.contains("wasp") || id.contains("beetle")
    }

    private val VANILLA_ARTHROPOD_IDS = setOf("spider", "cave_spider", "silverfish", "endermite", "bee")
    private const val SWARM_SENSE_RADIUS = 8.0
    private const val SWARM_SENSE_MIN_HOSTILES = 5
    private const val SWARM_SENSE_DURATION_TICKS = 120
    private const val SWARM_SENSE_COOLDOWN_TICKS = 900L
    private const val SWARM_SENSE_SCAN_INTERVAL_TICKS = 10L
    private val DEFAULT_TINY_FORAGER_POOL = listOf("minecraft:wheat_seeds", "minecraft:sweet_berries", "minecraft:string", "minecraft:spider_eye")
}
