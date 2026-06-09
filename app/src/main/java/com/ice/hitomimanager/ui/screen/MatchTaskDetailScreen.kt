package com.ice.hitomimanager.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ice.hitomimanager.MatchTaskDetailUiState
import com.ice.hitomimanager.data.local.entity.MatchCandidateEntity
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import com.ice.hitomimanager.data.model.MatchTaskStatus
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchTaskDetailScreen(
    state: MatchTaskDetailUiState,
    onBack: () -> Unit,
    onBindCandidate: (MatchCandidateEntity) -> Unit,
    onOpenMatchPage: () -> Unit,
    onReadTask: () -> Unit,
    onMarkSkipped: () -> Unit
) {
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
                    Text("匹配任务")
                }
            )
        }
    ) { paddingValues ->
        val task = state.task

        if (task == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("任务不存在")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TaskHeaderCard(
                    task = task,
                    isBinding = state.isBinding,
                    isRefreshing = state.isRefreshing,
                    onOpenMatchPage = onOpenMatchPage,
                    onReadTask = onReadTask,
                    onMarkSkipped = onMarkSkipped
                )
            }

            if (state.error != null) {
                item {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (state.isBinding || state.isRefreshing) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text(if (state.isBinding) "正在绑定..." else "正在重新搜索...")
                    }
                }
            }

            item {
                Text(
                    text = "候选列表",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.candidates.isEmpty()) {
                item {
                    Text(
                        text = "暂无候选。可以点击“打开匹配页”修改搜索词后重新搜索。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.candidates) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        localPageCount = task.localPageCount,
                        enabled = !state.isBinding && !state.isRefreshing,
                        onBind = {
                            onBindCandidate(candidate)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskHeaderCard(
    task: MatchTaskEntity,
    isBinding: Boolean,
    isRefreshing: Boolean,
    onOpenMatchPage: () -> Unit,
    onReadTask: () -> Unit,
    onMarkSkipped: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val coverPath = task.coverFilePath

                if (coverPath != null) {
                    AsyncImage(
                        model = File(coverPath),
                        contentDescription = task.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(72.dp)
                            .height(102.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = task.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text("状态：${matchTaskStatusLabel(task.status)}")
                    Text("搜索词：${task.query}")
                    Text("本地页数：${task.localPageCount?.let { "${it}p" } ?: "未知"}")
                    Text("候选数：${task.candidateCount}")

                    if (task.matchedGalleryId != null) {
                        Text("已匹配 ID：${task.matchedGalleryId}")
                    }

                    if (task.errorMessage != null) {
                        Text(
                            text = task.errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onReadTask,
                    enabled = !isBinding && !isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "打开阅读器"
                    )
                }

                FilledTonalButton(
                    onClick = onOpenMatchPage,
                    enabled = !isBinding && !isRefreshing
                ) {
                    Text("打开匹配页")
                }

                OutlinedButton(
                    onClick = onMarkSkipped,
                    enabled = !isBinding && !isRefreshing &&
                            task.status != MatchTaskStatus.Skipped
                ) {
                    Text("标记跳过")
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: MatchCandidateEntity,
    localPageCount: Int?,
    enabled: Boolean,
    onBind: () -> Unit
) {
    val context = LocalContext.current

    val pageMatched = localPageCount != null &&
            localPageCount > 0 &&
            candidate.pageCount == localPageCount

    ListItem(
        headlineContent = {
            Text(
                text = candidate.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                val info = listOfNotNull(
                    candidate.language,
                    candidate.type,
                    if (candidate.pageCount > 0) "${candidate.pageCount}p" else null,
                    candidate.date,
                    "ID:${candidate.galleryId}"
                ).joinToString(" · ")

                Text(
                    text = info,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!candidate.japaneseTitle.isNullOrBlank()) {
                    Text(
                        text = candidate.japaneseTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pageMatched) {
                    Text(
                        text = "页数一致",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (candidate.selected) {
                    Text(
                        text = "已选择",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val url = "https://hitomi.la/galleries/${candidate.galleryId}.html"
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = "在浏览器中打开"
                    )
                }

                IconButton(
                    onClick = onBind,
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = if (candidate.selected) {
                            "已绑定"
                        } else {
                            "绑定"
                        },
                        tint = if (candidate.selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    )
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