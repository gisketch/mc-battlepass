package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.ModList

object ParagliderStaminaBridge {
    private val api by lazy { runCatching { StaminaApi() }.getOrNull() }

    fun spend(player: Player, amount: Double): Boolean {
        if (amount <= 0.0) return true
        if (!ModList.get().isLoaded("paraglider")) return true
        val api = api ?: return false
        return runCatching {
            val stamina = api.get.invoke(null, player) ?: return false
            val taken = api.take.invoke(stamina, amount, false, true) as Number
            taken.toDouble() >= amount * 0.999
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to spend Paraglider stamina", exception)
        }.getOrDefault(false)
    }

    fun available(player: Player): Double {
        if (!ModList.get().isLoaded("paraglider")) return Double.POSITIVE_INFINITY
        val api = api ?: return 0.0
        return runCatching {
            val stamina = api.get.invoke(null, player) ?: return 0.0
            val base = api.stamina.invoke(stamina) as Number
            val extra = api.extraStamina.invoke(stamina) as Number
            base.toDouble() + extra.toDouble()
        }.getOrDefault(0.0)
    }

    fun setRecoveryDelay(player: Player, ticks: Int) {
        if (ticks <= 0) return
        if (!ModList.get().isLoaded("paraglider")) return
        val api = api ?: return
        runCatching {
            val movement = api.getMovement.invoke(null, player) ?: return
            val currentDelay = api.recoveryDelay.invoke(movement) as Number
            api.setRecoveryDelay.invoke(movement, maxOf(currentDelay.toInt(), ticks))
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to set Paraglider recovery delay", exception)
        }
    }

    fun isParaglidingInAir(player: Player): Boolean {
        if (!ModList.get().isLoaded("paraglider")) return false
        if (player.onGround()) return false
        val api = api ?: return false
        return runCatching {
            val movement = api.getMovement.invoke(null, player) ?: return false
            val state = api.state.invoke(movement) ?: return false
            api.paragliding.invoke(state) as? Boolean == true
        }.getOrDefault(false)
    }

    private class StaminaApi {
        private val staminaClass = Class.forName("tictim.paraglider.api.stamina.Stamina")
        private val movementClass = Class.forName("tictim.paraglider.api.movement.Movement")
        private val playerStateClass = Class.forName("tictim.paraglider.api.movement.PlayerState")
        val get = staminaClass.getMethod("get", Player::class.java)
        val stamina = staminaClass.getMethod("stamina")
        val extraStamina = staminaClass.getMethod("extraStamina")
        val take = staminaClass.getMethod("takeStamina", java.lang.Double.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
        val getMovement = movementClass.getMethod("get", Player::class.java)
        val state = movementClass.getMethod("state")
        val recoveryDelay = movementClass.getMethod("recoveryDelay")
        val setRecoveryDelay = movementClass.getMethod("setRecoveryDelay", java.lang.Integer.TYPE)
        val paragliding = playerStateClass.getMethod("paragliding")
    }
}