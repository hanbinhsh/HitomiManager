package com.ice.hitomimanager.data.model

data class BookItem(
    val displayName: String,
    val uriString: String,
    val coverFilePath: String? = null,
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
    val matchStatus: String = "unmatched"
)
