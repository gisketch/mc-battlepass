package dev.gisketch.chowkingdom.revive

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.glfw.GLFW
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.sin

object ReviveClient {
    private val REVIVE_OVERLAY_LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive_overlay")
    private val REVIVE_UI_LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "revive_ui")
    private var downedAnimationStartedAtMs = 0L
    private var wasIncapacitated = false
    private var previousButtonHover = false
    private var giveUpSent = false

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerGuiLayers)
        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(::onMovementInput)
        NeoForge.EVENT_BUS.addListener(::onMousePressed)
    }

    fun onSelfStateChanged(active: Boolean) {
        if (active && !wasIncapacitated) {
            downedAnimationStartedAtMs = System.currentTimeMillis()
            giveUpSent = false
            previousButtonHover = false
            stabilizeClientDeathState()
        }
        wasIncapacitated = active
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerBelowAll(REVIVE_OVERLAY_LAYER_ID) { guiGraphics, _ -> renderRedOverlay(guiGraphics) }
        event.registerAbove(VanillaGuiLayers.CHAT, REVIVE_UI_LAYER_ID) { guiGraphics, _ -> renderReviveUi(guiGraphics) }
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null || minecraft.level == null) {
            ReviveClientState.clearAll()
            wasIncapacitated = false
            return
        }
        if (ReviveClientState.selfState() != null) {
            minecraft.player?.isSprinting = false
            stabilizeClientDeathState()
        }
    }

    private fun onMovementInput(event: MovementInputUpdateEvent) {
        if (ReviveClientState.selfState() == null) return
        event.input.jumping = false
    }

    private fun onMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.screen !is ChatScreen) return
        if (ReviveClientState.selfState() == null || giveUpSent) return
        val minecraft = Minecraft.getInstance()
        val rect = giveUpButtonRect(minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight)
        if (!rect.contains(event.mouseX.toInt(), event.mouseY.toInt())) return
        giveUpSent = true
        playClickSound()
        ReviveNetwork.sendGiveUp()
        event.isCanceled = true
    }

    private fun renderRedOverlay(guiGraphics: GuiGraphics) {
        val state = ReviveClientState.selfState() ?: return
        val minecraft = Minecraft.getInstance()
        if (minecraft.options.hideGui || minecraft.gui.debugOverlay.showDebugScreen()) return
        val elapsed = elapsedMs()
        val overlayAlpha = easeOutCubic(progress(elapsed, 0L, OVERLAY_FADE_MS))
        guiGraphics.fill(0, 0, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, colorWithAlpha(RED_OVERLAY, overlayAlpha))
    }

    private fun renderReviveUi(guiGraphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (minecraft.options.hideGui || minecraft.gui.debugOverlay.showDebugScreen()) return
        val width = minecraft.window.guiScaledWidth
        val height = minecraft.window.guiScaledHeight
        renderReviveProgressHud(guiGraphics, minecraft, player.uuid, width, height)
        ReviveClientState.selfState()?.let { state -> renderIncapacitatedHud(guiGraphics, minecraft, player.uuid, state, width, height) }
        if (ReviveClientState.selfState() == null) ReviveClientState.completeNotice()?.let { notice -> renderRevivedNotice(guiGraphics, minecraft, notice, width, height) }
    }

    private fun renderReviveProgressHud(guiGraphics: GuiGraphics, minecraft: Minecraft, playerId: java.util.UUID, width: Int, height: Int) {
        val progress = ReviveClientState.progressForReviver(playerId) ?: return
        val remaining = remainingSecondsPrecise(progress.expiresAtMs)
        val segments = listOf(
            TextSegment(ckdmText("REVIVING ", CKDM_BOLD_FONT), CKDM_WHITE, CKDM_DARK_SHADOW),
            TextSegment(ckdmText(formatPreciseSeconds(remaining), CKDM_BOLD_FONT), CKDM_GOLD, CKDM_GOLD_SHADOW),
            TextSegment(ckdmText(" SECONDS", CKDM_BOLD_FONT), CKDM_WHITE, CKDM_DARK_SHADOW),
        )
        val y = (height - HOTBAR_REVIVE_TEXT_OFFSET).coerceAtLeast(8)
        drawCenteredSegments(guiGraphics, minecraft.font, segments, width, y)
    }

    private fun renderIncapacitatedHud(guiGraphics: GuiGraphics, minecraft: Minecraft, playerId: UUID, state: SelfReviveState, width: Int, height: Int) {
        val elapsed = elapsedMs()
        val shake = shakeOffset(elapsed)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(shake.x, shake.y, 0.0f)
        renderTitle(guiGraphics, minecraft.font, state, width, height, elapsed)
        renderCountdownLine(guiGraphics, minecraft.font, playerId, state, width, height, elapsed)
        renderGiveUpButton(guiGraphics, minecraft, width, height)
        pose.popPose()
    }

    private fun renderTitle(guiGraphics: GuiGraphics, font: Font, state: SelfReviveState, width: Int, height: Int, elapsed: Long) {
        val text = fitCkdmText(font, state.title.ifBlank { "YOU GOT KILLED" }, width - TITLE_MARGIN * 2, CKDM_BOLD_LARGE_FONT)
        val title = ckdmText(text, CKDM_BOLD_LARGE_FONT)
        val titleY = (height * TITLE_Y_RATIO).toInt().coerceAtLeast(TITLE_MIN_Y)
        val titleAlpha = easeOutCubic(progress(elapsed, TITLE_FADE_DELAY_MS, TITLE_FADE_MS))
        val scale = lerp(TITLE_START_SCALE, 1.0f, easeOutCubic(progress(elapsed, 0L, TITLE_SCALE_MS)))
        val color = colorWithAlpha(CKDM_WHITE, titleAlpha)
        val shadow = colorWithAlpha(CKDM_DARK_SHADOW, titleAlpha)
        val textWidth = font.width(title)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(width / 2.0f, titleY + font.lineHeight / 2.0f, 0.0f)
        pose.scale(scale, scale, 1.0f)
        drawCkdmShadowed(guiGraphics, font, title, -textWidth / 2, -font.lineHeight / 2, color, shadow, CKDM_SHADOW_OFFSET)
        pose.popPose()
    }

    private fun renderCountdownLine(guiGraphics: GuiGraphics, font: Font, playerId: UUID, state: SelfReviveState, width: Int, height: Int, elapsed: Long) {
        val reviveProgress = ReviveClientState.progressForTarget(playerId)
        val seconds = ceil((((reviveProgress?.expiresAtMs ?: state.expiresAtMs) - System.currentTimeMillis()).coerceAtLeast(0L)) / 1000.0).toInt()
        val alpha = easeOutCubic(progress(elapsed, COUNTDOWN_DELAY_MS, STAGGER_FADE_MS))
        val slide = ((1.0f - alpha) * STAGGER_SLIDE).toInt()
        val y = reviveCountdownY(height, slide)
        val segments = if (reviveProgress == null) countdownSegments(font, seconds, width - TITLE_MARGIN * 2, alpha) else revivedSoonSegments(font, seconds, width - TITLE_MARGIN * 2, alpha)
        drawCenteredSegments(guiGraphics, font, segments, width, y)
    }

    private fun renderRevivedNotice(guiGraphics: GuiGraphics, minecraft: Minecraft, notice: ReviveCompleteNotice, width: Int, height: Int) {
        val now = System.currentTimeMillis()
        val fadeIn = easeOutCubic(((now - notice.startedAtMs).toFloat() / COMPLETE_NOTICE_FADE_MS).coerceIn(0.0f, 1.0f))
        val fadeOut = (((notice.expiresAtMs - now).toFloat() / COMPLETE_NOTICE_FADE_MS).coerceIn(0.0f, 1.0f))
        val alpha = fadeIn.coerceAtMost(fadeOut)
        val label = ckdmText("YOU'VE BEEN REVIVED BY", CKDM_BOLD_FONT)
        val names = notice.reviverNames.joinToString(", ").takeIf { it.isNotBlank() }
        val fallback = if (notice.reviverIds.isEmpty() && names != null) ckdmText(" $names", CKDM_BOLD_FONT) else null
        val headCount = notice.reviverIds.size
        val headsWidth = if (headCount == 0) 0 else headCount * REVIVED_HEAD_SIZE + (headCount - 1) * REVIVED_HEAD_GAP
        val fallbackWidth = fallback?.let { minecraft.font.width(it) } ?: 0
        val contentWidth = minecraft.font.width(label) + if (headCount > 0) REVIVED_HEAD_TEXT_GAP + headsWidth else fallbackWidth
        var x = (width - contentWidth) / 2
        val y = reviveCountdownY(height, 0)
        drawCkdmShadowed(guiGraphics, minecraft.font, label, x, y, colorWithAlpha(CKDM_WHITE, alpha), colorWithAlpha(CKDM_DARK_SHADOW, alpha), CKDM_SHADOW_OFFSET)
        x += minecraft.font.width(label)
        if (headCount > 0) {
            drawReviverHeads(guiGraphics, minecraft, notice, x + REVIVED_HEAD_TEXT_GAP, y - 3, alpha)
        } else if (fallback != null) {
            drawCkdmShadowed(guiGraphics, minecraft.font, fallback, x, y, colorWithAlpha(CKDM_GOLD, alpha), colorWithAlpha(CKDM_GOLD_SHADOW, alpha), CKDM_SHADOW_OFFSET)
        }
    }

    private fun renderGiveUpButton(guiGraphics: GuiGraphics, minecraft: Minecraft, width: Int, height: Int) {
        val rect = giveUpButtonRect(width, height)
        val cursorEnabled = minecraft.screen is ChatScreen
        val mouse = scaledMouse(minecraft, width, height)
        val hovered = cursorEnabled && rect.contains(mouse.x, mouse.y) && !giveUpSent
        if (hovered && !previousButtonHover) playHoverSound()
        previousButtonHover = hovered
        val elapsed = elapsedMs()
        val alpha = easeOutCubic(progress(elapsed, BUTTON_DELAY_MS, STAGGER_FADE_MS))
        val slide = ((1.0f - alpha) * STAGGER_SLIDE).toInt()
        val drawRect = rect.offset(0, slide)
        val texture = if (hovered) GIVE_UP_BUTTON_HOVER_TEXTURE else GIVE_UP_BUTTON_TEXTURE
        val textureSize = if (hovered) BUTTON_HOVER_TEXTURE_SIZE else BUTTON_TEXTURE_SIZE
        val sourceCorner = if (hovered) BUTTON_HOVER_CORNER_SIZE else BUTTON_CORNER_SIZE
        val destinationCorner = if (hovered) BUTTON_HOVER_DESTINATION_CORNER_SIZE else BUTTON_DESTINATION_CORNER_SIZE
        RenderSystem.enableBlend()
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        renderNineSlice(guiGraphics, texture, if (hovered) drawRect.inflate(BUTTON_HOVER_BORDER_PADDING) else drawRect, textureSize, sourceCorner, destinationCorner)
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
        val label = ckdmText(if (giveUpSent) "GIVING UP" else "GIVE UP", CKDM_BOLD_FONT)
        val labelX = drawRect.x + (drawRect.width - minecraft.font.width(label)) / 2
        val labelY = drawRect.y + (drawRect.height - minecraft.font.lineHeight) / 2 + 1
        drawCkdmShadowed(guiGraphics, minecraft.font, label, labelX, labelY, colorWithAlpha(CKDM_WHITE, alpha), colorWithAlpha(BUTTON_TEXT_SHADOW, alpha), 1)
    }

    internal fun remainingSecondsPrecise(expiresAtMs: Long): Double =
        ((expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L)) / 1000.0

    internal fun formatPreciseSeconds(seconds: Double): String = String.format(Locale.ROOT, "%.2f", seconds)

    internal fun ckdmText(text: String, fontId: ResourceLocation): Component =
        Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    internal fun drawCkdmShadowed(guiGraphics: GuiGraphics, font: Font, component: Component, x: Int, y: Int, color: Int, shadowColor: Int, shadowOffset: Int) {
        guiGraphics.drawString(font, component, x + shadowOffset, y + shadowOffset, shadowColor, false)
        guiGraphics.drawString(font, component, x, y, color, false)
    }

    private fun drawCenteredSegments(guiGraphics: GuiGraphics, font: Font, segments: List<TextSegment>, screenWidth: Int, y: Int) {
        val totalWidth = segments.sumOf { font.width(it.component) }
        var x = (screenWidth - totalWidth) / 2
        segments.forEach { segment ->
            drawCkdmShadowed(guiGraphics, font, segment.component, x, y, segment.color, segment.shadowColor, CKDM_SHADOW_OFFSET)
            x += font.width(segment.component)
        }
    }

    internal fun fitCkdmText(font: Font, text: String, maxWidth: Int, fontId: ResourceLocation): String {
        if (font.width(ckdmText(text, fontId)) <= maxWidth) return text
        val suffix = "..."
        var trimmed = text
        while (trimmed.isNotEmpty() && font.width(ckdmText(trimmed + suffix, fontId)) > maxWidth) trimmed = trimmed.dropLast(1)
        return trimmed + suffix
    }

    private fun countdownSegments(font: Font, seconds: Int, maxWidth: Int, alpha: Float): List<TextSegment> {
        val prefix = "REVIVE WINDOW CLOSES IN "
        val suffix = " SECONDS"
        if (font.width(ckdmText(prefix + seconds + suffix, CKDM_BOLD_FONT)) > maxWidth) {
            return listOf(TextSegment(ckdmText("$seconds SECONDS", CKDM_BOLD_FONT), colorWithAlpha(CKDM_GOLD, alpha), colorWithAlpha(CKDM_GOLD_SHADOW, alpha)))
        }
        return listOf(
            TextSegment(ckdmText(prefix, CKDM_BOLD_FONT), colorWithAlpha(CKDM_WHITE, alpha), colorWithAlpha(CKDM_DARK_SHADOW, alpha)),
            TextSegment(ckdmText(seconds.toString(), CKDM_BOLD_FONT), colorWithAlpha(CKDM_GOLD, alpha), colorWithAlpha(CKDM_GOLD_SHADOW, alpha)),
            TextSegment(ckdmText(suffix, CKDM_BOLD_FONT), colorWithAlpha(CKDM_WHITE, alpha), colorWithAlpha(CKDM_DARK_SHADOW, alpha)),
        )
    }

    private fun revivedSoonSegments(font: Font, seconds: Int, maxWidth: Int, alpha: Float): List<TextSegment> {
        val prefix = "YOU'LL BE REVIVED IN "
        val suffix = " SECONDS"
        if (font.width(ckdmText(prefix + seconds + suffix, CKDM_BOLD_FONT)) > maxWidth) {
            return listOf(TextSegment(ckdmText("$seconds SECONDS", CKDM_BOLD_FONT), colorWithAlpha(CKDM_GOLD, alpha), colorWithAlpha(CKDM_GOLD_SHADOW, alpha)))
        }
        return listOf(
            TextSegment(ckdmText(prefix, CKDM_BOLD_FONT), colorWithAlpha(CKDM_WHITE, alpha), colorWithAlpha(CKDM_DARK_SHADOW, alpha)),
            TextSegment(ckdmText(seconds.toString(), CKDM_BOLD_FONT), colorWithAlpha(CKDM_GOLD, alpha), colorWithAlpha(CKDM_GOLD_SHADOW, alpha)),
            TextSegment(ckdmText(suffix, CKDM_BOLD_FONT), colorWithAlpha(CKDM_WHITE, alpha), colorWithAlpha(CKDM_DARK_SHADOW, alpha)),
        )
    }

    private fun drawReviverHeads(guiGraphics: GuiGraphics, minecraft: Minecraft, notice: ReviveCompleteNotice, x: Int, y: Int, alpha: Float) {
        val connection = minecraft.connection
        notice.reviverIds.forEachIndexed { index, reviverId ->
            val headX = x + index * (REVIVED_HEAD_SIZE + REVIVED_HEAD_GAP)
            val skin = connection?.getPlayerInfo(reviverId)?.skin
            if (skin != null) {
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha)
                PlayerFaceRenderer.draw(guiGraphics, skin, headX, y, REVIVED_HEAD_SIZE)
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
            } else {
                guiGraphics.fill(headX, y, headX + REVIVED_HEAD_SIZE, y + REVIVED_HEAD_SIZE, colorWithAlpha(REVIVED_HEAD_FALLBACK_FILL, alpha))
                val name = notice.reviverNames.getOrNull(index).orEmpty()
                if (name.isNotBlank()) guiGraphics.drawString(minecraft.font, name.take(1).uppercase(Locale.ROOT), headX + 4, y + 3, colorWithAlpha(CKDM_WHITE, alpha), false)
            }
        }
    }

    internal fun colorWithAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * alphaFactor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    private fun giveUpButtonRect(width: Int, height: Int): Rect {
        val buttonWidth = GIVE_UP_BUTTON_WIDTH.coerceAtMost((width - 24).coerceAtLeast(72))
        val x = (width - buttonWidth) / 2
        val y = reviveCountdownY(height, 0) + GIVE_UP_BUTTON_TOP_GAP
        return Rect(x, y, buttonWidth, GIVE_UP_BUTTON_HEIGHT)
    }

    private fun reviveCountdownY(height: Int, slide: Int): Int =
        ((height * TITLE_Y_RATIO).toInt().coerceAtLeast(TITLE_MIN_Y) + TITLE_TO_COUNTDOWN_GAP + slide).coerceAtLeast(8)

    private fun scaledMouse(minecraft: Minecraft, width: Int, height: Int): Point {
        val x = (minecraft.mouseHandler.xpos() * width / minecraft.window.screenWidth).toInt()
        val y = (minecraft.mouseHandler.ypos() * height / minecraft.window.screenHeight).toInt()
        return Point(x, y)
    }

    private fun elapsedMs(): Long = if (downedAnimationStartedAtMs == 0L) 0L else System.currentTimeMillis() - downedAnimationStartedAtMs

    private fun shakeOffset(elapsed: Long): Offset {
        val shakeProgress = progress(elapsed, SHAKE_DELAY_MS, SHAKE_MS)
        if (shakeProgress <= 0.0f || shakeProgress >= 1.0f) return Offset.ZERO
        val decay = 1.0f - shakeProgress
        val strength = SHAKE_AMPLITUDE * decay * decay
        val x = sin(elapsed * 0.1).toFloat() * strength
        val y = sin(elapsed * 0.16 + 1.7).toFloat() * strength * 0.55f
        return Offset(x, y)
    }

    private fun progress(elapsed: Long, delay: Long, duration: Long): Float =
        ((elapsed - delay).toFloat() / duration).coerceIn(0.0f, 1.0f)

    private fun easeOutCubic(value: Float): Float {
        val inverse = 1.0f - value
        return 1.0f - inverse * inverse * inverse
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureSize: Int, sourceCorner: Int, destinationCorner: Int = sourceCorner) {
        val edge = textureSize - sourceCorner
        val middle = textureSize - sourceCorner * 2
        val innerWidth = (rect.width - destinationCorner * 2).coerceAtLeast(0)
        val innerHeight = (rect.height - destinationCorner * 2).coerceAtLeast(0)
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y, destinationCorner, destinationCorner), 0, 0, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y, innerWidth, destinationCorner), sourceCorner, 0, middle, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y, destinationCorner, destinationCorner), edge, 0, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y + destinationCorner, destinationCorner, innerHeight), 0, sourceCorner, sourceCorner, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + destinationCorner, innerWidth, innerHeight), sourceCorner, sourceCorner, middle, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y + destinationCorner, destinationCorner, innerHeight), edge, sourceCorner, sourceCorner, middle, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x, rect.y + rect.height - destinationCorner, destinationCorner, destinationCorner), 0, edge, sourceCorner, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + destinationCorner, rect.y + rect.height - destinationCorner, innerWidth, destinationCorner), sourceCorner, edge, middle, sourceCorner, textureSize)
        blitRegion(guiGraphics, texture, Rect(rect.x + rect.width - destinationCorner, rect.y + rect.height - destinationCorner, destinationCorner, destinationCorner), edge, edge, sourceCorner, sourceCorner, textureSize)
    }

    private fun blitRegion(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int, textureSize: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, sourceX.toFloat(), sourceY.toFloat(), sourceWidth, sourceHeight, textureSize, textureSize)
    }

    private fun playClickSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), BUTTON_CLICK_SOUND_PITCH, BUTTON_CLICK_SOUND_VOLUME))
    }

    private fun playHoverSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), HOVER_SOUND_PITCH, HOVER_SOUND_VOLUME))
    }

    private fun stabilizeClientDeathState() {
        val player = Minecraft.getInstance().player ?: return
        player.deathTime = 0
        if (player.health <= 0.0f) player.health = 1.0f
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height

        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom

        fun inflate(amount: Int): Rect = Rect(x - amount, y - amount, width + amount * 2, height + amount * 2)

        fun offset(offsetX: Int, offsetY: Int): Rect = Rect(x + offsetX, y + offsetY, width, height)
    }

    private data class Point(val x: Int, val y: Int)

    private data class TextSegment(val component: Component, val color: Int, val shadowColor: Int)

    private data class Offset(val x: Float, val y: Float) {
        companion object {
            val ZERO = Offset(0.0f, 0.0f)
        }
    }

    internal val CKDM_BOLD_FONT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    internal val CKDM_BOLD_LARGE_FONT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_large")
    internal const val CKDM_WHITE = 0xFFFFFFFF.toInt()
    internal const val CKDM_GOLD = 0xFFFFD24A.toInt()
    internal const val CKDM_DARK_SHADOW = 0xCC240808.toInt()
    internal const val CKDM_GOLD_SHADOW = 0xCC7A2E00.toInt()
    internal const val CKDM_SHADOW_OFFSET = 2
    private val GIVE_UP_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_red.png")
    private val GIVE_UP_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_red_hover.png")
    private const val RED_OVERLAY = 0x5AC80000
    private const val BUTTON_TEXT_SHADOW = 0xAA173A20.toInt()
    private const val TITLE_MARGIN = 18
    private const val TITLE_Y_RATIO = 0.22f
    private const val TITLE_MIN_Y = 34
    private const val TITLE_START_SCALE = 1.55f
    private const val TITLE_TO_COUNTDOWN_GAP = 42
    private const val TITLE_FADE_DELAY_MS = 30L
    private const val TITLE_FADE_MS = 170L
    private const val TITLE_SCALE_MS = 360L
    private const val OVERLAY_FADE_MS = 500L
    private const val SHAKE_DELAY_MS = 290L
    private const val SHAKE_MS = 220L
    private const val SHAKE_AMPLITUDE = 3.5f
    private const val COUNTDOWN_DELAY_MS = 90L
    private const val BUTTON_DELAY_MS = 160L
    private const val STAGGER_FADE_MS = 160L
    private const val STAGGER_SLIDE = 8
    private const val HOTBAR_COUNTDOWN_TEXT_OFFSET = 78
    private const val HOTBAR_REVIVE_TEXT_OFFSET = 62
    private const val COMPLETE_NOTICE_FADE_MS = 250L
    private const val REVIVED_HEAD_SIZE = 14
    private const val REVIVED_HEAD_GAP = 3
    private const val REVIVED_HEAD_TEXT_GAP = 8
    private const val REVIVED_HEAD_FALLBACK_FILL = 0xAA3C1E1E.toInt()
    private const val GIVE_UP_BUTTON_WIDTH = 128
    private const val GIVE_UP_BUTTON_HEIGHT = 24
    private const val GIVE_UP_BUTTON_TOP_GAP = 24
    private const val BUTTON_TEXTURE_SIZE = 8
    private const val BUTTON_CORNER_SIZE = 2
    private const val BUTTON_DESTINATION_CORNER_SIZE = 4
    private const val BUTTON_HOVER_TEXTURE_SIZE = 10
    private const val BUTTON_HOVER_CORNER_SIZE = 3
    private const val BUTTON_HOVER_DESTINATION_CORNER_SIZE = 5
    private const val BUTTON_HOVER_BORDER_PADDING = 1
    private const val BUTTON_CLICK_SOUND_PITCH = 1.0f
    private const val BUTTON_CLICK_SOUND_VOLUME = 0.55f
    private const val HOVER_SOUND_PITCH = 1.55f
    private const val HOVER_SOUND_VOLUME = 0.24f
}
