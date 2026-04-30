package dev.gisketch.chowkingdom

import com.mojang.logging.LogUtils
import dev.gisketch.chowkingdom.battlepass.BattlepassClient
import dev.gisketch.chowkingdom.battlepass.BattlepassCommands
import dev.gisketch.chowkingdom.battlepass.BattlepassFarmersDelightEventIntegration
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionProgressStore
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.battlepass.BattlepassPassRegistry
import dev.gisketch.chowkingdom.battlepass.BattlepassQualityFoodEventIntegration
import dev.gisketch.chowkingdom.battlepass.BattlepassVanillaEventIntegration
import dev.gisketch.chowkingdom.battlepass.BattlepassWorldData
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.battlepass.CobblemonBattlepassIntegration
import dev.gisketch.chowkingdom.client.ChowKingdomHud
import dev.gisketch.chowkingdom.discord.DiscordFeature
import dev.gisketch.chowkingdom.shipping.ShippingBinClient
import dev.gisketch.chowkingdom.profiles.ProfilesFeature
import dev.gisketch.chowkingdom.shipping.ShippingBinFeature
import dev.gisketch.chowkingdom.shops.ShopsFeature
import dev.gisketch.chowkingdom.wallets.WalletsFeature
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import org.slf4j.Logger

@Mod(ChowKingdomMod.MOD_ID)
class ChowKingdomMod(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Loading {}", container.modInfo.displayName)
        BattlepassPassRegistry.reload()
        BattlepassXpStore.load()
        BattlepassMissionProgressStore.load()
        BattlepassNetwork.register(modBus)
        BattlepassWorldData.register()
        BattlepassVanillaEventIntegration.register()
        BattlepassQualityFoodEventIntegration.register()
        BattlepassFarmersDelightEventIntegration.register()
        CobblemonBattlepassIntegration.register()
        BattlepassCommands.register()
        WalletsFeature.register(modBus)
        ShippingBinFeature.register(modBus)
        ShopsFeature.register()
        ProfilesFeature.register()
        DiscordFeature.register()
        if (FMLEnvironment.dist == Dist.CLIENT) {
            BattlepassClient.register(modBus)
            ChowKingdomHud.register(modBus)
            ShippingBinClient.register(modBus)
        }
    }

    companion object {
        const val MOD_ID = "gisketchs_chowkingdom_mod"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}