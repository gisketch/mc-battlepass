package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerLevel
import java.util.UUID

object NpcEmoteController {
    private val active: MutableMap<UUID, ActiveNpcEmote> = linkedMapOf()
    private val cooldownUntil: MutableMap<String, Long> = linkedMapOf()

    fun tryPlay(npc: ChowNpcEntity, surface: String, requestedId: String, source: String, force: Boolean = false): NpcEmotePlayResult {
        val id = NpcEmoteCatalog.sanitizeEmote(requestedId, surface)
        if (id == NpcEmoteCatalog.NONE) return NpcEmotePlayResult(false, NpcEmoteCatalog.NONE, "none")
        val emote = NpcEmoteCatalog.resolve(id, surface) ?: return NpcEmotePlayResult(false, id, "not_allowed")
        val level = npc.level() as? ServerLevel ?: return NpcEmotePlayResult(false, id, "not_server")
        if (!npc.isAlive) return NpcEmotePlayResult(false, id, "dead")
        if (npc.isSleeping) return NpcEmotePlayResult(false, id, "sleeping")
        if (NpcBossFights.isActive(npc)) return NpcEmotePlayResult(false, id, "boss_active")
        if (NpcPokemonBattleService.isBattleLocked(npc)) return NpcEmotePlayResult(false, id, "pokemon_battle")
        if (emote.posture && npc.isTalking()) return NpcEmotePlayResult(false, id, "talking")
        if (emote.movementLock && !npc.navigation.isDone && !force) return NpcEmotePlayResult(false, id, "moving")
        val now = level.gameTime
        val cooldownKey = cooldownKey(npc.uuid, emote.id)
        val cooldown = cooldownUntil[cooldownKey] ?: 0L
        if (!force && now < cooldown) return NpcEmotePlayResult(false, id, "cooldown")
        active[npc.uuid]?.takeIf { current -> now < current.untilTick }?.let { current ->
            if (!force && NpcEmoteSurfaces.normalize(surface) in ambientSurfaces) return NpcEmotePlayResult(false, id, "active_${current.id}")
            cancel(npc, "replace")
        }
        val animation = NpcPlayerlikeAnimationRegistry.resolve(emote.animationId) ?: return NpcEmotePlayResult(false, id, "missing_animation")
        if (emote.movementLock) npc.navigation.stop()
        if (!npc.playPlayerlikeAnimation(animation.id)) return NpcEmotePlayResult(false, id, "play_failed")
        active[npc.uuid] = ActiveNpcEmote(emote.id, animation.id, NpcEmoteSurfaces.normalize(surface), emote.posture, emote.movementLock, now + emote.durationTicks)
        if (emote.cooldownTicks > 0) cooldownUntil[cooldownKey] = now + emote.cooldownTicks
        ChowKingdomMod.LOGGER.debug("NPC emote played npc={} emote={} animation={} surface={} source={}", npc.npcId, emote.id, animation.id, surface, source)
        return NpcEmotePlayResult(true, emote.id, "played")
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
        if (level.gameTime >= current.untilTick) {
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
}

data class NpcEmotePlayResult(val played: Boolean, val id: String, val reason: String)

private data class ActiveNpcEmote(
    val id: String,
    val animationId: String,
    val surface: String,
    val posture: Boolean,
    val movementLock: Boolean,
    val untilTick: Long,
)
