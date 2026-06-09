package com.ice.hitomimanager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ice.hitomimanager.MatchUiState
import com.ice.hitomimanager.data.model.HitomiBookMeta
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    state: MatchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onIdMatch: () -> Unit,
    onRead: () -> Unit,
    onBind: (HitomiBookMeta) -> Unit
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
                    Text("匹配元数据")
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MatchBookHeader(
                state = state,
                onSearch = onSearch,
                onIdMatch = onIdMatch,
                onRead = onRead
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = {
                    Text("搜索标题 / ID")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            if (state.isSearching) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()

                    Text(
                        text = "正在搜索候选...",
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.candidates) { meta ->
                    CandidateItem(
                        meta = meta,
                        localPageCount = state.localPageCount,
                        onBind = {
                            onBind(meta)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateItem(
    meta: HitomiBookMeta,
    localPageCount: Int?,
    onBind: () -> Unit
) {
    val pageMatched = localPageCount != null &&
            localPageCount > 0 &&
            meta.pageCount == localPageCount

    ListItem(
        headlineContent = {
            Text(
                text = meta.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                val info = listOfNotNull(
                    meta.language,
                    meta.type,
                    if (meta.pageCount > 0) "${meta.pageCount}p" else null,
                    meta.date,
                    "ID:${meta.id}"
                ).joinToString(" · ")

                Text(
                    text = info,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!meta.japaneseTitle.isNullOrBlank()) {
                    Text(
                        text = meta.japaneseTitle,
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
                } else if (localPageCount != null && localPageCount > 0 && meta.pageCount > 0) {
                    Text(
                        text = "页数不一致：本地 ${localPageCount}p / 候选 ${meta.pageCount}p",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Button(
                onClick = onBind
            ) {
                Text("绑定")
            }
        },
        modifier = Modifier
            .clickable(onClick = onBind)
            .fillMaxWidth()
    )
}

@Composable
private fun MatchBookHeader(
    state: MatchUiState,
    onSearch: () -> Unit,
    onIdMatch: () -> Unit,
    onRead: () -> Unit
) {
    val book = state.book ?: return

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
                text = book.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val localPages = state.localPageCount?.let {
                    "本地页数：${it}p"
                } ?: "本地页数：读取中"

                val matched = if (book.sourceGalleryId != null) {
                    "已匹配 ID：${book.sourceGalleryId}"
                } else {
                    "未匹配"
                }

                Text(
                    text = "$localPages · $matched",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onRead,
                        enabled = !state.isSearching
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "打开阅读器"
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onSearch,
                        enabled = !state.isSearching
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索"
                        )
                    }

                    OutlinedButton(
                        onClick = onIdMatch,
                        enabled = !state.isSearching
                    ) {
                        Text("ID 匹配")
                    }
                }
            }
        }
    )
}