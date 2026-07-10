package com.relentlessbadger.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ComputeNextOccurrenceTest {

    private val zone = ZoneId.of("America/New_York")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val monWedFri = 0b0010101
    private val daily = Recurrence(1, RecurUnit.DAYS)

    @Test
    fun `daily recurrence fires the next day at the same time`() {
        // Anchor Fri 2026-07-10 09:00, completed same day 14:00.
        val next = computeNextOccurrence(at(2026, 7, 10, 9), daily, at(2026, 7, 10, 14), zone)
        assertEquals(at(2026, 7, 11, 9), next)
    }

    @Test
    fun `every 3 days steps from the anchor date`() {
        val rec = Recurrence(3, RecurUnit.DAYS)
        val next = computeNextOccurrence(at(2026, 7, 10, 9), rec, at(2026, 7, 10, 14), zone)
        assertEquals(at(2026, 7, 13, 9), next)
    }

    @Test
    fun `completing before the anchor time still yields a strictly future occurrence`() {
        // Completed at 08:00, an hour before the 09:00 occurrence even nagged:
        // the next slot is tomorrow, not today's own anchor.
        val next = computeNextOccurrence(at(2026, 7, 10, 9), daily, at(2026, 7, 10, 8), zone)
        assertEquals(at(2026, 7, 11, 9), next)
    }

    @Test
    fun `months of missed occurrences collapse into a single future one`() {
        val next = computeNextOccurrence(at(2026, 1, 5, 9), daily, at(2026, 7, 10, 14), zone)
        assertEquals(at(2026, 7, 11, 9), next)
    }

    @Test
    fun `weekly bitmask picks the next marked day in the same week`() {
        // Anchor Mon 2026-07-06 09:00 (Mon/Wed/Fri), completed Tue.
        val rec = Recurrence(1, RecurUnit.WEEKS, monWedFri)
        val next = computeNextOccurrence(at(2026, 7, 6, 9), rec, at(2026, 7, 7, 10), zone)
        assertEquals(at(2026, 7, 8, 9), next)
    }

    @Test
    fun `weekly bitmask wraps to the next week after the last marked day`() {
        // Completed Fri after the 09:00 slot; next is Monday.
        val rec = Recurrence(1, RecurUnit.WEEKS, monWedFri)
        val next = computeNextOccurrence(at(2026, 7, 6, 9), rec, at(2026, 7, 10, 10), zone)
        assertEquals(at(2026, 7, 13, 9), next)
    }

    @Test
    fun `every 2 weeks skips the off week`() {
        // Anchor Mon 2026-07-06; completing late in an off week (2026-07-15)
        // must land on the next on-week's Monday, not the off week's days.
        val rec = Recurrence(2, RecurUnit.WEEKS, 0b0000001)
        val next = computeNextOccurrence(at(2026, 7, 6, 9), rec, at(2026, 7, 15, 10), zone)
        assertEquals(at(2026, 7, 20, 9), next)
    }

    @Test
    fun `anchor weekday outside the bitmask defers to the marked days`() {
        // First occurrence hand-picked on Wed 2026-07-08, but the rule says
        // Monday: completing Wednesday moves to next week's Monday.
        val rec = Recurrence(1, RecurUnit.WEEKS, 0b0000001)
        val next = computeNextOccurrence(at(2026, 7, 8, 9), rec, at(2026, 7, 8, 10), zone)
        assertEquals(at(2026, 7, 13, 9), next)
    }

    @Test
    fun `occurrences keep their wall-clock time across DST`() {
        // US spring-forward was 2026-03-08: daily 09:00 stays 09:00 even
        // though the UTC offset changed under it.
        val next = computeNextOccurrence(at(2026, 3, 7, 9), daily, at(2026, 3, 7, 10), zone)
        assertEquals(at(2026, 3, 8, 9), next)
    }

    @Test
    fun `weekly recurrence requires a non-empty bitmask`() {
        assertThrows(IllegalArgumentException::class.java) {
            Recurrence(1, RecurUnit.WEEKS, 0)
        }
    }

    @Test
    fun `everyN below 1 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Recurrence(0, RecurUnit.DAYS)
        }
    }
}
