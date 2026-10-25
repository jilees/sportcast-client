package pro.sportcast.tv

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EventTest {
    private fun item(id: String = "match", status: String = "live") = JSONObject("""{"id":"$id","sport":"football","league":"EPL","datetime":"2026-10-18T18:30:00+03:00","status":"$status","homeTeam":{"name":"Форест"},"awayTeam":{"name":"Арсенал"}}""")
    @Test fun moscowOffsetIsParsed() { assertEquals(Instant.parse("2026-10-18T15:30:00Z"), Event.fromJson(item()).start) }
    @Test fun computedStatusTakesPrecedence() { assertTrue(Event.fromJson(item(status = "upcoming").put("computedStatus", "live")).live()) }
    @Test fun invalidRowsAreSkippedAndLiveComesFirst() {
        val result = Event.parse("[${item("upcoming", "upcoming")},${item("bad/path")},${item("live")},{},null]")
        assertEquals(2, result.size); assertEquals("live", result.first().id)
    }
    @Test fun sourcesAreValidatedAndDeduplicated() {
        val j = item().put("playerEmbed", """<iframe src="https://embed.new.video/test"></iframe>""")
            .put("altStreams", JSONArray("""[{"playerEmbed":"<iframe src='https://embed.new.video/test'></iframe>"},{"playerEmbed":"https://evil.test/test"}]"""))
        assertEquals(1, Event.fromJson(j).sources.size)
        assertEquals("Форест — Арсенал", Event.fromJson(j).title())
    }
    @Test fun missingTeamsUseEventName() {
        val j = item().apply { remove("homeTeam"); put("eventName", "Гран-при") }
        assertEquals("Гран-при", Event.fromJson(j).title())
    }
    @Test fun onlyNativeSourcesRemainIncludingCachedVkEntries() {
        val j = item().put("vkVideoUrl", "https://vk.com/video-1_2").put("playerEmbed", "https://vkvideo.ru/video_ext.php?oid=-1&id=2")
            .put("altStreams", JSONArray("""[{"vkVideoUrl":"https://vk.com/video-3_4","playerEmbed":"https://embed.new.video/native"},{"playerEmbed":"https://sportcast.pro/event/match"}]"""))
        assertEquals(listOf(Event.Source("Эфир 2", "https://embed.new.video/native")), Event.fromJson(j).sources)
        j.remove("altStreams")
        assertTrue(Event.fromJson(j).sources.isEmpty())
    }
}
