package dev.gisketch.chowkingdom.battlepass

import com.google.gson.GsonBuilder
import java.util.UUID

object BattlepassClientState {
    data class PlayerProgress(
        val uuid: UUID,
        val name: String,
        val xpByPass: Map<String, Int>,
        val claimedByPass: Map<String, Set<Int>>,
    )

    private data class Snapshot(
        val passes: List<BattlepassPassDefinition>,
        val players: List<PlayerProgress>,
        val selfId: UUID,
    )

    private val gson = GsonBuilder().create()
    private var snapshot: Snapshot? = null

    fun apply(payload: BattlepassSyncPayload) {
        snapshot = Snapshot(
            payload.passesJson.mapNotNull { json -> runCatching { gson.fromJson(json, BattlepassPassDefinition::class.java) }.getOrNull() },
            payload.players.map { player ->
                PlayerProgress(
                    player.uuid,
                    player.name,
                    player.xpByPass,
                    player.claimedByPass.mapValues { (_, tiers) -> tiers.toSet() },
                )
            },
            payload.selfId,
        )
    }

    fun passes(): List<BattlepassPassDefinition> = snapshot?.passes.orEmpty()

    fun players(): List<PlayerProgress> = snapshot?.players.orEmpty()

    fun selfId(): UUID? = snapshot?.selfId

    fun xpFor(playerId: UUID, passId: String): Int? = snapshot?.players?.firstOrNull { player -> player.uuid == playerId }?.xpByPass?.get(passId)

    fun isClaimed(playerId: UUID, passId: String, tierXp: Int): Boolean? =
        snapshot?.players?.firstOrNull { player -> player.uuid == playerId }?.claimedByPass?.get(passId)?.contains(tierXp)
}