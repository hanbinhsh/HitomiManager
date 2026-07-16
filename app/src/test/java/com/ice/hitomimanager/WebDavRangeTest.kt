package com.ice.hitomimanager

import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.model.LibrarySourceType
import com.ice.hitomimanager.data.remote.RangeNotSupportedException
import com.ice.hitomimanager.data.remote.WebDavClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavRangeTest {
    private val source = LibrarySource(
        id = "test",
        name = "test",
        type = LibrarySourceType.WebDav,
        rootUriString = "webdav:test",
        webDavBaseUrl = "https://example.test"
    )

    @Test
    fun acceptsExactPartialContent() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 2-5/10")
                    .setBody("2345")
            )

            val result = WebDavClient().readRange(
                source = source,
                password = null,
                url = server.url("archive.cbz").toString(),
                start = 2,
                endInclusive = 5
            )

            assertArrayEquals("2345".toByteArray(), result)
        }
    }

    @Test
    fun rejectsServerThatIgnoresRange() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("0123456789"))

            assertThrows(RangeNotSupportedException::class.java) {
                WebDavClient().readRange(
                    source = source,
                    password = null,
                    url = server.url("archive.cbz").toString(),
                    start = 2,
                    endInclusive = 5
                )
            }
        }
    }

    @Test
    fun rejectsMismatchedContentRange() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-3/10")
                    .setBody("0123")
            )

            assertThrows(RangeNotSupportedException::class.java) {
                WebDavClient().readRange(
                    source = source,
                    password = null,
                    url = server.url("archive.cbz").toString(),
                    start = 2,
                    endInclusive = 5
                )
            }
        }
    }
}
