package com.ice.hitomimanager.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import java.util.Locale

class HitomiSearchWebView(
    private val context: Context
) {
    suspend fun searchByTitle(
        title: String,
        limit: Int = 10,
        waitMillis: Long = 5000L,
        timeoutMillis: Long = 30_000L
    ): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<String> { cont ->
            val handler = Handler(Looper.getMainLooper())
            val appContext = context.applicationContext

            val webView = WebView(appContext)
            val searchQuery = title.lowercase(Locale.ROOT)
            val encoded = Uri.encode(searchQuery)
            val searchUrl = "https://hitomi.la/search.html?$encoded"
            val deadlineAt = SystemClock.elapsedRealtime() + timeoutMillis

            var completed = false
            var pageStarted = false
            var pageFinished = false
            var attempts = 0
            var lastPageUrl = ""
            var lastTitle = ""
            var lastBodyTextLength = 0
            var lastHtmlLength = 0
            var lastResultCount = 0
            var lastCandidateIds: List<String> = emptyList()
            var lastMainFrameError: String? = null
            var lastParseError: String? = null
            var lastNumberOfResultsText = ""
            var lastLoaderVisible = false
            var lastNoResultsVisible = false

            fun buildSummary(
                result: List<HitomiCandidate>,
                failureReason: String?
            ): String {
                return buildString {
                    if (failureReason != null) {
                        append(failureReason)
                    } else {
                        append("搜索完成")
                    }
                    append("；提取候选ID ${result.size} 个")
                    append("；尝试 ${attempts} 次")
                    append("；页面标题：${lastTitle.ifBlank { "未知" }}")
                    append("；正文长度：$lastBodyTextLength")
                    append("；HTML长度：$lastHtmlLength")
                    if (lastNumberOfResultsText.isNotBlank()) {
                        append("；结果标题：$lastNumberOfResultsText")
                    }
                    append("；loader显示：$lastLoaderVisible")
                    append("；No results显示：$lastNoResultsVisible")
                    if (lastMainFrameError != null) {
                        append("；WebView错误：$lastMainFrameError")
                    }
                    if (lastParseError != null) {
                        append("；解析错误：$lastParseError")
                    }
                    if (lastCandidateIds.isNotEmpty()) {
                        append("；ID：${lastCandidateIds.take(10).joinToString(", ")}")
                    }
                }
            }

            fun buildRaw(
                result: List<HitomiCandidate>,
                failureReason: String?,
                timedOut: Boolean
            ): String {
                return JSONObject()
                    .put("diagnosticSummary", buildSummary(result, failureReason))
                    .put("query", title)
                    .put("searchQuery", searchQuery)
                    .put("searchUrl", searchUrl)
                    .put("pageUrl", lastPageUrl)
                    .put("pageTitle", lastTitle)
                    .put("bodyTextLength", lastBodyTextLength)
                    .put("htmlLength", lastHtmlLength)
                    .put("attempts", attempts)
                    .put("pageStarted", pageStarted)
                    .put("pageFinished", pageFinished)
                    .put("timedOut", timedOut)
                    .put("timeoutMillis", timeoutMillis)
                    .put("waitMillis", waitMillis)
                    .put("resultCount", lastResultCount)
                    .put("candidateIds", JSONArray(lastCandidateIds))
                    .put("numberOfResultsText", lastNumberOfResultsText)
                    .put("loaderVisible", lastLoaderVisible)
                    .put("noResultsVisible", lastNoResultsVisible)
                    .put("mainFrameError", lastMainFrameError)
                    .put("parseError", lastParseError)
                    .put("failureReason", failureReason)
                    .put(
                        "results",
                        JSONArray().apply {
                            result.forEach { candidate ->
                                put(
                                    JSONObject()
                                        .put("id", candidate.id)
                                        .put("url", candidate.url)
                                        .put("searchTitle", candidate.searchTitle)
                                )
                            }
                        }
                    )
                    .toString(2)
            }

            fun finish(
                result: List<HitomiCandidate>,
                failureReason: String? = null,
                timedOut: Boolean = false
            ) {
                if (completed) return
                completed = true

                handler.removeCallbacksAndMessages(null)

                if (cont.isActive) {
                    cont.resume(
                        buildRaw(result, failureReason, timedOut)
                    )
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

                        const html = document.documentElement ? document.documentElement.innerHTML : '';
                        const numberOfResults = document.querySelector('#number-of-results');
                        const numberOfResultsText = numberOfResults ? (numberOfResults.innerText || numberOfResults.textContent || '').trim() : '';
                        const loader = document.querySelector('#loader-content');
                        const noResults = document.querySelector('#no-results-content');
                        const isVisible = (el) => {
                            if (!el) return false;
                            const style = window.getComputedStyle(el);
                            return style.display !== 'none' &&
                                style.visibility !== 'hidden' &&
                                !el.classList.contains('hidden');
                        };
                        const noResultsDetected =
                            isVisible(noResults) ||
                            /^\s*0\s+Results?\s*$/i.test(numberOfResultsText) ||
                            (document.body && /\bNo results\b/i.test(document.body.innerText || ''));
                        const patterns = [
                            /\/(?:galleries|reader)\/(\d+)\.html/g,
                            /-(\d+)\.html/g
                        ];

                        for (const pattern of patterns) {
                            let match = null;

                            while ((match = pattern.exec(html)) !== null) {
                                const id = match[1];

                                if (!id || seen.has(id)) continue;

                                seen.add(id);
                                results.push({
                                    id: id,
                                    url: 'https://hitomi.la/galleries/' + id + '.html',
                                    searchTitle: ''
                                });

                                if (results.length >= $limit) break;
                            }

                            if (results.length >= $limit) break;
                        }

                        return JSON.stringify({
                            href: location.href,
                            title: document.title,
                            bodyTextLength: document.body ? document.body.innerText.length : 0,
                            htmlLength: html.length,
                            resultCount: results.length,
                            numberOfResultsText: numberOfResultsText,
                            loaderVisible: isVisible(loader),
                            noResultsVisible: noResultsDetected,
                            results: results
                        });
                    })()
                """.trimIndent()

                runCatching {
                    webView.evaluateJavascript(js) { raw ->
                        val snapshot = runCatching {
                            parseEvaluateResult(raw)
                        }.onFailure { e ->
                            lastParseError = e.message ?: e::class.java.simpleName
                        }.getOrNull()

                        if (snapshot != null) {
                            lastPageUrl = snapshot.href
                            lastTitle = snapshot.title
                            lastBodyTextLength = snapshot.bodyTextLength
                            lastHtmlLength = snapshot.htmlLength
                            lastResultCount = snapshot.resultCount
                            lastCandidateIds = snapshot.candidates.map { it.id }
                            lastNumberOfResultsText = snapshot.numberOfResultsText
                            lastLoaderVisible = snapshot.loaderVisible
                            lastNoResultsVisible = snapshot.noResultsVisible
                        }

                        val result = snapshot?.candidates.orEmpty()

                        if (result.isNotEmpty()) {
                            finish(result)
                        } else if (snapshot?.noResultsVisible == true) {
                            finish(
                                emptyList(),
                                failureReason = "搜索无结果"
                            )
                        } else {
                            val remainingMillis = deadlineAt - SystemClock.elapsedRealtime()

                            if (remainingMillis <= 1_000L) {
                                finish(
                                    emptyList(),
                                    failureReason = "搜索超时（${timeoutMillis / 1000} 秒）",
                                    timedOut = true
                                )
                            } else {
                                handler.postDelayed(
                                    { evaluateOnce() },
                                    minOf(1000L, remainingMillis)
                                )
                            }
                        }
                    }
                }.onFailure {
                    finish(
                        emptyList(),
                        failureReason = "执行页面提取脚本失败：${it.message ?: it::class.java.simpleName}"
                    )
                }
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                blockNetworkImage = false

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
                    lastPageUrl = url
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    pageFinished = true
                    lastPageUrl = url

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
                        lastMainFrameError = error.description?.toString()
                            ?: "未知 WebView 错误"
                        finish(
                            emptyList(),
                            failureReason = "搜索页面加载失败：$lastMainFrameError"
                        )
                    }
                }
            }

            webView.loadUrl(searchUrl)

            // 总兜底：如果连 onPageFinished 都没触发，也不要永远挂住。
            handler.postDelayed(
                {
                    if (!completed) {
                        finish(
                            emptyList(),
                            failureReason = "搜索页面超时（${timeoutMillis / 1000} 秒）",
                            timedOut = true
                        )
                    }
                },
                timeoutMillis
            )

            cont.invokeOnCancellation {
                finish(
                    emptyList(),
                    failureReason = "搜索协程已取消"
                )
            }
        }
    }

    private data class EvaluateSnapshot(
        val candidates: List<HitomiCandidate>,
        val href: String,
        val title: String,
        val bodyTextLength: Int,
        val htmlLength: Int,
        val resultCount: Int,
        val numberOfResultsText: String,
        val loaderVisible: Boolean,
        val noResultsVisible: Boolean
    )

    private fun parseEvaluateResult(raw: String?): EvaluateSnapshot {
        if (raw.isNullOrBlank() || raw == "null") {
            return EvaluateSnapshot(
                candidates = emptyList(),
                href = "",
                title = "",
                bodyTextLength = 0,
                htmlLength = 0,
                resultCount = 0,
                numberOfResultsText = "",
                loaderVisible = false,
                noResultsVisible = false
            )
        }

        val outer = JSONTokener(raw).nextValue()
        val jsonText = outer as? String ?: return EvaluateSnapshot(
            candidates = emptyList(),
            href = "",
            title = "",
            bodyTextLength = 0,
            htmlLength = 0,
            resultCount = 0,
            numberOfResultsText = "",
            loaderVisible = false,
            noResultsVisible = false
        )

        val obj = JSONObject(jsonText)
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

        return EvaluateSnapshot(
            candidates = result,
            href = obj.optString("href"),
            title = obj.optString("title"),
            bodyTextLength = obj.optInt("bodyTextLength"),
            htmlLength = obj.optInt("htmlLength"),
            resultCount = obj.optInt("resultCount"),
            numberOfResultsText = obj.optString("numberOfResultsText"),
            loaderVisible = obj.optBoolean("loaderVisible"),
            noResultsVisible = obj.optBoolean("noResultsVisible")
        )
    }
}
