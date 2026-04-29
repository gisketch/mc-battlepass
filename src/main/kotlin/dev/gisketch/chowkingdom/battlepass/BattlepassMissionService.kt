package dev.gisketch.chowkingdom.battlepass

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

enum class BattlepassMissionScope(val id: String) {
    PERMANENT("permanent"),
    DAILY("daily"),
    WEEKLY("weekly"),
}

data class BattlepassMissionEntry(
    val scope: BattlepassMissionScope,
    val index: Int,
    val event: BattlepassXpEventDefinition,
) {
    val key: String = BattlepassMissionService.missionKey(scope, event, index)
}

object BattlepassMissionService {
    fun allEntries(pass: BattlepassPassDefinition): List<BattlepassMissionEntry> =
        permanentEntries(pass) + rotatingEntries(BattlepassMissionScope.DAILY, pass.dailyEvents.events) + rotatingEntries(BattlepassMissionScope.WEEKLY, pass.weeklyEvents.events)

    fun permanentEntries(pass: BattlepassPassDefinition): List<BattlepassMissionEntry> {
        val events = pass.permanentEvents.ifEmpty { pass.xpEvents }
        return events.mapIndexed { index, event -> BattlepassMissionEntry(BattlepassMissionScope.PERMANENT, index, event) }
    }

    fun rotatingEntries(scope: BattlepassMissionScope, events: List<BattlepassXpEventDefinition>): List<BattlepassMissionEntry> =
        events.mapIndexed { index, event -> BattlepassMissionEntry(scope, index, event) }

    fun missionKey(scope: BattlepassMissionScope, event: BattlepassXpEventDefinition, index: Int): String {
        val localKey = event.id.ifBlank { listOf(event.event, event.eventDesc).filter(String::isNotBlank).joinToString(":") }.ifBlank { "event_$index" }
        return "${scope.id}:$localKey"
    }

    fun progressiveGoal(event: BattlepassXpEventDefinition): Int = event.progressGoals.lastOrNull() ?: event.xpCap.takeIf { cap -> cap > 0 } ?: event.progress.coerceAtLeast(1)

    fun isProgressive(event: BattlepassXpEventDefinition): Boolean = event.type.equals("progressive", ignoreCase = true)

    fun isCappedRepeating(event: BattlepassXpEventDefinition): Boolean = !isProgressive(event) && event.xpCap > 0

    fun periodKey(scope: BattlepassMissionScope, definition: BattlepassRotatingMissionDefinition): String = when (scope) {
        BattlepassMissionScope.PERMANENT -> BattlepassMissionScope.PERMANENT.id
        BattlepassMissionScope.DAILY -> "daily:${dailyPeriodDate(definition)}"
        BattlepassMissionScope.WEEKLY -> "weekly:${weeklyPeriodDate(definition)}"
    }

    private fun dailyPeriodDate(definition: BattlepassRotatingMissionDefinition): LocalDate {
        val now = now(definition)
        val resetTime = resetTime(definition)
        return if (now.toLocalTime().isBefore(resetTime)) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    private fun weeklyPeriodDate(definition: BattlepassRotatingMissionDefinition): LocalDate {
        val now = now(definition)
        val resetDay = resetDay(definition)
        val daysSinceReset = Math.floorMod(now.dayOfWeek.value - resetDay.value, 7).toLong()
        var resetDate = now.toLocalDate().minusDays(daysSinceReset)
        if (daysSinceReset == 0L && now.toLocalTime().isBefore(resetTime(definition))) {
            resetDate = resetDate.minusWeeks(1)
        }
        return resetDate
    }

    private fun now(definition: BattlepassRotatingMissionDefinition): LocalDateTime = LocalDateTime.now(zoneId(definition.timeZone))

    private fun resetTime(definition: BattlepassRotatingMissionDefinition): LocalTime =
        LocalTime.of(definition.resetHour.coerceIn(0, 23), definition.resetMinute.coerceIn(0, 59))

    private fun resetDay(definition: BattlepassRotatingMissionDefinition): DayOfWeek =
        runCatching { DayOfWeek.valueOf(definition.resetOnDay.trim().uppercase(Locale.ROOT)) }.getOrDefault(DayOfWeek.SUNDAY)

    private fun zoneId(value: String): ZoneId = runCatching { ZoneId.of(value) }
        .recoverCatching { ZoneId.of(value.replace("GMT+", "GMT+0").replace("GMT-", "GMT-0")) }
        .getOrDefault(ZoneOffset.ofHours(8))
}