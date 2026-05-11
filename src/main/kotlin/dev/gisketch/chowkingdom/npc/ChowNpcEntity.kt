package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.compat.PehkuiScaleBridge
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
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.tslat.smartbrainlib.api.SmartBrainOwner
import net.tslat.smartbrainlib.api.core.BrainActivityGroup
import net.tslat.smartbrainlib.api.core.SmartBrainProvider
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID

class ChowNpcEntity(entityType: EntityType<out PathfinderMob>, level: Level) : PathfinderMob(entityType, level), SmartBrainOwner<ChowNpcEntity>, GeoEntity {
    private val animatableCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    private var cachedCustomAnimationKey: String = ""
    private var cachedCustomAnimationLoop: Boolean = true
    private var cachedCustomAnimation: RawAnimation = IDLE_ANIMATION
    private var customAnimationEndsAtTick: Int = 0
    private var observedCustomAnimationPlayId: Int = 0

    var npcId: String
        get() = entityData.get(NPC_ID_DATA)
        set(value) = entityData.set(NPC_ID_DATA, value.trim().ifBlank { "finn" })
    var bodyType: String
        get() = entityData.get(BODY_TYPE_DATA)
        set(value) = entityData.set(BODY_TYPE_DATA, NpcBodyTypes.normalize(value))
    var customAnimation: Boolean
        get() = entityData.get(CUSTOM_ANIMATION_DATA)
        private set(value) = entityData.set(CUSTOM_ANIMATION_DATA, value)
    var customAnimationKey: String
        get() = entityData.get(CUSTOM_ANIMATION_KEY_DATA)
        private set(value) = entityData.set(CUSTOM_ANIMATION_KEY_DATA, value.trim().lowercase())
    var customAnimationSpeed: Float
        get() = entityData.get(CUSTOM_ANIMATION_SPEED_DATA)
        private set(value) = entityData.set(CUSTOM_ANIMATION_SPEED_DATA, value.coerceIn(0.1f, 4.0f))
    private var customAnimationPlayId: Int
        get() = entityData.get(CUSTOM_ANIMATION_PLAY_ID_DATA)
        set(value) = entityData.set(CUSTOM_ANIMATION_PLAY_ID_DATA, value)
    var scriptedAttackTicks: Int
        get() = entityData.get(SCRIPTED_ATTACK_TICKS_DATA)
        private set(value) = entityData.set(SCRIPTED_ATTACK_TICKS_DATA, value.coerceAtLeast(0))
    var passThroughInteractions: Boolean
        get() = entityData.get(PASS_THROUGH_INTERACTIONS_DATA)
        private set(value) = entityData.set(PASS_THROUGH_INTERACTIONS_DATA, value)
    var heldItemDebugRotX: Float
        get() = entityData.get(HELD_ITEM_DEBUG_ROT_X_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_ROT_X_DATA, value)
    var heldItemDebugRotY: Float
        get() = entityData.get(HELD_ITEM_DEBUG_ROT_Y_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_ROT_Y_DATA, value)
    var heldItemDebugRotZ: Float
        get() = entityData.get(HELD_ITEM_DEBUG_ROT_Z_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_ROT_Z_DATA, value)
    var heldItemDebugRotOrder: String
        get() = entityData.get(HELD_ITEM_DEBUG_ROT_ORDER_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_ROT_ORDER_DATA, value)
    var heldItemDebugRotSpace: String
        get() = entityData.get(HELD_ITEM_DEBUG_ROT_SPACE_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_ROT_SPACE_DATA, value)
    var heldItemDebugPosX: Float
        get() = entityData.get(HELD_ITEM_DEBUG_POS_X_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_POS_X_DATA, value)
    var heldItemDebugPosY: Float
        get() = entityData.get(HELD_ITEM_DEBUG_POS_Y_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_POS_Y_DATA, value)
    var heldItemDebugPosZ: Float
        get() = entityData.get(HELD_ITEM_DEBUG_POS_Z_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_POS_Z_DATA, value)
    var heldItemDebugScale: Float
        get() = entityData.get(HELD_ITEM_DEBUG_SCALE_DATA)
        private set(value) = entityData.set(HELD_ITEM_DEBUG_SCALE_DATA, value)
    var campPos: BlockPos? = null
    var homePos: BlockPos? = null
    var debugActivity: String = "idle"
    var debugGoal: String = "idle"
    var debugTargetPos: BlockPos? = null
    private var talkingTarget: UUID? = null
    private var talkingUntilTick: Int = 0

    override fun registerGoals() = Unit

    override fun brainProvider(): Brain.Provider<*> = SmartBrainProvider(this)

    override fun getSensors(): List<ExtendedSensor<out ChowNpcEntity>> = NpcSmartBrain.sensors()

    override fun getCoreTasks(): BrainActivityGroup<out ChowNpcEntity> = NpcSmartBrain.coreTasks()

    override fun getIdleTasks(): BrainActivityGroup<out ChowNpcEntity> = NpcSmartBrain.idleTasks()

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(NPC_ID_DATA, "finn")
        builder.define(BODY_TYPE_DATA, NpcBodyTypes.NORMAL)
        builder.define(CUSTOM_ANIMATION_DATA, false)
        builder.define(CUSTOM_ANIMATION_KEY_DATA, "")
        builder.define(CUSTOM_ANIMATION_SPEED_DATA, 1.0f)
        builder.define(CUSTOM_ANIMATION_PLAY_ID_DATA, 0)
        builder.define(SCRIPTED_ATTACK_TICKS_DATA, 0)
        builder.define(PASS_THROUGH_INTERACTIONS_DATA, false)
        builder.define(HELD_ITEM_DEBUG_ROT_X_DATA, 0.0f)
        builder.define(HELD_ITEM_DEBUG_ROT_Y_DATA, 0.0f)
        builder.define(HELD_ITEM_DEBUG_ROT_Z_DATA, 0.0f)
        builder.define(HELD_ITEM_DEBUG_ROT_ORDER_DATA, "xyz")
        builder.define(HELD_ITEM_DEBUG_ROT_SPACE_DATA, "item")
        builder.define(HELD_ITEM_DEBUG_POS_X_DATA, 0.0f)
        builder.define(HELD_ITEM_DEBUG_POS_Y_DATA, 0.0f)
        builder.define(HELD_ITEM_DEBUG_POS_Z_DATA, 0.0f)
        builder.define(HELD_ITEM_DEBUG_SCALE_DATA, 1.0f)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "custom", 0) { state ->
            if (!customAnimation) return@AnimationController PlayState.STOP
            val playId = customAnimationPlayId
            if (playId != observedCustomAnimationPlayId) {
                observedCustomAnimationPlayId = playId
                state.controller.forceAnimationReset()
            }
            state.controller.setAnimationSpeed(customAnimationSpeed.toDouble())
            if (customAnimationKey.isBlank()) PlayState.STOP else state.setAndContinue(customRawAnimation())
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animatableCache

    fun configure(definition: NpcDefinition, camp: BlockPos) {
        npcId = definition.id
        bodyType = definition.bodyType
        campPos = camp.immutable()
        homePos = NpcStore.homePos(definition.id)
        customAnimation = definition.customAnimation
        customName = Component.literal(definition.displayName())
        isCustomNameVisible = true
        PehkuiScaleBridge.apply(this, definition.bodyScale())
        setPersistenceRequired()
    }

    fun configureAnimationDebug(camp: BlockPos) {
        npcId = ANIMATION_DEBUG_NPC_ID
        bodyType = NpcBodyTypes.NORMAL
        campPos = camp.immutable()
        homePos = null
        customName = Component.literal("Animation Debug Steve")
        isCustomNameVisible = true
        setCustomAnimationMode(false)
    }

    fun setCustomAnimationMode(enabled: Boolean) {
        customAnimation = enabled
        if (!enabled) {
            customAnimationKey = ""
            customAnimationSpeed = 1.0f
            customAnimationEndsAtTick = 0
            customAnimationPlayId = customAnimationPlayId + 1
        }
    }

    fun playCustomIdleAnimation() {
        playCustomAnimation(CUSTOM_ANIMATION_IDLE)
    }

    fun playCustomWalkAnimation() {
        playCustomAnimation(CUSTOM_ANIMATION_WALK)
    }

    fun playCustomAttackAnimation() {
        playCustomAnimation(CUSTOM_ANIMATION_ATTACK)
    }

    fun playCustomAnimation(animationId: String, speed: Float = 1.0f): Boolean {
        val animation = NpcAnimationRegistry.resolve(animationId) ?: return false
        customAnimation = true
        customAnimationKey = animation.id
        customAnimationSpeed = speed
        customAnimationEndsAtTick = if (animation.loop) 0 else tickCount + animation.durationTicks()
        customAnimationPlayId = customAnimationPlayId + 1
        return true
    }

    fun restoreCustomAnimation(enabled: Boolean, animationKey: String, speed: Float = 1.0f) {
        customAnimation = enabled
        customAnimationKey = if (enabled) animationKey else ""
        customAnimationSpeed = if (enabled) speed else 1.0f
        customAnimationEndsAtTick = 0
        customAnimationPlayId = customAnimationPlayId + 1
    }

    fun setHeldItemDebugRotation(x: Float, y: Float, z: Float) {
        heldItemDebugRotX = x
        heldItemDebugRotY = y
        heldItemDebugRotZ = z
    }

    fun setHeldItemDebugPosition(x: Float, y: Float, z: Float) {
        heldItemDebugPosX = x
        heldItemDebugPosY = y
        heldItemDebugPosZ = z
    }

    fun updateHeldItemDebugScale(scale: Float) {
        heldItemDebugScale = scale.coerceIn(0.05f, 5.0f)
    }

    fun resetHeldItemDebugTransform() {
        setHeldItemDebugRotation(0.0f, 0.0f, 0.0f)
        setHeldItemDebugPosition(0.0f, 0.0f, 0.0f)
        updateHeldItemDebugScale(1.0f)
    }

    fun updatePassThroughInteractions(enabled: Boolean) {
        passThroughInteractions = enabled
    }

    fun setHeldItemDebugRotationOrder(order: String): Boolean {
        val normalized = order.trim().lowercase()
        if (normalized.length != 3 || normalized.toSet() != setOf('x', 'y', 'z')) return false
        heldItemDebugRotOrder = normalized
        return true
    }

    fun setHeldItemDebugRotationSpace(space: String): Boolean {
        val normalized = space.trim().lowercase()
        if (normalized != "item" && normalized != "socket") return false
        heldItemDebugRotSpace = normalized
        return true
    }

    private fun customRawAnimation(): RawAnimation {
        val animationKey = customAnimationKey
        val loop = NpcAnimationRegistry.isLooping(animationKey)
        if (animationKey != cachedCustomAnimationKey || loop != cachedCustomAnimationLoop) {
            cachedCustomAnimationKey = animationKey
            cachedCustomAnimationLoop = loop
            cachedCustomAnimation = if (loop) RawAnimation.begin().thenLoop(animationKey) else RawAnimation.begin().thenPlay(animationKey)
        }
        return cachedCustomAnimation
    }

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS
        if (passThroughInteractions) return InteractionResult.PASS
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

    fun isTalking(): Boolean {
        if (talkingTarget == null) return false
        if (tickCount <= talkingUntilTick) return true
        talkingTarget = null
        talkingUntilTick = 0
        return false
    }

    fun startScriptedAttackAnimation() {
        scriptedAttackTicks = SCRIPTED_ATTACK_ANIMATION_TICKS
    }

    override fun aiStep() {
        super.aiStep()
        if (scriptedAttackTicks > 0) scriptedAttackTicks = scriptedAttackTicks - 1
        if (!level().isClientSide) NpcBossFights.tickResultProtection(this)
        if (!level().isClientSide) tickTalking()
        if (!level().isClientSide) tickCustomAnimationReturn()
    }

    private fun tickCustomAnimationReturn() {
        if (!customAnimation || customAnimationEndsAtTick == 0 || tickCount < customAnimationEndsAtTick) return
        customAnimationEndsAtTick = 0
        customAnimationKey = (NpcAnimationRegistry.resolve(CUSTOM_ANIMATION_IDLE) ?: NpcAnimationRegistry.firstLooping())?.id.orEmpty()
        customAnimationPlayId = customAnimationPlayId + 1
    }

    override fun customServerAiStep() {
        if (NpcFeature.prepareSmartBrainTick(this)) tickBrain(this)
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
        tag.putBoolean(CUSTOM_ANIMATION_TAG, customAnimation)
        tag.putString(CUSTOM_ANIMATION_KEY_TAG, customAnimationKey)
        campPos?.let { pos -> tag.putLong(CAMP_POS_TAG, pos.asLong()) }
        homePos?.let { pos -> tag.putLong(HOME_POS_TAG, pos.asLong()) }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        npcId = tag.getString(NPC_ID_TAG).ifBlank { "finn" }
        bodyType = tag.getString(BODY_TYPE_TAG).ifBlank { NpcBodyTypes.NORMAL }
        customAnimation = tag.getBoolean(CUSTOM_ANIMATION_TAG)
        customAnimationKey = tag.getString(CUSTOM_ANIMATION_KEY_TAG)
        if (tag.contains(CAMP_POS_TAG)) campPos = BlockPos.of(tag.getLong(CAMP_POS_TAG))
        if (tag.contains(HOME_POS_TAG)) homePos = BlockPos.of(tag.getLong(HOME_POS_TAG))
        setPersistenceRequired()
    }

    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false

    companion object {
        private val NPC_ID_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val BODY_TYPE_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val CUSTOM_ANIMATION_DATA: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val CUSTOM_ANIMATION_KEY_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val CUSTOM_ANIMATION_SPEED_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val CUSTOM_ANIMATION_PLAY_ID_DATA: EntityDataAccessor<Int> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.INT)
        private val SCRIPTED_ATTACK_TICKS_DATA: EntityDataAccessor<Int> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.INT)
        private val PASS_THROUGH_INTERACTIONS_DATA: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val HELD_ITEM_DEBUG_ROT_X_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val HELD_ITEM_DEBUG_ROT_Y_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val HELD_ITEM_DEBUG_ROT_Z_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val HELD_ITEM_DEBUG_ROT_ORDER_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val HELD_ITEM_DEBUG_ROT_SPACE_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val HELD_ITEM_DEBUG_POS_X_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val HELD_ITEM_DEBUG_POS_Y_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val HELD_ITEM_DEBUG_POS_Z_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val HELD_ITEM_DEBUG_SCALE_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        const val ANIMATION_DEBUG_NPC_ID = "animation_debug_steve"
        const val CUSTOM_ANIMATION_IDLE = "idle"
        const val CUSTOM_ANIMATION_WALK = "walk"
        const val CUSTOM_ANIMATION_ATTACK = "attack"
        private const val NPC_ID_TAG = "NpcId"
        private const val BODY_TYPE_TAG = "BodyType"
        private const val CUSTOM_ANIMATION_TAG = "CustomAnimation"
        private const val CUSTOM_ANIMATION_KEY_TAG = "CustomAnimationKey"
        private const val CAMP_POS_TAG = "CampPos"
        private const val HOME_POS_TAG = "HomePos"
        private const val SCRIPTED_ATTACK_ANIMATION_TICKS = 9
        private val IDLE_ANIMATION: RawAnimation = RawAnimation.begin().thenLoop("idle")
    }
}
