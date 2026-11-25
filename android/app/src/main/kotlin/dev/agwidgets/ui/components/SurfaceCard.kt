package dev.agwidgets.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.agwidgets.ui.theme.AgTheme

@Composable
internal fun SurfaceCard(onPinWidget: () -> Unit) {
    GlassCard {
        SectionHeader(
            serial = "04 / SURFACE",
            title = "Виджет 4×2",
            chipText = "WIDGET",
            chipColor = AgTheme.colors.violet,
        )
        Text(
            text = "Кольца 5h/неделя, обратный отсчёт, статус ZCode и контекст ПК. " +
                "Уведомления о сбросе приходят без компьютера.",
            style = MaterialTheme.typography.bodySmall,
            color = AgTheme.colors.muted,
        )
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
