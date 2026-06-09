package com.ice.hitomimanager.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ice.hitomimanager.data.model.HitomiCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import kotlin.coroutines.resume

class HitomiSearchWebView(
    private val context: Context
) {
    suspend fun searchByTitle(
        title: String,
        limit: Int = 10,
        waitMillis: Long = 5000L
    ): List<HitomiCandidate> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val appContext = context.applicationContext

            val webView = WebView(appContext)

            var completed = false
            var pageStarted = false
            var pageFinished = false
            var attempts = 0

            fun finish(result: List<HitomiCandidate>) {
                if (completed) return
                completed = true

                handler.removeCallbacksAndMessages(null)

                if (cont.isActive) {
                    cont.resume(result)
                }

                runCatching {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.clearHistory()
                    webView.destroy()
                }
            }

            fun evaluateOnce() {
                if (completed) return

                attempts++

                val js = """
                    (() => {
                        const links = Array.from(document.querySelectorAll('a[href]')).map(a => ({
                            href: a.href,
                            text: (a.innerText || a.textContent || '').trim()
                        }));

                        const results = [];
                        const seen = new Set();

                        for (const item of links) {
                            const href = item.href || '';
                            let id = null;

                            const m1 = href.match(/\/(?:galleries|reader)\/(\d+)\.html/);
                            const m2 = href.match(/-(\d+)\.html/);

                            if (m1) id = m1[1];
                            else if (m2) id = m2[1];

                            if (!id || seen.has(id)) continue;

                            seen.add(id);
                            results.push({
                                id: id,
                                url: href,
                                searchTitle: item.text || ''
                            });

                            if (results.length >= $limit) break;
                        }

                        return JSON.stringify({
                            href: location.href,
                            title: document.title,
                            bodyTextLength: document.body ? document.body.innerText.length : 0,
                            resultCount: results.length,
                            results: results
                        });
                    })()
                """.trimIndent()

                runCatching {
                    webView.evaluateJavascript(js) { raw ->
                        val result = runCatching {
                            parseEvaluateResult(raw)
                        }.getOrDefault(emptyList())

                        if (result.isNotEmpty()) {
                            finish(result)
                        } else {
                            if (attempts >= 12) {
                                finish(emptyList())
                            } else {
                                handler.postDelayed(
                                    { evaluateOnce() },
                                    1000L
                                )
                            }
                        }
                    }
                }.onFailure {
                    finish(emptyList())
                }
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = false
                blockNetworkImage = true

                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false

                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT

                userAgentString =
                    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                            "Chrome/125.0.0.0 Mobile Safari/537.36"
            }

            CookieManager.getInstance().setAcceptCookie(true)

            webView.webChromeClient = WebChromeClient()

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: Bitmap?
                ) {
                    pageStarted = true
                    pageFinished = false
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    pageFinished = true

                    // onPageFinished 不等于前端搜索结果已经渲染完，所以延迟后轮询。
                    handler.postDelayed(
                        { evaluateOnce() },
                        waitMillis
                    )
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        finish(emptyList())
                    }
                }
            }

            val encoded = Uri.encode(title)
            val searchUrl = "https://hitomi.la/search.html?$encoded"

            webView.loadUrl(searchUrl)

            // 总兜底：如果连 onPageFinished 都没触发，也不要永远挂住。
            handler.postDelayed(
                {
                    if (!completed) {
                        finish(emptyList())
                    }
                },
                18_000L
            )

            cont.invokeOnCancellation {
                finish(emptyList())
            }
        }
    }

    private fun parseEvaluateResult(raw: String?): List<HitomiCandidate> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()

        val outer = JSONTokener(raw).nextValue()
        val jsonText = outer as? String ?: return emptyList()

        val obj = org.json.JSONObject(jsonText)
        val array = obj.optJSONArray("results") ?: JSONArray()

        val result = mutableListOf<HitomiCandidate>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            val id = item.optString("id")
            if (id.isBlank()) continue

            result += HitomiCandidate(
                id = id,
                url = item.optString("url"),
                searchTitle = item.optString("searchTitle")
            )
        }

        return result
    }
}