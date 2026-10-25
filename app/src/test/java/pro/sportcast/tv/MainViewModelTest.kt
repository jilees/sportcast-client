package pro.sportcast.tv

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val now = Instant.parse("2026-10-25T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone() = Schedule.ZONE
        override fun withZone(zone: java.time.ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant(): Instant = current
    }
    private val upcoming = Event(id = "match", sport = "football", start = now.plusSeconds(1))
    private class FakeRepository(var events: List<Event>) : EventsRepository {
        var calls = 0
        var offline = false
        var cache: List<Event> = events
        override suspend fun fetch(): List<Event> { calls++; if (offline) throw IOException(); return events }
        override suspend fun cached() = cache
    }
    @Test fun countdownAloneDoesNotLaunchButFreshLiveSourceDoes() = runTest(main.dispatcher) {
        val repo = FakeRepository(listOf(upcoming))
        val movingClock = MutableClock(now)
        val model = MainViewModel(repo, SavedStateHandle(), movingClock)
        model.start(); runCurrent(); model.open(upcoming)
        movingClock.current = now.plusSeconds(2)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(0L, Schedule.remaining(upcoming.start, model.state.value.now))
        assertEquals(upcoming, model.state.value.waiting); assertNull(model.playback.value)
        repo.events = listOf(upcoming.copy(status = "live", primaryUrl = "https://embed.new.video/main"))
        model.refresh(); runCurrent()
        assertEquals("https://embed.new.video/main", model.playback.value?.url)
        assertNull(model.state.value.waitingId)
        model.consumePlayback(); assertNull(model.playback.value)
        model.stop()
    }
    @Test fun cachedLiveStatusNeverAutomaticallyOpensWaitingEvent() = runTest(main.dispatcher) {
        val cached = upcoming.copy(status = "live", primaryUrl = "https://embed.new.video/main")
        val repo = FakeRepository(listOf(cached)).apply { offline = true }
        val saved = SavedStateHandle(mapOf("waiting" to cached.id))
        val model = MainViewModel(repo, saved, clock)
        model.start(); runCurrent()
        assertTrue(model.state.value.offline); assertEquals(cached, model.state.value.waiting)
        assertNull(model.playback.value); model.stop()
    }
    @Test fun stoppedScreenCancelsRefreshAndDoesNotPollUntilResumed() = runTest(main.dispatcher) {
        var calls = 0
        val cancelled = CompletableDeferred<Unit>()
        val repository = object : EventsRepository {
            override suspend fun fetch(): List<Event> {
                calls++
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
            override suspend fun cached() = emptyList<Event>()
        }
        val model = MainViewModel(repository, SavedStateHandle(), clock)
        model.start(); runCurrent(); model.stop(); runCurrent()
        assertTrue(cancelled.isCompleted)
        advanceTimeBy(120_000); runCurrent(); assertEquals(1, calls)
        model.start(); runCurrent(); assertEquals(2, calls)
        model.stop()
    }
    @Test fun manualRefreshAndMinutePollNeverOverlap() = runTest(main.dispatcher) {
        var calls = 0
        val response = CompletableDeferred<List<Event>>()
        val repository = object : EventsRepository {
            override suspend fun fetch(): List<Event> { calls++; return response.await() }
            override suspend fun cached() = emptyList<Event>()
        }
        val model = MainViewModel(repository, SavedStateHandle(), clock)
        model.start(); runCurrent(); model.refresh(); advanceTimeBy(60_001); runCurrent()
        assertEquals(1, calls)
        response.complete(listOf(upcoming)); runCurrent()
        assertFalse(model.state.value.loading); model.stop()
    }
    @Test fun unavailableFilterResetsAndDayFilterAndFocusAreRestored() = runTest(main.dispatcher) {
        val repo = FakeRepository(listOf(upcoming, upcoming.copy(id = "race", sport = "f1")))
        val saved = SavedStateHandle()
        val model = MainViewModel(repo, saved, clock)
        model.start(); runCurrent(); model.selectSport("f1"); model.focusedTag = "event:race"
        assertEquals(listOf("race"), model.state.value.cards.map { it.id })
        val restored = MainViewModel(repo, saved, clock)
        assertEquals("f1", restored.state.value.filter); assertEquals("event:race", restored.focusedTag)
        repo.events = listOf(upcoming)
        model.refresh(); runCurrent()
        assertEquals("all", model.state.value.selectedFilter)
        model.selectDay(1); assertEquals(LocalDate.of(2026, 10, 26), model.state.value.day)
        assertEquals("2026-10-26", saved.get<String>("day")); model.stop()
    }
    @Test fun failedRefreshPreservesVisibleEventsAndShowsOfflineStatus() = runTest(main.dispatcher) {
        val repo = FakeRepository(listOf(upcoming))
        val model = MainViewModel(repo, SavedStateHandle(), clock)
        model.start(); runCurrent(); repo.offline = true; model.refresh(); runCurrent()
        assertEquals(listOf(upcoming), model.state.value.cards)
        assertTrue(model.state.value.offline); assertTrue(model.state.value.error)
        model.stop()
    }
}
