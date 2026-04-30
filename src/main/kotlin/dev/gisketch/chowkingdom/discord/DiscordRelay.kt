package dev.gisketch.chowkingdom.discord

import dev.gisketch.chowkingdom.profiles.NicknameStore
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.Locale
import kotlin.math.roundToInt

object DiscordRelay {
    fun chat(player: ServerPlayer, rawMessage: String) {
        val config = DiscordConfig.current()
        if (!config.enabled || !config.relayChat) return

        val values = playerValues(player) + mapOf("message" to DiscordText.cleanContent(rawMessage))
        if (config.playerChatIdentity) {
            val displayName = NicknameStore.displayName(player)
            DiscordWebhookClient.send(
                DiscordWebhookMessage(
                    content = DiscordText.applyTemplate(config.formatting.chatMessage, values),
                    username = displayName,
                    avatarUrl = DiscordQuickSkinSupport.avatarUrl(player, config),
                ),
            )
        } else {
            DiscordWebhookClient.send(DiscordText.applyTemplate(config.formatting.chatFallbackMessage, values))
        }
    }

    fun join(player: ServerPlayer) {
        val config = DiscordConfig.current()
        if (!config.enabled || !config.relayJoinLeave) return
        DiscordWebhookClient.send(playerEmbedMessage(player, config.formatting.joinTitle, config.formatting.joinDescription, config.formatting.joinColor, playerValues(player) + serverValues(player.server)))
    }

    fun leave(player: ServerPlayer, onlineAfterLogout: Int) {
        val config = DiscordConfig.current()
        if (!config.enabled || !config.relayJoinLeave) return
        val values = playerValues(player) + mapOf("online" to onlineAfterLogout.toString(), "max" to player.server.maxPlayers.toString())
        DiscordWebhookClient.send(playerEmbedMessage(player, config.formatting.leaveTitle, config.formatting.leaveDescription, config.formatting.leaveColor, values))
    }

    fun death(player: ServerPlayer, deathMessage: String) {
        val config = DiscordConfig.current()
        if (!config.enabled || !config.relayDeaths) return
        val values = playerValues(player) + serverValues(player.server) + mapOf("death_message" to DiscordText.cleanContent(deathMessage))
        DiscordWebhookClient.send(playerEmbedMessage(player, config.formatting.deathTitle, config.formatting.deathDescription, config.formatting.deathColor, values))
    }

    fun status(server: MinecraftServer, tps: Double) {
        val config = DiscordConfig.current()
        if (!config.enabled || !config.relayStatus) return
        val values = serverValues(server) + mapOf("tps" to formatTps(tps))
        DiscordWebhookClient.send(DiscordText.applyTemplate(config.formatting.statusMessage, values))
    }

    fun battlepassMissionCompleted(player: ServerPlayer, battlepass: String, scope: String, mission: String, xp: Int) {
        val config = DiscordConfig.current()
        if (!config.enabled || !config.relayBattlepassCompletions) return
        val values = playerValues(player) + mapOf(
            "battlepass" to battlepass,
            "scope" to scope,
            "scope_lower" to scope.lowercase(Locale.ROOT),
            "mission" to DiscordText.cleanContent(mission),
            "xp" to xp.toString(),
        )
        DiscordWebhookClient.send(
            DiscordWebhookMessage(
                embeds = listOf(
                    DiscordEmbed(
                        title = DiscordText.applyTemplate(config.formatting.battlepassTitle, values),
                        description = DiscordText.applyTemplate(config.formatting.battlepassDescription, values),
                        color = DiscordText.parseColor(config.formatting.battlepassColor),
                        authorName = NicknameStore.displayName(player),
                        authorIconUrl = DiscordQuickSkinSupport.avatarUrl(player, config),
                    ),
                ),
            ),
        )
    }

    private fun embedMessage(title: String, description: String, color: String, values: Map<String, String>): DiscordWebhookMessage = DiscordWebhookMessage(
        embeds = listOf(
            DiscordEmbed(
                title = DiscordText.applyTemplate(title, values),
                description = DiscordText.applyTemplate(description, values),
                color = DiscordText.parseColor(color),
            ),
        ),
    )

    private fun playerEmbedMessage(player: ServerPlayer, title: String, description: String, color: String, values: Map<String, String>): DiscordWebhookMessage = DiscordWebhookMessage(
        embeds = listOf(
            DiscordEmbed(
                title = DiscordText.applyTemplate(title, values),
                description = DiscordText.applyTemplate(description, values),
                color = DiscordText.parseColor(color),
                authorName = NicknameStore.displayName(player),
                authorIconUrl = DiscordQuickSkinSupport.avatarUrl(player, DiscordConfig.current()),
            ),
        ),
    )

    private fun playerValues(player: ServerPlayer): Map<String, String> {
        val displayName = NicknameStore.displayName(player)
        return mapOf(
            "player" to DiscordText.escapeMarkdown(displayName),
            "player_raw" to displayName,
            "uuid" to player.uuid.toString(),
        )
    }

    private fun serverValues(server: MinecraftServer): Map<String, String> = mapOf(
        "online" to server.playerList.playerCount.toString(),
        "max" to server.maxPlayers.toString(),
    )

    private fun formatTps(value: Double): String = String.format(Locale.ROOT, "%.2f", (value * 100.0).roundToInt() / 100.0)
}