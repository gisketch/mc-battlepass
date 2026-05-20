package dev.gisketch.chowkingdom.discord

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import dev.gisketch.chowkingdom.profiles.NicknameStore
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
        event.dispatcher.register(discordRoot("chowkingdom"))
        event.dispatcher.register(discordRoot("ck"))
    }

    private fun discordRoot(root: String) = literal<CommandSourceStack>(root)
        .then(
            literal<CommandSourceStack>("discord")
                .then(
                    literal<CommandSourceStack>("link")
                        .executes { context ->
                            val player = context.source.playerOrException
                            val pending = DiscordAccountLinkStore.createPending(player, NicknameStore.displayName(player))
                            context.source.sendSuccess({ Component.literal("Discord link code: ${pending.code}. In Discord, send !link ${pending.code} within 10 minutes.") }, false)
                            1
                        }
                )
                .then(
                    literal<CommandSourceStack>("linked")
                        .executes { context ->
                            val player = context.source.playerOrException
                            val link = DiscordAccountLinkStore.linkFor(player)
                            if (link == null) {
                                context.source.sendSuccess({ Component.literal("No Discord account linked. Use /ck discord link.") }, false)
                            } else {
                                context.source.sendSuccess({ Component.literal("Linked to Discord ${link.discordName} (${link.discordId})") }, false)
                            }
                            1
                        }
                )
                .then(
                    literal<CommandSourceStack>("unlink")
                        .executes { context ->
                            val player = context.source.playerOrException
                            val link = DiscordAccountLinkStore.unlink(player)
                            if (link == null) {
                                context.source.sendSuccess({ Component.literal("No Discord account linked.") }, false)
                            } else {
                                context.source.sendSuccess({ Component.literal("Unlinked Discord ${link.discordName}.") }, false)
                            }
                            1
                        }
                        .then(
                            argument("player", EntityArgument.player())
                                .requires { source -> source.hasPermission(2) }
                                .executes { context ->
                                    val player = EntityArgument.getPlayer(context, "player")
                                    val link = DiscordAccountLinkStore.unlink(player)
                                    if (link == null) {
                                        context.source.sendSuccess({ Component.literal("${NicknameStore.displayName(player)} has no Discord account linked.") }, false)
                                    } else {
                                        context.source.sendSuccess({ Component.literal("Unlinked ${NicknameStore.displayName(player)} from Discord ${link.discordName}.") }, true)
                                    }
                                    1
                                }
                        )
                )
                        .then(
                            literal<CommandSourceStack>("avatar")
                                .requires { source -> source.hasPermission(2) }
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
                                .requires { source -> source.hasPermission(2) }
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
                                .requires { source -> source.hasPermission(2) }
                                .executes { context ->
                                    DiscordQuickSkinAvatarServer.diagnostics(DiscordConfig.current())
                                        .forEach { line -> context.source.sendSuccess({ Component.literal(line) }, false) }
                                    1
                                }
                        )
                        .then(
                            literal<CommandSourceStack>("inbound")
                                .requires { source -> source.hasPermission(2) }
                                .executes { context ->
                                    DiscordInboundBridge.diagnostics(DiscordConfig.current().discordToMinecraft)
                                        .forEach { line -> context.source.sendSuccess({ Component.literal(line) }, false) }
                                    1
                                }
                        )
                        .then(
                            literal<CommandSourceStack>("reload")
                                .requires { source -> source.hasPermission(2) }
                                .executes { context ->
                                    DiscordConfig.load()
                                    DiscordQuickSkinAvatarServer.reload(DiscordConfig.current())
                                    DiscordInboundBridge.reset()
                                    DiscordInboundBridge.checkChannel(DiscordConfig.current().discordToMinecraft)
                                    context.source.sendSuccess({ Component.literal("Discord config reloaded") }, true)
                                    1
                                }
                        )
        )
}