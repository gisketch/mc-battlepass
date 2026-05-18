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
        leagues = loaded.values.sortedWith(compareBy<GymLeagueDefinition> { it.generation.takeIf { gen -> gen > 0 } ?: 999 }.thenBy { it.id })
            .associateBy { it.id }
        npcToTrainer = loaded.values.flatMap { league -> league.trainers.map { trainer -> trainer.npcId to (league to trainer) } }.toMap()
    }

    fun all(): Collection<GymLeagueDefinition> = leagues.values

    fun league(id: String): GymLeagueDefinition? = leagues[cleanId(id)]

    fun trainerNpc(npcId: String): Pair<GymLeagueDefinition, GymTrainerDefinition>? = npcToTrainer[cleanId(npcId)]

    fun isTrainerNpc(npcId: String): Boolean = cleanId(npcId) in npcToTrainer

    fun isChowfan(npcId: String): Boolean = leagues.values.any { it.chowfanNpcId == cleanId(npcId) }

    fun teamFile(teamRef: String): Path = root.resolve(teamRef.replace('/', java.io.File.separatorChar))

    private fun writeDefaultsIfMissing() {
        defaultLeagues().forEach { league ->
            val file = leaguesDir.resolve("${league.id}.toml")
            if (!file.exists()) TomlConfigIO.write(file, league)
        }
        defaultNpcDefinitions().forEach { definition ->
            val file = npcDir.resolve("${definition.id}.toml")
            if (!file.exists()) TomlConfigIO.write(file, definition)
        }
        defaultTrainerJson().forEach { (relative, json) ->
            val file = trainerJsonDir.resolve(relative)
            if (!file.exists()) TomlConfigIO.writeJson(file, json)
        }
    }

    private fun defaultLeagues(): List<GymLeagueDefinition> = listOf(defaultKantoLeague(), defaultJohtoLeague(), defaultHoennLeague())

    private fun defaultKantoLeague(): GymLeagueDefinition = league(
        id = "kanto",
        displayName = "Kanto League",
        generation = 1,
        region = "Kanto",
        description = "The classic Kanto badge record, rebuilt as a CKDM Skylands stadium circuit.",
        trainers = listOf(
            trainer("blue", "Blue", "rival", "trainer_blue", "", "kanto_rivals", "cobblemon:blastoise"),
            trainer("brock", "Brock", "gym_leader", "gym_brock", "boulder_badge", "kanto_gym_leaders", "cobblemon:onix"),
            trainer("misty", "Misty", "gym_leader", "gym_misty", "cascade_badge", "kanto_gym_leaders", "cobblemon:starmie"),
            trainer("lt_surge", "Lt. Surge", "gym_leader", "gym_lt_surge", "thunder_badge", "kanto_gym_leaders", "cobblemon:raichu"),
            trainer("erika", "Erika", "gym_leader", "gym_erika", "rainbow_badge", "kanto_gym_leaders", "cobblemon:vileplume"),
            trainer("koga", "Koga", "gym_leader", "gym_koga", "soul_badge", "kanto_gym_leaders", "cobblemon:weezing"),
            trainer("sabrina", "Sabrina", "gym_leader", "gym_sabrina", "marsh_badge", "kanto_gym_leaders", "cobblemon:alakazam"),
            trainer("blaine", "Blaine", "gym_leader", "gym_blaine", "volcano_badge", "kanto_gym_leaders", "cobblemon:arcanine"),
            trainer("giovanni", "Giovanni", "gym_leader", "gym_giovanni", "earth_badge", "kanto_gym_leaders", "cobblemon:rhydon"),
            trainer("lorelei", "Lorelei", "elite_four", "elite_lorelei", "", "kanto_elite", "cobblemon:lapras"),
            trainer("bruno", "Bruno", "elite_four", "elite_bruno", "", "kanto_elite", "cobblemon:machamp"),
            trainer("agatha", "Agatha", "elite_four", "elite_agatha", "", "kanto_elite", "cobblemon:gengar"),
            trainer("lance", "Lance", "elite_four", "elite_lance", "", "kanto_elite", "cobblemon:dragonite"),
        ),
        sequence = listOf(
            encounter("kanto", "blue_oaks_lab", 1, "rival", "blue", "Blue - Oak's Lab", 5),
            encounter("kanto", "blue_route_22_pre_brock", 2, "rival", "blue", "Blue - Route 22", 9),
            encounter("kanto", "brock", 3, "gym", "brock", "Brock", 14, "boulder_badge"),
            encounter("kanto", "blue_nugget_bridge", 4, "rival", "blue", "Blue - Nugget Bridge", 19),
            encounter("kanto", "misty", 5, "gym", "misty", "Misty", 21, "cascade_badge"),
            encounter("kanto", "blue_ss_anne", 6, "rival", "blue", "Blue - S.S. Anne", 24),
            encounter("kanto", "lt_surge", 7, "gym", "lt_surge", "Lt. Surge", 24, "thunder_badge"),
            encounter("kanto", "erika", 8, "gym", "erika", "Erika", 29, "rainbow_badge"),
            encounter("kanto", "blue_pokemon_tower", 9, "rival", "blue", "Blue - Pokemon Tower", 34),
            encounter("kanto", "blue_silph_co", 10, "rival", "blue", "Blue - Silph Co.", 40),
            encounter("kanto", "koga", 11, "gym", "koga", "Koga", 43, "soul_badge"),
            encounter("kanto", "sabrina", 12, "gym", "sabrina", "Sabrina", 43, "marsh_badge"),
            encounter("kanto", "blaine", 13, "gym", "blaine", "Blaine", 47, "volcano_badge"),
            encounter("kanto", "blue_route_22_final", 14, "rival", "blue", "Blue - Route 22 Final", 48),
            encounter("kanto", "giovanni", 15, "gym", "giovanni", "Giovanni", 50, "earth_badge"),
            encounter("kanto", "elite_lorelei", 16, "elite_four", "lorelei", "Elite Four - Lorelei", 54),
            encounter("kanto", "elite_bruno", 17, "elite_four", "bruno", "Elite Four - Bruno", 56),
            encounter("kanto", "elite_agatha", 18, "elite_four", "agatha", "Elite Four - Agatha", 58),
            encounter("kanto", "elite_lance", 19, "elite_four", "lance", "Elite Four - Lance", 60),
            encounter("kanto", "champion_blue", 20, "champion", "blue", "Champion Blue", 62),
        ),
    )

    private fun defaultJohtoLeague(): GymLeagueDefinition = league(
        id = "johto",
        displayName = "Johto League",
        generation = 2,
        region = "Johto",
        description = "The Johto badge record with Silver's rival checks and Lance as the final Skylands circuit wall.",
        trainers = listOf(
            trainer("silver", "Silver", "rival", "trainer_silver", "", "johto_rivals", "cobblemon:feraligatr"),
            trainer("falkner", "Falkner", "gym_leader", "gym_falkner", "zephyr_badge", "johto_gym_leaders", "cobblemon:pidgeot"),
            trainer("bugsy", "Bugsy", "gym_leader", "gym_bugsy", "hive_badge", "johto_gym_leaders", "cobblemon:scizor"),
            trainer("whitney", "Whitney", "gym_leader", "gym_whitney", "plain_badge", "johto_gym_leaders", "cobblemon:miltank"),
            trainer("morty", "Morty", "gym_leader", "gym_morty", "fog_badge", "johto_gym_leaders", "cobblemon:gengar"),
            trainer("chuck", "Chuck", "gym_leader", "gym_chuck", "storm_badge", "johto_gym_leaders", "cobblemon:poliwrath"),
            trainer("jasmine", "Jasmine", "gym_leader", "gym_jasmine", "mineral_badge", "johto_gym_leaders", "cobblemon:steelix"),
            trainer("pryce", "Pryce", "gym_leader", "gym_pryce", "glacier_badge", "johto_gym_leaders", "cobblemon:piloswine"),
            trainer("clair", "Clair", "gym_leader", "gym_clair", "rising_badge", "johto_gym_leaders", "cobblemon:kingdra"),
            trainer("will", "Will", "elite_four", "johto_elite_will", "", "johto_elite", "cobblemon:xatu"),
            trainer("johto_koga", "Koga", "elite_four", "johto_elite_koga", "", "johto_elite", "cobblemon:crobat"),
            trainer("johto_bruno", "Bruno", "elite_four", "johto_elite_bruno", "", "johto_elite", "cobblemon:machamp"),
            trainer("karen", "Karen", "elite_four", "johto_elite_karen", "", "johto_elite", "cobblemon:houndoom"),
            trainer("johto_lance", "Lance", "champion", "johto_champion_lance", "", "johto_champion", "cobblemon:dragonite"),
        ),
        sequence = listOf(
            encounter("johto", "silver_new_bark", 1, "rival", "silver", "Silver - New Bark", 5),
            encounter("johto", "falkner", 2, "gym", "falkner", "Falkner", 9, "zephyr_badge"),
            encounter("johto", "silver_azalea", 3, "rival", "silver", "Silver - Azalea", 16),
            encounter("johto", "bugsy", 4, "gym", "bugsy", "Bugsy", 16, "hive_badge"),
            encounter("johto", "whitney", 5, "gym", "whitney", "Whitney", 20, "plain_badge"),
            encounter("johto", "silver_burned_tower", 6, "rival", "silver", "Silver - Burned Tower", 22),
            encounter("johto", "morty", 7, "gym", "morty", "Morty", 25, "fog_badge"),
            encounter("johto", "chuck", 8, "gym", "chuck", "Chuck", 31, "storm_badge"),
            encounter("johto", "jasmine", 9, "gym", "jasmine", "Jasmine", 35, "mineral_badge"),
            encounter("johto", "pryce", 10, "gym", "pryce", "Pryce", 34, "glacier_badge"),
            encounter("johto", "clair", 11, "gym", "clair", "Clair", 40, "rising_badge"),
            encounter("johto", "silver_victory_road", 12, "rival", "silver", "Silver - Victory Road", 42),
            encounter("johto", "elite_will", 13, "elite_four", "will", "Elite Four - Will", 45),
            encounter("johto", "elite_koga", 14, "elite_four", "johto_koga", "Elite Four - Koga", 47),
            encounter("johto", "elite_bruno", 15, "elite_four", "johto_bruno", "Elite Four - Bruno", 49),
            encounter("johto", "elite_karen", 16, "elite_four", "karen", "Elite Four - Karen", 51),
            encounter("johto", "champion_lance", 17, "champion", "johto_lance", "Champion Lance", 55),
        ),
    )

    private fun defaultHoennLeague(): GymLeagueDefinition = league(
        id = "hoenn",
        displayName = "Hoenn League",
        generation = 3,
        region = "Hoenn",
        description = "The Hoenn badge record with May, Wally, Wallace, and Steven adapted for the CKDM Skylands stadium.",
        trainers = listOf(
            trainer("may", "May", "rival", "trainer_may", "", "hoenn_rivals", "cobblemon:blaziken"),
            trainer("wally", "Wally", "rival", "trainer_wally", "", "hoenn_rivals", "cobblemon:gardevoir"),
            trainer("roxanne", "Roxanne", "gym_leader", "gym_roxanne", "stone_badge", "hoenn_gym_leaders", "cobblemon:nosepass"),
            trainer("brawly", "Brawly", "gym_leader", "gym_brawly", "knuckle_badge", "hoenn_gym_leaders", "cobblemon:hariyama"),
            trainer("wattson", "Wattson", "gym_leader", "gym_wattson", "dynamo_badge", "hoenn_gym_leaders", "cobblemon:manectric"),
            trainer("flannery", "Flannery", "gym_leader", "gym_flannery", "heat_badge", "hoenn_gym_leaders", "cobblemon:torkoal"),
            trainer("norman", "Norman", "gym_leader", "gym_norman", "balance_badge", "hoenn_gym_leaders", "cobblemon:slaking"),
            trainer("winona", "Winona", "gym_leader", "gym_winona", "feather_badge", "hoenn_gym_leaders", "cobblemon:altaria"),
            trainer("tate_liza", "Tate & Liza", "gym_leader", "gym_tate_liza", "mind_badge", "hoenn_gym_leaders", "cobblemon:lunatone"),
            trainer("wallace", "Wallace", "gym_leader", "gym_wallace", "rain_badge", "hoenn_gym_leaders", "cobblemon:milotic"),
            trainer("sidney", "Sidney", "elite_four", "hoenn_elite_sidney", "", "hoenn_elite", "cobblemon:absol"),
            trainer("phoebe", "Phoebe", "elite_four", "hoenn_elite_phoebe", "", "hoenn_elite", "cobblemon:dusclops"),
            trainer("glacia", "Glacia", "elite_four", "hoenn_elite_glacia", "", "hoenn_elite", "cobblemon:walrein"),
            trainer("drake", "Drake", "elite_four", "hoenn_elite_drake", "", "hoenn_elite", "cobblemon:salamence"),
            trainer("steven", "Steven", "champion", "hoenn_champion_steven", "", "hoenn_champion", "cobblemon:metagross"),
        ),
        sequence = listOf(
            encounter("hoenn", "may_route_103", 1, "rival", "may", "May - Route 103", 5),
            encounter("hoenn", "roxanne", 2, "gym", "roxanne", "Roxanne", 15, "stone_badge"),
            encounter("hoenn", "brawly", 3, "gym", "brawly", "Brawly", 19, "knuckle_badge"),
            encounter("hoenn", "may_route_110", 4, "rival", "may", "May - Route 110", 20),
            encounter("hoenn", "wattson", 5, "gym", "wattson", "Wattson", 24, "dynamo_badge"),
            encounter("hoenn", "wally_mauville", 6, "rival", "wally", "Wally - Mauville", 26),
            encounter("hoenn", "flannery", 7, "gym", "flannery", "Flannery", 29, "heat_badge"),
            encounter("hoenn", "norman", 8, "gym", "norman", "Norman", 31, "balance_badge"),
            encounter("hoenn", "winona", 9, "gym", "winona", "Winona", 33, "feather_badge"),
            encounter("hoenn", "tate_liza", 10, "gym", "tate_liza", "Tate & Liza", 42, "mind_badge"),
            encounter("hoenn", "wallace", 11, "gym", "wallace", "Wallace", 43, "rain_badge"),
            encounter("hoenn", "wally_victory_road", 12, "rival", "wally", "Wally - Victory Road", 45),
            encounter("hoenn", "elite_sidney", 13, "elite_four", "sidney", "Elite Four - Sidney", 48),
            encounter("hoenn", "elite_phoebe", 14, "elite_four", "phoebe", "Elite Four - Phoebe", 50),
            encounter("hoenn", "elite_glacia", 15, "elite_four", "glacia", "Elite Four - Glacia", 52),
            encounter("hoenn", "elite_drake", 16, "elite_four", "drake", "Elite Four - Drake", 55),
            encounter("hoenn", "champion_steven", 17, "champion", "steven", "Champion Steven", 58),
        ),
    )

    private fun league(
        id: String,
        displayName: String,
        generation: Int,
        region: String,
        description: String,
        trainers: List<GymTrainerDefinition>,
        sequence: List<GymEncounterDefinition>,
    ): GymLeagueDefinition = GymLeagueDefinition(
        id = id,
        displayName = displayName,
        generation = generation,
        region = region,
        description = description,
        icon = "textures/gui/icons/trophy.png",
        stadiumArea = "main_stadium",
        activeOnly = true,
        starterMode = "story_only",
        chowfanNpcId = "prof_chowfan",
        defaults = GymLeagueDefaults(),
        trainers = trainers.toMutableList(),
        sequence = sequence.toMutableList(),
    ).normalized(id)

    private fun trainer(id: String, name: String, role: String, npcId: String, badge: String, spawnGroup: String, mainPokemon: String) =
        GymTrainerDefinition(id, name, role, npcId, badge, spawnGroup, mainPokemon)

    private fun encounter(leagueId: String, id: String, order: Int, kind: String, trainer: String, name: String, cap: Int, badge: String = "") =
        GymEncounterDefinition(
            id = id,
            order = order,
            kind = kind,
            trainer = trainer,
            displayName = name,
            badgeId = badge,
            levelCap = cap,
            teamRef = "rct_trainers/$leagueId/$id.json",
            required = true,
            globalUnlockNext = true,
            spawnDelayDays = 1,
        )

    private fun defaultNpcDefinitions(): List<NpcDefinition> =
        defaultLeagues().flatMap { league ->
            league.trainers.map { trainer ->
                val lore = trainerLore(league, trainer)
                NpcDefinition(
                    id = trainer.npcId,
                    name = trainer.name,
                    title = trainer.role.replace('_', ' ').replaceFirstChar { it.titlecase() },
                    skin = if (trainer.npcId == "trainer_blue") "gisketchs_chowkingdom_mod:npc/trainer_blue" else "",
                    mainPokemon = trainer.mainPokemon,
                    housing = NpcHousingDefinition(canMoveIn = false, requiresBed = false),
                    missions = NpcMissionsDefinition(enabled = false),
                    personality = NpcPersonalityDefinition(
                        llmPrompt = lore.prompt,
                        traits = lore.traits.toMutableList(),
                        speechStyle = lore.speechStyle,
                        catchphrases = lore.catchphrases.toMutableList(),
                    ),
                    npcInteractionMessages = mutableListOf(
                        "Comparing battle notes with {other}.",
                        "Talking through matchup reads with {other}.",
                        "Checking tournament records with {other}.",
                        "Debating clean switches with {other}.",
                    ),
                    schedule = stadiumSchedule(),
                )
            }
        }

    private fun stadiumSchedule(): NpcScheduleDefinition = NpcScheduleDefinition(
        activities = mutableListOf(NpcScheduleEntryDefinition(fromHour = 0, toHour = 24, activity = "pokemon_roam")),
    )

    private fun trainerLore(league: GymLeagueDefinition, trainer: GymTrainerDefinition): TrainerLore {
        val partner = trainer.mainPokemon.substringAfter(':', trainer.mainPokemon).replace('_', ' ')
        val region = league.region
        val role = trainer.role.replace('_', ' ')
        return when (trainer.id) {
            "blue" -> TrainerLore(
                "Blue is a sharp-tongued rival pulled into CKDM Skylands by Arceus for the hosted Kanto record circuit. He is already strong, but league rules make him choose lower-level roster Pokemon for early caps. He treats the floating stadium like his proving ground, not like old Kanto.",
                listOf("pokemon trainer", "rival", "competitive", "cocky", "observant", "Arceus-called Skylands league visitor"),
                "confident rival with quick jabs, battle reads, and grudging respect",
                listOf("Smell ya later.", "Try keeping up.", "Do not waste my time."),
            )
            "silver" -> TrainerLore(
                "Silver is an intense rival pulled into CKDM Skylands by Arceus for the hosted Johto record circuit. He is already strong, but league rules make him choose lower-level roster Pokemon for early caps. He treats kindness as suspicious, strength as proof, and every loss as something to grind into discipline.",
                listOf("pokemon trainer", "rival", "johto", "harsh", "driven", "Arceus-called Skylands league visitor"),
                "sharp rival voice with clipped pressure and reluctant growth",
                listOf("Do not slow me down.", "Weakness is a choice."),
            )
            "may" -> TrainerLore(
                "May is a bright Hoenn rival and field researcher pulled into CKDM Skylands by Arceus for the hosted Hoenn record circuit. She is already capable, but league rules make her choose lower-level roster Pokemon for early caps. She watches how Pokemon adapt to floating islands.",
                listOf("pokemon trainer", "rival", "hoenn", "field researcher", "friendly", "Arceus-called Skylands league visitor"),
                "warm rival researcher with upbeat tactical comments",
                listOf("Field notes and battle notes both count.", "Let's test this properly."),
            )
            "wally" -> TrainerLore(
                "Wally is a careful Hoenn rival pulled into CKDM Skylands by Arceus for the hosted Hoenn record circuit. He is stronger than he looks, but league rules make him choose lower-level roster Pokemon for early caps. He is polite, brave, and quietly determined.",
                listOf("pokemon trainer", "rival", "hoenn", "polite", "determined", "Arceus-called Skylands league visitor"),
                "gentle but determined rival with sincere confidence",
                listOf("I can do this.", "One more clean battle."),
            )
            else -> TrainerLore(
                "${trainer.name} is a $role pulled into Chow Kingdom's Skylands by Arceus to represent the imported $region record circuit. They know this is Skylands, not literal $region. They are already strong, but league rules make them choose lower-level roster Pokemon for early caps. Their signature partner is $partner.",
                listOf("pokemon trainer", trainer.role, region.lowercase(), "Arceus-called Skylands league visitor"),
                "${region.lowercase()} $role with practical battle focus and distinct partner pride",
                emptyList(),
            )
        }
    }

    private fun defaultTrainerJson(): Map<String, String> =
        defaultLeagues().flatMap { league ->
            league.sequence.map { encounter ->
                val trainer = league.trainer(encounter.trainer)
                "${league.id}/${encounter.id}.json" to trainerJson(encounter, GymLeagueText.stageAppropriateSpecies(trainer?.mainPokemon ?: "cobblemon:pikachu", encounter.levelCap))
            }
        }.toMap()

    private fun trainerJson(encounter: GymEncounterDefinition, species: String): String = """
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
              "species": "$species",
              "level": ${encounter.levelCap},
              "moveset": ["tackle"]
            }
          ]
        }
    """.trimIndent()
}

private data class TrainerLore(
    val prompt: String,
    val traits: List<String>,
    val speechStyle: String,
    val catchphrases: List<String>,
)
