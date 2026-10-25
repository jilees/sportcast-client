package pro.sportcast.tv

import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun bufferingHasDeadlineEvenWhenSegmentsKeepArriving() {
        val r = PlaybackRecovery()
        assertNull(r.observe(0, true, true, 0, 0, true))
        assertNull(r.observe(29999, true, true, 100, 1, true))
        assertEquals("buffering_timeout", r.observe(30000, true, true, 200, 2, true))
    }
    @Test fun frozenVideoIsDetectedWhileAudioClockAdvances() {
        val r = PlaybackRecovery(); r.observe(0, true, false, 0, 100, true)
        assertNull(r.observe(19999, true, false, 19999, 100, true))
        assertEquals("video_stalled", r.observe(20000, true, false, 20000, 100, true))
    }
    @Test fun stoppedClockIsDetectedWithoutVideoFormat() {
        val r = PlaybackRecovery(); r.observe(0, true, false, 100, 0, false)
        assertEquals("position_stalled", r.observe(20000, true, false, 100, 0, false))
    }
    @Test fun pauseNeverReconnectsAndResumeGetsFullGracePeriod() {
        val r = PlaybackRecovery(); r.observe(0, true, false, 0, 0, true)
        assertNull(r.observe(19000, false, false, 0, 0, true)); assertNull(r.observe(100000, false, false, 0, 0, true))
        assertNull(r.observe(200000, true, false, 0, 0, true)); assertNull(r.observe(219999, true, false, 0, 0, true))
        assertEquals("position_stalled", r.observe(220000, true, false, 0, 0, true))
    }
    @Test fun retriesAreBoundedAcrossReconnects() {
        val r = PlaybackRecovery()
        assertEquals(2000L, r.nextRetryDelayMs()); r.resetObservation()
        assertEquals(5000L, r.nextRetryDelayMs()); assertEquals(10000L, r.nextRetryDelayMs()); assertEquals(-1L, r.nextRetryDelayMs())
        r.reset(); assertEquals(2000L, r.nextRetryDelayMs())
    }
    @Test fun healthyMinuteReplenishesBudgetButReadyAloneDoesNot() {
        val r = PlaybackRecovery(); r.nextRetryDelayMs()
        for (t in 0L..60000L step 2000) assertNull(r.observe(t, true, false, t, t / 33, true))
        assertEquals(1, r.attempts()); r.observe(62000, true, false, 62000, 1900, true); assertEquals(0, r.attempts())
        r.nextRetryDelayMs()
        for (t in 0L..60000L step 2000) r.observe(t, true, false, t, 0, true)
        assertEquals(1, r.attempts())
    }
    @Test fun shortBurstsDoNotCauseInfiniteRetryLoop() {
        val r = PlaybackRecovery()
        repeat(3) { assertTrue(r.nextRetryDelayMs() > 0); for (t in 0L..10000L step 2000) assertNull(r.observe(t, true, false, t, t / 33, true)) }
        assertEquals(-1L, r.nextRetryDelayMs())
    }
    @Test fun seekAndNewPlayerDoNotInheritStallTimers() {
        val r = PlaybackRecovery(); r.observe(0, true, false, 50000, 100, true); r.resetObservation()
        assertNull(r.observe(100000, true, false, 0, 0, true)); assertNull(r.observe(119000, true, false, 0, 0, true))
    }
    @Test fun audioOnlyPlaybackDoesNotRequireVideoFrames() {
        val r = PlaybackRecovery()
        for (t in 0L..120000L step 2000) assertNull(r.observe(t, true, false, t, 0, false))
    }
}
