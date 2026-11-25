package dev.agwidgets.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.R
import dev.agwidgets.data.AgStatus
import dev.agwidgets.ui.statusAgeMinutes
import dev.agwidgets.ui.theme.AgTheme

@Composable
internal fun Header(
    status: AgStatus?,
    nowMs: Long,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val age = statusAgeMinutes(status?.updatedAt, nowMs)
    val (chipText, chipColor) = when {
        status == null -> "НЕТ ДАННЫХ" to AgTheme.colors.muted
        age == null -> "—" to AgTheme.colors.muted
        age < 15 -> "LIVE" to AgTheme.colors.green
        age < 120 -> "ТИХО" to AgTheme.colors.amber
        else -> "СТАРО" to AgTheme.colors.muted
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_mark),
            contentDescription = null,
            modifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AG WIDGETS",
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.5).sp),
                color = AgTheme.colors.text,
            )
            Text(
                text = "ANTIGRAVITY · ZCODE · 001",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 1.1.sp,
                color = AgTheme.colors.muted,
            )
        }
        StatusChip(chipText, chipColor)
        Spacer(Modifier.width(8.dp))
        ThemeToggleButton(darkTheme = darkTheme, onClick = onToggleTheme)
    }
}
