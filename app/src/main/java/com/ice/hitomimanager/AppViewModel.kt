package com.ice.hitomimanager

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ice.hitomimanager.data.local.entity.MatchCandidateEntity
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.BookSortMode
import com.ice.hitomimanager.data.model.PageInfo
import com.ice.hitomimanager.domain.reader.ComicArchiveReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import com.ice.hitomimanager.data.repository.LibraryRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.repository.HitomiMetadataRepository
import com.ice.hitomimanager.data.local.entity.TagEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ice.hitomimanager.data.model.HomeTab
import com.ice.hitomimanager.data.model.HitomiSearchMetaResult
import com.ice.hitomimanager.data.model.MatchTaskFilter
import com.ice.hitomimanager.data.model.MatchTaskStatus
import com.ice.hitomimanager.data.model.SettingsTab
import com.ice.hitomimanager.data.model.TagCountItem
import com.ice.hitomimanager.data.model.TagSortMode
import com.ice.hitomimanager.data.model.LibraryLayoutMode
import com.ice.hitomimanager.data.model.LibraryFolderNode
import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.model.LibrarySourceScope
import com.ice.hitomimanager.data.model.TagFilterTab
import java.util.Locale

private const val ALL_SOURCES_KEY = "all"
private const val LOCAL_SOURCES_KEY = "local"

data class LibraryUiState(
    val folderUriString: String? = null,
    val librarySources: List<LibrarySource> = emptyList(),
    val sourceScopes: List<LibrarySourceScope> = emptyList(),
    val selectedSourceScopeKey: String = ALL_SOURCES_KEY,
    val currentDirectorySourceId: String? = null,
    val currentDirectoryPath: String = "",
    val directoryFolders: List<LibraryFolderNode> = emptyList(),
    val directoryBooks: List<BookItem> = emptyList(),
    val directoryContentVersion: Long = 0L,
    val directoryScrollToken: Long = 0L,
    val books: List<BookItem> = emptyList(),
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
    val scanCurrentName: String? = null,
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

    // 性别标签区分：开启时 male/female 标签分开计数并显示 ♂/♀；
    // 关闭时同名标签合并计数、不区分性别
    val distinguishGenderTags: Boolean = true,

    val removeUnderscoreInMatchTitle: Boolean = true,
    val removeTrailingNumberSuffixInMatchTitle: Boolean = true,

    // 新增：名称完全相同，默认开启
    val autoMatchExactTitle: Boolean = true,

    // 原有：搜索结果仅一个，默认关闭
    val autoMatchSingleResult: Boolean = false,

    // 新增：唯一页数相同，默认开启
    val autoMatchUniqueSamePage: Boolean = true,

    // 原有：页数相同的第一个，默认开启
    val autoMatchSamePageFirst: Boolean = true,

    val autoOpenNextReviewTask: Boolean = true,

    val settingsTab: SettingsTab = SettingsTab.General,

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
)

data class ReaderUiState(
    val book: BookItem? = null,
    val pages: List<String> = emptyList(),
    val pageIndex: Int = 0,
    val pageFiles: Map<Int, File> = emptyMap(),
    val pageInfos: Map<Int, PageInfo> = emptyMap(),
    val loadingPageIndices: Set<Int> = emptySet(),
    val isOpening: Boolean = false,
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
    val candidates: List<HitomiBookMeta> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val searchDiagnosticSummary: String? = null,
    val searchDiagnosticRaw: String? = null,
    val showSearchDiagnosticRaw: Boolean = false
)

data class HitomiWebViewUiState(
    val title: String = "搜索结果",
    val url: String = ""
)

class AppViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val app = application

    private val hitomiRepository = HitomiMetadataRepository(app)

    private val _matchState = MutableStateFlow(MatchUiState())

    private val _bookDetailState = MutableStateFlow(BookDetailUiState())
    val bookDetailState: StateFlow<BookDetailUiState> = _bookDetailState.asStateFlow()

    private var libraryObserveJob: Job? = null
    private var sourceObserveJob: Job? = null
    private var directoryFolderObserveJob: Job? = null
    private var directoryBookObserveJob: Job? = null
    private var shouldScrollDirectoryToTop: Boolean = false
    private var tagObserveJob: Job? = null

    private var taskObserveJob: Job? = null
    private var taskCountObserveJob: Job? = null
    private val coverRepairingBookUris = mutableSetOf<String>()
    private val coverRepairAttemptedBookUris = mutableSetOf<String>()

    val matchState: StateFlow<MatchUiState> = _matchState.asStateFlow()

    private val _hitomiWebViewState = MutableStateFlow(HitomiWebViewUiState())
    val hitomiWebViewState: StateFlow<HitomiWebViewUiState> =
        _hitomiWebViewState.asStateFlow()

    private val prefs = app.getSharedPreferences(
        "hitomi_manager_prefs",
        Context.MODE_PRIVATE
    )

    private val libraryRepository = LibraryRepository(app)
    private val initialSelectedSourceScopeKey = prefs.getString(
        KEY_SELECTED_SOURCE_SCOPE,
        ALL_SOURCES_KEY
    ) ?: ALL_SOURCES_KEY

    private val _libraryState = MutableStateFlow(
        LibraryUiState(
            folderUriString = prefs.getString(KEY_FOLDER_URI, null),
            selectedSourceScopeKey = initialSelectedSourceScopeKey,
            bookSortMode = readBookSortMode()
        )
    )
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    private val _readerState = MutableStateFlow(ReaderUiState())
    val readerState: StateFlow<ReaderUiState> = _readerState.asStateFlow()

    private var matchTaskDetailJob: Job? = null
    private var matchTaskCandidateJob: Job? = null

    private val _matchTaskDetailState = MutableStateFlow(MatchTaskDetailUiState())
    val matchTaskDetailState: StateFlow<MatchTaskDetailUiState> =
        _matchTaskDetailState.asStateFlow()

    private val _settingsState = MutableStateFlow(
        SettingsUiState(
            folderUriString = prefs.getString(KEY_FOLDER_URI, null),
            showTagNamespacePrefix = prefs.getBoolean(KEY_SHOW_TAG_NAMESPACE_PREFIX, true),
            distinguishGenderTags = prefs.getBoolean(KEY_DISTINGUISH_GENDER_TAGS, true),
            removeUnderscoreInMatchTitle = prefs.getBoolean(KEY_REMOVE_UNDERSCORE_IN_MATCH_TITLE, true),
            removeTrailingNumberSuffixInMatchTitle = prefs.getBoolean(KEY_REMOVE_TRAILING_NUMBER_SUFFIX_IN_MATCH_TITLE, true),
            autoMatchExactTitle = prefs.getBoolean(KEY_AUTO_MATCH_EXACT_TITLE, true),
            autoMatchSingleResult = prefs.getBoolean(KEY_AUTO_MATCH_SINGLE_RESULT, false),
            autoMatchUniqueSamePage = prefs.getBoolean(KEY_AUTO_MATCH_UNIQUE_SAME_PAGE, true),
            autoMatchSamePageFirst = prefs.getBoolean(KEY_AUTO_MATCH_SAME_PAGE_FIRST, true),
            autoOpenNextReviewTask = prefs.getBoolean(KEY_AUTO_OPEN_NEXT_REVIEW_TASK, true),
            showRematchButtonInLibrary = prefs.getBoolean(KEY_SHOW_REMATCH_BUTTON_IN_LIBRARY, true),
            openBookDirectlyInReader = prefs.getBoolean(KEY_OPEN_BOOK_DIRECTLY_IN_READER, false),
            showGridCoverPlayButton = prefs.getBoolean(KEY_SHOW_GRID_COVER_PLAY_BUTTON, false),
            filteredMatchLanguagesText = prefs.getString(KEY_FILTERED_MATCH_LANGUAGES, null).orEmpty(),
            filteredMatchLanguages = parseFilteredMatchLanguages(
                prefs.getString(KEY_FILTERED_MATCH_LANGUAGES, null).orEmpty()
            ),
            matchSearchTimeoutSeconds = prefs.getInt(
                KEY_MATCH_SEARCH_TIMEOUT_SECONDS,
                DEFAULT_MATCH_SEARCH_TIMEOUT_SECONDS
            ).coerceIn(
                MIN_MATCH_SEARCH_TIMEOUT_SECONDS,
                MAX_MATCH_SEARCH_TIMEOUT_SECONDS
            ),
            matchSearchTimeoutSecondsText = prefs.getInt(
                KEY_MATCH_SEARCH_TIMEOUT_SECONDS,
                DEFAULT_MATCH_SEARCH_TIMEOUT_SECONDS
            ).coerceIn(
                MIN_MATCH_SEARCH_TIMEOUT_SECONDS,
                MAX_MATCH_SEARCH_TIMEOUT_SECONDS
            ).toString(),
            batchMatchThreads = prefs.getInt(
                KEY_BATCH_MATCH_THREADS,
                DEFAULT_BATCH_MATCH_THREADS
            ).coerceIn(
                MIN_BATCH_MATCH_THREADS,
                MAX_BATCH_MATCH_THREADS
            ),
            batchMatchThreadsText = prefs.getInt(
                KEY_BATCH_MATCH_THREADS,
                DEFAULT_BATCH_MATCH_THREADS
            ).coerceIn(
                MIN_BATCH_MATCH_THREADS,
                MAX_BATCH_MATCH_THREADS
            ).toString(),
            libraryLayoutMode = runCatching {
                LibraryLayoutMode.valueOf(
                    prefs.getString(KEY_LIBRARY_LAYOUT_MODE, LibraryLayoutMode.List.name)
                        ?: LibraryLayoutMode.List.name
                )
            }.getOrDefault(LibraryLayoutMode.List),

            libraryGridColumns = prefs.getInt(KEY_LIBRARY_GRID_COLUMNS, 3),
        )
    )

    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    init {
        recoverInterruptedMatchTasks()
        viewModelScope.launch {
            runCatching {
                libraryRepository.ensureLegacyLocalSource(prefs.getString(KEY_FOLDER_URI, null))
            }.onFailure { error ->
                reportStartupDataError(error)
                return@launch
            }
            observeSources()
        }
    }

    private fun recoverInterruptedMatchTasks() {
        viewModelScope.launch {
            runCatching {
                libraryRepository.markInterruptedMatchTasksAsFailed()
            }
        }
    }

    private fun observeSources() {
        sourceObserveJob?.cancel()
        sourceObserveJob = viewModelScope.launch {
            try {
                libraryRepository.observeSources().collectLatest { sources ->
                    val scopes = buildSourceScopes(sources)
                    val currentKey = _libraryState.value.selectedSourceScopeKey
                    val fixedKey = when {
                        scopes.any { it.key == currentKey } -> currentKey
                        scopes.isNotEmpty() -> scopes.first().key
                        else -> ALL_SOURCES_KEY
                    }
                    val selected = scopes.firstOrNull { it.key == fixedKey }
                    val concreteSourceId = selected?.sourceIds?.singleOrNull()
                    _libraryState.update {
                        it.copy(
                            librarySources = sources,
                            sourceScopes = scopes,
                            selectedSourceScopeKey = fixedKey,
                            folderUriString = selected?.sourceIds?.singleOrNull() ?: selected?.key,
                            currentDirectorySourceId = concreteSourceId,
                            currentDirectoryPath = selected?.folderPath.orEmpty()
                        )
                    }
                    _settingsState.update {
                        it.copy(
                            librarySources = sources,
                            folderUriString = sources.firstOrNull()?.rootUriString
                        )
                    }
                    refreshLibraryBooks()
                    observeTagItems()
                    observeMatchTasks()
                    observeMatchTaskFilterCounts()
                    observeDirectory()
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                reportStartupDataError(error)
            }
        }
    }

    private fun reportStartupDataError(error: Throwable) {
        val message = error.message.orEmpty()
        val readable = when {
            message.contains("migration", ignoreCase = true) ||
                message.contains("Room cannot verify", ignoreCase = true) -> {
                "数据库版本不匹配，当前安装包无法打开已有数据库。请确认已安装最新构建；原始错误：$message"
            }

            message.isNotBlank() -> "数据库打开失败：$message"

            else -> "数据库打开失败：${error::class.java.simpleName}"
        }

        _libraryState.update {
            it.copy(
                isScanning = false,
                error = readable
            )
        }
        _settingsState.update {
            it.copy(
                librarySources = emptyList()
            )
        }
    }

    private fun observeMatchTasks() {
        taskObserveJob?.cancel()

        val sourceIds = currentSourceIds()

        if (_libraryState.value.librarySources.isEmpty()) {
            _libraryState.update {
                it.copy(
                    matchTasks = emptyList(),
                    unqueuedUnmatchedBooks = emptyList()
                )
            }
            return
        }

        val filter = _libraryState.value.taskFilter

        taskObserveJob = viewModelScope.launch {
            if (filter == MatchTaskFilter.Unqueued) {
                libraryRepository.observeUnqueuedUnmatchedBooksForSourceIds(sourceIds)
                    .collectLatest { books ->
                        _libraryState.update {
                            it.copy(
                                matchTasks = emptyList(),
                                unqueuedUnmatchedBooks = books
                            )
                        }
                    }
            } else {
                val statuses = statusesForFilter(filter)

                libraryRepository.observeMatchTasksByStatusesForSourceIds(
                    sourceIds = sourceIds,
                    statuses = statuses
                ).collectLatest { tasks ->
                    _libraryState.update {
                        it.copy(
                            matchTasks = tasks,
                            unqueuedUnmatchedBooks = emptyList()
                        )
                    }
                }
            }
        }
    }

    private fun observeMatchTaskFilterCounts() {
        taskCountObserveJob?.cancel()

        val sourceIds = currentSourceIds()

        if (_libraryState.value.librarySources.isEmpty()) {
            _libraryState.update {
                it.copy(matchTaskFilterCounts = emptyMap())
            }
            return
        }

        taskCountObserveJob = viewModelScope.launch {
            libraryRepository.observeMatchTaskFilterCountsForSourceIds(sourceIds)
                .collectLatest { counts ->
                    _libraryState.update {
                        it.copy(matchTaskFilterCounts = counts)
                    }
                }
        }
    }

    private suspend fun refreshMatchTaskFilterCountsOnce() {
        if (_libraryState.value.librarySources.isEmpty()) return
        val sourceIds = currentSourceIds()

        val counts = runCatching {
            libraryRepository.getMatchTaskFilterCountsForSourceIds(sourceIds)
        }.getOrNull() ?: return

        _libraryState.update {
            it.copy(matchTaskFilterCounts = counts)
        }
    }

    fun setTagFilterTab(tab: TagFilterTab) {
        _libraryState.update {
            it.copy(tagFilterTab = tab)
        }
    }

    fun setMatchTaskFilter(filter: MatchTaskFilter) {
        _libraryState.update {
            it.copy(taskFilter = filter)
        }

        observeMatchTasks()
    }

    private fun statusesForFilter(
        filter: MatchTaskFilter
    ): List<String> {
        return when (filter) {
            MatchTaskFilter.All -> emptyList()

            MatchTaskFilter.Running -> listOf(
                MatchTaskStatus.Pending,
                MatchTaskStatus.Running
            )

            MatchTaskFilter.Success -> listOf(
                MatchTaskStatus.AutoMatched
            )

            MatchTaskFilter.NeedReview -> listOf(
                MatchTaskStatus.NeedReview
            )

            MatchTaskFilter.Failed -> listOf(
                MatchTaskStatus.Failed
            )

            MatchTaskFilter.Skipped -> listOf(
                MatchTaskStatus.Skipped
            )

            MatchTaskFilter.Unqueued -> emptyList()
        }
    }

    private fun observeTagItems() {
        tagObserveJob?.cancel()

        val sourceIds = currentSourceIds()

        if (_libraryState.value.librarySources.isEmpty()) {
            _libraryState.update {
                it.copy(tagItems = emptyList())
            }
            return
        }

        val mergeGenderTags = !_settingsState.value.distinguishGenderTags

        tagObserveJob = viewModelScope.launch {
            libraryRepository.observeTagCountsForSourceIds(
                sourceIds = sourceIds,
                mergeGenderTags = mergeGenderTags
            ).collectLatest { tags ->
                val mode = _libraryState.value.tagSortMode
                val sortedTags = withContext(Dispatchers.Default) {
                    sortTags(
                        tags = tags,
                        mode = mode
                    )
                }

                _libraryState.update { state ->
                    state.copy(
                        tagItems = sortedTags
                    )
                }
            }
        }
    }

    private fun refreshLibraryBooks() {
        libraryObserveJob?.cancel()

        val state = _libraryState.value
        val sourceIds = currentSourceIds()

        if (state.librarySources.isEmpty()) {
            _libraryState.update {
                it.copy(books = emptyList())
            }
            return
        }

        val searchQuery = state.searchQuery.trim()
        val selectedTagKeys = state.selectedTagKeys.toList()

        libraryObserveJob = viewModelScope.launch {
            val flow = when {
                searchQuery.isNotBlank() -> {
                    libraryRepository.observeBooksBySearchForSourceIds(
                        sourceIds = sourceIds,
                        query = searchQuery
                    )
                }

                selectedTagKeys.isNotEmpty() -> {
                    libraryRepository.observeBooksByAllTagsForSourceIds(
                        sourceIds = sourceIds,
                        tagKeys = selectedTagKeys
                    )
                }

                else -> {
                    libraryRepository.observeBooksForSourceIds(sourceIds)
                }
            }

            flow.collectLatest { books ->
                _libraryState.update { state ->
                    state.copy(
                        books = sortBooks(
                            books = books,
                            mode = state.bookSortMode
                        )
                    )
                }
                repairMissingCovers(books)
            }
        }
    }

    private fun readBookSortMode(): BookSortMode {
        return runCatching {
            BookSortMode.valueOf(
                prefs.getString(KEY_BOOK_SORT_MODE, BookSortMode.NameAsc.name)
                    ?: BookSortMode.NameAsc.name
            )
        }.getOrDefault(BookSortMode.NameAsc)
    }

    private fun sortBooks(
        books: List<BookItem>,
        mode: BookSortMode
    ): List<BookItem> {
        fun displayName(book: BookItem): String {
            return book.displayName.lowercase(Locale.getDefault())
        }

        return when (mode) {
            BookSortMode.NameAsc -> {
                books.sortedWith(
                    compareBy<BookItem> { displayName(it) }
                        .thenBy { it.uriString }
                )
            }

            BookSortMode.NameDesc -> {
                books.sortedWith(
                    compareByDescending<BookItem> { displayName(it) }
                        .thenBy { it.uriString }
                )
            }

            BookSortMode.FileTimeDesc -> {
                books.sortedWith(
                    compareByDescending<BookItem> { it.lastModified }
                        .thenBy { displayName(it) }
                )
            }

            BookSortMode.FileTimeAsc -> {
                books.sortedWith(
                    compareBy<BookItem> { it.lastModified }
                        .thenBy { displayName(it) }
                )
            }

            BookSortMode.PageCountDesc -> {
                books.sortedWith(
                    compareBy<BookItem> { it.pageCount == null }
                        .thenByDescending { it.pageCount ?: 0 }
                        .thenBy { displayName(it) }
                )
            }

            BookSortMode.PageCountAsc -> {
                books.sortedWith(
                    compareBy<BookItem> { it.pageCount == null }
                        .thenBy { it.pageCount ?: Int.MAX_VALUE }
                        .thenBy { displayName(it) }
                )
            }
        }
    }

    fun setBookSortMode(mode: BookSortMode) {
        prefs.edit()
            .putString(KEY_BOOK_SORT_MODE, mode.name)
            .apply()

        _libraryState.update { state ->
            state.copy(
                bookSortMode = mode,
                books = sortBooks(
                    books = state.books,
                    mode = mode
                )
            )
        }
    }

    private fun repairMissingCovers(
        books: List<BookItem>
    ) {
        books
            .filter { book ->
                val path = book.coverFilePath
                (path.isNullOrBlank() || !File(path).exists()) &&
                        book.uriString !in coverRepairAttemptedBookUris
            }
            .take(12)
            .forEach { book ->
                if (!coverRepairingBookUris.add(book.uriString)) {
                    return@forEach
                }
                coverRepairAttemptedBookUris.add(book.uriString)

                viewModelScope.launch {
                    try {
                        libraryRepository.repairMissingCover(book)
                    } finally {
                        coverRepairingBookUris.remove(book.uriString)
                    }
                }
            }
    }

    fun setHomeTab(tab: HomeTab) {
        _libraryState.update { state ->
            if (state.homeTab == tab) {
                state.copy(homeTabReselectTick = state.homeTabReselectTick + 1L)
            } else {
                state.copy(homeTab = tab)
            }
        }
    }

    private fun currentLibraryRoot(): String? {
        val state = _libraryState.value
        val selectedScope = state.sourceScopes.firstOrNull {
            it.key == state.selectedSourceScopeKey
        }
        return selectedScope?.sourceIds?.singleOrNull()
            ?: state.librarySources.firstOrNull()?.id
            ?: state.folderUriString
    }

    fun selectSourceScope(key: String) {
        val state = _libraryState.value
        val scope = state.sourceScopes.firstOrNull { it.key == key }
            ?: state.sourceScopes.firstOrNull()
            ?: return
        prefs.edit()
            .putString(KEY_SELECTED_SOURCE_SCOPE, scope.key)
            .apply()
        val concreteSourceId = scope.sourceIds.singleOrNull()
        _libraryState.update {
            it.copy(
                selectedSourceScopeKey = scope.key,
                folderUriString = concreteSourceId ?: scope.key,
                currentDirectorySourceId = concreteSourceId,
                currentDirectoryPath = scope.folderPath.orEmpty(),
                selectedTagKeys = emptySet(),
                searchQuery = "",
                books = emptyList()
            )
        }
        shouldScrollDirectoryToTop = true
        refreshLibraryBooks()
        observeTagItems()
        observeMatchTasks()
        observeMatchTaskFilterCounts()
        observeDirectory()
    }

    fun openDirectory(folder: LibraryFolderNode) {
        _libraryState.update {
            it.copy(
                currentDirectorySourceId = folder.sourceId,
                currentDirectoryPath = folder.path
            )
        }
        shouldScrollDirectoryToTop = true
        observeDirectory()
    }

    fun navigateDirectoryUp() {
        val state = _libraryState.value
        val path = state.currentDirectoryPath
        if (path.isBlank()) {
            val selectedScope = state.sourceScopes.firstOrNull { it.key == state.selectedSourceScopeKey }
            if (selectedScope?.sourceIds?.size == 1) return
            _libraryState.update {
                it.copy(
                    currentDirectorySourceId = null,
                    currentDirectoryPath = ""
                )
            }
        } else {
            val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
            _libraryState.update {
                it.copy(currentDirectoryPath = parent)
            }
        }
        observeDirectory()
    }

    private fun observeDirectory() {
        directoryFolderObserveJob?.cancel()
        directoryBookObserveJob?.cancel()

        val state = _libraryState.value
        val concreteSourceId = state.currentDirectorySourceId
            ?: state.sourceScopes.firstOrNull { it.key == state.selectedSourceScopeKey }
                ?.sourceIds
                ?.singleOrNull()

        if (concreteSourceId == null) {
            val sourceIds = currentSourceIds()
            directoryFolderObserveJob = viewModelScope.launch {
                libraryRepository.observeFoldersForSourceIds(sourceIds)
                    .collectLatest { folders ->
                        val roots = folders.filter { it.parentPath.isNullOrBlank() }
                        val requestScroll = shouldScrollDirectoryToTop
                        if (requestScroll) {
                            shouldScrollDirectoryToTop = false
                        }
                        _libraryState.update {
                            it.copy(
                                directoryFolders = roots,
                                directoryBooks = emptyList(),
                                directoryContentVersion = it.directoryContentVersion + 1L,
                                directoryScrollToken = if (requestScroll) {
                                    it.directoryScrollToken + 1L
                                } else {
                                    it.directoryScrollToken
                                },
                                currentDirectorySourceId = null,
                                currentDirectoryPath = ""
                            )
                        }
                    }
            }
            return
        }

        val parentPath = state.currentDirectoryPath

        directoryFolderObserveJob = viewModelScope.launch {
            combine(
                libraryRepository.observeChildFolders(concreteSourceId, parentPath),
                libraryRepository.observeBooksInFolder(concreteSourceId, parentPath)
            ) { folders, books ->
                folders to books
            }.collectLatest { (folders, books) ->
                    val requestScroll = shouldScrollDirectoryToTop
                    if (requestScroll) {
                        shouldScrollDirectoryToTop = false
                    }
                    _libraryState.update { state ->
                        state.copy(
                            directoryFolders = folders,
                            directoryBooks = sortBooks(
                                books = books,
                                mode = state.bookSortMode
                            ),
                            directoryContentVersion = state.directoryContentVersion + 1L,
                            directoryScrollToken = if (requestScroll) {
                                state.directoryScrollToken + 1L
                            } else {
                                state.directoryScrollToken
                            }
                        )
                    }
                    repairMissingCovers(books)
            }
        }
    }

    private fun currentSourceIds(): List<String> {
        val state = _libraryState.value
        val scope = state.sourceScopes.firstOrNull {
            it.key == state.selectedSourceScopeKey
        }
        return when {
            state.librarySources.isEmpty() -> emptyList()
            scope == null -> emptyList()
            scope.key == ALL_SOURCES_KEY -> emptyList()
            else -> scope.sourceIds
        }
    }

    private fun buildSourceScopes(
        sources: List<LibrarySource>
    ): List<LibrarySourceScope> {
        if (sources.isEmpty()) return emptyList()
        val scopes = mutableListOf<LibrarySourceScope>()
        scopes += LibrarySourceScope(
            key = ALL_SOURCES_KEY,
            label = "全部",
            sourceIds = emptyList()
        )
        scopes += LibrarySourceScope(
            key = LOCAL_SOURCES_KEY,
            label = "本地",
            sourceIds = sources.map { it.id }
        )
        sources.forEach { source ->
            scopes += LibrarySourceScope(
                key = sourceScopeKey(source.id),
                label = source.name,
                sourceIds = listOf(source.id),
                folderSourceId = source.id,
                folderPath = ""
            )
        }
        return scopes
    }

    private fun sourceScopeKey(sourceId: String): String {
        return "source:$sourceId"
    }

    fun toggleTagFilter(tag: TagCountItem) {
        _libraryState.update { state ->
            val newKeys = if (tag.tagKey in state.selectedTagKeys) {
                state.selectedTagKeys - tag.tagKey
            } else {
                state.selectedTagKeys + tag.tagKey
            }

            state.copy(
                selectedTagKeys = newKeys,
                // 选择 tag 时清空搜索，避免两个入口互相覆盖
                searchQuery = ""
            )
        }

        refreshLibraryBooks()
    }

    fun startBatchMatchUnmatched() {
        if (_libraryState.value.isBatchMatching) return

        viewModelScope.launch {
            _libraryState.update {
                it.copy(
                    isBatchMatching = true,
                    error = null
                )
            }

            val sourceIds = currentSourceIds()

            if (_libraryState.value.librarySources.isEmpty()) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "请先选择书库目录"
                    )
                }
                return@launch
            }

            val unmatchedBooks: List<BookItem> = try {
                libraryRepository.getUnmatchedBooksForSourceIds(sourceIds)
            } catch (e: Exception) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = e.message ?: "读取未匹配作品失败"
                    )
                }
                return@launch
            }

            if (unmatchedBooks.isEmpty()) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "没有未匹配作品"
                    )
                }

                setHomeTab(HomeTab.Tasks)
                setMatchTaskFilter(MatchTaskFilter.All)
                return@launch
            }

            val batchThreads = batchMatchThreads()

            for (chunk in unmatchedBooks.chunked(batchThreads)) {
                chunk.map { book ->
                    async {
                        runCatching {
                            processOneBatchMatchBook(
                                book = book,
                                libraryRootUriString = book.libraryRootUriString ?: book.sourceId
                            )
                        }.onFailure {
                            // 单个任务失败不终止整个批量匹配
                        }
                    }
                }.awaitAll()
            }

            _libraryState.update {
                it.copy(isBatchMatching = false)
            }

            refreshMatchTaskFilterCountsOnce()

            setHomeTab(HomeTab.Tasks)
            setMatchTaskFilter(MatchTaskFilter.All)
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            try {
                libraryRepository.clearDatabase()

                _libraryState.update {
                    it.copy(
                        books = emptyList(),
                        librarySources = emptyList(),
                        sourceScopes = emptyList(),
                        directoryFolders = emptyList(),
                        directoryBooks = emptyList(),
                        tagItems = emptyList(),
                        matchTasks = emptyList(),
                        unqueuedUnmatchedBooks = emptyList(),
                        matchTaskFilterCounts = emptyMap(),
                        selectedTagKeys = emptySet(),
                        searchQuery = "",
                        error = "数据库已清空"
                    )
                }

                _bookDetailState.value = BookDetailUiState()
                _matchState.value = MatchUiState()
                _matchTaskDetailState.value = MatchTaskDetailUiState()
                _readerState.value = ReaderUiState()

                refreshLibraryBooks()
                observeTagItems()
                observeMatchTasks()
                observeMatchTaskFilterCounts()
            } catch (e: Exception) {
                _libraryState.update {
                    it.copy(error = e.message ?: "清空数据库失败")
                }
            }
        }
    }

    private fun buildMatchQueryFromName(
        name: String
    ): String {
        val settings = _settingsState.value

        return cleanFileName(
            name = name,
            removeUnderscore = settings.removeUnderscoreInMatchTitle,
            removeTrailingNumberSuffix = settings.removeTrailingNumberSuffixInMatchTitle
        )
    }

    private suspend fun processOneBatchMatchBook(
        book: BookItem,
        libraryRootUriString: String,
    ) {
        val query = buildMatchQueryFromName(book.displayName)

        val localPageCount = runCatching {
            ComicArchiveReader.listPages(
                context = app,
                archiveUri = Uri.parse(book.uriString)
            ).size
        }.getOrNull()

        val taskId = libraryRepository.createMatchTask(
            book = book,
            libraryRootUriString = libraryRootUriString,
            query = query,
            localPageCount = localPageCount
        )

        val created = libraryRepository.getMatchTask(taskId) ?: return

        val running = created.copy(
            status = MatchTaskStatus.Running,
            updatedAt = System.currentTimeMillis()
        )
        libraryRepository.updateMatchTask(running)

        executeMatchTaskSearch(
            task = running,
            query = query,
            localPageCount = localPageCount,
            defaultSearchFailMessage = "搜索失败"
        )
    }

    /**
     * 批量匹配与重试任务共用的核心流程：基于已置为 Running 的任务，
     * 执行搜索 → 过滤候选 → 自动匹配判定 → 落库更新任务状态。
     */
    private suspend fun executeMatchTaskSearch(
        task: MatchTaskEntity,
        query: String,
        localPageCount: Int?,
        defaultSearchFailMessage: String
    ) {
        if (localPageCount == null || localPageCount <= 0) {
            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Skipped,
                    errorMessage = "无法读取本地页数",
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        val searchResult = runCatching {
            hitomiRepository.searchTitle(
                title = query,
                timeoutMillis = matchSearchTimeoutMillis()
            )
        }.getOrElse { e ->
            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Failed,
                    errorMessage = compactDiagnostic(e.message ?: defaultSearchFailMessage),
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        val rawCandidates = searchResult.books
        val candidates = prepareHitomiCandidates(
            candidates = rawCandidates,
            localPageCount = localPageCount
        )

        if (rawCandidates.isEmpty()) {
            libraryRepository.replaceCandidatesForTask(
                taskId = task.id,
                candidates = emptyList(),
                selectedGalleryId = null
            )

            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Failed,
                    errorMessage = compactSearchFailure(searchResult),
                    candidateCount = 0,
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        if (candidates.isEmpty()) {
            libraryRepository.replaceCandidatesForTask(
                taskId = task.id,
                candidates = emptyList(),
                selectedGalleryId = null
            )

            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Failed,
                    errorMessage = compactDiagnostic(
                        "候选均被语言过滤；${searchResult.diagnosticSummary}"
                    ),
                    candidateCount = 0,
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        val autoSelected = chooseAutoMatchCandidate(
            candidates = candidates,
            localPageCount = localPageCount,
            query = query
        )

        libraryRepository.replaceCandidatesForTask(
            taskId = task.id,
            candidates = candidates,
            selectedGalleryId = autoSelected?.id
        )

        if (autoSelected != null) {
            val saved = runCatching {
                libraryRepository.bindHitomiMeta(
                    uriString = task.bookUriString,
                    meta = autoSelected
                )
            }.isSuccess

            if (saved) {
                libraryRepository.updateMatchTask(
                    task.copy(
                        status = MatchTaskStatus.AutoMatched,
                        matchedGalleryId = autoSelected.id,
                        candidateCount = candidates.size,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                libraryRepository.updateMatchTask(
                    task.copy(
                        status = MatchTaskStatus.Failed,
                        candidateCount = candidates.size,
                        errorMessage = "保存匹配结果失败",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.NeedReview,
                    matchedGalleryId = null,
                    candidateCount = candidates.size,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun chooseAutoMatchCandidate(
        candidates: List<HitomiBookMeta>,
        localPageCount: Int,
        query: String
    ): HitomiBookMeta? {
        val settings = _settingsState.value

        if (candidates.isEmpty()) {
            return null
        }

        // 1. 名称完全相同：候选标题或日文标题与搜索词完全一致，且唯一
        if (settings.autoMatchExactTitle) {
            val exactMatches = candidates.filter { meta ->
                isExactTitleMatch(
                    query = query,
                    meta = meta
                )
            }

            if (exactMatches.size == 1) {
                return exactMatches.first()
            }
        }

        val singleEnabled = settings.autoMatchSingleResult
        val samePageFirstEnabled = settings.autoMatchSamePageFirst
        val uniqueSamePageEnabled = settings.autoMatchUniqueSamePage

        // 2. 保留你之前要求的逻辑：
        // “搜索结果仅一个”和“页数相同的第一个”都开启时，必须仅一个候选且页数相同
        if (singleEnabled && samePageFirstEnabled) {
            val only = candidates.singleOrNull()

            if (only != null && only.pageCount == localPageCount) {
                return only
            }

            // 注意：这里不直接 return null。
            // 因为还允许后面的“唯一页数相同”规则独立生效。
        }

        // 3. 唯一页数相同：候选中只有一个页数等于本地页数
        if (uniqueSamePageEnabled) {
            val samePageMatches = candidates.filter {
                it.pageCount == localPageCount
            }

            if (samePageMatches.size == 1) {
                return samePageMatches.first()
            }
        }

        // 4. 只开启页数相同的第一个
        if (samePageFirstEnabled && !singleEnabled) {
            val firstSamePage = candidates.firstOrNull {
                it.pageCount == localPageCount
            }

            if (firstSamePage != null) {
                return firstSamePage
            }
        }

        // 5. 只开启搜索结果仅一个
        if (singleEnabled && !samePageFirstEnabled) {
            return candidates.singleOrNull()
        }

        return null
    }

    private fun isExactTitleMatch(
        query: String,
        meta: HitomiBookMeta
    ): Boolean {
        val normalizedQuery = normalizeTitleForExactMatch(query)

        if (normalizedQuery.isBlank()) {
            return false
        }

        val title = normalizeTitleForExactMatch(meta.title)
        val japaneseTitle = normalizeTitleForExactMatch(meta.japaneseTitle.orEmpty())

        return normalizedQuery == title || normalizedQuery == japaneseTitle
    }

    private fun normalizeTitleForExactMatch(
        text: String
    ): String {
        return text
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun prepareHitomiCandidates(
        candidates: List<HitomiBookMeta>,
        localPageCount: Int?
    ): List<HitomiBookMeta> {
        val filteredLanguages = _settingsState.value.filteredMatchLanguages

        return candidates
            .filter { meta ->
                !isLanguageFiltered(meta.language, filteredLanguages)
            }
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<HitomiBookMeta>> {
                    localPageCount != null &&
                            localPageCount > 0 &&
                            it.value.pageCount == localPageCount
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    private fun prepareStoredCandidates(
        candidates: List<MatchCandidateEntity>,
        localPageCount: Int?
    ): List<MatchCandidateEntity> {
        val filteredLanguages = _settingsState.value.filteredMatchLanguages

        return candidates
            .filter { candidate ->
                !isLanguageFiltered(candidate.language, filteredLanguages)
            }
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<MatchCandidateEntity>> {
                    localPageCount != null &&
                            localPageCount > 0 &&
                            it.value.pageCount == localPageCount
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    private fun isLanguageFiltered(
        language: String?,
        filteredLanguages: Set<String>
    ): Boolean {
        if (filteredLanguages.isEmpty()) return false
        val normalized = language?.trim()?.lowercase().orEmpty()
        return normalized.isNotEmpty() && normalized in filteredLanguages
    }

    private fun parseFilteredMatchLanguages(raw: String): Set<String> {
        return raw
            .split(",", "\n", "，")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun matchSearchTimeoutMillis(): Long {
        return _settingsState.value.matchSearchTimeoutSeconds
            .coerceIn(
                MIN_MATCH_SEARCH_TIMEOUT_SECONDS,
                MAX_MATCH_SEARCH_TIMEOUT_SECONDS
            ) * 1000L
    }

    private fun batchMatchThreads(): Int {
        return _settingsState.value.batchMatchThreads.coerceIn(
            MIN_BATCH_MATCH_THREADS,
            MAX_BATCH_MATCH_THREADS
        )
    }

    private fun compactSearchFailure(result: HitomiSearchMetaResult): String {
        return compactDiagnostic(
            result.failureReason
                ?: result.diagnosticSummary
                ?: "没有搜索到候选"
        )
    }

    private fun compactDiagnostic(message: String, maxLength: Int = 240): String {
        val normalized = message
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength - 1) + "…"
        }
    }

    private fun isNoCandidateSearchError(message: String?): Boolean {
        val text = message?.trim().orEmpty()
        return text == "没有搜索到候选" ||
                text.contains("没有提取到候选 ID") ||
                text.contains("没有搜索到候选")
    }

    fun clearTagFilters() {
        _libraryState.update {
            it.copy(
                selectedTagKeys = emptySet()
            )
        }

        refreshLibraryBooks()
    }

    private fun preloadAround(index: Int) {
        val offsets = listOf(0, 1, 2, 3, 4, -1, -2)
        offsets.forEach { offset ->
            ensurePageLoaded(index + offset)
        }
    }

    fun applyTagFilter(tag: TagEntity) {
        _libraryState.update { state ->
            state.copy(
                selectedTagKeys = setOf(tag.key),
                searchQuery = "",
                homeTab = HomeTab.Library
            )
        }

        refreshLibraryBooks()
    }

    fun clearTagFilter() {
        clearTagFilters()
    }

    fun setTagSortMode(mode: TagSortMode) {
        _libraryState.update { state ->
            state.copy(
                tagSortMode = mode
            )
        }

        viewModelScope.launch {
            val tags = _libraryState.value.tagItems
            val sortedTags = withContext(Dispatchers.Default) {
                sortTags(
                    tags = tags,
                    mode = mode
                )
            }

            _libraryState.update { state ->
                if (state.tagSortMode == mode) {
                    state.copy(tagItems = sortedTags)
                } else {
                    state
                }
            }
        }
    }

    private fun sortTags(
        tags: List<TagCountItem>,
        mode: TagSortMode
    ): List<TagCountItem> {
        return when (mode) {
            TagSortMode.CountDesc -> {
                tags.sortedWith(
                    compareByDescending<TagCountItem> { it.bookCount }
                        .thenBy { it.namespace }
                        .thenBy { it.name.lowercase() }
                )
            }

            TagSortMode.NameAsc -> {
                tags.sortedWith(
                    compareBy<TagCountItem> { it.namespace }
                        .thenBy { it.name.lowercase() }
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _libraryState.update {
            it.copy(
                searchQuery = query,
                selectedTagKeys = emptySet()
            )
        }

        refreshLibraryBooks()
    }

    fun openMatchTaskDetail(task: MatchTaskEntity) {
        matchTaskDetailJob?.cancel()
        matchTaskCandidateJob?.cancel()

        _matchTaskDetailState.value = MatchTaskDetailUiState(
            task = task,
            candidates = emptyList()
        )

        matchTaskDetailJob = viewModelScope.launch {
            libraryRepository.observeMatchTask(task.id).collectLatest { updatedTask ->
                _matchTaskDetailState.update {
                    it.copy(task = updatedTask)
                }
            }
        }

        matchTaskCandidateJob = viewModelScope.launch {
            libraryRepository.observeCandidatesForTask(task.id).collectLatest { candidates ->
                _matchTaskDetailState.update {
                    it.copy(
                        candidates = prepareStoredCandidates(
                            candidates = candidates,
                            localPageCount = it.task?.localPageCount ?: task.localPageCount
                        )
                    )
                }
            }
        }
    }

    /**
     * 在当前任务列表中切换到上一条 / 下一条匹配任务。
     * forward = true 表示下一条（列表中后一项），false 表示上一条。
     * 返回是否成功切换（用于界面边界提示）。
     */
    fun openAdjacentMatchTask(forward: Boolean): Boolean {
        val current = _matchTaskDetailState.value.task ?: return false
        if (_matchTaskDetailState.value.isBinding ||
            _matchTaskDetailState.value.isRefreshing
        ) {
            return false
        }

        val list = _libraryState.value.matchTasks
        val index = list.indexOfFirst { it.id == current.id }
        if (index < 0) return false

        val target = list.getOrNull(if (forward) index + 1 else index - 1)
            ?: return false

        openMatchTaskDetail(target)
        return true
    }

    fun bindMatchTaskCandidate(
        candidate: MatchCandidateEntity,
        onAutoAdvance: (MatchTaskEntity?) -> Unit = {}
    ) {
        val task = _matchTaskDetailState.value.task ?: return

        if (_matchTaskDetailState.value.isBinding) return

        viewModelScope.launch {
            _matchTaskDetailState.update {
                it.copy(
                    isBinding = true,
                    error = null
                )
            }

            val fullMeta = runCatching {
                hitomiRepository.fetchMetaById(candidate.galleryId)
            }.getOrNull()

            if (fullMeta == null) {
                _matchTaskDetailState.update {
                    it.copy(
                        isBinding = false,
                        error = "获取候选元数据失败"
                    )
                }
                return@launch
            }

            val saved = runCatching {
                libraryRepository.bindHitomiMeta(
                    uriString = task.bookUriString,
                    meta = fullMeta
                )
            }.isSuccess

            if (!saved) {
                _matchTaskDetailState.update {
                    it.copy(
                        isBinding = false,
                        error = "保存匹配结果失败"
                    )
                }
                return@launch
            }

            libraryRepository.markSelectedCandidate(
                taskId = task.id,
                galleryId = candidate.galleryId
            )

            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.AutoMatched,
                    matchedGalleryId = candidate.galleryId,
                    candidateCount = _matchTaskDetailState.value.candidates.size,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )

            val nextTask = if (_settingsState.value.autoOpenNextReviewTask) {
                libraryRepository.getNextNeedReviewTask(
                    currentTaskId = task.id,
                    currentUpdatedAt = task.updatedAt,
                    libraryRootUriString = task.libraryRootUriString
                )
            } else {
                null
            }

            _matchTaskDetailState.update {
                it.copy(
                    isBinding = false,
                    error = null
                )
            }

            onAutoAdvance(nextTask)
        }
    }

    fun markCurrentMatchTaskSkipped() {
        val task = _matchTaskDetailState.value.task ?: return

        viewModelScope.launch {
            skipMatchTask(task)
        }
    }

    fun skipMatchTaskFromList(task: MatchTaskEntity) {
        viewModelScope.launch {
            skipMatchTask(task)
            refreshMatchTaskFilterCountsOnce()
        }
    }

    fun skipUnqueuedBook(book: BookItem) {
        val root = book.libraryRootUriString ?: book.sourceId
        if (root.isBlank()) return

        viewModelScope.launch {
            val taskId = libraryRepository.createMatchTask(
                book = book,
                libraryRootUriString = root,
                query = buildMatchQueryFromName(book.displayName),
                localPageCount = null
            )
            val task = libraryRepository.getMatchTask(taskId) ?: return@launch

            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Skipped,
                    errorMessage = SKIPPED_FROM_UNQUEUED_MESSAGE,
                    updatedAt = System.currentTimeMillis()
                )
            )

            refreshMatchTaskFilterCountsOnce()
        }
    }

    fun cancelSkippedMatchTask(task: MatchTaskEntity) {
        if (task.status != MatchTaskStatus.Skipped) return

        viewModelScope.launch {
            if (task.errorMessage == SKIPPED_FROM_UNQUEUED_MESSAGE) {
                libraryRepository.deleteMatchTask(task.id)
            } else {
                libraryRepository.updateMatchTask(
                    task.copy(
                        status = MatchTaskStatus.Failed,
                        errorMessage = "已取消跳过",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            refreshMatchTaskFilterCountsOnce()
        }
    }

    private suspend fun skipMatchTask(task: MatchTaskEntity) {
        libraryRepository.updateMatchTask(
            task.copy(
                status = MatchTaskStatus.Skipped,
                errorMessage = "用户手动标记跳过",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun retryFailedMatchTasksExceptNoCandidates() {
        if (_libraryState.value.isBatchMatching) return

        viewModelScope.launch {
            _libraryState.update {
                it.copy(
                    isBatchMatching = true,
                    error = null
                )
            }

            val sourceIds = currentSourceIds()

            if (_libraryState.value.librarySources.isEmpty()) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "请先选择书库目录"
                    )
                }
                return@launch
            }

            val failedTasks: List<MatchTaskEntity> = try {
                libraryRepository.getMatchTasksByStatusesForSourceIds(
                    sourceIds = sourceIds,
                    statuses = listOf(MatchTaskStatus.Failed)
                )
            } catch (e: Exception) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = e.message ?: "读取失败任务失败"
                    )
                }
                return@launch
            }

            val retryableTasks = failedTasks.filterNot { task ->
                isNoCandidateSearchError(task.errorMessage)
            }

            if (retryableTasks.isEmpty()) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "没有可重试的失败任务"
                    )
                }

                setHomeTab(HomeTab.Tasks)
                setMatchTaskFilter(MatchTaskFilter.Failed)
                return@launch
            }

            val batchThreads = batchMatchThreads()

            for (chunk in retryableTasks.chunked(batchThreads)) {
                chunk.map { task ->
                    async {
                        runCatching {
                            retryOneMatchTask(task)
                        }.onFailure {
                            // 单个任务失败不影响其他任务
                        }
                    }
                }.awaitAll()
            }

            _libraryState.update {
                it.copy(isBatchMatching = false)
            }

            refreshMatchTaskFilterCountsOnce()

            setHomeTab(HomeTab.Tasks)
            setMatchTaskFilter(MatchTaskFilter.All)
        }
    }

    fun retryFailedMatchTask(task: MatchTaskEntity) {
        if (task.status != MatchTaskStatus.Failed) return

        viewModelScope.launch {
            runCatching {
                retryOneMatchTask(task)
            }.onFailure { e ->
                _libraryState.update {
                    it.copy(error = e.message ?: "重试失败")
                }
            }

            refreshMatchTaskFilterCountsOnce()
        }
    }

    fun clearSearchQuery() {
        _libraryState.update {
            it.copy(searchQuery = "")
        }

        refreshLibraryBooks()
    }

    fun onFolderPicked(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }

        viewModelScope.launch {
            runCatching {
                val source = libraryRepository.addOrUpdateLocalSource(uri)
                prefs.edit()
                    .putString(KEY_FOLDER_URI, source.rootUriString)
                    .putString(KEY_SELECTED_SOURCE_SCOPE, sourceScopeKey(source.id))
                    .apply()
                _settingsState.update {
                    it.copy(folderUriString = source.rootUriString)
                }
                _libraryState.update {
                    it.copy(
                        folderUriString = source.id,
                        selectedSourceScopeKey = sourceScopeKey(source.id),
                        currentDirectorySourceId = source.id,
                        currentDirectoryPath = "",
                        selectedTagKeys = emptySet(),
                        searchQuery = "",
                        error = null
                    )
                }
                scanSource(source.id)
            }.onFailure { e ->
                _libraryState.update {
                    it.copy(error = e.message ?: "添加目录失败")
                }
            }
        }
    }

    fun openBookDetail(book: BookItem) {
        _bookDetailState.value = BookDetailUiState(
            book = book,
            tags = emptyList()
        )

        viewModelScope.launch {
            libraryRepository.observeTagsForBook(book.uriString).collect { tags ->
                _bookDetailState.update {
                    it.copy(tags = tags)
                }
            }
        }
    }

    fun setRemoveUnderscoreInMatchTitle(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_REMOVE_UNDERSCORE_IN_MATCH_TITLE, enabled)
            .apply()

        _settingsState.update {
            it.copy(removeUnderscoreInMatchTitle = enabled)
        }
    }

    fun setRemoveTrailingNumberSuffixInMatchTitle(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_REMOVE_TRAILING_NUMBER_SUFFIX_IN_MATCH_TITLE, enabled)
            .apply()

        _settingsState.update {
            it.copy(removeTrailingNumberSuffixInMatchTitle = enabled)
        }
    }

    fun rescan() {
        scanSources(currentSourceIds())
    }

    fun renameSource(sourceId: String, name: String) {
        viewModelScope.launch {
            runCatching {
                libraryRepository.renameSource(sourceId, name)
            }.onFailure { e ->
                _libraryState.update {
                    it.copy(error = e.message ?: "重命名目录失败")
                }
            }
        }
    }

    fun deleteSource(sourceId: String) {
        viewModelScope.launch {
            runCatching {
                libraryRepository.deleteSource(sourceId)
                if (_libraryState.value.currentDirectorySourceId == sourceId) {
                    _libraryState.update {
                        it.copy(
                            currentDirectorySourceId = null,
                            currentDirectoryPath = ""
                        )
                    }
                }
            }.onFailure { e ->
                _libraryState.update {
                    it.copy(error = e.message ?: "删除目录来源失败")
                }
            }
        }
    }

    fun cleanupMissingFromConfiguredSources() {
        viewModelScope.launch {
            runCatching {
                libraryRepository.cleanupMissingFromConfiguredSources(currentSourceIds())
            }.onSuccess { count ->
                _libraryState.update {
                    it.copy(error = "已清理 $count 条数据库记录")
                }
                refreshLibraryBooks()
                observeTagItems()
                observeMatchTasks()
                observeMatchTaskFilterCounts()
                observeDirectory()
            }.onFailure { e ->
                _libraryState.update {
                    it.copy(error = e.message ?: "清理数据库记录失败")
                }
            }
        }
    }

    fun openBook(book: BookItem) {
        _readerState.value = ReaderUiState(
            book = book,
            isOpening = true
        )

        viewModelScope.launch {
            try {
                val archiveUri = Uri.parse(book.uriString)
                val pages = ComicArchiveReader.listPages(app, archiveUri)

                if (pages.isEmpty()) {
                    _readerState.value = ReaderUiState(
                        book = book,
                        pages = emptyList(),
                        isOpening = false,
                        error = "压缩包内没有找到可阅读图片"
                    )
                    return@launch
                }

                val initialFiles = mutableMapOf<Int, File>()
                val initialInfos = mutableMapOf<Int, PageInfo>()

                // 关键：打开本子时先加载前 3 页，减少第一次右滑黑屏
                for (index in 0..2) {
                    if (index !in pages.indices) continue

                    val extracted = ComicArchiveReader.extractPageWithInfo(
                        context = app,
                        archiveUri = archiveUri,
                        entryName = pages[index],
                        pageIndex = index
                    )

                    if (extracted != null) {
                        initialFiles[index] = extracted.file
                        initialInfos[index] = extracted.info
                    }
                }

                _readerState.value = ReaderUiState(
                    book = book,
                    pages = pages,
                    pageIndex = 0,
                    pageFiles = initialFiles,
                    pageInfos = initialInfos,
                    isOpening = false
                )

                preloadAround(0)
            } catch (e: Exception) {
                _readerState.value = ReaderUiState(
                    book = book,
                    isOpening = false,
                    error = e.message ?: "打开失败"
                )
            }
        }
    }

    fun startMatchFromTask(task: MatchTaskEntity) {
        val query = buildMatchQueryFromName(task.displayName)

        _matchState.value = MatchUiState(
            book = BookItem(
                displayName = task.displayName,
                uriString = task.bookUriString,
                coverFilePath = task.coverFilePath
            ),
            sourceTaskId = task.id,
            query = query,
            localPageCount = task.localPageCount,
            candidates = emptyList(),
            isSearching = false,
            error = null
        )
    }

    fun startMatch(book: BookItem) {
        val query = buildMatchQueryFromName(book.displayName)

        _matchState.value = MatchUiState(
            book = book,
            query = query,
            localPageCount = null
        )

        viewModelScope.launch {
            val pageCount = runCatching {
                ComicArchiveReader.listPages(
                    context = app,
                    archiveUri = Uri.parse(book.uriString)
                ).size
            }.getOrNull()

            _matchState.update { state ->
                if (state.book?.uriString == book.uriString) {
                    state.copy(localPageCount = pageCount)
                } else {
                    state
                }
            }
        }
    }

    fun updateMatchQuery(query: String) {
        _matchState.update {
            it.copy(query = query)
        }
    }

    fun openHitomiSearchWebView(): Boolean {
        val query = _matchState.value.query.trim()

        if (query.isBlank()) {
            _matchState.update {
                it.copy(error = "搜索词不能为空")
            }
            return false
        }

        val encoded = Uri.encode(query.lowercase(Locale.ROOT))

        _hitomiWebViewState.value = HitomiWebViewUiState(
            title = "搜索结果",
            url = "https://hitomi.la/search.html?$encoded"
        )

        return true
    }

    fun searchMatch() {
        val state = _matchState.value
        val query = state.query.trim()

        if (query.isBlank()) {
            _matchState.update {
                it.copy(
                    error = "搜索词不能为空",
                    searchDiagnosticSummary = null,
                    searchDiagnosticRaw = null,
                    showSearchDiagnosticRaw = false
                )
            }
            return
        }

        _matchState.update {
            it.copy(
                isSearching = true,
                error = null,
                candidates = emptyList(),
                searchDiagnosticSummary = null,
                searchDiagnosticRaw = null,
                showSearchDiagnosticRaw = false
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                hitomiRepository.searchTitle(
                    title = query,
                    timeoutMillis = matchSearchTimeoutMillis()
                )
            }

            result.onSuccess { searchResult ->
                val rawCandidates = searchResult.books
                val candidates = prepareHitomiCandidates(
                    candidates = rawCandidates,
                    localPageCount = _matchState.value.localPageCount
                )
                val hasDiagnostic = rawCandidates.isEmpty() || candidates.isEmpty()

                _matchState.update {
                    it.copy(
                        candidates = candidates,
                        isSearching = false,
                        error = when {
                            rawCandidates.isEmpty() -> {
                                searchResult.failureReason
                                    ?: "没有搜索到候选，可能是 WebView 搜索失败或标题需要缩短"
                            }

                            candidates.isEmpty() -> {
                                "候选均被语言过滤"
                            }

                            else -> {
                                null
                            }
                        },
                        searchDiagnosticSummary = if (hasDiagnostic) {
                            searchResult.diagnosticSummary
                        } else {
                            null
                        },
                        searchDiagnosticRaw = if (hasDiagnostic) {
                            searchResult.diagnosticRaw
                        } else {
                            null
                        },
                        showSearchDiagnosticRaw = false
                    )
                }
            }.onFailure { e ->
                _matchState.update {
                    it.copy(
                        isSearching = false,
                        error = e.message ?: "搜索失败",
                        searchDiagnosticSummary = e.message ?: "搜索失败",
                        searchDiagnosticRaw = e.stackTraceToString(),
                        showSearchDiagnosticRaw = false
                    )
                }
            }
        }
    }

    fun bindMatch(meta: HitomiBookMeta) {
        val state = _matchState.value
        val book = state.book ?: return

        viewModelScope.launch {
            try {
                libraryRepository.bindHitomiMeta(
                    uriString = book.uriString,
                    meta = meta
                )

                updateRelatedMatchTasksAfterBind(
                    book = book,
                    meta = meta,
                    sourceTaskId = state.sourceTaskId,
                    candidates = state.candidates
                )

                _matchState.update {
                    it.copy(error = null)
                }
            } catch (e: Exception) {
                _matchState.update {
                    it.copy(error = e.message ?: "保存失败")
                }
            }
        }
    }

    private suspend fun updateRelatedMatchTasksAfterBind(
        book: BookItem,
        meta: HitomiBookMeta,
        sourceTaskId: Long?,
        candidates: List<HitomiBookMeta>
    ) {
        val candidatesForTask = candidates.ifEmpty {
            listOf(meta)
        }

        val targetTasks = if (sourceTaskId != null) {
            listOfNotNull(
                libraryRepository.getMatchTask(sourceTaskId)
            )
        } else {
            libraryRepository.getMatchTasksByBookUri(book.uriString)
                .filter { task ->
                    task.status == MatchTaskStatus.Failed ||
                            task.status == MatchTaskStatus.NeedReview ||
                            task.status == MatchTaskStatus.Skipped ||
                            task.status == MatchTaskStatus.Running ||
                            task.status == MatchTaskStatus.Pending
                }
        }

        for (task in targetTasks) {
            libraryRepository.replaceCandidatesForTask(
                taskId = task.id,
                candidates = candidatesForTask,
                selectedGalleryId = meta.id
            )

            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.AutoMatched,
                    matchedGalleryId = meta.id,
                    candidateCount = candidatesForTask.size,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun retryFailedMatchTasks() {
        if (_libraryState.value.isBatchMatching) return

        viewModelScope.launch {
            _libraryState.update {
                it.copy(
                    isBatchMatching = true,
                    error = null
                )
            }

            val sourceIds = currentSourceIds()

            if (_libraryState.value.librarySources.isEmpty()) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "请先选择书库目录"
                    )
                }
                return@launch
            }

            val failedTasks: List<MatchTaskEntity> = try {
                libraryRepository.getMatchTasksByStatusesForSourceIds(
                    sourceIds = sourceIds,
                    statuses = listOf(MatchTaskStatus.Failed)
                )
            } catch (e: Exception) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = e.message ?: "读取失败任务失败"
                    )
                }
                return@launch
            }

            if (failedTasks.isEmpty()) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "没有失败任务需要重试"
                    )
                }

                setHomeTab(HomeTab.Tasks)
                setMatchTaskFilter(MatchTaskFilter.Failed)
                return@launch
            }

            val batchThreads = batchMatchThreads()

            for (chunk in failedTasks.chunked(batchThreads)) {
                chunk.map { task ->
                    async {
                        runCatching {
                            retryOneMatchTask(task)
                        }.onFailure {
                            // 单个任务失败不影响其他任务
                        }
                    }
                }.awaitAll()
            }

            _libraryState.update {
                it.copy(isBatchMatching = false)
            }

            refreshMatchTaskFilterCountsOnce()

            setHomeTab(HomeTab.Tasks)
            setMatchTaskFilter(MatchTaskFilter.All)
        }
    }

    private suspend fun retryOneMatchTask(
        task: MatchTaskEntity
    ) {
        val query = buildMatchQueryFromName(task.displayName)

        val runningTask = task.copy(
            query = query,
            status = MatchTaskStatus.Running,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )

        libraryRepository.updateMatchTask(runningTask)

        executeMatchTaskSearch(
            task = runningTask,
            query = query,
            localPageCount = runningTask.localPageCount,
            defaultSearchFailMessage = "重新搜索失败"
        )
    }

    fun matchById() {
        val rawInput = _matchState.value.query.trim()

        val galleryId = Regex("\\d+")
            .find(rawInput)
            ?.value

        if (galleryId.isNullOrBlank()) {
            _matchState.update {
                it.copy(
                    error = "请输入有效的 Gallery ID",
                    searchDiagnosticSummary = null,
                    searchDiagnosticRaw = null,
                    showSearchDiagnosticRaw = false
                )
            }
            return
        }

        viewModelScope.launch {
            _matchState.update {
                it.copy(
                    isSearching = true,
                    error = null,
                    candidates = emptyList(),
                    searchDiagnosticSummary = null,
                    searchDiagnosticRaw = null,
                    showSearchDiagnosticRaw = false
                )
            }

            val meta = runCatching {
                hitomiRepository.fetchMetaById(galleryId)
            }.getOrNull()

            if (meta == null) {
                _matchState.update {
                    it.copy(
                        isSearching = false,
                        error = "没有找到 ID：$galleryId",
                        searchDiagnosticSummary = null,
                        searchDiagnosticRaw = null,
                        showSearchDiagnosticRaw = false
                    )
                }
                return@launch
            }

            val candidates = prepareHitomiCandidates(
                candidates = listOf(meta),
                localPageCount = _matchState.value.localPageCount
            )

            _matchState.update {
                it.copy(
                    isSearching = false,
                    error = if (candidates.isEmpty()) {
                        "候选均被语言过滤"
                    } else {
                        null
                    },
                    searchDiagnosticSummary = null,
                    searchDiagnosticRaw = null,
                    showSearchDiagnosticRaw = false,
                    candidates = candidates
                )
            }
        }
    }

    fun exportDatabase(uri: Uri) {
        viewModelScope.launch {
            try {
                libraryRepository.exportDatabaseTo(uri)

                _libraryState.update {
                    it.copy(error = "数据库已导出")
                }
            } catch (e: Exception) {
                _libraryState.update {
                    it.copy(error = e.message ?: "导出数据库失败")
                }
            }
        }
    }

    fun importDatabase(uri: Uri) {
        viewModelScope.launch {
            try {
                libraryObserveJob?.cancel()
                sourceObserveJob?.cancel()
                directoryFolderObserveJob?.cancel()
                directoryBookObserveJob?.cancel()
                tagObserveJob?.cancel()
                taskObserveJob?.cancel()
                taskCountObserveJob?.cancel()
                matchTaskDetailJob?.cancel()
                matchTaskCandidateJob?.cancel()

                libraryRepository.importDatabaseFrom(uri)

                _bookDetailState.value = BookDetailUiState()
                _matchTaskDetailState.value = MatchTaskDetailUiState()
                _matchState.value = MatchUiState()
                _readerState.value = ReaderUiState()

                _libraryState.update {
                    it.copy(
                        books = emptyList(),
                        librarySources = emptyList(),
                        sourceScopes = emptyList(),
                        directoryFolders = emptyList(),
                        directoryBooks = emptyList(),
                        tagItems = emptyList(),
                        matchTasks = emptyList(),
                        unqueuedUnmatchedBooks = emptyList(),
                        matchTaskFilterCounts = emptyMap(),
                        selectedTagKeys = emptySet(),
                        searchQuery = "",
                        error = "数据库已导入"
                    )
                }

                observeSources()
            } catch (e: Exception) {
                _libraryState.update {
                    it.copy(error = e.message ?: "导入数据库失败")
                }

                observeSources()
            }
        }
    }

    private fun cleanFileName(
        name: String,
        removeUnderscore: Boolean,
        removeTrailingNumberSuffix: Boolean
    ): String {
        var result = name
            .replace(Regex("\\.(zip|cbz|rar|cbr|7z)$", RegexOption.IGNORE_CASE), "")
            .trim()

        if (removeTrailingNumberSuffix) {
            // 去掉末尾的 (1)、(2)、(123) 等重复编号
            result = result.replace(
                Regex("\\s*\\(\\d+\\)\\s*$"),
                ""
            )
        }

        if (removeUnderscore) {
            // 删除标题中的下划线；例如 a_b 或 a _ b 会变成 a b
            result = result.replace(
                Regex("\\s*_\\s*"),
                " "
            )
        } else {
            result = result.replace("_", " ")
        }

        return result
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun onReaderPageChanged(index: Int) {
        val state = _readerState.value
        if (state.pages.isEmpty()) return
        if (index !in state.pages.indices) return

        _readerState.update {
            it.copy(pageIndex = index)
        }

        preloadAround(index)
    }

    fun ensureReaderPageLoaded(index: Int) {
        ensurePageLoaded(index)
    }

    fun scanSource(sourceId: String) {
        scanSources(listOf(sourceId))
    }

    private fun scanSources(sourceIds: List<String>) {
        viewModelScope.launch {
            val targetSourceIds = if (sourceIds.isEmpty()) {
                _libraryState.value.librarySources.map { it.id }
            } else {
                sourceIds
            }

            if (targetSourceIds.isEmpty()) {
                _libraryState.update {
                    it.copy(error = "请先添加书库目录")
                }
                return@launch
            }

            _libraryState.update {
                it.copy(
                    isScanning = true,
                    scanDone = 0,
                    scanTotal = 0,
                    scanCurrentName = null,
                    error = null
                )
            }

            try {
                for (sourceId in targetSourceIds) {
                    libraryRepository.scanSource(
                        sourceId = sourceId,
                        onProgress = { progress ->
                            _libraryState.update {
                                it.copy(
                                    scanDone = progress.done,
                                    scanTotal = progress.total,
                                    scanCurrentName = progress.currentName
                                )
                            }
                        }
                    )
                }

                _libraryState.update {
                    it.copy(
                        isScanning = false,
                        scanCurrentName = null
                    )
                }

                refreshLibraryBooks()
                observeTagItems()
                observeMatchTasks()
                observeMatchTaskFilterCounts()
                observeDirectory()
            } catch (e: Exception) {
                _libraryState.update {
                    it.copy(
                        isScanning = false,
                        scanDone = 0,
                        scanTotal = 0,
                        scanCurrentName = null,
                        error = e.message ?: "扫描失败"
                    )
                }
            }
        }
    }

    fun openReaderFromMatchTask(
        task: MatchTaskEntity,
        onOpened: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val book = libraryRepository.getBookByUriString(task.bookUriString)

                if (book == null) {
                    _matchTaskDetailState.update {
                        it.copy(
                            error = "找不到该任务对应的本地文件，可能文件已移动或数据库记录已失效"
                        )
                    }
                    return@launch
                }

                openBook(book)
                onOpened()
            } catch (e: Exception) {
                _matchTaskDetailState.update {
                    it.copy(
                        error = e.message ?: "打开阅读器失败"
                    )
                }
            }
        }
    }

    private fun ensurePageLoaded(index: Int) {
        val state = _readerState.value
        val book = state.book ?: return
        val pages = state.pages

        if (index !in pages.indices) return
        if (state.pageFiles.containsKey(index)) return
        if (state.loadingPageIndices.contains(index)) return

        _readerState.update {
            it.copy(
                loadingPageIndices = it.loadingPageIndices + index,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val archiveUri = Uri.parse(book.uriString)
                val entryName = pages[index]

                val extracted = ComicArchiveReader.extractPageWithInfo(
                    context = app,
                    archiveUri = archiveUri,
                    entryName = entryName,
                    pageIndex = index
                )

                _readerState.update { current ->
                    val newFiles = if (extracted != null) {
                        current.pageFiles + (index to extracted.file)
                    } else {
                        current.pageFiles
                    }

                    val newInfos = if (extracted != null) {
                        current.pageInfos + (index to extracted.info)
                    } else {
                        current.pageInfos
                    }

                    current.copy(
                        pageFiles = newFiles,
                        pageInfos = newInfos,
                        loadingPageIndices = current.loadingPageIndices - index,
                        error = if (extracted == null && index == current.pageIndex) {
                            "页面读取失败"
                        } else {
                            current.error
                        }
                    )
                }
            } catch (e: Exception) {
                _readerState.update { current ->
                    current.copy(
                        loadingPageIndices = current.loadingPageIndices - index,
                        error = if (index == current.pageIndex) {
                            e.message ?: "页面读取失败"
                        } else {
                            current.error
                        }
                    )
                }
            }
        }
    }

    fun setShowTagNamespacePrefix(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_TAG_NAMESPACE_PREFIX, show)
            .apply()

        _settingsState.update {
            it.copy(showTagNamespacePrefix = show)
        }
    }

    fun setDistinguishGenderTags(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DISTINGUISH_GENDER_TAGS, enabled)
            .apply()

        _settingsState.update {
            it.copy(distinguishGenderTags = enabled)
        }

        // 切换合并模式后，原有的性别标签筛选键可能失效，清空筛选并重新统计
        _libraryState.update {
            it.copy(selectedTagKeys = emptySet())
        }
        observeTagItems()
        refreshLibraryBooks()
    }

    fun setAutoMatchSingleResult(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_MATCH_SINGLE_RESULT, enabled)
            .apply()

        _settingsState.update {
            it.copy(autoMatchSingleResult = enabled)
        }
    }

    fun setAutoMatchSamePageFirst(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_MATCH_SAME_PAGE_FIRST, enabled)
            .apply()

        _settingsState.update {
            it.copy(autoMatchSamePageFirst = enabled)
        }
    }

    fun setSettingsTab(tab: SettingsTab) {
        _settingsState.update {
            it.copy(settingsTab = tab)
        }
    }

    fun setAutoOpenNextReviewTask(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_OPEN_NEXT_REVIEW_TASK, enabled)
            .apply()

        _settingsState.update {
            it.copy(autoOpenNextReviewTask = enabled)
        }
    }

    fun setAutoMatchExactTitle(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_MATCH_EXACT_TITLE, enabled)
            .apply()

        _settingsState.update {
            it.copy(autoMatchExactTitle = enabled)
        }
    }

    fun setAutoMatchUniqueSamePage(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_MATCH_UNIQUE_SAME_PAGE, enabled)
            .apply()

        _settingsState.update {
            it.copy(autoMatchUniqueSamePage = enabled)
        }
    }

    fun setFilteredMatchLanguages(raw: String) {
        val languages = parseFilteredMatchLanguages(raw)

        prefs.edit()
            .putString(KEY_FILTERED_MATCH_LANGUAGES, raw)
            .apply()

        _settingsState.update {
            it.copy(
                filteredMatchLanguagesText = raw,
                filteredMatchLanguages = languages
            )
        }

        val detailTask = _matchTaskDetailState.value.task
        if (detailTask != null) {
            openMatchTaskDetail(detailTask)
        }
    }

    fun setMatchSearchTimeoutSeconds(raw: String) {
        val text = raw.filter { it.isDigit() }.take(3)
        val parsed = text.toIntOrNull()

        if (parsed == null) {
            _settingsState.update {
                it.copy(matchSearchTimeoutSecondsText = text)
            }
            return
        }

        val fixed = parsed.coerceIn(
            MIN_MATCH_SEARCH_TIMEOUT_SECONDS,
            MAX_MATCH_SEARCH_TIMEOUT_SECONDS
        )

        prefs.edit()
            .putInt(KEY_MATCH_SEARCH_TIMEOUT_SECONDS, fixed)
            .apply()

        _settingsState.update {
            it.copy(
                matchSearchTimeoutSecondsText = text,
                matchSearchTimeoutSeconds = fixed
            )
        }
    }

    fun setBatchMatchThreads(raw: String) {
        val text = raw.filter { it.isDigit() }.take(2)
        val parsed = text.toIntOrNull()

        if (parsed == null) {
            _settingsState.update {
                it.copy(batchMatchThreadsText = text)
            }
            return
        }

        val fixed = parsed.coerceIn(
            MIN_BATCH_MATCH_THREADS,
            MAX_BATCH_MATCH_THREADS
        )

        prefs.edit()
            .putInt(KEY_BATCH_MATCH_THREADS, fixed)
            .apply()

        _settingsState.update {
            it.copy(
                batchMatchThreadsText = text,
                batchMatchThreads = fixed
            )
        }
    }

    fun toggleSearchDiagnosticRaw() {
        _matchState.update {
            it.copy(showSearchDiagnosticRaw = !it.showSearchDiagnosticRaw)
        }
    }

    fun setShowRematchButtonInLibrary(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_REMATCH_BUTTON_IN_LIBRARY, show)
            .apply()

        _settingsState.update {
            it.copy(showRematchButtonInLibrary = show)
        }
    }

    fun setOpenBookDirectlyInReader(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_OPEN_BOOK_DIRECTLY_IN_READER, enabled)
            .apply()

        _settingsState.update {
            it.copy(openBookDirectlyInReader = enabled)
        }
    }

    fun setShowGridCoverPlayButton(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_GRID_COVER_PLAY_BUTTON, show)
            .apply()

        _settingsState.update {
            it.copy(showGridCoverPlayButton = show)
        }
    }

    fun setLibraryLayoutMode(mode: LibraryLayoutMode) {
        prefs.edit()
            .putString(KEY_LIBRARY_LAYOUT_MODE, mode.name)
            .apply()

        _settingsState.update {
            it.copy(libraryLayoutMode = mode)
        }
    }

    fun toggleLibraryLayoutMode() {
        val current = _settingsState.value.libraryLayoutMode
        val next = when (current) {
            LibraryLayoutMode.List -> LibraryLayoutMode.Grid
            LibraryLayoutMode.Grid -> LibraryLayoutMode.Directory
            LibraryLayoutMode.Directory -> LibraryLayoutMode.List
        }

        setLibraryLayoutMode(next)
    }

    fun setLibraryGridColumns(columns: Int) {
        val fixed = columns.coerceIn(2, 6)

        prefs.edit()
            .putInt(KEY_LIBRARY_GRID_COLUMNS, fixed)
            .apply()

        _settingsState.update {
            it.copy(libraryGridColumns = fixed)
        }
    }

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_SELECTED_SOURCE_SCOPE = "selected_source_scope"
        private const val KEY_SHOW_TAG_NAMESPACE_PREFIX = "show_tag_namespace_prefix"
        private const val KEY_DISTINGUISH_GENDER_TAGS = "distinguish_gender_tags"
        private const val KEY_REMOVE_UNDERSCORE_IN_MATCH_TITLE = "remove_underscore_in_match_title"
        private const val KEY_REMOVE_TRAILING_NUMBER_SUFFIX_IN_MATCH_TITLE = "remove_trailing_number_suffix_in_match_title"
        private const val KEY_AUTO_MATCH_SINGLE_RESULT = "auto_match_single_result"
        private const val KEY_AUTO_MATCH_SAME_PAGE_FIRST = "auto_match_same_page_first"
        private const val KEY_AUTO_OPEN_NEXT_REVIEW_TASK = "auto_open_next_review_task"
        private const val KEY_AUTO_MATCH_EXACT_TITLE = "auto_match_exact_title"
        private const val KEY_AUTO_MATCH_UNIQUE_SAME_PAGE = "auto_match_unique_same_page"
        private const val KEY_SHOW_REMATCH_BUTTON_IN_LIBRARY = "show_rematch_button_in_library"
        private const val KEY_OPEN_BOOK_DIRECTLY_IN_READER = "open_book_directly_in_reader"
        private const val KEY_SHOW_GRID_COVER_PLAY_BUTTON = "show_grid_cover_play_button"
        private const val KEY_LIBRARY_LAYOUT_MODE = "library_layout_mode"
        private const val KEY_LIBRARY_GRID_COLUMNS = "library_grid_columns"
        private const val KEY_BOOK_SORT_MODE = "book_sort_mode"
        private const val KEY_FILTERED_MATCH_LANGUAGES = "filtered_match_languages"
        private const val KEY_MATCH_SEARCH_TIMEOUT_SECONDS = "match_search_timeout_seconds"
        private const val DEFAULT_MATCH_SEARCH_TIMEOUT_SECONDS = 30
        private const val MIN_MATCH_SEARCH_TIMEOUT_SECONDS = 10
        private const val MAX_MATCH_SEARCH_TIMEOUT_SECONDS = 360
        private const val KEY_BATCH_MATCH_THREADS = "batch_match_threads"
        private const val DEFAULT_BATCH_MATCH_THREADS = 1
        private const val MIN_BATCH_MATCH_THREADS = 1
        private const val MAX_BATCH_MATCH_THREADS = 6
        private const val SKIPPED_FROM_UNQUEUED_MESSAGE = "用户从未匹配列表手动跳过"
    }
}
