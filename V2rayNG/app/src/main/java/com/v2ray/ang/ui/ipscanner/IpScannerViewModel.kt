package com.v2ray.ang.ui.ipscanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.reachability.CleanIPGenerator
import com.v2ray.ang.reachability.CloudflareIpPool
import com.v2ray.ang.reachability.IpScanPersistence
import com.v2ray.ang.reachability.IpScannerManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class IpScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "IpScannerVM"

    data class UiState(
        val isScanning: Boolean = false,
        val isGenerating: Boolean = false, // اضافه شدن متغیر برای برطرف شدن ارور Unresolved reference
        val isCooldown: Boolean = false,
        val cooldownSeconds: Int = 0,
        val scanPhase: Int = 0,
        val testedCount: Int = 0,
        val greenCount: Int = 0,
        val failedCount: Int = 0,
        val scanResults: List<IpScannerManager.ScanResult> = emptyList(),
        val optimizedIps: Map<String, List<IpScannerManager.ScanResult>> = emptyMap(),
        val selectedConfigGuids: Set<String> = emptySet(),
        val topN: Int = 20,
        val scanStatus: String = "Ready",
        val sessionId: String = UUID.randomUUID().toString(),
        val toastMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun startCooldownTimer(seconds: Int = 10) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCooldown = true, cooldownSeconds = seconds) }
            for (i in seconds downTo 1) {
                _uiState.update { it.copy(cooldownSeconds = i) }
                delay(1000)
            }
            _uiState.update { it.copy(isCooldown = false, cooldownSeconds = 0) }
        }
    }

    fun loadLastSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastSessionId = IpScanPersistence.getLastSessionId() ?: return@launch
                val results = IpScanPersistence.loadResults(lastSessionId)
                if (results.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            scanResults = results,
                            sessionId = lastSessionId,
                            scanStatus = "Loaded ${results.size} results from last session"
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "loadLastSession failed", e)
            }
        }
    }

    fun startTcpScan(ports: List<Int>, timeoutMs: Int, concurrency: Int) {
        if (_uiState.value.isScanning || _uiState.value.isCooldown) return

        val safeTargetCount = _uiState.value.topN.coerceAtLeast(10)
        val safeConcurrency = concurrency.coerceAtLeast(1)
        val safePorts = ports.ifEmpty { listOf(443) }

        startCooldownTimer(10)

        viewModelScope.launch(Dispatchers.IO) {
            val ips = CloudflareIpPool.generateRandomIps(safeTargetCount)
            if (ips.isEmpty()) {
                _uiState.update { it.copy(scanStatus = "No IPs to scan") }
                return@launch
            }

            val newSessionId = UUID.randomUUID().toString()
            _uiState.update {
                it.copy(
                    isScanning = true, scanPhase = 1, scanResults = emptyList(),
                    testedCount = 0, greenCount = 0, failedCount = 0,
                    scanStatus = "Scanning ${ips.size} IPs...", sessionId = newSessionId
                )
            }

            IpScannerManager.onScanProgress = { tested, green, failed ->
                _uiState.update { it.copy(testedCount = tested, greenCount = green, failedCount = failed) }
            }

            IpScannerManager.onScanComplete = { results ->
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        IpScanPersistence.saveResults(newSessionId, results)
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Failed to save scan results", e)
                    }
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            scanPhase = 0,
                            scanResults = results,
                            scanStatus = "Found ${results.size} reachable IPs",
                            toastMessage = "Scan complete: ${results.size} reachable IPs found."
                        )
                    }
                }
            }

            IpScannerManager.startTcpScan(ips, safePorts, timeoutMs, safeConcurrency, viewModelScope)
        }
    }

    fun startTlsValidation(configPort: Int, sni: String) {
        if (_uiState.value.isScanning || _uiState.value.isCooldown) return

        if (_uiState.value.scanResults.isEmpty()) {
            _uiState.update { it.copy(scanStatus = "Run TCP scan first") }
            return
        }

        startCooldownTimer(10)

        _uiState.update { it.copy(isScanning = true, scanPhase = 2, scanStatus = "Validating TLS with SNI: $sni...") }

        IpScannerManager.onScanProgress = { tested, green, failed ->
            _uiState.update { it.copy(testedCount = tested, greenCount = green, failedCount = failed) }
        }

        IpScannerManager.onScanComplete = { results ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    IpScanPersistence.saveResults(_uiState.value.sessionId, results)
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to save TLS validation results", e)
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanPhase = 0,
                        scanResults = results,
                        scanStatus = "Validated ${results.size} IPs with TLS/SNI"
                    )
                }
            }
        }

        IpScannerManager.startTlsValidation(_uiState.value.scanResults, configPort, sni, _uiState.value.topN, viewModelScope)
    }

    fun stopScan() {
        IpScannerManager.stopScan()
        _uiState.update { it.copy(isScanning = false, scanPhase = 0, scanStatus = "Stopped — ${_uiState.value.scanResults.size} results saved") }
    }

    fun saveOptimizedIps(configGuid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                IpScannerManager.saveOptimizedIps(configGuid, _uiState.value.scanResults.filter { it.tlsOk })
            } catch (e: Exception) {
                LogUtil.e(TAG, "saveOptimizedIps failed", e)
            }
        }
    }

    fun setTopN(n: Int) {
        _uiState.update { it.copy(topN = n.coerceAtLeast(10)) }
    }

    fun generateCleanIPs(numPerConfig: Int) {
        val scanResults = _uiState.value.scanResults
        if (scanResults.isEmpty()) {
            _uiState.update { it.copy(scanStatus = "No scan results — run IP Scan first") }
            return
        }

        if (_uiState.value.isGenerating) return

        val safeNum = numPerConfig.coerceIn(1, 50)

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isGenerating = true, scanStatus = "Generating Clean IP configs...") }

            try {
                val serverList = MmkvManager.decodeAllServerList()
                val generated = CleanIPGenerator.generateCleanIPConfigs(
                    sourceConfigs = serverList,
                    scanResults = scanResults,
                    n = safeNum
                )

                _uiState.update {
                    val statusText = if (safeNum != numPerConfig) {
                        "Limited to max 50/config. Generated ${generated.size} configs."
                    } else {
                        "Generated ${generated.size} Clean IP configs"
                    }

                    if (generated.isNotEmpty()) {
                        it.copy(scanStatus = statusText, isGenerating = false)
                    } else {
                        it.copy(scanStatus = "No configs found — check subscription", isGenerating = false)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "generateCleanIPs failed", e)
                _uiState.update { it.copy(scanStatus = "Failed to generate configs", isGenerating = false) }
            }
        }
    }

    fun clearResults() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                IpScanPersistence.clearAll()
                _uiState.update { it.copy(scanResults = emptyList(), scanStatus = "Results cleared") }
            } catch (e: Exception) {
                LogUtil.e(TAG, "clearResults failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            IpScannerManager.stopScan()
            IpScannerManager.onScanProgress = null
            IpScannerManager.onScanComplete = null
        } catch (e: Exception) {
            LogUtil.e(TAG, "onCleared cleanup failed", e)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(IpScannerViewModel::class.java)) return IpScannerViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}