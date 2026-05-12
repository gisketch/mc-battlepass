package dev.gisketch.chowkingdom.npc

import java.util.Locale

object NpcJobs {
    fun normalizeId(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_.:-]+"), "_")
            .trim('_')
    }
}
