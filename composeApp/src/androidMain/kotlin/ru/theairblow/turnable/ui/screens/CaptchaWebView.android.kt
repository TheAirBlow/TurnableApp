package ru.theairblow.turnable.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.platform.CAPTCHA_CAPTURE_SCRIPT
import ru.theairblow.turnable.platform.CaptchaRequest
import ru.theairblow.turnable.service.httpGet
import ru.theairblow.turnable.ui.components.ErrorBanner
import turnable.composeapp.generated.resources.*
import java.net.URLEncoder

private const val BRIDGE = "TurnableCaptcha"

private val CAPTURE_SCRIPT =
    "window.__tc = { done: function (m, c) { $BRIDGE.done(m, c); }, loggedIn: function () { $BRIDGE.loggedIn(); } };\n" +
        CAPTCHA_CAPTURE_SCRIPT

// The call page serves its mobile layout to phones, which has no anonymous join flow; a desktop browser identity gets the full page.
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private class CaptchaBridge(
    private val scope: CoroutineScope,
    private val port: Int,
    private val onTokens: (ok: Boolean) -> Unit,
    private val onLoggedIn: () -> Unit,
) {
    @JavascriptInterface
    fun done(messages: String, calls: String) {
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    httpGet("http://127.0.0.1:$port/done?messages=${enc(messages)}&calls=${enc(calls)}").code == 200
                }
            }.getOrDefault(false)
            onTokens(ok)
        }
    }

    @JavascriptInterface
    fun loggedIn() {
        scope.launch { onLoggedIn() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun CaptchaWebView(request: CaptchaRequest, onSolved: () -> Unit, modifier: Modifier) {
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val solved by rememberUpdatedState(onSolved)
    val scope = rememberCoroutineScope()
    val endpointFailed = stringResource(Res.string.captcha_endpoint_failed)
    val tokensRejected = stringResource(Res.string.captcha_tokens_rejected)
    val vkLoggedIn = stringResource(Res.string.captcha_vk_logged_in)

    // Keep the service's deadline from running out while the page loads
    LaunchedEffect(request.port) {
        runCatching { withContext(Dispatchers.IO) { httpGet("http://127.0.0.1:${request.port}/ping") } }
            .onFailure { error = endpointFailed }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    // A fresh anonymous session every time, never the user's own VK login
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    WebStorage.getInstance().deleteAllData()
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = DESKTOP_USER_AGENT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = true
                    }
                    addJavascriptInterface(
                        CaptchaBridge(
                            scope, request.port,
                            onTokens = { ok -> if (ok) solved() else error = tokensRejected },
                            onLoggedIn = { error = vkLoggedIn },
                        ),
                        BRIDGE,
                    )
                    val earlyInjection = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                    if (earlyInjection) WebViewCompat.addDocumentStartJavaScript(this, CAPTURE_SCRIPT, setOf("*"))
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean =
                            // Keep the page out of the VK app and any other deep link
                            req.url.scheme.let { it != "http" && it != "https" }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            if (!earlyInjection) view.evaluateJavascript(CAPTURE_SCRIPT, null)
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    loadUrl(request.url)
                }
            },
            onRelease = {
                it.stopLoading()
                CookieManager.getInstance().removeAllCookies(null)
                it.destroy()
            },
        )
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        error?.let { ErrorBanner(it, Modifier.align(Alignment.BottomCenter).padding(16.dp), onDismiss = { error = null }) }
    }
}
