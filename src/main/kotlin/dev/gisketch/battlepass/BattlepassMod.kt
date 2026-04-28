package dev.gisketch.battlepass

import com.mojang.logging.LogUtils
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import org.slf4j.Logger

@Mod(BattlepassMod.MOD_ID)
class BattlepassMod(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Loading {}", container.modInfo.displayName)
        BattlepassPassRegistry.reload()
        BattlepassXpStore.load()
        BattlepassCommands.register()
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, BattlepassConfig.SPEC)
            BattlepassClient.register(modBus, container)
        }
    }

    companion object {
        const val MOD_ID = "gisketchs_battlepass"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}