package dev.gisketch.chowkingdom.battlepass

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.relicroulette.RelicRouletteFeature
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

class BattlepassScreen(initialPassId: String? = null) : Screen(Component.translatable("screen.${ChowKingdomMod.MOD_ID}.battlepass")) {
    private enum class ViewMode { PASS_SELECTION, PASS_DETAIL }

    private enum class MissionFilter(val label: String) {
        ALL("ALL"), DAILY("DAILY"), WEEKLY("WEEKLY"), PERMANENT("CKDM"), COMPLETED("DONE")
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        fun inset(amount: Int): Rect = Rect(x + amount, y + amount, (width - amount * 2).coerceAtLeast(0), (height - amount * 2).coerceAtLeast(0))
        fun offset(dx: Int, dy: Int): Rect = Rect(x + dx, y + dy, width, height)
        fun inflate(amount: Int): Rect = Rect(x - amount, y - amount, width + amount * 2, height + amount * 2)
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

    private data class AvatarLayout(val perRow: Int, val width: Int, val height: Int)

    private data class RewardFlyout(
        val stack: ItemStack,
        val startedAtMs: Long,
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
    )

    private var selectedPassId: String? = initialPassId
    private var viewMode = if (initialPassId == null) ViewMode.PASS_SELECTION else ViewMode.PASS_DETAIL
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
    private val rewardFlyouts: MutableList<RewardFlyout> = mutableListOf()
    private var currentHoverSoundKey: String? = null
    private var previousHoverSoundKey: String? = null
    private var autoScrollKey: String? = null
    private var missionFilter = MissionFilter.ALL
    private var missionFilterRects: List<Pair<Rect, MissionFilter>> = emptyList()
    private var missionsRect = Rect(0, 0, 0, 0)
    private var backButtonRect = Rect(0, 0, 0, 0)
    private var claimAllRect = Rect(0, 0, 0, 0)
    private var playerDollTarget = Rect(0, 0, 0, 0)
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
        currentHoverSoundKey = null
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        rewardScroll = Mth.lerp(SCROLL_LERP, rewardScroll, targetRewardScroll)
        missionsScroll = Mth.lerp(SCROLL_LERP, missionsScroll, targetMissionsScroll)
        ensureSelectedPass()
        if (viewMode == ViewMode.PASS_SELECTION) renderSelection(guiGraphics, mouseX, mouseY) else renderDetail(guiGraphics, mouseX, mouseY)
        renderAnimatedWidgets(guiGraphics, mouseX, mouseY, partialTick)
        finishHoverSound()
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBlurredBackground(partialTick)
        guiGraphics.fill(0, 0, width, height, BACKGROUND_DIM_OVERLAY)
        val targetX = if (width == 0) 0.0f else ((mouseX - width / 2.0f) / (width / 2.0f)).coerceIn(-1.0f, 1.0f) * BACKGROUND_PARALLAX_MAX_OFFSET
        val targetY = if (height == 0) 0.0f else ((mouseY - height / 2.0f) / (height / 2.0f)).coerceIn(-1.0f, 1.0f) * BACKGROUND_PARALLAX_MAX_OFFSET
        backgroundParallaxX = Mth.lerp(BACKGROUND_PARALLAX_LERP, backgroundParallaxX, targetX)
        backgroundParallaxY = Mth.lerp(BACKGROUND_PARALLAX_LERP, backgroundParallaxY, targetY)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(-backgroundParallaxX, -backgroundParallaxY, 0.0f)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, UI_BACKGROUND_ALPHA)
        renderTexture(guiGraphics, UI_BACKGROUND_TEXTURE, Rect(-BACKGROUND_PARALLAX_PADDING, -BACKGROUND_PARALLAX_PADDING, width + BACKGROUND_PARALLAX_PADDING * 2, height + BACKGROUND_PARALLAX_PADDING * 2), UI_BACKGROUND_WIDTH, UI_BACKGROUND_HEIGHT)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
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

        if (viewMode == ViewMode.PASS_DETAIL && backButtonRect.contains(mouseX, mouseY)) {
            returnToSelection()
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
            rewardSlots.filter { slot -> slot.claimable }.forEachIndexed { index, slot ->
                enqueueRewardFlyout(slot, index * REWARD_FLYOUT_STAGGER_MS)
                BattlepassNetwork.claim(selectedPassId ?: return true, slot.tier.xp)
            }
            playClaimSound()
            return true
        }

        rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) }?.let { reward ->
            registerPress(rewardInteractionKey(reward))
            if (reward.claimable) {
                enqueueRewardFlyout(reward, 0)
                BattlepassNetwork.claim(selectedPassId ?: return true, reward.tier.xp)
                playClaimSound()
            } else if (!reward.unlocked) {
                playLockedSound()
            } else {
                playButtonClickSound()
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
        registerHoverSound("pass:${pass.id}", hovered)
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
        val contentBottom = hotbar.y - CONTENT_TO_ITEM_STRIP_GAP
        val availableContentWidth = panel.width - PANEL_PADDING * 2 - DETAIL_COLUMN_GAP * 2
        val missionWidth = availableContentWidth * MISSION_PANEL_WIDTH_PERCENT / 100
        val playerWidth = availableContentWidth * PLAYER_PREVIEW_WIDTH_PERCENT / 100
        val xpWidth = (availableContentWidth - missionWidth - playerWidth).coerceAtLeast(0)
        val missionRect = Rect(panel.x + PANEL_PADDING, contentTop, missionWidth, (contentBottom - contentTop).coerceAtLeast(PLAYER_PREVIEW_MIN_HEIGHT))
        val playerRect = Rect(missionRect.x + missionRect.width + DETAIL_COLUMN_GAP, contentTop, playerWidth, missionRect.height)
        val xpRect = Rect(playerRect.x + playerRect.width + DETAIL_COLUMN_GAP, contentTop, xpWidth, missionRect.height)

        renderMissions(guiGraphics, pass, missionRect, mouseX, mouseY)
        withEntrance(guiGraphics, EntranceStyle(MISSION_LIST_ANIMATION_DELAY_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET)) {
            renderPlayerPreview(guiGraphics, pass, playerRect, currentXp)
            renderXpPanel(guiGraphics, xpRect, currentXp)
        }
        autoCenterCurrentReward(pass, currentXp, hotbar)
        renderRewardHotbar(guiGraphics, hotbar, pass, currentXp, mouseX, mouseY)
        renderClaimAllButton(guiGraphics, mouseX, mouseY)
        renderRewardFlyouts(guiGraphics)
        renderHoveredBattlepassTooltip(guiGraphics, currentXp, mouseX, mouseY)
    }

    private fun renderHeader(guiGraphics: GuiGraphics, panel: Rect, pass: BattlepassPassDefinition) {
        if (!renderPassTitleTexture(guiGraphics, pass, Rect(panel.x + PANEL_PADDING, panel.y + 8, titleWidth(), HEADER_TITLE_TEXTURE_HEIGHT))) {
            drawCkdmShadowedText(guiGraphics, fitText(pass.displayName.uppercase(), titleWidth()), panel.x + PANEL_PADDING, panel.y + 12, TEXT_PRIMARY)
        }
    }

    private fun layoutBackButton(panel: Rect) {
        backButtonRect = Rect(panel.x + panel.width - PANEL_PADDING - BACK_BUTTON_WIDTH, panel.y + 10, BACK_BUTTON_WIDTH, BUTTON_HEIGHT)
        backButton?.visible = false
        backButton?.active = false
    }

    private fun renderMissions(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, rect: Rect, mouseX: Int, mouseY: Int) {
        withEntrance(guiGraphics, EntranceStyle(MISSION_FILTER_ANIMATION_DELAY_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET)) {
            renderFramePanel(guiGraphics, Rect(rect.x, rect.y + MISSION_PANEL_FRAME_TOP_OFFSET, rect.width, rect.height - MISSION_PANEL_FRAME_TOP_OFFSET))
        }
        withEntrance(guiGraphics, EntranceStyle(MISSIONS_TITLE_ANIMATION_DELAY_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET)) {
            drawCkdmHighlightText(guiGraphics, "MISSIONS", rect.x + 8, rect.y + 8, CKDM_BOLD_LARGE_FONT)
        }
        withEntrance(guiGraphics, EntranceStyle(MISSION_FILTER_ANIMATION_DELAY_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET)) {
            renderMissionFilterTabs(guiGraphics, rect, mouseX, mouseY)
        }

        missionsRect = Rect(rect.x + MISSION_PANEL_X_PADDING, rect.y + MISSION_HEADER_HEIGHT, rect.width - MISSION_PANEL_X_PADDING * 2, rect.height - MISSION_HEADER_HEIGHT - 8)
        val missions = visibleMissionEntries(pass)
            .map { entry -> entry to (currentPlayerId()?.let { id -> BattlepassClientState.isMissionCompleted(id, pass.id, entry.key) } == true) }
            .sortedBy { (_, completed) -> completed }
        val missionRowHeight = missionRowHeight(missionsRect.width)
        val missionRowPitch = missionRowHeight + ROW_GAP
        missionsMaxScroll = (missions.size * missionRowPitch + MISSION_LIST_VERTICAL_PADDING * 2 - missionsRect.height).coerceAtLeast(0).toFloat()
        targetMissionsScroll = targetMissionsScroll.coerceIn(0.0f, missionsMaxScroll)
        missionsScroll = missionsScroll.coerceIn(0.0f, missionsMaxScroll)
        missionSlots = emptyList()

        guiGraphics.enableScissor(missionsRect.x, missionsRect.y, missionsRect.x + missionsRect.width, missionsRect.y + missionsRect.height)
        var rowY = missionsRect.y + MISSION_LIST_VERTICAL_PADDING - missionsScroll.toInt()
        if (missions.isEmpty()) {
            guiGraphics.drawString(font, "No missions", missionsRect.x, rowY, TEXT_MUTED, false)
        } else {
            missions.forEachIndexed { index, (entry, completed) ->
                val row = Rect(missionsRect.x, rowY, missionsRect.width, missionRowHeight)
                if (row.y + row.height > missionsRect.y && row.y < missionsRect.y + missionsRect.height) {
                    missionSlots = missionSlots + MissionSlot(row, pass.id, entry, completed)
                    withEntrance(guiGraphics, EntranceStyle(index * MISSION_ROW_STAGGER_MS, offsetY = ENTRANCE_SLIDE_DOWN_OFFSET, timelineStartedAtMs = missionListAnimationStartedAtMs)) {
                        renderMissionRow(guiGraphics, pass.id, entry, row, completed, row.contains(mouseX.toDouble(), mouseY.toDouble()))
                    }
                }
                rowY += missionRowPitch
            }
        }
        guiGraphics.disableScissor()
    }

    private fun renderMissionFilterTabs(guiGraphics: GuiGraphics, parent: Rect, mouseX: Int, mouseY: Int) {
        val y = parent.y + MISSION_FILTER_Y_OFFSET
        var x = parent.x + MISSION_PANEL_X_PADDING
        missionFilterRects = MissionFilter.entries.map { filter ->
            val width = ckdmWidth(filter.label, CKDM_BOLD_FONT)
            val rect = Rect(x, y, width, MISSION_TAB_TEXT_HEIGHT)
            val selected = filter == missionFilter
            val hovered = rect.contains(mouseX.toDouble(), mouseY.toDouble())
            registerHoverSound(missionFilterKey(filter), hovered)
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
        registerHoverSound("mission:$passId:${entry.key}", hovered)
        val progress = missionProgress(passId, entry)
        val tracked = BattlepassTrackedMissions.isTracked(passId, entry.key)
        val titlePrefix = when {
            tracked -> "* "
            else -> ""
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha * if (hovered) MISSION_ROW_HOVER_BACKGROUND_ALPHA else MISSION_ROW_BACKGROUND_ALPHA)
        renderWideNineSlice(guiGraphics, if (hovered) MISSION_ROW_HOVER_TEXTURE else MISSION_ROW_TEXTURE, rect, MISSION_ROW_TEXTURE_WIDTH, MISSION_ROW_TEXTURE_HEIGHT, MISSION_ROW_SOURCE_CORNER_SIZE, MISSION_ROW_DESTINATION_CORNER_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)

        val detailStackHeight = MISSION_ROW_HEADER_TEXT_HEIGHT + MISSION_ROW_TEXT_GAP + MISSION_ROW_SUB_TEXT_HEIGHT + MISSION_ROW_TEXT_GAP + MISSION_ROW_SUB_TEXT_HEIGHT
        val iconSize = MISSION_ROW_ICON_SIZE
        val iconRect = Rect(rect.x + MISSION_ROW_PADDING, rect.y + (rect.height - iconSize) / 2, iconSize, iconSize)
        renderScaledItem(guiGraphics, missionIconStack(entry), iconRect)

        val detailsX = iconRect.x + iconRect.width + MISSION_ROW_COLUMN_GAP
        val detailsWidth = (rect.x + rect.width - MISSION_ROW_PADDING - detailsX).coerceAtLeast(40)
        var detailY = rect.y + (rect.height - detailStackHeight) / 2
        drawQuestTitleText(guiGraphics, fitCkdmText(titlePrefix + missionDescription(passId, entry), detailsWidth, CKDM_BOLD_SMALL_FONT), detailsX, detailY, if (completed) TEXT_MUTED else TEXT_PRIMARY, CKDM_BOLD_SMALL_FONT)
        detailY += MISSION_ROW_HEADER_TEXT_HEIGHT + MISSION_ROW_TEXT_GAP
        renderMissionProgressBar(guiGraphics, Rect(detailsX, detailY, detailsWidth, MISSION_PROGRESS_BAR_HEIGHT), progress, BattlepassMissionService.displayGoal(entry.event, progress) ?: progress.coerceAtLeast(1))
        detailY += MISSION_ROW_SUB_TEXT_HEIGHT + MISSION_ROW_TEXT_GAP
        drawCkdmText(guiGraphics, "XP", detailsX, detailY, MISSION_XP_LABEL_COLOR, CKDM_BOLD_SMALL_FONT)
        drawCkdmText(guiGraphics, "+${missionRewardXp(entry, progress)}", detailsX + ckdmWidth("XP", CKDM_BOLD_SMALL_FONT) + MISSION_XP_VALUE_GAP, detailY, TEXT_PRIMARY, CKDM_BOLD_SMALL_FONT)
    }

    private fun renderPlayerPreview(guiGraphics: GuiGraphics, pass: BattlepassPassDefinition, rect: Rect, currentXp: Int) {
        playerDollTarget = Rect(rect.x + rect.width / 2 - PLAYER_DOLL_TARGET_SIZE / 2, rect.y + rect.height / 2 - PLAYER_DOLL_TARGET_SIZE / 2, PLAYER_DOLL_TARGET_SIZE, PLAYER_DOLL_TARGET_SIZE)
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
    }

    private fun renderXpPanel(guiGraphics: GuiGraphics, rect: Rect, currentXp: Int) {
        renderFramePanel(guiGraphics, rect)
        val level = currentXp / XP_PER_LEVEL
        val nextLevel = level + 1
        val nextLevelXp = nextLevel * XP_PER_LEVEL
        val progressXp = currentXp - level * XP_PER_LEVEL
        val xpToNext = (nextLevelXp - currentXp).coerceAtLeast(0)
        val content = rect.inset(XP_PANEL_PADDING)
        val header = "PASS LEVEL"
        val headerY = content.y + XP_PANEL_TOP_GAP
        drawCkdmText(guiGraphics, fitCkdmText(header, content.width, CKDM_BOLD_LARGE_FONT), content.x + (content.width - ckdmWidth(header, CKDM_BOLD_LARGE_FONT)).coerceAtLeast(0) / 2, headerY, TEXT_PRIMARY, CKDM_BOLD_LARGE_FONT)

        val contentBottom = content.y + content.height
        var footerY = contentBottom - XP_PANEL_BOTTOM_GAP - XP_PANEL_FOOTER_HEIGHT
        drawCkdmText(guiGraphics, "CURRENT XP", content.x + (content.width - ckdmWidth("CURRENT XP", CKDM_BOLD_SMALL_FONT)) / 2, footerY, TEXT_MUTED, CKDM_BOLD_SMALL_FONT)
        footerY += XP_PANEL_FOOTER_LABEL_HEIGHT
        val xpText = "$currentXp/$nextLevelXp XP"
        drawCkdmText(guiGraphics, fitCkdmText(xpText, content.width, CKDM_BOLD_FONT), content.x + (content.width - ckdmWidth(xpText)).coerceAtLeast(0) / 2, footerY, PROGRESS_FILL, CKDM_BOLD_FONT)
        footerY += XP_PANEL_FOOTER_VALUE_HEIGHT + XP_PANEL_XP_BAR_GAP
        renderXpProgressBar(guiGraphics, Rect(content.x, footerY, content.width, XP_PANEL_PROGRESS_HEIGHT), progressXp, XP_PER_LEVEL)
        footerY += XP_PANEL_PROGRESS_HEIGHT + XP_PANEL_FOOTER_GAP
        val nextText = "${xpToNext}XP TO LEVEL $nextLevel"
        drawCkdmText(guiGraphics, fitCkdmText(nextText, content.width, CKDM_BOLD_SMALL_FONT), content.x + (content.width - ckdmWidth(nextText, CKDM_BOLD_SMALL_FONT)).coerceAtLeast(0) / 2, footerY, TEXT_MUTED, CKDM_BOLD_SMALL_FONT)

        val levelAreaTop = headerY + XP_PANEL_HEADER_HEIGHT + XP_PANEL_SECTION_GAP
        val levelAreaBottom = contentBottom - XP_PANEL_BOTTOM_GAP - XP_PANEL_FOOTER_HEIGHT - XP_PANEL_LEVEL_FOOTER_GAP
        val levelAreaHeight = (levelAreaBottom - levelAreaTop).coerceAtLeast(XP_PANEL_LEVEL_MIN_SIZE)
        val levelText = level.toString()
        val levelWidth = (ckdmWidth(levelText, CKDM_BOLD_LARGE_FONT) * XP_PANEL_LEVEL_NUMBER_SCALE).toInt()
        val levelBoxSize = minOf(content.width, levelAreaHeight).coerceAtLeast(XP_PANEL_LEVEL_MIN_SIZE)
        val levelBox = Rect(content.x + (content.width - levelBoxSize) / 2, levelAreaTop + (levelAreaHeight - levelBoxSize) / 2, levelBoxSize, levelBoxSize)
        renderWideNineSlice(guiGraphics, FRAME_PANEL_TEXTURE, levelBox, FRAME_PANEL_TEXTURE_WIDTH, FRAME_PANEL_TEXTURE_HEIGHT, FRAME_PANEL_SOURCE_CORNER_SIZE, FRAME_PANEL_DESTINATION_CORNER_SIZE)
        val levelTextHeight = (font.lineHeight * XP_PANEL_LEVEL_NUMBER_SCALE).toInt()
        drawScaledCkdmText(guiGraphics, levelText, levelBox.x + (levelBox.width - levelWidth) / 2, levelBox.y + (levelBox.height - levelTextHeight) / 2, CKDM_HIGHLIGHT_TEXT_COLOR, CKDM_BOLD_LARGE_FONT, XP_PANEL_LEVEL_NUMBER_SCALE)
    }

    private fun renderXpProgressBar(guiGraphics: GuiGraphics, rect: Rect, progressXp: Int, targetXp: Int) {
        renderTexturedProgressBar(guiGraphics, rect, progressXp.toFloat() / targetXp.coerceAtLeast(1))
    }

    private fun renderMissionProgressBar(guiGraphics: GuiGraphics, rect: Rect, progress: Int, goal: Int) {
        val progressText = "${progress.coerceAtMost(goal)}/$goal"
        val progressTextWidth = ckdmWidth(progressText, CKDM_BOLD_SMALL_FONT)
        val barWidth = (rect.width - progressTextWidth - MISSION_PROGRESS_TEXT_GAP).coerceAtLeast(0)
        renderMissionTexturedProgressBar(guiGraphics, Rect(rect.x, rect.y, barWidth, rect.height), progress.coerceAtMost(goal).toFloat() / goal.coerceAtLeast(1))
        drawCkdmText(guiGraphics, progressText, rect.x + barWidth + MISSION_PROGRESS_TEXT_GAP, rect.y, TEXT_MUTED, CKDM_BOLD_SMALL_FONT)
    }

    private fun renderMissionTexturedProgressBar(guiGraphics: GuiGraphics, rect: Rect, progress: Float) {
        val fillWidth = (rect.width * progress.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, rect.width)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        blitMissionProgressRegion(guiGraphics, PROGRESS_EMPTY_TEXTURE, rect)
        if (fillWidth > 0) blitMissionProgressRegion(guiGraphics, PROGRESS_FILL_TEXTURE, Rect(rect.x, rect.y, fillWidth, rect.height))
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        RenderSystem.disableBlend()
    }

    private fun blitMissionProgressRegion(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect) {
        if (rect.width <= 0 || rect.height <= 0) return
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, PROGRESS_MISSION_SOURCE_X.toFloat(), 0.0f, PROGRESS_MISSION_SOURCE_WIDTH, PROGRESS_TEXTURE_SIZE, PROGRESS_TEXTURE_SIZE, PROGRESS_TEXTURE_SIZE)
    }

    private fun renderTexturedProgressBar(guiGraphics: GuiGraphics, rect: Rect, progress: Float) {
        val fillWidth = (rect.width * progress.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, rect.width)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        renderNineSlice(guiGraphics, PROGRESS_EMPTY_TEXTURE, rect, PROGRESS_TEXTURE_SIZE, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_DESTINATION_CORNER_SIZE)
        if (fillWidth > 0) renderNineSlice(guiGraphics, PROGRESS_FILL_TEXTURE, Rect(rect.x, rect.y, fillWidth, rect.height), PROGRESS_TEXTURE_SIZE, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_DESTINATION_CORNER_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
    }

    private fun renderRewardHotbar(guiGraphics: GuiGraphics, hotbar: Rect, pass: BattlepassPassDefinition, currentXp: Int, mouseX: Int, mouseY: Int) {
        val tiers = pass.progression.sortedBy { tier -> tier.xp }
        val maxScroll = maxScroll(rewardContentWidth(pass), hotbar.width)
        targetRewardScroll = targetRewardScroll.coerceIn(0.0f, maxScroll)
        rewardScroll = rewardScroll.coerceIn(0.0f, maxScroll)
        rewardSlots = emptyList()
        val playerId = currentPlayerId()

        guiGraphics.enableScissor(hotbar.x + 4, hotbar.y - CLAIM_TEXT_SCISSOR_TOP_PADDING, hotbar.x + hotbar.width - 4, hotbar.y + hotbar.height - 4)
        var nextX = hotbar.x + 10 - rewardScroll.toInt()
        var visibleSlotIndex = 0
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
                    val entrance = EntranceStyle(REWARD_ANIMATION_DELAY_MS + visibleSlotIndex * REWARD_STAGGER_MS, offsetX = REWARD_SLIDE_OFFSET)
                    visibleSlotIndex++
                    renderRewardSlot(guiGraphics, slot, currentXp, hovered, entrance, interactionYOffset(rewardInteractionKey(slot), hovered).toFloat() + if (slot.claimable) claimableFloatOffset() else 0.0f)
                    tiers.getOrNull(index + 1)?.rewards?.firstOrNull()?.let { nextReward ->
                        withEntrance(guiGraphics, entrance) {
                            val nextSize = slotSize(nextReward.let(::isProminentReward))
                            val nextRect = Rect(nextX + slotSize + SLOT_GAP, hotbar.y + (hotbar.height - nextSize) / 2, nextSize, nextSize)
                            renderRewardTierConnector(guiGraphics, slot.rect, nextRect, tier.xp, tiers[index + 1].xp, currentXp)
                        }
                    }
                }
            }
            nextX += slotSize + SLOT_GAP
        }
        guiGraphics.disableScissor()
    }

    private fun renderRewardSlot(guiGraphics: GuiGraphics, slot: RewardSlot, currentXp: Int, hovered: Boolean, entranceStyle: EntranceStyle? = null, interactionOffsetY: Float = 0.0f) {
        registerHoverSound(rewardInteractionKey(slot), hovered)
        if (entranceStyle != null) {
            val boxProgress = entranceProgress(entranceStyle)
            val dx = (entranceStyle.offsetX * (1.0f - boxProgress)).toInt()
            val dy = (entranceStyle.offsetY * (1.0f - boxProgress)).toInt()
            val previousAlpha = renderAlpha
            renderAlpha *= boxProgress
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
            drawRewardTierLabel(guiGraphics, slot.copy(rect = slot.rect.offset(dx, dy)))
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate(0.0f, interactionOffsetY, 0.0f)
            renderRewardSlotContent(guiGraphics, slot.copy(rect = slot.rect.offset(dx, dy)), currentXp, hovered, rewardItemEntranceScale(entranceStyle))
            guiGraphics.pose().popPose()
            renderAlpha = previousAlpha
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
            return
        }
        drawRewardTierLabel(guiGraphics, slot)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0.0f, interactionOffsetY, 0.0f)
        renderRewardSlotContent(guiGraphics, slot, currentXp, hovered, 1.0f)
        guiGraphics.pose().popPose()
    }

    private fun renderRewardSlotContent(guiGraphics: GuiGraphics, slot: RewardSlot, currentXp: Int, hovered: Boolean, itemEntranceScale: Float) {
        val boxTexture = when {
            isProminentReward(slot.reward) -> REWARD_ITEM_PROMINENT_TEXTURE
            !slot.unlocked -> REWARD_ITEM_LOCKED_TEXTURE
            slot.claimed -> REWARD_ITEM_CLAIMED_TEXTURE
            else -> REWARD_ITEM_CLAIMABLE_TEXTURE
        }
        renderWideNineSlice(guiGraphics, boxTexture, slot.rect, REWARD_ITEM_TEXTURE_WIDTH, REWARD_ITEM_TEXTURE_HEIGHT, REWARD_ITEM_SOURCE_CORNER_SIZE, REWARD_ITEM_DESTINATION_CORNER_SIZE)
        if (slot.current) {
            renderTexturedProgressBar(guiGraphics, Rect(slot.rect.x, slot.rect.y + slot.rect.height - REWARD_PROGRESS_BAR_BOTTOM_OFFSET, slot.rect.width, REWARD_PROGRESS_BAR_HEIGHT), progressFor(slot, currentXp))
        }
        renderRewardItemSprite(guiGraphics, slot.stack, slot.rect, !slot.unlocked, itemEntranceScale, if (slot.claimable && itemEntranceScale >= 1.0f) claimableItemShakeDegrees(slot) else 0.0f)
        if (itemEntranceScale <= 0.0f) return
        if (slot.reward.quantity > 1) {
            val quantity = compactQuantity(slot.reward.quantity)
            drawCkdmShadowedText(guiGraphics, quantity, slot.rect.x + slot.rect.width - ckdmWidth(quantity, CKDM_BOLD_SMALL_FONT) - QUANTITY_PADDING, slot.rect.y + slot.rect.height - QUANTITY_TEXT_HEIGHT - QUANTITY_PADDING, TEXT_PRIMARY, CKDM_BOLD_SMALL_FONT)
        }
        drawRewardStatusLabel(guiGraphics, slot)
        if (!slot.unlocked) {
            renderLockedOverlay(guiGraphics, slot.rect, lockShakeDegrees(rewardInteractionKey(slot)))
        }
    }

    private fun drawRewardTierLabel(guiGraphics: GuiGraphics, slot: RewardSlot) {
        val text = slot.tierNumber.toString()
        val scale = if (isProminentReward(slot.reward)) REWARD_TIER_LABEL_PROMINENT_SCALE else REWARD_TIER_LABEL_SCALE
        val width = (ckdmWidth(text, CKDM_BOLD_SMALL_FONT) * scale).toInt()
        val y = slot.rect.y - REWARD_TIER_LABEL_BOTTOM_GAP - (REWARD_TIER_LABEL_HEIGHT * scale).toInt()
        val color = if (slot.claimable) TOOLTIP_GOLD else TEXT_PRIMARY
        drawScaledCkdmShadowedText(guiGraphics, text, slot.rect.x + (slot.rect.width - width) / 2, y, color, CKDM_SHADOW_COLOR, CKDM_SHADOW_OFFSET, CKDM_BOLD_SMALL_FONT, scale)
    }

    private fun drawRewardStatusLabel(guiGraphics: GuiGraphics, slot: RewardSlot) {
        val text = when {
            slot.claimed -> "CLAIMED"
            slot.claimable -> "CLAIM"
            else -> return
        }
        val color = if (slot.claimed) TEXT_MUTED else TOOLTIP_GOLD
        drawCkdmShadowedText(guiGraphics, text, slot.rect.x + (slot.rect.width - ckdmWidth(text, CKDM_BOLD_SMALL_FONT)) / 2, slot.rect.y + slot.rect.height + REWARD_STATUS_LABEL_TOP_GAP, color, CKDM_BOLD_SMALL_FONT)
    }

    private fun renderRewardTierConnector(guiGraphics: GuiGraphics, from: Rect, to: Rect, fromXp: Int, toXp: Int, currentXp: Int) {
        val x = from.x + from.width / 2 + REWARD_TIER_CONNECTOR_SIDE_PADDING
        val endX = to.x + to.width / 2 - REWARD_TIER_CONNECTOR_SIDE_PADDING
        val width = (endX - x).coerceAtLeast(0)
        if (width <= 0) return
        val span = (toXp - fromXp).coerceAtLeast(1)
        val progress = ((currentXp - fromXp).toFloat() / span).coerceIn(0.0f, 1.0f)
        renderMissionTexturedProgressBar(guiGraphics, Rect(x, from.y - REWARD_TIER_CONNECTOR_BOTTOM_GAP, width, REWARD_TIER_CONNECTOR_HEIGHT), progress)
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

    private fun renderClaimPrompt(guiGraphics: GuiGraphics, rect: Rect) {
        val text = "CLAIM"
        drawCkdmShadowedText(guiGraphics, text, rect.x + (rect.width - ckdmWidth(text, CKDM_BOLD_CLAIM_FONT)) / 2, rect.y - CLAIM_TEXT_TOP_OFFSET, TEXT_PRIMARY, CKDM_BOLD_CLAIM_FONT)
    }

    private fun claimableFloatOffset(): Float = Mth.sin(Util.getMillis().toFloat() / CLAIM_TEXT_FLOAT_PERIOD_MS * Math.PI.toFloat() * 2.0f) * CLAIM_TEXT_FLOAT_AMPLITUDE

    private fun claimableItemShakeDegrees(slot: RewardSlot): Float {
        val elapsed = ((Util.getMillis() + slot.tierNumber * CLAIMABLE_SHAKE_PHASE_OFFSET_MS) % CLAIMABLE_SHAKE_PERIOD_MS).toFloat()
        if (elapsed > CLAIMABLE_SHAKE_ACTIVE_MS) return 0.0f
        val fade = 1.0f - (elapsed / CLAIMABLE_SHAKE_ACTIVE_MS).coerceIn(0.0f, 1.0f)
        return Mth.sin(elapsed / CLAIMABLE_SHAKE_ACTIVE_MS * CLAIMABLE_SHAKE_WAVES * Math.PI.toFloat() * 2.0f) * CLAIMABLE_SHAKE_DEGREES * fade
    }

    private fun enqueueRewardFlyout(slot: RewardSlot, delayMs: Int) {
        val from = rewardItemRenderRect(slot.rect)
        val target = playerDollTarget.takeIf { rect -> rect.width > 0 && rect.height > 0 } ?: Rect(width / 2, height / 2, PLAYER_DOLL_TARGET_SIZE, PLAYER_DOLL_TARGET_SIZE)
        rewardFlyouts += RewardFlyout(
            slot.stack.copy(),
            Util.getMillis() + delayMs,
            from.x + from.width / 2.0f,
            from.y + from.height / 2.0f,
            target.x + target.width / 2.0f,
            target.y + target.height / 2.0f,
        )
    }

    private fun renderRewardFlyouts(guiGraphics: GuiGraphics) {
        val now = Util.getMillis()
        rewardFlyouts.removeIf { flyout -> now - flyout.startedAtMs > REWARD_FLYOUT_DURATION_MS }
        if (rewardFlyouts.isEmpty()) return
        guiGraphics.flush()
        RenderSystem.disableDepthTest()
        rewardFlyouts.forEach { flyout ->
            val elapsed = (now - flyout.startedAtMs).toFloat()
            if (elapsed < 0.0f) return@forEach
            val progress = (elapsed / REWARD_FLYOUT_DURATION_MS).coerceIn(0.0f, 1.0f)
            val eased = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress)
            val arc = Mth.sin(progress * Math.PI.toFloat()) * REWARD_FLYOUT_ARC_HEIGHT
            val x = flyout.fromX + (flyout.toX - flyout.fromX) * eased
            val y = flyout.fromY + (flyout.toY - flyout.fromY) * eased - arc
            val scale = REWARD_FLYOUT_SCALE_FROM + (REWARD_FLYOUT_SCALE_TO - REWARD_FLYOUT_SCALE_FROM) * eased
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate(x, y, REWARD_FLYOUT_Z)
            guiGraphics.pose().scale(scale, scale, 1.0f)
            guiGraphics.pose().translate(-BASE_ITEM_RENDER_SIZE / 2.0f, -BASE_ITEM_RENDER_SIZE / 2.0f, 0.0f)
            guiGraphics.renderItem(flyout.stack, 0, 0)
            guiGraphics.pose().popPose()
        }
        guiGraphics.flush()
        RenderSystem.enableDepthTest()
    }

    private fun renderItemSprite(guiGraphics: GuiGraphics, stack: ItemStack, x: Int, y: Int) {
        guiGraphics.renderItem(stack, x, y)
    }

    private fun renderScaledItem(guiGraphics: GuiGraphics, stack: ItemStack, rect: Rect) {
        val scale = rect.width / BASE_ITEM_RENDER_SIZE.toFloat()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(rect.x.toFloat(), rect.y.toFloat(), 0.0f)
        guiGraphics.pose().scale(scale, scale, 1.0f)
        guiGraphics.renderItem(stack, 0, 0)
        guiGraphics.pose().popPose()
    }

    private fun renderRewardItemSprite(guiGraphics: GuiGraphics, stack: ItemStack, rect: Rect, locked: Boolean, itemEntranceScale: Float, rotationDegrees: Float = 0.0f) {
        if (itemEntranceScale <= 0.0f) return
        val itemRect = rewardItemRenderRect(rect)
        val scale = itemRect.width / BASE_ITEM_RENDER_SIZE
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(itemRect.x + itemRect.width / 2.0f, itemRect.y + itemRect.height / 2.0f, 0.0f)
        if (rotationDegrees != 0.0f) guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees))
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
        claimAllClaimableCount = selectedPass()?.let { pass -> claimableRewardCount(pass) } ?: 0
        claimAllRect = Rect((footer.x + footer.width - CONTENT_PADDING - CLAIM_ALL_WIDTH).coerceAtLeast(footer.x + CONTENT_PADDING), footer.y + (footer.height - BUTTON_HEIGHT) / 2, CLAIM_ALL_WIDTH, BUTTON_HEIGHT)
        claimAllButton?.visible = false
        withEntrance(guiGraphics, EntranceStyle(FOOTER_ANIMATION_DELAY_MS, offsetY = FOOTER_SLIDE_OFFSET)) {
            val active = claimAllClaimableCount > 0
            val hovered = active && claimAllRect.contains(mouseX.toDouble(), mouseY.toDouble())
            registerHoverSound("claim_all", hovered)
            val text = if (active) "Claim All ($claimAllClaimableCount)" else "Claim All"
            renderBattlepassButton(guiGraphics, claimAllRect, text, active, hovered, if (active) CLAIM_BUTTON_TEXTURE else BACK_BUTTON_TEXTURE, if (active) CLAIM_BUTTON_HOVER_TEXTURE else BACK_BUTTON_HOVER_TEXTURE)
        }
    }

    private fun claimableRewardCount(pass: BattlepassPassDefinition): Int {
        val playerId = currentPlayerId() ?: return 0
        val currentXp = xpFor(playerId, pass.id)
        return pass.progression.count { tier -> currentXp >= tier.xp && !isClaimed(playerId, pass.id, tier.xp) }
    }

    private fun renderAnimatedWidgets(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (viewMode != ViewMode.PASS_DETAIL) {
            renderables.forEach { renderable -> renderable.render(guiGraphics, mouseX, mouseY, partialTick) }
            return
        }
        withEntrance(guiGraphics, EntranceStyle(HEADER_BUTTON_ANIMATION_DELAY_MS, offsetX = BUTTON_SLIDE_OFFSET)) {
            val hovered = backButtonRect.contains(mouseX.toDouble(), mouseY.toDouble())
            registerHoverSound("back", hovered)
            renderBattlepassButton(guiGraphics, backButtonRect, "Back", active = true, hovered = hovered, BACK_BUTTON_TEXTURE, BACK_BUTTON_HOVER_TEXTURE)
        }
    }

    private fun renderBattlepassButton(guiGraphics: GuiGraphics, rect: Rect, text: String, active: Boolean, hovered: Boolean, baseTexture: ResourceLocation, hoverTexture: ResourceLocation) {
        val texture = if (active && hovered) hoverTexture else baseTexture
        val renderRect = if (active && hovered) rect.inflate(CLAIM_BUTTON_HOVER_BORDER_PADDING) else rect
        val textureSize = if (active && hovered) CLAIM_BUTTON_HOVER_TEXTURE_SIZE else CLAIM_BUTTON_TEXTURE_SIZE
        val sourceCorner = if (active && hovered) CLAIM_BUTTON_HOVER_CORNER_SIZE else CLAIM_BUTTON_CORNER_SIZE
        val destinationCorner = if (active && hovered) CLAIM_BUTTON_HOVER_DESTINATION_CORNER_SIZE else CLAIM_BUTTON_DESTINATION_CORNER_SIZE
        renderNineSlice(guiGraphics, texture, renderRect, textureSize, sourceCorner, destinationCorner)
        if (!active) guiGraphics.fill(renderRect.x, renderRect.y, renderRect.x + renderRect.width, renderRect.y + renderRect.height, colorWithRenderAlpha(BUTTON_DISABLED_FILL))
        drawCkdmText(guiGraphics, text, rect.x + (rect.width - ckdmWidth(text)) / 2, rect.y + 6, if (active) TEXT_PRIMARY else TEXT_MUTED)
    }

    private fun returnToSelection() {
        viewMode = ViewMode.PASS_SELECTION
        rewardScroll = 0.0f
        targetRewardScroll = 0.0f
        missionsScroll = 0.0f
        targetMissionsScroll = 0.0f
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

    private fun renderFramePanel(guiGraphics: GuiGraphics, rect: Rect) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        renderWideNineSlice(guiGraphics, FRAME_PANEL_TEXTURE, rect, FRAME_PANEL_TEXTURE_WIDTH, FRAME_PANEL_TEXTURE_HEIGHT, FRAME_PANEL_SOURCE_CORNER_SIZE, FRAME_PANEL_DESTINATION_CORNER_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
    }

    private fun renderWideNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int, sourceCorner: Int, destinationCorner: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        val edgeX = textureWidth - sourceCorner
        val edgeY = textureHeight - sourceCorner
        val middleWidth = textureWidth - sourceCorner * 2
        val middleHeight = textureHeight - sourceCorner * 2
        val innerWidth = (rect.width - destinationCorner * 2).coerceAtLeast(0)
        val innerHeight = (rect.height - destinationCorner * 2).coerceAtLeast(0)
        blitWideRegion(guiGraphics, texture, Rect(rect.x, rect.y, destinationCorner, destinationCorner), 0, 0, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blitWideRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y, innerWidth, destinationCorner), sourceCorner, 0, middleWidth, sourceCorner, textureWidth, textureHeight)
        blitWideRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y, destinationCorner, destinationCorner), edgeX, 0, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blitWideRegion(guiGraphics, texture, Rect(rect.x, rect.y + destinationCorner, destinationCorner, innerHeight), 0, sourceCorner, sourceCorner, middleHeight, textureWidth, textureHeight)
        blitWideRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + destinationCorner, innerWidth, innerHeight), sourceCorner, sourceCorner, middleWidth, middleHeight, textureWidth, textureHeight)
        blitWideRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y + destinationCorner, destinationCorner, innerHeight), edgeX, sourceCorner, sourceCorner, middleHeight, textureWidth, textureHeight)
        blitWideRegion(guiGraphics, texture, Rect(rect.x, rect.y + rect.height - destinationCorner, destinationCorner, destinationCorner), 0, edgeY, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blitWideRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + rect.height - destinationCorner, innerWidth, destinationCorner), sourceCorner, edgeY, middleWidth, sourceCorner, textureWidth, textureHeight)
        blitWideRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y + rect.height - destinationCorner, destinationCorner, destinationCorner), edgeX, edgeY, sourceCorner, sourceCorner, textureWidth, textureHeight)
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

    private fun blitWideRegion(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int, textureWidth: Int, textureHeight: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, sourceX.toFloat(), sourceY.toFloat(), sourceWidth, sourceHeight, textureWidth, textureHeight)
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
        drawCkdmShadowedText(guiGraphics, text, x, y, color, CKDM_SHADOW_COLOR, CKDM_SHADOW_OFFSET, fontId)
    }

    private fun drawCkdmHighlightText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, fontId: ResourceLocation = CKDM_BOLD_FONT) {
        drawCkdmShadowedText(guiGraphics, text, x, y, CKDM_HIGHLIGHT_TEXT_COLOR, CKDM_HIGHLIGHT_SHADOW_COLOR, CKDM_HIGHLIGHT_SHADOW_OFFSET, fontId)
    }

    private fun drawCkdmShadowedText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, shadowColor: Int, shadowOffset: Int, fontId: ResourceLocation = CKDM_BOLD_FONT) {
        val component = ckdmText(text, fontId)
        guiGraphics.drawString(font, component, x + shadowOffset, y + shadowOffset, colorWithRenderAlpha(shadowColor), false)
        guiGraphics.drawString(font, component, x, y, colorWithRenderAlpha(color), false)
    }

    private fun drawScaledCkdmShadowedText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, shadowColor: Int, shadowOffset: Int, fontId: ResourceLocation, scale: Float) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat(), 0.0f)
        guiGraphics.pose().scale(scale, scale, 1.0f)
        drawCkdmShadowedText(guiGraphics, text, 0, 0, color, shadowColor, shadowOffset, fontId)
        guiGraphics.pose().popPose()
    }

    private fun drawScaledCkdmText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, fontId: ResourceLocation, scale: Float) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat(), 0.0f)
        guiGraphics.pose().scale(scale, scale, 1.0f)
        drawCkdmText(guiGraphics, text, 0, 0, color, fontId)
        guiGraphics.pose().popPose()
    }

    private fun drawQuestTitleText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, fontId: ResourceLocation = CKDM_BOLD_FONT) {
        var cursorX = x
        QUEST_TITLE_NUMBER_REGEX.findAll(text).fold(0) { index, match ->
            val before = text.substring(index, match.range.first)
            if (before.isNotEmpty()) {
                drawCkdmShadowedText(guiGraphics, before, cursorX, y, color, fontId)
                cursorX += ckdmWidth(before, fontId)
            }
            val number = match.value
            drawCkdmHighlightText(guiGraphics, number, cursorX, y, fontId)
            cursorX += ckdmWidth(number, fontId)
            match.range.last + 1
        }.let { index ->
            val rest = text.substring(index)
            if (rest.isNotEmpty()) drawCkdmShadowedText(guiGraphics, rest, cursorX, y, color, fontId)
        }
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
        val avatarLayout = avatarLayout(completedPlayers.size)
        val width = listOf(
            ckdmWidth(title),
            ckdmWidth(progressText, CKDM_BOLD_SMALL_FONT),
            avatarLayout.width,
        ).maxOrNull().orEmptyWidth() + TOOLTIP_PADDING * 2
        val avatarHeight = if (completedPlayers.isEmpty()) 0 else TOOLTIP_AVATAR_FOOTER_GAP + avatarLayout.height
        val height = TOOLTIP_PADDING * 2 + TOOLTIP_HEADER_CONTENT_GAP + TOOLTIP_SMALL_TEXT_HEIGHT + avatarHeight
        val origin = tooltipOrigin(mouseX, mouseY, width, height)
        var textY = origin.y + TOOLTIP_PADDING
        drawTooltipPanel(guiGraphics, origin, width, height)
        drawCkdmShadowedText(guiGraphics, title, origin.x + TOOLTIP_PADDING, textY, TEXT_PRIMARY)
        textY += TOOLTIP_HEADER_CONTENT_GAP
        drawCkdmText(guiGraphics, progressText, origin.x + TOOLTIP_PADDING, textY, TOOLTIP_GOLD, CKDM_BOLD_SMALL_FONT)
        textY += TOOLTIP_SMALL_TEXT_HEIGHT + TOOLTIP_AVATAR_FOOTER_GAP
        renderCompletedPlayerAvatars(guiGraphics, completedPlayers, origin.x + TOOLTIP_PADDING, textY, width - TOOLTIP_PADDING * 2)
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
        val avatarLayout = avatarLayout(claimedPlayers.size)
        val width = listOf(ckdmWidth(title), ckdmWidth(detail, CKDM_BOLD_SMALL_FONT), avatarLayout.width).maxOrNull().orEmptyWidth() + TOOLTIP_PADDING * 2
        val avatarHeight = if (claimedPlayers.isEmpty()) 0 else TOOLTIP_AVATAR_FOOTER_GAP + avatarLayout.height
        val detailHeight = if (detail.isBlank()) 0 else TOOLTIP_LINE_HEIGHT
        val height = TOOLTIP_PADDING * 2 + TOOLTIP_LINE_HEIGHT + detailHeight + avatarHeight
        val origin = tooltipOrigin(mouseX, mouseY, width, height)
        drawTooltipPanel(guiGraphics, origin, width, height)
        drawCkdmShadowedText(guiGraphics, title, origin.x + TOOLTIP_PADDING, origin.y + TOOLTIP_PADDING, TEXT_PRIMARY)
        if (detail.isNotBlank()) drawCkdmText(guiGraphics, detail, origin.x + TOOLTIP_PADDING, origin.y + TOOLTIP_PADDING + TOOLTIP_LINE_HEIGHT, TOOLTIP_GOLD, CKDM_BOLD_SMALL_FONT)
        if (claimedPlayers.isNotEmpty()) renderCompletedPlayerAvatars(guiGraphics, claimedPlayers, origin.x + TOOLTIP_PADDING, origin.y + TOOLTIP_PADDING + TOOLTIP_LINE_HEIGHT + TOOLTIP_AVATAR_FOOTER_GAP, width - TOOLTIP_PADDING * 2)
    }

    private fun renderCompletedPlayerAvatars(guiGraphics: GuiGraphics, players: List<BattlepassClientState.PlayerProgress>, x: Int, y: Int, maxWidth: Int) {
        if (players.isEmpty()) {
            return
        }
        val connection = Minecraft.getInstance().connection
        val layout = avatarLayout(players.size, maxWidth)
        players.forEachIndexed { index, player ->
            val column = index % layout.perRow
            val row = index / layout.perRow
            val avatarX = x + column * (TOOLTIP_AVATAR_SIZE + TOOLTIP_AVATAR_GAP)
            val avatarY = y + row * (TOOLTIP_AVATAR_SIZE + TOOLTIP_AVATAR_GAP)
            val quickSkinTexture = quickSkinAvatarTexture(player.uuid)
            val skin = connection?.getPlayerInfo(player.uuid)?.skin
            if (quickSkinTexture != null) {
                renderTexture(guiGraphics, quickSkinTexture, Rect(avatarX, avatarY, TOOLTIP_AVATAR_SIZE, TOOLTIP_AVATAR_SIZE), QUICKSKIN_HEAD_TEXTURE_SIZE, QUICKSKIN_HEAD_TEXTURE_SIZE)
            } else if (skin != null) {
                PlayerFaceRenderer.draw(guiGraphics, skin, avatarX, avatarY, TOOLTIP_AVATAR_SIZE)
            } else {
                guiGraphics.fill(avatarX, avatarY, avatarX + TOOLTIP_AVATAR_SIZE, avatarY + TOOLTIP_AVATAR_SIZE, colorWithRenderAlpha(TOOLTIP_AVATAR_FALLBACK_FILL))
                guiGraphics.drawString(font, player.name.take(1).uppercase(), avatarX + 3, avatarY + 2, colorWithRenderAlpha(TEXT_PRIMARY), false)
            }
        }
    }

    private fun avatarLayout(count: Int, maxWidth: Int = TOOLTIP_AVATAR_CONTENT_WIDTH): AvatarLayout {
        if (count <= 0) return AvatarLayout(1, 0, 0)
        val perRow = ((maxWidth + TOOLTIP_AVATAR_GAP) / (TOOLTIP_AVATAR_SIZE + TOOLTIP_AVATAR_GAP)).coerceAtLeast(1)
        val rows = (count + perRow - 1) / perRow
        val firstRowCount = count.coerceAtMost(perRow)
        val width = firstRowCount * TOOLTIP_AVATAR_SIZE + (firstRowCount - 1).coerceAtLeast(0) * TOOLTIP_AVATAR_GAP
        val height = rows * TOOLTIP_AVATAR_SIZE + (rows - 1).coerceAtLeast(0) * TOOLTIP_AVATAR_GAP
        return AvatarLayout(perRow, width, height)
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

    private fun completedPlayersFor(slot: MissionSlot): List<BattlepassClientState.PlayerProgress> {
        val players = BattlepassClientState.players().filter { player -> player.completedMissionKeysByPass[slot.passId]?.contains(slot.entry.key) == true }
        if (players.isNotEmpty() || !slot.completed) return players
        return selfPlayerProgress()?.let(::listOf).orEmpty()
    }

    private fun claimedPlayersFor(slot: RewardSlot): List<BattlepassClientState.PlayerProgress> {
        val players = BattlepassClientState.players().filter { player -> player.claimedByPass[slot.passId]?.contains(slot.tier.xp) == true }
        if (players.isNotEmpty() || !slot.claimed) return players
        return selfPlayerProgress()?.let(::listOf).orEmpty()
    }

    private fun selfPlayerProgress(): BattlepassClientState.PlayerProgress? {
        val player = Minecraft.getInstance().player ?: return null
        return BattlepassClientState.PlayerProgress(player.uuid, player.gameProfile.name, emptyMap(), emptyMap(), emptyMap(), emptyMap(), 0, 0, 0, 0, 0L, 0L)
    }

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
        val itemId = if (RelicRouletteFeature.isRelicTokenReward(reward.type)) RelicRouletteFeature.tokenItemIdForReward(reward.item, reward.data["pool"]) else reward.item
        val item = runCatching { ResourceLocation.parse(itemId) }.getOrNull()?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER) } ?: Items.BARRIER
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

    private fun missionProgressText(entry: BattlepassMissionEntry, progress: Int): String {
        val goal = BattlepassMissionService.displayGoal(entry.event, progress)
        return if (goal != null) "PROGRESS ${progress.coerceAtMost(goal)}/$goal" else "PROGRESS $progress"
    }

    private fun missionRewardXp(entry: BattlepassMissionEntry, progress: Int): Int {
        val event = entry.event
        if (BattlepassMissionService.isProgressive(event)) {
            val index = event.progressGoals.indexOfFirst { goal -> progress < goal }.takeIf { value -> value >= 0 } ?: (event.progressGoals.size - 1).coerceAtLeast(0)
            return event.progressXp.getOrNull(index) ?: event.xp
        }
        return event.xp.takeIf { xp -> xp > 0 } ?: event.progressXp.firstOrNull() ?: event.xpCap
    }

    private fun missionIconStack(entry: BattlepassMissionEntry): ItemStack = BattlepassMissionIcons.stack(entry)

    private fun renderMissionCompletedColumn(guiGraphics: GuiGraphics, slot: MissionSlot, x: Int, y: Int, width: Int, height: Int) {
        val players = completedPlayersFor(slot)
        if (players.isEmpty()) return
        val label = "COMPLETED BY"
        val labelWidth = ckdmWidth(label, CKDM_BOLD_SMALL_FONT)
        val avatarCount = players.size.coerceAtMost(MISSION_ROW_MAX_AVATARS)
        val overflow = (players.size - avatarCount).coerceAtLeast(0)
        val overflowText = if (overflow > 0) "+$overflow" else ""
        val avatarsWidth = if (avatarCount == 0) 0 else avatarCount * MISSION_ROW_AVATAR_SIZE + (avatarCount - 1) * MISSION_ROW_AVATAR_GAP
        val overflowWidth = if (overflowText.isBlank()) 0 else MISSION_ROW_AVATAR_GAP + ckdmWidth(overflowText, CKDM_BOLD_SMALL_FONT)
        val rowWidth = avatarsWidth + overflowWidth
        val stackHeight = MISSION_ROW_SUB_TEXT_HEIGHT + MISSION_ROW_COMPLETED_GAP + MISSION_ROW_AVATAR_SIZE
        val labelX = x + width - labelWidth
        val labelY = y + (height - stackHeight) / 2
        drawCkdmText(guiGraphics, label, labelX, labelY, TEXT_MUTED, CKDM_BOLD_SMALL_FONT)
        var avatarX = x + width - rowWidth
        val avatarY = labelY + MISSION_ROW_SUB_TEXT_HEIGHT + MISSION_ROW_COMPLETED_GAP
        players.take(avatarCount).forEach { player ->
            renderPlayerAvatar(guiGraphics, player, avatarX, avatarY, MISSION_ROW_AVATAR_SIZE)
            avatarX += MISSION_ROW_AVATAR_SIZE + MISSION_ROW_AVATAR_GAP
        }
        if (overflowText.isNotBlank()) drawCkdmText(guiGraphics, overflowText, avatarX, avatarY + (MISSION_ROW_AVATAR_SIZE - MISSION_ROW_SUB_TEXT_HEIGHT) / 2, TEXT_PRIMARY, CKDM_BOLD_SMALL_FONT)
    }

    private fun renderPlayerAvatar(guiGraphics: GuiGraphics, player: BattlepassClientState.PlayerProgress, x: Int, y: Int, size: Int) {
        val connection = Minecraft.getInstance().connection
        val quickSkinTexture = quickSkinAvatarTexture(player.uuid)
        val skin = connection?.getPlayerInfo(player.uuid)?.skin
        if (quickSkinTexture != null) {
            renderTexture(guiGraphics, quickSkinTexture, Rect(x, y, size, size), QUICKSKIN_HEAD_TEXTURE_SIZE, QUICKSKIN_HEAD_TEXTURE_SIZE)
        } else if (skin != null) {
            PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size)
        } else {
            guiGraphics.fill(x, y, x + size, y + size, colorWithRenderAlpha(TOOLTIP_AVATAR_FALLBACK_FILL))
            guiGraphics.drawString(font, player.name.take(1).uppercase(), x + 3, y + 2, colorWithRenderAlpha(TEXT_PRIMARY), false)
        }
    }

    private fun missionRowHeight(rowWidth: Int): Int = (rowWidth / MISSION_ROW_ASPECT_WIDTH).toInt().coerceAtLeast(MISSION_ROW_MIN_HEIGHT)

    private fun fitCkdmText(text: String, maxWidth: Int, fontId: ResourceLocation = CKDM_BOLD_FONT): String {
        if (ckdmWidth(text, fontId) <= maxWidth) return text
        val suffix = "..."
        var trimmed = text
        while (trimmed.isNotEmpty() && ckdmWidth(trimmed + suffix, fontId) > maxWidth) trimmed = trimmed.dropLast(1)
        return trimmed + suffix
    }

    private fun rewardContentWidth(pass: BattlepassPassDefinition): Int = pass.progression.sumOf { tier ->
        val reward = tier.rewards.firstOrNull()
        slotSize(reward?.let(::isProminentReward) == true) + SLOT_GAP
    } + 20

    private fun slotSize(prominent: Boolean): Int = SLOT_SIZE

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
        return Rect(panel.x + PANEL_PADDING, footerRect().y - ITEM_STRIP_FOOTER_GAP - ITEM_STRIP_HEIGHT, panel.width - PANEL_PADDING * 2, ITEM_STRIP_HEIGHT)
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

    private fun playHoverSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), HOVER_SOUND_PITCH, HOVER_SOUND_VOLUME))
    }

    private fun playLockedSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.CHEST_LOCKED, LOCKED_SOUND_PITCH, LOCKED_SOUND_VOLUME))
    }

    private fun registerHoverSound(key: String, hovered: Boolean) {
        if (hovered && currentHoverSoundKey == null) currentHoverSoundKey = key
    }

    private fun finishHoverSound() {
        val key = currentHoverSoundKey
        if (key != null && key != previousHoverSoundKey) playHoverSound()
        previousHoverSoundKey = key
    }

    private val UI_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/bp_bg.png")
    private val REWARD_BOX_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/box.png")
    private val REWARD_BOX_CLAIMED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/box_green_highlight.png")
    private val REWARD_BOX_CLAIMABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/box_yellow_highlight.png")
    private val REWARD_BOX_LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/box_locked.png")
    private val LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
    private val REWARD_ITEM_CLAIMABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
    private val REWARD_ITEM_CLAIMED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_green.png")
    private val REWARD_ITEM_LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_grey.png")
    private val REWARD_ITEM_PROMINENT_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_gold.png")
    private val PROGRESS_EMPTY_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_progress_empty.png")
    private val PROGRESS_FILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_progress_fill.png")
    private val FRAME_PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
    private val CLAIM_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_yellow.png")
    private val CLAIM_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_yellow_hover.png")
    private val BACK_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_gray.png")
    private val BACK_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_gray_hover.png")
    private val TOOLTIP_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_item.png")
    private val MISSION_ROW_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_grey.png")
    private val MISSION_ROW_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
    private val CKDM_BOLD_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    private val CKDM_BOLD_LARGE_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_large")
    private val CKDM_BOLD_SMALL_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
    private val CKDM_BOLD_CLAIM_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_claim")
    private val UI_BACKGROUND_WIDTH = 2560
    private val UI_BACKGROUND_HEIGHT = 1440
    private val UI_BACKGROUND_ALPHA = 0.6f
    private val BACKGROUND_DIM_OVERLAY = 0x66000000
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
    private val CLAIM_TEXT_FLOAT_PERIOD_MS = 1250.0f
    private val CLAIM_TEXT_FLOAT_AMPLITUDE = 2.0f
    private val CLAIMABLE_SHAKE_PERIOD_MS = 3000L
    private val CLAIMABLE_SHAKE_ACTIVE_MS = 520.0f
    private val CLAIMABLE_SHAKE_WAVES = 2.5f
    private val CLAIMABLE_SHAKE_DEGREES = 6.0f
    private val CLAIMABLE_SHAKE_PHASE_OFFSET_MS = 113L
    private val REWARD_FLYOUT_DURATION_MS = 700.0f
    private val REWARD_FLYOUT_STAGGER_MS = 65
    private val REWARD_FLYOUT_ARC_HEIGHT = 28.0f
    private val REWARD_FLYOUT_SCALE_FROM = 1.55f
    private val REWARD_FLYOUT_SCALE_TO = 0.7f
    private val REWARD_FLYOUT_Z = 500.0f
    private val ENTRANCE_SLIDE_DOWN_OFFSET = -10
    private val REWARD_SLIDE_OFFSET = 18
    private val BUTTON_SLIDE_OFFSET = 18
    private val FOOTER_SLIDE_OFFSET = 10
    private val HEADER_SCALE_FROM = 0.92f
    private val REWARD_BOX_TEXTURE_SIZE = 512
    private val LOCKED_TEXTURE_SIZE = 16
    private val LOCKED_ICON_SIZE = 40
    private val REWARD_ITEM_TEXTURE_WIDTH = 1643
    private val REWARD_ITEM_TEXTURE_HEIGHT = 253
    private val REWARD_ITEM_SOURCE_CORNER_SIZE = 75
    private val REWARD_ITEM_DESTINATION_CORNER_SIZE = 14
    private val CLAIM_BUTTON_TEXTURE_SIZE = 8
    private val CLAIM_BUTTON_CORNER_SIZE = 2
    private val CLAIM_BUTTON_DESTINATION_CORNER_SIZE = 4
    private val CLAIM_BUTTON_HOVER_TEXTURE_SIZE = 10
    private val CLAIM_BUTTON_HOVER_CORNER_SIZE = 3
    private val CLAIM_BUTTON_HOVER_DESTINATION_CORNER_SIZE = 5
    private val CLAIM_BUTTON_HOVER_BORDER_PADDING = 1
    private val FRAME_PANEL_TEXTURE_WIDTH = 1643
    private val FRAME_PANEL_TEXTURE_HEIGHT = 253
    private val FRAME_PANEL_SOURCE_CORNER_SIZE = 75
    private val FRAME_PANEL_DESTINATION_CORNER_SIZE = 14
    private val TOOLTIP_CONTAINER_TEXTURE_SIZE = 32
    private val MISSION_ROW_TEXTURE_WIDTH = 1643
    private val MISSION_ROW_TEXTURE_HEIGHT = 253
    private val MISSION_ROW_SOURCE_CORNER_SIZE = 75
    private val MISSION_ROW_DESTINATION_CORNER_SIZE = 14
    private val PROGRESS_TEXTURE_SIZE = 16
    private val PROGRESS_TEXTURE_CORNER_SIZE = 4
    private val PROGRESS_DESTINATION_CORNER_SIZE = 4
    private val PROGRESS_MISSION_SOURCE_X = 4
    private val PROGRESS_MISSION_SOURCE_WIDTH = 8
    private val TOOLTIP_CONTAINER_CORNER_SIZE = 10
    private val REWARD_HOVER_TINT = 0x44FFFFFF
    private val BUTTON_DISABLED_FILL = 0x6630353D
    private val TOOLTIP_GOLD = 0xFFFFC857.toInt()
    private val TOOLTIP_AVATAR_FALLBACK_FILL = 0xFF3A414C.toInt()
    private val LOCKED_ITEM_ALPHA = 0.5f
    private val PROGRESS_BACK = 0xFF2C3138.toInt()
    private val PROGRESS_FILL = 0xFF72C66F.toInt()
    private val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private val TEXT_MUTED = 0xFFB8C0CC.toInt()
    private val CKDM_HIGHLIGHT_TEXT_COLOR = 0xFFFFD447.toInt()
    private val CKDM_HIGHLIGHT_SHADOW_COLOR = 0xFFFF7A1A.toInt()
    private val CKDM_HIGHLIGHT_SHADOW_OFFSET = 2
    private val CKDM_SHADOW_COLOR = 0xAA000000.toInt()
    private val CKDM_SHADOW_OFFSET = 2
    private val QUEST_TITLE_NUMBER_REGEX = Regex("\\d+")
    private val SAFE_EDGE_PADDING = 8
    private val CONTENT_PADDING = 10
    private val PANEL_PADDING = 12
    private val DETAIL_COLUMN_GAP = 18
    private val HEADER_HEIGHT = 64
    private val HEADER_TITLE_TEXTURE_HEIGHT = 40
    private val FOOTER_HEIGHT = 40
    private val ITEM_STRIP_HEIGHT = 110
    private val CONTENT_TO_ITEM_STRIP_GAP = 28
    private val ITEM_STRIP_FOOTER_GAP = 6
    private val SLOT_SIZE = 68
    private val PROMINENT_SLOT_SIZE = 82
    private val SLOT_GAP = 8
    private val BASE_ITEM_RENDER_SIZE = 16
    private val ITEM_RENDER_PADDING = 12
    private val LOCK_OVERLAY_Z = 300.0f
    private val REWARD_TIER_LABEL_SCALE = 2.0f
    private val REWARD_TIER_LABEL_PROMINENT_SCALE = 2.0f
    private val REWARD_TIER_LABEL_HEIGHT = 7
    private val REWARD_TIER_LABEL_BOTTOM_GAP = 10
    private val REWARD_TIER_CONNECTOR_HEIGHT = 5
    private val REWARD_TIER_CONNECTOR_SIDE_PADDING = 16
    private val REWARD_TIER_CONNECTOR_BOTTOM_GAP = 16
    private val REWARD_STATUS_LABEL_TOP_GAP = 4
    private val REWARD_PROGRESS_BAR_HEIGHT = 5
    private val REWARD_PROGRESS_BAR_BOTTOM_OFFSET = 7
    private val CLAIM_TEXT_TOP_OFFSET = 14
    private val CLAIM_TEXT_SCISSOR_TOP_PADDING = 30
    private val QUANTITY_PADDING = 8
    private val QUANTITY_TEXT_HEIGHT = 7
    private val PLAYER_PREVIEW_WIDTH_PERCENT = 20
    private val PLAYER_PREVIEW_MIN_WIDTH = 112
    private val PLAYER_PREVIEW_MIN_HEIGHT = 120
    private val PLAYER_PREVIEW_MIN_SIZE = 42
    private val PLAYER_PREVIEW_MAX_SIZE = 82
    private val PLAYER_DOLL_TARGET_SIZE = 24
    private val PLAYER_PREVIEW_Y_OFFSET = 0.0625f
    private val PLAYER_PREVIEW_ANGLE_X = 0.15f
    private val PLAYER_PREVIEW_ANGLE_Y = 0.0f
    private val MISSION_PANEL_WIDTH_PERCENT = 50
    private val MISSION_PANEL_MIN_WIDTH = 320
    private val MISSION_PANEL_FRAME_TOP_OFFSET = 28
    private val MISSION_PANEL_X_PADDING = 18
    private val MISSION_ROW_ASPECT_WIDTH = 6.5f
    private val MISSION_ROW_MIN_HEIGHT = 48
    private val MISSION_ROW_PADDING = 14
    private val MISSION_ROW_COLUMN_GAP = 8
    private val MISSION_ROW_HEADER_TEXT_HEIGHT = 7
    private val MISSION_ROW_SUB_TEXT_HEIGHT = 6
    private val MISSION_ROW_TEXT_GAP = 4
    private val MISSION_ROW_ICON_SIZE = 22
    private val MISSION_ROW_COMPLETED_GAP = 5
    private val MISSION_ROW_AVATAR_SIZE = 12
    private val MISSION_ROW_AVATAR_GAP = 3
    private val MISSION_ROW_MAX_AVATARS = 3
    private val MISSION_ROW_CONTAINER_CORNER_SIZE = 10
    private val MISSION_XP_VALUE_GAP = 4
    private val MISSION_COMPLETED_COLUMN_WIDTH_PERCENT = 30
    private val MISSION_COMPLETED_COLUMN_MIN_WIDTH = 96
    private val MISSION_XP_LABEL_COLOR = 0xFF6BE06B.toInt()
    private val MISSION_ROW_BACKGROUND_ALPHA = 0.35f
    private val MISSION_ROW_HOVER_BACKGROUND_ALPHA = 0.92f
    private val MISSION_PROGRESS_BAR_HEIGHT = 5
    private val MISSION_PROGRESS_TEXT_GAP = 6
    private val MISSION_HEADER_HEIGHT = 64
    private val MISSION_FILTER_Y_OFFSET = 44
    private val MISSION_TAB_TEXT_HEIGHT = 9
    private val MISSION_TAB_GAP = 12
    private val MISSION_LIST_VERTICAL_PADDING = 12
    private val MISSIONS_SCROLL_STEP = 24.0f
    private val REWARD_SCROLL_STEP = 42.0f
    private val MISSIONS_TRACK_LIMIT = 7
    private val ROW_GAP = 4
    private val SCROLLBAR_GAP = 6
    private val XP_PER_LEVEL = 100
    private val XP_PANEL_PADDING = 16
    private val XP_PANEL_TOP_GAP = 6
    private val XP_PANEL_BOTTOM_GAP = 4
    private val XP_PANEL_SECTION_GAP = 10
    private val XP_PANEL_LEVEL_FOOTER_GAP = 18
    private val XP_PANEL_HEADER_HEIGHT = 18
    private val XP_PANEL_LEVEL_NUMBER_SCALE = 2.0f
    private val XP_PANEL_LEVEL_MIN_SIZE = 42
    private val XP_PANEL_FOOTER_LABEL_HEIGHT = 10
    private val XP_PANEL_FOOTER_VALUE_HEIGHT = 14
    private val XP_PANEL_XP_BAR_GAP = 5
    private val XP_PANEL_FOOTER_GAP = 4
    private val XP_PANEL_PROGRESS_HEIGHT = 8
    private val XP_PANEL_FOOTER_HEIGHT = XP_PANEL_FOOTER_LABEL_HEIGHT + XP_PANEL_FOOTER_VALUE_HEIGHT + XP_PANEL_XP_BAR_GAP + XP_PANEL_PROGRESS_HEIGHT + XP_PANEL_FOOTER_GAP + XP_PANEL_FOOTER_LABEL_HEIGHT
    private val BUTTON_HEIGHT = 20
    private val BACK_BUTTON_WIDTH = 92
    private val CLAIM_ALL_WIDTH = 124
    private val TOOLTIP_PADDING = 14
    private val TOOLTIP_LINE_HEIGHT = 13
    private val TOOLTIP_SMALL_TEXT_HEIGHT = 7
    private val TOOLTIP_HEADER_CONTENT_GAP = 11
    private val TOOLTIP_SECTION_GAP = 20
    private val TOOLTIP_MOUSE_OFFSET = 12
    private val TOOLTIP_AVATAR_SIZE = 12
    private val TOOLTIP_AVATAR_GAP = 3
    private val TOOLTIP_AVATAR_FOOTER_GAP = 8
    private val TOOLTIP_AVATAR_CONTENT_WIDTH = 96
    private val TOOLTIP_Z = 450.0f
    private val TOOLTIP_BACKGROUND_ALPHA = 0.75f
    private val QUICKSKIN_HEAD_TEXTURE_SIZE = 128
    private val SCROLL_LERP = 0.35f
    private val CLAIM_SOUND_PITCH = 1.2f
    private val CLAIM_SOUND_VOLUME = 0.7f
    private val BUTTON_CLICK_SOUND_PITCH = 1.0f
    private val BUTTON_CLICK_SOUND_VOLUME = 0.6f
    private val HOVER_SOUND_PITCH = 1.55f
    private val HOVER_SOUND_VOLUME = 0.18f
    private val LOCKED_SOUND_PITCH = 0.8f
    private val LOCKED_SOUND_VOLUME = 0.7f
}
