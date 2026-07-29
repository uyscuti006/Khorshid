package com.v2ray.ang.ui.subscription

import android.os.Bundle
import android.text.TextUtils
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.FormDropdownField
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.compose.SettingsSwitchItem
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

class SubEditActivity : BaseComponentActivity() {
    private val editSubId by lazy { intent.getStringExtra("subId").orEmpty() }
    private lateinit var suggestions: List<String>
    private lateinit var subItem: SubscriptionItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        suggestions = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(
                EConfigType.CUSTOM,
                EConfigType.POLICYGROUP,
                EConfigType.PROXYCHAIN,
            )
        )
        subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()
    }

    @Composable
    override fun ScreenContent() {
        SubEditScreen(
            editSubId = editSubId,
            initial = subItem,
            profileSuggestions = suggestions,
            onBackClick = { finish() },
            onSave = { saveServer(it) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(subItem: SubscriptionItem): Boolean {
        if (TextUtils.isEmpty(subItem.remarks)) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        if (subItem.url.isNotEmpty()) {
            if (!Utils.isValidUrl(subItem.url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            if (!Utils.isValidSubUrl(subItem.url)) {
                toast(R.string.toast_insecure_url_protocol)
                if (!subItem.allowInsecureUrl) {
                    return false
                }
            }
        }

        if (subItem.autoUpdate && subItem.updateInterval < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
            toast(R.string.toast_invalid_update_interval)
            return false
        }

        MmkvManager.encodeSubscription(editSubId, subItem)
        SubscriptionUpdater.syncOne(subId = editSubId)
        SettingsChangeManager.makeSetupGroupTab()
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editSubId.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                SettingsManager.removeSubscriptionWithDefault(editSubId)
                SettingsChangeManager.makeSetupGroupTab()
                launch(Dispatchers.Main) { finish() }
            }
        }
        return true
    }
}

@Composable
fun SubEditScreen(
    editSubId: String,
    initial: SubscriptionItem,
    profileSuggestions: List<String>,
    onBackClick: () -> Unit,
    onSave: (SubscriptionItem) -> Boolean,
    onDelete: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    var remarks by rememberSaveable { mutableStateOf(initial.remarks.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(initial.url.orEmpty()) }
    var userAgent by rememberSaveable { mutableStateOf(initial.userAgent.orEmpty()) }
    var requestHeaders by rememberSaveable { mutableStateOf(initial.requestHeaders.orEmpty()) }
    var filter by rememberSaveable { mutableStateOf(initial.filter ?: "") }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var autoUpdate by rememberSaveable { mutableStateOf(initial.autoUpdate) }
    var updateInterval by rememberSaveable { mutableStateOf(initial.updateInterval.toString()) }
    var allowInsecureUrl by rememberSaveable { mutableStateOf(initial.allowInsecureUrl) }
    var prevProfile by rememberSaveable { mutableStateOf(initial.prevProfile ?: "") }
    var nextProfile by rememberSaveable { mutableStateOf(initial.nextProfile ?: "") }

    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)
    val scrollState = rememberScrollState()

    fun buildSubItem(): SubscriptionItem {
        val subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()
        subItem.remarks = remarks
        subItem.url = url
        subItem.userAgent = userAgent
        subItem.requestHeaders = requestHeaders
        subItem.filter = filter
        subItem.enabled = enabled
        subItem.autoUpdate = autoUpdate
        subItem.updateInterval = updateInterval.toLongOrNull() ?: 0L
        subItem.prevProfile = prevProfile
        subItem.nextProfile = nextProfile
        subItem.allowInsecureUrl = allowInsecureUrl
        return subItem
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
                    actions = {
                        if (editSubId.isNotEmpty()) {
                            IconButton(onClick = {
                                if (confirmRemove) showDeleteConfirm = true else onDelete()
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = stringResource(R.string.menu_item_del_config),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = { onSave(buildSubItem()) }) {
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                FormTextField(
                    label = stringResource(R.string.sub_setting_remarks),
                    value = remarks,
                    onValueChange = { remarks = it }
                )

                FormTextField(
                    label = stringResource(R.string.sub_setting_url),
                    value = url,
                    onValueChange = { url = it }
                )

                FormTextField(
                    label = stringResource(R.string.sub_setting_user_agent),
                    value = userAgent,
                    onValueChange = { userAgent = it }
                )

                FormTextField(
                    label = stringResource(R.string.sub_setting_request_headers),
                    value = requestHeaders,
                    onValueChange = { requestHeaders = it }
                )

                FormTextField(
                    label = stringResource(R.string.sub_setting_filter),
                    value = filter,
                    onValueChange = { filter = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSwitchItem(
                    title = stringResource(R.string.sub_setting_enable),
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )

                SettingsSwitchItem(
                    title = stringResource(R.string.sub_auto_update),
                    checked = autoUpdate,
                    onCheckedChange = { autoUpdate = it }
                )

                FormTextField(
                    label = stringResource(R.string.title_pref_auto_update_interval),
                    value = updateInterval,
                    onValueChange = { updateInterval = it },
                    keyboardType = KeyboardType.Number
                )

                SettingsSwitchItem(
                    title = stringResource(R.string.sub_allow_insecure_url),
                    checked = allowInsecureUrl,
                    onCheckedChange = { allowInsecureUrl = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                FormDropdownField(
                    label = stringResource(R.string.sub_setting_pre_profile),
                    placeholder = stringResource(R.string.sub_setting_pre_profile_tip),
                    value = prevProfile,
                    options = profileSuggestions,
                    onValueChange = { prevProfile = it },
                    editable = true
                )

                FormDropdownField(
                    label = stringResource(R.string.sub_setting_next_profile),
                    placeholder = stringResource(R.string.sub_setting_pre_profile_tip),
                    value = nextProfile,
                    options = profileSuggestions,
                    onValueChange = { nextProfile = it },
                    editable = true
                )

                Spacer(modifier = Modifier.height(36.dp))
            }
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