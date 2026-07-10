package com.relentlessbadger.app.data

import com.relentlessbadger.app.db.OpenTaskDao
import com.relentlessbadger.app.db.OpenTaskEntity
import com.relentlessbadger.app.db.TitleHistoryDao
import com.relentlessbadger.app.notify.ReminderScheduler
import com.relentlessbadger.app.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.time.Instant
import java.util.UUID

/**
 * All business logic runs against the local database; the network is only
 * touched inside [sync]. Every mutation commits locally, flags what still
 * needs to reach the API (pendingCreate / pendingDone / settings-dirty) and
 * asks the [SyncScheduler] for a push — so the app is fully usable offline.
 */
class TaskRepository(
    private val apiClient: ApiClient,
    private val dao: OpenTaskDao,
    private val titleDao: TitleHistoryDao,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsStore,
    private val syncScheduler: SyncScheduler,
    private val timeSource: TimeSource = TimeSource.SYSTEM,
) {

    fun openTasks(): Flow<List<OpenTaskEntity>> = dao.observeActive()

    /**
     * Creates the task locally and schedules its first reminder immediately.
     * The id is minted here so a later push (and any retry of it) is
     * idempotent; the row stays flagged pendingCreate until acknowledged.
     */
    suspend fun addTask(title: String, firstWarningAtMillis: Long? = null): OpenTaskEntity {
        val now = timeSource.now()
        val session = settings.current()
        val entity = OpenTaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAtMillis = now,
            initialDelayMinutes = session.initialDelayMinutes,
            repeatIntervalMinutes = session.repeatIntervalMinutes,
            firstWarningAtMillis = firstWarningAtMillis,
            nextFireAtMillis = computeNextFire(
                now, session.initialDelayMinutes, session.repeatIntervalMinutes,
                now, firstWarningAtMillis,
            ),
            pendingCreate = true,
        )
        dao.upsert(entity)
        scheduler.schedule(entity)
        titleDao.recordUse(title, now)
        syncScheduler.requestSync()
        return entity
    }

    /**
     * Marks the task done locally, so the nagging stops immediately even
     * offline. The row stays flagged pendingDone until a sync pushes it.
     */
    suspend fun completeTask(id: String) {
        dao.markPendingDone(id)
        scheduler.cancel(id)
        syncScheduler.requestSync()
    }

    /**
     * Pushes the next nag out by [minutes] from now, reschedules the alarm and
     * clears the current reminder. Purely local: the new fire time lives in Room
     * and is preserved across syncs, so the server never needs to know.
     */
    suspend fun snoozeTask(id: String, minutes: Int) {
        val task = dao.getById(id) ?: return
        val next = task.copy(nextFireAtMillis = timeSource.now() + minutes * 60_000L)
        dao.upsert(next)
        scheduler.schedule(next)
        scheduler.dismissNotification(id)
    }

    /**
     * A reminder alarm fired: show the nag and schedule the next repeat. The
     * chain stops once the task is completed (row removed or pendingDone).
     */
    suspend fun onReminderFired(id: String) {
        val task = dao.getById(id) ?: return
        if (task.pendingDone) return
        val session = settings.current()
        scheduler.showReminder(task, session.mediumWaitMinutes, session.longWaitMinutes)
        val next = task.copy(
            nextFireAtMillis = timeSource.now() + task.repeatIntervalMinutes * 60_000L,
        )
        dao.upsert(next)
        scheduler.schedule(next)
    }

    /**
     * Re-arms every open task's alarm after a reboot or app update. Fire times
     * that passed while the device was off are nudged one minute out.
     */
    suspend fun reArmAlarms() {
        val now = timeSource.now()
        for (task in dao.getActive()) {
            val next = if (task.nextFireAtMillis <= now) {
                task.copy(nextFireAtMillis = now + 60_000L)
            } else {
                task
            }
            dao.upsert(next)
            scheduler.schedule(next)
        }
    }

    /**
     * Saves settings locally — they take effect immediately — and flags them
     * dirty until a sync pushes them (last write wins).
     */
    suspend fun updateSettings(newSettings: SettingsDto) {
        settings.saveSettings(newSettings)
        settings.markSettingsDirty()
        syncScheduler.requestSync()
    }

    suspend fun titles(): List<String> = titleDao.getRanked()

    /**
     * Push local changes, then pull server state. Each phase leaves the data
     * consistent if a later one fails: pending flags survive until their push
     * is acknowledged, and the pull never removes rows with pending changes.
     * Network errors propagate so callers (worker/UI) can retry or report.
     */
    suspend fun sync() {
        pushPendingCreates()
        flushPendingCompletions()
        pushSettingsIfDirty()

        val remote = apiClient.api().getTasks("open")
        val known = dao.getAll().associateBy { it.id }
        val entities = remote.map { dto ->
            // Preserve the local nag state for tasks we already track.
            known[dto.id] ?: dto.toEntity(timeSource.now())
        }
        dao.upsertAll(entities)
        val remoteIds = remote.map { it.id }.toSet()
        known.values
            .filter { !it.pendingDone && !it.pendingCreate && it.id !in remoteIds }
            .forEach { scheduler.cancel(it.id) }
        dao.deleteSyncedNotIn(remoteIds.ifEmpty { setOf("") }.toList())

        titleDao.upsertFromServer(apiClient.api().getTitles(), timeSource.now())
        pullSettingsIfClean()

        dao.getActive().forEach(scheduler::schedule)
    }

    suspend fun signOut() {
        // Best-effort flush so queued offline work isn't silently dropped.
        try {
            sync()
        } catch (_: Exception) {
        }
        dao.getAll().forEach { scheduler.cancel(it.id) }
        dao.clear()
        titleDao.clear()
    }

    private suspend fun pushPendingCreates() {
        for (task in dao.getPendingCreate()) {
            try {
                apiClient.api().createTask(
                    CreateTaskRequest(
                        title = task.title,
                        firstWarningAt = task.firstWarningAtMillis
                            ?.let { Instant.ofEpochMilli(it).toString() },
                        id = task.id,
                        createdAt = Instant.ofEpochMilli(task.createdAtMillis).toString(),
                        initialDelayMinutes = task.initialDelayMinutes,
                        repeatIntervalMinutes = task.repeatIntervalMinutes,
                    ),
                )
                dao.clearPendingCreate(task.id)
            } catch (e: HttpException) {
                when {
                    e.code() == 401 -> throw e
                    // Id taken by another user; give the task a fresh one and
                    // let the next sync push it. Practically unreachable.
                    e.code() == 409 -> {
                        val reborn = task.copy(id = UUID.randomUUID().toString())
                        dao.delete(task.id)
                        scheduler.cancel(task.id)
                        dao.upsert(reborn)
                        scheduler.schedule(reborn)
                    }
                    // Other 4xx would repeat forever; drop the flag instead of
                    // wedging sync. 5xx: keep the flag and retry next sync.
                    e.code() in 400..499 -> dao.clearPendingCreate(task.id)
                }
            }
        }
    }

    private suspend fun flushPendingCompletions() {
        for (task in dao.getPendingDone()) {
            try {
                apiClient.api().completeTask(task.id)
                dao.delete(task.id)
            } catch (e: HttpException) {
                when {
                    e.code() == 401 -> throw e
                    // Gone on the server already; stop retrying.
                    e.code() == 404 -> dao.delete(task.id)
                }
            }
        }
    }

    private suspend fun pushSettingsIfDirty() {
        if (!settings.isSettingsDirty()) return
        val session = settings.current()
        apiClient.api().updateSettings(
            SettingsDto(
                session.initialDelayMinutes, session.repeatIntervalMinutes,
                session.mediumWaitMinutes, session.longWaitMinutes,
            ),
        )
        settings.clearSettingsDirty()
    }

    private suspend fun pullSettingsIfClean() {
        if (settings.isSettingsDirty()) return
        settings.saveSettings(apiClient.api().getSettings())
    }
}

fun TaskDto.toEntity(nowMillis: Long): OpenTaskEntity {
    val createdAtMillis = Instant.parse(createdAt).toEpochMilli()
    val firstWarningAtMillis = firstWarningAt?.let { Instant.parse(it).toEpochMilli() }
    return OpenTaskEntity(
        id = id,
        title = title,
        createdAtMillis = createdAtMillis,
        initialDelayMinutes = initialDelayMinutes,
        repeatIntervalMinutes = repeatIntervalMinutes,
        firstWarningAtMillis = firstWarningAtMillis,
        nextFireAtMillis = computeNextFire(
            createdAtMillis, initialDelayMinutes, repeatIntervalMinutes,
            nowMillis, firstWarningAtMillis,
        ),
    )
}

/**
 * First reminder fires at [firstWarningAtMillis] when set, otherwise initialDelay
 * after creation; afterwards it repeats every repeatInterval. Returns the earliest
 * slot in the future.
 */
fun computeNextFire(
    createdAtMillis: Long,
    initialDelayMinutes: Int,
    repeatIntervalMinutes: Int,
    nowMillis: Long,
    firstWarningAtMillis: Long? = null,
): Long {
    val first = firstWarningAtMillis ?: (createdAtMillis + initialDelayMinutes * 60_000L)
    if (first > nowMillis) return first
    val interval = repeatIntervalMinutes * 60_000L
    val periodsElapsed = (nowMillis - first) / interval + 1
    return first + periodsElapsed * interval
}
