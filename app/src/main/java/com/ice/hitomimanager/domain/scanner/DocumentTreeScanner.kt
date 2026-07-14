package com.ice.hitomimanager.domain.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ice.hitomimanager.domain.util.NaturalOrder
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.domain.reader.ComicArchiveReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentTreeScanner(
    private val context: Context
) {
    private val archiveExtensions = setOf("zip", "cbz")
    private val coverCache = CoverCache(context)

    suspend fun scan(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
        onDiscovered: (Set<String>) -> Unit = {},
        onBook: suspend (ScannedBook) -> Unit
    ): List<ScannedFolder> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext emptyList()

        val archiveFiles = mutableListOf<Pair<DocumentFile, String>>()
        val folders = mutableListOf<ScannedFolder>()

        fun walk(dir: DocumentFile, relativeDir: String) {
            val children = dir.listFiles()

            for (child in children) {
                val name = child.name ?: continue
                val childPath = joinPath(relativeDir, name)

                if (child.isDirectory) {
                    folders += ScannedFolder(
                        path = childPath,
                        parentPath = relativeDir.ifBlank { null },
                        name = name
                    )
                    walk(child, childPath)
                    continue
                }

                val ext = name.substringAfterLast('.', missingDelimiterValue = "")
                    .lowercase()

                if (ext in archiveExtensions) {
                    archiveFiles += child to relativeDir
                }
            }
        }

        // 1. 先递归找出所有压缩包
        walk(root, "")

        // 2. 排序后再统计总数
        val sortedArchiveFiles = archiveFiles.sortedWith { a, b ->
            NaturalOrder.compare(a.first.name.orEmpty(), b.first.name.orEmpty())
        }

        val total = sortedArchiveFiles.size
        onDiscovered(sortedArchiveFiles.mapTo(linkedSetOf()) { it.first.uri.toString() })

        // 3. 通知 UI：已经知道总数了，但还没开始处理
        onProgress(
            ScanProgress(
                done = 0,
                total = total,
                currentName = null
            )
        )

        // 4. 逐个处理文件，并在生成封面前后更新进度
        sortedArchiveFiles.forEachIndexed { index, (file, parentPath) ->
            val name = file.name.orEmpty()

            // 开始处理当前文件
            onProgress(
                ScanProgress(
                    done = index,
                    total = total,
                    currentName = name
                )
            )

            val cachedCoverPath = coverCache.getValidCover(file)

            val coverPath = if (cachedCoverPath != null) {
                cachedCoverPath
            } else {
                val generatedCover = runCatching {
                    ComicArchiveReader.extractCoverToPersistentCache(
                        context = context,
                        archiveUri = file.uri
                    )
                }.getOrNull()

                if (generatedCover != null) {
                    coverCache.saveCover(
                        file = file,
                        coverPath = generatedCover.absolutePath
                    )
                }

                generatedCover?.absolutePath
            }

            onBook(ScannedBook(
                displayName = name,
                uriString = file.uri.toString(),
                fileSize = file.length(),
                lastModified = file.lastModified(),
                coverFilePath = coverPath,
                relativePath = joinPath(parentPath, name),
                parentPath = parentPath.ifBlank { null }
            ))

            // 当前文件处理完成
            onProgress(
                ScanProgress(
                    done = index + 1,
                    total = total,
                    currentName = name
                )
            )
        }

        folders.distinctBy { it.path }
    }

    private fun joinPath(parent: String, name: String): String {
        return if (parent.isBlank()) name else "$parent/$name"
    }

}
