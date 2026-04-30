package dev.gisketch.chowkingdom.discord

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import dev.gisketch.chowkingdom.profiles.NicknameStore
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
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
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerStopping)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        val config = DiscordConfig.current()
        DiscordQuickSkinAvatarServer.reload(config)
        DiscordInboundBridge.checkChannel(config.discordToMinecraft)
        DiscordInboundBridge.tick(event.server, smoothedTps)
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        DiscordInboundBridge.reset()
    }

    private fun onServerChat(event: ServerChatEvent) {
        DiscordRelay.chat(event.player, event.rawText)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        DiscordAccountLinkStore.refreshMinecraftName(player, NicknameStore.displayName(player))
        sendDiscordLinkReminder(player)
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
        DiscordInboundBridge.tick(event.server, smoothedTps)
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

    private fun sendDiscordLinkReminder(player: ServerPlayer) {
        if (DiscordAccountLinkStore.linkFor(player) != null) return
        player.sendSystemMessage(
            Component.literal("[Chow Kingdom] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Link your Discord to Minecraft.").withStyle(ChatFormatting.AQUA)),
        )
        player.sendSystemMessage(
            Component.literal("Run ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/ck discord link").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(", then send ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("!link CODE").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" in the Discord bridge channel.").withStyle(ChatFormatting.GRAY)),
        )
    }

}