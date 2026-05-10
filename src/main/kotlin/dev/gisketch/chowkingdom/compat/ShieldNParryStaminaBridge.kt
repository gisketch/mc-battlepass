package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList
import java.util.UUID

object ShieldNParryStaminaBridge {
    private val api by lazy { runCatching { ShieldNParryApi() }.getOrNull() }
    private val rewardedParryTicks: MutableMap<UUID, Long> = mutableMapOf()

    fun hasActiveParry(player: ServerPlayer): Boolean {
        if (!ModList.get().isLoaded("shieldnparry")) return false
        val api = api ?: return false
        return runCatching {
            val data = api.data(player) ?: return false
            api.hasActiveParry.getBoolean(data)
        }.getOrDefault(false)
    }

    fun clearActiveParry(player: ServerPlayer) {
        if (!ModList.get().isLoaded("shieldnparry")) return
        val api = api ?: return
        runCatching {
            val data = api.data(player) ?: return
            api.hasActiveParry.setBoolean(data, false)
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to clear Shield n Parry active parry", exception)
        }
    }

    fun consumeSuccessfulParry(player: ServerPlayer, gameTime: Long): Boolean {
        if (!ModList.get().isLoaded("shieldnparry")) return false
        val api = api ?: return false
        return runCatching {
            val data = api.data(player) ?: return false
            val successful = !api.hasActiveParry.getBoolean(data) && api.inRecovery.getBoolean(data) && api.consecutiveAttempts.getInt(data) == 0
            if (!successful || rewardedParryTicks[player.uuid] == gameTime) return false
            rewardedParryTicks[player.uuid] = gameTime
            true
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to read Shield n Parry success state", exception)
        }.getOrDefault(false)
    }

    fun clear(player: ServerPlayer) {
        rewardedParryTicks.remove(player.uuid)
    }

    private class ShieldNParryApi {
        private val handlerClass = Class.forName("com.ashalex.shieldnparry.handler.PlayerParryHandler")
        private val dataClass = Class.forName("com.ashalex.shieldnparry.handler.PlayerParryHandler\$ParryData")
        private val playerData = handlerClass.getField("playerData")
        val hasActiveParry = dataClass.getDeclaredField("hasActiveParry").also { it.isAccessible = true }
        val inRecovery = dataClass.getDeclaredField("inRecovery").also { it.isAccessible = true }
        val consecutiveAttempts = dataClass.getDeclaredField("consecutiveAttempts").also { it.isAccessible = true }

        fun data(player: ServerPlayer): Any? = (playerData.get(null) as? Map<*, *>)?.get(player.uuid)
    }
}