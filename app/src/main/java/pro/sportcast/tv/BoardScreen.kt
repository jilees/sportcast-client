package pro.sportcast.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Russian = Locale.forLanguageTag("ru")

@Composable
fun rememberArtwork(url: String, logos: LogoLoader): State<LogoLoader.Artwork?> = produceState<LogoLoader.Artwork?>(null, url, logos) {
    value = null
    value = logos.load(url)
}

@Composable
fun BoardScreen(
    state: BoardState, logos: LogoLoader, grid: LazyGridState,
    focusedTag: () -> String?, onFocus: (String) -> Unit,
    onDay: (Long) -> Unit, onSport: (String) -> Unit, onOpen: (Event) -> Unit, onRetry: () -> Unit,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun focus(tag: String) = requesters.getOrPut(tag) { FocusRequester() }
    val cards = state.cards
    val ids = cards.map { it.id }
    val sports = state.sports
    LaunchedEffect(state.day, state.selectedFilter) { grid.scrollToItem(0) }
    LaunchedEffect(ids, sports, state.day, state.selectedFilter) {
        if (state.loading && cards.isEmpty()) return@LaunchedEffect
        val previous = focusedTag()
        val target = when {
            previous == "day:0" || previous == "day:1" -> previous
            previous?.startsWith("sport:") == true && sports.size > 1 -> previous.takeIf { it.removePrefix("sport:") in sports } ?: "sport:all"
            previous?.startsWith("event:") == true && previous.removePrefix("event:") in ids -> previous
            cards.isNotEmpty() -> "event:${cards.first().id}"
            state.error -> "retry"
            else -> "day:0"
        }
        if (target.startsWith("event:")) {
            val index = ids.indexOf(target.removePrefix("event:")) + 1
            if (grid.layoutInfo.visibleItemsInfo.none { it.index == index }) grid.scrollToItem(if (index == 1) 0 else index)
        }
        withFrameNanos { }
        requesters[target]?.requestFocus()
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        val columns = if (maxWidth >= 1040.dp) 3 else 2
        val height = ((maxHeight - 18.dp) / 2).coerceIn(236.dp, 252.dp)
        LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().padding(3.dp), state = grid,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item(key = "board-header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.sportcast_wordmark), "Sportcast", Modifier.size(140.dp, 40.dp))
                        Spacer(Modifier.width(32.dp))
                        listOf("Сегодня", "Завтра").forEachIndexed { index, label ->
                            val tag = "day:$index"
                            val selected = state.day == state.now.atZone(Schedule.ZONE).toLocalDate().plusDays(index.toLong())
                            Box(Modifier.size(88.dp, 38.dp).focusRequester(focus(tag)).onFocusChanged { if (it.isFocused) onFocus(tag) }
                                .testTag(tag).navButton(selected) { onDay(index.toLong()) }, contentAlignment = Alignment.Center) {
                                UiText(label, 15, if (selected) TvUi.Text else TvUi.Muted, bold = true)
                            }
                            if (index == 0) Spacer(Modifier.width(6.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        UiText(state.day.format(DateTimeFormatter.ofPattern("d MMMM", Russian)), 12, TvUi.Muted)
                    }
                    if (sports.size > 1) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp).height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                            (listOf("all") + sports).forEach { sport ->
                                val tag = "sport:$sport"
                                val selected = sport == state.selectedFilter
                                Box(Modifier.padding(end = 5.dp).height(38.dp).focusRequester(focus(tag))
                                    .onFocusChanged { if (it.isFocused) onFocus(tag) }.testTag(tag)
                                    .navButton(selected) { onSport(sport) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                    UiText(Schedule.sportLabel(sport).uppercase(Russian), 19, if (selected) TvUi.Text else TvUi.Muted, display = true)
                                }
                            }
                        }
                    }
                    if (state.status.isNotEmpty()) UiText(state.status, 12, TvUi.Muted, Modifier.padding(start = 4.dp, bottom = 10.dp))
                }
            }
            if (cards.isEmpty() && !state.loading) {
                item(key = "board-empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxSize().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        UiText(if (state.error) "РАСПИСАНИЕ НЕДОСТУПНО" else "ТРАНСЛЯЦИЙ ПОКА НЕТ", 34, display = true)
                        UiText(if (state.error) "Проверьте интернет и повторите загрузку." else "Выберите другой день.", 14, TvUi.Muted, Modifier.padding(top = 12.dp, bottom = 20.dp))
                        if (state.error) Box(Modifier.focusRequester(focus("retry")).testTag("retry").navButton(false, onRetry).padding(16.dp, 12.dp)) { UiText("Повторить", 15) }
                    }
                }
            } else {
                itemsIndexed(cards, key = { _, event -> event.id }) { index, event ->
                    val tag = "event:${event.id}"
                    MatchCard(event, state.now, logos, Modifier.height(height).focusRequester(focus(tag))
                        .focusProperties {
                            left = focus("event:${cards[if (index % columns > 0) index - 1 else index].id}")
                            right = focus("event:${cards[if (index % columns < columns - 1 && index + 1 < cards.size) index + 1 else index].id}")
                            down = focus("event:${cards[if ((index / columns + 1) * columns < cards.size) minOf(index + columns, cards.lastIndex) else index].id}")
                            if (index >= columns) up = focus("event:${cards[index - columns].id}")
                        }.onFocusChanged { if (it.isFocused) onFocus(tag) }) { onOpen(event) }
                }
            }
        }
    }
}

@Composable
fun MatchCard(event: Event, now: Instant, logos: LogoLoader, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val teams = event.hasTeams() && event.homeLogo.isNotEmpty() && event.awayLogo.isNotEmpty()
    val home by rememberArtwork(if (teams) event.homeLogo else "", logos)
    val away by rememberArtwork(if (teams) event.awayLogo else "", logos)
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        .eventBackground(event, home?.color, away?.color, focused = focused || pressed)
        .testTag("event:${event.id}")
        .semantics { contentDescription = "${event.title()}, ${if (event.live()) "прямой эфир" else event.start.atZone(Schedule.ZONE).format(DateTimeFormatter.ofPattern("HH:mm"))}" }
        .clickable(interaction, indication = null, onClick = onClick)
        .padding(start = 20.dp, top = 15.dp, end = 20.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiText(event.leagueLabel().uppercase(Locale.ROOT), 10, Color(0xffc0c4d0), Modifier.weight(1f), maxLines = 1)
            UiText(if (event.live()) "● ПРЯМОЙ ЭФИР" else event.start.atZone(Schedule.ZONE).format(DateTimeFormatter.ofPattern("HH:mm")), 11,
                if (event.live()) Color(0xffff918c) else Color(0xffc0ceff))
        }
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
            if (teams) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) { home?.let { Image(it.bitmap.asImageBitmap(), null, Modifier.fillMaxSize()) } }
                    Box(Modifier.size(60.dp, 76.dp), contentAlignment = Alignment.Center) { UiText("VS", 25, Color(0xff9195a0), display = true) }
                    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) { away?.let { Image(it.bitmap.asImageBitmap(), null, Modifier.fillMaxSize()) } }
                }
                UiText(event.cardTitle().uppercase(Russian), 32, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), display = true, maxLines = 2, align = TextAlign.Center)
            } else {
                UiText(event.cardTitle().uppercase(Russian), if (event.cardTitle().length > 28) 34 else 44, display = true, maxLines = 2)
                UiText(event.session().uppercase(Russian), 11, Color(0xffe4bbb9), Modifier.padding(top = 7.dp), tracking = .1f)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            UiText(event.stage(), 10, Color(0xffc1c3cb), Modifier.weight(1f), maxLines = 1)
            val pending = Schedule.remaining(event.start, now) == 0L
            if (event.live() || !pending) UiText(if (event.live()) "Смотреть" else "Через", 10, Color(0xffc1c3cb), Modifier.padding(start = 5.dp, end = 7.dp, bottom = 2.dp))
            if (!event.live()) UiText(if (pending) "Ожидаем эфир" else Schedule.countdown(event.start, now, false), if (pending) 18 else 25, Color(0xffa3b7ff), display = true)
            UiText("↗", 16, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
