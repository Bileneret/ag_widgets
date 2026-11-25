package dev.agwidgets.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.data.ContextSummary
import dev.agwidgets.ui.contextLevel
import dev.agwidgets.ui.formatK
import dev.agwidgets.ui.formatWindow
import dev.agwidgets.ui.prettyModel
import dev.agwidgets.ui.theme.AgTheme
import dev.agwidgets.ui.updatedLabel
import kotlin.math.roundToInt

/**
 * Dashboard block 1 — context window importance.
 *
 * Shows the model, the used token count as the hero number, the fill bar and
 * the two thresholds, without ever pretending a missing value is 0%.
 */
@Composable
internal fun ContextCard(context: ContextSummary?, updatedAt: String?, nowMs: Long) {
    val colors = AgTheme.colors
    val tokens = context?.tokens
    val window = context?.window
    val pct = context?.pct?.takeIf { it.isFinite() }
        ?: if (tokens != null && window != null && window > 0) {
            tokens.toDouble() / window * 100.0
        } else {
            null
        }
    val level = contextLevel(pct, context?.level, context?.yellowPct, context?.redPct)
    val levelColor = when (level) {
        "red" -> colors.rose
        "yellow" -> colors.amber
        "green" -> colors.green
        else -> colors.muted
    }
    val chipText = when (level) {
        "red" -> "ПОРА /COMPACT"
        "yellow" -> "ВНИМАНИЕ"
        "green" -> "OK"
        else -> "ОЖИДАНИЕ"
    }
    val yellowPct = context?.yellowPct?.takeIf { it.isFinite() } ?: 40.0
    val redPct = context?.redPct?.takeIf { it.isFinite() } ?: 65.0
    val warnTokens = context?.warnTokens
    val riskTokens = context?.riskTokens

    GlassCard {
        SectionHeader(
            serial = "01 / CONTEXT",
            title = "Контекст",
            chipText = chipText,
            chipColor = levelColor,
        )

        if (tokens == null) {
            Text(
                text = if (context?.active == true) {
                    "Текущий чат · ожидание ответа"
                } else {
                    "Нет данных с ПК — настройте канал ntfy в ⚙"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondary,
            )
        } else {
            Text(
                text = context.model?.let { prettyModel(it) } ?: "Текущий чат",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatK(tokens),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = colors.text,
                )
                if (window != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "из ${formatWindow(window)}",
                        fontSize = 13.sp,
                        color = colors.secondary,
                    )
                }
                if (pct != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${pct.roundToInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = levelColor,
                    )
                }
            }
            if (pct != null) {
                ContextBar(
                    fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f),
                    color = levelColor,
                )
            }
            if (warnTokens != null && riskTokens != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Надёжно до ${formatK(warnTokens)} (${yellowPct.roundToInt()}%)",
                        fontSize = 11.5.sp,
                        color = colors.muted,
                    )
                    Text(
                        text = "Риск после ${formatK(riskTokens)} (${redPct.roundToInt()}%)",
                        fontSize = 11.5.sp,
                        color = colors.muted,
                    )
                }
            }
        }

        if (updatedAt != null) {
            val label = updatedLabel(updatedAt, nowMs)
            if (label != "—") {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun ContextBar(fraction: Float, color: Color) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(AgTheme.colors.track),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(shape)
                    .background(color),
            )
        }
    }
}
