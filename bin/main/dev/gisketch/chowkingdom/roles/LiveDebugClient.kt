package dev.gisketch.chowkingdom.roles

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.common.NeoForge
import kotlin.math.max

object LiveDebugClient {
    private var state: LiveDebugPayload? = null
    private var expiresAtMillis: Long = 0L
    private var registered = false

    @JvmStatic
    fun register() {
        if (registered) return
        registered = true
        NeoForge.EVENT_BUS.addListener(::onRenderGuiPost)
    }

    @JvmStatic
    fun handle(payload: LiveDebugPayload) {
        if (!payload.visible) {
            state = null
            expiresAtMillis = 0L
            return
        }
        state = payload
        expiresAtMillis = System.currentTimeMillis() + PAYLOAD_GRACE_MILLIS
    }

    private fun onRenderGuiPost(event: RenderGuiEvent.Post) {
        val payload = state ?: return
        if (System.currentTimeMillis() > expiresAtMillis) {
            state = null
            return
        }
        render(event.guiGraphics, payload)
    }

    private fun render(guiGraphics: GuiGraphics, payload: LiveDebugPayload) {
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val window = minecraft.window
        val title = Component.literal("LIVE DEBUG: ${payload.title}").withStyle(ChatFormatting.GOLD)
        val lines = payload.lines.take(MAX_RENDER_LINES)
        val textWidth = max(font.width(title), lines.maxOfOrNull { line -> lineWidth(font, line) } ?: 0)
        val panelWidth = (textWidth + PANEL_PAD * 2).coerceAtLeast(MIN_PANEL_WIDTH)
        val panelHeight = PANEL_PAD * 2 + font.lineHeight * (lines.size + 1) + LINE_GAP * lines.size
        val x = (window.guiScaledWidth - panelWidth) / 2
        val y = (window.guiScaledHeight - panelHeight) / 2
        guiGraphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BACKGROUND)
        guiGraphics.fill(x, y, x + panelWidth, y + 1, PANEL_BORDER)
        guiGraphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, PANEL_BORDER)
        guiGraphics.fill(x, y, x + 1, y + panelHeight, PANEL_BORDER)
        guiGraphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, PANEL_BORDER)
        var cursorY = y + PANEL_PAD
        guiGraphics.drawString(font, title, x + (panelWidth - font.width(title)) / 2, cursorY, TITLE_COLOR, false)
        cursorY += font.lineHeight + LINE_GAP
        lines.forEach { line ->
            drawLine(guiGraphics, font, line, x + PANEL_PAD, cursorY)
            cursorY += font.lineHeight + LINE_GAP
        }
    }

    private fun drawLine(guiGraphics: GuiGraphics, font: Font, line: String, x: Int, y: Int) {
        val split = line.indexOf(':').takeIf { index -> index > 0 } ?: run {
            guiGraphics.drawString(font, line, x, y, VALUE_COLOR, false)
            return
        }
        val label = Component.literal(line.take(split + 1)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
        val value = line.drop(split + 1).trimStart()
        guiGraphics.drawString(font, label, x, y, FIELD_COLOR, false)
        guiGraphics.drawString(font, value, x + font.width(label) + LABEL_VALUE_GAP, y, VALUE_COLOR, false)
    }

    private fun lineWidth(font: Font, line: String): Int {
        val split = line.indexOf(':').takeIf { index -> index > 0 } ?: return font.width(line)
        val label = Component.literal(line.take(split + 1)).withStyle(ChatFormatting.BOLD)
        val value = line.drop(split + 1).trimStart()
        return font.width(label) + LABEL_VALUE_GAP + font.width(value)
    }

    private const val PAYLOAD_GRACE_MILLIS = 1500L
    private const val MAX_RENDER_LINES = 20
    private const val PANEL_PAD = 8
    private const val LINE_GAP = 2
    private const val LABEL_VALUE_GAP = 4
    private const val MIN_PANEL_WIDTH = 220
    private const val PANEL_BACKGROUND = 0xB0000000.toInt()
    private const val PANEL_BORDER = 0xAAE7C66B.toInt()
    private const val TITLE_COLOR = 0xFFE7C66B.toInt()
    private const val FIELD_COLOR = 0xFFFFFFFF.toInt()
    private const val VALUE_COLOR = 0xFFEFEFEF.toInt()
}
