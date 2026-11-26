package dev.agwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import dev.agwidgets.data.StatusStore
import dev.agwidgets.data.ZCodeStore
import dev.agwidgets.notify.Notifier
import dev.agwidgets.ui.AgWidgetsScreen
import dev.agwidgets.ui.theme.AGWidgetsTheme
import dev.agwidgets.widget.LimitsWidget
import dev.agwidgets.work.RefreshWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)

        // No configured channel means no network work worth scheduling.
        val hasChannel = StatusStore.getTopic(applicationContext).isNotBlank() ||
            (BuildConfig.ZCODE_ENABLED && ZCodeStore.getToken(applicationContext).isNotBlank())
        if (hasChannel) RefreshWorker.schedulePeriodic(applicationContext)

        setContent {
            val appContext = applicationContext
            var themeMode by remember { mutableStateOf(StatusStore.getThemeMode(appContext)) }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            LaunchedEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            LaunchedEffect(darkTheme) {
                LimitsWidget().updateAll(appContext)
            }

            AGWidgetsTheme(darkTheme = darkTheme) {
                AgWidgetsScreen(
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        val next = if (darkTheme) "light" else "dark"
                        StatusStore.setThemeMode(appContext, next)
                        themeMode = next
                    },
                )
            }
        }
    }
}
