package dev.agwidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.agwidgets.MainActivity
import dev.agwidgets.R
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.StatusStore
import dev.agwidgets.ui.theme.AgColors
import dev.agwidgets.ui.theme.ResolvedAgTheme
import dev.agwidgets.ui.theme.agColorsFor
import dev.agwidgets.work.performRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Rapid taps must not stack overlapping network passes.
        if (!refreshInFlight.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        try {
            // Hard cap keeps the launcher's action window happy even if a host
            // ignores the short per-request timeouts.
            withTimeoutOrNull(8_500L) {
                withContext(Dispatchers.IO) {
                    performRefresh(appContext, timeoutMs = 4_000, updateWidget = false)
                }
            }
        } finally {
            runCatching { LimitsWidget().updateAll(appContext) }
            refreshInFlight.set(false)
        }
    }

    private companion object {
        private val refreshInFlight = AtomicBoolean(false)
    }
}

/** Widget height buckets used to adapt spacing and extra sections. */
internal enum class WidgetLayoutMode { COMPACT, STANDARD, EXPANDED }

/**
 * Height in dp below 165 is COMPACT, 165..215 inclusive is STANDARD and
 * anything above 215 is EXPANDED.
 */
internal fun widgetLayoutMode(heightDp: Float): WidgetLayoutMode = when {
    heightDp < 165f -> WidgetLayoutMode.COMPACT
    heightDp <= 215f -> WidgetLayoutMode.STANDARD
    else -> WidgetLayoutMode.EXPANDED
}

class LimitsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // A widget created before the config-store landed has no stored id;
        // -1 simply resolves to the default config.
        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(-1)
        val config = WidgetConfigStore.get(context, appWidgetId)
        val resolved = WidgetConfigStore.resolveWidgetTheme(context, config)
        val status = StatusStore.getMergedStatus(context)
        provideContent {
            WidgetContent(status = status, resolved = resolved, config = config)
        }
    }
}

/**
 * Background drawable for a resolved theme: calm flat surfaces for the modern
 * themes, the author's glass layers for `glass` and `classic`.
 */
private fun widgetBackground(resolved: ResolvedAgTheme): Int = when (resolved.id) {
    "graphite" -> R.drawable.widget_bg_graphite
    "paper" -> R.drawable.widget_bg_paper
    "oled" -> R.drawable.widget_bg_oled
    "glass_dark", "classic_dark" -> R.drawable.glass_bg
    else -> R.drawable.glass_bg_light
}

@Composable
private fun WidgetContent(status: AgStatus?, resolved: ResolvedAgTheme, config: WidgetConfig) {
    val heightDp = LocalSize.current.height.value
    val isCompactHeight = heightDp < 90f
    val step = if (isCompactHeight) 6 else densityPadding(config.density)

    // Colours are always fixed ARGB values: `system` was already resolved against
    // the current night mode, so every palette is concrete by this point.
    val baseColors = agColorsFor(resolved)
    val accentColor: Color = config.customAccent?.let { raw ->
        runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull()
    } ?: baseColors.glow
    val colors: AgColors = if (accentColor == baseColors.glow) {
        baseColors
    } else {
        baseColors.copy(glow = accentColor, violet = accentColor)
    }
    // The author's original glass art is kept verbatim for Classic at default
    // opacity/radius; every other combination is drawn as a plain rounded surface.
    val useLegacyBackground =
        colors.isClassic && config.opacity >= 0.9f && config.cornerRadiusDp == 10

    val rootModifier = GlanceModifier
        .fillMaxSize()
        .clickable(actionStartActivity<MainActivity>())
    val surfaceModifier = if (useLegacyBackground) {
        rootModifier
    } else {
        rootModifier
            .background(ColorProvider(colors.surface.copy(alpha = config.opacity)))
            .cornerRadius(config.cornerRadiusDp.dp)
    }

    val subtle = ColorProvider(colors.muted)
    val track = ColorProvider(colors.track)

    Box(modifier = surfaceModifier) {
        if (useLegacyBackground) {
            Image(
                provider = ImageProvider(widgetBackground(resolved)),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize()
            )
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    start = step.dp,
                    end = step.dp,
                    top = if (isCompactHeight) step.dp else 6.dp,
                    bottom = step.dp,
                ),
            verticalAlignment = Alignment.Top
        ) {
            val headerEnabled = config.isEnabled(WidgetBlockId.HEADER)
            val enabledBlocks = config.blocks.filter { it.enabled }
            val inlineHost = config.firstEnabled()?.id

            if (status == null) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Нет данных · откройте приложение",
                        style = TextStyle(
                            color = ColorProvider(colors.secondary),
                            fontSize = scaledSp(12f, config.fontScale).sp,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    // With no blocks at all this row is the only place for ⟳.
                    if (enabledBlocks.isEmpty()) {
                        InlineRefreshButton(tint = subtle, background = track)
                    }
                }
                if (enabledBlocks.isNotEmpty()) {
                    Spacer(GlanceModifier.height(step.dp))
                }
            }

            enabledBlocks.forEachIndexed { index, block ->
                if (index > 0) {
                    Spacer(GlanceModifier.height(step.dp))
                }
                // Presets without a header (context_compact / zcode_only) host the
                // single inline ⟳ in their first enabled block instead.
                val showInlineRefresh = !headerEnabled && block.id == inlineHost
                when (block.id) {
                    WidgetBlockId.HEADER -> HeaderFreshnessAtom(
                        status = status,
                        config = config,
                        colors = colors,
                        compact = isCompactHeight,
                    )
                    WidgetBlockId.CONTEXT -> ContextBarAtom(
                        ctx = status?.contextSummary,
                        config = config,
                        colors = colors,
                        showInlineRefresh = showInlineRefresh,
                    )
                    WidgetBlockId.ZCODE -> ZCodeAtom(
                        zcode = status?.zcode,
                        config = config,
                        colors = colors,
                        showInlineRefresh = showInlineRefresh,
                    )
                    WidgetBlockId.QUOTAS -> {
                        if (showInlineRefresh) {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = GlanceModifier.defaultWeight()) {
                                    QuotasAtom(
                                        status = status,
                                        config = config,
                                        colors = colors,
                                        isCompactHeight = isCompactHeight,
                                    )
                                }
                                InlineRefreshButton(tint = subtle, background = track)
                            }
                        } else {
                            QuotasAtom(
                                status = status,
                                config = config,
                                colors = colors,
                                isCompactHeight = isCompactHeight,
                            )
                        }
                    }
                }
            }

            // Every block disabled: keep the widget actionable instead of empty.
            if (enabledBlocks.isEmpty() && status != null) {
                InlineRefreshButton(tint = subtle, background = track)
            }
        }
    }
}
