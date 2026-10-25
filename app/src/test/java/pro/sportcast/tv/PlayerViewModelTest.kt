package pro.sportcast.tv

import android.view.KeyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class FakeEngine : PlaybackEngine {
        override val state = MutableStateFlow(EngineState())
        var connections = 0
        var releases = 0
        var rewinds = 0
        var paused = 0
        var played = 0
        var resolving = false
        var cancelled = false
        override suspend fun connect(url: String) {
            connections++
            if (resolving) try { awaitCancellation() } finally { cancelled = true }
        }
        override fun release() { releases++; state.value = EngineState() }
        override fun play() { played++ }
        override fun pause() { paused++ }
        override fun rewind() { rewinds++ }
        override fun sample(): PlaybackSample? = null
        override fun logStats() = Unit
    }
    private fun model(engine: FakeEngine) = PlayerViewModel(PlaybackEngineFactory { engine }) { main.dispatcher.scheduler.currentTime }
    private fun key(model: PlayerViewModel, code: Int, onBack: () -> Unit = {}) {
        model.handleKey(code, KeyEvent.ACTION_DOWN, 0, onBack)
        model.handleKey(code, KeyEvent.ACTION_UP, 0, onBack)
    }
    @Test fun hiddenControlsRequireOneOkToRevealAndAnotherToPause() = runTest(main.dispatcher) {
        val engine = FakeEngine(); val model = model(engine)
        model.start("source"); runCurrent(); engine.state.value = EngineState(PlaybackPhase.Ready, playing = true); runCurrent()
        advanceTimeBy(3501); runCurrent(); assertFalse(model.state.value.visible)
        key(model, KeyEvent.KEYCODE_DPAD_CENTER); assertTrue(model.state.value.visible); assertEquals(0, engine.paused)
        key(model, KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, engine.paused)
        model.stop()
    }
    @Test fun remoteFocusRewindAndBackMatchOriginalBehavior() = runTest(main.dispatcher) {
        val engine = FakeEngine(); val model = model(engine); var backs = 0
        model.start("source"); runCurrent(); engine.state.value = EngineState(PlaybackPhase.Ready, playing = true, seekable = true); runCurrent()
        key(model, KeyEvent.KEYCODE_DPAD_LEFT); key(model, KeyEvent.KEYCODE_ENTER)
        assertEquals(1, engine.rewinds)
        engine.state.value = EngineState(PlaybackPhase.Ready, playing = true, seekable = false); runCurrent()
        assertEquals(Control.Play, model.state.value.selected)
        key(model, KeyEvent.KEYCODE_MEDIA_REWIND); assertEquals(1, engine.rewinds)
        key(model, KeyEvent.KEYCODE_DPAD_UP); key(model, KeyEvent.KEYCODE_ENTER) { backs++ }
        assertEquals(1, backs); assertEquals(0, engine.paused)
        model.stop()
    }
    @Test fun pausedControlsStayVisibleAndPointerDownSuspendsAutoHide() = runTest(main.dispatcher) {
        val engine = FakeEngine(); val model = model(engine)
        model.start("source"); runCurrent(); engine.state.value = EngineState(PlaybackPhase.Ready, playing = true); runCurrent()
        model.beginTouch(); advanceTimeBy(10_000); runCurrent(); assertTrue(model.state.value.visible)
        model.endTouch(); advanceTimeBy(3501); runCurrent(); assertFalse(model.state.value.visible)
        engine.state.value = EngineState(PlaybackPhase.Ready, playing = false); runCurrent()
        advanceTimeBy(10_000); runCurrent(); assertTrue(model.state.value.visible)
        model.stop()
    }
    @Test fun recoveryReleasesDecoderAndLeavingCancelsRetry() = runTest(main.dispatcher) {
        val engine = FakeEngine(); val model = model(engine)
        model.start("source"); runCurrent(); val released = engine.releases
        engine.state.value = EngineState(PlaybackPhase.Ready, playing = true, error = "decoder"); runCurrent()
        assertTrue(engine.releases > released); assertTrue(model.state.value.retry)
        assertTrue(model.state.value.message.contains("Попытка 1"))
        model.stop(); advanceTimeBy(30_000); runCurrent(); assertEquals(1, engine.connections)
        model.start("source"); runCurrent()
        engine.state.value = EngineState(PlaybackPhase.Ready, playing = true, error = "decoder"); runCurrent()
        assertTrue(model.state.value.message.contains("Попытка 1"))
        model.stop()
    }
    @Test fun sourceResolutionIsCancelledOnPause() = runTest(main.dispatcher) {
        val engine = FakeEngine().apply { resolving = true }; val model = model(engine)
        model.start("source"); runCurrent(); model.stop(); runCurrent()
        assertTrue(engine.cancelled)
        advanceTimeBy(60_000); runCurrent(); assertEquals(1, engine.connections)
    }
    @Test fun retriesStopAfterThreeAndManualRetryResetsBudget() = runTest(main.dispatcher) {
        val engine = FakeEngine(); val model = model(engine)
        model.start("source"); runCurrent()
        for (delay in listOf(2000L, 5000L, 10000L)) {
            engine.state.value = EngineState(PlaybackPhase.Ready, playing = true, error = "decoder"); runCurrent()
            advanceTimeBy(delay + 1); runCurrent()
        }
        assertEquals(4, engine.connections)
        engine.state.value = EngineState(PlaybackPhase.Ready, playing = true, error = "decoder"); runCurrent()
        assertTrue(model.state.value.message.contains("Не удалось восстановить"))
        advanceTimeBy(60_000); runCurrent(); assertEquals(4, engine.connections)
        model.toggle(); runCurrent(); assertEquals(5, engine.connections)
        model.stop()
    }
    @Test fun errorDuringUserPauseAndEndedBroadcastNeverAutoReconnect() = runTest(main.dispatcher) {
        val engine = FakeEngine(); val model = model(engine)
        model.start("source"); runCurrent()
        engine.state.value = EngineState(PlaybackPhase.Ready, playing = false, error = "network"); runCurrent()
        advanceTimeBy(60_000); runCurrent(); assertEquals(1, engine.connections)
        model.toggle(); runCurrent()
        engine.state.value = EngineState(PlaybackPhase.Ended); runCurrent()
        advanceTimeBy(60_000); runCurrent(); assertEquals(2, engine.connections)
        assertEquals("Трансляция завершена", model.state.value.message)
        model.stop()
    }
}
