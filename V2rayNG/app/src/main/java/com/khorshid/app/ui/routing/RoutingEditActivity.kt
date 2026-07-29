package com.v2ray.ang.ui.routing

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.FormDropdownField
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.compose.SettingsSwitchItem
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.apppicker.AppPickerActivity
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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

class RoutingEditActivity : BaseComponentActivity() {
    private val position by lazy { intent.getIntExtra("position", -1) }

    private var initial: RulesetItem? = null
    private lateinit var outboundSuggestions: List<String>
    private var canUseProcess: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initial = SettingsManager.getRoutingRuleset(position)
        val profileRemarks = SettingsManager.getProfileRemarks()
        outboundSuggestions = (BUILTIN_OUTBOUND_TAGS.toList() + profileRemarks).distinct()
        canUseProcess = SettingsManager.canUseProcessRouting()
    }

    @Composable
    override fun ScreenContent() {
        RoutingEditScreen(
            position = position,
            initial = initial,
            outboundSuggestions = outboundSuggestions,
            canUseProcess = canUseProcess,
            onBackClick = { finish() },
            onSave = { saveServer(it) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(rulesetItem: RulesetItem): Boolean {
        if (rulesetItem.remarks.isNullOrEmpty()) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        if (position < 0 && rulesetItem.id.isEmpty()) {
            rulesetItem.id = UUID.randomUUID().toString()
        }
        SettingsManager.saveRoutingRuleset(position, rulesetItem)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (position >= 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                SettingsManager.removeRoutingRuleset(position)
                withContext(Dispatchers.Main) { finish() }
            }
        }
        return true
    }
}

@Composable
fun RoutingEditScreen(
    position: Int,
    initial: RulesetItem?,
    outboundSuggestions: List<String>,
    canUseProcess: Boolean,
    onBackClick: () -> Unit,
    onSave: (RulesetItem) -> Boolean,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var remarks by rememberSaveable { mutableStateOf(initial?.remarks ?: "") }
    var locked by rememberSaveable { mutableStateOf(initial?.locked == true) }
    var domain by rememberSaveable { mutableStateOf(initial?.domain?.joinToString(",") ?: "") }
    var ip by rememberSaveable { mutableStateOf(initial?.ip?.joinToString(",") ?: "") }
    var processText by rememberSaveable { mutableStateOf(initial?.process?.joinToString(",") ?: "") }
    var protocol by rememberSaveable { mutableStateOf(initial?.protocol?.joinToString(",") ?: "") }
    var network by rememberSaveable { mutableStateOf(initial?.network ?: "") }
    var port by rememberSaveable { mutableStateOf(initial?.port ?: "") }
    var outboundTag by rememberSaveable {
        mutableStateOf(initial?.outboundTag ?: BUILTIN_OUTBOUND_TAGS.first())
    }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val processPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedPackages = AppPickerActivity.getSelectedPackages(result.data)
            processText = selectedPackages.joinToString(",")
        }
    }

    fun buildRuleset(): RulesetItem {
        val rulesetItem = SettingsManager.getRoutingRuleset(position) ?: RulesetItem()
        rulesetItem.apply {
            this.remarks = remarks
            this.locked = locked
            this.domain = domain.nullIfBlank()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
            this.ip = ip.nullIfBlank()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
            this.process = processText.nullIfBlank()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
            this.protocol = protocol.nullIfBlank()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
            this.port = port.nullIfBlank()
            this.network = network.nullIfBlank()
            this.outboundTag = outboundTag.trim().ifEmpty { TAG_PROXY }
        }
        return rulesetItem
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
                    title = stringResource(R.string.routing_settings_rule_title),
                    onBackClick = onBackClick,
                    actions = {
                        if (position >= 0) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = stringResource(R.string.menu_item_del_config),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = { onSave(buildRuleset()) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fab_check),
                                contentDescription = stringResource(R.string.menu_item_save_config),
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                    .verticalScroll(scrollState)
                    .verticalScrollbar(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // کارت اول: مشخصات اصلی (نام و قفل)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FormTextField(
                            label = stringResource(R.string.sub_setting_remarks),
                            value = remarks,
                            onValueChange = { remarks = it }
                        )
                        SettingsSwitchItem(
                            title = stringResource(R.string.routing_settings_locked),
                            checked = locked,
                            onCheckedChange = { locked = it }
                        )
                    }
                }

                // کارت دوم: شروط و فیلترهای قانون (دامنه، IP، پروسس، پورت، پروتکل و شبکه)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FormTextField(
                            label = stringResource(R.string.routing_settings_domain),
                            placeholder = stringResource(R.string.routing_settings_tips),
                            value = domain,
                            onValueChange = { domain = it }
                        )
                        FormTextField(
                            label = stringResource(R.string.routing_settings_ip),
                            placeholder = stringResource(R.string.routing_settings_tips),
                            value = ip,
                            onValueChange = { ip = it }
                        )
                        FormTextField(
                            label = stringResource(R.string.routing_settings_process),
                            placeholder = stringResource(R.string.routing_settings_tips),
                            value = processText,
                            onValueChange = { processText = it },
                            enabled = canUseProcess
                        )
                        if (canUseProcess) {
                            TextButton(
                                onClick = {
                                    val current = processText
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .distinct()
                                    processPickerLauncher.launch(
                                        AppPickerActivity.createIntent(
                                            context = context,
                                            selectedPackages = current,
                                            title = context.getString(R.string.routing_settings_process_select)
                                        )
                                    )
                                },
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_per_apps_24dp),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.routing_settings_process_select),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        FormTextField(
                            label = stringResource(R.string.routing_settings_port),
                            value = port,
                            onValueChange = { port = it }
                        )
                        FormTextField(
                            label = stringResource(R.string.routing_settings_protocol),
                            placeholder = stringResource(R.string.routing_settings_protocol_tip),
                            value = protocol,
                            onValueChange = { protocol = it }
                        )
                        FormTextField(
                            label = stringResource(R.string.routing_settings_network),
                            placeholder = stringResource(R.string.routing_settings_network_tip),
                            value = network,
                            onValueChange = { network = it }
                        )
                    }
                }

                // کارت سوم: خروجی هدف (Outbound Tag)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        FormDropdownField(
                            label = stringResource(R.string.routing_settings_outbound_tag),
                            value = outboundTag,
                            options = outboundSuggestions,
                            onValueChange = { outboundTag = it },
                            editable = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (showDeleteConfirm) {
                ConfirmDialog(
                    message = stringResource(R.string.del_config_comfirm),
                    confirmText = stringResource(android.R.string.ok),
                    dismissText = stringResource(android.R.string.cancel),
                    onConfirm = onDelete,
                    onDismiss = { showDeleteConfirm = false }
                )
            }
        }
    }
}