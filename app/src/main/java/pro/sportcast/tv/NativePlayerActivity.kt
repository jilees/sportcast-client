package pro.sportcast.tv

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import org.koin.androidx.viewmodel.ext.android.viewModel

class NativePlayerActivity : ComponentActivity() {
    private val model: PlayerViewModel by viewModel()
    private var source = ""
    private var surfaceBound = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = StreamUrls.validated(savedInstanceState?.getString("url") ?: intent.getStringExtra("url"))
        if (!StreamUrls.kinescope(source)) { finish(); return }
        TvUi.immersive(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val ui by model.state.collectAsStateWithLifecycle()
            PlayerScreen(ui, intent.getStringExtra("title").orEmpty(), intent.getStringExtra("league").orEmpty(),
                model::toggle, model::rewind, ::finish, model::showControls, model::select, model::beginTouch, model::endTouch,
                onKey = { model.handleKey(it.keyCode, it.action, it.repeatCount, ::finish) }) {
                val engine = model.engine as? ExoPlaybackEngine
                if (engine != null) {
                    AndroidView(factory = { context ->
                        FrameLayout(context).apply {
                            val playerView = PlayerView(context).apply {
                                useController = false
                                keepScreenOn = true
                                isFocusable = false
                                setOnClickListener { model.showControls() }
                                setOnTouchListener { view, event ->
                                    when (event.actionMasked) {
                                        android.view.MotionEvent.ACTION_DOWN -> model.beginTouch()
                                        android.view.MotionEvent.ACTION_UP -> { model.endTouch(); view.performClick() }
                                        android.view.MotionEvent.ACTION_CANCEL -> model.endTouch()
                                    }
                                    true
                                }
                            }
                            addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            tag = playerView
                            engine.bind(playerView)
                            surfaceBound = true
                            // Surface creation follows onResume; start only after the host is bound.
                            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) model.start(source)
                        }
                    }, modifier = Modifier.fillMaxSize(), onRelease = { host ->
                        surfaceBound = false
                        model.stop()
                        (host.tag as? PlayerView)?.let(engine::unbind)
                    })
                }
            }
            DisposableEffect(Unit) { onDispose { model.stop() } }
        }
    }
    override fun onResume() {
        super.onResume()
        TvUi.immersive(this)
        if (surfaceBound && source.isNotEmpty()) model.start(source)
    }
    override fun onPause() { model.stop(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("url", source); super.onSaveInstanceState(outState) }
    override fun onDestroy() { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onDestroy() }
}
