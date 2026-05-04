package dev.gisketch.chowkingdom.client

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.Locale

class ChowDeathScreen(private val causeOfDeath: Component?, private val hardcore: Boolean) : Screen(Component.translatable(if (hardcore) "deathScreen.title.hardcore" else "deathScreen.title")) {
    private var delayTicker = 0
    private var previousRespawnHover = false
    private var previousLeaveHover = false

    override fun shouldCloseOnEsc(): Boolean = false

    override fun isPauseScreen(): Boolean = false

    override fun tick() {
        super.tick()
        delayTicker++
        minecraft?.player?.setDeltaMovement(0.0, 0.0, 0.0)
        minecraft?.player?.isSprinting = false
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBlurredBackground(partialTick)
        guiGraphics.fill(0, 0, width, height, DEATH_OVERLAY)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderDeathText(guiGraphics)
        renderButtons(guiGraphics, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !buttonsActive()) return super.mouseClicked(mouseX, mouseY, button)
        val pointX = mouseX.toInt()
        val pointY = mouseY.toInt()
        return when {
            respawnButtonRect().contains(pointX, pointY) -> {
                minecraft?.player?.respawn()
                true
            }
            leaveButtonRect().contains(pointX, pointY) -> {
                leaveGame()
                true
            }
            else -> super.mouseClicked(mouseX, mouseY, button)
        }
    }

    private fun renderDeathText(guiGraphics: GuiGraphics) {
        val titleText = fitCkdmText(title.string.ifBlank { "YOU DIED" }, width - TEXT_MARGIN * 2, CKDM_BOLD_LARGE_FONT)
        val titleComponent = ckdmText(titleText, CKDM_BOLD_LARGE_FONT)
        val titleWidth = font.width(titleComponent)
        val titleY = textBlockTop()
        drawCkdmShadowed(guiGraphics, font, titleComponent, (width - titleWidth) / 2, titleY, CKDM_WHITE, CKDM_DARK_SHADOW, CKDM_SHADOW_OFFSET)

        val causeText = causeOfDeath?.string?.takeIf { it.isNotBlank() } ?: return
        val causeComponent = ckdmText(fitCkdmText(causeText, width - TEXT_MARGIN * 2, CKDM_BOLD_FONT), CKDM_BOLD_FONT)
        val causeWidth = font.width(causeComponent)
        drawCkdmShadowed(guiGraphics, font, causeComponent, (width - causeWidth) / 2, titleY + CAUSE_TOP_GAP, CKDM_GOLD, CKDM_GOLD_SHADOW, CKDM_SHADOW_OFFSET)
    }

    private fun renderButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val respawnRect = respawnButtonRect()
        val leaveRect = leaveButtonRect()
        val active = buttonsActive()
        val respawnHovered = active && respawnRect.contains(mouseX, mouseY)
        val leaveHovered = active && leaveRect.contains(mouseX, mouseY)
        if (respawnHovered && !previousRespawnHover || leaveHovered && !previousLeaveHover) playHoverSound()
        previousRespawnHover = respawnHovered
        previousLeaveHover = leaveHovered
        renderButton(guiGraphics, respawnRect, "RESPAWN", GREEN_BUTTON_TEXTURE, GREEN_BUTTON_HOVER_TEXTURE, respawnHovered, active)
        renderButton(guiGraphics, leaveRect, "LEAVE GAME", RED_BUTTON_TEXTURE, RED_BUTTON_HOVER_TEXTURE, leaveHovered, active)
    }

    private fun renderButton(guiGraphics: GuiGraphics, rect: Rect, labelText: String, texture: ResourceLocation, hoverTexture: ResourceLocation, hovered: Boolean, active: Boolean) {
        val alpha = if (active) 1.0f else 0.45f
        RenderSystem.enableBlend()
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        renderNineSlice(guiGraphics, if (hovered) hoverTexture else texture, if (hovered) rect.inflate(BUTTON_HOVER_BORDER_PADDING) else rect, if (hovered) BUTTON_HOVER_TEXTURE_SIZE else BUTTON_TEXTURE_SIZE, if (hovered) BUTTON_HOVER_CORNER_SIZE else BUTTON_CORNER_SIZE, if (hovered) BUTTON_HOVER_DESTINATION_CORNER_SIZE else BUTTON_DESTINATION_CORNER_SIZE)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
        val label = ckdmText(labelText, CKDM_BOLD_FONT)
        val labelX = rect.x + (rect.width - font.width(label)) / 2
        val labelY = rect.y + (rect.height - font.lineHeight) / 2 + 1
        drawCkdmShadowed(guiGraphics, font, label, labelX, labelY, colorWithAlpha(CKDM_WHITE, alpha), colorWithAlpha(BUTTON_TEXT_SHADOW, alpha), 1)
    }

    private fun leaveGame() {
        val minecraft = minecraft ?: return
        minecraft.level?.disconnect()
        minecraft.disconnect(GenericMessageScreen(Component.translatable("menu.savingLevel")))
        minecraft.setScreen(TitleScreen())
    }

    private fun playHoverSound() {
        Minecraft.getInstance().soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), HOVER_SOUND_PITCH, HOVER_SOUND_VOLUME))
    }

    private fun buttonsActive(): Boolean = delayTicker >= BUTTON_ENABLE_TICKS

    private fun textBlockTop(): Int = (height / 2 - 74).coerceAtLeast(32)

    private fun buttonTop(): Int = textBlockTop() + BUTTONS_TOP_GAP

    private fun respawnButtonRect(): Rect {
        val totalWidth = BUTTON_WIDTH * 2 + BUTTON_GAP
        val x = (width - totalWidth) / 2
        return Rect(x, buttonTop(), BUTTON_WIDTH, BUTTON_HEIGHT)
    }

    private fun leaveButtonRect(): Rect = Rect(respawnButtonRect().right + BUTTON_GAP, buttonTop(), BUTTON_WIDTH, BUTTON_HEIGHT)

    private fun ckdmText(text: String, fontId: ResourceLocation): Component =
        Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    private fun fitCkdmText(text: String, maxWidth: Int, fontId: ResourceLocation): String {
        if (font.width(ckdmText(text, fontId)) <= maxWidth) return text
        val suffix = "..."
        var trimmed = text
        while (trimmed.isNotEmpty() && font.width(ckdmText(trimmed + suffix, fontId)) > maxWidth) trimmed = trimmed.dropLast(1)
        return trimmed + suffix
    }

    private fun drawCkdmShadowed(guiGraphics: GuiGraphics, font: Font, component: Component, x: Int, y: Int, color: Int, shadowColor: Int, shadowOffset: Int) {
        guiGraphics.drawString(font, component, x + shadowOffset, y + shadowOffset, shadowColor, false)
        guiGraphics.drawString(font, component, x, y, color, false)
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureSize: Int, sourceCorner: Int, destinationCorner: Int = sourceCorner) {
        val edge = textureSize - sourceCorner
        val middle = textureSize - sourceCorner * 2
        val innerWidth = (rect.width - destinationCorner * 2).coerceAtLeast(0)
        val innerHeight = (rect.height - destinationCorner * 2).coerceAtLeast(0)
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y, destinationCorner, destinationCorner), 0, 0, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y, innerWidth, destinationCorner), sourceCorner, 0, middle, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y, destinationCorner, destinationCorner), edge, 0, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y + destinationCorner, destinationCorner, innerHeight), 0, sourceCorner, sourceCorner, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + destinationCorner, innerWidth, innerHeight), sourceCorner, sourceCorner, middle, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y + destinationCorner, destinationCorner, innerHeight), edge, sourceCorner, sourceCorner, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y + rect.height - destinationCorner, destinationCorner, destinationCorner), 0, edge, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + rect.height - destinationCorner, innerWidth, destinationCorner), sourceCorner, edge, middle, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y + rect.height - destinationCorner, destinationCorner, destinationCorner), edge, edge, sourceCorner, sourceCorner, textureSize)
    }

    private fun blitRegion(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int, textureSize: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, sourceX.toFloat(), sourceY.toFloat(), sourceWidth, sourceHeight, textureSize, textureSize)
    }

    private fun colorWithAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * alphaFactor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < x + width && pointY >= y && pointY < y + height
        fun inflate(amount: Int): Rect = Rect(x - amount, y - amount, width + amount * 2, height + amount * 2)
    }

    private val CKDM_BOLD_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    private val CKDM_BOLD_LARGE_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_large")
    private val GREEN_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green.png")
    private val GREEN_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green_hover.png")
    private val RED_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_red.png")
    private val RED_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_red_hover.png")

    private companion object {
        const val CKDM_WHITE = 0xFFFFFFFF.toInt()
        const val CKDM_GOLD = 0xFFFFD24A.toInt()
        const val CKDM_DARK_SHADOW = 0xCC050505.toInt()
        const val CKDM_GOLD_SHADOW = 0xCC7A2E00.toInt()
        const val CKDM_SHADOW_OFFSET = 2
        const val DEATH_OVERLAY = 0xF2000000.toInt()
        const val BUTTON_TEXT_SHADOW = 0xAA101010.toInt()
        const val TEXT_MARGIN = 18
        const val CAUSE_TOP_GAP = 34
        const val BUTTONS_TOP_GAP = 96
        const val BUTTON_WIDTH = 118
        const val BUTTON_HEIGHT = 24
        const val BUTTON_GAP = 12
        const val BUTTON_ENABLE_TICKS = 20
        const val BUTTON_TEXTURE_SIZE = 8
        const val BUTTON_CORNER_SIZE = 2
        const val BUTTON_DESTINATION_CORNER_SIZE = 4
        const val BUTTON_HOVER_TEXTURE_SIZE = 10
        const val BUTTON_HOVER_CORNER_SIZE = 3
        const val BUTTON_HOVER_DESTINATION_CORNER_SIZE = 5
        const val BUTTON_HOVER_BORDER_PADDING = 1
        const val HOVER_SOUND_PITCH = 1.55f
        const val HOVER_SOUND_VOLUME = 0.24f
    }
}