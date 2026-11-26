package dev.agwidgets.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.ui.theme.AgTheme

@Composable
internal fun DiagnosticsCard(
    rawJson: String?,
    lastError: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChangeMode: (() -> Unit)? = null,
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "05 / SYSTEM",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 1.3.sp,
                    color = AgTheme.colors.muted,
                )
                Text(
                    text = "Диагностика",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = AgTheme.colors.text,
                )
            }
            if (onChangeMode != null) {
                TextButton(onClick = onChangeMode) {
                    Text(
                        text = "РЕЖИМ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                        color = AgTheme.colors.muted,
                    )
                }
            }
            TextButton(onClick = onToggle) {
                Text(
                    text = if (expanded) "СКРЫТЬ" else "JSON",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    color = AgTheme.colors.glow,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Text(
                    text = rawJson ?: "Данных пока нет — нажмите «Обновить»",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = AgTheme.colors.muted,
                )
                lastError?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        text = "Последняя ошибка: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = AgTheme.colors.amber,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
