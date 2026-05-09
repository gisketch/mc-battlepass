package dev.gisketch.chowkingdom.npc

import java.util.Locale

object NpcJobs {
    private val knownIds = setOf("adventurer", "warrior", "fashionista", "professor")

    fun normalizeId(value: String): String {
        val id = value.trim().lowercase(Locale.ROOT).ifBlank { DEFAULT_ID }
        return if (id in knownIds) id else DEFAULT_ID
    }

    private const val DEFAULT_ID = "adventurer"
}
