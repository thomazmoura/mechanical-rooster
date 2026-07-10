package com.relentlessbadger.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.relentlessbadger.app.BadgerApp
import retrofit2.HttpException

/**
 * Runs a full sync in the background. Enqueued with a network constraint, so
 * work queued while offline fires the moment connectivity returns.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BadgerApp).container
        if (!container.session.current().isSignedIn) return Result.success()
        return try {
            container.repository.sync()
            Result.success()
        } catch (e: HttpException) {
            // Retrying a rejected session is pointless until the user signs
            // in again; anything else (5xx, flaky network) backs off and retries.
            if (e.code() == 401) Result.failure() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
