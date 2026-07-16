package com.ice.hitomimanager.domain.reader

import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.remote.WebDavClient
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import kotlin.math.min

class HttpRangeSeekableByteChannel(
    private val client: WebDavClient,
    private val source: LibrarySource,
    private val password: String?,
    private val url: String,
    private val contentLength: Long,
    private val blockSize: Int,
    private val cache: RemoteArchiveCache,
    private val cacheKey: String
) : SeekableByteChannel {
    private var currentPosition = 0L
    private var open = true

    @Synchronized
    override fun read(destination: ByteBuffer): Int {
        ensureOpen()
        if (!destination.hasRemaining()) return 0
        if (currentPosition >= contentLength) return -1
        var copied = 0
        while (destination.hasRemaining() && currentPosition < contentLength) {
            val blockIndex = currentPosition / blockSize
            val blockStart = blockIndex * blockSize
            val blockEnd = min(contentLength - 1L, blockStart + blockSize - 1L)
            val bytes = cache.readBlock(cacheKey, blockIndex) ?: client.readRange(
                source = source,
                password = password,
                url = url,
                start = blockStart,
                endInclusive = blockEnd
            ).also { cache.writeBlock(cacheKey, blockIndex, it) }
            val offset = (currentPosition - blockStart).toInt()
            if (offset >= bytes.size) break
            val count = min(destination.remaining(), bytes.size - offset)
            destination.put(bytes, offset, count)
            currentPosition += count
            copied += count
        }
        return if (copied == 0) -1 else copied
    }

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()

    @Synchronized
    override fun position(): Long {
        ensureOpen()
        return currentPosition
    }

    @Synchronized
    override fun position(newPosition: Long): SeekableByteChannel {
        ensureOpen()
        require(newPosition >= 0L) { "position must be non-negative" }
        currentPosition = newPosition
        return this
    }

    override fun size(): Long = contentLength

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()

    override fun isOpen(): Boolean = open

    override fun close() {
        open = false
    }

    private fun ensureOpen() {
        if (!open) throw ClosedChannelException()
    }
}
