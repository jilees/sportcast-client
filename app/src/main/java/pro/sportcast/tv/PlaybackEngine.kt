package pro.sportcast.tv

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackPhase { Idle, Buffering, Ready, Ended }
data class EngineState(
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val playing: Boolean = false,
    val seekable: Boolean = false,
    val error: String? = null,
)
data class PlaybackSample(val shouldPlay: Boolean, val buffering: Boolean, val position: Long, val frames: Long, val hasVideo: Boolean)
interface PlaybackEngine {
    val state: StateFlow<EngineState>
    suspend fun connect(url: String)
    fun release()
    fun play()
    fun pause()
    fun rewind()
    fun sample(): PlaybackSample?
    fun logStats()
}
fun interface PlaybackEngineFactory { fun create(): PlaybackEngine }

@OptIn(UnstableApi::class)
class ExoPlaybackEngineFactory(private val context: Context, private val resolver: StreamSourceResolver) : PlaybackEngineFactory {
    override fun create(): PlaybackEngine = ExoPlaybackEngine(context, resolver)
}

/** Android renderer boundary. Views are bound by Compose and detached when it leaves composition. */
@OptIn(UnstableApi::class)
class ExoPlaybackEngine(private val context: Context, private val resolver: StreamSourceResolver) : PlaybackEngine {
    private val mutableState = MutableStateFlow(EngineState())
    override val state = mutableState.asStateFlow()
    private var player: ExoPlayer? = null
    private var video: PlayerView? = null
    fun bind(view: PlayerView) { video = view; view.player = player }
    fun unbind(view: PlayerView) { view.player = null; if (video === view) video = null }
    override suspend fun connect(url: String) {
        release()
        val host = video?.parent as? ViewGroup ?: error("Video surface is not attached")
        val source = resolver.resolve(url, host)
        val buffers = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 30_000, 1_000, 3_000)
            .setBackBuffer(10_000, false).setTargetBufferBytes(32 * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(false).build()
        val current = ExoPlayer.Builder(context).setLoadControl(buffers).build()
        player = current
        current.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { if (this@ExoPlaybackEngine.player === current) publish(current) }
            override fun onPlayerError(error: PlaybackException) {
                if (player !== current) return
                Log.w(TAG, "Playback failed: ${error.errorCodeName} cause=${failureDetails(error)}")
                publish(current, error.errorCodeName)
            }
        })
        video?.player = current
        current.setMediaItem(MediaItem.Builder().setUri(source.url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
        current.prepare()
        current.play()
        Log.i(TAG, "Started native HLS playback; surface=${video?.videoSurfaceView?.javaClass?.simpleName}")
    }
    private fun publish(current: ExoPlayer, error: String? = current.playerError?.errorCodeName) {
        mutableState.value = EngineState(
            phase = when (current.playbackState) {
                Player.STATE_BUFFERING -> PlaybackPhase.Buffering
                Player.STATE_READY -> PlaybackPhase.Ready
                Player.STATE_ENDED -> PlaybackPhase.Ended
                else -> PlaybackPhase.Idle
            },
            playing = current.playWhenReady,
            seekable = current.isCurrentMediaItemSeekable && current.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            error = error,
        )
    }
    override fun release() {
        val old = player
        player = null
        video?.player = null
        old?.release()
        mutableState.value = EngineState()
    }
    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun rewind() {
        val current = player ?: return
        if (state.value.seekable) current.seekTo((current.currentPosition - 10_000).coerceAtLeast(0))
    }
    override fun sample(): PlaybackSample? = player?.let {
        val counters = it.videoDecoderCounters
        counters?.ensureUpdated()
        PlaybackSample(it.playWhenReady && it.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            it.playbackState == Player.STATE_BUFFERING, it.currentPosition, counters?.renderedOutputBufferCount?.toLong() ?: 0, it.videoFormat != null)
    }
    override fun logStats() {
        val current = player ?: return
        val counters = current.videoDecoderCounters
        counters?.ensureUpdated()
        val format = current.videoFormat
        Log.i(TAG, "state=${current.playbackState} playing=${current.isPlaying} bufferedMs=${current.totalBufferedDuration} positionMs=${current.currentPosition} video=${format?.let { "${it.width}x${it.height}" } ?: "unknown"} rendered=${counters?.renderedOutputBufferCount ?: 0} dropped=${counters?.droppedBufferCount ?: 0}")
    }
    private fun failureDetails(error: Throwable): String = generateSequence(error) { it.cause }.take(6).joinToString(" <- ") {
        it.javaClass.simpleName + if (it is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) " HTTP ${it.responseCode}" else ""
    }
    companion object { private const val TAG = "SportcastNative" }
}
