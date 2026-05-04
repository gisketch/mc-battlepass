package dev.gisketch.chowkingdom.client

import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

object ChowDeathScreenClient {
    private val causeField = runCatching { DeathScreen::class.java.getDeclaredField("causeOfDeath").also { it.isAccessible = true } }.getOrNull()
    private val hardcoreField = runCatching { DeathScreen::class.java.getDeclaredField("hardcore").also { it.isAccessible = true } }.getOrNull()

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onScreenOpening)
        NeoForge.EVENT_BUS.addListener(::onMovementInput)
    }

    private fun onScreenOpening(event: ScreenEvent.Opening) {
        val deathScreen = event.newScreen as? DeathScreen ?: return
        val cause = runCatching { causeField?.get(deathScreen) as? Component }.getOrNull()
        val hardcore = runCatching { hardcoreField?.getBoolean(deathScreen) ?: false }.getOrDefault(false)
        event.newScreen = ChowDeathScreen(cause, hardcore)
    }

    private fun onMovementInput(event: MovementInputUpdateEvent) {
        if (net.minecraft.client.Minecraft.getInstance().screen !is ChowDeathScreen) return
        event.input.leftImpulse = 0.0f
        event.input.forwardImpulse = 0.0f
        event.input.up = false
        event.input.down = false
        event.input.left = false
        event.input.right = false
        event.input.jumping = false
        event.input.shiftKeyDown = false
    }
}
