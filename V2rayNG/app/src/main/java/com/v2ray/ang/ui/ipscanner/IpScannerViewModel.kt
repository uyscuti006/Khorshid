package com.v2ray.ang.ui.ipscanner

import android.app.Application
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.reachability.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class IpScannerViewModel(application: Application) : AndroidViewModel(application) {

    enum class IpSourceType { RANDOM_POOL, CUSTOM_IPS }

    data class ConfigSummary(
        val guid: String,
        val remarks: String,
        val server: String,
        val port: String,
        val isCompatible: Boolean,
        val reason: String
    )

    data class UiState(
        val isScanning: Boolean = false,
        val isTestingSpeed: Boolean = false,
        val isGenerating: Boolean = false,
        val isCooldown: Boolean = false,
        val cooldownSeconds: Int = 0,
        val scanningNeighborIp: String? = null,
        val subnetTestedCount: Int = 0,
        val subnetTotalCount: Int = 0,
        val subnetWhiteCount: Int = 0,
        val subnetProgressPercent: Float = 0f,
        val operator: KhorshidIspDetector.Operator = KhorshidIspDetector.Operator.GENERAL,

        val ipSource: IpSourceType = IpSourceType.RANDOM_POOL,
        val customIpText: String = "",
        val targetCount: String = "1,000",
        val customTargetCount: String = "",
        val workers: String = "100 (Balanced)",
        val customWorkers: String = "",
        val timeout: String = "5s (Default)",
        val customTimeout: String = "",
        val selectedPorts: Set<String> = setOf("443"),
        val requireWebSocket: Boolean = true,
        val autoScanNeighbors: Boolean = false,
        val autoGenerateConfigs: Boolean = false,

        val scanResults: List<KhorshidScanEngine.EndpointResult> = emptyList(),
        val userConfigs: List<ConfigSummary> = emptyList(),
        val selectedGuids: Set<String> = emptySet(),
        val ipsPerConfig: Int = 1,
        val ipsPerConfigCustom: String = "",
        val useManualCleanIps: Boolean = false,
        val manualCleanIpText: String = "",

        val progressText: String = "",
        val progressPercent: Float = 0f,
        val testedCount: Int = 0,
        val whiteCount: Int = 0,
        val failedCount: Int = 0,
        val statusMessage: String = "Ready"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<Int> = _navigationEvent.asSharedFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var vpnWatchCallback: ConnectivityManager.NetworkCallback? = null
    private var cooldownJob: Job? = null
    private var subnetJob: Job? = null
    private var isStartingScan = false

    private var previousScanResults: List<KhorshidScanEngine.EndpointResult> = emptyList()

    init {
        detectOperator()
        loadSavedResults()
        loadUserConfigs()
        observeScanEvents()
    }

    override fun onCleared() {
        super.onCleared()
        unregisterVpnWatch()
        cooldownJob?.cancel()
        subnetJob?.cancel()
    }

    private fun unregisterVpnWatch() {
        vpnWatchCallback?.let { VpnStateGuard.unregister(getApplication(), it) }
        vpnWatchCallback = null
    }

    private fun startCooldown(seconds: Int = 5) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(isCooldown = true, cooldownSeconds = seconds) }
            for (sec in seconds downTo 1) {
                _uiState.update { it.copy(cooldownSeconds = sec) }
                delay(1000)
            }
            _uiState.update { it.copy(isCooldown = false, cooldownSeconds = 0) }
        }
    }

    private fun loadSavedResults() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionId = IpScanPersistence.getLastSessionId() ?: return@launch
            val saved = IpScanPersistence.loadResults(sessionId)
            if (saved.isNotEmpty()) {
                _uiState.update {
                    it.copy(scanResults = saved, statusMessage = "Loaded ${saved.size} results from last scan")
                }
            }
        }
    }

    fun detectOperator() {
        val op = KhorshidIspDetector.detect(getApplication())
        _uiState.update { it.copy(operator = op) }
    }

    fun setIpSource(type: IpSourceType) = _uiState.update { it.copy(ipSource = type) }
    fun setCustomIpText(text: String) = _uiState.update { it.copy(customIpText = text) }
    fun setTargetCount(count: String) = _uiState.update { it.copy(targetCount = count) }
    fun setCustomTargetCount(count: String) = _uiState.update { it.copy(customTargetCount = count) }
    fun setWorkers(workers: String) = _uiState.update { it.copy(workers = workers) }
    fun setCustomWorkers(workers: String) = _uiState.update { it.copy(customWorkers = workers) }
    fun setTimeout(timeout: String) = _uiState.update { it.copy(timeout = timeout) }
    fun setCustomTimeout(timeout: String) = _uiState.update { it.copy(customTimeout = timeout) }
    fun setSelectedPorts(ports: Set<String>) = _uiState.update { it.copy(selectedPorts = ports) }
    fun setRequireWebSocket(enabled: Boolean) = _uiState.update { it.copy(requireWebSocket = enabled) }
    fun setAutoScanNeighbors(enabled: Boolean) = _uiState.update { it.copy(autoScanNeighbors = enabled) }
    fun setAutoGenerateConfigs(enabled: Boolean) = _uiState.update { it.copy(autoGenerateConfigs = enabled) }
    fun setIpsPerConfig(count: Int) = _uiState.update { it.copy(ipsPerConfig = count, ipsPerConfigCustom = "") }
    fun setIpsPerConfigCustom(count: String) = _uiState.update { it.copy(ipsPerConfigCustom = count, ipsPerConfig = count.toIntOrNull()?.coerceIn(1, 100) ?: 1) }

    fun setUseManualCleanIps(enabled: Boolean) = _uiState.update { it.copy(useManualCleanIps = enabled) }
    fun setManualCleanIpText(text: String) = _uiState.update { it.copy(manualCleanIpText = text) }

    fun loadUserConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            val allGuids = MmkvManager.decodeAllServerList()
            val list = allGuids.mapNotNull { guid ->
                val cfg = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
                if (KhorshidConfigGenerator.isCleanIPConfig(guid)) return@mapNotNull null

                val analysis = KhorshidConfigAnalyzer.analyze(cfg)
                ConfigSummary(
                    guid = guid,
                    remarks = cfg.remarks.orEmpty(),
                    server = cfg.server.orEmpty(),
                    port = cfg.serverPort.orEmpty(),
                    isCompatible = analysis.isCompatible,
                    reason = analysis.reason
                )
            }
            _uiState.update { it.copy(userConfigs = list, selectedGuids = emptySet()) }
        }
    }

    fun toggleConfigSelection(guid: String) {
        _uiState.update { state ->
            val set = state.selectedGuids.toMutableSet()
            if (set.contains(guid)) set.remove(guid) else set.add(guid)
            state.copy(selectedGuids = set)
        }
    }

    fun selectAllConfigs(selectAll: Boolean) {
        _uiState.update { state ->
            val set = if (selectAll) state.userConfigs.filter { it.isCompatible }.map { it.guid }.toSet() else emptySet()
            state.copy(selectedGuids = set)
        }
    }

    private fun observeScanEvents() {
        viewModelScope.launch {
            KhorshidScanEngine.eventFlow.collect { event ->
                val percent = if (event.total > 0) event.tested.toFloat() / event.total else 0f
                _uiState.update {
                    it.copy(
                        progressPercent = percent.coerceIn(0f, 1f),
                        testedCount = event.tested,
                        whiteCount = event.green,
                        failedCount = event.failed,
                        progressText = "${event.tested} tested | ${event.green} white | ${event.failed} failed"
                    )
                }
            }
        }
    }

    fun startDiscovery() {
        val state = _uiState.value
        if (state.isScanning || state.isCooldown || isStartingScan || state.scanningNeighborIp != null) return

        isStartingScan = true
        startCooldown(5)

        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                _uiState.update { it.copy(statusMessage = "Preparing network...") }

                LauncherManager.stopService(app)
                delay(600)

                if (VpnStateGuard.isForeignVpnActive(app)) {
                    _uiState.update { it.copy(statusMessage = "Foreign VPN active") }
                    _toastEvent.tryEmit("Another VPN is running. Please disconnect it.")
                    return@launch
                }

                startDiscoveryInternal()
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Error: ${e.message}") }
            } finally {
                isStartingScan = false
            }
        }
    }

    private fun startDiscoveryInternal() {
        val state = _uiState.value
        previousScanResults = state.scanResults

        val targetNum = when (state.targetCount) {
            "500" -> 500
            "1,000" -> 1000
            "5,000" -> 5000
            "20,000" -> 20000
            else -> state.customTargetCount.replace(",", "").toIntOrNull()?.coerceIn(10, 50000) ?: 1000
        }

        val workerNum = when {
            state.workers.startsWith("50") -> 50
            state.workers.startsWith("100") -> 100
            state.workers.startsWith("200") -> 200
            else -> state.customWorkers.toIntOrNull()?.coerceIn(5, 300) ?: 100
        }

        val timeoutMs = when {
            state.timeout.startsWith("2s") -> 2000
            state.timeout.startsWith("3s") -> 3000
            state.timeout.startsWith("5s") -> 5000
            else -> (state.customTimeout.removeSuffix("s").toIntOrNull()?.coerceIn(1, 30) ?: 5) * 1000
        }

        val ports = state.selectedPorts.mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(443) }

        viewModelScope.launch(Dispatchers.IO) {
            val ipsToScan = if (state.ipSource == IpSourceType.CUSTOM_IPS && state.customIpText.isNotBlank()) {
                state.customIpText.lines().map { it.trim() }.filter { it.isNotBlank() }
            } else {
                KhorshidConstants.generateRandomIps(targetNum, state.operator.ranges)
            }

            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanResults = emptyList(),
                    testedCount = 0,
                    whiteCount = 0,
                    failedCount = 0,
                    statusMessage = "Scanning ${ipsToScan.size} Cloudflare endpoints..."
                )
            }

            unregisterVpnWatch()
            vpnWatchCallback = VpnStateGuard.registerVpnAppearedCallback(getApplication()) {
                if (_uiState.value.isScanning) {
                    handlePartialStop(
                        statusMsg = "VPN detected — scan stopped.",
                        toastMsg = "VPN connected mid-scan; scan stopped and discovered endpoints saved."
                    )
                }
            }

            KhorshidScanEngine.startDiscovery(
                ips = ipsToScan,
                ports = ports,
                workers = workerNum,
                timeoutMs = timeoutMs,
                requireWebSocket = state.requireWebSocket,
                autoScanNeighbors = state.autoScanNeighbors,
                scope = viewModelScope
            ) { results ->
                unregisterVpnWatch()

                viewModelScope.launch(Dispatchers.IO) {
                    IpScanPersistence.saveResults("khorshid_session", results)
                }

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanResults = results,
                        statusMessage = if (results.isNotEmpty()) "Done. ${results.size} white IPs found." else "Scan finished. No clean IPs found."
                    )
                }
                startCooldown(5)

                if (results.isEmpty()) {
                    _toastEvent.tryEmit("Scan finished: no healthy IPs found.")
                    return@startDiscovery
                }

                if (state.autoGenerateConfigs) {
                    val compatibleConfigs = state.userConfigs.filter { it.isCompatible }
                    if (compatibleConfigs.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val count = KhorshidConfigGenerator.cloneConfigsWithCleanIps(
                                selectedGuids = compatibleConfigs.map { it.guid },
                                cleanEndpoints = results,
                                ipsPerConfig = state.ipsPerConfig
                            )
                            _toastEvent.tryEmit("Auto-generated: $count clean configs in 'Clean IP Configs'")
                        }
                    } else {
                        _toastEvent.tryEmit("${results.size} white IPs found, but no compatible configs for auto-generation.")
                    }
                } else {
                    _toastEvent.tryEmit("${results.size} white IPs discovered!")
                    _navigationEvent.tryEmit(1)
                }
            }
        }
    }

    private fun handlePartialStop(statusMsg: String, toastMsg: String) {
        unregisterVpnWatch()
        KhorshidScanEngine.stopDiscovery()

        val partialHealthy = KhorshidScanEngine.getDiscoveredHealthyResults()
        val mergedResults = (partialHealthy + previousScanResults)
            .distinctBy { "${it.ip}:${it.port}" }
            .sortedBy { it.latencyMs }

        viewModelScope.launch(Dispatchers.IO) {
            if (mergedResults.isNotEmpty()) {
                IpScanPersistence.saveResults("khorshid_session", mergedResults)
            }
        }

        _uiState.update {
            it.copy(
                isScanning = false,
                scanResults = mergedResults,
                statusMessage = statusMsg
            )
        }

        _toastEvent.tryEmit(toastMsg)
        startCooldown(5)
    }

    fun stopScan() {
        if (_uiState.value.isCooldown) return

        val newWhiteCount = KhorshidScanEngine.getDiscoveredHealthyResults().size
        val toast = if (newWhiteCount > 0) {
            "Scan stopped. $newWhiteCount new white IPs added."
        } else {
            "Scan stopped."
        }

        handlePartialStop(
            statusMsg = "Scan stopped.",
            toastMsg = toast
        )
    }

    fun scanNeighbors(baseIp: String) {
        val state = _uiState.value
        if (state.isScanning || state.scanningNeighborIp != null || state.isCooldown) {
            _toastEvent.tryEmit("Another scan task is running.")
            return
        }

        val neighbors = KhorshidNeighborScanner.getSubnet24Neighbors(baseIp)
        if (neighbors.isEmpty()) return

        val ports = state.selectedPorts.mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(443) }
        val timeoutMs = when {
            state.timeout.startsWith("2s") -> 2000
            state.timeout.startsWith("3s") -> 3000
            state.timeout.startsWith("5s") -> 5000
            else -> (state.customTimeout.removeSuffix("s").toIntOrNull()?.coerceIn(1, 30) ?: 5) * 1000
        }

        val totalTasks = neighbors.size * ports.size
        val testedCounter = AtomicInteger(0)
        val whiteCounter = AtomicInteger(0)

        _uiState.update {
            it.copy(
                scanningNeighborIp = baseIp,
                subnetTestedCount = 0,
                subnetTotalCount = totalTasks,
                subnetWhiteCount = 0,
                subnetProgressPercent = 0f,
                statusMessage = "Scanning /24 neighbors for $baseIp..."
            )
        }
        _toastEvent.tryEmit("Subnet /24 scan started for $baseIp...")

        subnetJob?.cancel()
        subnetJob = viewModelScope.launch(Dispatchers.IO) {
            val healthyFound = ConcurrentLinkedQueue<KhorshidScanEngine.EndpointResult>()
            val semaphore = Semaphore(25)

            val jobs = neighbors.flatMap { ip ->
                ports.map { port ->
                    async {
                        semaphore.withPermit {
                            if (!isActive) return@withPermit

                            val probe = KhorshidProber.probeEndpoint(
                                ip = ip,
                                port = port,
                                host = KhorshidConstants.DEFAULT_CF_HOST,
                                timeoutMs = timeoutMs,
                                requireWebSocket = state.requireWebSocket
                            )
                            if (probe.success) {
                                val item = KhorshidScanEngine.EndpointResult(
                                    ip = ip,
                                    port = port,
                                    latencyMs = probe.ttfbMs,
                                    colo = probe.colo,
                                    isHealthy = true
                                )
                                healthyFound.add(item)
                                whiteCounter.incrementAndGet()
                            }

                            val currentTested = testedCounter.incrementAndGet()
                            val currentWhite = whiteCounter.get()
                            val progress = if (totalTasks > 0) currentTested.toFloat() / totalTasks else 0f

                            _uiState.update {
                                it.copy(
                                    subnetTestedCount = currentTested,
                                    subnetWhiteCount = currentWhite,
                                    subnetProgressPercent = progress.coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                }
            }

            jobs.awaitAll()

            val healthyList = healthyFound.toList()
            val currentList = _uiState.value.scanResults
            val mergedList = (healthyList + currentList)
                .distinctBy { "${it.ip}:${it.port}" }
                .sortedBy { it.latencyMs }

            IpScanPersistence.saveResults("khorshid_session", mergedList)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        scanningNeighborIp = null,
                        subnetTestedCount = 0,
                        subnetTotalCount = 0,
                        subnetWhiteCount = 0,
                        subnetProgressPercent = 0f,
                        scanResults = mergedList,
                        statusMessage = "Subnet scan finished. ${healthyList.size} white IPs added."
                    )
                }

                if (healthyList.isNotEmpty()) {
                    _toastEvent.tryEmit("${healthyList.size} white IPs added from subnet!")
                } else {
                    _toastEvent.tryEmit("No white IPs found in this subnet.")
                }
            }
        }
    }

    fun runSpeedTestOnTopResults() {
        val currentResults = _uiState.value.scanResults
        if (currentResults.isEmpty() || _uiState.value.isTestingSpeed) return

        _uiState.update {
            it.copy(isTestingSpeed = true, statusMessage = "Testing download speed...")
        }

        KhorshidScanEngine.runSpeedTestShortlist(
            allResults = currentResults,
            topCount = 20,
            scope = viewModelScope
        ) { ranked ->
            viewModelScope.launch(Dispatchers.IO) {
                IpScanPersistence.saveResults("khorshid_session", ranked)
            }
            _uiState.update {
                it.copy(isTestingSpeed = false, scanResults = ranked, statusMessage = "Speed test finished.")
            }
            _toastEvent.tryEmit("Speed test completed on top 20 endpoints.")
        }
    }

    fun generateCleanConfigs(onFinish: ((Int) -> Unit)? = null) {
        val state = _uiState.value
        if (state.selectedGuids.isEmpty() || state.isGenerating) return

        val endpointsToUse: List<KhorshidScanEngine.EndpointResult> = if (state.useManualCleanIps && state.manualCleanIpText.isNotBlank()) {
            state.manualCleanIpText.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { line ->
                    val parts = line.split(":")
                    val ip = parts[0].trim()
                    val port = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 443 else 443
                    KhorshidScanEngine.EndpointResult(
                        ip = ip,
                        port = port,
                        latencyMs = 0L,
                        colo = "MANUAL",
                        isHealthy = true
                    )
                }
        } else {
            state.scanResults
        }

        if (endpointsToUse.isEmpty()) {
            _toastEvent.tryEmit("No clean IPs available to generate configs.")
            return
        }

        _uiState.update { it.copy(isGenerating = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val count = KhorshidConfigGenerator.cloneConfigsWithCleanIps(
                selectedGuids = state.selectedGuids.toList(),
                cleanEndpoints = endpointsToUse,
                ipsPerConfig = state.ipsPerConfig
            )

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isGenerating = false) }
                if (count > 0) {
                    _toastEvent.tryEmit("Generated $count clean configs in 'Clean IP Configs'")
                } else {
                    _toastEvent.tryEmit("Error: No configs generated. Check source configs.")
                }
                onFinish?.invoke(count)
            }
        }
    }
}