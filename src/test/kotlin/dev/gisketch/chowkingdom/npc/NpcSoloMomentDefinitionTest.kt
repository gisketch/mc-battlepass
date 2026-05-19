package dev.gisketch.chowkingdom.npc

import kotlin.test.Test
import kotlin.test.assertEquals

class NpcSoloMomentDefinitionTest {
    @Test
    fun soloMomentsNormalizeIdsTagsAndActivities() {
        val content = NpcMicroInteractionContentDefinition(
            soloMoments = mutableListOf(
                NpcSoloMomentDefinition(
                    id = " Quiet Thought! ",
                    topic = " Town Life ",
                    line = "  Watching the plaza.  ",
                    sourceTags = mutableListOf(" Town ", "town"),
                    activities = mutableListOf("Meet Up", "meetup"),
                ),
            ),
        ).normalized()

        val moment = content.soloMoments.single()
        assertEquals("quiet_thought", moment.id)
        assertEquals("town_life", moment.topic)
        assertEquals("Watching the plaza.", moment.line)
        assertEquals(listOf("town"), moment.sourceTags)
        assertEquals(listOf("meetup"), moment.activities)
    }
}
