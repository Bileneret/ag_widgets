package dev.agwidgets.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads a finite double from [key].
 *
 * Accepts JSON numbers and numeric strings; rejects booleans, objects, arrays,
 * garbage strings and non-finite values (NaN / Infinity).
 */
internal fun JSONObject.optFiniteDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }
}

/**
 * Reads an integral long from [key].
 *
 * Accepts JSON numbers (never booleans) and strings that contain only an
 * integer, e.g. `"123"`.
 */
internal fun JSONObject.optStrictLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }
}

/** Reads an int that fits into [Int] using the same strict rules as [optStrictLong]. */
internal fun JSONObject.optStrictInt(key: String): Int? {
    val value = optStrictLong(key) ?: return null
    if (value < Int.MIN_VALUE.toLong() || value > Int.MAX_VALUE.toLong()) return null
    return value.toInt()
}

/**
 * Best-effort warning logging.
 *
 * `android.util.Log` is not mocked in local JVM tests, so every call is wrapped
 * in [runCatching] and logging failures are silently ignored.
 */
internal fun safeWarn(tag: String, message: String, error: Throwable? = null) {
    runCatching {
        if (error != null) android.util.Log.w(tag, message, error) else android.util.Log.w(tag, message)
    }
}

/** One Antigravity quota bucket, e.g. `gemini-5h` or `3p-weekly`. */
data class QuotaBucket(
    val id: String,
    val kind: String, // "5h" | "weekly" | "other"
    val remainingFraction: Double?,
    val resetTime: String?,
)

/** ZCode coupon and balance state returned by `zcode.z.ai`. */
data class ZCodeStatus(
    val running: Boolean,
    val couponVisible: Boolean,
    val couponState: String = if (couponVisible) COUPON_VISIBLE else COUPON_ABSENT,
    val matched: String = "",
    val planId: String = "",
    val method: String = "",
    val authOk: Boolean? = null,
    val networkError: Boolean = false,
    val errorKind: String? = null,
    val remainingUnits: Long? = null,
    val totalUnits: Long? = null,
    val endsAt: String? = null,
    val planName: String = "",
    val checkedAt: String? = null,
) {
    val isCouponVisible: Boolean get() = couponState == COUPON_VISIBLE
    val isCouponUnknown: Boolean get() = couponState == COUPON_UNKNOWN

    fun toJson(): JSONObject = JSONObject().apply {
        put("running", running)
        put("coupon_state", couponState)
        // "unknown" is reported as JSON null so older readers do not treat it as "no coupon".
        if (couponState == COUPON_UNKNOWN) {
            put("coupon_visible", JSONObject.NULL)
        } else {
            put("coupon_visible", couponVisible)
        }
        put("matched", matched)
        put("plan_id", planId)
        put("method", method)
        if (authOk != null) put("auth_ok", authOk)
        put("network_error", networkError)
        if (errorKind != null) put("error_kind", errorKind)
        if (remainingUnits != null) put("remaining_units", remainingUnits)
        if (totalUnits != null) put("total_units", totalUnits)
        if (endsAt != null) put("ends_at", endsAt)
        put("plan_name", planName)
        if (checkedAt != null) put("checked_at", checkedAt)
    }

    companion object {
        const val COUPON_VISIBLE = "visible"
        const val COUPON_ABSENT = "absent"
        const val COUPON_UNKNOWN = "unknown"

        fun fromJson(obj: JSONObject): ZCodeStatus {
            val networkError = obj.optBoolean("network_error", false)
            val errorKind = obj.optString("error_kind").takeIf { it.isNotEmpty() && it != "null" }
            val couponState = parseCouponState(obj, networkError, errorKind)
            return ZCodeStatus(
                running = obj.optBoolean("running", false),
                couponVisible = couponState == COUPON_VISIBLE,
                couponState = couponState,
                matched = obj.optString("matched", ""),
                planId = obj.optString("plan_id", ""),
                method = obj.optString("method", ""),
                authOk = if (obj.has("auth_ok") && !obj.isNull("auth_ok")) obj.optBoolean("auth_ok") else null,
                networkError = networkError,
                errorKind = errorKind,
                remainingUnits = obj.optStrictLong("remaining_units"),
                totalUnits = obj.optStrictLong("total_units"),
                endsAt = obj.optString("ends_at").takeIf { it.isNotEmpty() && it != "null" },
                planName = obj.optString("plan_name", ""),
                checkedAt = obj.optString("checked_at").takeIf { it.isNotEmpty() && it != "null" },
            )
        }

        /**
         * Resolves the coupon state without ever turning an unknown state into
         * "no coupon": an explicit state wins, then the legacy boolean, and only
         * then the network/error heuristics.
         */
        private fun parseCouponState(obj: JSONObject, networkError: Boolean, errorKind: String?): String {
            val rawState = obj.optString("coupon_state").trim()
            if (rawState == COUPON_VISIBLE || rawState == COUPON_ABSENT || rawState == COUPON_UNKNOWN) {
                return rawState
            }
            val legacyVisible: Boolean? =
                if (obj.has("coupon_visible") && !obj.isNull("coupon_visible")) {
                    when (val value = obj.opt("coupon_visible")) {
                        is Boolean -> value
                        is String -> when (value.trim().lowercase()) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                        else -> null
                    }
                } else {
                    null
                }
            if (legacyVisible != null) return if (legacyVisible) COUPON_VISIBLE else COUPON_ABSENT
            if (networkError || !errorKind.isNullOrEmpty()) return COUPON_UNKNOWN
            return COUPON_ABSENT
        }
    }
}

/** Context summary published by the desktop collector. */
data class ContextSummary(
    val conversationId: String? = null,
    val active: Boolean = false,
    val model: String?,
    val tokens: Int?,
    val window: Int? = null,
    val pct: Double? = null,
    val level: String? = null,
    val warnTokens: Int? = null,
    val riskTokens: Int? = null,
    val updatedAt: String? = null,
    val yellowPct: Double? = null,
    val redPct: Double? = null,
    val windowSource: String? = null,
)

/** Full status document consumed by the app and the home-screen widget. */
data class AgStatus(
    val updatedAt: String?,
    val planTier: String?,
    val buckets: List<QuotaBucket>,
    val zcode: ZCodeStatus?,
    val contextSummary: ContextSummary?,
    val rawJson: String,
    val pcUpdatedAt: String? = null,
    val antigravityError: String? = null,
) {
    companion object {
        fun fromJson(raw: String): AgStatus? {
            return try {
                val obj = JSONObject(raw)
                // A random or empty message in the ntfy topic must not wipe the cached status.
                if (!hasStatusMarker(obj)) {
                    null
                } else {
                    parseStatus(obj, raw)
                }
            } catch (e: Exception) {
                safeWarn("AgStatus", "Failed to parse status JSON", e)
                null
            }
        }

        private fun hasStatusMarker(obj: JSONObject): Boolean =
            obj.has("updated_at") || obj.has("antigravity") || obj.has("zcode") || obj.has("context_summary")

        private fun parseStatus(obj: JSONObject, raw: String): AgStatus {
            val updatedAt = obj.optString("updated_at").takeIf { it.isNotEmpty() && it != "null" }
            val pcUpdatedAt = obj.optString("pc_updated_at").takeIf { it.isNotEmpty() && it != "null" }
                ?: updatedAt
            val antigravity = obj.optJSONObject("antigravity")
            val planTier = antigravity?.optString("plan_tier")?.takeIf { it.isNotEmpty() && it != "null" }
            val antigravityError = antigravity?.optString("error")?.takeIf { it.isNotEmpty() && it != "null" }
            val bucketsArray = antigravity?.optJSONArray("buckets") ?: JSONArray()
            val buckets = buildList {
                for (index in 0 until bucketsArray.length()) {
                    val bucket = bucketsArray.optJSONObject(index) ?: continue
                    add(
                        QuotaBucket(
                            id = bucket.optString("id", "unknown"),
                            kind = bucket.optString("kind", "other"),
                            remainingFraction = bucket.optFiniteDouble("remaining_fraction"),
                            resetTime = bucket.optString("reset_time").takeIf { it.isNotEmpty() && it != "null" },
                        ),
                    )
                }
            }

            val context = obj.optJSONObject("context_summary")?.let {
                ContextSummary(
                    conversationId = it.optString("conversation_id").takeIf { value ->
                        value.isNotEmpty() && value != "null"
                    },
                    active = it.optBoolean("active", false),
                    model = it.optString("model").takeIf { value -> value.isNotEmpty() && value != "null" },
                    tokens = it.optStrictInt("tokens"),
                    window = it.optStrictInt("window")?.takeIf { value -> value > 0 },
                    pct = it.optFiniteDouble("pct"),
                    level = it.optString("level").takeIf { value -> value.isNotEmpty() && value != "null" },
                    warnTokens = it.optStrictInt("warn_tokens"),
                    riskTokens = it.optStrictInt("risk_tokens"),
                    updatedAt = it.optString("updated_at").takeIf { value -> value.isNotEmpty() && value != "null" },
                    yellowPct = it.optFiniteDouble("yellow_pct"),
                    redPct = it.optFiniteDouble("red_pct"),
                    windowSource = it.optString("window_source").takeIf { value ->
                        value.isNotEmpty() && value != "null"
                    },
                )
            }

            return AgStatus(
                updatedAt = updatedAt,
                planTier = planTier,
                buckets = buckets,
                zcode = obj.optJSONObject("zcode")?.let { ZCodeStatus.fromJson(it) },
                contextSummary = context,
                rawJson = raw,
                pcUpdatedAt = pcUpdatedAt,
                antigravityError = antigravityError,
            )
        }
    }
}
