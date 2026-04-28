package dev.gisketch.battlepass

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

object BattlepassCommands {
    private val unknownPass = SimpleCommandExceptionType(Component.literal("Unknown battlepass pass"))

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(root("battlepass"))
        event.dispatcher.register(root("battlePass"))
    }

    private fun root(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .then(Commands.literal("list").executes(::listPasses))
        .then(Commands.literal("reload").requires { source -> source.hasPermission(2) }.executes(::reloadPasses))
        .then(
            Commands.literal("xp")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("pass", StringArgumentType.word())
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("targets", EntityArgument.players()).executes(::addXpFromActionSyntax)),
                                ),
                        ),
                ),
        )
        .then(
            Commands.argument("pass", StringArgumentType.word())
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.literal("xp")
                        .then(
                            Commands.argument("amount", IntegerArgumentType.integer(1))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(::addXpFromPassSyntax)),
                        ),
                ),
        )

    private fun listPasses(context: CommandContext<CommandSourceStack>): Int {
        context.source.sendSuccess({ Component.literal("Battlepass passes: ${BattlepassPassRegistry.knownIds()}") }, false)
        return BattlepassPassRegistry.all().size
    }

    private fun reloadPasses(context: CommandContext<CommandSourceStack>): Int {
        val count = BattlepassPassRegistry.reload()
        BattlepassXpStore.load()
        context.source.sendSuccess({ Component.literal("Reloaded $count battlepass pass(es).") }, true)
        return count
    }

    private fun addXpFromActionSyntax(context: CommandContext<CommandSourceStack>): Int = addXp(context)

    private fun addXpFromPassSyntax(context: CommandContext<CommandSourceStack>): Int = addXp(context)

    private fun addXp(context: CommandContext<CommandSourceStack>): Int {
        val passId = StringArgumentType.getString(context, "pass")
        val pass = BattlepassPassRegistry.get(passId) ?: throw unknownPass.create()
        val amount = IntegerArgumentType.getInteger(context, "amount")
        val targets = EntityArgument.getPlayers(context, "targets")

        targets.forEach { player -> BattlepassXpStore.addXp(player, pass.id, amount) }
        context.source.sendSuccess(
            { Component.literal("Added $amount XP to ${targets.size} player(s) for ${pass.displayName}.") },
            true,
        )
        return targets.size
    }
}