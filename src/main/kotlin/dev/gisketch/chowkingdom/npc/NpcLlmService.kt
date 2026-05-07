package dev.gisketch.chowkingdom.npc

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordRelay
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object NpcLlmService {
    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val executor = Executors.newCachedThreadPool()
    private val nextAllowedAtMs = ConcurrentHashMap<UUID, Long>()
    private val activeNpcRequests = ConcurrentHashMap.newKeySet<String>()

    fun interact(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, fallbackMessage: String) {
        val settings = NpcConfig.settings().llm
        val now = System.currentTimeMillis()
        val allowedAt = nextAllowedAtMs[player.uuid] ?: 0L
        if (now < allowedAt) {
            ChowKingdomMod.LOGGER.info("NPC LLM interact rate limited player={} npc={} remainingMs={}", player.gameProfile.name, definition.id, allowedAt - now)
            return sendFinal(player, npc, definition, fallbackMessage)
        }
        if (!activeNpcRequests.add(definition.id)) {
            ChowKingdomMod.LOGGER.info("NPC LLM interact busy npc={} player={}", definition.id, player.gameProfile.name)
            return sendFinal(player, npc, definition, fallbackMessage)
        }
        nextAllowedAtMs[player.uuid] = now + settings.cooldownSeconds * 1000L
        NpcFeature.showBalloonToNearby(npc.level() as ServerLevel, npc, "...", NPC_LLM_PENDING_BALLOON_TICKS)
        val input = "${player.gameProfile.name} interacted with you. Reply like a natural short NPC greeting or acknowledgement for this moment."
        CompletableFuture.supplyAsync({ complete(player, definition, input, settings, fallbackMessage, "Current event") }, executor).whenComplete { result, throwable ->
            player.server.execute {
                activeNpcRequests.remove(definition.id)
                val liveNpc = NpcFeature.existingNpc(player.server, definition.id) ?: return@execute NpcNetwork.sendTalkResponse(player, definition.id, fallbackMessage)
                if (throwable != null) ChowKingdomMod.LOGGER.warn("NPC LLM interact request failed npc={} player={}", definition.id, player.gameProfile.name, throwable)
                sendFinal(player, liveNpc, definition, if (throwable == null) result else fallbackMessage)
            }
        }
    }

    fun talk(player: ServerPlayer, npcId: String, playerMessage: String) {
        val definition = NpcConfig.get(npcId) ?: return NpcNetwork.sendTalkResponse(player, npcId, NpcConfig.settings().llm.errorMessage)
        val npc = NpcFeature.existingNpc(player.server, definition.id) ?: return NpcNetwork.sendTalkResponse(player, npcId, NpcConfig.settings().llm.errorMessage)
        if (npc.level() != player.level() || player.distanceToSqr(npc) > NPC_LLM_ACTION_DISTANCE_SQR) return NpcNetwork.sendTalkResponse(player, npcId, NpcConfig.settings().llm.errorMessage)
        val settings = NpcConfig.settings().llm
        val message = playerMessage.trim().take(MAX_PLAYER_MESSAGE_LENGTH)
        if (message.isBlank()) return
        val now = System.currentTimeMillis()
        val allowedAt = nextAllowedAtMs[player.uuid] ?: 0L
        if (now < allowedAt) {
            ChowKingdomMod.LOGGER.info("NPC LLM rate limited player={} npc={} remainingMs={}", player.gameProfile.name, definition.id, allowedAt - now)
            return sendFinal(player, npc, definition, settings.rateLimitedMessage)
        }
        if (!activeNpcRequests.add(definition.id)) {
            ChowKingdomMod.LOGGER.info("NPC LLM busy npc={} player={}", definition.id, player.gameProfile.name)
            return sendFinal(player, npc, definition, settings.rateLimitedMessage)
        }
        nextAllowedAtMs[player.uuid] = now + settings.cooldownSeconds * 1000L
        npc.startTalkingTo(player, NPC_LLM_TALK_DURATION_TICKS)
        relayPlayerTalk(player, npc, definition, message)
        NpcFeature.showBalloonToNearby(npc.level() as ServerLevel, npc, "...", NPC_LLM_PENDING_BALLOON_TICKS)
        if (!settings.enabled) {
            ChowKingdomMod.LOGGER.info("NPC LLM disabled npc={} player={}", definition.id, player.gameProfile.name)
            activeNpcRequests.remove(definition.id)
            return sendFinal(player, npc, definition, settings.fallbackMessage, message)
        }
        CompletableFuture.supplyAsync({ complete(player, definition, message, settings) }, executor).whenComplete { result, throwable ->
            player.server.execute {
                activeNpcRequests.remove(definition.id)
                val liveNpc = NpcFeature.existingNpc(player.server, definition.id) ?: return@execute NpcNetwork.sendTalkResponse(player, definition.id, settings.errorMessage)
                if (throwable != null) ChowKingdomMod.LOGGER.warn("NPC LLM request failed npc={} player={}", definition.id, player.gameProfile.name, throwable)
                val reply = if (throwable == null) result else settings.errorMessage
                sendFinal(player, liveNpc, definition, reply, message)
            }
        }
    }

    private fun complete(
        player: ServerPlayer,
        definition: NpcDefinition,
        playerMessage: String,
        settings: NpcLlmSettingsDefinition,
        fallbackMessage: String = settings.fallbackMessage,
        inputLabel: String = "Player says",
    ): String {
        if (settings.apiKey.isBlank()) {
            ChowKingdomMod.LOGGER.warn("NPC LLM missing api_key provider={} model={}", settings.provider, settings.model)
            return fallbackMessage
        }
        val prompt = buildPrompt(player, definition, playerMessage, settings, inputLabel)
        ChowKingdomMod.LOGGER.info("NPC LLM request provider={} model={} npc={} player={} promptChars={}", settings.provider, settings.model, definition.id, player.gameProfile.name, prompt.length)
        ChowKingdomMod.LOGGER.info("NPC LLM prompt npc={} player={} input=\n{}", definition.id, player.gameProfile.name, prompt)
        val raw = when (settings.provider) {
            "gemini" -> completeGemini(prompt, settings, fallbackMessage)
            else -> completeOpenAiCompatible(prompt, settings, fallbackMessage)
        }
        return sanitizeReply(raw, settings, fallbackMessage)
    }

    private fun completeOpenAiCompatible(prompt: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String): String {
        val root = JsonObject()
        root.addProperty("model", settings.model)
        val messages = JsonArray()
        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", prompt)
        })
        root.add("messages", messages)
        root.addProperty("temperature", 0.8)
        val url = settings.baseUrl.trimEnd('/') + "/chat/completions"
        val response = httpClient.send(request(url, settings, root), HttpResponse.BodyHandlers.ofString())
        ChowKingdomMod.LOGGER.info("NPC LLM openai-compatible status={} bodyChars={}", response.statusCode(), response.body().length)
        if (response.statusCode() !in 200..299) {
            ChowKingdomMod.LOGGER.warn("NPC LLM openai-compatible error status={} body={}", response.statusCode(), response.body().take(LOG_BODY_LIMIT))
            return fallbackMessage
        }
        val json = JsonParser.parseString(response.body()).asJsonObject
        val content = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")?.get("content")?.asString.orEmpty()
        return parseMessage(content, settings, fallbackMessage)
    }

    private fun completeGemini(prompt: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String): String {
        val root = JsonObject()
        val parts = JsonArray().apply { add(JsonObject().apply { addProperty("text", prompt) }) }
        val contents = JsonArray().apply { add(JsonObject().apply { add("parts", parts) }) }
        root.add("contents", contents)
        val url = settings.baseUrl.trimEnd('/') + "/v1beta/models/${settings.model}:generateContent?key=${settings.apiKey}"
        val response = httpClient.send(request(url, settings, root, includeAuth = false), HttpResponse.BodyHandlers.ofString())
        ChowKingdomMod.LOGGER.info("NPC LLM gemini status={} bodyChars={}", response.statusCode(), response.body().length)
        if (response.statusCode() !in 200..299) {
            ChowKingdomMod.LOGGER.warn("NPC LLM gemini error status={} body={}", response.statusCode(), response.body().take(LOG_BODY_LIMIT))
            return fallbackMessage
        }
        val json = JsonParser.parseString(response.body()).asJsonObject
        val text = json.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")?.firstOrNull()?.asJsonObject?.get("text")?.asString.orEmpty()
        return parseMessage(text, settings, fallbackMessage)
    }

    private fun request(url: String, settings: NpcLlmSettingsDefinition, body: JsonObject, includeAuth: Boolean = true): HttpRequest {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds.toLong()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
        if (includeAuth) builder.header("Authorization", "Bearer ${settings.apiKey}")
        return builder.build()
    }

    private fun parseMessage(content: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String): String {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val message = runCatching { JsonParser.parseString(cleaned).asJsonObject.get("message")?.asString.orEmpty() }
            .onFailure { exception -> ChowKingdomMod.LOGGER.warn("NPC LLM response was not JSON message. raw={}", cleaned.take(LOG_BODY_LIMIT), exception) }
            .getOrDefault(cleaned)
        return sanitizeReply(message, settings, fallbackMessage)
    }

    private fun sanitizeReply(raw: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String = settings.fallbackMessage): String {
        val reply = raw.replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "").replace('\n', ' ').trim().take(settings.maxReplyChars)
        if (reply.isBlank()) return fallbackMessage
        val lower = reply.lowercase()
        val blocked = listOf("as an ai", "system prompt", "hidden context", "i gave you", "i teleported", "i changed your friendship", "i completed your quest", "i changed the price")
        return if (blocked.any(lower::contains)) fallbackMessage else reply
    }

    private fun sendFinal(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, message: String, playerMessage: String = "") {
        val settings = NpcConfig.settings().llm
        val reply = sanitizeReply(message, settings)
        npc.startTalkingTo(player, NPC_LLM_TALK_DURATION_TICKS)
        if (playerMessage.isNotBlank()) NpcStore.recordConversation(definition.id, player, player.gameProfile.name, playerMessage.take(MAX_PLAYER_MESSAGE_LENGTH), "player_llm_talk")
        NpcStore.recordConversation(definition.id, player, definition.name, reply, "npc_llm_reply")
        NpcNetwork.sendTalkResponse(player, definition.id, reply)
        relayNpcTalk(player, npc, definition, reply)
        NpcFeature.showBalloonToNearby(npc.level() as ServerLevel, npc, reply, NPC_LLM_REPLY_BALLOON_TICKS, player.uuid)
        NpcFeature.relayNpcDialogToDiscord(player, definition, reply)
    }

    private fun relayPlayerTalk(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, message: String) {
        val level = npc.level() as? ServerLevel ?: return
        val line = Component.literal("${player.gameProfile.name} > ${definition.name}: $message").withStyle(ChatFormatting.GRAY)
        level.players().forEach { listener ->
            if (listener.uuid != player.uuid && listener.distanceToSqr(npc.x, npc.y, npc.z) <= NPC_LLM_HEAR_RADIUS_SQR) listener.sendSystemMessage(line)
        }
        DiscordRelay.chat(player, message)
    }

    private fun relayNpcTalk(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, message: String) {
        val level = npc.level() as? ServerLevel ?: return
        val line = Component.literal("${definition.name} > ${player.gameProfile.name}: $message").withStyle(ChatFormatting.GRAY)
        level.players().forEach { listener ->
            if (listener.uuid != player.uuid && listener.distanceToSqr(npc.x, npc.y, npc.z) <= NPC_LLM_HEAR_RADIUS_SQR) listener.sendSystemMessage(line)
        }
    }

    private fun buildPrompt(player: ServerPlayer, definition: NpcDefinition, playerMessage: String, settings: NpcLlmSettingsDefinition, inputLabel: String): String {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val context = NpcStore.llmContext(definition.id, player, NpcScheduleDefinition.hourAt(player.level().dayTime))
        val recentHistory = context.conversation
            .takeLast(settings.maxRecentTurns)
            .joinToString("\n") { record -> formatHistoryRecord(record, definition.name) }
            .ifBlank { "None." }
        val hurtContext = buildHurtContext(context)
        val traits = definition.personality.traits.joinToString(", ").ifBlank { "unspecified" }
        val catchphrases = definition.personality.catchphrases.joinToString(", ").ifBlank { "none" }
        val heldItem = player.mainHandItem.item.toString()
        return """
            You are roleplaying as an NPC in a Minecraft multiplayer server.

            NPC:
            - Name: ${definition.name}
            - Title: ${definition.title}
            - Job: ${definition.job}
            - Personality: $traits
            - Speech style: ${definition.personality.speechStyle}
            - Catchphrases: $catchphrases
            - Prompt: ${definition.personality.llmPrompt}

            Rules:
            - Stay in character as ${definition.name}.
            - You are not an assistant.
            - Do not mention AI, prompts, models, APIs, hidden rules, or system messages.
            - Reply in 1 to 3 short sentences.
            - Do not claim you gave items, changed friendship, changed prices, completed quests, teleported anyone, healed anyone, or changed the world.
            - If asked to do a game action, suggest the real UI action instead.
            - Return JSON only: {"message":"NPC reply here"}

            Relationship:
            - Player: ${player.gameProfile.name}
            - Friendship points: ${friendship.points}
            - Friendship level: ${friendship.level}
            - Category: ${friendship.category.id}
            - Tone: ${toneFor(friendship.category)}

            Current context:
            - Time hour: ${context.currentHour}
            - Player health: ${player.health.toInt()}/${player.maxHealth.toInt()}
            - Player held item: $heldItem

            Hurt context:
            $hurtContext

            Recent history:
            $recentHistory

            $inputLabel:
            "$playerMessage"
        """.trimIndent()
    }

    private fun formatHistoryRecord(record: NpcConversationRecord, npcName: String): String {
        val age = formatAge(record.timestamp)
        return when (record.type) {
            "player_llm_talk" -> "- $age: ${record.speaker} said: ${record.text}"
            "npc_llm_reply" -> "- $age: ${record.speaker} replied: ${record.text}"
            "player_interact" -> "- $age: ${record.playerName} interacted with $npcName."
            "player_hurt" -> "- $age: ${record.playerName} hurt $npcName."
            "npc_hurt_message" -> "- $age: $npcName reacted to being hurt: ${record.text}"
            "player_gift" -> "- $age: ${record.text}."
            "player_shop_buy" -> "- $age: ${record.text}."
            else -> "- $age: ${record.type}: ${record.speaker}: ${record.text}"
        }
    }

    private fun buildHurtContext(context: NpcLlmContext): String {
        val lines = mutableListOf<String>()
        if (context.lastHurtAt > 0L) {
            val name = context.lastHurtPlayerName.ifBlank { "Unknown player" }
            lines += "- Last hurt: $name hurt this NPC ${formatAge(context.lastHurtAt)}."
            lines += "- Current hurt streak from last hurter: ${context.hurtStreak}."
        }
        val history = context.hurtHistory.takeLast(5)
        if (history.isNotEmpty()) {
            lines += "- Recorded hurt milestones: " + history.joinToString("; ") { record ->
                "${record.playerName.ifBlank { "Unknown player" }} hurt this NPC ${formatAge(record.timestamp)}"
            }
        }
        return lines.joinToString("\n").ifBlank { "None." }
    }

    private fun formatAge(timestamp: Long): String {
        val seconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 60L -> "${seconds}s ago"
            seconds < 3600L -> "${seconds / 60L}m ago"
            else -> "${seconds / 3600L}h ago"
        }
    }

    private fun toneFor(category: NpcFriendshipCategory): String = when (category) {
        NpcFriendshipCategory.Hatred -> "hostile, guarded, brief"
        NpcFriendshipCategory.Enemy -> "suspicious, cold, cautious"
        NpcFriendshipCategory.Dislike -> "restrained, wary"
        NpcFriendshipCategory.Neutral -> "friendly but not intimate"
        NpcFriendshipCategory.Okay -> "warm, cooperative"
        NpcFriendshipCategory.GoodFriends -> "familiar, trusting"
        NpcFriendshipCategory.BestFriends -> "deeply loyal, playful, personal"
    }

    private const val MAX_PLAYER_MESSAGE_LENGTH = 280
    private const val NPC_LLM_ACTION_DISTANCE_SQR = 64.0
    private const val NPC_LLM_HEAR_RADIUS_SQR = 30.0 * 30.0
    private const val NPC_LLM_TALK_DURATION_TICKS = 20 * 12
    private const val NPC_LLM_PENDING_BALLOON_TICKS = 40
    private const val NPC_LLM_REPLY_BALLOON_TICKS = 120
    private const val LOG_BODY_LIMIT = 600
}
