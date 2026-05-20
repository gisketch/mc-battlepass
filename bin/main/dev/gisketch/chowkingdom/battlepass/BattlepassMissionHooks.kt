package dev.gisketch.chowkingdom.battlepass

import net.minecraft.server.level.ServerPlayer

object BattlepassMissionHooks {
    fun record(player: ServerPlayer, eventId: String, amount: Int = 1, attributes: Map<String, String> = emptyMap(), aliases: Set<String> = emptySet()): Boolean {
        val changed = BattlepassMissionEventBank.record(player, eventId, amount, attributes, aliases)
        if (changed) BattlepassNetwork.syncAllPlayers()
        return changed
    }

    fun setCounts(player: ServerPlayer, signals: List<BattlepassMissionSignal>): Boolean {
        val changed = BattlepassMissionProgressStore.setSignalCounts(player, signals)
        if (changed) BattlepassNetwork.syncAllPlayers()
        return changed
    }
}
