package com.mechanicalrooster.app.data

import com.mechanicalrooster.app.db.OpenTaskDao
import com.mechanicalrooster.app.db.OpenTaskEntity
import com.mechanicalrooster.app.notify.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.time.Instant

class TaskRepository(
    private val apiClient: ApiClient,
    private val dao: OpenTaskDao,
    private val scheduler: ReminderScheduler,
) {

    fun openTasks(): Flow<List<OpenTaskEntity>> = dao.observeActive()

    suspend fun addTask(title: String, firstWarningAtMillis: Long? = null): OpenTaskEntity {
        val firstWarningAt = firstWarningAtMillis?.let { Instant.ofEpochMilli(it).toString() }
        val dto = apiClient.api().createTask(CreateTaskRequest(title, firstWarningAt))
        val entity = dto.toEntity()
        dao.upsert(entity)
        scheduler.schedule(entity)
        return entity
    }

    /**
     * Marks the task done locally first (so the nagging stops immediately even
     * offline), then tells the API. If the call fails the row stays flagged as
     * pendingDone and is retried on the next sync.
     */
    suspend fun completeTask(id: String) {
        dao.markPendingDone(id)
        scheduler.cancel(id)
        pushCompletion(id)
    }

    /**
     * Pushes the next nag out by [minutes] from now, reschedules the alarm and
     * clears the current reminder. Purely local: the new fire time lives in Room
     * and is preserved across syncs, so the server never needs to know.
     */
    suspend fun snoozeTask(id: String, minutes: Int) {
        val task = dao.getById(id) ?: return
        val next = task.copy(nextFireAtMillis = System.currentTimeMillis() + minutes * 60_000L)
        dao.upsert(next)
        scheduler.schedule(next)
        scheduler.dismissNotification(id)
    }

    suspend fun sync() {
        flushPendingCompletions()

        val remote = apiClient.api().getTasks("open")
        val known = dao.getAll().associateBy { it.id }
        val entities = remote.map { dto ->
            // Preserve the local nag state for tasks we already track.
            known[dto.id] ?: dto.toEntity()
        }
        dao.upsertAll(entities)
        dao.deleteActiveNotIn(remote.map { it.id }.ifEmpty { listOf("") })
        dao.getActive().forEach(scheduler::schedule)
    }

    suspend fun titles(): List<String> = apiClient.api().getTitles()

    suspend fun updateSettings(settings: SettingsDto): SettingsDto =
        apiClient.api().updateSettings(settings)

    suspend fun signOut() {
        dao.getAll().forEach { scheduler.cancel(it.id) }
        dao.clear()
    }

    private suspend fun flushPendingCompletions() {
        dao.getPendingDone().forEach { pushCompletion(it.id) }
    }

    private suspend fun pushCompletion(id: String) {
        try {
            apiClient.api().completeTask(id)
            dao.delete(id)
        } catch (e: HttpException) {
            // Gone on the server already; stop retrying.
            if (e.code() == 404) dao.delete(id)
        } catch (_: Exception) {
            // Offline; the row stays pendingDone and is flushed by the next sync().
        }
    }
}

fun TaskDto.toEntity(): OpenTaskEntity {
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
            System.currentTimeMillis(), firstWarningAtMillis,
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
