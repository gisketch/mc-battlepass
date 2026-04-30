package dev.gisketch.chowkingdom.discord

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.profiles.NicknameStore
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object DiscordInboundBridge {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "chowkingdom-discord-gateway").apply { isDaemon = true } }
    private var nextPollMillis = 0L
    private var lastSeenMessageId: String? = null
    private var inFlight = false
    private var gateway: WebSocket? = null
    private var gatewayConnecting = false
    private var heartbeatTask: ScheduledFuture<*>? = null
    private var sequence: Int? = null
    private var lastStatus = "idle"
    private var lastError = ""
    private var lastMessage = ""
    private var lastChannelCheck = "not checked"
    private var lastPresence = "none"
    private var nextPresenceMillis = 0L

    fun diagnostics(config: DiscordInboundConfig): List<String> = listOf(
        "Discord inbound bridge",
        "enabled=${config.enabled}",
        "mode=${config.mode}",
        "bot_token_set=${config.botToken.isNotBlank()}",
        "channel_id=${config.channelId.ifBlank { "missing" }}",
        "gateway_connected=${gateway != null}",
        "gateway_connecting=$gatewayConnecting",
        "in_flight_poll=$inFlight",
        "last_status=$lastStatus",
        "last_error=${lastError.ifBlank { "none" }}",
        "last_channel_check=$lastChannelCheck",
        "last_message=${lastMessage.ifBlank { "none" }}",
        "presence_enabled=${config.botPresenceEnabled}",
        "last_presence=$lastPresence",
    )

    fun checkChannel(config: DiscordInboundConfig) {
        if (config.botToken.isBlank() || config.channelId.isBlank()) {
            lastChannelCheck = "missing bot token or channel id"
            return
        }
        val request = runCatching {
            HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/channels/${config.channelId}"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bot ${config.botToken}")
                .header("User-Agent", "ChowKingdomDiscordBridge")
                .GET()
                .build()
        }.getOrElse { exception ->
            lastChannelCheck = "invalid request: ${exception.message.orEmpty()}"
            return
        }

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                lastChannelCheck = if (response.statusCode() in 200..299) {
                    val channel = JsonParser.parseString(response.body()).asJsonObject
                    "ok #${channel.get("name")?.asString ?: config.channelId}"
                } else if (response.statusCode() == 403) {
                    "http 403 forbidden; bot lacks channel access"
                } else if (response.statusCode() == 404) {
                    "http 404 not found; channel id wrong or bot not in server"
                } else {
                    "http ${response.statusCode()}"
                }
            }
            .exceptionally { exception ->
                lastChannelCheck = "failed: ${exception.message.orEmpty()}"
                null
            }
    }

    fun reset() {
        nextPollMillis = 0L
        lastSeenMessageId = null
        inFlight = false
        gatewayConnecting = false
        sequence = null
        lastStatus = "reset"
        lastError = ""
        lastMessage = ""
        lastPresence = "none"
        nextPresenceMillis = 0L
        heartbeatTask?.cancel(false)
        heartbeatTask = null
        gateway?.sendClose(WebSocket.NORMAL_CLOSURE, "reload")
        gateway = null
    }

    fun tick(server: MinecraftServer, tps: Double) {
        val config = DiscordConfig.current().discordToMinecraft
        if (config.botToken.isBlank()) return
        val inboundReady = config.enabled && config.channelId.isNotBlank()
        if (!inboundReady && !config.botPresenceEnabled) return
        if (config.mode == "polling") {
            if (inboundReady) poll(server, config)
            if (config.botPresenceEnabled) ensureGateway(server, config)
        } else {
            ensureGateway(server, config)
        }
        updatePresence(server, config, tps)
    }

    private fun ensureGateway(server: MinecraftServer, config: DiscordInboundConfig) {
        if (gateway != null || gatewayConnecting) return
        gatewayConnecting = true
        lastStatus = "connecting gateway"
        client.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI.create("wss://gateway.discord.gg/?v=10&encoding=json"), GatewayListener(server, config))
            .thenAccept { socket ->
                gateway = socket
                gatewayConnecting = false
            }
            .exceptionally { exception ->
                gatewayConnecting = false
                lastStatus = "gateway connect failed"
                lastError = exception.message.orEmpty()
                ChowKingdomMod.LOGGER.warn("Failed to connect Discord gateway", exception)
                null
            }
    }

    private fun identify(socket: WebSocket, config: DiscordInboundConfig) {
        val payload = JsonObject().apply {
            addProperty("op", 2)
            add("d", JsonObject().apply {
                addProperty("token", config.botToken)
                addProperty("intents", gatewayIntents(config))
                add("properties", JsonObject().apply {
                    addProperty("os", "java")
                    addProperty("browser", "chowkingdom")
                    addProperty("device", "chowkingdom")
                })
            })
        }
        socket.sendText(payload.toString(), true)
    }

    private fun startHeartbeat(socket: WebSocket, intervalMillis: Long) {
        heartbeatTask?.cancel(false)
        heartbeatTask = scheduler.scheduleAtFixedRate({
            val payload = JsonObject().apply {
                addProperty("op", 1)
                if (sequence == null) add("d", null) else addProperty("d", sequence)
            }
            socket.sendText(payload.toString(), true)
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
    }

    private fun handleGatewayPayload(server: MinecraftServer, config: DiscordInboundConfig, raw: String, socket: WebSocket) {
        val payload = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return
        payload.get("s")?.takeIf { !it.isJsonNull }?.asInt?.let { sequence = it }
        when (payload.get("op")?.asInt) {
            0 -> when (val eventType = payload.get("t")?.asString.orEmpty()) {
                "READY" -> lastStatus = "gateway ready"
                "MESSAGE_CREATE" -> handleGatewayMessage(server, config, payload.getAsJsonObject("d"))
                else -> if (eventType.isNotBlank()) lastStatus = "gateway dispatch $eventType"
            }
            7, 9 -> reset()
            10 -> {
                val interval = payload.getAsJsonObject("d").get("heartbeat_interval").asLong
                lastStatus = "gateway hello heartbeat=${interval}ms"
                startHeartbeat(socket, interval)
                identify(socket, config)
            }
            11 -> lastStatus = "gateway heartbeat ack"
        }
    }

    private fun handleGatewayMessage(server: MinecraftServer, config: DiscordInboundConfig, message: JsonObject) {
        if (config.mode == "polling" || !config.enabled || config.channelId.isBlank()) return
        val channelId = message.get("channel_id")?.asString.orEmpty()
        if (channelId != config.channelId) {
            lastStatus = "message ignored channel=$channelId expected=${config.channelId}"
            return
        }
        val inbound = message.toInboundMessage() ?: return
        if (handleLinkCommand(config, inbound)) return
        lastStatus = "gateway message received"
        lastMessage = "${inbound.author}: ${inbound.content.take(80)}"
        broadcast(server, config, inbound)
    }

    private fun poll(server: MinecraftServer, config: DiscordInboundConfig) {
        val now = System.currentTimeMillis()
        if (inFlight || now < nextPollMillis) return

        inFlight = true
        lastStatus = "polling"
        nextPollMillis = now + config.pollIntervalSeconds * 1000L
        val request = runCatching {
            HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/channels/${config.channelId}/messages?limit=10"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bot ${config.botToken}")
                .header("User-Agent", "ChowKingdomDiscordBridge")
                .GET()
                .build()
        }.getOrElse { exception ->
            inFlight = false
            lastStatus = "poll request invalid"
            lastError = exception.message.orEmpty()
            ChowKingdomMod.LOGGER.warn("Invalid Discord inbound config", exception)
            return
        }

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                inFlight = false
                if (response.statusCode() !in 200..299) {
                    lastStatus = "poll http ${response.statusCode()}"
                    ChowKingdomMod.LOGGER.warn("Discord inbound poll returned HTTP {}", response.statusCode())
                    return@thenAccept
                }
                lastStatus = "poll http 200"
                handlePolledMessages(server, config, JsonParser.parseString(response.body()).asJsonArray)
            }
            .exceptionally { exception ->
                inFlight = false
                lastStatus = "poll failed"
                lastError = exception.message.orEmpty()
                ChowKingdomMod.LOGGER.warn("Failed to poll Discord inbound chat", exception)
                null
            }
    }

    private fun handlePolledMessages(server: MinecraftServer, config: DiscordInboundConfig, messages: JsonArray) {
        val parsed = messages.mapNotNull { element -> element.asJsonObject.toInboundMessage() }
        if (parsed.isEmpty()) return

        val newestId = parsed.first().id
        val previousId = lastSeenMessageId
        if (previousId == null) {
            lastSeenMessageId = newestId
            return
        }

        val unseen = parsed.takeWhile { message -> isAfter(message.id, previousId) }.asReversed()
        lastSeenMessageId = newestId
        unseen.forEach { message ->
            if (handleLinkCommand(config, message)) return@forEach
            lastMessage = "${message.author}: ${message.content.take(80)}"
            broadcast(server, config, message)
        }
    }

    private fun broadcast(server: MinecraftServer, config: DiscordInboundConfig, message: InboundMessage) {
        server.execute {
            val link = DiscordAccountLinkStore.linkForDiscord(message.authorId)
            val linkedPlayer = link?.minecraftUuid
                ?.let { playerId -> runCatching { UUID.fromString(playerId) }.getOrNull() }
                ?.let(server.playerList::getPlayer)
            val author = link?.minecraftName ?: message.author
            server.playerList.broadcastSystemMessage(inboundMessageComponent(config, message, author, linkedPlayer), false)
        }
    }

    private fun inboundMessageComponent(config: DiscordInboundConfig, message: InboundMessage, author: String, linkedPlayer: ServerPlayer?): Component {
        val values = mapOf(
            "discord_author" to message.author,
            "discord_id" to message.authorId,
            "message" to DiscordText.cleanContent(message.content),
        )
        val output = Component.empty()
        var cursor = 0
        TEMPLATE_TOKEN.findAll(config.messageFormat).forEach { match ->
            if (match.range.first > cursor) output.append(Component.literal(config.messageFormat.substring(cursor, match.range.first)))
            when (match.groupValues[1]) {
                "author" -> output.append(authorComponent(author, linkedPlayer))
                "message" -> output.append(Component.literal(values[match.groupValues[1]].orEmpty()).withStyle { style -> style.withColor(CHAT_MESSAGE_COLOR) })
                else -> output.append(Component.literal(values[match.groupValues[1]].orEmpty()))
            }
            cursor = match.range.last + 1
        }
        if (cursor < config.messageFormat.length) output.append(Component.literal(config.messageFormat.substring(cursor)))
        return output
    }

    private fun authorComponent(author: String, linkedPlayer: ServerPlayer?): MutableComponent {
        val component = Component.literal(author).withStyle { style -> style.withBold(true) }
        if (linkedPlayer == null) return component
        val originalName = NicknameStore.originalName(linkedPlayer)
        return component.withStyle { style -> style.withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell $originalName ")) }
    }

    private fun handleLinkCommand(config: DiscordInboundConfig, message: InboundMessage): Boolean {
        val code = LINK_COMMAND.matchEntire(message.content.trim())?.groupValues?.getOrNull(1) ?: return false
        val result = DiscordAccountLinkStore.link(message.authorId, message.author, code)
        sendBotChannelMessage(config, linkResponse(result))
        lastStatus = when (result) {
            is DiscordLinkResult.Linked -> "linked discord ${message.authorId} to ${result.link.minecraftName}"
            DiscordLinkResult.InvalidCode -> "link code invalid"
            is DiscordLinkResult.DiscordAlreadyLinked -> "discord already linked"
            is DiscordLinkResult.MinecraftAlreadyLinked -> "minecraft already linked"
        }
        return true
    }

    private fun linkResponse(result: DiscordLinkResult): String = when (result) {
        is DiscordLinkResult.Linked -> "Linked ${result.link.discordName} to Minecraft account ${result.link.minecraftName}. Minecraft chat will now use ${result.link.minecraftName}."
        DiscordLinkResult.InvalidCode -> "Link code invalid or expired. Run /ck discord link in Minecraft, then send !link CODE here."
        is DiscordLinkResult.DiscordAlreadyLinked -> "This Discord account is already linked to Minecraft account ${result.minecraftName}. Unlink first to change it."
        is DiscordLinkResult.MinecraftAlreadyLinked -> "Minecraft account ${result.minecraftName} is already linked to another Discord account. Unlink first to change it."
    }

    private fun sendBotChannelMessage(config: DiscordInboundConfig, content: String) {
        if (config.botToken.isBlank() || config.channelId.isBlank()) return
        val body = JsonObject().apply {
            addProperty("content", DiscordText.cleanContent(content))
            add("allowed_mentions", JsonObject().apply { add("parse", JsonArray()) })
        }
        val request = runCatching {
            HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/channels/${config.channelId}/messages"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bot ${config.botToken}")
                .header("User-Agent", "ChowKingdomDiscordBridge")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()
        }.getOrElse { exception ->
            lastError = exception.message.orEmpty()
            return
        }
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenAccept { response -> if (response.statusCode() !in 200..299) lastError = "link response http ${response.statusCode()}" }
            .exceptionally { exception ->
                lastError = exception.message.orEmpty()
                null
            }
    }

    private fun updatePresence(server: MinecraftServer, config: DiscordInboundConfig, tps: Double) {
        val socket = gateway ?: return
        if (!config.botPresenceEnabled) return
        val now = System.currentTimeMillis()
        if (nextPresenceMillis != 0L && now < nextPresenceMillis) return

        val season = DiscordSeasonStatus.current(server)
        val values = mapOf(
            "online" to server.playerList.playerCount.toString(),
            "max" to server.maxPlayers.toString(),
            "tps" to formatTps(tps),
            "season" to season.orEmpty(),
            "season_status" to season?.let { " - $it" }.orEmpty(),
        )
        val text = DiscordText.applyTemplate(config.botPresenceFormat, values).take(DISCORD_ACTIVITY_TEXT_LIMIT)
        val activityType = activityType(config.botPresenceActivityType)
        val activity = JsonObject().apply {
            addProperty("type", activityType)
            if (activityType == DISCORD_ACTIVITY_CUSTOM) {
                addProperty("name", "Custom Status")
                addProperty("state", text)
            } else {
                addProperty("name", text)
            }
        }
        val payload = JsonObject().apply {
            addProperty("op", 3)
            add("d", JsonObject().apply {
                add("since", null)
                add("activities", JsonArray().apply { add(activity) })
                addProperty("status", config.botPresenceStatus)
                addProperty("afk", false)
            })
        }
        socket.sendText(payload.toString(), true)
        lastPresence = text
        nextPresenceMillis = now + config.botPresenceIntervalSeconds * 1000L
    }

    private fun gatewayIntents(config: DiscordInboundConfig): Int = if (config.mode != "polling" && config.enabled && config.channelId.isNotBlank()) {
        DISCORD_INTENT_GUILDS or DISCORD_INTENT_GUILD_MESSAGES or DISCORD_INTENT_MESSAGE_CONTENT
    } else {
        DISCORD_INTENT_GUILDS
    }

    private fun activityType(type: String): Int = when (type) {
        "playing" -> 0
        "listening" -> 2
        "watching" -> 3
        "competing" -> 5
        "custom" -> DISCORD_ACTIVITY_CUSTOM
        else -> 3
    }

    private fun JsonObject.toInboundMessage(): InboundMessage? {
        if (has("webhook_id")) {
            lastStatus = "message ignored webhook"
            return null
        }
        val author = getAsJsonObject("author") ?: run {
            lastStatus = "message ignored missing author"
            return null
        }
        if (author.get("bot")?.asBoolean == true) {
            lastStatus = "message ignored bot author"
            return null
        }
        val content = get("content")?.asString?.takeIf(String::isNotBlank) ?: run {
            lastStatus = "message ignored blank content; enable Message Content Intent"
            return null
        }
        val id = get("id")?.asString?.takeIf(String::isNotBlank) ?: run {
            lastStatus = "message ignored missing id"
            return null
        }
        val authorId = author.get("id")?.asString?.takeIf(String::isNotBlank) ?: run {
            lastStatus = "message ignored missing author id"
            return null
        }
        val authorName = author.get("global_name")?.takeIf { !it.isJsonNull }?.asString?.takeIf(String::isNotBlank)
            ?: author.get("username")?.asString?.takeIf(String::isNotBlank)
            ?: "Discord"
        return InboundMessage(id, authorId, authorName, content)
    }

    private fun isAfter(id: String, previousId: String): Boolean = runCatching {
        BigInteger(id) > BigInteger(previousId)
    }.getOrDefault(id != previousId)

    private fun formatTps(value: Double): String = String.format(Locale.ROOT, "%.2f", (value * 100.0).roundToInt() / 100.0)

    private class GatewayListener(private val server: MinecraftServer, private val config: DiscordInboundConfig) : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            lastStatus = "gateway open"
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                handleGatewayPayload(server, config, buffer.toString(), webSocket)
                buffer.clear()
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            gateway = null
            heartbeatTask?.cancel(false)
            heartbeatTask = null
            lastStatus = "gateway closed $statusCode"
            lastError = reason
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            gateway = null
            heartbeatTask?.cancel(false)
            heartbeatTask = null
            lastStatus = "gateway error"
            lastError = error.message.orEmpty()
            ChowKingdomMod.LOGGER.warn("Discord gateway error", error)
        }
    }

    private data class InboundMessage(val id: String, val authorId: String, val author: String, val content: String)

    private const val DISCORD_INTENT_GUILDS = 1
    private const val DISCORD_INTENT_GUILD_MESSAGES = 512
    private const val DISCORD_INTENT_MESSAGE_CONTENT = 32768
    private const val DISCORD_ACTIVITY_CUSTOM = 4
    private const val DISCORD_ACTIVITY_TEXT_LIMIT = 128
    private val LINK_COMMAND = Regex("(?i)!link\\s+([a-z0-9-]{4,32})")
    private val TEMPLATE_TOKEN = Regex("\\{(author|discord_author|discord_id|message)\\}")
    private const val CHAT_MESSAGE_COLOR = 0xD7D9E0
}

private object DiscordSeasonStatus {
    fun current(server: MinecraftServer): String? {
        if (!ModList.get().isLoaded("sereneseasons")) return null
        val level = server.overworld()
        val state = runCatching {
            val helper = Class.forName("sereneseasons.api.season.SeasonHelper")
            val method = helper.methods.firstOrNull { method ->
                method.name == "getSeasonState" && method.parameterCount == 1 && method.parameterTypes[0].isAssignableFrom(level.javaClass)
            } ?: return null
            method.invoke(null, level)
        }.getOrNull() ?: return null

        val season = invokeNoArg(state, "getSeason", "getSubSeason")?.let(::formatEnumName) ?: return null
        val day = invokeNoArg(state, "getDay", "getDayOfSeason", "getSeasonDay")?.toString()?.toIntOrNull()?.let { it + 1 }
        return if (day == null) season else "$season, Day $day"
    }

    private fun invokeNoArg(target: Any, vararg names: String): Any? = names.firstNotNullOfOrNull { name ->
        runCatching { target.javaClass.methods.firstOrNull { method -> method.name == name && method.parameterCount == 0 }?.invoke(target) }.getOrNull()
    }

    private fun formatEnumName(value: Any): String = value.toString()
        .lowercase(Locale.ROOT)
        .split('_')
        .joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }
}
