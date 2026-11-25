package dev.agwidgets.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.ui.theme.AgTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Card surface.
 *
 * `Classic` keeps the author's glass panel with a hard 5dp offset shadow and a
 * 10dp corner. Every modern theme renders a calm utility surface: 14dp corners,
 * a 1dp border, no offset shadow and a roomier 16dp padding.
 */
@Composable
internal fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = AgTheme.colors
    if (colors.isClassic) {
        val shape = RoundedCornerShape(10.dp)
        Box(modifier = Modifier.fillMaxWidth().padding(end = 5.dp, bottom = 5.dp)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(5.dp, 5.dp)
                    .clip(shape)
                    .background(colors.cardShadow),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.card)
                    .border(1.dp, colors.border, shape)
                    .padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                content = content,
            )
        }
    } else {
        val shape = RoundedCornerShape(colors.cardCornerDp.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.card)
                .border(1.dp, colors.border, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * Card header.
 *
 * Classic keeps the monospace serial (`02 / CHANNEL`) above the title; modern
 * themes drop the technical code and show a single readable title row with the
 * status chip.
 */
@Composable
internal fun SectionHeader(
    serial: String,
    title: String,
    chipText: String,
    chipColor: Color,
) {
    val colors = AgTheme.colors
    if (colors.isClassic) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = serial,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 1.3.sp,
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(chipText, chipColor)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = colors.text,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            StatusChip(chipText, chipColor)
        }
    }
}

/** Small state chip; 11sp on modern themes, the original 9sp on Classic. */
@Composable
internal fun StatusChip(text: String, color: Color) {
    val classic = AgTheme.colors.isClassic
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = if (classic) 9.sp else 11.sp,
            letterSpacing = if (classic) 0.8.sp else 0.4.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
internal fun glassFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AgTheme.colors.text,
    unfocusedTextColor = AgTheme.colors.text,
    focusedBorderColor = accent,
    unfocusedBorderColor = AgTheme.colors.border,
    focusedLabelColor = accent,
    unfocusedLabelColor = AgTheme.colors.muted,
    cursorColor = accent,
)

@Composable
internal fun RefreshButton(isRefreshing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(AgTheme.colors.card)
            .border(1.dp, AgTheme.colors.border, shape)
            .clickable(enabled = !isRefreshing, onClick = onClick)
            .semantics { contentDescription = "Обновить данные" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isRefreshing) "…" else "⟳",
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = if (isRefreshing) AgTheme.colors.muted else AgTheme.colors.text,
        )
    }
}

/** Opens the separate Settings screen; 48dp touch target. */
@Composable
internal fun SettingsButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(AgTheme.colors.card)
            .border(1.dp, AgTheme.colors.border, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Настройки" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⚙",
            fontSize = 16.sp,
            color = AgTheme.colors.text,
        )
    }
}

/** Kept for backwards compatibility; the header uses [SettingsButton] instead. */
@Composable
internal fun ThemeToggleButton(darkTheme: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val ag = AgTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(ag.card)
            .border(1.dp, ag.border, shape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (darkTheme) "Включить светлую тему" else "Включить тёмную тему"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            if (darkTheme) {
                drawCircle(ag.text, radius = size.minDimension * 0.22f)
                val inner = size.minDimension * 0.34f
                val outer = size.minDimension * 0.46f
                repeat(8) { index ->
                    val angle = index * PI / 4.0
                    drawLine(
                        color = ag.text,
                        start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                        end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            } else {
                drawCircle(ag.text, radius = size.minDimension * 0.42f)
                drawCircle(
                    color = ag.card,
                    radius = size.minDimension * 0.36f,
                    center = Offset(center.x + size.minDimension * 0.18f, center.y - size.minDimension * 0.12f),
                )
            }
        }
    }
}
