package dev.gisketch.chowkingdom.gyms

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import dev.gisketch.chowkingdom.npc.NpcDefinition
import dev.gisketch.chowkingdom.npc.NpcHousingDefinition
import dev.gisketch.chowkingdom.npc.NpcMissionsDefinition
import dev.gisketch.chowkingdom.npc.NpcPersonalityDefinition
import dev.gisketch.chowkingdom.npc.NpcScheduleDefinition
import dev.gisketch.chowkingdom.npc.NpcScheduleEntryDefinition
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object GymLeagueConfig {
    private var leagues: Map<String, GymLeagueDefinition> = linkedMapOf()
    private var npcToTrainer: Map<String, Pair<GymLeagueDefinition, GymTrainerDefinition>> = linkedMapOf()

    private val root: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("gyms")

    private val leaguesDir: Path
        get() = root.resolve("leagues")

    private val trainerJsonDir: Path
        get() = root.resolve("rct_trainers")

    private val npcDir: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("npcs")

    fun load() {
        leaguesDir.createDirectories()
        trainerJsonDir.createDirectories()
        npcDir.createDirectories()
        writeDefaultsIfMissing()
        val loaded = linkedMapOf<String, GymLeagueDefinition>()
        Files.list(leaguesDir).use { stream ->
            stream.filter { path -> path.extension.equals("toml", ignoreCase = true) }.forEach { path ->
                val league = TomlConfigIO.read(path, GymLeagueDefinition::class.java, ::GymLeagueDefinition)
                    .normalized(path.nameWithoutExtension)
                if (league.id.isNotBlank()) loaded[league.id] = league
            }
        }
        leagues = loaded
        npcToTrainer = loaded.values.flatMap { league -> league.trainers.map { trainer -> trainer.npcId to (league to trainer) } }.toMap()
    }

    fun all(): Collection<GymLeagueDefinition> = leagues.values

    fun league(id: String): GymLeagueDefinition? = leagues[cleanId(id)]

    fun trainerNpc(npcId: String): Pair<GymLeagueDefinition, GymTrainerDefinition>? = npcToTrainer[cleanId(npcId)]

    fun isTrainerNpc(npcId: String): Boolean = cleanId(npcId) in npcToTrainer

    fun isChowfan(npcId: String): Boolean = leagues.values.any { it.chowfanNpcId == cleanId(npcId) }

    fun teamFile(teamRef: String): Path = root.resolve(teamRef.replace('/', java.io.File.separatorChar))

    private fun writeDefaultsIfMissing() {
        val kanto = leaguesDir.resolve("kanto.toml")
        if (!kanto.exists()) TomlConfigIO.write(kanto, defaultKantoLeague())
        defaultNpcDefinitions().forEach { definition ->
            val file = npcDir.resolve("${definition.id}.toml")
            if (!file.exists()) TomlConfigIO.write(file, definition)
        }
        defaultTrainerJson().forEach { (relative, json) ->
            val file = trainerJsonDir.resolve("kanto").resolve(relative)
            if (!file.exists()) TomlConfigIO.writeJson(file, json)
        }
    }

    private fun defaultKantoLeague(): GymLeagueDefinition = GymLeagueDefinition(
        id = "kanto",
        displayName = "Kanto League",
        stadiumArea = "main_stadium",
        activeOnly = true,
        starterMode = "story_only",
        chowfanNpcId = "prof_chowfan",
        defaults = GymLeagueDefaults(),
        trainers = mutableListOf(
            GymTrainerDefinition("blue", "Blue", "rival", "trainer_blue", "", "kanto_rivals", "cobblemon:blastoise"),
            GymTrainerDefinition("brock", "Brock", "gym_leader", "gym_brock", "boulder_badge", "kanto_gym_leaders", "cobblemon:onix"),
            GymTrainerDefinition("misty", "Misty", "gym_leader", "gym_misty", "cascade_badge", "kanto_gym_leaders", "cobblemon:starmie"),
            GymTrainerDefinition("lt_surge", "Lt. Surge", "gym_leader", "gym_lt_surge", "thunder_badge", "kanto_gym_leaders", "cobblemon:raichu"),
            GymTrainerDefinition("erika", "Erika", "gym_leader", "gym_erika", "rainbow_badge", "kanto_gym_leaders", "cobblemon:vileplume"),
            GymTrainerDefinition("koga", "Koga", "gym_leader", "gym_koga", "soul_badge", "kanto_gym_leaders", "cobblemon:weezing"),
            GymTrainerDefinition("sabrina", "Sabrina", "gym_leader", "gym_sabrina", "marsh_badge", "kanto_gym_leaders", "cobblemon:alakazam"),
            GymTrainerDefinition("blaine", "Blaine", "gym_leader", "gym_blaine", "volcano_badge", "kanto_gym_leaders", "cobblemon:arcanine"),
            GymTrainerDefinition("giovanni", "Giovanni", "gym_leader", "gym_giovanni", "earth_badge", "kanto_gym_leaders", "cobblemon:rhydon"),
            GymTrainerDefinition("lorelei", "Lorelei", "elite_four", "elite_lorelei", "", "kanto_elite", "cobblemon:lapras"),
            GymTrainerDefinition("bruno", "Bruno", "elite_four", "elite_bruno", "", "kanto_elite", "cobblemon:machamp"),
            GymTrainerDefinition("agatha", "Agatha", "elite_four", "elite_agatha", "", "kanto_elite", "cobblemon:gengar"),
            GymTrainerDefinition("lance", "Lance", "elite_four", "elite_lance", "", "kanto_elite", "cobblemon:dragonite"),
        ),
        sequence = defaultKantoSequence().toMutableList(),
    ).normalized("kanto")

    private fun defaultKantoSequence(): List<GymEncounterDefinition> = listOf(
        encounter("blue_oaks_lab", 1, "rival", "blue", "Blue - Oak's Lab", 5),
        encounter("blue_route_22_pre_brock", 2, "rival", "blue", "Blue - Route 22", 9),
        encounter("brock", 3, "gym", "brock", "Brock", 14, "boulder_badge"),
        encounter("blue_nugget_bridge", 4, "rival", "blue", "Blue - Nugget Bridge", 19),
        encounter("misty", 5, "gym", "misty", "Misty", 21, "cascade_badge"),
        encounter("blue_ss_anne", 6, "rival", "blue", "Blue - S.S. Anne", 24),
        encounter("lt_surge", 7, "gym", "lt_surge", "Lt. Surge", 24, "thunder_badge"),
        encounter("erika", 8, "gym", "erika", "Erika", 29, "rainbow_badge"),
        encounter("blue_pokemon_tower", 9, "rival", "blue", "Blue - Pokemon Tower", 34),
        encounter("blue_silph_co", 10, "rival", "blue", "Blue - Silph Co.", 40),
        encounter("koga", 11, "gym", "koga", "Koga", 43, "soul_badge"),
        encounter("sabrina", 12, "gym", "sabrina", "Sabrina", 43, "marsh_badge"),
        encounter("blaine", 13, "gym", "blaine", "Blaine", 47, "volcano_badge"),
        encounter("blue_route_22_final", 14, "rival", "blue", "Blue - Route 22 Final", 48),
        encounter("giovanni", 15, "gym", "giovanni", "Giovanni", 50, "earth_badge"),
        encounter("elite_lorelei", 16, "elite_four", "lorelei", "Elite Four - Lorelei", 54),
        encounter("elite_bruno", 17, "elite_four", "bruno", "Elite Four - Bruno", 56),
        encounter("elite_agatha", 18, "elite_four", "agatha", "Elite Four - Agatha", 58),
        encounter("elite_lance", 19, "elite_four", "lance", "Elite Four - Lance", 60),
        encounter("champion_blue", 20, "champion", "blue", "Champion Blue", 62),
    )

    private fun encounter(id: String, order: Int, kind: String, trainer: String, name: String, cap: Int, badge: String = "") =
        GymEncounterDefinition(
            id = id,
            order = order,
            kind = kind,
            trainer = trainer,
            displayName = name,
            badgeId = badge,
            levelCap = cap,
            teamRef = "rct_trainers/kanto/$id.json",
            required = true,
            globalUnlockNext = true,
            spawnDelayDays = 1,
        )

    private fun defaultNpcDefinitions(): List<NpcDefinition> {
        val league = defaultKantoLeague()
        val trainers = league.trainers.map { trainer ->
            NpcDefinition(
                id = trainer.npcId,
                name = trainer.name,
                title = trainer.role.replace('_', ' ').replaceFirstChar { it.titlecase() },
                mainPokemon = trainer.mainPokemon,
                housing = NpcHousingDefinition(canMoveIn = false, requiresBed = false),
                missions = NpcMissionsDefinition(enabled = false),
                personality = NpcPersonalityDefinition(
                    llmPrompt = "${trainer.name} is a ${trainer.role.replace('_', ' ')} in the Kanto League. Their main interest is Pokemon battles at the CKDM stadium.",
                    traits = mutableListOf("pokemon trainer", trainer.role),
                    speechStyle = "confident battle NPC",
                ),
                schedule = stadiumSchedule(),
            )
        }
        return trainers
    }

    private fun stadiumSchedule(): NpcScheduleDefinition = NpcScheduleDefinition(
        activities = mutableListOf(
            NpcScheduleEntryDefinition(fromHour = 6, toHour = 22, activity = "meetup"),
            NpcScheduleEntryDefinition(fromHour = 22, toHour = 6, activity = "sleep"),
        ),
    )

    private fun defaultTrainerJson(): Map<String, String> = defaultKantoSequence().associate { encounter ->
        "${encounter.id}.json" to """
            {
              "name": "${encounter.displayName}",
              "ai": { "type": "rct" },
              "battleFormat": "GEN_9_SINGLES",
              "battleRules": {
                "maxItemUses": 2,
                "healPlayers": true,
                "adjustPlayerLevels": false,
                "adjustNPCLevels": false
              },
              "team": [
                {
                  "species": "cobblemon:pikachu",
                  "level": ${encounter.levelCap},
                  "moveset": ["quickattack", "thundershock"]
                }
              ]
            }
        """.trimIndent()
    }
}
