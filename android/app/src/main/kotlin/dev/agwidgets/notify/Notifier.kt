package dev.agwidgets.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.agwidgets.BuildConfig
import dev.agwidgets.MainActivity
import dev.agwidgets.R
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.QuotaBucket
import dev.agwidgets.data.StatusStore
import dev.agwidgets.data.ZCodeStatus
import dev.agwidgets.ui.parseInstant
import kotlin.math.roundToInt

object Notifier {
    private const val CHANNEL_ID = "agw_limits"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Лимиты",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = if (BuildConfig.ZCODE_ENABLED) {
                    "Уведомления об обновлении квот Antigravity и ваучерах ZCode"
                } else {
                    "Уведомления об обновлении квот Antigravity"
                }
            }
            mgr.createNotificationChannel(channel)
        }
    }

    fun checkAndNotify(context: Context, prev: AgStatus?, curr: AgStatus?) {
        if (prev == null || curr == null) return
        if (!canPostNotifications(context)) return
        ensureChannel(context)

        for (currBucket in curr.buckets) {
            val prevBucket = prev.buckets.firstOrNull { it.id == currBucket.id } ?: continue
            if (shouldNotifyReset(prevBucket, currBucket)) {
                val currFrac = currBucket.remainingFraction
                // Key on the time before the reset so a future timestamp is never marked as notified.
                val resetKey = validResetKey(prevBucket.resetTime)
                    ?: validResetKey(currBucket.resetTime)
                    ?: "changed"
                if (StatusStore.isResetNotified(context, currBucket.id, resetKey)) continue
                StatusStore.markResetNotified(context, currBucket.id, resetKey)
                val pct = currFrac?.takeIf { it.isFinite() }?.let { "Доступно: ${(it * 100).roundToInt()}%" }
                    ?: "Лимит сброшен"
                sendNotification(
                    context,
                    notifIdForBucket(currBucket.id),
                    "Квота ${bucketDisplayName(currBucket.id, currBucket.kind)} обновилась",
                    pct
                )
            }
        }

        if (BuildConfig.ZCODE_ENABLED) checkZCodeCouponTransition(context, prev.zcode, curr.zcode)
    }

    fun checkZCodeCouponTransition(context: Context, prev: ZCodeStatus?, curr: ZCodeStatus?) {
        if (!BuildConfig.ZCODE_ENABLED) return
        if (curr == null) return
        if (curr.couponState != ZCodeStatus.COUPON_VISIBLE || !curr.couponVisible) return
        if (curr.networkError || curr.authOk == false) return
        if (!canPostNotifications(context)) return
        ensureChannel(context)

        val key = "${curr.planId}|${curr.matched}"
        if (StatusStore.getLastNotifiedCoupon(context) == key) return
        StatusStore.markCouponNotified(context, key)
        val detail = curr.matched.takeIf { it.isNotBlank() }
            ?: "Откройте ZCode, чтобы забрать 300–500M токенов"
        sendNotification(context, 103, "В ZCode доступен ваучер!", detail)
    }

    fun notifyScheduledReset(context: Context, bucketId: String, kind: String, resetTime: String) {
        if (!canPostNotifications(context)) return
        if (StatusStore.isResetNotified(context, bucketId, resetTime)) return
        ensureChannel(context)
        StatusStore.markResetNotified(context, bucketId, resetTime)
        val label = bucketDisplayName(bucketId, kind)
        sendNotification(
            context,
            notifIdForBucket(bucketId),
            "Квота $label обновилась",
            "Наступило запланированное время сброса лимита"
        )
    }

    /**
     * Pure quota-reset predicate: fires only when the bucket was actually consumed
     * (`< 99%`) and then either jumped back up (`>= +20%`, including resets where
     * `agy /usage` switches `reset_time` back to the epoch-zero sentinel) or its
     * real reset window moved forward while remaining quota increased.
     */
    internal fun shouldNotifyReset(prev: QuotaBucket, curr: QuotaBucket): Boolean {
        val prevFrac = prev.remainingFraction
        if (prevFrac == null || !prevFrac.isFinite() || prevFrac >= 0.99) return false
        val currFrac = curr.remainingFraction?.takeIf { it.isFinite() }
        val grew = currFrac != null && (currFrac - prevFrac) >= 0.20
        if (grew) return true

        val prevReset = parseInstant(prev.resetTime)?.takeIf { it.epochSecond > 0L }
        val currReset = parseInstant(curr.resetTime)?.takeIf { it.epochSecond > 0L }
        val resetAdvanced = prevReset != null && currReset != null && currReset.isAfter(prevReset)
        val quotaDidNotDrop = currFrac == null || currFrac > prevFrac + 0.01
        return resetAdvanced && quotaDidNotDrop
    }

    private fun validResetKey(iso: String?): String? {
        val instant = parseInstant(iso) ?: return null
        return if (instant.epochSecond > 0L) iso else null
    }

    private fun bucketDisplayName(bucketId: String, kind: String): String {
        val low = bucketId.lowercase()
        val group = when {
            low.startsWith("gemini-") -> "Gemini "
            low.startsWith("3p-") -> "Claude/GPT "
            else -> ""
        }
        val window = when (kind) {
            "5h" -> "(5h)"
            "weekly" -> "(неделя)"
            else -> ""
        }
        return "$group$window".trim().ifEmpty { bucketId }
    }

    private fun notifIdForBucket(bucketId: String): Int =
        200 + (bucketId.hashCode() and 0x7FFF) % 500

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun sendNotification(context: Context, id: Int, title: String, text: String) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(context, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, intent, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF22D3EE.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
