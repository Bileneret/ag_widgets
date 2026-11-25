package dev.agwidgets.data

/** OAuth flow returned by `zcode.z.ai/api/v1/oauth/cli/init`. */
data class ZCodeOAuthFlow(
    val flowId: String,
    val authorizeUrl: String,
    val pollToken: String,
    val pollIntervalSec: Long,
)

/** Result of polling an OAuth flow. */
sealed class ZCodeOAuthPollResult {
    data object Pending : ZCodeOAuthPollResult()
    data class Ready(val token: String) : ZCodeOAuthPollResult()
    data class Failed(val reason: String) : ZCodeOAuthPollResult()
}
