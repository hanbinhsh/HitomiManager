package com.ice.hitomimanager.domain.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.ice.hitomimanager.data.model.PageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ComicArchiveReader {

    data class ExtractedPage(
        val file: File,
        val info: PageInfo
    )

    private val imageExtensions = setOf(
        "jpg",
        "jpeg",
        "png",
        "webp",
        "gif"
    )

    suspend fun listPages(
        context: Context,
        archiveUri: Uri
    ): List<String> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<String>()

        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break

                    if (!entry.isDirectory && isImageEntry(entry.name)) {
                        pages += entry.name
                    }

                    zip.closeEntry()
                }
            }
        }

        pages.sortedWith { a, b -> naturalCompare(a, b) }
    }

    suspend fun extractCoverToCache(
        context: Context,
        archiveUri: Uri
    ): File? = withContext(Dispatchers.IO) {
        val pages = listPages(context, archiveUri)
        val firstPage = pages.firstOrNull() ?: return@withContext null

        extractPageWithInfo(
            context = context,
            archiveUri = archiveUri,
            entryName = firstPage,
            pageIndex = 0
        )?.file
    }

    suspend fun extractCoverToPersistentCache(
        context: Context,
        archiveUri: Uri
    ): File? = withContext(Dispatchers.IO) {
        val pages = listPages(context, archiveUri)
        val firstPage = pages.firstOrNull() ?: return@withContext null
        val archiveKey = archiveUri.toString()
            .hashCode()
            .toString()
            .replace("-", "m")
        val ext = firstPage.substringAfterLast('.', missingDelimiterValue = "webp")
            .lowercase()
        val dir = File(context.filesDir, "covers/$archiveKey")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val outFile = File(dir, "cover.$ext")
        if (outFile.exists() && outFile.length() > 0L) {
            return@withContext outFile
        }

        var found = false

        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break

                    if (!entry.isDirectory && entry.name == firstPage) {
                        found = true
                        FileOutputStream(outFile).use { output ->
                            zip.copyTo(output)
                        }
                        zip.closeEntry()
                        break
                    }

                    zip.closeEntry()
                }
            }
        }

        if (found && outFile.exists() && outFile.length() > 0L) {
            outFile
        } else {
            outFile.delete()
            null
        }
    }

    suspend fun extractPageWithInfo(
        context: Context,
        archiveUri: Uri,
        entryName: String,
        pageIndex: Int
    ): ExtractedPage? = withContext(Dispatchers.IO) {
        val archiveKey = archiveUri.toString()
            .hashCode()
            .toString()
            .replace("-", "m")

        val ext = entryName.substringAfterLast('.', missingDelimiterValue = "webp")
            .lowercase()

        val dir = File(context.cacheDir, "reader_pages/$archiveKey")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val outFile = File(dir, "page_$pageIndex.$ext")

        var found = false
        var modifiedTimeMillis: Long? = null
        var sizeFromZip: Long = -1L

        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break

                    if (!entry.isDirectory && entry.name == entryName) {
                        found = true
                        modifiedTimeMillis = entry.time.takeIf { it > 0L }
                        sizeFromZip = entry.size

                        if (!outFile.exists() || outFile.length() <= 0L) {
                            FileOutputStream(outFile).use { output ->
                                zip.copyTo(output)
                            }
                        }

                        zip.closeEntry()
                        break
                    }

                    zip.closeEntry()
                }
            }
        }

        if (!found || !outFile.exists() || outFile.length() <= 0L) {
            return@withContext null
        }

        val resolution = readImageResolution(outFile)
        val sizeBytes = if (sizeFromZip > 0L) sizeFromZip else outFile.length()

        ExtractedPage(
            file = outFile,
            info = PageInfo(
                entryName = entryName,
                modifiedTimeMillis = modifiedTimeMillis,
                sizeBytes = sizeBytes,
                width = resolution.first,
                height = resolution.second
            )
        )
    }

    private fun readImageResolution(file: File): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeFile(file.absolutePath, options)

            val width = options.outWidth.takeIf { it > 0 } ?: 0
            val height = options.outHeight.takeIf { it > 0 } ?: 0

            width to height
        } catch (_: Exception) {
            0 to 0
        }
    }

    private fun isImageEntry(name: String): Boolean {
        val normalized = name.replace("\\", "/")
        val lower = normalized.lowercase()
        val fileName = lower.substringAfterLast('/')

        if (lower.contains("__macosx/")) return false
        if (fileName == ".ds_store") return false
        if (fileName.startsWith(".")) return false

        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
        return ext in imageExtensions
    }

    private fun naturalCompare(a: String, b: String): Int {
        val aa = tokenizePath(a)
        val bb = tokenizePath(b)

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

    private fun tokenizePath(path: String): List<String> {
        val normalized = path.replace("\\", "/").lowercase()
        val regex = Regex("(\\d+)|(\\D+)")
        return regex.findAll(normalized).map { it.value }.toList()
    }
}
