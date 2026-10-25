package pro.sportcast.tv

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

data class BoardState(
    val events: List<Event> = emptyList(),
    val day: LocalDate,
    val filter: String = "all",
    val waitingId: String? = null,
    val loading: Boolean = true,
    val offline: Boolean = false,
    val error: Boolean = false,
    val now: Instant,
) {
    val sports: Set<String> get() = Schedule.sports(events, day)
    val selectedFilter: String get() = Schedule.filter(filter, sports)
    val cards: List<Event> get() = Schedule.forDay(events, day).filter { selectedFilter == "all" || it.sport == selectedFilter }.take(100)
    val waiting: Event? get() = events.firstOrNull { it.id == waitingId }
    val status: String get() = when {
        loading && events.isEmpty() -> "Загружаем расписание…"
        error -> "Не удалось обновить расписание"
        offline -> "Нет сети · сохранённое расписание"
        else -> ""
    }
}

data class PlaybackRequest(val url: String, val title: String, val league: String) {
    companion object {
        fun from(event: Event) = PlaybackRequest(event.primaryUrl, event.title(), "${event.leagueLabel()} · ${event.stage()}")
    }
}

class MainViewModel(
    private val repository: EventsRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BoardState(
        day = savedState.get<String>("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today(),
        filter = savedState["filter"] ?: "all",
        waitingId = savedState["waiting"],
        now = clock.instant(),
    ))
    val state = mutableState.asStateFlow()
    private val mutablePlayback = MutableStateFlow<PlaybackRequest?>(null)
    val playback = mutablePlayback.asStateFlow()
    private var activeJob: Job? = null
    private var refreshJob: Job? = null
    var focusedTag: String?
        get() = savedState["focused"]
        set(value) { savedState["focused"] = value }

    private fun today() = LocalDate.now(clock.withZone(Schedule.ZONE))
    fun start() {
        if (activeJob?.isActive == true) return
        activeJob = viewModelScope.launch {
            coroutineScope {
                launch { while (isActive) { refresh(); delay(60_000) } }
                launch {
                    while (isActive) {
                        val today = today()
                        mutableState.update { it.copy(now = clock.instant(), day = maxOf(it.day, today)) }
                        savedState["day"] = state.value.day.toString()
                        delay(1_000)
                    }
                }
            }
        }
    }
    fun stop() { activeJob?.cancel(); activeJob = null; refreshJob?.cancel(); refreshJob = null }
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            try {
                accept(repository.fetch(), offline = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (state.value.events.isEmpty()) {
                    try { accept(repository.cached(), offline = true) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { mutableState.update { it.copy(error = true, offline = true) } }
                } else mutableState.update { it.copy(error = true, offline = true) }
            } finally { mutableState.update { it.copy(loading = false) } }
        }
    }
    private fun accept(events: List<Event>, offline: Boolean) {
        mutableState.update { old ->
            old.copy(events = events, offline = offline, error = false, filter = Schedule.filter(old.filter, Schedule.sports(events, old.day)))
        }
        savedState["filter"] = state.value.filter
        // A cached live flag, or the countdown reaching zero, is never enough to open playback.
        state.value.waiting?.takeIf { !offline && it.live() && it.primaryUrl.isNotEmpty() }?.let {
            backToBoard()
            mutablePlayback.value = PlaybackRequest.from(it)
        }
    }
    fun selectDay(offset: Long) {
        val day = today().plusDays(offset)
        savedState["day"] = day.toString()
        mutableState.update { it.copy(day = day, filter = Schedule.filter(it.filter, Schedule.sports(it.events, day))) }
        savedState["filter"] = state.value.filter
        backToBoard()
    }
    fun selectSport(sport: String) {
        val filter = Schedule.filter(sport, state.value.sports)
        savedState["filter"] = filter
        mutableState.update { it.copy(filter = filter) }
    }
    fun open(event: Event) {
        if (event.live() && event.primaryUrl.isNotEmpty()) mutablePlayback.value = PlaybackRequest.from(event)
        else {
            savedState["waiting"] = event.id
            mutableState.update { it.copy(waitingId = event.id) }
        }
    }
    fun backToBoard() {
        savedState["waiting"] = null
        mutableState.update { it.copy(waitingId = null) }
    }
    fun consumePlayback() { mutablePlayback.value = null }
}
