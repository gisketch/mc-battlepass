package dev.gisketch.chowkingdom.client

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionIcons
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionScope
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionService
import dev.gisketch.chowkingdom.battlepass.BattlepassPassRegistry
import dev.gisketch.chowkingdom.battlepass.BattlepassTrackedMissions
import dev.gisketch.chowkingdom.battlepass.BattlepassXpEventDefinition
import dev.gisketch.chowkingdom.shipping.ShippingBinClientState
import dev.gisketch.chowkingdom.wallets.ChowcoinClientState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import org.lwjgl.glfw.GLFW
import java.util.Locale

object ChowKingdomHud {
    private data class HudMission(val title: String, val icon: ItemStack)
    private data class ActiveCompletionToast(val title: String, val missionName: String, val startedAt: Long)
    private data class ActiveShippingSaleToast(val itemCount: Int, val amount: Long, val startedAt: Long)

    private val LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "player_hud")
    private val CHOWCOIN_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/chowcoin.png")
    private val TOAST_BUTTON_SPRITE: ResourceLocation = ResourceLocation.withDefaultNamespace("widget/button")
    private val CKDM_BOLD_FONT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    private val CKDM_BOLD_SMALL_FONT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
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
        if (minecraft.options.hideGui || minecraft.gui.debugOverlay.showDebugScreen() || (minecraft.screen != null && minecraft.screen !is ChatScreen)) return
        val detailsVisible = GLFW.glfwGetKey(minecraft.window.window, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS

        val passes = BattlepassClientState.passes().ifEmpty { BattlepassPassRegistry.all().toList() }
        val trackedMissions = BattlepassTrackedMissions.trackedMissions(passes)
        val selfId = BattlepassClientState.selfId() ?: player.uuid
        renderCompactHud(guiGraphics, minecraft, trackedMissions, selfId, detailsVisible)
    }

    private fun renderCompactHud(guiGraphics: GuiGraphics, minecraft: Minecraft, missions: List<BattlepassTrackedMissions.TrackedMission>, playerId: java.util.UUID, showGoal: Boolean) {
        val font = minecraft.font
        val screenWidth = minecraft.window.guiScaledWidth
        val maxPanelWidth = (screenWidth - HUD_PADDING * 2).coerceAtMost(COMPACT_HUD_MAX_WIDTH)
        val maxMissionTextWidth = (maxPanelWidth - COMPACT_MISSION_ICON_SIZE - COMPACT_MISSION_ICON_GAP).coerceAtLeast(COMPACT_MIN_TEXT_WIDTH)
        val hudMissions = compactHudMissions(minecraft, missions, playerId, showGoal, maxMissionTextWidth)
        val now = System.currentTimeMillis()
        val coinText = formatChowcoins(ChowcoinClientState.displayBalance(now))
        val left = HUD_PADDING

        val coinX = left
        val coinY = HUD_PADDING
        val coinIconY = coinY + (font.lineHeight - COMPACT_COIN_SIZE) / 2 + COMPACT_COIN_ICON_Y_OFFSET
        renderIcon(guiGraphics, CHOWCOIN_TEXTURE, coinX, coinIconY, COMPACT_COIN_SIZE, COMPACT_COIN_TEXTURE_SIZE)
        val coinTextX = coinX + COMPACT_COIN_SIZE + COMPACT_COIN_TEXT_GAP
        drawCkdmShadowed(guiGraphics, font, coinText, coinTextX, coinY + COMPACT_COIN_TEXT_Y, COMPACT_WHITE, COMPACT_BLACK_SHADOW, COMPACT_SHADOW_OFFSET, CKDM_BOLD_FONT)
        renderChowcoinDelta(guiGraphics, font, coinText, coinTextX, coinY + COMPACT_COIN_TEXT_Y, now)

        if (hudMissions.isEmpty()) return

        val headerY = coinY + COMPACT_COIN_SIZE + COMPACT_MISSIONS_TOP_GAP
        drawCkdmShadowed(guiGraphics, font, MISSIONS_HEADER, left, headerY, COMPACT_GOLD, COMPACT_BLACK_SHADOW, COMPACT_SHADOW_OFFSET, CKDM_BOLD_SMALL_FONT)

        hudMissions.forEachIndexed { index, mission ->
            val rowX = left
            val rowY = headerY + COMPACT_HEADER_LINE_HEIGHT + COMPACT_MISSION_HEADER_GAP + index * COMPACT_MISSION_ROW_HEIGHT
            renderScaledItem(guiGraphics, mission.icon, rowX, rowY, COMPACT_MISSION_ICON_SIZE)
            drawCkdmShadowedScaled(guiGraphics, font, mission.title, rowX + COMPACT_MISSION_ICON_SIZE + COMPACT_MISSION_ICON_GAP, rowY + COMPACT_MISSION_TEXT_Y, COMPACT_MISSION_TEXT_SCALE, COMPACT_WHITE, COMPACT_BLACK_SHADOW, COMPACT_SMALL_SHADOW_OFFSET, CKDM_BOLD_SMALL_FONT)
        }
    }

    private fun compactHudMissions(minecraft: Minecraft, missions: List<BattlepassTrackedMissions.TrackedMission>, playerId: java.util.UUID, showGoal: Boolean, maxTextWidth: Int): List<HudMission> =
        missions.mapNotNull { mission ->
            val event = mission.entry.event
            val progress = BattlepassClientState.missionProgress(playerId, mission.pass.id, mission.entry.key)
                ?: BattlepassClientState.missionProgress(playerId, mission.pass.id, event.event)
                ?: event.progress
            val goal = missionGoal(event)
            val completed = BattlepassClientState.isMissionCompleted(playerId, mission.pass.id, mission.entry.key)
            if (completed || goal <= 0) return@mapNotNull null
            val title = fitCkdmText(minecraft.font, compactMissionDescription(event, progress, goal, showGoal), (maxTextWidth / COMPACT_MISSION_TEXT_SCALE).toInt(), CKDM_BOLD_SMALL_FONT)
            HudMission(title, BattlepassMissionIcons.stack(mission.entry))
        }

    private fun compactMissionDescription(event: BattlepassXpEventDefinition, progress: Int, goal: Int, showGoal: Boolean): String {
        val displayGoal = if (showGoal) goal else (goal - progress.coerceAtLeast(0)).coerceAtLeast(0)
        return event.eventDesc.ifBlank { event.event }
            .replace("{goal}", displayGoal.toString())
            .replace("{progress}", progress.coerceAtMost(goal).toString())
    }

    private fun renderIcon(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int, textureSize: Int, alpha: Float = 1.0f) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, textureSize, textureSize, textureSize, textureSize)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderScaledItem(guiGraphics: GuiGraphics, stack: ItemStack, x: Int, y: Int, size: Int) {
        val scale = size / VANILLA_ITEM_SIZE.toFloat()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat(), 0.0f)
        guiGraphics.pose().scale(scale, scale, 1.0f)
        guiGraphics.renderItem(stack, 0, 0)
        guiGraphics.pose().popPose()
    }

    private fun missionGoal(event: BattlepassXpEventDefinition): Int = when {
        BattlepassMissionService.isCappedRepeating(event) -> event.xpCap
        BattlepassMissionService.isProgressive(event) -> BattlepassMissionService.progressiveGoal(event)
        else -> 0
    }

    private fun formatChowcoins(amount: Long): String = String.format(Locale.US, "%,d", amount)

    private fun renderChowcoinDelta(guiGraphics: GuiGraphics, font: Font, coinText: String, coinTextX: Int, coinTextY: Int, now: Long) {
        val delta = ChowcoinClientState.deltaDisplay(now) ?: return
        val sign = if (delta.amount > 0L) "+" else "-"
        val text = sign + formatChowcoins(kotlin.math.abs(delta.amount))
        val color = colorWithAlpha(if (delta.amount > 0L) CHOWCOIN_DELTA_GAIN else CHOWCOIN_DELTA_LOSS, delta.alpha)
        val shadow = colorWithAlpha(COMPACT_BLACK_SHADOW, delta.alpha)
        drawCkdmShadowed(guiGraphics, font, text, coinTextX + ckdmWidth(font, coinText, CKDM_BOLD_FONT) + CHOWCOIN_DELTA_GAP, coinTextY, color, shadow, COMPACT_SHADOW_OFFSET, CKDM_BOLD_FONT)
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
            renderIcon(guiGraphics, CHOWCOIN_TEXTURE, x + TOAST_TEXT_X, y + TOAST_NAME_Y - 2, TOAST_COIN_SIZE, COMPACT_COIN_TEXTURE_SIZE, alpha)
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

    private fun ckdmText(text: String, fontId: ResourceLocation): Component =
        Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    private fun ckdmWidth(font: Font, text: String, fontId: ResourceLocation): Int = font.width(ckdmText(text, fontId))

    private fun fitCkdmText(font: Font, text: String, maxWidth: Int, fontId: ResourceLocation): String {
        if (font.width(ckdmText(text, fontId)) <= maxWidth) return text
        val suffix = "..."
        var trimmed = text
        while (trimmed.isNotEmpty() && font.width(ckdmText(trimmed + suffix, fontId)) > maxWidth) trimmed = trimmed.dropLast(1)
        return trimmed + suffix
    }

    private fun drawCkdmShadowed(guiGraphics: GuiGraphics, font: Font, text: String, x: Int, y: Int, color: Int, shadowColor: Int, shadowOffset: Int, fontId: ResourceLocation) {
        val component = ckdmText(text, fontId)
        guiGraphics.drawString(font, component, x + shadowOffset, y + shadowOffset, shadowColor, false)
        guiGraphics.drawString(font, component, x, y, color, false)
    }

    private fun drawCkdmShadowedScaled(guiGraphics: GuiGraphics, font: Font, text: String, x: Int, y: Int, scale: Float, color: Int, shadowColor: Int, shadowOffset: Int, fontId: ResourceLocation) {
        val component = ckdmText(text, fontId)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0.0f)
        pose.scale(scale, scale, 1.0f)
        guiGraphics.drawString(font, component, shadowOffset, shadowOffset, shadowColor, false)
        guiGraphics.drawString(font, component, 0, 0, color, false)
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

    private const val MISSIONS_HEADER = "Missions"
    private const val HUD_PADDING = 8
    private const val COMPACT_HUD_MAX_WIDTH = 220
    private const val COMPACT_MIN_TEXT_WIDTH = 72
    private const val COMPACT_COIN_SIZE = 9
    private const val COMPACT_COIN_TEXTURE_SIZE = 16
    private const val COMPACT_COIN_TEXT_GAP = 4
    private const val COMPACT_COIN_TEXT_Y = 0
    private const val COMPACT_COIN_ICON_Y_OFFSET = -1
    private const val COMPACT_MISSIONS_TOP_GAP = 5
    private const val COMPACT_HEADER_LINE_HEIGHT = 8
    private const val COMPACT_MISSION_HEADER_GAP = 2
    private const val COMPACT_MISSION_ROW_HEIGHT = 11
    private const val COMPACT_MISSION_ICON_SIZE = 9
    private const val COMPACT_MISSION_ICON_GAP = 4
    private const val COMPACT_MISSION_TEXT_Y = 1
    private const val COMPACT_MISSION_TEXT_SCALE = 0.82f
    private const val COMPACT_WHITE = 0xFFFFFFFF.toInt()
    private const val COMPACT_GOLD = 0xFFFFD24A.toInt()
    private const val CHOWCOIN_DELTA_GAIN = 0xFF4BFF7A.toInt()
    private const val CHOWCOIN_DELTA_LOSS = 0xFFFF5C5C.toInt()
    private const val COMPACT_BLACK_SHADOW = 0x80000000.toInt()
    private const val COMPACT_SHADOW_OFFSET = 1
    private const val CHOWCOIN_DELTA_GAP = 7
    private const val COMPACT_SMALL_SHADOW_OFFSET = 1
    private const val VANILLA_ITEM_SIZE = 16
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
