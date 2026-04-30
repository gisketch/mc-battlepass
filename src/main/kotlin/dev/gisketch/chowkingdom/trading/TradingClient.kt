package dev.gisketch.chowkingdom.trading

import dev.gisketch.chowkingdom.wallets.ChowcoinClientState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Inventory
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.UUID
import java.util.Locale

object TradingClient {
    private val glowTargets: MutableMap<UUID, Boolean> = linkedMapOf()
    private val previousGlow: MutableMap<UUID, Boolean> = linkedMapOf()

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerScreens)
        NeoForge.EVENT_BUS.addListener(::onClientTick)
    }

    fun setGlow(entityId: UUID, enabled: Boolean) {
        val level = Minecraft.getInstance().level
        val entity = level?.getEntitiesOfClass(Entity::class.java, net.minecraft.world.phys.AABB.ofSize(net.minecraft.world.phys.Vec3.ZERO, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE))
            ?.firstOrNull { it.uuid == entityId }
        if (enabled) {
            glowTargets[entityId] = true
            if (entity != null) {
                previousGlow.putIfAbsent(entityId, entity.isCurrentlyGlowing)
                entity.setGlowingTag(true)
            }
        } else {
            glowTargets.remove(entityId)
            restoreGlow(entityId, entity)
        }
    }

    private fun registerScreens(event: RegisterMenuScreensEvent) {
        event.register(TradingFeature.TRADE_MENU.get(), ::TradingScreen)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        val level = Minecraft.getInstance().level ?: return
        glowTargets.keys.toList().forEach { id ->
            val entity = level.getEntitiesOfClass(Entity::class.java, Minecraft.getInstance().player?.boundingBox?.inflate(128.0) ?: return@forEach)
                .firstOrNull { it.uuid == id }
            if (entity != null) {
                previousGlow.putIfAbsent(id, entity.isCurrentlyGlowing)
                entity.setGlowingTag(true)
            }
        }
    }

    private fun restoreGlow(entityId: UUID, entity: Entity?) {
        val previous = previousGlow.remove(entityId) ?: false
        entity?.setGlowingTag(previous)
    }
}

class TradingScreen(menu: TradingMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<TradingMenu>(menu, inventory, title) {
    private var readyButton: Button? = null

    init {
        imageWidth = TradingMenu.IMAGE_WIDTH
        imageHeight = TradingMenu.IMAGE_HEIGHT
        titleLabelX = 8
        titleLabelY = 6
        inventoryLabelY = 72
    }

    override fun init() {
        super.init()
        val buttonY = topPos + 174
        addRenderableWidget(Button.builder(Component.literal("+1k")) { adjustCoins(1_000) }.bounds(leftPos + 8, buttonY, 38, 20).build())
        addRenderableWidget(Button.builder(Component.literal("+10k")) { adjustCoins(10_000) }.bounds(leftPos + 50, buttonY, 42, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Max")) { setCoins(ChowcoinClientState.balance()) }.bounds(leftPos + 96, buttonY, 38, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Clear")) { setCoins(0) }.bounds(leftPos + 138, buttonY, 42, 20).build())
        readyButton = addRenderableWidget(Button.builder(Component.literal("Ready")) { readyOrConfirm() }.bounds(leftPos + 194, buttonY, 92, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Cancel")) { TradingNetwork.sendAction(menu.sessionId, TradeAction.CANCEL) }.bounds(leftPos + 292, buttonY, 64, 20).build())
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        updateReadyButton()
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        dimOtherInventory(guiGraphics)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        renderChest(guiGraphics, leftPos + TradingMenu.LEFT_X, topPos)
        renderChest(guiGraphics, leftPos + TradingMenu.RIGHT_X, topPos)
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val state = TradingClientState.get(menu.sessionId)
        guiGraphics.drawString(font, menu.selfName, 8, 6, 0x404040, false)
        guiGraphics.drawString(font, menu.otherName, TradingMenu.RIGHT_X + 8, 6, 0x404040, false)
        guiGraphics.drawString(font, "Offer: ${format(state?.selfChowcoins ?: 0L)} chowcoins", 8, 62, 0xE0A12A, false)
        guiGraphics.drawString(font, "Offer: ${format(state?.otherChowcoins ?: 0L)} chowcoins", TradingMenu.RIGHT_X + 8, 62, 0xE0A12A, false)
        guiGraphics.drawString(font, statusText(state, self = true), 8, 158, 0x404040, false)
        guiGraphics.drawString(font, statusText(state, self = false), TradingMenu.RIGHT_X + 8, 158, 0x404040, false)
    }

    override fun removed() {
        TradingClientState.clear(menu.sessionId)
        super.removed()
    }

    private fun renderChest(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val rows = 3
        guiGraphics.blit(CONTAINER_BACKGROUND, x, y, 0, 0, TradingMenu.PANEL_WIDTH, rows * 18 + 17)
        guiGraphics.blit(CONTAINER_BACKGROUND, x, y + rows * 18 + 17, 0, 126, TradingMenu.PANEL_WIDTH, 96)
    }

    private fun dimOtherInventory(guiGraphics: GuiGraphics) {
        val x = leftPos + TradingMenu.RIGHT_X + 7
        val y = topPos + TradingMenu.INVENTORY_Y - 1
        guiGraphics.fill(RenderType.guiOverlay(), x, y, x + 162, y + 76, 0x88000000.toInt())
    }

    private fun updateReadyButton() {
        val state = TradingClientState.get(menu.sessionId)
        val label = when {
            state?.selfConfirmed == true -> "Confirmed"
            state?.selfReady == true && state.otherReady -> "Confirm"
            state?.selfReady == true -> "Waiting"
            else -> "Ready"
        }
        readyButton?.message = Component.literal(label)
        readyButton?.active = state?.selfConfirmed != true
    }

    private fun readyOrConfirm() {
        val state = TradingClientState.get(menu.sessionId)
        if (state?.selfReady == true && state.otherReady) TradingNetwork.sendAction(menu.sessionId, TradeAction.CONFIRM)
        else TradingNetwork.sendAction(menu.sessionId, TradeAction.READY)
    }

    private fun adjustCoins(delta: Long) {
        val current = TradingClientState.get(menu.sessionId)?.selfChowcoins ?: 0L
        setCoins((current + delta).coerceAtMost(ChowcoinClientState.balance()))
    }

    private fun setCoins(amount: Long) {
        TradingNetwork.sendAction(menu.sessionId, TradeAction.SET_CHOWCOINS, amount.coerceAtLeast(0L))
    }

    private fun statusText(state: TradeStatePayload?, self: Boolean): String {
        val ready = if (self) state?.selfReady else state?.otherReady
        val confirmed = if (self) state?.selfConfirmed else state?.otherConfirmed
        return when {
            confirmed == true -> "Confirmed"
            ready == true -> "Ready"
            else -> "Not ready"
        }
    }

    private fun format(amount: Long): String = String.format(Locale.US, "%,d", amount)

    companion object {
        private val CONTAINER_BACKGROUND: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png")
    }
}
