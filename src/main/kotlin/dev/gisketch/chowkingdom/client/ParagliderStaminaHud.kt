package dev.gisketch.chowkingdom.client

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.compat.ParagliderStaminaBridge
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.FluidTags
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import kotlin.math.roundToInt

object ParagliderStaminaHud {
    private val LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "paraglider_stamina")
    private val EMPTY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_stamina_empty.png")
    private val FILL_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_stamina_fill.png")

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerGuiLayers)
    }

    @JvmStatic
    fun shouldReserveRightHudSpace(): Boolean {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        if (isHudHidden(minecraft)) return false
        return ParagliderStaminaBridge.snapshot(player) != null
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun shouldOffsetAirBubbles(): Boolean {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        return shouldReserveRightHudSpace() && (player.isEyeInFluid(FluidTags.WATER) || player.airSupply < player.maxAirSupply)
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAboveAll(LAYER_ID) { guiGraphics, _ -> render(guiGraphics) }
    }

    private fun render(guiGraphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (isHudHidden(minecraft)) return
        val snapshot = ParagliderStaminaBridge.snapshot(player) ?: return
        val screenWidth = minecraft.window.guiScaledWidth
        val screenHeight = minecraft.window.guiScaledHeight
        val x = screenWidth / 2 + RIGHT_HUD_LEFT_OFFSET
        val y = screenHeight - BAR_BOTTOM_OFFSET
        renderBar(guiGraphics, x, y, snapshot)
    }

    private fun isHudHidden(minecraft: Minecraft): Boolean =
        minecraft.options.hideGui || minecraft.gui.debugOverlay.showDebugScreen() || (minecraft.screen != null && minecraft.screen !is ChatScreen)

    private fun renderBar(guiGraphics: GuiGraphics, x: Int, y: Int, snapshot: ParagliderStaminaBridge.Snapshot) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        drawNineSlice(guiGraphics, EMPTY_TEXTURE, x, y, BAR_WIDTH, BAR_HEIGHT, EMPTY_CORNER, EMPTY_TEXTURE_SIZE, EMPTY_TEXTURE_SIZE)

        val innerX = x + FILL_INSET
        val innerY = y + FILL_INSET
        val innerWidth = BAR_WIDTH - FILL_INSET * 2
        val innerHeight = BAR_HEIGHT - FILL_INSET * 2
        val fillWidth = ((snapshot.stamina / snapshot.maxStamina).coerceIn(0.0, 1.0) * innerWidth).roundToInt().coerceIn(0, innerWidth)
        if (fillWidth <= 0) return

        guiGraphics.enableScissor(innerX, innerY, innerX + fillWidth, innerY + innerHeight)
        drawNineSlice(guiGraphics, FILL_TEXTURE, innerX, innerY, innerWidth, innerHeight, FILL_CORNER, FILL_TEXTURE_SIZE, FILL_TEXTURE_SIZE)
        guiGraphics.disableScissor()
    }

    private fun drawNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, corner: Int, textureWidth: Int, textureHeight: Int) {
        val centerWidth = textureWidth - corner * 2
        val centerHeight = textureHeight - corner * 2
        val targetCenterWidth = width - corner * 2
        val targetCenterHeight = height - corner * 2

        blit(guiGraphics, texture, x, y, corner, corner, 0, 0, corner, corner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + corner, y, targetCenterWidth, corner, corner, 0, centerWidth, corner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + width - corner, y, corner, corner, textureWidth - corner, 0, corner, corner, textureWidth, textureHeight)

        blit(guiGraphics, texture, x, y + corner, corner, targetCenterHeight, 0, corner, corner, centerHeight, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + corner, y + corner, targetCenterWidth, targetCenterHeight, corner, corner, centerWidth, centerHeight, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + width - corner, y + corner, corner, targetCenterHeight, textureWidth - corner, corner, corner, centerHeight, textureWidth, textureHeight)

        blit(guiGraphics, texture, x, y + height - corner, corner, corner, 0, textureHeight - corner, corner, corner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + corner, y + height - corner, targetCenterWidth, corner, corner, textureHeight - corner, centerWidth, corner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + width - corner, y + height - corner, corner, corner, textureWidth - corner, textureHeight - corner, corner, corner, textureWidth, textureHeight)
    }

    private fun blit(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, u: Int, v: Int, sourceWidth: Int, sourceHeight: Int, textureWidth: Int, textureHeight: Int) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return
        guiGraphics.blit(texture, x, y, width, height, u.toFloat(), v.toFloat(), sourceWidth, sourceHeight, textureWidth, textureHeight)
    }

    private const val BAR_WIDTH = 81
    private const val BAR_HEIGHT = 10
    private const val BAR_BOTTOM_OFFSET = 50
    private const val RIGHT_HUD_LEFT_OFFSET = 10
    private const val FILL_INSET = 1
    private const val EMPTY_TEXTURE_SIZE = 18
    private const val FILL_TEXTURE_SIZE = 16
    private const val EMPTY_CORNER = 5
    private const val FILL_CORNER = 4
}