package com.ice.hitomimanager.data.remote

import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.model.HitomiTagMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HitomiMetaProvider {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchMeta(
        id: String
    ): HitomiBookMeta? = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://ltn.gold-usergeneratedcontent.net/galleries/$id.js",
            "https://ltn.hitomi.la/galleries/$id.js"
        )

        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36"
                )
                .header("Origin", "https://hitomi.la")
                .header("Referer", "https://hitomi.la/")
                .build()

            val meta = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null

                    val body = response.body?.string() ?: return@use null
                    parseGalleryJs(id, body)
                }
            }.getOrNull()

            if (meta != null) {
                return@withContext meta
            }
        }

        null
    }

    private fun parseGalleryJs(
        fallbackId: String,
        js: String
    ): HitomiBookMeta? {
        val jsonText = js
            .substringAfter("=", missingDelimiterValue = "")
            .trim()
            .removeSuffix(";")
            .trim()

        if (jsonText.isBlank()) return null

        val obj = JSONObject(jsonText)
        val files = obj.optJSONArray("files")

        return HitomiBookMeta(
            id = obj.optString("id", fallbackId),
            title = obj.optString("title"),
            japaneseTitle = obj.optString("japanese_title").ifBlank { null },
            type = obj.optString("type").ifBlank { null },
            language = obj.optString("language").ifBlank { null },
            date = obj.optString("date").ifBlank { null },
            pageCount = files?.length() ?: 0,
            artists = readNames(obj.optJSONArray("artists"), "artist", "name"),
            groups = readNames(obj.optJSONArray("groups"), "group", "name"),
            series = readNames(obj.optJSONArray("parodys"), "parody", "series", "name") +
                    readNames(obj.optJSONArray("series"), "series", "parody", "name"),
            characters = readNames(obj.optJSONArray("characters"), "character", "name"),
            tags = readTags(obj.optJSONArray("tags"))
        )
    }

    private fun readNames(
        array: JSONArray?,
        vararg keys: String
    ): List<String> {
        if (array == null) return emptyList()

        val result = mutableListOf<String>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            for (key in keys) {
                val value = item.optString(key)
                if (value.isNotBlank()) {
                    result += value
                    break
                }
            }
        }

        return result.distinct()
    }

    private fun readTags(
        array: JSONArray?
    ): List<HitomiTagMeta> {
        if (array == null) return emptyList()

        val result = mutableListOf<HitomiTagMeta>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            val name = item.optString("tag").ifBlank {
                item.optString("name")
            }

            if (name.isBlank()) continue

            val namespace = when {
                isTruthy(item, "female") -> "female"
                isTruthy(item, "male") -> "male"
                else -> "tag"
            }

            result += HitomiTagMeta(
                namespace = namespace,
                name = name
            )
        }

        return result.distinctBy { "${it.namespace}:${it.name}" }
    }

    /**
     * hitomi 的 tags 里 male/female 字段是字符串 "1"（或 ""），而非 JSON 布尔值，
     * org.json 的 optBoolean 只识别 "true"/"false"，会把 "1" 当成 false。
     * 这里统一按 "1"/1/true 判定为真。
     */
    private fun isTruthy(
        obj: JSONObject,
        key: String
    ): Boolean {
        return when (val value = obj.opt(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "1" || value.equals("true", ignoreCase = true)
            else -> false
        }
    }
}