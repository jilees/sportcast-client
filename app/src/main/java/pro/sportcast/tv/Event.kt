package pro.sportcast.tv

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/** Immutable API model; only the published primary source is used for playback. */
data class Event(
    val id: String = "",
    val sport: String = "",
    val league: String = "",
    val round: String = "",
    val home: String = "",
    val away: String = "",
    val name: String = "",
    val homeLogo: String = "",
    val awayLogo: String = "",
    val homeShort: String = home,
    val awayShort: String = away,
    val status: String = "upcoming",
    val start: Instant = Instant.EPOCH,
    val primaryUrl: String = "",
    val sources: List<Source> = emptyList(),
) {
    data class Source(val label: String, val url: String)

    fun title() = if (hasTeams()) "$home — $away" else name.ifEmpty { league }
    fun hasTeams() = home.isNotEmpty() && away.isNotEmpty()
    fun live() = status == "live"
    fun finished() = status in setOf("finished", "ended", "completed")
    fun leagueLabel() = when (league) {
        "EPL" -> "Премьер-лига"
        "NHL" -> "НХЛ"
        "F1" -> "Формула-1"
        else -> league
    }
    fun cardTitle(): String {
        if (hasTeams()) return "${homeShort.ifEmpty { home }} — ${awayShort.ifEmpty { away }}"
        if (sport != "f1") return title()
        return name.split('|').map(String::trim).firstOrNull {
            it.isNotEmpty() && !it.matches(Regex("(?iu)формула[- ]?1")) && !it.matches(Regex("(?iu)\\d+\\s*этап.*"))
        }?.replace(Regex("(?iu)\\.\\s*(Практика|Квалификация|Гонка|Спринт).*$"), "")
            ?.replace(Regex("[.\\s]+$"), "") ?: title()
    }
    fun session() = if (sport != "f1") round else when (round.uppercase(Locale.ROOT)) {
        "FP1" -> "Практика 1"
        "FP2" -> "Практика 2"
        "FP3" -> "Практика 3"
        "КВАЛ" -> "Квалификация"
        "ГОНКА" -> "Гонка"
        "КВАЛ-С" -> "Спринт-квалификация"
        "СПРИНТ" -> "Спринт"
        else -> round
    }
    fun stage() = if (sport == "f1") Regex("(?iu)\\d+\\s*этап").find(name)?.value ?: round else round

    companion object {
        fun fromJson(j: JSONObject): Event {
            val h = j.optJSONObject("homeTeam")
            val a = j.optJSONObject("awayTeam")
            val primary = StreamUrls.fromEmbed(j.optString("playerEmbed")).takeIf(StreamUrls::kinescope).orEmpty()
            val sources = buildList {
                if (primary.isNotEmpty()) add(Source("Основной эфир", primary))
                val alt = j.optJSONArray("altStreams")
                if (alt != null) for (i in 0 until alt.length()) {
                    val row = alt.optJSONObject(i) ?: continue
                    val url = StreamUrls.fromEmbed(row.optString("playerEmbed"))
                    if (StreamUrls.kinescope(url) && none { it.url == url }) {
                        add(Source(row.optString("commentatorName").ifEmpty { "Эфир ${i + 2}" }, url))
                    }
                }
            }
            return Event(
                id = j.optString("id"), sport = j.optString("sport"), league = j.optString("league"),
                round = j.optString("matchType"), home = h?.optString("name").orEmpty(), away = a?.optString("name").orEmpty(),
                name = j.optString("eventName"), homeLogo = h?.optString("logo").orEmpty(), awayLogo = a?.optString("logo").orEmpty(),
                homeShort = h?.optString("short", h.optString("name")).orEmpty(), awayShort = a?.optString("short", a.optString("name")).orEmpty(),
                status = j.optString("computedStatus").ifEmpty { j.optString("status", "upcoming") },
                start = runCatching { OffsetDateTime.parse(j.optString("datetime")).toInstant() }.getOrDefault(Instant.EPOCH),
                primaryUrl = primary, sources = sources,
            )
        }
        fun parse(raw: String): List<Event> {
            val array = JSONArray(raw)
            return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::fromJson) }
                .filter { it.id.matches(Regex("[A-Za-z0-9_-]+")) && it.start != Instant.EPOCH }
                .sortedWith(compareBy<Event> { !it.live() }.thenBy { it.start })
        }
    }
}
