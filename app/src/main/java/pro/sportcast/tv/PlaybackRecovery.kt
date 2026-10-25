package pro.sportcast.tv

/** Monotonic-clock watchdog. READY alone never replenishes the retry budget. */
class PlaybackRecovery {
    private var attempts = 0
    private var lastPosition = 0L
    private var lastFrames = 0L
    private var positionAt = -1L
    private var framesAt = -1L
    private var bufferingAt = -1L
    private var healthyAt = -1L
    fun reset() { attempts = 0; resetObservation() }
    fun resetObservation() { positionAt = -1; framesAt = -1; bufferingAt = -1; healthyAt = -1 }
    fun nextRetryDelayMs(): Long {
        resetObservation()
        return RETRY_DELAYS_MS.getOrNull(attempts)?.also { attempts++ } ?: -1
    }
    fun attempts() = attempts
    fun observe(now: Long, shouldPlay: Boolean, buffering: Boolean, position: Long, frames: Long, hasVideo: Boolean): String? {
        if (!shouldPlay) { resetObservation(); return null }
        if (positionAt < 0) { lastPosition = position; lastFrames = frames; positionAt = now; framesAt = now }
        val positionMoved = position != lastPosition
        val framesMoved = frames != lastFrames
        if (positionMoved) { lastPosition = position; positionAt = now }
        if (framesMoved) { lastFrames = frames; framesAt = now }
        if (buffering) {
            healthyAt = -1
            if (bufferingAt < 0) bufferingAt = now
            return "buffering_timeout".takeIf { now - bufferingAt >= BUFFER_TIMEOUT_MS }
        }
        bufferingAt = -1
        if (now - positionAt >= FRAME_TIMEOUT_MS) return "position_stalled"
        if (hasVideo && now - framesAt >= FRAME_TIMEOUT_MS) return "video_stalled"
        if (positionMoved && (!hasVideo || framesMoved)) {
            if (healthyAt < 0) healthyAt = now
            if (now - healthyAt >= HEALTHY_RESET_MS) attempts = 0
        } else healthyAt = -1
        return null
    }
    companion object {
        const val BUFFER_TIMEOUT_MS = 30_000L
        const val FRAME_TIMEOUT_MS = 20_000L
        const val HEALTHY_RESET_MS = 60_000L
        private val RETRY_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000)
    }
}
