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
import dev.gisketch.chowkingdom.npc.NpcQuestHudEntryPayload
import dev.gisketch.chowkingdom.npc.NpcQuestService
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
    private const val DEFAULT_STADIUM_AREA = "main_stadium"
    private var nextSpawnTick = 0L
    private var nextAvailabilityTick = 0L

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

    fun isTrainerNpc(npcId: String): Boolean = GymLeagueConfig.isTrainerNpc(npcId)

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
                challengeTrainer(player, npc, definition, league, trainer, forceFriendly = false)
                return true
            }
            "gym_friendly_battle" -> {
                NpcLlmService.leaveDialog(player, definition.id)
                challengeTrainer(player, npc, definition, league, trainer, forceFriendly = true)
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
        if (!context.official) {
            SnackbarNetwork.send(player, SnackbarNotification.item("cobblemon:poke_ball", "FRIENDLY BATTLE WON", trainer.name, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            BattlepassNetwork.syncAllPlayers()
            NpcQuestService.syncTo(player)
            return
        }
        recordMission(player, "gisketchs_chowkingdom_mod:gym_battle_won", league, encounter, trainer)
        val firstClear = GymLeagueStore.grantClear(player, league, encounter)
        if (!firstClear) {
            NpcQuestService.syncTo(player)
            return
        }
        if (encounter.rewardXp > 0) BattlepassXpStore.addXp(player, league.defaults.passId, encounter.rewardXp)
        if (encounter.rewardChowcoins > 0) ChowcoinStore.add(player, encounter.rewardChowcoins)
        if (encounter.badgeId.isNotBlank()) {
            recordMission(player, "gisketchs_chowkingdom_mod:gym_badge_earned", league, encounter, trainer)
            SnackbarNetwork.send(
                player,
                SnackbarNotification.item("cobblemon:poke_ball", "RECORD UPDATED", "${encounter.displayName} cleared. ${encounter.badgeId.replace('_', ' ').uppercase()}", SnackbarType.SUCCESS, SnackbarSounds.REWARD),
            )
            DiscordRelay.gymBadgeEarned(player, league.displayName, encounter.displayName, encounter.badgeId)
        } else {
            SnackbarNetwork.send(player, SnackbarNotification.item("cobblemon:poke_ball", "LEAGUE BATTLE WON", encounter.displayName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        }
        BattlepassNetwork.syncAllPlayers()
        NpcQuestService.syncTo(player)
    }

    fun completeEncounterLoss(player: ServerPlayer, context: GymBattleContextState) {
        val league = GymLeagueConfig.league(context.leagueId) ?: return
        val trainer = league.trainer(context.trainerId) ?: return
        val title = if (context.official) "LEAGUE BATTLE LOST" else "FRIENDLY BATTLE LOST"
        SnackbarNetwork.send(player, SnackbarNotification.item("cobblemon:poke_ball", title, trainer.name, SnackbarType.GENERIC, SnackbarSounds.GENERIC))
        BattlepassNetwork.syncAllPlayers()
        NpcQuestService.syncTo(player)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        GymLeagueConfig.load()
        GymLeagueStore.load()
        GymBattleService.init(event.server)
        dedupeGymTrainers(event.server)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        GymBattleService.registerPlayer(player)
        checkChallengeAvailability(player)
        NpcQuestService.syncTo(player)
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        GymBattleService.unregisterPlayer(player)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        val now = server.overworld().gameTime
        if (now >= nextAvailabilityTick) {
            nextAvailabilityTick = now + 20L * 30L
            server.playerList.players.forEach { player -> checkChallengeAvailability(player) }
        }
        if (now < nextSpawnTick) return
        nextSpawnTick = now + 20L * 30L
        spawnUnlockedTrainers(server.overworld())
    }

    fun hudEntriesFor(player: ServerPlayer): List<NpcQuestHudEntryPayload> {
        val activeLeague = GymLeagueStore.activeLeague(player)
        if (activeLeague.isBlank()) return emptyList()
        val league = GymLeagueConfig.league(activeLeague) ?: return emptyList()
        val encounter = GymLeagueStore.nextPlayerEncounter(player, league) ?: return emptyList()
        val day = NpcTime.day(player.level())
        if (!GymLeagueStore.isUnlocked(league.id, encounter.id, day)) return emptyList()
        val trainer = league.trainer(encounter.trainer) ?: return emptyList()
        val maxAttempts = league.defaults.dailyAttemptsPerNpc
        if (GymLeagueStore.attempts(player, trainer.id, maxAttempts) >= maxAttempts) return emptyList()
        return listOf(
            NpcQuestHudEntryPayload(
                npcId = trainer.npcId,
                npcName = trainer.name,
                description = "Challenge ${trainer.name} in a Pokemon Battle",
                passId = league.defaults.passId,
                xp = encounter.rewardXp,
                chowcoins = encounter.rewardChowcoins,
                progress = 0,
                goal = 1,
                acceptedAtTick = 0L,
            ),
        )
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
                dedupeTrainer(level.server, trainer.npcId, BlockPos(area.x, area.y, area.z))
                if (NpcFeature.existingNpc(level.server, trainer.npcId) != null) return@forEach
                val definition = NpcConfig.get(trainer.npcId) ?: return@forEach
                NpcFeature.spawnConfiguredNpcAt(level, definition, BlockPos(area.x, area.y, area.z))
            }
        }
    }

    fun dedupeGymTrainers(server: net.minecraft.server.MinecraftServer) {
        GymLeagueConfig.all().forEach { league ->
            val area = GymLeagueStore.area(league.stadiumArea)
            val anchor = if (area != null) BlockPos(area.x, area.y, area.z) else server.overworld().sharedSpawnPos
            league.trainers.forEach { trainer -> dedupeTrainer(server, trainer.npcId, anchor) }
        }
    }

    private fun dedupeTrainer(server: net.minecraft.server.MinecraftServer, npcId: String, anchor: BlockPos) {
        val live = NpcFeature.existingNpcs(server, npcId)
        if (live.size <= 1) return
        val stored = NpcStore.entityUuid(npcId)
        val kept = live.firstOrNull { npc -> npc.uuid == stored }
            ?: live.minByOrNull { npc -> npc.blockPosition().distSqr(anchor) }
            ?: return
        live.filterNot { npc -> npc.uuid == kept.uuid }.forEach { duplicate ->
            duplicate.navigation.stop()
            duplicate.discard()
        }
        NpcStore.setEntity(npcId, kept.uuid, kept.campPos ?: anchor)
        ChowKingdomMod.LOGGER.info("Deduped gym trainer {}: kept {}, removed {}", npcId, kept.uuid, live.size - 1)
    }

    private fun openChowfanDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val activeLeague = GymLeagueStore.activeLeague(player)
        val message = if (activeLeague.isBlank()) {
            "Your league record is unopened. I can start your Kanto badge record whenever you're ready."
        } else {
            val league = GymLeagueConfig.league(activeLeague)
            val next = league?.let { GymLeagueStore.nextPlayerEncounter(player, it) }
            if (league != null && next != null) "Your ${league.displayName} record is active. Next match: ${next.displayName}. Level cap ${next.levelCap}."
            else "Your active league record is complete or missing. Ask an admin if the record looks wrong."
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
        val message = "Record opened. Your ${league.displayName} badge run starts with ${first?.displayName ?: "the first posted match"}. Keep your party under level ${first?.levelCap ?: "the posted cap"}."
        SnackbarNetwork.send(player, SnackbarNotification.item("cobblemon:poke_ball", "LEAGUE RECORD OPEN", league.displayName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        NpcQuestService.syncTo(player)
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, dialogMode = "league_chowfan", leagueAvailable = true))
    }

    private fun openTrainerDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, league: GymLeagueDefinition, trainer: GymTrainerDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val activeLeague = GymLeagueStore.activeLeague(player)
        val current = if (activeLeague == league.id) GymLeagueStore.nextPlayerEncounter(player, league) else null
        val day = NpcTime.day(player.level())
        val officialEncounter = current?.takeIf { it.trainer == trainer.id }
        val officialForTrainer = officialEncounter != null
        val currentUnlocked = current?.let { GymLeagueStore.isUnlocked(league.id, it.id, day) } == true
        val maxAttempts = league.defaults.dailyAttemptsPerNpc
        val attemptsLeft = (maxAttempts - GymLeagueStore.attempts(player, trainer.id, maxAttempts)).coerceAtLeast(0)
        val cooldownMs = GymLeagueStore.attemptCooldownRemainingMs(player, trainer.id, maxAttempts)
        val challengeAvailable = officialForTrainer && currentUnlocked && attemptsLeft > 0
        val challengeDisabledReason = when {
            officialEncounter != null && !currentUnlocked -> availabilityReason(league.id, officialEncounter.id, day)
            officialForTrainer && attemptsLeft <= 0 -> "Cooldown: ${formatCooldown(cooldownMs)}"
            else -> ""
        }
        val friendlyAvailable = attemptsLeft > 0
        val message = when {
            activeLeague != league.id && attemptsLeft > 0 -> "FRIENDLY BATTLE is open. It will not touch your badge record. Attempts left before cooldown: $attemptsLeft."
            activeLeague != league.id -> "Friendly battle cooldown is active. Come back in ${formatCooldown(cooldownMs)}."
            officialEncounter != null && !currentUnlocked -> "${officialEncounter.displayName} is next, but not yet. Meet me when the route opens. FRIENDLY BATTLE is open if you want practice."
            officialEncounter != null && attemptsLeft > 0 -> "You're up for ${officialEncounter.displayName}. Level cap ${officialEncounter.levelCap}. Attempts left before cooldown: $attemptsLeft."
            officialForTrainer -> "Battle cooldown is active. Come back in ${formatCooldown(cooldownMs)}."
            current != null -> "Your next ${league.displayName} record match is ${current.displayName}. I can still do a friendly battle."
            else -> "Your ${league.displayName} record looks complete from here."
        }
        npc.startTalkingTo(player, 100)
        NpcNetwork.openDialog(
            player,
            NpcFeature.dialogPayload(
                definition,
                npc,
                message,
                false,
                friendship.level,
                dialogMode = "gym_trainer",
                leagueAvailable = false,
                challengeAvailable = challengeAvailable,
                challengeDisabledReason = challengeDisabledReason,
                friendlyBattleAvailable = friendlyAvailable,
            ),
        )
    }

    private fun openBadgeDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, league: GymLeagueDefinition, trainer: GymTrainerDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val badges = GymLeagueStore.badges(player, league.id)
        val trainerBadge = trainer.badgeId.takeIf(String::isNotBlank)
        val message = when {
            trainerBadge == null -> "${trainer.name} has no badge entry on this record. This is a league battle checkpoint."
            trainerBadge in badges -> "Record confirmed: ${trainerBadge.replace('_', ' ').uppercase()} is yours."
            else -> "Record missing: beat ${trainer.name} in your active ${league.displayName} sequence to earn ${trainerBadge.replace('_', ' ').uppercase()}."
        }
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, dialogMode = "gym_trainer"))
    }

    private fun challengeTrainer(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, league: GymLeagueDefinition, trainer: GymTrainerDefinition, forceFriendly: Boolean) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val day = NpcTime.day(player.level())
        val active = GymLeagueStore.activeLeague(player)
        val official = !forceFriendly && active == league.id
        val encounter = if (official) GymLeagueStore.nextPlayerEncounter(player, league) else league.sequence.firstOrNull { it.trainer == trainer.id }
        fun fail(message: String) = NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
        if (encounter == null) return fail(if (official) "Your ${league.displayName} record is already complete." else "${trainer.name} has no friendly team on file.")
        if (official && encounter.trainer != trainer.id) return fail("Your next match is ${encounter.displayName}, not this one.")
        if (official && !GymLeagueStore.isUnlocked(league.id, encounter.id, day)) return fail("${encounter.displayName} is not on today's bracket yet.")
        val maxAttempts = league.defaults.dailyAttemptsPerNpc
        val attempts = GymLeagueStore.attempts(player, trainer.id, maxAttempts)
        if (attempts >= maxAttempts) return fail("${trainer.name} is on cooldown. Come back in ${formatCooldown(GymLeagueStore.attemptCooldownRemainingMs(player, trainer.id, maxAttempts))}.")
        if (!GymBattleService.partyBattleReady(player)) return fail("Your party cannot battle right now. Heal at least one Pokemon first.")
        if (official) GymBattleService.partyLevelViolation(player, encounter.levelCap)?.let { level ->
            return fail("Level cap is ${encounter.levelCap}. One of your active party Pokemon is level $level.")
        }
        GymLeagueStore.incrementAttempts(player, trainer.id, maxAttempts, league.defaults.attemptCooldownMinutes * 60_000L)
        if (official) recordMission(player, "gisketchs_chowkingdom_mod:gym_battle_attempted", league, encounter, trainer)
        val started = GymBattleService.startBattle(player, npc, league, encounter, trainer, official = official)
        if (!started.started) return fail(started.message)
        BattlepassNetwork.syncAllPlayers()
        NpcQuestService.syncTo(player)
        val message = if (official) "Record battle started. Show me your badge run is real." else "Friendly battle started. No badge record pressure."
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
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
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 256)).executes(::areaSetDefault)),
                )
                .then(
                    Commands.literal("set_named").requires { it.hasPermission(2) }
                        .then(Commands.argument("area_id", StringArgumentType.word())
                            .then(Commands.argument("radius", IntegerArgumentType.integer(4, 256)).executes(::areaSet))),
                )
                .then(Commands.literal("set_player").requires { it.hasPermission(2) }.executes(::areaSetPlayerSpot))
                .then(Commands.literal("set_trainer").requires { it.hasPermission(2) }.executes(::areaSetTrainerSpot))
                .then(
                    Commands.literal("tp")
                        .executes(::areaTeleportSelf)
                        .then(Commands.argument("player", EntityArgument.player()).requires { it.hasPermission(2) }.executes(::areaTeleportPlayer)),
                )
                .then(
                    Commands.literal("teleport")
                        .executes(::areaTeleportSelf)
                        .then(Commands.argument("player", EntityArgument.player()).requires { it.hasPermission(2) }.executes(::areaTeleportPlayer)),
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
        dedupeGymTrainers(context.source.server)
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

    private fun areaSetDefault(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val radius = IntegerArgumentType.getInteger(context, "radius")
        GymLeagueStore.setArea(DEFAULT_STADIUM_AREA, player.level().dimension().location().toString(), player.blockPosition(), radius)
        context.source.sendSuccess({ Component.literal("Set main gym stadium at ${player.blockPosition().toShortString()} radius $radius.") }, true)
        return 1
    }

    private fun areaSetPlayerSpot(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        GymLeagueStore.setBattleSpot(DEFAULT_STADIUM_AREA, "player", player.level().dimension().location().toString(), player.blockPosition(), player.yRot, player.xRot)
        context.source.sendSuccess({ Component.literal("Set gym player battle spot at ${player.blockPosition().toShortString()}.") }, true)
        return 1
    }

    private fun areaSetTrainerSpot(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        GymLeagueStore.setBattleSpot(DEFAULT_STADIUM_AREA, "trainer", player.level().dimension().location().toString(), player.blockPosition(), player.yRot, player.xRot)
        context.source.sendSuccess({ Component.literal("Set gym trainer battle spot at ${player.blockPosition().toShortString()}.") }, true)
        return 1
    }

    private fun areaTeleportSelf(context: CommandContext<CommandSourceStack>): Int = areaTeleport(context, context.source.playerOrException)

    private fun areaTeleportPlayer(context: CommandContext<CommandSourceStack>): Int = areaTeleport(context, EntityArgument.getPlayer(context, "player"))

    private fun areaTeleport(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        val area = GymLeagueStore.area(DEFAULT_STADIUM_AREA) ?: return fail(context, "Main gym stadium is not set. Use /ck gyms area set <radius> first.")
        val dimension = runCatching { ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(area.dimension)) }.getOrNull()
            ?: return fail(context, "Gym stadium dimension is invalid: ${area.dimension}")
        val level = context.source.server.getLevel(dimension) ?: return fail(context, "Gym stadium dimension is not loaded: ${area.dimension}")
        player.teleportTo(level, area.x + 0.5, area.y.toDouble(), area.z + 0.5, player.yRot, player.xRot)
        SnackbarNetwork.send(player, SnackbarNotification.texture(SnackbarIcons.CHOWCOIN_TEXTURE, "GYM STADIUM", "Teleported to the league area.", SnackbarType.SUCCESS, SnackbarSounds.SUCCESS))
        context.source.sendSuccess({ Component.literal("Teleported ${player.gameProfile.name} to the main gym stadium.") }, true)
        return 1
    }

    private fun areaStatus(context: CommandContext<CommandSourceStack>): Int {
        val areas = GymLeagueStore.areas()
        if (areas.isEmpty()) {
            context.source.sendSystemMessage(Component.literal("No gym stadium areas set."))
            return 0
        }
        areas.forEach { (id, area) ->
            val playerSpot = if (area.playerSpot.configured) "${area.playerSpot.x},${area.playerSpot.y},${area.playerSpot.z}" else "unset"
            val trainerSpot = if (area.trainerSpot.configured) "${area.trainerSpot.x},${area.trainerSpot.y},${area.trainerSpot.z}" else "unset"
            context.source.sendSystemMessage(Component.literal("$id ${area.dimension} ${area.x},${area.y},${area.z} r=${area.radius} player=$playerSpot trainer=$trainerSpot"))
        }
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
        NpcQuestService.syncTo(player)
        context.source.sendSuccess({ Component.literal("Started ${league.displayName} for ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun leagueReset(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val leagueId = StringArgumentType.getString(context, "league_id")
        GymLeagueStore.resetLeague(player, leagueId)
        NpcQuestService.syncTo(player)
        context.source.sendSuccess({ Component.literal("Reset ${cleanId(leagueId)} for ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun unlock(context: CommandContext<CommandSourceStack>): Int {
        val leagueId = StringArgumentType.getString(context, "league_id")
        val encounterId = StringArgumentType.getString(context, "encounter_id")
        if (GymLeagueConfig.league(leagueId)?.encounter(encounterId) == null) return fail(context, "Unknown encounter '$encounterId'.")
        GymLeagueStore.unlock(leagueId, encounterId, NpcTime.day(context.source.server.overworld()))
        context.source.server.playerList.players.forEach { player -> checkChallengeAvailability(player) }
        context.source.sendSuccess({ Component.literal("Unlocked ${cleanId(encounterId)} in ${cleanId(leagueId)}.") }, true)
        return 1
    }

    private fun grant(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val league = GymLeagueConfig.league(StringArgumentType.getString(context, "league_id")) ?: return fail(context, "Unknown league.")
        val encounter = league.encounter(StringArgumentType.getString(context, "encounter_id")) ?: return fail(context, "Unknown encounter.")
        GymLeagueStore.grantClear(player, league, encounter)
        NpcQuestService.syncTo(player)
        context.source.sendSuccess({ Component.literal("Granted ${encounter.id} to ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun attemptsGet(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val trainerId = StringArgumentType.getString(context, "trainer_id")
        val maxAttempts = GymLeagueConfig.all().firstOrNull()?.defaults?.dailyAttemptsPerNpc ?: 3
        val cooldown = GymLeagueStore.attemptCooldownRemainingMs(player, trainerId, maxAttempts)
        context.source.sendSystemMessage(Component.literal("${player.gameProfile.name} ${cleanId(trainerId)} record: ${GymLeagueStore.attempts(player, trainerId, maxAttempts)}/$maxAttempts, cooldown=${formatCooldown(cooldown)}"))
        return 1
    }

    private fun attemptsReset(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val trainerId = StringArgumentType.getString(context, "trainer_id")
        GymLeagueStore.resetAttempts(player, trainerId)
        NpcQuestService.syncTo(player)
        context.source.sendSuccess({ Component.literal("Reset ${cleanId(trainerId)} attempts for ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun checkChallengeAvailability(player: ServerPlayer) {
        val activeLeague = GymLeagueStore.activeLeague(player)
        if (activeLeague.isBlank()) return
        val league = GymLeagueConfig.league(activeLeague) ?: return
        if (GymLeagueStore.clearedCount(player, league.id) <= 0) return
        val encounter = GymLeagueStore.nextPlayerEncounter(player, league) ?: return
        val day = NpcTime.day(player.level())
        if (!GymLeagueStore.isUnlocked(league.id, encounter.id, day)) return
        if (GymLeagueStore.hasAnnouncedAvailableEncounter(player, league.id, encounter.id)) return
        val trainer = league.trainer(encounter.trainer) ?: return
        val maxAttempts = league.defaults.dailyAttemptsPerNpc
        if (GymLeagueStore.attempts(player, trainer.id, maxAttempts) >= maxAttempts) return
        GymLeagueStore.markAvailableEncounterAnnounced(player, league.id, encounter.id)
        SnackbarNetwork.send(
            player,
            SnackbarNotification.item("cobblemon:poke_ball", "POKEMON CHALLENGE READY", "${trainer.name} is ready for your ${encounter.displayName} match.", SnackbarType.SUCCESS, SnackbarSounds.SUCCESS),
        )
        NpcQuestService.syncTo(player)
    }

    private fun availabilityReason(leagueId: String, encounterId: String, day: Long): String {
        val available = GymLeagueStore.availableDay(leagueId, encounterId) ?: return "Not on today's bracket yet"
        val days = (available - day).coerceAtLeast(1L)
        return "Available in $days in-game day${if (days == 1L) "" else "s"}"
    }

    private fun suggestEncounters(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val leagueId = runCatching { StringArgumentType.getString(context, "league_id") }.getOrDefault("")
        return SharedSuggestionProvider.suggest(GymLeagueConfig.league(leagueId)?.sequence?.map { it.id }.orEmpty(), builder)
    }

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED))
        return 0
    }

    private fun formatCooldown(ms: Long): String {
        if (ms <= 0L) return "ready"
        val totalSeconds = ((ms + 999L) / 1000L).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "${minutes}m ${seconds.toString().padStart(2, '0')}s" else "${seconds}s"
    }
}
