package dev.gisketch.chowkingdom.debug

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.discord.DiscordWebhookClient
import dev.gisketch.chowkingdom.shops.ExplorerTargetCatalog
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

object ExtractCommands {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("extract")
                .requires { source -> source.hasPermission(2) }
                .then(Commands.literal("biome").executes(::sendBiomes))
                .then(Commands.literal("biomes").executes(::sendBiomes))
                .then(Commands.literal("structure").executes(::sendStructures))
                .then(Commands.literal("structures").executes(::sendStructures))
                .then(Commands.literal("biome_structures").executes(::sendBiomesAndStructures))
                .then(Commands.literal("worldgen").executes(::sendBiomesAndStructures))
                .then(Commands.literal("entities").executes { context -> sendRegistry(context, ENTITY_TYPE_REGISTRY) })
                .then(Commands.literal("entity_tags").executes { context -> sendTags(context, ENTITY_TYPE_REGISTRY) })
                .then(
                    Commands.literal("entity_tag")
                        .then(
                            Commands.argument("tag_id", StringArgumentType.greedyString())
                                .suggests { context, builder -> suggestTagIds(context, builder, ENTITY_TYPE_REGISTRY) }
                                .executes { context -> sendTagEntries(context, ENTITY_TYPE_REGISTRY, StringArgumentType.getString(context, "tag_id")) },
                        ),
                )
                .then(
                    Commands.literal("registry")
                        .then(
                            Commands.argument("registry", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(REGISTRY_SUGGESTIONS, builder) }
                                .executes { context -> sendRegistry(context, StringArgumentType.getString(context, "registry")) },
                        ),
                )
                .then(
                    Commands.literal("tags")
                        .then(
                            Commands.argument("registry", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(REGISTRY_SUGGESTIONS, builder) }
                                .executes { context -> sendTags(context, StringArgumentType.getString(context, "registry")) },
                        ),
                )
                .then(
                    Commands.literal("tag")
                        .then(
                            Commands.argument("registry", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(REGISTRY_SUGGESTIONS, builder) }
                                .then(
                                    Commands.argument("tag_id", StringArgumentType.greedyString())
                                        .suggests { context, builder -> suggestTagIds(context, builder, StringArgumentType.getString(context, "registry")) }
                                        .executes { context -> sendTagEntries(context, StringArgumentType.getString(context, "registry"), StringArgumentType.getString(context, "tag_id")) },
                                ),
                        ),
                ),
        )
    }

    private fun sendBiomes(context: CommandContext<CommandSourceStack>): Int {
        val lines = ExplorerTargetCatalog.biomeReportLines(context.source.server)
        val count = ExplorerTargetCatalog.biomeIds(context.source.server).size
        return sendRegistryReport(context, "Biomes", lines, count)
    }

    private fun sendStructures(context: CommandContext<CommandSourceStack>): Int {
        val lines = ExplorerTargetCatalog.structureReportLines(context.source.server)
        val count = ExplorerTargetCatalog.structureIds(context.source.server).size
        return sendRegistryReport(context, "Structures", lines, count)
    }

    private fun sendBiomesAndStructures(context: CommandContext<CommandSourceStack>): Int {
        val lines = ExplorerTargetCatalog.combinedReportLines(context.source.server)
        val count = ExplorerTargetCatalog.biomeIds(context.source.server).size + ExplorerTargetCatalog.structureIds(context.source.server).size
        return sendRegistryReport(context, "Biome/Structure Targets", lines, count)
    }

    private fun sendRegistry(context: CommandContext<CommandSourceStack>, rawRegistry: String): Int {
        val registry = normalizeRegistry(rawRegistry) ?: return unknownRegistry(context, rawRegistry)
        val lines = registryIds(context, registry)
        return sendRegistryReport(context, "${registryTitle(registry)} Registry", lines, lines.size)
    }

    private fun sendTags(context: CommandContext<CommandSourceStack>, rawRegistry: String): Int {
        val registry = normalizeRegistry(rawRegistry) ?: return unknownRegistry(context, rawRegistry)
        val lines = tagIds(context, registry)
        return sendRegistryReport(context, "${registryTitle(registry)} Tags", lines, lines.size)
    }

    private fun sendTagEntries(context: CommandContext<CommandSourceStack>, rawRegistry: String, rawTagId: String): Int {
        val registry = normalizeRegistry(rawRegistry) ?: return unknownRegistry(context, rawRegistry)
        val tagId = parseTagId(rawTagId) ?: run {
            context.source.sendFailure(Component.literal("Invalid tag id: $rawTagId"))
            return 0
        }
        val lines = tagEntries(context, registry, tagId)
        return sendRegistryReport(context, "${registryTitle(registry)} Tag #$tagId", lines, lines.size)
    }

    private fun registryIds(context: CommandContext<CommandSourceStack>, registry: String): List<String> = when (registry) {
        ITEM_REGISTRY -> registryIds(BuiltInRegistries.ITEM)
        BLOCK_REGISTRY -> registryIds(BuiltInRegistries.BLOCK)
        ENTITY_TYPE_REGISTRY -> registryIds(BuiltInRegistries.ENTITY_TYPE)
        BIOME_REGISTRY -> registryIds(context.source.server.registryAccess().registryOrThrow(Registries.BIOME))
        STRUCTURE_REGISTRY -> registryIds(context.source.server.registryAccess().registryOrThrow(Registries.STRUCTURE))
        else -> emptyList()
    }

    private fun tagIds(context: CommandContext<CommandSourceStack>, registry: String): List<String> = when (registry) {
        ITEM_REGISTRY -> tagIds(BuiltInRegistries.ITEM)
        BLOCK_REGISTRY -> tagIds(BuiltInRegistries.BLOCK)
        ENTITY_TYPE_REGISTRY -> tagIds(BuiltInRegistries.ENTITY_TYPE)
        BIOME_REGISTRY -> tagIds(context.source.server.registryAccess().registryOrThrow(Registries.BIOME))
        STRUCTURE_REGISTRY -> tagIds(context.source.server.registryAccess().registryOrThrow(Registries.STRUCTURE))
        else -> emptyList()
    }

    private fun tagEntries(context: CommandContext<CommandSourceStack>, registry: String, tagId: ResourceLocation): List<String> = when (registry) {
        ITEM_REGISTRY -> tagEntries(BuiltInRegistries.ITEM, Registries.ITEM, tagId)
        BLOCK_REGISTRY -> tagEntries(BuiltInRegistries.BLOCK, Registries.BLOCK, tagId)
        ENTITY_TYPE_REGISTRY -> tagEntries(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, tagId)
        BIOME_REGISTRY -> tagEntries(context.source.server.registryAccess().registryOrThrow(Registries.BIOME), Registries.BIOME, tagId)
        STRUCTURE_REGISTRY -> tagEntries(context.source.server.registryAccess().registryOrThrow(Registries.STRUCTURE), Registries.STRUCTURE, tagId)
        else -> emptyList()
    }

    private fun <T : Any> registryIds(registry: Registry<T>): List<String> =
        registry.keySet().map { id -> id.toString() }.sorted()

    private fun <T : Any> tagIds(registry: Registry<T>): List<String> =
        registry.tagNames.map { tag -> "#${tag.location()}" }.sorted().toList()

    private fun <T : Any> tagEntries(registry: Registry<T>, registryKey: ResourceKey<out Registry<T>>, tagId: ResourceLocation): List<String> =
        registry.getTagOrEmpty(TagKey.create(registryKey, tagId))
            .map { holder -> registry.getKey(holder.value()).toString() }
            .sorted()

    private fun suggestTagIds(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder, rawRegistry: String): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val registry = normalizeRegistry(rawRegistry) ?: return builder.buildFuture()
        return SharedSuggestionProvider.suggest(tagIds(context, registry), builder)
    }

    private fun parseTagId(rawTagId: String): ResourceLocation? =
        runCatching { ResourceLocation.parse(rawTagId.removePrefix("#")) }.getOrNull()

    private fun normalizeRegistry(rawRegistry: String): String? = when (rawRegistry.lowercase()) {
        "item", "items" -> ITEM_REGISTRY
        "block", "blocks" -> BLOCK_REGISTRY
        "entity", "entities", "entity_type", "entity_types" -> ENTITY_TYPE_REGISTRY
        "biome", "biomes" -> BIOME_REGISTRY
        "structure", "structures" -> STRUCTURE_REGISTRY
        else -> null
    }

    private fun registryTitle(registry: String): String = when (registry) {
        ITEM_REGISTRY -> "Item"
        BLOCK_REGISTRY -> "Block"
        ENTITY_TYPE_REGISTRY -> "Entity Type"
        BIOME_REGISTRY -> "Biome"
        STRUCTURE_REGISTRY -> "Structure"
        else -> registry
    }

    private fun unknownRegistry(context: CommandContext<CommandSourceStack>, rawRegistry: String): Int {
        context.source.sendFailure(Component.literal("Unknown registry: $rawRegistry. Use item, block, entity_type, biome, or structure."))
        return 0
    }

    private fun sendRegistryReport(context: CommandContext<CommandSourceStack>, title: String, lines: List<String>, count: Int): Int {
        val chunks = codeblockChunks("$title ($count)", lines.ifEmpty { listOf("none") })
        context.source.sendSuccess({ Component.literal("Extracted $count ${title.lowercase()} and sent to Discord.") }, true)
        chunks.forEach { chunk -> context.source.sendSuccess({ Component.literal(chunk) }, false) }
        chunks.forEach { chunk -> DiscordWebhookClient.send(chunk) }
        return count.coerceAtLeast(1)
    }

    private fun codeblockChunks(title: String, ids: List<String>): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        ids.forEach { id ->
            if (current.length + id.length + 1 > DISCORD_CODEBLOCK_BODY_LIMIT) {
                chunks += "$title\n```text\n$current```"
                current.clear()
            }
            current.append(id).append('\n')
        }
        if (current.isNotEmpty()) chunks += "$title\n```text\n$current```"
        return chunks
    }

    private const val DISCORD_CODEBLOCK_BODY_LIMIT = 1800
    private const val ITEM_REGISTRY = "item"
    private const val BLOCK_REGISTRY = "block"
    private const val ENTITY_TYPE_REGISTRY = "entity_type"
    private const val BIOME_REGISTRY = "biome"
    private const val STRUCTURE_REGISTRY = "structure"
    private val REGISTRY_SUGGESTIONS = listOf(ITEM_REGISTRY, BLOCK_REGISTRY, ENTITY_TYPE_REGISTRY, BIOME_REGISTRY, STRUCTURE_REGISTRY)
}
