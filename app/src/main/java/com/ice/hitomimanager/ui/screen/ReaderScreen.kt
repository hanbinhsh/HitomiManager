package com.ice.hitomimanager.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import coil3.compose.AsyncImage
import com.ice.hitomimanager.ReaderUiState
import com.ice.hitomimanager.data.model.PageInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onPagePreviewRequested: (Int) -> Unit,
    onBackToDetail: () -> Unit,
    onRetry: () -> Unit
) {
    val imagePageCount = state.pages.size

    var controlsVisible by rememberSaveable {
        mutableStateOf(true)
    }

    var backRequested by remember {
        mutableStateOf(false)
    }

    var readerMode by rememberSaveable {
        mutableStateOf(ReaderMode.Page)
    }
    var landscapeLocked by rememberSaveable {
        mutableStateOf(false)
    }
    val readerScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) {
        context.findActivity()
    }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }

    LaunchedEffect(landscapeLocked, activity) {
        activity?.requestedOrientation = if (landscapeLocked) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    ReaderSystemBars(
        visible = controlsVisible || readerMode == ReaderMode.Grid
    )

    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = {
            if (imagePageCount > 0) imagePageCount + 1 else 0
        }
    )

    LaunchedEffect(state.book?.uriString, imagePageCount) {
        if (imagePageCount > 0) {
            backRequested = false
            readerMode = ReaderMode.Page
            controlsVisible = true
            pagerState.scrollToPage(1)
            onPageChanged(0)
        }
    }

    LaunchedEffect(pagerState.currentPage, imagePageCount) {
        if (imagePageCount <= 0) return@LaunchedEffect

        if (pagerState.currentPage == 0) {
            if (!backRequested) {
                backRequested = true
                onBackToDetail()
            }
        } else {
            val imagePage = pagerState.currentPage - 1
            onPageChanged(imagePage)
        }
    }

    val currentImagePage = when {
        imagePageCount <= 0 -> 0
        pagerState.currentPage <= 0 -> 0
        else -> (pagerState.currentPage - 1).coerceIn(0, imagePageCount - 1)
    }

    val currentInfo = state.pageInfos[currentImagePage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            state.isOpening -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    state.remoteProgress?.let { progress ->
                        Text(progress.stage, color = Color.White)
                        if (progress.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = {
                                    (progress.bytesRead.toFloat() / progress.totalBytes)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${formatReaderBytes(progress.bytesRead)} / ${formatReaderBytes(progress.totalBytes)}",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            state.error != null && imagePageCount == 0 -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = state.error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("重试") }
                }
            }

            imagePageCount > 0 && readerMode == ReaderMode.Grid -> {
                ReaderGridContent(
                    state = state,
                    currentPage = currentImagePage,
                    onPagePreviewRequested = onPagePreviewRequested,
                    onPageClick = { index ->
                        readerScope.launch {
                            onPageChanged(index)
                            readerMode = ReaderMode.Page
                            controlsVisible = true
                            pagerState.scrollToPage(index + 1)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            imagePageCount > 0 -> {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { pagerPage ->
                    if (pagerPage == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "返回详情",
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        val imagePage = pagerPage - 1
                        val imageFile = state.pageFiles[imagePage]

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageFile != null) {
                                CoilZoomAsyncImage(
                                    model = imageFile,
                                    contentDescription = state.book?.displayName ?: "漫画页",
                                    modifier = Modifier.fillMaxSize(),
                                    scrollBar = null,
                                    onTap = {
                                        controlsVisible = !controlsVisible
                                    }
                                )
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || readerMode == ReaderMode.Grid,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                pageInfo = currentInfo,
                fallbackName = state.pages.getOrNull(currentImagePage),
                mode = readerMode,
                onBack = onBack,
                onToggleMode = {
                    readerMode = if (readerMode == ReaderMode.Page) {
                        controlsVisible = true
                        ReaderMode.Grid
                    } else {
                        ReaderMode.Page
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = (controlsVisible || readerMode == ReaderMode.Grid) && imagePageCount > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                pageIndex = currentImagePage,
                pageCount = imagePageCount,
                landscapeLocked = landscapeLocked,
                onToggleLandscape = {
                    landscapeLocked = !landscapeLocked
                }
            )
        }
    }
}

private fun formatReaderBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return String.format(Locale.US, "%.1f KiB", kib)
    val mib = kib / 1024.0
    if (mib < 1024.0) return String.format(Locale.US, "%.1f MiB", mib)
    return String.format(Locale.US, "%.2f GiB", mib / 1024.0)
}

private enum class ReaderMode {
    Page,
    Grid
}

@Composable
private fun ReaderTopBar(
    pageInfo: PageInfo?,
    fallbackName: String?,
    mode: ReaderMode,
    onBack: () -> Unit,
    onToggleMode: () -> Unit
) {
    val imageName = remember(pageInfo, fallbackName) {
        pageInfo?.entryName
            ?.replace("\\", "/")
            ?.substringAfterLast("/")
            ?: fallbackName
                ?.replace("\\", "/")
                ?.substringAfterLast("/")
            ?: "未知图片"
    }

    val detailText = remember(pageInfo) {
        buildDetailText(pageInfo)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 56.dp, end = 56.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = imageName,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = detailText,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }

        IconButton(
            onClick = onToggleMode,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = if (mode == ReaderMode.Page) {
                    Icons.Filled.GridView
                } else {
                    Icons.Filled.ViewCarousel
                },
                contentDescription = if (mode == ReaderMode.Page) {
                    "网格浏览"
                } else {
                    "大图浏览"
                },
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ReaderGridContent(
    state: ReaderUiState,
    currentPage: Int,
    onPagePreviewRequested: (Int) -> Unit,
    onPageClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = currentPage.coerceAtLeast(0)
    )

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = modifier
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 80.dp,
            end = 8.dp,
            bottom = 72.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = state.pages,
            key = { index, name -> "$index:$name" }
        ) { index, pageName ->
            ReaderGridPageItem(
                index = index,
                pageName = pageName,
                imageFile = state.pageFiles[index],
                loading = index in state.loadingPageIndices,
                selected = index == currentPage,
                onPagePreviewRequested = onPagePreviewRequested,
                onPageClick = onPageClick
            )
        }
    }
}

@Composable
private fun ReaderGridPageItem(
    index: Int,
    pageName: String,
    imageFile: java.io.File?,
    loading: Boolean,
    selected: Boolean,
    onPagePreviewRequested: (Int) -> Unit,
    onPageClick: (Int) -> Unit
) {
    LaunchedEffect(index) {
        onPagePreviewRequested(index)
    }

    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .background(Color.White.copy(alpha = 0.08f))
            .clickable {
                onPageClick(index)
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = pageName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White
            )
        } else {
            Text(
                text = "${index + 1}",
                color = Color.White.copy(alpha = 0.72f)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.68f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = "${index + 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    pageIndex: Int,
    pageCount: Int,
    landscapeLocked: Boolean,
    onToggleLandscape: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${pageIndex + 1} / $pageCount",
            color = Color.White
        )

        IconButton(
            onClick = onToggleLandscape,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Filled.ScreenRotation,
                contentDescription = if (landscapeLocked) {
                    "恢复方向"
                } else {
                    "横屏观看"
                },
                tint = if (landscapeLocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White
                }
            )
        }
    }
}

@Composable
private fun ReaderSystemBars(
    visible: Boolean
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) {
        context.findActivity()
    }

    DisposableEffect(visible, activity, view) {
        val window = activity?.window

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val controller = WindowInsetsControllerCompat(
                window,
                view
            )

            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            if (visible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            if (window != null) {
                val controller = WindowInsetsControllerCompat(
                    window,
                    view
                )
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }
}

private fun buildDetailText(
    pageInfo: PageInfo?
): String {
    if (pageInfo == null) {
        return "正在读取信息..."
    }

    val modified = pageInfo.modifiedTimeMillis
        ?.let { millis ->
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
            ).format(Date(millis))
        }
        ?: "未知时间"

    val size = formatBytes(pageInfo.sizeBytes)

    val resolution = if (pageInfo.width > 0 && pageInfo.height > 0) {
        "${pageInfo.width}×${pageInfo.height}"
    } else {
        "未知分辨率"
    }

    return "$modified  ·  $size  ·  $resolution"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "未知大小"

    val kb = bytes / 1024.0
    val mb = kb / 1024.0

    return if (mb >= 1.0) {
        String.format(Locale.getDefault(), "%.2f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.1f KB", kb)
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
