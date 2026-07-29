package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.FormDropdownField
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity

// 🎨 سیستم رنگی یکپارچه با BaseServerActivity و ServerCustomConfigActivity
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

class ServerGroupActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }
    private val subIds = mutableListOf<String>()
    private val subDisplay = mutableListOf<String>()

    private lateinit var initialRemarks: String
    private lateinit var initialFilter: String
    private var initialType: Int = 0
    private var initialSubIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)
        populateSubscriptionSpinner()

        initialRemarks = config?.remarks ?: ""
        initialFilter = config?.policyGroupFilter ?: ""
        initialType = config?.policyGroupType?.toIntOrNull() ?: 0
        initialSubIndex = if (config != null) {
            subIds.indexOf(config.policyGroupSubscriptionId ?: "").let { if (it >= 0) it else 0 }
        } else if (subscriptionId.isNotNullEmpty()) {
            subIds.indexOf(subscriptionId).let { if (it >= 0) it else 0 }
        } else 0
    }

    @Composable
    override fun ScreenContent() {
        ServerGroupScreen(
            editGuid = editGuid,
            isRunning = isRunning,
            subDisplay = subDisplay,
            initialRemarks = initialRemarks,
            initialFilter = initialFilter,
            initialType = initialType,
            initialSubIndex = initialSubIndex,
            onBackClick = { finish() },
            onSave = { remarks, filter, typeIdx, subIdx -> saveServer(remarks, filter, typeIdx, subIdx) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(
        remarks: String,
        filter: String,
        typeIdx: Int,
        subIdx: Int
    ): Boolean {
        if (remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return false
        }

        val config =
            MmkvManager.decodeServerConfig(editGuid)
                ?: ProfileItem.create(EConfigType.POLICYGROUP)

        config.remarks = remarks.trim()
        config.policyGroupFilter = filter.trim()
        config.policyGroupType = typeIdx.toString()
        config.policyGroupSubscriptionId =
            subIds.getOrNull(subIdx)

        if (
            config.subscriptionId.isEmpty() &&
            !subscriptionId.isNullOrEmpty()
        ) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        val typeDisplay =
            stringArrayPolicyGroupType()
                .getOrNull(typeIdx)
                .orEmpty()

        config.description = buildString {
            append(typeDisplay)
            append(" - ")
            append(subDisplay.getOrNull(subIdx).orEmpty())
            append(" - ")
            append(config.policyGroupFilter)
        }

        val savedGuid = MmkvManager.encodeServerConfig(
            editGuid,
            config
        )

        toastSuccess(R.string.toast_success)

        ProfileEditorResult.run {
            finishSaved(
                guid = savedGuid,
                restartService = isRunning
            )
        }

        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isEmpty()) {
            return false
        }

        if (editGuid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return false
        }

        MmkvManager.removeServer(editGuid)

        ProfileEditorResult.run {
            finishDeleted(editGuid)
        }

        return true
    }

    private fun populateSubscriptionSpinner() {
        val subs = MmkvManager.decodeSubscriptions()
        subIds.clear()
        subDisplay.clear()
        subDisplay.add(getString(R.string.filter_config_all))
        subIds.add("")
        subs.forEach { sub ->
            val name = when {
                sub.subscription.remarks.isNotBlank() -> sub.subscription.remarks
                else -> sub.guid
            }
            subDisplay.add(name)
            subIds.add(sub.guid)
        }
    }

    private fun stringArrayPolicyGroupType(): Array<String> =
        resources.getStringArray(R.array.policy_group_type)
}

@Composable
fun ServerGroupScreen(
    editGuid: String,
    isRunning: Boolean,
    subDisplay: List<String>,
    initialRemarks: String,
    initialFilter: String,
    initialType: Int,
    initialSubIndex: Int,
    onBackClick: () -> Unit,
    onSave: (String, String, Int, Int) -> Boolean,
    onDelete: () -> Unit
) {
    val typeEntries = stringArrayResource(R.array.policy_group_type).toList()

    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var typeValue by rememberSaveable { mutableStateOf(typeEntries.getOrNull(initialType).orEmpty()) }
    var subValue by rememberSaveable { mutableStateOf(subDisplay.getOrNull(initialSubIndex).orEmpty()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val showDelete = editGuid.isNotEmpty() && !isRunning

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
                    title = EConfigType.POLICYGROUP.toString(),
                    onBackClick = onBackClick,
                    actions = {
                        if (showDelete) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = stringResource(R.string.menu_item_del_config),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = {
                            val typeIdx = typeEntries.indexOf(typeValue).coerceAtLeast(0)
                            val subIdx = subDisplay.indexOf(subValue).coerceAtLeast(0)
                            onSave(remarks, filter, typeIdx, subIdx)
                        }) {
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // کارت اول: مشخصات اصلی (نام و نوع گروه)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FormTextField(
                                label = stringResource(R.string.server_lab_remarks),
                                value = remarks,
                                onValueChange = { remarks = it }
                            )
                            FormDropdownField(
                                label = stringResource(R.string.title_policy_group_type),
                                value = typeValue,
                                options = typeEntries,
                                onValueChange = { typeValue = it }
                            )
                        }
                    }
                }

                // کارت دوم: تنظیمات سابسکریپشن و فیلترها
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FormDropdownField(
                                label = stringResource(R.string.title_policy_group_subscription_id),
                                value = subValue,
                                options = subDisplay,
                                onValueChange = { subValue = it }
                            )
                            FormTextField(
                                label = stringResource(R.string.title_policy_group_subscription_filter),
                                value = filter,
                                onValueChange = { filter = it }
                            )
                        }
                    }
                }
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