package dev.agwidgets.data

/**
 * ZCode is not part of AG Context. This stub keeps the shared app code
 * compilable without shipping the API client, OAuth flow, or token handling.
 */
object ZCodeClient {
    private const val UNAVAILABLE = "ZCode is not available in AG Context"

    suspend fun startOAuthFlow(provider: String = "zai"): Result<ZCodeOAuthFlow> =
        Result.failure(UnsupportedOperationException(UNAVAILABLE))

    suspend fun pollOAuthFlow(flow: ZCodeOAuthFlow): ZCodeOAuthPollResult =
        ZCodeOAuthPollResult.Failed(UNAVAILABLE)

    suspend fun fetchStatus(token: String, deviceMid: String): Result<ZCodeStatus> =
        Result.failure(UnsupportedOperationException(UNAVAILABLE))
}
