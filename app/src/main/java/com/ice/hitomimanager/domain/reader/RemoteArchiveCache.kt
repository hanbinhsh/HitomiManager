package com.ice.hitomimanager.domain.reader

import android.content.Context
import com.ice.hitomimanager.data.model.RemoteCachePolicy
import java.io.File
import java.security.MessageDigest

class RemoteArchiveCache(context: Context) {
    private val root = File(context.filesDir, "remote_archives").apply { mkdirs() }

    fun key(
        sourceId: String,
        url: String,
        etag: String?,
        size: Long,
        lastModified: Long
    ): String = sha256("$sourceId\n$url\n${etag.orEmpty()}\n$size\n$lastModified")

    fun completeFile(key: String): File = File(entryDir(key), "archive.zip")

    fun partialFile(key: String): File = File(entryDir(key), "archive.zip.part")

    fun blockFile(key: String, blockIndex: Long): File =
        File(File(entryDir(key), "blocks").apply { mkdirs() }, "$blockIndex.bin")

    fun readBlock(key: String, blockIndex: Long): ByteArray? {
        val file = blockFile(key, blockIndex)
        if (!file.isFile || file.length() <= 0L) return null
        file.setLastModified(System.currentTimeMillis())
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun writeBlock(key: String, blockIndex: Long, bytes: ByteArray) {
        val target = blockFile(key, blockIndex)
        val temporary = File(target.parentFile, "${target.name}.part")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        target.setLastModified(System.currentTimeMillis())
    }

    fun finishDownload(key: String): File {
        val partial = partialFile(key)
        require(partial.isFile && partial.length() > 0L) { "远程压缩包下载结果为空" }
        val target = completeFile(key)
        target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        target.setLastModified(System.currentTimeMillis())
        return target
    }

    fun usageBytes(): Long = root.walkTopDown()
        .filter(File::isFile)
        .filterNot { it.name.endsWith(".part") }
        .sumOf(File::length)

    fun trimTo(limitBytes: Long, protectedKey: String? = null) {
        if (limitBytes <= 0L) return
        var used = usageBytes()
        if (used <= limitBytes) return
        val candidates = root.walkTopDown()
            .filter(File::isFile)
            .filterNot { it.name.endsWith(".part") }
            .filterNot { protectedKey != null && it.invariantSeparatorsPath.contains("/$protectedKey/") }
            .sortedBy(File::lastModified)
            .toList()
        for (file in candidates) {
            val length = file.length()
            if (file.delete()) used -= length
            if (used <= limitBytes) break
        }
        removeEmptyDirectories()
    }

    fun release(key: String, policy: RemoteCachePolicy, cacheLimitBytes: Long) {
        when (policy) {
            RemoteCachePolicy.Session -> entryDir(key).deleteRecursively()
            RemoteCachePolicy.Lru -> trimTo(cacheLimitBytes)
            RemoteCachePolicy.Persistent -> Unit
        }
    }

    fun clear() {
        root.listFiles()?.forEach(File::deleteRecursively)
    }

    private fun entryDir(key: String): File = File(root, key).apply { mkdirs() }

    private fun removeEmptyDirectories() {
        root.walkBottomUp().filter(File::isDirectory).filter { it != root }.forEach { dir ->
            if (dir.listFiles().isNullOrEmpty()) dir.delete()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
