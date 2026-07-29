package com.v2ray.ang.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.compose.QRCodeDialog
import com.v2ray.ang.dto.entities.ProfileItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// 🎨 پلت رنگی روشن (Light Theme)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF545F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    background = Color(0xFFFDFCFE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFE),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E3EB),
    onSurfaceVariant = Color(0xFF43474E),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

// 🎨 پلت رنگی تاریک (Dark Theme)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
    shareMethodEntries: List<String>,
    shareMethodMoreEntries: List<String>
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = uiState.statusText
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    // 🌗 محاسبه تم تاریک/روشن
    val systemDarkMode = isSystemInDarkTheme()
    val effectiveDarkMode = if (uiState.hasUserToggledTheme) uiState.isDarkMode else systemDarkMode
    val colorScheme = if (effectiveDarkMode) DarkColorScheme else LightColorScheme

    // 🦁 انتخاب آیکون شیر و خورشید متناسب با تم
    val lionDrawableRes = if (effectiveDarkMode) R.drawable.ic_w_lion else R.drawable.ic_b_lion

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    var locateInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    val latestDoubleColumnDisplay by rememberUpdatedState(doubleColumnDisplay)

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)
    val latestLocateInProgress by rememberUpdatedState(locateInProgress)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (!latestLocateInProgress && page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    LaunchedEffect(uiState.locateTarget) {
        val target = uiState.locateTarget ?: return@LaunchedEffect
        if (target.groupIndex !in 0 until pagerState.pageCount) {
            mainViewModel.onAction(MainAction.LocateHandled(target))
            return@LaunchedEffect
        }

        locateInProgress = true
        try {
            if (pagerState.settledPage != target.groupIndex) {
                pagerState.navigateToPageOptimized(
                    targetPage = target.groupIndex,
                    animateAdjacentPage = false
                )
            }
            onAction(MainAction.SelectGroup(target.groupId))

            repeat(10) {
                val ready = if (latestDoubleColumnDisplay) {
                    lazyGridStates[target.groupId] != null
                } else {
                    lazyListStates[target.groupId] != null
                }
                if (ready) return@repeat
                delay(16L)
            }

            if (latestDoubleColumnDisplay) {
                lazyGridStates[target.groupId]?.let { gridState ->
                    gridState.scrollToItem(
                        index = target.itemPosition,
                        scrollOffset = -gridState.layoutInfo.viewportSize.height / 3
                    )
                }
            } else {
                lazyListStates[target.groupId]?.let { listState ->
                    listState.scrollToItem(
                        index = target.itemPosition,
                        scrollOffset = -listState.layoutInfo.viewportSize.height / 3
                    )
                }
            }
        } finally {
            delay(32L)
            locateInProgress = false
            mainViewModel.onAction(MainAction.LocateHandled(target))
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        MainDialogs(
            showDelAllConfirm = showDelAllConfirm,
            onDismissDelAll = { showDelAllConfirm = false },
            onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
            showDelDuplicateConfirm = showDelDuplicateConfirm,
            onDismissDelDuplicate = { showDelDuplicateConfirm = false },
            onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
            showDelInvalidConfirm = showDelInvalidConfirm,
            onDismissDelInvalid = { showDelInvalidConfirm = false },
            onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
            showRemoveConfirm = showRemoveConfirm,
            onDismissRemove = { showRemoveConfirm = null },
            onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
        )

        if (shareTarget != null) {
            val (guid, profile, more) = shareTarget!!
            ShareMethodDialog(
                guid = guid,
                profile = profile,
                more = more,
                shareMethodEntries = shareMethodEntries,
                shareMethodMoreEntries = shareMethodMoreEntries,
                onDismiss = { shareTarget = null },
                onAction = onAction
            )
        }
        if (shareQRCodeBitmap != null) {
            QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            drawerContent = {
                MainDrawerContent(
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        onNavigate(route)
                    }
                )
            }
        ) {
            Scaffold(
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    MainTopBar(
                        isLoading = isLoading,
                        showSearch = showSearch,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { query: String ->
                            searchQuery = query
                            onAction(MainAction.Search(query))
                        },
                        onSearchClose = {
                            searchQuery = ""
                            onAction(MainAction.Search(""))
                            showSearch = false
                        },
                        onSearchToggle = { show: Boolean -> showSearch = show },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onAction = onAction,
                        onDelAllConfig = { showDelAllConfirm = true },
                        onDelDuplicateConfig = { showDelDuplicateConfirm = true },
                        onDelInvalidConfig = { showDelInvalidConfirm = true }
                    )
                },
                bottomBar = {
                    MainBottomBar(
                        displayText = displayText,
                        isRunning = isRunning,
                        isDarkTheme = effectiveDarkMode,
                        onAction = onAction
                    )
                },
                floatingActionButton = {},
            ) { innerPadding ->
                // 📦 استفاده از Box برای قرارگیری تصویر شیر و خورشید در پس‌زمینه (Watermark)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    // 🦁 ۱. تصویر پس‌زمینه شیر و خورشید با ۱۰٪ شفافیت
                    Image(
                        painter = painterResource(id = lionDrawableRes),
                        contentDescription = "Lion Watermark Background",
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit,
                        alpha = 0.10f
                    )

                    // 📋 ۲. محتوای اصلی (تب‌ها و پیجر کانفیگ‌ها)
                    if (groups.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (groups.size > 1) {
                                GroupTabBar(
                                    groups = groups,
                                    selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                                    mainViewModel = mainViewModel,
                                    onTabClick = { targetIndex ->
                                        scope.launch {
                                            pagerState.navigateToPageOptimized(
                                                targetPage = targetIndex,
                                                animateAdjacentPage = true
                                            )
                                        }
                                    }
                                )
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = true,
                                beyondViewportPageCount = 1,
                                key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                            ) { page ->
                                val group = groups.getOrNull(page) ?: return@HorizontalPager

                                GroupPagerPage(
                                    groupId = group.id,
                                    mainViewModel = mainViewModel,
                                    selectedGuid = selectedGuid,
                                    doubleColumnDisplay = doubleColumnDisplay,
                                    confirmRemove = confirmRemove,
                                    searchQuery = searchQuery,
                                    lazyListStates = lazyListStates,
                                    lazyGridStates = lazyGridStates,
                                    onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                                    onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                                    onShareServer = { guid, profile ->
                                        shareTarget = Triple(guid, profile, false)
                                    },
                                    onMoreServer = { guid, profile ->
                                        shareTarget = Triple(guid, profile, true)
                                    },
                                    onRemoveServer = { guid ->
                                        if (confirmRemove) showRemoveConfirm = guid
                                        else onAction(MainAction.RemoveServer(guid))
                                    },
                                    contentPadding = PaddingValues(
                                        start = 0.dp,
                                        top = 0.dp,
                                        end = 0.dp,
                                        bottom = 80.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}