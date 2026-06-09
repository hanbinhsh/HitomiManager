package com.ice.hitomimanager.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.ice.hitomimanager.ReaderUiState
import com.ice.hitomimanager.data.model.PageInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onBackToDetail: () -> Unit
) {
    val imagePageCount = state.pages.size

    var controlsVisible by rememberSaveable {
        mutableStateOf(true)
    }

    var backRequested by remember {
        mutableStateOf(false)
    }

    ReaderSystemBars(
        visible = controlsVisible
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
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            state.error != null && imagePageCount == 0 -> {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            imagePageCount > 0 -> {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 2,
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
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                pageInfo = currentInfo,
                fallbackName = state.pages.getOrNull(currentImagePage),
                onBack = onBack
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && imagePageCount > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                pageIndex = currentImagePage,
                pageCount = imagePageCount
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    pageInfo: PageInfo?,
    fallbackName: String?,
    onBack: () -> Unit
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
                .padding(start = 56.dp, end = 16.dp),
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
    }
}

@Composable
private fun ReaderBottomBar(
    pageIndex: Int,
    pageCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${pageIndex + 1} / $pageCount",
            color = Color.White
        )
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