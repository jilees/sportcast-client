package pro.sportcast.tv

import org.junit.Assert.*
import org.junit.Test

class StreamUrlsTest {
    @Test fun readsActualEmbedWithHtmlEntities() {
        assertEquals("https://embed.new.video/test?a=1&b=2", StreamUrls.fromEmbed("""<div><iframe src="https://embed.new.video/test?a=1&amp;b=2"></iframe></div>"""))
    }
    @Test fun blocksExecutableAndInsecureSchemes() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "http://embed.new.video/test", "https://evil.test", "https://sportcast.pro.evil.test/a", "https://x@embed.new.video/a", "https://embed.new.video:8443/a", "https://embed.new.video/a#fragment").forEach { assertEquals(it, "", StreamUrls.validated(it)) }
    }
    @Test fun rejectsVkSources() {
        listOf("https://vkvideo.ru/video_ext.php?oid=-1&id=2", "https://vksport.vkvideo.ru/live-41593209_456253648", "https://vk.com/video-1_2").forEach { assertEquals("", StreamUrls.validated(it)) }
    }
    @Test fun recognizesKinescopeCustomDomain() {
        assertTrue(StreamUrls.kinescope("https://embed.new.video/test")); assertTrue(StreamUrls.kinescope("https://kinescope.io/test"))
        assertFalse(StreamUrls.kinescope("https://vkvideo.ru/video_ext.php")); assertFalse(StreamUrls.kinescope("https://kinescope.io.evil.test/test"))
    }
}
