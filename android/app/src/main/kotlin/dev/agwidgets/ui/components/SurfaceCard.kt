package dev.agwidgets.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.ui.theme.AgTheme

/** One widget placed on the launcher, as shown in the Settings surface list. */
internal data class InstalledWidgetInfo(
    val appWidgetId: Int,
    val presetLabel: String,
    val themeLabel: String,
)

@Composable
internal fun SurfaceCard(
    installedWidgets: List<InstalledWidgetInfo> = emptyList(),
    onPinWidget: () -> Unit,
    onConfigureWidget: (Int) -> Unit = {},
) {
    GlassCard {
        SectionHeader(
            serial = "04 / SURFACE",
            title = if (installedWidgets.isEmpty()) {
                "Виджет 4×2"
            } else {
                "Виджет · ${installedWidgets.size}"
            },
            chipText = "WIDGET",
            chipColor = AgTheme.colors.violet,
        )
        if (installedWidgets.isEmpty()) {
            Text(
                text = "Шкалы 5h/неделя, обратный отсчёт, статус ZCode и контекст ПК. " +
                    "Уведомления о сбросе приходят без компьютера.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = if (AgTheme.colors.isClassic) 11.5.sp else 12.sp,
                color = AgTheme.colors.muted,
            )
        } else {
            installedWidgets.forEach { widget ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "#${widget.appWidgetId} · ${widget.presetLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AgTheme.colors.text,
                        )
                        Text(
                            text = widget.themeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = if (AgTheme.colors.isClassic) 11.5.sp else 12.sp,
                            color = AgTheme.colors.muted,
                        )
                    }
                    OutlinedButton(
                        onClick = { onConfigureWidget(widget.appWidgetId) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgTheme.colors.text),
                    ) {
                        Text("Настроить")
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onPinWidget,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AgTheme.colors.text),
        ) {
            Text("Добавить виджет на экран")
        }
    }
}
