package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowClock
import dev.gisketch.chowkingdom.ChowClockConfig
import net.minecraft.world.level.Level

object NpcTime {
    private fun settings() = ChowClockConfig.current()

    fun day(level: Level): Long = ChowClock.day(level, settings())

    fun hour(level: Level): Int = ChowClock.hour(level, settings())

    fun at(dayTime: Long) = ChowClock.at(dayTime, settings())

    fun activityAt(definition: NpcScheduleDefinition, level: Level): String = definition.activityAtHour(hour(level))

    fun addHours(dayTime: Long, hours: Int): Long = ChowClock.addHours(dayTime, hours, settings())

    fun periodForReset(dayTime: Long, resetHour: Int): Long = ChowClock.periodForReset(dayTime, resetHour, settings())

    fun nextDayAtHour(dayTime: Long, hour: Int): Long = ChowClock.nextDayAtHour(dayTime, hour, settings())

    fun readyAtOrAfterHour(dayTime: Long, targetTick: Long, hour: Int): Boolean = ChowClock.readyAtOrAfterHour(dayTime, targetTick, hour, settings())
}
