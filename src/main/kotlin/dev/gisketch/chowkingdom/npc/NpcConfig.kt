package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object NpcConfig {
    private var definitions: Map<String, NpcDefinition> = linkedMapOf()
    private var settings: NpcSettingsDefinition = NpcSettingsDefinition().normalized()
    private var genericQuests: NpcQuestPoolsDefinition = NpcQuestPoolsDefinition.defaults()

    private val dir: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("npcs")

    fun load() {
        dir.createDirectories()
        writeDefaultIfMissing()
        settings = loadSettings()
        genericQuests = loadGenericQuests()
        val friendshipDefaults = loadFriendshipDefaults()
        val loaded = linkedMapOf<String, NpcDefinition>()
        npcDefinitionFiles().forEach { path ->
            val fallbackId = path.nameWithoutExtension
            val definition = try {
                TomlConfigIO.read(path, NpcDefinition::class.java, ::NpcDefinition)
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load NPC definition {}", path, exception)
                NpcDefinition(id = fallbackId)
            }.normalized(fallbackId, friendshipDefaults)
            mergeQuestPools(definition)
            loaded[definition.id] = definition
        }
        definitions = loaded
    }

    fun all(): Collection<NpcDefinition> = definitions.values

    fun get(id: String): NpcDefinition? = definitions[id]

    fun settings(): NpcSettingsDefinition = settings

    fun llmPresetNames(): List<String> = settings.llm.presets.map { preset -> preset.name }

    fun switchLlmPreset(name: String): NpcLlmPresetSwitchResult {
        val normalizedName = name.trim().lowercase()
        val preset = settings.llm.presets.firstOrNull { preset -> preset.name.equals(normalizedName, ignoreCase = true) }
            ?: return NpcLlmPresetSwitchResult(false, "Unknown LLM preset '$name'. Available: ${llmPresetNames().joinToString(", ")}", settings.llm)
        settings.llm.activePreset = preset.name
        settings.llm.provider = preset.provider
        settings.llm.baseUrl = preset.baseUrl
        settings.llm.model = preset.model
        settings.llm.apiKey = preset.apiKey
        settings = settings.normalized()
        writeSettings(settings)
        return NpcLlmPresetSwitchResult(true, "Switched NPC LLM preset to ${settings.llm.activePreset} (${settings.llm.provider}/${settings.llm.model}).", settings.llm)
    }

    fun firstIntroducible(): NpcDefinition? = definitions["finn"] ?: definitions.values.firstOrNull()

    private fun writeDefaultIfMissing() {
        val file = dir.resolve("finn.toml")
        if (!file.exists()) {
            Files.createTempFile(dir, "finn", ".toml.tmp").also { temp ->
                TomlConfigIO.write(temp, defaultFinn())
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val friendshipFile = dir.resolve(FRIENDSHIP_MESSAGES_FILE)
        if (!friendshipFile.exists()) {
            Files.createTempFile(dir, "friendship_messages", ".toml.tmp").also { temp ->
                TomlConfigIO.write(temp, NpcFriendshipMessagesDefinition.default().normalized())
                Files.move(temp, friendshipFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val settingsFile = dir.resolve(NPC_SETTINGS_FILE)
        if (!settingsFile.exists()) {
            Files.createTempFile(dir, "npc_settings", ".toml.tmp").also { temp ->
                TomlConfigIO.write(temp, NpcSettingsDefinition().normalized())
                Files.move(temp, settingsFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val genericQuestsFile = dir.resolve(GENERIC_QUESTS_FILE)
        if (!genericQuestsFile.exists()) {
            Files.createTempFile(dir, "generic_quests", ".toml.tmp").also { temp ->
                TomlConfigIO.write(temp, NpcQuestPoolsDefinition.defaults())
                Files.move(temp, genericQuestsFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun npcDefinitionFiles(): List<Path> {
        val paths = mutableListOf<Path>()
        Files.list(dir).use { files ->
            files.forEach { path ->
                val fileName = path.fileName.toString()
                if (
                    path.extension.equals("toml", ignoreCase = true) &&
                    fileName != FRIENDSHIP_MESSAGES_FILE &&
                    fileName != NPC_SETTINGS_FILE &&
                    fileName != GENERIC_QUESTS_FILE
                ) {
                    paths.add(path)
                }
            }
        }
        return paths.sortedBy { path -> path.fileName.toString() }
    }

    private fun loadSettings(): NpcSettingsDefinition {
        val file = dir.resolve(NPC_SETTINGS_FILE)
        return try {
            TomlConfigIO.read(file, NpcSettingsDefinition::class.java, ::NpcSettingsDefinition)
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load NPC settings {}", file, exception)
            NpcSettingsDefinition()
        }.normalized()
    }

    private fun loadGenericQuests(): NpcQuestPoolsDefinition {
        val file = dir.resolve(GENERIC_QUESTS_FILE)
        return try {
            TomlConfigIO.read(file, NpcQuestPoolsDefinition::class.java, { NpcQuestPoolsDefinition.defaults() })
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load NPC generic quests {}", file, exception)
            NpcQuestPoolsDefinition.defaults()
        }.normalized()
    }

    private fun mergeQuestPools(definition: NpcDefinition) {
        if (!definition.missions.enabled) return
        val compiled = genericQuests.compile(definition.id, "generic") + definition.uniqueQuests.compile(definition.id, "unique")
        if (compiled.isEmpty()) return
        definition.missions.pool = (definition.missions.pool + compiled)
            .distinctBy { mission -> mission.id }
            .toMutableList()
    }

    private fun writeSettings(value: NpcSettingsDefinition) {
        TomlConfigIO.write(dir.resolve(NPC_SETTINGS_FILE), value.normalized())
    }

    private fun loadFriendshipDefaults(): NpcFriendshipMessagesDefinition {
        val file = dir.resolve(FRIENDSHIP_MESSAGES_FILE)
        return try {
            TomlConfigIO.read(file, NpcFriendshipMessagesDefinition::class.java, { NpcFriendshipMessagesDefinition.default() })
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load NPC friendship messages {}", file, exception)
            NpcFriendshipMessagesDefinition.default()
        }.normalized(NpcFriendshipMessagesDefinition.default())
    }

    private fun defaultFinn(): NpcDefinition = NpcDefinition(
        id = "finn",
        name = "Finn",
        title = "The Adventurer",
        skin = "gisketchs_chowkingdom_mod:npc/finn",
        bodyType = NpcBodyTypes.NORMAL,
        mainPokemon = "cobblemon:growlithe",
        store = "cosmetics",
        classId = "warrior",
        jobDefinition = NpcJobDefinition(scanIntervalTicks = 60, roamRadius = 7, workScanRadius = 9),
        schedule = NpcScheduleDefinition(
            activities = mutableListOf(
                NpcScheduleEntryDefinition(fromHour = 6, toHour = 15, activity = "work"),
                NpcScheduleEntryDefinition(fromHour = 15, toHour = 20, activity = "meetup"),
                NpcScheduleEntryDefinition(fromHour = 20, toHour = 22, activity = "home"),
                NpcScheduleEntryDefinition(fromHour = 22, toHour = 6, activity = "sleep"),
            ),
        ),
        personality = NpcPersonalityDefinition(
            llmPrompt = "Finn is brave, friendly, direct, and hungry for adventure. He wants a home near players and talks like an energetic hero.",
            traits = mutableListOf("brave", "friendly", "reckless"),
            speechStyle = "energetic hero",
            catchphrases = mutableListOf("Mathematical!", "Adventure time!"),
        ),
        housing = NpcHousingDefinition(canMoveIn = true, requiresBed = true),
        voice = NpcVoiceDefinition(animalesePitch = "low", pitch = 0.95f, volume = 0.38f, radius = 12.0f),
        chat = NpcChatDefinition(callNames = mutableListOf("finn", "dude")),
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
        missions = NpcMissionsDefinition(
            offerBalloonMessages = mutableListOf("@quest_log.png That smells like a quest, {player}.", "@quest_log.png I need a hero for this one."),
            pool = mutableListOf(
                NpcMissionDefinition(id = "finn_hunt_mobs", category = "timed", event = "minecraft:monster_killed", eventDesc = "Defeat {goal} Monsters In {seconds}s", questText = "Help me clear a quick monster wave near town.", passId = "combat", xp = 100, chowcoins = 50, goal = 3, timeWindowSeconds = 20),
                NpcMissionDefinition(id = "finn_fetch_beef", category = "fetch", eventDesc = "Bring {goal} Cooked Beef", questText = "Bring me cooked beef for the next patrol.", passId = "cozy", xp = 80, chowcoins = 25, fetchItem = "minecraft:cooked_beef", fetchCount = 4),
            ),
        ),
        friendshipMessages = NpcFriendshipMessagesDefinition(
            interact = FinnFriendshipMessages.interact,
            gift = FinnFriendshipMessages.gift,
            hurt = FinnFriendshipMessages.hurt,
            wake = FinnFriendshipMessages.wake,
            greeting = FinnFriendshipMessages.greeting,
            firstDailyChat = FinnFriendshipMessages.firstDailyChat,
        ).normalized(),
        shopMessages = NpcShopMessagesDefinition.default().normalized(),
        camperMessages = NpcCamperMessagesDefinition().normalized(),
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
        workBlocks = mutableListOf(
            NpcWorkBlockRequirementDefinition(id = "minecraft:barrel", count = 3, displayName = "barrels"),
            NpcWorkBlockRequirementDefinition(id = "minecraft:item_frame", count = 2, displayName = "item frames"),
            NpcWorkBlockRequirementDefinition(id = "#minecraft:beds", count = 1, displayName = "bed"),
        ),
    )
}

class NpcLlmPresetSwitchResult(val success: Boolean, val message: String, val settings: NpcLlmSettingsDefinition)

private const val FRIENDSHIP_MESSAGES_FILE = "friendship_messages.toml"
private const val NPC_SETTINGS_FILE = "settings.toml"
private const val GENERIC_QUESTS_FILE = "generic_quests.toml"
