package com.ice.hitomimanager.domain.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.domain.reader.ComicArchiveReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentTreeScanner(
    private val context: Context
) {
    private val archiveExtensions = setOf("zip", "cbz")
    private val coverCache = CoverCache(context)

    suspend fun scan(treeUri: Uri): List<ScannedBook> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext emptyList()

        val archiveFiles = mutableListOf<DocumentFile>()

        fun walk(dir: DocumentFile) {
            val children = dir.listFiles()

            for (child in children) {
                if (child.isDirectory) {
                    walk(child)
                    continue
                }

                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', missingDelimiterValue = "")
                    .lowercase()

                if (ext in archiveExtensions) {
                    archiveFiles += child
                }
            }
        }

        walk(root)

        archiveFiles
            .sortedWith { a, b ->
                naturalCompare(a.name.orEmpty(), b.name.orEmpty())
            }
            .map { file ->
                val name = file.name.orEmpty()

                val cachedCoverPath = coverCache.getValidCover(file)

                val coverPath = if (cachedCoverPath != null) {
                    cachedCoverPath
                } else {
                    val generatedCover = runCatching {
                        ComicArchiveReader.extractCoverToCache(
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

                ScannedBook(
                    displayName = name,
                    uriString = file.uri.toString(),
                    fileSize = file.length(),
                    lastModified = file.lastModified(),
                    coverFilePath = coverPath
                )
            }
    }

    private fun naturalCompare(a: String, b: String): Int {
        val regex = Regex("(\\d+)|(\\D+)")
        val aa = regex.findAll(a.lowercase()).map { it.value }.toList()
        val bb = regex.findAll(b.lowercase()).map { it.value }.toList()

        val max = maxOf(aa.size, bb.size)

        for (i in 0 until max) {
            val x = aa.getOrNull(i) ?: return -1
            val y = bb.getOrNull(i) ?: return 1

            val xNumber = x.all { it.isDigit() }
            val yNumber = y.all { it.isDigit() }

            val cmp = if (xNumber && yNumber) {
                val nx = x.trimStart('0').ifEmpty { "0" }
                val ny = y.trimStart('0').ifEmpty { "0" }

                when {
                    nx.length != ny.length -> nx.length.compareTo(ny.length)
                    else -> nx.compareTo(ny)
                }
            } else {
                x.compareTo(y)
            }

            if (cmp != 0) return cmp
        }

        return 0
    }
}