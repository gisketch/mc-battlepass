package dev.gisketch.chowkingdom.gyms

import com.gitlab.srcmc.rctapi.api.RCTApi
import com.gitlab.srcmc.rctapi.api.battle.BattleFormat
import com.gitlab.srcmc.rctapi.api.battle.BattleRules
import com.gitlab.srcmc.rctapi.api.battle.BattleState
import com.gitlab.srcmc.rctapi.api.events.Event
import com.gitlab.srcmc.rctapi.api.events.EventListener
import com.gitlab.srcmc.rctapi.api.events.Events
import com.gitlab.srcmc.rctapi.api.models.TrainerModel
import com.gitlab.srcmc.rctapi.api.trainer.Trainer
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC
import com.gitlab.srcmc.rctapi.api.trainer.TrainerPlayer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.npc.ChowNpcEntity
import dev.gisketch.chowkingdom.npc.NpcFeature
import dev.gisketch.chowkingdom.npc.NpcPokemonCompanions
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.RelativeMovement
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.util.EnumSet
import java.util.UUID
import kotlin.math.atan2
import kotlin.io.path.bufferedReader
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent

object GymBattleService {
    private const val API_ID = ChowKingdomMod.MOD_ID
    private var api: RCTApi? = null
    private var gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val activeStates: MutableMap<UUID, BattleState> = linkedMapOf()
    private val pendingStarts: MutableList<PendingGymBattleStart> = mutableListOf()
    private var listenerRegistered = false
    private var tickRegistered = false
    private val battleEndedListener = EventListener<BattleState> { event: Event<BattleState> -> handleBattleEnded(event.value) }

    fun init(server: MinecraftServer) {
        runCatching {
            val instance = RCTApi.getInstance(API_ID) ?: RCTApi.initInstance(API_ID)
            api = instance
            gson = instance.configureGsonBuilder(GsonBuilder().setPrettyPrinting()).create()
            instance.trainerRegistry.init(server)
            if (!listenerRegistered) {
                instance.eventContext.register(Events.BATTLE_ENDED, battleEndedListener)
                listenerRegistered = true
            }
            if (!tickRegistered) {
                NeoForge.EVENT_BUS.addListener(::onServerTick)
                tickRegistered = true
            }
            server.playerList.players.forEach(::registerPlayer)
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to initialize RCT gym API", exception)
        }
    }

    fun registerPlayer(player: ServerPlayer) {
        val instance = api ?: return
        runCatching {
            val id = playerTrainerId(player)
            instance.trainerRegistry.unregisterById(id)
            instance.trainerRegistry.registerPlayer(id, player)
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to register gym trainer player {}", player.gameProfile.name, exception)
        }
    }

    fun unregisterPlayer(player: ServerPlayer) {
        val instance = api ?: return
        runCatching { instance.trainerRegistry.unregisterById(playerTrainerId(player)) }
    }

    fun startBattle(player: ServerPlayer, npc: ChowNpcEntity, league: GymLeagueDefinition, encounter: GymEncounterDefinition, trainer: GymTrainerDefinition, official: Boolean = true): GymStartBattleResult {
        api ?: return GymStartBattleResult(false, "The trainer battle API is not ready yet.")
        if (GymLeagueStore.area(league.stadiumArea) == null) return GymStartBattleResult(false, "Gym stadium is not set. Use /ck gyms area set <radius> first.")
        GymTransitionNetwork.playBattleFade(player)
        pendingStarts.removeIf { pending -> pending.playerUuid == player.uuid && pending.trainerId == trainer.id }
        pendingStarts += PendingGymBattleStart(
            executeAtTick = player.server.overworld().gameTime + BATTLE_FADE_TELEPORT_DELAY_TICKS,
            playerUuid = player.uuid,
            leagueId = league.id,
            encounterId = encounter.id,
            trainerId = trainer.id,
            npcId = trainer.npcId,
            official = official,
        )
        return GymStartBattleResult(true, "Battle transition started.")
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        val now = server.overworld().gameTime
        val ready = pendingStarts.filter { pending -> now >= pending.executeAtTick }
        if (ready.isEmpty()) return
        pendingStarts.removeAll(ready.toSet())
        ready.forEach { pending -> executePendingStart(server, pending) }
    }

    private fun executePendingStart(server: MinecraftServer, pending: PendingGymBattleStart) {
        val instance = api ?: return
        val player = server.playerList.getPlayer(pending.playerUuid) ?: return
        val league = GymLeagueConfig.league(pending.leagueId) ?: return failPending(player, "League config missing.")
        val encounter = league.encounter(pending.encounterId) ?: return failPending(player, "Encounter config missing.")
        val trainer = league.trainer(pending.trainerId) ?: return failPending(player, "Trainer config missing.")
        val npc = NpcFeature.existingNpc(server, pending.npcId) ?: return failPending(player, "${trainer.name} is not loaded.")
        runCatching {
            prepareBattlePositions(player, npc, league)?.let { message -> return failPending(player, message) }
            NpcPokemonCompanions.suspendForBattle(trainer.npcId, server)
            registerPlayer(player)
            val playerTrainer = instance.trainerRegistry.getById(playerTrainerId(player), TrainerPlayer::class.java)
                ?: TrainerPlayer(player).also { instance.trainerRegistry.registerPlayer(playerTrainerId(player), it) }
            val npcTrainer = registerEncounterTrainer(instance, server, npc, league, encounter)
                ?: return failPending(player, "Could not prepare ${trainer.name}'s battle team.")
            npcTrainer.setEntity(npc)
            val rules = BattleRules.Builder()
                .withMaxItemUses(league.defaults.maxItemUses)
                .withHealPlayers(league.defaults.healPlayers)
                .withAdjustPlayerLevels(false)
                .withAdjustNPCLevels(false)
                .build()
            val format = runCatching { BattleFormat.valueOf(league.defaults.battleFormat) }.getOrDefault(BattleFormat.GEN_9_SINGLES)
            val uuid = instance.battleManager.startBattle(listOf(playerTrainer), listOf(npcTrainer), format, rules)
                ?: run {
                    NpcPokemonCompanions.resumeAfterBattle(trainer.npcId)
                    return failPending(player, "RCT refused to start the battle.")
                }
            val state = instance.battleManager.getState(uuid)
            if (state != null) activeStates[uuid] = state
            GymLeagueStore.putBattle(
                uuid,
                GymBattleContextState(
                    playerUuid = player.stringUUID,
                    playerName = player.gameProfile.name,
                    leagueId = league.id,
                    encounterId = encounter.id,
                    trainerId = trainer.id,
                    npcId = trainer.npcId,
                    official = pending.official,
                ),
            )
        }.getOrElse { exception ->
            NpcPokemonCompanions.resumeAfterBattle(trainer.npcId)
            ChowKingdomMod.LOGGER.warn("Failed to start gym battle", exception)
            failPending(player, "The battle failed to start. Check the trainer JSON and RCT logs.")
        }
    }

    private fun failPending(player: ServerPlayer, message: String) {
        SnackbarNetwork.send(player, SnackbarNotification.item(SnackbarIcons.ERROR, "BATTLE FAILED", message, SnackbarType.ERROR, SnackbarSounds.ERROR))
    }

    fun partyLevelViolation(player: ServerPlayer, cap: Int): Int? = runCatching {
        val team = TrainerPlayer(player).team
        team.mapNotNull(::pokemonLevel).firstOrNull { it > cap }
    }.getOrElse { exception ->
        ChowKingdomMod.LOGGER.debug("Could not inspect Cobblemon party levels for {}", player.gameProfile.name, exception)
        null
    }

    private fun registerEncounterTrainer(instance: RCTApi, server: MinecraftServer, npc: ChowNpcEntity, league: GymLeagueDefinition, encounter: GymEncounterDefinition): TrainerNPC? {
        val id = encounterTrainerId(league.id, encounter.id)
        val file = GymLeagueConfig.teamFile(encounter.teamRef)
        if (!Files.exists(file)) return null
        val model = file.bufferedReader().use { reader -> gson.fromJson(reader, TrainerModel::class.java) } ?: return null
        instance.trainerRegistry.unregisterById(id)
        val registered = instance.trainerRegistry.registerNPC(id, model)
        registered.setEntity(npc)
        return registered
    }

    private fun prepareBattlePositions(player: ServerPlayer, npc: ChowNpcEntity, league: GymLeagueDefinition): String? {
        val area = GymLeagueStore.area(league.stadiumArea) ?: return "Gym stadium is not set. Use /ck gyms area set <radius> first."
        val dimensionId = area.playerSpot.dimension.ifBlank { area.dimension }
        val dimension = runCatching { ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimensionId)) }.getOrNull()
            ?: return "Gym stadium dimension is invalid: $dimensionId"
        val level = player.server.getLevel(dimension) ?: return "Gym stadium dimension is not loaded: $dimensionId"
        val playerSpot = area.playerSpot.takeIf { it.configured } ?: GymBattleSpotState(dimensionId, area.x - 3, area.y, area.z, 0.0f, 0.0f, true)
        val trainerSpot = area.trainerSpot.takeIf { it.configured } ?: GymBattleSpotState(dimensionId, area.x + 3, area.y, area.z, 180.0f, 0.0f, true)
        val playerPos = Vec3(playerSpot.x + 0.5, playerSpot.y.toDouble(), playerSpot.z + 0.5)
        val trainerPos = Vec3(trainerSpot.x + 0.5, trainerSpot.y.toDouble(), trainerSpot.z + 0.5)
        val playerYaw = yawToward(playerPos, trainerPos)
        val trainerYaw = yawToward(trainerPos, playerPos)
        player.teleportTo(level, playerPos.x, playerPos.y, playerPos.z, playerYaw, 0.0f)
        npc.navigation.stop()
        if (npc.level() != level) {
            npc.teleportTo(level, trainerPos.x, trainerPos.y, trainerPos.z, EnumSet.noneOf(RelativeMovement::class.java), trainerYaw, 0.0f)
        } else {
            npc.moveTo(trainerPos.x, trainerPos.y, trainerPos.z, trainerYaw, 0.0f)
        }
        npc.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition())
        player.lookAt(EntityAnchorArgument.Anchor.EYES, npc.getEyePosition())
        return null
    }

    private fun yawToward(from: Vec3, to: Vec3): Float {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return (atan2(dz, dx) * 180.0 / Math.PI - 90.0).toFloat()
    }

    private fun handleBattleEnded(state: BattleState) {
        val uuid = activeStates.entries.firstOrNull { (_, active) -> active === state || active.battle == state.battle }?.key ?: return
        activeStates.remove(uuid)
        val context = GymLeagueStore.takeBattle(uuid) ?: return
        NpcPokemonCompanions.resumeAfterBattle(context.npcId)
        handleBattleEndedByContext(state, context)
    }

    private fun handleBattleEndedByContext(state: BattleState, context: GymBattleContextState) {
        if (state.isEndForced) return
        val player = state.winners.asSequence()
            .filterIsInstance<TrainerPlayer>()
            .map { it.player }
            .firstOrNull { it.stringUUID == context.playerUuid }
            ?: return
        GymLeagueFeature.completeEncounterWin(player, context)
    }

    private fun pokemonLevel(pokemon: Any): Int? {
        pokemon.javaClass.methods.firstOrNull { it.name == "getLevel" && it.parameterCount == 0 }?.let { method ->
            return (method.invoke(pokemon) as? Number)?.toInt()
        }
        return runCatching { pokemon.javaClass.getField("level").get(pokemon) as? Number }.getOrNull()?.toInt()
    }

    private fun playerTrainerId(player: ServerPlayer): String = "player_${player.stringUUID}"

    private fun encounterTrainerId(leagueId: String, encounterId: String): String = "gym_${cleanId(leagueId)}_${cleanId(encounterId)}"
}

data class GymStartBattleResult(val started: Boolean, val message: String, val battleUuid: UUID? = null)

private data class PendingGymBattleStart(
    val executeAtTick: Long,
    val playerUuid: UUID,
    val leagueId: String,
    val encounterId: String,
    val trainerId: String,
    val npcId: String,
    val official: Boolean,
)

private const val BATTLE_FADE_TELEPORT_DELAY_TICKS = 8L
