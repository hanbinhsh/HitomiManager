package com.ice.hitomimanager.ui.screen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ice.hitomimanager.AppViewModel
import com.ice.hitomimanager.data.model.HomeTab
import com.ice.hitomimanager.data.local.entity.MatchTaskEntity
import com.ice.hitomimanager.data.model.MatchTaskStatus

private object Routes {
    const val Library = "library"
    const val Detail = "detail"
    const val Reader = "reader"
    const val Match = "match"
    const val Settings = "settings"
    const val MatchTaskDetail = "match_task_detail"
}

@Composable
fun AppRoot(
    viewModel: AppViewModel = viewModel()
) {
    val navController = rememberNavController()

    val libraryState by viewModel.libraryState.collectAsState()
    val detailState by viewModel.bookDetailState.collectAsState()
    val readerState by viewModel.readerState.collectAsState()
    val matchState by viewModel.matchState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val matchTaskDetailState by viewModel.matchTaskDetailState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.Library,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(260)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(260)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(260)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(260)
            )
        }
    ) {
        composable(Routes.Library) {
            LibraryScreen(
                state = libraryState,
                showTagNamespacePrefix = settingsState.showTagNamespacePrefix,
                onHomeTabChange = viewModel::setHomeTab,
                onRescan = viewModel::rescan,
                onOpenSettings = {
                    navController.navigate(Routes.Settings)
                },
                onToggleTag = viewModel::toggleTagFilter,
                onClearTagFilters = viewModel::clearTagFilters,
                onTagSortModeChange = viewModel::setTagSortMode,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onClearSearch = viewModel::clearSearchQuery,
                onMatchTaskFilterChange = viewModel::setMatchTaskFilter,
                onRetryFailedMatchTasks = viewModel::retryFailedMatchTasks,
                showRematchButtonInLibrary = settingsState.showRematchButtonInLibrary,
                onRetryFailedExceptNoCandidates = viewModel::retryFailedMatchTasksExceptNoCandidates,
                onStartBatchMatch = viewModel::startBatchMatchUnmatched,
                libraryLayoutMode = settingsState.libraryLayoutMode,
                libraryGridColumns = settingsState.libraryGridColumns,
                onToggleLibraryLayoutMode = viewModel::toggleLibraryLayoutMode,
                onOpenMatchTask = { task ->
                    if (task.status == MatchTaskStatus.Failed) {
                        viewModel.startMatchFromTask(task)
                        navController.navigate(Routes.Match)
                    } else {
                        viewModel.openMatchTaskDetail(task)
                        navController.navigate(Routes.MatchTaskDetail)
                    }
                },
                onOpenBook = { book ->
                    viewModel.openBookDetail(book)
                    navController.navigate(Routes.Detail)
                },
                onMatchBook = { book ->
                    viewModel.startMatch(book)
                    navController.navigate(Routes.Match)
                },
            )
        }

        composable(Routes.Detail) {
            BookDetailScreen(
                state = detailState,
                showTagNamespacePrefix = settingsState.showTagNamespacePrefix,
                onBack = {
                    navController.popBackStack()
                },
                onRead = { book ->
                    viewModel.openBook(book)
                    navController.navigate(Routes.Reader)
                },
                onMatch = { book ->
                    viewModel.startMatch(book)
                    navController.navigate(Routes.Match)
                },
                onTagClick = { tag ->
                    viewModel.applyTagFilter(tag)
                    navController.popBackStack(
                        route = Routes.Library,
                        inclusive = false
                    )
                }
            )
        }

        composable(Routes.Reader) {
            ReaderScreen(
                state = readerState,
                onBack = {
                    navController.popBackStack()
                },
                onPageChanged = viewModel::onReaderPageChanged,
                onBackToDetail = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.Match) {
            MatchScreen(
                state = matchState,
                onBack = {
                    navController.popBackStack()
                },
                onQueryChange = viewModel::updateMatchQuery,
                onSearch = viewModel::searchMatch,
                onIdMatch = viewModel::matchById,
                onBind = { meta ->
                    viewModel.bindMatch(meta)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(
                state = settingsState,
                isBatchMatching = libraryState.isBatchMatching,
                onBack = {
                    navController.popBackStack()
                },
                onSettingsTabChange = viewModel::setSettingsTab,
                onFolderPicked = viewModel::onFolderPicked,
                onShowTagNamespacePrefixChange = viewModel::setShowTagNamespacePrefix,
                onRemoveUnderscoreInMatchTitleChange = viewModel::setRemoveUnderscoreInMatchTitle,
                onRemoveTrailingNumberSuffixInMatchTitleChange = viewModel::setRemoveTrailingNumberSuffixInMatchTitle,
                onAutoMatchExactTitleChange = viewModel::setAutoMatchExactTitle,
                onAutoMatchUniqueSamePageChange = viewModel::setAutoMatchUniqueSamePage,
                onAutoMatchSingleResultChange = viewModel::setAutoMatchSingleResult,
                onAutoMatchSamePageFirstChange = viewModel::setAutoMatchSamePageFirst,
                onAutoOpenNextReviewTaskChange = viewModel::setAutoOpenNextReviewTask,
                onStartBatchMatch = viewModel::startBatchMatchUnmatched,
                onClearDatabase = viewModel::clearDatabase,
                onShowRematchButtonInLibraryChange = viewModel::setShowRematchButtonInLibrary,
                onLibraryGridColumnsChange = viewModel::setLibraryGridColumns,
                onOpenTasks = {
                    viewModel.setHomeTab(HomeTab.Tasks)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.MatchTaskDetail) {
            MatchTaskDetailScreen(
                state = matchTaskDetailState,
                onBack = {
                    navController.popBackStack()
                },
                onBindCandidate = { candidate ->
                    viewModel.bindMatchTaskCandidate(candidate) { nextTask ->
                        if (nextTask != null) {
                            viewModel.openMatchTaskDetail(nextTask)
                        }
                    }
                },
                onOpenMatchPage = {
                    val task = matchTaskDetailState.task
                    if (task != null) {
                        viewModel.startMatchFromTask(task)
                        navController.navigate(Routes.Match)
                    }
                },
                onMarkSkipped = viewModel::markCurrentMatchTaskSkipped
            )
        }
    }
}