package dev.gisketch.chowkingdom.battlepass

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

class BattlepassScreen : Screen(Component.translatable("screen.${ChowKingdomMod.MOD_ID}.battlepass")) {
    private enum class ViewMode { PASS_SELECTION, PASS_DETAIL }

    private enum class MissionFilter(val label: String) {
        ALL("All"), WEEKLY("Weekly"), DAILY("Daily"), PERMANENT("Permanent"), COMPLETED("Done");

        fun next(): MissionFilter = entries[(ordinal + 1) % entries.size]
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        fun inset(amount: Int): Rect = Rect(x + amount, y + amount, (width - amount * 2).coerceAtLeast(0), (height - amount * 2).coerceAtLeast(0))
    }

    private data class RewardSlot(
        val rect: Rect,
        val passId: String,
        val tier: BattlepassProgressionDefinition,
        val reward: BattlepassRewardDefinition,
        val stack: ItemStack,
        val claimed: Boolean,
        val unlocked: Boolean,
        val claimable: Boolean,
        val current: Boolean,
        val previousXp: Int,
    )

    private data class MissionSlot(
        val rect: Rect,
        val passId: String,
        val entry: BattlepassMissionEntry,
        val completed: Boolean,
    )

    private var selectedPassId: String? = null
    private var viewMode = ViewMode.PASS_SELECTION
    private var rewardScroll = 0.0f
    private var targetRewardScroll = 0.0f
    private var missionsScroll = 0.0f
    private var targetMissionsScroll = 0.0f
    private var missionsMaxScroll = 0.0f
    private var autoScrollKey: String? = null
    private var missionFilter = MissionFilter.ALL
    private var missionFilterRect = Rect(0, 0, 0, 0)
    private var missionsRect = Rect(0, 0, 0, 0)
    private var passRects: List<Pair<Rect, BattlepassPassDefinition>> = emptyList()
    private var rewardSlots: List<RewardSlot> = emptyList()
    private var missionSlots: List<MissionSlot> = emptyList()
    private var backButton: Button? = null
    private var claimAllButton: Button? = null

    override fun init() {
        BattlepassNetwork.requestSync()
        BattlepassPassRegistry.reload()
        selectedPassId = selectedPassId ?: passes().firstOrNull()?.id
        backButton = addRenderableWidget(
            Button.builder(Component.translatable("ui.${ChowKingdomMod.MOD_ID}.back")) {
                viewMode = ViewMode.PASS_SELECTION
                rewardScroll = 0.0f
                targetRewardScroll = 0.0f
                missionsScroll = 0.0f
                targetMissionsScroll = 0.0f
            }.bounds(0, 0, BACK_BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        claimAllButton = addRenderableWidget(
            Button.builder(Component.literal("Claim All")) {
                selectedPassId?.let { passId ->
                    BattlepassNetwork.claimAll(passId)
                    playClaimSound()
                }
            }.bounds(0, 0, CLAIM_ALL_WIDTH, BUTTON_HEIGHT).build(),
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        rewardScroll = Mth.lerp(SCROLL_LERP, rewardScroll, targetRewardScroll)
        missionsScroll = Mth.lerp(SCROLL_LERP, missionsScroll, targetMissionsScroll)
        ensureSelectedPass()
        if (viewMode == ViewMode.PASS_SELECTION) renderSelection(guiGraphics, mouseX, mouseY) else renderDetail(guiGraphics, mouseX, mouseY)
        renderables.forEach { renderable -> renderable.render(guiGraphics, mouseX, mouseY, partialTick) }
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTexture(guiGraphics, UI_BACKGROUND_TEXTURE, Rect(0, 0, width, height), UI_BACKGROUND_WIDTH, UI_BACKGROUND_HEIGHT)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        if (super.mouseClicked(mouseX, mouseY, button)) return true

        if (viewMode == ViewMode.PASS_SELECTION) {
            passRects.firstOrNull { (rect, _) -> rect.contains(mouseX, mouseY) }?.second?.let { pass ->
                selectedPassId = pass.id
                viewMode = ViewMode.PASS_DETAIL
                rewardScroll = 0.0f
                targetRewardScroll = 0.0f
                missionsScroll = 0.0f
                targetMissionsScroll = 0.0f
                autoScrollKey = null
                playButtonClickSound()
                return true
            }
        }

        if (viewMode == ViewMode.PASS_DETAIL && missionFilterRect.contains(mouseX, mouseY)) {
            missionFilter = missionFilter.next()
            missionsScroll = 0.0f
            targetMissionsScroll = 0.0f
            playButtonClickSound()
            return true
        }

        missionSlots.firstOrNull { slot -> missionsRect.contains(mouseX, mouseY) && slot.rect.contains(mouseX, mouseY) }?.let { mission ->
            val pass = passes().firstOrNull { pass -> pass.id == mission.passId } ?: selectedPass() ?: return true
            val tracked = BattlepassTrackedMissions.toggle(pass, mission.entry)
            if (!tracked) Minecraft.getInstance().player?.displayClientMessage(Component.literal("Track limit reached ($MISSIONS_TRACK_LIMIT missions)"), true)
            playButtonClickSound()
            return true
        }

        rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) && slot.claimable }?.let { reward ->
            BattlepassNetwork.claim(selectedPassId ?: return true, reward.tier.xp)
            playClaimSound()
            return true
        }

        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (viewMode == ViewMode.PASS_DETAIL && missionsRect.contains(mouseX, mouseY)) {
            targetMissionsScroll = (targetMissionsScroll - scrollY.toFloat() * MISSIONS_SCROLL_STEP).coerceIn(0.0f, missionsMaxScroll)
            return true
        }

        if (viewMode == ViewMode.PASS_DETAIL && hotbarRect().contains(mouseX, mouseY)) {
            targetRewardScroll = (targetRewardScroll - scrollY.toFloat() * REWARD_SCROLL_STEP).coerceIn(0.0f, rewardMaxScroll())
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun renderSelection(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        backButton?.visible = false
        claimAllButton?.visible = false
        val panel = mainRect().inset(SAFE_EDGE_PADDING)
        drawCkdmText(guiGraphics, "BATTLEPASS", panel.x + PANEL_PADDING, panel.y + PANEL_PADDING, TEXT_PRIMARY)

        val passes = passes()
        if (passes.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("ui.${ChowKingdomMod.MOD_ID}.battlepass.empty"), panel.x + PANEL_PADDING, panel.y + 42, TEXT_MUTED, false)
            passRects = emptyList()
            return
        }

        val selectedPass = passes.firstOrNull { pass -> pass.id == selectedPassId } ?: passes.first()
        selectedPassId = selectedPass.id
        passRects = passes.zip(selectionCardRects(panel, passes.size)).map { (pass, rect) ->
            renderPassCard(guiGraphics, pass, rect, pass.id == selectedPass.id, rect.contains(mouseX.toDouble(), mouseY.toDouble()))
            rect to pass
        }

        passRects.firstOrNull { (rect, _) -> rect.contains(mouseX.toDouble(), mouseY.toDouble()) }?.second?.let { pass ->
            guiGraphics.renderComponentTooltip(font, passTooltip(pass), mouseX, mouseY)
        }
    }

    private fun selectionCardRects(panel: Rect, count: Int): List<Rect> {
        val columns = count.coerceIn(1, 3)
        val rows = (count + columns - 1) / columns
        val gap = 10
        val availableWidth = panel.width - PANEL_PADDING * 2 - gap * (columns - 1)
        val cardWidth = (availableWidth / columns).coerceAtLeast(120)
        val cardHeight = 78.coerceAtMost((panel.height - 56 - gap * (rows - 1)) / rows.coerceAtLeast(1))
        val startX = panel.x + PANEL_PADDING
        val startY = panel.y + 42
        return (0 until count).map { index ->
            Rect(startX + index % columns * (cardWidth + gap), startY + index / columns * (cardHeight + gap), cardWidth, cardHeight)
        }
    }

    private fun renderPassCard(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, rect: Rect, selected: Boolean, hovered: Boolean) {
        val fill = when {
            selected -> CARD_SELECTED_FILL
            hovered -> CARD_HOVER_FILL
            else -> CARD_FILL
        }
        fillPanel(guiGraphics, rect, fill)
        drawCkdmText(guiGraphics, fitText(pass.displayName.uppercase(), rect.width - 18), rect.x + 8, rect.y + 8, TEXT_PRIMARY)
        guiGraphics.drawString(font, "${pass.progression.size} tiers", rect.x + 8, rect.y + 24, TEXT_MUTED, false)
        guiGraphics.drawString(font, "${activeMissionEntries(pass).size} missions", rect.x + 8, rect.y + 38, TEXT_MUTED, false)
        pass.progression.sortedBy { tier -> tier.xp }.firstOrNull()?.rewards?.firstOrNull()?.let { reward ->
            renderItemSprite(guiGraphics, rewardStack(reward), rect.x + rect.width - 26, rect.y + rect.height - 26)
        }
    }

    private fun renderDetail(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val pass = selectedPass() ?: run {
            viewMode = ViewMode.PASS_SELECTION
            renderSelection(guiGraphics, mouseX, mouseY)
            return
        }
        val currentXp = currentPlayerId()?.let { id -> xpFor(id, pass.id) } ?: 0
        BattlepassTrackedMissions.sync(passes(), removeCompleted = true)

        val panel = mainRect().inset(SAFE_EDGE_PADDING)
        renderHeader(guiGraphics, panel, pass, currentXp)
        layoutBackButton(panel)

        val hotbar = hotbarRect()
        val contentTop = panel.y + HEADER_HEIGHT
        val contentBottom = hotbar.y - ITEM_STRIP_GAP
        val playerWidth = PLAYER_PREVIEW_WIDTH.coerceAtMost((panel.width / 3).coerceAtLeast(PLAYER_PREVIEW_MIN_WIDTH))
        val playerRect = Rect(panel.x + panel.width - PANEL_PADDING - playerWidth, contentTop, playerWidth, (contentBottom - contentTop).coerceAtLeast(PLAYER_PREVIEW_MIN_HEIGHT))
        val missionRect = Rect(panel.x + PANEL_PADDING, contentTop, (playerRect.x - panel.x - PANEL_PADDING * 2).coerceAtLeast(120), playerRect.height)

        renderMissions(guiGraphics, pass, missionRect, mouseX, mouseY)
        renderPlayerPreview(guiGraphics, pass, playerRect, currentXp)
        autoCenterCurrentReward(pass, currentXp, hotbar)
        renderRewardHotbar(guiGraphics, hotbar, pass, currentXp, mouseX, mouseY)
        renderClaimAllButton()
    }

    private fun renderHeader(guiGraphics: GuiGraphics, panel: Rect, pass: BattlepassPassDefinition, currentXp: Int) {
        drawCkdmText(guiGraphics, fitText(pass.displayName.uppercase(), titleWidth()), panel.x + PANEL_PADDING, panel.y + 12, TEXT_PRIMARY)
        guiGraphics.drawString(font, "XP $currentXp", panel.x + PANEL_PADDING, panel.y + 26, TEXT_MUTED, false)
    }

    private fun layoutBackButton(panel: Rect) {
        backButton?.setX(panel.x + panel.width - PANEL_PADDING - BACK_BUTTON_WIDTH)
        backButton?.setY(panel.y + 10)
        backButton?.visible = true
        backButton?.active = true
    }

    private fun renderMissions(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, rect: Rect, mouseX: Int, mouseY: Int) {
        fillPanel(guiGraphics, rect, CARD_FILL)
        drawCkdmText(guiGraphics, "MISSIONS", rect.x + 8, rect.y + 8, TEXT_PRIMARY)
        renderMissionFilterButton(guiGraphics, rect, mouseX, mouseY)

        missionsRect = Rect(rect.x + 8, rect.y + 28, rect.width - 16, rect.height - 36)
        val missions = visibleMissionEntries(pass)
            .map { entry -> entry to (currentPlayerId()?.let { id -> BattlepassClientState.isMissionCompleted(id, pass.id, entry.key) } == true) }
            .sortedBy { (_, completed) -> completed }
        missionsMaxScroll = (missions.sumOf { (entry, _) -> missionRowHeight(entry.event) } - missionsRect.height).coerceAtLeast(0).toFloat()
        targetMissionsScroll = targetMissionsScroll.coerceIn(0.0f, missionsMaxScroll)
        missionsScroll = missionsScroll.coerceIn(0.0f, missionsMaxScroll)
        missionSlots = emptyList()

        guiGraphics.enableScissor(missionsRect.x, missionsRect.y, missionsRect.x + missionsRect.width, missionsRect.y + missionsRect.height)
        var rowY = missionsRect.y - missionsScroll.toInt()
        if (missions.isEmpty()) {
            guiGraphics.drawString(font, "No missions", missionsRect.x, rowY, TEXT_MUTED, false)
        } else {
            missions.forEach { (entry, completed) ->
                val rowHeight = missionRowHeight(entry.event)
                val row = Rect(missionsRect.x, rowY, missionsRect.width - SCROLLBAR_GAP, rowHeight - ROW_GAP)
                if (row.y + row.height > missionsRect.y && row.y < missionsRect.y + missionsRect.height) {
                    missionSlots = missionSlots + MissionSlot(row, pass.id, entry, completed)
                    renderMissionRow(guiGraphics, pass.id, entry, row, completed, row.contains(mouseX.toDouble(), mouseY.toDouble()))
                }
                rowY += rowHeight
            }
        }
        guiGraphics.disableScissor()
        renderScrollbar(guiGraphics, missionsRect, missionsScroll, missionsMaxScroll)
        missionSlots.firstOrNull { slot -> missionsRect.contains(mouseX.toDouble(), mouseY.toDouble()) && slot.rect.contains(mouseX.toDouble(), mouseY.toDouble()) }?.let { slot ->
            guiGraphics.renderComponentTooltip(font, tooltipFor(slot), mouseX, mouseY)
        }
    }

    private fun renderMissionFilterButton(guiGraphics: GuiGraphics, parent: Rect, mouseX: Int, mouseY: Int) {
        val buttonWidth = font.width(missionFilter.label) + 18
        missionFilterRect = Rect(parent.x + parent.width - buttonWidth - 8, parent.y + 5, buttonWidth, BUTTON_HEIGHT)
        fillPanel(guiGraphics, missionFilterRect, if (missionFilterRect.contains(mouseX.toDouble(), mouseY.toDouble())) CARD_HOVER_FILL else PANEL_FILL)
        guiGraphics.drawString(font, missionFilter.label, missionFilterRect.x + 9, missionFilterRect.y + 6, TEXT_PRIMARY, false)
    }

    private fun renderMissionRow(guiGraphics: GuiGraphics, passId: String, entry: BattlepassMissionEntry, rect: Rect, completed: Boolean, hovered: Boolean) {
        val progress = missionProgress(passId, entry)
        val tracked = BattlepassTrackedMissions.isTracked(passId, entry.key)
        val titlePrefix = when {
            completed -> "Done "
            tracked -> "* "
            else -> ""
        }
        val fill = when {
            completed -> ROW_DONE_FILL
            hovered -> CARD_HOVER_FILL
            else -> ROW_FILL
        }
        fillPanel(guiGraphics, rect, fill)
        guiGraphics.drawString(font, fitText(titlePrefix + missionDescription(passId, entry), rect.width - 68), rect.x + 6, rect.y + 5, if (completed) TEXT_MUTED else TEXT_PRIMARY, false)
        guiGraphics.drawString(font, "+${entry.event.xp}", rect.x + rect.width - 34, rect.y + 5, missionColor(entry.scope), false)

        BattlepassMissionService.displayGoal(entry.event, progress)?.let { goal ->
            val bar = Rect(rect.x + 6, rect.y + 18, rect.width - 58, 5)
            val fillWidth = (bar.width * (progress.toFloat() / goal.coerceAtLeast(1))).toInt().coerceIn(0, bar.width)
            guiGraphics.fill(bar.x, bar.y, bar.x + bar.width, bar.y + bar.height, PROGRESS_BACK)
            guiGraphics.fill(bar.x, bar.y, bar.x + fillWidth, bar.y + bar.height, missionColor(entry.scope))
            val progressText = "${progress.coerceAtMost(goal)}/$goal"
            guiGraphics.drawString(font, progressText, rect.x + rect.width - font.width(progressText) - 6, rect.y + 17, TEXT_MUTED, false)
        }
    }

    private fun renderPlayerPreview(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, rect: Rect, currentXp: Int) {
        fillPanel(guiGraphics, rect, CARD_FILL)
        drawCkdmText(guiGraphics, "PLAYER", rect.x + 8, rect.y + 8, TEXT_PRIMARY)
        Minecraft.getInstance().player?.let { player ->
            InventoryScreen.renderEntityInInventoryFollowsAngle(
                guiGraphics,
                rect.x + 8,
                rect.y + 28,
                rect.x + rect.width - 8,
                rect.y + rect.height - 34,
                (rect.height / 3).coerceIn(PLAYER_PREVIEW_MIN_SIZE, PLAYER_PREVIEW_MAX_SIZE),
                PLAYER_PREVIEW_Y_OFFSET,
                PLAYER_PREVIEW_ANGLE_X,
                PLAYER_PREVIEW_ANGLE_Y,
                player,
            )
        }
        renderPlayerXpProgress(guiGraphics, pass, currentXp, Rect(rect.x + 8, rect.y + rect.height - 26, rect.width - 16, 14))
    }

    private fun renderPlayerXpProgress(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, currentXp: Int, rect: Rect) {
        val tiers = pass.progression.sortedBy { tier -> tier.xp }
        val nextTier = tiers.firstOrNull { tier -> currentXp < tier.xp }
        val previousTierXp = tiers.lastOrNull { tier -> tier.xp <= currentXp }?.xp ?: 0
        val targetXp = nextTier?.xp ?: tiers.lastOrNull()?.xp ?: currentXp
        val span = (targetXp - previousTierXp).coerceAtLeast(1)
        val progress = if (nextTier == null) 1.0f else ((currentXp - previousTierXp).toFloat() / span).coerceIn(0.0f, 1.0f)
        val fillWidth = (rect.width * progress).toInt().coerceIn(0, rect.width)
        val text = if (nextTier == null) "Max XP $currentXp" else "${(targetXp - currentXp).coerceAtLeast(0)} XP to $targetXp"
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, PROGRESS_BACK)
        guiGraphics.fill(rect.x, rect.y, rect.x + fillWidth, rect.y + rect.height, PROGRESS_FILL)
        guiGraphics.drawString(font, fitText(text, rect.width - 4), rect.x + 2, rect.y + 3, TEXT_PRIMARY, false)
    }

    private fun renderRewardHotbar(guiGraphics: GuiGraphics, hotbar: Rect, pass: BattlepassPassDefinition, currentXp: Int, mouseX: Int, mouseY: Int) {
        val tiers = pass.progression.sortedBy { tier -> tier.xp }
        val maxScroll = maxScroll(rewardContentWidth(pass), hotbar.width)
        targetRewardScroll = targetRewardScroll.coerceIn(0.0f, maxScroll)
        rewardScroll = rewardScroll.coerceIn(0.0f, maxScroll)
        rewardSlots = emptyList()
        val playerId = currentPlayerId()

        guiGraphics.enableScissor(hotbar.x + 4, hotbar.y + 4, hotbar.x + hotbar.width - 4, hotbar.y + hotbar.height - 4)
        var nextX = hotbar.x + 10 - rewardScroll.toInt()
        tiers.forEachIndexed { index, tier ->
            val reward = tier.rewards.firstOrNull()
            val slotSize = slotSize(reward?.let(::isProminentReward) == true)
            val previousXp = tiers.getOrNull(index - 1)?.xp ?: 0
            val claimed = playerId?.let { id -> isClaimed(id, pass.id, tier.xp) } == true
            val unlocked = currentXp >= tier.xp
            val claimable = unlocked && !claimed
            val current = !claimed && currentXp >= previousXp && !unlocked
            if (reward != null) {
                val slot = RewardSlot(Rect(nextX, hotbar.y + (hotbar.height - slotSize) / 2, slotSize, slotSize), pass.id, tier, reward, rewardStack(reward), claimed, unlocked, claimable, current, previousXp)
                rewardSlots = rewardSlots + slot
                renderRewardSlot(guiGraphics, slot, currentXp, slot.rect.contains(mouseX.toDouble(), mouseY.toDouble()))
            }
            nextX += slotSize + SLOT_GAP
        }
        guiGraphics.disableScissor()
        renderScrollbar(guiGraphics, Rect(hotbar.x + 4, hotbar.y + hotbar.height - 5, hotbar.width - 8, 2), rewardScroll, maxScroll)
        rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX.toDouble(), mouseY.toDouble()) }?.let { slot ->
            guiGraphics.renderComponentTooltip(font, tooltipFor(slot, currentXp), mouseX, mouseY)
        }
    }

    private fun renderRewardSlot(guiGraphics: GuiGraphics, slot: RewardSlot, currentXp: Int, hovered: Boolean) {
        renderTexture(guiGraphics, if (slot.claimed) REWARD_BOX_CLAIMED_TEXTURE else REWARD_BOX_TEXTURE, slot.rect, REWARD_BOX_TEXTURE_SIZE, REWARD_BOX_TEXTURE_SIZE)
        if (hovered && !slot.claimed) guiGraphics.fill(slot.rect.x, slot.rect.y, slot.rect.x + slot.rect.width, slot.rect.y + slot.rect.height, REWARD_HOVER_TINT)
        if (slot.current) {
            val progressWidth = (slot.rect.width * progressFor(slot, currentXp)).toInt().coerceIn(0, slot.rect.width)
            guiGraphics.fill(slot.rect.x, slot.rect.y + slot.rect.height - 4, slot.rect.x + progressWidth, slot.rect.y + slot.rect.height - 1, PROGRESS_FILL)
        }
        renderRewardItemSprite(guiGraphics, slot.stack, slot.rect)
        if (slot.reward.quantity > 1) {
            val quantity = compactQuantity(slot.reward.quantity)
            drawCkdmText(guiGraphics, quantity, slot.rect.x + slot.rect.width - ckdmWidth(quantity) - QUANTITY_PADDING, slot.rect.y + slot.rect.height - QUANTITY_TEXT_HEIGHT - QUANTITY_PADDING, TEXT_PRIMARY)
        }
        if (slot.claimed) guiGraphics.drawString(font, "OK", slot.rect.x + slot.rect.width - 17, slot.rect.y + 4, TEXT_GOOD, false)
        if (!slot.unlocked) {
            renderLockedOverlay(guiGraphics, slot.rect)
        }
    }

    private fun renderLockedOverlay(guiGraphics: GuiGraphics, rect: Rect) {
        guiGraphics.flush()
        RenderSystem.disableDepthTest()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0.0f, 0.0f, LOCK_OVERLAY_Z)
        renderTexture(guiGraphics, LOCKED_TEXTURE, Rect(rect.x + (rect.width - LOCKED_ICON_SIZE) / 2, rect.y + (rect.height - LOCKED_ICON_SIZE) / 2, LOCKED_ICON_SIZE, LOCKED_ICON_SIZE), LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE)
        guiGraphics.pose().popPose()
        guiGraphics.flush()
        RenderSystem.enableDepthTest()
    }

    private fun renderItemSprite(guiGraphics: GuiGraphics, stack: ItemStack, x: Int, y: Int) {
        guiGraphics.renderItem(stack, x, y)
    }

    private fun renderRewardItemSprite(guiGraphics: GuiGraphics, stack: ItemStack, rect: Rect) {
        val renderSize = (rect.width - ITEM_RENDER_PADDING * 2).coerceAtLeast(BASE_ITEM_RENDER_SIZE) / BASE_ITEM_RENDER_SIZE * BASE_ITEM_RENDER_SIZE
        val scale = renderSize / BASE_ITEM_RENDER_SIZE
        val x = rect.x + (rect.width - renderSize) / 2
        val y = rect.y + (rect.height - renderSize) / 2
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat(), 0.0f)
        guiGraphics.pose().scale(scale.toFloat(), scale.toFloat(), 1.0f)
        guiGraphics.renderItem(stack, 0, 0)
        guiGraphics.pose().popPose()
    }

    private fun renderClaimAllButton() {
        val footer = footerRect()
        val claimableCount = rewardSlots.count { slot -> slot.claimable }
        claimAllButton?.setX((footer.x + footer.width - CONTENT_PADDING - CLAIM_ALL_WIDTH).coerceAtLeast(footer.x + CONTENT_PADDING))
        claimAllButton?.setY(footer.y + (footer.height - BUTTON_HEIGHT) / 2)
        claimAllButton?.setMessage(Component.literal(if (claimableCount > 0) "Claim All ($claimableCount)" else "Claim All"))
        claimAllButton?.visible = true
        claimAllButton?.active = claimableCount > 0
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, rect: Rect, scrollValue: Float, maxScrollValue: Float) {
        if (maxScrollValue <= 0.0f) return
        if (rect.height <= 2) {
            val thumbWidth = (rect.width * (rect.width / (rect.width + maxScrollValue))).toInt().coerceIn(12, rect.width)
            val travel = (rect.width - thumbWidth).coerceAtLeast(1)
            val thumbX = rect.x + (travel * (scrollValue / maxScrollValue)).toInt().coerceIn(0, travel)
            guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, SCROLL_TRACK)
            guiGraphics.fill(thumbX, rect.y, thumbX + thumbWidth, rect.y + rect.height, SCROLL_THUMB)
            return
        }
        val trackX = rect.x + rect.width - SCROLLBAR_WIDTH
        val contentHeight = rect.height + maxScrollValue
        val thumbHeight = (rect.height * (rect.height / contentHeight)).toInt().coerceIn(12, rect.height)
        val travel = (rect.height - thumbHeight).coerceAtLeast(1)
        val thumbY = rect.y + (travel * (scrollValue / maxScrollValue)).toInt().coerceIn(0, travel)
        guiGraphics.fill(trackX, rect.y, trackX + SCROLLBAR_WIDTH, rect.y + rect.height, SCROLL_TRACK)
        guiGraphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLL_THUMB)
    }

    private fun fillPanel(guiGraphics: GuiGraphics, rect: Rect, fillColor: Int) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, fillColor)
    }

    private fun renderTexture(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, 0.0f, 0.0f, textureWidth, textureHeight, textureWidth, textureHeight)
        RenderSystem.disableBlend()
    }

    private fun drawCkdmText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int) {
        guiGraphics.drawString(font, ckdmText(text), x, y, color, false)
    }

    private fun ckdmWidth(text: String): Int = font.width(ckdmText(text))

    private fun ckdmText(text: String): Component = Component.literal(text.uppercase()).withStyle { style -> style.withFont(CKDM_BOLD_FONT) }

    private fun tooltipFor(slot: RewardSlot, currentXp: Int): List<Component> {
        val remaining = (slot.tier.xp - currentXp).coerceAtLeast(0)
        val status = when {
            slot.claimed -> Component.literal("Claimed").withStyle(ChatFormatting.GREEN)
            slot.claimable -> Component.literal("Click to claim").withStyle(ChatFormatting.AQUA)
            slot.current -> Component.literal("$remaining XP to claim").withStyle(ChatFormatting.AQUA)
            else -> Component.literal("Locked - $remaining XP needed").withStyle(ChatFormatting.GRAY)
        }
        return listOf(Component.literal("Tier ${slot.tier.xp} XP").withStyle(ChatFormatting.GOLD), Component.literal("${rewardName(slot.reward, slot.stack)} x${slot.reward.quantity}"), status)
    }

    private fun tooltipFor(slot: MissionSlot): List<Component> {
        val progress = missionProgress(slot.passId, slot.entry)
        val detail = BattlepassMissionService.displayGoal(slot.entry.event, progress)?.let { goal ->
            Component.literal("Progress: ${progress.coerceAtMost(goal)}/$goal").withStyle(ChatFormatting.GRAY)
        } ?: Component.literal("Repeating: +${slot.entry.event.xp} XP").withStyle(ChatFormatting.GRAY)
        val status = if (slot.completed) Component.literal("Completed") else Component.literal("Click to track")
        return listOf(Component.literal(missionTypeLabel(slot.entry.scope)).withStyle(missionChatColor(slot.entry.scope)), detail, status)
    }

    private fun passTooltip(pass: BattlepassPassDefinition): List<Component> = listOf(
        Component.literal(pass.displayName).withStyle(ChatFormatting.GOLD),
        Component.literal(pass.description.ifBlank { "Open battlepass" }).withStyle(ChatFormatting.GRAY),
    )

    private fun missionTypeLabel(scope: BattlepassMissionScope): String = when (scope) {
        BattlepassMissionScope.DAILY -> "Daily Mission"
        BattlepassMissionScope.WEEKLY -> "Weekly Mission"
        BattlepassMissionScope.PERMANENT -> "Permanent Mission"
    }

    private fun missionColor(scope: BattlepassMissionScope): Int = when (scope) {
        BattlepassMissionScope.DAILY -> 0xFFFFC857.toInt()
        BattlepassMissionScope.WEEKLY -> 0xFFFF7A6E.toInt()
        BattlepassMissionScope.PERMANENT -> 0xFF9AD67F.toInt()
    }

    private fun missionChatColor(scope: BattlepassMissionScope): ChatFormatting = when (scope) {
        BattlepassMissionScope.DAILY -> ChatFormatting.GOLD
        BattlepassMissionScope.WEEKLY -> ChatFormatting.RED
        BattlepassMissionScope.PERMANENT -> ChatFormatting.GREEN
    }

    private fun rewardStack(reward: BattlepassRewardDefinition): ItemStack {
        if (isChowcoinReward(reward)) return ItemStack(Items.GOLD_NUGGET, reward.quantity.coerceIn(1, 64))
        val item = runCatching { ResourceLocation.parse(reward.item) }.getOrNull()?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER) } ?: Items.BARRIER
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

    private fun autoCenterCurrentReward(pass: BattlepassPassDefinition, currentXp: Int, hotbar: Rect) {
        if (autoScrollKey == pass.id) return
        val sortedTiers = pass.progression.sortedBy { tier -> tier.xp }
        var offset = 10
        var selectedCenter = offset + SLOT_SIZE / 2
        val playerId = currentPlayerId()
        for (tier in sortedTiers) {
            val reward = tier.rewards.firstOrNull()
            val size = slotSize(reward?.let(::isProminentReward) == true)
            val claimed = playerId?.let { id -> isClaimed(id, pass.id, tier.xp) } == true
            selectedCenter = offset + size / 2
            if (!claimed) break
            offset += size + SLOT_GAP
        }
        val desiredScroll = (selectedCenter - hotbar.width / 2.0f).coerceIn(0.0f, maxScroll(rewardContentWidth(pass), hotbar.width))
        targetRewardScroll = desiredScroll
        rewardScroll = desiredScroll
        autoScrollKey = pass.id
    }

    private fun progressFor(slot: RewardSlot, currentXp: Int): Float {
        val span = (slot.tier.xp - slot.previousXp).coerceAtLeast(1)
        return ((currentXp - slot.previousXp).toFloat() / span).coerceIn(0.0f, 1.0f)
    }

    private fun visibleMissionEntries(pass: BattlepassPassDefinition): List<BattlepassMissionEntry> = filterMissionEntries(pass, activeMissionEntries(pass))

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
        MissionFilter.COMPLETED -> entries.filter { entry -> currentPlayerId()?.let { id -> BattlepassClientState.isMissionCompleted(id, pass.id, entry.key) } == true }
    }

    private fun missionDescription(passId: String, entry: BattlepassMissionEntry): String = BattlepassMissionService.missionDescription(entry.event, missionProgress(passId, entry))

    private fun missionProgress(passId: String, entry: BattlepassMissionEntry): Int {
        val event = entry.event
        return currentPlayerId()?.let { id -> BattlepassClientState.missionProgress(id, passId, entry.key) ?: BattlepassClientState.missionProgress(id, passId, event.event) } ?: event.progress
    }

    private fun missionRowHeight(event: BattlepassXpEventDefinition): Int = if (BattlepassMissionService.displayGoal(event, event.progress) != null) MISSIONS_PROGRESSIVE_ROW_HEIGHT else MISSIONS_REPEATING_ROW_HEIGHT

    private fun rewardContentWidth(pass: BattlepassPassDefinition): Int = pass.progression.sumOf { tier ->
        val reward = tier.rewards.firstOrNull()
        slotSize(reward?.let(::isProminentReward) == true) + SLOT_GAP
    } + 20

    private fun slotSize(prominent: Boolean): Int = if (prominent) PROMINENT_SLOT_SIZE else SLOT_SIZE

    private fun isProminentReward(reward: BattlepassRewardDefinition): Boolean = reward.isProminent || reward.data["is_prominent"]?.toBooleanStrictOrNull() == true

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

    private fun rewardMaxScroll(): Float = maxScroll(selectedPass()?.let(::rewardContentWidth) ?: 0, hotbarRect().width)

    private fun maxScroll(contentWidth: Int, visibleWidth: Int): Float = (contentWidth - visibleWidth + 16).coerceAtLeast(0).toFloat()

    private fun mainRect(): Rect = Rect(0, 0, width, height)

    private fun hotbarRect(): Rect {
        val panel = mainRect().inset(CONTENT_PADDING)
        return Rect(panel.x + PANEL_PADDING, footerRect().y - ITEM_STRIP_GAP - ITEM_STRIP_HEIGHT, panel.width - PANEL_PADDING * 2, ITEM_STRIP_HEIGHT)
    }

    private fun footerRect(): Rect = Rect(0, height - FOOTER_HEIGHT, width, FOOTER_HEIGHT)

    private fun titleWidth(): Int = (width - 176).coerceAtLeast(120).coerceAtMost(420)

    private fun fitText(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        val suffix = "..."
        var trimmed = text
        while (trimmed.isNotEmpty() && font.width(trimmed + suffix) > maxWidth) trimmed = trimmed.dropLast(1)
        return trimmed + suffix
    }

    private fun playClaimSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, CLAIM_SOUND_PITCH, CLAIM_SOUND_VOLUME))
    }

    private fun playButtonClickSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), BUTTON_CLICK_SOUND_PITCH, BUTTON_CLICK_SOUND_VOLUME))
    }

    private val UI_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/ui_bg.png")
    private val REWARD_BOX_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/box.png")
    private val REWARD_BOX_CLAIMED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/box_green_highlight.png")
    private val LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
    private val CKDM_BOLD_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    private val UI_BACKGROUND_WIDTH = 2560
    private val UI_BACKGROUND_HEIGHT = 1440
    private val REWARD_BOX_TEXTURE_SIZE = 512
    private val LOCKED_TEXTURE_SIZE = 16
    private val LOCKED_ICON_SIZE = 40
    private val PANEL_FILL = 0xCC101318.toInt()
    private val CARD_FILL = 0xCC171B22.toInt()
    private val CARD_HOVER_FILL = 0xDD222833.toInt()
    private val CARD_SELECTED_FILL = 0xDD283340.toInt()
    private val ROW_FILL = 0x881E242D.toInt()
    private val ROW_DONE_FILL = 0x66303A32
    private val REWARD_HOVER_TINT = 0x44FFFFFF
    private val SCROLL_TRACK = 0x7730343A
    private val SCROLL_THUMB = 0xFFC6CED8.toInt()
    private val PROGRESS_BACK = 0xFF2C3138.toInt()
    private val PROGRESS_FILL = 0xFF72C66F.toInt()
    private val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private val TEXT_MUTED = 0xFFB8C0CC.toInt()
    private val TEXT_GOOD = 0xFFB4F7B6.toInt()
    private val SAFE_EDGE_PADDING = 8
    private val CONTENT_PADDING = 10
    private val PANEL_PADDING = 12
    private val HEADER_HEIGHT = 52
    private val FOOTER_HEIGHT = 40
    private val ITEM_STRIP_HEIGHT = 110
    private val ITEM_STRIP_GAP = 8
    private val SLOT_SIZE = 80
    private val PROMINENT_SLOT_SIZE = 96
    private val SLOT_GAP = 8
    private val BASE_ITEM_RENDER_SIZE = 16
    private val ITEM_RENDER_PADDING = 16
    private val LOCK_OVERLAY_Z = 300.0f
    private val QUANTITY_PADDING = 8
    private val QUANTITY_TEXT_HEIGHT = 9
    private val PLAYER_PREVIEW_WIDTH = 172
    private val PLAYER_PREVIEW_MIN_WIDTH = 112
    private val PLAYER_PREVIEW_MIN_HEIGHT = 120
    private val PLAYER_PREVIEW_MIN_SIZE = 42
    private val PLAYER_PREVIEW_MAX_SIZE = 82
    private val PLAYER_PREVIEW_Y_OFFSET = 0.0625f
    private val PLAYER_PREVIEW_ANGLE_X = 0.15f
    private val PLAYER_PREVIEW_ANGLE_Y = 0.0f
    private val MISSIONS_REPEATING_ROW_HEIGHT = 27
    private val MISSIONS_PROGRESSIVE_ROW_HEIGHT = 38
    private val MISSIONS_SCROLL_STEP = 24.0f
    private val REWARD_SCROLL_STEP = 42.0f
    private val MISSIONS_TRACK_LIMIT = 7
    private val ROW_GAP = 4
    private val SCROLLBAR_WIDTH = 3
    private val SCROLLBAR_GAP = 6
    private val BUTTON_HEIGHT = 20
    private val BACK_BUTTON_WIDTH = 92
    private val CLAIM_ALL_WIDTH = 124
    private val SCROLL_LERP = 0.35f
    private val CLAIM_SOUND_PITCH = 1.2f
    private val CLAIM_SOUND_VOLUME = 0.7f
    private val BUTTON_CLICK_SOUND_PITCH = 1.0f
    private val BUTTON_CLICK_SOUND_VOLUME = 0.6f
}
