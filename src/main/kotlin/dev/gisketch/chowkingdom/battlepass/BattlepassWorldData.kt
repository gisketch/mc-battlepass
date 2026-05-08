package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Path

object BattlepassWorldData {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
    }

    fun battlepassDirectory(): Path {
        val server = ServerLifecycleHooks.getCurrentServer()
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ChowKingdomMod.MOD_ID).resolve("battlepass")
        }
        return FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("battlepass")
    }

    fun battlepassFile(name: String): Path {
        val server = ServerLifecycleHooks.getCurrentServer()
        val extension = if (server != null) "json" else "toml"
        val root = if (server != null) {
            server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ChowKingdomMod.MOD_ID).resolve("battlepass")
        } else {
            FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("battlepass")
        }
        return root.resolve("$name.$extension")
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        BattlepassXpStore.load()
        BattlepassMissionProgressStore.load()
        BattlepassNetwork.syncAllPlayers()
    }
}