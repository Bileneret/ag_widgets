package dev.agwidgets.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.UUID

/** ZCode-only storage: encrypted token, device id, direct cache, and poll sequence. */
object ZCodeStore {
    private const val PREFS_NAME = "agw"
    private const val KEY_TOKEN = "zcode_token"
    private const val KEY_DEVICE_MID = "device_mid"
    private const val KEY_DIRECT = "direct_zcode_json"
    private const val KEY_SEQ = "zcode_seq"

    /** Injectable for local JVM tests; the real cipher needs an Android runtime. */
    internal var cipher: SecretCipher = KeystoreSecretCipher()

    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getToken(context: Context): String = synchronized(lock) {
        val preferences = prefs(context)
        val stored = preferences.getString(KEY_TOKEN, null) ?: return@synchronized ""
        val plaintext = cipher.decrypt(stored) ?: return@synchronized ""
        val trimmed = plaintext.trim()
        if (trimmed.isNotEmpty() && !cipher.isEncrypted(stored)) {
            runCatching {
                preferences.edit().putString(KEY_TOKEN, cipher.encrypt(trimmed)).apply()
            }.onFailure { safeWarn("ZCodeStore", "Failed to migrate token to encrypted storage", it) }
        }
        trimmed
    }

    fun setToken(context: Context, token: String) {
        val trimmed = token.trim()
        synchronized(lock) {
            val edit = prefs(context).edit()
            if (trimmed.isEmpty()) edit.remove(KEY_TOKEN) else edit.putString(KEY_TOKEN, cipher.encrypt(trimmed))
            edit.apply()
        }
    }

    fun clearToken(context: Context) {
        synchronized(lock) {
            prefs(context).edit()
                .remove(KEY_TOKEN)
                .remove(KEY_DIRECT)
                .apply()
        }
    }

    fun getOrCreateDeviceMid(context: Context): String = synchronized(lock) {
        val preferences = prefs(context)
        val existing = preferences.getString(KEY_DEVICE_MID, null)?.trim()
        if (!existing.isNullOrEmpty()) return@synchronized existing
        val created = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_DEVICE_MID, created).apply()
        created
    }

    fun getDirect(context: Context): ZCodeStatus? {
        val raw = prefs(context).getString(KEY_DIRECT, null) ?: return null
        return try {
            ZCodeStatus.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun saveDirect(context: Context, status: ZCodeStatus): Pair<ZCodeStatus?, ZCodeStatus> =
        synchronized(lock) {
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
            previous to status
        }

    fun nextSeq(context: Context): Int = synchronized(lock) {
        val preferences = prefs(context)
        val current = preferences.getInt(KEY_SEQ, 0)
        preferences.edit().putInt(KEY_SEQ, (current + 1) and 0x7FFFFFFF).apply()
        current
    }
}
