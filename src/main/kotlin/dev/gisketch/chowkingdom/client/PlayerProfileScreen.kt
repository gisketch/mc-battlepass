package dev.gisketch.chowkingdom.client

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.roles.RolePerkUiPayload
import dev.gisketch.chowkingdom.roles.RoleProfile
import dev.gisketch.chowkingdom.roles.RoleUiDefinitionPayload
import dev.gisketch.chowkingdom.roles.RolesClientState
import dev.gisketch.chowkingdom.wallets.ChowcoinClientState
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale
import java.util.UUID
import kotlin.math.max

object PlayerProfileClient {
    fun openSelf() {
        Minecraft.getInstance().setScreen(PlayerProfileScreen())
    }
}

private class PlayerProfileScreen : Screen(Component.literal("Profile")) {
    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
    }

    private data class TooltipZone(val rect: Rect, val lines: List<Component>)
    private data class ProfileStat(val label: String, val value: String, val icon: ResourceLocation?, val stack: ItemStack, val detail: String)
    private data class ProfilePerk(val label: String, val detail: String)

    private var openedAtMs = 0L
    private var renderAlpha = 1.0f
    private var tooltipZones: List<TooltipZone> = emptyList()

    override fun init() {
        openedAtMs = Util.getMillis()
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        renderAlpha = entranceProgress()
        val zones = mutableListOf<TooltipZone>()
        val panel = panelRect()
        withEntrance(guiGraphics, panel) {
            renderNineSlice(guiGraphics, PANEL_TEXTURE, panel, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_SOURCE_CORNER, PANEL_DEST_CORNER, 1.0f)
            renderContent(guiGraphics, panel, mouseX, mouseY, zones)
        }
        tooltipZones = zones
        zones.firstOrNull { zone -> zone.rect.contains(mouseX, mouseY) }?.let { zone -> guiGraphics.renderComponentTooltip(font, zone.lines, mouseX, mouseY) }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0x99000000.toInt())
    }

    private fun renderContent(guiGraphics: GuiGraphics, panel: Rect, mouseX: Int, mouseY: Int, zones: MutableList<TooltipZone>) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        val playerId = BattlepassClientState.selfId() ?: player?.uuid
        val progress = playerId?.let(BattlepassClientState::playerProgress)
        val name = player?.displayName?.string ?: progress?.name ?: minecraft.user.name
        val roles = playerId?.let(RolesClientState::profileFor) ?: RoleProfile()
        val cozyXp = xpFor(playerId, progress, COZY_PASS_ID)
        val combatXp = xpFor(playerId, progress, COMBAT_PASS_ID)
        val level = ((cozyXp + combatXp) / XP_PER_LEVEL).coerceAtLeast(0)
        val chowcoins = progress?.chowcoins ?: ChowcoinClientState.balance()

        drawCenteredCkdm(guiGraphics, "Profile", panel.x, panel.y + 18, panel.width, WHITE, CKDM_BOLD)
        val contentX = panel.x + PAD
        var cursorY = panel.y + 46

        val identity = Rect(contentX, cursorY, panel.width - PAD * 2, 34)
        renderPlayerHead(guiGraphics, playerId, identity.x, identity.y, HEAD_SIZE, name)
        guiGraphics.drawString(font, fitPlain(name, identity.width - HEAD_SIZE - 12), identity.x + HEAD_SIZE + 10, identity.y + 7, colorWithRenderAlpha(WHITE), false)
        zones += TooltipZone(identity, listOf(Component.literal("Player"), Component.literal(name).withStyle(ChatFormatting.GRAY)))
        cursorY += 38

        val roleRow = Rect(contentX, cursorY, panel.width - PAD * 2, ROLE_ICON_SIZE + 8)
        renderRoleIcons(guiGraphics, roleRow, roles, zones)
        cursorY += ROLE_ICON_SIZE + 15

        val summary = Rect(contentX, cursorY, panel.width - PAD * 2, 24)
        renderSummaryMetric(guiGraphics, summary.x, summary.y, BP_ICON, "Lv.$level", "Battlepass Level", "Cozy XP + Combat XP divided by 100: $cozyXp + $combatXp")
        zones += TooltipZone(Rect(summary.x, summary.y, summary.width / 2, summary.height), listOf(Component.literal("Battlepass Level"), Component.literal("Cozy XP + Combat XP divided by 100.").withStyle(ChatFormatting.GRAY)))
        renderSummaryMetric(guiGraphics, summary.x + summary.width / 2, summary.y, CHOWCOIN_ICON, formatCompact(chowcoins), "Chowcoins", "Current wallet balance")
        zones += TooltipZone(Rect(summary.x + summary.width / 2, summary.y, summary.width / 2, summary.height), listOf(Component.literal("Chowcoins"), Component.literal("Current wallet balance: ${formatNumber(chowcoins)}").withStyle(ChatFormatting.GRAY)))
        cursorY += 35

        drawCkdm(guiGraphics, "Perks", contentX, cursorY, GOLD, CKDM_SMALL)
        cursorY += 15
        val perks = profilePerks(roles).ifEmpty { listOf(ProfilePerk("No active perks", "Choose a job and class to unlock profile perks.")) }
        val perkRows = perks.take(maxPerkRows(panel, cursorY))
        perkRows.forEach { perk ->
            val row = Rect(contentX, cursorY, panel.width - PAD * 2, PERK_ROW_HEIGHT)
            renderPerkRow(guiGraphics, row, perk)
            zones += TooltipZone(row, listOf(Component.literal(perk.label), Component.literal(perk.detail).withStyle(ChatFormatting.GRAY)))
            cursorY += PERK_ROW_HEIGHT + 3
        }

        cursorY += 5
        drawCkdm(guiGraphics, "Stats", contentX, cursorY, GOLD, CKDM_SMALL)
        cursorY += 15
        renderStatsGrid(guiGraphics, Rect(contentX, cursorY, panel.width - PAD * 2, panel.bottom - cursorY - PAD), profileStats(progress), zones)
    }

    private fun renderRoleIcons(guiGraphics: GuiGraphics, row: Rect, roles: RoleProfile, zones: MutableList<TooltipZone>) {
        var cursor = row.x
        val allRoles = roles.jobs.map { role -> role to "Job" } + roles.classes.map { role -> role to "Class" }
        if (allRoles.isEmpty()) {
            guiGraphics.drawString(font, "No job or class selected", row.x, row.y + 5, colorWithRenderAlpha(MUTED), false)
            zones += TooltipZone(row, listOf(Component.literal("Roles"), Component.literal("No active jobs or classes.").withStyle(ChatFormatting.GRAY)))
            return
        }
        allRoles.forEach { (role, kind) ->
            val iconRect = Rect(cursor, row.y, ROLE_ICON_SIZE, ROLE_ICON_SIZE)
            renderRoleIcon(guiGraphics, role.icon, iconRect.x, iconRect.y, ROLE_ICON_SIZE)
            zones += TooltipZone(iconRect, listOf(Component.literal("$kind: ${role.displayName}"), Component.literal(role.description).withStyle(ChatFormatting.GRAY)))
            cursor += ROLE_ICON_SIZE + ROLE_ICON_GAP
        }
    }

    private fun renderSummaryMetric(guiGraphics: GuiGraphics, x: Int, y: Int, icon: ResourceLocation, value: String, title: String, detail: String) {
        renderTexture(guiGraphics, icon, x, y + 2, SUMMARY_ICON_SIZE, ICON_TEXTURE_SIZE)
        guiGraphics.drawString(font, value, x + SUMMARY_ICON_SIZE + 6, y + 5, colorWithRenderAlpha(WHITE), false)
    }

    private fun renderPerkRow(guiGraphics: GuiGraphics, row: Rect, perk: ProfilePerk) {
        guiGraphics.fill(row.x, row.y, row.right, row.bottom, colorWithRenderAlpha(ROW_FILL))
        renderTexture(guiGraphics, PERK_ICON, row.x + 5, row.y + 3, 12, ICON_TEXTURE_SIZE)
        guiGraphics.drawString(font, fitPlain(perk.label, row.width - 25), row.x + 22, row.y + 4, colorWithRenderAlpha(WHITE), false)
    }

    private fun renderStatsGrid(guiGraphics: GuiGraphics, rect: Rect, stats: List<ProfileStat>, zones: MutableList<TooltipZone>) {
        val gap = 6
        val cellWidth = (rect.width - gap) / 2
        stats.forEachIndexed { index, stat ->
            val column = index % 2
            val row = index / 2
            val cell = Rect(rect.x + column * (cellWidth + gap), rect.y + row * (STAT_CELL_HEIGHT + gap), cellWidth, STAT_CELL_HEIGHT)
            guiGraphics.fill(cell.x, cell.y, cell.right, cell.bottom, colorWithRenderAlpha(ROW_FILL))
            if (stat.icon != null) renderTexture(guiGraphics, stat.icon, cell.x + 5, cell.y + 6, STAT_ICON_SIZE, ICON_TEXTURE_SIZE) else renderItem(guiGraphics, stat.stack, cell.x + 5, cell.y + 6, STAT_ICON_SIZE)
            guiGraphics.drawString(font, fitPlain(stat.value, cell.width - 30), cell.x + 27, cell.y + 5, colorWithRenderAlpha(WHITE), false)
            guiGraphics.drawString(font, fitPlain(stat.label, cell.width - 30), cell.x + 27, cell.y + 17, colorWithRenderAlpha(MUTED), false)
            zones += TooltipZone(cell, listOf(Component.literal(stat.label), Component.literal(stat.detail).withStyle(ChatFormatting.GRAY)))
        }
    }

    private fun profilePerks(roles: RoleProfile): List<ProfilePerk> = (roles.jobs + roles.classes).flatMap { role ->
        role.perks.map { perk -> perkText(role, perk) }
    }

    private fun perkText(role: RoleUiDefinitionPayload, perk: RolePerkUiPayload): ProfilePerk = when (perk.type) {
        "cobblemon_catch_rate" -> ProfilePerk("${multiplierText(perk.multiplier)}x ${typeLabel(perk.pokemonType)} catch rate", "${role.displayName} improves catch rate for ${typeLabel(perk.pokemonType)} Pokemon when the Cobblemon hook is enabled.")
        "mount_speed" -> ProfilePerk("${multiplierText(perk.multiplier)}x ${typeLabel(perk.pokemonType)} mount speed", "${role.displayName} has a data hook for ${typeLabel(perk.pokemonType)} Pokemon mount speed.")
        "quality_food_harvest_bonus" -> ProfilePerk("${multiplierText(perk.multiplier)}x Quality Food harvest", "${role.displayName} rerolls Quality Food crop drops based on this multiplier.")
        "prevent_crop_trample" -> ProfilePerk("Prevents crop trampling", "${role.displayName} cancels farmland trampling while active.")
        "starting_items" -> ProfilePerk("Starting items: ${perk.startingItems.size}", perk.startingItems.joinToString(", ").ifBlank { "No starting items configured." })
        "equipment_affinity" -> ProfilePerk("Equipment affinity", "Allowed weapon and armor tags/patterns reduce class penalties for ${role.displayName}.")
        else -> ProfilePerk(prettyId(perk.type), "${role.displayName} perk: ${perk.type}")
    }

    private fun profileStats(progress: BattlepassClientState.PlayerProgress?): List<ProfileStat> = listOf(
        ProfileStat("Pokemon Caught", (progress?.uniquePokemonCaught ?: 0).toString(), null, pokeBallStack(), "Unique Cobblemon species caught."),
        ProfileStat("Mob Kills", (progress?.hostileMonstersKilled ?: 0).toString(), KILLS_ICON, ItemStack.EMPTY, "Hostile monster kills recorded by battlepass progress."),
        ProfileStat("KOs", (progress?.koCount ?: 0).toString(), KOS_ICON, ItemStack.EMPTY, "Times you were knocked down by the revive system."),
        ProfileStat("Revives", (progress?.revivedCount ?: 0).toString(), REVIVE_ICON, ItemStack.EMPTY, "Times other players revived you."),
        ProfileStat("Revived By Me", (progress?.revivedOthersCount ?: 0).toString(), REVIVER_ICON, ItemStack.EMPTY, "Players you helped revive."),
        ProfileStat("Playtime", formatPlaytime(progress?.playtimeTicks ?: 0L), PLAYTIME_ICON, ItemStack.EMPTY, "Total playtime tracked by vanilla stats."),
    )

    private fun renderPlayerHead(guiGraphics: GuiGraphics, playerId: UUID?, x: Int, y: Int, size: Int, name: String) {
        val skin = playerId?.let { id -> Minecraft.getInstance().connection?.getPlayerInfo(id)?.skin }
        if (skin != null) {
            PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size)
        } else {
            guiGraphics.fill(x, y, x + size, y + size, colorWithRenderAlpha(AVATAR_FILL))
            guiGraphics.drawString(font, name.take(1).uppercase(Locale.ROOT), x + size / 2 - 3, y + size / 2 - 4, colorWithRenderAlpha(WHITE), false)
        }
    }

    private fun renderRoleIcon(guiGraphics: GuiGraphics, rawIcon: String, x: Int, y: Int, size: Int) {
        val stack = roleIconStack(rawIcon)
        if (!stack.isEmpty) {
            renderItem(guiGraphics, stack, x, y, size)
            return
        }
        val texture = roleIconTexture(rawIcon) ?: return
        renderTexture(guiGraphics, texture, x, y, size, ICON_TEXTURE_SIZE)
    }

    private fun renderItem(guiGraphics: GuiGraphics, stack: ItemStack, x: Int, y: Int, size: Int) {
        if (stack.isEmpty) return
        val scale = size / VANILLA_ITEM_SIZE.toFloat()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + size / 2.0f, y + size / 2.0f, 0.0f)
        pose.scale(scale, scale, 1.0f)
        pose.translate(-VANILLA_ITEM_SIZE / 2.0f, -VANILLA_ITEM_SIZE / 2.0f, 0.0f)
        guiGraphics.renderItem(stack, 0, 0)
        pose.popPose()
    }

    private fun renderTexture(guiGraphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, size: Int, sourceSize: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, sourceSize, sourceSize, sourceSize, sourceSize)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
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

    private fun withEntrance(guiGraphics: GuiGraphics, panel: Rect, render: () -> Unit) {
        val eased = renderAlpha
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0.0f, PROFILE_SLIDE_Y * (1.0f - eased), 0.0f)
        val scale = 0.97f + 0.03f * eased
        pose.translate((panel.x + panel.width / 2).toFloat(), (panel.y + panel.height / 2).toFloat(), 0.0f)
        pose.scale(scale, scale, 1.0f)
        pose.translate(-(panel.x + panel.width / 2).toFloat(), -(panel.y + panel.height / 2).toFloat(), 0.0f)
        render()
        pose.popPose()
    }

    private fun drawCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, fontId: ResourceLocation) {
        guiGraphics.drawString(font, ckdmText(text, fontId), x, y, colorWithRenderAlpha(color), false)
    }

    private fun drawCenteredCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int, fontId: ResourceLocation) {
        val component = ckdmText(text, fontId)
        guiGraphics.drawString(font, component, x + (width - font.width(component)) / 2, y, colorWithRenderAlpha(color), false)
    }

    private fun ckdmText(text: String, fontId: ResourceLocation): Component = Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    private fun panelRect(): Rect {
        val panelWidth = (width * 0.56f).toInt().coerceIn(330, 520).coerceAtMost(width - 24)
        val panelHeight = (height * 0.82f).toInt().coerceIn(300, 410).coerceAtMost(height - 24)
        return Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight)
    }

    private fun maxPerkRows(panel: Rect, cursorY: Int): Int {
        val statsHeight = 15 + STAT_CELL_HEIGHT * 3 + STAT_GRID_GAP * 2 + 26
        val available = (panel.bottom - PAD - cursorY - statsHeight).coerceAtLeast(PERK_ROW_HEIGHT)
        return (available / (PERK_ROW_HEIGHT + 3)).coerceIn(1, 6)
    }

    private fun xpFor(playerId: UUID?, progress: BattlepassClientState.PlayerProgress?, passId: String): Int =
        progress?.xpByPass?.get(passId) ?: playerId?.let { id -> BattlepassClientState.xpFor(id, passId) ?: BattlepassXpStore.getXp(id, passId) } ?: 0

    private fun roleIconTexture(rawIcon: String): ResourceLocation? {
        val icon = rawIcon.trim()
        if (icon.isBlank()) return null
        return runCatching {
            when {
                icon.startsWith("textures/") -> ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, icon)
                icon.contains(":textures/") -> ResourceLocation.parse(icon)
                icon.endsWith(".png") && icon.contains(":") -> ResourceLocation.parse(icon)
                icon.endsWith(".png") -> ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/$icon")
                else -> null
            }
        }.getOrNull()
    }

    private fun roleIconStack(rawIcon: String): ItemStack {
        val id = runCatching { ResourceLocation.parse(rawIcon.trim()) }.getOrNull() ?: return ItemStack.EMPTY
        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR)
        return if (item == Items.AIR) ItemStack.EMPTY else ItemStack(item)
    }

    private fun pokeBallStack(): ItemStack = ItemStack(BuiltInRegistries.ITEM.getOptional(POKE_BALL_ITEM_ID).orElse(Items.BARRIER))

    private fun fitPlain(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && font.width("$value...") > maxWidth) value = value.dropLast(1)
        return "$value..."
    }

    private fun typeLabel(raw: String): String = raw.replace('_', ' ').replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }

    private fun prettyId(raw: String): String = raw.replace('_', ' ').replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }

    private fun multiplierText(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    private fun formatNumber(value: Long): String = String.format(Locale.US, "%,d", value)

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

    private fun entranceProgress(): Float {
        val elapsed = (Util.getMillis() - openedAtMs).toFloat()
        val linear = (elapsed / ANIMATION_DURATION_MS).coerceIn(0.0f, 1.0f)
        val inverse = 1.0f - linear
        return 1.0f - inverse * inverse * inverse
    }

    private fun colorWithRenderAlpha(color: Int): Int = ((((color ushr 24) and 0xFF) * renderAlpha).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    companion object {
        private const val PAD = 20
        private const val HEAD_SIZE = 28
        private const val ROLE_ICON_SIZE = 20
        private const val ROLE_ICON_GAP = 6
        private const val SUMMARY_ICON_SIZE = 14
        private const val STAT_ICON_SIZE = 14
        private const val PERK_ROW_HEIGHT = 20
        private const val STAT_CELL_HEIGHT = 34
        private const val STAT_GRID_GAP = 6
        private const val VANILLA_ITEM_SIZE = 16
        private const val ICON_TEXTURE_SIZE = 16
        private const val PANEL_TEXTURE_WIDTH = 1646
        private const val PANEL_TEXTURE_HEIGHT = 256
        private const val PANEL_SOURCE_CORNER = 75
        private const val PANEL_DEST_CORNER = 14
        private const val XP_PER_LEVEL = 100
        private const val COZY_PASS_ID = "cozy"
        private const val COMBAT_PASS_ID = "combat"
        private const val ANIMATION_DURATION_MS = 220.0f
        private const val PROFILE_SLIDE_Y = 14.0f
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFD8D0B8.toInt()
        private const val GOLD = 0xFFFFD66B.toInt()
        private const val ROW_FILL = 0x26000000
        private const val AVATAR_FILL = 0xFF4C4037.toInt()
        private val PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
        private val CKDM_BOLD = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
        private val CKDM_SMALL = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
        private val BP_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/bp_book.png")
        private val CHOWCOIN_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/chowcoin.png")
        private val PERK_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/star.png")
        private val KILLS_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/kilic.png")
        private val KOS_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/skull.png")
        private val REVIVE_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/heart_blue.png")
        private val REVIVER_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/heart.png")
        private val PLAYTIME_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/clock.png")
        private val POKE_BALL_ITEM_ID = ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")
    }
}