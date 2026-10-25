package pro.sportcast.tv

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object Schedule {
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    private val sportsOrder = listOf("football", "basketball", "tennis", "hockey", "nfl", "f1", "wrestling")
    fun sportLabel(sport: String) = when (sport) {
        "all" -> "Все"
        "football" -> "Футбол"
        "basketball" -> "Баскетбол"
        "tennis" -> "Теннис"
        "hockey" -> "Хоккей"
        "nfl" -> "NFL"
        "f1" -> "Формула-1"
        "wrestling" -> "Wrestling"
        else -> sport
    }
    fun forDay(events: List<Event>, day: LocalDate) = events
        .filter { !it.finished() && it.start.atZone(ZONE).toLocalDate() == day }
        .sortedWith(compareBy<Event> { !it.live() }.thenBy { it.start })
    fun sports(events: List<Event>, day: LocalDate): Set<String> {
        val present = forDay(events, day).map { it.sport }.toSet()
        return (sportsOrder.filter { it in present } + present).toSet()
    }
    fun filter(requested: String, available: Set<String>) = requested.takeIf { available.size > 1 && it in available } ?: "all"
    fun remaining(start: Instant, now: Instant) = ((Duration.between(now, start).toMillis() + 999) / 1000).coerceAtLeast(0)
    fun countdown(start: Instant, now: Instant, days: Boolean): String {
        val seconds = remaining(start, now)
        return if (days) String.format(Locale.ROOT, "%02d : %02d : %02d : %02d", seconds / 86400, seconds / 3600 % 24, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}
