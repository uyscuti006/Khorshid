package com.v2ray.ang.ui.subscription

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.QRCodeDialog
import com.v2ray.ang.compose.ReorderableListItem
import com.v2ray.ang.compose.SelectListDialog
import com.v2ray.ang.compose.SettingsSwitchItem
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBF3FE),
    onPrimaryContainer = Color(0xFF1E293B),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF183A5D),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF8E94A0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF23252E),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFE67E22),
    onTertiary = Color.White,
    background = Color(0xFF0F1115),
    onBackground = Color.White,
    surface = Color(0xFF1E2026),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF23252E),
    onSurfaceVariant = Color(0xFF8E94A0),
    error = Color(0xFFEF4444),
    onError = Color.White
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)

class SubSettingActivity : BaseComponentActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        val isLoading by viewModel.isUpdating.collectAsStateWithLifecycle()
        SubSettingScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            onBackClick = { finish() },
            onAddClick = { startActivity(Intent(this, SubEditActivity::class.java)) },
            onSubUpdate = { viewModel.updateSubscriptions() },
            onEditSub = { subId ->
                startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
            },
            onRemoveSub = { subId -> removeSub(subId) },
            onShareQRCode = { url -> QRCodeDecoder.createQRCode(url) },
            onShareClipboard = { url ->
                Utils.setClipboard(this, url)
                toast(getString(R.string.toast_success))
            },
            shareSubMethodEntries = resources.getStringArray(R.array.share_sub_method).toList()
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun removeSub(subId: String) {
        viewModel.remove(subId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubUpdate: () -> Unit,
    onEditSub: (String) -> Unit,
    onRemoveSub: (String) -> Unit,
    onShareQRCode: (String) -> Bitmap?,
    onShareClipboard: (String) -> Unit,
    shareSubMethodEntries: List<String>
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val subscriptions by viewModel.subsFlow.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    val confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showQRCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.swap(from.index, to.index)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.title_sub_setting),
                    onBackClick = onBackClick,
                    isLoading = isLoading,
                    actions = {
                        IconButton(onClick = onAddClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add_24dp),
                                contentDescription = stringResource(R.string.menu_item_add_config),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showUpdateDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_restore_24dp),
                                contentDescription = stringResource(R.string.title_sub_update),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScrollbar(lazyListState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = subscriptions,
                    key = { _, item -> item.guid }
                ) { _, subCache ->
                    ReorderableItem(reorderableState, key = subCache.guid) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isDragging) 6.dp else 1.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = subCache.subscription.remarks,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (subCache.subscription.url.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = subCache.subscription.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = Utils.formatTimestamp(subCache.subscription.lastUpdated),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (subCache.subscription.url.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    shareTarget = Pair(subCache.guid, subCache.subscription.url)
                                                }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_share_24dp),
                                                        contentDescription = "Share",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { onEditSub(subCache.guid) }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_edit_24dp),
                                                    contentDescription = "Edit",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = {
                                                if (confirmRemove) removeTarget = subCache.guid
                                                else onRemoveSub(subCache.guid)
                                            }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_delete_24dp),
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = subCache.subscription.enabled,
                                            onCheckedChange = { checked ->
                                                val updated = subCache.subscription.copy()
                                                updated.enabled = checked
                                                viewModel.update(subCache.guid, updated)
                                            },
                                            modifier = Modifier.scale(0.85f),
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary
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

        if (shareTarget != null) {
            val (_, url) = shareTarget!!
            SelectListDialog(
                options = shareSubMethodEntries,
                onSelected = { index, _ ->
                    shareTarget = null
                    when (index) {
                        0 -> showQRCodeBitmap = onShareQRCode(url)
                        1 -> onShareClipboard(url)
                    }
                },
                onDismiss = { shareTarget = null }
            )
        }

        if (showQRCodeBitmap != null) {
            QRCodeDialog(
                bitmap = showQRCodeBitmap,
                onDismiss = { showQRCodeBitmap = null }
            )
        }

        if (removeTarget != null) {
            ConfirmDialog(
                message = stringResource(R.string.del_config_comfirm),
                confirmText = stringResource(android.R.string.ok),
                dismissText = stringResource(android.R.string.cancel),
                onConfirm = {
                    onRemoveSub(removeTarget!!)
                    removeTarget = null
                },
                onDismiss = { removeTarget = null }
            )
        }

        if (showUpdateDialog) {
            var autoTestAfterUpdateSubscription by rememberMmkvBool(
                AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false
            )
            var autoRemoveInvalidAfterTest by rememberMmkvBool(
                AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false
            )
            var autoSortAfterTest by rememberMmkvBool(
                AppConfig.PREF_AUTO_SORT_AFTER_TEST, false
            )

            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = stringResource(R.string.title_sub_update),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column {
                        SettingsSwitchItem(
                            title = stringResource(R.string.title_pref_auto_test_after_update_subscription),
                            summary = stringResource(R.string.summary_pref_auto_test_after_update_subscription),
                            checked = autoTestAfterUpdateSubscription,
                            onCheckedChange = { autoTestAfterUpdateSubscription = it }
                        )
                        SettingsSwitchItem(
                            title = stringResource(R.string.title_pref_auto_remove_invalid_after_test),
                            summary = stringResource(R.string.summary_pref_auto_remove_invalid_after_test),
                            checked = autoRemoveInvalidAfterTest,
                            onCheckedChange = { autoRemoveInvalidAfterTest = it }
                        )
                        SettingsSwitchItem(
                            title = stringResource(R.string.title_pref_auto_sort_after_test),
                            summary = stringResource(R.string.summary_pref_auto_sort_after_test),
                            checked = autoSortAfterTest,
                            onCheckedChange = { autoSortAfterTest = it }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUpdateDialog = false
                        onSubUpdate()
                    }) {
                        Text(
                            text = stringResource(android.R.string.ok),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}