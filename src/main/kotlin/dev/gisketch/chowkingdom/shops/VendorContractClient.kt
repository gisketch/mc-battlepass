package dev.gisketch.chowkingdom.shops

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.PacketDistributor
import java.util.Locale
import java.util.UUID

object VendorContractClient {
    private var highlighted: Set<BlockPos> = emptySet()

    fun register(modBus: IEventBus) {
        NeoForge.EVENT_BUS.addListener(::onRenderLevel)
    }

    @JvmStatic
    fun syncSelection(payload: VendorContractSelectionPayload) {
        highlighted = payload.positions.toSet()
    }

    @JvmStatic
    fun openVendor(payload: VendorOpenPayload) {
        Minecraft.getInstance().setScreen(VendorSellerScreen(payload))
    }

    private fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return
        if (highlighted.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val camera = event.camera.position
        val poseStack = event.poseStack
        val bufferSource = minecraft.renderBuffers().bufferSource()
        val buffer = bufferSource.getBuffer(RenderType.lines())
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        highlighted.forEach { pos ->
            val shop = level.getBlockEntity(pos) as? ShopBlockEntity ?: return@forEach
            val shape = shop.blockState.getShape(level, pos)
            val box = if (shape.isEmpty) AABB(pos) else shape.bounds().move(pos)
            LevelRenderer.renderLineBox(poseStack, buffer, box.inflate(0.035).move(-camera.x, -camera.y, -camera.z), 1.0f, 0.86f, 0.05f, 1.0f)
        }
        bufferSource.endBatch(RenderType.lines())
    }
}

private class VendorSellerScreen(private var payload: VendorOpenPayload) : Screen(Component.literal("Vendor")) {
    private val quantities: MutableMap<String, Int> = linkedMapOf()
    private var scroll = 0

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        val panel = panelRect()
        guiGraphics.fill(panel.x, panel.y, panel.right, panel.bottom, PANEL)
        guiGraphics.renderOutline(panel.x, panel.y, panel.width, panel.height, OUTLINE)
        drawCentered(guiGraphics, "VENDOR", panel.x, panel.y + 10, panel.width, WHITE)
        if (payload.entries.isEmpty()) {
            drawCentered(guiGraphics, "NO LOADED SHOP STOCK", panel.x, panel.y + 76, panel.width, DISABLED)
        } else {
            visibleEntries().forEachIndexed { row, entry ->
                renderEntry(guiGraphics, entry, row, mouseX, mouseY)
            }
        }
        if (payload.canVoid) renderButton(guiGraphics, voidRect(), "VOID CONTRACT", RED, mouseX, mouseY, true)
    }

    private fun renderEntry(guiGraphics: GuiGraphics, entry: VendorEntry, row: Int, mouseX: Int, mouseY: Int) {
        val rect = rowRect(row)
        guiGraphics.fill(rect.x, rect.y, rect.right, rect.bottom, ROW)
        guiGraphics.renderOutline(rect.x, rect.y, rect.width, rect.height, ROW_OUTLINE)
        if (entry.stockCount <= 0) RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.5f)
        guiGraphics.renderItem(entry.stack, rect.x + 8, rect.y + 8)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        val itemName = entry.stack.hoverName.string.uppercase(Locale.ROOT)
        guiGraphics.drawString(font, itemName.take(28), rect.x + 32, rect.y + 7, WHITE, false)
        val stockText = if (entry.stockCount <= 0) "OUT OF STOCK | ${format(entry.price)} CHOWCOINS" else "STOCK ${format(entry.stockCount)} | ${format(entry.price)} CHOWCOINS"
        guiGraphics.drawString(font, stockText, rect.x + 32, rect.y + 20, if (entry.stockCount <= 0) DISABLED else GOLD, false)
        guiGraphics.drawString(font, "SELLER ${entry.ownerName.uppercase(Locale.ROOT)}", rect.x + 32, rect.y + 33, DISABLED, false)
        renderButton(guiGraphics, minusRect(row), "-", GRAY, mouseX, mouseY, entry.stockCount > 0 && quantity(entry) > 1)
        drawCentered(guiGraphics, quantity(entry).toString(), qtyRect(row).x, qtyRect(row).y + 5, qtyRect(row).width, WHITE)
        renderButton(guiGraphics, plusRect(row), "+", GRAY, mouseX, mouseY, entry.stockCount > 0 && quantity(entry) < entry.stockCount)
        renderButton(guiGraphics, buyRect(row), "BUY", GREEN, mouseX, mouseY, entry.stockCount > 0)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        val x = mouseX.toInt()
        val y = mouseY.toInt()
        if (payload.canVoid && voidRect().contains(x, y)) {
            click()
            PacketDistributor.sendToServer(VendorVoidPayload(payload.sellerId))
            minecraft?.setScreen(null)
            return true
        }
        visibleEntries().forEachIndexed { row, entry ->
            when {
                minusRect(row).contains(x, y) && entry.stockCount > 0 && quantity(entry) > 1 -> {
                    setQuantity(entry, quantity(entry) - 1)
                    click()
                    return true
                }
                plusRect(row).contains(x, y) && entry.stockCount > 0 && quantity(entry) < entry.stockCount -> {
                    setQuantity(entry, quantity(entry) + 1)
                    click()
                    return true
                }
                buyRect(row).contains(x, y) && entry.stockCount > 0 -> {
                    click()
                    PacketDistributor.sendToServer(VendorBuyPayload(payload.sellerId, entry.dimension, entry.pos, quantity(entry)))
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val max = (payload.entries.size - VISIBLE_ROWS).coerceAtLeast(0)
        scroll = (scroll - scrollY.toInt()).coerceIn(0, max)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) {
            minecraft?.setScreen(null)
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun visibleEntries(): List<VendorEntry> = payload.entries.drop(scroll).take(VISIBLE_ROWS)

    private fun quantity(entry: VendorEntry): Int {
        if (entry.stockCount <= 0) return 0
        return quantities.getOrPut(key(entry)) { 1 }.coerceIn(1, entry.stockCount)
    }

    private fun setQuantity(entry: VendorEntry, value: Int) {
        if (entry.stockCount <= 0) {
            quantities[key(entry)] = 0
            return
        }
        quantities[key(entry)] = value.coerceIn(1, entry.stockCount.coerceAtLeast(1))
    }

    private fun key(entry: VendorEntry): String = "${entry.dimension}:${entry.pos.asLong()}"

    private fun renderButton(guiGraphics: GuiGraphics, rect: Rect, label: String, color: Int, mouseX: Int, mouseY: Int, active: Boolean) {
        val hovered = active && rect.contains(mouseX, mouseY)
        val fill = when {
            !active -> DISABLED_FILL
            hovered -> brighten(color)
            else -> color
        }
        guiGraphics.fill(rect.x, rect.y, rect.right, rect.bottom, fill)
        guiGraphics.renderOutline(rect.x, rect.y, rect.width, rect.height, OUTLINE)
        drawCentered(guiGraphics, label, rect.x, rect.y + (rect.height - font.lineHeight) / 2 + 1, rect.width, if (active) WHITE else DISABLED)
    }

    private fun click() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.45f))
    }

    private fun drawCentered(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int) {
        guiGraphics.drawString(font, Component.literal(text), x + (width - font.width(text)) / 2, y, color, false)
    }

    private fun panelRect(): Rect = Rect((width - PANEL_WIDTH) / 2, (height - PANEL_HEIGHT) / 2, PANEL_WIDTH, PANEL_HEIGHT)
    private fun rowRect(row: Int): Rect = panelRect().let { Rect(it.x + 12, it.y + 32 + row * (ROW_HEIGHT + ROW_GAP), it.width - 24, ROW_HEIGHT) }
    private fun minusRect(row: Int): Rect = rowRect(row).let { Rect(it.right - 126, it.y + 18, 20, 18) }
    private fun qtyRect(row: Int): Rect = rowRect(row).let { Rect(it.right - 104, it.y + 18, 34, 18) }
    private fun plusRect(row: Int): Rect = rowRect(row).let { Rect(it.right - 68, it.y + 18, 20, 18) }
    private fun buyRect(row: Int): Rect = rowRect(row).let { Rect(it.right - 44, it.y + 18, 36, 18) }
    private fun voidRect(): Rect = panelRect().let { Rect(it.right - 122, it.bottom - 27, 110, 18) }

    private fun format(amount: Int): String = String.format(Locale.US, "%,d", amount)
    private fun format(amount: Long): String = String.format(Locale.US, "%,d", amount)
    private fun brighten(color: Int): Int = (color and 0xFF000000.toInt()) or (((color shr 16 and 0xFF) + 24).coerceAtMost(255) shl 16) or (((color shr 8 and 0xFF) + 24).coerceAtMost(255) shl 8) or ((color and 0xFF) + 24).coerceAtMost(255)

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
    }

    companion object {
        private const val PANEL_WIDTH = 360
        private const val PANEL_HEIGHT = 246
        private const val VISIBLE_ROWS = 4
        private const val ROW_HEIGHT = 45
        private const val ROW_GAP = 5
        private const val PANEL = 0xEE151515.toInt()
        private const val ROW = 0xCC252525.toInt()
        private const val ROW_OUTLINE = 0x886B5A30.toInt()
        private const val OUTLINE = 0xFFE3C45D.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val GOLD = 0xFFFFD76A.toInt()
        private const val DISABLED = 0xFF9D9485.toInt()
        private const val GREEN = 0xFF287A45.toInt()
        private const val RED = 0xFF8A3030.toInt()
        private const val GRAY = 0xFF454545.toInt()
        private const val DISABLED_FILL = 0xFF333333.toInt()
    }
}
