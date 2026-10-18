package com.wiggyserver.immichwallpaper.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.wiggyserver.immichwallpaper.AppLog
import com.wiggyserver.immichwallpaper.data.FailReason
import com.wiggyserver.immichwallpaper.data.RefillResult
import com.wiggyserver.immichwallpaper.graph
import java.util.concurrent.TimeUnit

/** WorkManager jobs: queue refill after every swap or change, and a twice-daily offline-set sync. */
object Work {
    private const val REFILL = "refill"
    private const val SYNC = "offline-sync"

    private fun network(unmeteredOnly: Boolean) =
        Constraints.Builder().setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()

    /**
     * Appends rather than keeps: a refill that is already running may stop early because its slides
     * became stale, so a request made meanwhile must still run. A refill of a full queue is a no-op.
     */
    fun refill(context: Context, unmeteredOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<RefillWorker>()
            .setConstraints(network(unmeteredOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(REFILL, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Turning the app off: no refills or syncs until the service is turned on again. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(REFILL)
            cancelUniqueWork(SYNC)
        }
    }

    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<OfflineSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(network(unmeteredOnly = false))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class RefillWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val graph = applicationContext.graph
        val result = graph.queue.refill()
        graph.recordRefill(result)
        AppLog.log("REFILL", "$result queue=${graph.queue.size}" + (graph.queue.smallPoolSize?.let { " pool=$it" } ?: ""))
        return when (result) {
            is RefillResult.Failed -> if (result.reason == FailReason.UNREACHABLE && runAttemptCount < 5) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}

class OfflineSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val graph = applicationContext.graph
        val result = graph.offline.sync()
        graph.offline.cleanup()
        AppLog.log("OFFLINE_SYNC", result.toString())
        return Result.success()
    }
}
