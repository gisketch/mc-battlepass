package dev.gisketch.chowkingdom.npc

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

object NpcQuestDebugCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(root())
    }

    private fun root(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("quests")
        .requires { source -> source.hasPermission(2) }
        .then(
            Commands.literal("debug")
                .executes(::helpCommand)
                .then(Commands.literal("clear").executes(::clearCommand))
                .then(fetchCommand())
                .then(killCommand())
                .then(travelCommand())
                .then(itemTaskCommand("craft", "minecraft:item_crafted", "Craft"))
                .then(itemTaskCommand("smelt", "minecraft:item_smelted", "Smelt"))
                .then(itemTaskCommand("eat", "minecraft:item_eaten", "Eat"))
                .then(catchCommand())
                .then(quizCommand())
                .then(qualityCommand("quality_food", "food"))
                .then(qualityCommand("quality_crop", "crop"))
                .then(foodChainCommand())
                .then(
                    Commands.literal("quality")
                        .then(qualityCommand("food", "food"))
                        .then(qualityCommand("crop", "crop")),
                )
                .then(customTaskCommand()),
        )

    private fun fetchCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("fetch")
        .then(
            Commands.argument("item", StringArgumentType.word())
                .suggests(::suggestItems)
                .then(Commands.argument("qty", IntegerArgumentType.integer(1, 9999)).executes(::trackFetch)),
        )

    private fun killCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("kill")
        .then(
            Commands.argument("entity", StringArgumentType.word())
                .suggests(::suggestEntities)
                .then(
                    Commands.argument("qty", IntegerArgumentType.integer(1, 9999))
                        .executes { context -> trackKill(context, "") }
                        .then(Commands.argument("dimension", StringArgumentType.word()).suggests(::suggestDimensions).executes { context -> trackKill(context, StringArgumentType.getString(context, "dimension")) }),
                ),
        )

    private fun travelCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("travel")
        .then(
            Commands.literal("on_foot")
                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 1_000_000)).executes(::trackTravel)),
        )
        .then(
            Commands.literal("pokemon_land")
                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 1_000_000)).executes { context -> trackPokemonTravel(context, "pokemon_land", "cobblemon:pokemon_mount_land_traveled", "Travel ${IntegerArgumentType.getInteger(context, "blocks")} blocks on land Pokemon") }),
        )
        .then(
            Commands.literal("pokemon_flying")
                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 1_000_000)).executes { context -> trackPokemonTravel(context, "pokemon_flying", "cobblemon:pokemon_mount_flying_traveled", "Travel ${IntegerArgumentType.getInteger(context, "blocks")} blocks on flying Pokemon") }),
        )

    private fun itemTaskCommand(command: String, eventId: String, verb: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(command)
        .then(
            Commands.argument("item", StringArgumentType.word())
                .suggests(::suggestItems)
                .then(
                    Commands.argument("qty", IntegerArgumentType.integer(1, 9999))
                        .executes { context -> trackItemTask(context, command, eventId, verb) },
                ),
        )

    private fun catchCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("catch")
        .then(Commands.literal("any").then(Commands.argument("qty", IntegerArgumentType.integer(1, 9999)).executes { context -> trackCatch(context, "any", emptyMap()) }))
        .then(
            Commands.literal("type")
                .then(Commands.argument("type", StringArgumentType.word()).suggests(::suggestPokemonTypes).then(Commands.argument("qty", IntegerArgumentType.integer(1, 9999)).executes { context ->
                    val type = StringArgumentType.getString(context, "type")
                    trackCatch(context, "type_$type", mapOf("type" to type), "Catch ${IntegerArgumentType.getInteger(context, "qty")} $type Pokemon")
                })),
        )
        .then(
            Commands.literal("species")
                .then(Commands.argument("species", StringArgumentType.word()).then(Commands.argument("qty", IntegerArgumentType.integer(1, 9999)).executes { context ->
                    val species = StringArgumentType.getString(context, "species")
                    trackCatch(context, "species_$species", mapOf("species" to species), "Catch ${IntegerArgumentType.getInteger(context, "qty")} $species")
                })),
        )
        .then(
            Commands.literal("category")
                .then(Commands.argument("category", StringArgumentType.word()).suggests(::suggestPokemonCategories).then(Commands.argument("qty", IntegerArgumentType.integer(1, 9999)).executes { context ->
                    val category = StringArgumentType.getString(context, "category").lowercase()
                    val filters = when (category) {
                        "legendary" -> mapOf("legendary" to "true")
                        "mythical" -> mapOf("mythical" to "true")
                        "starter" -> mapOf("starter" to "true")
                        else -> emptyMap()
                    }
                    trackCatch(context, "category_$category", filters, "Catch ${IntegerArgumentType.getInteger(context, "qty")} $category Pokemon")
                })),
        )

    private fun quizCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("quiz")
        .executes { context -> trackQuiz(context, 80, 25L, "debug town lore") }
        .then(
            Commands.argument("xp", IntegerArgumentType.integer(0, 1000000))
                .then(
                    Commands.argument("chowcoins", IntegerArgumentType.integer(0, 1000000))
                        .executes { context -> trackQuiz(context, IntegerArgumentType.getInteger(context, "xp"), IntegerArgumentType.getInteger(context, "chowcoins").toLong(), "debug town lore") }
                        .then(Commands.argument("topic", StringArgumentType.greedyString()).executes { context ->
                            trackQuiz(context, IntegerArgumentType.getInteger(context, "xp"), IntegerArgumentType.getInteger(context, "chowcoins").toLong(), StringArgumentType.getString(context, "topic"))
                        }),
                ),
        )

    private fun qualityCommand(command: String, kind: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(command)
        .then(
            Commands.argument("tier", StringArgumentType.word())
                .suggests(::suggestQualityTiers)
                .then(Commands.argument("qty", IntegerArgumentType.integer(1, 9999)).executes { context -> trackQualityFetch(context, kind) }),
        )

    private fun foodChainCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("food_chain")
        .then(
            Commands.argument("item", StringArgumentType.word())
                .suggests(::suggestItems)
                .then(
                    Commands.argument("qty", IntegerArgumentType.integer(1, 64))
                        .executes { context -> trackFoodChain(context, "any") }
                        .then(Commands.argument("process", StringArgumentType.word()).suggests(::suggestFoodProcesses).executes { context ->
                            trackFoodChain(context, StringArgumentType.getString(context, "process"))
                        }),
                ),
        )

    private fun customTaskCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("custom")
        .then(
            Commands.argument("event", StringArgumentType.word())
                .suggests(::suggestCommonEvents)
                .then(
                    Commands.argument("qty", IntegerArgumentType.integer(1, 1_000_000))
                        .executes { context -> trackCustomTask(context, emptyMap()) }
                        .then(Commands.argument("filters", StringArgumentType.greedyString()).executes { context -> trackCustomTask(context, parseFilters(StringArgumentType.getString(context, "filters"))) }),
                ),
        )

    private fun trackFetch(context: CommandContext<CommandSourceStack>): Int {
        val item = StringArgumentType.getString(context, "item")
        val qty = IntegerArgumentType.getInteger(context, "qty")
        return track(
            context,
            slot = "fetch",
            category = "fetch",
            event = "",
            description = "Bring $qty $item",
            goal = qty,
            fetchItem = item,
        )
    }

    private fun trackKill(context: CommandContext<CommandSourceStack>, dimension: String): Int {
        val entity = StringArgumentType.getString(context, "entity")
        val qty = IntegerArgumentType.getInteger(context, "qty")
        val filters = linkedMapOf("entity" to entity)
        if (dimension.isNotBlank()) filters["dimension"] = dimension
        val suffix = if (dimension.isBlank()) "" else " in $dimension"
        return track(context, "kill", "task", "minecraft:entity_killed", "Defeat $qty $entity$suffix", qty, "combat", filters = filters)
    }

    private fun trackTravel(context: CommandContext<CommandSourceStack>): Int {
        val blocks = IntegerArgumentType.getInteger(context, "blocks")
        return track(context, "travel_on_foot", "task", "minecraft:travel_on_foot", "Travel $blocks blocks on foot", blocks, filters = mapOf("mode" to "on_foot"))
    }

    private fun trackPokemonTravel(context: CommandContext<CommandSourceStack>, mode: String, eventId: String, description: String): Int {
        val blocks = IntegerArgumentType.getInteger(context, "blocks")
        return track(context, "travel_$mode", "task", eventId, description, blocks, filters = mapOf("mode" to mode, "mount" to "pokemon"))
    }

    private fun trackItemTask(context: CommandContext<CommandSourceStack>, slot: String, eventId: String, verb: String): Int {
        val item = StringArgumentType.getString(context, "item")
        val qty = IntegerArgumentType.getInteger(context, "qty")
        return track(context, slot, "task", eventId, "$verb $qty $item", qty, filters = mapOf("item" to item))
    }

    private fun trackCatch(context: CommandContext<CommandSourceStack>, slotSuffix: String, filters: Map<String, String>, description: String = ""): Int {
        val qty = IntegerArgumentType.getInteger(context, "qty")
        val text = description.ifBlank { "Catch $qty Pokemon" }
        return track(context, "catch_$slotSuffix", "task", "cobblemon:pokemon_caught", text, qty, passId = "cozy", filters = filters)
    }

    private fun trackQualityFetch(context: CommandContext<CommandSourceStack>, kind: String): Int {
        val tier = StringArgumentType.getString(context, "tier").lowercase()
        val qty = IntegerArgumentType.getInteger(context, "qty")
        val filters = mapOf(
            "quality.has" to "true",
            "quality.kind" to kind,
            "quality.tier" to tier,
        )
        return track(context, "quality_${kind}_$tier", "fetch", "", "Bring $qty $tier quality $kind", qty, fetchItem = "", filters = filters)
    }

    private fun trackQuiz(context: CommandContext<CommandSourceStack>, xp: Int, chowcoins: Long, topic: String): Int {
        val player = context.source.playerOrException
        val npc = NpcFeature.lookedAtNpc(player) ?: run {
            context.source.sendFailure(Component.literal("No Chow Kingdom NPC under crosshair."))
            return 0
        }
        val definition = NpcConfig.get(npc.npcId) ?: run {
            context.source.sendFailure(Component.literal("Unknown NPC '${npc.npcId}'."))
            return 0
        }
        NpcQuestService.debugStartQuiz(player, npc, definition, xp, chowcoins, topic.ifBlank { "debug town lore" })
        context.source.sendSuccess({ Component.literal("Started quiz for ${definition.name}: xp=$xp chowcoins=$chowcoins topic=${topic.ifBlank { "debug town lore" }}").withStyle(ChatFormatting.GREEN) }, false)
        return 1
    }

    private fun trackFoodChain(context: CommandContext<CommandSourceStack>, process: String): Int {
        val item = StringArgumentType.getString(context, "item")
        val qty = IntegerArgumentType.getInteger(context, "qty")
        val normalizedProcess = process.lowercase().replace('-', '_')
        val filters = linkedMapOf(
            "item" to item,
            "item.namespace" to item.substringBefore(':', ""),
        )
        if (normalizedProcess !in setOf("", "any")) filters["process"] = normalizedProcess
        return track(context, "food_chain_${item.substringAfter(':')}", "food_chain", "farmersdelight:food_created", "Create $qty $item after accepting, then bring it back", qty, fetchItem = item, filters = filters)
    }

    private fun trackCustomTask(context: CommandContext<CommandSourceStack>, filters: Map<String, String>): Int {
        val event = StringArgumentType.getString(context, "event")
        val qty = IntegerArgumentType.getInteger(context, "qty")
        val filterText = if (filters.isEmpty()) "" else " ${filters.entries.joinToString(" ") { (key, value) -> "$key=$value" }}"
        return track(context, "custom", "task", event, "Debug $event x$qty$filterText", qty, filters = filters)
    }

    private fun track(
        context: CommandContext<CommandSourceStack>,
        slot: String,
        category: String,
        event: String,
        description: String,
        goal: Int,
        passId: String = "cozy",
        fetchItem: String = "",
        filters: Map<String, String> = emptyMap(),
    ): Int {
        val player = context.source.playerOrException
        val quest = NpcQuestService.debugQuest(player, sanitizeSlot(slot), category, event, description, goal, passId, fetchItem, filters)
        NpcQuestService.debugTrack(player, quest)
        context.source.sendSuccess({ Component.literal("Tracking debug quest: ${quest.description}").withStyle(ChatFormatting.GREEN) }, false)
        return 1
    }

    private fun clearCommand(context: CommandContext<CommandSourceStack>): Int {
        val count = NpcQuestService.debugClear(context.source.playerOrException)
        context.source.sendSuccess({ Component.literal("Cleared $count debug quest(s).").withStyle(ChatFormatting.GRAY) }, false)
        return count.coerceAtLeast(1)
    }

    private fun helpCommand(context: CommandContext<CommandSourceStack>): Int {
        val lines = listOf(
            "/quests debug fetch <item> <qty>",
            "/quests debug kill <entity> <qty> [dimension]",
            "/quests debug travel on_foot|pokemon_land|pokemon_flying <blocks>",
            "/quests debug craft|smelt|eat <item> <qty>",
            "/quests debug catch any <qty> | type <type> <qty> | species <species> <qty> | category <legendary|mythical|starter> <qty>",
            "/quests debug quiz [xp chowcoins topic]",
            "/quests debug quality_food <tier> <qty> | quality_crop <tier> <qty>",
            "/quests debug food_chain <farmersdelight:item> <qty> [cook|craft|smelt|any]",
            "/quests debug custom <event> <qty> [key=value key=value]",
            "/quests debug clear",
        )
        lines.forEach { line -> context.source.sendSuccess({ Component.literal(line).withStyle(ChatFormatting.GRAY) }, false) }
        return 1
    }

    private fun parseFilters(raw: String): Map<String, String> = raw
        .split(Regex("\\s+"))
        .mapNotNull { token ->
            val index = token.indexOf('=')
            if (index <= 0 || index == token.lastIndex) null else token.substring(0, index) to token.substring(index + 1)
        }
        .toMap(linkedMapOf())

    private fun sanitizeSlot(slot: String): String = slot.lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .ifBlank { "custom" }

    private fun suggestItems(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder)

    private fun suggestEntities(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder)

    private fun suggestDimensions(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(context.source.server.allLevels.map { level -> level.dimension().location().toString() }, builder)

    private fun suggestPokemonTypes(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(listOf("normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"), builder)

    private fun suggestPokemonCategories(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(listOf("legendary", "mythical", "starter"), builder)

    private fun suggestQualityTiers(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(listOf("iron", "gold", "diamond"), builder)

    private fun suggestFoodProcesses(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(listOf("cook", "craft", "smelt", "any"), builder)

    private fun suggestCommonEvents(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(
            listOf(
                "minecraft:entity_killed",
                "minecraft:travel_on_foot",
                "minecraft:item_crafted",
                "minecraft:item_smelted",
                "minecraft:item_eaten",
                "cobblemon:pokemon_caught",
            ),
            builder,
        )
}
