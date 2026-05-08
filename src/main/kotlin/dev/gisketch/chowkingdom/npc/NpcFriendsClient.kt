package dev.gisketch.chowkingdom.npc

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.Locale
import kotlin.math.max

object NpcFriendsClient {
    private var friends: List<NpcFriendEntryPayload> = emptyList()

    fun open() {
        Minecraft.getInstance().setScreen(NpcFriendsScreen())
        NpcNetwork.requestFriends()
    }

    @JvmStatic
    fun apply(payload: NpcFriendsSyncPayload) {
        friends = payload.friends
    }

    fun snapshot(): List<NpcFriendEntryPayload> = friends
}

private class NpcFriendsScreen : Screen(Component.literal("Friends")) {
    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
        fun contains(pointX: Double, pointY: Double): Boolean = contains(pointX.toInt(), pointY.toInt())
    }

    private data class TooltipZone(val rect: Rect, val lines: List<Component>)

    private var openedAtMs = 0L
    private var renderAlpha = 1.0f
    private var scroll = 0
    private var listArea = Rect(0, 0, 0, 0)

    override fun init() {
        openedAtMs = Util.getMillis()
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        renderAlpha = entranceProgress()
        val panel = panelRect()
        val zones = mutableListOf<TooltipZone>()
        withEntrance(guiGraphics, panel) {
            renderNineSlice(guiGraphics, PANEL_TEXTURE, panel, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER)
            renderContent(guiGraphics, panel, zones)
        }
        zones.firstOrNull { zone -> zone.rect.contains(mouseX, mouseY) }?.let { zone -> guiGraphics.renderComponentTooltip(font, zone.lines, mouseX, mouseY) }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0x99000000.toInt())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!listArea.contains(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val maxScroll = maxScroll()
        scroll = (scroll - (scrollY * SCROLL_STEP).toInt()).coerceIn(0, maxScroll)
        return true
    }

    private fun renderContent(guiGraphics: GuiGraphics, panel: Rect, zones: MutableList<TooltipZone>) {
        drawCenteredCkdm(guiGraphics, "Friends", panel.x, panel.y + 18, panel.width, WHITE, CKDM_BOLD)
        val inner = Rect(panel.x + PAD, panel.y + 45, panel.width - PAD * 2, panel.height - 64)
        listArea = inner
        val friends = NpcFriendsClient.snapshot()
        val rowArea = Rect(inner.x, inner.y, inner.width, inner.height)
        if (friends.isEmpty()) {
            guiGraphics.drawString(font, "Loading...", rowArea.x, rowArea.y + 8, colorWithRenderAlpha(MUTED), false)
            return
        }
        scroll = scroll.coerceIn(0, maxScroll())
        guiGraphics.enableScissor(rowArea.x, rowArea.y, rowArea.right, rowArea.bottom)
        friends.forEachIndexed { index, friend ->
            val row = Rect(rowArea.x, rowArea.y + index * ROW_STEP - scroll, rowArea.width, ROW_HEIGHT)
            if (row.bottom < rowArea.y || row.y > rowArea.bottom) return@forEachIndexed
            renderFriendRow(guiGraphics, row, friend)
            zones += TooltipZone(row, tooltip(friend))
        }
        guiGraphics.disableScissor()
        renderScrollbar(guiGraphics, rowArea, friends.size)
    }

    private fun renderFriendRow(guiGraphics: GuiGraphics, row: Rect, friend: NpcFriendEntryPayload) {
        guiGraphics.fill(row.x, row.y, row.right, row.bottom, colorWithRenderAlpha(ROW_FILL))
        renderNpcHead(guiGraphics, friend.npcId, row.x + 7, row.y + 7, HEAD_SIZE)
        val textX = row.x + HEAD_SIZE + 16
        val name = fitPlain(friend.name, row.width - HEAD_SIZE - 94)
        guiGraphics.drawString(font, name, textX, row.y + 7, colorWithRenderAlpha(WHITE), false)
        val hearts = heartText(friend.friendshipLevel)
        guiGraphics.drawString(font, hearts, textX + font.width(name) + 8, row.y + 7, colorWithRenderAlpha(HEART), false)
        val levelLabel = "Lv.${friend.friendshipLevel}"
        guiGraphics.drawString(font, levelLabel, row.right - font.width(levelLabel) - 8, row.y + 7, colorWithRenderAlpha(GOLD), false)
        val status = fitPlain(friend.missionStatus, row.width - HEAD_SIZE - 28)
        guiGraphics.drawString(font, status, textX, row.y + 23, colorWithRenderAlpha(MUTED), false)
    }

    private fun tooltip(friend: NpcFriendEntryPayload): List<Component> = listOf(
        Component.literal(friend.name),
        Component.literal(friend.title.ifBlank { friend.npcId }),
        Component.literal("Friendship: ${friend.friendshipPoints} points, level ${friend.friendshipLevel}"),
        Component.literal(friend.giftStatus),
        Component.literal(friend.shopStatus),
        Component.literal(if (friend.missionGoal > 0) "${friend.missionStatus} (${friend.missionProgress}/${friend.missionGoal})" else friend.missionStatus),
    )

    private fun renderNpcHead(guiGraphics: GuiGraphics, npcId: String, x: Int, y: Int, size: Int) {
        val texture = npcTexture(npcId)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        guiGraphics.blit(texture, x, y, size, size, 8.0f, 8.0f, 8, 8, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE)
        guiGraphics.blit(texture, x, y, size, size, 40.0f, 8.0f, 8, 8, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, area: Rect, count: Int) {
        val contentHeight = count * ROW_STEP
        if (contentHeight <= area.height) return
        val track = Rect(area.right - 4, area.y, 3, area.height)
        guiGraphics.fill(track.x, track.y, track.right, track.bottom, colorWithRenderAlpha(0x33000000))
        val thumbHeight = (area.height * area.height / contentHeight).coerceAtLeast(18)
        val thumbY = area.y + ((area.height - thumbHeight) * scroll / max(1, contentHeight - area.height))
        guiGraphics.fill(track.x, thumbY, track.right, thumbY + thumbHeight, colorWithRenderAlpha(0xAAFFFFFF.toInt()))
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int, sourceCorner: Int, destinationCorner: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        val edgeX = textureWidth - sourceCorner
        val edgeY = textureHeight - sourceCorner
        val middleWidth = textureWidth - sourceCorner * 2
        val middleHeight = textureHeight - sourceCorner * 2
        val innerWidth = (rect.width - destinationCorner * 2).coerceAtLeast(0)
        val innerHeight = (rect.height - destinationCorner * 2).coerceAtLeast(0)
        blit(guiGraphics, texture, Rect(rect.x, rect.y, destinationCorner, destinationCorner), 0, 0, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y, innerWidth, destinationCorner), sourceCorner, 0, middleWidth, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, Rect(rect.right - destinationCorner, rect.y, destinationCorner, destinationCorner), edgeX, 0, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, Rect(rect.x, rect.y + destinationCorner, destinationCorner, innerHeight), 0, sourceCorner, sourceCorner, middleHeight, textureWidth, textureHeight)
        blit(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + destinationCorner, innerWidth, innerHeight), sourceCorner, sourceCorner, middleWidth, middleHeight, textureWidth, textureHeight)
        blit(guiGraphics, texture, Rect(rect.right - destinationCorner, rect.y + destinationCorner, destinationCorner, innerHeight), edgeX, sourceCorner, sourceCorner, middleHeight, textureWidth, textureHeight)
        blit(guiGraphics, texture, Rect(rect.x, rect.bottom - destinationCorner, destinationCorner, destinationCorner), 0, edgeY, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.bottom - destinationCorner, innerWidth, destinationCorner), sourceCorner, edgeY, middleWidth, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, Rect(rect.right - destinationCorner, rect.bottom - destinationCorner, destinationCorner, destinationCorner), edgeX, edgeY, sourceCorner, sourceCorner, textureWidth, textureHeight)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun blit(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int, textureWidth: Int, textureHeight: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, sourceX.toFloat(), sourceY.toFloat(), sourceWidth, sourceHeight, textureWidth, textureHeight)
    }

    private fun withEntrance(guiGraphics: GuiGraphics, panel: Rect, render: () -> Unit) {
        val eased = renderAlpha
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0.0f, SLIDE_Y * (1.0f - eased), 0.0f)
        val scale = 0.97f + 0.03f * eased
        pose.translate((panel.x + panel.width / 2).toFloat(), (panel.y + panel.height / 2).toFloat(), 0.0f)
        pose.scale(scale, scale, 1.0f)
        pose.translate(-(panel.x + panel.width / 2).toFloat(), -(panel.y + panel.height / 2).toFloat(), 0.0f)
        render()
        pose.popPose()
    }

    private fun drawCenteredCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int, fontId: ResourceLocation) {
        val component = Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }
        guiGraphics.drawString(font, component, x + (width - font.width(component)) / 2, y, colorWithRenderAlpha(color), false)
    }

    private fun panelRect(): Rect {
        val panelWidth = (width * 0.52f).toInt().coerceIn(330, 500).coerceAtMost(width - 24)
        val panelHeight = (height * 0.78f).toInt().coerceIn(260, 390).coerceAtMost(height - 24)
        return Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight)
    }

    private fun maxScroll(): Int = (NpcFriendsClient.snapshot().size * ROW_STEP - listArea.height).coerceAtLeast(0)

    private fun heartText(level: Int): String {
        val count = level.coerceIn(0, 10)
        return if (count <= 0) "" else List(count) { "<3" }.joinToString(" ")
    }

    private fun fitPlain(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && font.width("$value...") > maxWidth) value = value.dropLast(1)
        return "$value..."
    }

    private fun entranceProgress(): Float {
        val elapsed = (Util.getMillis() - openedAtMs).toFloat()
        val linear = (elapsed / ANIMATION_DURATION_MS).coerceIn(0.0f, 1.0f)
        val inverse = 1.0f - linear
        return 1.0f - inverse * inverse * inverse
    }

    private fun colorWithRenderAlpha(color: Int): Int = ((((color ushr 24) and 0xFF) * renderAlpha).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    companion object {
        private const val PAD = 20
        private const val ROW_HEIGHT = 44
        private const val ROW_STEP = 48
        private const val HEAD_SIZE = 30
        private const val SCROLL_STEP = 22
        private const val SKIN_TEXTURE_SIZE = 64
        private const val PANEL_TEXTURE_WIDTH = 1646
        private const val PANEL_TEXTURE_HEIGHT = 256
        private const val PANEL_SOURCE_CORNER = 75
        private const val PANEL_DEST_CORNER = 14
        private const val ANIMATION_DURATION_MS = 220.0f
        private const val SLIDE_Y = 14.0f
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFD8D0B8.toInt()
        private const val GOLD = 0xFFFFD66B.toInt()
        private const val HEART = 0xFFFF7FA6.toInt()
        private const val ROW_FILL = 0x26000000
        private val PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
        private val CKDM_BOLD = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    }
}

private fun npcTexture(npcId: String): ResourceLocation {
    val cleanId = npcId.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_./-]"), "")
    return if (cleanId.isBlank()) STEVE_TEXTURE else ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/entity/npc/$cleanId.png")
}

private val STEVE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")