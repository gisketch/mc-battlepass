package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.discord.DiscordAccountLinkStore
import dev.gisketch.chowkingdom.discord.DiscordRelay
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NpcWorldChatService {
    private val nextAllowedAtMs = ConcurrentHashMap<String, Long>()
    private val activeThinking = ConcurrentHashMap<Long, NpcWorldChatThinking>()
    private val discordMessageNpcIds = linkedMapOf<String, String>()
    private val recentWorldChat = mutableListOf<NpcWorldChatLine>()

    fun beginThinking(server: MinecraftServer, responseToken: Long, npcId: String, npcName: String) {
        activeThinking[responseToken] = NpcWorldChatThinking(npcId, npcName)
        NpcNetwork.broadcastWorldChat(server, NpcWorldChatPayload(npcId, npcName, "", null, "thinking", "is thinking..."))
        DiscordRelay.npcWorldChatThinking(npcId, npcName) { messageId ->
            val thinking = activeThinking[responseToken]
            if (thinking != null) {
                thinking.discordMessageId = messageId
            } else {
                DiscordRelay.deleteNpcWorldChatMessage(messageId)
            }
        }
    }

    fun endThinking(responseToken: Long) {
        activeThinking.remove(responseToken)?.discordMessageId?.let(DiscordRelay::deleteNpcWorldChatMessage)
    }

    fun tick(server: MinecraftServer) = Unit

    fun onMinecraftChat(player: ServerPlayer, rawMessage: String) {
        recordChat("minecraft", player.gameProfile.name, rawMessage)
        val match = matchNpc(rawMessage, allowMinecraft = true, allowDiscord = false) ?: return
        val mentionId = DiscordAccountLinkStore.linkFor(player)?.discordId
        if (!claimCooldown(player.uuid.toString(), match.definition.id)) return
        NpcLlmService.worldChat(
            player = player,
            definition = match.definition,
            message = rawMessage,
            channel = "minecraft_chat",
            matchedCallName = match.callName,
            discordMentionUserId = mentionId,
        )
    }

    fun onDiscordChat(server: MinecraftServer, authorId: String, authorName: String, rawMessage: String, referencedMessageId: String? = null) {
        recordChat("discord", authorName, rawMessage)
        val match = matchNpc(rawMessage, allowMinecraft = false, allowDiscord = true)
            ?: referencedMessageId?.let(::matchReferencedDiscordNpc)
            ?: return
        if (!claimCooldown(authorId, match.definition.id)) return
        val link = DiscordAccountLinkStore.linkForDiscord(authorId)
        val player = link
            ?.minecraftUuid
            ?.let { uuid -> runCatching { UUID.fromString(uuid) }.getOrNull() }
            ?.let(server.playerList::getPlayer)
        if (player == null) {
            NpcLlmService.worldChatDiscordGuest(
                server = server,
                definition = match.definition,
                message = rawMessage,
                matchedCallName = match.callName,
                discordAuthorId = authorId,
                discordAuthorName = authorName,
            )
            return
        }
        NpcLlmService.worldChat(
            player = player,
            definition = match.definition,
            message = rawMessage,
            channel = "discord_chat by $authorName",
            matchedCallName = match.callName,
            discordMentionUserId = authorId,
        )
    }

    fun rememberDiscordNpcMessage(messageId: String, npcId: String) {
        if (messageId.isBlank() || npcId.isBlank()) return
        synchronized(discordMessageNpcIds) {
            discordMessageNpcIds[messageId] = npcId
            while (discordMessageNpcIds.size > MAX_TRACKED_DISCORD_NPC_MESSAGES) {
                val oldest = discordMessageNpcIds.keys.firstOrNull() ?: break
                discordMessageNpcIds.remove(oldest)
            }
        }
    }

    fun recordNpcReply(npcName: String, message: String, channel: String) {
        recordChat(channel, npcName, message)
    }

    fun recentChatSummary(): String = synchronized(recentWorldChat) {
        recentWorldChat.takeLast(MAX_RECENT_WORLD_CHAT_LINES)
            .joinToString("\n") { line -> "- [${line.channel}] ${line.speaker}: ${line.message}" }
            .ifBlank { "None." }
    }

    private fun recordChat(channel: String, speaker: String, rawMessage: String) {
        val cleanMessage = rawMessage.trim().replace(Regex("\\s+"), " ").take(MAX_WORLD_CHAT_MESSAGE_LENGTH)
        if (cleanMessage.isBlank()) return
        synchronized(recentWorldChat) {
            recentWorldChat += NpcWorldChatLine(channel, speaker.trim().ifBlank { "Unknown" }, cleanMessage)
            if (recentWorldChat.size > MAX_STORED_WORLD_CHAT_LINES) {
                val overflow = recentWorldChat.size - MAX_STORED_WORLD_CHAT_LINES
                repeat(overflow) { recentWorldChat.removeAt(0) }
            }
        }
    }

    private fun claimCooldown(sourceId: String, npcId: String): Boolean {
        val settings = NpcConfig.settings().llm
        val key = "$sourceId:$npcId"
        val now = System.currentTimeMillis()
        val allowedAt = nextAllowedAtMs[key] ?: 0L
        if (now < allowedAt) return false
        nextAllowedAtMs[key] = now + settings.cooldownSeconds * 1000L
        return true
    }

    private fun matchNpc(rawMessage: String, allowMinecraft: Boolean, allowDiscord: Boolean): NpcWorldChatMatch? {
        val message = rawMessage.trim()
        if (message.isBlank()) return null
        val matches = NpcConfig.all()
            .asSequence()
            .filter { definition -> definition.chat.enabled }
            .filter { definition -> (allowMinecraft && definition.chat.minecraftChat) || (allowDiscord && definition.chat.discordChat) }
            .flatMap { definition -> definition.chat.callNames.asSequence().mapNotNull { callName -> matchScore(message, callName)?.let { score -> NpcWorldChatMatch(definition, callName, score) } } }
            .sortedWith(compareByDescending<NpcWorldChatMatch> { match -> match.score }.thenBy { match -> match.definition.id })
            .toList()
        if (matches.isEmpty()) return null
        val topScore = matches.first().score
        val topMatches = matches.filter { match -> match.score == topScore }
        if (topMatches.map { match -> match.definition.id }.distinct().size > 1) return null
        return matches.first()
    }

    private fun matchReferencedDiscordNpc(messageId: String): NpcWorldChatMatch? {
        val npcId = synchronized(discordMessageNpcIds) { discordMessageNpcIds[messageId] } ?: return null
        val definition = NpcConfig.get(npcId) ?: return null
        if (!definition.chat.enabled || !definition.chat.discordChat) return null
        return NpcWorldChatMatch(definition, "discord_reply", 40)
    }

    private fun matchScore(message: String, callName: String): Int? {
        val normalizedCallName = callName.trim().lowercase(Locale.ROOT)
        if (normalizedCallName.isBlank()) return null
        val normalizedMessage = message.lowercase(Locale.ROOT)
        val escaped = Regex.escape(normalizedCallName)
        if (Regex("(?<![a-z0-9_])@$escaped(?![a-z0-9_])").containsMatchIn(normalizedMessage)) return 30
        if (Regex("^\\s*(hey\\s+|yo\\s+|hello\\s+|hi\\s+)?$escaped(\\b|[,.!?:;])").containsMatchIn(normalizedMessage)) return 20
        if (Regex("(?<![a-z0-9_])$escaped[,.!?:;](?![a-z0-9_])").containsMatchIn(normalizedMessage)) return 10
        return null
    }
}

private data class NpcWorldChatMatch(val definition: NpcDefinition, val callName: String, val score: Int)

private data class NpcWorldChatLine(val channel: String, val speaker: String, val message: String)

private data class NpcWorldChatThinking(
    val npcId: String,
    val npcName: String,
    @Volatile var discordMessageId: String? = null,
)

private const val MAX_WORLD_CHAT_MESSAGE_LENGTH = 220
private const val MAX_STORED_WORLD_CHAT_LINES = 40
private const val MAX_RECENT_WORLD_CHAT_LINES = 20
private const val MAX_TRACKED_DISCORD_NPC_MESSAGES = 200
