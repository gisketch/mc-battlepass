package dev.gisketch.chowkingdom.shops

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import java.util.Locale

object ShopsClient {
    fun register(modBus: IEventBus) {
        modBus.addListener(::onRegisterRenderers)
        modBus.addListener(::onRegisterScreens)
    }

    private fun onRegisterRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(ShopsFeature.SHOP_BLOCK_ENTITY.get(), ::ShopBlockEntityRenderer)
    }

    private fun onRegisterScreens(event: RegisterMenuScreensEvent) {
        event.register(ShopsFeature.SHOP_STOCK_MENU.get(), ::ShopStockScreen)
    }
}

class ShopStockScreen(menu: ShopStockMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<ShopStockMenu>(menu, inventory, title) {
    private var priceInput: EditBox? = null
    private var lastSentPrice = menu.price

    init {
        imageWidth = 176
        imageHeight = 120
        inventoryLabelY = 96
        titleLabelX = 12
    }

    override fun init() {
        super.init()
        priceInput = addRenderableWidget(EditBox(font, leftPos + 66, topPos + 54, 78, 20, Component.literal("Price")).also { input ->
            input.setFilter { value -> value.isEmpty() || value.all(Char::isDigit) }
            input.setMaxLength(10)
            input.active = menu.canEdit
            input.setValue(menu.price.toString())
            input.setResponder(::onPriceChanged)
        })
        addRenderableWidget(Button.builder(Component.literal("Done")) { onClose() }.bounds(leftPos + 62, topPos + 86, 52, 20).build())
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderStockTooltip(guiGraphics, mouseX, mouseY)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0101010.toInt())
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xF02B2118.toInt())
        renderStock(guiGraphics)
        renderChowcoin(guiGraphics, leftPos + 46, topPos + 56)
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xF5E6B3, false)
        val owner = if (menu.ownerName.isBlank()) "No owner" else "Owned by ${menu.ownerName}"
        guiGraphics.drawString(font, owner, 12, 20, 0xD8C7A4, false)
        guiGraphics.drawString(font, "Stock: ${format(displayStockCount())} / ${format(ShopBlockEntity.MAX_STOCK)}", 40, 36, 0xD8C7A4, false)
        if (!menu.canEdit) guiGraphics.drawString(font, "Owner only", 66, 76, 0xFF7878, false)
    }

    private fun renderStock(guiGraphics: GuiGraphics) {
        val x = leftPos + 14
        val y = topPos + 32
        val stock = displayStock()
        if (stock.isEmpty) {
            guiGraphics.drawString(font, "Right-click an item into stock", x, y + 4, 0xA89274, false)
            return
        }
        guiGraphics.renderItem(stock, x, y)
        guiGraphics.renderItemDecorations(font, stock, x, y, "")
    }

    private fun renderStockTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val stock = displayStock()
        if (stock.isEmpty) return
        val x = leftPos + 14
        val y = topPos + 32
        if (mouseX !in x until x + 16 || mouseY !in y until y + 16) return
        val owner = if (menu.ownerName.isBlank()) "No owner" else menu.ownerName
        guiGraphics.renderComponentTooltip(
            font,
            listOf(
                stock.hoverName,
                Component.literal("Owned by: $owner").withStyle(ChatFormatting.GRAY),
                Component.literal("Stock: ${format(displayStockCount())}").withStyle(ChatFormatting.YELLOW),
                Component.literal("Price: ${format(lastSentPrice)} Chowcoins").withStyle(ChatFormatting.GOLD),
            ),
            mouseX,
            mouseY,
        )
    }

    private fun onPriceChanged(value: String) {
        if (!menu.canEdit) return
        val amount = value.toLongOrNull()?.coerceIn(0L, ShopBlockEntity.MAX_PRICE) ?: 0L
        if (amount == lastSentPrice) return
        lastSentPrice = amount
        ShopStockNetwork.sendPrice(menu.pos, amount)
    }

    private fun renderChowcoin(guiGraphics: GuiGraphics, x: Int, y: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(CHOWCOIN_TEXTURE, x, y, 16, 16, 0.0f, 0.0f, 16, 16, 16, 16)
    }

    private fun displayStock(): net.minecraft.world.item.ItemStack =
        (Minecraft.getInstance().level?.getBlockEntity(menu.pos) as? ShopBlockEntity)?.stock ?: menu.stock

    private fun displayStockCount(): Int =
        (Minecraft.getInstance().level?.getBlockEntity(menu.pos) as? ShopBlockEntity)?.stockCount ?: menu.stockCount

    private fun format(amount: Int): String = String.format(Locale.US, "%,d", amount)

    private fun format(amount: Long): String = String.format(Locale.US, "%,d", amount)

    companion object {
        private val CHOWCOIN_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/chowcoin.png")
    }
}
