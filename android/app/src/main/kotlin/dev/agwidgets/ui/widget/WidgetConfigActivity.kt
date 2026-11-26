package dev.agwidgets.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dev.agwidgets.ui.components.GlassCard
import dev.agwidgets.ui.theme.AGWidgetsTheme
import dev.agwidgets.ui.theme.AgTheme
import dev.agwidgets.widget.LimitsWidget
import dev.agwidgets.widget.WidgetBlockConfig
import dev.agwidgets.widget.WidgetBlockId
import dev.agwidgets.widget.WidgetConfig
import dev.agwidgets.widget.WidgetConfigCodec
import dev.agwidgets.widget.WidgetConfigStore
import dev.agwidgets.widget.WidgetDensity
import dev.agwidgets.widget.WidgetPresets
import dev.agwidgets.widget.WidgetThemes
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * Per-widget configuration screen started by the launcher with
 * [AppWidgetManager.EXTRA_APPWIDGET_ID].
 *
 * It keeps a pinned live preview, edits a [WidgetConfig] and, on save, refreshes
 * exactly the configured Glance instance (falling back to `updateAll` when the
 * launcher does not hand out a glance id).
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Until the user confirms, the widget must keep its previous settings.
        setResult(RESULT_CANCELED)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val appContext = applicationContext
        setContent {
            var config by remember { mutableStateOf(WidgetConfigStore.get(appContext, appWidgetId)) }
            AGWidgetsTheme(themeMode = config.theme) {
                WidgetConfigScreen(
                    config = config,
                    onChange = { config = it },
                    onSave = { saveAndRefresh(appWidgetId, config) },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun saveAndRefresh(appWidgetId: Int, config: WidgetConfig) {
        WidgetConfigStore.save(this, appWidgetId, config)
        val manager = GlanceAppWidgetManager(this)
        val refreshed = runCatching {
            runBlocking {
                LimitsWidget().update(this@WidgetConfigActivity, manager.getGlanceIdBy(appWidgetId))
            }
        }.isSuccess
        if (!refreshed) {
            runCatching { runBlocking { LimitsWidget().updateAll(this@WidgetConfigActivity) } }
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@Composable
private fun WidgetConfigScreen(
    config: WidgetConfig,
    onChange: (WidgetConfig) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AgTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        // Pinned preview: stays visible while the builder below scrolls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Настройка виджета",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) {
                    Text(text = "Отмена", color = colors.muted)
                }
            }
            WidgetLivePreview(config = config, modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PresetsSection(config = config, onChange = onChange)
            BlocksSection(config = config, onChange = onChange)
            StyleSection(config = config, onChange = onChange)
            JsonSection(config = config, onChange = onChange)
            TextButton(
                onClick = { onChange(WidgetConfig()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Сбросить", color = colors.muted)
            }
            Spacer(Modifier.height(4.dp))
        }

        // Pinned primary action.
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Сохранить виджет")
                }
            }
        }
    }
}

@Composable
private fun ConfigSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = AgTheme.colors.text,
        )
        content()
    }
}

@Composable
private fun PresetsSection(config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    ConfigSection(title = "Пресеты") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WidgetPresets.all.forEach { preset ->
                FilterChip(
                    selected = preset.config == config,
                    onClick = { onChange(preset.config) },
                    label = { Text(text = preset.label) },
                )
            }
        }
    }
}

@Composable
private fun BlocksSection(config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    ConfigSection(title = "Блоки") {
        config.blocks.forEachIndexed { index, block ->
            BlockRow(
                block = block,
                title = blockTitle(block.id),
                canMoveUp = index > 0,
                canMoveDown = index < config.blocks.lastIndex,
                onMoveUp = { onChange(config.moveBlock(index, index - 1)) },
                onMoveDown = { onChange(config.moveBlock(index, index + 1)) },
                onToggle = { enabled ->
                    onChange(config.updateBlock(block.id) { it.copy(enabled = enabled) })
                },
                onOption = { key, value ->
                    onChange(
                        config.updateBlock(block.id) {
                            it.copy(options = it.options + (key to value))
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun BlockRow(
    block: WidgetBlockConfig,
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onOption: (String, Boolean) -> Unit,
) {
    val colors = AgTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.semantics {
                    contentDescription = "Переместить «$title» вверх"
                },
            ) {
                Text(text = "↑", fontSize = 16.sp, color = if (canMoveUp) colors.text else colors.muted)
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.semantics {
                    contentDescription = "Переместить «$title» вниз"
                },
            ) {
                Text(text = "↓", fontSize = 16.sp, color = if (canMoveDown) colors.text else colors.muted)
            }
            Switch(checked = block.enabled, onCheckedChange = onToggle)
        }
        when (block.id) {
            WidgetBlockId.CONTEXT -> OptionRow(
                label = "Чип /compact",
                checked = block.options["show_compact_chip"] ?: true,
                onCheckedChange = { onOption("show_compact_chip", it) },
            )
            WidgetBlockId.QUOTAS -> {
                OptionRow("Gemini", block.options["show_gemini"] ?: true) {
                    onOption("show_gemini", it)
                }
                OptionRow("Claude / GPT", block.options["show_3p"] ?: true) {
                    onOption("show_3p", it)
                }
                OptionRow("Таймер сброса", block.options["show_countdown"] ?: true) {
                    onOption("show_countdown", it)
                }
            }
            WidgetBlockId.ZCODE -> OptionRow(
                label = "Полоса баланса",
                checked = block.options["show_balance_bar"] ?: true,
                onCheckedChange = { onOption("show_balance_bar", it) },
            )
            WidgetBlockId.HEADER -> Unit
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = AgTheme.colors.secondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StyleSection(config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    val colors = AgTheme.colors
    val selectedTheme = WidgetThemes.normalize(config.theme)
    val selectedAccent = WidgetConfigCodec.normalizeHexAccent(config.customAccent)
    ConfigSection(title = "Стиль") {
        Text(text = "Тема", color = colors.secondary, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WidgetThemes.all.forEach { theme ->
                FilterChip(
                    selected = theme == selectedTheme,
                    onClick = { onChange(config.copy(theme = theme)) },
                    label = { Text(text = themeLabel(theme)) },
                )
            }
        }
        Text(text = "Акцент", color = colors.secondary, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentDot(
                color = null,
                selected = selectedAccent == null,
                label = "Акцент по умолчанию",
                onClick = { onChange(config.copy(customAccent = null)) },
            )
            accentChoices.forEach { hex ->
                AccentDot(
                    color = Color(android.graphics.Color.parseColor(hex)),
                    selected = selectedAccent == hex,
                    label = "Акцент $hex",
                    onClick = { onChange(config.copy(customAccent = hex)) },
                )
            }
        }
        LabeledSlider(
            title = "Прозрачность",
            valueLabel = "${(config.opacity * 100).roundToInt()}%",
            value = config.opacity,
            valueRange = 0f..1f,
            steps = 19,
            onValueChange = { onChange(config.copy(opacity = it)) },
        )
        LabeledSlider(
            title = "Скругление",
            valueLabel = "${config.cornerRadiusDp} dp",
            value = config.cornerRadiusDp.toFloat(),
            valueRange = 0f..24f,
            steps = 11,
            onValueChange = { onChange(config.copy(cornerRadiusDp = it.roundToInt())) },
        )
        LabeledSlider(
            title = "Масштаб текста",
            valueLabel = "${(config.fontScale * 100).roundToInt()}%",
            value = config.fontScale,
            valueRange = 0.85f..1.4f,
            steps = 10,
            onValueChange = { onChange(config.copy(fontScale = it)) },
        )
        Text(text = "Плотность", color = colors.secondary, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetDensity.all.forEach { density ->
                FilterChip(
                    selected = config.density == density,
                    onClick = { onChange(config.copy(density = density)) },
                    label = {
                        Text(
                            text = densityLabel(density),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AccentDot(color: Color?, selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = AgTheme.colors
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (selected) colors.glow.copy(alpha = 0.22f) else Color.Transparent)
            .border(2.dp, if (selected) colors.glow else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color ?: colors.surface)
                .border(1.dp, colors.border, CircleShape),
        )
        if (color == null) {
            Text(text = "A", fontSize = 9.sp, color = colors.muted)
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    val colors = AgTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = colors.secondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun JsonSection(config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    val context = LocalContext.current
    var importOpen by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }

    ConfigSection(title = "Экспорт / импорт") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText(
                            "AG Widgets",
                            WidgetConfigCodec.exportJson(config),
                        ),
                    )
                    Toast.makeText(context, "JSON скопирован", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Экспорт JSON", maxLines = 1)
            }
            OutlinedButton(
                onClick = { importOpen = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Импорт JSON", maxLines = 1)
            }
        }
    }

    if (importOpen) {
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text(text = "Импорт JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = {
                            importText = it
                            importError = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        label = { Text(text = "Конфигурация") },
                        isError = importError,
                        singleLine = false,
                        maxLines = 12,
                    )
                    if (importError) {
                        Text(
                            text = "Некорректный JSON",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val imported = WidgetConfigCodec.importJson(importText)
                        if (imported == null) {
                            importError = true
                        } else {
                            onChange(imported)
                            importOpen = false
                        }
                    },
                ) {
                    Text(text = "Применить")
                }
            },
            dismissButton = {
                TextButton(onClick = { importOpen = false }) {
                    Text(text = "Отмена")
                }
            },
        )
    }
}

private fun WidgetConfig.updateBlock(
    id: WidgetBlockId,
    transform: (WidgetBlockConfig) -> WidgetBlockConfig,
): WidgetConfig = copy(blocks = blocks.map { if (it.id == id) transform(it) else it })

private fun WidgetConfig.moveBlock(from: Int, to: Int): WidgetConfig {
    if (from == to || from !in blocks.indices || to !in blocks.indices) return this
    val reordered = blocks.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(blocks = reordered)
}

private fun blockTitle(id: WidgetBlockId): String = when (id) {
    WidgetBlockId.HEADER -> "Шапка"
    WidgetBlockId.CONTEXT -> "Контекст"
    WidgetBlockId.QUOTAS -> "Лимиты"
    WidgetBlockId.ZCODE -> "ZCode"
}

private fun themeLabel(theme: String): String = when (theme) {
    WidgetThemes.SYSTEM -> "Авто"
    WidgetThemes.GRAPHITE -> "Graphite"
    WidgetThemes.PAPER -> "Paper"
    WidgetThemes.OLED -> "OLED"
    WidgetThemes.GLASS -> "Glass"
    WidgetThemes.CLASSIC -> "Classic"
    else -> "Авто"
}

private fun densityLabel(density: WidgetDensity): String = when (density) {
    WidgetDensity.COMPACT -> "Компактно"
    WidgetDensity.COMFORTABLE -> "Обычно"
    WidgetDensity.SPACIOUS -> "Просторно"
}

private val accentChoices = listOf(
    "#60a5fa",
    "#34d399",
    "#a78bfa",
    "#f59e0b",
    "#f43f5e",
    "#38bdf8",
)
