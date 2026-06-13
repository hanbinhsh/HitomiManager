package com.ice.hitomimanager.data.repository

import android.content.Context
import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.model.HitomiCandidate
import com.ice.hitomimanager.data.model.HitomiSearchMetaResult
import com.ice.hitomimanager.data.remote.HitomiMetaProvider
import com.ice.hitomimanager.data.remote.HitomiSearchWebView
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withTimeoutOrNull

class HitomiMetadataRepository(
    context: Context
) {
    private val searcher = HitomiSearchWebView(context)
    private val metaProvider = HitomiMetaProvider()

    suspend fun searchTitle(
        title: String,
        timeoutMillis: Long = 30_000L
    ): HitomiSearchMetaResult {
        val searchRaw = searcher.searchByTitle(
            title = title,
            limit = 10,
            waitMillis = 5000L,
            timeoutMillis = timeoutMillis
        )

        val searchJson = parseSearchDiagnostic(searchRaw, title)
        val searchCandidates = parseSearchCandidates(searchJson)
        val searchSummary = searchJson.optString("diagnosticSummary")
            .ifBlank { searchRaw }
        val searchFailureReason = searchJson.optString("failureReason")
            .ifBlank { null }

        val metas = mutableListOf<HitomiBookMeta>()
        val failedIds = mutableListOf<String>()

        for (candidate in searchCandidates) {
            val meta = withTimeoutOrNull(META_FETCH_TIMEOUT_MILLIS) {
                runCatching {
                    metaProvider.fetchMeta(candidate.id)
                }.getOrNull()
            }

            if (meta != null) {
                metas += meta
            } else {
                failedIds += candidate.id
            }
        }

        val sorted = metas
            .sortedByDescending { meta ->
                scoreTitle(title, meta.title, meta.japaneseTitle)
            }

        val failureReason = when {
            sorted.isNotEmpty() -> null
            searchFailureReason != null -> searchFailureReason
            searchCandidates.isNotEmpty() -> {
                "已提取到候选 ID，但元数据获取失败：${failedIds.take(10).joinToString(", ")}"
            }
            else -> "没有提取到候选 ID"
        }

        val summary = buildString {
            if (failureReason != null) {
                append(failureReason)
            } else {
                append("搜索成功")
            }
            append("；提取 ID ${searchCandidates.size} 个")
            append("；元数据成功 ${sorted.size} 个")
            if (failedIds.isNotEmpty()) {
                append("；元数据失败 ${failedIds.size} 个：")
                append(failedIds.take(10).joinToString(", "))
            }
            append("；")
            append(searchSummary)
        }

        val raw = JSONObject()
            .put("query", title)
            .put("timeoutMillis", timeoutMillis)
            .put("search", searchJson)
            .put("candidateIds", JSONArray(searchCandidates.map { it.id }))
            .put("metaSuccessIds", JSONArray(sorted.map { it.id }))
            .put("metaFailedIds", JSONArray(failedIds))
            .put("failureReason", failureReason)
            .put("summary", summary)
            .toString(2)

        return HitomiSearchMetaResult(
            books = sorted,
            diagnosticSummary = summary,
            diagnosticRaw = raw,
            failureReason = failureReason
        )
    }

    private fun parseSearchDiagnostic(
        raw: String,
        title: String
    ): JSONObject {
        return runCatching {
            JSONObject(raw)
        }.getOrElse {
            JSONObject()
                .put("diagnosticSummary", raw)
                .put("failureReason", raw)
                .put("results", JSONArray())
                .put("query", title)
        }
    }

    private fun parseSearchCandidates(searchJson: JSONObject): List<HitomiCandidate> {
        val array = searchJson.optJSONArray("results") ?: JSONArray()
        val result = mutableListOf<HitomiCandidate>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
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

    private fun scoreTitle(
        query: String,
        title: String,
        japaneseTitle: String?
    ): Int {
        val q = normalize(query)
        val t1 = normalize(title)
        val t2 = normalize(japaneseTitle.orEmpty())

        return maxOf(
            simpleSimilarity(q, t1),
            simpleSimilarity(q, t2)
        )
    }

    private fun normalize(s: String): String {
        return s
            .lowercase()
            .replace(Regex("\\.(zip|cbz|rar|cbr|7z)$"), "")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun fetchMetaById(id: String): HitomiBookMeta? {
        return metaProvider.fetchMeta(id)
    }

    private fun simpleSimilarity(a: String, b: String): Int {
        if (a.isBlank() || b.isBlank()) return 0
        if (a == b) return 100
        if (b.contains(a) || a.contains(b)) return 90

        val aw = a.split(" ").filter { it.isNotBlank() }.toSet()
        val bw = b.split(" ").filter { it.isNotBlank() }.toSet()

        if (aw.isEmpty() || bw.isEmpty()) return 0

        val common = aw.intersect(bw).size
        val total = aw.union(bw).size

        return common * 100 / total
    }

    private companion object {
        private const val META_FETCH_TIMEOUT_MILLIS = 30_000L
    }
}
