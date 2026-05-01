package dev.gisketch.chowkingdom.battlepass

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordQuickSkinSupport
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.ChatFormatting
import net.minecraft.Util
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID
import java.io.ByteArrayInputStream

class BattlepassScreen : Screen(Component.translatable("screen.${ChowKingdomMod.MOD_ID}.battlepass")) {
    private enum class ViewMode { PASS_SELECTION, PASS_DETAIL }

    private enum class MissionFilter(val label: String) {
        ALL("ALL"), DAILY("DAILY"), WEEKLY("WEEKLY"), PERMANENT("CKDM"), COMPLETED("DONE")
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        fun inset(amount: Int): Rect = Rect(x + amount, y + amount, (width - amount * 2).coerceAtLeast(0), (height - amount * 2).coerceAtLeast(0))
        fun offset(dx: Int, dy: Int): Rect = Rect(x + dx, y + dy, width, height)
    }

    private data class RewardSlot(
        val rect: Rect,
        val passId: String,
        val tier: BattlepassProgressionDefinition,
        val tierNumber: Int,
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

    private data class EntranceStyle(
        val delayMs: Int,
        val offsetX: Int = 0,
        val offsetY: Int = 0,
        val scaleFrom: Float = 1.0f,
        val durationMs: Int = 260,
        val timelineStartedAtMs: Long = 0L,
    )

    private var selectedPassId: String? = null
    private var viewMode = ViewMode.PASS_SELECTION
    private var rewardScroll = 0.0f
    private var targetRewardScroll = 0.0f
    private var missionsScroll = 0.0f
    private var targetMissionsScroll = 0.0f
    private var missionsMaxScroll = 0.0f
    private var backgroundParallaxX = 0.0f
    private var backgroundParallaxY = 0.0f
    private var detailAnimationStartedAtMs = 0L
    private var missionListAnimationStartedAtMs = 0L
    private var renderAlpha = 1.0f
    private val hoverAnimations = mutableMapOf<String, Float>()
    private val pressAnimations = mutableMapOf<String, Long>()
    private val quickSkinAvatarTextures = mutableMapOf<UUID, ResourceLocation?>()
    private var autoScrollKey: String? = null
    private var missionFilter = MissionFilter.ALL
    private var missionFilterRects: List<Pair<Rect, MissionFilter>> = emptyList()
    private var missionsRect = Rect(0, 0, 0, 0)
    private var claimAllRect = Rect(0, 0, 0, 0)
    private var claimAllClaimableCount = 0
    private var passRects: List<Pair<Rect, BattlepassPassDefinition>> = emptyList()
    private var rewardSlots: List<RewardSlot> = emptyList()
    private var missionSlots: List<MissionSlot> = emptyList()
    private var backButton: Button? = null
    private var claimAllButton: Button? = null

    override fun init() {
        BattlepassNetwork.requestSync()
        BattlepassPassRegistry.reload()
        quickSkinAvatarTextures.clear()
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
        renderAnimatedWidgets(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val targetX = if (width == 0) 0.0f else ((mouseX - width / 2.0f) / (width / 2.0f)).coerceIn(-1.0f, 1.0f) * BACKGROUND_PARALLAX_MAX_OFFSET
        val targetY = if (height == 0) 0.0f else ((mouseY - height / 2.0f) / (height / 2.0f)).coerceIn(-1.0f, 1.0f) * BACKGROUND_PARALLAX_MAX_OFFSET
        backgroundParallaxX = Mth.lerp(BACKGROUND_PARALLAX_LERP, backgroundParallaxX, targetX)
        backgroundParallaxY = Mth.lerp(BACKGROUND_PARALLAX_LERP, backgroundParallaxY, targetY)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(-backgroundParallaxX, -backgroundParallaxY, 0.0f)
        renderTexture(guiGraphics, UI_BACKGROUND_TEXTURE, Rect(-BACKGROUND_PARALLAX_PADDING, -BACKGROUND_PARALLAX_PADDING, width + BACKGROUND_PARALLAX_PADDING * 2, height + BACKGROUND_PARALLAX_PADDING * 2), UI_BACKGROUND_WIDTH, UI_BACKGROUND_HEIGHT)
        guiGraphics.pose().popPose()
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
                resetDetailAnimation()
                playButtonClickSound()
                return true
            }
        }

        if (viewMode == ViewMode.PASS_DETAIL) missionFilterRects.firstOrNull { (rect, _) -> rect.contains(mouseX, mouseY) }?.second?.let { filter ->
            registerPress(missionFilterKey(filter))
            if (missionFilter != filter) {
                missionFilter = filter
                resetMissionListAnimation()
            }
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

        if (viewMode == ViewMode.PASS_DETAIL && claimAllClaimableCount > 0 && claimAllRect.contains(mouseX, mouseY)) {
            rewardSlots.filter { slot -> slot.claimable }.forEach { slot -> BattlepassNetwork.claim(selectedPassId ?: return true, slot.tier.xp) }
            playClaimSound()
            return true
        }

        rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) }?.let { reward ->
            registerPress(rewardInteractionKey(reward))
            if (reward.claimable) {
                BattlepassNetwork.claim(selectedPassId ?: return true, reward.tier.xp)
                playClaimSound()
            }
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
        drawCkdmShadowedText(guiGraphics, "BATTLEPASS", panel.x + PANEL_PADDING, panel.y + PANEL_PADDING, TEXT_PRIMARY)

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
        val titleTextured = renderPassTitleTexture(guiGraphics, pass, Rect(rect.x + 8, rect.y + 7, rect.width - 36, 24))
        if (!titleTextured) {
            drawCkdmShadowedText(guiGraphics, fitText(pass.displayName.uppercase(), rect.width - 18), rect.x + 8, rect.y + 8, if (selected || hovered) TEXT_PRIMARY else TEXT_MUTED)
        }
        val detailY = if (titleTextured) rect.y + 34 else rect.y + 24
        guiGraphics.drawString(font, "${pass.progression.size} tiers", rect.x + 8, detailY, TEXT_MUTED, false)
        guiGraphics.drawString(font, "${activeMissionEntries(pass).size} missions", rect.x + 8, detailY + 14, TEXT_MUTED, false)
        pass.progression.sortedBy { tier -> tier.xp }.firstOrNull()?.rewards?.firstOrNull()?.let { reward ->
            renderItemSprite(guiGraphics, rewardStack(reward), rect.x + rect.width - 26, rect.y + rect.height - 26)
        }
    }

    private fun renderDetail(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (detailAnimationStartedAtMs == 0L) resetDetailAnimation()
        val pass = selectedPass() ?: run {
            viewMode = ViewMode.PASS_SELECTION
            renderSelection(guiGraphics, mouseX, mouseY)
            return
        }
        val currentXp = currentPlayerId()?.let { id -> xpFor(id, pass.id) } ?: 0
        BattlepassTrackedMissions.sync(passes(), removeCompleted = true)

        val panel = mainRect().inset(SAFE_EDGE_PADDING)
        withEntrance(guiGraphics, EntranceStyle(HEADER_ANIMATION_DELAY_MS, scaleFrom = HEADER_SCALE_FROM), panel.x + PANEL_PADDING, panel.y + 8) {
            renderHeader(guiGraphics, panel, pass)
        }
        layoutBackButton(panel)

        val hotbar = hotbarRect()
        val contentTop = panel.y + HEADER_HEIGHT
        val contentBottom = hotbar.y - ITEM_STRIP_GAP
        val playerWidth = PLAYER_PREVIEW_WIDTH.coerceAtMost((panel.width / 3).coerceAtLeast(PLAYER_PREVIEW_MIN_WIDTH))
        val playerRect = Rect(panel.x + panel.width - PANEL_PADDING - playerWidth, contentTop, playerWidth, (contentBottom - contentTop).coerceAtLeast(PLAYER_PREVIEW_MIN_HEIGHT))
        val missionRect = Rect(panel.x + PANEL_PADDING, contentTop, (playerRect.x - panel.x - PANEL_PADDING * 2).coerceAtLeast(120), playerRect.height)

        renderMissions(guiGraphics, pass, missionRect, mouseX, mouseY)
        withEntrance(guiGraphics, EntranceStyle(MISSION_LIST_ANIMATION_DELAY_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET)) {
            renderPlayerPreview(guiGraphics, pass, playerRect, currentXp)
        }
        autoCenterCurrentReward(pass, currentXp, hotbar)
        renderRewardHotbar(guiGraphics, hotbar, pass, currentXp, mouseX, mouseY)
        renderClaimAllButton(guiGraphics, mouseX, mouseY)
        renderHoveredBattlepassTooltip(guiGraphics, currentXp, mouseX, mouseY)
    }

    private fun renderHeader(guiGraphics: GuiGraphics, panel: Rect, pass: BattlepassPassDefinition) {
        if (!renderPassTitleTexture(guiGraphics, pass, Rect(panel.x + PANEL_PADDING, panel.y + 8, titleWidth(), HEADER_TITLE_TEXTURE_HEIGHT))) {
            drawCkdmShadowedText(guiGraphics, fitText(pass.displayName.uppercase(), titleWidth()), panel.x + PANEL_PADDING, panel.y + 12, TEXT_PRIMARY)
        }
    }

    private fun layoutBackButton(panel: Rect) {
        backButton?.setX(panel.x + panel.width - PANEL_PADDING - BACK_BUTTON_WIDTH)
        backButton?.setY(panel.y + 10)
        backButton?.visible = true
        backButton?.active = true
    }

    private fun renderMissions(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, rect: Rect, mouseX: Int, mouseY: Int) {
        withEntrance(guiGraphics, EntranceStyle(MISSIONS_TITLE_ANIMATION_DELAY_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET)) {
            drawCkdmShadowedText(guiGraphics, "MISSIONS", rect.x + 8, rect.y + 8, TEXT_PRIMARY, CKDM_BOLD_LARGE_FONT)
        }
        withEntrance(guiGraphics, EntranceStyle(MISSION_FILTER_ANIMATION_DELAY_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET)) {
            renderMissionFilterTabs(guiGraphics, rect, mouseX, mouseY)
        }

        missionsRect = Rect(rect.x + 8, rect.y + MISSION_HEADER_HEIGHT, rect.width - 16, rect.height - MISSION_HEADER_HEIGHT - 8)
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
            missions.forEachIndexed { index, (entry, completed) ->
                val rowHeight = missionRowHeight(entry.event)
                val row = Rect(missionsRect.x, rowY, missionsRect.width - SCROLLBAR_GAP, rowHeight - ROW_GAP)
                if (row.y + row.height > missionsRect.y && row.y < missionsRect.y + missionsRect.height) {
                    missionSlots = missionSlots + MissionSlot(row, pass.id, entry, completed)
                    withEntrance(guiGraphics, EntranceStyle(index * MISSION_ROW_STAGGER_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET, timelineStartedAtMs = missionListAnimationStartedAtMs)) {
                        renderMissionRow(guiGraphics, pass.id, entry, row, completed, row.contains(mouseX.toDouble(), mouseY.toDouble()))
                    }
                }
                rowY += rowHeight
            }
        }
        guiGraphics.disableScissor()
    }

    private fun renderMissionFilterTabs(guiGraphics: GuiGraphics, parent: Rect, mouseX: Int, mouseY: Int) {
        val y = parent.y + 34
        var x = parent.x + 8
        missionFilterRects = MissionFilter.entries.map { filter ->
            val width = ckdmWidth(filter.label, CKDM_BOLD_FONT)
            val rect = Rect(x, y, width, MISSION_TAB_TEXT_HEIGHT)
            val selected = filter == missionFilter
            val hovered = rect.contains(mouseX.toDouble(), mouseY.toDouble())
            val offsetY = interactionYOffset(missionFilterKey(filter), hovered)
            if (selected) {
                drawCkdmShadowedText(guiGraphics, filter.label, x, y + offsetY, TEXT_PRIMARY)
            } else {
                drawCkdmText(guiGraphics, filter.label, x, y + offsetY, if (hovered) TEXT_PRIMARY else TEXT_MUTED)
            }
            x += width + MISSION_TAB_GAP
            rect to filter
        }
    }

    private fun renderMissionRow(guiGraphics: GuiGraphics, passId: String, entry: BattlepassMissionEntry, rect: Rect, completed: Boolean, hovered: Boolean) {
        val progress = missionProgress(passId, entry)
        val tracked = BattlepassTrackedMissions.isTracked(passId, entry.key)
        val titlePrefix = when {
            completed -> "Done "
            tracked -> "* "
            else -> ""
        }
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
        Minecraft.getInstance().player?.let { player ->
            val mainHand = player.mainHandItem.copy()
            val offHand = player.offhandItem.copy()
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
            try {
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
            } finally {
                player.setItemSlot(EquipmentSlot.MAINHAND, mainHand)
                player.setItemSlot(EquipmentSlot.OFFHAND, offHand)
            }
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
                val slot = RewardSlot(Rect(nextX, hotbar.y + (hotbar.height - slotSize) / 2, slotSize, slotSize), pass.id, tier, index + 1, reward, rewardStack(reward), claimed, unlocked, claimable, current, previousXp)
                if (slot.rect.x + slot.rect.width > hotbar.x && slot.rect.x < hotbar.x + hotbar.width) {
                    rewardSlots = rewardSlots + slot
                    val hovered = slot.rect.contains(mouseX.toDouble(), mouseY.toDouble())
                    renderRewardSlot(guiGraphics, slot, currentXp, hovered, EntranceStyle(REWARD_ANIMATION_DELAY_MS + index * REWARD_STAGGER_MS, offsetX = REWARD_SLIDE_OFFSET), interactionYOffset(rewardInteractionKey(slot), hovered))
                }
            }
            nextX += slotSize + SLOT_GAP
        }
        guiGraphics.disableScissor()
    }

    private fun renderRewardSlot(guiGraphics: GuiGraphics, slot: RewardSlot, currentXp: Int, hovered: Boolean, entranceStyle: EntranceStyle? = null, interactionOffsetY: Int = 0) {
        if (entranceStyle != null) {
            val boxProgress = entranceProgress(entranceStyle)
            val dx = (entranceStyle.offsetX * (1.0f - boxProgress)).toInt()
            val dy = (entranceStyle.offsetY * (1.0f - boxProgress)).toInt()
            val previousAlpha = renderAlpha
            renderAlpha *= boxProgress
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
            renderRewardSlotContent(guiGraphics, slot.copy(rect = slot.rect.offset(dx, dy + interactionOffsetY)), currentXp, hovered, rewardItemEntranceScale(entranceStyle))
            renderAlpha = previousAlpha
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
            return
        }
        renderRewardSlotContent(guiGraphics, slot.copy(rect = slot.rect.offset(0, interactionOffsetY)), currentXp, hovered, 1.0f)
    }

    private fun renderRewardSlotContent(guiGraphics: GuiGraphics, slot: RewardSlot, currentXp: Int, hovered: Boolean, itemEntranceScale: Float) {
        val boxTexture = when {
            !slot.unlocked -> REWARD_BOX_LOCKED_TEXTURE
            slot.claimed -> REWARD_BOX_CLAIMED_TEXTURE
            else -> REWARD_BOX_TEXTURE
        }
        renderTexture(guiGraphics, boxTexture, slot.rect, REWARD_BOX_TEXTURE_SIZE, REWARD_BOX_TEXTURE_SIZE)
        if (hovered && !slot.claimed) guiGraphics.fill(slot.rect.x, slot.rect.y, slot.rect.x + slot.rect.width, slot.rect.y + slot.rect.height, REWARD_HOVER_TINT)
        if (slot.current) {
            val progressWidth = (slot.rect.width * progressFor(slot, currentXp)).toInt().coerceIn(0, slot.rect.width)
            guiGraphics.fill(slot.rect.x, slot.rect.y + slot.rect.height - 4, slot.rect.x + progressWidth, slot.rect.y + slot.rect.height - 1, PROGRESS_FILL)
        }
        renderRewardItemSprite(guiGraphics, slot.stack, slot.rect, !slot.unlocked, itemEntranceScale)
        if (itemEntranceScale <= 0.0f) return
        if (slot.reward.quantity > 1) {
            val quantity = compactQuantity(slot.reward.quantity)
            drawCkdmShadowedText(guiGraphics, quantity, slot.rect.x + slot.rect.width - ckdmWidth(quantity, CKDM_BOLD_SMALL_FONT) - QUANTITY_PADDING, slot.rect.y + slot.rect.height - QUANTITY_TEXT_HEIGHT - QUANTITY_PADDING, TEXT_PRIMARY, CKDM_BOLD_SMALL_FONT)
        }
        drawCkdmShadowedText(guiGraphics, slot.tierNumber.toString(), slot.rect.x + REWARD_TIER_PADDING, slot.rect.y + REWARD_TIER_PADDING, TEXT_PRIMARY)
        if (!slot.unlocked) {
            renderLockedOverlay(guiGraphics, slot.rect, lockShakeDegrees(rewardInteractionKey(slot)))
        }
    }

    private fun renderLockedOverlay(guiGraphics: GuiGraphics, rect: Rect, rotationDegrees: Float) {
        guiGraphics.flush()
        RenderSystem.disableDepthTest()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((rect.x + rect.width / 2.0f), (rect.y + rect.height / 2.0f), LOCK_OVERLAY_Z)
        if (rotationDegrees != 0.0f) guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees))
        renderTexture(guiGraphics, LOCKED_TEXTURE, Rect(-LOCKED_ICON_SIZE / 2, -LOCKED_ICON_SIZE / 2, LOCKED_ICON_SIZE, LOCKED_ICON_SIZE), LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE)
        guiGraphics.pose().popPose()
        guiGraphics.flush()
        RenderSystem.enableDepthTest()
    }

    private fun renderItemSprite(guiGraphics: GuiGraphics, stack: ItemStack, x: Int, y: Int) {
        guiGraphics.renderItem(stack, x, y)
    }

    private fun renderRewardItemSprite(guiGraphics: GuiGraphics, stack: ItemStack, rect: Rect, locked: Boolean, itemEntranceScale: Float) {
        if (itemEntranceScale <= 0.0f) return
        val itemRect = rewardItemRenderRect(rect)
        val scale = itemRect.width / BASE_ITEM_RENDER_SIZE
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(itemRect.x + itemRect.width / 2.0f, itemRect.y + itemRect.height / 2.0f, 0.0f)
        guiGraphics.pose().scale(scale * itemEntranceScale, scale * itemEntranceScale, 1.0f)
        guiGraphics.pose().translate(-BASE_ITEM_RENDER_SIZE / 2.0f, -BASE_ITEM_RENDER_SIZE / 2.0f, 0.0f)
        val itemAlpha = if (locked) LOCKED_ITEM_ALPHA else 1.0f
        if (itemAlpha < 1.0f) {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, itemAlpha)
        guiGraphics.renderItem(stack, 0, 0)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        guiGraphics.pose().popPose()
    }

    private fun rewardItemRenderRect(rect: Rect): Rect {
        val renderSize = (rect.width - ITEM_RENDER_PADDING * 2).coerceAtLeast(BASE_ITEM_RENDER_SIZE) / BASE_ITEM_RENDER_SIZE * BASE_ITEM_RENDER_SIZE
        return Rect(rect.x + (rect.width - renderSize) / 2, rect.y + (rect.height - renderSize) / 2, renderSize, renderSize)
    }

    private fun renderClaimAllButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val footer = footerRect()
        claimAllClaimableCount = rewardSlots.count { slot -> slot.claimable }
        claimAllRect = Rect((footer.x + footer.width - CONTENT_PADDING - CLAIM_ALL_WIDTH).coerceAtLeast(footer.x + CONTENT_PADDING), footer.y + (footer.height - BUTTON_HEIGHT) / 2, CLAIM_ALL_WIDTH, BUTTON_HEIGHT)
        claimAllButton?.visible = false
        withEntrance(guiGraphics, EntranceStyle(FOOTER_ANIMATION_DELAY_MS, offsetY = FOOTER_SLIDE_OFFSET)) {
            val active = claimAllClaimableCount > 0
            val hovered = active && claimAllRect.contains(mouseX.toDouble(), mouseY.toDouble())
            val fill = when {
                hovered -> BUTTON_HOVER_FILL
                active -> BUTTON_FILL
                else -> BUTTON_DISABLED_FILL
            }
            val text = if (active) "Claim All ($claimAllClaimableCount)" else "Claim All"
            renderNineSlice(guiGraphics, CLAIM_BUTTON_TEXTURE, claimAllRect, CLAIM_BUTTON_TEXTURE_SIZE, CLAIM_BUTTON_CORNER_SIZE, CLAIM_BUTTON_DESTINATION_CORNER_SIZE)
            if (!active || hovered) guiGraphics.fill(claimAllRect.x, claimAllRect.y, claimAllRect.x + claimAllRect.width, claimAllRect.y + claimAllRect.height, colorWithRenderAlpha(fill))
            guiGraphics.drawString(font, text, claimAllRect.x + (claimAllRect.width - font.width(text)) / 2, claimAllRect.y + 6, colorWithRenderAlpha(if (active) TEXT_PRIMARY else TEXT_MUTED), false)
        }
    }

    private fun renderAnimatedWidgets(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (viewMode != ViewMode.PASS_DETAIL) {
            renderables.forEach { renderable -> renderable.render(guiGraphics, mouseX, mouseY, partialTick) }
            return
        }
        backButton?.takeIf { button -> button.visible }?.let { button ->
            withEntrance(guiGraphics, EntranceStyle(HEADER_BUTTON_ANIMATION_DELAY_MS, offsetX = BUTTON_SLIDE_OFFSET)) {
                button.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }
    }

    private fun withEntrance(guiGraphics: GuiGraphics, style: EntranceStyle, anchorX: Int = 0, anchorY: Int = 0, render: () -> Unit) {
        val eased = entranceProgress(style)
        val previousAlpha = renderAlpha
        renderAlpha *= eased
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(style.offsetX * (1.0f - eased), style.offsetY * (1.0f - eased), 0.0f)
        if (style.scaleFrom != 1.0f) {
            val scale = style.scaleFrom + (1.0f - style.scaleFrom) * eased
            guiGraphics.pose().translate(anchorX.toFloat(), anchorY.toFloat(), 0.0f)
            guiGraphics.pose().scale(scale, scale, 1.0f)
            guiGraphics.pose().translate(-anchorX.toFloat(), -anchorY.toFloat(), 0.0f)
        }
        render()
        guiGraphics.pose().popPose()
        renderAlpha = previousAlpha
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
    }

    private fun entranceProgress(style: EntranceStyle): Float {
        val timelineStartedAtMs = style.timelineStartedAtMs.takeIf { value -> value > 0L } ?: detailAnimationStartedAtMs
        val elapsed = (Util.getMillis() - timelineStartedAtMs - style.delayMs).toFloat()
        val linear = (elapsed / style.durationMs.coerceAtLeast(1)).coerceIn(0.0f, 1.0f)
        val inverse = 1.0f - linear
        return 1.0f - inverse * inverse * inverse
    }

    private fun rewardItemEntranceScale(style: EntranceStyle): Float {
        val timelineStartedAtMs = style.timelineStartedAtMs.takeIf { value -> value > 0L } ?: detailAnimationStartedAtMs
        val elapsed = (Util.getMillis() - timelineStartedAtMs - style.delayMs - style.durationMs).toFloat()
        val progress = (elapsed / REWARD_ITEM_BOUNCE_DURATION_MS).coerceIn(0.0f, 1.0f)
        if (progress <= 0.0f) return 0.0f
        val shifted = progress - 1.0f
        val overshoot = 1.70158f
        return (1.0f + (overshoot + 1.0f) * shifted * shifted * shifted + overshoot * shifted * shifted).coerceAtMost(REWARD_ITEM_MAX_BOUNCE_SCALE)
    }

    private fun interactionYOffset(key: String, hovered: Boolean): Int {
        val hover = hoverProgress(key, hovered)
        val press = pressProgress(key)
        return (-HOVER_LIFT_OFFSET * hover + PRESS_DOWN_OFFSET * press).toInt()
    }

    private fun hoverProgress(key: String, hovered: Boolean): Float {
        val current = hoverAnimations[key] ?: 0.0f
        val target = if (hovered) 1.0f else 0.0f
        val next = Mth.lerp(HOVER_LERP, current, target)
        if (next < 0.01f && !hovered) hoverAnimations.remove(key) else hoverAnimations[key] = next
        return next
    }

    private fun pressProgress(key: String): Float {
        val startedAtMs = pressAnimations[key] ?: return 0.0f
        val elapsed = (Util.getMillis() - startedAtMs).toFloat()
        if (elapsed >= PRESS_ANIMATION_DURATION_MS) {
            pressAnimations.remove(key)
            return 0.0f
        }
        val linear = 1.0f - (elapsed / PRESS_ANIMATION_DURATION_MS).coerceIn(0.0f, 1.0f)
        return linear * linear
    }

    private fun lockShakeDegrees(key: String): Float {
        val startedAtMs = pressAnimations[key] ?: return 0.0f
        val elapsed = (Util.getMillis() - startedAtMs).toFloat()
        if (elapsed >= LOCK_SHAKE_DURATION_MS) return 0.0f
        val fade = 1.0f - (elapsed / LOCK_SHAKE_DURATION_MS).coerceIn(0.0f, 1.0f)
        return Mth.sin(elapsed / LOCK_SHAKE_DURATION_MS * LOCK_SHAKE_WAVES * Math.PI.toFloat() * 2.0f) * LOCK_SHAKE_DEGREES * fade
    }

    private fun registerPress(key: String) {
        pressAnimations[key] = Util.getMillis()
    }

    private fun rewardInteractionKey(slot: RewardSlot): String = "reward:${slot.passId}:${slot.tier.xp}"

    private fun missionFilterKey(filter: MissionFilter): String = "mission-filter:${filter.name}"

    private fun resetDetailAnimation() {
        detailAnimationStartedAtMs = Util.getMillis()
        missionListAnimationStartedAtMs = detailAnimationStartedAtMs + MISSION_LIST_ANIMATION_DELAY_MS
    }

    private fun resetMissionListAnimation() {
        missionListAnimationStartedAtMs = Util.getMillis()
    }

    private fun renderTexture(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, 0.0f, 0.0f, textureWidth, textureHeight, textureWidth, textureHeight)
        RenderSystem.disableBlend()
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureSize: Int, sourceCorner: Int, destinationCorner: Int = sourceCorner) {
        val edge = textureSize - sourceCorner
        val middle = textureSize - sourceCorner * 2
        val innerWidth = (rect.width - destinationCorner * 2).coerceAtLeast(0)
        val innerHeight = (rect.height - destinationCorner * 2).coerceAtLeast(0)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y, destinationCorner, destinationCorner), 0, 0, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y, innerWidth, destinationCorner), sourceCorner, 0, middle, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y, destinationCorner, destinationCorner), edge, 0, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y + destinationCorner, destinationCorner, innerHeight), 0, sourceCorner, sourceCorner, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + destinationCorner, innerWidth, innerHeight), sourceCorner, sourceCorner, middle, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y + destinationCorner, destinationCorner, innerHeight), edge, sourceCorner, sourceCorner, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y + rect.height - destinationCorner, destinationCorner, destinationCorner), 0, edge, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + rect.height - destinationCorner, innerWidth, destinationCorner), sourceCorner, edge, middle, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y + rect.height - destinationCorner, destinationCorner, destinationCorner), edge, edge, sourceCorner, sourceCorner, textureSize)
        RenderSystem.disableBlend()
    }

    private fun blitRegion(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int, textureSize: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, sourceX.toFloat(), sourceY.toFloat(), sourceWidth, sourceHeight, textureSize, textureSize)
    }

    private fun renderPassTitleTexture(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, bounds: Rect): Boolean {
        val texture = pass.titleTexture.takeIf(String::isNotBlank)?.let { value -> runCatching { ResourceLocation.parse(value) }.getOrNull() } ?: return false
        val textureWidth = pass.titleTextureWidth.coerceAtLeast(1)
        val textureHeight = pass.titleTextureHeight.coerceAtLeast(1)
        val targetWidth = (bounds.height * textureWidth / textureHeight).coerceAtMost(bounds.width)
        renderTexture(guiGraphics, texture, Rect(bounds.x, bounds.y, targetWidth, bounds.height), textureWidth, textureHeight)
        return true
    }

    private fun drawCkdmText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, fontId: ResourceLocation = CKDM_BOLD_FONT) {
        guiGraphics.drawString(font, ckdmText(text, fontId), x, y, colorWithRenderAlpha(color), false)
    }

    private fun drawCkdmShadowedText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, fontId: ResourceLocation = CKDM_BOLD_FONT) {
        val component = ckdmText(text, fontId)
        guiGraphics.drawString(font, component, x + CKDM_SHADOW_OFFSET, y + CKDM_SHADOW_OFFSET, colorWithRenderAlpha(CKDM_SHADOW_COLOR), false)
        guiGraphics.drawString(font, component, x, y, colorWithRenderAlpha(color), false)
    }

    private fun colorWithRenderAlpha(color: Int): Int {
        val alpha = (((color ushr 24) and 0xFF) * renderAlpha).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    private fun ckdmWidth(text: String, fontId: ResourceLocation = CKDM_BOLD_FONT): Int = font.width(ckdmText(text, fontId))

    private fun ckdmText(text: String, fontId: ResourceLocation = CKDM_BOLD_FONT): Component = Component.literal(text.uppercase()).withStyle { style -> style.withFont(fontId) }

    private fun renderHoveredBattlepassTooltip(guiGraphics: GuiGraphics, currentXp: Int, mouseX: Int, mouseY: Int) {
        val rewardSlot = rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX.toDouble(), mouseY.toDouble()) }
        val missionSlot = missionSlots.firstOrNull { slot -> missionsRect.contains(mouseX.toDouble(), mouseY.toDouble()) && slot.rect.contains(mouseX.toDouble(), mouseY.toDouble()) }
        if (rewardSlot == null && missionSlot == null) return
        guiGraphics.flush()
        RenderSystem.disableDepthTest()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0.0f, 0.0f, TOOLTIP_Z)
        if (rewardSlot != null) {
            renderRewardTooltip(guiGraphics, rewardSlot, currentXp, mouseX, mouseY)
        } else if (missionSlot != null) {
            renderMissionTooltip(guiGraphics, missionSlot, mouseX, mouseY)
        }
        guiGraphics.pose().popPose()
        guiGraphics.flush()
        RenderSystem.enableDepthTest()
    }

    private fun renderMissionTooltip(guiGraphics: GuiGraphics, slot: MissionSlot, mouseX: Int, mouseY: Int) {
        val progress = missionProgress(slot.passId, slot.entry)
        val goal = BattlepassMissionService.displayGoal(slot.entry.event, progress)
        val progressText = goal?.let { value -> "${progress.coerceAtMost(value)}/$value" } ?: "+${slot.entry.event.xp} XP"
        val completedPlayers = completedPlayersFor(slot)
        val title = missionTypeLabel(slot.entry.scope)
        val width = listOf(
            ckdmWidth(title),
            ckdmWidth(progressText, CKDM_BOLD_SMALL_FONT),
            completedPlayers.size.coerceAtMost(TOOLTIP_MAX_AVATARS) * (TOOLTIP_AVATAR_SIZE + TOOLTIP_AVATAR_GAP),
        ).maxOrNull().orEmptyWidth() + TOOLTIP_PADDING * 2
        val avatarHeight = if (completedPlayers.isEmpty()) 0 else TOOLTIP_SECTION_GAP + TOOLTIP_AVATAR_SIZE
        val height = TOOLTIP_PADDING * 2 + TOOLTIP_HEADER_CONTENT_GAP + avatarHeight
        val origin = tooltipOrigin(mouseX, mouseY, width, height)
        var textY = origin.y + TOOLTIP_PADDING
        drawTooltipPanel(guiGraphics, origin, width, height)
        drawCkdmShadowedText(guiGraphics, title, origin.x + TOOLTIP_PADDING, textY, TEXT_PRIMARY)
        textY += TOOLTIP_HEADER_CONTENT_GAP
        drawCkdmText(guiGraphics, progressText, origin.x + TOOLTIP_PADDING, textY, TOOLTIP_GOLD, CKDM_BOLD_SMALL_FONT)
        textY += TOOLTIP_SECTION_GAP
        renderCompletedPlayerAvatars(guiGraphics, completedPlayers, origin.x + TOOLTIP_PADDING, textY)
    }

    private fun renderRewardTooltip(guiGraphics: GuiGraphics, slot: RewardSlot, currentXp: Int, mouseX: Int, mouseY: Int) {
        val remaining = (slot.tier.xp - currentXp).coerceAtLeast(0)
        val claimedPlayers = if (slot.claimed) claimedPlayersFor(slot) else emptyList()
        val title = when {
            slot.claimed -> "CLAIMED"
            else -> "${compactQuantity(slot.reward.quantity)} ${rewardName(slot.reward, slot.stack)}"
        }
        val detail = when {
            slot.claimed -> ""
            slot.unlocked -> "CLICK TO CLAIM"
            else -> "$remaining XP NEEDED"
        }
        val avatarWidth = claimedPlayers.size.coerceAtMost(TOOLTIP_MAX_AVATARS) * (TOOLTIP_AVATAR_SIZE + TOOLTIP_AVATAR_GAP)
        val width = listOf(ckdmWidth(title), ckdmWidth(detail, CKDM_BOLD_SMALL_FONT), avatarWidth).maxOrNull().orEmptyWidth() + TOOLTIP_PADDING * 2
        val avatarHeight = if (claimedPlayers.isEmpty()) 0 else TOOLTIP_SECTION_GAP + TOOLTIP_AVATAR_SIZE
        val height = TOOLTIP_PADDING * 2 + TOOLTIP_LINE_HEIGHT + if (detail.isBlank()) 0 else TOOLTIP_LINE_HEIGHT + avatarHeight
        val origin = tooltipOrigin(mouseX, mouseY, width, height)
        drawTooltipPanel(guiGraphics, origin, width, height)
        drawCkdmShadowedText(guiGraphics, title, origin.x + TOOLTIP_PADDING, origin.y + TOOLTIP_PADDING, TEXT_PRIMARY)
        if (detail.isNotBlank()) drawCkdmText(guiGraphics, detail, origin.x + TOOLTIP_PADDING, origin.y + TOOLTIP_PADDING + TOOLTIP_LINE_HEIGHT, TOOLTIP_GOLD, CKDM_BOLD_SMALL_FONT)
        if (claimedPlayers.isNotEmpty()) renderCompletedPlayerAvatars(guiGraphics, claimedPlayers, origin.x + TOOLTIP_PADDING, origin.y + TOOLTIP_PADDING + TOOLTIP_LINE_HEIGHT + TOOLTIP_SECTION_GAP)
    }

    private fun renderCompletedPlayerAvatars(guiGraphics: GuiGraphics, players: List<BattlepassClientState.PlayerProgress>, x: Int, y: Int) {
        if (players.isEmpty()) {
            return
        }
        val connection = Minecraft.getInstance().connection
        players.take(TOOLTIP_MAX_AVATARS).forEachIndexed { index, player ->
            val avatarX = x + index * (TOOLTIP_AVATAR_SIZE + TOOLTIP_AVATAR_GAP)
            val quickSkinTexture = quickSkinAvatarTexture(player.uuid)
            val skin = connection?.getPlayerInfo(player.uuid)?.skin
            if (quickSkinTexture != null) {
                renderTexture(guiGraphics, quickSkinTexture, Rect(avatarX, y, TOOLTIP_AVATAR_SIZE, TOOLTIP_AVATAR_SIZE), QUICKSKIN_HEAD_TEXTURE_SIZE, QUICKSKIN_HEAD_TEXTURE_SIZE)
            } else if (skin != null) {
                PlayerFaceRenderer.draw(guiGraphics, skin, avatarX, y, TOOLTIP_AVATAR_SIZE)
            } else {
                guiGraphics.fill(avatarX, y, avatarX + TOOLTIP_AVATAR_SIZE, y + TOOLTIP_AVATAR_SIZE, colorWithRenderAlpha(TOOLTIP_AVATAR_FALLBACK_FILL))
                guiGraphics.drawString(font, player.name.take(1).uppercase(), avatarX + 4, y + 4, colorWithRenderAlpha(TEXT_PRIMARY), false)
            }
        }
    }

    private fun quickSkinAvatarTexture(playerId: UUID): ResourceLocation? {
        if (quickSkinAvatarTextures.containsKey(playerId)) return quickSkinAvatarTextures[playerId]
        val texture = runCatching {
            val bytes = DiscordQuickSkinSupport.quickSkinHeadPng(playerId) ?: return@runCatching null
            val image = NativeImage.read(ByteArrayInputStream(bytes))
            val id = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "quickskin/battlepass_avatar/${playerId.toString().replace("-", "_")}")
            Minecraft.getInstance().textureManager.register(id, DynamicTexture(image))
            id
        }.getOrNull()
        quickSkinAvatarTextures[playerId] = texture
        return texture
    }

    private fun completedPlayersFor(slot: MissionSlot): List<BattlepassClientState.PlayerProgress> = BattlepassClientState.players()
        .filter { player -> player.completedMissionKeysByPass[slot.passId]?.contains(slot.entry.key) == true }

    private fun claimedPlayersFor(slot: RewardSlot): List<BattlepassClientState.PlayerProgress> = BattlepassClientState.players()
        .filter { player -> player.claimedByPass[slot.passId]?.contains(slot.tier.xp) == true }

    private fun drawTooltipPanel(guiGraphics: GuiGraphics, origin: Rect, width: Int, height: Int) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, TOOLTIP_BACKGROUND_ALPHA)
        renderNineSlice(guiGraphics, TOOLTIP_CONTAINER_TEXTURE, Rect(origin.x, origin.y, width, height), TOOLTIP_CONTAINER_TEXTURE_SIZE, TOOLTIP_CONTAINER_CORNER_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
    }

    private fun tooltipOrigin(mouseX: Int, mouseY: Int, width: Int, height: Int): Rect {
        val x = (mouseX + TOOLTIP_MOUSE_OFFSET).coerceAtMost(this.width - width - SAFE_EDGE_PADDING).coerceAtLeast(SAFE_EDGE_PADDING)
        val y = (mouseY + TOOLTIP_MOUSE_OFFSET).coerceAtMost(this.height - height - SAFE_EDGE_PADDING).coerceAtLeast(SAFE_EDGE_PADDING)
        return Rect(x, y, width, height)
    }

    private fun Int?.orEmptyWidth(): Int = this ?: 0

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
    private val REWARD_BOX_LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/box_locked.png")
    private val LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
    private val CLAIM_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green.png")
    private val TOOLTIP_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container.png")
    private val CKDM_BOLD_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    private val CKDM_BOLD_LARGE_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_large")
    private val CKDM_BOLD_SMALL_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
    private val UI_BACKGROUND_WIDTH = 2560
    private val UI_BACKGROUND_HEIGHT = 1440
    private val BACKGROUND_PARALLAX_PADDING = 18
    private val BACKGROUND_PARALLAX_MAX_OFFSET = 6.0f
    private val BACKGROUND_PARALLAX_LERP = 0.045f
    private val HEADER_ANIMATION_DELAY_MS = 0
    private val HEADER_BUTTON_ANIMATION_DELAY_MS = 80
    private val MISSIONS_TITLE_ANIMATION_DELAY_MS = 150
    private val MISSION_FILTER_ANIMATION_DELAY_MS = 220
    private val MISSION_LIST_ANIMATION_DELAY_MS = 290
    private val REWARD_ANIMATION_DELAY_MS = 410
    private val FOOTER_ANIMATION_DELAY_MS = 620
    private val MISSION_ROW_STAGGER_MS = 32
    private val REWARD_STAGGER_MS = 34
    private val REWARD_ITEM_BOUNCE_DURATION_MS = 220.0f
    private val REWARD_ITEM_MAX_BOUNCE_SCALE = 1.12f
    private val HOVER_LERP = 0.24f
    private val HOVER_LIFT_OFFSET = 4
    private val PRESS_DOWN_OFFSET = 7
    private val PRESS_ANIMATION_DURATION_MS = 150.0f
    private val LOCK_SHAKE_DURATION_MS = 260.0f
    private val LOCK_SHAKE_WAVES = 3.0f
    private val LOCK_SHAKE_DEGREES = 9.0f
    private val ENTRANCE_SLIDE_DOWN_OFFSET = -10
    private val REWARD_SLIDE_OFFSET = 18
    private val BUTTON_SLIDE_OFFSET = 18
    private val FOOTER_SLIDE_OFFSET = 10
    private val HEADER_SCALE_FROM = 0.92f
    private val REWARD_BOX_TEXTURE_SIZE = 512
    private val LOCKED_TEXTURE_SIZE = 16
    private val LOCKED_ICON_SIZE = 40
    private val CLAIM_BUTTON_TEXTURE_SIZE = 8
    private val CLAIM_BUTTON_CORNER_SIZE = 2
    private val CLAIM_BUTTON_DESTINATION_CORNER_SIZE = 4
    private val TOOLTIP_CONTAINER_TEXTURE_SIZE = 32
    private val TOOLTIP_CONTAINER_CORNER_SIZE = 10
    private val REWARD_HOVER_TINT = 0x44FFFFFF
    private val BUTTON_FILL = 0xCC232A34.toInt()
    private val BUTTON_HOVER_FILL = 0xDD303946.toInt()
    private val BUTTON_DISABLED_FILL = 0x6630353D
    private val TOOLTIP_GOLD = 0xFFFFC857.toInt()
    private val TOOLTIP_AVATAR_FALLBACK_FILL = 0xFF3A414C.toInt()
    private val LOCKED_ITEM_ALPHA = 0.5f
    private val PROGRESS_BACK = 0xFF2C3138.toInt()
    private val PROGRESS_FILL = 0xFF72C66F.toInt()
    private val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private val TEXT_MUTED = 0xFFB8C0CC.toInt()
    private val CKDM_SHADOW_COLOR = 0xAA000000.toInt()
    private val CKDM_SHADOW_OFFSET = 2
    private val SAFE_EDGE_PADDING = 8
    private val CONTENT_PADDING = 10
    private val PANEL_PADDING = 12
    private val HEADER_HEIGHT = 64
    private val HEADER_TITLE_TEXTURE_HEIGHT = 40
    private val FOOTER_HEIGHT = 40
    private val ITEM_STRIP_HEIGHT = 110
    private val ITEM_STRIP_GAP = 8
    private val SLOT_SIZE = 80
    private val PROMINENT_SLOT_SIZE = 96
    private val SLOT_GAP = 8
    private val BASE_ITEM_RENDER_SIZE = 16
    private val ITEM_RENDER_PADDING = 16
    private val LOCK_OVERLAY_Z = 300.0f
    private val REWARD_TIER_PADDING = 8
    private val QUANTITY_PADDING = 8
    private val QUANTITY_TEXT_HEIGHT = 7
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
    private val MISSION_HEADER_HEIGHT = 52
    private val MISSION_TAB_TEXT_HEIGHT = 9
    private val MISSION_TAB_GAP = 12
    private val MISSIONS_SCROLL_STEP = 24.0f
    private val REWARD_SCROLL_STEP = 42.0f
    private val MISSIONS_TRACK_LIMIT = 7
    private val ROW_GAP = 4
    private val SCROLLBAR_GAP = 6
    private val BUTTON_HEIGHT = 20
    private val BACK_BUTTON_WIDTH = 92
    private val CLAIM_ALL_WIDTH = 124
    private val TOOLTIP_PADDING = 14
    private val TOOLTIP_LINE_HEIGHT = 13
    private val TOOLTIP_HEADER_CONTENT_GAP = 11
    private val TOOLTIP_SECTION_GAP = 20
    private val TOOLTIP_MOUSE_OFFSET = 12
    private val TOOLTIP_AVATAR_SIZE = 16
    private val TOOLTIP_AVATAR_GAP = 4
    private val TOOLTIP_MAX_AVATARS = 6
    private val TOOLTIP_Z = 450.0f
    private val TOOLTIP_BACKGROUND_ALPHA = 0.75f
    private val QUICKSKIN_HEAD_TEXTURE_SIZE = 128
    private val SCROLL_LERP = 0.35f
    private val CLAIM_SOUND_PITCH = 1.2f
    private val CLAIM_SOUND_VOLUME = 0.7f
    private val BUTTON_CLICK_SOUND_PITCH = 1.0f
    private val BUTTON_CLICK_SOUND_VOLUME = 0.6f
}
