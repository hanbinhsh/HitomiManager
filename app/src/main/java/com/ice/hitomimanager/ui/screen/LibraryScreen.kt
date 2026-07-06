package com.ice.hitomimanager.ui.screen

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ice.hitomimanager.LibraryUiState
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.BookSortMode
import com.ice.hitomimanager.data.model.HomeTab
import com.ice.hitomimanager.data.model.LibraryFolderNode
import com.ice.hitomimanager.data.model.TagCountItem
import com.ice.hitomimanager.data.model.TagSortMode
import java.io.File
import com.ice.hitomimanager.data.model.MatchTaskFilter
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ice.hitomimanager.data.model.MatchTaskStatus
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TabRow
import com.ice.hitomimanager.data.model.LibraryLayoutMode
import com.ice.hitomimanager.data.model.TagFilterTab
import androidx.compose.material3.Tab
import kotlinx.coroutines.delay

private data class DirectoryScrollPosition(
    val index: Int,
    val offset: Int
)

private data class DirectoryRestoreRequest(
    val directoryKey: String,
    val position: DirectoryScrollPosition,
    val highlightFolderKey: String?,
    val minContentVersion: Long
)

private const val HighlightHoldMillis = 1000L
private const val HighlightFadeMillis = 450

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    showTagNamespacePrefix: Boolean,
    distinguishGenderTags: Boolean = true,
    onHomeTabChange: (HomeTab) -> Unit,
    onSourceScopeChange: (String) -> Unit,
    onOpenDirectory: (LibraryFolderNode) -> Unit,
    onDirectoryUp: () -> Unit,
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTag: (TagCountItem) -> Unit,
    onClearTagFilters: () -> Unit,
    onTagSortModeChange: (TagSortMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit,
    onMatchTaskFilterChange: (MatchTaskFilter) -> Unit,
    onOpenMatchTask: (MatchTaskEntity) -> Unit,
    onSkipMatchTask: (MatchTaskEntity) -> Unit,
    onCancelSkippedMatchTask: (MatchTaskEntity) -> Unit,
    onSkipUnqueuedBook: (BookItem) -> Unit,
    onRetryMatchTask: (MatchTaskEntity) -> Unit,
    onRetryFailedMatchTasks: () -> Unit,
    showRematchButtonInLibrary: Boolean,
    showGridCoverPlayButton: Boolean,
    onRetryFailedExceptNoCandidates: () -> Unit,
    onStartBatchMatch: () -> Unit,
    libraryLayoutMode: LibraryLayoutMode,
    libraryGridColumns: Int,
    onToggleLibraryLayoutMode: () -> Unit,
    onBookSortModeChange: (BookSortMode) -> Unit,
    onTagFilterTabChange: (TagFilterTab) -> Unit,
    highlightedBookUri: String?,
    onHighlightedBookConsumed: () -> Unit,
    onReadBook: (BookItem) -> Unit,
) {
    val libraryListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    val directoryListState = rememberLazyListState()
    val tagListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val searchGridState = rememberLazyGridState()
    val taskListState = rememberLazyListState()
    var sortMenuExpanded by remember {
        mutableStateOf(false)
    }
    var observedBookSortMode by remember {
        mutableStateOf(state.bookSortMode)
    }
    val directoryScrollPositions = remember {
        mutableMapOf<String, DirectoryScrollPosition>()
    }
    val currentDirectoryKey = directoryStateKey(state)
    var pendingDirectoryRestore by remember {
        mutableStateOf<DirectoryRestoreRequest?>(null)
    }
    var highlightedDirectoryFolderKey by remember {
        mutableStateOf<String?>(null)
    }
    var consumedDirectoryScrollToken by rememberSaveable {
        mutableStateOf(state.directoryScrollToken)
    }
    var pendingBookReturnDirectoryKey by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var pendingBookReturnDirectoryIndex by rememberSaveable {
        mutableStateOf(0)
    }
    var pendingBookReturnDirectoryOffset by rememberSaveable {
        mutableStateOf(0)
    }

    fun rememberCurrentDirectoryPosition() {
        directoryScrollPositions[currentDirectoryKey] = DirectoryScrollPosition(
            index = directoryListState.firstVisibleItemIndex,
            offset = directoryListState.firstVisibleItemScrollOffset
        )
    }

    fun openDirectoryWithPositionMemory(folder: LibraryFolderNode) {
        rememberCurrentDirectoryPosition()
        onOpenDirectory(folder)
    }

    fun navigateDirectoryUpWithPositionRestore() {
        val parentKey = parentDirectoryStateKey(state)
        if (parentKey != null) {
            pendingDirectoryRestore = DirectoryRestoreRequest(
                directoryKey = parentKey,
                position = directoryScrollPositions[parentKey] ?: DirectoryScrollPosition(0, 0),
                highlightFolderKey = currentDirectoryFolderKey(state),
                minContentVersion = state.directoryContentVersion
            )
        }
        onDirectoryUp()
    }

    fun openBookWithPositionMemory(book: BookItem) {
        if (state.homeTab == HomeTab.Library && libraryLayoutMode == LibraryLayoutMode.Directory) {
            pendingBookReturnDirectoryKey = currentDirectoryKey
            pendingBookReturnDirectoryIndex = directoryListState.firstVisibleItemIndex
            pendingBookReturnDirectoryOffset = directoryListState.firstVisibleItemScrollOffset
        }
        onOpenBook(book)
    }

    suspend fun scrollTabToTop(tab: HomeTab) {
        when (tab) {
            HomeTab.Library -> {
                when (libraryLayoutMode) {
                    LibraryLayoutMode.Grid -> libraryGridState.scrollToItem(0)
                    LibraryLayoutMode.Directory -> directoryListState.scrollToItem(0)
                    LibraryLayoutMode.List -> libraryListState.scrollToItem(0)
                }
            }

            HomeTab.Tags -> {
                tagListState.scrollToItem(0)
            }

            HomeTab.Search -> {
                if (libraryLayoutMode == LibraryLayoutMode.Grid) {
                    searchGridState.scrollToItem(0)
                } else {
                    searchListState.scrollToItem(0)
                }
            }

            HomeTab.Tasks -> {
                taskListState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(state.homeTabReselectTick) {
        if (state.homeTabReselectTick > 0L) {
            scrollTabToTop(state.homeTab)
        }
    }

    LaunchedEffect(state.bookSortMode) {
        if (state.bookSortMode != observedBookSortMode) {
            observedBookSortMode = state.bookSortMode
            if (state.homeTab == HomeTab.Library || state.homeTab == HomeTab.Search) {
                scrollTabToTop(state.homeTab)
            }
        }
    }

    LaunchedEffect(
        libraryLayoutMode,
        currentDirectoryKey,
        state.directoryContentVersion,
        pendingDirectoryRestore
    ) {
        val request = pendingDirectoryRestore
        if (
            libraryLayoutMode == LibraryLayoutMode.Directory &&
            request != null &&
            request.directoryKey == currentDirectoryKey &&
            state.directoryContentVersion > request.minContentVersion
        ) {
            directoryListState.scrollToItem(
                index = request.position.index,
                scrollOffset = request.position.offset
            )
            highlightedDirectoryFolderKey = request.highlightFolderKey
            pendingDirectoryRestore = null
        }
    }

    LaunchedEffect(libraryLayoutMode, state.directoryScrollToken) {
        val token = state.directoryScrollToken
        if (
            libraryLayoutMode == LibraryLayoutMode.Directory &&
            token > consumedDirectoryScrollToken
        ) {
            consumedDirectoryScrollToken = token
            directoryListState.scrollToItem(0)
        }
    }

    LaunchedEffect(highlightedBookUri, libraryLayoutMode, currentDirectoryKey) {
        val restoreKey = pendingBookReturnDirectoryKey
        if (
            highlightedBookUri != null &&
            libraryLayoutMode == LibraryLayoutMode.Directory &&
            restoreKey == currentDirectoryKey
        ) {
            directoryListState.scrollToItem(
                index = pendingBookReturnDirectoryIndex,
                scrollOffset = pendingBookReturnDirectoryOffset
            )
            pendingBookReturnDirectoryKey = null
        }
    }

    LaunchedEffect(highlightedDirectoryFolderKey) {
        val highlightedKey = highlightedDirectoryFolderKey ?: return@LaunchedEffect
        delay(HighlightHoldMillis)
        if (highlightedDirectoryFolderKey == highlightedKey) {
            highlightedDirectoryFolderKey = null
        }
    }

    LaunchedEffect(highlightedBookUri) {
        if (highlightedBookUri == null) return@LaunchedEffect
        delay(HighlightHoldMillis)
        onHighlightedBookConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hitomi Manager",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        SourceScopeDropdown(
                            state = state,
                            onSourceScopeChange = onSourceScopeChange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = {
                                sortMenuExpanded = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sort,
                                contentDescription = "排序"
                            )
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = {
                                sortMenuExpanded = false
                            }
                        ) {
                            BookSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (state.bookSortMode == mode) {
                                                "✓ ${bookSortModeLabel(mode)}"
                                            } else {
                                                bookSortModeLabel(mode)
                                            }
                                        )
                                    },
                                    onClick = {
                                        sortMenuExpanded = false
                                        onBookSortModeChange(mode)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleLibraryLayoutMode
                    ) {
                        Icon(
                            imageVector = when (libraryLayoutMode) {
                                LibraryLayoutMode.List -> Icons.Filled.GridView
                                LibraryLayoutMode.Grid -> Icons.Filled.FolderOpen
                                LibraryLayoutMode.Directory -> Icons.Filled.ViewList
                            },
                            contentDescription = when (libraryLayoutMode) {
                                LibraryLayoutMode.List -> "切换到网格布局"
                                LibraryLayoutMode.Grid -> "切换到目录布局"
                                LibraryLayoutMode.Directory -> "切换到列表布局"
                            }
                        )
                    }

                    IconButton(
                        onClick = onRescan,
                        enabled = state.librarySources.isNotEmpty() && !state.isScanning
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "重新扫描"
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        },
        bottomBar = {
            CompactBottomBar(
                selectedTab = state.homeTab,
                onTabSelected = onHomeTabChange
            )
        }
    ) { paddingValues ->
        if (state.librarySources.isEmpty()) {
            NoFolderContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onOpenSettings = onOpenSettings
            )
            return@Scaffold
        }

        when (state.homeTab) {
            HomeTab.Library -> {
                LibraryContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = state,
                    showTagNamespacePrefix = showTagNamespacePrefix,
                    showRematchButtonInLibrary = showRematchButtonInLibrary,
                    showGridCoverPlayButton = showGridCoverPlayButton,
                    libraryLayoutMode = libraryLayoutMode,
                    libraryGridColumns = libraryGridColumns,
                    listState = libraryListState,
                    directoryListState = directoryListState,
                    gridState = libraryGridState,
                    onClearTagFilters = onClearTagFilters,
                    onClearSearch = onClearSearch,
                    onOpenBook = ::openBookWithPositionMemory,
                    onReadBook = onReadBook,
                    onMatchBook = onMatchBook,
                    onOpenDirectory = ::openDirectoryWithPositionMemory,
                    onDirectoryUp = ::navigateDirectoryUpWithPositionRestore,
                    highlightedDirectoryFolderKey = highlightedDirectoryFolderKey,
                    highlightedBookUri = highlightedBookUri
                )
            }

            HomeTab.Tags -> {
                TagFilterContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = state,
                    showTagNamespacePrefix = showTagNamespacePrefix,
                    distinguishGenderTags = distinguishGenderTags,
                    listState = tagListState,
                    onToggleTag = onToggleTag,
                    onClearTagFilters = onClearTagFilters,
                    onTagSortModeChange = onTagSortModeChange,
                    onTagFilterTabChange = onTagFilterTabChange
                )
            }

            HomeTab.Search -> {
                SearchContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = state,
                    showRematchButtonInLibrary = showRematchButtonInLibrary,
                    showGridCoverPlayButton = showGridCoverPlayButton,
                    libraryLayoutMode = libraryLayoutMode,
                    libraryGridColumns = libraryGridColumns,
                    listState = searchListState,
                    gridState = searchGridState,
                    onSearchQueryChange = onSearchQueryChange,
                    onClearSearch = onClearSearch,
                    onOpenBook = ::openBookWithPositionMemory,
                    onReadBook = onReadBook,
                    onMatchBook = onMatchBook,
                    highlightedBookUri = highlightedBookUri
                )
            }

            HomeTab.Tasks -> {
                MatchTaskContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = state,
                    listState = taskListState,
                    onFilterChange = onMatchTaskFilterChange,
                    onOpenTask = onOpenMatchTask,
                    onOpenUnqueuedBook = onMatchBook,
                    onSkipTask = onSkipMatchTask,
                    onCancelSkippedTask = onCancelSkippedMatchTask,
                    onSkipUnqueuedBook = onSkipUnqueuedBook,
                    onRetryTask = onRetryMatchTask,
                    onRetryFailedTasks = onRetryFailedMatchTasks,
                    onRetryFailedExceptNoCandidates = onRetryFailedExceptNoCandidates,
                    onStartBatchMatch = onStartBatchMatch
                )
            }
        }
    }
}

@Composable
private fun SourceScopeDropdown(
    state: LibraryUiState,
    onSourceScopeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    val current = state.sourceScopes.firstOrNull {
        it.key == state.selectedSourceScopeKey
    }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = current?.label ?: "全部",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "选择来源"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            state.sourceScopes.forEach { scope ->
                DropdownMenuItem(
                    text = { Text(scope.label) },
                    onClick = {
                        expanded = false
                        onSourceScopeChange(scope.key)
                    }
                )
            }
        }
    }
}

@Composable
private fun NoFolderContent(
    modifier: Modifier,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "还没有设置本地漫画目录。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("前往设置")
        }
    }
}

@Composable
private fun ResultCountText(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun LibraryContent(
    modifier: Modifier,
    state: LibraryUiState,
    showTagNamespacePrefix: Boolean,
    showRematchButtonInLibrary: Boolean,
    showGridCoverPlayButton: Boolean,
    libraryLayoutMode: LibraryLayoutMode,
    libraryGridColumns: Int,
    listState: LazyListState,
    directoryListState: LazyListState,
    gridState: LazyGridState,
    onClearTagFilters: () -> Unit,
    onClearSearch: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onReadBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit,
    onOpenDirectory: (LibraryFolderNode) -> Unit,
    onDirectoryUp: () -> Unit,
    highlightedDirectoryFolderKey: String?,
    highlightedBookUri: String?
) {
    Column(
        modifier = modifier
    ) {
        if (state.selectedTagKeys.isNotEmpty()) {
            ActiveFilterBanner(
                text = "已选择 ${state.selectedTagKeys.size} 个标签",
                onClear = onClearTagFilters
            )
        }

        if (state.searchQuery.isNotBlank()) {
            ActiveFilterBanner(
                text = "搜索：${state.searchQuery}",
                onClear = onClearSearch
            )
        }

        if (state.isScanning) {
            ScanProgressCard(
                done = state.scanDone,
                total = state.scanTotal,
                currentName = state.scanCurrentName
            )
        }

        if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        ResultCountText(
            text = "共 ${state.books.size} 本"
        )

        if (libraryLayoutMode == LibraryLayoutMode.Directory) {
            DirectoryContent(
                state = state,
                listState = directoryListState,
                showRematchButtonInLibrary = showRematchButtonInLibrary,
                onOpenDirectory = onOpenDirectory,
                onDirectoryUp = onDirectoryUp,
                highlightedFolderKey = highlightedDirectoryFolderKey,
                highlightedBookUri = highlightedBookUri,
                onOpenBook = onOpenBook,
                onMatchBook = onMatchBook
            )
        } else if (!state.isScanning && state.books.isEmpty()) {
            EmptyHint(
                text = "没有符合条件的作品。"
            )
        } else {
            BookShelfContent(
                books = state.books,
                layoutMode = libraryLayoutMode,
                gridColumns = libraryGridColumns,
                listState = listState,
                gridState = gridState,
                showRematchButtonInLibrary = showRematchButtonInLibrary,
                showGridCoverPlayButton = showGridCoverPlayButton,
                highlightedBookUri = highlightedBookUri,
                onOpenBook = onOpenBook,
                onReadBook = onReadBook,
                onMatchBook = onMatchBook
            )
        }
    }
}

@Composable
private fun DirectoryContent(
    state: LibraryUiState,
    listState: LazyListState,
    showRematchButtonInLibrary: Boolean,
    onOpenDirectory: (LibraryFolderNode) -> Unit,
    onDirectoryUp: () -> Unit,
    highlightedFolderKey: String?,
    highlightedBookUri: String?,
    onOpenBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit
) {
    val books = remember(
        state.currentDirectorySourceId,
        state.books,
        state.directoryBooks
    ) {
        if (state.currentDirectorySourceId == null) {
            state.books.filter { it.parentPath.isNullOrBlank() }
        } else {
            state.directoryBooks
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDirectoryUp,
                enabled = canNavigateDirectoryUp(state)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "上一级"
                )
            }

            Text(
                text = directoryTitle(state),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(
                items = state.directoryFolders,
                key = { folder -> "folder:${folder.sourceId}:${folder.path}" }
            ) { folder ->
                val folderKey = folderItemKey(folder)
                val backgroundColor = animatedHighlightColor(
                    highlighted = highlightedFolderKey == folderKey
                )
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = backgroundColor
                    ),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = folder.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = folder.sourceName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDirectory(folder) }
                )
            }

            items(
                items = books,
                key = { book -> book.uriString }
            ) { book ->
                BookListItem(
                    book = book,
                    showRematchButtonInLibrary = showRematchButtonInLibrary,
                    highlighted = highlightedBookUri == book.uriString,
                    onClick = { onOpenBook(book) },
                    onMatchClick = { onMatchBook(book) }
                )
            }

            if (state.directoryFolders.isEmpty() && books.isEmpty()) {
                item {
                    EmptyHint("当前目录没有作品。")
                }
            }
        }
    }
}

private fun directoryStateKey(state: LibraryUiState): String {
    return directoryStateKey(
        selectedSourceScopeKey = state.selectedSourceScopeKey,
        sourceId = state.currentDirectorySourceId,
        path = state.currentDirectoryPath
    )
}

private fun directoryStateKey(
    selectedSourceScopeKey: String,
    sourceId: String?,
    path: String
): String {
    return "$selectedSourceScopeKey:${sourceId.orEmpty()}:$path"
}

private fun parentDirectoryStateKey(state: LibraryUiState): String? {
    val sourceId = state.currentDirectorySourceId ?: return null
    val path = state.currentDirectoryPath
    return if (path.isBlank()) {
        val selectedScope = state.sourceScopes.firstOrNull {
            it.key == state.selectedSourceScopeKey
        }
        if (selectedScope?.sourceIds?.singleOrNull() == sourceId) {
            null
        } else {
            directoryStateKey(
                selectedSourceScopeKey = state.selectedSourceScopeKey,
                sourceId = null,
                path = ""
            )
        }
    } else {
        directoryStateKey(
            selectedSourceScopeKey = state.selectedSourceScopeKey,
            sourceId = sourceId,
            path = path.substringBeforeLast('/', missingDelimiterValue = "")
        )
    }
}

private fun currentDirectoryFolderKey(state: LibraryUiState): String? {
    val sourceId = state.currentDirectorySourceId ?: return null
    return "folder:$sourceId:${state.currentDirectoryPath}"
}

private fun folderItemKey(folder: LibraryFolderNode): String {
    return "folder:${folder.sourceId}:${folder.path}"
}

private fun directoryTitle(state: LibraryUiState): String {
    val sourceName = state.librarySources
        .firstOrNull { it.id == state.currentDirectorySourceId }
        ?.name
    val path = state.currentDirectoryPath

    return when {
        sourceName == null -> "来源根目录"
        path.isBlank() -> sourceName
        else -> "$sourceName / $path"
    }
}

private fun canNavigateDirectoryUp(state: LibraryUiState): Boolean {
    val sourceId = state.currentDirectorySourceId ?: return false
    if (state.currentDirectoryPath.isNotBlank()) return true
    val selectedScope = state.sourceScopes.firstOrNull {
        it.key == state.selectedSourceScopeKey
    }
    return selectedScope?.sourceIds?.singleOrNull() != sourceId
}

@Composable
private fun SearchContent(
    modifier: Modifier,
    state: LibraryUiState,
    showRematchButtonInLibrary: Boolean,
    showGridCoverPlayButton: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onReadBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit,
    libraryLayoutMode: LibraryLayoutMode,
    libraryGridColumns: Int,
    highlightedBookUri: String?
) {
    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                label = {
                    Text("搜索标题、文件名或 Gallery ID")
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            TextButton(
                onClick = onClearSearch
            ) {
                Text("清除")
            }
        }

        ResultCountText(
            text = "共 ${state.books.size} 本"
        )

        BookShelfContent(
            books = state.books,
            layoutMode = libraryLayoutMode,
            gridColumns = libraryGridColumns,
            listState = listState,
            gridState = gridState,
            showRematchButtonInLibrary = showRematchButtonInLibrary,
            showGridCoverPlayButton = showGridCoverPlayButton,
            highlightedBookUri = highlightedBookUri,
            onOpenBook = onOpenBook,
            onReadBook = onReadBook,
            onMatchBook = onMatchBook
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFilterContent(
    modifier: Modifier,
    state: LibraryUiState,
    showTagNamespacePrefix: Boolean,
    distinguishGenderTags: Boolean,
    listState: LazyListState,
    onToggleTag: (TagCountItem) -> Unit,
    onClearTagFilters: () -> Unit,
    onTagSortModeChange: (TagSortMode) -> Unit,
    onTagFilterTabChange: (TagFilterTab) -> Unit
) {
    val visibleTags = remember(
        state.tagItems,
        state.tagFilterTab
    ) {
        state.tagItems.filter { tag ->
            tagBelongsToFilterTab(
                namespace = tag.namespace,
                tab = state.tagFilterTab
            )
        }
    }
    var renderedTagLimit by remember(
        state.tagFilterTab,
        state.tagSortMode,
        showTagNamespacePrefix
    ) {
        mutableStateOf(0)
    }
    var isPreparingTags by remember(
        state.tagFilterTab,
        state.tagSortMode,
        showTagNamespacePrefix
    ) {
        mutableStateOf(false)
    }
    val renderedTags = remember(
        visibleTags,
        renderedTagLimit
    ) {
        visibleTags.take(renderedTagLimit.coerceAtMost(visibleTags.size))
    }

    LaunchedEffect(
        visibleTags,
        state.tagFilterTab,
        state.tagSortMode,
        showTagNamespacePrefix
    ) {
        renderedTagLimit = 0
        isPreparingTags = visibleTags.isNotEmpty()

        if (visibleTags.isEmpty()) {
            isPreparingTags = false
            return@LaunchedEffect
        }

        delay(80)
        renderedTagLimit = TagInitialRenderCount.coerceAtMost(visibleTags.size)
        isPreparingTags = false

        while (renderedTagLimit < visibleTags.size) {
            delay(40)
            renderedTagLimit = (renderedTagLimit + TagRenderBatchSize)
                .coerceAtMost(visibleTags.size)
        }
    }

    Column(
        modifier = modifier
    ) {
        TabRow(
            selectedTabIndex = state.tagFilterTab.ordinal
        ) {
            TagFilterTab.values().forEach { tab ->
                Tab(
                    selected = state.tagFilterTab == tab,
                    onClick = {
                        onTagFilterTabChange(tab)
                    },
                    text = {
                        Text(tagFilterTabLabel(tab))
                    }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactSortChip(
                        selected = state.tagSortMode == TagSortMode.CountDesc,
                        text = "出现次数",
                        onClick = {
                            onTagSortModeChange(TagSortMode.CountDesc)
                        }
                    )

                    CompactSortChip(
                        selected = state.tagSortMode == TagSortMode.NameAsc,
                        text = "首字母",
                        onClick = {
                            onTagSortModeChange(TagSortMode.NameAsc)
                        }
                    )

                    TextButton(
                        onClick = onClearTagFilters,
                        enabled = state.selectedTagKeys.isNotEmpty()
                    ) {
                        Text("清除")
                    }
                }
            }

            if (state.selectedTagKeys.isNotEmpty()) {
                item {
                    ActiveFilterBanner(
                        text = "已选择 ${state.selectedTagKeys.size} 个筛选条件",
                        onClear = onClearTagFilters
                    )
                }
            }

            item {
                Text(
                    text = "${tagFilterTabLabel(state.tagFilterTab)}（${visibleTags.size}）",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (visibleTags.isEmpty()) {
                item {
                    EmptyHint("暂无可筛选标签。")
                }
            } else if (isPreparingTags && renderedTags.isEmpty()) {
                item {
                    LoadingTagsItem(
                        totalCount = visibleTags.size
                    )
                }
            } else {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CompactTagHorizontalGap),
                        verticalArrangement = Arrangement.spacedBy(CompactTagVerticalGap)
                    ) {
                        renderedTags.forEach { tag ->
                            CompactFilterTagChip(
                                tag = tag,
                                selected = tag.tagKey in state.selectedTagKeys,
                                showTagNamespacePrefix = showTagNamespacePrefix,
                                distinguishGenderTags = distinguishGenderTags,
                                onToggleTag = onToggleTag
                            )
                        }
                    }
                }

                if (renderedTags.size < visibleTags.size) {
                    item {
                        LoadingMoreTagsItem(
                            shownCount = renderedTags.size,
                            totalCount = visibleTags.size
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingTagsItem(
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )

        Text(
            text = "正在加载筛选标签（$totalCount）…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun LoadingMoreTagsItem(
    shownCount: Int,
    totalCount: Int
) {
    Text(
        text = "正在继续加载标签…（$shownCount/$totalCount）",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun CompactSortChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides 0.dp
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
private fun ActiveFilterBanner(
    text: String,
    onClear: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text)
        },
        trailingContent = {
            TextButton(
                onClick = onClear
            ) {
                Text("清除")
            }
        }
    )
}

@Composable
private fun BookList(
    books: List<BookItem>,
    listState: LazyListState,
    showRematchButtonInLibrary: Boolean,
    highlightedBookUri: String?,
    onOpenBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit
) {
    if (books.isEmpty()) {
        EmptyHint("没有符合条件的作品。")
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(books) { book ->
            BookListItem(
                book = book,
                showRematchButtonInLibrary = showRematchButtonInLibrary,
                highlighted = highlightedBookUri == book.uriString,
                onClick = {
                    onOpenBook(book)
                },
                onMatchClick = {
                    onMatchBook(book)
                }
            )
        }
    }
}

@Composable
private fun BookListItem(
    book: BookItem,
    showRematchButtonInLibrary: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onMatchClick: () -> Unit
) {
    val containerColor = animatedHighlightColor(highlighted)

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = containerColor
        ),
        leadingContent = {
            val coverPath = book.coverFilePath

            if (coverPath != null) {
                AsyncImage(
                    model = coverImageModel(coverPath),
                    contentDescription = book.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(64.dp)
                        .height(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .height(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("无封面")
                }
            }
        },
        headlineContent = {
            Text(
                text = book.title ?: book.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val info = remember(
                book.sourceGalleryId,
                book.language,
                book.type,
                book.pageCount
            ) {
                if (book.sourceGalleryId != null) {
                    listOfNotNull(
                        book.language,
                        book.type,
                        book.pageCount?.let { "${it}p" },
                        "ID:${book.sourceGalleryId}"
                    ).joinToString(" · ")
                } else {
                    "未匹配 · zip / cbz"
                }
            }

            Text(
                text = info,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = if (book.sourceGalleryId == null || showRematchButtonInLibrary) {
            {
                TextButton(
                    onClick = onMatchClick
                ) {
                    Text(
                        text = if (book.sourceGalleryId == null) "匹配" else "重匹配"
                    )
                }
            }
        } else {
            null
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
    )
}

@Composable
private fun EmptyHint(
    text: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTagLabel(
    tag: TagCountItem,
    showNamespace: Boolean,
    distinguishGenderTags: Boolean
): String {
    val name = tag.translatedName ?: tag.name

    return when {
        // 命名空间前缀优先级最高，显示 [male]/[female] 等文字前缀
        showNamespace -> "[${tag.namespace}] $name"
        distinguishGenderTags && tag.namespace == "male" -> "♂ $name"
        distinguishGenderTags && tag.namespace == "female" -> "♀ $name"
        else -> name
    }
}

private fun namespaceTitle(namespace: String): String {
    return when (namespace) {
        "tag" -> "标签"
        "artist" -> "作者"
        "group" -> "社团"
        "series" -> "系列"
        "character" -> "角色"
        "language" -> "语言"
        "type" -> "类型"
        "female" -> "女性标签"
        "male" -> "男性标签"
        else -> namespace
    }
}

private fun tagFilterTabLabel(
    tab: TagFilterTab
): String {
    return when (tab) {
        TagFilterTab.Tag -> "Tag"
        TagFilterTab.Artist -> "作者"
        TagFilterTab.Group -> "社团"
        TagFilterTab.Series -> "系列"
        TagFilterTab.Character -> "角色"
        TagFilterTab.Language -> "语言"
        TagFilterTab.Type -> "类型"
    }
}

private fun tagBelongsToFilterTab(
    namespace: String,
    tab: TagFilterTab
): Boolean {
    return when (tab) {
        TagFilterTab.Artist -> namespace == "artist"
        TagFilterTab.Group -> namespace == "group"
        TagFilterTab.Series -> namespace == "series"
        TagFilterTab.Character -> namespace == "character"
        TagFilterTab.Language -> namespace == "language"
        TagFilterTab.Type -> namespace == "type"

        TagFilterTab.Tag -> namespace !in setOf(
            "artist",
            "group",
            "series",
            "character",
            "language",
            "type"
        )
    }
}

@Composable
private fun CompactBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactBottomBarItem(
                selected = selectedTab == HomeTab.Library,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "书架",
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = "书架",
                onClick = {
                onTabSelected(HomeTab.Library)
                },
                modifier = Modifier.weight(1f)
            )

            CompactBottomBarItem(
                selected = selectedTab == HomeTab.Tags,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.FilterAlt,
                        contentDescription = "筛选",
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = "筛选",
                onClick = {
                onTabSelected(HomeTab.Tags)
                },
                modifier = Modifier.weight(1f)
            )

            CompactBottomBarItem(
                selected = selectedTab == HomeTab.Search,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = "搜索",
                onClick = {
                onTabSelected(HomeTab.Search)
                },
                modifier = Modifier.weight(1f)
            )

            CompactBottomBarItem(
                selected = selectedTab == HomeTab.Tasks,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Assignment,
                        contentDescription = "任务",
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = "任务",
                onClick = {
                onTabSelected(HomeTab.Tasks)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactBottomBarItem(
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onClick) {
                detectTapGestures(
                    onTap = {
                        onClick()
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            icon()

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

private fun coverImageModel(
    coverPath: String
): Any {
    return when {
        coverPath.startsWith("content://", ignoreCase = true) ||
                coverPath.startsWith("file://", ignoreCase = true) -> {
            Uri.parse(coverPath)
        }

        else -> {
            File(coverPath)
        }
    }
}

@Composable
private fun MatchTaskContent(
    modifier: Modifier,
    state: LibraryUiState,
    listState: LazyListState,
    onFilterChange: (MatchTaskFilter) -> Unit,
    onOpenTask: (MatchTaskEntity) -> Unit,
    onOpenUnqueuedBook: (BookItem) -> Unit,
    onSkipTask: (MatchTaskEntity) -> Unit,
    onCancelSkippedTask: (MatchTaskEntity) -> Unit,
    onSkipUnqueuedBook: (BookItem) -> Unit,
    onRetryTask: (MatchTaskEntity) -> Unit,
    onRetryFailedTasks: () -> Unit,
    onRetryFailedExceptNoCandidates: () -> Unit,
    onStartBatchMatch: () -> Unit
) {
    var showRetryAllFailedDialog by remember {
        mutableStateOf(false)
    }

    var showRetryRecoverableFailedDialog by remember {
        mutableStateOf(false)
    }

    var showStartBatchMatchDialog by remember {
        mutableStateOf(false)
    }

    if (showRetryAllFailedDialog) {
        AlertDialog(
            onDismissRequest = {
                showRetryAllFailedDialog = false
            },
            title = {
                Text("确认重试全部失败任务？")
            },
            text = {
                Text("这会重新搜索当前书库中所有失败任务，包括“没有搜索到候选”的任务。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRetryAllFailedDialog = false
                        onRetryFailedTasks()
                    },
                    enabled = !state.isBatchMatching
                ) {
                    Text("确认重试")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRetryAllFailedDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showRetryRecoverableFailedDialog) {
        AlertDialog(
            onDismissRequest = {
                showRetryRecoverableFailedDialog = false
            },
            title = {
                Text("确认重试异常失败任务？")
            },
            text = {
                Text("这会重试失败任务中除“没有搜索到候选”之外的项目，适合处理网络中断、应用关闭、WebView 搜索失败等情况。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRetryRecoverableFailedDialog = false
                        onRetryFailedExceptNoCandidates()
                    },
                    enabled = !state.isBatchMatching
                ) {
                    Text("确认重试")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRetryRecoverableFailedDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showStartBatchMatchDialog) {
        AlertDialog(
            onDismissRequest = {
                showStartBatchMatchDialog = false
            },
            title = {
                Text("确认匹配未匹配作品？")
            },
            text = {
                Text("这会为当前书库中尚未绑定元数据、且还没有进入任务队列的作品创建匹配任务。逻辑与设置页的批量匹配一致。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStartBatchMatchDialog = false
                        onStartBatchMatch()
                    },
                    enabled = !state.isBatchMatching
                ) {
                    Text("开始匹配")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStartBatchMatchDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TaskFilterRow(
                    selected = state.taskFilter,
                    counts = state.matchTaskFilterCounts,
                    isBatchMatching = state.isBatchMatching,
                    onFilterChange = onFilterChange,
                    onRetryFailedTasks = {
                        showRetryAllFailedDialog = true
                    },
                    onRetryFailedExceptNoCandidates = {
                        showRetryRecoverableFailedDialog = true
                    },
                    onStartBatchMatch = {
                        showStartBatchMatchDialog = true
                    }
                )
            }

            if (state.isBatchMatching) {
                item {
                    ListItem(
                        headlineContent = {
                            Text("批量任务正在运行")
                        },
                        supportingContent = {
                            Text("任务状态会陆续更新。")
                        }
                    )
                }
            }

            if (state.taskFilter == MatchTaskFilter.Unqueued) {
                if (state.unqueuedUnmatchedBooks.isEmpty()) {
                    item {
                        EmptyHint("暂无未进入任务队列的未匹配作品。")
                    }
                } else {
                    items(state.unqueuedUnmatchedBooks) { book ->
                        UnqueuedBookItem(
                            book = book,
                            onClick = {
                                onOpenUnqueuedBook(book)
                            },
                            onMatch = {
                                onOpenUnqueuedBook(book)
                            },
                            onSkip = {
                                onSkipUnqueuedBook(book)
                            }
                        )
                    }
                }
            } else {
                if (state.matchTasks.isEmpty()) {
                    item {
                        EmptyHint("暂无匹配任务。")
                    }
                } else {
                    items(state.matchTasks) { task ->
                        MatchTaskItem(
                            task = task,
                            onClick = {
                                onOpenTask(task)
                            },
                            onSkip = if (state.taskFilter == MatchTaskFilter.Failed) {
                                {
                                    onSkipTask(task)
                                }
                            } else {
                                null
                            },
                            onRetry = if (state.taskFilter == MatchTaskFilter.Failed) {
                                {
                                    onRetryTask(task)
                                }
                            } else {
                                null
                            },
                            onCancelSkipped = if (state.taskFilter == MatchTaskFilter.Skipped) {
                                {
                                    onCancelSkippedTask(task)
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnqueuedBookItem(
    book: BookItem,
    onClick: () -> Unit,
    onMatch: () -> Unit,
    onSkip: () -> Unit
) {
    ListItem(
        leadingContent = {
            val coverPath = book.coverFilePath

            if (coverPath != null) {
                AsyncImage(
                    model = coverImageModel(coverPath),
                    contentDescription = book.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(52.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("无")
                }
            }
        },
        headlineContent = {
            Text(
                text = book.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = "未匹配 · 未进入任务队列",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TaskActionButton(
                    onClick = onMatch
                ) {
                    TaskActionIcon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "匹配"
                    )
                }

                TaskActionButton(
                    onClick = onSkip
                ) {
                    TaskActionIcon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "跳过"
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun TaskActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            content()
        }
    }
}

@Composable
private fun TaskActionIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(20.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskFilterRow(
    selected: MatchTaskFilter,
    counts: Map<MatchTaskFilter, Int>,
    isBatchMatching: Boolean,
    onFilterChange: (MatchTaskFilter) -> Unit,
    onRetryFailedTasks: () -> Unit,
    onRetryFailedExceptNoCandidates: () -> Unit,
    onStartBatchMatch: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            MatchTaskFilter.entries.forEach { filter ->
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides 0.dp
                ) {
                    FilterChip(
                        selected = selected == filter,
                        onClick = {
                            onFilterChange(filter)
                        },
                        label = {
                            Text(
                                text = "${matchTaskFilterLabel(filter)}(${counts[filter] ?: 0})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                FilterChip(
                    selected = false,
                    onClick = onStartBatchMatch,
                    enabled = !isBatchMatching,
                    label = {
                        Text(
                            text = if (isBatchMatching) "匹配中" else "匹配未匹配",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(28.dp)
                )
            }

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                FilterChip(
                    selected = false,
                    onClick = onRetryFailedExceptNoCandidates,
                    enabled = !isBatchMatching,
                    label = {
                        Text(
                            text = if (isBatchMatching) "重试中" else "重试异常失败",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(28.dp)
                )
            }

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                FilterChip(
                    selected = false,
                    onClick = onRetryFailedTasks,
                    enabled = !isBatchMatching,
                    label = {
                        Text(
                            text = if (isBatchMatching) "重试中" else "重试全部失败",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}

@Composable
private fun MatchTaskItem(
    task: MatchTaskEntity,
    onClick: () -> Unit,
    onSkip: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onCancelSkipped: (() -> Unit)?
) {
    ListItem(
        leadingContent = {
            val coverPath = task.coverFilePath

            if (coverPath != null) {
                AsyncImage(
                    model = coverImageModel(coverPath),
                    contentDescription = task.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(52.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("无")
                }
            }
        },
        headlineContent = {
            Text(
                text = task.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val line1 = remember(
                task.status,
                task.localPageCount,
                task.candidateCount
            ) {
                listOfNotNull(
                    "状态：${matchTaskStatusLabel(task.status)}",
                    task.localPageCount?.let { "本地 ${it}p" },
                    "候选 ${task.candidateCount}"
                ).joinToString(" · ")
            }

            val line2 = remember(
                task.matchedGalleryId,
                task.errorMessage,
                task.query
            ) {
                when {
                    task.matchedGalleryId != null -> {
                        "已匹配 ID：${task.matchedGalleryId}"
                    }

                    task.errorMessage != null -> {
                        task.errorMessage
                    }

                    else -> {
                        "搜索词：${task.query}"
                    }
                }
            }

            Column {
                Text(
                    text = line1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = line2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = when {
            onRetry != null || onSkip != null -> {
                {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (onRetry != null) {
                            TaskActionButton(
                                onClick = onRetry
                            ) {
                                TaskActionIcon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "重试"
                                )
                            }
                        }

                        if (onSkip != null) {
                            TaskActionButton(
                                onClick = onSkip
                            ) {
                                TaskActionIcon(
                                    imageVector = Icons.Filled.FastForward,
                                    contentDescription = "跳过"
                                )
                            }
                        }
                    }
                }
            }

            onCancelSkipped != null -> {
                {
                    TaskActionButton(
                        onClick = onCancelSkipped
                    ) {
                        TaskActionIcon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "取消跳过"
                        )
                    }
                }
            }

            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

private fun matchTaskFilterLabel(
    filter: MatchTaskFilter
): String {
    return when (filter) {
        MatchTaskFilter.All -> "全部"
        MatchTaskFilter.Running -> "进行中"
        MatchTaskFilter.Success -> "成功"
        MatchTaskFilter.NeedReview -> "需复核"
        MatchTaskFilter.Failed -> "失败"
        MatchTaskFilter.Skipped -> "跳过"
        MatchTaskFilter.Unqueued -> "未匹配"
    }
}

private fun matchTaskStatusLabel(
    status: String
): String {
    return when (status) {
        MatchTaskStatus.Pending -> "等待中"
        MatchTaskStatus.Running -> "匹配中"
        MatchTaskStatus.AutoMatched -> "成功"
        MatchTaskStatus.NeedReview -> "需复核"
        MatchTaskStatus.Failed -> "失败"
        MatchTaskStatus.Skipped -> "跳过"
        else -> status
    }
}

private fun bookSortModeLabel(
    mode: BookSortMode
): String {
    return when (mode) {
        BookSortMode.NameAsc -> "名称 A-Z"
        BookSortMode.NameDesc -> "名称 Z-A"
        BookSortMode.FileTimeDesc -> "修改时间 新到旧"
        BookSortMode.FileTimeAsc -> "修改时间 旧到新"
        BookSortMode.PageCountDesc -> "页数 多到少"
        BookSortMode.PageCountAsc -> "页数 少到多"
    }
}

@Composable
private fun CompactFilterTagChip(
    tag: TagCountItem,
    selected: Boolean,
    showTagNamespacePrefix: Boolean,
    distinguishGenderTags: Boolean,
    onToggleTag: (TagCountItem) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides 0.dp
    ) {
        FilterChip(
            selected = selected,
            onClick = {
                onToggleTag(tag)
            },
            label = {
                Text(
                    text = "${formatTagLabel(tag, showTagNamespacePrefix, distinguishGenderTags)} (${tag.bookCount})",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = modifier.height(CompactTagChipHeight)
        )
    }
}

@Composable
private fun BookShelfContent(
    books: List<BookItem>,
    layoutMode: LibraryLayoutMode,
    gridColumns: Int,
    listState: LazyListState,
    gridState: LazyGridState,
    showRematchButtonInLibrary: Boolean,
    showGridCoverPlayButton: Boolean,
    highlightedBookUri: String?,
    onOpenBook: (BookItem) -> Unit,
    onReadBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit
) {
    if (books.isEmpty()) {
        EmptyHint("没有符合条件的作品。")
        return
    }

    when (layoutMode) {
        LibraryLayoutMode.List,
        LibraryLayoutMode.Directory -> {
            BookList(
                books = books,
                listState = listState,
                showRematchButtonInLibrary = showRematchButtonInLibrary,
                highlightedBookUri = highlightedBookUri,
                onOpenBook = onOpenBook,
                onMatchBook = onMatchBook
            )
        }

        LibraryLayoutMode.Grid -> {
            BookGrid(
                books = books,
                gridState = gridState,
                gridColumns = gridColumns,
                highlightedBookUri = highlightedBookUri,
                showGridCoverPlayButton = showGridCoverPlayButton,
                onOpenBook = onOpenBook,
                onReadBook = onReadBook
            )
        }
    }
}

@Composable
private fun BookGrid(
    books: List<BookItem>,
    gridState: LazyGridState,
    gridColumns: Int,
    highlightedBookUri: String?,
    showGridCoverPlayButton: Boolean,
    onOpenBook: (BookItem) -> Unit,
    onReadBook: (BookItem) -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(gridColumns.coerceIn(2, 6)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = books,
            key = { it.uriString }
        ) { book ->
            GridBookCoverItem(
                book = book,
                highlighted = highlightedBookUri == book.uriString,
                showPlayButton = showGridCoverPlayButton,
                onClick = {
                    onOpenBook(book)
                },
                onPlayClick = {
                    onReadBook(book)
                }
            )
        }
    }
}

@Composable
private fun GridBookCoverItem(
    book: BookItem,
    highlighted: Boolean,
    showPlayButton: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val overlayColor = animatedHighlightOverlayColor(highlighted)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        val coverPath = book.coverFilePath

        if (coverPath != null) {
            AsyncImage(
                model = coverImageModel(coverPath),
                contentDescription = book.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "无封面",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(overlayColor)
        )

        if (showPlayButton) {
            Surface(
                onClick = onPlayClick,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "直接阅读",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun animatedHighlightColor(
    highlighted: Boolean
): Color {
    val targetColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = HighlightFadeMillis),
        label = "itemHighlight"
    )
    return color
}

@Composable
private fun animatedHighlightOverlayColor(
    highlighted: Boolean
): Color {
    val targetColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
    } else {
        Color.Transparent
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = HighlightFadeMillis),
        label = "itemHighlightOverlay"
    )
    return color
}

@Composable
private fun ScanProgressCard(
    done: Int,
    total: Int,
    currentName: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (total > 0) {
                "正在扫描并生成封面：$done / $total"
            } else {
                "正在扫描压缩包..."
            },
            style = MaterialTheme.typography.bodyMedium
        )

        if (total > 0) {
            LinearProgressIndicator(
                progress = {
                    done.toFloat() / total.toFloat()
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!currentName.isNullOrBlank()) {
            Text(
                text = currentName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val CompactTagChipHeight = 28.dp
private val CompactTagHorizontalGap = 6.dp
private val CompactTagVerticalGap = 3.dp
private const val TagInitialRenderCount = 120
private const val TagRenderBatchSize = 160
