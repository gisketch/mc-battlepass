package dev.gisketch.chowkingdom.discord

import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

object DiscordFeature {
    private var lastTickNanos = 0L
    private var smoothedTps = 20.0
    private var nextStatusMillis = 0L

    fun register() {
        DiscordConfig.load()
        DiscordCommands.register()
        DiscordQuickSkinAvatarServer.start(DiscordConfig.current())
        NeoForge.EVENT_BUS.addListener(::onServerChat)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onLivingDeath)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
    }

    private fun onServerChat(event: ServerChatEvent) {
        DiscordRelay.chat(event.player, event.rawText)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        DiscordRelay.join(player)
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val onlineAfterLogout = (player.server.playerList.playerCount - 1).coerceAtLeast(0)
        DiscordRelay.leave(player, onlineAfterLogout)
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
        DiscordRelay.death(player, event.source.getLocalizedDeathMessage(player).string)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        updateTps()
        val config = DiscordConfig.current()
        if (!config.enabled || !config.relayStatus) return

        val nowMillis = System.currentTimeMillis()
        if (nextStatusMillis == 0L) nextStatusMillis = nowMillis + config.statusIntervalSeconds * 1000L
        if (nowMillis < nextStatusMillis) return

        nextStatusMillis = nowMillis + config.statusIntervalSeconds * 1000L
        DiscordRelay.status(event.server, smoothedTps)
    }

    private fun updateTps() {
        val now = System.nanoTime()
        if (lastTickNanos != 0L) {
            val seconds = (now - lastTickNanos) / 1_000_000_000.0
            if (seconds > 0.0) {
                val instantTps = (1.0 / seconds).coerceIn(0.0, 20.0)
                smoothedTps = (smoothedTps * 0.95) + (instantTps * 0.05)
            }
        }
        lastTickNanos = now
    }

}