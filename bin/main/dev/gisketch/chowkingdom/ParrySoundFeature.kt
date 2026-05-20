package dev.gisketch.chowkingdom

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent
import java.util.UUID

object ParrySoundFeature {
    private val lastPlayedTicks: MutableMap<UUID, Long> = mutableMapOf()

    fun register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ::onShieldBlock)
    }

    fun play(entity: LivingEntity, source: SoundSource = soundSource(entity), volume: Float = 1.0f, pitch: Float = 1.0f) {
        val level = entity.level() as? ServerLevel ?: return
        val tick = level.gameTime
        if (lastPlayedTicks[entity.uuid] == tick) return
        lastPlayedTicks[entity.uuid] = tick
        lastPlayedTicks.entries.removeIf { (_, playedTick) -> tick - playedTick > SOUND_DEDUPE_RETENTION_TICKS }
        level.playSound(null, entity.x, entity.y, entity.z, ChowSounds.PARRY.get(), source, volume, pitch)
    }

    private fun onShieldBlock(event: LivingShieldBlockEvent) {
        val entity = event.entity
        if (entity.level().isClientSide || !event.blocked || event.blockedDamage <= 0.0f) return
        play(entity)
    }

    private fun soundSource(entity: LivingEntity): SoundSource = if (entity is ServerPlayer) SoundSource.PLAYERS else SoundSource.HOSTILE

    private const val SOUND_DEDUPE_RETENTION_TICKS = 40L
}
