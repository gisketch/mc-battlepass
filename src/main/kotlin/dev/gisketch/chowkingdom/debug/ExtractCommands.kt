package dev.gisketch.chowkingdom.debug

import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.discord.DiscordWebhookClient
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
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
                .then(Commands.literal("structures").executes(::sendStructures)),
        )
    }

    private fun sendBiomes(context: CommandContext<CommandSourceStack>): Int {
        val ids = context.source.server.registryAccess()
            .registryOrThrow(Registries.BIOME)
            .keySet()
            .map { id -> id.toString() }
            .sorted()
        return sendRegistryReport(context, "Biomes", ids)
    }

    private fun sendStructures(context: CommandContext<CommandSourceStack>): Int {
        val ids = context.source.server.registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .keySet()
            .map { id -> id.toString() }
            .sorted()
        return sendRegistryReport(context, "Structures", ids)
    }

    private fun sendRegistryReport(context: CommandContext<CommandSourceStack>, title: String, ids: List<String>): Int {
        val chunks = codeblockChunks("$title (${ids.size})", ids.ifEmpty { listOf("none") })
        context.source.sendSuccess({ Component.literal("Extracted ${ids.size} ${title.lowercase()} and sent to Discord.") }, true)
        chunks.forEach { chunk -> context.source.sendSuccess({ Component.literal(chunk) }, false) }
        chunks.forEach { chunk -> DiscordWebhookClient.send(chunk) }
        return ids.size.coerceAtLeast(1)
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
}
