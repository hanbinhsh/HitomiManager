package com.ice.hitomimanager.data.model

import com.ice.hitomimanager.data.local.entity.BookEntity

fun BookEntity.toBookItem(): BookItem {
    return BookItem(
        displayName = displayName,
        uriString = uriString,
        coverFilePath = coverFilePath,
        lastModified = lastModified,
        createdAt = createdAt,
        updatedAt = updatedAt,
        libraryRootUriString = libraryRootUriString,
        sourceId = sourceId,
        relativePath = relativePath,
        parentPath = parentPath,
        lastSeenAt = lastSeenAt,
        sourceGalleryId = sourceGalleryId,
        title = title,
        japaneseTitle = japaneseTitle,
        language = language,
        type = type,
        pageCount = pageCount,
        matchStatus = matchStatus
    )
}
