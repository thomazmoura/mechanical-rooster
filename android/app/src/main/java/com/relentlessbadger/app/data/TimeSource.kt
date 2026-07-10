package com.relentlessbadger.app.data

/** Clock seam so scenario tests can control time. */
fun interface TimeSource {
    fun now(): Long

    companion object {
        val SYSTEM = TimeSource { System.currentTimeMillis() }
    }
}
