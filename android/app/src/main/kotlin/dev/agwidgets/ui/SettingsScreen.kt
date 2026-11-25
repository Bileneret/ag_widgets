package dev.agwidgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.ui.components.GlassCard
import dev.agwidgets.ui.components.SectionHeader
import dev.agwidgets.ui.theme.AgTheme
import dev.agwidgets.ui.theme.AgThemeMode
import dev.agwidgets.ui.theme.normalizeThemeMode

/** One selectable theme chip: stored id, human label and its preview dot. */
private data class ThemeChoice(
    val id: String,
    val label: String,
    val dotColor: Color,
    val dotEndColor: Color? = null,
)

private val themeChoices = listOf(
    // "Авто" previews both outcomes: dark graphite half / light paper half.
    ThemeChoice(AgThemeMode.SYSTEM, "Авто", Color(0xFF1B1E23), Color(0xFFFAF9F6)),
    ThemeChoice(AgThemeMode.GRAPHITE, "Graphite", Color(0xFF1B1E23)),
    ThemeChoice(AgThemeMode.PAPER, "Paper", Color(0xFFFAF9F6)),
    ThemeChoice(AgThemeMode.OLED, "OLED", Color(0xFF000000)),
    ThemeChoice(AgThemeMode.GLASS, "Glass", Color(0xFF7DD3FC)),
    ThemeChoice(AgThemeMode.CLASSIC, "Classic", Color(0xFFFFE9B8)),
)

private fun themeLabel(id: String): String =
    themeChoices.firstOrNull { it.id == id }?.label ?: "Авто"

/**
 * Settings surface: theme picker first, then whatever configuration cards the
 * caller passes in (channel, ZCode access, widget, diagnostics).
 */
@Composable
internal fun SettingsScreen(
    themeMode: String,
    onSelectTheme: (String) -> Unit,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = AgTheme.colors
    val selected = normalizeThemeMode(themeMode)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
            )
        }

        GlassCard {
            SectionHeader(
                serial = "06 / THEME",
                title = "Тема оформления",
                chipText = themeLabel(selected).uppercase(),
                chipColor = colors.glow,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    themeChoices.take(3).forEach { choice ->
                        ThemeChoiceButton(
                            choice = choice,
                            selected = choice.id == selected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTheme(choice.id) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    themeChoices.drop(3).forEach { choice ->
                        ThemeChoiceButton(
                            choice = choice,
                            selected = choice.id == selected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTheme(choice.id) },
                        )
                    }
                }
            }
            Text(
                text = "Classic — оригинальный стиль автора",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }

        content()
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val colors = AgTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Назад" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "←", fontSize = 18.sp, color = colors.text)
    }
}

@Composable
private fun ThemeChoiceButton(
    choice: ThemeChoice,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AgTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(if (selected) colors.glow.copy(alpha = 0.14f) else colors.surface)
            .border(1.dp, if (selected) colors.glow else colors.border, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Тема ${choice.label}" }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeDot(choice)
        Spacer(Modifier.width(6.dp))
        Text(
            text = choice.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.text else colors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThemeDot(choice: ThemeChoice) {
    val shape = CircleShape
    val end = choice.dotEndColor
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(shape)
            .then(
                if (end != null) {
                    Modifier.background(Brush.horizontalGradient(listOf(choice.dotColor, end)), shape)
                } else {
                    Modifier.background(choice.dotColor, shape)
                },
            )
            .border(1.dp, AgTheme.colors.border, shape),
    ) {}
}
