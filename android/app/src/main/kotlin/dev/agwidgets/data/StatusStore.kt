package dev.agwidgets.data

import android.content.Context
import android.content.res.Configuration
import dev.agwidgets.BuildConfig

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
    private const val KEY_NOTIFIED_RESETS = "notified_resets"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_MODE = "mode"

    const val MODE_UNSET = "unset"
    const val MODE_PC = "pc"
    const val MODE_ZCODE = "zcode"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTopic(context: Context): String {
        val saved = prefs(context).getString(KEY_TOPIC, null)?.trim()
        return if (!saved.isNullOrEmpty()) saved else BuildConfig.AGW_TOPIC
    }

    fun setTopic(context: Context, topic: String) {
        prefs(context).edit().putString(KEY_TOPIC, topic.trim()).apply()
    }

    /**
     * How the app is used: with a PC (Antigravity + optional ZCode) or ZCode only.
     * Existing installs are inferred from saved data so they skip onboarding.
     */
    fun getMode(context: Context): String {
        val saved = prefs(context).getString(KEY_MODE, null)?.lowercase()
        if (saved == MODE_PC || saved == MODE_ZCODE || saved == MODE_UNSET) return saved
        if (getTopic(context).isNotBlank()) return MODE_PC
        if (BuildConfig.ZCODE_ENABLED && ZCodeStore.getToken(context).isNotBlank()) return MODE_ZCODE
        return MODE_UNSET
    }

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_MODE, mode.lowercase()).apply()
    }

    fun getLastStatus(context: Context): AgStatus? {
        val raw = prefs(context).getString(KEY_LAST_JSON, null) ?: return null
        return AgStatus.fromJson(raw)
    }

    /** PC status with the fresher phone-side ZCode check taking precedence. */
    fun getMergedStatus(context: Context): AgStatus? =
        mergeStatus(getLastStatus(context), ZCodeStore.getDirect(context))

    fun saveNewStatus(context: Context, rawJson: String): Pair<AgStatus?, AgStatus?> {
        val preferences = prefs(context)
        val previousRaw = preferences.getString(KEY_LAST_JSON, null)
        preferences.edit()
            .putString(KEY_LAST_JSON, rawJson)
            .remove(KEY_LAST_ERROR)
            .apply()
        return previousRaw?.let { AgStatus.fromJson(it) } to AgStatus.fromJson(rawJson)
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
