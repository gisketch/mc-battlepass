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
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.client.event.RenderTooltipEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.Locale

object ShippingBinClient {
    private val CHOWCOIN_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/chowcoin.png")
    private val LOCKED_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
    private val CKDM_BOLD_FONT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
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
        val price = stackValue(event.itemStack, mutableMapOf())
        if (price <= 0L) return
        val itemKey = ShippingBinRules.itemKey(event.itemStack)
        event.tooltipElements.add(1.coerceAtMost(event.tooltipElements.size), Either.right(ShippingBinPriceTooltip(ShippingBinClientState.quotaUsed(itemKey), ShippingBinClientState.weeklyQuota())))
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

        renderHeader(event.guiGraphics, screen)
        renderTitle(event.guiGraphics, screen, previousPreview, shownPreview)
        renderLockedSlots(event.guiGraphics, screen, menu, event.mouseX, event.mouseY)
    }

    private fun previewValue(menu: ChestMenu): Long {
        val access = ShippingBinClientState.access()
        val previewCounts = mutableMapOf<String, Int>()
        return menu.slots.take(SHIPPING_BIN_SLOTS).filterIndexed { index, _ -> index < access.unlockedSlots }.sumOf { slot ->
            stackValue(slot.item, previewCounts)
        }
    }

    private fun stackValue(stack: ItemStack, previewCounts: MutableMap<String, Int>): Long {
        if (stack.isEmpty) return 0L
        val price = ShippingBinConfig.priceFor(stack)
        if (price <= 0L) return 0L
        val itemKey = ShippingBinRules.itemKey(stack)
        val used = ShippingBinClientState.quotaUsed(itemKey) + previewCounts.getOrDefault(itemKey, 0)
        val normalCount = (ShippingBinClientState.weeklyQuota() - used).coerceIn(0, stack.count)
        val reducedCount = (stack.count - normalCount).coerceAtLeast(0)
        previewCounts[itemKey] = previewCounts.getOrDefault(itemKey, 0) + stack.count
        return price * normalCount.toLong() + (price * reducedCount.toLong()) / 10L
    }

    private fun renderHeader(guiGraphics: GuiGraphics, screen: Screen) {
        val minecraft = Minecraft.getInstance()
        val component = ckdmText("Shipping Bin")
        val x = screen.width / 2 - minecraft.font.width(component) / 2
        val y = ((screen.height - CHEST_HEIGHT) / 2 - HEADER_TOP_GAP).coerceAtLeast(4)
        guiGraphics.drawString(minecraft.font, component, x + 1, y + 1, HEADER_SHADOW_COLOR, false)
        guiGraphics.drawString(minecraft.font, component, x, y, HEADER_COLOR, false)
    }

    private fun renderTitle(guiGraphics: GuiGraphics, screen: Screen, previous: Long, current: Long) {
        val minecraft = Minecraft.getInstance()
        val x = (screen.width - CHEST_WIDTH) / 2 + TITLE_X
        val y = (screen.height - CHEST_HEIGHT) / 2 + TITLE_Y
        renderIcon(guiGraphics, x, y - 2)

        val balance = ckdmText(formatChowcoins(ChowcoinClientState.balance()))
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
            guiGraphics.drawString(minecraft.font, ckdmText("+${formatChowcoins(previous)}"), x, oldY, colorWithAlpha(PREVIEW_COLOR, oldAlpha), false)
        }

        val newAlpha = if (previous == current) 1.0f else progress
        val newY = y + ((1.0f - progress) * PREVIEW_SLIDE).toInt()
        guiGraphics.drawString(minecraft.font, ckdmText("+${formatChowcoins(current)}"), x, newY, colorWithAlpha(PREVIEW_COLOR, newAlpha), false)
    }

    private fun renderIcon(guiGraphics: GuiGraphics, x: Int, y: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(CHOWCOIN_TEXTURE, x, y, TITLE_ICON_SIZE, TITLE_ICON_SIZE, 0.0f, 0.0f, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE)
    }

    private fun renderLockedSlots(guiGraphics: GuiGraphics, screen: Screen, menu: ChestMenu, mouseX: Int, mouseY: Int) {
        val access = ShippingBinClientState.access()
        val left = (screen.width - CHEST_WIDTH) / 2
        val top = (screen.height - CHEST_HEIGHT) / 2
        var tooltip: Component? = null
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        menu.slots.take(SHIPPING_BIN_SLOTS).forEachIndexed { index, slot ->
            if (index < access.unlockedSlots) return@forEachIndexed
            val x = left + slot.x
            val y = top + slot.y
            guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, LOCKED_SLOT_OVERLAY)
            guiGraphics.blit(LOCKED_TEXTURE, x, y, SLOT_SIZE, SLOT_SIZE, 0.0f, 0.0f, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE)
            if (mouseX in x until x + SLOT_SIZE && mouseY in y until y + SLOT_SIZE) {
                tooltip = lockedTooltip(index)
            }
        }
        tooltip?.let { guiGraphics.renderTooltip(Minecraft.getInstance().font, it, mouseX, mouseY) }
    }

    private fun lockedTooltip(slot: Int): Component {
        val unlockLevel = ShippingBinRules.unlockLevelForSlot(slot)
        val text = if (unlockLevel == null) "Max 27 shipping slots" else "Unlock slot at level $unlockLevel"
        return ckdmText(text)
    }

    private fun colorWithAlpha(color: Int, alpha: Float): Int = (Mth.clamp(alpha, 0.0f, 1.0f) * 255).toInt() shl 24 or (color and 0x00FFFFFF)

    private fun ckdmText(text: String): Component = Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(CKDM_BOLD_FONT) }

    private const val SHIPPING_BIN_SLOTS = 27
    private const val CHEST_WIDTH = 176
    private const val CHEST_HEIGHT = 168
    private const val TITLE_X = 8
    private const val TITLE_Y = 6
    private const val HEADER_TOP_GAP = 14
    private const val TITLE_ICON_SIZE = 12
    private const val TITLE_ICON_GAP = 4
    private const val COINS_TEXTURE_SIZE = 16
    private const val PREVIEW_GAP = 10
    private const val PREVIEW_SLIDE = 7
    private const val PREVIEW_ANIMATION_MS = 160L
    private const val SLOT_SIZE = 16
    private const val LOCKED_TEXTURE_SIZE = 16
    private const val LOCKED_SLOT_OVERLAY = 0x99000000.toInt()
    private const val HEADER_COLOR = 0xFFFFF2B8.toInt()
    private const val HEADER_SHADOW_COLOR = 0xFF2D1B10.toInt()
    private const val BALANCE_COLOR = 0xFFFFFFFF.toInt()
    private const val PREVIEW_COLOR = 0xFF5CFF8D.toInt()
}

private fun formatChowcoins(amount: Long): String = String.format(Locale.US, "%,d", amount)

class ShippingBinPriceTooltip(val quotaUsed: Int, val quotaLimit: Int) : TooltipComponent

class ShippingBinPriceClientTooltip(private val tooltip: ShippingBinPriceTooltip) : ClientTooltipComponent {
    override fun getHeight(): Int = 11

    override fun getWidth(font: Font): Int = font.width(quotaLine())

    override fun renderImage(font: Font, x: Int, y: Int, guiGraphics: GuiGraphics) {
        guiGraphics.drawString(font, quotaLine(), x, y + 1, TOOLTIP_TEXT_COLOR, false)
    }

    private fun quotaLine(): String {
        val used = tooltip.quotaUsed.coerceAtLeast(0)
        val limit = tooltip.quotaLimit.coerceAtLeast(1)
        return if (used >= limit) "Weekly quota: $used/$limit (10% profit)" else "Weekly quota: $used/$limit"
    }

    companion object {
        private const val TOOLTIP_TEXT_COLOR = 0xFFFFFFFF.toInt()
    }
}