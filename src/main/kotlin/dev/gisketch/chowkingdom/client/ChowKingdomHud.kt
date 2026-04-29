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
import dev.gisketch.chowkingdom.shipping.ShippingBinClientState
import dev.gisketch.chowkingdom.wallets.ChowcoinClientState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import org.lwjgl.glfw.GLFW
import java.util.Locale

object ChowKingdomHud {
    private data class HudMission(val passId: String, val entry: BattlepassMissionEntry, val progress: Int, val title: String, val completed: Boolean)
    private data class ActiveCompletionToast(val title: String, val missionName: String, val startedAt: Long)
    private data class ActiveShippingSaleToast(val itemCount: Int, val amount: Long, val startedAt: Long)

    private val LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "player_hud")
    private val AVATAR_BORDER_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/avatar-border.png")
    private val HUD_CONTAINER_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/hud-container.png")
    private val GREEN_BORDER_MASK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/green-border-mask.png")
    private val ORANGE_BORDER_MASK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/orange-border-mask.png")
    private val CYAN_BORDER_MASK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/cyan-border-mask.png")
    private val MARKER_QUEST_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/marker_quest.png")
    private val CHECK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/accept.png")
    private val COINS_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/coins.png")
    private val TOAST_BUTTON_SPRITE: ResourceLocation = ResourceLocation.withDefaultNamespace("widget/button")
    private var detailProgress = 0.0f
    private val activeCompletionToasts: MutableList<ActiveCompletionToast> = mutableListOf()
    private val activeShippingSaleToasts: MutableList<ActiveShippingSaleToast> = mutableListOf()

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerGuiLayers)
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAboveAll(LAYER_ID) { guiGraphics, _ -> render(guiGraphics) }
    }

    private fun render(guiGraphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (minecraft.options.hideGui || (minecraft.screen != null && minecraft.screen !is ChatScreen)) return
        queueCompletionToasts(minecraft)
        queueShippingSaleToasts(minecraft)

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
        if (!detailsVisible && detailProgress < TRACKED_DETAIL_MIN_ALPHA) detailProgress = 0.0f
        if (detailsVisible && detailProgress > 1.0f - TRACKED_DETAIL_MIN_ALPHA) detailProgress = 1.0f

        guiGraphics.blit(AVATAR_BORDER_TEXTURE, avatarX, avatarY, BORDER_SIZE, BORDER_SIZE, 0.0f, 0.0f, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE)
        PlayerFaceRenderer.draw(guiGraphics, player.skin, avatarX + AVATAR_INSET, avatarY + AVATAR_INSET, AVATAR_SIZE)

        val passes = BattlepassClientState.passes().ifEmpty { BattlepassPassRegistry.all().toList() }
        val trackedMissions = BattlepassTrackedMissions.trackedMissions(passes)
        val headerOffset = textX - trackedX
        val selfId = BattlepassClientState.selfId() ?: player.uuid
        val chowcoinText = formatChowcoins(ChowcoinClientState.balance())
        val sharedWidth = sharedRowWidth(minecraft, name, chowcoinText, trackedMissions, headerOffset, selfId)
        val headerWidth = (sharedWidth - headerOffset).coerceAtLeast(TRACKED_MIN_WIDTH)
        renderNamePill(guiGraphics, minecraft, textX, nameY, name, headerWidth)
        renderCoinPill(guiGraphics, minecraft, textX, coinY, chowcoinText, headerWidth)
        renderTrackedMissions(guiGraphics, minecraft, trackedX, trackedY, trackedMissions, missionPillWidth(sharedWidth))
        renderShippingSaleToasts(guiGraphics, minecraft)
        renderCompletionToasts(guiGraphics, minecraft)
    }

    private fun sharedRowWidth(minecraft: Minecraft, name: String, chowcoinText: String, missions: List<BattlepassTrackedMissions.TrackedMission>, headerOffset: Int, playerId: java.util.UUID): Int {
        val maxTextWidth = ((TRACKED_MAX_WIDTH - TRACKED_TEXT_PADDING * 2) / TRACKED_TEXT_SCALE).toInt()
        val nameWidth = headerOffset + pillWidthFor(minecraft, trimToWidth(minecraft, name, maxTextWidth))
        val coinWidth = headerOffset + HEADER_ICON_SIZE + HEADER_ICON_GAP + pillWidthFor(minecraft, chowcoinText)
        val missionWidth = missions.maxOfOrNull { mission -> TRACKED_MARKER_SIZE + TRACKED_MARKER_GAP + pillWidthFor(minecraft, trimToWidth(minecraft, missionDescription(mission.pass.id, mission.entry, playerId), maxTextWidth)) } ?: TRACKED_MIN_WIDTH
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
        val hudMissions = missions.mapNotNull { mission ->
            val event = mission.entry.event
            val progress = BattlepassClientState.missionProgress(playerId, mission.pass.id, mission.entry.key)
                ?: BattlepassClientState.missionProgress(playerId, mission.pass.id, event.event)
                ?: event.progress
            val completed = BattlepassClientState.isMissionCompleted(playerId, mission.pass.id, mission.entry.key)
            if (completed) return@mapNotNull null
            HudMission(mission.pass.id, mission.entry, progress, trimToWidth(minecraft, missionDescription(mission.pass.id, mission.entry, playerId), maxTextWidth), completed)
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
        val alpha = if (mission.completed) TRACKED_COMPLETED_ALPHA else 1.0f
        if (mission.completed) {
            renderIcon(guiGraphics, CHECK_TEXTURE, x + (TRACKED_MARKER_SIZE - TRACKED_CHECK_SIZE) / 2, y + (TRACKED_MARKER_SIZE - TRACKED_CHECK_SIZE) / 2, TRACKED_CHECK_SIZE, MARKER_TEXTURE_SIZE, alpha)
        } else {
            renderIcon(guiGraphics, MARKER_QUEST_TEXTURE, x, y, TRACKED_MARKER_SIZE, MARKER_TEXTURE_SIZE, alpha)
        }
        renderStretchedHudTexture(guiGraphics, HUD_CONTAINER_TEXTURE, pillX, y, pillWidth, TRACKED_HEIGHT, alpha)
        if (fillWidth > 0) {
            guiGraphics.enableScissor(pillX, y, pillX + fillWidth, y + TRACKED_HEIGHT)
            renderStretchedHudTexture(guiGraphics, missionMask(mission.entry.scope), pillX, y, pillWidth, TRACKED_HEIGHT, alpha)
            guiGraphics.disableScissor()
        }
        drawPillText(guiGraphics, minecraft, mission.title, pillX, y, TRACKED_HEIGHT, alpha)
        if (detailProgress > 0.01f) {
            val detailX = pillX + pillWidth + TRACKED_DETAIL_GAP - ((1.0f - detailProgress) * TRACKED_DETAIL_SLIDE).toInt()
            val detailY = y + (TRACKED_HEIGHT - minecraft.font.lineHeight) / 2
            guiGraphics.drawString(minecraft.font, progressText, detailX, detailY, colorWithAlpha(TRACKED_TEXT_COLOR, detailProgress * alpha), false)
        }
    }

    private fun renderStretchedHudTexture(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, alpha: Float = 1.0f) {
        val outputBorder = outputBorder(height).coerceAtMost(width / 2)
        val middleWidth = (width - outputBorder * 2).coerceAtLeast(0)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        guiGraphics.blit(texture, x, y, outputBorder, height, 0.0f, 0.0f, HUD_TEXTURE_BORDER, HUD_TEXTURE_HEIGHT, HUD_TEXTURE_WIDTH, HUD_TEXTURE_HEIGHT)
        if (middleWidth > 0) {
            guiGraphics.blit(texture, x + outputBorder, y, middleWidth, height, HUD_TEXTURE_BORDER.toFloat(), 0.0f, HUD_TEXTURE_WIDTH - HUD_TEXTURE_BORDER * 2, HUD_TEXTURE_HEIGHT, HUD_TEXTURE_WIDTH, HUD_TEXTURE_HEIGHT)
        }
        guiGraphics.blit(texture, x + width - outputBorder, y, outputBorder, height, (HUD_TEXTURE_WIDTH - HUD_TEXTURE_BORDER).toFloat(), 0.0f, HUD_TEXTURE_BORDER, HUD_TEXTURE_HEIGHT, HUD_TEXTURE_WIDTH, HUD_TEXTURE_HEIGHT)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderIcon(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int, textureSize: Int, alpha: Float = 1.0f) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, textureSize, textureSize, textureSize, textureSize)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun drawPillText(guiGraphics: GuiGraphics, minecraft: Minecraft, text: String, x: Int, y: Int, height: Int, alpha: Float = 1.0f) {
        val textHeight = minecraft.font.lineHeight * TRACKED_TEXT_SCALE
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + TRACKED_TEXT_PADDING.toFloat(), y + (height - textHeight) / 2.0f, 0.0f)
        pose.scale(TRACKED_TEXT_SCALE, TRACKED_TEXT_SCALE, 1.0f)
        guiGraphics.drawString(minecraft.font, text, 0, 0, colorWithAlpha(TRACKED_TITLE_COLOR, alpha), false)
        pose.popPose()
    }

    private fun outputBorder(height: Int): Int = ((HUD_TEXTURE_BORDER * height + HUD_TEXTURE_HEIGHT / 2) / HUD_TEXTURE_HEIGHT).coerceAtLeast(1)

    private fun missionPillWidth(sharedWidth: Int): Int = (sharedWidth - TRACKED_MARKER_SIZE - TRACKED_MARKER_GAP).coerceAtLeast(TRACKED_MIN_WIDTH)

    private fun missionGoal(event: BattlepassXpEventDefinition): Int = when {
        BattlepassMissionService.isCappedRepeating(event) -> event.xpCap
        BattlepassMissionService.isProgressive(event) -> BattlepassMissionService.progressiveGoal(event)
        else -> 0
    }

    private fun missionDescription(passId: String, entry: BattlepassMissionEntry, playerId: java.util.UUID): String {
        val event = entry.event
        val progress = BattlepassClientState.missionProgress(playerId, passId, entry.key)
            ?: BattlepassClientState.missionProgress(playerId, passId, event.event)
            ?: event.progress
        return BattlepassMissionService.missionDescription(event, progress)
    }

    private fun pillWidthFor(minecraft: Minecraft, text: String): Int = ((minecraft.font.width(text) * TRACKED_TEXT_SCALE).toInt() + TRACKED_TEXT_PADDING * 2).coerceIn(TRACKED_MIN_WIDTH, TRACKED_MAX_WIDTH)

    private fun formatChowcoins(amount: Long): String = String.format(Locale.US, "%,d", amount)

    private fun missionMask(scope: BattlepassMissionScope): ResourceLocation = when (scope) {
        BattlepassMissionScope.DAILY -> GREEN_BORDER_MASK_TEXTURE
        BattlepassMissionScope.WEEKLY -> ORANGE_BORDER_MASK_TEXTURE
        BattlepassMissionScope.PERMANENT -> CYAN_BORDER_MASK_TEXTURE
    }

    private fun colorWithAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0.0f, 1.0f) * 255).toInt() shl 24) or (color and 0x00FFFFFF)

    private fun queueCompletionToasts(minecraft: Minecraft) {
        BattlepassClientState.drainMissionCompletionNotifications().forEach { notification ->
            activeCompletionToasts += ActiveCompletionToast(completionTitle(notification.scope), notification.title, System.currentTimeMillis())
            minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.18f, 0.65f))
        }
    }

    private fun queueShippingSaleToasts(minecraft: Minecraft) {
        ShippingBinClientState.drainSaleNotifications().forEach { notification ->
            activeShippingSaleToasts += ActiveShippingSaleToast(notification.itemCount, notification.amount, System.currentTimeMillis())
            minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.35f, 0.75f))
            minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.45f, 0.35f))
        }
    }

    private fun renderShippingSaleToasts(guiGraphics: GuiGraphics, minecraft: Minecraft) {
        val now = System.currentTimeMillis()
        activeShippingSaleToasts.removeIf { toast -> now - toast.startedAt >= TOAST_DURATION_MS }
        activeShippingSaleToasts.take(1).forEach { toast ->
            val age = now - toast.startedAt
            val appear = easeOutBack((age / TOAST_ENTER_MS.toFloat()).coerceIn(0.0f, 1.0f))
            val exit = ((age - (TOAST_DURATION_MS - TOAST_EXIT_MS)) / TOAST_EXIT_MS.toFloat()).coerceIn(0.0f, 1.0f)
            val exitScale = 1.0f - easeInOut(exit)
            val scale = Mth.lerp(appear, TOAST_START_SCALE, 1.0f) * exitScale
            val alpha = (appear * exitScale).coerceIn(0.0f, 1.0f)
            if (alpha <= 0.01f) return@forEach

            val toastWidth = shippingToastWidth(minecraft, toast)
            val x = (minecraft.window.guiScaledWidth - toastWidth) / 2
            val y = TOAST_TOP
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(x + toastWidth / 2.0f, y + TOAST_HEIGHT / 2.0f, 0.0f)
            pose.scale(scale, scale, 1.0f)
            pose.translate(-(x + toastWidth / 2.0f), -(y + TOAST_HEIGHT / 2.0f), 0.0f)
            renderVanillaButtonBackground(guiGraphics, x, y, toastWidth, alpha)
            drawScaledToastText(guiGraphics, minecraft, "Shipping Bin", x + TOAST_TEXT_X, y + TOAST_TITLE_Y, TOAST_TITLE_SCALE, TOAST_TITLE_COLOR, alpha)
            renderIcon(guiGraphics, COINS_TEXTURE, x + TOAST_TEXT_X, y + TOAST_NAME_Y - 2, TOAST_COIN_SIZE, HEADER_ICON_TEXTURE_SIZE, alpha)
            drawShippingSaleLine(guiGraphics, minecraft, toast, x + TOAST_TEXT_X + TOAST_COIN_SIZE + TOAST_COIN_GAP, y + TOAST_NAME_Y, alpha)
            pose.popPose()
        }
    }

    private fun renderCompletionToasts(guiGraphics: GuiGraphics, minecraft: Minecraft) {
        val now = System.currentTimeMillis()
        activeCompletionToasts.removeIf { toast -> now - toast.startedAt >= TOAST_DURATION_MS }
        activeCompletionToasts.take(TOAST_MAX_VISIBLE).forEachIndexed { index, toast ->
            val age = now - toast.startedAt
            val appear = easeOutBack((age / TOAST_ENTER_MS.toFloat()).coerceIn(0.0f, 1.0f))
            val exit = ((age - (TOAST_DURATION_MS - TOAST_EXIT_MS)) / TOAST_EXIT_MS.toFloat()).coerceIn(0.0f, 1.0f)
            val exitScale = 1.0f - easeInOut(exit)
            val scale = Mth.lerp(appear, TOAST_START_SCALE, 1.0f) * exitScale
            val alpha = (appear * exitScale).coerceIn(0.0f, 1.0f)
            if (alpha <= 0.01f) return@forEachIndexed

            val toastWidth = toastWidth(minecraft, toast)
            val x = (minecraft.window.guiScaledWidth - toastWidth) / 2
            val y = TOAST_TOP + activeShippingSaleToasts.size.coerceAtMost(1) * (TOAST_HEIGHT + TOAST_GAP) + index * (TOAST_HEIGHT + TOAST_GAP)
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(x + toastWidth / 2.0f, y + TOAST_HEIGHT / 2.0f, 0.0f)
            pose.scale(scale, scale, 1.0f)
            pose.translate(-(x + toastWidth / 2.0f), -(y + TOAST_HEIGHT / 2.0f), 0.0f)
            renderVanillaButtonBackground(guiGraphics, x, y, toastWidth, alpha)
            drawScaledToastText(guiGraphics, minecraft, toast.title, x + TOAST_TEXT_X, y + TOAST_TITLE_Y, TOAST_TITLE_SCALE, TOAST_TITLE_COLOR, alpha)
            drawScaledToastText(guiGraphics, minecraft, toast.missionName, x + TOAST_TEXT_X, y + TOAST_NAME_Y, TOAST_NAME_SCALE, TOAST_NAME_COLOR, alpha)
            pose.popPose()
        }
    }

    private fun toastWidth(minecraft: Minecraft, toast: ActiveCompletionToast): Int {
        val titleWidth = (minecraft.font.width(toast.title) * TOAST_TITLE_SCALE).toInt() + TOAST_TEXT_X * 2
        val missionWidth = (minecraft.font.width(toast.missionName) * TOAST_NAME_SCALE).toInt() + TOAST_TEXT_X * 2
        return maxOf(TOAST_MIN_WIDTH, titleWidth, missionWidth).coerceAtMost(minecraft.window.guiScaledWidth - TOAST_SCREEN_PADDING * 2)
    }

    private fun shippingToastWidth(minecraft: Minecraft, toast: ActiveShippingSaleToast): Int {
        val titleWidth = (minecraft.font.width("Shipping Bin") * TOAST_TITLE_SCALE).toInt() + TOAST_TEXT_X * 2
        val lineWidth = minecraft.font.width("Sold ${toast.itemCount} items for ${formatChowcoins(toast.amount)} chowcoins") + TOAST_TEXT_X * 2 + TOAST_COIN_SIZE + TOAST_COIN_GAP
        return maxOf(TOAST_MIN_WIDTH, titleWidth, lineWidth).coerceAtMost(minecraft.window.guiScaledWidth - TOAST_SCREEN_PADDING * 2)
    }

    private fun drawShippingSaleLine(guiGraphics: GuiGraphics, minecraft: Minecraft, toast: ActiveShippingSaleToast, x: Int, y: Int, alpha: Float) {
        var cursor = x
        listOf(
            "Sold " to TOAST_NAME_COLOR,
            toast.itemCount.toString() to TOAST_HIGHLIGHT_COLOR,
            " items for " to TOAST_NAME_COLOR,
            formatChowcoins(toast.amount) to TOAST_HIGHLIGHT_COLOR,
            " chowcoins" to TOAST_NAME_COLOR,
        ).forEach { (text, color) ->
            guiGraphics.drawString(minecraft.font, text, cursor, y, colorWithAlpha(color, alpha), false)
            cursor += minecraft.font.width(text)
        }
    }

    private fun renderVanillaButtonBackground(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, alpha: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        guiGraphics.blitSprite(TOAST_BUTTON_SPRITE, x, y, width, TOAST_HEIGHT)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun drawScaledToastText(guiGraphics: GuiGraphics, minecraft: Minecraft, text: String, x: Int, y: Int, scale: Float, color: Int, alpha: Float) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0.0f)
        pose.scale(scale, scale, 1.0f)
        guiGraphics.drawString(minecraft.font, text, 0, 0, colorWithAlpha(color, alpha), false)
        pose.popPose()
    }

    private fun easeOutBack(progress: Float): Float {
        val shifted = progress - 1.0f
        return 1.0f + TOAST_BACK_OVERSHOOT * shifted * shifted * shifted + (TOAST_BACK_OVERSHOOT - 1.0f) * shifted * shifted
    }

    private fun easeInOut(progress: Float): Float = progress * progress * (3.0f - 2.0f * progress)

    private fun completionTitle(scope: BattlepassMissionScope): String = when (scope) {
        BattlepassMissionScope.DAILY -> "Daily Mission Completed"
        BattlepassMissionScope.WEEKLY -> "Weekly Mission Completed"
        BattlepassMissionScope.PERMANENT -> "Mission Completed"
    }

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
    private const val HEADER_PILL_GAP = 4
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
    private const val TRACKED_CHECK_SIZE = TRACKED_MARKER_SIZE / 2
    private const val TRACKED_MARKER_GAP = 2
    private const val TRACKED_DETAIL_GAP = 8
    private const val TRACKED_DETAIL_SLIDE = 8
    private const val TRACKED_DETAIL_ANIMATION_SPEED = 0.28f
    private const val TRACKED_DETAIL_MIN_ALPHA = 0.02f
    private const val TRACKED_COMPLETED_ALPHA = 0.5f
    private const val HUD_TEXTURE_WIDTH = 128
    private const val HUD_TEXTURE_HEIGHT = 24
    private const val HUD_TEXTURE_BORDER = 4
    private const val MARKER_TEXTURE_SIZE = 16
    private const val TRACKED_TITLE_COLOR = 0xFFFFFFFF.toInt()
    private const val TRACKED_TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val TOAST_MIN_WIDTH = 160
    private const val TOAST_SCREEN_PADDING = 10
    private const val TOAST_HEIGHT = 44
    private const val TOAST_TOP = 10
    private const val TOAST_GAP = 4
    private const val TOAST_MAX_VISIBLE = 2
    private const val TOAST_TEXT_X = 10
    private const val TOAST_COIN_SIZE = 12
    private const val TOAST_COIN_GAP = 5
    private const val TOAST_TITLE_Y = 8
    private const val TOAST_NAME_Y = 21
    private const val TOAST_TITLE_SCALE = 0.7f
    private const val TOAST_NAME_SCALE = 1.15f
    private const val TOAST_DURATION_MS = 5000L
    private const val TOAST_ENTER_MS = 420L
    private const val TOAST_EXIT_MS = 360L
    private const val TOAST_START_SCALE = 0.72f
    private const val TOAST_BACK_OVERSHOOT = 1.55f
    private const val TOAST_TITLE_COLOR = 0xFFFFE7AA.toInt()
    private const val TOAST_NAME_COLOR = 0xFFFFFFFF.toInt()
    private const val TOAST_HIGHLIGHT_COLOR = 0xFFFFD35C.toInt()
}