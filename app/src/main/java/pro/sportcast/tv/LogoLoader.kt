package pro.sportcast.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/** Application cache with bounded downloads; composition owns and cancels each request. */
class LogoLoader(private val io: CoroutineDispatcher) {
    data class Artwork(val bitmap: Bitmap, val color: Int?)
    private val cache = object : LruCache<String, Artwork>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Artwork) = value.bitmap.allocationByteCount
    }
    private val downloads = Semaphore(2)
    // A bounded set of locks deduplicates requests without retaining every URL ever seen.
    private val locks = List(16) { Mutex() }
    suspend fun load(raw: String): Artwork? {
        val url = validated(raw).ifEmpty { return null }
        return locks[(url.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            cache.get(url)?.let { return@withLock it }
            downloads.withPermit {
                try {
                    runInterruptible(io) {
                        download(url)?.let { bitmap ->
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            Artwork(bitmap, LogoColors.darkAverage(pixels)).also { cache.put(url, it) }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: IOException) { null }
                catch (_: RuntimeException) { null }
            }
        }
    }
    private fun download(initialUrl: String): Bitmap? {
        var url = initialUrl
        repeat(3) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 7_000; connection.readTimeout = 7_000
            connection.setRequestProperty("User-Agent", "SportcastTV/0.3 AndroidTV")
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    url = validated(URI.create(url).resolve(location).toString()).ifEmpty { return null }
                    return@repeat
                }
                if (code != 200) return null
                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (output.size() + count > 2 * 1024 * 1024) return null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth !in 1..4096 || options.outHeight !in 1..4096) return null
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 160) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } finally { connection.disconnect() }
        }
        return null
    }
    companion object {
        fun validated(raw: String?): String {
            if (raw.isNullOrEmpty()) return ""
            return try {
                val uri = URI.create(if (raw.startsWith('/') && !raw.startsWith("//")) Repository.BASE + raw else raw)
                if (uri.isSafeHttps() && uri.host.lowercase(Locale.ROOT) in setOf("sportcast.pro", "images.ruscore.ru", "upload.wikimedia.org", "thumb.wikimedia.org", "i.pinimg.com")) uri.toASCIIString() else ""
            } catch (_: IllegalArgumentException) { "" }
        }
    }
}
