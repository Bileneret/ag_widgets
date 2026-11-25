package dev.agwidgets.data

import android.content.Context
import android.content.res.Configuration
import dev.agwidgets.BuildConfig
import org.json.JSONObject
import java.util.UUID

/**
 * Single source of truth for the Android app.
 *
 * PC data arrives from ntfy, ZCode data is fetched directly by the phone, and
 * [getMergedStatus] combines both so the widget renders even when the PC is off.
 */
object StatusStore {
    private const val PREFS_NAME = "agw"
    private const val KEY_TOPIC = "ntfy_topic"
    private const val KEY_LAST_JSON = "last_json"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_ZCODE_TOKEN = "zcode_token"
    private const val KEY_DEVICE_MID = "device_mid"
    private const val KEY_DIRECT_ZCODE = "direct_zcode_json"
    private const val KEY_NOTIFIED_RESETS = "notified_resets"
    private const val KEY_THEME_MODE = "theme_mode"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTopic(context: Context): String {
        val saved = prefs(context).getString(KEY_TOPIC, null)?.trim()
        return if (!saved.isNullOrEmpty()) saved else BuildConfig.AGW_TOPIC
    }

    fun setTopic(context: Context, topic: String) {
        prefs(context).edit().putString(KEY_TOPIC, topic.trim()).apply()
    }

    fun getZCodeToken(context: Context): String =
        prefs(context).getString(KEY_ZCODE_TOKEN, "")?.trim().orEmpty()

    fun setZCodeToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_ZCODE_TOKEN, token.trim()).apply()
    }

    fun clearZCodeToken(context: Context) {
        prefs(context).edit()
            .remove(KEY_ZCODE_TOKEN)
            .remove(KEY_DIRECT_ZCODE)
            .apply()
    }

    fun getOrCreateDeviceMid(context: Context): String {
        val preferences = prefs(context)
        val existing = preferences.getString(KEY_DEVICE_MID, null)?.trim()
        if (!existing.isNullOrEmpty()) return existing
        val created = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_DEVICE_MID, created).apply()
        return created
    }

    fun getLastStatus(context: Context): AgStatus? {
        val raw = prefs(context).getString(KEY_LAST_JSON, null) ?: return null
        return AgStatus.fromJson(raw)
    }

    fun getDirectZCode(context: Context): ZCodeStatus? {
        val raw = prefs(context).getString(KEY_DIRECT_ZCODE, null) ?: return null
        return try {
            ZCodeStatus.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    /** PC status with the fresher phone-side ZCode check taking precedence. */
    fun getMergedStatus(context: Context): AgStatus? =
        mergeStatus(getLastStatus(context), getDirectZCode(context))

    fun saveNewStatus(context: Context, rawJson: String): Pair<AgStatus?, AgStatus?> {
        val preferences = prefs(context)
        val previousRaw = preferences.getString(KEY_LAST_JSON, null)
        preferences.edit()
            .putString(KEY_LAST_JSON, rawJson)
            .remove(KEY_LAST_ERROR)
            .apply()
        return previousRaw?.let { AgStatus.fromJson(it) } to AgStatus.fromJson(rawJson)
    }

    fun saveDirectZCode(context: Context, status: ZCodeStatus): Pair<ZCodeStatus?, ZCodeStatus> {
        val preferences = prefs(context)
        val previousRaw = preferences.getString(KEY_DIRECT_ZCODE, null)
        val previous = previousRaw?.let {
            try {
                ZCodeStatus.fromJson(JSONObject(it))
            } catch (_: Exception) {
                null
            }
        }
        preferences.edit()
            .putString(KEY_DIRECT_ZCODE, status.toJson().toString())
            .apply()
        return previous to status
    }

    fun isResetNotified(context: Context, bucketId: String, resetTime: String): Boolean {
        val key = "$bucketId|$resetTime"
        return key in (prefs(context).getStringSet(KEY_NOTIFIED_RESETS, emptySet()) ?: emptySet())
    }

    fun markResetNotified(context: Context, bucketId: String, resetTime: String) {
        val key = "$bucketId|$resetTime"
        val preferences = prefs(context)
        val notified = preferences.getStringSet(KEY_NOTIFIED_RESETS, emptySet())?.toMutableSet() ?: mutableSetOf()
        notified.add(key)
        val trimmed = if (notified.size > 40) notified.toList().takeLast(30).toSet() else notified
        preferences.edit().putStringSet(KEY_NOTIFIED_RESETS, trimmed).apply()
    }

    fun getThemeMode(context: Context): String =
        prefs(context).getString(KEY_THEME_MODE, "system")?.lowercase() ?: "system"

    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.lowercase()).apply()
    }

    fun isDarkTheme(context: Context): Boolean = when (getThemeMode(context)) {
        "light" -> false
        "dark" -> true
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun saveError(context: Context, error: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, error).apply()
    }

    fun getLastError(context: Context): String? =
        prefs(context).getString(KEY_LAST_ERROR, null)
}

/** Pure merge used by [StatusStore.getMergedStatus] and by unit tests. */
internal fun mergeStatus(pc: AgStatus?, directZCode: ZCodeStatus?): AgStatus? {
    if (pc == null && directZCode == null) return null
    if (pc == null) {
        return AgStatus(
            updatedAt = directZCode?.checkedAt,
            planTier = null,
            buckets = emptyList(),
            zcode = directZCode,
            contextSummary = null,
            rawJson = directZCode?.toJson()?.toString(2) ?: "{}",
        )
    }
    return if (directZCode != null) {
        pc.copy(updatedAt = directZCode.checkedAt ?: pc.updatedAt, zcode = directZCode)
    } else {
        pc
    }
}
