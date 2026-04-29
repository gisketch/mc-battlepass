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
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.Util
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

class BattlepassScreen : Screen(Component.translatable("screen.${ChowKingdomMod.MOD_ID}.battlepass")) {
    private enum class ViewMode { PASS_SELECTION, PASS_DETAIL }

    private enum class MissionFilter(val label: String) {
        ALL("All"),
        WEEKLY("Weekly"),
        DAILY("Daily"),
        PERMANENT("Permanent"),
        COMPLETED("Done");

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

    private data class SelectionMissionEntry(val passId: String, val entry: BattlepassMissionEntry)

    private data class SelectionMissionBook(val rect: Rect, val scope: BattlepassMissionScope, val maxScroll: Float)

    private data class SelectionRewardTooltip(val pass: BattlepassPassDefinition, val tier: BattlepassProgressionDefinition, val reward: BattlepassRewardDefinition, val stack: ItemStack, val currentXp: Int)

    private data class ClaimAnimation(val stack: ItemStack, val startX: Float, val startY: Float, val endX: Float, val endY: Float, val startedAt: Long)

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
    private var playerPreviewRect = Rect(0, 0, 0, 0)
    private var backButton: Button? = null
    private var claimAllButton: Button? = null
    private var autoScrollKey: String? = null
    private var missionFilter = MissionFilter.ALL
    private var missionFilterClickProgress = 0.0f
    private var selectionMissionBooks: List<SelectionMissionBook> = emptyList()
    private var passRects: List<Pair<Rect, BattlepassPassDefinition>> = emptyList()
    private var rewardSlots: List<RewardSlot> = emptyList()
    private var missionSlots: List<MissionSlot> = emptyList()
    private val hoverProgressBySlot: MutableMap<String, Float> = mutableMapOf()
    private val clickProgressBySlot: MutableMap<String, Float> = mutableMapOf()
    private val missionClickProgressByKey: MutableMap<String, Float> = mutableMapOf()
    private val claimAnimations: MutableList<ClaimAnimation> = mutableListOf()
    private val selectionScrollByScope: MutableMap<BattlepassMissionScope, Float> = mutableMapOf()
    private val selectionTargetScrollByScope: MutableMap<BattlepassMissionScope, Float> = mutableMapOf()

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
                selectedPassId?.let { passId ->
                    startClaimAnimations(rewardSlots.filter { slot -> slot.claimable })
                    BattlepassNetwork.claimAll(passId)
                }
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
            playButtonClickSound()
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
            playButtonClickSound()
            missionFilter = missionFilter.next()
            missionFilterClickProgress = 1.0f
            missionsScroll = 0.0f
            targetMissionsScroll = 0.0f
            return true
        }

        val clickedMission = when {
            viewMode == ViewMode.PASS_DETAIL && missionsRect.contains(mouseX, mouseY) -> missionSlots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) }
            viewMode == ViewMode.PASS_SELECTION -> missionSlots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) }
            else -> null
        }
        if (clickedMission != null) {
            playButtonClickSound()
            missionClickProgressByKey[missionEntryKey(clickedMission.passId, clickedMission.entry)] = 1.0f
            if (viewMode == ViewMode.PASS_SELECTION && clickedMission.entry.scope == BattlepassMissionScope.DAILY) return true
            val pass = passes().firstOrNull { pass -> pass.id == clickedMission.passId } ?: selectedPass() ?: return true
            val tracked = BattlepassTrackedMissions.toggle(pass, clickedMission.entry)
            if (!tracked) Minecraft.getInstance().player?.displayClientMessage(Component.literal("Track limit reached ($MISSIONS_TRACK_LIMIT missions)"), true)
            return true
        }

        val clickedReward = rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) }
        if (clickedReward != null) {
            clickProgressBySlot[slotKey(clickedReward)] = 1.0f
        }
        if (viewMode == ViewMode.PASS_DETAIL && clickedReward?.claimable == true) {
            startClaimAnimations(listOf(clickedReward))
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

        if (viewMode == ViewMode.PASS_SELECTION) {
            val book = selectionMissionBooks.firstOrNull { book -> book.rect.contains(mouseX, mouseY) }
            if (book != null && book.maxScroll > 0.0f) {
                val current = selectionTargetScrollByScope[book.scope] ?: 0.0f
                selectionTargetScrollByScope[book.scope] = (current - (scrollY.toFloat() * MISSIONS_SCROLL_STEP)).coerceIn(0.0f, book.maxScroll)
                return true
            }
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
        drawFrame(guiGraphics, panel, 0x16000000, 0x00000000)

        val passes = passes()
        if (passes.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("ui.${ChowKingdomMod.MOD_ID}.battlepass.empty"), panel.x + 28, panel.y + 60, 0xAAB2C0, true)
            passRects = emptyList()
            return
        }

        val selectedPass = passes.firstOrNull { pass -> pass.id == selectedPassId } ?: passes.first()
        selectedPassId = selectedPass.id
        val headerBottom = renderSelectionHeader(guiGraphics, panel)
        val scale = selectionBookScale(panel, headerBottom)
        val bookWidth = (MISSIONS_BOOK_WIDTH * scale).toInt()
        val bookHeight = (MISSIONS_BOOK_HEIGHT * scale).toInt()
        val totalBooksWidth = bookWidth * 3 + SELECTION_BOOK_GAP * 2
        val bookX = panel.x + (panel.width - totalBooksWidth) / 2
        val bookY = headerBottom + SELECTION_HEADER_BOOK_GAP
        val allEntries = passes.flatMap { pass -> activeMissionEntries(pass).map { entry -> SelectionMissionEntry(pass.id, entry) } }
        missionSlots = emptyList()
        selectionMissionBooks = emptyList()

        renderSelectionMissionBook(guiGraphics, BattlepassMissionScope.DAILY, "Daily", allEntries.filter { mission -> mission.entry.scope == BattlepassMissionScope.DAILY }, bookX, bookY, scale)
        renderSelectionMissionBook(guiGraphics, BattlepassMissionScope.WEEKLY, "Weekly", allEntries.filter { mission -> mission.entry.scope == BattlepassMissionScope.WEEKLY }, bookX + bookWidth + SELECTION_BOOK_GAP, bookY, scale)
        renderSelectionMissionBook(guiGraphics, BattlepassMissionScope.PERMANENT, "Chowkingdom", allEntries.filter { mission -> mission.entry.scope == BattlepassMissionScope.PERMANENT }, bookX + (bookWidth + SELECTION_BOOK_GAP) * 2, bookY, scale)

        renderSelectionPassButtons(guiGraphics, passes, selectedPass, bookY + bookHeight + SELECTION_BOOK_BUTTON_GAP, mouseX, mouseY)
        renderMissionSlotTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun renderSelectionHeader(guiGraphics: GuiGraphics, panel: Rect): Int {
        val imageWidth = (panel.width - CONTENT_PADDING * 2).coerceAtMost(SELECTION_TITLE_MAX_WIDTH).coerceAtLeast(SELECTION_TITLE_MIN_WIDTH)
        val imageHeight = imageWidth * PASS_TITLE_TEXTURE_HEIGHT / PASS_TITLE_TEXTURE_WIDTH
        val x = panel.x + (panel.width - imageWidth) / 2
        val y = panel.y + SELECTION_TITLE_TOP
        guiGraphics.blit(PASS_TITLE_TEXTURE, x, y, imageWidth, imageHeight, 0.0f, 0.0f, PASS_TITLE_TEXTURE_WIDTH, PASS_TITLE_TEXTURE_HEIGHT, PASS_TITLE_TEXTURE_WIDTH, PASS_TITLE_TEXTURE_HEIGHT)
        return y + imageHeight
    }

    private fun renderSelectionMissionBook(guiGraphics: GuiGraphics, scope: BattlepassMissionScope, title: String, entries: List<SelectionMissionEntry>, x: Int, y: Int, scale: Float) {
        val bookWidth = (MISSIONS_BOOK_WIDTH * scale).toInt()
        val bookHeight = (MISSIONS_BOOK_HEIGHT * scale).toInt()
        guiGraphics.blit(MISSIONS_BOOK_TEXTURE, x, y, bookWidth, bookHeight, 0.0f, 0.0f, MISSIONS_BOOK_WIDTH, MISSIONS_BOOK_HEIGHT, MISSIONS_BOOK_WIDTH, MISSIONS_BOOK_HEIGHT)

        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + MISSIONS_TITLE_X * scale, y + MISSIONS_TITLE_Y * scale, 0.0f)
        pose.scale(MISSIONS_TITLE_SCALE * scale, MISSIONS_TITLE_SCALE * scale, 1.0f)
        guiGraphics.drawString(font, title, 0, 0, MISSIONS_TITLE_COLOR, false)
        pose.popPose()

        val rectX = x + (MISSIONS_CONTAINER_X * scale).toInt()
        val rectY = y + (MISSIONS_CONTAINER_Y * scale).toInt()
        val rectWidth = ((MISSIONS_CONTAINER_RIGHT - MISSIONS_CONTAINER_X) * scale).toInt()
        val rectHeight = ((MISSIONS_CONTAINER_BOTTOM - MISSIONS_CONTAINER_Y) * scale).toInt()
        val playerId = currentPlayerId()
        val sortedEntries = entries
            .map { mission -> mission to (playerId?.let { id -> BattlepassClientState.isMissionCompleted(id, mission.passId, mission.entry.key) } == true) }
            .sortedWith(compareBy<Pair<SelectionMissionEntry, Boolean>> { (_, completed) -> completed }.thenByDescending { (mission, _) -> selectionMissionCloseness(mission) })
        val contentHeight = sortedEntries.sumOf { (mission, _) -> missionRowHeight(mission.entry.event) }
        val maxScroll = (contentHeight * scale - rectHeight).coerceAtLeast(0.0f)
        val targetScroll = (selectionTargetScrollByScope[scope] ?: 0.0f).coerceIn(0.0f, maxScroll)
        val currentScroll = Mth.lerp(0.24f, selectionScrollByScope[scope] ?: 0.0f, targetScroll).coerceIn(0.0f, maxScroll)
        selectionTargetScrollByScope[scope] = targetScroll
        selectionScrollByScope[scope] = currentScroll
        selectionMissionBooks = selectionMissionBooks + SelectionMissionBook(Rect(rectX, rectY, rectWidth, rectHeight), scope, maxScroll)

        guiGraphics.enableScissor(rectX, rectY, rectX + rectWidth, rectY + rectHeight)
        pose.pushPose()
        pose.translate(rectX.toFloat(), rectY - currentScroll, 0.0f)
        pose.scale(scale, scale, 1.0f)
        if (sortedEntries.isEmpty()) {
            guiGraphics.drawString(font, "No missions", 0, 0, MISSIONS_EVENT_COLOR, false)
        } else {
            var rowY = 0
            sortedEntries.forEach { (mission, completed) ->
                val entry = mission.entry
                val rowHeight = missionRowHeight(entry.event)
                val screenRowY = rectY + (rowY * scale - currentScroll).toInt()
                if (screenRowY + (rowHeight * scale).toInt() > rectY && screenRowY < rectY + rectHeight) {
                    missionSlots = missionSlots + MissionSlot(Rect(rectX, screenRowY, rectWidth, (rowHeight * scale).toInt().coerceAtLeast(1)), mission.passId, entry, completed)
                    val clickProgress = updateAnimation(missionClickProgressByKey, missionEntryKey(mission.passId, entry), 0.0f, CLICK_ANIMATION_SPEED)
                    val rowScale = 1.0f + (Mth.sin(clickProgress * Math.PI.toFloat()) * MISSIONS_ROW_PRESS_SCALE)
                    pose.pushPose()
                    pose.translate(MISSIONS_CONTENT_WIDTH / 2.0f, rowY + rowHeight / 2.0f, 0.0f)
                    pose.scale(rowScale, rowScale, 1.0f)
                    pose.translate(-MISSIONS_CONTENT_WIDTH / 2.0f, -(rowY + rowHeight / 2.0f), 0.0f)
                    if (isProgressiveMission(entry.event) || BattlepassMissionService.isCappedRepeating(entry.event)) {
                        renderProgressiveMission(guiGraphics, mission.passId, entry, rowY, completed)
                    } else {
                        renderRepeatingMission(guiGraphics, mission.passId, entry, rowY, completed)
                    }
                    pose.popPose()
                }
                rowY += rowHeight
            }
        }
        pose.popPose()
        guiGraphics.disableScissor()
        renderMissionsScrollbar(guiGraphics, rectX, rectY, rectWidth, rectHeight, currentScroll, maxScroll)
    }

    private fun selectionMissionCloseness(mission: SelectionMissionEntry): Float {
        val playerId = currentPlayerId() ?: return 0.0f
        val event = mission.entry.event
        val progress = BattlepassClientState.missionProgress(playerId, mission.passId, mission.entry.key)
            ?: BattlepassClientState.missionProgress(playerId, mission.passId, event.event)
            ?: event.progress
        val nextGoal = when {
            BattlepassMissionService.isCappedRepeating(event) -> event.xpCap
            BattlepassMissionService.isProgressive(event) -> event.progressGoals.firstOrNull { goal -> progress < goal } ?: BattlepassMissionService.progressiveGoal(event)
            else -> return 0.0f
        }
        return progress.coerceAtMost(nextGoal) / nextGoal.coerceAtLeast(1).toFloat()
    }

    private fun renderSelectionPassButtons(guiGraphics: GuiGraphics, passes: List<BattlepassPassDefinition>, selectedPass: BattlepassPassDefinition, y: Int, mouseX: Int, mouseY: Int) {
        guiGraphics.drawString(font, "Select Battlepass", (width - font.width("Select Battlepass")) / 2, y, SELECTION_PASS_TEXT_COLOR, false)
        val gapTotal = SELECTION_PASS_CARD_GAP * (passes.size - 1).coerceAtLeast(0)
        val cardWidth = ((width - CONTENT_PADDING * 2 - gapTotal) / passes.size.coerceAtLeast(1)).coerceIn(SELECTION_PASS_CARD_MIN_WIDTH, SELECTION_PASS_CARD_MAX_WIDTH)
        val totalWidth = cardWidth * passes.size + gapTotal
        val cardY = y + font.lineHeight + SELECTION_PASS_TITLE_GAP
        val cardHeight = (height - cardY - SELECTION_PASS_BOTTOM_PADDING).coerceAtLeast(SELECTION_PASS_CARD_HEIGHT)
        var x = (width - totalWidth) / 2
        val currentPlayerId = currentPlayerId()
        var hoveredReward: SelectionRewardTooltip? = null
        passRects = passes.map { pass ->
            val rect = Rect(x, cardY, cardWidth, cardHeight)
            val hovered = rect.contains(mouseX.toDouble(), mouseY.toDouble())
            val currentXp = currentPlayerId?.let { playerId -> xpFor(playerId, pass.id) } ?: 0
            renderSelectionPassCard(guiGraphics, pass, rect, hovered)
            if (hovered) renderSelectionReticle(guiGraphics, rect)
            if (hovered) hoveredReward = selectionRewardTooltip(pass, currentXp)
            x += cardWidth + SELECTION_PASS_CARD_GAP
            rect to pass
        }
        hoveredReward?.let { tooltip -> renderSelectionRewardTooltip(guiGraphics, tooltip, mouseX, mouseY) }
    }

    private fun renderSelectionPassCard(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, rect: Rect, hovered: Boolean) {
        val titlePadding = SELECTION_PASS_CARD_PADDING + SELECTION_PASS_TITLE_RETICLE_PADDING
        val pose = guiGraphics.pose()
        pose.pushPose()
        if (hovered) {
            pose.translate(rect.x + rect.width / 2.0f, rect.y + rect.height / 2.0f, 0.0f)
            pose.scale(SELECTION_PASS_TITLE_HOVER_SCALE, SELECTION_PASS_TITLE_HOVER_SCALE, 1.0f)
            pose.translate(-(rect.x + rect.width / 2.0f), -(rect.y + rect.height / 2.0f), 0.0f)
        }
        renderSelectionPassTitle(guiGraphics, pass, rect.x + titlePadding, rect.y + titlePadding, rect.width - titlePadding * 2, rect.height - titlePadding * 2)
        pose.popPose()
    }

    private fun selectionRewardTooltip(pass: BattlepassPassDefinition, currentXp: Int): SelectionRewardTooltip? {
        val tier = pass.progression.sortedBy { progression -> progression.xp }
            .firstOrNull { progression -> currentXp < progression.xp || currentPlayerId()?.let { playerId -> isClaimed(playerId, pass.id, progression.xp) } != true }
        val reward = tier?.rewards?.firstOrNull()
        return if (tier != null && reward != null) SelectionRewardTooltip(pass, tier, reward, rewardStack(reward), currentXp) else null
    }

    private fun renderSelectionRewardTooltip(guiGraphics: GuiGraphics, tooltip: SelectionRewardTooltip, mouseX: Int, mouseY: Int) {
        val itemName = rewardName(tooltip.reward, tooltip.stack)
        val quantity = if (tooltip.reward.quantity > 1) " x${tooltip.reward.quantity}" else ""
        val status = if (tooltip.currentXp >= tooltip.tier.xp) "Ready to claim" else "${tooltip.tier.xp - tooltip.currentXp} XP needed"
        val lines = listOf("Next Reward", "${itemName}$quantity", "${tooltip.pass.displayName} ${tooltip.tier.xp} XP", status)
        val tooltipWidth = (lines.maxOf { line -> font.width(line) } + TOOLTIP_PADDING * 2 + SELECTION_TOOLTIP_ITEM_SIZE + SELECTION_TOOLTIP_ITEM_GAP).coerceAtLeast(SELECTION_TOOLTIP_MIN_WIDTH)
        val tooltipHeight = TOOLTIP_PADDING * 2 + maxOf(SELECTION_TOOLTIP_ITEM_SIZE, lines.size * TOOLTIP_LINE_HEIGHT)
        val x = (mouseX + TOOLTIP_MOUSE_GAP).coerceAtMost(width - tooltipWidth - TOOLTIP_SCREEN_GAP).coerceAtLeast(TOOLTIP_SCREEN_GAP)
        val y = (mouseY + TOOLTIP_MOUSE_GAP).coerceAtMost(height - tooltipHeight - TOOLTIP_SCREEN_GAP).coerceAtLeast(TOOLTIP_SCREEN_GAP)
        renderNineSlice(guiGraphics, TOOLTIP_BACKGROUND_TEXTURE, x, y, tooltipWidth, tooltipHeight, TOOLTIP_BACKGROUND_ALPHA)

        val iconX = x + TOOLTIP_PADDING
        val iconY = y + (tooltipHeight - BASE_ITEM_RENDER_SIZE) / 2
        if (isChowcoinReward(tooltip.reward)) {
            renderChowcoinIcon(guiGraphics, iconX, iconY, BASE_ITEM_RENDER_SIZE)
            renderRewardAmount(guiGraphics, tooltip.reward.quantity, iconX, iconY)
        } else {
            guiGraphics.renderItem(tooltip.stack, iconX, iconY)
            guiGraphics.renderItemDecorations(font, tooltip.stack, iconX, iconY, if (tooltip.reward.quantity > 1) tooltip.reward.quantity.toString() else null)
        }
        var textY = y + TOOLTIP_PADDING
        val textX = x + TOOLTIP_PADDING + SELECTION_TOOLTIP_ITEM_SIZE + SELECTION_TOOLTIP_ITEM_GAP
        lines.forEachIndexed { index, line ->
            guiGraphics.drawString(font, line, textX, textY, if (index == 0) TOOLTIP_PRIMARY_TEXT else TOOLTIP_SECONDARY_TEXT, index == 0)
            textY += TOOLTIP_LINE_HEIGHT
        }
    }

    private fun renderSelectionPassTitle(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, x: Int, y: Int, maxWidth: Int, maxHeight: Int) {
        val texture = pass.titleTexture.takeIf { it.isNotBlank() }?.let { runCatching { ResourceLocation.parse(it) }.getOrNull() }
        if (texture == null || pass.titleTextureWidth <= 0 || pass.titleTextureHeight <= 0) {
            guiGraphics.drawString(font, pass.displayName, x, y + (maxHeight - font.lineHeight) / 2, SELECTION_PASS_BUTTON_COLOR, false)
            return
        }
        val imageWidth = minOf(maxWidth, maxHeight * pass.titleTextureWidth / pass.titleTextureHeight)
        val imageHeight = imageWidth * pass.titleTextureHeight / pass.titleTextureWidth
        guiGraphics.blit(texture, x + (maxWidth - imageWidth) / 2, y + (maxHeight - imageHeight) / 2, imageWidth, imageHeight, 0.0f, 0.0f, pass.titleTextureWidth, pass.titleTextureHeight, pass.titleTextureWidth, pass.titleTextureHeight)
    }

    private fun renderSelectionReticle(guiGraphics: GuiGraphics, rect: Rect) {
        renderReticleCorner(guiGraphics, rect.x + rect.width - SELECTION_RETICLE_SIZE, rect.y, 0.0f)
        renderReticleCorner(guiGraphics, rect.x, rect.y, -90.0f)
        renderReticleCorner(guiGraphics, rect.x + rect.width - SELECTION_RETICLE_SIZE, rect.y + rect.height - SELECTION_RETICLE_SIZE, 90.0f)
        renderReticleCorner(guiGraphics, rect.x, rect.y + rect.height - SELECTION_RETICLE_SIZE, 180.0f)
    }

    private fun renderReticleCorner(guiGraphics: GuiGraphics, x: Int, y: Int, rotation: Float) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + SELECTION_RETICLE_SIZE / 2.0f, y + SELECTION_RETICLE_SIZE / 2.0f, 0.0f)
        pose.mulPose(Axis.ZP.rotationDegrees(rotation))
        guiGraphics.blit(CORNER_RETICLE_TEXTURE, -SELECTION_RETICLE_SIZE / 2, -SELECTION_RETICLE_SIZE / 2, SELECTION_RETICLE_SIZE, SELECTION_RETICLE_SIZE, 0.0f, 0.0f, SELECTION_RETICLE_SOURCE_SIZE, SELECTION_RETICLE_SOURCE_SIZE, SELECTION_RETICLE_SOURCE_SIZE, SELECTION_RETICLE_SOURCE_SIZE)
        pose.popPose()
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
        BattlepassTrackedMissions.sync(passes(), removeCompleted = true)
        drawFrame(guiGraphics, panel, 0x16000000, 0x00000000)

        val titleBottom = renderPassTitle(guiGraphics, selectedPass, panel.x + 28, panel.y + 18)

        layoutBackButton(panel)

        val hotbar = hotbarRect()
        val contentTop = titleBottom + CONTENT_TOP_GAP
        val contentBottom = hotbar.y - CONTENT_BOTTOM_GAP
        autoCenterCurrentReward(selectedPass, currentXp, hotbar)
        renderMissionsBook(guiGraphics, selectedPass, panel.x + CONTENT_PADDING, contentTop, contentBottom, mouseX, mouseY)
        renderPlayerPreview(guiGraphics, selectedPass, contentTop, contentBottom, currentXp)
        renderRewardHotbar(guiGraphics, hotbar, selectedPass, currentXp, mouseX, mouseY)
        renderClaimAllButton(guiGraphics, mouseX, mouseY)
        renderClaimAnimations(guiGraphics)
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
        val label = missionFilter.label
        val y = bookY + (MISSIONS_FILTER_Y * scale).toInt()
        val textWidth = font.width(label)
        val width = ((font.width(label) + MISSIONS_FILTER_PADDING * 2) * scale).toInt().coerceAtLeast(24)
        val height = (MISSIONS_FILTER_HEIGHT * scale).toInt().coerceAtLeast(14)
        val x = bookX + ((MISSIONS_FILTER_RIGHT_X - MISSIONS_FILTER_RIGHT_PADDING) * scale).toInt() - width
        missionFilterRect = Rect(x, y, width, height)
        val hovered = missionFilterRect.contains(mouseX.toDouble(), mouseY.toDouble())
        missionFilterClickProgress = Mth.lerp(CLICK_ANIMATION_SPEED, missionFilterClickProgress, 0.0f).let { value -> if (value < 0.001f) 0.0f else value }
        val pressScale = 1.0f + (Mth.sin(missionFilterClickProgress * Math.PI.toFloat()) * MISSIONS_FILTER_PRESS_SCALE)
        val alpha = if (hovered) 1.0f else MISSIONS_FILTER_IDLE_ALPHA
        val textX = x + width - textWidth - (MISSIONS_FILTER_PADDING * scale)
        val textY = y + (height - font.lineHeight) / 2.0f
        val centerX = x + width / 2.0f
        val centerY = y + height / 2.0f

        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(centerX, centerY, 0.0f)
        pose.scale(pressScale, pressScale, 1.0f)
        pose.translate(-centerX, -centerY, 0.0f)
        guiGraphics.drawString(font, label, textX.toInt(), textY.toInt(), colorWithAlpha(MISSIONS_FILTER_TEXT, alpha), false)
        drawDottedUnderline(guiGraphics, textX.toInt(), (textY + font.lineHeight + MISSIONS_FILTER_UNDERLINE_GAP * scale).toInt(), textWidth, colorWithAlpha(MISSIONS_FILTER_TEXT, alpha))
        pose.popPose()

        if (hovered) {
            val trackedCount = BattlepassTrackedMissions.trackedMissions(passes()).size
            val trackedText = if (trackedCount >= MISSIONS_TRACK_LIMIT) "Tracked full $MISSIONS_TRACK_LIMIT/$MISSIONS_TRACK_LIMIT" else "Tracked $trackedCount/$MISSIONS_TRACK_LIMIT"
            guiGraphics.renderComponentTooltip(font, listOf(Component.literal(trackedText)), mouseX, mouseY)
        }
    }

    private fun drawDottedUnderline(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, color: Int) {
        var dotX = x
        while (dotX < x + width) {
            guiGraphics.hLine(dotX, (dotX + MISSIONS_FILTER_DOT_WIDTH).coerceAtMost(x + width), y, color)
            dotX += MISSIONS_FILTER_DOT_WIDTH + MISSIONS_FILTER_DOT_GAP
        }
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

        val playerId = currentPlayerId()
        val missions = visibleMissionEntries(pass)
            .map { entry -> entry to (playerId?.let { id -> BattlepassClientState.isMissionCompleted(id, pass.id, entry.key) } == true) }
            .sortedBy { (_, completed) -> completed }
        if (missions.isEmpty()) {
            guiGraphics.drawString(font, "No missions", 0, 0, MISSIONS_EVENT_COLOR, false)
        } else {
            var rowY = 0
            missions.forEach { (entry, completed) ->
                val screenRowY = rectY + (rowY * scale - missionsScroll).toInt()
                missionSlots = missionSlots + MissionSlot(Rect(rectX, screenRowY, rectWidth, (missionRowHeight(entry.event) * scale).toInt().coerceAtLeast(1)), pass.id, entry, completed)
                val clickProgress = updateAnimation(missionClickProgressByKey, missionEntryKey(pass.id, entry), 0.0f, CLICK_ANIMATION_SPEED)
                val rowScale = 1.0f + (Mth.sin(clickProgress * Math.PI.toFloat()) * MISSIONS_ROW_PRESS_SCALE)
                val rowHeight = missionRowHeight(entry.event)
                pose.pushPose()
                pose.translate(MISSIONS_CONTENT_WIDTH / 2.0f, rowY + rowHeight / 2.0f, 0.0f)
                pose.scale(rowScale, rowScale, 1.0f)
                pose.translate(-MISSIONS_CONTENT_WIDTH / 2.0f, -(rowY + rowHeight / 2.0f), 0.0f)
                if (isProgressiveMission(entry.event) || BattlepassMissionService.isCappedRepeating(entry.event)) {
                    renderProgressiveMission(guiGraphics, pass.id, entry, rowY, completed)
                } else {
                    renderRepeatingMission(guiGraphics, pass.id, entry, rowY, completed)
                }
                pose.popPose()
                rowY += missionRowHeight(entry.event)
            }
        }

        pose.popPose()
        guiGraphics.disableScissor()

        renderMissionsScrollbar(guiGraphics, rectX, rectY, rectWidth, rectHeight)

        renderMissionSlotTooltip(guiGraphics, mouseX, mouseY, requireMissionsRect = true)
    }

    private fun renderMissionSlotTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, requireMissionsRect: Boolean = false) {
        val hoveredSlot = missionSlots.firstOrNull { slot ->
            (!requireMissionsRect || missionsRect.contains(mouseX.toDouble(), mouseY.toDouble())) && slot.rect.contains(mouseX.toDouble(), mouseY.toDouble())
        } ?: return
        renderMissionTooltip(guiGraphics, hoveredSlot, mouseX, mouseY)
    }

    private fun renderMissionsScrollbar(guiGraphics: GuiGraphics, rectX: Int, rectY: Int, rectWidth: Int, rectHeight: Int, scrollValue: Float = missionsScroll, maxScrollValue: Float = missionsMaxScroll) {
        if (maxScrollValue <= 0.0f) return

        val trackX = rectX + rectWidth - MISSIONS_SCROLLBAR_RIGHT_INSET
        val trackY = rectY
        val trackHeight = rectHeight
        val contentHeight = rectHeight + maxScrollValue
        val thumbHeight = (trackHeight * (rectHeight / contentHeight)).toInt().coerceIn(MISSIONS_SCROLLBAR_MIN_THUMB_HEIGHT, trackHeight)
        val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(1)
        val thumbY = trackY + (thumbTravel * (scrollValue / maxScrollValue)).toInt().coerceIn(0, thumbTravel)

        guiGraphics.fill(trackX, trackY, trackX + MISSIONS_SCROLLBAR_WIDTH, trackY + trackHeight, MISSIONS_SCROLLBAR_TRACK)
        guiGraphics.fill(trackX, thumbY, trackX + MISSIONS_SCROLLBAR_WIDTH, thumbY + thumbHeight, MISSIONS_SCROLLBAR_THUMB)
    }

    private fun renderRepeatingMission(guiGraphics: GuiGraphics, passId: String, entry: BattlepassMissionEntry, y: Int, completed: Boolean) {
        val event = entry.event
        val xpText = "+${event.xp}"
        val alpha = if (completed) MISSIONS_COMPLETED_ALPHA else 1.0f
        val color = missionColor(entry.scope)
        drawMissionTitle(guiGraphics, passId, entry, y, color, alpha, completed)
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
        val barWidth = (MISSIONS_CONTENT_WIDTH - progressTextWidth - MISSIONS_PROGRESS_TEXT_GAP).coerceAtLeast(MISSIONS_PROGRESS_BAR_MIN_WIDTH)
        val progressWidth = (barWidth * (localProgress / span.toFloat())).toInt().coerceIn(0, barWidth)
        val alpha = if (completed) MISSIONS_COMPLETED_ALPHA else 1.0f
        val color = missionColor(entry.scope)

        drawMissionTitle(guiGraphics, passId, entry, y, color, alpha, completed)
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

    private fun drawMissionTitle(guiGraphics: GuiGraphics, passId: String, entry: BattlepassMissionEntry, y: Int, color: Int, alpha: Float, completed: Boolean) {
        val tracked = BattlepassTrackedMissions.isTracked(passId, entry.key)
        var textX = 0
        if (completed) {
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
            guiGraphics.blit(CHECK_TEXTURE, textX, y, MISSIONS_STAR_SIZE, MISSIONS_STAR_SIZE, 0.0f, 0.0f, STAR_TEXTURE_SIZE, STAR_TEXTURE_SIZE, STAR_TEXTURE_SIZE, STAR_TEXTURE_SIZE)
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
            textX += MISSIONS_STAR_SIZE + MISSIONS_STAR_GAP
        }
        if (tracked) {
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
            guiGraphics.blit(STAR_TEXTURE, textX, y, MISSIONS_STAR_SIZE, MISSIONS_STAR_SIZE, 0.0f, 0.0f, STAR_TEXTURE_SIZE, STAR_TEXTURE_SIZE, STAR_TEXTURE_SIZE, STAR_TEXTURE_SIZE)
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
            textX += MISSIONS_STAR_SIZE + MISSIONS_STAR_GAP
        }
        drawMissionString(guiGraphics, missionDescription(passId, entry), textX, y, color, alpha)
    }

    private fun drawMissionSeparator(guiGraphics: GuiGraphics, y: Int, color: Int, alpha: Float) {
        drawMissionString(guiGraphics, fullMissionSeparator(), 0, y, color, alpha * MISSIONS_SEPARATOR_ALPHA)
    }

    private fun fullMissionSeparator(): String {
        val unitWidth = missionStringWidth(MISSIONS_SEPARATOR_TEXT).coerceAtLeast(1)
        return MISSIONS_SEPARATOR_TEXT.repeat((MISSIONS_CONTENT_WIDTH / unitWidth) + 2)
    }

    private fun rightAlignedMissionX(text: String): Int = MISSIONS_CONTENT_WIDTH - missionStringWidth(text)

    private fun missionStringWidth(text: String): Int = (font.width(text) * MISSIONS_EVENT_TEXT_SCALE).toInt()

    private fun colorWithAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0.0f, 1.0f) * 255).toInt() shl 24) or (color and 0x00FFFFFF)

    private fun renderPlayerPreview(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, contentTop: Int, contentBottom: Int, currentXp: Int) {
        val player = Minecraft.getInstance().player ?: return
        val previewHeight = (contentBottom - contentTop).coerceAtLeast(PLAYER_PREVIEW_MIN_HEIGHT)
        val previewWidth = (previewHeight * PLAYER_PREVIEW_ASPECT_NUMERATOR / PLAYER_PREVIEW_ASPECT_DENOMINATOR).coerceIn(PLAYER_PREVIEW_MIN_WIDTH, PLAYER_PREVIEW_MAX_WIDTH)
        val previewSize = (previewHeight * PLAYER_PREVIEW_SIZE_NUMERATOR / PLAYER_PREVIEW_SIZE_DENOMINATOR).coerceIn(PLAYER_PREVIEW_MIN_SIZE, PLAYER_PREVIEW_MAX_SIZE)
        val x1 = (width - PLAYER_PREVIEW_RIGHT_PADDING - previewWidth).coerceAtLeast(28)
        val y1 = contentTop
        playerPreviewRect = Rect(x1, y1, previewWidth, previewHeight)
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
        renderPlayerXpProgress(guiGraphics, pass, currentXp, x1, y1 + PLAYER_XP_PILL_Y_OFFSET, previewWidth)
    }

    private fun renderPlayerXpProgress(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, currentXp: Int, x: Int, y: Int, width: Int) {
        val tiers = pass.progression.sortedBy { tier -> tier.xp }
        val nextTier = tiers.firstOrNull { tier -> currentXp < tier.xp }
        val previousTierXp = tiers.lastOrNull { tier -> tier.xp <= currentXp }?.xp ?: 0
        val targetXp = nextTier?.xp ?: tiers.lastOrNull()?.xp ?: currentXp
        val remainingXp = (targetXp - currentXp).coerceAtLeast(0)
        val span = (targetXp - previousTierXp).coerceAtLeast(1)
        val progress = if (nextTier == null) 1.0f else ((currentXp - previousTierXp).toFloat() / span).coerceIn(0.0f, 1.0f)
        val fillWidth = (width * progress).toInt().coerceIn(0, width)
        val text = if (nextTier == null) "Max XP $currentXp" else "$remainingXp XP to $targetXp"

        renderNineSlice(guiGraphics, TOOLTIP_BACKGROUND_TEXTURE, x, y, width, PLAYER_XP_PILL_HEIGHT, 1.0f)
        if (fillWidth > 0) {
            guiGraphics.enableScissor(x, y, x + fillWidth, y + PLAYER_XP_PILL_HEIGHT)
            renderNineSlice(guiGraphics, GREEN_BORDER_MASK_TEXTURE, x, y, width, PLAYER_XP_PILL_HEIGHT, 1.0f)
            guiGraphics.disableScissor()
        }
        guiGraphics.drawString(font, text, x + (width - font.width(text)) / 2, y + (PLAYER_XP_PILL_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF.toInt(), true)
    }

    private fun startClaimAnimations(slots: List<RewardSlot>) {
        if (slots.isEmpty()) return
        val targetX = playerPreviewRect.x + playerPreviewRect.width * CLAIM_ANIMATION_TARGET_X_FACTOR
        val targetY = playerPreviewRect.y + playerPreviewRect.height * CLAIM_ANIMATION_TARGET_Y_FACTOR
        val now = Util.getMillis()
        slots.forEach { slot ->
            claimAnimations += ClaimAnimation(
                slot.stack.copy(),
                slot.rect.x + slot.rect.width / 2.0f,
                slot.rect.y + slot.rect.height / 2.0f,
                targetX,
                targetY,
                now,
            )
        }
        playClaimSound()
    }

    private fun renderClaimAnimations(guiGraphics: GuiGraphics) {
        val now = Util.getMillis()
        claimAnimations.removeIf { animation -> now - animation.startedAt >= CLAIM_ANIMATION_DURATION_MS }
        claimAnimations.forEach { animation ->
            val progress = ((now - animation.startedAt) / CLAIM_ANIMATION_DURATION_MS.toFloat()).coerceIn(0.0f, 1.0f)
            val eased = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress)
            val lift = Mth.sin(progress * Math.PI.toFloat()) * CLAIM_ANIMATION_LIFT
            val itemX = Mth.lerp(eased, animation.startX, animation.endX)
            val itemY = Mth.lerp(eased, animation.startY, animation.endY) - lift
            val scale = Mth.lerp(eased, CLAIM_ANIMATION_START_SCALE, CLAIM_ANIMATION_END_SCALE)
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(itemX, itemY, CLAIM_ANIMATION_Z)
            pose.scale(scale, scale, 1.0f)
            guiGraphics.renderItem(animation.stack, -BASE_ITEM_RENDER_SIZE / 2, -BASE_ITEM_RENDER_SIZE / 2)
            pose.popPose()
        }
    }

    private fun playClaimSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, CLAIM_SOUND_PITCH, CLAIM_SOUND_VOLUME))
    }

    private fun playButtonClickSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), BUTTON_CLICK_SOUND_PITCH, BUTTON_CLICK_SOUND_VOLUME))
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
        if (isChowcoinReward(slot.reward)) {
            renderChowcoinIcon(guiGraphics, 0, 0, BASE_ITEM_RENDER_SIZE)
            renderRewardAmount(guiGraphics, slot.reward.quantity, 0, 0)
        } else {
            guiGraphics.renderItem(slot.stack, 0, 0)
            guiGraphics.renderItemDecorations(font, slot.stack, 0, 0, if (slot.reward.quantity > 1) slot.reward.quantity.toString() else null)
        }
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
        pose.popPose()
    }

    private fun renderChowcoinIcon(guiGraphics: GuiGraphics, x: Int, y: Int, size: Int) {
        guiGraphics.blit(COINS_TEXTURE, x, y, size, size, 0.0f, 0.0f, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE, COINS_TEXTURE_SIZE)
    }

    private fun renderRewardAmount(guiGraphics: GuiGraphics, quantity: Int, x: Int, y: Int) {
        if (quantity <= 1) return
        val text = compactQuantity(quantity)
        guiGraphics.drawString(font, text, x + BASE_ITEM_RENDER_SIZE + 1 - font.width(text), y + 9, 0xFFFFFFFF.toInt(), true)
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
            Component.literal("${rewardName(slot.reward, slot.stack)} x${slot.reward.quantity}"),
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
        val status = if (slot.completed) Component.literal("Completed") else Component.literal("Active")
        return listOf(
            detail,
            status,
        )
    }

    private fun renderMissionTooltip(guiGraphics: GuiGraphics, slot: MissionSlot, mouseX: Int, mouseY: Int) {
        val lines = tooltipFor(slot)
        val title = missionDescription(slot.passId, slot.entry)
        val missionType = missionTypeLabel(slot.entry.scope)
        val passLine = if (viewMode == ViewMode.PASS_SELECTION) passes().firstOrNull { pass -> pass.id == slot.passId }?.displayName?.let { name -> "Pass: $name" } else null
        val completedPlayers = BattlepassClientState.players().filter { player -> player.completedMissionKeysByPass[slot.passId]?.contains(slot.entry.key) == true }
        val avatarCount = completedPlayers.size.coerceAtMost(TOOLTIP_MAX_COMPLETED_AVATARS)
        val avatarWidth = if (avatarCount > 0) avatarCount * TOOLTIP_AVATAR_SIZE + (avatarCount - 1) * TOOLTIP_AVATAR_GAP else 0
        val plusWidth = if (completedPlayers.size > avatarCount) font.width("+${completedPlayers.size - avatarCount}") + TOOLTIP_AVATAR_GAP else 0
        val headerWidth = maxOf(font.width(title), font.width(missionType), passLine?.let(font::width) ?: 0)
        val lineWidth = maxOf(lines.maxOfOrNull { line -> font.width(line) } ?: 0, headerWidth)
        val completedWidth = maxOf(font.width(TOOLTIP_COMPLETED_LABEL), avatarWidth + plusWidth, font.width(TOOLTIP_COMPLETED_EMPTY))
        val tooltipWidth = (maxOf(lineWidth, completedWidth) + TOOLTIP_PADDING * 2).coerceAtLeast(TOOLTIP_MIN_WIDTH)
        val completedHeight = if (completedPlayers.isEmpty()) font.lineHeight else TOOLTIP_AVATAR_SIZE
        val headerLines = 2 + if (passLine == null) 0 else 1
        val tooltipHeight = TOOLTIP_PADDING * 2 + (lines.size + headerLines) * TOOLTIP_LINE_HEIGHT + TOOLTIP_SECTION_GAP + font.lineHeight + TOOLTIP_SECTION_GAP + completedHeight
        val x = (mouseX + TOOLTIP_MOUSE_GAP).coerceAtMost(width - tooltipWidth - TOOLTIP_SCREEN_GAP).coerceAtLeast(TOOLTIP_SCREEN_GAP)
        val y = (mouseY + TOOLTIP_MOUSE_GAP).coerceAtMost(height - tooltipHeight - TOOLTIP_SCREEN_GAP).coerceAtLeast(TOOLTIP_SCREEN_GAP)

        renderNineSlice(guiGraphics, TOOLTIP_BACKGROUND_TEXTURE, x, y, tooltipWidth, tooltipHeight, TOOLTIP_BACKGROUND_ALPHA)
        var textY = y + TOOLTIP_PADDING
        guiGraphics.drawString(font, title, x + TOOLTIP_PADDING, textY, TOOLTIP_PRIMARY_TEXT, true)
        textY += TOOLTIP_LINE_HEIGHT
        guiGraphics.drawString(font, missionType, x + TOOLTIP_PADDING, textY, TOOLTIP_PRIMARY_TEXT, true)
        textY += TOOLTIP_LINE_HEIGHT
        if (passLine != null) {
            guiGraphics.drawString(font, passLine, x + TOOLTIP_PADDING, textY, TOOLTIP_SECONDARY_TEXT, false)
            textY += TOOLTIP_LINE_HEIGHT
        }
        lines.forEach { line ->
            guiGraphics.drawString(font, line, x + TOOLTIP_PADDING, textY, TOOLTIP_SECONDARY_TEXT, false)
            textY += TOOLTIP_LINE_HEIGHT
        }
        textY += TOOLTIP_SECTION_GAP
        guiGraphics.drawString(font, TOOLTIP_COMPLETED_LABEL, x + TOOLTIP_PADDING, textY, TOOLTIP_PRIMARY_TEXT, false)
        textY += font.lineHeight + TOOLTIP_SECTION_GAP
        if (completedPlayers.isEmpty()) {
            guiGraphics.drawString(font, Component.literal(TOOLTIP_COMPLETED_EMPTY).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), x + TOOLTIP_PADDING, textY, TOOLTIP_SECONDARY_TEXT, false)
        } else {
            val connection = Minecraft.getInstance().connection
            completedPlayers.take(TOOLTIP_MAX_COMPLETED_AVATARS).forEachIndexed { index, player ->
                val skin = connection?.getPlayerInfo(player.uuid)?.skin ?: DefaultPlayerSkin.get(player.uuid)
                PlayerFaceRenderer.draw(guiGraphics, skin, x + TOOLTIP_PADDING + index * (TOOLTIP_AVATAR_SIZE + TOOLTIP_AVATAR_GAP), textY, TOOLTIP_AVATAR_SIZE)
            }
            if (completedPlayers.size > TOOLTIP_MAX_COMPLETED_AVATARS) {
                guiGraphics.drawString(font, "+${completedPlayers.size - TOOLTIP_MAX_COMPLETED_AVATARS}", x + TOOLTIP_PADDING + avatarWidth + TOOLTIP_AVATAR_GAP, textY + (TOOLTIP_AVATAR_SIZE - font.lineHeight) / 2, 0xFFFFFFFF.toInt(), false)
            }
        }
    }

    private fun missionTypeLabel(scope: BattlepassMissionScope): String = when (scope) {
        BattlepassMissionScope.DAILY -> "Daily Mission"
        BattlepassMissionScope.WEEKLY -> "Weekly Mission"
        BattlepassMissionScope.PERMANENT -> "Chowkingdom Mission"
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, alpha: Float) {
        val border = TOOLTIP_NINE_SLICE_BORDER
        val centerWidth = (width - border * 2).coerceAtLeast(0)
        val centerHeight = (height - border * 2).coerceAtLeast(0)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        guiGraphics.blit(texture, x, y, border, border, 0.0f, 0.0f, border, border, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
        guiGraphics.blit(texture, x + width - border, y, border, border, (TOOLTIP_TEXTURE_WIDTH - border).toFloat(), 0.0f, border, border, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
        guiGraphics.blit(texture, x, y + height - border, border, border, 0.0f, (TOOLTIP_TEXTURE_HEIGHT - border).toFloat(), border, border, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
        guiGraphics.blit(texture, x + width - border, y + height - border, border, border, (TOOLTIP_TEXTURE_WIDTH - border).toFloat(), (TOOLTIP_TEXTURE_HEIGHT - border).toFloat(), border, border, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
        if (centerWidth > 0) {
            guiGraphics.blit(texture, x + border, y, centerWidth, border, border.toFloat(), 0.0f, TOOLTIP_TEXTURE_WIDTH - border * 2, border, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
            guiGraphics.blit(texture, x + border, y + height - border, centerWidth, border, border.toFloat(), (TOOLTIP_TEXTURE_HEIGHT - border).toFloat(), TOOLTIP_TEXTURE_WIDTH - border * 2, border, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
        }
        if (centerHeight > 0) {
            guiGraphics.blit(texture, x, y + border, border, centerHeight, 0.0f, border.toFloat(), border, TOOLTIP_TEXTURE_HEIGHT - border * 2, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
            guiGraphics.blit(texture, x + width - border, y + border, border, centerHeight, (TOOLTIP_TEXTURE_WIDTH - border).toFloat(), border.toFloat(), border, TOOLTIP_TEXTURE_HEIGHT - border * 2, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
        }
        if (centerWidth > 0 && centerHeight > 0) {
            guiGraphics.blit(texture, x + border, y + border, centerWidth, centerHeight, border.toFloat(), border.toFloat(), TOOLTIP_TEXTURE_WIDTH - border * 2, TOOLTIP_TEXTURE_HEIGHT - border * 2, TOOLTIP_TEXTURE_WIDTH, TOOLTIP_TEXTURE_HEIGHT)
        }
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
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
        if (isChowcoinReward(reward)) return ItemStack(Items.GOLD_NUGGET, reward.quantity.coerceIn(1, 64))
        val item = runCatching { ResourceLocation.parse(reward.item) }.getOrNull()
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER) }
            ?: Items.BARRIER
        return ItemStack(item, reward.quantity.coerceIn(1, 64))
    }

    private fun rewardName(reward: BattlepassRewardDefinition, stack: ItemStack): String = if (isChowcoinReward(reward)) "Chowcoins" else stack.hoverName.string

    private fun compactQuantity(quantity: Int): String = when {
        quantity >= 1_000_000 -> "${quantity / 1_000_000}M"
        quantity >= 1_000 -> "${quantity / 1_000}K"
        else -> quantity.toString()
    }

    private fun isChowcoinReward(reward: BattlepassRewardDefinition): Boolean {
        if (reward.type.equals("chowcoin", ignoreCase = true) || reward.type.equals("chowcoins", ignoreCase = true)) return true
        return reward.type.equals("currency", ignoreCase = true) && reward.data["currency"]?.equals("chowcoin", ignoreCase = true) == true
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

    private fun selectionBookScale(panel: Rect, headerBottom: Int): Float {
        val availableWidth = panel.width - CONTENT_PADDING * 2 - SELECTION_BOOK_GAP * 2
        val availableHeight = panel.height - headerBottom - SELECTION_HEADER_BOOK_GAP - SELECTION_BOOK_BUTTON_GAP - SELECTION_PASS_BUTTON_HEIGHT - CONTENT_BOTTOM_GAP
        return minOf(
            (availableWidth / (MISSIONS_BOOK_WIDTH * 3.0f)).coerceAtLeast(SELECTION_BOOK_MIN_SCALE),
            (availableHeight / MISSIONS_BOOK_HEIGHT.toFloat()).coerceAtLeast(SELECTION_BOOK_MIN_SCALE),
        )
    }

    private fun maxScroll(): Float = maxScroll(selectedPass()?.let(::rewardContentWidth) ?: 10, hotbarRect().width)

    private fun maxScroll(contentWidth: Int, visibleWidth: Int): Float = (contentWidth - visibleWidth + 24).coerceAtLeast(0).toFloat()

    private fun maxMissionsScroll(pass: BattlepassPassDefinition, scale: Float, visibleHeight: Int): Float {
        val contentHeight = visibleMissionEntries(pass).ifEmpty { listOf(BattlepassMissionEntry(BattlepassMissionScope.PERMANENT, 0, BattlepassXpEventDefinition())) }.sumOf { entry -> missionRowHeight(entry.event) }
        return (contentHeight * scale - visibleHeight).coerceAtLeast(0.0f)
    }

    private fun visibleMissionEntries(pass: BattlepassPassDefinition): List<BattlepassMissionEntry> {
        return filterMissionEntries(pass, activeMissionEntries(pass))
    }

    private fun activeMissionEntries(pass: BattlepassPassDefinition): List<BattlepassMissionEntry> {
        val activeKeys = BattlepassClientState.activeMissionKeys(pass.id)
        if (activeKeys.isEmpty()) return BattlepassMissionService.permanentEntries(pass)
        val activeKeySet = activeKeys.toSet()
        return BattlepassMissionService.allEntries(pass).filter { entry -> entry.key in activeKeySet }
    }

    private fun filterMissionEntries(pass: BattlepassPassDefinition, entries: List<BattlepassMissionEntry>): List<BattlepassMissionEntry> = when (missionFilter) {
        MissionFilter.ALL -> entries
        MissionFilter.DAILY -> entries.filter { entry -> entry.scope == BattlepassMissionScope.DAILY }
        MissionFilter.WEEKLY -> entries.filter { entry -> entry.scope == BattlepassMissionScope.WEEKLY }
        MissionFilter.PERMANENT -> entries.filter { entry -> entry.scope == BattlepassMissionScope.PERMANENT }
        MissionFilter.COMPLETED -> entries.filter { entry -> currentPlayerId()?.let { playerId -> BattlepassClientState.isMissionCompleted(playerId, pass.id, entry.key) } == true }
    }

    private fun missionDescription(passId: String, entry: BattlepassMissionEntry): String = BattlepassMissionService.missionDescription(entry.event, missionProgress(passId, entry))

    private fun missionProgress(passId: String, entry: BattlepassMissionEntry): Int {
        val event = entry.event
        return currentPlayerId()?.let { playerId -> BattlepassClientState.missionProgress(playerId, passId, entry.key) ?: BattlepassClientState.missionProgress(playerId, passId, event.event) } ?: event.progress
    }

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

    private fun missionEntryKey(passId: String, entry: BattlepassMissionEntry): String = "$passId:${entry.key}"

        private val REWARD_CONTAINER_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/battlepass_container.png")
        private val REWARD_CLAIMABLE_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/battlepass-claimable.png")
        private val REWARD_LOCKED_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/battlepass-locked.png")
        private val LOCKED_OVERLAY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
        private val CLAIMED_OVERLAY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/accept.png")
        private val MARKER_ARROW_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/right_arrow.png")
        private val MISSIONS_BOOK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/bp_book.png")
        private val PASS_TITLE_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/pass_title.png")
        private val TOOLTIP_BACKGROUND_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/hud-container.png")
        private val GREEN_BORDER_MASK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/green-border-mask.png")
        private val COINS_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/coins.png")
        private val CORNER_RETICLE_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/corner_reticle.png")
        private val STAR_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/star.png")
        private val CHECK_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/accept.png")
        private val SLOT_SIZE = 52
        private val PROMINENT_SLOT_SIZE = 65
        private val SLOT_GAP = 12
        private val CONTAINER_TEXTURE_SOURCE_SIZE = 64
        private val CLAIMABLE_TEXTURE_SOURCE_SIZE = 72
        private val CLAIMABLE_TEXTURE_SIZE = 60
        private val CLAIMABLE_TEXTURE_OFFSET = 4
        private val BASE_ITEM_RENDER_SIZE = 16
        private val COINS_TEXTURE_SIZE = 16
        private val ITEM_SIZE = 32
        private val LOCKED_OVERLAY_SIZE = 40
        private val LOCKED_ITEM_ALPHA = 0.5f
        private val OVERLAY_TEXTURE_SIZE = 16
        private val CLAIMED_OVERLAY_SIZE = 24
        private val OVERLAY_Z = 300.0f
        private val TITLE_IMAGE_MIN_WIDTH = 120
        private val TITLE_IMAGE_MAX_HEIGHT = 48
        private val PASS_TITLE_TEXTURE_WIDTH = 1024
        private val PASS_TITLE_TEXTURE_HEIGHT = 213
        private val SELECTION_TITLE_TOP = 14
        private val SELECTION_TITLE_MIN_WIDTH = 90
        private val SELECTION_TITLE_MAX_WIDTH = 180
        private val SELECTION_HEADER_BOOK_GAP = 8
        private val SELECTION_BOOK_GAP = 16
        private val SELECTION_BOOK_BUTTON_GAP = 12
        private val SELECTION_BOOK_MIN_SCALE = 0.42f
        private val SELECTION_PASS_BUTTON_HEIGHT = 122
        private val SELECTION_PASS_BUTTON_PADDING = 5
        private val SELECTION_PASS_BUTTON_COLOR = 0x773A2F
        private val SELECTION_PASS_TEXT_COLOR = 0xFFFFFFFF.toInt()
        private val SELECTION_PASS_SEPARATOR_COLOR = 0x773A2F
        private val SELECTION_PASS_BUTTON_IDLE_ALPHA = 0.55f
        private val SELECTION_PASS_SEPARATOR = " | "
        private val SELECTION_PASS_CARD_MIN_WIDTH = 132
        private val SELECTION_PASS_CARD_MAX_WIDTH = 320
        private val SELECTION_PASS_CARD_HEIGHT = 54
        private val SELECTION_PASS_BOTTOM_PADDING = 14
        private val SELECTION_PASS_CARD_GAP = 16
        private val SELECTION_PASS_CARD_PADDING = 8
        private val SELECTION_PASS_TITLE_HOVER_SCALE = 1.06f
        private val SELECTION_PASS_TITLE_HEIGHT = 26
        private val SELECTION_PASS_TITLE_GAP = 5
        private val SELECTION_PASS_REWARD_GAP = 9
        private val SELECTION_PASS_REWARD_SIZE = 34
        private val SELECTION_RETICLE_SIZE = 16
        private val SELECTION_RETICLE_SOURCE_SIZE = 32
        private val SELECTION_PASS_TITLE_RETICLE_PADDING = 8
        private val CONTENT_PADDING = 28
        private val CONTENT_TOP_GAP = 12
        private val CONTENT_BOTTOM_GAP = 12
        private val HEADER_MIN_TEXT_Y = 8
        private val MISSIONS_BOOK_WIDTH = 201
        private val MISSIONS_BOOK_HEIGHT = 169
        private val MISSIONS_BOOK_MIN_WIDTH = 96
        private val MISSIONS_BOOK_MIN_HEIGHT = 108
        private val MISSIONS_TITLE_X = 18
        private val MISSIONS_TITLE_Y = 16
        private val MISSIONS_TITLE_COLOR = 0x773A2F
        private val MISSIONS_TITLE_SCALE = 1.0f
        private val MISSIONS_FILTER_X = 122
        private val MISSIONS_FILTER_RIGHT_X = 178
        private val MISSIONS_FILTER_RIGHT_PADDING = 12
        private val MISSIONS_FILTER_Y = 13
        private val MISSIONS_FILTER_WIDTH = 39
        private val MISSIONS_FILTER_HEIGHT = 18
        private val MISSIONS_FILTER_PADDING = 3
        private val MISSIONS_FILTER_IDLE_ALPHA = 0.5f
        private val MISSIONS_FILTER_PRESS_SCALE = 0.08f
        private val MISSIONS_FILTER_UNDERLINE_OFFSET = 1
        private val MISSIONS_FILTER_UNDERLINE_GAP = 3
        private val MISSIONS_FILTER_DOT_WIDTH = 1
        private val MISSIONS_FILTER_DOT_GAP = 4
        private val MISSIONS_FILTER_FILL = 0x66C69A62
        private val MISSIONS_FILTER_HOVER_FILL = 0x88D3AA72.toInt()
        private val MISSIONS_FILTER_BORDER = 0xAA7A5131.toInt()
        private val MISSIONS_FILTER_HOVER_BORDER = 0xFF7A5131.toInt()
        private val MISSIONS_FILTER_TEXT = 0x773A2F
        private val MISSIONS_CONTAINER_X = 18
        private val MISSIONS_CONTAINER_Y = 34
        private val MISSIONS_CONTAINER_RIGHT = 178
        private val MISSIONS_CONTAINER_BOTTOM = 146
        private val MISSIONS_CONTAINER_WIDTH = MISSIONS_CONTAINER_RIGHT - MISSIONS_CONTAINER_X
        private val MISSIONS_CONTENT_WIDTH = MISSIONS_CONTAINER_WIDTH - 9
        private val MISSIONS_REPEATING_ROW_HEIGHT = 13
        private val MISSIONS_PROGRESSIVE_ROW_HEIGHT = 23
        private val MISSIONS_PROGRESS_BAR_Y = 10
        private val MISSIONS_PROGRESS_BAR_MIN_WIDTH = 40
        private val MISSIONS_PROGRESS_BAR_HEIGHT = 3
        private val MISSIONS_PROGRESS_TEXT_GAP = 6
        private val MISSIONS_PROGRESS_TEXT_Y_OFFSET = 1
        private val MISSIONS_STAR_SIZE = 8
        private val MISSIONS_STAR_GAP = 2
        private val STAR_TEXTURE_SIZE = 16
        private val MISSIONS_EVENT_TEXT_SCALE = 0.68f
        private val MISSIONS_COMPLETED_ALPHA = 0.25f
        private val MISSIONS_SEPARATOR_ALPHA = 0.25f
        private val MISSIONS_SEPARATOR_TEXT = "- - - - - - - - -"
        private val MISSIONS_SEPARATOR_BOTTOM_GAP = 7
        private val MISSIONS_EVENT_COLOR = 0x773A2F
        private val MISSIONS_EVENT_DETAIL_COLOR = 0x5E4A3D
        private val MISSIONS_SEPARATOR_COLOR = 0x773A2F
        private val MISSIONS_DAILY_COLOR = 0x9B652C
        private val MISSIONS_WEEKLY_COLOR = 0x7D2F27
        private val MISSIONS_PERMANENT_COLOR = 0x5E5A2F
        private val MISSIONS_PROGRESS_BAR_BACKGROUND = 0x321F18
        private val MISSIONS_PROGRESS_BAR_BACKGROUND_ALPHA = 0.5f
        private val MISSIONS_PROGRESS_BAR_FILL = 0xFF773A2F.toInt()
        private val MISSIONS_SCROLLBAR_WIDTH = 2
        private val MISSIONS_SCROLLBAR_RIGHT_INSET = 3
        private val MISSIONS_SCROLLBAR_MIN_THUMB_HEIGHT = 12
        private val MISSIONS_SCROLLBAR_TRACK = 0x44321F18
        private val MISSIONS_SCROLLBAR_THUMB = 0xAA7A5131.toInt()
        private val MISSIONS_SCROLL_STEP = 18.0f
        private val MISSIONS_ROW_PRESS_SCALE = 0.035f
        private val MISSIONS_TRACK_LIMIT = 7
        private val TOOLTIP_TEXTURE_WIDTH = 128
        private val TOOLTIP_TEXTURE_HEIGHT = 24
        private val TOOLTIP_NINE_SLICE_BORDER = 4
        private val TOOLTIP_BACKGROUND_ALPHA = 0.94f
        private val TOOLTIP_PADDING = 8
        private val TOOLTIP_LINE_HEIGHT = 11
        private val TOOLTIP_SECTION_GAP = 4
        private val TOOLTIP_MOUSE_GAP = 12
        private val TOOLTIP_SCREEN_GAP = 4
        private val TOOLTIP_MIN_WIDTH = 132
        private val TOOLTIP_AVATAR_SIZE = 12
        private val TOOLTIP_AVATAR_GAP = 3
        private val TOOLTIP_MAX_COMPLETED_AVATARS = 8
        private val TOOLTIP_COMPLETED_LABEL = "Completed:"
        private val TOOLTIP_COMPLETED_EMPTY = "No one completed yet"
        private val TOOLTIP_PRIMARY_TEXT = 0xFFFFFFFF.toInt()
        private val TOOLTIP_SECONDARY_TEXT = 0xFFB8C0CC.toInt()
        private val ITEM_STRIP_HEIGHT = 76
        private val ITEM_STRIP_GAP = 10
        private val FOOTER_HEIGHT = 46
        private val FOOTER_PADDING = 28
        private val PLAYER_PREVIEW_MIN_WIDTH = 112
        private val PLAYER_PREVIEW_MAX_WIDTH = 190
        private val PLAYER_PREVIEW_MIN_HEIGHT = 130
        private val PLAYER_PREVIEW_ASPECT_NUMERATOR = 3
        private val PLAYER_PREVIEW_ASPECT_DENOMINATOR = 4
        private val PLAYER_PREVIEW_RIGHT_PADDING = 44
        private val PLAYER_PREVIEW_MIN_SIZE = 58
        private val PLAYER_PREVIEW_MAX_SIZE = 104
        private val PLAYER_PREVIEW_SIZE_NUMERATOR = 11
        private val PLAYER_PREVIEW_SIZE_DENOMINATOR = 20
        private val CLAIM_ANIMATION_DURATION_MS = 720L
        private val CLAIM_ANIMATION_TARGET_X_FACTOR = 0.5f
        private val CLAIM_ANIMATION_TARGET_Y_FACTOR = 0.58f
        private val CLAIM_ANIMATION_START_SCALE = 1.35f
        private val CLAIM_ANIMATION_END_SCALE = 0.62f
        private val CLAIM_ANIMATION_LIFT = 28.0f
        private val CLAIM_ANIMATION_Z = 420.0f
        private val CLAIM_SOUND_PITCH = 1.2f
        private val CLAIM_SOUND_VOLUME = 0.7f
        private val BUTTON_CLICK_SOUND_PITCH = 1.0f
        private val BUTTON_CLICK_SOUND_VOLUME = 0.6f
        private val PLAYER_PREVIEW_Y_OFFSET = 0.0625f
        private val PLAYER_PREVIEW_ANGLE_X = 0.15f
        private val PLAYER_PREVIEW_ANGLE_Y = 0.0f
        private val PLAYER_XP_PILL_HEIGHT = 18
        private val PLAYER_XP_PILL_Y_OFFSET = 16
        private val SELECTION_TOOLTIP_MIN_WIDTH = 148
        private val SELECTION_TOOLTIP_ITEM_SIZE = 16
        private val SELECTION_TOOLTIP_ITEM_GAP = 8
        private val CLAIM_ALL_WIDTH = 124
        private val BACK_BUTTON_WIDTH = 92
        private val BUTTON_HEIGHT = 20
        private val MARKER_AVATAR_SIZE = 24
        private val MARKER_ARROW_SIZE = 24
        private val MARKER_ARROW_SOURCE_SIZE = 16
        private val MARKER_GAP = 2
        private val MARKER_TOTAL_HEIGHT = MARKER_AVATAR_SIZE + MARKER_ARROW_SIZE + MARKER_GAP * 2
        private val PLAYER_MARKER_BASE_SIZE = 18
        private val PLAYER_MARKER_MIN_SIZE = 10
        private val PLAYER_MARKER_SHRINK_STEP = 2
        private val PLAYER_MARKER_GAP = 2
        private val PLAYER_MARKER_TOP_GAP = 4
        private val PLAYER_MARKER_MAX_COLUMNS = 3
        private val PLAYER_MARKER_SCISSOR_EXTRA = 112
        private val HOVER_LIFT = 4.0f
        private val HOVER_ANIMATION_SPEED = 0.22f
        private val CLICK_ANIMATION_SPEED = 0.32f
        private val CLICK_SCALE = 0.12f
        private val GOAL_FLOAT_SPEED = 0.18f
        private val GOAL_FLOAT_DISTANCE = 2.0f
        private val BACK_EASE_C1 = 1.15f
        private val BACK_EASE_C3 = BACK_EASE_C1 + 1.0f
}