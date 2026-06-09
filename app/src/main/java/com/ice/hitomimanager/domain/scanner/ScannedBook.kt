package com.ice.hitomimanager.domain.scanner

data class ScannedBook(
    val displayName: String,
    val uriString: String,
    val fileSize: Long,
    val lastModified: Long,
    val coverFilePath: String?
)