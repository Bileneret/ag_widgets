package dev.agwidgets.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.agwidgets.BuildConfig
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.NtfyClient
import dev.agwidgets.data.StatusStore
import dev.agwidgets.data.ZCodeClient
import dev.agwidgets.data.ZCodeStore
import dev.agwidgets.notify.Notifier
import dev.agwidgets.widget.LimitsWidget
import java.util.concurrent.TimeUnit

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        var anyNetworkFailure = false

        // 1. Poll ntfy channel (Antigravity quotas + PC context summary)
        val mode = StatusStore.getMode(applicationContext)
        val topic = if (mode == StatusStore.MODE_ZCODE) "" else StatusStore.getTopic(applicationContext)
        if (topic.isNotBlank()) {
            val res = NtfyClient.fetchLatestMessage(topic)
            res.fold(
                onSuccess = { jsonText ->
                    val parsed = AgStatus.fromJson(jsonText)
                    if (parsed != null) {
                        val (prev, curr) = StatusStore.saveNewStatus(applicationContext, jsonText)
                        if (curr != null) {
                            ResetAlertWorker.scheduleForBuckets(applicationContext, curr.buckets)
                            Notifier.checkAndNotify(applicationContext, prev, curr)
                        }
                    } else {
                        StatusStore.saveError(applicationContext, "Неверный JSON в сообщении ntfy")
                    }
                },
                onFailure = { err ->
                    StatusStore.saveError(applicationContext, err.message ?: "Ошибка сети ntfy")
                    anyNetworkFailure = true
                }
            )
        }

        // 2. Direct ZCode API check (works autonomously on phone even when laptop is off)
        if (BuildConfig.ZCODE_ENABLED) {
            val zcodeToken = ZCodeStore.getToken(applicationContext)
            if (zcodeToken.isNotBlank()) {
                val deviceMid = ZCodeStore.getOrCreateDeviceMid(applicationContext)
                val zRes = ZCodeClient.fetchStatus(zcodeToken, deviceMid)
                zRes.onSuccess { zStatus ->
                    val (prevZ, currZ) = ZCodeStore.saveDirect(applicationContext, zStatus)
                    Notifier.checkZCodeCouponTransition(applicationContext, prevZ, currZ)
                }.onFailure {
                    anyNetworkFailure = true
                }
            }
        }

        LimitsWidget().updateAll(applicationContext)
        return if (anyNetworkFailure && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "agw_periodic_refresh"
        private const val IMMEDIATE_WORK_NAME = "agw_immediate_refresh"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
