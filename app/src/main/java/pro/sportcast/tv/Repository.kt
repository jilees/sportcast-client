package pro.sportcast.tv

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface EventsRepository {
    suspend fun fetch(): List<Event>
    suspend fun cached(): List<Event>
}

class Repository(private val cache: File, private val io: CoroutineDispatcher) : EventsRepository {
    override suspend fun fetch(): List<Event> = runInterruptible(io) {
        val connection = URL("$BASE/api/events").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "SportcastTV/0.3 AndroidTV")
        val raw = try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > 8 * 1024 * 1024) throw IOException("Response too large")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally { connection.disconnect() }
        val events = Event.parse(raw)
        try { cache.writeText(raw) } catch (_: IOException) { /* A cache failure must not discard fresh events. */ }
        events
    }
    override suspend fun cached(): List<Event> = runInterruptible(io) { Event.parse(cache.readText()) }
    companion object { const val BASE = "https://sportcast.pro" }
}
