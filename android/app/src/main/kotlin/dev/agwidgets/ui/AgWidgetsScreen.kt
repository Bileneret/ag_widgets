package dev.agwidgets.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import dev.agwidgets.data.AgStatus
import dev.agwidgets.data.NtfyClient
import dev.agwidgets.data.StatusStore
import dev.agwidgets.data.ZCodeClient
import dev.agwidgets.data.ZCodeOAuthPollResult
import dev.agwidgets.notify.Notifier
import dev.agwidgets.ui.components.AccessCard
import dev.agwidgets.ui.components.ChannelCard
import dev.agwidgets.ui.components.DiagnosticsCard
import dev.agwidgets.ui.components.Header
import dev.agwidgets.ui.components.HeroCard
import dev.agwidgets.ui.components.SurfaceCard
import dev.agwidgets.ui.theme.AgTheme
import dev.agwidgets.widget.LimitsWidget
import dev.agwidgets.widget.LimitsWidgetReceiver
import dev.agwidgets.work.ResetAlertWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** App screen: configuration cards plus the live quota and context dashboard. */
@Composable
internal fun AgWidgetsScreen(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AgTheme.colors

    var topic by remember { mutableStateOf(StatusStore.getTopic(context)) }
    var zcodeToken by remember { mutableStateOf(StatusStore.getZCodeToken(context)) }
    var lastStatus by remember { mutableStateOf<AgStatus?>(StatusStore.getMergedStatus(context)) }
    var statusMessage by remember { mutableStateOf(StatusStore.getLastError(context) ?: "") }
    var zcodeMessage by remember {
        mutableStateOf(if (zcodeToken.isNotBlank()) "Автономная проверка ZCode активна" else "Не подключено")
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var isOAuthPending by remember { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(30_000)
        }
    }

    fun refresh() {
        if (isRefreshing) return
        StatusStore.setTopic(context, topic)
        if (zcodeToken.isNotBlank()) StatusStore.setZCodeToken(context, zcodeToken)
        isRefreshing = true
        statusMessage = "Обновление..."
        scope.launch {
            val messages = mutableListOf<String>()

            if (topic.isNotBlank()) {
                NtfyClient.fetchLatestMessage(topic).fold(
                    onSuccess = { jsonText ->
                        val parsed = AgStatus.fromJson(jsonText)
                        if (parsed != null) {
                            val (previous, current) = StatusStore.saveNewStatus(context, jsonText)
                            if (current != null) {
                                ResetAlertWorker.scheduleForBuckets(context, current.buckets)
                                Notifier.checkAndNotify(context, previous, current)
                            }
                            messages.add("ntfy OK")
                        } else {
                            messages.add("ntfy: неверный JSON")
                        }
                    },
                    onFailure = { messages.add("ntfy: ${it.message}") },
                )
            }

            val token = StatusStore.getZCodeToken(context)
            if (token.isNotBlank()) {
                val deviceMid = StatusStore.getOrCreateDeviceMid(context)
                ZCodeClient.fetchStatus(token, deviceMid).fold(
                    onSuccess = { zcode ->
                        val (previous, current) = StatusStore.saveDirectZCode(context, zcode)
                        Notifier.checkZCodeCouponTransition(context, previous, current)
                        zcodeMessage = when {
                            zcode.authOk == false -> "Токен истёк — войдите снова"
                            zcode.couponVisible -> "Ваучер доступен! (${zcode.matched})"
                            zcode.remainingUnits != null && zcode.remainingUnits > 0 ->
                                "Остаток: ${formatUnits(zcode.remainingUnits)}"
                            else -> "ZCode OK · ваучеров пока нет"
                        }
                        messages.add("ZCode OK")
                    },
                    onFailure = { messages.add("ZCode: ${it.message}") },
                )
            }

            lastStatus = StatusStore.getMergedStatus(context)
            nowMs = System.currentTimeMillis()
            LimitsWidget().updateAll(context)
            statusMessage = if (messages.isEmpty()) {
                "Укажите тему ntfy или войдите в Z.ai"
            } else {
                messages.joinToString(" · ")
            }
            isRefreshing = false
        }
    }

    fun login() {
        if (isOAuthPending) return
        isOAuthPending = true
        zcodeMessage = "Открываю вход Z.ai в браузере…"
        scope.launch {
            ZCodeClient.startOAuthFlow("zai").fold(
                onSuccess = { flow ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(flow.authorizeUrl)))
                    }
                    zcodeMessage = "Завершите вход в браузере и вернитесь сюда…"
                    var completed = false
                    val maxAttempts = (300L / flow.pollIntervalSec).toInt().coerceAtLeast(30)
                    for (attempt in 0 until maxAttempts) {
                        delay(flow.pollIntervalSec * 1000L)
                        when (val poll = ZCodeClient.pollOAuthFlow(flow)) {
                            is ZCodeOAuthPollResult.Pending -> Unit
                            is ZCodeOAuthPollResult.Failed -> {
                                zcodeMessage = poll.reason
                                completed = true
                                break
                            }
                            is ZCodeOAuthPollResult.Ready -> {
                                zcodeToken = poll.token
                                StatusStore.setZCodeToken(context, poll.token)
                                val deviceMid = StatusStore.getOrCreateDeviceMid(context)
                                ZCodeClient.fetchStatus(poll.token, deviceMid).onSuccess { fetched ->
                                    StatusStore.saveDirectZCode(context, fetched)
                                    lastStatus = StatusStore.getMergedStatus(context)
                                    nowMs = System.currentTimeMillis()
                                    LimitsWidget().updateAll(context)
                                }
                                zcodeMessage = "Вход выполнен. Автономный мониторинг включён."
                                completed = true
                                break
                            }
                        }
                    }
                    if (!completed) zcodeMessage = "Таймаут входа — попробуйте снова"
                },
                onFailure = { error -> zcodeMessage = "Ошибка OAuth: ${error.message}" },
            )
            isOAuthPending = false
        }
    }

    fun logout() {
        zcodeToken = ""
        StatusStore.clearZCodeToken(context)
        zcodeMessage = "Токен удалён"
        lastStatus = StatusStore.getMergedStatus(context)
        scope.launch { LimitsWidget().updateAll(context) }
    }

    fun pinWidget() {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, LimitsWidgetReceiver::class.java), null, null)
        } else {
            statusMessage = "Лаунчер не поддерживает автодобавление — добавьте виджет вручную"
        }
    }

    LaunchedEffect(Unit) {
        if (lastStatus == null && (topic.isNotBlank() || zcodeToken.isNotBlank())) refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgTheme.colors.background),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.glowMagenta, Color.Transparent),
                    center = Offset(size.width * 0.10f, size.height * 0.02f),
                    radius = size.width * 0.72f,
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.10f, size.height * 0.02f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.glowBlue, Color.Transparent),
                    center = Offset(size.width * 0.96f, size.height * 0.72f),
                    radius = size.width * 0.78f,
                ),
                radius = size.width * 0.78f,
                center = Offset(size.width * 0.96f, size.height * 0.72f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.glowOrange, Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.08f),
                    radius = size.width * 0.42f,
                ),
                radius = size.width * 0.42f,
                center = Offset(size.width * 0.88f, size.height * 0.08f),
            )
            val step = 22.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(colors.grid, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(colors.grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(
                status = lastStatus,
                nowMs = nowMs,
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
            )
            HeroCard(
                status = lastStatus,
                nowMs = nowMs,
                isRefreshing = isRefreshing,
                onRefresh = { refresh() },
            )
            ChannelCard(
                topic = topic,
                onTopicChange = { topic = it },
                isRefreshing = isRefreshing,
                statusMessage = statusMessage,
                onSave = {
                    StatusStore.setTopic(context, topic)
                    statusMessage = "Тема сохранена"
                },
                onRefresh = { refresh() },
            )
            AccessCard(
                zcode = lastStatus?.zcode,
                token = zcodeToken,
                onTokenChange = { zcodeToken = it },
                isPending = isOAuthPending,
                message = zcodeMessage,
                onLogin = { login() },
                onLogout = { logout() },
            )
            SurfaceCard(onPinWidget = { pinWidget() })
            DiagnosticsCard(
                rawJson = lastStatus?.rawJson,
                lastError = StatusStore.getLastError(context),
                expanded = showDiagnostics,
                onToggle = { showDiagnostics = !showDiagnostics },
            )
            Text(
                text = "AGW / 01 · В NTFY УХОДЯТ ЧИСЛА КВОТ, ТОКЕНОВ, ID ДИАЛОГА И ФЛАГ ВАУЧЕРА",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 0.8.sp,
                color = AgTheme.colors.muted.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
