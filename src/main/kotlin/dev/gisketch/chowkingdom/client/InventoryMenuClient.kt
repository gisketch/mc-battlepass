package dev.gisketch.chowkingdom.client

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClient
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.battlepass.BattlepassPassDefinition
import dev.gisketch.chowkingdom.battlepass.BattlepassPassRegistry
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.npc.NpcFriendsClient
import dev.gisketch.chowkingdom.skilltree.ClassSkillTreeClient
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.glfw.GLFW
import java.util.Locale

object InventoryMenuClient {
    private var activeScreen: Screen? = null
    private var openedAtMs = 0L
    private var renderAlpha = 1.0f
    private var showingPasses = false
    private var pendingPassMenu = false

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onScreenRenderPost)
        NeoForge.EVENT_BUS.addListener(::onMousePressed)
    }

    fun openPassMenuOnNextInventory() {
        pendingPassMenu = true
        if (Minecraft.getInstance().screen is InventoryScreen) showingPasses = true
    }

    private fun onScreenRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen as? InventoryScreen ?: return
        if (activeScreen !== screen) {
            activeScreen = screen
            openedAtMs = Util.getMillis()
            showingPasses = pendingPassMenu
            pendingPassMenu = false
        }
        renderAlpha = 1.0f
        renderMenu(event.guiGraphics, screen, event.mouseX, event.mouseY)
    }

    private fun onMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        val screen = event.screen as? InventoryScreen ?: return
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return
        val mouseX = event.mouseX.toInt()
        val mouseY = event.mouseY.toInt()
        val action = currentButtons().firstOrNull { button -> buttonRect(screen, button.index).contains(mouseX, mouseY) }?.action ?: return
        when (action) {
            InventoryMenuAction.Profile -> PlayerProfileClient.openSelf()
            InventoryMenuAction.Friends -> NpcFriendsClient.open()
            InventoryMenuAction.Skills -> ClassSkillTreeClient.open()
            InventoryMenuAction.Leaderboard -> Unit
            InventoryMenuAction.Battlepass -> showingPasses = true
            InventoryMenuAction.CozyPass -> BattlepassClient.openBattlepass("cozy")
            InventoryMenuAction.CombatPass -> BattlepassClient.openBattlepass("combat")
        }
        playClickSound()
        event.isCanceled = true
    }

    private fun renderMenu(guiGraphics: GuiGraphics, screen: Screen, mouseX: Int, mouseY: Int) {
        val menu = menuRect(screen)
        withEntrance(guiGraphics, EntranceStyle(0, offsetX = 18, scaleFrom = 0.96f)) {
            renderNineSlice(guiGraphics, PANEL_TEXTURE, menu, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER, 1.0f)
            drawCkdm(guiGraphics, "Menu", menu.x + PAD, menu.y + 13, WHITE, CKDM_BOLD)
        }

        currentButtons().forEach { button ->
            withEntrance(guiGraphics, EntranceStyle(70 + button.index * 38, offsetX = 14, scaleFrom = 0.98f)) {
                renderMenuButton(guiGraphics, buttonRect(screen, button.index), button, mouseX, mouseY)
            }
        }
    }

    private fun renderMenuButton(guiGraphics: GuiGraphics, rect: Rect, button: InventoryMenuButton, mouseX: Int, mouseY: Int) {
        val hovered = rect.contains(mouseX, mouseY)
        val textureSize = if (hovered) BUTTON_HOVER_TEXTURE_SIZE else BUTTON_TEXTURE_SIZE
        renderNineSlice(guiGraphics, if (hovered) GREEN_BUTTON_HOVER_TEXTURE else GRAY_BUTTON_TEXTURE, rect, textureSize, textureSize, if (hovered) BUTTON_HOVER_SOURCE_CORNER else BUTTON_SOURCE_CORNER, BUTTON_DEST_CORNER, 1.0f)
        renderIcon(guiGraphics, button.icon, rect.x + 7, rect.y + 3, ICON_SIZE)
        drawCkdm(guiGraphics, fitText(button.label, rect.width - 32, CKDM_SMALL), rect.x + 27, rect.y + 7, WHITE, CKDM_SMALL)
        renderBadge(guiGraphics, rect, claimableCount(button.action))
    }

    private fun renderBadge(guiGraphics: GuiGraphics, buttonRect: Rect, count: Int) {
        if (count <= 0) return
        val label = count.coerceAtMost(99).toString()
        val badgeWidth = (Minecraft.getInstance().font.width(ckdmText(label, CKDM_SMALL)) + 12).coerceAtLeast(17)
        val badge = Rect(buttonRect.right - badgeWidth + 4, buttonRect.y - 7, badgeWidth, 16)
        renderNineSlice(guiGraphics, RED_BUTTON_TEXTURE, badge, BUTTON_TEXTURE_SIZE, BUTTON_TEXTURE_SIZE, BUTTON_SOURCE_CORNER, BUTTON_DEST_CORNER, 1.0f)
        drawCenteredCkdm(guiGraphics, label, badge.x, badge.y + 5, badge.width, WHITE, CKDM_SMALL)
    }

    private fun currentButtons(): List<InventoryMenuButton> = if (showingPasses) {
        listOf(
            InventoryMenuButton(0, "Cozy Pass", HOME_ICON, InventoryMenuAction.CozyPass),
            InventoryMenuButton(1, "Combat Pass", DUNGEON_ICON, InventoryMenuAction.CombatPass),
        )
    } else {
        listOf(
            InventoryMenuButton(0, "Profile", PROFILE_ICON, InventoryMenuAction.Profile),
            InventoryMenuButton(1, "Friends", FRIENDS_ICON, InventoryMenuAction.Friends),
            InventoryMenuButton(2, "Battlepass", GIFT_ICON, InventoryMenuAction.Battlepass),
            InventoryMenuButton(3, "Skills", SKILLS_ICON, InventoryMenuAction.Skills),
            InventoryMenuButton(4, "Leaderboard", TROPHY_ICON, InventoryMenuAction.Leaderboard),
        )
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int, sourceCorner: Int, destinationCorner: Int, alpha: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha * renderAlpha)
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

    private fun renderIcon(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun withEntrance(guiGraphics: GuiGraphics, style: EntranceStyle, render: () -> Unit) {
        val eased = entranceProgress(style)
        val previousAlpha = renderAlpha
        renderAlpha *= eased
        val pose = guiGraphics.pose()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        pose.pushPose()
        pose.translate(style.offsetX * (1.0f - eased), style.offsetY * (1.0f - eased), 0.0f)
        if (style.scaleFrom != 1.0f) {
            val scale = style.scaleFrom + (1.0f - style.scaleFrom) * eased
            pose.scale(scale, scale, 1.0f)
        }
        render()
        pose.popPose()
        renderAlpha = previousAlpha
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
    }

    private fun entranceProgress(style: EntranceStyle): Float {
        val elapsed = (Util.getMillis() - openedAtMs - style.delayMs).toFloat()
        val linear = (elapsed / ENTRANCE_DURATION_MS).coerceIn(0.0f, 1.0f)
        val inverse = 1.0f - linear
        return 1.0f - inverse * inverse * inverse
    }

    private fun drawCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, fontId: ResourceLocation) {
        if (renderAlpha <= MIN_TEXT_RENDER_ALPHA) return
        guiGraphics.drawString(Minecraft.getInstance().font, ckdmText(text, fontId), x, y, colorWithRenderAlpha(color), false)
    }

    private fun ckdmText(text: String, fontId: ResourceLocation): Component =
        Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    private fun fitText(text: String, maxWidth: Int, fontId: ResourceLocation): String {
        val font = Minecraft.getInstance().font
        if (font.width(ckdmText(text, fontId)) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && font.width(ckdmText("$value...", fontId)) > maxWidth) value = value.dropLast(1)
        return "$value..."
    }

    private fun drawCenteredCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int, fontId: ResourceLocation) {
        if (renderAlpha <= MIN_TEXT_RENDER_ALPHA) return
        val font = Minecraft.getInstance().font
        val component = ckdmText(text, fontId)
        guiGraphics.drawString(font, component, x + (width - font.width(component)) / 2, y, colorWithRenderAlpha(color), false)
    }

    private fun claimableCount(action: InventoryMenuAction): Int = when (action) {
        InventoryMenuAction.Battlepass -> PASS_IDS.sumOf(::claimableCountForPass)
        InventoryMenuAction.CozyPass -> claimableCountForPass(COZY_PASS_ID)
        InventoryMenuAction.CombatPass -> claimableCountForPass(COMBAT_PASS_ID)
        InventoryMenuAction.Profile, InventoryMenuAction.Friends, InventoryMenuAction.Skills, InventoryMenuAction.Leaderboard -> 0
    }

    private fun claimableCountForPass(passId: String): Int {
        val playerId = BattlepassClientState.selfId() ?: Minecraft.getInstance().player?.uuid ?: return 0
        val pass = passes().firstOrNull { pass -> pass.id == passId } ?: return 0
        val xp = BattlepassClientState.xpFor(playerId, passId) ?: BattlepassXpStore.getXp(playerId, passId)
        return pass.progression.count { tier -> xp >= tier.xp && !(BattlepassClientState.isClaimed(playerId, passId, tier.xp) ?: BattlepassXpStore.isClaimed(playerId, passId, tier.xp)) }
    }

    private fun passes(): List<BattlepassPassDefinition> = BattlepassClientState.passes().ifEmpty { BattlepassPassRegistry.all().toList() }

    private fun colorWithRenderAlpha(color: Int): Int = ((((color ushr 24) and 0xFF) * renderAlpha).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    private fun menuRect(screen: Screen): Rect {
        val inventoryRight = (screen.width + INVENTORY_WIDTH) / 2
        val height = menuHeight()
        val x = (inventoryRight + 8).coerceAtMost(screen.width - MENU_WIDTH - 8).coerceAtLeast(8)
        val y = ((screen.height - INVENTORY_HEIGHT) / 2 + 8).coerceAtLeast(8)
        return Rect(x, y, MENU_WIDTH, height)
    }

    private fun buttonRect(screen: Screen, index: Int): Rect = menuRect(screen).let { rect ->
        Rect(rect.x + PAD, rect.y + 34 + index * BUTTON_STEP, rect.width - PAD * 2, BUTTON_HEIGHT)
    }

    private fun menuHeight(): Int = 46 + currentButtons().size * BUTTON_STEP

    private fun playClickSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.55f))
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
    }

    private data class InventoryMenuButton(val index: Int, val label: String, val icon: ResourceLocation, val action: InventoryMenuAction)

    private enum class InventoryMenuAction { Profile, Friends, Battlepass, Skills, Leaderboard, CozyPass, CombatPass }

    private data class EntranceStyle(val delayMs: Int, val offsetX: Int = 0, val offsetY: Int = 0, val scaleFrom: Float = 1.0f)

    private const val INVENTORY_WIDTH = 176
    private const val INVENTORY_HEIGHT = 166
    private const val MENU_WIDTH = 126
    private const val PAD = 12
    private const val BUTTON_HEIGHT = 20
    private const val BUTTON_STEP = 24
    private const val ICON_SIZE = 14
    private const val ICON_SOURCE_SIZE = 16
    private const val PANEL_TEXTURE_WIDTH = 1646
    private const val PANEL_TEXTURE_HEIGHT = 256
    private const val PANEL_SOURCE_CORNER = 75
    private const val PANEL_DEST_CORNER = 13
    private const val BUTTON_TEXTURE_SIZE = 8
    private const val BUTTON_HOVER_TEXTURE_SIZE = 10
    private const val BUTTON_SOURCE_CORNER = 2
    private const val BUTTON_HOVER_SOURCE_CORNER = 3
    private const val BUTTON_DEST_CORNER = 4
    private const val ENTRANCE_DURATION_MS = 220.0f
    private const val MIN_TEXT_RENDER_ALPHA = 0.004f
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val COZY_PASS_ID = "cozy"
    private const val COMBAT_PASS_ID = "combat"
    private val PASS_IDS = listOf(COZY_PASS_ID, COMBAT_PASS_ID)
    private val PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
    private val GRAY_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_gray.png")
    private val GREEN_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green_hover.png")
    private val RED_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_red.png")
    private val CKDM_BOLD = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    private val CKDM_SMALL = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
    private val PROFILE_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/profile.png")
    private val FRIENDS_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/friends.png")
    private val GIFT_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/gift.png")
    private val SKILLS_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/wisdom.png")
    private val TROPHY_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/trophy.png")
    private val HOME_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/home.png")
    private val DUNGEON_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/dungeon.png")
}
