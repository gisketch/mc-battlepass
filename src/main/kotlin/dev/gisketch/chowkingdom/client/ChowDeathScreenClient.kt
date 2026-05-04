package dev.gisketch.chowkingdom.client

import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

object ChowDeathScreenClient {
    private val causeField = runCatching { DeathScreen::class.java.getDeclaredField("causeOfDeath").also { it.isAccessible = true } }.getOrNull()
    private val hardcoreField = runCatching { DeathScreen::class.java.getDeclaredField("hardcore").also { it.isAccessible = true } }.getOrNull()

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onScreenOpening)
    }

    private fun onScreenOpening(event: ScreenEvent.Opening) {
        val deathScreen = event.newScreen as? DeathScreen ?: return
        val cause = runCatching { causeField?.get(deathScreen) as? Component }.getOrNull()
        val hardcore = runCatching { hardcoreField?.getBoolean(deathScreen) ?: false }.getOrDefault(false)
        event.newScreen = ChowDeathScreen(cause, hardcore)
    }
}
