package com.ice.hitomimanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.model.LibrarySourceType

@Entity(
    tableName = "library_source",
    indices = [
        Index(value = ["type"]),
        Index(value = ["rootUriString"], unique = true)
    ]
)
data class LibrarySourceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val rootUriString: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastCompletedScanAt: Long?
)

fun LibrarySourceEntity.toLibrarySource(): LibrarySource {
    return LibrarySource(
        id = id,
        name = name,
        type = runCatching { LibrarySourceType.valueOf(type) }
            .getOrDefault(LibrarySourceType.LocalSaf),
        rootUriString = rootUriString,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastCompletedScanAt = lastCompletedScanAt
    )
}

fun LibrarySource.toEntity(): LibrarySourceEntity {
    return LibrarySourceEntity(
        id = id,
        name = name,
        type = type.name,
        rootUriString = rootUriString,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastCompletedScanAt = lastCompletedScanAt
    )
}
