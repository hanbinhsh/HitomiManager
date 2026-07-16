package com.ice.hitomimanager.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ice.hitomimanager.SettingsUiState
import com.ice.hitomimanager.data.model.LibraryLayoutMode
import com.ice.hitomimanager.data.model.LibrarySource
import com.ice.hitomimanager.data.model.LibrarySourceType
import com.ice.hitomimanager.data.model.RemoteArchiveReadMode
import com.ice.hitomimanager.data.model.RemoteCachePolicy
import com.ice.hitomimanager.data.model.RemoteIndexMode
import com.ice.hitomimanager.data.model.SettingsTab
import com.ice.hitomimanager.data.model.WebDavSourceForm
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Slider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    isBatchMatching: Boolean,
    onBack: () -> Unit,
    onSettingsTabChange: (SettingsTab) -> Unit,
    onFolderPicked: (Uri) -> Unit,
    onRenameSource: (String, String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onScanSource: (String) -> Unit,
    onSaveWebDavSource: (WebDavSourceForm) -> Unit,
    onTestWebDavSource: (WebDavSourceForm) -> Unit,
    onShowTagNamespacePrefixChange: (Boolean) -> Unit,
    onDistinguishGenderTagsChange: (Boolean) -> Unit,
    onRemoveUnderscoreInMatchTitleChange: (Boolean) -> Unit,
    onRemoveTrailingNumberSuffixInMatchTitleChange: (Boolean) -> Unit,
    onAutoMatchExactTitleChange: (Boolean) -> Unit,
    onAutoMatchUniqueSamePageChange: (Boolean) -> Unit,
    onAutoMatchSingleResultChange: (Boolean) -> Unit,
    onAutoMatchSamePageFirstChange: (Boolean) -> Unit,
    onAutoOpenNextReviewTaskChange: (Boolean) -> Unit,
    onStartBatchMatch: () -> Unit,
    onClearDatabase: () -> Unit,
    onExportDatabasePicked: (Uri) -> Unit,
    onImportDatabasePicked: (Uri) -> Unit,
    onCleanupMissingRecords: () -> Unit,
    onOpenTasks: () -> Unit,
    onShowRematchButtonInLibraryChange: (Boolean) -> Unit,
    onOpenBookDirectlyInReaderChange: (Boolean) -> Unit,
    onShowGridCoverPlayButtonChange: (Boolean) -> Unit,
    onLibraryLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onLibraryGridColumnsChange: (Int) -> Unit,
    onFilteredMatchLanguagesChange: (String) -> Unit,
    onMatchSearchTimeoutSecondsChange: (String) -> Unit,
    onBatchMatchThreadsChange: (String) -> Unit,
    onRemoteArchiveReadModeChange: (RemoteArchiveReadMode) -> Unit,
    onRemoteCachePolicyChange: (RemoteCachePolicy) -> Unit,
    onRemoteCacheLimitMbChange: (String) -> Unit,
    onRemoteRangeBlockSizeKbChange: (String) -> Unit,
    onAllowBatchRemoteFullDownloadChange: (Boolean) -> Unit,
    onClearRemoteArchiveCache: () -> Unit,
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                onFolderPicked(uri)
            }
        }
    )

    val databaseExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                onExportDatabasePicked(uri)
            }
        }
    )

    val databaseImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                onImportDatabasePicked(uri)
            }
        }
    )

    var showClearDatabaseDialog by remember {
        mutableStateOf(false)
    }
    var showCleanupMissingDialog by remember {
        mutableStateOf(false)
    }
    var showSourceTypeDialog by remember { mutableStateOf(false) }
    var webDavForm by remember { mutableStateOf<WebDavSourceForm?>(null) }

    if (showSourceTypeDialog) {
        AlertDialog(
            onDismissRequest = { showSourceTypeDialog = false },
            title = { Text("添加书库来源") },
            text = { Text("选择本地目录或 WebDAV 网络目录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSourceTypeDialog = false
                        folderPicker.launch(null)
                    }
                ) { Text("本地目录") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSourceTypeDialog = false
                        webDavForm = WebDavSourceForm()
                    }
                ) { Text("WebDAV") }
            }
        )
    }

    webDavForm?.let { form ->
        WebDavSourceDialog(
            form = form,
            isTesting = state.isTestingWebDav,
            testMessage = state.webDavMessage,
            onFormChange = { webDavForm = it },
            onTest = onTestWebDavSource,
            onSave = {
                onSaveWebDavSource(it)
                webDavForm = null
            },
            onDismiss = { webDavForm = null }
        )
    }

    if (showClearDatabaseDialog) {
        AlertDialog(
            onDismissRequest = {
                showClearDatabaseDialog = false
            },
            title = {
                Text("确认清空数据库？")
            },
            text = {
                Text("这会删除所有书籍记录、匹配信息、标签和任务记录，但不会删除手机上的压缩包文件。该操作不可撤销。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDatabaseDialog = false
                        onClearDatabase()
                    }
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearDatabaseDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showCleanupMissingDialog) {
        AlertDialog(
            onDismissRequest = {
                showCleanupMissingDialog = false
            },
            title = {
                Text("清理缺失记录？")
            },
            text = {
                Text("会删除当前来源范围最近成功扫描未见到的作品记录；在全部范围下，也会删除已解绑来源留下的缓存。不会删除本地文件。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanupMissingDialog = false
                        onCleanupMissingRecords()
                    }
                ) {
                    Text("确认清理")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCleanupMissingDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                title = {
                    Text("设置")
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(
                selectedTabIndex = state.settingsTab.ordinal
            ) {
                SettingsTab.entries.forEach { tab ->
                    Tab(
                        selected = state.settingsTab == tab,
                        onClick = {
                            onSettingsTabChange(tab)
                        },
                        text = {
                            Text(settingsTabLabel(tab))
                        }
                    )
                }
            }

            when (state.settingsTab) {
                SettingsTab.Directory -> {
                    GeneralSettingsContent(
                        state = state,
                        onPickFolder = {
                            showSourceTypeDialog = true
                        },
                        onRenameSource = onRenameSource,
                        onDeleteSource = onDeleteSource,
                        onScanSource = onScanSource,
                        onEditWebDav = { source ->
                            webDavForm = WebDavSourceForm(
                                editingSourceId = source.id,
                                name = source.name,
                                baseUrl = source.webDavBaseUrl.orEmpty(),
                                rootPath = source.webDavRootPath.orEmpty().ifBlank { "/" },
                                username = source.webDavUsername.orEmpty(),
                                allowInsecureTls = source.webDavAllowInsecureTls,
                                indexMode = source.remoteIndexMode,
                                connectTimeoutSecondsText = source.connectTimeoutSeconds.toString(),
                                readTimeoutSecondsText = source.readTimeoutSeconds.toString()
                            )
                        }
                    )
                }

                SettingsTab.Reading -> ReadingSettingsContent(
                    state = state,
                    onReadModeChange = onRemoteArchiveReadModeChange,
                    onCachePolicyChange = onRemoteCachePolicyChange,
                    onCacheLimitChange = onRemoteCacheLimitMbChange,
                    onBlockSizeChange = onRemoteRangeBlockSizeKbChange,
                    onAllowBatchFullDownloadChange = onAllowBatchRemoteFullDownloadChange,
                    onClearCache = onClearRemoteArchiveCache
                )

                SettingsTab.Database -> {
                    DatabaseSettingsContent(
                        onExportDatabaseClick = {
                            val timestamp = SimpleDateFormat(
                                "yyyyMMdd_HHmmss",
                                Locale.US
                            ).format(Date())
                            databaseExporter.launch("hitomi_manager_${timestamp}.db")
                        },
                        onImportDatabaseClick = {
                            databaseImporter.launch(
                                arrayOf(
                                    "application/octet-stream",
                                    "application/vnd.sqlite3",
                                    "*/*"
                                )
                            )
                        },
                        onClearDatabaseClick = {
                            showClearDatabaseDialog = true
                        },
                        onCleanupMissingClick = {
                            showCleanupMissingDialog = true
                        }
                    )
                }

                SettingsTab.Display -> {
                    DisplaySettingsContent(
                        state = state,
                        onShowTagNamespacePrefixChange = onShowTagNamespacePrefixChange,
                        onDistinguishGenderTagsChange = onDistinguishGenderTagsChange,
                        onShowRematchButtonInLibraryChange = onShowRematchButtonInLibraryChange,
                        onOpenBookDirectlyInReaderChange = onOpenBookDirectlyInReaderChange,
                        onShowGridCoverPlayButtonChange = onShowGridCoverPlayButtonChange,
                        onLibraryLayoutModeChange = onLibraryLayoutModeChange,
                        onLibraryGridColumnsChange = onLibraryGridColumnsChange
                    )
                }

                SettingsTab.Match -> {
                    MatchSettingsContent(
                        state = state,
                        isBatchMatching = isBatchMatching,
                        onRemoveUnderscoreInMatchTitleChange = onRemoveUnderscoreInMatchTitleChange,
                        onRemoveTrailingNumberSuffixInMatchTitleChange = onRemoveTrailingNumberSuffixInMatchTitleChange,
                        onAutoMatchExactTitleChange = onAutoMatchExactTitleChange,
                        onAutoMatchUniqueSamePageChange = onAutoMatchUniqueSamePageChange,
                        onAutoMatchSingleResultChange = onAutoMatchSingleResultChange,
                        onAutoMatchSamePageFirstChange = onAutoMatchSamePageFirstChange,
                        onAutoOpenNextReviewTaskChange = onAutoOpenNextReviewTaskChange,
                        onFilteredMatchLanguagesChange = onFilteredMatchLanguagesChange,
                        onMatchSearchTimeoutSecondsChange = onMatchSearchTimeoutSecondsChange,
                        onBatchMatchThreadsChange = onBatchMatchThreadsChange,
                        onStartBatchMatch = onStartBatchMatch,
                        onOpenTasks = onOpenTasks
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsContent(
    state: SettingsUiState,
    onPickFolder: () -> Unit,
    onRenameSource: (String, String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onScanSource: (String) -> Unit,
    onEditWebDav: (LibrarySource) -> Unit
) {
    var renamingSource by remember {
        mutableStateOf<LibrarySource?>(null)
    }
    var renameText by remember {
        mutableStateOf("")
    }

    renamingSource?.let { source ->
        AlertDialog(
            onDismissRequest = {
                renamingSource = null
            },
            title = {
                Text("重命名目录")
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = {
                        renameText = it
                    },
                    label = {
                        Text("目录名称")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameSource(source.id, renameText)
                        renamingSource = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renamingSource = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("书库目录")

        ListItem(
            headlineContent = {
                Text("添加书库来源")
            },
            supportingContent = {
                Text("可添加多个本地目录或 WebDAV 网络目录。")
            },
            trailingContent = {
                Button(
                    onClick = onPickFolder
                ) {
                    Text("添加")
                }
            }
        )

        if (state.librarySources.isEmpty()) {
            ListItem(
                headlineContent = {
                    Text("尚未添加目录")
                },
                supportingContent = {
                    Text("添加目录后会自动扫描，也可以在列表里单独重新扫描。")
                }
            )
        } else {
            state.librarySources.forEach { source ->
                SourceSettingsItem(
                    source = source,
                    onRename = {
                        renamingSource = source
                        renameText = source.name
                    },
                    onScan = {
                        onScanSource(source.id)
                    },
                    onDelete = {
                        onDeleteSource(source.id)
                    },
                    onEditWebDav = { onEditWebDav(source) }
                )
            }
        }

        state.webDavMessage?.let { message ->
            ListItem(
                headlineContent = { Text("WebDAV 状态") },
                supportingContent = { Text(message) }
            )
        }
    }
}

@Composable
private fun DatabaseSettingsContent(
    onExportDatabaseClick: () -> Unit,
    onImportDatabaseClick: () -> Unit,
    onClearDatabaseClick: () -> Unit,
    onCleanupMissingClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("数据库")

        ListItem(
            headlineContent = {
                Text("导出数据库")
            },
            supportingContent = {
                Text("将当前 Room SQLite 数据库保存为 .db 文件。")
            },
            trailingContent = {
                Button(
                    onClick = onExportDatabaseClick
                ) {
                    Text("导出")
                }
            }
        )

        ListItem(
            headlineContent = {
                Text("导入数据库")
            },
            supportingContent = {
                Text("从 .db 文件恢复数据库，会替换当前所有数据库内容。")
            },
            trailingContent = {
                Button(
                    onClick = onImportDatabaseClick
                ) {
                    Text("导入")
                }
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionTitle("危险操作")

        ListItem(
            headlineContent = {
                Text("清理缺失记录")
            },
            supportingContent = {
                Text("删除最近成功扫描未见到的数据库记录，不会删除本地文件。")
            },
            trailingContent = {
                Button(
                    onClick = onCleanupMissingClick
                ) {
                    Text("清理")
                }
            }
        )

        ListItem(
            headlineContent = {
                Text("清空数据库")
            },
            supportingContent = {
                Text("删除所有书籍记录、匹配信息、标签和任务记录，但不会删除本地压缩包文件。")
            },
            trailingContent = {
                Button(
                    onClick = onClearDatabaseClick
                ) {
                    Text("清空")
                }
            }
        )
    }
}

@Composable
private fun SourceSettingsItem(
    source: LibrarySource,
    onRename: () -> Unit,
    onScan: () -> Unit,
    onDelete: () -> Unit,
    onEditWebDav: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = source.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = if (source.type == LibrarySourceType.WebDav) {
                    buildString {
                        append(source.webDavBaseUrl.orEmpty())
                        append(source.webDavRootPath.orEmpty())
                        if (!source.webDavUsername.isNullOrBlank() && !source.hasStoredPassword) {
                            append("\n未保存密码，请编辑连接后重新输入")
                        }
                    }
                } else {
                    source.rootUriString
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onScan) {
                        Text("扫描")
                    }
                    TextButton(onClick = onRename) {
                        Text("重命名")
                    }
                }
                if (source.type == LibrarySourceType.WebDav) {
                    TextButton(onClick = onEditWebDav) { Text("编辑连接") }
                }
                TextButton(onClick = onDelete) {
                    Text("删除来源")
                }
            }
        }
    )
}

@Composable
private fun DisplaySettingsContent(
    state: SettingsUiState,
    onShowTagNamespacePrefixChange: (Boolean) -> Unit,
    onDistinguishGenderTagsChange: (Boolean) -> Unit,
    onShowRematchButtonInLibraryChange: (Boolean) -> Unit,
    onOpenBookDirectlyInReaderChange: (Boolean) -> Unit,
    onShowGridCoverPlayButtonChange: (Boolean) -> Unit,
    onLibraryLayoutModeChange: (LibraryLayoutMode) -> Unit,
    onLibraryGridColumnsChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("标签显示")

        ListItem(
            headlineContent = {
                Text("显示标签命名空间")
            },
            supportingContent = {
                Text("打开后显示 [artist]、[series]、[female] 等前缀；关闭后只显示标签名称。")
            },
            trailingContent = {
                Switch(
                    checked = state.showTagNamespacePrefix,
                    onCheckedChange = onShowTagNamespacePrefixChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("区分性别标签")
            },
            supportingContent = {
                Text("打开后 male/female 标签分开计数并显示 ♂/♀；关闭后同名标签合并计数、不区分性别。")
            },
            trailingContent = {
                Switch(
                    checked = state.distinguishGenderTags,
                    onCheckedChange = onDistinguishGenderTagsChange
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionTitle("主页显示")

        ListItem(
            headlineContent = {
                Text("布局模式")
            },
            supportingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.libraryLayoutMode == LibraryLayoutMode.List,
                        onClick = {
                            onLibraryLayoutModeChange(LibraryLayoutMode.List)
                        },
                        label = {
                            Text("列表")
                        }
                    )
                    FilterChip(
                        selected = state.libraryLayoutMode == LibraryLayoutMode.Grid,
                        onClick = {
                            onLibraryLayoutModeChange(LibraryLayoutMode.Grid)
                        },
                        label = {
                            Text("网格")
                        }
                    )
                    FilterChip(
                        selected = state.libraryLayoutMode == LibraryLayoutMode.Directory,
                        onClick = {
                            onLibraryLayoutModeChange(LibraryLayoutMode.Directory)
                        },
                        label = {
                            Text("目录")
                        }
                    )
                }
            }
        )

        ListItem(
            headlineContent = {
                Text("显示已匹配作品的重匹配按钮")
            },
            supportingContent = {
                Text("关闭后，主页列表中已匹配作品不会显示“重匹配”按钮；未匹配作品仍会显示“匹配”。")
            },
            trailingContent = {
                Switch(
                    checked = state.showRematchButtonInLibrary,
                    onCheckedChange = onShowRematchButtonInLibraryChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("点击作品直接阅读")
            },
            supportingContent = {
                Text("开启后，从主页点击漫画会直接进入阅读器；详情页仍可在阅读器第一页向右滑动打开。")
            },
            trailingContent = {
                Switch(
                    checked = state.openBookDirectlyInReader,
                    onCheckedChange = onOpenBookDirectlyInReaderChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("网格封面显示阅读按钮")
            },
            supportingContent = {
                Text("开启后，网格模式每个封面右下角显示阅读按钮，点击后直接进入阅读器。")
            },
            trailingContent = {
                Switch(
                    checked = state.showGridCoverPlayButton,
                    onCheckedChange = onShowGridCoverPlayButtonChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("网格布局列数")
            },
            supportingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2, 3, 4, 5, 6).forEach { columns ->
                        FilterChip(
                            selected = state.libraryGridColumns == columns,
                            onClick = {
                                onLibraryGridColumnsChange(columns)
                            },
                            label = {
                                Text("${columns}")
                            }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun MatchSettingsContent(
    state: SettingsUiState,
    isBatchMatching: Boolean,
    onRemoveUnderscoreInMatchTitleChange: (Boolean) -> Unit,
    onRemoveTrailingNumberSuffixInMatchTitleChange: (Boolean) -> Unit,
    onAutoMatchExactTitleChange: (Boolean) -> Unit,
    onAutoMatchUniqueSamePageChange: (Boolean) -> Unit,
    onAutoMatchSingleResultChange: (Boolean) -> Unit,
    onAutoMatchSamePageFirstChange: (Boolean) -> Unit,
    onAutoOpenNextReviewTaskChange: (Boolean) -> Unit,
    onFilteredMatchLanguagesChange: (String) -> Unit,
    onMatchSearchTimeoutSecondsChange: (String) -> Unit,
    onBatchMatchThreadsChange: (String) -> Unit,
    onStartBatchMatch: () -> Unit,
    onOpenTasks: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("标题处理")

        ListItem(
            headlineContent = {
                Text("匹配时删除标题中的下划线")
            },
            supportingContent = {
                Text("默认开启。hitomi 下载文件名时，?、| 等特殊字符有时会变成 _，开启后会从搜索词中删除这些下划线。")
            },
            trailingContent = {
                Switch(
                    checked = state.removeUnderscoreInMatchTitle,
                    onCheckedChange = onRemoveUnderscoreInMatchTitleChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("匹配时删除末尾的 (数字)")
            },
            supportingContent = {
                Text("默认开启。用于去掉文件名末尾的 (1)、(2)、(123) 等重复编号。")
            },
            trailingContent = {
                Switch(
                    checked = state.removeTrailingNumberSuffixInMatchTitle,
                    onCheckedChange = onRemoveTrailingNumberSuffixInMatchTitleChange
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionTitle("自动匹配规则")

        ListItem(
            headlineContent = {
                Text("自动匹配名称完全相同的")
            },
            supportingContent = {
                Text("默认开启。若候选标题或日文标题与搜索词完全一致，且完全匹配项唯一，则自动绑定。")
            },
            trailingContent = {
                Switch(
                    checked = state.autoMatchExactTitle,
                    onCheckedChange = onAutoMatchExactTitleChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("自动匹配搜索结果仅一个的")
            },
            supportingContent = {
                Text("默认关闭。若只搜到一个候选，可参与自动匹配。")
            },
            trailingContent = {
                Switch(
                    checked = state.autoMatchSingleResult,
                    onCheckedChange = onAutoMatchSingleResultChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("自动匹配唯一页数相同的")
            },
            supportingContent = {
                Text("默认开启。若候选中只有一个条目的页数与本地页数相同，则自动绑定。")
            },
            trailingContent = {
                Switch(
                    checked = state.autoMatchUniqueSamePage,
                    onCheckedChange = onAutoMatchUniqueSamePageChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("自动匹配页数相同的第一个")
            },
            supportingContent = {
                Text("默认开启。若同时开启“搜索结果仅一个”，则必须“仅一个候选且页数相同”才会自动匹配。")
            },
            trailingContent = {
                Switch(
                    checked = state.autoMatchSamePageFirst,
                    onCheckedChange = onAutoMatchSamePageFirstChange
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("复核绑定后自动打开下一个")
            },
            supportingContent = {
                Text("开启后，在任务详情中绑定候选成功后，会自动切换到下一个需要复核的任务。")
            },
            trailingContent = {
                Switch(
                    checked = state.autoOpenNextReviewTask,
                    onCheckedChange = onAutoOpenNextReviewTaskChange
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionTitle("候选过滤")

        ListItem(
            headlineContent = {
                Text("批量匹配线程数")
            },
            supportingContent = {
                OutlinedTextField(
                    value = state.batchMatchThreadsText,
                    onValueChange = onBatchMatchThreadsChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("范围 1-6；当前生效 ${state.batchMatchThreads} 个。")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("匹配搜索超时")
            },
            supportingContent = {
                OutlinedTextField(
                    value = state.matchSearchTimeoutSecondsText,
                    onValueChange = onMatchSearchTimeoutSecondsChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = {
                        Text("秒")
                    },
                    supportingText = {
                        Text("范围 10-360 秒；当前生效 ${state.matchSearchTimeoutSeconds} 秒。")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("过滤候选语言")
            },
            supportingContent = {
                OutlinedTextField(
                    value = state.filteredMatchLanguagesText,
                    onValueChange = onFilteredMatchLanguagesChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text("spanish, korean")
                    }
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionTitle("批量匹配")

        ListItem(
            headlineContent = {
                Text("批量匹配未匹配作品")
            },
            supportingContent = {
                Text("会为所有未匹配作品创建匹配任务。无法自动匹配的项目会进入“需要复核”。")
            },
            trailingContent = {
                Button(
                    onClick = onStartBatchMatch,
                    enabled = !isBatchMatching
                ) {
                    Text(if (isBatchMatching) "运行中" else "开始")
                }
            }
        )

        ListItem(
            headlineContent = {
                Text("查看匹配任务")
            },
            supportingContent = {
                Text("查看进行中、成功、失败、跳过和需要复核的任务。")
            },
            trailingContent = {
                TextButton(
                    onClick = onOpenTasks
                ) {
                    Text("打开")
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

private fun settingsTabLabel(
    tab: SettingsTab
): String {
    return when (tab) {
        SettingsTab.Directory -> "目录"
        SettingsTab.Reading -> "阅读"
        SettingsTab.Display -> "显示"
        SettingsTab.Match -> "匹配"
        SettingsTab.Database -> "数据库"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return String.format(Locale.US, "%.1f KiB", kib)
    val mib = kib / 1024.0
    if (mib < 1024.0) return String.format(Locale.US, "%.1f MiB", mib)
    return String.format(Locale.US, "%.2f GiB", mib / 1024.0)
}

@Composable
private fun WebDavSourceDialog(
    form: WebDavSourceForm,
    isTesting: Boolean,
    testMessage: String?,
    onFormChange: (WebDavSourceForm) -> Unit,
    onTest: (WebDavSourceForm) -> Unit,
    onSave: (WebDavSourceForm) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (form.editingSourceId == null) "添加 WebDAV" else "编辑 WebDAV") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.baseUrl,
                    onValueChange = { onFormChange(form.copy(baseUrl = it)) },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.rootPath,
                    onValueChange = { onFormChange(form.copy(rootPath = it)) },
                    label = { Text("扫描根路径") },
                    placeholder = { Text("/dav/books") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { onFormChange(form.copy(username = it)) },
                    label = { Text("用户名（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { onFormChange(form.copy(password = it)) },
                    label = { Text(if (form.editingSourceId == null) "密码（可选）" else "新密码（留空保持不变）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("允许不受信任证书")
                        Text(
                            "仅用于确认可信的自签名 NAS",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = form.allowInsecureTls,
                        onCheckedChange = { onFormChange(form.copy(allowInsecureTls = it)) }
                    )
                }
                Text("索引模式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.indexMode == RemoteIndexMode.Full,
                        onClick = { onFormChange(form.copy(indexMode = RemoteIndexMode.Full)) },
                        label = { Text("完整") }
                    )
                    FilterChip(
                        selected = form.indexMode == RemoteIndexMode.Light,
                        onClick = { onFormChange(form.copy(indexMode = RemoteIndexMode.Light)) },
                        label = { Text("轻量") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.connectTimeoutSecondsText,
                        onValueChange = { onFormChange(form.copy(connectTimeoutSecondsText = it.filter(Char::isDigit))) },
                        label = { Text("连接超时/秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = form.readTimeoutSecondsText,
                        onValueChange = { onFormChange(form.copy(readTimeoutSecondsText = it.filter(Char::isDigit))) },
                        label = { Text("读取超时/秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                testMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(form) },
                enabled = form.baseUrl.isNotBlank() && !isTesting
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onTest(form) },
                    enabled = form.baseUrl.isNotBlank() && !isTesting
                ) { Text(if (isTesting) "测试中" else "测试连接") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun ReadingSettingsContent(
    state: SettingsUiState,
    onReadModeChange: (RemoteArchiveReadMode) -> Unit,
    onCachePolicyChange: (RemoteCachePolicy) -> Unit,
    onCacheLimitChange: (String) -> Unit,
    onBlockSizeChange: (String) -> Unit,
    onAllowBatchFullDownloadChange: (Boolean) -> Unit,
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("远程压缩包读取")
        ListItem(
            headlineContent = { Text("读取策略") },
            supportingContent = {
                Column {
                    RemoteArchiveReadMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.remoteArchiveReadMode == mode,
                            onClick = { onReadModeChange(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        RemoteArchiveReadMode.RangeWithDownloadFallback -> "Range 优先，失败时下载"
                                        RemoteArchiveReadMode.RangeOnly -> "仅 Range"
                                        RemoteArchiveReadMode.DownloadOnly -> "总是完整下载"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        )
        ListItem(
            headlineContent = { Text("批量匹配允许完整下载") },
            supportingContent = { Text("关闭时，批量任务不会为了读取页数而下载整个远程压缩包。") },
            trailingContent = {
                Switch(
                    checked = state.allowBatchRemoteFullDownload,
                    onCheckedChange = onAllowBatchFullDownloadChange
                )
            }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("远程缓存")
        ListItem(
            headlineContent = { Text("缓存策略") },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteCachePolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = state.remoteCachePolicy == policy,
                            onClick = { onCachePolicyChange(policy) },
                            label = {
                                Text(
                                    when (policy) {
                                        RemoteCachePolicy.Lru -> "LRU"
                                        RemoteCachePolicy.Session -> "仅本次"
                                        RemoteCachePolicy.Persistent -> "永久"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        )
        ListItem(
            headlineContent = { Text("缓存上限") },
            supportingContent = {
                OutlinedTextField(
                    value = state.remoteCacheLimitMbText,
                    onValueChange = onCacheLimitChange,
                    suffix = { Text("MB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
        ListItem(
            headlineContent = { Text("Range 分块大小") },
            supportingContent = {
                OutlinedTextField(
                    value = state.remoteRangeBlockSizeKbText,
                    onValueChange = onBlockSizeChange,
                    suffix = { Text("KiB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
        ListItem(
            headlineContent = { Text("已用 ${formatBytes(state.remoteCacheUsageBytes)}") },
            supportingContent = { Text("清理远程 Range 块与完整压缩包，不删除封面和数据库。") },
            trailingContent = {
                Button(onClick = onClearCache) { Text("清理") }
            }
        )
    }
}
