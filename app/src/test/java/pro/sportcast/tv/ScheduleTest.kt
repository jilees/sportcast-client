package pro.sportcast.tv

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

class ScheduleTest {
    private fun event(id: String, sport: String, time: String, status: String) = Event(id = id, sport = sport, start = OffsetDateTime.parse(time).toInstant(), status = status)
    @Test fun midnightUsesMoscowCalendarAndHidesFinishedEvents() {
        val early = event("early", "f1", "2026-10-24T21:01:00Z", "upcoming")
        val all = listOf(event("late", "football", "2026-10-24T23:59:00+03:00", "live"), early, event("done", "hockey", "2026-10-25T00:30:00+03:00", "finished"))
        assertEquals(listOf(early), Schedule.forDay(all, LocalDate.of(2026, 10, 25)))
        assertEquals(setOf("f1"), Schedule.sports(all, LocalDate.of(2026, 10, 25)))
    }
    @Test fun disappearingSportReturnsToAllAndAvailableSelectionStays() {
        assertEquals("all", Schedule.filter("football", setOf("basketball", "f1")))
        assertEquals("f1", Schedule.filter("f1", setOf("basketball", "f1")))
        assertEquals("all", Schedule.filter("f1", setOf("f1")))
    }
    @Test fun countdownRoundsUpAndDoesNotGoNegative() {
        val start = Instant.parse("2026-10-24T17:30:00Z")
        assertEquals("00:25:00", Schedule.countdown(start, start.minusSeconds(1500), false))
        assertEquals("00:00:01", Schedule.countdown(start, start.minusMillis(1), false))
        assertEquals("00 : 00 : 00 : 00", Schedule.countdown(start, start.plusSeconds(2), true))
    }
    @Test fun primaryNeverFallsBackToAlternativeOrVk() {
        val row = JSONObject().put("playerEmbed", "https://vk.com/video-1_2").put("altStreams", JSONArray().put(JSONObject().put("playerEmbed", "https://embed.new.video/alternative")))
        assertEquals("", Event.fromJson(row).primaryUrl)
        row.put("playerEmbed", "<iframe src='https://embed.new.video/main'></iframe>")
        assertEquals("https://embed.new.video/main", Event.fromJson(row).primaryUrl)
    }
    @Test fun formulaCardHasHeadlineSessionAndStageWithoutTeamPlaceholders() {
        val race = Event(sport = "f1", league = "F1", round = "FP3", name = "Формула-1 | ГРАН-ПРИ США. Практика 3 | 18 этап")
        assertFalse(race.hasTeams()); assertEquals("ГРАН-ПРИ США", race.cardTitle()); assertEquals("Практика 3", race.session()); assertEquals("18 этап", race.stage())
    }
}
