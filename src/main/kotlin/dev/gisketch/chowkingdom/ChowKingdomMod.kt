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
import dev.gisketch.chowkingdom.client.ChowKingdomConfigScreen
import dev.gisketch.chowkingdom.client.ChowDeathScreenClient
import dev.gisketch.chowkingdom.client.ChowKingdomHud
import dev.gisketch.chowkingdom.client.InventoryMenuClient
import dev.gisketch.chowkingdom.client.PlayerListHudClient
import dev.gisketch.chowkingdom.compat.UnifiedStaminaFeature
import dev.gisketch.chowkingdom.discord.DiscordFeature
import dev.gisketch.chowkingdom.discord.DiscordScreenshotClient
import dev.gisketch.chowkingdom.revive.ReviveClient
import dev.gisketch.chowkingdom.revive.ReviveFeature
import dev.gisketch.chowkingdom.roles.RolesFeature
import dev.gisketch.chowkingdom.shipping.ShippingBinClient
import dev.gisketch.chowkingdom.profiles.ProfilesFeature
import dev.gisketch.chowkingdom.shipping.ShippingBinFeature
import dev.gisketch.chowkingdom.shops.ShopsClient
import dev.gisketch.chowkingdom.shops.ShopsFeature
import dev.gisketch.chowkingdom.snackbar.SnackbarClient
import dev.gisketch.chowkingdom.snackbar.SnackbarFeature
import dev.gisketch.chowkingdom.trading.TradingClient
import dev.gisketch.chowkingdom.trading.TradingFeature
import dev.gisketch.chowkingdom.wallets.WalletsFeature
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
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
        ShopsFeature.register(modBus)
        ProfilesFeature.register(modBus)
        RolesFeature.register(modBus)
        UnifiedStaminaFeature.register()
        ReviveFeature.register(modBus)
        TradingFeature.register(modBus)
        DiscordFeature.register()
        SnackbarFeature.register(modBus)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerExtensionPoint(IConfigScreenFactory::class.java) {
                IConfigScreenFactory { _, modListScreen -> ChowKingdomConfigScreen(modListScreen) }
            }
            BattlepassClient.register(modBus)
            ChowDeathScreenClient.register()
            ChowKingdomHud.register(modBus)
            InventoryMenuClient.register()
            PlayerListHudClient.register(modBus)
            DiscordScreenshotClient.register(modBus)
            ShippingBinClient.register(modBus)
            ShopsClient.register(modBus)
            ReviveClient.register(modBus)
            TradingClient.register(modBus)
            SnackbarClient.register(modBus)
        }
    }

    companion object {
        const val MOD_ID = "gisketchs_chowkingdom_mod"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
