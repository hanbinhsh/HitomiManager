package com.ice.hitomimanager.domain.reader

import android.content.Context
import android.graphics.BitmapFactory
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.model.PageInfo
import com.ice.hitomimanager.data.model.RemoteArchiveReadMode
import com.ice.hitomimanager.data.model.RemoteArchiveSettings
import com.ice.hitomimanager.data.model.RemoteReaderProgress
import com.ice.hitomimanager.data.remote.RangeNotSupportedException
import com.ice.hitomimanager.data.remote.WebDavClient
import com.ice.hitomimanager.domain.util.NaturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.Enumeration

interface ComicArchiveSession : Closeable {
    suspend fun listPages(): List<String>
    suspend fun extractPage(entryName: String, pageIndex: Int): ComicArchiveReader.ExtractedPage?
    suspend fun extractPersistentCover(): File?
}

class ComicArchiveSessionFactory(
    private val context: Context,
    private val client: WebDavClient,
    private val cache: RemoteArchiveCache
) {
    suspend fun openRemote(
        book: BookItem,
        source: LibrarySource,
        password: String?,
        settings: RemoteArchiveSettings,
        allowFullDownload: Boolean,
        onProgress: (RemoteReaderProgress) -> Unit = {}
    ): ComicArchiveSession = withContext(Dispatchers.IO) {
        require(book.fileSize > 0L) { "远程压缩包缺少文件大小，需重新扫描来源" }
        val key = cache.key(
            sourceId = source.id,
            url = book.uriString,
            etag = book.remoteEtag,
            size = book.fileSize,
            lastModified = book.lastModified
        )
        val complete = cache.completeFile(key)
        val cachedZip = if (complete.isFile && complete.length() > 0L) {
            runCatching { openZipFile(complete) }
                .onFailure { complete.delete() }
                .getOrNull()
        } else {
            null
        }
        val zip = when {
            cachedZip != null -> {
                onProgress(RemoteReaderProgress("使用完整缓存", complete.length(), complete.length()))
                cachedZip
            }
            settings.readMode == RemoteArchiveReadMode.DownloadOnly -> {
                require(allowFullDownload) { "当前操作不允许完整下载远程压缩包" }
                openZipFile(download(source, password, book, key, onProgress))
            }
            else -> {
                try {
                    onProgress(RemoteReaderProgress("读取远程压缩包目录", 0L, book.fileSize))
                    val channel = HttpRangeSeekableByteChannel(
                        client = client,
                        source = source,
                        password = password,
                        url = book.uriString,
                        contentLength = book.fileSize,
                        blockSize = settings.rangeBlockSizeBytes,
                        cache = cache,
                        cacheKey = key
                    )
                    ZipFile.builder().setSeekableByteChannel(channel).get()
                } catch (error: Throwable) {
                    val canFallback = settings.readMode == RemoteArchiveReadMode.RangeWithDownloadFallback &&
                        allowFullDownload
                    if (!canFallback) {
                        if (error is RangeNotSupportedException) throw error
                        throw IllegalStateException("远程 ZIP Range 读取失败：${error.message.orEmpty()}", error)
                    }
                    onProgress(RemoteReaderProgress("Range 不可用，准备完整下载", 0L, book.fileSize))
                    openZipFile(download(source, password, book, key, onProgress))
                }
            }
        }
        RemoteZipComicArchiveSession(
            context = context,
            zip = zip,
            archiveKey = key,
            cache = cache,
            settings = settings
        )
    }

    private fun download(
        source: LibrarySource,
        password: String?,
        book: BookItem,
        key: String,
        onProgress: (RemoteReaderProgress) -> Unit
    ): File {
        val partial = cache.partialFile(key)
        partial.delete()
        try {
            client.download(
                source = source,
                password = password,
                url = book.uriString,
                target = partial,
                expectedSize = book.fileSize
            ) { done, total ->
                onProgress(RemoteReaderProgress("下载完整压缩包", done, total))
            }
            return cache.finishDownload(key)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private fun openZipFile(file: File): ZipFile = ZipFile.builder().setFile(file).get()
}

private class RemoteZipComicArchiveSession(
    private val context: Context,
    private val zip: ZipFile,
    private val archiveKey: String,
    private val cache: RemoteArchiveCache,
    private val settings: RemoteArchiveSettings
) : ComicArchiveSession {
    private val mutex = Mutex()
    private var pageNames: List<String>? = null

    override suspend fun listPages(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            pageNames ?: zip.entries.toList()
                .filter { !it.isDirectory && ComicArchiveReader.isImageEntryName(it.name) }
                .map(ZipArchiveEntry::getName)
                .sortedWith(NaturalOrder::compare)
                .also { pageNames = it }
        }
    }

    override suspend fun extractPage(
        entryName: String,
        pageIndex: Int
    ): ComicArchiveReader.ExtractedPage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entry = zip.getEntry(entryName) ?: return@withLock null
            val extension = entryName.substringAfterLast('.', "webp").lowercase()
            val directory = File(context.cacheDir, "reader_pages/$archiveKey").apply { mkdirs() }
            val output = File(directory, "page_$pageIndex.$extension")
            if (!output.isFile || output.length() <= 0L) {
                val temporary = File(directory, "${output.name}.part")
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(temporary).use(input::copyTo)
                }
                if (!temporary.renameTo(output)) {
                    temporary.copyTo(output, overwrite = true)
                    temporary.delete()
                }
            }
            val bounds = readBounds(output)
            ComicArchiveReader.ExtractedPage(
                file = output,
                info = PageInfo(
                    entryName = entryName,
                    modifiedTimeMillis = entry.lastModifiedDate?.time?.takeIf { it > 0L },
                    sizeBytes = entry.size.takeIf { it > 0L } ?: output.length(),
                    width = bounds.first,
                    height = bounds.second
                )
            )
        }
    }

    override suspend fun extractPersistentCover(): File? {
        val pages = listPages()
        val first = pages.firstOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val entry = zip.getEntry(first) ?: return@withLock null
                val extension = first.substringAfterLast('.', "webp").lowercase()
                val directory = File(context.filesDir, "covers/$archiveKey").apply { mkdirs() }
                val output = File(directory, "cover.$extension")
                if (!output.isFile || output.length() <= 0L) {
                    val temporary = File(directory, "${output.name}.part")
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(temporary).use(input::copyTo)
                    }
                    if (!temporary.renameTo(output)) {
                        temporary.copyTo(output, overwrite = true)
                        temporary.delete()
                    }
                }
                output.takeIf { it.isFile && it.length() > 0L }
            }
        }
    }

    override fun close() {
        runCatching { zip.close() }
        cache.release(archiveKey, settings.cachePolicy, settings.cacheLimitBytes)
    }

    private fun readBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return (options.outWidth.takeIf { it > 0 } ?: 0) to
            (options.outHeight.takeIf { it > 0 } ?: 0)
    }

    private fun <T> Enumeration<T>.toList(): List<T> = buildList {
        while (hasMoreElements()) add(nextElement())
    }
}
