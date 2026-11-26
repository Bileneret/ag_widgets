package dev.agwidgets.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.data.ZCodeStatus
import dev.agwidgets.ui.formatDateShort
import dev.agwidgets.ui.formatUnits
import dev.agwidgets.ui.theme.AgTheme

@Composable
internal fun AccessCard(
    zcode: ZCodeStatus?,
    token: String,
    onTokenChange: (String) -> Unit,
    isPending: Boolean,
    message: String,
    onLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
) {
    val (chipText, chipColor) = zcodeChip(zcode)
    val planSuffix = zcode?.planName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    GlassCard {
        SectionHeader(
            serial = "03 / ACCESS",
            title = "ZCode$planSuffix",
            chipText = chipText,
            chipColor = chipColor,
        )
        Text(
            text = "Прямой опрос zcode.z.ai каждые 15 минут: ваучеры на 300–500M токенов и баланс " +
                "приходят даже при выключенном компьютере.",
            style = MaterialTheme.typography.bodySmall,
            color = AgTheme.colors.muted,
        )
        if (zcode?.remainingUnits != null) {
            BalanceBar(zcode)
        }
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
            ),
            label = { Text("Или вставьте JWT-токен вручную") },
            colors = glassFieldColors(AgTheme.colors.violet),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onLogin,
                enabled = !isPending,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgTheme.colors.violet,
                    contentColor = Color.White,
                ),
            ) {
                Text(if (isPending) "Ожидание…" else "Войти в Z.ai")
            }
            if (isPending) {
                OutlinedButton(
                    onClick = onCancelLogin,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AgTheme.colors.text),
                ) {
                    Text("Отмена")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onSaveToken,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AgTheme.colors.text),
            ) {
                Text("Сохранить")
            }
            if (token.isNotBlank()) {
                OutlinedButton(
                    onClick = onClearToken,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AgTheme.colors.rose),
                ) {
                    Text("Очистить")
                }
            }
        }
        Text(message, style = MaterialTheme.typography.bodySmall, color = AgTheme.colors.secondary)
    }
}

@Composable
private fun BalanceBar(zcode: ZCodeStatus) {
    val remaining = zcode.remainingUnits ?: return
    val total = zcode.totalUnits
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "ОСТАТОК ${formatUnits(remaining)}${total?.let { " / ${formatUnits(it)}" } ?: ""}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                color = AgTheme.colors.text,
            )
            zcode.endsAt?.let {
                Text(
                    text = "ДО ${formatDateShort(it)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = AgTheme.colors.muted,
                )
            }
        }
        if (total != null && total > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AgTheme.colors.track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((remaining.toDouble() / total).toFloat().coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(AgTheme.colors.glow),
                )
            }
        }
    }
}

@Composable
private fun zcodeChip(zcode: ZCodeStatus?): Pair<String, Color> {
    if (zcode == null) return "НЕТ ДАННЫХ" to AgTheme.colors.muted
    if (zcode.isCouponUnknown) {
        return when (zcode.errorKind) {
            "auth_expired" -> "НУЖЕН ВХОД" to AgTheme.colors.amber
            "rate_limited" -> "ЛИМИТ 429" to AgTheme.colors.amber
            "network" -> "НЕТ СВЯЗИ" to AgTheme.colors.rose
            "server_error", "api_changed", "parse_failed" -> "ОШИБКА ПРОВЕРКИ" to AgTheme.colors.rose
            else -> "НЕИЗВЕСТНО" to AgTheme.colors.muted
        }
    }
    if (zcode.networkError) return "НЕТ СВЯЗИ" to AgTheme.colors.rose
    if (zcode.authOk == false) return "НУЖЕН ВХОД" to AgTheme.colors.amber
    if (zcode.method == "disabled") return "НЕ ПОДКЛЮЧЁН" to AgTheme.colors.muted
    if (zcode.isCouponVisible || zcode.couponVisible) return "ВАУЧЕР" to AgTheme.colors.glow
    if (zcode.running) return "АКТИВЕН" to AgTheme.colors.green
    return "НЕ ЗАПУЩЕН" to AgTheme.colors.muted
}
