package dev.gisketch.chowkingdom.revive

import dev.gisketch.chowkingdom.ChatGlyphs
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.item.ItemTossEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID

object ReviveFeature {
    private const val REVIVE_TEAM_NAME = "ck_revive_red"
    private const val TICKS_PER_SECOND = 20
    private const val LOCK_EFFECT_TICKS = 40
    private const val LOCKED_MOVE_TOLERANCE_SQR = 0.35 * 0.35
    private val incapacitated: MutableMap<UUID, IncapacitatedPlayer> = linkedMapOf()
    private val reviveSessionsByReviver: MutableMap<UUID, ReviveSession> = linkedMapOf()
    private val reviveSessionsByTarget: MutableMap<UUID, UUID> = linkedMapOf()
    private val finishingDeaths: MutableSet<UUID> = linkedSetOf()

    fun register() {
        ReviveConfig.load()
        ReviveStore.load()
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onLivingDeath)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onIncomingDamage)
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

    fun isActionLocked(playerId: UUID): Boolean = isIncapacitated(playerId) || reviveSessionsByReviver.containsKey(playerId)

    fun forceRevive(player: ServerPlayer, reviverName: String = "an operator"): Boolean {
        val state = incapacitated[player.uuid] ?: return false
        completeRevive(player, state, reviverName)
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

    fun debugExpire(player: ServerPlayer): Boolean {
        val state = incapacitated[player.uuid] ?: return false
        failRevive(player, state)
        return true
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

    private fun onEntityInteractSpecific(event: PlayerInteractEvent.EntityInteractSpecific) {
        handlePlayerEntityInteract(event.entity as? ServerPlayer ?: return, event.target as? ServerPlayer, event.hand) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.SUCCESS
        }
    }

    private fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        handlePlayerEntityInteract(event.entity as? ServerPlayer ?: return, event.target as? ServerPlayer, event.hand) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.SUCCESS
        }
    }

    private fun handlePlayerEntityInteract(player: ServerPlayer, target: ServerPlayer?, hand: InteractionHand, cancel: () -> Unit) {
        if (player.level().isClientSide || hand != InteractionHand.MAIN_HAND) return
        val activeSession = reviveSessionsByReviver[player.uuid]
        if (activeSession != null) {
            cancel()
            cancelRevive(activeSession, "Revive cancelled.")
            return
        }
        if (target == null || !incapacitated.containsKey(target.uuid)) return
        cancel()
        val state = incapacitated[target.uuid] ?: return
        if (player.uuid == target.uuid) {
            player.displayClientMessage(Component.literal("Use /revive debug self-revive to test this in singleplayer."), true)
            return
        }
        if (incapacitated.containsKey(player.uuid)) {
            player.displayClientMessage(Component.literal("You cannot revive while incapacitated."), true)
            return
        }
        startRevive(player, target, state, debugSelfRevive = false)
    }

    private fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        if (!isActionLocked(player.uuid)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        reviveSessionsByReviver[player.uuid]?.let { cancelRevive(it, "Revive cancelled.") }
    }

    private fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entity as? ServerPlayer ?: return
        if (!isActionLocked(player.uuid)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        reviveSessionsByReviver[player.uuid]?.let { cancelRevive(it, "Revive cancelled.") }
    }

    private fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        if (!isActionLocked(player.uuid)) return
        event.isCanceled = true
    }

    private fun onAttackEntity(event: AttackEntityEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (isActionLocked(player.uuid)) event.isCanceled = true
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        if (isActionLocked(player.uuid)) event.isCanceled = true
    }

    private fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (isActionLocked(player.uuid)) event.isCanceled = true
    }

    private fun onItemToss(event: ItemTossEvent) {
        val player = event.player as? ServerPlayer ?: return
        if (!isActionLocked(player.uuid)) return
        player.inventory.placeItemBackInInventory(event.entity.item.copy())
        event.entity.item.count = 0
        event.isCanceled = true
    }

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.level().isClientSide) return
        incapacitated[player.uuid]?.let { state ->
            stabilizeIncapacitated(player)
            lockPlayerAtAnchor(player, state.anchor)
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
        reviveSessionsByReviver.values.toList().forEach { session ->
            val reviver = event.server.playerList.getPlayer(session.reviverId) ?: return@forEach cancelRevive(session, "Revive cancelled: reviver left.")
            val target = event.server.playerList.getPlayer(session.targetId) ?: return@forEach cancelRevive(session, "Revive cancelled: target left.")
            val state = incapacitated[target.uuid] ?: return@forEach cancelRevive(session, "Revive cancelled: target is no longer incapacitated.")
            if (!session.debugSelfRevive && reviver.distanceToSqr(target) > maxReviveDistanceSqr()) {
                cancelRevive(session, "Revive cancelled: too far away.")
                return@forEach
            }
            if (tick >= session.completeAtTick) {
                completeRevive(target, state, reviver.gameProfile.name)
            } else {
                val seconds = ((session.completeAtTick - tick).coerceAtLeast(0) + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND
                reviver.displayClientMessage(Component.literal("Reviving ${target.gameProfile.name}: ${seconds}s"), true)
            }
        }
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val state = incapacitated[player.uuid] ?: return
        if (player.server.tickCount >= state.expiresAtTick) {
            failRevive(player, state)
        } else {
            applyIncapacitatedVisual(player, state)
            stabilizeIncapacitated(player)
        }
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        reviveSessionsByReviver[player.uuid]?.let { cancelRevive(it, "Revive cancelled: reviver left.") }
        reviveSessionsByTarget[player.uuid]?.let { reviveSessionsByReviver[it]?.let { session -> cancelRevive(session, "Revive cancelled: target left.") } }
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
        cancelReviveForTarget(player.uuid, "Revive cancelled: target was downed again.")
        player.closeContainer()
        player.stopUsingItem()
        stabilizeIncapacitated(player)
        applyIncapacitatedVisual(player, state)
        player.playNotifySound(SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.8f, 0.6f)
        player.server.playerList.broadcastSystemMessage(
            ChatGlyphs.chowKingdomPrefix()
                .append(Component.literal("${player.gameProfile.name} is incapacitated. ").withStyle(ChatFormatting.RED))
                .append(Component.literal("Right-click them to revive. (${count} total)").withStyle(ChatFormatting.YELLOW)),
            false,
        )
    }

    private fun startRevive(reviver: ServerPlayer, target: ServerPlayer, state: IncapacitatedPlayer, debugSelfRevive: Boolean) {
        if (reviveSessionsByReviver.containsKey(reviver.uuid)) return
        if (reviveSessionsByTarget.containsKey(target.uuid)) {
            reviver.displayClientMessage(Component.literal("${target.gameProfile.name} is already being revived."), true)
            return
        }
        if (!debugSelfRevive && reviver.distanceToSqr(target) > maxReviveDistanceSqr()) {
            reviver.displayClientMessage(Component.literal("Move closer to revive ${target.gameProfile.name}."), true)
            return
        }
        val ticks = ReviveConfig.current().reviveSeconds * TICKS_PER_SECOND
        val session = ReviveSession(reviver.uuid, target.uuid, reviver.server.tickCount, reviver.server.tickCount + ticks, PlayerAnchor(reviver.x, reviver.y, reviver.z), debugSelfRevive)
        reviveSessionsByReviver[reviver.uuid] = session
        reviveSessionsByTarget[target.uuid] = reviver.uuid
        reviver.closeContainer()
        reviver.stopUsingItem()
        lockReviver(reviver, session)
        reviver.sendSystemMessage(Component.literal("Reviving ${target.gameProfile.name}. Right-click again to cancel.").withStyle(ChatFormatting.YELLOW))
        if (reviver.uuid != target.uuid) target.sendSystemMessage(Component.literal("${reviver.gameProfile.name} is reviving you.").withStyle(ChatFormatting.GREEN))
        state.lastReviverName = reviver.gameProfile.name
    }

    private fun completeRevive(target: ServerPlayer, state: IncapacitatedPlayer, reviverName: String) {
        cancelReviveForTarget(target.uuid, null)
        incapacitated.remove(target.uuid)
        clearIncapacitatedVisual(target, state)
        restoreMinimumVitals(target)
        target.setForcedPose(null)
        target.setShiftKeyDown(false)
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
        target.removeEffect(MobEffects.DIG_SLOWDOWN)
        target.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f)
        target.server.playerList.broadcastSystemMessage(
            ChatGlyphs.chowKingdomPrefix()
                .append(Component.literal("${target.gameProfile.name} was revived by $reviverName.").withStyle(ChatFormatting.GREEN)),
            false,
        )
    }

    private fun failRevive(player: ServerPlayer, state: IncapacitatedPlayer) {
        incapacitated.remove(player.uuid)
        cancelReviveForTarget(player.uuid, null)
        clearIncapacitatedVisual(player, state)
        player.setForcedPose(null)
        player.setShiftKeyDown(false)
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
        reviveSessionsByTarget[targetId]?.let { reviverId ->
            reviveSessionsByReviver[reviverId]?.let { cancelRevive(it, reason) }
        }
    }

    private fun cancelRevive(session: ReviveSession, reason: String?) {
        reviveSessionsByReviver.remove(session.reviverId)
        reviveSessionsByTarget.remove(session.targetId)
        player(session.reviverId)?.let { reviver ->
            reviver.setForcedPose(null)
            reviver.setShiftKeyDown(false)
            reviver.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
            reviver.removeEffect(MobEffects.DIG_SLOWDOWN)
            reason?.let { reviver.sendSystemMessage(Component.literal(it).withStyle(ChatFormatting.YELLOW)) }
        }
        reason?.let { message ->
            player(session.targetId)?.takeIf { it.uuid != session.reviverId }?.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW))
        }
    }

    private fun stabilizeIncapacitated(player: ServerPlayer) {
        restoreMinimumVitals(player)
        player.setDeltaMovement(0.0, 0.0, 0.0)
        player.fallDistance = 0.0f
        player.stopUsingItem()
        player.setForcedPose(Pose.CROUCHING)
        player.setShiftKeyDown(true)
        addLockEffects(player)
    }

    private fun lockReviver(player: ServerPlayer, session: ReviveSession) {
        player.setForcedPose(Pose.CROUCHING)
        player.setShiftKeyDown(true)
        player.stopUsingItem()
        player.setDeltaMovement(0.0, 0.0, 0.0)
        addLockEffects(player)
        lockPlayerAtAnchor(player, session.anchor)
    }

    private fun lockPlayerAtAnchor(player: ServerPlayer, anchor: PlayerAnchor) {
        val dx = player.x - anchor.x
        val dz = player.z - anchor.z
        if (dx * dx + dz * dz > LOCKED_MOVE_TOLERANCE_SQR) {
            player.teleportTo(anchor.x, player.y, anchor.z)
            player.setDeltaMovement(0.0, 0.0, 0.0)
        }
    }

    private fun addLockEffects(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LOCK_EFFECT_TICKS, 10, false, false, false))
        player.addEffect(MobEffectInstance(MobEffects.DIG_SLOWDOWN, LOCK_EFFECT_TICKS, 10, false, false, false))
    }

    private fun restoreMinimumVitals(player: ServerPlayer) {
        val config = ReviveConfig.current()
        player.health = config.revivedHealth.coerceAtMost(player.maxHealth).coerceAtLeast(1.0f)
        player.foodData.setFoodLevel(config.revivedFoodLevel)
        player.foodData.setSaturation(0.0f)
        player.foodData.setExhaustion(0.0f)
        player.remainingFireTicks = 0
        player.setTicksFrozen(0)
    }

    private fun applyIncapacitatedVisual(player: ServerPlayer, state: IncapacitatedPlayer) {
        player.setGlowingTag(true)
        val scoreboard = player.server.scoreboard
        val team = ensureReviveTeam(player.server)
        scoreboard.addPlayerToTeam(state.scoreboardName, team)
    }

    private fun clearIncapacitatedVisual(player: ServerPlayer, state: IncapacitatedPlayer) {
        player.setGlowingTag(state.previousGlowing)
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

    private fun maxReviveDistanceSqr(): Double {
        val distance = ReviveConfig.current().maxReviveDistance
        return distance * distance
    }

    private fun player(playerId: UUID): ServerPlayer? =
        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(playerId)

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
        val startedTick: Int,
        val completeAtTick: Int,
        val anchor: PlayerAnchor,
        val debugSelfRevive: Boolean,
    )

    private data class PlayerAnchor(val x: Double, val y: Double, val z: Double)

    private class FailedReviveDamageSource(private val original: DamageSource) : DamageSource(original.typeHolder(), original.directEntity, original.entity, original.sourcePositionRaw()) {
        override fun getLocalizedDeathMessage(entity: LivingEntity): Component =
            original.getLocalizedDeathMessage(entity).copy()
                .append(Component.literal(" and could not be revived in time.").withStyle(ChatFormatting.GRAY))
    }
}
