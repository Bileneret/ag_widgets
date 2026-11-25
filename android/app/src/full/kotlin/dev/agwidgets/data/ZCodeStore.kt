package dev.agwidgets.data

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/** ZCode-only storage: token, device id, and the last direct API result. */
object ZCodeStore {
    private const val PREFS_NAME = "agw"
    private const val KEY_TOKEN = "zcode_token"
    private const val KEY_DEVICE_MID = "device_mid"
    private const val KEY_DIRECT = "direct_zcode_json"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getToken(context: Context): String =
        prefs(context).getString(KEY_TOKEN, "")?.trim().orEmpty()

    fun setToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun clearToken(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_DIRECT)
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

    fun getDirect(context: Context): ZCodeStatus? {
        val raw = prefs(context).getString(KEY_DIRECT, null) ?: return null
        return try {
            ZCodeStatus.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun saveDirect(context: Context, status: ZCodeStatus): Pair<ZCodeStatus?, ZCodeStatus> {
        val preferences = prefs(context)
        val previousRaw = preferences.getString(KEY_DIRECT, null)
        val previous = previousRaw?.let {
            try {
                ZCodeStatus.fromJson(JSONObject(it))
            } catch (_: Exception) {
                null
            }
        }
        preferences.edit().putString(KEY_DIRECT, status.toJson().toString()).apply()
        return previous to status
    }
}
