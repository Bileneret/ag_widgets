package dev.agwidgets.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.agwidgets.BuildConfig
import dev.agwidgets.ui.theme.AgTheme

@Composable
internal fun ChannelCard(
    topic: String,
    onTopicChange: (String) -> Unit,
    isRefreshing: Boolean,
    statusMessage: String,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
) {
    GlassCard {
        SectionHeader(
            serial = "02 / CHANNEL",
            title = "Antigravity",
            chipText = if (topic.isBlank()) "НЕ НАСТРОЕН" else "NTFY",
            chipColor = if (topic.isBlank()) AgTheme.colors.muted else AgTheme.colors.glow,
        )
        Text(
            text = if (BuildConfig.ZCODE_ENABLED) {
                "Квоты 5h/неделя, контекст ПК и статус ZCode приходят с компьютера " +
                    "через приватный ntfy-канал."
            } else {
                "Квоты 5h/неделя и контекст ПК приходят с компьютера через приватный ntfy-канал."
            },
            style = MaterialTheme.typography.bodySmall,
            color = AgTheme.colors.muted,
        )
        OutlinedTextField(
            value = topic,
            onValueChange = onTopicChange,
            singleLine = true,
            label = { Text("Тема ntfy") },
            colors = glassFieldColors(AgTheme.colors.glow),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AgTheme.colors.text),
            ) {
                Text("Сохранить")
            }
            Button(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgTheme.colors.glow,
                    contentColor = AgTheme.colors.onGlow,
                ),
            ) {
                Text(if (isRefreshing) "Обновляю…" else "Обновить")
            }
        }
        if (statusMessage.isNotBlank()) {
            Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = AgTheme.colors.glow)
        }
    }
}
