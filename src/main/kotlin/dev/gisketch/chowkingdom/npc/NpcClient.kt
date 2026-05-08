package dev.gisketch.chowkingdom.npc

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordQuickSkinSupport
import dev.gisketch.chowkingdom.mixin.GuiGraphicsAccessor
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ComponentRenderUtils
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.ChatVisiblity
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.event.RenderTooltipEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import com.mojang.datafixers.util.Either
import java.util.Locale
import java.util.UUID
import java.io.ByteArrayInputStream
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.joml.Quaternionf
import org.joml.Vector3f

object NpcClient {
    private val WORLD_CHAT_HEADS_LAYER_ID = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc_world_chat_heads")
    private val activeBalloons = mutableMapOf<Int, NpcBalloonLine>()
    private val activeFriendshipDeltas = mutableMapOf<Int, NpcFriendshipDeltaLine>()
    private val worldChatEntries = mutableListOf<NpcWorldChatEntry>()
    private val quickSkinChatTextures = mutableMapOf<UUID, ResourceLocation?>()
    private val skippedTalkResponses = mutableSetOf<Long>()

    @JvmStatic
    fun openDialog(payload: NpcDialogPayload) {
        Minecraft.getInstance().setScreen(NpcDialogScreen(payload))
    }

    @JvmStatic
    fun showBalloon(payload: NpcBalloonPayload) {
        val now = System.currentTimeMillis()
        val expiresAtMs = now + payload.durationTicks.coerceIn(20, 240) * 50L
        friendshipDeltaLine(payload.message, now, expiresAtMs)?.let { delta ->
            activeFriendshipDeltas[payload.npcEntityId] = delta
            return
        }
        activeBalloons[payload.npcEntityId] = NpcBalloonLine(payload.message, now, expiresAtMs)
    }

    @JvmStatic
    fun receiveTalkResponse(payload: NpcTalkResponsePayload) {
        (Minecraft.getInstance().screen as? NpcDialogScreen)?.receiveTalkResponse(payload)
    }

    @JvmStatic
    fun receiveWorldChat(payload: NpcWorldChatPayload) {
        val minecraft = Minecraft.getInstance()
        val line = worldChatLine(payload)
        minecraft.gui.chat.addMessage(line)
        val lineCount = worldChatLineCount(minecraft, line)
        val targetHeadX = if (payload.targetKind == "thinking") 0 else minecraft.font.width(worldChatTargetPrefix(payload.npcName))
        worldChatEntries.add(0, NpcWorldChatEntry(payload.npcId, payload.targetName, payload.targetId, payload.targetKind, minecraft.gui.guiTicks, lineCount, targetHeadX))
        while (worldChatEntries.size > MAX_WORLD_CHAT_HEAD_ENTRIES) worldChatEntries.removeLast()
    }

    fun skipTalkResponse(responseToken: Long) {
        if (responseToken != 0L) skippedTalkResponses += responseToken
    }

    fun shouldIgnoreTalkResponse(responseToken: Long): Boolean = responseToken != 0L && skippedTalkResponses.remove(responseToken)

    @JvmStatic
    fun renderBalloon(entity: LivingEntity, poseStack: PoseStack, bufferSource: MultiBufferSource, font: Font, packedLight: Int) {
        if (entity !is ChowNpcEntity || entity.isInvisible || !entity.isAlive) return
        val now = System.currentTimeMillis()
        val balloon = activeBalloons[entity.id]
        if (balloon != null && balloon.expiresAtMs <= now) {
            activeBalloons.remove(entity.id)
        }
        val activeBalloon = activeBalloons[entity.id]
        val delta = activeFriendshipDeltas[entity.id]
        if (delta != null && delta.expiresAtMs <= now) activeFriendshipDeltas.remove(entity.id)
        val activeDelta = activeFriendshipDeltas[entity.id]
        if (activeBalloon == null && activeDelta == null) return

        val minecraft = Minecraft.getInstance()
        if ((minecraft.player?.distanceToSqr(entity) ?: Double.MAX_VALUE) > BALLOON_RENDER_DISTANCE_SQR) return
        val guiGraphics = GuiGraphicsAccessor.`chowkingdom$create`(minecraft, poseStack, minecraft.renderBuffers().bufferSource())
        val rotation = Axis.YP.rotationDegrees(toEulerXyzDegrees(minecraft.entityRenderDispatcher.cameraOrientation()).y + 180.0f)
        if (activeBalloon == null) {
            renderFriendshipDeltaPopup(entity, poseStack, guiGraphics, rotation, font, activeDelta, now)
            return
        }
        val balloonIcon = balloonIcon(activeBalloon.message)
        val hasBalloonIcon = balloonIcon != null
        val balloonMessage = balloonIcon?.let { activeBalloon.message.removePrefix(it.marker).trimStart() } ?: activeBalloon.message
        val lines = font.split(FormattedText.of(balloonMessage), BALLOON_MAX_TEXT_WIDTH)
        if (lines.isEmpty()) return
        val alpha = animationAlpha(activeBalloon.startedAtMs, activeBalloon.expiresAtMs, now, BALLOON_FADE_MS)
        val iconSpace = if (hasBalloonIcon) BALLOON_ICON_SIZE + 2 else 0
        val greatestTextWidth = lines.mapIndexed { index, line -> font.width(line) + if (index == 0) iconSpace else 0 }.maxOrNull() ?: 0
        val balloonWidth = (greatestTextWidth + BALLOON_PADDING * 2).coerceAtLeast(BALLOON_MIN_WIDTH)
        val balloonHeight = lines.size * BALLOON_LINE_HEIGHT + BALLOON_PADDING * 2
        val balloonX = -balloonWidth / 2
        val balloonY = -balloonHeight

        poseStack.pushPose()
        poseStack.translate(0.0, entity.bbHeight + BALLOON_ENTITY_Y_OFFSET, 0.0)
        poseStack.mulPose(rotation)
        val animatedScale = BALLOON_SCALE * (0.88f + 0.12f * alpha)
        poseStack.scale(-animatedScale, -animatedScale, animatedScale)

        RenderSystem.enableBlend()
        RenderSystem.enableDepthTest()
        RenderSystem.enablePolygonOffset()
        RenderSystem.polygonOffset(3.0f, 3.0f)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, BALLOON_BG_ALPHA * alpha)
        renderBalloonNineSlice(guiGraphics, balloonX, balloonY, balloonWidth, balloonHeight)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.polygonOffset(0.0f, 0.0f)
        RenderSystem.disablePolygonOffset()
        RenderSystem.disableBlend()

        var textY = balloonY + BALLOON_PADDING
        lines.forEachIndexed { index, line ->
            val lineIconSpace = if (hasBalloonIcon && index == 0) iconSpace else 0
            val lineWidth = font.width(line) + lineIconSpace
            val iconX = -lineWidth / 2
            val textX = iconX + lineIconSpace
            if (balloonIcon != null && index == 0) {
                RenderSystem.enableBlend()
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
                guiGraphics.blit(balloonIcon.texture, iconX, textY, BALLOON_ICON_SIZE, BALLOON_ICON_SIZE, 0.0f, 0.0f, BALLOON_ICON_TEXTURE_SIZE, BALLOON_ICON_TEXTURE_SIZE, BALLOON_ICON_TEXTURE_SIZE, BALLOON_ICON_TEXTURE_SIZE)
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
            }
            guiGraphics.drawString(font, line, textX, textY, withAlpha(BALLOON_TEXT_COLOR, alpha), false)
            textY += BALLOON_LINE_HEIGHT
        }
        guiGraphics.flush()
        poseStack.popPose()
        renderFriendshipDeltaPopup(entity, poseStack, guiGraphics, rotation, font, activeDelta, now)
    }

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerRenderers)
        modBus.addListener(::registerGuiLayers)
        modBus.addListener(::registerTooltipFactories)
        NeoForge.EVENT_BUS.addListener(::hideHotbarDuringDialog)
        NeoForge.EVENT_BUS.addListener(::gatherRentContractTooltip)
    }

    @JvmStatic
    fun isDialogOpen(): Boolean = Minecraft.getInstance().screen is NpcDialogScreen

    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(NpcFeature.NPC_ENTITY.get(), ::ChowNpcRenderer)
        event.registerBlockEntityRenderer(NpcFeature.CAMPING_BLOCK_ENTITY.get()) { CampingBlockRenderer() }
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAbove(VanillaGuiLayers.CHAT, WORLD_CHAT_HEADS_LAYER_ID) { guiGraphics, _ -> renderWorldChatHeads(guiGraphics) }
    }

    private fun registerTooltipFactories(event: RegisterClientTooltipComponentFactoriesEvent) {
        event.register(NpcRentContractTooltip::class.java, ::NpcRentContractClientTooltip)
    }

    private fun gatherRentContractTooltip(event: RenderTooltipEvent.GatherComponents) {
        val npcId = NpcRentContractData.readNpcId(event.itemStack)
        if (npcId.isBlank()) return
        val definition = NpcConfig.get(npcId)
        val name = definition?.name ?: npcId
        event.tooltipElements.add(1.coerceAtMost(event.tooltipElements.size), Either.right(NpcRentContractTooltip(npcId, name)))
    }

    private fun hideHotbarDuringDialog(event: RenderGuiLayerEvent.Pre) {
        if (Minecraft.getInstance().screen !is NpcDialogScreen) return
        if (event.name in HIDDEN_DIALOG_HUD_LAYERS) event.isCanceled = true
    }

    private fun renderWorldChatHeads(guiGraphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.options.hideGui || minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return
        if (worldChatEntries.isEmpty()) return

        val focused = minecraft.screen is ChatScreen
        val now = minecraft.gui.guiTicks
        worldChatEntries.removeIf { entry -> !focused && now - entry.addedTick >= CHAT_FADE_TICKS }
        val scale = minecraft.gui.chat.scale
        if (scale <= 0.0) return

        val lineHeight = (9.0 * (minecraft.options.chatLineSpacing().get() + 1.0)).toInt().coerceAtLeast(1)
        val lineOffset = Math.round(-8.0 * (minecraft.options.chatLineSpacing().get() + 1.0) + 4.0 * minecraft.options.chatLineSpacing().get()).toInt()
        val chatBottom = floor((guiGraphics.guiHeight() - CHAT_BOTTOM_MARGIN) / scale).toInt()
        val headSize = (CHAT_HEAD_SIZE * scale).roundToInt().coerceAtLeast(1)
        var visualLineOffset = 0
        worldChatEntries.forEach { entry ->
            val firstLineOffset = visualLineOffset + entry.lineCount - 1
            visualLineOffset += entry.lineCount
            val age = now - entry.addedTick
            val alpha = if (focused) 1.0f else chatFade(age) * (minecraft.options.chatOpacity().get().toFloat() * 0.9f + 0.1f)
            if (alpha <= 0.02f) return@forEach
            val y = ((chatBottom - firstLineOffset * lineHeight + lineOffset) * scale).roundToInt()
            val npcX = (CHAT_LEFT_MARGIN * scale).roundToInt()
            renderNpcChatHead(guiGraphics, entry.npcId, npcX, y, headSize, alpha)
            if (entry.targetKind != "thinking") {
                val targetX = ((CHAT_LEFT_MARGIN + entry.targetHeadX) * scale).roundToInt()
                renderTargetChatHead(guiGraphics, entry.targetKind, entry.targetId, entry.targetName, targetX, y, headSize, alpha)
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderNpcChatHead(guiGraphics: GuiGraphics, npcId: String, x: Int, y: Int, size: Int, alpha: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
        val texture = npcTexture(npcId)
        guiGraphics.blit(texture, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64)
        guiGraphics.blit(texture, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64)
    }

    private fun renderTargetChatHead(guiGraphics: GuiGraphics, targetKind: String, playerId: UUID?, name: String, x: Int, y: Int, size: Int, alpha: Float) {
        if (targetKind == "discord") return renderDiscordChatHead(guiGraphics, x, y, size, alpha)
        val minecraft = Minecraft.getInstance()
        val quickSkinTexture = playerId?.let(::quickSkinChatTexture)
        val skin = playerId?.let { id -> minecraft.connection?.getPlayerInfo(id)?.skin }
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
        when {
            quickSkinTexture != null -> guiGraphics.blit(quickSkinTexture, x, y, size, size, 0.0f, 0.0f, QUICKSKIN_HEAD_TEXTURE_SIZE, QUICKSKIN_HEAD_TEXTURE_SIZE, QUICKSKIN_HEAD_TEXTURE_SIZE, QUICKSKIN_HEAD_TEXTURE_SIZE)
            skin != null -> PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size)
            else -> renderFallbackChatHead(guiGraphics, name, x, y, size, alpha)
        }
    }

    private fun renderDiscordChatHead(guiGraphics: GuiGraphics, x: Int, y: Int, size: Int, alpha: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
        guiGraphics.blit(DISCORD_CHAT_ICON, x, y, size, size, 0.0f, 0.0f, DISCORD_CHAT_ICON_TEXTURE_SIZE, DISCORD_CHAT_ICON_TEXTURE_SIZE, DISCORD_CHAT_ICON_TEXTURE_SIZE, DISCORD_CHAT_ICON_TEXTURE_SIZE)
    }

    private fun renderFallbackChatHead(guiGraphics: GuiGraphics, name: String, x: Int, y: Int, size: Int, alpha: Float) {
        val color = withAlpha(CHAT_HEAD_FALLBACK_FILL, alpha)
        guiGraphics.fill(x, y, x + size, y + size, color)
        val letter = name.trim().take(1).uppercase(Locale.ROOT).ifBlank { "?" }
        val textX = x + ((size - Minecraft.getInstance().font.width(letter)) / 2).coerceAtLeast(0)
        val textY = y + ((size - 8) / 2).coerceAtLeast(0)
        guiGraphics.drawString(Minecraft.getInstance().font, letter, textX, textY, withAlpha(0x00FFFFFF, alpha), false)
    }

    private fun quickSkinChatTexture(playerId: UUID): ResourceLocation? {
        if (quickSkinChatTextures.containsKey(playerId)) return quickSkinChatTextures[playerId]
        val texture = runCatching {
            val bytes = DiscordQuickSkinSupport.quickSkinHeadPng(playerId) ?: return@runCatching null
            val image = NativeImage.read(ByteArrayInputStream(bytes))
            val id = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "quickskin/chat_head/${playerId.toString().replace("-", "_")}")
            Minecraft.getInstance().textureManager.register(id, DynamicTexture(image))
            id
        }.getOrNull()
        quickSkinChatTextures[playerId] = texture
        return texture
    }

    private fun worldChatLine(payload: NpcWorldChatPayload): Component = Component.empty()
        .append(Component.literal(CHAT_HEAD_SPACES))
        .append(Component.literal(payload.npcName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
        .also { component ->
            if (payload.targetKind == "thinking") {
                component.append(Component.literal(" ${payload.message}").withStyle(ChatFormatting.GRAY))
            } else {
                component.append(Component.literal(" > ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(CHAT_HEAD_SPACES))
                    .append(Component.literal(payload.targetName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(": ${payload.message}").withStyle(ChatFormatting.GRAY))
            }
        }

    private fun worldChatTargetPrefix(npcName: String): Component = Component.empty()
        .append(Component.literal(CHAT_HEAD_SPACES))
        .append(Component.literal(npcName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
        .append(Component.literal(" > ").withStyle(ChatFormatting.GRAY))

    private fun worldChatLineCount(minecraft: Minecraft, line: Component): Int {
        val maxWidth = floor(minecraft.gui.chat.width / minecraft.gui.chat.scale).toInt().coerceAtLeast(1)
        return ComponentRenderUtils.wrapComponents(line, maxWidth, minecraft.font).size.coerceAtLeast(1)
    }

    private fun chatFade(ageTicks: Int): Float {
        val progress = (1.0 - (ageTicks.coerceAtLeast(0) / CHAT_FADE_TICKS.toDouble())).coerceIn(0.0, 1.0) * 10.0
        val clamped = progress.coerceIn(0.0, 1.0)
        return (clamped * clamped).toFloat()
    }

    private fun withAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0.0f, 1.0f) * 255).roundToInt() shl 24) or (rgb and 0x00FFFFFF)

    private fun friendshipDeltaLine(message: String, startedAtMs: Long, expiresAtMs: Long): NpcFriendshipDeltaLine? = when {
        message.startsWith(HEART_BALLOON_MARKER) -> NpcFriendshipDeltaLine(message.removePrefix(HEART_BALLOON_MARKER).trimStart(), HEART_BALLOON_ICON, FRIENDSHIP_DELTA_WORLD_POSITIVE, startedAtMs, expiresAtMs)
        message.startsWith(ANGRY_BALLOON_MARKER) -> NpcFriendshipDeltaLine(message.removePrefix(ANGRY_BALLOON_MARKER).trimStart(), ANGRY_BALLOON_ICON, FRIENDSHIP_DELTA_WORLD_NEGATIVE, startedAtMs, expiresAtMs)
        else -> null
    }

    private fun animationAlpha(startedAtMs: Long, expiresAtMs: Long, now: Long, fadeMs: Long): Float {
        val fadeIn = ((now - startedAtMs).toFloat() / fadeMs).coerceIn(0.0f, 1.0f)
        val fadeOut = ((expiresAtMs - now).toFloat() / fadeMs).coerceIn(0.0f, 1.0f)
        return minOf(fadeIn, fadeOut)
    }

    private fun renderFriendshipDeltaPopup(entity: LivingEntity, poseStack: PoseStack, guiGraphics: GuiGraphics, rotation: Quaternionf, font: Font, delta: NpcFriendshipDeltaLine?, now: Long) {
        if (delta == null) return
        val duration = (delta.expiresAtMs - delta.startedAtMs).coerceAtLeast(1L)
        val progress = ((now - delta.startedAtMs).toFloat() / duration).coerceIn(0.0f, 1.0f)
        val alpha = animationAlpha(delta.startedAtMs, delta.expiresAtMs, now, FRIENDSHIP_DELTA_WORLD_FADE_MS)
        if (alpha <= 0.0f) return
        val text = delta.text.ifBlank { return }
        val popupX = FRIENDSHIP_DELTA_WORLD_X
        val popupY = FRIENDSHIP_DELTA_WORLD_Y - (progress * FRIENDSHIP_DELTA_WORLD_SLIDE).roundToInt()

        poseStack.pushPose()
        poseStack.translate(0.0, entity.bbHeight + FRIENDSHIP_DELTA_WORLD_ENTITY_Y_OFFSET, 0.0)
        poseStack.mulPose(rotation)
        val scale = FRIENDSHIP_DELTA_WORLD_SCALE * (0.88f + 0.12f * alpha)
        poseStack.scale(-scale, -scale, scale)

        RenderSystem.enableBlend()
        RenderSystem.enableDepthTest()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
        guiGraphics.blit(delta.icon, popupX, popupY, FRIENDSHIP_DELTA_WORLD_ICON_SIZE, FRIENDSHIP_DELTA_WORLD_ICON_SIZE, 0.0f, 0.0f, BALLOON_ICON_TEXTURE_SIZE, BALLOON_ICON_TEXTURE_SIZE, BALLOON_ICON_TEXTURE_SIZE, BALLOON_ICON_TEXTURE_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        guiGraphics.drawString(font, text, popupX + FRIENDSHIP_DELTA_WORLD_ICON_SIZE + 2, popupY + 1, withAlpha(delta.color, alpha), false)
        guiGraphics.flush()
        RenderSystem.disableBlend()
        poseStack.popPose()
    }

    private data class NpcBalloonLine(val message: String, val startedAtMs: Long, val expiresAtMs: Long)

    private data class NpcFriendshipDeltaLine(val text: String, val icon: ResourceLocation, val color: Int, val startedAtMs: Long, val expiresAtMs: Long)

    private data class NpcWorldChatEntry(
        val npcId: String,
        val targetName: String,
        val targetId: UUID?,
        val targetKind: String,
        val addedTick: Int,
        val lineCount: Int,
        val targetHeadX: Int,
    )

    private val HIDDEN_DIALOG_HUD_LAYERS = setOf(
        VanillaGuiLayers.HOTBAR,
        VanillaGuiLayers.EXPERIENCE_BAR,
        VanillaGuiLayers.PLAYER_HEALTH,
        VanillaGuiLayers.ARMOR_LEVEL,
        VanillaGuiLayers.FOOD_LEVEL,
        VanillaGuiLayers.AIR_LEVEL,
        VanillaGuiLayers.VEHICLE_HEALTH,
    )

    private fun renderBalloonNineSlice(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        val middleWidth = (width - BALLOON_CORNER * 2).coerceAtLeast(0)
        val middleHeight = (height - BALLOON_CORNER * 2).coerceAtLeast(0)
        guiGraphics.blit(BALLOON_TEXTURE, x, y, BALLOON_CORNER, BALLOON_CORNER, 0.0f, 0.0f, BALLOON_CORNER, BALLOON_CORNER, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
        guiGraphics.blit(BALLOON_TEXTURE, x + BALLOON_CORNER, y, middleWidth, BALLOON_CORNER, BALLOON_CORNER.toFloat(), 0.0f, BALLOON_TEXTURE_SIZE - BALLOON_CORNER * 2, BALLOON_CORNER, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
        guiGraphics.blit(BALLOON_TEXTURE, x + width - BALLOON_CORNER, y, BALLOON_CORNER, BALLOON_CORNER, (BALLOON_TEXTURE_SIZE - BALLOON_CORNER).toFloat(), 0.0f, BALLOON_CORNER, BALLOON_CORNER, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
        guiGraphics.blit(BALLOON_TEXTURE, x, y + BALLOON_CORNER, BALLOON_CORNER, middleHeight, 0.0f, BALLOON_CORNER.toFloat(), BALLOON_CORNER, BALLOON_TEXTURE_SIZE - BALLOON_CORNER * 2, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
        guiGraphics.blit(BALLOON_TEXTURE, x + BALLOON_CORNER, y + BALLOON_CORNER, middleWidth, middleHeight, BALLOON_CORNER.toFloat(), BALLOON_CORNER.toFloat(), BALLOON_TEXTURE_SIZE - BALLOON_CORNER * 2, BALLOON_TEXTURE_SIZE - BALLOON_CORNER * 2, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
        guiGraphics.blit(BALLOON_TEXTURE, x + width - BALLOON_CORNER, y + BALLOON_CORNER, BALLOON_CORNER, middleHeight, (BALLOON_TEXTURE_SIZE - BALLOON_CORNER).toFloat(), BALLOON_CORNER.toFloat(), BALLOON_CORNER, BALLOON_TEXTURE_SIZE - BALLOON_CORNER * 2, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
        guiGraphics.blit(BALLOON_TEXTURE, x, y + height - BALLOON_CORNER, BALLOON_CORNER, BALLOON_CORNER, 0.0f, (BALLOON_TEXTURE_SIZE - BALLOON_CORNER).toFloat(), BALLOON_CORNER, BALLOON_CORNER, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
        guiGraphics.blit(BALLOON_TEXTURE, x + BALLOON_CORNER, y + height - BALLOON_CORNER, middleWidth, BALLOON_CORNER, BALLOON_CORNER.toFloat(), (BALLOON_TEXTURE_SIZE - BALLOON_CORNER).toFloat(), BALLOON_TEXTURE_SIZE - BALLOON_CORNER * 2, BALLOON_CORNER, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
        guiGraphics.blit(BALLOON_TEXTURE, x + width - BALLOON_CORNER, y + height - BALLOON_CORNER, BALLOON_CORNER, BALLOON_CORNER, (BALLOON_TEXTURE_SIZE - BALLOON_CORNER).toFloat(), (BALLOON_TEXTURE_SIZE - BALLOON_CORNER).toFloat(), BALLOON_CORNER, BALLOON_CORNER, BALLOON_TEXTURE_SIZE, BALLOON_TEXTURE_SIZE)
    }

    private fun toEulerXyz(quaternion: Quaternionf): Vector3f {
        val w = quaternion.w() * quaternion.w()
        val x = quaternion.x() * quaternion.x()
        val y = quaternion.y() * quaternion.y()
        val z = quaternion.z() * quaternion.z()
        val sum = w + x + y + z
        val pitchSin = 2.0f * quaternion.w() * quaternion.x() - 2.0f * quaternion.y() * quaternion.z()
        val pitch = asin((pitchSin / sum).toDouble()).toFloat()
        if (abs(pitchSin) > 0.999f * sum) return Vector3f(pitch, 2.0f * atan2(quaternion.y().toDouble(), quaternion.w().toDouble()).toFloat(), 0.0f)
        return Vector3f(
            pitch,
            atan2((2.0f * quaternion.x() * quaternion.z() + 2.0f * quaternion.y() * quaternion.w()).toDouble(), (w - x - y + z).toDouble()).toFloat(),
            atan2((2.0f * quaternion.x() * quaternion.y() + 2.0f * quaternion.w() * quaternion.z()).toDouble(), (w - x + y - z).toDouble()).toFloat(),
        )
    }

    private fun toEulerXyzDegrees(quaternion: Quaternionf): Vector3f {
        val radians = toEulerXyz(quaternion)
        return Vector3f(Math.toDegrees(radians.x().toDouble()).toFloat(), Math.toDegrees(radians.y().toDouble()).toFloat(), Math.toDegrees(radians.z().toDouble()).toFloat())
    }

    private fun balloonIcon(message: String): NpcBalloonIcon? = when {
        message.startsWith(GIFT_BALLOON_MARKER) -> NpcBalloonIcon(GIFT_BALLOON_MARKER, GIFT_BALLOON_ICON)
        message.startsWith(HEART_BALLOON_MARKER) -> NpcBalloonIcon(HEART_BALLOON_MARKER, HEART_BALLOON_ICON)
        message.startsWith(ANGRY_BALLOON_MARKER) -> NpcBalloonIcon(ANGRY_BALLOON_MARKER, ANGRY_BALLOON_ICON)
        else -> null
    }


private data class NpcBalloonIcon(val marker: String, val texture: ResourceLocation)
    private val BALLOON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/chat_bubble.png")
    private val GIFT_BALLOON_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/gift.png")
    private val HEART_BALLOON_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/heart.png")
    private val ANGRY_BALLOON_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/angry.png")
    private const val GIFT_BALLOON_MARKER = "@gift.png"
    private const val HEART_BALLOON_MARKER = "@heart.png"
    private const val ANGRY_BALLOON_MARKER = "@angry.png"
    private const val BALLOON_ICON_SIZE = 8
    private const val BALLOON_ICON_TEXTURE_SIZE = 16
    private const val BALLOON_SCALE = 0.020f
    private const val BALLOON_BG_ALPHA = 0.90f
    private const val BALLOON_FADE_MS = 180L
    private const val BALLOON_RENDER_DISTANCE_SQR = 8.0 * 8.0
    private const val BALLOON_ENTITY_Y_OFFSET = 0.9
    private const val BALLOON_MAX_TEXT_WIDTH = 118
    private const val BALLOON_MIN_WIDTH = 45
    private const val BALLOON_PADDING = 6
    private const val BALLOON_CORNER = 4
    private const val BALLOON_TEXTURE_SIZE = 16
    private const val BALLOON_LINE_HEIGHT = 9
    private const val BALLOON_TEXT_COLOR = 0xFF24201C.toInt()
    private const val FRIENDSHIP_DELTA_WORLD_SCALE = 0.021f
    private const val FRIENDSHIP_DELTA_WORLD_ENTITY_Y_OFFSET = 1.04
    private const val FRIENDSHIP_DELTA_WORLD_X = 14
    private const val FRIENDSHIP_DELTA_WORLD_Y = -22
    private const val FRIENDSHIP_DELTA_WORLD_SLIDE = 16
    private const val FRIENDSHIP_DELTA_WORLD_ICON_SIZE = 9
    private const val FRIENDSHIP_DELTA_WORLD_FADE_MS = 260L
    private const val FRIENDSHIP_DELTA_WORLD_POSITIVE = 0xFF83F28F.toInt()
    private const val FRIENDSHIP_DELTA_WORLD_NEGATIVE = 0xFFFF6F6F.toInt()
    private const val CHAT_HEAD_SPACES = "   "
    private const val CHAT_HEAD_SIZE = 8
    private const val CHAT_LEFT_MARGIN = 4
    private const val CHAT_BOTTOM_MARGIN = 40
    private const val CHAT_FADE_TICKS = 200
    private const val MAX_WORLD_CHAT_HEAD_ENTRIES = 50
    private const val QUICKSKIN_HEAD_TEXTURE_SIZE = 128
    private const val DISCORD_CHAT_ICON_TEXTURE_SIZE = 8
    private const val CHAT_HEAD_FALLBACK_FILL = 0x003D4352
    private val DISCORD_CHAT_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/fonts/discord.png")
}

private fun npcTexture(npcId: String): ResourceLocation {
    val cleanId = npcId.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_./-]"), "")
    return if (cleanId.isBlank()) STEVE_TEXTURE else ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/entity/npc/$cleanId.png")
}

private val STEVE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")

private class ChowNpcRenderer(context: EntityRendererProvider.Context) : MobRenderer<ChowNpcEntity, PlayerModel<ChowNpcEntity>>(context, ChowNpcModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f) {
    private val normalModel = ChowNpcModel(context.bakeLayer(ModelLayers.PLAYER), false)
    private val slimModel = ChowNpcModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true)

    init {
        addLayer(ItemInHandLayer(this, context.itemInHandRenderer))
    }

    override fun render(entity: ChowNpcEntity, entityYaw: Float, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        model = if (entity.bodyType == NpcBodyTypes.SLIM) slimModel else normalModel
        model.rightArmPose = if (entity.mainHandItem.isEmpty) HumanoidModel.ArmPose.EMPTY else HumanoidModel.ArmPose.ITEM
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight)
    }

    override fun getTextureLocation(entity: ChowNpcEntity): ResourceLocation = npcTexture(entity.npcId)
}

class NpcRentContractTooltip(val npcId: String, val npcName: String) : TooltipComponent

class NpcRentContractClientTooltip(private val tooltip: NpcRentContractTooltip) : ClientTooltipComponent {
    override fun getHeight(): Int = 26

    override fun getWidth(font: Font): Int = 28 + font.width(tooltip.npcName)

    override fun renderImage(font: Font, x: Int, y: Int, guiGraphics: GuiGraphics) {
        val texture = npcTexture(tooltip.npcId)
        guiGraphics.blit(texture, x, y, 24, 24, 8.0f, 8.0f, 8, 8, 64, 64)
        guiGraphics.blit(texture, x, y, 24, 24, 40.0f, 8.0f, 8, 8, 64, 64)
        guiGraphics.drawString(font, tooltip.npcName, x + 28, y + 8, 0xFFFFFFFF.toInt(), false)
    }
}

private class ChowNpcModel(root: ModelPart, slim: Boolean) : PlayerModel<ChowNpcEntity>(root, slim) {
    override fun setupAnim(entity: ChowNpcEntity, limbSwing: Float, limbSwingAmount: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)
        if (entity.scriptedAttackTicks <= 0) return
        val partialTick = (ageInTicks - entity.tickCount.toFloat()).coerceIn(0.0f, 1.0f)
        val remainingTicks = (entity.scriptedAttackTicks.toFloat() - partialTick).coerceAtLeast(0.0f)
        val progress = 1.0f - (remainingTicks / SCRIPTED_ATTACK_TICKS).coerceIn(0.0f, 1.0f)
        val windup = easeInOut((progress / 0.4f).coerceIn(0.0f, 1.0f))
        val slash = easeInOut(((progress - 0.4f) / 0.6f).coerceIn(0.0f, 1.0f))
        val followThrough = sin(progress * PI).toFloat()
        rightArm.xRot = lerp(lerp(-0.72f, -2.25f, windup), -0.5f, slash)
        rightArm.yRot = lerp(-0.08f, 0.36f, slash)
        rightArm.zRot = lerp(0.16f, -0.28f, followThrough)
        body.yRot = followThrough * 0.1f
        rightSleeve.copyFrom(rightArm)
        jacket.copyFrom(body)
    }

    private fun easeInOut(progress: Float): Float = progress * progress * (3.0f - 2.0f * progress)

    private fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress

    companion object {
        private const val SCRIPTED_ATTACK_TICKS = 9.0f
    }
}

private class NpcDialogScreen(private val payload: NpcDialogPayload) : Screen(Component.literal(payload.name)) {
    private val openedAtMs: Long = System.currentTimeMillis()
    private var messageStartedAtMs: Long = openedAtMs
    private var displayMessage: String = payload.message
    private var lastAnimaleseIndex: Int = 0
    private var talkMode: Boolean = payload.startTalkMode
    private var waitingForTalk: Boolean = payload.message == "..."
    private var activeResponseToken: Long = payload.responseToken
    private var talkModeChangedAtMs: Long = openedAtMs
    private var animatedPanelHeight: Float = BASE_PANEL_HEIGHT.toFloat()
    private var panelHeightAnimationFrom: Float = BASE_PANEL_HEIGHT.toFloat()
    private var panelHeightAnimationTarget: Int = BASE_PANEL_HEIGHT
    private var panelHeightAnimationStartedAtMs: Long = openedAtMs
    private var talkInput: EditBox? = null

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun renderTransparentBackground(guiGraphics: GuiGraphics) = Unit

    override fun renderBlurredBackground(partialTick: Float) = Unit

    override fun init() {
        val layout = layout()
        talkInput = EditBox(font, layout.inputX, layout.inputY, layout.inputWidth, INPUT_HEIGHT, Component.literal("Message ${payload.name}...")).apply {
            setMaxLength(280)
            setHint(Component.literal("Message ${payload.name}..."))
            visible = talkMode
        }
        talkInput?.let(::addRenderableWidget)
        if (talkMode) {
            talkInput?.isFocused = true
            setFocused(talkInput)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val layout = layout()
        updateTalkInput(layout)
        val panelWidth = layout.panelWidth
        val panelHeight = layout.panelHeight
        val x = layout.x
        val y = layout.y
        val progress = entranceProgress()
        val scale = 0.92f + progress * 0.08f
        val slideY = (18.0f * (1.0f - progress))
        val anchorX = x + panelWidth / 2.0f
        val anchorY = y + panelHeight.toFloat()
        val localMouse = localMouse(mouseX, mouseY, anchorX, anchorY, slideY, scale)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(anchorX, anchorY + slideY, 0.0f)
        pose.scale(scale, scale, 1.0f)
        pose.translate(-anchorX, -anchorY, 0.0f)
        renderNineSlice(guiGraphics, PANEL_TEXTURE, x, y, panelWidth, panelHeight, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER)
        renderAvatar(guiGraphics, payload.npcId, x + PAD, y + PAD)

        val nameX = x + PAD + AVATAR_SIZE + TEXT_GAP
        val buttonX = x + panelWidth - PAD - BUTTON_WIDTH
        val dialogX = x + PAD
        val dialogY = y + PAD + AVATAR_SIZE + 10
        val dialogWidth = buttonX - dialogX - TEXT_GAP
        val buttonTop = buttonTop(panelHeight)
        val name = ckdmText(payload.name)
        drawScaledString(guiGraphics, name, nameX, y + NAME_Y, NAME_SCALE, NAME_COLOR, NAME_SHADOW)
        renderFriendship(guiGraphics, nameX, y + FRIENDSHIP_Y, payload.friendshipLevel)
        renderFriendshipDelta(guiGraphics, nameX + FRIENDSHIP_ICON_COUNT * FRIENDSHIP_ICON_STEP + 5, y + FRIENDSHIP_Y + 1, payload.friendshipDelta)

        talkInput?.visible = talkMode
        val visibleCharacters = visibleDialogCharacters()
        playAnimalese(visibleCharacters)
        val visibleMessage = currentRenderMessage().take(visibleCharacters)
        val lines = font.split(ckdmDialogText(visibleMessage), dialogWidth)
        var lineY = dialogY
        lines.take(layout.dialogLineLimit).forEach { line ->
            guiGraphics.drawString(font, line, dialogX + 1, lineY + 1, DIALOG_SHADOW, false)
            guiGraphics.drawString(font, line, dialogX, lineY, DIALOG_COLOR, false)
            lineY += LINE_HEIGHT
        }
        if (talkMode) talkInput?.render(guiGraphics, localMouse.first, localMouse.second, partialTick)
        val hoveredAction = actionAt(localMouse.first, localMouse.second)
        renderActionButtons(guiGraphics, localMouse.first, localMouse.second, buttonX, y + buttonTop)
        pose.popPose()
        renderActionTooltip(guiGraphics, mouseX, mouseY, hoveredAction)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return true
        val layout = layout()
        updateTalkInput(layout)
        val panelWidth = layout.panelWidth
        val panelHeight = layout.panelHeight
        val x = layout.x
        val y = layout.y
        val progress = entranceProgress()
        val scale = 0.92f + progress * 0.08f
        val slideY = 18.0f * (1.0f - progress)
        val localMouse = localMouse(mouseX.toInt(), mouseY.toInt(), x + panelWidth / 2.0f, y + panelHeight.toFloat(), slideY, scale)
        if (talkMode && talkInput?.mouseClicked(localMouse.first.toDouble(), localMouse.second.toDouble(), button) == true) return true
        val action = actionAt(localMouse.first, localMouse.second) ?: return true
        when (action) {
            DialogAction.Buy -> if (isActionEnabled(action)) {
                skipPendingTalkResponse()
                NpcNetwork.sendAction(payload.npcId, "buy")
            }
            DialogAction.Gift -> if (isActionEnabled(action)) {
                skipPendingTalkResponse()
                NpcNetwork.sendAction(payload.npcId, "gift")
            }
            DialogAction.Bye -> onClose()
            DialogAction.Talk -> if (!waitingForTalk && payload.talkEnabled) {
                if (talkMode) sendTalkMessage() else {
                    NpcNetwork.sendAction(payload.npcId, "join_talk")
                    enterTalkMode()
                }
            }
        }
        return true
    }

    override fun onClose() {
        val wasWaitingForTalk = waitingForTalk
        val wasInTalkMode = talkMode
        skipPendingTalkResponse()
        if (wasWaitingForTalk || wasInTalkMode) NpcNetwork.sendAction(payload.npcId, "cancel_llm")
        super.onClose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (talkMode && keyCode == 257 && !waitingForTalk) {
            sendTalkMessage()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    fun receiveTalkResponse(response: NpcTalkResponsePayload) {
        if (response.npcId != payload.npcId) return
        if (NpcClient.shouldIgnoreTalkResponse(response.responseToken)) return
        if (response.responseToken != 0L && response.responseToken != activeResponseToken) return
        waitingForTalk = false
        activeResponseToken = 0L
        updateDisplayMessage(response.message)
    }

    private fun localMouse(mouseX: Int, mouseY: Int, anchorX: Float, anchorY: Float, slideY: Float, scale: Float): Pair<Int, Int> = Pair(
        (anchorX + (mouseX - anchorX) / scale).toInt(),
        (anchorY + (mouseY - anchorY - slideY) / scale).toInt(),
    )

    private fun renderActionButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int) {
        val giftStack = giftStack()
        actions().forEachIndexed { index, action ->
            val buttonY = y + index * BUTTON_STEP
            val hovered = mouseX in x until x + BUTTON_WIDTH && mouseY in buttonY until buttonY + BUTTON_HEIGHT
            val enabled = isActionEnabled(action, giftStack)
            val activeHover = hovered && enabled
            val texture = if (activeHover && action == DialogAction.Bye) RED_BUTTON_HOVER_TEXTURE else if (activeHover) BUTTON_HOVER_TEXTURE else BUTTON_TEXTURE
            val sourceSize = if (activeHover) BUTTON_HOVER_TEXTURE_SIZE else BUTTON_TEXTURE_SIZE
            val sourceCorner = if (activeHover) BUTTON_HOVER_SOURCE_CORNER else BUTTON_SOURCE_CORNER
            val textColor = if (enabled) NAME_COLOR else DISABLED_COLOR
            renderNineSlice(guiGraphics, texture, x, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, sourceSize, sourceSize, sourceCorner, BUTTON_DEST_CORNER)
            guiGraphics.blit(action.icon, x + 6, buttonY + 3, ICON_SIZE, ICON_SIZE, 0.0f, 0.0f, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE)
            guiGraphics.drawString(font, ckdmSmallText(actionLabel(action)), x + 25, buttonY + 6, textColor, false)
            if (action == DialogAction.Gift && hovered && !giftStack.isEmpty) guiGraphics.renderItem(giftStack.copyWithCount(1), x + BUTTON_WIDTH - 19, buttonY + 2)
        }
    }

    private fun renderFriendship(guiGraphics: GuiGraphics, x: Int, y: Int, level: Int) {
        val clamped = level.coerceIn(-10, 10)
        repeat(FRIENDSHIP_ICON_COUNT) { index ->
            val texture = when {
                clamped > 0 && index < clamped -> HEART_TEXTURE
                clamped < 0 && index < -clamped -> ANGRY_TEXTURE
                else -> HEART_EMPTY_TEXTURE
            }
            guiGraphics.blit(texture, x + index * FRIENDSHIP_ICON_STEP, y, FRIENDSHIP_ICON_SIZE, FRIENDSHIP_ICON_SIZE, 0.0f, 0.0f, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE)
        }
    }

    private fun renderFriendshipDelta(guiGraphics: GuiGraphics, x: Int, y: Int, delta: Int) {
        if (delta == 0) return
        val elapsed = System.currentTimeMillis() - messageStartedAtMs
        val alpha = when {
            elapsed < FRIENDSHIP_DELTA_FADE_MS -> elapsed.toFloat() / FRIENDSHIP_DELTA_FADE_MS
            elapsed > FRIENDSHIP_DELTA_DURATION_MS - FRIENDSHIP_DELTA_FADE_MS -> ((FRIENDSHIP_DELTA_DURATION_MS - elapsed).toFloat() / FRIENDSHIP_DELTA_FADE_MS).coerceAtLeast(0.0f)
            else -> 1.0f
        }
        if (alpha <= 0.0f) return
        val sign = if (delta > 0) "+" else ""
        val color = dialogWithAlpha(if (delta > 0) FRIENDSHIP_DELTA_POSITIVE else FRIENDSHIP_DELTA_NEGATIVE, alpha)
        val shadow = dialogWithAlpha(NAME_SHADOW, alpha)
        val text = ckdmSmallText("$sign$delta")
        guiGraphics.drawString(font, text, x + 1, y + 1, shadow, false)
        guiGraphics.drawString(font, text, x, y, color, false)
    }

    private fun dialogWithAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0.0f, 1.0f) * 255).roundToInt() shl 24) or (rgb and 0x00FFFFFF)

    private fun drawScaledString(guiGraphics: GuiGraphics, text: Component, x: Int, y: Int, scale: Float, color: Int, shadowColor: Int) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0.0f)
        pose.scale(scale, scale, 1.0f)
        guiGraphics.drawString(font, text, 1, 1, shadowColor, false)
        guiGraphics.drawString(font, text, 0, 0, color, false)
        pose.popPose()
    }

    private fun actionLabel(action: DialogAction): String = when {
        talkMode && action == DialogAction.Talk -> "SEND"
        joinMode() && action == DialogAction.Talk -> "JOIN CONVERSATION"
        action == DialogAction.Bye && payload.closeOnly -> payload.closeLabel
        else -> action.label
    }

    private fun renderActionTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, action: DialogAction?) {
        if (talkMode || action != DialogAction.Gift) return
        if (waitingForTalk) return
        if (payload.talkEnabled) return
        val giftStack = giftStack()
        if (giftStack.isEmpty) {
            guiGraphics.renderTooltip(font, Component.literal("Hold an item to gift."), mouseX, mouseY)
        } else {
            guiGraphics.renderTooltip(font, Component.literal("Gift ${giftStack.hoverName.string}"), mouseX, mouseY)
        }
    }

    private fun giftStack(): ItemStack {
        val player = Minecraft.getInstance().player ?: return ItemStack.EMPTY
        return if (!player.mainHandItem.isEmpty) player.mainHandItem else player.offhandItem
    }

    private fun actionAt(mouseX: Int, mouseY: Int): DialogAction? {
        val layout = layout()
        val panelWidth = layout.panelWidth
        val panelHeight = layout.panelHeight
        val x = layout.x
        val y = layout.y
        val buttonX = x + panelWidth - PAD - BUTTON_WIDTH
        val buttonY = y + buttonTop(panelHeight)
        return actions().firstOrNull { action ->
            val top = buttonY + actions().indexOf(action) * BUTTON_STEP
            mouseX in buttonX until buttonX + BUTTON_WIDTH && mouseY in top until top + BUTTON_HEIGHT
        }
    }

    private fun actions(): List<DialogAction> = when {
        joinMode() -> listOf(DialogAction.Talk, DialogAction.Bye)
        payload.closeOnly -> listOf(DialogAction.Bye)
        else -> DialogAction.entries
    }

    private fun joinMode(): Boolean = payload.dialogMode == "join"

    private fun isActionEnabled(action: DialogAction, giftStack: ItemStack = giftStack()): Boolean = when {
        waitingForTalk -> action != DialogAction.Talk
        action == DialogAction.Gift -> payload.talkEnabled || !giftStack.isEmpty
        action == DialogAction.Talk -> payload.talkEnabled
        else -> true
    }

    private fun buttonTop(panelHeight: Int): Int = if (payload.closeOnly) (panelHeight - BUTTON_HEIGHT) / 2 else BUTTON_TOP

    private fun layout(): DialogLayout {
        val panelWidth = 356.coerceAtMost(width - 24)
        val dialogWidth = panelWidth - PAD * 2 - BUTTON_WIDTH - TEXT_GAP
        val fullLineCount = dialogLineCount(dialogWidth).coerceIn(1, BASE_DIALOG_LINES + MAX_EXTRA_DIALOG_LINES)
        val extraLines = (fullLineCount - BASE_DIALOG_LINES).coerceAtLeast(0)
        val talkReserve = ((INPUT_HEIGHT + INPUT_GAP) * talkProgress()).toInt()
        val maxPanelHeight = (height - 24 - talkReserve).coerceAtLeast(BASE_PANEL_HEIGHT)
        val targetPanelHeight = (BASE_PANEL_HEIGHT + extraLines * LINE_HEIGHT + if (extraLines > 0) DYNAMIC_BOTTOM_PAD else 0).coerceAtMost(maxPanelHeight)
        val panelHeight = animatedPanelHeight(targetPanelHeight)
        val x = (width - panelWidth) / 2
        val y = height - panelHeight - 34 - talkReserve
        return DialogLayout(
            x = x,
            y = y,
            panelWidth = panelWidth,
            panelHeight = panelHeight,
            inputX = x + PAD,
            inputY = y + panelHeight + INPUT_GAP,
            inputWidth = panelWidth - PAD * 2,
            dialogLineLimit = fullLineCount.coerceAtLeast(BASE_DIALOG_LINES),
        )
    }

    private fun updateTalkInput(layout: DialogLayout) {
        talkInput?.setX(layout.inputX)
        talkInput?.setY(layout.inputY)
        talkInput?.width = layout.inputWidth
        talkInput?.visible = talkMode
    }

    private fun animatedPanelHeight(targetPanelHeight: Int): Int {
        if (targetPanelHeight != panelHeightAnimationTarget) {
            panelHeightAnimationFrom = animatedPanelHeight
            panelHeightAnimationTarget = targetPanelHeight
            panelHeightAnimationStartedAtMs = System.currentTimeMillis()
        }
        val progress = ((System.currentTimeMillis() - panelHeightAnimationStartedAtMs).toFloat() / PANEL_HEIGHT_DURATION_MS).coerceIn(0.0f, 1.0f)
        val eased = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress)
        animatedPanelHeight = panelHeightAnimationFrom + (panelHeightAnimationTarget - panelHeightAnimationFrom) * eased
        return animatedPanelHeight.toInt()
    }

    private fun dialogLineCount(dialogWidth: Int): Int {
        val wrapped = font.split(ckdmDialogText(displayMessage), dialogWidth).size.coerceAtLeast(1)
        val averageCharsPerLine = (dialogWidth / 6).coerceAtLeast(24)
        val estimated = (displayMessage.length + averageCharsPerLine - 1) / averageCharsPerLine
        return maxOf(wrapped, estimated)
    }

    private fun talkProgress(): Float {
        if (!talkMode) return 0.0f
        val progress = ((System.currentTimeMillis() - talkModeChangedAtMs).toFloat() / TALK_LAYOUT_DURATION_MS).coerceIn(0.0f, 1.0f)
        return 1.0f - (1.0f - progress) * (1.0f - progress)
    }

    private fun renderAvatar(guiGraphics: GuiGraphics, npcId: String, x: Int, y: Int) {
        val texture = npcTexture(npcId)
        guiGraphics.blit(texture, x, y, AVATAR_SIZE, AVATAR_SIZE, 8.0f, 8.0f, 8, 8, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE)
        guiGraphics.blit(texture, x, y, AVATAR_SIZE, AVATAR_SIZE, 40.0f, 8.0f, 8, 8, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE)
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, textureWidth: Int, textureHeight: Int, sourceCorner: Int, destinationCorner: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        val edgeX = textureWidth - sourceCorner
        val edgeY = textureHeight - sourceCorner
        val middleWidth = textureWidth - sourceCorner * 2
        val middleHeight = textureHeight - sourceCorner * 2
        val innerWidth = (width - destinationCorner * 2).coerceAtLeast(0)
        val innerHeight = (height - destinationCorner * 2).coerceAtLeast(0)
        blit(guiGraphics, texture, x, y, destinationCorner, destinationCorner, 0, 0, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + destinationCorner, y, innerWidth, destinationCorner, sourceCorner, 0, middleWidth, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + width - destinationCorner, y, destinationCorner, destinationCorner, edgeX, 0, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x, y + destinationCorner, destinationCorner, innerHeight, 0, sourceCorner, sourceCorner, middleHeight, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + destinationCorner, y + destinationCorner, innerWidth, innerHeight, sourceCorner, sourceCorner, middleWidth, middleHeight, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + width - destinationCorner, y + destinationCorner, destinationCorner, innerHeight, edgeX, sourceCorner, sourceCorner, middleHeight, textureWidth, textureHeight)
        blit(guiGraphics, texture, x, y + height - destinationCorner, destinationCorner, destinationCorner, 0, edgeY, sourceCorner, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + destinationCorner, y + height - destinationCorner, innerWidth, destinationCorner, sourceCorner, edgeY, middleWidth, sourceCorner, textureWidth, textureHeight)
        blit(guiGraphics, texture, x + width - destinationCorner, y + height - destinationCorner, destinationCorner, destinationCorner, edgeX, edgeY, sourceCorner, sourceCorner, textureWidth, textureHeight)
    }

    private fun blit(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int, textureWidth: Int, textureHeight: Int) {
        if (width <= 0 || height <= 0) return
        guiGraphics.blit(texture, x, y, width, height, sourceX.toFloat(), sourceY.toFloat(), sourceWidth, sourceHeight, textureWidth, textureHeight)
    }

    private fun ckdmText(text: String): Component = Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(CKDM_BOLD_FONT) }

    private fun ckdmSmallText(text: String): Component = Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(CKDM_SMALL_FONT) }

    private fun ckdmDialogText(text: String): Component = Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(CKDM_SMALL_FONT) }

    private fun enterTalkMode() {
        talkMode = true
        talkModeChangedAtMs = System.currentTimeMillis()
        talkInput?.visible = true
        talkInput?.isFocused = true
        setFocused(talkInput)
    }

    private fun exitTalkMode() {
        talkMode = false
        waitingForTalk = false
        talkInput?.visible = false
        setFocused(null)
    }

    private fun sendTalkMessage() {
        if (waitingForTalk) return
        val text = talkInput?.value?.trim().orEmpty()
        if (text.isBlank()) return
        talkInput?.value = ""
        waitingForTalk = true
        activeResponseToken = NpcDialogTokens.next()
        updateDisplayMessage("...")
        NpcNetwork.sendTalk(payload.npcId, text, activeResponseToken)
    }

    private fun skipPendingTalkResponse() {
        if (!waitingForTalk) return
        NpcClient.skipTalkResponse(activeResponseToken)
        waitingForTalk = false
        activeResponseToken = 0L
    }

    private fun updateDisplayMessage(message: String) {
        displayMessage = message
        messageStartedAtMs = System.currentTimeMillis()
        lastAnimaleseIndex = 0
    }

    private fun currentRenderMessage(): String = if (waitingForTalk) ".".repeat(((System.currentTimeMillis() / 300L) % 3L + 1L).toInt()) else displayMessage

    private fun entranceProgress(): Float {
        val progress = ((System.currentTimeMillis() - openedAtMs).toFloat() / ENTRANCE_DURATION_MS).coerceIn(0.0f, 1.0f)
        return 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress)
    }

    private fun visibleDialogCharacters(): Int {
        val elapsed = (System.currentTimeMillis() - messageStartedAtMs - TYPEWRITER_DELAY_MS).coerceAtLeast(0L)
        return ((elapsed / 1000.0) * TYPEWRITER_CHARS_PER_SECOND).toInt().coerceIn(0, currentRenderMessage().length)
    }

    private fun playAnimalese(visibleCharacters: Int) {
        if (visibleCharacters <= lastAnimaleseIndex) return
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = minecraft.level ?: return
        val npc = level.getEntity(payload.npcEntityId)
        val x = npc?.x ?: player.x
        val y = npc?.y ?: player.y
        val z = npc?.z ?: player.z
        val distance = npc?.distanceTo(player) ?: 0.0f
        val radius = payload.animaleseRadius.coerceAtLeast(1.0f)
        if (distance > radius) {
            lastAnimaleseIndex = visibleCharacters
            return
        }
        val distanceVolume = (1.0f - distance / radius).coerceIn(0.0f, 1.0f)
        val volume = payload.animaleseVolume.coerceIn(0.0f, 1.0f) * distanceVolume
        if (volume <= 0.01f) {
            lastAnimaleseIndex = visibleCharacters
            return
        }
        val message = currentRenderMessage()
        val end = visibleCharacters.coerceAtMost(message.length)
        for (index in lastAnimaleseIndex until end) {
            val soundIndex = animaleseSoundIndex(message, index) ?: continue
            val sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc.animalese.${animalesePitch()}.sound${soundIndex.toString().padStart(2, '0')}"))
            val pitch = (payload.animalesePitchMultiplier * (0.96f + player.random.nextFloat() * 0.08f)).coerceIn(0.5f, 2.0f)
            minecraft.soundManager.play(SimpleSoundInstance(sound, SoundSource.VOICE, volume, pitch, RandomSource.create(), x, y, z))
        }
        lastAnimaleseIndex = visibleCharacters
    }

    private fun animalesePitch(): String = payload.animalesePitch.lowercase(Locale.ROOT).let { value -> if (value in ANIMALESE_PITCHES) value else "med" }

    private fun animaleseSoundIndex(message: String, index: Int): Int? {
        val char = message[index].lowercaseChar()
        if (index > 0 && char == message[index - 1].lowercaseChar()) return null
        if (char == 's' && index + 1 < message.length && message[index + 1].lowercaseChar() == 'h') return 28
        if (char == 't' && index + 1 < message.length && message[index + 1].lowercaseChar() == 'h') return 27
        if (char == 'h' && index > 0) {
            return when (message[index - 1].lowercaseChar()) {
                't', 's' -> null
                else -> null
            }
        }
        if (char in 'a'..'z') return char - 'a' + 1
        return when (char) {
            '.', ',', '?' -> 30
            else -> null
        }
    }

    companion object {
        private val PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_grey.png")
        private val CKDM_BOLD_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
        private val CKDM_SMALL_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
        private val BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_gray.png")
        private val BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green_hover.png")
        private val RED_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_red_hover.png")
        private val HEART_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/heart.png")
        private val HEART_EMPTY_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/heart_empty.png")
        private val ANGRY_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/angry.png")
        private const val PANEL_TEXTURE_WIDTH = 1646
        private const val PANEL_TEXTURE_HEIGHT = 256
        private const val PANEL_SOURCE_CORNER = 75
        private const val PANEL_DEST_CORNER = 13
        private const val PAD = 14
        private const val BASE_PANEL_HEIGHT = 112
        private const val AVATAR_SIZE = 42
        private const val SKIN_TEXTURE_SIZE = 64
        private const val TEXT_GAP = 12
        private const val LINE_HEIGHT = 11
        private const val NAME_Y = 19
        private const val NAME_SCALE = 1.25f
        private const val FRIENDSHIP_Y = 43
        private const val BASE_DIALOG_LINES = 4
        private const val MAX_EXTRA_DIALOG_LINES = 5
        private const val INPUT_HEIGHT = 18
        private const val INPUT_GAP = 16
        private const val DYNAMIC_BOTTOM_PAD = 11
        private const val BUTTON_WIDTH = 112
        private const val BUTTON_HEIGHT = 20
        private const val BUTTON_TOP = 14
        private const val BUTTON_STEP = 23
        private const val BUTTON_TEXTURE_SIZE = 8
        private const val BUTTON_HOVER_TEXTURE_SIZE = 10
        private const val BUTTON_SOURCE_CORNER = 2
        private const val BUTTON_HOVER_SOURCE_CORNER = 3
        private const val BUTTON_DEST_CORNER = 4
        private const val ICON_SIZE = 14
        private const val ICON_SOURCE_SIZE = 16
        private const val FRIENDSHIP_ICON_COUNT = 10
        private const val FRIENDSHIP_ICON_SIZE = 10
        private const val FRIENDSHIP_ICON_STEP = 11
        private const val ENTRANCE_DURATION_MS = 180.0f
        private const val PANEL_HEIGHT_DURATION_MS = 140.0f
        private const val TALK_LAYOUT_DURATION_MS = 160.0f
        private const val TYPEWRITER_DELAY_MS = 110L
        private const val TYPEWRITER_CHARS_PER_SECOND = 68.0
        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val NAME_SHADOW = 0xCC050505.toInt()
        private const val DIALOG_COLOR = 0xFFFFFFFF.toInt()
        private const val DIALOG_SHADOW = 0xCC050505.toInt()
        private const val FRIENDSHIP_DELTA_POSITIVE = 0xFF83F28F.toInt()
        private const val FRIENDSHIP_DELTA_NEGATIVE = 0xFFFF6F6F.toInt()
        private const val FRIENDSHIP_DELTA_DURATION_MS = 2200L
        private const val FRIENDSHIP_DELTA_FADE_MS = 260L
        private const val CONTRACT_COLOR = 0xFF83F28F.toInt()
        private const val DISABLED_COLOR = 0xFF8C8778.toInt()
        private val ANIMALESE_PITCHES = setOf("high", "med", "low", "lowest")
    }
}

private data class DialogLayout(
    val x: Int,
    val y: Int,
    val panelWidth: Int,
    val panelHeight: Int,
    val inputX: Int,
    val inputY: Int,
    val inputWidth: Int,
    val dialogLineLimit: Int,
)

private enum class DialogAction(val label: String, val icon: ResourceLocation) {
    Talk("TALK", ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/chat_bubble_white.png")),
    Buy("BUY", ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/shop.png")),
    Gift("GIFT", ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/gift.png")),
    Bye("BYE", ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/chat_bubble_orange.png")),
}
