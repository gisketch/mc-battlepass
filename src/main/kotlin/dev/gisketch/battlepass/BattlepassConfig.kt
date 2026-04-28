package dev.gisketch.battlepass

import net.neoforged.neoforge.common.ModConfigSpec

object BattlepassConfig {
    val SPEC: ModConfigSpec
    private val client: Client

    init {
        val specPair = ModConfigSpec.Builder().configure(::Client)
        client = specPair.left
        SPEC = specPair.right
    }

    fun camAnimationTicks(): Int = client.camAnimation.getAsInt()
    fun cameraOffsetHorizontal(): Float = client.cameraOffsetHorizontal.getAsDouble().toFloat()
    fun cameraOffsetVertical(): Float = client.cameraOffsetVertical.getAsDouble().toFloat()
    fun cameraZoomOffset(): Float = client.cameraZoomOffset.getAsDouble().toFloat()

    private class Client(builder: ModConfigSpec.Builder) {
        val camAnimation: ModConfigSpec.IntValue = builder
            .comment("Battlepass preview camera animation duration in ticks. Lower values animate faster.")
            .translation("config.${BattlepassMod.MOD_ID}.cam_animation")
            .defineInRange("cam_animation", 8, 1, 40)

        val cameraOffsetHorizontal: ModConfigSpec.DoubleValue = builder
            .comment("Battlepass preview camera horizontal framing offset in degrees. Negative values push the character left on screen.")
            .translation("config.${BattlepassMod.MOD_ID}.camera_offset_horizontal")
            .defineInRange("camera_offset_horizontal", -10.0, -120.0, 120.0)

        val cameraOffsetVertical: ModConfigSpec.DoubleValue = builder
            .comment("Battlepass preview camera vertical framing offset in degrees. Positive values push the character higher on screen.")
            .translation("config.${BattlepassMod.MOD_ID}.camera_offset_vertical")
            .defineInRange("camera_offset_vertical", 5.0, -80.0, 80.0)

        val cameraZoomOffset: ModConfigSpec.DoubleValue = builder
            .comment("Battlepass preview camera zoom offset in blocks. Negative values zoom in, positive values zoom out.")
            .translation("config.${BattlepassMod.MOD_ID}.camera_zoom_offset")
            .defineInRange("camera_zoom_offset", 0.0, -2.5, 10.0)
    }
}