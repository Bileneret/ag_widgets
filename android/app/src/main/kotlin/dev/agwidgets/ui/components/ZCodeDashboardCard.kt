package dev.agwidgets.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.data.ZCodeStatus
import dev.agwidgets.ui.formatDateShort
import dev.agwidgets.ui.formatUnits
import dev.agwidgets.ui.theme.AgTheme
import kotlin.math.roundToInt

/**
 * Dashboard block 3 — compact ZCode status.
 *
 * Read-only: OAuth login and a link to the Settings screen, never a JWT field.
 * The chip never claims "no coupon" while the state is unknown.
 */
@Composable
internal fun ZCodeDashboardCard(
    zcode: ZCodeStatus?,
    isPending: Boolean,
    message: String,
    onLogin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = AgTheme.colors
    val (chipText, chipColor) = zcodeDashboardChip(zcode)
    val remaining = zcode?.remainingUnits
    val total = zcode?.totalUnits
    val totalSafe = total?.takeIf { it > 0L }
    val planName = zcode?.planName?.takeIf { it.isNotBlank() }
    val showLogin = zcode == null ||
        zcode.method == "disabled" ||
        zcode.authOk == false ||
        (zcode.isCouponUnknown && zcode.errorKind == "auth_expired")

    GlassCard {
        SectionHeader(
            serial = "03 / ACCESS",
            title = "ZCode",
            chipText = chipText,
            chipColor = chipColor,
        )
        if (planName != null) {
            Text(
                text = planName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text,
            )
        }
        if (message.isNotBlank()) {
            Text(
                text = message,
                fontSize = 12.sp,
                color = colors.secondary,
            )
        }
        if (remaining != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (total != null) {
                            "Остаток ${formatUnits(remaining)} / ${formatUnits(total)}"
                        } else {
                            "Остаток ${formatUnits(remaining)}"
                        },
                        fontSize = 12.sp,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (totalSafe != null) {
                        Text(
                            text = "${(remaining.toDouble() / totalSafe * 100).roundToInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.green,
                        )
                    }
                }
                if (totalSafe != null) {
                    ZCodeBar((remaining.toDouble() / totalSafe).toFloat().coerceIn(0f, 1f))
                }
                zcode.endsAt?.let {
                    Text(
                        text = "Действует до ${formatDateShort(it)}",
                        fontSize = 11.5.sp,
                        color = colors.muted,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showLogin) {
                Button(
                    onClick = onLogin,
                    enabled = !isPending,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.violet,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (isPending) "Ожидание входа…" else "Войти в Z.ai")
                }
            }
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = "Настроить",
                    fontSize = 12.sp,
                    color = colors.secondary,
                )
            }
        }
    }
}

@Composable
private fun ZCodeBar(fraction: Float) {
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
                    .background(AgTheme.colors.glow),
            )
        }
    }
}

/** Tri-state chip: "unknown" never degrades into "no coupon". */
@Composable
private fun zcodeDashboardChip(zcode: ZCodeStatus?): Pair<String, Color> {
    val colors = AgTheme.colors
    if (zcode == null) return "НЕТ ДАННЫХ" to colors.muted
    if (zcode.isCouponUnknown) {
        return when (zcode.errorKind) {
            "auth_expired" -> "НУЖЕН ВХОД" to colors.amber
            "rate_limited" -> "ЛИМИТ 429" to colors.amber
            "network" -> "НЕТ СВЯЗИ" to colors.rose
            "server_error", "api_changed", "parse_failed" -> "ОШИБКА ПРОВЕРКИ" to colors.rose
            else -> "НЕИЗВЕСТНО" to colors.muted
        }
    }
    if (zcode.networkError) return "НЕТ СВЯЗИ" to colors.rose
    if (zcode.authOk == false) return "НУЖЕН ВХОД" to colors.amber
    if (zcode.method == "disabled") return "НЕ ПОДКЛЮЧЁН" to colors.muted
    if (zcode.isCouponVisible) return "ВАУЧЕР" to colors.glow
    val remaining = zcode.remainingUnits
    if (remaining != null && (remaining > 0L || (zcode.totalUnits ?: 0L) > 0L)) {
        return "БАЛАНС" to colors.green
    }
    if (zcode.couponState == ZCodeStatus.COUPON_ABSENT) return "НЕТ ВАУЧЕРА" to colors.muted
    return "—" to colors.muted
}
