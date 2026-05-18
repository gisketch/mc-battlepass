package dev.gisketch.chowkingdom.client

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.gyms.GymEncounterUiPayload
import dev.gisketch.chowkingdom.gyms.GymLeagueClientState
import dev.gisketch.chowkingdom.gyms.GymLeagueNetwork
import dev.gisketch.chowkingdom.gyms.GymLeagueUiPayload
import dev.gisketch.chowkingdom.gyms.GymPlayerLeagueUiPayload
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale

object PokemonBadgesClient {
    fun open() {
        GymLeagueNetwork.requestSync()
        Minecraft.getInstance().setScreen(PokemonBadgesScreen())
    }
}

private class PokemonBadgesScreen : Screen(Component.literal("Badges")) {
    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
    }

    private data class TooltipZone(val rect: Rect, val lines: List<Component>)

    private var openedAtMs = 0L
    private var renderAlpha = 1.0f
    private var selectedLeagueId = ""
    private var tooltipZones: List<TooltipZone> = emptyList()

    override fun init() {
        openedAtMs = Util.getMillis()
        GymLeagueNetwork.requestSync()
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        renderAlpha = entranceProgress()
        val panel = panelRect()
        val zones = mutableListOf<TooltipZone>()
        withEntrance(guiGraphics, panel) {
            renderNineSlice(guiGraphics, PANEL_TEXTURE, panel, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER, 1.0f)
            renderContent(guiGraphics, panel, mouseX, mouseY, zones)
        }
        tooltipZones = zones
        zones.firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { guiGraphics.renderComponentTooltip(font, it.lines, mouseX, mouseY) }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0x99000000.toInt())
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val leagues = GymLeagueClientState.leagues()
        val tabs = tabRects(panelRect(), leagues)
        tabs.entries.firstOrNull { it.value.contains(mouseX.toInt(), mouseY.toInt()) }?.let { (leagueId, _) ->
            selectedLeagueId = leagueId
            playClick()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun renderContent(guiGraphics: GuiGraphics, panel: Rect, mouseX: Int, mouseY: Int, zones: MutableList<TooltipZone>) {
        val leagues = GymLeagueClientState.leagues()
        if (leagues.isEmpty()) {
            drawCenteredCkdm(guiGraphics, "Badges", panel.x, panel.y + 18, panel.width, WHITE, CKDM_BOLD)
            guiGraphics.drawString(font, "No league data synced yet.", panel.x + PAD, panel.y + 52, colorWithRenderAlpha(MUTED), false)
            return
        }
        val selected = selectedLeague(leagues)
        val state = GymLeagueClientState.stateFor(selected.id) ?: GymPlayerLeagueUiPayload(selected.id, emptyList(), emptyList(), "", false, "Not active")
        val active = GymLeagueClientState.activeLeagueId() == selected.id
        val badgeEncounters = selected.encounters.filter { it.badgeId.isNotBlank() }
        val earnedBadges = state.badgeIds.toSet()
        val stats = GymLeagueClientState.stats()

        drawCenteredCkdm(guiGraphics, "Badges", panel.x, panel.y + 16, panel.width, WHITE, CKDM_BOLD)
        renderTabs(guiGraphics, panel, leagues, selected.id, mouseX, mouseY, zones)

        val contentX = panel.x + PAD
        var cursorY = panel.y + 78
        val summary = Rect(contentX, cursorY, panel.width - PAD * 2, 34)
        renderSummary(guiGraphics, summary, selected, state, active, stats, zones)
        cursorY += 45

        drawCkdm(guiGraphics, "${selected.region} Badge Record", contentX, cursorY, GOLD, CKDM_SMALL)
        cursorY += 15
        renderBadgeGrid(guiGraphics, Rect(contentX, cursorY, panel.width - PAD * 2, 96), badgeEncounters, earnedBadges, zones)
        cursorY += 108

        renderNextRecord(guiGraphics, Rect(contentX, cursorY, panel.width - PAD * 2, panel.bottom - cursorY - PAD), selected, state, active, zones)
    }

    private fun renderTabs(guiGraphics: GuiGraphics, panel: Rect, leagues: List<GymLeagueUiPayload>, selectedId: String, mouseX: Int, mouseY: Int, zones: MutableList<TooltipZone>) {
        tabRects(panel, leagues).forEach { (leagueId, rect) ->
            val league = leagues.firstOrNull { it.id == leagueId } ?: return@forEach
            val selected = league.id == selectedId
            val hovered = rect.contains(mouseX, mouseY)
            val texture = if (selected || hovered) GREEN_BUTTON_TEXTURE else GRAY_BUTTON_TEXTURE
            renderNineSlice(guiGraphics, texture, rect, BUTTON_TEXTURE_SIZE, BUTTON_TEXTURE_SIZE, BUTTON_SOURCE_CORNER, BUTTON_DEST_CORNER, 1.0f)
            renderTexture(guiGraphics, TROPHY_ICON, rect.x + 7, rect.y + 4, 13, ICON_SOURCE_SIZE)
            drawCkdm(guiGraphics, "GEN ${league.generation}", rect.x + 25, rect.y + 5, WHITE, CKDM_SMALL)
            zones += TooltipZone(rect, listOf(Component.literal(league.displayName), Component.literal(league.description).withStyle(ChatFormatting.GRAY)))
        }
    }

    private fun renderSummary(guiGraphics: GuiGraphics, rect: Rect, league: GymLeagueUiPayload, state: GymPlayerLeagueUiPayload, active: Boolean, stats: dev.gisketch.chowkingdom.gyms.GymPlayerPokemonStatsPayload, zones: MutableList<TooltipZone>) {
        renderNineSlice(guiGraphics, PANEL_TEXTURE, rect, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER, 0.55f)
        val badgeTotal = league.encounters.count { it.badgeId.isNotBlank() }.coerceAtLeast(1)
        val status = if (active) "ACTIVE RECORD" else state.nextStatus.uppercase(Locale.ROOT)
        val left = "${league.region} / ${state.badgeIds.size}/$badgeTotal badges"
        guiGraphics.drawString(font, left, rect.x + 9, rect.y + 7, colorWithRenderAlpha(WHITE), false)
        guiGraphics.drawString(font, status, rect.x + 9, rect.y + 19, colorWithRenderAlpha(if (active) GREEN else MUTED), false)
        val caught = "Caught ${stats.uniquePokemonCaught}"
        val wins = "Wins ${stats.totalRecordWins}"
        guiGraphics.drawString(font, caught, rect.right - font.width(caught) - 9, rect.y + 7, colorWithRenderAlpha(GOLD), false)
        guiGraphics.drawString(font, wins, rect.right - font.width(wins) - 9, rect.y + 19, colorWithRenderAlpha(MUTED), false)
        zones += TooltipZone(rect, listOf(Component.literal(league.displayName), Component.literal(league.description).withStyle(ChatFormatting.GRAY)))
    }

    private fun renderBadgeGrid(guiGraphics: GuiGraphics, rect: Rect, badges: List<GymEncounterUiPayload>, earnedBadges: Set<String>, zones: MutableList<TooltipZone>) {
        val columns = 4
        val gap = 5
        val cellWidth = (rect.width - gap * (columns - 1)) / columns
        badges.forEachIndexed { index, encounter ->
            val col = index % columns
            val row = index / columns
            val cell = Rect(rect.x + col * (cellWidth + gap), rect.y + row * (BADGE_CELL_HEIGHT + gap), cellWidth, BADGE_CELL_HEIGHT)
            val earned = encounter.badgeId in earnedBadges
            renderNineSlice(guiGraphics, PANEL_TEXTURE, cell, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER, 0.58f)
            val badgeSize = 28
            val badgeX = cell.x + 7
            val badgeY = cell.y + (cell.height - badgeSize) / 2
            if (earned) guiGraphics.fill(badgeX - 2, badgeY - 2, badgeX + badgeSize + 2, badgeY + badgeSize + 2, colorWithRenderAlpha(BADGE_GOLD_FILL))
            renderTextureAlpha(guiGraphics, badgeTexture(encounter.badgeId), badgeX, badgeY, badgeSize, BADGE_TEXTURE_SIZE, if (earned) 1.0f else 0.28f)
            val textX = cell.x + 43
            val title = fitPlain(badgeLabel(encounter.badgeId), cell.width - 49)
            guiGraphics.drawString(font, title, textX, cell.y + 7, colorWithRenderAlpha(if (earned) GOLD else MUTED), false)
            guiGraphics.drawString(font, fitPlain(encounter.trainerName, cell.width - 49), textX, cell.y + 19, colorWithRenderAlpha(if (earned) WHITE else MUTED), false)
            zones += TooltipZone(
                cell,
                listOf(
                    Component.literal(badgeLabel(encounter.badgeId)),
                    Component.literal("${encounter.displayName} / cap ${encounter.levelCap}").withStyle(ChatFormatting.GRAY),
                    Component.literal(if (earned) "Earned" else "Locked").withStyle(if (earned) ChatFormatting.YELLOW else ChatFormatting.DARK_GRAY),
                ),
            )
        }
    }

    private fun renderNextRecord(guiGraphics: GuiGraphics, rect: Rect, league: GymLeagueUiPayload, state: GymPlayerLeagueUiPayload, active: Boolean, zones: MutableList<TooltipZone>) {
        renderNineSlice(guiGraphics, PANEL_TEXTURE, rect, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER, 0.55f)
        val next = league.encounters.firstOrNull { it.id == state.nextEncounterId }
        val title = when {
            next == null -> "Record complete"
            active && state.nextAvailable -> "Next: ${next.displayName}"
            active -> "Next locked: ${next.displayName}"
            else -> "Record archived"
        }
        drawCkdm(guiGraphics, title, rect.x + 9, rect.y + 8, if (active && state.nextAvailable) GREEN else GOLD, CKDM_SMALL)
        val detail = when {
            next == null -> "All posted encounters are cleared for this league record."
            active -> "${state.nextStatus}. Level cap ${next.levelCap}. ${next.rewardXp} XP / ${next.rewardChowcoins} Chowcoins."
            state.clearedEncounterIds.isNotEmpty() -> "Progress is saved. Retired records keep earned badges."
            else -> "Talk to Professor Chowfan to start this league."
        }
        guiGraphics.drawString(font, fitPlain(detail, rect.width - 18), rect.x + 9, rect.y + 24, colorWithRenderAlpha(WHITE), false)
        zones += TooltipZone(rect, listOf(Component.literal(title), Component.literal(detail).withStyle(ChatFormatting.GRAY)))
    }

    private fun selectedLeague(leagues: List<GymLeagueUiPayload>): GymLeagueUiPayload {
        val active = GymLeagueClientState.activeLeagueId()
        if (selectedLeagueId.isBlank()) selectedLeagueId = active.ifBlank { leagues.first().id }
        return leagues.firstOrNull { it.id == selectedLeagueId } ?: leagues.first().also { selectedLeagueId = it.id }
    }

    private fun tabRects(panel: Rect, leagues: List<GymLeagueUiPayload>): Map<String, Rect> {
        val contentX = panel.x + PAD
        val y = panel.y + 48
        val gap = 5
        val width = ((panel.width - PAD * 2) - gap * (leagues.size - 1).coerceAtLeast(0)) / leagues.size.coerceAtLeast(1)
        return leagues.associate { league ->
            league.id to Rect(contentX + leagues.indexOf(league) * (width + gap), y, width, 21)
        }
    }

    private fun renderTexture(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int, sourceSize: Int) {
        renderTextureAlpha(guiGraphics, texture, x, y, size, sourceSize, 1.0f)
    }

    private fun renderTextureAlpha(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int, sourceSize: Int, alpha: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha * alpha.coerceIn(0.0f, 1.0f))
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, sourceSize, sourceSize, sourceSize, sourceSize)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun badgeTexture(badgeId: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/badges/${badgeId.lowercase(Locale.ROOT)}.png")

    private fun badgeLabel(badgeId: String): String {
        val label = badgeId.replace('_', ' ').lowercase(Locale.ROOT).trim()
        return label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int, sourceCorner: Int, destinationCorner: Int, alpha: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha * renderAlpha)
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
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0.0f, 12.0f * (1.0f - renderAlpha), 0.0f)
        val scale = 0.97f + 0.03f * renderAlpha
        pose.translate((panel.x + panel.width / 2).toFloat(), (panel.y + panel.height / 2).toFloat(), 0.0f)
        pose.scale(scale, scale, 1.0f)
        pose.translate(-(panel.x + panel.width / 2).toFloat(), -(panel.y + panel.height / 2).toFloat(), 0.0f)
        render()
        pose.popPose()
    }

    private fun drawCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, fontId: ResourceLocation) {
        guiGraphics.drawString(font, ckdmText(text, fontId), x, y, colorWithRenderAlpha(color), false)
    }

    private fun drawCenteredCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int, fontId: ResourceLocation) {
        val component = ckdmText(text, fontId)
        guiGraphics.drawString(font, component, x + (width - font.width(component)) / 2, y, colorWithRenderAlpha(color), false)
    }

    private fun ckdmText(text: String, fontId: ResourceLocation): Component = Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    private fun fitPlain(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && font.width("$value...") > maxWidth) value = value.dropLast(1)
        return "$value..."
    }

    private fun panelRect(): Rect {
        val panelWidth = (width * 0.62f).toInt().coerceIn(430, 590).coerceAtMost(width - 24)
        val panelHeight = (height * 0.76f).toInt().coerceIn(300, 390).coerceAtMost(height - 24)
        return Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight)
    }

    private fun entranceProgress(): Float {
        val elapsed = (Util.getMillis() - openedAtMs).toFloat()
        val linear = (elapsed / ANIMATION_DURATION_MS).coerceIn(0.0f, 1.0f)
        val inverse = 1.0f - linear
        return 1.0f - inverse * inverse * inverse
    }

    private fun colorWithRenderAlpha(color: Int): Int = ((((color ushr 24) and 0xFF) * renderAlpha).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    private fun playClick() {
        Minecraft.getInstance().soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.55f))
    }

    @Suppress("unused")
    private fun pokeBallStack(): ItemStack = ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")).orElse(Items.BARRIER))

    companion object {
        private const val PAD = 16
        private const val BADGE_CELL_HEIGHT = 38
        private const val BADGE_TEXTURE_SIZE = 32
        private const val PANEL_TEXTURE_WIDTH = 1646
        private const val PANEL_TEXTURE_HEIGHT = 256
        private const val PANEL_SOURCE_CORNER = 75
        private const val PANEL_DEST_CORNER = 14
        private const val BUTTON_TEXTURE_SIZE = 8
        private const val BUTTON_SOURCE_CORNER = 2
        private const val BUTTON_DEST_CORNER = 4
        private const val ICON_SOURCE_SIZE = 16
        private const val ANIMATION_DURATION_MS = 220.0f
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFD8D0B8.toInt()
        private const val GOLD = 0xFFFFD66B.toInt()
        private const val GREEN = 0xFF63E68B.toInt()
        private const val BADGE_GOLD_FILL = 0xCC8B6100.toInt()
        private val PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
        private val GRAY_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_gray.png")
        private val GREEN_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green.png")
        private val CKDM_BOLD = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
        private val CKDM_SMALL = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
        private val TROPHY_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/trophy.png")
    }
}
