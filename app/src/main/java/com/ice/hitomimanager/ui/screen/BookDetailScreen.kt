package com.ice.hitomimanager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ice.hitomimanager.BookDetailUiState
import com.ice.hitomimanager.data.local.entity.TagEntity
import com.ice.hitomimanager.data.model.BookItem
import java.io.File
import androidx.compose.material3.TopAppBar
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalMinimumInteractiveComponentSize

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    state: BookDetailUiState,
    showTagNamespacePrefix: Boolean,
    onBack: () -> Unit,
    onRead: (BookItem) -> Unit,
    onMatch: (BookItem) -> Unit,
    onTagClick: (TagEntity) -> Unit
) {
    val book = state.book
    val context = LocalContext.current

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
                    Text("作品详情")
                }
            )
        }
    ) { paddingValues ->
        if (book == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("未选择作品")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(book.uriString) {
                    var totalDrag = 0f
                    val threshold = 140f

                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                        },
                        onDragEnd = {
                            when {
                                // 从左侧向右侧滑：返回书架
                                totalDrag > threshold -> {
                                    onBack()
                                }

                                // 从右侧向左侧滑：开始阅读第一页
                                totalDrag < -threshold -> {
                                    onRead(book)
                                }
                            }

                            totalDrag = 0f
                        },
                        onDragCancel = {
                            totalDrag = 0f
                        }
                    )
                }
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderCard(
                book = book
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            onRead(book)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "开始阅读"
                        )
                    }

                    if (book.sourceGalleryId == null) {
                        FilledTonalButton(
                            onClick = {
                                onMatch(book)
                            }
                        ) {
                            Text("匹配元数据")
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = {
                                onMatch(book)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "重新匹配"
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val url = "https://hitomi.la/galleries/${book.sourceGalleryId}.html"
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
                    }
                }
            }

            InfoSection(book)

            MetadataGroupSection(
                title = "作者",
                tags = state.tags.filter { it.namespace == "artist" },
                showTagNamespacePrefix = showTagNamespacePrefix,
                onTagClick = onTagClick
            )

            MetadataGroupSection(
                title = "社团",
                tags = state.tags.filter { it.namespace == "group" },
                showTagNamespacePrefix = showTagNamespacePrefix,
                onTagClick = onTagClick
            )

            MetadataGroupSection(
                title = "系列",
                tags = state.tags.filter { it.namespace == "series" },
                showTagNamespacePrefix = showTagNamespacePrefix,
                onTagClick = onTagClick
            )

            MetadataGroupSection(
                title = "角色",
                tags = state.tags.filter { it.namespace == "character" },
                showTagNamespacePrefix = showTagNamespacePrefix,
                onTagClick = onTagClick
            )

            TagSection(
                title = "标签",
                tags = state.tags.filter {
                    it.namespace != "artist" &&
                            it.namespace != "group" &&
                            it.namespace != "series" &&
                            it.namespace != "character"
                },
                showTagNamespacePrefix = showTagNamespacePrefix,
                onTagClick = onTagClick
            )
        }
    }
}

@Composable
private fun HeaderCard(
    book: BookItem
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val coverPath = book.coverFilePath

            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = book.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(110.dp)
                        .height(155.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(155.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("无封面")
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = book.title ?: book.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                if (book.japaneseTitle != null) {
                    Text(
                        text = book.japaneseTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = book.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun InfoSection(
    book: BookItem
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "基础信息",
                style = MaterialTheme.typography.titleMedium
            )

            InfoLine("匹配状态", book.matchStatus)
            InfoLine("Gallery ID", book.sourceGalleryId ?: "未匹配")
            InfoLine("语言", book.language ?: "未知")
            InfoLine("类型", book.type ?: "未知")
            InfoLine("页数", book.pageCount?.toString() ?: "未知")
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label：",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )

        Text(
            text = value,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(
    title: String,
    tags: List<TagEntity>,
    showTagNamespacePrefix: Boolean,
    onTagClick: (TagEntity) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CompactTagCardPadding),
            verticalArrangement = Arrangement.spacedBy(CompactTagSectionGap)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )

            if (tags.isEmpty()) {
                Text(
                    text = "暂无标签。匹配元数据后会显示。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CompactTagHorizontalGap),
                    verticalArrangement = Arrangement.spacedBy(CompactTagVerticalGap)
                ) {
                    tags.forEach { tag ->
                        CompactAssistTagChip(
                            tag = tag,
                            showTagNamespacePrefix = showTagNamespacePrefix,
                            onTagClick = onTagClick
                        )
                    }
                }
            }
        }
    }
}

private fun formatTagLabel(
    tag: TagEntity,
    showNamespace: Boolean
): String {
    val name = tag.translatedName ?: tag.name

    return if (showNamespace) {
        "[${tag.namespace}] $name"
    } else {
        name
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataGroupSection(
    title: String,
    tags: List<TagEntity>,
    showTagNamespacePrefix: Boolean,
    onTagClick: (TagEntity) -> Unit
) {
    if (tags.isEmpty()) return

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CompactTagCardPadding),
            verticalArrangement = Arrangement.spacedBy(CompactTagSectionGap)
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
                    CompactAssistTagChip(
                        tag = tag,
                        showTagNamespacePrefix = showTagNamespacePrefix,
                        onTagClick = onTagClick
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactAssistTagChip(
    tag: TagEntity,
    showTagNamespacePrefix: Boolean,
    onTagClick: (TagEntity) -> Unit
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides 0.dp
    ) {
        AssistChip(
            onClick = {
                onTagClick(tag)
            },
            label = {
                Text(
                    text = formatTagLabel(
                        tag = tag,
                        showNamespace = showTagNamespacePrefix
                    ),
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
private val CompactTagSectionGap = 8.dp
private val CompactTagCardPadding = 12.dp