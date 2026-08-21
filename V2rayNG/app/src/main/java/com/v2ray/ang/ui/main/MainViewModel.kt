package com.v2ray.ang.ui.main

import android.app.Application
import android.net.TrafficStats
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.reachability.IpScanPersistence
import com.v2ray.ang.reachability.KhorshidConfigGenerator
import com.v2ray.ang.handler.AutoFailoverManager
import com.v2ray.ang.handler.KillSwitchManager
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.CipherSuitesManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import com.v2ray.ang.extension.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val DEFAULT_SUBSCRIPTION_URL = "https://raw.githubusercontent.com/uyscuti006/vpn-public-configs/main/sub.txt"
        private const val DEFAULT_SUBSCRIPTION_REMARKS = "Khorshid Configs"
        private const val PREF_LAST_CATEGORY = "pref_last_category"
        private const val PREF_APP_THEME = "pref_app_theme"
    }

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val initialCategory: ConfigCategory = run {
        val savedName = MmkvManager.decodeSettingsString(PREF_LAST_CATEGORY, ConfigCategory.ALL.name)
        if (savedName.isNullOrEmpty()) ConfigCategory.ALL
        else runCatching { ConfigCategory.valueOf(savedName) }.getOrDefault(ConfigCategory.ALL)
    }

    private val savedThemeMode: String = MmkvManager.decodeSettingsString(PREF_APP_THEME, "system") ?: "system"

    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay(),
            selectedCategory = initialCategory,
            isDarkMode = savedThemeMode == "dark",
            hasUserToggledTheme = savedThemeMode != "system",
            isCipherSuitesEnabled = CipherSuitesManager.isEnabled()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _requestConnectAfterPick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val connectRequest: SharedFlow<Unit> = _requestConnectAfterPick.asSharedFlow()

    private val _restartConnectRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restartConnectRequest: SharedFlow<Unit> = _restartConnectRequest.asSharedFlow()

    private val _disconnectRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val disconnectRequest: SharedFlow<Unit> = _disconnectRequest.asSharedFlow()

    @Volatile
    private var keywordFilter: String = ""
    private var filterJob: Job? = null

    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupPageFlows = ConcurrentHashMap<String, MutableStateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val serverOrderPersistenceJobs = mutableMapOf<String, Job>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null
    private var connectJob: Job? = null
    private var connectionTimerJob: Job? = null
    private var connectedTimestamp: Long = 0L
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSpeedCheckTimestamp: Long = 0L
    private var initialRxBytes = 0L
    private var initialTxBytes = 0L

    @Volatile
    private var testingGroupId: String? = null
    @Volatile
    private var skipRestoreOnNextStop = false

    private val initialPageReady = CompletableDeferred<Unit>()

    fun getCleanIpGroupId(): String? {
        val subs = dataSource.getSubscriptions()
        return subs.find { it.subscription.remarks == KhorshidConfigGenerator.CLEAN_IP_GROUP_NAME }?.guid
    }

    fun getDefaultGroupId(): String? {
        val subs = dataSource.getSubscriptions()
        return subs.find { it.subscription.remarks == DEFAULT_SUBSCRIPTION_REMARKS }?.guid
    }

    init {
        collectServiceEvents()
        ensureSubscriptionConfigured()
        setupGroupTab()

        AutoFailoverManager.onFailoverNeeded = {
            _restartConnectRequest.tryEmit(Unit)
        }

        KillSwitchManager.onReconnectNeeded = {
            _restartConnectRequest.tryEmit(Unit)
        }
        KillSwitchManager.onMaxRetriesReached = {
            _uiState.update { it.copy(status = MainStatus.TestProgress("Connection failed. Tap to retry or disable Kill Switch.")) }
        }
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        val wasRunning = _uiState.value.isRunning

        when (event) {
            MainServiceEvent.StateRunning -> {
                updateRunningState(true, clearTestingText = false)
                KillSwitchManager.exitBlockingMode()
            }
            MainServiceEvent.StateNotRunning -> {
                if (!skipRestoreOnNextStop) {
                    val guid = dataSource.getSelectServer()
                    if (!guid.isNullOrBlank()) {
                        CipherSuitesManager.restoreAfterDisconnect(guid)
                    }
                }
                skipRestoreOnNextStop = false

                if (wasRunning && KillSwitchManager.isEnabled()) {
                    _uiState.update { it.copy(isBlocking = true, isRunning = false) }
                    KillSwitchManager.enterBlockingMode(viewModelScope)
                    LogUtil.i(TAG, "VPN stopped — Kill Switch active, entering blocking mode")
                } else {
                    updateRunningState(false, clearTestingText = false)
                    if (wasRunning && AutoFailoverManager.isEnabled()) {
                        LogUtil.i(TAG, "VPN stopped unexpectedly — triggering immediate failover")
                        AutoFailoverManager.triggerImmediateFailover()
                    } else {
                        AutoFailoverManager.cancelHealthCheck()
                    }
                }
            }
            MainServiceEvent.StateStartSuccess -> {
                updateRunningState(true)
                _uiState.update { it.copy(isBlocking = false) }
                KillSwitchManager.exitBlockingMode()
                AutoFailoverManager.startHealthCheck(viewModelScope)
            }

            is MainServiceEvent.StateStartFailure -> {
                updateRunningState(false)
                AutoFailoverManager.cancelHealthCheck()
                if (KillSwitchManager.isEnabled()) {
                    _uiState.update { it.copy(isBlocking = true) }
                }
            }

            MainServiceEvent.StateStopSuccess -> {
                if (KillSwitchManager.isEnabled() && _uiState.value.isBlocking) {
                    AutoFailoverManager.cancelHealthCheck()
                    return
                }
                val guid = dataSource.getSelectServer()
                if (!guid.isNullOrBlank()) {
                    CipherSuitesManager.restoreAfterDisconnect(guid)
                }
                AutoFailoverManager.cancelHealthCheck()
                updateRunningState(false)
            }
            is MainServiceEvent.MeasureDelayResult -> {
                if (uiState.value.isRunning) {
                    val delay = if (event.result.delayMillis == -1L) -3L else event.result.delayMillis
                    _uiState.update { it.copy(status = MainStatus.ConnectionTest(event.result), currentPingDelay = delay) }
                }
            }

            MainServiceEvent.MeasureConfigSuccess -> {
                viewModelScope.launch(ioDispatcher) {
                    val gid = testingGroupId ?: uiState.value.selectedGroupId
                    cacheMutex.withLock { groupDataCache.remove(gid) }
                    updateGroupUi(gid, loadGroup(gid, forceRefresh = true))
                }
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                _uiState.update { it.copy(status = MainStatus.TestProgress(event.progress)) }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                onTestsFinished()
            }
        }
    }

    internal fun formatStatus(status: MainStatus): String = when (status) {
        MainStatus.Disconnected -> dataSource.getString(R.string.connection_not_connected)
        MainStatus.Connected -> dataSource.getString(R.string.connection_connected)
        MainStatus.Testing -> dataSource.getString(R.string.connection_test_testing)
        is MainStatus.TestProgress -> dataSource.getString(
            R.string.connection_running_task_left,
            status.progress
        )
        is MainStatus.ConnectionTest -> formatConnectionTestResult(status.result)
    }

    private fun formatConnectionTestResult(result: ConnectionTestResult): String {
        val status = if (result.delayMillis >= 0) {
            val delay = dataSource.getString(R.string.server_test_delay_value, result.delayMillis)
            dataSource.getString(R.string.connection_test_available, delay)
        } else {
            val detail = result.errorMessage.ifBlank {
                dataSource.getString(R.string.connection_test_empty_message)
            }
            dataSource.getString(R.string.connection_test_error, detail)
        }

        if (result.delayMillis < 0 || (result.country == null && result.ipAddress == null)) {
            return status
        }

        val unknown = dataSource.getString(R.string.value_unknown)
        return "$status\n(${result.country ?: unknown}) ${result.ipAddress ?: unknown}"
    }

    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }
            .asStateFlow()

    private fun mutableServersForGroup(groupId: String): MutableStateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }

    private fun currentServers(): List<ServersCache> =
        mutableServersForGroup(uiState.value.selectedGroupId).value

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            MainAction.CancelTesting -> cancelAllPing()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsAsync()
            MainAction.UpdateSubscriptions -> importConfigViaSub()
            MainAction.ExportAll -> exportAllAsync()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            is MainAction.LocateHandled -> consumeLocateTarget(action.target)
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }

            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }

            is MainAction.SelectCategory -> {
                MmkvManager.encodeSettings(PREF_LAST_CATEGORY, action.category.name)

                val cleanIpGroup = getCleanIpGroupId()
                val defaultGroup = getDefaultGroupId()
                val targetGroupId = if (action.category == ConfigCategory.CLEAN_IP) {
                    cleanIpGroup ?: uiState.value.selectedGroupId
                } else {
                    defaultGroup ?: uiState.value.selectedGroupId
                }

                if (targetGroupId.isNotEmpty() && targetGroupId != uiState.value.selectedGroupId) {
                    subscriptionIdChanged(targetGroupId)
                }

                _uiState.update { it.copy(selectedCategory = action.category, showCleanIpEmptyBanner = action.category == ConfigCategory.CLEAN_IP) }
                if (uiState.value.isRunning) {
                    _restartConnectRequest.tryEmit(Unit)
                }
            }

            MainAction.ToggleTheme -> {
                val currentMode = ThemeManager.themeMode.value
                val newMode = when (currentMode) {
                    "2" -> "1"
                    else -> "2"
                }
                ThemeManager.setThemeMode(newMode)
                val isDark = newMode == "2"
                _uiState.update {
                    it.copy(
                        isDarkMode = isDark,
                        hasUserToggledTheme = true
                    )
                }
            }

            MainAction.ConnectFastest -> {
                _uiState.update { it.copy(showCleanIpEmptyBanner = false) }
                connectToFastestServer()
            }

            MainAction.CancelConnect -> {
                connectJob?.cancel()
                connectJob = null
                cancelAllPing()
                CipherSuitesManager.startupSafetyCheck()
                _uiState.update { it.copy(isConnecting = false, status = MainStatus.Disconnected) }
            }

            MainAction.ToggleCipherSuites -> {
                val newState = !_uiState.value.isCipherSuitesEnabled
                CipherSuitesManager.setEnabled(newState)
                _uiState.update { it.copy(isCipherSuitesEnabled = newState) }

                if (_uiState.value.isRunning) {
                    if (newState) {
                        val guid = dataSource.getSelectServer()
                        if (!guid.isNullOrBlank()) {
                            CipherSuitesManager.applyBeforeConnect(guid)
                            skipRestoreOnNextStop = true
                            _restartConnectRequest.tryEmit(Unit)
                        }
                    } else {
                        _disconnectRequest.tryEmit(Unit)
                    }
                }
            }

            MainAction.EnterAdvancedMode -> {
                CipherSuitesManager.setEnabled(false)
                _uiState.update { it.copy(isCipherSuitesEnabled = false) }
            }

            MainAction.ToggleService -> {
                if (KillSwitchManager.isEnabled() && (_uiState.value.isRunning || _uiState.value.isBlocking)) {
                    _uiState.update { it.copy(showDisconnectDialog = true) }
                    return
                }
                connectJob?.cancel()
                connectJob = null
                _uiState.update { it.copy(isConnecting = false) }
            }

            MainAction.TestCurrentServer -> testCurrentServerRealPing()

            MainAction.ImportQRcode, MainAction.ImportClipboard, MainAction.ImportConfigLocal, is MainAction.ImportManually, MainAction.RestartService, MainAction.LocateSelectedServer, is MainAction.EditServer, is MainAction.ShareClipboard, is MainAction.ShareFullContent -> {}

            MainAction.DismissEmptyCategoryDialog -> {
                _uiState.update { it.copy(showEmptyCategoryDialog = false, emptyCategoryName = "") }
            }

            MainAction.DismissCleanIpEmptyBanner -> {
                _uiState.update { it.copy(showCleanIpEmptyBanner = false) }
            }

            MainAction.OpenIpScanner -> {
            }

            MainAction.ConfirmDisconnectWithKillSwitch -> {
                _uiState.update { it.copy(showDisconnectDialog = false) }
                connectJob?.cancel()
                connectJob = null
                cancelAllPing()
                _uiState.update { it.copy(isRunning = false, isBlocking = false, status = MainStatus.Disconnected) }
                val guid = dataSource.getSelectServer()
                if (!guid.isNullOrBlank()) {
                    CipherSuitesManager.restoreAfterDisconnect(guid)
                }
            }

            MainAction.DismissDisconnectDialog -> {
                _uiState.update { it.copy(showDisconnectDialog = false) }
            }

            MainAction.GenerateCleanIPs -> {
                generateCleanIPs()
            }
        }
    }

    private fun ensureSubscriptionConfigured() {
        val existingSubs = dataSource.getSubscriptions()
        val hasDefault = existingSubs.any { it.subscription.remarks == DEFAULT_SUBSCRIPTION_REMARKS }

        if (!hasDefault) {
            val oldSub = existingSubs.find {
                val r = it.subscription.remarks.trim().lowercase()
                r == "default subscription" || r == "default"
            }
            if (oldSub != null) {
                oldSub.subscription.remarks = DEFAULT_SUBSCRIPTION_REMARKS
                MmkvManager.encodeSubscription(oldSub.guid, oldSub.subscription)
                LogUtil.i(AppConfig.TAG, "Migrated subscription to: $DEFAULT_SUBSCRIPTION_REMARKS")
            } else {
                val subItem = SubscriptionItem().apply {
                    url = DEFAULT_SUBSCRIPTION_URL
                    remarks = DEFAULT_SUBSCRIPTION_REMARKS
                    enabled = true
                }
                val guid = UUID.randomUUID().toString()
                MmkvManager.encodeSubscription(guid, subItem)
                MmkvManager.encodeServerList(ArrayList<String>(), guid)

                val subsList = MmkvManager.decodeSubsList().toMutableList()
                if (!subsList.contains(guid)) {
                    subsList.add(guid)
                    MmkvManager.encodeSubsList(subsList)
                }
                LogUtil.i(AppConfig.TAG, "Created default subscription with URL: $DEFAULT_SUBSCRIPTION_URL")
            }
        }
    }

    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                ensureSubscriptionConfigured()
                initialPageReady.await()
                delay(32)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
            )
        }
    }

    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.distinct().mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L
            )
        }.distinctBy { it.guid }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        mutableServersForGroup(groupId).value = applyKeywordFilter(servers)
    }

    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val groups = dataSource.getSubscriptions().map {
                    GroupMapItem(id = it.guid, remarks = it.subscription.remarks)
                }

                val selectedGroup = if (uiState.value.selectedCategory == ConfigCategory.CLEAN_IP) {
                    getCleanIpGroupId() ?: resolveSelectedGroup(groups)
                } else {
                    getDefaultGroupId() ?: resolveSelectedGroup(groups)
                }

                val validIds = groups.mapTo(HashSet()) { it.id }
                groupPageFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = dataSource.getSelectServer()
                    )
                }
                groups.forEach { mutableServersForGroup(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock { groupDataCache.clear() }
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex =
                    groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    private fun importBatchConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.importBatchConfig(
                        configText, uiState.value.selectedGroupId, true
                    )
                    when {
                        count > 0 -> {
                            toast(dataSource.getString(R.string.title_import_config_count, count))
                            setupGroupTab(forceRefresh = true)
                        }

                        countSub > 0 -> setupGroupTab(forceRefresh = true)
                        else -> toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importConfigViaSub() {
        val subId = uiState.value.selectedGroupId
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val result = if (subId.isEmpty()) {
                        dataSource.updateConfigViaSubAll()
                    } else {
                        val item = dataSource.getSubscriptionItem(subId) ?: return@withContext
                        dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    }
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                            toast(dataSource.getString(R.string.title_update_config_count, result.configCount))

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val groupId = uiState.value.selectedGroupId
                    val list = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
                        dataSource.getServerGuidList("")
                    } else {
                        currentServers().map { it.guid }
                    }
                    val ret = dataSource.shareNonCustomConfigsToClipboard(list)
                    if (ret > 0) {
                        toast(dataSource.getString(R.string.title_export_config_count, ret))
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Export failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeAllServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count =
                        if (uiState.value.selectedGroupId.isEmpty() && keywordFilter.isEmpty()) {
                            dataSource.removeAllServer()
                        } else {
                            val guids = currentServers().map { it.guid }
                            guids.forEach { dataSource.removeServer(it) }
                            guids.size
                        }
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                    }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val seen = HashSet<ProfileItem>()
                    val duplicates = ArrayList<String>()
                    currentServers().forEach { server ->
                        val profile = server.profile
                        if (!profile.configType.isComplexType()) {
                            val identity = profile.duplicateIdentity()
                            if (!seen.add(identity)) duplicates += server.guid
                        }
                    }
                    duplicates.forEach { dataSource.removeServer(it) }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count = removeInvalidServerInternal()
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                        setupGroupTab(forceRefresh = true)
                    }
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly =
            uiState.value.selectedGroupId.isNotEmpty() || keywordFilter.isNotBlank()
        return if (visibleServersOnly) {
            currentServers().sumOf { server ->
                dataSource.removeInvalidServerByGuid(server.guid)
            }
        } else {
            dataSource.removeInvalidServersInGroup("")
        }
    }

    private fun sortByTestResultsAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    sortByTestResultsInternal()
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) {
            dataSource.getSubsList()
        } else {
            listOf(uiState.value.selectedGroupId)
        }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServersForGroup(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
        }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    fun reloadServerList() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) {
            val selected = uiState.value.selectedGroupId
            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
            }
            order.forEachIndexed { index, groupId ->
                ensureActive()
                if (index > 0) delay(32)
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            delay(300)
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        dataSource.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid) }
    }

    fun refreshSelectedGuid() {
        _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) }
    }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun moveServer(groupId: String, fromPosition: Int, toPosition: Int) {
        val servers = mutableServersForGroup(groupId).value.toMutableList()
        if (!servers.moveItem(fromPosition, toPosition)) return
        val guids = servers.map { it.guid }
        mutableServersForGroup(groupId).value = servers
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch(ioDispatcher) {
            previousPersistenceJob?.join()
            dataSource.encodeServerList(guids, groupId)
            cacheMutex.withLock { groupDataCache[groupId] = servers }
        }
    }

    fun cancelAllPing() {
        dataSource.cancelAllPing()
        testingGroupId = null
        AutoFailoverManager.cancelHealthCheck()
        _uiState.update {
            it.copy(
                isTesting = false,
                status = if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        dataSource.cancelAllPing()
        val groupId = uiState.value.selectedGroupId
        val servers = currentServers()
        if (servers.isEmpty()) {
            _uiState.update { it.copy(isTesting = false) }
            return
        }
        val serverGuids = servers.map { it.guid }
        mutableServersForGroup(groupId).update { current ->
            current.map { server ->
                if (server.testDelayMillis == 0L) server
                else server.copy(testDelayMillis = 0L)
            }
        }
        testingGroupId = groupId
        _uiState.update {
            it.copy(
                isTesting = true,
                status = MainStatus.Testing
            )
        }
        viewModelScope.launch(ioDispatcher) {
            dataSource.clearAllTestDelayResults(serverGuids)
            cacheMutex.withLock { groupDataCache.remove(groupId) }
            dataSource.sendMsg2TestService(
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = groupId,
                    serverGuids = if (keywordFilter.isNotEmpty()) serverGuids else emptyList(),
                    onlyTcp = onlyTcp
                )
            )
        }
    }

    fun testCurrentServerRealPing() {
        _uiState.update {
            it.copy(
                status = MainStatus.Testing,
                currentPingDelay = -2L
            )
        }
        dataSource.testCurrentServerRealPing()
    }

    private fun connectToFastestServer() {
        launchLoading { withContext(ioDispatcher) {
            var guids = emptyList<String>()
            try {
                connectJob?.cancel()
                connectJob = currentCoroutineContext()[Job]
                val category = uiState.value.selectedCategory

                val targetGroupId: String

                if (category == ConfigCategory.CLEAN_IP) {
                    _uiState.update { it.copy(isConnecting = true, status = MainStatus.TestProgress("Loading Clean IPs...")) }

                    val cleanGroupId = getCleanIpGroupId()
                    var cleanGuids = if (cleanGroupId != null) dataSource.getServerGuidList(cleanGroupId) else emptyList()

                    if (cleanGuids.isEmpty()) {
                        cleanGuids = dataSource.getAllServerGuids().filter { guid ->
                            KhorshidConfigGenerator.isCleanIPConfig(guid)
                        }
                    }

                    if (cleanGuids.isEmpty()) {
                        _uiState.update { it.copy(
                            showCleanIpEmptyBanner = true,
                            isConnecting = false
                        ) }
                        return@withContext
                    }
                    guids = cleanGuids
                    targetGroupId = cleanGroupId ?: uiState.value.selectedGroupId

                } else {
                    _uiState.update { it.copy(isConnecting = true, status = MainStatus.TestProgress("Updating configs...")) }

                    ensureSubscriptionConfigured()

                    val existingSubs = dataSource.getSubscriptions()
                    val defaultSub = existingSubs.find { it.subscription.remarks == DEFAULT_SUBSCRIPTION_REMARKS }

                    if (defaultSub != null) {
                        defaultSub.subscription.url = "$DEFAULT_SUBSCRIPTION_URL?t=${System.currentTimeMillis()}"
                        MmkvManager.encodeSubscription(defaultSub.guid, defaultSub.subscription)

                        _uiState.update { it.copy(status = MainStatus.TestProgress("Downloading configs...")) }

                        val result = dataSource.updateConfigViaSub(defaultSub)

                        try {
                            val rawContent = HttpUtil.getUrlContent(UrlContentRequest(url = defaultSub.subscription.url))
                            if (rawContent != null) {
                                val decoded = android.util.Base64.decode(rawContent, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
                                val boostProfile = CipherSuitesManager.parseSpeedBoostLine(decoded)
                                if (boostProfile != null) {
                                    CipherSuitesManager.cacheProfile(boostProfile)
                                    LogUtil.i(AppConfig.TAG, "CipherSuites profile cached: v=${boostProfile.version}")
                                }
                            }
                        } catch (e: Exception) {
                            LogUtil.e(AppConfig.TAG, "Failed to parse CipherSuites", e)
                        }

                        defaultSub.subscription.url = DEFAULT_SUBSCRIPTION_URL
                        MmkvManager.encodeSubscription(defaultSub.guid, defaultSub.subscription)

                        if (result.configCount == 0) {
                            _uiState.update { it.copy(status = MainStatus.TestProgress("No configs found!"), isConnecting = false) }
                            return@withContext
                        }
                        targetGroupId = defaultSub.guid
                    } else {
                        dataSource.updateConfigViaSubAll()
                        targetGroupId = uiState.value.selectedGroupId
                    }

                    setupGroupTab(forceRefresh = true).join()
                    refreshSelectedGuid()

                    val allGroupGuids = dataSource.getServerGuidList(targetGroupId).ifEmpty { dataSource.getAllServerGuids() }
                    if (allGroupGuids.isEmpty()) {
                        _uiState.update { it.copy(status = MainStatus.TestProgress("No configs found!"), isConnecting = false) }
                        return@withContext
                    }

                    val filteredGuids = when (category) {
                        ConfigCategory.ALL -> allGroupGuids.filter { guid ->
                            !KhorshidConfigGenerator.isCleanIPConfig(guid)
                        }
                        ConfigCategory.BPB -> allGroupGuids.filter { guid ->
                            val profile = dataSource.decodeServerConfig(guid)
                            profile?.remarks?.contains("[BPB]", ignoreCase = true) == true &&
                                    !KhorshidConfigGenerator.isCleanIPConfig(guid)
                        }
                        ConfigCategory.NAHAN -> allGroupGuids.filter { guid ->
                            val profile = dataSource.decodeServerConfig(guid)
                            profile?.remarks?.contains("[NAHAN]", ignoreCase = true) == true &&
                                    !KhorshidConfigGenerator.isCleanIPConfig(guid)
                        }
                        ConfigCategory.OTHER -> allGroupGuids.filter { guid ->
                            val profile = dataSource.decodeServerConfig(guid)
                            profile != null && !profile.remarks.contains("[BPB]", ignoreCase = true) &&
                                    !profile.remarks.contains("[NAHAN]", ignoreCase = true) &&
                                    !KhorshidConfigGenerator.isCleanIPConfig(guid)
                        }
                        ConfigCategory.CLEAN_IP -> allGroupGuids
                    }

                    if (filteredGuids.isEmpty()) {
                        _uiState.update { it.copy(
                            showEmptyCategoryDialog = true,
                            emptyCategoryName = category.label,
                            isConnecting = false
                        ) }
                        return@withContext
                    }
                    guids = filteredGuids
                }

                _uiState.update { it.copy(status = MainStatus.TestProgress("Testing ${guids.size} ${category.label} servers...")) }

                CipherSuitesManager.applyToAll(guids)

                dataSource.clearAllTestDelayResults(guids)

                val finishDeferred = CompletableDeferred<Unit>()
                val eventCollector = viewModelScope.launch {
                    dataSource.mainServiceEvent.collect { event ->
                        if (event is MainServiceEvent.MeasureConfigFinish && event.finishedCount == "0") {
                            finishDeferred.complete(Unit)
                        }
                    }
                }

                dataSource.sendMsg2TestService(
                    TestServiceMessage(
                        key = AppConfig.MSG_MEASURE_CONFIG_START,
                        subscriptionId = targetGroupId,
                        serverGuids = guids
                    )
                )

                val startTime = System.currentTimeMillis()
                while (true) {
                    if (finishDeferred.isCompleted) break
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= 90_000L) {
                        dataSource.cancelAllPing()
                        break
                    }
                    delay(500L)
                }

                eventCollector.cancel()

                val candidates = guids.mapNotNull { guid ->
                    val affiliation = dataSource.decodeAffiliationInfo(guid)
                    val delay = affiliation?.testDelayMillis ?: 0L
                    if (delay > 0) Pair(guid, delay) else null
                }.sortedBy { it.second }

                if (candidates.isEmpty()) {
                    CipherSuitesManager.restoreAllExcept(guids, "")
                    _uiState.update { it.copy(status = MainStatus.TestProgress("All configs returned ping -1. Check network or refresh subscriptions."), isConnecting = false) }
                    return@withContext
                }

                val best = candidates.first()
                _uiState.update { it.copy(status = MainStatus.TestProgress("Connecting..."), selectedGuid = best.first, isConnecting = false) }
                dataSource.setSelectServer(best.first)

                CipherSuitesManager.restoreAllExcept(guids, best.first)

                _requestConnectAfterPick.tryEmit(Unit)

            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "ConnectFastest failed", e)
                CipherSuitesManager.restoreAllExcept(guids, "")
                _uiState.update { it.copy(status = MainStatus.TestProgress("Error occurred!"), isConnecting = false) }
            }
        } }
    }

    private fun isCleanIpServer(server: ServersCache): Boolean {
        return server.profile.remarks.contains(KhorshidConfigGenerator.CLEAN_TAG_PREFIX, ignoreCase = true) ||
                server.profile.description?.contains(KhorshidConfigGenerator.CLEAN_FLAG) == true ||
                server.profile.description?.contains("isCleanIpGenerated=true") == true ||
                KhorshidConfigGenerator.isCleanIPConfig(server.guid)
    }

    fun filterByCategory(servers: List<ServersCache>, category: ConfigCategory): List<ServersCache> {
        return when (category) {
            ConfigCategory.ALL -> servers.filter { !isCleanIpServer(it) }
            ConfigCategory.BPB -> servers.filter {
                it.profile.remarks.contains("[BPB]", ignoreCase = true) &&
                        !isCleanIpServer(it)
            }
            ConfigCategory.NAHAN -> servers.filter {
                it.profile.remarks.contains("[NAHAN]", ignoreCase = true) &&
                        !isCleanIpServer(it)
            }
            ConfigCategory.OTHER -> servers.filter {
                !it.profile.remarks.contains("[BPB]", ignoreCase = true) &&
                        !it.profile.remarks.contains("[NAHAN]", ignoreCase = true) &&
                        !isCleanIpServer(it)
            }
            ConfigCategory.CLEAN_IP -> {
                val inCurrent = servers.filter {
                    it.profile.remarks.contains("[Clean IP]", ignoreCase = true) ||
                            it.profile.description?.contains("isKhorshidClean=true") == true ||
                            it.profile.description?.contains("isCleanIpGenerated=true") == true ||
                            KhorshidConfigGenerator.isCleanIPConfig(it.guid)
                }
                if (inCurrent.isNotEmpty()) {
                    inCurrent
                } else {
                    val cleanSubId = getCleanIpGroupId()
                    if (!cleanSubId.isNullOrEmpty()) {
                        dataSource.getServerGuidList(cleanSubId).mapNotNull { guid ->
                            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
                            val aff = dataSource.decodeAffiliationInfo(guid)
                            ServersCache(guid, profile, aff?.testDelayMillis ?: 0L)
                        }
                    } else {
                        emptyList()
                    }
                }
            }
        }
    }

    fun generateCleanIPs(numPerConfig: Int = 3) {
        launchLoading { withContext(ioDispatcher) {
            try {
                val lastSessionId = IpScanPersistence.getLastSessionId()
                val scanResults = if (lastSessionId != null) IpScanPersistence.loadResults(lastSessionId) else emptyList()
                LogUtil.i(AppConfig.TAG, "CleanIP: scanResults count = ${scanResults.size}")

                if (scanResults.isEmpty()) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress("No scan results — run IP Scan first")) }
                    toast("No scan results — run IP Scan first")
                    return@withContext
                }

                val subGuids = dataSource.getServerGuidList(uiState.value.selectedGroupId)
                LogUtil.i(AppConfig.TAG, "CleanIP: source config GUIDs count = ${subGuids.size}")

                if (subGuids.isEmpty()) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress("No source configs found")) }
                    toast("No source configs found — check your subscription list")
                    return@withContext
                }

                val generated = KhorshidConfigGenerator.cloneConfigsWithCleanIps(subGuids, scanResults, numPerConfig)
                if (generated > 0) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress("Generated $generated Clean IP configs")) }
                    toast("Generated $generated Clean IP configs")
                    setupGroupTab(forceRefresh = true)
                } else {
                    _uiState.update { it.copy(status = MainStatus.TestProgress("No matching validated IPs for your configs' SNI")) }
                    toast("No matching validated IPs for your configs' SNI")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "generateCleanIPs failed", e)
                toastError(R.string.toast_failure)
            }
        } }
    }

    private fun onTestsFinished() {
        viewModelScope.launch(ioDispatcher) {
            if (dataSource.getAutoRemoveInvalidAfterTest()) removeInvalidServerInternal()
            if (dataSource.getAutoSortAfterTest()) sortByTestResultsInternal()
            cacheMutex.withLock { groupDataCache.clear() }
            testingGroupId = null
            _uiState.update { it.copy(isTesting = false, status = if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected) }
            reloadAllGroups(_uiState.value.groups.map { it.id })
        }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return
        val profile = dataSource.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        val groupIndex =
            _uiState.value.groups.indexOfFirst { it.id == groupId }.takeIf { it >= 0 } ?: return
        viewModelScope.launch(ioDispatcher) {
            val position =
                loadGroup(groupId).indexOfFirst { it.guid == selected }.takeIf { it >= 0 }
                    ?: return@launch
            _uiState.update {
                it.copy(locateTarget = LocateTarget(groupId, groupIndex, position))
            }
        }
    }

    fun getPosition(guid: String): Int = currentServers().indexOfFirst { it.guid == guid }

    private fun consumeLocateTarget(target: LocateTarget) {
        _uiState.update { state ->
            if (state.locateTarget == target) state.copy(locateTarget = null) else state
        }
    }

    private fun updateRunningState(running: Boolean, clearTestingText: Boolean = true) {
        _uiState.update {
            it.copy(
                isRunning = running,
                status = if (!clearTestingText && it.isTesting) it.status
                else if (running) MainStatus.Connected else MainStatus.Disconnected,
                isConnecting = false,
                currentPingDelay = if (running) it.currentPingDelay else -1L
            )
        }
        if (running) {
            if (connectionTimerJob?.isActive != true) startConnectionTimer()
        } else {
            stopConnectionTimer()
        }
    }

    private fun startConnectionTimer() {
        connectionTimerJob?.cancel()
        connectedTimestamp = System.currentTimeMillis()

        val uid = Process.myUid()
        initialRxBytes = TrafficStats.getUidRxBytes(uid)
        initialTxBytes = TrafficStats.getUidTxBytes(uid)

        lastRxBytes = if (initialRxBytes != TrafficStats.UNSUPPORTED.toLong()) initialRxBytes else 0L
        lastTxBytes = if (initialTxBytes != TrafficStats.UNSUPPORTED.toLong()) initialTxBytes else 0L
        lastSpeedCheckTimestamp = System.currentTimeMillis()

        connectionTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val currentTime = System.currentTimeMillis()

                val elapsedSeconds = (currentTime - connectedTimestamp) / 1000
                val hours = elapsedSeconds / 3600
                val minutes = (elapsedSeconds % 3600) / 60
                val seconds = elapsedSeconds % 60
                val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                val currentRxBytes = TrafficStats.getUidRxBytes(uid)
                val currentTxBytes = TrafficStats.getUidTxBytes(uid)

                var totalDownloaded = 0L
                var totalUploaded = 0L

                if (currentRxBytes != TrafficStats.UNSUPPORTED.toLong() && currentTxBytes != TrafficStats.UNSUPPORTED.toLong()) {
                    totalDownloaded = maxOf(0L, currentRxBytes - initialRxBytes)
                    totalUploaded = maxOf(0L, currentTxBytes - initialTxBytes)
                }

                _uiState.update { currentState ->
                    currentState.copy(
                        connectionDurationText = formattedTime,
                        downloadSpeedText = formatSpeed(totalDownloaded),
                        uploadSpeedText = formatSpeed(totalUploaded)
                    )
                }

                delay(1000L)
            }
        }
    }

    private fun stopConnectionTimer() {
        connectionTimerJob?.cancel()
        connectionTimerJob = null
        lastRxBytes = 0L
        lastTxBytes = 0L
        _uiState.update {
            it.copy(
                connectionDurationText = "00:00:00",
                downloadSpeedText = "0 B/s",
                uploadSpeedText = "0 B/s"
            )
        }
    }

    private fun formatSpeed(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B/s"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB/s"
            else -> String.format("%.1f MB/s", bytes / (1024.0 * 1024.0))
        }
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()
        connectJob?.cancel()
        connectionTimerJob?.cancel()
        cancelAllPing()
        dataSource.close()
        super.onCleared()
    }

    class Factory(private val application: Application, private val dataSource: MainDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}