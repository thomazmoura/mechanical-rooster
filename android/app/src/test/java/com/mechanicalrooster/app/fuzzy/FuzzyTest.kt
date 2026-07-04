package com.mechanicalrooster.app.fuzzy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTest {

    @Test
    fun `totr matches take out trash`() {
        assertNotNull(Fuzzy.score("totr", "take out trash"))
    }

    @Test
    fun `non-subsequence does not match`() {
        assertNull(Fuzzy.score("xyz", "take out trash"))
        assertNull(Fuzzy.score("trot", "take out trash"))
    }

    @Test
    fun `match is case-insensitive`() {
        assertNotNull(Fuzzy.score("TOTR", "Take Out Trash"))
    }

    @Test
    fun `word-boundary initials beat scattered matches`() {
        val ranked = Fuzzy.rank("totr", listOf("the other train", "take out trash"))
        assertEquals("take out trash", ranked.first())
    }

    @Test
    fun `rank filters non-matches and respects limit`() {
        val candidates = listOf("walk dog", "take out trash", "water plants", "buy milk")
        val ranked = Fuzzy.rank("wa", candidates, limit = 1)
        assertEquals(1, ranked.size)
        assertTrue(ranked.first() in listOf("walk dog", "water plants"))
    }

    @Test
    fun `empty query matches everything with zero score`() {
        assertEquals(0, Fuzzy.score("", "anything"))
    }

    @Test
    fun `exact prefix scores higher than gapped match`() {
        val prefix = Fuzzy.score("take", "take out trash")!!
        val gapped = Fuzzy.score("tkot", "take out trash")!!
        assertTrue(prefix > gapped)
    }
}
