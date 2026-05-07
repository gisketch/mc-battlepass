package dev.gisketch.chowkingdom.npc

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import java.util.Locale

object NpcClient {
    @JvmStatic
    fun openDialog(payload: NpcDialogPayload) {
        Minecraft.getInstance().setScreen(NpcDialogScreen(payload))
    }

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerRenderers)
        NeoForge.EVENT_BUS.addListener(::hideHotbarDuringDialog)
    }

    @JvmStatic
    fun isDialogOpen(): Boolean = Minecraft.getInstance().screen is NpcDialogScreen

    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(NpcFeature.NPC_ENTITY.get(), ::ChowNpcRenderer)
    }

    private fun hideHotbarDuringDialog(event: RenderGuiLayerEvent.Pre) {
        if (event.name == VanillaGuiLayers.HOTBAR && Minecraft.getInstance().screen is NpcDialogScreen) event.isCanceled = true
    }
}

private class ChowNpcRenderer(context: EntityRendererProvider.Context) : MobRenderer<ChowNpcEntity, PlayerModel<ChowNpcEntity>>(context, PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f) {
    private val normalModel = PlayerModel<ChowNpcEntity>(context.bakeLayer(ModelLayers.PLAYER), false)
    private val slimModel = PlayerModel<ChowNpcEntity>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true)

    init {
        addLayer(ItemInHandLayer(this, context.itemInHandRenderer))
    }

    override fun render(entity: ChowNpcEntity, entityYaw: Float, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        model = if (entity.bodyType == NpcBodyTypes.SLIM) slimModel else normalModel
        model.rightArmPose = if (entity.mainHandItem.isEmpty) HumanoidModel.ArmPose.EMPTY else HumanoidModel.ArmPose.ITEM
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight)
    }

    override fun getTextureLocation(entity: ChowNpcEntity): ResourceLocation = if (entity.npcId == "finn") FINN_TEXTURE else STEVE_TEXTURE

    companion object {
        private val FINN_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/entity/npc/finn.png")
        private val STEVE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")
    }
}

private class NpcDialogScreen(private val payload: NpcDialogPayload) : Screen(Component.literal(payload.name)) {
    private val openedAtMs: Long = System.currentTimeMillis()

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun renderTransparentBackground(guiGraphics: GuiGraphics) = Unit

    override fun renderBlurredBackground(partialTick: Float) = Unit

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val panelWidth = 356.coerceAtMost(width - 24)
        val panelHeight = 112.coerceAtMost(height - 24)
        val x = (width - panelWidth) / 2
        val y = height - panelHeight - 34
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
        guiGraphics.drawString(font, name, nameX + 1, y + NAME_Y + 1, NAME_SHADOW, false)
        guiGraphics.drawString(font, name, nameX, y + NAME_Y, NAME_COLOR, false)
        renderFriendship(guiGraphics, nameX, y + FRIENDSHIP_Y, payload.friendshipLevel)

        val visibleMessage = payload.message.take(visibleDialogCharacters())
        val lines = font.split(ckdmDialogText(visibleMessage), dialogWidth)
        var lineY = dialogY
        lines.take(MAX_DIALOG_LINES).forEach { line ->
            guiGraphics.drawString(font, line, dialogX, lineY, DIALOG_COLOR, false)
            lineY += LINE_HEIGHT
        }
        if (payload.contractGranted && visibleMessage.length >= payload.message.length) guiGraphics.drawString(font, "Rent Contract received", dialogX, y + panelHeight - 16, CONTRACT_COLOR, false)
        val hoveredAction = actionAt(localMouse.first, localMouse.second)
        renderActionButtons(guiGraphics, localMouse.first, localMouse.second, buttonX, y + buttonTop)
        pose.popPose()
        renderActionTooltip(guiGraphics, mouseX, mouseY, hoveredAction)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return true
        val panelWidth = 356.coerceAtMost(width - 24)
        val panelHeight = 112.coerceAtMost(height - 24)
        val x = (width - panelWidth) / 2
        val y = height - panelHeight - 34
        val progress = entranceProgress()
        val scale = 0.92f + progress * 0.08f
        val slideY = 18.0f * (1.0f - progress)
        val localMouse = localMouse(mouseX.toInt(), mouseY.toInt(), x + panelWidth / 2.0f, y + panelHeight.toFloat(), slideY, scale)
        val action = actionAt(localMouse.first, localMouse.second) ?: return true
        when (action) {
            DialogAction.Buy -> NpcNetwork.sendAction(payload.npcId, "buy")
            DialogAction.Gift -> if (!giftStack().isEmpty) NpcNetwork.sendAction(payload.npcId, "gift")
            DialogAction.Bye -> onClose()
            DialogAction.Talk -> Unit
        }
        return true
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
            val enabled = action != DialogAction.Gift || !giftStack.isEmpty
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

    private fun actionLabel(action: DialogAction): String = if (action == DialogAction.Bye && payload.closeOnly) payload.closeLabel else action.label

    private fun renderActionTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, action: DialogAction?) {
        if (action != DialogAction.Gift) return
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
        val panelWidth = 356.coerceAtMost(width - 24)
        val panelHeight = 112.coerceAtMost(height - 24)
        val x = (width - panelWidth) / 2
        val y = height - panelHeight - 34
        val buttonX = x + panelWidth - PAD - BUTTON_WIDTH
        val buttonY = y + buttonTop(panelHeight)
        return actions().firstOrNull { action ->
            val top = buttonY + actions().indexOf(action) * BUTTON_STEP
            mouseX in buttonX until buttonX + BUTTON_WIDTH && mouseY in top until top + BUTTON_HEIGHT
        }
    }

    private fun actions(): List<DialogAction> = if (payload.closeOnly) listOf(DialogAction.Bye) else DialogAction.entries

    private fun buttonTop(panelHeight: Int): Int = if (payload.closeOnly) (panelHeight - BUTTON_HEIGHT) / 2 else BUTTON_TOP

    private fun renderAvatar(guiGraphics: GuiGraphics, npcId: String, x: Int, y: Int) {
        val texture = if (npcId == "finn") FINN_TEXTURE else STEVE_TEXTURE
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

    private fun entranceProgress(): Float {
        val progress = ((System.currentTimeMillis() - openedAtMs).toFloat() / ENTRANCE_DURATION_MS).coerceIn(0.0f, 1.0f)
        return 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress)
    }

    private fun visibleDialogCharacters(): Int {
        val elapsed = (System.currentTimeMillis() - openedAtMs - TYPEWRITER_DELAY_MS).coerceAtLeast(0L)
        return ((elapsed / 1000.0) * TYPEWRITER_CHARS_PER_SECOND).toInt().coerceIn(0, payload.message.length)
    }

    companion object {
        private val PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_grey.png")
        private val CKDM_BOLD_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
        private val CKDM_SMALL_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
        private val FINN_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/entity/npc/finn.png")
        private val STEVE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")
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
        private const val AVATAR_SIZE = 42
        private const val SKIN_TEXTURE_SIZE = 64
        private const val TEXT_GAP = 12
        private const val LINE_HEIGHT = 11
        private const val NAME_Y = 15
        private const val FRIENDSHIP_Y = 33
        private const val MAX_DIALOG_LINES = 4
        private const val BUTTON_WIDTH = 74
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
        private const val FRIENDSHIP_ICON_SIZE = 8
        private const val FRIENDSHIP_ICON_STEP = 9
        private const val ENTRANCE_DURATION_MS = 180.0f
        private const val TYPEWRITER_DELAY_MS = 110L
        private const val TYPEWRITER_CHARS_PER_SECOND = 68.0
        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val NAME_SHADOW = 0xCC050505.toInt()
        private const val DIALOG_COLOR = 0xBFFFFFFF.toInt()
        private const val CONTRACT_COLOR = 0xFF83F28F.toInt()
        private const val DISABLED_COLOR = 0xFF8C8778.toInt()
    }
}

private enum class DialogAction(val label: String, val icon: ResourceLocation) {
    Talk("TALK", ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/chat_bubble_white.png")),
    Buy("BUY", ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/shop.png")),
    Gift("GIFT", ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/gift.png")),
    Bye("BYE", ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/chat_bubble_orange.png")),
}