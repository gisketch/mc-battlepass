package dev.gisketch.chowkingdom.compat

import net.neoforged.fml.ModList

object PunchyParryClientBridge {
    private val shieldNParryClient by lazy { runCatching { ShieldNParryClientApi() }.getOrNull() }

    @JvmStatic
    fun shouldDisablePunchyFirstPerson(): Boolean {
        if (!ModList.get().isLoaded("punchy") || !ModList.get().isLoaded("shieldnparry")) return false
        val api = shieldNParryClient ?: return false
        return runCatching { api.visualBlockTicks.getInt(null) > 0 }.getOrDefault(false)
    }

    private class ShieldNParryClientApi {
        private val clientEventsClass = Class.forName("com.ashalex.shieldnparry.client.ClientModEvents")
        val visualBlockTicks = clientEventsClass.getDeclaredField("visualBlockTicks").also { it.isAccessible = true }
    }
}
