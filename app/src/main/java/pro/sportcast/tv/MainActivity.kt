package pro.sportcast.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModel()
    private val logos: LogoLoader by inject()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvUi.immersive(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.playback.collect { request ->
                    if (request != null) {
                        model.consumePlayback()
                        startActivity(Intent(this@MainActivity, NativePlayerActivity::class.java)
                            .putExtra("url", request.url).putExtra("title", request.title).putExtra("league", request.league))
                    }
                }
            }
        }
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val grid = rememberLazyGridState()
            BackHandler(state.waitingId != null) { model.backToBoard() }
            Box(Modifier.fillMaxSize().background(TvUi.Bg)) {
                val waiting = state.waiting
                if (waiting != null) WaitingScreen(waiting, state.now, logos, model::backToBoard)
                else BoardScreen(state, logos, grid, { model.focusedTag }, { model.focusedTag = it }, model::selectDay, model::selectSport, model::open, model::refresh)
            }
        }
    }
    override fun onResume() { super.onResume(); TvUi.immersive(this); model.start() }
    override fun onPause() { model.stop(); super.onPause() }
}
