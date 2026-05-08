package dev.gisketch.chowkingdom.profiles

import com.mojang.brigadier.arguments.StringArgumentType
import net.neoforged.bus.api.IEventBus
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

object ProfilesFeature {
    fun register(modBus: IEventBus) {
        NicknameNetwork.register(modBus)
        NeoForge.EVENT_BUS.register(this)
        if (FMLEnvironment.dist.isClient) NicknameConfig.load()
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            literal("nickname")
                .then(
                    literal("clear")
                        .executes { context ->
                            val player = context.source.playerOrException
                            val removed = NicknameStore.clear(player)
                            if (removed) {
                                context.source.sendSuccess({ Component.literal("Nickname cleared.") }, true)
                            } else {
                                context.source.sendSuccess({ Component.literal("No nickname set.") }, false)
                            }
                            1
                        },
                )
                    .then(nicknameListRoot("list"))
                    .then(nicknameListRoot("lists"))
                .then(
                    argument("name", StringArgumentType.word())
                        .executes { context ->
                            val player = context.source.playerOrException
                            when (val result = NicknameStore.set(player, StringArgumentType.getString(context, "name"))) {
                                is NicknameResult.Changed -> context.source.sendSuccess({ Component.literal("Nickname changed to ${result.nickname}.") }, true)
                                NicknameResult.Invalid -> {
                                    context.source.sendFailure(Component.literal("Nickname must be 1-16 characters: letters, numbers, underscore only."))
                                    return@executes 0
                                }
                            }
                            1
                        },
                ),
        )
    }

    private fun nicknameListRoot(name: String) = literal(name)
        .requires { source -> source.hasPermission(2) }
        .executes { context ->
            context.source.sendSuccess({ Component.literal("Original -> Nickname") }, false)
            context.source.server.playerList.players.forEach { player ->
                val nickname = NicknameStore.nicknameFor(player.uuid) ?: "-"
                context.source.sendSuccess({ Component.literal("${NicknameStore.originalName(player)} -> $nickname") }, false)
            }
            1
        }

    @SubscribeEvent
    fun onNameFormat(event: PlayerEvent.NameFormat) {
        val displayName = NicknameStore.nicknameFor(event.entity.uuid)?.let(Component::literal) ?: event.username.copy()
        event.displayname = displayName.withStyle { style -> style.withBold(false) }
    }

    @SubscribeEvent
    fun onTabListNameFormat(event: PlayerEvent.TabListNameFormat) {
        val nickname = NicknameStore.nicknameFor(event.entity.uuid) ?: return
        event.displayName = Component.literal(nickname)
    }

    @SubscribeEvent
    fun onServerChat(event: ServerChatEvent) {
        event.message = Component.literal(event.rawText).withStyle { style -> style.withColor(CHAT_MESSAGE_COLOR) }
    }

    private const val CHAT_MESSAGE_COLOR = 0xD7D9E0
}