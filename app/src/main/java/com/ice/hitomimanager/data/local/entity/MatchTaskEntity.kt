package com.ice.hitomimanager.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "match_task",
    indices = [
        Index(value = ["bookUriString"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
        Index(value = ["libraryRootUriString"]),
        Index(value = ["libraryRootUriString", "status", "updatedAt"])
    ]
)
data class MatchTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val bookUriString: String,
    val displayName: String,
    val coverFilePath: String?,

    val query: String,
    val localPageCount: Int?,

    val status: String,

    val matchedGalleryId: String?,
    val candidateCount: Int,

    val errorMessage: String?,

    val createdAt: Long,
    val updatedAt: Long,
    val libraryRootUriString: String? = null,
)