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
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.bufferedReader

object GymBattleService {
    private const val API_ID = ChowKingdomMod.MOD_ID
    private var api: RCTApi? = null
    private var gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val activeStates: MutableMap<UUID, BattleState> = linkedMapOf()
    private var listenerRegistered = false
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

    fun startBattle(player: ServerPlayer, npc: ChowNpcEntity, league: GymLeagueDefinition, encounter: GymEncounterDefinition, trainer: GymTrainerDefinition): GymStartBattleResult {
        val instance = api ?: return GymStartBattleResult(false, "The trainer battle API is not ready yet.")
        return runCatching {
            registerPlayer(player)
            val playerTrainer = instance.trainerRegistry.getById(playerTrainerId(player), TrainerPlayer::class.java)
                ?: TrainerPlayer(player).also { instance.trainerRegistry.registerPlayer(playerTrainerId(player), it) }
            val npcTrainer = registerEncounterTrainer(instance, player.server, npc, league, encounter)
                ?: return GymStartBattleResult(false, "Could not prepare ${trainer.name}'s battle team.")
            npcTrainer.setEntity(npc)
            val rules = BattleRules.Builder()
                .withMaxItemUses(league.defaults.maxItemUses)
                .withHealPlayers(league.defaults.healPlayers)
                .withAdjustPlayerLevels(false)
                .withAdjustNPCLevels(false)
                .build()
            val format = runCatching { BattleFormat.valueOf(league.defaults.battleFormat) }.getOrDefault(BattleFormat.GEN_9_SINGLES)
            val uuid = instance.battleManager.startBattle(listOf(playerTrainer), listOf(npcTrainer), format, rules)
                ?: return GymStartBattleResult(false, "RCT refused to start the battle.")
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
                ),
            )
            GymStartBattleResult(true, "Battle started.", uuid)
        }.getOrElse { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to start gym battle", exception)
            GymStartBattleResult(false, "The battle failed to start. Check the trainer JSON and RCT logs.")
        }
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

    private fun handleBattleEnded(state: BattleState) {
        val uuid = activeStates.entries.firstOrNull { (_, active) -> active === state || active.battle == state.battle }?.key ?: return
        activeStates.remove(uuid)
        val context = GymLeagueStore.takeBattle(uuid) ?: return
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
