package dev.agwidgets.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object NtfyClient {
    suspend fun fetchLatestMessage(topic: String): Result<String> = withContext(Dispatchers.IO) {
        if (topic.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Тема ntfy не задана"))
        }
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://ntfy.sh/${topic.trim()}/json?poll=1&since=latest")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/x-ndjson, application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code"))
            }
            val lines = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
            if (lines.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("В теме пока нет сообщений"))
            }
            // Pick the last actual message event
            val messageLine = lines.asReversed().firstOrNull { line ->
                try {
                    val obj = JSONObject(line)
                    obj.optString("event") == "message" && obj.has("message")
                } catch (_: Exception) {
                    false
                }
            } ?: return@withContext Result.failure(IllegalStateException("Нет событий message"))

            val payload = JSONObject(messageLine).getString("message")
            Result.success(payload)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}
