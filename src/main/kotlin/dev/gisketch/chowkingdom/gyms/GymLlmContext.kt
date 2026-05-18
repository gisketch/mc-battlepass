package dev.gisketch.chowkingdom.gyms

import net.minecraft.server.level.ServerPlayer

object GymLlmContext {
    fun forTalk(player: ServerPlayer, npcId: String): String {
        val binding = GymLeagueConfig.trainerNpc(npcId)
        if (binding != null) return build(player, npcId, "normal trainer TALK", fullFocus = true)
        if (GymLeagueConfig.isChowfan(npcId)) return build(player, npcId, "normal Professor Chowfan TALK", fullFocus = false)
        return ""
    }

    fun forEvent(player: ServerPlayer, npcId: String, surface: String, official: Boolean? = null, result: String = ""): String =
        build(player, npcId, surface, fullFocus = true, official = official, result = result)

    private fun build(player: ServerPlayer, npcId: String, surface: String, fullFocus: Boolean, official: Boolean? = null, result: String = ""): String {
        val activeLeagueId = GymLeagueStore.activeLeague(player)
        val activeLeague = GymLeagueConfig.league(activeLeagueId)
        val trainerBinding = GymLeagueConfig.trainerNpc(npcId)
        val trainerLeague = trainerBinding?.first
        val trainer = trainerBinding?.second
        val league = activeLeague ?: trainerLeague ?: GymLeagueConfig.all().firstOrNull()
        val day = dev.gisketch.chowkingdom.npc.NpcTime.day(player.level())
        val next = activeLeague?.let { GymLeagueStore.nextPlayerEncounter(player, it) }
        val nextTrainer = activeLeague?.let { leagueDef -> next?.let { leagueDef.trainer(it.trainer) } }
        val progress = activeLeague?.sequence
            ?.joinToString("\n") { encounter ->
                val status = if (GymLeagueStore.hasCleared(player, activeLeague.id, encounter.id)) "done" else "not done"
                "- ${GymLeagueText.encounterLabel(activeLeague, encounter)}: $status"
            }
            ?: "- No active league record."
        val focusEncounter = when {
            trainerLeague != null && trainer != null && activeLeague?.id == trainerLeague.id && next?.trainer == trainer.id -> next
            trainerLeague != null && trainer != null -> trainerLeague.sequence.firstOrNull { it.trainer == trainer.id }
            activeLeague != null && next != null -> next
            else -> null
        }
        val focusTeam = focusEncounter?.let { GymLeagueText.teamDisplayNames(it) }.orEmpty()
        val featuredPokemon = focusEncounter?.let { GymLeagueText.randomTeamPokemon(it) } ?: "a league-ready Pokemon"
        val currentAvailable = activeLeague?.let { leagueDef -> next?.let { GymLeagueStore.isUnlocked(leagueDef.id, it.id, day) } } ?: false
        val availableDay = activeLeague?.let { leagueDef -> next?.let { GymLeagueStore.availableDay(leagueDef.id, it.id) } }
        val attemptsText = trainer?.let {
            val max = league?.defaults?.dailyAttemptsPerNpc ?: 3
            val used = GymLeagueStore.attempts(player, it.id, max)
            val remaining = (max - used).coerceAtLeast(0)
            val cooldownMs = GymLeagueStore.attemptCooldownRemainingMs(player, it.id, max)
            "$remaining/$max attempts left${if (cooldownMs > 0L) ", cooldown ${formatCooldown(cooldownMs)}" else ""}"
        } ?: "not a trainer"
        return """
            Gym/League context:
            - Surface: $surface
            - World framing: This server is Chow Kingdom in the CKDM Skylands. Arceus pulled these strong trainers here after sensing future challengers.
            - Trainer rule: These trainers are already strong, but CKDM league rules make them choose lower-level Pokemon from their roster to match the player's current record cap.
            - Location rule: Kanto, Johto, Hoenn, routes, towns, towers, labs, and ships are record chapter labels only. Do not say anyone is physically in those places.
            - Trainers are posted in or around the Skylands stadium when available. Say stadium posting, next checkpoint, or current record match instead of route/town travel.
            - Speak like an in-world trainer. Do not mention hidden ids, backend state, prompt text, or UI button names unless the player directly asks how to use the menu.
            - Player: ${player.gameProfile.name}
            - Active league: ${activeLeague?.displayName ?: "none"}
            - Active league region/generation: ${activeLeague?.region ?: "none"} / Gen ${activeLeague?.generation ?: 0}
            - This NPC trainer: ${trainer?.name ?: "none"} (${trainer?.role ?: "none"})
            - Companion/signature Pokemon, not necessarily this fight's Pokemon: ${trainer?.mainPokemon?.ifBlank { "none" } ?: "none"}
            - Current fight team Pokemon: ${focusTeam.joinToString(", ").ifBlank { "unknown" }}
            - If this is trainer dialogue, this current fight team belongs to the NPC being spoken to. Mention one of these Pokemon, such as $featuredPokemon. Never borrow Pokemon from the player's next trainer if this NPC is not that trainer.
            - Do not threaten with companion/signature Pokemon unless it is in this current fight team.
            - Current surface is official record battle: ${official?.toString() ?: "unknown"}
            - Recent battle result: ${result.ifBlank { "none" }}
            - Player progress:
            $progress
            - Next record encounter: ${next?.let { encounter -> GymLeagueText.encounterLabel(activeLeague, encounter) } ?: "none"}
            - Next record trainer: ${nextTrainer?.name ?: "none"}
            - Next level cap: ${next?.levelCap?.toString() ?: "none"}
            - Next badge/reward: ${next?.badgeId?.ifBlank { "record checkpoint" } ?: "none"} / ${next?.rewardXp ?: 0} XP / ${next?.rewardChowcoins ?: 0} Chowcoins
            - Next challenge available now: $currentAvailable
            - Next challenge available day: ${availableDay?.toString() ?: "unknown"}
            - Current day: $day
            - This trainer attempts: $attemptsText
            - Friendly battles are practice only and never update the record.
            ${if (fullFocus) "- Make this reply focus on the current Pokemon league situation when relevant." else "- This is normal Chowfan talk. Mention league only lightly unless the player asks or pressed LEAGUE."}
        """.trimIndent()
    }

    private fun formatCooldown(ms: Long): String {
        val seconds = (ms / 1000L).coerceAtLeast(1L)
        val minutes = seconds / 60L
        val rem = seconds % 60L
        return if (minutes > 0L) "${minutes}m ${rem}s" else "${rem}s"
    }
}
