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

    @Test
    fun npcInteractionSettingsNormalizeSocialPacingKnobs() {
        val settings = NpcInteractionSettingsDefinition(
            durationSeconds = 90,
            cooldownMinHours = 0,
            cooldownMaxHours = 0,
            witnessRadius = 99.0,
            firstWitnessNudgeMinSeconds = -5,
            firstWitnessNudgeMaxSeconds = 3,
            areaCooldownMinSeconds = -1,
            areaCooldownMaxSeconds = 20,
            dailyParticipationBudget = 99,
            trainerDailyParticipationBudget = -3,
            pairCooldownHours = 99,
        ).normalized()

        assertEquals(30, settings.durationSeconds)
        assertEquals(1, settings.cooldownMinHours)
        assertEquals(1, settings.cooldownMaxHours)
        assertEquals(32.0, settings.witnessRadius)
        assertEquals(0, settings.firstWitnessNudgeMinSeconds)
        assertEquals(3, settings.firstWitnessNudgeMaxSeconds)
        assertEquals(0, settings.areaCooldownMinSeconds)
        assertEquals(20, settings.areaCooldownMaxSeconds)
        assertEquals(48, settings.dailyParticipationBudget)
        assertEquals(0, settings.trainerDailyParticipationBudget)
        assertEquals(48, settings.pairCooldownHours)
    }

    @Test
    fun npcPokemonCompanionSettingsNormalizeEventWindowKnobs() {
        val settings = NpcPokemonCompanionSettingsDefinition(
            releaseMinSeconds = 1,
            releaseMaxSeconds = 10,
            recallMinSeconds = 2,
            recallMaxSeconds = 10,
            pokemonRoamReleaseChance = 120,
            meetupReleaseChance = -4,
            ambientPokemonReleaseChance = 55,
        ).normalized()

        assertEquals(30, settings.releaseMinSeconds)
        assertEquals(30, settings.releaseMaxSeconds)
        assertEquals(30, settings.recallMinSeconds)
        assertEquals(30, settings.recallMaxSeconds)
        assertEquals(100, settings.pokemonRoamReleaseChance)
        assertEquals(0, settings.meetupReleaseChance)
        assertEquals(55, settings.ambientPokemonReleaseChance)
    }
}
