package pro.sportcast.tv

import java.net.URI
import java.util.Locale

object StreamUrls {
    private val src = Regex("\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    fun fromEmbed(embed: String?): String {
        if (embed == null) return ""
        return validated(src.find(embed)?.groupValues?.get(1)?.replace("&amp;", "&") ?: embed.trim())
    }
    fun validated(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return try {
            val uri = URI.create(value)
            if (!uri.isSafeHttps() || !allowedHost(uri.host.lowercase(Locale.ROOT))) "" else uri.toASCIIString()
        } catch (_: IllegalArgumentException) { "" }
    }
    fun allowedHost(host: String) = host == "sportcast.pro" || host.endsWith(".sportcast.pro") || host == "embed.new.video" || host == "kinescope.io" || host.endsWith(".kinescope.io")
    fun kinescope(url: String) = runCatching {
        val host = URI.create(url).host?.lowercase(Locale.ROOT)
        host == "embed.new.video" || host == "kinescope.io" || host?.endsWith(".kinescope.io") == true
    }.getOrDefault(false)
}

internal fun URI.isSafeHttps() = scheme.equals("https", ignoreCase = true) && host != null && userInfo == null && fragment == null && port in listOf(-1, 443)
