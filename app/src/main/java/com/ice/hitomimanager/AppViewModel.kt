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
import com.ice.hitomimanager.data.model.HitomiBookMeta
import com.ice.hitomimanager.data.repository.HitomiMetadataRepository
import com.ice.hitomimanager.data.local.entity.TagEntity
import kotlinx.coroutines.Job
import com.ice.hitomimanager.data.model.HomeTab
import com.ice.hitomimanager.data.model.MatchTaskFilter
import com.ice.hitomimanager.data.model.MatchTaskStatus
import com.ice.hitomimanager.data.model.SettingsTab
import com.ice.hitomimanager.data.model.TagCountItem
import com.ice.hitomimanager.data.model.TagSortMode
import com.ice.hitomimanager.data.model.LibraryLayoutMode
import com.ice.hitomimanager.data.model.TagFilterTab

data class LibraryUiState(
    val folderUriString: String? = null,
    val books: List<BookItem> = emptyList(),

    val homeTab: HomeTab = HomeTab.Library,

    val selectedTagKeys: Set<String> = emptySet(),
    val tagItems: List<TagCountItem> = emptyList(),
    val tagSortMode: TagSortMode = TagSortMode.CountDesc,

    val searchQuery: String = "",

    val taskFilter: MatchTaskFilter = MatchTaskFilter.All,
    val matchTasks: List<MatchTaskEntity> = emptyList(),
    val unqueuedUnmatchedBooks: List<BookItem> = emptyList(),

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
    val showTagNamespacePrefix: Boolean = true,

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
    val libraryLayoutMode: LibraryLayoutMode = LibraryLayoutMode.List,
    val libraryGridColumns: Int = 3,
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
    val error: String? = null
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
    private var tagObserveJob: Job? = null

    private var taskObserveJob: Job? = null

    val matchState: StateFlow<MatchUiState> = _matchState.asStateFlow()

    private val prefs = app.getSharedPreferences(
        "hitomi_manager_prefs",
        Context.MODE_PRIVATE
    )

    private val libraryRepository = LibraryRepository(app)

    private val _libraryState = MutableStateFlow(
        LibraryUiState(
            folderUriString = prefs.getString(KEY_FOLDER_URI, null)
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
            removeUnderscoreInMatchTitle = prefs.getBoolean(KEY_REMOVE_UNDERSCORE_IN_MATCH_TITLE, true),
            removeTrailingNumberSuffixInMatchTitle = prefs.getBoolean(KEY_REMOVE_TRAILING_NUMBER_SUFFIX_IN_MATCH_TITLE, true),
            autoMatchExactTitle = prefs.getBoolean(KEY_AUTO_MATCH_EXACT_TITLE, true),
            autoMatchSingleResult = prefs.getBoolean(KEY_AUTO_MATCH_SINGLE_RESULT, false),
            autoMatchUniqueSamePage = prefs.getBoolean(KEY_AUTO_MATCH_UNIQUE_SAME_PAGE, true),
            autoMatchSamePageFirst = prefs.getBoolean(KEY_AUTO_MATCH_SAME_PAGE_FIRST, true),
            autoOpenNextReviewTask = prefs.getBoolean(KEY_AUTO_OPEN_NEXT_REVIEW_TASK, true),
            showRematchButtonInLibrary = prefs.getBoolean(KEY_SHOW_REMATCH_BUTTON_IN_LIBRARY, true),
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
        observeTagItems()
        refreshLibraryBooks()
        observeMatchTasks()
    }

    private fun recoverInterruptedMatchTasks() {
        viewModelScope.launch {
            runCatching {
                libraryRepository.markInterruptedMatchTasksAsFailed()
            }
        }
    }

    private fun observeMatchTasks() {
        taskObserveJob?.cancel()

        val root = currentLibraryRoot()

        if (root == null) {
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
                libraryRepository.observeUnqueuedUnmatchedBooks(root)
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

                libraryRepository.observeMatchTasksByStatuses(
                    libraryRootUriString = root,
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

        val root = currentLibraryRoot()

        if (root == null) {
            _libraryState.update {
                it.copy(tagItems = emptyList())
            }
            return
        }

        tagObserveJob = viewModelScope.launch {
            libraryRepository.observeTagCounts(root).collectLatest { tags ->
                _libraryState.update { state ->
                    state.copy(
                        tagItems = sortTags(
                            tags = tags,
                            mode = state.tagSortMode
                        )
                    )
                }
            }
        }
    }

    private fun refreshLibraryBooks() {
        libraryObserveJob?.cancel()

        val state = _libraryState.value
        val root = state.folderUriString

        if (root == null) {
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
                    libraryRepository.observeBooksBySearch(
                        libraryRootUriString = root,
                        query = searchQuery
                    )
                }

                selectedTagKeys.isNotEmpty() -> {
                    libraryRepository.observeBooksByAllTags(
                        libraryRootUriString = root,
                        tagKeys = selectedTagKeys
                    )
                }

                else -> {
                    libraryRepository.observeBooks(root)
                }
            }

            flow.collectLatest { books ->
                _libraryState.update {
                    it.copy(books = books)
                }
            }
        }
    }

    fun setHomeTab(tab: HomeTab) {
        _libraryState.update {
            it.copy(homeTab = tab)
        }
    }

    private fun currentLibraryRoot(): String? {
        return _libraryState.value.folderUriString
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

            val root = currentLibraryRoot()

            if (root == null) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "请先选择书库目录"
                    )
                }
                return@launch
            }

            val unmatchedBooks: List<BookItem> = try {
                libraryRepository.getUnmatchedBooks(
                    libraryRootUriString = root
                )
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

            for (book in unmatchedBooks) {
                runCatching {
                    processOneBatchMatchBook(
                        book = book,
                        libraryRootUriString = root
                    )
                }.onFailure {
                    // 单个任务失败不终止整个批量匹配
                }
            }

            _libraryState.update {
                it.copy(isBatchMatching = false)
            }

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
                        tagItems = emptyList(),
                        matchTasks = emptyList(),
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

        var task = libraryRepository.getMatchTask(taskId) ?: return

        task = task.copy(
            status = MatchTaskStatus.Running,
            updatedAt = System.currentTimeMillis()
        )
        libraryRepository.updateMatchTask(task)

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

        val candidates = runCatching {
            hitomiRepository.searchTitle(query)
        }.getOrElse { e ->
            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Failed,
                    errorMessage = e.message ?: "搜索失败",
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        if (candidates.isEmpty()) {
            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Failed,
                    errorMessage = "没有搜索到候选",
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
            taskId = taskId,
            candidates = candidates,
            selectedGalleryId = autoSelected?.id
        )

        if (autoSelected != null) {
            val saved = runCatching {
                libraryRepository.bindHitomiMeta(
                    uriString = book.uriString,
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

    fun clearTagFilters() {
        _libraryState.update {
            it.copy(
                selectedTagKeys = emptySet()
            )
        }

        refreshLibraryBooks()
    }

    private fun preloadAround(index: Int) {
        for (i in (index - 2)..(index + 2)) {
            ensurePageLoaded(i)
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
                tagSortMode = mode,
                tagItems = sortTags(
                    tags = state.tagItems,
                    mode = mode
                )
            )
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
                    it.copy(candidates = candidates)
                }
            }
        }
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
            libraryRepository.updateMatchTask(
                task.copy(
                    status = MatchTaskStatus.Skipped,
                    errorMessage = "用户手动标记跳过",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
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

            val root = currentLibraryRoot()

            if (root == null) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "请先选择书库目录"
                    )
                }
                return@launch
            }

            val failedTasks: List<MatchTaskEntity> = try {
                libraryRepository.getMatchTasksByStatuses(
                    libraryRootUriString = root,
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

            val retryableTasks = failedTasks.filter { task ->
                task.errorMessage?.trim() != "没有搜索到候选"
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

            for (task in retryableTasks) {
                runCatching {
                    retryOneMatchTask(task)
                }.onFailure {
                    // 单个任务失败不影响其他任务
                }
            }

            _libraryState.update {
                it.copy(isBatchMatching = false)
            }

            setHomeTab(HomeTab.Tasks)
            setMatchTaskFilter(MatchTaskFilter.All)
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

        val root = uri.toString()

        prefs.edit()
            .putString(KEY_FOLDER_URI, root)
            .apply()

        _settingsState.update {
            it.copy(folderUriString = root)
        }

        _libraryState.update {
            it.copy(
                folderUriString = root,
                selectedTagKeys = emptySet(),
                searchQuery = "",
                error = null
            )
        }

        refreshLibraryBooks()
        observeTagItems()
        observeMatchTasks()

        scanFolder(uri)
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
        val uriString = _libraryState.value.folderUriString ?: return
        scanFolder(Uri.parse(uriString))
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

    fun searchMatch() {
        val state = _matchState.value
        val query = state.query.trim()

        if (query.isBlank()) {
            _matchState.update {
                it.copy(error = "搜索词不能为空")
            }
            return
        }

        _matchState.update {
            it.copy(
                isSearching = true,
                error = null,
                candidates = emptyList()
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                hitomiRepository.searchTitle(query)
            }

            result.onSuccess { candidates ->
                _matchState.update {
                    it.copy(
                        candidates = candidates,
                        isSearching = false,
                        error = if (candidates.isEmpty()) {
                            "没有搜索到候选，可能是 WebView 搜索失败或标题需要缩短"
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { e ->
                _matchState.update {
                    it.copy(
                        isSearching = false,
                        error = e.message ?: "搜索失败"
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

            val root = currentLibraryRoot()

            if (root == null) {
                _libraryState.update {
                    it.copy(
                        isBatchMatching = false,
                        error = "请先选择书库目录"
                    )
                }
                return@launch
            }

            val failedTasks: List<MatchTaskEntity> = try {
                libraryRepository.getMatchTasksByStatuses(
                    libraryRootUriString = root,
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

            for (task in failedTasks) {
                runCatching {
                    retryOneMatchTask(task)
                }.onFailure {
                    // 单个任务失败不影响其他任务
                }
            }

            _libraryState.update {
                it.copy(isBatchMatching = false)
            }

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

        val localPageCount = runningTask.localPageCount

        if (localPageCount == null || localPageCount <= 0) {
            libraryRepository.updateMatchTask(
                runningTask.copy(
                    status = MatchTaskStatus.Skipped,
                    errorMessage = "无法读取本地页数",
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        val candidates = runCatching {
            hitomiRepository.searchTitle(query)
        }.getOrElse { e ->
            libraryRepository.updateMatchTask(
                runningTask.copy(
                    status = MatchTaskStatus.Failed,
                    errorMessage = e.message ?: "重新搜索失败",
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        if (candidates.isEmpty()) {
            libraryRepository.replaceCandidatesForTask(
                taskId = runningTask.id,
                candidates = emptyList(),
                selectedGalleryId = null
            )

            libraryRepository.updateMatchTask(
                runningTask.copy(
                    status = MatchTaskStatus.Failed,
                    candidateCount = 0,
                    errorMessage = "没有搜索到候选",
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
            taskId = runningTask.id,
            candidates = candidates,
            selectedGalleryId = autoSelected?.id
        )

        if (autoSelected != null) {
            val saved = runCatching {
                libraryRepository.bindHitomiMeta(
                    uriString = runningTask.bookUriString,
                    meta = autoSelected
                )
            }.isSuccess

            if (saved) {
                libraryRepository.updateMatchTask(
                    runningTask.copy(
                        status = MatchTaskStatus.AutoMatched,
                        matchedGalleryId = autoSelected.id,
                        candidateCount = candidates.size,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                libraryRepository.updateMatchTask(
                    runningTask.copy(
                        status = MatchTaskStatus.Failed,
                        candidateCount = candidates.size,
                        errorMessage = "保存匹配结果失败",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            libraryRepository.updateMatchTask(
                runningTask.copy(
                    status = MatchTaskStatus.NeedReview,
                    matchedGalleryId = null,
                    candidateCount = candidates.size,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun matchById() {
        val rawInput = _matchState.value.query.trim()

        val galleryId = Regex("\\d+")
            .find(rawInput)
            ?.value

        if (galleryId.isNullOrBlank()) {
            _matchState.update {
                it.copy(
                    error = "请输入有效的 Gallery ID"
                )
            }
            return
        }

        viewModelScope.launch {
            _matchState.update {
                it.copy(
                    isSearching = true,
                    error = null,
                    candidates = emptyList()
                )
            }

            val meta = runCatching {
                hitomiRepository.fetchMetaById(galleryId)
            }.getOrNull()

            if (meta == null) {
                _matchState.update {
                    it.copy(
                        isSearching = false,
                        error = "没有找到 ID：$galleryId"
                    )
                }
                return@launch
            }

            _matchState.update {
                it.copy(
                    isSearching = false,
                    error = null,
                    candidates = listOf(meta)
                )
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

    private fun scanFolder(uri: Uri) {
        viewModelScope.launch {
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
                libraryRepository.scanAndSync(
                    treeUri = uri,
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

                _libraryState.update {
                    it.copy(
                        isScanning = false,
                        scanCurrentName = null
                    )
                }

                refreshLibraryBooks()
                observeTagItems()
                observeMatchTasks()
            } catch (e: Exception) {
                _libraryState.update {
                    it.copy(
                        isScanning = false,
                        scanDone = 0,
                        scanTotal = 0,
                        scanCurrentName = null
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

    fun setShowRematchButtonInLibrary(show: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SHOW_REMATCH_BUTTON_IN_LIBRARY, show)
            .apply()

        _settingsState.update {
            it.copy(showRematchButtonInLibrary = show)
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
        val next = if (current == LibraryLayoutMode.List) {
            LibraryLayoutMode.Grid
        } else {
            LibraryLayoutMode.List
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
        private const val KEY_SHOW_TAG_NAMESPACE_PREFIX = "show_tag_namespace_prefix"
        private const val KEY_REMOVE_UNDERSCORE_IN_MATCH_TITLE = "remove_underscore_in_match_title"
        private const val KEY_REMOVE_TRAILING_NUMBER_SUFFIX_IN_MATCH_TITLE = "remove_trailing_number_suffix_in_match_title"
        private const val KEY_AUTO_MATCH_SINGLE_RESULT = "auto_match_single_result"
        private const val KEY_AUTO_MATCH_SAME_PAGE_FIRST = "auto_match_same_page_first"
        private const val KEY_AUTO_OPEN_NEXT_REVIEW_TASK = "auto_open_next_review_task"
        private const val KEY_AUTO_MATCH_EXACT_TITLE = "auto_match_exact_title"
        private const val KEY_AUTO_MATCH_UNIQUE_SAME_PAGE = "auto_match_unique_same_page"
        private const val KEY_SHOW_REMATCH_BUTTON_IN_LIBRARY = "show_rematch_button_in_library"
        private const val KEY_LIBRARY_LAYOUT_MODE = "library_layout_mode"
        private const val KEY_LIBRARY_GRID_COLUMNS = "library_grid_columns"
    }
}