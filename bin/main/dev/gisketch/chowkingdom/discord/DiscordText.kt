package dev.gisketch.chowkingdom.discord

object DiscordText {
    fun cleanContent(value: String): String = value.replace("@", "@\u200B").replace('\n', ' ').trim()

    fun escapeMarkdown(value: String): String = value
        .replace("\\", "\\\\")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("`", "\\`")

    fun applyTemplate(template: String, values: Map<String, String>): String = values.entries.fold(template) { current, (key, value) ->
        current.replace("{$key}", value)
    }

    fun parseColor(value: String): Int? {
        val normalized = value.trim().removePrefix("#").removePrefix("0x")
        if (normalized.isBlank()) return null
        return normalized.toIntOrNull(16)?.coerceIn(0, 0xFFFFFF)
    }
}