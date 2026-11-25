package dev.agwidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.agwidgets.BuildConfig
import dev.agwidgets.R
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.ContextSummary
import dev.agwidgets.data.QuotaBucket
import dev.agwidgets.data.StatusStore
import dev.agwidgets.data.ZCodeStatus
import dev.agwidgets.work.RefreshWorker
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        RefreshWorker.runNow(context)
    }
}

class LimitsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val status = StatusStore.getMergedStatus(context)
        val dark = StatusStore.isDarkTheme(context)
        provideContent {
            WidgetContent(status = status, dark = dark)
        }
    }
}

@Composable
private fun WidgetContent(status: AgStatus?, dark: Boolean) {
    val fg = ColorProvider(if (dark) Color.White else Color(0xFF09090B))
    val muted = ColorProvider(if (dark) Color(0xB8FFFFFF) else Color(0xFF52525B))
    val subtle = ColorProvider(if (dark) Color(0x8AFFFFFF) else Color(0xFF6B6B76))
    val accent = if (dark) Color(0xFFFFE9B8) else Color(0xFF7C3AED)
    val divider = if (dark) Color(0x1FFFFFFF) else Color(0x1F09090B)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<RefreshActionCallback>())
    ) {
        Image(
            provider = ImageProvider(if (dark) R.drawable.glass_bg else R.drawable.glass_bg_light),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "●",
                    style = TextStyle(color = ColorProvider(accent), fontSize = 9.sp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = "AGW / 01",
                    style = TextStyle(
                        color = ColorProvider(accent),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.width(7.dp))
                Text(
                    text = "Antigravity" + (status?.planTier?.let { " · $it" } ?: ""),
                    style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = formatTimeShort(status?.updatedAt) ?: "—",
                    style = TextStyle(
                        color = subtle,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(7.dp))
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(divider)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))

            if (status == null) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет данных · откройте приложение",
                        style = TextStyle(color = muted, fontSize = 12.sp)
                    )
                }
            } else {
                val fiveHour = status.buckets.firstOrNull { it.id.startsWith("gemini-") && it.kind == "5h" }
                    ?: status.buckets.firstOrNull { it.kind == "5h" }
                val weekly = status.buckets.firstOrNull { it.id.startsWith("gemini-") && it.kind == "weekly" }
                    ?: status.buckets.firstOrNull { it.kind == "weekly" }
                val thirdFiveHour = status.buckets.firstOrNull { it.id.startsWith("3p-") && it.kind == "5h" }
                val thirdWeekly = status.buckets.firstOrNull { it.id.startsWith("3p-") && it.kind == "weekly" }
                val thirdAccent = if (dark) Color(0xFFF472B6) else Color(0xFFBE185D)

                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        QuotaCell(
                            label = "GEMINI 5H",
                            bucket = fiveHour,
                            fg = fg,
                            muted = muted,
                            subtle = subtle,
                            accent = accent,
                            dark = dark,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(7.dp))
                        QuotaCell(
                            label = "GEMINI WK",
                            bucket = weekly,
                            fg = fg,
                            muted = muted,
                            subtle = subtle,
                            accent = accent,
                            dark = dark,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        QuotaCell(
                            label = "CLAUDE/GPT 5H",
                            bucket = thirdFiveHour,
                            fg = fg,
                            muted = muted,
                            subtle = subtle,
                            accent = thirdAccent,
                            dark = dark,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(7.dp))
                        QuotaCell(
                            label = "CLAUDE/GPT WK",
                            bucket = thirdWeekly,
                            fg = fg,
                            muted = muted,
                            subtle = subtle,
                            accent = thirdAccent,
                            dark = dark,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (BuildConfig.ZCODE_ENABLED) {
                        val (zText, zBg) = zcodeChip(status.zcode, dark)
                        WidgetChip(zText, zBg, fg)
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    contextChip(status.contextSummary, dark)?.let { (text, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = text,
                                style = TextStyle(color = muted, fontSize = 9.5.sp)
                            )
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Text(
                                text = "●",
                                style = TextStyle(color = ColorProvider(color), fontSize = 9.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaCell(
    label: String,
    bucket: QuotaBucket?,
    fg: ColorProvider,
    muted: ColorProvider,
    subtle: ColorProvider,
    accent: Color,
    dark: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    val frac = bucket?.remainingFraction?.toFloat()?.coerceIn(0f, 1f)
    val pct = frac?.let { (it * 100).toInt() }
    val color = when {
        bucket == null -> if (dark) Color(0xFF8F8F9A) else Color(0xFF6B6B76)
        frac != null && frac < 0.2f -> if (dark) Color(0xFFFF4D6D) else Color(0xFFBE123C)
        frac != null && frac < 0.45f -> if (dark) Color(0xFFF5A623) else Color(0xFFB45309)
        else -> accent
    }
    val track = if (dark) Color(0x1FFFFFFF) else Color(0x1F09090B)

    Column(modifier = modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = TextStyle(
                    color = muted,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = if (pct == null) "—" else "$pct%",
                style = TextStyle(color = ColorProvider(color), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Image(
            provider = ImageProvider(BarRenderer.renderBar(frac ?: 0f, color.toArgb(), track.toArgb())),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().height(3.dp)
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = formatCountdownShort(bucket?.resetTime) ?: "нет данных",
            style = TextStyle(
                color = subtle,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

@Composable
private fun WidgetChip(text: String, background: Color, color: ColorProvider) {
    Box(
        modifier = GlanceModifier
            .background(background)
            .cornerRadius(12.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
        )
    }
}

private fun zcodeChip(z: ZCodeStatus?, dark: Boolean): Pair<String, Color> {
    val neutral = if (dark) Color(0x26FFFFFF) else Color(0x0F09090B)
    val error = if (dark) Color(0x45FF4D6D) else Color(0x24BE123C)
    val warn = if (dark) Color(0x45F5A623) else Color(0x24B45309)
    val coupon = if (dark) Color(0x66FFE9B8) else Color(0x1F7C3AED)
    val glm = if (dark) Color(0x33B026FF) else Color(0x1F7C3AED)
    return when {
        z == null -> "ZCode: нет данных" to neutral
        z.networkError -> "ZCode: нет связи" to error
        z.method == "api" && z.authOk == false -> "ZCode: нужен вход" to warn
        z.method == "disabled" -> "ZCode не подключён" to neutral
        z.couponVisible -> "Ваучер доступен!" to coupon
        z.remainingUnits != null && z.remainingUnits > 0 ->
            "GLM ${formatUnits(z.remainingUnits)}" to glm
        !z.running && z.method != "api" -> "ZCode не запущен" to neutral
        else -> "ZCode: нет ваучера" to neutral
    }
}

private fun contextChip(ctx: ContextSummary?, dark: Boolean): Pair<String, Color>? {
    val tokens = ctx?.tokens ?: return null
    val color = when (ctx.level) {
        "red" -> if (dark) Color(0xFFFF4D6D) else Color(0xFFBE123C)
        "yellow" -> if (dark) Color(0xFFF5A623) else Color(0xFFB45309)
        "green" -> if (dark) Color(0xFF4EF2D2) else Color(0xFF047857)
        else -> if (dark) Color(0xFF8F8F9A) else Color(0xFF6B6B76)
    }
    val pct = ctx.pct?.let { " ${it.toInt()}%" } ?: ""
    val prefix = if (ctx.active) "ЧАТ" else "ПК"
    return "$prefix ${formatK(tokens)}$pct" to color
}

private fun formatK(tokens: Int): String = when {
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> "${tokens / 1000}K"
    else -> "$tokens"
}

private fun formatUnits(n: Long): String = when {
    n >= 1_000_000_000L -> "${n / 1_000_000_000L}B"
    n >= 1_000_000L -> "${n / 1_000_000L}M"
    n >= 1_000L -> "${n / 1_000L}K"
    else -> "$n"
}

private fun formatTimeShort(iso: String?): String? {
    val instant = parseInstant(iso) ?: return null
    return DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private fun formatCountdownShort(iso: String?): String? {
    val target = parseInstant(iso) ?: return null
    val totalSeconds = Duration.between(Instant.now(), target).seconds
    if (totalSeconds <= 0) return "сброс сейчас"
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        days > 0 -> "${days}д ${hours}ч"
        hours > 0 -> "${hours}ч ${minutes}м"
        else -> "${minutes}м"
    }
}

private fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (_: Exception) {
        try {
            Instant.parse(iso)
        } catch (_: Exception) {
            null
        }
    }
}
