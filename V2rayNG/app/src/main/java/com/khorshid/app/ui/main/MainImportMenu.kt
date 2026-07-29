package com.v2ray.ang.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.SelectListDialog
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType

@Composable
fun ImportMenuContent(
    onAction: (MainAction) -> Unit,
    onDismiss: () -> Unit = {}
) {
    // 📸 روش‌های وارد کردن رایج (QR، کلپ‌بورد، فایل)
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_qrcode)) },
        leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.ImportQRcode) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_clipboard)) },
        leadingIcon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.ImportClipboard) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_local)) },
        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.ImportConfigLocal) }
    )

    AppDivider()

    // 🔀 ساخت دستی گروه‌های پیچیده
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_policy_group)) },
        leadingIcon = { Icon(Icons.Outlined.Route, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.ImportManually(EConfigType.POLICYGROUP.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_proxy_chain)) },
        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.ImportManually(EConfigType.PROXYCHAIN.value)) }
    )

    AppDivider()

    // 🛠️ ساخت دستی انواع پروتکل‌ها
    val manualConfigs = listOf(
        R.string.menu_item_import_config_manually_vmess to EConfigType.VMESS,
        R.string.menu_item_import_config_manually_vless to EConfigType.VLESS,
        R.string.menu_item_import_config_manually_ss to EConfigType.SHADOWSOCKS,
        R.string.menu_item_import_config_manually_socks to EConfigType.SOCKS,
        R.string.menu_item_import_config_manually_http to EConfigType.HTTP,
        R.string.menu_item_import_config_manually_trojan to EConfigType.TROJAN,
        R.string.menu_item_import_config_manually_wireguard to EConfigType.WIREGUARD,
        R.string.menu_item_import_config_manually_hysteria2 to EConfigType.HYSTERIA2
    )

    manualConfigs.forEach { (labelRes, configType) ->
        DropdownMenuItem(
            text = { Text(stringResource(labelRes)) },
            leadingIcon = { Icon(Icons.Outlined.AddCard, contentDescription = null) },
            onClick = { onDismiss(); onAction(MainAction.ImportManually(configType.value)) }
        )
    }
}

@Composable
fun MoreMenuContent(
    onAction: (MainAction) -> Unit,
    onDelAllConfig: () -> Unit,
    onDelDuplicateConfig: () -> Unit,
    onDelInvalidConfig: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    // ⚡ عملیات سرویس و اشتراک
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_service_restart)) },
        leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.RestartService) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_sub_update)) },
        leadingIcon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.UpdateSubscriptions) }
    )

    AppDivider()

    // 📊 تست پینگ و مرتب‌سازی
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_real_ping_all_server)) },
        leadingIcon = { Icon(Icons.Outlined.Speed, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.TestAllServers) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_locate_selected_config)) },
        leadingIcon = { Icon(Icons.Outlined.MyLocation, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.LocateSelectedServer) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_sort_by_test_results)) },
        leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.SortByTestResults) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_export_all)) },
        leadingIcon = { Icon(Icons.Outlined.IosShare, contentDescription = null) },
        onClick = { onDismiss(); onAction(MainAction.ExportAll) }
    )

    AppDivider()

    // 🧹 پاک‌سازی و حذف کانفیگ‌ها
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_del_duplicate_config)) },
        leadingIcon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
        onClick = { onDismiss(); onDelDuplicateConfig() }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_del_invalid_config)) },
        leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, contentDescription = null) },
        onClick = { onDismiss(); onDelInvalidConfig() }
    )

    // 🔴 گزینه قرمز رنگ برای حذف کلیه کانفیگ‌ها (جهت جلوگیری از لمس ناخواسته)
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(R.string.title_del_all_config),
                color = MaterialTheme.colorScheme.error
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error
        ),
        onClick = { onDismiss(); onDelAllConfig() }
    )
}

@Composable
fun ShareMethodDialog(
    guid: String,
    profile: ProfileItem,
    more: Boolean,
    shareMethodEntries: List<String>,
    shareMethodMoreEntries: List<String>,
    onDismiss: () -> Unit,
    onAction: (MainAction) -> Unit
) {
    val isCustom = profile.configType.isComplexType()

    val (shareOptions, skip) = if (more) {
        val options = if (isCustom) shareMethodMoreEntries.takeLast(3) else shareMethodMoreEntries
        options to if (isCustom) 2 else 0
    } else {
        val options = if (isCustom) shareMethodEntries.takeLast(1) else shareMethodEntries
        options to if (isCustom) 2 else 0
    }

    SelectListDialog(
        options = shareOptions,
        onSelected = { index, _ ->
            onDismiss()
            when (index + skip) {
                0 -> onAction(MainAction.ShareQRCode(guid))
                1 -> onAction(MainAction.ShareClipboard(guid))
                2 -> onAction(MainAction.ShareFullContent(guid))
                3 -> onAction(MainAction.EditServer(guid, profile))
                4 -> onAction(MainAction.RemoveServer(guid))
            }
        },
        onDismiss = onDismiss
    )
}