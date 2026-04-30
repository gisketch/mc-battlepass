package dev.gisketch.chowkingdom

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object ChatGlyphs {
    const val DISCORD: String = "\uE100"
    const val CHOW_KINGDOM: String = "\uE101"
    private const val PADDED_SUFFIX = "  "
    private const val LEGACY_DISCORD_MESSAGE_FORMAT = "$DISCORD {author}: {message}"

    fun chowKingdomPrefix(): MutableComponent = Component.literal("$CHOW_KINGDOM$PADDED_SUFFIX").withStyle(ChatFormatting.GOLD)

    fun defaultDiscordMessageFormat(): String = "$DISCORD$PADDED_SUFFIX{author}: {message}"

    fun normalizeDiscordMessageFormat(messageFormat: String): String {
        val trimmed = messageFormat.trim()
        return when {
            trimmed.isBlank() -> defaultDiscordMessageFormat()
            trimmed == LEGACY_DISCORD_MESSAGE_FORMAT -> defaultDiscordMessageFormat()
            else -> trimmed
        }
    }
}
