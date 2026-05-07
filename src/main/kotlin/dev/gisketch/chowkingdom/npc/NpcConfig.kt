package dev.gisketch.chowkingdom.npc

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object NpcConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var definitions: Map<String, NpcDefinition> = linkedMapOf()

    private val dir: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("npcs")

    fun load() {
        dir.createDirectories()
        writeDefaultIfMissing()
        val loaded = linkedMapOf<String, NpcDefinition>()
        Files.list(dir).use { files ->
            files.filter { path -> path.extension.equals("json", ignoreCase = true) }
                .sorted(Comparator.comparing { path -> path.fileName.toString() })
                .forEach { path ->
                    val fallbackId = path.nameWithoutExtension
                    val definition = try {
                        path.bufferedReader().use { reader -> gson.fromJson(reader, NpcDefinition::class.java) } ?: NpcDefinition()
                    } catch (exception: Exception) {
                        ChowKingdomMod.LOGGER.warn("Failed to load NPC definition {}", path, exception)
                        NpcDefinition(id = fallbackId)
                    }.normalized(fallbackId)
                    loaded[definition.id] = definition
                }
        }
        definitions = loaded
    }

    fun all(): Collection<NpcDefinition> = definitions.values

    fun get(id: String): NpcDefinition? = definitions[id]

    fun firstIntroducible(): NpcDefinition? = definitions["finn"] ?: definitions.values.firstOrNull()

    private fun writeDefaultIfMissing() {
        val file = dir.resolve("finn.json")
        if (file.exists()) return
        Files.createTempFile(dir, "finn", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(defaultFinn(), writer) }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun defaultFinn(): NpcDefinition = NpcDefinition(
        id = "finn",
        name = "Finn",
        title = "The Adventurer",
        skin = "gisketchs_chowkingdom_mod:npc/finn",
        bodyType = NpcBodyTypes.NORMAL,
        job = "adventurer",
        jobDefinition = NpcJobDefinition(id = "adventurer", scanIntervalTicks = 60, roamRadius = 7, workScanRadius = 9),
        schedule = NpcScheduleDefinition(
            activities = mutableListOf(
                NpcScheduleEntryDefinition(fromHour = 6, toHour = 20, activity = "work"),
                NpcScheduleEntryDefinition(fromHour = 20, toHour = 22, activity = "home"),
                NpcScheduleEntryDefinition(fromHour = 22, toHour = 6, activity = "sleep"),
            ),
        ),
        store = "cosmetics",
        personality = NpcPersonalityDefinition(
            llmPrompt = "Finn is brave, friendly, direct, and hungry for adventure. He wants a home near players and talks like an energetic hero.",
            traits = mutableListOf("brave", "friendly", "reckless"),
            speechStyle = "energetic hero",
            catchphrases = mutableListOf("Mathematical!", "Adventure time!"),
        ),
        housing = NpcHousingDefinition(canMoveIn = true, requiresBed = true),
        gifts = NpcGiftsDefinition(
            loved = mutableListOf("minecraft:diamond_sword", "#minecraft:swords"),
            liked = mutableListOf("minecraft:cooked_beef", "minecraft:iron_sword"),
            disliked = mutableListOf("minecraft:rotten_flesh"),
            dailyLimit = 1,
            resetHour = 5,
            reactions = NpcGiftReactionsDefinition(
                loved = mutableListOf("Whoa, {player}! {item} is amazing. Thank you!", "This is perfect. I love {item}!"),
                liked = mutableListOf("Thanks, {player}. I like {item}.", "Nice gift. {item} will come in handy."),
                disliked = mutableListOf("Oh. {item}. Thanks, I guess.", "I don't really like {item}, but I appreciate the thought."),
                neutral = mutableListOf("Thanks for {item}.", "I'll keep {item} safe."),
            ),
        ),
        hurtMessages = mutableListOf(
            "Hey, watch it, {player}!",
            "Ouch. That's not very heroic.",
            "Careful! I'm on your side.",
        ),
        wakeMessages = mutableListOf(
            "Mmph... {player}? I was sleeping.",
            "I'm awake. Is everything okay?",
            "You woke me up, but I'm listening.",
        ),
        workTargetBlocks = mutableListOf("minecraft:campfire", "minecraft:crafting_table", "minecraft:barrel"),
    )
}
