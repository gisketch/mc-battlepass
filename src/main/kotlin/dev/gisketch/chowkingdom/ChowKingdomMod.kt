package dev.gisketch.chowkingdom

import com.mojang.logging.LogUtils
import dev.gisketch.chowkingdom.battlepass.BattlepassClient
import dev.gisketch.chowkingdom.battlepass.BattlepassCommands
import dev.gisketch.chowkingdom.battlepass.BattlepassConfig
import dev.gisketch.chowkingdom.battlepass.BattlepassPassRegistry
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.profiles.ProfilesFeature
import dev.gisketch.chowkingdom.shops.ShopsFeature
import dev.gisketch.chowkingdom.wallets.WalletsFeature
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import org.slf4j.Logger

@Mod(ChowKingdomMod.MOD_ID)
class ChowKingdomMod(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Loading {}", container.modInfo.displayName)
        BattlepassPassRegistry.reload()
        BattlepassXpStore.load()
        BattlepassCommands.register()
        WalletsFeature.register()
        ShopsFeature.register()
        ProfilesFeature.register()
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, BattlepassConfig.SPEC)
            BattlepassClient.register(modBus, container)
        }
    }

    companion object {
        const val MOD_ID = "gisketchs_chowkingdom_mod"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}