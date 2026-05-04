package dev.gisketch.chowkingdom.revive

import dev.gisketch.chowkingdom.ChatGlyphs
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.item.ItemTossEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

object ReviveFeature {
    private const val REVIVE_TEAM_NAME = "ck_revive_red"
    private const val REVIVE_DUMMY_TAG = "ck_revive_dummy"
    private const val REVIVE_DUMMY_NAME = "Incapacitated Test Dummy"
    private const val TICKS_PER_SECOND = 20
    private const val LOCK_EFFECT_TICKS = 40
    private const val LOCKED_MOVE_TOLERANCE_SQR = 0.35 * 0.35
    private val incapacitated: MutableMap<UUID, IncapacitatedPlayer> = linkedMapOf()
    private val reviveSessionsByReviver: MutableMap<UUID, ReviveSession> = linkedMapOf()
    private val reviveSessionsByTarget: MutableMap<UUID, ReviveTargetSession> = linkedMapOf()
    private val dummySessionsByReviver: MutableMap<UUID, DummyReviveSession> = linkedMapOf()
    private val dummySessionsByDummy: MutableMap<UUID, UUID> = linkedMapOf()
    private val reviveDummies: MutableMap<UUID, ReviveDummy> = linkedMapOf()
    private val pendingDebugRevivers: MutableList<PendingDebugReviver> = mutableListOf()
    private val finishingDeaths: MutableSet<UUID> = linkedSetOf()
    private var debugReviverSequence = 1

    fun register(modBus: IEventBus) {
        ReviveConfig.load()
        ReviveStore.load()
        ReviveNetwork.register(modBus)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onLivingDeath)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onIncomingDamage)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onLivingDamagePre)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onEntityInteractSpecific)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onEntityInteract)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickBlock)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickItem)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onLeftClickBlock)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onAttackEntity)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onBlockBreak)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onBlockPlace)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onItemToss)
        NeoForge.EVENT_BUS.addListener(::onPlayerTick)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    fun reloadConfig() {
        ReviveConfig.load()
    }

    fun isIncapacitated(playerId: UUID): Boolean = incapacitated.containsKey(playerId)

    fun isActionLocked(playerId: UUID): Boolean = reviveSessionsByReviver.containsKey(playerId) || dummySessionsByReviver.containsKey(playerId)

    fun forceRevive(player: ServerPlayer, reviverName: String = "an operator"): Boolean {
        val state = incapacitated[player.uuid] ?: return false
        completeRevive(player, state, emptyList(), listOf(reviverName))
        return true
    }

    fun debugIncapacitate(player: ServerPlayer, seconds: Int? = null): Boolean {
        if (incapacitated.containsKey(player.uuid)) return false
        val durationTicks = seconds?.coerceAtLeast(1)?.times(TICKS_PER_SECOND)
        beginIncapacitated(player, player.damageSources().generic(), durationTicks)
        return true
    }

    fun debugSelfRevive(player: ServerPlayer): Boolean {
        val state = incapacitated[player.uuid] ?: return false
        startRevive(player, player, state, debugSelfRevive = true)
        return true
    }

    fun debugScheduleReviver(target: ServerPlayer, delaySeconds: Int = 1): Boolean {
        if (!incapacitated.containsKey(target.uuid) || !reviveSessionsByTarget.containsKey(target.uuid)) return false
        val name = "Debug Reviver ${debugReviverSequence++}"
        pendingDebugRevivers += PendingDebugReviver(target.uuid, target.server.tickCount + delaySeconds.coerceAtLeast(0) * TICKS_PER_SECOND, name)
        return true
    }

    fun debugExpire(player: ServerPlayer): Boolean {
        val state = incapacitated[player.uuid] ?: return false
        failRevive(player, state)
        return true
    }

    fun giveUp(player: ServerPlayer): Boolean {
        val state = incapacitated[player.uuid] ?: return false
        failRevive(player, state)
        return true
    }

    fun recover(player: ServerPlayer): Boolean {
        incapacitated.remove(player.uuid)?.let { clearIncapacitatedVisual(player, it) }
        cancelReviveForTarget(player.uuid, null)
        cancelActiveRevive(player.uuid, "Revive recovery cleared your active revive.")
        finishingDeaths.remove(player.uuid)
        pendingDebugRevivers.removeIf { it.targetId == player.uuid }
        player.deathTime = 0
        player.setForcedPose(null)
        player.setSwimming(false)
        player.pose = Pose.STANDING
        player.refreshDimensions()
        player.setShiftKeyDown(false)
        player.isSprinting = false
        player.fallDistance = 0.0f
        player.setDeltaMovement(0.0, 0.0, 0.0)
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
        player.removeEffect(MobEffects.DIG_SLOWDOWN)
        player.removeAllEffects()
        player.health = player.maxHealth.coerceAtLeast(1.0f)
        player.foodData.setFoodLevel(20)
        player.foodData.setSaturation(5.0f)
        player.foodData.setExhaustion(0.0f)
        player.remainingFireTicks = 0
        player.setTicksFrozen(0)
        syncHealthNow(player)
        ReviveNetwork.syncSelfState(player, active = false)
        clearReviveProgress(player.uuid)
        return true
    }

    fun spawnDebugDummy(player: ServerPlayer): UUID {
        val level = player.level() as ServerLevel
        val look = player.lookAngle
        val dummy = EntityType.ARMOR_STAND.create(level) ?: ArmorStand(level, player.x, player.y, player.z)
        dummy.setPos(player.x + look.x * 2.0, player.y, player.z + look.z * 2.0)
        dummy.yRot = player.yRot + 180.0f
        dummy.setCustomName(Component.literal(REVIVE_DUMMY_NAME).withStyle(ChatFormatting.RED))
        dummy.isCustomNameVisible = true
        dummy.setNoGravity(true)
        dummy.setInvulnerable(true)
        dummy.setShowArms(true)
        dummy.setGlowingTag(true)
        dummy.addTag(REVIVE_DUMMY_TAG)
        level.addFreshEntity(dummy)
        val team = ensureReviveTeam(player.server)
        player.server.scoreboard.addPlayerToTeam(dummy.scoreboardName, team)
        reviveDummies[dummy.uuid] = ReviveDummy(dummy.uuid, dummy.scoreboardName)
        return dummy.uuid
    }

    fun clearDebugDummies(server: MinecraftServer): Int {
        val count = reviveDummies.values.count { dummy -> removeDebugDummy(server, dummy, announce = false) }
        dummySessionsByReviver.values.toList().forEach { cancelDummyRevive(it, "Dummy revive cancelled.") }
        reviveDummies.clear()
        val team = server.scoreboard.getPlayerTeam(REVIVE_TEAM_NAME)
        server.allLevels.forEach { level ->
            level.getEntities(EntityType.ARMOR_STAND) { entity -> entity.tags.contains(REVIVE_DUMMY_TAG) }
                .forEach { entity ->
                    if (team != null && team.players.contains(entity.scoreboardName)) server.scoreboard.removePlayerFromTeam(entity.scoreboardName, team)
                    entity.discard()
                }
        }
        return count
    }

    fun status(player: ServerPlayer): Component {
        val state = incapacitated[player.uuid]
        val count = ReviveStore.incapacitatedCount(player.uuid)
        val lastCause = ReviveStore.lastCause(player.uuid).ifBlank { "none" }
        return if (state == null) {
            Component.literal("${player.gameProfile.name}: normal, incapacitated_count=$count, last_cause=$lastCause")
        } else {
            val remaining = ((state.expiresAtTick - player.server.tickCount).coerceAtLeast(0) + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND
            Component.literal("${player.gameProfile.name}: incapacitated, ${remaining}s left, incapacitated_count=$count, cause=${state.causeText}")
        }
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        ReviveConfig.load()
        ReviveStore.load()
        clearStaleReviveTeam(event.server)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        ReviveCommands.register(event.dispatcher)
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (finishingDeaths.remove(player.uuid)) {
            cleanupAfterDeath(player)
            return
        }
        if (incapacitated.containsKey(player.uuid)) {
            event.isCanceled = true
            stabilizeIncapacitated(player)
            return
        }
        event.isCanceled = true
        beginIncapacitated(player, event.source)
    }

    private fun onIncomingDamage(event: LivingIncomingDamageEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (!incapacitated.containsKey(player.uuid) || finishingDeaths.contains(player.uuid)) return
        event.isCanceled = true
        event.amount = 0.0f
        stabilizeIncapacitated(player)
    }

    private fun onLivingDamagePre(event: LivingDamageEvent.Pre) {
        val player = event.entity as? ServerPlayer ?: return
        if (finishingDeaths.contains(player.uuid)) return
        if (incapacitated.containsKey(player.uuid)) {
            event.newDamage = 0.0f
            stabilizeIncapacitated(player)
            return
        }
        if (event.newDamage < player.health) return
        event.newDamage = 0.0f
        beginIncapacitated(player, event.source)
    }

    private fun onEntityInteractSpecific(event: PlayerInteractEvent.EntityInteractSpecific) {
        handlePlayerEntityInteract(event.entity as? ServerPlayer ?: return, event.target, event.hand) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.SUCCESS
        }
    }

    private fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        handlePlayerEntityInteract(event.entity as? ServerPlayer ?: return, event.target, event.hand) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.SUCCESS
        }
    }

    private fun handlePlayerEntityInteract(player: ServerPlayer, target: Entity?, hand: InteractionHand, cancel: () -> Unit) {
        if (player.level().isClientSide || hand != InteractionHand.MAIN_HAND) return
        if (hasActiveRevive(player.uuid)) {
            cancel()
            cancelActiveRevive(player.uuid, "Revive cancelled.")
            return
        }
        if (incapacitated.containsKey(player.uuid)) {
            cancel()
            return
        }
        if (target == null) return
        val dummy = target as? ArmorStand
        if (dummy != null && isReviveDummy(dummy)) {
            cancel()
            if (incapacitated.containsKey(player.uuid)) {
                player.displayClientMessage(Component.literal("You cannot revive while incapacitated."), true)
                return
            }
            startDummyRevive(player, dummy)
            return
        }
        val targetPlayer = target as? ServerPlayer ?: return
        if (!incapacitated.containsKey(targetPlayer.uuid)) return
        cancel()
        val state = incapacitated[targetPlayer.uuid] ?: return
        if (player.uuid == targetPlayer.uuid) {
            player.displayClientMessage(Component.literal("Use /revive debug self-revive to test this in singleplayer."), true)
            return
        }
        if (incapacitated.containsKey(player.uuid)) {
            player.displayClientMessage(Component.literal("You cannot revive while incapacitated."), true)
            return
        }
        startRevive(player, targetPlayer, state, debugSelfRevive = false)
    }

    private fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        if (!isActionBlocked(player.uuid)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        if (isActionLocked(player.uuid)) cancelActiveRevive(player.uuid, "Revive cancelled.")
    }

    private fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entity as? ServerPlayer ?: return
        if (!isActionBlocked(player.uuid)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        if (isActionLocked(player.uuid)) cancelActiveRevive(player.uuid, "Revive cancelled.")
    }

    private fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        if (!isActionBlocked(player.uuid)) return
        event.isCanceled = true
    }

    private fun onAttackEntity(event: AttackEntityEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (isActionBlocked(player.uuid)) event.isCanceled = true
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        if (isActionBlocked(player.uuid)) event.isCanceled = true
    }

    private fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (isActionBlocked(player.uuid)) event.isCanceled = true
    }

    private fun onItemToss(event: ItemTossEvent) {
        val player = event.player as? ServerPlayer ?: return
        if (!isActionBlocked(player.uuid)) return
        player.inventory.placeItemBackInInventory(event.entity.item.copy())
        event.entity.item.count = 0
        event.isCanceled = true
    }

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.level().isClientSide) return
        incapacitated[player.uuid]?.let { state ->
            stabilizeIncapacitated(player)
        }
        reviveSessionsByReviver[player.uuid]?.let { session ->
            lockReviver(player, session)
        }
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val tick = event.server.tickCount
        incapacitated.values.toList().forEach { state ->
            val player = event.server.playerList.getPlayer(state.playerId) ?: return@forEach
            if (tick >= state.expiresAtTick) failRevive(player, state)
        }
        processPendingDebugRevivers(event.server, tick)
        reviveSessionsByReviver.values.toList().forEach { session ->
            val reviver = event.server.playerList.getPlayer(session.reviverId) ?: return@forEach cancelRevive(session, "Revive cancelled: reviver left.")
            val target = event.server.playerList.getPlayer(session.targetId) ?: return@forEach cancelRevive(session, "Revive cancelled: target left.")
            val state = incapacitated[target.uuid] ?: return@forEach cancelRevive(session, "Revive cancelled: target is no longer incapacitated.")
            if (!session.debugSelfRevive && reviver.distanceToSqr(target) > maxReviveDistanceSqr()) {
                cancelRevive(session, "Revive cancelled: too far away.")
            }
        }
        reviveSessionsByTarget.values.toList().forEach { session ->
            updateReviveProgress(session, tick)
            if (!session.isComplete()) return@forEach
            val target = event.server.playerList.getPlayer(session.targetId) ?: return@forEach cancelReviveForTarget(session.targetId, "Revive cancelled: target left.")
            val state = incapacitated[target.uuid] ?: return@forEach cancelReviveForTarget(session.targetId, "Revive cancelled: target is no longer incapacitated.")
            completeRevive(target, state, session.reviverIds.toList(), reviverNames(event.server, session))
        }
        dummySessionsByReviver.values.toList().forEach { session ->
            val reviver = event.server.playerList.getPlayer(session.reviverId) ?: return@forEach cancelDummyRevive(session, "Dummy revive cancelled: reviver left.")
            val dummy = dummyEntity(event.server, session.dummyId) ?: return@forEach cancelDummyRevive(session, "Dummy revive cancelled: dummy disappeared.")
            if (reviver.distanceToSqr(dummy) > maxReviveDistanceSqr()) {
                cancelDummyRevive(session, "Dummy revive cancelled: too far away.")
                return@forEach
            }
            if (tick >= session.completeAtTick) {
                completeDummyRevive(reviver, dummy, session)
            }
        }
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        incapacitated[player.uuid]?.let { state ->
            if (player.server.tickCount >= state.expiresAtTick) {
                failRevive(player, state)
            } else {
                applyIncapacitatedVisual(player, state)
                stabilizeIncapacitated(player)
                syncIncapacitatedClient(player, state)
            }
        }
        syncActiveReviveProgressTo(player)
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        reviveSessionsByReviver[player.uuid]?.let { cancelRevive(it, "Revive cancelled: reviver left.") }
        dummySessionsByReviver[player.uuid]?.let { cancelDummyRevive(it, "Dummy revive cancelled: reviver left.") }
        reviveSessionsByTarget[player.uuid]?.let { cancelReviveForTarget(it.targetId, "Revive cancelled: target left.") }
    }

    private fun beginIncapacitated(player: ServerPlayer, source: DamageSource, overrideDurationTicks: Int? = null) {
        val config = ReviveConfig.current()
        val causeText = source.getLocalizedDeathMessage(player).string
        val count = ReviveStore.recordIncapacitated(player, causeText)
        val scoreboardName = player.scoreboardName
        val previousTeamName = player.team?.name?.takeUnless { it == REVIVE_TEAM_NAME }
        val state = IncapacitatedPlayer(
            player.uuid,
            player.gameProfile.name,
            player.server.tickCount,
            player.server.tickCount + (overrideDurationTicks ?: config.incapacitatedSeconds * TICKS_PER_SECOND),
            source,
            causeText,
            previousTeamName,
            player.isCurrentlyGlowing,
            PlayerAnchor(player.x, player.y, player.z),
            scoreboardName,
        )
        incapacitated[player.uuid] = state
        cancelActiveRevive(player.uuid, "Revive cancelled: reviver was downed.")
        cancelReviveForTarget(player.uuid, "Revive cancelled: target was downed again.")
        player.closeContainer()
        player.stopUsingItem()
        stabilizeIncapacitated(player)
        applyIncapacitatedVisual(player, state)
        syncIncapacitatedClient(player, state)
        player.playNotifySound(SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.8f, 0.6f)
        player.server.playerList.broadcastSystemMessage(
            ChatGlyphs.chowKingdomPrefix()
                .append(Component.literal("${player.gameProfile.name} is incapacitated. ").withStyle(ChatFormatting.RED))
                .append(Component.literal("Right-click them to revive. (${count} total)").withStyle(ChatFormatting.YELLOW)),
            false,
        )
    }

    private fun startRevive(reviver: ServerPlayer, target: ServerPlayer, state: IncapacitatedPlayer, debugSelfRevive: Boolean) {
        if (hasActiveRevive(reviver.uuid)) return
        if (!debugSelfRevive && reviver.distanceToSqr(target) > maxReviveDistanceSqr()) {
            reviver.displayClientMessage(Component.literal("Move closer to revive ${target.gameProfile.name}."), true)
            return
        }
        val tick = reviver.server.tickCount
        val targetSession = reviveSessionsByTarget.getOrPut(target.uuid) {
            ReviveTargetSession(target.uuid, target.gameProfile.name, target.id, tick, ReviveConfig.current().reviveSeconds * TICKS_PER_SECOND, tick, debugSelfRevive)
        }
        if (targetSession.debugSelfRevive != debugSelfRevive) return
        updateReviveProgress(targetSession, tick)
        targetSession.reviverIds += reviver.uuid
        targetSession.reviverNamesById[reviver.uuid] = reviver.gameProfile.name
        val session = ReviveSession(reviver.uuid, target.uuid, PlayerAnchor(reviver.x, reviver.y, reviver.z), debugSelfRevive)
        reviveSessionsByReviver[reviver.uuid] = session
        reviver.closeContainer()
        reviver.stopUsingItem()
        lockReviver(reviver, session)
        reviver.sendSystemMessage(Component.literal("Reviving ${target.gameProfile.name}. Right-click again to cancel.").withStyle(ChatFormatting.YELLOW))
        if (reviver.uuid != target.uuid) target.sendSystemMessage(Component.literal("${reviver.gameProfile.name} is reviving you.").withStyle(ChatFormatting.GREEN))
        state.lastReviverName = reviver.gameProfile.name
        syncReviveProgress(target, targetSession)
    }

    private fun startDummyRevive(reviver: ServerPlayer, dummy: ArmorStand) {
        if (hasActiveRevive(reviver.uuid)) return
        if (dummySessionsByDummy.containsKey(dummy.uuid)) {
            reviver.displayClientMessage(Component.literal("That dummy is already being revived."), true)
            return
        }
        if (reviver.distanceToSqr(dummy) > maxReviveDistanceSqr()) {
            reviver.displayClientMessage(Component.literal("Move closer to revive the dummy."), true)
            return
        }
        reviveDummies.putIfAbsent(dummy.uuid, ReviveDummy(dummy.uuid, dummy.scoreboardName))
        val ticks = ReviveConfig.current().reviveSeconds * TICKS_PER_SECOND
        val session = DummyReviveSession(reviver.uuid, dummy.uuid, reviver.server.tickCount, reviver.server.tickCount + ticks, PlayerAnchor(reviver.x, reviver.y, reviver.z))
        dummySessionsByReviver[reviver.uuid] = session
        dummySessionsByDummy[dummy.uuid] = reviver.uuid
        reviver.closeContainer()
        reviver.stopUsingItem()
        lockReviver(reviver, session)
        reviver.sendSystemMessage(Component.literal("Reviving test dummy. Right-click again to cancel.").withStyle(ChatFormatting.YELLOW))
        syncDummyReviveProgress(reviver.server, dummy, session)
    }

    private fun completeRevive(target: ServerPlayer, state: IncapacitatedPlayer, reviverIds: List<UUID>, reviverNames: List<String>) {
        cancelReviveForTarget(target.uuid, null)
        incapacitated.remove(target.uuid)
        clearIncapacitatedVisual(target, state)
        restoreMinimumVitals(target)
        target.setForcedPose(null)
        target.setSwimming(false)
        target.pose = Pose.STANDING
        target.refreshDimensions()
        target.setShiftKeyDown(false)
        target.isSprinting = false
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
        target.removeEffect(MobEffects.DIG_SLOWDOWN)
        target.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f)
        ReviveNetwork.syncComplete(target, reviverIds, reviverNames)
        target.server.playerList.broadcastSystemMessage(
            ChatGlyphs.chowKingdomPrefix()
                .append(Component.literal("${target.gameProfile.name} was revived by ${formatReviverNames(reviverNames)}.").withStyle(ChatFormatting.GREEN)),
            false,
        )
    }

    private fun failRevive(player: ServerPlayer, state: IncapacitatedPlayer) {
        incapacitated.remove(player.uuid)
        cancelReviveForTarget(player.uuid, null)
        clearIncapacitatedVisual(player, state)
        player.setForcedPose(null)
        player.setSwimming(false)
        player.pose = Pose.STANDING
        player.refreshDimensions()
        player.setShiftKeyDown(false)
        player.isSprinting = false
        finishingDeaths += player.uuid
        player.health = 1.0f
        val deathSource = FailedReviveDamageSource(state.source)
        if (!player.hurt(deathSource, Float.MAX_VALUE)) {
            player.die(deathSource)
            finishingDeaths.remove(player.uuid)
        }
    }

    private fun cleanupAfterDeath(player: ServerPlayer) {
        incapacitated.remove(player.uuid)?.let { clearIncapacitatedVisual(player, it) }
        cancelReviveForTarget(player.uuid, null)
        reviveSessionsByReviver[player.uuid]?.let { cancelRevive(it, null) }
    }

    private fun cancelReviveForTarget(targetId: UUID, reason: String?) {
        reviveSessionsByTarget[targetId]?.reviverIds?.toList()?.forEach { reviverId -> reviveSessionsByReviver[reviverId]?.let { cancelRevive(it, reason) } }
    }

    private fun cancelRevive(session: ReviveSession, reason: String?) {
        reviveSessionsByReviver.remove(session.reviverId)
        val targetSession = reviveSessionsByTarget[session.targetId]
        val tick = currentServer()?.tickCount ?: targetSession?.lastProgressTick ?: 0
        if (targetSession != null) {
            updateReviveProgress(targetSession, tick)
            targetSession.reviverIds.remove(session.reviverId)
            targetSession.reviverNamesById.remove(session.reviverId)
            val hasRealReviver = targetSession.reviverIds.any { reviveSessionsByReviver.containsKey(it) }
            if (targetSession.reviverIds.isEmpty() || !hasRealReviver) {
                reviveSessionsByTarget.remove(session.targetId)
                pendingDebugRevivers.removeIf { it.targetId == session.targetId }
                clearReviveProgress(session.targetId)
            } else {
                player(session.targetId)?.let { target -> syncReviveProgress(target, targetSession) }
            }
        }
        player(session.reviverId)?.let { reviver ->
            reviver.setForcedPose(null)
            reviver.setShiftKeyDown(false)
            reviver.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
            reviver.removeEffect(MobEffects.DIG_SLOWDOWN)
            reason?.let { reviver.sendSystemMessage(Component.literal(it).withStyle(ChatFormatting.YELLOW)) }
        }
        reason?.let { message ->
            if (!reviveSessionsByTarget.containsKey(session.targetId)) player(session.targetId)?.takeIf { it.uuid != session.reviverId }?.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW))
        }
    }

    private fun cancelDummyRevive(session: DummyReviveSession, reason: String?) {
        dummySessionsByReviver.remove(session.reviverId)
        dummySessionsByDummy.remove(session.dummyId)
        clearDummyReviveProgress(session)
        player(session.reviverId)?.let { reviver ->
            reviver.setForcedPose(null)
            reviver.setShiftKeyDown(false)
            reviver.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
            reviver.removeEffect(MobEffects.DIG_SLOWDOWN)
            reason?.let { reviver.sendSystemMessage(Component.literal(it).withStyle(ChatFormatting.YELLOW)) }
        }
    }

    private fun completeDummyRevive(reviver: ServerPlayer, dummy: ArmorStand, session: DummyReviveSession) {
        cancelDummyRevive(session, null)
        reviveDummies.remove(dummy.uuid)
        reviver.server.scoreboard.getPlayerTeam(REVIVE_TEAM_NAME)?.let { team ->
            if (team.players.contains(dummy.scoreboardName)) reviver.server.scoreboard.removePlayerFromTeam(dummy.scoreboardName, team)
        }
        dummy.discard()
        reviver.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f)
        reviver.sendSystemMessage(Component.literal("Revive dummy completed.").withStyle(ChatFormatting.GREEN))
    }

    private fun stabilizeIncapacitated(player: ServerPlayer) {
        restoreMinimumVitals(player)
        player.fallDistance = 0.0f
        player.isSprinting = false
        val movement = player.deltaMovement
        if (movement.y > 0.0) player.deltaMovement = Vec3(movement.x, 0.0, movement.z)
        player.setForcedPose(Pose.SWIMMING)
        player.pose = Pose.SWIMMING
        player.setSwimming(true)
        player.refreshDimensions()
        player.setShiftKeyDown(false)
        addIncapacitatedEffects(player)
    }

    private fun lockReviver(player: ServerPlayer, session: ReviveSession) {
        lockReviver(player, session.anchor)
    }

    private fun lockReviver(player: ServerPlayer, session: DummyReviveSession) {
        lockReviver(player, session.anchor)
    }

    private fun lockReviver(player: ServerPlayer, anchor: PlayerAnchor) {
        player.setForcedPose(Pose.CROUCHING)
        player.setShiftKeyDown(true)
        player.stopUsingItem()
        player.setDeltaMovement(0.0, 0.0, 0.0)
        addReviverLockEffects(player)
        lockPlayerAtAnchor(player, anchor)
    }

    private fun lockPlayerAtAnchor(player: ServerPlayer, anchor: PlayerAnchor) {
        val dx = player.x - anchor.x
        val dz = player.z - anchor.z
        if (dx * dx + dz * dz > LOCKED_MOVE_TOLERANCE_SQR) {
            player.teleportTo(anchor.x, player.y, anchor.z)
            player.setDeltaMovement(0.0, 0.0, 0.0)
        }
    }

    private fun addIncapacitatedEffects(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LOCK_EFFECT_TICKS, 7, false, false, false))
    }

    private fun addReviverLockEffects(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LOCK_EFFECT_TICKS, 10, false, false, false))
        player.addEffect(MobEffectInstance(MobEffects.DIG_SLOWDOWN, LOCK_EFFECT_TICKS, 10, false, false, false))
    }

    private fun restoreMinimumVitals(player: ServerPlayer) {
        val config = ReviveConfig.current()
        player.deathTime = 0
        player.health = config.revivedHealth.coerceAtMost(player.maxHealth).coerceAtLeast(1.0f)
        player.foodData.setFoodLevel(config.revivedFoodLevel)
        player.foodData.setSaturation(0.0f)
        player.foodData.setExhaustion(0.0f)
        player.remainingFireTicks = 0
        player.setTicksFrozen(0)
        syncHealthNow(player)
    }

    private fun syncHealthNow(player: ServerPlayer) {
        player.connection.send(ClientboundSetHealthPacket(player.health, player.foodData.foodLevel, player.foodData.saturationLevel))
    }

    private fun applyIncapacitatedVisual(player: ServerPlayer, state: IncapacitatedPlayer) {
        player.setGlowingTag(true)
        val scoreboard = player.server.scoreboard
        val team = ensureReviveTeam(player.server)
        scoreboard.addPlayerToTeam(state.scoreboardName, team)
    }

    private fun clearIncapacitatedVisual(player: ServerPlayer, state: IncapacitatedPlayer) {
        ReviveNetwork.syncSelfState(player, active = false)
        player.setGlowingTag(false)
        val scoreboard = player.server.scoreboard
        scoreboard.getPlayerTeam(REVIVE_TEAM_NAME)?.let { team ->
            if (team.players.contains(state.scoreboardName)) scoreboard.removePlayerFromTeam(state.scoreboardName, team)
        }
        state.previousTeamName?.let { previousName ->
            scoreboard.getPlayerTeam(previousName)?.let { previousTeam -> scoreboard.addPlayerToTeam(state.scoreboardName, previousTeam) }
        }
    }

    private fun clearStaleReviveTeam(server: MinecraftServer) {
        val scoreboard = server.scoreboard
        val team = ensureReviveTeam(server)
        team.players.toList().forEach { playerName -> scoreboard.removePlayerFromTeam(playerName, team) }
    }

    private fun ensureReviveTeam(server: MinecraftServer): PlayerTeam {
        val scoreboard = server.scoreboard
        val team = scoreboard.getPlayerTeam(REVIVE_TEAM_NAME) ?: scoreboard.addPlayerTeam(REVIVE_TEAM_NAME)
        team.setColor(ChatFormatting.RED)
        team.setCollisionRule(Team.CollisionRule.NEVER)
        return team
    }

    private fun syncIncapacitatedClient(player: ServerPlayer, state: IncapacitatedPlayer) {
        ReviveNetwork.syncSelfState(player, active = true, title = incapacitatedTitle(player, state.source), expiresAtMs = expiresAtMillis(player.server, state.expiresAtTick))
    }

    private fun syncReviveProgress(target: ServerPlayer, session: ReviveTargetSession) {
        val completeAtTick = target.server.tickCount + reviveRemainingTicks(session, target.server.tickCount)
        ReviveNetwork.syncProgress(target.server, session.reviverIds.toList(), reviverNames(target.server, session), target.uuid, target.id, target.gameProfile.name, expiresAtMillis(target.server, completeAtTick), active = true)
    }

    private fun syncDummyReviveProgress(server: MinecraftServer, dummy: ArmorStand, session: DummyReviveSession) {
        val reviver = server.playerList.getPlayer(session.reviverId)
        ReviveNetwork.syncProgress(server, listOf(session.reviverId), listOfNotNull(reviver?.gameProfile?.name), dummy.uuid, dummy.id, REVIVE_DUMMY_NAME, expiresAtMillis(server, session.completeAtTick), active = true)
    }

    private fun clearReviveProgress(targetId: UUID) {
        val server = currentServer() ?: return
        ReviveNetwork.syncProgress(server, emptyList(), emptyList(), targetId, -1, "", 0L, active = false)
    }

    private fun clearDummyReviveProgress(session: DummyReviveSession) {
        val server = currentServer() ?: return
        ReviveNetwork.syncProgress(server, emptyList(), emptyList(), session.dummyId, -1, "", 0L, active = false)
    }

    private fun syncActiveReviveProgressTo(receiver: ServerPlayer) {
        reviveSessionsByTarget.values.forEach { session ->
            val target = receiver.server.playerList.getPlayer(session.targetId) ?: return@forEach
            val completeAtTick = receiver.server.tickCount + reviveRemainingTicks(session, receiver.server.tickCount)
            ReviveNetwork.syncProgressTo(receiver, session.reviverIds.toList(), reviverNames(receiver.server, session), target.uuid, target.id, target.gameProfile.name, expiresAtMillis(receiver.server, completeAtTick), active = true)
        }
        dummySessionsByReviver.values.forEach { session ->
            val dummy = dummyEntity(receiver.server, session.dummyId) ?: return@forEach
            val reviver = receiver.server.playerList.getPlayer(session.reviverId)
            ReviveNetwork.syncProgressTo(receiver, listOf(session.reviverId), listOfNotNull(reviver?.gameProfile?.name), dummy.uuid, dummy.id, REVIVE_DUMMY_NAME, expiresAtMillis(receiver.server, session.completeAtTick), active = true)
        }
    }

    private fun expiresAtMillis(server: MinecraftServer, expiresAtTick: Int): Long =
        System.currentTimeMillis() + (expiresAtTick - server.tickCount).coerceAtLeast(0) * 50L

    private fun incapacitatedTitle(player: ServerPlayer, source: DamageSource): String {
        val cause = incapacitatedCauseName(player, source)
        return if (cause == null) "YOU GOT KILLED" else "YOU GOT KILLED BY ${cause.uppercase(Locale.ROOT)}"
    }

    private fun incapacitatedCauseName(player: ServerPlayer, source: DamageSource): String? {
        val entity = source.entity ?: source.directEntity
        val entityName = entity?.displayName?.string?.trim()
            ?.takeIf { it.isNotBlank() && entity.uuid != player.uuid }
        if (entityName != null) return entityName
        val msgId = source.getMsgId().replace('_', ' ').replace('.', ' ').trim()
        return msgId.takeIf { it.isNotBlank() && !it.equals("generic", ignoreCase = true) }
    }

    private fun maxReviveDistanceSqr(): Double {
        val distance = ReviveConfig.current().maxReviveDistance
        return distance * distance
    }

    private fun updateReviveProgress(session: ReviveTargetSession, tick: Int) {
        val elapsed = (tick - session.lastProgressTick).coerceAtLeast(0)
        if (elapsed > 0 && session.reviverIds.isNotEmpty()) session.progressTicks = (session.progressTicks + elapsed * reviveRate(session.reviverIds.size)).coerceAtMost(session.baseTicks.toDouble())
        session.lastProgressTick = tick
    }

    private fun reviveRemainingTicks(session: ReviveTargetSession, tick: Int): Int {
        if (session.reviverIds.isEmpty()) return 0
        val elapsed = (tick - session.lastProgressTick).coerceAtLeast(0)
        val progress = (session.progressTicks + elapsed * reviveRate(session.reviverIds.size)).coerceAtMost(session.baseTicks.toDouble())
        return ceil((session.baseTicks - progress).coerceAtLeast(0.0) / reviveRate(session.reviverIds.size)).toInt()
    }

    private fun reviveRate(reviverCount: Int): Double {
        var rate = 1.0
        repeat((reviverCount - 1).coerceAtLeast(0)) { rate *= 2.0 }
        return rate
    }

    private fun processPendingDebugRevivers(server: MinecraftServer, tick: Int) {
        val due = pendingDebugRevivers.filter { it.addAtTick <= tick }
        if (due.isEmpty()) return
        pendingDebugRevivers.removeAll(due.toSet())
        due.forEach { pending ->
            val target = server.playerList.getPlayer(pending.targetId) ?: return@forEach
            val session = reviveSessionsByTarget[pending.targetId] ?: return@forEach
            if (!incapacitated.containsKey(pending.targetId)) return@forEach
            updateReviveProgress(session, tick)
            val reviverId = UUID.randomUUID()
            session.reviverIds += reviverId
            session.reviverNamesById[reviverId] = pending.name
            syncReviveProgress(target, session)
            target.sendSystemMessage(Component.literal("${pending.name} joined the debug revive.").withStyle(ChatFormatting.YELLOW))
        }
    }

    private fun ReviveTargetSession.isComplete(): Boolean = progressTicks >= baseTicks

    private fun reviverNames(server: MinecraftServer, session: ReviveTargetSession): List<String> =
        session.reviverIds.mapNotNull { reviverId -> session.reviverNamesById[reviverId] ?: server.playerList.getPlayer(reviverId)?.gameProfile?.name }

    private fun formatReviverNames(names: List<String>): String = when (names.size) {
        0 -> "someone"
        1 -> names.first()
        2 -> names.joinToString(" and ")
        else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
    }

    private fun hasActiveRevive(playerId: UUID): Boolean = reviveSessionsByReviver.containsKey(playerId) || dummySessionsByReviver.containsKey(playerId)

    private fun isActionBlocked(playerId: UUID): Boolean = isIncapacitated(playerId) || isActionLocked(playerId)

    private fun cancelActiveRevive(playerId: UUID, reason: String) {
        reviveSessionsByReviver[playerId]?.let { cancelRevive(it, reason) }
        dummySessionsByReviver[playerId]?.let { cancelDummyRevive(it, reason) }
    }

    private fun isReviveDummy(entity: ArmorStand): Boolean = entity.tags.contains(REVIVE_DUMMY_TAG) || reviveDummies.containsKey(entity.uuid)

    private fun dummyEntity(server: MinecraftServer, dummyId: UUID): ArmorStand? =
        server.allLevels.asSequence()
            .mapNotNull { level -> level.getEntity(dummyId) as? ArmorStand }
            .firstOrNull { dummy -> !dummy.isRemoved }

    private fun removeDebugDummy(server: MinecraftServer, dummy: ReviveDummy, announce: Boolean): Boolean {
        dummySessionsByDummy[dummy.dummyId]?.let { reviverId -> dummySessionsByReviver[reviverId]?.let { cancelDummyRevive(it, null) } }
        server.scoreboard.getPlayerTeam(REVIVE_TEAM_NAME)?.let { team ->
            if (team.players.contains(dummy.scoreboardName)) server.scoreboard.removePlayerFromTeam(dummy.scoreboardName, team)
        }
        val entity = dummyEntity(server, dummy.dummyId) ?: return false
        entity.discard()
        if (announce) server.playerList.broadcastSystemMessage(Component.literal("Revive dummy removed.").withStyle(ChatFormatting.GRAY), false)
        return true
    }

    private fun player(playerId: UUID): ServerPlayer? =
        currentServer()?.playerList?.getPlayer(playerId)

    private fun currentServer(): MinecraftServer? =
        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()

    private data class IncapacitatedPlayer(
        val playerId: UUID,
        val playerName: String,
        val startedTick: Int,
        val expiresAtTick: Int,
        val source: DamageSource,
        val causeText: String,
        val previousTeamName: String?,
        val previousGlowing: Boolean,
        val anchor: PlayerAnchor,
        val scoreboardName: String,
        var lastReviverName: String = "",
    )

    private data class ReviveSession(
        val reviverId: UUID,
        val targetId: UUID,
        val anchor: PlayerAnchor,
        val debugSelfRevive: Boolean,
    )

    private data class ReviveTargetSession(
        val targetId: UUID,
        val targetName: String,
        val targetEntityId: Int,
        val startedTick: Int,
        val baseTicks: Int,
        var lastProgressTick: Int,
        val debugSelfRevive: Boolean,
        var progressTicks: Double = 0.0,
        val reviverIds: MutableSet<UUID> = linkedSetOf(),
        val reviverNamesById: MutableMap<UUID, String> = linkedMapOf(),
    )

    private data class PendingDebugReviver(val targetId: UUID, val addAtTick: Int, val name: String)

    private data class DummyReviveSession(
        val reviverId: UUID,
        val dummyId: UUID,
        val startedTick: Int,
        val completeAtTick: Int,
        val anchor: PlayerAnchor,
    )

    private data class ReviveDummy(val dummyId: UUID, val scoreboardName: String)

    private data class PlayerAnchor(val x: Double, val y: Double, val z: Double)

    private class FailedReviveDamageSource(private val original: DamageSource) : DamageSource(original.typeHolder(), original.directEntity, original.entity, original.sourcePositionRaw()) {
        override fun getLocalizedDeathMessage(entity: LivingEntity): Component =
            original.getLocalizedDeathMessage(entity).copy()
                .append(Component.literal(" and could not be revived in time.").withStyle(ChatFormatting.GRAY))
    }
}
