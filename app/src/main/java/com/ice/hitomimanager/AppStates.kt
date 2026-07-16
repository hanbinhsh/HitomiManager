package com.ice.hitomimanager

import com.ice.hitomimanager.data.local.entity.MatchCandidateEntity
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import com.ice.hitomimanager.data.local.entity.TagEntity
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.BookSortMode
import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.model.HomeTab
import com.ice.hitomimanager.data.model.LibraryFolderNode
import com.ice.hitomimanager.data.model.LibraryLayoutMode
import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.model.LibrarySourceScope
import com.ice.hitomimanager.data.model.MatchTaskFilter
import com.ice.hitomimanager.data.model.PageInfo
import com.ice.hitomimanager.data.model.SettingsTab
import com.ice.hitomimanager.data.model.RemoteArchiveReadMode
import com.ice.hitomimanager.data.model.RemoteCachePolicy
import com.ice.hitomimanager.data.model.RemoteReaderProgress
import com.ice.hitomimanager.data.model.TagCountItem
import com.ice.hitomimanager.data.model.TagFilterTab
import com.ice.hitomimanager.data.model.TagSortMode
import java.io.File

data class LibraryUiState(
    val folderUriString: String? = null,
    val librarySources: List<LibrarySource> = emptyList(),
    val sourceScopes: List<LibrarySourceScope> = emptyList(),
    val selectedSourceScopeKey: String = "all",
    val currentDirectorySourceId: String? = null,
    val currentDirectoryPath: String = "",
    val directoryFolders: List<LibraryFolderNode> = emptyList(),
    val directoryBooks: List<BookItem> = emptyList(),
    val directoryContentVersion: Long = 0L,
    val directoryScrollToken: Long = 0L,
    val books: List<BookItem> = emptyList(),
    val bookCount: Int = 0,
    val bookSortMode: BookSortMode = BookSortMode.NameAsc,
    val homeTab: HomeTab = HomeTab.Library,
    val homeTabReselectTick: Long = 0L,
    val selectedTagKeys: Set<String> = emptySet(),
    val tagItems: List<TagCountItem> = emptyList(),
    val tagSortMode: TagSortMode = TagSortMode.CountDesc,
    val searchQuery: String = "",
    val taskFilter: MatchTaskFilter = MatchTaskFilter.All,
    val matchTasks: List<MatchTaskEntity> = emptyList(),
    val unqueuedUnmatchedBooks: List<BookItem> = emptyList(),
    val matchTaskFilterCounts: Map<MatchTaskFilter, Int> = emptyMap(),
    val isBatchMatching: Boolean = false,
    val isScanning: Boolean = false,
    val error: String? = null,
    val tagFilterTab: TagFilterTab = TagFilterTab.Tag,
    val scanDone: Int = 0,
    val scanTotal: Int = 0,
    val scanCurrentName: String? = null
)

data class MatchTaskDetailUiState(
    val task: MatchTaskEntity? = null,
    val candidates: List<MatchCandidateEntity> = emptyList(),
    val isBinding: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

data class SettingsUiState(
    val folderUriString: String? = null,
    val librarySources: List<LibrarySource> = emptyList(),
    val showTagNamespacePrefix: Boolean = true,
    val distinguishGenderTags: Boolean = true,
    val removeUnderscoreInMatchTitle: Boolean = true,
    val removeTrailingNumberSuffixInMatchTitle: Boolean = true,
    val autoMatchExactTitle: Boolean = true,
    val autoMatchSingleResult: Boolean = false,
    val autoMatchUniqueSamePage: Boolean = true,
    val autoMatchSamePageFirst: Boolean = true,
    val autoOpenNextReviewTask: Boolean = true,
    val settingsTab: SettingsTab = SettingsTab.Directory,
    val showRematchButtonInLibrary: Boolean = true,
    val openBookDirectlyInReader: Boolean = false,
    val showGridCoverPlayButton: Boolean = false,
    val libraryLayoutMode: LibraryLayoutMode = LibraryLayoutMode.List,
    val libraryGridColumns: Int = 3,
    val filteredMatchLanguagesText: String = "",
    val filteredMatchLanguages: Set<String> = emptySet(),
    val matchSearchTimeoutSecondsText: String = "30",
    val matchSearchTimeoutSeconds: Int = 30,
    val batchMatchThreadsText: String = "1",
    val batchMatchThreads: Int = 1,
    val remoteArchiveReadMode: RemoteArchiveReadMode = RemoteArchiveReadMode.RangeWithDownloadFallback,
    val remoteCachePolicy: RemoteCachePolicy = RemoteCachePolicy.Lru,
    val remoteCacheLimitMbText: String = "2048",
    val remoteCacheLimitMb: Int = 2048,
    val remoteRangeBlockSizeKbText: String = "512",
    val remoteRangeBlockSizeKb: Int = 512,
    val allowBatchRemoteFullDownload: Boolean = false,
    val remoteCacheUsageBytes: Long = 0L,
    val webDavMessage: String? = null,
    val isTestingWebDav: Boolean = false
)

data class ReaderUiState(
    val book: BookItem? = null,
    val pages: List<String> = emptyList(),
    val pageIndex: Int = 0,
    val pageFiles: Map<Int, File> = emptyMap(),
    val pageInfos: Map<Int, PageInfo> = emptyMap(),
    val loadingPageIndices: Set<Int> = emptySet(),
    val isOpening: Boolean = false,
    val remoteProgress: RemoteReaderProgress? = null,
    val error: String? = null
)

data class BookDetailUiState(
    val book: BookItem? = null,
    val tags: List<TagEntity> = emptyList()
)

data class MatchUiState(
    val book: BookItem? = null,
    val sourceTaskId: Long? = null,
    val query: String = "",
    val localPageCount: Int? = null,
    val remoteProgress: RemoteReaderProgress? = null,
    val candidates: List<HitomiBookMeta> = emptyList(),
    val isSearching: Boolean = false,
    val isBinding: Boolean = false,
    val error: String? = null,
    val searchDiagnosticSummary: String? = null,
    val searchDiagnosticRaw: String? = null,
    val showSearchDiagnosticRaw: Boolean = false
)

data class HitomiWebViewUiState(
    val title: String = "搜索结果",
    val url: String = ""
)
