package dev.gisketch.chowkingdom.npc

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.Locale
import kotlin.math.roundToInt

object NpcBossBarClient {
    private val LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc_boss_bar")
    private val PROGRESS_EMPTY_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_progress_empty.png")
    private val PROGRESS_FILL_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_progress_fill.png")
    private val CKDM_BOLD_FONT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    private val CKDM_BOLD_SMALL_FONT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")

    private var state: ClientBossBarState? = null
    private var activeMusic: SimpleSoundInstance? = null
    private var activeMusicNpcId: String = ""
    private var activeMusicId: String = ""
    private var clientTick = 0

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerGuiLayers)
        NeoForge.EVENT_BUS.addListener(::onClientTick)
    }

    @JvmStatic
    fun apply(payload: NpcBossBarPayload) {
        val targetProgress = payload.progress()
        val current = state
        if (current == null || current.npcId != payload.npcId) {
            state = ClientBossBarState(
                npcId = payload.npcId,
                name = payload.name,
                mode = payload.mode,
                phaseName = payload.phaseName,
                phaseIndex = payload.phaseIndex,
                phaseCount = payload.phaseCount,
                displayedProgress = targetProgress,
                targetProgress = targetProgress,
                musicId = payload.musicId,
                musicVolume = payload.musicVolume,
                musicPitch = payload.musicPitch,
                musicRepeatTicks = payload.musicRepeatTicks,
                lastSyncTick = clientTick,
            )
            playMusic(payload)
            return
        }
        val musicChanged = current.musicId != payload.musicId || current.phaseIndex != payload.phaseIndex
        current.name = payload.name
        current.mode = payload.mode
        current.phaseName = payload.phaseName
        current.phaseIndex = payload.phaseIndex
        current.phaseCount = payload.phaseCount
        current.targetProgress = targetProgress
        current.musicId = payload.musicId
        current.musicVolume = payload.musicVolume
        current.musicPitch = payload.musicPitch
        current.musicRepeatTicks = payload.musicRepeatTicks
        current.lastSyncTick = clientTick
        if (payload.forceMusic || musicChanged) playMusic(payload)
    }

    @JvmStatic
    fun clear(payload: NpcBossBarClearPayload) {
        if (state?.npcId == payload.npcId || activeMusicNpcId == payload.npcId) clearState()
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAboveAll(LAYER_ID) { guiGraphics, _ -> render(guiGraphics) }
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        clientTick++
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null || minecraft.level == null) {
            clearState()
            return
        }
        val active = state ?: run {
            if (activeMusic != null) stopMusic()
            return
        }
        if (clientTick - active.lastSyncTick > STALE_SYNC_TICKS) {
            clearState()
            return
        }
        val music = activeMusic
        if (music != null && !minecraft.soundManager.isActive(music) && active.musicRepeatTicks <= 0) stopMusic()
        if (active.musicRepeatTicks <= 0 || active.musicId.isBlank() || clientTick < active.nextMusicTick) return
        playMusic(active.toPayload(forceMusic = true))
    }

    private fun render(guiGraphics: GuiGraphics) {
        val active = state ?: return
        val minecraft = Minecraft.getInstance()
        if (minecraft.options.hideGui || minecraft.gui.debugOverlay.showDebugScreen()) return
        if (minecraft.player == null || minecraft.level == null) return
        val screen = minecraft.screen
        if (screen != null && screen !is ChatScreen && !NpcClient.isBossDialogOpen()) return

        active.displayedProgress += (active.targetProgress - active.displayedProgress) * PROGRESS_LERP
        if (kotlin.math.abs(active.displayedProgress - active.targetProgress) <= 0.002f) active.displayedProgress = active.targetProgress

        val width = minecraft.window.guiScaledWidth
        val barWidth = (width - 36).coerceIn(MIN_BAR_WIDTH, MAX_BAR_WIDTH)
        val x = (width - barWidth) / 2
        val title = fitText(minecraft.font, active.name, barWidth, CKDM_BOLD_FONT)
        val titleY = TOP_Y
        drawCenteredCkdm(guiGraphics, minecraft.font, title, x, titleY, barWidth, TITLE_COLOR, SHADOW_COLOR, CKDM_BOLD_FONT)

        val barY = titleY + TITLE_HEIGHT + 3
        renderProgressSlice(guiGraphics, PROGRESS_EMPTY_TEXTURE, x, barY, barWidth, BAR_HEIGHT, 1.0f)
        val fillWidth = (barWidth * active.displayedProgress.coerceIn(0.0f, 1.0f)).roundToInt().coerceIn(0, barWidth)
        if (fillWidth > 0) renderProgressSlice(guiGraphics, PROGRESS_FILL_TEXTURE, x, barY, fillWidth, BAR_HEIGHT, 1.0f)

        val details = "${active.phaseName}  ${active.mode}".uppercase(Locale.ROOT)
        drawCenteredCkdm(guiGraphics, minecraft.font, fitText(minecraft.font, details, barWidth, CKDM_BOLD_SMALL_FONT), x, barY + BAR_HEIGHT + 3, barWidth, DETAIL_COLOR, SHADOW_COLOR, CKDM_BOLD_SMALL_FONT)
    }

    private fun playMusic(payload: NpcBossBarPayload) {
        if (payload.musicId.isBlank()) {
            stopMusic()
            return
        }
        val id = runCatching { ResourceLocation.parse(payload.musicId) }.getOrNull() ?: return
        runCatching { BuiltInRegistries.SOUND_EVENT.get(id) }.getOrNull() ?: return
        val minecraft = Minecraft.getInstance()
        val current = activeMusic
        if (current != null && activeMusicId == id.toString() && minecraft.soundManager.isActive(current)) {
            state?.nextMusicTick = if (payload.musicRepeatTicks > 0) clientTick + payload.musicRepeatTicks else Int.MAX_VALUE
            return
        }
        stopMusic()
        val instance = SimpleSoundInstance(
            id,
            SoundSource.MUSIC,
            payload.musicVolume.coerceIn(0.0f, 1.0f),
            payload.musicPitch.coerceIn(0.25f, 4.0f),
            RandomSource.create(),
            false,
            0,
            SoundInstance.Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true,
        )
        minecraft.soundManager.play(instance)
        activeMusic = instance
        activeMusicNpcId = payload.npcId
        activeMusicId = id.toString()
        state?.nextMusicTick = if (payload.musicRepeatTicks > 0) clientTick + payload.musicRepeatTicks else Int.MAX_VALUE
    }

    private fun clearState() {
        state = null
        stopMusic()
    }

    private fun stopMusic() {
        activeMusic?.let { sound -> Minecraft.getInstance().soundManager.stop(sound) }
        activeMusic = null
        activeMusicNpcId = ""
        activeMusicId = ""
    }

    private fun renderProgressSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, alpha: Float) {
        if (width <= 0 || height <= 0) return
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
        val corner = PROGRESS_DESTINATION_CORNER_SIZE.coerceAtMost(width / 2).coerceAtMost(height / 2).coerceAtLeast(1)
        val innerWidth = (width - corner * 2).coerceAtLeast(0)
        val innerHeight = (height - corner * 2).coerceAtLeast(0)
        blitTexture(guiGraphics, texture, x, y, corner, corner, 0, 0, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE)
        blitTexture(guiGraphics, texture, x + corner, y, innerWidth, corner, PROGRESS_TEXTURE_CORNER_SIZE, 0, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE * 2, PROGRESS_TEXTURE_CORNER_SIZE)
        blitTexture(guiGraphics, texture, x + width - corner, y, corner, corner, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE, 0, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE)
        blitTexture(guiGraphics, texture, x, y + corner, corner, innerHeight, 0, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE * 2)
        blitTexture(guiGraphics, texture, x + corner, y + corner, innerWidth, innerHeight, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE * 2, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE * 2)
        blitTexture(guiGraphics, texture, x + width - corner, y + corner, corner, innerHeight, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE * 2)
        blitTexture(guiGraphics, texture, x, y + height - corner, corner, corner, 0, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE)
        blitTexture(guiGraphics, texture, x + corner, y + height - corner, innerWidth, corner, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE * 2, PROGRESS_TEXTURE_CORNER_SIZE)
        blitTexture(guiGraphics, texture, x + width - corner, y + height - corner, corner, corner, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_SIZE - PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE, PROGRESS_TEXTURE_CORNER_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun blitTexture(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return
        guiGraphics.blit(texture, x, y, width, height, sourceX.toFloat(), sourceY.toFloat(), sourceWidth, sourceHeight, PROGRESS_TEXTURE_SIZE, PROGRESS_TEXTURE_SIZE)
    }

    private fun drawCenteredCkdm(guiGraphics: GuiGraphics, font: Font, text: String, x: Int, y: Int, width: Int, color: Int, shadowColor: Int, fontId: ResourceLocation) {
        val component = ckdmText(text, fontId)
        val textX = x + (width - font.width(component)).coerceAtLeast(0) / 2
        guiGraphics.drawString(font, component, textX + 1, y + 1, shadowColor, false)
        guiGraphics.drawString(font, component, textX, y, color, false)
    }

    private fun fitText(font: Font, raw: String, maxWidth: Int, fontId: ResourceLocation): String {
        val clean = raw.trim().ifBlank { "Boss" }
        if (font.width(ckdmText(clean, fontId)) <= maxWidth) return clean
        var text = clean
        while (text.length > 4 && font.width(ckdmText("$text...", fontId)) > maxWidth) text = text.dropLast(1)
        return "$text..."
    }

    private fun ckdmText(text: String, fontId: ResourceLocation): Component =
        Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    private fun NpcBossBarPayload.progress(): Float = if (maxHealth > 0.0f) (health / maxHealth).coerceIn(0.0f, 1.0f) else 0.0f

    private data class ClientBossBarState(
        val npcId: String,
        var name: String,
        var mode: String,
        var phaseName: String,
        var phaseIndex: Int,
        var phaseCount: Int,
        var displayedProgress: Float,
        var targetProgress: Float,
        var musicId: String,
        var musicVolume: Float,
        var musicPitch: Float,
        var musicRepeatTicks: Int,
        var lastSyncTick: Int,
        var nextMusicTick: Int = Int.MAX_VALUE,
    ) {
        fun toPayload(forceMusic: Boolean): NpcBossBarPayload = NpcBossBarPayload(
            npcId = npcId,
            name = name,
            mode = mode,
            phaseName = phaseName,
            phaseIndex = phaseIndex,
            phaseCount = phaseCount,
            health = targetProgress,
            maxHealth = 1.0f,
            musicId = musicId,
            musicVolume = musicVolume,
            musicPitch = musicPitch,
            musicRepeatTicks = musicRepeatTicks,
            forceMusic = forceMusic,
        )
    }

    private const val TOP_Y = 8
    private const val TITLE_HEIGHT = 9
    private const val BAR_HEIGHT = 12
    private const val MIN_BAR_WIDTH = 120
    private const val MAX_BAR_WIDTH = 340
    private const val PROGRESS_TEXTURE_SIZE = 16
    private const val PROGRESS_TEXTURE_CORNER_SIZE = 4
    private const val PROGRESS_DESTINATION_CORNER_SIZE = 4
    private const val PROGRESS_LERP = 0.18f
    private const val STALE_SYNC_TICKS = 20 * 5
    private const val TITLE_COLOR = 0xFFFFE6A3.toInt()
    private const val DETAIL_COLOR = 0xFFEBD8B6.toInt()
    private const val SHADOW_COLOR = 0xAA000000.toInt()
}
