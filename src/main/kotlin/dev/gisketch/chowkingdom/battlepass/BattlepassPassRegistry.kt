package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
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
        permanentEvents = mutableListOf(
            mission("permanent_scan_pokedex", "cobblemon:pokedex_scanned", "Scan {goal} Pokedex Entries", listOf(25, 100, 250, 500), listOf(200, 500, 1000, 2000)),
            mission("permanent_crop_keeper", "minecraft:crop_harvested", "Harvest {goal} Crops", listOf(256, 1024, 4096), listOf(250, 700, 1500)),
            mission("permanent_quality_crop_keeper", "quality_food:gold_quality_crop_harvested", "Harvest {goal} Gold Quality Crops", listOf(64, 256, 1024), listOf(300, 800, 1800)),
            mission("permanent_fisher", "minecraft:fish_caught", "Catch {goal} Fish", listOf(50, 250, 1000), listOf(250, 700, 1500)),
            mission("permanent_shipping_value", "gisketchs_chowkingdom_mod:shipping_bin_value_sold", "Ship {goal} Chowcoins Worth", listOf(10000, 50000, 200000), listOf(300, 800, 1800)),
            mission("permanent_animal_keeper", "minecraft:animal_bred", "Breed {goal} Animals", listOf(25, 100, 300), listOf(200, 600, 1200)),
            mission("permanent_traveler", "minecraft:blocks_traveled", "Travel {goal} Blocks", listOf(5000, 25000, 100000), listOf(200, 600, 1500)),
        )
        weeklyEvents = weekly(
            mission("weekly_harvest_crops", "minecraft:crop_harvested", "Harvest {goal} Crops", listOf(512), listOf(220)),
            mission("weekly_go_fishing", "minecraft:fish_caught", "Catch {goal} Fish", listOf(35), listOf(220)),
            mission("weekly_cooking_pot_meals", "farmersdelight:cooking_pot_meal_cooked", "Cook {goal} Cooking Pot Meals", listOf(25), listOf(260)),
            mission("weekly_ship_value", "gisketchs_chowkingdom_mod:shipping_bin_value_sold", "Ship {goal} Chowcoins Worth", listOf(50000), listOf(400)),
            mission("weekly_friendship_starter", "cobblemon:max_friendship_starter_pokemon", "Max Friendship with {goal} Starter Pokemon", listOf(1), listOf(600)),
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
        permanentEvents = mutableListOf(
            mission("permanent_monster_slayer", "minecraft:monster_killed", "Defeat {goal} Monsters", listOf(100, 500, 2000), listOf(250, 800, 2000)),
            mission("permanent_skeleton_slayer", "minecraft:skeleton_killed", "Defeat {goal} Skeletons", listOf(50, 250, 1000), listOf(200, 700, 1800)),
            mission("permanent_zombie_slayer", "minecraft:zombie_killed", "Defeat {goal} Zombies", listOf(50, 250, 1000), listOf(200, 700, 1800)),
            mission("permanent_combat_traveler", "minecraft:blocks_traveled", "Travel {goal} Blocks", listOf(5000, 25000, 100000), listOf(200, 600, 1500)),
            mission("permanent_starter_training", "cobblemon:max_friendship_starter_pokemon", "Train {goal} Starter Pokemon to Max Friendship", listOf(1, 3), listOf(500, 1200)),
        )
        weeklyEvents = weekly(
            mission("weekly_hunt_mobs", "minecraft:monster_killed", "Defeat {goal} Monsters", listOf(150), listOf(260)),
            mission("weekly_hunt_skeletons", "minecraft:skeleton_killed", "Defeat {goal} Skeletons", listOf(40), listOf(220)),
            mission("weekly_hunt_zombies", "minecraft:zombie_killed", "Defeat {goal} Zombies", listOf(40), listOf(220)),
            mission("weekly_combat_travel", "minecraft:blocks_traveled", "Travel {goal} Blocks", listOf(15000), listOf(260)),
            mission("weekly_catch_starter", "cobblemon:catch_starter_pokemon", "Catch {goal} Starter Pokemon", listOf(1), listOf(600)),
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

    private fun mission(id: String, event: String, description: String, goals: List<Int>, xp: List<Int>): BattlepassXpEventDefinition =
        BattlepassXpEventDefinition().apply {
            this.id = id
            this.event = event
            type = "progressive"
            eventDesc = description
            progress = 0
            progressGoals = goals.toMutableList()
            progressXp = xp.toMutableList()
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

    private data class RewardEntry(val item: String, val quantity: Int, val maxQuantity: Int, val scaleEvery: Int = 200)
    private data class RewardTables(val normal: List<RewardEntry>, val utility: List<RewardEntry>, val prominent: List<RewardEntry>)

    private val SPECIAL_LEVELS = setOf(25, 50, 75, 100, 150, 200, 250, 300, 350, 400, 500)

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
