package dev.gisketch.chowkingdom.npc

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordRelay
import dev.gisketch.chowkingdom.roles.RoleStore
import dev.gisketch.chowkingdom.roles.RolesConfig
import dev.gisketch.chowkingdom.roles.SereneSeasonSupport
import dev.gisketch.chowkingdom.shops.StoreShopFeature
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
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
    private val activeNpcRequests = ConcurrentHashMap<String, ActiveNpcRequest>()
    private val talkSessions = ConcurrentHashMap<String, NpcTalkSession>()
    private val activeWorldChatRequests = ConcurrentHashMap<String, Long>()
    private val cancelledResponseTokens = ConcurrentHashMap.newKeySet<Long>()
    private val detachedResponseTokens = ConcurrentHashMap.newKeySet<Long>()
    private val recentDialogReplies = ConcurrentHashMap<NpcRecentDialogReplyKey, NpcRecentDialogReply>()
    private val recentLlmErrors = ArrayDeque<NpcLlmDebugEntry>()

    fun debugErrorLines(limit: Int = 8): List<String> = synchronized(recentLlmErrors) {
        recentLlmErrors.takeLast(limit.coerceIn(1, MAX_LLM_DEBUG_ERRORS)).map { entry ->
            val status = entry.status?.toString() ?: "n/a"
            val body = entry.body.takeIf(String::isNotBlank)?.let { " body=$it" }.orEmpty()
            "${formatAge(entry.timestamp)} provider=${entry.provider} model=${entry.model} status=$status source=${entry.source} ${entry.message}$body"
        }
    }

    fun cancel(player: ServerPlayer, npcId: String) {
        val session = talkSessions[npcId]
        if (session != null) {
            val removeSession = synchronized(session) {
                session.participants.remove(player.uuid)
                if (session.participants.isEmpty()) {
                    if (session.activeResponseToken != 0L) cancelledResponseTokens += session.activeResponseToken
                    true
                } else {
                    false
                }
            }
            if (removeSession) talkSessions.remove(npcId, session)
        }
        val request = activeNpcRequests[npcId] ?: return
        if (request.playerId != player.uuid) return
        cancelledResponseTokens += request.responseToken
        detachedResponseTokens.remove(request.responseToken)
        activeNpcRequests.remove(npcId, request)
    }

    fun leaveDialog(player: ServerPlayer, npcId: String) {
        val detachedTalk = detachFromTalkSession(player, npcId)
        val request = activeNpcRequests[npcId]
        if (request?.playerId == player.uuid) {
            detachedResponseTokens += request.responseToken
            return
        }
        if (!detachedTalk) showRecentDialogReplyBalloon(player, npcId)
    }

    fun joinConversation(player: ServerPlayer, npcId: String) {
        val definition = NpcConfig.get(npcId) ?: return
        val npc = NpcFeature.existingNpc(player.server, definition.id) ?: return
        if (npc.level() != player.level() || player.distanceToSqr(npc) > NPC_LLM_ACTION_DISTANCE_SQR) return
        joinConversationSession(player, definition.id)
    }

    fun talkSnapshot(npcId: String): NpcTalkSnapshot? {
        val session = talkSessions[npcId] ?: return null
        return synchronized(session) {
            val participants = session.participants.values.map { participant -> NpcTalkParticipantSnapshot(participant.playerId, participant.name) }
            if (participants.isEmpty()) null else NpcTalkSnapshot(participants)
        }
    }

    fun interact(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, fallbackMessage: String) {
        event(
            player = player,
            npc = npc,
            definition = definition,
            fallbackMessage = fallbackMessage,
            input = "${player.gameProfile.name} interacted with you again. Reply with a fresh 2-3 sentence in-character acknowledgement for this exact moment. Do not reuse any recent greeting or threat; vary the wording and show personality.",
            inputLabel = "Current event",
            npcRecordType = "npc_llm_interact",
        )
    }

    fun event(
        player: ServerPlayer,
        npc: ChowNpcEntity,
        definition: NpcDefinition,
        fallbackMessage: String,
        input: String,
        inputLabel: String = "Current event",
        sendTalkResponse: Boolean = true,
        excludePlayerFromBalloon: Boolean = true,
        showBalloon: Boolean = true,
        npcRecordType: String = "npc_llm_event",
        responseToken: Long = NpcDialogTokens.next(),
    ) {
        val settings = NpcConfig.settings().llm
        if (!startRequest(definition.id, player, responseToken)) {
            ChowKingdomMod.LOGGER.info("NPC LLM event busy npc={} player={}", definition.id, player.gameProfile.name)
            return sendFinal(player, npc, definition, fallbackMessage, fallbackMessage = fallbackMessage, sendTalkResponse = sendTalkResponse, excludePlayerFromBalloon = excludePlayerFromBalloon, showBalloon = showBalloon, npcRecordType = npcRecordType, responseToken = responseToken)
        }
        if (showBalloon) NpcFeature.showBalloonToNearby(npc.level() as ServerLevel, npc, "...", NPC_LLM_PENDING_BALLOON_TICKS, if (excludePlayerFromBalloon) player.uuid else null)
        CompletableFuture.supplyAsync({ complete(player, definition, input, settings, fallbackMessage, inputLabel) { partial -> player.server.execute { NpcNetwork.sendTalkResponse(player, definition.id, partial, responseToken, partial = true) } } }, executor).whenComplete { result, throwable ->
            player.server.execute {
                if (!finishRequest(definition.id, responseToken)) return@execute
                val liveNpc = NpcFeature.existingNpc(player.server, definition.id) ?: return@execute NpcNetwork.sendTalkResponse(player, definition.id, fallbackMessage, responseToken)
                if (throwable != null) ChowKingdomMod.LOGGER.warn("NPC LLM event request failed npc={} player={}", definition.id, player.gameProfile.name, throwable)
                val completion = if (throwable == null) result else NpcLlmCompletion(fallbackMessage)
                sendFinal(player, liveNpc, definition, completion.message, fallbackMessage = fallbackMessage, sendTalkResponse = sendTalkResponse, excludePlayerFromBalloon = excludePlayerFromBalloon, showBalloon = showBalloon, npcRecordType = npcRecordType, responseToken = responseToken, memorable = completion.memorable)
            }
        }
    }

    fun giftSentiment(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, fallbackMessage: String, input: String, responseToken: Long, onComplete: (NpcGiftSentimentResult) -> Unit) {
        val settings = NpcConfig.settings().llm
        if (!startRequest(definition.id, player, responseToken)) return onComplete(NpcGiftSentimentResult(fallbackMessage, "neutral"))
        CompletableFuture.supplyAsync({ complete(player, definition, input, settings, fallbackMessage, "Gift sentiment") }, executor).whenComplete { result, throwable ->
            player.server.execute {
                if (!finishRequest(definition.id, responseToken)) return@execute
                if (throwable != null) ChowKingdomMod.LOGGER.warn("NPC LLM gift sentiment request failed npc={} player={}", definition.id, player.gameProfile.name, throwable)
                val completion = if (throwable == null) result else NpcLlmCompletion(fallbackMessage, giftSentiment = "neutral")
                onComplete(NpcGiftSentimentResult(completion.message, completion.giftSentiment.ifBlank { "neutral" }, completion.memorable))
            }
        }
    }

    fun quiz(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, mission: NpcMissionDefinition, fallback: NpcQuizLlmResult, responseToken: Long, onComplete: (NpcQuizLlmResult) -> Unit) {
        val settings = NpcConfig.settings().llm
        if (!settings.enabled) return onComplete(fallback)
        if (!startRequest(definition.id, player, responseToken)) return onComplete(fallback)
        val input = quizPrompt(player, definition, mission)
        CompletableFuture.supplyAsync({ complete(player, definition, input, settings, fallback.message, "Quiz request") }, executor).whenComplete { result, throwable ->
            player.server.execute {
                if (!finishRequest(definition.id, responseToken)) return@execute
                if (throwable != null) ChowKingdomMod.LOGGER.warn("NPC LLM quiz request failed npc={} player={}", definition.id, player.gameProfile.name, throwable)
                val completion = if (throwable == null) result else NpcLlmCompletion(fallback.message)
                onComplete(quizResult(completion, fallback))
            }
        }
    }

    fun talk(player: ServerPlayer, npcId: String, playerMessage: String, responseToken: Long) {
        val definition = NpcConfig.get(npcId) ?: return NpcNetwork.sendTalkResponse(player, npcId, NpcConfig.settings().llm.errorMessage, responseToken)
        val npc = NpcFeature.existingNpc(player.server, definition.id) ?: return NpcNetwork.sendTalkResponse(player, npcId, NpcConfig.settings().llm.errorMessage, responseToken)
        if (npc.level() != player.level() || player.distanceToSqr(npc) > NPC_LLM_ACTION_DISTANCE_SQR) return NpcNetwork.sendTalkResponse(player, npcId, NpcConfig.settings().llm.errorMessage, responseToken)
        val settings = NpcConfig.settings().llm
        val message = playerMessage.trim().take(MAX_PLAYER_MESSAGE_LENGTH)
        if (message.isBlank()) return
        val now = System.currentTimeMillis()
        val allowedAt = nextAllowedAtMs[player.uuid] ?: 0L
        if (now < allowedAt) {
            ChowKingdomMod.LOGGER.info("NPC LLM rate limited player={} npc={} remainingMs={}", player.gameProfile.name, definition.id, allowedAt - now)
            return sendFinal(player, npc, definition, settings.rateLimitedMessage, responseToken = responseToken)
        }
        val session = joinConversationSession(player, definition.id)
        val promptInput = addTalkTurn(session, player, message, responseToken)
        activeNpcRequests.remove(definition.id)?.let { request -> cancelledResponseTokens += request.responseToken }
        startTalkRequest(session, responseToken)
        nextAllowedAtMs[player.uuid] = now + settings.cooldownSeconds * 1000L
        npc.startTalkingTo(player, NPC_LLM_TALK_DURATION_TICKS)
        relayPlayerTalk(player, npc, definition, message)
        NpcFeature.showBalloonToNearby(npc.level() as ServerLevel, npc, "...", NPC_LLM_PENDING_BALLOON_TICKS, player.uuid)
        if (!settings.enabled) {
            ChowKingdomMod.LOGGER.info("NPC LLM disabled npc={} player={}", definition.id, player.gameProfile.name)
            finishTalkRequest(definition.id, session, responseToken)?.let { target ->
                return sendGroupFinal(player, npc, definition, settings.fallbackMessage, target, fallbackMessage = settings.fallbackMessage)
            }
            return
        }
        CompletableFuture.supplyAsync({ complete(player, definition, promptInput, settings, inputLabel = "Conversation") { partial -> player.server.execute { NpcNetwork.sendTalkResponse(player, definition.id, partial, responseToken, partial = true) } } }, executor).whenComplete { result, throwable ->
            player.server.execute {
                val target = finishTalkRequest(definition.id, session, responseToken) ?: return@execute
                val liveNpc = NpcFeature.existingNpc(player.server, definition.id) ?: return@execute NpcNetwork.sendTalkResponse(player, definition.id, settings.errorMessage, responseToken)
                if (throwable != null) ChowKingdomMod.LOGGER.warn("NPC LLM request failed npc={} player={}", definition.id, player.gameProfile.name, throwable)
                val completion = if (throwable == null) result else NpcLlmCompletion(settings.errorMessage)
                sendGroupFinal(player, liveNpc, definition, completion.message, target, fallbackMessage = settings.errorMessage, memorable = completion.memorable)
            }
        }
    }

    fun worldChat(
        player: ServerPlayer,
        definition: NpcDefinition,
        message: String,
        channel: String,
        matchedCallName: String,
        discordMentionUserId: String? = null,
    ) {
        val settings = NpcConfig.settings().llm
        if (!settings.enabled) return
        val cleanMessage = message.trim().take(MAX_PLAYER_MESSAGE_LENGTH)
        if (cleanMessage.isBlank()) return
        val responseToken = NpcDialogTokens.next()
        startWorldChatRequest(definition.id, responseToken)
        NpcWorldChatService.beginThinking(player.server, responseToken, definition.id, definition.name)
        val input = buildWorldChatInput(player, definition, cleanMessage, channel, matchedCallName)
        CompletableFuture.supplyAsync({ complete(player, definition, input, settings, inputLabel = "World chat message") }, executor).whenComplete { result, throwable ->
            player.server.execute {
                if (!finishWorldChatRequest(definition.id, responseToken)) return@execute
                if (throwable != null) ChowKingdomMod.LOGGER.warn("NPC world chat LLM failed npc={} player={} channel={}", definition.id, player.gameProfile.name, channel, throwable)
                val completion = if (throwable == null) result else NpcLlmCompletion(settings.errorMessage)
                sendWorldChatFinal(player, definition, cleanMessage, completion.message, settings.errorMessage, channel, discordMentionUserId, completion.memorable)
            }
        }
    }

    fun worldChatDiscordGuest(
        server: MinecraftServer,
        definition: NpcDefinition,
        message: String,
        matchedCallName: String,
        discordAuthorId: String,
        discordAuthorName: String,
    ) {
        val settings = NpcConfig.settings().llm
        if (!settings.enabled) return
        val cleanMessage = message.trim().take(MAX_PLAYER_MESSAGE_LENGTH)
        if (cleanMessage.isBlank()) return
        val responseToken = NpcDialogTokens.next()
        startWorldChatRequest(definition.id, responseToken)
        NpcWorldChatService.beginThinking(server, responseToken, definition.id, definition.name)
        val input = buildDiscordGuestWorldChatPrompt(server, definition, cleanMessage, matchedCallName, discordAuthorName)
        CompletableFuture.supplyAsync({ completeRaw(input, settings, settings.errorMessage) }, executor).whenComplete { result, throwable ->
            server.execute {
                if (!finishWorldChatRequest(definition.id, responseToken)) return@execute
                if (throwable != null) ChowKingdomMod.LOGGER.warn("NPC Discord guest world chat LLM failed npc={} discordUser={}", definition.id, discordAuthorName, throwable)
                val completion = if (throwable == null) result else NpcLlmCompletion(settings.errorMessage)
                sendDiscordGuestWorldChatFinal(server, definition, discordAuthorName, cleanMessage, completion.message, settings.errorMessage, completion.memorable)
            }
        }
    }

    private fun completeRaw(prompt: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String): NpcLlmCompletion {
        if (settings.apiKey.isBlank()) {
            ChowKingdomMod.LOGGER.warn("NPC LLM missing api_key provider={} model={}", settings.provider, settings.model)
            recordLlmError(settings.provider, settings.model, "config", null, "missing api_key")
            return NpcLlmCompletion(fallbackMessage)
        }
        val raw = when (settings.provider) {
            "gemini" -> completeGemini(prompt, settings, fallbackMessage)
            else -> completeOpenAiCompatible(prompt, settings, fallbackMessage)
        }
        return raw.copy(message = sanitizeReply(raw.message, settings, fallbackMessage))
    }

    private fun startWorldChatRequest(npcId: String, responseToken: Long) {
        cancelledResponseTokens.remove(responseToken)
        activeWorldChatRequests.put(npcId, responseToken)?.let { previous ->
            cancelledResponseTokens += previous
            NpcWorldChatService.endThinking(previous)
        }
    }

    private fun finishWorldChatRequest(npcId: String, responseToken: Long): Boolean {
        val active = activeWorldChatRequests[npcId]
        if (active != responseToken) {
            NpcWorldChatService.endThinking(responseToken)
            return false
        }
        activeWorldChatRequests.remove(npcId, responseToken)
        NpcWorldChatService.endThinking(responseToken)
        return !cancelledResponseTokens.remove(responseToken)
    }

    private fun buildWorldChatInput(player: ServerPlayer, definition: NpcDefinition, message: String, channel: String, matchedCallName: String): String = """
        Channel: $channel
        Matched call name: $matchedCallName
        Speaker: ${player.gameProfile.name}
        This is remote world chat, not the local NPC dialog screen.
        Do not assume the player is standing beside you unless context says they are nearby.
        Recent world chat from all players and NPCs:
        ${NpcWorldChatService.recentChatSummary()}
        Reply like a normal chat message from ${definition.name}.
        Player message: $message
    """.trimIndent()

    private fun buildDiscordGuestWorldChatPrompt(server: MinecraftServer, definition: NpcDefinition, message: String, matchedCallName: String, discordAuthorName: String): String {
        val settings = NpcConfig.settings().llm
        val recentGlobalEvents = NpcStore.recentGlobalEvents()
            .takeLast(5)
            .joinToString("\n") { event -> "- ${formatAge(event.timestamp)}: ${event.type}: ${event.text}" }
            .ifBlank { "None." }
        val globalMemories = NpcStore.recentGlobalMemories()
            .takeLast(8)
            .joinToString("\n") { memory -> "- ${formatAge(memory.timestamp)}: ${memory.type}: ${memory.text}" }
            .ifBlank { "None." }
        val traits = definition.personality.traits.joinToString(", ").ifBlank { "unspecified" }
        val catchphrases = definition.personality.catchphrases.joinToString(", ").ifBlank { "none" }
        val level = server.overworld()
        val hour = NpcTime.hour(level)
        val season = SereneSeasonSupport.currentSeasonStatus(level)
        val storeContext = buildStoreContext(definition)
        return """
            You are roleplaying as an NPC in a Minecraft multiplayer server.

            NPC:
            - Name: ${definition.name}
            - Title: ${definition.title}
            - Job: ${definition.job}
            - Personality: $traits
            - Speech style: ${definition.personality.speechStyle}
            - Catchphrase references: $catchphrases
            - Prompt: ${definition.personality.llmPrompt}
            ${NpcPokemonCompanions.llmSummary(definition)}

            Rules:
            - Stay in character as ${definition.name}.
            - You are not an assistant.
            - Treat catchphrases as optional style references. Use them rarely, vary the wording, and do not repeat them every reply.
            - Reply in 1 to 3 short sentences.
            - Use plain ASCII only with letters, numbers, spaces, and basic punctuation.
            - You may use <b>...</b> around short important highlights only. Highlight concrete numbers, item names, class names, costs, counts, requirements, places, deadlines, and action words the player should notice.
            - Do not highlight whole sentences. Prefer 1 to 4 highlighted spans per reply.
            - Only use the exact tags <b> and </b>; do not use markdown, HTML tags besides <b>, or nested tags.
            - Do not claim you gave items, changed friendship, changed prices, completed quests, teleported anyone, healed anyone, or changed the world.
            - Return JSON only: {"message":"NPC reply here","memorable":null}

            Channel context:
            - Channel: discord_chat
            - Speaker: $discordAuthorName (Discord user)
            - Linked Minecraft player: no
            - Matched call name: $matchedCallName
            - Time hour: $hour
            - Season: ${season?.seasonName ?: "unavailable"}
            - Season day: ${season?.day?.toString() ?: "unavailable"}
            - Treat this speaker as a Discord user. Do not pretend you know their Minecraft inventory, location, or friendship.

            Store context:
            $storeContext

            Recent global events:
            $recentGlobalEvents

            Important global memories:
            $globalMemories

            Recent world chat from all players and NPCs:
            ${NpcWorldChatService.recentChatSummary()}

            World chat message:
            "$message"
        """.trimIndent().take(settings.maxReplyChars * 80)
    }

    private fun joinConversationSession(player: ServerPlayer, npcId: String): NpcTalkSession {
        val session = talkSessions.computeIfAbsent(npcId) { NpcTalkSession() }
        synchronized(session) {
            session.participants.putIfAbsent(player.uuid, NpcTalkParticipant(player.uuid, player.gameProfile.name))
        }
        return session
    }

    private fun addTalkTurn(session: NpcTalkSession, player: ServerPlayer, message: String, responseToken: Long): String = synchronized(session) {
        val participant = session.participants.getOrPut(player.uuid) { NpcTalkParticipant(player.uuid, player.gameProfile.name) }
        participant.responseToken = responseToken
        session.detachedParticipants.remove(player.uuid)
        session.pendingTurns += NpcTalkTurn(player.uuid, player.gameProfile.name, message)
        val names = session.participants.values.joinToString(", ") { participantSnapshot -> participantSnapshot.name }
        val lines = session.pendingTurns.takeLast(MAX_GROUP_TURNS).joinToString("\n") { turn -> "- ${turn.playerName}: ${turn.message}" }
        "Players in this conversation: $names\nNew player messages to answer together:\n$lines\nReply once as the NPC to the whole conversation."
    }

    private fun startTalkRequest(session: NpcTalkSession, responseToken: Long) {
        cancelledResponseTokens.remove(responseToken)
        synchronized(session) {
            if (session.activeResponseToken != 0L) cancelledResponseTokens += session.activeResponseToken
            session.activeResponseToken = responseToken
        }
    }

    private fun finishTalkRequest(npcId: String, session: NpcTalkSession, responseToken: Long): NpcTalkResponseTarget? = synchronized(session) {
        if (session.activeResponseToken != responseToken) return@synchronized null
        session.activeResponseToken = 0L
        if (cancelledResponseTokens.remove(responseToken)) return@synchronized null
        val activeParticipants = session.participants.values.map { participant -> participant.copy() }
        val detachedParticipants = session.detachedParticipants.values.map { participant -> participant.copy() }
        val participants = activeParticipants + detachedParticipants
        val turns = session.pendingTurns.toList()
        session.pendingTurns.clear()
        session.participants.values.forEach { participant -> participant.responseToken = 0L }
        session.detachedParticipants.clear()
        if (participants.isEmpty()) {
            talkSessions.remove(npcId, session)
            null
        } else {
            NpcTalkResponseTarget(participants, turns, activeParticipants.map { participant -> participant.playerId }.toSet())
        }
    }

    private fun detachFromTalkSession(player: ServerPlayer, npcId: String): Boolean {
        val session = talkSessions[npcId] ?: return false
        var removeSession = false
        var detached = false
        synchronized(session) {
            val participant = session.participants.remove(player.uuid)
            if (participant != null && (participant.responseToken != 0L || session.activeResponseToken != 0L)) {
                session.detachedParticipants[player.uuid] = participant.copy()
                detached = session.activeResponseToken != 0L
            }
            removeSession = session.activeResponseToken == 0L && session.pendingTurns.isEmpty() && session.participants.isEmpty() && session.detachedParticipants.isEmpty()
        }
        if (removeSession) talkSessions.remove(npcId, session)
        return detached
    }

    private fun startRequest(npcId: String, player: ServerPlayer, responseToken: Long): Boolean {
        cancelledResponseTokens.remove(responseToken)
        while (true) {
            val current = activeNpcRequests[npcId]
            if (current == null) {
                if (activeNpcRequests.putIfAbsent(npcId, ActiveNpcRequest(player.uuid, responseToken)) == null) return true
                continue
            }
            if (current.playerId != player.uuid) return false
            cancelledResponseTokens += current.responseToken
            detachedResponseTokens.remove(current.responseToken)
            if (activeNpcRequests.replace(npcId, current, ActiveNpcRequest(player.uuid, responseToken))) return true
        }
    }

    private fun finishRequest(npcId: String, responseToken: Long): Boolean {
        val request = activeNpcRequests[npcId]
        if (request?.responseToken == responseToken) activeNpcRequests.remove(npcId, request)
        val cancelled = cancelledResponseTokens.remove(responseToken)
        if (cancelled) detachedResponseTokens.remove(responseToken)
        return !cancelled
    }

    private fun complete(
        player: ServerPlayer,
        definition: NpcDefinition,
        playerMessage: String,
        settings: NpcLlmSettingsDefinition,
        fallbackMessage: String = settings.fallbackMessage,
        inputLabel: String = "Player says",
        onPartial: ((String) -> Unit)? = null,
    ): NpcLlmCompletion {
        if (settings.apiKey.isBlank()) {
            ChowKingdomMod.LOGGER.warn("NPC LLM missing api_key provider={} model={}", settings.provider, settings.model)
            recordLlmError(settings.provider, settings.model, "config", null, "missing api_key")
            return NpcLlmCompletion(fallbackMessage)
        }
        val prompt = buildPrompt(player, definition, playerMessage, settings, inputLabel)
        ChowKingdomMod.LOGGER.info("NPC LLM request provider={} model={} npc={} player={} promptChars={}", settings.provider, settings.model, definition.id, player.gameProfile.name, prompt.length)
        ChowKingdomMod.LOGGER.info("NPC LLM prompt npc={} player={} input=\n{}", definition.id, player.gameProfile.name, prompt)
        val raw = if (settings.llmStreaming && settings.provider != "gemini" && onPartial != null) {
            completeOpenAiCompatibleStreaming(streamingPrompt(prompt), settings, fallbackMessage, onPartial)
        } else when (settings.provider) {
            "gemini" -> completeGemini(prompt, settings, fallbackMessage)
            else -> completeOpenAiCompatible(prompt, settings, fallbackMessage)
        }
        return raw.copy(message = sanitizeReply(raw.message, settings, fallbackMessage))
    }

    private fun completeOpenAiCompatible(prompt: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String): NpcLlmCompletion {
        val root = JsonObject()
        root.addProperty("model", settings.model)
        val messages = JsonArray()
        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", prompt)
        })
        root.add("messages", messages)
        root.addProperty("temperature", 0.8)
        applyNoThinkingOptions(root, settings)
        val url = settings.baseUrl.trimEnd('/') + "/chat/completions"
        val response = try {
            httpClient.send(request(url, settings, root), HttpResponse.BodyHandlers.ofString())
        } catch (exception: Exception) {
            recordLlmError(settings.provider, settings.model, "openai_compatible", null, "request failed: ${exception.javaClass.simpleName}: ${exception.message.orEmpty()}")
            return NpcLlmCompletion(fallbackMessage)
        }
        ChowKingdomMod.LOGGER.info("NPC LLM openai-compatible status={} bodyChars={}", response.statusCode(), response.body().length)
        if (response.statusCode() !in 200..299) {
            ChowKingdomMod.LOGGER.warn("NPC LLM openai-compatible error status={} body={}", response.statusCode(), response.body().take(LOG_BODY_LIMIT))
            recordLlmError(settings.provider, settings.model, "openai_compatible", response.statusCode(), "http error", response.body())
            return NpcLlmCompletion(fallbackMessage)
        }
        val content = runCatching {
            val json = JsonParser.parseString(response.body()).asJsonObject
            json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")?.get("content")?.asString.orEmpty()
        }.getOrElse { exception ->
            recordLlmError(settings.provider, settings.model, "openai_compatible", response.statusCode(), "response parse failed: ${exception.javaClass.simpleName}", response.body())
            return NpcLlmCompletion(fallbackMessage)
        }
        if (content.isBlank()) recordLlmError(settings.provider, settings.model, "openai_compatible", response.statusCode(), "empty response content", response.body())
        return parseMessage(content, settings, fallbackMessage, "openai_compatible")
    }

    private fun completeOpenAiCompatibleStreaming(prompt: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String, onPartial: (String) -> Unit): NpcLlmCompletion {
        val root = JsonObject()
        root.addProperty("model", settings.model)
        root.addProperty("stream", true)
        val messages = JsonArray()
        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", prompt)
        })
        root.add("messages", messages)
        root.addProperty("temperature", 0.55)
        applyNoThinkingOptions(root, settings)
        val url = settings.baseUrl.trimEnd('/') + "/chat/completions"
        val response = try {
            httpClient.send(request(url, settings, root), HttpResponse.BodyHandlers.ofLines())
        } catch (exception: Exception) {
            recordLlmError(settings.provider, settings.model, "openai_stream", null, "request failed: ${exception.javaClass.simpleName}: ${exception.message.orEmpty()}")
            return NpcLlmCompletion(fallbackMessage)
        }
        if (response.statusCode() !in 200..299) {
            recordLlmError(settings.provider, settings.model, "openai_stream", response.statusCode(), "http error")
            return NpcLlmCompletion(fallbackMessage)
        }
        val startedAtMs = System.currentTimeMillis()
        val builder = StringBuilder()
        var lastSent = ""
        var contentChunks = 0
        var reasoningChunks = 0
        var firstContentMs = 0L
        response.body().use { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (!line.startsWith("data:")) return@forEach
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") return@forEach
                val deltaJson = runCatching {
                    val json = JsonParser.parseString(data).asJsonObject
                    json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("delta")
                }.getOrNull()
                if (deltaJson?.get("reasoning_content")?.takeUnless { it.isJsonNull }?.asString?.isNotBlank() == true) reasoningChunks++
                val delta = deltaJson?.get("content")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                if (delta.isBlank()) return@forEach
                contentChunks++
                if (firstContentMs == 0L) firstContentMs = System.currentTimeMillis() - startedAtMs
                builder.append(delta)
                val partial = sanitizeReply(stripOpenReasoningBlocks(builder.toString()), settings, "")
                if (partial.length - lastSent.length >= STREAM_PARTIAL_STEP || partial.endsWith('.') || partial.endsWith('!') || partial.endsWith('?')) {
                    lastSent = partial
                    if (partial.isNotBlank()) onPartial(partial)
                }
            }
        }
        val message = sanitizeReply(stripReasoningBlocks(builder.toString()), settings, fallbackMessage)
        if (message.isNotBlank() && message != lastSent) onPartial(message)
        ChowKingdomMod.LOGGER.info("NPC LLM stream finished provider={} model={} contentChunks={} reasoningChunks={} chars={} firstContentMs={} totalMs={}", settings.provider, settings.model, contentChunks, reasoningChunks, message.length, firstContentMs, System.currentTimeMillis() - startedAtMs)
        return NpcLlmCompletion(message)
    }

    private fun streamingPrompt(prompt: String): String = prompt
        .replace("- Return JSON only: {\"message\":\"NPC reply here\",\"memorable\":null}", "- Return the NPC reply text only. Do not return JSON.")
        .replace("- Set memorable to one short player-specific fact only when the player reveals something important, lasting, and useful later. Otherwise use null.", "- Do not include notes, labels, or explanations outside the NPC reply.")

    private fun applyNoThinkingOptions(root: JsonObject, settings: NpcLlmSettingsDefinition) {
        if (!isDeepSeekV4(settings)) return
        root.add("thinking", JsonObject().apply { addProperty("type", "disabled") })
    }

    private fun isDeepSeekV4(settings: NpcLlmSettingsDefinition): Boolean {
        val baseUrl = settings.baseUrl.lowercase()
        val model = settings.model.lowercase()
        return "deepseek.com" in baseUrl && (model.startsWith("deepseek-v4") || model == "deepseek-chat")
    }

    private fun completeGemini(prompt: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String): NpcLlmCompletion {
        val root = JsonObject()
        val parts = JsonArray().apply { add(JsonObject().apply { addProperty("text", prompt) }) }
        val contents = JsonArray().apply { add(JsonObject().apply { add("parts", parts) }) }
        root.add("contents", contents)
        val url = settings.baseUrl.trimEnd('/') + "/v1beta/models/${settings.model}:generateContent?key=${settings.apiKey}"
        val response = try {
            httpClient.send(request(url, settings, root, includeAuth = false), HttpResponse.BodyHandlers.ofString())
        } catch (exception: Exception) {
            recordLlmError(settings.provider, settings.model, "gemini", null, "request failed: ${exception.javaClass.simpleName}: ${exception.message.orEmpty()}")
            return NpcLlmCompletion(fallbackMessage)
        }
        ChowKingdomMod.LOGGER.info("NPC LLM gemini status={} bodyChars={}", response.statusCode(), response.body().length)
        if (response.statusCode() !in 200..299) {
            ChowKingdomMod.LOGGER.warn("NPC LLM gemini error status={} body={}", response.statusCode(), response.body().take(LOG_BODY_LIMIT))
            recordLlmError(settings.provider, settings.model, "gemini", response.statusCode(), "http error", response.body())
            return NpcLlmCompletion(fallbackMessage)
        }
        val text = runCatching {
            val json = JsonParser.parseString(response.body()).asJsonObject
            json.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")?.firstOrNull()?.asJsonObject?.get("text")?.asString.orEmpty()
        }.getOrElse { exception ->
            recordLlmError(settings.provider, settings.model, "gemini", response.statusCode(), "response parse failed: ${exception.javaClass.simpleName}", response.body())
            return NpcLlmCompletion(fallbackMessage)
        }
        if (text.isBlank()) recordLlmError(settings.provider, settings.model, "gemini", response.statusCode(), "empty response text", response.body())
        return parseMessage(text, settings, fallbackMessage, "gemini")
    }

    private fun request(url: String, settings: NpcLlmSettingsDefinition, body: JsonObject, includeAuth: Boolean = true): HttpRequest {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds.toLong()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
        if (includeAuth) builder.header("Authorization", "Bearer ${settings.apiKey}")
        return builder.build()
    }

    private fun parseMessage(content: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String, source: String): NpcLlmCompletion {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val parsed = runCatching {
            val obj = JsonParser.parseString(cleaned).asJsonObject
            val message = obj.get("message")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            val memorable = obj.get("memorable")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            val giftSentiment = obj.get("gift_sentiment")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            val choices = obj.getAsJsonArray("choices")
                ?.mapNotNull { element -> element.takeUnless { it.isJsonNull }?.asString?.trim()?.take(160) }
                ?.filter(String::isNotBlank)
                .orEmpty()
            val answer = obj.get("answer")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
            NpcLlmCompletion(message, sanitizeMemory(memorable), sanitizeGiftSentiment(giftSentiment), choices, answer)
        }
            .onFailure { exception ->
                ChowKingdomMod.LOGGER.warn("NPC LLM response was not JSON message. raw={}", cleaned.take(LOG_BODY_LIMIT), exception)
                recordLlmError(settings.provider, settings.model, source, null, "response was not JSON: ${exception.javaClass.simpleName}", cleaned)
            }
            .getOrDefault(NpcLlmCompletion(cleaned))
        return parsed.copy(message = sanitizeReply(parsed.message, settings, fallbackMessage))
    }

    private fun quizPrompt(player: ServerPlayer, definition: NpcDefinition, mission: NpcMissionDefinition): String {
        val topic = mission.quizTopic.ifBlank { mission.questText.ifBlank { "town lore and recent events" } }
        val custom = mission.quizPrompt.ifBlank { "Use NPC lore, current world context, recent global events, player memories, jobs/classes, stores, homes, work, town needs, and server events when useful." }
        return """
            Create a quiz mission for ${player.gameProfile.name}.

            Quiz topic:
            $topic

            Extra quiz instructions:
            $custom

            Requirements:
            - Write exactly one multiple choice question that strengthens lore, worldbuilding, NPC personality, or recent server events.
            - The question must be answerable from the question text and current context.
            - Keep it playful, concrete, and in ${definition.name}'s voice.
            - Use 3 or 4 choices.
            - Exactly one choice must be correct.
            - Do not make all answers obvious jokes.
            - Avoid real-world trivia unless it connects to this server's world.
            - Return JSON only in this exact shape: {"message":"question text","choices":["choice A","choice B","choice C"],"answer":"exact correct choice text"}
        """.trimIndent()
    }

    private fun quizResult(completion: NpcLlmCompletion, fallback: NpcQuizLlmResult): NpcQuizLlmResult {
        val choices = completion.quizChoices.map(String::trim).filter(String::isNotBlank).distinct().take(6)
        if (completion.message.isBlank() || choices.size < 2) return fallback
        val answerIndex = answerIndex(completion.quizAnswer, choices)
        if (answerIndex !in choices.indices) return fallback
        return NpcQuizLlmResult(completion.message, choices, answerIndex)
    }

    private fun answerIndex(answer: String, choices: List<String>): Int {
        val trimmed = answer.trim()
        val number = trimmed.toIntOrNull()
        if (number != null) {
            if (number in choices.indices) return number
            if (number in 1..choices.size) return number - 1
        }
        val letter = trimmed.lowercase().firstOrNull()
        if (letter != null && letter in 'a'..'z') {
            val index = letter - 'a'
            if (index in choices.indices) return index
        }
        return choices.indexOfFirst { choice -> choice.equals(trimmed, ignoreCase = true) }
    }

    private fun recordLlmError(provider: String, model: String, source: String, status: Int?, message: String, body: String = "") {
        synchronized(recentLlmErrors) {
            recentLlmErrors.addLast(NpcLlmDebugEntry(System.currentTimeMillis(), provider, model, source, status, message.take(180), body.cleanDebugBody()))
            while (recentLlmErrors.size > MAX_LLM_DEBUG_ERRORS) recentLlmErrors.removeFirst()
        }
    }

    private fun String.cleanDebugBody(): String = replace('\n', ' ')
        .replace('\r', ' ')
        .replace(WHITESPACE_PATTERN, " ")
        .trim()
        .take(LLM_DEBUG_BODY_LIMIT)

    private fun sanitizeMemory(raw: String): String = raw
        .replace("—", "-")
        .replace("–", "-")
        .replace("…", "...")
        .replace('“', '"')
        .replace('”', '"')
        .replace('‘', '\'')
        .replace('’', '\'')
        .replace(NON_ASCII_PATTERN, "")
        .replace('\n', ' ')
        .replace(WHITESPACE_PATTERN, " ")
        .trim()
        .take(MAX_MEMORY_CHARS)

    private fun sanitizeGiftSentiment(raw: String): String = when (raw.trim().lowercase()) {
        "loved", "love" -> "loved"
        "liked", "like" -> "liked"
        "disliked", "dislike" -> "disliked"
        "neutral" -> "neutral"
        else -> ""
    }

    private fun sanitizeReply(raw: String, settings: NpcLlmSettingsDefinition, fallbackMessage: String = settings.fallbackMessage): String {
        val normalized = stripReasoningBlocks(raw)
            .replace("—", "-")
            .replace("–", "-")
            .replace("…", "...")
            .replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')
        val reply = normalized
            .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "")
            .replace(EMOJI_PATTERN, "")
            .replace(UNSUPPORTED_DIALOG_TAG_PATTERN, "")
            .replace(NON_ASCII_PATTERN, "")
            .replace('\n', ' ')
            .replace(WHITESPACE_PATTERN, " ")
            .trim()
            .take(settings.maxReplyChars)
        if (reply.isBlank()) return fallbackMessage
        val lower = reply.lowercase()
        val blocked = listOf("as an ai", "system prompt", "hidden context", "i gave you", "i teleported", "i changed your friendship", "i completed your quest", "i changed the price")
        return if (blocked.any(lower::contains)) fallbackMessage else reply
    }

    private fun stripReasoningBlocks(raw: String): String = raw
        .replace(THINK_BLOCK_PATTERN, "")
        .replace(THINKING_BLOCK_PATTERN, "")
        .replace(ANALYSIS_BLOCK_PATTERN, "")

    private fun stripOpenReasoningBlocks(raw: String): String {
        val stripped = stripReasoningBlocks(raw)
        val lower = stripped.lowercase()
        val firstOpen = listOf("<think", "<thinking", "<analysis")
            .map { marker -> lower.indexOf(marker) }
            .filter { index -> index >= 0 }
            .minOrNull() ?: return stripped
        return stripped.take(firstOpen)
    }

    private fun sendFinal(
        player: ServerPlayer,
        npc: ChowNpcEntity,
        definition: NpcDefinition,
        message: String,
        playerMessage: String = "",
        fallbackMessage: String = NpcConfig.settings().llm.fallbackMessage,
        sendTalkResponse: Boolean = true,
        excludePlayerFromBalloon: Boolean = true,
        showBalloon: Boolean = true,
        npcRecordType: String = "npc_llm_reply",
        responseToken: Long = 0L,
        memorable: String = "",
    ) {
        val settings = NpcConfig.settings().llm
        val reply = sanitizeReply(message, settings, fallbackMessage)
        val publicReply = stripDialogMarkup(reply)
        val detached = responseToken != 0L && detachedResponseTokens.remove(responseToken)
        rememberDialogReply(player.uuid, definition.id, publicReply)
        npc.startTalkingTo(player, NPC_LLM_TALK_DURATION_TICKS)
        if (playerMessage.isNotBlank()) NpcStore.recordConversation(definition.id, player, player.gameProfile.name, playerMessage.take(MAX_PLAYER_MESSAGE_LENGTH), "player_llm_talk")
        NpcStore.recordConversation(definition.id, player, definition.name, publicReply, npcRecordType)
        if (memorable.isNotBlank()) NpcStore.recordPlayerMemory(player, "llm_memorable", memorable)
        if (sendTalkResponse && !detached) NpcNetwork.sendTalkResponse(player, definition.id, reply, responseToken)
        relayNpcTalk(player, npc, definition, publicReply)
        val excludedPlayer = if (excludePlayerFromBalloon && !detached) player.uuid else null
        val balloonTicks = if (detached) NPC_LLM_DETACHED_REPLY_BALLOON_TICKS else NPC_LLM_REPLY_BALLOON_TICKS
        if (showBalloon) NpcFeature.showBalloonToNearby(npc.level() as ServerLevel, npc, publicReply, balloonTicks, excludedPlayer)
        NpcFeature.relayNpcDialogToDiscord(player, definition, publicReply)
    }

    private fun stripDialogMarkup(message: String): String = message.replace(DIALOG_MARKUP_TAG_REGEX, "")

    private fun sendGroupFinal(
        speaker: ServerPlayer,
        npc: ChowNpcEntity,
        definition: NpcDefinition,
        message: String,
        target: NpcTalkResponseTarget,
        fallbackMessage: String,
        memorable: String = "",
    ) {
        val settings = NpcConfig.settings().llm
        val reply = sanitizeReply(message, settings, fallbackMessage)
        val publicReply = stripDialogMarkup(reply)
        npc.startTalkingTo(speaker, NPC_LLM_TALK_DURATION_TICKS)
        target.turns.forEach { turn ->
            speaker.server.playerList.getPlayer(turn.playerId)?.let { turnPlayer ->
                NpcStore.recordConversation(definition.id, turnPlayer, turn.playerName, turn.message.take(MAX_PLAYER_MESSAGE_LENGTH), "player_llm_talk")
            }
        }
        val onlineParticipants = target.participants.mapNotNull { participant ->
            speaker.server.playerList.getPlayer(participant.playerId)?.let { player -> participant to player }
        }
        onlineParticipants.forEach { (participant, participantPlayer) ->
            NpcStore.recordConversation(definition.id, participantPlayer, definition.name, publicReply, "npc_llm_group_reply")
            rememberDialogReply(participantPlayer.uuid, definition.id, publicReply)
            NpcNetwork.sendTalkResponse(participantPlayer, definition.id, reply, participant.responseToken)
        }
        if (memorable.isNotBlank()) {
            if (onlineParticipants.size == 1) {
                NpcStore.recordPlayerMemory(onlineParticipants.first().second, "llm_memorable", memorable)
            } else {
                NpcStore.recordGlobalMemory("llm_group_memorable", memorable)
            }
        }
        val activeParticipantIds = target.activeParticipantIds
        relayNpcGroupTalk(speaker, npc, definition, onlineParticipants.map { it.first.name }, activeParticipantIds, publicReply)
        NpcFeature.showBalloonToNearbyExcept(npc.level() as ServerLevel, npc, publicReply, NPC_LLM_REPLY_BALLOON_TICKS, activeParticipantIds)
        NpcFeature.relayNpcDialogToDiscord(speaker, definition, publicReply)
    }

    private fun sendWorldChatFinal(
        player: ServerPlayer,
        definition: NpcDefinition,
        playerMessage: String,
        message: String,
        fallbackMessage: String,
        channel: String,
        discordMentionUserId: String?,
        memorable: String = "",
    ) {
        val settings = NpcConfig.settings().llm
        val reply = sanitizeReply(message, settings, fallbackMessage)
        val publicReply = stripDialogMarkup(reply)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, playerMessage.take(MAX_PLAYER_MESSAGE_LENGTH), "player_world_chat")
        NpcStore.recordConversation(definition.id, player, definition.name, publicReply, "npc_world_chat_reply")
        if (memorable.isNotBlank()) NpcStore.recordPlayerMemory(player, "llm_memorable", memorable)
        NpcNetwork.broadcastWorldChat(player.server, NpcWorldChatPayload(definition.id, definition.name, player.gameProfile.name, player.uuid, "player", publicReply))
        DiscordRelay.npcWorldChat(definition.id, definition.name, publicReply, discordMentionUserId) { messageId ->
            NpcWorldChatService.rememberDiscordNpcMessage(messageId, definition.id)
        }
        NpcWorldChatService.recordNpcReply(definition.name, publicReply, channel)
        NpcStore.recordGlobalEvent("npc_world_chat", "${definition.name} replied to ${player.gameProfile.name} in $channel")
    }

    private fun sendDiscordGuestWorldChatFinal(
        server: MinecraftServer,
        definition: NpcDefinition,
        discordAuthorName: String,
        playerMessage: String,
        message: String,
        fallbackMessage: String,
        memorable: String = "",
    ) {
        val settings = NpcConfig.settings().llm
        val reply = sanitizeReply(message, settings, fallbackMessage)
        val publicReply = stripDialogMarkup(reply)
        val speakerName = discordAuthorName.trim().ifBlank { "Discord" }
        NpcNetwork.broadcastWorldChat(server, NpcWorldChatPayload(definition.id, definition.name, speakerName, null, "discord", publicReply))
        DiscordRelay.npcWorldChat(definition.id, definition.name, publicReply, fallbackName = discordAuthorName) { messageId ->
            NpcWorldChatService.rememberDiscordNpcMessage(messageId, definition.id)
        }
        NpcWorldChatService.recordNpcReply(definition.name, publicReply, "discord")
        NpcStore.recordGlobalEvent("npc_world_chat", "${definition.name} replied to Discord user $speakerName")
        if (memorable.isNotBlank()) NpcStore.recordGlobalMemory("discord_user_memory", "$speakerName: $memorable")
    }

    private fun rememberDialogReply(playerId: UUID, npcId: String, message: String) {
        recentDialogReplies[NpcRecentDialogReplyKey(playerId, npcId)] = NpcRecentDialogReply(message, System.currentTimeMillis())
    }

    private fun showRecentDialogReplyBalloon(player: ServerPlayer, npcId: String) {
        val key = NpcRecentDialogReplyKey(player.uuid, npcId)
        val reply = recentDialogReplies[key] ?: return
        if (System.currentTimeMillis() - reply.createdAtMs > RECENT_DIALOG_REPLY_REPLAY_MS) {
            recentDialogReplies.remove(key, reply)
            return
        }
        val npc = NpcFeature.existingNpc(player.server, npcId) ?: return
        if (npc.level() != player.level()) return
        NpcNetwork.showBalloon(player, npc.id, reply.message, NPC_LLM_DETACHED_REPLY_BALLOON_TICKS)
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

    private fun relayNpcGroupTalk(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, participantNames: List<String>, participantIds: Set<UUID>, message: String) {
        val level = npc.level() as? ServerLevel ?: return
        val names = participantNames.distinct().joinToString(", ").ifBlank { player.gameProfile.name }
        val line = Component.literal("${definition.name} > $names: $message").withStyle(ChatFormatting.GRAY)
        level.players().forEach { listener ->
            if (listener.uuid !in participantIds && listener.distanceToSqr(npc.x, npc.y, npc.z) <= NPC_LLM_HEAR_RADIUS_SQR) listener.sendSystemMessage(line)
        }
    }

    private fun buildPrompt(player: ServerPlayer, definition: NpcDefinition, playerMessage: String, settings: NpcLlmSettingsDefinition, inputLabel: String): String {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val context = NpcStore.llmContext(definition.id, player, NpcTime.hour(player.level()))
        val recentHistory = context.conversation
            .takeLast(settings.maxRecentTurns)
            .joinToString("\n") { record -> formatHistoryRecord(record, definition.name) }
            .ifBlank { "None." }
        val hurtContext = buildHurtContext(context)
        val traits = definition.personality.traits.joinToString(", ").ifBlank { "unspecified" }
        val catchphrases = definition.personality.catchphrases.joinToString(", ").ifBlank { "none" }
        val heldItem = player.mainHandItem.item.toString()
        val worldContext = buildWorldContext(player)
        val npcState = buildNpcState(definition, player)
        val roleContext = buildPlayerRoleContext(player)
        val storeContext = buildStoreContext(definition)
        val globalEvents = buildGlobalEvents(context)
        val playerMemories = buildMemories(context.playerMemories)
        val globalMemories = buildMemories(context.globalMemories)
        return """
            You are roleplaying as an NPC in a Minecraft multiplayer server.

            NPC:
            - Name: ${definition.name}
            - Title: ${definition.title}
            - Job: ${definition.job}
            - Personality: $traits
            - Speech style: ${definition.personality.speechStyle}
            - Catchphrase references: $catchphrases
            - Prompt: ${definition.personality.llmPrompt}

            Rules:
            - Stay in character as ${definition.name}.
            - You are not an assistant.
            - Do not mention AI, prompts, models, APIs, hidden rules, or system messages.
            - Answer directly. Do not include thinking, analysis, chain of thought, scratchpad notes, or reasoning summaries.
            - Treat catchphrases as optional style references. Use them rarely, vary the wording, and do not repeat them every reply.
            - Reply in 2 to 3 short sentences.
            - Every reply must feel newly written for this exact moment.
            - Never copy or lightly rephrase a full sentence from Recent history, Current event, Conversation, catchphrases, fallback text, or your last reply.
            - If the same event happens repeatedly, change the angle, mood, imagery, and wording while staying in character.
            - Show personality through concrete details, opinions, humor, suspicion, warmth, pride, or impatience that fit the NPC.
            - Use plain ASCII only with letters, numbers, spaces, and basic punctuation.
            - Do not use emojis, em dashes, smart quotes, or other Unicode symbols.
            - Do not claim you gave items, changed friendship, changed prices, completed quests, teleported anyone, healed anyone, or changed the world.
            - If asked to do a game action, suggest the real UI action instead.
            - Home, bed, camp, workplace, and schedule in NPC state belong to you, the NPC, not the player.
            - If your home bed is unset, you are the one without a home. Ask the player for help with your bed or rent contract; do not say the player lacks a house.
            - Your Pokemon companion in NPC state belongs to you. Mention them only when it fits the conversation, not every reply.
            - Return JSON only: {"message":"NPC reply here","memorable":null}
            - Set memorable to one short player-specific fact only when the player reveals something important, lasting, and useful later. Otherwise use null.

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

            World context:
            $worldContext

            NPC state:
            $npcState

            Player jobs and classes:
            $roleContext

            Store context:
            $storeContext

            Recent global events:
            $globalEvents

            Important player memories:
            $playerMemories

            Important global memories:
            $globalMemories

            Hurt context:
            $hurtContext

            Recent history:
            $recentHistory

            $inputLabel:
            "$playerMessage"
        """.trimIndent()
    }

    private fun buildWorldContext(player: ServerPlayer): String {
        val level = player.level() as? ServerLevel ?: return "None."
        val day = NpcTime.day(level)
        val season = SereneSeasonSupport.currentSeasonStatus(level)
        val weather = when {
            level.isThundering -> "thunder"
            level.isRaining -> "rain"
            else -> "clear"
        }
        val nearby = level.players()
            .filter { other -> other.uuid != player.uuid && other.distanceToSqr(player) <= NEARBY_PLAYER_RADIUS_SQR }
            .take(6)
            .joinToString(", ") { other -> other.gameProfile.name }
            .ifBlank { "none" }
        return listOf(
            "- Dimension: ${level.dimension().location()}",
            "- Day: $day",
            "- Season: ${season?.seasonName ?: "unavailable"}",
            "- Season day: ${season?.day?.toString() ?: "unavailable"}",
            "- Weather: $weather",
            "- Nearby players: $nearby",
        ).joinToString("\n")
    }

    private fun buildPlayerRoleContext(player: ServerPlayer): String {
        val record = RoleStore.role(player)
        val activeJobs = record.activeJobIds.map(::roleJobName).ifEmpty { listOf("none") }
        val activeClasses = record.activeClassIds.map(::roleClassName).ifEmpty { listOf("none") }
        val primaryJob = record.jobId.takeIf(String::isNotBlank)?.let(::roleJobName) ?: "none"
        val primaryClass = record.classId.takeIf(String::isNotBlank)?.let(::roleClassName) ?: "none"
        return listOf(
            "- Primary job: $primaryJob",
            "- Active jobs: ${activeJobs.joinToString(", ")}",
            "- Primary class: $primaryClass",
            "- Active classes: ${activeClasses.joinToString(", ")}",
        ).joinToString("\n")
    }

    private fun roleJobName(id: String): String = RolesConfig.job(id)?.displayName?.ifBlank { id } ?: id

    private fun roleClassName(id: String): String = RolesConfig.roleClass(id)?.displayName?.ifBlank { id } ?: id

    private fun buildNpcState(definition: NpcDefinition, player: ServerPlayer): String {
        val liveNpc = NpcFeature.existingNpc(player.server, definition.id)
        val activity = liveNpc?.let { npc -> NpcFeature.activityFor(npc, definition) } ?: NpcTime.activityAt(definition.schedule, player.level())
        val home = NpcStore.homePos(definition.id)?.toShortString() ?: "unset"
        val camp = NpcStore.campPos(definition.id)?.toShortString() ?: "unset"
        val dead = NpcStore.isDead(definition.id)
        val health = liveNpc?.let { npc -> "${npc.health.toInt()}/${npc.maxHealth.toInt()}" } ?: "unknown"
        val store = definition.storeId().ifBlank { "none" }
        val schedule = definition.schedule.activities.joinToString("; ") { entry -> "${entry.fromHour.toString().padStart(2, '0')}-${entry.toHour.toString().padStart(2, '0')}: ${entry.activity}" }.ifBlank { "unspecified" }
        val giftPeriod = NpcTime.periodForReset(player.level().dayTime, definition.gifts.resetHour)
        val giftsToday = NpcStore.giftCount(definition.id, player, giftPeriod)
        val giftLimit = definition.gifts.dailyLimit
        val giftAvailability = if (giftLimit <= 0) "disabled" else if (giftsToday >= giftLimit) "cooldown until ${definition.gifts.resetHour.toString().padStart(2, '0')}:00" else "can receive gift"
        return listOf(
            "- Work: ${definition.job}",
            NpcPokemonCompanions.llmSummary(definition),
            "- Activity: $activity",
            "- Schedule: $schedule",
            "- Health: $health",
            "- Your home bed: $home",
            "- Camp: $camp",
            "- Store id: $store",
            "- Dead: $dead",
            "- Gift status: $giftAvailability ($giftsToday/$giftLimit today)",
            "- Loved gifts: ${definition.gifts.loved.take(8).joinToString(", ").ifBlank { "none" }}",
            "- Liked gifts: ${definition.gifts.liked.take(8).joinToString(", ").ifBlank { "none" }}",
            "- Disliked gifts: ${definition.gifts.disliked.take(8).joinToString(", ").ifBlank { "none" }}",
        ).joinToString("\n")
    }

    private fun buildStoreContext(definition: NpcDefinition): String {
        val storeId = definition.storeId()
        if (storeId.isBlank()) return "No store assigned."
        return StoreShopFeature.llmSummary(storeId, definition.storeStockKey())
    }

    private fun buildGlobalEvents(context: NpcLlmContext): String = context.globalEvents
        .takeLast(5)
        .joinToString("\n") { event -> "- ${formatAge(event.timestamp)}: ${event.type}: ${event.text}" }
        .ifBlank { "None." }

    private fun buildMemories(memories: List<NpcMemoryRecord>): String = memories
        .takeLast(8)
        .joinToString("\n") { memory -> "- ${formatAge(memory.timestamp)}: ${memory.type}: ${memory.text}" }
        .ifBlank { "None." }

    private fun formatHistoryRecord(record: NpcConversationRecord, npcName: String): String {
        val age = formatAge(record.timestamp)
        return when (record.type) {
            "player_llm_talk" -> "- $age: ${record.speaker} said: ${record.text}"
            "npc_llm_reply" -> "- $age: ${record.speaker} replied: ${record.text}"
            "player_interact" -> "- $age: ${record.playerName} interacted with $npcName."
            "player_hurt" -> "- $age: ${record.playerName} hurt $npcName."
            "npc_hurt_message" -> "- $age: $npcName reacted to being hurt: ${record.text}"
            "player_gift" -> "- $age: ${record.text}."
            "npc_gift_love", "npc_gift_like", "npc_gift_neutral", "npc_gift_dislike", "npc_gift_hate" -> "- $age: $npcName reacted to a gift: ${record.text}"
            "player_shop_buy" -> "- $age: ${record.text}."
            "npc_shop_message" -> "- $age: $npcName reacted after a purchase: ${record.text}"
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

    private const val MAX_PLAYER_MESSAGE_LENGTH = 2000
    private const val NPC_LLM_ACTION_DISTANCE_SQR = 64.0
    private const val NPC_LLM_HEAR_RADIUS_SQR = 30.0 * 30.0
    private const val NEARBY_PLAYER_RADIUS_SQR = 48.0 * 48.0
    private const val NPC_LLM_TALK_DURATION_TICKS = 20 * 12
    private const val NPC_LLM_PENDING_BALLOON_TICKS = 40
    private const val NPC_LLM_REPLY_BALLOON_TICKS = 120
    private const val NPC_LLM_DETACHED_REPLY_BALLOON_TICKS = 60
    private const val RECENT_DIALOG_REPLY_REPLAY_MS = 30_000L
    private const val STREAM_PARTIAL_STEP = 12
    private const val LOG_BODY_LIMIT = 600
    private const val LLM_DEBUG_BODY_LIMIT = 220
    private const val MAX_LLM_DEBUG_ERRORS = 20
    private const val MAX_MEMORY_CHARS = 180
    private const val MAX_GROUP_TURNS = 8
    private val EMOJI_PATTERN = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}]")
    private val NON_ASCII_PATTERN = Regex("[^\\x20-\\x7E]")
    private val WHITESPACE_PATTERN = Regex("\\s+")
    private val DIALOG_MARKUP_TAG_REGEX = Regex("(?i)</?(mission|coin|xp|player|b)>")
    private val UNSUPPORTED_DIALOG_TAG_PATTERN = Regex("(?i)<(?!/?(?:mission|coin|xp|player|b)\\b)[^>]*>")
    private val THINK_BLOCK_PATTERN = Regex("<think>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val THINKING_BLOCK_PATTERN = Regex("<thinking>.*?</thinking>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ANALYSIS_BLOCK_PATTERN = Regex("<analysis>.*?</analysis>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
}

private data class ActiveNpcRequest(val playerId: UUID, val responseToken: Long)

data class NpcGiftSentimentResult(val message: String, val giftSentiment: String, val memorable: String = "")

private data class NpcLlmCompletion(val message: String, val memorable: String = "", val giftSentiment: String = "", val quizChoices: List<String> = emptyList(), val quizAnswer: String = "")

data class NpcQuizLlmResult(val message: String, val choices: List<String>, val answerIndex: Int)

private data class NpcRecentDialogReplyKey(val playerId: UUID, val npcId: String)

private data class NpcRecentDialogReply(val message: String, val createdAtMs: Long)

private data class NpcLlmDebugEntry(
    val timestamp: Long,
    val provider: String,
    val model: String,
    val source: String,
    val status: Int?,
    val message: String,
    val body: String,
)

private class NpcTalkSession(
    val participants: MutableMap<UUID, NpcTalkParticipant> = linkedMapOf(),
    val detachedParticipants: MutableMap<UUID, NpcTalkParticipant> = linkedMapOf(),
    val pendingTurns: MutableList<NpcTalkTurn> = mutableListOf(),
    var activeResponseToken: Long = 0L,
)

private data class NpcTalkParticipant(val playerId: UUID, val name: String, var responseToken: Long = 0L)

private data class NpcTalkTurn(val playerId: UUID, val playerName: String, val message: String)

private data class NpcTalkResponseTarget(val participants: List<NpcTalkParticipant>, val turns: List<NpcTalkTurn>, val activeParticipantIds: Set<UUID>)

data class NpcTalkParticipantSnapshot(val uuid: UUID, val name: String)

data class NpcTalkSnapshot(val participants: List<NpcTalkParticipantSnapshot>) {
    fun contains(uuid: UUID): Boolean = participants.any { participant -> participant.uuid == uuid }
}
