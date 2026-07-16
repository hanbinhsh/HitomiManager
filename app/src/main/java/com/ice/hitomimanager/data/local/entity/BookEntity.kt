package com.ice.hitomimanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book",
    indices = [
        Index(value = ["uriString"], unique = true),
        Index(value = ["sourceGalleryId"]),
        Index(value = ["matchStatus"]),
        Index(value = ["sourceId"]),
        Index(value = ["sourceId", "parentPath"]),
        Index(value = ["displayName", "fileSize"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val displayName: String,
    val uriString: String,

    val fileSize: Long,
    val lastModified: Long,

    val coverFilePath: String?,

    // 后续 hitomi 元数据用
    val source: String? = null,
    val sourceGalleryId: String? = null,
    val title: String? = null,
    val japaneseTitle: String? = null,
    val language: String? = null,
    val type: String? = null,
    val pageCount: Int? = null,
    val localPageCount: Int? = null,
    val remoteEtag: String? = null,

    // unmatched / candidate_found / auto_matched / manual_matched / failed
    val matchStatus: String = "unmatched",

    val createdAt: Long,
    val updatedAt: Long,
    val libraryRootUriString: String? = null,
    val sourceId: String = libraryRootUriString.orEmpty(),
    val relativePath: String? = null,
    val parentPath: String? = null,
    val lastSeenAt: Long? = null,
)
