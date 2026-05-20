package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList
import java.util.UUID

object CombatRollStaminaBridge {
    private val api by lazy { runCatching { CombatRollApi() }.getOrNull() }
    private val lastAvailableRolls: MutableMap<UUID, Int> = mutableMapOf()

    fun onPlayerTick(player: ServerPlayer, config: StaminaCompatDefinition) {
        if (!ModList.get().isLoaded("combatroll")) return
        val api = api ?: return
        val available = api.availableRolls(player) ?: return
        val previous = lastAvailableRolls.put(player.uuid, available) ?: return
        if (available < previous) UnifiedStaminaFeature.spendEpicFightSkill(player, config.combatRollCost)
    }

    fun clear(player: ServerPlayer) {
        lastAvailableRolls.remove(player.uuid)
    }

    private class CombatRollApi {
        private val rollingEntityClass = Class.forName("net.combat_roll.internals.RollingEntity")
        private val rollManagerClass = Class.forName("net.combat_roll.internals.RollManager")
        private val getRollManager = rollingEntityClass.getMethod("getRollManager")
        private val availableRolls = rollManagerClass.getDeclaredField("availableRolls").also { it.isAccessible = true }

        fun availableRolls(player: ServerPlayer): Int? = runCatching {
            if (!rollingEntityClass.isInstance(player)) return null
            val manager = getRollManager.invoke(player) ?: return null
            availableRolls.getInt(manager)
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to read Combat Roll state", exception)
        }.getOrNull()
    }
}