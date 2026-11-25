package dev.agwidgets.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.QuotaBucket
import dev.agwidgets.ui.formatCountdownShort
import dev.agwidgets.ui.theme.AgTheme
import dev.agwidgets.ui.updatedLabel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dashboard block 2 — Antigravity quotas.
 *
 * Modern themes render four horizontal bars with a readable reset time and a
 * large percentage. `Classic` keeps the original orbital rings 1:1.
 */
@Composable
internal fun HeroCard(status: AgStatus?, nowMs: Long) {
    val colors = AgTheme.colors
    val buckets = status?.buckets.orEmpty()
    // 3p-* belongs to CLAUDE/GPT only; never duplicate it in the GEMINI slots.
    val geminiBuckets = buckets.filterNot { it.id.startsWith("3p-") }
    val fiveHour = geminiBuckets.firstOrNull { it.id.startsWith("gemini-") && it.kind == "5h" }
        ?: geminiBuckets.firstOrNull { it.kind == "5h" }
    val weekly = geminiBuckets.firstOrNull { it.id.startsWith("gemini-") && it.kind == "weekly" }
        ?: geminiBuckets.firstOrNull { it.kind == "weekly" }
    val thirdBuckets = buckets.filter { it.id.startsWith("3p-") }
    val thirdFiveHour = thirdBuckets.firstOrNull { it.kind == "5h" }
    val thirdWeekly = thirdBuckets.firstOrNull { it.kind == "weekly" }
    val updated = status?.let { updatedLabel(it.pcUpdatedAt ?: it.updatedAt, nowMs) } ?: "—"

    GlassCard {
        if (colors.isClassic) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "02 / LIMITS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 1.3.sp,
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = updated,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = colors.muted,
                )
            }
            Text(
                text = "Лимиты",
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.4).sp),
                color = colors.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuotaRing("GEMINI · 5Ч", fiveHour, nowMs, accent = colors.glow, modifier = Modifier.weight(1f))
                QuotaRing("GEMINI · НЕД", weekly, nowMs, accent = colors.glow, modifier = Modifier.weight(1f))
            }
            if (thirdFiveHour != null || thirdWeekly != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuotaRing(
                        "CLAUDE/GPT · 5Ч",
                        thirdFiveHour,
                        nowMs,
                        accent = colors.pink,
                        modifier = Modifier.weight(1f),
                    )
                    QuotaRing(
                        "CLAUDE/GPT · НЕД",
                        thirdWeekly,
                        nowMs,
                        accent = colors.pink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Лимиты",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    status?.planTier?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            color = colors.secondary,
                        )
                    }
                    Text(
                        text = updated,
                        fontSize = 11.sp,
                        color = colors.muted,
                    )
                }
            }
            QuotaRow("Gemini · 5ч", fiveHour, nowMs)
            QuotaRow("Gemini · неделя", weekly, nowMs)
            QuotaRow("Claude/GPT · 5ч", thirdFiveHour, nowMs)
            QuotaRow("Claude/GPT · неделя", thirdWeekly, nowMs)
        }
    }
}

/** One modern quota line: label + reset time on the left, big % on the right, bar below. */
@Composable
private fun QuotaRow(label: String, bucket: QuotaBucket?, nowMs: Long) {
    val colors = AgTheme.colors
    // A missing or non-finite fraction must render as "—", never as 0% or NaN.
    val rawFraction = bucket?.remainingFraction
    val known = rawFraction != null && rawFraction.isFinite()
    val target = rawFraction?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat()
    val color = if (target == null) {
        colors.muted
    } else if (target < 0.2f) {
        colors.rose
    } else if (target < 0.45f) {
        colors.amber
    } else {
        colors.green
    }
    val reset = formatCountdownShort(bucket?.resetTime, nowMs) ?: "нет данных"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                )
                Text(
                    text = reset,
                    fontSize = 11.5.sp,
                    color = colors.muted,
                )
            }
            Text(
                text = if (known && target != null) "${(target * 100).roundToInt()}%" else "—",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = if (known) colors.text else colors.muted,
            )
        }
        QuotaBar(fraction = target, color = color)
    }
}

@Composable
private fun QuotaBar(fraction: Float?, color: Color) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(AgTheme.colors.track),
    ) {
        if (fraction != null && fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

/** Original Classic ring with the dashed orbit, reticle and sweeping arc. */
@Composable
private fun QuotaRing(
    label: String,
    bucket: QuotaBucket?,
    nowMs: Long,
    accent: Color = AgTheme.colors.glow,
    modifier: Modifier = Modifier,
) {
    // A missing or non-finite fraction must render as "—", never as 0% or NaN.
    val fraction = bucket?.remainingFraction?.takeIf { it.isFinite() }
    val known = fraction != null
    val target = fraction?.coerceIn(0.0, 1.0)?.toFloat() ?: 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "quota",
    )
    val color = when {
        !known -> AgTheme.colors.muted
        target < 0.2f -> AgTheme.colors.rose
        target < 0.45f -> AgTheme.colors.amber
        else -> accent
    }
    val ag = AgTheme.colors

    Box(
        modifier = modifier.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.5.dp.toPx()
                val radius = size.minDimension * 0.36f
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx()), 0f)
                drawCircle(
                    color = ag.border,
                    radius = size.minDimension * 0.47f,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                )
                drawLine(
                    color = ag.grid,
                    start = Offset(center.x - radius - 8.dp.toPx(), center.y),
                    end = Offset(center.x + radius + 8.dp.toPx(), center.y),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = ag.grid,
                    start = Offset(center.x, center.y - radius - 8.dp.toPx()),
                    end = Offset(center.x, center.y + radius + 8.dp.toPx()),
                    strokeWidth = 1f,
                )
                drawCircle(color = ag.track, radius = radius, style = Stroke(width = stroke))
                if (known && animated > 0.004f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    val angle = (-90.0 + 360.0 * animated) * PI / 180.0
                    drawCircle(
                        color = color,
                        radius = 3.dp.toPx(),
                        center = Offset(
                            center.x + radius * cos(angle).toFloat(),
                            center.y + radius * sin(angle).toFloat(),
                        ),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (known) "${(target * 100).roundToInt()}%" else "—",
                    color = if (known) AgTheme.colors.text else AgTheme.colors.muted,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
                Text(
                    text = label.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 0.8.sp,
                    color = AgTheme.colors.muted,
                )
                Text(
                    text = formatCountdownShort(bucket?.resetTime, nowMs) ?: "нет данных",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = AgTheme.colors.muted.copy(alpha = 0.75f),
                )
            }
        }
    }
}
