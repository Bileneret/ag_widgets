package dev.agwidgets.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.Instant

data class ZCodeOAuthFlow(
    val flowId: String,
    val authorizeUrl: String,
    val pollToken: String,
    val pollIntervalSec: Long
)

sealed class ZCodeOAuthPollResult {
    data object Pending : ZCodeOAuthPollResult()
    data class Ready(val token: String) : ZCodeOAuthPollResult()
    data class Failed(val reason: String) : ZCodeOAuthPollResult()
}

object ZCodeClient {
    private const val ORIGIN = "https://zcode.z.ai"
    private const val APP_VERSION = "3.5.0"

    suspend fun startOAuthFlow(provider: String = "zai"): Result<ZCodeOAuthFlow> = withContext(Dispatchers.IO) {
        try {
            val pollToken = randomHex(32)
            val body = JSONObject().put("provider", provider).toString()
            val (code, json) = postJson(
                url = "$ORIGIN/api/v1/oauth/cli/init",
                body = body,
                headers = mapOf("Authorization" to "Bearer $pollToken")
            )
            if (code !in 200..299 || json == null || json.optInt("code", -1) != 0) {
                return@withContext Result.failure(IllegalStateException("Ошибка init OAuth (HTTP $code)"))
            }
            val data = json.optJSONObject("data")
                ?: return@withContext Result.failure(IllegalStateException("Пустой ответ init"))
            val flowId = data.optString("flow_id").trim()
            val authUrl = data.optString("authorize_url").trim()
            val interval = data.optLong("poll_interval_sec", 3L).coerceIn(2L, 10L)
            if (flowId.isEmpty() || authUrl.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Некорректные поля OAuth init"))
            }
            Result.success(ZCodeOAuthFlow(flowId, authUrl, pollToken, interval))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollOAuthFlow(flow: ZCodeOAuthFlow): ZCodeOAuthPollResult = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(flow.flowId, "UTF-8")
            val (code, json) = getJson(
                url = "$ORIGIN/api/v1/oauth/cli/poll/$encoded",
                headers = mapOf("Authorization" to "Bearer ${flow.pollToken}")
            )
            if (code in 400..499 && code != 408 && code != 429) {
                return@withContext ZCodeOAuthPollResult.Failed("Сессия входа истекла (HTTP $code)")
            }
            if (code !in 200..299 || json == null) {
                return@withContext ZCodeOAuthPollResult.Pending
            }
            val data = json.optJSONObject("data") ?: return@withContext ZCodeOAuthPollResult.Pending
            when (data.optString("status")) {
                "pending" -> ZCodeOAuthPollResult.Pending
                "failed" -> ZCodeOAuthPollResult.Failed("Вход отклонён")
                "ready" -> {
                    val token = data.optString("token").trim()
                    if (token.isNotEmpty()) ZCodeOAuthPollResult.Ready(token)
                    else ZCodeOAuthPollResult.Failed("Сервер не вернул токен")
                }
                else -> ZCodeOAuthPollResult.Pending
            }
        } catch (_: Exception) {
            ZCodeOAuthPollResult.Pending
        }
    }

    suspend fun fetchStatus(token: String, deviceMid: String): Result<ZCodeStatus> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Токен ZCode не задан"))
        }
        try {
            val headers = mapOf(
                "Authorization" to "Bearer ${token.trim()}",
                "X-Device-Mid" to deviceMid.trim(),
                "X-Client-Language" to "ru-RU",
                "X-ZCode-App-Version" to APP_VERSION
            )

            val (tCode, tJson) = getJson("$ORIGIN/api/v1/marketing/touch?seq=0", headers)
            val (bCode, bJson) = getJson("$ORIGIN/api/v1/zcode-plan/billing/balance", headers)

            val nowIso = Instant.now().toString()
            if (tCode == 401 || bCode == 401) {
                return@withContext Result.success(
                    ZCodeStatus(
                        running = true,
                        couponVisible = false,
                        method = "api",
                        authOk = false,
                        checkedAt = nowIso
                    )
                )
            }

            if (tCode !in 200..299 && bCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("zcode.z.ai недоступен (HTTP $tCode/$bCode)"))
            }

            var couponVisible = false
            var planId = ""
            var matched = ""
            if (tCode in 200..299 && tJson != null && tJson.optInt("code", -1) == 0) {
                val deliveries = tJson.optJSONObject("data")?.optJSONArray("deliveries") ?: JSONArray()
                outer@ for (i in 0 until deliveries.length()) {
                    val d = deliveries.optJSONObject(i) ?: continue
                    val pos = d.optString("resource_position")
                    val box = if (pos == "banner") d.optJSONObject("banner") else d.optJSONObject("popup")
                    val buttons = box?.optJSONArray("buttons") ?: continue
                    for (j in 0 until buttons.length()) {
                        val btn = buttons.optJSONObject(j) ?: continue
                        val action = btn.optJSONObject("action") ?: continue
                        if (action.optString("type") == "claim_zcode_plan") {
                            couponVisible = true
                            planId = action.optJSONObject("args")?.optString("plan_id").orEmpty()
                            val btnText = btn.optJSONObject("text")?.optString("content").orEmpty().trim()
                            val title = box.optJSONObject("title")?.optString("content").orEmpty().trim()
                            matched = (btnText.ifEmpty { title.ifEmpty { "plan:$planId" } }).take(100)
                            break@outer
                        }
                    }
                }
            }

            var remUnits: Long? = null
            var totUnits: Long? = null
            var endsAtIso: String? = null
            var planName = ""

            if (bCode in 200..299 && bJson != null && (bJson.optInt("code", -1) == 0 || bJson.optBoolean("success", false))) {
                val data = bJson.optJSONObject("data")
                if (data != null) {
                    val nowSec = data.optDouble("server_time", Instant.now().epochSecond.toDouble())
                    val plans = data.optJSONArray("plans") ?: JSONArray()
                    val activePlanIds = mutableSetOf<String>()
                    val activeUserPlanIds = mutableSetOf<String>()
                    var soonestEnd: Double? = null

                    for (i in 0 until plans.length()) {
                        val p = plans.optJSONObject(i) ?: continue
                        val st = p.optString("status").trim().lowercase()
                        var endSec = parseDoubleOrNull(p, "ends_at")
                        if (endSec != null && endSec > 1e12) endSec /= 1000.0
                        val expired = st == "expired" || (endSec != null && endSec > 0 && endSec <= nowSec)
                        if (!expired) {
                            p.optString("plan_id").takeIf { it.isNotEmpty() }?.let { activePlanIds.add(it) }
                            p.optString("user_plan_id").takeIf { it.isNotEmpty() }?.let { activeUserPlanIds.add(it) }
                            if (planName.isEmpty()) planName = p.optString("name").trim()
                            if (endSec != null && endSec > nowSec && (soonestEnd == null || endSec < soonestEnd)) {
                                soonestEnd = endSec
                            }
                        }
                    }

                    val balances = data.optJSONArray("balances") ?: JSONArray()
                    var totalSum = 0.0
                    var remSum = 0.0
                    var found = false
                    for (i in 0 until balances.length()) {
                        val b = balances.optJSONObject(i) ?: continue
                        val upid = b.optString("user_plan_id")
                        val pid = b.optString("plan_id")
                        if (activeUserPlanIds.isNotEmpty() || activePlanIds.isNotEmpty()) {
                            val belongs = (upid.isNotEmpty() && upid in activeUserPlanIds) ||
                                (pid.isNotEmpty() && pid in activePlanIds)
                            if ((upid.isNotEmpty() || pid.isNotEmpty()) && !belongs) continue
                        }

                        val tot = parseDoubleOrNull(b, "total_units")
                        var rem = parseDoubleOrNull(b, "available_units") ?: parseDoubleOrNull(b, "remaining_units")
                        if (rem == null && tot != null) {
                            val used = parseDoubleOrNull(b, "used_units") ?: 0.0
                            rem = (tot - used).coerceAtLeast(0.0)
                        }
                        if (tot != null || rem != null) {
                            found = true
                            totalSum += tot ?: 0.0
                            remSum += rem ?: 0.0
                            var pEnd = parseDoubleOrNull(b, "period_end")
                            if (pEnd != null && pEnd > 1e12) pEnd /= 1000.0
                            if (pEnd != null && pEnd > nowSec && (soonestEnd == null || pEnd < soonestEnd)) {
                                soonestEnd = pEnd
                            }
                        }
                    }
                    if (found) {
                        remUnits = remSum.toLong()
                        totUnits = if (totalSum > 0) totalSum.toLong() else null
                    }
                    endsAtIso = soonestEnd?.let { Instant.ofEpochSecond(it.toLong()).toString() }
                }
            }

            Result.success(
                ZCodeStatus(
                    running = true,
                    couponVisible = couponVisible,
                    matched = matched,
                    planId = planId,
                    method = "api",
                    authOk = true,
                    remainingUnits = remUnits,
                    totalUnits = totUnits,
                    endsAt = endsAtIso,
                    planName = planName,
                    checkedAt = nowIso
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDoubleOrNull(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val raw = obj.opt(key) ?: return null
        return when (raw) {
            is Number -> raw.toDouble().takeIf { !it.isNaN() }
            is String -> raw.trim().toDoubleOrNull()?.takeIf { !it.isNaN() }
            else -> null
        }
    }

    private fun randomHex(numBytes: Int): String {
        val bytes = ByteArray(numBytes)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getJson(url: String, headers: Map<String, String>): Pair<Int, JSONObject?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            code to json
        } finally {
            conn?.disconnect()
        }
    }

    private fun postJson(url: String, body: String, headers: Map<String, String>): Pair<Int, JSONObject?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            code to json
        } finally {
            conn?.disconnect()
        }
    }
}
