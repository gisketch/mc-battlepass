package dev.gisketch.chowkingdom.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.discord.DiscordQuickSkinSupport
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.glfw.GLFW
import java.util.Locale
import java.util.UUID
import java.io.ByteArrayInputStream
import kotlin.random.Random

object PlayerListHudClient {
    private val LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "player_list")
    private var lastSyncRequestMs = 0L
    private var animationStartedAtMs = 0L
    private var animationFrom = 0.0f
    private var animationTo = 0.0f
    private var animationValue = 0.0f
    private var wasActive = false
    private var debugRowsEnabled = false
    private var fakeRows: List<PlayerRow> = emptyList()
    private val quickSkinAvatarTextures = mutableMapOf<UUID, ResourceLocation?>()

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerGuiLayers)
        NeoForge.EVENT_BUS.addListener(::hideVanillaPlayerList)
        NeoForge.EVENT_BUS.addListener(::registerClientCommands)
    }

    private fun registerClientCommands(event: RegisterClientCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("ck")
                .then(
                    Commands.literal("tab")
                        .then(Commands.literal("debug").requires { source -> source.hasPermission(2) }.executes(::toggleDebugRows)),
                ),
        )
    }

    private fun toggleDebugRows(context: CommandContext<CommandSourceStack>): Int {
        debugRowsEnabled = !debugRowsEnabled
        fakeRows = if (debugRowsEnabled) makeFakeRows() else emptyList()
        context.source.sendSuccess({ Component.literal("CK tab debug ${if (debugRowsEnabled) "enabled" else "disabled"}.") }, false)
        return 1
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAboveAll(LAYER_ID) { guiGraphics, _ -> render(guiGraphics) }
    }

    private fun hideVanillaPlayerList(event: RenderGuiLayerEvent.Pre) {
        if (event.name == VanillaGuiLayers.TAB_LIST && isTabHeld()) event.isCanceled = true
    }

    private fun render(guiGraphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        val active = isTabHeld() && !minecraft.options.hideGui && !minecraft.gui.debugOverlay.showDebugScreen() && minecraft.screen == null
        updateAnimation(active)
        if (animationValue <= 0.01f) return
        if (active) requestSyncIfNeeded()
        val entries = playerRows(minecraft)
        if (entries.isEmpty()) return

        val rowCount = entries.size.coerceAtMost(MAX_ROWS)
        val visibleRows = entries.take(rowCount)
        val width = tableWidth(visibleRows, minecraft.font, minecraft.window.guiScaledWidth - TABLE_SCREEN_PAD * 2)
        val x = (minecraft.window.guiScaledWidth - width) / 2
        val y = TABLE_TOP
        val height = TABLE_PAD * 2 + HEADER_HEIGHT + rowCount * ROW_HEIGHT
        val panel = Rect(x, y, width, height)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0.0f, -ANIMATION_SLIDE_Y * (1.0f - animationValue), 0.0f)
        renderNineSlice(guiGraphics, PANEL_TEXTURE, panel, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER, 0.96f)
        val columns = columns(panel, visibleRows, minecraft.font)
        renderHeader(guiGraphics, minecraft.font, panel, columns)
        visibleRows.forEachIndexed { index, row -> renderRow(guiGraphics, minecraft.font, panel, columns, index, row) }
        pose.popPose()
    }

    private fun renderHeader(guiGraphics: GuiGraphics, font: Font, panel: Rect, columns: List<Rect>) {
        val y = panel.y + TABLE_PAD + 3
        drawHeader(guiGraphics, font, "Lv.", columns[0], y)
        drawHeader(guiGraphics, font, "Name", columns[2], y)
        drawIconHeader(guiGraphics, KILLS_ICON, columns[3], y)
        drawIconHeader(guiGraphics, KOS_ICON, columns[4], y)
        drawIconHeader(guiGraphics, DEATHS_ICON, columns[5], y)
        drawIconHeader(guiGraphics, CHOWCOIN_TEXTURE, columns[6], y)
        drawItemHeader(guiGraphics, pokeBallStack(), columns[7], y)
        drawIconHeader(guiGraphics, PLAYTIME_ICON, columns[8], y)
        drawIconHeader(guiGraphics, NETWORK_ICON, columns[9], y)
    }

    private fun renderRow(guiGraphics: GuiGraphics, font: Font, panel: Rect, columns: List<Rect>, index: Int, row: PlayerRow) {
        val y = panel.y + TABLE_PAD + HEADER_HEIGHT + index * ROW_HEIGHT
        if (index % 2 == 1) guiGraphics.fill(panel.x + TABLE_PAD, y, panel.right - TABLE_PAD, y + ROW_HEIGHT, ROW_STRIPE)
        val textY = y + 4
        val textColor = if (row.online) CELL_TEXT else OFFLINE_TEXT
        drawCell(guiGraphics, font, row.level.toString(), columns[0], textY, textColor)
        renderAvatar(guiGraphics, row, columns[1].x, y + 2, AVATAR_SIZE)
        guiGraphics.drawString(font, font.plainSubstrByWidth(row.name, columns[2].width), columns[2].x, textY, colorAlpha(textColor), false)
        drawCell(guiGraphics, font, row.kills.toString(), columns[3], textY, textColor)
        drawCell(guiGraphics, font, row.kos.toString(), columns[4], textY, textColor)
        drawCell(guiGraphics, font, row.deaths.toString(), columns[5], textY, textColor)
        drawCell(guiGraphics, font, formatCompact(row.chowcoins), columns[6], textY, textColor)
        drawCell(guiGraphics, font, row.uniquePokemonCaught.toString(), columns[7], textY, textColor)
        drawCell(guiGraphics, font, formatPlaytime(row.playtimeTicks), columns[8], textY, textColor)
        drawCell(guiGraphics, font, pingText(row.pingMs), columns[9], textY, pingColor(row.pingMs))
    }

    private fun playerRows(minecraft: Minecraft): List<PlayerRow> {
        val connection = minecraft.connection ?: return emptyList()
        val onlinePlayers = connection.listedOnlinePlayers.associateBy { info -> info.profile.id }
        val rowsById = linkedMapOf<UUID, PlayerRow>()
        BattlepassClientState.players().forEach { progress ->
            val info = onlinePlayers[progress.uuid]
            rowsById[progress.uuid] = playerRow(progress, info?.profile?.name ?: progress.name, info?.latency, info != null)
        }
        onlinePlayers.values.forEach { info ->
            val id = info.profile.id
            val progress = BattlepassClientState.playerProgress(id)
            rowsById[id] = if (progress != null) playerRow(progress, info.profile.name, info.latency, true) else PlayerRow(id, info.profile.name, 0, 0, 0, 0, 0L, 0, 0L, info.latency, true)
        }
        val rows = rowsById.values.toList() + if (debugRowsEnabled) fakeRows else emptyList()
        val selfId = minecraft.player?.uuid
        return rows.sortedWith(
            compareBy<PlayerRow> { row -> if (row.id == selfId) 0 else 1 }
                .thenBy { row -> if (row.online) 0 else 1 }
                .thenBy { row -> row.pingMs ?: Int.MAX_VALUE }
                .thenBy { row -> row.name.lowercase(Locale.ROOT) },
        )
    }

    private fun playerRow(progress: BattlepassClientState.PlayerProgress, name: String, pingMs: Int?, online: Boolean): PlayerRow = PlayerRow(
        progress.uuid,
        name,
        ((progress.xpByPass[COZY_PASS_ID] ?: 0) + (progress.xpByPass[COMBAT_PASS_ID] ?: 0)) / XP_PER_LEVEL,
        progress.hostileMonstersKilled,
        progress.koCount,
        progress.deaths,
        progress.chowcoins,
        progress.uniquePokemonCaught,
        progress.playtimeTicks,
        pingMs,
        online,
    )

    private fun requestSyncIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastSyncRequestMs < SYNC_INTERVAL_MS) return
        lastSyncRequestMs = now
        BattlepassNetwork.requestSync()
    }

    private fun updateAnimation(active: Boolean) {
        val now = System.currentTimeMillis()
        if (active != wasActive) {
            wasActive = active
            animationStartedAtMs = now
            animationFrom = animationValue
            animationTo = if (active) 1.0f else 0.0f
        }
        val progress = ((now - animationStartedAtMs) / ANIMATION_DURATION_MS.toFloat()).coerceIn(0.0f, 1.0f)
        val eased = if (animationTo > animationFrom) easeOutBack(progress) else easeInOut(progress)
        animationValue = (animationFrom + (animationTo - animationFrom) * eased).coerceIn(0.0f, 1.08f)
    }

    private fun tableWidth(rows: List<PlayerRow>, font: Font, maxWidth: Int): Int =
        (columnWidths(rows, font, Int.MAX_VALUE / 4).sum() + COLUMN_GAP * (COLUMN_COUNT - 1) + TABLE_PAD * 2).coerceIn(TABLE_MIN_WIDTH, maxWidth)

    private fun columns(panel: Rect, rows: List<PlayerRow>, font: Font): List<Rect> {
        val widths = columnWidths(rows, font, panel.width - TABLE_PAD * 2)
        var x = panel.x + TABLE_PAD
        val y = panel.y + TABLE_PAD
        return widths.map { width ->
            val rect = Rect(x, y, width, 0)
            x += width + COLUMN_GAP
            rect
        }
    }

    private fun columnWidths(rows: List<PlayerRow>, font: Font, availableWidth: Int): MutableList<Int> {
        val widths = mutableListOf(
            contentWidth(rows, font, font.width(ckdmText("Lv."))) { row -> row.level.toString() },
            AVATAR_SIZE,
            contentWidth(rows, font, font.width(ckdmText("Name"))) { row -> row.name }.coerceIn(NAME_MIN_WIDTH, NAME_MAX_WIDTH),
            contentWidth(rows, font, HEADER_ICON_SIZE) { row -> row.kills.toString() },
            contentWidth(rows, font, HEADER_ICON_SIZE) { row -> row.kos.toString() },
            contentWidth(rows, font, HEADER_ICON_SIZE) { row -> row.deaths.toString() },
            contentWidth(rows, font, HEADER_ICON_SIZE) { row -> formatCompact(row.chowcoins) },
            contentWidth(rows, font, HEADER_ICON_SIZE) { row -> row.uniquePokemonCaught.toString() },
            contentWidth(rows, font, HEADER_ICON_SIZE) { row -> formatPlaytime(row.playtimeTicks) },
            contentWidth(rows, font, HEADER_ICON_SIZE) { row -> pingText(row.pingMs) },
        )
        val overflow = widths.sum() + COLUMN_GAP * (widths.size - 1) - availableWidth
        if (overflow > 0) widths[2] = (widths[2] - overflow).coerceAtLeast(NAME_MIN_WIDTH)
        return widths
    }

    private fun contentWidth(rows: List<PlayerRow>, font: Font, minWidth: Int, value: (PlayerRow) -> String): Int =
        maxOf(minWidth, rows.maxOfOrNull { row -> font.width(value(row)) } ?: minWidth)
            .coerceAtLeast(HEADER_ICON_SIZE)

    private fun renderAvatar(guiGraphics: GuiGraphics, row: PlayerRow, x: Int, y: Int, size: Int) {
        val quickSkinTexture = quickSkinAvatarTexture(row.id)
        val skin = Minecraft.getInstance().connection?.getPlayerInfo(row.id)?.skin
        if (quickSkinTexture != null) {
            renderTexture(guiGraphics, quickSkinTexture, Rect(x, y, size, size), QUICKSKIN_HEAD_TEXTURE_SIZE, QUICKSKIN_HEAD_TEXTURE_SIZE)
        } else if (skin != null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, animationValue.coerceIn(0.0f, 1.0f))
            PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size)
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        } else {
            guiGraphics.fill(x, y, x + size, y + size, colorAlpha(AVATAR_FALLBACK_FILL))
            guiGraphics.drawString(Minecraft.getInstance().font, row.name.take(1).uppercase(Locale.ROOT), x + 3, y + 2, colorAlpha(CELL_TEXT), false)
        }
    }

    private fun quickSkinAvatarTexture(playerId: UUID): ResourceLocation? {
        if (quickSkinAvatarTextures.containsKey(playerId)) return quickSkinAvatarTextures[playerId]
        val texture = runCatching {
            val bytes = DiscordQuickSkinSupport.quickSkinHeadPng(playerId) ?: return@runCatching null
            val image = NativeImage.read(ByteArrayInputStream(bytes))
            val id = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "quickskin/tab_avatar/${playerId.toString().replace("-", "_")}")
            Minecraft.getInstance().textureManager.register(id, DynamicTexture(image))
            id
        }.getOrNull()
        quickSkinAvatarTextures[playerId] = texture
        return texture
    }

    private fun drawHeader(guiGraphics: GuiGraphics, font: Font, text: String, rect: Rect, y: Int) {
        val component = ckdmText(text)
        guiGraphics.drawString(font, component, rect.x + HEADER_SHADOW_OFFSET, y + HEADER_SHADOW_OFFSET, colorAlpha(HEADER_SHADOW), false)
        guiGraphics.drawString(font, component, rect.x, y, colorAlpha(HEADER_TEXT), false)
    }

    private fun drawIconHeader(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, y: Int) {
        renderIcon(guiGraphics, texture, rect.x, y - 1, HEADER_ICON_SIZE, ICON_TEXTURE_SIZE)
    }

    private fun drawItemHeader(guiGraphics: GuiGraphics, stack: ItemStack, rect: Rect, y: Int) {
        renderScaledItem(guiGraphics, stack, rect.x, y - 1, HEADER_ICON_SIZE)
    }

    private fun drawCell(guiGraphics: GuiGraphics, font: Font, text: String, rect: Rect, y: Int, color: Int) {
        guiGraphics.drawString(font, text, rect.x, y, colorAlpha(color), false)
    }

    private fun renderIcon(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int, textureSize: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, animationValue.coerceIn(0.0f, 1.0f))
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, textureSize, textureSize, textureSize, textureSize)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderTexture(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, animationValue.coerceIn(0.0f, 1.0f))
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, 0.0f, 0.0f, textureWidth, textureHeight, textureWidth, textureHeight)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderScaledItem(guiGraphics: GuiGraphics, stack: ItemStack, x: Int, y: Int, size: Int) {
        val pose = guiGraphics.pose()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, animationValue.coerceIn(0.0f, 1.0f))
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0.0f)
        pose.scale(size / VANILLA_ITEM_SIZE.toFloat(), size / VANILLA_ITEM_SIZE.toFloat(), 1.0f)
        guiGraphics.renderItem(stack, 0, 0)
        pose.popPose()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderNineSlice(guiGraphics: GuiGraphics, texture: ResourceLocation, rect: Rect, textureWidth: Int, textureHeight: Int, sourceCorner: Int, destinationCorner: Int, alpha: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha * animationValue.coerceIn(0.0f, 1.0f))
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

    private fun isTabHeld(): Boolean = GLFW.glfwGetKey(Minecraft.getInstance().window.window, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS

    private fun ckdmText(text: String): Component = Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(CKDM_BOLD_FONT) }

    private fun pingText(pingMs: Int?): String = pingMs?.takeIf { value -> value >= 0 }?.let { value -> "${value}ms" } ?: "N/A"

    private fun pingColor(pingMs: Int?): Int = when {
        pingMs == null || pingMs < 0 -> OFFLINE_TEXT
        pingMs <= 100 -> CELL_TEXT
        pingMs <= 150 -> PING_YELLOW
        else -> PING_RED
    }

    private fun formatPlaytime(ticks: Long): String {
        val minutes = (ticks / 20L / 60L).coerceAtLeast(0L)
        val days = minutes / (24L * 60L)
        val hours = (minutes / 60L) % 24L
        val mins = minutes % 60L
        return buildString {
            if (days > 0) append(days).append('d')
            if (hours > 0 || days > 0) append(hours).append('h')
            append(mins).append('m')
        }
    }

    private fun formatCompact(value: Long): String {
        val amount = value.coerceAtLeast(0L)
        return when {
            amount < 1_000L -> amount.toString()
            amount < 10_000L -> String.format(Locale.US, "%.1fK", amount / 1_000.0)
            amount < 1_000_000L -> "${amount / 1_000}K"
            amount < 10_000_000L -> String.format(Locale.US, "%.1fM", amount / 1_000_000.0)
            else -> "${amount / 1_000_000}M"
        }
    }

    private fun pokeBallStack(): ItemStack = ItemStack(BuiltInRegistries.ITEM.getOptional(POKE_BALL_ITEM_ID).orElse(Items.BARRIER))

    private fun colorAlpha(color: Int): Int = (((color ushr 24) and 0xFF) * animationValue.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255) shl 24 or (color and 0x00FFFFFF)

    private fun easeOutBack(progress: Float): Float {
        val shifted = progress - 1.0f
        return 1.0f + ANIMATION_BACK_OVERSHOOT * shifted * shifted * shifted + ANIMATION_BACK_OVERSHOOT * shifted * shifted
    }

    private fun easeInOut(progress: Float): Float = progress * progress * (3.0f - 2.0f * progress)

    private fun makeFakeRows(): List<PlayerRow> = List(10) { index ->
        PlayerRow(
            UUID.nameUUIDFromBytes("ck-tab-debug-$index".toByteArray()),
            "DebugPlayer${index + 1}",
            DEBUG_RANDOM.nextInt(0, 160),
            DEBUG_RANDOM.nextInt(0, 900),
            DEBUG_RANDOM.nextInt(0, 80),
            DEBUG_RANDOM.nextInt(0, 60),
            DEBUG_RANDOM.nextLong(0, 2_500_000),
            DEBUG_RANDOM.nextInt(0, 600),
            DEBUG_RANDOM.nextLong(0, 20L * 60L * 60L * 24L * 8L),
            if (index % 3 == 2) null else listOf(10, 42, 87, 100, 128, 150, 210, 340).random(DEBUG_RANDOM),
            index % 3 != 2,
        )
    }

    private data class PlayerRow(
        val id: UUID,
        val name: String,
        val level: Int,
        val kills: Int,
        val kos: Int,
        val deaths: Int,
        val chowcoins: Long,
        val uniquePokemonCaught: Int,
        val playtimeTicks: Long,
        val pingMs: Int?,
        val online: Boolean,
    )

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
    }

    private const val TABLE_WIDTH = 620
    private const val TABLE_MIN_WIDTH = 360
    private const val COLUMN_COUNT = 10
    private const val MAX_ROWS = 12
    private const val TABLE_TOP = 14
    private const val TABLE_PAD = 12
    private const val TABLE_SCREEN_PAD = 8
    private const val HEADER_HEIGHT = 19
    private const val ROW_HEIGHT = 16
    private const val COLUMN_GAP = 12
    private const val NAME_MIN_WIDTH = 80
    private const val NAME_MAX_WIDTH = 150
    private const val CELL_PAD = 6
    private const val HEADER_ICON_SIZE = 12
    private const val HEADER_ICON_GAP = 4
    private const val AVATAR_SIZE = 12
    private const val ICON_TEXTURE_SIZE = 16
    private const val QUICKSKIN_HEAD_TEXTURE_SIZE = 128
    private const val VANILLA_ITEM_SIZE = 16
    private const val SYNC_INTERVAL_MS = 1_000L
    private const val ANIMATION_DURATION_MS = 150L
    private const val ANIMATION_SLIDE_Y = 20.0f
    private const val ANIMATION_BACK_OVERSHOOT = 1.25f
    private const val PANEL_TEXTURE_WIDTH = 1643
    private const val PANEL_TEXTURE_HEIGHT = 253
    private const val PANEL_SOURCE_CORNER = 75
    private const val PANEL_DEST_CORNER = 14
    private const val HEADER_TEXT = 0xFFFFFFFF.toInt()
    private const val HEADER_SHADOW = 0x99000000.toInt()
    private const val HEADER_SHADOW_OFFSET = 1
    private const val CELL_TEXT = 0xFFFFFFFF.toInt()
    private const val OFFLINE_TEXT = 0xFF9A948A.toInt()
    private const val MUTED_TEXT = 0xFF9A948A.toInt()
    private const val PING_YELLOW = 0xFFFFD65A.toInt()
    private const val PING_RED = 0xFFFF6161.toInt()
    private const val ROW_STRIPE = 0x26000000
    private const val AVATAR_FALLBACK_FILL = 0xFF4C4037.toInt()
    private const val XP_PER_LEVEL = 100
    private const val COZY_PASS_ID = "cozy"
    private const val COMBAT_PASS_ID = "combat"
    private val PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_grey.png")
    private val KILLS_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/kilic.png")
    private val KOS_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/skull.png")
    private val DEATHS_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/dead.png")
    private val CHOWCOIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/chowcoin.png")
    private val NETWORK_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/dunya.png")
    private val PLAYTIME_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/clock.png")
    private val POKE_BALL_ITEM_ID = ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")
    private val CKDM_BOLD_FONT = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
    private val DEBUG_RANDOM = Random(0xC04A11)
}