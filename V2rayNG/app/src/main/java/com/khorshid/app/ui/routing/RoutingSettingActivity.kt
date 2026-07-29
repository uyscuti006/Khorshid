package com.v2ray.ang.ui.routing

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ReorderableListItem
import com.v2ray.ang.compose.SelectListDialog
import com.v2ray.ang.compose.SettingsListItem
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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

class RoutingSettingActivity : HelperBaseComponentActivity() {
    private val viewModel: RoutingSettingsViewModel by viewModels()
    private val domainStrategyState = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domainStrategyState.value = getDomainStrategy()
    }

    @Composable
    override fun ScreenContent() {
        RoutingSettingScreen(
            viewModel = viewModel,
            domainStrategyState = domainStrategyState,
            onBackClick = { finish() },
            onAddRule = { startActivity(Intent(this, RoutingEditActivity::class.java)) },
            onEditRule = { position ->
                startActivity(Intent(this, RoutingEditActivity::class.java).putExtra("position", position))
            },
            onDomainStrategySelected = { value ->
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                domainStrategyState.value = value
            },
            onImportPredefined = { index -> importPredefined(index) },
            onImportClipboard = { importFromClipboard() },
            onImportQRcode = { importQRcode() },
            onExportClipboard = { export2Clipboard() }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun getDomainStrategy(): String {
        val strategies = resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: strategies.first()
    }

    private fun importPredefined(index: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, index)
                launch(Dispatchers.Main) {
                    viewModel.reload()
                    toastSuccess(R.string.toast_success)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = try {
            Utils.getClipboard(this)
        } catch (e: Exception) {
            toastError(R.string.toast_failure)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = SettingsManager.resetRoutingRulesets(clipboard)
            withContext(Dispatchers.Main) {
                if (result) {
                    viewModel.reload()
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(scanResult)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            viewModel.reload()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
        }
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.toast_failure)
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.toast_success)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingSettingScreen(
    viewModel: RoutingSettingsViewModel,
    domainStrategyState: MutableStateFlow<String>,
    onBackClick: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onDomainStrategySelected: (String) -> Unit,
    onImportPredefined: (Int) -> Unit,
    onImportClipboard: () -> Unit,
    onImportQRcode: () -> Unit,
    onExportClipboard: () -> Unit
) {
    val rulesets by viewModel.rulesetsFlow.collectAsStateWithLifecycle()
    val domainStrategy by domainStrategyState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }

    val domainStrategies = stringArrayResource(R.array.routing_domain_strategy).toList()
    val presetRulesets = stringArrayResource(R.array.preset_rulesets).toList()

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.swap(from.index - 2, to.index - 2) // با توجه به کارت‌های بالای لیست
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
                    title = stringResource(R.string.routing_settings_title),
                    onBackClick = onBackClick,
                    actions = {
                        IconButton(onClick = onAddRule) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add_24dp),
                                contentDescription = stringResource(R.string.routing_settings_add_rule),
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                                    text = { Text(stringResource(R.string.routing_settings_import_predefined_rulesets)) },
                                    onClick = { showMenu = false; showPresetDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_clipboard)) },
                                    onClick = { showMenu = false; onImportClipboard() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_qrcode)) },
                                    onClick = { showMenu = false; onImportQRcode() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.routing_settings_export_rulesets_to_clipboard)) },
                                    onClick = { showMenu = false; onExportClipboard() }
                                )
                            }
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
                    .imePadding()
                    .verticalScrollbar(lazyListState),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // کارت اول: تنظیمات Domain Strategy
                item(key = "domain_strategy") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        SettingsListItem(
                            title = stringResource(R.string.routing_settings_domain_strategy),
                            entries = domainStrategies,
                            values = domainStrategies,
                            selectedValue = domainStrategy,
                            onSelected = { onDomainStrategySelected(it) }
                        )
                    }
                }

                // عنوان بخش قوانین مسیریابی
                item(key = "rules_title") {
                    Text(
                        text = stringResource(R.string.routing_settings_rule_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }

                // لیست قوانین به‌صورت کارت‌های گوشه‌گرد و قابلیت جابه‌جایی (Reorderable)
                itemsIndexed(
                    items = rulesets,
                    key = { _, ruleset -> ruleset.id }
                ) { index, ruleset ->
                    ReorderableItem(reorderableState, key = ruleset.id) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            RoutingRulesetItem(
                                ruleset = ruleset,
                                onEdit = { onEditRule(index) },
                                onEnabledChange = { checked ->
                                    val updated = ruleset.copy(enabled = checked)
                                    viewModel.update(index, updated)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showPresetDialog) {
            SelectListDialog(
                title = stringResource(R.string.routing_settings_import_predefined_rulesets),
                options = presetRulesets,
                onSelected = { index, _ ->
                    showPresetDialog = false
                    onImportPredefined(index)
                },
                onDismiss = { showPresetDialog = false }
            )
        }
    }
}

@Composable
private fun RoutingRulesetItem(
    ruleset: RulesetItem,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ruleset.remarks.takeIf { !it.isNullOrBlank() } ?: "Rule",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ruleset.locked == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_lock_24dp),
                            contentDescription = "Locked",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val domainIpInfo = (ruleset.domain ?: ruleset.ip ?: ruleset.process ?: ruleset.port)?.toString() ?: ""
                if (domainIpInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = domainIpInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!ruleset.outboundTag.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ruleset.outboundTag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_24dp),
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = ruleset.enabled ?: false,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.scale(0.75f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}