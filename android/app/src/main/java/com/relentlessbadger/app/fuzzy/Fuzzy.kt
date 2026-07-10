package com.relentlessbadger.app.fuzzy

/**
 * fzf-style fuzzy matching: the query must appear in the candidate as a
 * subsequence (case-insensitive). Like fzf v2, a small dynamic program finds
 * the best-scoring alignment, favoring word-boundary hits and consecutive
 * runs, so "totr" ranks "take out trash" highly.
 */
object Fuzzy {

    private const val NO_MATCH = Int.MIN_VALUE / 2
    private const val BOUNDARY_BONUS = 8
    private const val CONSECUTIVE_BONUS = 4

    fun score(query: String, candidate: String): Int? {
        if (query.isBlank()) return 0
        val q = query.lowercase()
        val c = candidate.lowercase()
        if (q.length > c.length) return null

        // prev[j] = best score matching the query up to the previous char,
        // with that char matched exactly at candidate position j.
        var prev = IntArray(c.length) { NO_MATCH }
        for (qi in q.indices) {
            val cur = IntArray(c.length) { NO_MATCH }
            for (j in qi until c.length) {
                if (c[j] != q[qi]) continue
                val base = 1 + if (j == 0 || !c[j - 1].isLetterOrDigit()) BOUNDARY_BONUS else 0
                if (qi == 0) {
                    cur[j] = base
                    continue
                }
                var best = NO_MATCH
                for (k in qi - 1 until j) {
                    if (prev[k] == NO_MATCH) continue
                    val transition =
                        if (k == j - 1) CONSECUTIVE_BONUS else -(j - k - 1) // gap penalty
                    if (prev[k] + transition > best) best = prev[k] + transition
                }
                if (best != NO_MATCH) cur[j] = base + best
            }
            prev = cur
        }
        val result = prev.max()
        return if (result == NO_MATCH) null else result
    }

    /**
     * Ranks candidates by score, then by length (shorter wins), preserving the
     * input order (frequency/recency from the server) as the final tiebreak.
     */
    fun rank(query: String, candidates: List<String>, limit: Int = 8): List<String> =
        candidates
            .mapNotNull { candidate -> score(query, candidate)?.let { it to candidate } }
            .sortedWith(compareByDescending<Pair<Int, String>> { it.first }.thenBy { it.second.length })
            .take(limit)
            .map { it.second }
}
