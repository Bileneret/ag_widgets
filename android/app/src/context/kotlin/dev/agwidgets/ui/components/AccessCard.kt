package dev.agwidgets.ui.components

import androidx.compose.runtime.Composable
import dev.agwidgets.data.ZCodeStatus

/**
 * AG Context has no ZCode access card. The shared screen still references this
 * composable, so the lite flavor provides a no-op implementation.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AccessCard(
    zcode: ZCodeStatus?,
    token: String,
    onTokenChange: (String) -> Unit,
    isPending: Boolean,
    message: String,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    // Intentionally empty.
}
