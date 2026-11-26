package dev.agwidgets.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.ContextSummary
import dev.agwidgets.data.QuotaBucket
import dev.agwidgets.data.ZCodeStatus
import dev.agwidgets.ui.formatCountdownShort
import dev.agwidgets.ui.formatK
import dev.agwidgets.ui.formatUnits
import dev.agwidgets.ui.formatWindow
import dev.agwidgets.ui.parseInstant
import dev.agwidgets.ui.prettyModel
import dev.agwidgets.ui.theme.AgColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Modular Glance atoms shared by the widget body. Every atom is a pure function
 * of the stored config and the resolved palette, so the host widget only decides
 * block order, spacing and background.
 */

/** Spacing scale for the widget body: compact 10dp, comfortable 12dp, spacious 14dp. */
internal fun densityPadding(density: WidgetDensity): Int = when (density) {
    WidgetDensity.COMPACT -> 10
    WidgetDensity.COMFORTABLE -> 12
    WidgetDensity.SPACIOUS -> 14
}

/** Scales a base font size and clamps it into the readable 8sp..24sp range. */
internal fun scaledSp(base: Float, fontScale: Float): Float =
    (base * fontScale).coerceIn(8f, 24f)

/** Small intra-block spacing: compact 4dp, comfortable +2dp, spacious +4dp. */
private fun densityGap(density: WidgetDensity): Int = when (density) {
    WidgetDensity.COMPACT -> 4
    WidgetDensity.COMFORTABLE -> 6
    WidgetDensity.SPACIOUS -> 8
}

/** Shared 42x40 tap target with a 26x24 visible pill around the refresh glyph. */
@Composable
fun InlineRefreshButton(tint: ColorProvider, background: ColorProvider) {
    // The pill alone is too small to hit reliably, so the clickable modifier
    // lives on the larger outer box.
    Box(
        modifier = GlanceModifier
            .clickable(actionRunCallback<RefreshActionCallback>())
            .width(42.dp)
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .width(26.dp)
                .height(24.dp)
                .background(background)
                .cornerRadius(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⟳",
                style = TextStyle(
                    color = tint,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Brand line, plan tier, last-update time and the always-present refresh pill. */
@Composable
fun HeaderFreshnessAtom(
    status: AgStatus?,
    config: WidgetConfig,
    colors: AgColors,
    compact: Boolean,
) {
    val fg = ColorProvider(colors.text)
    val subtle = ColorProvider(colors.muted)
    val accent = ColorProvider(colors.glow)
    val pill = ColorProvider(colors.track)
    // The author's `AGW / 01` code stays exclusively in Classic.
    val brand = if (colors.isClassic) "AGW / 01" else "AG Widgets"
    val time = formatTimeShort(status?.pcUpdatedAt ?: status?.updatedAt) ?: "—"

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "●",
            style = TextStyle(color = accent, fontSize = scaledSp(9f, config.fontScale).sp),
        )
        Spacer(GlanceModifier.width((if (compact) 4 else 5).dp))
        Text(
            text = brand,
            style = TextStyle(
                color = if (colors.isClassic) accent else subtle,
                fontSize = scaledSp(if (compact) 8f else 9f, config.fontScale).sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width((if (compact) 4 else 7).dp))
        Text(
            text = "Antigravity" + (status?.planTier?.let { " · $it" } ?: ""),
            style = TextStyle(
                color = fg,
                fontSize = scaledSp(if (compact) 10f else 12f, config.fontScale).sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        InlineRefreshButton(tint = subtle, background = pill)
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = time,
            style = TextStyle(
                color = subtle,
                fontSize = scaledSp(9f, config.fontScale).sp,
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 1,
        )
    }
}

/** Active context window: model, token usage, colour-coded bar and `/compact` chip. */
@Composable
fun ContextBarAtom(
    ctx: ContextSummary?,
    config: WidgetConfig,
    colors: AgColors,
    showInlineRefresh: Boolean,
) {
    val subtle = ColorProvider(colors.muted)
    val pill = ColorProvider(colors.track)
    val tokens = ctx?.tokens
    if (ctx == null || tokens == null) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Контекст недоступен",
                style = TextStyle(
                    color = ColorProvider(colors.secondary),
                    fontSize = scaledSp(10f, config.fontScale).sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (showInlineRefresh) InlineRefreshButton(tint = subtle, background = pill)
        }
        return
    }
    val window = ctx.window
    // NaN/Infinity must never reach the bitmap renderer or the percent label.
    val pct = (ctx.pct ?: window?.takeIf { it > 0 }?.let { tokens * 100.0 / it })
        ?.takeIf { it.isFinite() }
    val fraction = (pct?.div(100.0) ?: 0.0).toFloat().coerceIn(0f, 1f)
    val levelColor: Color = when (ctx.level) {
        "red" -> colors.rose
        "yellow" -> colors.amber
        "unknown" -> colors.muted
        "green" -> colors.green
        else -> colors.glow
    }
    val showChip = config.option(WidgetBlockId.CONTEXT, "show_compact_chip", true) && ctx.level == "red"
    val barHeight = if (config.density == WidgetDensity.COMPACT) 4 else 5

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CONTEXT",
                style = TextStyle(
                    color = subtle,
                    fontSize = scaledSp(8f, config.fontScale).sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = prettyModel(ctx.model.orEmpty().ifBlank { "—" }),
                style = TextStyle(
                    color = ColorProvider(colors.text),
                    fontSize = scaledSp(10f, config.fontScale).sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = buildString {
                    append(formatK(tokens))
                    append(" / ")
                    append(window?.let { formatWindow(it) } ?: "—")
                    pct?.let { append(" (").append(it.roundToInt()).append("%)") }
                },
                style = TextStyle(
                    color = subtle,
                    fontSize = scaledSp(9f, config.fontScale).sp,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
            )
            if (showChip) {
                Spacer(GlanceModifier.width(4.dp))
                CompactChip(colors)
            }
            if (showInlineRefresh) InlineRefreshButton(tint = subtle, background = pill)
        }
        Spacer(GlanceModifier.height(4.dp))
        BarImage(fraction, levelColor, colors.track, barHeight)
    }
}

/** Gemini and CLAUDE/GPT quota pairs as a 2x2 grid (or a single row in short widgets). */
@Composable
fun QuotasAtom(
    status: AgStatus?,
    config: WidgetConfig,
    colors: AgColors,
    isCompactHeight: Boolean,
) {
    val showGemini = config.option(WidgetBlockId.QUOTAS, "show_gemini", true)
    val show3p = config.option(WidgetBlockId.QUOTAS, "show_3p", true)
    val showCountdown = config.option(WidgetBlockId.QUOTAS, "show_countdown", true)
    // Both groups disabled means the block disappears entirely: no empty holes.
    if (!showGemini && !show3p) return

    val buckets = status?.buckets.orEmpty()
    // Gemini slots never use 3p- buckets; CLAUDE/GPT slots use only 3p- buckets.
    val geminiBuckets = buckets.filter { !it.id.startsWith("3p-") }
    val fiveHour = geminiBuckets.firstOrNull { it.id.startsWith("gemini-") && it.kind == "5h" }
        ?: geminiBuckets.firstOrNull { it.kind == "5h" }
    val weekly = geminiBuckets.firstOrNull { it.id.startsWith("gemini-") && it.kind == "weekly" }
        ?: geminiBuckets.firstOrNull { it.kind == "weekly" }
    val thirdFiveHour = buckets.firstOrNull { it.id.startsWith("3p-") && it.kind == "5h" }
    val thirdWeekly = buckets.firstOrNull { it.id.startsWith("3p-") && it.kind == "weekly" }

    val gap = densityGap(config.density)
    val now = System.currentTimeMillis()
    val cellGap = if (isCompactHeight) 5.dp else 7.dp

    if (showGemini && show3p && !isCompactHeight) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                QuotaCell(
                    label = "GEMINI 5H",
                    bucket = fiveHour,
                    config = config,
                    colors = colors,
                    showCountdown = showCountdown,
                    accent = colors.glow,
                    now = now,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(cellGap))
                QuotaCell(
                    label = "GEMINI WK",
                    bucket = weekly,
                    config = config,
                    colors = colors,
                    showCountdown = showCountdown,
                    accent = colors.glow,
                    now = now,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            Spacer(GlanceModifier.height(gap.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                QuotaCell(
                    label = "CLAUDE/GPT 5H",
                    bucket = thirdFiveHour,
                    config = config,
                    colors = colors,
                    showCountdown = showCountdown,
                    accent = colors.pink,
                    now = now,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(cellGap))
                QuotaCell(
                    label = "CLAUDE/GPT WK",
                    bucket = thirdWeekly,
                    config = config,
                    colors = colors,
                    showCountdown = showCountdown,
                    accent = colors.pink,
                    now = now,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    } else {
        // One row: only the enabled groups get cells, so no blank placeholders.
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            if (showGemini) {
                QuotaCell(
                    label = "GEMINI 5H",
                    bucket = fiveHour,
                    config = config,
                    colors = colors,
                    showCountdown = showCountdown,
                    accent = colors.glow,
                    now = now,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(cellGap))
                QuotaCell(
                    label = "GEMINI WK",
                    bucket = weekly,
                    config = config,
                    colors = colors,
                    showCountdown = showCountdown,
                    accent = colors.glow,
                    now = now,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            if (showGemini && show3p) Spacer(GlanceModifier.width(gap.dp))
            if (show3p) {
                QuotaCell(
                    label = "CLAUDE/GPT 5H",
                    bucket = thirdFiveHour,
                    config = config,
                    colors = colors,
                    showCountdown = showCountdown,
                    accent = colors.pink,
                    now = now,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(cellGap))
                QuotaCell(
                    label = "CLAUDE/GPT WK",
                    bucket = thirdWeekly,
                    config = config,
                    colors = colors,
                    showCountdown = showCountdown,
                    accent = colors.pink,
                    now = now,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

/** ZCode status chip plus the optional remaining-balance bar. */
@Composable
fun ZCodeAtom(
    zcode: ZCodeStatus?,
    config: WidgetConfig,
    colors: AgColors,
    showInlineRefresh: Boolean,
) {
    val subtle = ColorProvider(colors.muted)
    val pill = ColorProvider(colors.track)
    val (text, background) = zcodeChip(zcode, colors)

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(
                text = text,
                background = background,
                color = ColorProvider(colors.text),
                fontScale = config.fontScale,
            )
            if (showInlineRefresh) {
                Spacer(GlanceModifier.defaultWeight())
                InlineRefreshButton(tint = subtle, background = pill)
            }
        }
        val remaining = zcode?.remainingUnits
        val total = zcode?.totalUnits
        if (config.option(WidgetBlockId.ZCODE, "show_balance_bar", true) &&
            remaining != null && total != null && total > 0L
        ) {
            Spacer(GlanceModifier.height(densityGap(config.density).dp))
            BarImage(
                fraction = (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                fillColor = colors.glow,
                trackColor = colors.track,
                heightDp = 4,
            )
        }
    }
}

@Composable
private fun BarImage(fraction: Float, fillColor: Color, trackColor: Color, heightDp: Int) {
    Image(
        provider = ImageProvider(
            BarRenderer.renderBar(fraction.coerceIn(0f, 1f), fillColor.toArgb(), trackColor.toArgb())
        ),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp),
    )
}

@Composable
private fun CompactChip(colors: AgColors) {
    val chipAlpha = if (colors.isDark) 0.27f else 0.14f
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(colors.rose.copy(alpha = chipAlpha)))
            .cornerRadius(9.dp)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = "/compact",
            style = TextStyle(
                color = ColorProvider(colors.rose),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    background: ColorProvider,
    color: ColorProvider,
    fontScale: Float,
) {
    Box(
        modifier = GlanceModifier
            .background(background)
            .cornerRadius(12.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = color,
                fontSize = scaledSp(9.5f, fontScale).sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun QuotaCell(
    label: String,
    bucket: QuotaBucket?,
    config: WidgetConfig,
    colors: AgColors,
    showCountdown: Boolean,
    accent: Color,
    now: Long,
    modifier: GlanceModifier = GlanceModifier,
) {
    val frac = bucket?.remainingFraction?.toFloat()
        ?.takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f)
    val pct = frac?.let { (it * 100).roundToInt() }
    // Healthy buckets follow the group accent; warning tones kick in below the thresholds.
    val valueColor: Color = when {
        bucket == null -> colors.muted
        frac != null && frac < 0.2f -> colors.rose
        frac != null && frac < 0.45f -> colors.amber
        else -> accent
    }
    val barColor: Color = when {
        bucket == null -> colors.muted
        frac != null && frac < 0.2f -> colors.rose
        frac != null && frac < 0.45f -> colors.amber
        else -> accent
    }

    Column(modifier = modifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(colors.muted),
                    fontSize = scaledSp(9f, config.fontScale).sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = if (pct == null) "—" else "$pct%",
                style = TextStyle(
                    color = ColorProvider(valueColor),
                    fontSize = scaledSp(10f, config.fontScale).sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        BarImage(frac ?: 0f, barColor, colors.track, 3)
        if (showCountdown) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = formatCountdownShort(bucket?.resetTime, now) ?: "—",
                style = TextStyle(
                    color = ColorProvider(colors.muted),
                    fontSize = scaledSp(9f, config.fontScale).sp,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Chip backgrounds keep the original Classic alphas; modern palettes reuse their
 * semantic accent colours with the same translucency so contrast stays stable.
 */
private fun zcodeChip(z: ZCodeStatus?, colors: AgColors): Pair<String, ColorProvider> {
    val chipAlpha = if (colors.isDark) 0.27f else 0.14f
    val neutral = ColorProvider(colors.track)
    val error = ColorProvider(colors.rose.copy(alpha = chipAlpha))
    val warn = ColorProvider(colors.amber.copy(alpha = chipAlpha))
    val coupon = ColorProvider(colors.glow.copy(alpha = if (colors.isDark) 0.40f else 0.12f))
    val glm = ColorProvider(colors.violet.copy(alpha = if (colors.isDark) 0.20f else 0.12f))
    if (z == null) return "ZCode: нет данных" to neutral
    // Local copies keep the nullable checks below readable and smart-cast safe.
    val remaining = z.remainingUnits
    val total = z.totalUnits
    return when {
        z.isCouponUnknown -> {
            val text = when (z.errorKind) {
                "network" -> "ZCode: нет связи"
                "auth_expired" -> "ZCode: нужен вход"
                "rate_limited" -> "ZCode: лимит 429"
                "server_error", "api_changed", "parse_failed" -> "ZCode: ошибка проверки"
                else -> "ZCode: неизвестно"
            }
            val bg = when (z.errorKind) {
                "network", "rate_limited", "server_error", "api_changed", "parse_failed" -> error
                else -> warn
            }
            text to bg
        }
        z.networkError -> "ZCode: нет связи" to error
        z.method == "api" && z.authOk == false -> "ZCode: нужен вход" to warn
        z.method == "disabled" -> "ZCode не подключён" to neutral
        z.isCouponVisible -> "Ваучер доступен!" to coupon
        remaining != null && (remaining > 0 || (total != null && total > 0)) ->
            "${z.planName.ifBlank { "ZCode" }} ${formatUnits(remaining)}" to glm
        !z.running && z.method != "api" -> "ZCode не запущен" to neutral
        else -> "ZCode: нет ваучера" to neutral
    }
}

private fun formatTimeShort(iso: String?): String? {
    val instant = parseInstant(iso) ?: return null
    return DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
