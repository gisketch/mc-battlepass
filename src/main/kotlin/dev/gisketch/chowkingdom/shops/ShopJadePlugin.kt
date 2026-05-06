package dev.gisketch.chowkingdom.shops

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig
import java.util.Locale

@WailaPlugin
class ShopJadePlugin : IWailaPlugin {
    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(ShopJadeProvider, StockShopBlock::class.java)
    }
}

object ShopJadeProvider : IBlockComponentProvider {
    private val UID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shop_stock")

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val shop = accessor.blockEntity as? ShopBlockEntity ?: return
        if (shop.stock.isEmpty) return
        tooltip.add(Component.translatable("jade.${ChowKingdomMod.MOD_ID}.seller", shop.ownerName.ifBlank { "Unknown" }))
        tooltip.add(Component.translatable("jade.${ChowKingdomMod.MOD_ID}.quantity", format(shop.stockCount.toLong())))
        tooltip.add(Component.translatable("jade.${ChowKingdomMod.MOD_ID}.price", format(shop.price)))
    }

    override fun getUid(): ResourceLocation = UID

    private fun format(amount: Long): String = String.format(Locale.US, "%,d", amount)
}
