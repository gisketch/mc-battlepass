package dev.gisketch.chowkingdom.discord

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.gisketch.chowkingdom.ChowKingdomMod
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object DiscordWebhookClient {
    private val gson = Gson()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun send(content: String, username: String? = null, avatarUrl: String? = null) {
        send(DiscordWebhookMessage(content = content, username = username, avatarUrl = avatarUrl))
    }

    fun send(message: DiscordWebhookMessage) {
        val config = DiscordConfig.current()
        if (!config.enabled || config.webhookUrl.isBlank()) return
        val effectiveUsername = message.username?.trim()?.takeIf(String::isNotBlank) ?: config.webhookUsername
        val effectiveAvatarUrl = message.avatarUrl?.trim()?.takeIf(String::isNotBlank) ?: config.avatarUrl

        val payload = JsonObject().apply {
            addProperty("content", message.content.take(DISCORD_CONTENT_LIMIT))
            addProperty("username", effectiveUsername.take(DISCORD_USERNAME_LIMIT))
            if (effectiveAvatarUrl.isNotBlank()) addProperty("avatar_url", effectiveAvatarUrl)
            if (message.embeds.isNotEmpty()) add("embeds", JsonArray().apply { message.embeds.take(DISCORD_EMBED_LIMIT).forEach { add(it.toJson()) } })
            add("allowed_mentions", JsonObject().apply {
                add("parse", JsonArray())
                if (message.allowedUserMentions.isNotEmpty()) add("users", JsonArray().apply { message.allowedUserMentions.take(DISCORD_ALLOWED_MENTION_LIMIT).forEach { add(it) } })
            })
        }

        val request = runCatching {
            HttpRequest.newBuilder(URI.create(config.webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build()
        }.getOrElse { exception ->
            ChowKingdomMod.LOGGER.warn("Invalid Discord webhook URL", exception)
            return
        }

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenAccept { response ->
                if (response.statusCode() !in 200..299) {
                    ChowKingdomMod.LOGGER.warn("Discord webhook returned HTTP {}", response.statusCode())
                }
            }
            .exceptionally { exception ->
                ChowKingdomMod.LOGGER.warn("Failed to send Discord webhook", exception)
                null
            }
    }

    private const val DISCORD_CONTENT_LIMIT = 2000
    private const val DISCORD_USERNAME_LIMIT = 80
    private const val DISCORD_EMBED_LIMIT = 10
    private const val DISCORD_ALLOWED_MENTION_LIMIT = 10

    private fun DiscordEmbed.toJson(): JsonObject = JsonObject().apply {
        if (title.isNotBlank()) addProperty("title", title.take(DISCORD_EMBED_TITLE_LIMIT))
        if (description.isNotBlank()) addProperty("description", description.take(DISCORD_EMBED_DESCRIPTION_LIMIT))
        color?.let { addProperty("color", it) }
        if (authorName.isNotBlank()) {
            add("author", JsonObject().apply {
                addProperty("name", authorName.take(DISCORD_EMBED_AUTHOR_LIMIT))
                if (authorIconUrl.isNotBlank()) addProperty("icon_url", authorIconUrl)
            })
        }
    }

    private const val DISCORD_EMBED_TITLE_LIMIT = 256
    private const val DISCORD_EMBED_DESCRIPTION_LIMIT = 4096
    private const val DISCORD_EMBED_AUTHOR_LIMIT = 256
}