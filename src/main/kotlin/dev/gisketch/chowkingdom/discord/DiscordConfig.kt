package dev.gisketch.chowkingdom.discord

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChatGlyphs
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object DiscordConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var config = DiscordWebhookConfig()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("discord").resolve("webhook.json")

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            file.bufferedReader().use { reader -> gson.fromJson(reader, DiscordWebhookConfig::class.java) } ?: DiscordWebhookConfig()
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load Discord webhook config {}", file, exception)
            DiscordWebhookConfig()
        }.normalized()
    }

    fun current(): DiscordWebhookConfig = config

    private fun writeDefault() {
        Files.createTempFile(file.parent, "discord_webhook", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(DiscordWebhookConfig(), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

class DiscordWebhookConfig(
    var enabled: Boolean = false,
    @SerializedName("webhook_url") var webhookUrl: String = "",
    @SerializedName("webhook_username") var webhookUsername: String = "Chow Kingdom",
    @SerializedName("avatar_url") var avatarUrl: String = "",
    @SerializedName("player_chat_identity") var playerChatIdentity: Boolean = true,
    @SerializedName("debug_avatar_resolution") var debugAvatarResolution: Boolean = true,
    @SerializedName("player_avatar_urls") var playerAvatarUrls: MutableMap<String, String> = linkedMapOf(),
    @SerializedName("minecraft_avatar_url_template") var minecraftAvatarUrlTemplate: String = "https://mc-heads.net/avatar/{uuid}/128",
    @SerializedName("quick_skin_avatar_url_template") var quickSkinAvatarUrlTemplate: String = "",
    @SerializedName("quick_skin_avatar_server") var quickSkinAvatarServer: DiscordQuickSkinAvatarServerConfig = DiscordQuickSkinAvatarServerConfig(),
    @SerializedName("relay_chat") var relayChat: Boolean = true,
    @SerializedName("relay_join_leave") var relayJoinLeave: Boolean = false,
    @SerializedName("relay_deaths") var relayDeaths: Boolean = true,
    @SerializedName("relay_battlepass_completions") var relayBattlepassCompletions: Boolean = true,
    @SerializedName("relay_status") var relayStatus: Boolean = false,
    @SerializedName("status_interval_seconds") var statusIntervalSeconds: Int = 300,
    @SerializedName("discord_to_minecraft") var discordToMinecraft: DiscordInboundConfig = DiscordInboundConfig(),
    var formatting: DiscordFormattingConfig = DiscordFormattingConfig(),
) {
    fun normalized(): DiscordWebhookConfig = apply {
        webhookUrl = webhookUrl.trim()
        webhookUsername = webhookUsername.trim().ifBlank { "Chow Kingdom" }
        avatarUrl = avatarUrl.trim()
        playerAvatarUrls = playerAvatarUrls.mapKeys { (key, _) -> key.trim().lowercase() }
            .mapValues { (_, value) -> value.trim() }
            .filterKeys(String::isNotBlank)
            .filterValues(String::isNotBlank)
            .toMutableMap()
        minecraftAvatarUrlTemplate = minecraftAvatarUrlTemplate.trim().ifBlank { "https://mc-heads.net/avatar/{uuid}/128" }
        quickSkinAvatarUrlTemplate = quickSkinAvatarUrlTemplate.trim()
        quickSkinAvatarServer.normalize()
        discordToMinecraft.normalize()
        formatting.normalize()
        statusIntervalSeconds = statusIntervalSeconds.coerceAtLeast(30)
    }
}

class DiscordInboundConfig(
    var enabled: Boolean = false,
    var mode: String = "gateway",
    @SerializedName("bot_token") var botToken: String = "",
    @SerializedName("channel_id") var channelId: String = "",
    @SerializedName("poll_interval_seconds") var pollIntervalSeconds: Int = 3,
    @SerializedName("message_format") var messageFormat: String = ChatGlyphs.defaultDiscordMessageFormat(),
    @SerializedName("bot_presence_enabled") var botPresenceEnabled: Boolean = true,
    @SerializedName("bot_presence_interval_seconds") var botPresenceIntervalSeconds: Int = 60,
    @SerializedName("bot_presence_status") var botPresenceStatus: String = "online",
    @SerializedName("bot_presence_activity_type") var botPresenceActivityType: String = "watching",
    @SerializedName("bot_presence_format") var botPresenceFormat: String = "{online}/{max} players - {tps} TPS{season_status}",
) {
    fun normalize(): DiscordInboundConfig = apply {
        mode = mode.trim().lowercase().ifBlank { "gateway" }
        if (mode !in setOf("gateway", "polling")) mode = "gateway"
        botToken = botToken.trim()
        channelId = channelId.trim()
        pollIntervalSeconds = pollIntervalSeconds.coerceAtLeast(2)
        messageFormat = ChatGlyphs.normalizeDiscordMessageFormat(messageFormat)
        botPresenceIntervalSeconds = botPresenceIntervalSeconds.coerceAtLeast(15)
        botPresenceStatus = botPresenceStatus.trim().lowercase().ifBlank { "online" }
        if (botPresenceStatus !in setOf("online", "idle", "dnd", "invisible")) botPresenceStatus = "online"
        botPresenceActivityType = botPresenceActivityType.trim().lowercase().ifBlank { "watching" }
        if (botPresenceActivityType !in setOf("playing", "watching", "listening", "competing", "custom")) botPresenceActivityType = "watching"
        botPresenceFormat = botPresenceFormat.trim().ifBlank { "{online}/{max} players - {tps} TPS{season_status}" }
    }
}

class DiscordFormattingConfig(
    @SerializedName("chat_message") var chatMessage: String = "{message}",
    @SerializedName("chat_fallback_message") var chatFallbackMessage: String = "**{player}**: {message}",
    @SerializedName("join_title") var joinTitle: String = "Player Joined",
    @SerializedName("join_description") var joinDescription: String = "{player} joined. {online}/{max} players online",
    @SerializedName("join_color") var joinColor: String = "#57F287",
    @SerializedName("leave_title") var leaveTitle: String = "Player Left",
    @SerializedName("leave_description") var leaveDescription: String = "{player} left. {online}/{max} players online",
    @SerializedName("leave_color") var leaveColor: String = "#ED4245",
    @SerializedName("death_title") var deathTitle: String = "Player Died",
    @SerializedName("death_description") var deathDescription: String = "{death_message}",
    @SerializedName("death_color") var deathColor: String = "#ED4245",
    @SerializedName("battlepass_title") var battlepassTitle: String = "[[{battlepass}]] {scope} Mission Complete",
    @SerializedName("battlepass_description") var battlepassDescription: String = "{player} completed \"{mission}\" {scope_lower} mission. {player_raw} now has {xp} XP.",
    @SerializedName("battlepass_color") var battlepassColor: String = "#FEE75C",
    @SerializedName("status_message") var statusMessage: String = "Server status: {online}/{max} players online | TPS {tps}",
) {
    fun normalize(): DiscordFormattingConfig = apply {
        chatMessage = chatMessage.trim().ifBlank { "{message}" }
        chatFallbackMessage = chatFallbackMessage.trim().ifBlank { "**{player}**: {message}" }
        joinTitle = joinTitle.trim().ifBlank { "Player Joined" }
        joinDescription = joinDescription.trim().ifBlank { "{player} joined. {online}/{max} players online" }
        joinColor = joinColor.trim().ifBlank { "#57F287" }
        leaveTitle = leaveTitle.trim().ifBlank { "Player Left" }
        leaveDescription = leaveDescription.trim().ifBlank { "{player} left. {online}/{max} players online" }
        leaveColor = leaveColor.trim().ifBlank { "#ED4245" }
        deathTitle = deathTitle.trim().ifBlank { "Player Died" }
        deathDescription = deathDescription.trim().ifBlank { "{death_message}" }
        deathColor = deathColor.trim().ifBlank { "#ED4245" }
        battlepassTitle = battlepassTitle.trim().ifBlank { "[[{battlepass}]] {scope} Mission Complete" }
        battlepassDescription = battlepassDescription.trim().ifBlank { "{player} completed \"{mission}\" {scope_lower} mission. {player_raw} now has {xp} XP." }
        battlepassColor = battlepassColor.trim().ifBlank { "#FEE75C" }
        statusMessage = statusMessage.trim().ifBlank { "Server status: {online}/{max} players online | TPS {tps}" }
    }
}

class DiscordQuickSkinAvatarServerConfig(
    var enabled: Boolean = false,
    @SerializedName("bind_host") var bindHost: String = "0.0.0.0",
    var port: Int = 8765,
    @SerializedName("public_base_url") var publicBaseUrl: String = "",
) {
    fun normalize(): DiscordQuickSkinAvatarServerConfig = apply {
        bindHost = bindHost.trim().ifBlank { "0.0.0.0" }
        port = port.coerceIn(1, 65535)
        publicBaseUrl = publicBaseUrl.trim().removeSuffix("/")
    }
}
