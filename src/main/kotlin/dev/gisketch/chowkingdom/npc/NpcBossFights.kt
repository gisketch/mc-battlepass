package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.roles.ClassMentorQuestService
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID

object NpcBossFights {
    private val active: MutableMap<UUID, ActiveBossFight> = linkedMapOf()
    private val resultProtection: MutableMap<UUID, BossResultProtection> = linkedMapOf()

    fun start(player: ServerPlayer, entity: ChowNpcEntity, definition: NpcDefinition): NpcBossStartResult {
        val boss = definition.boss.normalized()
        if (!boss.enabled) return NpcBossStartResult(false, "${definition.displayName()} has boss fights disabled.")
        if (boss.template != NpcBossDefinition.DEFAULT_BOSS_TEMPLATE) return NpcBossStartResult(false, "Unknown NPC boss template '${boss.template}'.")
        if (active.containsKey(entity.uuid)) return NpcBossStartResult(false, "${definition.displayName()} is already in a boss fight.")
        if (active.values.any { fight -> fight.targetId == player.uuid }) return NpcBossStartResult(false, "You are already in an NPC boss fight.")
        if (entity.isSleeping) entity.stopSleeping()
        entity.navigation.stop()
        val health = boss.health.toFloat().coerceAtLeast(1.0f)
        val bossBar = ServerBossEvent(
            Component.literal("${definition.displayName()} | NPC mode: chase"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS,
        )
        bossBar.addPlayer(player)
        bossBar.setProgress(1.0f)
        val fight = ActiveBossFight(
            npcId = definition.id,
            displayName = definition.displayName(),
            targetId = player.uuid,
            startPos = entity.position(),
            maxHealth = health,
            health = health,
            damage = boss.damage.toFloat().coerceAtLeast(0.0f),
            balloons = boss.balloons,
            animationSnapshot = NpcCustomAnimationController.snapshot(entity),
            originalHealth = entity.health,
            bossBar = bossBar,
            startedTick = entity.tickCount,
            nextActionTick = 0,
            phaseStartedTick = entity.tickCount,
            strafeSide = if (entity.random.nextBoolean()) 1 else -1,
        )
        active[entity.uuid] = fight
        resultProtection.remove(entity.uuid)
        entity.updatePassThroughInteractions(true)
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "BOSS FIGHT STARTED", definition.displayName(), SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        return NpcBossStartResult(true, "Started boss fight with ${definition.displayName()}.")
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
        val boss = (sourceEntity as? ChowNpcEntity) ?: (directEntity as? ChowNpcEntity) ?: return false
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
            val attacker = (sourceEntity as? ServerPlayer) ?: (directEntity as? ServerPlayer)
            return attacker?.uuid != fight.targetId
        }

        val targetPlayer = target as? ServerPlayer
        if (targetPlayer != null) {
            val fight = active.entries.firstOrNull { entry -> entry.value.targetId == targetPlayer.uuid }
            if (fight != null) {
                if (sourceEntity == null && directEntity == null) return false
                return sourceEntity?.uuid != fight.key && directEntity?.uuid != fight.key
            }
        }

        val sourcePlayer = (sourceEntity as? ServerPlayer) ?: (directEntity as? ServerPlayer)
        if (sourcePlayer != null) {
            val fight = active.entries.firstOrNull { entry -> entry.value.targetId == sourcePlayer.uuid } ?: return false
            return target.uuid != fight.key
        }

        val sourceBoss = (sourceEntity as? ChowNpcEntity) ?: (directEntity as? ChowNpcEntity) ?: return false
        val fight = active[sourceBoss.uuid] ?: return false
        return target.uuid != fight.targetId
    }

    fun handleNpcAttackAttempt(entity: ChowNpcEntity, attacker: ServerPlayer?): Boolean {
        if (isResultProtected(entity)) return true
        val fight = active[entity.uuid] ?: return false
        val target = attacker?.takeIf { player -> player.uuid == fight.targetId } ?: return true
        return when (fight.phase) {
            BossFightPhase.RECOVERY -> {
                if (fight.recoveryHitsTaken >= MAX_RECOVERY_HITS || fight.guardAfterHurtTick > 0) {
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

        when (fight.phase) {
            BossFightPhase.RECOVERY -> {
                if (fight.recoveryHitsTaken >= MAX_RECOVERY_HITS || fight.guardAfterHurtTick > 0) startGuardReact(entity, target, fight) else acceptRecoveryHit(entity, target, fight, damage)
            }
            BossFightPhase.GUARD_MODE -> startGuardReact(entity, target, fight)
            BossFightPhase.GUARD_REACT,
            BossFightPhase.PARRY -> blockBossHit(entity)
            BossFightPhase.CHASE,
            BossFightPhase.ATTACK -> Unit
        }
        return true
    }

    fun cancelForPlayer(player: ServerPlayer, reason: String = "Boss fight reset.") {
        active.values.filter { fight -> fight.targetId == player.uuid }.forEach { fight ->
            val entity = NpcFeature.existingNpc(player.server, fight.npcId)
            if (entity != null) cancel(entity, fight, reason) else {
                active.entries.removeIf { entry -> entry.value === fight }
                fight.bossBar.removeAllPlayers()
            }
        }
    }

    fun clear(entity: ChowNpcEntity) {
        val fight = active.remove(entity.uuid) ?: return
        resultProtection.remove(entity.uuid)
        fight.bossBar.removeAllPlayers()
        NpcCustomAnimationController.restore(entity, fight.animationSnapshot)
        entity.updatePassThroughInteractions(false)
    }

    private fun tickActive(entity: ChowNpcEntity, fight: ActiveBossFight): Boolean {
        if (!entity.isAlive || entity.tickCount > fight.startedTick + MAX_FIGHT_TICKS) return false
        val level = entity.level() as? ServerLevel ?: return false
        val target = level.getPlayerByUUID(fight.targetId) as? ServerPlayer ?: return false
        if (!target.isAlive) return false
        if (!withinTether(entity, target, fight)) return false
        updateBossBar(fight)
        return when (fight.phase) {
            BossFightPhase.CHASE -> tickChase(entity, target, fight)
            BossFightPhase.ATTACK -> tickAttack(entity, target, fight)
            BossFightPhase.RECOVERY -> tickRecovery(entity, target, fight)
            BossFightPhase.GUARD_MODE -> tickGuardMode(entity, target, fight)
            BossFightPhase.GUARD_REACT -> tickGuardReact(entity, target, fight)
            BossFightPhase.PARRY -> tickParry(entity, target, fight)
        }
    }

    private fun tickChase(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "chase"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "chase")
        faceTarget(entity, target)
        playTemplate(entity, fight, NpcAnimationTemplates.BOSS_CHASE_SWORD)
        if (entity.distanceToSqr(target) <= ATTACK_START_DISTANCE_SQR && targetInForwardCone(entity, target)) {
            startAttack(entity, target, fight)
            return true
        }
        entity.navigation.moveTo(target.x, target.y, target.z, BOSS_CHASE_SPEED)
        if (entity.navigation.isDone) entity.moveControl.setWantedPosition(target.x, target.y, target.z, BOSS_CHASE_SPEED)
        return true
    }

    private fun startAttack(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.ATTACK)
        fight.firedEvents.clear()
        playTemplate(entity, fight, NpcAnimationTemplates.ATTACK_SWORD, forceRestart = true)
        entity.navigation.stop()
        showModeBalloon(entity, target, fight, "attacking")
    }

    private fun tickAttack(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "attack"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "attacking")
        entity.navigation.stop()
        val elapsed = entity.tickCount - fight.phaseStartedTick
        NpcAnimationTemplates.ATTACK_SWORD.events.forEach { event ->
            if (elapsed >= event.tick && fight.firedEvents.add(event)) {
                when (event.type) {
                    NpcAnimationEventType.ATTACK_HIT -> attackHit(entity, target, fight)
                }
            }
        }
        if (elapsed < NpcAnimationTemplates.ATTACK_SWORD.durationTicks) return true
        startRecovery(entity, target, fight)
        return true
    }

    private fun startRecovery(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.RECOVERY)
        fight.nextActionTick = entity.tickCount + RECOVERY_TICKS
        fight.recoveryHitsTaken = 0
        fight.hurtUntilTick = 0
        fight.guardAfterHurtTick = 0
        prepareStrafe(entity, fight)
        playTemplate(entity, fight, NpcAnimationTemplates.BOSS_STRAFE_SWORD, forceRestart = true)
        showModeBalloon(entity, target, fight, "recovery")
    }

    private fun tickRecovery(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "recovery"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "recovery")
        faceTarget(entity, target)
        if (fight.guardAfterHurtTick > 0 && entity.tickCount >= fight.guardAfterHurtTick) {
            startGuardMode(entity, target, fight)
            return true
        }
        if (entity.tickCount < fight.hurtUntilTick) {
            entity.navigation.stop()
            return true
        }
        playTemplate(entity, fight, NpcAnimationTemplates.BOSS_STRAFE_SWORD)
        if (entity.distanceToSqr(target) > STRAFE_RETURN_CHASE_DISTANCE_SQR || entity.tickCount >= fight.nextActionTick) {
            startGuardMode(entity, target, fight)
            return true
        }
        strafeAroundTarget(entity, target, fight)
        return true
    }

    private fun startGuardMode(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.GUARD_MODE)
        fight.nextActionTick = entity.tickCount + guardBaitDelay(entity)
        fight.nextTauntTick = entity.tickCount + guardTauntDelay(entity)
        fight.recoveryHitsTaken = 0
        fight.hurtUntilTick = 0
        fight.guardAfterHurtTick = 0
        prepareStrafe(entity, fight)
        playTemplate(entity, fight, NpcAnimationTemplates.BOSS_STRAFE_SWORD, forceRestart = true)
        showModeBalloon(entity, target, fight, "guard")
    }

    private fun tickGuardMode(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "guard"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "guard")
        faceTarget(entity, target)
        playTemplate(entity, fight, NpcAnimationTemplates.BOSS_STRAFE_SWORD)
        if (entity.distanceToSqr(target) > STRAFE_RETURN_CHASE_DISTANCE_SQR || entity.tickCount >= fight.nextActionTick) {
            startChase(entity, target, fight)
            return true
        }
        if (entity.tickCount >= fight.nextTauntTick) {
            showBossBalloon(entity, target, fight, fight.balloons.taunt, "taunt")
            fight.nextTauntTick = entity.tickCount + guardTauntDelay(entity)
        }
        strafeAroundTarget(entity, target, fight)
        return true
    }

    private fun startGuardReact(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.GUARD_REACT)
        fight.nextActionTick = entity.tickCount + GUARD_REACT_TICKS
        playTemplate(entity, fight, NpcAnimationTemplates.GUARD_SWORD, forceRestart = true)
        entity.navigation.stop()
        blockBossHit(entity)
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
        playTemplate(entity, fight, NpcAnimationTemplates.PARRY_SWORD, forceRestart = true)
        entity.navigation.stop()
        blockBossHit(entity)
        target.knockback(PARRY_KNOCKBACK, entity.x - target.x, entity.z - target.z)
        target.hurtMarked = true
    }

    private fun tickParry(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight): Boolean {
        entity.debugActivity = "boss"
        entity.debugGoal = "parry"
        entity.debugTargetPos = target.blockPosition().immutable()
        updateStatus(entity, target, fight, "parry")
        faceTarget(entity, target)
        entity.navigation.stop()
        if (entity.tickCount - fight.phaseStartedTick < NpcAnimationTemplates.PARRY_SWORD.durationTicks) return true
        startChase(entity, target, fight)
        return true
    }

    private fun attackHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        if (entity.distanceToSqr(target) > HIT_DISTANCE_SQR || !targetInForwardCone(entity, target)) {
            entity.debugGoal = "attack_miss"
            return
        }
        val level = entity.level() as? ServerLevel ?: return
        entity.swing(InteractionHand.MAIN_HAND, true)
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0f, 0.85f + entity.random.nextFloat() * 0.2f)
        target.invulnerableTime = 0
        if (target.hurt(entity.damageSources().mobAttack(entity), fight.damage)) {
            target.knockback(BOSS_KNOCKBACK, entity.x - target.x, entity.z - target.z)
            target.hurtMarked = true
            showBossBalloon(entity, target, fight, fight.balloons.hitPlayer, "hit_player")
        }
    }

    private fun acceptRecoveryHit(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight, damage: Float) {
        fight.health = (fight.health - damage).coerceAtLeast(0.0f)
        fight.recoveryHitsTaken++
        fight.hurtUntilTick = entity.tickCount + NpcAnimationTemplates.HURT_SWORD.durationTicks
        fight.guardAfterHurtTick = if (fight.recoveryHitsTaken >= MAX_RECOVERY_HITS) fight.hurtUntilTick else 0
        updateBossBar(fight)
        playTemplate(entity, fight, NpcAnimationTemplates.HURT_SWORD, forceRestart = true)
        entity.navigation.stop()
        entity.knockback(HURT_KNOCKBACK, target.x - entity.x, target.z - entity.z)
        entity.hurtMarked = true
        showBossBalloon(entity, target, fight, fight.balloons.tookDamage, "took_damage")
        if (fight.health <= 0.0f) defeat(entity, target, fight)
    }

    private fun blockBossHit(entity: ChowNpcEntity) {
        val level = entity.level() as? ServerLevel ?: return
        level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.9f, 0.85f + entity.random.nextFloat() * 0.2f)
        level.sendParticles(ParticleTypes.END_ROD, entity.x, entity.y + 1.05, entity.z, 18, 0.45, 0.45, 0.45, 0.02)
        entity.hurtTime = 0
        entity.hurtMarked = false
    }

    private fun startChase(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        setPhase(entity, fight, BossFightPhase.CHASE)
        fight.nextActionTick = 0
        fight.recoveryHitsTaken = 0
        fight.hurtUntilTick = 0
        fight.guardAfterHurtTick = 0
        playTemplate(entity, fight, NpcAnimationTemplates.BOSS_CHASE_SWORD, forceRestart = true)
        showModeBalloon(entity, target, fight, "chase")
    }

    private fun prepareStrafe(entity: ChowNpcEntity, fight: ActiveBossFight) {
        fight.strafeSide = if (entity.random.nextBoolean()) 1 else -1
        fight.nextStrafeFlipTick = entity.tickCount + STRAFE_MIN_FLIP_TICKS + entity.random.nextInt(STRAFE_RANDOM_FLIP_TICKS + 1)
    }

    private fun strafeAroundTarget(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
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
        val radius = when {
            distanceSqr < STRAFE_TOO_CLOSE_DISTANCE_SQR -> STRAFE_OUTER_RADIUS
            distanceSqr > STRAFE_TOO_FAR_DISTANCE_SQR -> STRAFE_INNER_RADIUS
            else -> STRAFE_RADIUS
        }
        val desired = target.position().add(radial.scale(radius)).add(tangent.scale(STRAFE_STEP))
        entity.navigation.moveTo(desired.x, target.y, desired.z, BOSS_STRAFE_SPEED)
        if (entity.navigation.isDone) entity.moveControl.setWantedPosition(desired.x, target.y, desired.z, BOSS_STRAFE_SPEED)
    }

    private fun defeat(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        showBossBalloon(entity, target, fight, fight.balloons.defeat, "defeat")
        finish(entity, fight, protectResultDialog = true)
        val definition = NpcConfig.get(fight.npcId) ?: return
        if (ClassMentorQuestService.onMentorDuelWon(target, entity, definition)) return
        SnackbarNetwork.send(target, SnackbarNotification.npc(definition.id, "BOSS DEFEATED", definition.displayName(), SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        openDefeatDialog(target, entity, definition)
    }

    private fun bossVictory(entity: ChowNpcEntity, target: ServerPlayer, fight: ActiveBossFight) {
        showBossBalloon(entity, target, fight, fight.balloons.victory, "victory")
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
        entity.debugActivity = NpcFeature.smartBrainDefinition(entity)?.let { def -> NpcFeature.activityFor(entity, def) } ?: "idle"
        entity.debugGoal = "boss_reset"
    }

    private fun finish(entity: ChowNpcEntity, fight: ActiveBossFight, protectResultDialog: Boolean = false) {
        active.remove(entity.uuid)
        fight.bossBar.removeAllPlayers()
        entity.navigation.stop()
        NpcCustomAnimationController.restore(entity, fight.animationSnapshot)
        val restoredHealth = if (protectResultDialog) entity.maxHealth else fight.originalHealth.coerceIn(1.0f, entity.maxHealth)
        entity.setHealth(restoredHealth.coerceIn(1.0f, entity.maxHealth))
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

    private fun playTemplate(entity: ChowNpcEntity, fight: ActiveBossFight, template: NpcAnimationTemplate, forceRestart: Boolean = false) {
        if (!forceRestart && fight.activeTemplateId == template.id) return
        NpcCustomAnimationController.play(entity, template)
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
            "chase" -> fight.balloons.chase
            "attacking" -> fight.balloons.attack
            "recovery" -> fight.balloons.recovery
            "guard" -> fight.balloons.taunt
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
    }

    private fun targetInForwardCone(entity: ChowNpcEntity, target: ServerPlayer): Boolean {
        val toTarget = Vec3(target.x - entity.x, 0.0, target.z - entity.z)
        if (toTarget.lengthSqr() <= 0.0001) return true
        val look = entity.lookAngle
        val forward = Vec3(look.x, 0.0, look.z)
        if (forward.lengthSqr() <= 0.0001) return true
        return forward.normalize().dot(toTarget.normalize()) >= HIT_CONE_DOT
    }

    private fun updateBossBar(fight: ActiveBossFight) {
        fight.bossBar.setProgress((fight.health / fight.maxHealth).coerceIn(0.0f, 1.0f))
    }

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

    private fun guardBaitDelay(entity: ChowNpcEntity): Int = GUARD_BAIT_MIN_TICKS + entity.random.nextInt(GUARD_BAIT_RANDOM_TICKS + 1)

    private class ActiveBossFight(
        val npcId: String,
        val displayName: String,
        val targetId: UUID,
        val startPos: Vec3,
        val maxHealth: Float,
        var health: Float,
        val damage: Float,
        val balloons: NpcBossBalloonDefinition,
        val animationSnapshot: NpcAnimationSnapshot,
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
        var strafeSide: Int = 1,
        var nextStrafeFlipTick: Int = 0,
        var nextTauntTick: Int = 0,
        var activeTemplateId: String = "",
        var modeLabel: String = "",
        var lastModeBalloon: String = "",
        var shownBossBalloons: Int = 0,
        val firedEvents: MutableSet<NpcAnimationEvent> = mutableSetOf(),
        val lastBalloonByKey: MutableMap<String, String> = linkedMapOf(),
    )

    private data class BossResultProtection(val untilTick: Int, val healthFloor: Float)

    private enum class BossFightPhase {
        CHASE,
        ATTACK,
        RECOVERY,
        GUARD_MODE,
        GUARD_REACT,
        PARRY,
    }

    private const val MAX_FIGHT_TICKS = 20 * 60 * 5
    private const val TETHER_DISTANCE_SQR = 40.0 * 40.0
    private const val TETHER_GRACE_TICKS = 60
    private const val RECOVERY_TICKS = 14
    private const val GUARD_REACT_TICKS = 6
    private const val GUARD_BAIT_MIN_TICKS = 60
    private const val GUARD_BAIT_RANDOM_TICKS = 60
    private const val STATUS_UPDATE_TICKS = 8
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
    private const val BOSS_KNOCKBACK = 0.35
    private const val PARRY_KNOCKBACK = 0.6
    private const val HURT_KNOCKBACK = 0.35
    private const val STRAFE_RADIUS = 2.7
    private const val STRAFE_INNER_RADIUS = 2.4
    private const val STRAFE_OUTER_RADIUS = 3.1
    private const val STRAFE_STEP = 1.7
    private const val STRAFE_MIN_FLIP_TICKS = 18
    private const val STRAFE_RANDOM_FLIP_TICKS = 18
    private const val MAX_RECOVERY_HITS = 1

    private fun guardTauntDelay(entity: ChowNpcEntity): Int = GUARD_TAUNT_MIN_TICKS + entity.random.nextInt(GUARD_TAUNT_RANDOM_TICKS + 1)

    private const val GUARD_TAUNT_MIN_TICKS = 40
    private const val GUARD_TAUNT_RANDOM_TICKS = 40
}

data class NpcBossStartResult(val success: Boolean, val message: String)
