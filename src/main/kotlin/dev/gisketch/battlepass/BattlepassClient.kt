package dev.gisketch.battlepass

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.CameraType
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.client.settings.KeyModifier
import net.neoforged.neoforge.common.NeoForge

object BattlepassClient {
    private const val CATEGORY = "key.category.${BattlepassMod.MOD_ID}.battlepass"

    val OPEN_BATTLEPASS: KeyMapping = KeyMapping(
        "key.${BattlepassMod.MOD_ID}.open_battlepass",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_B,
        CATEGORY,
    )

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerKeyMappings)
        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(BattlepassCameraController::onCameraAngles)
        NeoForge.EVENT_BUS.addListener(BattlepassCameraController::onCameraDistance)
    }

    private fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_BATTLEPASS)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        BattlepassCameraController.tick()
        while (OPEN_BATTLEPASS.consumeClick()) {
            openBattlepassCamera()
        }
    }

    private fun openBattlepassCamera() {
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null || minecraft.screen is BattlepassCameraScreen) return

        BattlepassCameraController.start()
        minecraft.setScreen(BattlepassCameraScreen())
    }
}

private object BattlepassCameraController {
    private const val FRONT_DISTANCE = 3.2f
    private const val FRONT_PITCH = 8.0f

    private var active = false
    private var previousCameraType: CameraType = CameraType.FIRST_PERSON
    private var ticks = 0
    private var startYaw = 0.0f
    private var startPitch = 0.0f
    private var startDistance = 0.0f
    private var previewYaw = 0.0f
    private var previousBodyYaw = 0.0f
    private var previousBodyYawOld = 0.0f
    private var previousHeadYaw = 0.0f
    private var previousHeadYawOld = 0.0f

    fun start() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val camera = minecraft.gameRenderer.mainCamera

        previousCameraType = minecraft.options.cameraType
        startYaw = camera.yRot
        startPitch = camera.xRot
        startDistance = if (previousCameraType.isFirstPerson) 0.25f else 4.0f
        previewYaw = player.yRot
        previousBodyYaw = player.yBodyRot
        previousBodyYawOld = player.yBodyRotO
        previousHeadYaw = player.yHeadRot
        previousHeadYawOld = player.yHeadRotO
        ticks = 0
        active = true

        faceCamera(player)
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK)
    }

    fun stop() {
        if (!active) return

        active = false
        Minecraft.getInstance().player?.let { player ->
            player.yBodyRot = previousBodyYaw
            player.yBodyRotO = previousBodyYawOld
            player.yHeadRot = previousHeadYaw
            player.yHeadRotO = previousHeadYawOld
        }
        Minecraft.getInstance().options.setCameraType(previousCameraType)
    }

    fun tick() {
        if (!active) return

        Minecraft.getInstance().player?.let(::faceCamera)
        if (ticks < BattlepassConfig.camAnimationTicks()) {
            ticks++
        }
    }

    fun onCameraAngles(event: ViewportEvent.ComputeCameraAngles) {
        if (!active) return

        val partialTick = event.partialTick.toFloat()
        val progress = easedProgress(partialTick)
        val frontYaw = previewYaw + 180.0f + BattlepassConfig.cameraOffsetHorizontal()
        val frontPitch = FRONT_PITCH + BattlepassConfig.cameraOffsetVertical()

        event.yaw = Mth.rotLerp(progress, startYaw, frontYaw)
        event.pitch = Mth.lerp(progress, startPitch, frontPitch)
        event.roll = 0.0f
    }

    fun onCameraDistance(event: CalculateDetachedCameraDistanceEvent) {
        if (!active) return

        val targetDistance = (FRONT_DISTANCE + BattlepassConfig.cameraZoomOffset()).coerceAtLeast(0.5f)
        event.distance = Mth.lerp(easedProgress(0.0f), startDistance, targetDistance)
    }

    private fun easedProgress(partialTick: Float): Float {
        val duration = BattlepassConfig.camAnimationTicks().toFloat().coerceAtLeast(1.0f)
        val progress = ((ticks.toFloat() + partialTick) / duration).coerceIn(0.0f, 1.0f)
        val inverse = 1.0f - progress
        return 1.0f - inverse * inverse * inverse
    }

    private fun faceCamera(player: net.minecraft.client.player.LocalPlayer) {
        player.yBodyRot = previewYaw
        player.yBodyRotO = previewYaw
        player.yHeadRot = previewYaw
        player.yHeadRotO = previewYaw
    }
}

private class BattlepassCameraScreen : Screen(Component.translatable("screen.${BattlepassMod.MOD_ID}.battlepass_camera")) {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    }

    override fun isPauseScreen(): Boolean = false

    override fun removed() {
        BattlepassCameraController.stop()
    }
}