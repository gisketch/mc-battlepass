package dev.gisketch.chowkingdom.shops

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.JadeIds
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig
import snownee.jade.api.ui.IElement
import snownee.jade.api.ui.IElementHelper
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
        replaceObjectNameWithPrice(tooltip, shop.price)
        tooltip.add(Component.translatable("jade.${ChowKingdomMod.MOD_ID}.seller", shop.ownerName.ifBlank { "Unknown" }))
        tooltip.add(Component.translatable("jade.${ChowKingdomMod.MOD_ID}.quantity", format(shop.stockCount.toLong())))
    }

    override fun getUid(): ResourceLocation = UID

    private fun replaceObjectNameWithPrice(tooltip: ITooltip, price: Long) {
        val replaced = tooltip.replace(JadeIds.CORE_OBJECT_NAME) { listOf(priceElements(price)) }
        if (!replaced) tooltip.add(0, priceElements(price))
    }

    private fun priceElements(price: Long): List<IElement> = listOf(
        IElementHelper.get().sprite(CHOWCOIN_SPRITE, PRICE_ICON_SIZE, PRICE_ICON_SIZE),
        IElementHelper.get().text(Component.literal(" ${format(price)}").withStyle(ChatFormatting.WHITE)),
    )

    private fun format(amount: Long): String = String.format(Locale.US, "%,d", amount)

    private val CHOWCOIN_SPRITE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "chowcoin")
    private const val PRICE_ICON_SIZE = 10
}
