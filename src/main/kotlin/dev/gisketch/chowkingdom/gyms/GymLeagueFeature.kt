package dev.gisketch.chowkingdom.gyms

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionEventBank
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.discord.DiscordRelay
import dev.gisketch.chowkingdom.npc.ChowNpcEntity
import dev.gisketch.chowkingdom.npc.NpcConfig
import dev.gisketch.chowkingdom.npc.NpcDefinition
import dev.gisketch.chowkingdom.npc.NpcFeature
import dev.gisketch.chowkingdom.npc.NpcLlmService
import dev.gisketch.chowkingdom.npc.NpcNetwork
import dev.gisketch.chowkingdom.npc.NpcStore
import dev.gisketch.chowkingdom.npc.NpcTime
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

object GymLeagueFeature {
    private var nextSpawnTick = 0L

    fun register() {
        GymLeagueConfig.load()
        GymLeagueStore.load()
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
    }

    fun leagueAvailable(definition: NpcDefinition): Boolean = GymLeagueConfig.isChowfan(definition.id)

    fun tryOpenNpcDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val trainerBinding = GymLeagueConfig.trainerNpc(definition.id)
        if (trainerBinding != null) {
            openTrainerDialog(player, npc, definition, trainerBinding.first, trainerBinding.second)
            return true
        }
        if (GymLeagueConfig.isChowfan(definition.id)) {
            openChowfanDialog(player, npc, definition)
            return true
        }
        return false
    }

    fun handleAction(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, rawAction: String): Boolean {
        val action = rawAction.lowercase()
        if (GymLeagueConfig.isChowfan(definition.id)) {
            when (action) {
                "league_ticket" -> {
                    NpcLlmService.leaveDialog(player, definition.id)
                    startLeagueFromChowfan(player, npc, definition)
                    return true
                }
                "league_status" -> {
                    openChowfanDialog(player, npc, definition)
                    return true
                }
                "join_talk" -> {
                    NpcLlmService.joinConversation(player, definition.id)
                    return true
                }
            }
        }
        val trainerBinding = GymLeagueConfig.trainerNpc(definition.id) ?: return false
        val (league, trainer) = trainerBinding
        when (action) {
            "gym_challenge" -> {
                NpcLlmService.leaveDialog(player, definition.id)
                challengeTrainer(player, npc, definition, league, trainer)
                return true
            }
            "gym_badge" -> {
                openBadgeDialog(player, npc, definition, league, trainer)
                return true
            }
            "join_talk" -> {
                NpcLlmService.joinConversation(player, definition.id)
                return true
            }
        }
        return false
    }

    fun completeEncounterWin(player: ServerPlayer, context: GymBattleContextState) {
        val league = GymLeagueConfig.league(context.leagueId) ?: return
        val encounter = league.encounter(context.encounterId) ?: return
        val trainer = league.trainer(context.trainerId) ?: return
        recordMission(player, "gisketchs_chowkingdom_mod:gym_battle_won", league, encounter, trainer)
        val firstClear = GymLeagueStore.grantClear(player, league, encounter)
        if (!firstClear) return
        if (encounter.rewardXp > 0) BattlepassXpStore.addXp(player, league.defaults.passId, encounter.rewardXp)
        if (encounter.rewardChowcoins > 0) ChowcoinStore.add(player, encounter.rewardChowcoins)
        if (encounter.badgeId.isNotBlank()) {
            recordMission(player, "gisketchs_chowkingdom_mod:gym_badge_earned", league, encounter, trainer)
            SnackbarNetwork.send(
                player,
                SnackbarNotification.item("cobblemon:poke_ball", "BADGE EARNED", "${encounter.displayName} cleared. ${encounter.badgeId.replace('_', ' ').uppercase()}", SnackbarType.SUCCESS, SnackbarSounds.REWARD),
            )
            DiscordRelay.gymBadgeEarned(player, league.displayName, encounter.displayName, encounter.badgeId)
        } else {
            SnackbarNetwork.send(player, SnackbarNotification.item("cobblemon:poke_ball", "LEAGUE BATTLE WON", encounter.displayName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        }
        BattlepassNetwork.syncAllPlayers()
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        GymLeagueConfig.load()
        GymLeagueStore.load()
        GymBattleService.init(event.server)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        GymBattleService.registerPlayer(player)
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        GymBattleService.unregisterPlayer(player)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        val now = server.overworld().gameTime
        if (now < nextSpawnTick) return
        nextSpawnTick = now + 20L * 30L
        spawnUnlockedTrainers(server.overworld())
    }

    private fun spawnUnlockedTrainers(fallbackLevel: ServerLevel) {
        val currentDay = NpcTime.day(fallbackLevel)
        GymLeagueConfig.all().forEach { league ->
            val area = GymLeagueStore.area(league.stadiumArea) ?: return@forEach
            val dimension = runCatching { ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(area.dimension)) }.getOrNull() ?: fallbackLevel.dimension()
            val level = fallbackLevel.server.getLevel(dimension) ?: fallbackLevel
            league.sequence.forEach { encounter ->
                if (!GymLeagueStore.isUnlocked(league.id, encounter.id, currentDay)) return@forEach
                val trainer = league.trainer(encounter.trainer) ?: return@forEach
                if (NpcFeature.existingNpc(level.server, trainer.npcId) != null) return@forEach
                val definition = NpcConfig.get(trainer.npcId) ?: return@forEach
                NpcFeature.spawnConfiguredNpcAt(level, definition, BlockPos(area.x, area.y, area.z))
            }
        }
    }

    private fun openChowfanDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val activeLeague = GymLeagueStore.activeLeague(player)
        val message = if (activeLeague.isBlank()) {
            "League desk is open. I can issue your Kanto ticket and start your badge record whenever you're ready."
        } else {
            val league = GymLeagueConfig.league(activeLeague)
            val next = league?.let { GymLeagueStore.nextPlayerEncounter(player, it) }
            if (league != null && next != null) "Your ${league.displayName} ticket is active. Next match: ${next.displayName}. Level cap ${next.levelCap}."
            else "Your active league is complete or missing. Ask an admin if your ticket looks wrong."
        }
        npc.startTalkingTo(player, 100)
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, dialogMode = "league_chowfan", leagueAvailable = true))
    }

    private fun startLeagueFromChowfan(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val active = GymLeagueStore.activeLeague(player)
        if (active.isNotBlank()) {
            openChowfanDialog(player, npc, definition)
            return
        }
        val league = GymLeagueConfig.league("kanto") ?: run {
            NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, "Kanto paperwork is missing from the league desk.", false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
            return
        }
        GymLeagueStore.startLeague(player, league)
        val first = league.firstEncounter()
        val message = "Ticket checked. Your ${league.displayName} record is open. First match: ${first?.displayName ?: "waiting on the bracket"}. Keep your party under level ${first?.levelCap ?: "the posted cap"}."
        SnackbarNetwork.send(player, SnackbarNotification.item("cobblemon:poke_ball", "LEAGUE TICKET ACTIVE", league.displayName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, dialogMode = "league_chowfan", leagueAvailable = true))
    }

    private fun openTrainerDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, league: GymLeagueDefinition, trainer: GymTrainerDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val activeLeague = GymLeagueStore.activeLeague(player)
        val current = if (activeLeague == league.id) GymLeagueStore.nextPlayerEncounter(player, league) else null
        val day = NpcTime.day(player.level())
        val currentForTrainer = current?.trainer == trainer.id && GymLeagueStore.isUnlocked(league.id, current.id, day)
        val attemptsLeft = (league.defaults.dailyAttemptsPerNpc - GymLeagueStore.attempts(player, trainer.id, day)).coerceAtLeast(0)
        val message = when {
            activeLeague.isBlank() -> "I'm on the ${league.displayName} roster. Get a ticket from Chowfan before we make this official."
            activeLeague != league.id -> "You're carrying another league ticket. Finish that record before challenging ${league.displayName}."
            currentForTrainer && attemptsLeft > 0 -> "You're up for ${current?.displayName}. Level cap ${current?.levelCap}. Attempts left today: $attemptsLeft."
            currentForTrainer -> "You've used today's attempts with me. Come back after the league desk resets."
            current != null -> "Your next ${league.displayName} match is ${current.displayName}. I'm not your current bracket."
            else -> "Your ${league.displayName} record looks complete from here."
        }
        npc.startTalkingTo(player, 100)
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, dialogMode = "gym_trainer", leagueAvailable = false))
    }

    private fun openBadgeDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, league: GymLeagueDefinition, trainer: GymTrainerDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val badges = GymLeagueStore.badges(player, league.id)
        val trainerBadge = trainer.badgeId.takeIf(String::isNotBlank)
        val message = when {
            trainerBadge == null -> "${trainer.name} has no badge on file. This is a league battle checkpoint."
            trainerBadge in badges -> "Badge record confirmed: ${trainerBadge.replace('_', ' ').uppercase()} is yours."
            else -> "Badge record missing: beat ${trainer.name} in your active ${league.displayName} sequence to earn ${trainerBadge.replace('_', ' ').uppercase()}."
        }
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, dialogMode = "gym_trainer"))
    }

    private fun challengeTrainer(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, league: GymLeagueDefinition, trainer: GymTrainerDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val day = NpcTime.day(player.level())
        val active = GymLeagueStore.activeLeague(player)
        val encounter = GymLeagueStore.nextPlayerEncounter(player, league)
        fun fail(message: String) = NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
        if (active != league.id) return fail("You need an active ${league.displayName} ticket from Chowfan first.")
        if (encounter == null) return fail("Your ${league.displayName} record is already complete.")
        if (encounter.trainer != trainer.id) return fail("Your next match is ${encounter.displayName}, not this one.")
        if (!GymLeagueStore.isUnlocked(league.id, encounter.id, day)) return fail("${encounter.displayName} is not on today's bracket yet.")
        val attempts = GymLeagueStore.attempts(player, trainer.id, day)
        if (attempts >= league.defaults.dailyAttemptsPerNpc) return fail("You've used today's ${trainer.name} attempts. Come back after the league desk resets.")
        GymBattleService.partyLevelViolation(player, encounter.levelCap)?.let { level ->
            return fail("Level cap is ${encounter.levelCap}. One of your active party Pokemon is level $level.")
        }
        GymLeagueStore.incrementAttempts(player, trainer.id, day)
        recordMission(player, "gisketchs_chowkingdom_mod:gym_battle_attempted", league, encounter, trainer)
        val started = GymBattleService.startBattle(player, npc, league, encounter, trainer)
        if (!started.started) return fail(started.message)
        BattlepassNetwork.syncAllPlayers()
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, "Battle started. Show me your badge run is real.", false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
    }

    private fun recordMission(player: ServerPlayer, eventId: String, league: GymLeagueDefinition, encounter: GymEncounterDefinition, trainer: GymTrainerDefinition) {
        val attributes = buildMap {
            put("league", league.id)
            put("encounter", encounter.id)
            put("trainer", trainer.id)
            put("kind", encounter.kind)
            if (encounter.badgeId.isNotBlank()) put("badge", encounter.badgeId)
        }
        if (BattlepassMissionEventBank.record(player, eventId, 1, attributes)) BattlepassNetwork.syncAllPlayers()
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("ck").then(gymsRoot()))
        event.dispatcher.register(Commands.literal("chowkingdom").then(gymsRoot()))
    }

    private fun gymsRoot() = Commands.literal("gyms")
        .then(Commands.literal("reload").requires { it.hasPermission(2) }.executes(::reload))
        .then(
            Commands.literal("area")
                .then(Commands.literal("status").executes(::areaStatus))
                .then(
                    Commands.literal("set").requires { it.hasPermission(2) }
                        .then(Commands.argument("area_id", StringArgumentType.word())
                            .then(Commands.argument("radius", IntegerArgumentType.integer(4, 256)).executes(::areaSet))),
                ),
        )
        .then(Commands.literal("status").executes(::statusSelf).then(Commands.argument("player", EntityArgument.player()).executes(::statusPlayer)))
        .then(
            Commands.literal("league")
                .then(Commands.literal("start").requires { it.hasPermission(2) }
                    .then(Commands.argument("league_id", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(GymLeagueConfig.all().map { it.id }, builder) }
                        .executes(::leagueStartSelf)
                        .then(Commands.argument("player", EntityArgument.player()).executes(::leagueStartPlayer))))
                .then(Commands.literal("reset").requires { it.hasPermission(2) }
                    .then(Commands.argument("league_id", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(GymLeagueConfig.all().map { it.id }, builder) }
                        .then(Commands.argument("player", EntityArgument.player()).executes(::leagueReset)))),
        )
        .then(Commands.literal("unlock").requires { it.hasPermission(2) }
            .then(Commands.argument("league_id", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(GymLeagueConfig.all().map { it.id }, builder) }
                .then(Commands.argument("encounter_id", StringArgumentType.word()).suggests(::suggestEncounters).executes(::unlock))))
        .then(Commands.literal("grant").requires { it.hasPermission(2) }
            .then(Commands.argument("league_id", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(GymLeagueConfig.all().map { it.id }, builder) }
                .then(Commands.argument("encounter_id", StringArgumentType.word()).suggests(::suggestEncounters)
                    .then(Commands.argument("player", EntityArgument.player()).executes(::grant)))))
        .then(
            Commands.literal("attempts").requires { it.hasPermission(2) }
                .then(Commands.literal("get").then(Commands.argument("trainer_id", StringArgumentType.word()).then(Commands.argument("player", EntityArgument.player()).executes(::attemptsGet))))
                .then(Commands.literal("reset").then(Commands.argument("trainer_id", StringArgumentType.word()).then(Commands.argument("player", EntityArgument.player()).executes(::attemptsReset)))),
        )

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        GymLeagueConfig.load()
        GymLeagueStore.load()
        context.source.sendSuccess({ Component.literal("Reloaded ${GymLeagueConfig.all().size} gym league(s).") }, true)
        return GymLeagueConfig.all().size
    }

    private fun areaSet(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val id = StringArgumentType.getString(context, "area_id")
        val radius = IntegerArgumentType.getInteger(context, "radius")
        GymLeagueStore.setArea(id, player.level().dimension().location().toString(), player.blockPosition(), radius)
        context.source.sendSuccess({ Component.literal("Set gym area ${cleanId(id)} at ${player.blockPosition().toShortString()} radius $radius.") }, true)
        return 1
    }

    private fun areaStatus(context: CommandContext<CommandSourceStack>): Int {
        val areas = GymLeagueStore.areas()
        if (areas.isEmpty()) {
            context.source.sendSystemMessage(Component.literal("No gym stadium areas set."))
            return 0
        }
        areas.forEach { (id, area) -> context.source.sendSystemMessage(Component.literal("$id ${area.dimension} ${area.x},${area.y},${area.z} r=${area.radius}")) }
        return areas.size
    }

    private fun statusSelf(context: CommandContext<CommandSourceStack>): Int = status(context, context.source.playerOrException)

    private fun statusPlayer(context: CommandContext<CommandSourceStack>): Int = status(context, EntityArgument.getPlayer(context, "player"))

    private fun status(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        GymLeagueStore.statusLines(player).forEach { line -> context.source.sendSystemMessage(Component.literal(line)) }
        return 1
    }

    private fun leagueStartSelf(context: CommandContext<CommandSourceStack>): Int = leagueStart(context, context.source.playerOrException)

    private fun leagueStartPlayer(context: CommandContext<CommandSourceStack>): Int = leagueStart(context, EntityArgument.getPlayer(context, "player"))

    private fun leagueStart(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        val leagueId = StringArgumentType.getString(context, "league_id")
        val league = GymLeagueConfig.league(leagueId) ?: return fail(context, "Unknown league '$leagueId'.")
        if (!GymLeagueStore.startLeague(player, league)) return fail(context, "${player.gameProfile.name} already has active league ${GymLeagueStore.activeLeague(player)}.")
        context.source.sendSuccess({ Component.literal("Started ${league.displayName} for ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun leagueReset(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val leagueId = StringArgumentType.getString(context, "league_id")
        GymLeagueStore.resetLeague(player, leagueId)
        context.source.sendSuccess({ Component.literal("Reset ${cleanId(leagueId)} for ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun unlock(context: CommandContext<CommandSourceStack>): Int {
        val leagueId = StringArgumentType.getString(context, "league_id")
        val encounterId = StringArgumentType.getString(context, "encounter_id")
        if (GymLeagueConfig.league(leagueId)?.encounter(encounterId) == null) return fail(context, "Unknown encounter '$encounterId'.")
        GymLeagueStore.unlock(leagueId, encounterId, NpcTime.day(context.source.server.overworld()))
        context.source.sendSuccess({ Component.literal("Unlocked ${cleanId(encounterId)} in ${cleanId(leagueId)}.") }, true)
        return 1
    }

    private fun grant(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val league = GymLeagueConfig.league(StringArgumentType.getString(context, "league_id")) ?: return fail(context, "Unknown league.")
        val encounter = league.encounter(StringArgumentType.getString(context, "encounter_id")) ?: return fail(context, "Unknown encounter.")
        GymLeagueStore.grantClear(player, league, encounter)
        context.source.sendSuccess({ Component.literal("Granted ${encounter.id} to ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun attemptsGet(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val trainerId = StringArgumentType.getString(context, "trainer_id")
        val day = NpcTime.day(player.level())
        context.source.sendSystemMessage(Component.literal("${player.gameProfile.name} ${cleanId(trainerId)} attempts today: ${GymLeagueStore.attempts(player, trainerId, day)}"))
        return 1
    }

    private fun attemptsReset(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val trainerId = StringArgumentType.getString(context, "trainer_id")
        GymLeagueStore.resetAttempts(player, trainerId)
        context.source.sendSuccess({ Component.literal("Reset ${cleanId(trainerId)} attempts for ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun suggestEncounters(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val leagueId = runCatching { StringArgumentType.getString(context, "league_id") }.getOrDefault("")
        return SharedSuggestionProvider.suggest(GymLeagueConfig.league(leagueId)?.sequence?.map { it.id }.orEmpty(), builder)
    }

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED))
        return 0
    }
}
