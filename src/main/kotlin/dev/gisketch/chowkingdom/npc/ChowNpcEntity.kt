package dev.gisketch.chowkingdom.npc

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.util.UUID

class ChowNpcEntity(entityType: EntityType<out PathfinderMob>, level: Level) : PathfinderMob(entityType, level) {
    var npcId: String
        get() = entityData.get(NPC_ID_DATA)
        set(value) = entityData.set(NPC_ID_DATA, value.trim().ifBlank { "finn" })
    var bodyType: String
        get() = entityData.get(BODY_TYPE_DATA)
        set(value) = entityData.set(BODY_TYPE_DATA, NpcBodyTypes.normalize(value))
    var scriptedAttackTicks: Int
        get() = entityData.get(SCRIPTED_ATTACK_TICKS_DATA)
        private set(value) = entityData.set(SCRIPTED_ATTACK_TICKS_DATA, value.coerceAtLeast(0))
    var campPos: BlockPos? = null
    var homePos: BlockPos? = null
    var debugActivity: String = "idle"
    var debugGoal: String = "idle"
    var debugTargetPos: BlockPos? = null
    private var talkingTarget: UUID? = null
    private var talkingUntilTick: Int = 0

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(NPC_ID_DATA, "finn")
        builder.define(BODY_TYPE_DATA, NpcBodyTypes.NORMAL)
        builder.define(SCRIPTED_ATTACK_TICKS_DATA, 0)
    }

    fun configure(definition: NpcDefinition, camp: BlockPos) {
        npcId = definition.id
        bodyType = definition.bodyType
        campPos = camp.immutable()
        homePos = NpcStore.homePos(definition.id)
        customName = Component.literal(definition.displayName())
        isCustomNameVisible = true
        setPersistenceRequired()
    }

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS
        if (!level().isClientSide && player is ServerPlayer) NpcFeature.interact(player, this)
        return InteractionResult.sidedSuccess(level().isClientSide)
    }

    fun startTalkingTo(player: ServerPlayer, durationTicks: Int) {
        talkingTarget = player.uuid
        talkingUntilTick = tickCount + durationTicks
        debugActivity = "dialog"
        debugGoal = "talking"
        debugTargetPos = player.blockPosition().immutable()
        navigation.stop()
        lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition())
    }

    fun continueTalkingTo(player: ServerPlayer, durationTicks: Int) {
        if (talkingTarget != player.uuid) return
        talkingUntilTick = talkingUntilTick.coerceAtLeast(tickCount + durationTicks)
        navigation.stop()
    }

    fun stopTalkingTo(player: ServerPlayer) {
        if (talkingTarget != player.uuid) return
        talkingTarget = null
        talkingUntilTick = 0
    }

    fun isTalking(): Boolean = talkingTarget != null && tickCount <= talkingUntilTick

    fun startScriptedAttackAnimation() {
        scriptedAttackTicks = SCRIPTED_ATTACK_ANIMATION_TICKS
    }

    override fun aiStep() {
        super.aiStep()
        if (scriptedAttackTicks > 0) scriptedAttackTicks = scriptedAttackTicks - 1
        if (!level().isClientSide) tickTalking()
        if (!level().isClientSide) NpcFeature.tickNpc(this)
    }

    private fun tickTalking() {
        if (!isTalking()) {
            talkingTarget = null
            return
        }
        val targetId = talkingTarget ?: return
        val target = (level() as? ServerLevel)?.getPlayerByUUID(targetId) ?: run {
            talkingTarget = null
            return
        }
        navigation.stop()
        lookControl.setLookAt(target, 30.0f, 30.0f)
        lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition())
        debugActivity = "dialog"
        debugGoal = "talking"
        debugTargetPos = target.blockPosition().immutable()
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putString(NPC_ID_TAG, npcId)
        tag.putString(BODY_TYPE_TAG, bodyType)
        campPos?.let { pos -> tag.putLong(CAMP_POS_TAG, pos.asLong()) }
        homePos?.let { pos -> tag.putLong(HOME_POS_TAG, pos.asLong()) }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        npcId = tag.getString(NPC_ID_TAG).ifBlank { "finn" }
        bodyType = tag.getString(BODY_TYPE_TAG).ifBlank { NpcBodyTypes.NORMAL }
        if (tag.contains(CAMP_POS_TAG)) campPos = BlockPos.of(tag.getLong(CAMP_POS_TAG))
        if (tag.contains(HOME_POS_TAG)) homePos = BlockPos.of(tag.getLong(HOME_POS_TAG))
        setPersistenceRequired()
    }

    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false

    companion object {
        private val NPC_ID_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val BODY_TYPE_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val SCRIPTED_ATTACK_TICKS_DATA: EntityDataAccessor<Int> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.INT)
        private const val NPC_ID_TAG = "NpcId"
        private const val BODY_TYPE_TAG = "BodyType"
        private const val CAMP_POS_TAG = "CampPos"
        private const val HOME_POS_TAG = "HomePos"
        private const val SCRIPTED_ATTACK_ANIMATION_TICKS = 9
    }
}
