package dev.gisketch.chowkingdom.trading

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.wallets.ChowcoinClientState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Inventory
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.Locale
import java.util.UUID

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
    private var coinInput: EditBox? = null
    private var lastCoinInputValue: String? = null

    init {
        imageWidth = TradingMenu.IMAGE_WIDTH
        imageHeight = TradingMenu.IMAGE_HEIGHT
        titleLabelX = TradingMenu.LEFT_X + 8
        titleLabelY = 6
        inventoryLabelY = 72
    }

    override fun init() {
        super.init()
        val centerX = leftPos + imageWidth / 2
        val readyY = topPos + 174
        val coinY = readyY + 26
        readyButton = addRenderableWidget(Button.builder(Component.literal("Ready")) { readyOrConfirm() }.bounds(centerX - 54, readyY, 108, 20).build())
        coinInput = addRenderableWidget(EditBox(font, centerX - 38, coinY, 92, 20, Component.literal("Chowcoins")).also { input ->
            input.setFilter { value -> value.isEmpty() || value.all(Char::isDigit) }
            input.setMaxLength(15)
            input.setResponder(::onCoinInputChanged)
        })
        addRenderableWidget(Button.builder(Component.literal("Cancel")) { TradingNetwork.sendAction(menu.sessionId, TradeAction.CANCEL) }.bounds(centerX - 42, coinY + 26, 84, 20).build())
        syncCoinInputFromState()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        updateReadyButton()
        syncCoinInputFromState()
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        dimOtherInventory(guiGraphics)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        renderChest(guiGraphics, leftPos + TradingMenu.LEFT_X, topPos)
        renderChest(guiGraphics, leftPos + TradingMenu.RIGHT_X, topPos)
        renderPaperDoll(guiGraphics, leftPos, topPos - 10, minecraft?.player, mouseX, mouseY)
        renderPaperDoll(guiGraphics, leftPos + TradingMenu.RIGHT_PAPER_DOLL_X, topPos - 10, otherPlayer(), mouseX, mouseY)
        renderProjectedBalances(guiGraphics)
        renderCoinInputIcon(guiGraphics)
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val state = TradingClientState.get(menu.sessionId)
        renderReadyState(guiGraphics, TradingMenu.LEFT_X + 8, 6, state, self = true)
        renderReadyState(guiGraphics, TradingMenu.RIGHT_X + TradingMenu.PANEL_WIDTH - 86, 6, state, self = false)
        renderCoinRow(guiGraphics, TradingMenu.LEFT_X + 8, -19, state?.selfChowcoins ?: 0L)
        renderCoinRow(guiGraphics, TradingMenu.RIGHT_X + 8, -19, state?.otherChowcoins ?: 0L)
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
        coinInput?.active = true
    }

    private fun readyOrConfirm() {
        val state = TradingClientState.get(menu.sessionId)
        if (state?.selfReady == true && state.otherReady) TradingNetwork.sendAction(menu.sessionId, TradeAction.CONFIRM)
        else TradingNetwork.sendAction(menu.sessionId, TradeAction.READY)
    }

    private fun setCoins(amount: Long) {
        TradingNetwork.sendAction(menu.sessionId, TradeAction.SET_CHOWCOINS, amount.coerceAtLeast(0L))
    }

    private fun renderReadyState(guiGraphics: GuiGraphics, x: Int, y: Int, state: TradeStatePayload?, self: Boolean) {
        val text = statusText(state, self)
        val icon = if (isReadyOrConfirmed(state, self)) READY_TEXTURE else NOT_READY_TEXTURE
        renderIcon(guiGraphics, icon, x, y - 3, STATUS_ICON_SIZE, STATUS_ICON_TEXTURE_SIZE)
        guiGraphics.drawString(font, text, x + STATUS_ICON_SIZE + 4, y, 0x404040, false)
    }

    private fun statusText(state: TradeStatePayload?, self: Boolean): String {
        val ready = if (self) state?.selfReady else state?.otherReady
        val confirmed = if (self) state?.selfConfirmed else state?.otherConfirmed
        return when {
            confirmed == true -> "Confirmed"
            ready == true -> "Ready"
            else -> "Not Ready"
        }
    }

    private fun isReadyOrConfirmed(state: TradeStatePayload?, self: Boolean): Boolean =
        if (self) state?.selfReady == true || state?.selfConfirmed == true else state?.otherReady == true || state?.otherConfirmed == true

    private fun renderCoinRow(guiGraphics: GuiGraphics, x: Int, y: Int, amount: Long) {
        renderIcon(guiGraphics, COINS_TEXTURE, x, y, COIN_ICON_SIZE, COIN_ICON_TEXTURE_SIZE)
        guiGraphics.drawString(font, format(amount), x + COIN_ICON_SIZE + 6, y + 4, 0xE0A12A, false)
    }

    private fun renderPaperDoll(guiGraphics: GuiGraphics, x: Int, y: Int, entity: LivingEntity?, mouseX: Int, mouseY: Int) {
        val tagWidth = TradingMenu.PAPER_DOLL_WIDTH
        if (entity != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                x + 4,
                y,
                x + tagWidth - 4,
                y + 174,
                46,
                0.0f,
                mouseX.toFloat(),
                mouseY.toFloat(),
                entity,
            )
        } else {
            guiGraphics.drawCenteredString(font, "No preview", x + tagWidth / 2, y + 76, 0x777777)
        }
    }

    private fun renderProjectedBalances(guiGraphics: GuiGraphics) {
        val state = TradingClientState.get(menu.sessionId)
        val selfBalance = state?.selfBalance ?: ChowcoinClientState.balance()
        val otherBalance = state?.otherBalance ?: 0L
        renderProjectedBalance(guiGraphics, 0, 162, selfBalance, state?.selfChowcoins ?: 0L, state?.otherChowcoins ?: 0L)
        renderProjectedBalance(guiGraphics, TradingMenu.RIGHT_PAPER_DOLL_X, 162, otherBalance, state?.otherChowcoins ?: 0L, state?.selfChowcoins ?: 0L)
    }

    private fun renderProjectedBalance(guiGraphics: GuiGraphics, x: Int, y: Int, balance: Long, outgoing: Long, incoming: Long) {
        val projected = balance - outgoing + incoming
        val delta = incoming - outgoing
        val iconX = leftPos + x + 10
        val iconY = topPos + y
        renderIcon(guiGraphics, COINS_TEXTURE, iconX, iconY, COIN_ICON_SIZE, COIN_ICON_TEXTURE_SIZE)
        val amountX = iconX + COIN_ICON_SIZE + 5
        guiGraphics.drawString(font, format(projected), amountX, iconY + 4, 0xE0A12A, false)
        if (delta != 0L) {
            val deltaText = if (delta > 0) "(+${format(delta)})" else "(-${format(-delta)})"
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate((amountX + font.width(format(projected)) + 5).toFloat(), (iconY + 6).toFloat(), 0.0f)
            pose.scale(0.75f, 0.75f, 1.0f)
            guiGraphics.drawString(font, deltaText, 0, 0, 0xFF8A8A8A.toInt(), false)
            pose.popPose()
        }
    }

    private fun renderCoinInputIcon(guiGraphics: GuiGraphics) {
        val input = coinInput ?: return
        renderIcon(guiGraphics, COINS_TEXTURE, input.x - COIN_ICON_SIZE - 6, input.y + 2, COIN_ICON_SIZE, COIN_ICON_TEXTURE_SIZE)
    }

    private fun syncCoinInputFromState() {
        val input = coinInput ?: return
        if (input.isFocused) return
        val value = (TradingClientState.get(menu.sessionId)?.selfChowcoins ?: 0L).toString()
        if (input.value == value) return
        lastCoinInputValue = value
        input.setValue(value)
    }

    private fun onCoinInputChanged(value: String) {
        if (value == lastCoinInputValue) return
        lastCoinInputValue = value
        val amount = value.toLongOrNull()?.coerceAtMost(ChowcoinClientState.balance()) ?: 0L
        setCoins(amount)
    }

    private fun otherPlayer(): LivingEntity? {
        val state = TradingClientState.get(menu.sessionId) ?: return null
        if (state.debug) return null
        return minecraft?.level?.players()?.firstOrNull { player -> player.uuid == state.otherId }
    }

    private fun renderIcon(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int, textureSize: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, textureSize, textureSize, textureSize, textureSize)
    }

    private fun format(amount: Long): String = String.format(Locale.US, "%,d", amount)

    companion object {
        private val CONTAINER_BACKGROUND: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png")
        private val COINS_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/coins.png")
        private val READY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/friend_add.png")
        private val NOT_READY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/friend_remove.png")
        private const val STATUS_ICON_SIZE = 14
        private const val STATUS_ICON_TEXTURE_SIZE = 16
        private const val COIN_ICON_SIZE = 16
        private const val COIN_ICON_TEXTURE_SIZE = 16
    }
}
