package dev.gisketch.chowkingdom.shipping

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.datafixers.util.Either
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.wallets.ChowcoinClientState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.client.event.RenderTooltipEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.Locale

object ShippingBinClient {
    private val COINS_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/coins.png")
    private var activeScreen: Screen? = null
    private var shownPreview = 0L
    private var previousPreview = 0L
    private var changedAt = 0L

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerTooltipFactories)
        NeoForge.EVENT_BUS.addListener(::onScreenRenderPost)
        NeoForge.EVENT_BUS.addListener(::onGatherTooltipComponents)
    }

    private fun registerTooltipFactories(event: RegisterClientTooltipComponentFactoriesEvent) {
        event.register(ShippingBinPriceTooltip::class.java, ::ShippingBinPriceClientTooltip)
    }

    private fun onGatherTooltipComponents(event: RenderTooltipEvent.GatherComponents) {
        val price = ShippingBinConfig.priceFor(event.itemStack)
        if (price <= 0L) return
        event.tooltipElements.add(1.coerceAtMost(event.tooltipElements.size), Either.right(ShippingBinPriceTooltip(price)))
    }

    private fun onScreenRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        val menu = screen.menu as? ChestMenu ?: return
        if (screen.title.string.isNotBlank()) return
        if (menu.slots.size < SHIPPING_BIN_SLOTS) return

        if (activeScreen !== screen) {
            activeScreen = screen
            shownPreview = previewValue(menu)
            previousPreview = shownPreview
            changedAt = System.currentTimeMillis() - PREVIEW_ANIMATION_MS
        }

        val preview = previewValue(menu)
        if (preview != shownPreview) {
            previousPreview = shownPreview
            shownPreview = preview
            changedAt = System.currentTimeMillis()
        }

        renderTitle(event.guiGraphics, screen, previousPreview, shownPreview)
    }

    private fun previewValue(menu: ChestMenu): Long = menu.slots.take(SHIPPING_BIN_SLOTS).sumOf { slot ->
        val stack = slot.item
        ShippingBinConfig.priceFor(stack) * stack.count.toLong()
    }

    private fun renderTitle(guiGraphics: GuiGraphics, screen: Screen, previous: Long, current: Long) {
        val minecraft = Minecraft.getInstance()
        val x = (screen.width - CHEST_WIDTH) / 2 + TITLE_X
        val y = (screen.height - SIX_ROW_CHEST_HEIGHT) / 2 + TITLE_Y
        renderIcon(guiGraphics, x, y - 2)

        val balance = formatChowcoins(ChowcoinClientState.balance())
        val balanceX = x + TITLE_ICON_SIZE + TITLE_ICON_GAP
        guiGraphics.drawString(minecraft.font, balance, balanceX, y, BALANCE_COLOR, false)
        val previewX = balanceX + minecraft.font.width(balance) + PREVIEW_GAP
        renderAnimatedPreview(guiGraphics, minecraft, previewX, y, previous, current)
    }

    private fun renderAnimatedPreview(guiGraphics: GuiGraphics, minecraft: Minecraft, x: Int, y: Int, previous: Long, current: Long) {
        val progress = ((System.currentTimeMillis() - changedAt) / PREVIEW_ANIMATION_MS.toFloat()).coerceIn(0.0f, 1.0f)
        if (progress < 1.0f && previous != current) {
            val oldAlpha = 1.0f - progress
            val oldY = y + (progress * PREVIEW_SLIDE).toInt()
            guiGraphics.drawString(minecraft.font, "+${formatChowcoins(previous)}", x, oldY, colorWithAlpha(PREVIEW_COLOR, oldAlpha), false)
        }

        val newAlpha = if (previous == current) 1.0f else progress
        val newY = y + ((1.0f - progress) * PREVIEW_SLIDE).toInt()
        guiGraphics.drawString(minecraft.font, "+${formatChowcoins(current)}", x, newY, colorWithAlpha(PREVIEW_COLOR, newAlpha), false)
    }

    private fun renderIcon(guiGraphics: GuiGraphics, x: Int, y: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(COINS_TEXTURE, x, y, TITLE_ICON_SIZE, TITLE_ICON_SIZE, 0.0f, 0.0f, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE)
    }

    private fun colorWithAlpha(color: Int, alpha: Float): Int = (Mth.clamp(alpha, 0.0f, 1.0f) * 255).toInt() shl 24 or (color and 0x00FFFFFF)

    private const val SHIPPING_BIN_SLOTS = 54
    private const val CHEST_WIDTH = 176
    private const val SIX_ROW_CHEST_HEIGHT = 222
    private const val TITLE_X = 8
    private const val TITLE_Y = 6
    private const val TITLE_ICON_SIZE = 12
    private const val TITLE_ICON_GAP = 4
    private const val COINS_TEXTURE_SIZE = 16
    private const val PREVIEW_GAP = 10
    private const val PREVIEW_SLIDE = 7
    private const val PREVIEW_ANIMATION_MS = 160L
    private const val BALANCE_COLOR = 0xFFFFFFFF.toInt()
    private const val PREVIEW_COLOR = 0xFF5CFF8D.toInt()
}

private fun formatChowcoins(amount: Long): String = String.format(Locale.US, "%,d", amount)

class ShippingBinPriceTooltip(val amount: Long) : TooltipComponent

class ShippingBinPriceClientTooltip(private val tooltip: ShippingBinPriceTooltip) : ClientTooltipComponent {
    override fun getHeight(): Int = 13

    override fun getWidth(font: Font): Int = TOOLTIP_ICON_SIZE + TOOLTIP_TEXT_GAP + font.width(formatChowcoins(tooltip.amount))

    override fun renderImage(font: Font, x: Int, y: Int, guiGraphics: GuiGraphics) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(COINS_TEXTURE, x, y, TOOLTIP_ICON_SIZE, TOOLTIP_ICON_SIZE, 0.0f, 0.0f, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE)
        guiGraphics.drawString(font, formatChowcoins(tooltip.amount), x + TOOLTIP_ICON_SIZE + TOOLTIP_TEXT_GAP, y + 2, TOOLTIP_TEXT_COLOR, false)
    }

    companion object {
        private val COINS_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/coins.png")
        private const val COINS_TEXTURE_SIZE = 16
        private const val TOOLTIP_ICON_SIZE = 10
        private const val TOOLTIP_TEXT_GAP = 4
        private const val TOOLTIP_TEXT_COLOR = 0xFFFFFFFF.toInt()
    }
}