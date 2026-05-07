package dev.gisketch.chowkingdom.relicroulette

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale
import kotlin.random.Random

object RelicRouletteClient {
    @JvmStatic
    fun open(payload: RelicRouletteOpenPayload) {
        Minecraft.getInstance().setScreen(RelicRouletteScreen(payload))
    }

    @JvmStatic
    fun rollStarted(payload: RelicRouletteRollStartedPayload) {
        val minecraft = Minecraft.getInstance()
        val current = minecraft.screen as? RelicRouletteScreen
        if (current?.poolId == payload.poolId) current.startRoll(payload)
        else minecraft.setScreen(RelicRouletteScreen(payload.toOpenPayload()).also { screen -> screen.startRoll(payload) })
    }

    private fun RelicRouletteRollStartedPayload.toOpenPayload(): RelicRouletteOpenPayload = RelicRouletteOpenPayload(
        poolId,
        displayName,
        rarity,
        spinItemIds,
        emptyList(),
        unlockedCount,
        totalCount,
    )
}

private class RelicRouletteScreen(private var payload: RelicRouletteOpenPayload) : Screen(Component.literal(payload.displayName)) {
    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
    }

    val poolId: String get() = payload.poolId
    private val openedAtMs = Util.getMillis()
    private var rollPayload: RelicRouletteRollStartedPayload? = null
    private var rollStartedAtMs = 0L
    private var rollSequence = emptyList<String>()
    private var lastSoundStep = Int.MIN_VALUE
    private var claimSoundPlayed = false
    private var rollRequested = false
    private var closing = false
    private var closingStartedAtMs = 0L

    fun startRoll(payload: RelicRouletteRollStartedPayload) {
        rollPayload = payload
        this.payload = payload.toOpenPayload()
        rollStartedAtMs = Util.getMillis()
        rollSequence = rollSequence(payload)
        lastSoundStep = Int.MIN_VALUE
        claimSoundPlayed = false
        rollRequested = false
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        finishCloseIfReady()
        val panel = panelRect()
        val slot = slotRect(panel)
        val alpha = transitionAlpha()
        val scale = transitionScale()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate((panel.x + panel.width / 2).toFloat(), (panel.y + panel.height / 2).toFloat(), 0.0f)
        pose.scale(scale, scale, 1.0f)
        pose.translate(-(panel.x + panel.width / 2).toFloat(), -(panel.y + panel.height / 2).toFloat(), 0.0f)
        renderNineSlice(guiGraphics, containerTexture(), panel, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, CONTAINER_DEST_CORNER, alpha)
        drawCenteredCkdm(guiGraphics, headerText(), panel.x, panel.y + 22, panel.width, withAlpha(0xFFFFFFFF.toInt(), alpha), CKDM_BOLD)
        renderNineSlice(guiGraphics, slotTexture(), slot, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, SLOT_DEST_CORNER, alpha)
        renderRouletteTrack(guiGraphics, slot, alpha)
        drawCenteredCkdm(guiGraphics, progressText(), panel.x, slot.bottom + PROGRESS_TOP_GAP, panel.width, withAlpha(0xFFE7D8A8.toInt(), alpha), CKDM_SMALL)
        val button = buttonRect(panel)
        renderButton(guiGraphics, button, buttonText(), mouseX, mouseY, canPressButton(), alpha)
        pose.popPose()
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closing) return true
        if (button == 0 && buttonRect(panelRect()).contains(mouseX.toInt(), mouseY.toInt()) && canPressButton()) {
            if (isRollComplete()) beginClose()
            else {
                rollRequested = true
                lastSoundStep = Int.MIN_VALUE
                RelicRouletteNetwork.requestRoll(payload.poolId)
            }
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f))
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun onClose() {
        beginClose()
    }

    private fun renderRouletteTrack(guiGraphics: GuiGraphics, slot: Rect, alpha: Float) {
        val mask = Rect(slot.x + 8, slot.y + 8, slot.width - 16, slot.height - 16)
        guiGraphics.enableScissor(mask.x, mask.y, mask.right, mask.bottom)
        if (rollPayload == null) renderIdleTrack(guiGraphics, mask, alpha)
        else renderResultTrack(guiGraphics, mask, alpha)
        guiGraphics.disableScissor()
    }

    private fun renderIdleTrack(guiGraphics: GuiGraphics, mask: Rect, alpha: Float) {
        val choices = payload.remainingItemIds.ifEmpty { payload.itemIds }
        if (choices.isEmpty()) return
        val now = Util.getMillis()
        val speed = if (rollRequested) PENDING_TRACK_SPEED else IDLE_TRACK_SPEED
        val travel = (now % 1_000_000L).toFloat() * speed / 1000.0f
        val firstIndex = (travel / ITEM_SPACING).toInt()
        if (rollRequested) playTrackTick(firstIndex, 1.35f)
        val offset = travel % ITEM_SPACING
        val centerX = mask.x + mask.width / 2 - ITEM_SIZE / 2
        val itemY = mask.y + (mask.height - ITEM_SIZE) / 2
        for (index in -2..4) {
            val itemId = choices[Math.floorMod(firstIndex + index, choices.size)]
            val x = (centerX + index * ITEM_SPACING - offset).toInt()
            renderScaledItem(guiGraphics, itemStack(itemId), x, itemY, ITEM_SIZE, alpha)
        }
    }

    private fun renderResultTrack(guiGraphics: GuiGraphics, mask: Rect, alpha: Float) {
        val roll = rollPayload ?: return
        val sequence = rollSequence.ifEmpty { listOf(roll.resultItemId) }
        val progress = ((Util.getMillis() - rollStartedAtMs).toFloat() / roll.durationMs.toFloat()).coerceIn(0.0f, 1.0f)
        val eased = easeInOutCubic(progress)
        val centerX = mask.x + mask.width / 2 - ITEM_SIZE / 2
        val itemY = mask.y + (mask.height - ITEM_SIZE) / 2
        val travel = (sequence.lastIndex * ITEM_SPACING).toFloat() * eased
        playTrackTick((travel / ITEM_SPACING).toInt(), Mth.lerp(progress, 1.35f, 0.75f))
        sequence.forEachIndexed { index, itemId ->
            val x = (centerX + index * ITEM_SPACING - travel).toInt()
            if (x > mask.x - ITEM_SIZE && x < mask.right) renderScaledItem(guiGraphics, itemStack(itemId), x, itemY, ITEM_SIZE, alpha)
        }
        if (progress >= 1.0f && !claimSoundPlayed) {
            claimSoundPlayed = true
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f))
        }
    }

    private fun playTrackTick(step: Int, pitch: Float) {
        if (lastSoundStep == Int.MIN_VALUE) {
            lastSoundStep = step
            return
        }
        if (step == lastSoundStep) return
        lastSoundStep = step
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch))
    }

    private fun rollSequence(payload: RelicRouletteRollStartedPayload): List<String> {
        val choices = payload.spinItemIds.ifEmpty { listOf(payload.resultItemId) }
        return buildList {
            repeat(ROLL_TRACK_ITEMS - 1) { add(choices.random(Random.Default)) }
            add(payload.resultItemId)
        }
    }

    private fun beginClose() {
        if (closing) return
        closing = true
        closingStartedAtMs = Util.getMillis()
    }

    private fun finishCloseIfReady() {
        if (closing && Util.getMillis() - closingStartedAtMs >= EXIT_ANIMATION_MS) Minecraft.getInstance().setScreen(null)
    }

    private fun transitionAlpha(): Float {
        val now = Util.getMillis()
        return if (closing) {
            1.0f - easeInCubic(((now - closingStartedAtMs).toFloat() / EXIT_ANIMATION_MS.toFloat()).coerceIn(0.0f, 1.0f))
        } else {
            easeOutCubic(((now - openedAtMs).toFloat() / ENTER_ANIMATION_MS.toFloat()).coerceIn(0.0f, 1.0f))
        }
    }

    private fun transitionScale(): Float {
        val now = Util.getMillis()
        return if (closing) {
            val progress = easeInCubic(((now - closingStartedAtMs).toFloat() / EXIT_ANIMATION_MS.toFloat()).coerceIn(0.0f, 1.0f))
            Mth.lerp(progress, 1.0f, 0.88f)
        } else {
            val progress = easeOutBack(((now - openedAtMs).toFloat() / ENTER_ANIMATION_MS.toFloat()).coerceIn(0.0f, 1.0f))
            Mth.lerp(progress, 0.82f, 1.0f)
        }
    }

    private fun easeInCubic(progress: Float): Float = progress * progress * progress

    private fun easeOutCubic(progress: Float): Float {
        val inverse = 1.0f - progress
        return 1.0f - inverse * inverse * inverse
    }

    private fun easeInOutCubic(progress: Float): Float = if (progress < 0.5f) {
        4.0f * progress * progress * progress
    } else {
        val shifted = -2.0f * progress + 2.0f
        1.0f - shifted * shifted * shifted / 2.0f
    }

    private fun easeOutBack(progress: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1.0f
        val shifted = progress - 1.0f
        return 1.0f + c3 * shifted * shifted * shifted + c1 * shifted * shifted
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val originalAlpha = color ushr 24
        val scaledAlpha = (originalAlpha.toFloat() * alpha).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaledAlpha shl 24)
    }

    private fun canRoll(): Boolean = rollPayload == null && !rollRequested && payload.remainingItemIds.isNotEmpty()

    private fun canPressButton(): Boolean = canRoll() || isRollComplete()

    private fun isRollComplete(): Boolean = rollPayload?.let { roll -> Util.getMillis() - rollStartedAtMs >= roll.durationMs } == true

    private fun buttonText(): String = when {
        rollPayload != null -> if (isRollComplete()) "DONE" else "ROLLING"
        payload.remainingItemIds.isEmpty() -> "COMPLETE"
        rollRequested -> "ROLLING"
        else -> "ROLL"
    }

    private fun progressText(): String = when {
        rollPayload != null -> "${rollPayload?.unlockedCount ?: payload.unlockedCount}/${rollPayload?.totalCount ?: payload.totalCount} unlocked"
        payload.totalCount <= 0 -> "No relics in pool"
        else -> "${payload.unlockedCount}/${payload.totalCount} unlocked"
    }

    private fun headerText(): String = payload.displayName.ifBlank { payload.rarity.replaceFirstChar { it.titlecase(Locale.ROOT) } }.uppercase(Locale.ROOT)

    private fun panelRect(): Rect {
        val panelWidth = 246.coerceAtMost((width - 24).coerceAtLeast(180))
        val panelHeight = 218.coerceAtMost((height - 24).coerceAtLeast(176))
        return Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight)
    }

    private fun slotRect(panel: Rect): Rect = Rect(panel.x + (panel.width - 166) / 2, panel.y + 58, 166, 86)

    private fun buttonRect(panel: Rect): Rect = Rect(panel.x + (panel.width - 120) / 2, panel.bottom - 36, 120, 24)

    private fun containerTexture(): ResourceLocation = if (payload.rarity.equals("rare", ignoreCase = true)) GOLD_CONTAINER_TEXTURE else YELLOW_CONTAINER_TEXTURE

    private fun slotTexture(): ResourceLocation = if (payload.rarity.equals("rare", ignoreCase = true)) GOLD_CONTAINER_TEXTURE else YELLOW_CONTAINER_TEXTURE

    private fun renderButton(guiGraphics: GuiGraphics, rect: Rect, label: String, mouseX: Int, mouseY: Int, active: Boolean, alpha: Float) {
        val hovered = active && rect.contains(mouseX, mouseY)
        val texture = when {
            !active -> GRAY_BUTTON_TEXTURE
            hovered -> GREEN_BUTTON_HOVER_TEXTURE
            else -> GREEN_BUTTON_TEXTURE
        }
        val textureSize = if (hovered) BUTTON_HOVER_TEXTURE_SIZE else BUTTON_TEXTURE_SIZE
        val sourceCorner = if (hovered) BUTTON_HOVER_SOURCE_CORNER else BUTTON_SOURCE_CORNER
        renderNineSlice(guiGraphics, texture, rect, textureSize, textureSize, sourceCorner, BUTTON_DEST_CORNER, alpha * if (active) 1.0f else 0.55f)
        drawCenteredCkdm(guiGraphics, label, rect.x, rect.y + 8, rect.width, withAlpha(if (active) 0xFFFFFFFF.toInt() else 0xFF777777.toInt(), alpha), CKDM_SMALL)
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int, sourceCorner: Int, destinationCorner: Int, alpha: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
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

    private fun renderScaledItem(guiGraphics: GuiGraphics, stack: ItemStack, x: Int, y: Int, size: Int, alpha: Float) {
        val scale = size / 16.0f
        val pose = guiGraphics.pose()
        pose.pushPose()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
        pose.translate(x.toFloat(), y.toFloat(), 100.0f)
        pose.scale(scale, scale, 1.0f)
        guiGraphics.renderItem(stack, 0, 0)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        pose.popPose()
    }

    private fun drawCenteredCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int, fontId: ResourceLocation) {
        val component = ckdmText(text, fontId)
        guiGraphics.drawString(font, component, x + (width - font.width(component)) / 2, y, color, false)
    }

    private fun ckdmText(text: String, fontId: ResourceLocation): Component =
        Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    private fun itemStack(itemId: String): ItemStack {
        val item = runCatching { ResourceLocation.parse(itemId) }.getOrNull()?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER) } ?: Items.BARRIER
        return ItemStack(item)
    }

    private fun RelicRouletteRollStartedPayload.toOpenPayload(): RelicRouletteOpenPayload = RelicRouletteOpenPayload(
        poolId,
        displayName,
        rarity,
        spinItemIds,
        emptyList(),
        unlockedCount,
        totalCount,
    )

    companion object {
        private const val ENTER_ANIMATION_MS = 240L
        private const val EXIT_ANIMATION_MS = 160L
        private const val ITEM_SIZE = 46
        private const val ITEM_SPACING = 70
        private const val ROLL_TRACK_ITEMS = 64
        private const val IDLE_TRACK_SPEED = 34.0f
        private const val PENDING_TRACK_SPEED = 150.0f
        private const val PROGRESS_TOP_GAP = 20
        private const val CONTAINER_TEXTURE_WIDTH = 1646
        private const val CONTAINER_TEXTURE_HEIGHT = 256
        private const val CONTAINER_SOURCE_CORNER = 75
        private const val CONTAINER_DEST_CORNER = 14
        private const val SLOT_DEST_CORNER = 10
        private const val BUTTON_TEXTURE_SIZE = 8
        private const val BUTTON_HOVER_TEXTURE_SIZE = 10
        private const val BUTTON_SOURCE_CORNER = 2
        private const val BUTTON_HOVER_SOURCE_CORNER = 3
        private const val BUTTON_DEST_CORNER = 4
        private val YELLOW_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
        private val GOLD_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_gold.png")
        private val GREEN_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green.png")
        private val GREEN_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green_hover.png")
        private val GRAY_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_gray.png")
        private val CKDM_BOLD = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
        private val CKDM_SMALL = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
    }
}