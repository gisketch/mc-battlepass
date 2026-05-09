package dev.gisketch.chowkingdom.npc

import com.mojang.datafixers.util.Pair
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
    private val weapons = listOf(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.WOODEN_AXE, Items.STONE_AXE)

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
        entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(weapons[entity.random.nextInt(weapons.size)]))
        active[entity.uuid] = ActiveOverride(NpcSmartBrainOverrideType.ATTACK_BACK, entity.tickCount + ATTACK_BACK_MAX_TICKS, player.uuid, attacksRemaining = 2)
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
        val type: NpcSmartBrainOverrideType,
        val untilTick: Int,
        val targetId: UUID? = null,
        val threatPos: Vec3? = null,
        val goal: String = type.id,
        var attacksRemaining: Int = 0,
        var nextActionTick: Int = 0,
    )

    private enum class NpcSmartBrainOverrideType(val id: String) {
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