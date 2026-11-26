package dev.agwidgets.ui.widget

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.QuotaBucket
import dev.agwidgets.data.StatusStore
import dev.agwidgets.data.ZCodeStatus
import dev.agwidgets.ui.contextLevel
import dev.agwidgets.ui.formatCountdownShort
import dev.agwidgets.ui.formatK
import dev.agwidgets.ui.formatUnits
import dev.agwidgets.ui.formatWindow
import dev.agwidgets.ui.parseInstant
import dev.agwidgets.ui.prettyModel
import dev.agwidgets.ui.theme.AgColors
import dev.agwidgets.ui.theme.agColorsFor
import dev.agwidgets.widget.WidgetBlockId
import dev.agwidgets.widget.WidgetConfig
import dev.agwidgets.widget.WidgetConfigStore
import dev.agwidgets.widget.WidgetDensity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Compose imitation of the home-screen widget drawn over a soft wallpaper.
 *
 * The preview is intentionally not Glance: it lives in a normal Activity, can
 * be re-measured on every slider move and keeps the real widget contract
 * (`WidgetConfig` + `WidgetConfigStore`) as its only source of truth.
 */
@Composable
fun WidgetLivePreview(config: WidgetConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = remember(context, config.theme) {
        agColorsFor(WidgetConfigStore.resolveWidgetTheme(context, config))
    }
    val customAccent = parseAccent(config.customAccent)
    val glow = customAccent ?: colors.glow
    val violet = customAccent ?: colors.violet
    val status = remember(context) { StatusStore.getMergedStatus(context) }
    val state = remember(status) { buildPreviewState(status) }
    val scale = config.fontScale.coerceIn(0.85f, 1.4f)
    val innerPadding = when (config.density) {
        WidgetDensity.COMPACT -> 8.dp
        WidgetDensity.COMFORTABLE -> 10.dp
        WidgetDensity.SPACIOUS -> 14.dp
    }
    val blockSpacing = when (config.density) {
        WidgetDensity.COMPACT -> 4.dp
        WidgetDensity.COMFORTABLE -> 5.dp
        WidgetDensity.SPACIOUS -> 7.dp
    }
    val shape = RoundedCornerShape(config.cornerRadiusDp.dp)
    val wallpaper = Brush.linearGradient(
        listOf(glow.copy(alpha = 0.45f), colors.background, violet.copy(alpha = 0.40f)),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(RoundedCornerShape(18.dp))
            .background(wallpaper),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val lineColor = colors.text.copy(alpha = 0.05f)
            val step = 22.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(shape)
                .background(colors.surface.copy(alpha = config.opacity.coerceIn(0f, 1f)))
                .border(1.dp, colors.border, shape)
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(blockSpacing),
        ) {
            config.blocks.filter { it.enabled }.forEach { block ->
                when (block.id) {
                    WidgetBlockId.HEADER -> PreviewHeader(colors, glow, state, scale)
                    WidgetBlockId.CONTEXT -> PreviewContext(
                        colors = colors,
                        glow = glow,
                        state = state,
                        scale = scale,
                        showCompactChip = config.option(
                            WidgetBlockId.CONTEXT,
                            "show_compact_chip",
                            true,
                        ),
                    )
                    WidgetBlockId.QUOTAS -> PreviewQuotas(
                        colors = colors,
                        glow = glow,
                        violet = violet,
                        state = state,
                        scale = scale,
                        showGemini = config.option(WidgetBlockId.QUOTAS, "show_gemini", true),
                        showThirdParty = config.option(WidgetBlockId.QUOTAS, "show_3p", true),
                        showCountdown = config.option(WidgetBlockId.QUOTAS, "show_countdown", true),
                    )
                    WidgetBlockId.ZCODE -> PreviewZCode(
                        colors = colors,
                        violet = violet,
                        state = state,
                        scale = scale,
                        showBalanceBar = config.option(
                            WidgetBlockId.ZCODE,
                            "show_balance_bar",
                            true,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(colors: AgColors, glow: Color, state: PreviewState, scale: Float) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "●", fontSize = scaled(8f, scale), color = glow)
        Spacer(Modifier.width(4.dp))
        if (colors.isClassic) {
            Text(
                text = "AGW / 01",
                fontFamily = FontFamily.Monospace,
                fontSize = scaled(8f, scale),
                color = glow,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "AG Widgets",
            fontSize = scaled(11f, scale),
            fontWeight = FontWeight.Bold,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = state.time ?: "—",
            fontFamily = FontFamily.Monospace,
            fontSize = scaled(8f, scale),
            color = colors.muted,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.track)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = "⟳",
                fontFamily = FontFamily.Monospace,
                fontSize = scaled(8f, scale),
                color = colors.secondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PreviewContext(
    colors: AgColors,
    glow: Color,
    state: PreviewState,
    scale: Float,
    showCompactChip: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CONTEXT",
                fontFamily = FontFamily.Monospace,
                fontSize = scaled(7f, scale),
                letterSpacing = 0.5.sp,
                color = colors.muted,
                maxLines = 1,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = state.model,
                fontSize = scaled(10f, scale),
                fontWeight = FontWeight.Bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = state.usage,
                fontFamily = FontFamily.Monospace,
                fontSize = scaled(8f, scale),
                color = colors.secondary,
                maxLines = 1,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PreviewBar(
                fraction = (state.contextPct ?: 0) / 100f,
                color = levelColor(state.contextLevel, colors, glow),
                track = colors.track,
                modifier = Modifier.weight(1f),
                height = 3.dp,
            )
            if (showCompactChip) {
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.track)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "/compact",
                        fontFamily = FontFamily.Monospace,
                        fontSize = scaled(7f, scale),
                        color = colors.secondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewQuotas(
    colors: AgColors,
    glow: Color,
    violet: Color,
    state: PreviewState,
    scale: Float,
    showGemini: Boolean,
    showThirdParty: Boolean,
    showCountdown: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showGemini) {
            PreviewQuotaRow(state.gemini, glow, colors, scale, showCountdown)
        }
        if (showThirdParty) {
            PreviewQuotaRow(state.thirdParty, violet, colors, scale, showCountdown)
        }
    }
}

@Composable
private fun PreviewQuotaRow(
    quotas: List<PreviewQuota>,
    accent: Color,
    colors: AgColors,
    scale: Float,
    showCountdown: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        quotas.forEachIndexed { index, quota ->
            if (index > 0) Spacer(Modifier.width(6.dp))
            PreviewQuotaCell(quota, accent, colors, scale, showCountdown, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewQuotaCell(
    quota: PreviewQuota,
    accent: Color,
    colors: AgColors,
    scale: Float,
    showCountdown: Boolean,
    modifier: Modifier = Modifier,
) {
    val fraction = quota.pct?.let { (it / 100f).coerceIn(0f, 1f) }
    val valueColor = when {
        fraction == null -> colors.muted
        fraction < 0.2f -> colors.rose
        fraction < 0.45f -> colors.amber
        else -> accent
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = quota.label,
                fontFamily = FontFamily.Monospace,
                fontSize = scaled(7f, scale),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = quota.pct?.let { "$it%" } ?: "—",
                fontSize = scaled(9f, scale),
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
            )
        }
        PreviewBar(
            fraction = fraction ?: 0f,
            color = valueColor,
            track = colors.track,
            height = 3.dp,
        )
        if (showCountdown) {
            Text(
                text = quota.reset ?: "—",
                fontFamily = FontFamily.Monospace,
                fontSize = scaled(7f, scale),
                color = colors.muted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PreviewZCode(
    colors: AgColors,
    violet: Color,
    state: PreviewState,
    scale: Float,
    showBalanceBar: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(violet.copy(alpha = if (colors.isDark) 0.20f else 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = state.zcodeLabel,
                fontSize = scaled(8f, scale),
                fontWeight = FontWeight.Medium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showBalanceBar) {
            Spacer(Modifier.width(6.dp))
            PreviewBar(
                fraction = state.zcodeFraction ?: 0f,
                color = violet,
                track = colors.track,
                modifier = Modifier.weight(1f),
                height = 4.dp,
            )
        }
    }
}

@Composable
private fun PreviewBar(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color),
        )
    }
}

private fun scaled(size: Float, scale: Float) = (size * scale).sp

private fun parseAccent(raw: String?): Color? = raw?.let { hex ->
    runCatching { AndroidColor.parseColor(hex) }.getOrNull()?.let { Color(it) }
}

private fun levelColor(level: String?, colors: AgColors, glow: Color): Color = when (level) {
    "red" -> colors.rose
    "yellow" -> colors.amber
    "green" -> colors.green
    "unknown" -> colors.muted
    else -> glow
}

private data class PreviewQuota(val label: String, val pct: Int?, val reset: String?)

private data class PreviewState(
    val time: String?,
    val model: String,
    val usage: String,
    val contextPct: Int?,
    val contextLevel: String?,
    val gemini: List<PreviewQuota>,
    val thirdParty: List<PreviewQuota>,
    val zcodeLabel: String,
    val zcodeFraction: Float?,
)

/** Shown when the phone has no cached status yet. */
private val demoPreview = PreviewState(
    time = "12:40",
    model = "Gemini 4",
    usage = "312K / 512K (61%)",
    contextPct = 61,
    contextLevel = "yellow",
    gemini = listOf(
        PreviewQuota("GEMINI 5H", 74, "2ч 40м"),
        PreviewQuota("GEMINI WK", 82, "4д 2ч"),
    ),
    thirdParty = listOf(
        PreviewQuota("CLAUDE/GPT 5H", 41, "1ч 15м"),
        PreviewQuota("CLAUDE/GPT WK", 63, "3д 8ч"),
    ),
    zcodeLabel = "GLM 320M",
    zcodeFraction = 0.64f,
)

private fun buildPreviewState(status: AgStatus?): PreviewState {
    if (status == null) return demoPreview
    val ctx = status.contextSummary
    val tokens = ctx?.tokens
    val window = ctx?.window?.takeIf { it > 0 }
    val pct = (ctx?.pct ?: if (tokens != null && window != null) tokens * 100.0 / window else null)
        ?.takeIf { it.isFinite() }
    val usage = if (tokens == null) {
        "—"
    } else {
        buildString {
            append(formatK(tokens))
            append(" / ")
            append(window?.let { formatWindow(it) } ?: "—")
            pct?.let { append(" (").append(it.roundToInt()).append("%)") }
        }
    }
    val geminiBuckets = status.buckets.filter { !it.id.startsWith("3p-") }
    val thirdBuckets = status.buckets.filter { it.id.startsWith("3p-") }
    val fiveHour = geminiBuckets.firstOrNull { it.id.startsWith("gemini-") && it.kind == "5h" }
        ?: geminiBuckets.firstOrNull { it.kind == "5h" }
    val weekly = geminiBuckets.firstOrNull { it.id.startsWith("gemini-") && it.kind == "weekly" }
        ?: geminiBuckets.firstOrNull { it.kind == "weekly" }
    val thirdFiveHour = thirdBuckets.firstOrNull { it.kind == "5h" }
    val thirdWeekly = thirdBuckets.firstOrNull { it.kind == "weekly" }
    val now = System.currentTimeMillis()
    return PreviewState(
        time = formatPreviewTime(status.pcUpdatedAt ?: status.updatedAt),
        model = prettyModel(ctx?.model.orEmpty().ifBlank { "—" }),
        usage = usage,
        contextPct = pct?.roundToInt(),
        contextLevel = contextLevel(pct, ctx?.level, ctx?.yellowPct, ctx?.redPct),
        gemini = listOf(
            quotaPreview("GEMINI 5H", fiveHour, now),
            quotaPreview("GEMINI WK", weekly, now),
        ),
        thirdParty = listOf(
            quotaPreview("CLAUDE/GPT 5H", thirdFiveHour, now),
            quotaPreview("CLAUDE/GPT WK", thirdWeekly, now),
        ),
        zcodeLabel = zcodeLabel(status.zcode),
        zcodeFraction = zcodeFraction(status.zcode),
    )
}

private fun quotaPreview(label: String, bucket: QuotaBucket?, now: Long): PreviewQuota {
    val fraction = bucket?.remainingFraction?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
    return PreviewQuota(
        label = label,
        pct = fraction?.let { (it * 100).roundToInt() },
        reset = formatCountdownShort(bucket?.resetTime, now),
    )
}

private fun zcodeLabel(zcode: ZCodeStatus?): String {
    if (zcode == null) return "ZCode: нет данных"
    val remaining = zcode.remainingUnits
    return when {
        remaining != null && remaining > 0 ->
            "${zcode.planName.ifBlank { "ZCode" }} ${formatUnits(remaining)}"
        zcode.isCouponVisible -> "Ваучер доступен!"
        else -> "ZCode"
    }
}

private fun zcodeFraction(zcode: ZCodeStatus?): Float? {
    if (zcode == null) return null
    val remaining = zcode.remainingUnits ?: return null
    val total = zcode.totalUnits ?: return null
    if (total <= 0) return null
    return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun formatPreviewTime(iso: String?): String? {
    val instant = parseInstant(iso) ?: return null
    return DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
