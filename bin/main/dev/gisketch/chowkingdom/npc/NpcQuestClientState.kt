package dev.gisketch.chowkingdom.npc

object NpcQuestClientState {
    private var quests: List<NpcQuestHudEntryPayload> = emptyList()

    fun apply(payload: NpcQuestSyncPayload) {
        quests = payload.quests.sortedBy { quest -> quest.acceptedAtTick }
    }

    fun activeQuests(): List<NpcQuestHudEntryPayload> = quests
}