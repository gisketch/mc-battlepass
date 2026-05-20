package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerPlayer

object CobblemonStarterBridge {
    fun requestStarterAfterOnboarding(player: ServerPlayer) {
        player.server.execute {
            runCatching {
                val cobblemon = cobblemonObject() ?: return@execute
                val playerDataManager = cobblemon.javaClass.getMethod("getPlayerDataManager").invoke(cobblemon)
                val playerData = playerDataManager.javaClass.getMethod("getGenericData", ServerPlayer::class.java).invoke(playerDataManager, player)
                val starterSelected = playerData.javaClass.getMethod("getStarterSelected").invoke(playerData) as? Boolean ?: return@execute
                if (starterSelected) return@execute

                val starterLocked = playerData.javaClass.getMethod("getStarterLocked").invoke(playerData) as? Boolean ?: false
                if (starterLocked) {
                    playerData.javaClass.getMethod("setStarterLocked", java.lang.Boolean.TYPE).invoke(playerData, false)
                }

                val starterHandler = cobblemon.javaClass.getMethod("getStarterHandler").invoke(cobblemon)
                starterHandler.javaClass.getMethod("requestStarterChoice", ServerPlayer::class.java).invoke(starterHandler, player)
            }.onFailure { exception ->
                ChowKingdomMod.LOGGER.debug("Cobblemon starter bridge unavailable", exception)
            }
        }
    }

    private fun cobblemonObject(): Any? = runCatching {
        Class.forName("com.cobblemon.mod.common.Cobblemon").getField("INSTANCE").get(null)
    }.getOrNull()
}
