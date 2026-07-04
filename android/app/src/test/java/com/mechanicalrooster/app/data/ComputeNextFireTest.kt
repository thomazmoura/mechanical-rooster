package com.mechanicalrooster.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeNextFireTest {

    private val minute = 60_000L

    @Test
    fun `first reminder fires initialDelay after creation`() {
        val createdAt = 1_000_000L
        val next = computeNextFire(createdAt, 60, 15, nowMillis = createdAt + 10 * minute)
        assertEquals(createdAt + 60 * minute, next)
    }

    @Test
    fun `after the first reminder it repeats on the interval`() {
        val createdAt = 1_000_000L
        // 70 min in: first fire (60m) passed, next repeat lands at 75m.
        val next = computeNextFire(createdAt, 60, 15, nowMillis = createdAt + 70 * minute)
        assertEquals(createdAt + 75 * minute, next)
    }

    @Test
    fun `next fire is always in the future`() {
        val createdAt = 0L
        val now = 1_000 * minute
        val next = computeNextFire(createdAt, 60, 15, nowMillis = now)
        assert(next > now)
        assert(next - now <= 15 * minute)
    }
}
