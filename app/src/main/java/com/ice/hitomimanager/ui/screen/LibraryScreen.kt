package com.ice.hitomimanager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ice.hitomimanager.LibraryUiState
import com.ice.hitomimanager.data.model.BookItem
import com.ice.hitomimanager.data.model.HomeTab
import com.ice.hitomimanager.data.model.TagCountItem
import com.ice.hitomimanager.data.model.TagSortMode
import java.io.File
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.ice.hitomimanager.data.model.MatchTaskFilter
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ice.hitomimanager.data.model.MatchTaskStatus
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    showTagNamespacePrefix: Boolean,
    onHomeTabChange: (HomeTab) -> Unit,
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
    onRetryFailedMatchTasks: () -> Unit,
    showRematchButtonInLibrary: Boolean,
    onRetryFailedExceptNoCandidates: () -> Unit,
    onStartBatchMatch: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Hitomi Manager")
                },
                actions = {
                    IconButton(
                        onClick = onRescan,
                        enabled = state.folderUriString != null && !state.isScanning
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
        if (state.folderUriString == null) {
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
                    onClearTagFilters = onClearTagFilters,
                    onClearSearch = onClearSearch,
                    onOpenBook = onOpenBook,
                    onMatchBook = onMatchBook
                )
            }

            HomeTab.Tags -> {
                TagFilterContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = state,
                    showTagNamespacePrefix = showTagNamespacePrefix,
                    onToggleTag = onToggleTag,
                    onClearTagFilters = onClearTagFilters,
                    onTagSortModeChange = onTagSortModeChange
                )
            }

            HomeTab.Search -> {
                SearchContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = state,
                    showRematchButtonInLibrary = showRematchButtonInLibrary,
                    onSearchQueryChange = onSearchQueryChange,
                    onClearSearch = onClearSearch,
                    onOpenBook = onOpenBook,
                    onMatchBook = onMatchBook
                )
            }

            HomeTab.Tasks -> {
                MatchTaskContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = state,
                    onFilterChange = onMatchTaskFilterChange,
                    onOpenTask = onOpenMatchTask,
                    onRetryFailedTasks = onRetryFailedMatchTasks,
                    onRetryFailedExceptNoCandidates = onRetryFailedExceptNoCandidates,
                    onStartBatchMatch = onStartBatchMatch
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
private fun LibraryContent(
    modifier: Modifier,
    state: LibraryUiState,
    showTagNamespacePrefix: Boolean,
    showRematchButtonInLibrary: Boolean,
    onClearTagFilters: () -> Unit,
    onClearSearch: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text(
                    text = "正在扫描压缩包并生成封面...",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (!state.isScanning && state.books.isEmpty()) {
            EmptyHint(
                text = "没有符合条件的作品。"
            )
        } else {
            BookList(
                books = state.books,
                showRematchButtonInLibrary = showRematchButtonInLibrary,
                onOpenBook = onOpenBook,
                onMatchBook = onMatchBook
            )
        }
    }
}

@Composable
private fun SearchContent(
    modifier: Modifier,
    state: LibraryUiState,
    showRematchButtonInLibrary: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit
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

        BookList(
            books = state.books,
            showRematchButtonInLibrary = showRematchButtonInLibrary,
            onOpenBook = onOpenBook,
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
    onToggleTag: (TagCountItem) -> Unit,
    onClearTagFilters: () -> Unit,
    onTagSortModeChange: (TagSortMode) -> Unit
) {
    val grouped = state.tagItems.groupBy { it.namespace }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.tagSortMode == TagSortMode.CountDesc,
                    onClick = {
                        onTagSortModeChange(TagSortMode.CountDesc)
                    },
                    label = {
                        Text("按出现次数")
                    }
                )

                FilterChip(
                    selected = state.tagSortMode == TagSortMode.NameAsc,
                    onClick = {
                        onTagSortModeChange(TagSortMode.NameAsc)
                    },
                    label = {
                        Text("按首字母")
                    }
                )

                TextButton(
                    onClick = onClearTagFilters,
                    enabled = state.selectedTagKeys.isNotEmpty()
                ) {
                    Text("清除选择")
                }
            }
        }

        val namespaceOrder = listOf(
            "tag",
            "artist",
            "group",
            "series",
            "character",
            "female",
            "male"
        )

        namespaceOrder.forEach { namespace ->
            val tags = grouped[namespace].orEmpty()
            if (tags.isNotEmpty()) {
                item {
                    TagGroup(
                        title = namespaceTitle(namespace),
                        tags = tags,
                        selectedKeys = state.selectedTagKeys,
                        showTagNamespacePrefix = showTagNamespacePrefix,
                        onToggleTag = onToggleTag
                    )
                }
            }
        }

        val otherNamespaces = grouped.keys
            .filter { it !in namespaceOrder }
            .sorted()

        otherNamespaces.forEach { namespace ->
            val tags = grouped[namespace].orEmpty()
            if (tags.isNotEmpty()) {
                item {
                    TagGroup(
                        title = namespaceTitle(namespace),
                        tags = tags,
                        selectedKeys = state.selectedTagKeys,
                        showTagNamespacePrefix = showTagNamespacePrefix,
                        onToggleTag = onToggleTag
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(
    title: String,
    tags: List<TagCountItem>,
    selectedKeys: Set<String>,
    showTagNamespacePrefix: Boolean,
    onToggleTag: (TagCountItem) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CompactTagGroupGap)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CompactTagHorizontalGap),
            verticalArrangement = Arrangement.spacedBy(CompactTagVerticalGap)
        ) {
            tags.forEach { tag ->
                CompactFilterTagChip(
                    tag = tag,
                    selected = tag.tagKey in selectedKeys,
                    showTagNamespacePrefix = showTagNamespacePrefix,
                    onToggleTag = onToggleTag
                )
            }
        }
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
    showRematchButtonInLibrary: Boolean,
    onOpenBook: (BookItem) -> Unit,
    onMatchBook: (BookItem) -> Unit
) {
    if (books.isEmpty()) {
        EmptyHint("没有符合条件的作品。")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(books) { book ->
            BookListItem(
                book = book,
                showRematchButtonInLibrary = showRematchButtonInLibrary,
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
    onClick: () -> Unit,
    onMatchClick: () -> Unit
) {
    ListItem(
        leadingContent = {
            val coverPath = book.coverFilePath

            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
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
            val info = if (book.sourceGalleryId != null) {
                listOfNotNull(
                    book.language,
                    book.type,
                    book.pageCount?.let { "${it}p" },
                    "ID:${book.sourceGalleryId}"
                ).joinToString(" · ")
            } else {
                "未匹配 · zip / cbz"
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
    showNamespace: Boolean
): String {
    val name = tag.translatedName ?: tag.name

    return if (showNamespace) {
        "[${tag.namespace}] $name"
    } else {
        name
    }
}

private fun namespaceTitle(namespace: String): String {
    return when (namespace) {
        "tag" -> "标签"
        "artist" -> "作者"
        "group" -> "社团"
        "series" -> "系列"
        "character" -> "角色"
        "female" -> "女性标签"
        "male" -> "男性标签"
        else -> namespace
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
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactBottomBarItem(
                selected = selectedTab == HomeTab.Library,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "书架"
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
                        contentDescription = "筛选"
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
                        contentDescription = "搜索"
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
                        contentDescription = "任务"
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
            .clickable(onClick = onClick),
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

@Composable
private fun MatchTaskContent(
    modifier: Modifier,
    state: LibraryUiState,
    onFilterChange: (MatchTaskFilter) -> Unit,
    onOpenTask: (MatchTaskEntity) -> Unit,
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TaskFilterRow(
                    selected = state.taskFilter,
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
                        UnqueuedBookItem(book)
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
    book: BookItem
) {
    ListItem(
        leadingContent = {
            val coverPath = book.coverFilePath

            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
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
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskFilterRow(
    selected: MatchTaskFilter,
    isBatchMatching: Boolean,
    onFilterChange: (MatchTaskFilter) -> Unit,
    onRetryFailedTasks: () -> Unit,
    onRetryFailedExceptNoCandidates: () -> Unit,
    onStartBatchMatch: () -> Unit
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
                            text = matchTaskFilterLabel(filter),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(28.dp)
                )
            }
        }

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

@Composable
private fun MatchTaskItem(
    task: MatchTaskEntity,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = {
            val coverPath = task.coverFilePath

            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
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
            val line1 = listOfNotNull(
                "状态：${matchTaskStatusLabel(task.status)}",
                task.localPageCount?.let { "本地 ${it}p" },
                "候选 ${task.candidateCount}"
            ).joinToString(" · ")

            val line2 = when {
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

@Composable
private fun CompactFilterTagChip(
    tag: TagCountItem,
    selected: Boolean,
    showTagNamespacePrefix: Boolean,
    onToggleTag: (TagCountItem) -> Unit
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
                    text = "${formatTagLabel(tag, showTagNamespacePrefix)} (${tag.bookCount})",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.height(CompactTagChipHeight)
        )
    }
}

private val CompactTagChipHeight = 28.dp
private val CompactTagHorizontalGap = 6.dp
private val CompactTagVerticalGap = 3.dp
private val CompactTagGroupGap = 6.dp