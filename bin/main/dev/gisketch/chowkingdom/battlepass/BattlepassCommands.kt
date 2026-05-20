package dev.gisketch.chowkingdom.battlepass

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.concurrent.CompletableFuture

object BattlepassCommands {
    private val unknownPass = SimpleCommandExceptionType(Component.literal("Unknown battlepass pass"))
    private val noDailyPass = SimpleCommandExceptionType(Component.literal("Daily battlepass missions were removed. Use NPC mission pools instead."))
    private val noMilestone = SimpleCommandExceptionType(Component.literal("No active incomplete milestone found"))

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(chowKingdomRoot("chowkingdom"))
        event.dispatcher.register(chowKingdomRoot("ck"))
        event.dispatcher.register(battlepassRoot("battlepass"))
    }

    private fun chowKingdomRoot(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .then(battlepassRoot("battlepass"))

    private fun battlepassRoot(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .then(Commands.literal("list").executes(::listPasses))
        .then(Commands.literal("reload").requires { source -> source.hasPermission(2) }.executes(::reloadPasses))
        .then(
            Commands.literal("reset")
                .requires { source -> source.hasPermission(2) }
                .then(Commands.literal("daily").executes { context -> resetMissions(context, BattlepassMissionScope.DAILY) })
                .then(Commands.literal("weekly").executes { context -> resetMissions(context, BattlepassMissionScope.WEEKLY) }),
        )
        .then(
            Commands.literal("complete")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.argument("mission", StringArgumentType.word())
                        .suggests(::suggestMissions)
                        .then(Commands.argument("targets", EntityArgument.players()).executes(::completeMission)),
                ),
        )
        .then(
            Commands.literal("daily")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.literal("replace")
                        .then(
                            Commands.argument("quest_event", ResourceLocationArgument.id())
                                .suggests(::suggestHookEvents)
                                .then(Commands.argument("qty", StringArgumentType.greedyString()).executes(::replaceDailyMission)),
                        ),
                ),
        )
            .then(
                Commands.literal("milestone")
                .requires { source -> source.hasPermission(2) }
                .then(Commands.literal("complete").executes(::completeRandomMilestone)),
            )
        .then(
            Commands.literal("claim")
                .then(
                    Commands.argument("pass", StringArgumentType.word())
                        .then(Commands.argument("tierXp", IntegerArgumentType.integer(1)).executes(::claimTier)),
                ),
        )
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
                )
                .then(
                    Commands.literal("reduce")
                        .then(
                            Commands.argument("pass", StringArgumentType.word())
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("targets", EntityArgument.players()).executes(::reduceXpFromActionSyntax)),
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
                            Commands.literal("add")
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("targets", EntityArgument.players()).executes(::addXpFromPassSyntax)),
                                ),
                        )
                        .then(
                            Commands.literal("reduce")
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("targets", EntityArgument.players()).executes(::reduceXpFromPassSyntax)),
                                ),
                        )
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
        BattlepassMissionProgressStore.load()
        BattlepassNetwork.syncAllPlayers()
        context.source.sendSuccess({ Component.literal("Reloaded $count battlepass pass(es).") }, true)
        return count
    }

    private fun addXpFromActionSyntax(context: CommandContext<CommandSourceStack>): Int = addXp(context)

    private fun addXpFromPassSyntax(context: CommandContext<CommandSourceStack>): Int = addXp(context)

    private fun reduceXpFromActionSyntax(context: CommandContext<CommandSourceStack>): Int = addXp(context, -IntegerArgumentType.getInteger(context, "amount"))

    private fun reduceXpFromPassSyntax(context: CommandContext<CommandSourceStack>): Int = addXp(context, -IntegerArgumentType.getInteger(context, "amount"))

    private fun resetMissions(context: CommandContext<CommandSourceStack>, scope: BattlepassMissionScope): Int {
        val count = BattlepassMissionProgressStore.reset(scope)
        BattlepassNetwork.syncAllPlayers()
        context.source.sendSuccess({ Component.literal("Reset $count ${scope.id} battlepass mission pool(s).") }, true)
        return count
    }

    private fun completeMission(context: CommandContext<CommandSourceStack>): Int {
        val missionKey = StringArgumentType.getString(context, "mission")
        val targets = EntityArgument.getPlayers(context, "targets")
        val completed = targets.count { player -> BattlepassMissionProgressStore.completeMission(player, missionKey) }
        BattlepassNetwork.syncAllPlayers()
        context.source.sendSuccess({ Component.literal("Completed $missionKey for $completed player(s).") }, true)
        return completed
    }

    private fun completeRandomMilestone(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val missionKey = BattlepassMissionProgressStore.activeIncompleteMilestoneKeys(player.uuid).shuffled().firstOrNull() ?: throw noMilestone.create()
        val completed = BattlepassMissionProgressStore.completeMission(player, missionKey)
        BattlepassNetwork.syncAllPlayers()
        context.source.sendSuccess({ Component.literal("Completed test milestone $missionKey for ${player.name.string}.") }, true)
        return if (completed) 1 else 0
    }

    private fun suggestMissions(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        BattlepassMissionProgressStore.activeMissionKeys()
            .filter { key -> key.startsWith(builder.remaining, ignoreCase = true) }
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    private fun suggestHookEvents(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        BattlepassAvailableMissionEvents.ids()
            .filter { eventId -> eventId.startsWith(builder.remaining, ignoreCase = true) }
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    private fun replaceDailyMission(context: CommandContext<CommandSourceStack>): Int {
        val eventId = ResourceLocationArgument.getId(context, "quest_event").toString()
        val goals = StringArgumentType.getString(context, "qty")
            .split(',')
            .mapNotNull { raw -> raw.trim().toIntOrNull()?.takeIf { value -> value > 0 } }
        if (goals.isEmpty()) throw SimpleCommandExceptionType(Component.literal("qty must be positive numbers, e.g. 10 or 10,25,50")).create()

        val pass = BattlepassPassRegistry.replaceFirstDailyEvent(eventId, goals) ?: throw noDailyPass.create()
        BattlepassMissionProgressStore.reset(BattlepassMissionScope.DAILY)
        BattlepassNetwork.syncAllPlayers()
        context.source.sendSuccess({ Component.literal("Replaced first daily mission in ${pass.displayName} with $eventId goals ${goals.joinToString(",")}") }, true)
        return goals.last()
    }

    private fun addXp(context: CommandContext<CommandSourceStack>, signedAmount: Int? = null): Int {
        val passId = StringArgumentType.getString(context, "pass")
        val pass = BattlepassPassRegistry.get(passId) ?: throw unknownPass.create()
        val amount = signedAmount ?: IntegerArgumentType.getInteger(context, "amount")
        val targets = EntityArgument.getPlayers(context, "targets")

        targets.forEach { player -> BattlepassXpStore.addXp(player, pass.id, amount) }
        BattlepassNetwork.syncAllPlayers()
        context.source.sendSuccess(
            { Component.literal("${if (amount >= 0) "Added" else "Reduced"} ${kotlin.math.abs(amount)} XP ${if (amount >= 0) "to" else "from"} ${targets.size} player(s) for ${pass.displayName}.") },
            true,
        )
        return targets.size
    }

    private fun claimTier(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val passId = StringArgumentType.getString(context, "pass")
        val tierXp = IntegerArgumentType.getInteger(context, "tierXp")
        val claimed = BattlepassClaimService.claim(player, passId, tierXp)
        BattlepassNetwork.syncAllPlayers()
        return if (claimed) 1 else 0
    }
}