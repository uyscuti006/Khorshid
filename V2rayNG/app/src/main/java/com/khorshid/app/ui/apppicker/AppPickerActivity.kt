package com.v2ray.ang.ui.apppicker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppListItem
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.base.BaseComponentActivity

// 🎨 سیستم رنگی یکپارچه با سایر بخش‌های برنامه
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF545F71),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF43474E),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF263141),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF1E2026),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF232832),
    onSurfaceVariant = Color(0xFFC3C6CF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)

class AppPickerActivity : BaseComponentActivity() {

    companion object {
        private const val EXTRA_SELECTED_PACKAGES = "selected_packages"
        private const val EXTRA_PICKER_TITLE = "picker_title"

        fun createIntent(
            context: Context,
            selectedPackages: Collection<String> = emptyList(),
            title: String? = null
        ): Intent = Intent(context, AppPickerActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(selectedPackages))
            title?.let { putExtra(EXTRA_PICKER_TITLE, it) }
        }

        fun getSelectedPackages(intent: Intent?): List<String> {
            return intent?.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        }
    }

    private val viewModel: AppPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        viewModel.initialize(initial)
        viewModel.loadApps(this)
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(viewModel.getSelectedPackages()))
            }
        )
        super.finish()
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()

        AppPickerScreen(
            title = resolveScreenTitle(),
            apps = apps,
            isLoading = isLoading,
            selectedPackages = selectedPackages,
            onBackClick = { finish() },
            onToggleApp = { viewModel.toggleApp(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() }
        )
    }

    private fun resolveScreenTitle(): String {
        return intent.getStringExtra(EXTRA_PICKER_TITLE) ?: getString(R.string.per_app_proxy_settings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    title: String,
    apps: List<AppInfo>,
    isLoading: Boolean,
    selectedPackages: Set<String>,
    onBackClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(searchQuery) {
        onSearch(searchQuery)
    }

    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AppTopBar(
                    title = title,
                    onBackClick = onBackClick,
                    isLoading = isLoading,
                    isSearchActive = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                    },
                    onSearchClose = {
                        searchQuery = ""
                        showSearch = false
                    },
                    searchPlaceholder = stringResource(R.string.menu_item_search),
                    actions = {
                        if (!showSearch) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search_24dp),
                                    contentDescription = stringResource(R.string.menu_item_search),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert_24dp),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_item_select_all)) },
                                    onClick = { showMenu = false; onSelectAll() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_item_invert_selection)) },
                                    onClick = { showMenu = false; onInvertSelection() }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .verticalScrollbar(listState),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = apps, key = { it.packageName }) { app ->
                    val checked = selectedPackages.contains(app.packageName)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        AppListItem(
                            appName = app.appName,
                            packageName = app.packageName,
                            icon = app.appIcon,
                            checked = checked,
                            onCheckedChange = { onToggleApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}