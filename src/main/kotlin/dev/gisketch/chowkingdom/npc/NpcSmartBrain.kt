package dev.gisketch.chowkingdom.npc

import com.mojang.datafixers.util.Pair
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.tslat.smartbrainlib.api.core.BrainActivityGroup
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FloatToSurfaceOfFluid
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor
import java.util.UUID

object NpcSmartBrain {
    fun sensors(): List<ExtendedSensor<out ChowNpcEntity>> = emptyList()

    fun coreTasks(): BrainActivityGroup<ChowNpcEntity> = BrainActivityGroup.coreTasks(
        FloatToSurfaceOfFluid<ChowNpcEntity>(),
        LookAtTarget<ChowNpcEntity>(),
        MoveToWalkTarget<ChowNpcEntity>(),
    )

    fun idleTasks(): BrainActivityGroup<ChowNpcEntity> = BrainActivityGroup.idleTasks(
        NpcTownBrainBehaviour(),
    )

}

private class NpcTownBrainBehaviour : ExtendedBehaviour<ChowNpcEntity>() {
    private val tasks: List<NpcSmartBrainTask> = listOf(
        NpcCriticalOverrideTask,
        NpcFeatureTask("quest_claim", NpcFeature::tickSmartBrainQuestClaim),
        NpcFeatureTask("rent_contract_follow", NpcFeature::tickSmartBrainRentContractFollow),
        NpcFeatureTask("job_application_follow", NpcFeature::tickSmartBrainJobApplicationFollow),
        NpcFeatureTask("npc_micro_interaction", NpcFeature::tickSmartBrainNpcMicroInteraction),
        NpcFeatureTask("outgoing_gift", NpcFeature::tickSmartBrainOutgoingGift),
        NpcFeatureTask("quest_offer", NpcFeature::tickSmartBrainQuestOffer),
        NpcFeatureTask("greeting", NpcFeature::tickSmartBrainGreeting),
        NpcFeatureTask("talking_pause", NpcFeature::tickSmartBrainTalkingPause),
        NpcRoutineTask,
    )

    init {
        noTimeout()
    }

    override fun getMemoryRequirements(): List<Pair<MemoryModuleType<*>, MemoryStatus>> = emptyList()

    override fun checkExtraStartConditions(level: ServerLevel, entity: ChowNpcEntity): Boolean = true

    override fun tick(entity: ChowNpcEntity) {
        tasks.firstOrNull { task -> task.run(entity) }
    }

    override fun shouldKeepRunning(entity: ChowNpcEntity): Boolean = true
}

private interface NpcSmartBrainTask {
    val id: String
    fun run(entity: ChowNpcEntity): Boolean
}

private class NpcFeatureTask(
    override val id: String,
    private val action: (ChowNpcEntity) -> Boolean,
) : NpcSmartBrainTask {
    override fun run(entity: ChowNpcEntity): Boolean = action(entity)
}

private object NpcRoutineTask : NpcSmartBrainTask {
    override val id: String = "routine"

    override fun run(entity: ChowNpcEntity): Boolean {
        val definition = NpcFeature.smartBrainDefinition(entity) ?: return false
        val activity = NpcFeature.activityFor(entity, definition)
        entity.debugActivity = activity
        if (activity != "sleep" && entity.isSleeping) entity.stopSleeping()
        if (entity.tickCount % definition.jobDefinition.scanIntervalTicks != 0 || !entity.navigation.isDone) return true
        NpcFeature.moveToActivityTarget(entity, definition, activity)
        return true
    }
}

private object NpcCriticalOverrideTask : NpcSmartBrainTask {
    override val id: String = "critical_override"

    override fun run(entity: ChowNpcEntity): Boolean = NpcSmartBrainOverrides.tick(entity)
}

object NpcSmartBrainOverrides {
    private val active: MutableMap<UUID, ActiveOverride> = linkedMapOf()

    fun tick(entity: ChowNpcEntity): Boolean {
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
        val snapshot = NpcCustomAnimationController.snapshot(entity)
        val override = ActiveOverride(
            type = NpcSmartBrainOverrideType.ATTACK_BACK,
            untilTick = entity.tickCount + ATTACK_BACK_MAX_TICKS,
            targetId = player.uuid,
            attacksRemaining = 2,
            nextActionTick = entity.tickCount,
            animationSnapshot = snapshot,
        )
        active[entity.uuid] = override
        playTemplate(entity, override, NpcAnimationTemplates.RUN_WITH_SWORD)
    }

    private fun startRunAway(entity: ChowNpcEntity, threatPos: Vec3, goal: String) {
        if (entity.isSleeping) entity.stopSleeping()
        active[entity.uuid] = ActiveOverride(NpcSmartBrainOverrideType.RUN_AWAY, entity.tickCount + RUN_AWAY_TICKS, threatPos = threatPos, goal = goal)
        moveAway(entity, threatPos)
    }

    private fun tickActive(entity: ChowNpcEntity, override: ActiveOverride): Boolean {
        if (entity.tickCount > override.untilTick || !entity.isAlive) return false
        return when (override.type) {
            NpcSmartBrainOverrideType.ATTACK_BACK -> tickAttackBack(entity, override)
            NpcSmartBrainOverrideType.RUN_AWAY -> tickRunAway(entity, override)
        }
    }

    private fun tickAttackBack(entity: ChowNpcEntity, override: ActiveOverride): Boolean {
        val level = entity.level() as? ServerLevel ?: return false
        val player = override.targetId?.let(level::getPlayerByUUID) as? ServerPlayer ?: return false
        if (!player.isAlive) return false
        if (override.phase == NpcSmartBrainOverridePhase.ATTACKING) return tickAttackAnimation(entity, player, override)
        if (override.attacksRemaining <= 0) return entity.tickCount < override.nextActionTick
        entity.debugActivity = "override"
        entity.debugGoal = "attack_back"
        entity.debugTargetPos = player.blockPosition().immutable()
        entity.lookControl.setLookAt(player, 30.0f, 30.0f)
        entity.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition())
        if (entity.distanceToSqr(player) > ATTACK_START_DISTANCE_SQR) {
            playTemplate(entity, override, NpcAnimationTemplates.RUN_WITH_SWORD)
            moveTowardTarget(entity, player)
            return true
        }
        entity.navigation.stop()
        if (entity.tickCount >= override.nextActionTick) {
            startAttackAnimation(entity, override)
        }
        return true
    }

    private fun startAttackAnimation(entity: ChowNpcEntity, override: ActiveOverride) {
        playTemplate(entity, override, NpcAnimationTemplates.ATTACK_SWORD, forceRestart = true)
        override.phase = NpcSmartBrainOverridePhase.ATTACKING
        override.phaseStartedTick = entity.tickCount
        override.firedEvents.clear()
    }

    private fun tickAttackAnimation(entity: ChowNpcEntity, player: ServerPlayer, override: ActiveOverride): Boolean {
        entity.debugActivity = "override"
        entity.debugGoal = "attack_swing"
        entity.debugTargetPos = player.blockPosition().immutable()
        entity.lookControl.setLookAt(player, 30.0f, 30.0f)
        entity.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition())
        val template = NpcAnimationTemplates.ATTACK_SWORD
        val elapsed = entity.tickCount - override.phaseStartedTick
        template.events.forEach { event ->
            if (elapsed >= event.tick && override.firedEvents.add(event)) {
                when (event.type) {
                    NpcAnimationEventType.ATTACK_HIT -> attackHit(entity, player)
                }
            }
        }
        if (elapsed < template.durationTicks) return true
        override.attacksRemaining -= 1
        override.phase = NpcSmartBrainOverridePhase.CHASING
        override.nextActionTick = entity.tickCount + if (override.attacksRemaining <= 0) ATTACK_FINISH_HOLD_TICKS else ATTACK_COOLDOWN_TICKS
        if (override.attacksRemaining > 0) playTemplate(entity, override, NpcAnimationTemplates.RUN_WITH_SWORD)
        return true
    }

    private fun attackHit(entity: ChowNpcEntity, player: ServerPlayer) {
        val distanceSqr = entity.distanceToSqr(player)
        if (distanceSqr > ATTACK_HIT_DISTANCE_SQR) {
            entity.debugGoal = "attack_miss_range_${"%.2f".format(kotlin.math.sqrt(distanceSqr))}"
            return
        }
        val level = entity.level() as? ServerLevel ?: return
        entity.swing(InteractionHand.MAIN_HAND, true)
        level.broadcastEntityEvent(entity, ATTACK_ANIMATION_EVENT)
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0f, 0.9f + entity.random.nextFloat() * 0.2f)
        applyAttackDamage(entity, player)
        player.knockback(ATTACK_KNOCKBACK, entity.x - player.x, entity.z - player.z)
        player.hurtMarked = true
    }

    private fun applyAttackDamage(entity: ChowNpcEntity, player: ServerPlayer) {
        if (player.abilities.invulnerable) return
        player.invulnerableTime = 0
        val before = player.health
        val source = entity.damageSources().mobAttack(entity)
        val damaged = player.hurt(source, ATTACK_DAMAGE) || player.hurt(player.damageSources().generic(), ATTACK_DAMAGE)
        if (!damaged && player.health >= before) forceHealthDamage(player, source)
    }

    private fun forceHealthDamage(player: ServerPlayer, source: DamageSource) {
        val newHealth = (player.health - ATTACK_DAMAGE).coerceAtLeast(0.0f)
        player.setHealth(newHealth)
        player.hurtDuration = 10
        player.hurtTime = player.hurtDuration
        if (newHealth <= 0.0f) player.die(source)
    }

    private fun playTemplate(entity: ChowNpcEntity, override: ActiveOverride, template: NpcAnimationTemplate, forceRestart: Boolean = false) {
        if (!forceRestart && override.activeTemplateId == template.id) return
        NpcCustomAnimationController.play(entity, template)
        override.activeTemplateId = template.id
    }

    private fun moveTowardTarget(entity: ChowNpcEntity, player: ServerPlayer) {
        entity.navigation.moveTo(player.x, player.y, player.z, ATTACK_CHASE_SPEED)
        if (entity.navigation.isDone) {
            entity.moveControl.setWantedPosition(player.x, player.y, player.z, ATTACK_CHASE_SPEED)
        }
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
        val override = active.remove(entity.uuid)
        entity.navigation.stop()
        if (override?.type == NpcSmartBrainOverrideType.ATTACK_BACK) {
            override.animationSnapshot?.let { snapshot -> NpcCustomAnimationController.restore(entity, snapshot) }
        }
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
        val type: NpcSmartBrainOverrideType,
        val untilTick: Int,
        val targetId: UUID? = null,
        val threatPos: Vec3? = null,
        val goal: String = type.id,
        var attacksRemaining: Int = 0,
        var nextActionTick: Int = 0,
        val animationSnapshot: NpcAnimationSnapshot? = null,
        var phase: NpcSmartBrainOverridePhase = NpcSmartBrainOverridePhase.CHASING,
        var phaseStartedTick: Int = 0,
        var activeTemplateId: String = "",
        val firedEvents: MutableSet<NpcAnimationEvent> = mutableSetOf(),
    )

    private enum class NpcSmartBrainOverridePhase {
        CHASING,
        ATTACKING,
    }

    private enum class NpcSmartBrainOverrideType(val id: String) {
        ATTACK_BACK("attack_back"),
        RUN_AWAY("run_away"),
    }

    private const val RUN_AWAY_TICKS = 60
    private const val RUN_REPATH_TICKS = 10
    private const val RUN_AWAY_DISTANCE = 7.0
    private const val RUN_AWAY_SPEED = 1.25
    private const val ATTACK_BACK_MAX_TICKS = 160
    private const val ATTACK_START_DISTANCE_SQR = 4.0
    private const val ATTACK_HIT_DISTANCE_SQR = 5.0625
    private const val ATTACK_CHASE_SPEED = 1.25
    private const val ATTACK_DAMAGE = 4.0f
    private const val ATTACK_KNOCKBACK = 0.35
    private const val ATTACK_COOLDOWN_TICKS = 13
    private const val ATTACK_FINISH_HOLD_TICKS = 12
    private const val ATTACK_ANIMATION_EVENT: Byte = 4
}
