package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class BattlepassScreen : Screen(Component.translatable("screen.${ChowKingdomMod.MOD_ID}.battlepass")) {
    private enum class ViewMode { PASS_SELECTION, PASS_DETAIL }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    private data class RewardSlot(
        val rect: Rect,
        val tier: BattlepassProgressionDefinition,
        val reward: BattlepassRewardDefinition,
        val stack: ItemStack,
        val claimed: Boolean,
        val unlocked: Boolean,
        val claimable: Boolean,
        val current: Boolean,
        val previousXp: Int,
    )

    private var selectedPassId: String? = null
    private var viewMode = ViewMode.PASS_SELECTION
    private var scroll = 0.0f
    private var targetScroll = 0.0f
    private var backRect = Rect(0, 0, 0, 0)
    private var passRects: List<Pair<Rect, BattlepassPassDefinition>> = emptyList()
    private var rewardSlots: List<RewardSlot> = emptyList()

    override fun init() {
        BattlepassPassRegistry.reload()
        selectedPassId = selectedPassId ?: BattlepassPassRegistry.all().firstOrNull()?.id
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        scroll = Mth.lerp(0.24f, scroll, targetScroll)
        if (viewMode == ViewMode.PASS_SELECTION) {
            renderSelection(guiGraphics, mouseX, mouseY)
        } else {
            renderDetail(guiGraphics, mouseX, mouseY)
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun removed() {
        BattlepassCameraController.stop()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        if (viewMode == ViewMode.PASS_DETAIL && backRect.contains(mouseX, mouseY)) {
            viewMode = ViewMode.PASS_SELECTION
            scroll = 0.0f
            targetScroll = 0.0f
            return true
        }

        val clickedPass = if (viewMode == ViewMode.PASS_SELECTION) passRects.firstOrNull { (rect, _) -> rect.contains(mouseX, mouseY) }?.second else null
        if (clickedPass != null) {
            selectedPassId = clickedPass.id
            viewMode = ViewMode.PASS_DETAIL
            scroll = 0.0f
            targetScroll = 0.0f
            return true
        }

        val clickedReward = rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) }
        if (viewMode == ViewMode.PASS_DETAIL && clickedReward?.claimable == true) {
            Minecraft.getInstance().connection?.sendCommand("chowkingdom battlepass claim ${selectedPassId ?: return true} ${clickedReward.tier.xp}")
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (viewMode != ViewMode.PASS_DETAIL || !hotbarRect().contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }

        targetScroll = (targetScroll - (scrollY.toFloat() * 36.0f)).coerceIn(0.0f, maxScroll())
        return true
    }

    private fun renderSelection(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val panel = mainRect()
        drawFrame(guiGraphics, panel, 0x22000000, 0xAAE0E6F0.toInt())
        guiGraphics.drawString(font, Component.translatable("ui.${ChowKingdomMod.MOD_ID}.battlepass.select_pass"), panel.x + 22, panel.y + 20, 0xF0F4FF, true)

        val passes = BattlepassPassRegistry.all().toList()
        passRects = passes.mapIndexed { index, pass ->
            val rect = Rect(panel.x + 28, panel.y + 56 + index * 42, 224, 34)
            val hovered = rect.contains(mouseX.toDouble(), mouseY.toDouble())
            drawFrame(guiGraphics, rect, if (hovered) 0x663A70C4 else 0x3310141C, if (hovered) 0xFF8DB3FF.toInt() else 0x88D7DDEA.toInt())
            guiGraphics.drawString(font, pass.displayName, rect.x + 10, rect.y + 7, 0xF0F4FF, true)
            guiGraphics.drawString(font, pass.categories.joinToString(" | "), rect.x + 10, rect.y + 20, 0x9CA3AF, false)
            rect to pass
        }

        if (passes.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("ui.${ChowKingdomMod.MOD_ID}.battlepass.empty"), panel.x + 28, panel.y + 58, 0xAAB2C0, true)
        }
    }

    private fun renderDetail(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val selectedPass = BattlepassPassRegistry.all().firstOrNull { pass -> pass.id == selectedPassId } ?: run {
            viewMode = ViewMode.PASS_SELECTION
            renderSelection(guiGraphics, mouseX, mouseY)
            return
        }
        val currentXp = Minecraft.getInstance().player?.uuid?.let { BattlepassXpStore.getXp(it, selectedPass.id) } ?: 0
        val panel = mainRect()
        drawFrame(guiGraphics, panel, 0x16000000, 0xAAE0E6F0.toInt())

        val titleRect = Rect(panel.x + 24, panel.y + 18, 172, 28)
        drawFrame(guiGraphics, titleRect, 0x4410141C, 0xDDFFFFFF.toInt())
        guiGraphics.drawString(font, selectedPass.displayName, titleRect.x + 12, titleRect.y + 9, 0xF0F4FF, true)

        backRect = Rect(panel.x + panel.width - 118, panel.y + 18, 92, 28)
        val backHovered = backRect.contains(mouseX.toDouble(), mouseY.toDouble())
        drawFrame(guiGraphics, backRect, if (backHovered) 0x663A70C4 else 0x4410141C, 0xDDFFFFFF.toInt())
        guiGraphics.drawString(font, Component.translatable("ui.${ChowKingdomMod.MOD_ID}.back"), backRect.x + 31, backRect.y + 9, 0xF0F4FF, true)

        guiGraphics.drawString(font, Component.literal("XP $currentXp").withStyle(ChatFormatting.AQUA), panel.x + 24, panel.y + 54, 0x8DEBFF, true)
        guiGraphics.drawString(font, selectedPass.description, panel.x + 24, panel.y + 68, 0xAAB2C0, false)

        val hotbar = hotbarRect()
        drawFrame(guiGraphics, hotbar, 0x08000000, 0x33FFFFFF)
        renderRewardHotbar(guiGraphics, hotbar, selectedPass, currentXp, mouseX, mouseY)
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
        val contentWidth = sortedTiers.size * SLOT_STEP + 10
        targetScroll = targetScroll.coerceIn(0.0f, maxScroll(contentWidth, hotbar.width))
        scroll = scroll.coerceIn(0.0f, maxScroll(contentWidth, hotbar.width))
        rewardSlots = emptyList()

        guiGraphics.enableScissor(hotbar.x + 6, hotbar.y + 2, hotbar.x + hotbar.width - 6, hotbar.y + hotbar.height - 2)
        sortedTiers.forEachIndexed { index, tier ->
            val x = hotbar.x + 12 + index * SLOT_STEP - scroll.toInt()
            val y = hotbar.y + 12
            val previousXp = sortedTiers.getOrNull(index - 1)?.xp ?: 0
            val claimed = Minecraft.getInstance().player?.uuid?.let { playerId -> BattlepassXpStore.isClaimed(playerId, pass.id, tier.xp) } == true
            val unlocked = currentXp >= tier.xp
            val claimable = unlocked && !claimed
            val current = !claimed && currentXp >= previousXp && !unlocked
            tier.rewards.firstOrNull()?.let { reward ->
                val slot = RewardSlot(Rect(x, y, SLOT_SIZE, SLOT_SIZE), tier, reward, rewardStack(reward), claimed, unlocked, claimable, current, previousXp)
                renderRewardSlot(guiGraphics, slot, index + 1, currentXp)
                rewardSlots = rewardSlots + slot
            }
        }
        guiGraphics.disableScissor()

        rewardSlots.firstOrNull { slot -> slot.rect.contains(mouseX.toDouble(), mouseY.toDouble()) }?.let { slot ->
            guiGraphics.renderComponentTooltip(font, tooltipFor(slot, currentXp), mouseX, mouseY)
        }
    }

    private fun renderRewardSlot(guiGraphics: GuiGraphics, slot: RewardSlot, number: Int, currentXp: Int) {
        val rect = slot.rect
        val texture = when {
            slot.claimed -> REWARD_CLAIMED_TEXTURE
            slot.current || slot.claimable -> REWARD_ACTIVE_TEXTURE
            else -> REWARD_INACTIVE_TEXTURE
        }
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, 0.0f, 0.0f, 32, 32, 32, 32)

        if (slot.current || slot.claimable) {
            drawFrame(guiGraphics, Rect(rect.x - 2, rect.y - 2, rect.width + 4, rect.height + 4), 0x00000000, 0xCC8DEBFF.toInt())
            val progress = progressFor(slot, currentXp)
            val barWidth = ((rect.width - 8) * progress).toInt().coerceIn(0, rect.width - 8)
            guiGraphics.fill(rect.x + 4, rect.y + rect.height - 8, rect.x + 4 + barWidth, rect.y + rect.height - 4, 0xCC57D7FF.toInt())
            guiGraphics.fill(rect.x + 4 + barWidth, rect.y + rect.height - 8, rect.x + rect.width - 4, rect.y + rect.height - 4, 0x66000000)
        }

        guiGraphics.renderItem(slot.stack, rect.x + 17, rect.y + 16, number)
        guiGraphics.renderItemDecorations(font, slot.stack, rect.x + 17, rect.y + 16, if (slot.reward.quantity > 1) slot.reward.quantity.toString() else null)

        if (!slot.unlocked && !slot.current) {
            guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, 0x66000000)
        }

        val color = when {
            slot.claimed -> 0xC8FFD7
            slot.claimable || slot.current -> 0x8DEBFF
            else -> 0x858B96
        }
        guiGraphics.drawString(font, number.toString(), rect.x + 7, rect.y + 6, color, true)
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

    private fun progressFor(slot: RewardSlot, currentXp: Int): Float {
        if (slot.claimable || slot.claimed) return 1.0f
        val span = (slot.tier.xp - slot.previousXp).coerceAtLeast(1)
        return ((currentXp - slot.previousXp).toFloat() / span.toFloat()).coerceIn(0.0f, 1.0f)
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
        val panelWidth = (width * 0.72f).toInt().coerceAtLeast(360).coerceAtMost(width - 24)
        val panelHeight = (height * 0.72f).toInt().coerceAtLeast(240).coerceAtMost(height - 24)
        return Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight)
    }

    private fun hotbarRect(): Rect {
        val panel = mainRect()
        return Rect(panel.x + 24, panel.y + panel.height - 100, panel.width - 48, 78)
    }

    private fun maxScroll(): Float = maxScroll((selectedPass()?.progression?.size ?: 0) * SLOT_STEP + 10, hotbarRect().width)

    private fun maxScroll(contentWidth: Int, visibleWidth: Int): Float = (contentWidth - visibleWidth + 24).coerceAtLeast(0).toFloat()

    private fun selectedPass(): BattlepassPassDefinition? = BattlepassPassRegistry.all().firstOrNull { pass -> pass.id == selectedPassId }

    companion object {
        private val REWARD_CLAIMED_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/reward_claimed.png")
        private val REWARD_ACTIVE_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/reward_active.png")
        private val REWARD_INACTIVE_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/reward_inactive.png")
        private const val SLOT_SIZE = 52
        private const val SLOT_STEP = 68
    }
}