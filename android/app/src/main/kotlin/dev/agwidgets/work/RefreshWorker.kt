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
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.NtfyClient
import dev.agwidgets.data.StatusStore
import dev.agwidgets.data.ZCodeClient
import dev.agwidgets.data.ZCodeStatus
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
        val topic = StatusStore.getTopic(applicationContext)
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
        val zcodeToken = StatusStore.getZCodeToken(applicationContext)
        if (zcodeToken.isNotBlank()) {
            val deviceMid = StatusStore.getOrCreateDeviceMid(applicationContext)
            val seq = StatusStore.nextZCodeSeq(applicationContext)
            val previous = StatusStore.getDirectZCode(applicationContext)
            val zRes = ZCodeClient.fetchStatus(zcodeToken, deviceMid, seq, previous)
            zRes.onSuccess { zStatus ->
                val (prevZ, currZ) = StatusStore.saveDirectZCode(applicationContext, zStatus)
                Notifier.checkZCodeCouponTransition(applicationContext, prevZ, currZ)
                if (zStatus.networkError || zStatus.errorKind != null) {
                    val reason = when (zStatus.errorKind) {
                        "auth_expired" -> "ZCode: токен истёк"
                        "rate_limited" -> "ZCode: лимит запросов (429)"
                        "server_error" -> "ZCode: ошибка сервера"
                        "api_changed" -> "ZCode: схема API изменилась"
                        "parse_failed" -> "ZCode: не удалось разобрать ответ"
                        else -> "ZCode: нет связи"
                    }
                    StatusStore.saveError(applicationContext, reason)
                    if (zStatus.networkError || zStatus.errorKind in RETRYABLE_ZCODE_ERRORS) {
                        anyNetworkFailure = true
                    }
                }
            }.onFailure { err ->
                val nowIso = java.time.Instant.now().toString()
                val fallback = previous ?: ZCodeStatus(running = true, couponVisible = false, method = "api")
                val failed = fallback.copy(
                    running = true,
                    method = "api",
                    authOk = null,
                    networkError = true,
                    couponVisible = false,
                    couponState = ZCodeStatus.COUPON_UNKNOWN,
                    errorKind = "network",
                    checkedAt = nowIso,
                )
                StatusStore.saveDirectZCode(applicationContext, failed)
                StatusStore.saveError(applicationContext, err.message ?: "Ошибка сети ZCode")
                anyNetworkFailure = true
            }
        }

        LimitsWidget().updateAll(applicationContext)
        return if (anyNetworkFailure && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "agw_periodic_refresh"
        private const val IMMEDIATE_WORK_NAME = "agw_immediate_refresh"
        private val RETRYABLE_ZCODE_ERRORS = setOf("network", "rate_limited", "server_error")

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
                ExistingWorkPolicy.KEEP,
                req
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
