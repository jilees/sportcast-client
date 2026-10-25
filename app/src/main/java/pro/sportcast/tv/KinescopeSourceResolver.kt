package pro.sportcast.tv

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener
import java.io.IOException
import kotlin.coroutines.resume

interface StreamSourceResolver {
    suspend fun resolve(url: String, host: ViewGroup): NativeStreamSource
}

/** WebView only bootstraps the source; finally destroys it before native playback or cancellation. */
class KinescopeSourceResolver : StreamSourceResolver {
    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun resolve(url: String, host: ViewGroup): NativeStreamSource = withContext(Dispatchers.Main.immediate) {
        require(StreamUrls.kinescope(StreamUrls.validated(url)))
        val failure = CompletableDeferred<String>()
        val web = WebView(host.context)
        var destroyed = false
        fun dispose() {
            if (destroyed) return
            destroyed = true
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        try {
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
            }
            web.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            }
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    request.isForMainFrame && !StreamUrls.kinescope(StreamUrls.validated(request.url.toString()))
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failure.complete("Не удалось открыть источник · проверьте сеть")
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) failure.complete("Источник недоступен · повторите подключение")
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    failure.complete("Источник не отвечает · повторите подключение")
                    dispose()
                    return true
                }
            }
            web.alpha = 0f
            host.addView(web, 0, ViewGroup.LayoutParams(1, 1))
            val original = Uri.parse(url)
            val builder = original.buildUpon().clearQuery()
            for (key in original.queryParameterNames) {
                if (key != "autoplay" && key != "preload") for (value in original.getQueryParameters(key)) builder.appendQueryParameter(key, value)
            }
            web.loadUrl(builder.appendQueryParameter("autoplay", "0").appendQueryParameter("preload", "false").build().toString(), mapOf("Referer" to "https://sportcast.pro/"))
            withTimeout(20_000) {
                var source: NativeStreamSource? = null
                while (source == null) {
                    if (failure.isCompleted) throw IOException(failure.await())
                    val value = suspendCancellableCoroutine { continuation ->
                        web.evaluateJavascript("JSON.stringify(window.playerOptions || null)") {
                            if (continuation.isActive) continuation.resume(it)
                        }
                    }
                    val decoded = JSONTokener(value).nextValue()
                    if (decoded is String && decoded != "null") source = NativeStreamSource.fromOptions(decoded)
                    else delay(300)
                }
                source
            }
        } finally { dispose() }
    }
}
