package dev.agwidgets.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.ContextSummary
import dev.agwidgets.data.QuotaBucket
import dev.agwidgets.ui.contextLevel
import dev.agwidgets.ui.formatCountdownShort
import dev.agwidgets.ui.formatK
import dev.agwidgets.ui.formatWindow
import dev.agwidgets.ui.prettyModel
import dev.agwidgets.ui.theme.AgTheme
import dev.agwidgets.ui.updatedLabel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun HeroCard(
    status: AgStatus?,
    nowMs: Long,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
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

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "01 / CONTEXT",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 1.3.sp,
                color = AgTheme.colors.muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = status?.let { updatedLabel(it.pcUpdatedAt ?: it.updatedAt, nowMs) } ?: "—",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = AgTheme.colors.muted,
            )
            Spacer(Modifier.width(8.dp))
            RefreshButton(isRefreshing = isRefreshing, onClick = onRefresh)
        }
        Text(
            text = "Контекст",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.4).sp),
            color = AgTheme.colors.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuotaRing("GEMINI · 5Ч", fiveHour, nowMs, accent = AgTheme.colors.glow, modifier = Modifier.weight(1f))
            QuotaRing("GEMINI · НЕД", weekly, nowMs, accent = AgTheme.colors.glow, modifier = Modifier.weight(1f))
        }
        if (thirdFiveHour != null || thirdWeekly != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuotaRing(
                    "CLAUDE/GPT · 5Ч",
                    thirdFiveHour,
                    nowMs,
                    accent = AgTheme.colors.pink,
                    modifier = Modifier.weight(1f),
                )
                QuotaRing(
                    "CLAUDE/GPT · НЕД",
                    thirdWeekly,
                    nowMs,
                    accent = AgTheme.colors.pink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ContextHealth(status?.contextSummary)
    }
}

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

@Composable
private fun ContextHealth(context: ContextSummary?) {
    val tokens = context?.tokens
    val window = context?.window
    val pct = context?.pct ?: if (tokens != null && window != null && window > 0) {
        tokens.toDouble() / window * 100.0
    } else {
        null
    }
    val level = contextLevel(pct, context?.level, context?.yellowPct, context?.redPct)
    val color = when (level) {
        "red" -> AgTheme.colors.rose
        "yellow" -> AgTheme.colors.amber
        "green" -> AgTheme.colors.green
        else -> AgTheme.colors.muted
    }
    val yellowPct = context?.yellowPct ?: 40.0
    val redPct = context?.redPct ?: 65.0
    val warnTokens = context?.warnTokens
    val riskTokens = context?.riskTokens

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = context?.model?.let { prettyModel(it) }
                        ?: if (context?.active == true) "Текущий чат" else "Контекст ПК",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        tokens != null && window != null && pct != null ->
                            "${if (context.active) "ТЕКУЩИЙ ЧАТ · " else ""}" +
                                "${formatK(tokens)} / ${formatWindow(window)} · ${pct.roundToInt()}%"
                        tokens != null ->
                            "${if (context.active) "ТЕКУЩИЙ ЧАТ · " else ""}${formatK(tokens)}"
                        context?.active == true -> "ТЕКУЩИЙ ЧАТ · ожидание ответа"
                        else -> "нет данных с ПК"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 0.4.sp,
                    color = AgTheme.colors.muted,
                )
            }
            when {
                level == "red" -> StatusChip("ПОРА /COMPACT", AgTheme.colors.rose)
                level == "yellow" -> StatusChip("ВНИМАНИЕ", AgTheme.colors.amber)
                context?.active == true -> StatusChip("ЧАТ", AgTheme.colors.glow)
            }
        }

        if (pct != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AgTheme.colors.track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((pct / 100.0).toFloat().coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(color),
                )
            }
            if (warnTokens != null && riskTokens != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "НАДЁЖНО ДО ${formatK(warnTokens)} · ${yellowPct.roundToInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        color = AgTheme.colors.muted.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "РИСК ПОСЛЕ ${formatK(riskTokens)} · ${redPct.roundToInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        color = AgTheme.colors.muted.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
