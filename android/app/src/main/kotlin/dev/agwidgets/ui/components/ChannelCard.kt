package dev.agwidgets.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.agwidgets.ui.theme.AgDim
import dev.agwidgets.ui.theme.AgTheme
import dev.agwidgets.ui.theme.AgType

/**
 * ntfy channel settings: topic, optional self-hosted server, the E2E key entry
 * points (paste a pairing code / reset the key) and the manual refresh.
 *
 * Pairing itself is confirmed by the caller: this card only collects the code
 * and validates the server field before anything is persisted.
 */
@Composable
internal fun ChannelCard(
    topic: String,
    onTopicChange: (String) -> Unit,
    server: String,
    onServerChange: (String) -> Unit,
    hasE2eKey: Boolean,
    isRefreshing: Boolean,
    statusMessage: String,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPairing: (String) -> Unit,
    onResetE2eKey: () -> Unit,
) {
    val colors = AgTheme.colors
    var showPairingInput by remember { mutableStateOf(false) }
    var pairingText by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }

    GlassCard {
        SectionHeader(
            serial = "02 / CHANNEL",
            title = "Antigravity",
            chipText = when {
                hasE2eKey -> "E2E"
                topic.isBlank() -> "Не настроен"
                else -> "ntfy"
            },
            chipColor = when {
                hasE2eKey -> colors.green
                topic.isBlank() -> colors.muted
                else -> colors.glow
            },
        )
        Text(
            text = "Квоты 5 ч и недели, контекст ПК и статус ZCode приходят с компьютера " +
                "через приватный ntfy-канал.",
            style = AgType.body,
            color = colors.secondary,
        )
        OutlinedTextField(
            value = topic,
            onValueChange = onTopicChange,
            singleLine = true,
            label = { Text("Тема ntfy", style = AgType.caption) },
            textStyle = AgType.body,
            shape = RoundedCornerShape(AgDim.ControlRadius),
            colors = glassFieldColors(colors.glow),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = server,
            onValueChange = onServerChange,
            singleLine = true,
            label = { Text("Сервер ntfy", style = AgType.caption) },
            placeholder = { Text("https://ntfy.sh", style = AgType.caption) },
            textStyle = AgType.body,
            shape = RoundedCornerShape(AgDim.ControlRadius),
            colors = glassFieldColors(colors.glow),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Только https://. Пустое значение вернёт https://ntfy.sh",
            style = AgType.caption,
            color = colors.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AgDim.ControlGap),
        ) {
            // Field-local save is a quiet action; "Обновить" is tonal so the one
            // filled primary on the screen stays "Войти в Z.ai".
            AgGhostButton(text = "Сохранить", onClick = onSave)
            FilledTonalButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                shape = RoundedCornerShape(AgDim.ControlRadius),
                modifier = Modifier.weight(1f).height(AgDim.MinTouch),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.glow.copy(alpha = 0.16f),
                    contentColor = colors.accentInk,
                ),
            ) {
                Text(
                    text = if (isRefreshing) "Обновляю…" else "Обновить",
                    style = AgType.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Paste-by-hand fallback for phones that cannot open the agw:// link.
        AgGhostButton(
            text = "Вставить код сопряжения",
            onClick = { showPairingInput = true },
        )
        if (hasE2eKey) {
            AgGhostButton(
                text = "Сбросить E2E-ключ",
                onClick = { showResetConfirm = true },
                contentColor = colors.rose,
            )
        }
        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                style = AgType.body,
                color = colors.secondary,
            )
        }
    }

    if (showPairingInput) {
        AlertDialog(
            onDismissRequest = { showPairingInput = false },
            shape = RoundedCornerShape(AgDim.Page),
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.secondary,
            title = { Text("Вставить код сопряжения", style = AgType.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AgDim.ControlGap)) {
                    OutlinedTextField(
                        value = pairingText,
                        onValueChange = { pairingText = it },
                        minLines = 2,
                        label = { Text("Код agw://pair?...", style = AgType.caption) },
                        textStyle = AgType.body,
                        shape = RoundedCornerShape(AgDim.ControlRadius),
                        colors = glassFieldColors(colors.glow),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = pairingText.trim()
                        showPairingInput = false
                        pairingText = ""
                        onRequestPairing(value)
                    },
                    enabled = pairingText.isNotBlank(),
                    shape = RoundedCornerShape(AgDim.ControlRadius),
                    modifier = Modifier.height(AgDim.MinTouch),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.glow,
                        contentColor = colors.onGlow,
                    ),
                ) {
                    Text("Далее", style = AgType.body)
                }
            },
            dismissButton = {
                AgGhostButton(text = "Отмена", onClick = { showPairingInput = false })
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            shape = RoundedCornerShape(AgDim.Page),
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.secondary,
            title = { Text("Сбросить E2E-ключ?", style = AgType.title) },
            text = {
                Text(
                    text = "Телефон перестанет расшифровывать сообщения, пока вы " +
                        "не отсканируете новый QR-код.",
                    style = AgType.body,
                    color = colors.secondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetE2eKey()
                    },
                    shape = RoundedCornerShape(AgDim.ControlRadius),
                    modifier = Modifier.height(AgDim.MinTouch),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.riskStrong,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Сбросить", style = AgType.body)
                }
            },
            dismissButton = {
                AgGhostButton(text = "Отмена", onClick = { showResetConfirm = false })
            },
        )
    }
}
