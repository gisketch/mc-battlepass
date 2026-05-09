package dev.gisketch.chowkingdom.worlds

import com.mojang.brigadier.context.CommandContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

object WorldsFeature {
    val SKY_LANDS: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("ckdm", "sky_lands"))

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onPlayerRespawn)
        NeoForge.EVENT_BUS.addListener(::onPlayerTick)
        NeoForge.EVENT_BUS.addListener(::onSpawnPlacementCheck)
        NeoForge.EVENT_BUS.addListener(::onPositionCheck)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.persistentData.getBoolean(FIRST_SKY_LANDS_SPAWN_TAG)) return
        player.persistentData.putBoolean(FIRST_SKY_LANDS_SPAWN_TAG, true)
        player.server.execute { sendToSkyLands(player, setRespawn = true) }
    }

    private fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (event.isEndConquered || player.respawnPosition != null) return
        player.server.execute { sendToSkyLands(player, setRespawn = true) }
    }

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (!isSkyLands(player.level()) || player.y > SKY_LANDS_FALLTHROUGH_Y) return
        val overworld = player.server.overworld()
        val velocity = player.deltaMovement
        player.teleportTo(overworld, player.x, OVERWORLD_FALLTHROUGH_Y, player.z, player.yRot, player.xRot)
        player.deltaMovement = velocity
        player.hurtMarked = true
    }

    private fun onSpawnPlacementCheck(event: MobSpawnEvent.SpawnPlacementCheck) {
        if (!isSkyLands(event.level.level) || !blocksNaturalMobSpawn(event.spawnType)) return
        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL)
    }

    private fun onPositionCheck(event: MobSpawnEvent.PositionCheck) {
        if (!isSkyLands(event.level.level) || !blocksNaturalMobSpawn(event.spawnType)) return
        event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("ck").then(worldsRoot()))
        event.dispatcher.register(Commands.literal("chowkingdom").then(worldsRoot()))
    }

    private fun worldsRoot() = Commands.literal("worlds")
        .requires { source -> source.hasPermission(2) }
        .then(Commands.literal("status").executes(::statusSelf).then(Commands.argument("player", EntityArgument.player()).executes(::status)))
        .then(Commands.literal("sky_lands").executes(::skyLandsSelf).then(Commands.argument("player", EntityArgument.player()).executes(::skyLands)))
        .then(Commands.literal("hub").executes(::skyLandsSelf).then(Commands.argument("player", EntityArgument.player()).executes(::skyLands)))
        .then(Commands.literal("overworld").executes(::overworldSelf).then(Commands.argument("player", EntityArgument.player()).executes(::overworld)))
        .then(Commands.literal("base").executes(::overworldSelf).then(Commands.argument("player", EntityArgument.player()).executes(::overworld)))

    private fun statusSelf(context: CommandContext<CommandSourceStack>): Int = status(context, context.source.playerOrException)

    private fun status(context: CommandContext<CommandSourceStack>): Int = status(context, EntityArgument.getPlayer(context, "player"))

    private fun status(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        val skyLoaded = context.source.server.getLevel(SKY_LANDS) != null
        context.source.sendSuccess(
            {
                val skySpawn = context.source.server.getLevel(SKY_LANDS)?.sharedSpawnPos
                Component.literal("Worlds: ${player.gameProfile.name} is in ${player.level().dimension().location()} | sky_lands_loaded=$skyLoaded | sky_spawn=$skySpawn")
                    .withStyle(if (skyLoaded) ChatFormatting.GREEN else ChatFormatting.RED)
            },
            false,
        )
        return 1
    }

    private fun skyLandsSelf(context: CommandContext<CommandSourceStack>): Int = skyLands(context, context.source.playerOrException)

    private fun skyLands(context: CommandContext<CommandSourceStack>): Int = skyLands(context, EntityArgument.getPlayer(context, "player"))

    private fun skyLands(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        return if (sendToSkyLands(player, setRespawn = true)) {
            context.source.sendSuccess({ Component.literal("Sent ${player.gameProfile.name} to Sky Lands.").withStyle(ChatFormatting.GREEN) }, true)
            1
        } else {
            context.source.sendFailure(Component.literal("Sky Lands is not loaded. Check Sky Archipelago and ckdm:sky_lands datapack."))
            0
        }
    }

    private fun overworldSelf(context: CommandContext<CommandSourceStack>): Int = overworld(context, context.source.playerOrException)

    private fun overworld(context: CommandContext<CommandSourceStack>): Int = overworld(context, EntityArgument.getPlayer(context, "player"))

    private fun overworld(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        val level = context.source.server.overworld()
        val pos = level.sharedSpawnPos
        player.teleportTo(level, pos.x + 0.5, (pos.y + 1).toDouble(), pos.z + 0.5, player.yRot, player.xRot)
        context.source.sendSuccess({ Component.literal("Sent ${player.gameProfile.name} to the normal overworld.").withStyle(ChatFormatting.GREEN) }, true)
        return 1
    }

    private fun sendToSkyLands(player: ServerPlayer, setRespawn: Boolean): Boolean {
        val level = player.server.getLevel(SKY_LANDS) ?: return false
        val pos = level.sharedSpawnPos
        if (setRespawn) player.setRespawnPosition(SKY_LANDS, pos, 0.0f, true, false)
        player.teleportTo(level, pos.x + 0.5, (pos.y + 1).toDouble(), pos.z + 0.5, 0.0f, 0.0f)
        return true
    }

    private fun blocksNaturalMobSpawn(spawnType: MobSpawnType): Boolean = spawnType in BLOCKED_SPAWN_TYPES

    private fun isSkyLands(level: Level): Boolean = level.dimension() == SKY_LANDS

    private val BLOCKED_SPAWN_TYPES = setOf(
        MobSpawnType.NATURAL,
        MobSpawnType.CHUNK_GENERATION,
        MobSpawnType.STRUCTURE,
        MobSpawnType.SPAWNER,
        MobSpawnType.PATROL,
        MobSpawnType.REINFORCEMENT,
        MobSpawnType.TRIAL_SPAWNER,
    )
    private const val FIRST_SKY_LANDS_SPAWN_TAG = "ckdm_first_sky_lands_spawn"
    private const val SKY_LANDS_FALLTHROUGH_Y = 48.0
    private const val OVERWORLD_FALLTHROUGH_Y = 320.0
}
