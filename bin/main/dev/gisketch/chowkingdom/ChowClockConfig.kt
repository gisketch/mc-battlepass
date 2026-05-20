package dev.gisketch.chowkingdom

import net.neoforged.fml.loading.FMLPaths
import kotlin.io.path.exists
import kotlin.io.path.readLines

object ChowClockConfig {
    private var settings = ChowClockSettings()
    private var source = "vanilla"

    private val betterDaysFile
        get() = FMLPaths.CONFIGDIR.get().resolve("betterdays-common.toml")

    fun load() {
        val file = betterDaysFile
        if (!file.exists()) {
            settings = ChowClockSettings()
            source = "vanilla"
            return
        }
        val values = file.readLines()
            .map(String::trim)
            .filter { line -> line.isNotBlank() && !line.startsWith("#") && "=" in line }
            .associate { line ->
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").substringBefore("#").trim().trim('"')
                key to value
            }
        settings = ChowClockSettings(
            cycleTicks = VANILLA_CYCLE_TICKS,
            dayStartTick = values["dayStart"]?.toDoubleOrNull()?.toInt() ?: VANILLA_DAY_START,
            nightStartTick = values["nightStart"]?.toDoubleOrNull()?.toInt() ?: VANILLA_NIGHT_START,
            dayStartHour = 6,
            nightStartHour = 18,
        ).normalized()
        source = "betterdays-common.toml"
    }

    fun current(): ChowClockSettings = settings

    fun sourceName(): String = source

    private const val VANILLA_CYCLE_TICKS = 24000
    private const val VANILLA_DAY_START = 0
    private const val VANILLA_NIGHT_START = 12000
}
