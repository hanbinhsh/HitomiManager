package com.ice.hitomimanager.data.model

enum class LibrarySourceType {
    LocalSaf,
    WebDav
}

enum class RemoteIndexMode {
    Light,
    Full
}

enum class RemoteArchiveReadMode {
    RangeWithDownloadFallback,
    RangeOnly,
    DownloadOnly
}

enum class RemoteCachePolicy {
    Lru,
    Session,
    Persistent
}

data class LibrarySource(
    val id: String,
    val name: String,
    val type: LibrarySourceType,
    val rootUriString: String,
    val webDavBaseUrl: String? = null,
    val webDavRootPath: String? = null,
    val webDavUsername: String? = null,
    val webDavAllowInsecureTls: Boolean = false,
    val remoteIndexMode: RemoteIndexMode = RemoteIndexMode.Full,
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 60,
    val hasStoredPassword: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastCompletedScanAt: Long? = null
) {
    val isRemote: Boolean
        get() = type == LibrarySourceType.WebDav
}

data class WebDavSourceForm(
    val editingSourceId: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val rootPath: String = "/",
    val username: String = "",
    val password: String = "",
    val allowInsecureTls: Boolean = false,
    val indexMode: RemoteIndexMode = RemoteIndexMode.Full,
    val connectTimeoutSecondsText: String = "15",
    val readTimeoutSecondsText: String = "60"
)

data class RemoteArchiveSettings(
    val readMode: RemoteArchiveReadMode = RemoteArchiveReadMode.RangeWithDownloadFallback,
    val cachePolicy: RemoteCachePolicy = RemoteCachePolicy.Lru,
    val cacheLimitBytes: Long = 2L * 1024L * 1024L * 1024L,
    val rangeBlockSizeBytes: Int = 512 * 1024,
    val allowBatchFullDownload: Boolean = false
)

data class RemoteReaderProgress(
    val stage: String,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L
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
