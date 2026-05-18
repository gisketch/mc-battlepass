package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import dev.gisketch.chowkingdom.mobility.MobilityLicenseStore
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

object BattlepassPassRegistry {
    private const val MAX_LEVEL = 500
    private val passes: MutableMap<String, BattlepassPassDefinition> = linkedMapOf()

    val passDirectory: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("battlepass").resolve("passes")

    fun reload(): Int {
        ensureDefaultPasses()
        passes.clear()

        passFiles().forEach(::loadPass)

        ChowKingdomMod.LOGGER.info("Loaded {} battlepass definition(s)", passes.size)
        return passes.size
    }

    fun all(): Collection<BattlepassPassDefinition> = passes.values

    fun get(id: String): BattlepassPassDefinition? = passes[normalizeId(id)]

    fun knownIds(): String = passes.keys.joinToString(", ").ifBlank { "none" }

    fun replaceFirstDailyEvent(eventId: String, goals: List<Int>): BattlepassPassDefinition? = null

    private fun loadPass(path: Path) {
        try {
            val pass = TomlConfigIO.read(path, BattlepassPassDefinition::class.java, ::BattlepassPassDefinition)
            if (pass.id.isBlank()) {
                pass.id = path.nameWithoutExtension
            }

            val id = normalizeId(pass.id)
            if (id.isBlank()) return

            pass.id = id
            val hadDailyMissions = pass.dailyEvents.count != 0 || pass.dailyEvents.events.isNotEmpty()
            pass.dailyEvents.count = 0
            pass.dailyEvents.events.clear()
            if (hadDailyMissions) TomlConfigIO.write(path, pass)
            if (pass.displayName.isBlank()) {
                pass.displayName = id
            }
            passes[id] = pass
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load battlepass pass {}", path, exception)
        }
    }

    private fun ensureDefaultPasses() {
        passDirectory.createDirectories()
        writeDefault("cozy.toml", defaultCozyPass())
        writeDefault("combat.toml", defaultCombatPass())
    }

    private fun passFiles(): List<Path> {
        val paths = mutableListOf<Path>()
        Files.list(passDirectory).use { files ->
            files.forEach { path ->
                if (Files.isRegularFile(path) && path.fileName.toString().endsWith(".toml")) paths.add(path)
            }
        }
        return paths.sorted()
    }

    private fun writeDefault(fileName: String, pass: BattlepassPassDefinition) {
        val path = passDirectory.resolve(fileName)
        if (path.exists()) return
        TomlConfigIO.write(path, pass)
    }

    private fun defaultCozyPass(): BattlepassPassDefinition = BattlepassPassDefinition().apply {
        id = "cozy"
        displayName = "Cozy Pass"
        description = "Long-season cozy progression from farming, cooking, fishing, shipping, and Cobblemon care."
        titleTexture = "gisketchs_chowkingdom_mod:textures/gui/cozy_pass.png"
        titleTextureWidth = 1024
        titleTextureHeight = 230
        categories = mutableListOf("cozy", "season_1")
        permanentEvents = (
            listOf(
                mission("permanent_scan_pokedex", "cobblemon:pokedex_scanned", "Scan {goal} Pokedex Entries", listOf(25, 100, 250, 500, 750, 1000), listOf(75, 150, 300, 500, 750, 1200)),
            ) +
                generationScanMissions() +
                listOf(
                    mission("permanent_pokemon_friendship", "cobblemon:pokemon_friendship_maxed", "Max Friendship with {goal} Pokemon", listOf(1, 3, 6, 10, 20), listOf(100, 200, 350, 500, 850)),
                    mission("permanent_pokemon_rider", "cobblemon:pokemon_mount_traveled", "Travel {goal} Blocks on Pokemon", listOf(10000, 50000, 150000, 500000, 1000000), listOf(150, 400, 900, 1600, 2500)),
                    mission("permanent_pokemon_flight", "cobblemon:pokemon_mount_flying_traveled", "Travel {goal} Blocks on Flying Pokemon", listOf(10000, 50000, 150000, 400000), listOf(150, 400, 900, 1600)),
                    mission("permanent_pokemon_land_rider", "cobblemon:pokemon_mount_land_traveled", "Travel {goal} Blocks on Land Pokemon", listOf(10000, 50000, 150000, 400000), listOf(150, 400, 900, 1600)),
                    mission("permanent_crop_keeper", "minecraft:crop_harvested", "Harvest {goal} Crops", listOf(256, 1024, 4096, 16000), listOf(150, 400, 900, 1800)),
                    mission("permanent_quality_crop_keeper", "quality_food:gold_quality_crop_harvested", "Harvest {goal} Gold Quality Crops", listOf(64, 256, 1024, 4096), listOf(150, 400, 900, 1800)),
                    mission("permanent_fisher", "minecraft:fish_caught", "Catch {goal} Fish", listOf(50, 250, 1000, 3000), listOf(150, 400, 900, 1800)),
                    mission("permanent_animal_keeper", "minecraft:animal_bred", "Breed {goal} Animals", listOf(25, 100, 300, 1000), listOf(150, 400, 900, 1600)),
                    mission("permanent_villager_trader", "minecraft:villager_traded", "Trade with Villagers {goal} Times", listOf(25, 100, 300, 1000), listOf(150, 400, 900, 1600)),
                    mission("permanent_cooking_pot_meals", "farmersdelight:cooking_pot_meal_cooked", "Cook {goal} Cooking Pot Meals", listOf(25, 100, 300, 1000), listOf(200, 500, 1000, 2000)),
                    mission("permanent_feast_servings", "farmersdelight:feast_served", "Serve {goal} Feast Portions", listOf(10, 50, 150, 500), listOf(200, 600, 1200, 2200)),
                    mission("permanent_shipping_value", "gisketchs_chowkingdom_mod:shipping_bin_value_sold", "Ship {goal} Chowcoins Worth", listOf(10000, 50000, 200000, 750000), listOf(150, 500, 1200, 2500)),
                )
            ).toMutableList()
        weeklyEvents = weekly(
            mission("weekly_harvest_crops", "minecraft:crop_harvested", "Harvest {goal} Crops", listOf(384), listOf(220)),
            mission("weekly_gold_quality_crops", "quality_food:gold_quality_crop_harvested", "Harvest {goal} Gold Quality Crops", listOf(64), listOf(260)),
            mission("weekly_go_fishing", "minecraft:fish_caught", "Catch {goal} Fish", listOf(30), listOf(220)),
            mission("weekly_cooking_pot_meals", "farmersdelight:cooking_pot_meal_cooked", "Cook {goal} Cooking Pot Meals", listOf(16), listOf(240)),
            mission("weekly_cutting_board_outputs", "farmersdelight:cutting_board_outputs", "Make {goal} Cutting Board Outputs", listOf(48), listOf(220)),
            mission("weekly_feast_servings", "farmersdelight:feast_served", "Serve {goal} Feast Portions", listOf(8), listOf(300)),
            mission("weekly_breed_animals", "minecraft:animal_bred", "Breed {goal} Animals", listOf(20), listOf(220)),
            mission("weekly_ship_value", "gisketchs_chowkingdom_mod:shipping_bin_value_sold", "Ship {goal} Chowcoins Worth", listOf(25000), listOf(300)),
            mission("weekly_ship_quality_value", "gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold", "Ship {goal} Quality Chowcoins Worth", listOf(10000), listOf(320)),
            mission("weekly_pokemon_mount_travel", "cobblemon:pokemon_mount_traveled", "Travel {goal} Blocks on Pokemon", listOf(10000), listOf(220)),
            mission("weekly_farmer_meals_eaten", "farmersdelight:meal_eaten", "Eat {goal} Farmer's Delight Meals", listOf(12), listOf(200)),
        )
        progression = buildProgression(COZY_REWARDS, COZY_RELIC_LEVELS, COZY_EXTRA_REWARDS)
    }

    private fun defaultCombatPass(): BattlepassPassDefinition = BattlepassPassDefinition().apply {
        id = "combat"
        displayName = "Combat Pass"
        description = "Long-season combat progression from limited missions, exploration, and boss preparation."
        titleTexture = "gisketchs_chowkingdom_mod:textures/gui/combat_pass.png"
        titleTextureWidth = 1024
        titleTextureHeight = 215
        categories = mutableListOf("combat", "season_1")
        permanentEvents = (
            listOf(
                mission("permanent_pokemon_caught", "cobblemon:pokemon_caught", "Catch {goal} Pokemon", listOf(10, 50, 150, 400, 800), listOf(50, 100, 200, 400, 750)),
            ) +
                generationCatchMissions() +
                pokemonTypeCatchMissions() +
                listOf(
                    mission("permanent_monster_slayer", "minecraft:monster_killed", "Defeat {goal} Monsters", listOf(100, 500, 2000, 8000), listOf(200, 600, 1400, 3000)),
                    mission("permanent_zombie_slayer", "minecraft:entity_killed", "Defeat {goal} Zombies", listOf(50, 250, 750, 2500), listOf(150, 500, 1000, 2000), filters = mapOf("entity" to "minecraft:zombie")),
                    mission("permanent_skeleton_slayer", "minecraft:entity_killed", "Defeat {goal} Skeletons", listOf(50, 250, 750, 2500), listOf(150, 500, 1000, 2000), filters = mapOf("entity" to "minecraft:skeleton")),
                    mission("permanent_creeper_slayer", "minecraft:entity_killed", "Defeat {goal} Creepers", listOf(25, 100, 300, 1000), listOf(150, 500, 1000, 2200), filters = mapOf("entity" to "minecraft:creeper")),
                    mission("permanent_enderman_slayer", "minecraft:entity_killed", "Defeat {goal} Endermen", listOf(10, 50, 150, 500), listOf(150, 500, 1100, 2400), filters = mapOf("entity" to "minecraft:enderman")),
                    mission("permanent_nether_hunter", "minecraft:monster_killed", "Defeat {goal} Nether Monsters", listOf(50, 200, 750, 2500), listOf(200, 600, 1400, 3000), filters = mapOf("dimension" to "minecraft:the_nether")),
                    mission("permanent_combat_traveler", "minecraft:travel_on_foot", "Travel {goal} Blocks on Foot", listOf(25000, 100000, 500000, 1500000), listOf(150, 500, 1200, 2500)),
                )
            ).toMutableList()
        weeklyEvents = weekly(
            mission("weekly_hunt_mobs", "minecraft:monster_killed", "Defeat {goal} Monsters", listOf(100), listOf(240), rotationGroup = "combat:monster_general"),
            mission("weekly_hunt_zombies", "minecraft:entity_killed", "Defeat {goal} Zombies", listOf(30), listOf(220), filters = mapOf("entity" to "minecraft:zombie"), rotationGroup = "combat:monster_specific"),
            mission("weekly_hunt_skeletons", "minecraft:entity_killed", "Defeat {goal} Skeletons", listOf(30), listOf(220), filters = mapOf("entity" to "minecraft:skeleton"), rotationGroup = "combat:monster_specific"),
            mission("weekly_hunt_spiders", "minecraft:entity_killed", "Defeat {goal} Spiders", listOf(20), listOf(220), filters = mapOf("entity" to "minecraft:spider"), rotationGroup = "combat:monster_specific"),
            mission("weekly_hunt_creepers", "minecraft:entity_killed", "Defeat {goal} Creepers", listOf(12), listOf(260), filters = mapOf("entity" to "minecraft:creeper"), rotationGroup = "combat:monster_specific"),
            mission("weekly_hunt_endermen", "minecraft:entity_killed", "Defeat {goal} Endermen", listOf(8), listOf(300), filters = mapOf("entity" to "minecraft:enderman"), rotationGroup = "combat:monster_specific"),
            mission("weekly_combat_travel", "minecraft:travel_on_foot", "Travel {goal} Blocks on Foot", listOf(10000), listOf(220)),
            mission("weekly_catch_pokemon", "cobblemon:pokemon_caught", "Catch {goal} Pokemon", listOf(8), listOf(260), rotationGroup = "combat:pokemon_catch"),
            mission("weekly_catch_fire_type", "cobblemon:catch_fire_type", "Catch {goal} Fire-type Pokemon", listOf(4), listOf(260), rotationGroup = "combat:pokemon_catch"),
            mission("weekly_catch_water_type", "cobblemon:catch_water_type", "Catch {goal} Water-type Pokemon", listOf(4), listOf(260), rotationGroup = "combat:pokemon_catch"),
            mission("weekly_catch_electric_type", "cobblemon:catch_electric_type", "Catch {goal} Electric-type Pokemon", listOf(4), listOf(260), rotationGroup = "combat:pokemon_catch"),
        )
        progression = buildProgression(COMBAT_REWARDS, COMBAT_RELIC_LEVELS, COMBAT_EXTRA_REWARDS)
    }

    private fun buildProgression(
        rewards: RewardTables,
        relicLevels: Map<Int, String>,
        extraRewards: Map<Int, List<BattlepassRewardDefinition>> = emptyMap(),
    ): MutableList<BattlepassProgressionDefinition> =
        (1..MAX_LEVEL).map { level ->
            BattlepassProgressionDefinition().apply {
                xp = level * 100
                this.rewards = (listOf(rewardForLevel(level, rewards, relicLevels)) + extraRewards[level].orEmpty()).toMutableList()
            }
        }.toMutableList()

    private fun rewardForLevel(level: Int, rewards: RewardTables, relicLevels: Map<Int, String>): BattlepassRewardDefinition {
        relicLevels[level]?.let { pool -> return relicReward(pool) }
        return when {
            level in SPECIAL_LEVELS || level % 10 == 0 -> itemReward(rewards.prominent[(level - 1) % rewards.prominent.size], prominent = true, level = level)
            level % 5 == 0 -> itemReward(rewards.utility[(level - 1) % rewards.utility.size], level = level)
            else -> itemReward(rewards.normal[(level - 1) % rewards.normal.size], level = level)
        }
    }

    private fun itemReward(entry: RewardEntry, prominent: Boolean = false, level: Int): BattlepassRewardDefinition =
        BattlepassRewardDefinition().apply {
            type = "item"
            item = entry.item
            quantity = (entry.quantity + level / entry.scaleEvery).coerceAtMost(entry.maxQuantity)
            isProminent = prominent
        }

    private fun relicReward(pool: String): BattlepassRewardDefinition =
        BattlepassRewardDefinition().apply {
            type = "relic_token"
            item = tokenItemForPool(pool)
            quantity = 1
            isProminent = true
            data = mutableMapOf("pool" to pool)
        }

    private fun tokenItemForPool(pool: String): String = when (pool) {
        "common_cozy_relics" -> "${ChowKingdomMod.MOD_ID}:common_cozy_relic_token"
        "rare_cozy_relics" -> "${ChowKingdomMod.MOD_ID}:rare_cozy_relic_token"
        "common_combat_relics" -> "${ChowKingdomMod.MOD_ID}:common_combat_relic_token"
        "rare_combat_relics" -> "${ChowKingdomMod.MOD_ID}:rare_combat_relic_token"
        else -> "minecraft:air"
    }

    private fun fixedItemReward(itemId: String, quantity: Int = 1, prominent: Boolean = true): BattlepassRewardDefinition =
        BattlepassRewardDefinition().apply {
            type = "item"
            item = itemId
            this.quantity = quantity
            isProminent = prominent
        }

    private fun chowcoinReward(amount: Int): BattlepassRewardDefinition =
        BattlepassRewardDefinition().apply {
            type = "chowcoin"
            item = "minecraft:gold_ingot"
            quantity = amount
            isProminent = false
        }

    private fun ridingLicenseReward(): BattlepassRewardDefinition =
        BattlepassRewardDefinition().apply {
            type = "license"
            item = "minecraft:saddle"
            quantity = 1
            isProminent = true
            data = mutableMapOf("license" to MobilityLicenseStore.RIDING_LICENSE, "name" to "Riding License")
        }

    private fun withChowcoinRewards(base: Map<Int, List<BattlepassRewardDefinition>>): Map<Int, List<BattlepassRewardDefinition>> {
        val rewards = base.mapValuesTo(linkedMapOf()) { (_, value) -> value.toMutableList() }
        for (level in 25..MAX_LEVEL step 25) {
            rewards.getOrPut(level) { mutableListOf() } += chowcoinReward(chowcoinAmountForLevel(level))
        }
        return rewards
    }

    private fun chowcoinAmountForLevel(level: Int): Int = when {
        level <= 100 -> 250
        level <= 250 -> 500
        level <= 400 -> 1000
        else -> 2000
    }

    private fun generationScanMissions(): List<BattlepassXpEventDefinition> =
        GENERATION_MISSIONS.map { generation ->
            mission(
                "permanent_scan_${generation.id}_pokemon",
                "cobblemon:scan_${generation.id}_pokemon",
                "Scan {goal} ${generation.displayName} Pokemon",
                generation.scanGoals,
                generation.scanXp,
            )
        }

    private fun generationCatchMissions(): List<BattlepassXpEventDefinition> =
        GENERATION_MISSIONS.map { generation ->
            mission(
                "permanent_catch_${generation.id}_pokemon",
                "cobblemon:catch_${generation.id}_pokemon",
                "Catch {goal} ${generation.displayName} Pokemon",
                generation.catchGoals,
                generation.catchXp,
            )
        }

    private fun pokemonTypeCatchMissions(): List<BattlepassXpEventDefinition> =
        POKEMON_TYPES.map { type ->
            val displayName = type.replaceFirstChar { character -> character.uppercase(Locale.ROOT) }
            mission(
                "permanent_catch_${type}_type",
                "cobblemon:catch_${type}_type",
                "Catch {goal} $displayName-type Pokemon",
                listOf(5, 25, 75, 150),
                listOf(25, 75, 150, 250),
            )
        }

    private fun mission(
        id: String,
        event: String,
        description: String,
        goals: List<Int>,
        xp: List<Int>,
        filters: Map<String, String> = emptyMap(),
        rotationGroup: String = "",
    ): BattlepassXpEventDefinition =
        BattlepassXpEventDefinition().apply {
            this.id = id
            this.event = event
            type = "progressive"
            eventDesc = description
            progress = 0
            progressGoals = goals.toMutableList()
            progressXp = xp.toMutableList()
            this.filters = filters.toMutableMap()
            this.rotationGroup = rotationGroup
        }

    private fun weekly(vararg events: BattlepassXpEventDefinition): BattlepassRotatingMissionDefinition =
        BattlepassRotatingMissionDefinition().apply {
            count = 5
            timeZone = "GMT+8"
            resetHour = 5
            resetMinute = 0
            resetOnDay = "Sunday"
            this.events = events.toMutableList()
        }

    private fun normalizeId(id: String): String = id.trim().lowercase(Locale.ROOT)

    private data class GenerationMission(
        val id: String,
        val displayName: String,
        val scanGoals: List<Int>,
        val scanXp: List<Int>,
        val catchGoals: List<Int>,
        val catchXp: List<Int>,
    )

    private data class RewardEntry(val item: String, val quantity: Int, val maxQuantity: Int, val scaleEvery: Int = 200)
    private data class RewardTables(val normal: List<RewardEntry>, val utility: List<RewardEntry>, val prominent: List<RewardEntry>)

    private val SPECIAL_LEVELS = setOf(25, 50, 75, 100, 150, 200, 250, 300, 350, 400, 500)

    private val POKEMON_TYPES = listOf(
        "normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
        "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy",
    )

    private val GENERATION_MISSIONS = listOf(
        GenerationMission("kanto", "Kanto", listOf(25, 75, 151), listOf(100, 250, 400), listOf(10, 50, 100, 151), listOf(50, 100, 200, 400)),
        GenerationMission("johto", "Johto", listOf(25, 75, 100), listOf(100, 225, 300), listOf(10, 40, 75, 100), listOf(50, 100, 175, 300)),
        GenerationMission("hoenn", "Hoenn", listOf(30, 90, 135), listOf(100, 250, 400), listOf(10, 50, 100, 135), listOf(50, 100, 200, 400)),
        GenerationMission("sinnoh", "Sinnoh", listOf(25, 75, 107), listOf(100, 225, 350), listOf(10, 40, 75, 107), listOf(50, 100, 175, 350)),
        GenerationMission("unova", "Unova", listOf(30, 100, 156), listOf(100, 300, 500), listOf(10, 50, 100, 156), listOf(50, 125, 250, 500)),
        GenerationMission("kalos", "Kalos", listOf(20, 50, 72), listOf(100, 200, 300), listOf(10, 30, 50, 72), listOf(50, 100, 150, 250)),
        GenerationMission("alola", "Alola", listOf(25, 60, 88), listOf(100, 225, 350), listOf(10, 35, 60, 88), listOf(50, 100, 175, 300)),
        GenerationMission("galar", "Galar", listOf(25, 70, 96), listOf(100, 250, 400), listOf(10, 40, 70, 96), listOf(50, 100, 175, 350)),
        GenerationMission("paldea", "Paldea", listOf(30, 80, 120), listOf(100, 250, 450), listOf(10, 50, 90, 120), listOf(50, 100, 200, 400)),
    )

    private val COZY_RELIC_LEVELS = mapOf(
        50 to "common_cozy_relics",
        100 to "rare_cozy_relics",
        200 to "common_cozy_relics",
        350 to "common_cozy_relics",
        500 to "rare_cozy_relics",
    )

    private val COMBAT_RELIC_LEVELS = mapOf(
        50 to "common_combat_relics",
        100 to "rare_combat_relics",
        200 to "common_combat_relics",
        350 to "common_combat_relics",
        500 to "rare_combat_relics",
    )

    private val COZY_EXTRA_REWARDS = withChowcoinRewards(mapOf(
        1 to listOf(fixedItemReward("minecraft:torch", 16, prominent = false)),
        3 to listOf(fixedItemReward("paraglider:paraglider")),
        10 to listOf(fixedItemReward("cobblemon:poke_bait", 8)),
        45 to listOf(ridingLicenseReward()),
        500 to listOf(fixedItemReward("minecraft:elytra")),
    ))

    private val COMBAT_EXTRA_REWARDS = withChowcoinRewards(mapOf(
        1 to listOf(fixedItemReward("minecraft:arrow", 32, prominent = false)),
        8 to listOf(fixedItemReward("sophisticatedbackpacks:backpack")),
        28 to listOf(fixedItemReward("sophisticatedbackpacks:pickup_upgrade")),
        64 to listOf(fixedItemReward("sophisticatedbackpacks:filter_upgrade")),
        96 to listOf(fixedItemReward("sophisticatedbackpacks:deposit_upgrade")),
        128 to listOf(fixedItemReward("sophisticatedbackpacks:refill_upgrade")),
        165 to listOf(fixedItemReward("sophisticatedbackpacks:restock_upgrade")),
        225 to listOf(fixedItemReward("sophisticatedbackpacks:crafting_upgrade")),
        325 to listOf(fixedItemReward("sophisticatedbackpacks:jukebox_upgrade")),
    ))

    private val COZY_REWARDS = RewardTables(
        normal = listOf(
            RewardEntry("minecraft:bread", 2, 16),
            RewardEntry("minecraft:wheat_seeds", 4, 32),
            RewardEntry("minecraft:carrot", 3, 24),
            RewardEntry("minecraft:potato", 3, 24),
            RewardEntry("minecraft:beetroot_seeds", 4, 32),
            RewardEntry("minecraft:sweet_berries", 3, 24),
            RewardEntry("minecraft:apple", 2, 16),
            RewardEntry("minecraft:kelp", 6, 48),
            RewardEntry("minecraft:sugar_cane", 4, 32),
            RewardEntry("minecraft:cocoa_beans", 3, 24),
            RewardEntry("cobblemon:red_apricorn_seed", 1, 6, 100),
            RewardEntry("cobblemon:blue_apricorn_seed", 1, 6, 100),
            RewardEntry("cobblemon:green_apricorn_seed", 1, 6, 100),
        ),
        utility = listOf(
            RewardEntry("minecraft:bread", 4, 32),
            RewardEntry("minecraft:string", 6, 48),
            RewardEntry("minecraft:bowl", 4, 32),
            RewardEntry("minecraft:cookie", 6, 48),
            RewardEntry("minecraft:torch", 8, 64),
            RewardEntry("minecraft:lantern", 1, 8),
            RewardEntry("minecraft:bone_meal", 8, 64),
            RewardEntry("minecraft:paper", 8, 64),
            RewardEntry("cobblemon:poke_bait", 2, 16),
            RewardEntry("cobblemon:oran_berry", 3, 24),
            RewardEntry("create:andesite_alloy", 2, 16),
        ),
        prominent = listOf(
            RewardEntry("minecraft:fishing_rod", 1, 1, 10000),
            RewardEntry("minecraft:campfire", 1, 4),
            RewardEntry("minecraft:compass", 1, 4),
            RewardEntry("minecraft:name_tag", 1, 4),
            RewardEntry("minecraft:saddle", 1, 2, 10000),
            RewardEntry("farmersdelight:cooking_pot", 1, 2, 10000),
            RewardEntry("farmersdelight:skillet", 1, 2, 10000),
            RewardEntry("farmersdelight:cutting_board", 1, 2, 10000),
            RewardEntry("create:andesite_alloy", 4, 24),
            RewardEntry("cobblemon:poke_ball", 3, 24),
            RewardEntry("cobblemon:poke_bait", 6, 32),
        ),
    )

    private val COMBAT_REWARDS = RewardTables(
        normal = listOf(
            RewardEntry("minecraft:bread", 2, 16),
            RewardEntry("minecraft:arrow", 8, 64),
            RewardEntry("minecraft:torch", 8, 64),
            RewardEntry("minecraft:bread", 3, 24),
            RewardEntry("minecraft:coal", 4, 32),
            RewardEntry("minecraft:bone", 4, 32),
            RewardEntry("minecraft:string", 4, 32),
            RewardEntry("minecraft:flint", 4, 32),
            RewardEntry("minecraft:leather", 3, 24),
        ),
        utility = listOf(
            RewardEntry("minecraft:spectral_arrow", 6, 48),
            RewardEntry("minecraft:iron_ingot", 3, 24),
            RewardEntry("minecraft:gold_ingot", 2, 18),
            RewardEntry("minecraft:lapis_lazuli", 8, 64),
            RewardEntry("minecraft:redstone", 8, 64),
            RewardEntry("minecraft:experience_bottle", 2, 16),
            RewardEntry("minecraft:cooked_beef", 3, 24),
            RewardEntry("minecraft:ender_pearl", 1, 8),
            RewardEntry("minecraft:golden_carrot", 2, 16),
        ),
        prominent = listOf(
            RewardEntry("minecraft:shield", 1, 2, 10000),
            RewardEntry("minecraft:bow", 1, 2, 10000),
            RewardEntry("minecraft:crossbow", 1, 2, 10000),
            RewardEntry("minecraft:iron_ingot", 8, 48),
            RewardEntry("minecraft:gold_ingot", 6, 36),
            RewardEntry("minecraft:amethyst_shard", 8, 48),
            RewardEntry("minecraft:experience_bottle", 4, 24),
            RewardEntry("minecraft:ender_pearl", 2, 12),
            RewardEntry("minecraft:golden_apple", 1, 3, 500),
            RewardEntry("minecraft:diamond", 1, 4, 500),
        ),
    )
}
