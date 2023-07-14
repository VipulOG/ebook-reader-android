package com.vipulog.ebookreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


@Suppress("MemberVisibilityCanBePrivate", "unused")
class EbookReaderView : WebView {
    private val fileServer: FileServer = FileServer()
    private var listener: EbookReaderEventListener? = null
    private val scope = CoroutineScope(Main)


    constructor(context: Context) : super(context) {
        init()
    }


    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }


    private fun init() {
        setupWebViewSettings()
        addJavascriptInterface(JavaScriptInterface(), "AndroidInterface")
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fileServer.stop()
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = this.settings
        settings.javaScriptEnabled = true
        setBackgroundColor(Color.TRANSPARENT)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }
    }


    fun setEbookReaderListener(listener: EbookReaderEventListener?) {
        this.listener = listener
    }


    fun openBook(path: String) {
        val readerUrl = "https://appassets.androidplatform.net/assets/ebook-reader/reader.html"
        val bookUrl = "http://localhost:8080/?url=$path"
        val uriBuilder = Uri.parse(readerUrl).buildUpon().appendQueryParameter("url", bookUrl)
        val url = uriBuilder.build().toString()
        loadUrl(url)
    }


    fun goto(locator: String) {
        processJavascript("goto('$locator')")
    }


    fun next() {
        processJavascript("next()")
    }


    fun prev() {
        processJavascript("prev()")
    }


    fun getTheme(callback: (ReaderTheme) -> Unit) {
        processJavascript("getTheme()") {
            callback(Json.decodeFromString(it))
        }
    }


    fun setTheme(theme: ReaderTheme) {
        val themeJson = Json.encodeToString(theme)
        processJavascript("setTheme($themeJson)")
    }


    fun getFlow(callback: (String?) -> Unit) {
        processJavascript("getFlow()") {
            if (it == "null") callback(null)
            else callback(it)
        }
    }


    fun setFlow(flow: String) {
        processJavascript("setFlow('$flow')")
    }


    private fun processJavascript(script: String, callback: ((String) -> Unit)? = null) {
        evaluateJavascript(script) {
            val pattern = "^\"(.*)\"$".toRegex()
            val matchResult = pattern.find(it)
            val out = matchResult?.groupValues?.get(1) ?: it
            callback?.invoke(out)
        }
    }


    private inner class JavaScriptInterface {
        private val json = Json { ignoreUnknownKeys = true }

        @JavascriptInterface
        fun onBookLoaded(bookJson: String) {
            scope.launch {
                listener?.onBookLoaded(json.decodeFromString(bookJson))
            }
        }

        @JavascriptInterface
        fun onBookLoadFailed(error: String) {
            scope.launch {
                listener?.onBookLoadFailed(error)
            }
        }

        @JavascriptInterface
        fun onRelocated(relocationInfoJson: String) {
            scope.launch {
                val relocationInfo: RelocationInfo = json.decodeFromString(relocationInfoJson)
                val currentTocItem = relocationInfo.tocItem
                val progress = (relocationInfo.fraction * 100).toInt()
                listener?.onProgressChanged(progress, currentTocItem)
            }
        }

        @JavascriptInterface
        fun onSelectionStart() {
            scope.launch {
                listener?.onTextSelectionModeChange(true)
            }
        }

        @JavascriptInterface
        fun onSelectionEnd() {
            scope.launch {
                listener?.onTextSelectionModeChange(false)
            }
        }
    }
}
