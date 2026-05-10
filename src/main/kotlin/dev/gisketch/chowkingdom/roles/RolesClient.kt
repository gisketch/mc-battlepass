package dev.gisketch.chowkingdom.roles

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import net.minecraft.Util
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemDisplayContext
import net.neoforged.neoforge.client.ClientHooks
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent
import net.neoforged.neoforge.client.event.RenderNameTagEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.TriState
import org.joml.Matrix4f
import java.util.Optional
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.round

object RolesClient {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onRenderNameTag)
        NeoForge.EVENT_BUS.addListener(::onGatherEffectTooltips)
        NeoForge.EVENT_BUS.addListener(::onScreenRenderPost)
        RoleEquipmentOverlayClient.register()
    }

    @JvmStatic
    fun sync(payload: RolesSyncPayload) {
        RolesClientState.apply(payload)
        val minecraft = Minecraft.getInstance()
        val current = minecraft.screen as? RolesOnboardingScreen
        current?.updatePayload(payload)
        if (payload.openOnboarding && current == null) {
            minecraft.setScreen(RolesOnboardingScreen(payload))
        }
    }

    private fun onRenderNameTag(event: RenderNameTagEvent) {
        val player = event.entity as? Player ?: return
        val icons = RolesClientState.iconsFor(player.uuid)
        if (icons.jobIcons.isEmpty() && icons.classIcons.isEmpty()) return
        event.setCanRender(TriState.FALSE)
        renderRoleNameTag(event, icons)
    }

    private fun onGatherEffectTooltips(event: GatherEffectScreenTooltipsEvent) {
        val status = jobStatusFor(event.effectInstance) ?: return
        event.tooltip.clear()
        event.tooltip.addAll(status.tooltip)
    }

    private fun onScreenRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen as? InventoryScreen ?: return
        val status = hoveredJobStatus(event.mouseX, event.mouseY, screen.width, screen.height) ?: return
        event.guiGraphics.renderTooltip(Minecraft.getInstance().font, status.tooltip, Optional.empty(), event.mouseX, event.mouseY)
    }

    private fun renderRoleNameTag(event: RenderNameTagEvent, icons: RoleNametagIcons) {
        val entity = event.entity
        val minecraft = Minecraft.getInstance()
        val distance = minecraft.entityRenderDispatcher.distanceToSqr(entity)
        if (!ClientHooks.isNameplateInRenderDistance(entity, distance)) return
        val attachment = entity.attachments.getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(event.partialTick)) ?: return
        val visibleThroughWalls = !entity.isDiscrete
        val y = if (event.content.string == "deadmau5") -10.0f else 0.0f
        val poseStack = event.poseStack
        poseStack.pushPose()
        poseStack.translate(attachment.x, attachment.y + 0.5, attachment.z)
        poseStack.mulPose(minecraft.entityRenderDispatcher.cameraOrientation())
        poseStack.scale(0.025f, -0.025f, 0.025f)
        val matrix = poseStack.last().pose()
        val backgroundAlpha = (minecraft.options.getBackgroundOpacity(0.25f) * 255.0f).toInt() shl 24
        val font = event.entityRenderer.font
        val textWidth = font.width(event.content).toFloat()
        val jobWidth = iconGroupWidth(icons.jobIcons.size)
        val classWidth = iconGroupWidth(icons.classIcons.size)
        val jobTextGap = if (icons.jobIcons.isEmpty()) 0.0f else NAMETAG_ICON_TEXT_GAP
        val classTextGap = if (icons.classIcons.isEmpty()) 0.0f else NAMETAG_ICON_TEXT_GAP
        val totalWidth = jobWidth + jobTextGap + textWidth + classTextGap + classWidth
        val startX = -totalWidth / 2.0f
        if (visibleThroughWalls) {
            renderRoleNameTagPass(event.content, icons, startX, y, true, matrix, poseStack, event.multiBufferSource, backgroundAlpha, event.packedLight)
        }
        renderRoleNameTagPass(event.content, icons, startX, y, false, matrix, poseStack, event.multiBufferSource, 0, event.packedLight)
        poseStack.popPose()
    }

    private fun renderRoleNameTagPass(
        content: Component,
        icons: RoleNametagIcons,
        startX: Float,
        y: Float,
        seeThrough: Boolean,
        matrix: Matrix4f,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        backgroundColor: Int,
        packedLight: Int,
    ) {
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        var cursor = startX
        icons.jobIcons.forEach { icon ->
            renderNametagIcon(icon, cursor, y + NAMETAG_ICON_Y_OFFSET, seeThrough, matrix, poseStack, bufferSource, packedLight)
            cursor += NAMETAG_ICON_SIZE + NAMETAG_ICON_GAP
        }
        if (icons.jobIcons.isNotEmpty()) cursor += NAMETAG_ICON_TEXT_GAP - NAMETAG_ICON_GAP
        val displayMode = if (seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
        val color = if (seeThrough) NAMETAG_SEE_THROUGH_TEXT else NAMETAG_TEXT
        font.drawInBatch(content, cursor, y, color, false, matrix, bufferSource, displayMode, backgroundColor, packedLight)
        cursor += font.width(content).toFloat() + if (icons.classIcons.isEmpty()) 0.0f else NAMETAG_ICON_TEXT_GAP
        icons.classIcons.forEach { icon ->
            renderNametagIcon(icon, cursor, y + NAMETAG_ICON_Y_OFFSET, seeThrough, matrix, poseStack, bufferSource, packedLight)
            cursor += NAMETAG_ICON_SIZE + NAMETAG_ICON_GAP
        }
    }

    private fun renderNametagIcon(rawIcon: String, x: Float, y: Float, seeThrough: Boolean, matrix: Matrix4f, poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int) {
        val stack = roleIconStack(rawIcon)
        if (!stack.isEmpty) {
            if (!seeThrough) renderNametagItem(stack, x, y, poseStack, bufferSource, packedLight)
            return
        }
        val texture = roleIconTexture(rawIcon) ?: return
        val renderType = if (seeThrough) RenderType.textSeeThrough(texture) else RenderType.text(texture)
        val consumer = bufferSource.getBuffer(renderType)
        val alpha = if (seeThrough) NAMETAG_SEE_THROUGH_ICON_ALPHA else 255
        val right = x + NAMETAG_ICON_SIZE
        val bottom = y + NAMETAG_ICON_SIZE
        consumer.addVertex(matrix, x, bottom, 0.0f).setColor(255, 255, 255, alpha).setUv(0.0f, 1.0f).setLight(packedLight)
        consumer.addVertex(matrix, right, bottom, 0.0f).setColor(255, 255, 255, alpha).setUv(1.0f, 1.0f).setLight(packedLight)
        consumer.addVertex(matrix, right, y, 0.0f).setColor(255, 255, 255, alpha).setUv(1.0f, 0.0f).setLight(packedLight)
        consumer.addVertex(matrix, x, y, 0.0f).setColor(255, 255, 255, alpha).setUv(0.0f, 0.0f).setLight(packedLight)
    }

    private fun renderNametagItem(stack: ItemStack, x: Float, y: Float, poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int) {
        val minecraft = Minecraft.getInstance()
        poseStack.pushPose()
        poseStack.translate((x + NAMETAG_ICON_SIZE / 2.0f).toDouble(), (y + NAMETAG_ICON_SIZE / 2.0f).toDouble(), 0.0)
        val scale = NAMETAG_ICON_SIZE / 16.0f
        poseStack.scale(scale, scale, scale)
        poseStack.translate(-8.0, -8.0, 0.0)
        minecraft.itemRenderer.renderStatic(stack, ItemDisplayContext.GUI, packedLight, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, minecraft.level, 0)
        poseStack.popPose()
    }

    private fun iconGroupWidth(count: Int): Float = if (count <= 0) 0.0f else count * NAMETAG_ICON_SIZE + (count - 1) * NAMETAG_ICON_GAP
}

private fun jobStatusFor(instance: net.minecraft.world.effect.MobEffectInstance): JobStatusDisplay? {
    val slot = RolesFeature.jobStatusEffectIndex(instance) ?: return null
    return RolesClientState.jobStatusFor(slot, instance.amplifier + 1)
}

private fun hoveredJobStatus(mouseX: Int, mouseY: Int, width: Int, height: Int): JobStatusDisplay? {
    val minecraft = Minecraft.getInstance()
    val effects = minecraft.player?.activeEffects
        ?.filter { effect -> IClientMobEffectExtensions.of(effect).isVisibleInInventory(effect) }
        ?.sorted()
        .orEmpty()
    if (effects.isEmpty()) return null
    val left = (width - INVENTORY_WIDTH) / 2
    val top = (height - INVENTORY_HEIGHT) / 2
    val x = left + INVENTORY_WIDTH + 2
    val availableSpace = width - x
    if (availableSpace < 32) return null
    val rowWidth = if (availableSpace >= 120) 120 else 32
    val rowStep = if (effects.size > 5) 132 / (effects.size - 1) else 33
    var y = top
    effects.forEach { effect ->
        if (mouseX in x..(x + rowWidth) && mouseY in y..(y + rowStep)) {
            return jobStatusFor(effect)
        }
        y += rowStep
    }
    return null
}

object RolesClientState {
    private var jobsById: Map<String, RoleUiDefinitionPayload> = emptyMap()
    private var classesById: Map<String, RoleUiDefinitionPayload> = emptyMap()
    private var jobRankUnlockOverallLevels: List<Int> = JobLevels.fallbackJobRankUnlockOverallLevels
    private var catchRateBonusPercentByRank: List<Double> = JobLevels.fallbackCatchRateBonusPercentByRank
    private var mountSpeedBonusPercentByRank: List<Double> = JobLevels.fallbackMountSpeedBonusPercentByRank
    private val playerIcons: MutableMap<UUID, RoleNametagIcons> = linkedMapOf()
    private val playerRoleIds: MutableMap<UUID, RoleProfileIds> = linkedMapOf()

    fun apply(payload: RolesSyncPayload) {
        jobsById = payload.jobs.associateBy { role -> role.id }
        classesById = payload.classes.associateBy { role -> role.id }
        jobRankUnlockOverallLevels = payload.jobRankUnlockOverallLevels.ifEmpty { JobLevels.fallbackJobRankUnlockOverallLevels }
        catchRateBonusPercentByRank = payload.catchRateBonusPercentByRank.ifEmpty { JobLevels.fallbackCatchRateBonusPercentByRank }
        mountSpeedBonusPercentByRank = payload.mountSpeedBonusPercentByRank.ifEmpty { JobLevels.fallbackMountSpeedBonusPercentByRank }
        playerIcons.clear()
        playerRoleIds.clear()
        payload.players.forEach { player ->
            playerRoleIds[player.playerId] = RoleProfileIds(player.jobIds, player.classIds)
            playerIcons[player.playerId] = RoleNametagIcons(
                jobIcons = player.jobIds.mapNotNull { id -> jobsById[id]?.icon },
                classIcons = player.classIds.mapNotNull { id -> classesById[id]?.icon },
            )
        }
    }

    fun iconsFor(playerId: UUID): RoleNametagIcons = playerIcons[playerId] ?: RoleNametagIcons()

    fun activeClassIdsFor(playerId: UUID): Set<String> = playerRoleIds[playerId]?.classIds?.toSet().orEmpty()

    fun profileFor(playerId: UUID): RoleProfile = playerRoleIds[playerId]?.let { ids ->
        RoleProfile(
            jobs = ids.jobIds.mapNotNull(jobsById::get),
            classes = ids.classIds.mapNotNull(classesById::get),
        )
    } ?: RoleProfile()

    fun jobStatusFor(slot: Int, rank: Int): JobStatusDisplay? {
        val selfId = BattlepassClientState.selfId() ?: Minecraft.getInstance().player?.uuid ?: return null
        val job = profileFor(selfId).jobs.getOrNull(slot) ?: return null
        val overallLevel = overallLevelFor(selfId)
        val title = "Lv. $rank ${job.displayName}"
        val perkLines = job.perks.mapNotNull { perk -> perkTooltipLine(perk, rank) }
        val tooltip = mutableListOf<Component>(
            Component.literal(title).withStyle(ChatFormatting.GOLD),
            Component.literal("Overall Lv. $overallLevel - Rank $rank").withStyle(ChatFormatting.GRAY),
        )
        if (perkLines.isEmpty()) {
            tooltip += Component.literal("No rank perks active.").withStyle(ChatFormatting.DARK_GRAY)
        } else {
            tooltip += Component.literal("Perks:").withStyle(ChatFormatting.YELLOW)
            tooltip += perkLines.map { line -> Component.literal(line).withStyle(ChatFormatting.GRAY) }
        }
        return JobStatusDisplay(
            title = title,
            icon = job.icon,
            summary = perkLines.firstOrNull() ?: "No rank perks active",
            tooltip = tooltip,
        )
    }

    fun jobRankFor(playerId: UUID?): Int = playerId?.let { id -> jobRankUnlockOverallLevels.count { unlockLevel -> overallLevelFor(id) >= unlockLevel } } ?: 0

    fun maxJobRank(): Int = jobRankUnlockOverallLevels.size.coerceAtLeast(1)

    fun catchRateBonusPercent(perk: RolePerkUiPayload, rank: Int): Double {
        if (rank <= 0) return 0.0
        return perk.bonusPercentByLevel.getOrNull(rank - 1) ?: catchRateBonusPercentByRank.getOrElse(rank - 1) { catchRateBonusPercentByRank.lastOrNull() ?: 0.0 }
    }

    fun mountSpeedBonusPercent(perk: RolePerkUiPayload, rank: Int): Double {
        if (rank <= 0) return 0.0
        return perk.bonusPercentByLevel.getOrNull(rank - 1) ?: mountSpeedBonusPercentByRank.getOrElse(rank - 1) { mountSpeedBonusPercentByRank.lastOrNull() ?: 0.0 }
    }

    fun configuredBonusPercent(perk: RolePerkUiPayload, rank: Int): Double {
        if (rank <= 0) return 0.0
        return perk.bonusPercentByLevel.getOrNull(rank - 1) ?: perk.bonusPercentByLevel.lastOrNull() ?: 0.0
    }

    fun firstBonusPercent(perk: RolePerkUiPayload): Double = perk.bonusPercentByLevel.firstOrNull() ?: 0.0

    private fun overallLevelFor(playerId: UUID): Int = BattlepassClientState.playerProgress(playerId)?.xpByPass?.values?.sum()?.div(BATTLEPASS_XP_PER_LEVEL) ?: 0

    private fun perkTooltipLine(perk: RolePerkUiPayload, rank: Int): String? = when (perk.type) {
        "cobblemon_catch_rate" -> {
            val bonus = catchRateBonusPercent(perk, rank)
            val target = perk.pokemonType.ifBlank { "matching" }.replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
            "${formatBonusPercent(bonus)} catch rate for $target Pokemon"
        }
        "mount_speed" -> {
            val bonus = mountSpeedBonusPercent(perk, rank)
            val target = perk.pokemonType.ifBlank { "matching" }.replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
            "${formatBonusPercent(bonus)} mount speed for $target Pokemon"
        }
        "crop_bonus_drop_chance" -> "${formatBonusPercent(configuredBonusPercent(perk, rank))} bonus crop drop chance"
        "quality_harvest_upgrade_chance" -> "${formatBonusPercent(configuredBonusPercent(perk, rank))} Quality Harvest upgrade chance"
        "swim_speed" -> "${formatBonusPercent(configuredBonusPercent(perk, rank))} swim movement speed"
        "underwater_mining_penalty_reduction" -> "${formatBonusPercent(configuredBonusPercent(perk, rank))} underwater mining penalty reduction"
        "fishing_bonus_drop_chance" -> "${formatBonusPercent(configuredBonusPercent(perk, rank))} chance for one extra fishing drop"
        "rain_catch_rate_bonus" -> "${formatBonusPercent(configuredBonusPercent(perk, rank))} extra rainy ${perk.pokemonType.ifBlank { "matching" }} catch rate"
        "gentle_steps" -> "Cannot trample farmland"
        "seasonal_farmer" -> "${formatBonusPercent(firstBonusPercent(perk))} favored-season crop growth"
        "quality_food_harvest_bonus" -> "${formatMultiplier(perk.multiplier)}x quality food harvest"
        "prevent_crop_trample" -> "Prevents crop trampling"
        else -> perk.type.replace('_', ' ').replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
    }

    private fun formatBonusPercent(value: Double): String = String.format(Locale.ROOT, "+%.0f%%", value * 100.0)

    private fun formatMultiplier(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}

private data class RoleProfileIds(val jobIds: List<String>, val classIds: List<String>)

data class RoleProfile(
    val jobs: List<RoleUiDefinitionPayload> = emptyList(),
    val classes: List<RoleUiDefinitionPayload> = emptyList(),
)

data class RoleNametagIcons(
    val jobIcons: List<String> = emptyList(),
    val classIcons: List<String> = emptyList(),
)

data class JobStatusDisplay(
    val title: String,
    val icon: String,
    val summary: String,
    val tooltip: List<Component>,
)

internal fun roleIconTexture(rawIcon: String): ResourceLocation? {
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

internal fun roleIconStack(rawIcon: String): ItemStack {
    return RoleItemStacks.fromId(rawIcon, "role icon") ?: ItemStack.EMPTY
}

private const val NAMETAG_ICON_SIZE = 9.0f
private const val NAMETAG_ICON_GAP = 1.0f
private const val NAMETAG_ICON_TEXT_GAP = 3.0f
private const val NAMETAG_ICON_Y_OFFSET = -1.0f
private const val NAMETAG_TEXT = -1
private const val NAMETAG_SEE_THROUGH_TEXT = 553648127
private const val NAMETAG_SEE_THROUGH_ICON_ALPHA = 96
private const val BATTLEPASS_XP_PER_LEVEL = 100
private const val INVENTORY_WIDTH = 176
private const val INVENTORY_HEIGHT = 166

private class RolesOnboardingScreen(private var payload: RolesSyncPayload) : Screen(Component.literal("Roles Onboarding")) {
    private enum class Step { WELCOME, JOB, CLASS, BODY }
    private enum class BodySlider { HEIGHT, WEIGHT }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
        fun contains(pointX: Int, pointY: Int): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
        fun contains(pointX: Double, pointY: Double): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
        fun inset(amount: Int): Rect = Rect(x + amount, y + amount, (width - amount * 2).coerceAtLeast(0), (height - amount * 2).coerceAtLeast(0))
    }

    private data class Layout(val left: Rect, val center: Rect, val right: Rect, val grid: Rect)
    private data class RoleSlot(val rect: Rect, val role: RoleUiDefinitionPayload?)
    private data class EntranceStyle(
        val delayMs: Int,
        val offsetX: Int = 0,
        val offsetY: Int = 0,
        val scaleFrom: Float = 1.0f,
        val durationMs: Int = 260,
    )

    private var step = Step.WELCOME
    private var selectedJobId: String? = payload.activeJobIds.firstOrNull()
    private var selectedClassId: String? = payload.activeClassIds.firstOrNull()
    private var selectedHeight = payload.height
    private var selectedWeight = payload.weight
    private var draggingSlider: BodySlider? = null
    private var hoveredRoleId: String? = null
    private var jobScroll = 0
    private var classScroll = 0
    private var renderedSlots: List<RoleSlot> = emptyList()
    private var openedAtMs = Util.getMillis()
    private var stepStartedAtMs = openedAtMs
    private var renderAlpha = 1.0f
    private var backgroundParallaxX = 0.0f
    private var backgroundParallaxY = 0.0f
    private var skipPanelEntrance = false

    fun updatePayload(next: RolesSyncPayload) {
        payload = next
        if (step != Step.BODY || draggingSlider == null) {
            selectedHeight = next.height
            selectedWeight = next.weight
        }
        if (!next.openOnboarding && Minecraft.getInstance().screen === this) {
            Minecraft.getInstance().setScreen(null)
        }
    }

    override fun init() {
        openedAtMs = Util.getMillis()
        stepStartedAtMs = openedAtMs
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderAlpha = 1.0f
        when (step) {
            Step.WELCOME -> renderWelcome(guiGraphics, mouseX, mouseY)
            Step.BODY -> renderBodyStep(guiGraphics, mouseX, mouseY)
            else -> renderRoleStep(guiGraphics, mouseX, mouseY)
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, BLACK)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        val x = mouseX.toInt()
        val y = mouseY.toInt()
        if (step == Step.WELCOME) {
            if (welcomeContinueRect().contains(x, y)) {
                playClick()
                goTo(Step.JOB)
                return true
            }
            return true
        }

        if (step == Step.BODY) {
            val controls = bodyControlsRect()
            BodySlider.entries.firstOrNull { slider -> bodySliderRect(controls, slider).contains(x, y) }?.let { slider ->
                draggingSlider = slider
                updateBodySlider(slider, x)
                playClick()
                return true
            }
            if (continueRect().contains(x, y)) {
                val jobId = selectedJobId ?: return true
                val classId = selectedClassId ?: return true
                RolesNetwork.choose(jobId, classId, selectedHeight, selectedWeight)
                minecraft?.setScreen(null)
                playClick()
                return true
            }
            return true
        }

        renderedSlots.firstOrNull { slot -> slot.rect.contains(x, y) }?.let { slot ->
            if (isLockedClassSlot(slot)) return true
            setSelectedRole(slot.role?.id)
            playClick()
            return true
        }

        val canContinue = canContinueRoleStep()
        if (continueRect().contains(x, y)) {
            if (!canContinue) return true
            playClick()
            if (step == Step.JOB) {
                goTo(Step.CLASS)
            } else {
                val classId = selectedClassId ?: return true
                goTo(Step.BODY)
            }
            return true
        }

        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val slider = draggingSlider ?: return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        updateBodySlider(slider, mouseX.toInt())
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingSlider = null
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (step == Step.WELCOME || step == Step.BODY) return true
        val grid = selectionLayout().grid
        if (!grid.contains(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val roles = currentRoles()
        val maxScroll = maxGridScroll(roles, grid)
        val delta = if (scrollY > 0.0) -1 else 1
        if (step == Step.JOB) jobScroll = (jobScroll + delta).coerceIn(0, maxScroll) else classScroll = (classScroll + delta).coerceIn(0, maxScroll)
        return true
    }

    private fun renderWelcome(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        renderBackground(guiGraphics, mouseX, mouseY, 0.0f)
        val panel = welcomePanel()
        withEntrance(guiGraphics, EntranceStyle(0, offsetY = 12, scaleFrom = 0.96f), panel.x + panel.width / 2, panel.y + panel.height / 2) {
            renderNineSlice(guiGraphics, GOLD_CONTAINER_TEXTURE, panel, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, CONTAINER_DEST_CORNER, 1.0f)
            drawCenteredCkdm(guiGraphics, fitText("Welcome to Chowkingdom", panel.width - 32, CKDM_LARGE), panel.x, panel.y + 24, panel.width, WHITE, CKDM_LARGE)
            drawWrapped(guiGraphics, payload.welcomeContent, panel.x + 28, panel.y + 62, panel.width - 56, WHITE_MUTED, maxLines = 5)
        }
        withEntrance(guiGraphics, EntranceStyle(90, offsetY = 10, scaleFrom = 0.98f), width / 2, welcomeContinueRect().y + welcomeContinueRect().height / 2) {
            renderButton(guiGraphics, welcomeContinueRect(), "CONTINUE", GREEN_BUTTON_TEXTURE, GREEN_BUTTON_HOVER_TEXTURE, mouseX, mouseY, active = true)
        }
    }

    private fun renderBodyStep(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        renderParallaxBackground(guiGraphics, mouseX, mouseY)
        val layout = selectionLayout()
        withEntrance(guiGraphics, EntranceStyle(0, offsetY = -10, scaleFrom = 0.98f), width / 2, 20) {
            drawCenteredCkdm(guiGraphics, "SHAPE YOUR BODY", 0, TITLE_Y, width, WHITE, CKDM_LARGE)
        }
        val summary = bodySummaryRect()
        val controls = bodyControlsRect()
        withEntrance(guiGraphics, EntranceStyle(50, offsetX = -18, scaleFrom = 0.98f), summary.x + summary.width / 2, summary.y + summary.height / 2) {
            renderBodySummary(guiGraphics, summary)
        }
        withEntrance(guiGraphics, EntranceStyle(90, offsetY = 14, scaleFrom = 0.98f), layout.center.x + layout.center.width / 2, layout.center.y + layout.center.height / 2) {
            renderPaperDoll(guiGraphics, layout.center, mouseX, mouseY)
        }
        withEntrance(guiGraphics, EntranceStyle(110, offsetX = 18, scaleFrom = 0.98f), controls.x + controls.width / 2, controls.y + controls.height / 2) {
            renderBodyControls(guiGraphics, controls, mouseX, mouseY)
        }
        withEntrance(guiGraphics, EntranceStyle(170, offsetY = 10, scaleFrom = 0.98f), width / 2, continueRect().y + continueRect().height / 2) {
            renderButton(guiGraphics, continueRect(), "START", GREEN_BUTTON_TEXTURE, GREEN_BUTTON_HOVER_TEXTURE, mouseX, mouseY, active = true)
        }
    }

    private fun renderRoleStep(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        renderParallaxBackground(guiGraphics, mouseX, mouseY)
        val layout = selectionLayout()
        val roles = currentRoles()
        val maxScroll = maxGridScroll(roles, layout.grid)
        if (step == Step.JOB) jobScroll = jobScroll.coerceIn(0, maxScroll) else classScroll = classScroll.coerceIn(0, maxScroll)
        val slots = roleSlots(roles, layout.grid, currentScroll())
        renderedSlots = slots
        hoveredRoleId = slots.firstOrNull { slot -> slot.rect.contains(mouseX, mouseY) }?.role?.id

        withEntrance(guiGraphics, EntranceStyle(0, offsetY = -10, scaleFrom = 0.98f), width / 2, 20) {
            drawCenteredCkdm(guiGraphics, stepTitle(), 0, TITLE_Y, width, WHITE, CKDM_LARGE)
        }
        if (skipPanelEntrance) {
            renderLeftColumn(guiGraphics, layout.left)
            renderPaperDoll(guiGraphics, layout.center, mouseX, mouseY)
            renderRightColumn(guiGraphics, layout.right, layout.grid, slots, mouseX, mouseY)
        } else {
            withEntrance(guiGraphics, EntranceStyle(50, offsetX = -18, scaleFrom = 0.98f), layout.left.x + layout.left.width / 2, layout.left.y + layout.left.height / 2) {
                renderLeftColumn(guiGraphics, layout.left)
            }
            withEntrance(guiGraphics, EntranceStyle(90, offsetY = 14, scaleFrom = 0.98f), layout.center.x + layout.center.width / 2, layout.center.y + layout.center.height / 2) {
                renderPaperDoll(guiGraphics, layout.center, mouseX, mouseY)
            }
            withEntrance(guiGraphics, EntranceStyle(110, offsetX = 18, scaleFrom = 0.98f), layout.right.x + layout.right.width / 2, layout.right.y + layout.right.height / 2) {
                renderRightColumn(guiGraphics, layout.right, layout.grid, slots, mouseX, mouseY)
            }
        }
        withEntrance(guiGraphics, EntranceStyle(170, offsetY = 10, scaleFrom = 0.98f), width / 2, continueRect().y + continueRect().height / 2) {
            renderButton(guiGraphics, continueRect(), "CONTINUE", GREEN_BUTTON_TEXTURE, GREEN_BUTTON_HOVER_TEXTURE, mouseX, mouseY, active = canContinueRoleStep())
        }
    }

    private fun renderParallaxBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.fill(0, 0, width, height, BLACK)
        val targetX = if (width == 0) 0.0f else ((mouseX - width / 2.0f) / (width / 2.0f)).coerceIn(-1.0f, 1.0f) * BACKGROUND_PARALLAX
        val targetY = if (height == 0) 0.0f else ((mouseY - height / 2.0f) / (height / 2.0f)).coerceIn(-1.0f, 1.0f) * BACKGROUND_PARALLAX
        backgroundParallaxX = Mth.lerp(PARALLAX_LERP, backgroundParallaxX, targetX)
        backgroundParallaxY = Mth.lerp(PARALLAX_LERP, backgroundParallaxY, targetY)
        val pad = BACKGROUND_PADDING
        val drawScale = max((width + pad * 2) / BG_TEXTURE_WIDTH.toFloat(), (height + pad * 2) / BG_TEXTURE_HEIGHT.toFloat())
        val drawWidth = (BG_TEXTURE_WIDTH * drawScale).toInt()
        val drawHeight = (BG_TEXTURE_HEIGHT * drawScale).toInt()
        val x = (width - drawWidth) / 2 - backgroundParallaxX.toInt()
        val y = (height - drawHeight) / 2 - backgroundParallaxY.toInt()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, BACKGROUND_ALPHA)
        guiGraphics.blit(BACKGROUND_TEXTURE, x, y, drawWidth, drawHeight, 0.0f, 0.0f, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderLeftColumn(guiGraphics: GuiGraphics, rect: Rect) {
        renderNineSlice(guiGraphics, GREY_CONTAINER_TEXTURE, rect, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, CONTAINER_DEST_CORNER, 0.96f)
        val preview = previewRole()
        val title = preview?.displayName ?: noSelectionLabel()
        val description = preview?.description ?: noSelectionDescription()
        drawCenteredCkdm(guiGraphics, fitText(title, rect.width - PAD * 2, CKDM_BOLD), rect.x + PAD, rect.y + 22, rect.width - PAD * 2, WHITE, CKDM_BOLD)
        if (preview == null) {
            drawWrapped(guiGraphics, description, rect.x + PAD + 2, rect.y + 56, rect.width - PAD * 2 - 4, WHITE_MUTED, maxLines = ((rect.height - 72) / 12).coerceAtLeast(1))
            return
        }
        renderRolePreviewDetails(guiGraphics, rect, preview)
    }

    private fun renderRolePreviewDetails(guiGraphics: GuiGraphics, rect: Rect, role: RoleUiDefinitionPayload) {
        val contentX = rect.x + PAD + 2
        val contentWidth = rect.width - PAD * 2 - 4
        var cursorY = rect.y + 54
        drawWrapped(guiGraphics, role.description, contentX, cursorY, contentWidth, WHITE_MUTED, maxLines = 3)
        cursorY += 38
        val rank = RolesClientState.jobRankFor(Minecraft.getInstance().player?.uuid)
        val displays = rolePerkDisplays(role, rank)
        cursorY = renderPerkPairs(guiGraphics, displays.filter { display -> display.group == RolePerkDisplayGroup.STAT }, contentX, cursorY, contentWidth, rect.bottom - PAD)
        cursorY = renderPerkSection(guiGraphics, "Passive", displays.filter { display -> display.group == RolePerkDisplayGroup.PASSIVE }, contentX, cursorY + 4, contentWidth, rect.bottom - PAD)
        renderPerkSection(guiGraphics, "Unique Perks", displays.filter { display -> display.group == RolePerkDisplayGroup.UNIQUE || display.group == RolePerkDisplayGroup.OTHER }, contentX, cursorY + 4, contentWidth, rect.bottom - PAD)
    }

    private fun renderPerkPairs(guiGraphics: GuiGraphics, displays: List<RolePerkDisplay>, x: Int, startY: Int, width: Int, bottom: Int): Int {
        var cursorY = startY
        displays.forEach { display ->
            if (cursorY + PERK_PAIR_LINE_HEIGHT > bottom) return cursorY
            drawCkdmPair(guiGraphics, display.title, display.value, x, cursorY, width)
            cursorY += PERK_PAIR_LINE_HEIGHT
        }
        return cursorY
    }

    private fun renderPerkSection(guiGraphics: GuiGraphics, header: String, displays: List<RolePerkDisplay>, x: Int, startY: Int, width: Int, bottom: Int): Int {
        if (displays.isEmpty() || startY + PERK_SECTION_HEADER_HEIGHT > bottom) return startY
        var cursorY = startY
        drawCkdm(guiGraphics, header, x, cursorY, GOLD, CKDM_SMALL)
        cursorY += PERK_SECTION_HEADER_HEIGHT
        displays.forEachIndexed { index, display ->
            if (cursorY + PERK_PAIR_LINE_HEIGHT > bottom) return cursorY
            val title = if (display.group == RolePerkDisplayGroup.UNIQUE) "Perk ${index + 1} - ${display.title}" else display.title
            drawCkdmPair(guiGraphics, title, display.value, x, cursorY, width)
            cursorY += PERK_PAIR_LINE_HEIGHT
            display.rankValues.chunked(3).take(2).forEach { ranks ->
                if (cursorY + PERK_RANK_LINE_HEIGHT > bottom) return cursorY
                guiGraphics.drawString(font, ranks.joinToString("   "), x + 6, cursorY, colorWithRenderAlpha(WHITE), false)
                cursorY += PERK_RANK_LINE_HEIGHT
            }
        }
        return cursorY
    }

    private fun renderRightColumn(guiGraphics: GuiGraphics, rect: Rect, grid: Rect, slots: List<RoleSlot>, mouseX: Int, mouseY: Int) {
        renderNineSlice(guiGraphics, YELLOW_CONTAINER_TEXTURE, rect, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, CONTAINER_DEST_CORNER, 0.96f)
        drawCenteredCkdm(guiGraphics, rightHeader(), rect.x + PAD, rect.y + 18, rect.width - PAD * 2, WHITE, CKDM_BOLD)
        guiGraphics.enableScissor(grid.x, grid.y, grid.right, grid.bottom)
        slots.forEachIndexed { index, slot ->
            withEntrance(guiGraphics, EntranceStyle(170 + index * 24, offsetY = 8, scaleFrom = 0.96f), slot.rect.x + slot.rect.width / 2, slot.rect.y + slot.rect.height / 2) {
                renderRoleSlot(guiGraphics, slot, mouseX, mouseY)
            }
        }
        guiGraphics.disableScissor()
    }

    private fun renderRoleSlot(guiGraphics: GuiGraphics, slot: RoleSlot, mouseX: Int, mouseY: Int) {
        val selected = selectedRoleId() == slot.role?.id || (selectedRoleId() == null && slot.role == null)
        val hovered = slot.rect.contains(mouseX, mouseY)
        val texture = when {
            selected -> GOLD_CONTAINER_TEXTURE
            hovered -> YELLOW_CONTAINER_TEXTURE
            else -> GREY_CONTAINER_TEXTURE
        }
        renderNineSlice(guiGraphics, texture, slot.rect, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, TILE_DEST_CORNER, if (hovered || selected) 1.0f else 0.86f)
        val role = slot.role
        if (role == null) {
            drawCenteredCkdm(guiGraphics, fitText(noSelectionLabel(), slot.rect.width - 10, CKDM_SMALL), slot.rect.x + 5, slot.rect.y + slot.rect.height / 2 - 4, slot.rect.width - 10, if (selected || hovered) WHITE else WHITE_MUTED, CKDM_SMALL)
            return
        }
        val locked = isLockedUpgradeClass(role)
        renderRoleIcon(guiGraphics, role.icon, Rect(slot.rect.x + (slot.rect.width - ROLE_ICON_SIZE) / 2, slot.rect.y + 9, ROLE_ICON_SIZE, ROLE_ICON_SIZE))
        drawCenteredCkdm(guiGraphics, fitText(role.displayName, slot.rect.width - 10, CKDM_SMALL), slot.rect.x + 5, slot.rect.bottom - 18, slot.rect.width - 10, if (locked) DISABLED else WHITE, CKDM_SMALL)
        if (locked) renderLockedClassOverlay(guiGraphics, slot.rect)
    }

    private fun renderLockedClassOverlay(guiGraphics: GuiGraphics, rect: Rect) {
        guiGraphics.fill(rect.x + 3, rect.y + 3, rect.right - 3, rect.bottom - 3, colorWithRenderAlpha(0x99000000.toInt()))
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        guiGraphics.blit(LOCKED_TEXTURE, rect.x + (rect.width - LOCK_ICON_SIZE) / 2, rect.y + (rect.height - LOCK_ICON_SIZE) / 2, LOCK_ICON_SIZE, LOCK_ICON_SIZE, 0.0f, 0.0f, LOCK_TEXTURE_SIZE, LOCK_TEXTURE_SIZE, LOCK_TEXTURE_SIZE, LOCK_TEXTURE_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderPaperDoll(guiGraphics: GuiGraphics, rect: Rect, mouseX: Int, mouseY: Int) {
        val player = Minecraft.getInstance().player ?: return
        val dollHeight = (rect.height - 16).coerceAtLeast(120)
        val bodyHeight = if (step == Step.BODY) selectedHeight.toFloat() else 1.0f
        val bodyWeight = if (step == Step.BODY) selectedWeight.toFloat() else 1.0f
        val scale = (dollHeight / 2.25f / MAX_BODY_SCALE).toInt().coerceIn(42, 116)
        val pose = guiGraphics.pose()
        pose.pushPose()
        val centerX = (rect.x + rect.right) / 2.0f
        val floorY = rect.bottom - 10.0f
        pose.translate(centerX, floorY, 0.0f)
        pose.scale(bodyWeight, bodyHeight, 1.0f)
        pose.translate(-centerX, -floorY, 0.0f)
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            guiGraphics,
            rect.x + 4,
            rect.y + 6,
            rect.right - 4,
            rect.bottom - 8,
            scale,
            0.04f,
            (rect.x + rect.right) / 2.0f,
            (rect.y + rect.bottom) / 2.0f,
            player,
        )
        pose.popPose()
    }

    private fun renderBodySummary(guiGraphics: GuiGraphics, rect: Rect) {
        renderNineSlice(guiGraphics, GREY_CONTAINER_TEXTURE, rect, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, CONTAINER_DEST_CORNER, 0.96f)
        drawCenteredCkdm(guiGraphics, "YOUR PATH", rect.x + PAD, rect.y + 22, rect.width - PAD * 2, WHITE, CKDM_BOLD)
        var cursorY = rect.y + 58
        drawCkdmPair(guiGraphics, "Job", selectedJob()?.displayName ?: "None", rect.x + PAD + 2, cursorY, rect.width - PAD * 2 - 4)
        cursorY += 18
        drawCkdmPair(guiGraphics, "Class", selectedClass()?.displayName ?: "None", rect.x + PAD + 2, cursorY, rect.width - PAD * 2 - 4)
        cursorY += 26
        drawCkdmPair(guiGraphics, "Height", scaleLabel(selectedHeight), rect.x + PAD + 2, cursorY, rect.width - PAD * 2 - 4)
        cursorY += 18
        drawCkdmPair(guiGraphics, "Weight", scaleLabel(selectedWeight), rect.x + PAD + 2, cursorY, rect.width - PAD * 2 - 4)
    }

    private fun renderBodyControls(guiGraphics: GuiGraphics, rect: Rect, mouseX: Int, mouseY: Int) {
        renderNineSlice(guiGraphics, YELLOW_CONTAINER_TEXTURE, rect, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, CONTAINER_DEST_CORNER, 0.96f)
        drawCenteredCkdm(guiGraphics, "HEIGHT AND WEIGHT", rect.x + PAD, rect.y + 20, rect.width - PAD * 2, WHITE, CKDM_BOLD)
        renderBodySlider(guiGraphics, rect, BodySlider.HEIGHT, "Height", selectedHeight, mouseX, mouseY)
        renderBodySlider(guiGraphics, rect, BodySlider.WEIGHT, "Weight", selectedWeight, mouseX, mouseY)
    }

    private fun renderBodySlider(guiGraphics: GuiGraphics, controls: Rect, slider: BodySlider, label: String, value: Double, mouseX: Int, mouseY: Int) {
        val track = bodySliderRect(controls, slider)
        val hovered = track.contains(mouseX, mouseY) || draggingSlider == slider
        drawCkdm(guiGraphics, label, track.x, track.y - 16, WHITE, CKDM_SMALL)
        drawCkdm(guiGraphics, scaleLabel(value), track.right - font.width(ckdmText(scaleLabel(value), CKDM_SMALL)), track.y - 16, GOLD, CKDM_SMALL)
        renderNineSlice(guiGraphics, GREY_CONTAINER_TEXTURE, track, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, TILE_DEST_CORNER, if (hovered) 1.0f else 0.82f)
        val fillWidth = ((track.width - 8) * scaleProgress(value)).toInt().coerceIn(0, track.width - 8)
        guiGraphics.fill(track.x + 4, track.y + 5, track.x + 4 + fillWidth, track.bottom - 5, colorWithRenderAlpha(GOLD))
        val handleX = track.x + 4 + fillWidth - BODY_SLIDER_HANDLE_WIDTH / 2
        val handle = Rect(handleX.coerceIn(track.x + 2, track.right - BODY_SLIDER_HANDLE_WIDTH - 2), track.y + 2, BODY_SLIDER_HANDLE_WIDTH, track.height - 4)
        renderNineSlice(guiGraphics, GOLD_CONTAINER_TEXTURE, handle, CONTAINER_TEXTURE_WIDTH, CONTAINER_TEXTURE_HEIGHT, CONTAINER_SOURCE_CORNER, TILE_DEST_CORNER, if (hovered) 1.0f else 0.92f)
    }

    private fun renderRoleIcon(guiGraphics: GuiGraphics, rawIcon: String, rect: Rect) {
        val stack = itemStack(rawIcon)
        if (!stack.isEmpty) {
            renderScaledItem(guiGraphics, stack, rect)
            return
        }
        val texture = iconTexture(rawIcon) ?: return
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        guiGraphics.blit(texture, rect.x, rect.y, rect.width, rect.height, 0.0f, 0.0f, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderScaledItem(guiGraphics: GuiGraphics, stack: ItemStack, rect: Rect) {
        val scale = rect.width / VANILLA_ITEM_SIZE.toFloat()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(rect.x + rect.width / 2.0f, rect.y + rect.height / 2.0f, 0.0f)
        pose.scale(scale, scale, 1.0f)
        pose.translate(-VANILLA_ITEM_SIZE / 2.0f, -VANILLA_ITEM_SIZE / 2.0f, 0.0f)
        guiGraphics.renderItem(stack, 0, 0)
        pose.popPose()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun renderButton(guiGraphics: GuiGraphics, rect: Rect, label: String, texture: ResourceLocation, hoverTexture: ResourceLocation, mouseX: Int, mouseY: Int, active: Boolean) {
        val hovered = active && rect.contains(mouseX, mouseY)
        val buttonTexture = when {
            !active -> GRAY_BUTTON_TEXTURE
            hovered -> hoverTexture
            else -> texture
        }
        val textureSize = if (hovered) BUTTON_HOVER_TEXTURE_SIZE else BUTTON_TEXTURE_SIZE
        val sourceCorner = if (hovered) BUTTON_HOVER_SOURCE_CORNER else BUTTON_SOURCE_CORNER
        renderNineSlice(guiGraphics, buttonTexture, rect, textureSize, textureSize, sourceCorner, BUTTON_DEST_CORNER, if (active) 1.0f else 0.52f)
        drawCenteredCkdm(guiGraphics, label, rect.x, rect.y + (rect.height - font.lineHeight) / 2 + 1, rect.width, if (active) WHITE else DISABLED, CKDM_BOLD)
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

    private fun withEntrance(guiGraphics: GuiGraphics, style: EntranceStyle, anchorX: Int, anchorY: Int, render: () -> Unit) {
        val eased = entranceProgress(style)
        val previousAlpha = renderAlpha
        renderAlpha *= eased
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(style.offsetX * (1.0f - eased), style.offsetY * (1.0f - eased), 0.0f)
        if (style.scaleFrom != 1.0f) {
            val scale = style.scaleFrom + (1.0f - style.scaleFrom) * eased
            pose.translate(anchorX.toFloat(), anchorY.toFloat(), 0.0f)
            pose.scale(scale, scale, 1.0f)
            pose.translate(-anchorX.toFloat(), -anchorY.toFloat(), 0.0f)
        }
        render()
        pose.popPose()
        renderAlpha = previousAlpha
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, renderAlpha)
    }

    private fun entranceProgress(style: EntranceStyle): Float {
        val elapsed = (Util.getMillis() - stepStartedAtMs - style.delayMs).toFloat()
        val linear = (elapsed / style.durationMs.coerceAtLeast(1)).coerceIn(0.0f, 1.0f)
        val inverse = 1.0f - linear
        return 1.0f - inverse * inverse * inverse
    }

    private fun roleSlots(roles: List<RoleUiDefinitionPayload>, grid: Rect, scrollRows: Int): List<RoleSlot> {
        val items = listOf<RoleUiDefinitionPayload?>(null) + roles
        val visibleRows = visibleGridRows(grid)
        val startIndex = scrollRows * GRID_COLUMNS
        val endIndex = (startIndex + visibleRows * GRID_COLUMNS).coerceAtMost(items.size)
        val cellWidth = (grid.width - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        return (startIndex until endIndex).map { index ->
            val localIndex = index - startIndex
            val col = localIndex % GRID_COLUMNS
            val row = localIndex / GRID_COLUMNS
            val x = grid.x + col * (cellWidth + GRID_GAP)
            val y = grid.y + row * (TILE_HEIGHT + GRID_GAP)
            RoleSlot(Rect(x, y, cellWidth, TILE_HEIGHT), items[index])
        }
    }

    private fun maxGridScroll(roles: List<RoleUiDefinitionPayload>, grid: Rect): Int {
        val rows = (roles.size + 1 + GRID_COLUMNS - 1) / GRID_COLUMNS
        return (rows - visibleGridRows(grid)).coerceAtLeast(0)
    }

    private fun visibleGridRows(grid: Rect): Int = ((grid.height + GRID_GAP) / (TILE_HEIGHT + GRID_GAP)).coerceAtLeast(1)

    private fun selectionLayout(): Layout {
        val margin = 12
        val gap = 10
        val top = 48
        val bottom = 46
        val totalWidth = (width - margin * 2 - gap * 2).coerceAtLeast(300)
        val side = (totalWidth * 0.31f).toInt().coerceAtLeast(116)
        val center = (totalWidth - side * 2).coerceAtLeast(80)
        val columnHeight = (height - top - bottom).coerceAtLeast(160)
        val left = Rect(margin, top, side, columnHeight)
        val centerRect = Rect(left.right + gap, top, center, columnHeight)
        val right = Rect(centerRect.right + gap, top, width - margin - (centerRect.right + gap), columnHeight)
        val grid = Rect(right.x + PAD, right.y + 48, right.width - PAD * 2, right.height - 62)
        return Layout(left, centerRect, right, grid)
    }

    private fun welcomePanel(): Rect {
        val panelWidth = (width * 0.58f).toInt().coerceIn(260, 520).coerceAtMost((width - 32).coerceAtLeast(240))
        val panelHeight = 164.coerceAtMost((height - 92).coerceAtLeast(132))
        return Rect((width - panelWidth) / 2, (height - panelHeight) / 2 - 16, panelWidth, panelHeight)
    }

    private fun welcomeContinueRect(): Rect = welcomePanel().let { panel -> Rect(panel.x + (panel.width - 150) / 2, panel.bottom + 12, 150, 26) }

    private fun continueRect(): Rect = Rect((width - 168) / 2, height - 38, 168, 26)

    private fun bodySummaryRect(): Rect = selectionLayout().left

    private fun bodyControlsRect(): Rect = selectionLayout().right

    private fun bodySliderRect(controls: Rect, slider: BodySlider): Rect {
        val y = controls.y + if (slider == BodySlider.HEIGHT) 78 else 132
        return Rect(controls.x + PAD, y, controls.width - PAD * 2, 22)
    }

    private fun stepTitle(): String = if (step == Step.JOB) "CHOOSE YOUR JOB" else "CHOOSE YOUR CLASS"

    private fun rightHeader(): String = if (step == Step.JOB) "CHOOSE YOUR JOB" else "CHOOSE YOUR CLASS"

    private fun noSelectionLabel(): String = if (step == Step.JOB) "NO JOB SELECTED" else "NO CLASS SELECTED"

    private fun noSelectionDescription(): String =
        if (step == Step.JOB) "No job is selected yet." else "No class is selected yet."

    private fun currentRoles(): List<RoleUiDefinitionPayload> = if (step == Step.JOB) payload.jobs else sortedOnboardingClasses()

    private fun selectedRoleId(): String? = if (step == Step.JOB) selectedJobId else selectedClassId

    private fun setSelectedRole(roleId: String?) {
        if (step == Step.JOB) selectedJobId = roleId else selectedClassId = roleId
    }

    private fun canContinueRoleStep(): Boolean {
        val selected = selectedRole() ?: return false
        return step != Step.CLASS || !isLockedUpgradeClass(selected)
    }

    private fun currentScroll(): Int = if (step == Step.JOB) jobScroll else classScroll

    private fun previewRole(): RoleUiDefinitionPayload? {
        val roles = currentRoles()
        return hoveredRoleId?.let { id -> roles.firstOrNull { role -> role.id == id } }
            ?: selectedRoleId()?.let { id -> roles.firstOrNull { role -> role.id == id } }
    }

    private fun selectedRole(): RoleUiDefinitionPayload? {
        val selectedId = selectedRoleId() ?: return null
        return currentRoles().firstOrNull { role -> role.id == selectedId }
    }

    private fun selectedJob(): RoleUiDefinitionPayload? = selectedJobId?.let { id -> payload.jobs.firstOrNull { role -> role.id == id } }

    private fun selectedClass(): RoleUiDefinitionPayload? = selectedClassId?.let { id -> payload.classes.firstOrNull { role -> role.id == id } }

    private fun sortedOnboardingClasses(): List<RoleUiDefinitionPayload> = payload.classes.sortedWith(
        compareBy<RoleUiDefinitionPayload> { if (isStarterClass(it)) 0 else 1 }
            .thenBy { starterClassOrder(it.id) }
            .thenBy { role -> role.displayName.ifBlank { role.id } },
    )

    private fun isLockedClassSlot(slot: RoleSlot): Boolean = slot.role?.let(::isLockedUpgradeClass) == true

    private fun isLockedUpgradeClass(role: RoleUiDefinitionPayload): Boolean = step == Step.CLASS && !isStarterClass(role)

    private fun isStarterClass(role: RoleUiDefinitionPayload): Boolean = role.classification.equals("starter", ignoreCase = true)

    private fun starterClassOrder(id: String): Int = STARTER_CLASS_ORDER.indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE

    private fun updateBodySlider(slider: BodySlider, mouseX: Int) {
        val track = bodySliderRect(bodyControlsRect(), slider)
        val progress = ((mouseX - (track.x + 4)).toDouble() / (track.width - 8).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val value = round((MIN_BODY_SCALE + (MAX_BODY_SCALE - MIN_BODY_SCALE) * progress) * 100.0) / 100.0
        if (slider == BodySlider.HEIGHT) selectedHeight = value else selectedWeight = value
    }

    private fun scaleProgress(value: Double): Double = ((value - MIN_BODY_SCALE) / (MAX_BODY_SCALE - MIN_BODY_SCALE)).coerceIn(0.0, 1.0)

    private fun scaleLabel(value: Double): String = String.format(Locale.ROOT, "%.0f%%", value * 100.0)

    private fun goTo(next: Step) {
        skipPanelEntrance = step == Step.JOB && next == Step.CLASS
        step = next
        stepStartedAtMs = Util.getMillis()
        hoveredRoleId = null
        renderedSlots = emptyList()
    }

    private fun drawCenteredCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int, fontId: ResourceLocation) {
        if (renderAlpha <= MIN_TEXT_RENDER_ALPHA) return
        val component = ckdmText(text, fontId)
        guiGraphics.drawString(font, component, x + (width - font.width(component)) / 2, y, colorWithRenderAlpha(color), false)
    }

    private fun drawCkdm(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, fontId: ResourceLocation) {
        if (renderAlpha <= MIN_TEXT_RENDER_ALPHA) return
        guiGraphics.drawString(font, ckdmText(text, fontId), x, y, colorWithRenderAlpha(color), false)
    }

    private fun drawCkdmPair(guiGraphics: GuiGraphics, label: String, value: String, x: Int, y: Int, width: Int) {
        if (renderAlpha <= MIN_TEXT_RENDER_ALPHA) return
        val valueComponent = ckdmText(value, CKDM_SMALL)
        val valueWidth = font.width(valueComponent)
        val labelText = fitText(label, (width - valueWidth - 5).coerceAtLeast(24), CKDM_SMALL)
        val labelComponent = ckdmText(labelText, CKDM_SMALL)
        guiGraphics.drawString(font, labelComponent, x, y, colorWithRenderAlpha(GOLD), false)
        guiGraphics.drawString(font, valueComponent, x + font.width(labelComponent) + 5, y, colorWithRenderAlpha(WHITE), false)
    }

    private fun drawWrapped(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int, maxLines: Int) {
        if (renderAlpha <= MIN_TEXT_RENDER_ALPHA) return
        font.split(Component.literal(text), width).take(maxLines).forEachIndexed { index, line ->
            guiGraphics.drawString(font, line, x, y + index * WRAPPED_LINE_HEIGHT, colorWithRenderAlpha(color), false)
        }
    }

    private fun ckdmText(text: String, fontId: ResourceLocation): Component =
        Component.literal(text.uppercase(Locale.ROOT)).withStyle { style -> style.withFont(fontId) }

    private fun fitText(text: String, maxWidth: Int, fontId: ResourceLocation): String {
        if (font.width(ckdmText(text, fontId)) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && font.width(ckdmText("$value...", fontId)) > maxWidth) value = value.dropLast(1)
        return "$value..."
    }

    private fun colorWithRenderAlpha(color: Int): Int =
        ((((color ushr 24) and 0xFF) * renderAlpha).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    private fun itemStack(raw: String): ItemStack {
        return RoleItemStacks.fromId(raw, "role icon") ?: ItemStack.EMPTY
    }

    private fun iconTexture(raw: String): ResourceLocation? {
        val icon = raw.trim()
        if (icon.isBlank()) return null
        return runCatching {
            when {
                icon.contains(":") -> ResourceLocation.parse(icon)
                icon.startsWith("textures/") -> ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, icon)
                icon.endsWith(".png") -> ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/$icon")
                else -> ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/icons/$icon.png")
            }
        }.getOrNull()
    }

    private fun playClick() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f))
    }

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val WHITE_MUTED = 0xFFD8D0B8.toInt()
        private const val GOLD = 0xFFFFD66B.toInt()
        private const val DISABLED = 0xFF817865.toInt()
        private const val PAD = 14
        private const val TITLE_Y = 14
        private const val GRID_COLUMNS = 4
        private const val GRID_GAP = 6
        private const val TILE_HEIGHT = 66
        private const val ROLE_ICON_SIZE = 28
        private const val LOCK_ICON_SIZE = 18
        private const val ICON_TEXTURE_SIZE = 16
        private const val LOCK_TEXTURE_SIZE = 16
        private const val VANILLA_ITEM_SIZE = 16
        private const val WRAPPED_LINE_HEIGHT = 12
        private const val PERK_PAIR_LINE_HEIGHT = 11
        private const val PERK_SECTION_HEADER_HEIGHT = 12
        private const val PERK_RANK_LINE_HEIGHT = 10
        private const val MIN_TEXT_RENDER_ALPHA = 0.004f
        private const val MIN_BODY_SCALE = 0.6
        private const val MAX_BODY_SCALE = 1.4
        private const val BODY_SLIDER_HANDLE_WIDTH = 10
        private const val CONTAINER_TEXTURE_WIDTH = 1646
        private const val CONTAINER_TEXTURE_HEIGHT = 256
        private const val CONTAINER_SOURCE_CORNER = 75
        private const val CONTAINER_DEST_CORNER = 14
        private const val TILE_DEST_CORNER = 9
        private const val BUTTON_TEXTURE_SIZE = 8
        private const val BUTTON_HOVER_TEXTURE_SIZE = 10
        private const val BUTTON_SOURCE_CORNER = 2
        private const val BUTTON_HOVER_SOURCE_CORNER = 3
        private const val BUTTON_DEST_CORNER = 4
        private const val BG_TEXTURE_WIDTH = 1919
        private const val BG_TEXTURE_HEIGHT = 1080
        private const val BACKGROUND_PADDING = 36
        private const val BACKGROUND_PARALLAX = 18.0f
        private const val BACKGROUND_ALPHA = 0.5f
        private const val PARALLAX_LERP = 0.12f
        private val GOLD_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_gold.png")
        private val GREY_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_grey.png")
        private val YELLOW_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_container_yellow.png")
        private val GREEN_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green.png")
        private val GREEN_BUTTON_HOVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_green_hover.png")
        private val GRAY_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/9slice_btn_gray.png")
        private val LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
        private val BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/bg_onboarding.png")
        private val STARTER_CLASS_ORDER = listOf("warrior", "rogue", "archer", "wizard", "priest")
        private val CKDM_BOLD = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold")
        private val CKDM_SMALL = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_small")
        private val CKDM_LARGE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "ckdm_bold_large")
    }
}
