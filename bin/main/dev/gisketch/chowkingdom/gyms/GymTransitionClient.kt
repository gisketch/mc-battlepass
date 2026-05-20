package dev.gisketch.chowkingdom.gyms

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers

object GymTransitionClient {
    private val LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "gym_battle_fade")
    private var startedAtMs = 0L
    private var fadeInMs = 0
    private var holdMs = 0
    private var fadeOutMs = 0

    @JvmStatic
    fun startFade(payload: GymBattleFadePayload) {
        startedAtMs = System.currentTimeMillis()
        fadeInMs = payload.fadeInMs.coerceIn(0, 5_000)
        holdMs = payload.holdMs.coerceIn(0, 5_000)
        fadeOutMs = payload.fadeOutMs.coerceIn(0, 5_000)
    }

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerGuiLayers)
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID) { guiGraphics, _ ->
            val alpha = alpha() ?: return@registerAbove
            val minecraft = Minecraft.getInstance()
            guiGraphics.fill(0, 0, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, colorWithAlpha(alpha))
        }
    }

    private fun alpha(): Float? {
        if (startedAtMs <= 0L) return null
        val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        val total = fadeInMs + holdMs + fadeOutMs
        if (elapsed >= total) {
            startedAtMs = 0L
            return null
        }
        val raw = when {
            fadeInMs > 0 && elapsed < fadeInMs -> elapsed / fadeInMs.toFloat()
            elapsed < fadeInMs + holdMs -> 1.0f
            fadeOutMs > 0 -> 1.0f - ((elapsed - fadeInMs - holdMs) / fadeOutMs.toFloat()).coerceIn(0.0f, 1.0f)
            else -> 0.0f
        }
        return easeInOut(raw.coerceIn(0.0f, 1.0f))
    }

    private fun easeInOut(value: Float): Float =
        if (value < 0.5f) 2.0f * value * value else 1.0f - Math.pow((-2.0f * value + 2.0f).toDouble(), 2.0).toFloat() / 2.0f

    private fun colorWithAlpha(alpha: Float): Int = ((alpha.coerceIn(0.0f, 1.0f) * 255).toInt() shl 24)
}
