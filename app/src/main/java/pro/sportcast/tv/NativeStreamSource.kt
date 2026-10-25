package pro.sportcast.tv

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class NativeStreamSource(val url: String) {
    companion object {
        fun fromOptions(json: String): NativeStreamSource {
            val options = JSONObject(json)
            val playlist = options.optJSONArray("playlist")
            if (playlist == null || playlist.length() == 0) throw JSONException("Нет доступного эфира")
            val item = playlist.getJSONObject(0)
            if (nonEmpty(item.opt("drm")) || nonEmpty(item.opt("ad")) || nonEmpty(options.optJSONObject("settings")?.opt("drm"))) {
                throw JSONException("Этот источник не поддерживается")
            }
            val hls = item.optJSONObject("sources")?.opt("hls").let { if (it is JSONArray) it.opt(0) else it }
            val url = validated(when (hls) { is JSONObject -> hls.optString("src"); is String -> hls; else -> "" })
            if (url.isEmpty()) throw JSONException("Источник не предоставил поддерживаемый HLS-поток")
            return NativeStreamSource(url)
        }
        private fun nonEmpty(value: Any?): Boolean = when (value) {
            null, JSONObject.NULL, false -> false
            is JSONObject -> value.length() > 0
            is JSONArray -> value.length() > 0
            else -> value.toString().isNotEmpty()
        }
        fun validated(src: String): String = try {
            val uri = URI.create(src)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (uri.isSafeHttps() && (host == "kinescopecdn.net" || host.endsWith(".kinescopecdn.net") || host == "kinescope.io" || host.endsWith(".kinescope.io") || host == "embed.new.video")) uri.toASCIIString() else ""
        } catch (_: IllegalArgumentException) { "" }
    }
}
