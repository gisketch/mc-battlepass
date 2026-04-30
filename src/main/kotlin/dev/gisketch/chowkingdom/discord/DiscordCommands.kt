package dev.gisketch.chowkingdom.discord

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

object DiscordCommands {
    fun register() {
        NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            literal<CommandSourceStack>("chowkingdom")
                .then(
                    literal<CommandSourceStack>("discord")
                        .requires { source -> source.hasPermission(2) }
                        .then(
                            literal<CommandSourceStack>("avatar")
                                .then(
                                    argument("player", EntityArgument.player())
                                        .executes { context ->
                                            val player = EntityArgument.getPlayer(context, "player")
                                            DiscordQuickSkinSupport.diagnostics(player, DiscordConfig.current())
                                                .forEach { line -> context.source.sendSuccess({ Component.literal(line) }, false) }
                                            1
                                        }
                                )
                        )
                        .then(
                            literal<CommandSourceStack>("debug-avatar")
                                .then(
                                    argument("mode", StringArgumentType.word())
                                        .suggests { _, builder ->
                                            listOf("on", "off").forEach(builder::suggest)
                                            builder.buildFuture()
                                        }
                                        .executes { context ->
                                            val mode = StringArgumentType.getString(context, "mode")
                                            val enabled = when (mode) {
                                                "on" -> true
                                                "off" -> false
                                                else -> {
                                                    context.source.sendFailure(Component.literal("Use on or off"))
                                                    return@executes 0
                                                }
                                            }
                                            DiscordConfig.current().debugAvatarResolution = enabled
                                            context.source.sendSuccess({ Component.literal("Discord avatar debug logging: $enabled") }, true)
                                            1
                                        }
                                )
                        )
                        .then(
                            literal<CommandSourceStack>("avatar-server")
                                .executes { context ->
                                    DiscordQuickSkinAvatarServer.diagnostics(DiscordConfig.current())
                                        .forEach { line -> context.source.sendSuccess({ Component.literal(line) }, false) }
                                    1
                                }
                        )
                )
        )
    }
}