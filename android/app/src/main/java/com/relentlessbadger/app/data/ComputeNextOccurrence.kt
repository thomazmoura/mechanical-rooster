package com.relentlessbadger.app.data

import com.relentlessbadger.app.db.OpenTaskEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class RecurUnit {
    DAYS, WEEKS;

    fun wire(): String = name.lowercase()

    companion object {
        fun fromWire(value: String?): RecurUnit? = when (value) {
            "days" -> DAYS
            "weeks" -> WEEKS
            else -> null
        }
    }
}

/**
 * A task's recurrence rule. [daysOfWeek] is a bitmask (bit 0 = Monday ..
 * bit 6 = Sunday) and only meaningful when [unit] is WEEKS, where it must
 * have at least one bit set.
 */
data class Recurrence(
    val everyN: Int,
    val unit: RecurUnit,
    val daysOfWeek: Int = 0,
) {
    init {
        require(everyN >= 1) { "everyN must be at least 1" }
        require(unit != RecurUnit.WEEKS || daysOfWeek in 1..127) {
            "weekly recurrence needs a daysOfWeek bitmask between 1 and 127"
        }
    }
}

fun OpenTaskEntity.recurrence(): Recurrence? {
    val everyN = recurEveryN ?: return null
    val unit = RecurUnit.fromWire(recurUnit) ?: return null
    return Recurrence(everyN, unit, recurDaysOfWeek ?: 0)
}

/**
 * First on-schedule instant strictly after max([afterMillis], [anchorMillis]).
 *
 * The anchor is the current occurrence's first-warning time; its local
 * time-of-day (in [zone]) is the series' time-of-day and its date anchors the
 * cadence, so every spawned occurrence stays aligned to the schedule no matter
 * when the previous one was completed. Expanding in local wall time keeps the
 * clock time stable across DST transitions.
 */
fun computeNextOccurrence(
    anchorMillis: Long,
    recurrence: Recurrence,
    afterMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val anchor = Instant.ofEpochMilli(anchorMillis).atZone(zone)
    val anchorDate = anchor.toLocalDate()
    val timeOfDay = anchor.toLocalTime()
    val floor = maxOf(afterMillis, anchorMillis)
    val floorDate = Instant.ofEpochMilli(floor).atZone(zone).toLocalDate()

    fun instantOn(date: java.time.LocalDate): Long =
        date.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()

    when (recurrence.unit) {
        RecurUnit.DAYS -> {
            val stepDays = recurrence.everyN.toLong()
            // Land on the last on-schedule date not after the floor date, then
            // step forward until strictly past the floor instant.
            val elapsed = ChronoUnit.DAYS.between(anchorDate, floorDate)
            var k = maxOf(0L, elapsed / stepDays)
            while (true) {
                val candidate = instantOn(anchorDate.plusDays(k * stepDays))
                if (candidate > floor) return candidate
                k++
            }
        }
        RecurUnit.WEEKS -> {
            val anchorWeekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val floorWeekStart = floorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val stepWeeks = recurrence.everyN.toLong()
            val elapsedWeeks = ChronoUnit.WEEKS.between(anchorWeekStart, floorWeekStart)
            var m = maxOf(0L, elapsedWeeks / stepWeeks)
            while (true) {
                val weekStart = anchorWeekStart.plusWeeks(m * stepWeeks)
                for (d in 0..6) {
                    if (recurrence.daysOfWeek and (1 shl d) == 0) continue
                    val candidate = instantOn(weekStart.plusDays(d.toLong()))
                    if (candidate > floor) return candidate
                }
                m++
            }
        }
    }
}
