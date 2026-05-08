package dev.gisketch.chowkingdom.npc

import net.minecraft.core.BlockPos
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import java.util.Locale
import java.util.UUID

interface NpcJob {
    val id: String
    fun tick(entity: ChowNpcEntity, definition: NpcDefinition)
}

object NpcJobs {
    private val jobs: MutableMap<String, NpcJob> = linkedMapOf()

    init {
        register(AdventurerNpcJob)
        register("warrior", AdventurerNpcJob)
        register("fashionista", AdventurerNpcJob)
        register("professor", AdventurerNpcJob)
    }

    fun tick(entity: ChowNpcEntity, definition: NpcDefinition) {
        jobs[definition.job]?.tick(entity, definition) ?: AdventurerNpcJob.tick(entity, definition)
    }

    fun normalizeId(value: String): String {
        val id = value.trim().lowercase(Locale.ROOT).ifBlank { AdventurerNpcJob.id }
        return if (id in jobs) id else AdventurerNpcJob.id
    }

    private fun register(job: NpcJob) {
        jobs[job.id] = job
    }

    private fun register(id: String, job: NpcJob) {
        jobs[id] = job
    }
}

object NpcBrain {
    fun tick(entity: ChowNpcEntity, definition: NpcDefinition) {
        val activity = NpcTime.activityAt(definition.schedule, entity.level())
        entity.debugActivity = activity
        if (activity != "sleep" && entity.isSleeping) entity.stopSleeping()
        if (entity.tickCount % definition.jobDefinition.scanIntervalTicks != 0 || !entity.navigation.isDone) return
        NpcFeature.moveToActivityTarget(entity, definition, activity)
    }
}

object NpcBrainOverrides {
    private val active: MutableMap<UUID, ActiveOverride> = linkedMapOf()
    private val weapons = listOf(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.WOODEN_AXE, Items.STONE_AXE)

    fun tick(entity: ChowNpcEntity, definition: NpcDefinition): Boolean {
        active[entity.uuid]?.let { override ->
            if (tickActive(entity, override)) return true
            finish(entity)
        }
        val hazardPos = hazardPos(entity) ?: return false
        startRunAway(entity, hazardPos.center, "avoid_fire")
        return active[entity.uuid]?.let { override -> tickActive(entity, override) } ?: false
    }

    fun startHurtResponse(entity: ChowNpcEntity, player: ServerPlayer) {
        startAttackBack(entity, player)
    }

    fun clear(entity: ChowNpcEntity) {
        finish(entity)
    }

    private fun startAttackBack(entity: ChowNpcEntity, player: ServerPlayer) {
        if (entity.isSleeping) entity.stopSleeping()
        entity.navigation.stop()
        entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(weapons[entity.random.nextInt(weapons.size)]))
        active[entity.uuid] = ActiveOverride(NpcBrainOverrideType.ATTACK_BACK, entity.tickCount + ATTACK_BACK_MAX_TICKS, player.uuid, attacksRemaining = 2)
    }

    private fun startRunAway(entity: ChowNpcEntity, threatPos: Vec3, goal: String) {
        if (entity.isSleeping) entity.stopSleeping()
        active[entity.uuid] = ActiveOverride(NpcBrainOverrideType.RUN_AWAY, entity.tickCount + RUN_AWAY_TICKS, threatPos = threatPos, goal = goal)
        moveAway(entity, threatPos)
    }

    private fun tickActive(entity: ChowNpcEntity, override: ActiveOverride): Boolean {
        if (entity.tickCount > override.untilTick || !entity.isAlive) return false
        return when (override.type) {
            NpcBrainOverrideType.ATTACK_BACK -> tickAttackBack(entity, override)
            NpcBrainOverrideType.RUN_AWAY -> tickRunAway(entity, override)
        }
    }

    private fun tickAttackBack(entity: ChowNpcEntity, override: ActiveOverride): Boolean {
        val level = entity.level() as? ServerLevel ?: return false
        val player = override.targetId?.let(level::getPlayerByUUID) as? ServerPlayer ?: return false
        if (!player.isAlive) return false
        if (override.attacksRemaining <= 0) return entity.tickCount < override.nextActionTick
        entity.debugActivity = "override"
        entity.debugGoal = "attack_back"
        entity.debugTargetPos = player.blockPosition().immutable()
        entity.lookControl.setLookAt(player, 30.0f, 30.0f)
        entity.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition())
        if (entity.distanceToSqr(player) > ATTACK_REACH_DISTANCE_SQR) {
            entity.navigation.moveTo(player, ATTACK_CHASE_SPEED)
            return true
        }
        entity.navigation.stop()
        if (entity.tickCount >= override.nextActionTick) {
            scriptedAttack(entity, player)
            override.attacksRemaining -= 1
            override.nextActionTick = entity.tickCount + if (override.attacksRemaining <= 0) ATTACK_FINISH_HOLD_TICKS else ATTACK_COOLDOWN_TICKS
        }
        return true
    }

    private fun scriptedAttack(entity: ChowNpcEntity, player: ServerPlayer) {
        val level = entity.level() as? ServerLevel ?: return
        entity.startScriptedAttackAnimation()
        entity.swing(InteractionHand.MAIN_HAND, true)
        level.broadcastEntityEvent(entity, ATTACK_ANIMATION_EVENT)
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0f, 0.9f + entity.random.nextFloat() * 0.2f)
        player.invulnerableTime = 0
        val damaged = player.hurt(entity.damageSources().mobAttack(entity), ATTACK_DAMAGE)
        if (!damaged && !player.abilities.invulnerable) player.hurt(player.damageSources().generic(), ATTACK_DAMAGE)
        player.knockback(ATTACK_KNOCKBACK, entity.x - player.x, entity.z - player.z)
    }

    private fun tickRunAway(entity: ChowNpcEntity, override: ActiveOverride): Boolean {
        val threatPos = override.threatPos ?: return false
        entity.debugActivity = "override"
        entity.debugGoal = override.goal
        if (entity.tickCount >= override.nextActionTick || entity.navigation.isDone) {
            moveAway(entity, threatPos)
            override.nextActionTick = entity.tickCount + RUN_REPATH_TICKS
        }
        return true
    }

    private fun moveAway(entity: ChowNpcEntity, threatPos: Vec3) {
        val away = entity.position().subtract(threatPos).let { vector ->
            if (vector.horizontalDistanceSqr() < 0.01) Vec3(entity.random.nextDouble() - 0.5, 0.0, entity.random.nextDouble() - 0.5) else vector
        }.normalize()
        val target = entity.position().add(away.x * RUN_AWAY_DISTANCE, 0.0, away.z * RUN_AWAY_DISTANCE)
        val targetPos = BlockPos.containing(target.x, entity.y, target.z)
        entity.debugTargetPos = targetPos.immutable()
        entity.navigation.moveTo(target.x, entity.y, target.z, RUN_AWAY_SPEED)
    }

    private fun finish(entity: ChowNpcEntity) {
        active.remove(entity.uuid)
        entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
    }

    private fun hazardPos(entity: ChowNpcEntity): BlockPos? {
        val level = entity.level()
        val pos = entity.blockPosition()
        return listOf(pos, pos.below()).firstOrNull { candidate ->
            val block = level.getBlockState(candidate).block
            block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE
        }
    }

    private class ActiveOverride(
        val type: NpcBrainOverrideType,
        val untilTick: Int,
        val targetId: UUID? = null,
        val threatPos: Vec3? = null,
        val goal: String = type.id,
        var attacksRemaining: Int = 0,
        var nextActionTick: Int = 0,
    )

    private enum class NpcBrainOverrideType(val id: String) {
        ATTACK_BACK("attack_back"),
        RUN_AWAY("run_away"),
    }

    private const val RUN_AWAY_TICKS = 60
    private const val RUN_REPATH_TICKS = 10
    private const val RUN_AWAY_DISTANCE = 7.0
    private const val RUN_AWAY_SPEED = 1.25
    private const val ATTACK_BACK_MAX_TICKS = 160
    private const val ATTACK_REACH_DISTANCE_SQR = 25.0
    private const val ATTACK_CHASE_SPEED = 1.05
    private const val ATTACK_DAMAGE = 4.0f
    private const val ATTACK_KNOCKBACK = 0.35
    private const val ATTACK_COOLDOWN_TICKS = 13
    private const val ATTACK_FINISH_HOLD_TICKS = 12
    private const val ATTACK_ANIMATION_EVENT: Byte = 4
}

private object AdventurerNpcJob : NpcJob {
    override val id: String = "adventurer"

    override fun tick(entity: ChowNpcEntity, definition: NpcDefinition) {
        if (entity.tickCount % 60 != 0 || !entity.navigation.isDone) return
        NpcFeature.moveToActivityTarget(entity, definition, "work")
    }
}
