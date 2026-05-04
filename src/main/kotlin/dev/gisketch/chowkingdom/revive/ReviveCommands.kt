package dev.gisketch.chowkingdom.revive

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object ReviveCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(reviveRoot("revive"))
        dispatcher.register(Commands.literal("ck").then(reviveRoot("revive")))
        dispatcher.register(Commands.literal("chowkingdom").then(reviveRoot("revive")))
    }

    private fun reviveRoot(name: String) = Commands.literal(name)
        .requires { source -> source.hasPermission(2) }
        .then(Commands.literal("reload").executes(::reload))
        .then(
            Commands.literal("recover")
                .executes(::recoverSelf)
                .then(Commands.argument("player", EntityArgument.player()).executes(::recover)),
        )
        .then(
            Commands.literal("status")
                .executes(::statusSelf)
                .then(Commands.argument("player", EntityArgument.player()).executes(::status)),
        )
        .then(
            Commands.literal("debug")
                .then(
                    Commands.literal("down")
                        .executes(::debugDownSelf)
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1)).executes(::debugDownSelfForSeconds))
                        .then(
                            Commands.argument("player", EntityArgument.player())
                                .executes(::debugDown)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1)).executes(::debugDownForSeconds)),
                        ),
                )
                .then(Commands.literal("self-revive").executes(::debugSelfRevive))
                .then(
                    Commands.literal("double-revive")
                        .executes(::debugDoubleReviveSelf)
                        .then(Commands.argument("delaySeconds", IntegerArgumentType.integer(0)).executes(::debugDoubleReviveSelfAfter))
                        .then(
                            Commands.argument("player", EntityArgument.player())
                                .executes(::debugDoubleRevive)
                                .then(Commands.argument("delaySeconds", IntegerArgumentType.integer(0)).executes(::debugDoubleReviveAfter)),
                        ),
                )
                .then(Commands.literal("expire").then(Commands.argument("player", EntityArgument.player()).executes(::debugExpire)))
                .then(
                    Commands.literal("dummy")
                        .then(Commands.literal("spawn").executes(::spawnDummy))
                        .then(Commands.literal("clear").executes(::clearDummies)),
                ),
        )
        .then(Commands.argument("player", EntityArgument.player()).executes(::forceRevive))

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        ReviveFeature.reloadConfig()
        context.source.sendSuccess({ Component.literal("Revive config reloaded.").withStyle(ChatFormatting.GREEN) }, true)
        return 1
    }

    private fun forceRevive(context: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(context, "player")
        return if (ReviveFeature.forceRevive(target, context.source.textName)) {
            context.source.sendSuccess({ Component.literal("Revived ${target.gameProfile.name}.").withStyle(ChatFormatting.GREEN) }, true)
            1
        } else {
            context.source.sendFailure(Component.literal("${target.gameProfile.name} is not incapacitated."))
            0
        }
    }

    private fun recoverSelf(context: CommandContext<CommandSourceStack>): Int =
        recover(context.source.playerOrException, context.source)

    private fun recover(context: CommandContext<CommandSourceStack>): Int =
        recover(EntityArgument.getPlayer(context, "player"), context.source)

    private fun recover(target: ServerPlayer, source: CommandSourceStack): Int {
        ReviveFeature.recover(target)
        source.sendSuccess({ Component.literal("Recovered ${target.gameProfile.name}: cleared revive state, reset pose, and healed.").withStyle(ChatFormatting.GREEN) }, true)
        return 1
    }

    private fun statusSelf(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        context.source.sendSuccess({ ReviveFeature.status(player) }, false)
        return 1
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(context, "player")
        context.source.sendSuccess({ ReviveFeature.status(target) }, false)
        return 1
    }

    private fun debugDownSelf(context: CommandContext<CommandSourceStack>): Int =
        debugDown(context.source.playerOrException, context.source, null)

    private fun debugDownSelfForSeconds(context: CommandContext<CommandSourceStack>): Int =
        debugDown(context.source.playerOrException, context.source, IntegerArgumentType.getInteger(context, "seconds"))

    private fun debugDown(context: CommandContext<CommandSourceStack>): Int =
        debugDown(EntityArgument.getPlayer(context, "player"), context.source, null)

    private fun debugDownForSeconds(context: CommandContext<CommandSourceStack>): Int =
        debugDown(EntityArgument.getPlayer(context, "player"), context.source, IntegerArgumentType.getInteger(context, "seconds"))

    private fun debugDown(target: ServerPlayer, source: CommandSourceStack, seconds: Int?): Int {
        return if (ReviveFeature.debugIncapacitate(target, seconds)) {
            val suffix = seconds?.let { " for ${it}s" }.orEmpty()
            source.sendSuccess({ Component.literal("Debug-incapacitated ${target.gameProfile.name}$suffix.").withStyle(ChatFormatting.YELLOW) }, true)
            1
        } else {
            source.sendFailure(Component.literal("${target.gameProfile.name} is already incapacitated."))
            0
        }
    }

    private fun debugSelfRevive(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        return if (ReviveFeature.debugSelfRevive(player)) {
            context.source.sendSuccess({ Component.literal("Started self-revive debug timer.").withStyle(ChatFormatting.YELLOW) }, false)
            1
        } else {
            context.source.sendFailure(Component.literal("You must be incapacitated first. Use /revive debug down."))
            0
        }
    }

    private fun debugDoubleReviveSelf(context: CommandContext<CommandSourceStack>): Int =
        debugDoubleRevive(context.source.playerOrException, context.source, 1)

    private fun debugDoubleReviveSelfAfter(context: CommandContext<CommandSourceStack>): Int =
        debugDoubleRevive(context.source.playerOrException, context.source, IntegerArgumentType.getInteger(context, "delaySeconds"))

    private fun debugDoubleRevive(context: CommandContext<CommandSourceStack>): Int =
        debugDoubleRevive(EntityArgument.getPlayer(context, "player"), context.source, 1)

    private fun debugDoubleReviveAfter(context: CommandContext<CommandSourceStack>): Int =
        debugDoubleRevive(EntityArgument.getPlayer(context, "player"), context.source, IntegerArgumentType.getInteger(context, "delaySeconds"))

    private fun debugDoubleRevive(target: ServerPlayer, source: CommandSourceStack, delaySeconds: Int): Int {
        return if (ReviveFeature.debugScheduleReviver(target, delaySeconds)) {
            source.sendSuccess({ Component.literal("Scheduled debug reviver for ${target.gameProfile.name} in ${delaySeconds}s.").withStyle(ChatFormatting.YELLOW) }, false)
            1
        } else {
            source.sendFailure(Component.literal("${target.gameProfile.name} needs an active revive first. Use /revive debug self-revive while incapacitated."))
            0
        }
    }

    private fun debugExpire(context: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(context, "player")
        return if (ReviveFeature.debugExpire(target)) {
            context.source.sendSuccess({ Component.literal("Expired revive timer for ${target.gameProfile.name}.").withStyle(ChatFormatting.RED) }, true)
            1
        } else {
            context.source.sendFailure(Component.literal("${target.gameProfile.name} is not incapacitated."))
            0
        }
    }

    private fun spawnDummy(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        ReviveFeature.spawnDebugDummy(player)
        context.source.sendSuccess({ Component.literal("Spawned incapacitated revive dummy. Right-click it to test reviving another target.").withStyle(ChatFormatting.YELLOW) }, false)
        return 1
    }

    private fun clearDummies(context: CommandContext<CommandSourceStack>): Int {
        val count = ReviveFeature.clearDebugDummies(context.source.server)
        context.source.sendSuccess({ Component.literal("Cleared $count revive dummy(s).").withStyle(ChatFormatting.GRAY) }, true)
        return count.coerceAtLeast(1)
    }
}
