package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.battlepass.BattlepassMissionHooks
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionSignal
import net.minecraft.server.level.ServerPlayer

object NpcMissionHooks {
    private const val NPC_QUEST_COMPLETED = "gisketchs_chowkingdom_mod:npc_quest_completed"
    private const val NPC_QUIZ_ANSWERED_CORRECTLY = "gisketchs_chowkingdom_mod:npc_quiz_answered_correctly"
    private const val NPC_FRIENDSHIP_LEVEL_REACHED = "gisketchs_chowkingdom_mod:npc_friendship_level_reached"
    private val baselineLevel = NpcFriendshipLevels.level(NpcFriendshipLevels.DEFAULT_POINTS)

    fun recordQuestCompleted(player: ServerPlayer, definition: NpcDefinition, quest: NpcAcceptedQuestState): Boolean =
        BattlepassMissionHooks.record(player, NPC_QUEST_COMPLETED, attributes = questAttributes(definition, quest))

    fun recordQuizAnsweredCorrectly(player: ServerPlayer, definition: NpcDefinition, quest: NpcAcceptedQuestState): Boolean =
        BattlepassMissionHooks.record(player, NPC_QUIZ_ANSWERED_CORRECTLY, attributes = questAttributes(definition, quest) + quizAttributes(quest))

    fun refreshFriendshipProgress(player: ServerPlayer): Boolean {
        val signals = NpcStore.friendshipSnapshotsFor(player).flatMap { (npcId, friendship) ->
            if (friendship.level <= baselineLevel) return@flatMap emptyList()
            ((baselineLevel + 1)..friendship.level).map { level ->
                BattlepassMissionSignal(
                    eventIds = setOf(NPC_FRIENDSHIP_LEVEL_REACHED),
                    attributes = mapOf(
                        "npc" to npcId,
                        "level" to level.toString(),
                        "friendship.level" to level.toString(),
                        "friendship.category" to friendship.category.id,
                    ),
                )
            }
        }
        if (signals.isEmpty()) return false
        return BattlepassMissionHooks.setCounts(player, signals)
    }

    private fun questAttributes(definition: NpcDefinition, quest: NpcAcceptedQuestState): Map<String, String> = mapOf(
        "npc" to definition.id,
        "quest_id" to quest.questId,
        "category" to quest.category,
        "pass_id" to quest.passId,
    )

    private fun quizAttributes(quest: NpcAcceptedQuestState): Map<String, String> = buildMap {
        quest.filters["quiz.topic"]?.takeIf(String::isNotBlank)?.let { put("quiz.topic", it) }
        quest.filters["quiz.prompt"]?.takeIf(String::isNotBlank)?.let { put("quiz.prompt", it) }
    }
}
