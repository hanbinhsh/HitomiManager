package com.ice.hitomimanager.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class BookItem(
    val displayName: String,
    val uriString: String,
    val coverFilePath: String? = null,
    val fileSize: Long = 0L,
    val lastModified: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val libraryRootUriString: String? = null,
    val sourceId: String = libraryRootUriString.orEmpty(),
    val relativePath: String? = null,
    val parentPath: String? = null,
    val lastSeenAt: Long? = null,

    val sourceGalleryId: String? = null,
    val title: String? = null,
    val japaneseTitle: String? = null,
    val language: String? = null,
    val type: String? = null,
    val pageCount: Int? = null,
    val localPageCount: Int? = null,
    val remoteEtag: String? = null,
    val matchStatus: String = "unmatched"
)
