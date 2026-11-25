package dev.agwidgets.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.agwidgets.data.QuotaBucket
import dev.agwidgets.notify.Notifier
import dev.agwidgets.widget.LimitsWidget
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Fires locally on the phone at the scheduled Antigravity quota reset_time,
 * even when the laptop is turned off or sleeping.
 */
class ResetAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bucketId = inputData.getString(KEY_BUCKET_ID) ?: return Result.success()
        val kind = inputData.getString(KEY_KIND) ?: "other"
        val resetTime = inputData.getString(KEY_RESET_TIME) ?: return Result.success()

        Notifier.notifyScheduledReset(applicationContext, bucketId, kind, resetTime)
        LimitsWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val KEY_BUCKET_ID = "bucket_id"
        private const val KEY_KIND = "kind"
        private const val KEY_RESET_TIME = "reset_time"

        fun scheduleForBuckets(context: Context, buckets: List<QuotaBucket>) {
            val wm = WorkManager.getInstance(context.applicationContext)
            val now = Instant.now()

            for (bucket in buckets) {
                val resetIso = bucket.resetTime ?: continue
                // Only schedule an alert if quota was actually consumed (< 99%)
                val frac = bucket.remainingFraction
                if (frac != null && frac >= 0.99) continue

                val target = parseInstant(resetIso) ?: continue
                val delayMs = Duration.between(now, target).toMillis()
                if (delayMs <= 5_000L) continue

                val data = Data.Builder()
                    .putString(KEY_BUCKET_ID, bucket.id)
                    .putString(KEY_KIND, bucket.kind)
                    .putString(KEY_RESET_TIME, resetIso)
                    .build()

                val req = OneTimeWorkRequestBuilder<ResetAlertWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()

                wm.enqueueUniqueWork(
                    "agw_reset_${bucket.id}",
                    ExistingWorkPolicy.REPLACE,
                    req
                )
            }
        }

        private fun parseInstant(iso: String): Instant? {
            return try {
                OffsetDateTime.parse(iso).toInstant()
            } catch (_: Exception) {
                try {
                    Instant.parse(iso)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
