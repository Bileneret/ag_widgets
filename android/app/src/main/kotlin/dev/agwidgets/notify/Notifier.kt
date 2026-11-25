package dev.agwidgets.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.agwidgets.R
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.StatusStore
import dev.agwidgets.data.ZCodeStatus

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
                description = "Уведомления об обновлении квот Antigravity и ваучерах ZCode"
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
            val fracIncreased = (currBucket.remainingFraction != null && prevBucket.remainingFraction != null &&
                (currBucket.remainingFraction - prevBucket.remainingFraction) >= 0.20)
            val resetChanged = currBucket.resetTime != null && prevBucket.resetTime != null &&
                currBucket.resetTime != prevBucket.resetTime

            if (fracIncreased || resetChanged) {
                val resetKey = prevBucket.resetTime ?: currBucket.resetTime ?: "changed"
                if (!StatusStore.isResetNotified(context, currBucket.id, resetKey)) {
                    StatusStore.markResetNotified(context, currBucket.id, resetKey)
                    val pct = currBucket.remainingFraction?.let { "Доступно: ${(it * 100).toInt()}%" } ?: "Лимит сброшен"
                    sendNotification(
                        context,
                        notifIdForBucket(currBucket.id),
                        "Квота ${bucketDisplayName(currBucket.id, currBucket.kind)} обновилась",
                        pct
                    )
                }
            }
        }

        checkZCodeCouponTransition(context, prev.zcode, curr.zcode)
    }

    fun checkZCodeCouponTransition(context: Context, prev: ZCodeStatus?, curr: ZCodeStatus?) {
        if (curr == null || !curr.couponVisible) return
        if (!canPostNotifications(context)) return
        ensureChannel(context)

        val wasCoupon = prev?.couponVisible == true
        val newPlan = curr.planId.isNotEmpty() && curr.planId != prev?.planId
        if (!wasCoupon || newPlan) {
            val detail = curr.matched.takeIf { it.isNotBlank() } ?: "Откройте ZCode, чтобы забрать 300–500M токенов"
            sendNotification(context, 103, "В ZCode доступен ваучер!", detail)
        }
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF22D3EE.toInt())
            .setContentTitle(title)
            .setContentText(text)
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
