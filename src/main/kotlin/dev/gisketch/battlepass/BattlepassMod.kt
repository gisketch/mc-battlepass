package dev.gisketch.battlepass

import com.mojang.logging.LogUtils
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import org.slf4j.Logger

@Mod(value = BattlepassMod.MOD_ID, dist = [Dist.CLIENT])
class BattlepassMod(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Loading {}", container.modInfo.displayName)
        container.registerConfig(ModConfig.Type.CLIENT, BattlepassConfig.SPEC)
        container.registerExtensionPoint(IConfigScreenFactory::class.java, IConfigScreenFactory { modContainer, parent ->
            ConfigurationScreen(modContainer, parent)
        })
        BattlepassClient.register(modBus)
    }

    companion object {
        const val MOD_ID = "gisketchs_battlepass"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}