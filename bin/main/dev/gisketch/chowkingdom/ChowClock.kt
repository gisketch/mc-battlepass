package dev.gisketch.chowkingdom

import net.minecraft.world.level.Level

object ChowClock {
    fun now(level: Level, settings: ChowClockSettings = ChowClockSettings()): ChowClockSnapshot = at(level.dayTime, settings)

    fun at(dayTime: Long, settings: ChowClockSettings = ChowClockSettings()): ChowClockSnapshot {
        val clock = settings.normalized()
        val cycleTicks = clock.cycleTicks.toLong()
        val day = Math.floorDiv(dayTime, cycleTicks)
        val tickOfCycle = Math.floorMod(dayTime, cycleTicks)
        val minuteOfDay = minuteOfDayAtTick(tickOfCycle, clock)
        val hour = minuteOfDay / MINUTES_PER_HOUR
        val minute = minuteOfDay % MINUTES_PER_HOUR
        return ChowClockSnapshot(day, hour, minute, tickOfCycle, dayTime)
    }

    fun hour(level: Level, settings: ChowClockSettings = ChowClockSettings()): Int = now(level, settings).hour

    fun day(level: Level, settings: ChowClockSettings = ChowClockSettings()): Long = now(level, settings).day

    fun displayTime(level: Level, settings: ChowClockSettings = ChowClockSettings()): String = now(level, settings).displayTime()

    fun addHours(dayTime: Long, hours: Int, settings: ChowClockSettings = ChowClockSettings()): Long {
        if (hours <= 0) return dayTime
        var day = at(dayTime, settings).day
        var hour = at(dayTime, settings).hour
        repeat(hours) {
            hour += 1
            if (hour >= HOURS_PER_DAY) {
                hour = 0
                day += 1
            }
        }
        return absoluteTick(day, hour, settings)
    }

    fun periodForReset(dayTime: Long, resetHour: Int, settings: ChowClockSettings = ChowClockSettings()): Long {
        val now = at(dayTime, settings)
        val resetTick = absoluteTick(now.day, resetHour, settings)
        return if (dayTime >= resetTick) now.day else now.day - 1
    }

    fun nextDayAtHour(dayTime: Long, hour: Int, settings: ChowClockSettings = ChowClockSettings()): Long {
        val now = at(dayTime, settings)
        val targetHour = hour.coerceIn(0, 23)
        val targetDay = if (now.hour < targetHour) now.day else now.day + 1
        return absoluteTick(targetDay, targetHour, settings)
    }

    fun readyAtOrAfterHour(dayTime: Long, targetTick: Long, hour: Int, settings: ChowClockSettings = ChowClockSettings()): Boolean {
        if (dayTime < targetTick) return false
        val now = at(dayTime, settings)
        val target = at(targetTick, settings)
        return now.day > target.day || now.day == target.day && now.hour >= hour.coerceIn(0, 23)
    }

    fun absoluteTick(day: Long, hour: Int, settings: ChowClockSettings = ChowClockSettings()): Long {
        val clock = settings.normalized()
        return day * clock.cycleTicks.toLong() + tickForHour(hour.coerceIn(0, 23), clock)
    }

    private fun hourAtTick(tick: Long, settings: ChowClockSettings): Int {
        return minuteOfDayAtTick(tick, settings) / MINUTES_PER_HOUR
    }

    private fun minuteOfDayAtTick(tick: Long, settings: ChowClockSettings): Int {
        val cycleTicks = settings.cycleTicks.toLong()
        val dayStartTick = settings.dayStartTick.toLong()
        val nightStartTick = settings.nightStartTick.toLong()
        val normalized = Math.floorMod(tick - dayStartTick, cycleTicks)
        val dayLength = Math.floorMod(nightStartTick - dayStartTick, cycleTicks).coerceAtLeast(1L)
        val nightLength = (cycleTicks - dayLength).coerceAtLeast(1L)
        return if (normalized < dayLength) {
            val progress = normalized.toDouble() / dayLength.toDouble()
            Math.floorMod(settings.dayStartHour * MINUTES_PER_HOUR + (progress * DAY_MINUTES).toInt(), MINUTES_PER_DAY)
        } else {
            val progress = (normalized - dayLength).toDouble() / nightLength.toDouble()
            Math.floorMod(settings.nightStartHour * MINUTES_PER_HOUR + (progress * NIGHT_MINUTES).toInt(), MINUTES_PER_DAY)
        }
    }

    private fun tickForHour(hour: Int, settings: ChowClockSettings): Long {
        val cycleTicks = settings.cycleTicks.toLong()
        val dayLength = Math.floorMod(settings.nightStartTick - settings.dayStartTick, settings.cycleTicks).toLong().coerceAtLeast(1L)
        val nightLength = (cycleTicks - dayLength).coerceAtLeast(1L)
        val dayOffset = Math.floorMod(hour - settings.dayStartHour, HOURS_PER_DAY)
        return if (dayOffset < DAY_HOURS) {
            settings.dayStartTick + (dayLength * dayOffset / DAY_HOURS)
        } else {
            val nightOffset = Math.floorMod(hour - settings.nightStartHour, HOURS_PER_DAY)
            Math.floorMod(settings.nightStartTick.toLong() + (nightLength * nightOffset / NIGHT_HOURS), cycleTicks)
        }
    }

    private const val HOURS_PER_DAY = 24
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = HOURS_PER_DAY * MINUTES_PER_HOUR
    private const val DAY_HOURS = 12
    private const val NIGHT_HOURS = 12
    private const val DAY_MINUTES = DAY_HOURS * MINUTES_PER_HOUR
    private const val NIGHT_MINUTES = NIGHT_HOURS * MINUTES_PER_HOUR
}

class ChowClockSettings(
    var cycleTicks: Int = 24000,
    var dayStartTick: Int = 0,
    var nightStartTick: Int = 12000,
    var dayStartHour: Int = 6,
    var nightStartHour: Int = 18,
) {
    fun normalized(): ChowClockSettings = apply {
        cycleTicks = cycleTicks.coerceIn(1200, 24000 * 20)
        dayStartTick = Math.floorMod(dayStartTick, cycleTicks)
        nightStartTick = Math.floorMod(nightStartTick, cycleTicks)
        if (nightStartTick == dayStartTick) nightStartTick = Math.floorMod(dayStartTick + cycleTicks / 2, cycleTicks)
        dayStartHour = Math.floorMod(dayStartHour, 24)
        nightStartHour = Math.floorMod(nightStartHour, 24)
    }
}

class ChowClockSnapshot(
    val day: Long,
    val hour: Int,
    val minute: Int,
    val tickOfCycle: Long,
    val rawDayTime: Long,
) {
    fun displayTime(): String {
        val displayHour = when (val normalized = hour % 12) {
            0 -> 12
            else -> normalized
        }
        val suffix = if (hour < 12) "AM" else "PM"
        return "${displayHour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $suffix"
    }
}
