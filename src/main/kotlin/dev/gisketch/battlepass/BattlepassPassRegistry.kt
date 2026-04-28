package dev.gisketch.battlepass

import com.google.gson.GsonBuilder
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
        get() = FMLPaths.CONFIGDIR.get().resolve(BattlepassMod.MOD_ID).resolve("passes")

    fun reload(): Int {
        ensureDefaultPasses()
        passes.clear()

        Files.list(passDirectory).use { files ->
            files
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .sorted()
                .forEach(::loadPass)
        }

        BattlepassMod.LOGGER.info("Loaded {} battlepass definition(s)", passes.size)
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
            BattlepassMod.LOGGER.warn("Failed to load battlepass pass {}", path, exception)
        }
    }

    private fun ensureDefaultPasses() {
        passDirectory.createDirectories()
        writeDefault("cobblemon.json", COBBLEMON_PASS)
        writeDefault("combat.json", COMBAT_PASS)
    }

    private fun writeDefault(fileName: String, content: String) {
        val path = passDirectory.resolve(fileName)
        if (path.exists()) return
        path.bufferedWriter().use { writer -> writer.write(content.trimIndent()) }
    }

    private fun normalizeId(id: String): String = id.trim().lowercase(Locale.ROOT)

    private const val COBBLEMON_PASS = """
        {
          "id": "cobblemon",
          "displayName": "Cobblemon Pass",
          "description": "Progress from Cobblemon captures and battles.",
          "categories": ["cobblemon", "season_1"],
          "xpEvents": [
            {
              "event": "cobblemon:pokemon_captured",
              "xp": 10
            },
            {
              "event": "cobblemon:pokemon_defeated",
              "xp": 5
            }
          ],
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