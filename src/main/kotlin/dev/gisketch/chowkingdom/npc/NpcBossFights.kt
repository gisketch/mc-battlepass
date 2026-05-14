package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ParrySoundFeature
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.roles.ClassMentorQuestService
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.entity.projectile.Arrow
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

object NpcBossFights {
    private val active: MutableMap<UUID, ActiveBossFight> = linkedMapOf()
    private val resultProtection: MutableMap<UUID, BossResultProtection> = linkedMapOf()

    fun start(player: ServerPlayer, entity: ChowNpcEntity, definition: NpcDefinition): NpcBossStartResult {
        NpcBossMovesets.load()
        val boss = definition.boss.normalized()
        if (!boss.enabled) return NpcBossStartResult(false, "${definition.displayName()} has boss fights disabled.")
        if (boss.template != NpcBossDefinition.DEFAULT_BOSS_TEMPLATE) return NpcBossStartResult(false, "Unknown NPC boss template '${boss.template}'.")
        if (active.containsKey(entity.uuid)) return NpcBossStartResult(false, "${definition.displayName()} is already in a boss fight.")
        if (active.values.any { fight -> fight.targetId == player.uuid }) return NpcBossStartResult(false, "You are already in an NPC boss fight.")
        val moveset = NpcBossMovesets.forDefinition(definition)
        val armory = bossArmory(boss)
        if (entity.isSleeping) entity.stopSleeping()
        entity.navigation.stop()
        val health = boss.health.toFloat().coerceAtLeast(1.0f)
        val bossBar = ServerBossEvent(
            Component.literal("${definition.displayName()} | NPC mode: offense"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS,
        )
        bossBar.setProgress(1.0f)
        val fight = ActiveBossFight(
            npcId = definition.id,
            displayName = definition.displayName(),
            moveset = moveset,
            targetId = player.uuid,
            startPos = entity.position(),
            maxHealth = health,
            health = health,
            damage = boss.damage.toFloat().coerceAtLeast(0.0f),
            balloons = boss.balloons,
            animationSnapshot = NpcCustomAnimationController.snapshot(entity),
            armory = armory,
            originalHealth = entity.health,
            bossBar = bossBar,
            startedTick = entity.tickCount,
            nextActionTick = 0,
            phaseStartedTick = entity.tickCount,
            strafeSide = if (entity.random.nextBoolean()) 1 else -1,
        )
        fight.bossPhaseIndex = bossPhaseIndexForHealth(fight)
        fight.offenseAttacksRemaining = offenseChainCount(entity, fight)
        active[entity.uuid] = fight
        resultProtection.remove(entity.uuid)
        entity.updatePassThroughInteractions(true)
        updateBossBar(entity, player, fight, forceMusic = true)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "BOSS FIGHT STARTED", definition.displayName(), SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        return NpcBossStartResult(true, "Started boss fight with ${definition.displayName()}.")
    }

    fun startDebug(player: ServerPlayer, entity: ChowNpcEntity, moveset: NpcBossMovesetDefinition, definition: NpcDefinition? = null): NpcBossStartResult {
        val displayName = definition?.displayName() ?: moveset.displayName
        if (active.containsKey(entity.uuid)) return NpcBossStartResult(false, "$displayName is already in a boss fight.")
        if (active.values.any { fight -> fight.targetId == player.uuid }) return NpcBossStartResult(false, "You are already in an NPC boss fight.")
        entity.navigation.stop()
        val health = moveset.health.toFloat().coerceAtLeast(1.0f)
        val bossBar = ServerBossEvent(
            Component.literal("$displayName | NPC mode: offense"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS,
        )
        bossBar.setProgress(1.0f)
        val fight = ActiveBossFight(
            npcId = entity.npcId,
            displayName = displayName,
            moveset = moveset.normalized(),
            targetId = player.uuid,
            startPos = entity.position(),
            maxHealth = health,
            health = health,
            damage = moveset.damage.toFloat().coerceAtLeast(0.0f),
            balloons = moveset.balloons,
            animationSnapshot = NpcCustomAnimationController.snapshot(entity),
            armory = debugArmory(moveset, definition),
            originalHealth = entity.health,
            bossBar = bossBar,
            startedTick = entity.tickCount,
            nextActionTick = 0,
            phaseStartedTick = entity.tickCount,
            strafeSide = if (entity.random.nextBoolean()) 1 else -1,
            debug = true,
        )
        fight.bossPhaseIndex = bossPhaseIndexForHealth(fight)
        fight.offenseAttacksRemaining = offenseChainCount(entity, fight)
        active[entity.uuid] = fight
        resultProtection.remove(entity.uuid)
        entity.updatePassThroughInteractions(true)
        updateBossBar(entity, player, fight, forceMusic = true)
        SnackbarNetwork.send(player, SnackbarNotification.npc(entity.npcId, "BOSS TEST STARTED", displayName, SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        return NpcBossStartResult(true, "Spawned $displayName boss test NPC.")
    }

    fun tick(entity: ChowNpcEntity): Boolean {
        val fight = active[entity.uuid] ?: return false
        if (!tickActive(entity, fight)) cancel(entity, fight, "Boss fight reset.")
        return true
    }

    fun isActive(entity: ChowNpcEntity): Boolean = active.containsKey(entity.uuid)

    fun isDuelist(entity: ChowNpcEntity, player: ServerPlayer): Boolean = active[entity.uuid]?.targetId == player.uuid

    fun isResultProtected(entity: ChowNpcEntity): Boolean {
        val protection = resultProtection[entity.uuid] ?: return false
        if (entity.tickCount <= protection.untilTick) return true
        resultProtection.remove(entity.uuid)
        if (!isActive(entity)) entity.updatePassThroughInteractions(false)
        return false
    }

    fun tickResultProtection(entity: ChowNpcEntity) {
        val protection = resultProtection[entity.uuid]
        if (protection == null) {
            if (!isActive(entity) && entity.passThroughInteractions) entity.updatePassThroughInteractions(false)
            return
        }
        if (entity.tickCount <= protection.untilTick) {
            entity.updatePassThroughInteractions(true)
            if (entity.health < protection.healthFloor) entity.setHealth(protection.healthFloor.coerceAtMost(entity.maxHealth))
            return
        }
        resultProtection.remove(entity.uuid)
        if (!isActive(entity)) entity.updatePassThroughInteractions(false)
    }

    fun handlePlayerDamagePre(target: ServerPlayer, sourceEntity: Entity?, directEntity: Entity?, damage: Float): Boolean {
        val boss = bossDamageSource(sourceEntity) ?: bossDamageSource(directEntity) ?: return false
        val fight = active[boss.uuid] ?: return false
        if (fight.targetId != target.uuid || damage <= 0.0f) return false
        if (!wouldDefeatPlayer(target, damage)) return false
        bossVictory(boss, target, fight)
        return true
    }

    fun shouldBlockDamage(target: LivingEntity, sourceEntity: Entity?, directEntity: Entity?): Boolean {
        val targetBoss = target as? ChowNpcEntity
        if (targetBoss != null) {
            if (isResultProtected(targetBoss)) return true
            val fight = active[targetBoss.uuid] ?: return false
            if (targetBoss.tickCount <= fight.npcIframeUntilTick) return true
            val attacker = (sourceEntity as? ServerPlayer) ?: (directEntity as? ServerPlayer)
            return attacker?.uuid != fight.targetId
        }

        val targetPlayer = target as? ServerPlayer
        if (targetPlayer != null) {
            val fight = active.entries.firstOrNull { entry -> entry.value.targetId == targetPlayer.uuid }
            if (fight != null) {
                if (sourceEntity == null && directEntity == null) return false
                val sourceBoss = bossDamageSource(sourceEntity) ?: bossDamageSource(directEntity)
                if (sourceBoss?.uuid == fight.key) return NpcCombatRollBridge.isRolling(targetPlayer)
                return sourceEntity?.uuid != fight.key && directEntity?.uuid != fight.key
            }
        }

        val sourcePlayer = (sourceEntity as? ServerPlayer) ?: (directEntity as? ServerPlayer)
        if (sourcePlayer != null) {
            val fight = active.entries.firstOrNull { entry -> entry.value.targetId == sourcePlayer.uuid } ?: return false
            return target.uuid != fight.key
        }

        val sourceBoss = bossDamageSource(sourceEntity) ?: bossDamageSource(directEntity) ?: return false
        val fight = active[sourceBoss.uuid] ?: return false
        return target.uuid != fight.targetId
    }

    fun handleNpcAttackAttempt(entity: ChowNpcEntity, attacker: ServerPlayer?): Boolean {
        if (isResultProtected(entity)) return true
        val fight = active[entity.uuid] ?: return false
        val target = attacker?.takeIf { player -> player.uuid == fight.targetId } ?: return true
        if (entity.tickCount <= fight.npcIframeUntilTick) return true
        return when (fight.phase) {
            BossFightPhase.PHASE_DIALOGUE -> true
            BossFightPhase.RECOVERY -> {
                if (fight.recoveryHitsTaken >= fight.moveset.recoveryHitsAllowed || fight.guardAfterHurtTick > 0) {
                    startGuardReact(entity, target, fight)
                    true
                } else {
                    false
                }
            }
            BossFightPhase.GUARD_MODE -> {
                startGuardReact(entity, target, fight)
                true
            }
            BossFightPhase.GUARD_REACT,
            BossFightPhase.GUARD_ROLL,
            BossFightPhase.GUARD_DODGE,
            BossFightPhase.PARRY -> {
                blockBossHit(entity)
                true
            }
            BossFightPhase.CHASE,
            BossFightPhase.ATTACK -> true
        }
    }

    fun handleNpcDamage(entity: ChowNpcEntity, attacker: ServerPlayer?, damage: Float): Boolean {
        val fight = active[entity.uuid] ?: return false
        val target = attacker?.takeIf { player -> player.uuid == fight.targetId } ?: return true
        if (damage <= 0.0f) return true
        if (entity.tickCount <= fight.npcIframeUntilTick) return true

        when (fight.phase) {
            BossFightPhase.PHASE_DIALOGUE -> Unit
            BossFightPhase.RECOVERY -> {
                if (fight.recoveryHitsTaken >= fight.moveset.recoveryHitsAllowed || fight.guardAfterHurtTick > 0) startGuardReact(entity, target, fight) else acceptRecoveryHit(entity, target, fight, damage)
            }
            BossFightPhase.GUARD_MODE -> startGuardReact(entity, target, fight)
            BossFightPhase.GUARD_REACT,
            BossFightPhase.GUARD_ROLL,
            BossFightPhase.GUARD_DODGE,
            BossFightPhase.PARRY -> blockBossHit(entity)
            BossFightPhase.CHASE,
            BossFightPhase.ATTACK -> Unit
        }
        return true
    }

    fun handleDialogAction(player: ServerPlayer, npcId: String, action: String): Boolean {
        val entry = active.entries.firstOrNull { candidate ->
            candidate.value.targetId == player.uuid &&
                candidate.value.npcId == npcId &&
                candidate.value.phase == BossFightPhase.PHASE_DIALOGUE
        } ?: return false
        val entity = bossEntity(player.server, entry.key) ?: return true
        if (entity.level() != player.level()) return true
        return when (action.lowercase()) {
            "dialog_keepalive" -> {
                entry.value.phaseDialogueUntilTick = entity.tickCount + PHASE_DIALOGUE_TIMEOUT_TICKS
                entity.continueTalkingTo(player, PHASE_DIALOGUE_KEEPALIVE_TICKS)
                true
            }
            "dialog_close" -> {
                resumePhaseTransition(entity, player, entry.value)
                true
            }
            else -> false
        }
    }

    fun cancelForPlayer(player: ServerPlayer, reason: String = "Boss fight reset.") {
        NpcCombatRollBridge.clear(player)
        active.values.filter { fight -> fight.targetId == player.uuid }.forEach { fight ->
            val entity = NpcFeature.existingNpc(player.server, fight.npcId)
            if (entity != null) cancel(entity, fight, reason) else {
                active.entries.removeIf { entry -> entry.value === fight }
                fight.bossBar.removeAllPlayers()
                NpcNetwork.clearBossBar(player, fight.npcId)
            }
        }
    }

    fun clear(entity: ChowNpcEntity) {
        val fight = active.remove(entity.uuid) ?: return
        resultProtection.remove(entity.uuid)
        fight.bossBar.removeAllPlayers()
        clearBossBar(entity, fight)
        NpcCustomAnimationController.restore(entity, fight.animationSnapshot)
        entity.updatePassThroughInteractions(false)
    }

    private fun tickActive(entity: ChowNpcEntity, fight: ActiveBossFight): Boolean {
        if (!entity.isAlive || entity.tickCount > fight.startedTick + MAX_FIGHT_TICKS) return false
        val level = entity.level() as? ServerLevel ?: return false
        val target = level.getPlayerByUUID(fight.targetId) as? ServerPlayer ?: return false
        if (!target.isAlive) return false
        if (!withinTether(entity, target, fight)) return false
        tickSupportEffects(entity, fight)
        tickMagicProjectiles(entity, target, fight)
        tickTrackedArrows(entity, fight)
        if (active[entity.uuid] !== fight) return true
        updateBossBar(entity, target, fight)
        return when (fight.phase) {
            BossFightPhase.CHASE -> tickChase(entity, target, fight)
            BossFightPhase.ATTACK -> tickAttack(entity, target, fight)
            BossFightPhase.RECOVERY -> tickRecovery(entity, target, fight)
            BossFightPhase.PHASE_DIALOGUE -> tickPhaseDialogue(entity, target, fight)
            BossFightPhase.GUARD_MODE -> tickGuardMode(entity, target, fight)
            BossFightPhase.GUARD_REACT -> tickGuardReact(entity, target, fight)
            BossFightPhase.GUARD_ROLL -> tickGuardRoll(entity, target, fight)
            BossFightPhase.GUARD_DODGE -> tickGuardDodge(entity, target, fight)
            BossFightPhase.PARRY -> tickParry(entity, target, fight)
        }
    }

    private fun tickChase(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        if (fight.tacticPhase == BossTacticPhase.OFFENSE && fight.offenseAttacksRemaining <= 0) {
            fight.offenseAttacksRemaining = offenseChainCount(entity, fight)
        }
        val mode = if (fight.tacticPhase == BossTacticPhase.OFFENSE) "offense" else "chase"
        entity.debugActivity = "boss"
        entity.debugGoal = mode
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, mode)
        faceTarget(entity, target)
        playTemplate(entity, fight, approachTemplate(fight))
        val move = selectMove(entity, target, fight)
        if (move != null) {
            startMove(entity, target, fight, move)
            return true
        }
        if (usesRangedBossSpacing(fight) && entity.distanceTo(target).toDouble() <= fight.moveset.attackStartDistance + RANGED_SPACING_BUFFER) {
            playTemplate(entity, fight, strafeTemplate(fight))
            holdRangedSpacing(entity, target, fight)
            return true
        }
        val chaseSpeed = phaseSpeed(fight, BOSS_CHASE_SPEED)
        entity.navigation.moveTo(target.x, target.y, target.z, chaseSpeed)
        if (entity.navigation.isDone) entity.moveControl.setWantedPosition(target.x, target.y, target.z, chaseSpeed)
        return true
    }

    private fun startMove(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        setPhase(entity, fight, BossFightPhase.ATTACK)
        fight.activeMove = move
        fight.firedHitTicks.clear()
        fight.moveCooldowns[move.id] = entity.tickCount + move.cooldownTicks
        if (move.kind != NpcBossMoveKinds.ROLL) {
            fight.lastMoveId = move.id
            if (fight.tacticPhase == BossTacticPhase.OFFENSE) {
                fight.offenseAttacksRemaining = (fight.offenseAttacksRemaining - 1).coerceAtLeast(0)
            }
        }
        playMoveAnimation(entity, fight, move, forceRestart = true)
        playMoveCastVfx(entity, move)
        entity.navigation.stop()
        if (move.kind == NpcBossMoveKinds.ROLL) performNpcRoll(entity, target, fight, move)
        showModeBalloon(entity, target, fight, if (move.kind == NpcBossMoveKinds.ROLL) "rolling" else "attacking")
    }

    private fun tickAttack(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        val move = fight.activeMove ?: run {
            startChase(entity, target, fight)
            return true
        }
        entity.debugActivity = "boss"
        entity.debugGoal = move.id
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, if (move.kind == NpcBossMoveKinds.ROLL) "rolling" else "attacking")
        entity.navigation.stop()
        if (move.kind != NpcBossMoveKinds.ROLL) faceTarget(entity, target)
        val elapsed = entity.tickCount - fight.phaseStartedTick
        move.hitTicks.forEach { hitTick ->
            if (elapsed >= hitTick && fight.firedHitTicks.add(hitTick)) {
                executeMoveHit(entity, target, fight, move)
            }
        }
        if (elapsed < move.durationTicks) return true
        val continueOffense = fight.tacticPhase == BossTacticPhase.OFFENSE && fight.offenseAttacksRemaining > 0
        if (move.kind == NpcBossMoveKinds.ROLL || move.recoveryTicks <= 0) {
            if (continueOffense) startChase(entity, target, fight) else startDefensePhase(entity, target, fight)
            return true
        }
        val recoveryTicks = if (continueOffense) {
            move.recoveryTicks.coerceAtMost(currentBossPhase(fight).offenseChainRecoveryTicks).coerceAtLeast(1)
        } else {
            move.recoveryTicks
        }
        startRecovery(entity, target, fight, recoveryTicks, returnToOffense = continueOffense)
        return true
    }

    private fun startRecovery(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, recoveryTicks: Int, returnToOffense: Boolean = false) {
        setPhase(entity, fight, BossFightPhase.RECOVERY)
        fight.activeMove = null
        fight.nextActionTick = entity.tickCount + recoveryTicks.coerceAtLeast(1)
        fight.recoveryHitsTaken = 0
        fight.hurtUntilTick = 0
        fight.guardAfterHurtTick = 0
        fight.recoveryReturnToOffense = returnToOffense
        prepareStrafe(entity, fight)
        playTemplate(entity, fight, recoveryTemplate(fight), forceRestart = true)
        showModeBalloon(entity, target, fight, "recovery")
    }

    private fun tickRecovery(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "recovery"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "recovery")
        faceTarget(entity, target)
        if (fight.guardAfterHurtTick > 0 && entity.tickCount >= fight.guardAfterHurtTick) {
            startDefensePhase(entity, target, fight)
            return true
        }
        if (entity.tickCount < fight.hurtUntilTick) {
            entity.navigation.stop()
            return true
        }
        playTemplate(entity, fight, recoveryTemplate(fight))
        if (entity.tickCount >= fight.nextActionTick) {
            finishRecovery(entity, target, fight)
            return true
        }
        strafeAroundTarget(
            entity,
            target,
            fight,
            BOSS_RECOVERY_STRAFE_SPEED,
            RECOVERY_STRAFE_STEP,
            RECOVERY_STRAFE_RADIUS,
            RECOVERY_STRAFE_INNER_RADIUS,
            RECOVERY_STRAFE_OUTER_RADIUS,
        )
        return true
    }

    private fun startPhaseTransitionDialogue(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, phase: NpcBossPhaseDefinition) {
        setPhase(entity, fight, BossFightPhase.PHASE_DIALOGUE)
        fight.activeMove = null
        fight.firedHitTicks.clear()
        fight.recoveryReturnToOffense = false
        fight.forceOffenseAfterRecovery = false
        fight.offenseAttacksRemaining = offenseChainCount(entity, fight)
        fight.phaseDialogueUntilTick = entity.tickCount + PHASE_DIALOGUE_TIMEOUT_TICKS
        entity.navigation.stop()
        faceTarget(entity, target)
        playTemplate(entity, fight, guardTemplate(fight), forceRestart = true)
        updateStatus(entity, target, fight, "dialogue")
        entity.startTalkingTo(target, PHASE_DIALOGUE_TIMEOUT_TICKS)
        openPhaseTransitionDialog(entity, target, fight, phase)
    }

    private fun tickPhaseDialogue(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "phase_dialogue"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "dialogue")
        faceTarget(entity, target)
        entity.navigation.stop()
        playTemplate(entity, fight, guardTemplate(fight))
        if (entity.tickCount >= fight.phaseDialogueUntilTick) resumePhaseTransition(entity, target, fight)
        return true
    }

    private fun resumePhaseTransition(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        if (fight.phase != BossFightPhase.PHASE_DIALOGUE) return
        entity.stopTalkingTo(target)
        fight.phaseDialogueUntilTick = 0
        startOffensePhase(entity, target, fight)
        updateBossBar(entity, target, fight, forceMusic = true)
    }

    private fun startGuardMode(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.GUARD_MODE)
        fight.tacticPhase = BossTacticPhase.DEFENSE
        fight.activeMove = null
        fight.nextActionTick = entity.tickCount + guardBaitDelay(entity, fight)
        fight.nextTauntTick = entity.tickCount + guardTauntDelay(entity, fight)
        fight.recoveryHitsTaken = 0
        fight.hurtUntilTick = 0
        fight.guardAfterHurtTick = 0
        fight.offenseAttacksRemaining = 0
        fight.recoveryReturnToOffense = false
        fight.forceOffenseAfterRecovery = false
        prepareStrafe(entity, fight)
        playTemplate(entity, fight, guardTemplate(fight), forceRestart = true)
        showModeBalloon(entity, target, fight, "guard")
    }

    private fun tickGuardMode(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "guard"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "guard")
        faceTarget(entity, target)
        playTemplate(entity, fight, guardTemplate(fight))
        if (entity.distanceToSqr(target) > STRAFE_RETURN_CHASE_DISTANCE_SQR || entity.tickCount >= fight.nextActionTick) {
            startOffensePhase(entity, target, fight)
            return true
        }
        if (entity.tickCount >= fight.nextTauntTick) {
            showBossBalloon(entity, target, fight, fight.balloons.taunt, "taunt")
            fight.nextTauntTick = entity.tickCount + guardTauntDelay(entity, fight)
        }
        strafeAroundTarget(entity, target, fight)
        return true
    }

    private fun startGuardReact(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        fight.activeMove = null
        when (entity.random.nextInt(GUARD_RESPONSE_CHOICES)) {
            0 -> startParry(entity, target, fight)
            1 -> startGuardRoll(entity, target, fight)
            else -> startGuardDodge(entity, target, fight)
        }
        showBossBalloon(entity, target, fight, fight.balloons.guardReact, "guard_react")
        fight.lastModeBalloon = "guard"
    }

    private fun tickGuardReact(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "guard_react"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "guard")
        faceTarget(entity, target)
        entity.navigation.stop()
        if (entity.tickCount < fight.nextActionTick) return true
        startParry(entity, target, fight)
        return true
    }

    private fun startParry(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.PARRY)
        fight.activeMove = null
        playTemplate(entity, fight, parryTemplate(fight), forceRestart = true)
        entity.navigation.stop()
        blockBossHit(entity)
        if (!NpcCombatRollBridge.isRolling(target)) {
            if (fight.moveset.parryDamage > 0.0) target.hurt(entity.damageSources().mobAttack(entity), fight.moveset.parryDamage.toFloat())
            target.knockback(fight.moveset.parryKnockback, entity.x - target.x, entity.z - target.z)
            target.hurtMarked = true
        } else {
            entity.debugGoal = "parry_roll_whiff"
        }
    }

    private fun tickParry(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "parry"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "parry")
        faceTarget(entity, target)
        entity.navigation.stop()
        if (entity.tickCount - fight.phaseStartedTick < fight.moveset.guardCounterTicks) return true
        startOffensePhase(entity, target, fight)
        return true
    }

    private fun startGuardRoll(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.GUARD_ROLL)
        fight.activeMove = null
        fight.nextActionTick = entity.tickCount + fight.moveset.guardRollTicks
        playTemplate(entity, fight, guardRollTemplate(fight), forceRestart = true)
        entity.navigation.stop()
        blockBossHit(entity)
        performGuardRoll(entity, target, fight)
    }

    private fun tickGuardRoll(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "guard_roll"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "rolling")
        entity.navigation.stop()
        if (entity.tickCount < fight.nextActionTick) return true
        startOffensePhase(entity, target, fight)
        return true
    }

    private fun startGuardDodge(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.GUARD_DODGE)
        fight.activeMove = null
        fight.nextActionTick = entity.tickCount + fight.moveset.guardDodgeTicks
        playTemplate(entity, fight, guardDodgeTemplate(fight), forceRestart = true)
        entity.navigation.stop()
        blockBossHit(entity)
        performGuardDodge(entity, target, fight)
    }

    private fun tickGuardDodge(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "guard_dodge"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "dodging")
        entity.navigation.stop()
        if (entity.tickCount < fight.nextActionTick) return true
        startOffensePhase(entity, target, fight)
        return true
    }

    private fun executeMoveHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        when (move.kind) {
            NpcBossMoveKinds.AREA -> areaHit(entity, target, fight, move)
            NpcBossMoveKinds.PROJECTILE -> projectileHit(entity, target, fight, move)
            NpcBossMoveKinds.SUPPORT -> supportHit(entity, target, fight, move)
            else -> attackHit(entity, target, fight, move)
        }
    }

    private fun attackHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        val level = entity.level() as? ServerLevel ?: return
        entity.swing(InteractionHand.MAIN_HAND, true)
        if (entity.distanceToSqr(target) > move.range * move.range || !targetInForwardCone(entity, target, move.arcDegrees)) {
            entity.debugGoal = "${move.id}_miss"
            return
        }
        if (NpcCombatRollBridge.isRolling(target)) {
            entity.debugGoal = "${move.id}_roll_whiff"
            level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.HOSTILE, 0.8f, 1.1f)
            return
        }
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0f, 0.85f + entity.random.nextFloat() * 0.2f)
        target.invulnerableTime = 0
        if (target.hurt(entity.damageSources().mobAttack(entity), phaseDamage(fight, move.damage))) {
            target.knockback(move.knockback, entity.x - target.x, entity.z - target.z)
            target.hurtMarked = true
            showBossBalloon(entity, target, fight, fight.balloons.hitPlayer, "hit_player")
        }
    }

    private fun areaHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        val level = entity.level() as? ServerLevel ?: return
        if (move.releaseAnimationId != move.animationId) {
            playTemplate(entity, fight, projectileReleaseTemplate(fight, move), forceRestart = true)
        }
        if (move.releaseSoundId.isNotBlank()) {
            playBossSound(level, move.releaseSoundId, SoundEvents.EVOKER_CAST_SPELL, entity.x, entity.eyeY, entity.z, 0.75f, 1.0f)
        }
        sendMoveParticle(level, move.releaseParticle, entity.x, entity.eyeY, entity.z, 10, 0.3, 0.3, 0.3, 0.02, ParticleTypes.END_ROD)
        val radius = move.areaRadius.takeIf { value -> value > 0.0 } ?: move.range
        if (entity.distanceToSqr(target) > radius * radius) {
            entity.debugGoal = "${move.id}_miss"
            return
        }
        if (NpcCombatRollBridge.isRolling(target)) {
            entity.debugGoal = "${move.id}_roll_whiff"
            level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.HOSTILE, 0.8f, 1.1f)
            return
        }
        playMoveImpactVfx(level, move, target.position().add(0.0, 1.0, 0.0), radius, SoundEvents.GENERIC_EXPLODE.value())
        target.invulnerableTime = 0
        if (target.hurt(entity.damageSources().mobAttack(entity), phaseDamage(fight, move.damage))) {
            target.knockback(move.knockback, entity.x - target.x, entity.z - target.z)
            target.hurtMarked = true
            showBossBalloon(entity, target, fight, fight.balloons.hitPlayer, "hit_player")
        }
    }

    private fun projectileHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        if (move.projectileType == "magic") {
            magicProjectileHit(entity, target, fight, move)
            return
        }
        val level = entity.level() as? ServerLevel ?: return
        faceTarget(entity, target)
        playTemplate(entity, fight, projectileReleaseTemplate(fight, move), forceRestart = true)
        playBossSound(level, move.releaseSoundId, SoundEvents.ARROW_SHOOT, entity.x, entity.eyeY, entity.z, 1.0f, 0.9f + entity.random.nextFloat() * 0.25f)

        val eye = entity.getEyePosition()
        val firstAim = target.getEyePosition().subtract(eye).takeIf { vector -> vector.lengthSqr() > 0.0001 }?.normalize() ?: Vec3(0.0, 0.0, 1.0)
        val start = eye.add(firstAim.scale(0.45)).add(0.0, -0.15, 0.0)
        val aim = target.getEyePosition().add(0.0, -0.1, 0.0)
        val baseDirection = aim.subtract(start).takeIf { vector -> vector.lengthSqr() > 0.0001 }?.normalize() ?: firstAim
        sendMoveParticle(level, move.releaseParticle, start.x, start.y, start.z, 10, 0.2, 0.2, 0.2, 0.02, ParticleTypes.END_ROD)
        val count = move.projectileCount.coerceAtLeast(1)
        repeat(count) { index ->
            val offset = spreadOffset(index, count, move.projectileSpreadDegrees)
            val direction = rotateY(baseDirection, offset)
            val arrow = Arrow(level, entity, ItemStack(Items.ARROW), fight.armory.mainHand)
            arrow.setPos(start.x, start.y, start.z)
            arrow.setBaseDamage(phaseDamage(fight, move.damage).toDouble())
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED
            arrow.shoot(direction.x, direction.y, direction.z, move.projectileSpeed.toFloat(), move.projectileInaccuracy.toFloat())
            level.addFreshEntity(arrow)
            if (tracksArrowVfx(move)) {
                fight.trackedArrows.add(
                    TrackedBossArrow(
                        arrowId = arrow.uuid,
                        move = move,
                        position = arrow.position().add(0.0, arrow.bbHeight.toDouble() * 0.5, 0.0),
                        ticksRemaining = arrowVfxLifetimeTicks(move),
                    ),
                )
            }
        }
    }

    private fun magicProjectileHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        val level = entity.level() as? ServerLevel ?: return
        faceTarget(entity, target)
        playTemplate(entity, fight, projectileReleaseTemplate(fight, move), forceRestart = true)
        playBossSound(level, move.releaseSoundId, SoundEvents.EVOKER_CAST_SPELL, entity.x, entity.eyeY, entity.z, 0.9f, 0.9f + entity.random.nextFloat() * 0.25f)
        sendMoveParticle(level, move.releaseParticle, entity.x, entity.eyeY, entity.z, 10, 0.2, 0.2, 0.2, 0.02, ParticleTypes.END_ROD)

        val start = entity.getEyePosition().add(0.0, -0.08, 0.0)
        val aim = target.getEyePosition().add(0.0, -0.08, 0.0)
        val baseDirection = aim.subtract(start).takeIf { vector -> vector.lengthSqr() > 0.0001 }?.normalize() ?: entity.lookAngle.normalize()
        val count = move.projectileCount.coerceAtLeast(1)
        repeat(count) { index ->
            val offset = spreadOffset(index, count, move.projectileSpreadDegrees)
            val direction = rotateY(baseDirection, offset)
            fight.magicProjectiles.add(
                MagicBossProjectile(
                    move = move,
                    position = start.add(direction.scale(0.45)),
                    velocity = direction.scale(move.projectileSpeed),
                    ticksRemaining = magicProjectileLifetimeTicks(move),
                ),
            )
        }
    }

    private fun tickMagicProjectiles(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        val level = entity.level() as? ServerLevel ?: return
        val iterator = fight.magicProjectiles.iterator()
        while (iterator.hasNext()) {
            val projectile = iterator.next()
            if (projectile.ticksRemaining-- <= 0) {
                iterator.remove()
                continue
            }
            val previous = projectile.position
            val next = previous.add(projectile.velocity)
            val blockHit = level.clip(ClipContext(previous, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity))
            if (blockHit.type != HitResult.Type.MISS) {
                magicImpact(entity, target, fight, projectile, blockHit.location)
                iterator.remove()
                if (active[entity.uuid] !== fight) return
                continue
            }
            projectile.position = next
            level.sendParticles(bossParticle(projectile.move.projectileParticle, ParticleTypes.END_ROD), next.x, next.y, next.z, 2, 0.06, 0.06, 0.06, 0.0)
            if (target.boundingBox.inflate(projectile.move.impactRadius).contains(next)) {
                magicImpact(entity, target, fight, projectile, next)
                iterator.remove()
                if (active[entity.uuid] !== fight) return
            }
        }
    }

    private fun tickTrackedArrows(entity: ChowNpcEntity, fight: ActiveBossFight) {
        val level = entity.level() as? ServerLevel ?: return
        val iterator = fight.trackedArrows.iterator()
        while (iterator.hasNext()) {
            val tracked = iterator.next()
            val arrow = level.getEntity(tracked.arrowId) as? AbstractArrow
            if (arrow == null || !arrow.isAlive || tracked.ticksRemaining-- <= 0) {
                playTrackedArrowImpactVfx(level, tracked.move, tracked.position)
                iterator.remove()
                continue
            }
            val position = arrow.position().add(0.0, arrow.bbHeight.toDouble() * 0.5, 0.0)
            tracked.position = position
            if (tracked.move.projectileParticle.isNotBlank()) {
                level.sendParticles(bossParticle(tracked.move.projectileParticle, ParticleTypes.END_ROD), position.x, position.y, position.z, 1, 0.03, 0.03, 0.03, 0.0)
            }
            if (arrow.tickCount > ARROW_VFX_MIN_IMPACT_TICKS && arrow.deltaMovement.lengthSqr() <= ARROW_VFX_STOPPED_SPEED_SQR) {
                playTrackedArrowImpactVfx(level, tracked.move, position)
                iterator.remove()
            }
        }
    }

    private fun playTrackedArrowImpactVfx(level: ServerLevel, move: NpcBossMoveDefinition, position: Vec3) {
        val particleId = move.impactParticle.ifBlank { move.projectileParticle }
        if (particleId.isNotBlank()) {
            level.sendParticles(bossParticle(particleId, ParticleTypes.POOF), position.x, position.y, position.z, 10, move.impactRadius * 0.25, move.impactRadius * 0.25, move.impactRadius * 0.25, 0.02)
        }
        if (move.impactSoundId.isNotBlank()) {
            playBossSound(level, move.impactSoundId, SoundEvents.ARROW_HIT, position.x, position.y, position.z, 0.45f, 1.25f)
        }
    }

    private fun magicImpact(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, projectile: MagicBossProjectile, position: Vec3) {
        val level = entity.level() as? ServerLevel ?: return
        val radius = projectile.move.impactRadius
        playMoveImpactVfx(level, projectile.move, position, radius, SoundEvents.GENERIC_EXPLODE.value())
        if (target.distanceToSqr(position) > radius * radius && !target.boundingBox.inflate(radius).contains(position)) return
        if (NpcCombatRollBridge.isRolling(target)) {
            entity.debugGoal = "${projectile.move.id}_roll_whiff"
            level.playSound(null, position.x, position.y, position.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.HOSTILE, 0.8f, 1.1f)
            return
        }
        if (target.isBlocking) {
            entity.debugGoal = "${projectile.move.id}_shield_block"
            level.playSound(null, target.x, target.y, target.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9f, 1.0f)
            return
        }
        target.invulnerableTime = 0
        if (target.hurt(entity.damageSources().mobAttack(entity), phaseDamage(fight, projectile.move.damage))) {
            val knockback = projectile.move.knockback.coerceAtLeast(0.0)
            if (knockback > 0.0) target.knockback(knockback, -projectile.velocity.x, -projectile.velocity.z)
            target.hurtMarked = true
            applyMagicStatus(target, projectile.move)
            showBossBalloon(entity, target, fight, fight.balloons.hitPlayer, "hit_player")
        }
    }

    private fun applySelfHeal(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        if (move.selfHealAmount <= 0.0) return
        val key = "${fight.bossPhaseIndex}:${move.id}"
        val used = fight.supportUses[key] ?: 0
        if (move.selfHealMaxUsesPerPhase > 0 && used >= move.selfHealMaxUsesPerPhase) return
        val phase = currentBossPhase(fight)
        val phaseCapRatio = if (phase.startsAtHealthRatio < 1.0) {
            (phase.startsAtHealthRatio - 0.05).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        val capRatio = move.selfHealCapHealthRatio.coerceAtMost(phaseCapRatio).toFloat()
        val cap = fight.maxHealth * capRatio
        val before = fight.health
        fight.health = (fight.health + move.selfHealAmount.toFloat()).coerceAtMost(cap).coerceAtMost(fight.maxHealth)
        if (fight.health <= before) return
        fight.supportUses[key] = used + 1
        updateBossBar(entity, target, fight)
    }

    private fun applyAbsorption(entity: ChowNpcEntity, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        if (move.absorptionAmount <= 0.0 || move.absorptionTicks <= 0) return
        fight.absorptionHealth = fight.absorptionHealth.coerceAtLeast(move.absorptionAmount.toFloat())
        fight.absorptionUntilTick = entity.tickCount + move.absorptionTicks
    }

    private fun tickSupportEffects(entity: ChowNpcEntity, fight: ActiveBossFight) {
        if (fight.absorptionUntilTick > 0 && entity.tickCount > fight.absorptionUntilTick) {
            fight.absorptionUntilTick = 0
            fight.absorptionHealth = 0.0f
        }
    }

    private fun supportHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        val level = entity.level() as? ServerLevel ?: return
        faceTarget(entity, target)
        playTemplate(entity, fight, projectileReleaseTemplate(fight, move), forceRestart = true)
        val position = entity.position().add(0.0, 1.0, 0.0)
        playBossSound(level, move.releaseSoundId, SoundEvents.EVOKER_CAST_SPELL, position.x, position.y, position.z, 0.8f, 1.1f)
        sendMoveParticle(level, move.supportParticle, position.x, position.y, position.z, 24, 0.55, 0.7, 0.55, 0.04, ParticleTypes.END_ROD)
        applySelfHeal(entity, target, fight, move)
        applyAbsorption(entity, fight, move)
        playBossSound(level, move.impactSoundId.ifBlank { move.releaseSoundId }, SoundEvents.EVOKER_CAST_SPELL, position.x, position.y, position.z, 0.7f, 1.25f)
    }

    private fun acceptRecoveryHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, damage: Float) {
        val shielded = damage.coerceAtMost(fight.absorptionHealth)
        if (shielded > 0.0f) fight.absorptionHealth = (fight.absorptionHealth - shielded).coerceAtLeast(0.0f)
        val healthDamage = (damage - shielded).coerceAtLeast(0.0f)
        fight.health = (fight.health - healthDamage).coerceAtLeast(0.0f)
        fight.recoveryHitsTaken++
        fight.hurtUntilTick = entity.tickCount + PLAYERLIKE_HURT_TICKS
        fight.guardAfterHurtTick = if (fight.recoveryHitsTaken >= fight.moveset.recoveryHitsAllowed) fight.hurtUntilTick else 0
        updateBossBar(entity, target, fight)
        playTemplate(entity, fight, hurtTemplate(fight), forceRestart = true)
        entity.navigation.stop()
        entity.knockback(HURT_KNOCKBACK, target.x - entity.x, target.z - entity.z)
        entity.hurtMarked = true
        showBossBalloon(entity, target, fight, fight.balloons.tookDamage, "took_damage")
        if (fight.health <= 0.0f) {
            defeat(entity, target, fight)
            return
        }
        maybeAdvanceBossPhase(entity, target, fight)
    }

    private fun blockBossHit(entity: ChowNpcEntity) {
        val level = entity.level() as? ServerLevel ?: return
        ParrySoundFeature.play(entity, SoundSource.HOSTILE, 0.95f, 0.95f + entity.random.nextFloat() * 0.1f)
        level.sendParticles(ParticleTypes.END_ROD, entity.x, entity.y + 1.05, entity.z, 18, 0.45, 0.45, 0.45, 0.02)
        entity.hurtTime = 0
        entity.hurtMarked = false
    }

    private fun startChase(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.CHASE)
        if (fight.tacticPhase == BossTacticPhase.OFFENSE && fight.offenseAttacksRemaining <= 0) {
            fight.offenseAttacksRemaining = offenseChainCount(entity, fight)
        }
        val mode = if (fight.tacticPhase == BossTacticPhase.OFFENSE) "offense" else "chase"
        fight.nextActionTick = 0
        fight.recoveryHitsTaken = 0
        fight.hurtUntilTick = 0
        fight.guardAfterHurtTick = 0
        fight.recoveryReturnToOffense = false
        fight.activeMove = null
        playTemplate(entity, fight, approachTemplate(fight), forceRestart = true)
        showModeBalloon(entity, target, fight, mode)
    }

    private fun selectMove(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): NpcBossMoveDefinition? {
        val distance = entity.distanceTo(target).toDouble()
        if (distance > fight.moveset.attackStartDistance) return null
        val baseCandidates = fight.moveset.moves
            .asSequence()
            .filter { move -> move.weight > 0 }
            .filter { move -> fight.bossPhaseIndex in move.minPhaseIndex..move.maxPhaseIndex }
            .filter { move -> entity.tickCount >= (fight.moveCooldowns[move.id] ?: 0) }
            .filter { move -> distance >= move.minDistance && distance <= move.maxDistance }
            .filter { move -> move.kind == NpcBossMoveKinds.ROLL || targetInForwardCone(entity, target, move.arcDegrees) }
            .toList()
        val offenseCandidates = if (fight.tacticPhase == BossTacticPhase.OFFENSE) {
            baseCandidates.filter { move -> move.kind != NpcBossMoveKinds.ROLL }.ifEmpty { baseCandidates }
        } else {
            baseCandidates
        }
        val candidates = if (fight.tacticPhase == BossTacticPhase.OFFENSE && offenseCandidates.size > 1 && fight.lastMoveId.isNotBlank()) {
            offenseCandidates.filter { move -> move.id != fight.lastMoveId }.ifEmpty { offenseCandidates }
        } else {
            offenseCandidates
        }
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { move -> move.weight }.coerceAtLeast(1)
        var roll = entity.random.nextInt(totalWeight)
        for (move in candidates) {
            roll -= move.weight
            if (roll < 0) return move
        }
        return candidates.last()
    }

    private fun playMoveAnimation(entity: ChowNpcEntity, fight: ActiveBossFight, move: NpcBossMoveDefinition, forceRestart: Boolean = false) {
        val template = NpcAnimationTemplate(
            id = "boss_${fight.moveset.id}_${move.id}",
            animationId = move.animationId,
            animationSource = move.animationSource,
            loop = false,
            durationTicks = move.durationTicks,
            mainHand = fight.armory.mainHand,
            offHand = fight.armory.offHand,
        )
        if (!forceRestart && fight.activeTemplateId == template.id) return
        if (!NpcCustomAnimationController.play(entity, template)) NpcCustomAnimationController.play(entity, playerlikeFallbackTemplate(fight))
        fight.activeTemplateId = template.id
    }

    private fun playMoveCastVfx(entity: ChowNpcEntity, move: NpcBossMoveDefinition) {
        val level = entity.level() as? ServerLevel ?: return
        playBossSound(level, move.castSoundId, SoundEvents.EVOKER_PREPARE_ATTACK, entity.x, entity.eyeY, entity.z, 0.55f, 1.2f)
        sendMoveParticle(level, move.castParticle, entity.x, entity.y + 0.65, entity.z, 10, 0.35, 0.5, 0.35, 0.02, ParticleTypes.END_ROD)
    }

    private fun playMoveImpactVfx(level: ServerLevel, move: NpcBossMoveDefinition, position: Vec3, radius: Double, fallbackSound: SoundEvent) {
        val particleFallback = if (move.kind == NpcBossMoveKinds.AREA) ParticleTypes.SWEEP_ATTACK else ParticleTypes.POOF
        val particleId = move.impactParticle.ifBlank { move.releaseParticle }
        val count = if (move.kind == NpcBossMoveKinds.AREA) 18 else 14
        level.sendParticles(bossParticle(particleId, particleFallback), position.x, position.y, position.z, count, radius * 0.35, radius * 0.35, radius * 0.35, 0.02)
        playBossSound(level, move.impactSoundId, fallbackSound, position.x, position.y, position.z, 0.45f, 1.35f)
    }

    private fun sendMoveParticle(level: ServerLevel, particleId: String, x: Double, y: Double, z: Double, count: Int, dx: Double, dy: Double, dz: Double, speed: Double, fallback: SimpleParticleType) {
        if (particleId.isBlank()) return
        level.sendParticles(bossParticle(particleId, fallback), x, y, z, count, dx, dy, dz, speed)
    }

    private fun performNpcRoll(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, move: NpcBossMoveDefinition) {
        val level = entity.level() as? ServerLevel
        val direction = rollDirection(entity, target, move)
        val distance = move.rollDistance.coerceAtLeast(1.0) * currentBossPhase(fight).speedMultiplier.coerceIn(0.75, 1.6)
        val desired = entity.position().add(direction.scale(distance))
        level?.let { sendMoveParticle(it, move.supportParticle, entity.x, entity.y + 0.65, entity.z, 10, 0.25, 0.25, 0.25, 0.02, ParticleTypes.POOF) }
        fight.npcIframeUntilTick = (fight.phaseStartedTick + move.iframeEndTick).coerceAtLeast(entity.tickCount)
        NpcCombatRollBridge.applyNpcIframes(entity, move.iframeEndTick - move.iframeStartTick)
        val impulse = 0.55 * currentBossPhase(fight).speedMultiplier.coerceIn(0.75, 1.6)
        entity.deltaMovement = Vec3(direction.x * impulse, entity.deltaMovement.y.coerceAtLeast(0.05), direction.z * impulse)
        entity.hurtMarked = true
        entity.moveControl.setWantedPosition(desired.x, desired.y, desired.z, phaseSpeed(fight, BOSS_CHASE_SPEED))
        level?.let { sendMoveParticle(it, move.supportParticle, desired.x, desired.y + 0.65, desired.z, 10, 0.25, 0.25, 0.25, 0.02, ParticleTypes.POOF) }
    }

    private fun performGuardRoll(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        val direction = sideRollDirection(entity, target)
        val distance = fight.moveset.guardRollDistance.coerceAtLeast(1.0) * currentBossPhase(fight).speedMultiplier.coerceIn(0.75, 1.6)
        val desired = entity.position().add(direction.scale(distance))
        val iframeTicks = fight.moveset.guardRollIframeTicks
        fight.npcIframeUntilTick = entity.tickCount + iframeTicks
        if (iframeTicks > 0) NpcCombatRollBridge.applyNpcIframes(entity, iframeTicks)
        val impulse = 0.55 * currentBossPhase(fight).speedMultiplier.coerceIn(0.75, 1.6)
        entity.deltaMovement = Vec3(direction.x * impulse, entity.deltaMovement.y.coerceAtLeast(0.05), direction.z * impulse)
        entity.hurtMarked = true
        entity.moveControl.setWantedPosition(desired.x, desired.y, desired.z, phaseSpeed(fight, BOSS_CHASE_SPEED))
    }

    private fun performGuardDodge(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        val direction = guardDodgeDirection(entity, target, fight)
        val distance = fight.moveset.guardDodgeDistance.coerceAtLeast(1.0) * currentBossPhase(fight).speedMultiplier.coerceIn(0.75, 1.6)
        val desired = entity.position().add(direction.scale(distance))
        val iframeTicks = fight.moveset.guardDodgeIframeTicks
        fight.npcIframeUntilTick = entity.tickCount + iframeTicks
        if (iframeTicks > 0) NpcCombatRollBridge.applyNpcIframes(entity, iframeTicks)
        val impulse = 0.5 * currentBossPhase(fight).speedMultiplier.coerceIn(0.75, 1.6)
        entity.deltaMovement = Vec3(direction.x * impulse, entity.deltaMovement.y.coerceAtLeast(0.04), direction.z * impulse)
        entity.hurtMarked = true
        entity.moveControl.setWantedPosition(desired.x, desired.y, desired.z, phaseSpeed(fight, BOSS_CHASE_SPEED))
    }

    private fun rollDirection(entity: ChowNpcEntity, target: ServerPlayer, move: NpcBossMoveDefinition): Vec3 {
        val toTargetRaw = Vec3(target.x - entity.x, 0.0, target.z - entity.z)
        val toTarget = if (toTargetRaw.lengthSqr() > 0.0001) toTargetRaw.normalize() else Vec3(0.0, 0.0, 1.0)
        return when (move.rollDirection) {
            "forward" -> toTarget
            "side" -> {
                val side = Vec3(-toTarget.z, 0.0, toTarget.x)
                side.scale(if (entity.random.nextBoolean()) 1.0 else -1.0)
            }
            else -> toTarget.scale(-1.0)
        }
    }

    private fun sideRollDirection(entity: ChowNpcEntity, target: ServerPlayer): Vec3 {
        val toTargetRaw = Vec3(target.x - entity.x, 0.0, target.z - entity.z)
        val toTarget = if (toTargetRaw.lengthSqr() > 0.0001) toTargetRaw.normalize() else Vec3(0.0, 0.0, 1.0)
        val side = Vec3(-toTarget.z, 0.0, toTarget.x)
        return side.scale(if (entity.random.nextBoolean()) 1.0 else -1.0)
    }

    private fun guardDodgeDirection(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Vec3 {
        val toTargetRaw = Vec3(target.x - entity.x, 0.0, target.z - entity.z)
        val toTarget = if (toTargetRaw.lengthSqr() > 0.0001) toTargetRaw.normalize() else Vec3(0.0, 0.0, 1.0)
        return when (fight.moveset.guardDodgeDirection) {
            "forward" -> toTarget
            "side" -> {
                val side = Vec3(-toTarget.z, 0.0, toTarget.x)
                side.scale(if (entity.random.nextBoolean()) 1.0 else -1.0)
            }
            else -> toTarget.scale(-1.0)
        }
    }

    private fun prepareStrafe(entity: ChowNpcEntity, fight: ActiveBossFight) {
        fight.strafeSide = if (entity.random.nextBoolean()) 1 else -1
        fight.nextStrafeFlipTick = entity.tickCount + STRAFE_MIN_FLIP_TICKS + entity.random.nextInt(STRAFE_RANDOM_FLIP_TICKS + 1)
    }

    private fun strafeAroundTarget(
        entity: ChowNpcEntity,
        target: ServerPlayer,
        fight: ActiveBossFight,
        speed: Double = BOSS_STRAFE_SPEED,
        step: Double = STRAFE_STEP,
        radius: Double = STRAFE_RADIUS,
        innerRadius: Double = STRAFE_INNER_RADIUS,
        outerRadius: Double = STRAFE_OUTER_RADIUS,
    ) {
        if (entity.tickCount >= fight.nextStrafeFlipTick) {
            fight.strafeSide *= -1
            fight.nextStrafeFlipTick = entity.tickCount + STRAFE_MIN_FLIP_TICKS + entity.random.nextInt(STRAFE_RANDOM_FLIP_TICKS + 1)
        }
        val fromTargetRaw = Vec3(entity.x - target.x, 0.0, entity.z - target.z)
        val radial = when {
            fromTargetRaw.lengthSqr() > 0.0001 -> fromTargetRaw.normalize()
            else -> Vec3(1.0, 0.0, 0.0)
        }
        val tangent = Vec3(-radial.z, 0.0, radial.x).scale(fight.strafeSide.toDouble())
        val distanceSqr = entity.distanceToSqr(target)
        val desiredRadius = when {
            distanceSqr < STRAFE_TOO_CLOSE_DISTANCE_SQR -> outerRadius
            distanceSqr > STRAFE_TOO_FAR_DISTANCE_SQR -> innerRadius
            else -> radius
        }
        val desired = target.position().add(radial.scale(desiredRadius)).add(tangent.scale(step))
        val adjustedSpeed = phaseSpeed(fight, speed)
        entity.navigation.moveTo(desired.x, target.y, desired.z, adjustedSpeed)
        if (entity.navigation.isDone) entity.moveControl.setWantedPosition(desired.x, target.y, desired.z, adjustedSpeed)
    }

    private fun holdRangedSpacing(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        if (entity.tickCount >= fight.nextStrafeFlipTick) {
            fight.strafeSide *= -1
            fight.nextStrafeFlipTick = entity.tickCount + STRAFE_MIN_FLIP_TICKS + entity.random.nextInt(STRAFE_RANDOM_FLIP_TICKS + 1)
        }
        val fromTargetRaw = Vec3(entity.x - target.x, 0.0, entity.z - target.z)
        val radial = when {
            fromTargetRaw.lengthSqr() > 0.0001 -> fromTargetRaw.normalize()
            else -> Vec3(1.0, 0.0, 0.0)
        }
        val tangent = Vec3(-radial.z, 0.0, radial.x).scale(fight.strafeSide.toDouble())
        val (innerRadius, outerRadius) = rangedSpacingBounds(fight)
        val distance = entity.distanceTo(target).toDouble()
        val desiredRadius = when {
            distance < innerRadius -> outerRadius
            distance > outerRadius -> outerRadius
            else -> (innerRadius + outerRadius) * 0.5
        }
        val desired = target.position().add(radial.scale(desiredRadius)).add(tangent.scale(RANGED_STRAFE_STEP))
        val adjustedSpeed = phaseSpeed(fight, BOSS_STRAFE_SPEED)
        entity.navigation.moveTo(desired.x, target.y, desired.z, adjustedSpeed)
        if (entity.navigation.isDone) entity.moveControl.setWantedPosition(desired.x, target.y, desired.z, adjustedSpeed)
    }

    private fun usesRangedBossSpacing(fight: ActiveBossFight): Boolean =
        fight.moveset.moves.any { move -> move.kind == NpcBossMoveKinds.PROJECTILE || move.kind == NpcBossMoveKinds.SUPPORT } &&
            fight.moveset.moves.none { move -> move.kind == NpcBossMoveKinds.MELEE }

    private fun rangedSpacingBounds(fight: ActiveBossFight): Pair<Double, Double> {
        val outer = (fight.moveset.attackStartDistance - 1.0).coerceIn(RANGED_MIN_OUTER_RADIUS, RANGED_MAX_OUTER_RADIUS)
        val inner = (outer * RANGED_INNER_RADIUS_FACTOR).coerceIn(RANGED_MIN_INNER_RADIUS, outer - RANGED_MIN_WIDTH)
        return inner to outer
    }

    private fun defeat(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        showBossBalloon(entity, target, fight, fight.balloons.defeat, "defeat")
        if (fight.debug) {
            SnackbarNetwork.send(target, SnackbarNotification.npc(fight.npcId, "BOSS TEST WON", fight.displayName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            finish(entity, fight)
            return
        }
        finish(entity, fight, protectResultDialog = true)
        val definition = NpcConfig.get(fight.npcId) ?: return
        if (ClassMentorQuestService.onMentorDuelWon(target, entity, definition)) return
        SnackbarNetwork.send(target, SnackbarNotification.npc(definition.id, "BOSS DEFEATED", definition.displayName(), SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        openDefeatDialog(target, entity, definition)
    }

    private fun bossVictory(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        showBossBalloon(entity, target, fight, fight.balloons.victory, "victory")
        if (fight.debug) {
            healDuelist(target)
            SnackbarNetwork.send(target, SnackbarNotification.npc(fight.npcId, "BOSS TEST LOST", "${fight.displayName} healed you.", SnackbarType.GENERIC, SnackbarSounds.GENERIC))
            finish(entity, fight)
            return
        }
        finish(entity, fight, protectResultDialog = true)
        healDuelist(target)
        val definition = NpcConfig.get(fight.npcId) ?: return
        SnackbarNetwork.send(target, SnackbarNotification.npc(definition.id, "BOSS WON", "${definition.displayName()} healed you.", SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        openVictoryDialog(target, entity, definition)
    }

    private fun cancel(entity: ChowNpcEntity, fight: ActiveBossFight, reason: String) {
        finish(entity, fight)
        val player = (entity.level() as? ServerLevel)?.server?.playerList?.getPlayer(fight.targetId)
        if (player != null) SnackbarNetwork.send(player, SnackbarNotification.npc(fight.npcId, "BOSS FIGHT RESET", reason, SnackbarType.ERROR, SnackbarSounds.ERROR))
        if (fight.debug) return
        entity.debugActivity = NpcFeature.smartBrainDefinition(entity)?.let { def -> NpcFeature.activityFor(entity, def) } ?: "idle"
        entity.debugGoal = "boss_reset"
    }

    private fun finish(entity: ChowNpcEntity, fight: ActiveBossFight, protectResultDialog: Boolean = false) {
        active.remove(entity.uuid)
        fight.bossBar.removeAllPlayers()
        clearBossBar(entity, fight)
        entity.navigation.stop()
        NpcCustomAnimationController.restore(entity, fight.animationSnapshot)
        val restoredHealth = if (protectResultDialog) entity.maxHealth else fight.originalHealth.coerceIn(1.0f, entity.maxHealth)
        entity.setHealth(restoredHealth.coerceIn(1.0f, entity.maxHealth))
        if (fight.debug) {
            resultProtection.remove(entity.uuid)
            entity.updatePassThroughInteractions(false)
            entity.discard()
            return
        }
        if (protectResultDialog) {
            resultProtection[entity.uuid] = BossResultProtection(entity.tickCount + RESULT_DIALOG_PROTECTION_TICKS, restoredHealth)
            entity.updatePassThroughInteractions(true)
        } else {
            resultProtection.remove(entity.uuid)
            entity.updatePassThroughInteractions(false)
        }
    }

    private fun openDefeatDialog(player: ServerPlayer, entity: ChowNpcEntity, definition: NpcDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val fallback = "${definition.name} lowers their weapon. Good fight, ${player.gameProfile.name}. I am done."
        val llmEnabled = NpcConfig.settings().llm.enabled
        val responseToken = if (llmEnabled) NpcDialogTokens.next() else 0L
        NpcNetwork.openDialog(
            player,
            NpcFeature.dialogPayload(
                definition,
                entity,
                if (llmEnabled) "..." else fallback,
                false,
                friendship.level,
                closeOnly = true,
                closeLabel = "OKAY",
                responseToken = responseToken,
                dialogMode = "boss",
            ),
        )
        if (!llmEnabled) {
            NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_boss_defeat")
            return
        }
        NpcLlmService.event(
            player,
            entity,
            definition,
            fallback,
            "${player.gameProfile.name} just defeated you in a non-lethal boss duel. Reply as ${definition.name} with one short in-character line. Concede the fight, respect the player, and make clear you are alive and the duel is over.",
            inputLabel = "Boss duel result",
            npcRecordType = "npc_boss_defeat",
            responseToken = responseToken,
        )
    }

    private fun withinTether(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        val outside = entity.position().distanceToSqr(fight.startPos) > TETHER_DISTANCE_SQR || target.position().distanceToSqr(fight.startPos) > TETHER_DISTANCE_SQR
        if (!outside) {
            fight.tetherExceededSince = 0
            return true
        }
        if (fight.tetherExceededSince == 0) fight.tetherExceededSince = entity.tickCount
        return entity.tickCount - fight.tetherExceededSince <= TETHER_GRACE_TICKS
    }

    private fun approachTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_approach",
        animationId = fight.moveset.approachAnimationId,
        animationSource = fight.moveset.approachAnimationSource,
        loop = true,
        durationTicks = PLAYERLIKE_READY_TICKS,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
    )

    private fun strafeTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_strafe",
        animationId = fight.moveset.strafeAnimationId,
        animationSource = fight.moveset.strafeAnimationSource,
        loop = true,
        durationTicks = PLAYERLIKE_READY_TICKS,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
        speed = PLAYERLIKE_STRAFE_ANIMATION_SPEED,
    )

    private fun recoveryTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_recovery",
        animationId = fight.moveset.recoveryAnimationId,
        animationSource = fight.moveset.recoveryAnimationSource,
        loop = true,
        durationTicks = PLAYERLIKE_GUARD_TICKS,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
        speed = PLAYERLIKE_STRAFE_ANIMATION_SPEED,
    )

    private fun guardTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_guard",
        animationId = fight.moveset.guardAnimationId,
        animationSource = fight.moveset.guardAnimationSource,
        loop = true,
        durationTicks = PLAYERLIKE_GUARD_TICKS,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
    )

    private fun parryTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_parry",
        animationId = fight.moveset.parryAnimationId,
        animationSource = fight.moveset.parryAnimationSource,
        loop = false,
        durationTicks = fight.moveset.guardCounterTicks,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
    )

    private fun guardRollTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_guard_roll",
        animationId = fight.moveset.guardRollAnimationId,
        animationSource = fight.moveset.guardRollAnimationSource,
        loop = false,
        durationTicks = fight.moveset.guardRollTicks,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
    )

    private fun guardDodgeTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_guard_dodge",
        animationId = fight.moveset.guardDodgeAnimationId,
        animationSource = fight.moveset.guardDodgeAnimationSource,
        loop = false,
        durationTicks = fight.moveset.guardDodgeTicks,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
    )

    private fun hurtTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_hurt",
        animationId = fight.moveset.hurtAnimationId,
        animationSource = fight.moveset.hurtAnimationSource,
        loop = false,
        durationTicks = PLAYERLIKE_HURT_TICKS,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
    )

    private fun playerlikeFallbackTemplate(fight: ActiveBossFight): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_playerlike_fallback",
        animationId = NpcBossMovesetDefinition.DEFAULT_READY_ANIMATION,
        animationSource = NpcBossAnimationSources.PLAYERLIKE,
        loop = true,
        durationTicks = PLAYERLIKE_GUARD_TICKS,
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
    )

    private fun projectileReleaseTemplate(fight: ActiveBossFight, move: NpcBossMoveDefinition): NpcAnimationTemplate = NpcAnimationTemplate(
        id = "boss_${fight.moveset.id}_${move.id}_release",
        animationId = move.releaseAnimationId,
        animationSource = move.releaseAnimationSource,
        loop = false,
        durationTicks = (move.durationTicks - (move.hitTicks.firstOrNull() ?: 0)).coerceIn(6, 20),
        mainHand = fight.armory.mainHand,
        offHand = fight.armory.offHand,
    )

    private fun playTemplate(entity: ChowNpcEntity, fight: ActiveBossFight, template: NpcAnimationTemplate, forceRestart: Boolean = false) {
        if (!forceRestart && fight.activeTemplateId == template.id) return
        if (!NpcCustomAnimationController.play(entity, template)) NpcCustomAnimationController.play(entity, playerlikeFallbackTemplate(fight))
        fight.activeTemplateId = template.id
    }

    private fun setPhase(entity: ChowNpcEntity, fight: ActiveBossFight, phase: BossFightPhase) {
        fight.phase = phase
        fight.phaseStartedTick = entity.tickCount
        fight.activeTemplateId = ""
    }

    private fun updateStatus(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, mode: String) {
        if (fight.modeLabel != mode) {
            fight.modeLabel = mode
            fight.bossBar.name = Component.literal("${fight.displayName} | NPC mode: $mode")
            showModeBalloon(entity, target, fight, mode)
        }
    }

    private fun openVictoryDialog(player: ServerPlayer, entity: ChowNpcEntity, definition: NpcDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val fallback = "${definition.name} steadies you after the duel. I win this round, ${player.gameProfile.name}. I healed you back up; breathe, learn, and try again."
        val llmEnabled = NpcConfig.settings().llm.enabled
        val responseToken = if (llmEnabled) NpcDialogTokens.next() else 0L
        NpcNetwork.openDialog(
            player,
            NpcFeature.dialogPayload(
                definition,
                entity,
                if (llmEnabled) "..." else fallback,
                false,
                friendship.level,
                closeOnly = true,
                closeLabel = "OKAY",
                responseToken = responseToken,
                dialogMode = "boss",
            ),
        )
        if (!llmEnabled) {
            NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_boss_victory")
            return
        }
        NpcLlmService.event(
            player,
            entity,
            definition,
            fallback,
            "You defeated ${player.gameProfile.name} in a non-lethal boss duel, stopped before killing them, and healed them to full health. Reply as ${definition.name} with one short in-character line. Say you won this round, the duel is over, and you healed them so they can learn and try again.",
            inputLabel = "Boss duel victory",
            npcRecordType = "npc_boss_victory",
            responseToken = responseToken,
        )
    }

    private fun showModeBalloon(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, mode: String) {
        if (fight.lastModeBalloon == mode && entity.tickCount - fight.phaseStartedTick <= STATUS_UPDATE_TICKS) return
        val messages = when (mode) {
            "offense" -> fight.balloons.chase
            "chase" -> fight.balloons.chase
            "attacking" -> fight.balloons.attack
            "recovery" -> fight.balloons.recovery
            "guard" -> fight.balloons.taunt
            "rolling" -> fight.balloons.recovery
            "parry" -> fight.balloons.parry
            else -> emptyList()
        }
        showBossBalloon(entity, target, fight, messages, mode, force = fight.shownBossBalloons == 0)
        fight.lastModeBalloon = mode
    }

    private fun showBossBalloon(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, messages: List<String>, key: String, force: Boolean = false) {
        if (!force && entity.random.nextFloat() >= BOSS_BALLOON_CHANCE) return
        val level = entity.level() as? ServerLevel ?: return
        val message = bossBalloonMessage(entity, target, fight, messages, key) ?: return
        if (NpcFeature.showBalloonToNearby(level, entity, message, BOSS_BALLOON_TICKS) > 0) fight.shownBossBalloons++
    }

    private fun bossBalloonMessage(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, messages: List<String>, key: String): String? {
        if (messages.isEmpty()) return null
        val previous = fight.lastBalloonByKey[key]
        val candidates = messages.filter { message -> message != previous }.ifEmpty { messages }
        val template = candidates[entity.random.nextInt(candidates.size)]
        fight.lastBalloonByKey[key] = template
        return template
            .replace("{player}", target.gameProfile.name)
            .replace("{npc}", fight.displayName)
            .replace("{boss}", fight.displayName)
            .replace("{phase}", key)
            .replace("{health}", fight.health.toInt().toString())
            .replace("{max_health}", fight.maxHealth.toInt().toString())
            .replace(Regex("<[^>]+>"), "")
            .trim()
            .take(BOSS_BALLOON_MAX_CHARS)
            .takeIf(String::isNotBlank)
    }

    private fun faceTarget(entity: ChowNpcEntity, target: ServerPlayer) {
        entity.lookControl.setLookAt(target, 30.0f, 30.0f)
        entity.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition())
        val direction = Vec3(target.x - entity.x, 0.0, target.z - entity.z)
        if (direction.lengthSqr() > 0.0001) {
            val yaw = (Math.toDegrees(kotlin.math.atan2(direction.z, direction.x)) - 90.0).toFloat()
            entity.yRot = yaw
            entity.yBodyRot = yaw
            entity.yHeadRot = yaw
            entity.yBodyRotO = yaw
            entity.yHeadRotO = yaw
        }
    }

    private fun targetInForwardCone(entity: ChowNpcEntity, target: ServerPlayer, arcDegrees: Double = 140.0): Boolean {
        val toTarget = Vec3(target.x - entity.x, 0.0, target.z - entity.z)
        if (toTarget.lengthSqr() <= 0.0001) return true
        val look = entity.lookAngle
        val forward = Vec3(look.x, 0.0, look.z)
        if (forward.lengthSqr() <= 0.0001) return true
        val dot = forward.normalize().dot(toTarget.normalize())
        if (arcDegrees >= 359.0) return true
        return dot >= kotlin.math.cos(Math.toRadians(arcDegrees.coerceIn(1.0, 360.0) * 0.5))
    }

    private fun bossDamageSource(entity: Entity?): ChowNpcEntity? = when (entity) {
        is ChowNpcEntity -> entity
        is Projectile -> entity.owner as? ChowNpcEntity
        else -> null
    }

    private fun spreadOffset(index: Int, count: Int, spreadDegrees: Double): Double {
        if (count <= 1 || spreadDegrees <= 0.0) return 0.0
        val center = (count - 1) * 0.5
        return (index - center) * spreadDegrees
    }

    private fun rotateY(vector: Vec3, degrees: Double): Vec3 {
        if (degrees == 0.0) return vector
        val radians = Math.toRadians(degrees)
        val sin = kotlin.math.sin(radians)
        val cos = kotlin.math.cos(radians)
        return Vec3(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos)
    }

    private fun magicProjectileLifetimeTicks(move: NpcBossMoveDefinition): Int =
        ((move.maxDistance + 2.0) / move.projectileSpeed).toInt().coerceIn(8, 80)

    private fun arrowVfxLifetimeTicks(move: NpcBossMoveDefinition): Int =
        ((move.maxDistance + 8.0) / move.projectileSpeed).toInt().coerceIn(10, 100)

    private fun tracksArrowVfx(move: NpcBossMoveDefinition): Boolean =
        move.projectileParticle.isNotBlank() || move.impactParticle.isNotBlank() || move.impactSoundId.isNotBlank()

    private fun bossParticle(id: String, fallback: SimpleParticleType): SimpleParticleType {
        val raw = id.trim()
        if (raw.isBlank()) return fallback
        val normalized = if (':' in raw) raw else "minecraft:$raw"
        val location = runCatching { ResourceLocation.parse(normalized) }.getOrNull() ?: return fallback
        return BuiltInRegistries.PARTICLE_TYPE.getOptional(location).orElse(fallback) as? SimpleParticleType ?: fallback
    }

    private fun playBossSound(level: ServerLevel, soundId: String, fallback: SoundEvent, x: Double, y: Double, z: Double, volume: Float, pitch: Float) {
        level.playSound(null, x, y, z, bossSound(soundId, fallback), SoundSource.HOSTILE, volume, pitch)
    }

    private fun bossSound(id: String, fallback: SoundEvent): SoundEvent {
        val raw = id.trim()
        if (raw.isBlank()) return fallback
        val normalized = if (':' in raw) raw else "minecraft:$raw"
        val location = runCatching { ResourceLocation.parse(normalized) }.getOrNull() ?: return fallback
        return BuiltInRegistries.SOUND_EVENT.getOptional(location).orElse(fallback)
    }

    private fun applyMagicStatus(target: ServerPlayer, move: NpcBossMoveDefinition) {
        if (move.statusEffectTicks <= 0) return
        val effect = when (move.statusEffectId) {
            "minecraft:slowness", "slowness" -> MobEffects.MOVEMENT_SLOWDOWN
            else -> null
        } ?: return
        target.addEffect(MobEffectInstance(effect, move.statusEffectTicks, move.statusEffectAmplifier, false, false, true))
    }

    private fun currentBossPhase(fight: ActiveBossFight): NpcBossPhaseDefinition =
        fight.moveset.phases.getOrNull(fight.bossPhaseIndex) ?: fight.moveset.phases.first()

    private fun bossPhaseIndexForHealth(fight: ActiveBossFight): Int {
        val healthRatio = if (fight.maxHealth > 0.0f) (fight.health / fight.maxHealth).coerceIn(0.0f, 1.0f) else 0.0f
        var selected = 0
        fight.moveset.phases.forEachIndexed { index, phase ->
            if (healthRatio <= phase.startsAtHealthRatio) selected = index
        }
        return selected.coerceIn(0, fight.moveset.phases.lastIndex.coerceAtLeast(0))
    }

    private fun maybeAdvanceBossPhase(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        val nextPhaseIndex = bossPhaseIndexForHealth(fight)
        if (nextPhaseIndex <= fight.bossPhaseIndex) return false
        fight.bossPhaseIndex = nextPhaseIndex
        val phase = currentBossPhase(fight)
        updateBossBar(entity, target, fight, forceMusic = true)
        startPhaseTransitionDialogue(entity, target, fight, phase)
        return true
    }

    private fun openPhaseTransitionDialog(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, phase: NpcBossPhaseDefinition) {
        val fallback = phase.transitionFallback.ifBlank { "${fight.displayName} changes stance." }
        val definition = if (fight.debug) null else NpcConfig.get(fight.npcId)
        if (definition == null) {
            runCatching { NpcStore.recordConversation(fight.npcId, target, fight.displayName, fallback, "npc_boss_phase_transition") }
            NpcNetwork.openDialog(
                target,
                NpcDialogPayload(
                    npcId = fight.npcId,
                    name = fight.displayName,
                    title = "Boss",
                    message = fallback,
                    contractGranted = false,
                    closeOnly = true,
                    closeLabel = "FIGHT",
                    friendshipLevel = 0,
                    npcEntityId = entity.id,
                    animalesePitch = "low",
                    animalesePitchMultiplier = 0.95f,
                    animaleseVolume = 0.5f,
                    animaleseRadius = 12.0f,
                    talkEnabled = false,
                    dialogMode = "boss",
                ),
            )
            return
        }
        val friendship = NpcStore.friendshipSnapshot(definition.id, target)
        val llmEnabled = NpcConfig.settings().llm.enabled && phase.transitionLlmPrompt.isNotBlank()
        val responseToken = if (llmEnabled) NpcDialogTokens.next() else 0L
        NpcNetwork.openDialog(
            target,
            NpcFeature.dialogPayload(
                definition,
                entity,
                if (llmEnabled) "..." else fallback,
                false,
                friendship.level,
                closeOnly = true,
                closeLabel = "FIGHT",
                responseToken = responseToken,
                dialogMode = "boss",
            ),
        )
        if (!llmEnabled) {
            NpcStore.recordConversation(definition.id, target, definition.name, fallback, "npc_boss_phase_transition")
            return
        }
        NpcLlmService.event(
            target,
            entity,
            definition,
            fallback,
            phase.transitionLlmPrompt,
            inputLabel = "Boss phase transition",
            sendTalkResponse = true,
            excludePlayerFromBalloon = true,
            showBalloon = false,
            relayToNearby = false,
            npcRecordType = "npc_boss_phase_transition",
            responseToken = responseToken,
        )
    }

    private fun phaseDamage(fight: ActiveBossFight, damage: Double): Float =
        (damage * currentBossPhase(fight).damageMultiplier).toFloat().coerceAtLeast(0.0f)

    private fun phaseSpeed(fight: ActiveBossFight, speed: Double): Double =
        (speed * currentBossPhase(fight).speedMultiplier).coerceIn(0.05, 2.0)

    private fun updateBossBar(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, forceMusic: Boolean = false) {
        fight.bossBar.setProgress((fight.health / fight.maxHealth).coerceIn(0.0f, 1.0f))
        NpcNetwork.syncBossBar(
            target,
            NpcBossBarPayload(
                npcId = fight.npcId,
                name = fight.displayName,
                mode = fight.modeLabel.ifBlank { if (fight.tacticPhase == BossTacticPhase.OFFENSE) "offense" else "guard" },
                phaseName = currentBossPhase(fight).displayName,
                phaseIndex = fight.bossPhaseIndex,
                phaseCount = fight.moveset.phases.size.coerceAtLeast(1),
                health = fight.health,
                maxHealth = fight.maxHealth,
                musicId = currentBossPhase(fight).musicId,
                musicVolume = currentBossPhase(fight).musicVolume.toFloat(),
                musicPitch = currentBossPhase(fight).musicPitch.toFloat(),
                musicRepeatTicks = currentBossPhase(fight).musicRepeatTicks,
                forceMusic = forceMusic,
            ),
        )
    }

    private fun clearBossBar(entity: ChowNpcEntity, fight: ActiveBossFight) {
        val player = (entity.level() as? ServerLevel)?.server?.playerList?.getPlayer(fight.targetId) ?: return
        NpcNetwork.clearBossBar(player, fight.npcId)
    }

    private fun bossEntity(server: MinecraftServer, entityId: UUID): ChowNpcEntity? = server.allLevels.asSequence()
        .mapNotNull { level -> level.getEntity(entityId) as? ChowNpcEntity }
        .firstOrNull()

    private fun wouldDefeatPlayer(player: ServerPlayer, damage: Float): Boolean {
        val health = finitePositive(player.health, 1.0f)
        val absorption = finiteNonNegative(player.absorptionAmount, 0.0f)
        return finitePositive(damage, 0.0f) >= health + absorption
    }

    private fun healDuelist(player: ServerPlayer) {
        val maxHealth = finitePositive(player.maxHealth, 20.0f).coerceAtLeast(1.0f)
        player.health = maxHealth
        player.remainingFireTicks = 0
        player.setTicksFrozen(0)
        player.fallDistance = 0.0f
        player.invulnerableTime = player.invulnerableTime.coerceAtLeast(20)
    }

    private fun finitePositive(value: Float, fallback: Float): Float =
        if (java.lang.Float.isFinite(value) && value > 0.0f) value else fallback

    private fun finiteNonNegative(value: Float, fallback: Float): Float =
        if (java.lang.Float.isFinite(value) && value >= 0.0f) value else fallback

    private fun guardBaitDelay(entity: ChowNpcEntity, fight: ActiveBossFight): Int =
        fight.moveset.guardMinTicks + entity.random.nextInt(fight.moveset.guardRandomTicks + 1)

    private fun offenseChainCount(entity: ChowNpcEntity, fight: ActiveBossFight): Int =
        currentBossPhase(fight).offenseChainMin + entity.random.nextInt(currentBossPhase(fight).offenseChainRandom + 1)

    private fun finishRecovery(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        if (fight.forceOffenseAfterRecovery) {
            fight.forceOffenseAfterRecovery = false
            startOffensePhase(entity, target, fight)
        } else if (fight.recoveryReturnToOffense && fight.offenseAttacksRemaining > 0) {
            fight.recoveryReturnToOffense = false
            startChase(entity, target, fight)
        } else {
            startDefensePhase(entity, target, fight)
        }
    }

    private fun startOffensePhase(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        fight.tacticPhase = BossTacticPhase.OFFENSE
        fight.offenseAttacksRemaining = offenseChainCount(entity, fight)
        fight.recoveryReturnToOffense = false
        fight.forceOffenseAfterRecovery = false
        startChase(entity, target, fight)
    }

    private fun startDefensePhase(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        fight.tacticPhase = BossTacticPhase.DEFENSE
        fight.offenseAttacksRemaining = 0
        fight.recoveryReturnToOffense = false
        fight.forceOffenseAfterRecovery = false
        startGuardMode(entity, target, fight)
    }

    private fun bossArmory(boss: NpcBossDefinition): NpcBossArmory = NpcBossArmory(
        mainHand = bossItemStack(boss.mainHand, ItemStack(Items.IRON_SWORD)),
        offHand = bossItemStack(boss.offHand, ItemStack.EMPTY),
    )

    private fun debugArmory(moveset: NpcBossMovesetDefinition, definition: NpcDefinition?): NpcBossArmory {
        val boss = definition?.boss?.normalized()
        if (boss != null && (boss.mainHand.isNotBlank() || boss.offHand.isNotBlank())) return bossArmory(boss)
        return when (moveset.id) {
            "archer" -> NpcBossArmory(
                bossItemStack("archers:composite_longbow", ItemStack(Items.BOW)),
                ItemStack.EMPTY,
            )
            "bard" -> NpcBossArmory(
                bossItemStack("bards_rpg:aether_harp_crossbow", ItemStack(Items.CROSSBOW)),
                ItemStack.EMPTY,
            )
            "wizard" -> NpcBossArmory(
                bossItemStack("wizards:staff_wizard", ItemStack(Items.BLAZE_ROD)),
                ItemStack.EMPTY,
            )
            "priest" -> NpcBossArmory(
                bossItemStack("paladins:holy_staff", ItemStack(Items.BLAZE_ROD)),
                ItemStack.EMPTY,
            )
            "rogue" -> NpcBossArmory(
                bossItemStack("simplyswords:iron_rapier", ItemStack(Items.IRON_SWORD)),
                bossItemStack("simplyswords:iron_rapier", ItemStack.EMPTY),
            )
            else -> NpcBossArmory(ItemStack(Items.IRON_SWORD), ItemStack.EMPTY)
        }
    }

    private fun bossItemStack(itemId: String, fallback: ItemStack): ItemStack {
        val raw = itemId.trim()
        if (raw.isBlank()) return fallback.copy()
        val normalized = if (':' in raw) raw else "minecraft:$raw"
        val id = runCatching { ResourceLocation.parse(normalized) }.getOrNull() ?: return fallback.copy()
        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR)
        return if (item == Items.AIR) fallback.copy() else ItemStack(item)
    }

    private data class NpcBossArmory(val mainHand: ItemStack, val offHand: ItemStack)

    private data class MagicBossProjectile(
        val move: NpcBossMoveDefinition,
        var position: Vec3,
        val velocity: Vec3,
        var ticksRemaining: Int,
    )

    private data class TrackedBossArrow(
        val arrowId: UUID,
        val move: NpcBossMoveDefinition,
        var position: Vec3,
        var ticksRemaining: Int,
    )

    private class ActiveBossFight(
        val npcId: String,
        val displayName: String,
        val moveset: NpcBossMovesetDefinition,
        val targetId: UUID,
        val startPos: Vec3,
        val maxHealth: Float,
        var health: Float,
        val damage: Float,
        val balloons: NpcBossBalloonDefinition,
        val animationSnapshot: NpcAnimationSnapshot,
        val armory: NpcBossArmory,
        val originalHealth: Float,
        val bossBar: ServerBossEvent,
        val startedTick: Int = 0,
        var nextActionTick: Int,
        var phase: BossFightPhase = BossFightPhase.CHASE,
        var phaseStartedTick: Int = 0,
        var tetherExceededSince: Int = 0,
        var recoveryHitsTaken: Int = 0,
        var hurtUntilTick: Int = 0,
        var guardAfterHurtTick: Int = 0,
        var tacticPhase: BossTacticPhase = BossTacticPhase.OFFENSE,
        var bossPhaseIndex: Int = 0,
        var offenseAttacksRemaining: Int = 0,
        var recoveryReturnToOffense: Boolean = false,
        var forceOffenseAfterRecovery: Boolean = false,
        var phaseDialogueUntilTick: Int = 0,
        var strafeSide: Int = 1,
        var nextStrafeFlipTick: Int = 0,
        var nextTauntTick: Int = 0,
        var activeTemplateId: String = "",
        var modeLabel: String = "",
        var lastModeBalloon: String = "",
        var shownBossBalloons: Int = 0,
        var activeMove: NpcBossMoveDefinition? = null,
        var lastMoveId: String = "",
        var npcIframeUntilTick: Int = 0,
        val firedHitTicks: MutableSet<Int> = mutableSetOf(),
        val moveCooldowns: MutableMap<String, Int> = linkedMapOf(),
        val magicProjectiles: MutableList<MagicBossProjectile> = mutableListOf(),
        val trackedArrows: MutableList<TrackedBossArrow> = mutableListOf(),
        val supportUses: MutableMap<String, Int> = linkedMapOf(),
        var absorptionHealth: Float = 0.0f,
        var absorptionUntilTick: Int = 0,
        val lastBalloonByKey: MutableMap<String, String> = linkedMapOf(),
        val debug: Boolean = false,
    )

    private data class BossResultProtection(val untilTick: Int, val healthFloor: Float)

    private enum class BossFightPhase {
        CHASE,
        ATTACK,
        RECOVERY,
        PHASE_DIALOGUE,
        GUARD_MODE,
        GUARD_REACT,
        GUARD_ROLL,
        GUARD_DODGE,
        PARRY,
    }

    private enum class BossTacticPhase {
        OFFENSE,
        DEFENSE,
    }

    private const val MAX_FIGHT_TICKS = 20 * 60 * 5
    private const val TETHER_DISTANCE_SQR = 40.0 * 40.0
    private const val TETHER_GRACE_TICKS = 60
    private const val RECOVERY_TICKS = 14
    private const val GUARD_REACT_TICKS = 6
    private const val GUARD_RESPONSE_CHOICES = 3
    private const val GUARD_BAIT_MIN_TICKS = 60
    private const val GUARD_BAIT_RANDOM_TICKS = 60
    private const val STATUS_UPDATE_TICKS = 8
    private const val PHASE_DIALOGUE_TIMEOUT_TICKS = 20 * 8
    private const val PHASE_DIALOGUE_KEEPALIVE_TICKS = 20 * 5
    private const val PLAYERLIKE_READY_TICKS = 16
    private const val PLAYERLIKE_GUARD_TICKS = 20
    private const val PLAYERLIKE_HURT_TICKS = 8
    private const val PLAYERLIKE_STRAFE_ANIMATION_SPEED = 0.55f
    private const val BOSS_BALLOON_TICKS = 55
    private const val BOSS_BALLOON_MAX_CHARS = 120
    private const val BOSS_BALLOON_CHANCE = 0.3f
    private const val RESULT_DIALOG_PROTECTION_TICKS = 20 * 120
    private const val ATTACK_START_DISTANCE_SQR = 3.0 * 3.0
    private const val HIT_DISTANCE_SQR = 2.5 * 2.5
    private const val STRAFE_TOO_CLOSE_DISTANCE_SQR = 2.0 * 2.0
    private const val STRAFE_TOO_FAR_DISTANCE_SQR = 3.6 * 3.6
    private const val STRAFE_RETURN_CHASE_DISTANCE_SQR = 6.0 * 6.0
    private const val HIT_CONE_DOT = 0.34
    private const val BOSS_CHASE_SPEED = 1.65
    private const val BOSS_STRAFE_SPEED = 0.9
    private const val BOSS_RECOVERY_STRAFE_SPEED = 0.55
    private const val BOSS_KNOCKBACK = 0.35
    private const val PARRY_KNOCKBACK = 0.6
    private const val HURT_KNOCKBACK = 0.35
    private const val STRAFE_RADIUS = 2.7
    private const val STRAFE_INNER_RADIUS = 2.4
    private const val STRAFE_OUTER_RADIUS = 3.1
    private const val STRAFE_STEP = 1.7
    private const val STRAFE_MIN_FLIP_TICKS = 18
    private const val STRAFE_RANDOM_FLIP_TICKS = 18
    private const val RECOVERY_STRAFE_RADIUS = 2.9
    private const val RECOVERY_STRAFE_INNER_RADIUS = 2.6
    private const val RECOVERY_STRAFE_OUTER_RADIUS = 3.3
    private const val RECOVERY_STRAFE_STEP = 1.1
    private const val RANGED_SPACING_BUFFER = 1.0
    private const val RANGED_STRAFE_STEP = 1.5
    private const val RANGED_INNER_RADIUS_FACTOR = 0.55
    private const val RANGED_MIN_INNER_RADIUS = 4.0
    private const val RANGED_MIN_OUTER_RADIUS = 5.5
    private const val RANGED_MAX_OUTER_RADIUS = 12.0
    private const val RANGED_MIN_WIDTH = 1.5
    private const val ARROW_VFX_MIN_IMPACT_TICKS = 3
    private const val ARROW_VFX_STOPPED_SPEED_SQR = 0.0004
    private const val MAX_RECOVERY_HITS = 1

    private fun guardTauntDelay(entity: ChowNpcEntity, fight: ActiveBossFight): Int =
        fight.moveset.guardTauntMinTicks + entity.random.nextInt(fight.moveset.guardTauntRandomTicks + 1)

    private const val GUARD_TAUNT_MIN_TICKS = 40
    private const val GUARD_TAUNT_RANDOM_TICKS = 40
}

data class NpcBossStartResult(val success: Boolean, val message: String)
