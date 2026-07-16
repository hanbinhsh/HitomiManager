package com.ice.hitomimanager.domain.scanner

data class ScannedBook(
    val displayName: String,
    val uriString: String,
    val fileSize: Long,
    val lastModified: Long,
    val coverFilePath: String?,
    val localPageCount: Int? = null,
    val remoteEtag: String? = null,
    val relativePath: String? = null,
    val parentPath: String? = null
)

data class ScannedFolder(
    val path: String,
    val parentPath: String?,
    val name: String
)

data class ScannedLibrary(
    val books: List<ScannedBook>,
    val folders: List<ScannedFolder>
)
