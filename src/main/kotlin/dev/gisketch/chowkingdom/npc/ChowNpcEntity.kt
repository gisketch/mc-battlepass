package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.compat.PehkuiScaleBridge
import dev.gisketch.chowkingdom.roles.DEFAULT_BODY_MODEL
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_BOUNCE
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_BUST_SIZE
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_FLOPPY
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_PHYSICS
import dev.gisketch.chowkingdom.roles.DEFAULT_FG_SHOW_IN_ARMOR
import dev.gisketch.chowkingdom.roles.FemaleGenderChoice
import dev.gisketch.chowkingdom.roles.normalizeBodyModel
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderBounce
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderBustSize
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderFloppy
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
import net.minecraft.world.item.Items
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
    var bodyModel: String
        get() = entityData.get(BODY_MODEL_DATA)
        private set(value) = entityData.set(BODY_MODEL_DATA, normalizeBodyModel(value))
    var fgBustSize: Double
        get() = entityData.get(FG_BUST_SIZE_DATA).toDouble()
        private set(value) = entityData.set(FG_BUST_SIZE_DATA, normalizeFemaleGenderBustSize(value).toFloat())
    var fgBounce: Double
        get() = entityData.get(FG_BOUNCE_DATA).toDouble()
        private set(value) = entityData.set(FG_BOUNCE_DATA, normalizeFemaleGenderBounce(value).toFloat())
    var fgFloppy: Double
        get() = entityData.get(FG_FLOPPY_DATA).toDouble()
        private set(value) = entityData.set(FG_FLOPPY_DATA, normalizeFemaleGenderFloppy(value).toFloat())
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
    var playerlikeAnimation: Boolean
        get() = entityData.get(PLAYERLIKE_ANIMATION_DATA)
        private set(value) = entityData.set(PLAYERLIKE_ANIMATION_DATA, value)
    var playerlikeAnimationKey: String
        get() = entityData.get(PLAYERLIKE_ANIMATION_KEY_DATA)
        private set(value) = entityData.set(PLAYERLIKE_ANIMATION_KEY_DATA, NpcPlayerlikeAnimationRegistry.normalize(value))
    var playerlikeAnimationPlayId: Int
        get() = entityData.get(PLAYERLIKE_ANIMATION_PLAY_ID_DATA)
        private set(value) = entityData.set(PLAYERLIKE_ANIMATION_PLAY_ID_DATA, value)
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
        builder.define(BODY_MODEL_DATA, DEFAULT_BODY_MODEL)
        builder.define(FG_BUST_SIZE_DATA, DEFAULT_FG_BUST_SIZE.toFloat())
        builder.define(FG_BOUNCE_DATA, DEFAULT_FG_BOUNCE.toFloat())
        builder.define(FG_FLOPPY_DATA, DEFAULT_FG_FLOPPY.toFloat())
        builder.define(CUSTOM_ANIMATION_DATA, false)
        builder.define(CUSTOM_ANIMATION_KEY_DATA, "")
        builder.define(CUSTOM_ANIMATION_SPEED_DATA, 1.0f)
        builder.define(CUSTOM_ANIMATION_PLAY_ID_DATA, 0)
        builder.define(PLAYERLIKE_ANIMATION_DATA, false)
        builder.define(PLAYERLIKE_ANIMATION_KEY_DATA, "")
        builder.define(PLAYERLIKE_ANIMATION_PLAY_ID_DATA, 0)
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
        bodyModel = definition.bodyModel
        fgBustSize = definition.fgBustSize
        fgBounce = definition.fgBounce
        fgFloppy = definition.fgFloppy
        campPos = camp.immutable()
        homePos = NpcStore.homePos(definition.id)
        customAnimation = definition.customAnimation
        playerlikeAnimation = definition.playerlikeAnimation
        if (playerlikeAnimation) customAnimation = false
        customName = Component.literal(definition.displayName())
        isCustomNameVisible = true
        PehkuiScaleBridge.apply(this, definition.bodyScale())
        setPersistenceRequired()
    }

    fun configureAnimationDebug(camp: BlockPos) {
        npcId = ANIMATION_DEBUG_NPC_ID
        bodyType = NpcBodyTypes.NORMAL
        bodyModel = DEFAULT_BODY_MODEL
        fgBustSize = DEFAULT_FG_BUST_SIZE
        fgBounce = DEFAULT_FG_BOUNCE
        fgFloppy = DEFAULT_FG_FLOPPY
        campPos = camp.immutable()
        homePos = null
        customName = Component.literal("Animation Debug Steve")
        isCustomNameVisible = true
        setCustomAnimationMode(false)
    }

    fun configureBossDebug(id: String, displayName: String, camp: BlockPos, debugBodyType: String = NpcBodyTypes.NORMAL) {
        npcId = id
        bodyType = debugBodyType
        bodyModel = DEFAULT_BODY_MODEL
        fgBustSize = DEFAULT_FG_BUST_SIZE
        fgBounce = DEFAULT_FG_BOUNCE
        fgFloppy = DEFAULT_FG_FLOPPY
        campPos = camp.immutable()
        homePos = null
        customName = Component.literal(displayName)
        isCustomNameVisible = true
        setCustomAnimationMode(false)
        setPersistenceRequired()
    }

    fun setCustomAnimationMode(enabled: Boolean) {
        customAnimation = enabled
        if (enabled) playerlikeAnimation = false
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
        playerlikeAnimation = false
        customAnimation = true
        customAnimationKey = animation.id
        customAnimationSpeed = speed
        customAnimationEndsAtTick = if (animation.loop) 0 else tickCount + animation.durationTicks()
        customAnimationPlayId = customAnimationPlayId + 1
        return true
    }

    fun restoreCustomAnimation(enabled: Boolean, animationKey: String, speed: Float = 1.0f) {
        customAnimation = enabled
        if (enabled) playerlikeAnimation = false
        customAnimationKey = if (enabled) animationKey else ""
        customAnimationSpeed = if (enabled) speed else 1.0f
        customAnimationEndsAtTick = 0
        customAnimationPlayId = customAnimationPlayId + 1
    }

    fun restoreAnimationState(customEnabled: Boolean, customKey: String, customSpeed: Float, playerlikeEnabled: Boolean, playerlikeKey: String) {
        if (playerlikeEnabled) {
            customAnimation = false
            customAnimationKey = ""
            customAnimationSpeed = 1.0f
            customAnimationEndsAtTick = 0
            customAnimationPlayId = customAnimationPlayId + 1
            playerlikeAnimation = true
            playerlikeAnimationKey = playerlikeKey
            playerlikeAnimationPlayId = playerlikeAnimationPlayId + 1
            return
        }
        playerlikeAnimation = false
        playerlikeAnimationKey = ""
        playerlikeAnimationPlayId = playerlikeAnimationPlayId + 1
        restoreCustomAnimation(customEnabled, customKey, customSpeed)
    }

    fun setPlayerlikeAnimationMode(enabled: Boolean) {
        playerlikeAnimation = enabled
        if (enabled) setCustomAnimationMode(false) else playerlikeAnimationKey = ""
        playerlikeAnimationPlayId = playerlikeAnimationPlayId + 1
    }

    fun playPlayerlikeAnimation(animationId: String): Boolean {
        val animation = NpcPlayerlikeAnimationRegistry.resolve(animationId) ?: return false
        setPlayerlikeAnimationMode(true)
        playerlikeAnimationKey = animation.id
        playerlikeAnimationPlayId = playerlikeAnimationPlayId + 1
        return true
    }

    fun clearPlayerlikeAnimation() {
        playerlikeAnimationKey = ""
        playerlikeAnimationPlayId = playerlikeAnimationPlayId + 1
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

    fun femaleGenderChoice(): FemaleGenderChoice = FemaleGenderChoice(
        bodyModel = bodyModel,
        bustSize = fgBustSize,
        physics = DEFAULT_FG_PHYSICS,
        showInArmor = DEFAULT_FG_SHOW_IN_ARMOR,
        bounce = fgBounce,
        floppy = fgFloppy,
    )

    fun updateFemaleGenderBody(model: String = bodyModel, bustSize: Double = fgBustSize, bounce: Double = fgBounce, floppy: Double = fgFloppy) {
        bodyModel = model
        fgBustSize = bustSize
        fgBounce = bounce
        fgFloppy = floppy
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
        if (passThroughInteractions && NpcBossFights.isActive(this)) return InteractionResult.PASS
        if (player.getItemInHand(hand).`is`(Items.LEAD) || isLeashed) {
            val leashResult = super.mobInteract(player, hand)
            if (leashResult != InteractionResult.PASS) return leashResult
        }
        if (!level().isClientSide && player is ServerPlayer) NpcFeature.interact(player, this)
        return InteractionResult.sidedSuccess(level().isClientSide)
    }

    fun startTalkingTo(player: ServerPlayer, durationTicks: Int) {
        NpcEmoteController.cancelPosture(this, "talking")
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
        tag.putString(BODY_MODEL_TAG, bodyModel)
        tag.putDouble(FG_BUST_SIZE_TAG, fgBustSize)
        tag.putDouble(FG_BOUNCE_TAG, fgBounce)
        tag.putDouble(FG_FLOPPY_TAG, fgFloppy)
        tag.putBoolean(CUSTOM_ANIMATION_TAG, customAnimation)
        tag.putString(CUSTOM_ANIMATION_KEY_TAG, customAnimationKey)
        tag.putBoolean(PLAYERLIKE_ANIMATION_TAG, playerlikeAnimation)
        tag.putString(PLAYERLIKE_ANIMATION_KEY_TAG, playerlikeAnimationKey)
        campPos?.let { pos -> tag.putLong(CAMP_POS_TAG, pos.asLong()) }
        homePos?.let { pos -> tag.putLong(HOME_POS_TAG, pos.asLong()) }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        npcId = tag.getString(NPC_ID_TAG).ifBlank { "finn" }
        bodyType = tag.getString(BODY_TYPE_TAG).ifBlank { NpcBodyTypes.NORMAL }
        bodyModel = tag.getString(BODY_MODEL_TAG).ifBlank { DEFAULT_BODY_MODEL }
        if (tag.contains(FG_BUST_SIZE_TAG)) fgBustSize = tag.getDouble(FG_BUST_SIZE_TAG) else fgBustSize = DEFAULT_FG_BUST_SIZE
        if (tag.contains(FG_BOUNCE_TAG)) fgBounce = tag.getDouble(FG_BOUNCE_TAG) else fgBounce = DEFAULT_FG_BOUNCE
        if (tag.contains(FG_FLOPPY_TAG)) fgFloppy = tag.getDouble(FG_FLOPPY_TAG) else fgFloppy = DEFAULT_FG_FLOPPY
        customAnimation = tag.getBoolean(CUSTOM_ANIMATION_TAG)
        customAnimationKey = tag.getString(CUSTOM_ANIMATION_KEY_TAG)
        playerlikeAnimation = tag.getBoolean(PLAYERLIKE_ANIMATION_TAG)
        playerlikeAnimationKey = tag.getString(PLAYERLIKE_ANIMATION_KEY_TAG)
        if (playerlikeAnimation) customAnimation = false
        if (tag.contains(CAMP_POS_TAG)) campPos = BlockPos.of(tag.getLong(CAMP_POS_TAG))
        if (tag.contains(HOME_POS_TAG)) homePos = BlockPos.of(tag.getLong(HOME_POS_TAG))
        refreshConfigVisuals()
        setPersistenceRequired()
    }

    private fun refreshConfigVisuals() {
        val definition = NpcConfig.get(npcId) ?: return
        bodyType = definition.bodyType
        updateFemaleGenderBody(
            model = definition.bodyModel,
            bustSize = definition.fgBustSize,
            bounce = definition.fgBounce,
            floppy = definition.fgFloppy,
        )
        customAnimation = definition.customAnimation
        playerlikeAnimation = definition.playerlikeAnimation
        if (playerlikeAnimation) customAnimation = false
        customName = Component.literal(definition.displayName())
        isCustomNameVisible = true
        PehkuiScaleBridge.apply(this, definition.bodyScale())
    }

    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false

    companion object {
        private val NPC_ID_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val BODY_TYPE_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val BODY_MODEL_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val FG_BUST_SIZE_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val FG_BOUNCE_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val FG_FLOPPY_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val CUSTOM_ANIMATION_DATA: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val CUSTOM_ANIMATION_KEY_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val CUSTOM_ANIMATION_SPEED_DATA: EntityDataAccessor<Float> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.FLOAT)
        private val CUSTOM_ANIMATION_PLAY_ID_DATA: EntityDataAccessor<Int> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.INT)
        private val PLAYERLIKE_ANIMATION_DATA: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val PLAYERLIKE_ANIMATION_KEY_DATA: EntityDataAccessor<String> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.STRING)
        private val PLAYERLIKE_ANIMATION_PLAY_ID_DATA: EntityDataAccessor<Int> = SynchedEntityData.defineId(ChowNpcEntity::class.java, EntityDataSerializers.INT)
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
        private const val BODY_MODEL_TAG = "BodyModel"
        private const val FG_BUST_SIZE_TAG = "FgBustSize"
        private const val FG_BOUNCE_TAG = "FgBounce"
        private const val FG_FLOPPY_TAG = "FgFloppy"
        private const val CUSTOM_ANIMATION_TAG = "CustomAnimation"
        private const val CUSTOM_ANIMATION_KEY_TAG = "CustomAnimationKey"
        private const val PLAYERLIKE_ANIMATION_TAG = "PlayerlikeAnimation"
        private const val PLAYERLIKE_ANIMATION_KEY_TAG = "PlayerlikeAnimationKey"
        private const val CAMP_POS_TAG = "CampPos"
        private const val HOME_POS_TAG = "HomePos"
        private const val SCRIPTED_ATTACK_ANIMATION_TICKS = 9
        private val IDLE_ANIMATION: RawAnimation = RawAnimation.begin().thenLoop("idle")
    }
}
