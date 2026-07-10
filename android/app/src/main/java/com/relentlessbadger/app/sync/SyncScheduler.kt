package com.relentlessbadger.app.sync

/**
 * Asks for a sync with the backend to happen as soon as possible — immediately
 * when online, or as soon as connectivity returns. Local mutations call this
 * after committing; they never talk to the network themselves.
 */
fun interface SyncScheduler {
    fun requestSync()
}
