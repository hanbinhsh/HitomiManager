package com.ice.hitomimanager.domain.scanner

import android.content.Context
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.model.RemoteArchiveSettings
import com.ice.hitomimanager.data.model.RemoteIndexMode
import com.ice.hitomimanager.data.remote.WebDavClient
import com.ice.hitomimanager.domain.reader.ComicArchiveSessionFactory
import com.ice.hitomimanager.domain.util.NaturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WebDavArchiveScanner(
    private val context: Context,
    private val client: WebDavClient,
    private val sessionFactory: ComicArchiveSessionFactory
) {
    suspend fun scan(
        source: LibrarySource,
        password: String?,
        known: Map<String, BookItem>,
        archiveSettings: RemoteArchiveSettings,
        onProgress: (ScanProgress) -> Unit = {},
        onDirectoryFailure: () -> Unit = {},
        onBook: suspend (ScannedBook) -> Unit
    ): List<ScannedFolder> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<ScannedFolder>()
        val queue = ArrayDeque<RemoteFolder>()
        queue += RemoteFolder("", client.buildUrl(source, ""))
        val visited = mutableSetOf<String>()
        var done = 0
        onProgress(ScanProgress(0, 0, source.name))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current.url.trimEnd('/'))) continue
            val entries = try {
                client.listUrl(source, password, current.url)
            } catch (error: Throwable) {
                if (current.path.isBlank()) throw error
                onDirectoryFailure()
                onProgress(
                    ScanProgress(
                        done,
                        0,
                        "跳过目录 ${current.path}：${error.message.orEmpty().lineSequence().firstOrNull().orEmpty()}"
                    )
                )
                continue
            }
            entries.sortedWith { left, right -> NaturalOrder.compare(left.name, right.name) }
                .forEach { entry ->
                    val childPath = joinPath(current.path, entry.name)
                    if (entry.isDirectory) {
                        folders += ScannedFolder(
                            path = childPath,
                            parentPath = current.path.ifBlank { null },
                            name = entry.name
                        )
                        queue += RemoteFolder(childPath, entry.url)
                        return@forEach
                    }
                    if (!isArchive(entry.name)) return@forEach
                    done += 1
                    onProgress(ScanProgress(done, 0, childPath))
                    val actualSize = if (entry.size > 0L) {
                        entry.size
                    } else {
                        runCatching { client.contentLength(source, password, entry.url) }.getOrDefault(0L)
                    }
                    val old = known[entry.url]
                    val unchanged = old != null &&
                        old.fileSize == actualSize &&
                        old.lastModified == entry.lastModified &&
                        (entry.etag.isNullOrBlank() || old.remoteEtag == entry.etag)
                    var coverPath = old?.coverFilePath
                    var localPageCount = old?.localPageCount
                    val coverUsable = coverPath?.let { File(it).isFile && File(it).length() > 0L } == true
                    if (
                        source.remoteIndexMode == RemoteIndexMode.Full &&
                        (!unchanged || !coverUsable || localPageCount == null)
                    ) {
                        val remoteBook = BookItem(
                            displayName = entry.name,
                            uriString = entry.url,
                            sourceId = source.id,
                            fileSize = actualSize,
                            lastModified = entry.lastModified,
                            remoteEtag = entry.etag
                        )
                        try {
                            sessionFactory.openRemote(
                                book = remoteBook,
                                source = source,
                                password = password,
                                settings = archiveSettings,
                                allowFullDownload = true
                            ) { progress ->
                                onProgress(ScanProgress(done, 0, "$childPath · ${progress.stage}"))
                            }.use { session ->
                                val pages = session.listPages()
                                localPageCount = pages.size
                                coverPath = session.extractPersistentCover()?.absolutePath ?: coverPath
                            }
                        } catch (error: Throwable) {
                            onProgress(
                                ScanProgress(
                                    done,
                                    0,
                                    "$childPath · 索引失败：${error.message.orEmpty().lineSequence().firstOrNull().orEmpty()}"
                                )
                            )
                        }
                    }
                    onBook(
                        ScannedBook(
                            displayName = entry.name,
                            uriString = entry.url,
                            fileSize = actualSize,
                            lastModified = entry.lastModified,
                            coverFilePath = coverPath,
                            localPageCount = localPageCount,
                            remoteEtag = entry.etag,
                            relativePath = childPath,
                            parentPath = current.path.ifBlank { null }
                        )
                    )
                }
        }
        folders.distinctBy(ScannedFolder::path)
    }

    private fun isArchive(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("zip", "cbz")

    private fun joinPath(parent: String, child: String): String =
        if (parent.isBlank()) child else "$parent/$child"

    private data class RemoteFolder(val path: String, val url: String)
}
