package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget

enum class ConfigCategory(val label: String) {
    ALL("ALL"),
    BPB("BPB"),
    NAHAN("NAHAN"),
    OTHER("OTHER")
}

data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val statusText: String = "",
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null,
    val currentPingDelay: Long = -1L,
    val selectedCategory: ConfigCategory = ConfigCategory.ALL,
    val isConnecting: Boolean = false,
    val isDarkMode: Boolean = false,
    val hasUserToggledTheme: Boolean = false,
    // این ۳ خط اضافه شدند:
    val downloadSpeedText: String = "0 B/s",
    val uploadSpeedText: String = "0 B/s",
    val connectionDurationText: String = "00:00:00"
)

sealed interface MainAction {
    data object Initialize : MainAction
    data object ConnectFastest : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.v2ray.ang.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class SwapServer(val fromIndex: Int, val toIndex: Int) : MainAction

    data class ShareQRCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data object DismissQRCodeDialog : MainAction

    data object CancelConnect : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data class LocateHandled(val target: LocateTarget) : MainAction

    data class SelectCategory(val category: ConfigCategory) : MainAction

    data object ToggleTheme : MainAction
}
