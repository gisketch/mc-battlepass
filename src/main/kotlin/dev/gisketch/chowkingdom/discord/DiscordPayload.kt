package dev.gisketch.chowkingdom.discord

data class DiscordWebhookMessage(
    val content: String = "",
    val username: String? = null,
    val avatarUrl: String? = null,
    val embeds: List<DiscordEmbed> = emptyList(),
)

data class DiscordEmbed(
    val title: String = "",
    val description: String = "",
    val color: Int? = null,
    val authorName: String = "",
    val authorIconUrl: String = "",
)