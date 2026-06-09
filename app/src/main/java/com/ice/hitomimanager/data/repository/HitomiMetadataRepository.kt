package com.ice.hitomimanager.data.repository

import android.content.Context
import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.remote.HitomiMetaProvider
import com.ice.hitomimanager.data.remote.HitomiSearchWebView
import kotlinx.coroutines.withTimeoutOrNull

class HitomiMetadataRepository(
    context: Context
) {
    private val searcher = HitomiSearchWebView(context)
    private val metaProvider = HitomiMetaProvider()

    suspend fun searchTitle(
        title: String
    ): List<HitomiBookMeta> {
        val candidates = withTimeoutOrNull(20_000L) {
            searcher.searchByTitle(
                title = title,
                limit = 10,
                waitMillis = 5000L
            )
        } ?: emptyList()

        return candidates
            .mapNotNull { candidate ->
                metaProvider.fetchMeta(candidate.id)
            }
            .sortedByDescending { meta ->
                scoreTitle(title, meta.title, meta.japaneseTitle)
            }
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
}