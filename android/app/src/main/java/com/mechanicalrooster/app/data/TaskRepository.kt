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

    suspend fun addTask(title: String): OpenTaskEntity {
        val dto = apiClient.api().createTask(CreateTaskRequest(title))
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
    return OpenTaskEntity(
        id = id,
        title = title,
        createdAtMillis = createdAtMillis,
        initialDelayMinutes = initialDelayMinutes,
        repeatIntervalMinutes = repeatIntervalMinutes,
        nextFireAtMillis = computeNextFire(
            createdAtMillis, initialDelayMinutes, repeatIntervalMinutes, System.currentTimeMillis(),
        ),
    )
}

/**
 * First reminder fires initialDelay after creation; afterwards it repeats every
 * repeatInterval. Returns the earliest slot in the future.
 */
fun computeNextFire(
    createdAtMillis: Long,
    initialDelayMinutes: Int,
    repeatIntervalMinutes: Int,
    nowMillis: Long,
): Long {
    val first = createdAtMillis + initialDelayMinutes * 60_000L
    if (first > nowMillis) return first
    val interval = repeatIntervalMinutes * 60_000L
    val periodsElapsed = (nowMillis - first) / interval + 1
    return first + periodsElapsed * interval
}
