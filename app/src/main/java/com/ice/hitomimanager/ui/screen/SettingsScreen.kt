package com.ice.hitomimanager.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TabRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ice.hitomimanager.SettingsUiState
import com.ice.hitomimanager.data.model.SettingsTab
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
    onShowTagNamespacePrefixChange: (Boolean) -> Unit,
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
    onOpenTasks: () -> Unit,
    onShowRematchButtonInLibraryChange: (Boolean) -> Unit,
    onLibraryGridColumnsChange: (Int) -> Unit,
    onFilteredMatchLanguagesChange: (String) -> Unit,
    onMatchSearchTimeoutSecondsChange: (String) -> Unit,
    onBatchMatchThreadsChange: (String) -> Unit,
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
            TabRow(
                selectedTabIndex = state.settingsTab.ordinal
            ) {
                SettingsTab.values().forEach { tab ->
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
                SettingsTab.General -> {
                    GeneralSettingsContent(
                        state = state,
                        onPickFolder = {
                            folderPicker.launch(null)
                        },
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
                        }
                    )
                }

                SettingsTab.Display -> {
                    DisplaySettingsContent(
                        state = state,
                        onShowTagNamespacePrefixChange = onShowTagNamespacePrefixChange,
                        onShowRematchButtonInLibraryChange = onShowRematchButtonInLibraryChange,
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
    onExportDatabaseClick: () -> Unit,
    onImportDatabaseClick: () -> Unit,
    onClearDatabaseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("书库目录")

        ListItem(
            headlineContent = {
                Text("当前目录")
            },
            supportingContent = {
                Text(
                    text = state.folderUriString ?: "尚未选择",
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )

        ListItem(
            headlineContent = {
                Text("选择本地目录")
            },
            supportingContent = {
                Text("选择存放 zip / cbz 文件的目录。")
            },
            trailingContent = {
                Button(
                    onClick = onPickFolder
                ) {
                    Text(if (state.folderUriString == null) "选择" else "更换")
                }
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

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
private fun DisplaySettingsContent(
    state: SettingsUiState,
    onShowTagNamespacePrefixChange: (Boolean) -> Unit,
    onShowRematchButtonInLibraryChange: (Boolean) -> Unit,
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

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionTitle("主页显示")

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
        SettingsTab.General -> "常规"
        SettingsTab.Display -> "显示"
        SettingsTab.Match -> "匹配"
    }
}
