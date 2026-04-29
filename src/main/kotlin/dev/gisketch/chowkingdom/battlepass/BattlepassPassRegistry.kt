package dev.gisketch.chowkingdom.battlepass

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

object BattlepassPassRegistry {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val passes: MutableMap<String, BattlepassPassDefinition> = linkedMapOf()

    val passDirectory: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("battlepass").resolve("passes")

    fun reload(): Int {
        ensureDefaultPasses()
        passes.clear()

        Files.list(passDirectory).use { files ->
            files
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .sorted()
                .forEach(::loadPass)
        }

        ChowKingdomMod.LOGGER.info("Loaded {} battlepass definition(s)", passes.size)
        return passes.size
    }

    fun all(): Collection<BattlepassPassDefinition> = passes.values

    fun get(id: String): BattlepassPassDefinition? = passes[normalizeId(id)]

    fun knownIds(): String = passes.keys.joinToString(", ").ifBlank { "none" }

    private fun loadPass(path: Path) {
        try {
            val pass = path.bufferedReader().use { reader -> gson.fromJson(reader, BattlepassPassDefinition::class.java) }
            if (pass.id.isBlank()) {
                pass.id = path.nameWithoutExtension
            }

            val id = normalizeId(pass.id)
            if (id.isBlank()) return

            pass.id = id
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
        writeDefault("cozy.json", COZY_PASS)
        writeDefault("combat.json", COMBAT_PASS)
    }

    private fun writeDefault(fileName: String, content: String) {
        val path = passDirectory.resolve(fileName)
        if (path.exists()) return
        path.bufferedWriter().use { writer -> writer.write(content.trimIndent()) }
    }

    private fun normalizeId(id: String): String = id.trim().lowercase(Locale.ROOT)

    private const val COZY_PASS = """
        {
          "id": "cozy",
          "displayName": "Cozy Pass",
          "description": "Progress from cozy tasks and peaceful rewards.",
          "titleTexture": "gisketchs_chowkingdom_mod:textures/gui/cozy_pass.png",
          "titleTextureWidth": 1024,
          "titleTextureHeight": 230,
          "categories": ["cozy", "season_1"],
          "permanent_events": [
            {
              "id": "permanent_scan_pokedex",
              "event": "cobblemon:pokedex_scanned",
              "type": "progressive",
              "event_desc": "Scan {goal} Pokedex Entries",
              "progress": 0,
              "progress_goals": [25, 50, 100],
              "progress_xp": [300, 400, 800]
            },
            {
              "id": "permanent_monster_slayer",
              "event": "minecraft:monster_killed",
              "type": "progressive",
              "event_desc": "Defeat {goal} Monsters",
              "progress": 0,
              "progress_goals": [100, 250, 500],
              "progress_xp": [300, 600, 1000]
            },
            {
              "id": "permanent_crop_keeper",
              "event": "minecraft:crop_harvested",
              "type": "progressive",
              "event_desc": "Harvest {goal} Crops",
              "progress": 0,
              "progress_goals": [128, 512, 1024],
              "progress_xp": [250, 500, 900]
            },
            {
              "id": "permanent_quality_crop_keeper",
              "event": "quality_food:quality_crop_harvested",
              "type": "progressive",
              "event_desc": "Harvest {goal} Quality Crops",
              "progress": 0,
              "progress_goals": [32, 128, 256],
              "progress_xp": [300, 600, 1000]
            },
            {
              "id": "permanent_shipping_value",
              "event": "gisketchs_chowkingdom_mod:shipping_bin_value_sold",
              "type": "progressive",
              "event_desc": "Ship {goal} Chowcoins Worth",
              "progress": 0,
              "progress_goals": [10000, 50000, 100000],
              "progress_xp": [300, 700, 1200]
            },
            {
              "id": "permanent_animal_keeper",
              "event": "minecraft:animal_bred",
              "type": "progressive",
              "event_desc": "Breed {goal} Animals",
              "progress": 0,
              "progress_goals": [25, 75, 150],
              "progress_xp": [250, 450, 850]
            },
            {
              "id": "permanent_traveler",
              "event": "minecraft:blocks_traveled",
              "type": "progressive",
              "event_desc": "Travel {goal} Blocks",
              "progress": 0,
              "progress_goals": [1000, 5000, 10000],
              "progress_xp": [250, 600, 1200]
            }
          ],
          "daily_events": {
            "count": 3,
            "time_zone": "GMT+8",
            "reset_hour": 5,
            "reset_minute": 0,
            "events": [
              {
                "id": "daily_hunt_mobs",
                "event": "minecraft:monster_killed",
                "type": "repeating",
                "event_desc": "Hunt Monsters",
                "xp": 8,
                "xp_cap": 50
              },
              {
                "id": "daily_catch_fire_type",
                "event": "cobblemon:catch_fire_type",
                "type": "repeating",
                "event_desc": "Catch Fire Pokemon",
                "xp": 5,
                "xp_cap": 50
              },
              {
                "id": "daily_harvest_crops",
                "event": "minecraft:crop_harvested",
                "type": "repeating",
                "event_desc": "Harvest {goal} Crops",
                "xp": 3,
                "xp_cap": 50
              },
              {
                "id": "daily_harvest_quality_crops",
                "event": "quality_food:quality_crop_harvested",
                "type": "progressive",
                "event_desc": "Harvest {goal} Quality Crops",
                "progress": 0,
                "progress_goals": [10],
                "progress_xp": [80]
              },
              {
                "id": "daily_cook_quality_food",
                "event": "quality_food:quality_food_cooked",
                "type": "progressive",
                "event_desc": "Cook {goal} Quality Foods",
                "progress": 0,
                "progress_goals": [10],
                "progress_xp": [80]
              },
              {
                "id": "daily_ship_quality_food",
                "event": "gisketchs_chowkingdom_mod:shipping_bin_quality_food_sold",
                "type": "progressive",
                "event_desc": "Ship {goal} Quality Foods",
                "progress": 0,
                "progress_goals": [16],
                "progress_xp": [100]
              },
              {
                "id": "daily_ship_iron_quality_food",
                "event": "gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold",
                "type": "progressive",
                "event_desc": "Ship {goal} Iron Quality Foods",
                "progress": 0,
                "progress_goals": [8],
                "progress_xp": [80]
              },
              {
                "id": "daily_ship_gold_quality_food",
                "event": "gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold",
                "type": "progressive",
                "event_desc": "Ship {goal} Gold Quality Foods",
                "progress": 0,
                "progress_goals": [8],
                "progress_xp": [100]
              },
              {
                "id": "daily_ship_diamond_quality_food",
                "event": "gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold",
                "type": "progressive",
                "event_desc": "Ship {goal} Diamond Quality Foods",
                "progress": 0,
                "progress_goals": [4],
                "progress_xp": [120]
              },
              {
                "id": "daily_ship_value",
                "event": "gisketchs_chowkingdom_mod:shipping_bin_value_sold",
                "type": "progressive",
                "event_desc": "Ship {goal} Chowcoins Worth",
                "progress": 0,
                "progress_goals": [5000],
                "progress_xp": [120]
              },
              {
                "id": "daily_walk_blocks",
                "event": "minecraft:blocks_traveled",
                "type": "repeating",
                "event_desc": "Travel {goal} Blocks",
                "xp": 1,
                "xp_cap": 50
              },
              {
                "id": "daily_breed_animals",
                "event": "minecraft:animal_bred",
                "type": "repeating",
                "event_desc": "Breed {goal} Animals",
                "xp": 12,
                "xp_cap": 50
              },
              {
                "id": "daily_trade_villagers",
                "event": "minecraft:villager_traded",
                "type": "repeating",
                "event_desc": "Trade with Villagers",
                "xp": 10,
                "xp_cap": 50
              },
              {
                "id": "daily_go_fishing",
                "event": "minecraft:fish_caught",
                "type": "repeating",
                "event_desc": "Catch Fish",
                "xp": 10,
                "xp_cap": 50
              },
              {
                "id": "daily_send_out_starter",
                "event": "cobblemon:send_out_starter_pokemon",
                "type": "repeating",
                "event_desc": "Send Out Starters",
                "xp": 12,
                "xp_cap": 50
              },
              {
                "id": "daily_catch_legendary",
                "event": "cobblemon:catch_legendary_pokemon",
                "type": "repeating",
                "event_desc": "Catch Legendary Pokemon",
                "xp": 50,
                "xp_cap": 50
              },
              {
                "id": "daily_max_friendship_fairy",
                "event": "cobblemon:max_friendship_fairy_type",
                "type": "repeating",
                "event_desc": "Max Fairy Friendship",
                "xp": 30,
                "xp_cap": 50
              }
            ]
          },
          "weekly_events": {
            "count": 5,
            "time_zone": "GMT+8",
            "reset_hour": 5,
            "reset_minute": 0,
            "reset_on_day": "Sunday",
            "events": [
              { "id": "weekly_harvest_quality_crops", "event": "quality_food:quality_crop_harvested", "type": "progressive", "event_desc": "Harvest {goal} Quality Crops", "progress": 0, "progress_goals": [64], "progress_xp": [250] },
              { "id": "weekly_cook_quality_food", "event": "quality_food:quality_food_cooked", "type": "progressive", "event_desc": "Cook {goal} Quality Foods", "progress": 0, "progress_goals": [64], "progress_xp": [250] },
              { "id": "weekly_ship_quality_food", "event": "gisketchs_chowkingdom_mod:shipping_bin_quality_food_sold", "type": "progressive", "event_desc": "Ship {goal} Quality Foods", "progress": 0, "progress_goals": [128], "progress_xp": [300] },
              { "id": "weekly_ship_iron_quality_food", "event": "gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold", "type": "progressive", "event_desc": "Ship {goal} Iron Quality Foods", "progress": 0, "progress_goals": [32], "progress_xp": [250] },
              { "id": "weekly_ship_gold_quality_food", "event": "gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold", "type": "progressive", "event_desc": "Ship {goal} Gold Quality Foods", "progress": 0, "progress_goals": [32], "progress_xp": [300] },
              { "id": "weekly_ship_diamond_quality_food", "event": "gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold", "type": "progressive", "event_desc": "Ship {goal} Diamond Quality Foods", "progress": 0, "progress_goals": [16], "progress_xp": [350] },
              { "id": "weekly_ship_value", "event": "gisketchs_chowkingdom_mod:shipping_bin_value_sold", "type": "progressive", "event_desc": "Ship {goal} Chowcoins Worth", "progress": 0, "progress_goals": [50000], "progress_xp": [350] },
              { "id": "weekly_catch_normal_type", "event": "cobblemon:catch_normal_type", "type": "progressive", "event_desc": "Catch {goal} Normal Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_fire_type", "event": "cobblemon:catch_fire_type", "type": "progressive", "event_desc": "Catch {goal} Fire Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_water_type", "event": "cobblemon:catch_water_type", "type": "progressive", "event_desc": "Catch {goal} Water Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_grass_type", "event": "cobblemon:catch_grass_type", "type": "progressive", "event_desc": "Catch {goal} Grass Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_electric_type", "event": "cobblemon:catch_electric_type", "type": "progressive", "event_desc": "Catch {goal} Electric Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_ice_type", "event": "cobblemon:catch_ice_type", "type": "progressive", "event_desc": "Catch {goal} Ice Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_fighting_type", "event": "cobblemon:catch_fighting_type", "type": "progressive", "event_desc": "Catch {goal} Fighting Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_poison_type", "event": "cobblemon:catch_poison_type", "type": "progressive", "event_desc": "Catch {goal} Poison Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_ground_type", "event": "cobblemon:catch_ground_type", "type": "progressive", "event_desc": "Catch {goal} Ground Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_flying_type", "event": "cobblemon:catch_flying_type", "type": "progressive", "event_desc": "Catch {goal} Flying Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_psychic_type", "event": "cobblemon:catch_psychic_type", "type": "progressive", "event_desc": "Catch {goal} Psychic Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_bug_type", "event": "cobblemon:catch_bug_type", "type": "progressive", "event_desc": "Catch {goal} Bug Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_rock_type", "event": "cobblemon:catch_rock_type", "type": "progressive", "event_desc": "Catch {goal} Rock Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_ghost_type", "event": "cobblemon:catch_ghost_type", "type": "progressive", "event_desc": "Catch {goal} Ghost Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_dragon_type", "event": "cobblemon:catch_dragon_type", "type": "progressive", "event_desc": "Catch {goal} Dragon Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_dark_type", "event": "cobblemon:catch_dark_type", "type": "progressive", "event_desc": "Catch {goal} Dark Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_steel_type", "event": "cobblemon:catch_steel_type", "type": "progressive", "event_desc": "Catch {goal} Steel Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_fairy_type", "event": "cobblemon:catch_fairy_type", "type": "progressive", "event_desc": "Catch {goal} Fairy Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [250] },
              { "id": "weekly_catch_legendary", "event": "cobblemon:catch_legendary_pokemon", "type": "progressive", "event_desc": "Catch {goal} Legendary Pokemon", "progress": 0, "progress_goals": [10, 20, 30], "progress_xp": [900, 1900, 3000] },
              { "id": "weekly_catch_mythical", "event": "cobblemon:catch_mythical_pokemon", "type": "progressive", "event_desc": "Catch {goal} Mythical Pokemon", "progress": 0, "progress_goals": [1], "progress_xp": [900] },
              { "id": "weekly_catch_starter", "event": "cobblemon:catch_starter_pokemon", "type": "progressive", "event_desc": "Catch {goal} Starter Pokemon", "progress": 0, "progress_goals": [1], "progress_xp": [600] },
              { "id": "weekly_friendship_normal_type", "event": "cobblemon:max_friendship_normal_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Normal Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_fire_type", "event": "cobblemon:max_friendship_fire_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Fire Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_water_type", "event": "cobblemon:max_friendship_water_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Water Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_grass_type", "event": "cobblemon:max_friendship_grass_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Grass Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_electric_type", "event": "cobblemon:max_friendship_electric_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Electric Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_ice_type", "event": "cobblemon:max_friendship_ice_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Ice Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_fighting_type", "event": "cobblemon:max_friendship_fighting_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Fighting Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_poison_type", "event": "cobblemon:max_friendship_poison_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Poison Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_ground_type", "event": "cobblemon:max_friendship_ground_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Ground Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_flying_type", "event": "cobblemon:max_friendship_flying_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Flying Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_psychic_type", "event": "cobblemon:max_friendship_psychic_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Psychic Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_bug_type", "event": "cobblemon:max_friendship_bug_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Bug Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_rock_type", "event": "cobblemon:max_friendship_rock_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Rock Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_ghost_type", "event": "cobblemon:max_friendship_ghost_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Ghost Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_dragon_type", "event": "cobblemon:max_friendship_dragon_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Dragon Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_dark_type", "event": "cobblemon:max_friendship_dark_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Dark Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_steel_type", "event": "cobblemon:max_friendship_steel_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Steel Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_fairy_type", "event": "cobblemon:max_friendship_fairy_type", "type": "progressive", "event_desc": "Max Friendship with {goal} Fairy Pokemon", "progress": 0, "progress_goals": [10], "progress_xp": [300] },
              { "id": "weekly_friendship_legendary", "event": "cobblemon:max_friendship_legendary_pokemon", "type": "progressive", "event_desc": "Max Friendship with {goal} Legendary Pokemon", "progress": 0, "progress_goals": [1], "progress_xp": [900] },
              { "id": "weekly_friendship_mythical", "event": "cobblemon:max_friendship_mythical_pokemon", "type": "progressive", "event_desc": "Max Friendship with {goal} Mythical Pokemon", "progress": 0, "progress_goals": [1], "progress_xp": [900] },
              { "id": "weekly_friendship_starter", "event": "cobblemon:max_friendship_starter_pokemon", "type": "progressive", "event_desc": "Max Friendship with {goal} Starter Pokemon", "progress": 0, "progress_goals": [1], "progress_xp": [600] }
            ]
          },
          "progression": [
            {
              "xp": 100,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:diamond",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 200,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:emerald",
                  "quantity": 3
                }
              ]
            },
            {
              "xp": 300,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:golden_apple",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 400,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:lapis_lazuli",
                  "quantity": 16
                }
              ]
            },
            {
              "xp": 500,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:amethyst_shard",
                  "quantity": 8
                }
              ]
            },
            {
              "xp": 600,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:experience_bottle",
                  "quantity": 12
                }
              ]
            },
            {
              "xp": 700,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:name_tag",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 800,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:ender_pearl",
                  "quantity": 8
                }
              ]
            },
            {
              "xp": 900,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:netherite_scrap",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 1000,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:nether_star",
                  "quantity": 1
                }
              ]
            }
          ]
        }
    """

    private const val COMBAT_PASS = """
        {
          "id": "combat",
          "displayName": "Combat Pass",
          "description": "Progress from normal Minecraft combat and gathering events.",
          "titleTexture": "gisketchs_chowkingdom_mod:textures/gui/combat_pass.png",
          "titleTextureWidth": 1024,
          "titleTextureHeight": 215,
          "categories": ["combat", "gathering"],
          "xpEvents": [
            {
              "event": "minecraft:monster_killed",
              "xp": 5
            },
            {
              "event": "minecraft:block_harvested",
              "xp": 1,
              "filters": {
                "blockTag": "minecraft:mineable/pickaxe"
              }
            }
          ],
          "progression": [
            {
              "xp": 100,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:iron_ingot",
                  "quantity": 16
                }
              ]
            },
            {
              "xp": 200,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:arrow",
                  "quantity": 32
                }
              ]
            },
            {
              "xp": 300,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:shield",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 400,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:bow",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 500,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:diamond",
                  "quantity": 2
                }
              ]
            },
            {
              "xp": 600,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:enchanted_book",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 700,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:golden_apple",
                  "quantity": 2
                }
              ]
            },
            {
              "xp": 800,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:diamond_sword",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 900,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:totem_of_undying",
                  "quantity": 1
                }
              ]
            },
            {
              "xp": 1000,
              "rewards": [
                {
                  "type": "item",
                  "item": "minecraft:netherite_ingot",
                  "quantity": 1
                }
              ]
            }
          ]
        }
    """
}
