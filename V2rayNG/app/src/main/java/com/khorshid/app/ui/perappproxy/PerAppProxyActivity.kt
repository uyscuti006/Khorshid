package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.Utils

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

class PerAppProxyActivity : BaseComponentActivity() {

    private val viewModel: PerAppProxyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val blacklist by viewModel.blacklist.collectAsStateWithLifecycle()
        val perAppProxyEnabled by viewModel.perAppProxyEnabled.collectAsStateWithLifecycle()
        val bypassApps by viewModel.bypassApps.collectAsStateWithLifecycle()

        PerAppProxyScreen(
            apps = apps,
            isLoading = isLoading,
            blacklist = blacklist,
            perAppProxyEnabled = perAppProxyEnabled,
            bypassApps = bypassApps,
            onBackClick = { finish() },
            onPerAppProxyChanged = { viewModel.setPerAppProxyEnabled(it) },
            onBypassAppsChanged = { viewModel.setBypassAppsEnabled(it) },
            onInfoClick = {
                toastInfo(R.string.summary_pref_per_app_proxy)
            },
            onToggleApp = { viewModel.toggle(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() },
            onSelectProxyAuto = { viewModel.selectProxyAppAuto(this) },
            onImportProxyApp = {
                val content = Utils.getClipboard(applicationContext)
                viewModel.importProxyApp(content, this)
            },
            onExportProxyApp = {
                val export = viewModel.exportProxyApp()
                Utils.setClipboard(applicationContext, export)
                toastSuccess(R.string.toast_success)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppProxyScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    blacklist: Set<String>,
    perAppProxyEnabled: Boolean,
    bypassApps: Boolean,
    onBackClick: () -> Unit,
    onPerAppProxyChanged: (Boolean) -> Unit,
    onBypassAppsChanged: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectProxyAuto: () -> Unit,
    onImportProxyApp: () -> Unit,
    onExportProxyApp: () -> Unit
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
                    title = stringResource(R.string.per_app_proxy_settings),
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
                                    painterResource(R.drawable.ic_search_24dp),
                                    contentDescription = stringResource(R.string.menu_item_search),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_more_vert_24dp),
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_item_select_proxy_app)) },
                                    onClick = { showMenu = false; onSelectProxyAuto() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_item_import_proxy_app)) },
                                    onClick = { showMenu = false; onImportProxyApp() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_item_export_proxy_app)) },
                                    onClick = { showMenu = false; onExportProxyApp() }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            ) {
                // کارت سوئیچ‌های اصلی (فعال‌سازی Per-App Proxy و Bypass)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // سوئیچ اول: Per-App Proxy
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.per_app_proxy_settings_enable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = perAppProxyEnabled,
                                modifier = Modifier.scale(0.75f),
                                onCheckedChange = onPerAppProxyChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        // سوئیچ دوم: Bypass Mode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.switch_bypass_apps_mode),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = bypassApps,
                                modifier = Modifier.scale(0.75f),
                                onCheckedChange = onBypassAppsChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        // آیکون راهنما (Info)
                        IconButton(onClick = onInfoClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_about_24dp),
                                contentDescription = stringResource(R.string.summary_pref_per_app_proxy),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // لیست برنامه‌ها داخل کارت کلی یا به صورت آیتم‌های مجزا
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScrollbar(listState),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(items = apps, key = { it.packageName }) { app ->
                            val checked = blacklist.contains(app.packageName)
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
}