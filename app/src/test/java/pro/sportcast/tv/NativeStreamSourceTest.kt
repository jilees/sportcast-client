package pro.sportcast.tv

import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class NativeStreamSourceTest {
    private val url = "https://live.kinescopecdn.net/on-air/workspace/session/master.m3u8"
    private fun options(hls: String, extra: String = "") = """{"playlist":[{"sources":{"hls":$hls}$extra}]}"""
    @Test fun resolvesPublishedLiveSource() { assertEquals(url, NativeStreamSource.fromOptions(options("""{"segments":"fmp4","src":"$url"}""")).url) }
    @Test fun preservesSignedSourceQuery() {
        val signed = "$url?token=sample&expires=1234"
        assertEquals(signed, NativeStreamSource.fromOptions(options("\"$signed\"")).url)
    }
    @Test fun supportsSourceArray() { assertEquals(url, NativeStreamSource.fromOptions(options("""[{"src":"$url"}]""")).url) }
    @Test fun rejectsUnsupportedAndUntrustedSources() {
        listOf("http://live.kinescopecdn.net/a.m3u8", "https://evil.test/a.m3u8", "https://kinescopecdn.net.evil.test/a.m3u8", "file:///a", "https://user@live.kinescopecdn.net/a", "https://live.kinescopecdn.net:8443/a", "https://127.0.0.1/a").forEach { assertEquals(it, "", NativeStreamSource.validated(it)) }
    }
    @Test fun rejectsProtectedAndAdSupportedSources() {
        listOf(""","drm":{"widevine":{"licenseUrl":"https://example.com"}}""", ""","ad":[{"adTagUrl":"https://example.com"}]""").forEach { extra ->
            assertThrows(JSONException::class.java) { NativeStreamSource.fromOptions(options("\"$url\"", extra)) }
        }
    }
    @Test fun failsCleanlyWithoutPlayableSource() {
        listOf("{}", """{"playlist":[]}""", """{"playlist":[{"sources":{"dash":{"src":"https://example.com/a.mpd"}}}]}""", "not json").forEach { input ->
            assertThrows(JSONException::class.java) { NativeStreamSource.fromOptions(input) }
        }
    }
    @Test fun rejectsPlayerLevelDrm() {
        val input = options("\"$url\"").dropLast(1) + ""","settings":{"drm":{"enabled":true}}}"""
        assertThrows(JSONException::class.java) { NativeStreamSource.fromOptions(input) }
    }
}
