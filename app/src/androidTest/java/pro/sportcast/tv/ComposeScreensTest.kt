package pro.sportcast.tv

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Device checks for real Compose geometry, pointer input and focus restoration. */
@OptIn(ExperimentalTestApi::class)
class ComposeScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playRemainsAtExactCenterWithAndWithoutRewind() {
        var state by mutableStateOf(PlayerUiState(seekable = true))
        compose.setContent {
            PlayerScreen(state, "Челси — Тоттенхэм", "Премьер-лига · 8 тур", {}, {}, {}, {}, { state = state.copy(selected = it) }, {}, {})
        }
        fun assertCentered() {
            val root = compose.onNodeWithTag("player-root").fetchSemanticsNode().boundsInRoot
            val play = compose.onNodeWithTag("control:play").fetchSemanticsNode().boundsInRoot
            assertEquals(root.center.x, play.center.x, 1f)
            assertEquals(root.center.y, play.center.y, 1f)
        }
        assertCentered()
        compose.onNodeWithTag("control:rewind").assertExists()
        compose.runOnIdle { state = state.copy(seekable = false) }
        compose.onNodeWithTag("control:rewind").assertDoesNotExist()
        assertCentered()
        compose.onNodeWithText("ЧЕЛСИ — ТОТТЕНХЭМ").assertExists()
    }
    @Test fun firstPointerTapActivatesBackExactlyOnce() {
        var backs = 0
        var toggles = 0
        compose.setContent {
            PlayerScreen(PlayerUiState(), "Матч", "Лига", { toggles++ }, {}, { backs++ }, {}, {}, {}, {})
        }
        compose.onNodeWithTag("control:back").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, backs); assertEquals(0, toggles) }
    }
    @Test fun hiddenControlsCanBeRevealedByTouch() {
        var state by mutableStateOf(PlayerUiState(playing = true, visible = false))
        compose.setContent {
            PlayerScreen(state, "Матч", "Лига", {}, {}, {}, { state = state.copy(visible = true) }, {}, {}, {})
        }
        compose.onNodeWithTag("control:play").assertDoesNotExist()
        compose.onNodeWithTag("player-root").performTouchInput { click() }
        compose.onNodeWithTag("control:play").assertExists()
    }
    @Test fun waitingLabelDisappearsAtZeroAndTimerRemains() {
        val start = Instant.parse("2026-10-25T12:00:00Z")
        var now by mutableStateOf(start.minusSeconds(1))
        val event = Event(id = "match", name = "Матч", start = start)
        val logos = LogoLoader(Dispatchers.IO)
        compose.setContent { WaitingScreen(event, now, logos, {}) }
        compose.onNodeWithTag("countdown-label").assertExists()
        compose.onNodeWithTag("waiting-timer").assertTextEquals("00 : 00 : 00 : 01")
        compose.runOnIdle { now = start }
        compose.onNodeWithTag("countdown-label").assertDoesNotExist()
        compose.onNodeWithTag("waiting-timer").assertTextEquals("00 : 00 : 00 : 00")
        compose.onNodeWithText("ОЖИДАЕМ НАЧАЛО ЭФИРА").assertExists()
    }
    @Test fun cardFocusSurvivesRefreshAndRemoteStaysInRow() {
        val now = Instant.parse("2026-10-25T12:00:00Z")
        var state by mutableStateOf(BoardState(
            events = (1..4).map { Event(id = "$it", name = "Матч $it", sport = "football", start = now.plusSeconds(it.toLong())) },
            day = now.atZone(Schedule.ZONE).toLocalDate(), now = now, loading = false))
        var focused: String? = null
        val logos = LogoLoader(Dispatchers.IO)
        compose.setContent {
            BoardScreen(state, logos, rememberLazyGridState(), { focused }, { focused = it }, {}, {}, {}, {})
        }
        compose.onNodeWithTag("event:1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("event:1").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        compose.onNodeWithTag("event:2").assertIsFocused()
        compose.runOnIdle { state = state.copy(events = state.events.map { it.copy(round = "Обновлено") }) }
        compose.onNodeWithTag("event:2").assertIsFocused()
        compose.runOnIdle { assertEquals("event:2", focused) }
    }
}
