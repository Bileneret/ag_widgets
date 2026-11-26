@file:Suppress("UNUSED_PARAMETER")

package dev.agwidgets.data

import android.content.Context

/** AG Context has no ZCode state, token, or device id. */
object ZCodeStore {
    fun getToken(context: Context): String = ""

    fun setToken(context: Context, token: String) = Unit

    fun clearToken(context: Context) = Unit

    fun getOrCreateDeviceMid(context: Context): String = ""

    fun getDirect(context: Context): ZCodeStatus? = null

    fun saveDirect(context: Context, status: ZCodeStatus): Pair<ZCodeStatus?, ZCodeStatus> =
        null to status

    fun nextSeq(context: Context): Int = 0

    fun shouldBackoffRateLimit(context: Context, cooldownSec: Long = 300L): Boolean = false
}
