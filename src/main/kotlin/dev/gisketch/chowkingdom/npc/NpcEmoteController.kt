package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerLevel
import java.util.UUID

object NpcEmoteController {
    private val active: MutableMap<UUID, ActiveNpcEmote> = linkedMapOf()
    private val cooldownUntil: MutableMap<String, Long> = linkedMapOf()

    fun tryPlayOrDefaultSpeaking(npc: ChowNpcEntity, surface: String, requestedId: String, source: String, force: Boolean = false, loopUntilTick: Long = 0L): NpcEmotePlayResult? {
        if (NpcEmoteCatalog.normalizeId(requestedId) != NpcEmoteCatalog.NONE) return tryPlay(npc, surface, requestedId, source, force, loopUntilTick)
        return tryPlayDefaultSpeaking(npc, surface, source, force, loopUntilTick)
    }

    fun tryPlayDefaultSpeaking(npc: ChowNpcEntity, surface: String, source: String, force: Boolean = false, loopUntilTick: Long = 0L): NpcEmotePlayResult? {
        val id = defaultSpeakingEmote(surface) ?: return null
        return tryPlay(npc, surface, id, "${source}_default_speaking", force, loopUntilTick)
    }

    fun tryPlay(npc: ChowNpcEntity, surface: String, requestedId: String, source: String, force: Boolean = false, loopUntilTick: Long = 0L): NpcEmotePlayResult {
        val id = NpcEmoteCatalog.sanitizeEmote(requestedId, surface)
        if (id == NpcEmoteCatalog.NONE) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, NpcEmoteCatalog.NONE, "none"))
        val emote = NpcEmoteCatalog.resolve(id, surface) ?: return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "not_allowed"))
        val level = npc.level() as? ServerLevel ?: return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "not_server"))
        if (!npc.isAlive) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "dead"))
        if (npc.isSleeping) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "sleeping"))
        if (NpcBossFights.isActive(npc)) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "boss_active"))
        if (NpcPokemonBattleService.isBattleLocked(npc)) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "pokemon_battle"))
        if (emote.posture && npc.isTalking()) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "talking"))
        if (emote.movementLock && !npc.navigation.isDone && !force) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "moving"))
        val now = level.gameTime
        val cooldownKey = cooldownKey(npc.uuid, emote.id)
        val cooldown = cooldownUntil[cooldownKey] ?: 0L
        if (!force && now < cooldown) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "cooldown"))
        active[npc.uuid]?.takeIf { current -> current.isActive(now, npc) }?.let { current ->
            if (!force && NpcEmoteSurfaces.normalize(surface) in ambientSurfaces) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "active_${current.id}"))
            cancel(npc, "replace")
        }
        val animation = NpcPlayerlikeAnimationRegistry.resolve(emote.animationId) ?: return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "missing_animation"))
        if (emote.movementLock) npc.navigation.stop()
        if (!npc.playPlayerlikeAnimation(animation.id)) return finish(npc, surface, requestedId, source, NpcEmotePlayResult(false, id, "play_failed"))
        val loopHoldUntil = if (emote.loopWhileTalking && loopUntilTick > now) loopUntilTick else 0L
        active[npc.uuid] = ActiveNpcEmote(
            id = emote.id,
            animationId = animation.id,
            surface = NpcEmoteSurfaces.normalize(surface),
            posture = emote.posture,
            movementLock = emote.movementLock,
            durationTicks = emote.durationTicks,
            loopWhileTalking = emote.loopWhileTalking && npc.isTalking(),
            loopUntilTick = loopHoldUntil,
            untilTick = now + emote.durationTicks,
        )
        if (emote.cooldownTicks > 0) cooldownUntil[cooldownKey] = now + emote.cooldownTicks
        ChowKingdomMod.LOGGER.debug("NPC emote played npc={} emote={} animation={} surface={} source={}", npc.npcId, emote.id, animation.id, surface, source)
        return finish(npc, surface, requestedId, source, NpcEmotePlayResult(true, emote.id, "played"))
    }

    fun tick(npc: ChowNpcEntity) {
        val current = active[npc.uuid] ?: return
        val level = npc.level() as? ServerLevel ?: return cancel(npc, "not_server").let {}
        if (!npc.isAlive || npc.isSleeping || NpcBossFights.isActive(npc) || NpcPokemonBattleService.isBattleLocked(npc)) {
            cancel(npc, "blocked")
            return
        }
        if (current.posture && (npc.isTalking() || !npc.navigation.isDone)) {
            cancel(npc, "posture_interrupted")
            return
        }
        val now = level.gameTime
        if (current.loopWhileTalking && !npc.isTalking() && now >= current.loopUntilTick) {
            cancel(npc, "talk_finished")
            return
        }
        if (!current.loopWhileTalking && current.loopUntilTick > 0L && now >= current.loopUntilTick) {
            cancel(npc, "loop_finished")
            return
        }
        if (current.shouldLoop(now, npc)) {
            if (now >= current.untilTick) {
                if (!npc.playPlayerlikeAnimation(current.animationId)) {
                    cancel(npc, "loop_failed")
                    return
                }
                active[npc.uuid] = current.copy(untilTick = now + current.durationTicks)
                ChowKingdomMod.LOGGER.debug("NPC emote looped npc={} emote={} animation={}", npc.npcId, current.id, current.animationId)
            }
            if (current.movementLock) npc.navigation.stop()
            return
        }
        if (now >= current.untilTick) {
            cancel(npc, "finished")
            return
        }
        if (current.movementLock) npc.navigation.stop()
    }

    fun cancel(npc: ChowNpcEntity, reason: String): Boolean {
        val current = active.remove(npc.uuid) ?: return false
        if (npc.playerlikeAnimationKey == current.animationId) npc.clearPlayerlikeAnimation()
        ChowKingdomMod.LOGGER.debug("NPC emote cleared npc={} emote={} reason={}", npc.npcId, current.id, reason)
        return true
    }

    fun cancelPosture(npc: ChowNpcEntity, reason: String): Boolean {
        val current = active[npc.uuid] ?: return false
        if (!current.posture) return false
        return cancel(npc, reason)
    }

    fun isActive(npc: ChowNpcEntity): Boolean = active.containsKey(npc.uuid)

    private fun cooldownKey(uuid: UUID, id: String): String = "$uuid:$id"

    private val ambientSurfaces = setOf(NpcEmoteSurfaces.AMBIENT, NpcEmoteSurfaces.AMBIENT_POSTURE, NpcEmoteSurfaces.POKEMON)

    private fun defaultSpeakingEmote(surface: String): String? {
        val id = NpcConfig.settings().defaultSpeakingEmote ?: return null
        return NpcEmoteCatalog.resolve(id, surface)?.id
    }

    private fun finish(npc: ChowNpcEntity, surface: String, requestedId: String, source: String, result: NpcEmotePlayResult): NpcEmotePlayResult {
        NpcFeature.debugNpcEmoteAttempt(npc, surface, requestedId, source, result)
        return result
    }
}

data class NpcEmotePlayResult(val played: Boolean, val id: String, val reason: String)

private data class ActiveNpcEmote(
    val id: String,
    val animationId: String,
    val surface: String,
    val posture: Boolean,
    val movementLock: Boolean,
    val durationTicks: Int,
    val loopWhileTalking: Boolean,
    val loopUntilTick: Long,
    val untilTick: Long,
) {
    fun isActive(now: Long, npc: ChowNpcEntity): Boolean = now < untilTick || shouldLoop(now, npc)

    fun shouldLoop(now: Long, npc: ChowNpcEntity): Boolean = (loopWhileTalking && npc.isTalking()) || now < loopUntilTick
}
