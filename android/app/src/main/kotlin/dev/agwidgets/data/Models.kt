package dev.agwidgets.data

import org.json.JSONArray
import org.json.JSONObject

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
    val matched: String = "",
    val planId: String = "",
    val method: String = "",
    val authOk: Boolean? = null,
    val networkError: Boolean = false,
    val remainingUnits: Long? = null,
    val totalUnits: Long? = null,
    val endsAt: String? = null,
    val planName: String = "",
    val checkedAt: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("running", running)
        put("coupon_visible", couponVisible)
        put("matched", matched)
        put("plan_id", planId)
        put("method", method)
        if (authOk != null) put("auth_ok", authOk)
        put("network_error", networkError)
        if (remainingUnits != null) put("remaining_units", remainingUnits)
        if (totalUnits != null) put("total_units", totalUnits)
        if (endsAt != null) put("ends_at", endsAt)
        put("plan_name", planName)
        if (checkedAt != null) put("checked_at", checkedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): ZCodeStatus = ZCodeStatus(
            running = obj.optBoolean("running", false),
            couponVisible = obj.optBoolean("coupon_visible", false),
            matched = obj.optString("matched", ""),
            planId = obj.optString("plan_id", ""),
            method = obj.optString("method", ""),
            authOk = if (obj.has("auth_ok") && !obj.isNull("auth_ok")) obj.optBoolean("auth_ok") else null,
            networkError = obj.optBoolean("network_error", false),
            remainingUnits = if (obj.has("remaining_units") && !obj.isNull("remaining_units")) {
                obj.optLong("remaining_units")
            } else {
                null
            },
            totalUnits = if (obj.has("total_units") && !obj.isNull("total_units")) {
                obj.optLong("total_units")
            } else {
                null
            },
            endsAt = obj.optString("ends_at").takeIf { it.isNotEmpty() && it != "null" },
            planName = obj.optString("plan_name", ""),
            checkedAt = obj.optString("checked_at").takeIf { it.isNotEmpty() && it != "null" },
        )
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
)

/** Full status document consumed by the app and the home-screen widget. */
data class AgStatus(
    val updatedAt: String?,
    val planTier: String?,
    val buckets: List<QuotaBucket>,
    val zcode: ZCodeStatus?,
    val contextSummary: ContextSummary?,
    val rawJson: String,
) {
    companion object {
        fun fromJson(raw: String): AgStatus? {
            return try {
                val obj = JSONObject(raw)
                val antigravity = obj.optJSONObject("antigravity")
                val planTier = antigravity?.optString("plan_tier")?.takeIf { it.isNotEmpty() && it != "null" }
                val bucketsArray = antigravity?.optJSONArray("buckets") ?: JSONArray()
                val buckets = buildList {
                    for (index in 0 until bucketsArray.length()) {
                        val bucket = bucketsArray.optJSONObject(index) ?: continue
                        add(
                            QuotaBucket(
                                id = bucket.optString("id", "unknown"),
                                kind = bucket.optString("kind", "other"),
                                remainingFraction = if (bucket.isNull("remaining_fraction")) {
                                    null
                                } else {
                                    bucket.optDouble("remaining_fraction")
                                },
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
                        tokens = if (it.isNull("tokens")) null else it.optInt("tokens"),
                        window = if (it.isNull("window")) null else it.optInt("window").takeIf { value -> value > 0 },
                        pct = if (it.isNull("pct")) null else it.optDouble("pct").takeIf { value -> !value.isNaN() },
                        level = it.optString("level").takeIf { value -> value.isNotEmpty() && value != "null" },
                        warnTokens = if (it.isNull("warn_tokens")) null else it.optInt("warn_tokens"),
                        riskTokens = if (it.isNull("risk_tokens")) null else it.optInt("risk_tokens"),
                        updatedAt = it.optString("updated_at").takeIf { value -> value.isNotEmpty() && value != "null" },
                    )
                }

                AgStatus(
                    updatedAt = obj.optString("updated_at").takeIf { it.isNotEmpty() && it != "null" },
                    planTier = planTier,
                    buckets = buckets,
                    zcode = obj.optJSONObject("zcode")?.let { ZCodeStatus.fromJson(it) },
                    contextSummary = context,
                    rawJson = raw,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
