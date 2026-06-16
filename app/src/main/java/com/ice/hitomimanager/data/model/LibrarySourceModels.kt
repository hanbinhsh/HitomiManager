package com.ice.hitomimanager.data.model

enum class LibrarySourceType {
    LocalSaf
}

data class LibrarySource(
    val id: String,
    val name: String,
    val type: LibrarySourceType,
    val rootUriString: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastCompletedScanAt: Long? = null
)

data class LibrarySourceScope(
    val key: String,
    val label: String,
    val sourceIds: List<String>,
    val folderSourceId: String? = null,
    val folderPath: String? = null
)

data class LibraryFolderNode(
    val sourceId: String,
    val sourceName: String,
    val path: String,
    val parentPath: String?,
    val name: String
)
