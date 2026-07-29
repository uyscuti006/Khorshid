package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException
import android.net.TrafficStats
import android.os.Process

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    companion object {
        private const val DEFAULT_SUBSCRIPTION_URL = "https://raw.githubusercontent.com/uyscuti006/vpn-public-configs/main/sub.txt"
        private const val DEFAULT_SUBSCRIPTION_REMARKS = "Default Subscription"
        private const val PREF_LAST_CATEGORY = "pref_last_category"
        private const val PREF_APP_THEME = "pref_app_theme"
    }

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val disconnectedText: String = dataSource.getString(R.string.connection_not_connected)
    private val connectedText: String = dataSource.getString(R.string.connection_connected)

    private val initialCategory: ConfigCategory = run {
        val savedName = MmkvManager.decodeSettingsString(PREF_LAST_CATEGORY, ConfigCategory.ALL.name)
        if (savedName.isNullOrEmpty()) {
            ConfigCategory.ALL
        } else {
            runCatching { ConfigCategory.valueOf(savedName) }.getOrDefault(ConfigCategory.ALL)
        }
    }

    // ۲. خواندن تم ذخیره‌شده ("dark" ، "light" یا "system")
    private val savedThemeMode: String = MmkvManager.decodeSettingsString(PREF_APP_THEME, "system") ?: "system"

    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            statusText = disconnectedText,
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay(),
            selectedCategory = initialCategory, // لود متد قبلی
            isDarkMode = savedThemeMode == "dark", // لود حالت تاریک
            hasUserToggledTheme = savedThemeMode != "system" // آیا کاربر قبلاً شخصی‌سازی کرده؟
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _requestConnectAfterPick = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1
    )
    val connectRequest: SharedFlow<Unit> = _requestConnectAfterPick.asSharedFlow()

    private val _restartConnectRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restartConnectRequest: SharedFlow<Unit> = _restartConnectRequest.asSharedFlow()

    @Volatile
    private var keywordFilter: String = ""
    private var filterJob: Job? = null

    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupPageFlows = ConcurrentHashMap<String, MutableStateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null
    private var connectJob: Job? = null
    private var connectionTimerJob: Job? = null
    private var connectedTimestamp: Long = 0L
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSpeedCheckTimestamp = 0L

    @Volatile
    private var testingGroupId: String? = null

    private val initialPageReady = CompletableDeferred<Unit>()

    init {
        collectServiceEvents()
        setupGroupTab()
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private suspend fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> updateRunningState(false, clearTestingText = false)
            MainServiceEvent.StateStartSuccess -> {
                updateRunningState(true)
            }
            is MainServiceEvent.StateStartFailure -> {
                updateRunningState(false)
            }
            MainServiceEvent.StateStopSuccess -> updateRunningState(false)
            is MainServiceEvent.MeasureDelaySuccess -> {
                val delay = Regex("(-?\\d+)\\s*ms", RegexOption.IGNORE_CASE)
                    .find(event.content)
                    ?.groupValues?.get(1)
                    ?.toLongOrNull() ?: -1L
                _uiState.update { it.copy(statusText = event.content, currentPingDelay = delay) }
            }
            MainServiceEvent.MeasureConfigSuccess -> {
                viewModelScope.launch(ioDispatcher) {
                    val gid = testingGroupId ?: uiState.value.selectedGroupId
                    cacheMutex.withLock { groupDataCache.remove(gid) }
                    updateGroupUi(gid, loadGroup(gid, forceRefresh = true))
                }
            }
            is MainServiceEvent.MeasureConfigNotify -> { _uiState.update { it.copy(statusText = dataSource.getString(R.string.connection_runing_task_left, event.progress)) } }
            is MainServiceEvent.MeasureConfigFinish -> { if (event.finishedCount == "0") { onTestsFinished() } }
        }
    }

    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }.asStateFlow()

    private fun mutableServersForGroup(groupId: String): MutableStateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }

    private fun currentServers(): List<ServersCache> = mutableServersForGroup(uiState.value.selectedGroupId).value

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.ConnectFastest -> connectToFastestServer()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing()
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
            is MainAction.SwapServer -> swapServer(action.fromIndex, action.toIndex)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            is MainAction.LocateHandled -> consumeLocateTarget(action.target)
            is MainAction.ShareQRCode -> { val bitmap = dataSource.share2QRCode(action.guid); _uiState.update { it.copy(shareQRCodeBitmap = bitmap) } }
            MainAction.DismissQRCodeDialog -> { _uiState.update { it.copy(shareQRCodeBitmap = null) } }
            MainAction.TestCurrentServer -> testCurrentServerRealPing()
            MainAction.ToggleService -> {
                connectJob?.cancel()
                connectJob = null
                _uiState.update { it.copy(isConnecting = false) }
            }
            MainAction.CancelConnect -> {
                connectJob?.cancel()
                connectJob = null
                cancelAllPing()
                _uiState.update { it.copy(isConnecting = false, statusText = disconnectedText) }
            }
            MainAction.ImportQRcode, MainAction.ImportClipboard, MainAction.ImportConfigLocal, is MainAction.ImportManually, MainAction.RestartService, MainAction.LocateSelectedServer, is MainAction.EditServer, is MainAction.ShareClipboard, is MainAction.ShareFullContent -> {}
            is MainAction.SelectCategory -> {
                MmkvManager.encodeSettings(PREF_LAST_CATEGORY, action.category.name)
                _uiState.update { it.copy(selectedCategory = action.category) }
                if (uiState.value.isRunning) {
                    _restartConnectRequest.tryEmit(Unit)
                }
            }
            MainAction.ToggleTheme -> {
                _uiState.update { currentState ->
                    val newIsDark = !currentState.isDarkMode
                    val themeModeString = if (newIsDark) "dark" else "light"

                    // ذخیره تم جدید در حافظه دائمی
                    MmkvManager.encodeSettings(PREF_APP_THEME, themeModeString)

                    currentState.copy(
                        isDarkMode = newIsDark,
                        hasUserToggledTheme = true) }
            }
        }
    }

    private fun ensureSubscriptionConfigured() {
        val existingSubs = dataSource.getSubscriptions()
        val hasDefault = existingSubs.any { it.subscription.remarks == DEFAULT_SUBSCRIPTION_REMARKS }
        if (!hasDefault) {
            val subItem = SubscriptionItem().apply {
                url = DEFAULT_SUBSCRIPTION_URL
                remarks = DEFAULT_SUBSCRIPTION_REMARKS
                enabled = true
            }
            val guid = UUID.randomUUID().toString()
            MmkvManager.encodeSubscription(guid, subItem)
            LogUtil.i(AppConfig.TAG, "SimpleUI: Created default subscription with URL: $DEFAULT_SUBSCRIPTION_URL")
        }
    }

    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                ensureSubscriptionConfigured()
                initialPageReady.await()
                delay(32L)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { LogUtil.e(AppConfig.TAG, "Main background initialization failed", error) }
        }
    }

    fun refreshUiSettings() { _uiState.update { it.copy(confirmRemove = dataSource.getConfirmRemove(), doubleColumnDisplay = dataSource.getDoubleColumnDisplay()) } }

    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> = guids.mapNotNull { guid ->
        currentCoroutineContext().ensureActive()
        val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
        val affiliation = dataSource.decodeAffiliationInfo(guid)
        ServersCache(guid = guid, profile = profile.copy(), testDelayMillis = affiliation?.testDelayMillis ?: 0L, testDelayString = affiliation?.getTestDelayString().orEmpty())
    }

    private suspend fun loadGroup(groupId: String, forceRefresh: Boolean = false): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) { cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } } }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try { Regex(keyword, RegexOption.IGNORE_CASE) } catch (_: PatternSyntaxException) { return servers }
        return servers.filter { cache -> val p = cache.profile; p.remarks.matchesPattern(regex, keyword) || p.description.orEmpty().matchesPattern(regex, keyword) || p.server.orEmpty().matchesPattern(regex, keyword) || p.configType.name.matchesPattern(regex, keyword) }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) { mutableServersForGroup(groupId).value = applyKeywordFilter(servers) }
    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when { groups.isEmpty() -> ""; groups.any { it.id == current } -> current; else -> groups.first().id }
        if (resolved != current) { dataSource.setSelectedSubscriptionId(resolved) }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) { val r = selectedIndex + distance; val l = selectedIndex - distance; if (r in groups.indices) result += groups[r].id; if (l in groups.indices) result += groups[l].id }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel(); preloadJob?.cancel(); selectedGroupLoadJob?.cancel()
        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) { cacheMutex.withLock { groupDataCache.clear() } }
                val groups = dataSource.getSubscriptions().map { GroupMapItem(id = it.guid, remarks = it.subscription.remarks) }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupPageFlows.keys.removeAll { it !in validIds }; groupLoadMutexes.keys.removeAll { it !in validIds }
                _uiState.update { it.copy(groups = groups, selectedGroupId = selectedGroup, selectedGuid = dataSource.getSelectServer()) }
                groups.forEach { mutableServersForGroup(it.id) }
                if (groups.isEmpty()) { cacheMutex.withLock { groupDataCache.clear() }; return@launch }
                val selectedServers = loadGroup(selectedGroup, forceRefresh); updateGroupUi(selectedGroup, selectedServers)
                if (!initialPageReady.isCompleted) { initialPageReady.complete(Unit) }
                val selectedIndex = groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) { preloadOrder.forEach { ensureActive(); delay(32L); val s = loadGroup(it, forceRefresh); updateGroupUi(it, s) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error) }
            finally { if (!initialPageReady.isCompleted) { initialPageReady.complete(Unit) } }
        }.also { setupGroupJob = it }
    }

    private fun importBatchConfig(configText: String) {
        launchLoading { withContext(ioDispatcher) {
            try {
                val (count, countSub) = dataSource.importBatchConfig(configText, uiState.value.selectedGroupId, true)
                when { count > 0 -> { toast(dataSource.getString(R.string.title_import_config_count, count)); setupGroupTab(forceRefresh = true) }; countSub > 0 -> setupGroupTab(forceRefresh = true); else -> toastError(R.string.toast_failure) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Failed to import batch config", e); toastError(R.string.toast_failure) }
        } }
    }

    private fun importConfigViaSub() {
        val subId = uiState.value.selectedGroupId
        launchLoading { withContext(ioDispatcher) {
            try {
                val result = if (subId.isEmpty()) { dataSource.updateConfigViaSubAll() } else { val item = dataSource.getSubscriptionItem(subId) ?: return@withContext; dataSource.updateConfigViaSub(SubscriptionCache(subId, item)) }
                when { result.successCount + result.failureCount + result.skipCount == 0 -> toast(R.string.title_update_subscription_no_subscription)
                    result.successCount > 0 && result.failureCount + result.skipCount == 0 -> toast(dataSource.getString(R.string.title_update_config_count, result.configCount))
                    else -> toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount)) }
                if (result.configCount > 0) { setupGroupTab(forceRefresh = true); refreshSelectedGuid() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Subscription update failed", e); toastError(R.string.toast_failure) }
        } }
    }

    private fun connectToFastestServer() {
        launchLoading { withContext(ioDispatcher) {
            try {
                connectJob?.cancel()
                connectJob = currentCoroutineContext()[Job]
                _uiState.update { it.copy(isConnecting = true, statusText = "Updating configs...") }

                ensureSubscriptionConfigured()

                val existingSubs = dataSource.getSubscriptions()
                val defaultSub = existingSubs.find { it.subscription.remarks == DEFAULT_SUBSCRIPTION_REMARKS }

                if (defaultSub != null) {
                    defaultSub.subscription.url = "$DEFAULT_SUBSCRIPTION_URL?t=${System.currentTimeMillis()}"
                    MmkvManager.encodeSubscription(defaultSub.guid, defaultSub.subscription)

                    _uiState.update { it.copy(statusText = "Downloading configs...") }

                    val result = dataSource.updateConfigViaSub(defaultSub)

                    defaultSub.subscription.url = DEFAULT_SUBSCRIPTION_URL
                    MmkvManager.encodeSubscription(defaultSub.guid, defaultSub.subscription)

                    if (result.configCount == 0) {
                        _uiState.update { it.copy(statusText = "No configs found!") }
                        return@withContext
                    }
                } else {
                    dataSource.updateConfigViaSubAll()
                }

                setupGroupTab(forceRefresh = true).join()
                refreshSelectedGuid()

                val category = uiState.value.selectedCategory
                val allGuids = dataSource.getAllServerGuids()
                if (allGuids.isEmpty()) {
                    _uiState.update { it.copy(statusText = "No configs found!") }
                    return@withContext
                }

                // Filter servers by selected category
                val guids = if (category == ConfigCategory.ALL) {
                    allGuids
                } else {
                    allGuids.filter { guid ->
                        val profile = dataSource.decodeServerConfig(guid)
                        profile != null && when (category) {
                            ConfigCategory.BPB -> profile.remarks.contains("[BPB]", ignoreCase = true)
                            ConfigCategory.NAHAN -> profile.remarks.contains("[NAHAN]", ignoreCase = true)
                            ConfigCategory.OTHER -> !profile.remarks.contains("[BPB]", ignoreCase = true) &&
                                                    !profile.remarks.contains("[NAHAN]", ignoreCase = true)
                            ConfigCategory.ALL -> true
                        }
                    }
                }

                if (guids.isEmpty()) {
                    _uiState.update { it.copy(statusText = "No ${category.label} servers found!") }
                    return@withContext
                }

                _uiState.update { it.copy(statusText = "Testing ${guids.size} ${category.label} servers...") }

                // Clear previous results and trigger Real Delay test
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
                        subscriptionId = uiState.value.selectedGroupId,
                        serverGuids = guids
                    )
                )

                // Wait up to 30 seconds, then check if at least one valid result exists
                val startTime = System.currentTimeMillis()
                while (true) {
                    if (finishDeferred.isCompleted) break
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= 30_000L) {
                        val validSoFar = guids.mapNotNull { guid ->
                            val affiliation = dataSource.decodeAffiliationInfo(guid)
                            val delay = affiliation?.testDelayMillis ?: 0L
                            if (delay > 0) Pair(guid, delay) else null
                        }
                        if (validSoFar.isNotEmpty()) {
                            dataSource.cancelAllPing()
                            break
                        }
                    }
                    delay(500L)
                }

                eventCollector.cancel()

                // Read results and pick the server with lowest valid delay
                val candidates = guids.mapNotNull { guid ->
                    val affiliation = dataSource.decodeAffiliationInfo(guid)
                    val delay = affiliation?.testDelayMillis ?: 0L
                    if (delay > 0) Pair(guid, delay) else null
                }.sortedBy { it.second }

                if (candidates.isEmpty()) {
                    _uiState.update { it.copy(statusText = "All servers Timeout!") }
                    return@withContext
                }

                val best = candidates.first()
                _uiState.update { it.copy(statusText = "Connecting...", selectedGuid = best.first, isConnecting = false) }
                dataSource.setSelectServer(best.first)
                _requestConnectAfterPick.tryEmit(Unit)

            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "ConnectFastest failed", e)
                _uiState.update { it.copy(statusText = "Error occurred!", isConnecting = false) }
            }
        } }
    }

    private fun exportAllAsync() {
        launchLoading { withContext(ioDispatcher) {
            try { val groupId = uiState.value.selectedGroupId; val list = if (groupId.isEmpty() && keywordFilter.isEmpty()) { dataSource.getServerGuidList("") } else { currentServers().map { it.guid } }; val ret = dataSource.shareNonCustomConfigsToClipboard(list); if (ret > 0) { toast(dataSource.getString(R.string.title_export_config_count, ret)) } else { toastError(R.string.toast_failure) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Export failed", e); toastError(R.string.toast_failure) }
        } }
    }

    private fun removeAllServerAsync() {
        launchLoading { withContext(ioDispatcher) {
            try { val count = if (uiState.value.selectedGroupId.isEmpty() && keywordFilter.isEmpty()) { dataSource.removeAllServer() } else { val guids = currentServers().map { it.guid }; guids.forEach { dataSource.removeServer(it) }; guids.size }; viewModelScope.launch(ioDispatcher) { cacheMutex.withLock { groupDataCache.clear() } }; setupGroupTab(forceRefresh = true); toast(dataSource.getString(R.string.title_del_config_count, count)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Delete all failed", e); toastError(R.string.toast_failure) }
        } }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading { withContext(ioDispatcher) {
            try { val seen = HashSet<ProfileItem>(); val duplicates = ArrayList<String>(); currentServers().forEach { server -> val profile = server.profile; if (!profile.configType.isComplexType()) { val identity = profile.duplicateIdentity(); if (!seen.add(identity)) duplicates += server.guid } }; duplicates.forEach { dataSource.removeServer(it) }; setupGroupTab(forceRefresh = true); toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e); toastError(R.string.toast_failure) }
        } }
    }

    private fun removeInvalidServerAsync() {
        launchLoading { withContext(ioDispatcher) {
            try { val count = removeInvalidServerInternal(); viewModelScope.launch(ioDispatcher) { cacheMutex.withLock { groupDataCache.clear() }; setupGroupTab(forceRefresh = true) }; toast(dataSource.getString(R.string.title_del_config_count, count)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Delete invalid failed", e); toastError(R.string.toast_failure) }
        } }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly = uiState.value.selectedGroupId.isNotEmpty() || keywordFilter.isNotBlank()
        return if (visibleServersOnly) { currentServers().sumOf { server -> dataSource.removeInvalidServerByGuid(server.guid) } } else { dataSource.removeInvalidServersInGroup("") }
    }

    private fun sortByTestResultsAsync() {
        launchLoading { withContext(ioDispatcher) {
            try { sortByTestResultsInternal(); cacheMutex.withLock { groupDataCache.clear() }; setupGroupTab(forceRefresh = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { LogUtil.e(AppConfig.TAG, "Sort by test results failed", e); toastError(R.string.toast_failure) }
        } }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) { dataSource.getSubsList() } else { listOf(uiState.value.selectedGroupId) }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return; mutableServersForGroup(id)
        if (uiState.value.selectedGroupId != id) { dataSource.setSelectedSubscriptionId(id); _uiState.update { it.copy(selectedGroupId = id) } }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) { try { updateGroupUi(id, loadGroup(id)) } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error) } }
    }

    fun reloadServerList() { val groupId = uiState.value.selectedGroupId; selectedGroupLoadJob?.cancel(); selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) { updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true)) } }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) { val selected = uiState.value.selectedGroupId; val order = buildList { if (selected in groupIds) add(selected); addAll(groupIds.filter { it != selected }) }; order.forEachIndexed { index, groupId -> ensureActive(); if (index > 0) delay(32L); updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true)) } }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return; keywordFilter = keyword; filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) { delay(300L); val snapshot = cacheMutex.withLock { groupDataCache.toMap() }; ensureActive(); snapshot.forEach { (groupId, servers) -> ensureActive(); updateGroupUi(groupId, servers) } }
    }

    fun updateSelectedGuid(guid: String) { dataSource.setSelectServer(guid); _uiState.update { it.copy(selectedGuid = guid) } }
    fun refreshSelectedGuid() { _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) } }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) { toast(R.string.toast_action_not_allowed); return }
        viewModelScope.launch(ioDispatcher) { dataSource.removeServer(guid); cacheMutex.withLock { groupDataCache.clear() }; setupGroupTab(forceRefresh = true).join() }
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        val groupId = uiState.value.selectedGroupId; if (groupId.isEmpty()) return
        val servers = currentServers().toMutableList(); if (fromPosition !in servers.indices || toPosition !in servers.indices) return
        Collections.swap(servers, fromPosition, toPosition); val guids = servers.mapTo(ArrayList(servers.size)) { it.guid }
        mutableServersForGroup(groupId).value = servers; viewModelScope.launch(ioDispatcher) { dataSource.encodeServerList(guids, groupId); cacheMutex.withLock { groupDataCache[groupId] = servers } }
    }

    fun cancelAllPing() { dataSource.cancelAllPing(); testingGroupId = null; _uiState.update { it.copy(isTesting = false, statusText = if (it.isRunning) connectedText else disconnectedText) } }

    fun testAllRealPing() {
        dataSource.cancelAllPing(); val groupId = uiState.value.selectedGroupId; val servers = currentServers(); dataSource.clearAllTestDelayResults(servers.map { it.guid })
        if (servers.isEmpty()) { _uiState.update { it.copy(isTesting = false) }; return }
        testingGroupId = groupId; _uiState.update { it.copy(isTesting = true, statusText = dataSource.getString(R.string.connection_test_testing)) }
        viewModelScope.launch(ioDispatcher) { cacheMutex.withLock { groupDataCache.remove(groupId) }; dataSource.sendMsg2TestService(TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, subscriptionId = groupId, serverGuids = if (keywordFilter.isNotEmpty()) servers.map { it.guid } else emptyList())) }
    }

    fun testCurrentServerRealPing() { _uiState.update { it.copy(statusText = dataSource.getString(R.string.connection_test_testing), currentPingDelay = -2L) }; dataSource.testCurrentServerRealPing() }

    private suspend fun pollPingResult(timeoutMs: Long): Long {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val delay = uiState.value.currentPingDelay
            if (delay != -2L) return delay
            delay(200)
        }
        return -1L
    }

    fun filterByCategory(servers: List<ServersCache>, category: ConfigCategory): List<ServersCache> {
        return when (category) {
            ConfigCategory.ALL -> servers
            ConfigCategory.BPB -> servers.filter { it.profile.remarks.contains("[BPB]", ignoreCase = true) }
            ConfigCategory.NAHAN -> servers.filter { it.profile.remarks.contains("[NAHAN]", ignoreCase = true) }
            ConfigCategory.OTHER -> servers.filter {
                !it.profile.remarks.contains("[BPB]", ignoreCase = true) &&
                !it.profile.remarks.contains("[NAHAN]", ignoreCase = true)
            }
        }
    }

    private fun onTestsFinished() {
        viewModelScope.launch(ioDispatcher) { if (dataSource.getAutoRemoveInvalidAfterTest()) { removeInvalidServerInternal() }; if (dataSource.getAutoSortAfterTest()) { sortByTestResultsInternal() }; cacheMutex.withLock { groupDataCache.clear() }; testingGroupId = null; _uiState.update { it.copy(isTesting = false, statusText = if (it.isRunning) connectedText else disconnectedText) }; reloadAllGroups(_uiState.value.groups.map { it.id }) }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return; val profile = dataSource.decodeServerConfig(selected) ?: return; val groupId = profile.subscriptionId
        val groupIndex = _uiState.value.groups.indexOfFirst { it.id == groupId }.takeIf { it >= 0 } ?: return
        viewModelScope.launch(ioDispatcher) { val position = loadGroup(groupId).indexOfFirst { it.guid == selected }.takeIf { it >= 0 } ?: return@launch; _uiState.update { it.copy(locateTarget = LocateTarget(groupId, groupIndex, position)) } }
    }

    fun getPosition(guid: String): Int = currentServers().indexOfFirst { it.guid == guid }
    private fun consumeLocateTarget(target: LocateTarget) { _uiState.update { state -> if (state.locateTarget == target) state.copy(locateTarget = null) else state } }
    private fun updateRunningState(isRunning: Boolean, clearTestingText: Boolean = true) {
        _uiState.update {
            it.copy(
                isRunning = isRunning,
                statusText = if (isRunning) connectedText else disconnectedText,
                isConnecting = false
            )
        }

        if (isRunning) {
            if (connectionTimerJob?.isActive != true) {
                startConnectionTimer()
            }
        } else {
            stopConnectionTimer()
        }
    }
    // شروع تایمر اتصال و آپدیت زمان و سرعت
    private fun startConnectionTimer() {
        connectionTimerJob?.cancel()
        connectedTimestamp = System.currentTimeMillis()

        val uid = Process.myUid()
        val initialRx = TrafficStats.getUidRxBytes(uid)
        val initialTx = TrafficStats.getUidTxBytes(uid)

        lastRxBytes = if (initialRx != TrafficStats.UNSUPPORTED.toLong()) initialRx else 0L
        lastTxBytes = if (initialTx != TrafficStats.UNSUPPORTED.toLong()) initialTx else 0L
        lastSpeedCheckTimestamp = System.currentTimeMillis()

        connectionTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val currentTime = System.currentTimeMillis()

                // ۱. محاسبه زمان اتصال
                val elapsedSeconds = (currentTime - connectedTimestamp) / 1000
                val hours = elapsedSeconds / 3600
                val minutes = (elapsedSeconds % 3600) / 60
                val seconds = elapsedSeconds % 60
                val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                // ۲. محاسبه سرعت دانلود و آپلود
                val currentRxBytes = TrafficStats.getUidRxBytes(uid)
                val currentTxBytes = TrafficStats.getUidTxBytes(uid)
                val timeDiff = (currentTime - lastSpeedCheckTimestamp) / 1000.0

                var downSpeed = 0L
                var upSpeed = 0L

                if (timeDiff > 0 && currentRxBytes != TrafficStats.UNSUPPORTED.toLong()) {
                    val rxDiff = currentRxBytes - lastRxBytes
                    val txDiff = currentTxBytes - lastTxBytes

                    downSpeed = if (rxDiff > 0) (rxDiff / timeDiff).toLong() else 0L
                    upSpeed = if (txDiff > 0) (txDiff / timeDiff).toLong() else 0L

                    lastRxBytes = currentRxBytes
                    lastTxBytes = currentTxBytes
                    lastSpeedCheckTimestamp = currentTime
                }

                // ۳. آپدیت UI State
                _uiState.update { currentState ->
                    currentState.copy(
                        connectionDurationText = formattedTime,
                        downloadSpeedText = formatSpeed(downSpeed),
                        uploadSpeedText = formatSpeed(upSpeed)
                    )
                }

                delay(1000L) // هر ۱ ثانیه اجرا می‌شود
            }
        }
    }

    // متوقف کردن تایمر و ریست مقادیر
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

    // تابع کمکی فرمت‌دهی سرعت (بر حسب بایت بر ثانیه)
    fun updateSpeed(downBytesPerSec: Long, upBytesPerSec: Long) {
        _uiState.update { currentState ->
            currentState.copy(
                downloadSpeedText = formatSpeed(downBytesPerSec),
                uploadSpeedText = formatSpeed(upBytesPerSec)
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


}
