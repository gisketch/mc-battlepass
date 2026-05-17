package dev.gisketch.chowkingdom.npc

import net.neoforged.fml.ModList

object NpcFemaleGenderBridge {
    private val installed: Boolean by lazy {
        ModList.get().isLoaded("wildfire_gender")
    }

    fun available(): Boolean = installed
}
