package dev.agwidgets.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agwidgets.R
import dev.agwidgets.ui.components.GlassCard
import dev.agwidgets.ui.components.SectionHeader
import dev.agwidgets.ui.theme.AgTheme

/** First-run choice: ZCode-only or connected to a PC with Antigravity. */
@Composable
internal fun OnboardingScreen(
    zcodeAvailable: Boolean,
    onChoosePc: () -> Unit,
    onChooseZcode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_mark),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AgTheme.colors.text,
                )
                Text(
                    text = "ANTIGRAVITY · 001",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 1.1.sp,
                    color = AgTheme.colors.muted,
                )
            }
        }

        Text(
            text = "Как вы будете пользоваться?",
            style = MaterialTheme.typography.titleLarge,
            color = AgTheme.colors.text,
        )
        Text(
            text = if (zcodeAvailable) {
                "Режим можно сменить позже в разделе «Диагностика»."
            } else {
                "AG Context работает вместе с коллектором на компьютере."
            },
            style = MaterialTheme.typography.bodySmall,
            color = AgTheme.colors.muted,
        )

        if (zcodeAvailable) {
            GlassCard {
                SectionHeader(
                    serial = "01 / ZCODE",
                    title = "Только ZCode",
                    chipText = "БЕЗ ПК",
                    chipColor = AgTheme.colors.glow,
                )
                Text(
                    text = "Ваучеры на 300–500M токенов и баланс. Компьютер не нужен: " +
                        "приложение само проверяет ваучеры.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AgTheme.colors.muted,
                )
                Button(
                    onClick = onChooseZcode,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgTheme.colors.glow,
                        contentColor = AgTheme.colors.onGlow,
                    ),
                ) {
                    Text("Выбрать ZCode")
                }
            }
        }

        GlassCard {
            SectionHeader(
                serial = if (zcodeAvailable) "02 / PC" else "01 / PC",
                title = "С компьютером",
                chipText = "ANTIGRAVITY",
                chipColor = AgTheme.colors.violet,
            )
            Text(
                text = if (zcodeAvailable) {
                    "Контекст открытого чата Antigravity, лимиты 5ч/неделя и ZCode. " +
                        "Нужен компьютер с установленным коллектором."
                } else {
                    "Контекст открытого чата Antigravity и лимиты 5ч/неделя. " +
                        "Нужен компьютер с установленным коллектором."
                },
                style = MaterialTheme.typography.bodySmall,
                color = AgTheme.colors.muted,
            )
            OutlinedButton(
                onClick = onChoosePc,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AgTheme.colors.text),
            ) {
                Text("Выбрать ПК")
            }
        }

        Text(
            text = if (zcodeAvailable) {
                "AG Widgets · Antigravity + ZCode"
            } else {
                "AG Context · только Antigravity"
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp,
            color = AgTheme.colors.muted.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
