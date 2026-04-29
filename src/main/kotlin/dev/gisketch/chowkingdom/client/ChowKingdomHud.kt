package dev.gisketch.chowkingdom.client

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionEntry
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionScope
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionService
import dev.gisketch.chowkingdom.battlepass.BattlepassPassRegistry
import dev.gisketch.chowkingdom.battlepass.BattlepassTrackedMissions
import dev.gisketch.chowkingdom.battlepass.BattlepassXpEventDefinition
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import org.lwjgl.glfw.GLFW

object ChowKingdomHud {
    private data class HudMission(val entry: BattlepassMissionEntry, val progress: Int, val title: String)

    private val LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "player_hud")
    private val AVATAR_BORDER_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/avatar-border.png")
    private val HUD_CONTAINER_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/hud-container.png")
    private val GREEN_BORDER_MASK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/green-border-mask.png")
    private val ORANGE_BORDER_MASK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/orange-border-mask.png")
    private val CYAN_BORDER_MASK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/cyan-border-mask.png")
    private val MARKER_QUEST_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/marker_quest.png")
    private val COINS_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/coins.png")
    private var detailProgress = 0.0f

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerGuiLayers)
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAboveAll(LAYER_ID) { guiGraphics, _ -> render(guiGraphics) }
    }

    private fun render(guiGraphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (minecraft.options.hideGui || minecraft.screen != null) return

        val name = player.gameProfile.name
        val avatarX = HUD_PADDING
        val avatarY = HUD_PADDING
        val textX = avatarX + BORDER_SIZE + NAME_GAP
        val nameY = avatarY
        val coinY = nameY + HEADER_PILL_HEIGHT + HEADER_PILL_GAP
        val trackedX = avatarX
        val trackedY = avatarY + BORDER_SIZE + TRACKED_TOP_GAP
        val detailsVisible = GLFW.glfwGetKey(minecraft.window.window, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS
        detailProgress = Mth.lerp(TRACKED_DETAIL_ANIMATION_SPEED, detailProgress, if (detailsVisible) 1.0f else 0.0f)

        guiGraphics.blit(AVATAR_BORDER_TEXTURE, avatarX, avatarY, BORDER_SIZE, BORDER_SIZE, 0.0f, 0.0f, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE)
        PlayerFaceRenderer.draw(guiGraphics, player.skin, avatarX + AVATAR_INSET, avatarY + AVATAR_INSET, AVATAR_SIZE)

        val passes = BattlepassClientState.passes().ifEmpty { BattlepassPassRegistry.all().toList() }
        val trackedMissions = BattlepassTrackedMissions.trackedMissions(passes)
        val headerOffset = textX - trackedX
        val sharedWidth = sharedRowWidth(minecraft, name, trackedMissions, headerOffset)
        val headerWidth = (sharedWidth - headerOffset).coerceAtLeast(TRACKED_MIN_WIDTH)
        renderNamePill(guiGraphics, minecraft, textX, nameY, name, headerWidth)
        renderCoinPill(guiGraphics, minecraft, textX, coinY, "100K", headerWidth)
        renderTrackedMissions(guiGraphics, minecraft, trackedX, trackedY, trackedMissions, missionPillWidth(sharedWidth))
    }

    private fun sharedRowWidth(minecraft: Minecraft, name: String, missions: List<BattlepassTrackedMissions.TrackedMission>, headerOffset: Int): Int {
        val maxTextWidth = ((TRACKED_MAX_WIDTH - TRACKED_TEXT_PADDING * 2) / TRACKED_TEXT_SCALE).toInt()
        val nameWidth = headerOffset + pillWidthFor(minecraft, trimToWidth(minecraft, name, maxTextWidth))
        val coinWidth = headerOffset + HEADER_ICON_SIZE + HEADER_ICON_GAP + pillWidthFor(minecraft, "100K")
        val missionWidth = missions.maxOfOrNull { mission -> TRACKED_MARKER_SIZE + TRACKED_MARKER_GAP + pillWidthFor(minecraft, trimToWidth(minecraft, missionDescription(mission.entry.event), maxTextWidth)) } ?: TRACKED_MIN_WIDTH
        return maxOf(nameWidth, coinWidth, missionWidth).coerceIn(TRACKED_MIN_WIDTH, TRACKED_MAX_WIDTH)
    }

    private fun renderNamePill(guiGraphics: GuiGraphics, minecraft: Minecraft, x: Int, y: Int, name: String, pillWidth: Int) {
        val title = trimToWidth(minecraft, name, ((pillWidth - TRACKED_TEXT_PADDING * 2) / TRACKED_TEXT_SCALE).toInt())
        renderStretchedHudTexture(guiGraphics, HUD_CONTAINER_TEXTURE, x, y, pillWidth, HEADER_PILL_HEIGHT)
        renderStretchedHudTexture(guiGraphics, CYAN_BORDER_MASK_TEXTURE, x, y, pillWidth, HEADER_PILL_HEIGHT)
        drawPillText(guiGraphics, minecraft, title, x, y, HEADER_PILL_HEIGHT)
    }

    private fun renderCoinPill(guiGraphics: GuiGraphics, minecraft: Minecraft, x: Int, y: Int, amount: String, totalWidth: Int) {
        val pillX = x + HEADER_ICON_SIZE + HEADER_ICON_GAP
        val pillWidth = (totalWidth - HEADER_ICON_SIZE - HEADER_ICON_GAP).coerceAtLeast(TRACKED_MIN_WIDTH)
        val title = trimToWidth(minecraft, amount, ((pillWidth - TRACKED_TEXT_PADDING * 2) / TRACKED_TEXT_SCALE).toInt())
        renderIcon(guiGraphics, COINS_TEXTURE, x, y, HEADER_ICON_SIZE, HEADER_ICON_TEXTURE_SIZE)
        renderStretchedHudTexture(guiGraphics, HUD_CONTAINER_TEXTURE, pillX, y, pillWidth, HEADER_PILL_HEIGHT)
        renderStretchedHudTexture(guiGraphics, ORANGE_BORDER_MASK_TEXTURE, pillX, y, pillWidth, HEADER_PILL_HEIGHT)
        drawPillText(guiGraphics, minecraft, title, pillX, y, HEADER_PILL_HEIGHT)
    }

    private fun renderTrackedMissions(guiGraphics: GuiGraphics, minecraft: Minecraft, x: Int, y: Int, missions: List<BattlepassTrackedMissions.TrackedMission>, pillWidth: Int) {
        val playerId = BattlepassClientState.selfId() ?: minecraft.player?.uuid ?: return
        val maxTextWidth = ((pillWidth - TRACKED_TEXT_PADDING * 2) / TRACKED_TEXT_SCALE).toInt()
        val hudMissions = missions.map { mission ->
            val event = mission.entry.event
            val progress = BattlepassClientState.missionProgress(playerId, mission.pass.id, mission.entry.key)
                ?: BattlepassClientState.missionProgress(playerId, mission.pass.id, event.event)
                ?: event.progress
            HudMission(mission.entry, progress, trimToWidth(minecraft, missionDescription(event), maxTextWidth))
        }

        hudMissions.forEachIndexed { index, mission ->
            val rowY = y + index * TRACKED_ROW_HEIGHT
            renderTrackedProgress(guiGraphics, minecraft, x, rowY, mission, pillWidth)
        }
    }

    private fun renderTrackedProgress(guiGraphics: GuiGraphics, minecraft: Minecraft, x: Int, y: Int, mission: HudMission, pillWidth: Int) {
        val event = mission.entry.event
        val goal = missionGoal(event)
        if (goal <= 0) return
        val cappedProgress = mission.progress.coerceIn(0, goal)
        val progressText = "$cappedProgress/$goal"
        val fillWidth = (pillWidth * (cappedProgress / goal.toFloat())).toInt().coerceIn(0, pillWidth)
        val pillX = x + TRACKED_MARKER_SIZE + TRACKED_MARKER_GAP
        renderIcon(guiGraphics, MARKER_QUEST_TEXTURE, x, y, TRACKED_MARKER_SIZE, MARKER_TEXTURE_SIZE)
        renderStretchedHudTexture(guiGraphics, HUD_CONTAINER_TEXTURE, pillX, y, pillWidth, TRACKED_HEIGHT)
        if (fillWidth > 0) {
            guiGraphics.enableScissor(pillX, y, pillX + fillWidth, y + TRACKED_HEIGHT)
            renderStretchedHudTexture(guiGraphics, missionMask(mission.entry.scope), pillX, y, pillWidth, TRACKED_HEIGHT)
            guiGraphics.disableScissor()
        }
        drawPillText(guiGraphics, minecraft, mission.title, pillX, y, TRACKED_HEIGHT)
        if (detailProgress > 0.01f) {
            val detailX = pillX + pillWidth + TRACKED_DETAIL_GAP + ((1.0f - detailProgress) * TRACKED_DETAIL_SLIDE).toInt()
            val detailY = y + (TRACKED_HEIGHT - minecraft.font.lineHeight) / 2
            guiGraphics.drawString(minecraft.font, progressText, detailX, detailY, colorWithAlpha(TRACKED_TEXT_COLOR, detailProgress), true)
        }
    }

    private fun renderStretchedHudTexture(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int) {
        val outputBorder = outputBorder(height).coerceAtMost(width / 2)
        val middleWidth = (width - outputBorder * 2).coerceAtLeast(0)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
        guiGraphics.blit(texture, x, y, outputBorder, height, 0.0f, 0.0f, HUD_TEXTURE_BORDER, HUD_TEXTURE_HEIGHT, HUD_TEXTURE_WIDTH, HUD_TEXTURE_HEIGHT)
        if (middleWidth > 0) {
            guiGraphics.blit(texture, x + outputBorder, y, middleWidth, height, HUD_TEXTURE_BORDER.toFloat(), 0.0f, HUD_TEXTURE_WIDTH - HUD_TEXTURE_BORDER * 2, HUD_TEXTURE_HEIGHT, HUD_TEXTURE_WIDTH, HUD_TEXTURE_HEIGHT)
        }
        guiGraphics.blit(texture, x + width - outputBorder, y, outputBorder, height, (HUD_TEXTURE_WIDTH - HUD_TEXTURE_BORDER).toFloat(), 0.0f, HUD_TEXTURE_BORDER, HUD_TEXTURE_HEIGHT, HUD_TEXTURE_WIDTH, HUD_TEXTURE_HEIGHT)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderIcon(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int, textureSize: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, textureSize, textureSize, textureSize, textureSize)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun drawPillText(guiGraphics: GuiGraphics, minecraft: Minecraft, text: String, x: Int, y: Int, height: Int) {
        val textHeight = minecraft.font.lineHeight * TRACKED_TEXT_SCALE
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + TRACKED_TEXT_PADDING.toFloat(), y + (height - textHeight) / 2.0f, 0.0f)
        pose.scale(TRACKED_TEXT_SCALE, TRACKED_TEXT_SCALE, 1.0f)
        guiGraphics.drawString(minecraft.font, text, 0, 0, TRACKED_TITLE_COLOR, true)
        pose.popPose()
    }

    private fun outputBorder(height: Int): Int = ((HUD_TEXTURE_BORDER * height + HUD_TEXTURE_HEIGHT / 2) / HUD_TEXTURE_HEIGHT).coerceAtLeast(1)

    private fun missionPillWidth(sharedWidth: Int): Int = (sharedWidth - TRACKED_MARKER_SIZE - TRACKED_MARKER_GAP).coerceAtLeast(TRACKED_MIN_WIDTH)

    private fun missionGoal(event: BattlepassXpEventDefinition): Int = when {
        BattlepassMissionService.isCappedRepeating(event) -> event.xpCap
        BattlepassMissionService.isProgressive(event) -> BattlepassMissionService.progressiveGoal(event)
        else -> 0
    }

    private fun missionDescription(event: BattlepassXpEventDefinition): String = event.eventDesc.ifBlank { event.event }

    private fun pillWidthFor(minecraft: Minecraft, text: String): Int = ((minecraft.font.width(text) * TRACKED_TEXT_SCALE).toInt() + TRACKED_TEXT_PADDING * 2).coerceIn(TRACKED_MIN_WIDTH, TRACKED_MAX_WIDTH)

    private fun missionMask(scope: BattlepassMissionScope): ResourceLocation = when (scope) {
        BattlepassMissionScope.DAILY -> GREEN_BORDER_MASK_TEXTURE
        BattlepassMissionScope.WEEKLY -> ORANGE_BORDER_MASK_TEXTURE
        BattlepassMissionScope.PERMANENT -> CYAN_BORDER_MASK_TEXTURE
    }

    private fun colorWithAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0.0f, 1.0f) * 255).toInt() shl 24) or (color and 0x00FFFFFF)

    private fun trimToWidth(minecraft: Minecraft, text: String, width: Int): String {
        if (minecraft.font.width(text) <= width) return text
        var trimmed = text
        while (trimmed.isNotEmpty() && minecraft.font.width("$trimmed...") > width) {
            trimmed = trimmed.dropLast(1)
        }
        return "$trimmed..."
    }

    private const val HUD_PADDING = 8
    private const val BORDER_SIZE = 32
    private const val AVATAR_SIZE = 24
    private const val AVATAR_INSET = (BORDER_SIZE - AVATAR_SIZE) / 2
    private const val NAME_GAP = 6
    private const val TRACKED_TOP_GAP = 5
    private const val HEADER_PILL_HEIGHT = 14
    private const val HEADER_PILL_GAP = 8
    private const val HEADER_ICON_SIZE = 14
    private const val HEADER_ICON_GAP = 4
    private const val HEADER_ICON_TEXTURE_SIZE = 16
    private const val TRACKED_MIN_WIDTH = 52
    private const val TRACKED_MAX_WIDTH = 150
    private const val TRACKED_HEIGHT = 19
    private const val TRACKED_ROW_HEIGHT = 22
    private const val TRACKED_TEXT_SCALE = 0.85f
    private const val TRACKED_TEXT_PADDING = 6
    private const val TRACKED_MARKER_SIZE = TRACKED_HEIGHT
    private const val TRACKED_MARKER_GAP = 2
    private const val TRACKED_DETAIL_GAP = 8
    private const val TRACKED_DETAIL_SLIDE = 8
    private const val TRACKED_DETAIL_ANIMATION_SPEED = 0.28f
    private const val HUD_TEXTURE_WIDTH = 128
    private const val HUD_TEXTURE_HEIGHT = 24
    private const val HUD_TEXTURE_BORDER = 4
    private const val MARKER_TEXTURE_SIZE = 16
    private const val TRACKED_TITLE_COLOR = 0xFFFFFFFF.toInt()
    private const val TRACKED_TEXT_COLOR = 0xFFFFFFFF.toInt()
}