package pro.sportcast.tv

import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Control { Play, Rewind, Back }
data class PlayerUiState(
    val playing: Boolean = false,
    val seekable: Boolean = false,
    val retry: Boolean = false,
    val visible: Boolean = true,
    val selected: Control = Control.Play,
    val message: String = "Подключаем трансляцию…",
)

class PlayerViewModel(factory: PlaybackEngineFactory, private val elapsedTime: () -> Long = SystemClock::elapsedRealtime) : ViewModel() {
    val engine = factory.create()
    private val mutableState = MutableStateFlow(PlayerUiState())
    val state = mutableState.asStateFlow()
    private val recovery = PlaybackRecovery()
    private var active = false
    private var url = ""
    private var connection: Job? = null
    private var observer: Job? = null
    private var watchdog: Job? = null
    private var hide: Job? = null
    private var pointerDown = false
    private var recovering = false
    private var lastPhase = PlaybackPhase.Idle

    fun start(source: String) {
        if (active) return
        url = source
        active = true
        recovery.reset()
        observer = viewModelScope.launch { engine.state.collect(::onEngineState) }
        watchdog = viewModelScope.launch {
            var iterations = 0
            while (isActive) {
                if (!state.value.retry) engine.sample()?.let { sample ->
                    recovery.observe(elapsedTime(), sample.shouldPlay, sample.buffering, sample.position, sample.frames, sample.hasVideo)?.let(::recover)
                }
                if (iterations++ % 5 == 0) engine.logStats()
                delay(2_000)
            }
        }
        connect()
    }
    fun stop() {
        active = false
        connection?.cancel(); connection = null
        observer?.cancel(); observer = null
        watchdog?.cancel(); watchdog = null
        hide?.cancel(); hide = null
        recovery.resetObservation()
        engine.release()
        pointerDown = false
    }
    private fun connect() {
        connection?.cancel()
        engine.release()
        recovering = false
        lastPhase = PlaybackPhase.Idle
        mutableState.value = PlayerUiState()
        connection = viewModelScope.launch {
            try { engine.connect(url) }
            catch (_: TimeoutCancellationException) { recover("source_timeout") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { recover("source_unavailable") }
        }
    }
    private fun onEngineState(snapshot: EngineState) {
        if (!active || recovering) return
        if (snapshot.error != null) {
            if (snapshot.playing) recover(snapshot.error)
            else {
                mutableState.update { it.copy(playing = false, retry = true, seekable = false, message = "Не удалось продолжить эфир. Нажмите OK, чтобы повторить") }
                showControls()
            }
            return
        }
        if (snapshot.phase == PlaybackPhase.Idle) return
        val old = state.value
        if (lastPhase != snapshot.phase || old.playing != snapshot.playing) recovery.resetObservation()
        lastPhase = snapshot.phase
        mutableState.update { it.copy(
            playing = snapshot.playing && snapshot.phase != PlaybackPhase.Ended,
            seekable = snapshot.seekable && snapshot.phase != PlaybackPhase.Ended,
            retry = snapshot.phase == PlaybackPhase.Ended,
            selected = if (!snapshot.seekable && it.selected == Control.Rewind) Control.Play else it.selected,
            message = when (snapshot.phase) {
                PlaybackPhase.Buffering -> "Буферизация…"
                PlaybackPhase.Ended -> "Трансляция завершена"
                else -> ""
            },
        ) }
        if (old.playing != state.value.playing || !state.value.playing) showControls()
    }
    private fun recover(reason: String) {
        if (!active || recovering) return
        recovering = true
        val delayMs = recovery.nextRetryDelayMs()
        android.util.Log.w("SportcastNative", "Recovery reason=$reason attempt=${recovery.attempts()} delayMs=$delayMs")
        connection?.cancel()
        engine.release()
        mutableState.update { it.copy(playing = false, retry = true, seekable = false, selected = Control.Play,
            message = if (delayMs < 0) "Не удалось восстановить эфир. Нажмите OK, чтобы повторить" else "Восстанавливаем эфир… Попытка ${recovery.attempts()} из 3") }
        showControls()
        if (delayMs >= 0) connection = viewModelScope.launch { delay(delayMs); if (active) connect() }
    }
    fun toggle() {
        if (state.value.retry) { recovery.reset(); connect() }
        else if (state.value.playing) engine.pause() else engine.play()
        showControls()
    }
    fun rewind() { if (state.value.seekable) { recovery.resetObservation(); engine.rewind(); showControls() } }
    fun select(control: Control) { mutableState.update { it.copy(selected = control) } }
    fun showControls() { mutableState.update { it.copy(visible = true) }; scheduleHide() }
    private fun scheduleHide() {
        hide?.cancel()
        if (state.value.playing && !pointerDown) hide = viewModelScope.launch {
            delay(3_500)
            mutableState.update { it.copy(visible = false) }
        }
    }
    fun beginTouch() { pointerDown = true; hide?.cancel() }
    fun endTouch() { pointerDown = false; scheduleHide() }
    fun handleKey(code: Int, action: Int, repeat: Int, onBack: () -> Unit): Boolean {
        val ok = code in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
        val arrow = code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
        val media = code in setOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)
        if (!ok && !arrow && !media && code != KeyEvent.KEYCODE_MENU && code != KeyEvent.KEYCODE_MEDIA_REWIND) return false
        if (action != KeyEvent.ACTION_DOWN || repeat != 0) return true
        when {
            media -> {
                if (state.value.retry || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) toggle()
                else if (code == KeyEvent.KEYCODE_MEDIA_PLAY) engine.play() else engine.pause()
                showControls()
            }
            code == KeyEvent.KEYCODE_MEDIA_REWIND -> rewind()
            !state.value.visible -> showControls()
            ok -> when (state.value.selected) { Control.Back -> onBack(); Control.Rewind -> rewind(); Control.Play -> toggle() }
            else -> {
                select(when {
                    code == KeyEvent.KEYCODE_DPAD_UP -> Control.Back
                    code == KeyEvent.KEYCODE_DPAD_LEFT && state.value.seekable -> Control.Rewind
                    else -> Control.Play
                })
                showControls()
            }
        }
        return true
    }
    override fun onCleared() { stop() }
}
