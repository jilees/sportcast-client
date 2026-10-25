package pro.sportcast.tv

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EventOverlay(title: String, details: String, status: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color.Transparent)))
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .35f to Color(0x99000000), .72f to Color(0xee000000), 1f to Color.Black,
                startY = size.height * .55f, endY = size.height), topLeft = Offset(0f, size.height * .55f), size = Size(size.width, size.height * .45f))
        }
        ControlButton(Control.Back, "Назад к афише", Modifier.padding(start = 12.dp, top = 8.dp).align(Alignment.TopStart).then(modifier), onClick = onBack)
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (details.isNotEmpty()) UiText(details, 12, Color(0xffc7cbd3), Modifier.padding(bottom = 6.dp), shadow = 2)
                if (title.isNotEmpty()) UiText(title.uppercase(Locale.forLanguageTag("ru")), 32, display = true, maxLines = 2, shadow = 2)
            }
            if (status.isNotEmpty()) UiText(status, 11, modifier = Modifier.padding(start = 20.dp, bottom = 2.dp))
        }
    }
}

@Composable
fun WaitingScreen(event: Event, now: Instant, logos: LogoLoader, onBack: () -> Unit) {
    val teams = event.hasTeams() && event.homeLogo.isNotEmpty() && event.awayLogo.isNotEmpty()
    val home by rememberArtwork(if (teams) event.homeLogo else "", logos)
    val away by rememberArtwork(if (teams) event.awayLogo else "", logos)
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { back.requestFocus() }
    val beforeStart = Schedule.remaining(event.start, now) > 0
    val details = buildString {
        append("${event.leagueLabel()} · ${event.session()}")
        if (event.sport == "f1") append(" · ${event.stage()}")
        append(" · ${event.start.atZone(Schedule.ZONE).format(DateTimeFormatter.ofPattern("d MMMM · HH:mm 'МСК'", Locale.forLanguageTag("ru")))}")
    }
    BoxWithConstraints(Modifier.fillMaxSize().eventBackground(event, home?.color, away?.color, radius = 0, border = false)) {
        EventOverlay(event.title(), details, if (beforeStart) "" else when {
            event.finished() -> "ТРАНСЛЯЦИЯ ЗАВЕРШЕНА"
            event.live() -> "ОСНОВНОЙ ЭФИР ПОКА НЕДОСТУПЕН"
            else -> "ОЖИДАЕМ НАЧАЛО ЭФИРА"
        }, onBack, Modifier.focusRequester(back))
        val timer = Schedule.countdown(event.start, now, true)
        // The approved size is 61 sp; scale down only on narrow screens.
        val timerSize = minOf(61, ((maxWidth.value - 56) / 8.7f).toInt()).coerceAtLeast(28)
        UiText(timer, timerSize, Color(0xff8aa4ff), Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp)
            .testTag("waiting-timer").semantics { contentDescription = "До начала: дни, часы, минуты, секунды. $timer" }, display = true, maxLines = 1, align = TextAlign.Center)
        if (beforeStart) UiText("ДО НАЧАЛА", 11, modifier = Modifier.align(Alignment.Center).offset(y = (-68).dp).testTag("countdown-label"))
    }
}

@Composable
fun PlayerScreen(
    ui: PlayerUiState, title: String, details: String,
    onToggle: () -> Unit, onRewind: () -> Unit, onBack: () -> Unit,
    onShow: () -> Unit, onSelect: (Control) -> Unit,
    onTouchStart: () -> Unit, onTouchEnd: () -> Unit,
    onKey: (android.view.KeyEvent) -> Boolean = { false },
    video: @Composable () -> Unit = {},
) {
    val rootFocus = remember { FocusRequester() }
    val play = remember { FocusRequester() }
    val rewind = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    val currentTouchStart by rememberUpdatedState(onTouchStart)
    val currentTouchEnd by rememberUpdatedState(onTouchEnd)
    LaunchedEffect(ui.visible, ui.selected, ui.seekable) {
        if (!ui.visible) rootFocus.requestFocus()
        else when (ui.selected) {
            Control.Back -> back
            Control.Rewind -> if (ui.seekable) rewind else play
            Control.Play -> play
        }.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("player-root").onPreviewKeyEvent { onKey(it.nativeKeyEvent) }.focusRequester(rootFocus)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                currentTouchStart()
                try { waitForUpOrCancellation(pass = PointerEventPass.Final) } finally { currentTouchEnd() }
            }
        }.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onShow)) {
        video()
        if (ui.visible) {
            EventOverlay(title, details, if (ui.playing) "● ПРЯМОЙ ЭФИР" else "ПАУЗА", onBack,
                Modifier.focusRequester(back).onFocusChanged { if (it.isFocused) onSelect(Control.Back) })
            Canvas(Modifier.fillMaxSize().testTag("controls-panel")) {
                val left = if (ui.seekable) 126.dp.toPx() else 44.dp.toPx()
                val top = Offset(center.x - left, center.y - 40.dp.toPx())
                val panelSize = Size(left + 44.dp.toPx(), 80.dp.toPx())
                drawRoundRect(Color(0xd5101723), top, panelSize, CornerRadius(14.dp.toPx()))
                drawRoundRect(Color(0x30b5c8f3), top, panelSize, CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()))
                if (ui.seekable) drawLine(Color(0x52b6c7e7), Offset(center.x - 44.dp.toPx(), center.y - 14.dp.toPx()), Offset(center.x - 44.dp.toPx(), center.y + 14.dp.toPx()), 1.dp.toPx())
            }
            ControlButton(Control.Play, if (ui.retry) "Повторить подключение" else if (ui.playing) "Пауза" else "Продолжить",
                Modifier.align(Alignment.Center).focusRequester(play).onFocusChanged { if (it.isFocused) onSelect(Control.Play) }, playing = ui.playing, retry = ui.retry, onClick = onToggle)
            if (ui.seekable) ControlButton(Control.Rewind, "Назад на 10 секунд",
                Modifier.align(Alignment.Center).offset(x = (-86).dp).focusRequester(rewind).onFocusChanged { if (it.isFocused) onSelect(Control.Rewind) }, onClick = onRewind)
            if (ui.message.isNotEmpty()) Box(Modifier.align(Alignment.Center).offset(y = 84.dp).fillMaxWidth().height(70.dp).padding(horizontal = 48.dp), contentAlignment = Alignment.Center) {
                UiText(ui.message, 15, maxLines = 3, align = TextAlign.Center, shadow = 3)
            }
        }
    }
}

@Composable
fun ControlButton(control: Control, description: String, modifier: Modifier = Modifier, playing: Boolean = false, retry: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val resources = LocalResources.current
    val font = remember(resources) { resources.getFont(R.font.agency_fb) }
    Canvas(modifier.size(when (control) { Control.Back -> 64.dp; Control.Play -> 70.dp; Control.Rewind -> 62.dp },
        when (control) { Control.Back -> 64.dp; Control.Play -> 62.dp; Control.Rewind -> 58.dp })
        .testTag("control:${control.name.lowercase(Locale.ROOT)}").semantics { contentDescription = description }
        .onFocusChanged { focused = it.isFocused }
        .clickable(interaction, indication = null, onClick = onClick)) {
        val factor = (if (control == Control.Back) 44.dp.toPx() else minOf(size.width, size.height)) / 70f
        withTransform({ translate(center.x, center.y); scale(factor, factor, Offset.Zero) }) {
            if (focused || pressed) {
                drawRoundRect(Color(0x563d5278), Offset(-33f, -32f), Size(66f, 64f), CornerRadius(7f))
                drawRoundRect(Color(0xffa7bcff), Offset(-33f, -32f), Size(66f, 64f), CornerRadius(7f), style = Stroke(1.5f))
            }
            val stroke = Stroke(2f, cap = StrokeCap.Round)
            when (control) {
                Control.Back -> {
                    drawLine(TvUi.Text, Offset(-17f, 0f), Offset(17f, 0f), 2f, StrokeCap.Round)
                    drawPath(Path().apply { moveTo(-3f, -14f); lineTo(-17f, 0f); lineTo(-3f, 14f) }, TvUi.Text, style = stroke)
                }
                Control.Rewind -> {
                    drawArc(TvUi.Text, 205f, 290f, false, Offset(-24f, -13f), Size(22f, 22f), style = stroke)
                    drawPath(Path().apply { moveTo(-24f, -5f); lineTo(-24f, -14f); lineTo(-15f, -14f) }, TvUi.Text, style = stroke)
                    drawContext.canvas.nativeCanvas.drawText("10 с", 3f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfff4f4f1.toInt(); typeface = font; textSize = 22f })
                }
                Control.Play -> when {
                    retry -> {
                        drawArc(TvUi.Text, 205f, 290f, false, Offset(-15f, -15f), Size(30f, 30f), style = stroke)
                        drawPath(Path().apply { moveTo(-15f, -6f); lineTo(-15f, -15f); lineTo(-6f, -15f) }, TvUi.Text, style = stroke)
                    }
                    !playing -> drawPath(Path().apply { moveTo(-8f, -14f); lineTo(14f, 0f); lineTo(-8f, 14f); close() }, TvUi.Text, style = stroke)
                    else -> {
                        drawRoundRect(TvUi.Text, Offset(-10f, -13f), Size(6f, 26f), CornerRadius(1f))
                        drawRoundRect(TvUi.Text, Offset(4f, -13f), Size(6f, 26f), CornerRadius(1f))
                    }
                }
            }
        }
    }
}
