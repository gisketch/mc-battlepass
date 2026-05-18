package dev.gisketch.chowkingdom.gyms

import com.google.gson.annotations.SerializedName
import java.util.Locale

class GymLeagueDefinition(
    var id: String = "",
    @SerializedName("display_name") var displayName: String = "",
    @SerializedName("stadium_area") var stadiumArea: String = "main_stadium",
    @SerializedName("active_only") var activeOnly: Boolean = true,
    @SerializedName("starter_mode") var starterMode: String = "story_only",
    @SerializedName("chowfan_npc_id") var chowfanNpcId: String = "chowfan",
    var defaults: GymLeagueDefaults = GymLeagueDefaults(),
    var trainers: MutableList<GymTrainerDefinition> = mutableListOf(),
    var sequence: MutableList<GymEncounterDefinition> = mutableListOf(),
) {
    fun normalized(fallbackId: String): GymLeagueDefinition = apply {
        id = cleanId(id.ifBlank { fallbackId })
        displayName = displayName.trim().ifBlank { id.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) } }
        stadiumArea = cleanId(stadiumArea.ifBlank { "main_stadium" })
        starterMode = cleanId(starterMode.ifBlank { "story_only" })
        chowfanNpcId = cleanId(chowfanNpcId.ifBlank { "chowfan" })
        defaults = defaults.normalized()
        trainers = trainers.map { it.normalized() }.filter { it.id.isNotBlank() && it.npcId.isNotBlank() }.distinctBy { it.id }.toMutableList()
        sequence = sequence.mapIndexed { index, encounter -> encounter.normalized(index + 1, defaults) }
            .filter { it.id.isNotBlank() && it.trainer.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.order }
            .toMutableList()
    }

    fun trainer(id: String): GymTrainerDefinition? = trainers.firstOrNull { it.id == cleanId(id) }

    fun encounter(id: String): GymEncounterDefinition? = sequence.firstOrNull { it.id == cleanId(id) }

    fun firstEncounter(): GymEncounterDefinition? = sequence.firstOrNull()

    fun nextAfter(encounterId: String): GymEncounterDefinition? {
        val current = encounter(encounterId) ?: return null
        return sequence.firstOrNull { it.order > current.order }
    }
}

class GymLeagueDefaults(
    @SerializedName("daily_attempts_per_npc") var dailyAttemptsPerNpc: Int = 3,
    @SerializedName("attempt_cooldown_minutes") var attemptCooldownMinutes: Int = 15,
    @SerializedName("battle_format") var battleFormat: String = "GEN_9_SINGLES",
    @SerializedName("hard_level_cap") var hardLevelCap: Boolean = true,
    @SerializedName("level_cap_scope") var levelCapScope: String = "whole_party",
    @SerializedName("heal_players") var healPlayers: Boolean = true,
    @SerializedName("max_item_uses") var maxItemUses: Int = 2,
    @SerializedName("rival_branch") var rivalBranch: String = "default",
    @SerializedName("pass_id") var passId: String = "combat",
    @SerializedName("badge_xp") var badgeXp: Int = 250,
    @SerializedName("badge_chowcoins") var badgeChowcoins: Long = 500L,
) {
    fun normalized(): GymLeagueDefaults = apply {
        dailyAttemptsPerNpc = dailyAttemptsPerNpc.coerceIn(1, 12)
        attemptCooldownMinutes = attemptCooldownMinutes.coerceIn(1, 24 * 60)
        battleFormat = battleFormat.trim().ifBlank { "GEN_9_SINGLES" }
        levelCapScope = cleanId(levelCapScope.ifBlank { "whole_party" })
        maxItemUses = maxItemUses.coerceIn(0, 99)
        rivalBranch = cleanId(rivalBranch.ifBlank { "default" })
        passId = cleanId(passId.ifBlank { "combat" })
        badgeXp = badgeXp.coerceAtLeast(0)
        badgeChowcoins = badgeChowcoins.coerceAtLeast(0L)
    }
}

class GymTrainerDefinition(
    var id: String = "",
    var name: String = "",
    var role: String = "gym_leader",
    @SerializedName("npc_id") var npcId: String = "",
    @SerializedName("badge_id") var badgeId: String = "",
    @SerializedName("spawn_group") var spawnGroup: String = "",
    @SerializedName("main_pokemon") var mainPokemon: String = "",
) {
    fun normalized(): GymTrainerDefinition = apply {
        id = cleanId(id)
        name = name.trim().ifBlank { id.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) } }
        role = cleanId(role.ifBlank { "gym_leader" })
        npcId = cleanId(npcId.ifBlank { id })
        badgeId = cleanId(badgeId)
        spawnGroup = cleanId(spawnGroup)
        mainPokemon = cleanEntityId(mainPokemon)
    }
}

class GymEncounterDefinition(
    var id: String = "",
    var order: Int = 0,
    var kind: String = "gym",
    var trainer: String = "",
    @SerializedName("display_name") var displayName: String = "",
    @SerializedName("badge_id") var badgeId: String = "",
    @SerializedName("level_cap") var levelCap: Int = 5,
    @SerializedName("team_ref") var teamRef: String = "",
    var required: Boolean = true,
    @SerializedName("global_unlock_next") var globalUnlockNext: Boolean = true,
    @SerializedName("spawn_delay_days") var spawnDelayDays: Int = 1,
    @SerializedName("reward_xp") var rewardXp: Int = -1,
    @SerializedName("reward_chowcoins") var rewardChowcoins: Long = -1L,
) {
    fun normalized(fallbackOrder: Int, defaults: GymLeagueDefaults): GymEncounterDefinition = apply {
        id = cleanId(id)
        order = order.takeIf { it > 0 } ?: fallbackOrder
        kind = cleanId(kind.ifBlank { "gym" })
        trainer = cleanId(trainer)
        displayName = displayName.trim().ifBlank { id.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) } }
        badgeId = cleanId(badgeId)
        levelCap = levelCap.coerceIn(1, 100)
        teamRef = teamRef.trim()
        spawnDelayDays = spawnDelayDays.coerceIn(0, 30)
        rewardXp = if (rewardXp < 0) defaults.badgeXp else rewardXp.coerceAtLeast(0)
        rewardChowcoins = if (rewardChowcoins < 0) defaults.badgeChowcoins else rewardChowcoins.coerceAtLeast(0L)
    }
}

internal fun cleanId(value: String): String = value.trim().lowercase(Locale.ROOT).replace(' ', '_')

internal fun cleanEntityId(value: String): String {
    val clean = value.trim().lowercase(Locale.ROOT)
    return if (clean.contains(':')) clean else clean
}
