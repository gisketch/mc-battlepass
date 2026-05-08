package dev.gisketch.chowkingdom.discord

import dev.gisketch.chowkingdom.npc.NpcStore
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
        val content = DiscordText.applyTemplate(config.formatting.chatMessage, values)
        if (config.playerChatIdentity) {
            val displayName = NicknameStore.displayName(player)
            DiscordWebhookClient.send(
                DiscordWebhookMessage(
                    content = content,
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

    fun shippingTopSeller(server: MinecraftServer, playerName: String, itemCount: Int, amount: Long) {
        val config = DiscordConfig.current()
        if (!config.enabled) return
        val values = serverValues(server) + mapOf(
            "player" to DiscordText.cleanContent(playerName),
            "items" to itemCount.toString(),
            "amount" to amount.toString(),
        )
        DiscordWebhookClient.send(
            DiscordWebhookMessage(
                embeds = listOf(
                    DiscordEmbed(
                        title = DiscordText.applyTemplate("Top Shipping Seller", values),
                        description = DiscordText.applyTemplate("{player} sold {items} items for {amount} chowcoins.", values),
                        color = DiscordText.parseColor("#F4C542"),
                    ),
                ),
            ),
        )
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

    fun relicRolled(player: ServerPlayer, relic: String, itemName: String, itemId: String) {
        val config = DiscordConfig.current()
        if (!config.enabled || !config.relayRelicRolls) return
        val values = playerValues(player) + mapOf(
            "relic" to DiscordText.cleanContent(relic),
            "item" to DiscordText.cleanContent(itemName),
            "item_id" to itemId,
        )
        DiscordWebhookClient.send(
            DiscordWebhookMessage(
                embeds = listOf(
                    DiscordEmbed(
                        title = DiscordText.applyTemplate(config.formatting.relicTitle, values),
                        description = DiscordText.applyTemplate(config.formatting.relicDescription, values),
                        color = DiscordText.parseColor(config.formatting.relicColor),
                        authorName = NicknameStore.displayName(player),
                        authorIconUrl = DiscordQuickSkinSupport.avatarUrl(player, config),
                    ),
                ),
            ),
        )
    }

    fun npcDialog(player: ServerPlayer, npcId: String, npcName: String, message: String) {
        val config = DiscordConfig.current()
        if (!config.enabled) return
        DiscordWebhookClient.send(
            DiscordWebhookMessage(
                content = DiscordText.cleanContent(message),
                username = npcName,
                avatarUrl = DiscordQuickSkinSupport.npcAvatarUrl(npcId, config),
            ),
        )
    }

    fun npcInteraction(player: ServerPlayer, npcName: String) {
        val config = DiscordConfig.current()
        if (!config.enabled) return
        DiscordWebhookClient.send(
            DiscordWebhookMessage(
                content = "*interacts with ${DiscordText.cleanContent(npcName)}*",
                username = NicknameStore.displayName(player),
                avatarUrl = DiscordQuickSkinSupport.avatarUrl(player, config),
            ),
        )
    }

    fun npcWorldChat(npcId: String, npcName: String, message: String, mentionUserId: String? = null, fallbackName: String = "", onDiscordMessageId: (String) -> Unit = {}) {
        val config = DiscordConfig.current()
        if (!config.enabled) return
        val payload = DiscordWebhookMessage(
            content = DiscordText.cleanContent(message),
            username = npcName,
            avatarUrl = DiscordQuickSkinSupport.npcAvatarUrl(npcId, config),
        )
        DiscordWebhookClient.sendAndCaptureMessageId(payload, onDiscordMessageId)
    }

    fun npcWorldChatThinking(npcId: String, npcName: String, onDiscordMessageId: (String) -> Unit) {
        val config = DiscordConfig.current()
        if (!config.enabled) return
        DiscordWebhookClient.sendAndCaptureMessageId(
            DiscordWebhookMessage(
                content = "$npcName is thinking...",
                username = npcName,
                avatarUrl = DiscordQuickSkinSupport.npcAvatarUrl(npcId, config),
            ),
            onDiscordMessageId,
        )
    }

    fun deleteNpcWorldChatMessage(messageId: String) {
        DiscordWebhookClient.deleteMessage(messageId)
    }

    fun npcCamperArrived(server: MinecraftServer, npcId: String, npcName: String) {
        val config = DiscordConfig.current()
        if (!config.enabled) return
        val values = serverValues(server) + mapOf(
            "npc" to DiscordText.cleanContent(npcName),
            "npc_id" to npcId,
        )
        DiscordWebhookClient.send(
            DiscordWebhookMessage(
                username = npcName,
                avatarUrl = DiscordQuickSkinSupport.npcAvatarUrl(npcId, config),
                embeds = listOf(
                    DiscordEmbed(
                        title = DiscordText.applyTemplate("New Camper at the Camping Block", values),
                        description = DiscordText.applyTemplate("{npc} has arrived at camp and is looking for a bed. Talk to them to welcome them and hand over a rent contract.", values),
                        color = DiscordText.parseColor("#57F287"),
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
