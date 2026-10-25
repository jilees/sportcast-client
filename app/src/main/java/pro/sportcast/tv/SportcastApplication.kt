package pro.sportcast.tv

import android.app.Application
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.File
import java.time.Clock

val appModule = module {
    single<Clock> { Clock.system(Schedule.ZONE) }
    single<EventsRepository> { Repository(File(androidContext().cacheDir, "events.json"), Dispatchers.IO) }
    single { LogoLoader(Dispatchers.IO) }
    single<StreamSourceResolver> { KinescopeSourceResolver() }
    single<PlaybackEngineFactory> { ExoPlaybackEngineFactory(androidContext(), get()) }
    viewModel { MainViewModel(get(), get(), get()) }
    viewModel { PlayerViewModel(get()) }
}

class SportcastApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin { androidContext(this@SportcastApplication); modules(appModule) }
    }
}
