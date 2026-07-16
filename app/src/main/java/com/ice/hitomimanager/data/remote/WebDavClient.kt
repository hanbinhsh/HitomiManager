package com.ice.hitomimanager.data.remote

import android.util.Base64
import com.ice.hitomimanager.data.model.LibrarySource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

class WebDavClient {
    private val clientCache = ConcurrentHashMap<ClientKey, OkHttpClient>()
    private val xmlFactory by lazy {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
        }
    }

    fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return if (trimmed.contains("://")) trimmed else "https://$trimmed"
    }

    fun validateSource(source: LibrarySource) {
        val base = normalizeBaseUrl(source.webDavBaseUrl.orEmpty())
        require(base.isNotBlank()) { "WebDAV 服务器地址不能为空" }
        val uri = URI(base)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "WebDAV 默认要求 HTTPS，请检查服务器地址"
        }
        require(!uri.host.isNullOrBlank()) { "WebDAV 服务器地址无效" }
    }

    fun authHeaders(source: LibrarySource, password: String?): Map<String, String> {
        if (password.isNullOrBlank()) return emptyMap()
        val username = source.webDavUsername.orEmpty().trim()
        val token = Base64.encodeToString(
            "$username:$password".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        return mapOf("Authorization" to "Basic $token")
    }

    fun buildUrl(source: LibrarySource, path: String): String {
        val base = normalizeBaseUrl(source.webDavBaseUrl.orEmpty())
        val root = source.webDavRootPath.orEmpty().trim('/').takeIf(String::isNotBlank)
        val relative = path.trim('/').takeIf(String::isNotBlank)
        val combined = listOfNotNull(root, relative).joinToString("/")
        if (combined.isBlank()) return base
        return "$base/${combined.split('/').joinToString("/") { encodePathSegment(it) }}"
    }

    fun testConnection(source: LibrarySource, password: String?): String {
        val entries = listUrl(source, password, buildUrl(source, ""))
        return "连接成功，当前目录 ${entries.size} 个条目"
    }

    fun listUrl(source: LibrarySource, password: String?, url: String): List<WebDavEntry> {
        validateSource(source)
        val requestBody = ByteArray(0).toRequestBody("application/xml".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .method("PROPFIND", requestBody)
            .header("Depth", "1")
            .header("Accept", "application/xml,text/xml,*/*")
            .header("User-Agent", "HitomiManager/1.0")
        authHeaders(source, password).forEach(builder::header)
        return executeWithReadableErrors(source, url) {
            client(source).newCall(builder.build()).execute().use { response ->
                checkResponse(source, url, response.code, response.message, response.body?.bytes())
                    .let { parseMultistatus(it, url) }
            }
        }
    }

    fun readRange(
        source: LibrarySource,
        password: String?,
        url: String,
        start: Long,
        endInclusive: Long
    ): ByteArray {
        val builder = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=$start-$endInclusive")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "HitomiManager/1.0")
        authHeaders(source, password).forEach(builder::header)
        return executeWithReadableErrors(source, url) {
            client(source).newCall(builder.build()).execute().use { response ->
                if (response.code == 200) {
                    throw RangeNotSupportedException("WebDAV 服务器忽略了 Range 请求（HTTP 200）")
                }
                if (response.code != 206) {
                    throw IllegalStateException(httpError(source, url, response.code, response.message, response.body?.string()))
                }
                val contentRange = response.header("Content-Range").orEmpty()
                val match = CONTENT_RANGE.find(contentRange)
                val parsedStart = match?.groupValues?.getOrNull(1)?.toLongOrNull()
                val parsedEnd = match?.groupValues?.getOrNull(2)?.toLongOrNull()
                if (parsedStart != start || parsedEnd != endInclusive) {
                    throw RangeNotSupportedException("WebDAV 返回了无效 Content-Range：$contentRange")
                }
                val bytes = response.body?.bytes() ?: error("WebDAV Range 响应体为空")
                val expectedLength = (endInclusive - start + 1L).toInt()
                if (bytes.size != expectedLength) {
                    throw RangeNotSupportedException(
                        "WebDAV Range 长度不一致：期望 $expectedLength，实际 ${bytes.size}"
                    )
                }
                bytes
            }
        }
    }

    fun contentLength(source: LibrarySource, password: String?, url: String): Long {
        val builder = Request.Builder()
            .url(url)
            .head()
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "HitomiManager/1.0")
        authHeaders(source, password).forEach(builder::header)
        return executeWithReadableErrors(source, url) {
            client(source).newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error(httpError(source, url, response.code, response.message, response.body?.string()))
                }
                response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
                    ?: error("WebDAV 未返回文件大小，无法随机读取压缩包")
            }
        }
    }

    fun download(
        source: LibrarySource,
        password: String?,
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val builder = Request.Builder()
            .url(url)
            .get()
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "HitomiManager/1.0")
        authHeaders(source, password).forEach(builder::header)
        executeWithReadableErrors(source, url) {
            client(source).newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error(httpError(source, url, response.code, response.message, response.body?.string()))
                }
                val body = response.body ?: error("WebDAV 下载响应体为空")
                val total = body.contentLength().takeIf { it > 0L } ?: expectedSize
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var done = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            done += count
                            onProgress(done, total)
                        }
                    }
                }
            }
        }
    }

    private fun checkResponse(
        source: LibrarySource,
        url: String,
        code: Int,
        message: String,
        body: ByteArray?
    ): ByteArray {
        if (code !in 200..299) {
            error(httpError(source, url, code, message, body?.toString(Charsets.UTF_8)))
        }
        return body ?: error("WebDAV PROPFIND 成功但响应体为空")
    }

    private inline fun <T> executeWithReadableErrors(
        source: LibrarySource,
        url: String,
        block: () -> T
    ): T {
        try {
            return block()
        } catch (error: RangeNotSupportedException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw IllegalStateException("WebDAV 连接或读取超时：${error.message ?: "timeout"}", error)
        } catch (error: SSLHandshakeException) {
            throw IllegalStateException(certError(source), error)
        } catch (error: SSLException) {
            throw IllegalStateException(certError(source), error)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("WebDAV 地址无效：$url；${error.message.orEmpty()}", error)
        }
    }

    private fun certError(source: LibrarySource): String =
        "WebDAV HTTPS/证书错误；如果 NAS 使用自签名证书，请为“${source.name}”开启允许不受信任证书"

    private fun httpError(
        source: LibrarySource,
        url: String,
        code: Int,
        message: String,
        body: String?
    ): String = buildString {
        when (code) {
            401, 403 -> append("WebDAV 认证失败或无权限")
            404 -> append("WebDAV 路径不存在")
            else -> append("WebDAV 请求失败")
        }
        append("（HTTP $code")
        if (message.isNotBlank()) append(" $message")
        append("）\n请求 URL：").append(url)
        append("\n服务器：").append(source.webDavBaseUrl.orEmpty())
        append("\n根路径：").append(source.webDavRootPath.orEmpty().ifBlank { "/" })
        body?.replace(Regex("\\s+"), " ")?.trim()?.take(300)?.takeIf(String::isNotBlank)?.let {
            append("\n响应片段：").append(it)
        }
    }

    private fun client(source: LibrarySource): OkHttpClient {
        val key = ClientKey(
            source.connectTimeoutSeconds.coerceIn(5, 300),
            source.readTimeoutSeconds.coerceIn(5, 600),
            source.webDavAllowInsecureTls
        )
        return clientCache.getOrPut(key) {
            val builder = OkHttpClient.Builder()
                .connectTimeout(key.connect.toLong(), TimeUnit.SECONDS)
                .readTimeout(key.read.toLong(), TimeUnit.SECONDS)
            if (key.insecure) {
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf(trustManager), SecureRandom())
                builder.sslSocketFactory(context.socketFactory, trustManager)
                builder.hostnameVerifier { _, _ -> true }
            }
            builder.build()
        }
    }

    private fun parseMultistatus(bytes: ByteArray, requestUrl: String): List<WebDavEntry> {
        val document = xmlFactory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val requestPath = URI(requestUrl).path.trimEnd('/')
        val responses = document.getElementsByTagNameNS("*", "response")
        return buildList {
            for (index in 0 until responses.length) {
                val element = responses.item(index) as? Element ?: continue
                val href = element.childText("href") ?: continue
                val hrefUri = runCatching { URI(href) }.getOrNull()
                val hrefPath = (hrefUri?.path ?: href).trimEnd('/')
                if (hrefPath == requestPath) continue
                val encodedName = hrefPath.substringAfterLast('/').ifBlank { continue }
                val requestUri = URI(requestUrl)
                val absolute = if (href.startsWith("http://") || href.startsWith("https://")) {
                    href
                } else {
                    URI(requestUri.scheme, requestUri.authority, hrefUri?.path ?: href, hrefUri?.query, null).toString()
                }
                add(
                    WebDavEntry(
                        name = decodeName(encodedName),
                        url = absolute,
                        isDirectory = element.getElementsByTagNameNS("*", "collection").length > 0,
                        size = element.childText("getcontentlength")?.toLongOrNull() ?: 0L,
                        lastModified = element.childText("getlastmodified")?.let(::parseHttpDate) ?: 0L,
                        etag = element.childText("getetag")?.trim()?.trim('"')?.takeIf(String::isNotBlank)
                    )
                )
            }
        }
    }

    private fun Element.childText(name: String): String? {
        val nodes = getElementsByTagNameNS("*", name)
        return if (nodes.length == 0) null else nodes.item(0)?.textContent?.trim()?.takeIf(String::isNotBlank)
    }

    private fun parseHttpDate(value: String): Long? {
        for (format in HTTP_DATE_FORMATS) {
            val parsed = runCatching { SimpleDateFormat(format, Locale.US).parse(value)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun decodeName(value: String): String = runCatching {
        java.net.URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrDefault(value)

    private data class ClientKey(val connect: Int, val read: Int, val insecure: Boolean)

    private companion object {
        val CONTENT_RANGE = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
        val HTTP_DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy"
        )
    }
}

class RangeNotSupportedException(message: String) : java.io.IOException(message)

data class WebDavEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val etag: String?
)
