package dev.gisketch.chowkingdom.battlepass

import com.mojang.math.Axis
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.Util
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

class BattlepassScreen : Screen(Component.translatable("screen.${ChowKingdomMod.MOD_ID}.battlepass")) {
    private enum class ViewMode { PASS_SELECTION, PASS_DETAIL }

    private enum class MissionFilter(val label: String) {
        ALL("ALL"),
        DAILY("DLY"),
        WEEKLY("WKLY"),
        PERMANENT("PERM"),
        COMPLETED("DONE");

        fun next(): MissionFilter = entries[(ordinal + 1) % entries.size]
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    private data class RewardSlot(
        val rect: Rect,
        val passId: String,
        val tier: BattlepassProgressionDefinition,
        val reward: BattlepassRewardDefinition,
        val stack: ItemStack,
        val prominent: Boolean,
        val claimed: Boolean,
        val unlocked: Boolean,
        val claimable: Boolean,
        val current: Boolean,
        val previousXp: Int,
        val otherPlayers: List<BattlepassClientState.PlayerProgress>,
    )

    private data class MissionSlot(
        val rect: Rect,
        val passId: String,
        val entry: BattlepassMissionEntry,
        val completed: Boolean,
    )

    private var selectedPassId: String? = null
    private var viewMode = ViewMode.PASS_SELECTION
    private var scroll = 0.0f
    private var targetScroll = 0.0f
    private var missionsScroll = 0.0f
    private var targetMissionsScroll = 0.0f
    private var missionsMaxScroll = 0.0f
    private var backRect = Rect(0, 0, 0, 0)
    private var claimAllRect = Rect(0, 0, 0, 0)
    private var missionsRect = Rect(0, 0, 0, 0)
    private var missionFilterRect = Rect(0, 0, 0, 0)
    private var backButton: Button? = null
    private var claimAllButton: Button? = null
    private var autoScrollKey: String? = null
    private var missionFilter = MissionFilter.ALL
    private var passRects: List<Pair<Rect, BattlepassPassDefinition>> = emptyList()
    private var rewardSlots: List<RewardSlot> = emptyList()
    private var missionSlots: List<MissionSlot> = emptyList()
    private val hoverProgressBySlot: MutableMap<String, Float> = mutableMapOf()
    private val clickProgressBySlot: MutableMap<String, Float> = mutableMapOf()

    override fun init() {
        BattlepassNetwork.requestSync()
        BattlepassPassRegistry.reload()
        selectedPassId = selectedPassId ?: passes().firstOrNull()?.id
        backButton = addRenderableWidget(
            Button.builder(Component.translatable("ui.${ChowKingdomMod.MOD_ID}.back")) {
                viewMode = ViewMode.PASS_SELECTION
                scroll = 0.0f
                targetScroll = 0.0f
            }.bounds(0, 0, BACK_BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        claimAllButton = addRenderableWidget(
            Button.builder(Component.literal("Claim All")) {
                selectedPassId?.let(BattlepassNetwork::claimAll)
            }.bounds(0, 0, CLAIM_ALL_WIDTH, BUTTON_HEIGHT).build(),
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        scroll = Mth.lerp(0.24f, scroll, targetScroll)
        missionsScroll = Mth.lerp(0.24f, missionsScroll, targetMissionsScroll)
        ensureSelectedPass()
        if (viewMode == ViewMode.PASS_SELECTION) {
            renderSelection(guiGraphics, mouseX, mouseY)
        } else {
            renderDetail(guiGraphics, mouseX, mouseY)
        }
        renderables.forEach { renderable -> renderable.render(guiGraphics, mouseX, mouseY, partialTick) }
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBlurredBackground(partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        if (super.mouseClicked(mouseX, mouseY, button)) return true

        val clickedPass = if (viewMode == ViewMode.PASS_SELECTION) passRects.firstOrNull { (rect, _) -> rect.contains(mouseX, mouseY) }?.second else null
        if (clickedPass != null) {
            selectedPassId = clickedPass.id
            viewMode = ViewMode.PASS_DETAIL
            scroll = 0.0f
            targetScroll = 0.0f
            missionsScroll = 0.0f
            targetMissionsScroll = 0.0f
            autoScrollKey = null
            return true
        }

        if (viewMode == ViewMode.PASS_DETAIL && missionFilterRect.contains(mouseX, mouseY)) {
            missionFilter = missionFilter.next()
            missionsScroll = 0.0f
            targetMissionsScroll = 0.0f
            return true
        }

        val clickedReward = rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) }
        if (clickedReward != null) {
            clickProgressBySlot[slotKey(clickedReward)] = 1.0f
        }
        if (viewMode == ViewMode.PASS_DETAIL && clickedReward?.claimable == true) {
            BattlepassNetwork.claim(selectedPassId ?: return true, clickedReward.tier.xp)
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (viewMode == ViewMode.PASS_DETAIL && missionsRect.contains(mouseX, mouseY)) {
            targetMissionsScroll = (targetMissionsScroll - (scrollY.toFloat() * MISSIONS_SCROLL_STEP)).coerceIn(0.0f, missionsMaxScroll)
            return true
        }

        if (viewMode != ViewMode.PASS_DETAIL || !hotbarRect().contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }

        targetScroll = (targetScroll - (scrollY.toFloat() * 36.0f)).coerceIn(0.0f, maxScroll())
        return true
    }

    private fun renderSelection(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        backButton?.visible = false
        claimAllButton?.visible = false
        val panel = mainRect()
        drawFrame(guiGraphics, panel, 0x22000000, 0x00000000)
        guiGraphics.drawString(font, Component.translatable("ui.${ChowKingdomMod.MOD_ID}.battlepass.select_pass"), panel.x + 28, panel.y + 24, 0xF0F4FF, true)

        val passes = passes()
        passRects = passes.mapIndexed { index, pass ->
            val rect = Rect(panel.x + 28, panel.y + 60 + index * 46, passListWidth(), 38)
            val hovered = rect.contains(mouseX.toDouble(), mouseY.toDouble())
            drawFrame(guiGraphics, rect, if (hovered) 0x663A70C4 else 0x3310141C, if (hovered) 0xFF8DB3FF.toInt() else 0x88D7DDEA.toInt())
            guiGraphics.drawString(font, pass.displayName, rect.x + 12, rect.y + 8, 0xF0F4FF, true)
            guiGraphics.drawString(font, pass.categories.joinToString(" | "), rect.x + 12, rect.y + 22, 0x9CA3AF, false)
            rect to pass
        }

        if (passes.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("ui.${ChowKingdomMod.MOD_ID}.battlepass.empty"), panel.x + 28, panel.y + 60, 0xAAB2C0, true)
        }
    }

    private fun renderDetail(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val selectedPass = passes().firstOrNull { pass -> pass.id == selectedPassId } ?: run {
            viewMode = ViewMode.PASS_SELECTION
            renderSelection(guiGraphics, mouseX, mouseY)
            return
        }
        val currentPlayerId = currentPlayerId()
        val currentXp = currentPlayerId?.let { playerId -> xpFor(playerId, selectedPass.id) } ?: 0
        val panel = mainRect()
        drawFrame(guiGraphics, panel, 0x16000000, 0x00000000)

        val titleBottom = renderPassTitle(guiGraphics, selectedPass, panel.x + 28, panel.y + 18)

        layoutBackButton(panel)

        val hotbar = hotbarRect()
        val contentTop = titleBottom + CONTENT_TOP_GAP
        val contentBottom = hotbar.y - CONTENT_BOTTOM_GAP
        autoCenterCurrentReward(selectedPass, currentXp, hotbar)
        renderMissionsBook(guiGraphics, selectedPass, panel.x + CONTENT_PADDING, contentTop, contentBottom, mouseX, mouseY)
        renderPlayerPreview(guiGraphics, contentTop, contentBottom, currentXp)
        renderRewardHotbar(guiGraphics, hotbar, selectedPass, currentXp, mouseX, mouseY)
        renderClaimAllButton(guiGraphics, mouseX, mouseY)
    }

    private fun layoutBackButton(panel: Rect) {
        backRect = Rect(panel.x + panel.width - CONTENT_PADDING - BACK_BUTTON_WIDTH, panel.y + 22, BACK_BUTTON_WIDTH, BUTTON_HEIGHT)
        backButton?.setX(backRect.x)
        backButton?.setY(backRect.y)
        backButton?.visible = true
        backButton?.active = true
    }

    private fun renderMissionsBook(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, x: Int, y: Int, contentBottom: Int, mouseX: Int, mouseY: Int) {
        val availableHeight = (contentBottom - y).coerceAtLeast(MISSIONS_BOOK_MIN_HEIGHT)
        val availableWidth = (width - CONTENT_PADDING * 3 - PLAYER_PREVIEW_MAX_WIDTH).coerceAtLeast(MISSIONS_BOOK_MIN_WIDTH)
        val scale = minOf(availableHeight / MISSIONS_BOOK_HEIGHT.toFloat(), availableWidth / MISSIONS_BOOK_WIDTH.toFloat())
        val bookWidth = (MISSIONS_BOOK_WIDTH * scale).toInt()
        val bookHeight = (MISSIONS_BOOK_HEIGHT * scale).toInt()
        guiGraphics.blit(MISSIONS_BOOK_TEXTURE, x, y, bookWidth, bookHeight, 0.0f, 0.0f, MISSIONS_BOOK_WIDTH, MISSIONS_BOOK_HEIGHT, MISSIONS_BOOK_WIDTH, MISSIONS_BOOK_HEIGHT)

        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + MISSIONS_TITLE_X * scale, y + MISSIONS_TITLE_Y * scale, 0.0f)
        pose.scale(MISSIONS_TITLE_SCALE * scale, MISSIONS_TITLE_SCALE * scale, 1.0f)
        guiGraphics.drawString(font, "Missions", 0, 0, MISSIONS_TITLE_COLOR, false)
        pose.popPose()

        renderMissionFilterButton(guiGraphics, x, y, scale, mouseX, mouseY)

        renderMissionEvents(guiGraphics, pass, x, y, scale, mouseX, mouseY)
    }

    private fun renderMissionFilterButton(guiGraphics: GuiGraphics, bookX: Int, bookY: Int, scale: Float, mouseX: Int, mouseY: Int) {
        val x = bookX + (MISSIONS_FILTER_X * scale).toInt()
        val y = bookY + (MISSIONS_FILTER_Y * scale).toInt()
        val width = (MISSIONS_FILTER_WIDTH * scale).toInt().coerceAtLeast(24)
        val height = (MISSIONS_FILTER_HEIGHT * scale).toInt().coerceAtLeast(10)
        missionFilterRect = Rect(x, y, width, height)
        val hovered = missionFilterRect.contains(mouseX.toDouble(), mouseY.toDouble())
        drawFrame(guiGraphics, missionFilterRect, if (hovered) MISSIONS_FILTER_HOVER_FILL else MISSIONS_FILTER_FILL, if (hovered) MISSIONS_FILTER_HOVER_BORDER else MISSIONS_FILTER_BORDER)
        val label = missionFilter.label
        val textX = x + (width - font.width(label)) / 2
        val textY = y + (height - font.lineHeight) / 2
        guiGraphics.drawString(font, label, textX, textY, MISSIONS_FILTER_TEXT, false)
    }

    private fun renderMissionEvents(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, bookX: Int, bookY: Int, scale: Float, mouseX: Int, mouseY: Int) {
        val rectX = bookX + (MISSIONS_CONTAINER_X * scale).toInt()
        val rectY = bookY + (MISSIONS_CONTAINER_Y * scale).toInt()
        val rectWidth = ((MISSIONS_CONTAINER_RIGHT - MISSIONS_CONTAINER_X) * scale).toInt()
        val rectHeight = ((MISSIONS_CONTAINER_BOTTOM - MISSIONS_CONTAINER_Y) * scale).toInt()
        missionsRect = Rect(rectX, rectY, rectWidth, rectHeight)
        missionsMaxScroll = maxMissionsScroll(pass, scale, rectHeight)
        targetMissionsScroll = targetMissionsScroll.coerceIn(0.0f, missionsMaxScroll)
        missionsScroll = missionsScroll.coerceIn(0.0f, missionsMaxScroll)
        missionSlots = emptyList()

        guiGraphics.enableScissor(rectX, rectY, rectX + rectWidth, rectY + rectHeight)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(rectX.toFloat(), rectY - missionsScroll, 0.0f)
        pose.scale(scale, scale, 1.0f)

        val missions = visibleMissionEntries(pass)
        if (missions.isEmpty()) {
            guiGraphics.drawString(font, "No missions", 0, 0, MISSIONS_EVENT_COLOR, false)
        } else {
            var rowY = 0
            missions.forEach { entry ->
                val completed = currentPlayerId()?.let { playerId -> BattlepassClientState.isMissionCompleted(playerId, pass.id, entry.key) } == true
                val screenRowY = rectY + (rowY * scale - missionsScroll).toInt()
                missionSlots = missionSlots + MissionSlot(Rect(rectX, screenRowY, rectWidth, (missionRowHeight(entry.event) * scale).toInt().coerceAtLeast(1)), pass.id, entry, completed)
                if (isProgressiveMission(entry.event) || BattlepassMissionService.isCappedRepeating(entry.event)) {
                    renderProgressiveMission(guiGraphics, pass.id, entry, rowY, completed)
                } else {
                    renderRepeatingMission(guiGraphics, entry, rowY, completed)
                }
                rowY += missionRowHeight(entry.event)
            }
        }

        pose.popPose()
        guiGraphics.disableScissor()

        missionSlots.firstOrNull { slot -> missionsRect.contains(mouseX.toDouble(), mouseY.toDouble()) && slot.rect.contains(mouseX.toDouble(), mouseY.toDouble()) }?.let { slot ->
            guiGraphics.renderComponentTooltip(font, tooltipFor(slot), mouseX, mouseY)
        }
    }

    private fun renderRepeatingMission(guiGraphics: GuiGraphics, entry: BattlepassMissionEntry, y: Int, completed: Boolean) {
        val event = entry.event
        val xpText = "+${event.xp}"
        val alpha = if (completed) MISSIONS_COMPLETED_ALPHA else 1.0f
        val color = missionColor(entry.scope)
        drawMissionString(guiGraphics, missionDescription(event), 0, y, color, alpha)
        drawMissionString(guiGraphics, xpText, rightAlignedMissionX(xpText), y, color, alpha)
        drawMissionSeparator(guiGraphics, y + MISSIONS_REPEATING_ROW_HEIGHT - MISSIONS_SEPARATOR_BOTTOM_GAP, color, alpha)
    }

    private fun renderProgressiveMission(guiGraphics: GuiGraphics, passId: String, entry: BattlepassMissionEntry, y: Int, completed: Boolean) {
        val event = entry.event
        val syncedProgress = currentPlayerId()?.let { playerId -> BattlepassClientState.missionProgress(playerId, passId, entry.key) ?: BattlepassClientState.missionProgress(playerId, passId, event.event) }
        val eventProgress = syncedProgress ?: event.progress
        val cappedRepeating = BattlepassMissionService.isCappedRepeating(event)
        val goalIndex = nextProgressGoalIndex(event, eventProgress)
        val goal = if (cappedRepeating) event.xpCap else event.progressGoals.getOrNull(goalIndex) ?: eventProgress.coerceAtLeast(1)
        val previousGoal = event.progressGoals.getOrNull(goalIndex - 1) ?: 0
        val xp = if (cappedRepeating) event.xp else event.progressXp.getOrNull(goalIndex) ?: event.xp
        val progress = eventProgress.coerceAtMost(goal)
        val span = (goal - previousGoal).coerceAtLeast(1)
        val localProgress = (progress - previousGoal).coerceAtLeast(0)
        val xpText = "+$xp"
        val progressText = "$progress/$goal"
        val progressTextWidth = missionStringWidth(progressText)
        val barWidth = (MISSIONS_CONTAINER_WIDTH - progressTextWidth - MISSIONS_PROGRESS_TEXT_GAP).coerceAtLeast(MISSIONS_PROGRESS_BAR_MIN_WIDTH)
        val progressWidth = (barWidth * (localProgress / span.toFloat())).toInt().coerceIn(0, barWidth)
        val alpha = if (completed) MISSIONS_COMPLETED_ALPHA else 1.0f
        val color = missionColor(entry.scope)

        drawMissionString(guiGraphics, missionDescription(event), 0, y, color, alpha)
        drawMissionString(guiGraphics, xpText, rightAlignedMissionX(xpText), y, color, alpha)
        guiGraphics.fill(0, y + MISSIONS_PROGRESS_BAR_Y, barWidth, y + MISSIONS_PROGRESS_BAR_Y + MISSIONS_PROGRESS_BAR_HEIGHT, colorWithAlpha(MISSIONS_PROGRESS_BAR_BACKGROUND, alpha * MISSIONS_PROGRESS_BAR_BACKGROUND_ALPHA))
        guiGraphics.fill(0, y + MISSIONS_PROGRESS_BAR_Y, progressWidth, y + MISSIONS_PROGRESS_BAR_Y + MISSIONS_PROGRESS_BAR_HEIGHT, colorWithAlpha(color, alpha))
        drawMissionString(guiGraphics, progressText, rightAlignedMissionX(progressText), y + MISSIONS_PROGRESS_BAR_Y - MISSIONS_PROGRESS_TEXT_Y_OFFSET, MISSIONS_EVENT_DETAIL_COLOR, alpha)
        drawMissionSeparator(guiGraphics, y + MISSIONS_PROGRESSIVE_ROW_HEIGHT - MISSIONS_SEPARATOR_BOTTOM_GAP, color, alpha)
    }

    private fun drawMissionString(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, alpha: Float = 1.0f) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0.0f)
        pose.scale(MISSIONS_EVENT_TEXT_SCALE, MISSIONS_EVENT_TEXT_SCALE, 1.0f)
        guiGraphics.drawString(font, text, 0, 0, colorWithAlpha(color, alpha), false)
        pose.popPose()
    }

    private fun drawMissionSeparator(guiGraphics: GuiGraphics, y: Int, color: Int, alpha: Float) {
        drawMissionString(guiGraphics, fullMissionSeparator(), 0, y, color, alpha * MISSIONS_SEPARATOR_ALPHA)
    }

    private fun fullMissionSeparator(): String {
        val unitWidth = missionStringWidth(MISSIONS_SEPARATOR_TEXT).coerceAtLeast(1)
        return MISSIONS_SEPARATOR_TEXT.repeat((MISSIONS_CONTAINER_WIDTH / unitWidth) + 2)
    }

    private fun rightAlignedMissionX(text: String): Int = MISSIONS_CONTAINER_WIDTH - missionStringWidth(text)

    private fun missionStringWidth(text: String): Int = (font.width(text) * MISSIONS_EVENT_TEXT_SCALE).toInt()

    private fun colorWithAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0.0f, 1.0f) * 255).toInt() shl 24) or (color and 0x00FFFFFF)

    private fun renderPlayerPreview(guiGraphics: GuiGraphics, contentTop: Int, contentBottom: Int, currentXp: Int) {
        val player = Minecraft.getInstance().player ?: return
        val previewHeight = (contentBottom - contentTop).coerceAtLeast(PLAYER_PREVIEW_MIN_HEIGHT)
        val previewWidth = (previewHeight * PLAYER_PREVIEW_ASPECT_NUMERATOR / PLAYER_PREVIEW_ASPECT_DENOMINATOR).coerceIn(PLAYER_PREVIEW_MIN_WIDTH, PLAYER_PREVIEW_MAX_WIDTH)
        val previewSize = (previewHeight * PLAYER_PREVIEW_SIZE_NUMERATOR / PLAYER_PREVIEW_SIZE_DENOMINATOR).coerceIn(PLAYER_PREVIEW_MIN_SIZE, PLAYER_PREVIEW_MAX_SIZE)
        val x1 = (width - PLAYER_PREVIEW_RIGHT_PADDING - previewWidth).coerceAtLeast(28)
        val y1 = contentTop
        val xpText = "BP XP $currentXp"
        guiGraphics.drawString(font, xpText, x1 + (previewWidth - font.width(xpText)) / 2, (y1 - PLAYER_PREVIEW_XP_GAP).coerceAtLeast(HEADER_MIN_TEXT_Y), 0x8DEBFF, true)
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            guiGraphics,
            x1,
            y1,
            x1 + previewWidth,
            y1 + previewHeight,
            previewSize,
            PLAYER_PREVIEW_Y_OFFSET,
            PLAYER_PREVIEW_ANGLE_X,
            PLAYER_PREVIEW_ANGLE_Y,
            player,
        )
    }

    private fun renderClaimAllButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val claimableCount = rewardSlots.count { slot -> slot.claimable }
        val footer = footerRect()
        drawFrame(guiGraphics, footer, 0x22000000, 0x00000000)
        claimAllRect = Rect((footer.x + footer.width - FOOTER_PADDING - CLAIM_ALL_WIDTH).coerceAtLeast(footer.x + FOOTER_PADDING), footer.y + (footer.height - BUTTON_HEIGHT) / 2, CLAIM_ALL_WIDTH, BUTTON_HEIGHT)
        val label = if (claimableCount > 0) "Claim All ($claimableCount)" else "Claim All"
        claimAllButton?.setX(claimAllRect.x)
        claimAllButton?.setY(claimAllRect.y)
        claimAllButton?.setMessage(Component.literal(label))
        claimAllButton?.visible = true
        claimAllButton?.active = claimableCount > 0
    }

    private fun renderPassTitle(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, x: Int, y: Int): Int {
        val texture = pass.titleTexture.takeIf { it.isNotBlank() }?.let { runCatching { ResourceLocation.parse(it) }.getOrNull() }
        val sourceWidth = pass.titleTextureWidth.coerceAtLeast(1)
        val sourceHeight = pass.titleTextureHeight.coerceAtLeast(1)
        if (texture == null || pass.titleTextureWidth <= 0 || pass.titleTextureHeight <= 0) {
            guiGraphics.drawString(font, pass.displayName, x, y + 10, 0xF0F4FF, true)
            return y + 30
        }

        val maxWidth = titleWidth()
        val widthByHeight = TITLE_IMAGE_MAX_HEIGHT * sourceWidth / sourceHeight
        val imageWidth = maxWidth.coerceAtMost(widthByHeight).coerceAtLeast(TITLE_IMAGE_MIN_WIDTH)
        val imageHeight = imageWidth * sourceHeight / sourceWidth
        guiGraphics.blit(texture, x, y, imageWidth, imageHeight, 0.0f, 0.0f, sourceWidth, sourceHeight, sourceWidth, sourceHeight)
        return y + imageHeight
    }

    private fun autoCenterCurrentReward(pass: BattlepassPassDefinition, currentXp: Int, hotbar: Rect) {
        val sortedTiers = pass.progression.sortedBy { tier -> tier.xp }
        if (sortedTiers.isEmpty()) return

        var offset = 12
        var selectedCenter = offset + slotSize(sortedTiers.first().rewards.firstOrNull()?.let(::isProminentReward) == true) / 2
        val currentPlayerId = currentPlayerId()

        for (tier in sortedTiers) {
            val prominent = tier.rewards.firstOrNull()?.let(::isProminentReward) == true
            val size = slotSize(prominent)
            val claimed = currentPlayerId?.let { playerId -> isClaimed(playerId, pass.id, tier.xp) } == true
            selectedCenter = offset + size / 2
            if (!claimed) break
            offset += size + SLOT_GAP
        }

        if (autoScrollKey != null) return

        val desiredScroll = (selectedCenter - hotbar.width / 2.0f).coerceIn(0.0f, maxScroll(rewardContentWidth(pass), hotbar.width))
        targetScroll = desiredScroll
        scroll = desiredScroll
        autoScrollKey = pass.id
    }

    private fun renderRewardHotbar(
        guiGraphics: GuiGraphics,
        hotbar: Rect,
        pass: BattlepassPassDefinition,
        currentXp: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val sortedTiers = pass.progression.sortedBy { tier -> tier.xp }
        val contentWidth = rewardContentWidth(pass)
        targetScroll = targetScroll.coerceIn(0.0f, maxScroll(contentWidth, hotbar.width))
        scroll = scroll.coerceIn(0.0f, maxScroll(contentWidth, hotbar.width))
        rewardSlots = emptyList()
        val animationTime = (Util.getMillis() % 100_000L).toFloat() / 50.0f
        val currentPlayerId = currentPlayerId()
        val otherPlayersByTier = BattlepassClientState.players()
            .filter { player -> player.uuid != currentPlayerId }
            .groupBy { player -> tierForPlayer(sortedTiers, player.xpByPass[pass.id] ?: 0) }

        guiGraphics.enableScissor(hotbar.x + 2, (hotbar.y - PLAYER_MARKER_SCISSOR_EXTRA).coerceAtLeast(0), hotbar.x + hotbar.width - 2, hotbar.y + hotbar.height - 2)
        var nextX = hotbar.x + 12 - scroll.toInt()
        sortedTiers.forEachIndexed { index, tier ->
            val reward = tier.rewards.firstOrNull()
            val prominent = reward?.let(::isProminentReward) == true
            val slotSize = slotSize(prominent)
            val x = nextX
            val y = hotbar.y + (hotbar.height - slotSize) / 2
            nextX += slotSize + SLOT_GAP
            val previousXp = sortedTiers.getOrNull(index - 1)?.xp ?: 0
            val claimed = currentPlayerId?.let { playerId -> isClaimed(playerId, pass.id, tier.xp) } == true
            val unlocked = currentXp >= tier.xp
            val claimable = unlocked && !claimed
            val current = !claimed && currentXp >= previousXp && !unlocked
            reward?.let {
                val slot = RewardSlot(Rect(x, y, slotSize, slotSize), pass.id, tier, it, rewardStack(it), prominent, claimed, unlocked, claimable, current, previousXp, otherPlayersByTier[tier.xp].orEmpty())
                val hovered = slot.rect.contains(mouseX.toDouble(), mouseY.toDouble())
                renderRewardSlot(guiGraphics, slot, index + 1, currentXp, hovered, animationTime)
                rewardSlots = rewardSlots + slot
            }
        }
        guiGraphics.disableScissor()

        rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX.toDouble(), mouseY.toDouble()) }?.let { slot ->
            guiGraphics.renderComponentTooltip(font, tooltipFor(slot, currentXp), mouseX, mouseY)
        }
    }

    private fun renderRewardSlot(guiGraphics: GuiGraphics, slot: RewardSlot, number: Int, currentXp: Int, hovered: Boolean, animationTime: Float) {
        val rect = slot.rect
        val slotKey = slotKey(slot)
        val hoverProgress = updateAnimation(hoverProgressBySlot, slotKey, if (hovered) 1.0f else 0.0f, HOVER_ANIMATION_SPEED)
        val clickProgress = updateAnimation(clickProgressBySlot, slotKey, 0.0f, CLICK_ANIMATION_SPEED)
        val hoverLift = -HOVER_LIFT * easeOutBack(hoverProgress)
        val clickScale = 1.0f + (Mth.sin(clickProgress * Math.PI.toFloat()) * CLICK_SCALE)
        val goalLift = if (slot.current || slot.claimable) Mth.sin(animationTime * GOAL_FLOAT_SPEED) * GOAL_FLOAT_DISTANCE else 0.0f
        val slotCenterX = rect.x + (rect.width / 2.0f)
        val slotCenterY = rect.y + (rect.height / 2.0f)
        val texture = when {
            slot.claimable -> REWARD_CLAIMABLE_TEXTURE
            slot.unlocked || slot.claimed -> REWARD_CONTAINER_TEXTURE
            else -> REWARD_LOCKED_TEXTURE
        }
        val textureSize = if (slot.claimable) scaled(CLAIMABLE_TEXTURE_SIZE, slot.rect.width) else slot.rect.width
        val textureOffset = if (slot.claimable) scaled(CLAIMABLE_TEXTURE_OFFSET, slot.rect.width) else 0
        val sourceTextureSize = if (slot.claimable) CLAIMABLE_TEXTURE_SOURCE_SIZE else CONTAINER_TEXTURE_SOURCE_SIZE

        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(slotCenterX, slotCenterY + hoverLift + goalLift, 0.0f)
        pose.scale(clickScale, clickScale, 1.0f)
        pose.translate(-slotCenterX, -slotCenterY, 0.0f)

        renderRewardContainer(guiGraphics, slot, texture, textureSize, textureOffset, sourceTextureSize, currentXp)

        renderRewardItem(guiGraphics, slot)

        val color = when {
            slot.claimed -> 0xC8FFD7
            slot.claimable || slot.current -> 0x8DEBFF
            else -> 0x858B96
        }
        guiGraphics.drawString(font, number.toString(), rect.x + 7, rect.y + 6, color, true)

        if (!slot.unlocked && !slot.current) {
            val overlaySize = scaled(LOCKED_OVERLAY_SIZE, rect.width)
            val overlayInset = (rect.width - overlaySize) / 2
            renderOverlay(guiGraphics, LOCKED_OVERLAY_TEXTURE, rect.x + overlayInset, rect.y + overlayInset, overlaySize, overlaySize, OVERLAY_TEXTURE_SIZE)
        }

        if (slot.claimed) {
            val claimedOverlaySize = scaled(CLAIMED_OVERLAY_SIZE, rect.width)
            renderOverlay(
                guiGraphics,
                CLAIMED_OVERLAY_TEXTURE,
                rect.x + rect.width - claimedOverlaySize,
                rect.y + rect.height - claimedOverlaySize,
                claimedOverlaySize,
                claimedOverlaySize,
                OVERLAY_TEXTURE_SIZE,
            )
        }

        if (slot.current) {
            renderCurrentMarker(guiGraphics, rect)
        }
        renderOtherPlayerMarkers(guiGraphics, slot)

        pose.popPose()
    }

    private fun renderCurrentMarker(guiGraphics: GuiGraphics, rect: Rect) {
        val centerX = rect.x + rect.width / 2
        val arrowX = centerX - MARKER_ARROW_SIZE / 2
        val arrowY = rect.y - MARKER_ARROW_SIZE - MARKER_GAP
        val avatarX = centerX - MARKER_AVATAR_SIZE / 2
        val avatarY = arrowY - MARKER_AVATAR_SIZE - MARKER_GAP

        Minecraft.getInstance().player?.let { player ->
            PlayerFaceRenderer.draw(guiGraphics, player.skin, avatarX, avatarY, MARKER_AVATAR_SIZE)
        }

        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate((arrowX + MARKER_ARROW_SIZE / 2.0f), (arrowY + MARKER_ARROW_SIZE / 2.0f), OVERLAY_Z)
        pose.mulPose(Axis.ZP.rotationDegrees(90.0f))
        guiGraphics.blit(
            MARKER_ARROW_TEXTURE,
            -MARKER_ARROW_SIZE / 2,
            -MARKER_ARROW_SIZE / 2,
            MARKER_ARROW_SIZE,
            MARKER_ARROW_SIZE,
            0.0f,
            0.0f,
            MARKER_ARROW_SOURCE_SIZE,
            MARKER_ARROW_SOURCE_SIZE,
            MARKER_ARROW_SOURCE_SIZE,
            MARKER_ARROW_SOURCE_SIZE,
        )
        pose.popPose()
    }

    private fun renderOtherPlayerMarkers(guiGraphics: GuiGraphics, slot: RewardSlot) {
        if (slot.otherPlayers.isEmpty()) return

        val count = slot.otherPlayers.size
        val size = (PLAYER_MARKER_BASE_SIZE - ((count - 1) / PLAYER_MARKER_MAX_COLUMNS) * PLAYER_MARKER_SHRINK_STEP).coerceAtLeast(PLAYER_MARKER_MIN_SIZE)
        val rows = (count + PLAYER_MARKER_MAX_COLUMNS - 1) / PLAYER_MARKER_MAX_COLUMNS
        val gridHeight = rows * size + (rows - 1) * PLAYER_MARKER_GAP
        val currentMarkerOffset = if (slot.current) MARKER_TOTAL_HEIGHT + PLAYER_MARKER_TOP_GAP else 0
        val baseY = slot.rect.y - gridHeight - PLAYER_MARKER_TOP_GAP - currentMarkerOffset
        val centerX = slot.rect.x + slot.rect.width / 2
        val connection = Minecraft.getInstance().connection

        slot.otherPlayers.forEachIndexed { index, player ->
            val row = index / PLAYER_MARKER_MAX_COLUMNS
            val rowStart = row * PLAYER_MARKER_MAX_COLUMNS
            val rowCount = (count - rowStart).coerceAtMost(PLAYER_MARKER_MAX_COLUMNS)
            val column = index % PLAYER_MARKER_MAX_COLUMNS
            val rowWidth = rowCount * size + (rowCount - 1) * PLAYER_MARKER_GAP
            val x = centerX - rowWidth / 2 + column * (size + PLAYER_MARKER_GAP)
            val y = baseY + row * (size + PLAYER_MARKER_GAP)
            val skin = connection?.getPlayerInfo(player.uuid)?.skin ?: DefaultPlayerSkin.get(player.uuid)
            PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size)
        }
    }

    private fun renderRewardContainer(
        guiGraphics: GuiGraphics,
        slot: RewardSlot,
        texture: ResourceLocation,
        textureSize: Int,
        textureOffset: Int,
        sourceTextureSize: Int,
        currentXp: Int,
    ) {
        val rect = slot.rect
        guiGraphics.blit(
            texture,
            rect.x - textureOffset,
            rect.y - textureOffset,
            textureSize,
            textureSize,
            0.0f,
            0.0f,
            sourceTextureSize,
            sourceTextureSize,
            sourceTextureSize,
            sourceTextureSize,
        )

        if (!slot.current) return

        val progressWidth = (rect.width * progressFor(slot, currentXp)).toInt().coerceIn(0, rect.width)
        if (progressWidth <= 0) return
        val sourceProgressWidth = (CONTAINER_TEXTURE_SOURCE_SIZE * progressFor(slot, currentXp)).toInt().coerceIn(0, CONTAINER_TEXTURE_SOURCE_SIZE)

        guiGraphics.blit(
            REWARD_CONTAINER_TEXTURE,
            rect.x,
            rect.y,
            progressWidth,
            rect.height,
            0.0f,
            0.0f,
            sourceProgressWidth,
            CONTAINER_TEXTURE_SOURCE_SIZE,
            CONTAINER_TEXTURE_SOURCE_SIZE,
            CONTAINER_TEXTURE_SOURCE_SIZE,
        )
    }

    private fun renderRewardItem(guiGraphics: GuiGraphics, slot: RewardSlot) {
        val pose = guiGraphics.pose()
        val itemSize = scaled(ITEM_SIZE, slot.rect.width)
        val itemInset = (slot.rect.width - itemSize) / 2
        val itemX = slot.rect.x + itemInset + itemSize / 2.0f
        val itemY = slot.rect.y + itemInset + itemSize / 2.0f
        val itemScale = itemSize / BASE_ITEM_RENDER_SIZE.toFloat()
        val alpha = if (!slot.unlocked && !slot.current) LOCKED_ITEM_ALPHA else 1.0f

        pose.pushPose()
        pose.translate(itemX, itemY, 0.0f)
        pose.scale(itemScale, itemScale, 1.0f)
        pose.translate(-BASE_ITEM_RENDER_SIZE / 2.0f, -BASE_ITEM_RENDER_SIZE / 2.0f, 0.0f)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        guiGraphics.renderItem(slot.stack, 0, 0)
        guiGraphics.renderItemDecorations(font, slot.stack, 0, 0, if (slot.reward.quantity > 1) slot.reward.quantity.toString() else null)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
        pose.popPose()
    }

    private fun renderOverlay(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, textureSize: Int) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0.0f, 0.0f, OVERLAY_Z)
        guiGraphics.blit(texture, x, y, width, height, 0.0f, 0.0f, textureSize, textureSize, textureSize, textureSize)
        pose.popPose()
    }

    private fun progressFor(slot: RewardSlot, currentXp: Int): Float {
        val span = (slot.tier.xp - slot.previousXp).coerceAtLeast(1)
        return ((currentXp - slot.previousXp).toFloat() / span.toFloat()).coerceIn(0.0f, 1.0f)
    }

    private fun updateAnimation(progressByKey: MutableMap<String, Float>, key: String, target: Float, speed: Float): Float {
        val current = progressByKey[key] ?: 0.0f
        val next = Mth.lerp(speed, current, target)
        if (next < 0.001f && target == 0.0f) {
            progressByKey.remove(key)
            return 0.0f
        }
        progressByKey[key] = next
        return next
    }

    private fun easeOutBack(progress: Float): Float {
        val shifted = progress - 1.0f
        return 1.0f + (BACK_EASE_C3 * shifted * shifted * shifted) + (BACK_EASE_C1 * shifted * shifted)
    }

    private fun tooltipFor(slot: RewardSlot, currentXp: Int): List<Component> {
        val remaining = (slot.tier.xp - currentXp).coerceAtLeast(0)
        val status = when {
            slot.claimed -> Component.literal("Claimed").withStyle(ChatFormatting.GREEN)
            slot.claimable -> Component.literal("Click to claim").withStyle(ChatFormatting.AQUA)
            slot.current -> Component.literal("$remaining XP to claim").withStyle(ChatFormatting.AQUA)
            else -> Component.literal("Locked - $remaining XP needed").withStyle(ChatFormatting.GRAY)
        }

        return listOf(
            Component.literal("Tier ${slot.tier.xp} XP").withStyle(ChatFormatting.GOLD),
            Component.literal("${slot.stack.hoverName.string} x${slot.reward.quantity}"),
            status,
        )
    }

    private fun tooltipFor(slot: MissionSlot): List<Component> {
        val event = slot.entry.event
        val progress = currentPlayerId()?.let { playerId -> BattlepassClientState.missionProgress(playerId, slot.passId, slot.entry.key) ?: BattlepassClientState.missionProgress(playerId, slot.passId, event.event) } ?: event.progress
        val detail = when {
            BattlepassMissionService.isCappedRepeating(event) -> Component.literal("XP cap: ${progress.coerceAtMost(event.xpCap)}/${event.xpCap} (+${event.xp} each)").withStyle(ChatFormatting.GRAY)
            BattlepassMissionService.isProgressive(event) -> Component.literal("Progress: ${progress.coerceAtMost(BattlepassMissionService.progressiveGoal(event))}/${BattlepassMissionService.progressiveGoal(event)}").withStyle(ChatFormatting.GRAY)
            else -> Component.literal("Repeating: +${event.xp} XP").withStyle(ChatFormatting.GRAY)
        }
        val status = if (slot.completed) Component.literal("Completed").withStyle(ChatFormatting.GREEN) else Component.literal("Active").withStyle(ChatFormatting.DARK_GREEN)
        return listOf(
            Component.literal(missionDescription(event)).withStyle(missionChatColor(slot.entry.scope)),
            detail,
            status,
            Component.literal(missionFooter(slot.entry.scope)).withStyle(missionChatColor(slot.entry.scope)),
        )
    }

    private fun missionColor(scope: BattlepassMissionScope): Int = when (scope) {
        BattlepassMissionScope.DAILY -> MISSIONS_DAILY_COLOR
        BattlepassMissionScope.WEEKLY -> MISSIONS_WEEKLY_COLOR
        BattlepassMissionScope.PERMANENT -> MISSIONS_PERMANENT_COLOR
    }

    private fun missionChatColor(scope: BattlepassMissionScope): ChatFormatting = when (scope) {
        BattlepassMissionScope.DAILY -> ChatFormatting.GOLD
        BattlepassMissionScope.WEEKLY -> ChatFormatting.DARK_RED
        BattlepassMissionScope.PERMANENT -> ChatFormatting.DARK_GREEN
    }

    private fun missionFooter(scope: BattlepassMissionScope): String = when (scope) {
        BattlepassMissionScope.DAILY -> "Daily mission"
        BattlepassMissionScope.WEEKLY -> "Weekly mission"
        BattlepassMissionScope.PERMANENT -> "Permanent mission"
    }

    private fun drawFrame(guiGraphics: GuiGraphics, rect: Rect, fillColor: Int, borderColor: Int) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, fillColor)
        guiGraphics.hLine(rect.x, rect.x + rect.width - 1, rect.y, borderColor)
        guiGraphics.hLine(rect.x, rect.x + rect.width - 1, rect.y + rect.height - 1, borderColor)
        guiGraphics.vLine(rect.x, rect.y, rect.y + rect.height - 1, borderColor)
        guiGraphics.vLine(rect.x + rect.width - 1, rect.y, rect.y + rect.height - 1, borderColor)
    }

    private fun rewardStack(reward: BattlepassRewardDefinition): ItemStack {
        val item = runCatching { ResourceLocation.parse(reward.item) }.getOrNull()
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER) }
            ?: Items.BARRIER
        return ItemStack(item, reward.quantity.coerceIn(1, 64))
    }

    private fun mainRect(): Rect {
        return Rect(0, 0, width, height)
    }

    private fun hotbarRect(): Rect {
        val panel = mainRect()
        return Rect(panel.x + CONTENT_PADDING, footerRect().y - ITEM_STRIP_GAP - ITEM_STRIP_HEIGHT, (panel.width - CONTENT_PADDING * 2).coerceAtLeast(160), ITEM_STRIP_HEIGHT)
    }

    private fun footerRect(): Rect {
        val panel = mainRect()
        return Rect(panel.x, panel.y + panel.height - FOOTER_HEIGHT, panel.width, FOOTER_HEIGHT)
    }

    private fun passListWidth(): Int = (width - 56).coerceAtMost(420)

    private fun titleWidth(): Int = (width - 176).coerceAtLeast(160).coerceAtMost(420)

    private fun maxScroll(): Float = maxScroll(selectedPass()?.let(::rewardContentWidth) ?: 10, hotbarRect().width)

    private fun maxScroll(contentWidth: Int, visibleWidth: Int): Float = (contentWidth - visibleWidth + 24).coerceAtLeast(0).toFloat()

    private fun maxMissionsScroll(pass: BattlepassPassDefinition, scale: Float, visibleHeight: Int): Float {
        val contentHeight = visibleMissionEntries(pass).ifEmpty { listOf(BattlepassMissionEntry(BattlepassMissionScope.PERMANENT, 0, BattlepassXpEventDefinition())) }.sumOf { entry -> missionRowHeight(entry.event) }
        return (contentHeight * scale - visibleHeight).coerceAtLeast(0.0f)
    }

    private fun visibleMissionEntries(pass: BattlepassPassDefinition): List<BattlepassMissionEntry> {
        val activeKeys = BattlepassClientState.activeMissionKeys(pass.id)
        if (activeKeys.isEmpty()) return filterMissionEntries(pass, BattlepassMissionService.permanentEntries(pass))
        val activeKeySet = activeKeys.toSet()
        return filterMissionEntries(pass, BattlepassMissionService.allEntries(pass).filter { entry -> entry.key in activeKeySet })
    }

    private fun filterMissionEntries(pass: BattlepassPassDefinition, entries: List<BattlepassMissionEntry>): List<BattlepassMissionEntry> = when (missionFilter) {
        MissionFilter.ALL -> entries
        MissionFilter.DAILY -> entries.filter { entry -> entry.scope == BattlepassMissionScope.DAILY }
        MissionFilter.WEEKLY -> entries.filter { entry -> entry.scope == BattlepassMissionScope.WEEKLY }
        MissionFilter.PERMANENT -> entries.filter { entry -> entry.scope == BattlepassMissionScope.PERMANENT }
        MissionFilter.COMPLETED -> entries.filter { entry -> currentPlayerId()?.let { playerId -> BattlepassClientState.isMissionCompleted(playerId, pass.id, entry.key) } == true }
    }

    private fun missionDescription(event: BattlepassXpEventDefinition): String = event.eventDesc.ifBlank { event.event }

    private fun missionRowHeight(event: BattlepassXpEventDefinition): Int = if (isProgressiveMission(event) || BattlepassMissionService.isCappedRepeating(event)) MISSIONS_PROGRESSIVE_ROW_HEIGHT else MISSIONS_REPEATING_ROW_HEIGHT

    private fun isProgressiveMission(event: BattlepassXpEventDefinition): Boolean = event.type.equals("progressive", ignoreCase = true)

    private fun nextProgressGoalIndex(event: BattlepassXpEventDefinition, progress: Int): Int {
        val nextIndex = event.progressGoals.indexOfFirst { goal -> progress < goal }
        return if (nextIndex >= 0) nextIndex else (event.progressGoals.size - 1).coerceAtLeast(0)
    }

    private fun rewardContentWidth(pass: BattlepassPassDefinition): Int = pass.progression.sumOf { tier ->
        val prominent = tier.rewards.firstOrNull()?.let(::isProminentReward) == true
        slotSize(prominent) + SLOT_GAP
    } + 10

    private fun slotSize(prominent: Boolean): Int = if (prominent) PROMINENT_SLOT_SIZE else SLOT_SIZE

    private fun isProminentReward(reward: BattlepassRewardDefinition): Boolean =
        reward.isProminent || reward.data["is_prominent"]?.toBooleanStrictOrNull() == true

    private fun scaled(value: Int, slotSize: Int): Int = (value * slotSize / SLOT_SIZE.toFloat()).toInt()

    private fun selectedPass(): BattlepassPassDefinition? = passes().firstOrNull { pass -> pass.id == selectedPassId }

    private fun passes(): List<BattlepassPassDefinition> = BattlepassClientState.passes().ifEmpty { BattlepassPassRegistry.all().toList() }

    private fun ensureSelectedPass() {
        val passes = passes()
        if (selectedPassId == null || passes.none { pass -> pass.id == selectedPassId }) {
            selectedPassId = passes.firstOrNull()?.id
            autoScrollKey = null
        }
    }

    private fun currentPlayerId(): UUID? = BattlepassClientState.selfId() ?: Minecraft.getInstance().player?.uuid

    private fun xpFor(playerId: UUID, passId: String): Int = BattlepassClientState.xpFor(playerId, passId) ?: BattlepassXpStore.getXp(playerId, passId)

    private fun isClaimed(playerId: UUID, passId: String, tierXp: Int): Boolean = BattlepassClientState.isClaimed(playerId, passId, tierXp) ?: BattlepassXpStore.isClaimed(playerId, passId, tierXp)

    private fun tierForPlayer(sortedTiers: List<BattlepassProgressionDefinition>, xp: Int): Int {
        sortedTiers.firstOrNull { tier -> xp < tier.xp }?.let { tier -> return tier.xp }
        return sortedTiers.lastOrNull()?.xp ?: 0
    }

    private fun slotKey(slot: RewardSlot): String = "${slot.passId}:${slot.tier.xp}"

    companion object {
        private val REWARD_CONTAINER_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/battlepass_container.png")
        private val REWARD_CLAIMABLE_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/battlepass-claimable.png.png")
        private val REWARD_LOCKED_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/battlepass-locked.png")
        private val LOCKED_OVERLAY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
        private val CLAIMED_OVERLAY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/check.png")
        private val MARKER_ARROW_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/right_arrow.png")
        private val MISSIONS_BOOK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/bp_book.png")
        private const val SLOT_SIZE = 52
        private const val PROMINENT_SLOT_SIZE = 65
        private const val SLOT_GAP = 12
        private const val CONTAINER_TEXTURE_SOURCE_SIZE = 64
        private const val CLAIMABLE_TEXTURE_SOURCE_SIZE = 72
        private const val CLAIMABLE_TEXTURE_SIZE = 60
        private const val CLAIMABLE_TEXTURE_OFFSET = 4
        private const val BASE_ITEM_RENDER_SIZE = 16
        private const val ITEM_SIZE = 32
        private const val LOCKED_OVERLAY_SIZE = 40
        private const val LOCKED_ITEM_ALPHA = 0.5f
        private const val OVERLAY_TEXTURE_SIZE = 16
        private const val CLAIMED_OVERLAY_SIZE = 24
        private const val OVERLAY_Z = 300.0f
        private const val TITLE_IMAGE_MIN_WIDTH = 120
        private const val TITLE_IMAGE_MAX_HEIGHT = 48
        private const val CONTENT_PADDING = 28
        private const val CONTENT_TOP_GAP = 12
        private const val CONTENT_BOTTOM_GAP = 12
        private const val HEADER_MIN_TEXT_Y = 8
        private const val MISSIONS_BOOK_WIDTH = 201
        private const val MISSIONS_BOOK_HEIGHT = 169
        private const val MISSIONS_BOOK_MIN_WIDTH = 96
        private const val MISSIONS_BOOK_MIN_HEIGHT = 108
        private const val MISSIONS_TITLE_X = 18
        private const val MISSIONS_TITLE_Y = 16
        private const val MISSIONS_TITLE_COLOR = 0x773A2F
        private const val MISSIONS_TITLE_SCALE = 12.0f / 9.0f
        private const val MISSIONS_FILTER_X = 132
        private const val MISSIONS_FILTER_Y = 13
        private const val MISSIONS_FILTER_WIDTH = 39
        private const val MISSIONS_FILTER_HEIGHT = 14
        private const val MISSIONS_FILTER_FILL = 0x66C69A62
        private const val MISSIONS_FILTER_HOVER_FILL = 0x88D3AA72.toInt()
        private const val MISSIONS_FILTER_BORDER = 0xAA7A5131.toInt()
        private const val MISSIONS_FILTER_HOVER_BORDER = 0xFF7A5131.toInt()
        private const val MISSIONS_FILTER_TEXT = 0x4A2819
        private const val MISSIONS_CONTAINER_X = 18
        private const val MISSIONS_CONTAINER_Y = 34
        private const val MISSIONS_CONTAINER_RIGHT = 178
        private const val MISSIONS_CONTAINER_BOTTOM = 146
        private const val MISSIONS_CONTAINER_WIDTH = MISSIONS_CONTAINER_RIGHT - MISSIONS_CONTAINER_X
        private const val MISSIONS_REPEATING_ROW_HEIGHT = 18
        private const val MISSIONS_PROGRESSIVE_ROW_HEIGHT = 36
        private const val MISSIONS_PROGRESS_BAR_Y = 14
        private const val MISSIONS_PROGRESS_BAR_MIN_WIDTH = 40
        private const val MISSIONS_PROGRESS_BAR_HEIGHT = 5
        private const val MISSIONS_PROGRESS_TEXT_GAP = 6
        private const val MISSIONS_PROGRESS_TEXT_Y_OFFSET = 1
        private const val MISSIONS_EVENT_TEXT_SCALE = 0.8f
        private const val MISSIONS_COMPLETED_ALPHA = 0.25f
        private const val MISSIONS_SEPARATOR_ALPHA = 0.25f
        private const val MISSIONS_SEPARATOR_TEXT = "- - - - - - - - -"
        private const val MISSIONS_SEPARATOR_BOTTOM_GAP = 7
        private const val MISSIONS_EVENT_COLOR = 0x773A2F
        private const val MISSIONS_EVENT_DETAIL_COLOR = 0x5E4A3D
        private const val MISSIONS_SEPARATOR_COLOR = 0x773A2F
        private const val MISSIONS_DAILY_COLOR = 0x8A5528
        private const val MISSIONS_WEEKLY_COLOR = 0x8B3F2B
        private const val MISSIONS_PERMANENT_COLOR = 0x5E5A2F
        private const val MISSIONS_PROGRESS_BAR_BACKGROUND = 0x321F18
        private const val MISSIONS_PROGRESS_BAR_BACKGROUND_ALPHA = 0.5f
        private const val MISSIONS_PROGRESS_BAR_FILL = 0xFF773A2F.toInt()
        private const val MISSIONS_SCROLL_STEP = 18.0f
        private const val ITEM_STRIP_HEIGHT = 76
        private const val ITEM_STRIP_GAP = 10
        private const val FOOTER_HEIGHT = 46
        private const val FOOTER_PADDING = 28
        private const val PLAYER_PREVIEW_MIN_WIDTH = 112
        private const val PLAYER_PREVIEW_MAX_WIDTH = 190
        private const val PLAYER_PREVIEW_MIN_HEIGHT = 130
        private const val PLAYER_PREVIEW_ASPECT_NUMERATOR = 3
        private const val PLAYER_PREVIEW_ASPECT_DENOMINATOR = 4
        private const val PLAYER_PREVIEW_RIGHT_PADDING = 44
        private const val PLAYER_PREVIEW_MIN_SIZE = 58
        private const val PLAYER_PREVIEW_MAX_SIZE = 104
        private const val PLAYER_PREVIEW_SIZE_NUMERATOR = 11
        private const val PLAYER_PREVIEW_SIZE_DENOMINATOR = 20
        private const val PLAYER_PREVIEW_Y_OFFSET = 0.0625f
        private const val PLAYER_PREVIEW_ANGLE_X = 0.15f
        private const val PLAYER_PREVIEW_ANGLE_Y = 0.0f
        private const val PLAYER_PREVIEW_XP_GAP = 14
        private const val CLAIM_ALL_WIDTH = 124
        private const val BACK_BUTTON_WIDTH = 92
        private const val BUTTON_HEIGHT = 20
        private const val MARKER_AVATAR_SIZE = 24
        private const val MARKER_ARROW_SIZE = 24
        private const val MARKER_ARROW_SOURCE_SIZE = 16
        private const val MARKER_GAP = 2
        private const val MARKER_TOTAL_HEIGHT = MARKER_AVATAR_SIZE + MARKER_ARROW_SIZE + MARKER_GAP * 2
        private const val PLAYER_MARKER_BASE_SIZE = 18
        private const val PLAYER_MARKER_MIN_SIZE = 10
        private const val PLAYER_MARKER_SHRINK_STEP = 2
        private const val PLAYER_MARKER_GAP = 2
        private const val PLAYER_MARKER_TOP_GAP = 4
        private const val PLAYER_MARKER_MAX_COLUMNS = 3
        private const val PLAYER_MARKER_SCISSOR_EXTRA = 112
        private const val HOVER_LIFT = 4.0f
        private const val HOVER_ANIMATION_SPEED = 0.22f
        private const val CLICK_ANIMATION_SPEED = 0.32f
        private const val CLICK_SCALE = 0.12f
        private const val GOAL_FLOAT_SPEED = 0.18f
        private const val GOAL_FLOAT_DISTANCE = 2.0f
        private const val BACK_EASE_C1 = 1.15f
        private const val BACK_EASE_C3 = BACK_EASE_C1 + 1.0f
    }
}