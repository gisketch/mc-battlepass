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

    private fun onServerStarted(event: ServerStartedEvent) {
        BattlepassXpStore.load()
        BattlepassMissionProgressStore.load()
        BattlepassNetwork.syncAllPlayers()
    }
}